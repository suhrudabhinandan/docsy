package com.suhrud.docsy.ui.avatar

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suhrud.docsy.R

data class AvatarItem(
    val id: String,
    @DrawableRes val drawableRes: Int,
    val contentDescription: String
)

object AvatarCatalog {

    val avatars: List<AvatarItem> = listOf(
        AvatarItem("avatar_01", R.drawable.avatar_01, "Woman in sari"),
        AvatarItem("avatar_02", R.drawable.avatar_02, "Boy with acoustic guitar"),
        AvatarItem("avatar_03", R.drawable.avatar_03, "Boy with headphones and hoodie"),
        AvatarItem("avatar_04", R.drawable.avatar_04, "Girl with glasses reading book"),
        AvatarItem("avatar_05", R.drawable.avatar_05, "Girl with camera"),
        AvatarItem("avatar_06", R.drawable.avatar_06, "Boy with cap and backpack"),
        AvatarItem("avatar_07", R.drawable.avatar_07, "Girl with headphones and mug"),
        AvatarItem("avatar_08", R.drawable.avatar_08, "Boy with laptop and books"),
        AvatarItem("avatar_09", R.drawable.avatar_09, "Boy reading book with mug"),
        AvatarItem("avatar_10", R.drawable.avatar_10, "Girl with glasses at desk with laptop"),
        AvatarItem("avatar_11", R.drawable.avatar_11, "Man with beard, glasses and mug"),
        AvatarItem("avatar_12", R.drawable.avatar_12, "Elder woman with glasses and sari"),
        AvatarItem("avatar_13", R.drawable.avatar_13, "Girl coding with computer science books"),
        AvatarItem("avatar_14", R.drawable.avatar_14, "Boy at gym with shaker"),
        AvatarItem("avatar_15", R.drawable.avatar_15, "Elder man with white beard and mug"),
        AvatarItem("avatar_16", R.drawable.avatar_16, "Boy singing with guitar and microphone"),
        AvatarItem("avatar_17", R.drawable.avatar_17, "Boy studying at desk with notebook")
    )

    val defaultAvatar: AvatarItem = avatars.first()

    fun getAvatar(id: String?): AvatarItem {
        if (id.isNullOrBlank()) return defaultAvatar
        return avatars.find { it.id == id } ?: defaultAvatar
    }
}

@Composable
fun ProfileAvatar(
    avatarId: String?,
    size: Dp,
    ring: Boolean = false,
    modifier: Modifier = Modifier
) {
    val avatar = AvatarCatalog.getAvatar(avatarId)
    val ringBorderWidth = if (ring) 2.dp else 0.dp
    val ringColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (ring) {
                    Modifier.border(ringBorderWidth, ringColor, CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = avatar.drawableRes),
            contentDescription = avatar.contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    }
}
