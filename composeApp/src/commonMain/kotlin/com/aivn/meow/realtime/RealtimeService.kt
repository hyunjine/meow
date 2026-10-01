package com.aivn.meow.realtime

import androidx.compose.ui.graphics.Color
import com.aivn.meow.config.SupabaseConfig
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.ItemKind
import com.aivn.meow.model.Label
import com.aivn.meow.model.PullRequest
import com.aivn.meow.model.SectionItem
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.MeowNotice
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

@Serializable
private data class PrLabelRow(val name: String, val color: String)

@Serializable
private data class PrEventRow(
    val id: String,
    @SerialName("delivery_id") val deliveryId: String? = null,
    @SerialName("requested_reviewer") val requestedReviewer: String,
    @SerialName("pr_url") val prUrl: String,
    @SerialName("pr_number") val prNumber: Int,
    @SerialName("pr_title") val prTitle: String,
    @SerialName("repo_full_name") val repoFullName: String,
    val author: String? = null,
    @SerialName("is_draft") val isDraft: Boolean = false,
    val labels: List<PrLabelRow> = emptyList(),
)

@Serializable
private data class NotifyEventRow(
    val id: String,
    val kind: String,
    @SerialName("target_login") val targetLogin: String,
    @SerialName("repo_full_name") val repoFullName: String,
    val number: Int,
    val title: String,
    val url: String,
    val actor: String? = null,
    val excerpt: String? = null,
    @SerialName("review_state") val reviewState: String? = null,
)

/**
 * Supabase Realtime 을 통해 `pr_events`(리뷰 요청) · `notify_events`(#3 멘션 · 새 댓글 · 할당 · 내 PR 리뷰)의
 * INSERT 를 한 채널로 구독해 [MeowNotice] 로 매핑한다. 서버 필터와 같은 조건으로 로컬에서도 한 번 더 거른다.
 */
class RealtimeService(private val config: SupabaseConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = createSupabaseClient(config.url, config.anonKey) {
        install(Realtime)
        install(Postgrest)
    }

    fun subscribeNotices(login: String): Flow<MeowNotice> = flow {
        // notify_events.target_login 은 소문자로 저장된다 (GitHub login 은 대소문자 무시).
        val target = login.lowercase()
        val channel = client.channel("meow_${login}")
        val reviewRequests = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "pr_events"
            filter("requested_reviewer", FilterOperator.EQ, login)
        }.mapNotNull { action ->
            runCatching { json.decodeFromJsonElement(PrEventRow.serializer(), action.record) }
                .getOrNull()
                ?.takeIf { it.requestedReviewer == login }
                ?.let { MeowNotice.ReviewRequested(it.toDomain()) }
        }
        val others = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notify_events"
            filter("target_login", FilterOperator.EQ, target)
        }.mapNotNull { action ->
            runCatching { json.decodeFromJsonElement(NotifyEventRow.serializer(), action.record) }
                .getOrNull()
                ?.takeIf { it.targetLogin == target }
                ?.toNotice()
        }
        channel.subscribe()
        try {
            merge(reviewRequests, others).collect { emit(it) }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }.onCompletion { runCatching { client.close() } }
}

/**
 * 60초 조회 섹션의 [SectionItem] 과 같은 모양으로 매핑해 #66 알림 문구를 그대로 쓴다.
 * 새 댓글은 detail · body 를 섹션과 같은 형식("@작성자 님의 댓글", 댓글 본문)으로, 멘션은 멘션한 사람을 작성자로 둔다.
 */
private fun NotifyEventRow.toNotice(): MeowNotice? {
    val repoShort = repoFullName.substringAfter('/', repoFullName)
    val color = colorForRepo(repoShort)
    val authorLogin = actor ?: "unknown"
    val item = SectionItem(
        kind = if ("/pull/" in url) ItemKind.PullRequest else ItemKind.Issue,
        repo = repoShort,
        repoColor = color,
        number = number,
        title = title,
        author = authorLogin,
        authorInitials = initialsFrom(authorLogin),
        updatedAtIso = "",
        labels = emptyList(),
        url = url,
        detail = if (kind == "new_comment") "@$authorLogin 님의 댓글" else null,
        body = excerpt,
    )
    return when (kind) {
        "mentioned" -> MeowNotice.Mentioned(item)
        "new_comment" -> MeowNotice.NewComment(item)
        "assigned" -> MeowNotice.Assigned(item)
        "pr_review" -> when (reviewState) {
            "approved" -> MeowNotice.MyPrReviewed(item.copy(reviewState = "APPROVED"), approved = true)
            "changes_requested" -> MeowNotice.MyPrReviewed(item.copy(reviewState = "CHANGES_REQUESTED"), approved = false)
            else -> null
        }
        else -> null
    }
}

private fun PrEventRow.toDomain(): PullRequest {
    val repoShort = repoFullName.substringAfter('/', repoFullName)
    val color = colorForRepo(repoShort)
    val authorLogin = author ?: "unknown"
    return PullRequest(
        repo = repoShort,
        repoColor = color,
        number = prNumber,
        title = prTitle,
        author = authorLogin,
        authorInitials = initialsFrom(authorLogin),
        relativeTime = "방금 업데이트",
        updatedAtIso = "", // Realtime 이벤트엔 GitHub updated_at 이 없음 — 대시보드 카드는 다음 폴링에서 정확해짐
        isDraft = isDraft,
        ci = CiStatus.Pending,
        labels = labels.take(4).map { Label(text = it.name, color = hexColorOrFallback(it.color, color)) },
        url = prUrl,
    )
}

private val palette = listOf(
    MeowColors.Brand,
    MeowColors.Violet,
    MeowColors.Teal,
    MeowColors.Warning,
    MeowColors.Success,
    MeowColors.Grey,
    MeowColors.Error,
)

private fun colorForRepo(repo: String): Color {
    val hash = repo.fold(0) { acc, c -> acc * 31 + c.code }
    return palette[abs(hash) % palette.size]
}

private fun hexColorOrFallback(hex: String, fallback: Color): Color {
    val v = hex.trim().removePrefix("#")
    if (v.length != 6) return fallback
    val r = v.substring(0, 2).toIntOrNull(16) ?: return fallback
    val g = v.substring(2, 4).toIntOrNull(16) ?: return fallback
    val b = v.substring(4, 6).toIntOrNull(16) ?: return fallback
    return Color(r / 255f, g / 255f, b / 255f)
}

private fun initialsFrom(login: String): String {
    val parts = login.split(Regex("[._-]+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        login.length >= 2 -> login.take(2).uppercase()
        login.isNotEmpty() -> login.take(1).uppercase()
        else -> "?"
    }
}
