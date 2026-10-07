package com.aivn.meow.ui.schedule

/** Outlook 앱이 없을 때 대신 여는 Outlook 웹 캘린더(주 보기). */
const val OUTLOOK_WEB_CALENDAR_URL = "https://outlook.office.com/calendar/view/week"

/** "Outlook 일정 ↗" 버튼을 눌렀을 때 실제로 열린 곳. */
enum class OutlookOpenResult {
    /** Outlook 앱을 켜고 일정(⌘2) 화면으로 전환했다. */
    Calendar,

    /** 손쉬운 사용 권한이 없어 키 입력을 못 보내고 Outlook 앱만 켰다. 권한 안내를 보여 준다. */
    AppOnlyNeedsPermission,

    /** Outlook 앱만 켰다 (권한 외 이유로 일정 전환 실패). */
    AppOnly,

    /** Outlook 앱이 없어 브라우저로 웹 캘린더를 열었다. */
    Browser,
}
