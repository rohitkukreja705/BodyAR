package com.hftx.bodyar

import androidx.annotation.DrawableRes

enum class ClothingCategory {
    SHIRT, PANT, SUNGLASSES
}

/**
 * A single selectable wardrobe item. Exactly one of [drawableRes] / [filePath]
 * is used:
 *  - Built-in placeholder items reference a generated vector asset via [drawableRes].
 *  - User-uploaded items ([isCustom] = true) point at a file copied into app-private
 *    storage via [filePath].
 */
data class ClothingItem(
    val id: String,
    val category: ClothingCategory,
    val displayName: String,
    @DrawableRes val drawableRes: Int = 0,
    val filePath: String? = null
) {
    val isCustom: Boolean get() = filePath != null
}
