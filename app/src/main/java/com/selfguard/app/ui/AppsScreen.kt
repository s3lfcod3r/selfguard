package com.selfguard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.core.RiskBand
import com.selfguard.app.scan.apps.AppAction
import com.selfguard.app.scan.apps.AppActions
import com.selfguard.app.scan.apps.InstallOrigin
import com.selfguard.app.scan.apps.InstalledApp
import com.selfguard.app.ui.theme.BandColors
import com.selfguard.app.ui.theme.Self

/** Eine Zeile in der App-Liste, angereichert um das Scan-Ergebnis. */
data class AppRow(
    val app: InstalledApp,
    val band: RiskBand?,
)

/**
 * Alle installierten Apps -- ansehen, mehrere ankreuzen, nacheinander entfernen.
 *
 * Wichtig für die Sicherheit des Geräts: **Systembestandteile werden nicht
 * gelöscht.** Sie sind ankreuzbar, führen aber auf ihre Systemseite, wo der
 * Schalter zum Deaktivieren sitzt. An der Zeile steht jeweils, was möglich
 * ist -- entfernen, zurücksetzen oder deaktivieren -- damit niemand eine
 * andere Wirkung erwartet als die eintretende.
 *
 * Entfernt wird außerdem einzeln und mit Rückfrage: Für jede angekreuzte
 * App erscheint der gewohnte Android-Dialog. Nichts verschwindet ungefragt.
 */
@Composable
fun AppsScreen(
    rows: List<AppRow>,
    loading: Boolean,
    onBack: () -> Unit,
    onUninstall: (List<String>) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<String>()) }
    var onlyUserApps by remember { mutableStateOf(true) }

    val visible = remember(rows, onlyUserApps) {
        rows
            .filter { !onlyUserApps || !it.app.isSystemApp }
            .sortedWith(
                compareByDescending<AppRow> { it.band?.ordinal ?: -1 }
                    .thenBy { it.app.label.lowercase() }
            )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Self.bg0)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
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
                    "Installierte Apps",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Self.text,
                )
            }

            Spacer(Modifier.height(12.dp))
            FilterRow(onlyUserApps) { onlyUserApps = it }
            Spacer(Modifier.height(8.dp))
            Text(
                if (onlyUserApps) {
                    "Alles, was nicht ab Werk auf dem Gerät war -- von Ihnen " +
                        "installiert oder vom Hersteller nachgeladen. Woher genau, " +
                        "steht bei jeder App."
                } else {
                    "Zusätzlich die Systembestandteile, die fest zum Gerät gehören."
                },
                fontSize = 11.sp,
                color = Self.textMuted,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (loading) {
            Text(
                "Apps werden gelesen ...",
                modifier = Modifier.padding(horizontal = 20.dp),
                fontSize = 13.sp,
                color = Self.textMuted,
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = { it.app.packageName }) { row ->
                AppRowCard(
                    row = row,
                    checked = row.app.packageName in selected,
                    onToggle = { checked ->
                        selected = if (checked) {
                            selected + row.app.packageName
                        } else {
                            selected - row.app.packageName
                        }
                    },
                    onOpenDetails = { onOpenDetails(row.app.packageName) },
                )
            }
        }

        if (selected.isNotEmpty()) {
            RemoveBar(visible.filter { it.app.packageName in selected }.map { it.app }) {
                onUninstall(selected.toList())
                selected = emptySet()
            }
        }
    }
}

@Composable
private fun FilterRow(onlyUserApps: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip("Nachinstalliert", onlyUserApps) { onChange(true) }
        FilterChip("Alle", !onlyUserApps) { onChange(false) }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Self.tealDeep else Self.bg2
        ),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 12.sp,
            color = if (active) Self.text else Self.textMuted,
        )
    }
}

@Composable
private fun AppRowCard(
    row: AppRow,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val action = AppActions.actionFor(row.app)
    // Auch Systembestandteile sind ankreuzbar -- sie führen dann auf ihre
    // Systemseite zum Deaktivieren statt in den Löschdialog. Früher fehlte
    // das Kreuzchen dort ganz, womit die App zwar sagte "kann man
    // deaktivieren", aber keinen Weg dorthin anbot.
    val selectable = action != AppAction.KEINE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (selectable) onToggle(!checked) else onOpenDetails() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = if (action == AppAction.ENTFERNEN) Self.red else Self.teal
                    ),
                )
            } else {
                Spacer(Modifier.width(48.dp))
            }

            AppIcon(
                packageName = row.app.packageName,
                label = row.app.label,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    row.band?.let { band ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BandColors.of(band))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        row.app.label,
                        fontSize = 15.sp,
                        color = Self.text,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    InstallOrigin.describe(row.app),
                    fontSize = 11.sp,
                    color = Self.ice,
                )
                Text(
                    row.app.packageName,
                    fontSize = 10.sp,
                    color = Self.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Was mit dieser App möglich ist, steht direkt an der Zeile --
                // sonst kreuzt jemand an und wundert sich, dass statt des
                // Löschdialogs eine Einstellungsseite aufgeht.
                AppActions.label(action)?.let { label ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Möglich: $label",
                        fontSize = 11.sp,
                        color = if (action == AppAction.ENTFERNEN) Self.textMuted else Self.ice,
                    )
                }
            }
        }
    }
}

/**
 * Die Leiste am unteren Rand.
 *
 * Sie nennt die Auswahl getrennt nach dem, was tatsächlich passiert: Löschen
 * und Deaktivieren sind zwei verschiedene Dinge, und wer drei Apps ankreuzt,
 * soll vorher wissen, welche davon wirklich verschwinden.
 */
@Composable
private fun RemoveBar(selectedApps: List<InstalledApp>, onRemove: () -> Unit) {
    val byAction = selectedApps.groupingBy { AppActions.actionFor(it) }.eachCount()
    val toRemove = byAction[AppAction.ENTFERNEN] ?: 0
    val toRollBack = byAction[AppAction.UPDATES_ENTFERNEN] ?: 0
    val toDisable = byAction[AppAction.DEAKTIVIEREN] ?: 0

    val parts = buildList {
        if (toRemove > 0) add("$toRemove entfernen")
        if (toRollBack > 0) add("$toRollBack zurücksetzen")
        if (toDisable > 0) add("$toDisable deaktivieren")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Self.bg1)
            .padding(20.dp)
    ) {
        Text(
            buildString {
                append("Nacheinander, mit Rückfrage bei jeder App. ")
                if (toDisable > 0) {
                    append(
                        "Zum Deaktivieren öffnet sich die Systemseite der App -- " +
                            "dort auf Deaktivieren tippen."
                    )
                }
            }.trim(),
            fontSize = 11.sp,
            color = Self.textMuted,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRemove,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (toRemove > 0) Self.red else Self.teal,
                contentColor = if (toRemove > 0) Color.White else Color(0xFF04120F),
            ),
        ) {
            Text(
                parts.joinToString(", "),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
