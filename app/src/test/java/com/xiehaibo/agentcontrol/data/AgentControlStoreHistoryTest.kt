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
