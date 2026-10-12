package com.aivn.meow.ui.card

import androidx.compose.runtime.staticCompositionLocalOf
import com.aivn.meow.data.CardDiscussion
import com.aivn.meow.data.DiscussionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** #131 펼친 카드의 세그먼트 탭. */
enum class CardTab { Body, Comments, Reviews }

/**
 * #131 항목 url 하나의 댓글 · 리뷰 로딩 상태. [data] 가 있으면 다시 받는 중([loading])이나 실패([error])여도 그대로 보여준다.
 * [updatedAtIso] 는 [data] 를 받을 때 기준이 된 항목 갱신 시각.
 */
data class DiscussionState(
    val updatedAtIso: String,
    val data: CardDiscussion? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * #131 펼친 카드의 댓글 · 리뷰 캐시 (항목 url 별). 대시보드 뷰모델이 들고 있어 화면이 살아 있는 동안 유지된다.
 * 카드를 펼치면 [ensureLoaded] 로 처음 한 번 받고, 자동 새로고침으로 항목의 updatedAt 이 바뀌면 다시 받는다.
 */
class CardDiscussionStore(
    private val scope: CoroutineScope,
    private val loader: suspend (DiscussionTarget) -> CardDiscussion,
) {
    private val _states = MutableStateFlow<Map<String, DiscussionState>>(emptyMap())
    val states: StateFlow<Map<String, DiscussionState>> = _states.asStateFlow()

    /** 카드별로 고른 탭 (기본 [CardTab.Body]). 접었다 펼쳐도 유지된다. */
    private val _selectedTabs = MutableStateFlow<Map<String, CardTab>>(emptyMap())
    val selectedTabs: StateFlow<Map<String, CardTab>> = _selectedTabs.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    fun selectTab(url: String, tab: CardTab) {
        _selectedTabs.update { it + (url to tab) }
    }

    /** 아직 없거나, 항목이 갱신됐거나, 지난번에 실패했으면 받는다. 같은 기준으로 받는 중이면 건너뛴다. */
    fun ensureLoaded(target: DiscussionTarget) {
        val current = _states.value[target.url]
        val fresh = current != null && current.updatedAtIso == target.updatedAtIso
        if (fresh && (current.loading || (current.data != null && current.error == null))) return
        load(target)
    }

    /** '다시 시도'. */
    fun retry(target: DiscussionTarget) = load(target)

    private fun load(target: DiscussionTarget) {
        val url = target.url
        jobs[url]?.cancel()
        _states.update { states ->
            val prior = states[url] ?: DiscussionState(target.updatedAtIso)
            states + (url to prior.copy(updatedAtIso = target.updatedAtIso, loading = true, error = null))
        }
        jobs[url] = scope.launch {
            val next = try {
                val data = loader(target)
                DiscussionState(target.updatedAtIso, data = data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e::class.simpleName ?: "unknown error"
                (_states.value[url] ?: DiscussionState(target.updatedAtIso)).copy(loading = false, error = message)
            }
            _states.update { it + (url to next) }
        }
    }
}

/** 대시보드가 내려주는 댓글 · 리뷰 캐시. 없으면(미리보기 등) 펼친 카드는 본문만 보여준다. */
val LocalCardDiscussions = staticCompositionLocalOf<CardDiscussionStore?> { null }
