package ir.hooshyar.app.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import ir.hooshyar.app.BuildConfig
import ir.myket.billingclient.IabHelper
import ir.myket.billingclient.util.IabResult
import ir.myket.billingclient.util.Purchase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * پرداخت درون‌برنامه‌ای مایکت.
 * نام تابع‌ها عمداً مثل نسخه‌ی بازار است تا بقیه‌ی برنامه فرقی نکند.
 * محصولات در پنل مایکت: plan_1m تا plan_12m (مصرفی).
 */
object Billing {
    private const val MYKET = "ir.mservices.market"

    /** آیا این نسخه از مایکت نصب شده است؟ (نام قدیمی fromBazaar برای سازگاری با صفحه‌ی قیمت‌ها) */
    fun fromBazaar(ctx: Context): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == MYKET
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName) == MYKET
    } catch (e: Exception) { false }

    fun productId(months: Int) = "plan_${months}m"

    fun activityOf(ctx: Context): ComponentActivity? {
        var c = ctx
        while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
        return null
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var helper: IabHelper? = null

    /** برای نسخه‌هایی از کتابخانه که نتیجه‌ی صفحه‌ی پرداخت را از onActivityResult می‌گیرند (اگر نداشته باشد بی‌اثر است) */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean {
        val h = helper ?: return false
        return try {
            h.javaClass.getMethod("handleActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, android.content.Intent::class.java)
                .invoke(h, requestCode, resultCode, data) as? Boolean ?: false
        } catch (e: Exception) { false }
    }

    private fun setup(ctx: Context, onReady: (IabHelper) -> Unit, onFail: (String) -> Unit) {
        if (BuildConfig.MYKET_RSA_KEY.isBlank()) { onFail("کلید مایکت در این نسخه تنظیم نشده است."); return }
        helper?.let { onReady(it); return }
        val h = IabHelper(ctx.applicationContext, BuildConfig.MYKET_RSA_KEY)
        h.startSetup { r: IabResult ->
            if (r.isSuccess) { helper = h; onReady(h) }
            else onFail("اتصال به مایکت برقرار نشد. مطمئن شو مایکت نصب و واردشده است.")
        }
    }

    /** تأیید خرید روی سرور و اعطای اشتراک */
    private suspend fun verify(productId: String, token: String): Long = withContext(Dispatchers.IO) {
        val body = JSONObject().put("productId", productId).put("purchaseToken", token).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(Provider.SERVER.baseUrl + "/pay/myket").header("Authorization", "Bearer ${Store.deviceId}").post(body).build()
        try {
            Net.http.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val m = try { JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty() } catch (e: Exception) { "" }
                    throw AiException(m.ifBlank { "خطای سرور (${r.code})" })
                }
                JSONObject(raw).optLong("until")
            }
        } catch (e: java.io.IOException) { throw AiException("اتصال به سرور برقرار نشد. خرید شما ثبت شده؛ بعداً «بازیابی خرید» را بزن.") }
    }

    private fun consume(h: IabHelper, p: Purchase) {
        try { h.consumeAsync(p) { _, _ -> } } catch (e: Exception) { }
    }

    /** خرید یک پلن */
    fun buy(activity: ComponentActivity, months: Int, onResult: (ok: Boolean, msg: String) -> Unit) {
        if (Store.accountEmail.isBlank()) { onResult(false, "اول وارد حساب شو."); return }
        setup(activity, onReady = { h ->
            try {
                h.launchPurchaseFlow(activity as Activity, productId(months), { r: IabResult, p: Purchase? ->
                    if (r.isFailure || p == null) { onResult(false, "پرداخت انجام نشد یا لغو شد."); return@launchPurchaseFlow }
                    io.launch {
                        try {
                            val until = verify(p.sku, p.token)
                            Store.savePaid(until)
                            consume(h, p)
                            onResult(true, "اشتراک فعال شد ✅")
                        } catch (e: Exception) { onResult(false, e.message ?: "خطا") }
                    }
                }, Store.deviceId)
            } catch (e: Exception) { onResult(false, "شروع پرداخت ناموفق بود.") }
        }, onFail = { onResult(false, it) })
    }

    /** خریدهای مصرف‌نشده را دوباره روی سرور ثبت می‌کند */
    fun restore(ctx: Context, onResult: (count: Int, msg: String) -> Unit) {
        if (Store.accountEmail.isBlank()) { onResult(0, "اول وارد حساب شو."); return }
        setup(ctx, onReady = { h ->
            try {
                h.queryInventoryAsync { r, inv ->
                    if (r.isFailure || inv == null) { onResult(0, "خواندن خریدها ناموفق بود."); return@queryInventoryAsync }
                    val mine = inv.allPurchases.filter { it.sku.startsWith("plan_") }
                    if (mine.isEmpty()) { onResult(0, "خرید ثبت‌نشده‌ای پیدا نشد."); return@queryInventoryAsync }
                    io.launch {
                        var n = 0
                        for (p in mine) {
                            try { Store.savePaid(verify(p.sku, p.token)); consume(h, p); n++ } catch (e: Exception) { }
                        }
                        onResult(n, if (n > 0) "$n خرید بازیابی شد ✅" else "بازیابی انجام نشد.")
                    }
                }
            } catch (e: Exception) { onResult(0, "خواندن خریدها ناموفق بود.") }
        }, onFail = { onResult(0, it) })
    }
}
