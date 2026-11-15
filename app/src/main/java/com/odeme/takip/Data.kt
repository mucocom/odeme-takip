package com.odeme.takip

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
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
