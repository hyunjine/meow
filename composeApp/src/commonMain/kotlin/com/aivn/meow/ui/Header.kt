package com.aivn.meow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aivn.meow.theme.MeowColors
import com.aivn.meow.theme.glassSurface
import meow.composeapp.generated.resources.Res
import meow.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

@Composable
fun DashboardHeader(
    lastSyncLabel: String,
    userInitials: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
) {
    Row(
        modifier = modifier
            .glassSurface(corner = 26.dp)
            .padding(horizontal = 28.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MeowColors.GlassSurfaceStrong)
                    .border(1.dp, MeowColors.GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)),
                    )
                    Text(
                        text = "Meow",
                        color = MeowColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = "PR Review Dashboard",
                color = MeowColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MeowColors.GlassSurfaceStrong)
                    .border(1.dp, MeowColors.GlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MeowColors.Success),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "마지막 동기화",
                        color = MeowColors.TextTertiary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = lastSyncLabel,
                        color = MeowColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            UserAvatar(avatarUrl = avatarUrl, initials = userInitials)
        }
    }
}

/** 44dp 원형 아바타. 로딩 중이거나 실패하면 이니셜 원으로 폴백. */
@Composable
private fun UserAvatar(avatarUrl: String?, initials: String) {
    val avatar by produceState<ImageBitmap?>(initialValue = null, avatarUrl) {
        value = avatarUrl?.takeIf { it.isNotBlank() }?.let { loadImageBitmap(it) }
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MeowColors.Brand),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = avatar
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "GitHub 아바타",
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
