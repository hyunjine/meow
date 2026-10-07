package com.aivn.meow.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors

/** 메인 영역 공통 여백 · 최대 폭. 모든 화면이 같은 자리에 제목 줄을 두도록 함께 쓴다. */
val PageHorizontalPadding = 40.dp
val PageVerticalPadding = 32.dp
val PageMaxWidth = 1120.dp

/**
 * 모든 화면 공통 제목 줄. 좌: 제목 (+ 부제), 우: 마지막 동기화 상태 + [동기화] 버튼.
 * [syncOk] 가 false 면 상태 점을 Warning 색으로 칠한다.
 */
@Composable
fun PageHeader(
    title: String,
    syncLabel: String,
    syncValue: String,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    syncOk: Boolean = true,
    syncing: Boolean = false,
    syncEnabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, color = MeowColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(text = it, color = MeowColors.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SyncStatus(label = syncLabel, value = syncValue, ok = syncOk)
            SyncButton(onClick = onSync, syncing = syncing, enabled = syncEnabled && !syncing)
        }
    }
}

@Composable
private fun SyncStatus(label: String, value: String, ok: Boolean) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (ok) MeowColors.Success else MeowColors.Warning),
            )
            Text(text = label, color = MeowColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = value,
            color = MeowColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SyncButton(onClick: () -> Unit, syncing: Boolean, enabled: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val shadowColor = MeowColors.Brand.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .alpha(if (enabled || syncing) 1f else 0.45f)
            .shadow(elevation = 8.dp, shape = shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(shape)
            .background(MeowColors.Brand)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (syncing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(text = "동기화", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
