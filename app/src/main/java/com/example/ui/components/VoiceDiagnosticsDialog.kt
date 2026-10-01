package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DiagnosticsState
import com.example.ui.theme.SanaGold
import com.example.ui.theme.SanaPinkPrimary
import com.example.ui.theme.SanaPinkSecondary
import com.example.ui.theme.SanaPurpleDark
import com.example.ui.theme.SanaSubtext

@Composable
fun VoiceDiagnosticsDialog(
    diagnostics: DiagnosticsState,
    onRunTest: () -> Unit,
    onDismiss: () -> Unit
) {
    // Automatically execute diagnostics test when dialog opens if untested
    LaunchedEffect(Unit) {
        if (diagnostics.geminiConnected == null && !diagnostics.isRunningTest) {
            onRunTest()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, SanaPinkPrimary.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .testTag("voice_diagnostics_dialog"),
            color = SanaPurpleDark
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SanaGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = SanaGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "VOICE DIAGNOSTICS",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_diagnostics")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiagnosticRow(
                            label = "Microphone",
                            status = diagnostics.micWorking,
                            isRunning = diagnostics.isRunningTest && diagnostics.micWorking == null
                        )

                        DiagnosticRow(
                            label = "Gemini connection",
                            status = diagnostics.geminiConnected,
                            extraInfo = if (diagnostics.latencyMs != null) "${diagnostics.latencyMs}ms" else null,
                            isRunning = diagnostics.isRunningTest && diagnostics.geminiConnected == null
                        )

                        DiagnosticRow(
                            label = "Live audio connection",
                            status = diagnostics.liveAudioConnected,
                            isRunning = diagnostics.isRunningTest && diagnostics.liveAudioConnected == null
                        )

                        DiagnosticRow(
                            label = "Voice generation",
                            status = diagnostics.voiceGenerationWorking,
                            isRunning = diagnostics.isRunningTest && diagnostics.voiceGenerationWorking == null
                        )

                        DiagnosticRow(
                            label = "Audio playback",
                            status = diagnostics.audioPlaybackWorking,
                            isRunning = diagnostics.isRunningTest && diagnostics.audioPlaybackWorking == null
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current voice",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Surface(
                                color = SanaPinkPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = diagnostics.currentVoice,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = SanaPinkPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                if (!diagnostics.lastErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33FF5252)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Diagnostic notice: ${diagnostics.lastErrorMessage}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFFF8A80),
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (diagnostics.voiceGenerationWorking == true && diagnostics.audioPlaybackWorking == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x3300E676)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live voice pipeline is 100% active and working, Boss! ❤️",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRunTest,
                    enabled = !diagnostics.isRunningTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_voice_test_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = SanaPinkPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (diagnostics.isRunningTest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TESTING LIVE SANA PIPELINE...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RUN VOICE TEST", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    status: Boolean?,
    extraInfo: String? = null,
    isRunning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontSize = 14.sp
            )
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (extraInfo != null && status == true) {
                Text(
                    text = extraInfo,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SanaPinkSecondary,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = SanaPinkSecondary,
                    strokeWidth = 2.dp
                )
            } else {
                when (status) {
                    true -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Working",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    false -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Failed",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    null -> {
                        Text(
                            text = "Untested",
                            style = MaterialTheme.typography.labelSmall.copy(color = SanaSubtext)
                        )
                    }
                }
            }
        }
    }
}
