package com.example.imsicatching

import android.content.Context
import android.os.Build
import android.telephony.CellIdentityLte
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TelephonyLogger(
    private val context: Context,
    private val onLine: ((String) -> Unit)? = null
) {
    data class CsvRow(
        val event: String,
        val networkType: String = "",
        val serviceState: String = "",
        val dataRegState: String = "",
        val emergencyOnly: String = "",
        val dataEnabled: String = "",
        val mcc: String = "",
        val mnc: String = "",
        val plmn: String = "",
        val tac: String = "",
        val pci: String = "",
        val earfcn: String = "",
        val cellId: String = "",
        val signalDbm: String = "",
        val signalAsu: String = "",
        val rsrp: String = "",
        val rsrq: String = "",
        val rssnrSinr: String = "",
        val regInfo: String = "",
        val notes: String = ""
    )

    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val txtFile = File(context.filesDir, "telemetry_log.txt")
    private val csvFile = File(context.filesDir, "telemetry_log.csv")

    private var isRunning = false
    private var callback31: TelephonyCallback? = null
    private var listenerLegacy: PhoneStateListener? = null

    fun textLogPath(): String = txtFile.absolutePath
    fun csvLogPath(): String = csvFile.absolutePath

    fun start() {
        if (isRunning) return
        isRunning = true
        ensureCsvHeader()
        append("session_start")
        appendCsv(CsvRow(event = "session_start"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.ServiceStateListener,
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.CellInfoListener,
                TelephonyCallback.DataConnectionStateListener,
                TelephonyCallback.DisplayInfoListener,
                TelephonyCallback.UserMobileDataStateListener {

                override fun onServiceStateChanged(serviceState: ServiceState) {
                    appendServiceState("service_state", serviceState)
                }

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    appendSignal(signalStrength)
                }

                override fun onCellInfoChanged(cellInfo: List<CellInfo>) {
                    appendCellInfo(cellInfo)
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    val nt = networkTypeToString(networkType)
                    append("data_connection state=${dataStateToString(state)} networkType=$nt")
                    appendCsv(
                        CsvRow(
                            event = "data_connection",
                            networkType = nt,
                            notes = dataStateToString(state)
                        )
                    )
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    val networkType = networkTypeToString(telephonyDisplayInfo.networkType)
                    append("display_info override=${telephonyDisplayInfo.overrideNetworkType} network=$networkType")
                    appendCsv(
                        CsvRow(
                            event = "display_info",
                            networkType = networkType,
                            notes = "override=${telephonyDisplayInfo.overrideNetworkType}"
                        )
                    )
                }

                override fun onUserMobileDataStateChanged(enabled: Boolean) {
                    append("mobile_data_enabled=$enabled")
                    appendCsv(CsvRow(event = "mobile_data", dataEnabled = enabled.toString()))
                }
            }
            callback31 = callback
            telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onServiceStateChanged(serviceState: ServiceState?) {
                    serviceState?.let { appendServiceState("service_state", it) }
                }

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    signalStrength?.let { appendSignal(it) }
                }

                override fun onCellInfoChanged(cellInfo: List<CellInfo>?) {
                    cellInfo?.let { appendCellInfo(it) }
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    val nt = networkTypeToString(networkType)
                    append("data_connection state=${dataStateToString(state)} networkType=$nt")
                    appendCsv(
                        CsvRow(
                            event = "data_connection",
                            networkType = nt,
                            notes = dataStateToString(state)
                        )
                    )
                }

                override fun onDataConnectionStateChanged(state: Int) {
                    append("data_connection state=${dataStateToString(state)}")
                }

                override fun onUserMobileDataStateChanged(enabled: Boolean) {
                    append("mobile_data_enabled=$enabled")
                    appendCsv(CsvRow(event = "mobile_data", dataEnabled = enabled.toString()))
                }
            }
            listenerLegacy = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(
                listener,
                PhoneStateListener.LISTEN_SERVICE_STATE or
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    PhoneStateListener.LISTEN_CELL_INFO or
                    PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
            )
        }

        pollCurrentState()
    }

    fun stop() {
        if (!isRunning) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback31?.let { telephonyManager.unregisterTelephonyCallback(it) }
            callback31 = null
        } else {
            @Suppress("DEPRECATION")
            listenerLegacy?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            listenerLegacy = null
        }
        isRunning = false
        append("session_stop")
        appendCsv(CsvRow(event = "session_stop"))
    }

    private fun pollCurrentState() {
        val dataType = networkTypeToString(telephonyManager.dataNetworkType)
        val voiceType = networkTypeToString(telephonyManager.voiceNetworkType)
        append("network_summary data=$dataType voice=$voiceType")
        appendCsv(CsvRow(event = "network_summary", networkType = "$dataType/$voiceType"))
        append("data_enabled=${telephonyManager.isDataEnabled}")
        appendCsv(CsvRow(event = "data_enabled_snapshot", dataEnabled = telephonyManager.isDataEnabled.toString()))
        telephonyManager.serviceState?.let { appendServiceState("service_state_snapshot", it) }
        try {
            appendCellInfo(telephonyManager.allCellInfo ?: emptyList())
        } catch (se: SecurityException) {
            append("cell_info_error=SecurityException(${se.message})")
            appendCsv(CsvRow(event = "cell_info_error", notes = "SecurityException"))
        }
    }

    private fun appendSignal(signalStrength: SignalStrength) {
        append("signal_strength dbm=${signalStrength.dbm} level=${signalStrength.level} asu=${signalStrength.gsmSignalStrength} raw=$signalStrength")
        appendCsv(
            CsvRow(
                event = "signal_strength",
                signalDbm = signalStrength.dbm.toString(),
                signalAsu = signalStrength.gsmSignalStrength.toString(),
                notes = "level=${signalStrength.level}"
            )
        )
    }

    private fun appendServiceState(label: String, s: ServiceState) {
        val registrationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s.networkRegistrationInfoList.joinToString("|") { info ->
                "domain=${info.domain},transport=${info.transportType},regState=${info.registrationState},roamingType=${info.roamingType},accessTech=${info.accessNetworkTechnology}"
            }
        } else {
            "unavailable_pre_api29"
        }
        val dataRegState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceStateToString(s.dataRegistrationState)
        } else {
            "n/a_pre_api30"
        }
        val state = serviceStateToString(s.state)
        val plmn = s.operatorNumeric ?: ""
        append("$label state=$state dataReg=$dataRegState emergencyOnly=${s.isEmergencyOnly} operatorNumeric=${s.operatorNumeric ?: "unknown"} roaming=${s.roaming} regInfo=$registrationInfo")
        appendCsv(
            CsvRow(
                event = label,
                serviceState = state,
                dataRegState = dataRegState,
                emergencyOnly = s.isEmergencyOnly.toString(),
                plmn = plmn,
                regInfo = registrationInfo,
                notes = "roaming=${s.roaming}"
            )
        )
    }

    private fun appendCellInfo(cellInfos: List<CellInfo>) {
        if (cellInfos.isEmpty()) {
            append("cell_info empty")
            appendCsv(CsvRow(event = "cell_info", notes = "empty"))
            return
        }

        for ((index, info) in cellInfos.withIndex()) {
            val registered = info.isRegistered
            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) info.timestampMillis else info.timeStamp

            when (info) {
                is CellInfoLte -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    val mcc = ci.mccString.orEmpty()
                    val mnc = ci.mncString.orEmpty()
                    val plmn = "$mcc$mnc"
                    append(
                        "cell_info[$index] registered=$registered ts=$timestamp type=LTE mcc=$mcc mnc=$mnc plmn=$plmn tac=${ci.tac} pci=${ci.pci} earfcn=${ci.earfcn} cellId=${ci.ci} rsrp=${ss.rsrp} rsrq=${ss.rsrq} rssnr=${ss.rssnr} dbm=${ss.dbm}"
                    )
                    appendCsv(
                        CsvRow(
                            event = "cell_lte",
                            mcc = mcc,
                            mnc = mnc,
                            plmn = plmn,
                            tac = ci.tac.toString(),
                            pci = ci.pci.toString(),
                            earfcn = ci.earfcn.toString(),
                            cellId = ci.ci.toString(),
                            signalDbm = ss.dbm.toString(),
                            rsrp = ss.rsrp.toString(),
                            rsrq = ss.rsrq.toString(),
                            rssnrSinr = ss.rssnr.toString(),
                            notes = "registered=$registered"
                        )
                    )
                }

                is CellInfoNr -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    val mcc = ci.mccString.orEmpty()
                    val mnc = ci.mncString.orEmpty()
                    val plmn = "$mcc$mnc"
                    append(
                        "cell_info[$index] registered=$registered ts=$timestamp type=NR mcc=$mcc mnc=$mnc plmn=$plmn tac=${ci.tac} pci=${ci.pci} nrarfcn=${ci.nrarfcn} cellId=${ci.nci} ssRsrp=${ss.ssRsrp} ssRsrq=${ss.ssRsrq} ssSinr=${ss.ssSinr} dbm=${ss.dbm}"
                    )
                    appendCsv(
                        CsvRow(
                            event = "cell_nr",
                            mcc = mcc,
                            mnc = mnc,
                            plmn = plmn,
                            tac = ci.tac.toString(),
                            pci = ci.pci.toString(),
                            earfcn = ci.nrarfcn.toString(),
                            cellId = ci.nci.toString(),
                            signalDbm = ss.dbm.toString(),
                            rsrp = ss.ssRsrp.toString(),
                            rsrq = ss.ssRsrq.toString(),
                            rssnrSinr = ss.ssSinr.toString(),
                            notes = "registered=$registered"
                        )
                    )
                }

                is CellInfoGsm -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    append("cell_info[$index] registered=$registered ts=$timestamp type=GSM mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} arfcn=${ci.arfcn} dbm=${ss.dbm}")
                    appendCsv(
                        CsvRow(
                            event = "cell_gsm",
                            mcc = ci.mccString.orEmpty(),
                            mnc = ci.mncString.orEmpty(),
                            cellId = ci.cid.toString(),
                            earfcn = ci.arfcn.toString(),
                            signalDbm = ss.dbm.toString(),
                            signalAsu = ss.asuLevel.toString(),
                            notes = "registered=$registered lac=${ci.lac}"
                        )
                    )
                }

                is CellInfoWcdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    append("cell_info[$index] registered=$registered ts=$timestamp type=WCDMA mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} uarfcn=${ci.uarfcn} psc=${ci.psc} dbm=${ss.dbm}")
                    appendCsv(
                        CsvRow(
                            event = "cell_wcdma",
                            mcc = ci.mccString.orEmpty(),
                            mnc = ci.mncString.orEmpty(),
                            cellId = ci.cid.toString(),
                            earfcn = ci.uarfcn.toString(),
                            signalDbm = ss.dbm.toString(),
                            signalAsu = ss.asuLevel.toString(),
                            notes = "registered=$registered lac=${ci.lac}"
                        )
                    )
                }

                is CellInfoTdscdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    append("cell_info[$index] registered=$registered ts=$timestamp type=TDSCDMA mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} uarfcn=${ci.uarfcn} cpid=${ci.cpid} dbm=${ss.dbm}")
                    appendCsv(
                        CsvRow(
                            event = "cell_tdscdma",
                            mcc = ci.mccString.orEmpty(),
                            mnc = ci.mncString.orEmpty(),
                            cellId = ci.cid.toString(),
                            earfcn = ci.uarfcn.toString(),
                            signalDbm = ss.dbm.toString(),
                            signalAsu = ss.asuLevel.toString(),
                            notes = "registered=$registered lac=${ci.lac}"
                        )
                    )
                }

                is CellInfoCdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    append("cell_info[$index] registered=$registered ts=$timestamp type=CDMA networkId=${ci.networkId} systemId=${ci.systemId} basestationId=${ci.basestationId} cdmaDbm=${ss.cdmaDbm} evdoDbm=${ss.evdoDbm}")
                    appendCsv(
                        CsvRow(
                            event = "cell_cdma",
                            cellId = ci.basestationId.toString(),
                            signalDbm = ss.cdmaDbm.toString(),
                            notes = "registered=$registered networkId=${ci.networkId} systemId=${ci.systemId}"
                        )
                    )
                }

                else -> {
                    append("cell_info[$index] registered=$registered ts=$timestamp raw=$info")
                    appendCsv(CsvRow(event = "cell_unknown", notes = "registered=$registered raw=$info"))
                }
            }
        }
    }

    private fun ensureCsvHeader() {
        if (csvFile.exists() && csvFile.length() > 0L) return
        csvFile.appendText(
            "timestamp,event,network_type,service_state,data_reg_state,emergency_only,data_enabled,mcc,mnc,plmn,tac,pci,earfcn,cell_id,signal_dbm,signal_asu,rsrp,rsrq,rssnr_sinr,reg_info,notes\n"
        )
    }

    private fun append(message: String) {
        val line = "${dateFormat.format(Date())} | $message"
        txtFile.appendText(line + "\n")
        onLine?.invoke(line)
    }

    private fun appendCsv(row: CsvRow) {
        val ts = dateFormat.format(Date())
        val data = listOf(
            ts,
            row.event,
            row.networkType,
            row.serviceState,
            row.dataRegState,
            row.emergencyOnly,
            row.dataEnabled,
            row.mcc,
            row.mnc,
            row.plmn,
            row.tac,
            row.pci,
            row.earfcn,
            row.cellId,
            row.signalDbm,
            row.signalAsu,
            row.rsrp,
            row.rsrq,
            row.rssnrSinr,
            row.regInfo,
            row.notes
        ).joinToString(",") { escapeCsv(it) }
        csvFile.appendText(data + "\n")
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

    private fun dataStateToString(state: Int): String = when (state) {
        TelephonyManager.DATA_CONNECTED -> "CONNECTED"
        TelephonyManager.DATA_CONNECTING -> "CONNECTING"
        TelephonyManager.DATA_DISCONNECTED -> "DISCONNECTED"
        TelephonyManager.DATA_SUSPENDED -> "SUSPENDED"
        else -> "UNKNOWN($state)"
    }

    private fun networkTypeToString(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1xRTT"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
        else -> "TYPE_$type"
    }
}
