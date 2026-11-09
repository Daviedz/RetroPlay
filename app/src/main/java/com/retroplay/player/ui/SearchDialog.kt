package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retroplay.player.player.LibState
import com.retroplay.player.player.PlayerController

private enum class SearchBy(val label: String) { SONG("SONG"), ARTIST("ARTIST") }

/** Song search window (opened from the playlist's MISC button): by song title or by artist. */
@Composable
fun SearchDialog(c: PlayerController) {
    if (!c.showSearch) return
    val s = LocalSkin.current
    val a = LocalActions.current
    var query by remember { mutableStateOf("") }
    var by by remember { mutableStateOf(SearchBy.SONG) }
    // Search the whole device library plus anything already in the playlist
    LaunchedEffect(Unit) { if (c.libraryState == LibState.NOT_LOADED) a.requestLibrary() }
    val pool = remember(c.library, c.playlist.size) {
        // The same song can be in both lists under different addresses, so de-duplicate by what it is too
        (c.playlist.filter { !it.isStream } + c.library)
            .distinctBy { it.uri }
            .distinctBy { Triple(it.title.trim().lowercase(), it.artist.trim().lowercase(), it.durationMs / 1000) }
    }
    val q = query.trim().lowercase()
    val results = remember(q, by, pool) {
        if (q.isEmpty()) emptyList()
        else pool.filter {
            val field = if (by == SearchBy.SONG) it.title else it.artist
            field.lowercase().contains(q)
        }.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    }
    val close = { c.showSearch = false }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SkinLocals {
            Box(Modifier.fillMaxSize().background(s.desktop).padding(10.dp)) {
                SkinWindow("SEARCH", Modifier.fillMaxSize(), onClose = close, fill = true) {
                    Spacer(Modifier.height(8.dp))
                    LcdTextField(query, { query = it }, "Type a ${by.label.lowercase()} name", Modifier.fillMaxWidth().height(34.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SearchBy.entries.forEach { m ->
                            LedToggle(m.label, by == m, { by = m }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FaceLabel(
                        when {
                            c.libraryState == LibState.LOADING -> "Scanning your music…"
                            q.isEmpty() -> "${pool.size} songs available"
                            else -> "${results.size} found"
                        },
                        size = 10.sp, bold = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    ListBox(Modifier.fillMaxWidth().weight(1f)) {
                        if (q.isNotEmpty() && results.isEmpty()) {
                            Text(
                                "No matches.", color = s.listText.copy(alpha = 0.7f), fontSize = 13.sp,
                                fontFamily = s.listFont, modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                            itemsIndexed(results, key = { i, t -> "$i|${t.uri}" }) { i, t ->
                                TrackRow(
                                    i, t,
                                    isCurrent = c.currentTrack?.uri == t.uri, isSelected = false,
                                    modifier = Modifier.clickable { c.playTrack(t); close() },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SkinTextButton(
                            "ADD ALL TO PLAYLIST",
                            { if (results.isNotEmpty()) { c.appendTracks(results); close() } },
                            Modifier.weight(1f).fillMaxHeight(), fontSize = 10.sp,
                        )
                        SkinTextButton(
                            "PLAY ALL",
                            { if (results.isNotEmpty()) { c.replaceAndPlay(results); close() } },
                            Modifier.weight(1f).fillMaxHeight(), fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}
