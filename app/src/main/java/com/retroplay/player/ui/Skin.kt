package com.retroplay.player.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retroplay.player.player.SkinId

/**
 * Every colour and shape choice a skin makes. Both skins are original designs
 * that borrow the *era* (late-90s pixel LCD, early-2000s glossy chrome) rather
 * than copying any real skin's artwork.
 */
@Immutable
data class Skin(
    val id: SkinId,
    val desktop: Color,
    val faceTop: Color, val faceBottom: Color,
    val edgeLight: Color, val edgeDark: Color,
    val titleTop: Color, val titleBottom: Color, val titleText: Color, val titleStripe: Color,
    val lcdTop: Color, val lcdBottom: Color, val lcdText: Color, val lcdDim: Color, val lcdLabel: Color,
    val btnTop: Color, val btnBottom: Color, val btnText: Color,
    val btnPressedTop: Color, val btnPressedBottom: Color,
    val ledOn: Color, val ledOff: Color,
    /** Glyph colour of the transport button that matches the current state (green). */
    val activeGlyph: Color,
    val volumeFill: Color, val balanceFill: Color, val seekFill: Color,
    val groove: Color,
    val thumbTop: Color, val thumbBottom: Color,
    val eqCurve: Color, val eqCurveBg: Color,
    val visBars: List<Color>, val visPeak: Color,
    val listBg: Color, val listText: Color, val listCurrent: Color, val listSelBg: Color,
    val menuBg: Color, val menuText: Color,
    val labelText: Color,
    val corner: Dp,
    val lcdFont: FontFamily, val uiFont: FontFamily, val listFont: FontFamily,
) {
    val isClassic: Boolean get() = id == SkinId.CLASSIC
    val faceBrush: Brush get() = Brush.verticalGradient(listOf(faceTop, faceBottom))
    val lcdBrush: Brush get() = Brush.verticalGradient(listOf(lcdTop, lcdBottom))
}

val ClassicSkin = Skin(
    id = SkinId.CLASSIC,
    desktop = Color(0xFF0E0E16),
    faceTop = Color(0xFF3A3A58), faceBottom = Color(0xFF24243A),
    edgeLight = Color(0xFF6E6E96), edgeDark = Color(0xFF0A0A12),
    titleTop = Color(0xFF34344F), titleBottom = Color(0xFF26263D),
    titleText = Color(0xFFE4DDBA), titleStripe = Color(0xFFC8B36A),
    lcdTop = Color(0xFF000000), lcdBottom = Color(0xFF000000),
    lcdText = Color(0xFF00E800), lcdDim = Color(0xFF0F4A16), lcdLabel = Color(0xFFC8C8DC),
    btnTop = Color(0xFFD2D3E2), btnBottom = Color(0xFF8E90A8), btnText = Color(0xFF1C1C30),
    btnPressedTop = Color(0xFF7E8098), btnPressedBottom = Color(0xFFAEB0C4),
    ledOn = Color(0xFF22FF22), ledOff = Color(0xFF1D3D1D), activeGlyph = Color(0xFF0FA824),
    volumeFill = Color(0xFFE8A33A), balanceFill = Color(0xFF33C24A), seekFill = Color(0xFF4A4A70),
    groove = Color(0xFF121220),
    thumbTop = Color(0xFFE6E6F0), thumbBottom = Color(0xFF9A9CB2),
    eqCurve = Color(0xFFF0D848), eqCurveBg = Color(0xFF15151F),
    visBars = listOf(Color(0xFFE8402A), Color(0xFFE8C82A), Color(0xFF2AC83A)),
    visPeak = Color(0xFFB8B8C8),
    listBg = Color(0xFF000000), listText = Color(0xFF00E000),
    listCurrent = Color(0xFFFFFFFF), listSelBg = Color(0xFF0A1A9A),
    menuBg = Color(0xFF2A2A40), menuText = Color(0xFFE4DDBA),
    labelText = Color(0xFFC8C8DC),
    corner = 0.dp,
    lcdFont = FontFamily.Monospace, uiFont = FontFamily.Monospace, listFont = FontFamily.SansSerif,
)

val ModernSkin = Skin(
    id = SkinId.MODERN,
    desktop = Color(0xFF2B3F73),
    faceTop = Color(0xFFF4F5F9), faceBottom = Color(0xFFBFC4D2),
    edgeLight = Color(0xFFFFFFFF), edgeDark = Color(0xFF6D7385),
    titleTop = Color(0xFF6C8FDB), titleBottom = Color(0xFF2C4D9E),
    titleText = Color(0xFFE6EEFF), titleStripe = Color(0xFF9DB6EE),
    lcdTop = Color(0xFF2A4CA6), lcdBottom = Color(0xFF0F2163),
    lcdText = Color(0xFFD2E2FF), lcdDim = Color(0xFF4A66B4), lcdLabel = Color(0xFF9DB6EE),
    btnTop = Color(0xFFFFFFFF), btnBottom = Color(0xFFC3C9D8), btnText = Color(0xFF1B2B60),
    btnPressedTop = Color(0xFFA9B4CF), btnPressedBottom = Color(0xFFE6EAF3),
    ledOn = Color(0xFF2FC44A), ledOff = Color(0xFFA8B0C4), activeGlyph = Color(0xFF16A834),
    volumeFill = Color(0xFF3A6EE8), balanceFill = Color(0xFF3A6EE8), seekFill = Color(0xFF3A6EE8),
    groove = Color(0xFF8E96AC),
    thumbTop = Color(0xFFFFFFFF), thumbBottom = Color(0xFFBCC3D6),
    eqCurve = Color(0xFFD2E2FF), eqCurveBg = Color(0xFF14286E),
    visBars = listOf(Color(0xFFFFFFFF), Color(0xFFB8CCF8), Color(0xFF6E90E0)),
    visPeak = Color(0xFFD2E2FF),
    listBg = Color(0xFF233A8E), listText = Color(0xFFDCE6FF),
    listCurrent = Color(0xFFFFFFFF), listSelBg = Color(0xFF4E6FD6),
    menuBg = Color(0xFFF2F3F7), menuText = Color(0xFF1B2B60),
    labelText = Color(0xFF2A3456),
    corner = 12.dp,
    lcdFont = FontFamily.Monospace, uiFont = FontFamily.SansSerif, listFont = FontFamily.SansSerif,
)

fun skinFor(id: SkinId): Skin = when (id) {
    SkinId.CLASSIC -> ClassicSkin
    SkinId.MODERN -> ModernSkin
}

val LocalSkin = staticCompositionLocalOf { ClassicSkin }

/** Things only the Activity can do (system pickers, permission prompts). */
data class UiActions(
    val openFiles: () -> Unit,
    val scanLibrary: () -> Unit,
    val requestVis: () -> Unit,
    val showHelp: () -> Unit,
    /** Ask for the music-library permission if needed, then load the library. */
    val requestLibrary: () -> Unit,
    /** System folder picker → play that folder. */
    val pickFolder: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }
