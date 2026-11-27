package com.example.lotobot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.lotobot.LotoBot
import com.example.lotobot.R
import com.example.lotobot.data.Preferences
import com.example.lotobot.engine.Backtest
import com.example.lotobot.engine.Rapport
import com.example.lotobot.engine.StatsEngine
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class StatsActivity : AppCompatActivity() {

    private val calcul = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val tvRapport = findViewById<TextView>(R.id.tvRapport)
        val btnPartager = findViewById<MaterialButton>(R.id.btnPartager)
        val appContext = applicationContext

        calcul.execute {
            val tirages = LotoBot.historique(appContext).charger()
            val stats = StatsEngine.calculer(tirages, LotoBot.config)
            // Le backtest rejoue jusqu'à 300 tirages : quelques centaines de ms sur un téléphone
            val backtest = Backtest.executer(tirages, LotoBot.config, Preferences(appContext).reglages)
            val rapport = Rapport.generer(stats, backtest)

            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                tvRapport.text = rapport
                btnPartager.isEnabled = true
                btnPartager.setOnClickListener {
                    val envoi = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Rapport LOTOPOSBOT")
                        putExtra(Intent.EXTRA_TEXT, rapport)
                    }
                    startActivity(Intent.createChooser(envoi, getString(R.string.bouton_partager)))
                }
            }
        }
    }

    override fun onDestroy() {
        calcul.shutdownNow()
        super.onDestroy()
    }
}
