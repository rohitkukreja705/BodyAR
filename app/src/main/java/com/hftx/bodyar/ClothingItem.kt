package com.hftx.bodyar

import androidx.annotation.DrawableRes

enum class ClothingCategory {
    SHIRT, PANT, SUNGLASSES
}

/**
 * A single selectable wardrobe item.
 *
 * [drawableRes] currently points at a generated placeholder vector asset
 * (see res/drawable/shirt_*.xml, pant_*.xml, glass_*.xml). For production
 * quality, replace these with transparent-background PNG/WebP garment
 * cutouts of the same naming pattern - no code changes needed, the
 * repository below just needs the resource id swapped.
 */
data class ClothingItem(
    val id: String,
    val category: ClothingCategory,
    val displayName: String,
    @DrawableRes val drawableRes: Int
)
