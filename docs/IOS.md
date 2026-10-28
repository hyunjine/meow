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

폰에서는 왼쪽 드로워 대신 **하단 탭 바**(GitHub · 주간 보고 · 일정 · 구내 식당)를 쓰고, 좁은 폭에 맞춰 한 열 · 16dp 여백으로 배치한다
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

개발용으로 시작 화면을 고를 수 있다 (`GITHUB` · `WEEKLY_REPORT` · `SCHEDULE` · `CAFETERIA`).

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

## 1단계에서 되는 것 · 안 되는 것

| 화면 · 기능 | 1단계 (지금) | 예정 |
| --- | --- | --- |
| 구내 식당 | 전부 동작 (카카오 채널 · 이미지 캐시 · 라이트박스) | — |
| GitHub | 토큰이 없으면 "설정에서 연결해 주세요 (2단계 예정)" 안내. 토큰이 있으면 대시보드 · 상세(전체 화면 시트) · 즐겨찾기 칩 줄 | 2단계: 토큰 입력 / 기기 로그인 |
| 주간 보고 · 일정 | Microsoft 계정 연결 안내만 표시 | 2단계: iOS 리디렉션 로그인 · 키체인 저장 |
| 알림 | 보내지 않음 | 3단계: 푸시 알림 |

1단계에서 iOS 쪽 구현을 비워 둔 부분 (`composeApp/src/iosMain`):

- **GitHub 토큰**: 앱 Documents/meow/token 파일만 읽는다 (넣는 화면 없음 → 2단계).
- **Microsoft 로그인**: 루프백 서버가 없어 `msLoginSupported = false`, refresh token 보관소는 빈 구현 (2단계에서 키체인).
- **주간 보고 docx 편집**: 데스크톱 전용 (JDK zip · XML). iOS 에서는 오류를 낸다 (로그인이 없어 실제로는 불리지 않음).
- **실적 요약**: Claude CLI 가 없어 늘 PR · 이슈 제목 초안으로 폴백한다.
- **Outlook 일정 ↗**: `ms-outlook://events` 로 Outlook 앱을 열고, 없으면 웹 캘린더를 Safari 로 연다.
- **알림 · 메뉴 바 · 알림 헬퍼(osascript / Meow Notifier.app)**: 데스크톱 전용.

설정 · 캐시 파일 위치는 데스크톱의 `~/.config/meow` · `~/.cache/meow` 대신 앱 샌드박스의 `Documents/meow` · `Caches/meow` 이다.
