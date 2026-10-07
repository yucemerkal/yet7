package com.ustagozu.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** .ustagozu = ZIP paketi: manifest.json + record.json + media/ */
object Share {
    private const val MAX = 1_000_000
    private const val MAX_MEDIA = 500L * 1024 * 1024
    private val NAME = Regex("^[A-Za-z0-9-]+\\.(jpg|jpeg|png|webp|gif|mp4|3gp|webm)$")

    fun build(c: Context, r: ServiceRecord, withCustomer: Boolean, withAddress: Boolean, withPrice: Boolean, withMedia: Boolean): Intent {
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "kayit_${r.id.take(8)}.ustagozu")
        val files = if (withMedia) r.mediaList().filter { File(Media.dir(c), it).exists() } else emptyList()
        val man = JSONObject().put("format", "ustagozu").put("version", 2).put("created", System.currentTimeMillis())
        val rec = JSONObject().put("id", r.id)
            .put("customer", if (withCustomer) r.customer else "")
            .put("address", if (withAddress) r.address else "")
            .put("price", if (withPrice) r.price else "")
            .put("device", r.device).put("fault", r.fault).put("solution", r.solution)
            .put("status", r.status).put("date", r.date)
            .put("media", files.joinToString("|"))
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(man.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("record.json")); z.write(rec.toString().toByteArray()); z.closeEntry()
            files.forEach { n ->
                z.putNextEntry(ZipEntry("media/$n"))
                File(Media.dir(c), n).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ustagozu"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Kaydı gönder")
    }

    /** Kullanıcı gelen kaydı kabul etmezse, açılan medya dosyalarını sil. */
    fun discard(c: Context, r: ServiceRecord) {
        Media.deleteAll(c, r)
    }

    fun read(c: Context, u: Uri): ServiceRecord? {
        val saved = mutableListOf<String>()
        val holder = arrayOfNulls<JSONObject>(1)
        var total = 0L
        try {
            val ins = c.contentResolver.openInputStream(u) ?: return null
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name
                    if (n == "record.json") {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val k = z.read(buf)
                            if (k < 0) break
                            out.write(buf, 0, k)
                            if (out.size() > MAX) throw IOException("büyük")
                        }
                        holder[0] = JSONObject(out.toString("UTF-8"))
                    } else if (holder[0] != null && n.startsWith("media/")) {
                        val fn = n.removePrefix("media/")
                        if (NAME.matches(fn)) {
                            val newName = UUID.randomUUID().toString() + "." + fn.substringAfterLast('.')
                            saved.add(newName)
                            File(Media.dir(c), newName).outputStream().use { o ->
                                val buf = ByteArray(65536)
                                while (true) {
                                    val k = z.read(buf)
                                    if (k < 0) break
                                    total += k
                                    if (total > MAX_MEDIA) throw IOException("büyük")
                                    o.write(buf, 0, k)
                                }
                            }
                        }
                    }
                    e = z.nextEntry
                }
            }
            val j = holder[0] ?: return null
            return ServiceRecord(
                id = j.getString("id"),
                customer = j.optString("customer"),
                address = j.optString("address"),
                device = j.optString("device"),
                fault = j.optString("fault"),
                solution = j.optString("solution"),
                price = j.optString("price"),
                status = j.optInt("status").coerceIn(0, 2),
                date = j.optLong("date", System.currentTimeMillis()),
                shared = true,
                media = saved.joinToString("|")
            )
        } catch (e: Exception) {
            saved.forEach { Media.delete(c, it) }
            return null
        }
    }
}
