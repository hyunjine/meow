package com.aivn.meow.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.glassSurface(
    corner: Dp = 24.dp,
    fill: Color = MeowColors.GlassSurface,
    borderColor: Color = MeowColors.GlassBorder,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 12.dp,
    shadowColor: Color = Color(0x141A1F4D),
): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .shadow(elevation = elevation, shape = shape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
        .background(fill, shape)
        .border(BorderStroke(borderWidth, borderColor), shape)
        .clip(shape)
}
