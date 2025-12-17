package com.pbrp.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun PBRPTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PBRP_Dark,
            surface = PBRP_Card,
            primary = PBRP_Red
        ),
        content = content
    )
}
