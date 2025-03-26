cmake_minimum_required(VERSION 3.22.1)

project("streamingapp")

include(FetchContent)
if (ANDROID)
    FetchContent_Declare(
            android_openssl
            DOWNLOAD_EXTRACT_TIMESTAMP true
            URL https://github.com/KDAB/android_openssl/archive/refs/heads/master.zip
    )
    FetchContent_MakeAvailable(android_openssl)
    include(${android_openssl_SOURCE_DIR}/android_openssl.cmake)

    # Указываем пути к OpenSSL для Android
    set(OPENSSL_ROOT_DIR ${android_openssl_SOURCE_DIR})
    set(OPENSSL_INCLUDE_DIR ${android_openssl_SOURCE_DIR}/ssl_3/include)
    set(OPENSSL_CRYPTO_LIBRARY ${android_openssl_SOURCE_DIR}/ssl_3/${ANDROID_ABI}/libcrypto.a)
    set(OPENSSL_SSL_LIBRARY ${android_openssl_SOURCE_DIR}/ssl_3/${ANDROID_ABI}/libssl.a)
    set(OPENSSL_LIBRARIES ${OPENSSL_SSL_LIBRARY} ${OPENSSL_CRYPTO_LIBRARY})
endif()

set(CMAKE_HAVE_LIBC_PTHREAD ON CACHE BOOL "Pthread support is built into libc" FORCE)
set(HAVE_PTHREAD_H ON CACHE BOOL "Pthread header exists" FORCE)
set(CMAKE_THREAD_LIBS_INIT "" CACHE STRING "No separate pthread library needed" FORCE)

set(ENABLE_APPS OFF CACHE BOOL "Disable SRT apps" FORCE)
set(ENABLE_SHARED ON CACHE BOOL "Enable shared SRT library" FORCE)
set(ENABLE_STATIC OFF CACHE BOOL "Disable static SRT library" FORCE)

# Указываем зависимости OpenSSL перед добавлением подмодуля SRT
find_package(OpenSSL REQUIRED)
add_subdirectory(srt)

add_library(
        srt-lib
        SHARED
        srt-lib.cpp
)

find_library(log-lib log)

target_include_directories(
        srt-lib PRIVATE
        ${CMAKE_SOURCE_DIR}/srt/srtcore
)

target_link_libraries(
        srt-lib
        srt_shared
        ${log-lib}
)

if (ANDROID)
    add_android_openssl_libraries(srt-lib srt_shared)
endif()