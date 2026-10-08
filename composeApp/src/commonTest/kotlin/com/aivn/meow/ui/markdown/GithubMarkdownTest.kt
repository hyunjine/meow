package com.aivn.meow.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GithubMarkdownTest {

    @Test
    fun stripsSingleLineHtmlComment() {
        assertEquals("## 작업 내용\n\n- 로그인 수정", preprocessGithubMarkdown("## 작업 내용\n<!-- 무엇을 했는지 적어주세요 -->\n\n- 로그인 수정"))
    }

    @Test
    fun stripsMultiLineHtmlCommentAndCollapsesBlankLines() {
        val raw = "본문\n\n<!--\n템플릿 안내\n여러 줄\n-->\n\n\n\n끝"
        assertEquals("본문\n\n끝", preprocessGithubMarkdown(raw))
    }

    @Test
    fun stripsUnterminatedHtmlCommentToEnd() {
        assertEquals("본문", preprocessGithubMarkdown("본문\n<!-- 닫히지 않은 주석"))
    }

    @Test
    fun convertsImgTagToMarkdownImage() {
        val raw = """<img width="300" alt="스크린샷" src="https://github.com/user-attachments/assets/abc" />"""
        assertEquals("![스크린샷](https://github.com/user-attachments/assets/abc)", preprocessGithubMarkdown(raw))
    }

    @Test
    fun convertsImgTagWithSingleQuotesAndNoAlt() {
        assertEquals("앞 ![](https://x.dev/a.png) 뒤", preprocessGithubMarkdown("앞 <IMG src='https://x.dev/a.png'> 뒤"))
    }

    @Test
    fun dropsImgTagWithoutSrc() {
        assertEquals("텍스트", preprocessGithubMarkdown("텍스트<img alt=\"x\">"))
    }

    @Test
    fun wrapsImageUrlWithSpacesInAngleBrackets() {
        assertEquals("![a](<https://x.dev/a b.png>)", preprocessGithubMarkdown("<img alt=\"a\" src=\"https://x.dev/a b.png\">"))
    }

    @Test
    fun convertsAnchorAndImageInsideAnchor() {
        assertEquals("[문서](https://x.dev)", preprocessGithubMarkdown("<a href=\"https://x.dev\">문서</a>"))
        assertEquals(
            "[![로고](https://x.dev/l.png)](https://x.dev)",
            preprocessGithubMarkdown("<a href=\"https://x.dev\"><img src=\"https://x.dev/l.png\" alt=\"로고\"></a>"),
        )
    }

    @Test
    fun convertsDetailsSummaryAndDropsUnknownTags() {
        val raw = "<details>\n<summary>로그 보기</summary>\n\n<span class=\"x\">내용</span>\n</details>"
        assertEquals("**로그 보기**\n\n내용", preprocessGithubMarkdown(raw))
    }

    @Test
    fun convertsHeadingAndInlineFormattingTags() {
        assertEquals("## 제목\n\n**굵게** *기울임* `코드`", preprocessGithubMarkdown("<h2 align=\"center\">제목</h2><b>굵게</b> <em>기울임</em> <code>코드</code>"))
    }

    @Test
    fun convertsBrToHardBreakButSpaceInsideTableRow() {
        assertEquals("가  \n나", preprocessGithubMarkdown("가<br>나"))
        assertEquals("| a b | c |", preprocessGithubMarkdown("| a<br/>b | c |"))
    }

    @Test
    fun keepsFencedCodeBlockUntouched() {
        val raw = "설명 <!-- 주석 -->\n\n```html\n<!-- 코드 속 주석 -->\n<img src=\"a.png\">\n\n\n\n<b>x</b>\n```\n\n<b>밖</b>"
        assertEquals(
            "설명 \n\n```html\n<!-- 코드 속 주석 -->\n<img src=\"a.png\">\n\n\n\n<b>x</b>\n```\n\n**밖**",
            preprocessGithubMarkdown(raw),
        )
    }

    @Test
    fun keepsInlineCodeUntouched() {
        assertEquals("태그 `<img src=\"a\">` 와 `<!-- x -->`", preprocessGithubMarkdown("태그 `<img src=\"a\">` 와 `<!-- x -->`"))
    }

    @Test
    fun keepsAutolinksAndPlainMarkdown() {
        val raw = "- [x] 완료\n- [ ] 남음\n\n> 인용 <https://x.dev> a < b"
        assertEquals(raw, preprocessGithubMarkdown(raw))
    }

    @Test
    fun normalizesCrLf() {
        assertEquals("a\nb", preprocessGithubMarkdown("a\r\nb\r\n"))
    }

    @Test
    fun plainTextDropsMarkdownSyntax() {
        val plain = githubMarkdownToPlainText("## 제목\n<!-- 안내 -->\n- [x] **완료** [링크](https://x.dev)\n> 인용 `code`\n<img alt=\"그림\" src=\"https://x.dev/a.png\">")
        assertEquals("제목\n\n완료 링크\n인용 code\n그림", plain)
        assertFalse("<" in plain)
        assertTrue(plain.isNotEmpty())
    }
}
