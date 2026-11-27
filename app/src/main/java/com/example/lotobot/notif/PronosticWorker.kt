package com.example.lotobot.notif

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.lotobot.LotoBot

/** Exécuté une fois par jour par WorkManager (survit aux redémarrages du téléphone). */
class PronosticWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result = try {
        val grille = LotoBot.pronosticDuJour(applicationContext)
        Notifications.afficherGrille(applicationContext, grille.numeros)
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
