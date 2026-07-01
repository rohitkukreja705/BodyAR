package com.hftx.bodyar

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

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
        ClothingItem("shirt_$n", ClothingCategory.SHIRT, name, drawableIdFor("shirt_$n"))
    }

    val pants: List<ClothingItem> = pantNames.mapIndexed { i, name ->
        val n = i + 1
        ClothingItem("pant_$n", ClothingCategory.PANT, name, drawableIdFor("pant_$n"))
    }

    val sunglasses: List<ClothingItem> = glassNames.mapIndexed { i, name ->
        val n = i + 1
        ClothingItem("glass_$n", ClothingCategory.SUNGLASSES, name, drawableIdFor("glass_$n"))
    }

    private fun builtInFor(category: ClothingCategory): List<ClothingItem> = when (category) {
        ClothingCategory.SHIRT -> shirts
        ClothingCategory.PANT -> pants
        ClothingCategory.SUNGLASSES -> sunglasses
    }

    /** Built-in placeholder wardrobe only (no context/custom items needed). */
    fun itemsFor(category: ClothingCategory): List<ClothingItem> = builtInFor(category)

    /** Built-in items followed by anything the user has uploaded for this category. */
    fun itemsFor(context: Context, category: ClothingCategory): List<ClothingItem> =
        builtInFor(category) + loadCustomItems(context, category)

    // ------------------------------------------------------------- Custom (uploaded) items

    private fun folderName(category: ClothingCategory) = when (category) {
        ClothingCategory.SHIRT -> "custom_shirts"
        ClothingCategory.PANT -> "custom_pants"
        ClothingCategory.SUNGLASSES -> "custom_glasses"
    }

    private fun customDir(context: Context, category: ClothingCategory): File {
        val dir = File(context.filesDir, folderName(category))
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadCustomItems(context: Context, category: ClothingCategory): List<ClothingItem> {
        val dir = customDir(context, category)
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        return files.sortedBy { it.name }.mapIndexed { index, file ->
            ClothingItem(
                id = "custom_${category.name}_${file.name}",
                category = category,
                displayName = "My Upload ${index + 1}",
                filePath = file.absolutePath
            )
        }
    }

    /**
     * Copies the picked image into app-private storage so it survives the
     * picker's temporary content:// grant, and returns the new item.
     * Returns null if the copy failed.
     */
    fun saveCustomItem(context: Context, category: ClothingCategory, sourceUri: Uri): ClothingItem? {
        return try {
            val dir = customDir(context, category)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(java.util.Date())
            val destFile = File(dir, "$stamp.png")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            ClothingItem(
                id = "custom_${category.name}_${destFile.name}",
                category = category,
                displayName = "My Upload",
                filePath = destFile.absolutePath
            )
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCustomItem(item: ClothingItem): Boolean {
        val path = item.filePath ?: return false
        return File(path).delete()
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
