package pranay.com.cameraxapp


import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.*
import android.view.Surface
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val REQUEST_CODE_PERMISSIONS =999
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
            if(allPermissionsGranted()){
                textureView.post {
                    startCameraForCapture()
                }
            }else{
                ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS)
            }

        // Every time the provided texture view changes, recompute layout
        textureView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            updateTransform()
        }
    }



    private fun startCameraForCapture()
    {
        // pull the metrics from our TextureView
        val metrics = DisplayMetrics().also { textureView.display.getRealMetrics(it) }
// define the screen size
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)


        //====================== Image Preview Config code Start==========================
        // Create configuration object for the viewfinder use case
        val previewConfig=PreviewConfig.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetResolution(screenSize)
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {
            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView,0)
            textureView.surfaceTexture=it.surfaceTexture
            updateTransform()
        }
        //====================== Image Preview Config code End==========================


        //====================== Image CAPTURE Config code Start==========================
        val imageCaptureConfig=ImageCaptureConfig.Builder().apply {
            setTargetAspectRatio(Rational(1,1))
            // We don't set a resolution for image capture; instead, we
            // select a capture mode which will infer the appropriate
            // resolution based on aspect ration and requested mode
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            setLensFacing(CameraX.LensFacing.BACK)

        }.build()

        // Build the viewfinder use case
        val imageCapture = ImageCapture(imageCaptureConfig)

       capture_button.setOnClickListener {
           val file = File(externalMediaDirs.first(),"${System.currentTimeMillis()}.jpg")
           imageCapture.takePicture(file,object :ImageCapture.OnImageSavedListener{
               override fun onImageSaved(file: File) {
                   val msg = "Photo capture succeeded: ${file.absolutePath}"
                    msg.toast()
               }

               override fun onError(useCaseError: ImageCapture.UseCaseError, message: String, cause: Throwable?) {
                   val msg = "Photo capture failed: $message"
                   msg.toast()
                   cause?.printStackTrace()
               }
           })
       }
        //====================== Image CAPTURE Config code End==========================


        //====================== Image Analysis Config code Start==========================

        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // Use a worker thread for image analysis to prevent glitches
            val analyzerThread = HandlerThread("OCR").apply {
                start()
            }
            setCallbackHandler(Handler(analyzerThread.looper))
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        val valueToFind="CameraX"
         val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
         }
        analyzerUseCase.analyzer=TextAnalyzer(valueToFind){
            val file = File(externalMediaDirs.first(),"${System.currentTimeMillis()}.jpg")
            imageCapture.takePicture(file,object :ImageCapture.OnImageSavedListener{
                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    msg.toast()
                }

                override fun onError(useCaseError: ImageCapture.UseCaseError, message: String, cause: Throwable?) {
                    val msg = "Photo capture failed: $message"
                    msg.toast()
                    cause?.printStackTrace()
                }
            })
        }

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

        CameraX.bindToLifecycle(this,preview,imageCapture,analyzerUseCase)
        // For Preview, image Capture and analysis use case

    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX =textureView.width/2f
        val centerY =textureView.height/2f

        // Correct preview output to account for display rotation
        val rotationDegree=when(textureView.display.rotation){
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegree.toFloat(),centerX,centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
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
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun String.toast(){
        Toast.makeText(this@MainActivity,
            this,
            Toast.LENGTH_SHORT).show()
    }


    private class LuminosityAnalyzer:ImageAnalysis.Analyzer{
        private var lastAnalyzedTimestamp = 0L
        /**
         * Helper extension function used to extract a byte array from an
         * image plane buffer
         */
        private fun ByteBuffer.toByteArray():ByteArray{
            rewind() //Rewind buffer to zero
            val data=ByteArray(remaining())
            get(data)  // Copy buffer into byte array
            return data // Return byte array
        }
        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            val currentTimestamp =System.currentTimeMillis()
            // Calculate the average luma no more often than every second
            if(currentTimestamp-lastAnalyzedTimestamp>=TimeUnit.SECONDS.toMillis(1)){
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val buffer = image.planes[0].buffer
                // Extract image data from callback object
                val data = buffer.toByteArray()
                // Convert the data into an array of pixel values
                val pixels = data.map { it.toInt() and 0xFF }
                // Compute average luminance for the image
                val luma = pixels.average()
                // Log the new luma value
                Log.d( "CameraX Demo" , "Average luminosity: $luma")
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp
            }
        }
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
                        val textToSearch = result.text.split("\n").joinToString(" ")
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
}
