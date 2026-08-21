package com.selfguard.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.selfguard.app.ioc.IocUpdater
import com.selfguard.app.net.BlocklistTier
import com.selfguard.app.net.BlocklistUpdater
import java.util.concurrent.TimeUnit

/**
 * Haelt Indikatoren und Sperrlisten von selbst aktuell.
 *
 * Warum das noetig ist: Ein Sicherheitswerkzeug mit veralteten Daten ist
 * schlimmer als keines, weil es Sicherheit vortaeuscht. Die Stalkerware-Liste
 * bekommt laufend neue Eintraege, die Sperrlisten taeglich.
 *
 * Bewusst nur bei ungetakteter Verbindung: die strenge Sperrliste bringt
 * zweistellige Megabyte mit, und niemand soll dafuer sein Datenvolumen
 * verlieren.
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val indicators = IocUpdater.refresh(context)

        // Sperrlisten nur erneuern, wenn schon einmal welche gebaut wurden --
        // sonst laedt die App im Hintergrund Megabyte fuer eine Funktion
        // herunter, die der Nutzer nie eingeschaltet hat.
        val blocklists = if (Prefs.blockedDomains(context) > 0) {
            val tier = runCatching { BlocklistTier.valueOf(Prefs.blocklistTier(context)) }
                .getOrDefault(BlocklistTier.STANDARD)
            BlocklistUpdater.refresh(context, tier).onSuccess {
                Prefs.setBlockedDomains(context, it.domains)
            }
        } else {
            kotlin.Result.success(Unit)
        }

        // Ein misslungener Versuch ist meist nur ein kurzer Netzaussetzer;
        // WorkManager wiederholt ihn mit wachsendem Abstand.
        return if (indicators.isSuccess && blocklists.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "selfguard_update"
        private const val INTERVAL_HOURS = 24L

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP: eine bereits laufende Planung nicht bei jedem App-Start
                // zuruecksetzen, sonst kaeme sie nie zur Ausfuehrung.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
