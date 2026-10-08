package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import com.aivn.meow.ui.common.MeowType

/** #127 사이드바 레포 한 줄. [fullName] = `owner/name`, [count] 는 모든 탭 항목 중 이 레포 항목 수 (url 중복 제외). */
internal data class SidebarRepo(val fullName: String, val name: String, val checked: Boolean, val count: Int)

private val CheckedRowTint = Color(0xFFF3F5FF)
private val UncheckedText = Color(0xFF9AA0BD)
private val UncheckedMuted = Color(0xFFC9CCDA)

/**
 * #127 즐겨찾기 레포 사이드바. 즐겨찾기 순서대로 체크 목록을 보여주고, 행 · 체크박스를 누르면 체크를 토글한다.
 * 오른쪽 내용은 체크한 레포의 항목만 보여준다. '관리' 는 즐겨찾기 관리 모달을 연다.
 */
@Composable
internal fun RepoSidebar(
    repos: List<SidebarRepo>,
    onToggle: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassSurface(corner = 24.dp)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "즐겨찾기",
                modifier = Modifier.weight(1f),
                style = MeowType.Meta,
                color = MeowColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${repos.count { it.checked }}/${repos.size}",
                style = MeowType.Meta,
                color = MeowColors.TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "관리",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MeowType.Meta,
                color = MeowColors.Brand,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (repos.isEmpty()) {
            Text(
                text = "관리에서 레포를 즐겨찾기해 보세요",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = MeowType.Meta,
                color = MeowColors.TextTertiary,
            )
        }
        repos.forEach { repo ->
            RepoCheckRow(repo = repo, onClick = { onToggle(repo.fullName) })
        }
    }
}

@Composable
private fun RepoCheckRow(repo: SidebarRepo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (repo.checked) CheckedRowTint else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepoCheckbox(checked = repo.checked)
        Text(
            text = repo.name,
            modifier = Modifier.weight(1f),
            color = if (repo.checked) MeowColors.TextPrimary else UncheckedText,
            fontSize = 14.sp,
            fontWeight = if (repo.checked) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = repo.count.toString(),
            style = MeowType.Badge,
            color = if (repo.checked) MeowColors.Brand else UncheckedMuted,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 18dp · 모서리 5dp 체크박스. 체크 = 브랜드 채움 + 흰 체크, 해제 = 흰 바탕 + 회색 테두리. 클릭은 행이 받는다. */
@Composable
private fun RepoCheckbox(checked: Boolean) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(shape)
            .background(if (checked) MeowColors.Brand else MeowColors.Surface)
            .border(1.5.dp, if (checked) MeowColors.Brand else UncheckedMuted, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Default.Check, contentDescription = "체크됨", tint = MeowColors.Surface, modifier = Modifier.size(13.dp))
        }
    }
}
