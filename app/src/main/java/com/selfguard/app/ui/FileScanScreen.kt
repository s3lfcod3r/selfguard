package com.selfguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.core.FindingTexts
import com.selfguard.app.scan.files.FileScanResult
import com.selfguard.app.ui.theme.Self

/** Zustand des Dateidurchlaufs. */
sealed interface FileScanState {
    data object Bereit : FileScanState
    data class Läuft(val scanned: Int, val currentName: String) : FileScanState
    data class Fertig(val result: FileScanResult) : FileScanState
}

/**
 * Dateiprüfung über einen selbst gewählten Ordner.
 *
 * Ein Knopf, ein Ergebnis. Die Einstellung für den Online-Abgleich liegt
 * bewusst nicht hier, sondern bei den Einstellungen -- hier soll man nur
 * prüfen, nicht konfigurieren.
 */
@Composable
fun FileScanScreen(
    state: FileScanState,
    lang: String,
    onBack: () -> Unit,
    onPickFolder: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Self.bg0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Zurück",
                tint = Self.text,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBack)
                    .padding(8.dp)
                    .size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Dateien prüfen",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.text,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Wählen Sie einen Ordner -- meist Downloads. Jede Datei wird mit " +
                "bekannter Schadsoftware abgeglichen. Ein Treffer ist eindeutig.",
            fontSize = 13.sp,
            color = Self.textMuted,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPickFolder,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = state !is FileScanState.Läuft,
            colors = ButtonDefaults.buttonColors(
                containerColor = Self.teal,
                contentColor = Color(0xFF04120F),
            ),
        ) {
            Text(
                if (state is FileScanState.Läuft) "Prüfung läuft ..." else "Ordner wählen",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "SelfGuard fordert bewusst keinen Vollzugriff auf den Speicher an. " +
                "Sie geben genau den Ordner frei, der geprüft werden soll.",
            fontSize = 11.sp,
            color = Self.textMuted,
        )

        Spacer(Modifier.height(24.dp))
        when (state) {
            is FileScanState.Bereit -> Unit
            is FileScanState.Läuft -> Running(state)
            is FileScanState.Fertig -> Result(state, lang)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Running(state: FileScanState.Läuft) {
    Column {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Self.teal)
        Spacer(Modifier.height(10.dp))
        Text("${state.scanned} Dateien geprüft", fontSize = 13.sp, color = Self.text)
        Text(state.currentName, fontSize = 11.sp, color = Self.textMuted)
    }
}

@Composable
private fun Result(state: FileScanState.Fertig, lang: String) {
    val result = state.result
    val megabytes = result.bytesScanned / (1024 * 1024)
    val clean = result.findings.isEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (clean) "Nichts gefunden" else "${result.findings.size} Treffer",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (clean) Self.green else Self.red,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${result.filesScanned} Dateien, $megabytes MB geprüft" +
                    if (result.skipped > 0) ", ${result.skipped} übersprungen" else "",
                fontSize = 12.sp,
                color = Self.textMuted,
            )
        }
    }

    result.findings.forEach { finding ->
        Spacer(Modifier.height(10.dp))
        val text = FindingTexts.of(finding.code, lang)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Self.bg2),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    finding.subjectLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Self.red,
                )
                Spacer(Modifier.height(6.dp))
                Text(text.detail, fontSize = 13.sp, color = Self.text, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                finding.evidence.forEach {
                    Text("- $it", fontSize = 11.sp, color = Self.textMuted)
                }
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Self.bg3),
                ) {
                    Text(
                        text.advice,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = Self.iceBright,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}
