package com.suhrud.docsy.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.suhrud.docsy.ui.onboarding.openAppSettings

data class AccessItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isBlockedByAndroid: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessPermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accessList = checkRealPermissions(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What Docsy can access", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("access_back_button")) {
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
                .testTag("access_permissions_content")
        ) {
            Text(
                text = "Live On-Device Permission Audit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Docsy runs 100% offline. No data ever leaves your device. Below is what Android currently allows Docsy to read.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            accessList.forEach { item ->
                AccessStatusCard(item = item)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { openAppSettings(context) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("open_app_settings_button")
            ) {
                Text("Open Android App Settings")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AccessStatusCard(item: AccessItem) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Close,
                contentDescription = if (item.isGranted) "Granted" else "Not Granted",
                tint = if (item.isGranted) {
                    MaterialTheme.colorScheme.primary
                } else if (item.isBlockedByAndroid) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 2.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private fun checkRealPermissions(context: Context): List<AccessItem> {
    val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    val hasMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        hasStorage
    }

    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    val hasCalls = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    return listOf(
        AccessItem(
            title = "All Files Access",
            description = if (hasStorage) "Granted. Docsy can search PDFs, spreadsheets, and documents." else "Not granted. Document search is restricted to media folders.",
            isGranted = hasStorage
        ),
        AccessItem(
            title = "Photos & Media",
            description = if (hasMedia) "Granted. Images and receipts can be read locally." else "Not granted. Photos cannot be scanned for text.",
            isGranted = hasMedia
        ),
        AccessItem(
            title = "Contacts",
            description = if (hasContacts) "Granted. You can query contacts and phone numbers in chat." else "Not granted. Contact lookups will ask to grant permission.",
            isGranted = hasContacts
        ),
        AccessItem(
            title = "SMS Messages",
            description = if (hasSms) "Granted. Docsy can search on-device messages." else "Not granted. SMS queries are unavailable.",
            isGranted = hasSms
        ),
        AccessItem(
            title = "Call Log",
            description = if (hasCalls) "Granted. Call history queries are available." else "Not granted. Call records are unavailable.",
            isGranted = hasCalls
        ),
        AccessItem(
            title = "Android/data and Android/obb",
            description = "Blocked by Android OS design (Android 11+). Docsy respects official platform security boundaries.",
            isGranted = false,
            isBlockedByAndroid = true
        ),
        AccessItem(
            title = "Network / Internet Access",
            description = "Disabled by design (Rule R1). Docsy operates with zero network traffic.",
            isGranted = false,
            isBlockedByAndroid = true
        )
    )
}
