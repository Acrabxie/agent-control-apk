package com.xiehaibo.agentcontrol.api

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class BridgeDiagnosticsDecodeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun decodesBridgeHealth() {
        val health = json.decodeFromString(
            BridgeHealth.serializer(),
            """
                {
                  "ok": true,
                  "version": "agent-control.v1",
                  "desktopName": "dev-mac",
                  "addresses": ["192.168.1.20"],
                  "pairedDevices": 1,
                  "pairing": {
                    "challengeTtlMs": 300000,
                    "maxAttempts": 5,
                    "keyExpiresAt": 1770000000000,
                    "pendingChallenges": 1,
                    "desktopFingerprint": "AA:BB"
                  }
                }
            """.trimIndent(),
        )

        assertThat(health.ok).isTrue()
        assertThat(health.version).isEqualTo("agent-control.v1")
        assertThat(health.pairing.maxAttempts).isEqualTo(5)
    }

    @Test
    fun decodesBridgeDiagnostics() {
        val diagnostics = json.decodeFromString(
            BridgeDiagnostics.serializer(),
            """
                {
                  "bridgeVersion": "agent-control.v1",
                  "desktopName": "dev-mac",
                  "generatedAt": 1770000000000,
                  "pairing": {
                    "pairedDeviceCount": 1,
                    "sessionActive": true,
                    "pairedDeviceId": "AA:BB:CC:DD",
                    "desktopFingerprint": "11:22",
                    "pendingChallenges": 0,
                    "keyExpiresAt": 1770000100000
                  },
                  "connectionMode": "relay",
                  "relayConfigured": true,
                  "relayHost": "agent-control-relay.example.com",
                  "pairedDeviceCount": 1,
                  "sessionActive": true,
                  "pairedDeviceId": "AA:BB:CC:DD",
                  "agents": [
                    {
                      "id": "codex",
                      "name": "Codex",
                      "kind": "CODEX",
                      "status": "ONLINE",
                      "tools": ["shell"],
                      "model": "gpt-5.5",
                      "reasoningEffort": "low",
                      "permissionMode": "workspace-write",
                      "contextUsedTokens": 120,
                      "contextLimitTokens": 400000,
                      "modelOptions": ["gpt-5.5"],
                      "reasoningOptions": ["low"],
                      "permissionOptions": ["read-only", "workspace-write"],
                      "lastAction": "codex.exec / running",
                      "diagnosticState": "ok"
                    }
                  ],
                  "runtimeOptions": {
                    "codex": {
                      "modelOptions": ["gpt-5.5"],
                      "reasoningOptions": ["low"],
                      "permissionOptions": ["read-only"]
                    }
                  },
                  "recentErrors": []
                }
            """.trimIndent(),
        )

        assertThat(diagnostics.relayConfigured).isTrue()
        assertThat(diagnostics.agents.single().model).isEqualTo("gpt-5.5")
        assertThat(diagnostics.runtimeOptions["codex"]?.permissionOptions).contains("read-only")
    }
}
