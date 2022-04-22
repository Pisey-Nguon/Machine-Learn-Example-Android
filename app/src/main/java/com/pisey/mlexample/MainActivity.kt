package com.pisey.mlexample

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Html
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import com.pisey.mlexample.databinding.ActivityMainBinding
import java.util.concurrent.ExecutionException

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private lateinit var mBinding:ActivityMainBinding
    private var previewUseCase: Preview? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    private var mTextLabelList : ArrayList<TextView> = ArrayList()
    private val launcher = registerForActivityResult(ActivityResultContracts.OpenDocument()) {

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initLabelList()
        initCameraProvider()

//        launcher.launch(arrayOf("image/*"))
    }

    private fun initLabelList(){
        mTextLabelList.add(mBinding.label1)
        mTextLabelList.add(mBinding.label2)
        mTextLabelList.add(mBinding.label3)
    }

    private fun initCameraProvider(){
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture : ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    cameraProvider = cameraProviderFuture.get()
                    runWithPermissions(Manifest.permission.CAMERA){
                        bindAllCameraUseCase()
                    }

                } catch (e: ExecutionException) {

                } catch (e: InterruptedException) {

                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindPreviewUseCase(){
        if (cameraProvider != null){
            cameraProvider!!.unbind(previewUseCase)
            val metrics = DisplayMetrics().also { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.display!!.getRealMetrics(it)
            }else{
                windowManager.defaultDisplay.getMetrics(it)
            }
            }
            val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
            val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)

            previewUseCase = Preview.Builder().apply {
                setTargetResolution(screenSize)
                setTargetRotation(windowManager.defaultDisplay.rotation)
            }.build()

            previewUseCase!!.setSurfaceProvider(mBinding.previewView.surfaceProvider)
        }
    }

    private fun bindAllCameraUseCase(){
        if (cameraProvider != null){
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalyticsUseCase()
        }
    }

    private fun bindAnalyticsUseCase(){
        bindCustomModel()
    }

    @SuppressLint("UnsafeOptInUsageError", "WrongConstant")
    private fun bindCustomModel(){
        if (cameraProvider == null) {
            return
        }

//        // Live detection and tracking
//        val options = ObjectDetectorOptions.Builder()
//            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
//            .enableClassification()  // Optional
//            .build()

        val localModel = LocalModel.Builder().setAssetFilePath("custom_models/model-export_icn_tflite-multi_label_datas_20220310013938-2022-03-10T07_57_57.910802Z_model.tflite").build()
        // Live detection and tracking
        val customObjectDetectorOptions =
            CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .setClassificationConfidenceThreshold(0.5f)
                .setMaxPerObjectLabelCount(3)
                .build()

        val objectDetector = ObjectDetection.getClient(customObjectDetectorOptions)
        val metrics = DisplayMetrics().also { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display!!.getRealMetrics(it)
        }else{
            windowManager.defaultDisplay.getMetrics(it)
        }
        }
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        val builder = ImageAnalysis.Builder()
        builder.setTargetResolution(screenSize)
        builder.setTargetRotation(windowManager.defaultDisplay.rotation)

        val analysisUseCase = builder.build()
        analysisUseCase.setAnalyzer(ContextCompat.getMainExecutor(this),
            { imageProxy ->
                val mediaImage = imageProxy.image

                if (mediaImage != null) {
                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                    // Pass image to an ML Kit Vision API
                    // ...
                    objectDetector.process(image).addOnSuccessListener {

                        for (detectedObject in it) {
                            val boundingBox = detectedObject.boundingBox

//                            for (label in detectedObject.labels) {
//                                val text = label.text
//                                val index = label.index
//                                val confidence = label.confidence
//                            }

                            val labels = detectedObject.labels
                            Log.d(TAG, "bindCustomModel: size ${labels.size}")

                            if (labels.size > 0){
                                labels.forEachIndexed { index, label ->
                                    val trackingId = detectedObject.trackingId
                                    Log.d(TAG, "bindCustomModel: size of label ${label.text}")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        mTextLabelList[index].text = Html.fromHtml("<h3>${label.text}</h3>\n" +
                                                "<p>Accurate: ${(label.confidence * 100).toInt()}%</p>",Html.FROM_HTML_MODE_COMPACT)
                                    }else{
                                        mTextLabelList[index].text = Html.fromHtml("<h3>${label.text}</h3>\n" +
                                                "<p>Accurate: ${(label.confidence * 100).toInt()}%</p>")
                                    }
                                }
                                if (labels.size < mTextLabelList.size){
                                    for (i in labels.size until mTextLabelList.size){
                                        mTextLabelList[i].text = "Empty"
                                    }
                                }
                            }else{
                                mTextLabelList.forEach { text ->
                                    text.text = "Empty"
                                }
                            }

                        }
                    }.addOnCompleteListener{
                        imageProxy.close()
                        if(it.result.size == 0){
                            mTextLabelList.forEach { text ->
                                text.text = "Empty"
                            }
                        }
                    }.addOnFailureListener{
                        imageProxy.close()
                        mTextLabelList.forEach { text ->
                            text.text = "Empty"
                        }
                    }

                }

            })

        cameraProvider!!.bindToLifecycle(this,cameraSelector!!,previewUseCase,analysisUseCase)
    }
}