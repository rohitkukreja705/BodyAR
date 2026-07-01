package com.hftx.bodyar

object ClothingRepository {

    private val shirtNames = listOf(
        "Crimson Tee", "Ocean Blue", "Forest Green", "Sunshine Yellow", "Royal Purple",
        "Tangerine", "Cyan Wave", "Indigo Classic", "Berry Pink", "Espresso Brown"
    )

    private val pantNames = listOf(
        "Charcoal", "Slate Grey", "Coffee Brown", "Midnight Navy", "Jet Black",
        "Olive Cargo", "Rust Chino", "Deep Teal", "Plum", "Walnut"
    )

    private val glassNames = listOf(
        "Classic Black", "Tortoise", "Steel Frame", "Crimson Edge",
        "Ocean Blue", "Violet Pop", "Gold Aviator", "Graphite"
    )

    val shirts: List<ClothingItem> = shirtNames.mapIndexed { i, name ->
        val n = i + 1
        ClothingItem(
            id = "shirt_$n",
            category = ClothingCategory.SHIRT,
            displayName = name,
            drawableRes = drawableIdFor("shirt_$n")
        )
    }

    val pants: List<ClothingItem> = pantNames.mapIndexed { i, name ->
        val n = i + 1
        ClothingItem(
            id = "pant_$n",
            category = ClothingCategory.PANT,
            displayName = name,
            drawableRes = drawableIdFor("pant_$n")
        )
    }

    val sunglasses: List<ClothingItem> = glassNames.mapIndexed { i, name ->
        val n = i + 1
        ClothingItem(
            id = "glass_$n",
            category = ClothingCategory.SUNGLASSES,
            displayName = name,
            drawableRes = drawableIdFor("glass_$n")
        )
    }

    fun itemsFor(category: ClothingCategory): List<ClothingItem> = when (category) {
        ClothingCategory.SHIRT -> shirts
        ClothingCategory.PANT -> pants
        ClothingCategory.SUNGLASSES -> sunglasses
    }

    /** Resolves "shirt_3" -> R.drawable.shirt_3 etc. via reflection so the
     *  lists above stay purely data-driven. */
    private fun drawableIdFor(name: String): Int {
        return try {
            R.drawable::class.java.getField(name).getInt(null)
        } catch (e: Exception) {
            0
        }
    }
}
