package com.suhrud.docsy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suhrud.docsy.ui.theme.TextPrimary

@Composable
fun ProfileIconView(
    iconIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        AvatarView(avatarIndex = iconIndex, size = size)
    }
}
