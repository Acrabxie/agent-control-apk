package com.xiehaibo.agentcontrol.data

import com.google.common.truth.Truth.assertThat
import com.xiehaibo.agentcontrol.api.AgentDiagnostic
import com.xiehaibo.agentcontrol.api.BridgeDiagnostics
import com.xiehaibo.agentcontrol.api.BridgeHealth
import com.xiehaibo.agentcontrol.api.BridgeSnapshot
import com.xiehaibo.agentcontrol.api.DiagnosticPairingSummary
import com.xiehaibo.agentcontrol.model.AgentCapabilityOption
import com.xiehaibo.agentcontrol.model.AgentKind
import com.xiehaibo.agentcontrol.model.AgentNode
import com.xiehaibo.agentcontrol.model.AgentStatus
import com.xiehaibo.agentcontrol.model.AgentTeam
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.MessageKind
import com.xiehaibo.agentcontrol.model.SlashCommand
import com.xiehaibo.agentcontrol.model.ToolCall
import com.xiehaibo.agentcontrol.model.ToolStatus
import org.junit.Test

class AgentControlStoreHistoryTest {
    @Test
    fun freshStoreStartsWithoutDemoAgents() {
        val store = AgentControlStore()

        assertThat(store.agents).isEmpty()
        assertThat(store.teams).isEmpty()
    }

    @Test
    fun restoresLocalConversationHistoryAndSendsItAsContext() {
        val persistence = FakeConversationPersistence()
        val firstStore = AgentControlStore(conversationPersistence = persistence)

        firstStore.selectedTargetId = "codex"
        firstStore.draftText = "请记住测试词 blue-orbit"
        val firstUserMessage = firstStore.consumeDraft()!!
        firstStore.addRemoteReply(
            ChatMessage(
                id = "codex-reply-1",
                authorId = "codex",
                kind = MessageKind.AGENT,
                text = "已记住 blue-orbit",
                createdAt = firstUserMessage.createdAt + 1,
                targetAgentId = "you",
            )
        )

        val restoredStore = AgentControlStore(conversationPersistence = persistence)
        assertThat(restoredStore.messages.map { it.text }).contains("请记住测试词 blue-orbit")
        assertThat(restoredStore.messages.map { it.text }).contains("已记住 blue-orbit")

        restoredStore.selectedTargetId = "codex"
        restoredStore.draftText = "刚才测试词是什么？"
        val followUp = restoredStore.consumeDraft()!!
        val payload = restoredStore.outboundPayload(followUp)

        assertThat(payload.conversationContext.map { it.text }).contains("请记住测试词 blue-orbit")
        assertThat(payload.conversationContext.map { it.text }).contains("已记住 blue-orbit")
        assertThat(payload.conversationContext.map { it.id }).doesNotContain(followUp.id)
    }

    @Test
    fun newConversationDoesNotSendPreviousThreadAsContext() {
        val store = AgentControlStore()

        store.selectedTargetId = "codex"
        store.draftText = "请记住测试词 amber-thread"
        val firstUserMessage = store.consumeDraft()!!
        store.addRemoteReply(
            ChatMessage(
                id = "codex-reply-amber",
                authorId = "codex",
                kind = MessageKind.AGENT,
                text = "已记住 amber-thread",
                createdAt = firstUserMessage.createdAt + 1,
                targetAgentId = "you",
                conversationId = firstUserMessage.conversationId,
            )
        )

        val oldConversationId = firstUserMessage.conversationId
        val newConversationId = store.startNewConversation("codex")
        store.draftText = "新对话里上一轮测试词是什么？"
        val newUserMessage = store.consumeDraft()!!
        val payload = store.outboundPayload(newUserMessage)

        assertThat(newConversationId).isNotEqualTo(oldConversationId)
        assertThat(payload.conversationId).isEqualTo(newConversationId)
        assertThat(payload.conversationContext.map { it.text }).doesNotContain("请记住测试词 amber-thread")
        assertThat(payload.conversationContext.map { it.text }).doesNotContain("已记住 amber-thread")
    }

    @Test
    fun sendsPermissionModeForSelectedAgent() {
        val store = AgentControlStore()

        store.selectedTargetId = "claude"
        store.updatePermissionForTarget("claude", "full-access")
        store.draftText = "权限测试"
        val message = store.consumeDraft()!!
        val payload = store.outboundPayload(message)

        assertThat(payload.targetAgentId).isEqualTo("claude")
        assertThat(payload.agentPermissionMode).isEqualTo("full-access")
        assertThat(store.permissionForTarget("codex")).isEqualTo("read-only")
    }

    @Test
    fun diagnosticsLabelsReflectUnpairedOfflineAndPairedStates() {
        val store = AgentControlStore()

        val unpaired = store.connectionDiagnostics()
        assertThat(unpaired.checks.first { it.id == "pairing_state" }.status).isEqualTo("warn")

        store.desktopUrlDraft = "https://relay.example.com"
        store.recordDiagnosticsFailure("HTTP 503 desktop_offline")
        val offline = store.connectionDiagnostics()
        assertThat(offline.checks.first { it.id == "url_reachable" }.status).isEqualTo("fail")

        store.deviceId = "device-1234567890"
        store.recordDiagnosticsSuccess(
            health = BridgeHealth(ok = true, service = "agent-control-relay", mode = "digital-key"),
            diagnostics = BridgeDiagnostics(
                bridgeVersion = "agent-control.v1",
                desktopName = "dev-mac",
                pairing = DiagnosticPairingSummary(
                    pairedDeviceCount = 1,
                    sessionActive = true,
                    pairedDeviceId = "device-1234567890",
                ),
                connectionMode = "relay",
                sessionActive = true,
                pairedDeviceId = "device-1234567890",
                agents = listOf(
                    AgentDiagnostic(
                        id = "codex",
                        name = "Codex",
                        modelOptions = listOf("gpt-5.5"),
                        permissionOptions = listOf("read-only"),
                    )
                ),
            ),
        )
        val paired = store.connectionDiagnostics()
        assertThat(paired.checks.first { it.id == "bridge_health" }.status).isEqualTo("pass")
        assertThat(paired.checks.first { it.id == "encrypted_session" }.status).isEqualTo("pass")
    }

    @Test
    fun agentDetailRuntimeUpdatesUseTargetSpecificStorePaths() {
        val store = AgentControlStore()
        store.applyRosterForTest(listOf(codexAgent(), claudeAgent()))

        store.updateModelForTarget("claude", "opus")
        store.updateReasoningForTarget("claude", "max")
        store.updatePermissionForTarget("claude", "workspace-write")

        assertThat(store.runtimeSettingsForTarget("claude").model).isEqualTo("opus")
        assertThat(store.runtimeSettingsForTarget("claude").reasoningEffort).isEqualTo("max")
        assertThat(store.permissionForTarget("claude")).isEqualTo("workspace-write")
        assertThat(store.runtimeSettingsForTarget("codex").model).isEqualTo("gpt-5.5")
    }

    @Test
    fun declaredAgentCapabilitiesDriveModelAndPermissionChoices() {
        val store = AgentControlStore()
        val customAgent = AgentNode(
            id = "local_worker",
            name = "Local Worker",
            kind = AgentKind.SUBAGENT,
            role = "custom registered agent",
            status = AgentStatus.ONLINE,
            parentId = "codex",
            modelOptions = listOf(AgentCapabilityOption("local/model-a", "Local Model A")),
            reasoningOptions = listOf(AgentCapabilityOption("balanced", "Balanced")),
            permissionOptions = listOf(AgentCapabilityOption("observe", "Observe"), AgentCapabilityOption("edit-safe", "Edit Safe")),
            slashCommands = listOf(SlashCommand("/audit", "Audit", "selected")),
        )

        store.applySnapshot(
            BridgeSnapshot(
                agents = listOf(customAgent),
                commands = emptyList(),
                messages = emptyList(),
                transfers = emptyList(),
                documents = emptyList(),
                heartbeats = emptyList(),
            )
        )

        val runtime = store.runtimeSettingsForTarget("local_worker")
        assertThat(runtime.modelOptions.map { it.id }).containsExactly("local/model-a")
        assertThat(runtime.permissionOptions.map { it.id }).containsExactly("observe", "edit-safe")
        assertThat(store.agents.first { it.id == "local_worker" }.slashCommands.map { it.trigger }).containsExactly("/audit")

        store.updatePermissionForTarget("local_worker", "edit-safe")
        assertThat(store.permissionForTarget("local_worker")).isEqualTo("edit-safe")
    }

    @Test
    fun localDismissCommandRemovesSelectedSubagentFromRosterAndTeams() {
        val store = AgentControlStore()
        store.applyRosterForTest(listOf(codexAgent()))

        store.selectedTargetId = "codex"
        store.runCommand(SlashCommand("/spawn", "Spawn", "selected"))
        val childId = store.selectedAgentId
        assertThat(store.agents.map { it.id }).contains(childId)
        assertThat(store.team.value.memberIds).contains(childId)

        store.selectedTargetId = childId
        store.runCommand(SlashCommand("/dismiss", "Dismiss subagent", "selected"))

        assertThat(store.agents.map { it.id }).doesNotContain(childId)
        assertThat(store.teams.flatMap { it.memberIds }).doesNotContain(childId)
        assertThat(store.selectedTargetId).isNotEqualTo(childId)
    }

    @Test
    fun userDismissCanRemoveNamedSubagentFromAnotherRoot() {
        val store = AgentControlStore()
        val claudeChild = AgentNode(
            id = "sub-releaseguard",
            name = "ReleaseGuard",
            kind = AgentKind.SUBAGENT,
            role = "release monitor",
            status = AgentStatus.ONLINE,
            parentId = "claude",
        )
        store.applyRosterForTest(listOf(codexAgent(), claudeAgent(), claudeChild))

        store.selectedTargetId = "codex"
        store.draftText = "/dismiss ReleaseGuard"
        val message = store.consumeDraft()!!
        store.respondLocallyTo(message)

        assertThat(store.agents.map { it.id }).doesNotContain("sub-releaseguard")
        assertThat(store.teams.flatMap { it.memberIds }).doesNotContain("sub-releaseguard")
        assertThat(store.messages.last().text).contains("Subagent removed: ReleaseGuard")
    }

    @Test
    fun runningRepliesShowTypingAndCompletionBecomesUnread() {
        val store = AgentControlStore()
        val pending = ChatMessage(
            id = "reply-running",
            authorId = "codex",
            kind = MessageKind.AGENT,
            text = "Editing...",
            createdAt = 1_000L,
            targetAgentId = "you",
            toolCalls = listOf(
                ToolCall(
                    id = "tool-running",
                    agentId = "codex",
                    toolName = "codex.edit",
                    status = ToolStatus.RUNNING,
                    input = "edit",
                    output = "working",
                    startedAt = 1_000L,
                )
            ),
        )

        store.addRemoteReply(pending)
        assertThat(store.targetIsTyping("codex")).isTrue()
        store.markConversationSeen("codex")

        store.applySnapshot(
            BridgeSnapshot(
                agents = store.agents.toList(),
                commands = emptyList(),
                messages = listOf(
                    pending.copy(
                        text = "Done",
                        toolCalls = pending.toolCalls.map { it.copy(status = ToolStatus.SUCCESS, output = "saved") },
                    )
                ),
                transfers = emptyList(),
                documents = emptyList(),
                heartbeats = emptyList(),
            )
        )

        assertThat(store.targetIsTyping("codex")).isFalse()
        assertThat(store.unreadCompletedCount("codex")).isEqualTo(1)
    }

    @Test
    fun teamMessagesDoNotLeakIntoSingleAgentConversation() {
        val store = AgentControlStore()
        store.applyRosterForTest(listOf(codexAgent()))
        val teamConversationId = store.conversationIdFor("core")
        val directConversationId = store.conversationIdFor("codex")

        store.addRemoteReply(
            ChatMessage(
                id = "team-codex-reply",
                authorId = "codex",
                kind = MessageKind.AGENT,
                text = "team-only update",
                createdAt = 1_000L,
                targetAgentId = "core",
                conversationId = teamConversationId,
            )
        )
        store.addRemoteReply(
            ChatMessage(
                id = "direct-codex-reply",
                authorId = "codex",
                kind = MessageKind.AGENT,
                text = "direct-only update",
                createdAt = 1_100L,
                targetAgentId = "you",
                conversationId = directConversationId,
            )
        )

        assertThat(store.messagesForActiveConversation("codex").map { it.text }).contains("direct-only update")
        assertThat(store.messagesForActiveConversation("codex").map { it.text }).doesNotContain("team-only update")
        assertThat(store.messagesForActiveConversation("core").map { it.text }).contains("team-only update")
    }

    @Test
    fun slashCommandsAcceptBotSuffixAndTrailingPunctuation() {
        val store = AgentControlStore()
        store.applyRosterForTest(listOf(codexAgent()))
        store.selectedTargetId = "codex"
        store.draftText = "/status@AgentControl。"

        val message = store.consumeDraft()!!
        store.respondLocallyTo(message)

        assertThat(store.messages.map { it.text }).contains("Team online: 1 online, 0 subagents visible.")

        store.draftText = "／perms@AgentControl,"
        val permissions = store.consumeDraft()!!
        store.respondLocallyTo(permissions)
        assertThat(store.messages.last().text).contains("permissions:")

        store.draftText = "/help models"
        val help = store.consumeDraft()!!
        store.respondLocallyTo(help)
        assertThat(store.messages.last().text).contains("/model:")
    }

    @Test
    fun testerReportRedactsKeysAndIncludesVersionSummary() {
        val store = AgentControlStore()
        store.desktopUrlDraft = "https://relay.example.com/path?key=12345678"
        store.pairingKeyDraft = "1234 5678"
        store.rememberSendFailure("token=secret-value key=12345678 bearer abc")

        val report = store.testerReport(
            packageName = "com.acrab.agentcontrol",
            versionName = "1.0.1",
            versionCode = 51,
        )

        assertThat(report).contains("Package: com.acrab.agentcontrol")
        assertThat(report).contains("Version: 1.0.1 (51)")
        assertThat(report).doesNotContain("12345678")
        assertThat(report).doesNotContain("secret-value")
    }

    private class FakeConversationPersistence : ConversationPersistence {
        var state: PersistedConversationState? = null

        override fun load(): PersistedConversationState? = state

        override fun save(state: PersistedConversationState) {
            this.state = state
        }

        override fun clear() {
            state = null
        }
    }

    private fun AgentControlStore.applyRosterForTest(agents: List<AgentNode>) {
        applySnapshot(
            BridgeSnapshot(
                agents = agents,
                teams = listOf(
                    AgentTeam(
                        id = "core",
                        name = "Local Agent Team",
                        adminAgentId = agents.firstOrNull()?.id ?: "codex",
                        memberIds = agents.map { it.id },
                        sharedProfile = "test roster",
                    )
                ),
                commands = emptyList(),
                messages = emptyList(),
                transfers = emptyList(),
                documents = emptyList(),
                heartbeats = emptyList(),
            )
        )
    }

    private fun codexAgent(): AgentNode =
        AgentNode(
            id = "codex",
            name = "Codex",
            kind = AgentKind.CODEX,
            role = "controller",
            status = AgentStatus.ONLINE,
            modelOptions = listOf(AgentCapabilityOption("gpt-5.5", "gpt-5.5")),
            reasoningOptions = listOf(AgentCapabilityOption("low", "Low")),
            permissionOptions = listOf(
                AgentCapabilityOption("read-only", "Read Only"),
                AgentCapabilityOption("workspace-write", "Workspace Write"),
            ),
            slashCommands = listOf(SlashCommand("/plan", "Plan", "selected")),
            canSpawnChildren = true,
        )

    private fun claudeAgent(): AgentNode =
        AgentNode(
            id = "claude",
            name = "Claude Code",
            kind = AgentKind.CLAUDE_CODE,
            role = "implementation",
            status = AgentStatus.ONLINE,
            modelOptions = listOf(
                AgentCapabilityOption("sonnet", "sonnet"),
                AgentCapabilityOption("opus", "opus"),
            ),
            reasoningOptions = listOf(
                AgentCapabilityOption("medium", "Medium"),
                AgentCapabilityOption("max", "Max"),
            ),
            permissionOptions = listOf(
                AgentCapabilityOption("read-only", "Read Only"),
                AgentCapabilityOption("workspace-write", "Workspace Write"),
            ),
            canSpawnChildren = true,
        )
}
