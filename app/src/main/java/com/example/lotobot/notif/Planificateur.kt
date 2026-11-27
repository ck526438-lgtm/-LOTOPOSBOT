package com.example.lotobot.notif

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Planifie la notification quotidienne à l'heure choisie.
 * WorkManager ne garantit pas la minute exacte (économie de batterie) : compte quelques
 * minutes de décalage possible, ce qui suffit largement pour une grille du jour.
 */
object Planificateur {

    private const val NOM = "grille_quotidienne"

    fun planifier(context: Context, heure: Int, minute: Int) {
        val maintenant = LocalDateTime.now()
        var prochaine = maintenant.with(LocalTime.of(heure, minute))
        if (!prochaine.isAfter(maintenant)) prochaine = prochaine.plusDays(1)
        val delai = Duration.between(maintenant, prochaine).toMillis()

        val requete = PeriodicWorkRequestBuilder<PronosticWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delai, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOM,
            // On remplace l'ancienne planification pour appliquer la nouvelle heure
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            requete
        )
    }

    fun annuler(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(NOM)
    }
}
