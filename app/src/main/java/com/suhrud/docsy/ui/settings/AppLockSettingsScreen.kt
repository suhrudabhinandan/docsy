package com.suhrud.docsy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.security.BiometricHelper
import com.suhrud.docsy.security.PinSecurityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    pinSecurityManager: PinSecurityManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isPinEnabled by remember { mutableStateOf(pinSecurityManager.isPinEnabled()) }
    var isFingerprintEnabled by remember { mutableStateOf(pinSecurityManager.isFingerprintEnabled()) }
    var isRandomizeEnabled by remember { mutableStateOf(pinSecurityManager.isRandomizeKeypadEnabled()) }

    val biometricStatus = remember { BiometricHelper.checkBiometricStatus(context) }
    val isBiometricAvailable = biometricStatus == BiometricHelper.BiometricStatus.AVAILABLE

    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinRemoveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "App Lock", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("app_lock_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("app_lock_settings_content")
        ) {
            Text(
                text = "Secure your local assistant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "When enabled, Docsy requires authentication on launch, after 30 seconds in the background, or when the screen turns off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Toggle 1: Require PIN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Require PIN",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isPinEnabled) "4-6 digit PBKDF2 salted hash" else "Set a PIN to lock the app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = isPinEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showPinSetupDialog = true
                        } else {
                            showPinRemoveDialog = true
                        }
                    },
                    modifier = Modifier.testTag("require_pin_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Toggle 2: Use Fingerprint
            val fingerprintSubtitle = when {
                !isPinEnabled -> "Set a PIN first to enable fingerprint unlock"
                biometricStatus == BiometricHelper.BiometricStatus.AVAILABLE -> "Unlock with fingerprint sensor"
                biometricStatus == BiometricHelper.BiometricStatus.NOT_ENROLLED -> "No biometric enrolled on device"
                biometricStatus == BiometricHelper.BiometricStatus.NO_HARDWARE -> "Biometric hardware not available"
                else -> "Biometric authentication unavailable"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Use Fingerprint",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isBiometricAvailable && isPinEnabled) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                        }
                    )
                    Text(
                        text = fingerprintSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Switch(
                    checked = isFingerprintEnabled && isBiometricAvailable && isPinEnabled,
                    enabled = isBiometricAvailable && isPinEnabled,
                    onCheckedChange = { checked ->
                        isFingerprintEnabled = checked
                        pinSecurityManager.setFingerprintEnabled(checked)
                    },
                    modifier = Modifier.testTag("use_fingerprint_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Toggle 3: Randomize Keypad
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Randomize keypad numbers",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Shuffles digit positions to prevent smudge pattern reading",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Switch(
                    checked = isRandomizeEnabled,
                    onCheckedChange = { checked ->
                        isRandomizeEnabled = checked
                        pinSecurityManager.setRandomizeKeypadEnabled(checked)
                    },
                    modifier = Modifier.testTag("randomize_keypad_switch")
                )
            }
        }
    }

    // Dialog: Setup PIN
    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var setupError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text("Set 4-6 Digit PIN", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("New PIN (4-6 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (setupError != null) {
                        Text(
                            text = setupError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length !in 4..6) {
                            setupError = "PIN must be between 4 and 6 digits"
                        } else if (pinInput != confirmPinInput) {
                            setupError = "PINs do not match"
                        } else {
                            val ok = pinSecurityManager.setPin(pinInput)
                            if (ok) {
                                isPinEnabled = true
                                showPinSetupDialog = false
                            } else {
                                setupError = "Failed to set PIN"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog: Remove PIN Confirmation
    if (showPinRemoveDialog) {
        var currentPinInput by remember { mutableStateOf("") }
        var removeError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPinRemoveDialog = false },
            title = { Text("Turn off PIN Lock", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        text = "Enter your current PIN to turn off App Lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentPinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) currentPinInput = it },
                        label = { Text("Current PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (removeError != null) {
                        Text(
                            text = removeError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = pinSecurityManager.verifyPin(currentPinInput)
                        if (result is PinSecurityManager.VerifyResult.Success) {
                            pinSecurityManager.removePin()
                            isPinEnabled = false
                            isFingerprintEnabled = false
                            showPinRemoveDialog = false
                        } else {
                            removeError = "Incorrect PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove PIN")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPinRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
