package com.pbrp.manager.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val PBRP_Red = Color(0xFFD32F2F)
val PBRP_Purple = Color(0xFF9400D3)
val PBRP_Dark = Color(0xFF000000) // Pure black as per website
val PBRP_Card = Color(0xFF121212) // Zinc-900 equivalent
val PBRP_Orange = Color(0xFFFF9800)

// Website Gradients
val PBRP_Gradient = Brush.linearGradient(
    colors = listOf(PBRP_Red, PBRP_Purple)
)

// Warning Colors (from website _includes)
val Warn_Blue_Bg = Color(0xFF1E3A8A).copy(alpha = 0.2f) // Blue-900/20
val Warn_Blue_Border = Color(0xFF3B82F6) // Blue-500
val Warn_Red_Bg = Color(0xFF7F1D1D).copy(alpha = 0.2f) // Red-900/20
val Warn_Red_Border = Color(0xFFEF4444) // Red-500
val Warn_Purple_Bg = Color(0xFF581C87).copy(alpha = 0.2f) // Purple-900/20
val Warn_Purple_Border = Color(0xFFA855F7) // Purple-500
