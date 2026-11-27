package com.example.lotobot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class MoteurTest {

    private val cfg = LotoConfig()
    private val historiqueSimule: List<Tirage> by lazy {
        val r = Random(42)
        List(1000) { Tirage("", (1..90).shuffled(r).take(5).sorted()) }
    }

    @Test
    fun probabilitesExactes() {
        assertEquals(1.0 / 511_038, cfg.probaBons(4), 1e-12)
        assertEquals(1.0, (0..4).sumOf { cfg.probaBons(it) }, 1e-12)
        assertEquals(17.0, cfg.ecartAttendu, 1e-9)
        assertEquals(4 * 5.0 / 90, cfg.bonsAttendus, 1e-12)
    }

    @Test
    fun pValeurKhiDeux() {
        assertEquals(0.05, MathUtils.pValeurKhiDeux(3.841458820694124, 1), 1e-9)
        assertEquals(0.4800, MathUtils.pValeurKhiDeux(89.0, 89), 1e-3)
    }

    @Test
    fun parseurValideLesTirages() {
        val r = TirageParser.analyser(
            "date,n\n2026-11-20; 5,12,34,67,89\n20/11/2026 1-2-3-4-90\n1 2 3\n1,1,2,3,4\n1,2,3,4,95\n# commentaire\n",
            cfg
        )
        assertEquals(2, r.tirages.size)
        assertEquals(3, r.erreurs.size)
        assertEquals("2026-11-20", r.tirages[0].date)
    }

    @Test
    fun statistiquesCoherentes() {
        val s = StatsEngine.calculer(historiqueSimule, cfg)
        assertEquals(5000, s.parNumero.sumOf { it.frequence })
        val ecartMoyen = s.parNumero.mapNotNull { it.ecartMoyen }.average()
        assertTrue("écart moyen $ecartMoyen", abs(ecartMoyen - 17.0) < 1.0)
        assertTrue("tirage simulé équitable", s.uniformite!!.compatibleAvecHasard)
    }

    @Test
    fun pronosticDeterministeEtEquilibre() {
        val s = StatsEngine.calculer(historiqueSimule, cfg)
        val reglages = ReglagesModele()
        assertEquals(PronosticEngine.generer(s, reglages, 7L), PronosticEngine.generer(s, reglages, 7L))
        repeat(300) { graine ->
            val p = PronosticEngine.generer(s, reglages, graine.toLong())
            assertEquals(4, p.numeros.toSet().size)
            assertTrue(p.numeros.all { it in 1..90 })
            assertTrue(PronosticEngine.estEquilibre(p.numeros, cfg))
        }
    }

    @Test
    fun historiqueVide() {
        val p = PronosticEngine.generer(emptyList(), cfg, ReglagesModele(), 1L)
        assertEquals(4, p.numeros.size)
    }

    @Test
    fun backtestNeBatPasLeHasardSurDesTiragesEquitables() {
        val bt = Backtest.executer(historiqueSimule, cfg, ReglagesModele(1.0, 1.0))!!
        assertEquals(300, bt.nbTests)
        assertTrue("z = ${bt.zModele}", abs(bt.zModele) < 3.0)
    }
}
