package com.retroplay.player.player

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

object MediaLibrary {

    /** All music in the device's MediaStore. Needs READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE. */
    @Suppress("DEPRECATION")
    fun scanDevice(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val folderCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH
        else MediaStore.MediaColumns.DATA
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            folderCol,
        )
        val sort = "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE ASC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        ctx.contentResolver.query(base, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, sort)?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val tI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val aI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val dI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val alI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val aiI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val fI = c.getColumnIndexOrThrow(folderCol)
            while (c.moveToNext()) {
                val rawFolder = c.getString(fI) ?: ""
                val folder = if (Build.VERSION.SDK_INT >= 29) rawFolder else rawFolder.substringBeforeLast('/') + "/"
                out += Track(
                    uri = ContentUris.withAppendedId(base, c.getLong(idI)),
                    title = c.getString(tI) ?: "Unknown",
                    artist = c.getString(aI) ?: "",
                    durationMs = c.getLong(dI),
                    album = c.getString(alI) ?: "",
                    albumId = c.getLong(aiI),
                    folder = folder,
                )
            }
        }
        return out
    }

    /** A file the user picked with the system file picker. */
    fun fromDocument(ctx: Context, uri: Uri, takePermission: Boolean = true): Track {
        if (takePermission) try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var dur = 0L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        val finalTitle = title?.takeIf { it.isNotBlank() }
            ?: displayName(ctx, uri)?.substringBeforeLast('.')
            ?: "Unknown"
        return Track(uri, finalTitle, artist ?: "", dur, album = album ?: "")
    }

    /** Every audio file inside a folder the user picked (works for SD cards / USB drives too). */
    fun scanTree(ctx: Context, tree: Uri, limit: Int = 3000): List<Track> {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val out = ArrayList<Track>()
        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(DocumentsContract.getTreeDocumentId(tree) to "")
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        while (queue.isNotEmpty() && out.size < limit) {
            val (docId, path) = queue.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            try {
                ctx.contentResolver.query(children, proj, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: ""
                        val mime = c.getString(2) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(id to "$path$name/")
                        } else if (mime.startsWith("audio/")) {
                            val u = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            out += fromDocument(ctx, u, takePermission = false).copy(folder = path.ifEmpty { "/" })
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out.sortedWith(compareBy({ it.folder.lowercase() }, { it.title.lowercase() }))
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /** Bitrate / sample rate / channel count for the kbps / kHz / stereo indicators. */
    fun streamInfo(ctx: Context, uri: Uri): StreamInfo? = try {
        var kbps = 0
        var khz = 0
        var stereo = true
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            kbps = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000
        } catch (_: Exception) {
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) khz = f.getInteger(MediaFormat.KEY_SAMPLE_RATE) / 1000
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) stereo = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) > 1
                    if (kbps == 0 && f.containsKey(MediaFormat.KEY_BIT_RATE)) kbps = f.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                    break
                }
            }
        } catch (_: Exception) {
        } finally {
            ex.release()
        }
        StreamInfo(kbps, khz, stereo)
    } catch (_: Exception) {
        null
    }
}
