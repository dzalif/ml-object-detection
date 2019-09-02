package com.kucingselfie.objectdetectionsample

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import androidx.camera.core.ImageProxy
import androidx.core.graphics.toRectF
import androidx.lifecycle.ViewModel
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.firebase.ml.vision.objects.FirebaseVisionObjectDetectorOptions
import de.crysxd.cameraXTracker.ThreadedImageAnalyzer
import de.crysxd.cameraXTracker.ar.ArObject
import de.crysxd.cameraXTracker.ar.ArObjectTracker
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

class ClassifyImageViewModel : ViewModel(), ThreadedImageAnalyzer {

    val arObjectTracker = ArObjectTracker()
    private val isBusy = AtomicBoolean(false)
    private val handlerThread = HandlerThread("ClasssifyImageViewModel").apply { start() }
    private val uiHandler = Handler(Looper.getMainLooper())
    private val objectDetector = FirebaseVision.getInstance().getOnDeviceObjectDetector(
        FirebaseVisionObjectDetectorOptions.Builder()
            .setDetectorMode(FirebaseVisionObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
    )



    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        if (image.image != null && isBusy.compareAndSet(false, true)) {
            val rotation = rotationDegreesToFirebaseRotation(rotationDegrees)
            val visionImage = FirebaseVisionImage.fromMediaImage(image.image!!, rotation)
            val imageSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Size(image.width, image.height)
            } else {
                TODO("VERSION.SDK_INT < LOLLIPOP")
            }
            objectDetector.processImage(visionImage).addOnCompleteListener {
                isBusy.set(false)
                // Error? Log it :(
                if (it.exception != null) {
                    Timber.e(it.exception)
                    return@addOnCompleteListener
                }

                // Get the first object of CATEGORY_FASHION_GOOD (first = most prominent) or the already tracked object
                val o = it.result?.firstOrNull { o ->
                    o.classificationCategory == FirebaseVisionObject.CATEGORY_FASHION_GOOD && o.trackingId != null
                }

                // Hand the object to the tracker. It will interpolate the path and ensure a fluent visual even if we dropped
                // frames because the detection was too slow
                uiHandler.post {
                    arObjectTracker.processObject(
                        if (o != null) {
                            ArObject(
                                trackingId = o.trackingId ?: -1,
                                boundingBox = o.boundingBox.toRectF(),
                                sourceSize = imageSize,
                                sourceRotationDegrees = rotationDegrees
                            )
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }

    override fun getHandler(): Handler = Handler(handlerThread.looper)

    private fun rotationDegreesToFirebaseRotation(rotationDegrees: Int) = when (rotationDegrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw IllegalArgumentException("Rotation $rotationDegrees not supported")
    }
}