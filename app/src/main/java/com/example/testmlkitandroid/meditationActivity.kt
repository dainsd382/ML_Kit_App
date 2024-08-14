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

    // Khai báo các biến cần thiết cho PreviewView, nút bắt đầu và quản lý quyền
    private lateinit var previewView: PreviewView
    private lateinit var startButton: Button
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // Executor để xử lý ảnh trên một luồng riêng
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meditation)

        // Khởi tạo các view và biến
        previewView = findViewById(R.id.viewFinder)
        startButton = findViewById(R.id.recordButton)

        // Đăng ký launcher để yêu cầu quyền truy cập camera
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        // Kiểm tra quyền truy cập camera và bắt đầu camera nếu đã được cấp quyền
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Xử lý sự kiện click cho nút bắt đầu
        startButton.setOnClickListener {
            // Code để bắt đầu phát hiện khuôn mặt hoặc chức năng khác
            Toast.makeText(this, "Button clicked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        // Lấy ProcessCameraProvider để quản lý các camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Cấu hình preview để hiển thị hình ảnh từ camera
            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            // Cấu hình phân tích ảnh để phát hiện khuôn mặt
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .apply {
                    val overlayView = findViewById<OverlayView>(R.id.overlay)
                    setAnalyzer(cameraExecutor, FaceAnalyzer(overlayView, CameraSelector.DEFAULT_FRONT_CAMERA))
                }

            // Chọn camera trước
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Ràng buộc lifecycle với camera và các use case (preview và phân tích ảnh)
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(
        private val overlayView: OverlayView,
        private val cameraSelector: CameraSelector
    ) : ImageAnalysis.Analyzer {
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

                // Lấy kích thước thực tế của hình ảnh từ camera
                val imageWidth = mediaImage.width
                val imageHeight = mediaImage.height

                faceDetector.process(inputImage)
                    .addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            overlayView.setBoxColor(android.graphics.Color.GREEN)

                            val boundingBoxes = faces.map { face ->
                                val boundingBox = face.boundingBox

                                // Tính toán tỷ lệ giữa kích thước ảnh gốc và kích thước PreviewView
                                val scaleX = overlayView.width.toFloat() / imageWidth
                                val scaleY = overlayView.height.toFloat() / imageHeight

                                // Mở rộng bounding box một chút (ví dụ: 5%)
                                val expansionFactor = 0.05f
                                val expandedLeft = boundingBox.left - (boundingBox.width() * expansionFactor / 2)
                                val expandedTop = boundingBox.top - (boundingBox.height() * expansionFactor / 2)
                                val expandedRight = boundingBox.right + (boundingBox.width() * expansionFactor / 2)
                                val expandedBottom = boundingBox.bottom + (boundingBox.height() * expansionFactor / 2)

                                // Nếu đang sử dụng camera trước, lật bounding box theo chiều ngang
                                val mirroredLeft = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                                    (imageWidth - expandedRight) * scaleX
                                else expandedLeft * scaleX
                                val mirroredRight = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                                    (imageWidth - expandedLeft) * scaleX
                                else expandedRight * scaleX

                                RectF(
                                    mirroredLeft,
                                    expandedTop * scaleY,
                                    mirroredRight,
                                    expandedBottom * scaleY
                                )
                            }
                            overlayView.updateBoxes(boundingBoxes)
                            Toast.makeText(this@meditationActivity, "Detected ${faces.size} face(s)", Toast.LENGTH_SHORT).show()
                        } else {
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
        cameraExecutor.shutdown() // Đóng executor khi activity bị hủy
    }
}
