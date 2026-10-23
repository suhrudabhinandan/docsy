package com.suhrud.docsy.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun InstructionsScreen(
    initialStep: Int = 1,
    onStepChanged: (Int) -> Unit = {},
    onFinish: () -> Unit,
    onBack: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = (initialStep - 1).coerceIn(0, 4),
        pageCount = { 5 }
    )
    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("instructions_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top row: Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.testTag("skip_button")
            ) {
                Text(
                    text = "Skip",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal Pager for 5 screens
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            InstructionPageContent(page = page)
        }

        // 5-dot progress indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(vertical = 24.dp)
                .testTag("instruction_progress_dots")
        ) {
            repeat(5) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 8.dp else 6.dp)
                        .background(
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            shape = CircleShape
                        )
                )
            }
        }

        // Navigation controls: Back & Next / Get Started
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val prev = pagerState.currentPage - 1
                            pagerState.animateScrollToPage(prev)
                            onStepChanged(prev + 1)
                        }
                    },
                    modifier = Modifier.testTag("instruction_back_button")
                ) {
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.width(80.dp))
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < 4) {
                        coroutineScope.launch {
                            val next = pagerState.currentPage + 1
                            pagerState.animateScrollToPage(next)
                            onStepChanged(next + 1)
                        }
                    } else {
                        onFinish()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("instruction_next_button")
            ) {
                Text(
                    text = if (pagerState.currentPage == 4) "Get Started" else "Next"
                )
            }
        }
    }
}

@Composable
private fun InstructionPageContent(page: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "instructionGlow")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val (title, subtitle) = when (page) {
        0 -> "Give Docsy access." to "Allow once. Docsy keeps your information ready."
        1 -> "Ask naturally." to "Type what you want to know."
        2 -> "Docsy finds it." to "Your files are searched on your device."
        3 -> "Get the answer." to "Docsy gives you what you asked for."
        else -> "Your data stays with you." to "Docsy is designed to work on your device."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Visual Demonstration Box
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> {
                    // Step 1: softly outlined access area
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = pulse),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = pulse),
                                    shape = CircleShape
                                )
                        )
                    }
                }
                1 -> {
                    // Step 2: empty "Ask anything..." field with blinking cursor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(26.dp)
                            )
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Ask anything...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Blinking cursor
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(18.dp)
                                    .alpha(if (pulse > 0.8f) 1f else 0f)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
                2 -> {
                    // Step 3: blank tiles with grey lines, one coming into focus
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(3) { idx ->
                            val isFocused = idx == 1
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(44.dp)
                                    .border(
                                        width = if (isFocused) 1.5.dp else 1.dp,
                                        color = if (isFocused) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = pulse)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(8.dp)
                                            .weight(if (idx == 0) 0.6f else 0.4f)
                                            .background(
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (isFocused) 0.5f else 0.2f
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(8.dp)
                                            .weight(0.3f)
                                            .background(
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = if (isFocused) 0.4f else 0.15f
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Step 4: answer card of grey placeholder bars only
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(16.dp)
                            )
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(14.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(10.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
                else -> {
                    // Step 5: phone outline with lock and "Works offline"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp, 160.dp)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .scale(pulse)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Works offline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subtitle (one short line)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
