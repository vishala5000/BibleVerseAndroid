package com.example.bibleverse

import android.app.*
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var modelStatus: TextView
    private lateinit var configStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var log: TextView
    private val pickModel = 10
    private val pickConfig = 11
    private var modelUri: android.net.Uri? = null
    private var configUri: android.net.Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        root.addView(TextView(this).apply {
            text = "BIBLE VERSE VIDEO GENERATOR"
            textSize = 22f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))

        modelStatus = TextView(this).apply { text = "Ryan High ONNX: not selected" }
        configStatus = TextView(this).apply { text = "Ryan High JSON: not selected" }
        root.addView(modelStatus)
        root.addView(Button(this).apply {
            text = "Select en_US-ryan-high.onnx"
            setOnClickListener { pick(pickModel, "application/octet-stream") }
        })
        root.addView(configStatus)
        root.addView(Button(this).apply {
            text = "Select en_US-ryan-high.onnx.json"
            setOnClickListener { pick(pickConfig, "application/json") }
        })

        val format = Spinner(this)
        format.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("Vertical 1080 × 1920", "Horizontal 1920 × 1080"))
        root.addView(TextView(this).apply { text = "Video format" })
        root.addView(format)

        val generate = Button(this).apply { text = "GENERATE VIDEOS" }
        root.addView(generate)

        progress = ProgressBar(this).apply { isIndeterminate = false; max = 100 }
        root.addView(progress)

        log = TextView(this).apply { textSize = 13f; setPadding(0, 16, 0, 0) }
        root.addView(ScrollView(this).apply { addView(log) },
            LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)

        generate.setOnClickListener {
            val m = modelUri
            val c = configUri
            if (m == null || c == null) {
                toast("Select both Ryan High model files first")
                return@setOnClickListener
            }
            generate.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    File(filesDir, "piper").mkdirs()
                    val model = copyUri(m, "piper/en_US-ryan-high.onnx")
                    val config = copyUri(c, "piper/en_US-ryan-high.onnx.json")
                    val vertical = format.selectedItemPosition == 0
                    VideoGenerator(this@MainActivity, model, config, vertical) { pct, msg ->
                        runOnUiThread { progress.progress = pct; log.append("$msg\n") }
                    }.run()
                    runOnUiThread { toast("Finished. Open the app output folder.") }
                } catch (e: Exception) {
                    runOnUiThread { toast("ERROR: ${e.message}") }
                } finally {
                    runOnUiThread { generate.isEnabled = true }
                }
            }
        }
    }

    private fun pick(code: Int, mime: String) {
        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            type = mime
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val u = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        if (requestCode == pickModel) {
            modelUri = u
            modelStatus.text = "Ryan High ONNX: selected"
        } else {
            configUri = u
            configStatus.text = "Ryan High JSON: selected"
        }
    }

    private fun copyUri(uri: android.net.Uri, name: String): File {
        val f = File(filesDir, name)
        contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(f).use { out -> input.copyTo(out) }
        }
        return f
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
