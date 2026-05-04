package com.xiehaibo.agentcontrol.api

import android.util.Log
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.security.EncryptedPayload
import com.xiehaibo.agentcontrol.security.SecurePairing
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.KeyPair
import javax.crypto.SecretKey

object NetworkBridgeClient {
    private const val TAG = "AgentControlBridge"
    private const val DEFAULT_READ_TIMEOUT_MS = 90_000
    private const val MESSAGE_READ_TIMEOUT_MS = 310_000

    init {
        System.setProperty("http.keepAlive", "false")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun pair(
        desktopUrl: String,
        pairingKey: String,
        devicePublicKey: String,
        keyPair: KeyPair,
        pinnedDesktopFingerprint: String?,
    ): PairingResult {
        val challengeText = requestJson(
            method = "GET",
            url = desktopUrl.endpoint("/v1/pairing-challenge"),
            headers = mapOf("X-Pairing-Key" to pairingKey),
        )
        val challenge = json.decodeFromString(PairingChallenge.serializer(), challengeText)
        if (SecurePairing.challengeExpired(challenge.expiresAt)) {
            error("Pairing key expired. Refresh the desktop key and try again.")
        }
        val desktopPublicKey = SecurePairing.decodePublicKey(challenge.desktopPublicKey)
        val actualDesktopFingerprint = SecurePairing.fingerprint(desktopPublicKey)
        if (!actualDesktopFingerprint.equals(challenge.desktopFingerprint, ignoreCase = true)) {
            error("Desktop fingerprint did not match the pairing challenge.")
        }
        if (SecurePairing.desktopFingerprintChanged(pinnedDesktopFingerprint, actualDesktopFingerprint)) {
            error("Desktop identity changed. Forget this computer, verify the desktop key, then pair again.")
        }

        val proof = SecurePairing.pairingProof(
            pairingKey = pairingKey,
            sessionId = challenge.sessionId,
            nonce = challenge.nonce,
            devicePublicKey = devicePublicKey,
            desktopPublicKey = challenge.desktopPublicKey,
        )
        val request = PairRequest(
            sessionId = challenge.sessionId,
            deviceName = "Agent Control Android",
            devicePublicKey = devicePublicKey,
            pairingProof = proof,
        )
        val responseText = requestJson(
            method = "POST",
            url = desktopUrl.endpoint("/v1/pair"),
            body = json.encodeToString(PairRequest.serializer(), request),
        )
        val response = json.decodeFromString(PairResponse.serializer(), responseText)
        require(response.accepted) { "Pairing rejected" }
        require(response.desktopFingerprint.equals(actualDesktopFingerprint, ignoreCase = true)) { "Desktop fingerprint changed during pairing" }
        val sessionKey = SecurePairing.deriveSessionKey(
            privateKey = keyPair.private,
            localPublicKey = keyPair.public,
            remotePublicKey = desktopPublicKey,
        )
        return PairingResult(response, sessionKey, challenge)
    }

    fun sendMessage(
        desktopUrl: String,
        deviceId: String,
        sessionKey: SecretKey,
        payload: OutboundMessagePayload,
    ): ChatMessage {
        val envelope = BridgeEnvelope(
            type = "message.send",
            id = System.currentTimeMillis().toString(),
            createdAt = System.currentTimeMillis(),
            payload = payload,
        )
        val encodedEnvelope = json.encodeToString(
            BridgeEnvelope.serializer(OutboundMessagePayload.serializer()),
            envelope,
        )
        val encrypted = SecurePairing.encrypt(encodedEnvelope.toByteArray(Charsets.UTF_8), sessionKey)
        val responseText = requestJson(
            method = "POST",
            url = desktopUrl.endpoint("/v1/messages"),
            body = json.encodeToString(EncryptedPayload.serializer(), encrypted),
            headers = mapOf("X-Device-Id" to deviceId),
            readTimeoutMs = MESSAGE_READ_TIMEOUT_MS,
        )
        val responsePayload = json.decodeFromString(EncryptedPayload.serializer(), responseText)
        val decrypted = SecurePairing.decrypt(responsePayload, sessionKey).toString(Charsets.UTF_8)
        val responseEnvelope = json.decodeFromString(
            BridgeEnvelope.serializer(ChatMessage.serializer()),
            decrypted,
        )
        return responseEnvelope.payload
    }

    fun fetchSnapshot(
        desktopUrl: String,
        deviceId: String,
        sessionKey: SecretKey,
    ): BridgeSnapshot {
        val responseText = requestJson(
            method = "GET",
            url = desktopUrl.endpoint("/v1/snapshot"),
            headers = mapOf("X-Device-Id" to deviceId),
        )
        val responsePayload = json.decodeFromString(EncryptedPayload.serializer(), responseText)
        val decrypted = SecurePairing.decrypt(responsePayload, sessionKey).toString(Charsets.UTF_8)
        val responseEnvelope = json.decodeFromString(
            BridgeEnvelope.serializer(BridgeSnapshot.serializer()),
            decrypted,
        )
        return responseEnvelope.payload
    }

    private fun requestJson(
        method: String,
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ): String {
        val endpoint = URL(url)
        val startedAt = System.currentTimeMillis()
        logDebug("$method ${endpoint.path} start")
        val connection = (endpoint.openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = false
            doInput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            logDebug("$method ${endpoint.path} -> $status in ${System.currentTimeMillis() - startedAt}ms")
            if (status !in 200..299) {
                error("HTTP $status: $text")
            }
            return text
        } catch (error: Throwable) {
            logError("$method ${endpoint.path} failed after ${System.currentTimeMillis() - startedAt}ms", error)
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logError(message: String, error: Throwable) {
        runCatching { Log.e(TAG, message, error) }
    }

    private fun String.endpoint(path: String): String = trimEnd('/') + path
}

data class PairingResult(
    val response: PairResponse,
    val sessionKey: SecretKey,
    val challenge: PairingChallenge,
)
