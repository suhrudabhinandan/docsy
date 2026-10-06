package com.suhrud.docsy.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ProfileAvatar(
    avatarId: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ringColor: Color? = null,
    ringWidth: Dp = 1.dp
) {
    val item = AvatarCatalog.getById(avatarId)
    val baseModifier = modifier
        .size(size)
        .clip(CircleShape)

    val finalModifier = if (ringColor != null) {
        baseModifier.border(ringWidth, ringColor, CircleShape)
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = item.drawableRes),
            contentDescription = stringResource(id = item.contentDescriptionRes),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun AvatarView(
    avatarIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val avatarId = AvatarCatalog.migrateIndexToId(avatarIndex)
    ProfileAvatar(
        avatarId = avatarId,
        modifier = modifier,
        size = size
    )
}
