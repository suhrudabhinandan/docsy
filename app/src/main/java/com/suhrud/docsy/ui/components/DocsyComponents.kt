package com.suhrud.docsy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suhrud.docsy.data.model.ExamScheduleItem
import com.suhrud.docsy.ui.theme.BorderLight
import com.suhrud.docsy.ui.theme.LightSurface
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextMuted
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary

@Composable
fun ScheduleTable(
    items: List<ExamScheduleItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, BorderLight, RoundedCornerShape(20.dp))
            .background(PureWhite)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SUBJECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.weight(2f)
                )
                Text(
                    text = "DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1.5f)
                )
                Text(
                    text = "TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = BorderLight, thickness = 0.5.dp)

            for (item in items) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.subject,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary,
                        modifier = Modifier.weight(2f)
                    )
                    Text(
                        text = item.date,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextSecondary,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = item.time.ifBlank { "-" },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
