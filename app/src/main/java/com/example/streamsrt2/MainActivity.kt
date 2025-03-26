package com.example.streamsrt2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.streamsrt2.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import com.example.streamsrt2.SrtManager
import com.example.streamsrt2.CameraManager
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var srtManager: SrtManager
    private val streamingUrl = "srt://your.server:port"
    private val CAMERA_PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        srtManager = SrtManager()
        cameraManager = CameraManager(this, binding.cameraPreview) // Передаем TextureView

        // Добавляем обработчик нажатия кнопки
        binding.startButton.setOnClickListener {
            if (checkCameraPermission()) {
                setupStreaming()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupStreaming()
            } else {
                Log.e("MainActivity", "Camera permission denied")
                // Можно показать сообщение пользователю
            }
        }
    }

    private fun setupStreaming() {
        lifecycleScope.launch {
            try {
                Log.d("Streaming", "Initializing SRT...")
                srtManager.initialize()
                Log.d("Streaming", "Starting camera...")
                cameraManager.startCamera { frame ->
                    Log.d("Streaming", "Frame received, sending...")
                    srtManager.sendFrame(frame)
                }
                Log.d("Streaming", "Starting stream to $streamingUrl...")
                srtManager.start(streamingUrl)
                Log.d("Streaming", "Streaming started successfully")
            } catch (e: Exception) {
                Log.e("Streaming", "Error in setupStreaming: ${e.message}", e)
            }
        }
    }
}