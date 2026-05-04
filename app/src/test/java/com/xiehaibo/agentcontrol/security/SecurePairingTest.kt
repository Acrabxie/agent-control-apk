package com.xiehaibo.agentcontrol.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecurePairingTest {
    @Test
    fun pairedKeysCanEncryptAndDecrypt() {
        val phone = SecurePairing.generateKeyPair()
        val desktop = SecurePairing.generateKeyPair()

        val phoneKey = SecurePairing.deriveSessionKey(
            privateKey = phone.private,
            localPublicKey = phone.public,
            remotePublicKey = desktop.public,
        )
        val desktopKey = SecurePairing.deriveSessionKey(
            privateKey = desktop.private,
            localPublicKey = desktop.public,
            remotePublicKey = phone.public,
        )

        assertThat(phoneKey.encoded).isEqualTo(desktopKey.encoded)

        val payload = SecurePairing.encrypt("hello bridge".toByteArray(), phoneKey)
        val decrypted = SecurePairing.decrypt(payload, desktopKey)

        assertThat(String(decrypted)).isEqualTo("hello bridge")
    }

    @Test
    fun publicKeysRoundTripThroughCompactText() {
        val pair = SecurePairing.generateKeyPair()
        val encoded = SecurePairing.encodePublicKey(pair.public)
        val decoded = SecurePairing.decodePublicKey(encoded)

        assertThat(decoded.encoded).isEqualTo(pair.public.encoded)
        assertThat(SecurePairing.fingerprint(decoded)).contains(":")
    }

    @Test
    fun pairingProofVerifiesOnlyForMatchingKeyAndChallenge() {
        val phone = SecurePairing.generateKeyPair()
        val desktop = SecurePairing.generateKeyPair()
        val devicePublicKey = SecurePairing.encodePublicKey(phone.public)
        val desktopPublicKey = SecurePairing.encodePublicKey(desktop.public)
        val proof = SecurePairing.pairingProof(
            pairingKey = "1234 5678",
            sessionId = "session-1",
            nonce = "nonce-1",
            devicePublicKey = devicePublicKey,
            desktopPublicKey = desktopPublicKey,
        )

        assertThat(
            SecurePairing.verifyPairingProof(
                pairingKey = "12345678",
                sessionId = "session-1",
                nonce = "nonce-1",
                devicePublicKey = devicePublicKey,
                desktopPublicKey = desktopPublicKey,
                proof = proof,
            )
        ).isTrue()
        assertThat(
            SecurePairing.verifyPairingProof(
                pairingKey = "00000000",
                sessionId = "session-1",
                nonce = "nonce-1",
                devicePublicKey = devicePublicKey,
                desktopPublicKey = desktopPublicKey,
                proof = proof,
            )
        ).isFalse()
        assertThat(
            SecurePairing.verifyPairingProof(
                pairingKey = "12345678",
                sessionId = "session-1",
                nonce = "nonce-2",
                devicePublicKey = devicePublicKey,
                desktopPublicKey = desktopPublicKey,
                proof = proof,
            )
        ).isFalse()
    }

    @Test
    fun challengeExpiryAndDesktopFingerprintPinningAreDetected() {
        assertThat(SecurePairing.challengeExpired(expiresAt = 100L, now = 101L)).isTrue()
        assertThat(SecurePairing.challengeExpired(expiresAt = 100L, now = 99L)).isFalse()

        assertThat(SecurePairing.desktopFingerprintChanged(null, "AA:BB")).isFalse()
        assertThat(SecurePairing.desktopFingerprintChanged("AA:BB", "aa:bb")).isFalse()
        assertThat(SecurePairing.desktopFingerprintChanged("AA:BB", "CC:DD")).isTrue()
    }
}
