package app.mochipiano.music

/**
 * Standard MIDI File reader (format 0 and 1, ticks-per-quarter timing).
 * Hands: with two or more note tracks, the higher one is the right hand and the next the left.
 * With a single track, notes from Middle C up go to the right hand unless the whole part sits high.
 */
object MidiFileImport {

    private class Raw(val start: Long, var end: Long, val pitch: Int, val track: Int, val channel: Int)

    fun parse(b: ByteArray, fileName: String): Song {
        var pos = 0
        fun u8(): Int = b[pos++].toInt() and 0xFF
        fun u16(): Int = (u8() shl 8) or u8()
        fun u32(): Long = (u8().toLong() shl 24) or (u8().toLong() shl 16) or (u8().toLong() shl 8) or u8().toLong()
        fun tag(): String { val s = String(b, pos, 4, Charsets.US_ASCII); pos += 4; return s }

        if (b.size < 14) throw ImportException("This MIDI file is empty.")
        if (tag() != "MThd") throw ImportException("This is not a MIDI file.")
        val hlen = u32().toInt()
        val hstart = pos
        u16() // format
        val ntrks = u16()
        val division = u16()
        pos = hstart + hlen
        if (division and 0x8000 != 0) throw ImportException("SMPTE-timed MIDI files are not supported.")
        val tpq = division.toDouble()

        val raws = mutableListOf<Raw>()
        var tempoUs: Long? = null
        var barBeats = 4.0
        var timeSig = "4/4"
        var keyName = ""
        var trackTitle: String? = null

        for (t in 0 until ntrks) {
            if (pos + 8 > b.size) break
            val id = tag()
            val len = u32().toInt()
            val end = (pos + len).coerceAtMost(b.size)
            if (id != "MTrk") { pos = end; continue }
            var tick = 0L
            var running = 0
            val open = HashMap<Int, ArrayDeque<Raw>>()
            while (pos < end) {
                var delta = 0L
                while (true) { val c = u8(); delta = (delta shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) break }
                tick += delta
                var status = b[pos].toInt() and 0xFF
                if (status < 0x80) status = running else { pos++; if (status < 0xF0) running = status }
                when {
                    status == 0xFF -> {
                        val type = u8()
                        var l = 0
                        while (true) { val c = u8(); l = (l shl 7) or (c and 0x7F); if (c and 0x80 == 0) break }
                        val data = b.copyOfRange(pos, (pos + l).coerceAtMost(b.size))
                        pos += l
                        when (type) {
                            0x51 -> if (tempoUs == null && data.size >= 3) tempoUs = ((data[0].toLong() and 0xFF) shl 16) or ((data[1].toLong() and 0xFF) shl 8) or (data[2].toLong() and 0xFF)
                            0x58 -> if (tick == 0L && data.size >= 2) { val nn = data[0].toInt(); val dd = 1 shl data[1].toInt(); barBeats = nn * 4.0 / dd; timeSig = "$nn/$dd" }
                            0x59 -> if (keyName.isEmpty() && data.size >= 2) keyName = Song.keyName(data[0].toInt(), data[1].toInt() == 1)
                            0x03 -> if (trackTitle == null && t == 0) trackTitle = String(data, Charsets.UTF_8).trim().ifEmpty { null }
                        }
                    }
                    status == 0xF0 || status == 0xF7 -> {
                        var l = 0
                        while (true) { val c = u8(); l = (l shl 7) or (c and 0x7F); if (c and 0x80 == 0) break }
                        pos += l
                    }
                    else -> {
                        val type = status and 0xF0
                        val ch = status and 0x0F
                        val d1 = u8()
                        val d2 = if (type == 0xC0 || type == 0xD0) 0 else u8()
                        val key = ch * 128 + d1
                        if (ch == 9) continue // drums
                        if (type == 0x90 && d2 > 0) {
                            val r = Raw(tick, tick, d1, t, ch)
                            raws += r
                            open.getOrPut(key) { ArrayDeque() }.addLast(r)
                        } else if (type == 0x80 || (type == 0x90 && d2 == 0)) {
                            open[key]?.removeFirstOrNull()?.let { it.end = tick }
                        }
                    }
                }
            }
            open.values.forEach { q -> q.forEach { if (it.end == it.start) it.end = tick } }
            pos = end
        }

        val usable = raws.filter { it.end > it.start }
        if (usable.isEmpty()) throw ImportException("No notes found in this MIDI file.")

        // Decide hands.
        val byTrack = usable.groupBy { it.track }.filterValues { it.size >= 4 }
        val handOf: (Raw) -> Hand = if (byTrack.size >= 2) {
            val ranked = byTrack.entries.sortedByDescending { it.value.size }.take(2).sortedByDescending { e -> e.value.map { it.pitch }.average() }
            val right = ranked[0].key
            val left = ranked[1].key
            ({ r: Raw -> if (r.track == right) Hand.R else if (r.track == left) Hand.L else if (r.pitch >= 60) Hand.R else Hand.L })
        } else {
            val min = usable.minOf { it.pitch }
            if (min >= 55) ({ _: Raw -> Hand.R }) else ({ r: Raw -> if (r.pitch >= 60) Hand.R else Hand.L })
        }

        val notes = usable.map { r ->
            val s = quant(r.start / tpq)
            val e = quant(r.end / tpq)
            SNote(s, (e - s).coerceAtLeast(0.125), r.pitch, handOf(r))
        }.sortedWith(compareBy({ it.start }, { it.pitch }))

        // Trim leading silence to the bar.
        val first = notes.first().start
        val shift = Math.floor(first / barBeats) * barBeats
        val shifted = if (shift > 0) notes.map { it.copy(start = it.start - shift) } else notes

        val title = trackTitle?.takeIf { it.length in 2..60 && !it.startsWith("Track", true) }
            ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        val bpm = tempoUs?.let { 60_000_000.0 / it } ?: 100.0
        return Song(
            id = "imp-" + System.currentTimeMillis().toString(36),
            title = title, composer = "", bpm = bpm.coerceIn(30.0, 240.0), barBeats = barBeats, timeSig = timeSig,
            keyName = keyName, notes = shifted, sections = Song.autoSections(shifted, barBeats), imported = true,
            coverSeed = title.hashCode() and 0xFFFF,
        )
    }

    /** Snap to a 1/24-beat grid so human-played files still line up. */
    private fun quant(beats: Double): Double = Math.round(beats * 24) / 24.0
}
