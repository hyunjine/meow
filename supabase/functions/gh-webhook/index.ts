// deno-lint-ignore-file no-explicit-any
// Meow · GitHub → Supabase 웹훅 수신기
// - GitHub 이 `pull_request` action=`review_requested` 를 POST 하면
//   서명 검증 후 pr_events 테이블에 INSERT.
// - #3 그 밖의 알림(멘션 · 내 이슈 새 댓글 · 할당 · 내 PR 리뷰)은 notify_events 테이블에 사람 단위로 INSERT.
//   issue_comment · issues · pull_request_review · pull_request(opened / edited) 이벤트를 처리한다.
// - INSERT 는 supabase_realtime publication 을 통해 데스크톱 앱으로 push.
//
// Deploy: supabase functions deploy gh-webhook --no-verify-jwt
// Secrets: supabase secrets set GITHUB_WEBHOOK_SECRET=... (프로젝트 SERVICE_ROLE_KEY / URL 은 자동 주입)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const GITHUB_WEBHOOK_SECRET = Deno.env.get("GITHUB_WEBHOOK_SECRET")!;

/** 처리하는 GitHub 이벤트. 나머지는 200 ignored. */
const HANDLED_EVENTS = new Set(["pull_request", "pull_request_review", "issue_comment", "issues"]);

/** 알림 미리보기로 저장하는 본문 길이. */
const EXCERPT_LENGTH = 120;

async function hmacHex(secret: string, body: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, enc.encode(body));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function verifySignature(body: string, header: string | null): Promise<boolean> {
  if (!header) return false;
  const expected = "sha256=" + (await hmacHex(GITHUB_WEBHOOK_SECRET, body));
  return timingSafeEqual(expected, header);
}

function db() {
  return createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });
}

type NotifyKind = "mentioned" | "new_comment" | "assigned" | "pr_review";

interface NotifyRow {
  delivery_id: string;
  kind: NotifyKind;
  target_login: string;
  repo_full_name: string;
  number: number;
  title: string;
  url: string;
  actor: string | null;
  excerpt: string | null;
  review_state: string | null;
}

/** 이슈 · PR 하나에 대한 알림 행의 공통 부분. */
interface Thread {
  repo: string;
  number: number;
  title: string;
  url: string;
}

/** 공백을 한 칸으로 줄인 본문 앞 [EXCERPT_LENGTH] 자. 본문이 없으면 null. */
function excerptOf(body: string | null | undefined): string | null {
  const text = (body ?? "").replace(/\s+/g, " ").trim();
  if (!text) return null;
  return Array.from(text).slice(0, EXCERPT_LENGTH).join("");
}

// `@login` — 앞이 단어 문자 · 경로가 아니고(이메일 · URL 제외), 뒤에 `/team` 이 붙지 않은 것(팀 멘션 제외).
const MENTION_RE = /(^|[^A-Za-z0-9_`/@.-])@([A-Za-z0-9](?:[A-Za-z0-9-]{0,38}))(?![A-Za-z0-9-]|\/[A-Za-z0-9])/g;

/** 본문에서 멘션된 login (소문자, 중복 제거). 코드 블록 · 인라인 코드 · 인용(`>`) 줄은 제외한다. */
function mentionsIn(body: string | null | undefined): Set<string> {
  const text = (body ?? "")
    .replace(/```[\s\S]*?```/g, " ")
    .replace(/`[^`\n]*`/g, " ")
    .replace(/^\s*>.*$/gm, " ");
  const logins = new Set<string>();
  for (const match of text.matchAll(MENTION_RE)) logins.add(match[2].toLowerCase());
  return logins;
}

/** edited 이벤트는 수정 전 본문에 없던 멘션만 새로 알린다. 본문이 바뀌지 않았으면 빈 집합. */
function newMentions(payload: any, body: string | null | undefined): Set<string> {
  if (payload.action !== "edited") return mentionsIn(body);
  const before = payload.changes?.body?.from;
  if (typeof before !== "string") return new Set();
  const previous = mentionsIn(before);
  return new Set([...mentionsIn(body)].filter((login) => !previous.has(login)));
}

/** 이벤트 하나에서 사람 · 종류별 알림 행을 만든다. 처리 대상이 아니면 빈 배열. */
function notifyRows(event: string, payload: any, delivery: string): NotifyRow[] {
  const rows: NotifyRow[] = [];
  const repo = payload.repository?.full_name as string;
  const add = (
    kind: NotifyKind,
    target: string | null | undefined,
    thread: Thread,
    actor: string | null | undefined,
    body: string | null | undefined,
    reviewState: string | null = null,
  ) => {
    if (!target) return;
    rows.push({
      delivery_id: delivery,
      kind,
      target_login: target.toLowerCase(),
      repo_full_name: thread.repo,
      number: thread.number,
      title: thread.title,
      url: thread.url,
      actor: actor ?? null,
      excerpt: excerptOf(body),
      review_state: reviewState,
    });
  };
  /** 작성자 본인과 [skip] 을 제외한 멘션마다 'mentioned'. */
  const addMentions = (
    mentions: Set<string>,
    thread: Thread,
    actor: string | null | undefined,
    body: string | null | undefined,
    skip: string | null = null,
  ) => {
    for (const login of mentions) {
      if (login === actor?.toLowerCase() || login === skip?.toLowerCase()) continue;
      add("mentioned", login, thread, actor, body);
    }
  };

  switch (event) {
    case "issue_comment": {
      if (payload.action !== "created") break;
      const issue = payload.issue;
      const comment = payload.comment;
      const thread: Thread = { repo, number: issue.number, title: issue.title, url: issue.html_url };
      const commenter = comment.user?.login as string | undefined;
      const issueAuthor = issue.user?.login as string | undefined;
      // 앱의 '내 이슈에 달린 새 댓글' 섹션과 같은 기준: PR 이 아닌 이슈, 작성자가 아닌 사람의 댓글. url 은 댓글 url.
      let notifiedAuthor: string | null = null;
      if (!issue.pull_request && issueAuthor && issueAuthor !== commenter) {
        add("new_comment", issueAuthor, { ...thread, url: comment.html_url }, commenter, comment.body);
        notifiedAuthor = issueAuthor;
      }
      // 새 댓글 알림을 받은 이슈 작성자에게는 같은 댓글로 멘션 알림을 또 보내지 않는다.
      addMentions(mentionsIn(comment.body), thread, commenter, comment.body, notifiedAuthor);
      break;
    }
    case "issues": {
      const issue = payload.issue;
      const thread: Thread = { repo, number: issue.number, title: issue.title, url: issue.html_url };
      if (payload.action === "assigned") {
        add("assigned", payload.assignee?.login, thread, payload.sender?.login, issue.body);
      } else if (payload.action === "opened" || payload.action === "edited") {
        const actor = payload.action === "opened" ? issue.user?.login : payload.sender?.login;
        addMentions(newMentions(payload, issue.body), thread, actor, issue.body);
      }
      break;
    }
    case "pull_request": {
      if (payload.action !== "opened" && payload.action !== "edited") break;
      const pr = payload.pull_request;
      const thread: Thread = { repo, number: pr.number, title: pr.title, url: pr.html_url };
      const actor = payload.action === "opened" ? pr.user?.login : payload.sender?.login;
      addMentions(newMentions(payload, pr.body), thread, actor, pr.body);
      break;
    }
    case "pull_request_review": {
      if (payload.action !== "submitted") break;
      const state = (payload.review?.state ?? "").toLowerCase();
      if (state !== "approved" && state !== "changes_requested") break;
      const pr = payload.pull_request;
      const reviewer = payload.review.user?.login as string | undefined;
      const prAuthor = pr.user?.login as string | undefined;
      if (prAuthor === reviewer) break;
      const thread: Thread = { repo, number: pr.number, title: pr.title, url: pr.html_url };
      add("pr_review", prAuthor, thread, reviewer, payload.review.body, state);
      break;
    }
  }
  return rows;
}

/** 기존 리뷰 요청 처리 — pr_events 에 INSERT. */
async function insertReviewRequest(event: string, payload: any, delivery: string): Promise<Response> {
  const reviewer =
    payload.requested_reviewer?.login ??
    payload.requested_team?.name ??
    null;
  if (!reviewer) return new Response("no reviewer", { status: 200 });

  const pr = payload.pull_request;
  const labels = Array.isArray(pr.labels)
    ? pr.labels.map((label: any) => ({ name: label.name, color: label.color }))
    : [];

  const { error } = await db().from("pr_events").insert({
    delivery_id: delivery,
    event_type: event,
    action: payload.action,
    requested_reviewer: reviewer,
    pr_url: pr.html_url,
    pr_number: pr.number,
    pr_title: pr.title,
    repo_full_name: payload.repository.full_name,
    author: pr.user?.login ?? null,
    is_draft: !!pr.draft,
    labels,
  });

  if (error) {
    // 중복 delivery 는 무시 (retry 방어)
    if (error.code === "23505") return new Response("duplicate", { status: 200 });
    return new Response(`db error: ${error.message}`, { status: 500 });
  }

  return new Response("ok", { status: 200 });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("method not allowed", { status: 405 });

  const body = await req.text();
  const sigHeader = req.headers.get("x-hub-signature-256");
  if (!(await verifySignature(body, sigHeader))) {
    return new Response("invalid signature", { status: 401 });
  }

  const event = req.headers.get("x-github-event");
  const delivery = req.headers.get("x-github-delivery") ?? crypto.randomUUID();

  if (event === "ping") return new Response("pong", { status: 200 });
  if (!event || !HANDLED_EVENTS.has(event)) return new Response("ignored", { status: 200 });

  let payload: any;
  try {
    payload = JSON.parse(body);
  } catch {
    return new Response("invalid json", { status: 400 });
  }

  if (event === "pull_request" && payload.action === "review_requested") {
    return await insertReviewRequest(event, payload, delivery);
  }

  const rows = notifyRows(event, payload, delivery);
  if (rows.length === 0) return new Response("ignored", { status: 200 });

  // 한 문장으로 INSERT 하므로 retry 로 같은 delivery 가 다시 오면 전체가 unique 위반 → 무시.
  const { error } = await db().from("notify_events").insert(rows);
  if (error) {
    if (error.code === "23505") return new Response("duplicate", { status: 200 });
    return new Response(`db error: ${error.message}`, { status: 500 });
  }

  return new Response(`ok (${rows.length})`, { status: 200 });
});
