package com.example.aihub.core

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** یک مورد تاریخچه. tool نام Screen است (مثلاً CHAT)، file نام فایل کنار آن (تصویر، صدا، html) */
data class HistoryItem(
    val id: Long,
    val tool: String,
    val title: String,
    val body: String,
    val time: Long,
    val file: String? = null,
    val ext: String? = null,
)

/** تاریخچه‌ی همه‌ی کارهای برنامه؛ روی حافظه‌ی خود برنامه ذخیره می‌شود و بعد از بستن برنامه هم می‌ماند. */
object History {
    private const val MAX_ITEMS = 300
    private const val MAX_BODY = 30000
    val items = mutableStateListOf<HistoryItem>()
    private lateinit var dir: File
    private lateinit var index: File
    private val io = Executors.newSingleThreadExecutor()
    private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        dir = File(ctx.applicationContext.filesDir, "history").also { it.mkdirs() }
        index = File(dir, "index.json")
        try {
            if (index.exists()) {
                val arr = JSONArray(index.readText())
                val list = ArrayList<HistoryItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        HistoryItem(
                            o.getLong("id"), o.getString("tool"), o.optString("title"), o.optString("body"),
                            o.optLong("time"), if (o.isNull("file")) null else o.optString("file"),
                            if (o.isNull("ext")) null else o.optString("ext"),
                        )
                    )
                }
                items.addAll(list.sortedByDescending { it.time })
            }
        } catch (_: Exception) {
        }
        ready = true
    }

    private fun persist() {
        if (!ready) return
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("tool", it.tool).put("title", it.title).put("body", it.body)
                    .put("time", it.time).put("file", it.file ?: JSONObject.NULL).put("ext", it.ext ?: JSONObject.NULL)
            )
        }
        val text = arr.toString()
        io.execute { try { index.writeText(text) } catch (_: Exception) {} }
    }

    private fun writeFile(name: String, bytes: ByteArray) {
        io.execute { try { File(dir, name).writeBytes(bytes) } catch (_: Exception) {} }
    }

    /** افزودن مورد جدید؛ شناسه‌ی آن را برمی‌گرداند تا بعداً بشود به‌روزش کرد */
    fun add(tool: String, title: String, body: String, bytes: ByteArray? = null, ext: String? = null): Long {
        if (!ready) return -1
        val id = System.currentTimeMillis() * 1000 + (System.nanoTime() % 1000)
        var fname: String? = null
        if (bytes != null && ext != null) {
            fname = "h_$id.$ext"
            writeFile(fname, bytes)
        }
        items.add(0, HistoryItem(id, tool, title.trim().take(80).ifBlank { "بدون عنوان" }, body.take(MAX_BODY), System.currentTimeMillis(), fname, ext))
        while (items.size > MAX_ITEMS) removeInternal(items.last().id)
        persist()
        return id
    }

    /** به‌روزرسانی متن (و در صورت نیاز فایل) یک مورد موجود؛ اگر پیدا نشد مورد تازه می‌سازد */
    fun update(id: Long, tool: String, title: String, body: String, bytes: ByteArray? = null, ext: String? = null): Long {
        val i = items.indexOfFirst { it.id == id }
        if (i < 0) return add(tool, title, body, bytes, ext)
        val old = items[i]
        var fname = old.file
        if (bytes != null && ext != null) {
            fname = old.file ?: "h_${old.id}.$ext"
            writeFile(fname, bytes)
        }
        items[i] = old.copy(title = title.trim().take(80).ifBlank { old.title }, body = body.take(MAX_BODY), file = fname, ext = ext ?: old.ext)
        persist()
        return id
    }

    private fun removeInternal(id: Long) {
        val it = items.firstOrNull { x -> x.id == id } ?: return
        items.remove(it)
        it.file?.let { f -> io.execute { try { File(dir, f).delete() } catch (_: Exception) {} } }
    }

    fun remove(id: Long) { removeInternal(id); persist() }

    /** پاک کردن همه (یا فقط یک بخش) */
    fun clear(tool: String? = null) {
        items.filter { tool == null || it.tool == tool }.forEach { removeInternal(it.id) }
        persist()
    }

    fun readFile(item: HistoryItem): ByteArray? {
        val f = item.file ?: return null
        return try { File(dir, f).takeIf { it.exists() }?.readBytes() } catch (_: Exception) { null }
    }
}
