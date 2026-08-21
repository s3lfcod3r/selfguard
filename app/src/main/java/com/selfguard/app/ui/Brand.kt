package com.selfguard.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.selfguard.app.R
import com.selfguard.app.ui.theme.Self

/**
 * Marken-Schrift. Orbitron liegt **lokal** im Projekt, nicht über Google
 * Fonts -- so schreibt es die Self-Markenrichtlinie vor.
 */
val Orbitron = FontFamily(Font(R.font.orbitron, FontWeight.Bold))

/**
 * Der Self-Schriftzug: "Self" in Eis-Blau, der Projektteil in Teal.
 *
 * Aufbau und Farben stammen aus `brand-kit/logo/LOGO.md` und sind für alle
 * Self-Projekte gleich -- nur der zweite Teil wechselt.
 */
@Composable
fun SelfGuardWordmark(
    fontSize: TextUnit = 26.sp,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Self.ice)) { append("Self") }
            withStyle(SpanStyle(color = Self.teal)) { append("Guard") }
        },
        fontFamily = Orbitron,
        fontSize = fontSize,
    )
}
