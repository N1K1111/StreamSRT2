package com.example.streamsrt2

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.*
import android.view.Surface
import android.view.TextureView
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CameraManager(private val context: Context, private val textureView: TextureView) {
    private val systemCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    @SuppressLint("MissingPermission")
    suspend fun startCamera(onFrame: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        val cameraId = systemCameraManager.cameraIdList[0] // Используем первую камеру
        val cameraCaptureSession = CompletableDeferred<CameraCaptureSession>()

        // Ждем, пока TextureView будет готов
        if (!textureView.isAvailable) {
            val surfaceReady = CompletableDeferred<Unit>()
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    surfaceReady.complete(Unit)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
            surfaceReady.await()
        }

        val surfaceTexture = textureView.surfaceTexture
        surfaceTexture?.setDefaultBufferSize(1920, 1080) // Установите нужное разрешение
        val surface = Surface(surfaceTexture)

        imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.YUV_420_888, 2)
        val imageSurface = imageReader!!.surface

        try {
            systemCameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewRequestBuilder?.addTarget(surface)
                    previewRequestBuilder?.addTarget(imageSurface)

                    camera.createCaptureSession(listOf(surface, imageSurface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSession = session
                            cameraCaptureSession.complete(session)
                            try {
                                val request = previewRequestBuilder?.build()
                                session.setRepeatingRequest(request!!, null, backgroundHandler)
                            } catch (e: Exception) {
                                Log.e("CameraManager", "Failed to start preview: ${e.message}", e)
                            }
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e("CameraManager", "Session configuration failed")
                            cameraCaptureSession.cancel() // Убрано true, оставлен вызов без аргументов
                        }
                    }, backgroundHandler)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e("CameraManager", "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e("CameraManager", "Missing camera permission: ${e.message}", e)
            throw e
        }

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val buffer = it.planes[0].buffer // Берем только Y-плоскость для простоты
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                onFrame(bytes)
                it.close()
            }
        }, backgroundHandler)

        cameraCaptureSession.await()
    }

    fun stopCamera() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
            captureSession = null
            cameraDevice = null
            imageReader = null
        } catch (e: Exception) {
            Log.e("CameraManager", "Error stopping camera: ${e.message}", e)
        } finally {
            backgroundThread.quitSafely()
        }
    }
}