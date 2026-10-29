package app.mochipiano.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.mochipiano.course.Clef
import app.mochipiano.data.Store
import app.mochipiano.music.Hand
import app.mochipiano.music.ImportException
import app.mochipiano.music.MidiFileImport
import app.mochipiano.music.MusicXmlImport
import app.mochipiano.music.Song
import app.mochipiano.ui.components.Hairline
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Label
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.StaffNote
import app.mochipiano.ui.components.StaffView
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun displayName(ctx: Context, uri: Uri): String {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0) ?: "score"
    }
    return uri.lastPathSegment ?: "score"
}

fun parseScore(bytes: ByteArray, name: String): Song {
    val lower = name.lowercase()
    val isMidi = lower.endsWith(".mid") || lower.endsWith(".midi") || (bytes.size > 4 && String(bytes, 0, 4, Charsets.US_ASCII) == "MThd")
    return if (isMidi) MidiFileImport.parse(bytes, name) else MusicXmlImport.parse(bytes, name)
}

@Composable
fun ImportScreen(initial: Uri?, onBack: () -> Unit, onAdded: (String) -> Unit) {
    val ctx = LocalContext.current
    var song by remember { mutableStateOf<Song?>(null) }
    var file by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(initial) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pending = uri }

    LaunchedEffect(pending) {
        val uri = pending ?: return@LaunchedEffect
        busy = true; error = null; song = null
        try {
            val (s, n, size) = withContext(Dispatchers.IO) {
                val n = displayName(ctx, uri)
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw ImportException("Could not open the file.")
                Triple(parseScore(bytes, n), n, bytes.size)
            }
            song = s; title = s.title; file = n to size
        } catch (e: ImportException) {
            error = e.message
        } catch (e: Exception) {
            error = "Something in this file could not be read. MuseScore's \"Export → MusicXML\" works best."
        }
        busy = false
        pending = null
    }

    Page {
        TopBar(onBack, center = { Text("Import sheet music", style = T.title, color = MC.Ink, modifier = Modifier.weight(1f)) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceCard("MusicXML", ".mxl or .musicxml from MuseScore", Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
            SourceCard("MIDI", ".mid files", Modifier.weight(1f)) { picker.launch(arrayOf("audio/midi", "audio/x-midi", "application/x-midi", "*/*")) }
        }
        Text("Photos of printed pages are coming later. For now, look up the piece on MuseScore or Mutopia and export it as MusicXML.", style = T.small, color = MC.InkSoft)

        if (busy) Text("Reading the score…", style = T.bodyStrong, color = MC.InkSoft)
        error?.let { MCard(color = MC.RightSoft) { Text(it, style = T.bodyStrong, color = MC.RightText) } }

        val s = song
        if (s != null) {
            file?.let { (n, size) ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MC.MintSoft).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.Check, null, tint = MC.MintText, modifier = Modifier.size(18.dp))
                    Text("$n · ${size / 1024 + 1} KB", style = T.mono, color = MC.MintText, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("Read OK", style = T.small, color = MC.MintText)
                }
            }
            val preview = (s.notes.filter { it.hand == Hand.R }.ifEmpty { s.notes }).let { list ->
                list.groupBy { it.start }.toSortedMap().values.map { chord -> chord.maxBy { it.pitch } }.take(10)
            }
            val clef = if ((preview.map { it.pitch }.average().takeIf { !it.isNaN() } ?: 60.0) < 57) Clef.Bass else Clef.Treble
            MCard(pad = 12.dp) {
                StaffView(preview.map { StaffNote(it.pitch, it.dur.coerceIn(0.5, 4.0), color = if (it.hand == Hand.R) MC.Right else MC.Left) }, clef, aspect = 3.2f)
                Text("First notes · ${s.bars} bars", style = T.small, color = MC.InkSoft)
            }
            Label("Title")
            BasicTextField(
                title, { title = it }, singleLine = true, textStyle = T.bodyStrong.copy(color = MC.Ink), cursorBrush = SolidColor(MC.Right),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MC.Surface).border(2.dp, MC.Mist, RoundedCornerShape(14.dp)).padding(14.dp),
            )
            MCard(pad = 16.dp) {
                Fact("Composer", s.composer.ifBlank { "—" })
                Hairline(); Fact("Time · key", "${s.timeSig}" + if (s.keyName.isNotBlank()) " · ${s.keyName}" else "")
                Hairline(); Fact("Tempo", "♩ = ${s.bpm.toInt()}")
                Hairline(); Fact("Right hand", "${s.count(Hand.R)} notes")
                Hairline(); Fact("Left hand", "${s.count(Hand.L)} notes")
                Hairline(); Fact("Sections", s.sections.joinToString(" · ") { it.name })
                Hairline(); Fact("Difficulty", "Level ${s.difficulty} of 10")
            }
            PrimaryButton("Add to Songs", {
                val final = s.copy(title = title.trim().ifEmpty { s.title })
                Store.addSong(final)
                onAdded(final.id)
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SourceCard(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(92.dp).clip(RoundedCornerShape(18.dp)).background(MC.Surface).clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Ic.File, null, tint = MC.Ink, modifier = Modifier.size(22.dp))
        Text(title, style = T.bodyStrong, color = MC.Ink)
        Text(sub, style = T.small, color = MC.InkSoft, maxLines = 1)
    }
}

@Composable
private fun Fact(k: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, style = T.body, color = MC.InkSoft)
        Text(v, style = T.bodyStrong, color = MC.Ink)
    }
}
