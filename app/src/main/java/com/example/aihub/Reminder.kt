package com.example.aihub

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import com.example.aihub.core.Store
import org.json.JSONArray
import java.util.Calendar

/** یادآور روزانه: هر روز در ساعت انتخابی یک اعلان با کار بعدیِ برنامه‌ی تو نشان می‌دهد */
object Reminder {
    private const val CHANNEL = "daily"
    private const val ACTION = "com.example.aihub.REMINDER"

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 77, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(ctx))
    }

    /** اولین کار انجام‌نشده‌ی برنامه‌ها (اگر برنامه‌ای باشد) */
    private fun nextTask(): String? = try {
        val arr = JSONArray(Store.getStr("plans").ifBlank { "[]" })
        var found: String? = null
        loop@ for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val its = o.getJSONArray("items")
            for (j in 0 until its.length()) {
                val x = its.getJSONObject(j)
                if (!x.optBoolean("d")) { found = o.optString("title") + " — " + x.optString("t"); break@loop }
            }
        }
        found
    } catch (_: Exception) {
        null
    }

    fun notifyNow(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val task = nextTask()
        val text = task ?: "یک ایده‌ی تازه منتظر توست؛ بیا با هوش مصنوعی کار کنیم."
        val open = PendingIntent.getActivity(
            ctx, 78, Intent(ctx, MainActivity::class.java).putExtra("screen", if (task != null) "PLANNER" else "HOME")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (task != null) "کار امروزت ✅" else "هوش مصنوعی هوشیار")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1001, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Store.reminderOn) Reminder.schedule(ctx, Store.reminderHour)
        } else if (Store.reminderOn) {
            Reminder.notifyNow(ctx)
        }
    }
}
