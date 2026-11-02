package com.aivn.meow.schedule

import com.aivn.meow.platform.openUrl
import com.aivn.meow.ui.schedule.OUTLOOK_WEB_CALENDAR_URL
import com.aivn.meow.ui.schedule.OutlookOpenResult
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Outlook for iOS 의 일정 화면 URL scheme. */
private const val OUTLOOK_CALENDAR_SCHEME = "ms-outlook://events"

/** #165 아이폰: Outlook 앱이 있으면 일정 화면으로, 없으면 웹 캘린더를 Safari 로 연다. */
internal suspend fun openOutlookCalendar(): OutlookOpenResult = suspendCancellableCoroutine { cont ->
    openUrl(OUTLOOK_CALENDAR_SCHEME) { opened ->
        val result = if (opened) {
            OutlookOpenResult.Calendar
        } else {
            openUrl(OUTLOOK_WEB_CALENDAR_URL)
            OutlookOpenResult.Browser
        }
        if (cont.isActive) cont.resume(result)
    }
}
