package com.odeme.takip

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

object Reminder {
    private const val CH = "odeme"

    /** Ayarlardaki saate (varsayılan 09:00) bir sonraki günlük alarmı kurar. */
    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 1, Intent(c, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        var t = LocalDateTime.now().withHour(Prefs.hour(c)).withMinute(0).withSecond(0).withNano(0)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        val ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    /** Vadesi yaklaşan veya geçen ödemeler için bildirim gösterir (bildirimde "Ödendi" düğmesi var). */
    fun check(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH, "Ödeme hatırlatma", NotificationManager.IMPORTANCE_HIGH)
        )
        val ym = YearMonth.now().toString()
        val lead = Prefs.lead(c)
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        Store.loadPay(c).filter { ym !in it.paid }.forEach { p ->
            val d = daysLeft(p)
            if (d <= lead) {
                val payPi = PendingIntent.getBroadcast(
                    c, p.id.hashCode(),
                    Intent(c, PayReceiver::class.java).putExtra("payId", p.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                @Suppress("DEPRECATION")
                val n = Notification.Builder(c, CH)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("💸 " + p.name + " · " + fmt(p.amount))
                    .setContentText(daysText(d))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .addAction(android.R.drawable.ic_menu_save, "Ödendi", payPi)
                    .build()
                nm.notify(p.id.hashCode(), n)
            }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminder.check(c)
        Reminder.schedule(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminder.schedule(c)
    }
}

/** Bildirimdeki "Ödendi" düğmesi: uygulamayı açmadan ödemeyi işaretler. */
class PayReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("payId") ?: return
        Store.markPaid(c, id)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
    }
}
