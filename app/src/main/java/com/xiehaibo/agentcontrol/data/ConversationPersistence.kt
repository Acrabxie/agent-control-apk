package com.xiehaibo.agentcontrol.data

import android.content.Context
import android.content.SharedPreferences
import com.xiehaibo.agentcontrol.api.CodexRuntimeSettings
import com.xiehaibo.agentcontrol.model.AgentNode
import com.xiehaibo.agentcontrol.model.AgentTeam
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.HeartbeatEntry
import com.xiehaibo.agentcontrol.model.ProjectDocument
import com.xiehaibo.agentcontrol.model.SlashCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersistedConversationState(
    val agents: List<AgentNode> = emptyList(),
    val teams: List<AgentTeam> = emptyList(),
    val commands: List<SlashCommand> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val activeConversationIds: Map<String, String> = emptyMap(),
    val agentPermissionModes: Map<String, String> = emptyMap(),
    val documents: List<ProjectDocument> = emptyList(),
    val heartbeats: List<HeartbeatEntry> = emptyList(),
    val codexRuntimeSettings: CodexRuntimeSettings = CodexRuntimeSettings(),
    val updatedAt: Long = System.currentTimeMillis(),
)

interface ConversationPersistence {
    fun load(): PersistedConversationState?

    fun save(state: PersistedConversationState)

    fun clear()
}

class SharedPreferencesConversationPersistence(context: Context) : ConversationPersistence {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("agent_control_conversations", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun load(): PersistedConversationState? {
        val raw = prefs.getString(KEY_STATE, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            json.decodeFromString(PersistedConversationState.serializer(), raw)
        }.getOrNull()
    }

    override fun save(state: PersistedConversationState) {
        prefs.edit()
            .putString(KEY_STATE, json.encodeToString(state))
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_STATE = "state"
    }
}
