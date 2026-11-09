package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.Updater

@Composable
fun PlayerScreen(c: PlayerController, actions: UiActions) {
    val skin = skinFor(c.skin)
    var showHelp by remember { mutableStateOf(false) }
    val a = remember(actions) { actions.copy(showHelp = { showHelp = true }) }

    MaterialTheme {
        CompositionLocalProvider(LocalSkin provides skin, LocalActions provides a) {
            SkinLocals {
                BoxWithConstraints(
                    Modifier.fillMaxSize().background(skin.desktop).safeDrawingPadding(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    val viewport = maxHeight - 20.dp // minus the 10.dp padding top and bottom
                    Column(
                        Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                    ) {
                        WindowStack(viewport = viewport, minFill = 190.dp, gap = 8.dp) {
                            if (skin.isClassic) ClassicMainWindow(c) else ModernMainWindow(c)
                            if (c.showArt) ArtWindow(c)
                            if (c.showEq) EqWindow(c)
                            if (c.showPl) Box(Modifier.layoutId(FILL)) { PlaylistWindow(c, Modifier.fillMaxSize()) }
                        }
                    }
                }
                LibraryDialog(c)
                SearchDialog(c)
                LyricsOverlay(c)
            }
            Updater.available?.let { u ->
                val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
                AlertDialog(
                    onDismissRequest = { if (!Updater.busy) Updater.available = null },
                    confirmButton = {
                        TextButton(enabled = !Updater.busy, onClick = { Updater.install(app, u) }) {
                            Text(if (Updater.busy) "Downloading ${Updater.progress.coerceAtLeast(0)}%" else "Update")
                        }
                    },
                    dismissButton = {
                        TextButton(enabled = !Updater.busy, onClick = { Updater.available = null }) { Text("Later") }
                    },
                    title = { Text("RetroPlay ${u.version} is available") },
                    text = { Text(u.notes.ifBlank { "A new version is ready to install." }) },
                )
            }
            if (showHelp) {
                AlertDialog(
                    onDismissRequest = { showHelp = false },
                    confirmButton = { TextButton(onClick = { showHelp = false }) { Text("OK") } },
                    title = { Text("RetroPlay") },
                    text = { Text(HELP_TEXT) },
                )
            }
        }
    }
}

private const val FILL = "fill"

/**
 * Stacks the windows vertically. The window tagged [FILL] (the playlist) gets all the
 * screen height the others leave, so its buttons sit at the bottom of the screen.
 * If there isn't room for [minFill], it keeps that height and the page scrolls instead.
 */
@Composable
private fun WindowStack(viewport: Dp, minFill: Dp, gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val w = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val fillM = measurables.firstOrNull { it.layoutId == FILL }
        val fixed = measurables.filter { it !== fillM }
            .map { it to it.measure(Constraints(minWidth = w, maxWidth = w)) }
        val gaps = gapPx * (measurables.size - 1).coerceAtLeast(0)
        val used = fixed.sumOf { it.second.height } + gaps
        val fillH = maxOf(minFill.roundToPx(), viewport.roundToPx() - used)
        val fillP = fillM?.measure(Constraints.fixed(w, fillH))
        val placeables = measurables.map { m ->
            if (m === fillM) fillP!! else fixed.first { it.first === m }.second
        }
        val total = placeables.sumOf { it.height } + gaps
        layout(w, total) {
            var y = 0
            placeables.forEach {
                it.place(0, y)
                y += it.height + gapPx
            }
        }
    }
}
