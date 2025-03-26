package com.example.streamsrt2

object SrtNative {
    init {
        System.loadLibrary("srt-lib")
    }

    external fun initSrt(): Int
    external fun startStreaming(url: String): Int
    external fun stopStreaming(): Int
    external fun sendFrame(data: ByteArray): Int
}