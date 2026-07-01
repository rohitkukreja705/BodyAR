package com.hftx.bodyar

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Streams camera frames into ML Kit's on-device pose detector and reports
 * results back on [onPoseDetected], along with the (rotation-corrected)
 * frame dimensions the pose coordinates are expressed in - the overlay
 * needs those to map pose-space -> screen-space.
 */
class CameraPoseAnalyzer(
    private val onPoseDetected: (pose: Pose?, imageWidth: Int, imageHeight: Int) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector: PoseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        // IMPORTANT: InputImage.getWidth()/getHeight() return the RAW buffer
        // dimensions (e.g. 1280x720 landscape sensor output), NOT the
        // dimensions of the upright image the pose landmarks are actually
        // expressed in. On a portrait phone the sensor buffer is landscape
        // and rotationDegrees is typically 90/270, so we have to swap
        // width/height ourselves - otherwise every downstream coordinate
        // mapping (CoordinateMapper) is computed against the wrong aspect
        // ratio and garments render detached from the tracked body.
        val swapped = rotation == 90 || rotation == 270
        val width = if (swapped) mediaImage.height else mediaImage.width
        val height = if (swapped) mediaImage.width else mediaImage.height

        detector.process(inputImage)
            .addOnSuccessListener { pose -> onPoseDetected(pose, width, height) }
            .addOnFailureListener { onPoseDetected(null, width, height) }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = detector.close()
}
