// deno-lint-ignore-file no-explicit-any
// Meow · GitHub → Supabase 웹훅 수신기
// - GitHub 이 `pull_request` action=`review_requested` 를 POST 하면
//   서명 검증 후 pr_events 테이블에 INSERT.
// - INSERT 는 supabase_realtime publication 을 통해 데스크톱 앱으로 push.
//
// Deploy: supabase functions deploy gh-webhook --no-verify-jwt
// Secrets: supabase secrets set GITHUB_WEBHOOK_SECRET=... (프로젝트 SERVICE_ROLE_KEY / URL 은 자동 주입)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const GITHUB_WEBHOOK_SECRET = Deno.env.get("GITHUB_WEBHOOK_SECRET")!;

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
  if (event !== "pull_request") return new Response("ignored", { status: 200 });

  let payload: any;
  try {
    payload = JSON.parse(body);
  } catch {
    return new Response("invalid json", { status: 400 });
  }

  if (payload.action !== "review_requested") {
    return new Response("ignored", { status: 200 });
  }

  const reviewer =
    payload.requested_reviewer?.login ??
    payload.requested_team?.name ??
    null;
  if (!reviewer) return new Response("no reviewer", { status: 200 });

  const pr = payload.pull_request;
  const labels = Array.isArray(pr.labels)
    ? pr.labels.map((label: any) => ({ name: label.name, color: label.color }))
    : [];
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
    auth: { persistSession: false },
  });

  const { error } = await supabase.from("pr_events").insert({
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
});
