package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiLiveConnectionState
import com.example.data.model.AudioPlaybackState
import com.example.data.model.ChatMessage
import com.example.data.model.SenderType
import com.example.service.SanaBackgroundService
import com.example.session.SanaSessionManager
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.MemoryDialog
import com.example.ui.components.PermissionCenterDialog
import com.example.ui.components.SanaAvatarVisualizer
import com.example.ui.components.SettingsSheet
import com.example.ui.components.VoiceDiagnosticsDialog
import com.example.ui.components.VoiceStudioDialog
import com.example.ui.theme.SanaGold
import com.example.ui.theme.SanaPeachAccent
import com.example.ui.theme.SanaPinkPrimary
import com.example.ui.theme.SanaPinkSecondary
import com.example.ui.theme.SanaPurpleDark
import com.example.ui.theme.SanaRomanticRed
import com.example.ui.theme.SanaSoftWhite
import com.example.ui.theme.SanaSubtext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SanaMainScreen(sessionManager: SanaSessionManager) {
    val context = LocalContext.current

    // State bindings from Central Engine
    val nickname by sessionManager.nickname.collectAsState()
    val selectedVoice by sessionManager.selectedVoice.collectAsState()
    val voiceMode by sessionManager.voiceMode.collectAsState()
    val isRomanticMode by sessionManager.isRomanticMode.collectAsState()
    val selectedLanguage by sessionManager.selectedLanguage.collectAsState()
    val playbackState by sessionManager.playbackState.collectAsState()
    val isMemoryEnabled by sessionManager.isMemoryEnabled.collectAsState()
    val wakeWordEnabled by sessionManager.wakeWordEnabled.collectAsState()
    val backgroundAssistantEnabled by sessionManager.backgroundAssistantEnabled.collectAsState()
    val voiceTestPassed by sessionManager.voiceTestPassed.collectAsState()
    val voiceError by sessionManager.voiceError.collectAsState()
    val diagnostics by sessionManager.diagnostics.collectAsState()
    val messages by sessionManager.messages.collectAsState()
    val persistentMemory by sessionManager.persistentMemory.collectAsState()

    val isSpeaking by sessionManager.audioManager.isSpeaking.collectAsState()
    val isListening by sessionManager.audioManager.isListening.collectAsState()
    val audioLevel by sessionManager.audioManager.audioVisualizerLevel.collectAsState()
    val micError by sessionManager.audioManager.micError.collectAsState()
    val isLiveConversationActive by sessionManager.isLiveConversationActive.collectAsState()

    // Dialog sheets visibility
    var showVoiceStudio by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    val pendingConfirmation by sessionManager.pendingConfirmation.collectAsState()
    val confirmationEnabled by sessionManager.confirmationEnabled.collectAsState()
    val confirmationLevel by sessionManager.confirmationLevel.collectAsState()

    // Text input & image
    var textInput by remember { mutableStateOf("") }
    var pendingImageBase64 by remember { mutableStateOf<String?>(null) }

    // Auto-scroll list state
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Photo picker for multimodal screenshot/image understanding
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val base64 = uriToBase64(context, uri)
            pendingImageBase64 = base64
        }
    }

    // Microphone permission launcher for real Gemini Live conversation
    var micPermissionGranted by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        micPermissionGranted = isGranted
        if (isGranted) {
            sessionManager.startLiveConversation()
        }
    }

    // Generic permission launcher for Permission Center
    val genericPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        sessionManager.permissionManager.refreshPermissions()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("sana_main_screen"),
        containerColor = SanaPurpleDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Navigation & Companion Badges
            TopCompanionBar(
                nickname = nickname,
                voiceName = selectedVoice.name,
                isRomanticMode = isRomanticMode,
                onToggleRomantic = { sessionManager.toggleRomanticMode(!isRomanticMode) },
                onOpenVoiceStudio = { showVoiceStudio = true },
                onOpenDiagnostics = { showDiagnostics = true },
                onOpenPermissions = { showPermissionsDialog = true },
                onOpenMemory = { showMemoryDialog = true },
                onOpenSettings = { showSettingsSheet = true }
            )

            // SANA Hero Section: Avatar Visualizer & Voice Test
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SanaAvatarVisualizer(
                        playbackState = playbackState,
                        audioLevel = audioLevel,
                        isRomanticMode = isRomanticMode,
                        currentVoiceName = selectedVoice.name
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Real Two-Way Gemini Live Voice Controls Row (Start Once, Continuous Loop, Stop Button)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLiveConversationActive || playbackState == AudioPlaybackState.LISTENING || playbackState == AudioPlaybackState.SPEAKING || playbackState == AudioPlaybackState.THINKING) {
                            // Prominent STOP button (Requirement 6)
                            Button(
                                onClick = { sessionManager.stopLiveConversation() },
                                colors = ButtonDefaults.buttonColors(containerColor = SanaRomanticRed),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("stop_live_assistant_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "STOP ASSISTANT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        } else {
                            // START LIVE BUTTON (Requirement 1 & 4)
                            Button(
                                onClick = {
                                    val hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (hasAudioPerm) {
                                        sessionManager.startLiveConversation()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SanaPinkPrimary),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("start_live_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Start Live Voice",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "START LIVE VOICE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Test voice button
                            Button(
                                onClick = { sessionManager.runVoiceTest() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (voiceTestPassed) Color(0xFF00C853) else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("test_sana_voice_button")
                            ) {
                                Icon(
                                    imageVector = if (voiceTestPassed) Icons.Default.Check else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (voiceTestPassed) "Voice OK" else "Test Voice",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Voice Error Warning banner if native audio fails
                    AnimatedVisibility(visible = voiceError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF3E141E)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag("voice_error_banner")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "NATURAL SANA VOICE IS UNAVAILABLE",
                                    color = Color(0xFFFF5252),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = voiceError ?: "Unable to contact Gemini audio engine.",
                                    color = SanaSoftWhite,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = { sessionManager.retryLastVoice() },
                                        colors = ButtonDefaults.buttonColors(containerColor = SanaPinkPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("retry_voice_button")
                                    ) {
                                        Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { showDiagnostics = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("open_diagnostics_from_error")
                                    ) {
                                        Text("Diagnostics", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { showSettingsSheet = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Settings", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (micError != null) {
                        Text(
                            text = micError ?: "",
                            color = Color(0xFFFFAB91),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Quick Phone Control Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionQuickChip("🔋 Battery") { sessionManager.sendMessage("What's my battery level?") }
                ActionQuickChip("💬 WhatsApp") { sessionManager.sendMessage("SANA, open WhatsApp") }
                ActionQuickChip("📷 Camera") { sessionManager.sendMessage("SANA, open camera") }
                ActionQuickChip("🗺️ Maps") { sessionManager.sendMessage("SANA, open maps") }
                ActionQuickChip("🎵 Play Media") { sessionManager.sendMessage("SANA, play music") }
                ActionQuickChip("⏸️ Pause Media") { sessionManager.sendMessage("SANA, pause music") }
                ActionQuickChip("🔔 Notifications") { sessionManager.sendMessage("SANA, do I have any messages?") }
                ActionQuickChip("🛡️ Permissions") { showPermissionsDialog = true }
            }

            // Conversation Messages Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        isSpeaking = isSpeaking,
                        onReplayAudio = {
                            if (msg.sender == SenderType.SANA) {
                                sessionManager.retryLastVoice()
                            }
                        }
                    )
                }

                if (playbackState == AudioPlaybackState.THINKING) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = SanaPinkSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SANA is preparing a sweet voice reply...",
                                color = SanaSubtext,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(6.dp)) }
            }

            // Quick Prompt Suggestions Row
            QuickSuggestionsRow(
                isRomanticMode = isRomanticMode,
                onSuggestionClick = { prompt ->
                    sessionManager.sendMessage(prompt)
                }
            )

            // Pending Image Attachment Preview
            AnimatedVisibility(visible = pendingImageBase64 != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📸 Photo attached for SANA to view & explain",
                            color = SanaPeachAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { pendingImageBase64 = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("✕", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Bottom Control Bar (Microphone, Stop, Text Field, Attachment, Send)
            BottomInputBar(
                textInput = textInput,
                onTextChange = { textInput = it },
                onSend = {
                    val toSend = textInput.trim()
                    val img = pendingImageBase64
                    if (toSend.isNotBlank() || img != null) {
                        sessionManager.sendMessage(toSend, img)
                        textInput = ""
                        pendingImageBase64 = null
                    }
                },
                isListening = isListening || isLiveConversationActive,
                isLiveActive = isLiveConversationActive,
                onMicClick = {
                    if (isLiveConversationActive || isListening) {
                        sessionManager.stopLiveConversation()
                    } else {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPerm) {
                            sessionManager.startLiveConversation()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onStopClick = {
                    sessionManager.stopLiveConversation()
                },
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    }

    // Modal Dialogs
    if (showVoiceStudio) {
        VoiceStudioDialog(
            selectedVoice = selectedVoice,
            onVoiceSelected = { voice ->
                sessionManager.setGeminiVoice(voice)
            },
            onPreviewVoice = { voice ->
                sessionManager.previewVoice(voice)
            },
            onDismiss = { showVoiceStudio = false }
        )
    }

    if (showDiagnostics) {
        VoiceDiagnosticsDialog(
            diagnostics = diagnostics,
            onRunTest = { sessionManager.runDiagnostics() },
            onDismiss = { showDiagnostics = false }
        )
    }

    if (showMemoryDialog) {
        MemoryDialog(
            memoryList = persistentMemory,
            isMemoryEnabled = isMemoryEnabled,
            onToggleMemory = { sessionManager.toggleMemory(it) },
            onDeleteMemory = { sessionManager.deleteMemoryItem(it) },
            onClearAll = { sessionManager.clearAllMemory() },
            onAddMemory = { k, v -> sessionManager.saveCustomMemory(k, v) },
            onDismiss = { showMemoryDialog = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            nickname = nickname,
            onNicknameChange = { sessionManager.setNickname(it, persist = true) },
            voiceMode = voiceMode,
            onVoiceModeChange = { sessionManager.setVoiceMode(it) },
            selectedVoice = selectedVoice,
            onOpenVoiceStudio = {
                showSettingsSheet = false
                showVoiceStudio = true
            },
            isRomanticMode = isRomanticMode,
            onToggleRomanticMode = { sessionManager.toggleRomanticMode(it) },
            selectedLanguage = selectedLanguage,
            onLanguageChange = { sessionManager.setLanguage(it) },
            wakeWordEnabled = wakeWordEnabled,
            onToggleWakeWord = { sessionManager.toggleWakeWord(it) },
            backgroundAssistantEnabled = backgroundAssistantEnabled,
            onToggleBackgroundAssistant = { enabled ->
                sessionManager.toggleBackgroundAssistant(enabled)
                val intent = Intent(context, SanaBackgroundService::class.java).apply {
                    action = if (enabled) SanaBackgroundService.ACTION_START else SanaBackgroundService.ACTION_STOP
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && enabled) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            confirmationEnabled = confirmationEnabled,
            onToggleConfirmation = { sessionManager.toggleConfirmation(it) },
            confirmationLevel = confirmationLevel,
            onConfirmationLevelChange = { sessionManager.setConfirmationLevel(it) },
            onOpenPermissions = {
                showSettingsSheet = false
                showPermissionsDialog = true
            },
            onOpenMemory = {
                showSettingsSheet = false
                showMemoryDialog = true
            },
            onOpenDiagnostics = {
                showSettingsSheet = false
                showDiagnostics = true
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showPermissionsDialog) {
        PermissionCenterDialog(
            permissionManager = sessionManager.permissionManager,
            onRequestPermission = { perm ->
                genericPermissionLauncher.launch(perm)
            },
            onDismiss = { showPermissionsDialog = false }
        )
    }

    if (pendingConfirmation != null) {
        ConfirmationDialog(
            pending = pendingConfirmation!!,
            onConfirm = { sessionManager.confirmPendingAction() },
            onDismiss = { sessionManager.dismissPendingAction() }
        )
    }
}

@Composable
private fun TopCompanionBar(
    nickname: String,
    voiceName: String,
    isRomanticMode: Boolean,
    onToggleRomantic: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo & Nickname
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SANA AI",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = SanaPinkPrimary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = if (isRomanticMode) SanaRomanticRed.copy(alpha = 0.2f) else SanaPinkPrimary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "for $nickname ❤️",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isRomanticMode) SanaRomanticRed else SanaPinkSecondary
                    )
                )
            }
        }

        // Action Icons
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Romantic Mode Quick Toggle Heart
            IconButton(
                onClick = onToggleRomantic,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("quick_romantic_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Romantic Mode",
                    tint = if (isRomanticMode) SanaRomanticRed else Color(0x66FFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Voice Studio
            IconButton(
                onClick = onOpenVoiceStudio,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_voice_studio_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice Studio",
                    tint = SanaPinkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Permissions Center
            IconButton(
                onClick = onOpenPermissions,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_permissions_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Permissions",
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(19.dp)
                )
            }

            // Diagnostics
            IconButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Diagnostics",
                    tint = SanaGold,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Memory
            IconButton(
                onClick = onOpenMemory,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_memory_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Memory",
                    tint = SanaPeachAccent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Settings
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionQuickChip(label: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
private fun ChatBubbleItem(
    message: ChatMessage,
    isSpeaking: Boolean,
    onReplayAudio: () -> Unit
) {
    val isUser = message.sender == SenderType.USER
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeStr = timeFormat.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}"),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) {
                    SanaPinkPrimary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = if (!isUser) {
                    androidx.compose.foundation.BorderStroke(1.dp, SanaPinkSecondary.copy(alpha = 0.3f))
                } else null,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Header tag for SANA
                    if (!isUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "SANA ${message.emotion ?: "❤️"}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SanaPinkSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    // Main Spoken Text
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isUser) Color.White else SanaSoftWhite,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    )

                    // Action confirmation badge if an Android tool executed
                    if (!message.actionExecuted.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFF00E676).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = message.actionExecuted,
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Timestamp
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isUser) Color(0xCCFFFFFF) else SanaSubtext.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSuggestionsRow(
    isRomanticMode: Boolean,
    onSuggestionClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val suggestions = if (isRomanticMode) {
        listOf(
            "Hi SANA! ❤️",
            "I missed you",
            "You're being cute today",
            "Call me Babe",
            "How was your day?",
            "Open YouTube",
            "Open Camera"
        )
    } else {
        listOf(
            "Hi SANA! ❤️",
            "I'm happy!",
            "I'm having a bad day",
            "I'm tired",
            "Open WhatsApp",
            "Open YouTube",
            "Open Camera",
            "Set an alarm"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { chipText ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, SanaPinkSecondary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clickable { onSuggestionClick(chipText) }
                    .testTag("suggestion_${chipText.take(6)}")
            ) {
                Text(
                    text = chipText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SanaSoftWhite,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BottomInputBar(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isListening: Boolean,
    isLiveActive: Boolean = false,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit = {},
    onPickPhoto: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isLiveActive) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_scale"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo Picker Button
            IconButton(
                onClick = onPickPhoto,
                modifier = Modifier.testTag("pick_photo_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Attach screenshot or photo",
                    tint = SanaPeachAccent
                )
            }

            // Text Input
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (isLiveActive || isListening) "Listening to you, Boss..." else "Talk with SANA...",
                        fontSize = 13.sp,
                        color = SanaSubtext
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SanaPinkPrimary,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Dedicated STOP Button if Live Assistant is active (Requirement 6)
            if (isLiveActive || isListening) {
                IconButton(
                    onClick = onStopClick,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SanaRomanticRed)
                        .testTag("bottom_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Microphone / Start Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(
                        if (isLiveActive || isListening) Color(0xFF00E676) else SanaPinkPrimary
                    )
                    .clickable { onMicClick() }
                    .testTag("microphone_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isLiveActive || isListening) "Live Voice Active (Tap to Stop)" else "Start Live Voice",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Send Button if text is present
            if (textInput.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SanaPinkSecondary)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }
}
