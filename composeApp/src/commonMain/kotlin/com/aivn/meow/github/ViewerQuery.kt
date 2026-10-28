package com.aivn.meow.github

import kotlinx.serialization.Serializable

@Serializable
data class ViewerLoginData(val viewer: ViewerLogin)

/** #165 토큰 확인용: 이 토큰의 GitHub 로그인. 토큰이 틀리면 [GithubApiException.Unauthorized]. */
suspend fun GithubClient.fetchViewerLogin(): String =
    query("query { viewer { login } }", ViewerLoginData.serializer()).viewer.login
