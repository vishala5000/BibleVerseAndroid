package com.example.bibleverse

import java.io.File

class NativePiper(
    private val model: File,
    private val config: File,
    private val espeakData: File
) : AutoCloseable {

    private var handle = 0L

    init {

        validateFiles()

        loadNativeLibraries()

        handle = nativeCreate(
            model.absolutePath,
            config.absolutePath,
            espeakData.absolutePath
        )

        if (handle == 0L) {

            throw RuntimeException(
                nativeLastError().ifBlank {
                    "Piper initialization failed."
                }
            )
        }
    }

    private fun validateFiles() {

        if (!model.exists()) {
            throw RuntimeException(
                "Ryan High ONNX file does not exist:\n" +
                    model.absolutePath
            )
        }

        if (model.length() == 0L) {
            throw RuntimeException(
                "Ryan High ONNX file is empty."
            )
        }

        if (!config.exists()) {
            throw RuntimeException(
                "Ryan High JSON file does not exist:\n" +
                    config.absolutePath
            )
        }

        if (config.length() == 0L) {
            throw RuntimeException(
                "Ryan High JSON file is empty."
            )
        }

        if (!espeakData.exists()) {
            throw RuntimeException(
                "eSpeak data directory is missing."
            )
        }

        if (
            espeakData.listFiles()
                .isNullOrEmpty()
        ) {
            throw RuntimeException(
                "eSpeak data directory is empty."
            )
        }
    }

    private fun loadNativeLibraries() {

        try {

            /*
             * ONNX Runtime must be loaded first because
             * Piper depends on it.
             */
            System.loadLibrary(
                "onnxruntime"
            )

            System.loadLibrary(
                "piper"
            )

            System.loadLibrary(
                "piper_jni"
            )

        } catch (e: UnsatisfiedLinkError) {

            throw RuntimeException(
                """
                Native Piper libraries failed to load.

                ABI: arm64-v8a

                Required:
                libonnxruntime.so
                libpiper.so
                libpiper_jni.so

                Native error:
                ${e.message}
                """.trimIndent(),
                e
            )
        }
    }

    @Synchronized
    fun synthesize(
        text: String,
        outputRaw: File
    ) {

        check(handle != 0L) {
            "Piper has already been closed."
        }

        require(text.isNotBlank()) {
            "Text to synthesize is empty."
        }

        outputRaw.parentFile?.mkdirs()

        if (
            outputRaw.exists()
        ) {
            outputRaw.delete()
        }

        val result =
            nativeSynthesize(
                handle,
                text,
                outputRaw.absolutePath
            )

        if (result != 0) {

            throw RuntimeException(
                nativeLastError().ifBlank {
                    "Piper synthesis failed. " +
                        "Return code: $result"
                }
            )
        }

        if (!outputRaw.exists()) {

            throw RuntimeException(
                "Piper finished but created no RAW file."
            )
        }

        if (outputRaw.length() == 0L) {

            throw RuntimeException(
                "Piper created an empty RAW file."
            )
        }
    }

    override fun close() {

        if (handle != 0L) {

            nativeFree(handle)

            handle = 0L
        }
    }

    companion object {

        init {
            /*
             * Loading is intentionally done lazily in the
             * constructor after file validation.
             */
        }

        @JvmStatic
        private external fun nativeCreate(
            model: String,
            config: String,
            espeakData: String
        ): Long

        @JvmStatic
        private external fun nativeSynthesize(
            handle: Long,
            text: String,
            outputRaw: String
        ): Int

        @JvmStatic
        private external fun nativeFree(
            handle: Long
        )

        @JvmStatic
        private external fun nativeLastError(): String
    }
}
