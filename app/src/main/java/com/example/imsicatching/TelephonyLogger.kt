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
    private val onLine: (String) -> Unit
) {
    private val telephonyManager =
        context.getSystemService(TelephonyManager::class.java)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logFile = File(context.filesDir, "telemetry_log.txt")

    private var isRunning = false
    private var callback31: TelephonyCallback? = null
    private var listenerLegacy: PhoneStateListener? = null

    fun filePath(): String = logFile.absolutePath

    fun start() {
        if (isRunning) return
        isRunning = true
        append("session_start")

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
                    append(
                        "data_connection state=${dataStateToString(state)} networkType=${
                            networkTypeToString(
                                networkType
                            )
                        }"
                    )
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    append(
                        "display_info override=${telephonyDisplayInfo.overrideNetworkType} network=${networkTypeToString(telephonyDisplayInfo.networkType)}"
                    )
                }

                override fun onUserMobileDataStateChanged(enabled: Boolean) {
                    append("mobile_data_enabled=$enabled")
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
                    append(
                        "data_connection state=${dataStateToString(state)} networkType=${
                            networkTypeToString(
                                networkType
                            )
                        }"
                    )
                }

                override fun onDataConnectionStateChanged(state: Int) {
                    append("data_connection state=${dataStateToString(state)}")
                }

                override fun onUserMobileDataStateChanged(enabled: Boolean) {
                    append("mobile_data_enabled=$enabled")
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
    }

    private fun pollCurrentState() {
        append(
            "network_summary data=${networkTypeToString(telephonyManager.dataNetworkType)} voice=${networkTypeToString(telephonyManager.voiceNetworkType)}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            append("data_enabled=${telephonyManager.isDataEnabled}")
        }
        telephonyManager.serviceState?.let { appendServiceState("service_state_snapshot", it) }
        try {
            appendCellInfo(telephonyManager.allCellInfo ?: emptyList())
        } catch (se: SecurityException) {
            append("cell_info_error=SecurityException(${se.message})")
        }
    }

    private fun appendSignal(signalStrength: SignalStrength) {
        append(
            "signal_strength dbm=${signalStrength.dbm} level=${signalStrength.level} asu=${signalStrength.gsmSignalStrength} raw=${signalStrength}"
        )
    }

    private fun appendServiceState(label: String, s: ServiceState) {
        val registrationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            s.networkRegistrationInfoList.joinToString(separator = "|") { info ->
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
        append(
            "$label state=${serviceStateToString(s.state)} dataReg=$dataRegState voiceReg=${serviceStateToString(s.state)} emergencyOnly=${s.isEmergencyOnly} operatorNumeric=${s.operatorNumeric ?: "unknown"} operatorAlphaLong=${s.operatorAlphaLong ?: "unknown"} roaming=${s.roaming} nrState=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) s.nrState else "n/a"} regInfo=$registrationInfo"
        )
    }

    private fun appendCellInfo(cellInfos: List<CellInfo>) {
        if (cellInfos.isEmpty()) {
            append("cell_info empty")
            return
        }
        cellInfos.forEachIndexed { index, info ->
            val registered = info.isRegistered
            val timestampNs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.timestampMillis.toString()
            } else {
                info.timeStamp.toString()
            }
            val identityDetails = when (info) {
                is CellInfoLte -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    lteDetails(ci, ss.rsrp, ss.rsrq, ss.rssnr, ss.cqi, ss.dbm)
                }

                is CellInfoNr -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    nrDetails(ci, ss.ssRsrp, ss.ssRsrq, ss.ssSinr, ss.csiRsrp, ss.csiRsrq, ss.csiSinr, ss.dbm)
                }

                is CellInfoGsm -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    "type=GSM mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} arfcn=${ci.arfcn} bsic=${ci.bsic} dbm=${ss.dbm} asu=${ss.asuLevel}"
                }

                is CellInfoWcdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    "type=WCDMA mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} psc=${ci.psc} uarfcn=${ci.uarfcn} dbm=${ss.dbm} asu=${ss.asuLevel}"
                }

                is CellInfoTdscdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    "type=TDSCDMA mcc=${ci.mccString} mnc=${ci.mncString} lac=${ci.lac} cid=${ci.cid} cpid=${ci.cpid} uarfcn=${ci.uarfcn} dbm=${ss.dbm} asu=${ss.asuLevel}"
                }

                is CellInfoCdma -> {
                    val ci = info.cellIdentity
                    val ss = info.cellSignalStrength
                    "type=CDMA networkId=${ci.networkId} systemId=${ci.systemId} basestationId=${ci.basestationId} latitude=${ci.latitude} longitude=${ci.longitude} cdmaDbm=${ss.cdmaDbm} evdoDbm=${ss.evdoDbm}"
                }

                else -> "type=UNKNOWN raw=$info"
            }
            append("cell_info[$index] registered=$registered ts=$timestampNs $identityDetails")
        }
    }

    private fun lteDetails(
        ci: CellIdentityLte,
        rsrp: Int,
        rsrq: Int,
        rssnr: Int,
        cqi: Int,
        dbm: Int
    ): String {
        return "type=LTE mcc=${ci.mccString} mnc=${ci.mncString} plmn=${ci.mccString}${ci.mncString} tac=${ci.tac} ci=${ci.ci} pci=${ci.pci} earfcn=${ci.earfcn} bandwidth=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ci.bandwidth else "n/a"} rsrp=$rsrp rsrq=$rsrq rssnr=$rssnr cqi=$cqi dbm=$dbm"
    }

    private fun nrDetails(
        ci: CellIdentityNr,
        ssRsrp: Int,
        ssRsrq: Int,
        ssSinr: Int,
        csiRsrp: Int,
        csiRsrq: Int,
        csiSinr: Int,
        dbm: Int
    ): String {
        val nci = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ci.nci else Long.MAX_VALUE
        val tac = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ci.tac else Int.MAX_VALUE
        val nrarfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ci.nrarfcn else Int.MAX_VALUE
        val pci = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ci.pci else Int.MAX_VALUE
        return "type=NR mcc=${ci.mccString} mnc=${ci.mncString} plmn=${ci.mccString}${ci.mncString} tac=$tac nci=$nci pci=$pci nrarfcn=$nrarfcn ssRsrp=$ssRsrp ssRsrq=$ssRsrq ssSinr=$ssSinr csiRsrp=$csiRsrp csiRsrq=$csiRsrq csiSinr=$csiSinr dbm=$dbm"
    }

    private fun append(message: String) {
        val line = "${dateFormat.format(Date())} | $message"
        logFile.appendText(line + "\n")
        onLine(line)
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
