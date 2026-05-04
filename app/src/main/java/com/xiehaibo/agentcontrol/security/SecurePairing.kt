package com.xiehaibo.agentcontrol.security

import kotlinx.serialization.Serializable
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object SecurePairing {
    const val CIPHER_SUITE = "ECDH-P256 + HKDF-SHA256 + AES-256-GCM"
    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        return generator.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.encoded)

    fun decodePublicKey(encoded: String): PublicKey {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun deriveSessionKey(
        privateKey: PrivateKey,
        localPublicKey: PublicKey,
        remotePublicKey: PublicKey,
    ): SecretKey {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(remotePublicKey, true)
        val sharedSecret = agreement.generateSecret()
        val salt = orderedDigest(localPublicKey.encoded, remotePublicKey.encoded)
        val keyBytes = hkdfSha256(
            inputKeyMaterial = sharedSecret,
            salt = salt,
            info = "agent-control pairing v1".toByteArray(Charsets.UTF_8),
            length = 32,
        )
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: ByteArray, key: SecretKey): EncryptedPayload {
        val nonce = ByteArray(12)
        random.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(plainText)
        return EncryptedPayload(
            nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce),
            cipherText = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted),
        )
    }

    fun decrypt(payload: EncryptedPayload, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, Base64.getUrlDecoder().decode(payload.nonce)),
        )
        return cipher.doFinal(Base64.getUrlDecoder().decode(payload.cipherText))
    }

    fun fingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return digest.take(12).joinToString(":") { "%02X".format(it) }
    }

    fun pairingProof(
        pairingKey: String,
        sessionId: String,
        nonce: String,
        devicePublicKey: String,
        desktopPublicKey: String,
    ): String {
        val message = listOf(sessionId, nonce, devicePublicKey, desktopPublicKey).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(normalizePairingKey(pairingKey).toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }

    fun verifyPairingProof(
        pairingKey: String,
        sessionId: String,
        nonce: String,
        devicePublicKey: String,
        desktopPublicKey: String,
        proof: String,
    ): Boolean {
        val expected = pairingProof(pairingKey, sessionId, nonce, devicePublicKey, desktopPublicKey)
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), proof.toByteArray(Charsets.UTF_8))
    }

    fun normalizePairingKey(value: String): String = value.filter { it.isDigit() }

    fun challengeExpired(expiresAt: Long, now: Long = System.currentTimeMillis()): Boolean = expiresAt <= now

    fun desktopFingerprintChanged(pinnedFingerprint: String?, actualFingerprint: String): Boolean =
        !pinnedFingerprint.isNullOrBlank() && !pinnedFingerprint.equals(actualFingerprint, ignoreCase = true)

    private fun orderedDigest(left: ByteArray, right: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val compareLength = min(left.size, right.size)
        val leftFirst = (0 until compareLength).firstOrNull { left[it] != right[it] }
            ?.let { left[it].toUByte() < right[it].toUByte() }
            ?: (left.size <= right.size)
        if (leftFirst) {
            digest.update(left)
            digest.update(right)
        } else {
            digest.update(right)
            digest.update(left)
        }
        return digest.digest()
    }

    private fun hkdfSha256(
        inputKeyMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(inputKeyMaterial)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < length) {
            mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val copyLength = min(previous.size, length - generated)
            previous.copyInto(output, destinationOffset = generated, endIndex = copyLength)
            generated += copyLength
            counter += 1
        }
        return output
    }
}

@Serializable
data class EncryptedPayload(
    val nonce: String,
    val cipherText: String,
)
