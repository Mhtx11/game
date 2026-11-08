package com.spcollector.gacha.engine

import kotlinx.coroutines.*
import java.util.SplittableRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/** histogram[i] = jumlah simulasi sukses tepat di pull ke-i. histogram[0] = jumlah gagal (melewati horizon). */
class McResult(
    val simulations: Int, val successes: Long, val failures: Long,
    val mean: Double, val median: Int?, val p90: Int?, val p95: Int?,
    val histogram: LongArray, val budget: Int?, val successWithinBudget: Double?,
)

data class McValidation(val maxAbsDiff: Double, val ok: Boolean)

enum class Scenario(val label: String) {
    CURRENT("Sesuai pengaturan & kondisi sekarang"),
    FRESH("Mulai dari pity 0 (tanpa carry-over)"),
    INDEPENDENT("Independen (tanpa pity & guarantee)"),
    NO_GUARANTEE("Tanpa featured guarantee"),
    NO_SOFT("Tanpa soft pity");

    fun apply(c: GachaConfig): GachaConfig = when (this) {
        INDEPENDENT -> c.copy(softPityStart = null, hardPity = null, guaranteeEnabled = false)
        NO_GUARANTEE -> c.copy(guaranteeEnabled = false)
        NO_SOFT -> c.copy(softPityStart = null)
        else -> c
    }

    fun start(s: PlayerState): PlayerState = if (this == FRESH || this == INDEPENDENT) PlayerState() else s
}

object MonteCarloEngine {
    fun simulateOne(cfg: GachaConfig, start: PlayerState, rng: SplittableRandom, horizon: Int): Int {
        var pity = start.pity
        var g = start.guaranteed && cfg.guaranteeEnabled
        var pulls = 0
        while (pulls < horizon) {
            pulls++
            if (rng.nextDouble() < cfg.topRateAt(pity + 1)) {
                pity = 0
                if (g) return pulls
                if (rng.nextDouble() < cfg.featuredRate) return pulls
                if (cfg.guaranteeEnabled) g = true
            } else pity++
        }
        return -1
    }

    suspend fun run(
        cfg: GachaConfig, start: PlayerState, sims: Int, budget: Int?,
        seed: Long = System.nanoTime(), onProgress: (Float) -> Unit = {},
    ): McResult = coroutineScope {
        val horizon = cfg.horizon
        val workers = max(1, min(8, Runtime.getRuntime().availableProcessors()))
        val per = sims / workers
        val done = AtomicInteger(0)
        val parts = (0 until workers).map { w ->
            async(Dispatchers.Default) {
                val count = if (w == workers - 1) sims - per * (workers - 1) else per
                val rng = SplittableRandom(seed + w * 7919L)
                val hist = LongArray(horizon + 1)
                for (s in 0 until count) {
                    val t = simulateOne(cfg, start, rng, horizon)
                    if (t > 0) hist[t]++ else hist[0]++
                    if ((s and 4095) == 4095) {
                        ensureActive()
                        onProgress(min(1f, done.addAndGet(4096).toFloat() / sims))
                    }
                }
                hist
            }
        }
        val total = LongArray(horizon + 1)
        parts.awaitAll().forEach { h -> for (i in h.indices) total[i] += h[i] }
        val failures = total[0]
        val succ = sims - failures
        var sum = 0.0
        for (i in 1..horizon) sum += i.toDouble() * total[i]
        fun pct(q: Double): Int? {
            var cum = 0L
            val target = q * sims
            for (i in 1..horizon) { cum += total[i]; if (cum >= target) return i }
            return null
        }
        var within: Double? = null
        if (budget != null) {
            var c = 0L
            for (i in 1..min(budget, horizon)) c += total[i]
            within = c.toDouble() / sims
        }
        McResult(sims, succ, failures, if (succ > 0) sum / succ else Double.NaN, pct(0.5), pct(0.9), pct(0.95), total, budget, within)
    }

    /** Bandingkan CDF empiris dengan CDF analitik; toleransi 5 sigma binomial. */
    fun validate(mc: McResult, cdf: DoubleArray): McValidation {
        var cum = 0L
        var maxD = 0.0
        var ok = true
        val n = mc.simulations.toDouble()
        for (i in 1 until min(mc.histogram.size, cdf.size)) {
            cum += mc.histogram[i]
            val a = cdf[i]
            val d = abs(cum / n - a)
            val tol = 5 * sqrt(max(a * (1 - a), 1e-12) / n) + 10.0 / n
            if (d > maxD) maxD = d
            if (d > tol) ok = false
        }
        return McValidation(maxD, ok)
    }
}
