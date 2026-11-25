package com.muyassir.app.core

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing, directly on the device (no server), as the plan says.
 *
 * Play Console products to create (see docs/PLUS.md):
 *  - subscription `plus` with base plans `monthly` (7.99 SAR) and `yearly` (49.99 SAR),
 *    offer `trial7` on `yearly` (7 days free, new customers), optional offer `founders` (39.99 SAR first year).
 *  - one-time consumable product `plus_day` (2.99 SAR) = 24 hours of Plus.
 */
object Billing {
    const val SUB = "plus"
    const val DAY = "plus_day"
    private const val TAG = "MuyassirBilling"
    const val TRIAL_REMINDER_ID = 7_700_001

    data class Plan(
        val id: String, val title: String, val price: String, val period: String, val note: String,
        val trialDays: Int, val micros: Long, internal val details: ProductDetails?, internal val token: String?,
    )

    val plans = mutableStateListOf<Plan>()
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    private var client: BillingClient? = null
    private lateinit var app: Context

    fun start(ctx: Context) {
        if (client != null) return
        app = ctx.applicationContext
        val c = BillingClient.newBuilder(app)
            .setListener { r, list -> onPurchases(r, list) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) {
                if (r.responseCode == BillingClient.BillingResponseCode.OK) { loadProducts(); refresh() }
                else Log.i(TAG, "billing unavailable: ${r.responseCode} ${r.debugMessage}")
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun product(id: String, type: String) = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()

    private fun loadProducts() {
        val c = client ?: return
        val found = mutableListOf<Plan>()
        c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product(SUB, ProductType.SUBS))).build()) { r, res ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) res.productDetailsList.firstOrNull()?.let { found += subPlans(it) }
            c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product(DAY, ProductType.INAPP))).build()) { r2, res2 ->
                if (r2.responseCode == BillingClient.BillingResponseCode.OK) res2.productDetailsList.firstOrNull()?.let { d ->
                    d.oneTimePurchaseOfferDetails?.let { o -> found += Plan("day", "مرور يوم", o.formattedPrice, "24 ساعة", "كل ميزات بلس ليوم واحد، بلا اشتراك", 0, o.priceAmountMicros, d, null) }
                }
                plans.clear(); plans.addAll(found.sortedBy { listOf("yearly", "monthly", "day").indexOf(it.id) })
                val wasLive = Plus.live
                Plus.live = plans.any { it.id == "yearly" } && plans.any { it.id == "monthly" }
                if (Plus.live && !wasLive && Plus.notifyWhenLive) {
                    Plus.clearNotify()
                    ReminderReceiver.schedule(app, 7_700_002, System.currentTimeMillis() + 2000, "ميسر بلس متاح الآن", "افتح العمليات بلا حدود والقوالب الاحترافية وOCR للملفات الكاملة.")
                }
            }
        }
    }

    /** One plan per base plan; for yearly, the eligible offer with the lowest first payment (trial or founders). */
    private fun subPlans(d: ProductDetails): List<Plan> {
        val offers = d.subscriptionOfferDetails ?: return emptyList()
        return listOf("yearly", "monthly").mapNotNull { base ->
            val mine = offers.filter { it.basePlanId == base }
            if (mine.isEmpty()) return@mapNotNull null
            val pick = mine.minByOrNull { o -> o.pricingPhases.pricingPhaseList.first().priceAmountMicros } ?: mine.first()
            val phases = pick.pricingPhases.pricingPhaseList
            val recurring = phases.last()
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }?.let { days(it.billingPeriod) } ?: 0
            val intro = phases.firstOrNull { it.priceAmountMicros > 0 && it != recurring }
            if (base == "yearly") {
                val perMonth = recurring.priceAmountMicros / 12 / 10_000 / 100.0
                val note = buildString {
                    append("يعادل ${"%.2f".format(java.util.Locale.US, perMonth)} ${currency(recurring.priceCurrencyCode)} شهريا")
                    if (intro != null) append(" • أول سنة ${intro.formattedPrice}")
                }
                Plan("yearly", "سنوي", recurring.formattedPrice, "سنة", note, trial, recurring.priceAmountMicros, d, pick.offerToken)
            } else Plan("monthly", "شهري", recurring.formattedPrice, "شهر", "يلغى في أي وقت", trial, recurring.priceAmountMicros, d, pick.offerToken)
        }
    }

    private fun currency(code: String) = if (code == "SAR") "ر.س" else code
    private fun days(iso: String): Int = Regex("P(\\d+)([DWM])").find(iso)?.let { m ->
        val n = m.groupValues[1].toInt(); when (m.groupValues[2]) { "W" -> n * 7; "M" -> n * 30; else -> n }
    } ?: 0

    fun buy(activity: Activity, plan: Plan) {
        val c = client; val d = plan.details
        if (c == null || d == null) { message = "وضع التجربة: لا يتم الدفع في هذه النسخة"; return }
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        plan.token?.let { p.setOfferToken(it) }
        val r = c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p.build())).build())
        if (r.responseCode != BillingClient.BillingResponseCode.OK) message = "تعذر فتح صفحة الدفع في Google Play. حاول مرة أخرى."
        else if (plan.trialDays > 0) pendingTrial = plan.trialDays
    }
    private var pendingTrial = 0

    /** Restore purchases: asks Google Play again for what this account owns. */
    fun restore(done: (Boolean) -> Unit = {}) {
        if (client == null) { done(false); return }
        busy = true
        refresh { busy = false; message = if (Plus.active) "تمت استعادة اشتراكك" else "لم نجد اشتراكا على حساب Google هذا"; done(Plus.active) }
    }

    fun refresh(done: () -> Unit = {}) {
        val c = client ?: run { done(); return }
        c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                val own = list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && SUB in it.products }
                Plus.subscribed = own.isNotEmpty()
                Plus.renews = own.any { it.isAutoRenewing }
                Plus.planName = if (own.isNotEmpty()) "اشتراك بلس" else ""
                own.forEach { acknowledge(it) }
            }
            c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()) { _, l2 ->
                l2.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && DAY in it.products }.forEach { consumeDay(it) }
                done()
            }
        }
    }

    private fun onPurchases(r: BillingResult, list: List<Purchase>?) {
        when (r.responseCode) {
            BillingClient.BillingResponseCode.OK -> list.orEmpty().forEach { p ->
                when {
                    p.purchaseState == Purchase.PurchaseState.PENDING -> message = "عملية الدفع قيد المعالجة. ستفعل بلس فور اكتمالها."
                    p.purchaseState != Purchase.PurchaseState.PURCHASED -> {}
                    DAY in p.products -> { consumeDay(p); message = "تم تفعيل مرور اليوم لمدة 24 ساعة" }
                    else -> {
                        acknowledge(p); Plus.subscribed = true; Plus.planName = "اشتراك بلس"; Plus.paywall = null
                        message = "مرحبا بك في ميسر بلس"
                        if (pendingTrial > 0) {
                            // Remind the user one day before the free trial turns into a paid subscription.
                            ReminderReceiver.schedule(app, TRIAL_REMINDER_ID, p.purchaseTime + (pendingTrial - 1) * 86_400_000L,
                                "تجربة ميسر بلس تنتهي غدا", "إن لم ترغب في الاستمرار ألغ الاشتراك من Google Play قبل نهاية التجربة.")
                            pendingTrial = 0
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh { message = "اشتراكك مفعل بالفعل" }
            else -> message = "لم تكتمل عملية الشراء (${r.responseCode}). لم يخصم أي مبلغ."
        }
    }

    private fun acknowledge(p: Purchase) {
        if (p.isAcknowledged) return
        client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "acknowledge failed ${r.responseCode}")
        }
    }

    private fun consumeDay(p: Purchase) {
        Plus.grantDayPass(p.purchaseTime)
        client?.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r, _ ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) Log.w(TAG, "consume failed ${r.responseCode}")
        }
    }

    /** Debug builds only: sample plans so the paywall can be tested and screenshotted without the store. */
    fun samplePlans() {
        plans.clear()
        plans += Plan("yearly", "سنوي", "SAR 49.99", "سنة", "يعادل 4.17 ر.س شهريا", 7, 49_990_000, null, null)
        plans += Plan("monthly", "شهري", "SAR 7.99", "شهر", "يلغى في أي وقت", 0, 7_990_000, null, null)
        plans += Plan("day", "مرور يوم", "SAR 2.99", "24 ساعة", "كل ميزات بلس ليوم واحد، بلا اشتراك", 0, 2_990_000, null, null)
    }

    const val MANAGE_URL = "https://play.google.com/store/account/subscriptions?sku=plus&package=com.muyassir.app"
}
