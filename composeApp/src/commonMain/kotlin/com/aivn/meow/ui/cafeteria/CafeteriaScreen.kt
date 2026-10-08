package com.aivn.meow.ui.cafeteria

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.cafeteria.CafeteriaDay
import com.aivn.meow.cafeteria.CafeteriaWeek
import com.aivn.meow.cafeteria.KST
import com.aivn.meow.cafeteria.WeeklyMenuPost
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.ui.common.OpenOriginalButton
import com.aivn.meow.ui.common.PageHeader
import com.aivn.meow.ui.common.pageContent
import com.aivn.meow.ui.weekly.formatSyncTime
import com.aivn.meow.weekly.isoWeekNumber
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

internal val CardBorder = Color(0xFFE4E4EC)
internal val Placeholder = Color(0xFFEEF0F5)
private val MenuText = Color(0xFF383D66)
private val Muted = Color(0xFF9AA0BD)
private val HolidayBg = Color(0xFFFDF1EF)
private val HolidayText = Color(0xFFD9534F)
private val SoftBg = Color(0xFFF5F6FA)

/** 열려 있는 라이트박스. */
internal sealed interface LightboxTarget {
    data class Lunch(val day: CafeteriaDay, val index: Int) : LightboxTarget
    data class Weekly(val post: WeeklyMenuPost, val monday: LocalDate) : LightboxTarget
}

/** 구내 식당 화면: 카카오톡 채널의 주간 메뉴표와 요일별 '오늘의 중식' 사진. */
@Composable
fun CafeteriaScreen(
    viewModel: CafeteriaViewModel,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var lightbox by remember { mutableStateOf<LightboxTarget?>(null) }

    LaunchedEffect(viewModel) { viewModel.onShown() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.pageContent(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PageHeader(
                    title = "구내 식당",
                    subtitle = "kt대덕2연구센터 구내 식당 · 카카오톡 채널",
                    syncLabel = if (state.lastSyncFailed) "마지막 동기화 · 실패" else "마지막 동기화",
                    syncValue = state.lastSync?.let { formatSyncTime(it, Clock.System.now()) } ?: "아직 동기화 안 함",
                    syncOk = !state.lastSyncFailed,
                    syncing = state.selectedLoading,
                    onSync = viewModel::sync,
                    modifier = Modifier.fillMaxWidth(),
                )
                WeekNavigator(
                    monday = state.selectedMonday,
                    onPrevious = viewModel::previousWeek,
                    onNext = viewModel::nextWeek,
                    onThisWeek = viewModel::thisWeek,
                )
                val week = state.selectedWeek
                val error = state.errors[state.selectedMonday]
                when {
                    week != null -> WeekContent(
                        week = week,
                        today = state.today,
                        onOpenUrl = onOpenUrl,
                        onOpenLightbox = { lightbox = it },
                    )
                    error != null && !state.selectedLoading -> CafeCard {
                        StatusMessage("메뉴를 불러오지 못했어요 · $error", color = MeowColors.Error)
                    }
                    else -> CafeCard {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = MeowColors.Brand, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }

    lightbox?.let { target ->
        CafeteriaLightbox(
            target = target,
            onChange = { lightbox = it },
            onClose = { lightbox = null },
            onOpenUrl = onOpenUrl,
        )
    }
}

// ---- 주 이동 ----

@Composable
private fun WeekNavigator(monday: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onThisWeek: () -> Unit) {
    val friday = monday.plus(4, DateTimeUnit.DAY)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        NavButton(text = "‹", onClick = onPrevious, square = true)
        Text(
            text = "${monday.month.number}월 ${monday.day}일 – ${friday.month.number}월 ${friday.day}일",
            color = MeowColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Week ${monday.isoWeekNumber()}",
            color = MeowColors.TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        NavButton(text = "›", onClick = onNext, square = true)
        NavButton(text = "이번 주", onClick = onThisWeek, square = false)
    }
}

@Composable
private fun NavButton(text: String, onClick: () -> Unit, square: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .let { if (square) it.width(32.dp) else it }
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (square) 0.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MeowColors.TextPrimary,
            fontSize = if (square) 16.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ---- 본문 ----

@Composable
private fun WeekContent(
    week: CafeteriaWeek,
    today: LocalDate,
    onOpenUrl: (String) -> Unit,
    onOpenLightbox: (LightboxTarget) -> Unit,
) {
    WeeklyMenuCard(
        post = week.weeklyMenu,
        onOpenUrl = onOpenUrl,
        onOpen = { post -> onOpenLightbox(LightboxTarget.Weekly(post, week.monday)) },
    )
    Text(text = "오늘의 중식", color = MeowColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        week.days.forEach { day ->
            DayCard(
                day = day,
                today = today,
                modifier = Modifier.weight(1f),
                onOpenPhoto = { index -> onOpenLightbox(LightboxTarget.Lunch(day, index)) },
            )
        }
    }
}

@Composable
private fun WeeklyMenuCard(post: WeeklyMenuPost?, onOpenUrl: (String) -> Unit, onOpen: (WeeklyMenuPost) -> Unit) {
    CafeCard(verticalSpacing = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "주간 메뉴표", color = MeowColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (post != null) {
                Text(
                    text = listOfNotNull(post.label, "${formatPostTime(post.publishedAt)} 게시").joinToString(" · "),
                    color = MeowColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                OpenOriginalButton(onClick = { onOpenUrl(post.permalink) })
            }
        }
        val photo = post?.photo
        when {
            post == null -> StatusMessage("아직 이번 주 메뉴표가 안 올라왔어요", color = Muted, height = 160.dp)
            photo == null -> StatusMessage("메뉴표 이미지가 없어요", color = Muted, height = 160.dp)
            else -> KakaoImage(
                url = photo.large,
                contentDescription = "주간 메뉴표",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(photo.ratio(default = 1.4f))
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen(post) },
            )
        }
    }
}

@Composable
private fun DayCard(day: CafeteriaDay, today: LocalDate, modifier: Modifier, onOpenPhoto: (Int) -> Unit) {
    val isToday = day.date == today
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .heightIn(min = 230.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (isToday) 1.5.dp else 1.dp, if (isToday) MeowColors.Brand else CardBorder, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = day.date.dayLetter(),
                color = if (isToday) MeowColors.Brand else MeowColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${day.date.month.number}/${day.date.day}",
                color = if (isToday) MeowColors.Brand else MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (isToday) {
                Text(text = "오늘", color = MeowColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val lunch = day.lunch
        when {
            lunch != null && lunch.photos.isNotEmpty() -> {
                KakaoImage(
                    url = lunch.photos.first().medium,
                    contentDescription = "중식 사진",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenPhoto(0) },
                )
                if (lunch.photos.size > 1) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lunch.photos.drop(1).forEachIndexed { i, photo ->
                            KakaoImage(
                                url = photo.medium,
                                contentDescription = "중식 사진",
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onOpenPhoto(i + 1) },
                            )
                        }
                    }
                }
                if (lunch.menu.isNotBlank()) {
                    Text(text = lunch.menu, color = MenuText, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            lunch != null -> Text(text = lunch.menu.ifBlank { lunch.title }, color = MenuText, fontSize = 12.sp, lineHeight = 17.sp)
            day.holiday -> StatusBox(background = HolidayBg) {
                Text(text = "운영 없음", color = HolidayText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                day.holidayName?.let {
                    Text(text = it, color = MeowColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            isToday -> StatusBox(background = SoftBg) {
                Text(text = "11:20쯤 올라와요", color = MeowColors.Brand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            day.date > today -> StatusBox(background = SoftBg) {
                Text(text = "아직 안 올라왔어요", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            else -> StatusBox(background = SoftBg) {
                Text(text = "게시물이 없어요", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun StatusBox(background: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        content = content,
    )
}

@Composable
private fun StatusMessage(text: String, color: Color, height: androidx.compose.ui.unit.Dp = 120.dp) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(text = text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CafeCard(verticalSpacing: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

// ---- 이미지 ----

/** 캐시된 카카오 이미지. 로딩 중엔 옅은 회색, 실패하면 안내 문구. [placeholderUrls] 중 메모리에 있는 것을 먼저 보여 준다. */
@Composable
internal fun KakaoImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderUrls: List<String> = emptyList(),
) {
    fun initial(): ImageResult = peekCachedImage(url)?.let { ImageResult.Loaded(it) } ?: ImageResult.Loading(
        placeholderUrls.firstNotNullOfOrNull { peekCachedImage(it) },
    )
    val result by produceState(initialValue = initial(), url) {
        // url 이 바뀌면 produceState 는 이전 값을 들고 다시 시작하므로, 새 url 기준으로 먼저 되돌린다.
        value = initial()
        if (value is ImageResult.Loaded) return@produceState
        value = loadCachedImage(url)?.let { ImageResult.Loaded(it) } ?: ImageResult.Failed
    }
    Box(modifier = modifier.background(Placeholder), contentAlignment = Alignment.Center) {
        when (val r = result) {
            is ImageResult.Loaded -> Image(
                bitmap = r.image,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            is ImageResult.Loading -> r.preview?.let {
                Image(bitmap = it, contentDescription = contentDescription, contentScale = contentScale, modifier = Modifier.fillMaxSize())
            }
            ImageResult.Failed -> Text(
                text = "이미지를 불러오지 못했어요",
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

private sealed interface ImageResult {
    data class Loading(val preview: ImageBitmap?) : ImageResult
    data class Loaded(val image: ImageBitmap) : ImageResult
    data object Failed : ImageResult
}

// ---- 형식 ----

internal fun com.aivn.meow.cafeteria.CafeteriaPhoto.ratio(default: Float): Float =
    if (width > 0 && height > 0) width.toFloat() / height else default

private val DAY_LETTERS = listOf("월", "화", "수", "목", "금", "토", "일")

internal fun LocalDate.dayLetter(): String = DAY_LETTERS[dayOfWeek.isoDayNumber - 1]

/** "10/2(금) 16:59" (KST). */
internal fun formatPostTime(instant: Instant): String {
    val t = instant.toLocalDateTime(KST)
    val hm = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    return "${t.month.number}/${t.day}(${t.date.dayLetter()}) $hm"
}
