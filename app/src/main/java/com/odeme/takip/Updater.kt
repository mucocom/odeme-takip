package com.odeme.takip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val code: Int, val name: String, val notes: String, val url: String)

object Updater {
    private const val REPO = "mucocom/odeme-takip"

    @Suppress("DEPRECATION")
    fun currentCode(c: Context): Int {
        val i = c.packageManager.getPackageInfo(c.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) i.longVersionCode.toInt() else i.versionCode
    }

    fun currentName(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""

    /** GitHub'daki son sürümü sorar; daha yeni bir sürüm varsa bilgisini döndürür. */
    fun check(c: Context): UpdateInfo? = try {
        val con = URL("https://api.github.com/repos/$REPO/releases/latest")
            .openConnection() as HttpURLConnection
        con.setRequestProperty("User-Agent", "OdemeTakip")
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.connectTimeout = 8000
        con.readTimeout = 8000
        if (con.responseCode != 200) {
            null
        } else {
            val o = JSONObject(con.inputStream.bufferedReader().readText())
            val code = o.getString("tag_name").trim().removePrefix("v").toInt()
            val assets = o.getJSONArray("assets")
            var url = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            if (code > currentCode(c) && url.isNotEmpty())
                UpdateInfo(code, o.optString("name"), o.optString("body"), url)
            else null
        }
    } catch (e: Exception) {
        null
    }

    fun download(c: Context, url: String, progress: (Int) -> Unit): File? = try {
        val f = File(c.cacheDir, "update.apk")
        val con = URL(url).openConnection() as HttpURLConnection
        con.setRequestProperty("User-Agent", "OdemeTakip")
        con.connectTimeout = 10000
        con.readTimeout = 15000
        val total = con.contentLengthLong
        con.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(16384)
                var read = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) progress((read * 100 / total).toInt())
                }
            }
        }
        f
    } catch (e: Exception) {
        null
    }

    fun canInstall(c: Context): Boolean = c.packageManager.canRequestPackageInstalls()

    /** "Bu uygulamadan yüklemeye izin ver" ayar sayfasını açar. */
    fun askPermission(c: Context) {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(c: Context, f: File) {
        Handler(Looper.getMainLooper()).post {
            val uri = FileProvider.getUriForFile(c, c.packageName + ".fileprovider", f)
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
