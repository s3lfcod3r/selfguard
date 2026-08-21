package com.selfguard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.core.Capability
import com.selfguard.app.core.CapabilityText
import com.selfguard.app.core.CapabilityTexts
import com.selfguard.app.core.FindingTexts
import com.selfguard.app.core.RiskBand
import com.selfguard.app.core.Severity
import com.selfguard.app.core.SubjectReport
import com.selfguard.app.scan.apps.AppAction
import com.selfguard.app.scan.apps.AppActions
import com.selfguard.app.ui.theme.BandColors
import com.selfguard.app.ui.theme.Self

/**
 * Ein Befund in Klartext.
 *
 * Zugeklappt steht nur, **was** los ist und **wo**. Erst beim Antippen kommen
 * Erklärung, Belege und die Handlungsempfehlung. Die technischen Kennungen
 * (D01, S03) tauchen bewusst nur klein und ganz unten auf -- sie helfen beim
 * Nachschlagen, sollen aber niemanden abschrecken.
 */
@Composable
fun FindingCard(
    subject: SubjectReport,
    lang: String,
    action: AppAction = AppAction.KEINE,
    isApp: Boolean = false,
    onAct: (String) -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onOpenCapability: (String, Capability) -> Unit = { _, _ -> },
) {
    var expanded by remember { mutableStateOf(false) }
    val color = BandColors.of(subject.band)
    val leading = subject.findings.maxByOrNull { it.severity.weight } ?: return
    val leadingText = FindingTexts.of(leading.code, lang)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg2),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(12.dp))
                if (isApp) {
                    AppIcon(
                        packageName = subject.subject,
                        label = subject.label,
                        size = 34.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        leadingText.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Self.text,
                    )
                    Text(
                        subject.label,
                        fontSize = 12.sp,
                        color = Self.textMuted,
                    )
                }
                if (subject.findings.size > 1) {
                    Text(
                        "+${subject.findings.size - 1}",
                        fontSize = 12.sp,
                        color = Self.textMuted,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Self.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    subject.findings
                        .sortedByDescending { it.severity.weight }
                        .forEach { finding ->
                            val text = FindingTexts.of(finding.code, lang)
                            Detail(
                                title = text.title,
                                detail = text.detail,
                                advice = text.advice,
                                evidence = finding.evidence,
                                capabilities = finding.capabilities,
                                packageName = subject.subject,
                                lang = lang,
                                code = finding.code.id,
                                critical = finding.severity == Severity.CRITICAL,
                                onOpenCapability = onOpenCapability,
                            )
                            Spacer(Modifier.height(14.dp))
                        }

                    if (isApp) {
                        Actions(
                            packageName = subject.subject,
                            action = action,
                            onAct = onAct,
                            onOpenDetails = onOpenDetails,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Was der Nutzer mit dem Befund tun kann.
 *
 * Bei System-Apps fehlt der Entfernen-Knopf bewusst -- dort führt der Weg in
 * die Systemeinstellungen. Entfernen löst nur den Android-Standarddialog
 * aus; SelfGuard löscht selbst nichts.
 */
@Composable
private fun Actions(
    packageName: String,
    action: AppAction,
    onAct: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val label = AppActions.label(action)

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (label != null) {
                Button(
                    onClick = { onAct(packageName) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (action == AppAction.ENTFERNEN) Self.red else Self.teal,
                        contentColor =
                            if (action == AppAction.ENTFERNEN) Color.White
                            else Color(0xFF04120F),
                    ),
                ) {
                    Text(label, fontSize = 13.sp)
                }
            }
            OutlinedButton(
                onClick = { onOpenDetails(packageName) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Details", fontSize = 13.sp)
            }
        }

        // Was der Knopf bewirkt, steht darunter -- besonders beim
        // Deaktivieren, wo sich eine Systemseite öffnet statt eines Dialogs.
        AppActions.explain(action)?.let { explanation ->
            Spacer(Modifier.height(6.dp))
            Text(
                explanation,
                fontSize = 11.sp,
                color = Self.textMuted,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun Detail(
    title: String,
    detail: String,
    advice: String,
    evidence: List<String>,
    capabilities: List<Capability>,
    packageName: String,
    lang: String,
    code: String,
    critical: Boolean,
    onOpenCapability: (String, Capability) -> Unit,
) {
    Column {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (critical) BandColors.of(RiskBand.ALARM) else Self.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(detail, fontSize = 13.sp, color = Self.text, lineHeight = 19.sp)

        if (evidence.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            evidence.forEach {
                Text("- $it", fontSize = 11.sp, color = Self.textMuted)
            }
        }

        if (capabilities.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Antippen zeigt, was ohne die jeweilige Berechtigung nicht mehr geht.",
                fontSize = 11.sp,
                color = Self.textMuted,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(6.dp))
            capabilities.forEach { capability ->
                CapabilityRow(
                    text = CapabilityTexts.of(capability, lang),
                    revocable = capability.revocable,
                    onOpen = { onOpenCapability(packageName, capability) },
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Self.bg3),
        ) {
            Text(
                advice,
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                color = Self.iceBright,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("Prüfung $code", fontSize = 10.sp, color = Self.textMuted)
    }
}

/**
 * Eine einzelne Fähigkeit einer App -- zugeklappt nur der Name, aufgeklappt
 * die Folgen und der Weg dorthin.
 *
 * Zugeklappt, weil eine App mit zehn Fähigkeiten sonst eine Textwand ergibt,
 * durch die niemand liest. Die Namen allein reichen zum Überfliegen; wer bei
 * einer Zeile stutzt, tippt sie an und erfährt, was ihr Entzug kostet.
 *
 * Der Knopf entzieht nichts -- er öffnet nur die Seite, auf der der Schalter
 * sitzt. Warum das so sein muss, steht in [PermissionActions]. Die
 * Beschriftung sagt es dazu, damit niemand nach dem Tippen überrascht in den
 * Einstellungen steht.
 */
@Composable
private fun CapabilityRow(
    text: CapabilityText,
    revocable: Boolean,
    onOpen: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Self.bg3),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text.label,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = Self.text,
                )
                Icon(
                    if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = Self.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }

            AnimatedVisibility(visible = open) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text.consequence,
                        fontSize = 11.sp,
                        color = Self.textMuted,
                        lineHeight = 16.sp,
                    )
                    TextButton(
                        onClick = onOpen,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (revocable) {
                                text.action + " -- öffnet die Systemeinstellung"
                            } else {
                                text.action + " -- öffnet die App-Info"
                            },
                            fontSize = 12.sp,
                            color = Self.teal,
                        )
                    }
                }
            }
        }
    }
}
