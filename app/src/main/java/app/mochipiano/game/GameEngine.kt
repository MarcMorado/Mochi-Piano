package app.mochipiano.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mochipiano.audio.Sound
import app.mochipiano.midi.NoteEvent
import app.mochipiano.music.Hand
import app.mochipiano.music.SNote
import app.mochipiano.music.Section
import app.mochipiano.music.Song
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

enum class Judge { Perfect, Good, Off, Miss }

data class GameConfig(
    val song: Song,
    val hands: Set<Hand>,
    val speed: Float,
    val wait: Boolean,
    val dynamic: Boolean,
    val level: Int,
    val loop: Section?,
    val autoSpeed: Boolean,
    val otherHand: Boolean,
    val clicks: Boolean = false,
)

data class GameResult(
    val accuracy: Double,
    val perfect: Int,
    val good: Int,
    val off: Int,
    val miss: Int,
    val stray: Int,
    val score: Int,
    val maxCombo: Int,
    val meanOffsetMs: Double?,
    val sectionAcc: Map<String, Double>,
    val missedPitches: Map<Int, Int>,
    val level: Int,
    val speed: Float,
    val loops: Int,
    val mastery: Double,
)

class PlayNote(val n: SNote, val mine: Boolean, val section: Int) {
    var required = true
    var judge: Judge? = null
    var autoDone = false
    var weight = 0.0
}

/**
 * The song clock and the judge. [tick] advances time every frame; [onNote] judges what she played.
 * Wait mode freezes the clock at each note until she plays it. Dynamic difficulty shows only the most
 * important notes at low levels and adds more after each section she plays well.
 */
class GameEngine(private val cfg: GameConfig) {
    val song = cfg.song
    var speed = cfg.speed
        private set
    var level = cfg.level.coerceIn(1, 10)
        private set

    val notes: List<PlayNote> = song.notes.map { n ->
        val si = song.sections.indexOfFirst { n.start >= it.start - 1e-6 && n.start < it.end - 1e-6 }.coerceAtLeast(0)
        PlayNote(n, n.hand in cfg.hands, si)
    }.sortedBy { it.n.start }

    private val regionStart = cfg.loop?.start ?: 0.0
    private val regionEnd = cfg.loop?.end ?: song.length
    var beat = regionStart - song.barBeats
        private set
    private var lastNanos = 0L
    var paused by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var frame by mutableIntStateOf(0)
        private set
    var waiting = false
        private set

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var perfect = 0
        private set
    var good = 0
        private set
    var off = 0
        private set
    var miss = 0
        private set
    var stray = 0
        private set
    var lastJudge: Judge? = null
        private set
    var lastJudgeNanos = 0L
        private set
    var strayPitch = -1
        private set
    var strayNanos = 0L
        private set
    var loops = 0
        private set
    var lastLoopAcc: Double? = null
        private set
    var levelChangedNanos = 0L
        private set
    private val offsets = ArrayList<Double>()
    private val missed = HashMap<Int, Int>()
    private val secHit = HashMap<Int, Double>()
    private val secTot = HashMap<Int, Int>()
    private var lastClick = floor(beat).toInt() - 1
    private var sectionCursor = -1

    val secPerBeat: Double get() = 60.0 / (song.bpm * speed)

    init {
        applyLevel(fromBeat = Double.NEGATIVE_INFINITY)
    }

    // ---------- difficulty ----------
    private fun applyLevel(fromBeat: Double) {
        val mine = notes.filter { it.mine }
        if (!cfg.dynamic || level >= 10) { mine.forEach { if (it.n.start >= fromBeat) it.required = true }; return }
        // Importance: downbeats, beats, long notes, and the outer voice of each hand.
        mine.forEach { pn ->
            val n = pn.n
            val chord = mine.filter { abs(it.n.start - n.start) < 1e-6 && it.n.hand == n.hand }
            val outer = if (n.hand == Hand.R) n.pitch == chord.maxOf { it.n.pitch } else n.pitch == chord.minOf { it.n.pitch }
            val downbeat = abs(n.start / song.barBeats - Math.round(n.start / song.barBeats)) < 1e-6
            val onBeat = abs(n.start - Math.round(n.start)) < 1e-6
            pn.weight = (if (downbeat) 3.0 else 0.0) + (if (onBeat) 1.5 else 0.0) + n.dur.coerceAtMost(4.0) + (if (outer) 1.2 else 0.0)
        }
        val frac = 0.35 + 0.065 * (level - 1)
        mine.groupBy { it.section }.values.forEach { group ->
            val keep = ceil(group.size * frac).toInt().coerceAtLeast(1)
            val chosen = group.sortedByDescending { it.weight }.take(keep).toSet()
            group.forEach { if (it.n.start >= fromBeat && it.judge == null) it.required = it in chosen }
        }
    }

    // ---------- clock ----------
    fun tick(now: Long) {
        if (lastNanos == 0L) { lastNanos = now; return }
        val dt = (now - lastNanos) / 1e9
        lastNanos = now
        if (paused || finished) { frame++; return }

        // Wait mode: hold at the next note until all keys of that moment are played.
        val pending = if (cfg.wait) notes.firstOrNull { it.mine && it.required && it.judge == null && it.n.start >= regionStart - 1e-6 && it.n.start < regionEnd } else null
        var next = beat + dt / secPerBeat
        waiting = false
        if (pending != null && next >= pending.n.start) { next = pending.n.start; waiting = true }

        // Metronome: always during the count-in, during the song only if asked.
        val fb = floor(next).toInt()
        if (fb > lastClick && !waiting) {
            lastClick = fb
            if (next < regionStart || cfg.clicks) {
                val inBar = ((fb - floor(regionStart).toInt()) % song.barBeats.toInt().coerceAtLeast(1) + song.barBeats.toInt().coerceAtLeast(1)) % song.barBeats.toInt().coerceAtLeast(1)
                Sound.click(inBar == 0)
            }
        }

        // The other hand (and anything not hers) plays itself.
        notes.forEach { pn ->
            if (!pn.mine && !pn.autoDone && pn.n.start <= next && pn.n.start > beat - 0.01 && pn.n.start >= regionStart - 1e-6) {
                pn.autoDone = true
                if (cfg.otherHand) Sound.note(pn.n.pitch, (pn.n.dur * secPerBeat * 1000 * 0.95).toLong(), 64)
            }
        }
        beat = next

        // Misses.
        if (!cfg.wait) notes.forEach { pn ->
            if (pn.mine && pn.required && pn.judge == null && (beat - pn.n.start) * secPerBeat > 0.22) {
                pn.judge = Judge.Miss
                miss++; combo = 0
                lastJudge = Judge.Miss; lastJudgeNanos = now
                missed[pn.n.pitch] = (missed[pn.n.pitch] ?: 0) + 1
                secTot[pn.section] = (secTot[pn.section] ?: 0) + 1
            }
        }

        // Section boundaries drive dynamic difficulty.
        val si = song.sections.indexOfLast { beat >= it.start }
        if (si != sectionCursor) {
            if (sectionCursor >= 0 && cfg.dynamic && cfg.loop == null) {
                val acc = sectionAccuracy(sectionCursor)
                if (acc != null) {
                    val before = level
                    if (acc >= 0.9) level = (level + 1).coerceAtMost(10)
                    else if (acc < 0.6) level = (level - 1).coerceAtLeast(1)
                    if (level != before) { applyLevel(beat); levelChangedNanos = now }
                }
            }
            sectionCursor = si
        }

        // End of the loop or the song.
        if (beat >= regionEnd + 0.5) {
            if (cfg.loop != null) restartLoop(now) else finished = true
        }
        frame++
    }

    private fun sectionAccuracy(i: Int): Double? {
        val t = secTot[i] ?: return null
        if (t == 0) return null
        return (secHit[i] ?: 0.0) / t
    }

    private fun restartLoop(now: Long) {
        val inLoop = notes.filter { it.mine && it.required && it.n.start >= regionStart - 1e-6 && it.n.start < regionEnd }
        val total = inLoop.size.coerceAtLeast(1)
        val acc = inLoop.sumOf { when (it.judge) { Judge.Perfect -> 1.0; Judge.Good -> 0.75; Judge.Off -> 0.45; else -> 0.0 } } / total
        lastLoopAcc = acc
        loops++
        if (cfg.autoSpeed && acc >= 0.9 && speed < 1f) speed = (speed + 0.05f).coerceAtMost(1f)
        if (cfg.dynamic && acc >= 0.9 && level < 10) { level++; levelChangedNanos = now }
        notes.forEach { if (it.n.start >= regionStart - 1e-6 && it.n.start < regionEnd) { it.judge = null; it.autoDone = false } }
        applyLevel(Double.NEGATIVE_INFINITY)
        beat = regionStart - song.barBeats
        lastClick = floor(beat).toInt() - 1
    }

    // ---------- judging ----------
    fun onNote(e: NoteEvent) {
        if (!e.on || paused || finished) return
        val ago = if (lastNanos > 0) (lastNanos - e.nanos) / 1e9 else 0.0
        val at = beat - ago.coerceIn(-0.05, 0.3) / secPerBeat
        val window = if (cfg.wait) 0.35 else 0.25
        val cand = notes.filter { it.mine && it.judge == null && it.n.pitch == e.pitch && abs(it.n.start - at) * secPerBeat <= window }
            .minByOrNull { abs(it.n.start - at) }
        if (cand == null) {
            stray++; strayPitch = e.pitch; strayNanos = e.nanos
            return
        }
        val errS = (at - cand.n.start) * secPerBeat
        val j = when {
            cfg.wait -> Judge.Perfect
            abs(errS) <= 0.07 -> Judge.Perfect
            abs(errS) <= 0.14 -> Judge.Good
            else -> Judge.Off
        }
        cand.judge = j
        if (!cand.required) return // bonus note: lights up, does not count
        when (j) { Judge.Perfect -> perfect++; Judge.Good -> good++; Judge.Off -> off++; else -> {} }
        if (!cfg.wait) offsets += errS * 1000
        combo++; maxCombo = maxOf(maxCombo, combo)
        val mult = 1 + (combo.coerceAtMost(24) / 8)
        score += mult * when (j) { Judge.Perfect -> 100; Judge.Good -> 70; else -> 40 }
        lastJudge = j; lastJudgeNanos = System.nanoTime()
        secHit[cand.section] = (secHit[cand.section] ?: 0.0) + when (j) { Judge.Perfect -> 1.0; Judge.Good -> 0.75; else -> 0.45 }
        secTot[cand.section] = (secTot[cand.section] ?: 0) + 1
    }

    /** Pitches she should be playing right now (for glowing keys). */
    fun targetsNow(): List<PlayNote> {
        val near = notes.filter { it.mine && it.required && it.judge == null && it.n.start >= beat - 0.05 }
        val first = near.minOfOrNull { it.n.start } ?: return emptyList()
        if ((first - beat) * secPerBeat > 0.6) return emptyList()
        return near.filter { abs(it.n.start - first) < 1e-6 }
    }

    val judgedCount: Int get() = perfect + good + off + miss
    val accuracy: Double get() = if (judgedCount == 0) 1.0 else (perfect + 0.75 * good + 0.45 * off) / judgedCount
    val progress: Float get() = ((beat - regionStart) / (regionEnd - regionStart).coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    fun result(): GameResult {
        val secAcc = song.sections.indices.mapNotNull { i -> sectionAccuracy(i)?.let { song.sections[i].name + "#" + i to it } }.toMap()
        val lvl = level
        val mastery = (accuracy * (0.5 + 0.5 * lvl / 10.0) * (0.6 + 0.4 * speed) * (if (cfg.hands.size >= song.hands.size) 1.0 else 0.85) * (if (cfg.wait) 0.7 else 1.0)).coerceIn(0.0, 1.0)
        return GameResult(
            accuracy = accuracy, perfect = perfect, good = good, off = off, miss = miss, stray = stray, score = score, maxCombo = maxCombo,
            meanOffsetMs = if (offsets.size >= 4) offsets.average() else null, sectionAcc = secAcc, missedPitches = missed.toMap(),
            level = lvl, speed = speed, loops = loops, mastery = mastery,
        )
    }

    fun restart() {
        notes.forEach { it.judge = null; it.autoDone = false }
        score = 0; combo = 0; maxCombo = 0; perfect = 0; good = 0; off = 0; miss = 0; stray = 0
        offsets.clear(); missed.clear(); secHit.clear(); secTot.clear()
        beat = regionStart - song.barBeats
        lastClick = floor(beat).toInt() - 1
        sectionCursor = -1
        lastJudge = null
        level = cfg.level.coerceIn(1, 10); speed = cfg.speed
        applyLevel(Double.NEGATIVE_INFINITY)
        finished = false; paused = false
    }
}
