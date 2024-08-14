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
import androidx.camera.core.ExperimentalGetImage
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

class meditationActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var startButton: Button
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meditation)

        previewView = findViewById(R.id.viewFinder)
        startButton = findViewById(R.id.recordButton)

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        startButton.setOnClickListener {
            // Code to start face detection or other functionality
            Toast.makeText(this, "Button clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .apply {
                    val overlayView = findViewById<OverlayView>(R.id.overlay)
                    setAnalyzer(cameraExecutor, FaceAnalyzer(overlayView))
                }

            // Chuyển từ camera sau sang camera trước
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }


    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(private val overlayView: OverlayView) : ImageAnalysis.Analyzer {
        private val faceDetector: FaceDetector by lazy {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // Chế độ hiệu suất chính xác nhất
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)  // Phát hiện tất cả các landmarks trên khuôn mặt
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)  // Chế độ phân loại tất cả
                .build()
            FaceDetection.getClient(options)
        }

        override fun analyze(image: ImageProxy) {
            val mediaImage = image.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            // Cập nhật màu của paint để vẽ bounding box màu xanh
                            overlayView.setBoxColor(android.graphics.Color.GREEN)

                            val boundingBoxes = faces.map { face ->
                                val boundingBox = face.boundingBox

                                // Tỷ lệ giữa kích thước 640x640 và kích thước thực tế của hình ảnh từ camera
                                val scaleX = overlayView.width / 640f
                                val scaleY = overlayView.height / 640f

                                // Nếu đang sử dụng camera trước, lật bounding box theo chiều ngang
                                val mirroredLeft = (640f - boundingBox.right.toFloat()) * scaleX
                                val mirroredRight = (640f - boundingBox.left.toFloat()) * scaleX

                                RectF(
                                    mirroredLeft,
                                    boundingBox.top.toFloat() * scaleY,
                                    mirroredRight,
                                    boundingBox.bottom.toFloat() * scaleY
                                )
                            }
                            overlayView.updateBoxes(boundingBoxes)
                            // Thông báo khi phát hiện khuôn mặt
                            Toast.makeText(this@meditationActivity, "Detected ${faces.size} face(s)", Toast.LENGTH_SHORT).show()
                        } else {
                            // Không phát hiện được khuôn mặt nào, đổi lại màu đỏ
                            overlayView.setBoxColor(android.graphics.Color.RED)
                            Toast.makeText(this@meditationActivity, "No faces detected", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { _ ->
                        overlayView.setBoxColor(android.graphics.Color.RED)  // Đổi lại màu đỏ khi thất bại
                        Toast.makeText(this@meditationActivity, "Face detection failed", Toast.LENGTH_SHORT).show()
                    }
                    .addOnCompleteListener {
                        image.close()
                    }
            } else {
                image.close() // Đảm bảo rằng hình ảnh được đóng nếu null để tránh rò rỉ bộ nhớ
            }
        }
    }








    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
