package com.example.bibleverse

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class PiperRuntime(private val ctx: Context) {
    private val root = File(ctx.filesDir, "piper").apply { mkdirs() }
    private val bin = File(root, "piper")
    private val lib = File(root, "lib").apply { mkdirs() }
    private val data = File(root, "espeak-ng-data").apply { mkdirs() }

    fun prepare() {
        if (bin.exists()) return
        copyTree("piper/bin", root)
        copyTree("piper/lib", lib)
        copyTree("piper/espeak-ng-data", data)
        require(bin.exists()) { "Piper binary was not packaged" }
        bin.setExecutable(true)
    }

    fun synthesize(text: String, outputRaw: File) {
        val pb = ProcessBuilder(
            bin.absolutePath,
            "-m", "en_US-ryan-high",
            "-f", outputRaw.absolutePath,
            "--", text
        )
        val env = pb.environment()
        env["LD_LIBRARY_PATH"] = lib.absolutePath
        env["ESPEAK_DATA_PATH"] = data.absolutePath
        env["PIPER_VOICE_PATH"] = root.absolutePath
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.bufferedReader().use { it.readText() }
        val code = p.waitFor()
        if (code != 0) throw RuntimeException("Piper failed ($code): $output")
    }

    private fun copyTree(assetPath: String, destination: File) {
        val names = ctx.assets.list(assetPath) ?: return
        for (name in names) {
            val src = "$assetPath/$name"
            val dst = File(destination, name)
            val children = ctx.assets.list(src)
            if (children != null && children.isNotEmpty()) {
                dst.mkdirs()
                copyTree(src, dst)
            } else {
                dst.parentFile?.mkdirs()
                ctx.assets.open(src).use { input -> FileOutputStream(dst).use { input.copyTo(it) } }
            }
        }
    }
}
