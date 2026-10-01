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
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
        }
    }
}

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

            macOS {
                bundleID = "com.aivn.meow"
                dockName = "Meow"
                // scripts/make-icns.sh 로 app_icon.png(1024) 에서 생성
                iconFile.set(project.file("icons/meow.icns"))
            }
        }
    }
}
