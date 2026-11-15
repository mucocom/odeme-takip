package com.odeme.takip

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Bu telefona özel yerel hesap. Şifre düz metin olarak saklanmaz. */
object Auth {
    private fun p(c: Context) = c.getSharedPreferences("auth", Context.MODE_PRIVATE)

    private fun hash(pass: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pass.toCharArray(), salt, 60000, 256)
        val h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(h, Base64.NO_WRAP)
    }

    fun hasAccount(c: Context) = p(c).contains("hash")
    fun email(c: Context): String = p(c).getString("email", "") ?: ""
    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun isLoggedIn(c: Context) = hasAccount(c) && p(c).getBoolean("in", false)
    fun setLoggedIn(c: Context, v: Boolean) = p(c).edit().putBoolean("in", v).apply()

    fun register(c: Context, name: String, email: String, pass: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString("name", name)
            .putString("email", email.trim().lowercase())
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pass, salt))
            .putBoolean("in", true)
            .apply()
    }

    fun login(c: Context, email: String, pass: String): Boolean {
        val pr = p(c)
        val saltS = pr.getString("salt", null) ?: return false
        val salt = Base64.decode(saltS, Base64.NO_WRAP)
        val ok = pr.getString("email", "") == email.trim().lowercase() &&
            pr.getString("hash", "") == hash(pass, salt)
        if (ok) setLoggedIn(c, true)
        return ok
    }

    /** Hesabı ve bu telefondaki tüm kayıtları siler. */
    fun reset(c: Context) {
        p(c).edit().clear().apply()
        c.getSharedPreferences("odeme", Context.MODE_PRIVATE).edit().clear().apply()
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
