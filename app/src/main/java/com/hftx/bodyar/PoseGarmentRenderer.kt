package com.hftx.bodyar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Maps ML Kit's pose-space coordinates (pixels in the analyzed camera frame)
 * into the coordinate space of whatever we're drawing on to (a live overlay
 * View, or a just-captured full-res photo Bitmap). Both share this class so
 * the "where does the shirt go" math is written exactly once.
 *
 * This follows the same center-crop scale/offset approach used throughout
 * Google's ML Kit + CameraX samples: the analyzed frame is scaled up
 * uniformly until it fully covers the target canvas (matching PreviewView's
 * FILL_CENTER), then centered.
 */
class CoordinateMapper(
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val targetWidth: Int,
    private val targetHeight: Int,
    private val isFrontCamera: Boolean
) {
    private val scaleFactor: Float = maxOf(
        targetWidth.toFloat() / imageWidth,
        targetHeight.toFloat() / imageHeight
    )
    private val offsetX = (targetWidth - imageWidth * scaleFactor) / 2f
    private val offsetY = (targetHeight - imageHeight * scaleFactor) / 2f

    fun mapX(x: Float): Float {
        val mapped = x * scaleFactor + offsetX
        return if (isFrontCamera) targetWidth - mapped else mapped
    }

    fun mapY(y: Float): Float = y * scaleFactor + offsetY

    fun map(point: PointF): PointF = PointF(mapX(point.x), mapY(point.y))

    fun mapDistance(d: Float): Float = d * scaleFactor
}

/** Minimum ML Kit confidence before we trust a landmark enough to anchor clothing to it. */
private const val MIN_LIKELIHOOD = 0.55f

data class GarmentPlacement(val bitmap: Bitmap, val centerX: Float, val centerY: Float, val width: Float, val height: Float, val rotationDegrees: Float)

object PoseGarmentRenderer {

    /** Draws whichever of shirt/pant/sunglasses bitmaps are non-null, anchored to [pose]. */
    fun draw(
        canvas: Canvas,
        pose: Pose,
        mapper: CoordinateMapper,
        shirtBitmap: Bitmap?,
        pantBitmap: Bitmap?,
        glassesBitmap: Bitmap?
    ) {
        shirtPlacement(pose, mapper, shirtBitmap)?.let { drawPlacement(canvas, it) }
        pantPlacement(pose, mapper, pantBitmap)?.let { drawPlacement(canvas, it) }
        glassesPlacement(pose, mapper, glassesBitmap)?.let { drawPlacement(canvas, it) }
    }

    private fun drawPlacement(canvas: Canvas, p: GarmentPlacement) {
        val matrix = Matrix()
        val srcW = p.bitmap.width.toFloat()
        val srcH = p.bitmap.height.toFloat()
        // Scale the source bitmap to the target width/height, centered at origin first.
        matrix.postScale(p.width / srcW, p.height / srcH)
        matrix.postTranslate(-p.width / 2f, -p.height / 2f)
        matrix.postRotate(p.rotationDegrees)
        matrix.postTranslate(p.centerX, p.centerY)
        canvas.drawBitmap(p.bitmap, matrix, null)
    }

    private fun landmark(pose: Pose, type: Int): PoseLandmark? {
        val lm = pose.getPoseLandmark(type) ?: return null
        return if (lm.inFrameLikelihood >= MIN_LIKELIHOOD) lm else null
    }

    private fun angleDegrees(a: PointF, b: PointF): Float =
        Math.toDegrees(atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

    private fun midpoint(a: PointF, b: PointF) = PointF((a.x + b.x) / 2f, (a.y + b.y) / 2f)

    private fun dist(a: PointF, b: PointF) = hypot((b.x - a.x), (b.y - a.y))

    private fun shirtPlacement(pose: Pose, mapper: CoordinateMapper, bitmap: Bitmap?): GarmentPlacement? {
        if (bitmap == null) return null
        val ls = landmark(pose, PoseLandmark.LEFT_SHOULDER) ?: return null
        val rs = landmark(pose, PoseLandmark.RIGHT_SHOULDER) ?: return null
        val lsP = mapper.map(PointF(ls.position.x, ls.position.y))
        val rsP = mapper.map(PointF(rs.position.x, rs.position.y))

        val lh = landmark(pose, PoseLandmark.LEFT_HIP)
        val rh = landmark(pose, PoseLandmark.RIGHT_HIP)
        val shoulderWidth = dist(lsP, rsP)
        val torsoLength = if (lh != null && rh != null) {
            val hipMid = mapper.map(midpoint(
                PointF(lh.position.x, lh.position.y),
                PointF(rh.position.x, rh.position.y)
            ))
            val shoulderMid = midpoint(lsP, rsP)
            dist(shoulderMid, hipMid)
        } else {
            shoulderWidth * 1.35f
        }

        val shoulderMid = midpoint(lsP, rsP)
        val angle = angleDegrees(lsP, rsP)

        // Garment art has ~2 units of horizontal margin baked in around the
        // torso for sleeves, and its collar sits near the top edge - nudge
        // the anchor down slightly so the collar lines up with the shoulders.
        val width = shoulderWidth * 2.25f
        val height = torsoLength * 1.35f
        val centerY = shoulderMid.y + height * 0.42f
        return GarmentPlacement(bitmap, shoulderMid.x, centerY, width, height, angle)
    }

    private fun pantPlacement(pose: Pose, mapper: CoordinateMapper, bitmap: Bitmap?): GarmentPlacement? {
        if (bitmap == null) return null
        val lh = landmark(pose, PoseLandmark.LEFT_HIP) ?: return null
        val rh = landmark(pose, PoseLandmark.RIGHT_HIP) ?: return null
        val lhP = mapper.map(PointF(lh.position.x, lh.position.y))
        val rhP = mapper.map(PointF(rh.position.x, rh.position.y))
        val hipWidth = dist(lhP, rhP)
        val hipMid = midpoint(lhP, rhP)

        val lAnkle = landmark(pose, PoseLandmark.LEFT_ANKLE)
        val rAnkle = landmark(pose, PoseLandmark.RIGHT_ANKLE)
        val lKnee = landmark(pose, PoseLandmark.LEFT_KNEE)
        val rKnee = landmark(pose, PoseLandmark.RIGHT_KNEE)

        val legLength = when {
            lAnkle != null && rAnkle != null -> {
                val ankleMid = mapper.map(midpoint(
                    PointF(lAnkle.position.x, lAnkle.position.y),
                    PointF(rAnkle.position.x, rAnkle.position.y)
                ))
                dist(hipMid, ankleMid)
            }
            lKnee != null && rKnee != null -> {
                val kneeMid = mapper.map(midpoint(
                    PointF(lKnee.position.x, lKnee.position.y),
                    PointF(rKnee.position.x, rKnee.position.y)
                ))
                dist(hipMid, kneeMid) * 2f
            }
            else -> hipWidth * 2.8f
        }

        val angle = angleDegrees(lhP, rhP)
        val width = hipWidth * 2.5f
        val height = legLength * 1.12f
        val centerY = hipMid.y + height * 0.46f
        return GarmentPlacement(bitmap, hipMid.x, centerY, width, height, angle)
    }

    private fun glassesPlacement(pose: Pose, mapper: CoordinateMapper, bitmap: Bitmap?): GarmentPlacement? {
        if (bitmap == null) return null
        val le = landmark(pose, PoseLandmark.LEFT_EYE) ?: return null
        val re = landmark(pose, PoseLandmark.RIGHT_EYE) ?: return null
        val leP = mapper.map(PointF(le.position.x, le.position.y))
        val reP = mapper.map(PointF(re.position.x, re.position.y))
        val eyeMid = midpoint(leP, reP)

        val lEar = landmark(pose, PoseLandmark.LEFT_EAR)
        val rEar = landmark(pose, PoseLandmark.RIGHT_EAR)
        val faceWidth = if (lEar != null && rEar != null) {
            dist(mapper.map(PointF(lEar.position.x, lEar.position.y)), mapper.map(PointF(rEar.position.x, rEar.position.y)))
        } else {
            dist(leP, reP) * 2.6f
        }

        val angle = angleDegrees(leP, reP)
        val width = faceWidth * 1.05f
        val height = width * 0.4f // matches the 100:40 glasses artwork aspect ratio
        return GarmentPlacement(bitmap, eyeMid.x, eyeMid.y, width, height, angle)
    }
}
