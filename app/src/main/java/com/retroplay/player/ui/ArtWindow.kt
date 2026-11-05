package com.retroplay.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroplay.player.player.LibTab
import com.retroplay.player.player.PlayerController

/** Now-playing cover. Tap the cover (or BROWSE) to pick another album by its art. */
@Composable
fun ArtWindow(c: PlayerController) {
    val s = LocalSkin.current
    val t = c.currentTrack
    SkinWindow(title = "ALBUM ART", onClose = { c.showArt = false }) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(128.dp)) {
            ArtImage(t, Modifier.size(128.dp).tap { c.libraryTab = LibTab.ALBUMS })
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    t?.title ?: "Nothing loaded",
                    color = s.labelText, fontSize = 15.sp, fontFamily = s.listFont, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                FaceLabel(
                    if (t?.isStream == true) "Live radio" else t?.artist?.ifBlank { "Unknown artist" } ?: "",
                    size = 12.sp, bold = false,
                )
                FaceLabel(
                    if (t?.isStream == true) t.folder else t?.album ?: "",
                    size = 12.sp, bold = false, active = false,
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinTextButton("BROWSE ALBUMS", { c.libraryTab = LibTab.ALBUMS }, Modifier.height(26.dp))
                    SkinTextButton("RADIO", { c.libraryTab = LibTab.RADIO }, Modifier.height(26.dp))
                }
            }
        }
    }
}
