package com.vibemeet.app.billing
import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
/** Real Play Billing wrapper.
 *  A purchase is NEVER treated as granted here. When Play reports PURCHASED,
 *  this class forwards (purchaseToken, productId, isSubscription) to
 *  [onPurchaseToken], and the caller must send it to the backend
 *  (/coins/purchase or /subscription/verify) and only grant premium/coins
 *  after the backend confirms Google's verification. Only then should the
 *  app acknowledge/consume the purchase — acknowledging before server
 *  verification would let a spoofed client claim a grant.
 *  Products (create these exact IDs in Play Console):
 *  subscriptions: premium_monthly, premium_yearly
 *  one-time: coins_100, coins_500, coins_1000 */
class BillingManager(context: Context, private val onPurchaseToken: (token: String, productId: String, isSubscription: Boolean) -> Unit) : PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(context).setListener(this).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()
    fun connect() { client.startConnection(object : BillingClientStateListener { override fun onBillingSetupFinished(r: BillingResult) {} override fun onBillingServiceDisconnected() {} }) }
    fun launchSubscription(activity: Activity, productId: String) = launch(activity, productId, BillingClient.ProductType.SUBS)
    fun launchCoins(activity: Activity, productId: String) = launch(activity, productId, BillingClient.ProductType.INAPP)
    private fun launch(activity: Activity, productId: String, type: String) {
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(productId).setProductType(type).build())).build()) { _, details ->
            details.firstOrNull()?.let {
                client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(it).build())).build())
            }
        }
    }
    fun acknowledgeAndConsume(purchase: Purchase, onDone: () -> Unit) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged.not()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { onDone() }
        } else onDone()
        // One-time coin products are consumable in Play terms; consumption is
        // handled by the backend verification flow, not by granting locally.
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) return
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            val productId = p.products.firstOrNull() ?: return@forEach
            val isSub = productId.startsWith("premium_")
            onPurchaseToken(p.purchaseToken, productId, isSub)
        }
    }
}
