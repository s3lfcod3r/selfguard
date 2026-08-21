package com.selfguard.app.ui

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.net.PrivateDns
import com.selfguard.app.net.PrivateDnsStatus
import com.selfguard.app.ui.theme.Self

/**
 * Die drei Wege, Werbung zu blocken -- mit ihren echten Nachteilen.
 *
 * Warum dieser Bildschirm überhaupt existiert: Der Filter von SelfGuard legt
 * ein VPN-Symbol in die Statusleiste und belegt den einzigen VPN-Platz, den
 * Android hergibt. Das verunsichert zu Recht. Statt es wegzuerklären zeigt
 * die App alle Möglichkeiten samt Preis -- und sagt auch dann ehrlich Bescheid,
 * wenn der bessere Weg ohne sie auskommt.
 */
@Composable
fun BlockingMethodsScreen(
    filterRunning: Boolean,
    privateDns: PrivateDns,
    onBack: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
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
                "Wege zum Blocken",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.text,
            )
        }

        Spacer(Modifier.height(16.dp))
        NoVpnNote()

        Spacer(Modifier.height(20.dp))
        SelfGuardMethod(filterRunning)

        Spacer(Modifier.height(12.dp))
        PrivateDnsMethod(privateDns, onOpenNetworkSettings)

        Spacer(Modifier.height(12.dp))
        HomeNetworkMethod()

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Der wichtigste Satz überhaupt -- er steht deshalb ganz oben und nicht im
 * Kleingedruckten.
 */
@Composable
private fun NoVpnNote() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg1),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Der Filter ist keine VPN-Verbindung",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.teal,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Es geht nichts an einen Server. Kein Anbieter, kein Tunnel ins " +
                    "Internet, Ihr Verkehr wird nirgendwohin umgeleitet. Android " +
                    "nennt die Schnittstelle nur VpnService -- daher das " +
                    "Schlüsselsymbol in der Statusleiste.\n\n" +
                    "Was wirklich passiert: SelfGuard richtet eine Schnittstelle " +
                    "auf diesem Gerät ein und trägt sich als Namensserver ein. Es " +
                    "sieht dadurch Anfragen wie \"welche Adresse hat " +
                    "werbenetz.example\" und beantwortet sie selbst. Nur die -- " +
                    "aller übrige Verkehr läuft daran vorbei.\n\n" +
                    "Warum es nicht anders geht: Android kapselt Apps streng ab. " +
                    "Keine App darf den Verkehr einer anderen sehen, sonst könnte " +
                    "jede beliebige App Ihre Bankverbindung mitlesen. VpnService " +
                    "ist der einzige Weg, den das System dafür freigibt.",
                fontSize = 12.sp,
                color = Self.text,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun SelfGuardMethod(running: Boolean) {
    Method(
        title = "1. Filter von SelfGuard",
        state = if (running) "läuft gerade" else "aus",
        stateColor = if (running) Self.green else Self.textMuted,
        good = listOf(
            "Funktioniert überall, auch im Mobilfunknetz",
            "Niemand ausser Ihrem Gerät sieht die Anfragen",
            "Zeigt, was geblockt wurde",
            "Einzelne Apps lassen sich ausnehmen",
        ),
        bad = listOf(
            "Belegt den einzigen VPN-Platz von Android -- ein WireGuard-Tunnel kann nicht gleichzeitig laufen",
            "Schlüsselsymbol in der Statusleiste",
            "Braucht ein wenig Akku, weil ein Dienst mitläuft",
        ),
    )
}

@Composable
private fun PrivateDnsMethod(privateDns: PrivateDns, onOpenSettings: () -> Unit) {
    val (state, color) = when (privateDns) {
        is PrivateDns.Aus -> "aus" to Self.textMuted
        is PrivateDns.Automatisch -> "auf Automatik" to Self.yellow
        is PrivateDns.Anbieter ->
            if (PrivateDnsStatus.looksLikeFiltering(privateDns.hostname)) {
                "aktiv, filtert vermutlich" to Self.green
            } else {
                "aktiv" to Self.ice
            }
    }

    Column {
        Method(
            title = "2. Privates DNS von Android",
            state = state,
            stateColor = color,
            good = listOf(
                "Kein VPN, kein Symbol, kein Hintergrunddienst",
                "Kostet praktisch keinen Akku",
                "SelfWG und andere Tunnel bleiben nutzbar",
            ),
            bad = listOf(
                "Der gewählte Anbieter sieht jede einzelne Namensanfrage",
                "Keine Ausnahmen für einzelne Apps",
                "Keine Zähler -- Sie sehen nicht, was geblockt wurde",
                "Gilt nur ab Android 9",
            ),
        )

        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Self.bg1),
        ) {
            Column(Modifier.padding(14.dp)) {
                // Der aktuelle Zustand in Klartext -- damit niemand raten muss,
                // ob die Einstellung ueberhaupt etwas tut.
                Text(currentStateText(privateDns), fontSize = 12.sp, color = Self.text, lineHeight = 17.sp)

                Spacer(Modifier.height(10.dp))
                Text(
                    "So stellen Sie es ein: Einstellungen, Verbindungen, weitere " +
                        "Verbindungseinstellungen, Privates DNS. Dort \"Hostname des " +
                        "privaten DNS-Anbieters\" wählen und eintragen, zum Beispiel " +
                        "dns.adguard-dns.com.",
                    fontSize = 12.sp,
                    color = Self.textMuted,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "SelfGuard kann das nicht für Sie setzen -- Android bietet " +
                        "dafür keine Schnittstelle an. Der Knopf führt nur bis zu " +
                        "den Netzwerkeinstellungen.",
                    fontSize = 11.sp,
                    color = Self.textMuted,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Netzwerkeinstellungen öffnen", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun currentStateText(privateDns: PrivateDns): String = when (privateDns) {
    is PrivateDns.Aus ->
        "Bei Ihnen steht es auf Aus. Namensanfragen laufen unverschlüsselt zu " +
            "dem Server, den Ihr Netz vorgibt -- und es wird nichts gefiltert."
    is PrivateDns.Automatisch ->
        "Bei Ihnen steht es auf Automatik. Das verschlüsselt nur, wenn der " +
            "Server des Netzes es anbietet. Geblockt wird dabei nichts -- " +
            "Automatik ist kein Filter."
    is PrivateDns.Anbieter ->
        if (PrivateDnsStatus.looksLikeFiltering(privateDns.hostname)) {
            "Bei Ihnen läuft bereits \"${privateDns.hostname}\". Dieser Anbieter " +
                "filtert nach allem, was wir wissen, bereits Werbung. Der Filter " +
                "von SelfGuard bringt dann kaum noch etwas -- Sie können ihn " +
                "ausgeschaltet lassen."
        } else {
            "Bei Ihnen läuft \"${privateDns.hostname}\". Ob dieser Anbieter " +
                "Werbung filtert, kann SelfGuard nicht messen. Filtert er nicht, " +
                "ergänzen sich beide Wege nicht -- dann lohnt der eigene Filter."
        }
}

@Composable
private fun HomeNetworkMethod() {
    Column {
        Method(
            title = "3. Eigener Filter im Heimnetz",
            state = "empfohlen, wenn Sie einen Server haben",
            stateColor = Self.ice,
            good = listOf(
                "Blockt für jedes Gerät im Haushalt -- Fernseher, Konsole, Gäste",
                "Der Filter läuft bei Ihnen, niemand sonst sieht die Anfragen",
                "Kein VPN, keine App, keine Akkulast auf dem Handy",
                "Eigene Regeln und Statistiken",
            ),
            bad = listOf(
                "Wirkt nur zu Hause -- unterwegs bleibt es ungefiltert",
                "Braucht einen Rechner, der durchläuft",
                "Einmalige Einrichtung im Router",
            ),
        )

        Spacer(Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Self.bg1),
        ) {
            Text(
                "AdGuard Home oder Pi-hole auf einem kleinen Server oder NAS " +
                    "installieren und im Router als DNS eintragen. Unterwegs " +
                    "greift das nicht mehr -- ausser Sie haben ohnehin einen " +
                    "WireGuard-Tunnel nach Hause und tragen dort im Profil " +
                    "Ihren eigenen Filter als DNS ein. Dann blockt es auch " +
                    "unterwegs, ohne dass ein zweites VPN nötig wäre.",
                modifier = Modifier.padding(14.dp),
                fontSize = 12.sp,
                color = Self.textMuted,
                lineHeight = 17.sp,
            )
        }
    }
}

/** Ein Weg mit Vor- und Nachteilen -- immer beides, nie nur die Vorteile. */
@Composable
private fun Method(
    title: String,
    state: String,
    stateColor: Color,
    good: List<String>,
    bad: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Self.text)
                Text(state, fontSize = 11.sp, color = stateColor)
            }

            Spacer(Modifier.height(12.dp))
            good.forEach { Bullet(it, Self.green) }
            Spacer(Modifier.height(8.dp))
            bad.forEach { Bullet(it, Self.yellow) }
        }
    }
}

@Composable
private fun Bullet(text: String, color: Color) {
    Row(Modifier.padding(bottom = 5.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 12.sp, color = Self.text, lineHeight = 17.sp)
    }
}
