package com.xiehaibo.agentcontrol.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xiehaibo.agentcontrol.api.BridgeApiContract
import com.xiehaibo.agentcontrol.api.BridgeDiagnostics
import com.xiehaibo.agentcontrol.api.BridgeHealth
import com.xiehaibo.agentcontrol.api.BridgeSnapshot
import com.xiehaibo.agentcontrol.api.CodexRuntimeSettings
import com.xiehaibo.agentcontrol.api.ConnectionDiagnostics
import com.xiehaibo.agentcontrol.api.DiagnosticCheck
import com.xiehaibo.agentcontrol.api.OutboundMessagePayload
import com.xiehaibo.agentcontrol.api.PairingChallenge
import com.xiehaibo.agentcontrol.api.PairResponse
import com.xiehaibo.agentcontrol.api.RuntimeOption
import com.xiehaibo.agentcontrol.model.AgentCapabilityOption
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
        const val CHECK_PASS = "pass"
        const val CHECK_WARN = "warn"
        const val CHECK_FAIL = "fail"
        const val CHECK_PENDING = "pending"
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
    var latestBridgeHealth by mutableStateOf<BridgeHealth?>(null)
    var latestBridgeDiagnostics by mutableStateOf<BridgeDiagnostics?>(null)
    var diagnosticsRunning by mutableStateOf(false)
    var lastDiagnosticsError by mutableStateOf<String?>(null)
    var lastSendFailure by mutableStateOf<String?>(null)
    var latestSnapshotVerifiedAt by mutableStateOf<Long?>(null)
    var latestAttachmentQueuedAt by mutableStateOf<Long?>(null)
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
    val transfers = mutableStateListOf<FileTransfer>()
    val agents = mutableStateListOf(
        AgentNode(
            id = "codex",
            name = "Codex",
            kind = AgentKind.CODEX,
            role = "controller, planner, integrator",
            status = AgentStatus.ONLINE,
            tools = listOf("shell", "patch", "browser", "notion", "android-build"),
            slashCommands = listOf(
                SlashCommand("/plan", "Plan", "selected"),
                SlashCommand("/diff", "Diff", "selected"),
                SlashCommand("/review", "Review", "selected"),
                SlashCommand("/test", "Test", "selected"),
                SlashCommand("/commit", "Commit", "selected"),
            ),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "claude",
            name = "Claude Code",
            kind = AgentKind.CLAUDE_CODE,
            role = "deep implementation and repo surgery",
            status = AgentStatus.IDLE,
            tools = listOf("repo-read", "edit", "test", "review"),
            slashCommands = listOf(
                SlashCommand("/plan", "Plan", "selected"),
                SlashCommand("/edit", "Edit", "selected"),
                SlashCommand("/diff", "Diff", "selected"),
                SlashCommand("/review", "Review", "selected"),
                SlashCommand("/login", "Login status", "selected"),
            ),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "antigravity",
            name = "Antigravity",
            kind = AgentKind.ANTIGRAVITY,
            role = "independent UI/product review",
            status = AgentStatus.IDLE,
            tools = listOf("browser", "manual-check", "visual-review"),
            slashCommands = listOf(
                SlashCommand("/review", "Review", "selected"),
                SlashCommand("/screenshot", "Screenshot", "selected"),
                SlashCommand("/issues", "Issues", "selected"),
            ),
            canSpawnChildren = true,
        ),
        AgentNode(
            id = "gemini_cli",
            name = "Gemini CLI",
            kind = AgentKind.GEMINI_CLI,
            role = "official Gemini API-key agent lane",
            status = AgentStatus.ONLINE,
            tools = listOf("gemini", "planning", "analysis", "review"),
            slashCommands = listOf(
                SlashCommand("/ask", "Ask", "selected"),
                SlashCommand("/plan", "Plan", "selected"),
                SlashCommand("/research", "Research", "selected"),
                SlashCommand("/review", "Review", "selected"),
            ),
            canSpawnChildren = false,
        ),
        AgentNode(
            id = "opencode",
            name = "OpenCode",
            kind = AgentKind.OPENCODE,
            role = "OpenCode CLI using DeepSeek V4-Pro",
            status = AgentStatus.ONLINE,
            tools = listOf("opencode", "deepseek-v4-pro", "coding", "review"),
            slashCommands = listOf(
                SlashCommand("/run", "Run", "selected"),
                SlashCommand("/edit", "Edit", "selected"),
                SlashCommand("/test", "Test", "selected"),
                SlashCommand("/review", "Review", "selected"),
            ),
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
            slashCommands = listOf(
                SlashCommand("/audit", "Audit", "selected"),
                SlashCommand("/threat-model", "Threat model", "selected"),
            ),
        ),
        AgentNode(
            id = "sub-files",
            name = "Transfer Subagent",
            kind = AgentKind.SUBAGENT,
            role = "photo and file shuttle",
            status = AgentStatus.BUSY,
            parentId = "claude",
            tools = listOf("upload", "download", "checksum"),
            slashCommands = listOf(
                SlashCommand("/files", "Files", "transfer"),
                SlashCommand("/checksum", "Checksum", "selected"),
            ),
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
        SlashCommand("/team-create", "New team", "team"),
        SlashCommand("/new", "New chat", "chat"),
        SlashCommand("/model", "Model", "selected"),
        SlashCommand("/reasoning", "Reasoning", "selected"),
        SlashCommand("/permissions", "Permissions", "selected"),
        SlashCommand("/context", "Context", "selected"),
        SlashCommand("/compact", "Compact", "selected"),
        SlashCommand("/diagnostics", "Diagnostics", "team"),
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
    val seenConversationTimestamps = mutableStateMapOf<String, Long>()
    val agentPermissionModes = mutableStateMapOf<String, String>()
    val agentRuntimeSettings = mutableStateMapOf<String, CodexRuntimeSettings>()

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
                ${BridgeApiContract.HEALTH_ENDPOINT}
                ${BridgeApiContract.PAIRING_CHALLENGE_ENDPOINT}
                ${BridgeApiContract.PAIR_ENDPOINT}
                ${BridgeApiContract.STREAM_ENDPOINT}
                ${BridgeApiContract.MESSAGE_ENDPOINT}
                ${BridgeApiContract.FILE_ENDPOINT}
                ${BridgeApiContract.PROJECT_ENDPOINT}
                ${BridgeApiContract.COMMAND_ENDPOINT}
                ${BridgeApiContract.DIAGNOSTICS_ENDPOINT}

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
        addSystemMessage("Switched this paired desktop to the managed relay so LAN IP changes do not break control.")
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
        latestSnapshotVerifiedAt = now()
        pairingInfo = if (pairingInfo.paired) {
            pairingInfo.copy(lastVerifiedAt = latestSnapshotVerifiedAt, lastPairingError = null)
        } else {
            pairingInfo
        }
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
        transfers.clear()
        transfers.addAll(snapshot.transfers)
        documents.clear()
        documents.addAll(snapshot.documents)
        heartbeats.clear()
        heartbeats.addAll(snapshot.heartbeats)
        codexRuntimeSettings = mergeCodexRuntimeSettings(snapshot.runtimeSettings.codex)
        mergeAgentRuntimeSettings(snapshot.runtimeSettings.agents)
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
        latestAttachmentQueuedAt = now()
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

    fun removePendingAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
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

    fun markConversationSeen(targetId: String) {
        seenConversationTimestamps[seenKey(targetId)] = now()
        persistConversation()
    }

    fun unreadCompletedCount(targetId: String): Int {
        val seenAt = seenConversationTimestamps[seenKey(targetId)] ?: 0L
        return messagesForActiveConversation(targetId).count { message ->
            message.kind == MessageKind.AGENT &&
                message.createdAt > seenAt &&
                message.toolCalls.none { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED }
        }
    }

    fun targetIsTyping(targetId: String): Boolean =
        messagesForActiveConversation(targetId).any { message ->
            message.kind == MessageKind.AGENT &&
                message.toolCalls.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED }
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
                runtimeOptions = runtimeSettingsForTarget(targetId),
                conversationContext = conversationContextFor(message),
            )
        }

    fun updateCodexModel(model: String) {
        updateModelForTarget("codex", model)
    }

    fun updateCodexReasoning(reasoningEffort: String) {
        updateReasoningForTarget("codex", reasoningEffort)
    }

    fun updateCodexPermission(permissionMode: String) {
        updatePermissionForTarget("codex", permissionMode)
        codexRuntimeSettings = codexRuntimeSettings.copy(permissionMode = normalizePermissionMode(permissionMode))
        persistConversation()
    }

    fun permissionForTarget(targetId: String): String {
        val requested = agentPermissionModes[targetId] ?: if (targetId == "codex") codexRuntimeSettings.permissionMode else "read-only"
        val options = runtimeSettingsForTarget(targetId).permissionOptions
        return requested.takeIf { value -> options.any { it.id == value } }
            ?: normalizePermissionMode(requested)
    }

    fun updatePermissionForTarget(targetId: String = selectedTargetId, permissionMode: String) {
        val options = runtimeSettingsForTarget(targetId).permissionOptions
        val normalized = permissionMode.takeIf { value -> options.any { it.id == value } }
            ?: normalizePermissionMode(permissionMode)
        agentPermissionModes[targetId] = normalized
        if (targetId == "codex") {
            codexRuntimeSettings = codexRuntimeSettings.copy(permissionMode = normalized)
        }
        persistConversation()
    }

    fun runtimeSettingsForTarget(targetId: String = selectedTargetId): CodexRuntimeSettings {
        agentRuntimeSettings[targetId]?.let { return it }
        val runtimeKey = runtimeKeyForTarget(targetId)
        return if (runtimeKey == "codex") {
            codexRuntimeSettings
        } else {
            agentRuntimeSettings[runtimeKey]
                ?: defaultRuntimeSettingsForAgent(agentForTarget(targetId) ?: rootAgentForTarget(targetId))
        }
    }

    fun updateModelForTarget(targetId: String = selectedTargetId, model: String) {
        val runtimeKey = runtimeUpdateKeyForTarget(targetId)
        if (runtimeKey == "codex") {
            codexRuntimeSettings = codexRuntimeSettings.copy(model = model)
        } else {
            val current = runtimeSettingsForTarget(runtimeKey)
            agentRuntimeSettings[runtimeKey] = current.copy(model = model)
        }
        persistConversation()
    }

    fun updateReasoningForTarget(targetId: String = selectedTargetId, reasoningEffort: String) {
        val runtimeKey = runtimeUpdateKeyForTarget(targetId)
        if (runtimeKey == "codex") {
            codexRuntimeSettings = codexRuntimeSettings.copy(reasoningEffort = reasoningEffort)
        } else {
            val current = runtimeSettingsForTarget(runtimeKey)
            agentRuntimeSettings[runtimeKey] = current.copy(reasoningEffort = reasoningEffort)
        }
        persistConversation()
    }

    private fun runtimeUpdateKeyForTarget(targetId: String): String =
        when {
            agentRuntimeSettings.containsKey(targetId) -> targetId
            declaredRuntimeSettings(agentForTarget(targetId)) != null -> targetId
            else -> runtimeKeyForTarget(targetId)
        }

    private fun runtimeKeyForTarget(targetId: String): String {
        return rootAgentId(rootAgentForTarget(targetId))?.ifBlank { "codex" } ?: "codex"
    }

    private fun agentForTarget(targetId: String): AgentNode? {
        val teamTarget = teams.firstOrNull { it.id == targetId }
        return if (teamTarget != null) {
            agents.firstOrNull { it.id == teamTarget.adminAgentId }
        } else {
            agents.firstOrNull { it.id == targetId }
        }
    }

    private fun rootAgentForTarget(targetId: String): AgentNode? {
        val agent = agentForTarget(targetId) ?: agents.firstOrNull { it.id == selectedAgentId }
        return rootAgent(agent)
    }

    private fun rootAgentId(agent: AgentNode?): String? {
        return rootAgent(agent)?.id
    }

    private fun rootAgent(agent: AgentNode?): AgentNode? {
        var current = agent ?: return null
        val seen = mutableSetOf<String>()
        while (current.kind == AgentKind.SUBAGENT && current.parentId != null && seen.add(current.id)) {
            current = agents.firstOrNull { it.id == current.parentId } ?: break
        }
        return current
    }

    private fun defaultRuntimeSettingsForAgent(agent: AgentNode?): CodexRuntimeSettings {
        declaredRuntimeSettings(agent)?.let { return it }
        return when (agent?.kind) {
        AgentKind.CLAUDE_CODE -> CodexRuntimeSettings(
            model = "claude-sonnet-4-6",
            reasoningEffort = "medium",
            contextLimitTokens = 200_000,
            modelOptions = listOf(
                RuntimeOption("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                RuntimeOption("claude-opus-4-6", "Claude Opus 4.6"),
                RuntimeOption("sonnet", "sonnet"),
                RuntimeOption("opus", "opus"),
            ),
            reasoningOptions = listOf(
                RuntimeOption("low", "Low"),
                RuntimeOption("medium", "Medium"),
                RuntimeOption("high", "High"),
                RuntimeOption("xhigh", "Extra High"),
                RuntimeOption("max", "Max"),
            ),
            permissionOptions = defaultPermissionOptions(),
        )
        AgentKind.GEMINI_CLI -> CodexRuntimeSettings(
            model = "gemini-2.5-flash",
            reasoningEffort = "default",
            contextLimitTokens = 1_048_576,
            modelOptions = listOf(
                RuntimeOption("gemini-2.5-flash", "Gemini 2.5 Flash"),
                RuntimeOption("gemini-2.5-pro", "Gemini 2.5 Pro"),
                RuntimeOption("gemini-3-flash-preview", "Gemini 3 Flash"),
                RuntimeOption("gemini-3.1-pro-preview", "Gemini 3.1 Pro"),
            ),
            reasoningOptions = listOf(RuntimeOption("default", "Default")),
            permissionOptions = defaultPermissionOptions(),
        )
        AgentKind.ANTIGRAVITY -> CodexRuntimeSettings(
            model = "openrouter/deepseek/deepseek-v3.2",
            reasoningEffort = "off",
            contextLimitTokens = 160_000,
            modelOptions = listOf(
                RuntimeOption("openrouter/deepseek/deepseek-v3.2", "DeepSeek V3.2"),
                RuntimeOption("openrouter/google/gemini-3-flash-preview", "Gemini 3 Flash"),
                RuntimeOption("openrouter/anthropic/claude-opus-4.6", "Claude Opus 4.6"),
                RuntimeOption("openrouter/google/gemini-3.1-pro-preview", "Gemini 3.1 Pro"),
                RuntimeOption("openai/gpt-5.4", "GPT 5.4"),
                RuntimeOption("openai-codex/gpt-5.4", "Codex GPT 5.4"),
            ),
            reasoningOptions = listOf(
                RuntimeOption("off", "Off"),
                RuntimeOption("minimal", "Minimal"),
                RuntimeOption("low", "Low"),
                RuntimeOption("medium", "Medium"),
                RuntimeOption("high", "High"),
                RuntimeOption("xhigh", "Extra High"),
            ),
            permissionOptions = defaultPermissionOptions(),
        )
        AgentKind.OPENCODE -> CodexRuntimeSettings(
            model = "deepseek/deepseek-v4-pro",
            reasoningEffort = "default",
            contextLimitTokens = 128_000,
            modelOptions = listOf(
                RuntimeOption("deepseek/deepseek-v4-pro", "DeepSeek V4-Pro"),
                RuntimeOption("openrouter/deepseek/deepseek-v3.2", "DeepSeek V3.2"),
                RuntimeOption("openrouter/google/gemini-3-flash-preview", "Gemini 3 Flash"),
                RuntimeOption("openrouter/anthropic/claude-opus-4.6", "Claude Opus 4.6"),
            ),
            reasoningOptions = listOf(
                RuntimeOption("default", "Default"),
                RuntimeOption("minimal", "Minimal"),
                RuntimeOption("low", "Low"),
                RuntimeOption("medium", "Medium"),
                RuntimeOption("high", "High"),
                RuntimeOption("max", "Max"),
            ),
            permissionOptions = defaultPermissionOptions(),
        )
        else -> CodexRuntimeSettings(
            model = "gpt-5.5",
            modelOptions = listOf(
                RuntimeOption("gpt-5.5", "5.5"),
                RuntimeOption("gpt-5.4", "5.4"),
                RuntimeOption("gpt-5.3-codex", "5.3 Codex"),
                RuntimeOption("gpt-5.2", "5.2"),
            ),
            reasoningOptions = listOf(
                RuntimeOption("low", "Low"),
                RuntimeOption("medium", "Medium"),
                RuntimeOption("high", "High"),
                RuntimeOption("xhigh", "Extra High"),
            ),
            permissionOptions = defaultPermissionOptions(),
        )
        }
    }

    private fun declaredRuntimeSettings(agent: AgentNode?): CodexRuntimeSettings? {
        val modelOptions = agent?.modelOptions.toRuntimeOptions()
        val reasoningOptions = agent?.reasoningOptions.toRuntimeOptions()
        val permissionOptions = agent?.permissionOptions.toRuntimeOptions()
        if (modelOptions.isEmpty() && reasoningOptions.isEmpty() && permissionOptions.isEmpty()) return null
        val finalModels = modelOptions.ifEmpty {
            listOf(RuntimeOption(agent?.id ?: "agent", agent?.name ?: "Agent"))
        }
        val finalReasoning = reasoningOptions.ifEmpty { listOf(RuntimeOption("default", "Default")) }
        val finalPermissions = permissionOptions.ifEmpty { defaultPermissionOptions() }
        return CodexRuntimeSettings(
            model = finalModels.first().id,
            reasoningEffort = finalReasoning.first().id,
            permissionMode = finalPermissions.first().id,
            contextLimitTokens = 128_000,
            modelOptions = finalModels,
            reasoningOptions = finalReasoning,
            permissionOptions = finalPermissions,
        )
    }

    private fun List<AgentCapabilityOption>?.toRuntimeOptions(): List<RuntimeOption> =
        this.orEmpty()
            .mapNotNull { option ->
                val id = option.id.trim()
                if (id.isBlank()) null else RuntimeOption(id, option.label.ifBlank { id })
            }
            .distinctBy { it.id }

    private fun defaultPermissionOptions(): List<RuntimeOption> = listOf(
        RuntimeOption("read-only", "Read Only"),
        RuntimeOption("workspace-write", "Workspace Write"),
        RuntimeOption("full-access", "Full Access"),
    )

    private fun mergeCodexRuntimeSettings(remote: CodexRuntimeSettings): CodexRuntimeSettings {
        val current = codexRuntimeSettings
        return remote.copy(
            model = current.model.takeIf { optionExists(remote.modelOptions, it) } ?: remote.model,
            reasoningEffort = current.reasoningEffort.takeIf { optionExists(remote.reasoningOptions, it) } ?: remote.reasoningEffort,
            permissionMode = current.permissionMode.takeIf { optionExists(remote.permissionOptions, it) } ?: remote.permissionMode,
        )
    }

    private fun mergeAgentRuntimeSettings(remote: Map<String, CodexRuntimeSettings>) {
        val merged = remote.mapValues { (agentId, settings) ->
            val current = if (agentId == "codex") codexRuntimeSettings else agentRuntimeSettings[agentId]
            if (current == null) {
                settings
            } else {
                settings.copy(
                    model = current.model.takeIf { optionExists(settings.modelOptions, it) } ?: settings.model,
                    reasoningEffort = current.reasoningEffort.takeIf { optionExists(settings.reasoningOptions, it) } ?: settings.reasoningEffort,
                    permissionMode = current.permissionMode.takeIf { optionExists(settings.permissionOptions, it) } ?: settings.permissionMode,
                )
            }
        }
        agentRuntimeSettings.clear()
        agentRuntimeSettings.putAll(merged)
        agentRuntimeSettings["codex"] = codexRuntimeSettings
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
        val existing = messages.firstOrNull { it.id == normalized.id }
        val existingRunning = existing?.toolCalls?.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED } == true
        val nextRunning = normalized.toolCalls.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED }
        val timestamped = if (existingRunning && !nextRunning && normalized.kind == MessageKind.AGENT) {
            normalized.copy(createdAt = completionTimestampFor(targetId))
        } else {
            normalized
        }
        messages.removeAll { it.id == timestamped.id }
        messages.add(timestamped)
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

    fun beginDiagnostics() {
        diagnosticsRunning = true
        lastDiagnosticsError = null
    }

    fun recordDiagnosticsHealth(health: BridgeHealth) {
        latestBridgeHealth = health
        lastDiagnosticsError = null
    }

    fun recordDiagnosticsSuccess(
        health: BridgeHealth?,
        diagnostics: BridgeDiagnostics?,
    ) {
        health?.let { latestBridgeHealth = it }
        diagnostics?.let {
            latestBridgeDiagnostics = it
            latestSnapshotVerifiedAt = it.generatedAt.takeIf { generatedAt -> generatedAt > 0L } ?: now()
            if (pairingInfo.paired) {
                pairingInfo = pairingInfo.copy(lastVerifiedAt = now(), lastPairingError = null)
                pairingPersistence?.save(pairingInfo, deviceId, sessionKey)
            }
        }
        diagnosticsRunning = false
        lastDiagnosticsError = null
        heartbeats.add(0, HeartbeatEntry(nextId(), "diagnostics", "Connection diagnostics completed.", now()))
        pruneHeartbeats()
        persistConversation()
    }

    fun recordDiagnosticsFailure(message: String) {
        diagnosticsRunning = false
        lastDiagnosticsError = message
        heartbeats.add(0, HeartbeatEntry(nextId(), "diagnostics", "Diagnostics failed: ${redactSecrets(message)}", now()))
        pruneHeartbeats()
        persistConversation()
    }

    fun rememberSendFailure(message: String) {
        lastSendFailure = redactSecrets(message)
    }

    fun clearSendFailure() {
        lastSendFailure = null
    }

    fun prepareStatusDraft(targetId: String = selectedTargetId) {
        selectedTargetId = targetId
        if (agents.any { it.id == targetId }) selectedAgentId = targetId
        ensureConversationFor(targetId)
        draftText = "/status"
    }

    fun connectionDiagnostics(): ConnectionDiagnostics {
        val checks = diagnosticChecks()
        val summary = when {
            checks.any { it.status == CHECK_FAIL } -> "Needs attention"
            checks.any { it.status == CHECK_WARN } -> "Partially ready"
            checks.all { it.status == CHECK_PASS } -> "Ready for testing"
            else -> "Not tested yet"
        }
        return ConnectionDiagnostics(
            generatedAt = now(),
            summary = summary,
            checks = checks,
            health = latestBridgeHealth,
            bridge = latestBridgeDiagnostics,
        )
    }

    fun testerReport(
        packageName: String,
        versionName: String,
        versionCode: Int,
    ): String {
        val diagnostics = connectionDiagnostics()
        val mode = latestBridgeDiagnostics?.connectionMode?.ifBlank { null }
            ?: latestBridgeHealth?.mode?.ifBlank { null }
            ?: if (pairingInfo.desktopUrl.startsWith("https://", ignoreCase = true)) "relay/https" else "direct/vpn"
        val agentSummary = agents.take(10).joinToString(", ") { "${it.name}:${it.status.name.lowercase()}" }
        val checks = diagnostics.checks.joinToString("\n") {
            "- ${it.label}: ${it.status.uppercase()}${it.detail.takeIf { detail -> detail.isNotBlank() }?.let { detail -> " - ${redactSecrets(detail)}" }.orEmpty()}"
        }
        return """
            Agent Control tester report
            Package: $packageName
            Version: $versionName ($versionCode)
            Generated: ${now()}
            Summary: ${diagnostics.summary}
            Desktop: ${pairingInfo.desktopName ?: latestBridgeDiagnostics?.desktopName ?: latestBridgeHealth?.desktopName ?: "unknown"}
            Address: ${safeUrlLabel(pairingInfo.desktopUrl.ifBlank { desktopUrlDraft })}
            Connection mode: $mode
            Paired: ${pairingInfo.paired}
            Device: ${deviceId.redactedId()}
            Agents: $agentSummary

            Checks:
            $checks

            Last diagnostics error: ${lastDiagnosticsError?.let(::redactSecrets) ?: "none"}
            Last send failure: ${lastSendFailure?.let(::redactSecrets) ?: "none"}
        """.trimIndent()
    }

    private fun diagnosticChecks(): List<DiagnosticCheck> {
        val health = latestBridgeHealth
        val diagnostics = latestBridgeDiagnostics
        val hasUrl = pairingInfo.desktopUrl.isNotBlank() || desktopUrlDraft.isNotBlank()
        val encryptedOk = diagnostics?.sessionActive == true || latestSnapshotVerifiedAt != null
        val statusCommandSent = messages.any { it.kind == MessageKind.USER && it.text.trim() == "/status" }
        val statusReplyReceived = statusCommandSent && messages.any { message ->
            message.kind == MessageKind.AGENT &&
                (message.text.contains("codex", ignoreCase = true) ||
                    message.text.contains("agent", ignoreCase = true) ||
                    message.text.contains("online", ignoreCase = true))
        }
        val attachmentTested = latestAttachmentQueuedAt != null ||
            messages.any { it.attachments.isNotEmpty() } ||
            transfers.any { it.direction == TransferDirection.PHONE_TO_DESKTOP }
        val reconnectLikely = pairingInfo.paired && pairingInfo.lastVerifiedAt != null
        val runtimeAvailable = diagnostics?.agents?.any { agent ->
            agent.modelOptions.isNotEmpty() && agent.permissionOptions.isNotEmpty()
        } ?: agents.any { runtimeSettingsForTarget(it.id).modelOptions.isNotEmpty() && runtimeSettingsForTarget(it.id).permissionOptions.isNotEmpty() }
        val deviceMatches = diagnostics?.pairedDeviceId.orEmpty().let { remoteDeviceId ->
            remoteDeviceId.isBlank() || deviceId.isBlank() || remoteDeviceId == deviceId
        }
        return listOf(
            DiagnosticCheck(
                id = "url_reachable",
                label = "URL reachable",
                status = when {
                    health?.ok == true -> CHECK_PASS
                    lastDiagnosticsError != null && hasUrl -> CHECK_FAIL
                    hasUrl -> CHECK_PENDING
                    else -> CHECK_WARN
                },
                detail = when {
                    health?.service == "agent-control-relay" -> "Relay reachable"
                    health?.desktopName?.isNotBlank() == true -> "Bridge ${health.desktopName} reachable"
                    hasUrl -> safeUrlLabel(pairingInfo.desktopUrl.ifBlank { desktopUrlDraft })
                    else -> "No desktop or relay address yet"
                },
            ),
            DiagnosticCheck(
                id = "bridge_health",
                label = "Bridge health",
                status = when {
                    diagnostics?.bridgeVersion?.isNotBlank() == true -> CHECK_PASS
                    health?.version == BridgeApiContract.VERSION -> CHECK_PASS
                    health?.service == "agent-control-relay" -> CHECK_WARN
                    else -> CHECK_PENDING
                },
                detail = diagnostics?.bridgeVersion ?: health?.version ?: health?.service ?: "Run diagnostics",
            ),
            DiagnosticCheck(
                id = "pairing_state",
                label = "Pairing state",
                status = if (pairingInfo.paired) CHECK_PASS else CHECK_WARN,
                detail = pairingInfo.desktopName ?: pairingInfo.desktopFingerprint ?: "Phone is not paired",
            ),
            DiagnosticCheck(
                id = "encrypted_session",
                label = "Encrypted snapshot/session",
                status = if (encryptedOk) CHECK_PASS else if (pairingInfo.paired) CHECK_WARN else CHECK_PENDING,
                detail = latestSnapshotVerifiedAt?.let { "Verified ${formatRelativeAge(it)}" } ?: "No encrypted check yet",
            ),
            DiagnosticCheck(
                id = "connection_mode",
                label = "Relay/direct mode",
                status = CHECK_PASS,
                detail = diagnostics?.connectionMode?.ifBlank { null }
                    ?: health?.mode?.ifBlank { null }
                    ?: if (pairingInfo.desktopUrl.startsWith("https://", ignoreCase = true)) "relay/https" else "direct/vpn",
            ),
            DiagnosticCheck(
                id = "paired_device_id",
                label = "Paired device id",
                status = when {
                    deviceId.isBlank() -> CHECK_WARN
                    !deviceMatches -> CHECK_FAIL
                    else -> CHECK_PASS
                },
                detail = deviceId.redactedId(),
            ),
            DiagnosticCheck(
                id = "agent_roster",
                label = "Agent roster",
                status = if (agents.isNotEmpty() && (diagnostics == null || diagnostics.agents.isNotEmpty())) CHECK_PASS else CHECK_WARN,
                detail = "${agents.size} visible; ${diagnostics?.agents?.size ?: 0} diagnostic summaries",
            ),
            DiagnosticCheck(
                id = "runtime_options",
                label = "Runtime model/permission availability",
                status = if (runtimeAvailable) CHECK_PASS else CHECK_WARN,
                detail = "${diagnostics?.runtimeOptions?.size ?: agentRuntimeSettings.size.coerceAtLeast(1)} runtime profile(s)",
            ),
            DiagnosticCheck(
                id = "recent_send_failure",
                label = "Recent send failure",
                status = if (lastSendFailure == null) CHECK_PASS else CHECK_FAIL,
                detail = lastSendFailure ?: "No recent send failure",
            ),
            DiagnosticCheck(
                id = "send_status",
                label = "Send /status",
                status = when {
                    statusReplyReceived -> CHECK_PASS
                    statusCommandSent -> CHECK_WARN
                    else -> CHECK_PENDING
                },
                detail = if (statusReplyReceived) "Reply seen" else if (statusCommandSent) "Command sent; waiting for reply" else "Not tested",
            ),
            DiagnosticCheck(
                id = "attachment",
                label = "Test attachment",
                status = if (attachmentTested) CHECK_PASS else CHECK_PENDING,
                detail = latestAttachmentQueuedAt?.let { "Queued ${formatRelativeAge(it)}" } ?: "No attachment queued",
            ),
            DiagnosticCheck(
                id = "reconnect",
                label = "Test reconnect",
                status = if (reconnectLikely) CHECK_PASS else CHECK_PENDING,
                detail = pairingInfo.lastVerifiedAt?.let { "Last verified ${formatRelativeAge(it)}" } ?: "Run diagnostics after reopen",
            ),
        )
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
            "/new" -> {
                startNewConversation(selectedTargetId)
                addSystemMessage("Started a new local conversation for ${selectedTargetName()}.")
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
        val slashCommand = normalizeSlashCommand(text)
        val commandTool = if (slashCommand.command.isNotBlank()) {
            listOf(
                ToolCall(
                    id = nextId(),
                    agentId = agent.id,
                    toolName = "slash.command",
                    status = ToolStatus.SUCCESS,
                    input = slashCommand.command,
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
    ): String {
        val slash = normalizeSlashCommand(text)
        return when (slash.command) {
            "/status" -> "Team online: ${agents.count { it.status == AgentStatus.ONLINE }} online, ${agents.count { it.kind == AgentKind.SUBAGENT }} subagents visible."
            "/agents" -> agents.joinToString("\n") { "${it.name}: ${it.status} / parent=${it.parentId ?: "none"}" }
            "/team", "/team-create" -> "${team.value.name}: admin=${agentName(team.value.adminAgentId)}, members=${team.value.memberIds.size}."
            "/parent" -> agent.parentId?.let { "${agent.name} is under ${agentName(it)}." } ?: "${agent.name} has no parent."
            "/tools" -> "${agent.name} tools: ${agent.tools.joinToString(", ")}"
            "/model" -> runtimeSummary(agent.id, "model")
            "/reasoning" -> runtimeSummary(agent.id, "reasoning")
            "/permissions" -> runtimeSummary(agent.id, "permissions")
            "/context" -> {
                val runtime = runtimeSettingsForTarget(agent.id)
                "${agent.name} context: ${runtime.contextUsedTokens} / ${runtime.contextLimitTokens} tokens."
            }
            "/compact" -> "Context compaction is automatic on the desktop side; future prompts stay scoped to this conversation."
            "/diagnostics" -> connectionDiagnostics().summary
            "/new" -> "Use the header + button for a separate thread; the bridge isolates messages by conversation id."
            "/clear" -> "Clear is local to the phone thread. Use New conversation for a fresh thread."
            "/handoff" -> "Handoff recorded for ${agent.name}; next owner stays visible in the shared queue."
            "/approve" -> "Approved ${agent.name} to continue current task."
            "/pause" -> "Pause requested for ${agent.name}."
            "/resume" -> "Resume requested for ${agent.name}."
            "/stop" -> "Stop requested for ${agent.name}."
            "/files" -> "File bridge ready for upload and download events."
            "/photo" -> "Photo bridge ready; use the photo button to attach an image."
            "/", "/help" -> commandHelp(slash.rest)
            else -> when {
                attachments.isNotEmpty() -> "${agent.name} received ${attachments.size} encrypted transfer item(s)."
                text.isBlank() -> "${agent.name} is listening."
                agentSupportsSlashCommand(agent, slash.command) -> "${agent.name} command ${slash.command} accepted. Pair the bridge to let this agent execute its own command handler."
                slash.command.isNotBlank() -> "Unknown command ${slash.command}. Try /help."
                else -> "${agent.name} will route this through the desktop bridge: $text"
            }
        }
    }

    private fun respondToTeam(team: AgentTeam, text: String, attachments: List<FileTransfer>) {
        val conversationId = conversationIdFor(team.id)
        val speakers = team.memberIds.mapNotNull { memberId -> agents.firstOrNull { it.id == memberId } }.take(3)
        val slash = normalizeSlashCommand(text)
        val summary = when {
            slash.command == "/team" || slash.command == "/team-create" -> "${team.name}: ${team.memberIds.size} members, shared profile: ${team.sharedProfile}"
            slash.command == "/" || slash.command == "/help" -> commandsForTarget(team.id).joinToString(" ") { it.trigger }
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
        seenConversationTimestamps.clear()
        seenConversationTimestamps.putAll(restored.seenConversationTimestamps)
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
        agentRuntimeSettings.clear()
        agentRuntimeSettings.putAll(restored.agentRuntimeSettings)
        agentRuntimeSettings["codex"] = codexRuntimeSettings
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
                seenConversationTimestamps = seenConversationTimestamps.toMap(),
                agentPermissionModes = agentPermissionModes.toMap(),
                documents = documents.toList(),
                heartbeats = heartbeats.take(MAX_HEARTBEATS),
                codexRuntimeSettings = codexRuntimeSettings,
                agentRuntimeSettings = agentRuntimeSettings.toMap(),
                updatedAt = now(),
            )
        )
    }

    private fun mergeMessages(remoteMessages: List<ChatMessage>) {
        val mergedById = linkedMapOf<String, ChatMessage>()
        (messages + remoteMessages).forEach { message ->
            val previous = mergedById[message.id]
            val previousRunning = previous?.toolCalls?.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED } == true
            val nextRunning = message.toolCalls.any { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED }
            mergedById[message.id] = if (previousRunning && !nextRunning && message.kind == MessageKind.AGENT) {
                message.copy(createdAt = completionTimestampFor(targetIdForMessage(message)))
            } else {
                message
            }
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
        val isTeamTarget = teams.any { it.id == targetId }
        val teamIds = teams.map { it.id }.toSet()
        if (isTeamTarget) return targetAgentId == targetId || authorId == targetId
        val isTeamScoped = targetAgentId?.let { teamIds.contains(it) } == true
        return when (kind) {
            MessageKind.USER -> targetAgentId == targetId
            MessageKind.AGENT -> authorId == targetId && (targetAgentId == "you" || targetAgentId.isNullOrBlank())
            MessageKind.SYSTEM -> targetAgentId == targetId || (targetAgentId.isNullOrBlank() && authorId == targetId)
        } && !isTeamScoped
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

    private fun seenKey(targetId: String): String =
        "$targetId|${conversationIdFor(targetId)}"

    private data class SlashCommandParts(
        val command: String,
        val rest: String,
    )

    private fun normalizeSlashCommand(text: String): SlashCommandParts {
        val trimmed = text.trim().replace(Regex("^／"), "/")
        if (!trimmed.startsWith("/")) return SlashCommandParts("", trimmed)
        if (trimmed == "/") return SlashCommandParts("/", "")
        val withoutTrailingPunctuation = trimmed.trimEnd('.', '。', '!', '！', '?', '？', ',', '，', ';', '；')
        val firstToken = withoutTrailingPunctuation
            .substringBefore(' ')
            .substringBefore('\n')
            .substringBefore('\t')
        val command = canonicalSlashCommand(firstToken.substringBefore('@').lowercase())
        val rest = withoutTrailingPunctuation.removePrefix(firstToken).trim()
        return SlashCommandParts(command, rest)
    }

    private fun canonicalSlashCommand(command: String): String {
        val normalized = command.trim().lowercase().replace(Regex("^/+"), "/").replace('_', '-')
        return mapOf(
            "/start" to "/help",
            "/commands" to "/help",
            "/cmds" to "/help",
            "/ls" to "/agents",
            "/agent" to "/agents",
            "/who" to "/agents",
            "/teams" to "/team",
            "/group" to "/team",
            "/groups" to "/team",
            "/newchat" to "/new",
            "/new-chat" to "/new",
            "/new-session" to "/new",
            "/reset" to "/new",
            "/models" to "/model",
            "/set-model" to "/model",
            "/think" to "/reasoning",
            "/thinking" to "/reasoning",
            "/reason" to "/reasoning",
            "/permission" to "/permissions",
            "/perms" to "/permissions",
            "/sandbox" to "/permissions",
            "/compact-context" to "/compact",
            "/memory-compact" to "/compact",
            "/health" to "/diagnostics",
            "/diag" to "/diagnostics",
            "/diagnostic" to "/diagnostics",
            "/file" to "/files",
            "/upload" to "/files",
            "/attach" to "/files",
            "/image" to "/photo",
            "/pic" to "/photo",
            "/photos" to "/photo",
            "/tool" to "/tools",
            "/continue" to "/resume",
            "/cancel" to "/stop",
            "/abort" to "/stop",
            "/mem" to "/memory",
            "/logs" to "/heartbeat",
            "/log" to "/heartbeat",
            "/docs" to "/api",
        )[normalized] ?: normalized
    }

    private fun commandHelp(topic: String): String {
        val normalizedTopic = topic.trim().takeIf { it.isNotBlank() }?.let {
            canonicalSlashCommand(if (it.startsWith("/")) it else "/$it")
        }
        val descriptions = mapOf(
            "/status" to "Show bridge and local CLI status.",
            "/agents" to "List visible agents and subagents.",
            "/team" to "List team chats and shared group context.",
            "/spawn" to "Create a persistent subagent: /spawn Name | role.",
            "/team-create" to "Create a persistent team: /team-create Name | purpose.",
            "/model" to "Show this agent's current model and model ids.",
            "/reasoning" to "Show this agent's reasoning levels.",
            "/permissions" to "Show this agent's permission modes.",
            "/context" to "Show current context usage.",
            "/compact" to "Request compact-context behavior.",
            "/diagnostics" to "Show safe bridge diagnostics summary.",
            "/new" to "Start a fresh app conversation from the header + button.",
            "/files" to "Show file transfer support.",
            "/photo" to "Show photo/video attachment support.",
            "/help" to "Show commands; supports /help model.",
        )
        val commandSet = commandsForTarget(selectedTargetId)
        return normalizedTopic?.let { command ->
            descriptions[command]?.let { "$command: $it" }
                ?: commandSet.firstOrNull { it.trigger == command }?.let { "${it.trigger}: ${it.label}" }
        } ?: commandSet.joinToString(" ") { it.trigger }
    }

    private fun runtimeSummary(targetId: String, field: String): String {
        val runtime = runtimeSettingsForTarget(targetId)
        return when (field) {
            "model" -> "${selectedTargetName()} model: ${runtime.model}. Available: ${runtime.modelOptions.joinToString(", ") { it.id }.ifBlank { "none reported" }}."
            "reasoning" -> "${selectedTargetName()} reasoning: ${runtime.reasoningEffort}. Available: ${runtime.reasoningOptions.joinToString(", ") { it.id }.ifBlank { "none reported" }}."
            else -> "${selectedTargetName()} permissions: ${permissionForTarget(targetId)}. Available: ${runtime.permissionOptions.joinToString(", ") { it.id }.ifBlank { "none reported" }}."
        }
    }

    private fun commandsForTarget(targetId: String): List<SlashCommand> {
        val targetAgent = teams.firstOrNull { it.id == targetId }
            ?.memberIds
            ?.mapNotNull { memberId -> agents.firstOrNull { it.id == memberId } }
            ?.flatMap { it.slashCommands }
            ?: agents.firstOrNull { it.id == targetId }?.slashCommands.orEmpty()
        return (commands + targetAgent)
            .filter { it.trigger.isNotBlank() }
            .distinctBy { it.trigger }
    }

    private fun agentSupportsSlashCommand(agent: AgentNode, command: String): Boolean =
        command.isNotBlank() && agent.slashCommands.any { it.trigger == command }

    private fun targetIdForMessage(message: ChatMessage): String =
        if (message.targetAgentId == "you") message.authorId else message.targetAgentId ?: selectedTargetId

    private fun completionTimestampFor(targetId: String): Long =
        maxOf(now(), (seenConversationTimestamps[seenKey(targetId)] ?: 0L) + 1L)

    private fun normalizePermissionMode(value: String): String =
        when (value) {
            "read-only", "workspace-write", "full-access" -> value
            else -> "read-only"
        }

    private fun formatRelativeAge(timestamp: Long): String {
        val seconds = ((now() - timestamp).coerceAtLeast(0L) / 1000L).coerceAtLeast(0L)
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }

    private fun safeUrlLabel(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "not set"
        return runCatching {
            val uri = java.net.URI(trimmed)
            val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "http"
            val host = uri.host?.takeIf { it.isNotBlank() } ?: trimmed.substringAfter("://", trimmed).substringBefore('/')
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            "$scheme://$host$port"
        }.getOrElse { redactSecrets(trimmed).take(120) }
    }

    private fun String.redactedId(): String =
        if (isBlank()) "none" else "${take(6)}...${takeLast(4)}"

    private fun redactSecrets(value: String): String =
        value
            .replace(Regex("""\b\d{4}\s?\d{4}\b"""), "**** ****")
            .replace(Regex("""(?i)(bearer|token|secret|key|password)=([^,\s]+)"""), "$1=<redacted>")
            .replace(Regex("""(?i)(authorization:\s*bearer\s+)[^\s]+"""), "$1<redacted>")
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
