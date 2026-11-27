package com.example.lotobot.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.lotobot.R
import com.example.lotobot.ui.MainActivity

object Notifications {

    private const val CANAL = "grille_du_jour"
    private const val ID_NOTIF = 1001

    fun creerCanal(context: Context) {
        val canal = NotificationChannel(
            CANAL,
            context.getString(R.string.canal_nom),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.canal_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    fun autorisee(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun afficherGrille(context: Context, numeros: List<Int>) {
        if (!autorisee(context)) return

        val ouvrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val texte = numeros.joinToString("  ·  ") { it.toString().padStart(2, '0') }

        val notif = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_stat_loto)
            .setContentTitle("Ta grille du jour")
            .setContentText(texte)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$texte\nSélection statistique, sans garantie : chaque tirage est indépendant."
                )
            )
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID_NOTIF, notif)
        } catch (_: SecurityException) {
            // Permission retirée entre la vérification et l'envoi : on ignore.
        }
    }
}
