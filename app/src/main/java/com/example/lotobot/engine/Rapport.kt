package com.example.lotobot.engine

import java.util.Locale

/** Produit le rapport statistique affiché dans l'écran "Statistiques" (texte à police fixe). */
object Rapport {

    private fun f(x: Double, dec: Int = 2) = String.format(Locale.FRANCE, "%.${dec}f", x)
    private fun pct(x: Double) = String.format(Locale.FRANCE, "%.1f %%", x * 100)
    private fun n2(x: Int) = x.toString().padStart(2, '0')

    /** Comparatif des paris Loto Bonheur : le vrai levier mathématique. */
    fun sectionParis(): String = buildString {
        appendLine("── LOTO BONHEUR : CE QUE RAPPORTE CHAQUE PARI ──")
        appendLine("  (cotes officielles LONACI, retour moyen sur 100 FCFA misés)")
        for (t in Paris.classement()) {
            appendLine(
                "  ${t.code.padEnd(3)} cote ${t.cote.toString().padStart(6)}   1 sur ${f(1 / t.probabilite, 0).padStart(9)}" +
                    "   rend ${f(t.tauxDeRetour * 100, 1).padStart(5)} FCFA"
            )
        }
        val perm = Paris.perm(TypePari.DEUX_N, 4)
        appendLine("  → Tes 4 numéros en 4N : ${f(TypePari.QUATRE_N.tauxDeRetour * 100, 1)} FCFA rendus / 100.")
        appendLine("    Les mêmes 4 numéros en 2N Perm (${perm.nbCombinaisons} combinaisons) :")
        appendLine("    ${f(perm.tauxDeRetour * 100, 1)} FCFA rendus / 100, un gain 1 fois sur ${f(1 / perm.probaAuMoinsUnGain, 0)}.")
        appendLine("  Tous les paris perdent en moyenne : le 2N perd le moins.")
        appendLine()
    }

    fun generer(stats: Statistiques, backtest: ResultatBacktest?): String = buildString {
        val c = stats.config
        appendLine("LOTO ${c.numerosParTirage}/${c.maxNumero} · ${stats.nbTirages} tirages analysés")
        appendLine()

        if (stats.nbTirages == 0) {
            appendLine("Aucun tirage enregistré.")
            appendLine("Ajoute les résultats officiels depuis l'écran principal.")
            appendLine()
            if (c.maxNumero == 90 && c.numerosParTirage == 5) append(sectionParis())
            return@buildString
        }

        appendLine("── REPÈRES THÉORIQUES ──")
        appendLine("P(un numéro sort)          : 1/${f(1 / c.pSortie, 0)}  (${pct(c.pSortie)})")
        appendLine("Sorties attendues/numéro   : ${f(stats.nbTirages * c.pSortie, 1)}")
        appendLine("Écart actuel moyen attendu : ${f(c.ecartAttendu, 0)} tirages")
        appendLine("Bons numéros attendus (${c.taillePronostic} joués) : ${f(c.bonsAttendus, 3)}")
        for (b in c.taillePronostic downTo 2) {
            val p = c.probaBons(b)
            appendLine("  P($b bons sur ${c.taillePronostic}) = 1 sur ${f(1 / p, 0)}")
        }
        appendLine()

        if (c.maxNumero == 90 && c.numerosParTirage == 5) append(sectionParis())

        appendLine("── NUMÉROS LES PLUS SORTIS (« chauds ») ──")
        stats.plusFrequents(10).forEach {
            appendLine("  ${n2(it.numero)}  ${it.frequence.toString().padStart(4)} fois   z = ${f(it.zFrequence).padStart(5)}")
        }
        appendLine()

        appendLine("── NUMÉROS LES PLUS EN RETARD ──")
        stats.plusEnRetard(10).forEach {
            val moy = it.ecartMoyen?.let { m -> f(m, 1) } ?: "—"
            val jamais = if (it.jamaisSorti) "  (jamais sorti)" else ""
            appendLine("  ${n2(it.numero)}  écart ${it.ecartActuel.toString().padStart(3)}   moyen $moy   max ${it.ecartMax}$jamais")
        }
        appendLine()

        appendLine("── TEST D'UNIFORMITÉ (khi-deux) ──")
        val u = stats.uniformite
        if (u == null) {
            appendLine("  Il faut au moins 10 tirages.")
        } else {
            appendLine("  χ² = ${f(u.statistique, 1)}  (ddl ${u.ddl})   p = ${f(u.pValeur, 3)}")
            if (u.compatibleAvecHasard) {
                appendLine("  → Fréquences compatibles avec un tirage équitable.")
                appendLine("    Les « chauds » et « froids » sont du bruit statistique.")
            } else {
                appendLine("  → Écart inhabituel. Vérifie d'abord la saisie de l'historique")
                appendLine("    (doublons, erreurs) avant d'y voir un biais réel.")
            }
        }
        appendLine()

        appendLine("── CHASSEUR DE BIAIS (défaut réel de machine ?) ──")
        if (stats.nbTirages < 100) {
            appendLine("  Il faut au moins 100 tirages réels (idéalement 1 000+).")
        } else if (stats.numerosBiaises.isEmpty()) {
            appendLine("  Aucun numéro significativement sur-représenté")
            appendLine("  (test binomial corrigé de Bonferroni, seuil 5 %).")
            appendLine("  → Aucun biais exploitable : le tirage se comporte comme du hasard.")
        } else {
            stats.numerosBiaises.forEach {
                appendLine("  ${n2(it.numero)}  ${it.frequence} sorties  p corrigé = ${f(it.pSurRepresentation, 4)}")
            }
            appendLine("  → Possible défaut réel. À confirmer sur les tirages SUIVANTS")
            appendLine("    avant d'y croire (un biais réel persiste, une coïncidence non).")
        }
        appendLine()

        appendLine("── PARITÉ (nb de pairs par tirage) ──")
        for (p in stats.repartitionPairs.indices) {
            val obs = stats.repartitionPairs[p].toDouble() / stats.nbTirages
            appendLine("  $p pairs : observé ${pct(obs).padStart(7)}   théorique ${pct(stats.repartitionPairsTheorique[p]).padStart(7)}")
        }
        stats.sommeMoyenneObservee?.let {
            val theo = c.numerosParTirage * (c.maxNumero + 1) / 2.0
            appendLine("  Somme moyenne d'un tirage : ${f(it, 1)}  (théorique ${f(theo, 1)})")
        }
        appendLine()

        appendLine("── BACKTEST DU MODÈLE (walk-forward) ──")
        if (backtest == null) {
            appendLine("  Il faut plus de 30 tirages pour tester le modèle.")
        } else {
            appendLine("  ${backtest.nbTests} grilles testées sur des tirages que le modèle ne connaissait pas.")
            appendLine("  Bons numéros / grille :")
            appendLine("    Modèle     : ${f(backtest.moyenneModele, 3)}")
            appendLine("    Aléatoire  : ${f(backtest.moyenneAleatoire, 3)}")
            appendLine("    Théorique  : ${f(backtest.moyenneTheorique, 3)}")
            appendLine("    Score z    : ${f(backtest.zModele)}")
            for (b in backtest.repartitionModele.indices) {
                val obs = backtest.repartitionModele[b].toDouble() / backtest.nbTests
                appendLine("    $b bon(s) : ${pct(obs).padStart(7)}   attendu ${pct(backtest.repartitionTheorique[b]).padStart(7)}")
            }
            if (backtest.significatif) {
                appendLine("  → Le modèle a fait mieux que le hasard sur cette période.")
                appendLine("    Avec des tirages équitables, c'est un coup de chance :")
                appendLine("    cela ne se reproduit pas de façon durable.")
            } else {
                appendLine("  → Aucune différence significative avec le hasard (|z| < 2),")
                appendLine("    ce qui est le résultat attendu pour des tirages indépendants.")
            }
        }
        appendLine()
        appendLine("Rappel : chaque tirage est indépendant des précédents.")
        appendLine("Aucune méthode ne peut prédire les numéros. Joue avec modération.")
    }
}
