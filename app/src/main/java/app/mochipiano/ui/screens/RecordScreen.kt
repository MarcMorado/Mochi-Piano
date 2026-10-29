package app.mochipiano.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.mochipiano.course.Mood
import app.mochipiano.midi.MidiHub
import app.mochipiano.record.Capture
import app.mochipiano.record.Recordings
import app.mochipiano.record.Take
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Mochi
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.RoundIcon
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.components.Toggle
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun RecordScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cap by remember { mutableStateOf<Capture?>(null) }
    var withMic by remember { mutableStateOf(false) }
    var micId by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var noteCount by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var listening by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    fun begin(mic: Boolean) {
        Recordings.stop()
        cap = Capture()
        micId = if (mic) Recordings.startMic(ctx).also { if (it == null) msg = "The microphone couldn't start, so this take is MIDI only." } else null
    }
    fun finish() {
        val c = cap ?: return
        Recordings.stopMic()
        val title = "Free play · " + SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(Date())
        val t = Recordings.save(title, c.snapshot(), c.startNanos, micId)
        msg = if (t == null) "Nothing was played, so nothing was saved." else null
        cap = null; micId = null
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) msg = "Without the microphone, takes are saved as MIDI only."
        begin(ok)
    }

    LaunchedEffect(cap) {
        val c = cap ?: return@LaunchedEffect
        launch { MidiHub.raw.collect { c.add(it) } }
        while (true) {
            elapsed = (System.nanoTime() - c.startNanos) / 1_000_000
            noteCount = c.snapshot().count { (it.status and 0xF0) == 0x90 && it.d2 > 0 }
            delay(250)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (cap != null) finish()
            Recordings.stop()
            player?.release()
        }
    }

    fun listen(t: Take) {
        player?.release(); player = null
        if (listening == t.id) { listening = null; return }
        Recordings.stop()
        player = MediaPlayer().apply {
            setDataSource(Recordings.audioFile(t.id).absolutePath)
            setOnCompletionListener { listening = null }
            prepare(); start()
        }
        listening = t.id
    }

    Page {
        TopBar(onBack, center = { Text("Recordings", style = T.title, color = MC.Ink, modifier = Modifier.weight(1f)) })
        MCard(radius = 28.dp, pad = 20.dp, color = if (cap != null) MC.RightSoft else MC.Surface) {
            if (cap != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(MC.Right))
                    Text("Recording", style = T.title, color = MC.Ink, modifier = Modifier.weight(1f))
                    Text(mmss(elapsed), style = T.monoBig, color = MC.Ink)
                }
                Text("$noteCount notes" + if (micId != null) " · sound from the microphone too" else "", style = T.body, color = MC.InkSoft)
                PrimaryButton("Stop and save", { finish() }, Modifier.fillMaxWidth(), icon = Ic.Stop, color = MC.Ink)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Mochi(Mood.Listening, Modifier.size(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Play anything, keep it", style = T.title, color = MC.Ink)
                        Text("Every key is saved, so it can play back on the piano exactly as you played it.", style = T.small, color = MC.InkSoft)
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Also record the sound", style = T.bodyStrong, color = MC.Ink)
                        Text("Uses the microphone, so you can send it to anyone", style = T.small, color = MC.InkSoft)
                    }
                    Toggle(withMic, { withMic = it }, "Also record the sound")
                }
                PrimaryButton("Record", {
                    msg = null
                    if (withMic && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPerm.launch(Manifest.permission.RECORD_AUDIO)
                    else begin(withMic)
                }, Modifier.fillMaxWidth())
            }
        }
        msg?.let { Text(it, style = T.bodyStrong, color = MC.Right) }

        if (Recordings.takes.isEmpty()) Text("No recordings yet. After a song you can also save your take from the results screen.", style = T.body, color = MC.InkSoft)
        Recordings.takes.forEach { t ->
            MCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon(if (Recordings.playing == t.id) Ic.Stop else Ic.Play, "Play on the piano", {
                        player?.release(); player = null; listening = null
                        if (Recordings.playing == t.id) Recordings.stop() else Recordings.play(t, scope)
                    }, bg = MC.Right, tint = MC.Surface)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = T.bodyStrong, color = MC.Ink, maxLines = 1)
                        Text(SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(Date(t.createdMs)) + " · " + mmss(t.durationMs) + " · ${t.notes} notes", style = T.small, color = MC.InkSoft)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (t.hasAudio) SoftButton(if (listening == t.id) "Stop sound" else "Sound", { listen(t) }, icon = Ic.Ear, height = 42.dp, bg = MC.Porcelain)
                    SoftButton("Share", { Recordings.share(ctx, t, audio = t.hasAudio) }, height = 42.dp, bg = MC.Porcelain)
                    if (t.hasAudio) SoftButton("MIDI", { Recordings.share(ctx, t, audio = false) }, height = 42.dp, bg = MC.Porcelain)
                    SoftButton(if (confirmDelete == t.id) "Tap to delete" else "Delete", {
                        if (confirmDelete == t.id) { Recordings.delete(t); confirmDelete = null } else confirmDelete = t.id
                    }, height = 42.dp, bg = if (confirmDelete == t.id) MC.RightSoft else MC.Porcelain)
                }
            }
        }
        Text("Play sends the notes back to the piano when it's connected, so it sounds just like she played. Share sends the sound file if there is one, otherwise the MIDI file.", style = T.small, color = MC.InkSoft)
    }
}
