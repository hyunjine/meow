package com.aivn.meow.ui.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.ms.MsAuthState
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.EmptyStateCard
import com.aivn.meow.ui.common.PageHeader
import com.aivn.meow.ui.common.PageHorizontalPadding
import com.aivn.meow.ui.common.PageMaxWidth
import com.aivn.meow.ui.common.PageVerticalPadding
import com.aivn.meow.weekly.MyWeeklyRow
import com.aivn.meow.weekly.WeekDoc
import com.aivn.meow.weekly.WeekDocSource
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** 주간 업무 보고 화면: 동기화로 이번 주 문서를 찾아 내 행을 보여 주고, PR 초안을 다듬어 문서에 반영한다. */
@Composable
fun WeeklyReportScreen(
    viewModel: WeeklyReportViewModel,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val auth by viewModel.auth.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    val connected = auth as? MsAuthState.Connected
    // 앱을 켜고 계정을 다시 연결하는 동안에도 보관해 둔 결과는 바로 보여 준다.
    val showCached = auth is MsAuthState.Connecting && state.lastSync != null && state.reportName != null

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = PageHorizontalPadding, vertical = PageVerticalPadding)
                    .fillMaxWidth()
                    .widthIn(max = PageMaxWidth),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val sync = state.lastSync
                PageHeader(
                    title = "주간 업무 보고",
                    subtitle = "보고 메일이 오면 동기화를 눌러 주세요 · 메일에서 보고 문서 링크를 찾아 이번 주 PR 로 초안을 만들어요",
                    syncLabel = syncLabel(sync),
                    syncValue = syncValue(sync),
                    syncOk = sync != null && !sync.failed && sync.doc != null,
                    syncing = state.syncing,
                    syncEnabled = connected != null && state.reportName != null &&
                        (state.content as? WeeklyContent.Found)?.saving != true,
                    onSync = viewModel::sync,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    connected == null && !showCached -> ConnectCard(auth = auth, onConnect = viewModel::signIn)
                    state.reportName == null || state.editingName -> ReportNameCard(
                        initialName = state.reportName,
                        onSave = viewModel::saveReportName,
                        // 저장된 이름이 있을 때(이름 바꾸기)만 돌아갈 수 있다.
                        onCancel = if (state.reportName != null) viewModel::cancelEditingName else null,
                    )
                    else -> ReportBody(
                        state = state,
                        viewModel = viewModel,
                        onOpenUrl = onOpenUrl,
                        onPublish = { confirming = true },
                        canWrite = connected != null,
                    )
                }
                if (connected != null) {
                    AccountLine(
                        upn = connected.upn,
                        reportName = state.reportName.takeUnless { state.editingName },
                        onChangeName = viewModel::startEditingName,
                        onSignOut = viewModel::signOut,
                    )
                }
            }
        }
        if (confirming) {
            ConfirmPublishDialog(
                onConfirm = {
                    confirming = false
                    viewModel.publish()
                },
                onDismiss = { confirming = false },
            )
        }
    }
}

private fun syncLabel(sync: WeeklySyncInfo?): String = when {
    sync == null -> "마지막 동기화"
    sync.failed -> "마지막 동기화 · 실패"
    sync.doc == null -> "마지막 동기화 · 보고 메일 없음"
    sync.doc.source == WeekDocSource.Mail -> "마지막 동기화 · 보고 메일"
    else -> "마지막 동기화 · 폴더"
}

private fun syncValue(sync: WeeklySyncInfo?): String {
    if (sync == null) return "아직 동기화 안 함"
    val time = formatSyncTime(sync.at, Clock.System.now())
    val doc = sync.doc?.doc ?: return time
    return "$time · Week${doc.week}-${doc.nextWeek} 문서"
}

// ---- 연결 · 이름 ----

@Composable
private fun ConnectCard(auth: MsAuthState, onConnect: () -> Unit) {
    WeeklyCard(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "🔗", fontSize = 28.sp)
        Text(
            text = "Microsoft 계정을 연결하면 주간회의 문서를 찾아 실적 초안을 만들어 드려요",
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
            else -> Text(
                text = "회사 계정으로 로그인하면 메일 · OneDrive 의 주간회의 자료를 읽어요",
                color = MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        BrandButton(
            text = if (auth is MsAuthState.Error) "다시 시도" else "Microsoft 계정 연결",
            onClick = onConnect,
            loading = auth is MsAuthState.Connecting,
        )
    }
}

@Composable
private fun ReportNameCard(initialName: String?, onSave: (String) -> Unit, onCancel: (() -> Unit)?) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName.orEmpty()) }
    val save = { if (name.isNotBlank()) onSave(name) }
    WeeklyCard(modifier = Modifier.fillMaxWidth()) {
        Text("보고서 표의 내 이름", color = MeowColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "주간회의 문서 표에서 내 행을 찾을 때 써요. 표에 적힌 이름 그대로 입력해 주세요",
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.width(280.dp).onPreviewKeyEvent { event ->
                    if (onCancel != null && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(MeowColors.Brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                decorationBox = { field ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeowColors.Background)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (name.isEmpty()) {
                            Text("예: 양현진", color = MeowColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        field()
                    }
                },
            )
            BrandButton(text = "저장", onClick = save, enabled = name.isNotBlank())
            if (onCancel != null) TextButton(text = "취소", onClick = onCancel)
        }
    }
}

@Composable
private fun AccountLine(upn: String, reportName: String?, onChangeName: () -> Unit, onSignOut: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val muted = TextStyle(color = MeowColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text("연결됨 · $upn", style = muted)
        if (reportName != null) {
            Text("· 표 이름 $reportName", style = muted)
            LinkText("이름 바꾸기", onClick = onChangeName)
        }
        Text("·", style = muted)
        LinkText("로그아웃", onClick = onSignOut)
    }
}

// ---- 본문 ----

@Composable
private fun ReportBody(
    state: WeeklyUiState,
    viewModel: WeeklyReportViewModel,
    onOpenUrl: (String) -> Unit,
    onPublish: () -> Unit,
    canWrite: Boolean,
) {
    val content = state.content
    val showWeeks = content is WeeklyContent.Found || content is WeeklyContent.NotFound
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (showWeeks) {
            WeekList(
                state = state,
                onSelectPast = viewModel::selectPastWeek,
                onSelectThisWeek = viewModel::clearPastWeek,
                modifier = Modifier.width(240.dp),
            )
        }
        val selectedPast = state.selectedPast.takeIf { showWeeks }
        Box(modifier = Modifier.weight(1f)) {
            if (selectedPast != null) {
                PastWeekCard(
                    selection = selectedPast,
                    row = state.pastRows[selectedPast.doc.itemId],
                    onOpenUrl = onOpenUrl,
                    onRetry = viewModel::retryPastWeek,
                    onBack = viewModel::clearPastWeek,
                )
            } else when (content) {
                WeeklyContent.Idle -> EmptyStateCard(
                    title = if (state.syncing) "이번 주 문서를 찾는 중…" else "아직 동기화 안 함",
                    hint = "오른쪽 위 동기화를 누르면 이번 주 문서를 찾아 실적 초안을 만들어 드려요",
                    modifier = Modifier.fillMaxWidth(),
                )
                WeeklyContent.NotFound -> WeeklyCard(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "📭", fontSize = 28.sp)
                    Text(
                        text = "이번 주 보고 메일을 아직 찾지 못했어요",
                        color = MeowColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "메일을 받은 뒤 오른쪽 위 동기화를 누르면 초안을 만들어 드려요",
                        color = MeowColors.TextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                is WeeklyContent.Failed -> WeeklyCard(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("동기화하지 못했어요", color = MeowColors.Error, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = content.message,
                        color = MeowColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlineButton(text = "다시 시도", onClick = viewModel::sync, enabled = !state.syncing)
                }
                is WeeklyContent.Found -> DocumentCard(
                    found = content,
                    myName = state.reportName.orEmpty(),
                    onEditResult = viewModel::editResult,
                    onEditPlan = viewModel::editPlan,
                    onRegenerate = viewModel::regenerateDraft,
                    onPublish = onPublish,
                    onOpenUrl = onOpenUrl,
                    onChangeName = viewModel::startEditingName,
                    canWrite = canWrite,
                )
            }
        }
    }
}

/** 주차 목록에 보일 한 줄. 이번 주 문서를 아직 못 찾았으면 [doc] 이 null 인 자리 항목. */
private data class WeekItem(val week: Int, val doc: WeekDoc?)

@Composable
private fun WeekList(
    state: WeeklyUiState,
    onSelectPast: (WeekDoc) -> Unit,
    onSelectThisWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = buildList {
        if (state.weeks.none { it.week == state.currentWeek }) add(WeekItem(state.currentWeek, null))
        state.weeks.forEach { add(WeekItem(it.week, it)) }
    }.take(WeeklyReportViewModel.WEEK_LIST_SIZE)
    val found = state.content as? WeeklyContent.Found
    val year = Clock.System.todayIn(TimeZone.of("Asia/Seoul")).year
    val selectedId = state.selectedPast?.doc?.itemId
    Column(
        modifier = modifier.glassSurface(corner = 20.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val isThisWeek = item.week == state.currentWeek
            val badge: Pair<String, Color>? = when {
                !isThisWeek -> when (state.weekStatus[item.doc?.itemId]) {
                    WeekRowStatus.Done -> "반영 완료" to MeowColors.Success
                    WeekRowStatus.Empty -> "미작성" to MeowColors.Grey
                    null -> null
                }
                found == null -> "메일 대기" to MeowColors.Warning
                found.row.results.isNotEmpty() -> "반영 완료" to MeowColors.Success
                found.resultIsDraft -> "초안" to MeowColors.Warning
                else -> "미작성" to MeowColors.Grey
            }
            val monday = isoWeekMonday(year, item.week)
            val friday = monday.plus(4, DateTimeUnit.DAY)
            val doc = item.doc
            // 고른 지난 주차가 있으면 그 줄을, 없으면 이번 주 줄을 강조한다.
            val highlighted = if (selectedId != null) !isThisWeek && doc?.itemId == selectedId else isThisWeek
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (highlighted) MeowColors.BrandSubtle.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable(enabled = if (isThisWeek) selectedId != null else doc != null) {
                        if (isThisWeek) onSelectThisWeek() else doc?.let(onSelectPast)
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Week ${item.week}" + if (isThisWeek) " · 이번 주" else "",
                        color = if (isThisWeek) MeowColors.Brand else MeowColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${monday.monthNumber}/${monday.dayOfMonth} – ${friday.monthNumber}/${friday.dayOfMonth}",
                        color = MeowColors.TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (badge != null) {
                    StatusBadge(text = badge.first, color = badge.second)
                } else {
                    Text("—", color = MeowColors.TextTertiary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DocumentCard(
    found: WeeklyContent.Found,
    myName: String,
    onEditResult: (String) -> Unit,
    onEditPlan: (String) -> Unit,
    onRegenerate: () -> Unit,
    onPublish: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onChangeName: () -> Unit,
    canWrite: Boolean,
) {
    val row = found.row
    WeeklyCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.header.title ?: row.doc.name.removeSuffix(".docx"),
                modifier = Modifier.weight(1f),
                color = MeowColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Word 문서 미리보기 · 편집 가능",
                color = MeowColors.TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (!row.found) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "표에서 '$myName' 행을 찾지 못했어요. 표에 적힌 이름과 같은지 확인해 주세요",
                    color = MeowColors.Error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                LinkText("이름 바꾸기", onClick = onChangeName)
            }
        }
        val tableShape = RoundedCornerShape(12.dp)
        Column(modifier = Modifier.fillMaxWidth().clip(tableShape).border(1.dp, MeowColors.GlassBorder, tableShape)) {
            CellRow(
                label = row.header.result.label,
                value = found.resultText,
                onValueChange = onEditResult,
                placeholder = "이번 주 실적을 입력해 주세요",
                draft = found.resultIsDraft,
                enabled = row.found,
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(MeowColors.GlassBorder))
            CellRow(
                label = row.header.plan.label,
                value = found.planText,
                onValueChange = onEditPlan,
                placeholder = "직접 입력해 주세요",
                draft = false,
                enabled = row.found,
            )
        }
        found.draftError?.let {
            Text(text = it, color = MeowColors.Warning, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            row.doc.webUrl?.let { url -> LinkText("원본 문서 열기 ↗", onClick = { onOpenUrl(url) }, fontSize = 12.sp) }
            Spacer(Modifier.weight(1f))
            found.saveMessage?.let { message ->
                Text(
                    text = message.text,
                    color = if (message.ok) MeowColors.Success else MeowColors.Error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlineButton(text = "초안 다시 생성", onClick = onRegenerate, enabled = canWrite && !found.drafting && !found.saving, loading = found.drafting)
            BrandButton(text = "문서에 반영", onClick = onPublish, enabled = canWrite && row.found && !found.saving && !found.drafting, loading = found.saving)
        }
    }
}

/** 고른 지난 주차의 내 행(실적 · 계획)을 읽기 전용으로 보여 준다. */
@Composable
private fun PastWeekCard(
    selection: PastWeekSelection,
    row: MyWeeklyRow?,
    onOpenUrl: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val doc = selection.doc
    val monday = isoWeekMonday(Clock.System.todayIn(TimeZone.of("Asia/Seoul")).year, doc.week)
    val friday = monday.plus(4, DateTimeUnit.DAY)
    WeeklyCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Week ${doc.week} · ${monday.monthNumber}/${monday.dayOfMonth} – ${friday.monthNumber}/${friday.dayOfMonth}",
                modifier = Modifier.weight(1f),
                color = MeowColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "지난 주차 · 읽기 전용",
                color = MeowColors.TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = row?.header?.title ?: doc.name.removeSuffix(".docx"),
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        when {
            row != null && (row.results.isNotEmpty() || row.plans.isNotEmpty()) -> {
                val tableShape = RoundedCornerShape(12.dp)
                Column(modifier = Modifier.fillMaxWidth().clip(tableShape).border(1.dp, MeowColors.GlassBorder, tableShape)) {
                    ReadOnlyCellRow(label = row.header.result.label, lines = row.results)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MeowColors.GlassBorder))
                    ReadOnlyCellRow(label = row.header.plan.label, lines = row.plans)
                }
            }
            row != null -> Text(
                text = "이 주에는 작성한 내용이 없어요",
                color = MeowColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            selection.error != null -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = selection.error, color = MeowColors.Error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlineButton(text = "다시 시도", onClick = onRetry)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MeowColors.Brand, strokeWidth = 2.dp)
                Text("문서를 읽는 중이에요…", color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LinkText("이번 주로 돌아가기", onClick = onBack, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            doc.webUrl?.let { url -> OutlineButton(text = "원본 보기 ↗", onClick = { onOpenUrl(url) }) }
        }
    }
}

@Composable
private fun ReadOnlyCellRow(label: String, lines: List<String>) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MeowColors.Background)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(text = label, color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MeowColors.GlassBorder))
        Box(modifier = Modifier.weight(1f).heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (lines.isEmpty()) {
                Text("—", color = MeowColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                SelectionContainer {
                    Text(
                        text = lines.joinToString("\n"),
                        color = MeowColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 21.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CellRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    draft: Boolean,
    enabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(MeowColors.Background)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = label, color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (draft) StatusBadge(text = "초안", color = MeowColors.Warning)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MeowColors.GlassBorder))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
            textStyle = TextStyle(color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
            cursorBrush = SolidColor(MeowColors.Brand),
            decorationBox = { field ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = MeowColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    field()
                }
            },
        )
    }
}

@Composable
private fun ConfirmPublishDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeowColors.TextPrimary.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
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
            Text("문서에 반영", color = MeowColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "공용 문서의 내 칸(실적 · 계획)만 바꿔요. 반영할까요?",
                color = MeowColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                OutlineButton(text = "취소", onClick = onDismiss)
                BrandButton(text = "반영", onClick = onConfirm)
            }
        }
    }
}

// ---- 작은 구성 요소 ----

@Composable
private fun WeeklyCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.glassSurface(corner = 20.dp).padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit, fontSize: androidx.compose.ui.unit.TextUnit = 11.sp) {
    Text(
        text = text,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick).padding(horizontal = 2.dp),
        color = MeowColors.Brand,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        color = MeowColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BrandButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .alpha(if (enabled || loading) 1f else 0.45f)
            .clip(shape)
            .background(MeowColors.Brand)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
        Text(text = text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .alpha(if (enabled || loading) 1f else 0.45f)
            .clip(shape)
            .background(MeowColors.Surface)
            .border(1.dp, MeowColors.GlassBorder, shape)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = MeowColors.Brand, strokeWidth = 2.dp)
        Text(text = text, color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
