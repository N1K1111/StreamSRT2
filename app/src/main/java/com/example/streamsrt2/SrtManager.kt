package com.example.streamsrt2

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SrtManager {
    fun initialize() {
        val result = SrtNative.initSrt()
        if (result != 0) throw RuntimeException("SRT init failed with code: $result")
    }

    fun startStreaming(url: String = "srt://89.169.135.34:9999", streamid: String = "default") {
        initialize()
        start(url, streamid)
    }

    private fun start(url: String, streamid: String) {
        val result = SrtNative.startStreaming(url, streamid)
        if (result != 0) throw RuntimeException("SRT start failed with code: $result")
    }

    fun stop() {
        val result = SrtNative.stopStreaming()
        if (result != 0) throw RuntimeException("SRT stop failed with code: $result")
    }

    fun sendFrame(data: ByteArray) {
        Log.d("SRT", "Sending frame of size ${data.size} bytes")
        val result = SrtNative.sendFrame(data)
        if (result != 0) throw RuntimeException("SRT send frame failed with code: $result")
    }

    suspend fun receiveFrame(): ByteArray? = withContext(Dispatchers.IO) {
        SrtNative.receiveFrame()
    }
}