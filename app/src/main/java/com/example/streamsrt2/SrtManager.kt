package com.example.streamsrt2

class SrtManager {
    fun initialize() {
        val result = SrtNative.initSrt()
        if (result != 0) throw RuntimeException("SRT init failed with code: $result")
    }

    fun start(url: String) {
        val result = SrtNative.startStreaming(url)
        if (result != 0) throw RuntimeException("SRT start failed with code: $result")
    }

    fun stop() {
        val result = SrtNative.stopStreaming()
        if (result != 0) throw RuntimeException("SRT stop failed with code: $result")
    }

    fun sendFrame(data: ByteArray) {
        val result = SrtNative.sendFrame(data)
        if (result != 0) throw RuntimeException("SRT send frame failed with code: $result")
    }

    fun startStreaming(url: String = "srt://89.169.135.34:9999") {
        initialize() // Инициализация SRT
        start(url)   // Запуск стриминга
    }
}