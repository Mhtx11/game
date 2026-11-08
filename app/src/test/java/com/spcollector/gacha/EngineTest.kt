package com.spcollector.gacha

import com.spcollector.gacha.engine.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class EngineTest {
    @Test fun independentMatchesClosedForm() {
        val cfg = GachaConfig(topRate = 0.02, featuredRate = 1.0)
        val cdf = ProbabilityEngine.cdf(cfg, PlayerState(), 300)
        for (n in listOf(1, 10, 50, 100, 300)) assertEquals(1 - 0.98.pow(n), cdf[n], 1e-9)
        assertEquals(50.0, ProbabilityEngine.expectedPulls(ProbabilityEngine.cdf(cfg, PlayerState(), 3000)), 0.01)
    }

    @Test fun hardPityGuaranteesAtThreshold() {
        val cfg = GachaConfig(topRate = 0.01, featuredRate = 1.0, hardPity = 10)
        val cdf = ProbabilityEngine.cdf(cfg, PlayerState())
        assertTrue(cdf[9] < 1.0)
        assertEquals(1.0, cdf[10], 1e-12)
        assertEquals(10, ProbabilityEngine.worstCasePulls(cfg, PlayerState()))
        assertEquals(4, ProbabilityEngine.worstCasePulls(cfg, PlayerState(pity = 6)))
    }

    @Test fun featuredGuaranteeNeedsAtMostTwoHardPities() {
        val cfg = GachaConfig(topRate = 0.0001, featuredRate = 0.5, guaranteeEnabled = true, hardPity = 10)
        val cdf = ProbabilityEngine.cdf(cfg, PlayerState())
        assertEquals(0.5, cdf[10], 1e-3)
        assertEquals(1.0, cdf[20], 1e-9)
        assertEquals(20, ProbabilityEngine.worstCasePulls(cfg, PlayerState()))
        assertEquals(10, ProbabilityEngine.worstCasePulls(cfg, PlayerState(guaranteed = true)))
    }

    @Test fun softPityRaisesRate() {
        val cfg = GachaConfig(0.01, 1.0, softPityStart = 5, softPityIncrement = 0.1, hardPity = 10)
        assertEquals(0.01, cfg.topRateAt(4), 1e-12)
        assertEquals(0.11, cfg.topRateAt(5), 1e-12)
        assertEquals(1.0, cfg.topRateAt(10), 1e-12)
    }

    @Test fun monteCarloAgreesWithAnalytic() = runBlocking {
        val cfg = GachaConfig(0.02, 0.5, guaranteeEnabled = true, softPityStart = 40, softPityIncrement = 0.05, hardPity = 60)
        val start = PlayerState(pity = 10)
        val mc = MonteCarloEngine.run(cfg, start, 200_000, 100, seed = 42)
        val cdf = ProbabilityEngine.cdf(cfg, start)
        val v = MonteCarloEngine.validate(mc, cdf)
        assertTrue("maxDiff=${v.maxAbsDiff}", v.ok)
        assertEquals(cdf[100], mc.successWithinBudget!!, 0.01)
        assertEquals(ProbabilityEngine.expectedPulls(cdf), mc.mean, 0.5)
        assertEquals(0L, mc.failures)
    }

    @Test fun chiSquareKnownValue() {
        assertEquals(0.05, 1 - StatsEngine.regGammaP(0.5, 3.841459 / 2), 1e-4)
        assertNull(StatsEngine.chiSquare(listOf(1, 9), listOf(0.1, 0.9)))
        val r = StatsEngine.chiSquare(listOf(60, 40), listOf(0.5, 0.5))!!
        assertEquals(4.0, r.statistic, 1e-9)
        assertEquals(0.0455, r.pValue, 1e-3)
    }

    @Test fun wilsonContainsEstimate() {
        val (lo, hi) = StatsEngine.wilson(5, 100)
        assertTrue(lo < 0.05 && hi > 0.05 && abs(lo - 0.0215) < 0.002)
    }

    @Test fun recommendations() {
        val cfg = GachaConfig(0.01, 1.0, hardPity = 10)
        fun rec(avail: Int, owned: Boolean = false, days: Int? = 5, income: Double = 0.0, pity: Int = 0) =
            RecommendationEngine.recommend(RecInput(cfg, PlayerState(pity), avail, days, income, owned, true)).action
        assertEquals(Action.PULL_NOW, rec(10))
        assertEquals(Action.SAVE_RESOURCE, rec(0))
        assertEquals(Action.STOP_PULLING, rec(10, owned = true))
        assertEquals(Action.WAIT_FOR_GUARANTEE, rec(3, income = 2.0))
        assertEquals(Action.SAVE_RESOURCE, rec(3, income = 0.0))
        assertEquals(Action.CONFIG_REQUIRED, RecommendationEngine.recommend(RecInput(null, PlayerState(), 5, 5, 0.0, false, true)).action)
    }
}
