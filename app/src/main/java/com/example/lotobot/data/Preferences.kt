package com.example.lotobot.data

import android.content.Context
import com.example.lotobot.engine.ReglagesModele

/** La grille retenue pour une journée donnée. */
data class PronosticJour(
    /** Date ISO (aaaa-mm-jj). */
    val date: String,
    val numeros: List<Int>,
    /** Taille de l'historique au moment du calcul. */
    val nbTirages: Int
)

class Preferences(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("lotoposbot", Context.MODE_PRIVATE)

    var reglages: ReglagesModele
        get() = ReglagesModele(
            poidsChaud = sp.getFloat("chaud", 0.5f).toDouble(),
            poidsRetard = sp.getFloat("retard", 0.5f).toDouble(),
            equilibre = sp.getBoolean("equilibre", true)
        )
        set(v) {
            sp.edit()
                .putFloat("chaud", v.poidsChaud.toFloat())
                .putFloat("retard", v.poidsRetard.toFloat())
                .putBoolean("equilibre", v.equilibre)
                .apply()
        }

    var notificationActive: Boolean
        get() = sp.getBoolean("notif", false)
        set(v) = sp.edit().putBoolean("notif", v).apply()

    var heureNotif: Int
        get() = sp.getInt("heure", 9)
        set(v) = sp.edit().putInt("heure", v).apply()

    var minuteNotif: Int
        get() = sp.getInt("minute", 0)
        set(v) = sp.edit().putInt("minute", v).apply()

    fun pronosticStocke(): PronosticJour? {
        val date = sp.getString("pj_date", null) ?: return null
        val numeros = sp.getString("pj_numeros", "")!!
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        if (numeros.isEmpty()) return null
        return PronosticJour(date, numeros, sp.getInt("pj_nb", 0))
    }

    fun stocker(p: PronosticJour) {
        sp.edit()
            .putString("pj_date", p.date)
            .putString("pj_numeros", p.numeros.joinToString(","))
            .putInt("pj_nb", p.nbTirages)
            .apply()
    }
}
