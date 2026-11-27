package com.example.lotobot.engine

import kotlin.math.sqrt
import kotlin.random.Random

data class ResultatBacktest(
    val nbTests: Int,
    /** Nombre moyen de bons numéros du modèle. */
    val moyenneModele: Double,
    /** Nombre moyen de bons numéros de grilles 100 % aléatoires, sur les mêmes tirages. */
    val moyenneAleatoire: Double,
    /** Valeur théorique attendue par pur hasard (0,222 pour 4 numéros en 5/90). */
    val moyenneTheorique: Double,
    /** Score z du modèle par rapport au hasard théorique. |z| < 2 = aucune différence significative. */
    val zModele: Double,
    /** Répartition du modèle : index = nombre de bons numéros. */
    val repartitionModele: IntArray,
    val repartitionTheorique: DoubleArray
) {
    val significatif: Boolean get() = zModele >= 2.0
}

/**
 * Backtest "walk-forward" : pour chaque tirage t, le modèle ne voit QUE les tirages 0..t-1,
 * génère sa grille, puis on compte les bons numéros dans le tirage t.
 * Pas de fuite de données vers le futur.
 */
object Backtest {

    fun executer(
        historique: List<Tirage>,
        config: LotoConfig,
        reglages: ReglagesModele,
        historiqueMinimum: Int = 30,
        maxTests: Int = 300
    ): ResultatBacktest? {
        val debut = maxOf(historiqueMinimum, historique.size - maxTests)
        if (debut >= historique.size) return null

        val k = config.taillePronostic
        val repartModele = IntArray(k + 1)
        var bonsModele = 0L
        var bonsAleatoire = 0L
        var nb = 0

        for (t in debut until historique.size) {
            val passe = historique.subList(0, t)
            val reel = historique[t].numeros.toSet()

            val grille = PronosticEngine.generer(passe, config, reglages, graine = t.toLong()).numeros
            val bons = grille.count { it in reel }
            bonsModele += bons
            repartModele[bons]++

            val aleatoire = (1..config.maxNumero).shuffled(Random(1_000_003L * t + 7)).take(k)
            bonsAleatoire += aleatoire.count { it in reel }
            nb++
        }

        val moyModele = bonsModele.toDouble() / nb
        val z = (moyModele - config.bonsAttendus) / (config.bonsEcartType / sqrt(nb.toDouble()))
        return ResultatBacktest(
            nbTests = nb,
            moyenneModele = moyModele,
            moyenneAleatoire = bonsAleatoire.toDouble() / nb,
            moyenneTheorique = config.bonsAttendus,
            zModele = z,
            repartitionModele = repartModele,
            repartitionTheorique = DoubleArray(k + 1) { config.probaBons(it) }
        )
    }
}
