package com.example.streamsrt2

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager // Переименовываем для избежания конфликта имен
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import android.graphics.SurfaceTexture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.media.ImageReader
import android.view.TextureView
import android.graphics.ImageFormat
import android.util.Log

class CameraManager(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private lateinit var textureView: TextureView // Добавьте это поле

    // Конструктор принимает TextureView
    constructor(context: Context, textureView: TextureView) : this(context) {
        this.textureView = textureView
    }
    @SuppressLint("MissingPermission")
    suspend fun startCamera(onFrame: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val cameraId = cameraManager.cameraIdList[0]
        val cameraCaptureSession = CompletableDeferred<CameraCaptureSession>()

        val surfaceTexture = textureView.surfaceTexture ?: SurfaceTexture(10)
        val surface = Surface(surfaceTexture)

        val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        val imageSurface = imageReader.surface

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewRequestBuilder?.addTarget(surface)
                    previewRequestBuilder?.addTarget(imageSurface)

                    camera.createCaptureSession(listOf(surface, imageSurface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession.complete(session)
                            try {
                                val request = previewRequestBuilder?.build()
                                session.setRepeatingRequest(request!!, null, null)
                            } catch (e: Exception) {
                                Log.e("CameraManager", "Failed to start preview: ${e.message}")
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("CameraManager", "Session configuration failed")
                        }
                    }, null)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e("CameraManager", "Missing camera permission: ${e.message}")
            throw e // Или обработайте ошибку иным способом
        }

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val buffer = it.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                onFrame(bytes)
                it.close()
            }
        }, null)

        cameraCaptureSession.await()
    }
}