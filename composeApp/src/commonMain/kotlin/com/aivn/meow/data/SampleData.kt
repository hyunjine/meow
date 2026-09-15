package com.aivn.meow.data

import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.Label
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors

val samplePullRequests: List<PullRequest> = listOf(
    PullRequest(
        repo = "chatsea-web",
        repoColor = MeowColors.Brand,
        number = 812,
        title = "Feat: 마이 페이지 리뷰 대기 위젯 추가",
        author = "eunji",
        authorInitials = "EJ",
        relativeTime = "3일 전 업데이트",
        isDraft = false,
        ci = CiStatus.Pass,
        labels = listOf(
            Label("frontend", MeowColors.Brand),
            Label("priority: high", MeowColors.Error),
        ),
        url = "https://github.com/Team-AIVN/chatsea-web/pull/812",
    ),
    PullRequest(
        repo = "chatsea-api",
        repoColor = MeowColors.Violet,
        number = 934,
        title = "Fix: 메시지 스레드 페이징 리셋 이슈",
        author = "minsu",
        authorInitials = "MS",
        relativeTime = "1일 전 업데이트",
        isDraft = false,
        ci = CiStatus.Fail,
        labels = listOf(
            Label("backend", MeowColors.Violet),
            Label("bug", MeowColors.Error),
        ),
        url = "https://github.com/Team-AIVN/chatsea-api/pull/934",
    ),
    PullRequest(
        repo = "chatsea-design-system",
        repoColor = MeowColors.Teal,
        number = 145,
        title = "Chore: Pill 컴포넌트 variants 리팩터링",
        author = "haneul",
        authorInitials = "HN",
        relativeTime = "5시간 전 업데이트",
        isDraft = true,
        ci = CiStatus.Pending,
        labels = listOf(Label("design-system", MeowColors.Teal)),
        url = "https://github.com/Team-AIVN/chatsea-design-system/pull/145",
    ),
    PullRequest(
        repo = "chatsea-mobile",
        repoColor = MeowColors.Violet,
        number = 421,
        title = "Refactor: 홈 피드 로딩 상태를 스켈레톤으로 교체",
        author = "yuna",
        authorInitials = "YN",
        relativeTime = "2일 전 업데이트",
        isDraft = false,
        ci = CiStatus.Pass,
        labels = listOf(
            Label("mobile", MeowColors.Violet),
            Label("UX", MeowColors.Teal),
        ),
        url = "https://github.com/Team-AIVN/chatsea-mobile/pull/421",
    ),
    PullRequest(
        repo = "chatsea-web",
        repoColor = MeowColors.Brand,
        number = 815,
        title = "Docs: 온보딩 가이드 v2 반영",
        author = "sohyun",
        authorInitials = "SH",
        relativeTime = "방금 업데이트",
        isDraft = false,
        ci = CiStatus.Pending,
        labels = listOf(Label("docs", MeowColors.Grey)),
        url = "https://github.com/Team-AIVN/chatsea-web/pull/815",
    ),
)
