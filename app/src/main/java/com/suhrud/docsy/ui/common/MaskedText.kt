package com.suhrud.docsy.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object IdMasker {

    /**
     * Masks an ID showing only the last 4 characters by default.
     * e.g., Aadhaar "1234 5678 9012" -> "•••• •••• 9012"
     * PAN "ABCDE1234F" -> "••••••234F"
     */
    fun mask(raw: String, idType: String = ""): String {
        val clean = raw.trim()
        if (clean.length <= 4) return "••••"

        return when (idType.uppercase()) {
            "AADHAAR" -> {
                val digitsOnly = clean.filter { it.isDigit() }
                if (digitsOnly.length == 12) {
                    "•••• •••• " + digitsOnly.takeLast(4)
                } else {
                    "•".repeat(clean.length - 4) + clean.takeLast(4)
                }
            }
            "PAN" -> {
                "••••••" + clean.takeLast(4)
            }
            else -> {
                "•".repeat((clean.length - 4).coerceAtLeast(4)) + " " + clean.takeLast(4)
            }
        }
    }
}

/**
 * Masked ID text composable with tap-to-reveal toggle.
 * Satisfies Phase 7.10 in AUDIT.md.
 */
@Composable
fun MaskedText(
    fullText: String,
    idType: String = "",
    modifier: Modifier = Modifier,
    testTag: String = "masked_text"
) {
    var isRevealed by remember { mutableStateOf(false) }
    val maskedText = remember(fullText, idType) { IdMasker.mask(fullText, idType) }

    Row(
        modifier = modifier
            .clickable { isRevealed = !isRevealed }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(targetState = isRevealed, label = "mask_toggle") { revealed ->
            Text(
                text = if (revealed) fullText else maskedText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (isRevealed) "Hide ID" else "Reveal ID",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
