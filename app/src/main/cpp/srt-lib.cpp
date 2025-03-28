#include <jni.h>
#include "srt.h"
#include <cstring>
#include <vector>
#include <android/log.h>

#define LOG_TAG "SRT"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static SRTSOCKET sock = SRT_INVALID_SOCK;

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_initSrt(JNIEnv *env, jobject /* this */) {
    LOGD("Initializing SRT...");
    srt_startup();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_startStreaming(JNIEnv *env, jobject /* this */, jstring url, jstring jstreamid) {
    const char *url_str = env->GetStringUTFChars(url, nullptr);
    const char *streamid = env->GetStringUTFChars(jstreamid, nullptr);
    LOGD("Starting streaming to %s with streamid %s...", url_str, streamid);

    sock = srt_create_socket();
    if (sock == SRT_INVALID_SOCK) {
        LOGE("Failed to create SRT socket: %s", srt_getlasterror_str());
        env->ReleaseStringUTFChars(url, url_str);
        env->ReleaseStringUTFChars(jstreamid, streamid);
        return -1;
    }

    // Установка опций до подключения
    int yes = 1;
    int no = 0;
    int transtype = SRTT_LIVE;
    if (srt_setsockflag(sock, SRTO_SENDER, &yes, sizeof(yes)) == SRT_ERROR ||
        srt_setsockflag(sock, SRTO_MESSAGEAPI, &yes, sizeof(yes)) == SRT_ERROR ||
        srt_setsockflag(sock, SRTO_TRANSTYPE, &transtype, sizeof(transtype)) == SRT_ERROR || // Явно задаём Live
        srt_setsockflag(sock, SRTO_SNDSYN, &no, sizeof(no)) == SRT_ERROR ||
        srt_setsockflag(sock, SRTO_STREAMID, streamid, strlen(streamid)) == SRT_ERROR) {
        LOGE("Failed to set socket options: %s", srt_getlasterror_str());
        srt_close(sock);
        sock = SRT_INVALID_SOCK;
        env->ReleaseStringUTFChars(url, url_str);
        env->ReleaseStringUTFChars(jstreamid, streamid);
        return -1;
    }

    int messageapi = 0;
    int optlen = sizeof(messageapi);
    srt_getsockflag(sock, SRTO_MESSAGEAPI, &messageapi, &optlen);
    LOGD("Client SRTO_MESSAGEAPI: %d", messageapi);

    LOGD("Setting streamid: %s", streamid);

    sockaddr_in sa;
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons(9999);
    sa.sin_addr.s_addr = inet_addr("89.169.135.34");

    if (srt_connect(sock, (sockaddr*)&sa, sizeof(sa)) == SRT_ERROR) {
        LOGE("Failed to connect: %s", srt_getlasterror_str());
        srt_close(sock);
        sock = SRT_INVALID_SOCK;
        env->ReleaseStringUTFChars(url, url_str);
        env->ReleaseStringUTFChars(jstreamid, streamid);
        return -1;
    }

    int state = srt_getsockstate(sock);
    LOGD("Socket state after connect: %d (SRTS_CONNECTED = %d)", state, SRTS_CONNECTED);
    if (state != SRTS_CONNECTED) {
        LOGE("Socket not in connected state: %d", state);
        srt_close(sock);
        sock = SRT_INVALID_SOCK;
        env->ReleaseStringUTFChars(url, url_str);
        env->ReleaseStringUTFChars(jstreamid, streamid);
        return -1;
    }

    LOGD("Connected successfully");
    env->ReleaseStringUTFChars(url, url_str);
    env->ReleaseStringUTFChars(jstreamid, streamid);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_stopStreaming(JNIEnv *env, jobject /* this */) {
    LOGD("Stopping streaming...");
    if (sock != SRT_INVALID_SOCK) {
        srt_close(sock);
        sock = SRT_INVALID_SOCK;
    }
    srt_cleanup();
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_sendFrame(JNIEnv *env, jobject /* this */, jbyteArray data) {
    jbyte *bytes = env->GetByteArrayElements(data, nullptr);
    jsize len = env->GetArrayLength(data);
    LOGD("Sending frame of size %d bytes...", len);

    int state = srt_getsockstate(sock);
    LOGD("Socket state before send: %d (SRTS_CONNECTED = %d)", state, SRTS_CONNECTED);
    if (state != SRTS_CONNECTED) {
        LOGE("Socket not connected before send: %d", state);
        env->ReleaseByteArrayElements(data, bytes, 0);
        return -1;
    }

    const int MAX_PACKET_SIZE = 1316;
    int offset = 0;
    while (offset < len) {
        int chunk_size = std::min(MAX_PACKET_SIZE, (int)(len - offset));
        LOGD("Calling srt_sendmsg with sock=%d, size=%d, offset=%d", sock, chunk_size, offset);
        int result = srt_sendmsg(sock, (char*)bytes + offset, chunk_size, -1, true); // Упрощённый вызов
        if (result == SRT_ERROR) {
            int errcode = srt_getlasterror(nullptr);
            LOGE("Failed to send frame chunk at offset %d: %s (error code: %d)", offset, srt_getlasterror_str(), errcode);
            env->ReleaseByteArrayElements(data, bytes, 0);
            return -1;
        }

        offset += chunk_size;
    }

    env->ReleaseByteArrayElements(data, bytes, 0);
    LOGD("Frame sent successfully");
    return 0;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_streamsrt2_SrtNative_receiveFrame(JNIEnv *env, jobject /* this */) {
    if (sock == SRT_INVALID_SOCK) {
        LOGE("Socket is invalid, cannot receive frame");
        return nullptr;
    }

    char buffer[1316];
    int bytes_received = srt_recvmsg(sock, buffer, sizeof(buffer));
    if (bytes_received <= 0) {
        if (bytes_received == SRT_ERROR) {
            LOGE("Failed to receive frame: %s", srt_getlasterror_str());
        }
        return nullptr;
    }

    LOGD("Received frame of size %d bytes", bytes_received);
    jbyteArray result = env->NewByteArray(bytes_received);
    env->SetByteArrayRegion(result, 0, bytes_received, (jbyte*)buffer);
    return result;
}