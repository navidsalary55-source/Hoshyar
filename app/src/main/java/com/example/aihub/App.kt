package com.example.aihub

import android.app.Application
import android.content.Intent
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/** اگر برنامه خطا بدهد، به‌جای بسته‌شدن بی‌صدا، متن خطا را در یک صفحه‌ی جدا نشان می‌دهد تا بشود کپی و ارسال کرد. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":crash")) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val sw = StringWriter()
                error.printStackTrace(PrintWriter(sw))
                val i = Intent(this, CrashActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("trace", "Thread: ${thread.name}\n" + sw.toString().take(15000))
                startActivity(i)
                Process.killProcess(Process.myPid())
                exitProcess(10)
            } catch (t: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
