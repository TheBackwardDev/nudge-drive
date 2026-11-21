package com.nudge.drive.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private fun variable(file: String, weight: Int) = Font(
    "fonts/$file", FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

actual val Sora: FontFamily = FontFamily((400..800 step 100).map { variable("sora_variable.ttf", it) })
actual val Inter: FontFamily = FontFamily((400..800 step 100).map { variable("inter_variable.ttf", it) })

@Composable
internal actual fun SystemBarsEffect(dark: Boolean) = Unit
