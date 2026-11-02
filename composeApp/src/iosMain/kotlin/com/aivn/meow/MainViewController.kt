package com.aivn.meow

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.aivn.meow.config.AppConfig
import com.aivn.meow.config.loadAppConfig
import com.aivn.meow.github.GithubClient
import com.aivn.meow.platform.openUrl
import com.aivn.meow.schedule.openOutlookCalendar
import com.aivn.meow.ui.nav.AppScreen
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

/**
 * #165 아이폰 앱 진입점. iosApp(SwiftUI) 의 ContentView 가 이 뷰 컨트롤러를 띄운다.
 *
 * 1단계에서는 GitHub 토큰 · Microsoft 로그인을 넣을 곳이 없어(2단계), 토큰이 없으면 GitHub 화면에 연결 안내를 보여 주고
 * 인증이 필요 없는 구내 식당 화면으로 시작한다. 알림(3단계)은 아직 보내지 않는다.
 */
@Suppress("FunctionName", "unused") // Swift 에서 MainViewControllerKt.MainViewController() 로 부른다.
fun MainViewController(): UIViewController = ComposeUIViewController {
    val config = remember { loadAppConfig() }
    val githubConnected = config != null
    App(
        config = config ?: AppConfig(token = ""),
        githubClientFactory = { token -> GithubClient({ token }, Darwin) },
        onOpenUrl = ::openUrl,
        msEngine = Darwin,
        onOpenOutlookCalendar = { openOutlookCalendar() },
        compact = true,
        githubConnected = githubConnected,
        initialScreen = remember { startScreen(githubConnected) },
    )
}

/**
 * 시작 화면. 개발용으로 `MEOW_START_SCREEN` 환경변수(GITHUB · WEEKLY_REPORT · SCHEDULE · CAFETERIA)로 고를 수 있다
 * (`SIMCTL_CHILD_MEOW_START_SCREEN=SCHEDULE xcrun simctl launch …`).
 */
private fun startScreen(githubConnected: Boolean): AppScreen {
    val requested = NSProcessInfo.processInfo.environment["MEOW_START_SCREEN"] as? String
    requested?.let { name -> AppScreen.entries.firstOrNull { it.name == name.uppercase() }?.let { return it } }
    return if (githubConnected) AppScreen.GITHUB else AppScreen.CAFETERIA
}
