package app.mochipiano.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.mochipiano.course.Course
import app.mochipiano.course.Practice
import app.mochipiano.data.Store
import app.mochipiano.game.GameConfig
import app.mochipiano.midi.Link
import app.mochipiano.midi.MidiHub
import app.mochipiano.ui.components.BottomNav
import app.mochipiano.ui.screens.CalibrateScreen
import app.mochipiano.ui.screens.ConnectScreen
import app.mochipiano.ui.screens.RecordScreen
import app.mochipiano.ui.screens.GameScreen
import app.mochipiano.ui.screens.GamesScreen
import app.mochipiano.ui.screens.HomeScreen
import app.mochipiano.ui.screens.ImportScreen
import app.mochipiano.ui.screens.LessonScreen
import app.mochipiano.ui.screens.MeScreen
import app.mochipiano.ui.screens.ResultsScreen
import app.mochipiano.ui.screens.SongSetupScreen
import app.mochipiano.ui.screens.SongsScreen
import app.mochipiano.ui.theme.MC

@Composable
fun MochiApp(openUri: Uri?) {
    val nav = remember { Nav() }
    val ctx = LocalContext.current
    var importUri by remember { mutableStateOf(openUri) }

    LaunchedEffect(openUri) { if (openUri != null) { importUri = openUri; nav.push(Scr.Import) } }
    // Reconnect to the last Bluetooth piano when the app opens.
    LaunchedEffect(Unit) {
        val d = Store.data
        if (MidiHub.state.value.link == Link.Off && d.lastBtAddress != null && MidiHub.hasBtPermission(ctx)) {
            MidiHub.connectBluetooth(ctx, d.lastBtAddress, d.lastBtName ?: "Piano")
        }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    val solfege = Store.data.settings.solfege
    when (val top = nav.top) {
        is Scr.Tabs -> Column(Modifier.fillMaxSize().background(MC.Porcelain)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (top.tab) {
                    "learn" -> HomeScreen(
                        onLesson = { nav.push(Scr.LessonS(LessonSession(it))) },
                        onPlan = { item ->
                            val d = Store.data
                            when (item.kind) {
                                Practice.Kind.Warm -> nav.push(Scr.LessonS(LessonSession(Practice.warmup(d), "warm", item.key)))
                                Practice.Kind.Lesson -> Course.byId(item.ref)?.let { nav.push(Scr.LessonS(LessonSession(it, planKey = item.key))) }
                                Practice.Kind.Review -> nav.push(Scr.LessonS(LessonSession(
                                    if (item.ref == "keys") Practice.keyFinder(d, 8) else Practice.noteNinja(d, item.ref, 10), "review", item.key)))
                                Practice.Kind.Song -> nav.push(Scr.Setup(item.ref))
                            }
                        },
                        onSong = { nav.push(Scr.Setup(it)) },
                        onConnect = { nav.push(Scr.Connect) },
                    )
                    "songs" -> SongsScreen(onSong = { nav.push(Scr.Setup(it)) }, onImport = { importUri = null; nav.push(Scr.Import) })
                    "games" -> GamesScreen(onStart = { l, key -> nav.push(Scr.LessonS(LessonSession(l, key))) }, onRecord = { nav.push(Scr.Recordings) })
                    else -> MeScreen(onConnect = { nav.push(Scr.Connect) }, onRecord = { nav.push(Scr.Recordings) }, onCalibrate = { nav.push(Scr.Calibrate) })
                }
            }
            BottomNav(top.tab) { nav.tab(it) }
        }

        is Scr.LessonS -> LessonScreen(
            top.s,
            onExit = { nav.pop() },
            onPlaySong = { id -> nav.push(Scr.Setup(id, top.s)) },
            onNextLesson = { id -> Course.byId(id)?.let { nav.replace(Scr.LessonS(LessonSession(it))) } },
        )

        is Scr.Setup -> SongSetupScreen(
            top.songId, fromLesson = top.from != null,
            onBack = { nav.pop() },
            onPlay = { cfg -> nav.push(Scr.Play(cfg, top.from)) },
            onDeleted = { nav.pop() },
        )

        is Scr.Play -> GameScreen(top.cfg, solfege, onQuit = { nav.pop() }, onDone = { r ->
            val prev = Store.songStat(top.cfg.song.id)
            Store.update { a ->
                val st = a.songs[top.cfg.song.id] ?: prev
                a.copy(songs = a.songs + (top.cfg.song.id to st.copy(
                    plays = st.plays + 1, best = maxOf(st.best, r.accuracy), mastery = maxOf(st.mastery, r.mastery),
                    speed = r.speed, level = if (top.cfg.dynamic) r.level else st.level,
                    hands = top.cfg.hands.joinToString("") { it.name },
                    sectionAcc = if (top.cfg.loop == null) st.sectionAcc + r.sectionAcc else st.sectionAcc,
                )))
            }
            Store.markPractice(2, 10 + (r.accuracy * 20).toInt())
            Store.planMark("song:${top.cfg.song.id}")
            top.from?.songDone = true
            resultPrev[top.cfg.song.id] = prev.mastery
            nav.replace(Scr.Result(r, top.cfg, top.from))
        })

        is Scr.Result -> ResultsScreen(
            top.r, top.cfg, Store.data.name, resultPrev[top.cfg.song.id] ?: 0.0, fromLesson = top.from != null,
            onClose = { nav.popTo { it is Scr.LessonS || it is Scr.Tabs || it is Scr.Setup } },
            onAgain = { cfg: GameConfig -> nav.replace(Scr.Play(cfg, top.from)) },
            onLoop = { cfg: GameConfig -> nav.replace(Scr.Play(cfg, top.from)) },
            onContinueLesson = { nav.popTo { it is Scr.LessonS } },
        )

        Scr.Connect -> ConnectScreen(onDone = { nav.pop() }, onCalibrate = { nav.push(Scr.Calibrate) })

        Scr.Recordings -> RecordScreen(onBack = { nav.pop() })

        Scr.Calibrate -> CalibrateScreen(onDone = { nav.pop() })

        Scr.Import -> ImportScreen(importUri, onBack = { nav.pop() }, onAdded = { id -> nav.replace(Scr.Setup(id)) })
    }
}

/** Mastery before the last run, so Results can show the change. */
private val resultPrev = HashMap<String, Double>()
