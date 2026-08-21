package com.selfguard.app.ui

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.selfguard.app.ui.theme.Self
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Das Symbol einer installierten App.
 *
 * Ohne Symbol ist eine App-Liste eine Liste von Zeichenketten. Der Nutzer
 * erkennt sein Telefon darin nicht wieder und kann deshalb auch nicht
 * beurteilen, was da nicht hingehört -- genau das aber ist die Aufgabe. Mit
 * Symbol wird aus "com.samsung.android.heartplugin" die Uhren-App, die er
 * kennt.
 *
 * Bewusst ohne Bildbibliothek: Coil oder Glide wären für diesen einen Zweck
 * ein halbes Megabyte Programmcode und eine weitere Abhängigkeit, die
 * gepflegt werden will. Der PackageManager liefert das Symbol direkt.
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) { AppIconCache.load(context, packageName) }
    }

    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Self.bg3),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        } else {
            // Bis das Symbol da ist, der Anfangsbuchstabe. Ein leeres Kästchen
            // lässt die Liste beim Scrollen flackern; ein Buchstabe steht
            // ruhig und trägt sogar etwas bei.
            Text(
                text = label.take(1).uppercase(),
                fontSize = (size.value / 2.4f).sp,
                fontWeight = FontWeight.SemiBold,
                color = Self.textMuted,
            )
        }
    }
}

/**
 * Gelesene Symbole, damit das Scrollen flüssig bleibt.
 *
 * Der PackageManager liest bei jedem Aufruf die APK an -- auf einem Gerät
 * mit dreihundert Apps wäre das bei jedem Durchscrollen erneut zu tun.
 * Deshalb ein Zwischenspeicher, begrenzt auf [MAX_BYTES]: klein genug, um
 * nicht ins Gewicht zu fallen, groß genug für jede Liste, die jemand am
 * Stück durchsieht.
 */
private object AppIconCache {

    /** Kantenlänge in Bildpunkten. Angezeigt werden höchstens 44dp. */
    private const val PIXELS = 128

    /** Vier Megabyte -- rund sechzig Symbole in voller Auflösung. */
    private const val MAX_BYTES = 4 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    /**
     * Darf nur außerhalb des Hauptthreads laufen -- das Lesen der APK
     * dauert je nach Gerät einige Millisekunden.
     *
     * Fällt das Lesen aus (App gerade entfernt, beschädigtes Paket), gibt
     * es null und die Oberfläche zeigt den Anfangsbuchstaben. Ein Absturz
     * wäre hier die falsche Antwort auf ein fehlendes Bildchen.
     */
    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }

        val bitmap = runCatching {
            context.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = PIXELS, height = PIXELS)
                .asImageBitmap()
        }.getOrNull() ?: return null

        cache.put(packageName, bitmap)
        return bitmap
    }
}
