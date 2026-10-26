package com.cosmosoft.tvtheme.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.TimeUnit

/**
 * Free 14-day trial from install, then a lifetime purchase or a monthly subscription through Google Play.
 *
 * The product ids below must exist in the Play Console (a one-time product and a subscription with
 * one base plan) before purchases work. Until then the store shows nothing to buy and only the trial applies.
 * Purchases are checked on the device only; verify them on a server before relying on them for revenue.
 */
class Premium(context: Context) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences("premium", Context.MODE_PRIVATE)
    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val trialEndsAtMs: Long = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime +
        TimeUnit.DAYS.toMillis(TRIAL_DAYS)

    /** Last known purchase state, cached so the app stays unlocked while Play is unreachable. */
    var owned by mutableStateOf(prefs.getBoolean(KEY_OWNED, false))
        private set
    var lifetime by mutableStateOf<ProductDetails?>(null)
        private set
    var monthly by mutableStateOf<ProductDetails?>(null)
        private set

    private val ownedByType = mutableMapOf<String, Boolean>()

    val trialDaysLeft: Int
        get() {
            val left = trialEndsAtMs - System.currentTimeMillis()
            return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
        }

    val isUnlocked get() = owned || trialDaysLeft > 0

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) refresh()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun buy(activity: Activity, product: ProductDetails) {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
        product.subscriptionOfferDetails?.firstOrNull()?.let { params.setOfferToken(it.offerToken) }
        client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build(),
        )
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingResponseCode.OK && purchases != null && handle(purchases)) saveOwned(true)
    }

    private fun refresh() {
        for ((id, type) in listOf(LIFETIME_ID to ProductType.INAPP, MONTHLY_ID to ProductType.SUBS)) {
            val query = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()))
                .build()
            client.queryProductDetailsAsync(query) { _, details ->
                if (type == ProductType.INAPP) lifetime = details.firstOrNull() else monthly = details.firstOrNull()
            }
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { response, purchases ->
                if (response.responseCode != BillingResponseCode.OK) return@queryPurchasesAsync
                ownedByType[type] = handle(purchases)
                // Only trust a "not owned" answer once both product types have reported.
                if (ownedByType.size == 2) saveOwned(ownedByType.values.any { it })
            }
        }
    }

    /** Acknowledges new purchases (Play refunds unacknowledged ones after 3 days) and says whether any is ours. */
    private fun handle(purchases: List<Purchase>): Boolean {
        val ours = purchases.filter {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && (LIFETIME_ID in it.products || MONTHLY_ID in it.products)
        }
        ours.filterNot { it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
        }
        return ours.isNotEmpty()
    }

    private fun saveOwned(value: Boolean) {
        owned = value
        prefs.edit().putBoolean(KEY_OWNED, value).apply()
    }

    companion object {
        const val LIFETIME_ID = "tvtheme_lifetime"
        const val MONTHLY_ID = "tvtheme_monthly"
        private const val TRIAL_DAYS = 14L
        private val DAY_MS = TimeUnit.DAYS.toMillis(1)
        private const val KEY_OWNED = "owned"

        fun price(product: ProductDetails): String? =
            product.oneTimePurchaseOfferDetails?.formattedPrice
                ?: product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
    }
}
