package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.Track
import com.retroplay.player.player.durationLabel
import com.retroplay.player.player.formatLong
import com.retroplay.player.player.formatTime

@Composable
fun PlaylistWindow(c: PlayerController, modifier: Modifier = Modifier) {
    val s = LocalSkin.current
    val a = LocalActions.current
    SkinWindow(
        title = if (s.isClassic) "PLAYLIST" else "PLAYLIST EDITOR",
        modifier = modifier,
        onClose = { c.showPl = false },
        fill = true,
    ) {
        if (!s.isClassic) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                MenuBarItem("File") { addMenu(c, a) + remMenu(c) }
                MenuBarItem("Playlist") { listOptsMenu(c).take(1) + selMenu(c) }
                MenuBarItem("Sort") { miscMenu(c) }
                MenuBarItem("Help") { helpMenu(a) }
            }
        } else Spacer(Modifier.height(6.dp))

        val listState = rememberLazyListState()
        LaunchedEffect(c.current) {
            val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
            if (c.current >= 0 && c.current !in visible) listState.animateScrollToItem(c.current)
        }
        // Fills whatever height the screen layout gives this window, so the buttons stay visible
        ListBox(Modifier.fillMaxWidth().weight(1f)) {
            if (c.playlist.isEmpty()) {
                Text(
                    "Playlist is empty.\nTap ADD to pick files, a folder, an album or a radio station.",
                    color = s.listText.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = s.listFont,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                itemsIndexed(c.playlist, key = { i, t -> "$i|${t.uri}" }) { i, t ->
                    PlaylistRow(
                        index = i, track = t,
                        isCurrent = i == c.current, isSelected = i in c.selection,
                        onTap = { c.toggleSelect(i) }, onDoubleTap = { c.playIndex(i) },
                        onLongPress = { c.beginAddToPlaylist(i) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val classic = s.isClassic
            PlButton(if (classic) "ADD" else "+Add", { addMenu(c, a) }, Modifier.weight(1f))
            PlButton(if (classic) "REM" else "−Rem", { remMenu(c) }, Modifier.weight(1f))
            PlButton(if (classic) "SEL" else "Sel", { selMenu(c) }, Modifier.weight(1f))
            PlButton(if (classic) "MISC" else "Misc", { miscMenu(c) }, Modifier.weight(1f))
            LcdBox(Modifier.weight(2.1f).fillMaxHeight()) {
                Text(
                    "${formatTime(c.positionMs)}/${formatLong(c.totalDurationMs)}",
                    color = s.lcdText, fontSize = 9.sp, fontFamily = s.lcdFont, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.Center),
                )
            }
            PlButton(if (classic) "OPTS" else "Lists", { listOptsMenu(c) }, Modifier.weight(1.1f))
        }
    }
}

@Composable
private fun PlButton(label: String, items: () -> List<MenuItem>, modifier: Modifier) {
    Box(modifier.fillMaxHeight()) {
        MenuAnchor(items) { open -> SkinTextButton(label, open, Modifier.fillMaxSize(), fontSize = 10.sp) }
    }
}

@Composable
fun TrackRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LocalSkin.current
    val color = if (isCurrent) s.listCurrent else s.listText
    Row(
        modifier
            .fillMaxWidth()
            .background(if (isSelected) s.listSelBg else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}. ${track.display}",
            color = color, fontSize = 14.sp, fontFamily = s.listFont,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(track.durationLabel(), color = color, fontSize = 14.sp, fontFamily = s.listFont, maxLines = 1)
    }
}

@Composable
private fun PlaylistRow(
    index: Int,
    track: Track,
    isCurrent: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    TrackRow(
        index, track, isCurrent, isSelected,
        Modifier.pointerInput(index, track.uri) {
            detectTapGestures(
                onTap = { onTap() }, onDoubleTap = { onDoubleTap() },
                onLongPress = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onLongPress()
                },
            )
        },
    )
}
