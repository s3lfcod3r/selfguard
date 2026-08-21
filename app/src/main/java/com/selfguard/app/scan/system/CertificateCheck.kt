package com.selfguard.app.scan.system

import com.selfguard.app.core.Finding
import com.selfguard.app.core.FindingCode
import com.selfguard.app.core.Severity
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * S01 -- nachtraeglich eingerichtete Wurzelzertifikate.
 *
 * Der aussagekraeftigste Einzelbefund, den eine App ohne Root ueberhaupt
 * erheben kann: Wer ein eigenes Wurzelzertifikat auf dem Geraet hinterlegt,
 * kann verschluesselten Verkehr aufbrechen und mitlesen.
 *
 * Der Trick liegt in den Alias-Namen: Android fuehrt System- und
 * Nutzerzertifikate im selben Speicher `AndroidCAStore`, unterscheidet sie
 * aber am Praefix -- `system:` gegen `user:`. Damit ist die Unterscheidung
 * ohne Root moeglich, obwohl das Verzeichnis der Nutzerzertifikate selbst
 * nicht lesbar ist.
 */
object CertificateCheck {

    private const val USER_ALIAS_PREFIX = "user:"
    private const val KEYSTORE_TYPE = "AndroidCAStore"

    fun run(): List<Finding> {
        val store = runCatching {
            KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null, null) }
        }.getOrNull() ?: return emptyList()

        val aliases = runCatching { store.aliases().toList() }.getOrDefault(emptyList())

        return aliases
            .filter { it.startsWith(USER_ALIAS_PREFIX) }
            .mapNotNull { alias -> findingFor(store, alias) }
    }

    private fun findingFor(store: KeyStore, alias: String): Finding? {
        val certificate = runCatching { store.getCertificate(alias) }.getOrNull()
            as? X509Certificate ?: return null

        val issuer = certificate.issuerDN?.name.orEmpty()
        val subject = certificate.subjectDN?.name.orEmpty()

        return Finding(
            code = FindingCode.SYS_USER_CA_CERT,
            severity = Severity.HIGH,
            subject = alias,
            subjectLabel = commonNameOf(subject) ?: subject.ifEmpty { alias },
            evidence = listOfNotNull(
                subject.takeIf { it.isNotEmpty() }?.let { "Inhaber: $it" },
                issuer.takeIf { it.isNotEmpty() }?.let { "Aussteller: $it" },
                "gueltig bis ${certificate.notAfter}",
            ),
        )
    }

    /** Zieht "Meine Firma CA" aus "CN=Meine Firma CA, O=..." heraus. */
    private fun commonNameOf(distinguishedName: String): String? =
        distinguishedName
            .split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=", ignoreCase = true) }
            ?.removePrefix("CN=")
            ?.removePrefix("cn=")
            ?.takeIf { it.isNotBlank() }
}
