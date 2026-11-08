package com.spcollector.gacha.data

import androidx.room.*
import com.spcollector.gacha.engine.GachaConfig
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "Character")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val characterId: Long = 0,
    val characterName: String, val rarity: String, val isTarget: Boolean = false,
)

@Entity(tableName = "Banner")
data class BannerEntity(
    @PrimaryKey(autoGenerate = true) val bannerId: Long = 0,
    val bannerName: String, val startDate: Long? = null, val endDate: Long? = null,
    val targetCharacter: Long? = null, val publishedDropRate: Double? = null,
    val pityRules: String = "", val bannerStatus: String = "ACTIVE",
)

@Entity(tableName = "PullHistory", indices = [Index(value = ["dedupKey"], unique = true)])
data class PullEntity(
    @PrimaryKey(autoGenerate = true) val pullId: Long = 0,
    val characterId: Long?, val bannerId: Long?, val pullNumber: Int?, val pullDate: Long?,
    val rarity: String, val characterNameRaw: String, val screenshotSource: String?,
    val ocrConfidence: Float, val verifiedByUser: Boolean, val dedupKey: String,
)

@Entity(tableName = "PlayerResources")
data class ResourcesEntity(
    @PrimaryKey val id: Int = 1, val diamonds: Int = 0, val tickets: Int = 0,
    val currentPity: Int = 0, val guaranteedStatus: Boolean = false,
)

/** Semua nilai nullable: kosong = belum diketahui. Tidak ada default fiktif. */
@Entity(tableName = "ProbabilitySettings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val baseDropRate: Double? = null,       // P(rarity SP) per pull
    val featuredRate: Double? = null,       // P(target | SP) saat belum guaranteed
    val softPity: Int? = null, val softPityIncrement: Double? = null,
    val hardPity: Int? = null, val pityCarryOver: Boolean = false,
    val guaranteeEnabled: Boolean = false,
    val diamondsPerPull: Int? = null, val ticketsPerPull: Int = 1,
    val dailyIncomePulls: Double = 0.0,
)

fun SettingsEntity.toConfig(): GachaConfig? {
    val b = baseDropRate ?: return null
    val f = featuredRate ?: return null
    return GachaConfig(b, f, guaranteeEnabled, softPity, softPityIncrement ?: 0.0, hardPity).takeIf { it.problems().isEmpty() }
}

fun SettingsEntity.problems(): List<String> {
    val b = baseDropRate; val f = featuredRate
    if (b == null || f == null) return buildList {
        if (b == null) add("Drop rate rarity SP belum diisi")
        if (f == null) add("Peluang target (featured rate) belum diisi")
    }
    return GachaConfig(b, f, guaranteeEnabled, softPity, softPityIncrement ?: 0.0, hardPity).problems()
}

fun availablePulls(r: ResourcesEntity, s: SettingsEntity): Int {
    val t = if (s.ticketsPerPull > 0) r.tickets / s.ticketsPerPull else 0
    val d = s.diamondsPerPull?.takeIf { it > 0 }?.let { r.diamonds / it } ?: 0
    return t + d
}

@Dao
interface AppDao {
    @Query("SELECT * FROM Character ORDER BY characterName") fun characters(): Flow<List<CharacterEntity>>
    @Query("SELECT * FROM Character WHERE characterName = :n COLLATE NOCASE LIMIT 1") suspend fun findCharacter(n: String): CharacterEntity?
    @Insert suspend fun insertCharacter(c: CharacterEntity): Long
    @Update suspend fun updateCharacter(c: CharacterEntity)

    @Query("SELECT * FROM Banner ORDER BY bannerId DESC") fun banners(): Flow<List<BannerEntity>>
    @Insert suspend fun insertBanner(b: BannerEntity): Long
    @Update suspend fun updateBanner(b: BannerEntity)

    @Query("SELECT * FROM PullHistory ORDER BY pullId DESC") fun pulls(): Flow<List<PullEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPull(p: PullEntity): Long
    @Query("DELETE FROM PullHistory WHERE pullId = :id") suspend fun deletePull(id: Long)
    @Query("SELECT COUNT(*) FROM PullHistory WHERE screenshotSource = :h") suspend fun hasScreenshot(h: String): Int

    @Query("SELECT * FROM PlayerResources WHERE id = 1") fun resources(): Flow<ResourcesEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveResources(r: ResourcesEntity)
    @Query("SELECT * FROM ProbabilitySettings WHERE id = 1") fun settings(): Flow<SettingsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(s: SettingsEntity)
}

@Database(
    entities = [CharacterEntity::class, BannerEntity::class, PullEntity::class, ResourcesEntity::class, SettingsEntity::class],
    version = 1, exportSchema = false,
)
abstract class AppDb : RoomDatabase() { abstract fun dao(): AppDao }
