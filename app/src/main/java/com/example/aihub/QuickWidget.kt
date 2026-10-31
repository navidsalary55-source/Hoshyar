package com.example.aihub

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/** ویجت صفحه‌ی اصلی گوشی: شش دکمه‌ی میان‌بر به بخش‌های پرکاربرد */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(ctx.packageName, R.layout.widget_quick)
        listOf(
            R.id.w_chat to "CHAT", R.id.w_voice to "VOICE_CHAT", R.id.w_vision to "VISION",
            R.id.w_notes to "NOTES", R.id.w_draw to "DRAW", R.id.w_plan to "PLANNER",
        ).forEachIndexed { i, (viewId, screen) ->
            val intent = Intent(ctx, MainActivity::class.java)
                .setData(Uri.parse("aihub://open/$screen"))
                .putExtra("screen", screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getActivity(ctx, 200 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        ids.forEach { mgr.updateAppWidget(it, views) }
    }
}
