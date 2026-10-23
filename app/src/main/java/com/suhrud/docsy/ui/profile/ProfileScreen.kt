package com.suhrud.docsy.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.data.preferences.UserProfile
import com.suhrud.docsy.ui.avatar.AvatarPickerBottomSheet
import com.suhrud.docsy.ui.avatar.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    onSaveProfile: (name: String, alias: String?, avatarId: String) -> Unit,
    onBack: () -> Unit
) {
    // Edit mode survives rotation (rememberSaveable), never starts open by default (false)
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(userProfile.name) }
    var editAlias by rememberSaveable { mutableStateOf(userProfile.alias ?: "") }
    var editAvatarId by rememberSaveable { mutableStateOf(userProfile.avatarId) }
    var showAvatarPicker by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf(false) }

    // System back cancels edit mode first
    BackHandler {
        if (showAvatarPicker) {
            showAvatarPicker = false
        } else if (isEditing) {
            // Revert changes on cancel
            editName = userProfile.name
            editAlias = userProfile.alias ?: ""
            editAvatarId = userProfile.avatarId
            isEditing = false
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                editName = userProfile.name
                                editAlias = userProfile.alias ?: ""
                                editAvatarId = userProfile.avatarId
                                isEditing = false
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isEditing) {
                        IconButton(
                            onClick = {
                                editName = userProfile.name
                                editAlias = userProfile.alias ?: ""
                                editAvatarId = userProfile.avatarId
                                isEditing = true
                            },
                            modifier = Modifier.testTag("profile_edit_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("profile_screen_content"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Large Avatar
            val activeAvatarId = if (isEditing) editAvatarId else userProfile.avatarId
            ProfileAvatar(
                avatarId = activeAvatarId,
                size = 120.dp,
                ring = true,
                modifier = Modifier.testTag("profile_avatar")
            )

            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = { showAvatarPicker = true },
                    modifier = Modifier.testTag("change_avatar_button")
                ) {
                    Text(text = "Change avatar", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!isEditing) {
                // READ-ONLY MODE
                Text(
                    text = userProfile.name.ifEmpty { "User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("profile_name_text")
                )

                if (!userProfile.alias.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Name on documents: ${userProfile.alias}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        modifier = Modifier.testTag("profile_alias_text")
                    )
                }
            } else {
                // EDIT MODE
                OutlinedTextField(
                    value = editName,
                    onValueChange = {
                        editName = it
                        if (nameError && it.trim().isNotEmpty()) nameError = false
                    },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name cannot be empty", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_name_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editAlias,
                    onValueChange = { editAlias = it },
                    label = { Text("Name on documents (optional alias)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = editName.trim()
                        if (trimmed.isEmpty()) {
                            nameError = true
                        } else {
                            onSaveProfile(trimmed, editAlias.trim().ifEmpty { null }, editAvatarId)
                            isEditing = false
                        }
                    }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_alias_input")
                )

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            editName = userProfile.name
                            editAlias = userProfile.alias ?: ""
                            editAvatarId = userProfile.avatarId
                            isEditing = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_edit_button")
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val trimmed = editName.trim()
                            if (trimmed.isEmpty()) {
                                nameError = true
                            } else {
                                onSaveProfile(trimmed, editAlias.trim().ifEmpty { null }, editAvatarId)
                                isEditing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_profile_button")
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showAvatarPicker) {
        AvatarPickerBottomSheet(
            selectedId = editAvatarId,
            onSelect = {
                editAvatarId = it
                showAvatarPicker = false
            },
            onDismiss = { showAvatarPicker = false }
        )
    }
}
