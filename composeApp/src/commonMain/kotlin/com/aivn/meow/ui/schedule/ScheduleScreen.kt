package com.aivn.meow.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.ms.MsAuthState
import com.aivn.meow.schedule.AbsenceEntry
import com.aivn.meow.schedule.AbsenceKind
import com.aivn.meow.schedule.CALENDAR_NAME
import com.aivn.meow.schedule.ScheduleWeek
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.EmptyStateCard
import com.aivn.meow.ui.common.PageHeader
import com.aivn.meow.ui.common.PageHorizontalPadding
import com.aivn.meow.ui.common.PageMaxWidth
import com.aivn.meow.ui.common.PageVerticalPadding
import com.aivn.meow.ui.weekly.formatSyncTime
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

private val CardBorder = Color(0xFFE4E4EC)
private val NameColor = Color(0xFF0D0F26)
private val NoteColor = Color(0xFF616A94)
private val DayLetters = listOf("월", "화", "수", "목", "금")

/** 일정 화면: TeamAIVN 공용 캘린더에서 이번 주 월~금 부재자(휴가 · 반차 · 출장 …)를 요일 칸으로 보여 준다. */
@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val auth by viewModel.auth.state.collectAsState()
    val connected = auth is MsAuthState.Connected

    LaunchedEffect(connected) { if (connected) viewModel.ensureLoaded() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding)
                .fillMaxWidth()
                .widthIn(max = PageMaxWidth),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val lastSync = state.lastSyncAt
            PageHeader(
                title = "일정",
                subtitle = "$CALENDAR_NAME 공용 캘린더 · 사무실/재택 제외",
                syncLabel = if (state.lastSyncFailed) "마지막 동기화 · 실패" else "마지막 동기화",
                syncValue = lastSync?.let { formatSyncTime(it, Clock.System.now()) } ?: "아직 동기화 안 함",
                syncOk = lastSync != null && !state.lastSyncFailed,
                syncing = state.syncing,
                syncEnabled = connected,
                onSync = viewModel::sync,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!connected) {
                ConnectCard(auth = auth, onConnect = viewModel::signIn)
                return@Column
            }
            WeekNavigator(
                monday = state.weekStart,
                onPrevious = viewModel::previousWeek,
                onNext = viewModel::nextWeek,
                onThisWeek = viewModel::thisWeek,
            )
            when (val content = state.content) {
                null, ScheduleContent.Loading -> LoadingCard()
                ScheduleContent.CalendarNotFound -> EmptyStateCard(
                    title = "'$CALENDAR_NAME' 캘린더를 찾지 못했어요",
                    hint = "Outlook 에서 $CALENDAR_NAME 공용 캘린더를 내 캘린더 목록에 추가한 뒤 동기화해 주세요",
                    modifier = Modifier.fillMaxWidth(),
                )
                is ScheduleContent.Failed -> FailedCard(message = content.message, onRetry = viewModel::sync, enabled = !state.syncing)
                is ScheduleContent.Loaded -> WeekColumns(week = content.week, today = state.today)
            }
        }
    }
}

// ---- 주 이동 ----

@Composable
private fun WeekNavigator(monday: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onThisWeek: () -> Unit) {
    val friday = monday.plus(4, DateTimeUnit.DAY)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 주", onPrevious)
        Text(
            text = "${monday.monthNumber}월 ${monday.dayOfMonth}일 – ${friday.monthNumber}월 ${friday.dayOfMonth}일",
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
        ArrowButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 주", onNext)
        Spacer(Modifier.weight(1f))
        val shape = RoundedCornerShape(10.dp)
        Text(
            text = "이번 주",
            modifier = Modifier
                .clip(shape)
                .background(Color.White)
                .border(1.dp, CardBorder, shape)
                .clickable(onClick = onThisWeek)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = MeowColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, CardBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MeowColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

// ---- 요일 칸 ----

@Composable
private fun WeekColumns(week: ScheduleWeek, today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        (0 until 5).forEach { i ->
            val date = week.monday.plus(i, DateTimeUnit.DAY)
            DayColumn(
                letter = DayLetters[i],
                date = date,
                isToday = date == today,
                entries = week.days[date].orEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayColumn(letter: String, date: LocalDate, isToday: Boolean, entries: List<AbsenceEntry>, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val headerColor = if (isToday) Color.White else MeowColors.TextPrimary
    val subColor = if (isToday) Color.White else MeowColors.TextTertiary
    val people = entries.mapNotNull { it.name }.distinct().size
    Column(
        modifier = modifier
            .heightIn(min = 420.dp)
            .clip(shape)
            .background(Color.White)
            .border(if (isToday) 2.dp else 1.dp, if (isToday) MeowColors.Brand else CardBorder, shape)
            .padding(start = 12.dp, end = 12.dp, top = if (isToday) 12.dp else 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 오늘 칸은 헤더를 브랜드 색 바로 채워 강조한다.
        val headerModifier = if (isToday) {
            Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MeowColors.Brand)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        } else {
            Modifier.fillMaxWidth().padding(bottom = 4.dp)
        }
        Row(
            modifier = headerModifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = letter, color = headerColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = "${date.monthNumber}/${date.dayOfMonth}", color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (isToday) TodayPill()
            Spacer(Modifier.weight(1f))
            if (people > 0) {
                Text(text = "${people}명", color = subColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (entries.isEmpty()) {
            Text(
                text = "모두 출근",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = MeowColors.TextTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        } else {
            entries.forEach { AbsenceCard(it) }
        }
    }
}

@Composable
private fun TodayPill() {
    Text(
        text = "오늘",
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = MeowColors.Brand,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AbsenceCard(entry: AbsenceEntry) {
    val (fg, bg) = entry.kind.colors()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = entry.kind.label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(
            text = entry.name ?: entry.title.orEmpty(),
            color = NameColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        entry.note?.let { Text(text = it, color = NoteColor, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
    }
}

/** 종류별 (글자색, 배경색). */
private fun AbsenceKind.colors(): Pair<Color, Color> = when (this) {
    AbsenceKind.Vacation -> Color(0xFF3D5EFF) to Color(0xFFEBEFFF)
    AbsenceKind.HalfDay -> Color(0xFF7C4DFF) to Color(0xFFF1EBFF)
    AbsenceKind.Trip -> Color(0xFFE8770E) to Color(0xFFFFF2E5)
    AbsenceKind.Checkup -> Color(0xFF14A06B) to Color(0xFFE6F7EF)
    else -> Color(0xFF616A94) to Color(0xFFF1F2F6)
}

// ---- 상태 카드 ----

@Composable
private fun LoadingCard() {
    Column(
        modifier = Modifier.fillMaxWidth().glassSurface(corner = 20.dp).padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MeowColors.Brand, strokeWidth = 2.dp)
        Text("일정을 불러오는 중…", color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FailedCard(message: String, onRetry: () -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().glassSurface(corner = 20.dp).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("일정을 불러오지 못했어요", color = MeowColors.Error, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            text = message,
            color = MeowColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        val shape = RoundedCornerShape(12.dp)
        Text(
            text = "다시 시도",
            modifier = Modifier
                .alpha(if (enabled) 1f else 0.45f)
                .clip(shape)
                .background(MeowColors.Surface)
                .border(1.dp, MeowColors.GlassBorder, shape)
                .clickable(enabled = enabled, onClick = onRetry)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            color = MeowColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConnectCard(auth: MsAuthState, onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().glassSurface(corner = 20.dp).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "📅", fontSize = 28.sp)
        Text(
            text = "Microsoft 계정을 연결하면 $CALENDAR_NAME 캘린더에서 이번 주 부재자를 보여 드려요",
            color = MeowColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        when (auth) {
            is MsAuthState.Connecting -> Text(
                text = "연결하는 중이에요 · 브라우저가 열리면 로그인을 마쳐 주세요",
                color = MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            is MsAuthState.Error -> Text(
                text = auth.message,
                color = MeowColors.Error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            else -> Unit
        }
        Spacer(Modifier.height(4.dp))
        val loading = auth is MsAuthState.Connecting
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MeowColors.Brand)
                .clickable(enabled = !loading, onClick = onConnect)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            Text(
                text = if (auth is MsAuthState.Error) "다시 시도" else "Microsoft 계정 연결",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
