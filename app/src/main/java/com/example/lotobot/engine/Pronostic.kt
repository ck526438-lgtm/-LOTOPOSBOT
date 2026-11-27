package com.example.lotobot.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Réglages du modèle de pondération (curseurs de l'appli, de 0 à 1).
 *
 * @param poidsChaud   favorise les numéros sortis plus souvent que la moyenne
 * @param poidsRetard  favorise les numéros absents depuis longtemps
 * @param equilibre    applique les filtres d'équilibre (parité, somme, dizaines)
 * @param modeBiais    « chasseur de biais » : ignore chauds/retards et ne favorise QUE les numéros
 *                     dont la sur-représentation est statistiquement significative (Bonferroni).
 *                     C'est le seul cas où un historique peut réellement aider : une machine
 *                     ou des boules défectueuses. Sans biais détecté, la sélection est uniforme.
 */
data class ReglagesModele(
    val poidsChaud: Double = 0.5,
    val poidsRetard: Double = 0.5,
    val equilibre: Boolean = true,
    val modeBiais: Boolean = false
)

data class Pronostic(
    val numeros: List<Int>,
    /** Poids relatif (1,0 = neutre) de chaque numéro choisi, pour l'affichage. */
    val poidsRelatifs: Map<Int, Double>,
    val nbPairs: Int,
    val somme: Int
)

/**
 * Génère un pronostic de k numéros.
 *
 * RAPPEL SCIENTIFIQUE : si le tirage est équitable, chaque combinaison a exactement la même
 * probabilité, quel que soit l'historique. Les pondérations ci-dessous ne modifient PAS les
 * chances de gain : elles produisent une sélection "raisonnée" selon des critères descriptifs.
 * Le backtest ([Backtest]) permet de le vérifier sur tes propres données.
 */
object PronosticEngine {

    /** En dessous de ce nombre de tirages, les pondérations sont fortement atténuées. */
    private const val HISTORIQUE_DE_REFERENCE = 50.0

    /**
     * Poids de chaque numéro (1..N). Bornés pour qu'aucun numéro ne pèse plus de ~10x un autre :
     * avec un historique court, les écarts observés sont surtout du bruit.
     */
    fun poids(stats: Statistiques, reglages: ReglagesModele): DoubleArray {
        if (reglages.modeBiais) return poidsBiais(stats)
        val n = stats.nbTirages.toDouble()
        // Rétrécissement vers l'uniforme quand l'historique est court
        val confiance = n / (n + HISTORIQUE_DE_REFERENCE)
        val aChaud = reglages.poidsChaud.coerceIn(0.0, 1.0) * confiance
        val aRetard = reglages.poidsRetard.coerceIn(0.0, 1.0) * confiance
        val ecartAttendu = stats.config.ecartAttendu

        val w = DoubleArray(stats.config.maxNumero + 1)
        for (s in stats.parNumero) {
            val z = s.zFrequence.coerceIn(-3.0, 3.0)
            val ratioRetard = (s.ecartActuel / ecartAttendu).coerceIn(0.0, 4.0)
            w[s.numero] = exp(0.35 * aChaud * z) * (1.0 + 0.5 * aRetard * ratioRetard)
        }
        return w
    }

    /**
     * Mode chasseur de biais : poids 1 partout, sauf pour les numéros significativement
     * sur-représentés, pondérés par leur fréquence relative observée (bornée à 3).
     */
    private fun poidsBiais(stats: Statistiques): DoubleArray {
        val w = DoubleArray(stats.config.maxNumero + 1) { if (it == 0) 0.0 else 1.0 }
        val attendu = stats.nbTirages * stats.config.pSortie
        for (s in stats.numerosBiaises) {
            w[s.numero] = (s.frequence / attendu).coerceIn(1.0, 3.0)
        }
        return w
    }

    fun generer(
        historique: List<Tirage>,
        config: LotoConfig,
        reglages: ReglagesModele,
        graine: Long
    ): Pronostic = generer(StatsEngine.calculer(historique, config), reglages, graine)

    fun generer(stats: Statistiques, reglages: ReglagesModele, graine: Long): Pronostic {
        val config = stats.config
        val w = poids(stats, reglages)
        val random = Random(graine)

        var candidat = tirerPondere(w, config.taillePronostic, random)
        if (reglages.equilibre) {
            var essais = 0
            while (!estEquilibre(candidat, config) && essais < 5000) {
                candidat = tirerPondere(w, config.taillePronostic, random)
                essais++
            }
        }

        val moyenne = (1..config.maxNumero).sumOf { w[it] } / config.maxNumero
        val tries = candidat.sorted()
        return Pronostic(
            numeros = tries,
            poidsRelatifs = tries.associateWith { w[it] / moyenne },
            nbPairs = tries.count { it % 2 == 0 },
            somme = tries.sum()
        )
    }

    /** Tirage pondéré SANS remise (méthode de la roulette, robuste aux arrondis). */
    fun tirerPondere(w: DoubleArray, k: Int, random: Random): List<Int> {
        val restants = (1 until w.size).toMutableList()
        val choix = ArrayList<Int>(k)
        repeat(k) {
            val total = restants.sumOf { w[it] }
            var cible = random.nextDouble() * total
            var indexChoisi = restants.lastIndex // repli si l'arrondi empêche d'atteindre 0
            for (i in restants.indices) {
                cible -= w[restants[i]]
                if (cible <= 0) {
                    indexChoisi = i
                    break
                }
            }
            choix += restants.removeAt(indexChoisi)
        }
        return choix
    }

    /**
     * Filtres d'équilibre (descriptifs, ils ne changent pas la probabilité de gain d'une grille,
     * mais écartent les combinaisons atypiques comme 1-2-3-4) :
     *  - parité : ni tout pair ni tout impair (pour 4 numéros, 2/2 sort ~38 % du temps, 1/3 ou 3/1 ~50 %)
     *  - somme dans l'intervalle central de 80 % (moyenne ± 1,28 écart-type)
     *  - au plus 2 numéros dans la même dizaine
     */
    fun estEquilibre(numeros: List<Int>, config: LotoConfig): Boolean {
        val k = numeros.size
        if (k >= 3) {
            val pairs = numeros.count { it % 2 == 0 }
            if (pairs == 0 || pairs == k) return false
        }
        val somme = numeros.sum()
        if (abs(somme - config.sommeMoyenne) > 1.28 * config.sommeEcartType) return false
        val parDizaine = numeros.groupingBy { (it - 1) / 10 }.eachCount()
        if (parDizaine.values.any { it > 2 }) return false
        return true
    }
}
