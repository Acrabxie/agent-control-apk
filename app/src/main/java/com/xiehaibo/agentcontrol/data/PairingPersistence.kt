package com.xiehaibo.agentcontrol.data

import android.content.Context
import android.content.SharedPreferences
import com.xiehaibo.agentcontrol.model.PairingInfo
import java.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

data class RestoredPairing(
    val pairingInfo: PairingInfo,
    val deviceId: String,
    val sessionKey: SecretKey,
)

interface PairingPersistence {
    fun load(
        devicePublicKey: String,
        deviceFingerprint: String,
        cipherSuite: String,
    ): RestoredPairing?

    fun save(pairingInfo: PairingInfo, deviceId: String, sessionKey: SecretKey?)

    fun clear()
}

class SharedPreferencesPairingPersistence(context: Context) : PairingPersistence {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("agent_control_pairing", Context.MODE_PRIVATE)

    override fun load(
        devicePublicKey: String,
        deviceFingerprint: String,
        cipherSuite: String,
    ): RestoredPairing? {
        if (!prefs.getBoolean(KEY_PAIRED, false)) return null
        val desktopUrl = prefs.getString(KEY_DESKTOP_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val encodedKey = prefs.getString(KEY_SESSION_KEY, null)?.takeIf { it.isNotBlank() } ?: return null
        val key = runCatching {
            SecretKeySpec(Base64.getUrlDecoder().decode(encodedKey), "AES")
        }.getOrElse {
            clear()
            return null
        }
        val info = PairingInfo(
            paired = true,
            desktopUrl = desktopUrl,
            devicePublicKey = devicePublicKey,
            fingerprint = deviceFingerprint,
            cipherSuite = cipherSuite,
            desktopName = prefs.getString(KEY_DESKTOP_NAME, null),
            desktopFingerprint = prefs.getString(KEY_DESKTOP_FINGERPRINT, null),
            pairedAt = prefs.longOrNull(KEY_PAIRED_AT),
            lastVerifiedAt = prefs.longOrNull(KEY_LAST_VERIFIED_AT),
            challengeExpiresAt = null,
            lastPairingError = null,
        )
        return RestoredPairing(info, deviceId, key)
    }

    override fun save(pairingInfo: PairingInfo, deviceId: String, sessionKey: SecretKey?) {
        val encodedKey = sessionKey?.encoded ?: return
        if (!pairingInfo.paired || deviceId.isBlank() || pairingInfo.desktopUrl.isBlank()) return
        prefs.edit()
            .putBoolean(KEY_PAIRED, true)
            .putString(KEY_DESKTOP_URL, pairingInfo.desktopUrl)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_SESSION_KEY, Base64.getUrlEncoder().withoutPadding().encodeToString(encodedKey))
            .putString(KEY_DESKTOP_NAME, pairingInfo.desktopName)
            .putString(KEY_DESKTOP_FINGERPRINT, pairingInfo.desktopFingerprint)
            .putLong(KEY_PAIRED_AT, pairingInfo.pairedAt ?: System.currentTimeMillis())
            .putLong(KEY_LAST_VERIFIED_AT, pairingInfo.lastVerifiedAt ?: System.currentTimeMillis())
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun SharedPreferences.longOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L).takeIf { it > 0L } else null

    private companion object {
        const val KEY_PAIRED = "paired"
        const val KEY_DESKTOP_URL = "desktop_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SESSION_KEY = "session_key"
        const val KEY_DESKTOP_NAME = "desktop_name"
        const val KEY_DESKTOP_FINGERPRINT = "desktop_fingerprint"
        const val KEY_PAIRED_AT = "paired_at"
        const val KEY_LAST_VERIFIED_AT = "last_verified_at"
    }
}
