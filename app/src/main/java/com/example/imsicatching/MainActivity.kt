package com.example.imsicatching

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat.registerReceiver
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var logView: TextView
    private lateinit var statusView: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
            val allGranted = perms.values.all { it }
            if (allGranted) {
                startLoggingService()
            } else {
                appendLine("Permission denied; telephony/cell granularity may be reduced.")
            }
        }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LoggingForegroundService.ACTION_LOG_LINE -> {
                    val line = intent.getStringExtra(LoggingForegroundService.EXTRA_LINE)
                    if (!line.isNullOrBlank()) appendLine(line)
                }
                LoggingForegroundService.ACTION_STATUS -> {
                    val running = intent.getBooleanExtra(LoggingForegroundService.EXTRA_RUNNING, false)
                    statusView.text = if (running) "Status: Running" else "Status: Stopped"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)
        statusView = findViewById(R.id.statusView)
        val txtPathView: TextView = findViewById(R.id.filePathView)
        val csvPathView: TextView = findViewById(R.id.csvPathView)
        val startButton: Button = findViewById(R.id.startButton)
        val stopButton: Button = findViewById(R.id.stopButton)
        val exportCsvButton: Button = findViewById(R.id.exportCsvButton)

        txtPathView.text = "Text log: ${File(filesDir, "telemetry_log.txt").absolutePath}"
        csvPathView.text = "CSV log: ${File(filesDir, "telemetry_log.csv").absolutePath}"

        startButton.setOnClickListener { requestPermissionsAndStart() }
        stopButton.setOnClickListener { stopLoggingService() }
        exportCsvButton.setOnClickListener { exportCsv() }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(LoggingForegroundService.ACTION_LOG_LINE)
            addAction(LoggingForegroundService.ACTION_STATUS)
        }
        registerReceiver(this, logReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(logReceiver)
        super.onStop()
    }

    private fun requestPermissionsAndStart() {
        val required = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            required.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
            required.add(Manifest.permission.READ_BASIC_PHONE_STATE)
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startLoggingService()
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    private fun startLoggingService() {
        val serviceIntent = Intent(this, LoggingForegroundService::class.java).apply {
            action = LoggingForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        statusView.text = "Status: Starting..."
    }

    private fun stopLoggingService() {
        val serviceIntent = Intent(this, LoggingForegroundService::class.java).apply {
            action = LoggingForegroundService.ACTION_STOP
        }
        startService(serviceIntent)
        statusView.text = "Status: Stopped"
    }

    private fun exportCsv() {
        val csvFile = File(filesDir, "telemetry_log.csv")
        if (!csvFile.exists()) {
            Toast.makeText(this, "CSV file not found yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            csvFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export CSV"))
    }

    private fun appendLine(line: String) {
        val current = logView.text?.toString().orEmpty()
        val merged = if (current.isBlank()) line else "$current\n$line"
        val lines = merged.lines()
        logView.text = lines.takeLast(500).joinToString("\n")
    }
}
