package com.suhrud.docsy.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.suhrud.docsy.domain.security.AppLockManager
import com.suhrud.docsy.domain.security.BiometricStatus
import com.suhrud.docsy.ui.components.DocsyLockIcon
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.OffWhite
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    appLockManager: AppLockManager,
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val expectedPinLength = remember { appLockManager.getPinLength() }
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var lockoutSeconds by remember { mutableIntStateOf(appLockManager.getRemainingLockoutSeconds()) }

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0) {
            delay(1000)
            lockoutSeconds = appLockManager.getRemainingLockoutSeconds()
        }
    }

    val triggerBiometric = {
        if (activity != null) {
            when (appLockManager.checkBiometricStatus()) {
                BiometricStatus.READY -> {
                    appLockManager.showBiometricPrompt(
                        activity = activity,
                        onAuthenticated = onUnlockSuccess,
                        onError = { }
                    )
                }
                BiometricStatus.NONE_ENROLLED -> {
                    Toast.makeText(context, "No fingerprint enrolled. Opening security settings...", Toast.LENGTH_SHORT).show()
                    appLockManager.openSecurityEnrollment(context)
                }
                BiometricStatus.NO_HARDWARE -> {
                    Toast.makeText(context, "Fingerprint hardware unavailable. Please use PIN.", Toast.LENGTH_SHORT).show()
                }
                else -> { }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (lockoutSeconds == 0) {
            delay(150)
            triggerBiometric()
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == expectedPinLength) {
            if (appLockManager.verifyPin(enteredPin)) {
                onUnlockSuccess()
            } else {
                isError = true
                lockoutSeconds = appLockManager.getRemainingLockoutSeconds()
                delay(350)
                enteredPin = ""
                isError = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(1.dp, BorderLight, CircleShape)
                        .background(PureWhite),
                    contentAlignment = Alignment.Center
                ) {
                    DocsyLockIcon(tint = TextPrimary, size = 24.dp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Docsy",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (lockoutSeconds > 0) {
                        "Try again in ${lockoutSeconds}s"
                    } else {
                        "Unlock to continue"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (lockoutSeconds > 0) TextPrimary else TextSecondary
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (i in 0 until expectedPinLength) {
                        val filled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isError) TextPrimary else if (filled) TextPrimary else BorderLight,
                                    shape = CircleShape
                                )
                                .background(if (filled) TextPrimary else PureWhite)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, BorderLight, CircleShape)
                                    .background(PureWhite)
                                    .clickable(enabled = lockoutSeconds == 0) {
                                        when (key) {
                                            "DEL" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            "BIO" -> triggerBiometric()
                                            else -> if (enteredPin.length < expectedPinLength) enteredPin += key
                                        }
                                    }
                                    .testTag("pin_key_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                when (key) {
                                    "BIO" -> {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Use fingerprint",
                                            tint = TextPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    "DEL" -> {
                                        Text(
                                            text = "⌫",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = { triggerBiometric() },
                enabled = lockoutSeconds == 0,
                modifier = Modifier.testTag("btn_use_biometric")
            ) {
                Text(
                    text = "Use fingerprint",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
            }
        }
    }
}
