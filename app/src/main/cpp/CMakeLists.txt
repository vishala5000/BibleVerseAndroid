cmake_minimum_required(VERSION 3.26)

project(bibleverse_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PIPER_SOURCE_DIR
        "${CMAKE_CURRENT_LIST_DIR}/piper")

set(ONNXRUNTIME_DIR
        "${CMAKE_CURRENT_LIST_DIR}/onnxruntime")

# Android ONNX Runtime AAR uses "headers", not "include".
set(ONNXRUNTIME_INCLUDE_DIR
        "${ONNXRUNTIME_DIR}/headers")

set(ONNXRUNTIME_LIBRARY
        "${ONNXRUNTIME_DIR}/lib/libonnxruntime.so")

if(NOT EXISTS "${PIPER_SOURCE_DIR}/libpiper/CMakeLists.txt")
    message(FATAL_ERROR
            "Piper source not found: ${PIPER_SOURCE_DIR}")
endif()

if(NOT EXISTS "${PIPER_SOURCE_DIR}/libpiper/include/piper.h")
    message(FATAL_ERROR
            "Piper header not found.")
endif()

if(NOT EXISTS "${ONNXRUNTIME_INCLUDE_DIR}/onnxruntime_cxx_api.h")
    message(FATAL_ERROR
            "ONNX Runtime headers not found: ${ONNXRUNTIME_INCLUDE_DIR}")
endif()

if(NOT EXISTS "${ONNXRUNTIME_LIBRARY}")
    message(FATAL_ERROR
            "ONNX Runtime library not found: ${ONNXRUNTIME_LIBRARY}")
endif()

# Tell Piper's CMake where our Android ONNX Runtime is.
set(
        ONNXRUNTIME_DIR
        "${ONNXRUNTIME_DIR}"
        CACHE PATH
        "Android ONNX Runtime directory"
        FORCE
)

# Piper's CMake expects:
#   ${ONNXRUNTIME_DIR}/include
#   ${ONNXRUNTIME_DIR}/lib
#
# Create a compatibility symlink because the Android AAR
# extracts headers into "headers".
if(NOT EXISTS "${ONNXRUNTIME_DIR}/include")
    execute_process(
            COMMAND
            ${CMAKE_COMMAND} -E create_symlink
            "${ONNXRUNTIME_INCLUDE_DIR}"
            "${ONNXRUNTIME_DIR}/include"
    )
endif()

if(NOT EXISTS "${ONNXRUNTIME_DIR}/include/onnxruntime_cxx_api.h")
    message(FATAL_ERROR
            "Could not create ONNX Runtime include compatibility path.")
endif()

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

        "${ONNXRUNTIME_INCLUDE_DIR}"
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
