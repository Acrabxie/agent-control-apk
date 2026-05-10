package com.xiehaibo.agentcontrol.api

import com.xiehaibo.agentcontrol.model.AgentNode
import com.xiehaibo.agentcontrol.model.AgentTeam
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.FileTransfer
import com.xiehaibo.agentcontrol.model.HeartbeatEntry
import com.xiehaibo.agentcontrol.model.ProjectDocument
import com.xiehaibo.agentcontrol.model.SlashCommand
import kotlinx.serialization.Serializable

object BridgeApiContract {
    const val VERSION = "agent-control.v1"
    const val HEALTH_ENDPOINT = "GET /v1/health"
    const val PAIRING_CHALLENGE_ENDPOINT = "GET /v1/pairing-challenge"
    const val PAIR_ENDPOINT = "POST /v1/pair"
    const val STREAM_ENDPOINT = "GET /v1/stream"
    const val MESSAGE_ENDPOINT = "POST /v1/messages"
    const val FILE_ENDPOINT = "POST /v1/files"
    const val PROJECT_ENDPOINT = "PATCH /v1/projects/{projectId}/documents/{documentId}"
    const val COMMAND_ENDPOINT = "GET /v1/slash-commands"
    const val DIAGNOSTICS_ENDPOINT = "GET /v1/diagnostics"

    val requiredEvents = listOf(
        "agent.output",
        "agent.tool.started",
        "agent.tool.finished",
        "agent.spawned",
        "agent.relationship.changed",
        "team.changed",
        "file.uploaded",
        "file.available",
        "project.document.changed",
        "heartbeat",
    )
}

@Serializable
data class PairingChallenge(
    val sessionId: String,
    val desktopName: String,
    val desktopPublicKey: String,
    val desktopFingerprint: String,
    val nonce: String,
    val expiresAt: Long,
    val addresses: List<String> = emptyList(),
    val port: Int = 7149,
    val cipherSuite: String = "",
    val proof: String = "HMAC-SHA256",
)

@Serializable
data class PairResponse(
    val desktopPublicKey: String,
    val desktopName: String,
    val desktopFingerprint: String,
    val accepted: Boolean,
    val deviceId: String? = null,
    val pairedAt: Long? = null,
    val snapshot: BridgeSnapshot? = null,
)

@Serializable
data class PairRequest(
    val sessionId: String,
    val deviceName: String,
    val devicePublicKey: String,
    val pairingProof: String,
)

@Serializable
data class BridgeEnvelope<T>(
    val version: String = BridgeApiContract.VERSION,
    val type: String,
    val id: String,
    val createdAt: Long,
    val payload: T,
)

@Serializable
data class BridgeHealth(
    val ok: Boolean = false,
    val version: String = "",
    val service: String = "",
    val mode: String = "",
    val desktopName: String = "",
    val addresses: List<String> = emptyList(),
    val pairedDevices: Int = 0,
    val pairing: PublicPairingState = PublicPairingState(),
)

@Serializable
data class PublicPairingState(
    val challengeTtlMs: Long = 0L,
    val maxAttempts: Int = 0,
    val keyExpiresAt: Long = 0L,
    val pendingChallenges: Int = 0,
    val desktopFingerprint: String = "",
)

@Serializable
data class BridgeDiagnostics(
    val bridgeVersion: String = "",
    val desktopName: String = "",
    val generatedAt: Long = 0L,
    val pairing: DiagnosticPairingSummary = DiagnosticPairingSummary(),
    val connectionMode: String = "",
    val relayConfigured: Boolean = false,
    val relayHost: String = "",
    val pairedDeviceCount: Int = 0,
    val sessionActive: Boolean = false,
    val pairedDeviceId: String = "",
    val agents: List<AgentDiagnostic> = emptyList(),
    val runtimeOptions: Map<String, RuntimeOptionAvailability> = emptyMap(),
    val recentErrors: List<String> = emptyList(),
)

@Serializable
data class DiagnosticPairingSummary(
    val pairedDeviceCount: Int = 0,
    val sessionActive: Boolean = false,
    val pairedDeviceId: String = "",
    val desktopFingerprint: String = "",
    val pendingChallenges: Int = 0,
    val keyExpiresAt: Long = 0L,
)

@Serializable
data class AgentDiagnostic(
    val id: String,
    val name: String,
    val kind: String = "",
    val status: String = "",
    val parentId: String? = null,
    val teamId: String = "",
    val tools: List<String> = emptyList(),
    val model: String = "",
    val reasoningEffort: String = "",
    val permissionMode: String = "",
    val contextUsedTokens: Int = 0,
    val contextLimitTokens: Int = 0,
    val modelOptions: List<String> = emptyList(),
    val reasoningOptions: List<String> = emptyList(),
    val permissionOptions: List<String> = emptyList(),
    val lastAction: String = "",
    val lastError: String = "",
    val diagnosticState: String = "unknown",
)

@Serializable
data class RuntimeOptionAvailability(
    val modelOptions: List<String> = emptyList(),
    val reasoningOptions: List<String> = emptyList(),
    val permissionOptions: List<String> = emptyList(),
)

@Serializable
data class ConnectionDiagnostics(
    val generatedAt: Long = 0L,
    val summary: String = "",
    val checks: List<DiagnosticCheck> = emptyList(),
    val health: BridgeHealth? = null,
    val bridge: BridgeDiagnostics? = null,
)

@Serializable
data class DiagnosticCheck(
    val id: String,
    val label: String,
    val status: String,
    val detail: String = "",
)

@Serializable
data class BridgeSnapshot(
    val agents: List<AgentNode>,
    val teams: List<AgentTeam> = emptyList(),
    val commands: List<SlashCommand>,
    val messages: List<ChatMessage>,
    val transfers: List<FileTransfer>,
    val documents: List<ProjectDocument>,
    val heartbeats: List<HeartbeatEntry>,
    val runtimeSettings: AgentRuntimeSettings = AgentRuntimeSettings(),
)

@Serializable
data class OutboundMessagePayload(
    val text: String,
    val targetAgentId: String,
    val clientMessageId: String = "",
    val conversationId: String = "",
    val agentPermissionMode: String = "",
    val attachments: List<FileTransfer> = emptyList(),
    val runtimeOptions: CodexRuntimeSettings? = null,
    val conversationContext: List<ChatMessage> = emptyList(),
)

@Serializable
data class AgentRuntimeSettings(
    val codex: CodexRuntimeSettings = CodexRuntimeSettings(),
    val agents: Map<String, CodexRuntimeSettings> = emptyMap(),
    val permissionOptions: List<RuntimeOption> = emptyList(),
)

@Serializable
data class CodexRuntimeSettings(
    val model: String = "gpt-5.5",
    val reasoningEffort: String = "low",
    val permissionMode: String = "read-only",
    val contextUsedTokens: Int = 0,
    val contextLimitTokens: Int = 400_000,
    val updatedAt: Long = 0L,
    val modelOptions: List<RuntimeOption> = emptyList(),
    val reasoningOptions: List<RuntimeOption> = emptyList(),
    val permissionOptions: List<RuntimeOption> = emptyList(),
)

@Serializable
data class RuntimeOption(
    val id: String,
    val label: String,
)
