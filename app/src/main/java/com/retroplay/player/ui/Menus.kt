package com.retroplay.player.ui

import com.retroplay.player.player.LibTab
import com.retroplay.player.player.PlayerController
import com.retroplay.player.player.SkinId

fun onPlayPressed(c: PlayerController, a: UiActions) {
    if (c.playlist.isEmpty()) a.openFiles() else c.play()
}

fun playMenu(c: PlayerController, a: UiActions) = listOf(
    MenuItem("Open files…") { a.openFiles() },
    MenuItem("Play a folder…") { a.pickFolder() },
    MenuItem("Scan device music") { a.scanLibrary() },
    MenuItem("Play") { onPlayPressed(c, a) },
    MenuItem("Pause") { c.pause() },
    MenuItem("Stop") { c.stop() },
    MenuItem("Previous") { c.prev() },
    MenuItem("Next") { c.next() },
)

fun libraryMenu(c: PlayerController, a: UiActions) = listOf(
    MenuItem("Albums (cover art)") { c.libraryTab = LibTab.ALBUMS },
    MenuItem("Folders") { c.libraryTab = LibTab.FOLDERS },
    MenuItem("Saved playlists") { c.libraryTab = LibTab.PLAYLISTS },
    MenuItem("Internet radio") { c.libraryTab = LibTab.RADIO },
    MenuItem("Play a folder from SD / USB…") { a.pickFolder() },
)

fun skinMenu(c: PlayerController) = SkinId.entries.map { id ->
    MenuItem("Skin: ${id.label}", checked = c.skin == id) { c.setSkinId(id) }
}

fun optionsMenu(c: PlayerController) = skinMenu(c) + listOf(
    MenuItem("Shuffle", checked = c.shuffleOn) { c.toggleShuffle() },
    MenuItem("Repeat", checked = c.repeatOn) { c.toggleRepeat() },
    MenuItem("Show time remaining", checked = c.showRemaining) { c.showRemaining = !c.showRemaining },
    MenuItem("Show player on lock screen", checked = c.showOnLock) { c.showOnLock = !c.showOnLock },
) + listOf("Small", "Medium").mapIndexed { i, name ->
    MenuItem("Widget display: $name", checked = c.widgetLcd == i) {
        c.widgetLcd = i
        c.save()
    }
}

fun viewMenu(c: PlayerController, a: UiActions) = listOf(
    MenuItem("Equalizer", checked = c.showEq) { c.showEq = !c.showEq },
    MenuItem("Playlist", checked = c.showPl) { c.showPl = !c.showPl },
    MenuItem("Album art", checked = c.showArt) { c.showArt = !c.showArt },
    MenuItem("Visualizer: ${if (c.hasVisPermission) c.visMode.label else "needs permission"}") {
        if (!c.hasVisPermission) a.requestVis() else c.cycleVisMode()
    },
)

fun helpMenu(a: UiActions) = listOf(MenuItem("How to use…") { a.showHelp() })

/** The classic skin crams everything into the little title-bar menu. */
fun classicMainMenu(c: PlayerController, a: UiActions) =
    playMenu(c, a).take(3) + libraryMenu(c, a).take(4) + optionsMenu(c) + viewMenu(c, a) + helpMenu(a)

fun addMenu(c: PlayerController, a: UiActions) = listOf(
    MenuItem("Add files…") { a.openFiles() },
    MenuItem("Play a folder…") { a.pickFolder() },
    MenuItem("Scan device music") { a.scanLibrary() },
    MenuItem("Browse albums…") { c.libraryTab = LibTab.ALBUMS },
    MenuItem("Internet radio…") { c.libraryTab = LibTab.RADIO },
)

fun remMenu(c: PlayerController) = listOf(
    MenuItem("Remove selected") { c.removeSelected() },
    MenuItem("Crop (keep selected)") { c.cropSelected() },
    MenuItem("Clear playlist") { c.clearPlaylist() },
)

fun selMenu(c: PlayerController) = listOf(
    MenuItem("Select all") { c.selectAll() },
    MenuItem("Select none") { c.selectNone() },
    MenuItem("Invert selection") { c.invertSelection() },
)

fun miscMenu(c: PlayerController) = listOf(
    MenuItem("Sort by title") { c.sortByTitle() },
    MenuItem("Sort by artist") { c.sortByArtist() },
    MenuItem("Randomize list") { c.randomize() },
    MenuItem("Reverse list") { c.reverse() },
)

fun listOptsMenu(c: PlayerController) = listOf(
    MenuItem("Save / load playlists…") { c.libraryTab = LibTab.PLAYLISTS },
) + skinMenu(c) + listOf(
    MenuItem("Show time remaining", checked = c.showRemaining) { c.showRemaining = !c.showRemaining },
)

const val HELP_TEXT = """• Tap a playlist row to select it, double-tap to play it.
• The lit (green) transport button shows what the player is doing: Play, Pause or Stop.
• ML opens the Media Library: browse Albums by cover art, Folders, your saved playlists (LISTS) and Internet RADIO.
• To make your own playlist: build it in the playlist window, then OPTS → Save / load playlists and give it a name.
• Play a folder: Library → Folders, or "Play a folder…" for SD cards and USB drives.
• Radio stations are added to the playlist marked LIVE. Star a station to keep it in Favorites, or add any stream URL.
• Tap the clock to switch elapsed / remaining time. Tap the visualizer to change its mode (Android asks for the audio permission for this; nothing is recorded).
• VOL is your phone's media volume: the volume keys move the slider and the slider moves the phone volume.
• Lock screen: playback controls with cover art appear there automatically. With "Show player on lock screen" (Options) on, if RetroPlay is open when you lock the phone it reappears when you wake it, and tapping the lock-screen controls opens it without unlocking.
• Home-screen widget: tap its ≡ to switch between Small and Medium (also in Options). Resize the widget by long-pressing it.
• Switch skins from the ≡ menu (Classic) or Options (Chrome 2K)."""
