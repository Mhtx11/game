package com.spcollector.gacha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.spcollector.gacha.data.*
import com.spcollector.gacha.engine.*
import kotlinx.coroutines.delay
import java.util.Locale

private fun pctIn(f: Double?): String = f?.let { String.format(Locale.US, "%.4f", it * 100).trimEnd('0').trimEnd('.') } ?: ""
private fun String.toFrac(): Double? = trim().replace(',', '.').toDoubleOrNull()?.div(100)
private fun String.toD(): Double? = trim().replace(',', '.').toDoubleOrNull()
private fun String.toI(): Int? = trim().toIntOrNull()

@Composable
fun SplashScreen(nav: NavController) {
    LaunchedEffect(Unit) { delay(1200); nav.navigate("dashboard") { popUpTo("splash") { inclusive = true } } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SP COLLECTOR", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Crimson)
            Text("GACHA OPTIMIZER", fontSize = 20.sp, color = Orange)
        }
    }
}

@Composable
fun DashboardScreen(vm: AppViewModel, nav: NavController) {
    val cfg by vm.config.collectAsState(); val r by vm.resources.collectAsState(); val s by vm.settings.collectAsState()
    val chars by vm.characters.collectAsState(); val pulls by vm.pulls.collectAsState(); val rec by vm.recommendation.collectAsState()
    val avail = availablePulls(r, s)
    val est = remember(cfg, r.currentPity, r.guaranteedStatus, avail) {
        cfg?.let { ProbabilityEngine.cdf(it, PlayerState(r.currentPity, r.guaranteedStatus))[minOf(avail, it.horizon)] }
    }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dashboard", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Crimson)
        Panel("Target SP Collector") {
            val t = chars.filter { it.isTarget }
            Text(if (t.isEmpty()) "Belum ada target (atur di Tracker)" else t.joinToString { it.characterName })
        }
        Panel("Status") {
            Text("Current pity: ${r.currentPity}   Guarantee: ${if (r.guaranteedStatus) "ya" else "tidak"}")
            Text("Tiket: ${r.tickets}   Diamond: ${r.diamonds}   Pull tersedia: $avail")
            Text("Estimasi peluang target: ${est?.let { pct(it) } ?: "CONFIGURATION REQUIRED"}")
            Text("Total riwayat pull: ${pulls.size}")
        }
        Panel("Rekomendasi terakhir") {
            Text(rec.action.name, color = Orange, fontWeight = FontWeight.Bold)
            rec.reasons.firstOrNull()?.let { Text(it) }
        }
        listOf(
            "scanner" to "Screenshot Scanner", "history" to "Gacha History", "calculator" to "Probability Calculator",
            "montecarlo" to "Monte Carlo Simulator", "rng" to "RNG Analyzer", "tracker" to "SP Collector Tracker",
            "recommendation" to "Smart Recommendation", "resources" to "Resource Management", "settings" to "Settings",
        ).forEach { (route, label) -> BigButton(label) { nav.navigate(route) } }
    }
}

@Composable
fun ScannerScreen(vm: AppViewModel, nav: NavController) {
    val busy by vm.busy.collectAsState(); val msg by vm.message.collectAsState(); val drafts by vm.drafts.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { vm.scan(it) }
    Page("Screenshot Scanner", nav) {
        Panel("Impor riwayat gacha") {
            Text("Pilih satu atau beberapa screenshot. OCR berjalan di perangkat (offline). Hasil OCR tidak dianggap benar sebelum kamu memeriksanya.")
            BigButton(if (busy) "Memproses..." else "Pilih screenshot dari galeri", enabled = !busy) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (msg.isNotBlank()) Text(msg)
        if (drafts.isNotEmpty()) BigButton("Verifikasi ${drafts.size} baris") { nav.navigate("verify") }
    }
}

@Composable
fun VerifyScreen(vm: AppViewModel, nav: NavController) {
    val drafts by vm.drafts.collectAsState(); val banners by vm.banners.collectAsState(); val msg by vm.message.collectAsState()
    var bannerId by remember { mutableStateOf<Long?>(null) }
    Page("OCR Verification", nav, scroll = false) {
        if (banners.isNotEmpty()) {
            Text("Banner untuk impor ini:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                banners.take(3).forEach { b -> FilterChip(bannerId == b.bannerId, { bannerId = b.bannerId }, label = { Text(b.bannerName) }) }
            }
        }
        if (msg.isNotBlank()) Text(msg, color = Orange)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(drafts, key = { it.key }) { d ->
                Panel {
                    if (drafts.indexOfFirst { it.imageHash == d.imageHash } == drafts.indexOf(d)) Preview(d.uri)
                    Text("OCR: ${d.raw}", fontSize = 12.sp)
                    Text(if (d.unknown) "UNKNOWN: isi nama manual" else "Confidence ${pct(d.confidence.toDouble())}", color = if (d.unknown) Crimson else Orange)
                    Field("Nama karakter", d.name, { v -> vm.updateDraft(d.key) { it.copy(name = v) } })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Rarity", d.rarity, { v -> vm.updateDraft(d.key) { it.copy(rarity = v) } }, modifier = Modifier.weight(1f))
                        Field("No. pull", d.pullNo, { v -> vm.updateDraft(d.key) { it.copy(pullNo = v) } }, number = true, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d.include, { v -> vm.updateDraft(d.key) { it.copy(include = v) } }); Text("Sertakan")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.clearDrafts(); nav.popBackStack() }, Modifier.weight(1f)) { Text("Buang") }
            Button({ vm.confirm(bannerId) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Crimson)) { Text("Konfirmasi & simpan") }
        }
    }
}

@Composable
fun HistoryScreen(vm: AppViewModel, nav: NavController) {
    val pulls by vm.pulls.collectAsState()
    Page("Gacha History", nav, scroll = false) {
        Text("${pulls.size} pull tersimpan")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pulls, key = { it.pullId }) { p ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.characterNameRaw}  [${p.rarity}]", fontWeight = FontWeight.Bold)
                            Text("No. pull: ${p.pullNumber ?: "tidak diketahui"}  OCR: ${pct(p.ocrConfidence.toDouble())}  " + if (p.verifiedByUser) "terverifikasi" else "belum", fontSize = 12.sp)
                        }
                        TextButton({ vm.deletePull(p.pullId) }) { Text("Hapus", color = Crimson) }
                    }
                }
            }
        }
    }
}

@Composable
fun CalcScreen(vm: AppViewModel, nav: NavController) {
    val cfg by vm.config.collectAsState(); val r by vm.resources.collectAsState(); val s by vm.settings.collectAsState()
    val pulls by vm.pulls.collectAsState()
    var custom by remember { mutableStateOf("300") }
    Page("Probability Calculator", nav) {
        val c = cfg
        if (c == null) { ConfigNeeded(s.problems(), nav); return@Page }
        val st = PlayerState(r.currentPity, r.guaranteedStatus)
        val cdf = remember(c, st) { ProbabilityEngine.cdf(c, st) }
        val counts = listOf(10, 20, 50, 100, 150, 200) + listOfNotNull(custom.toI()?.takeIf { it in 1..c.horizon })
        Panel("Peluang mendapat target (model Markov, dengan pity/guarantee)") {
            counts.forEach { n -> Text("$n pull  →  sukses ${pct(cdf[minOf(n, c.horizon)])}   gagal ${pct(1 - cdf[minOf(n, c.horizon)])}") }
            Field("Custom pulls", custom, { custom = it }, number = true)
            CdfChart(listOf(cdf to Orange), counts.max())
        }
        Panel("Ringkasan") {
            val e = ProbabilityEngine.expectedPulls(cdf)
            Text("Expected pulls: ${"%.1f".format(e)}" + if (ProbabilityEngine.residual(cdf) > 1e-6) " (terpotong, residual ${pct(ProbabilityEngine.residual(cdf))})" else "")
            Text("Persentil 90: ${ProbabilityEngine.percentile(cdf, 0.9) ?: ">${c.horizon}"}   95: ${ProbabilityEngine.percentile(cdf, 0.95) ?: ">${c.horizon}"}")
            Text("Per pull tanpa pity (referensi): p = ${pct(c.topRate * c.featuredRate)}  →  1-(1-p)^n untuk 100 pull = ${pct(ProbabilityEngine.independent(c.topRate * c.featuredRate, 100))}")
        }
        Panel("Resource") {
            val worst = ProbabilityEngine.worstCasePulls(c, st)
            val dpp = s.diamondsPerPull
            Text("Minimum: 1 pull (${if (dpp != null) "$dpp diamond atau " else ""}${s.ticketsPerPull} tiket)")
            Text(if (worst == null) "Maksimum: tidak terbatas (tidak ada hard pity yang menjamin target)"
            else "Maksimum (hard pity): $worst pull" + (dpp?.let { " ≈ ${worst * it} diamond" } ?: ""))
            Text("Pull tersedia: ${availablePulls(r, s)}")
        }
        Panel("Tingkat keyakinan data") {
            val v = pulls.count { it.verifiedByUser }
            Text("Parameter diisi manual oleh pengguna dan belum diverifikasi aplikasi terhadap sumber resmi. Riwayat terverifikasi: $v pull" + if (v < 100) " (sampel kecil, tidak cukup menguji drop rate)." else ".")
            Text("Peluang rarity SP tidak sama dengan peluang karakter target; keduanya dihitung terpisah.")
        }
    }
}

@Composable
fun MonteCarloScreen(vm: AppViewModel, nav: NavController) {
    val cfg by vm.config.collectAsState(); val s by vm.settings.collectAsState(); val r by vm.resources.collectAsState()
    val res by vm.mcResult.collectAsState(); val cdf by vm.mcCdf.collectAsState(); val val_ by vm.mcValidation.collectAsState()
    val prog by vm.mcProgress.collectAsState(); val running by vm.mcRunning.collectAsState()
    var sims by remember { mutableStateOf(100_000) }
    var scenario by remember { mutableStateOf(Scenario.CURRENT) }
    var budget by remember { mutableStateOf("") }
    Page("Monte Carlo Simulator", nav) {
        if (cfg == null) { ConfigNeeded(s.problems(), nav); return@Page }
        Panel("Pengaturan") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(10_000, 100_000, 500_000, 1_000_000).forEach { n -> FilterChip(sims == n, { sims = n }, label = { Text("%,d".format(n)) }) }
            }
            Scenario.values().forEach { sc ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(scenario == sc, { scenario = sc }); Text(sc.label, fontSize = 13.sp) }
            }
            Field("Budget pull (kosongkan = pull tersedia: ${availablePulls(r, s)})", budget, { budget = it }, number = true)
            if (running) { LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth()); OutlinedButton({ vm.cancelMc() }) { Text("Batal") } }
            else BigButton("Jalankan simulasi") { vm.runMc(sims, budget.toI() ?: availablePulls(r, s), scenario) }
        }
        res?.let { m ->
            val cost = s.diamondsPerPull
            Panel("Hasil (${"%,d".format(m.simulations)} simulasi)") {
                m.successWithinBudget?.let { Text("Success rate dalam ${m.budget} pull: ${pct(it)}   Failure rate: ${pct(1 - it)}") }
                Text("Simulasi berhasil: ${"%,d".format(m.successes)}   gagal melewati horizon: ${"%,d".format(m.failures)}")
                Text("Rata-rata pull: ${"%.2f".format(m.mean)}   Median: ${m.median ?: "-"}")
                Text("Persentil 90: ${m.p90 ?: "-"}   95: ${m.p95 ?: "-"}")
                if (cost != null) Text("Konsumsi diamond (rata-rata / P95): ${"%.0f".format(m.mean * cost)} / ${m.p95?.let { it * cost } ?: "-"}")
                cdf?.let { a -> Text("Analitik P(≤budget): ${pct(a[minOf(m.budget ?: 0, a.size - 1)])}") }
                val h = m.histogram; var cum = 0L
                val emp = DoubleArray(h.size) { i -> if (i > 0) cum += h[i]; cum.toDouble() / m.simulations }
                CdfChart(listOfNotNull(emp to Crimson, cdf?.let { it to Orange }), m.p95 ?: 100)
                Text("Merah: simulasi. Oranye: analitik.", fontSize = 11.sp)
                val_?.let { Text(if (it.ok) "Validasi vs analitik: LULUS (selisih maks ${"%.5f".format(it.maxAbsDiff)})" else "Validasi vs analitik: GAGAL (selisih maks ${"%.5f".format(it.maxAbsDiff)})", color = if (it.ok) Orange else Crimson) }
            }
        }
    }
}

@Composable
fun RngScreen(vm: AppViewModel, nav: NavController) {
    val pulls by vm.pulls.collectAsState(); val s by vm.settings.collectAsState()
    Page("RNG Analyzer", nav) {
        val v = pulls.filter { it.verifiedByUser }
        val n = v.size
        val by = v.groupingBy { it.rarity.uppercase() }.eachCount()
        Panel("Frekuensi rarity (n = $n)") {
            by.entries.sortedByDescending { it.value }.forEach { (k, c) ->
                val (lo, hi) = StatsEngine.wilson(c, n)
                Text("$k: $c (${pct(c.toDouble() / n)})  CI95% ${pct(lo)} – ${pct(hi)}")
            }
            if (n == 0) Text("INSUFFICIENT DATA")
        }
        Panel("Observasi vs drop rate resmi (rarity SP)") {
            val b = s.baseDropRate
            val sp = by["SP"] ?: 0
            if (b == null) Text("Drop rate belum diisi di Settings.")
            else {
                val (lo, hi) = StatsEngine.wilson(sp, n)
                Text("SP diamati: $sp dari $n   Rate resmi: ${pct(b)}")
                if (n > 0) Text(if (b in lo..hi) "Rate resmi berada dalam CI95% sampel." else "Rate resmi di luar CI95%. Dengan sampel kecil atau data terbias, ini belum bukti apa pun.")
                val chi = StatsEngine.chiSquare(listOf(sp, n - sp), listOf(b, 1 - b))
                if (chi == null) Text("Chi-square: INSUFFICIENT DATA (butuh n ≥ 30 dan expected count ≥ 5 tiap kategori).", color = Orange)
                else Text("Chi-square = ${"%.3f".format(chi.statistic)}, df = ${chi.df}, p = ${"%.4f".format(chi.pValue)}")
            }
        }
        Panel("Keterbatasan") {
            Text("• Tidak ada \"RNG panas/dingin\". Tanpa mekanisme pity, tiap pull independen dan kegagalan sebelumnya tidak menaikkan peluang berikutnya.")
            Text("• Jika screenshot hanya memuat hasil tertentu (misalnya hanya SSR ke atas), frekuensi di sini terbias dan tidak valid dibandingkan dengan drop rate.")
            Text("• Pull yang hilang atau screenshot yang tumpang tindih tanpa nomor pull dapat menggeser hitungan.")
        }
    }
}

@Composable
fun TrackerScreen(vm: AppViewModel, nav: NavController) {
    val chars by vm.characters.collectAsState(); val banners by vm.banners.collectAsState()
    var name by remember { mutableStateOf("") }; var rar by remember { mutableStateOf("SP") }; var tgt by remember { mutableStateOf(true) }
    var bn by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }; var rules by remember { mutableStateOf("") }
    Page("SP Collector Tracker", nav) {
        Panel("Tambah karakter") {
            Field("Nama", name, { name = it }); Field("Rarity", rar, { rar = it })
            SwitchRow("Jadikan target", tgt) { tgt = it }
            BigButton("Simpan karakter") { vm.addCharacter(name, rar, tgt); name = "" }
        }
        Panel("Karakter") {
            if (chars.isEmpty()) Text("Belum ada karakter.")
            chars.forEach { c -> SwitchRow("${c.characterName} [${c.rarity}]  target", c.isTarget) { vm.toggleTarget(c) } }
        }
        Panel("Tambah banner") {
            Field("Nama banner", bn, { bn = it }); Field("Tanggal berakhir (yyyy-MM-dd, opsional)", end, { end = it }); Field("Catatan aturan pity", rules, { rules = it })
            BigButton("Simpan banner") { if (bn.isNotBlank()) { vm.addBanner(bn, end, rules); bn = ""; end = ""; rules = "" } }
        }
        Panel("Banner") {
            banners.forEach { b -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.bannerName} [${b.bannerStatus}]", Modifier.weight(1f)); TextButton({ vm.toggleBanner(b) }) { Text("Ubah status") }
            } }
        }
    }
}

@Composable
fun RecommendationScreen(vm: AppViewModel, nav: NavController) {
    val rec by vm.recommendation.collectAsState(); val s by vm.settings.collectAsState()
    Page("Smart Recommendation", nav) {
        if (rec.action == Action.CONFIG_REQUIRED) ConfigNeeded(s.problems(), nav)
        else Panel(rec.action.name.replace('_', ' ')) { rec.reasons.forEach { Text("• $it") } }
    }
}

@Composable
fun ResourcesScreen(vm: AppViewModel, nav: NavController) {
    val r by vm.resources.collectAsState()
    var d by remember(r) { mutableStateOf(r.diamonds.toString()) }; var t by remember(r) { mutableStateOf(r.tickets.toString()) }
    var p by remember(r) { mutableStateOf(r.currentPity.toString()) }; var g by remember(r) { mutableStateOf(r.guaranteedStatus) }
    Page("Resource Management", nav) {
        Field("Diamond", d, { d = it }, true); Field("Tiket", t, { t = it }, true); Field("Current pity (pull sejak SP terakhir)", p, { p = it }, true)
        SwitchRow("Guaranteed aktif", g) { g = it }
        BigButton("Simpan") { vm.saveResources(ResourcesEntity(1, d.toI() ?: 0, t.toI() ?: 0, p.toI() ?: 0, g)) }
        OutlinedButton({ vm.switchBanner() }, Modifier.fillMaxWidth()) { Text("Ganti banner (reset pity kecuali carry-over aktif)") }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val s by vm.settings.collectAsState()
    var base by remember(s) { mutableStateOf(pctIn(s.baseDropRate)) }; var feat by remember(s) { mutableStateOf(pctIn(s.featuredRate)) }
    var soft by remember(s) { mutableStateOf(s.softPity?.toString() ?: "") }; var inc by remember(s) { mutableStateOf(pctIn(s.softPityIncrement)) }
    var hard by remember(s) { mutableStateOf(s.hardPity?.toString() ?: "") }
    var carry by remember(s) { mutableStateOf(s.pityCarryOver) }; var gua by remember(s) { mutableStateOf(s.guaranteeEnabled) }
    var dpp by remember(s) { mutableStateOf(s.diamondsPerPull?.toString() ?: "") }; var tpp by remember(s) { mutableStateOf(s.ticketsPerPull.toString()) }
    var inflow by remember(s) { mutableStateOf(s.dailyIncomePulls.toString()) }
    Page("Settings", nav) {
        Panel("Parameter gacha (isi dari informasi resmi game; kosong = belum diketahui)") {
            Field("Drop rate rarity SP per pull (%)", base, { base = it }, true)
            Field("Peluang karakter target saat SP, tanpa guarantee (%)  [100 jika selalu featured]", feat, { feat = it }, true)
            SwitchRow("Featured guarantee (setelah kalah, SP berikutnya pasti target)", gua) { gua = it }
            Field("Awal soft pity (pull ke-)", soft, { soft = it }, true)
            Field("Kenaikan rate per pull soft pity (%)", inc, { inc = it }, true)
            Field("Hard pity (pull ke-)", hard, { hard = it }, true)
            SwitchRow("Pity carry-over antar banner", carry) { carry = it }
        }
        Panel("Biaya & pemasukan") {
            Field("Diamond per pull", dpp, { dpp = it }, true); Field("Tiket per pull", tpp, { tpp = it }, true)
            Field("Perkiraan pull baru per hari (opsional)", inflow, { inflow = it }, true)
        }
        BigButton("Simpan") {
            vm.saveSettings(SettingsEntity(1, base.toFrac(), feat.toFrac(), soft.toI(), inc.toFrac(), hard.toI(), carry, gua, dpp.toI(), tpp.toI() ?: 1, inflow.toD() ?: 0.0))
        }
        Text("Konfigurasi: " + (s.problems().takeIf { it.isNotEmpty() }?.joinToString("; ") ?: "lengkap"), color = Orange)
    }
}
