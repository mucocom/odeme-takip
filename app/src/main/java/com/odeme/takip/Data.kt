package com.odeme.takip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

data class Tx(
    val id: String, val income: Boolean, val amount: Double,
    val cat: String, val date: String, val note: String, val payId: String = ""
)

data class Pay(
    val id: String, val name: String, val amount: Double, val day: Int,
    val paid: Set<String> = emptySet()
)

val CG = listOf("Market", "Fatura", "Kira", "Ulaşım", "Sağlık", "Eğlence", "Eğitim", "Diğer")
val CI = listOf("Maaş", "Ek gelir", "Diğer")

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("odeme", Context.MODE_PRIVATE)

    /** Bu ayki ödemeyi ödendi işaretler ve gider olarak kaydeder. */
    fun markPaid(c: Context, payId: String) {
        val ym = YearMonth.now().toString()
        val pays = loadPay(c)
        val p = pays.find { it.id == payId } ?: return
        if (ym in p.paid) return
        savePay(c, pays.map { if (it.id == payId) it.copy(paid = it.paid + ym) else it })
        saveTx(c, loadTx(c) + Tx(newId(), false, p.amount, "Fatura", LocalDate.now().toString(), p.name, p.id))
    }

    fun exportAll(c: Context): String = JSONObject()
        .put("app", "odeme-takip")
        .put("tx", JSONArray(prefs(c).getString("tx", "[]")))
        .put("pay", JSONArray(prefs(c).getString("pay", "[]")))
        .toString()

    fun importAll(c: Context, text: String): Boolean = try {
        val o = JSONObject(text.trim())
        val tx = o.getJSONArray("tx")
        val pay = o.getJSONArray("pay")
        for (i in 0 until tx.length()) {
            val x = tx.getJSONObject(i)
            x.getString("id"); x.getBoolean("income"); x.getDouble("amount")
            x.getString("cat"); x.getString("date"); x.getString("note")
        }
        for (i in 0 until pay.length()) {
            val x = pay.getJSONObject(i)
            x.getString("id"); x.getString("name"); x.getDouble("amount"); x.getInt("day")
        }
        prefs(c).edit().putString("tx", tx.toString()).putString("pay", pay.toString()).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun loadTx(c: Context): List<Tx> {
        val a = JSONArray(prefs(c).getString("tx", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Tx(
                o.getString("id"), o.getBoolean("income"), o.getDouble("amount"),
                o.getString("cat"), o.getString("date"), o.getString("note"),
                o.optString("payId", "")
            )
        }
    }

    fun saveTx(c: Context, l: List<Tx>) {
        val a = JSONArray()
        l.forEach {
            a.put(
                JSONObject().put("id", it.id).put("income", it.income)
                    .put("amount", it.amount).put("cat", it.cat).put("date", it.date)
                    .put("note", it.note).put("payId", it.payId)
            )
        }
        prefs(c).edit().putString("tx", a.toString()).apply()
    }

    fun loadPay(c: Context): List<Pay> {
        val a = JSONArray(prefs(c).getString("pay", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val pj = o.optJSONArray("paid") ?: JSONArray()
            val paid = (0 until pj.length()).map { i -> pj.getString(i) }.toSet()
            Pay(o.getString("id"), o.getString("name"), o.getDouble("amount"), o.getInt("day"), paid)
        }
    }

    fun savePay(c: Context, l: List<Pay>) {
        val a = JSONArray()
        l.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("amount", it.amount).put("day", it.day)
                    .put("paid", JSONArray(it.paid.toList()))
            )
        }
        prefs(c).edit().putString("pay", a.toString()).apply()
    }
}

fun dueDate(p: Pay, ym: YearMonth): LocalDate = ym.atDay(minOf(p.day, ym.lengthOfMonth()))

fun daysLeft(p: Pay): Int =
    ChronoUnit.DAYS.between(LocalDate.now(), dueDate(p, YearMonth.now())).toInt()

fun daysText(d: Int): String = when {
    d < 0 -> "${-d} gün gecikti"
    d == 0 -> "Bugün son gün"
    d == 1 -> "Yarın son gün"
    else -> "$d gün kaldı"
}

fun fmt(d: Double): String = String.format(Locale("tr", "TR"), "%,.2f ₺", d)

fun newId(): String = UUID.randomUUID().toString()

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun hour(c: Context) = p(c).getInt("hour", 9)
    fun setHour(c: Context, h: Int) = p(c).edit().putInt("hour", h).apply()
    fun lead(c: Context) = p(c).getInt("lead", 3)
    fun setLead(c: Context, d: Int) = p(c).edit().putInt("lead", d).apply()
    fun limit(c: Context): Double = p(c).getString("limit", "0")?.toDoubleOrNull() ?: 0.0
    fun setLimit(c: Context, v: Double) = p(c).edit().putString("limit", v.toString()).apply()
}

fun emoji(c: String): String = when (c) {
    "Market" -> "🛒"; "Fatura" -> "🧾"; "Kira" -> "🏠"; "Ulaşım" -> "🚌"
    "Sağlık" -> "💊"; "Eğlence" -> "🎬"; "Eğitim" -> "🎓"
    "Maaş" -> "💼"; "Ek gelir" -> "✨"
    else -> "📦"
}

fun trDate(s: String): String = try {
    LocalDate.parse(s).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("tr")))
} catch (e: Exception) {
    s
}

fun numText(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
