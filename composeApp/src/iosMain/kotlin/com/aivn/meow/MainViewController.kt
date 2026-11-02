package com.aivn.meow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.aivn.meow.config.AppConfig
import com.aivn.meow.config.IosAccountSettings
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
 * GitHub 토큰 · Supabase 설정은 앱 설정 화면에서 키체인에 넣는다([IosAccountSettings]). 값이 바뀌면 App 을 통째로 다시 만들어
 * 대시보드 · 실시간 구독이 새 설정으로 다시 시작하게 한다(이전 조회 루프는 컴포지션과 함께 취소된다).
 * 알림(3단계)은 아직 보내지 않는다.
 */
@Suppress("FunctionName", "unused") // Swift 에서 MainViewControllerKt.MainViewController() 로 부른다.
fun MainViewController(): UIViewController = ComposeUIViewController {
    var generation by remember { mutableStateOf(0) }
    remember {
        IosAccountSettings.onChanged = { generation++ }
    }
    key(generation) {
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
            // 설정을 바꾼 뒤 다시 뜰 때는 GitHub 화면으로 (연결 결과를 바로 보이게).
            initialScreen = remember { if (generation > 0) AppScreen.GITHUB else startScreen(githubConnected) },
            accountSettings = IosAccountSettings,
            initialShowSettings = remember { generation == 0 && requestedStartScreen() == "SETTINGS" },
        )
    }
}

/** 개발용 `MEOW_START_SCREEN` 환경변수 (대문자). */
private fun requestedStartScreen(): String? =
    (NSProcessInfo.processInfo.environment["MEOW_START_SCREEN"] as? String)?.uppercase()

/**
 * 시작 화면. 개발용으로 `MEOW_START_SCREEN` 환경변수(GITHUB · WEEKLY_REPORT · SCHEDULE · CAFETERIA, 설정 화면은 SETTINGS)로
 * 고를 수 있다 (`SIMCTL_CHILD_MEOW_START_SCREEN=SCHEDULE xcrun simctl launch …`).
 */
private fun startScreen(githubConnected: Boolean): AppScreen {
    if (requestedStartScreen() == "SETTINGS") return AppScreen.GITHUB
    val requested = requestedStartScreen()
    requested?.let { name -> AppScreen.entries.firstOrNull { it.name == name.uppercase() }?.let { return it } }
    return if (githubConnected) AppScreen.GITHUB else AppScreen.CAFETERIA
}
