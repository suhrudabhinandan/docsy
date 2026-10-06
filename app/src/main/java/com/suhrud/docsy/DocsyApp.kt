package com.suhrud.docsy

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suhrud.docsy.ui.DocsyViewModel
import com.suhrud.docsy.ui.screens.SimpleHomeScreen
import com.suhrud.docsy.ui.screens.SimpleOnboardingScreen
import com.suhrud.docsy.ui.screens.SimpleSettingsSheet
import com.suhrud.docsy.ui.theme.DocsyTheme

@Composable
fun DocsyApp(
    viewModel: DocsyViewModel = viewModel()
) {
    val context = LocalContext.current

    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val isAppLocked by viewModel.isAppLocked.collectAsState()

    // File picker launcher using Storage Access Framework (SAF)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            var displayName = "Imported_Document"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            viewModel.importFile(context, uri, displayName, mimeType)
            Toast.makeText(context, "Added $displayName", Toast.LENGTH_SHORT).show()
        }
    }

    val onChooseFiles = {
        filePickerLauncher.launch(
            arrayOf(
                "application/pdf",
                "image/*",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    DocsyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (isAppLocked) {
                // Biometric / PIN Lock Screen
                com.suhrud.docsy.ui.screens.AppLockScreen(
                    appLockManager = viewModel.appLockManager,
                    onUnlockSuccess = { viewModel.unlockApp() }
                )
            } else {
                AnimatedContent(
                    targetState = isOnboardingCompleted,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "AppScreenTransition"
                ) { completed ->
                    if (!completed) {
                        SimpleOnboardingScreen(
                            viewModel = viewModel,
                            onChooseFilesClick = onChooseFiles
                        )
                    } else {
                        SimpleHomeScreen(
                            viewModel = viewModel
                        )
                    }
                }

                // Minimal Settings Bottom Sheet
                if (isSettingsOpen) {
                    SimpleSettingsSheet(
                        viewModel = viewModel,
                        onAddFilesClick = onChooseFiles
                    )
                }
            }
        }
    }
}
