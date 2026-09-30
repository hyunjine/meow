-- Meow · pr_events 에 라벨 컬럼 추가
-- GitHub PR 라벨을 [{name, color}, ...] 형태의 jsonb 로 저장해
-- Realtime 수신 시 데스크톱 앱이 라벨을 매핑할 수 있게 한다.

alter table public.pr_events
    add column if not exists labels jsonb not null default '[]'::jsonb;
