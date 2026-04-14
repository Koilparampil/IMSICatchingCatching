package com.example.imsicatching

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var logger: TelephonyLogger
    private lateinit var logView: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val allGranted = perms.values.all { it }
            if (allGranted) {
                logger.start()
            } else {
                appendLine("Permission denied; cannot access full telephony/cell data.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)
        val filePathView: TextView = findViewById(R.id.filePathView)
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)

        logger = TelephonyLogger(applicationContext) { line ->
            runOnUiThread { appendLine(line) }
        }

        filePathView.text = "Log file: ${logger.filePath()}"

        startButton.setOnClickListener {
            requestPermissionsAndStart()
        }

        stopButton.setOnClickListener {
            logger.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logger.stop()
    }

    private fun requestPermissionsAndStart() {
        val required = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            logger.start()
        } else {
            permissionLauncher.launch(required)
        }
    }

    private fun appendLine(line: String) {
        val current = logView.text?.toString().orEmpty()
        val merged = if (current.isBlank()) line else "$current\n$line"
        val lines = merged.lines()
        logView.text = lines.takeLast(300).joinToString("\n")
    }
}
