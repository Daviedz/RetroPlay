package com.retroplay.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.player.player.EQ_LABELS
import com.retroplay.player.player.EQ_PRESETS
import com.retroplay.player.player.PlayerController

@Composable
fun EqWindow(c: PlayerController) {
    val s = LocalSkin.current
    SkinWindow(title = "EQUALIZER", onClose = { c.showEq = false }) {
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            LedToggle("ON", c.eqOn, { c.toggleEq() }, Modifier.fillMaxHeight())
            Spacer(Modifier.width(4.dp))
            SkinTextButton("RESET", { c.applyPreset(EQ_PRESETS.first()) }, Modifier.fillMaxHeight())
            Spacer(Modifier.width(6.dp))
            LcdBox(Modifier.weight(1f).fillMaxHeight()) { EqCurve(c) }
            Spacer(Modifier.width(6.dp))
            MenuAnchor({ EQ_PRESETS.map { p -> MenuItem(p.name) { c.applyPreset(p) } } }) { open ->
                SkinTextButton("PRESETS", open, Modifier.height(24.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(118.dp)) {
            EqColumn("PRE", c.preamp) { c.setPreampDb(it) }
            Column(
                Modifier.width(30.dp).fillMaxHeight().padding(bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FaceLabel("+12", size = 8.sp)
                FaceLabel("0", size = 8.sp)
                FaceLabel("-12", size = 8.sp)
            }
            for (i in 0 until 10) {
                EqColumn(EQ_LABELS[i], c.bands[i]) { c.setBandDb(i, it) }
            }
        }
    }
}

@Composable
private fun RowScope.EqColumn(label: String, db: Float, onChange: (Float) -> Unit) {
    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        VBar((db + 12f) / 24f, { onChange(it * 24f - 12f) }, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(2.dp))
        FaceLabel(label, size = 8.sp)
    }
}

/** Little preview of the EQ curve, drawn through the band values. */
@Composable
private fun EqCurve(c: PlayerController) {
    val s = LocalSkin.current
    Canvas(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp, vertical = 3.dp)) {
        val w = size.width
        val h = size.height
        drawLine(s.lcdDim, Offset(0f, h / 2), Offset(w, h / 2), 1f)
        val pts = c.bands.mapIndexed { i, db ->
            Offset(i * w / 9f, h / 2 - (db / 12f) * (h / 2))
        }
        val p = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val cur = pts[i]
                val mx = (prev.x + cur.x) / 2
                cubicTo(mx, prev.y, mx, cur.y, cur.x, cur.y)
            }
        }
        drawPath(p, if (c.eqOn) s.eqCurve else s.lcdDim, style = Stroke(1.5.dp.toPx()))
    }
}
