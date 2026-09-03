cmake_minimum_required(VERSION 3.26)

project(bibleverse_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PIPER_SOURCE_DIR
        "${CMAKE_CURRENT_LIST_DIR}/piper")

set(ONNXRUNTIME_DIR
        "${CMAKE_CURRENT_LIST_DIR}/onnxruntime")

if(NOT EXISTS
        "${PIPER_SOURCE_DIR}/libpiper/CMakeLists.txt")
    message(FATAL_ERROR
            "Piper source not found.")
endif()

if(NOT EXISTS
        "${ONNXRUNTIME_DIR}/include/onnxruntime_cxx_api.h")
    message(FATAL_ERROR
            "ONNX Runtime headers not found.")
endif()

if(NOT EXISTS
        "${ONNXRUNTIME_DIR}/lib/libonnxruntime.so")
    message(FATAL_ERROR
            "Android ONNX Runtime library not found.")
endif()

set(
        ONNXRUNTIME_DIR
        "${ONNXRUNTIME_DIR}"
        CACHE PATH
        "Android ONNX Runtime directory"
        FORCE
)

add_subdirectory(
        "${PIPER_SOURCE_DIR}/libpiper"
        "${CMAKE_CURRENT_BINARY_DIR}/piper"
)

add_library(
        piper_jni
        SHARED
        native_piper.cpp
)

target_include_directories(
        piper_jni
        PRIVATE

        "${PIPER_SOURCE_DIR}/libpiper/include"

        "${ONNXRUNTIME_DIR}/include"
)

target_link_directories(
        piper_jni
        PRIVATE

        "${ONNXRUNTIME_DIR}/lib"
)

target_link_libraries(
        piper_jni

        piper
        onnxruntime
        log
)

set_target_properties(
        piper_jni
        PROPERTIES

        OUTPUT_NAME "piper_jni"

        CXX_VISIBILITY_PRESET hidden

        VISIBILITY_INLINES_HIDDEN ON
)
