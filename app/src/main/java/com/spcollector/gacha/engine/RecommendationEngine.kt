package com.spcollector.gacha.engine

enum class Action { PULL_NOW, SAVE_RESOURCE, WAIT_FOR_GUARANTEE, STOP_PULLING, CONFIG_REQUIRED }

data class Recommendation(val action: Action, val reasons: List<String>)

data class RecInput(
    val cfg: GachaConfig?, val state: PlayerState, val availablePulls: Int,
    val daysLeft: Int?, val dailyIncomePulls: Double,
    val targetOwned: Boolean, val bannerActive: Boolean,
)

object RecommendationEngine {
    private fun f(x: Double) = "%.1f%%".format(x * 100)

    fun recommend(i: RecInput): Recommendation {
        val cfg = i.cfg ?: return Recommendation(Action.CONFIG_REQUIRED, listOf("Parameter gacha belum lengkap. Isi di Settings; aplikasi tidak menebak angka."))
        val note = "Rekomendasi hanya memakai pity, status guarantee, resource, dan sisa durasi. Hasil pull sebelumnya tidak mempengaruhi peluang."
        if (i.targetOwned) return Recommendation(Action.STOP_PULLING, listOf("Karakter target sudah ada di riwayat.", note))
        if (!i.bannerActive || (i.daysLeft != null && i.daysLeft < 0)) return Recommendation(Action.STOP_PULLING, listOf("Banner sudah berakhir atau tidak aktif.", note))
        val cdf = ProbabilityEngine.cdf(cfg, i.state)
        val avail = i.availablePulls.coerceAtLeast(0)
        val pAvail = cdf[minOf(avail, cdf.size - 1)]
        val worst = ProbabilityEngine.worstCasePulls(cfg, i.state)
        val reasons = mutableListOf(
            "Pity ${i.state.pity}, guarantee: ${if (i.state.guaranteed) "aktif" else "tidak aktif"}.",
            "Pull tersedia: $avail. Peluang target dengan resource itu: ${f(pAvail)}.",
        )
        if (worst != null) reasons += "Pull maksimum sampai target pasti didapat: $worst."
        else reasons += "Tidak ada batas pasti (hard pity/guarantee tidak mencakup target)."
        reasons += note
        if (avail == 0) { reasons.add(0, "Tidak ada resource untuk pull."); return Recommendation(Action.SAVE_RESOURCE, reasons) }
        if (worst != null && avail >= worst) { reasons.add(0, "Resource cukup untuk menjamin target."); return Recommendation(Action.PULL_NOW, reasons) }
        if (pAvail >= 0.9) { reasons.add(0, "Peluang sangat tinggi dengan resource saat ini."); return Recommendation(Action.PULL_NOW, reasons) }
        val projected = avail + i.dailyIncomePulls * (i.daysLeft ?: 0)
        if (worst != null && i.daysLeft != null && projected >= worst) {
            reasons.add(0, "Dengan pemasukan harian yang kamu isi, resource mencapai jaminan sebelum banner berakhir (~${"%.0f".format(projected)} pull).")
            return Recommendation(Action.WAIT_FOR_GUARANTEE, reasons)
        }
        if (pAvail >= 0.5) { reasons.add(0, "Peluang cukup tinggi dengan resource saat ini."); return Recommendation(Action.PULL_NOW, reasons) }
        reasons.add(0, "Peluang rendah dan jaminan belum terjangkau.")
        return Recommendation(Action.SAVE_RESOURCE, reasons)
    }
}
