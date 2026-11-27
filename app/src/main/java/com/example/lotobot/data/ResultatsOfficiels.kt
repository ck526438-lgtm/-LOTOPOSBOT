package com.example.lotobot.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * Télécharge les résultats officiels du Loto Bonheur.
 *
 * Les données viennent de l'API publique de lotobonheur.ci ; une action GitHub du dépôt
 * les récupère chaque nuit et les publie dans data/import/ (un fichier par tirage).
 * L'appli lit ces fichiers : pas de clé, pas de serveur à maintenir.
 */
object ResultatsOfficiels {

    private const val BASE =
        "https://raw.githubusercontent.com/ck526438-lgtm/-LOTOPOSBOT/main/data/import/"

    data class Source(val fichier: String, val libelle: String, val nbTirages: Int)

    /** Liste des historiques disponibles (physiques, digitaux, et chaque tirage). À appeler hors thread UI. */
    fun sources(): List<Source> =
        lire(BASE + "index.txt").lines().mapNotNull { ligne ->
            val p = ligne.split(";")
            if (p.size == 3 && p[0].matches(Regex("[A-Za-z0-9_]+\\.txt"))) {
                Source(p[0], p[1], p[2].trim().toIntOrNull() ?: 0)
            } else null
        }

    /** Contenu du fichier, au format d'import de l'appli. À appeler hors thread UI. */
    fun telecharger(source: Source): String {
        require(source.fichier.matches(Regex("[A-Za-z0-9_]+\\.txt")))
        return lire(BASE + source.fichier)
    }

    private fun lire(adresse: String): String {
        val connexion = URL(adresse).openConnection() as HttpURLConnection
        connexion.connectTimeout = 15_000
        connexion.readTimeout = 30_000
        connexion.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (connexion.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Serveur : code ${connexion.responseCode}")
            }
            return connexion.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connexion.disconnect()
        }
    }
}
