package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.player.player.LibTab
import com.retroplay.player.player.PlayState
import com.retroplay.player.player.PlayerController

// =====================================================================
//  Classic Pixel — late-90s look: dark slate face, black LCD, bevels
// =====================================================================

@Composable
fun ClassicMainWindow(c: PlayerController) {
    val s = LocalSkin.current
    val a = LocalActions.current
    SkinWindow(
        title = "RETROPLAY",
        leading = {
            MenuAnchor({ classicMainMenu(c, a) }) { open ->
                Box(Modifier.size(22.dp).tap(open), contentAlignment = Alignment.Center) {
                    Text("≡", color = s.titleText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Left: clock + visualizer
            LcdBox(Modifier.weight(0.42f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StateGlyph(c, 9.dp)
                        Spacer(Modifier.width(6.dp))
                        TimeDisplay(c, 24.sp)
                    }
                    Spacer(Modifier.height(3.dp))
                    VisDisplay(c, Modifier.fillMaxWidth().weight(1f))
                }
            }
            // Right: scrolling title / stream info / window toggles — three equal rows
            Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                LcdBox(Modifier.fillMaxWidth().height(22.dp)) {
                    Marquee(
                        marqueeText(c, upper = true), s.lcdText, 11.sp,
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoCell(c.info?.kbps)
                    FaceLabel("kbps", Modifier.padding(start = 3.dp, end = 5.dp))
                    InfoCell(c.info?.khz)
                    FaceLabel("kHz", Modifier.padding(start = 3.dp))
                    Spacer(Modifier.weight(1f))
                    val stereo = c.info?.stereo
                    Column(horizontalAlignment = Alignment.End) {
                        FaceLabel("mono", active = stereo == false, size = 8.sp)
                        FaceLabel("stereo", active = stereo == true, size = 8.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    WindowToggle("EQ", c.showEq) { c.showEq = !c.showEq }
                    WindowToggle("PL", c.showPl) { c.showPl = !c.showPl }
                    WindowToggle("ART", c.showArt) { c.showArt = !c.showArt }
                    WindowToggle("ML", c.libraryTab != null) { c.libraryTab = LibTab.ALBUMS }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
            FaceLabel("VOL", Modifier.width(26.dp))
            HBar(
                c.volume, { c.setVolumeLevel(it) }, Modifier.weight(0.62f).fillMaxHeight(),
                fill = volumeColor(c.volume), onFinished = { c.endVolumeDrag() },
            )
            Spacer(Modifier.width(10.dp))
            FaceLabel("BAL", Modifier.width(26.dp))
            HBar(
                (c.balance + 1f) / 2f, { c.setBalanceLevel(it * 2f - 1f) }, Modifier.weight(0.38f).fillMaxHeight(),
                fill = if (c.balance == 0f) Color(0xFF33C24A) else Color(0xFFE53935), centered = true,
            )
        }
        Spacer(Modifier.height(10.dp))
        SeekBar(c, Modifier.fillMaxWidth().height(14.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TransportButton(Glyph.PREV) { c.prev() }
            TransportButton(Glyph.PLAY, active = c.playState == PlayState.PLAYING) { onPlayPressed(c, a) }
            TransportButton(Glyph.PAUSE, active = c.playState == PlayState.PAUSED) { c.pause() }
            TransportButton(Glyph.STOP, active = c.playState == PlayState.STOPPED && c.currentTrack != null) { c.stop() }
            TransportButton(Glyph.NEXT) { c.next() }
            Spacer(Modifier.width(3.dp))
            TransportButton(Glyph.EJECT) { a.openFiles() }
            Spacer(Modifier.width(3.dp))
            LedToggle("SHUF", c.shuffleOn, { c.toggleShuffle() }, Modifier.weight(1.6f).fillMaxHeight())
            LedToggle("", c.repeatOn, { c.toggleRepeat() }, Modifier.weight(1.1f).fillMaxHeight(), glyph = Glyph.REPEAT)
        }
    }
}

@Composable
private fun RowScope.WindowToggle(label: String, on: Boolean, onClick: () -> Unit) {
    LedToggle(label, on, onClick, Modifier.weight(1f).fillMaxHeight())
}

/** Transport button; the one matching the current state lights up green. */
@Composable
private fun RowScope.TransportButton(g: Glyph, active: Boolean = false, onClick: () -> Unit) {
    val s = LocalSkin.current
    SkinButton(onClick, Modifier.weight(1f).fillMaxHeight()) {
        GlyphIcon(g, if (active) s.activeGlyph else s.btnText, Modifier.size(13.dp))
        if (active) Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .size(5.dp)
                .background(s.ledOn, RectangleShape)
        )
    }
}

@Composable
private fun InfoCell(value: Int?) {
    val s = LocalSkin.current
    Box(
        Modifier.width(28.dp).height(16.dp).background(s.lcdTop).bevel(s.edgeDark, s.edgeLight),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            value?.takeIf { it > 0 }?.toString() ?: "",
            color = s.lcdText, fontSize = 10.sp, fontFamily = s.lcdFont, fontWeight = FontWeight.Bold,
            maxLines = 1, modifier = Modifier.padding(end = 3.dp),
        )
    }
}

/** Volume bar shifts green → orange → red as it gets louder. */
private fun volumeColor(v: Float): Color = when {
    v < 0.5f -> lerp(Color(0xFF2DB84A), Color(0xFFE8A33A), v / 0.5f)
    else -> lerp(Color(0xFFE8A33A), Color(0xFFE0402A), (v - 0.5f) / 0.5f)
}

// =====================================================================
//  Chrome 2K — early-2000s look: brushed silver, blue LCD, round buttons
// =====================================================================

@Composable
fun ModernMainWindow(c: PlayerController) {
    val s = LocalSkin.current
    val a = LocalActions.current
    SkinWindow(title = "RETROPLAY") {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            MenuBarItem("Play") { playMenu(c, a) }
            MenuBarItem("Options") { optionsMenu(c) }
            MenuBarItem("View") { viewMenu(c, a) }
            MenuBarItem("Library") { libraryMenu(c, a) }
            MenuBarItem("Help") { helpMenu(a) }
        }
        Row(Modifier.fillMaxWidth().height(96.dp)) {
            LcdBox(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        StateGlyph(c, 10.dp)
                        Spacer(Modifier.width(6.dp))
                        TimeDisplay(c, 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Badge("KBPS", c.info?.kbps?.takeIf { it > 0 }?.toString() ?: "--")
                            Badge("KHZ", c.info?.khz?.takeIf { it > 0 }?.toString() ?: "--")
                            Badge(null, if (c.info?.stereo == false) "MONO" else "STEREO")
                        }
                        Spacer(Modifier.width(8.dp))
                        VisDisplay(c, Modifier.weight(1f).fillMaxHeight(0.85f), segmented = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    Marquee(marqueeText(c, upper = true), s.lcdText, 12.sp, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                RoundToggle(Glyph.REPEAT, c.repeatOn) { c.toggleRepeat() }
                RoundToggle(Glyph.SHUFFLE, c.shuffleOn) { c.toggleShuffle() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
            SeekBar(c, Modifier.weight(1f).height(18.dp))
            Spacer(Modifier.width(6.dp))
            SkinButton({ a.openFiles() }, Modifier.width(32.dp).fillMaxHeight()) {
                GlyphIcon(Glyph.EJECT, s.btnText, Modifier.size(12.dp))
            }
            Spacer(Modifier.width(3.dp))
            PillToggle("PL", c.showPl) { c.showPl = !c.showPl }
            PillToggle("EQ", c.showEq) { c.showEq = !c.showEq }
            PillToggle("ART", c.showArt) { c.showArt = !c.showArt }
            PillToggle("ML", c.libraryTab != null) { c.libraryTab = LibTab.ALBUMS }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundTransport(Glyph.PREV) { c.prev() }
            RoundTransport(Glyph.PLAY, active = c.playState == PlayState.PLAYING) { onPlayPressed(c, a) }
            RoundTransport(Glyph.PAUSE, active = c.playState == PlayState.PAUSED) { c.pause() }
            RoundTransport(Glyph.STOP, active = c.playState == PlayState.STOPPED && c.currentTrack != null) { c.stop() }
            RoundTransport(Glyph.NEXT) { c.next() }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.SPEAKER, s.labelText, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            HBar(
                c.volume, { c.setVolumeLevel(it) }, Modifier.weight(0.6f).fillMaxHeight(),
                fill = s.volumeFill, onFinished = { c.endVolumeDrag() },
            )
            Spacer(Modifier.width(12.dp))
            FaceLabel("BAL", Modifier.width(24.dp))
            HBar(
                (c.balance + 1f) / 2f, { c.setBalanceLevel(it * 2f - 1f) }, Modifier.weight(0.4f).fillMaxHeight(),
                fill = if (c.balance == 0f) Color(0xFF33C24A) else Color(0xFFE53935), centered = true,
            )
        }
    }
}

@Composable
fun MenuBarItem(label: String, items: () -> List<MenuItem>) {
    val s = LocalSkin.current
    MenuAnchor(items) { open ->
        Text(
            label, color = s.labelText, fontSize = 13.sp, fontFamily = s.uiFont, maxLines = 1,
            modifier = Modifier.tap(open).padding(horizontal = 7.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Badge(label: String?, value: String) {
    val s = LocalSkin.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) {
            FaceLabel("$label:", Modifier.width(30.dp), color = s.lcdLabel, size = 8.sp)
        }
        Box(
            Modifier.background(s.lcdLabel.copy(alpha = 0.25f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp, vertical = 1.dp),
        ) { FaceLabel(value, color = s.lcdText, size = 8.sp) }
    }
}

@Composable
private fun RowScope.RoundTransport(g: Glyph, active: Boolean = false, onClick: () -> Unit) {
    val s = LocalSkin.current
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        SkinButton(
            onClick,
            Modifier
                .size(42.dp)
                .then(if (active) Modifier.border(2.dp, s.ledOn, CircleShape) else Modifier),
            shape = CircleShape,
        ) {
            GlyphIcon(g, if (active) s.activeGlyph else s.btnText, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun RoundToggle(g: Glyph, on: Boolean, onClick: () -> Unit) {
    val s = LocalSkin.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        SkinButton(onClick, Modifier.size(width = 38.dp, height = 28.dp)) {
            GlyphIcon(g, if (on) s.activeGlyph else s.btnText, Modifier.size(14.dp))
        }
        Spacer(Modifier.width(3.dp))
        Box(Modifier.size(6.dp).background(if (on) s.ledOn else s.ledOff, CircleShape))
    }
}

@Composable
private fun PillToggle(label: String, on: Boolean, onClick: () -> Unit) {
    val s = LocalSkin.current
    SkinButton(onClick, Modifier.padding(start = 3.dp).width(36.dp).fillMaxHeight()) {
        Text(
            label, color = if (on) s.activeGlyph else s.btnText.copy(alpha = 0.5f),
            fontSize = 11.sp, fontFamily = s.uiFont, fontWeight = FontWeight.Bold, maxLines = 1,
        )
    }
}
