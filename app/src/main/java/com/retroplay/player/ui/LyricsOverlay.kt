package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retroplay.player.player.LyricsResult
import com.retroplay.player.player.PlayerController

/** Full-screen lyrics for the current song, fetched online. Synced lyrics follow the music. */
@Composable
fun LyricsOverlay(c: PlayerController) {
    if (!c.showLyrics) return
    val s = LocalSkin.current
    val close = { c.closeLyrics() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SkinLocals {
            Box(Modifier.fillMaxSize().background(s.desktop).padding(10.dp)) {
                SkinWindow("LYRICS", Modifier.fillMaxSize(), onClose = close, fill = true) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        c.currentTrack?.display ?: "", color = s.lcdText, fontSize = 15.sp, fontFamily = s.lcdFont,
                        fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    ListBox(Modifier.fillMaxWidth().weight(1f)) {
                        val ly = c.lyrics
                        when {
                            c.lyricsLoading -> Note("Fetching lyrics…")
                            ly == null -> Note(c.lyricsError ?: "No lyrics.")
                            else -> LyricLines(c, ly)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Note(text: String) {
    val s = LocalSkin.current
    Text(
        text, color = s.listText.copy(alpha = 0.8f), fontSize = 14.sp, fontFamily = s.listFont,
        textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(20.dp),
    )
}

@Composable
private fun LyricLines(c: PlayerController, ly: LyricsResult) {
    val s = LocalSkin.current
    val state = rememberLazyListState()
    // For synced lyrics, the active line is the last one whose time has passed
    val active = if (ly.synced) ly.lines.indexOfLast { it.timeMs in 0..c.positionMs } else -1
    LaunchedEffect(active) {
        if (active >= 0) state.animateScrollToItem((active - 3).coerceAtLeast(0))
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        itemsIndexed(ly.lines) { i, line ->
            val on = i == active
            Text(
                line.text.ifBlank { " " },
                color = if (on) s.listCurrent else s.listText,
                fontSize = if (on) 20.sp else 17.sp, fontFamily = s.listFont,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
            )
        }
    }
}
