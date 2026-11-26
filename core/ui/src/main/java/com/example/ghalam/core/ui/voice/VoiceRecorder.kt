package com.example.ghalam.core.ui.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** نتیجهٔ یک ضبط: فایل M4A و مدت آن. */
data class VoiceClip(val file: File, val durationMs: Long)

/** پوشهٔ فایل‌های ضبط‌شدهٔ موقت (filesDir/voice). */
object VoiceStorage {
    fun dir(context: Context): File = File(context.applicationContext.filesDir, "voice").apply { mkdirs() }

    fun newFile(context: Context): File =
        File(dir(context), "rec_${System.currentTimeMillis()}.${VoiceConfig.EXTENSION}")

    /** فایل‌های قدیمی را پاک می‌کند (ضبط‌هایی که به هیچ‌جا نرسیدند). */
    fun prune(context: Context) {
        val limit = System.currentTimeMillis() - VoiceConfig.KEEP_TEMP_FILES_MS
        dir(context).listFiles()?.forEach { if (it.isFile && it.lastModified() < limit) it.delete() }
    }
}

/**
 * ضبط‌کنندهٔ صدا (MediaRecorder): AAC / M4A، تک‌کاناله، کم‌حجم. با مکث و ادامه.
 * فقط از ترد اصلی صدا زده شود.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var segmentStart = 0L
    private var accumulated = 0L

    var isRecording: Boolean = false
        private set
    var isPaused: Boolean = false
        private set

    /** مدت ضبط تا این لحظه (بدون زمان مکث). */
    fun elapsedMs(): Long =
        accumulated + if (isRecording && !isPaused) SystemClock.elapsedRealtime() - segmentStart else 0L

    /** بلندی صدا بین ۰ تا ۱ (برای موج). */
    fun level(): Float {
        val r = recorder ?: return 0f
        if (isPaused) return 0f
        val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
        return (amp / 12_000f).coerceIn(0f, 1f)
    }

    /** شروع ضبط؛ [onMaxReached] وقتی به سقف مدت رسید (ضبط خودش متوقف شده) صدا زده می‌شود. */
    fun start(onMaxReached: () -> Unit): Boolean {
        if (isRecording) return true
        VoiceStorage.prune(context)
        val out = VoiceStorage.newFile(context)
        val r = newRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(VoiceConfig.CHANNELS)
            r.setAudioSamplingRate(VoiceConfig.SAMPLE_RATE_HZ)
            r.setAudioEncodingBitRate(VoiceConfig.BITRATE_BPS)
            r.setMaxDuration(VoiceConfig.MAX_DURATION_MS)
            r.setOutputFile(out.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
            r.prepare()
            r.start()
            recorder = r
            file = out
            accumulated = 0L
            segmentStart = SystemClock.elapsedRealtime()
            isRecording = true
            isPaused = false
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            false
        }
    }

    fun pause() {
        val r = recorder ?: return
        if (!isRecording || isPaused) return
        runCatching { r.pause() }.onSuccess {
            accumulated += SystemClock.elapsedRealtime() - segmentStart
            isPaused = true
        }
    }

    fun resume() {
        val r = recorder ?: return
        if (!isRecording || !isPaused) return
        runCatching { r.resume() }.onSuccess {
            segmentStart = SystemClock.elapsedRealtime()
            isPaused = false
        }
    }

    /** پایان ضبط. null = خیلی کوتاه بود یا خطا شد (فایل پاک می‌شود). */
    fun stop(): VoiceClip? {
        val r = recorder ?: return null
        val duration = elapsedMs()
        val out = file
        val ok = runCatching { r.stop() }.isSuccess
        reset(r)
        if (out == null) return null
        if (!ok || duration < VoiceConfig.MIN_DURATION_MS || !out.exists() || out.length() == 0L) {
            out.delete()
            return null
        }
        return VoiceClip(out, duration)
    }

    /** انصراف: فایل پاک می‌شود. */
    fun cancel() {
        val r = recorder
        val out = file
        if (r != null) {
            runCatching { r.stop() }
            reset(r)
        }
        out?.delete()
    }

    private fun reset(r: MediaRecorder) {
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
        file = null
        isRecording = false
        isPaused = false
        accumulated = 0L
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
}
