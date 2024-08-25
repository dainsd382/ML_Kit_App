package com.example.testmlkitandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.Bundle
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

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(
        private val overlayView: OverlayView,
        private val cameraSelector: CameraSelector
    ) : ImageAnalysis.Analyzer {

        private val faceDetector: FaceDetector by lazy {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
        }

        private val expandFactor = 0.35f
        private val shrinkFactor = 0.015f

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
                    } else {
                        overlayView.setBoxColor(android.graphics.Color.RED)
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

            return RectF(mirroredLeft, top * scaleY, mirroredRight, bottom * scaleY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
