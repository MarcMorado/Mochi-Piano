package app.mochipiano.course

import app.mochipiano.music.Hand
import app.mochipiano.music.Pitch

enum class Clef { Treble, Bass }
enum class Mood { Calm, Listening, Cheer }
enum class Tint { Right, Left, Twin, Triplet, Soft }

/** What a step shows besides its text. */
sealed interface Vis {
    data object None : Vis
    data class Keys(val range: IntRange, val tint: Map<Int, Tint> = emptyMap(), val fingers: Map<Int, Int> = emptyMap(), val labels: Boolean = true, val middleC: Boolean = false) : Vis
    data class Staff(val notes: List<Int>, val clef: Clef, val letters: Boolean = true) : Vis
    data object Hands : Vis
    data class Rhythm(val pattern: List<Double>) : Vis
    data object Alphabet : Vis
    data class Checklist(val items: List<String>) : Vis
}

/** A thing to find on the keyboard, for "play any C" style steps. */
data class Target(val prompt: String, val accepts: (Int) -> Boolean)

sealed interface Step {
    val title: String

    data class Info(override val title: String, val text: String, val vis: Vis = Vis.None, val mood: Mood = Mood.Calm) : Step

    /** Play these in order. Each entry is one key, or several keys together for a chord. */
    data class Play(
        override val title: String, val text: String, val seq: List<List<Int>>, val range: IntRange,
        val fingers: Map<Int, Int> = emptyMap(), val hand: Hand = Hand.R, val staff: Clef? = null, val rounds: Int = 1,
    ) : Step

    /** Play any key matching each prompt; prompts are chosen to favour the ones that are still shaky. */
    data class Find(override val title: String, val targets: List<Target>, val count: Int, val range: IntRange = 48..71) : Step

    /** A note on the staff: play it on the piano (exact octave). */
    data class Read(override val title: String, val clef: Clef, val pool: List<Int>, val count: Int) : Step

    /** Tap any key on each note of the rhythm. Negative values are rests. */
    data class Rhythm(override val title: String, val text: String, val pattern: List<Double>, val bpm: Int) : Step

    data class Choice(override val title: String, val options: List<String>, val answer: String) : Step

    data class Song(override val title: String, val text: String, val songId: String) : Step

    /** Listen to a short phrase, then play it back by ear. */
    data class Echo(override val title: String, val pool: List<Int>, val rounds: Int, val startLength: Int = 3) : Step

    /** Measure her softest and loudest touch, so dynamics are judged against her own range. */
    data class TouchRange(override val title: String, val text: String) : Step

    /** Play each target softly, medium or loudly. */
    data class Dynamics(override val title: String, val text: String, val targets: List<Dyn>, val pitch: Int) : Step

    /** Play the notes getting louder ([up]) or softer, one after another. */
    data class Swell(override val title: String, val text: String, val seq: List<Int>, val up: Boolean) : Step

    /** Pedal down, chord, hands off while it rings, pedal up. */
    data class Pedal(override val title: String, val text: String, val chord: List<Int>) : Step

    /** Legato pedal: after each new chord, lift the pedal and press it again. */
    data class PedalChange(override val title: String, val text: String, val chords: List<List<Int>>) : Step
}

enum class Dyn(val mark: String, val word: String) { P("p", "softly"), MF("mf", "medium"), F("f", "loudly") }

data class Lesson(val id: String, val unit: Int, val title: String, val steps: List<Step>) {
    var number: Int = 0
}

data class CourseUnit(val title: String, val tint: Tint)

object Course {
    private fun p(s: String) = Pitch.parse(s)
    private fun ps(vararg s: String) = s.map { listOf(p(it)) }
    private val RH = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)
    private val LH = mapOf(48 to 5, 50 to 4, 52 to 3, 53 to 2, 55 to 1)
    private val TWINS = listOf(49, 51, 61, 63)
    private val TRIPS = listOf(54, 56, 58, 66, 68, 70)
    private fun letter(c: Char) = Target("Play any ${c}") { Pitch.letter(it) == c && !Pitch.isSharp(it) }
    private val twin = Target("Play a black key in a group of two") { Pitch.pc(it) in intArrayOf(1, 3) }
    private val triplet = Target("Play a black key in a group of three") { Pitch.pc(it) in intArrayOf(6, 8, 10) }
    private val C4 = 60
    private val oct = 60..71
    private val two = 48..71

    val units = listOf(
        CourseUnit("Hello, piano", Tint.Right),
        CourseUnit("The white keys", Tint.Soft),
        CourseUnit("Feel the beat", Tint.Left),
        CourseUnit("First songs", Tint.Twin),
        CourseUnit("Reading music", Tint.Right),
        CourseUnit("Two hands", Tint.Soft),
        CourseUnit("Touch and pedal", Tint.Left),
    )

    val lessons: List<Lesson> = listOf(
        Lesson("hello", 0, "Sit like a pianist", listOf(
            Step.Info("Hi {name}, I'm Mochi!", "We'll learn piano together, a few minutes at a time. I hear every key you play, so just play and I'll follow.", mood = Mood.Cheer),
            Step.Info("Comfy posture", "Tick each one as you settle in.", Vis.Checklist(listOf("Sit on the front half of the bench", "Elbows level with the keys", "Feet flat on the floor", "Shoulders soft and relaxed", "Sit facing the middle of the piano"))),
            Step.Info("Fingers have numbers", "Thumb is 1, pinky is 5. Both hands use the same numbers.", Vis.Hands),
            Step.Choice("Which number is your thumb?", listOf("1", "3", "5"), "1"),
            Step.Choice("Which number is your pinky?", listOf("2", "4", "5"), "5"),
            Step.Info("Bubble hands", "Curve your fingers as if you held a little mochi. Play with fingertips and keep your wrists floating.", mood = Mood.Listening),
            Step.Play("Wiggle hello", "Thumb on the lit key. Play each lit key with the finger shown, up and back down.", ps("C4", "D4", "E4", "F4", "G4", "F4", "E4", "D4", "C4"), oct, RH),
        )),
        Lesson("black", 0, "Twins and triplets", listOf(
            Step.Info("Your piano map", "Black keys come in groups of two (the twins) and three (the triplets). The pattern repeats all along the piano.", Vis.Keys(two, (TWINS.map { it to Tint.Twin } + TRIPS.map { it to Tint.Triplet }).toMap(), labels = false)),
            Step.Find("Find the groups", listOf(twin, triplet), 8, two),
        )),
        Lesson("findc", 0, "Meet C", listOf(
            Step.Info("C lives by the twins", "C is the white key just left of every pair of twins.", Vis.Keys(two, mapOf(48 to Tint.Right, 60 to Tint.Right) + TWINS.associateWith { Tint.Twin })),
            Step.Find("Find a C", listOf(letter('C')), 5, two),
            Step.Info("Middle C", "Your piano has 88 keys and 8 Cs. The one in front of you, near the middle, is Middle C: home base for your first songs.", Vis.Keys(two, mapOf(C4 to Tint.Right), middleC = true)),
            Step.Play("Three Cs", "Play the C below Middle C, then Middle C, then the C above it.", ps("C3", "C4", "C5", "C4"), 48..72),
        )),
        Lesson("cde", 1, "C, D and E", listOf(
            Step.Info("Around the twins", "C is left of the twins, D sits between them, E is on their right.", Vis.Keys(oct, mapOf(60 to Tint.Right, 62 to Tint.Right, 64 to Tint.Right))),
            Step.Find("Find them", listOf(letter('C'), letter('D'), letter('E')), 8, two),
            Step.Play("Your first tune", "Right thumb on Middle C. Play C D E, then E D C, slow and even.", ps("C4", "D4", "E4", "E4", "D4", "C4"), oct, mapOf(60 to 1, 62 to 2, 64 to 3)),
        )),
        Lesson("fgab", 1, "F, G, A and B", listOf(
            Step.Info("Around the triplets", "F is just left of the triplets. G and A sit between them, B is on their right.", Vis.Keys(oct, listOf(65, 67, 69, 71).associateWith { Tint.Right })),
            Step.Find("Find them", listOf(letter('F'), letter('G'), letter('A'), letter('B')), 8, two),
            Step.Play("Climb the stairs", "From Middle C, play every white key up to the next C. Say each name out loud.", ps("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"), 60..72),
        )),
        Lesson("abc", 1, "The music alphabet", listOf(
            Step.Info("Only seven letters", "Music uses just A B C D E F G. After G it starts again at A, all the way up.", Vis.Alphabet),
            Step.Choice("What comes after G?", listOf("A", "H", "C"), "A"),
            Step.Choice("What comes before C?", listOf("D", "B", "A"), "B"),
            Step.Choice("In Spain they say Do, Re, Mi. Which letter is Do?", listOf("A", "C", "D"), "C"),
            Step.Find("All seven", Pitch.LETTERS.map { letter(it) }, 10, two),
        )),
        Lesson("beat", 2, "Feel the beat", listOf(
            Step.Info("A steady pulse", "Music has a beat, like a heartbeat. A quarter note lasts one beat. Say \"ta\" for each one.", Vis.Rhythm(listOf(1.0, 1.0, 1.0, 1.0))),
            Step.Rhythm("Play the beat", "Play Middle C on every note. Four clicks count you in.", listOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0), 66),
        )),
        Lesson("long", 2, "Long notes", listOf(
            Step.Info("Hold it", "A half note lasts 2 beats (ta-a), a whole note 4 (ta-a-a-a). Keep the key down the whole time.", Vis.Rhythm(listOf(2.0, 2.0, 4.0))),
            Step.Rhythm("Long and short", "Play when each note starts, then hold while it rings.", listOf(1.0, 1.0, 2.0, 2.0, 2.0, 4.0), 66),
            Step.Rhythm("Mix it up", "Short, long, longest. Count along in your head.", listOf(2.0, 1.0, 1.0, 1.0, 1.0, 2.0, 4.0), 72),
        )),
        Lesson("rest", 2, "Quiet beats", listOf(
            Step.Info("Rests are silence", "A rest is a beat you count but don't play.", Vis.Rhythm(listOf(1.0, -1.0, 1.0, -1.0, 2.0, -2.0))),
            Step.Rhythm("Shh!", "Play on the notes only. Stay still on the rests.", listOf(1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -2.0), 66),
            Step.Rhythm("Rest challenge", "Three bars, with a few quiet beats hiding inside.", listOf(1.0, 1.0, 1.0, -1.0, 2.0, -2.0, 1.0, 1.0, 2.0), 72),
        )),
        Lesson("cpos", 3, "C position", listOf(
            Step.Info("Home base", "Right thumb on Middle C, one finger per key: C D E F G. This is C position.", Vis.Keys(60..72, RH.keys.associateWith { Tint.Right }, RH)),
            Step.Play("Five-finger stairs", "Play C D E F G and back down, one finger per key.", ps("C4", "D4", "E4", "F4", "G4", "F4", "E4", "D4", "C4"), 60..72, RH, rounds = 2),
            Step.Choice("In C position, which finger plays F?", listOf("3", "4", "5"), "4"),
            Step.Song("Hot Cross Buns", "Your first real song. I'll wait for each note, so take your time.", "hotcross"),
        )),
        Lesson("mary", 3, "Mary Had a Little Lamb", listOf(
            Step.Info("Your second song", "It stays on C, D, E with one quick visit to G.", mood = Mood.Cheer),
            Step.Song("Mary Had a Little Lamb", "Right hand first. Try both hands once it feels easy.", "mary"),
        )),
        Lesson("lune", 3, "Au Clair de la Lune", listOf(
            Step.Info("A French lullaby", "Gentle and slow. Hold the long notes at the end of each line for the full count."),
            Step.Song("Au Clair de la Lune", "Listen first, then play along.", "lune"),
        )),
        Lesson("jingle", 3, "Jingle Bells", listOf(
            Step.Info("Ready for December", "The chorus uses all five fingers of C position. One note is dotted: hold it a little longer.", mood = Mood.Cheer),
            Step.Song("Jingle Bells", "Right hand, C position.", "jingle"),
        )),
        Lesson("staff", 4, "Meet the staff", listOf(
            Step.Info("Five lines", "Written music sits on a staff: 5 lines and 4 spaces. Higher on the staff means higher on the piano.", Vis.Staff(emptyList(), Clef.Treble)),
            Step.Info("C, D, E on paper", "Middle C sits on its own little line below the staff. D hangs under the bottom line. E sits on it.", Vis.Staff(listOf(60, 62, 64), Clef.Treble)),
            Step.Read("Read and play", Clef.Treble, listOf(60, 62, 64), 8),
            Step.Play("Read a tune", "Play what the staff shows.", ps("C4", "E4", "D4", "C4", "E4", "E4", "D4", "C4"), 60..72, RH, staff = Clef.Treble),
        )),
        Lesson("treble1", 4, "F, G and A on the staff", listOf(
            Step.Info("Line, space, line", "Notes climb line, space, line, space: E on a line, F in a space, G on a line, A in a space.", Vis.Staff(listOf(64, 65, 67, 69), Clef.Treble)),
            Step.Read("Read and play", Clef.Treble, listOf(60, 62, 64, 65, 67, 69), 12),
        )),
        Lesson("treble2", 4, "The top of the staff", listOf(
            Step.Info("Keep climbing", "B sits on the middle line. Then C, D, E; the top line is F and G sits above it.", Vis.Staff(listOf(71, 72, 74, 76, 77, 79), Clef.Treble)),
            Step.Read("Read and play", Clef.Treble, listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79), 14),
        )),
        Lesson("bass", 4, "The bass clef", listOf(
            Step.Info("For the left hand", "The bass clef holds the low notes. Middle C sits on a little line above it; going down come B, A, G.", Vis.Staff(listOf(60, 59, 57, 55), Clef.Bass)),
            Step.Info("The F line", "The clef's two dots hug the F line. Below it come E, D and C.", Vis.Staff(listOf(53, 52, 50, 48), Clef.Bass)),
            Step.Read("Read and play", Clef.Bass, listOf(48, 50, 52, 53, 55, 57, 59, 60), 12),
        )),
        Lesson("left", 5, "Left hand hello", listOf(
            Step.Info("Left C position", "Left pinky (5) on the C below Middle C, thumb (1) on G. The numbers run the other way on this hand.", Vis.Keys(48..60, LH.keys.associateWith { Tint.Left }, LH)),
            Step.Play("Left-hand stairs", "C D E F G and back down: fingers 5 4 3 2 1 2 3 4 5.", ps("C3", "D3", "E3", "F3", "G3", "F3", "E3", "D3", "C3"), 48..60, LH, Hand.L, staff = Clef.Bass),
            Step.Song("Hot Cross Buns, left hand", "Same song, other hand.", "lhbuns"),
        )),
        Lesson("chord", 5, "Your first chord", listOf(
            Step.Info("Notes together", "A chord is several notes at once. The C chord is C, E and G, with fingers 1, 3 and 5.", Vis.Keys(60..71, mapOf(60 to Tint.Right, 64 to Tint.Right, 67 to Tint.Right), mapOf(60 to 1, 64 to 3, 67 to 5))),
            Step.Play("Right-hand chord", "Press C, E and G together. Lift, and press again.", List(4) { listOf(60, 64, 67) }, 60..71, mapOf(60 to 1, 64 to 3, 67 to 5)),
            Step.Play("Left-hand chord", "Left hand on C, E, G below Middle C, fingers 5, 3, 1.", List(4) { listOf(48, 52, 55) }, 48..59, mapOf(48 to 5, 52 to 3, 55 to 1), Hand.L),
            Step.Play("Both hands", "Both C chords at the same time.", List(3) { listOf(48, 52, 55, 60, 64, 67) }, 48..71),
        )),
        Lesson("ode", 5, "Ode to Joy", listOf(
            Step.Info("Beethoven, 1824", "You know every note in this melody already. Two dotted notes give it a little lilt.", mood = Mood.Cheer),
            Step.Song("Ode to Joy", "Start with the right hand, then add the left in song setup.", "ode"),
        )),
        Lesson("softloud", 6, "Soft and loud", listOf(
            Step.Info("Piano means soft", "The instrument's full name is pianoforte: soft-loud. How hard you press decides how loud it sounds. In music, p means soft and f means loud.", mood = Mood.Cheer),
            Step.TouchRange("Your touch", "First I'll learn your own soft and loud, so I can judge fairly."),
            Step.Dynamics("p or f", "Play Middle C softly or loudly, as each card says.", listOf(Dyn.P, Dyn.F, Dyn.P, Dyn.F, Dyn.F, Dyn.P), 60),
            Step.Dynamics("Add the middle", "mf means medium: not too soft, not too loud.", listOf(Dyn.P, Dyn.MF, Dyn.F, Dyn.MF, Dyn.P, Dyn.MF), 60),
        )),
        Lesson("swell", 6, "Getting louder", listOf(
            Step.Info("Crescendo", "A crescendo grows louder note by note; a diminuendo fades away. It's how music breathes.", Vis.Keys(60..72, RH.keys.associateWith { Tint.Right }, RH)),
            Step.Swell("Crescendo", "Play C D E F G, each note a little louder than the last.", listOf(60, 62, 64, 65, 67), true),
            Step.Swell("Diminuendo", "Now G F E D C, each one softer.", listOf(67, 65, 64, 62, 60), false),
        )),
        Lesson("pedal", 6, "The sustain pedal", listOf(
            Step.Info("The right pedal", "The right pedal keeps notes ringing after you let go. Keep your heel on the floor and press with the ball of your foot.", mood = Mood.Listening),
            Step.Pedal("Let it ring", "Pedal down, play the C chord, lift your hands and listen. Then lift the pedal.", listOf(60, 64, 67)),
            Step.Pedal("Big and low", "Same again with a bigger chord across both hands.", listOf(48, 55, 60, 64)),
        )),
        Lesson("pedalchange", 6, "Changing the pedal", listOf(
            Step.Info("Play, then change", "To join chords without blurring them, play the new chord first, then quickly lift the pedal and press it again.", mood = Mood.Calm),
            Step.PedalChange("C, F, G, C", "Hold the pedal through each chord and change it just after every new one.", listOf(listOf(60, 64, 67), listOf(60, 65, 69), listOf(59, 62, 67), listOf(60, 64, 67))),
        )),
    ).also { list -> list.forEachIndexed { i, l -> l.number = i + 1 } }

    fun byId(id: String) = lessons.firstOrNull { it.id == id }
}
