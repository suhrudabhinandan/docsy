package com.suhrud.docsy.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.suhrud.docsy.security.PinSecurityManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    pinSecurityManager: PinSecurityManager,
    actualProfileName: String,
    onUnlocked: () -> Unit,
    onRequestBiometric: () -> Unit
) {
    // Safety guard: if no PIN is enabled, unlock immediately
    LaunchedEffect(pinSecurityManager.isPinEnabled()) {
        if (!pinSecurityManager.isPinEnabled()) {
            onUnlocked()
        }
    }

    val haptic = LocalHapticFeedback.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingLockoutSeconds by remember { mutableStateOf(pinSecurityManager.getRemainingLockoutSeconds()) }
    var showForgotPinDialog by remember { mutableStateOf(false) }

    // Countdown effect if locked out
    LaunchedEffect(remainingLockoutSeconds) {
        if (remainingLockoutSeconds > 0) {
            delay(1000L)
            remainingLockoutSeconds = pinSecurityManager.getRemainingLockoutSeconds()
        }
    }

    // Keypad layout: randomized or standard 1..9, 0
    val keypadNumbers = remember(pinSecurityManager.isRandomizeKeypadEnabled()) {
        if (pinSecurityManager.isRandomizeKeypadEnabled()) {
            (0..9).shuffled()
        } else {
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        }
    }

    val shakeOffset = remember { androidx.compose.animation.core.Animatable(0f) }

    val triggerShake: suspend () -> Unit = {
        val shakeAnim = androidx.compose.animation.core.keyframes {
            durationMillis = 400
            0f at 0
            (-20f) at 50
            20f at 100
            (-15f) at 150
            15f at 200
            (-10f) at 250
            10f at 300
            (-5f) at 350
            0f at 400
        }
        shakeOffset.animateTo(0f, animationSpec = shakeAnim)
    }

    val expectedPinLength = remember { pinSecurityManager.getPinLength() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val onDigitClick: (Int) -> Unit = { digit ->
        if (remainingLockoutSeconds <= 0 && enteredPin.length < expectedPinLength) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val updated = enteredPin + digit
            enteredPin = updated
            errorMessage = null

            // Verify when full PIN length is entered
            if (updated.length == expectedPinLength) {
                val result = pinSecurityManager.verifyPin(updated)
                when (result) {
                    is PinSecurityManager.VerifyResult.Success -> {
                        onUnlocked()
                    }
                    is PinSecurityManager.VerifyResult.Incorrect -> {
                        coroutineScope.launch {
                            triggerShake()
                            enteredPin = ""
                        }
                        if (result.isLockedOut) {
                            remainingLockoutSeconds = result.lockoutSeconds
                            errorMessage = "Too many failed attempts. Locked out for ${result.lockoutSeconds}s."
                        } else {
                            val attemptsLeft = (5 - result.failedAttempts).coerceAtLeast(0)
                            errorMessage = "Incorrect PIN ($attemptsLeft attempts left)"
                        }
                    }
                    is PinSecurityManager.VerifyResult.LockedOut -> {
                        coroutineScope.launch {
                            triggerShake()
                            enteredPin = ""
                        }
                        remainingLockoutSeconds = result.lockoutSeconds
                        errorMessage = "Locked out. Try again in ${result.lockoutSeconds}s."
                    }
                }
            }
        }
    }

    val onDeleteClick = {
        if (enteredPin.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("app_lock_screen")
    ) {
        val minHeight = maxHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Lock icon and Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Docsy Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Docsy Locked",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Enter your $expectedPinLength-digit PIN",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Dot PIN Indicator with shake animation
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .graphicsLayer { translationX = shakeOffset.value }
                        .testTag("pin_dots_indicator")
                ) {
                    for (i in 0 until expectedPinLength) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    color = if (isFilled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                                    },
                                    shape = CircleShape
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error or Lockout Text
                AnimatedVisibility(
                    visible = errorMessage != null || remainingLockoutSeconds > 0,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val text = if (remainingLockoutSeconds > 0) {
                        "Locked out. Try again in ${remainingLockoutSeconds}s."
                    } else {
                        errorMessage ?: ""
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Middle section: Keypad Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Rows 1 to 3: Digits 1 through 9
                val digits1to9 = if (pinSecurityManager.isRandomizeKeypadEnabled()) {
                    keypadNumbers.take(9)
                } else {
                    listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
                }

                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0..2) {
                            val digit = digits1to9[row * 3 + col]
                            KeypadButton(
                                text = digit.toString(),
                                onClick = { onDigitClick(digit) },
                                testTag = "keypad_btn_$digit"
                            )
                        }
                    }
                }

                // Row 4: Fingerprint / Digit 0 / Delete
                val zeroDigit = if (pinSecurityManager.isRandomizeKeypadEnabled()) keypadNumbers.last() else 0
                Row(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Biometric shortcut button
                    if (pinSecurityManager.isFingerprintEnabled()) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                                .clickable(onClick = onRequestBiometric)
                                .testTag("keypad_biometric_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Use Fingerprint",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(70.dp))
                    }

                    // 0 digit
                    KeypadButton(
                        text = zeroDigit.toString(),
                        onClick = { onDigitClick(zeroDigit) },
                        testTag = "keypad_btn_$zeroDigit"
                    )

                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onDeleteClick)
                            .testTag("keypad_delete_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Bottom section: Forgot PIN & Reset options
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                TextButton(
                    onClick = { showForgotPinDialog = true },
                    modifier = Modifier.testTag("forgot_pin_button")
                ) {
                    Text(
                        text = "Forgot PIN / Reset Lock",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    // Emergency Forgot PIN Recovery Dialog
    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            title = {
                Text(text = "Reset App Lock", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        text = "Resetting the PIN will disable App Lock so you can access Docsy immediately. Your documents, history, and settings will remain completely safe.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pinSecurityManager.removePin()
                        showForgotPinDialog = false
                        onUnlocked()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Reset PIN & Unlock")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showForgotPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun KeypadButton(
    text: String,
    onClick: () -> Unit,
    testTag: String
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF27272A) else Color(0xFFF4F4F5)
    val borderColor = if (isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)
    val textColor = if (isDark) Color(0xFFFAFAFA) else Color(0xFF18181B)

    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
