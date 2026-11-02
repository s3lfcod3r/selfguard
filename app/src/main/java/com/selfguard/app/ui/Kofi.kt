package com.selfguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfguard.app.ui.theme.Self

/** Ko-fi-Seite zum Unterstützen. Öffnet sich im Browser, die App selbst lädt nichts. */
const val KOFI_URL = "https://ko-fi.com/s3lfcod3r"

/** Kleine Kaffeetasse für die Kopfzeile. */
@Composable
fun KofiIcon() {
    val uri = LocalUriHandler.current
    Icon(
        imageVector = Icons.Rounded.Coffee,
        contentDescription = "Auf Ko-fi unterstützen",
        tint = Self.textMuted,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { runCatching { uri.openUri(KOFI_URL) } }
            .padding(8.dp)
            .size(24.dp),
    )
}

/** Abschnitt für die Einstellungen. */
@Composable
fun KofiSection() {
    val uri = LocalUriHandler.current
    Text(
        "SelfGuard ist kostenlos, werbefrei und ohne Tracking. Wenn dir die App hilft, freue ich mich über einen Kaffee.",
        fontSize = 12.sp,
        color = Self.textMuted,
    )
    OutlinedButton(
        onClick = { runCatching { uri.openUri(KOFI_URL) } },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Auf Ko-fi unterstützen")
    }
}
