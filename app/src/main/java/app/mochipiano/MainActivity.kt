package app.mochipiano

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mochipiano.audio.Sound
import app.mochipiano.data.Store
import app.mochipiano.midi.MidiHub
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
        val uri = intent?.data
        setContent {
            MochiTheme { MochiApp(uri) }
        }
    }
}
