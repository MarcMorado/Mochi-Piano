package app.mochipiano.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mochipiano.music.BuiltInSongs
import app.mochipiano.music.Song
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Leitner-style box: 0 = new … 5 = mastered. */
data class Box(val b: Int = 0, val c: Int = 0, val t: Int = 0) {
    fun bump(ok: Boolean, fast: Boolean) = Box(
        b = if (ok) (if (fast) (b + 1).coerceAtMost(5) else b) else (b - 2).coerceAtLeast(0),
        c = c + if (ok) 1 else 0, t = t + 1,
    )
}

data class LessonRec(val stars: Int, val acc: Double, val n: Int, val day: String)

data class SongStat(
    val plays: Int = 0,
    val best: Double = 0.0,
    val mastery: Double = 0.0,
    val speed: Float = 0.8f,
    val level: Int = 5,
    val hands: String = "",
    val sectionAcc: Map<String, Double> = emptyMap(),
)

data class Settings(
    val solfege: Boolean = false,
    val otherHandForMe: Boolean = true,
    val letters: Boolean = true,
)

data class AppData(
    val name: String = "Krizia",
    val xp: Int = 0,
    val streak: Int = 0,
    val lastDay: String? = null,
    val lessons: Map<String, LessonRec> = emptyMap(),
    val notes: Map<String, Box> = emptyMap(),
    val keys: Map<String, Box> = emptyMap(),
    val songs: Map<String, SongStat> = emptyMap(),
    val days: Map<String, Int> = emptyMap(),
    val rhythmBpm: Int = 70,
    val lastBtAddress: String? = null,
    val lastBtName: String? = null,
    val settings: Settings = Settings(),
    val planDay: String? = null,
    val planDone: Set<String> = emptySet(),
)

/** All app state, saved as JSON in the app's private files. Imported songs live one per file. */
object Store {
    var data by mutableStateOf(AppData())
        private set
    val imported = mutableStateListOf<Song>()
    private var dir: File? = null

    fun load(ctx: Context) {
        val d = ctx.filesDir
        dir = d
        val f = File(d, "state.json")
        if (f.exists()) data = try { decode(JSONObject(f.readText())) } catch (_: Exception) { AppData() }
        val sd = File(d, "songs").apply { mkdirs() }
        imported.clear()
        sd.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.forEach { sf ->
            try { imported += Song.fromJson(JSONObject(sf.readText())) } catch (_: Exception) {}
        }
    }

    fun update(f: (AppData) -> AppData) {
        data = f(data)
        save()
    }

    private fun save() {
        val d = dir ?: return
        try { File(d, "state.json").writeText(encode(data).toString()) } catch (_: Exception) {}
    }

    fun addSong(s: Song) {
        val d = dir ?: return
        File(File(d, "songs"), s.id + ".json").writeText(s.toJson().toString())
        imported.add(0, s)
    }

    fun deleteSong(id: String) {
        val d = dir ?: return
        File(File(d, "songs"), "$id.json").delete()
        imported.removeAll { it.id == id }
    }

    fun allSongs(): List<Song> = BuiltInSongs.all + imported
    fun song(id: String): Song? = BuiltInSongs.byId(id) ?: imported.firstOrNull { it.id == id }
    fun songStat(id: String) = data.songs[id] ?: SongStat()

    fun today(): String = LocalDate.now().toString()

    /** Count today towards the streak and add practice minutes. */
    fun markPractice(minutes: Int, xp: Int) = update { a ->
        val t = today()
        val streak = when (a.lastDay) {
            t -> a.streak
            LocalDate.now().minusDays(1).toString() -> a.streak + 1
            else -> 1
        }
        val days = (a.days + (t to ((a.days[t] ?: 0) + minutes.coerceAtLeast(1)))).entries
            .sortedBy { it.key }.takeLast(90).associate { it.key to it.value }
        a.copy(streak = streak, lastDay = t, days = days, xp = a.xp + xp)
    }

    fun planMark(key: String) = update { a ->
        val t = today()
        if (a.planDay == t) a.copy(planDone = a.planDone + key) else a.copy(planDay = t, planDone = setOf(key))
    }

    // ---------- JSON ----------
    private fun boxes(m: Map<String, Box>) = JSONObject().apply { m.forEach { (k, v) -> put(k, "${v.b},${v.c},${v.t}") } }
    private fun boxesOf(o: JSONObject?): Map<String, Box> {
        o ?: return emptyMap()
        return o.keys().asSequence().associateWith { k -> o.getString(k).split(',').map { it.toInt() }.let { Box(it[0], it[1], it[2]) } }
    }

    private fun encode(a: AppData) = JSONObject().apply {
        put("name", a.name); put("xp", a.xp); put("streak", a.streak); put("lastDay", a.lastDay ?: "")
        put("lessons", JSONObject().apply { a.lessons.forEach { (k, v) -> put(k, JSONObject().put("s", v.stars).put("a", v.acc).put("n", v.n).put("d", v.day)) } })
        put("notes", boxes(a.notes)); put("keys", boxes(a.keys))
        put("songs", JSONObject().apply {
            a.songs.forEach { (k, v) ->
                put(k, JSONObject().put("p", v.plays).put("b", v.best).put("m", v.mastery).put("s", v.speed.toDouble()).put("l", v.level).put("h", v.hands)
                    .put("sec", JSONObject().apply { v.sectionAcc.forEach { (sk, sv) -> put(sk, sv) } }))
            }
        })
        put("days", JSONObject().apply { a.days.forEach { (k, v) -> put(k, v) } })
        put("rhythmBpm", a.rhythmBpm); put("btAddr", a.lastBtAddress ?: ""); put("btName", a.lastBtName ?: "")
        put("solfege", a.settings.solfege); put("otherHand", a.settings.otherHandForMe); put("letters", a.settings.letters)
        put("planDay", a.planDay ?: ""); put("planDone", a.planDone.joinToString(","))
    }

    private fun decode(o: JSONObject): AppData {
        val lessons = o.optJSONObject("lessons")?.let { lo -> lo.keys().asSequence().associateWith { k -> lo.getJSONObject(k).let { LessonRec(it.optInt("s"), it.optDouble("a", 0.0), it.optInt("n"), it.optString("d")) } } } ?: emptyMap()
        val songs = o.optJSONObject("songs")?.let { so ->
            so.keys().asSequence().associateWith { k ->
                val v = so.getJSONObject(k)
                val sec = v.optJSONObject("sec")
                SongStat(v.optInt("p"), v.optDouble("b", 0.0), v.optDouble("m", 0.0), v.optDouble("s", 0.8).toFloat(), v.optInt("l", 5), v.optString("h"),
                    sec?.let { s -> s.keys().asSequence().associateWith { s.getDouble(it) } } ?: emptyMap())
            }
        } ?: emptyMap()
        val days = o.optJSONObject("days")?.let { d -> d.keys().asSequence().associateWith { d.getInt(it) } } ?: emptyMap()
        return AppData(
            name = o.optString("name", "Krizia").ifBlank { "Krizia" }, xp = o.optInt("xp"), streak = o.optInt("streak"),
            lastDay = o.optString("lastDay").ifEmpty { null }, lessons = lessons,
            notes = boxesOf(o.optJSONObject("notes")), keys = boxesOf(o.optJSONObject("keys")), songs = songs, days = days,
            rhythmBpm = o.optInt("rhythmBpm", 70), lastBtAddress = o.optString("btAddr").ifEmpty { null }, lastBtName = o.optString("btName").ifEmpty { null },
            settings = Settings(o.optBoolean("solfege", false), o.optBoolean("otherHand", true), o.optBoolean("letters", true)),
            planDay = o.optString("planDay").ifEmpty { null },
            planDone = o.optString("planDone").split(',').filter { it.isNotBlank() }.toSet(),
        )
    }
}
