package com.spcollector.gacha.engine

import kotlin.math.*

/** pity = jumlah pull sejak SP terakhir. Pull berikutnya adalah pull ke (pity + 1). */
data class PlayerState(val pity: Int = 0, val guaranteed: Boolean = false)

/**
 * topRate = P(rarity SP) per pull sebelum soft pity.
 * featuredRate = P(target | rarity SP) saat belum guaranteed. Jika guaranteed, target 100%.
 */
data class GachaConfig(
    val topRate: Double,
    val featuredRate: Double,
    val guaranteeEnabled: Boolean = false,
    val softPityStart: Int? = null,
    val softPityIncrement: Double = 0.0,
    val hardPity: Int? = null,
) {
    fun topRateAt(pullNo: Int): Double {
        hardPity?.let { if (pullNo >= it) return 1.0 }
        val s = softPityStart
        return if (s != null && pullNo >= s) min(1.0, topRate + (pullNo - s + 1) * softPityIncrement) else topRate
    }

    val horizon: Int get() = max(2000, (hardPity ?: 0) * 2 + 1)

    fun problems(): List<String> = buildList {
        if (topRate <= 0.0 || topRate > 1.0) add("Drop rate rarity SP harus di antara 0 dan 100%")
        if (featuredRate <= 0.0 || featuredRate > 1.0) add("Featured rate harus di antara 0 dan 100%")
        if (hardPity != null && hardPity <= 0) add("Hard pity harus > 0")
        if (softPityStart != null) {
            if (softPityStart <= 0) add("Awal soft pity harus > 0")
            if (softPityIncrement <= 0.0) add("Kenaikan soft pity harus > 0")
            if (hardPity != null && softPityStart > hardPity) add("Soft pity tidak boleh melebihi hard pity")
        }
    }
}

object ProbabilityEngine {
    /** Model independen tanpa pity: 1 - (1-p)^n. */
    fun independent(p: Double, n: Int): Double = 1.0 - (1.0 - p).pow(n)

    /**
     * Rantai Markov dengan state (pityCounter, guaranteedFlag).
     * Mengembalikan cdf[n] = P(target didapat dalam n pull pertama), cdf[0] = 0.
     */
    fun cdf(cfg: GachaConfig, start: PlayerState, horizon: Int = cfg.horizon): DoubleArray {
        val size = cfg.hardPity ?: (horizon + start.pity + 2)
        var a0 = DoubleArray(size)
        var a1 = DoubleArray(size)
        val k0 = start.pity.coerceIn(0, size - 1)
        if (start.guaranteed && cfg.guaranteeEnabled) a1[k0] = 1.0 else a0[k0] = 1.0
        val out = DoubleArray(horizon + 1)
        var cum = 0.0
        for (i in 1..horizon) {
            val n0 = DoubleArray(size)
            val n1 = DoubleArray(size)
            var succ = 0.0
            for (k in 0 until size) {
                val pt = cfg.topRateAt(k + 1)
                val nk = min(k + 1, size - 1)
                val m0 = a0[k]
                val m1 = a1[k]
                if (m0 > 0.0) {
                    n0[nk] += m0 * (1 - pt)
                    val hit = m0 * pt
                    succ += hit * cfg.featuredRate
                    val lose = hit * (1 - cfg.featuredRate)
                    if (cfg.guaranteeEnabled) n1[0] += lose else n0[0] += lose
                }
                if (m1 > 0.0) {
                    n1[nk] += m1 * (1 - pt)
                    succ += m1 * pt
                }
            }
            cum += succ
            out[i] = min(1.0, cum)
            a0 = n0; a1 = n1
        }
        return out
    }

    /** E[T] = sum P(T > n). Terpotong di horizon (lihat residual). */
    fun expectedPulls(cdf: DoubleArray): Double {
        var s = 0.0
        for (n in 0 until cdf.size - 1) s += 1.0 - cdf[n]
        return s
    }

    fun residual(cdf: DoubleArray): Double = 1.0 - cdf.last()

    fun percentile(cdf: DoubleArray, q: Double): Int? {
        for (n in 1 until cdf.size) if (cdf[n] >= q) return n
        return null
    }

    /** Jumlah pull terburuk sampai target PASTI didapat. null jika tidak ada batas (tanpa hard pity/guarantee). */
    fun worstCasePulls(cfg: GachaConfig, s: PlayerState): Int? {
        val hard = cfg.hardPity ?: return null
        val remaining = max(1, hard - s.pity)
        return when {
            cfg.featuredRate >= 1.0 -> remaining
            cfg.guaranteeEnabled -> if (s.guaranteed) remaining else remaining + hard
            else -> null
        }
    }
}
