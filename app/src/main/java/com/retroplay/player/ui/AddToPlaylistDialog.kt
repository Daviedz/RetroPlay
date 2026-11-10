package com.retroplay.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.retroplay.player.player.PlayerController

/** Shown after a long-press on a playlist row: create a new playlist or add to one you already saved. */
@Composable
fun AddToPlaylistDialog(c: PlayerController) {
    val tracks = c.addToListTracks ?: return
    var name by remember(tracks) { mutableStateOf("") }
    val close = { c.addToListTracks = null }
    val done = { c.selectNone(); close() }
    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("Add ${tracks.size} song${if (tracks.size == 1) "" else "s"} to a playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text("New playlist name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (c.savedPlaylists.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Or add to an existing playlist:")
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        c.savedPlaylists.keys.sorted().forEach { n ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { if (c.addTracksToSaved(n, tracks)) done() }
                                    .padding(vertical = 10.dp),
                            ) { Text("$n  (${c.savedPlaylists[n]?.size ?: 0})") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (c.addTracksToSaved(name, tracks)) done() }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { close() }) { Text("Cancel") } },
    )
}
