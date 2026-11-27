package com.example.lotobot

import android.content.Context
import com.example.lotobot.data.HistoriqueRepository
import com.example.lotobot.data.Preferences
import com.example.lotobot.data.PronosticJour
import com.example.lotobot.engine.LotoConfig
import com.example.lotobot.engine.PronosticEngine
import java.io.File
import java.time.LocalDate

/** Point d'accès unique utilisé par l'écran principal ET par la notification. */
object LotoBot {

    /** Loto 5/90 (ex. Loto Bonheur). Pour un autre jeu : LotoConfig(49, 6, 4), etc. */
    val config = LotoConfig(maxNumero = 90, numerosParTirage = 5, taillePronostic = 4)

    fun historique(context: Context) =
        HistoriqueRepository(File(context.applicationContext.filesDir, "historique.txt"), config)

    /**
     * Renvoie la grille du jour. Elle est calculée une seule fois par jour puis mémorisée,
     * pour rester identique toute la journée (même si on ajoute des résultats entre-temps).
     * [forcer] = recalcul avec l'historique et les réglages actuels.
     *
     * Le calcul est déterministe : même jour + même historique + mêmes réglages = même grille.
     * À appeler hors du thread principal.
     */
    @Synchronized
    fun pronosticDuJour(context: Context, forcer: Boolean = false): PronosticJour {
        val prefs = Preferences(context)
        val aujourdhui = LocalDate.now()
        val stocke = prefs.pronosticStocke()
        if (!forcer && stocke != null && stocke.date == aujourdhui.toString()) return stocke

        val tirages = historique(context).charger()
        val graine = aujourdhui.toEpochDay() * 1_000_003L + tirages.size
        val p = PronosticEngine.generer(tirages, config, prefs.reglages, graine)
        val resultat = PronosticJour(aujourdhui.toString(), p.numeros, tirages.size)
        prefs.stocker(resultat)
        return resultat
    }
}
