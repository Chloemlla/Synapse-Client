package com.synapse.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SynapseColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    secondary = Color(0xFF0F766E),
    tertiary = Color(0xFF9333EA),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    error = Color(0xFFB42318),
)

@Composable
fun SynapseMobileTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SynapseColorScheme,
        content = content,
    )
}
