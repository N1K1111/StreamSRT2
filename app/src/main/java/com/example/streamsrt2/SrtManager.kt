package com.example.streamsrt2

class SrtManager {
    fun initialize() {
        val result = SrtNative.initSrt()
        if (result != 0) throw RuntimeException("SRT init failed")
    }

    fun start(url: String) {
        SrtNative.startStreaming(url)
    }

    fun stop() {
        SrtNative.stopStreaming()
    }

    fun sendFrame(data: ByteArray) {
        SrtNative.sendFrame(data)
    }
}