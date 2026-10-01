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

/** 레포 목록 사이드바. 맨 위 '전체'(= [selectedRepo] null) 와 레포별 행 중 하나를 고른다. */
@Composable
internal fun RepoSidebar(
    repos: List<RepoInfo>,
    totalCount: Int,
    selectedRepo: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassSurface(corner = 24.dp)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "레포지토리",
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            color = MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        RepoRow(name = "전체", color = null, count = totalCount, selected = selectedRepo == null, onClick = { onSelect(null) })
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
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = if (selected) MeowColors.Brand else MeowColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = count.toString(),
            color = if (selected) MeowColors.Brand else MeowColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
