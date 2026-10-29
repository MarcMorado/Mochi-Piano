package app.mochipiano.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mochipiano.midi.ConnState
import app.mochipiano.midi.Link
import app.mochipiano.ui.theme.MC
import app.mochipiano.ui.theme.T

/** Standard scrolling page: status-bar safe, 20dp gutters, 16dp rhythm between blocks. */
@Composable
fun Page(modifier: Modifier = Modifier, bottomPad: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(MC.Porcelain)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun MCard(modifier: Modifier = Modifier, color: Color = MC.Surface, radius: Dp = 24.dp, pad: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(color)
    Column((if (onClick != null) m.clickable(onClick = onClick) else m).padding(pad), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, color: Color = MC.Right, textColor: Color = Color.White, height: Dp = 56.dp) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color else color.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = textColor, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = T.button, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, height: Dp = 52.dp, bg: Color = MC.Surface) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = MC.Ink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = T.bodyStrong, color = if (enabled) MC.Ink else MC.InkSoft, maxLines = 1)
    }
}

@Composable
fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = MC.Surface, tint: Color = MC.Ink, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(bg).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.45f)) }
}

@Composable
fun Chip(text: String, bg: Color = MC.Mist, fg: Color = MC.Ink, modifier: Modifier = Modifier, dot: Color? = null, onClick: (() -> Unit)? = null, style: TextStyle = T.smallStrong) {
    val m = modifier.clip(RoundedCornerShape(99.dp)).background(bg)
    Row(
        (if (onClick != null) m.clickable(onClick = onClick) else m).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = style, color = fg, maxLines = 1)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = MC.InkSoft) =
    Text(text.uppercase(), style = T.label, color = color, modifier = modifier)

@Composable
fun <K> Segmented(options: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit, modifier: Modifier = Modifier, dots: Map<K, List<Color>> = emptyMap()) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MC.Mist).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (k, label) ->
            val on = k == selected
            Row(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) MC.Surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(k) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dots[k]?.forEachIndexed { i, c -> Box(Modifier.offset(x = (-4 * i).dp).size(10.dp).clip(CircleShape).background(c)) }
                if (dots[k] != null) Spacer(Modifier.width(6.dp))
                Text(label, style = if (on) T.bodyStrong else T.body, color = MC.Ink)
            }
        }
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val x by animateFloatAsState(if (on) 1f else 0f, label = "toggle")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (on) MC.Mint else MC.KeyLine)
            .clickable(role = Role.Switch, onClickLabel = label) { onChange(!on) },
    ) {
        Box(Modifier.offset(x = (3 + 20 * x).dp, y = 3.dp).size(26.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MC.Line, stroke: Dp = 5.dp, label: String? = null, labelStyle: TextStyle = T.monoS) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            if (fraction > 0) drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, style = labelStyle, color = MC.Ink)
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, track: Color = MC.Line, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(99.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(99.dp)).background(color))
    }
}

@Composable
fun TopBar(onBack: () -> Unit, icon: ImageVector = Ic.Back, center: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RoundIcon(icon, if (icon == Ic.Close) "Close" else "Back", onBack)
        if (center != null) center() else Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Small pill showing the piano connection; taps through to Connect. */
@Composable
fun ConnPill(state: ConnState, onClick: () -> Unit) {
    val (dot, text) = when (state.link) {
        Link.Connected -> MC.Mint to state.name.ifBlank { "Piano" }.take(14)
        Link.Connecting -> MC.Butter to "Connecting…"
        Link.Scanning -> MC.Butter to "Searching…"
        Link.Off -> MC.Right to "Connect piano"
    }
    Chip(text, bg = MC.Surface, dot = dot, onClick = onClick, modifier = Modifier.height(36.dp))
}

/** Generated cover art: two shapes from the palette, picked by [seed]. */
@Composable
fun Cover(seed: Int, modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val pal = listOf(MC.Left, MC.Butter, MC.Mint, MC.Right, MC.MochiBody, MC.LeftSoft, MC.ButterSoft, MC.MintSoft)
    val bg = pal[seed % pal.size]
    val a = pal[(seed / 3 + 3) % pal.size].let { if (it == bg) MC.Ink else it }
    val b = if (seed % 2 == 0) MC.Ink else MC.Surface
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            when (seed % 4) {
                0 -> { drawCircle(a, w * 0.36f, Offset(w * 0.5f, w * 0.5f)); drawArc(b, 180f, 180f, true, Offset(w * 0.14f, w * 0.14f), Size(w * 0.72f, w * 0.72f)) }
                1 -> { drawCircle(a, w * 0.42f, Offset(w * 0.2f, w * 0.92f)); drawCircle(b, w * 0.09f, Offset(w * 0.72f, w * 0.34f)); drawRect(b, Offset(w * 0.78f, 0f), Size(w * 0.055f, w * 0.34f)) }
                2 -> { drawRect(b, Offset(w * 0.2f, 0f), Size(w * 0.15f, w)); drawRect(b, Offset(w * 0.46f, 0f), Size(w * 0.15f, w)); drawCircle(a, w * 0.2f, Offset(w * 0.8f, w * 0.3f)) }
                else -> { drawArc(a, 180f, 180f, true, Offset(0f, w * 0.5f), Size(w, w)); drawCircle(b, w * 0.13f, Offset(w * 0.5f, w * 0.28f)) }
            }
        }
    }
}

@Composable
fun BottomNav(tab: String, onTab: (String) -> Unit) {
    val items = listOf(Triple("learn", "Learn", Ic.Learn), Triple("songs", "Songs", Ic.Songs), Triple("games", "Games", Ic.Games), Triple("me", "Me", Ic.Me))
    Column(Modifier.fillMaxWidth().background(MC.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MC.Mist))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
            items.forEach { (k, label, icon) ->
                val on = k == tab
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) MC.RightSoft else Color.Transparent)
                        .clickable(role = Role.Tab) { onTab(k) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, null, tint = if (on) MC.Right else MC.InkSoft, modifier = Modifier.size(26.dp))
                    Text(label, style = T.smallStrong, color = if (on) MC.Right else MC.InkSoft, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun Stars(n: Int, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i -> Icon(Ic.Star, null, tint = if (i < n) MC.Butter else MC.KeyLine, modifier = Modifier.size(size)) }
    }
}

@Composable
fun Bubble(title: String, text: String?, modifier: Modifier = Modifier, mood: app.mochipiano.course.Mood = app.mochipiano.course.Mood.Calm) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Mochi(mood, Modifier.size(54.dp))
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)).background(MC.Surface).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = T.title, color = MC.Ink)
            if (!text.isNullOrBlank()) Text(text, style = T.body, color = MC.InkSoft)
        }
    }
}

@Composable
fun noRipple() = remember { MutableInteractionSource() }

@Composable
fun BoxScope.Toast(text: String?) {
    if (text == null) return
    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp).clip(RoundedCornerShape(14.dp)).background(MC.Ink).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, style = T.bodyStrong, color = MC.Porcelain)
    }
}

@Composable
fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(MC.Line))

@Composable
fun Outlined(modifier: Modifier = Modifier, selected: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) MC.Ink else MC.Surface)
            .then(if (!selected) Modifier.border(0.dp, Color.Transparent, RoundedCornerShape(18.dp)) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}
