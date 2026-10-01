-- Meow · 새 댓글 알림 확장 (#84)
-- 1) notify_events.kind 에 내 PR 댓글 · Comment 리뷰 · 참여한 스레드 댓글을 추가한다.
-- 2) 참여한 스레드 판별용 thread_participants: 웹훅으로 본 댓글 · 리뷰 작성자를 스레드별로 기록한다.
--    배포 이전 참여는 서버가 모르므로 앱의 60초 조회가 담당한다.

-- 'pr_comment'        : 내 PR 에 달린 일반 댓글 (PR 작성자에게)
-- 'pr_review_comment' : 내 PR 에 'Comment' 로 제출된 리뷰 (PR 작성자에게)
-- 'thread_comment'    : 참여한 스레드의 새 댓글 (참여자에게)
alter table public.notify_events drop constraint if exists notify_events_kind_check;
alter table public.notify_events add constraint notify_events_kind_check
    check (kind in (
        'mentioned', 'new_comment', 'assigned', 'pr_review',
        'pr_comment', 'pr_review_comment', 'thread_comment'
    ));

create table if not exists public.thread_participants (
    repo_full_name     text        not null,
    -- 이슈 · PR 번호 (저장소 안에서 둘은 번호를 공유한다).
    number             int         not null,
    -- GitHub login 은 대소문자를 구분하지 않으므로 소문자로 저장한다.
    login              text        not null,
    last_commented_at  timestamptz not null default now(),
    primary key (repo_full_name, number, login)
);

-- RLS: 정책 없음 → anon 은 읽기 · 쓰기 불가. Edge Function(service_role, RLS bypass) 전용.
-- 앱이 구독하지 않으므로 Realtime publication 에도 추가하지 않는다.
alter table public.thread_participants enable row level security;
