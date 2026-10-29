package app.mochipiano.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mochipiano.course.Lesson
import app.mochipiano.game.GameConfig
import app.mochipiano.game.GameResult

/** A lesson or practice run in progress. Kept on the back stack so it survives a trip into a song. */
class LessonSession(val lesson: Lesson, val practiceKey: String? = null, val planKey: String? = null) {
    var index by mutableIntStateOf(0)
    var correct by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var songDone by mutableStateOf(false)
    var finished by mutableStateOf(false)
    val startMs = System.currentTimeMillis()
}

sealed interface Scr {
    data class Tabs(val tab: String) : Scr
    data class LessonS(val s: LessonSession) : Scr
    data class Setup(val songId: String, val from: LessonSession? = null) : Scr
    data class Play(val cfg: GameConfig, val from: LessonSession?) : Scr
    data class Result(val r: GameResult, val cfg: GameConfig, val from: LessonSession?) : Scr
    data object Connect : Scr
    data object Import : Scr
}

class Nav {
    val stack = mutableStateListOf<Scr>(Scr.Tabs("learn"))
    val top: Scr get() = stack.last()
    fun push(s: Scr) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun replace(s: Scr) { stack[stack.size - 1] = s }
    fun tab(t: String) { stack.clear(); stack.add(Scr.Tabs(t)) }
    fun popTo(pred: (Scr) -> Boolean) { while (stack.size > 1 && !pred(stack.last())) stack.removeAt(stack.size - 1) }
}
