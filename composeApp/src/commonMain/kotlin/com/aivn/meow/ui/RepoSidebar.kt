package com.aivn.meow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** 사이드바 레포 한 줄. [count] 는 모든 탭 항목 중 이 레포 항목 수 (url 중복 제외). */
internal data class RepoInfo(val name: String, val color: Color, val count: Int)

/**
 * 즐겨찾기 레포 사이드바. 맨 위 '전체'(= [selectedRepo] null) 와 즐겨찾기 레포 행 중 하나를 고른다.
 * [repos] 는 즐겨찾기 레포만 (항목이 0개여도 포함). '관리' 는 즐겨찾기 관리 모달을 연다.
 */
@Composable
internal fun RepoSidebar(
    repos: List<RepoInfo>,
    totalCount: Int,
    selectedRepo: String?,
    onSelect: (String?) -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "즐겨찾기",
                modifier = Modifier.weight(1f),
                color = MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "관리",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onManage)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = MeowColors.Brand,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        RepoRow(name = "전체", color = null, count = totalCount, selected = selectedRepo == null, onClick = { onSelect(null) })
        if (repos.isEmpty()) {
            Text(
                text = "관리에서 레포를 즐겨찾기해 보세요",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                color = MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        repos.forEach { repo ->
            RepoRow(
                name = repo.name,
                color = repo.color,
                count = repo.count,
                selected = repo.name == selectedRepo,
                onClick = { onSelect(repo.name) },
            )
        }
    }
}

@Composable
private fun RepoRow(name: String, color: Color?, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MeowColors.Brand.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        color?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(it)) }
        // 항목이 없는 즐겨찾기 레포는 이름을 흐리게 하고 개수를 숨긴다
        val empty = count == 0 && color != null
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = when {
                selected -> MeowColors.Brand
                empty -> MeowColors.TextTertiary
                else -> MeowColors.TextPrimary
            },
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!empty) {
            Text(
                text = count.toString(),
                color = if (selected) MeowColors.Brand else MeowColors.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
