package com.xiehaibo.agentcontrol.api

import com.google.common.truth.Truth.assertThat
import com.xiehaibo.agentcontrol.security.SecurePairing
import java.io.File
import java.net.ServerSocket
import java.net.URL
import org.junit.Test

class NetworkBridgeClientInteropTest {
    @Test
    fun androidClientPairsAndReceivesMessageReplyFromNodeBridge() {
        val port = freePort()
        val repoRoot = File("..").canonicalFile
        val tempRoot = File(System.getProperty("java.io.tmpdir"), "agent-control-test-${System.nanoTime()}").apply { mkdirs() }
        val process = ProcessBuilder("node", "bridge/server.mjs")
            .directory(repoRoot)
            .redirectErrorStream(true)
            .apply {
                environment()["AGENT_CONTROL_PORT"] = port.toString()
                environment()["AGENT_CONTROL_RELAY_URL"] = ""
                environment()["AGENT_CONTROL_DISABLE_REAL_AGENTS"] = "1"
                environment()["AGENT_CONTROL_ROOT"] = tempRoot.absolutePath
            }
            .start()

        try {
            val desktopUrl = "http://127.0.0.1:$port"
            val pairingKey = waitForPairingKey(desktopUrl)
            val keyPair = SecurePairing.generateKeyPair()
            val result = NetworkBridgeClient.pair(
                desktopUrl = desktopUrl,
                pairingKey = pairingKey,
                devicePublicKey = SecurePairing.encodePublicKey(keyPair.public),
                keyPair = keyPair,
                pinnedDesktopFingerprint = null,
            )

            assertThat(result.response.accepted).isTrue()
            assertThat(result.response.deviceId).isNotEmpty()

            val reply = NetworkBridgeClient.sendMessage(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
                payload = OutboundMessagePayload(text = "hello from android client", targetAgentId = "codex"),
            )

            assertThat(reply.kind.name).isEqualTo("AGENT")
            assertThat(reply.text).contains("Codex received")

            val snapshot = NetworkBridgeClient.fetchSnapshot(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
            )
            assertThat(snapshot.messages.map { it.text }).contains("hello from android client")
            assertThat(snapshot.messages.map { it.text }).contains("Codex received: hello from android client")

            val teamName = "InteropTeam${System.nanoTime()}"
            NetworkBridgeClient.sendMessage(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
                payload = OutboundMessagePayload(text = "/team-create $teamName", targetAgentId = "codex"),
            )
            val teamSnapshot = NetworkBridgeClient.fetchSnapshot(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
            )
            val team = teamSnapshot.teams.first { it.name == teamName }
            assertThat(team.memberIds).containsAtLeast("codex", "claude", "gemini_cli")

            NetworkBridgeClient.sendMessage(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
                payload = OutboundMessagePayload(text = "hello team", targetAgentId = team.id),
            )
            val groupSnapshot = NetworkBridgeClient.fetchSnapshot(
                desktopUrl = desktopUrl,
                deviceId = result.response.deviceId.orEmpty(),
                sessionKey = result.sessionKey,
            )
            val teamMessages = groupSnapshot.messages.filter { it.targetAgentId == team.id }
            assertThat(teamMessages.map { it.text }).contains("hello team")
            assertThat(teamMessages.count { it.kind.name == "AGENT" }).isAtLeast(2)
        } finally {
            process.destroy()
            tempRoot.deleteRecursively()
        }
    }

    private fun freePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun waitForPairingKey(desktopUrl: String): String {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            runCatching {
                val page = URL(desktopUrl).readText()
                Regex("""<div class="key">(\d{4}) (\d{4})</div>""")
                    .find(page)
                    ?.let { return it.groupValues[1] + it.groupValues[2] }
            }
            Thread.sleep(50)
        }
        error("Bridge pairing page did not expose a key")
    }
}
