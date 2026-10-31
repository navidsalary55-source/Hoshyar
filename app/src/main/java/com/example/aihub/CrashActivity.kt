package com.example.aihub

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** صفحه‌ی ساده (بدون Compose) برای نمایش متن خطا */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = intent.getStringExtra("trace") ?: "no trace"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#10121C"))
            setPadding(36, 110, 36, 36)
        }
        val title = TextView(this).apply {
            text = "برنامه با خطا مواجه شد.\nدکمه‌ی «کپی خطا» را بزن و متن را برای پشتیبانی بفرست."
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.START
        }
        val tv = TextView(this).apply {
            text = trace
            setTextColor(Color.parseColor("#FFB4B4"))
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(0, 24, 0, 24)
        }
        val scroll = ScrollView(this).apply { addView(tv) }

        val copy = Button(this).apply {
            text = "کپی خطا"
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", trace))
                Toast.makeText(this@CrashActivity, "کپی شد", Toast.LENGTH_SHORT).show()
            }
        }
        val share = Button(this).apply {
            text = "اشتراک"
            setOnClickListener {
                val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, trace) }
                startActivity(Intent.createChooser(i, null))
            }
        }
        val close = Button(this).apply { text = "بستن"; setOnClickListener { finishAndRemoveTask() } }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(copy, lp); addView(share, lp); addView(close, lp)
        }
        root.addView(title)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(row)
        setContentView(root)
    }
}
