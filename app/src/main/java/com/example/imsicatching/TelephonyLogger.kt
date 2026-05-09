package com.example.imsicatching

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.telephony.CellIdentityNr
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.TelephonyManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelephonyLogger(
    private val context: Context,
    private val onLine: ((String) -> Unit)? = null
) {
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val csvFile = File(context.filesDir, "telemetry_log.csv")

    private var isRunning = false
    private var pollingThread: HandlerThread? = null
    private var pollingHandler: Handler? = null

    fun csvLogPath(): String = csvFile.absolutePath

    fun start() {
        if (isRunning) return
        isRunning = true
        ensureCsvHeader()

        val thread = HandlerThread("TelephonyPoller")
        thread.start()
        pollingThread = thread
        val handler = Handler(thread.looper)
        pollingHandler = handler

        val ticker = object : Runnable {
            override fun run() {
                if (!isRunning) return
                poll()
                handler.postDelayed(this, 50)
            }
        }
        handler.post(ticker)
    }

    fun stop() {
        isRunning = false
        pollingHandler?.removeCallbacksAndMessages(null)
        pollingThread?.quitSafely()
        pollingThread = null
        pollingHandler = null
    }

    private fun poll() {
        val ts = dateFormat.format(Date())
        val serviceState = serviceStateToString(
            telephonyManager.serviceState?.state ?: ServiceState.STATE_OUT_OF_SERVICE
        )

        val cells = try {
            telephonyManager.allCellInfo ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }

        val cell = cells.firstOrNull { it.isRegistered } ?: cells.firstOrNull()

        var mcc = ""; var mnc = ""; var plmn = ""
        var tac = ""; var pci = ""; var earfcn = ""
        var cellId = ""; var signalDbm = ""; var rsrp = ""

        when {
            cell is CellInfoLte -> {
                val ci = cell.cellIdentity
                val ss = cell.cellSignalStrength
                @Suppress("DEPRECATION")
                mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mccString.orEmpty()
                      else ci.mcc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                @Suppress("DEPRECATION")
                mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mncString.orEmpty()
                      else ci.mnc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                plmn = "$mcc$mnc"
                tac = ci.tac.toString()
                pci = ci.pci.toString()
                earfcn = ci.earfcn.toString()
                cellId = ci.ci.toString()
                signalDbm = ss.dbm.toString()
                rsrp = ss.rsrp.toString()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cell is CellInfoNr -> {
                val ci = cell.cellIdentity as CellIdentityNr
                val ss = cell.cellSignalStrength as CellSignalStrengthNr
                mcc = ci.mccString.orEmpty()
                mnc = ci.mncString.orEmpty()
                plmn = "$mcc$mnc"
                tac = ci.tac.toString()
                pci = ci.pci.toString()
                earfcn = ci.nrarfcn.toString()
                cellId = ci.nci.toString()
                signalDbm = ss.dbm.toString()
                rsrp = ss.ssRsrp.toString()
            }
            cell is CellInfoGsm -> {
                val ci = cell.cellIdentity
                val ss = cell.cellSignalStrength
                @Suppress("DEPRECATION")
                mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mccString.orEmpty()
                      else ci.mcc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                @Suppress("DEPRECATION")
                mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mncString.orEmpty()
                      else ci.mnc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                plmn = "$mcc$mnc"
                earfcn = ci.arfcn.toString()
                cellId = ci.cid.toString()
                signalDbm = ss.dbm.toString()
            }
            cell is CellInfoWcdma -> {
                val ci = cell.cellIdentity
                val ss = cell.cellSignalStrength
                @Suppress("DEPRECATION")
                mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mccString.orEmpty()
                      else ci.mcc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                @Suppress("DEPRECATION")
                mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.mncString.orEmpty()
                      else ci.mnc.takeIf { it != Int.MAX_VALUE }?.toString() ?: ""
                plmn = "$mcc$mnc"
                earfcn = ci.uarfcn.toString()
                cellId = ci.cid.toString()
                signalDbm = ss.dbm.toString()
            }
        }

        val line = listOf(ts, serviceState, mcc, mnc, plmn, tac, pci, earfcn, cellId, signalDbm, rsrp)
            .joinToString(",") { escapeCsv(it) }
        csvFile.appendText(line + "\n")
        onLine?.invoke(line)
    }

    private fun ensureCsvHeader() {
        if (csvFile.exists() && csvFile.length() > 0L) return
        csvFile.appendText("timestamp,service_state,mcc,mnc,plmn,tac,pci,earfcn,cell_id,signal_dbm,rsrp\n")
    }

    private fun escapeCsv(value: String): String {
        val clean = value.replace("\n", " ")
        return if (clean.contains(',') || clean.contains('"')) {
            "\"${clean.replace("\"", "\"\"")}\""
        } else {
            clean
        }
    }

    private fun serviceStateToString(state: Int): String = when (state) {
        ServiceState.STATE_IN_SERVICE -> "IN_SERVICE"
        ServiceState.STATE_OUT_OF_SERVICE -> "OUT_OF_SERVICE"
        ServiceState.STATE_EMERGENCY_ONLY -> "EMERGENCY_ONLY"
        ServiceState.STATE_POWER_OFF -> "POWER_OFF"
        else -> "UNKNOWN($state)"
    }
}
