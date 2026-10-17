package com.nihongodoushi.app.storage

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * 本地 JSON 文件存储：每个 StorageKey 对应 filesDir 下一个 .json 文件。
 * 读取采用防御性校验（与 Web 端一致）：解析失败或结构非法时返回空记录并置 invalid 标记，
 * 由调用方决定提示文案；写入失败静默返回 false（与 Web 端 storageAvailable 降级语义一致）。
 */
class LocalStore(context: Context) {

    private val dir: File = context.filesDir
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 最近一次读取是否遇到无法解析的数据（用于 UI 提示） */
    var lastLoadInvalid: Boolean = false
        private set

    fun loadMistakes(): List<MistakeRecord> {
        val raw = readRaw(StorageKeys.MISTAKES) ?: return emptyList()
        return try {
            val records = json.decodeFromString<List<MistakeRecord>>(raw)
            records.filter { it.key.isNotBlank() && it.isValid() }
        } catch (error: Exception) {
            lastLoadInvalid = true
            emptyList()
        }
    }

    fun saveMistakes(records: List<MistakeRecord>): Boolean =
        writeRaw(StorageKeys.MISTAKES, json.encodeToString(records))

    fun loadVerbStats(): ModuleStats = loadStats(StorageKeys.VERB_STATS)

    fun saveVerbStats(stats: ModuleStats): Boolean = saveStats(StorageKeys.VERB_STATS, stats)

    fun loadAdjectiveStats(): ModuleStats = loadStats(StorageKeys.ADJECTIVE_STATS)

    fun saveAdjectiveStats(stats: ModuleStats): Boolean = saveStats(StorageKeys.ADJECTIVE_STATS, stats)

    /**
     * 助词错题（v2）。缺失 v2 时把 v1 的纯 ID 数组迁移成记录：
     * `dueAt = 现在`（立即可复习）、`count = 1`、`streak = 0`，迁移后写回 v2，v1 原样保留作备份。
     */
    fun loadParticleMistakes(): List<ParticleMistakeRecord> {
        val rawV2 = readRaw(StorageKeys.PARTICLE_MISTAKES)
        if (rawV2 != null) {
            return try {
                json.decodeFromString<List<ParticleMistakeRecord>>(rawV2)
                    .filter {
                        it.questionId.isNotBlank() && it.count >= 1 &&
                            it.correctStreak >= 0 && it.intervalDays >= 1
                    }
                    .distinctBy { it.questionId }
            } catch (error: Exception) {
                lastLoadInvalid = true
                emptyList()
            }
        }

        val rawV1 = readRaw(StorageKeys.PARTICLE_MISTAKES_LEGACY) ?: return emptyList()
        val legacyIds = try {
            json.decodeFromString<List<String>>(rawV1)
        } catch (error: Exception) {
            lastLoadInvalid = true
            return emptyList()
        }
        val now = Instant.now().toString()
        val migrated = legacyIds
            .filter { it.isNotBlank() }
            .distinct()
            .map { ParticleMistakeRecord(questionId = it, updatedAt = now, dueAt = now) }
        if (migrated.isNotEmpty()) {
            saveParticleMistakes(migrated)
        }
        return migrated
    }

    fun saveParticleMistakes(records: List<ParticleMistakeRecord>): Boolean =
        writeRaw(StorageKeys.PARTICLE_MISTAKES, json.encodeToString(records.distinctBy { it.questionId }))

    fun loadParticleStats(): ModuleStats = loadStats(StorageKeys.PARTICLE_STATS)

    fun saveParticleStats(stats: ModuleStats): Boolean = saveStats(StorageKeys.PARTICLE_STATS, stats)

    fun loadDaily(): DailyRecord {
        val raw = readRaw(StorageKeys.DAILY) ?: return DailyRecord()
        return try {
            val record = json.decodeFromString<DailyRecord>(raw)
            if (record.isValid()) record else DailyRecord()
        } catch (error: Exception) {
            lastLoadInvalid = true
            DailyRecord()
        }
    }

    fun saveDaily(record: DailyRecord): Boolean =
        writeRaw(StorageKeys.DAILY, json.encodeToString(record))

    fun loadHistory(): ActivityHistory {
        val raw = readRaw(StorageKeys.HISTORY) ?: return ActivityHistory()
        return try {
            val history = json.decodeFromString<ActivityHistory>(raw)
            ActivityHistory(
                days = history.days
                    .filter {
                        it.date.isNotBlank() && it.verbAnswers >= 0 && it.adjectiveAnswers >= 0 &&
                            it.particleAnswers >= 0 && it.seconds >= 0 && it.tasks >= 0
                    }
                    .sortedBy { it.date },
            )
        } catch (error: Exception) {
            lastLoadInvalid = true
            ActivityHistory()
        }
    }

    fun saveHistory(history: ActivityHistory): Boolean =
        writeRaw(StorageKeys.HISTORY, json.encodeToString(history))

    fun loadProgress(): PracticeProgress {
        val raw = readRaw(StorageKeys.PRACTICE) ?: return PracticeProgress()
        return try {
            val progress = json.decodeFromString<PracticeProgress>(raw)
            PracticeProgress(
                verbIds = progress.verbIds.filter { it.isNotBlank() }.distinct(),
                adjectiveIds = progress.adjectiveIds.filter { it.isNotBlank() }.distinct(),
                masteredMistakes = if (progress.masteredMistakes >= 0) progress.masteredMistakes else 0,
                particleMastered = progress.particleMastered.filter { it.isNotBlank() }.distinct(),
            )
        } catch (error: Exception) {
            lastLoadInvalid = true
            PracticeProgress()
        }
    }

    fun saveProgress(progress: PracticeProgress): Boolean =
        writeRaw(StorageKeys.PRACTICE, json.encodeToString(progress))

    private fun loadStats(key: String): ModuleStats {
        val raw = readRaw(key) ?: return ModuleStats()
        return try {
            val stats = json.decodeFromString<ModuleStats>(raw)
            if (stats.correct >= 0 && stats.incorrect >= 0) stats else ModuleStats()
        } catch (error: Exception) {
            lastLoadInvalid = true
            ModuleStats()
        }
    }

    private fun saveStats(key: String, stats: ModuleStats): Boolean =
        writeRaw(key, json.encodeToString(stats))

    private fun readRaw(key: String): String? {
        val file = File(dir, "$key.json")
        if (!file.exists()) {
            return null
        }
        return try {
            file.readText()
        } catch (error: Exception) {
            lastLoadInvalid = true
            null
        }
    }

    private fun writeRaw(key: String, content: String): Boolean {
        return try {
            val file = File(dir, "$key.json")
            val tmp = File(dir, "$key.json.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(file)) {
                // rename 在个别文件系统上可能失败，退回直接写入
                file.writeText(content)
                tmp.delete()
            }
            true
        } catch (error: Exception) {
            false
        }
    }
}

/** 结构合法性校验（对齐 Web 端读取时的字段校验语义） */
private fun MistakeRecord.isValid(): Boolean =
    count >= 0 && correctStreak >= 0 && intervalDays >= 1 &&
        if (isAdjective) {
            !adjectiveId.isNullOrBlank() && !formId.isNullOrBlank()
        } else {
            !verbId.isNullOrBlank() && !voiceId.isNullOrBlank() && !inflectionId.isNullOrBlank()
        }

private fun DailyRecord.isValid(): Boolean =
    streak >= 0 && best >= 0 && verbAnswers >= 0 && adjectiveAnswers >= 0 && particleAnswers >= 0
