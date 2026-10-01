package com.aivn.meow.ms

import com.aivn.meow.config.readConfigFile

/**
 * 사내 Entra 등록 앱(퍼블릭 클라이언트). 클라이언트 · 테넌트 ID 는 비밀이 아니다.
 * `~/.config/meow/ms_client_id` · `ms_tenant_id` 파일이 있으면 그 값으로 덮어쓴다.
 */
data class MsAppConfig(
    val clientId: String,
    val tenantId: String,
    val scopes: List<String> = DEFAULT_SCOPES,
) {
    val authority: String get() = "https://login.microsoftonline.com/$tenantId/oauth2/v2.0"

    companion object {
        const val DEFAULT_CLIENT_ID = "7e970034-cbcd-4a89-9e40-0a3dc40024db"
        const val DEFAULT_TENANT_ID = "2203e2d8-9b7e-4547-bc98-1be950010851"
        val DEFAULT_SCOPES = listOf("User.Read", "Mail.Read", "Files.ReadWrite.All", "offline_access")

        fun load(): MsAppConfig = MsAppConfig(
            clientId = readConfigFile("ms_client_id") ?: DEFAULT_CLIENT_ID,
            tenantId = readConfigFile("ms_tenant_id") ?: DEFAULT_TENANT_ID,
        )
    }
}
