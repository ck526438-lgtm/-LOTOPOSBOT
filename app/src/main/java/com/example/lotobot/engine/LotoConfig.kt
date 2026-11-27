package com.example.lotobot.engine

import kotlin.math.sqrt

/**
 * Paramètres du jeu.
 *
 * Par défaut : Loto 5/90 (5 numéros tirés parmi 1..90), pronostic de 4 numéros.
 * Modifiable pour un 6/49, 5/50, etc.
 */
data class LotoConfig(
    val maxNumero: Int = 90,
    val numerosParTirage: Int = 5,
    val taillePronostic: Int = 4
) {
    init {
        require(maxNumero >= 10) { "maxNumero doit être >= 10" }
        require(numerosParTirage in 1 until maxNumero) { "numerosParTirage invalide" }
        require(taillePronostic in 1..maxNumero) { "taillePronostic invalide" }
    }

    /** Probabilité qu'un numéro donné sorte dans un tirage : K / N (1/18 pour un 5/90). */
    val pSortie: Double get() = numerosParTirage.toDouble() / maxNumero

    /**
     * Écart actuel attendu en régime stationnaire = (1 - p) / p
     * (nombre de tirages consécutifs SANS le numéro avant sa prochaine sortie ; 17 pour un 5/90).
     */
    val ecartAttendu: Double get() = (1 - pSortie) / pSortie

    /** Moyenne théorique de la somme des numéros du pronostic : k (N + 1) / 2. */
    val sommeMoyenne: Double get() = taillePronostic * (maxNumero + 1) / 2.0

    /** Écart-type théorique de la somme (tirage sans remise). */
    val sommeEcartType: Double
        get() {
            val n = maxNumero.toDouble()
            val k = taillePronostic.toDouble()
            val varianceUnitaire = (n * n - 1) / 12.0
            return sqrt(k * varianceUnitaire * (n - k) / (n - 1))
        }

    /**
     * Probabilité exacte (hypergéométrique) d'avoir exactement [bons] numéros justes
     * parmi les [taillePronostic] joués, quand [numerosParTirage] sont tirés.
     */
    fun probaBons(bons: Int): Double =
        MathUtils.combinaison(numerosParTirage, bons) *
            MathUtils.combinaison(maxNumero - numerosParTirage, taillePronostic - bons) /
            MathUtils.combinaison(maxNumero, taillePronostic)

    /** Nombre moyen de bons numéros attendu par pur hasard : k * K / N (0,222 pour 4 numéros en 5/90). */
    val bonsAttendus: Double get() = taillePronostic * pSortie

    /** Écart-type du nombre de bons numéros (loi hypergéométrique). */
    val bonsEcartType: Double
        get() {
            val n = maxNumero.toDouble()
            val k = taillePronostic.toDouble()
            val p = pSortie
            return sqrt(k * p * (1 - p) * (n - k) / (n - 1))
        }
}
