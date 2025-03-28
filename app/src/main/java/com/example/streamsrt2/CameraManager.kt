package com.example.streamsrt2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class CameraManager(private val context: Context, private val textureView: TextureView) {
    private val systemCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private val backgroundThread = HandlerThread("CameraBackground").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)
    private var codec: MediaCodec? = null

    @SuppressLint("MissingPermission")
    suspend fun startCamera(onFrame: (ByteArray) -> Unit) = withContext(Dispatchers.IO) {
        // Инициализация кодека H.264
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000) // Битрейт 4 Мбит/с
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Ключевой кадр каждую секунду
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }

        val cameraId = systemCameraManager.cameraIdList[0] // Используем первую камеру
        val cameraCaptureSession = CompletableDeferred<CameraCaptureSession>()

        // Ждём, пока TextureView будет готов
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
                            cameraCaptureSession.cancel()
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
                // Извлекаем YUV данные
                val yBuffer = it.planes[0].buffer
                val uBuffer = it.planes[1].buffer
                val vBuffer = it.planes[2].buffer

                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()

                val data = ByteArray(ySize + uSize + vSize)
                yBuffer.get(data, 0, ySize)
                uBuffer.get(data, ySize, uSize)
                vBuffer.get(data, ySize + uSize, vSize)

                // Кодирование в H.264
                val inputBufferIndex = codec!!.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec!!.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    val capacity = inputBuffer?.capacity() ?: 0
                    if (data.size > capacity) {
                        Log.w("CameraManager", "Data size ${data.size} exceeds buffer capacity $capacity, truncating")
                        inputBuffer?.put(data, 0, capacity) // Обрезаем данные до размера буфера
                        codec!!.queueInputBuffer(inputBufferIndex, 0, capacity, System.nanoTime() / 1000, 0)
                    } else {
                        inputBuffer?.put(data)
                        codec!!.queueInputBuffer(inputBufferIndex, 0, data.size, System.nanoTime() / 1000, 0)
                    }
                } else {
                    Log.w("CameraManager", "No input buffer available")
                }

                val bufferInfo = MediaCodec.BufferInfo()
                var outputBufferIndex = codec!!.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = codec!!.getOutputBuffer(outputBufferIndex)
                    val encodedData = ByteArray(bufferInfo.size)
                    outputBuffer?.get(encodedData)
                    onFrame(encodedData) // Отправляем закодированные данные
                    codec!!.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = codec!!.dequeueOutputBuffer(bufferInfo, 0)
                }

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
            codec?.stop()
            codec?.release()
            captureSession = null
            cameraDevice = null
            imageReader = null
            codec = null
        } catch (e: Exception) {
            Log.e("CameraManager", "Error stopping camera: ${e.message}", e)
        } finally {
            backgroundThread.quitSafely()
        }
    }
}