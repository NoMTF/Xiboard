/*
 * SPDX-FileCopyrightText: 2026 Xiboard contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.correction

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.xiboard.core.CandidateItem
import com.xiboard.core.CompositionProto
import com.xiboard.core.MenuProto
import com.xiboard.core.RimeMessage
import com.xiboard.data.base.DataManager
import com.xiboard.ime.broadcast.InputBroadcastReceiver
import com.xiboard.ime.dependency.InputDependencyManager
import org.kodein.di.instance
import timber.log.Timber
import java.io.File
import java.util.Locale
import kotlin.math.min

/**
 * Very small local learning layer for mobile typing corrections.
 *
 * Heavy lifting stays in Rime's Lua filters. This class only observes candidate
 * updates and commits to build a bounded, private signal that can later feed a
 * richer reranker without adding network access or per-key CPU cost.
 */
class TypingCorrectionStats : InputBroadcastReceiver {
    private val di = InputDependencyManager.getInstance().di
    private val context: Context by di.instance()

    private val prefs by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private var lastCandidates: Array<CandidateItem> = emptyArray()
    private var lastCandidateAt = 0L
    private var lastInputCode = ""
    private var lastCommittedCorrection: CorrectionCandidate? = null

    override fun onCompositionUpdate(data: CompositionProto) {
        onInputCodeChanged(data.preedit.orEmpty())
    }

    override fun onCandidateListUpdate(data: RimeMessage.CandidateListMessage.Data) {
        if (data.candidates.isEmpty()) {
            return
        }
        lastCandidates = data.candidates.take(MAX_OBSERVED_CANDIDATES).toTypedArray()
        lastCandidateAt = SystemClock.elapsedRealtime()
    }

    override fun onCandidateMenuUpdate(data: MenuProto) {
        if (data.candidates.isEmpty()) {
            return
        }
        lastCandidates = data.candidates
            .asSequence()
            .map { CandidateItem(it.text, it.comment.orEmpty()) }
            .take(MAX_OBSERVED_CANDIDATES)
            .toList()
            .toTypedArray()
        lastCandidateAt = SystemClock.elapsedRealtime()
    }

    fun onCandidateSelected(index: Int) {
        val candidate = lastCandidates.getOrNull(index) ?: return
        val correction = candidate.toCorrectionCandidate() ?: return
        adjustMapping(correction.inputCode, correction.correctedCode, SELECT_DELTA)
        lastCommittedCorrection = correction.copy(committedAt = SystemClock.elapsedRealtime())
    }

    fun onCommitted(text: String) {
        if (text.isBlank()) return
        val elapsed = SystemClock.elapsedRealtime() - lastCandidateAt
        if (elapsed !in 0..COMMIT_OBSERVE_WINDOW_MS) return
        val index = lastCandidates.indexOfFirst { it.text == text }
        if (index < 0) return
        val correction = lastCandidates[index].toCorrectionCandidate()
        if (correction != null) {
            val deltaForCorrection = if (index == 0) SELECT_DELTA else SECONDARY_SELECT_DELTA
            adjustMapping(correction.inputCode, correction.correctedCode, deltaForCorrection)
            lastCommittedCorrection = correction.copy(committedAt = SystemClock.elapsedRealtime())
        }

        val key = fingerprint(text)
        val old = prefs.getInt(key, 0)
        val delta = when (index) {
            0 -> 2
            1, 2 -> 1
            else -> 0
        }
        if (delta == 0) return

        val next = min(MAX_SCORE, old + delta)
        prefs.edit { putInt(key, next) }
        Timber.d("Local correction stat updated: index=$index, score=$next")
    }

    fun onBackspace() {
        val correction = lastCommittedCorrection ?: return
        val elapsed = SystemClock.elapsedRealtime() - correction.committedAt
        if (elapsed in 0..BACKSPACE_PENALTY_WINDOW_MS) {
            adjustMapping(correction.inputCode, correction.correctedCode, BACKSPACE_DELTA)
            lastCommittedCorrection = null
        }
    }

    private fun CandidateItem.toCorrectionCandidate(): CorrectionCandidate? {
        val mapping = comment.correctionMappingFromComment() ?: return null
        val input = mapping.from.ifBlank { lastInputCode }
        val corrected = mapping.to
        if (input.isBlank() || input == corrected) return null
        return CorrectionCandidate(
            inputCode = input,
            correctedCode = corrected,
            text = text,
            committedAt = 0L,
        )
    }

    private fun String.correctionMappingFromComment(): MappingCode? {
        for (marker in CORRECTION_MARKERS) {
            if (startsWith(marker)) {
                val payload = substringAfter(' ', "").trim()
                if (payload.isBlank()) return null
                val from = payload.substringBefore('>', "").normalizeInputCode()
                val to = payload.substringAfter('>', payload).normalizeInputCode()
                if (to.isBlank()) return null
                return MappingCode(from, to)
            }
        }
        return null
    }

    fun onInputCodeChanged(input: String) {
        val normalized = input.normalizeInputCode()
        if (normalized.isBlank()) return
        lastInputCode = normalized
    }

    private fun String.normalizeInputCode(): String =
        asSequence()
            .filter { it in 'A'..'Z' || it in 'a'..'z' }
            .joinToString("")
            .lowercase(Locale.ROOT)
            .take(MAX_INPUT_CODE_CHARS)

    private fun adjustMapping(inputCode: String, correctedCode: String, delta: Int) {
        if (inputCode.isBlank() || correctedCode.isBlank() || inputCode == correctedCode) return
        val key = "$inputCode>$correctedCode"
        val current = prefs.getInt(KEY_MAPPING_PREFIX + key, 0)
        val oldHits = prefs.getInt(KEY_MAPPING_HITS_PREFIX + key, 0)
        val next = (current + delta).coerceIn(MIN_MAPPING_SCORE, MAX_MAPPING_SCORE)
        val nextHits = if (delta > 0) (oldHits + 1).coerceAtMost(MAX_MAPPING_HITS) else oldHits
        prefs.edit {
            putInt(KEY_MAPPING_PREFIX + key, next)
            putInt(KEY_MAPPING_HITS_PREFIX + key, nextHits)
        }
        writeStatsFile()
        Timber.d("Typing correction mapping updated: $key score=$next hits=$nextHits")
    }

    private fun writeStatsFile() {
        val file = File(DataManager.userDataDir, STATS_FILE_NAME)
        file.parentFile?.mkdirs()
        val entries = prefs.all
            .asSequence()
            .filter { it.key.startsWith(KEY_MAPPING_PREFIX) }
            .mapNotNull { entry ->
                val score = entry.value as? Int ?: return@mapNotNull null
                if (score == 0) return@mapNotNull null
                val mapping = entry.key.removePrefix(KEY_MAPPING_PREFIX)
                val from = mapping.substringBefore('>')
                val to = mapping.substringAfter('>', "")
                if (from.isBlank() || to.isBlank()) return@mapNotNull null
                val hits = prefs.getInt(KEY_MAPPING_HITS_PREFIX + mapping, 0)
                MappingStat(from, to, score, hits)
            }
            .sortedWith(compareByDescending<MappingStat> { it.score }.thenByDescending { it.hits })
            .take(MAX_PERSISTED_MAPPINGS)
            .joinToString(separator = "\n", postfix = "\n") { "${it.from}\t${it.to}\t${it.score}\t${it.hits}" }
        file.writeText(entries)
    }

    private fun fingerprint(text: String): String {
        val normalized = text
            .trim()
            .lowercase(Locale.ROOT)
            .take(MAX_FINGERPRINT_CHARS)
        return "commit:" + normalized.hashCode().toUInt().toString(16)
    }

    companion object {
        private const val PREF_NAME = "typing_correction_stats"
        private const val STATS_FILE_NAME = "mobile_typo_stats.tsv"
        private const val CORRECTION_MARKER = "纠 "
        private const val STRONG_CORRECTION_MARKER = "纠! "
        private const val ASCII_CORRECTION_MARKER = "@typo "
        private const val ASCII_STRONG_CORRECTION_MARKER = "@typo! "
        private val CORRECTION_MARKERS = arrayOf(
            STRONG_CORRECTION_MARKER,
            CORRECTION_MARKER,
            ASCII_STRONG_CORRECTION_MARKER,
            ASCII_CORRECTION_MARKER,
        )
        private const val KEY_MAPPING_PREFIX = "map:"
        private const val KEY_MAPPING_HITS_PREFIX = "hits:"
        private const val MAX_OBSERVED_CANDIDATES = 20
        private const val COMMIT_OBSERVE_WINDOW_MS = 1500L
        private const val BACKSPACE_PENALTY_WINDOW_MS = 2500L
        private const val MAX_SCORE = 100
        private const val MAX_MAPPING_SCORE = 50
        private const val MIN_MAPPING_SCORE = -20
        private const val SELECT_DELTA = 3
        private const val SECONDARY_SELECT_DELTA = 1
        private const val BACKSPACE_DELTA = -5
        private const val MAX_FINGERPRINT_CHARS = 24
        private const val MAX_INPUT_CODE_CHARS = 24
        private const val MAX_PERSISTED_MAPPINGS = 256
        private const val MAX_MAPPING_HITS = 1000
    }

    private data class CorrectionCandidate(
        val inputCode: String,
        val correctedCode: String,
        val text: String,
        val committedAt: Long,
    )

    private data class MappingCode(
        val from: String,
        val to: String,
    )

    private data class MappingStat(
        val from: String,
        val to: String,
        val score: Int,
        val hits: Int,
    )
}
