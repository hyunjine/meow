import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.supabase.realtime)
            implementation(libs.supabase.postgrest)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
    }
}

// macOS 알림용 헬퍼 앱(Meow Notifier.app). 앱 리소스 디렉터리(macos/)에 만들어 두면 run/패키징 모두에 포함되고,
// 런타임에 compose.application.resources.dir 에서 찾아 ~/Library/Application Support/meow 로 설치한다.
val notifierResourcesDir = layout.buildDirectory.dir("notifier-resources")
val buildMacNotifier by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds Meow Notifier.app (Swift, UserNotifications) for macOS notifications."
    val notifierSrc = file("notifier")
    val icns = file("icons/meow.icns")
    val appDir = notifierResourcesDir.get().dir("macos/Meow Notifier.app").asFile
    inputs.dir(notifierSrc)
    inputs.file(icns)
    outputs.dir(appDir)
    onlyIf { System.getProperty("os.name").contains("Mac", ignoreCase = true) }
    commandLine("bash", File(notifierSrc, "build.sh").absolutePath, appDir.absolutePath, icns.absolutePath)
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(buildMacNotifier) }

compose.desktop {
    application {
        mainClass = "com.aivn.meow.MainKt"
        // 메뉴 바(트레이) 아이콘을 템플릿 이미지로 — 다크/라이트 메뉴 바에 맞춰 색이 바뀐다 (JDK 21+).
        jvmArgs += listOf("-Dapple.awt.enableTemplateImages=true")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Meow"
            packageVersion = "1.2.0"
            description = "Personal PR review dashboard for Team-AIVN"
            copyright = "© 2026 aivn"
            vendor = "aivn"
            appResourcesRootDir.set(notifierResourcesDir)

            macOS {
                bundleID = "com.aivn.meow"
                dockName = "Meow"
                // scripts/make-icns.sh 로 app_icon.png(1024) 에서 생성
                iconFile.set(project.file("icons/meow.icns"))
            }
        }
    }
}
