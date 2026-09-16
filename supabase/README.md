# Supabase 셋업 — Meow 실시간 알림

Meow 데스크톱 앱이 GitHub PR review-requested 이벤트를 실시간으로 받는 파이프라인.

```
GitHub Webhook  →  Supabase Edge Function (gh-webhook)
                          ↓ INSERT
                    Postgres · pr_events
                          ↓ Realtime (WebSocket)
                    Meow.app → macOS 알림
```

## 1. 사전 준비

- [Supabase CLI](https://supabase.com/docs/guides/cli) (`brew install supabase/tap/supabase`)
- Supabase 계정 (free tier 로 충분)
- Team-AIVN 조직에서 hyunjine 이 **admin 권한을 가진 저장소** 목록:
    - `ChatSea-Android`
    - `ChatSea-Apple`
    - `ChatSeaAPI-Android`
    - `mms-agent-kmp`
    - `mms-agent-kmp-integration-tester`
    - `mms-kmp-ios`

## 2. Supabase 프로젝트 생성

```bash
supabase login
supabase projects create meow --region ap-northeast-2
supabase link --project-ref <프로젝트 ref>
```

## 3. DB 스키마 적용

```bash
# 로컬 개발용
supabase db reset

# 또는 원격 배포
supabase db push
```

`migrations/20260916000000_pr_events.sql` 이 자동으로 실행됩니다.

## 4. Edge Function 배포

먼저 GitHub 웹훅 시크릿을 만들고 (openssl rand -hex 32) 환경변수로 등록:

```bash
export GH_SECRET=$(openssl rand -hex 32)
supabase secrets set GITHUB_WEBHOOK_SECRET="$GH_SECRET"
supabase functions deploy gh-webhook --no-verify-jwt
```

배포되면 Function URL 이 나옵니다: `https://<ref>.supabase.co/functions/v1/gh-webhook`

## 5. 6개 저장소에 웹훅 등록 (셀프 서비스)

각 저장소마다 **Settings → Webhooks → Add webhook**:

| 필드 | 값 |
| --- | --- |
| Payload URL | `https://<ref>.supabase.co/functions/v1/gh-webhook` |
| Content type | `application/json` |
| Secret | `$GH_SECRET` (위에서 생성한 값) |
| SSL verification | Enable |
| Which events | Let me select individual events → **Pull requests** 만 체크 |
| Active | ✅ |

저장 직후 GitHub 이 "ping" 을 쏘고, Function 이 `pong` 을 리턴하면 등록 성공.

## 6. 데스크톱 앱 설정

`~/.config/meow/supabase_url`, `~/.config/meow/supabase_anon` 파일 생성:

```bash
mkdir -p ~/.config/meow
printf '%s' 'https://<ref>.supabase.co' > ~/.config/meow/supabase_url
printf '%s' '<anon key — Supabase dashboard → Project Settings → API>' > ~/.config/meow/supabase_anon
chmod 600 ~/.config/meow/supabase_*
```

또는 환경변수:

```bash
export MEOW_SUPABASE_URL=https://<ref>.supabase.co
export MEOW_SUPABASE_ANON_KEY=<anon key>
```

앱을 재실행하면 자동으로 Realtime 채널 구독을 시작합니다. Realtime 이 없으면 기존 60초 폴링만 동작하고, 있으면 폴링 + Realtime 이 함께 돌면서 알림이 중복되지 않도록 URL 셋으로 dedup 합니다.

## 7. 동작 확인

1. 다른 계정으로 위 6개 저장소 중 하나에 PR 생성 → hyunjine 에게 리뷰 요청
2. Supabase Dashboard → Table Editor → `pr_events` 에 새 행 뜨는지 확인
3. macOS 알림 배너 팝업 (5초 안팎)

## 로그 확인

```bash
supabase functions logs gh-webhook --tail
```

## 조직 전체(Team-AIVN) 로 확장

위 6개 이외 저장소도 커버하려면 org 오너 (ChangyunLeee / jakob22r / JinkiJung / oliverhaagh / seunghyeoks) 중 한 명에게 org 웹훅 추가 요청:

- Team-AIVN 조직 Settings → Webhooks → Add webhook
- Payload URL / Secret / Content type 는 위와 동일
- Events: `Pull requests`

## 롤백

- Edge Function 삭제: `supabase functions delete gh-webhook`
- 웹훅 삭제: 각 저장소 Settings → Webhooks → Delete
- 테이블 삭제: `drop table public.pr_events cascade;`
