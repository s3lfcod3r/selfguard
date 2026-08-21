package com.selfguard.app.net

/**
 * Der Nachschlagespeicher für gesperrte Domains.
 *
 * Auslegung: Auf einem Telefon liegen schnell mehrere hunderttausend Einträge
 * an, und jede DNS-Anfrage muss in Mikrosekunden beantwortet sein. Ein
 * `Set<String>` wäre dafür zu schwer -- allein die Zeichenketten kosten das
 * Vielfache.
 *
 * Stattdessen: 64-Bit-Streuwerte in einem sortierten `LongArray`, gesucht per
 * binärer Suche. Acht Byte je Domain, kein Objektaufwand, kein Sammeldruck
 * auf den Speicherbereiniger im Anfragepfad.
 *
 * Zur Kollisionsgefahr: bei 500.000 Einträgen und 64 Bit liegt die
 * Wahrscheinlichkeit einer Kollision im Bereich von 10 hoch -8. Eine
 * Kollision würde eine harmlose Domain sperren -- vertretbar gegenüber dem
 * Speichergewinn, und der Nutzer kann jede Domain freigeben.
 */
class Blocklist private constructor(
    private val hashes: LongArray,
) {

    val size: Int get() = hashes.size

    /**
     * Sperrt die Liste diese Domain?
     *
     * Geprüft wird die Domain selbst und jede übergeordnete Ebene: steht
     * `doubleclick.net` auf der Liste, ist auch `ad.doubleclick.net` gesperrt.
     * Genau so sind die Listen gemeint -- ohne diese Regel müsste jede
     * Unterdomain einzeln geführt werden.
     */
    fun blocks(domain: String): Boolean {
        if (hashes.isEmpty()) return false
        var candidate = domain.lowercase().trimEnd('.')

        while (true) {
            if (contains(candidate)) return true
            val dot = candidate.indexOf('.')
            // Eine einzelne Bezeichnung ("net") wird nicht mehr geprüft --
            // das wäre eine ganze Endung und nie gewollt.
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
            if (!candidate.contains('.')) return false
        }
    }

    private fun contains(domain: String): Boolean =
        hashes.binarySearch(hashOf(domain)) >= 0

    companion object {
        val EMPTY = Blocklist(LongArray(0))

        /** Streuwert nach FNV-1a, 64 Bit. Schnell und ohne Zuteilungen. */
        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        fun hashOf(domain: String): Long {
            var hash = FNV_OFFSET_BASIS
            for (index in domain.indices) {
                hash = hash xor (domain[index].code.toLong() and 0xFF)
                hash *= FNV_PRIME
            }
            return hash
        }

        /** Baut die Liste aus Domains. Doppelte fallen dabei weg. */
        fun of(domains: Collection<Long>): Blocklist {
            val array = domains.toLongArray()
            array.sort()
            return Blocklist(dedupe(array))
        }

        fun fromSortedArray(sorted: LongArray): Blocklist = Blocklist(sorted)

        /** Entfernt Doppelte aus einem bereits sortierten Feld. */
        private fun dedupe(sorted: LongArray): LongArray {
            if (sorted.isEmpty()) return sorted
            var write = 1
            for (read in 1 until sorted.size) {
                if (sorted[read] != sorted[write - 1]) {
                    sorted[write] = sorted[read]
                    write++
                }
            }
            return if (write == sorted.size) sorted else sorted.copyOf(write)
        }
    }
}
