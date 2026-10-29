package app.mochipiano.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class NoteEvent(val pitch: Int, val velocity: Int, val on: Boolean, val nanos: Long, val fromPiano: Boolean)
data class FoundDevice(val name: String, val address: String)

enum class Link { Off, Scanning, Connecting, Connected }
data class ConnState(val link: Link = Link.Off, val name: String = "", val kind: String = "", val error: String? = null)

/**
 * Everything MIDI. The FP-10 shows up either as a Bluetooth LE MIDI device (scan + openBluetoothDevice)
 * or, over a USB cable, as a USB MIDI device that the system announces by itself.
 * Incoming bytes are parsed into note events; outgoing notes make the piano itself play previews.
 */
object MidiHub {
    val BLE_MIDI_SERVICE: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")

    private var app: Context? = null
    private var mgr: MidiManager? = null
    private val main = Handler(Looper.getMainLooper())

    val events = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 512)
    val held = MutableStateFlow<Set<Int>>(emptySet())
    val sustain = MutableStateFlow(false)
    val state = MutableStateFlow(ConnState())
    val found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val lastNote = MutableStateFlow<NoteEvent?>(null)

    private var device: MidiDevice? = null
    private var outPort: MidiOutputPort? = null
    private var inPort: MidiInputPort? = null
    private var scanCb: ScanCallback? = null
    var onConnected: ((address: String?) -> Unit)? = null

    val canSendToPiano: Boolean get() = inPort != null

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        mgr = app!!.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        try {
            @Suppress("DEPRECATION")
            mgr?.registerDeviceCallback(deviceCallback, main)
        } catch (_: Exception) {}
        connectAnyUsb()
    }

    // ---------- permissions ----------
    fun btPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasBtPermission(ctx: Context): Boolean = btPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    // ---------- USB ----------
    private fun connectAnyUsb() {
        val m = mgr ?: return
        @Suppress("DEPRECATION")
        val list = try { m.devices } catch (_: Exception) { emptyArray() }
        list.firstOrNull { it.type == MidiDeviceInfo.TYPE_USB && it.outputPortCount > 0 }?.let { openInfo(it, "USB") }
    }

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) {
            if (info.type == MidiDeviceInfo.TYPE_USB && info.outputPortCount > 0 && state.value.link != Link.Connected) {
                openInfo(info, "USB")
            }
        }

        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (device?.info?.id == info.id) dropDevice(null)
        }
    }

    private fun openInfo(info: MidiDeviceInfo, kind: String) {
        val m = mgr ?: return
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI keyboard"
        state.value = ConnState(Link.Connecting, name, kind)
        m.openDevice(info, { dev ->
            if (dev == null) state.value = ConnState(Link.Off, error = "Could not open $name")
            else attach(dev, name, kind, null)
        }, main)
    }

    // ---------- Bluetooth ----------
    @SuppressLint("MissingPermission")
    fun startScan(ctx: Context) {
        if (!hasBtPermission(ctx)) return
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            state.value = state.value.copy(error = "Turn Bluetooth on to find the piano.")
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: return
        stopScan(ctx)
        found.value = emptyList()
        if (state.value.link != Link.Connected) state.value = ConnState(Link.Scanning)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                val name = try { d.name } catch (_: SecurityException) { null } ?: result.scanRecord?.deviceName ?: "MIDI device"
                val fd = FoundDevice(name, d.address)
                found.update { list -> if (list.any { it.address == fd.address }) list else list + fd }
            }

            override fun onScanFailed(errorCode: Int) {
                state.value = ConnState(Link.Off, error = "Bluetooth scan failed ($errorCode)")
            }
        }
        scanCb = cb
        try {
            val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BLE_MIDI_SERVICE)).build()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(listOf(filter), settings, cb)
            main.postDelayed({ stopScan(ctx) }, 15_000)
        } catch (e: SecurityException) {
            state.value = ConnState(Link.Off, error = "Bluetooth permission is needed")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan(ctx: Context) {
        val cb = scanCb ?: return
        scanCb = null
        try {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {}
        if (state.value.link == Link.Scanning) state.value = ConnState(Link.Off)
    }

    @SuppressLint("MissingPermission")
    fun connectBluetooth(ctx: Context, address: String, name: String) {
        val m = mgr ?: return
        if (!hasBtPermission(ctx)) return
        stopScan(ctx)
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return
        val bt: BluetoothDevice = try { adapter.getRemoteDevice(address) } catch (_: Exception) { return }
        state.value = ConnState(Link.Connecting, name, "Bluetooth")
        try {
            m.openBluetoothDevice(bt, { dev ->
                if (dev == null) state.value = ConnState(Link.Off, error = "Could not connect to $name. Is it on and nearby?")
                else attach(dev, name, "Bluetooth", address)
            }, main)
        } catch (e: SecurityException) {
            state.value = ConnState(Link.Off, error = "Bluetooth permission is needed")
        }
    }

    // ---------- device ----------
    private fun attach(dev: MidiDevice, name: String, kind: String, address: String?) {
        dropDevice(null)
        device = dev
        val info = dev.info
        val outIndex = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }?.portNumber ?: 0
        val inIndex = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_INPUT }?.portNumber
        outPort = try { dev.openOutputPort(outIndex) } catch (_: Exception) { null }
        outPort?.connect(receiver)
        inPort = if (inIndex != null) try { dev.openInputPort(inIndex) } catch (_: Exception) { null } else null
        state.value = ConnState(Link.Connected, name, kind)
        onConnected?.invoke(address)
    }

    private fun dropDevice(error: String?) {
        try { outPort?.disconnect(receiver) } catch (_: Exception) {}
        try { outPort?.close() } catch (_: Exception) {}
        try { inPort?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        outPort = null; inPort = null; device = null
        held.value = emptySet()
        if (state.value.link == Link.Connected) state.value = ConnState(Link.Off, error = error)
    }

    fun disconnect() = dropDevice(null)

    // ---------- parsing ----------
    private val receiver = object : MidiReceiver() {
        private var running = 0
        private var d1 = -1

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val ts = if (timestamp > 0) timestamp else System.nanoTime()
            for (i in offset until offset + count) {
                val b = msg[i].toInt() and 0xFF
                if (b >= 0xF8) continue // realtime: clock, active sensing
                if (b >= 0x80) {
                    running = if (b < 0xF0) b else 0
                    d1 = -1
                    continue
                }
                if (running == 0) continue
                val type = running and 0xF0
                if (type == 0xC0 || type == 0xD0) { d1 = -1; continue }
                if (d1 < 0) { d1 = b; continue }
                val n = d1
                d1 = -1
                when (type) {
                    0x90 -> emit(n, b, b > 0, ts, true)
                    0x80 -> emit(n, 0, false, ts, true)
                    0xB0 -> if (n == 64) sustain.value = b >= 64
                }
            }
        }
    }

    private fun emit(p: Int, v: Int, on: Boolean, ts: Long, fromPiano: Boolean) {
        val e = NoteEvent(p, v, on, ts, fromPiano)
        events.tryEmit(e)
        if (on) lastNote.value = e
        held.update { if (on) it + p else it - p }
    }

    /** On-screen keys behave like the piano, so everything can be tried without one. */
    fun inject(p: Int, on: Boolean) = emit(p, if (on) 80 else 0, on, System.nanoTime(), false)

    // ---------- output ----------
    fun sendNote(p: Int, velocity: Int, on: Boolean) {
        val port = inPort ?: return
        val status = if (on) 0x90 else 0x80
        try { port.send(byteArrayOf(status.toByte(), p.toByte(), velocity.coerceIn(0, 127).toByte()), 0, 3) } catch (_: Exception) {}
    }

    fun allNotesOff() {
        val port = inPort ?: return
        try { port.send(byteArrayOf(0xB0.toByte(), 123, 0), 0, 3) } catch (_: Exception) {}
    }
}
