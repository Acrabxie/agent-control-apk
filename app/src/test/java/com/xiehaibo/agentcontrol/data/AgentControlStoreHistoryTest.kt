package com.xiehaibo.agentcontrol.data

import com.google.common.truth.Truth.assertThat
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.MessageKind
import org.junit.Test

class AgentControlStoreHistoryTest {
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
}
