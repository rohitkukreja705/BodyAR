package com.hftx.bodyar

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.pose.Pose
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

object CaptureHelper {

    /**
     * Takes a full-resolution photo, draws the currently-selected garments
     * on top (using the most recent pose from the live analyzer), and saves
     * the result to the device gallery under a "BodyAR" album.
     */
    fun captureAndSave(
        context: Context,
        imageCapture: ImageCapture,
        overlay: PoseOverlayView,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val photoBitmap = imageProxyToBitmap(image, overlay.isFrontCamera)
                    image.close()

                    val pose = overlay.currentPose()
                    val (imgW, imgH) = overlay.currentImageSize()
                    val finalBitmap = if (pose != null && imgW > 0 && imgH > 0) {
                        compositeGarments(photoBitmap, pose, imgW, imgH, overlay)
                    } else {
                        photoBitmap
                    }

                    val saved = saveToGallery(context, finalBitmap)
                    if (saved) {
                        onResult(true, context.getString(R.string.saved_toast))
                    } else {
                        onResult(false, "Could not save photo")
                    }
                } catch (e: Exception) {
                    onResult(false, "Capture failed: ${e.message}")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(false, "Capture failed: ${exception.message}")
            }
        })
    }

    private fun compositeGarments(
        photo: Bitmap,
        pose: Pose,
        analysisImageWidth: Int,
        analysisImageHeight: Int,
        overlay: PoseOverlayView
    ): Bitmap {
        val result = photo.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        // The analyzer's pose coordinates are expressed relative to the
        // analysis stream's frame size, which shares the same aspect ratio
        // as the full-res capture (both come from the same sensor output).
        val mapper = CoordinateMapper(
            analysisImageWidth,
            analysisImageHeight,
            result.width,
            result.height,
            overlay.isFrontCamera
        )
        PoseGarmentRenderer.draw(
            canvas,
            pose,
            mapper,
            overlay.currentShirtBitmap(),
            overlay.currentPantBitmap(),
            overlay.currentGlassesBitmap()
        )
        return result
    }

    private fun imageProxyToBitmap(image: ImageProxy, isFrontCamera: Boolean): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotation = image.imageInfo.rotationDegrees
        val matrix = Matrix()
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        // Front camera capture is already mirrored to match what the user
        // saw in the preview - CameraX's ImageCapture does not auto-mirror,
        // so we do it here to match the (mirrored) overlay coordinate mapper.
        if (isFrontCamera) matrix.postScale(-1f, 1f)

        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return bitmap
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        val filename = "BodyAR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BodyAR")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return true
    }
}
