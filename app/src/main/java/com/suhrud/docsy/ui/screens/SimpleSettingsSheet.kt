package com.suhrud.docsy.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.suhrud.docsy.domain.security.BiometricStatus
import com.suhrud.docsy.ui.DocsyViewModel
import com.suhrud.docsy.ui.components.AvatarCatalog
import com.suhrud.docsy.ui.components.AvatarView
import com.suhrud.docsy.ui.components.DocsyCloseIcon
import com.suhrud.docsy.ui.components.ProfileAvatar
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.LightSurface
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextMuted
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleSettingsSheet(
    viewModel: DocsyViewModel,
    onAddFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentName by viewModel.userName.collectAsState()
    val selectedAvatarId by viewModel.profileAvatarId.collectAsState()

    var isEditingProfile by rememberSaveable { mutableStateOf(false) }

    var tempName by remember(currentName, isEditingProfile) { mutableStateOf(currentName) }
    var tempAvatarId by remember(selectedAvatarId, isEditingProfile) { mutableStateOf(selectedAvatarId) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showAvatarPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditingProfile) {
        isEditingProfile = false
        tempName = currentName
        tempAvatarId = selectedAvatarId
        nameError = null
        showAvatarPicker = false
    }

    val appLockManager = viewModel.appLockManager
    val activity = context as? FragmentActivity
    var isAppLockEnabled by remember { mutableStateOf(appLockManager.isAppLockEnabled()) }
    var showSetupPinDialog by remember { mutableStateOf(false) }
    var isChangingPinOnly by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var showAuthDialogToDisable by remember { mutableStateOf(false) }
    var authPinInput by remember { mutableStateOf("") }
    var selectedTimeoutMinutes by remember { mutableIntStateOf(appLockManager.getTimeoutMinutes()) }
    var showTimeoutMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeSettings() },
        sheetState = sheetState,
        containerColor = PureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = TextPrimary
                )
                IconButton(
                    onClick = { viewModel.closeSettings() },
                    modifier = Modifier.testTag("btn_close_settings")
                ) {
                    DocsyCloseIcon(tint = TextPrimary, size = 18.dp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PROFILE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = TextMuted
                )
                if (!isEditingProfile) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(LightSurface)
                            .clickable { isEditingProfile = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("btn_edit_profile"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = TextPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isEditingProfile) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(22.dp))
                        .background(PureWhite)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(
                            avatarId = selectedAvatarId,
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Column {
                            Text(
                                text = currentName.ifBlank { "User" },
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextPrimary,
                                modifier = Modifier.testTag("profile_view_name")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Docsy User",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, TextPrimary, RoundedCornerShape(22.dp))
                        .background(PureWhite)
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { showAvatarPicker = !showAvatarPicker }
                            ) {
                                ProfileAvatar(
                                    avatarId = tempAvatarId,
                                    size = 56.dp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (showAvatarPicker) "Close" else "Change avatar",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Name",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (nameError != null) 1.5.dp else 1.dp,
                                            color = if (nameError != null) TextPrimary else BorderLight,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .background(LightSurface)
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    BasicTextField(
                                        value = tempName,
                                        onValueChange = {
                                            tempName = it
                                            if (it.isNotBlank()) nameError = null
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                                        cursorBrush = SolidColor(TextPrimary),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth().testTag("edit_profile_name_input")
                                    )
                                }
                                if (nameError != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = nameError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        if (showAvatarPicker) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Select Avatar",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(LightSurface, RoundedCornerShape(18.dp))
                                    .padding(10.dp)
                            ) {
                                items(AvatarCatalog.size) { idx ->
                                    val item = AvatarCatalog.items[idx]
                                    val isSelected = item.id == tempAvatarId

                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) TextPrimary else BorderLight,
                                                shape = CircleShape
                                            )
                                            .background(PureWhite)
                                            .clickable {
                                                tempAvatarId = item.id
                                            }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ProfileAvatar(avatarId = item.id, size = 36.dp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    isEditingProfile = false
                                    tempName = currentName
                                    tempAvatarId = selectedAvatarId
                                    nameError = null
                                    showAvatarPicker = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LightSurface,
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_cancel_edit_profile")
                            ) {
                                Text("Cancel", style = MaterialTheme.typography.labelLarge)
                            }

                            Button(
                                onClick = {
                                    val trimmed = tempName.trim()
                                    if (trimmed.isBlank()) {
                                        nameError = "Name cannot be empty"
                                    } else {
                                        viewModel.setUserName(trimmed)
                                        viewModel.setProfileAvatarId(tempAvatarId)
                                        isEditingProfile = false
                                        showAvatarPicker = false
                                        nameError = null
                                        Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextPrimary,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_save_edit_profile")
                            ) {
                                Text("Save", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PRIVACY & SECURITY",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Step Counting Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Step counting", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(
                        text = "Tracks daily walking steps offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "App Lock", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Text(
                        text = if (isAppLockEnabled) "PIN & biometric protection active" else "Protect with PIN & biometrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = isAppLockEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            isChangingPinOnly = false
                            pinInput = ""
                            confirmPinInput = ""
                            showSetupPinDialog = true
                        } else {
                            if (appLockManager.checkBiometricStatus() == BiometricStatus.READY && activity != null) {
                                appLockManager.showBiometricPrompt(
                                    activity = activity,
                                    onAuthenticated = {
                                        appLockManager.setAppLockEnabled(false)
                                        isAppLockEnabled = false
                                        Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        authPinInput = ""
                                        showAuthDialogToDisable = true
                                    }
                                )
                            } else {
                                authPinInput = ""
                                showAuthDialogToDisable = true
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = TextPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = LightSurface
                    )
                )
            }

            if (isAppLockEnabled) {
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (appLockManager.checkBiometricStatus() == BiometricStatus.READY && activity != null) {
                                appLockManager.showBiometricPrompt(
                                    activity = activity,
                                    onAuthenticated = {
                                        isChangingPinOnly = true
                                        pinInput = ""
                                        confirmPinInput = ""
                                        showSetupPinDialog = true
                                    },
                                    onError = {
                                        authPinInput = ""
                                        showAuthDialogToDisable = true
                                    }
                                )
                            } else {
                                isChangingPinOnly = true
                                pinInput = ""
                                confirmPinInput = ""
                                showSetupPinDialog = true
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Change PIN", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(text = "••••", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }

                HorizontalDivider(color = BorderLight, thickness = 0.5.dp)
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showTimeoutMenu = true }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Lock timeout", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        val timeoutLabel = when (selectedTimeoutMinutes) {
                            0 -> "Immediately"
                            1 -> "1 minute"
                            5 -> "5 minutes"
                            else -> "15 minutes"
                        }
                        Text(text = timeoutLabel, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }

                    DropdownMenu(
                        expanded = showTimeoutMenu,
                        onDismissRequest = { showTimeoutMenu = false }
                    ) {
                        listOf(0 to "Immediately", 1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes").forEach { (min, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedTimeoutMinutes = min
                                    appLockManager.setTimeoutMinutes(min)
                                    showTimeoutMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PHONE STORAGE & INDEX",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.closeSettings()
                        onAddFilesClick()
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Pick document to index", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(text = "+", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }

            HorizontalDivider(color = BorderLight, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        viewModel.triggerDeviceSync()
                        Toast.makeText(context, "Scanning phone documents...", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Scan phone now", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(text = "↻", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ABOUT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Docsy", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            val versionStr = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: com.suhrud.docsy.BuildConfig.VERSION_NAME
            } catch (_: Exception) {
                com.suhrud.docsy.BuildConfig.VERSION_NAME
            }
            Text(text = "Version $versionStr · Fully offline on-device", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSetupPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetupPinDialog = false
                pinInput = ""
                confirmPinInput = ""
            },
            title = {
                Text(
                    text = if (isChangingPinOnly) "Change App PIN" else "Set App PIN (4–6 Digits)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a 4 to 6 digit security PIN twice to protect Docsy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "New PIN", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .background(LightSurface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 6.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("input_new_pin")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Confirm PIN", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .background(LightSurface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = confirmPinInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPinInput = it
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 6.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("input_confirm_pin")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length !in 4..6) {
                            Toast.makeText(context, "PIN must be 4 to 6 digits", Toast.LENGTH_SHORT).show()
                        } else if (pinInput != confirmPinInput) {
                            Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!isChangingPinOnly && appLockManager.checkBiometricStatus() == BiometricStatus.READY && activity != null) {
                                appLockManager.showBiometricPrompt(
                                    activity = activity,
                                    onAuthenticated = {
                                        appLockManager.setPin(pinInput)
                                        appLockManager.setAppLockEnabled(true)
                                        isAppLockEnabled = true
                                        showSetupPinDialog = false
                                        pinInput = ""
                                        confirmPinInput = ""
                                        Toast.makeText(context, "App Lock enabled with biometric & PIN", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Fingerprint verification cancelled: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                appLockManager.setPin(pinInput)
                                if (!isChangingPinOnly) {
                                    appLockManager.setAppLockEnabled(true)
                                    isAppLockEnabled = true
                                }
                                showSetupPinDialog = false
                                pinInput = ""
                                confirmPinInput = ""
                                Toast.makeText(context, "PIN saved securely", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(if (!isChangingPinOnly && appLockManager.checkBiometricStatus() == BiometricStatus.READY) "Verify & Enable" else "Save PIN")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showSetupPinDialog = false
                        pinInput = ""
                        confirmPinInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightSurface,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showAuthDialogToDisable) {
        AlertDialog(
            onDismissRequest = {
                showAuthDialogToDisable = false
                authPinInput = ""
            },
            title = {
                Text(
                    text = "Authenticate to Disable",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your current PIN to turn off App Lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                            .background(LightSurface, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = authPinInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) authPinInput = it
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 6.sp,
                                color = TextPrimary
                            ),
                            cursorBrush = SolidColor(TextPrimary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth().testTag("input_auth_pin")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (appLockManager.verifyPin(authPinInput)) {
                            appLockManager.setAppLockEnabled(false)
                            isAppLockEnabled = false
                            showAuthDialogToDisable = false
                            authPinInput = ""
                            Toast.makeText(context, "App lock disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Turn Off")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAuthDialogToDisable = false
                        authPinInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LightSurface,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = PureWhite,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
