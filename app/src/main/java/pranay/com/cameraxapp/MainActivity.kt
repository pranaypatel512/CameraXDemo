package pranay.com.cameraxapp


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSIONS = 999
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (allPermissionsGranted()) {
            textureView.post {
                startCameraForCapture()
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

    }


    private fun startCameraForCapture() {
        // pull the metrics from our TextureView
        val metrics = DisplayMetrics().also { textureView.display.getRealMetrics(it) }
        // define the screen size
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)


        //====================== Image Preview Config code Start==========================
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetResolution(screenSize)
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener { previewOutput: Preview.PreviewOutput ->
            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)
            textureView.surfaceTexture = previewOutput.surfaceTexture
            updateTransform()
        }
        //====================== Image Preview Config code End==========================


        //====================== Image CAPTURE Config code Start==========================
        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setTargetAspectRatio(Rational(1, 1))
            // We don't set a resolution for image capture; instead, we
            // select a capture mode which will infer the appropriate
            // resolution based on aspect ration and requested mode
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            setLensFacing(CameraX.LensFacing.BACK)
            setTargetRotation(windowManager.defaultDisplay.rotation)
        }.build()

        // Build the viewfinder use case
        val imageCapture = ImageCapture(imageCaptureConfig)

        capture_button.setOnClickListener {
            val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
            //Method 1
            imageCapture.takePicture(file, object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    msg.toast()
                    val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                        putExtra(PreviewActivity.FILE_PATH, file.absolutePath)
                    }
                    startActivity(intent)
                }

                override fun onError(
                    useCaseError: ImageCapture.UseCaseError,
                    message: String,
                    cause: Throwable?
                ) {
                    val msg = "Photo capture failed: $message"
                    msg.toast()
                    cause?.printStackTrace()
                }
            })

            //Method 2
            /*imageCapture.takePicture(object : ImageCapture.OnImageCapturedListener() {
                override fun onCaptureSuccess(image: ImageProxy?, rotationDegrees: Int) {
                    super.onCaptureSuccess(image, rotationDegrees)
                }

                override fun onError(
                    useCaseError: ImageCapture.UseCaseError?,
                    message: String?,
                    cause: Throwable?
                ) {
                    super.onError(useCaseError, message, cause)
                }
            })*/

        }
        //====================== Image CAPTURE Config code End==========================


        //====================== Image Analysis Config code Start==========================

        // Setup image analysis config for image text recognize
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // Use a worker thread for image analysis to prevent glitches
            val analyzerThread = HandlerThread("OCR").apply {
                start()
            }
            setCallbackHandler(Handler(analyzerThread.looper))
            // we only care about the latest image in the buffer,
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetResolution(screenSize)
        }.build()

        val valueToFind = "CameraX"
        val analyzerUseCase = ImageAnalysis(analyzerConfig)
        /*analyzerUseCase.analyzer = TextAnalyzer(valueToFind) {
            //get the callback when text found on preview analysis and capture it.
            val file = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
            imageCapture.takePicture(file, object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    msg.toast()
                    val intent = Intent(this@MainActivity, PreviewActivity::class.java).apply {
                        putExtra(PreviewActivity.FILE_PATH, file.absolutePath)
                    }
                    startActivity(intent)
                }

                override fun onError(
                    useCaseError: ImageCapture.UseCaseError,
                    message: String,
                    cause: Throwable?
                ) {
                    val msg = "Photo capture failed: $message"
                    cause?.printStackTrace()
                }
            })
        }*/
        analyzerUseCase.analyzer = ImageAnalyzerClass()

        /*val analyzerUseCase = TextAnalyzer(analyzerConfig).apply {
            analyzer=LuminosityAnalyzer()
        }*/

        //====================== Image Analysis Config code End==========================

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.

        //CameraX.bindToLifecycle(this,preview) // For Preview

        //CameraX.bindToLifecycle(this,preview,imageCapture) // For Preview and image Capture

        CameraX.bindToLifecycle(this, preview, imageCapture, analyzerUseCase)
        // For Preview, image Capture and analysis use case

    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegree = when (textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegree.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                textureView.post {
                    //startCameraForPreview()
                    startCameraForCapture()
                }
            } else {
                "Permissions not granted by the user.".toast()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    fun String.toast() {
        Toast.makeText(
            this@MainActivity,
            this,
            Toast.LENGTH_SHORT
        ).show()
    }


    internal class TextAnalyzer(
        private val identifier: String,
        private val identifierDetectedCallback: () -> Unit
    ) : ImageAnalysis.Analyzer {

        companion object {
            private val ORIENTATIONS = SparseIntArray()

            init {
                ORIENTATIONS.append(0, FirebaseVisionImageMetadata.ROTATION_0)
                ORIENTATIONS.append(90, FirebaseVisionImageMetadata.ROTATION_90)
                ORIENTATIONS.append(180, FirebaseVisionImageMetadata.ROTATION_180)
                ORIENTATIONS.append(270, FirebaseVisionImageMetadata.ROTATION_270)
            }
        }

        private var lastAnalyzedTimestamp = 0L


        private fun getOrientationFromRotation(rotationDegrees: Int): Int {
            return when (rotationDegrees) {
                0 -> FirebaseVisionImageMetadata.ROTATION_0
                90 -> FirebaseVisionImageMetadata.ROTATION_90
                180 -> FirebaseVisionImageMetadata.ROTATION_180
                270 -> FirebaseVisionImageMetadata.ROTATION_270
                else -> FirebaseVisionImageMetadata.ROTATION_90
            }
        }

        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
            if (image?.image == null || image.image == null) return

            val timestamp = System.currentTimeMillis()
            // only run once per second
            if (timestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                val visionImage = FirebaseVisionImage.fromMediaImage(
                    image.image!!,
                    getOrientationFromRotation(rotationDegrees)
                )

                val detector = FirebaseVision.getInstance()
                    .onDeviceTextRecognizer


                detector.processImage(visionImage)
                    .addOnSuccessListener { result: FirebaseVisionText ->
                        // remove the new lines and join to a single string,
                        // then search for our identifier
                        val textToSearch = result.text.split("\n")
                            .joinToString(" ")
                        if (textToSearch.contains(identifier, true)) {
                            identifierDetectedCallback()
                        }
                    }
                    .addOnFailureListener {
                        Log.e("", "Error processing image", it)
                    }
                lastAnalyzedTimestamp = timestamp
            }
        }
    }

    internal class ImageAnalyzerClass : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L


        private fun getOrientationFromRotation(rotationDegrees: Int): Int {
            return when (rotationDegrees) {
                0 -> FirebaseVisionImageMetadata.ROTATION_0
                90 -> FirebaseVisionImageMetadata.ROTATION_90
                180 -> FirebaseVisionImageMetadata.ROTATION_180
                270 -> FirebaseVisionImageMetadata.ROTATION_270
                else -> FirebaseVisionImageMetadata.ROTATION_90
            }
        }

        companion object {
            private val ORIENTATIONS = SparseIntArray()

            init {
                ORIENTATIONS.append(0, FirebaseVisionImageMetadata.ROTATION_0)
                ORIENTATIONS.append(90, FirebaseVisionImageMetadata.ROTATION_90)
                ORIENTATIONS.append(180, FirebaseVisionImageMetadata.ROTATION_180)
                ORIENTATIONS.append(270, FirebaseVisionImageMetadata.ROTATION_270)
            }
        }

        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
            if (image?.image == null || image.image == null) return

            val timestamp = System.currentTimeMillis()
            // only run once per second
            if (timestamp - lastAnalyzedTimestamp >= TimeUnit.SECONDS.toMillis(1)) {
                val visionImage = FirebaseVisionImage.fromMediaImage(
                    image.image!!,
                    getOrientationFromRotation(rotationDegrees)
                )


                val options = FirebaseVisionBarcodeDetectorOptions.Builder()
                    .setBarcodeFormats(
                        FirebaseVisionBarcode.FORMAT_QR_CODE,
                        FirebaseVisionBarcode.FORMAT_AZTEC
                    )
                    .build()
                val detector = FirebaseVision.getInstance()
                    .getVisionBarcodeDetector(options)

                detector.detectInImage(visionImage).addOnSuccessListener { barcodes ->
                    barcodes.forEach { barcode ->
                        val bound = barcode.boundingBox
                        val corner = barcode.cornerPoints
                        val rawValue = barcode.rawValue
                        when (barcode.valueType) {
                            FirebaseVisionBarcode.TYPE_URL -> {
                                Log.e("URL is:", barcode?.url?.url)
                            }
                        }
                    }
                }



                lastAnalyzedTimestamp = timestamp
            }
        }
    }
}
