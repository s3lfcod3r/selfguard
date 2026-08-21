package com.selfguard.app.scan.apps

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.selfguard.app.core.Hashing
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Liest den PackageManager aus und übersetzt ihn in [InstalledApp].
 *
 * Die einzige Stelle im App-Audit mit Android-Bezug. Alles Urteilende steckt
 * in den Detektoren, die nur noch auf dem Ergebnis arbeiten.
 */
object AppInventory {

    /**
     * Metadaten werden **paketweise** geholt, nicht gebündelt.
     *
     * `getInstalledPackages` mit GET_SERVICES und GET_RECEIVERS läuft auf
     * Geräten mit vielen Apps in eine TransactionTooLargeException, weil das
     * Ergebnis nicht mehr durch die Binder-Transaktion passt. Einzelabfragen
     * sind langsamer, brechen aber nicht ab.
     */
    private const val DETAIL_FLAGS = PackageManager.GET_PERMISSIONS or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS

    /**
     * @param onProgress bekommt (fertig, gesamt) gemeldet
     * @param isActive wird vor jedem Paket geprüft -- gibt es false zurück,
     *   endet der Scan vorzeitig mit dem bisher Gesammelten
     */
    fun readAll(
        context: Context,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        isActive: () -> Boolean = { true },
    ): List<InstalledApp> {
        val pm = context.packageManager
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        val result = ArrayList<InstalledApp>(installed.size)

        installed.forEachIndexed { index, appInfo ->
            if (!isActive()) return result
            onProgress(index, installed.size)
            read(pm, appInfo)?.let { result += it }
        }
        onProgress(installed.size, installed.size)
        return result
    }

    private fun read(pm: PackageManager, appInfo: ApplicationInfo): InstalledApp? {
        val pkg = appInfo.packageName
        val info = runCatching { packageInfo(pm, pkg) }.getOrNull() ?: return null
        val certificates = signingCertificates(info)

        return InstalledApp(
            packageName = pkg,
            label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg),
            versionName = info.versionName ?: "",
            installerPackage = installerOf(pm, pkg),
            isSystemApp = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystemApp = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            isDebuggable = appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            targetSdk = appInfo.targetSdkVersion,
            requestedPermissions = info.requestedPermissions?.toList() ?: emptyList(),
            signingSha256 = certificates.map { Hashing.sha256(it.encoded) },
            signingSha1 = certificates.map { Hashing.sha1(it.encoded) },
            signerSubjects = certificates.map { it.subjectDN.name },
            hasLauncher = pm.getLaunchIntentForPackage(pkg) != null,
            declaresAccessibilityService = info.services.orEmpty()
                .any { it.permission == Manifest.permission.BIND_ACCESSIBILITY_SERVICE },
            declaresDeviceAdmin = info.receivers.orEmpty()
                .any { it.permission == Manifest.permission.BIND_DEVICE_ADMIN },
            declaresNotificationListener = info.services.orEmpty()
                .any { it.permission == Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE },
            serviceCount = info.services?.size ?: 0,
            receiverCount = info.receivers?.size ?: 0,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            apkPath = appInfo.sourceDir ?: "",
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(pm: PackageManager, pkg: String): PackageInfo {
        val signingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                pkg,
                PackageManager.PackageInfoFlags.of((DETAIL_FLAGS or signingFlag).toLong())
            )
        } else {
            pm.getPackageInfo(pkg, DETAIL_FLAGS or signingFlag)
        }
    }

    /** Wer hat die App installiert? Null heißt: nicht nachvollziehbar. */
    @Suppress("DEPRECATION")
    private fun installerOf(pm: PackageManager, pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkg).installingPackageName
        } else {
            pm.getInstallerPackageName(pkg)
        }
    }.getOrNull()

    /**
     * Bei mehreren Signaturen (Schlüsselwechsel) werden alle berücksichtigt --
     * sonst entginge uns ein Treffer, sobald ein Anbieter den Schlüssel dreht.
     */
    @Suppress("DEPRECATION")
    private fun signingCertificates(info: PackageInfo): List<X509Certificate> {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> emptyArray()
                signing.hasMultipleSigners() -> signing.apkContentsSigners
                else -> signing.signingCertificateHistory
            }
        } else {
            info.signatures
        }

        val factory = runCatching { CertificateFactory.getInstance("X.509") }.getOrNull()
            ?: return emptyList()

        return raw.orEmpty().mapNotNull { signature ->
            runCatching {
                factory.generateCertificate(signature.toByteArray().inputStream()) as X509Certificate
            }.getOrNull()
        }
    }
}
