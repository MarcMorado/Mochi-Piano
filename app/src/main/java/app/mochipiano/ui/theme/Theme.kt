package app.mochipiano.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.mochipiano.R

/** Colour tokens from the Foundations artboard. Pink is always the right hand, blue the left. */
object MC {
    val Porcelain = Color(0xFFF3F1F7)
    val Surface = Color(0xFFFFFFFF)
    val Mist = Color(0xFFE6E1EE)
    val Line = Color(0xFFECE8F2)
    val KeyLine = Color(0xFFDCD6E6)
    val Ink = Color(0xFF1C1530)
    val InkSoft = Color(0xFF5D5670)
    val InkFaint = Color(0xFFC9C1D6)

    val Right = Color(0xFFDB2464)
    val RightSoft = Color(0xFFFFE1EB)
    val RightText = Color(0xFF8E1240)
    val Left = Color(0xFF2F5BEA)
    val LeftSoft = Color(0xFFE1E8FF)
    val LeftText = Color(0xFF1F44B8)
    val Butter = Color(0xFFFFC43D)
    val ButterSoft = Color(0xFFFFF1CC)
    val ButterText = Color(0xFF6B4E00)
    val Mint = Color(0xFF12B886)
    val MintSoft = Color(0xFFD3F5E9)
    val MintText = Color(0xFF0B5E45)

    val Stage = Color(0xFF140F1F)
    val Stage2 = Color(0xFF241C35)
    val StageLine = Color(0xFF3A2F50)
    val StageText = Color(0xFFF4EFFA)
    val StageMuted = Color(0xFFA79FBA)
    val StageRight = Color(0xFFFF4F8B)
    val StageLeft = Color(0xFF5B7CFF)
    val StageKey = Color(0xFFF6F3FA)
    val StageBlack = Color(0xFF0E0A16)
    val Good = Color(0xFF7F9DFF)

    val MochiBody = Color(0xFFFFD3E1)
    val Blush = Color(0xFFFF8DB3)
}

val Bricolage = FontFamily(
    Font(R.font.bricolage_bold, FontWeight.Bold),
    Font(R.font.bricolage_extrabold, FontWeight.ExtraBold),
)
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)
val GeistMono = FontFamily(
    Font(R.font.geistmono_medium, FontWeight.Medium),
    Font(R.font.geistmono_bold, FontWeight.Bold),
)
val ClefFont = FontFamily(Font(R.font.music_clefs))

/** Type scale. Every number in the app uses [mono] or [monoBig]. */
object T {
    val display = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 35.sp, letterSpacing = (-0.02).em)
    val displayS = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em)
    val title = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 25.sp)
    val h3 = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp)
    val bodyL = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 25.sp)
    val small = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val smallStrong = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
    val button = TextStyle(fontFamily = Bricolage, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    val mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)
    val monoS = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp)
    val monoBig = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp)
}

@Composable
fun MochiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MC.Right,
            onPrimary = Color.White,
            secondary = MC.Left,
            background = MC.Porcelain,
            onBackground = MC.Ink,
            surface = MC.Surface,
            onSurface = MC.Ink,
            surfaceVariant = MC.Mist,
            onSurfaceVariant = MC.InkSoft,
            outline = MC.KeyLine,
        ),
        content = content,
    )
}
