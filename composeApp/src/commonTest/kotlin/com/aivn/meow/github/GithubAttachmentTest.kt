package com.aivn.meow.github

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #172 토큰을 붙여 받을 첨부 이미지 주소 판별 · 리다이렉트 주소 해석. */
class GithubAttachmentTest {

    @Test
    fun userAttachmentUrlNeedsToken() {
        assertTrue(isGithubAttachmentUrl("https://github.com/user-attachments/assets/8a27231a-1257-402a-b8eb-1589a8589dab"))
    }

    @Test
    fun legacyRepoAssetUrlNeedsToken() {
        assertTrue(isGithubAttachmentUrl("https://github.com/Team-AIVN/meow/assets/12138424/8a27231a-1257"))
    }

    @Test
    fun otherGithubPagesDoNotGetToken() {
        assertFalse(isGithubAttachmentUrl("https://github.com/Team-AIVN/meow/pull/3"))
        assertFalse(isGithubAttachmentUrl("https://github.com/user-attachments"))
    }

    @Test
    fun nonGithubHostsNeverGetToken() {
        assertFalse(isGithubAttachmentUrl("https://evil.com/user-attachments/assets/abc"))
        assertFalse(isGithubAttachmentUrl("https://github.com.evil.com/user-attachments/assets/abc"))
        assertFalse(isGithubAttachmentUrl("https://evil.com/github.com/user-attachments/assets/abc"))
        assertFalse(isGithubAttachmentUrl("https://private-user-images.githubusercontent.com/1/a.png?jwt=x"))
        assertFalse(isGithubAttachmentUrl("https://user-images.githubusercontent.com/1/a.png"))
    }

    @Test
    fun plainHttpNeverGetsToken() {
        assertFalse(isGithubAttachmentUrl("http://github.com/user-attachments/assets/abc"))
    }

    @Test
    fun invalidUrlIsNotAttachment() {
        assertFalse(isGithubAttachmentUrl("not a url"))
        assertFalse(isGithubAttachmentUrl(""))
    }

    @Test
    fun absoluteRedirectIsKept() {
        val s3 = "https://github-production-user-asset-6210df.s3.amazonaws.com/1/a.jpg?X-Amz-Signature=abc&X-Amz-Expires=300"
        assertEquals(s3, resolveRedirectLocation("https://github.com/user-attachments/assets/abc", s3))
    }

    @Test
    fun relativeRedirectIsResolvedAgainstBase() {
        assertEquals(
            "https://github.com/login",
            resolveRedirectLocation("https://github.com/user-attachments/assets/abc", "/login"),
        )
    }

    @Test
    fun missingRedirectIsNull() {
        assertNull(resolveRedirectLocation("https://github.com/user-attachments/assets/abc", null))
        assertNull(resolveRedirectLocation("https://github.com/user-attachments/assets/abc", " "))
    }
}
