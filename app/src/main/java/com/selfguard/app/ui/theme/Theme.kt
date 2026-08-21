package com.selfguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.selfguard.app.core.RiskBand

/**
 * Die Self-Markenfarben, übernommen aus `brand-kit/tokens.css`.
 *
 * Werte nicht abwandeln: das Brandkit ist die eine Quelle der Wahrheit für
 * alle Self-Projekte. Ändert sich dort etwas, wird es hier nachgezogen --
 * nicht umgekehrt.
 */
object Self {
    // Flächen, dunkel von tief nach hoch
    val bg0 = Color(0xFF080C11)   // Seite
    val bg1 = Color(0xFF0D1117)   // Sektion
    val bg2 = Color(0xFF161B22)   // Karte
    val bg3 = Color(0xFF1C2333)   // Eingabe, Hover

    // Marke
    val teal = Color(0xFF33A78C)
    val tealBright = Color(0xFF43D3AD)
    val tealDeep = Color(0xFF1F7763)
    val cyan = Color(0xFF00E5C8)

    // Akzente
    val ice = Color(0xFF9DBDD0)
    val iceBright = Color(0xFFBCD9E9)
    val blue = Color(0xFF1DB8D4)

    // Status
    val green = Color(0xFF3FB950)
    val red = Color(0xFFF85149)
    val yellow = Color(0xFFD29922)

    val text = Color(0xFFE6EDF3)
    val textMuted = Color(0xFF8B98A5)
}

private val SelfGuardColors = darkColorScheme(
    primary = Self.teal,
    onPrimary = Color(0xFF04120F),
    secondary = Self.tealDeep,
    onSecondary = Self.text,
    tertiary = Self.ice,
    background = Self.bg0,
    onBackground = Self.text,
    surface = Self.bg2,
    onSurface = Self.text,
    surfaceVariant = Self.bg3,
    onSurfaceVariant = Self.textMuted,
    error = Self.red,
    onError = Color(0xFF1A0605),
    outline = Color(0xFF2A3440),
)

/**
 * Farbsprache der Bewertungsstufen.
 *
 * Bewusst nicht durchgängig rot: Wenn jede Auffälligkeit gleich alarmiert,
 * verliert der Alarm seine Bedeutung.
 */
object BandColors {
    val sauber = Self.green
    val beobachten = Self.yellow
    val bedenklich = Color(0xFFDB6D28)
    val alarm = Self.red

    fun of(band: RiskBand): Color = when (band) {
        RiskBand.SAUBER -> sauber
        RiskBand.BEOBACHTEN -> beobachten
        RiskBand.BEDENKLICH -> bedenklich
        RiskBand.ALARM -> alarm
    }
}

@Composable
fun SelfGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SelfGuardColors, content = content)
}
