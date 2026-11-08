package com.spcollector.gacha.ocr

import kotlin.math.max
import kotlin.math.min

data class OcrLine(val text: String, val confidence: Float)
data class DraftPull(
    val rawText: String, val characterName: String?, val rarity: String?,
    val pullNumber: Int?, val confidence: Float, val unknown: Boolean,
)

object ScreenshotParser {
    private val numRe = Regex("""^\s*(?:#|No\.?\s*)(\d{1,4})\b""", RegexOption.IGNORE_CASE)

    /** known = nama karakter -> rarity. Urutan pull hanya diisi bila ada penanda eksplisit (#12 / No.12). */
    fun parse(
        lines: List<OcrLine>, known: Map<String, String>,
        rarityTokens: List<String> = listOf("SSR", "SR", "SP", "UR", "R"),
        minSimilarity: Double = 0.8,
    ): List<DraftPull> {
        val rarRe = Regex("""\b(${rarityTokens.joinToString("|") { Regex.escape(it) }})\b""", RegexOption.IGNORE_CASE)
        val out = mutableListOf<DraftPull>()
        for (line in lines) {
            val raw = line.text.trim()
            if (raw.length < 2) continue
            val pn = numRe.find(raw)?.groupValues?.get(1)?.toIntOrNull()
            val rar = rarRe.find(raw)?.value?.uppercase()
            val cleaned = raw.replace(numRe, "").replace(rarRe, "")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
            var best: String? = null
            var sim = 0.0
            if (cleaned.isNotEmpty()) for (name in known.keys) {
                val s = if (cleaned.contains(name, ignoreCase = true)) 1.0 else similarity(cleaned, name)
                if (s > sim) { sim = s; best = name }
            }
            if (best != null && sim >= minSimilarity) {
                out += DraftPull(raw, best, rar ?: known[best], pn, (line.confidence * sim).toFloat(), false)
            } else if (rar != null || pn != null) {
                out += DraftPull(raw, null, rar, pn, min(line.confidence, 0.4f), true)
            }
        }
        return out
    }

    fun similarity(a: String, b: String): Double {
        val x = a.lowercase(); val y = b.lowercase()
        if (x.isEmpty() && y.isEmpty()) return 1.0
        val d = IntArray(y.length + 1) { it }
        for (i in 1..x.length) {
            var prev = d[0]; d[0] = i
            for (j in 1..y.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (x[i - 1] == y[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return 1.0 - d[y.length].toDouble() / max(x.length, y.length)
    }
}
