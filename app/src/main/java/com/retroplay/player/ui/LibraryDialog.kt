package com.retroplay.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retroplay.player.player.Album
import com.retroplay.player.player.Folder
import com.retroplay.player.player.LibState
import com.retroplay.player.player.LibTab
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.Station
import com.retroplay.player.player.Track
import com.retroplay.player.player.formatLong
import java.util.Locale

@Composable
fun LibraryDialog(c: PlayerController) {
    val tab = c.libraryTab ?: return
    val s = LocalSkin.current
    Dialog(
        onDismissRequest = { c.libraryTab = null },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SkinLocals {
            Box(Modifier.fillMaxSize().background(s.desktop).padding(10.dp)) {
                SkinWindow("MEDIA LIBRARY", Modifier.fillMaxSize(), onClose = { c.libraryTab = null }, fill = true) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LibTab.entries.forEach { t ->
                            LedToggle(t.label, t == tab, { c.libraryTab = t }, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            LibTab.ALBUMS -> AlbumsTab(c)
                            LibTab.FOLDERS -> FoldersTab(c)
                            LibTab.PLAYLISTS -> PlaylistsTab(c)
                            LibTab.RADIO -> RadioTab(c)
                        }
                    }
                }
            }
        }
    }
}

// ================================================================== shared

/** Shows a "load library" prompt until the device library has been scanned. */
@Composable
private fun libraryReady(c: PlayerController): Boolean {
    val a = LocalActions.current
    LaunchedEffect(Unit) { if (c.libraryState == LibState.NOT_LOADED) a.requestLibrary() }
    when (c.libraryState) {
        LibState.READY -> {
            if (c.library.isNotEmpty()) return true
            CenterMessage("No music found on this device.", "SCAN AGAIN") { a.requestLibrary() }
        }
        LibState.LOADING -> CenterMessage("Scanning your music…")
        LibState.NOT_LOADED -> CenterMessage(
            "RetroPlay needs permission to read your music to show albums and folders.",
            "ALLOW & LOAD",
        ) { a.requestLibrary() }
    }
    return false
}

@Composable
private fun CenterMessage(text: String, button: String? = null, onClick: () -> Unit = {}) {
    val s = LocalSkin.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = s.labelText, fontSize = 14.sp, fontFamily = s.listFont, textAlign = TextAlign.Center)
        if (button != null) {
            Spacer(Modifier.height(12.dp))
            SkinTextButton(button, onClick, Modifier.height(30.dp), fontSize = 11.sp)
        }
    }
}

/** Album / folder detail: cover, PLAY ALL / ADD / SHUFFLE, and the track list (tap a track to play from it). */
@Composable
private fun CollectionDetail(
    c: PlayerController,
    title: String,
    subtitle: String,
    tracks: List<Track>,
    folderIcon: Boolean,
    onBack: () -> Unit,
) {
    val s = LocalSkin.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinTextButton("◂ BACK", onBack, Modifier.fillMaxHeight())
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(110.dp)) {
            if (folderIcon) LcdBox(Modifier.size(110.dp)) {
                GlyphIcon(Glyph.FOLDER, s.lcdText, Modifier.size(48.dp).align(Alignment.Center))
            } else ArtImage(tracks.firstOrNull(), Modifier.size(110.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    title, color = s.labelText, fontSize = 15.sp, fontFamily = s.listFont,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                FaceLabel(subtitle, size = 12.sp, bold = false)
                FaceLabel(
                    "${tracks.size} tracks • ${formatLong(tracks.sumOf { it.durationMs })}",
                    size = 11.sp, bold = false, active = false,
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinTextButton("PLAY ALL", {
                        c.replaceAndPlay(tracks, 0)
                        c.libraryTab = null
                    }, Modifier.fillMaxHeight())
                    SkinTextButton("SHUFFLE", {
                        c.replaceAndPlay(tracks.shuffled(), 0)
                        c.libraryTab = null
                    }, Modifier.fillMaxHeight())
                    SkinTextButton("+ ADD", { c.appendTracks(tracks) }, Modifier.fillMaxHeight())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ListBox(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                itemsIndexed(tracks) { i, t ->
                    TrackRow(
                        i, t, isCurrent = c.currentTrack?.uri == t.uri, isSelected = false,
                        modifier = Modifier.tap {
                            c.replaceAndPlay(tracks, i)
                            c.libraryTab = null
                        },
                    )
                }
            }
        }
    }
}

// ================================================================== albums

@Composable
private fun AlbumsTab(c: PlayerController) {
    if (!libraryReady(c)) return
    val s = LocalSkin.current
    val albums = remember(c.library) {
        c.library.groupBy { it.albumId to it.album }.map { (k, ts) ->
            val artists = ts.map { it.artist }.distinct()
            Album(
                id = k.first,
                title = k.second.ifBlank { "Unknown album" },
                artist = if (artists.size == 1) artists[0].ifBlank { "Unknown artist" } else "Various artists",
                tracks = ts,
            )
        }.sortedBy { it.title.lowercase() }
    }
    var open by remember { mutableStateOf<Album?>(null) }
    val sel = open
    if (sel != null) {
        CollectionDetail(c, sel.title, sel.artist, sel.tracks, folderIcon = false) { open = null }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { "${it.id}|${it.title}" }) { al ->
            Column(Modifier.tap { open = al }) {
                ArtImage(al.tracks.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(4.dp))
                Text(
                    al.title, color = s.labelText, fontSize = 12.sp, fontFamily = s.listFont,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                FaceLabel(al.artist, size = 10.sp, bold = false, active = false)
            }
        }
    }
}

// ================================================================= folders

@Composable
private fun FoldersTab(c: PlayerController) {
    val s = LocalSkin.current
    val a = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        SkinTextButton(
            "PLAY A FOLDER FROM SD CARD / USB / DOWNLOADS…", { a.pickFolder() },
            Modifier.fillMaxWidth().height(30.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) { FolderList(c, s) }
    }
}

@Composable
private fun BoxScope.FolderList(c: PlayerController, s: Skin) {
    if (!libraryReady(c)) return
    val folders = remember(c.library) {
        c.library.groupBy { it.folder }.map { (path, ts) -> Folder(path, ts) }.sortedBy { it.path.lowercase() }
    }
    var open by remember { mutableStateOf<Folder?>(null) }
    val sel = open
    if (sel != null) {
        CollectionDetail(c, sel.name, sel.path, sel.tracks, folderIcon = true) { open = null }
        return
    }
    ListBox(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
            items(folders, key = { it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().tap { open = f }.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.FOLDER, s.listText, Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            f.name, color = s.listCurrent, fontSize = 14.sp, fontFamily = s.listFont,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            f.path, color = s.listText, fontSize = 11.sp, fontFamily = s.listFont,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${f.tracks.size}", color = s.listText, fontSize = 13.sp, fontFamily = s.listFont)
                }
            }
        }
    }
}

// ========================================================= saved playlists

@Composable
private fun PlaylistsTab(c: PlayerController) {
    val s = LocalSkin.current
    var name by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            LcdTextField(name, { name = it }, "New playlist name", Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(6.dp))
            SkinTextButton("SAVE", { if (c.saveCurrentAs(name)) name = "" }, Modifier.fillMaxHeight(), fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        FaceLabel(
            "Saves what's in the playlist window now (${c.playlist.size} tracks). Add songs from Albums, Folders or Radio first.",
            size = 10.sp, bold = false,
        )
        Spacer(Modifier.height(8.dp))
        ListBox(Modifier.fillMaxWidth().weight(1f)) {
            if (c.savedPlaylists.isEmpty()) {
                Text(
                    "No saved playlists yet.",
                    color = s.listText.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = s.listFont,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                items(c.savedPlaylists.keys.sorted(), key = { it }) { pl ->
                    val tracks = c.savedPlaylists[pl].orEmpty()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                pl, color = s.listCurrent, fontSize = 14.sp, fontFamily = s.listFont,
                                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${tracks.size} tracks • ${formatLong(tracks.sumOf { it.durationMs })}",
                                color = s.listText, fontSize = 11.sp, fontFamily = s.listFont,
                            )
                        }
                        Row(Modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            SkinTextButton("PLAY", {
                                c.loadSaved(pl)
                                c.libraryTab = null
                            }, Modifier.fillMaxHeight())
                            SkinTextButton("ADD", { c.addSaved(pl) }, Modifier.fillMaxHeight())
                            SkinTextButton(if (confirmDelete == pl) "SURE?" else "DEL", {
                                if (confirmDelete == pl) {
                                    c.deleteSaved(pl)
                                    confirmDelete = null
                                } else confirmDelete = pl
                            }, Modifier.fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

// =================================================================== radio

private enum class RadioMode { TOP, LOCAL, SEARCH, FAVS, URL }

@Composable
private fun RadioTab(c: PlayerController) {
    val s = LocalSkin.current
    val country = remember { Locale.getDefault().country.uppercase().takeIf { it.length == 2 } }
    var mode by remember { mutableStateOf(if (country != null) RadioMode.LOCAL else RadioMode.TOP) }
    var query by remember { mutableStateOf("") }
    var urlName by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (c.radioResults.isEmpty() && !c.radioLoading) {
            c.searchRadio(null, if (mode == RadioMode.LOCAL) country else null)
        }
    }

    fun play(st: Station) {
        c.playStation(st)
        c.libraryTab = null
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            val search = {
                if (query.isNotBlank()) {
                    mode = RadioMode.SEARCH
                    c.searchRadio(query, null)
                }
            }
            LcdTextField(query, { query = it }, "Search stations", Modifier.weight(1f).fillMaxHeight(), onDone = search)
            Spacer(Modifier.width(6.dp))
            SkinTextButton("SEARCH", search, Modifier.fillMaxHeight(), fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (country != null) LedToggle(country, mode == RadioMode.LOCAL, {
                mode = RadioMode.LOCAL
                c.searchRadio(null, country)
            }, Modifier.weight(1f).fillMaxHeight())
            LedToggle("TOP", mode == RadioMode.TOP, {
                mode = RadioMode.TOP
                c.searchRadio(null, null)
            }, Modifier.weight(1f).fillMaxHeight())
            LedToggle("FAVS", mode == RadioMode.FAVS, { mode = RadioMode.FAVS }, Modifier.weight(1f).fillMaxHeight())
            LedToggle("URL", mode == RadioMode.URL, { mode = RadioMode.URL }, Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(8.dp))

        when (mode) {
            RadioMode.URL -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FaceLabel("Add any internet radio stream (MP3 / AAC / HLS link):", size = 11.sp, bold = false)
                LcdTextField(urlName, { urlName = it }, "Station name", Modifier.fillMaxWidth().height(32.dp))
                LcdTextField(url, { url = it }, "https://…", Modifier.fillMaxWidth().height(32.dp))
                Row(Modifier.height(28.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinTextButton("PLAY", {
                        c.addCustomStation(urlName, url)?.let { play(it) }
                    }, Modifier.fillMaxHeight())
                    SkinTextButton("SAVE TO FAVORITES", {
                        if (c.addCustomStation(urlName, url) != null) {
                            urlName = ""
                            url = ""
                            mode = RadioMode.FAVS
                        }
                    }, Modifier.fillMaxHeight())
                }
            }
            else -> ListBox(Modifier.fillMaxWidth().weight(1f)) {
                val list = if (mode == RadioMode.FAVS) c.favorites else c.radioResults
                when {
                    mode == RadioMode.FAVS && list.isEmpty() -> RadioNote("No favorites yet. Tap ★ next to a station.")
                    mode != RadioMode.FAVS && c.radioLoading -> RadioNote("Tuning in…")
                    mode != RadioMode.FAVS && c.radioError != null -> RadioNote(c.radioError ?: "")
                    else -> LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                        items(list, key = { it.url }) { st ->
                            StationRow(st, c.isFavorite(st), isCurrent = c.currentTrack?.uri?.toString() == st.url,
                                onPlay = { play(st) }, onStar = { c.toggleFavorite(st) })
                        }
                    }
                }
            }
        }
        if (mode != RadioMode.URL) {
            Spacer(Modifier.height(4.dp))
            FaceLabel("Station directory: radio-browser.info", size = 9.sp, bold = false, active = false)
        }
    }
}

@Composable
private fun BoxScope.RadioNote(text: String) {
    val s = LocalSkin.current
    Text(
        text, color = s.listText.copy(alpha = 0.75f), fontSize = 13.sp, fontFamily = s.listFont,
        textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(16.dp),
    )
}

@Composable
private fun StationRow(st: Station, fav: Boolean, isCurrent: Boolean, onPlay: () -> Unit, onStar: () -> Unit) {
    val s = LocalSkin.current
    Row(
        Modifier.fillMaxWidth().tap(onPlay).padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.RADIO, if (isCurrent) s.listCurrent else s.listText, Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                st.name, color = if (isCurrent) s.listCurrent else s.listText, fontSize = 14.sp,
                fontFamily = s.listFont, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (st.subtitle.isNotBlank()) Text(
                st.subtitle, color = s.listText.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = s.listFont,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(36.dp).tap(onStar), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.STAR, if (fav) s.eqCurve else s.listText.copy(alpha = 0.3f), Modifier.size(18.dp))
        }
    }
}
