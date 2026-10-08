package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.data.RepoUniverse
import com.aivn.meow.data.repoOwner
import com.aivn.meow.data.repoShortName
import com.aivn.meow.theme.MeowColors

private val SegmentTrack = Color(0xFFF1F2F6)
private val OrgBadgeBg = Color(0xFFEEF1FF)
private val OrgBadgeText = Color(0xFF3D5EFF)
private val PersonalBadgeBg = Color(0xFFF1EBFF)
private val PersonalBadgeText = Color(0xFF7C4DFF)
private val StarOn = Color(0xFFF5A623)

/** 관리 모달의 세그먼트 탭 하나. [repos] 는 `owner/name`. */
internal data class RepoManagerTab(val label: String, val repos: List<String>)

/**
 * #127 관리 모달 탭: 즐겨찾기 · 전체 · 조직 · 개인(login).
 * 전체 = 조직 레포(이름순) + 개인 레포(최근 갱신순) + 목록에 없는 즐겨찾기. 레포 목록을 아직 못 불러왔으면 즐겨찾기만 보인다.
 */
internal fun repoManagerTabs(universe: RepoUniverse?, favorites: List<String>, fallbackOrg: String): List<RepoManagerTab> {
    val org = universe?.org ?: fallbackOrg
    val orgRepos = universe?.orgRepos.orEmpty()
    val personalRepos = universe?.personalRepos.orEmpty()
    val all = (orgRepos + personalRepos + favorites).distinct()
    return buildList {
        add(RepoManagerTab("즐겨찾기", favorites))
        add(RepoManagerTab("전체", all))
        add(RepoManagerTab(org, orgRepos))
        universe?.login?.let { add(RepoManagerTab(it, personalRepos)) }
    }
}

/**
 * #127 레포 즐겨찾기 모달 (같은 창 위 오버레이). 여기서는 즐겨찾기만 한다 — 사이드바 체크는 GitHub 화면에서.
 * ☆/★ 토글은 즉시 [onToggleFavorite] 로 반영 · 저장된다. 완료 · ✕ · Esc · 바깥 dim 클릭으로 닫는다.
 */
@Composable
internal fun RepoManagerDialog(
    universe: RepoUniverse?,
    fallbackOrg: String,
    favorites: List<String>,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var tabIndex by remember { mutableStateOf(0) }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { searchFocus.requestFocus() }

    val tabs = repoManagerTabs(universe, favorites, fallbackOrg)
    val tab = tabs[tabIndex.coerceIn(0, tabs.lastIndex)]
    val org = universe?.org ?: fallbackOrg
    val visible = tab.repos.filter { it.contains(query.trim(), ignoreCase = true) }

    BoxWithConstraints(
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
        val cardShape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .heightIn(max = minOf(640.dp, maxHeight * 0.9f))
                .shadow(elevation = 32.dp, shape = cardShape)
                .background(MeowColors.Surface, cardShape)
                // 카드 안 클릭이 dim 으로 전달돼 모달이 닫히지 않도록 소비한다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "레포 즐겨찾기",
                    modifier = Modifier.weight(1f),
                    color = MeowColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = MeowColors.TextTertiary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(4.dp).size(20.dp),
                )
            }
            SegmentedTabs(tabs = tabs, selectedIndex = tabIndex, onSelect = { tabIndex = it })
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                singleLine = true,
                textStyle = TextStyle(color = MeowColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(MeowColors.Brand),
                decorationBox = { field ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MeowColors.Background)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, tint = MeowColors.TextTertiary, modifier = Modifier.size(16.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    "${tab.label} 레포 검색",
                                    color = MeowColors.TextTertiary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            field()
                        }
                    }
                },
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (visible.isEmpty()) {
                    Text(
                        text = when {
                            universe == null && tabIndex != 0 -> "레포 목록을 불러오는 중이에요"
                            else -> "조건에 맞는 레포가 없어요"
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = MeowColors.TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    visible.forEach { repo ->
                        RepoFavoriteRow(
                            fullName = repo,
                            isOrg = repoOwner(repo).equals(org, ignoreCase = true),
                            favorite = repo in favorites,
                            onToggle = { onToggleFavorite(repo) },
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "☆ 를 눌러 즐겨찾기에 추가해요 · 사이드바 표시는 GitHub 화면에서 체크",
                    modifier = Modifier.weight(1f),
                    color = MeowColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "완료",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MeowColors.Brand)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MeowColors.Surface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** 회색 둥근 트랙 위 세그먼트 탭. 선택된 칸은 흰 바탕으로 떠 보인다. */
@Composable
private fun SegmentedTabs(tabs: List<RepoManagerTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SegmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(9.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.shadow(elevation = 2.dp, shape = shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) MeowColors.Surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tab.label,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (selected) MeowColors.TextPrimary else MeowColors.TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tab.repos.size.toString(),
                    color = if (selected) MeowColors.Brand else MeowColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RepoFavoriteRow(fullName: String, isOrg: Boolean, favorite: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = repoShortName(fullName),
                modifier = Modifier.weight(1f, fill = false),
                color = MeowColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = repoOwner(fullName),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isOrg) OrgBadgeBg else PersonalBadgeBg)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                color = if (isOrg) OrgBadgeText else PersonalBadgeText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorite) "즐겨찾기 해제" else "즐겨찾기",
            tint = if (favorite) StarOn else MeowColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
    }
}
