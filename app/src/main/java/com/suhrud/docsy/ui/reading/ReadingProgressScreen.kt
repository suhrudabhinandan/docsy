package com.suhrud.docsy.ui.reading

import android.provider.Settings
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suhrud.docsy.R
import com.suhrud.docsy.scanner.ReadingStateRepository
import com.suhrud.docsy.ui.common.BackgroundRunningDialog
import com.suhrud.docsy.util.BatteryOptimizationHelper
import java.text.NumberFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingProgressScreen(
    onContinueToHome: () -> Unit
) {
    val context = LocalContext.current
    val readingState by ReadingStateRepository.readingState.collectAsStateWithLifecycle()

    // Check system animator duration scale
    val animatorScale = try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    } catch (_: Throwable) {
        1f
    }
    val animationsEnabled = animatorScale > 0f

    val numFormat = remember { NumberFormat.getInstance() }
    val tabularTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFeatureSettings = "tnum"
    )

    // Dot animation for "Eating Docs..."
    var dotCount by remember { mutableStateOf(1) }
    if (animationsEnabled && !readingState.isFinished) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(500)
                dotCount = (dotCount % 3) + 1
            }
        }
    }
    val animatedDots = ".".repeat(dotCount)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("reading_progress_screen"),
            horizontalAlignment = Alignment.Start
        ) {
            // 1. Top-left in-app icon
            Image(
                painter = painterResource(id = R.drawable.docsy_icon),
                contentDescription = "Docsy Icon",
                modifier = Modifier
                    .size(42.dp)
                    .testTag("reading_app_icon")
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 2. Heading
            val headingText = if (readingState.isFinished) {
                "Done reading!"
            } else {
                "Eating Docs$animatedDots"
            }
            Text(
                text = headingText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.testTag("reading_heading_text")
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Pac-Man Row
            val progressFraction = readingState.progressFraction.coerceIn(0f, 1f)
            if (animationsEnabled) {
                PacManRowCanvas(
                    progress = progressFraction,
                    isFinished = readingState.isFinished,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pacman_progress_canvas")
                )
            } else {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("pacman_progress_linear"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. File Counts Display
            val formattedCurrent = numFormat.format(readingState.processedFiles)
            val formattedTotal = numFormat.format(readingState.totalFiles)

            Column(modifier = Modifier.fillMaxWidth()) {
                if (readingState.totalFiles > 0) {
                    Text(
                        text = "Reading $formattedCurrent of $formattedTotal files",
                        style = tabularTextStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("reading_file_count_text")
                    )
                } else {
                    Text(
                        text = "$formattedCurrent files so far",
                        style = tabularTextStyle,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("reading_file_count_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Still finding files...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 5. Per-type Tallies Chips
            Text(
                text = "Read so far",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip(label = "Docs", count = readingState.documentsCount, numFormat = numFormat)
                CategoryChip(label = "Images", count = readingState.photosCount, numFormat = numFormat)
                CategoryChip(label = "Videos", count = readingState.videosCount, numFormat = numFormat)
                CategoryChip(label = "Music", count = readingState.musicCount, numFormat = numFormat)
                CategoryChip(label = "Messages", count = readingState.messagesCount, numFormat = numFormat)
                CategoryChip(label = "Other", count = readingState.otherCount, numFormat = numFormat)
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))
            Spacer(modifier = Modifier.height(24.dp))

            // 6. Permission & Local Note
            Text(
                text = "Docsy processes all documents privately on your device. No files leave your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            var showBackgroundDialog by remember { mutableStateOf(false) }

            if (showBackgroundDialog) {
                BackgroundRunningDialog(
                    onDismiss = {
                        BatteryOptimizationHelper.setPrompted(context, true)
                        showBackgroundDialog = false
                        onContinueToHome()
                    },
                    onAllow = {
                        BatteryOptimizationHelper.setPrompted(context, true)
                        showBackgroundDialog = false
                        BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                        onContinueToHome()
                    }
                )
            }

            // 7. Action Buttons
            if (readingState.isFinished) {
                Button(
                    onClick = onContinueToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_asking_button"),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Start asking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = {
                        val shouldPrompt = !BatteryOptimizationHelper.hasBeenPrompted(context) &&
                                !BatteryOptimizationHelper.isExempt(context)
                        if (shouldPrompt) {
                            showBackgroundDialog = true
                        } else {
                            onContinueToHome()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("continue_in_background_button"),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "Continue in background",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    count: Int,
    numFormat: NumberFormat
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.testTag("category_chip_$label")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = numFormat.format(count),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun BorderStroke(width: androidx.compose.ui.unit.Dp, color: Color) =
    androidx.compose.foundation.BorderStroke(width, color)

@Composable
private fun PacManRowCanvas(
    progress: Float,
    isFinished: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val dotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    // Infinite transition for mouth opening/closing
    val infiniteTransition = rememberInfiniteTransition()
    val mouthAngle by if (!isFinished) {
        infiniteTransition.animateValue(
            initialValue = 10f,
            targetValue = 45f,
            typeConverter = TwoWayConverter({ AnimationVector1D(it) }, { it.value }),
            animationSpec = infiniteRepeatable(
                animation = tween(250, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    } else {
        remember { mutableStateOf(20f) }
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val centerY = size.height / 2f

        if (totalWidth <= 0f) return@Canvas

        val dotCount = 12
        val pacManRadius = 16.dp.toPx()
        val dotRadius = 4.dp.toPx()

        val startX = pacManRadius
        val endX = totalWidth - pacManRadius
        val travelDistance = (endX - startX).coerceAtLeast(0f)

        val currentPacManX = startX + (travelDistance * progress.coerceIn(0f, 1f))

        // Draw 12 hollow dots along the path
        val stepX = if (dotCount > 1) travelDistance / (dotCount - 1) else 0f
        for (i in 0 until dotCount) {
            val dotX = startX + (i * stepX)
            // If dot is ahead of Pac-Man, draw it
            if (dotX > currentPacManX + (pacManRadius * 0.5f)) {
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(dotX, centerY),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Draw Pac-Man
        val startAngle = mouthAngle
        val sweepAngle = 360f - (2f * mouthAngle)

        drawArc(
            color = primaryColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = true,
            topLeft = Offset(currentPacManX - pacManRadius, centerY - pacManRadius),
            size = Size(pacManRadius * 2f, pacManRadius * 2f)
        )
    }
}
