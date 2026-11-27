package com.example.lotobot.data

import com.example.lotobot.engine.LotoConfig
import com.example.lotobot.engine.Tirage
import com.example.lotobot.engine.TirageParser
import java.io.File

/**
 * Historique des tirages, sauvegardé dans un fichier texte (une ligne par tirage,
 * du plus ancien au plus récent) : "2026-11-20;5,12,34,67,89".
 *
 * Contrairement à la version d'origine, les tirages ajoutés survivent à la fermeture de l'appli.
 */
class HistoriqueRepository(private val fichier: File, private val config: LotoConfig) {

    @Synchronized
    fun charger(): List<Tirage> {
        if (!fichier.exists()) return emptyList()
        return TirageParser.analyser(fichier.readText(), config).tirages
    }

    @Synchronized
    fun ajouter(nouveaux: List<Tirage>) {
        if (nouveaux.isEmpty()) return
        fichier.parentFile?.mkdirs()
        val texte = nouveaux.joinToString("\n", postfix = "\n") { it.versLigne() }
        fichier.appendText(texte)
    }

    @Synchronized
    fun remplacer(tirages: List<Tirage>) {
        fichier.parentFile?.mkdirs()
        fichier.writeText(tirages.joinToString("\n", postfix = if (tirages.isEmpty()) "" else "\n") { it.versLigne() })
    }

    /** Supprime le dernier tirage (correction d'une erreur de saisie). */
    @Synchronized
    fun supprimerDernier(): Tirage? {
        val tous = charger()
        if (tous.isEmpty()) return null
        remplacer(tous.dropLast(1))
        return tous.last()
    }

    @Synchronized
    fun exporter(): String = if (fichier.exists()) fichier.readText() else ""
}
