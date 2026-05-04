package com.xiehaibo.agentcontrol.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentKind {
    CODEX,
    CLAUDE_CODE,
    ANTIGRAVITY,
    GEMINI_CLI,
    OPENCODE,
    SUBAGENT,
}

@Serializable
enum class AgentStatus {
    ONLINE,
    BUSY,
    IDLE,
    PAUSED,
}

@Serializable
enum class MessageKind {
    USER,
    AGENT,
    SYSTEM,
}

@Serializable
enum class ToolStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
}

@Serializable
enum class TransferDirection {
    PHONE_TO_DESKTOP,
    DESKTOP_TO_PHONE,
}

@Serializable
data class AgentNode(
    val id: String,
    val name: String,
    val kind: AgentKind,
    val role: String,
    val status: AgentStatus,
    val parentId: String? = null,
    val teamId: String = "core",
    val tools: List<String> = emptyList(),
    val canSpawnChildren: Boolean = false,
)

@Serializable
data class AgentTeam(
    val id: String,
    val name: String,
    val adminAgentId: String,
    val memberIds: List<String>,
    val sharedProfile: String,
    val createdByAgentId: String? = null,
    val purpose: String = "",
    val sharedDocuments: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val canAgentsPost: Boolean = true,
)

@Serializable
data class FileTransfer(
    val id: String,
    val name: String,
    val mimeType: String,
    val direction: TransferDirection,
    val uri: String,
    val sizeLabel: String = "queued",
)

@Serializable
data class ToolCall(
    val id: String,
    val agentId: String,
    val toolName: String,
    val status: ToolStatus,
    val input: String,
    val output: String,
    val startedAt: Long,
)

@Serializable
data class ChatMessage(
    val id: String,
    val authorId: String,
    val kind: MessageKind,
    val text: String,
    val createdAt: Long,
    val targetAgentId: String? = null,
    val attachments: List<FileTransfer> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
)

@Serializable
data class SlashCommand(
    val trigger: String,
    val label: String,
    val target: String,
)

@Serializable
data class ProjectDocument(
    val id: String,
    val title: String,
    val path: String,
    val content: String,
    val editable: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class HeartbeatEntry(
    val id: String,
    val source: String,
    val text: String,
    val createdAt: Long,
)

@Serializable
data class PairingInfo(
    val paired: Boolean,
    val desktopUrl: String,
    val devicePublicKey: String,
    val fingerprint: String,
    val cipherSuite: String,
    val desktopName: String? = null,
    val desktopFingerprint: String? = null,
    val pairedAt: Long? = null,
    val lastVerifiedAt: Long? = null,
    val challengeExpiresAt: Long? = null,
    val lastPairingError: String? = null,
)
