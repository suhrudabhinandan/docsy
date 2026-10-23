package com.suhrud.docsy.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suhrud.docsy.R
import com.suhrud.docsy.data.preferences.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    onToggleAutoRead: (Boolean) -> Unit,
    onNavigateToAppLock: () -> Unit,
    onNavigateToChatHistory: () -> Unit,
    onNavigateToReadingFiles: () -> Unit,
    onNavigateToInspection: () -> Unit = {},
    onNavigateToAccessInfo: () -> Unit,
    onNavigateToStepDiagnostics: () -> Unit = {},
    onTriggerQuickRefresh: () -> Unit = {},
    onTriggerFullReRead: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appVersion = getAppVersion(context)
    var showForceReadDialog by remember { mutableStateOf(false) }
    val readingProgress by com.suhrud.docsy.scanner.ReadingStateRepository.readingState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("settings_screen_content")
        ) {
            // Section: Privacy & Security
            SettingsSectionHeader(title = "Privacy & Security")
            SettingsRowItem(
                icon = Icons.Default.Lock,
                title = "App Lock",
                subtitle = "PIN and Fingerprint unlock",
                onClick = onNavigateToAppLock,
                testTag = "settings_app_lock_row"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SettingsRowItem(
                icon = Icons.Default.History,
                title = "Chat history",
                subtitle = "Encrypted local transcripts and auto-delete",
                onClick = onNavigateToChatHistory,
                testTag = "settings_chat_history_row"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Storage & Sync
            SettingsSectionHeader(title = "Reading & Engine")
            SettingsRowItem(
                icon = Icons.Default.Sync,
                title = "Reading files",
                subtitle = if (userProfile.autoRead24h) "Auto-refreshes every 24h" else "Manual refresh only",
                onClick = onNavigateToReadingFiles,
                testTag = "settings_reading_files_row"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SettingsRowItem(
                icon = Icons.Default.Info,
                title = "Extraction inspector",
                subtitle = "Document OCR status and extraction diagnostics",
                onClick = onNavigateToInspection,
                testTag = "settings_extraction_inspector_row"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SettingsRowItem(
                icon = Icons.Default.Refresh,
                title = "Force re-read",
                subtitle = "Trigger quick refresh or full re-read pass",
                onClick = { showForceReadDialog = true },
                testTag = "settings_force_reread_row"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SettingsRowItem(
                icon = Icons.Default.RecordVoiceOver,
                title = "Chat voice status",
                subtitle = "${userProfile.chatVoiceTier} (Offline grounded)",
                onClick = {},
                testTag = "settings_chat_voice_row",
                trailing = {
                    Text(
                        text = userProfile.chatVoiceTier,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            SettingsRowItem(
                icon = Icons.Default.DirectionsWalk,
                title = "Step counting status",
                subtitle = "Sensors & Health Connect",
                onClick = onNavigateToStepDiagnostics,
                testTag = "settings_step_counting_row"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Permissions & System
            SettingsSectionHeader(title = "Permissions & System")
            SettingsRowItem(
                icon = Icons.Default.Security,
                title = "What Docsy can access",
                subtitle = "On-device permissions overview",
                onClick = onNavigateToAccessInfo,
                testTag = "settings_access_overview_row"
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // About row
            SettingsRowItem(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Docsy v$appVersion • Fully offline",
                onClick = {},
                testTag = "settings_about_row",
                trailing = {
                    Text(
                        text = "v$appVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            )
            Spacer(modifier = Modifier.height(28.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.docsy_logo),
                    contentDescription = "Docsy Logo",
                    colorFilter = ColorFilter.tint(
                        if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Docsy",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your on-device personal knowledge store",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showForceReadDialog) {
            com.suhrud.docsy.ui.reading.ForceReadDialog(
                isReadingActive = readingProgress.isReading,
                onDismiss = { showForceReadDialog = false },
                onQuickRefresh = {
                    onTriggerQuickRefresh()
                },
                onFullReRead = {
                    onTriggerFullReRead()
                }
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 16.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        pInfo.versionName ?: "1.0.6"
    } catch (_: Exception) {
        "1.0.6"
    }
}
