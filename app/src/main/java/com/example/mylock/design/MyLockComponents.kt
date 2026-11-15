package com.example.mylock.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.AlertCrimsonDark
import com.example.ui.theme.SecureEmerald
import com.example.ui.theme.SecureEmeraldDark
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberDark
import kotlin.math.sin

enum class SemanticTone {
    PRIMARY,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

@Composable
fun semanticContainerColor(tone: SemanticTone): Color {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    return when (tone) {
        SemanticTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        SemanticTone.SUCCESS -> if (isDark) Color(0xFF0A3327) else Color(0xFFD1FAE5)
        SemanticTone.WARNING -> if (isDark) Color(0xFF3A2807) else Color(0xFFFEF3C7)
        SemanticTone.ERROR -> if (isDark) Color(0xFF3E1216) else Color(0xFFFEE2E2)
        SemanticTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
    }
}

@Composable
fun semanticContentColor(tone: SemanticTone): Color {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    return when (tone) {
        SemanticTone.PRIMARY -> MaterialTheme.colorScheme.primary
        SemanticTone.SUCCESS -> if (isDark) SecureEmeraldDark else SecureEmerald
        SemanticTone.WARNING -> if (isDark) WarningAmberDark else WarningAmber
        SemanticTone.ERROR -> if (isDark) AlertCrimsonDark else AlertCrimson
        SemanticTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun MyLockLogoBadge(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = "My Lock Shield Logo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    tone: SemanticTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = semanticContainerColor(tone)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = semanticContentColor(tone),
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = semanticContentColor(tone),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SecurityMetricCard(
    title: String,
    primaryValue: String,
    supportingText: String,
    icon: ImageVector,
    tone: SemanticTone,
    badgeText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val borderColor = semanticContentColor(tone).copy(alpha = 0.25f)

    Card(
        modifier = modifier.border(1.dp, borderColor, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = semanticContainerColor(tone),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = semanticContentColor(tone),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(label = badgeText, tone = tone)
            }

            Text(
                text = primaryValue,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AcousticWaveformVisualizer(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_float"
    )
    val smoothedAmp by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.08f, 1f) else 0.06f,
        animationSpec = spring(stiffness = 380f),
        label = "smoothed_amp"
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val barCount = 28
        val totalW = size.width
        val totalH = size.height
        val slotW = totalW / barCount
        val barW = slotW * 0.56f
        val centerY = totalH / 2f

        for (i in 0 until barCount) {
            val normPos = i.toFloat() / barCount.toFloat()
            val envelope = sin(normPos * Math.PI).toFloat().coerceAtLeast(0.25f)
            val ripple = if (isRecording) {
                (0.55f + 0.45f * sin(phase + i * 0.55f))
            } else {
                0.35f
            }
            val barH = (totalH * smoothedAmp * envelope * ripple).coerceIn(6f, totalH)
            val x = i * slotW + (slotW - barW) / 2f
            val y = centerY - barH / 2f
            val brush = Brush.verticalGradient(
                colors = listOf(primaryColor, secondaryColor),
                startY = y,
                endY = y + barH
            )
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

@Composable
fun CompatibilityScopeBanner(
    badgeText: String,
    title: String,
    description: String,
    isSystemSupported: Boolean,
    modifier: Modifier = Modifier
) {
    val tone = if (isSystemSupported) SemanticTone.SUCCESS else SemanticTone.WARNING
    Card(
        modifier = modifier.border(
            1.dp,
            semanticContentColor(tone).copy(alpha = 0.35f),
            MaterialTheme.shapes.medium
        ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = semanticContainerColor(tone).copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isSystemSupported) Icons.Filled.VerifiedUser else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = semanticContentColor(tone),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = semanticContentColor(tone)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
