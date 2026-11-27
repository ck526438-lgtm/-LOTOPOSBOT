package com.example.lotobot.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

object MathUtils {

    /** Coefficient binomial C(n, k) en Double (exact jusqu'à de très grandes valeurs pour un loto). */
    fun combinaison(n: Int, k: Int): Double {
        if (k < 0 || k > n) return 0.0
        val kk = minOf(k, n - k)
        var r = 1.0
        for (i in 1..kk) r = r * (n - kk + i) / i
        return r
    }

    /** ln Γ(x) — approximation de Lanczos. */
    fun lnGamma(x: Double): Double {
        val c = doubleArrayOf(
            76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5
        )
        var y = x
        val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
        var ser = 1.000000000190015
        for (cj in c) {
            y += 1.0
            ser += cj / y
        }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    /** Fonction gamma incomplète régularisée supérieure Q(a, x) = 1 - P(a, x). */
    fun gammaQ(a: Double, x: Double): Double {
        require(a > 0 && x >= 0)
        if (x == 0.0) return 1.0
        return if (x < a + 1) 1.0 - gammaSerie(a, x) else gammaFractionContinue(a, x)
    }

    private fun gammaSerie(a: Double, x: Double): Double {
        var ap = a
        var somme = 1.0 / a
        var del = somme
        repeat(1000) {
            ap += 1.0
            del *= x / ap
            somme += del
            if (abs(del) < abs(somme) * 1e-14) return somme * exp(-x + a * ln(x) - lnGamma(a))
        }
        return somme * exp(-x + a * ln(x) - lnGamma(a))
    }

    private fun gammaFractionContinue(a: Double, x: Double): Double {
        val petit = 1e-300
        var b = x + 1.0 - a
        var c = 1.0 / petit
        var d = 1.0 / b
        var h = d
        for (i in 1..1000) {
            val an = -i * (i - a)
            b += 2.0
            d = an * d + b
            if (abs(d) < petit) d = petit
            c = b + an / c
            if (abs(c) < petit) c = petit
            d = 1.0 / d
            val del = d * c
            h *= del
            if (abs(del - 1.0) < 1e-14) break
        }
        return exp(-x + a * ln(x) - lnGamma(a)) * h
    }

    /** p-valeur d'un khi-deux : P(X >= stat) pour X ~ χ²(ddl). */
    fun pValeurKhiDeux(stat: Double, ddl: Int): Double = gammaQ(ddl / 2.0, stat / 2.0)
}
