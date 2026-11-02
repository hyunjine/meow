package com.aivn.meow.weekly

/** #165 iOS 에는 Claude Code CLI 가 없으니 요약하지 않는다 → PR · 이슈 제목 초안으로 폴백. */
actual fun defaultDraftSummarizer(): DraftSummarizer = DraftSummarizer { null }
