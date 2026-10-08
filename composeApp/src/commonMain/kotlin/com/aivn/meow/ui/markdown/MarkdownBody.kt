package com.aivn.meow.ui.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.loadImageBitmap
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding

/**
 * #119 펼친 카드 본문을 GitHub 마크다운으로 그린다. [markdown] 은 GitHub 원문(`body`)이고,
 * [preprocessGithubMarkdown] 으로 HTML 주석 · 태그를 정리한 뒤 렌더링한다.
 * 링크는 LocalUriHandler(대시보드에서 onOpenUrl 로 연결)로 연다.
 */
@Composable
internal fun MarkdownBody(markdown: String, modifier: Modifier = Modifier) {
    val content = remember(markdown) { preprocessGithubMarkdown(markdown) }
    if (content.isEmpty()) return

    val body = TextStyle(color = MeowColors.TextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
    val heading = body.copy(color = MeowColors.TextPrimary, fontWeight = FontWeight.Bold)
    val code = body.copy(color = MeowColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp)

    Markdown(
        content = content,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(
            text = MeowColors.TextSecondary,
            codeBackground = MeowColors.TextPrimary.copy(alpha = 0.05f),
            inlineCodeBackground = MeowColors.TextPrimary.copy(alpha = 0.07f),
            dividerColor = MeowColors.GlassBorder,
            tableBackground = MeowColors.TextPrimary.copy(alpha = 0.03f),
        ),
        typography = markdownTypography(
            h1 = heading.copy(fontSize = 17.sp, lineHeight = 24.sp),
            h2 = heading.copy(fontSize = 16.sp, lineHeight = 22.sp),
            h3 = heading.copy(fontSize = 15.sp, lineHeight = 21.sp),
            h4 = heading.copy(fontSize = 14.sp),
            h5 = heading.copy(fontSize = 13.sp),
            h6 = heading.copy(fontSize = 13.sp, color = MeowColors.TextTertiary),
            text = body,
            code = code,
            inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            quote = body.copy(color = MeowColors.TextTertiary, fontStyle = FontStyle.Italic),
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            textLink = TextLinkStyles(style = SpanStyle(color = MeowColors.Brand, fontWeight = FontWeight.SemiBold)),
            table = body.copy(fontSize = 12.sp, lineHeight = 18.sp),
        ),
        padding = markdownPadding(
            block = 3.dp,
            list = 2.dp,
            listItemTop = 1.dp,
            listItemBottom = 1.dp,
            listIndent = 6.dp,
            codeBlock = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            blockQuote = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            blockQuoteText = PaddingValues(vertical = 2.dp),
        ),
        dimens = markdownDimens(
            codeBackgroundCornerSize = 8.dp,
            blockQuoteThickness = 3.dp,
            tableCellWidth = 140.dp,
            tableCellPadding = 8.dp,
        ),
        imageTransformer = UrlImageTransformer,
        components = markdownComponents(
            // 읽기 전용 체크박스 — 머티리얼 체크박스 대신 ☐ / ☑ 기호.
            checkbox = { model ->
                MarkdownCheckBox(model.content, model.node, model.typography.text) { checked, mod ->
                    Text(
                        text = if (checked) "☑" else "☐",
                        modifier = mod,
                        style = model.typography.text.copy(
                            color = if (checked) MeowColors.Brand else MeowColors.TextTertiary,
                        ),
                    )
                }
            },
        ),
    )
}

/** 이미지 주소를 기존 [loadImageBitmap] 으로 받아 그린다. 실패하면 깨진 이미지 아이콘을 보여준다. */
private object UrlImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val result by produceState<ImageResult>(ImageCache.get(link) ?: ImageResult.Loading, link) {
            if (value !is ImageResult.Loaded) {
                value = loadImageBitmap(link)
                    ?.let { ImageResult.Loaded(it).also { loaded -> ImageCache.put(link, loaded) } }
                    ?: ImageResult.Failed
            }
        }
        return when (val r = result) {
            ImageResult.Loading -> null
            is ImageResult.Loaded -> ImageData(painter = remember(r.bitmap) { BitmapPainter(r.bitmap) })
            ImageResult.Failed -> ImageData(
                painter = rememberVectorPainter(Icons.Outlined.BrokenImage),
                contentDescription = "이미지를 불러오지 못했습니다",
                colorFilter = ColorFilter.tint(MeowColors.TextTertiary),
            )
        }
    }
}

private sealed interface ImageResult {
    data object Loading : ImageResult
    data object Failed : ImageResult
    class Loaded(val bitmap: ImageBitmap) : ImageResult
}

/** 펼침/접힘마다 다시 받지 않도록 최근 이미지 몇 장을 기억한다. 컴포지션(UI) 스레드에서만 접근한다. */
private object ImageCache {
    private const val MAX_ENTRIES = 32
    private val entries = LinkedHashMap<String, ImageResult.Loaded>()

    fun get(link: String): ImageResult.Loaded? = entries[link]

    fun put(link: String, value: ImageResult.Loaded) {
        entries.remove(link)
        entries[link] = value
        while (entries.size > MAX_ENTRIES) entries.remove(entries.keys.first())
    }
}
