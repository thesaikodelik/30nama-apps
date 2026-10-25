cmake_minimum_required(VERSION 3.22.1)
project(namampv C)

# libmpv and FFmpeg come prebuilt from tools/build-libmpv.sh (LGPL build)
set(PREBUILT ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

if(NOT EXISTS ${PREBUILT}/libmpv.so OR NOT EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/include/mpv/client.h)
    message(FATAL_ERROR "libmpv is missing. Run quest/tools/build-libmpv.sh in WSL or Linux first.")
endif()

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES IMPORTED_LOCATION ${PREBUILT}/libmpv.so)

# only for av_jni_set_java_vm, which mpv's MediaCodec and Surface code read
add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${PREBUILT}/libavcodec.so)

add_library(namampv SHARED namampv.c)
target_include_directories(namampv PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_compile_options(namampv PRIVATE -Wall -Wextra -Werror=implicit-function-declaration)
# 16 KB pages, same as the prebuilt libraries
target_link_options(namampv PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(namampv mpv avcodec log)
