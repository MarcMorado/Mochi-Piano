package app.mochipiano.record

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.mochipiano.audio.Sound
import app.mochipiano.midi.MidiHub
import app.mochipiano.midi.MidiMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** One recorded performance. [events] are (ms from start, status, data1, data2). */
data class Take(
    val id: String,
    val title: String,
    val createdMs: Long,
    val durationMs: Long,
    val notes: Int,
    val events: List<IntArray>,
    val hasAudio: Boolean,
)

/**
 * Recording and playback. Every take is kept as MIDI events (so it can be played back on the piano and
 * exported as a .mid file) and, when she asks, as microphone sound too (an .m4a that plays anywhere).
 */
object Recordings {
    val takes = mutableStateListOf<Take>()
    var playing by mutableStateOf<String?>(null)
        private set
    private var dir: File? = null
    private var playJob: Job? = null

    fun load(ctx: Context) {
        val d = File(ctx.filesDir, "recordings").apply { mkdirs() }
        dir = d
        takes.clear()
        d.listFiles()?.filter { it.name.endsWith(".json") }?.mapNotNull { f -> try { decode(JSONObject(f.readText())) } catch (_: Exception) { null } }
            ?.sortedByDescending { it.createdMs }?.let { takes.addAll(it) }
    }

    fun midiFile(t: Take) = File(dir, "${t.id}.mid")
    fun audioFile(id: String) = File(dir, "$id.m4a")

    /** Turn raw messages captured since [startNanos] into a saved take. */
    fun save(title: String, msgs: List<MidiMsg>, startNanos: Long, audioId: String? = null): Take? {
        val d = dir ?: return null
        val notes = msgs.count { (it.status and 0xF0) == 0x90 && it.d2 > 0 }
        if (notes == 0 && audioId == null) return null
        val first = msgs.firstOrNull { (it.status and 0xF0) == 0x90 }?.nanos ?: startNanos
        val origin = minOf(startNanos, first).let { o -> if (first - o > 3_000_000_000L) first - 500_000_000L else o }
        val ev = msgs.map { intArrayOf(((it.nanos - origin) / 1_000_000).toInt().coerceAtLeast(0), it.status, it.d1, it.d2) }
        val id = audioId ?: ("rec-" + System.currentTimeMillis().toString(36))
        val t = Take(id, title, System.currentTimeMillis(), (ev.lastOrNull()?.get(0) ?: 0).toLong() + 400, notes, ev, audioId != null && audioFile(id).exists())
        File(d, "$id.json").writeText(encode(t).toString())
        midiFile(t).writeBytes(smf(ev))
        takes.add(0, t)
        return t
    }

    fun delete(t: Take) {
        val d = dir ?: return
        listOf("json", "mid", "m4a").forEach { File(d, "${t.id}.$it").delete() }
        takes.removeAll { it.id == t.id }
    }

    fun rename(t: Take, title: String) {
        val d = dir ?: return
        val n = t.copy(title = title)
        File(d, "${t.id}.json").writeText(encode(n).toString())
        val i = takes.indexOfFirst { it.id == t.id }
        if (i >= 0) takes[i] = n
    }

    // ---------- playback ----------
    fun play(t: Take, scope: CoroutineScope) {
        stop()
        playing = t.id
        playJob = scope.launch {
            val toPiano = MidiHub.canSendToPiano
            // Without a piano, pair each note-on with its note-off to know how long to sound it.
            val durations = HashMap<Int, Long>()
            if (!toPiano) {
                val open = HashMap<Int, Int>()
                t.events.forEachIndexed { i, e ->
                    val type = e[1] and 0xF0
                    if (type == 0x90 && e[3] > 0) open[e[2]] = i
                    else if (type == 0x80 || (type == 0x90 && e[3] == 0)) open.remove(e[2])?.let { s -> durations[s] = (e[0] - t.events[s][0]).toLong() }
                }
            }
            val start = System.currentTimeMillis()
            try {
                t.events.forEachIndexed { i, e ->
                    val wait = start + e[0] - System.currentTimeMillis()
                    if (wait > 0) delay(wait)
                    if (toPiano) MidiHub.sendRaw(e[1], e[2], e[3])
                    else if ((e[1] and 0xF0) == 0x90 && e[3] > 0) Sound.note(e[2], (durations[i] ?: 500).coerceIn(80, 4000), e[3])
                }
                delay(600)
            } finally {
                MidiHub.allNotesOff()
                playing = null
            }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        playing = null
    }

    // ---------- sharing ----------
    fun share(ctx: Context, t: Take, audio: Boolean) {
        val f = if (audio && t.hasAudio) audioFile(t.id) else midiFile(t)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (audio && t.hasAudio) "audio/mp4" else "audio/midi"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${t.title} · played on Mochi Piano")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---------- microphone ----------
    private var mic: MediaRecorder? = null

    /** Start recording sound to a new file; returns its id, or null if the microphone is unavailable. */
    fun startMic(ctx: Context): String? {
        val d = dir ?: return null
        val id = "rec-" + System.currentTimeMillis().toString(36)
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(File(d, "$id.m4a").absolutePath)
            r.prepare()
            r.start()
            mic = r
            id
        } catch (_: Exception) {
            mic = null
            null
        }
    }

    fun stopMic() {
        try { mic?.stop() } catch (_: Exception) {}
        try { mic?.release() } catch (_: Exception) {}
        mic = null
    }

    // ---------- Standard MIDI File, format 0, 480 ticks per quarter at 120 bpm (1 tick = 1/960 s) ----------
    private fun smf(ev: List<IntArray>): ByteArray {
        val track = ByteArrayOutputStream()
        fun vlq(v: Int) {
            var x = v.coerceAtLeast(0)
            val bytes = ArrayList<Int>()
            bytes += x and 0x7F
            x = x shr 7
            while (x > 0) { bytes += (x and 0x7F) or 0x80; x = x shr 7 }
            bytes.reversed().forEach { track.write(it) }
        }
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x51, 0x03, 0x07, 0xA1.toByte(), 0x20))
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x58, 0x04, 0x04, 0x02, 0x18, 0x08))
        var lastTick = 0
        ev.forEach { e ->
            val tick = (e[0] * 0.96).toInt()
            vlq(tick - lastTick); lastTick = tick
            track.write(e[1]); track.write(e[2] and 0x7F); track.write(e[3] and 0x7F)
        }
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0x00))
        val body = track.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray()); out.write(byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, 0x01, 0xE0.toByte()))
        out.write("MTrk".toByteArray())
        out.write(byteArrayOf((body.size shr 24).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()))
        out.write(body)
        return out.toByteArray()
    }

    private fun encode(t: Take) = JSONObject().apply {
        put("id", t.id); put("title", t.title); put("created", t.createdMs); put("duration", t.durationMs); put("notes", t.notes); put("audio", t.hasAudio)
        put("events", JSONArray().apply { t.events.forEach { e -> put(JSONArray().apply { e.forEach { put(it) } }) } })
    }

    private fun decode(o: JSONObject): Take {
        val ea = o.getJSONArray("events")
        val ev = (0 until ea.length()).map { i -> val a = ea.getJSONArray(i); IntArray(4) { a.getInt(it) } }
        return Take(o.getString("id"), o.getString("title"), o.getLong("created"), o.getLong("duration"), o.getInt("notes"), ev, o.optBoolean("audio", false))
    }
}

/** The performance from the last song run, kept until Results saves it or a new run starts. */
object LastTake {
    var capture: Capture? = null
    var title: String = ""
}

/** Collects every message from the piano while a performance is being recorded. */
class Capture {
    val msgs = ArrayList<MidiMsg>()
    val startNanos = System.nanoTime()
    fun add(m: MidiMsg) = synchronized(msgs) { msgs += m }
    fun snapshot(): List<MidiMsg> = synchronized(msgs) { ArrayList(msgs) }
}
