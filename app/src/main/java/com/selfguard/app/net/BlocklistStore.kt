package com.selfguard.app.net

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Auswahl der Sperrlisten.
 *
 * HaGeZi gilt derzeit als der am besten gepflegte Satz freier DNS-Sperrlisten.
 * Die Listen werden **zur Laufzeit geladen und nicht mitgeliefert** -- sie
 * stehen unter GPL-3.0, und ein Mitliefern im APK würde die Lizenzfrage
 * aufwerfen, die beim bloßen Abrufen gar nicht erst entsteht.
 *
 * Die Stufen bilden den bekannten Zielkonflikt ab: mehr Einträge bedeuten
 * mehr geblockte Werbung, aber auch mehr kaputte Apps.
 */
enum class BlocklistTier(
    val label: String,
    val description: String,
    val urls: List<String>,
) {
    LEICHT(
        label = "Leicht",
        description = "Werbung und Tracker. Bricht praktisch nie etwas.",
        urls = listOf(HaGeZi.LIGHT),
    ),
    STANDARD(
        label = "Standard",
        description = "Werbung, Tracker und bekannte Schadsoftware-Adressen.",
        urls = listOf(HaGeZi.LIGHT, HaGeZi.THREATS_MINI),
    ),
    STRENG(
        label = "Streng",
        description = "Deutlich mehr Einträge. Einzelne Apps können zicken.",
        urls = listOf(HaGeZi.MULTI, HaGeZi.THREATS_MEDIUM),
    );

    private object HaGeZi {
        private const val BASE =
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard"
        const val LIGHT = "$BASE/light-onlydomains.txt"
        const val MULTI = "$BASE/multi-onlydomains.txt"
        const val THREATS_MINI = "$BASE/tif.mini-onlydomains.txt"
        const val THREATS_MEDIUM = "$BASE/tif.medium-onlydomains.txt"
    }
}

/**
 * Legt die aufbereitete Sperrliste als Binärdatei ab.
 *
 * Warum nicht die heruntergeladenen Textdateien behalten: mehrere Megabyte
 * Text bei jedem Dienststart zu zerlegen kostet Sekunden. Die fertig
 * sortierten Streuwerte sind in Millisekunden gelesen.
 */
object BlocklistStore {

    private const val DIR = "blocklist"
    private const val FILE = "domains.bin"

    /** Erkennungsmarke, damit eine fremde oder alte Datei auffliegt. */
    private const val MAGIC = 0x53474C31 // "SGL1"

    fun file(context: Context): File =
        File(File(context.filesDir, DIR).apply { mkdirs() }, FILE)

    fun exists(context: Context): Boolean = file(context).let { it.exists() && it.length() > 0 }

    fun load(context: Context): Blocklist {
        val file = file(context)
        if (!file.exists()) return Blocklist.EMPTY

        return runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC) return Blocklist.EMPTY
                val count = input.readInt()
                if (count <= 0) return Blocklist.EMPTY
                val hashes = LongArray(count)
                for (index in 0 until count) hashes[index] = input.readLong()
                Blocklist.fromSortedArray(hashes)
            }
        }.getOrDefault(Blocklist.EMPTY)
    }

    /**
     * Schreibt erst vollständig in eine Nebendatei und benennt dann um --
     * ein Abbruch darf keine halbe Sperrliste hinterlassen, mit der still
     * weniger geblockt wird als angezeigt.
     */
    fun save(context: Context, sorted: LongArray) {
        val target = file(context)
        val temp = File(target.parentFile, "$FILE.tmp")

        DataOutputStream(temp.outputStream().buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(sorted.size)
            for (value in sorted) output.writeLong(value)
        }

        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
