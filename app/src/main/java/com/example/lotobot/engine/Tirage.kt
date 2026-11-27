package com.example.lotobot.engine

/** Un tirage officiel. [date] est libre (ex. "2026-11-20"), éventuellement vide. */
data class Tirage(val date: String, val numeros: List<Int>) {
    fun versLigne(): String = (if (date.isBlank()) "" else "$date;") + numeros.joinToString(",")
}

data class ResultatAnalyse(val tirages: List<Tirage>, val erreurs: List<String>)

/**
 * Lit un ou plusieurs tirages saisis ou collés, un par ligne.
 *
 * Formats acceptés :
 *   12, 34, 56, 78, 90
 *   12 34 56 78 90
 *   2026-11-20; 12,34,56,78,90
 *   20/11/2026 12-34-56-78-90
 *
 * Chaque tirage est contrôlé : bon nombre de numéros, dans 1..maxNumero, sans doublon.
 */
object TirageParser {

    private val regexDateIso = Regex("""^\s*(\d{4}-\d{2}-\d{2})\s*[;:|]?\s*""")
    private val regexDateFr = Regex("""^\s*(\d{2}/\d{2}/\d{4})\s*[;:|]?\s*""")
    private val regexNombre = Regex("""\d+""")

    fun analyser(texte: String, config: LotoConfig): ResultatAnalyse {
        val tirages = mutableListOf<Tirage>()
        val erreurs = mutableListOf<String>()

        texte.lines().forEachIndexed { index, brute ->
            val ligne = brute.trim()
            if (ligne.isEmpty() || ligne.startsWith("#")) return@forEachIndexed
            // Ignore une éventuelle ligne d'en-tête CSV
            if (ligne.none { it.isDigit() }) return@forEachIndexed

            var reste = ligne
            var date = ""
            val iso = regexDateIso.find(reste)
            val fr = regexDateFr.find(reste)
            when {
                iso != null -> { date = iso.groupValues[1]; reste = reste.substring(iso.range.last + 1) }
                fr != null -> { date = fr.groupValues[1]; reste = reste.substring(fr.range.last + 1) }
            }

            val numeros = regexNombre.findAll(reste).map { it.value.toInt() }.toList()
            val numLigne = index + 1
            when {
                numeros.size != config.numerosParTirage ->
                    erreurs += "Ligne $numLigne : ${numeros.size} numéros au lieu de ${config.numerosParTirage}"
                numeros.any { it !in 1..config.maxNumero } ->
                    erreurs += "Ligne $numLigne : numéro hors de 1..${config.maxNumero}"
                numeros.toSet().size != numeros.size ->
                    erreurs += "Ligne $numLigne : numéro en double"
                else -> tirages += Tirage(date, numeros.sorted())
            }
        }
        return ResultatAnalyse(tirages, erreurs)
    }
}
