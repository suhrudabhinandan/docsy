package com.suhrud.docsy.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suhrud.docsy.ui.DocsyViewModel
import com.suhrud.docsy.ui.components.AvatarView
import com.suhrud.docsy.ui.components.DocsyCheckIcon
import com.suhrud.docsy.ui.components.DocsyLockIcon
import com.suhrud.docsy.ui.components.DocsySearchIcon
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.LightSurface
import com.suhrud.docsy.ui.theme.OffWhite
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextMuted
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SimpleOnboardingScreen(
    viewModel: DocsyViewModel,
    onChooseFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val step by viewModel.onboardingStep.collectAsState()
    val introIndex by viewModel.introStepIndex.collectAsState()
    val currentName by viewModel.userName.collectAsState()
    val selectedAvatar by viewModel.profileIconIndex.collectAsState()

    var inputName by remember(currentName) { mutableStateOf(currentName) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = step > 1) {
        viewModel.previousOnboardingStep()
    }

    val submitName: () -> Unit = {
        if (inputName.trim().isBlank()) {
            nameError = "Please enter your name"
        } else if (!isSubmitting) {
            isSubmitting = true
            nameError = null
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            viewModel.setUserName(inputName.trim())
            coroutineScope.launch {
                delay(180)
                viewModel.advanceOnboardingStep()
                isSubmitting = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.triggerDeviceSync()
        viewModel.advanceOnboardingStep()
    }

    val requestAllPermissions: () -> Unit = {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        permissions.add(Manifest.permission.READ_SMS)
        permissions.add(Manifest.permission.READ_CALL_LOG)
        permissions.add(Manifest.permission.READ_CONTACTS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 28.dp)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "OnboardingTransition"
        ) { targetStep ->
            when (targetStep) {
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Column {
                            Text(
                                text = "Docsy",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1.0).sp,
                                    fontSize = 42.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Ask your documents anything.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { viewModel.advanceOnboardingStep() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_welcome_continue")
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Column {
                            Text(
                                text = "What's your name?",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(32.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (nameError != null) 1.5.dp else 1.dp,
                                        color = if (nameError != null) TextPrimary else BorderLight,
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .background(PureWhite, RoundedCornerShape(24.dp))
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                BasicTextField(
                                    value = inputName,
                                    onValueChange = {
                                        inputName = it
                                        if (it.isNotBlank()) nameError = null
                                    },
                                    textStyle = MaterialTheme.typography.titleLarge.copy(color = TextPrimary),
                                    cursorBrush = SolidColor(TextPrimary),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submitName() }),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_user_name")
                                )
                            }

                            if (nameError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = nameError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = { submitName() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_name_continue")
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                3 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Choose your avatar",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(16) { idx ->
                                    val isSelected = (idx == selectedAvatar)
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(CircleShape)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) TextPrimary else BorderLight,
                                                shape = CircleShape
                                            )
                                            .background(if (isSelected) LightSurface else PureWhite)
                                            .clickable { viewModel.setProfileIcon(idx) }
                                            .padding(6.dp)
                                            .testTag("avatar_option_$idx"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AvatarView(avatarIndex = idx, size = 46.dp)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.advanceOnboardingStep() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_avatar_continue")
                        ) {
                            Text(
                                text = "Continue",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                4 -> {
                    val titles = listOf(
                        "Give Docsy access.",
                        "Ask naturally.",
                        "Docsy finds it.",
                        "Get the answer.",
                        "Your data stays with you."
                    )
                    val subtitles = listOf(
                        "Allow once. Docsy keeps your information ready.",
                        "Type what you want to know.",
                        "Your files are searched on your device.",
                        "Docsy gives you what you asked for.",
                        "Docsy is designed to work on your device."
                    )

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0 until 5) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(if (i == introIndex) TextPrimary else BorderLight)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.skipOnboardingIntro() },
                                    modifier = Modifier.testTag("btn_intro_skip")
                                ) {
                                    Text(
                                        text = "Skip",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(LightSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                when (introIndex) {
                                    0 -> Step01AccessVisual()
                                    1 -> Step02SearchVisual()
                                    2 -> Step03FindsItVisual()
                                    3 -> Step04AnswerVisual()
                                    else -> Step05LockVisual()
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = titles[introIndex],
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 34.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = subtitles[introIndex],
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (introIndex > 0) {
                                Button(
                                    onClick = { viewModel.previousOnboardingStep() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LightSurface,
                                        contentColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(26.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                ) {
                                    Text("Back", style = MaterialTheme.typography.titleMedium)
                                }
                            }

                            Button(
                                onClick = { viewModel.advanceOnboardingStep() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TextPrimary,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(26.dp),
                                modifier = Modifier
                                    .weight(if (introIndex > 0) 2f else 1f)
                                    .height(56.dp)
                                    .testTag("btn_intro_next")
                            ) {
                                Text(
                                    text = if (introIndex == 4) "Get Started" else "Next",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                5 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Column {
                            Text(
                                text = "Let Docsy see your files.",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Docsy indexes your local documents, SMS, call history and step count directly on your phone so you can ask anything offline.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                PermissionItem("Storage & Files", "To search your documents and PDFs")
                                PermissionItem("Messages & Calls", "To answer queries about SMS and call logs")
                                PermissionItem("Activity & Sensor", "To answer questions about your steps")
                            }
                        }

                        Button(
                            onClick = { requestAllPermissions() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_allow_access")
                        ) {
                            Text(
                                text = "Allow Access",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }

                6 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Column {
                            Text(
                                text = "Ready.",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-1.0).sp,
                                    fontSize = 42.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Docsy has indexed your accessible documents locally on this device.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { viewModel.completeOnboarding() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TextPrimary,
                                contentColor = PureWhite
                            ),
                            shape = RoundedCornerShape(26.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("btn_finish_onboarding")
                        ) {
                            Text(
                                text = "Start Asking",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
            .background(PureWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocsyCheckIcon(size = 16.dp, tint = TextPrimary)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }
}

@Composable
fun Step01AccessVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse1")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(20.dp))
            .background(PureWhite)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocsyCheckIcon(tint = TextPrimary, size = 16.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Allow Access",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
    }
}

@Composable
fun Step02SearchVisual() {
    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .clip(RoundedCornerShape(24.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(24.dp))
            .background(PureWhite)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocsySearchIcon(tint = TextMuted, size = 16.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(18.dp)
                    .background(if (cursorVisible) TextPrimary else Color.Transparent)
            )
        }
    }
}

@Composable
fun Step03FindsItVisual() {
    var dotCount by remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            dotCount = (dotCount % 3) + 1
        }
    }

    val dots = ".".repeat(dotCount)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(20.dp))
            .background(PureWhite)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Finding$dots",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
    }
}

@Composable
fun Step04AnswerVisual() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(22.dp))
            .background(PureWhite)
            .padding(horizontal = 28.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BorderLight)
            )
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LightSurface)
            )
        }
    }
}

@Composable
fun Step05LockVisual() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.5.dp, TextPrimary, RoundedCornerShape(20.dp))
            .background(PureWhite)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DocsyLockIcon(tint = TextPrimary, size = 18.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "100% On-Device & Offline",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
    }
}
