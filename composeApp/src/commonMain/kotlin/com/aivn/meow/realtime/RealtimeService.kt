package com.aivn.meow.realtime

import androidx.compose.ui.graphics.Color
import com.aivn.meow.config.SupabaseConfig
import com.aivn.meow.model.CiStatus
import com.aivn.meow.model.Label
import com.aivn.meow.model.PullRequest
import com.aivn.meow.theme.MeowColors
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

/**
 * Supabase Realtime 을 통해 `pr_events` 테이블의 INSERT 를 구독한다.
 * 로컬 필터로 `requested_reviewer == reviewerLogin` 만 남겨서 PullRequest 로 매핑한다.
 */
class RealtimeService(private val config: SupabaseConfig) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = createSupabaseClient(config.url, config.anonKey) {
        install(Realtime)
        install(Postgrest)
    }

    fun subscribeReviewRequests(reviewerLogin: String): Flow<PullRequest> = flow {
        val channel = client.channel("pr_events_${reviewerLogin}")
        val insertFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "pr_events"
            filter("requested_reviewer", FilterOperator.EQ, reviewerLogin)
        }
        channel.subscribe()
        try {
            insertFlow
                .mapNotNull { action ->
                    runCatching { json.decodeFromJsonElement(PrEventRow.serializer(), action.record) }
                        .getOrNull()
                        ?.takeIf { it.requestedReviewer == reviewerLogin }
                        ?.toDomain()
                }
                .collect { emit(it) }
        } finally {
            runCatching { channel.unsubscribe() }
        }
    }.onCompletion { runCatching { client.close() } }
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
