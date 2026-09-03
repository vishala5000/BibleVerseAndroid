package com.example.bibleverse

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class PiperRuntime(
    private val ctx: Context
) {

    private val root =
        File(ctx.filesDir, "piper").apply {
            mkdirs()
        }

    private val bin =
        File(root, "piper")

    private val lib =
        File(root, "lib").apply {
            mkdirs()
        }

    private val data =
        File(root, "espeak-ng-data").apply {
            mkdirs()
        }

    fun prepare() {

        copyTreeIfNeeded(
            "piper/bin",
            root
        )

        copyTreeIfNeeded(
            "piper/lib",
            lib
        )

        copyTreeIfNeeded(
            "piper/espeak-ng-data",
            data
        )

        if (!bin.exists()) {

            throw RuntimeException(
                "Piper executable is missing from the APK.\n\n" +
                "Expected:\n" +
                "assets/piper/bin/piper"
            )
        }

        if (bin.length() == 0L) {

            throw RuntimeException(
                "Piper executable is empty."
            )
        }

        if (!bin.canExecute()) {

            bin.setExecutable(true)
        }

        if (!bin.canExecute()) {

            throw RuntimeException(
                "Android refused to make Piper executable.\n\n" +
                "This usually means the bundled Piper binary " +
                "is not packaged as an Android-native executable."
            )
        }

        if (!data.exists() ||
            data.listFiles().isNullOrEmpty()
        ) {

            throw RuntimeException(
                "espeak-ng-data is missing from the APK."
            )
        }
    }

    fun synthesize(
        text: String,
        outputRaw: File
    ) {

        require(text.isNotBlank()) {
            "Piper text is empty."
        }

        outputRaw.parentFile?.mkdirs()

        if (!bin.exists()) {

            prepare()
        }

        val command =
            arrayListOf(
                bin.absolutePath,
                "-m",
                "en_US-ryan-high",
                "-f",
                outputRaw.absolutePath,
                "--",
                text
            )

        val processBuilder =
            ProcessBuilder(command)

        val environment =
            processBuilder.environment()

        environment["LD_LIBRARY_PATH"] =
            lib.absolutePath

        environment["ESPEAK_DATA_PATH"] =
            data.absolutePath

        environment["PIPER_VOICE_PATH"] =
            root.absolutePath

        processBuilder.redirectErrorStream(true)

        val process =
            try {
                processBuilder.start()
            } catch (e: Exception) {

                throw RuntimeException(
                    "Could not start Piper.\n\n" +
                    "Binary: ${bin.absolutePath}\n" +
                    "Exists: ${bin.exists()}\n" +
                    "Executable: ${bin.canExecute()}\n" +
                    "Size: ${bin.length()} bytes\n" +
                    "Library path: ${lib.absolutePath}\n" +
                    "eSpeak path: ${data.absolutePath}\n\n" +
                    "Original error: ${e.message}",
                    e
                )
            }

        val output =
            process.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        val exitCode =
            process.waitFor()

        if (exitCode != 0) {

            throw RuntimeException(
                "Piper failed.\n\n" +
                "Exit code: $exitCode\n\n" +
                "Piper output:\n$output"
            )
        }

        if (!outputRaw.exists()) {

            throw RuntimeException(
                "Piper finished without creating audio output."
            )
        }

        if (outputRaw.length() == 0L) {

            throw RuntimeException(
                "Piper created an empty audio file."
            )
        }
    }

    private fun copyTreeIfNeeded(
        assetPath: String,
        destination: File
    ) {

        val names =
            ctx.assets.list(assetPath)
                ?: return

        for (name in names) {

            val source =
                "$assetPath/$name"

            val target =
                File(destination, name)

            val children =
                ctx.assets.list(source)

            if (
                children != null &&
                children.isNotEmpty()
            ) {

                target.mkdirs()

                copyTreeIfNeeded(
                    source,
                    target
                )

            } else {

                if (
                    !target.exists() ||
                    target.length() == 0L
                ) {

                    target.parentFile?.mkdirs()

                    ctx.assets
                        .open(source)
                        .use { input ->

                            FileOutputStream(target)
                                .use { output ->

                                    input.copyTo(
                                        output,
                                        1024 * 1024
                                    )
                                }
                        }
                }
            }
        }
    }
}
