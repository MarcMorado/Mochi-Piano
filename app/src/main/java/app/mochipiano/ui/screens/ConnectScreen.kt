package app.mochipiano.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mochipiano.data.Store
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.music.Pitch
import app.mochipiano.ui.components.Hairline
import app.mochipiano.ui.components.Ic
import app.mochipiano.ui.components.Label
import app.mochipiano.ui.components.MCard
import app.mochipiano.ui.components.Page
import app.mochipiano.ui.components.PianoKeys
import app.mochipiano.ui.components.PrimaryButton
import app.mochipiano.ui.components.SoftButton
import app.mochipiano.ui.components.TopBar
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T

@Composable
fun ConnectScreen(onDone: () -> Unit, onCalibrate: () -> Unit) {
    val ctx = LocalContext.current
    val state by MidiHub.state.collectAsStateWithLifecycle()
    val found by MidiHub.found.collectAsStateWithLifecycle()
    val last by MidiHub.lastNote.collectAsStateWithLifecycle()
    val held by MidiHub.held.collectAsStateWithLifecycle()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) MidiHub.startScan(ctx)
    }
    val scan = { if (MidiHub.hasBtPermission(ctx)) MidiHub.startScan(ctx) else perm.launch(MidiHub.btPermissions()) }

    LaunchedEffect(Unit) { if (state.link != Link.Connected) scan() }
    DisposableEffect(Unit) { onDispose { MidiHub.stopScan(ctx) } }

    Page {
        TopBar(onDone, center = { Text("SETUP", style = T.monoS, color = MC.InkSoft, modifier = Modifier.weight(1f)) })
        Text(if (state.link == Link.Connected) "Piano connected" else "Connect your piano", style = T.display, color = MC.Ink)
        Text("Mochi hears every key through the piano's Bluetooth MIDI or a USB cable. Nothing is recorded as audio.", style = T.body, color = MC.InkSoft)

        if (state.link == Link.Connected) {
            MCard(color = MC.MintSoft) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MC.Ink), contentAlignment = Alignment.Center) { Icon(Ic.Piano, null, tint = MC.Surface, modifier = Modifier.size(24.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text(state.name, style = T.bodyStrong, color = MC.Ink)
                        Text("${state.kind} MIDI · connected", style = T.small, color = MC.MintText)
                    }
                    SoftButton("Disconnect", { MidiHub.disconnect() }, height = 40.dp)
                }
            }
            MCard {
                Text("Check the timing", style = T.bodyStrong, color = MC.Ink)
                Text(
                    if (Store.data.settings.calibrated) "Measured: ${Store.data.settings.latencyMs} ms in songs, ${Store.data.settings.latencyAudioMs} ms with clicks."
                    else "A 30-second tap-along so Mochi can allow for the Bluetooth delay when judging your timing.",
                    style = T.small, color = MC.InkSoft,
                )
                SoftButton(if (Store.data.settings.calibrated) "Check again" else "Check timing", onCalibrate, bg = MC.Porcelain)
            }
        } else {
            MCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Label("Found nearby")
                    Text(if (state.link == Link.Scanning) "searching…" else "", style = T.mono, color = MC.InkSoft)
                }
                if (found.isEmpty()) {
                    Text(
                        if (state.link == Link.Scanning) "Turn the piano on and keep it within a few metres." else "Nothing found yet.",
                        style = T.body, color = MC.InkSoft,
                    )
                }
                found.forEachIndexed { i, f ->
                    if (i > 0) Hairline()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MC.Ink), contentAlignment = Alignment.Center) { Icon(Ic.Piano, null, tint = MC.Surface, modifier = Modifier.size(24.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = T.bodyStrong, color = MC.Ink)
                            Text("Bluetooth MIDI", style = T.small, color = MC.InkSoft)
                        }
                        PrimaryButton(if (state.link == Link.Connecting) "…" else "Connect", {
                            MidiHub.onConnected = { addr -> if (addr != null) Store.update { it.copy(lastBtAddress = addr, lastBtName = f.name) } }
                            MidiHub.connectBluetooth(ctx, f.address, f.name)
                        }, height = 40.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Search again", { scan() }, icon = Ic.Bluetooth, bg = MC.Porcelain)
                    SoftButton("Bluetooth settings", { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }, bg = MC.Porcelain)
                }
            }
            state.error?.let { Text(it, style = T.bodyStrong, color = MC.Right) }
            MCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MC.Mist), contentAlignment = Alignment.Center) { Icon(Ic.Usb, null, tint = MC.Ink, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("Or use a USB cable", style = T.bodyStrong, color = MC.Ink)
                        Text("The piano's USB port to the phone's USB-C (a USB-B to USB-C cable). Lowest delay, no pairing. It connects by itself.", style = T.small, color = MC.InkSoft)
                    }
                }
            }
        }

        MCard(color = if (last != null) MC.MintSoft else MC.Surface) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(MC.Surface), contentAlignment = Alignment.Center) {
                    Text(last?.let { Pitch.name(it.pitch) } ?: "?", style = T.displayS, color = MC.Ink)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (last != null) "Heard ${Pitch.name(last!!.pitch)}. Sounds great!" else "Play any key to test", style = T.bodyStrong, color = MC.Ink)
                    if (last != null) Text("velocity ${last!!.velocity} · ${if (last!!.fromPiano) "from the piano" else "on screen"}", style = T.mono, color = MC.MintText)
                }
            }
            PianoKeys(48..71, held = held, aspect = 2.6f)
        }
        Box(Modifier.height(4.dp))
        PrimaryButton(if (state.link == Link.Connected) "Start playing" else "Continue without a piano", onDone, Modifier.fillMaxWidth())
    }
}
