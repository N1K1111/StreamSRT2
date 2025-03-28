package com.example.streamsrt2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.streamsrt2.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ArrayAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var srtManager: SrtManager
    private val apiUrl = "https://bba0mmpjvepkel6mbo3l.containers.yandexcloud.net/api/streams"
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        srtManager = SrtManager()
        cameraManager = CameraManager(this, binding.cameraPreview)

        binding.startButton.setOnClickListener {
            if (checkCameraPermission()) {
                if (binding.streamMode.checkedRadioButtonId == R.id.radioStream) {
                    setupStreaming()
                } else {
                    setupViewing()
                }
            } else {
                requestCameraPermission()
            }
        }

        lifecycleScope.launch {
            loadStreamList()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (binding.streamMode.checkedRadioButtonId == R.id.radioStream) {
                setupStreaming()
            } else {
                setupViewing()
            }
        }
    }

    /*
    private fun setupStreaming() {
        lifecycleScope.launch {
            try {
                val streamerId = "user_${System.currentTimeMillis()}"
                val title = "My Stream"
                val response = registerStream(streamerId, title)
                val streamerUrl = response.getString("StreamerUrl")
                Log.d("Streaming", "Starting streaming to $streamerUrl...")
                srtManager.startStreaming(streamerUrl)
                cameraManager.startCamera { frame ->
                    srtManager.sendFrame(frame)
                }
            } catch (e: Exception) {
                Log.e("Streaming", "Error: ${e.message}", e)
            }
        }
    }
    */
    private fun setupStreaming() {
        lifecycleScope.launch {
            try {
                val streamerId = "user_${System.currentTimeMillis()}"
                val title = "My Stream"
                val response = registerStream(streamerId, title)
                Log.d("Streaming", "Full response: $response")
                val streamerUrl = response.optString("StreamerUrl", "srt://89.169.135.34:9999")
                Log.d("Streaming", "Starting streaming to $streamerUrl with streamid $streamerId...")
                srtManager.startStreaming(streamerUrl, streamerId)
                cameraManager.startCamera { frame ->
                    srtManager.sendFrame(frame)
                }
            } catch (e: Exception) {
                Log.e("Streaming", "Error: ${e.message}", e)
            }
        }
    }
    private fun setupViewing() {
        lifecycleScope.launch {
            val selectedStream = binding.streamList.selectedItem as? String ?: return@launch
            val streams = getStreamList()
            val streamList = (0 until streams.length()).map { streams.getJSONObject(it) }
            val stream = streamList.find { it.getString("title") == selectedStream }
            val srtPath = stream?.getString("SrtPath") ?: return@launch
            val streamId = stream?.getString("streamerId") ?: "default" // Используем streamerId из JSON
            Log.d("Viewing", "Connecting to $srtPath with streamid $streamId...")
            srtManager.startStreaming(srtPath, streamId)
            // TODO: Реализовать отображение полученных данных в TextureView
        }
    }

    private suspend fun registerStream(streamerId: String, title: String): JSONObject = withContext(Dispatchers.IO) {
        val json = """{"StreamerId": "$streamerId", "Title": "$title"}"""
        val request = Request.Builder()
            .url(apiUrl)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw IllegalStateException("Response body is null")
        Log.d("API", "Register stream response: $body")
        JSONObject(body)
    }

    private suspend fun getStreamList(): JSONArray = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(apiUrl).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        Log.d("API", "Get streams response: $body, code: ${response.code}")
        try {
            if (body.isEmpty()) {
                JSONArray("[]") // Возвращаем пустой массив, если ответ пустой
            } else {
                JSONArray(body)
            }
        } catch (e: JSONException) {
            Log.e("API", "Failed to parse JSON: ${e.message}")
            JSONArray("[]") // Возвращаем пустой массив в случае ошибки
        }
    }

    private suspend fun loadStreamList() {
        try {
            val streams = getStreamList()
            val titles = (0 until streams.length()).map { streams.getJSONObject(it).getString("title") } // Изменено на "title"
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, titles)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.streamList.adapter = adapter
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading stream list: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        srtManager.stop()
        cameraManager.stopCamera()
    }
}