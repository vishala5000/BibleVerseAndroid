package com.example.bibleverse

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var modelStatus: TextView
    private lateinit var configStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var log: TextView
    private lateinit var generateButton: Button
    private lateinit var formatSpinner: Spinner

    private val pickModel = 10
    private val pickConfig = 11
    private val permissionRequest = 100

    private var modelUri: Uri? = null
    private var configUri: Uri? = null

    private val requiredOldStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        requestRequiredPermissions()

        restoreSelectedFiles()
    }

    private fun buildUi() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        root.addView(
            TextView(this).apply {
                text = "BIBLE VERSE VIDEO GENERATOR"
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 25)
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        modelStatus = TextView(this).apply {
            text = "Ryan High ONNX: not selected"
            textSize = 15f
        }

        root.addView(modelStatus)

        root.addView(
            Button(this).apply {
                text = "SELECT en_US-ryan-high.onnx"
                setOnClickListener {
                    pickModelFile()
                }
            }
        )

        configStatus = TextView(this).apply {
            text = "Ryan High JSON: not selected"
            textSize = 15f
        }

        root.addView(configStatus)

        root.addView(
            Button(this).apply {
                text = "SELECT en_US-ryan-high.onnx.json"
                setOnClickListener {
                    pickConfigFile()
                }
            }
        )

        root.addView(
            TextView(this).apply {
                text = "Video format"
                textSize = 15f
                setPadding(0, 15, 0, 5)
            }
        )

        formatSpinner = Spinner(this)

        formatSpinner.adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf(
                    "Vertical 1080 × 1920",
                    "Horizontal 1920 × 1080"
                )
            )

        root.addView(formatSpinner)

        generateButton = Button(this).apply {
            text = "GENERATE VIDEOS"
            setOnClickListener {
                startGeneration()
            }
        }

        root.addView(generateButton)

        progress = ProgressBar(this).apply {
            isIndeterminate = false
            max = 100
            progress = 0
        }

        root.addView(
            progress,
            LinearLayout.LayoutParams(
                -1,
                12
            )
        )

        log = TextView(this).apply {
            textSize = 13f
            setPadding(0, 16, 0, 16)
            textIsSelectable = true
        }

        val scroll = ScrollView(this).apply {
            addView(log)
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        setContentView(root)
    }

    private fun requestRequiredPermissions() {

        /*
         * Android 10+:
         * No storage permission is required for:
         * - ACTION_OPEN_DOCUMENT
         * - app-specific external storage
         * - MediaStore output
         *
         * Android 9 and below:
         * request legacy storage permission.
         */

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

            val missing = requiredOldStoragePermissions.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    missing.toTypedArray(),
                    permissionRequest
                )
            }
        }

        /*
         * Notification permission is NOT required for generation.
         * We intentionally do not request it because the app does not
         * need notifications.
         */
    }

    private fun pickModelFile() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/octet-stream"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        startActivityForResult(intent, pickModel)
    }

    private fun pickConfigFile() {

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }

        startActivityForResult(intent, pickConfig)
    }

    @Deprecated("Deprecated Android API retained for broad device compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }

        when (requestCode) {

            pickModel -> {

                modelUri = uri

                modelStatus.text =
                    "Ryan High ONNX: SELECTED\n${getFileName(uri)}"
            }

            pickConfig -> {

                configUri = uri

                configStatus.text =
                    "Ryan High JSON: SELECTED\n${getFileName(uri)}"
            }
        }

        appendLog("File selected successfully.")
    }

    private fun startGeneration() {

        val model = modelUri
        val config = configUri

        if (model == null) {
            toast("Please select en_US-ryan-high.onnx")
            return
        }

        if (config == null) {
            toast("Please select en_US-ryan-high.onnx.json")
            return
        }

        if (!hasRequiredAssets()) {
            toast("Required bundled assets are missing.")
            return
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            !hasLegacyStoragePermission()
        ) {
            toast("Storage permission is required on this Android version.")
            requestRequiredPermissions()
            return
        }

        generateButton.isEnabled = false
        progress.progress = 0
        log.text = ""

        appendLog("Starting generation...")
        appendLog("Checking files...")

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val piperDir =
                    File(filesDir, "piper").apply {
                        mkdirs()
                    }

                val modelFile =
                    copyUri(
                        model,
                        File(
                            piperDir,
                            "en_US-ryan-high.onnx"
                        )
                    )

                val configFile =
                    copyUri(
                        config,
                        File(
                            piperDir,
                            "en_US-ryan-high.onnx.json"
                        )
                    )

                if (!modelFile.exists() ||
                    modelFile.length() == 0L
                ) {
                    throw Exception(
                        "ONNX model could not be copied correctly."
                    )
                }

                if (!configFile.exists() ||
                    configFile.length() == 0L
                ) {
                    throw Exception(
                        "ONNX JSON could not be copied correctly."
                    )
                }

                appendLog("ONNX model copied.")
                appendLog("ONNX JSON copied.")

                val vertical =
                    formatSpinner.selectedItemPosition == 0

                val generator =
                    VideoGenerator(
                        this@MainActivity,
                        modelFile,
                        configFile,
                        vertical
                    ) { percent, message ->

                        runOnUiThread {

                            progress.progress =
                                percent.coerceIn(0, 100)

                            appendLog(message)
                        }
                    }

                generator.run()

                runOnUiThread {

                    progress.progress = 100

                    appendLog("")
                    appendLog("================================")
                    appendLog("GENERATION COMPLETED")
                    appendLog("================================")

                    toast(
                        "Videos generated successfully."
                    )
                }

            } catch (e: Throwable) {

                val error =
                    buildFullErrorMessage(e)

                runOnUiThread {

                    appendLog("")
                    appendLog("================================")
                    appendLog("GENERATION ERROR")
                    appendLog("================================")
                    appendLog(error)

                    toast(
                        "Generation failed. See error log."
                    )
                }

            } finally {

                runOnUiThread {
                    generateButton.isEnabled = true
                }
            }
        }
    }

    private fun hasRequiredAssets(): Boolean {

        return try {

            assets.open("quotes.txt").close()
            assets.open("font.ttf").close()
            assets.open("bg.mp3").close()

            true

        } catch (_: Exception) {
            false
        }
    }

    private fun hasLegacyStoragePermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun copyUri(
        uri: Uri,
        destination: File
    ): File {

        destination.parentFile?.mkdirs()

        contentResolver.openInputStream(uri)?.use { input ->

            FileOutputStream(destination).use { output ->

                input.copyTo(
                    output,
                    bufferSize = 1024 * 1024
                )
            }

        } ?: throw Exception(
            "Cannot open selected file."
        )

        return destination
    }

    private fun restoreSelectedFiles() {

        val dir = File(filesDir, "piper")

        val model =
            File(
                dir,
                "en_US-ryan-high.onnx"
            )

        val config =
            File(
                dir,
                "en_US-ryan-high.onnx.json"
            )

        if (model.exists() && model.length() > 0) {

            modelStatus.text =
                "Ryan High ONNX: already selected"
        }

        if (config.exists() && config.length() > 0) {

            configStatus.text =
                "Ryan High JSON: already selected"
        }

        if (model.exists() &&
            config.exists() &&
            model.length() > 0 &&
            config.length() > 0
        ) {

            modelUri = Uri.fromFile(model)
            configUri = Uri.fromFile(config)
        }
    }

    private fun getFileName(uri: Uri): String {

        var result = uri.lastPathSegment ?: "Selected file"

        try {

            contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->

                if (cursor.moveToFirst()) {

                    val index =
                        cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )

                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }

        } catch (_: Exception) {
        }

        return result
    }

    private fun buildFullErrorMessage(
        throwable: Throwable
    ): String {

        val sb = StringBuilder()

        var current: Throwable? = throwable
        var level = 0

        while (current != null) {

            sb.append("Exception #")
                .append(level)
                .append("\n")

            sb.append(
                current.javaClass.name
            )
                .append("\n")

            sb.append(
                current.message ?: "No error message"
            )
                .append("\n\n")

            current = current.cause
            level++
        }

        sb.append("STACK TRACE\n")

        val writer =
            java.io.StringWriter()

        throwable.printStackTrace(
            java.io.PrintWriter(writer)
        )

        sb.append(
            writer.toString()
        )

        return sb.toString()
    }

    private fun appendLog(message: String) {

        runOnUiThread {

            log.append(
                if (log.text.isEmpty()) {
                    message
                } else {
                    "\n$message"
                }
            )
        }
    }

    private fun toast(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
