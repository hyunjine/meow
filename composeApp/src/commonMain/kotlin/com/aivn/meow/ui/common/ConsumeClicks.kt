package com.aivn.meow.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * #161 이 영역의 클릭이 바깥(어두운 배경 · 카드 토글)으로 전달되지 않게 누름만 소비한다. 포커스는 가져가지 않는다.
 *
 * `clickable(onClick = {})` 로 막으면 데스크톱에서 누르는 순간 그 clickable 이 포커스를 가져가
 * (foundation `isRequestFocusOnClickEnabled() = true`) 안쪽 SelectionContainer 가 포커스를 잃는다.
 * 그러면 글자는 선택돼 보여도 ⌘C 가 SelectionContainer 의 onKeyEvent(복사)에 닿지 않아 복사가 안 된다.
 * 안쪽 선택 · 버튼은 자식이라 먼저 이벤트를 받으므로 영향이 없다.
 */
internal fun Modifier.consumeClicks(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
}
