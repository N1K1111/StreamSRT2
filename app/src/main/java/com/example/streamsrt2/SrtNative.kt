package com.example.streamsrt2

object SrtNative {
    init {
        System.loadLibrary("srt-lib")
    }

    external fun initSrt(): Int
    external fun startStreaming(url: String, streamid: String): Int  // Обновлено
    external fun stopStreaming(): Int
    external fun sendFrame(data: ByteArray): Int
    external fun receiveFrame(): ByteArray?
}