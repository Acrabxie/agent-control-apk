package com.xiehaibo.agentcontrol

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Log
import android.util.Base64
import android.widget.Toast
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import java.io.File
import java.io.FileInputStream
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
    SETUP,
    PROJECT,
}

private const val BRIDGE_TAG = "AgentControlBridge"
private const val MESSAGE_SEND_TIMEOUT_MS = 315_000L
private const val ONBOARDING_PREFS = "agent_control_onboarding"
private const val ONBOARDING_COMPLETE_KEY = "onboarding_complete"
private const val ONBOARDING_REVISION_KEY = "onboarding_revision"
private const val ONBOARDING_REVISION = 1
private const val AGENT_CONTROL_REPO_URL = "https://github.com/Acrabxie/agent-control-apk"
private const val AGENT_CONTROL_PRIVACY_URL = "https://github.com/Acrabxie/agent-control-apk/blob/main/docs/privacy-policy.md"
private val LogoCanvas = Color(0xFFFFFAF1)
private val LogoSurface = Color(0xFFFFFCF7)
private val LogoSurfaceWarm = Color(0xFFFFF4E4)
private val LogoOrange = Color(0xFFF1B47E)
private val LogoYellow = Color(0xFFF5DE87)
private val WarmPrimary = Color(0xFFC86F2D)
private val WarmPrimaryDark = Color(0xFF7A3F18)
private val WarmInk = Color(0xFF2F251B)
private val WarmMuted = Color(0xFF756957)
private val WarmOutline = Color(0xFFE4C89F)
private val WarmOutlineSoft = Color(0xFFF0DDBC)
private val WarmUserBubble = Color(0xFFF0EEE8)
private val WarmError = Color(0xFFB45A4E)

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
    val colors = lightColorScheme(
        primary = WarmPrimary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDFC1),
        onPrimaryContainer = WarmPrimaryDark,
        inversePrimary = LogoOrange,
        secondary = Color(0xFF9A6B10),
        onSecondary = Color.White,
        secondaryContainer = LogoYellow,
        onSecondaryContainer = Color(0xFF473300),
        tertiary = Color(0xFFA45F25),
        onTertiary = Color.White,
        tertiaryContainer = LogoOrange,
        onTertiaryContainer = Color(0xFF452100),
        background = LogoCanvas,
        onBackground = WarmInk,
        surface = LogoSurface,
        onSurface = WarmInk,
        surfaceVariant = LogoSurfaceWarm,
        onSurfaceVariant = WarmMuted,
        surfaceTint = WarmPrimary,
        inverseSurface = WarmInk,
        inverseOnSurface = LogoCanvas,
        outline = WarmOutline,
        outlineVariant = WarmOutlineSoft,
        error = WarmError,
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD3),
        onErrorContainer = Color(0xFF5E160F),
        scrim = Color(0x66000000),
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
    val onboardingPrefs = remember(appContext) {
        appContext.getSharedPreferences(ONBOARDING_PREFS, Context.MODE_PRIVATE)
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(onboardingPrefs.getInt(ONBOARDING_REVISION_KEY, 0) < ONBOARDING_REVISION)
    }
    var onboardingPage by rememberSaveable { mutableStateOf(0) }

    fun completeOnboarding() {
        onboardingPrefs.edit()
            .putBoolean(ONBOARDING_COMPLETE_KEY, true)
            .putInt(ONBOARDING_REVISION_KEY, ONBOARDING_REVISION)
            .apply()
        showOnboarding = false
    }

    fun applyPairingLink(link: PairingDeepLink, source: String) {
        completeOnboarding()
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

    fun currentBridgeUrl(): String =
        store.pairingInfo.desktopUrl.ifBlank { store.desktopUrlDraft }.trim().trimEnd('/')

    fun runDiagnostics() {
        val desktopUrl = currentBridgeUrl()
        if (desktopUrl.isBlank()) {
            store.recordDiagnosticsFailure("Enter a desktop or relay address before running diagnostics.")
            panel = AppPanel.SETUP
            return
        }
        store.beginDiagnostics()
        panel = AppPanel.SETUP
        scope.launch {
            var health = store.latestBridgeHealth
            try {
                health = withTimeout(45_000) {
                    withContext(Dispatchers.IO) {
                        NetworkBridgeClient.fetchHealth(desktopUrl)
                    }
                }
                store.recordDiagnosticsHealth(health)
                var diagnostics: com.xiehaibo.agentcontrol.api.BridgeDiagnostics? = null
                val key = store.sessionKey
                if (store.pairingInfo.paired && key != null && store.deviceId.isNotBlank()) {
                    diagnostics = withTimeout(45_000) {
                        withContext(Dispatchers.IO) {
                            NetworkBridgeClient.fetchDiagnostics(
                                desktopUrl = desktopUrl,
                                deviceId = store.deviceId,
                                sessionKey = key,
                            )
                        }
                    }
                    val snapshot = withTimeout(45_000) {
                        withContext(Dispatchers.IO) {
                            NetworkBridgeClient.fetchSnapshot(
                                desktopUrl = desktopUrl,
                                deviceId = store.deviceId,
                                sessionKey = key,
                            )
                        }
                    }
                    store.applySnapshot(snapshot)
                }
                store.recordDiagnosticsSuccess(health, diagnostics)
            } catch (error: Throwable) {
                val message = error.message ?: "Diagnostics failed"
                if (message.contains("not_paired") || message.contains("HTTP 401")) {
                    store.recordDiagnosticsFailure("Diagnostics needs a paired encrypted session.")
                } else {
                    store.recordDiagnosticsFailure(message)
                }
            }
        }
    }

    fun sendStatusCommand() {
        val targetId = store.selectedTargetId.takeIf { target ->
            store.agents.any { it.id == target } || store.teams.any { it.id == target }
        } ?: "codex"
        store.prepareStatusDraft(targetId)
        panel = AppPanel.CHAT
        scope.launch {
            val message = store.consumeDraft() ?: return@launch
            val key = store.sessionKey
            try {
                if (store.pairingInfo.paired && key != null && store.deviceId.isNotBlank()) {
                    val desktopUrl = currentBridgeUrl()
                    val reply = withTimeout(MESSAGE_SEND_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            NetworkBridgeClient.sendMessage(
                                desktopUrl = desktopUrl,
                                deviceId = store.deviceId,
                                sessionKey = key,
                                payload = store.outboundPayload(message),
                            )
                        }
                    }
                    store.addRemoteReply(reply)
                    store.clearSendFailure()
                    runCatching {
                        val snapshot = withTimeout(45_000) {
                            withContext(Dispatchers.IO) {
                                NetworkBridgeClient.fetchSnapshot(
                                    desktopUrl = desktopUrl,
                                    deviceId = store.deviceId,
                                    sessionKey = key,
                                )
                            }
                        }
                        store.applySnapshot(snapshot)
                    }
                } else {
                    store.respondLocallyTo(message)
                }
            } catch (error: Throwable) {
                val messageText = error.message ?: "Send /status failed"
                store.rememberSendFailure(messageText)
                if (messageText.contains("not_paired") || messageText.contains("HTTP 401")) {
                    store.markPairingInvalid("Bridge session expired. Pair with the computer again, then resend /status.")
                } else {
                    store.addSystemMessage("Could not send /status: $messageText")
                }
            }
        }
    }

    fun copyTesterReport() {
        val report = store.testerReport(
            packageName = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
        )
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Agent Control tester report", report))
        store.addSystemMessage("Tester report copied.")
    }

    fun copyOnboardingText(label: String, text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        store.addSystemMessage("$label copied.")
    }

    LaunchedEffect(incomingPairingLink) {
        val link = incomingPairingLink ?: return@LaunchedEffect
        applyPairingLink(link, "Loaded")
        onPairingLinkConsumed()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
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
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = panel == AppPanel.CHAT,
                    onClick = { panel = AppPanel.CHAT },
                    icon = { Icon(Icons.Default.Terminal, contentDescription = null) },
                    label = { Text("Chat") },
                    colors = warmNavigationItemColors(),
                )
                NavigationBarItem(
                    selected = panel == AppPanel.SETUP,
                    onClick = { panel = AppPanel.SETUP },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("Setup") },
                    colors = warmNavigationItemColors(),
                )
                NavigationBarItem(
                    selected = panel == AppPanel.PROJECT,
                    onClick = { panel = AppPanel.PROJECT },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    label = { Text("Project") },
                    colors = warmNavigationItemColors(),
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
                AppPanel.SETUP -> SetupPanel(
                    store = store,
                    onStartPairing = { showPairDialog = true },
                    onRunDiagnostics = { runDiagnostics() },
                    onSendStatus = { sendStatusCommand() },
                    onCopyTesterReport = { copyTesterReport() },
                )
                AppPanel.PROJECT -> ProjectPanel(
                    store = store,
                    openChat = { panel = AppPanel.CHAT },
                )
            }
        }
    }

    if (showOnboarding) {
        SetupOnboarding(
            page = onboardingPage,
            onOpenRepository = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AGENT_CONTROL_REPO_URL)))
                }.onFailure {
                    store.addSystemMessage("Open $AGENT_CONTROL_REPO_URL on your computer.")
                }
            },
            onCopyText = { label, text -> copyOnboardingText(label, text) },
            onOpenPrivacyPolicy = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AGENT_CONTROL_PRIVACY_URL)))
                }.onFailure {
                    store.addSystemMessage("Privacy policy: $AGENT_CONTROL_PRIVACY_URL")
                }
            },
            onContinue = { onboardingPage = 1 },
            onBack = { onboardingPage = 0 },
            onPair = {
                completeOnboarding()
                showPairDialog = true
            },
            onSkip = { completeOnboarding() },
        )
    } else if (showPairDialog) {
        PairingDialog(
            store = store,
            busy = pairingBusy,
            scanBusy = scanBusy,
            error = pairingError,
            builtInRelayUrl = builtInRelayUrl,
            onScanQr = { startPairingQrScan() },
            onOpenPrivacyPolicy = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AGENT_CONTROL_PRIVACY_URL)))
                }.onFailure {
                    store.addSystemMessage("Privacy policy: $AGENT_CONTROL_PRIVACY_URL")
                }
            },
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
private fun warmNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun SetupPanel(
    store: AgentControlStore,
    onStartPairing: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onSendStatus: () -> Unit,
    onCopyTesterReport: () -> Unit,
) {
    val diagnostics = store.connectionDiagnostics()
    val checksById = diagnostics.checks.associateBy { it.id }
    val checklist = listOf(
        checksById["url_reachable"]?.copy(id = "install_bridge", label = "Install desktop bridge")
            ?: setupCheck("install_bridge", "Install desktop bridge"),
        checksById["pairing_state"] ?: setupCheck("pairing_state", "Pair phone"),
        checksById["bridge_health"]?.copy(id = "run_diagnostics", label = "Run diagnostics")
            ?: setupCheck("run_diagnostics", "Run diagnostics"),
        checksById["send_status"] ?: setupCheck("send_status", "Send /status"),
        checksById["attachment"] ?: setupCheck("attachment", "Test attachment"),
        checksById["reconnect"] ?: setupCheck("reconnect", "Test reconnect"),
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.46f)),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Setup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        diagnostics.summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (store.pairingInfo.paired) {
                            "${store.pairingInfo.desktopName ?: "Desktop"} · ${store.pairingInfo.desktopUrl}"
                        } else {
                            "Install the desktop bridge, then pair this phone with the QR code or 8-digit key."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onStartPairing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start pairing", maxLines = 1)
                    }
                    Button(
                        onClick = onRunDiagnostics,
                        modifier = Modifier.weight(1f),
                        enabled = !store.diagnosticsRunning,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (store.diagnosticsRunning) "Running..." else "Run diagnostics", maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onSendStatus,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Send /status", maxLines = 1)
                    }
                    Button(
                        onClick = onCopyTesterReport,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy report", maxLines = 1)
                    }
                }
            }
        }
        items(checklist, key = { it.id }) { check ->
            DiagnosticCheckRow(check)
        }
        item {
            Text(
                "Detailed checks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        items(diagnostics.checks, key = { "detail-${it.id}" }) { check ->
            DiagnosticCheckRow(check)
        }
        store.lastDiagnosticsError?.let { error ->
            item {
                Text(
                    error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun setupCheck(id: String, label: String) =
    com.xiehaibo.agentcontrol.api.DiagnosticCheck(id, label, "pending", "Not tested")

@Composable
private fun DiagnosticCheckRow(check: com.xiehaibo.agentcontrol.api.DiagnosticCheck) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, diagnosticColor(check.status).copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = diagnosticIcon(check.status),
                contentDescription = null,
                tint = diagnosticColor(check.status),
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(check.label, fontWeight = FontWeight.SemiBold)
                if (check.detail.isNotBlank()) {
                    Text(
                        check.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                check.status.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = diagnosticColor(check.status),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun diagnosticColor(status: String): Color = when (status.lowercase(Locale.getDefault())) {
    "pass" -> Color(0xFF5A8F58)
    "warn", "pending" -> Color(0xFFD4A850)
    "fail" -> WarmError
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun diagnosticIcon(status: String): ImageVector = when (status.lowercase(Locale.getDefault())) {
    "pass" -> Icons.Default.CheckCircle
    "fail" -> Icons.Default.Error
    "warn" -> Icons.Default.Security
    else -> Icons.Default.Schedule
}

@Composable
private fun SetupOnboarding(
    page: Int,
    onOpenRepository: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onPair: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Agent Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnboardingDot(active = page == 0)
                    OnboardingDot(active = page == 1)
                }
            }
            if (page == 0) {
                BackendInstallPage(
                    modifier = Modifier.weight(1f),
                    onOpenRepository = onOpenRepository,
                    onCopyText = onCopyText,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                    onContinue = onContinue,
                )
            } else {
                PairingStartPage(
                    modifier = Modifier.weight(1f),
                    onCopyText = onCopyText,
                    onBack = onBack,
                    onPair = onPair,
                    onSkip = onSkip,
                )
            }
        }
    }
}

@Composable
private fun OnboardingDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(if (active) 22.dp else 8.dp, 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
    )
}

@Composable
private fun BackendInstallPage(
    modifier: Modifier,
    onOpenRepository: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onContinue: () -> Unit,
) {
    val installPrompt = "Go to $AGENT_CONTROL_REPO_URL, read docs/self-hosted-relay.md, install/start the desktop bridge, deploy a self-hosted relay if remote access is needed, then open http://127.0.0.1:7149 for pairing."
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingHeroIcon(Icons.Default.Terminal)
        Text(
            "Install the desktop side first",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Open the GitHub repo on your computer and ask your coding agent to install the bridge and relay pieces before pairing this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OnboardingInfoBlock(
            title = "Repository",
            body = AGENT_CONTROL_REPO_URL,
            onCopy = { onCopyText("Repository", AGENT_CONTROL_REPO_URL) },
        )
        OnboardingInfoBlock(
            title = "Prompt for your agent",
            body = installPrompt,
            onCopy = { onCopyText("Agent setup prompt", installPrompt) },
        )
        Spacer(Modifier.heightIn(min = 4.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenRepository,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open GitHub repo")
        }
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPrivacyPolicy,
        ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Privacy policy")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinue,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Continue to pairing")
        }
    }
}

@Composable
private fun PairingStartPage(
    modifier: Modifier,
    onCopyText: (String, String) -> Unit,
    onBack: () -> Unit,
    onPair: () -> Unit,
    onSkip: () -> Unit,
) {
    val desktopPairingUrl = "http://127.0.0.1:7149"
    val remoteAccessPrompt = "Use the self-hosted Cloudflare relay URL shown by your agent, such as https://agent-control-relay.<account>.workers.dev."
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OnboardingHeroIcon(Icons.Default.QrCodeScanner)
        Text(
            "Pair this phone",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "When the desktop bridge is running, open http://127.0.0.1:7149 on that computer. Scan the QR code, or enter the relay/direct address and 8-digit key manually.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OnboardingInfoBlock(
            title = "Remote access",
            body = remoteAccessPrompt,
            onCopy = { onCopyText("Remote access note", remoteAccessPrompt) },
        )
        OnboardingInfoBlock(
            title = "Direct / VPN",
            body = "Open $desktopPairingUrl on the desktop. Use http://<desktop-ip>:7149 or http://<tailscale-ip>:7149 only when the phone can reach the computer directly.",
            onCopy = { onCopyText("Desktop pairing URL", desktopPairingUrl) },
        )
        Spacer(Modifier.heightIn(min = 4.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onPair,
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start pairing")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            TextButton(onClick = onSkip) {
                Text("Skip for now")
            }
        }
    }
}

@Composable
private fun OnboardingHeroIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun OnboardingInfoBlock(
    title: String,
    body: String,
    onCopy: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.44f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (onCopy != null) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy $title", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    var showAgentDetails by rememberSaveable { mutableStateOf(false) }
    var showTeamDetails by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraName by rememberSaveable { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            store.queueAttachment(
                uri = it.toString(),
                name = it.bestName("file"),
                mimeType = context.contentResolver.getType(it) ?: "application/octet-stream",
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
                store.ensureConversationFor(agent.id)
                openConversationId = agent.id
            },
            onOpenTeam = { team ->
                store.selectedTargetId = team.id
                store.ensureConversationFor(team.id)
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
        store.messagesForActiveConversation(activeTeam.id)
    } else {
        store.messagesForActiveConversation(activeAgent!!.id)
    }
    val activeTyping = store.targetIsTyping(activeTargetId)

    LaunchedEffect(activeTargetId, conversationMessages.size) {
        store.markConversationSeen(activeTargetId)
        if (conversationMessages.isNotEmpty()) {
            messageListState.animateScrollToItem(conversationMessages.lastIndex)
        }
    }

    fun requestScrollToBottom() {
        scope.launch {
            val latestMessages = if (activeTeam != null) {
                store.messagesForActiveConversation(activeTeam.id)
            } else {
                store.messagesForActiveConversation(activeAgent!!.id)
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
                isTyping = activeTyping,
                onBack = { openConversationId = null },
                onNewConversation = {
                    store.startNewConversation(activeTeam.id)
                    focusManager.clearFocus()
                },
                onInfo = { showTeamDetails = true },
            )
            TeamSharedProfile(activeTeam)
        } else {
            ConversationHeader(
                agent = activeAgent!!,
                parentName = activeAgent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
                isTyping = activeTyping,
                onBack = { openConversationId = null },
                onNewConversation = {
                    store.startNewConversation(activeAgent.id)
                    focusManager.clearFocus()
                },
                onInfo = { showAgentDetails = true },
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
        }
        PendingAttachments(
            attachments = store.pendingAttachments,
            onRemove = { store.removePendingAttachment(it) },
        )
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
                            store.clearSendFailure()
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
                        store.rememberSendFailure(errorText)
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
    if (showAgentDetails && activeAgent != null) {
        AgentDetailsDialog(
            store = store,
            agent = activeAgent,
            parentName = activeAgent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
            onDismiss = { showAgentDetails = false },
        )
    }
    if (showTeamDetails && activeTeam != null) {
        TeamDetailsDialog(
            store = store,
            team = activeTeam,
            onDismiss = { showTeamDetails = false },
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
                lastMessage = store.lastMessageForActiveConversation(team.id),
                isTyping = store.targetIsTyping(team.id),
                unreadCount = store.unreadCompletedCount(team.id),
                onClick = { onOpenTeam(team) },
            )
        }
        items(store.agents, key = { it.id }) { agent ->
            ConversationRow(
                agent = agent,
                parentName = agent.parentId?.let { id -> store.agents.firstOrNull { it.id == id }?.name },
                lastMessage = store.lastMessageForActiveConversation(agent.id),
                isTyping = store.targetIsTyping(agent.id),
                unreadCount = store.unreadCompletedCount(agent.id),
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
    isTyping: Boolean,
    unreadCount: Int,
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
    val displayPreview = if (isTyping) "typing..." else preview
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LogoYellow.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                TeamAvatar()
                if (unreadCount > 0) UnreadDot(Modifier.align(Alignment.TopEnd))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(team.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        if (isTyping) "typing" else lastMessage?.let { formatTime(it.createdAt) } ?: "${team.memberIds.size} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    memberNames.take(3).joinToString(", ").ifBlank { "agent team" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    displayPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unreadCount > 0) {
                UnreadBadge(unreadCount)
            } else {
                StatusDot(AgentStatus.ONLINE)
            }
        }
    }
}

@Composable
private fun ConversationRow(
    agent: AgentNode,
    parentName: String?,
    lastMessage: ChatMessage?,
    isTyping: Boolean,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    val preview = lastMessage?.let { message ->
        when {
            message.kind == MessageKind.USER -> "You: ${message.text}"
            message.text.isNotBlank() -> message.text
            else -> "Attachment"
        }
    } ?: agent.role
    val displayPreview = if (isTyping) "typing..." else preview
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                AgentAvatar(agent)
                if (unreadCount > 0) UnreadDot(Modifier.align(Alignment.TopEnd))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(agent.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        if (isTyping) "typing" else lastMessage?.let { formatTime(it.createdAt) } ?: agent.status.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    displayPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unreadCount > 0) {
                UnreadBadge(unreadCount)
            } else {
                StatusDot(agent.status)
            }
        }
    }
}

@Composable
private fun ConversationHeader(
    agent: AgentNode,
    parentName: String?,
    isTyping: Boolean,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
    onInfo: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f)),
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
                    if (isTyping) {
                        "typing..."
                    } else {
                        parentName?.let { "under $it" } ?: "${agent.kind.name.lowercase()} · ${agent.status.name.lowercase()}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                modifier = Modifier.semantics { contentDescription = "New conversation" },
                onClick = onNewConversation,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
            IconButton(
                modifier = Modifier.semantics { contentDescription = "Agent details" },
                onClick = onInfo,
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
            }
            StatusDot(if (isTyping) AgentStatus.BUSY else agent.status)
        }
    }
}

@Composable
private fun TeamConversationHeader(
    team: AgentTeam,
    memberNames: List<String>,
    isTyping: Boolean,
    onBack: () -> Unit,
    onNewConversation: () -> Unit,
    onInfo: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LogoYellow.copy(alpha = 0.72f)),
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
                    if (isTyping) "typing..." else "${team.memberIds.size} members · ${memberNames.take(3).joinToString(", ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                modifier = Modifier.semantics { contentDescription = "New conversation" },
                onClick = onNewConversation,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
            IconButton(
                modifier = Modifier.semantics { contentDescription = "Team details" },
                onClick = onInfo,
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
            }
            StatusDot(if (isTyping) AgentStatus.BUSY else AgentStatus.ONLINE)
        }
    }
}

@Composable
private fun TeamSharedProfile(team: AgentTeam) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
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
private fun AgentDetailsDialog(
    store: AgentControlStore,
    agent: AgentNode,
    parentName: String?,
    onDismiss: () -> Unit,
) {
    val runtime = store.runtimeSettingsForTarget(agent.id)
    val permission = store.permissionForTarget(agent.id)
    val diagnostic = store.latestBridgeDiagnostics?.agents?.firstOrNull { it.id == agent.id }
    val teamName = store.teams.firstOrNull { it.id == agent.teamId }?.name ?: agent.teamId
    val recentMessage = store.messagesForActiveConversation(agent.id).asReversed().firstOrNull { it.kind == MessageKind.AGENT }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AgentAvatar(agent) },
        title = { Text(agent.name) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailLine("Identity", "${agent.kind.name.lowercase()} · ${agent.status.name.lowercase()}")
                DetailLine("Parent", parentName ?: "none")
                DetailLine("Team", teamName)
                DetailLine("Role", agent.role)
                DetailLine("Tools", agent.tools.joinToString(", ").ifBlank { "none reported" })
                DetailLine("Slash commands", agent.slashCommands.joinToString(" ") { it.trigger }.ifBlank { "none reported" })
                RuntimeSettingRow(
                    label = "Model",
                    value = runtimeLabel(runtime.modelOptions, runtime.model, fallbackModelLabel(runtime.model)),
                    options = runtime.modelOptions.ifEmpty { listOf(RuntimeOption(runtime.model, fallbackModelLabel(runtime.model))) },
                    selectedId = runtime.model,
                    onSelect = { store.updateModelForTarget(agent.id, it) },
                )
                RuntimeSettingRow(
                    label = "Reasoning",
                    value = runtimeLabel(runtime.reasoningOptions, runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort)),
                    options = runtime.reasoningOptions.ifEmpty { listOf(RuntimeOption(runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort))) },
                    selectedId = runtime.reasoningEffort,
                    onSelect = { store.updateReasoningForTarget(agent.id, it) },
                )
                RuntimeSettingRow(
                    label = "Permissions",
                    value = runtimeLabel(runtime.permissionOptions, permission, fallbackPermissionLabel(permission)),
                    options = runtime.permissionOptions.ifEmpty {
                        listOf(
                            RuntimeOption("read-only", "Read Only"),
                            RuntimeOption("workspace-write", "Workspace Write"),
                            RuntimeOption("full-access", "Full Access"),
                        )
                    },
                    selectedId = permission,
                    onSelect = { store.updatePermissionForTarget(agent.id, it) },
                )
                ContextMeter(
                    progress = (runtime.contextUsedTokens.toFloat() / runtime.contextLimitTokens.coerceAtLeast(1)).coerceIn(0f, 1f),
                    usedTokens = runtime.contextUsedTokens,
                    limitTokens = runtime.contextLimitTokens,
                )
                DetailLine("Recent action", diagnostic?.lastAction?.ifBlank { null } ?: recentMessage?.toolCalls?.lastOrNull()?.toolName ?: "none")
                DetailLine("Recent error", diagnostic?.lastError?.ifBlank { null } ?: store.lastSendFailure ?: "none")
                DetailLine("Diagnostic state", diagnostic?.diagnosticState ?: store.connectionDiagnostics().summary)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun TeamDetailsDialog(
    store: AgentControlStore,
    team: AgentTeam,
    onDismiss: () -> Unit,
) {
    val admin = store.agents.firstOrNull { it.id == team.adminAgentId }
    val runtime = store.runtimeSettingsForTarget(team.id)
    val permission = store.permissionForTarget(team.id)
    val recentActivity = store.messagesForActiveConversation(team.id)
        .asReversed()
        .take(3)
        .joinToString(" / ") { message ->
            val author = store.agents.firstOrNull { it.id == message.authorId }?.name ?: if (message.authorId == "you") "You" else message.authorId
            "$author: ${displayMessageText(message.text).take(64)}"
        }
        .ifBlank { "none" }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { TeamAvatar() },
        title = { Text(team.name) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailLine("Admin", admin?.name ?: team.adminAgentId)
                DetailLine("Members", team.memberIds.map { memberId -> store.agents.firstOrNull { it.id == memberId }?.name ?: memberId }.joinToString(", "))
                DetailLine("Purpose", team.purpose.ifBlank { team.sharedProfile })
                DetailLine("Shared documents", team.sharedDocuments.joinToString(", ").ifBlank { "none" })
                DetailLine("Posting", if (team.canAgentsPost) "agents can post to this team" else "agent posting paused")
                RuntimeSettingRow(
                    label = "Model",
                    value = runtimeLabel(runtime.modelOptions, runtime.model, fallbackModelLabel(runtime.model)),
                    options = runtime.modelOptions.ifEmpty { listOf(RuntimeOption(runtime.model, fallbackModelLabel(runtime.model))) },
                    selectedId = runtime.model,
                    onSelect = { store.updateModelForTarget(team.id, it) },
                )
                RuntimeSettingRow(
                    label = "Reasoning",
                    value = runtimeLabel(runtime.reasoningOptions, runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort)),
                    options = runtime.reasoningOptions.ifEmpty { listOf(RuntimeOption(runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort))) },
                    selectedId = runtime.reasoningEffort,
                    onSelect = { store.updateReasoningForTarget(team.id, it) },
                )
                RuntimeSettingRow(
                    label = "Permissions",
                    value = runtimeLabel(runtime.permissionOptions, permission, fallbackPermissionLabel(permission)),
                    options = runtime.permissionOptions.ifEmpty {
                        listOf(
                            RuntimeOption("read-only", "Read Only"),
                            RuntimeOption("workspace-write", "Workspace Write"),
                            RuntimeOption("full-access", "Full Access"),
                        )
                    },
                    selectedId = permission,
                    onSelect = { store.updatePermissionForTarget(team.id, it) },
                )
                DetailLine("Recent activity", recentActivity)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun RuntimeSettingRow(
    label: String,
    value: String,
    options: List<RuntimeOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = true },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)),
    ) {
        Box {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.2f),
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            RuntimeDropdown(
                expanded = expanded,
                options = options,
                selectedId = selectedId,
                onDismiss = { expanded = false },
                onSelect = {
                    onSelect(it)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            .background(LogoYellow.copy(alpha = 0.46f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
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
    val conversationId = message.conversationId.ifBlank { store.conversationIdFor(targetId) }
    return store.messages.asReversed().firstOrNull { candidate ->
        candidate.kind == MessageKind.AGENT &&
            candidate.createdAt >= message.createdAt &&
            store.messageBelongsToConversation(candidate, targetId, conversationId) &&
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
            color = WarmUserBubble,
            border = BorderStroke(1.dp, Color(0xFFE1DED6)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectableMessageText(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TransferList(message.attachments)
            }
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
                border = BorderStroke(1.dp, agentBubbleBorderColor(agent)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectableMessageText(
                        text = displayMessageText(message.text),
                        color = agentBubbleTextColor(agent),
                    )
                    TransferList(message.attachments)
                }
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
    val colors = actionColors(agent)
    val titleColor = actionTextColor(toolCall.status, colors).copy(alpha = alpha)
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
            tint = actionTextColor(toolCall.status, colors),
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
                    color = colors.detail.copy(alpha = if (isRunning) alpha else 0.82f),
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
            SelectionContainer {
                Text(
                    displayMessageText(message.text),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectableMessageText(
    text: String,
    color: Color,
) {
    SelectionContainer {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.bodyLarge,
        )
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
    AgentKind.CODEX -> WarmInk
    AgentKind.CLAUDE_CODE -> Color(0xFFC15F3C)
    AgentKind.ANTIGRAVITY -> Color(0xFF6C4DD5)
    AgentKind.GEMINI_CLI -> Color(0xFF4285F4)
    AgentKind.OPENCODE -> Color(0xFF00A67E)
    AgentKind.SUBAGENT -> LogoOrange
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    AgentKind.CODEX -> Color(0xFFF2F0EB)
    AgentKind.CLAUDE_CODE -> Color(0xFFFFEFE2)
    AgentKind.ANTIGRAVITY -> Color(0xFFF1EDFF)
    AgentKind.GEMINI_CLI -> Color(0xFFEAF3FF)
    AgentKind.OPENCODE -> Color(0xFFE8F7F0)
    AgentKind.SUBAGENT -> Color(0xFFF7EEDC)
    null -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun agentBubbleBorderColor(agent: AgentNode?): Color = when (agent?.kind) {
    AgentKind.CODEX -> Color(0xFFD7D0C5)
    AgentKind.CLAUDE_CODE -> Color(0xFFF0C1A0)
    AgentKind.ANTIGRAVITY -> Color(0xFFD6CCFF)
    AgentKind.GEMINI_CLI -> Color(0xFFC7DCFF)
    AgentKind.OPENCODE -> Color(0xFFBFE6D5)
    AgentKind.SUBAGENT -> WarmOutline
    null -> MaterialTheme.colorScheme.outline.copy(alpha = 0.52f)
}

@Composable
private fun agentBubbleTextColor(agent: AgentNode?): Color = when (agent?.kind) {
    AgentKind.CLAUDE_CODE -> Color(0xFF4A2414)
    AgentKind.ANTIGRAVITY -> Color(0xFF2F275C)
    AgentKind.GEMINI_CLI -> Color(0xFF17345C)
    AgentKind.OPENCODE -> Color(0xFF123E31)
    else -> MaterialTheme.colorScheme.onSurface
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
        container = Color(0xFFF2F0EB),
        border = Color(0xFFD7D0C5),
        title = Color(0xFF46413A),
        detail = Color(0xFF7E776D),
    )
    AgentKind.CLAUDE_CODE -> ActionColors(
        container = Color(0xFFFFEFE2),
        border = Color(0xFFF0C1A0),
        title = Color(0xFF8A4726),
        detail = Color(0xFF9A7057),
    )
    AgentKind.GEMINI_CLI -> ActionColors(
        container = Color(0xFFEAF3FF),
        border = Color(0xFFC7DCFF),
        title = Color(0xFF315F9C),
        detail = Color(0xFF6680A7),
    )
    AgentKind.ANTIGRAVITY -> ActionColors(
        container = Color(0xFFF1EDFF),
        border = Color(0xFFD6CCFF),
        title = Color(0xFF5D4EBC),
        detail = Color(0xFF7D72A8),
    )
    AgentKind.OPENCODE -> ActionColors(
        container = Color(0xFFE8F7F0),
        border = Color(0xFFBFE6D5),
        title = Color(0xFF1F795F),
        detail = Color(0xFF5C8374),
    )
    AgentKind.SUBAGENT, null -> ActionColors(
        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
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
        "ask" in name || "question" in name || "confirm" in name -> Icons.Default.Person
        "prompt" in name || "plan" in name -> Icons.Default.Schedule
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
        "ask" in name || "question" in name || "confirm" in name -> "Ask user"
        "model_fallback" in name -> "Switch model"
        "model" in name -> "Wait for model"
        "file" in name || "attachment" in name -> "Send file"
        "permission" in name -> "Set permissions"
        "auth" in name -> "Check auth"
        "memory" in name -> "Read memory"
        "context" in name -> "Prepare context"
        "compact" in name -> "Compress context"
        "create" in name || "spawn" in name -> "Create"
        "edit" in name || "patch" in name || "save" in name || "write" in name -> "Edit"
        "search" in name -> "Search"
        "read" in name -> "Read"
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

private fun actionTextColor(status: ToolStatus, colors: ActionColors): Color = when (status) {
    ToolStatus.QUEUED, ToolStatus.RUNNING -> colors.detail
    ToolStatus.SUCCESS -> colors.title
    ToolStatus.FAILED -> WarmError
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
        "asking user...",
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
    if (transfers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        transfers.forEach { transfer ->
            AttachmentTile(
                transfer = transfer,
                onRemove = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
            )
        }
    }
}

@Composable
private fun PendingAttachments(
    attachments: List<FileTransfer>,
    onRemove: (String) -> Unit,
) {
    if (attachments.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentTile(
                transfer = attachment,
                onRemove = { onRemove(attachment.id) },
                modifier = Modifier.width(168.dp),
            )
        }
    }
}

@Composable
private fun AttachmentTile(
    transfer: FileTransfer,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val thumbnail = rememberAttachmentThumbnail(transfer)
    Surface(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val saved = saveTransferToDownloads(context, transfer)
                Toast.makeText(
                    context,
                    if (saved) "Saved to Downloads" else "Could not save this file",
                    Toast.LENGTH_SHORT,
                ).show()
            },
        ),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)),
    ) {
        Box {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AttachmentPreview(transfer, thumbnail)
                Column(modifier = Modifier.weight(1f)) {
                    Text(transfer.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${transfer.direction} · ${transfer.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (transfer.sizeLabel.isNotBlank()) {
                        Text(
                            transfer.sizeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (onRemove != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.56f),
                ) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    transfer: FileTransfer,
    thumbnail: ImageBitmap?,
) {
    Surface(
        modifier = Modifier.size(46.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    if (transfer.mimeType.startsWith("video/")) Icons.Default.PhotoCamera else Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun rememberAttachmentThumbnail(transfer: FileTransfer): ImageBitmap? {
    val context = LocalContext.current
    var thumbnail by remember(transfer.uri, transfer.mimeType, transfer.contentBase64) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(transfer.uri, transfer.mimeType, transfer.contentBase64) {
        thumbnail = withContext(Dispatchers.IO) {
            loadAttachmentThumbnail(context, transfer)?.asImageBitmap()
        }
    }
    return thumbnail
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
    var permissionMenuOpen by remember { mutableStateOf(false) }
    val runtime = store.runtimeSettingsForTarget(store.selectedTargetId)
    val modelLabel = runtimeLabel(runtime.modelOptions, runtime.model, fallbackModelLabel(runtime.model))
    val reasoningLabel = runtimeLabel(runtime.reasoningOptions, runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort))
    val targetPermission = store.permissionForTarget(store.selectedTargetId)
    val permissionLabel = runtimeLabel(runtime.permissionOptions, targetPermission, fallbackPermissionLabel(targetPermission))
    val planModeEnabled = targetPermission == "read-only"
    val contextProgress = (runtime.contextUsedTokens.toFloat() / runtime.contextLimitTokens.coerceAtLeast(1)).coerceIn(0f, 1f)
    val placeholderAlpha = rememberPulseAlpha(isSending)
    val modelOptions = runtime.modelOptions.ifEmpty {
        listOf(
            RuntimeOption(runtime.model, fallbackModelLabel(runtime.model)),
        )
    }
    val reasoningOptions = runtime.reasoningOptions.ifEmpty {
        listOf(
            RuntimeOption(runtime.reasoningEffort, fallbackReasoningLabel(runtime.reasoningEffort)),
        )
    }
    val permissionOptions = runtime.permissionOptions.ifEmpty {
        listOf(
            RuntimeOption("read-only", "Read Only"),
            RuntimeOption("workspace-write", "Workspace Write"),
            RuntimeOption("full-access", "Full Access"),
        )
    }
    fun setPlanMode(enabled: Boolean) {
        store.updatePermissionForTarget(store.selectedTargetId, if (enabled) "read-only" else "workspace-write")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, LogoOrange.copy(alpha = 0.56f)),
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
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "More actions",
                            tint = MaterialTheme.colorScheme.primary,
                        )
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
                            text = {
                                Column {
                                    Text("Permissions", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        permissionLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                toolsMenuOpen = false
                                permissionMenuOpen = true
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
                        selectedId = runtime.model,
                        onDismiss = { modelMenuOpen = false },
                        onSelect = {
                            store.updateModelForTarget(store.selectedTargetId, it)
                            modelMenuOpen = false
                        },
                    )
                    RuntimeDropdown(
                        expanded = reasoningMenuOpen,
                        options = reasoningOptions,
                        selectedId = runtime.reasoningEffort,
                        onDismiss = { reasoningMenuOpen = false },
                        onSelect = {
                            store.updateReasoningForTarget(store.selectedTargetId, it)
                            reasoningMenuOpen = false
                        },
                    )
                    RuntimeDropdown(
                        expanded = permissionMenuOpen,
                        options = permissionOptions,
                        selectedId = targetPermission,
                        onDismiss = { permissionMenuOpen = false },
                        onSelect = {
                            store.updatePermissionForTarget(store.selectedTargetId, it)
                            permissionMenuOpen = false
                        },
                    )
                }
                ContextMeter(
                    progress = contextProgress,
                    usedTokens = runtime.contextUsedTokens,
                    limitTokens = runtime.contextLimitTokens,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onVoiceInput, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice input",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.84f),
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
    "claude-sonnet-4-6" -> "Claude Sonnet 4.6"
    "claude-opus-4-6" -> "Claude Opus 4.6"
    "gemini-2.5-flash" -> "Gemini 2.5 Flash"
    "gemini-2.5-pro" -> "Gemini 2.5 Pro"
    "gemini-3-flash-preview" -> "Gemini 3 Flash"
    "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro"
    "deepseek/deepseek-v4-pro" -> "DeepSeek V4-Pro"
    "openrouter/deepseek/deepseek-v3.2" -> "DeepSeek V3.2"
    "openrouter/google/gemini-3-flash-preview" -> "Gemini 3 Flash"
    "openrouter/anthropic/claude-opus-4.6" -> "Claude Opus 4.6"
    else -> id
}

private fun fallbackReasoningLabel(id: String): String = when (id) {
    "low" -> "Low"
    "medium" -> "Medium"
    "high" -> "High"
    "xhigh" -> "Extra High"
    "max" -> "Max"
    "minimal" -> "Minimal"
    "off" -> "Off"
    "default" -> "Default"
    else -> id
}

private fun fallbackPermissionLabel(id: String): String = when (id) {
    "read-only" -> "Read Only"
    "workspace-write" -> "Workspace Write"
    "full-access" -> "Full Access"
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
    val reports = agentReportDocuments(store)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = openChat) {
                Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open chat")
            }
        }
        Text("Recent Work", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(reports, key = { it.id }) { report ->
                AgentWorkReportCard(report)
            }
        }
    }
}

@Composable
private fun AgentWorkReportCard(report: ProjectDocument) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        report.path.removePrefix("agent-report://"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(formatTime(report.updatedAt), style = MaterialTheme.typography.labelSmall)
            }
            SelectionContainer {
                Text(
                    report.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private fun agentReportDocuments(store: AgentControlStore): List<ProjectDocument> {
    val bridgeReports = store.documents.filter {
        it.path.startsWith("agent-report://") || it.id.startsWith("agent-report-")
    }
    if (bridgeReports.isNotEmpty()) return bridgeReports
    return store.agents.map { agent ->
        val recentMessages = store.messagesForActiveConversation(agent.id)
            .asReversed()
            .take(4)
            .map { message ->
                val who = if (message.kind == MessageKind.USER) "You" else agent.name
                "- $who: ${displayMessageText(message.text).take(140)}"
            }
        val recentAction = store.messagesForActiveConversation(agent.id)
            .asReversed()
            .flatMap { it.toolCalls.asReversed() }
            .firstOrNull()
        ProjectDocument(
            id = "agent-report-${agent.id}",
            title = agent.name,
            path = "agent-report://${agent.id}",
            editable = false,
            updatedAt = recentAction?.startedAt ?: store.messagesForActiveConversation(agent.id).lastOrNull()?.createdAt ?: System.currentTimeMillis(),
            content = listOf(
                "${agent.status.name.lowercase()} · ${agent.role}",
                "Recent action: ${recentAction?.let { actionTitle(it, agent) } ?: "none"}",
                "Recent messages:",
                recentMessages.ifEmpty { listOf("- No recent work in this conversation.") }.joinToString("\n"),
            ).joinToString("\n"),
        )
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
    onOpenPrivacyPolicy: () -> Unit,
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
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
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
                                Text("Default relay", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Use this app build's relay URL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    TextButton(onClick = { useCustomAddress = true }, enabled = !busy) {
                        Text("Use self-hosted or direct address")
                    }
                } else {
                    OutlinedTextField(
                        value = store.desktopUrlDraft,
                        onValueChange = { store.desktopUrlDraft = it },
                        label = { Text("Computer or relay address") },
                        placeholder = { Text("https://agent-control-relay.<account>.workers.dev") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Text(
                        "Use a self-hosted Cloudflare relay for remote access, or a LAN/Tailscale/ZeroTier desktop URL for direct mode.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (hasBuiltInRelay) {
                        TextButton(
                            onClick = {
                                useCustomAddress = false
                                store.desktopUrlDraft = builtInRelayUrl
                            },
                            enabled = !busy,
                        ) {
                            Text("Use default relay")
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
                TextButton(onClick = onOpenPrivacyPolicy, enabled = !busy) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Privacy policy")
                }
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
                    AgentStatus.ONLINE -> Color(0xFF74A872)
                    AgentStatus.BUSY -> WarmPrimary
                    AgentStatus.IDLE -> Color(0xFFD4A850)
                    AgentStatus.PAUSED -> WarmError
                }
            ),
    )
}

@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(Color(0xFFD33F35)),
    )
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFFD33F35)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun statusIcon(status: ToolStatus): ImageVector = when (status) {
    ToolStatus.QUEUED -> Icons.Default.Schedule
    ToolStatus.RUNNING -> Icons.Default.Build
    ToolStatus.SUCCESS -> Icons.Default.CheckCircle
    ToolStatus.FAILED -> Icons.Default.Error
}

private fun statusColor(status: ToolStatus): Color = when (status) {
    ToolStatus.QUEUED -> Color(0xFFD4A850)
    ToolStatus.RUNNING -> WarmPrimary
    ToolStatus.SUCCESS -> Color(0xFF74A872)
    ToolStatus.FAILED -> WarmError
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

private fun loadAttachmentThumbnail(context: Context, transfer: FileTransfer): Bitmap? =
    runCatching {
        when {
            transfer.mimeType.startsWith("image/") -> loadImageBitmap(context, transfer)
            transfer.mimeType.startsWith("video/") -> loadVideoFrame(context, transfer)
            else -> null
        }?.let { scaleThumbnail(it) }
    }.getOrNull()

private fun loadImageBitmap(context: Context, transfer: FileTransfer): Bitmap? {
    val bytes = transfer.inlineBytes()
    if (bytes != null) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val uri = transfer.safeUri() ?: return null
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}

private fun loadVideoFrame(context: Context, transfer: FileTransfer): Bitmap? {
    val retriever = MediaMetadataRetriever()
    var tempFile = transfer.inlineBytes()?.let { bytes ->
        File.createTempFile("agent-control-video-", ".tmp", context.cacheDir).apply {
            writeBytes(bytes)
            deleteOnExit()
        }
    }
    return try {
        when {
            tempFile != null -> retriever.setDataSource(tempFile.absolutePath)
            transfer.uri.startsWith("/") -> retriever.setDataSource(transfer.uri)
            else -> transfer.safeUri()?.let { uri ->
                runCatching { retriever.setDataSource(context, uri) }
                    .getOrElse {
                        tempFile = copyUriToCache(context, uri, "agent-control-video-", ".tmp") ?: throw it
                        retriever.setDataSource(tempFile.absolutePath)
                    }
            } ?: return null
        }
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        runCatching { retriever.release() }
        tempFile?.delete()
    }
}

private fun scaleThumbnail(bitmap: Bitmap): Bitmap {
    val width = bitmap.width.coerceAtLeast(1)
    val height = bitmap.height.coerceAtLeast(1)
    val maxSide = maxOf(width, height)
    if (maxSide <= 192) return bitmap
    val scale = 192f / maxSide
    return Bitmap.createScaledBitmap(bitmap, (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1), true)
}

private fun FileTransfer.inlineBytes(): ByteArray? {
    val raw = when {
        contentBase64.isNotBlank() -> contentBase64
        uri.startsWith("data:", ignoreCase = true) && ";base64," in uri -> uri.substringAfter(";base64,")
        else -> return null
    }
    return runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()
}

private fun FileTransfer.safeUri(): Uri? =
    runCatching {
        when {
            uri.isBlank() -> null
            uri.startsWith("/") -> Uri.fromFile(File(uri))
            else -> Uri.parse(uri)
        }
    }.getOrNull()

private fun saveTransferToDownloads(context: Context, transfer: FileTransfer): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val resolver = context.contentResolver
    val displayName = transfer.name.ifBlank { "agent-control-${System.currentTimeMillis()}" }
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
        put(MediaStore.Downloads.MIME_TYPE, transfer.mimeType.ifBlank { "application/octet-stream" })
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        resolver.openOutputStream(destination)?.use { output ->
            val inline = transfer.inlineBytes()
            if (inline != null) {
                output.write(inline)
            } else if (transfer.uri.startsWith("/")) {
                FileInputStream(File(transfer.uri)).use { it.copyTo(output) }
            } else {
                val uri = transfer.safeUri() ?: error("missing source uri")
                resolver.openInputStream(uri)?.use { it.copyTo(output) } ?: error("missing source stream")
            }
        } ?: error("missing destination stream")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(destination, values, null, null)
        true
    }.getOrElse {
        runCatching { resolver.delete(destination, null, null) }
        false
    }
}

private fun copyUriToCache(context: Context, uri: Uri, prefix: String, suffix: String): File? =
    runCatching {
        val file = File.createTempFile(prefix, suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        file
    }.getOrNull()
