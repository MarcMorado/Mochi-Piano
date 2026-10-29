package app.mochipiano.course

import app.mochipiano.data.AppData
import app.mochipiano.data.Store
import app.mochipiano.music.BuiltInSongs
import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch
import app.mochipiano.music.Song
import kotlin.math.roundToInt

/** Practice runs built on the fly from what she has learned so far, and the daily plan. */
object Practice {
    private val RH = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)
    private val LH = mapOf(48 to 5, 50 to 4, 52 to 3, 53 to 2, 55 to 1)

    fun done(d: AppData, id: String) = d.lessons.containsKey(id)

    fun lessonOpen(d: AppData, l: Lesson): Boolean {
        val i = Course.lessons.indexOf(l)
        return i == 0 || done(d, l.id) || done(d, Course.lessons[i - 1].id)
    }

    fun nextLesson(d: AppData): Lesson? = Course.lessons.firstOrNull { !done(d, it.id) }

    fun songOpen(d: AppData, s: Song): Boolean {
        if (s.imported) return true
        val l = s.lessonId?.let { Course.byId(it) } ?: return true
        return done(d, l.id) || lessonOpen(d, l)
    }

    fun treblePool(d: AppData): List<Int> = when {
        done(d, "treble2") -> listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79)
        done(d, "treble1") -> listOf(60, 62, 64, 65, 67, 69)
        done(d, "staff") -> listOf(60, 62, 64)
        else -> emptyList()
    }

    fun bassPool(d: AppData): List<Int> = if (done(d, "bass")) listOf(48, 50, 52, 53, 55, 57, 59, 60) else emptyList()

    fun letterPool(d: AppData): List<Char> = when {
        done(d, "fgab") || done(d, "abc") -> Pitch.LETTERS
        done(d, "cde") -> listOf('C', 'D', 'E')
        done(d, "findc") -> listOf('C')
        else -> emptyList()
    }

    fun rhythmLevel(d: AppData) = when { done(d, "rest") -> 3; done(d, "long") -> 2; done(d, "beat") -> 1; else -> 0 }

    fun warmup(d: AppData): Lesson {
        val steps = mutableListOf<Step>()
        if (done(d, "cpos")) {
            steps += Step.Play("Five-finger stairs", "Right hand in C position. Up and down, smooth and even.", listOf(60, 62, 64, 65, 67, 65, 64, 62, 60).map { listOf(it) }, 60..72, RH, rounds = 2)
            if (done(d, "left")) steps += Step.Play("Now the left hand", "Left pinky on the C below Middle C. Same stairs.", listOf(48, 50, 52, 53, 55, 53, 52, 50, 48).map { listOf(it) }, 48..60, LH, Hand.L, rounds = 2)
            if (done(d, "chord")) steps += Step.Play("Chord bounce", "Both C chords together, four gentle presses.", List(4) { listOf(48, 52, 55, 60, 64, 67) }, 48..71)
        } else if (done(d, "findc")) {
            steps += Step.Play("Three Cs", "Low C, Middle C, high C, and back.", listOf(48, 60, 72, 60).map { listOf(it) }, 48..72)
        } else {
            steps += Step.Play("Wiggle hello", "Thumb on the lit key, then one finger per key.", listOf(60, 62, 64, 65, 67, 65, 64, 62, 60).map { listOf(it) }, 60..72, RH)
        }
        return Lesson("p-warm", 0, "Warm-up", steps)
    }

    fun noteNinja(d: AppData, clef: String, count: Int = 12): Lesson {
        val t = treblePool(d); val b = bassPool(d)
        val steps = mutableListOf<Step>()
        if ((clef == "treble" || clef == "mix") && t.isNotEmpty()) steps += Step.Read("Treble notes", Clef.Treble, t, if (clef == "mix" && b.isNotEmpty()) count / 2 else count)
        if ((clef == "bass" || clef == "mix") && b.isNotEmpty()) steps += Step.Read("Bass notes", Clef.Bass, b, if (clef == "mix") count / 2 else count)
        return Lesson("p-ninja", 0, "Note Ninja", steps)
    }

    fun keyFinder(d: AppData, count: Int = 10): Lesson =
        Lesson("p-keys", 0, "Key Finder", listOf(Step.Find("Key Finder", letterPool(d).map { c -> Target("Play any $c") { Pitch.letter(it) == c && !Pitch.isSharp(it) } }, count)))

    fun echo(d: AppData): Lesson {
        val pool = if (done(d, "fgab")) listOf(60, 62, 64, 65, 67, 69, 71, 72) else listOf(60, 62, 64, 65, 67)
        return Lesson("p-echo", 0, "Echo", listOf(Step.Echo("Play it back", pool, 6)))
    }

    fun rhythm(d: AppData): Lesson {
        val lv = rhythmLevel(d).coerceAtLeast(1)
        val out = mutableListOf<Double>()
        repeat(2) {
            var left = 4.0
            while (left > 0) {
                val opts = mutableListOf(1.0, 1.0)
                if (lv >= 2) { if (left >= 2) opts += listOf(2.0, 2.0); if (left == 4.0) opts += 4.0 }
                if (lv >= 3) { opts += -1.0; if (left >= 2) opts += -2.0 }
                var x = opts.random()
                if (kotlin.math.abs(x) > left) x = 1.0
                out += x; left -= kotlin.math.abs(x)
            }
        }
        if (out[0] < 0) out[0] = -out[0]
        return Lesson("p-rhythm", 0, "Rhythm Tap", listOf(Step.Rhythm("Rhythm Tap", "A fresh pattern from the notes you know. The tempo follows how you do.", out, d.rhythmBpm)))
    }

    // ---------------------------------------------------------------- daily plan

    enum class Kind { Warm, Lesson, Review, Song }
    data class Item(val key: String, val kind: Kind, val title: String, val meta: String, val minutes: Int, val ref: String = "")

    fun plan(d: AppData): List<Item> {
        val items = mutableListOf<Item>()
        items += Item("warm", Kind.Warm, "Warm-up", if (done(d, "cpos")) "Five-finger stairs" else "Wake up your fingers", 2)
        val next = nextLesson(d)
        if (next != null) items += Item("lesson:${next.id}", Kind.Lesson, "Lesson ${next.number} · ${next.title}", "New", 5, next.id)
        when {
            treblePool(d).isNotEmpty() -> {
                val weak = (treblePool(d).map { "t:$it" } + bassPool(d).map { "b:$it" }).sortedBy { d.notes[it]?.b ?: 0 }.take(2)
                items += Item("review", Kind.Review, "Note Ninja · 10 notes", "Extra practice on " + weak.joinToString { Pitch.display(Pitch.letter(it.substring(2).toInt()), d.settings.solfege) }, 2, if (bassPool(d).isNotEmpty()) "mix" else "treble")
            }
            letterPool(d).isNotEmpty() -> items += Item("review", Kind.Review, "Key Finder · 8 keys", "Find notes on the piano", 2, "keys")
        }
        val songs = Store.allSongs().filter { s -> songOpen(d, s) && (s.imported || done(d, s.lessonId ?: "")) }
        songs.minByOrNull { (d.songs[it.id]?.mastery ?: 0.0) + (d.songs[it.id]?.plays ?: 0) * 0.01 }?.let { s ->
            val st = d.songs[s.id]
            items += Item("song:${s.id}", Kind.Song, "Play ${s.title}", if (st == null) "First time" else "Mastery ${(st.mastery * 100).roundToInt()}% · ${(st.speed * 100).roundToInt()}% speed", 3, s.id)
        }
        return items
    }

    fun planReason(d: AppData): String {
        fun acc(keys: List<String>): Double? { val c = keys.sumOf { d.notes[it]?.c ?: 0 }; val t = keys.sumOf { d.notes[it]?.t ?: 0 }; return if (t >= 6) c.toDouble() / t else null }
        val bass = acc(bassPool(d).map { "b:$it" })
        val treble = acc(treblePool(d).map { "t:$it" })
        val weakLesson = d.lessons.entries.filter { it.value.acc < 0.7 && it.value.day >= Store.today().let { t -> java.time.LocalDate.parse(t).minusDays(3).toString() } }.maxByOrNull { it.value.day }
        return when {
            weakLesson != null -> "\"${Course.byId(weakLesson.key)?.title}\" was tricky, so its notes come up more in today's cards."
            bass != null && bass < 0.75 && (treble == null || bass < treble) -> "Bass notes are still settling in (${(bass * 100).roundToInt()}% right), so the cards lean that way."
            treble != null && treble < 0.75 -> "Staff notes are at ${(treble * 100).roundToInt()}%. A few cards a day makes them stick."
            d.streak >= 2 && d.lastDay == Store.today() -> "Day ${d.streak} in a row. Lovely."
            d.streak >= 2 -> "${d.streak}-day streak going. Keep it alive today."
            else -> "Short and sweet. Ten minutes a day is plenty."
        }
    }

    @Suppress("unused")
    private val keepSongs = BuiltInSongs.all
}
