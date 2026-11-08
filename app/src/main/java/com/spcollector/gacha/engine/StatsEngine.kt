package com.spcollector.gacha.engine

import kotlin.math.*

data class ChiSquareResult(val statistic: Double, val df: Int, val pValue: Double)

object StatsEngine {
    fun wilson(k: Int, n: Int, z: Double = 1.96): Pair<Double, Double> {
        if (n == 0) return 0.0 to 1.0
        val p = k.toDouble() / n
        val d = 1 + z * z / n
        val c = p + z * z / (2 * n)
        val m = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n))
        return ((c - m) / d).coerceAtLeast(0.0) to ((c + m) / d).coerceAtMost(1.0)
    }

    /** null = INSUFFICIENT DATA (n < 30 atau ada expected count < 5). rates harus berjumlah 1. */
    fun chiSquare(observed: List<Int>, rates: List<Double>): ChiSquareResult? {
        val n = observed.sum()
        if (n < 30) return null
        val cats = rates.indices.filter { rates[it] > 0.0 }
        if (cats.size < 2 || cats.any { n * rates[it] < 5.0 }) return null
        var chi = 0.0
        for (i in cats) { val e = n * rates[i]; chi += (observed[i] - e).pow(2) / e }
        val df = cats.size - 1
        return ChiSquareResult(chi, df, (1.0 - regGammaP(df / 2.0, chi / 2.0)).coerceIn(0.0, 1.0))
    }

    fun lnGamma(x: Double): Double {
        val c = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
        var y = x
        var tmp = x + 5.5
        tmp -= (x + 0.5) * ln(tmp)
        var ser = 1.000000000190015
        for (j in 0..5) { y += 1; ser += c[j] / y }
        return -tmp + ln(2.5066282746310005 * ser / x)
    }

    /** Regularized lower incomplete gamma P(a, x). */
    fun regGammaP(a: Double, x: Double): Double {
        if (x <= 0.0) return 0.0
        if (x < a + 1) {
            var term = 1.0 / a
            var sum = term
            var n = a
            for (i in 1..1000) { n += 1; term *= x / n; sum += term; if (abs(term) < abs(sum) * 1e-15) break }
            return sum * exp(-x + a * ln(x) - lnGamma(a))
        }
        var b = x + 1 - a
        var c = 1e300
        var d = 1 / b
        var h = d
        for (i in 1..1000) {
            val an = -i * (i - a)
            b += 2
            d = an * d + b; if (abs(d) < 1e-300) d = 1e-300
            c = b + an / c; if (abs(c) < 1e-300) c = 1e-300
            d = 1 / d
            val del = d * c
            h *= del
            if (abs(del - 1) < 1e-15) break
        }
        return 1.0 - exp(-x + a * ln(x) - lnGamma(a)) * h
    }
}
