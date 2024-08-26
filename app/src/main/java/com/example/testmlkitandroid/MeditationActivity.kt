package com.example.testmlkitandroid

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.media.Image.Plane
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
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

        // Initialize permission launcher for camera access
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                showToast("Camera permission denied")
            }
        }

        // Check for camera permission or request it
        if (isCameraPermissionGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        startButton.setOnClickListener {
            showToast("Button clicked")
        }
    }

    // Check if the camera permission is granted
    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Request camera permission from the user
    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Start the camera and set up image analysis
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Create a Preview use case to display the camera feed
            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            // Create an ImageAnalysis use case for processing image frames
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .apply {
                    val overlayView = findViewById<OverlayView>(R.id.overlay)
                    setAnalyzer(cameraExecutor, FaceAnalyzer(overlayView, CameraSelector.DEFAULT_FRONT_CAMERA))
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Bind the camera lifecycle to the activity with preview and image analysis use cases
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // Handle toast messages to avoid overlapping
    private var currentToast: Toast? = null

    private fun showToast(message: String) {
        currentToast?.cancel()
        currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        currentToast?.show()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(
        private val overlayView: OverlayView,
        private val cameraSelector: CameraSelector
    ) : ImageAnalysis.Analyzer {

        // Initialize the face detector with performance mode
        private val faceDetector: FaceDetector by lazy {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                    .build()
            )
        }

        private val expandFactor = 0.5f // Expand bounding box for better visualization
        private val shrinkFactor = -0.25f // Shrink bounding box if needed

        private var lastDetectedFaces = -1 // Keep track of the number of detected faces
        private var lastSaveTime = 0L // Track last save time for image saving

        override fun analyze(image: ImageProxy) {
            val mediaImage = image.image ?: run {
                image.close()
                return
            }

            // Convert Image to Bitmap
            val bitmap = imageToBitmap(mediaImage)
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            val imageWidth = mediaImage.height
            val imageHeight = mediaImage.width

            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val currentTime = System.currentTimeMillis()
                    if (faces.isNotEmpty()) {
                        val boundingBoxes = faces.map { face ->
                            val boundingBox = face.boundingBox
                            calculateBoundingBox(boundingBox, imageWidth, imageHeight)
                        }

                        overlayView.setBoxColor(android.graphics.Color.GREEN)
                        overlayView.updateBoxes(boundingBoxes)

                        // Display toast if the number of detected faces has changed
                        if (faces.size != lastDetectedFaces) {
                            showToast("Detected ${faces.size} face(s)")
                            lastDetectedFaces = faces.size
                        }

                        // Save the image every 5 seconds
                        if (bitmap != null && (currentTime - lastSaveTime) >= 5000) {
                            val boundingBox = faces[0].boundingBox
                            val croppedBitmap = cropImage(bitmap, boundingBox)
                            if (croppedBitmap != null) {
                                saveImageToDevice(croppedBitmap)
                            }
                            lastSaveTime = currentTime // Update last save time
                        }
                    } else {
                        overlayView.setBoxColor(android.graphics.Color.RED)
                        overlayView.updateBoxes(emptyList())

                        // Display toast if the number of detected faces has changed
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

        // Convert Image to Bitmap


        private fun imageToBitmap(image: Image): Bitmap? {
            val planes: Array<Plane> = image.planes
            val yBuffer: ByteBuffer = planes[0].buffer
            val uBuffer: ByteBuffer = planes[1].buffer
            val vBuffer: ByteBuffer = planes[2].buffer

            val ySize: Int = yBuffer.remaining()
            val uSize: Int = uBuffer.remaining()
            val vSize: Int = vBuffer.remaining()

            val yBytes = ByteArray(ySize)
            val uBytes = ByteArray(uSize)
            val vBytes = ByteArray(vSize)

            yBuffer.get(yBytes)
            uBuffer.get(uBytes)
            vBuffer.get(vBytes)

            val yuvImage = YuvImage(yBytes, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val imageBytes = out.toByteArray()

            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }


        // Crop the image based on the bounding box of the detected face
        private fun cropImage(bitmap: Bitmap, boundingBox: Rect): Bitmap? {
            // Check if the bounding box is valid
            if (boundingBox.width() <= 0 || boundingBox.height() <= 0) {
                Log.e("MeditationActivity", "Invalid bounding box dimensions.")
                return null
            }

            // Check if the bounding box is within the bitmap dimensions
            if (boundingBox.left < 0 || boundingBox.top < 0 ||
                boundingBox.right > bitmap.width || boundingBox.bottom > bitmap.height) {
                Log.e("MeditationActivity", "Bounding box is out of bitmap bounds.")
                return null
            }

            return try {
                // Crop the bitmap based on the bounding box
                Bitmap.createBitmap(bitmap, boundingBox.left, boundingBox.top,
                    boundingBox.width(), boundingBox.height())
            } catch (e: Exception) {
                Log.e("MeditationActivity", "Error while cropping image: ${e.message}")
                null
            }
        }

        // Save the cropped image to the device storage
        private fun saveImageToDevice(bitmap: Bitmap) {
            val filename = "face_${System.currentTimeMillis()}.jpg"
            val file = File(getExternalFilesDir(null), filename)

            try {
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    showToast("Image saved to: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                showToast("Error saving image: ${e.message}")
            }
        }

        // Calculate the bounding box with scaling for visualization
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
        cameraExecutor.shutdown() // Shutdown the camera executor when the activity is destroyed
    }
}
