package com.suhrud.docsy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suhrud.docsy.ui.theme.DocsyTheme
import com.suhrud.docsy.ui.theme.OffWhite
import com.suhrud.docsy.ui.theme.PureWhite
import com.suhrud.docsy.ui.theme.TextPrimary
import com.suhrud.docsy.ui.theme.TextSecondary

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DocsyTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OffWhite)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Health Connect Access",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Docsy uses Health Connect to read your step count history offline on your device so you can ask about your walking activity.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { finish() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPrimary,
                            contentColor = PureWhite
                        )
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    }
}
