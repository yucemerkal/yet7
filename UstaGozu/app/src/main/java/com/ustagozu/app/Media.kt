package com.ustagozu.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object Media {
    private val ALLOWED = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "3gp", "webm")

    fun dir(c: Context): File = File(c.filesDir, "media").apply { mkdirs() }

    fun isVideo(n: String): Boolean = n.substringAfterLast('.').lowercase() in setOf("mp4", "3gp", "webm")

    fun copyIn(c: Context, u: Uri): String? {
        val type = c.contentResolver.getType(u) ?: ""
        var ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)?.lowercase() ?: ""
        if (ext !in ALLOWED) ext = if (type.startsWith("video/")) "mp4" else "jpg"
        val name = UUID.randomUUID().toString() + "." + ext
        return try {
            val ins = c.contentResolver.openInputStream(u) ?: return null
            ins.use { i -> File(dir(c), name).outputStream().use { o -> i.copyTo(o) } }
            name
        } catch (e: Exception) {
            null
        }
    }

    fun delete(c: Context, name: String) {
        File(dir(c), name).delete()
    }

    fun deleteAll(c: Context, r: ServiceRecord) {
        r.mediaList().forEach { delete(c, it) }
    }

    fun thumb(c: Context, name: String): Bitmap? {
        val f = File(dir(c), name)
        if (!f.exists()) return null
        return try {
            if (isVideo(name)) {
                val r = MediaMetadataRetriever()
                var b: Bitmap? = null
                try {
                    r.setDataSource(f.path)
                    b = r.getFrameAtTime(0)
                } finally {
                    r.release()
                }
                b
            } else {
                val o = BitmapFactory.Options()
                o.inJustDecodeBounds = true
                BitmapFactory.decodeFile(f.path, o)
                o.inSampleSize = maxOf(1, o.outWidth / 256)
                o.inJustDecodeBounds = false
                BitmapFactory.decodeFile(f.path, o)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun open(c: Context, name: String) {
        try {
            val uri = FileProvider.getUriForFile(c, c.packageName + ".files", File(dir(c), name))
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, if (isVideo(name)) "video/*" else "image/*")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            c.startActivity(i)
        } catch (e: Exception) {
        }
    }
}
