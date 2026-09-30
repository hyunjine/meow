-- Meow · PR review-requested 이벤트 스트림
-- Edge Function (gh-webhook) 가 INSERT, 데스크톱 앱이 Realtime 으로 구독한다.

create table if not exists public.pr_events (
    id                  uuid        primary key default gen_random_uuid(),
    received_at         timestamptz not null    default now(),
    delivery_id         text        unique,
    event_type          text        not null,
    action              text        not null,
    requested_reviewer  text        not null,
    pr_url              text        not null,
    pr_number           int         not null,
    pr_title            text        not null,
    repo_full_name      text        not null,
    author              text,
    is_draft            boolean     not null    default false
);

create index if not exists pr_events_reviewer_recent_idx
    on public.pr_events (requested_reviewer, received_at desc);

-- Realtime publication 등록: INSERT 이벤트를 클라이언트로 흘려보낸다
alter publication supabase_realtime add table public.pr_events;

-- RLS
alter table public.pr_events enable row level security;

-- anon key 로 조회 허용 (개인 앱 · 단일 사용자 전제).
-- 필요 시 requested_reviewer = current_setting('...') 형태로 좁힐 수 있다.
create policy "anon can read pr_events"
    on public.pr_events
    for select
    to anon
    using (true);

-- INSERT 는 service_role 만 (RLS bypass) — Edge Function 전용.
