package com.aivn.meow.ui.pr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.aivn.meow.github.GithubClient
import com.aivn.meow.github.MergeStateNode
import com.aivn.meow.github.fetchMergeState
import com.aivn.meow.github.mergePullRequest
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.common.MeowType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---- 상태 홀더 ----

/** 머지 상태 조회 결과. */
sealed interface MergeLoad {
    data object Loading : MergeLoad
    data class Loaded(val pr: MergeStateNode) : MergeLoad
    data class Failed(val message: String) : MergeLoad
}

/** 머지 요청 진행 상태. */
sealed interface MergeAction {
    data object Idle : MergeAction
    data object Merging : MergeAction
    data object Merged : MergeAction
    data class Failed(val message: String) : MergeAction
}

/** PR url 하나의 푸터 상태. [generation] 은 이 상태를 불러온 대시보드 새로고침 회차. */
data class MergeEntry(
    val load: MergeLoad,
    val generation: Int,
    val action: MergeAction = MergeAction.Idle,
    /** 사용자가 ▾ 메뉴로 고른 방식. null 이면 레포 기본값. */
    val chosenMethod: MergeMethod? = null,
)

/**
 * #130 내 PR 카드의 머지 푸터 상태를 PR url 별로 캐시한다.
 * 카드를 펼칠 때 [ensureLoaded] 로 지연 조회하고, 대시보드가 새로 불러올 때마다 [onDashboardRefreshed] 로
 * 회차를 올려 펼쳐진 카드가 다시 조회하게 한다. 머지에 성공하면 [onMerged] 로 대시보드를 새로고침한다.
 */
class MergeController(
    private val client: GithubClient,
    private val scope: CoroutineScope,
    private val onMerged: () -> Unit,
) {
    private val entries = mutableStateMapOf<String, MergeEntry>()
    private val loadJobs = mutableMapOf<String, Job>()

    /** 대시보드 새로고침 회차. 캐시 항목의 회차와 다르면 다시 조회한다. */
    var generation by mutableStateOf(0)
        private set

    fun entry(url: String): MergeEntry? = entries[url]

    fun onDashboardRefreshed() {
        generation++
    }

    /** 캐시가 없거나 · 지난 회차거나 · 실패했으면 조회한다. 이전 결과는 조회 중에도 그대로 보여준다. */
    fun ensureLoaded(url: String) {
        val current = entries[url]
        if (loadJobs[url]?.isActive == true) return
        if (current?.action == MergeAction.Merging || current?.action == MergeAction.Merged) return
        if (current != null && current.generation == generation && current.load !is MergeLoad.Failed) return
        load(url)
    }

    private fun load(url: String, attempt: Int = 0) {
        val gen = generation
        if (entries[url] == null) entries[url] = MergeEntry(MergeLoad.Loading, gen)
        loadJobs[url] = scope.launch {
            val result = try {
                MergeLoad.Loaded(client.fetchMergeState(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                MergeLoad.Failed(githubErrorText(e))
            }
            val previous = entries[url] ?: MergeEntry(result, gen)
            // 새 회차로 다시 불러오면 지난 머지 실패 문구는 지운다.
            val action = if (previous.generation != gen && previous.action is MergeAction.Failed) MergeAction.Idle else previous.action
            entries[url] = previous.copy(load = result, generation = gen, action = action)
            // GitHub 은 mergeable 을 지연 계산한다 — UNKNOWN 이면 잠시 뒤 다시 묻는다.
            if (result is MergeLoad.Loaded && mergeVerdict(result.pr).retry && attempt < MAX_UNKNOWN_RETRIES) {
                delay(UNKNOWN_RETRY_DELAY_MS)
                load(url, attempt + 1)
            }
        }
    }

    fun chooseMethod(url: String, method: MergeMethod) {
        entries[url]?.let { entries[url] = it.copy(chosenMethod = method) }
    }

    fun merge(url: String, method: MergeMethod) {
        val current = entries[url] ?: return
        val pr = (current.load as? MergeLoad.Loaded)?.pr ?: return
        if (current.action == MergeAction.Merging || current.action == MergeAction.Merged) return
        entries[url] = current.copy(action = MergeAction.Merging)
        scope.launch {
            val failure = try {
                client.mergePullRequest(pr.id, method.name)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                githubErrorText(e)
            }
            val latest = entries[url] ?: current
            if (failure == null) {
                entries[url] = latest.copy(action = MergeAction.Merged)
                // '머지했어요' 를 잠깐 보여준 뒤 새로고침해 목록에서 빠지게 한다.
                delay(MERGED_HOLD_MS)
                onMerged()
            } else {
                entries[url] = latest.copy(action = MergeAction.Failed(failure))
                load(url)
            }
        }
    }

    private companion object {
        const val UNKNOWN_RETRY_DELAY_MS = 3_000L
        const val MAX_UNKNOWN_RETRIES = 5
        const val MERGED_HOLD_MS = 1_500L
    }
}

/** App 이 제공하는 머지 컨트롤러. 없으면 푸터를 그리지 않는다. */
val LocalMergeController = staticCompositionLocalOf<MergeController?> { null }

// ---- UI ----

private val FooterBg = Color(0xFFF3FBF7)
private val MergeGreen = Color(0xFF14A06B)
private val DisabledGrey = Color(0xFFC9CCDA)
private val BlockedOrange = Color(0xFFE8770E)
private val SubtitleGrey = Color(0xFF616A94)
private val SplitDivider = Color(0x33FFFFFF)

/** #130 내 열린 PR 카드를 펼쳤을 때 아래에 붙는 머지 바. */
@Composable
fun MergeFooter(prUrl: String, prNumber: Int, prTitle: String, modifier: Modifier = Modifier) {
    val controller = LocalMergeController.current ?: return
    val generation = controller.generation
    LaunchedEffect(prUrl, generation) { controller.ensureLoaded(prUrl) }

    val entry = controller.entry(prUrl)
    val pr = (entry?.load as? MergeLoad.Loaded)?.pr
    val verdict = pr?.let(::mergeVerdict)
    val action = entry?.action ?: MergeAction.Idle
    val allowed = pr?.let { allowedMergeMethods(it.repository) }.orEmpty()
    val method = pr?.let { selectMergeMethod(it.repository, entry?.chosenMethod) }
    var confirming by remember { mutableStateOf(false) }

    val (title, titleColor) = when {
        action == MergeAction.Merged -> "머지했어요" to MergeGreen
        action is MergeAction.Failed -> "머지하지 못했어요" to MeowColors.Error
        entry?.load is MergeLoad.Failed -> "머지 상태를 불러오지 못했어요" to MeowColors.Error
        verdict == null -> "머지 가능 여부 확인 중…" to SubtitleGrey
        else -> verdict.title to when (verdict.tone) {
            MergeTone.Ready -> MergeGreen
            MergeTone.Blocked -> BlockedOrange
            MergeTone.Checking -> SubtitleGrey
        }
    }
    val (subtitle, subtitleColor) = when {
        action == MergeAction.Merged -> "${method?.label ?: ""} 머지 완료 · 곧 목록에서 빠져요" to SubtitleGrey
        action is MergeAction.Failed -> action.message to MeowColors.Error
        entry?.load is MergeLoad.Failed -> (entry.load as MergeLoad.Failed).message to MeowColors.Error
        verdict == null -> "GitHub 에서 상태를 불러오고 있어요" to SubtitleGrey
        else -> verdict.subtitle to SubtitleGrey
    }
    val merging = action == MergeAction.Merging
    val enabled = verdict?.enabled == true && method != null && !merging && action != MergeAction.Merged

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FooterBg)
            // 푸터 클릭이 카드 펼침 토글로 전달되지 않도록 소비한다
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = titleColor, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != MergeAction.Merged) {
            SplitMergeButton(
                method = method,
                allowed = allowed,
                enabled = enabled,
                merging = merging,
                onMerge = { confirming = true },
                onChoose = { controller.chooseMethod(prUrl, it) },
            )
        }
    }

    if (confirming && method != null) {
        ConfirmMergeDialog(
            number = prNumber,
            title = pr?.title ?: prTitle,
            method = method,
            onConfirm = {
                confirming = false
                controller.merge(prUrl, method)
            },
            onDismiss = { confirming = false },
        )
    }
}

/** 왼쪽 = `{방식} 머지`, 오른쪽 ▾ = 허용된 방식 고르기. */
@Composable
private fun SplitMergeButton(
    method: MergeMethod?,
    allowed: List<MergeMethod>,
    enabled: Boolean,
    merging: Boolean,
    onMerge: () -> Unit,
    onChoose: (MergeMethod) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val bg = if (enabled || merging) MergeGreen else DisabledGrey
    val canChoose = allowed.size > 1 && !merging
    Row(modifier = Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(bg)
                .clickable(enabled = enabled, onClick = onMerge)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (merging) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Merge, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Text(
                text = "${method?.label ?: "PR"} 머지",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(if (enabled || merging) SplitDivider else Color.White.copy(alpha = 0.5f)))
        Box {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(bg)
                    .clickable(enabled = canChoose) { menuOpen = true }
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "▾", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = MeowColors.Surface,
                border = BorderStroke(1.dp, MeowColors.GlassBorder),
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                allowed.forEach { option ->
                    val selected = option == method
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "${option.label} 머지",
                                style = MeowType.Meta,
                                color = if (selected) MergeGreen else MeowColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        trailingIcon = if (selected) {
                            { Icon(Icons.Default.Check, null, tint = MergeGreen, modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                        onClick = {
                            onChoose(option)
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/** 창 전체를 덮는 오버레이로 띄운다 (주간 보고의 '문서에 반영' 확인창과 같은 모양). */
private object FullWindowPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}

@Composable
private fun ConfirmMergeDialog(
    number: Int,
    title: String,
    method: MergeMethod,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Popup(
        popupPositionProvider = FullWindowPosition,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MeowColors.TextPrimary.copy(alpha = 0.35f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focus)
                .focusTarget(),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .shadow(elevation = 32.dp, shape = shape)
                    .background(MeowColors.Surface, shape)
                    // 카드 안 클릭이 dim 으로 전달돼 닫히지 않도록 소비한다
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    text = "PR #$number 을(를) ${method.label} 으로 머지할까요?",
                    color = MeowColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = title, style = MeowType.Body, color = MeowColors.TextSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    DialogButton(text = "취소", filled = false, onClick = onDismiss)
                    DialogButton(text = "머지", filled = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        modifier = Modifier
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(MergeGreen)
                } else {
                    Modifier.background(MeowColors.Surface).border(1.dp, MeowColors.GlassBorder, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        style = MeowType.Meta,
        color = if (filled) Color.White else MeowColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
    )
}
