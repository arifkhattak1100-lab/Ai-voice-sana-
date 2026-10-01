package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AudioPlaybackState
import com.example.ui.theme.SanaGold
import com.example.ui.theme.SanaPeachAccent
import com.example.ui.theme.SanaPinkPrimary
import com.example.ui.theme.SanaPinkSecondary
import com.example.ui.theme.SanaPurpleDark
import com.example.ui.theme.SanaRomanticRed

@Composable
fun SanaAvatarVisualizer(
    playbackState: AudioPlaybackState,
    audioLevel: Float,
    isRomanticMode: Boolean,
    currentVoiceName: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (playbackState == AudioPlaybackState.SPEAKING) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (playbackState == AudioPlaybackState.SPEAKING) 400 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowBrush = if (isRomanticMode) {
        Brush.radialGradient(
            colors = listOf(
                SanaRomanticRed.copy(alpha = 0.5f),
                SanaPinkPrimary.copy(alpha = 0.25f),
                Color.Transparent
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                SanaPinkSecondary.copy(alpha = 0.45f),
                SanaPeachAccent.copy(alpha = 0.2f),
                Color.Transparent
            )
        )
    }

    Column(
        modifier = modifier.testTag("sana_avatar_visualizer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Aura Glow reacting to voice amplitude
            val dynamicGlowSize = (110 + (audioLevel * 26)).dp
            Box(
                modifier = Modifier
                    .size(dynamicGlowSize)
                    .clip(CircleShape)
                    .background(glowBrush)
            )

            // Animated Outer Ring
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(
                        width = if (playbackState == AudioPlaybackState.SPEAKING) 3.dp else 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                SanaPinkPrimary,
                                SanaPeachAccent,
                                SanaRomanticRed,
                                SanaGold,
                                SanaPinkPrimary
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Inner Avatar Image
            Surface(
                modifier = Modifier
                    .size(98.dp)
                    .clip(CircleShape),
                color = SanaPurpleDark,
                shadowElevation = 8.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.sana_avatar_1790873621226),
                    contentDescription = "SANA AI Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Status Badge Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .clip(CircleShape)
                    .background(
                        when (playbackState) {
                            AudioPlaybackState.SPEAKING -> SanaPinkPrimary
                            AudioPlaybackState.LISTENING -> Color(0xFF00E676)
                            AudioPlaybackState.THINKING -> SanaGold
                            AudioPlaybackState.ERROR -> Color(0xFFFF5252)
                            else -> SanaPeachAccent
                        }
                    )
                    .padding(5.dp)
            ) {
                Icon(
                    imageVector = when (playbackState) {
                        AudioPlaybackState.SPEAKING -> Icons.AutoMirrored.Filled.VolumeUp
                        AudioPlaybackState.LISTENING -> Icons.Default.Mic
                        AudioPlaybackState.THINKING -> Icons.Default.Favorite
                        else -> Icons.Default.Favorite
                    },
                    contentDescription = "SANA State",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // State & Real Voice Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            val statusText = when (playbackState) {
                AudioPlaybackState.SPEAKING -> "Speaking with $currentVoiceName voice..."
                AudioPlaybackState.LISTENING -> "Listening to you, Boss..."
                AudioPlaybackState.THINKING -> "Thinking with love..."
                AudioPlaybackState.ERROR -> "Voice paused"
                else -> if (isRomanticMode) "Loving companion active ❤️" else "Ready to talk with you"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
