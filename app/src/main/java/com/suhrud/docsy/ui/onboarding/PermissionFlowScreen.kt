package com.suhrud.docsy.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.suhrud.docsy.R

fun isPermissionDeclared(context: Context, permission: String): Boolean {
    return try {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        info.requestedPermissions?.contains(permission) == true
    } catch (_: Exception) {
        false
    }
}

@Composable
fun PermissionFlowScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var allGranted by remember { mutableStateOf(checkAllPermissionsGranted(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        allGranted = checkAllPermissionsGranted(context)
    }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        allGranted = checkAllPermissionsGranted(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allGranted = checkAllPermissionsGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestAllPermissions = {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (isPermissionDeclared(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (isPermissionDeclared(context, Manifest.permission.READ_CONTACTS)) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }
        if (isPermissionDeclared(context, Manifest.permission.READ_SMS)) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (isPermissionDeclared(context, Manifest.permission.READ_CALL_LOG)) {
            permissionsToRequest.add(Manifest.permission.READ_CALL_LOG)
        }

        if (isPermissionDeclared(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            try {
                manageStorageLauncher.launch(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(fallbackIntent)
                } catch (_: Exception) {
                    if (permissionsToRequest.isNotEmpty()) {
                        try {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        } catch (_: Exception) {}
                    }
                }
            }
        } else if (permissionsToRequest.isNotEmpty()) {
            try {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
            .testTag("permission_flow_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Keep your data ready",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Docsy processes everything offline on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isPermissionDeclared(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE) ||
            isPermissionDeclared(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        ) {
            PermissionRationaleCard(
                icon = Icons.Default.Description,
                title = "Files and Documents",
                description = "Reads PDF marksheets, bills, CVs, and spreadsheets to extract text and answer questions."
            )
            Spacer(modifier = Modifier.height(12.dp))
        }



        if (isPermissionDeclared(context, Manifest.permission.READ_CONTACTS) ||
            isPermissionDeclared(context, Manifest.permission.READ_CALL_LOG)
        ) {
            PermissionRationaleCard(
                icon = Icons.Default.PermContactCalendar,
                title = "Contacts and Calls",
                description = "Helps you look up phone numbers and view missed calls directly in chat."
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isPermissionDeclared(context, Manifest.permission.READ_SMS)) {
            PermissionRationaleCard(
                icon = Icons.Default.Sms,
                title = "Messages",
                description = "Allows you to search your SMS messages on device for OTPs, codes, and reminders."
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (allGranted) {
                    onContinue()
                } else {
                    requestAllPermissions()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(52.dp)
                .testTag(if (allGranted) "continue_anyway_button" else "grant_permissions_button")
        ) {
            Text(
                text = stringResource(if (allGranted) R.string.continue_label else R.string.allow_access),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun checkAllPermissionsGranted(context: Context): Boolean {
    val hasStorage = if (isPermissionDeclared(context, Manifest.permission.MANAGE_EXTERNAL_STORAGE)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    } else if (isPermissionDeclared(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val hasContacts = if (isPermissionDeclared(context, Manifest.permission.READ_CONTACTS)) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasSms = if (isPermissionDeclared(context, Manifest.permission.READ_SMS)) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    val hasCalls = if (isPermissionDeclared(context, Manifest.permission.READ_CALL_LOG)) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    } else true

    return hasStorage && hasContacts && hasSms && hasCalls
}

@Composable
fun PermissionRationaleCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}
