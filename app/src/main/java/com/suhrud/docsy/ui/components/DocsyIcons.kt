package com.suhrud.docsy.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.suhrud.docsy.ui.theme.TextPrimary

@Composable
fun DocsySearchIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.Default.Search,
        contentDescription = "Search",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyBackIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyArrowRightIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = "Continue",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyCloseIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.Default.Close,
        contentDescription = "Close",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyCheckIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = "Check",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyLockIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "Locked",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyLockOpenIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.Default.LockOpen,
        contentDescription = "Unlocked",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DocsyOpenIcon(modifier: Modifier = Modifier, tint: Color = TextPrimary, size: Dp = 20.dp) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
        contentDescription = "Open",
        tint = tint,
        modifier = modifier.size(size)
    )
}
