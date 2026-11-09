# RetroPlay

An Android music player with a classic late-90s desktop-player layout, written in Kotlin and Jetpack Compose. It has two switchable skins:

- **Classic Pixel**: a dark slate face, a black/green LCD, bevelled square buttons and a red-yellow-green spectrum analyser.
- **Chrome 2K**: brushed silver, a blue LCD, round transport buttons and a menu bar.

Both skins are original artwork drawn in code. They borrow the style of the era but contain no copied skin graphics or logos.

## Features
- **Main window**: time display (tap it to switch between elapsed and remaining; it blinks while paused), a scrolling title, kbps / kHz / mono-stereo readouts, a spectrum or oscilloscope visualizer (tap it to change mode), volume, balance, seek bar, Prev / Play / Pause / Stop / Next / Eject, Shuffle and Repeat.
- **Equalizer**: 10 bands (60 Hz–16 kHz) plus a preamp, an on/off switch, a curve preview and 11 presets. Your phone may have fewer hardware EQ bands than 10; the 10-band curve is interpolated onto however many it has.
- **Playlist**: tap a row to select it and double-tap to play it. The buttons work as follows:
  - ADD picks files or scans the device library.
  - REM removes, crops or clears.
  - SEL selects all, none or inverts the selection.
  - MISC sorts, randomizes or reverses.
  - OPTS switches skins.
  
  It also shows the elapsed time over the playlist's total time.
- **Active indicators**: the transport button for the current state (Play / Pause / Stop) lights up green.
- **Album art window**: shows the current track's cover. Tap the cover to browse other albums by their art.
- **Media Library (ML button)**:
  - Albums: a grid of covers. Open an album to play it, shuffle it or add it to the playlist.
  - Folders: every music folder on the phone. "Play a folder…" also opens SD cards, USB drives and Downloads.
  - Lists: save the current playlist under a name, then load, add or delete saved playlists.
  - Radio: popular stations for your country and the world, plus search, favorites (★) and custom stream URLs. Stations come from the free radio-browser.info directory. They appear in the playlist marked LIVE.
- **Phone-volume sync**: VOL is the phone's media volume. The hardware keys move the slider, and the slider moves the phone volume.
- **Lock screen and background playback**: a media session plus a media-style notification put Prev / Play-Pause / Next / Stop, the cover art and a seek bar on the lock screen. They also make Bluetooth and headset buttons work.
  - With Options → "Show player on lock screen", the RetroPlay window itself can appear over the lock screen.
  - Playback pauses when headphones are unplugged.
- **Home-screen widget**: a resizable widget, 4×2 cells by default. The display is Small or Medium; tap the widget's ≡ or use Options to switch. Small hides the EQ / PL / ART / ML row so the widget fits in 2 rows. The widget is laid out like the main window. It follows the selected skin.
  - It has the LCD with a live clock and spectrum, the track title (long titles slide through in pieces), and kbps / kHz / stereo readouts.
  - All transport buttons work, and the active one lights green. SHUF and repeat also work.
  - Tap anywhere on the VOL, BAL or seek bars to jump to that point.
  - EQ turns the equalizer on or off. ART swaps the spectrum for the album cover. PL and ML open the app. Eject opens the file picker.
  - The widget redraws twice a second only while music plays, the screen is on and the app itself isn't open.
- **Layout**: the playlist window stretches to fill the remaining screen height, so its buttons stay visible.
- **Saved settings**: the playlist, skin, EQ, volume and the other settings are kept between launches.

## Build
1. Open the `RetroPlay` folder in Android Studio (Ladybug 2024.2 or newer).
2. Let Gradle sync. It uses AGP 8.7, Kotlin 2.0.21, Gradle 8.9 and a Compose BOM from 2024.10.
3. Run on a device with Android 8.0 (API 26) or newer.
4. To get an APK, use Build → Build App Bundle(s) / APK(s) → Build APK(s).

## Permissions
- **Music / audio files**: needed only for "Scan device music". Files picked with ADD don't need it.
- **Record audio**: Android requires it before an app can use its audio Visualizer. Nothing is recorded. If you deny it, the visualizer stays off.
- **Notifications**: used for the playback controls.

## Code map
- `player/AudioEngine.kt` wraps MediaPlayer together with the Equalizer and Visualizer effects, all on one fixed audio session.
- `player/PlayerController.kt` holds all player state and Winamp-style transport logic, plus the playlist and persistence code.
- `PlaybackService.kt` is the foreground service and its notification.
- `ui/Skin.kt` has both skins' colours and fonts. Add a third skin here.
- `ui/Widgets.kt` holds the bevels, buttons, sliders, LCD, marquee, visualizer and menus.
- `ui/MainWindows.kt` lays out the main window for each skin.
- `ui/EqWindow.kt` and `ui/PlaylistWindow.kt` are the equalizer and playlist windows.
