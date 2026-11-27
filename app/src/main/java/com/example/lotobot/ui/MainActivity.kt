package com.example.lotobot.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.lotobot.LotoBot
import com.example.lotobot.R
import com.example.lotobot.data.Preferences
import com.example.lotobot.data.PronosticJour
import com.example.lotobot.engine.Tirage
import com.example.lotobot.engine.TirageParser
import com.example.lotobot.notif.Notifications
import com.example.lotobot.notif.Planificateur
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val calcul = Executors.newSingleThreadExecutor()
    private lateinit var prefs: Preferences

    private lateinit var boules: List<TextView>
    private lateinit var tvDate: TextView
    private lateinit var tvDetails: TextView
    private lateinit var tvHistorique: TextView
    private lateinit var tvChaud: TextView
    private lateinit var tvRetard: TextView
    private lateinit var tvInfoModele: TextView
    private lateinit var etNouveauTirage: EditText
    private lateinit var switchNotif: MaterialSwitch
    private lateinit var btnHeure: MaterialButton

    private val demandePermissionNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { accordee ->
            if (accordee) {
                activerNotification()
            } else {
                switchNotif.isChecked = false
                Toast.makeText(this, "Notifications refusées dans les réglages Android", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Preferences(this)

        boules = listOf(R.id.boule1, R.id.boule2, R.id.boule3, R.id.boule4).map { findViewById<TextView>(it) }
        tvDate = findViewById(R.id.tvDate)
        tvDetails = findViewById(R.id.tvDetails)
        tvHistorique = findViewById(R.id.tvHistorique)
        tvChaud = findViewById(R.id.tvChaud)
        tvRetard = findViewById(R.id.tvRetard)
        tvInfoModele = findViewById(R.id.tvInfoModele)
        etNouveauTirage = findViewById(R.id.etNouveauTirage)
        switchNotif = findViewById(R.id.switchNotif)
        btnHeure = findViewById(R.id.btnHeure)

        initialiserReglages()
        initialiserNotification()

        findViewById<MaterialButton>(R.id.btnRecalculer).setOnClickListener { chargerGrille(forcer = true) }
        findViewById<MaterialButton>(R.id.btnAjouter).setOnClickListener { ajouterResultats() }
        findViewById<MaterialButton>(R.id.btnAnnulerDernier).setOnClickListener { annulerDernier() }
        findViewById<MaterialButton>(R.id.btnExporter).setOnClickListener { exporterHistorique() }
        findViewById<MaterialButton>(R.id.btnStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnDemo).setOnClickListener { confirmerDemo() }
    }

    override fun onResume() {
        super.onResume()
        chargerGrille(forcer = false)
    }

    override fun onDestroy() {
        calcul.shutdown()
        super.onDestroy()
    }

    // ───────────── Grille du jour ─────────────

    private fun chargerGrille(forcer: Boolean) {
        val appContext = applicationContext
        calcul.execute {
            val grille = LotoBot.pronosticDuJour(appContext, forcer)
            val nbTirages = LotoBot.historique(appContext).charger().size
            runOnUiThread {
                if (!isDestroyed) afficherGrille(grille, nbTirages)
                if (forcer && !isDestroyed) Toast.makeText(this, "Grille recalculée", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun afficherGrille(grille: PronosticJour, nbTirages: Int) {
        grille.numeros.forEachIndexed { i, n -> if (i < boules.size) boules[i].text = n.toString().padStart(2, '0') }

        val date = LocalDate.parse(grille.date)
            .format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE))
            .replaceFirstChar { it.uppercase() }
        tvDate.text = date

        val pairs = grille.numeros.count { it % 2 == 0 }
        val somme = grille.numeros.sum()
        val base = if (grille.nbTirages == 0) {
            "Aucun historique : grille équilibrée tirée au hasard."
        } else {
            "Calculée sur ${grille.nbTirages} tirage(s)."
        }
        tvDetails.text = "$base  Pairs/impairs : $pairs/${grille.numeros.size - pairs} · Somme : $somme"

        tvHistorique.text = "Historique enregistré : $nbTirages tirage(s). " +
            (if (nbTirages < 50) "Il en faut au moins 50 pour que les pondérations aient un effet notable." else "")
    }

    // ───────────── Réglages du modèle ─────────────

    private fun initialiserReglages() {
        val seekChaud = findViewById<SeekBar>(R.id.seekChaud)
        val seekRetard = findViewById<SeekBar>(R.id.seekRetard)
        val switchEquilibre = findViewById<MaterialSwitch>(R.id.switchEquilibre)

        val r = prefs.reglages
        seekChaud.progress = (r.poidsChaud * 100).toInt()
        seekRetard.progress = (r.poidsRetard * 100).toInt()
        switchEquilibre.isChecked = r.equilibre
        majLibelles(seekChaud.progress, seekRetard.progress)
        tvInfoModele.text = "Les réglages s'appliquent au prochain « Recalculer ». " +
            "Ils changent la sélection, pas la probabilité de gagner."

        val ecouteur = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) =
                majLibelles(seekChaud.progress, seekRetard.progress)

            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) {
                prefs.reglages = prefs.reglages.copy(
                    poidsChaud = seekChaud.progress / 100.0,
                    poidsRetard = seekRetard.progress / 100.0
                )
            }
        }
        seekChaud.setOnSeekBarChangeListener(ecouteur)
        seekRetard.setOnSeekBarChangeListener(ecouteur)
        switchEquilibre.setOnCheckedChangeListener { _, coche ->
            prefs.reglages = prefs.reglages.copy(equilibre = coche)
        }
    }

    private fun majLibelles(chaud: Int, retard: Int) {
        tvChaud.text = "${getString(R.string.label_chaud)} : $chaud %"
        tvRetard.text = "${getString(R.string.label_retard)} : $retard %"
    }

    // ───────────── Historique ─────────────

    private fun ajouterResultats() {
        val saisie = etNouveauTirage.text.toString()
        val resultat = TirageParser.analyser(saisie, LotoBot.config)

        if (resultat.tirages.isEmpty()) {
            val detail = resultat.erreurs.firstOrNull()
                ?: "Entre ${LotoBot.config.numerosParTirage} numéros entre 1 et ${LotoBot.config.maxNumero}"
            Toast.makeText(this, detail, Toast.LENGTH_LONG).show()
            return
        }

        // Un seul tirage saisi sans date : on considère que c'est celui du jour
        val aEnregistrer = if (resultat.tirages.size == 1 && resultat.tirages[0].date.isBlank()) {
            listOf(resultat.tirages[0].copy(date = LocalDate.now().toString()))
        } else {
            resultat.tirages
        }

        val appContext = applicationContext
        calcul.execute {
            LotoBot.historique(appContext).ajouter(aEnregistrer)
            runOnUiThread {
                etNouveauTirage.text.clear()
                val message = buildString {
                    append("${aEnregistrer.size} tirage(s) enregistré(s)")
                    if (resultat.erreurs.isNotEmpty()) append(" · ${resultat.erreurs.size} ligne(s) ignorée(s) : ${resultat.erreurs.first()}")
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                chargerGrille(forcer = false)
            }
        }
    }

    private fun annulerDernier() {
        val appContext = applicationContext
        calcul.execute {
            val supprime = LotoBot.historique(appContext).supprimerDernier()
            runOnUiThread {
                val msg = supprime?.let { "Supprimé : ${it.versLigne()}" } ?: "Historique vide"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                chargerGrille(forcer = false)
            }
        }
    }

    private fun exporterHistorique() {
        val appContext = applicationContext
        calcul.execute {
            val texte = LotoBot.historique(appContext).exporter()
            runOnUiThread {
                if (texte.isBlank()) {
                    Toast.makeText(this, "Historique vide", Toast.LENGTH_SHORT).show()
                } else {
                    val envoi = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Historique LOTOPOSBOT")
                        putExtra(Intent.EXTRA_TEXT, texte)
                    }
                    startActivity(Intent.createChooser(envoi, "Exporter l'historique"))
                }
            }
        }
    }

    private fun confirmerDemo() {
        AlertDialog.Builder(this)
            .setTitle("Historique de démonstration")
            .setMessage(
                "Charge 500 tirages SIMULÉS (générés au hasard, ce ne sont pas de vrais résultats) " +
                    "pour essayer les statistiques et le backtest.\n\nL'historique actuel sera remplacé."
            )
            .setPositiveButton("Charger") { _, _ -> chargerDemo() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun chargerDemo() {
        val appContext = applicationContext
        calcul.execute {
            val texte = appContext.assets.open("historique_demo_simule.csv").bufferedReader().use { it.readText() }
            val tirages: List<Tirage> = TirageParser.analyser(texte, LotoBot.config).tirages
            LotoBot.historique(appContext).remplacer(tirages)
            runOnUiThread {
                Toast.makeText(this, "${tirages.size} tirages simulés chargés", Toast.LENGTH_SHORT).show()
                chargerGrille(forcer = true)
            }
        }
    }

    // ───────────── Notification quotidienne ─────────────

    private fun initialiserNotification() {
        switchNotif.isChecked = prefs.notificationActive && Notifications.autorisee(this)
        majBoutonHeure()

        switchNotif.setOnCheckedChangeListener { _, coche ->
            if (coche) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.autorisee(this)) {
                    demandePermissionNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    activerNotification()
                }
            } else {
                prefs.notificationActive = false
                Planificateur.annuler(this)
            }
        }

        btnHeure.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                prefs.heureNotif = h
                prefs.minuteNotif = m
                majBoutonHeure()
                if (prefs.notificationActive) Planificateur.planifier(this, h, m)
            }, prefs.heureNotif, prefs.minuteNotif, true).show()
        }
    }

    private fun activerNotification() {
        prefs.notificationActive = true
        Planificateur.planifier(this, prefs.heureNotif, prefs.minuteNotif)
        Toast.makeText(this, "Notification programmée chaque jour à ${heureTexte()}", Toast.LENGTH_SHORT).show()
    }

    private fun majBoutonHeure() {
        btnHeure.text = "Heure de la notification : ${heureTexte()}"
    }

    private fun heureTexte() =
        "${prefs.heureNotif.toString().padStart(2, '0')}:${prefs.minuteNotif.toString().padStart(2, '0')}"
}
