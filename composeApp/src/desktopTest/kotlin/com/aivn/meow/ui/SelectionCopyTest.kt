package com.aivn.meow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.compose.ui.platform.asAwtTransferable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.click
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import com.aivn.meow.ui.common.consumeClicks
import com.aivn.meow.ui.detail.DetailItem
import com.aivn.meow.ui.detail.DetailModal
import com.aivn.meow.ui.markdown.MarkdownBody
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시스템 클립보드 대신 쓰는 가짜. setClipEntry 로 들어온 글자를 기록한다. */
@OptIn(ExperimentalComposeUiApi::class)
private class FakeClipboard : Clipboard {
    var text: String? = null
    override suspend fun getClipEntry(): ClipEntry? = null
    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        text = clipEntry?.asAwtTransferable?.getTransferData(DataFlavor.stringFlavor) as? String
    }
    override val nativeClipboard: NativeClipboard get() = Any()
}

/**
 * #161 SelectionContainer 로 고른 글자가 ⌘C 로 복사되는지, 선택 뒤에도 단축키가 동작하는지 확인한다.
 *
 * 원인: 데스크톱 `clickable` 은 누르는 순간 포커스를 가져간다(isRequestFocusOnClickEnabled = true).
 * 선택 영역을 감싼 `clickable(onClick = {})` 이 포커스를 뺏으면 ⌘C 가 SelectionContainer 의 복사 처리에 닿지 않는다.
 */
@OptIn(ExperimentalTestApi::class)
class SelectionCopyTest {

    private fun ComposeUiTest.setContentWith(clipboard: FakeClipboard, content: @Composable () -> Unit) {
        setContent { CompositionLocalProvider(LocalClipboard provides clipboard) { content() } }
        waitForIdle()
    }

    /** 글자 왼쪽 끝에서 오른쪽 끝까지 마우스로 끌어 고른다. */
    private fun ComposeUiTest.dragSelect(node: SemanticsNodeInteraction) {
        node.performMouseInput {
            moveTo(centerLeft + Offset(1f, 0f))
            press()
            moveTo(center)
            moveTo(centerRight - Offset(1f, 0f))
            release()
        }
        waitForIdle()
    }

    private fun ComposeUiTest.pressCopy(node: SemanticsNodeInteraction) {
        node.performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.C) } }
        waitForIdle()
    }

    @Test
    fun `clickable 로 감싼 선택 영역은 누를 때 포커스를 뺏겨 복사되지 않는다`() = runComposeUiTest {
        val clipboard = FakeClipboard()
        setContentWith(clipboard) {
            Box(Modifier.clickable(interactionSource = null, indication = null, onClick = {})) {
                SelectionContainer { Text("Hello selectable world") }
            }
        }
        val text = onNodeWithText("Hello selectable world", useUnmergedTree = true)
        dragSelect(text)
        pressCopy(text)
        // 원인 재현: 바깥 clickable 이 포커스를 가져가 ⌘C 가 복사로 이어지지 않는다.
        assertNull(clipboard.text)
    }

    @Test
    fun `consumeClicks 로 감싼 선택 영역은 복사된다`() = runComposeUiTest {
        val clipboard = FakeClipboard()
        var outerClicks = 0
        setContentWith(clipboard) {
            Box(Modifier.clickable { outerClicks++ }) {
                Box(Modifier.consumeClicks()) {
                    SelectionContainer { Text("Hello selectable world") }
                }
            }
        }
        val text = onNodeWithText("Hello selectable world", useUnmergedTree = true)
        dragSelect(text)
        pressCopy(text)
        assertEquals("Hello selectable world", clipboard.text)
        // 누름은 바깥으로 전달되지 않는다 (모달 배경 클릭으로 닫히지 않음).
        text.performMouseInput { click(center) }
        waitForIdle()
        assertEquals(0, outerClicks)
    }

    @Test
    fun `대시보드 구조 - 고른 뒤 복사되고 단축키도 계속 동작한다`() = runComposeUiTest {
        val clipboard = FakeClipboard()
        var down = 0
        var tab = -1
        setContentWith(clipboard) {
            // Dashboard: focusRequester + focusable + onPreviewKeyEvent, 포커스가 비면 다시 가져온다.
            val focusRequester = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            LaunchedEffect(hasFocus) { if (!hasFocus) focusRequester.requestFocus() }
            Column(
                Modifier.fillMaxSize()
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when {
                            event.isMetaPressed && event.key == Key.Two -> { tab = 1; true }
                            event.key == Key.J -> { down++; true }
                            else -> false
                        }
                    }
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer { Text("불러오지 못했어요 · timeout") }
            }
        }
        val text = onNodeWithText("불러오지 못했어요 · timeout", useUnmergedTree = true)
        dragSelect(text)
        pressCopy(text)
        assertEquals("불러오지 못했어요 · timeout", clipboard.text)
        text.performKeyInput { pressKey(Key.J) }
        text.performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Two) } }
        waitForIdle()
        assertEquals(1, down)
        assertEquals(1, tab)
    }

    @Test
    fun `고른 글자에서 우클릭하면 복사 메뉴가 뜨고 누르면 복사된다`() = runComposeUiTest {
        val clipboard = FakeClipboard()
        setContentWith(clipboard) {
            Box(Modifier.consumeClicks()) {
                SelectionContainer { Text("Hello selectable world") }
            }
        }
        val text = onNodeWithText("Hello selectable world", useUnmergedTree = true)
        dragSelect(text)
        text.performMouseInput { rightClick(center) }
        waitForIdle()
        // 메뉴 문구는 시스템 언어를 따른다 (Copy / 복사).
        val copyItem = listOf("Copy", "복사").firstNotNullOfOrNull { label ->
            onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let { label }
        }
        assertTrue(copyItem != null, "context menu copy item not shown")
        onAllNodesWithText(copyItem, useUnmergedTree = true)[0].performMouseInput { click(center) }
        waitForIdle()
        assertEquals("Hello selectable world", clipboard.text)
    }

    @Test
    fun `마크다운 본문도 골라서 복사된다`() = runComposeUiTest {
        val clipboard = FakeClipboard()
        setContentWith(clipboard) {
            SelectionContainer { Column { MarkdownBody("첫 문단 **굵게** 입니다\n\n두번째 문단") } }
        }
        // 마크다운은 파싱이 끝난 뒤 그려진다.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("첫 문단", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        val text = onNodeWithText("첫 문단", substring = true, useUnmergedTree = true)
        dragSelect(text)
        pressCopy(text)
        assertTrue(clipboard.text.orEmpty().contains("굵게"), "copied=${clipboard.text}")
    }

    /**
     * 실제 상세 모달(Popup). Popup 은 LocalClipboard 를 창의 클립보드로 다시 넣어서 가짜를 쓸 수 없어,
     * 시스템 클립보드를 잠시 쓰고 원래 내용으로 되돌린다. 헤드리스 환경에서는 건너뛴다.
     */
    @Test
    fun `상세 모달 - 제목 · 본문을 골라 복사하고 선택 뒤에도 Esc · 좌우 키가 동작한다`() {
        if (GraphicsEnvironment.isHeadless()) return
        val system = Toolkit.getDefaultToolkit().systemClipboard
        val saved = runCatching { system.getContents(null) }.getOrNull()
        fun systemText() = runCatching { system.getData(DataFlavor.stringFlavor) as String }.getOrNull()
        try {
            system.setContents(StringSelection(""), null)
            runComposeUiTest {
                var closed = 0
                var previous = 0
                var next = 0
                setContent {
                    DetailModal(
                        item = DetailItem(
                            url = "https://github.com/o/repo/pull/1", repo = "repo", repoColor = Color.Red,
                            repoFullName = "o/repo", number = 1, title = "Hello selectable title", author = "me",
                            authorInitials = "M", updatedAtIso = "2026-10-01T00:00:00Z", isPullRequest = true,
                            body = "Body **markdown** paragraph text", chips = emptyList(),
                        ),
                        onClose = { closed++ },
                        onOpenOriginal = {},
                        onPrevious = { previous++ },
                        onNext = { next++ },
                    )
                }
                waitForIdle()
                val title = onNodeWithText("Hello selectable title", useUnmergedTree = true)
                dragSelect(title)
                pressCopy(title)
                assertEquals("Hello selectable title", systemText())

                val body = onNodeWithText("paragraph", substring = true, useUnmergedTree = true)
                dragSelect(body)
                pressCopy(body)
                assertTrue(systemText().orEmpty().contains("markdown"), "copied=${systemText()}")

                // 선택 영역이 포커스를 가져도 키는 바깥 Box 로 올라간다.
                body.performKeyInput { pressKey(Key.DirectionRight) }
                body.performKeyInput { pressKey(Key.DirectionLeft) }
                body.performKeyInput { pressKey(Key.Escape) }
                waitForIdle()
                assertEquals(1, next)
                assertEquals(1, previous)
                assertEquals(1, closed)
            }
        } finally {
            runCatching { system.setContents(saved ?: StringSelection(""), null) }
        }
    }
}
