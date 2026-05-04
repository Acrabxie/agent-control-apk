package com.xiehaibo.agentcontrol

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.xiehaibo.agentcontrol.api.NetworkBridgeClient
import com.xiehaibo.agentcontrol.api.RuntimeOption
import com.xiehaibo.agentcontrol.data.AgentControlStore
import com.xiehaibo.agentcontrol.data.SharedPreferencesConversationPersistence
import com.xiehaibo.agentcontrol.data.SharedPreferencesPairingPersistence
import com.xiehaibo.agentcontrol.model.AgentKind
import com.xiehaibo.agentcontrol.model.AgentNode
import com.xiehaibo.agentcontrol.model.AgentStatus
import com.xiehaibo.agentcontrol.model.AgentTeam
import com.xiehaibo.agentcontrol.model.ChatMessage
import com.xiehaibo.agentcontrol.model.FileTransfer
import com.xiehaibo.agentcontrol.model.MessageKind
import com.xiehaibo.agentcontrol.model.ProjectDocument
import com.xiehaibo.agentcontrol.model.ToolCall
import com.xiehaibo.agentcontrol.model.ToolStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {
    private val incomingPairingLink = mutableStateOf<PairingDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingPairingLink.value = parsePairingDeepLink(intent)
        setContent {
            AgentControlTheme {
                AgentControlApp(
                    incomingPairingLink = incomingPairingLink.value,
                    onPairingLinkConsumed = { incomingPairingLink.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingPairingLink.value = parsePairingDeepLink(intent)
    }
}

private enum class AppPanel {
    CHAT,
    PROJECT,
}

private const val BRIDGE_TAG = "AgentControlBridge"
private const val MESSAGE_SEND_TIMEOUT_MS = 315_000L

private data class PairingDeepLink(
    val desktopUrl: String,
    val pairingKey: String,
    val desktopFingerprint: String?,
    val desktopName: String?,
)

private fun parsePairingDeepLink(intent: Intent?): PairingDeepLink? {
    return parsePairingDeepLinkUri(intent?.data)
}

private fun parsePairingDeepLinkText(value: String?): PairingDeepLink? {
    val raw = value?.trim().orEmpty()
    if (raw.isBlank()) return null
    val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
    return parsePairingDeepLinkUri(uri)
}

private fun parsePairingDeepLinkUri(uri: Uri?): PairingDeepLink? {
    uri ?: return null
    if (uri.scheme != "agentcontrol" || uri.host != "pair") return null
    val desktopUrl = uri.getQueryParameter("url")?.trim()?.trimEnd('/') ?: return null
    val pairingKey = uri.getQueryParameter("key")?.filter { it.isDigit() } ?: return null
    if (desktopUrl.isBlank() || pairingKey.length != 8) return null
    return PairingDeepLink(
        desktopUrl = desktopUrl,
        pairingKey = pairingKey,
        desktopFingerprint = uri.getQueryParameter("fp"),
        desktopName = uri.getQueryParameter("name"),
    )
}

@Composable
private fun AgentControlTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF7DD3FC),
        secondary = Color(0xFFB5E48C),
        tertiary = Color(0xFFFFC857),
        background = Color(0xFF101418),
        surface = Color(0xFF171D23),
        surfaceVariant = Color(0xFF232B33),
        outline = Color(0xFF536171),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentControlApp(
    incomingPairingLink: PairingDeepLink?,
    onPairingLinkConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val builtInRelayUrl = remember { BuildConfig.AGENT_CONTROL_DEFAULT_RELAY_URL.trim().trimEnd('/') }
    val store = remember(appContext) {
        AgentControlStore(
            pairingPersistence = SharedPreferencesPairingPersistence(appContext),
            conversationPersistence = SharedPreferencesConversationPersistence(appContext),
        ).also {
            it.useDefaultDesktopUrl(builtInRelayUrl)
            it.preferDefaultRelayForPairedDesktop(builtInRelayUrl)
        }
    }
    val scope = rememberCoroutineScope()
    var panel by rememberSaveable { mutableStateOf(AppPanel.CHAT) }
    var showPairDialog by rememberSaveable { mutableStateOf(false) }
    var pairingBusy by rememberSaveable { mutableStateOf(false) }
    var scanBusy by rememberSaveable { mutableStateOf(false) }
    var pairingError by rememberSaveable { mutableStateOf<String?>(null) }

    fun applyPairingLink(link: PairingDeepLink, source: String) {
        store.desktopUrlDraft = link.desktopUrl
        store.pairingKeyDraft = link.pairingKey.chunked(4).joinToString(" ")
        pairingError = null
        showPairDialog = true
        store.addSystemMessage("$source pairing QR for ${link.desktopName ?: link.desktopUrl}.")
    }

    fun startPairingQrScan() {
        if (scanBusy || pairingBusy) return
        scanBusy = true
        GmsBarcodeScanning.getClient(context)
            .startScan()
            .addOnSuccessListener { barcode ->
                scanBusy = false
                val link = parsePairingDeepLinkText(barcode.rawValue)
                if (link == null) {
                    val message = "That QR code is not an Agent Control pairing code."
                    pairingError = message
                    store.rememberPairingError(message)
                } else {
                    applyPairingLink(link, "Scanned")
                }
            }
            .addOnCanceledListener {
                scanBusy = false
            }
            .addOnFailureListener { error ->
                scanBusy = false
                val message = error.message ?: "QR scanner is not available on this device."
                pairingError = message
                store.rememberPairingError(message)
            }
    }

    LaunchedEffect(incomingPairingLink) {
        val link = incomingPairingLink ?: return@LaunchedEffect
        applyPairingLink(link, "Loaded")
        onPairingLinkConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Agent Control", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (store.pairingInfo.paired) store.pairingInfo.desktopUrl else "not paired",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = { showPairDialog = true },
                        label = { Text(if (store.pairingInfo.paired) "encrypted" else "pair") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = panel == AppPanel.CHAT,
                    onClick = { panel = AppPanel.CHAT },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Chat") },
                )
                NavigationBarItem(
                    selected = panel == AppPanel.PROJECT,
                    onClick = { panel = AppPanel.PROJECT },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Project") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (panel) {
                AppPanel.CHAT -> ChatPanel(
                    store = store,
                )
                AppPanel.PROJECT -> ProjectPanel(
                    store = store,
                    openChat = { panel = AppPanel.CHAT },
                )
            }
        }
    }

    if (showPairDialog) {
        PairingDialog(
            store = store,
            busy = pairingBusy,
            scanBusy = scanBusy,
            error = pairingError,
            builtInRelayUrl = builtInRelayUrl,
            onScanQr = { startPairingQrScan() },
            onDismiss = { showPairDialog = false },
            onForget = {
                store.forgetDesktopPairing()
                pairingError = null
            },
            onPair = pairAction@{
                val normalizedKey = store.pairingKeyDraft.filter { it.isDigit() }
                if (store.desktopUrlDraft.isBlank() || normalizedKey.length != 8) {
                    val message = "Enter the computer address and the 8-digit key."
                    pairingError = message
                    store.rememberPairingError(message)
                    return@pairAction
                }
                pairingBusy = true
                pairingError = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            NetworkBridgeClient.pair(
                                desktopUrl = store.desktopUrlDraft,
                                pairingKey = store.pairingKeyDraft,
                                devicePublicKey = store.pairingInfo.devicePublicKey,
                                keyPair = store.keyPair,
                                pinnedDesktopFingerprint = store.pinnedDesktopFingerprintForDraft(),
                            )
                        }
                        store.applyRemotePairing(
                            desktopUrl = store.desktopUrlDraft,
                            response = result.response,
                            challenge = result.challenge,
                            key = result.sessionKey,
                        )
                        store.preferDefaultRelayForPairedDesktop(builtInRelayUrl)
                        showPairDialog = false
                    } catch (error: Throwable) {
                        val message = error.message ?: "Pairing failed"
                        pairingError = message
                        store.rememberPairingError(message)
                        store.addSystemMessage("Bridge pairing failed: $message")
                    } finally {
                        pairingBusy = false
                    }
                }
            },
        )
    }
}

@Composable
private fun ChatPanel(
    store: AgentControlStore,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val messageListState = rememberLazyListState()
    var openConversationId by rememberSaveable { mutableStateOf<String?>(null) }
    var sendingMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraName by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            store.queueAttachment(
                uri = it.toString(),
                name = it.bestName("file"),
                mimeType = "application/octet-stream",
            )
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (captured && uri != null) {
            store.queueAttachment(
                uri = uri.toString(),
                name = pendingCameraName ?: uri.bestName("photo.jpg"),
                mimeType = "image/jpeg",
            )
        } else if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        pendingCameraUri = null
        pendingCameraName = null
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spokenText.isNullOrBlank()) {
                store.draftText = listOf(store.draftText.trim(), spokenText)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }
    }
    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Agent Control")
        }
        runCatching { voiceLauncher.launch(intent) }
            .onFailure { store.addSystemMessage("Voice input is not available on this device.") }
    }
    fun startPhotoCapture() {
        val name = "agent-control-${System.currentTimeMillis()}.jpg"
        val uri = createImageCaptureUri(context, name)
        if (uri == null) {
            store.addSystemMessage("Camera capture is not available on this device.")
            return
        }
        pendingCameraUri = uri.toString()
        pendingCameraName = name
        runCatching { cameraLauncher.launch(uri) }
            .onFailure {
                runCatching { context.contentResolver.delete(uri, null, null) }
                pendingCameraUri = null
                pendingCameraName = null
                store.addSystemMessage("Camera capture is not available on this device.")
            }
    }
    val activeAgent = openConversationId?.let { id -> store.agents.firstOrNull { it.id == id } }
    val activeTeam = openConversationId?.let { id -> store.teams.firstOrNull { it.id == id } }

    if (activeAgent == null && activeTeam == null) {
        ConversationList(
            store = store,
            onOpenAgent = { agent ->
                store.selectedAgentId = agent.id
                store.selectedTargetId = agent.id
                openConversationId = agent.id
            },
            onOpenTeam = { team ->
                store.selectedTargetId = team.id
                team.memberIds.firstOrNull()?.let { memberId ->
                    if (store.agents.any { it.id == memberId }) store.selectedAgentId = memberId
                }
                openConversationId = team.id
            },
        )
        return
    }

    val activeTargetId = activeAgent?.id ?: activeTeam?.id.orEmpty()
    val conversationMessages = if (activeTeam != null) {
        messagesForTeamConversation(store, activeTeam.id)
    } else {
        messagesForConversation(store, activeAgent!!.id)
    }
    val awaitingReply = conversationAwaitingReply(conversationMessages, activeTargetId)

    LaunchedEffect(activeTargetId, conversationMessages.size) {
        if (conversationMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(conversationMessages.lastIndex)
        }
    }

    fun requestScrollToBottom() {
        scope.launch {
            val latestMessages = if (activeTeam != null) {
                messagesForTeamConversation(store, activeTeam.id)
            } else {
                messagesForConversation(store, activeAgent!!.id)
            }
            if (latestMessages.isNotEmpty()) {
                runCatching { messageListState.animateScrollToItem(latestMessages.lastIndex) }
            }
        }
    }

    suspend fun fetchRemoteSnapshot(key: javax.crypto.SecretKey, timeoutMs: Long = 30_000): Boolean {
        val snapshot = try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    NetworkBridgeClient.fetchSnapshot(
                        desktopUrl = store.pairingInfo.desktopUrl,
                        deviceId = store.deviceId,
                        sessionKey = key,
                    )
                }
            }
        } catch (_: Throwable) {
            null
        }
        if (snapshot != null) {
            store.applySnapshot(snapshot)
            return true
        }
        return false
    }

    suspend fun retryRemoteSendAfterFailure(message: ChatMessage, key: javax.crypto.SecretKey): ChatMessage? =
        try {
            withTimeout(MESSAGE_SEND_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    NetworkBridgeClient.sendMessage(
                        desktopUrl = store.pairingInfo.desktopUrl,
                        deviceId = store.deviceId,
                        sessionKey = key,
                        payload = store.outboundPayload(message),
                    )
                }
            }
        } catch (error: Throwable) {
            Log.w(BRIDGE_TAG, "retry ${message.id} still waiting", error)
            null
        }

    suspend fun awaitRunningReplyCompletion(reply: ChatMessage, key: javax.crypto.SecretKey) {
        if (reply.toolCalls.none { it.status == ToolStatus.RUNNING }) return
        repeat(60) {
            delay(3_000)
            fetchRemoteSnapshot(key, timeoutMs = 12_000)
            val latest = store.messages.firstOrNull { it.id == reply.id }
            if (latest != null && latest.toolCalls.none { it.status == ToolStatus.RUNNING }) {
                requestScrollToBottom()
                return
            }
        }
    }

    suspend fun awaitRemoteReplyAfterSendFailure(message: ChatMessage, key: javax.crypto.SecretKey): Boolean {
        repeat(120) { attempt ->
            if (fetchRemoteSnapshot(key, timeoutMs = 12_000)) {
                val recovered = remoteReplyFor(store, message)
                if (recovered != null) {
                    awaitRunningReplyCompletion(recovered, key)
                    return true
                }
            }
            if (attempt in setOf(2, 8, 20, 40, 70)) {
                val retryReply = retryRemoteSendAfterFailure(message, key)
                if (retryReply != null) {
                    store.addRemoteReply(retryReply)
                    requestScrollToBottom()
                    awaitRunningReplyCompletion(retryReply, key)
                    return true
                }
            }
            delay(5_000)
        }
        return false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (activeTeam != null) {
            TeamConversationHeader(
                team = activeTeam,
                memberNames = activeTeam.memberIds.mapNotNull { memberId -> store.agents.firstOrNull { it.id == memberId }?.name },
                onBack = { openConversationId = null },
            )
            TeamSharedProfile(activeTeam)
        } else {
            ConversationHeader(
                agent = activeAgent!!,
                parentName = activeAgent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
                onBack = { openConversationId = null },
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(conversationMessages, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    agent = store.agents.firstOrNull { it.id == message.authorId },
                )
            }
            if (awaitingReply) {
                item(key = "thinking-$activeTargetId") {
                    ThinkingMessage(
                        agent = activeAgent ?: activeTeam?.adminAgentId?.let { adminId ->
                            store.agents.firstOrNull { it.id == adminId }
                        },
                    )
                }
            }
        }
        PendingAttachments(store.pendingAttachments)
        Composer(
            store = store,
            isSending = sendingMessageId != null,
            onAttachFile = { filePicker.launch(arrayOf("*/*")) },
            onCapturePhoto = { startPhotoCapture() },
            onVoiceInput = { startVoiceInput() },
            onSend = {
                scope.launch {
                    val message = store.consumeDraft() ?: return@launch
                    sendingMessageId = message.id
                    Log.d(BRIDGE_TAG, "queued ${message.id}: ${message.text}")
                    try {
                        requestScrollToBottom()
                        val key = store.sessionKey
                        if (store.pairingInfo.paired && key != null && store.deviceId.isNotBlank()) {
                            Log.d(BRIDGE_TAG, "send ${message.id} to ${store.pairingInfo.desktopUrl}")
                            val (reply, snapshot) = withTimeout(MESSAGE_SEND_TIMEOUT_MS) {
                                withContext(Dispatchers.IO) {
                                    val remoteReply = NetworkBridgeClient.sendMessage(
                                        desktopUrl = store.pairingInfo.desktopUrl,
                                        deviceId = store.deviceId,
                                        sessionKey = key,
                                        payload = store.outboundPayload(message),
                                    )
                                    val remoteSnapshot = runCatching {
                                        NetworkBridgeClient.fetchSnapshot(
                                            desktopUrl = store.pairingInfo.desktopUrl,
                                            deviceId = store.deviceId,
                                            sessionKey = key,
                                        )
                                    }.getOrNull()
                                    remoteReply to remoteSnapshot
                                }
                            }
                            if (snapshot != null) {
                                store.applySnapshot(snapshot)
                            }
                            store.addRemoteReply(reply)
                            if (sendingMessageId == message.id) {
                                sendingMessageId = null
                            }
                            Log.d(BRIDGE_TAG, "send ${message.id} displayed")
                            requestScrollToBottom()
                            awaitRunningReplyCompletion(reply, key)
                        } else {
                            store.respondLocallyTo(message)
                            if (sendingMessageId == message.id) {
                                sendingMessageId = null
                            }
                            requestScrollToBottom()
                        }
                    } catch (error: Throwable) {
                        val errorText = error.message ?: "unknown error"
                        val key = store.sessionKey
                        Log.e(BRIDGE_TAG, "send ${message.id} failed", error)
                        if (errorText.contains("session_key_mismatch") || errorText.contains("not_paired") || errorText.contains("HTTP 401")) {
                            store.markPairingInvalid("Bridge session expired. Pair with the computer again, then resend the message.")
                        } else if (key != null && awaitRemoteReplyAfterSendFailure(message, key)) {
                            Log.d(BRIDGE_TAG, "send ${message.id} recovered from snapshot polling")
                        } else {
                            store.addSystemMessage("Bridge connection is unstable. The message is still in this chat; resend if it does not finish.")
                        }
                        requestScrollToBottom()
                    } finally {
                        if (sendingMessageId == message.id) {
                            sendingMessageId = null
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun ConversationList(
    store: AgentControlStore,
    onOpenAgent: (AgentNode) -> Unit,
    onOpenTeam: (AgentTeam) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(store.teams, key = { "team-${it.id}" }) { team ->
            TeamConversationRow(
                team = team,
                memberNames = team.memberIds.mapNotNull { memberId -> store.agents.firstOrNull { it.id == memberId }?.name },
                lastMessage = lastMessageForTeamConversation(store, team.id),
                onClick = { onOpenTeam(team) },
            )
        }
        items(store.agents, key = { it.id }) { agent ->
            ConversationRow(
                agent = agent,
                parentName = agent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
                lastMessage = lastMessageForConversation(store, agent.id),
                onClick = { onOpenAgent(agent) },
            )
        }
    }
}

@Composable
private fun TeamConversationRow(
    team: AgentTeam,
    memberNames: List<String>,
    lastMessage: ChatMessage?,
    onClick: () -> Unit,
) {
    val preview = lastMessage?.let { message ->
        when {
            message.kind == MessageKind.USER -> "You: ${message.text}"
            message.text.isNotBlank() -> {
                val author = memberNames.firstOrNull().orEmpty()
                if (author.isNotBlank() && message.authorId != "you") "${message.authorId}: ${message.text}" else message.text
            }
            else -> "Shared attachment"
        }
    } ?: team.sharedProfile
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TeamAvatar()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(team.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        lastMessage?.let { formatTime(it.createdAt) } ?: "${team.memberIds.size} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    memberNames.take(3).joinToString(", ").ifBlank { "agent team" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(AgentStatus.ONLINE)
        }
    }
}

@Composable
private fun ConversationRow(
    agent: AgentNode,
    parentName: String?,
    lastMessage: ChatMessage?,
    onClick: () -> Unit,
) {
    val preview = lastMessage?.let { message ->
        when {
            message.kind == MessageKind.USER -> "You: ${message.text}"
            message.text.isNotBlank() -> message.text
            else -> "Attachment"
        }
    } ?: agent.role
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentAvatar(agent)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        lastMessage?.let { formatTime(it.createdAt) } ?: agent.status.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    parentName?.let { "under $it" } ?: agent.kind.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(agent.status)
        }
    }
}

@Composable
private fun ConversationHeader(
    agent: AgentNode,
    parentName: String?,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            AgentAvatar(agent)
            Column(modifier = Modifier.weight(1f)) {
                Text(agent.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    parentName?.let { "under $it" } ?: "${agent.kind.name.lowercase()} · ${agent.status.name.lowercase()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(agent.status)
        }
    }
}

@Composable
private fun TeamConversationHeader(
    team: AgentTeam,
    memberNames: List<String>,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            TeamAvatar()
            Column(modifier = Modifier.weight(1f)) {
                Text(team.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${team.memberIds.size} members · ${memberNames.take(3).joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusDot(AgentStatus.ONLINE)
        }
    }
}

@Composable
private fun TeamSharedProfile(team: AgentTeam) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                team.purpose.ifBlank { team.sharedProfile },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (team.sharedDocuments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(team.sharedDocuments, key = { it }) { document ->
                        AssistChip(
                            onClick = {},
                            label = { Text(document, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentAvatar(agent: AgentNode) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(agentLogoColor(agent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            agentLogoText(agent),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TeamAvatar() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun messagesForConversation(store: AgentControlStore, agentId: String): List<ChatMessage> =
    store.messages.filter { message ->
        message.authorId == agentId ||
            message.targetAgentId == agentId
    }

private fun lastMessageForConversation(store: AgentControlStore, agentId: String): ChatMessage? =
    store.messages.asReversed().firstOrNull { message ->
        message.kind != MessageKind.SYSTEM &&
            (message.authorId == agentId || message.targetAgentId == agentId)
    }

private fun messagesForTeamConversation(store: AgentControlStore, teamId: String): List<ChatMessage> =
    store.messages.filter { message -> message.targetAgentId == teamId }

private fun lastMessageForTeamConversation(store: AgentControlStore, teamId: String): ChatMessage? =
    store.messages.asReversed().firstOrNull { message ->
        message.kind != MessageKind.SYSTEM && message.targetAgentId == teamId
    }

private fun conversationAwaitingReply(messages: List<ChatMessage>, targetId: String): Boolean {
    val lastUser = messages.asReversed().firstOrNull { it.kind == MessageKind.USER && it.targetAgentId == targetId }
        ?: return false
    return messages.none { message ->
        message.kind == MessageKind.AGENT &&
            message.createdAt >= lastUser.createdAt &&
            (message.authorId == targetId || message.targetAgentId == targetId || message.targetAgentId == "you")
    }
}

private fun remoteReplyFor(store: AgentControlStore, message: ChatMessage): ChatMessage? {
    val targetId = message.targetAgentId ?: store.selectedTargetId
    val isTeam = store.teams.any { it.id == targetId }
    return store.messages.asReversed().firstOrNull { candidate ->
        candidate.kind == MessageKind.AGENT &&
            candidate.createdAt >= message.createdAt &&
            if (isTeam) {
                candidate.targetAgentId == targetId
            } else {
                candidate.authorId == targetId && candidate.targetAgentId == "you"
            }
    }
}

@Composable
private fun TeamHeader(store: AgentControlStore) {
    val admin = store.agents.firstOrNull { it.id == store.team.value.adminAgentId }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(modifier = Modifier.weight(1f)) {
                Text(store.team.value.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "admin ${admin?.name ?: "unknown"} · ${store.team.value.memberIds.size} agents · unified profile",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusDot(AgentStatus.ONLINE)
        }
    }
}

@Composable
private fun AgentRoster(store: AgentControlStore) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(store.agents, key = { it.id }) { agent ->
            AgentChip(
                agent = agent,
                selected = agent.id == store.selectedAgentId,
                parentName = agent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
                onClick = { store.selectedAgentId = agent.id },
            )
        }
    }
}

@Composable
private fun AgentChip(
    agent: AgentNode,
    selected: Boolean,
    parentName: String?,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = if (agent.kind == AgentKind.SUBAGENT) Icons.Default.AccountTree else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        label = {
            Column {
                Text(agent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    parentName?.let { "under $it" } ?: agent.kind.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingIcon = { StatusDot(agent.status) },
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    agent: AgentNode?,
) {
    when (message.kind) {
        MessageKind.USER -> UserMessageBubble(message)
        MessageKind.AGENT -> {
            if (message.toolCalls.any { it.status == ToolStatus.RUNNING }) {
                ThinkingMessage(agent, message)
            } else {
                AgentMessageBubble(message, agent)
            }
        }
        MessageKind.SYSTEM -> SystemMessageBubble(message)
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = Color(0xFFE7EAEE),
        ) {
            Text(
                message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color(0xFF14181D),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AgentMessageBubble(
    message: ChatMessage,
    agent: AgentNode?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (agent != null) {
            AgentAvatar(agent)
        } else {
            SystemAvatar()
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.84f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                agent?.name ?: "Agent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Surface(
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = agentBubbleColor(agent),
            ) {
                Text(
                    displayMessageText(message.text),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            AgentActionTrail(message.toolCalls, agent)
        }
    }
}

@Composable
private fun ThinkingMessage(agent: AgentNode?, message: ChatMessage? = null) {
    val statusText = message?.let { runningActionText(it, agent) }
        ?: "thinking..."
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (agent != null) {
            AgentAvatar(agent)
        } else {
            SystemAvatar()
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(0.84f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                agent?.name ?: "Agent",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            PulsingStatusLine(statusText)
            message?.let { AgentActionTrail(it.toolCalls, agent, includeRunning = false) }
        }
    }
}

@Composable
private fun AgentActionTrail(
    toolCalls: List<ToolCall>,
    agent: AgentNode?,
    includeRunning: Boolean = true,
) {
    val visibleCalls = toolCalls
        .filter { shouldShowActionCall(it) && (includeRunning || it.status != ToolStatus.RUNNING) }
        .takeLast(8)
    if (visibleCalls.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visibleCalls.forEach { toolCall ->
            AgentActionRow(toolCall, agent)
        }
    }
}

@Composable
private fun AgentActionRow(
    toolCall: ToolCall,
    agent: AgentNode?,
) {
    val isRunning = toolCall.status == ToolStatus.RUNNING || toolCall.status == ToolStatus.QUEUED
    val alpha = rememberPulseAlpha(isRunning)
    val titleColor = actionTextColor(toolCall.status).copy(alpha = alpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .alpha(if (isRunning) alpha else 1f),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = actionIcon(toolCall),
            contentDescription = null,
            tint = actionTextColor(toolCall.status),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                actionTitle(toolCall, agent),
                style = MaterialTheme.typography.labelMedium,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = actionDetail(toolCall)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8F969F).copy(alpha = if (isRunning) alpha else 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SystemMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        ) {
            Text(
                displayMessageText(message.text),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemAvatar() {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun agentLogoColor(agent: AgentNode): Color = when (agent.kind) {
    AgentKind.CODEX -> Color(0xFF111111)
    AgentKind.CLAUDE_CODE -> Color(0xFFC15F3C)
    AgentKind.ANTIGRAVITY -> Color(0xFF6C4DD5)
    AgentKind.GEMINI_CLI -> Color(0xFF4285F4)
    AgentKind.OPENCODE -> Color(0xFF00A67E)
    AgentKind.SUBAGENT -> MaterialTheme.colorScheme.outline
}

private fun agentLogoText(agent: AgentNode): String = when (agent.kind) {
    AgentKind.CODEX -> "Cx"
    AgentKind.CLAUDE_CODE -> "Cl"
    AgentKind.ANTIGRAVITY -> "Ag"
    AgentKind.GEMINI_CLI -> "G"
    AgentKind.OPENCODE -> "Oc"
    AgentKind.SUBAGENT -> agent.name.take(1).uppercase(Locale.getDefault()).ifBlank { "S" }
}

private fun displayMessageText(value: String): String {
    val text = value.trim()
    if (text.isBlank()) return "Done."
    if (text.contains("received your message and is thinking", ignoreCase = true)) return "thinking"
    val failurePrefix = Regex("""^([A-Za-z ]+ failed to reply:).*""", RegexOption.DOT_MATCHES_ALL)
    val failure = failurePrefix.matchEntire(text)
    if (failure != null) return failure.groupValues[1]
    return text
}

@Composable
private fun PulsingStatusLine(text: String) {
    val alpha = rememberPulseAlpha(true)
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 5.dp)
            .alpha(alpha),
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFAEB4BC),
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun rememberPulseAlpha(enabled: Boolean): Float {
    if (!enabled) return 1f
    val transition = rememberInfiniteTransition(label = "agent-control-pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "agent-control-pulse-alpha",
    )
    return alpha
}

@Composable
private fun agentBubbleColor(agent: AgentNode?): Color = when (agent?.kind) {
    AgentKind.CODEX -> Color(0xFF30343A)
    AgentKind.CLAUDE_CODE -> Color(0xFF5A3A30)
    AgentKind.ANTIGRAVITY -> Color(0xFF3D365C)
    AgentKind.GEMINI_CLI -> Color(0xFF263C63)
    AgentKind.OPENCODE -> Color(0xFF173F36)
    AgentKind.SUBAGENT -> Color(0xFF374151)
    null -> MaterialTheme.colorScheme.surfaceVariant
}

private data class ActionColors(
    val container: Color,
    val border: Color,
    val title: Color,
    val detail: Color,
)

@Composable
private fun actionColors(agent: AgentNode?): ActionColors = when (agent?.kind) {
    AgentKind.CODEX -> ActionColors(
        container = Color(0xFF101418),
        border = Color(0xFF343A40),
        title = Color(0xFFE8EAED),
        detail = Color(0xFFAEB4BC),
    )
    AgentKind.CLAUDE_CODE -> ActionColors(
        container = Color(0xFF2F211B),
        border = Color(0xFF8B5B43),
        title = Color(0xFFF3C7A6),
        detail = Color(0xFFD6A989),
    )
    AgentKind.GEMINI_CLI -> ActionColors(
        container = Color(0xFF13243D),
        border = Color(0xFF3F7BE0),
        title = Color(0xFFDDEAFF),
        detail = Color(0xFFA9C7FF),
    )
    AgentKind.ANTIGRAVITY -> ActionColors(
        container = Color(0xFF211D35),
        border = Color(0xFF7866E8),
        title = Color(0xFFE5DFFF),
        detail = Color(0xFFC2B8FF),
    )
    AgentKind.OPENCODE -> ActionColors(
        container = Color(0xFF102D27),
        border = Color(0xFF26A982),
        title = Color(0xFFDDF8EE),
        detail = Color(0xFFA9DEC9),
    )
    AgentKind.SUBAGENT, null -> ActionColors(
        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        title = MaterialTheme.colorScheme.onSurface,
        detail = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun shouldShowActionCall(toolCall: ToolCall): Boolean =
    toolCall.toolName.isNotBlank() &&
        !toolCall.toolName.endsWith(".progress") &&
        !(toolCall.toolName == "agent.adapter" && toolCall.output.startsWith("routed to", ignoreCase = true))

private fun actionIcon(toolCall: ToolCall): ImageVector {
    val name = toolCall.toolName.lowercase(Locale.getDefault())
    return when {
        "context" in name || "compact" in name || "memory" in name -> Icons.Default.Memory
        "read" in name || "search" in name -> Icons.Default.Memory
        "create" in name || "spawn" in name -> Icons.Default.Folder
        "edit" in name || "patch" in name || "save" in name -> Icons.Default.Save
        "run" in name || "exec" in name || "invoke" in name || "terminal" in name || "build" in name || "test" in name || "install" in name -> Icons.Default.Terminal
        else -> statusIcon(toolCall.status)
    }
}

private fun actionTitle(toolCall: ToolCall, agent: AgentNode?): String {
    val verb = actionVerb(toolCall.toolName)
    return when (toolCall.status) {
        ToolStatus.QUEUED, ToolStatus.RUNNING -> "$verb..."
        ToolStatus.SUCCESS -> verb
        ToolStatus.FAILED -> "$verb failed"
    }
}

private fun actionVerb(toolName: String): String {
    val name = toolName.lowercase(Locale.getDefault())
    return when {
        "model_fallback" in name -> "Switch model"
        "model" in name -> "Wait for model"
        "auth" in name -> "Check auth"
        "context" in name -> "Prepare context"
        "compact" in name -> "Compress context"
        "create" in name || "spawn" in name -> "Create"
        "edit" in name || "patch" in name || "save" in name || "write" in name -> "Edit"
        "read" in name || "search" in name -> "Read"
        "build" in name -> "Build"
        "test" in name -> "Test"
        "install" in name -> "Install"
        "run" in name || "exec" in name || "terminal" in name -> "Run"
        "invoke" in name -> "Call agent"
        "prompt" in name || "plan" in name -> "Plan"
        "answer" in name -> "Write reply"
        "team" in name -> "Update team"
        "slash" in name -> "Run command"
        else -> toolName.substringAfterLast('.').replace('_', ' ')
    }
}

private fun runningActionText(message: ChatMessage, agent: AgentNode?): String {
    val visibleText = displayMessageText(message.text)
    if (visibleText.isNotBlank() && !isGenericProgressText(visibleText)) {
        return visibleText
    }
    val running = message.toolCalls.lastOrNull { it.status == ToolStatus.RUNNING || it.status == ToolStatus.QUEUED }
    if (running != null) return actionTitle(running, agent)
    return if (visibleText.isBlank()) "thinking..." else visibleText
}

private fun actionTextColor(status: ToolStatus): Color = when (status) {
    ToolStatus.QUEUED, ToolStatus.RUNNING -> Color(0xFFAEB4BC)
    ToolStatus.SUCCESS -> Color(0xFF9AA1AA)
    ToolStatus.FAILED -> Color(0xFFD99191)
}

private fun isGenericProgressText(text: String): Boolean {
    val normalized = text.trim().lowercase(Locale.getDefault())
    return normalized in setOf(
        "thinking...",
        "thinking",
        "preparing context...",
        "starting codex...",
        "running...",
        "still running...",
        "waiting for model...",
        "writing reply...",
    )
}

private fun actionDetail(toolCall: ToolCall): String =
    listOf(toolCall.input, toolCall.output)
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "(empty)" }
        .distinct()
        .joinToString("  ->  ")

@Composable
private fun LegacyMessageCard(
    message: ChatMessage,
    agent: AgentNode?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        agent?.name ?: if (message.kind == MessageKind.USER) "You" else "System",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            Text(message.text, style = MaterialTheme.typography.bodyMedium)
            if (message.attachments.isNotEmpty()) {
                TransferList(message.attachments)
            }
        }
    }
}

@Composable
private fun ToolCallRow(toolCall: ToolCall) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = statusIcon(toolCall.status),
                contentDescription = null,
                tint = statusColor(toolCall.status),
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(toolCall.toolName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Text(toolCall.input, style = MaterialTheme.typography.labelMedium)
                Text(
                    toolCall.output,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatTime(toolCall.startedAt), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TransferList(transfers: List<FileTransfer>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        transfers.forEach { transfer ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(transfer.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${transfer.direction} · ${transfer.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingAttachments(attachments: List<FileTransfer>) {
    if (attachments.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { attachment ->
            AssistChip(
                onClick = {},
                label = { Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Composable
private fun Composer(
    store: AgentControlStore,
    isSending: Boolean,
    onAttachFile: () -> Unit,
    onCapturePhoto: () -> Unit,
    onVoiceInput: () -> Unit,
    onSend: () -> Unit,
) {
    val canSend = store.draftText.isNotBlank() || store.pendingAttachments.isNotEmpty()
    var toolsMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var reasoningMenuOpen by remember { mutableStateOf(false) }
    val codex = store.codexRuntimeSettings
    val modelLabel = runtimeLabel(codex.modelOptions, codex.model, fallbackModelLabel(codex.model))
    val reasoningLabel = runtimeLabel(codex.reasoningOptions, codex.reasoningEffort, fallbackReasoningLabel(codex.reasoningEffort))
    val planModeEnabled = codex.permissionMode == "read-only"
    val contextProgress = (codex.contextUsedTokens.toFloat() / codex.contextLimitTokens.coerceAtLeast(1)).coerceIn(0f, 1f)
    val placeholderAlpha = rememberPulseAlpha(isSending)
    val modelOptions = codex.modelOptions.ifEmpty {
        listOf(
            RuntimeOption("gpt-5.5", "5.5"),
            RuntimeOption("gpt-5.4", "5.4"),
            RuntimeOption("gpt-5.3-codex", "5.3 Codex"),
            RuntimeOption("gpt-5.2", "5.2"),
        )
    }
    val reasoningOptions = codex.reasoningOptions.ifEmpty {
        listOf(
            RuntimeOption("low", "Low"),
            RuntimeOption("medium", "Medium"),
            RuntimeOption("high", "High"),
            RuntimeOption("xhigh", "Extra High"),
        )
    }
    fun setPlanMode(enabled: Boolean) {
        store.updateCodexPermission(if (enabled) "read-only" else "workspace-write")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 144.dp)
                    .padding(horizontal = 10.dp),
                value = store.draftText,
                onValueChange = { store.draftText = it },
                minLines = 1,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (store.draftText.isEmpty()) {
                            Text(
                                if (isSending) "Sending..." else "Message ${store.selectedTargetName()}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = placeholderAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box {
                    IconButton(onClick = { toolsMenuOpen = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Model", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        modelLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                toolsMenuOpen = false
                                modelMenuOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Reasoning", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        reasoningLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                toolsMenuOpen = false
                                reasoningMenuOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Plan mode") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                Switch(
                                    checked = planModeEnabled,
                                    onCheckedChange = {
                                        setPlanMode(it)
                                        toolsMenuOpen = false
                                    },
                                )
                            },
                            onClick = {
                                setPlanMode(!planModeEnabled)
                                toolsMenuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Take photo") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                toolsMenuOpen = false
                                onCapturePhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Upload file") },
                            leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                toolsMenuOpen = false
                                onAttachFile()
                            },
                        )
                    }
                    RuntimeDropdown(
                        expanded = modelMenuOpen,
                        options = modelOptions,
                        selectedId = codex.model,
                        onDismiss = { modelMenuOpen = false },
                        onSelect = {
                            store.updateCodexModel(it)
                            modelMenuOpen = false
                        },
                    )
                    RuntimeDropdown(
                        expanded = reasoningMenuOpen,
                        options = reasoningOptions,
                        selectedId = codex.reasoningEffort,
                        onDismiss = { reasoningMenuOpen = false },
                        onSelect = {
                            store.updateCodexReasoning(it)
                            reasoningMenuOpen = false
                        },
                    )
                }
                ContextMeter(
                    progress = contextProgress,
                    usedTokens = codex.contextUsedTokens,
                    limitTokens = codex.contextLimitTokens,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onVoiceInput, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeDropdown(
    expanded: Boolean,
    options: List<RuntimeOption>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelect(option.id) },
                trailingIcon = {
                    if (option.id == selectedId) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
            )
        }
    }
}

@Composable
private fun ContextMeter(
    progress: Float,
    usedTokens: Int,
    limitTokens: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
        )
        Text(
            "${formatTokenCount(usedTokens)} / ${formatTokenCount(limitTokens)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

private fun runtimeLabel(options: List<RuntimeOption>, id: String, fallback: String): String =
    options.firstOrNull { it.id == id }?.label ?: fallback

private fun fallbackModelLabel(id: String): String = when (id) {
    "gpt-5.5" -> "5.5"
    "gpt-5.4" -> "5.4"
    "gpt-5.3-codex" -> "5.3 Codex"
    "gpt-5.2" -> "5.2"
    else -> id
}

private fun fallbackReasoningLabel(id: String): String = when (id) {
    "low" -> "Low"
    "medium" -> "Medium"
    "high" -> "High"
    "xhigh" -> "Extra High"
    else -> id
}

private fun formatTokenCount(value: Int): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000 -> "${value / 1_000_000}M"
        abs >= 1_000 -> "${value / 1_000}k"
        else -> value.toString()
    }
}

@Composable
private fun ProjectPanel(
    store: AgentControlStore,
    openChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(store.documents, key = { it.id }) { doc ->
                FilterChip(
                    selected = doc.id == store.selectedDocumentId,
                    onClick = { store.selectDocument(doc.id) },
                    label = { Text(doc.title) },
                    leadingIcon = {
                        Icon(
                            if (doc.id == "memory") Icons.Default.Memory else Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }
        DocumentEditor(store.selectedDocument(), store.editorText, { store.editorText = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { store.saveDocument() },
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save")
            }
            TextButton(onClick = openChat) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open chat")
            }
        }
        Text("Heartbeat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(store.heartbeats, key = { it.id }) { heartbeat ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, DividerDefaults.color.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(heartbeat.source, fontWeight = FontWeight.SemiBold)
                            Text(heartbeat.text)
                        }
                        Text(formatTime(heartbeat.createdAt), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentEditor(
    document: ProjectDocument,
    value: String,
    onValueChange: (String) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(document.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        document.path,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    formatTime(document.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 210.dp, max = 320.dp),
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(8.dp),
            )
        }
    }
}

@Composable
private fun PairingDialog(
    store: AgentControlStore,
    busy: Boolean,
    scanBusy: Boolean,
    error: String?,
    builtInRelayUrl: String,
    onScanQr: () -> Unit,
    onDismiss: () -> Unit,
    onForget: () -> Unit,
    onPair: () -> Unit,
) {
    val hasBuiltInRelay = builtInRelayUrl.isNotBlank()
    var useCustomAddress by rememberSaveable(builtInRelayUrl) {
        mutableStateOf(!hasBuiltInRelay || (store.desktopUrlDraft.isNotBlank() && store.desktopUrlDraft.trim().trimEnd('/') != builtInRelayUrl))
    }
    LaunchedEffect(hasBuiltInRelay, useCustomAddress, builtInRelayUrl) {
        if (hasBuiltInRelay && !useCustomAddress) {
            store.desktopUrlDraft = builtInRelayUrl
        }
    }
    LaunchedEffect(store.desktopUrlDraft, builtInRelayUrl) {
        val draft = store.desktopUrlDraft.trim().trimEnd('/')
        if (hasBuiltInRelay && draft.isNotBlank() && draft != builtInRelayUrl) {
            useCustomAddress = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Pair with computer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onScanQr,
                    enabled = !busy && !scanBusy,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (scanBusy) "Opening scanner..." else "Scan QR code")
                }
                if (hasBuiltInRelay && !useCustomAddress) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Secure HTTPS relay", fontWeight = FontWeight.SemiBold)
                                Text(
                                    builtInRelayUrl,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { useCustomAddress = true }, enabled = !busy) {
                        Text("Use custom address")
                    }
                } else {
                    OutlinedTextField(
                        value = store.desktopUrlDraft,
                        onValueChange = { store.desktopUrlDraft = it },
                        label = { Text("Bridge or relay address") },
                        placeholder = { Text("https://agent-control-relay.example.workers.dev") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(8.dp),
                    )
                    if (hasBuiltInRelay) {
                        TextButton(
                            onClick = {
                                useCustomAddress = false
                                store.desktopUrlDraft = builtInRelayUrl
                            },
                            enabled = !busy,
                        ) {
                            Text("Use secure relay")
                        }
                    }
                }
                OutlinedTextField(
                    value = store.pairingKeyDraft,
                    onValueChange = { store.pairingKeyDraft = it.take(12) },
                    label = { Text("8-digit key") },
                    placeholder = { Text("1234 5678") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(8.dp),
                )
                Text(
                    store.pairingInfo.cipherSuite,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                store.pairingInfo.desktopFingerprint?.let { fingerprint ->
                    Text(
                        "Pinned desktop ${store.pairingInfo.desktopName ?: ""} $fingerprint",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onPair, enabled = !busy, shape = RoundedCornerShape(8.dp)) {
                Text(if (busy) "Pairing..." else "Pair")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (store.pairingInfo.desktopFingerprint != null) {
                    TextButton(onClick = onForget, enabled = !busy) { Text("Forget") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun StatusDot(status: AgentStatus) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(
                when (status) {
                    AgentStatus.ONLINE -> Color(0xFF80ED99)
                    AgentStatus.BUSY -> Color(0xFFFFC857)
                    AgentStatus.IDLE -> Color(0xFF7DD3FC)
                    AgentStatus.PAUSED -> Color(0xFFFF7A90)
                }
            ),
    )
}

private fun statusIcon(status: ToolStatus): ImageVector = when (status) {
    ToolStatus.QUEUED -> Icons.Default.Schedule
    ToolStatus.RUNNING -> Icons.Default.Build
    ToolStatus.SUCCESS -> Icons.Default.CheckCircle
    ToolStatus.FAILED -> Icons.Default.Error
}

private fun statusColor(status: ToolStatus): Color = when (status) {
    ToolStatus.QUEUED -> Color(0xFF7DD3FC)
    ToolStatus.RUNNING -> Color(0xFFFFC857)
    ToolStatus.SUCCESS -> Color(0xFF80ED99)
    ToolStatus.FAILED -> Color(0xFFFF7A90)
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun createImageCaptureUri(context: Context, displayName: String): Uri? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return runCatching {
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()
}

private fun Uri.bestName(fallback: String): String =
    lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.ifBlank { null }
        ?: fallback
