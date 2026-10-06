package com.ustagozu.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** .ustagozu = ZIP paketi: manifest.json + record.json */
object Share {
    private const val MAX = 1_000_000

    fun build(c: Context, r: ServiceRecord, withCustomer: Boolean): Intent {
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "kayit_${r.id.take(8)}.ustagozu")
        val man = JSONObject().put("format", "ustagozu").put("version", 1).put("created", System.currentTimeMillis())
        val rec = JSONObject().put("id", r.id)
            .put("customer", if (withCustomer) r.customer else "")
            .put("device", r.device).put("fault", r.fault).put("solution", r.solution)
            .put("status", r.status).put("date", r.date)
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json")); z.write(man.toString().toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("record.json")); z.write(rec.toString().toByteArray()); z.closeEntry()
        }
        val uri = FileProvider.getUriForFile(c, c.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ustagozu"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Kaydı gönder")
    }

    fun read(c: Context, u: Uri): ServiceRecord? {
        try {
            val ins = c.contentResolver.openInputStream(u) ?: return null
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "record.json") {
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = z.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX) return null
                        }
                        val j = JSONObject(out.toString("UTF-8"))
                        return ServiceRecord(
                            id = j.getString("id"),
                            customer = j.optString("customer"),
                            device = j.optString("device"),
                            fault = j.optString("fault"),
                            solution = j.optString("solution"),
                            status = j.optInt("status").coerceIn(0, 2),
                            date = j.optLong("date", System.currentTimeMillis()),
                            shared = true
                        )
                    }
                    e = z.nextEntry
                }
            }
        } catch (_: Exception) {
        }
        return null
    }
}
