package app.mochipiano

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import app.mochipiano.audio.Sound
import app.mochipiano.data.Store
import app.mochipiano.midi.MidiHub
import app.mochipiano.record.Recordings
import app.mochipiano.ui.MochiApp
import app.mochipiano.ui.theme.MochiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The phone lives on the music stand: keep the screen awake while the app is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Store.load(this)
        MidiHub.init(this)
        Sound.init(this)
        Recordings.load(this)
        incoming.value = scoreUri(intent)
        setContent {
            MochiTheme { MochiApp(incoming.value) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scoreUri(intent)?.let { incoming.value = it }
    }

    private val incoming = mutableStateOf<Uri?>(null)

    /** A score opened with Mochi (VIEW) or shared to it (SEND). */
    private fun scoreUri(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        else -> null
    }
}
