package com.example.lotobot.engine

import kotlin.math.sqrt

/** Statistiques d'un numéro sur l'historique. */
data class StatNumero(
    val numero: Int,
    /** Nombre de sorties. */
    val frequence: Int,
    /** Écart standardisé de la fréquence : (f - n·p) / √(n·p·(1-p)). |z| < 2 dans ~95 % des cas par pur hasard. */
    val zFrequence: Double,
    /** Tirages écoulés depuis la dernière sortie (0 = sorti au dernier tirage). */
    val ecartActuel: Int,
    /** Moyenne des écarts entre deux sorties consécutives (null si moins de 2 sorties). */
    val ecartMoyen: Double?,
    /** Plus long écart observé (y compris l'écart en cours). */
    val ecartMax: Int,
    /** true si le numéro n'est jamais sorti dans l'historique. */
    val jamaisSorti: Boolean
)

/** Test du khi-deux d'uniformité des fréquences. */
data class TestUniformite(
    val statistique: Double,
    val ddl: Int,
    val pValeur: Double
) {
    /** On ne rejette l'hypothèse "tirage équitable" qu'avec p < 0,01 (et même alors, prudence). */
    val compatibleAvecHasard: Boolean get() = pValeur >= 0.01
}

data class Statistiques(
    val config: LotoConfig,
    val nbTirages: Int,
    val parNumero: List<StatNumero>,
    val uniformite: TestUniformite?,
    /** Répartition observée du nombre de numéros pairs par tirage : index = nb de pairs. */
    val repartitionPairs: IntArray,
    /** Répartition théorique (probabilités) du nombre de pairs par tirage. */
    val repartitionPairsTheorique: DoubleArray,
    val sommeMoyenneObservee: Double?
) {
    fun stat(numero: Int): StatNumero = parNumero[numero - 1]
    fun plusFrequents(n: Int) = parNumero.sortedWith(compareByDescending<StatNumero> { it.frequence }.thenBy { it.numero }).take(n)
    fun plusEnRetard(n: Int) = parNumero.sortedWith(compareByDescending<StatNumero> { it.ecartActuel }.thenBy { it.numero }).take(n)
}

object StatsEngine {

    /** [historique] du plus ancien au plus récent. */
    fun calculer(historique: List<Tirage>, config: LotoConfig): Statistiques {
        val n = historique.size
        val big = config.maxNumero
        val frequences = IntArray(big + 1)
        val derniereSortie = IntArray(big + 1) { -1 }
        val sommeEcarts = LongArray(big + 1)
        val nbEcarts = IntArray(big + 1)
        val ecartMax = IntArray(big + 1)

        historique.forEachIndexed { t, tirage ->
            for (num in tirage.numeros) {
                if (num !in 1..big) continue
                frequences[num]++
                val prec = derniereSortie[num]
                if (prec >= 0) {
                    val ecart = t - prec - 1
                    sommeEcarts[num] += ecart.toLong()
                    nbEcarts[num]++
                    if (ecart > ecartMax[num]) ecartMax[num] = ecart
                } else if (t > ecartMax[num]) {
                    ecartMax[num] = t // écart initial avant la première sortie
                }
                derniereSortie[num] = t
            }
        }

        val p = config.pSortie
        val esperance = n * p
        val sigma = sqrt(n * p * (1 - p))

        val parNumero = (1..big).map { num ->
            val actuel = if (derniereSortie[num] < 0) n else n - 1 - derniereSortie[num]
            StatNumero(
                numero = num,
                frequence = frequences[num],
                zFrequence = if (sigma > 0) (frequences[num] - esperance) / sigma else 0.0,
                ecartActuel = actuel,
                ecartMoyen = if (nbEcarts[num] > 0) sommeEcarts[num].toDouble() / nbEcarts[num] else null,
                ecartMax = maxOf(ecartMax[num], actuel),
                jamaisSorti = derniereSortie[num] < 0
            )
        }

        // Khi-deux d'uniformité. Les numéros d'un même tirage sont tirés SANS remise :
        // E[Σ(O-E)²/E] = N - K au lieu de N - 1. On corrige par le facteur (N-1)/(N-K)
        // pour retrouver approximativement une loi χ²(N-1).
        val uniformite = if (n >= 10) {
            var chi2 = 0.0
            for (num in 1..big) {
                val d = frequences[num] - esperance
                chi2 += d * d / esperance
            }
            val corrige = chi2 * (big - 1).toDouble() / (big - config.numerosParTirage)
            TestUniformite(corrige, big - 1, MathUtils.pValeurKhiDeux(corrige, big - 1))
        } else null

        // Parité
        val k = config.numerosParTirage
        val nbPairsTotal = big / 2
        val repartition = IntArray(k + 1)
        historique.forEach { t -> repartition[t.numeros.count { it % 2 == 0 }.coerceAtMost(k)]++ }
        val theorique = DoubleArray(k + 1) { pairs ->
            MathUtils.combinaison(nbPairsTotal, pairs) *
                MathUtils.combinaison(big - nbPairsTotal, k - pairs) /
                MathUtils.combinaison(big, k)
        }

        val sommeMoy = if (n > 0) historique.sumOf { it.numeros.sum() }.toDouble() / n else null

        return Statistiques(config, n, parNumero, uniformite, repartition, theorique, sommeMoy)
    }
}
