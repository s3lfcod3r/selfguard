package com.selfguard.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.core.Capability
import com.selfguard.app.core.RiskBand
import com.selfguard.app.core.ScanReport
import com.selfguard.app.scan.ScanState
import com.selfguard.app.net.PrivateDns
import com.selfguard.app.net.PrivateDnsStatus
import com.selfguard.app.scan.apps.AppAction
import com.selfguard.app.scan.ScanStep
import com.selfguard.app.ui.theme.BandColors
import com.selfguard.app.ui.theme.Self

/**
 * Der Hauptbildschirm.
 *
 * Gestaltungsregel: **ein Blick, eine Aussage, ein Knopf.** Wer die App
 * öffnet, soll nach zwei Sekunden wissen, ob etwas zu tun ist -- ohne einen
 * Reiter zu wählen und ohne einen Fachbegriff zu lesen. Alles Technische
 * liegt hinter dem Zahnrad.
 */
@Composable
fun HomeScreen(
    scanState: ScanState,
    lang: String,
    lastScanText: String,
    filterRunning: Boolean,
    filterBusy: Boolean,
    blockedToday: Long,
    foreignVpnActive: Boolean,
    privateDns: PrivateDns,
    onScan: () -> Unit,
    onToggleFilter: (Boolean) -> Unit,
    appActions: Map<String, AppAction>,
    knownPackages: Set<String>,
    onOpenFiles: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenMethods: () -> Unit,
    onAct: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenCapability: (String, Capability) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Self.bg0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Header(onOpenSettings)

        Spacer(Modifier.height(24.dp))
        StatusArea(scanState, lastScanText)

        Spacer(Modifier.height(20.dp))
        ScanButton(scanState, onScan)

        Spacer(Modifier.height(28.dp))
        AdBlockCard(
            running = filterRunning,
            busy = filterBusy,
            blockedToday = blockedToday,
            foreignVpnActive = foreignVpnActive,
            privateDns = privateDns,
            onToggle = onToggleFilter,
            onOpenMethods = onOpenMethods,
        )

        Spacer(Modifier.height(10.dp))
        FileScanRow(onOpenFiles)

        Spacer(Modifier.height(10.dp))
        AllAppsRow(onOpenApps)

        if (scanState is ScanState.Fertig) {
            Spacer(Modifier.height(28.dp))
            Results(
                report = scanState.report,
                lang = lang,
                appActions = appActions,
                knownPackages = knownPackages,
                onAct = onAct,
                onOpenDetails = onOpenDetails,
                onOpenCapability = onOpenCapability,
            )
            Spacer(Modifier.height(20.dp))
            Coverage()
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SelfGuardWordmark(fontSize = 24.sp)
        Icon(
            imageVector = Icons.Rounded.Settings,
            contentDescription = "Einstellungen",
            tint = Self.textMuted,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onOpenSettings)
                .padding(8.dp)
                .size(24.dp),
        )
    }
}

// -- Statusbereich ------------------------------------------------------

@Composable
private fun StatusArea(state: ScanState, lastScanText: String) {
    when (state) {
        is ScanState.Bereit -> StatusRing(
            band = null,
            headline = "Noch nicht geprüft",
            sub = lastScanText,
        )
        is ScanState.Läuft -> RunningRing(state)
        is ScanState.Fertig -> {
            val report = state.report
            StatusRing(
                band = report.worstBand,
                headline = headlineFor(report),
                sub = "${report.appsScanned} Apps geprüft, gerade eben",
            )
        }
    }
}

private fun headlineFor(report: ScanReport): String = when (report.worstBand) {
    RiskBand.SAUBER -> "Alles in Ordnung"
    RiskBand.BEOBACHTEN -> "Kleinigkeiten gefunden"
    RiskBand.BEDENKLICH -> "Bitte nachsehen"
    RiskBand.ALARM -> "Handeln Sie jetzt"
}

/**
 * Der große farbige Ring. Er trägt die gesamte Aussage -- Farbe und Symbol
 * sagen schon alles, bevor jemand ein Wort gelesen hat.
 */
@Composable
private fun StatusRing(band: RiskBand?, headline: String, sub: String) {
    val color = band?.let { BandColors.of(it) } ?: Self.textMuted

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                drawCircle(
                    color = color.copy(alpha = 0.12f),
                    radius = size.minDimension / 2,
                )
                drawCircle(
                    color = color,
                    radius = size.minDimension / 2 - 6.dp.toPx(),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            if (band != null) {
                Icon(
                    imageVector = if (band == RiskBand.SAUBER) {
                        Icons.Rounded.Check
                    } else {
                        Icons.Rounded.PriorityHigh
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Self.text)
        Spacer(Modifier.height(4.dp))
        Text(sub, fontSize = 13.sp, color = Self.textMuted)
    }
}

/** Während des Durchlaufs: Ring als Fortschritt plus Klartext, was gerade läuft. */
@Composable
private fun RunningRing(state: ScanState.Läuft) {
    val target = if (state.total > 0) state.done.toFloat() / state.total else 0f
    val progress by animateFloatAsState(targetValue = target, label = "fortschritt")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                drawCircle(
                    color = Self.bg3,
                    radius = size.minDimension / 2 - inset,
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color = Self.teal,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                "${(progress * 100).toInt()} %",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.text,
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(state.step.label, fontSize = 15.sp, color = Self.text)
        if (state.detail.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(state.detail, fontSize = 12.sp, color = Self.textMuted)
        }
    }
}

@Composable
private fun ScanButton(state: ScanState, onScan: () -> Unit) {
    val running = state is ScanState.Läuft
    Button(
        onClick = onScan,
        enabled = !running,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Self.teal,
            contentColor = Color(0xFF04120F),
        ),
    ) {
        Text(
            when {
                running -> "Prüfung läuft ..."
                state is ScanState.Fertig -> "Erneut prüfen"
                else -> "Handy prüfen"
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// -- Werbeblocker -------------------------------------------------------

/**
 * Ein Schalter, sonst nichts.
 *
 * Früher musste man erst die Sperrlisten von Hand laden und dann einschalten.
 * Das war der häufigste Weg, mit einem Filter dazustehen, der nichts blockt.
 * Jetzt besorgt der Schalter alles Nötige selbst.
 */
@Composable
private fun AdBlockCard(
    running: Boolean,
    busy: Boolean,
    blockedToday: Long,
    foreignVpnActive: Boolean,
    privateDns: PrivateDns,
    onToggle: (Boolean) -> Unit,
    onOpenMethods: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Werbung blockieren",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Self.text,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            busy -> "Sperrlisten werden geladen ..."
                            running && blockedToday > 0 -> "An. $blockedToday Anfragen geblockt."
                            running -> "An. Werbung und Tracker werden gefiltert."
                            else -> "Aus. Werbung wird angezeigt."
                        },
                        fontSize = 13.sp,
                        color = if (running) Self.teal else Self.textMuted,
                    )
                }
                Switch(checked = running, onCheckedChange = onToggle, enabled = !busy)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Blockiert Werbung in Apps und im Browser, dazu Tracker und " +
                    "bekannte Schadsoftware-Adressen. Läuft auf dem Gerät; es " +
                    "wird nichts an Dritte weitergeleitet.",
                fontSize = 12.sp,
                color = Self.textMuted,
            )

            if (foreignVpnActive && !running) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hinweis: Es läuft bereits ein anderes VPN. Android erlaubt " +
                        "nur eines gleichzeitig -- beim Einschalten wird die " +
                        "bestehende Verbindung getrennt.",
                    fontSize = 12.sp,
                    color = Self.yellow,
                )
            }

            // Wer bereits einen filternden Anbieter eingestellt hat,
            // braucht den eigenen Filter nicht -- das gehoert auf die
            // Startseite und nicht in eine Unterseite.
            val dns = privateDns
            if (dns is PrivateDns.Anbieter &&
                PrivateDnsStatus.looksLikeFiltering(dns.hostname) && !running
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sie nutzen bereits privates DNS über ${dns.hostname}. " +
                        "Das filtert vermutlich schon Werbung -- dieser Schalter " +
                        "bringt dann kaum noch etwas.",
                    fontSize = 12.sp,
                    color = Self.ice,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Warum dafür ein VPN-Symbol erscheint, und welche Wege es " +
                    "sonst gibt",
                modifier = Modifier.clickable(onClick = onOpenMethods),
                fontSize = 12.sp,
                color = Self.teal,
            )
        }
    }
}

@Composable
private fun FileScanRow(onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Self.ice)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Downloads prüfen",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Self.text,
                )
                Text(
                    "Einzelne Dateien gegen Schadsoftware abgleichen",
                    fontSize = 12.sp,
                    color = Self.textMuted,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Self.textMuted)
        }
    }
}

// -- Ergebnisse ---------------------------------------------------------

@Composable
private fun Results(
    report: ScanReport,
    lang: String,
    appActions: Map<String, AppAction>,
    knownPackages: Set<String>,
    onAct: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenCapability: (String, Capability) -> Unit,
) {
    if (report.subjects.isEmpty()) return

    Text(
        "Gefunden",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Self.textMuted,
    )
    Spacer(Modifier.height(10.dp))

    report.subjects.forEach { subject ->
        FindingCard(
            subject = subject,
            lang = lang,
            action = appActions[subject.subject] ?: AppAction.KEINE,
            isApp = subject.subject in knownPackages,
            onAct = onAct,
            onOpenDetails = onOpenDetails,
            onOpenCapability = onOpenCapability,
        )
        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Zeigt, was tatsächlich geprüft wurde.
 *
 * Ein Scanner, der nur "nichts gefunden" sagt, lässt offen, ob er überhaupt
 * gesucht hat. Diese Liste macht die Abdeckung sichtbar -- und macht zugleich
 * ehrlich, wo die Grenze liegt.
 */
@Composable
private fun Coverage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Geprüft wurde",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.textMuted,
            )
            Spacer(Modifier.height(10.dp))

            ScanStep.entries.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Self.green,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(step.label, fontSize = 12.sp, color = Self.text)
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Nicht erfasst: Spionagesoftware mit Systemrechten, wie sie " +
                    "Behörden einsetzen. Die versteckt sich vor jeder App und " +
                    "lässt sich nur mit einer Auswertung am Rechner aufspüren.",
                fontSize = 11.sp,
                color = Self.textMuted,
            )
        }
    }
}

/** Einstieg in die vollständige App-Liste zum Ansehen und Entfernen. */
@Composable
private fun AllAppsRow(onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Apps, contentDescription = null, tint = Self.ice)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Installierte Apps",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Self.text,
                )
                Text(
                    "Ansehen, mehrere ankreuzen und entfernen",
                    fontSize = 12.sp,
                    color = Self.textMuted,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Self.textMuted)
        }
    }
}
