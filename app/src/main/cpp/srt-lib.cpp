#include <jni.h>
#include <android/log.h>

#define LOG_TAG "SRT"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Заглушки для методов из SrtNative.kt
extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_initSrt(JNIEnv *env, jobject /* this */) {
    LOGD("Initializing SRT...");
    // Здесь будет инициализация SRT
    return 0; // 0 - успех, другое - ошибка
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_startStreaming(JNIEnv *env, jobject /* this */, jstring url) {
    LOGD("Starting streaming...");
    // Логика запуска стриминга
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_stopStreaming(JNIEnv *env, jobject /* this */) {
    LOGD("Stopping streaming...");
    // Остановка стриминга
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_streamsrt2_SrtNative_sendFrame(JNIEnv *env, jobject /* this */, jbyteArray data) {
    LOGD("Sending frame...");
    // Отправка кадра
    return 0;
}