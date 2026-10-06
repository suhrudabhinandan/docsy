package com.suhrud.docsy.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.suhrud.docsy.R

data class AvatarItem(
    val id: String,
    @DrawableRes val drawableRes: Int,
    @StringRes val contentDescriptionRes: Int
)

object AvatarCatalog {
    val items: List<AvatarItem> = listOf(
        AvatarItem("avatar_01", R.drawable.avatar_01, R.string.avatar_desc_01),
        AvatarItem("avatar_02", R.drawable.avatar_02, R.string.avatar_desc_02),
        AvatarItem("avatar_03", R.drawable.avatar_03, R.string.avatar_desc_03),
        AvatarItem("avatar_04", R.drawable.avatar_04, R.string.avatar_desc_04),
        AvatarItem("avatar_05", R.drawable.avatar_05, R.string.avatar_desc_05),
        AvatarItem("avatar_06", R.drawable.avatar_06, R.string.avatar_desc_06),
        AvatarItem("avatar_07", R.drawable.avatar_07, R.string.avatar_desc_07),
        AvatarItem("avatar_08", R.drawable.avatar_08, R.string.avatar_desc_08),
        AvatarItem("avatar_09", R.drawable.avatar_09, R.string.avatar_desc_09),
        AvatarItem("avatar_10", R.drawable.avatar_10, R.string.avatar_desc_10),
        AvatarItem("avatar_11", R.drawable.avatar_11, R.string.avatar_desc_11),
        AvatarItem("avatar_12", R.drawable.avatar_12, R.string.avatar_desc_12),
        AvatarItem("avatar_13", R.drawable.avatar_13, R.string.avatar_desc_13),
        AvatarItem("avatar_14", R.drawable.avatar_14, R.string.avatar_desc_14),
        AvatarItem("avatar_15", R.drawable.avatar_15, R.string.avatar_desc_15),
        AvatarItem("avatar_16", R.drawable.avatar_16, R.string.avatar_desc_16),
        AvatarItem("avatar_17", R.drawable.avatar_17, R.string.avatar_desc_17)
    )

    val size: Int get() = items.size

    fun getById(id: String?): AvatarItem {
        if (id == null) return items[0]
        return items.find { it.id == id } ?: items[0]
    }

    fun migrateIndexToId(index: Int): String {
        val clamped = (index % items.size).coerceAtLeast(0)
        return items[clamped].id
    }
}
