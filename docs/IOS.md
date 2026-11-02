# Meow 아이폰 앱 (#165)

데스크톱 Meow 와 같은 Compose Multiplatform 코드(`composeApp/src/commonMain`)를 아이폰에서 띄우는 앱이다.
App Store · TestFlight 없이 **Xcode 로 직접 빌드해 내 아이폰에 설치**하는 것만 지원한다.

## 구성

| 위치 | 내용 |
| --- | --- |
| `composeApp/build.gradle.kts` | `iosArm64` · `iosSimulatorArm64` 타깃, 정적 프레임워크 `ComposeApp` |
| `composeApp/src/iosMain` | iOS 구현(`actual`) · 진입점 `MainViewController()` |
| `iosApp/` | SwiftUI 앱 껍데기. `ContentView` 가 `MainViewController()` 를 띄운다 |
| `iosApp/project.yml` | Xcode 프로젝트 생성 스펙(XcodeGen). 고친 뒤 `cd iosApp && xcodegen generate` |
| `iosApp/Configuration/Config.xcconfig` | 팀 ID · 번들 ID(`com.aivn.meow`) · 앱 이름(Meow) |
| `scripts/make-ios-icon.swift` | `app_icon.png` 로 iOS AppIcon(1024, 불투명) 생성 |

Xcode 의 **Compile Kotlin Framework** 빌드 단계가 `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` 를 실행해
프레임워크를 만들기 때문에, Xcode 에서 실행만 해도 Kotlin 코드가 함께 빌드된다.

폰에서는 왼쪽 드로워 대신 **하단 탭 바**(GitHub · 주간 보고 · 출근 · 구내 식당)를 쓰고, 좁은 폭에 맞춰 한 열 · 16dp 여백으로 배치한다
(`LocalCompactLayout`). 데스크톱 화면은 바뀌지 않는다.

## 시뮬레이터에서 실행

```bash
# Kotlin 컴파일만 확인
./gradlew :composeApp:compileKotlinIosSimulatorArm64

# 시뮬레이터 이름 확인 후 빌드
xcrun simctl list devices available
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath iosApp/DerivedData build

# 부팅 · 설치 · 실행
xcrun simctl boot 'iPhone 17'
open -a Simulator
xcrun simctl install booted iosApp/DerivedData/Build/Products/Debug-iphonesimulator/Meow.app
xcrun simctl launch booted com.aivn.meow
```

Xcode 로 할 때는 `iosApp/iosApp.xcodeproj` 를 열고 시뮬레이터를 고른 뒤 ▶︎ 를 누르면 된다.

개발용으로 시작 화면을 고를 수 있다 (`GITHUB` · `WEEKLY_REPORT` · `SCHEDULE` · `CAFETERIA`, 설정 화면을 연 채로 시작하려면 `SETTINGS`).

```bash
SIMCTL_CHILD_MEOW_START_SCREEN=SCHEDULE xcrun simctl launch booted com.aivn.meow
```

기본 시작 화면은 GitHub 토큰이 있으면 GitHub, 없으면 구내 식당이다.

## 내 아이폰에 설치

1. 아이폰을 USB(또는 같은 Wi-Fi)로 Mac 에 연결하고, 아이폰에서 **이 컴퓨터를 신뢰**를 누른다.
2. 아이폰 **설정 › 개인정보 보호 및 보안 › 개발자 모드**를 켜고 재시동한다 (처음 한 번).
3. Xcode 에서 `iosApp/iosApp.xcodeproj` 를 열고, 위쪽 실행 대상에서 내 아이폰을 고른다.
4. `iosApp` 타깃 › **Signing & Capabilities** 에서 Team 이 `YCCX589JXZ`(Apple Development: 현진 양)인지 확인한다.
   자동 서명이라 프로비저닝 프로파일은 Xcode 가 만든다. 다른 팀으로 빌드하려면 `Config.xcconfig` 의 `TEAM_ID` 를 바꾼다.
5. ▶︎ 로 빌드 · 설치한다.
6. 처음 실행할 때 "신뢰하지 않는 개발자" 가 뜨면 아이폰 **설정 › 일반 › VPN 및 기기 관리**에서
   `Apple Development: 현진 양` 개발자 앱을 **신뢰**한 뒤 다시 실행한다.

무료(개인) 팀으로 서명한 앱은 7일, 유료 Apple Developer Program 팀은 1년 뒤 서명이 만료된다. 만료되면 Xcode 로 다시 설치하면 된다.

## 설정 · 로그인 (2단계)

각 화면 제목 줄의 ⚙︎ 나 "GitHub 연결하기" 버튼으로 **설정** 화면을 연다. 넣은 값은 모두 iOS **키체인**
(generic password, `AfterFirstUnlockThisDeviceOnly` — 백업 · 다른 기기로 옮겨지지 않음)에만 저장한다.

| 항목 | 키체인 서비스 | 내용 |
| --- | --- | --- |
| GitHub | `meow-github` | Personal Access Token. 저장 전에 `viewer { login }` 으로 확인한다 |
| Microsoft | `meow-ms-graph` (계정 = UPN) | refresh token (데스크톱 macOS 키체인과 같은 서비스 이름) |
| 실시간 알림 | `meow-supabase` (계정 `url` · `anon`) | Supabase URL · anon key. 없으면 실시간 구독 없이 주기 새로고침만 한다 |

값을 저장 · 삭제하면 앱이 새 설정으로 다시 뜬다(GitHub 화면). 데스크톱은 지금처럼 `~/.config/meow/token` · gh 토큰 ·
`supabase_url` · `supabase_anon` 파일을 쓰고 설정 화면이 없다.

### GitHub 토큰

- **Classic**: `repo`, `read:org` (설정 화면의 "GitHub 에서 토큰 만들기 ↗" 가 이 권한을 골라 둔 페이지를 연다)
- **Fine-grained**: 리소스 소유자 Team-AIVN · 모든 저장소, Pull requests · Contents 읽기/쓰기(머지 바), Issues · Metadata 읽기,
  조직 권한 Members 읽기. 조직 정책상 fine-grained 토큰은 조직 관리자 승인이 필요할 수 있다.
- GitHub OAuth **기기 로그인(Device Flow)** 은 OAuth 앱 client id 가 없어 아직 없다 (`SettingsScreen.kt` 의 TODO).

### Microsoft 로그인 — Entra 앱에 리디렉션 URI 추가 (한 번, 관리자)

아이폰은 루프백 서버 대신 `ASWebAuthenticationSession`(앱 안 Safari 로그인 창) + Authorization Code + PKCE 로 로그인하고,
리디렉션 URI 로 **`msauth.com.aivn.meow://auth`** 를 쓴다 (Info.plist `CFBundleURLTypes` 에 스킴 등록됨).
이 주소가 Entra 앱 등록에 없으면 로그인 페이지에서 `AADSTS50011 (redirect URI mismatch)` 오류가 난다.

1. <https://entra.microsoft.com> → **App registrations** → 앱 `7e970034-cbcd-4a89-9e40-0a3dc40024db` (Meow) 를 연다.
2. **Authentication** → **Add a platform** 에서 둘 중 하나:
   - **iOS / macOS** → Bundle ID `com.aivn.meow` 입력 → Configure. (`msauth.com.aivn.meow://auth` 가 자동으로 만들어진다)
   - 또는 **Mobile and desktop applications** → Custom redirect URIs 에 `msauth.com.aivn.meow://auth` 입력.
3. **Save**. 같은 화면의 *Allow public client flows* 등 기존 데스크톱 설정(`http://localhost`)은 그대로 둔다.

권한(scope)은 데스크톱과 같다: `User.Read`, `Mail.Read`, `Files.ReadWrite.All`, `offline_access`
(출근 화면의 캘린더 읽기는 데스크톱과 마찬가지로 앱에 동의된 Graph 권한으로 들어온다).

### 주간 보고 문서 읽기 · 반영

데스크톱의 JDK zip · DOM 대신 아이폰은 시스템 zlib 로 zip 을 읽고 다시 묶고(`iosMain/platform/ZipArchive.kt`),
`word/document.xml` 은 공통 순수 Kotlin 구현(`commonMain/weekly/DocxXml.kt`)으로 첫 표를 읽고 내 행 셀만 갈아 끼운다.
그래서 **문서에 반영까지 아이폰에서 된다.** 두 구현이 같은 결과를 내는지 `desktopTest` 의 `DocxXmlTest` 가 비교한다.

## 단계별로 되는 것

| 화면 · 기능 | 지금 (2단계) | 예정 |
| --- | --- | --- |
| 구내 식당 | 전부 동작 (카카오 채널 · 이미지 캐시 · 라이트박스) | — |
| GitHub | 설정에서 토큰을 넣으면 대시보드 · 상세(전체 화면 시트) · 즐겨찾기 칩 줄 · 머지 바 · (Supabase 설정 시) 실시간 반영 | Device Flow 로그인 (client id 필요) |
| 주간 보고 | Microsoft 로그인 · 문서 찾기 · 내 행 보기 · 초안 · 문서에 반영 | — |
| 출근 | Microsoft 로그인 뒤 TeamAIVN 캘린더 주간 부재 | — |
| 알림 | 보내지 않음 | 3단계: 푸시 알림 |

아이폰에서 여전히 다른 부분 (`composeApp/src/iosMain`):

- **실적 요약**: Claude CLI 가 없어 늘 PR · 이슈 제목 초안으로 폴백한다.
- **Outlook 일정 ↗**: `ms-outlook://events` 로 Outlook 앱을 열고, 없으면 웹 캘린더를 Safari 로 연다.
- **알림 · 메뉴 바 · 알림 헬퍼(osascript / Meow Notifier.app)**: 데스크톱 전용.
- 개발용으로 앱 Documents/meow 의 `token` · `supabase_url` · `supabase_anon` 파일도 (키체인 값이 없을 때) 읽는다.

설정 · 캐시 파일 위치는 데스크톱의 `~/.config/meow` · `~/.cache/meow` 대신 앱 샌드박스의 `Documents/meow` · `Caches/meow` 이다.
