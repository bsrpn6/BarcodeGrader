package me.brandonray.barcodegrader.util

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class BillingClientManager(private val context: Context) {

    private var billingClient: BillingClient? = null

    fun startConnection(onSetupFinished: () -> Unit) {
        billingClient = BillingClient.newBuilder(context)
            .setListener { billingResult, purchases ->
                // Handle purchase updates here
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            .enablePendingPurchases()
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onSetupFinished()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Handle disconnection, possibly retry connection
            }
        })
    }

    // Method to query product details before initiating a purchase
    fun queryProductDetails(productId: String, onProductDetailsFound: (ProductDetails?) -> Unit) {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS) // or BillingClient.ProductType.INAPP
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onProductDetailsFound(productDetailsList.firstOrNull())
            } else {
                onProductDetailsFound(null)
            }
        }
    }

    // Method to query active purchases
    fun queryPurchases(onResult: (Boolean) -> Unit) {
        billingClient?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { billingResult, purchases ->
            val hasActiveSubscription = purchases.any { purchase ->
                purchase.products.contains("your_subscription_id")
            }
            onResult(hasActiveSubscription)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        // Handle purchase
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Provide the subscription benefits to the user
        }
    }

    // Method to launch the purchase flow
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient?.launchBillingFlow(activity, billingFlowParams)
    }
}