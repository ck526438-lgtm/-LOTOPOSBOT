package com.example.lotobot.engine

/**
 * Types de paris du Loto Bonheur (LONACI) et leurs cotes officielles,
 * relevées sur lonaci.ci (novembre 2026). Gain = mise unitaire × cote × combinaisons gagnantes.
 *
 * C'est un jeu À COTE FIXE : la cote ne dépend pas du nombre de gagnants. Choisir des numéros
 * « peu joués » ne rapporte donc rien de plus. Le seul vrai levier mathématique est le TYPE de pari.
 */
enum class TypePari(val code: String, val libelle: String, val nbNumeros: Int, val cote: Int) {
    UN_N("1N", "1 numéro parmi les 5", 1, 10),
    PN("PN", "1er numéro tiré", 1, 40),
    DEUX_N("2N", "2 numéros parmi les 5", 2, 240),
    TROIS_N("3N", "3 numéros parmi les 5", 3, 2_000),
    QUATRE_N("4N", "4 numéros parmi les 5", 4, 5_000),
    CINQ_N("5N", "5 numéros parmi les 5", 5, 40_000);

    /** Probabilité qu'UNE combinaison de ce pari soit gagnante (tirage 5/90 équitable). */
    val probabilite: Double
        get() = when (this) {
            PN -> 1.0 / 90
            else -> MathUtils.combinaison(5, nbNumeros) / MathUtils.combinaison(90, nbNumeros)
        }

    /** Part moyenne des mises rendue aux joueurs (1,0 = jeu équitable). */
    val tauxDeRetour: Double get() = probabilite * cote

    /** Perte moyenne pour 10 000 FCFA misés. */
    val perteMoyennePour10000: Double get() = 10_000 * (1 - tauxDeRetour)
}

/** Analyse d'un pari « Perm » : on désigne [nbChoisis] numéros pour un type de pari donné. */
data class AnalysePerm(
    val type: TypePari,
    val nbChoisis: Int,
    val nbCombinaisons: Int,
    /** Probabilité qu'au moins une combinaison soit gagnante. */
    val probaAuMoinsUnGain: Double,
    val tauxDeRetour: Double
)

object Paris {

    /** Paris classés du moins défavorable au plus défavorable. */
    fun classement(): List<TypePari> = TypePari.entries.sortedByDescending { it.tauxDeRetour }

    /**
     * Pari Perm : C(m, k) combinaisons. Le taux de retour reste celui du pari simple
     * (chaque combinaison a la même espérance) ; seule la fréquence des gains change.
     */
    fun perm(type: TypePari, nbChoisis: Int): AnalysePerm {
        require(type != TypePari.PN) { "Le PN ne se joue pas en Perm" }
        require(nbChoisis in type.nbNumeros..10)
        val k = type.nbNumeros
        val combinaisons = MathUtils.combinaison(nbChoisis, k).toInt()
        // P(au moins k de mes m numéros parmi les 5 tirés) — loi hypergéométrique
        var p = 0.0
        for (bons in k..minOf(5, nbChoisis)) {
            p += MathUtils.combinaison(nbChoisis, bons) *
                MathUtils.combinaison(90 - nbChoisis, 5 - bons) /
                MathUtils.combinaison(90, 5)
        }
        return AnalysePerm(type, nbChoisis, combinaisons, p, type.tauxDeRetour)
    }
}
