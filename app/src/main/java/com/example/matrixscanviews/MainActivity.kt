package com.example.matrixscanviews

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import com.example.matrixscanviews.databinding.ActivityMainBinding
import com.example.matrixscanviews.ui.BarcodeGraphic
import com.google.gson.Gson
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit

class MainActivity : AppCompatActivity() {


    private val barcodeList = mutableListOf<com.example.matrixscanviews.Barcode>()
    private lateinit var viewBinding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null


    private lateinit var cameraExecutor: ExecutorService

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_AZTEC
        )
        .enableAllPotentialBarcodes()
        .build()

    val scanner = BarcodeScanning.getClient(options)

    private var graphicOverlay: GraphicOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        graphicOverlay = findViewById(R.id.graphic_overlay)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for take photo and video capture buttons
        viewBinding.imageCaptureButton.setOnClickListener { takePhoto() }

        viewBinding.btGoList.setOnClickListener {
            val intent = Intent(this, BarcodesListActivity::class.java)
            intent.putExtra("barcodes", Gson().toJson(barcodeList))
            startActivity(intent)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()


        if (OpenCVLoader.initLocal()) {
            Log.i("OpenCV", "OpenCV successfully loaded.");
        }


    }

    fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        // Convert Bitmap to OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Create a new Mat for grayscale image
        val grayMat = Mat(mat.rows(), mat.cols(), CvType.CV_8UC1)

        // Convert the image to grayscale
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Convert the grayscale Mat back to Bitmap
        val grayBitmap =
            Bitmap.createBitmap(grayMat.cols(), grayMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(grayMat, grayBitmap)

        return grayBitmap
    }

    fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        // Convert Bitmap to Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Create a new Mat for the grayscale image
        val grayMat = Mat(mat.rows(), mat.cols(), CvType.CV_8UC1)

        // Convert the original image to grayscale
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Create another Mat to store the blurred image
        val blurredMat = Mat()

        // Apply Gaussian Blur (Kernel size 15x15, and sigmaX = 0)
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(3.0, 3.0), 1.5)

        // Convert the blurred Mat back to Bitmap
        val blurredBitmap = Bitmap.createBitmap(blurredMat.cols(), blurredMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(blurredMat, blurredBitmap)

        return blurredBitmap
    }

    fun applyCannyEdgeDetection(bitmap: Bitmap): Bitmap {
        // Convert Bitmap to Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to Grayscale
        val grayMat = Mat(mat.rows(), mat.cols(), CvType.CV_8UC1)
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Apply Gaussian Blur to reduce noise
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(15.0, 15.0), 0.0)

        // Apply Canny Edge Detection
        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 0.0, 0.0)

        // Convert Mat back to Bitmap
        val edgeBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(edgesMat, edgeBitmap)

        return edgeBitmap
    }

    fun detectContoursAfterEdgeDetection(bitmap: Bitmap): Bitmap {
        // Convert the bitmap to OpenCV Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Apply GaussianBlur to reduce noise
        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0)

        // Apply Canny edge detection
        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)

        // Find contours
        val contours = ArrayList<MatOfPoint>()
        val filterContours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(edgesMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        contours.sortByDescending { Imgproc.contourArea(it) }
        val largestContours = contours.take(10)

        for (contour in largestContours) {
            val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * perimeter, true)

            // Step 4: If the contour has four points, assume it is the barcode
            if (approx.rows() == 4) {
                // Create a bounding box or polygon for the barcode
               // return Mat(MatOfPoint(*approx.toArray()))
                filterContours.add(contour)
                Log.d("BARCODE", "Barcode found contour")
            }
        }

        // Draw the contour (bounding box) on the image in green
        val contourImage = Mat.zeros(edgesMat.size(), CvType.CV_8UC3)
        val contourColor = Scalar(0.0, 255.0, 0.0) // Green color
        Imgproc.drawContours(contourImage, filterContours, -1, contourColor, 2)

        // Convert the Mat back to Bitmap to display
        val resultBitmap = Bitmap.createBitmap(contourImage.cols(), contourImage.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(contourImage, resultBitmap)

        // Draw contours
        //val contourImage = Mat.zeros(edgesMat.size(), CvType.CV_8UC3)
        //Imgproc.drawContours(contourImage, contours, -1, Scalar(0.0, 255.0, 0.0), 2)

        // Convert Mat back to Bitmap to display
        //val resultBitmap = Bitmap.createBitmap(contourImage.cols(), contourImage.rows(), Bitmap.Config.ARGB_8888)
        //Utils.matToBitmap(contourImage, resultBitmap)

        return resultBitmap
    }


    fun optimizedCannyForBarcode(bitmap: Bitmap): Bitmap {
        // Convert Bitmap to Mat
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        // Convert to Grayscale
        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)

        // Normalize brightness and contrast
        val normalizedMat = Mat()
        Core.normalize(grayMat, normalizedMat, 0.0, 255.0, Core.NORM_MINMAX)

        // Apply Gaussian Blur to reduce noise
        val blurredMat = Mat()
        Imgproc.GaussianBlur(normalizedMat, blurredMat, Size(5.0, 5.0), 0.0)

        // Apply Canny Edge Detection
        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0)

        // Apply Morphological Transformations to enhance barcode edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.dilate(edgesMat, edgesMat, kernel)

        // Convert Mat back to Bitmap
        val resultBitmap = Bitmap.createBitmap(edgesMat.cols(), edgesMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(edgesMat, resultBitmap)

        return resultBitmap
    }

    private fun takePhoto() {


        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time stamped name and MediaStore entry.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"

                    Toast.makeText(baseContext, "Escaneando...", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    output.savedUri?.let { proccessImage(it) }
                }
            }
        )
    }


    private fun captureVideo() {}

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()



            val targetResolution = android.util.Size(1080, 1920) //
            // Preview
            val preview = Preview.Builder()
                .setTargetResolution(targetResolution)
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(targetResolution)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer())
                }

            // Select back camera as a default

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()


            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                val camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )


                val cameraControl = camera.cameraControl
                val exposureState = camera.cameraInfo.exposureState

                //cameraControl.setExposureCompensationIndex(exposureState.exposureCompensationRange.lower)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {

        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {
            Log.d(TAG, "LuminosityAnalizer")
            /*            val buffer = image.planes[0].buffer
                        val data = buffer.toByteArray()
                        val pixels = data.map { it.toInt() and 0xFF }
                        val luma = pixels.average()

                        listener(luma)*/

            image.close()
        }
    }


    private fun proccessImage(uri: Uri) {

        Log.d(TAG, "Proccess Image")

/*        var image: InputImage? = null
        try {
            image = InputImage.fromFilePath(this, uri)
        } catch (e: IOException) {
            e.printStackTrace()
        }*/


        val imageBitmap = BitmapUtils.getBitmapFromContentUri(contentResolver, uri) ?: return

        //val imageBitmapGrayScale = applyGaussianBlur(imageBitmap)
        val imageBitmapGrayScale = applyGaussianBlur(imageBitmap)

        val image = InputImage.fromBitmap(imageBitmapGrayScale, 0)

        if (image != null) {
            graphicOverlay?.clear()
            Log.d(TAG, "Image != null")


            val result = scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    // Task completed successfully
                    // ...

                    if (barcodes.isNotEmpty()) {

                        Toast.makeText(
                            this,
                            "${barcodes.size} códigos de barras leídos",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this, "No se detectaron códigos", Toast.LENGTH_SHORT).show()
                    }

                    for (barcode in barcodes) {
                        val bounds = barcode.boundingBox
                        val corners = barcode.cornerPoints

                        val rawValue = barcode.rawValue
                        val formatType = barcode.format
                        // See API reference for complete list of supported types
                        val type = when (formatType) {
                            Barcode.FORMAT_CODE_128 -> {
                                "CODE 128"
                            }

                            Barcode.FORMAT_EAN_13 -> {
                                "EAN 13"

                            }

                            else -> {
                                "QR"
                            }
                        }
                        if (rawValue != null) {
                            val barcodeToBeAdded = Barcode(type, rawValue)
                            if (!barcodeList.contains(barcodeToBeAdded))
                                barcodeList.add(barcodeToBeAdded)
                            Log.d(TAG, "Barcode: $rawValue")
                        }

                        graphicOverlay!!.setImageSourceInfo(
                            image.width,
                            image.height,
                            /* isFlipped= */ false
                        )

                        graphicOverlay?.add(BarcodeGraphic(graphicOverlay, barcode))
                        graphicOverlay?.invalidate()

                    }

                }
                .addOnFailureListener {
                    // Task failed with an exception
                    // ...
                    Log.d(TAG, "No barcodes found")

                }
        }


    }

    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {


        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            Log.d(TAG, "BarcodeAnalizer")
            /*            val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            Log.d(TAG, "Media image != null")
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            // Pass image to an ML Kit Vision API
                            // ...

                            val scanner = BarcodeScanning.getClient(options)

                            val result = scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    // Task completed successfully
                                    // ...
                                    for (barcode in barcodes) {
                                        val bounds = barcode.boundingBox
                                        val corners = barcode.cornerPoints

                                        val rawValue = barcode.rawValue
                                        val formatType = barcode.format
                                        // See API reference for complete list of supported types
                                        val type = when (formatType) {
                                            Barcode.FORMAT_CODE_128 -> {
                                                "CODE 128"
                                            }

                                            Barcode.FORMAT_EAN_13 -> {
                                                "EAN 13"
                                            }

                                            else -> {
                                                "QR"
                                            }
                                        }

                                        if (rawValue != null) {
                                            val barcodeToBeAdded = Barcode(type, rawValue)
                                            if (!barcodeList.contains(barcodeToBeAdded))
                                                barcodeList.add(barcodeToBeAdded)
                                            Log.d(TAG, "Barcode: $rawValue")
                                        }
                                        Log.d(TAG, "Barcode: " + rawValue.toString())
                                    }
                                    imageProxy.close()
                                }
                                .addOnFailureListener {
                                    // Task failed with an exception
                                    // ...
                                    Log.d(TAG, "No barcodes found")
                                    imageProxy.close()
                                }
                        }*/


        }
    }
}