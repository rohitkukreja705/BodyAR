package com.hftx.bodyar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.Pose

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pose: Pose? = null
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    var isFrontCamera: Boolean = true

    private var shirtBitmap: Bitmap? = null
    private var pantBitmap: Bitmap? = null
    private var glassesBitmap: Bitmap? = null

    private val bitmapCache = HashMap<String, Bitmap>()

    fun setSelectedShirt(item: ClothingItem?) {
        shirtBitmap = item?.let { loadBitmap(it) }
        invalidate()
    }

    fun setSelectedPant(item: ClothingItem?) {
        pantBitmap = item?.let { loadBitmap(it) }
        invalidate()
    }

    fun setSelectedGlasses(item: ClothingItem?) {
        glassesBitmap = item?.let { loadBitmap(it) }
        invalidate()
    }

    /** Called by [CameraPoseAnalyzer] on the main thread for every processed frame. */
    fun updatePose(newPose: Pose?, imgWidth: Int, imgHeight: Int) {
        pose = newPose
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    fun currentShirtBitmap() = shirtBitmap
    fun currentPantBitmap() = pantBitmap
    fun currentGlassesBitmap() = glassesBitmap
    fun currentPose() = pose
    fun currentImageSize() = Pair(imageWidth, imageHeight)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = pose ?: return
        if (imageWidth == 0 || imageHeight == 0) return
        if (shirtBitmap == null && pantBitmap == null && glassesBitmap == null) return

        val mapper = CoordinateMapper(imageWidth, imageHeight, width, height, isFrontCamera)
        PoseGarmentRenderer.draw(canvas, p, mapper, shirtBitmap, pantBitmap, glassesBitmap)
    }

    private fun loadBitmap(item: ClothingItem): Bitmap? {
        bitmapCache[item.id]?.let { return it }

        val bitmap: Bitmap? = if (item.isCustom) {
            item.filePath?.let { BitmapFactory.decodeFile(it) }
        } else {
            val drawable = ContextCompat.getDrawable(context, item.drawableRes) ?: return null
            val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 200
            val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 200
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp
        }

        if (bitmap != null) bitmapCache[item.id] = bitmap
        return bitmap
    }
}
