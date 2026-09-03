package com.example.bibleverse

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

class VideoGenerator(
    private val ctx: Context,
    private val model: File,
    private val config: File,
    private val vertical: Boolean,
    private val report: (Int, String) -> Unit
) {
    private val work = File(ctx.filesDir, "generator").apply { mkdirs() }
    private val out = File(ctx.getExternalFilesDir(null), "videos").apply { mkdirs() }
    private val width = if (vertical) 1080 else 1920
    private val height = if (vertical) 1920 else 1080

    fun run() {
        val quotes = assetText("quotes.txt")
        require(quotes.isNotBlank()) { "quotes.txt is empty/missing from app assets" }
        require(assetExists("font.ttf")) { "font.ttf is missing from app assets" }
        require(assetExists("bg.mp3")) { "bg.mp3 is missing from app assets" }

        val font = copyAsset("font.ttf")
        val music = copyAsset("bg.mp3")
        val piper = PiperRuntime(ctx)
        piper.prepare()

        val items = parseQuotes(quotes)
        out.listFiles()?.forEach { it.delete() }

        items.forEachIndexed { index, item ->
            val pct = (index * 100 / max(1, items.size))
            report(pct, "Generating ${index + 1}/${items.size}")
            generateOne(index + 1, item.first, item.second, piper, font, music)
        }

        val zip = File(ctx.getExternalFilesDir(null), "videos.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            out.listFiles()?.filter { it.extension.lowercase() == "mp4" }?.sortedBy { it.name.toIntOrNull() ?: 0 }?.forEach {
                zos.putNextEntry(ZipEntry(it.name))
                it.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
        report(100, "ZIP created: ${zip.absolutePath}")
    }

    private fun generateOne(
        number: Int, verse: String, reference: String,
        piper: PiperRuntime, font: File, music: File
    ) {
        val safe = File(work, "v$number").apply { mkdirs() }
        val verseRaw = File(safe, "verse.raw")
        val ctaRaw = File(safe, "cta.raw")
        val verseWav = File(safe, "verse.wav")
        val ctaWav = File(safe, "cta.wav")
        val silence = File(safe, "silence.wav")
        val voice = File(safe, "voice.wav")
        val verseTxt = File(safe, "verse.txt")
        val refTxt = File(safe, "ref.txt")
        val ctaTxt = File(safe, "cta.txt")

        piper.synthesize(verse, verseRaw)
        piper.synthesize("Comment Amen To Receive.", ctaRaw)

        ff("-f f32le -ar 22050 -ac 1 -i ${q(verseRaw.path)} -c:a pcm_s16le ${q(verseWav.path)}")
        ff("-f f32le -ar 22050 -ac 1 -i ${q(ctaRaw.path)} -c:a pcm_s16le ${q(ctaWav.path)}")
        ff("-f lavfi -i anullsrc=r=22050:cl=mono -t 0.5 -c:a pcm_s16le ${q(silence.path)}")

        val concat = File(safe, "concat.txt")
        concat.writeText("file '${verseWav.absolutePath.replace("'", "'\\\\''")}'\nfile '${silence.absolutePath.replace("'", "'\\\\''")}'\nfile '${ctaWav.absolutePath.replace("'", "'\\\\''")}'\n")
        ff("-f concat -safe 0 -i ${q(concat.path)} -c:a pcm_s16le ${q(voice.path)}")

        verseTxt.writeText(wrap(verse, font, if (vertical) 680 else 1320, 90))
        refTxt.writeText(reference)
        ctaTxt.writeText("Comment Amen To Receive")

        val verseDuration = probe(verseWav)
        val ctaStart = verseDuration + 0.5
        val ctaEnd = ctaStart + probe(ctaWav)

        val vf = buildFilter(font, verseTxt, refTxt, ctaTxt, ctaStart, ctaEnd)
        val target = File(out, "$number.mp4")
        val cmd = "-f lavfi -i color=c=black:s=${width}x${height}:r=30 " +
                "-i ${q(voice.path)} -stream_loop -1 -i ${q(music.path)} " +
                "-filter_complex ${q(vf)} " +
                "-map 0:v -map '[a]' -c:v libx264 -preset ultrafast -crf 18 -pix_fmt yuv420p " +
                "-r 30 -c:a aac -b:a 192k -shortest -movflags +faststart ${q(target.path)}"
        ff(cmd)
    }

    private fun buildFilter(font: File, verse: File, ref: File, cta: File, start: Double, end: Double): String {
        val left = 200
        val right = 200
        val top = 300
        val bottom = 300
        val safeW = width - left - right
        val safeH = height - top - bottom
        val verseLines = verse.readLines().size.coerceAtLeast(1)
        val verseSize = if (vertical) 80 else 70
        val headingY = top
        val refY = headingY + 100 + 14
        val verseY = refY + 50 + 32
        val ctaY = (height / 2) - 60
        return "[0:v]" +
                "drawtext=fontfile=${esc(font.path)}:text='BIBLE VERSE':fontcolor=yellow:fontsize=100:x=(w-text_w)/2:y=$headingY," +
                "drawtext=fontfile=${esc(font.path)}:textfile=${esc(ref.path)}:fontcolor=yellow:fontsize=50:x=(w-text_w)/2:y=$refY," +
                "drawtext=fontfile=${esc(font.path)}:textfile=${esc(verse.path)}:fontcolor=white:fontsize=$verseSize:line_spacing=${(verseSize*0.18).toInt()}:x=(w-text_w)/2:y=$verseY," +
                "drawtext=fontfile=${esc(font.path)}:textfile=${esc(cta.path)}:fontcolor=yellow:fontsize=70:x=(w-text_w)/2:y=$ctaY:enable='between(t\\,$start\\,$end)'[v];" +
                "[1:a]volume=1[voice];[2:a]volume=0.2[music];[voice][music]amix=inputs=2:duration=first:dropout_transition=0[a]"
    }

    private fun wrap(text: String, font: File, maxWidth: Int, maxSize: Int): String {
        // Android-side wrapping mirrors the source intent: measured lines are kept inside the safe width.
        // FFmpeg performs final glyph rendering using the same supplied font.
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (roughWidth(test, maxSize) <= maxWidth) current = test
            else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines.joinToString("\n")
    }

    private fun roughWidth(s: String, size: Int) = s.length * size * 0.52

    private fun parseQuotes(s: String): List<Pair<String,String>> =
        s.lines().mapNotNull { line ->
            val x = line.trim()
            if (x.isEmpty()) null else {
                val p = x.split(Regex("\\s+[—-]\\s+"), limit = 2)
                if (p.size == 2) p[0].trim() to p[1].trim() else x to ""
            }
        }

    private fun assetText(name: String): String =
        ctx.assets.open(name).bufferedReader().use { it.readText() }

    private fun assetExists(name: String): Boolean = try { ctx.assets.open(name).close(); true } catch (_: Exception) { false }

    private fun copyAsset(name: String): File {
        val f = File(work, name)
        ctx.assets.open(name).use { input -> FileOutputStream(f).use { input.copyTo(it) } }
        return f
    }

    private fun ff(args: String) {
        val s = FFmpegKit.execute(args)
        if (!ReturnCode.isSuccess(s.returnCode)) throw IOException("FFmpeg failed: ${s.failStackTrace}")
    }

    private fun probe(f: File): Double {
        val s = FFmpegKit.execute("-i ${q(f.path)} -f null -")
        val m = Regex("Duration: (\\d+):(\\d+):(\\d+\\.\\d+)").find(s.output ?: "") ?: return 1.0
        return m.groupValues[1].toDouble()*3600 + m.groupValues[2].toDouble()*60 + m.groupValues[3].toDouble()
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\\\''") + "'"
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")
}
