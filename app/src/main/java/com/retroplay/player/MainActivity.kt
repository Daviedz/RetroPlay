package com.retroplay.player

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.retroplay.player.player.LibTab
import com.retroplay.player.player.PlayerController
import com.retroplay.player.ui.PlayerScreen
import com.retroplay.player.ui.UiActions

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the home-screen widget: "files", "library" or "playlist". */
        const val EXTRA_OPEN = "com.retroplay.player.OPEN"
    }

    private val controller: PlayerController get() = (application as RetroPlayApp).controller

    /** What to do once the music-library permission is granted. */
    private var afterReadPermission: (() -> Unit)? = null

    private val openDocs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) controller.addDocuments(uris)
    }

    private val openFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) controller.playFolderTree(uri)
    }

    private val readPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val next = afterReadPermission
        afterReadPermission = null
        if (ok) next?.invoke()
        else Toast.makeText(this, "Permission is needed to read your music library", Toast.LENGTH_SHORT).show()
    }

    private val visPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        controller.onVisPermission(ok)
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Hardware volume keys always control music volume while the app is open
        volumeControlStream = AudioManager.STREAM_MUSIC
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val actions = UiActions(
            openFiles = { openDocs.launch(arrayOf("audio/*")) },
            scanLibrary = { withReadPermission { controller.scanLibrary() } },
            requestVis = { visPermission.launch(Manifest.permission.RECORD_AUDIO) },
            showHelp = {},
            requestLibrary = { withReadPermission { controller.loadLibrary() } },
            pickFolder = { openFolder.launch(null) },
        )
        setContent {
            LaunchedEffect(controller.showOnLock) { showOverLockScreen(controller.showOnLock) }
            PlayerScreen(controller, actions)
        }
        handleOpen(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            "files" -> openDocs.launch(arrayOf("audio/*"))
            "library" -> controller.libraryTab = LibTab.ALBUMS
            "playlist" -> controller.showPl = true
        }
        intent?.removeExtra(EXTRA_OPEN)
    }

    /** Lets the player appear on the lock screen (and open from the lock-screen notification). */
    private fun showOverLockScreen(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
        } else {
            @Suppress("DEPRECATION")
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }

    private fun withReadPermission(action: () -> Unit) {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (granted(p)) action() else {
            afterReadPermission = action
            readPermission.launch(p)
        }
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onStart() {
        super.onStart()
        com.retroplay.player.widget.WidgetUpdater.appVisible = true
    }

    override fun onStop() {
        super.onStop()
        com.retroplay.player.widget.WidgetUpdater.appVisible = false
        controller.save()
        // Back on the home screen: make sure the widget shows the latest state right away
        try { com.retroplay.player.widget.WidgetUpdater.pushAll(this, controller) } catch (_: Exception) {}
    }
}
