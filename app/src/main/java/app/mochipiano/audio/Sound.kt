package app.mochipiano.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import app.mochipiano.midi.MidiHub
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sound for previews and accompaniment. When the piano is connected, notes are sent to it over MIDI,
 * so "Hear it" plays on the real piano. Without a piano a small additive synth (rendered once into
 * SoundPool) stands in. Metronome clicks always come from the phone.
 */
object Sound {
    private const val RATE = 22050
    private var pool: SoundPool? = null
    private val ids = ConcurrentHashMap<Int, Int>()
    private var clickHi = 0
    private var clickLo = 0
    private val main = Handler(Looper.getMainLooper())

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(20)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            ).build()
        pool = p
        val dir = File(ctx.cacheDir, "tones").apply { mkdirs() }
        thread(name = "mochi-synth") {
            clickHi = p.load(writeWav(File(dir, "click_hi.wav"), renderClick(1560.0)).path, 1)
            clickLo = p.load(writeWav(File(dir, "click_lo.wav"), renderClick(1040.0)).path, 1)
            // Middle range first so the common notes are ready soonest.
            val order = (48..84).toList() + (36..47).toList() + (85..96).toList()
            for (n in order) {
                val f = File(dir, "n$n.wav")
                if (!f.exists()) writeWav(f, renderNote(n))
                ids[n] = p.load(f.path, 1)
            }
        }
    }

    /** Play a note for [ms] milliseconds: on the piano when it is connected, else on the phone. */
    fun note(p: Int, ms: Long = 600, velocity: Int = 80) {
        if (MidiHub.canSendToPiano) {
            MidiHub.sendNote(p, velocity, true)
            main.postDelayed({ MidiHub.sendNote(p, 0, false) }, ms)
        } else {
            ids[p]?.let { id -> val v = (velocity / 127f).coerceIn(0.15f, 1f); pool?.play(id, v, v, 1, 0, 1f) }
        }
    }

    fun chord(ps: List<Int>, ms: Long = 900, velocity: Int = 70) = ps.forEach { note(it, ms, velocity) }

    fun click(accent: Boolean) {
        val id = if (accent) clickHi else clickLo
        if (id != 0) pool?.play(id, 0.9f, 0.9f, 2, 0, 1f)
    }

    fun stopAll() = MidiHub.allNotesOff()

    // ---------- rendering ----------
    private fun renderNote(p: Int): ShortArray {
        val f = 440.0 * 2.0.pow((p - 69) / 12.0)
        val len = (RATE * 1.6).toInt()
        val out = ShortArray(len)
        val partials = doubleArrayOf(1.0, 0.36, 0.13, 0.06, 0.03)
        val decay = 1.4 + f / 520.0
        for (i in 0 until len) {
            val t = i / RATE.toDouble()
            val attack = if (t < 0.006) t / 0.006 else 1.0
            val env = attack * exp(-t * decay) * (0.65 + 0.35 * exp(-t * 9))
            var s = 0.0
            for (k in partials.indices) {
                val fk = f * (k + 1) * (1 + 0.0004 * k * k)
                if (fk > RATE / 2.2) break
                s += partials[k] * exp(-t * k * 1.2) * sin(2 * PI * fk * t)
            }
            out[i] = (s * env * 0.42 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun renderClick(freq: Double): ShortArray {
        val len = (RATE * 0.05).toInt()
        return ShortArray(len) { i ->
            val t = i / RATE.toDouble()
            (sin(2 * PI * freq * t) * exp(-t * 90) * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun writeWav(file: File, pcm: ShortArray): File {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort(it) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size * 2); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size * 2)
        }
        FileOutputStream(file).use { it.write(header.array()); it.write(data.array()) }
        return file
    }
}
