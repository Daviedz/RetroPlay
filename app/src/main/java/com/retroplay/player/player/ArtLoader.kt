package com.retroplay.player.player

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Collections

/** Loads and caches album covers: MediaStore album art first, then art embedded in the file. */
object ArtLoader {
    private val cache = LruCache<String, ImageBitmap>(150)
    private val missing: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun key(t: Track): String = if (t.albumId >= 0) "a${t.albumId}" else "u${t.uri}"

    fun cached(t: Track): ImageBitmap? = cache.get(key(t))

    /** Call off the main thread. */
    fun load(ctx: Context, t: Track, px: Int = 360): ImageBitmap? {
        if (t.isStream) return null
        val k = key(t)
        cache.get(k)?.let { return it }
        if (k in missing) return null
        val bmp = (if (t.albumId >= 0) albumThumb(ctx, t.albumId, px) else null) ?: embedded(ctx, t.uri, px)
        val img = bmp?.asImageBitmap()
        if (img != null) cache.put(k, img) else missing += k
        return img
    }

    @Suppress("DEPRECATION")
    private fun albumThumb(ctx: Context, albumId: Long, px: Int): Bitmap? = try {
        val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(albumUri, android.util.Size(px, px), null)
        } else {
            val path = ctx.contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Albums.ALBUM_ART),
                "${MediaStore.Audio.Albums._ID}=?", arrayOf(albumId.toString()), null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
            path?.let { p ->
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(p, o)
                BitmapFactory.decodeFile(p, BitmapFactory.Options().apply { inSampleSize = sample(o, px) })
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun embedded(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val bytes = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample(o, px) })
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun sample(o: BitmapFactory.Options, px: Int): Int {
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        return s
    }
}
