package com.example.testmlkitandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

class MeditationActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var startButton: Button
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meditation)

        previewView = findViewById(R.id.viewFinder)
        startButton = findViewById(R.id.recordButton)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                showToast("Camera permission denied")
            }
        }

        if (isCameraPermissionGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        startButton.setOnClickListener {
            showToast("Button clicked")
        }
    }

    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .apply {
                    val overlayView = findViewById<OverlayView>(R.id.overlay)
                    setAnalyzer(cameraExecutor, FaceAnalyzer(overlayView, CameraSelector.DEFAULT_FRONT_CAMERA))
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private var currentToast: Toast? = null

    private fun showToast(message: String) {
        currentToast?.cancel() // Cancel the previous toast if any
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }


    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(
        private val overlayView: OverlayView,
        private val cameraSelector: CameraSelector
    ) : ImageAnalysis.Analyzer {

        // Lazy initialization of the FaceDetector with performance mode set to FAST
        private val faceDetector: FaceDetector by lazy {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    // Prioritizes accuracy over speed
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    // Prioritizes speed over accuracy
                    // .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
            )
        }

        // Factors to expand and shrink the bounding box for better visualization
        private val expandFactor = 0.35f
        private val shrinkFactor = 0.015f

        // Variable to store the last detected number of faces
        private var lastDetectedFaces = -1

        override fun analyze(image: ImageProxy) {
            val mediaImage = image.image ?: run {
                image.close()
                return
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            val imageWidth = mediaImage.height
            val imageHeight = mediaImage.width

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val boundingBoxes = faces.map { face ->
                            val boundingBox = face.boundingBox
                            calculateBoundingBox(boundingBox, imageWidth, imageHeight)
                        }

                        overlayView.setBoxColor(android.graphics.Color.GREEN)
                        overlayView.updateBoxes(boundingBoxes)

                        // Display a toast message only if the number of faces has changed
                        if (faces.size != lastDetectedFaces) {
                            showToast("Detected ${faces.size} face(s)")
                            lastDetectedFaces = faces.size
                        }
                    } else {
                        overlayView.setBoxColor(android.graphics.Color.RED)
                        // Clear bounding boxes when no faces are detected
                        overlayView.updateBoxes(emptyList())

                        // Display a toast message only if the number of faces has changed
                        if (0 != lastDetectedFaces) {
                            showToast("Detected 0 face(s)")
                            lastDetectedFaces = 0
                        }
                    }
                }
                .addOnFailureListener {
                    overlayView.setBoxColor(android.graphics.Color.RED)
                    showToast("Face detection failed")
                }
                .addOnCompleteListener {
                    image.close()
                }
        }

        private fun calculateBoundingBox(boundingBox: android.graphics.Rect, imageWidth: Int, imageHeight: Int): RectF {
            val scaleX = overlayView.width.toFloat() / imageWidth
            val scaleY = overlayView.height.toFloat() / imageHeight

            val left = boundingBox.left - boundingBox.width() * expandFactor
            val top = boundingBox.top + boundingBox.height() * shrinkFactor
            val right = boundingBox.right + boundingBox.width() * expandFactor
            val bottom = boundingBox.bottom - boundingBox.height() * shrinkFactor

            val mirroredLeft = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                (imageWidth - right) * scaleX else left * scaleX
            val mirroredRight = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                (imageWidth - left) * scaleX else right * scaleX

            // Log bounding box values
            Log.d("BoundingBox", "Original Bounding Box: left=$left, top=$top, right=$right, bottom=$bottom")
            Log.d("BoundingBox", "Mirrored: left=$mirroredLeft, right=$mirroredRight")
            Log.d("BoundingBox", "Scale: scaleX=$scaleX, scaleY=$scaleY")
            Log.d("BoundingBox", "OverlayView Size: width=${overlayView.width}, height=${overlayView.height}")

            return RectF(mirroredLeft, top * scaleY, mirroredRight, bottom * scaleY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
