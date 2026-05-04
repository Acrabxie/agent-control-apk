package com.xiehaibo.agentcontrol.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiehaibo.agentcontrol.api.BridgeApiContract
import com.xiehaibo.agentcontrol.api.BridgeSnapshot
import com.xiehaibo.agentcontrol.api.CodexRuntimeSettings
import com.xiehaibo.agentcontrol.api.OutboundMessagePayload
import com.xiehaibo.agentcontrol.api.PairingChallenge
import com.xiehaibo.agentcontrol.api.PairResponse
import com.xiehaibo.agentcontrol.api.RuntimeOption
import com.xiehaibo.agentcontrol.model.AgentKind
import com.xiehaibo.agentcontrol.model.AgentNode
import com.xiehaibo.agentcontrol.model.AgentStatus
import com.xiehaibo.agentcontrol.model.AgentTeam
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.FileTransfer
import com.xiehaibo.agentcontrol.model.HeartbeatEntry
import com.xiehaibo.agentcontrol.model.MessageKind
import com.xiehaibo.agentcontrol.model.PairingInfo
import com.xiehaibo.agentcontrol.model.ProjectDocument
import com.xiehaibo.agentcontrol.model.SlashCommand
import com.xiehaibo.agentcontrol.model.ToolCall
import com.xiehaibo.agentcontrol.model.ToolStatus
import com.xiehaibo.agentcontrol.model.TransferDirection
import com.xiehaibo.agentcontrol.security.SecurePairing
import java.util.UUID
import javax.crypto.SecretKey

class AgentControlStore(
    private val pairingPersistence: PairingPersistence? = null,
    private val conversationPersistence: ConversationPersistence? = null,
) {
    private companion object {
        const val MAX_LOCAL_MESSAGES = 1_000
        const val MAX_CONTEXT_MESSAGES = 40
        const val MAX_HEARTBEATS = 80
    }

    val keyPair = SecurePairing.generateKeyPair()

    var selectedAgentId by mutableStateOf("codex")
    var selectedTargetId by mutableStateOf("codex")
    var selectedDocumentId by mutableStateOf("memory")
    var draftText by mutableStateOf("")
    var editorText by mutableStateOf("")
    var desktopUrlDraft by mutableStateOf("")
    var pairingKeyDraft by mutableStateOf("")
    var deviceId by mutableStateOf("")
    var sessionKey by mutableStateOf<SecretKey?>(null)
    var codexRuntimeSettings by mutableStateOf(CodexRuntimeSettings())
    var pairingInfo by mutableStateOf(
        PairingInfo(
            paired = false,
            desktopUrl = "",
            devicePublicKey = SecurePairing.encodePublicKey(keyPair.public),
            fingerprint = SecurePairing.fingerprint(keyPair.public),
            cipherSuite = SecurePairing.CIPHER_SUITE,
        )
    )

    val pendingAttachments = mutableStateListOf<FileTransfer>()
    val agents = mutableStateListOf(
        AgentNode(
            id = "codex",
            name = "Codex",
            kind = AgentKind.CODEX,
            role = "controller, planner, integrator",
            status = AgentStatus.ONLINE,
            tools = listOf("shell", "patch", "browser", "notion", "android-build"),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "claude",
            name = "Claude Code",
            kind = AgentKind.CLAUDE_CODE,
            role = "deep implementation and repo surgery",
            status = AgentStatus.IDLE,
            tools = listOf("repo-read", "edit", "test", "review"),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "antigravity",
            name = "Antigravity",
            kind = AgentKind.ANTIGRAVITY,
            role = "independent UI/product review",
            status = AgentStatus.IDLE,
            tools = listOf("browser", "manual-check", "visual-review"),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "gemini_cli",
            name = "Gemini CLI",
            kind = AgentKind.GEMINI_CLI,
            role = "official Gemini API-key agent lane",
            status = AgentStatus.ONLINE,
            tools = listOf("gemini", "planning", "analysis", "review"),
            canSpawnChildren = false,
        ),
        AgentNode(
            id = "opencode",
            name = "OpenCode",
            kind = AgentKind.OPENCODE,
            role = "OpenCode CLI using DeepSeek V4-Pro",
            status = AgentStatus.ONLINE,
            tools = listOf("opencode", "deepseek-v4-pro", "coding", "review"),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "sub-security",
            name = "Security Subagent",
            kind = AgentKind.SUBAGENT,
            role = "pairing, envelope crypto, threat review",
            status = AgentStatus.ONLINE,
            parentId = "codex",
            tools = listOf("crypto-check", "api-review"),
        ),
        AgentNode(
            id = "sub-files",
            name = "Transfer Subagent",
            kind = AgentKind.SUBAGENT,
            role = "photo and file shuttle",
            status = AgentStatus.BUSY,
            parentId = "claude",
            tools = listOf("upload", "download", "checksum"),
        ),
    )

    val team = mutableStateOf(
        AgentTeam(
            id = "core",
            name = "Local Agent Team",
            adminAgentId = "codex",
            memberIds = agents.map { it.id },
            sharedProfile = "One shared roster, one admin, visible subagent tree.",
        )
    )
    val teams = mutableStateListOf(team.value)

    val commands = mutableStateListOf(
        SlashCommand("/status", "Status", "team"),
        SlashCommand("/agents", "Agents", "team"),
        SlashCommand("/spawn", "Spawn", "selected"),
        SlashCommand("/team", "Team", "team"),
        SlashCommand("/parent", "Parent", "selected"),
        SlashCommand("/memory", "Memory", "project"),
        SlashCommand("/heartbeat", "Heartbeat", "project"),
        SlashCommand("/files", "Files", "transfer"),
        SlashCommand("/photo", "Photo", "transfer"),
        SlashCommand("/tools", "Tools", "selected"),
        SlashCommand("/handoff", "Handoff", "team"),
        SlashCommand("/approve", "Approve", "selected"),
        SlashCommand("/pause", "Pause", "selected"),
        SlashCommand("/resume", "Resume", "selected"),
        SlashCommand("/stop", "Stop", "selected"),
        SlashCommand("/clear", "Clear", "chat"),
        SlashCommand("/api", "API", "project"),
        SlashCommand("/help", "Help", "chat"),
    )

    val messages = mutableStateListOf(
        ChatMessage(
            id = nextId(),
            authorId = "system",
            kind = MessageKind.SYSTEM,
            text = "Secure pairing is ready. Select an agent, use a slash command, or attach a file.",
            createdAt = now(),
        ),
        ChatMessage(
            id = nextId(),
            authorId = "codex",
            targetAgentId = "team",
            kind = MessageKind.AGENT,
            text = "MVP channel online. Tool calls, subagents, files, team hierarchy, memory, and heartbeat are visible from the phone.",
            createdAt = now(),
            toolCalls = listOf(
                ToolCall(
                    id = nextId(),
                    agentId = "codex",
                    toolName = "shared-agent-loop",
                    status = ToolStatus.SUCCESS,
                    input = "read roles, queue, memory, daily log",
                    output = "shared state mounted",
                    startedAt = now(),
                )
            ),
        ),
    )
    val activeConversationIds = mutableStateMapOf<String, String>()
    val agentPermissionModes = mutableStateMapOf<String, String>()

    val documents = mutableStateListOf(
        ProjectDocument(
            id = "memory",
            title = "MEMORY.md",
            path = "~/.agents/shared-agent-loop/MEMORY.md",
            content = """
                # Shared Loop Memory

                - Codex is controller and final integrator.
                - Claude Code handles implementation-heavy work.
                - Antigravity handles review and verification.
                - Local phone control must show every subagent and tool call.
            """.trimIndent(),
        ),
        ProjectDocument(
            id = "queue",
            title = "QUEUE.md",
            path = "~/.agents/shared-agent-loop/QUEUE.md",
            content = """
                # Active

                shared-2026-04-25-agent-control-apk
                owner: codex
                status: in_progress
                next_action: connect Android client to desktop bridge daemon.
            """.trimIndent(),
        ),
        ProjectDocument(
            id = "heartbeat",
            title = "heartbeat",
            path = "~/.agents/shared-agent-loop/daily/2026-04-25.md",
            content = """
                00:00 phone control APK started
                00:01 secure pairing model loaded
                00:02 UI agent registry synced
            """.trimIndent(),
        ),
        ProjectDocument(
            id = "api",
            title = "Bridge API",
            path = "docs/bridge-api.md",
            content = """
                ${BridgeApiContract.PAIRING_CHALLENGE_ENDPOINT}
                ${BridgeApiContract.PAIR_ENDPOINT}
                ${BridgeApiContract.STREAM_ENDPOINT}
                ${BridgeApiContract.MESSAGE_ENDPOINT}
                ${BridgeApiContract.FILE_ENDPOINT}
                ${BridgeApiContract.PROJECT_ENDPOINT}
                ${BridgeApiContract.COMMAND_ENDPOINT}

                Every payload is wrapped in agent-control.v1, encrypted after pairing with AES-256-GCM, and streamed as ordered events.
            """.trimIndent(),
        ),
    )

    val heartbeats = mutableStateListOf(
        HeartbeatEntry(nextId(), "codex", "Controller lease active; Android shell visible.", now()),
        HeartbeatEntry(nextId(), "sub-security", "Pairing fingerprint generated.", now()),
        HeartbeatEntry(nextId(), "sub-files", "Bidirectional transfer queue empty.", now()),
    )

    init {
        restorePersistedConversation()
        editorText = selectedDocument().content
        restorePersistedPairing()
    }

    fun selectedAgent(): AgentNode = agents.first { it.id == selectedAgentId }

    fun selectedTargetName(): String =
        teams.firstOrNull { it.id == selectedTargetId }?.name
            ?: agents.firstOrNull { it.id == selectedTargetId }?.name
            ?: selectedTargetId

    fun selectedDocument(): ProjectDocument = documents.first { it.id == selectedDocumentId }

    fun selectDocument(id: String) {
        selectedDocumentId = id
        editorText = selectedDocument().content
    }

    fun useDefaultDesktopUrl(defaultUrl: String) {
        val normalized = defaultUrl.trim().trimEnd('/')
        if (normalized.isNotBlank() && !pairingInfo.paired && desktopUrlDraft.isBlank()) {
            desktopUrlDraft = normalized
        }
    }

    fun preferDefaultRelayForPairedDesktop(defaultUrl: String) {
        val normalized = defaultUrl.trim().trimEnd('/')
        val current = pairingInfo.desktopUrl.trim().trimEnd('/')
        if (normalized.isBlank() || !pairingInfo.paired || current == normalized || !isLocalBridgeUrl(current)) return
        desktopUrlDraft = normalized
        pairingInfo = pairingInfo.copy(desktopUrl = normalized, lastVerifiedAt = now(), lastPairingError = null)
        pairingPersistence?.save(pairingInfo, deviceId, sessionKey)
        addSystemMessage("Switched this paired desktop to the secure relay so LAN IP changes do not break control.")
        persistConversation()
    }

    fun completePairing() {
        pairingInfo = pairingInfo.copy(
            paired = true,
            desktopUrl = desktopUrlDraft,
            pairedAt = now(),
            lastVerifiedAt = now(),
            challengeExpiresAt = null,
            lastPairingError = null,
        )
        messages.add(
            ChatMessage(
                id = nextId(),
                authorId = "system",
                kind = MessageKind.SYSTEM,
                text = "Paired with $desktopUrlDraft using ${pairingInfo.cipherSuite}. Fingerprint ${pairingInfo.fingerprint}.",
                createdAt = now(),
            )
        )
        heartbeats.add(0, HeartbeatEntry(nextId(), "security", "Encrypted pairing verified.", now()))
        persistConversation()
    }

    fun applyRemotePairing(
        desktopUrl: String,
        response: PairResponse,
        challenge: PairingChallenge,
        key: SecretKey,
    ) {
        sessionKey = key
        deviceId = response.deviceId.orEmpty()
        desktopUrlDraft = desktopUrl
        pairingInfo = pairingInfo.copy(
            paired = true,
            desktopUrl = desktopUrl,
            desktopName = response.desktopName,
            desktopFingerprint = response.desktopFingerprint,
            pairedAt = response.pairedAt ?: now(),
            lastVerifiedAt = now(),
            challengeExpiresAt = challenge.expiresAt,
            lastPairingError = null,
        )
        pairingKeyDraft = ""
        pairingPersistence?.save(pairingInfo, deviceId, sessionKey)
        response.snapshot?.let { applySnapshot(it) }
        addSystemMessage("Paired with ${response.desktopName}; encrypted bridge session active.")
        heartbeats.add(0, HeartbeatEntry(nextId(), "security", "Remote encrypted pairing verified.", now()))
        persistConversation()
    }

    fun pinnedDesktopFingerprintForDraft(): String? =
        pairingInfo.desktopFingerprint
            ?.takeIf { desktopUrlDraft.trim().trimEnd('/') == pairingInfo.desktopUrl.trim().trimEnd('/') }

    fun rememberPairingError(message: String) {
        pairingInfo = pairingInfo.copy(lastPairingError = message)
    }

    fun forgetDesktopPairing() {
        sessionKey = null
        deviceId = ""
        pairingKeyDraft = ""
        pairingPersistence?.clear()
        pairingInfo = pairingInfo.copy(
            paired = false,
            desktopUrl = "",
            desktopName = null,
            desktopFingerprint = null,
            pairedAt = null,
            lastVerifiedAt = null,
            challengeExpiresAt = null,
            lastPairingError = null,
        )
        addSystemMessage("Forgot the paired desktop. Verify the desktop key before pairing again.")
    }

    fun markPairingInvalid(message: String) {
        sessionKey = null
        deviceId = ""
        pairingKeyDraft = ""
        pairingPersistence?.clear()
        pairingInfo = pairingInfo.copy(
            paired = false,
            lastVerifiedAt = null,
            lastPairingError = message,
        )
        addSystemMessage(message)
    }

    private fun restorePersistedPairing() {
        val restored = pairingPersistence?.load(
            devicePublicKey = pairingInfo.devicePublicKey,
            deviceFingerprint = pairingInfo.fingerprint,
            cipherSuite = pairingInfo.cipherSuite,
        ) ?: return
        sessionKey = restored.sessionKey
        deviceId = restored.deviceId
        desktopUrlDraft = restored.pairingInfo.desktopUrl
        pairingInfo = restored.pairingInfo.copy(lastVerifiedAt = now(), lastPairingError = null)
    }

    fun applySnapshot(snapshot: BridgeSnapshot) {
        agents.clear()
        agents.addAll(snapshot.agents)
        teams.clear()
        teams.addAll(
            snapshot.teams.ifEmpty {
                listOf(team.value.copy(memberIds = snapshot.agents.map { it.id }))
            }
        )
        commands.clear()
        commands.addAll(snapshot.commands)
        mergeMessages(snapshot.messages)
        documents.clear()
        documents.addAll(snapshot.documents)
        heartbeats.clear()
        heartbeats.addAll(snapshot.heartbeats)
        codexRuntimeSettings = mergeCodexRuntimeSettings(snapshot.runtimeSettings.codex)
        team.value = teams.firstOrNull { it.id == "core" } ?: team.value.copy(memberIds = agents.map { it.id })
        if (agents.none { it.id == selectedAgentId }) {
            selectedAgentId = agents.firstOrNull()?.id ?: "codex"
        }
        if (agents.none { it.id == selectedTargetId } && teams.none { it.id == selectedTargetId }) {
            selectedTargetId = selectedAgentId
        }
        if (documents.none { it.id == selectedDocumentId }) {
            selectedDocumentId = documents.firstOrNull()?.id ?: "memory"
        }
        editorText = documents.firstOrNull { it.id == selectedDocumentId }?.content.orEmpty()
        persistConversation()
    }

    fun queueAttachment(uri: String, name: String, mimeType: String) {
        pendingAttachments.add(
            FileTransfer(
                id = nextId(),
                name = name,
                mimeType = mimeType,
                direction = TransferDirection.PHONE_TO_DESKTOP,
                uri = uri,
            )
        )
    }

    fun conversationIdFor(targetId: String): String =
        activeConversationIds[targetId] ?: defaultConversationId(targetId)

    fun ensureConversationFor(targetId: String): String {
        val conversationId = conversationIdFor(targetId)
        activeConversationIds[targetId] = conversationId
        persistConversation()
        return conversationId
    }

    fun startNewConversation(targetId: String = selectedTargetId): String {
        val conversationId = "conv:${targetId.take(32)}:${UUID.randomUUID().toString().take(8)}"
        activeConversationIds[targetId] = conversationId
        if (selectedTargetId == targetId) {
            draftText = ""
            pendingAttachments.clear()
        }
        persistConversation()
        return conversationId
    }

    fun messagesForActiveConversation(targetId: String): List<ChatMessage> =
        messages.filter { it.belongsToConversationThread(targetId, conversationIdFor(targetId)) }

    fun lastMessageForActiveConversation(targetId: String): ChatMessage? =
        messages.asReversed().firstOrNull { message ->
            message.kind != MessageKind.SYSTEM && message.belongsToConversationThread(targetId, conversationIdFor(targetId))
        }

    fun sendDraft() {
        val sent = consumeDraft() ?: return
        respondLocallyTo(sent)
    }

    fun consumeDraft(): ChatMessage? {
        val text = draftText.trim()
        if (text.isEmpty() && pendingAttachments.isEmpty()) return null
        val attachments = pendingAttachments.toList()
        pendingAttachments.clear()
        draftText = ""
        val targetId = selectedTargetId
        val conversationId = ensureConversationFor(targetId)
        val message = ChatMessage(
            id = nextId(),
            authorId = "you",
            kind = MessageKind.USER,
            text = text.ifEmpty { "Sent ${attachments.size} attachment(s)." },
            createdAt = now(),
            targetAgentId = targetId,
            conversationId = conversationId,
            attachments = attachments,
        )
        messages.add(message)
        pruneLocalMessages()
        persistConversation()
        return message
    }

    fun respondLocallyTo(message: ChatMessage) {
        respondTo(message.text, message.attachments)
    }

    fun outboundPayload(message: ChatMessage): OutboundMessagePayload =
        (message.targetAgentId ?: selectedAgentId).let { targetId ->
            OutboundMessagePayload(
                text = message.text,
                targetAgentId = targetId,
                clientMessageId = message.id,
                conversationId = message.conversationId.ifBlank { conversationIdFor(targetId) },
                agentPermissionMode = permissionForTarget(targetId),
                attachments = message.attachments,
                runtimeOptions = codexRuntimeSettings,
                conversationContext = conversationContextFor(message),
            )
        }

    fun updateCodexModel(model: String) {
        codexRuntimeSettings = codexRuntimeSettings.copy(model = model)
        persistConversation()
    }

    fun updateCodexReasoning(reasoningEffort: String) {
        codexRuntimeSettings = codexRuntimeSettings.copy(reasoningEffort = reasoningEffort)
        persistConversation()
    }

    fun updateCodexPermission(permissionMode: String) {
        updatePermissionForTarget("codex", permissionMode)
        codexRuntimeSettings = codexRuntimeSettings.copy(permissionMode = normalizePermissionMode(permissionMode))
        persistConversation()
    }

    fun permissionForTarget(targetId: String): String =
        normalizePermissionMode(agentPermissionModes[targetId] ?: if (targetId == "codex") codexRuntimeSettings.permissionMode else "read-only")

    fun updatePermissionForTarget(targetId: String = selectedTargetId, permissionMode: String) {
        val normalized = normalizePermissionMode(permissionMode)
        agentPermissionModes[targetId] = normalized
        if (targetId == "codex") {
            codexRuntimeSettings = codexRuntimeSettings.copy(permissionMode = normalized)
        }
        persistConversation()
    }

    private fun mergeCodexRuntimeSettings(remote: CodexRuntimeSettings): CodexRuntimeSettings {
        val current = codexRuntimeSettings
        return remote.copy(
            model = current.model.takeIf { optionExists(remote.modelOptions, it) } ?: remote.model,
            reasoningEffort = current.reasoningEffort.takeIf { optionExists(remote.reasoningOptions, it) } ?: remote.reasoningEffort,
            permissionMode = current.permissionMode.takeIf { optionExists(remote.permissionOptions, it) } ?: remote.permissionMode,
        )
    }

    private fun optionExists(options: List<RuntimeOption>, id: String): Boolean =
        options.isEmpty() || options.any { it.id == id }

    fun addRemoteReply(message: ChatMessage) {
        val targetId = if (message.targetAgentId == "you") message.authorId else message.targetAgentId ?: selectedTargetId
        val normalized = if (message.conversationId.isBlank()) {
            message.copy(conversationId = conversationIdFor(targetId))
        } else {
            message
        }
        messages.removeAll { it.id == normalized.id }
        messages.add(normalized)
        pruneLocalMessages()
        heartbeats.add(0, HeartbeatEntry(nextId(), "bridge", "Remote message accepted.", now()))
        pruneHeartbeats()
        persistConversation()
    }

    fun hasRemoteReplyFor(message: ChatMessage): Boolean {
        val targetId = message.targetAgentId ?: selectedTargetId
        val isTeam = teams.any { it.id == targetId }
        val conversationId = message.conversationId.ifBlank { conversationIdFor(targetId) }
        return messages.any { candidate ->
            candidate.kind == MessageKind.AGENT &&
                candidate.createdAt >= message.createdAt &&
                candidate.belongsToConversationThread(targetId, conversationId) &&
                if (isTeam) {
                    candidate.targetAgentId == targetId
                } else {
                    candidate.authorId == targetId && candidate.targetAgentId == "you"
                }
        }
    }

    fun addSystemMessage(text: String) {
        messages.add(
            ChatMessage(
                id = nextId(),
                authorId = "system",
                kind = MessageKind.SYSTEM,
                text = text,
                createdAt = now(),
            )
        )
        pruneLocalMessages()
        persistConversation()
    }

    fun runCommand(command: SlashCommand) {
        draftText = command.trigger
        when (command.trigger) {
            "/clear" -> {
                val targetId = selectedTargetId
                val conversationId = conversationIdFor(targetId)
                messages.removeAll { it.kind != MessageKind.SYSTEM && it.belongsToConversationThread(targetId, conversationId) }
                persistConversation()
            }
            "/spawn" -> spawnSubagent()
            "/memory" -> selectDocument("memory")
            "/heartbeat" -> selectDocument("heartbeat")
            "/api" -> selectDocument("api")
            else -> sendDraft()
        }
    }

    fun saveDocument() {
        val index = documents.indexOfFirst { it.id == selectedDocumentId }
        if (index >= 0) {
            documents[index] = documents[index].copy(content = editorText, updatedAt = now())
            heartbeats.add(0, HeartbeatEntry(nextId(), "project", "Saved ${documents[index].title}", now()))
            messages.add(
                ChatMessage(
                    id = nextId(),
                    authorId = "system",
                    kind = MessageKind.SYSTEM,
                    text = "Project document saved: ${documents[index].path}",
                    createdAt = now(),
                )
            )
            pruneLocalMessages()
            persistConversation()
        }
    }

    private fun respondTo(text: String, attachments: List<FileTransfer>) {
        val targetId = selectedTargetId
        val conversationId = conversationIdFor(targetId)
        val targetTeam = teams.firstOrNull { it.id == targetId }
        if (targetTeam != null) {
            respondToTeam(targetTeam, text, attachments)
            return
        }
        val agent = agents.firstOrNull { it.id == targetId } ?: selectedAgent()
        val transferTool = attachments.map {
            ToolCall(
                id = nextId(),
                agentId = agent.id,
                toolName = "file.transfer",
                status = ToolStatus.SUCCESS,
                input = "${it.direction}: ${it.name}",
                output = "queued for encrypted bridge upload",
                startedAt = now(),
            )
        }
        val commandTool = if (text.startsWith("/")) {
            listOf(
                ToolCall(
                    id = nextId(),
                    agentId = agent.id,
                    toolName = "slash.command",
                    status = ToolStatus.SUCCESS,
                    input = text,
                    output = "resolved against unified command registry",
                    startedAt = now(),
                )
            )
        } else {
            emptyList()
        }
        messages.add(
            ChatMessage(
                id = nextId(),
                authorId = agent.id,
                targetAgentId = "you",
                kind = MessageKind.AGENT,
                text = responseText(agent, text, attachments),
                createdAt = now(),
                conversationId = conversationId,
                toolCalls = commandTool + transferTool + listOf(
                    ToolCall(
                        id = nextId(),
                        agentId = agent.id,
                        toolName = "agent.output",
                        status = ToolStatus.SUCCESS,
                        input = "stream text",
                        output = "visible in conversation timeline",
                        startedAt = now(),
                    )
                ),
            )
        )
        heartbeats.add(0, HeartbeatEntry(nextId(), agent.name, "Handled phone message.", now()))
        pruneLocalMessages()
        pruneHeartbeats()
        persistConversation()
    }

    private fun responseText(
        agent: AgentNode,
        text: String,
        attachments: List<FileTransfer>,
    ): String = when {
        text.startsWith("/status") -> "Team online: ${agents.count { it.status == AgentStatus.ONLINE }} online, ${agents.count { it.kind == AgentKind.SUBAGENT }} subagents visible."
        text.startsWith("/agents") -> agents.joinToString("\n") { "${it.name}: ${it.status} / parent=${it.parentId ?: "none"}" }
        text.startsWith("/team") -> "${team.value.name}: admin=${agentName(team.value.adminAgentId)}, members=${team.value.memberIds.size}."
        text.startsWith("/tools") -> "${agent.name} tools: ${agent.tools.joinToString(", ")}"
        text.startsWith("/handoff") -> "Handoff recorded for ${agent.name}; next owner stays visible in the shared queue."
        text.startsWith("/approve") -> "Approved ${agent.name} to continue current task."
        text.startsWith("/pause") -> "Pause requested for ${agent.name}."
        text.startsWith("/resume") -> "Resume requested for ${agent.name}."
        text.startsWith("/stop") -> "Stop requested for ${agent.name}."
        text.startsWith("/files") -> "File bridge ready for upload and download events."
        text.startsWith("/photo") -> "Photo bridge ready; use the photo button to attach an image."
        text.startsWith("/help") -> commands.joinToString(" ") { it.trigger }
        attachments.isNotEmpty() -> "${agent.name} received ${attachments.size} encrypted transfer item(s)."
        text.isBlank() -> "${agent.name} is listening."
        else -> "${agent.name} will route this through the desktop bridge: $text"
    }

    private fun respondToTeam(team: AgentTeam, text: String, attachments: List<FileTransfer>) {
        val conversationId = conversationIdFor(team.id)
        val speakers = team.memberIds.mapNotNull { memberId -> agents.firstOrNull { it.id == memberId } }.take(3)
        val summary = when {
            text.startsWith("/team") -> "${team.name}: ${team.memberIds.size} members, shared profile: ${team.sharedProfile}"
            attachments.isNotEmpty() -> "${team.name} received ${attachments.size} shared item(s)."
            text.isBlank() -> "${team.name} is listening."
            else -> "Local fallback: ${speakers.joinToString(", ") { it.name }} saw this group message. Pair the bridge for real agent discussion."
        }
        messages.add(
            ChatMessage(
                id = nextId(),
                authorId = speakers.firstOrNull()?.id ?: team.adminAgentId,
                targetAgentId = team.id,
                kind = MessageKind.AGENT,
                text = summary,
                createdAt = now(),
                conversationId = conversationId,
                toolCalls = listOf(
                    ToolCall(
                        id = nextId(),
                        agentId = team.adminAgentId,
                        toolName = "team.group",
                        status = ToolStatus.SUCCESS,
                        input = text.ifBlank { "(empty)" },
                        output = "local group fallback",
                        startedAt = now(),
                    )
                ),
            )
        )
        heartbeats.add(0, HeartbeatEntry(nextId(), team.name, "Handled local team message.", now()))
        pruneLocalMessages()
        pruneHeartbeats()
        persistConversation()
    }

    private fun spawnSubagent() {
        val parent = selectedAgent()
        val child = AgentNode(
            id = "sub-${UUID.randomUUID().toString().take(8)}",
            name = "${parent.name} Child",
            kind = AgentKind.SUBAGENT,
            role = "self-created helper under ${parent.name}",
            status = AgentStatus.ONLINE,
            parentId = parent.id,
            tools = listOf("direct-chat", "report", "handoff"),
        )
        agents.add(child)
        team.value = team.value.copy(memberIds = team.value.memberIds + child.id)
        teams.replaceAllById(team.value)
        selectedAgentId = child.id
        messages.add(
            ChatMessage(
                id = nextId(),
                authorId = "system",
                kind = MessageKind.SYSTEM,
                text = "Subagent created: ${child.name}. It is now directly chat-addressable.",
                createdAt = now(),
            )
        )
        pruneLocalMessages()
        persistConversation()
    }

    private fun restorePersistedConversation() {
        val restored = conversationPersistence?.load() ?: return
        if (restored.agents.isNotEmpty()) {
            agents.clear()
            agents.addAll(restored.agents)
        }
        if (restored.teams.isNotEmpty()) {
            teams.clear()
            teams.addAll(restored.teams)
            team.value = teams.firstOrNull { it.id == "core" } ?: teams.first()
        }
        if (restored.commands.isNotEmpty()) {
            commands.clear()
            commands.addAll(restored.commands)
        }
        if (restored.messages.isNotEmpty()) {
            messages.clear()
            messages.addAll(restored.messages.takeLast(MAX_LOCAL_MESSAGES))
        }
        activeConversationIds.clear()
        activeConversationIds.putAll(restored.activeConversationIds)
        agentPermissionModes.clear()
        agentPermissionModes.putAll(restored.agentPermissionModes.mapValues { normalizePermissionMode(it.value) })
        if (restored.documents.isNotEmpty()) {
            documents.clear()
            documents.addAll(restored.documents)
        }
        if (restored.heartbeats.isNotEmpty()) {
            heartbeats.clear()
            heartbeats.addAll(restored.heartbeats.take(MAX_HEARTBEATS))
        }
        codexRuntimeSettings = restored.codexRuntimeSettings
        normalizeSelections()
    }

    private fun persistConversation() {
        conversationPersistence?.save(
            PersistedConversationState(
                agents = agents.toList(),
                teams = teams.toList(),
                commands = commands.toList(),
                messages = messages.takeLast(MAX_LOCAL_MESSAGES),
                activeConversationIds = activeConversationIds.toMap(),
                agentPermissionModes = agentPermissionModes.toMap(),
                documents = documents.toList(),
                heartbeats = heartbeats.take(MAX_HEARTBEATS),
                codexRuntimeSettings = codexRuntimeSettings,
                updatedAt = now(),
            )
        )
    }

    private fun mergeMessages(remoteMessages: List<ChatMessage>) {
        val mergedById = linkedMapOf<String, ChatMessage>()
        (messages + remoteMessages).forEach { message ->
            mergedById[message.id] = message
        }
        messages.clear()
        messages.addAll(
            mergedById.values
                .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                .takeLast(MAX_LOCAL_MESSAGES)
        )
    }

    private fun conversationContextFor(current: ChatMessage): List<ChatMessage> {
        val targetId = current.targetAgentId ?: selectedTargetId
        val conversationId = current.conversationId.ifBlank { conversationIdFor(targetId) }
        return messages
            .asSequence()
            .filter { it.id != current.id }
            .filter { it.belongsToConversationThread(targetId, conversationId) }
            .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
            .toList()
            .takeLast(MAX_CONTEXT_MESSAGES)
    }

    fun messageBelongsToConversation(
        message: ChatMessage,
        targetId: String,
        conversationId: String = conversationIdFor(targetId),
    ): Boolean = message.belongsToConversationThread(targetId, conversationId)

    private fun ChatMessage.belongsToTarget(targetId: String): Boolean {
        if (teams.any { it.id == targetId }) return targetAgentId == targetId || authorId == targetId
        return targetAgentId == targetId || authorId == targetId
    }

    private fun ChatMessage.belongsToConversationThread(targetId: String, conversationId: String): Boolean =
        belongsToTarget(targetId) &&
            effectiveConversationId(this, targetId) == conversationId.ifBlank { defaultConversationId(targetId) }

    private fun pruneLocalMessages() {
        if (messages.size > MAX_LOCAL_MESSAGES) {
            messages.removeRange(0, messages.size - MAX_LOCAL_MESSAGES)
        }
    }

    private fun pruneHeartbeats() {
        if (heartbeats.size > MAX_HEARTBEATS) {
            heartbeats.removeRange(MAX_HEARTBEATS, heartbeats.size)
        }
    }

    private fun normalizeSelections() {
        if (agents.none { it.id == selectedAgentId }) {
            selectedAgentId = agents.firstOrNull()?.id ?: "codex"
        }
        if (agents.none { it.id == selectedTargetId } && teams.none { it.id == selectedTargetId }) {
            selectedTargetId = selectedAgentId
        }
        if (documents.none { it.id == selectedDocumentId }) {
            selectedDocumentId = documents.firstOrNull()?.id ?: "memory"
        }
    }

    private fun MutableList<AgentTeam>.replaceAllById(team: AgentTeam) {
        val index = indexOfFirst { it.id == team.id }
        if (index >= 0) {
            this[index] = team
        } else {
            add(team)
        }
    }

    private fun agentName(id: String): String = agents.firstOrNull { it.id == id }?.name ?: id

    private fun nextId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()

    private fun defaultConversationId(targetId: String): String = "default:$targetId"

    private fun effectiveConversationId(message: ChatMessage, targetId: String): String =
        message.conversationId.ifBlank { defaultConversationId(targetId) }

    private fun normalizePermissionMode(value: String): String =
        when (value) {
            "read-only", "workspace-write", "full-access" -> value
            else -> "read-only"
        }
}

private fun isLocalBridgeUrl(value: String): Boolean {
    val normalized = value.trim().lowercase()
    if (!normalized.startsWith("http://")) return false
    val host = normalized
        .removePrefix("http://")
        .substringBefore('/')
        .substringBefore(':')
    if (host == "localhost" || host.endsWith(".local")) return true
    if (host == "10.0.2.2" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) return true
    val parts = host.split('.')
    if (parts.size >= 2 && parts[0] == "172") {
        val second = parts[1].toIntOrNull()
        if (second != null && second in 16..31) return true
    }
    return host.startsWith("198.18.") || host.startsWith("198.19.")
}
