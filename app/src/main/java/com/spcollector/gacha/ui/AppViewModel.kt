package com.spcollector.gacha.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.spcollector.gacha.data.*
import com.spcollector.gacha.engine.*
import com.spcollector.gacha.ocr.MlKitScanner
import com.spcollector.gacha.ocr.ScreenshotParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Draft(
    val key: Int, val uri: String, val imageHash: String, val lineIdx: Int,
    val name: String, val rarity: String, val pullNo: String,
    val confidence: Float, val unknown: Boolean, val raw: String, val include: Boolean = true,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = Room.databaseBuilder(app, AppDb::class.java, "spc.db").build().dao()
    private val scanner = MlKitScanner(app)
    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val characters = dao.characters().state(emptyList())
    val banners = dao.banners().state(emptyList())
    val pulls = dao.pulls().state(emptyList())
    val resources = dao.resources().map { it ?: ResourcesEntity() }.state(ResourcesEntity())
    val settings = dao.settings().map { it ?: SettingsEntity() }.state(SettingsEntity())
    val config = settings.map { it.toConfig() }.state(null)

    private val ctx = combine(banners, pulls, characters) { b, p, c -> Triple(b, p, c) }
    val recommendation = combine(config, resources, settings, ctx) { cfg, r, s, t ->
        val (b, p, c) = t
        val active = b.firstOrNull { it.bannerStatus == "ACTIVE" }
        val days = active?.endDate?.let {
            ChronoUnit.DAYS.between(LocalDate.now(), Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()).toInt()
        }
        val targets = c.filter { it.isTarget }.map { it.characterId }.toSet()
        RecommendationEngine.recommend(
            RecInput(cfg, PlayerState(r.currentPity, r.guaranteedStatus), availablePulls(r, s), days,
                s.dailyIncomePulls, p.any { it.characterId in targets }, active != null || b.isEmpty()))
    }.state(Recommendation(Action.CONFIG_REQUIRED, emptyList()))

    // ---- scanner ----
    val drafts = MutableStateFlow<List<Draft>>(emptyList())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")

    fun scan(uris: List<Uri>) {
        viewModelScope.launch {
            busy.value = true
            val known = characters.value.associate { it.characterName to it.rarity }
            val out = drafts.value.toMutableList()
            val seen = out.map { it.imageHash }.toMutableSet()
            var key = (out.maxOfOrNull { it.key } ?: 0) + 1
            val notes = mutableListOf<String>()
            for (u in uris) {
                try {
                    val hash = withContext(Dispatchers.IO) { scanner.sha256(u) }
                    if (hash in seen || dao.hasScreenshot(hash) > 0) { notes += "Screenshot duplikat dilewati."; continue }
                    seen += hash
                    val parsed = ScreenshotParser.parse(scanner.scan(u), known)
                    if (parsed.isEmpty()) notes += "Tidak ada baris gacha terdeteksi di salah satu screenshot."
                    parsed.forEachIndexed { i, p ->
                        out += Draft(key++, u.toString(), hash, i, p.characterName ?: "", p.rarity ?: "",
                            p.pullNumber?.toString() ?: "", p.confidence, p.unknown, p.rawText)
                    }
                } catch (e: Exception) { notes += "Gagal membaca screenshot: ${e.message}" }
            }
            drafts.value = out
            message.value = (listOf("${out.size} baris siap diverifikasi.") + notes).joinToString("\n")
            busy.value = false
        }
    }

    fun updateDraft(key: Int, f: (Draft) -> Draft) { drafts.value = drafts.value.map { if (it.key == key) f(it) else it } }
    fun clearDrafts() { drafts.value = emptyList() }

    fun confirm(bannerId: Long?) {
        viewModelScope.launch {
            val sel = drafts.value.filter { it.include }
            val bad = sel.count { it.name.isBlank() || it.rarity.isBlank() }
            if (bad > 0) { message.value = "$bad baris belum lengkap. Nama dan rarity wajib diisi atau baris dikeluarkan."; return@launch }
            var added = 0; var dup = 0
            for (d in sel) {
                val nm = d.name.trim(); val rar = d.rarity.trim().uppercase()
                val cid = dao.findCharacter(nm)?.characterId ?: dao.insertCharacter(CharacterEntity(characterName = nm, rarity = rar))
                val pn = d.pullNo.toIntOrNull()
                val dedup = if (pn != null) "${bannerId ?: 0}|$pn|${nm.lowercase()}|$rar" else "${d.imageHash}|${d.lineIdx}"
                val id = dao.insertPull(PullEntity(characterId = cid, bannerId = bannerId, pullNumber = pn, pullDate = null,
                    rarity = rar, characterNameRaw = nm, screenshotSource = d.imageHash, ocrConfidence = d.confidence,
                    verifiedByUser = true, dedupKey = dedup))
                if (id == -1L) dup++ else added++
            }
            drafts.value = emptyList()
            message.value = "$added pull disimpan, $dup duplikat dilewati."
        }
    }

    // ---- CRUD ----
    fun deletePull(id: Long) = viewModelScope.launch { dao.deletePull(id) }
    fun saveSettings(s: SettingsEntity) = viewModelScope.launch { dao.saveSettings(s) }
    fun saveResources(r: ResourcesEntity) = viewModelScope.launch { dao.saveResources(r) }
    fun addCharacter(name: String, rarity: String, target: Boolean) = viewModelScope.launch {
        if (name.isNotBlank() && dao.findCharacter(name.trim()) == null)
            dao.insertCharacter(CharacterEntity(characterName = name.trim(), rarity = rarity.trim().uppercase(), isTarget = target))
    }
    fun toggleTarget(c: CharacterEntity) = viewModelScope.launch { dao.updateCharacter(c.copy(isTarget = !c.isTarget)) }
    fun addBanner(name: String, endDate: String, rules: String) = viewModelScope.launch {
        val end = runCatching { LocalDate.parse(endDate.trim()).atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        dao.insertBanner(BannerEntity(bannerName = name.trim(), endDate = end, pityRules = rules,
            targetCharacter = characters.value.firstOrNull { it.isTarget }?.characterId))
    }
    fun toggleBanner(b: BannerEntity) = viewModelScope.launch {
        dao.updateBanner(b.copy(bannerStatus = if (b.bannerStatus == "ACTIVE") "ENDED" else "ACTIVE"))
    }
    /** Ganti banner: pity & guarantee di-reset kecuali carry-over aktif. */
    fun switchBanner() = viewModelScope.launch {
        if (!settings.value.pityCarryOver) dao.saveResources(resources.value.copy(currentPity = 0, guaranteedStatus = false))
    }

    // ---- Monte Carlo ----
    val mcResult = MutableStateFlow<McResult?>(null)
    val mcCdf = MutableStateFlow<DoubleArray?>(null)
    val mcValidation = MutableStateFlow<McValidation?>(null)
    val mcProgress = MutableStateFlow(0f)
    val mcRunning = MutableStateFlow(false)
    private var mcJob: Job? = null

    fun runMc(sims: Int, budget: Int?, scenario: Scenario) {
        val base = config.value ?: return
        val r = resources.value
        mcJob?.cancel()
        mcJob = viewModelScope.launch {
            mcRunning.value = true; mcResult.value = null; mcProgress.value = 0f
            try {
                val cfg = scenario.apply(base)
                val start = scenario.start(PlayerState(r.currentPity, r.guaranteedStatus))
                val res = MonteCarloEngine.run(cfg, start, sims, budget) { mcProgress.value = it }
                val cdf = withContext(Dispatchers.Default) { ProbabilityEngine.cdf(cfg, start) }
                mcCdf.value = cdf; mcValidation.value = MonteCarloEngine.validate(res, cdf); mcResult.value = res
            } finally { mcRunning.value = false }
        }
    }
    fun cancelMc() { mcJob?.cancel() }
}
