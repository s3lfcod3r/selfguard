package com.selfguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.net.BlocklistTier
import com.selfguard.app.ui.theme.Self

/**
 * Alles Technische an einer Stelle.
 *
 * Bewusst hinter dem Zahnrad versteckt: Wer die App nur benutzen will, soll
 * nie hierher müssen. Wer es genau wissen will, findet hier jede Stellschraube
 * und jede Datenquelle.
 */
@Composable
fun SettingsScreen(
    tier: BlocklistTier,
    blockedDomains: Int,
    updateState: ListUpdateState,
    iocSummary: List<String>,
    reputationEnabled: Boolean,
    reputationEndpoint: String,
    onBack: () -> Unit,
    onTierChange: (BlocklistTier) -> Unit,
    onUpdateLists: () -> Unit,
    onUpdateIocs: () -> Unit,
    onToggleReputation: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
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
            Text("Einstellungen", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Self.text)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Sperrliste für den Werbeblocker")
        Spacer(Modifier.height(8.dp))
        BlocklistTier.entries.forEach { entry ->
            TierRow(entry, entry == tier) { onTierChange(entry) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        Text(listStatusText(blockedDomains, updateState), fontSize = 12.sp, color = statusColor(updateState))
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onUpdateLists,
            modifier = Modifier.fillMaxWidth(),
            enabled = updateState !is ListUpdateState.Läuft,
        ) {
            Text(if (updateState is ListUpdateState.Läuft) "Wird geladen ..." else "Jetzt aktualisieren")
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle("Online-Abgleich von Dateien")
        Spacer(Modifier.height(8.dp))
        ReputationSection(
            enabled = reputationEnabled,
            endpoint = reputationEndpoint,
            onToggle = onToggleReputation,
            onEndpointChange = onEndpointChange,
        )

        Spacer(Modifier.height(28.dp))
        SectionTitle("Woher die Erkennung ihr Wissen hat")
        Spacer(Modifier.height(8.dp))
        Sources(iocSummary, onUpdateIocs)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Self.textMuted)
}

private fun listStatusText(blockedDomains: Int, state: ListUpdateState): String = when (state) {
    is ListUpdateState.Ruht ->
        if (blockedDomains == 0) "Noch keine Liste geladen."
        else "$blockedDomains Domains geladen."
    is ListUpdateState.Läuft -> "Lädt ${state.source} ..."
    is ListUpdateState.Fertig -> "${state.domains} Domains aus ${state.sources.size} Quellen."
    is ListUpdateState.Fehler -> "Aktualisierung fehlgeschlagen: ${state.message}"
}

@Composable
private fun statusColor(state: ListUpdateState) =
    if (state is ListUpdateState.Fehler) Self.red else Self.textMuted

@Composable
private fun TierRow(tier: BlocklistTier, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Self.bg3 else Self.bg2
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tier.label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) Self.teal else Self.text,
                )
                Text(tier.description, fontSize = 12.sp, color = Self.textMuted)
            }
            if (selected) {
                Text("aktiv", fontSize = 11.sp, color = Self.teal)
            }
        }
    }
}

@Composable
private fun ReputationSection(
    enabled: Boolean,
    endpoint: String,
    onToggle: (Boolean) -> Unit,
    onEndpointChange: (String) -> Unit,
) {
    var draft by remember(endpoint) { mutableStateOf(endpoint) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Zusätzlich online nachfragen", fontSize = 15.sp, color = Self.text)
                    Text(
                        "Unbekannte Dateien beim eigenen Dienst prüfen",
                        fontSize = 12.sp,
                        color = Self.textMuted,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Adresse des eigenen Dienstes", fontSize = 12.sp) },
                placeholder = { Text("https://...workers.dev", fontSize = 12.sp) },
            )
            TextButton(onClick = { onEndpointChange(draft) }, enabled = draft != endpoint) {
                Text("Speichern", fontSize = 13.sp)
            }

            if (endpoint.isNotBlank() && !endpoint.startsWith("https://", ignoreCase = true)) {
                Text(
                    "Wird nicht verwendet: nur https ist erlaubt. Unverschlüsselt " +
                        "könnte jeder im Netz mitlesen, welche Dateien Sie prüfen.",
                    fontSize = 11.sp,
                    color = Self.red,
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                "Ab Werk aus. Jede Abfrage verrät dem Dienst, dass eine bestimmte " +
                    "Datei auf diesem Gerät liegt. Ohne Adresse arbeitet die App " +
                    "vollständig ohne Internet.",
                fontSize = 11.sp,
                color = Self.textMuted,
            )
        }
    }
}

/**
 * Herkunft der Daten -- zugleich die von der CC-BY-Lizenz geforderte
 * Namensnennung für den Stalkerware-Datensatz.
 */
@Composable
private fun Sources(iocSummary: List<String>, onUpdateIocs: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(14.dp)) {
            Source(
                "Stalkerware-Indikatoren",
                "Echap (echap.eu.org), Lizenz CC-BY 4.0",
            )
            iocSummary.forEach {
                Text(it, fontSize = 11.sp, color = Self.textMuted)
            }

            Spacer(Modifier.height(14.dp))
            Source(
                "Sperrlisten",
                "HaGeZi DNS-Blocklists, GPL-3.0. Werden zur Laufzeit geladen " +
                    "und sind nicht Teil dieser App.",
            )

            Spacer(Modifier.height(14.dp))
            Source("Root-Erkennung", "RootBeer, Apache-2.0")

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onUpdateIocs, modifier = Modifier.fillMaxWidth()) {
                Text("Indikatoren aktualisieren")
            }
        }
    }
}

@Composable
private fun Source(title: String, detail: String) {
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Self.text)
    Text(detail, fontSize = 12.sp, color = Self.textMuted)
}
