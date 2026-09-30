#!/usr/bin/env bash
# meow Supabase 셋업 스크립트
#
# supabase/README.md 의 4~6단계(Edge Function 배포 / 저장소 웹훅 등록 / 데스크톱 앱 설정)를
# 한 번에 진행합니다. 각 단계 실행 전에 무엇을 하는지 보여주고 y/N 확인을 받습니다.
#
# 사전 조건: 1~3단계(Supabase 프로젝트 생성, `supabase link`, `supabase db push`)는
# 이 스크립트가 대신하지 않습니다. README 를 따라 먼저 진행하세요.
#
# 사용법:
#   supabase/scripts/setup.sh \
#     --project-ref <supabase-project-ref> \
#     --anon-key <supabase-anon-key> \
#     --webhook-secret <github-webhook-secret> \
#     [--owner <github-owner, 기본: Team-AIVN>] \
#     [--repos "repo1,repo2,..."] \
#     [--generate-secret] \
#     [--skip-deploy] [--skip-webhooks] [--skip-config]
#
# 값은 인자 대신 환경변수로도 줄 수 있습니다:
#   PROJECT_REF, SUPABASE_ANON_KEY, GITHUB_WEBHOOK_SECRET, GH_OWNER, GH_REPOS

set -euo pipefail

DEFAULT_OWNER="Team-AIVN"
DEFAULT_REPOS=(
  ChatSea-Android
  ChatSea-Apple
  ChatSeaAPI-Android
  mms-agent-kmp
  mms-agent-kmp-integration-tester
  mms-kmp-ios
)

usage() {
  cat <<'EOF'
사용법: supabase/scripts/setup.sh [옵션]

옵션:
  --project-ref <ref>       Supabase 프로젝트 ref (PROJECT_REF 환경변수로도 지정 가능)
  --anon-key <key>          Supabase anon key (SUPABASE_ANON_KEY 환경변수로도 지정 가능)
  --webhook-secret <secret> GitHub 웹훅 시크릿 (GITHUB_WEBHOOK_SECRET 환경변수로도 지정 가능)
  --generate-secret         webhook-secret 미지정 시 openssl 로 새로 생성해서 사용
  --owner <owner>           웹훅을 등록할 GitHub owner (기본: Team-AIVN)
  --repos "a,b,c"           웹훅을 등록할 저장소 목록 (기본: README 6개 저장소)
  --skip-deploy             4단계(Edge Function 배포) 건너뛰기
  --skip-webhooks           5단계(저장소 웹훅 등록) 건너뛰기
  --skip-config             6단계(데스크톱 앱 설정 파일) 건너뛰기
  -h, --help                이 도움말 출력

각 단계는 실행 전에 무엇을 할지 출력하고 y/N 확인을 받습니다.
EOF
}

PROJECT_REF="${PROJECT_REF:-}"
ANON_KEY="${SUPABASE_ANON_KEY:-}"
WEBHOOK_SECRET="${GITHUB_WEBHOOK_SECRET:-}"
OWNER="${GH_OWNER:-$DEFAULT_OWNER}"
REPOS_CSV="${GH_REPOS:-}"
GENERATE_SECRET=0
SKIP_DEPLOY=0
SKIP_WEBHOOKS=0
SKIP_CONFIG=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-ref) PROJECT_REF="$2"; shift 2 ;;
    --anon-key) ANON_KEY="$2"; shift 2 ;;
    --webhook-secret) WEBHOOK_SECRET="$2"; shift 2 ;;
    --generate-secret) GENERATE_SECRET=1; shift ;;
    --owner) OWNER="$2"; shift 2 ;;
    --repos) REPOS_CSV="$2"; shift 2 ;;
    --skip-deploy) SKIP_DEPLOY=1; shift ;;
    --skip-webhooks) SKIP_WEBHOOKS=1; shift ;;
    --skip-config) SKIP_CONFIG=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "알 수 없는 옵션: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -n "$REPOS_CSV" ]]; then
  IFS=',' read -r -a REPOS <<< "$REPOS_CSV"
else
  REPOS=("${DEFAULT_REPOS[@]}")
fi

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "오류: 필요한 명령을 찾을 수 없습니다: $1" >&2
    exit 1
  }
}

confirm() {
  local prompt="$1" reply
  read -r -p "$prompt [y/N] " reply || reply=""
  [[ "$reply" =~ ^[Yy]$ ]]
}

if [[ "$GENERATE_SECRET" -eq 1 && -z "$WEBHOOK_SECRET" ]]; then
  need_cmd openssl
  WEBHOOK_SECRET="$(openssl rand -hex 32)"
  echo "새 웹훅 시크릿을 생성했습니다. 안전한 곳에 보관하세요:"
  echo "  $WEBHOOK_SECRET"
fi

# 필요한 값이 빠졌는지 실행할 단계 기준으로 먼저 검증합니다.
if [[ "$SKIP_DEPLOY" -eq 0 || "$SKIP_WEBHOOKS" -eq 0 ]] && [[ -z "$PROJECT_REF" ]]; then
  echo "오류: --project-ref (또는 PROJECT_REF) 가 필요합니다." >&2
  exit 1
fi
if [[ "$SKIP_DEPLOY" -eq 0 || "$SKIP_WEBHOOKS" -eq 0 ]] && [[ -z "$WEBHOOK_SECRET" ]]; then
  echo "오류: --webhook-secret (또는 GITHUB_WEBHOOK_SECRET, --generate-secret) 이 필요합니다." >&2
  exit 1
fi
if [[ "$SKIP_CONFIG" -eq 0 ]] && [[ -z "$ANON_KEY" ]]; then
  echo "오류: --anon-key (또는 SUPABASE_ANON_KEY) 가 필요합니다." >&2
  exit 1
fi

FUNCTION_URL=""
if [[ -n "$PROJECT_REF" ]]; then
  FUNCTION_URL="https://${PROJECT_REF}.supabase.co/functions/v1/gh-webhook"
fi

step_deploy() {
  need_cmd supabase
  echo
  echo "=== 4단계: Edge Function 배포 ==="
  echo "다음을 실행합니다:"
  echo "  supabase secrets set GITHUB_WEBHOOK_SECRET=<hidden>"
  echo "  supabase functions deploy gh-webhook --no-verify-jwt"
  if confirm "진행할까요?"; then
    supabase secrets set GITHUB_WEBHOOK_SECRET="$WEBHOOK_SECRET"
    supabase functions deploy gh-webhook --no-verify-jwt
    echo "배포 완료. Function URL: $FUNCTION_URL"
  else
    echo "건너뜀"
  fi
}

step_webhooks() {
  need_cmd gh
  echo
  echo "=== 5단계: 저장소 웹훅 등록 ==="
  echo "대상 저장소 (owner: $OWNER): ${REPOS[*]}"
  echo "Payload URL: $FUNCTION_URL"
  local repo existing
  for repo in "${REPOS[@]}"; do
    echo "--- $OWNER/$repo ---"
    existing="$(gh api "repos/$OWNER/$repo/hooks" --jq \
      ".[] | select(.config.url == \"$FUNCTION_URL\") | .id" 2>/dev/null || true)"
    if [[ -n "$existing" ]]; then
      echo "이미 같은 URL 의 웹훅이 등록되어 있습니다 (id: $existing). 건너뜀"
      continue
    fi
    if confirm "$OWNER/$repo 에 웹훅을 등록할까요? (events: pull_request)"; then
      gh api "repos/$OWNER/$repo/hooks" \
        -X POST \
        -f name=web \
        -F active=true \
        -f 'events[]=pull_request' \
        -f config[url]="$FUNCTION_URL" \
        -f config[content_type]=json \
        -f config[secret]="$WEBHOOK_SECRET" \
        -f config[insecure_ssl]=0 \
        >/dev/null
      echo "등록 완료"
    else
      echo "건너뜀"
    fi
  done
}

step_config() {
  local dir="$HOME/.config/meow"
  local url="https://${PROJECT_REF}.supabase.co"
  echo
  echo "=== 6단계: 데스크톱 앱 설정 ==="
  echo "다음 파일을 작성합니다 (권한 600):"
  echo "  $dir/supabase_url"
  echo "  $dir/supabase_anon"
  if confirm "진행할까요?"; then
    mkdir -p "$dir"
    printf '%s' "$url" > "$dir/supabase_url"
    printf '%s' "$ANON_KEY" > "$dir/supabase_anon"
    chmod 600 "$dir/supabase_url" "$dir/supabase_anon"
    echo "작성 완료: $dir/supabase_url, $dir/supabase_anon"
  else
    echo "건너뜀"
  fi
}

main() {
  echo "meow Supabase 셋업 스크립트 (supabase/README.md 4~6단계)"
  [[ "$SKIP_DEPLOY" -eq 1 ]] || step_deploy
  [[ "$SKIP_WEBHOOKS" -eq 1 ]] || step_webhooks
  [[ "$SKIP_CONFIG" -eq 1 ]] || step_config
  echo
  echo "완료. 7단계(동작 확인)는 README.md 를 참고해 수동으로 확인하세요."
}

main
