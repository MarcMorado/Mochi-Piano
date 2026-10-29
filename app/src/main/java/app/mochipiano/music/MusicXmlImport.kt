package app.mochipiano.music

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ImportException(message: String) : Exception(message)

/**
 * Reads MusicXML (score-partwise), plain (.musicxml/.xml) or compressed (.mxl).
 * Hands: a two-staff part gives staff 1 to the right hand and staff 2 to the left;
 * two single-staff parts give the first to the right hand and the second to the left.
 */
object MusicXmlImport {

    fun parse(bytes: ByteArray, fileName: String): Song {
        val xml = if (isZip(bytes)) unzipScore(bytes) else bytes
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
        }
        val doc = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(stripDoctype(xml)))
        } catch (e: Exception) {
            throw ImportException("This file is not readable MusicXML.")
        }
        val root = doc.documentElement
        if (root.tagName == "score-timewise") throw ImportException("Timewise MusicXML is not supported yet. Export it as partwise from MuseScore.")
        if (root.tagName != "score-partwise") throw ImportException("This file is not a MusicXML score.")

        val title = text(child(child(root, "work"), "work-title")) ?: text(child(root, "movement-title"))
            ?: fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        val composer = children(child(root, "identification"), "creator")
            .firstOrNull { it.getAttribute("type") == "composer" }?.textContent?.trim() ?: ""

        val parts = children(root, "part")
        if (parts.isEmpty()) throw ImportException("The score has no parts.")

        val all = mutableListOf<SNote>()
        val rehearsals = mutableListOf<Pair<Double, String>>()
        var tempo: Double? = null
        var barBeats = 4.0
        var timeSig = "4/4"
        var keyName = ""

        parts.take(2).forEachIndexed { partIndex, part ->
            var divisions = 1.0
            var measureStart = 0.0
            var staves = 1
            var bar = barBeats
            val open = HashMap<String, Int>() // tie key -> index in all
            children(part, "measure").forEach { m ->
                var cursor = 0.0
                var maxPos = 0.0
                var lastStart = 0.0
                for (node in elements(m)) {
                    when (node.tagName) {
                        "attributes" -> {
                            text(child(node, "divisions"))?.toDoubleOrNull()?.let { divisions = it }
                            text(child(node, "staves"))?.toIntOrNull()?.let { staves = it }
                            child(node, "time")?.let { t ->
                                val b = text(child(t, "beats"))?.substringBefore('+')?.toIntOrNull()
                                val bt = text(child(t, "beat-type"))?.toIntOrNull()
                                if (b != null && bt != null) {
                                    bar = b * 4.0 / bt
                                    if (partIndex == 0 && measureStart == 0.0) { barBeats = bar; timeSig = "$b/$bt" }
                                }
                            }
                            child(node, "key")?.let { k ->
                                val f = text(child(k, "fifths"))?.toIntOrNull()
                                if (f != null && keyName.isEmpty()) keyName = Song.keyName(f, text(child(k, "mode")) == "minor")
                            }
                        }
                        "direction" -> {
                            child(node, "sound")?.getAttribute("tempo")?.toDoubleOrNull()?.let { if (tempo == null) tempo = it }
                            val dt = child(node, "direction-type")
                            child(dt, "metronome")?.let { mm ->
                                val per = text(child(mm, "per-minute"))?.toDoubleOrNull()
                                val unit = text(child(mm, "beat-unit")) ?: "quarter"
                                val dotted = child(mm, "beat-unit-dot") != null
                                if (per != null && tempo == null) tempo = per * unitQuarters(unit) * (if (dotted) 1.5 else 1.0)
                            }
                            text(child(dt, "rehearsal"))?.let { if (partIndex == 0) rehearsals += (measureStart + cursor) to it }
                        }
                        "sound" -> node.getAttribute("tempo").toDoubleOrNull()?.let { if (tempo == null) tempo = it }
                        "backup" -> cursor -= (text(child(node, "duration"))?.toDoubleOrNull() ?: 0.0) / divisions
                        "forward" -> { cursor += (text(child(node, "duration"))?.toDoubleOrNull() ?: 0.0) / divisions; maxPos = maxOf(maxPos, cursor) }
                        "note" -> {
                            if (child(node, "grace") != null || child(node, "cue") != null) continue
                            val dur = (text(child(node, "duration"))?.toDoubleOrNull() ?: 0.0) / divisions
                            val isChord = child(node, "chord") != null
                            val start = if (isChord) lastStart else cursor
                            if (!isChord) { lastStart = cursor; cursor += dur; maxPos = maxOf(maxPos, cursor) }
                            val pitchEl = child(node, "pitch") ?: continue // rests and unpitched
                            val step = text(child(pitchEl, "step"))?.firstOrNull() ?: continue
                            val oct = text(child(pitchEl, "octave"))?.toIntOrNull() ?: continue
                            val alter = text(child(pitchEl, "alter"))?.toDoubleOrNull()?.toInt() ?: 0
                            val p = Pitch.of(step, oct, alter)
                            val staff = text(child(node, "staff"))?.toIntOrNull() ?: 1
                            val hand = if (staves >= 2) (if (staff >= 2) Hand.L else Hand.R) else (if (partIndex == 0) Hand.R else Hand.L)
                            val finger = node.getElementsByTagName("fingering").let { if (it.length > 0) it.item(0).textContent.trim().take(1).toIntOrNull() ?: 0 else 0 }
                            val ties = children(node, "tie").map { it.getAttribute("type") }
                            val key = "$hand:$p"
                            val abs = measureStart + start
                            if ("stop" in ties && open.containsKey(key)) {
                                val idx = open.getValue(key)
                                val prev = all[idx]
                                all[idx] = prev.copy(dur = prev.dur + dur)
                                if ("start" !in ties) open.remove(key)
                            } else {
                                all += SNote(abs, dur, p, hand, finger)
                                if ("start" in ties) open[key] = all.size - 1
                            }
                        }
                    }
                }
                // Pickup bars are shorter than the time signature; use what was actually written.
                val implicit = m.getAttribute("implicit") == "yes"
                measureStart += if (implicit || (maxPos < bar - 1e-6 && maxPos > 0 && measureStart == 0.0)) maxPos.coerceAtLeast(0.25) else maxOf(maxPos, bar)
            }
        }

        if (all.isEmpty()) throw ImportException("No notes found in this score.")
        val notes = all.filter { it.dur > 0 }.sortedWith(compareBy({ it.start }, { it.pitch }))
        val sections = if (rehearsals.size >= 2) {
            val sorted = rehearsals.sortedBy { it.first }.let { if (it.first().first > 0.01) listOf(0.0 to "Intro") + it else it }
            val len = notes.maxOf { it.start + it.dur }
            sorted.mapIndexed { i, (s, n) -> Section(n, s, sorted.getOrNull(i + 1)?.first ?: len) }
        } else Song.autoSections(notes, barBeats)

        return Song(
            id = "imp-" + System.currentTimeMillis().toString(36),
            title = title.trim().ifEmpty { "Untitled" }, composer = composer,
            bpm = (tempo ?: 90.0).coerceIn(30.0, 240.0), barBeats = barBeats, timeSig = timeSig,
            keyName = keyName, notes = notes, sections = sections, imported = true,
            coverSeed = (title.hashCode() and 0xFFFF),
        )
    }

    private fun unitQuarters(unit: String) = when (unit) {
        "whole" -> 4.0; "half" -> 2.0; "quarter" -> 1.0; "eighth" -> 0.5; "16th" -> 0.25; else -> 1.0
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    private fun unzipScore(bytes: ByteArray): ByteArray {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (!e.isDirectory) files[e.name] = z.readBytes()
            }
        }
        val container = files["META-INF/container.xml"]?.toString(Charsets.UTF_8)
        val path = container?.let { Regex("full-path=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        return path?.let { files[it] }
            ?: files.entries.firstOrNull { !it.key.startsWith("META-INF") && (it.key.endsWith(".xml") || it.key.endsWith(".musicxml")) }?.value
            ?: throw ImportException("This .mxl file has no score inside.")
    }

    /** DOCTYPE lines point at remote DTDs; drop them so parsing never touches the network. */
    private fun stripDoctype(b: ByteArray): ByteArray {
        val s = b.toString(Charsets.UTF_8)
        return Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE).replace(s, "").toByteArray(Charsets.UTF_8)
    }

    private fun elements(e: Element): List<Element> {
        val out = ArrayList<Element>()
        val nl = e.childNodes
        for (i in 0 until nl.length) { val n = nl.item(i); if (n.nodeType == Node.ELEMENT_NODE) out += n as Element }
        return out
    }

    private fun child(e: Element?, name: String): Element? = e?.let { elements(it).firstOrNull { c -> c.tagName == name } }
    private fun children(e: Element?, name: String): List<Element> = e?.let { elements(it).filter { c -> c.tagName == name } } ?: emptyList()
    private fun text(e: Element?): String? = e?.textContent?.trim()?.takeIf { it.isNotEmpty() }
}
