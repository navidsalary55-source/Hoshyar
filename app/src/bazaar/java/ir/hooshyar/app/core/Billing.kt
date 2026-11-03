package ir.hooshyar.app.core

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
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
 * پرداخت درون‌برنامه‌ای کافه‌بازار (Poolakey).
 * فقط وقتی فعال است که برنامه از خود بازار نصب شده باشد؛ نسخه‌ی مستقیم (APK) با کد اشتراک و تلگرام کار می‌کند.
 * شناسه‌ی محصولات در پنل بازار: plan_1m تا plan_12m (نوع: مصرفی).
 */
object Billing {
    private const val BAZAAR = "com.farsitel.bazaar"

    /** آیا این نسخه از کافه‌بازار نصب شده است؟ */
    fun fromBazaar(ctx: Context): Boolean = try {
        if (android.os.Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == BAZAAR
        else @Suppress("DEPRECATION") ctx.packageManager.getInstallerPackageName(ctx.packageName) == BAZAAR
    } catch (e: Exception) { false }

    fun bazaarInstalled(ctx: Context): Boolean = try { ctx.packageManager.getPackageInfo(BAZAAR, 0); true } catch (e: PackageManager.NameNotFoundException) { false }

    fun productId(months: Int) = "plan_${months}m"

    fun activityOf(ctx: Context): ComponentActivity? {
        var c = ctx
        while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
        return null
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?): Boolean = false

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var payment: Payment? = null
    private var connection: Connection? = null

    private fun connect(ctx: Context, onReady: (Payment) -> Unit, onFail: (String) -> Unit) {
        val p = payment ?: Payment(ctx.applicationContext, PaymentConfiguration(localSecurityCheck = SecurityCheck.Disable)).also { payment = it }
        connection?.disconnect()
        connection = p.connect {
            connectionSucceed { onReady(p) }
            connectionFailed { onFail("اتصال به بازار برقرار نشد. مطمئن شو کافه‌بازار نصب و واردشده است.") }
            disconnected { }
        }
    }

    /** تأیید خرید روی سرور و اعطای اشتراک */
    private suspend fun verify(productId: String, token: String): Long = withContext(Dispatchers.IO) {
        val body = JSONObject().put("productId", productId).put("purchaseToken", token).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(Provider.SERVER.baseUrl + "/pay/bazaar").header("Authorization", "Bearer ${Store.deviceId}").post(body).build()
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

    /** خرید یک پلن. نتیجه: پیام موفقیت یا خطا */
    fun buy(activity: ComponentActivity, months: Int, onResult: (ok: Boolean, msg: String) -> Unit) {
        if (Store.accountEmail.isBlank()) { onResult(false, "اول وارد حساب شو."); return }
        connect(activity, onReady = { pay ->
            pay.purchaseProduct(
                registry = activity.activityResultRegistry,
                request = PurchaseRequest(productId = productId(months), payload = Store.deviceId),
            ) {
                purchaseFlowBegan { }
                failedToBeginFlow { onResult(false, "شروع پرداخت ناموفق بود.") }
                purchaseCanceled { onResult(false, "پرداخت لغو شد.") }
                purchaseFailed { onResult(false, "پرداخت ناموفق بود.") }
                purchaseSucceed { info ->
                    io.launch {
                        try {
                            val until = verify(info.productId, info.purchaseToken)
                            Store.savePaid(until)
                            pay.consumeProduct(info.purchaseToken) { consumeSucceed { }; consumeFailed { } }
                            onResult(true, "اشتراک فعال شد ✅")
                        } catch (e: Exception) { onResult(false, e.message ?: "خطا") }
                    }
                }
            }
        }, onFail = { onResult(false, it) })
    }

    /** خریدهای مصرف‌نشده را (اگر پرداخت شده ولی ثبت نشده) دوباره روی سرور ثبت می‌کند */
    fun restore(ctx: Context, onResult: (count: Int, msg: String) -> Unit) {
        if (Store.accountEmail.isBlank()) { onResult(0, "اول وارد حساب شو."); return }
        connect(ctx, onReady = { pay ->
            pay.getPurchasedProducts {
                queryFailed { onResult(0, "خواندن خریدها ناموفق بود.") }
                querySucceed { list ->
                    val mine = list.filter { it.productId.startsWith("plan_") }
                    if (mine.isEmpty()) { onResult(0, "خرید ثبت‌نشده‌ای پیدا نشد."); return@querySucceed }
                    io.launch {
                        var n = 0
                        for (pi in mine) {
                            try {
                                Store.savePaid(verify(pi.productId, pi.purchaseToken))
                                pay.consumeProduct(pi.purchaseToken) { consumeSucceed { }; consumeFailed { } }
                                n++
                            } catch (e: Exception) { }
                        }
                        onResult(n, if (n > 0) "$n خرید بازیابی شد ✅" else "بازیابی انجام نشد.")
                    }
                }
            }
        }, onFail = { onResult(0, it) })
    }
}
