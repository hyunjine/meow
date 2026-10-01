# 브랜치 · 배포 전략

## 한눈에 보기

```
feat/#41-tab-layout ─┐
fix/#43-margin ──────┼─PR─▶ dev/v1.1.0 ──PR─▶ release ──(자동)──▶ 태그 v1.1.0 + GitHub Release
refactor/#50-... ────┘
```

| 브랜치 | 역할 | 어디로 PR |
|---|---|---|
| `<태그>/#<이슈번호>-<내용>` | 작업 브랜치. 이슈 하나 = 브랜치 하나 | 현재 `dev/vX.Y.Z` |
| `dev/vX.Y.Z` | 다음 배포 버전의 개발 브랜치 (기본 브랜치) | `release` |
| `release` | 배포 브랜치. 머지될 때마다 릴리즈가 만들어진다 | — |

## 1. 작업 브랜치

- 이름: `<태그>/#<이슈번호>-<내용>` — 내용은 소문자 · 숫자 · `-` `.` `_` 만.
  - 예: `feat/#41-tab-layout`, `fix/#43-responsive-margin`, `refactor/#60-section-loader`
  - 태그: `feat` `fix` `refactor` `chore` `docs` `style` `perf` `test` `build` `ci` `setting`
- 현재 `dev/vX.Y.Z` 에서 분기하고, PR 도 그 dev 브랜치로 올린다.
- PR 에는 이슈와 같은 라벨(💻 Feat / 🛠️ Fix / ⚙️ Setting 등)을 붙인다 → 릴리즈 노트 분류에 쓰인다.
- 커밋 메시지는 `[#이슈번호] <접두사>: <요약>`.

> `#` 은 단어 중간이라 터미널에서 그대로 써도 되지만, 따옴표로 감싸면 안전하다: `git checkout -b "feat/#41-tab-layout"`

## 2. dev 브랜치 (`dev/vX.Y.Z`)

- 다음에 배포할 버전 하나당 하나. 저장소의 **기본 브랜치**로 둔다.
- 새 버전을 시작할 때 `release` 에서 분기하고, 첫 커밋으로 `composeApp/build.gradle.kts` 의 `packageVersion` 을 같은 버전으로 올린다 (release PR 검사에서 확인함).
- 버전 규칙 (SemVer)
  - `X` major: 화면 구조 · 데이터가 크게 바뀌어 이전과 호환되지 않을 때
  - `Y` minor: 새 기능
  - `Z` patch: 버그 수정만

## 3. 배포 (`dev/vX.Y.Z` → `release`)

1. `dev/vX.Y.Z` → `release` PR 을 연다. 라벨 `🚀 Release` 를 붙이면 릴리즈 노트에서 이 PR 은 빠진다.
2. **Branch guard** 검사가 통과해야 머지할 수 있다.
   - 출발 브랜치가 `dev/vX.Y.Z` 형식인지
   - `packageVersion` 이 `X.Y.Z` 와 같은지
   - `vX.Y.Z` 태그가 아직 없는지
3. 머지되면 **Release** 워크플로가
   - 브랜치 이름에서 `vX.Y.Z` 를 파싱해 머지 커밋에 태그를 만들고
   - 직전 릴리즈 이후 머지된 PR 들을 모아 GitHub Release 노트를 게시한다.
4. 다음 버전 `dev/vX.Y.Z+1` (또는 minor/major) 을 `release` 에서 새로 만들고 기본 브랜치를 옮긴다. 다 쓴 dev 브랜치는 지워도 된다.

> 앱 파일(.dmg)은 릴리즈에 첨부하지 않는다. 새 버전은 `git pull` 후 `./gradlew :composeApp:run` 또는 `packageDmg` 로 직접 빌드한다.

## 4. 급한 수정 (hotfix)

`release` 로는 dev 브랜치만 PR 할 수 있으므로, 급한 수정도 같은 흐름을 따른다.

1. `release` 에서 `dev/vX.Y.(Z+1)` 을 만들고 `packageVersion` 을 올린다.
2. `fix/#<이슈>-...` 를 그 dev 로 PR → 머지.
3. `dev/vX.Y.(Z+1)` → `release` PR → 자동 릴리즈.
4. 진행 중이던 다음 버전 dev 브랜치에 `release` 를 머지해 수정 사항을 반영한다.

## 5. 저장소 보호 규칙

| 대상 | 규칙 |
|---|---|
| `release` | PR 로만 변경 · `branch-guard` 통과 필수 · 강제 푸시 · 삭제 금지 |
| `dev/v*` | PR 로만 변경 · `branch-guard` 통과 필수 · 강제 푸시 금지 |
