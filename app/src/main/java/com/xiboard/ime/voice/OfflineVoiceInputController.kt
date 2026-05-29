/*
 * SPDX-FileCopyrightText: 2026 Xiboard contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getMainExecutor
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.xiboard.R
import com.xiboard.ime.core.TrimeInputMethodService
import com.xiboard.ime.dependency.InputDependencyManager
import com.xiboard.ime.keyboard.KeyboardWindow
import com.xiboard.ime.window.BoardWindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.kodein.di.instance
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.sqrt

class OfflineVoiceInputController {
    private val di = InputDependencyManager.getInstance().di
    private val service: TrimeInputMethodService by di.instance()
    private val windowManager: BoardWindowManager by di.instance()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recordMode = RecordMode.TOGGLE
    private var shouldCommitResult = true
    private var shouldShowVoiceWindow = false
    private var stopRequestedAt = 0L
    private var warmUpJob: Job? = null
    private val recognizerLock = Any()
    private var recognizer: OfflineRecognizer? = null
    private val voiceProfile = VoiceModelProfile.SENSE_VOICE_2025
    private val _state = MutableStateFlow(VoiceUiState())

    val state: StateFlow<VoiceUiState> = _state.asStateFlow()
    val serviceScope: CoroutineScope
        get() = service.lifecycleScope

    @Volatile
    private var isRecording = false

    @Volatile
    private var isFinishing = false

    @Volatile
    private var captureFailure: VoiceCaptureFailure? = null

    @Volatile
    private var sessionMessageRes: Int? = null

    var isHoldToTalkRecording = false
        private set

    val isHoldToTalkActive: Boolean
        get() = recordMode == RecordMode.HOLD && (isHoldToTalkRecording || isRecording || isFinishing || recordingJob?.isActive == true)

    fun toggle() {
        if (isRecording || isFinishing) {
            stopRecording(commitResult = true, withGrace = true)
        } else {
            recordMode = RecordMode.TOGGLE
            startRecording(commitResult = true, showVoiceWindow = true)
        }
    }

    fun warmUp() {
        if (recognizer != null || warmUpJob?.isActive == true) return
        warmUpJob = service.lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                ensureRecognizer()
                Timber.i("Offline voice model warmed up: ${voiceProfile.id}")
            }.onFailure {
                Timber.w(it, "Offline voice model warm-up failed")
            }
        }
    }

    fun startHoldToTalk() {
        if (!isRecording && !isFinishing) {
            recordMode = RecordMode.HOLD
            isHoldToTalkRecording = true
            startRecording(commitResult = true, showVoiceWindow = false)
        }
    }

    fun stopHoldToTalk() {
        if (isHoldToTalkActive) {
            isHoldToTalkRecording = false
            stopRecording(commitResult = true, withGrace = true)
        }
    }

    fun cancelHoldToTalk() {
        if (isHoldToTalkActive) {
            isHoldToTalkRecording = false
            stopRecording(commitResult = false, withGrace = false)
        }
    }

    private fun startRecording(
        commitResult: Boolean = true,
        showVoiceWindow: Boolean,
    ) {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudioPermission()
            return
        }
        if (recordingJob?.isActive == true) return
        shouldCommitResult = commitResult
        shouldShowVoiceWindow = showVoiceWindow
        isRecording = true
        isFinishing = false
        captureFailure = null
        sessionMessageRes = null
        stopRequestedAt = 0L
        Timber.i("Offline voice start requested: mode=$recordMode, model=${voiceProfile.id}, showWindow=$showVoiceWindow")
        updateUi(VoiceUiStatus.LISTENING, "")
        if (shouldShowVoiceWindow) {
            attachVoiceWindow()
        }
        recordingJob = service.lifecycleScope.launch {
            val initialized = withContext(Dispatchers.IO) {
                runCatching {
                    initAudioRecord()
                }.getOrElse {
                    Timber.e(it, "Failed to initialize offline voice input")
                    showError(it)
                    false
                }
            }
            if (!initialized) {
                val failedCapture = captureFailure ?: VoiceCaptureFailure.START_FAILED
                val commitStoppedHold = shouldCommitResult
                val restoreKeyboard = shouldShowVoiceWindow
                isHoldToTalkRecording = false
                cleanupRecorder()
                finishEmptySession(commitStoppedHold, restoreKeyboard, failedCapture.messageRes)
                return@launch
            }
            if (!shouldContinueRecording()) {
                val commitStoppedHold = shouldCommitResult
                val restoreKeyboard = shouldShowVoiceWindow
                Timber.d("Offline voice session ended before recorder became active")
                cleanupRecorder()
                finishEmptySession(commitStoppedHold, restoreKeyboard)
                return@launch
            }
            withContext(Dispatchers.IO) {
                recordAndRecognize(shouldRestoreKeyboard = shouldShowVoiceWindow)
            }
        }
    }

    private fun requestRecordAudioPermission() {
        isHoldToTalkRecording = false
        Toast.makeText(service, R.string.offline_voice_permission_denied, Toast.LENGTH_SHORT).show()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            service.startActivity(
                Intent(service, VoicePermissionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    private fun stopRecording(commitResult: Boolean, withGrace: Boolean) {
        Timber.i("Offline voice stop requested: commit=$commitResult, grace=$withGrace, recording=$isRecording")
        shouldCommitResult = commitResult
        isRecording = false
        isFinishing = commitResult && withGrace
        stopRequestedAt = SystemClock.elapsedRealtime()
        if (!isFinishing) {
            runCatching { audioRecord?.stop() }
        } else {
            updateUi(VoiceUiStatus.FINALIZING, _state.value.text)
        }
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        synchronized(recognizerLock) {
            recognizer?.let { return it }
            if (!voiceProfile.requiredAssets.all(::assetExists)) {
                throw IllegalStateException("${service.getString(R.string.offline_voice_model_missing)}: ${voiceProfile.id}")
            }
            return OfflineRecognizer(
                assetManager = service.assets,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
                    modelConfig = voiceProfile.modelConfig(),
                ),
            ).also {
                recognizer = it
            }
        }
    }

    private fun VoiceModelProfile.modelConfig(): OfflineModelConfig = OfflineModelConfig(
        senseVoice = OfflineSenseVoiceModelConfig(
            model = "$dir/model.int8.onnx",
            language = "auto",
            useInverseTextNormalization = false,
        ),
        tokens = "$dir/tokens.txt",
        numThreads = threads,
        provider = "cpu",
    )

    @SuppressLint("MissingPermission")
    private fun initAudioRecord(): Boolean {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) return false
        val bufferSize = (minBufferSize * 4).coerceAtLeast(SAMPLE_RATE * 2)
        for (source in AUDIO_SOURCES) {
            val recorder = try {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            } catch (e: SecurityException) {
                Timber.w(e, "Record audio permission was revoked before recorder creation")
                captureFailure = VoiceCaptureFailure.START_FAILED
                return false
            } catch (e: RuntimeException) {
                Timber.w(e, "Failed to create offline voice recorder for source=${audioSourceName(source)}")
                continue
            }
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Timber.w("Offline voice recorder source=${audioSourceName(source)} was not initialized: state=${recorder.state}")
                runCatching { recorder.release() }
                continue
            }
            val started = runCatching {
                recorder.startRecording()
                recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
            }.getOrElse {
                Timber.w(it, "Offline voice recorder source=${audioSourceName(source)} failed during start")
                false
            }
            if (started) {
                audioRecord = recorder
                Timber.i("Offline voice recorder active: source=${audioSourceName(source)}, buffer=$bufferSize")
                return true
            }
            Timber.w("Offline voice recorder source=${audioSourceName(source)} failed to start: state=${recorder.recordingState}")
            runCatching { recorder.release() }
        }
        captureFailure = VoiceCaptureFailure.START_FAILED
        return false
    }

    private suspend fun recordAndRecognize(shouldRestoreKeyboard: Boolean) = coroutineScope {
        val recorder = audioRecord ?: return@coroutineScope
        val audioQueue = LinkedBlockingQueue<FloatArray>()
        val captureJob = launch(Dispatchers.IO) {
            captureAudio(recorder, audioQueue)
        }
        val audio = ArrayList<Float>(SAMPLE_RATE * 4)
        var displayedText = ""
        var finalText = ""
        var recognitionFailed = false
        var lastDecodeAt = 0L

        runCatching {
            warmUp()
            var currentRecognizer = currentRecognizerOrNull()
            var captureFinished = false
            while (!captureFinished || audioQueue.isNotEmpty()) {
                if (currentRecognizer == null) {
                    currentRecognizer = currentRecognizerOrNull()
                }
                val samples = audioQueue.poll(AUDIO_QUEUE_POLL_MS, TimeUnit.MILLISECONDS)
                if (samples == null) {
                    if (currentRecognizer != null && shouldDecodePartial(lastDecodeAt, audio.size)) {
                        lastDecodeAt = SystemClock.elapsedRealtime()
                        displayedText = decodeAudio(currentRecognizer, audio, partial = true, displayedText)
                    }
                    continue
                }
                if (samples === END_OF_AUDIO) {
                    captureFinished = true
                    continue
                }
                samples.forEach { audio.add(it) }
                if (currentRecognizer != null && !isFinishing && shouldDecodePartial(lastDecodeAt, audio.size)) {
                    lastDecodeAt = SystemClock.elapsedRealtime()
                    displayedText = decodeAudio(currentRecognizer, audio, partial = true, displayedText)
                }
            }
            if (currentRecognizer == null) {
                currentRecognizer = waitForRecognizerWithinBudget(displayedText)
            }
            if (currentRecognizer != null) {
                finalText = decodeFinalWithinBudget(currentRecognizer, audio, displayedText)
            } else {
                finalText = displayedText
            }
        }.onFailure {
            Timber.e(it, "Offline voice recognition failed")
            recognitionFailed = true
            showError(it)
        }.also {
            if (captureJob.isActive) {
                isRecording = false
                captureJob.cancelAndJoin()
            }
        }

        if (recognitionFailed) {
            cleanupRecorder()
            return@coroutineScope
        }
        val commitResult = shouldCommitResult
        val failedCapture = captureFailure
        val messageRes = sessionMessageRes
        cleanupRecorder()
        service.lifecycleScope.launch {
            if (commitResult && finalText.isNotEmpty()) {
                service.finishVoiceComposingText(finalText)
            } else {
                service.finishVoiceComposingText("")
                if (commitResult) {
                    val message = failedCapture?.messageRes ?: messageRes ?: R.string.offline_voice_empty
                    Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
                }
            }
            updateUi(VoiceUiStatus.IDLE, "")
            if (shouldRestoreKeyboard) {
                attachKeyboardWindow()
            }
        }
    }

    private fun captureAudio(
        recorder: AudioRecord,
        audioQueue: LinkedBlockingQueue<FloatArray>,
    ) {
        val buffer = ShortArray((SAMPLE_RATE * READ_INTERVAL_SECONDS).toInt())
        var quietSince = 0L
        runCatching {
            if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                captureFailure = VoiceCaptureFailure.START_FAILED
                Timber.e("Offline voice recorder failed to start: state=${recorder.recordingState}")
                return@runCatching
            }
            while (shouldContinueRecording() && serviceScope.isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) {
                    captureFailure = VoiceCaptureFailure.READ_FAILED
                    Timber.e("Offline voice recorder read failed: code=$read")
                    break
                }
                if (read == 0) continue
                val samples = FloatArray(read) { buffer[it] / PCM_16BIT_SCALE }
                audioQueue.offer(samples)
                if (isFinishing) {
                    val now = SystemClock.elapsedRealtime()
                    quietSince = if (samples.rms() < FINAL_SILENCE_RMS) {
                        if (quietSince == 0L) now else quietSince
                    } else {
                        0L
                    }
                    if (quietSince != 0L && now - quietSince >= FINAL_SILENCE_MS) break
                }
            }
        }.onFailure {
            if (shouldCommitResult) {
                captureFailure = VoiceCaptureFailure.READ_FAILED
                Timber.e(it, "Offline voice audio capture failed")
            } else {
                Timber.d(it, "Offline voice audio capture cancelled")
            }
        }
        audioQueue.offer(END_OF_AUDIO)
        Timber.i("Offline voice recorder stopped")
    }

    private suspend fun waitForRecognizer(): OfflineRecognizer {
        warmUpJob?.takeIf { it.isActive }?.let { job ->
            runCatching { job.join() }
        }
        return ensureRecognizer()
    }

    private fun currentRecognizerOrNull(): OfflineRecognizer? = synchronized(recognizerLock) {
        recognizer
    }

    private suspend fun waitForRecognizerWithinBudget(previousText: String): OfflineRecognizer? {
        val budgetMs = if (isFinishing) remainingFinalizeBudgetMs() else START_RECOGNIZER_TIMEOUT_MS
        if (budgetMs <= 0L) return null
        warmUp()
        return withTimeoutOrNull(budgetMs) {
            warmUpJob?.takeIf { it.isActive }?.join()
            currentRecognizerOrNull()
        }.also { recognizer ->
            if (recognizer == null) {
                sessionMessageRes = R.string.offline_voice_model_loading
                Timber.w("Offline voice recognizer wait timed out after ${budgetMs}ms; committing live text length=${previousText.length}")
            }
        }
    }

    private suspend fun decodeAudio(
        recognizer: OfflineRecognizer,
        audio: List<Float>,
        partial: Boolean,
        previousText: String,
    ): String {
        val text = withContext(Dispatchers.IO) {
            decodeAudioSnapshot(recognizer, audio, partial).toDisplayText(final = !partial)
        }
        if (text.isNotEmpty() && text != previousText) {
            updateLiveText(text)
            delay(PARTIAL_UI_SETTLE_MS)
            return text
        }
        return previousText
    }

    private suspend fun decodeFinalWithinBudget(
        recognizer: OfflineRecognizer,
        audio: List<Float>,
        previousText: String,
    ): String {
        val budgetMs = remainingFinalizeBudgetMs()
        if (budgetMs <= 0L || audio.size < MIN_DECODE_SAMPLES) return previousText

        val finalDecode = service.lifecycleScope.async(Dispatchers.IO) {
            decodeAudioSnapshot(
                recognizer = recognizer,
                audio = audio,
                partial = false,
                maxDecodeSecondsOverride = FINAL_DECODE_MAX_SECONDS,
            ).toDisplayText(final = true)
        }
        val text = withTimeoutOrNull(budgetMs) {
            finalDecode.await()
        }
        if (text == null) {
            Timber.w("Offline voice final decode timed out after ${budgetMs}ms; committing live text")
            finalDecode.invokeOnCompletion { throwable ->
                if (throwable != null) {
                    Timber.w(throwable, "Offline voice late final decode failed")
                }
            }
            return previousText
        }

        if (text.isBlank()) return previousText
        val previousLength = previousText.comparableLength()
        val finalLength = text.comparableLength()
        return if (previousLength > 0 && finalLength < previousLength * MIN_FINAL_REPLACE_RATIO) {
            previousText
        } else {
            text
        }
    }

    private fun decodeAudioSnapshot(
        recognizer: OfflineRecognizer,
        audio: List<Float>,
        partial: Boolean,
        maxDecodeSecondsOverride: Int? = null,
    ): RecognitionSnapshot {
        if (audio.size < MIN_DECODE_SAMPLES) return RecognitionSnapshot.EMPTY
        val maxSamples = (maxDecodeSecondsOverride ?: voiceProfile.maxDecodeSeconds) * SAMPLE_RATE
        val shouldLimitWindow = partial || maxDecodeSecondsOverride != null
        val start = if (shouldLimitWindow) (audio.size - maxSamples).coerceAtLeast(0) else 0
        val limit = min(audio.size - start, if (shouldLimitWindow) maxSamples else audio.size)
        val samples = FloatArray(limit)
        for (i in 0 until limit) {
            samples[i] = audio[start + i]
        }
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).toSnapshot()
        } finally {
            stream.release()
        }
    }

    private fun shouldDecodePartial(
        lastDecodeAt: Long,
        sampleCount: Int,
    ): Boolean {
        if (sampleCount < MIN_DECODE_SAMPLES) return false
        val now = SystemClock.elapsedRealtime()
        return now - lastDecodeAt >= voiceProfile.partialDecodeIntervalMs
    }

    private fun finishEmptySession(
        commitResult: Boolean,
        restoreKeyboard: Boolean,
        messageRes: Int = R.string.offline_voice_empty,
    ) {
        service.lifecycleScope.launch {
            service.finishVoiceComposingText("")
            if (commitResult) {
                Toast.makeText(service, messageRes, Toast.LENGTH_SHORT).show()
            }
            updateUi(VoiceUiStatus.IDLE, "")
            if (restoreKeyboard) {
                attachKeyboardWindow()
            }
        }
    }

    private fun shouldContinueRecording(): Boolean {
        if (isRecording) return true
        if (!isFinishing) return false
        val elapsed = SystemClock.elapsedRealtime() - stopRequestedAt
        return elapsed < FINAL_GRACE_MS && elapsed < FINALIZE_TOTAL_TIMEOUT_MS
    }

    private fun remainingFinalizeBudgetMs(): Long {
        if (stopRequestedAt == 0L) return FINALIZE_TOTAL_TIMEOUT_MS
        val elapsed = SystemClock.elapsedRealtime() - stopRequestedAt
        return (FINALIZE_TOTAL_TIMEOUT_MS - elapsed).coerceAtLeast(0L)
    }

    private fun updateLiveText(text: String) {
        updateUi(if (isFinishing) VoiceUiStatus.FINALIZING else VoiceUiStatus.LISTENING, text)
        service.lifecycleScope.launch {
            service.updateVoiceComposingText(text)
        }
    }

    private fun updateUi(status: VoiceUiStatus, text: String) {
        _state.value = VoiceUiState(status, text)
    }

    private fun assetExists(path: String): Boolean = runCatching {
        service.assets.open(path).use { true }
    }.getOrDefault(false)

    private fun showError(throwable: Throwable) {
        service.lifecycleScope.launch {
            Toast.makeText(
                service,
                service.getString(R.string.offline_voice_error, throwable.message ?: throwable.javaClass.simpleName),
                Toast.LENGTH_LONG,
            ).show()
            service.finishVoiceComposingText("")
            updateUi(VoiceUiStatus.IDLE, "")
            attachKeyboardWindow()
        }
    }

    private fun cleanupRecorder() {
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        isRecording = false
        isFinishing = false
        isHoldToTalkRecording = false
        recordingJob = null
        shouldShowVoiceWindow = false
        stopRequestedAt = 0L
        captureFailure = null
        sessionMessageRes = null
    }

    private fun attachVoiceWindow() {
        getMainExecutor(service).execute {
            windowManager.attachWindow(OfflineVoiceWindow)
        }
    }

    private fun attachKeyboardWindow() {
        getMainExecutor(service).execute {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    private fun audioSourceName(source: Int): String = when (source) {
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        else -> source.toString()
    }

    private enum class RecordMode {
        TOGGLE,
        HOLD,
    }

    private enum class VoiceCaptureFailure(val messageRes: Int) {
        START_FAILED(R.string.offline_voice_record_start_failed),
        READ_FAILED(R.string.offline_voice_record_read_failed),
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val READ_INTERVAL_SECONDS = 0.1f
        private const val PCM_16BIT_SCALE = 32768.0f
        private const val FINAL_GRACE_MS = 400L
        private const val FINALIZE_TOTAL_TIMEOUT_MS = 1500L
        private const val FINAL_DECODE_MAX_SECONDS = 8
        private const val START_RECOGNIZER_TIMEOUT_MS = 2500L
        private const val FINAL_SILENCE_MS = 450L
        private const val FINAL_SILENCE_RMS = 0.0065
        private const val AUDIO_QUEUE_POLL_MS = 50L
        private const val MIN_DECODE_SAMPLES = SAMPLE_RATE / 2
        private const val MIN_FINAL_REPLACE_RATIO = 0.65
        private const val PARTIAL_UI_SETTLE_MS = 8L
        private val END_OF_AUDIO = FloatArray(0)
        private val AUDIO_SOURCES = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT,
        )
    }
}

data class VoiceUiState(
    val status: VoiceUiStatus = VoiceUiStatus.IDLE,
    val text: String = "",
)

enum class VoiceUiStatus {
    IDLE,
    LISTENING,
    FINALIZING,
}

private data class VoiceModelProfile(
    val id: String,
    val dir: String,
    val threads: Int,
    val partialDecodeIntervalMs: Long,
    val maxDecodeSeconds: Int,
) {
    val requiredAssets: List<String>
        get() = listOf("$dir/model.int8.onnx", "$dir/tokens.txt")

    companion object {
        val SENSE_VOICE_2025 = VoiceModelProfile(
            id = "senseVoice20250909",
            dir = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09",
            threads = 2,
            partialDecodeIntervalMs = 1000L,
            maxDecodeSeconds = 14,
        )
    }
}

private data class RecognitionSnapshot(
    val text: String,
    val tokens: List<String>,
    val timestamps: FloatArray,
    val durations: FloatArray,
    val lang: String,
) {
    fun toDisplayText(final: Boolean): String {
        val baseText = text.cleanRecognitionText()
        val punctuated = if (final) punctuateFromTokens(baseText) else baseText
        return punctuated
            .normalizeEnglishCasing()
            .normalizeRecognitionSpacing(final)
    }

    private fun punctuateFromTokens(baseText: String): String {
        if (baseText.isBlank() || tokens.isEmpty() || timestamps.isEmpty()) return baseText
        val pieces = tokenPieces()
        if (pieces.size < 2) return baseText
        val english = baseText.looksMostlyEnglish() || lang.equals("en", ignoreCase = true)
        val builder = StringBuilder()
        var previousEnd = pieces.first().start
        pieces.forEachIndexed { index, piece ->
            if (index > 0) {
                val gap = piece.start - previousEnd
                if (gap >= SHORT_PAUSE_SECONDS) {
                    builder.appendPausePunctuation(english, gap >= LONG_PAUSE_SECONDS)
                }
            }
            builder.append(piece.text)
            previousEnd = piece.end
        }
        val rebuilt = builder.toString().cleanRecognitionText()
        val baseLength = baseText.comparableLength()
        return if (rebuilt.comparableLength() >= baseLength * 0.6) rebuilt else baseText
    }

    private fun tokenPieces(): List<TokenPiece> = tokens.mapIndexedNotNull { index, raw ->
        val tokenText = raw
            .replace(SPECIAL_TOKEN_REGEX, "")
            .replace("▁", " ")
            .replace("\uFFFD", "")
            .replace("\uFFFC", "")
        if (tokenText.isBlank()) return@mapIndexedNotNull null
        val start = timestamps.getOrNull(index) ?: return@mapIndexedNotNull null
        val duration = durations.getOrNull(index)?.takeIf { it > 0f } ?: DEFAULT_TOKEN_DURATION_SECONDS
        TokenPiece(tokenText, start, start + duration)
    }

    companion object {
        val EMPTY = RecognitionSnapshot("", emptyList(), floatArrayOf(), floatArrayOf(), "")
        private const val SHORT_PAUSE_SECONDS = 0.65f
        private const val LONG_PAUSE_SECONDS = 1.1f
        private const val DEFAULT_TOKEN_DURATION_SECONDS = 0.08f
    }
}

private data class TokenPiece(
    val text: String,
    val start: Float,
    val end: Float,
)

private fun OfflineRecognizerResult.toSnapshot(): RecognitionSnapshot = RecognitionSnapshot(
    text = text,
    tokens = tokens.toList(),
    timestamps = timestamps,
    durations = durations,
    lang = lang,
)

private fun FloatArray.rms(): Double {
    if (isEmpty()) return 0.0
    var sum = 0.0
    for (sample in this) {
        sum += sample * sample
    }
    return sqrt(sum / size)
}

private fun String.cleanRecognitionText(): String = trim()
    .removeJsonEnvelope()
    .replace(SPECIAL_TOKEN_REGEX, "")
    .replace("▁", " ")
    .replace(Regex("\\s+"), " ")
    .replace("\uFFFD", "")
    .replace("\uFFFC", "")
    .trim()

private fun String.removeJsonEnvelope(): String {
    val match = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").find(this) ?: return this
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")
}

private fun String.normalizeEnglishCasing(): String = WORD_REGEX.replace(this) { match ->
    val word = match.value
    val upper = word.uppercase(Locale.US)
    KNOWN_ENGLISH_CASE[upper] ?: when {
        word.length > 1 && word == upper -> word.lowercase(Locale.US)
        else -> word
    }
}.let { normalized ->
    if (normalized.looksMostlyEnglish()) normalized.capitalizeEnglishSentences() else normalized
}

private fun String.capitalizeEnglishSentences(): String {
    val out = StringBuilder(length)
    var capitalizeNext = true
    for (char in this) {
        val next = if (capitalizeNext && char in 'a'..'z') {
            capitalizeNext = false
            char.uppercaseChar()
        } else {
            if (char.isLetter()) capitalizeNext = false
            char
        }
        out.append(next)
        if (char in ".!?。！？") capitalizeNext = true
    }
    return WORD_REGEX.replace(out.toString()) { match ->
        val upper = match.value.uppercase(Locale.US)
        KNOWN_ENGLISH_CASE[upper] ?: match.value
    }
}

private fun String.normalizeRecognitionSpacing(final: Boolean): String {
    var result = trim()
        .replace(Regex("\\s+([,.;:!?])"), "$1")
        .replace(Regex("\\s+([，。！？；：])"), "$1")
        .replace(Regex("([，。！？；：])\\s+"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
    return result
}

private fun String.looksMostlyEnglish(): Boolean {
    val latin = count { it in 'A'..'Z' || it in 'a'..'z' }
    val cjk = count { it.isCjkIdeograph() }
    return latin > 0 && latin >= cjk * 2
}

private fun Char.isCjkIdeograph(): Boolean = code.let {
    it in 0x3400..0x4DBF ||
        it in 0x4E00..0x9FFF ||
        it in 0xF900..0xFAFF
}

private fun String.comparableLength(): Int = count {
    !it.isWhitespace() && !it.isSpeechPunctuation()
}

private fun StringBuilder.appendPausePunctuation(english: Boolean, longPause: Boolean) {
    while (isNotEmpty() && last().isWhitespace()) {
        setLength(length - 1)
    }
    if (isEmpty() || last().isSpeechPunctuation()) return
    if (english) {
        append(if (longPause) "." else ",")
    }
    append(' ')
}

private fun String.endsWithSentencePunctuation(): Boolean = lastOrNull()?.isSpeechPunctuation() == true

private fun Char.isSpeechPunctuation(): Boolean = this in "，。！？；：,.!?;:"

private val SPECIAL_TOKEN_REGEX = Regex("<\\|[^>]+\\|>|\\[[^\\]]+\\]")
private val WORD_REGEX = Regex("[A-Za-z][A-Za-z']*")
private val KNOWN_ENGLISH_CASE = mapOf(
    "I" to "I",
    "OK" to "OK",
    "AI" to "AI",
    "API" to "API",
    "APK" to "APK",
    "ASR" to "ASR",
    "CPU" to "CPU",
    "GPU" to "GPU",
    "GPS" to "GPS",
    "GPT" to "GPT",
    "HTTP" to "HTTP",
    "HTTPS" to "HTTPS",
    "IOS" to "iOS",
    "XIBOARD" to "Xiboard",
    "SDK" to "SDK",
    "USB" to "USB",
    "URL" to "URL",
    "ANDROID" to "Android",
    "CHATGPT" to "ChatGPT",
    "OPENAI" to "OpenAI",
    "SENSEVOICE" to "SenseVoice",
)
