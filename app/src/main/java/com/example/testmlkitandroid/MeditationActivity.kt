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

    // Declare UI components and permission launcher
    private lateinit var previewView: PreviewView
    private lateinit var startButton: Button
    private lateinit var permissionLauncher: ActivityResultLauncher<String>

    // Executor for processing images on a separate thread
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_meditation)

        // Initialize the UI components
        previewView = findViewById(R.id.viewFinder)
        startButton = findViewById(R.id.recordButton)

        // Register a launcher for requesting camera permission from the user
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Start the camera if permission is granted
                startCamera()
            } else {
                // Show a message if the permission is denied
                showToast("Camera permission denied")
            }
        }

        // Check if the camera permission is already granted
        if (isCameraPermissionGranted()) {
            // Start the camera if permission is already granted
            startCamera()
        } else {
            // Request camera permission if not granted
            requestCameraPermission()
        }

        // Set up a click listener for the start button
        startButton.setOnClickListener {
            // Display a simple toast message when the button is clicked
            showToast("Button clicked")
        }
    }

    // Method to check if camera permission is granted
    private fun isCameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Method to request camera permission from the user
    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Method to start the camera and set up the preview and analysis use cases
    private fun startCamera() {
        // Get an instance of the ProcessCameraProvider to manage the camera lifecycle
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Retrieve the camera provider instance
            val cameraProvider = cameraProviderFuture.get()

            // Configure the preview to display the camera feed on the PreviewView
            val preview = androidx.camera.core.Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            // Configure image analysis to process images for face detection
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation) // Set the target rotation to match the display orientation
                .build()
                .apply {
                    // Set up the face analyzer to process images and overlay bounding boxes
                    val overlayView = findViewById<OverlayView>(R.id.overlay)
                    setAnalyzer(cameraExecutor, FaceAnalyzer(overlayView, CameraSelector.DEFAULT_FRONT_CAMERA))
                }

            // Select the front camera for analysis
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Bind the camera lifecycle with the preview and analysis use cases
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    // Utility method to display a toast message
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Inner class for analyzing images and detecting faces
    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private inner class FaceAnalyzer(
        private val overlayView: OverlayView,
        private val cameraSelector: CameraSelector
    ) : ImageAnalysis.Analyzer {

        // Initialize the face detector with specified options for accuracy, landmarks, and classification
        private val faceDetector: FaceDetector by lazy {
            FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Accurate mode for better detection results
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Detect all facial landmarks
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Enable all classification modes (e.g., smiling, eyes open)
                    .build()
            )
        }

        // Method called for each image frame analyzed by the ImageAnalysis use case
        override fun analyze(image: ImageProxy) {
            // Get the media image from the ImageProxy
            val mediaImage = image.image ?: run {
                // Close the image and return if mediaImage is null to avoid memory leaks
                image.close()
                return
            }

            // Convert the media image to an InputImage for ML Kit processing
            val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

            // Get the actual size of the image from the camera (width and height are swapped)
            val imageWidth = mediaImage.height
            val imageHeight = mediaImage.width

            // Process the image to detect faces
            faceDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    // Map detected faces to bounding boxes adjusted for screen size and camera orientation
                    val boundingBoxes = faces.map { face ->
                        val boundingBox = face.boundingBox
                        calculateBoundingBox(boundingBox, imageWidth, imageHeight)
                    }

                    // Update the overlay view based on detection results
                    overlayView.setBoxColor(if (faces.isNotEmpty()) android.graphics.Color.GREEN else android.graphics.Color.RED)
                    overlayView.updateBoxes(boundingBoxes)

                    // Show a toast with the number of detected faces
                    showToast("Detected ${faces.size} face(s)")
                }
                .addOnFailureListener {
                    // Handle face detection failure and update the overlay view
                    overlayView.setBoxColor(android.graphics.Color.RED)
                    showToast("Face detection failed")
                }
                .addOnCompleteListener {
                    // Close the image after processing to free up resources
                    image.close()
                }
        }

        // Calculate the bounding box for a face, adjusting for camera orientation and preview size
        private fun calculateBoundingBox(boundingBox: android.graphics.Rect, imageWidth: Int, imageHeight: Int): RectF {
            // Calculate the scale factors between the original image size and the PreviewView size
            val scaleX = overlayView.width.toFloat() / imageWidth
            val scaleY = overlayView.height.toFloat() / imageHeight

            // Adjust bounding box dimensions (expand horizontally and shrink vertically)
            val expandedLeft = boundingBox.left - (boundingBox.width() * 0.35f)
            val expandedTop = boundingBox.top + (boundingBox.height() * 0.015f)
            val expandedRight = boundingBox.right + (boundingBox.width() * 0.35f)
            val expandedBottom = boundingBox.bottom - (boundingBox.height() * 0.015f)

            // Mirror the bounding box horizontally if using the front camera
            val mirroredLeft = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                (imageWidth - expandedRight) * scaleX
            else expandedLeft * scaleX

            val mirroredRight = if (cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA)
                (imageWidth - expandedLeft) * scaleX
            else expandedRight * scaleX

            // Return the calculated bounding box as a RectF object
            return RectF(mirroredLeft, expandedTop * scaleY, mirroredRight, expandedBottom * scaleY)
        }
    }

    // Override onDestroy to clean up resources when the activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        // Shut down the camera executor to avoid memory leaks
        cameraExecutor.shutdown()
    }
}
