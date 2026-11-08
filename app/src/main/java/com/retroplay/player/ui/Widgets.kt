package com.retroplay.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.player.player.ArtLoader
import com.retroplay.player.player.PlayState
import com.retroplay.player.player.RadioSweep
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.Track
import com.retroplay.player.player.VisMode
import com.retroplay.player.player.formatClock
import com.retroplay.player.player.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ============================================================ text & scaling

/** Tight text boxes with no extra line height or font padding, so small labels aren't clipped. */
val SkinTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

/**
 * A skin is a fixed-size layout, like the original: ignore the phone's "large text"
 * setting inside it (otherwise labels overflow their buttons), and use [SkinTextStyle].
 * Dialogs and popups open new windows, so they call this again.
 */
@Composable
fun SkinLocals(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(d.density, fontScale = 1f),
        LocalTextStyle provides SkinTextStyle,
        content = content,
    )
}

// ============================================================ basic drawing

/** Classic Windows-style 3D edge: light top/left, dark bottom/right (or inverted). */
fun Modifier.bevel(light: Color, dark: Color, inset: Boolean = false, width: Dp = 1.dp): Modifier =
    drawWithContent {
        drawContent()
        val s = width.toPx()
        val tl = if (inset) dark else light
        val br = if (inset) light else dark
        drawRect(tl, Offset.Zero, Size(size.width, s))
        drawRect(tl, Offset.Zero, Size(s, size.height))
        drawRect(br, Offset(0f, size.height - s), Size(size.width, s))
        drawRect(br, Offset(size.width - s, 0f), Size(s, size.height))
    }

fun DrawScope.bevelRect(topLeft: Offset, sz: Size, top: Color, bottom: Color, light: Color, dark: Color) {
    drawRect(Brush.verticalGradient(listOf(top, bottom), startY = topLeft.y, endY = topLeft.y + sz.height), topLeft, sz)
    val s = 1.dp.toPx()
    drawRect(light, topLeft, Size(sz.width, s))
    drawRect(light, topLeft, Size(s, sz.height))
    drawRect(dark, Offset(topLeft.x, topLeft.y + sz.height - s), Size(sz.width, s))
    drawRect(dark, Offset(topLeft.x + sz.width - s, topLeft.y), Size(s, sz.height))
}

@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier =
    this.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)

// ============================================================ windows

/**
 * A skinned window: title bar + face. With [fill] the content area takes all remaining
 * height (used by the full-screen Media Library).
 */
@Composable
fun SkinWindow(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = LocalSkin.current
    val shape = RoundedCornerShape(s.corner)
    val frame = if (s.isClassic) Modifier.bevel(s.edgeLight, s.edgeDark, width = 2.dp)
    else Modifier.border(1.dp, s.edgeDark, shape)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(s.faceBrush)
            .then(frame)
            .padding(bottom = 8.dp)
    ) {
        TitleBar(title, onClose, leading)
        Column(
            (if (fill) Modifier.weight(1f) else Modifier).padding(horizontal = 8.dp),
            content = content,
        )
    }
}

@Composable
fun TitleBar(title: String, onClose: (() -> Unit)?, leading: (@Composable () -> Unit)?) {
    val s = LocalSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (s.isClassic) 24.dp else 28.dp)
            .background(Brush.verticalGradient(listOf(s.titleTop, s.titleBottom)))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Spacer(Modifier.width(22.dp))
        Stripes(Modifier.weight(1f))
        Text(
            title,
            color = s.titleText,
            fontFamily = s.uiFont,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Stripes(Modifier.weight(1f))
        if (onClose != null) {
            Box(Modifier.size(22.dp).tap(onClose), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CLOSE, s.titleText, Modifier.size(10.dp))
            }
        } else Spacer(Modifier.width(22.dp))
    }
}

@Composable
private fun Stripes(modifier: Modifier) {
    val s = LocalSkin.current
    Canvas(modifier.height(8.dp)) {
        val w = 1.5.dp.toPx()
        drawLine(s.titleStripe, Offset(0f, size.height * 0.25f), Offset(size.width, size.height * 0.25f), w)
        drawLine(s.titleStripe.copy(alpha = 0.55f), Offset(0f, size.height * 0.75f), Offset(size.width, size.height * 0.75f), w)
    }
}

// ============================================================ buttons

@Composable
fun SkinButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val s = LocalSkin.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shp = shape ?: if (s.isClassic) RectangleShape else RoundedCornerShape(50)
    val brush = Brush.verticalGradient(
        if (pressed) listOf(s.btnPressedTop, s.btnPressedBottom) else listOf(s.btnTop, s.btnBottom)
    )
    val edge = if (s.isClassic) Modifier.bevel(Color.White.copy(alpha = 0.85f), s.edgeDark, inset = pressed)
    else Modifier.border(1.dp, s.edgeDark, shp)
    Box(
        modifier
            .clip(shp)
            .background(brush, shp)
            .then(edge)
            .clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun SkinTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = 10.sp) {
    val s = LocalSkin.current
    SkinButton(onClick, modifier) {
        Text(
            label, color = s.btnText, fontSize = fontSize, fontFamily = s.uiFont,
            fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/** Button with a little indicator light, like the EQ / PL / SHUFFLE toggles. */
@Composable
fun LedToggle(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, glyph: Glyph? = null) {
    val s = LocalSkin.current
    SkinButton(onClick, modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 5.dp),
        ) {
            Box(
                Modifier
                    .size(if (s.isClassic) 5.dp else 6.dp)
                    .background(if (on) s.ledOn else s.ledOff, if (s.isClassic) RectangleShape else CircleShape)
            )
            Spacer(Modifier.width(4.dp))
            if (glyph != null) GlyphIcon(glyph, s.btnText, Modifier.size(13.dp))
            if (label.isNotEmpty()) Text(
                label, color = s.btnText, fontSize = 10.sp, fontFamily = s.uiFont,
                fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            )
        }
    }
}

// ============================================================ glyphs

enum class Glyph { PREV, PLAY, PAUSE, STOP, NEXT, EJECT, SHUFFLE, REPEAT, SPEAKER, CLOSE, FOLDER, NOTE, STAR, RADIO }

@Composable
fun GlyphIcon(g: Glyph, color: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = (w * 0.12f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
        fun tri(x0: Float, x1: Float, y0: Float = 0f, y1: Float = h) = Path().apply {
            moveTo(x0, y0); lineTo(x1, (y0 + y1) / 2); lineTo(x0, y1); close()
        }
        when (g) {
            Glyph.PLAY -> drawPath(tri(w * 0.18f, w * 0.95f), color)
            Glyph.PAUSE -> {
                drawRect(color, Offset(w * 0.15f, 0f), Size(w * 0.25f, h))
                drawRect(color, Offset(w * 0.6f, 0f), Size(w * 0.25f, h))
            }
            Glyph.STOP -> drawRect(color, Offset(w * 0.08f, h * 0.08f), Size(w * 0.84f, h * 0.84f))
            Glyph.PREV -> {
                drawRect(color, Offset(0f, 0f), Size(w * 0.16f, h))
                drawPath(tri(w * 0.98f, w * 0.22f), color)
            }
            Glyph.NEXT -> {
                drawPath(tri(w * 0.02f, w * 0.78f), color)
                drawRect(color, Offset(w * 0.84f, 0f), Size(w * 0.16f, h))
            }
            Glyph.EJECT -> {
                drawPath(Path().apply {
                    moveTo(w * 0.5f, 0f); lineTo(w, h * 0.6f); lineTo(0f, h * 0.6f); close()
                }, color)
                drawRect(color, Offset(0f, h * 0.75f), Size(w, h * 0.25f))
            }
            Glyph.SHUFFLE -> {
                drawLine(color, Offset(0f, h * 0.25f), Offset(w * 0.85f, h * 0.75f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(0f, h * 0.75f), Offset(w * 0.85f, h * 0.25f), stroke.width, StrokeCap.Round)
                drawPath(tri(w * 0.7f, w, h * 0.05f, h * 0.45f), color)
                drawPath(tri(w * 0.7f, w, h * 0.55f, h * 0.95f), color)
            }
            Glyph.REPEAT -> {
                drawRoundRect(
                    color, Offset(w * 0.08f, h * 0.2f), Size(w * 0.84f, h * 0.6f),
                    CornerRadius(h * 0.25f), style = stroke,
                )
                drawPath(tri(w * 0.45f, w * 0.75f, h * 0.02f, h * 0.38f), color)
            }
            Glyph.SPEAKER -> {
                drawRect(color, Offset(0f, h * 0.33f), Size(w * 0.3f, h * 0.34f))
                drawPath(Path().apply {
                    moveTo(w * 0.25f, h * 0.33f); lineTo(w * 0.62f, 0f); lineTo(w * 0.62f, h)
                    lineTo(w * 0.25f, h * 0.67f); close()
                }, color)
                drawArc(color, -45f, 90f, false, Offset(w * 0.45f, h * 0.2f), Size(w * 0.5f, h * 0.6f), style = stroke)
            }
            Glyph.CLOSE -> {
                drawLine(color, Offset(0f, 0f), Offset(w, h), stroke.width * 1.4f, StrokeCap.Round)
                drawLine(color, Offset(w, 0f), Offset(0f, h), stroke.width * 1.4f, StrokeCap.Round)
            }
            Glyph.FOLDER -> {
                drawRect(color, Offset(0f, h * 0.12f), Size(w * 0.45f, h * 0.2f))
                drawRect(color, Offset(0f, h * 0.25f), Size(w, h * 0.65f))
            }
            Glyph.NOTE -> {
                drawOval(color, Offset(0f, h * 0.62f), Size(w * 0.5f, h * 0.36f))
                drawRect(color, Offset(w * 0.4f, h * 0.05f), Size(w * 0.1f, h * 0.78f))
                drawPath(Path().apply {
                    moveTo(w * 0.45f, h * 0.05f); lineTo(w * 0.95f, h * 0.22f); lineTo(w * 0.95f, h * 0.42f)
                    lineTo(w * 0.45f, h * 0.27f); close()
                }, color)
            }
            Glyph.STAR -> {
                val cx = w / 2
                val cy = h / 2 + h * 0.04f
                val outer = min(w, h) / 2
                val inner = outer * 0.45f
                val p = Path()
                for (i in 0 until 10) {
                    val ang = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) outer else inner
                    val x = cx + (r * cos(ang)).toFloat()
                    val y = cy + (r * sin(ang)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, color)
            }
            Glyph.RADIO -> {
                drawCircle(color, w * 0.12f, Offset(w / 2, h / 2))
                drawArc(color, -50f, 100f, false, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f), style = stroke)
                drawArc(color, 130f, 100f, false, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.6f), style = stroke)
                drawArc(color, -50f, 100f, false, Offset(0f, 0f), Size(w, h), style = stroke)
                drawArc(color, 130f, 100f, false, Offset(0f, 0f), Size(w, h), style = stroke)
            }
        }
    }
}

// ============================================================ LCD bits

@Composable
fun LcdBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val s = LocalSkin.current
    val shape = if (s.isClassic) RectangleShape else RoundedCornerShape(8.dp)
    val edge = if (s.isClassic) Modifier.bevel(s.edgeDark, s.edgeLight) else Modifier.border(1.dp, s.edgeDark, shape)
    Box(modifier.clip(shape).background(s.lcdBrush).then(edge), content = content)
}

/** Sunken list area (playlist and library lists). */
@Composable
fun ListBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val s = LocalSkin.current
    val shape = if (s.isClassic) RectangleShape else RoundedCornerShape(6.dp)
    val edge = if (s.isClassic) Modifier.bevel(s.edgeDark, s.edgeLight) else Modifier.border(1.dp, s.edgeDark, shape)
    Box(modifier.clip(shape).background(s.listBg).then(edge), content = content)
}

@Composable
fun FaceLabel(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    color: Color? = null,
    size: TextUnit = 9.sp,
    bold: Boolean = true,
) {
    val s = LocalSkin.current
    val c = color ?: s.labelText
    Text(
        text, color = if (active) c else c.copy(alpha = 0.3f), fontSize = size,
        fontFamily = s.uiFont, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

@Composable
fun StateGlyph(c: PlayerController, size: Dp = 10.dp) {
    val s = LocalSkin.current
    val g = when (c.playState) {
        PlayState.PLAYING -> Glyph.PLAY
        PlayState.PAUSED -> Glyph.PAUSE
        PlayState.STOPPED -> Glyph.STOP
    }
    GlyphIcon(g, s.lcdText, Modifier.size(size))
}

/** Big elapsed/remaining clock. Tap to switch; blinks while paused. */
@Composable
fun TimeDisplay(c: PlayerController, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    var lit by remember { mutableStateOf(true) }
    LaunchedEffect(c.playState) {
        lit = true
        if (c.playState == PlayState.PAUSED) while (true) {
            delay(600)
            lit = !lit
        }
    }
    val remaining = c.showRemaining && c.durationMs > 0
    val ms = if (remaining) c.durationMs - c.positionMs else c.positionMs
    Text(
        text = (if (remaining) "-" else "") + formatClock(ms),
        color = if (c.playState == PlayState.STOPPED) s.lcdDim else s.lcdText,
        fontSize = fontSize,
        fontFamily = s.lcdFont,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.alpha(if (lit) 1f else 0.12f).tap { c.showRemaining = !c.showRemaining },
    )
}

fun marqueeText(c: PlayerController, upper: Boolean): String {
    val t = c.currentTrack
    val txt = when {
        t == null -> "RetroPlay  -  tap Eject or Add to load music"
        t.isStream -> "${c.current + 1}. ${t.title} (LIVE RADIO)"
        else -> "${c.current + 1}. ${t.display} (${formatTime(if (c.durationMs > 0) c.durationMs else t.durationMs)})"
    }
    return (if (upper) txt.uppercase() else txt) + "   ***   "
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Marquee(text: String, color: Color, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    Text(
        text, color = color, fontSize = fontSize, fontFamily = s.lcdFont, fontWeight = FontWeight.Bold,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(iterations = Int.MAX_VALUE),
    )
}

/** Single-line text input that looks like an LCD. */
@Composable
fun LcdTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    val s = LocalSkin.current
    LcdBox(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = SkinTextStyle.copy(color = s.lcdText, fontSize = 13.sp, fontFamily = s.lcdFont),
            cursorBrush = SolidColor(s.lcdText),
            keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(onSearch = { onDone?.invoke() }, onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(horizontal = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(
                        placeholder, color = s.lcdDim, fontSize = 13.sp, fontFamily = s.lcdFont, maxLines = 1,
                    )
                    inner()
                }
            },
        )
    }
}

/** Album cover (or a note glyph when there's none). Loads off the main thread and caches. */
@Composable
fun ArtImage(track: Track?, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    val ctx = LocalContext.current
    val art by produceState(initialValue = track?.let { ArtLoader.cached(it) }, track?.uri, track?.albumId) {
        value = if (track == null) null else withContext(Dispatchers.IO) { ArtLoader.load(ctx, track) }
    }
    LcdBox(modifier) {
        val img = art
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GlyphIcon(
                if (track?.isStream == true) Glyph.RADIO else Glyph.NOTE, s.lcdDim,
                Modifier.fillMaxSize(0.38f).align(Alignment.Center),
            )
        }
    }
}

/** Spectrum analyser / oscilloscope. Tap to cycle modes (or to grant the permission). */
@Composable
fun VisDisplay(c: PlayerController, modifier: Modifier = Modifier, segmented: Boolean = false) {
    val s = LocalSkin.current
    val a = LocalActions.current
    Canvas(modifier.tap { if (!c.hasVisPermission) a.requestVis() else c.cycleVisMode() }) {
        val w = size.width
        val h = size.height
        val px = 1.dp.toPx()
        if (!c.hasVisPermission || c.visMode == VisMode.OFF) {
            var x = 0f
            while (x < w) {
                drawRect(s.lcdDim, Offset(x, h - 2 * px), Size(px, px))
                x += 3 * px
            }
            return@Canvas
        }
        when (c.visMode) {
            VisMode.SPECTRUM -> {
                val bars = c.spectrum
                val peaks = c.peaks
                val n = bars.size
                val slot = w / n
                val bw = slot * (if (s.isClassic) 0.8f else 0.66f)
                val brush = Brush.verticalGradient(s.visBars, startY = 0f, endY = h)
                for (i in 0 until n) {
                    val x = i * slot
                    val bh = bars[i] * h
                    if (bh > 0.5f) drawRect(brush, Offset(x, h - bh), Size(bw, bh))
                    if (peaks[i] > 0.01f) {
                        val py = (h - peaks[i] * h - 1.5f * px).coerceAtLeast(0f)
                        drawRect(s.visPeak, Offset(x, py), Size(bw, 1.5f * px))
                    }
                }
                if (segmented) {
                    var y = h - 3 * px
                    while (y > 0) {
                        drawRect(s.lcdBottom.copy(alpha = 0.8f), Offset(0f, y), Size(w, px))
                        y -= 3 * px
                    }
                }
            }
            VisMode.SCOPE -> {
                val wf = c.waveform
                if (wf.size > 1) {
                    val p = Path()
                    wf.forEachIndexed { i, v ->
                        val x = i * w / (wf.size - 1)
                        val y = h / 2 - v * h / 2
                        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, s.lcdText, style = Stroke(1.5f * px))
                } else {
                    drawLine(s.lcdDim, Offset(0f, h / 2), Offset(w, h / 2), px)
                }
            }
            VisMode.OFF -> Unit
        }
    }
}

// ============================================================ sliders

/** Bare drag surface: reports 0..1 along its axis, consumes the gesture so the page doesn't scroll. */
@Composable
fun SkinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
    render: DrawScope.(Float) -> Unit,
) {
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onFinished)
    Canvas(
        modifier.pointerInput(vertical) {
            fun pos(o: Offset) = (if (vertical) 1f - o.y / size.height else o.x / size.width).coerceIn(0f, 1f)
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                change(pos(down.position))
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                    change(pos(ch.position))
                    ch.consume()
                }
                finish()
            }
        }
    ) { render(value) }
}

@Composable
fun HBar(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color? = null,
    centered: Boolean = false,
    onFinished: () -> Unit = {},
) {
    val s = LocalSkin.current
    SkinSlider(value, onChange, vertical = false, modifier = modifier, onFinished = onFinished) { v ->
        val w = size.width
        val h = size.height
        if (s.isClassic) {
            val th = (16.dp.toPx()).coerceAtMost(w)
            val gh = h * 0.45f
            val gy = (h - gh) / 2
            drawRect(s.groove, Offset(0f, gy), Size(w, gh))
            val x = (w - th) * v
            val cx = x + th / 2
            if (fill != null) {
                if (centered && abs(v - 0.5f) < 0.002f) drawRect(fill, Offset(0f, gy + 1), Size(w, gh - 2))
                else if (centered) drawRect(fill, Offset(min(w / 2, cx), gy + 1), Size(abs(cx - w / 2), gh - 2))
                else drawRect(fill, Offset(0f, gy + 1), Size(cx, gh - 2))
            }
            bevelRect(Offset(x, 0f), Size(th, h), s.thumbTop, s.thumbBottom, Color.White, s.edgeDark)
            for (k in -1..1) {
                val lx = cx + k * 3.dp.toPx()
                drawLine(s.edgeDark, Offset(lx, h * 0.3f), Offset(lx, h * 0.7f), 1.dp.toPx())
            }
        } else {
            val gh = 6.dp.toPx()
            val gy = (h - gh) / 2
            val r = CornerRadius(gh / 2)
            drawRoundRect(s.groove, Offset(0f, gy), Size(w, gh), r)
            val rad = (h / 2).coerceAtMost(9.dp.toPx())
            val cx = rad + (w - 2 * rad) * v
            if (fill != null) {
                if (centered && abs(v - 0.5f) < 0.002f) drawRoundRect(fill, Offset(0f, gy), Size(w, gh), r)
                else if (centered) drawRoundRect(fill, Offset(min(w / 2, cx), gy), Size(abs(cx - w / 2), gh), r)
                else drawRoundRect(fill, Offset(0f, gy), Size(cx, gh), r)
            }
            drawCircle(
                Brush.verticalGradient(listOf(s.thumbTop, s.thumbBottom), startY = h / 2 - rad, endY = h / 2 + rad),
                rad, Offset(cx, h / 2),
            )
            drawCircle(s.edgeDark, rad, Offset(cx, h / 2), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Vertical EQ slider. value 0..1, 0.5 = 0 dB. */
@Composable
fun VBar(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    SkinSlider(value, onChange, vertical = true, modifier = modifier) { v ->
        val w = size.width
        val h = size.height
        if (s.isClassic) {
            val th = 10.dp.toPx()
            val tw = (w * 0.8f).coerceAtMost(16.dp.toPx())
            val bw = 5.dp.toPx()
            val bx = (w - bw) / 2
            val y = (h - th) * (1 - v)
            drawRect(Color.Black, Offset(bx - 1, 0f), Size(bw + 2, h))
            drawRect(
                Brush.verticalGradient(s.visBars, startY = 0f, endY = h),
                Offset(bx, y + th / 2), Size(bw, h - y - th / 2),
            )
            bevelRect(Offset((w - tw) / 2, y), Size(tw, th), s.thumbTop, s.thumbBottom, Color.White, s.edgeDark)
            drawLine(s.edgeDark, Offset((w - tw) / 2 + 3, y + th / 2), Offset((w + tw) / 2 - 3, y + th / 2), 1.dp.toPx())
        } else {
            val gw = 5.dp.toPx()
            val gx = (w - gw) / 2
            val r = CornerRadius(gw / 2)
            drawRoundRect(s.groove, Offset(gx, 0f), Size(gw, h), r)
            val rad = (w / 2).coerceAtMost(8.dp.toPx())
            val cy = rad + (h - 2 * rad) * (1 - v)
            drawRoundRect(s.seekFill, Offset(gx, min(h / 2, cy)), Size(gw, abs(cy - h / 2)), r)
            drawCircle(
                Brush.verticalGradient(listOf(s.thumbTop, s.thumbBottom), startY = cy - rad, endY = cy + rad),
                rad, Offset(w / 2, cy),
            )
            drawCircle(s.edgeDark, rad, Offset(w / 2, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

val SEEK_YELLOW = Color(0xFFFFF59D)

/** Track position bar; only seeks when the finger lifts, like the original. Idle for live radio. */
@Composable
fun SeekBar(c: PlayerController, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    var drag by remember { mutableStateOf<Float?>(null) }
    val isRadio = c.currentTrack?.isStream == true
    val running = isRadio && c.playState == PlayState.PLAYING
    val pos = if (c.durationMs > 0) (c.positionMs.toFloat() / c.durationMs).coerceIn(0f, 1f) else 0f
    HBar(
        value = if (isRadio) RadioSweep.fraction(c.currentTrack, running) else drag ?: pos,
        onChange = { if (!isRadio && c.durationMs > 0) drag = it },
        modifier = modifier,
        fill = SEEK_YELLOW,
        onFinished = {
            drag?.let { c.seekTo(it) }
            drag = null
        },
    )
}

// ============================================================ menus

data class MenuItem(val label: String, val checked: Boolean? = null, val action: () -> Unit)

@Composable
fun MenuAnchor(items: () -> List<MenuItem>, anchor: @Composable (open: () -> Unit) -> Unit) {
    val s = LocalSkin.current
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = s.menuBg) {
            SkinLocals {
                Column {
                    if (open) items().forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (when (m.checked) { true -> "✓  "; false -> "    "; null -> "" }) + m.label,
                                    color = s.menuText, fontFamily = s.uiFont, fontSize = 14.sp,
                                )
                            },
                            onClick = {
                                open = false
                                m.action()
                            },
                        )
                    }
                }
            }
        }
    }
}
