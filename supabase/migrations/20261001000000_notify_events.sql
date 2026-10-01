-- Meow · 리뷰 요청 외 알림 이벤트 스트림 (#3)
-- 멘션 · 내 이슈 새 댓글 · 할당 · 내 PR 리뷰(승인 / 변경 요청)를 담는다.
-- 리뷰 요청은 기존 pr_events 가 그대로 담당하고, 이 테이블은 사람(target_login) 단위의 알림 한 건이 한 행.
-- Edge Function (gh-webhook) 가 INSERT, 데스크톱 앱이 Realtime 으로 구독한다.

create table if not exists public.notify_events (
    id              uuid        primary key default gen_random_uuid(),
    created_at      timestamptz not null    default now(),
    delivery_id     text        not null,
    -- 'mentioned' | 'new_comment' | 'assigned' | 'pr_review'
    kind            text        not null
                    check (kind in ('mentioned', 'new_comment', 'assigned', 'pr_review')),
    -- GitHub login 은 대소문자를 구분하지 않으므로 소문자로 저장한다.
    target_login    text        not null,
    repo_full_name  text        not null,
    number          int         not null,
    title           text        not null,
    -- new_comment 는 댓글 url, 나머지는 이슈 · PR url (앱의 60초 조회 섹션과 같은 기준).
    url             text        not null,
    actor           text,
    excerpt         text,
    -- pr_review 전용: 'approved' | 'changes_requested'
    review_state    text,
    -- delivery 하나가 여러 사람 · 종류의 알림을 만들 수 있어 세 컬럼 조합으로 중복(retry)을 막는다.
    unique (delivery_id, kind, target_login)
);

create index if not exists notify_events_target_recent_idx
    on public.notify_events (target_login, created_at desc);

-- Realtime publication 등록: INSERT 이벤트를 클라이언트로 흘려보낸다
alter publication supabase_realtime add table public.notify_events;

-- RLS
alter table public.notify_events enable row level security;

-- anon key 로 조회 허용 (개인 앱 · 단일 사용자 전제, pr_events 와 동일).
create policy "anon can read notify_events"
    on public.notify_events
    for select
    to anon
    using (true);

-- INSERT 는 service_role 만 (RLS bypass) — Edge Function 전용.
