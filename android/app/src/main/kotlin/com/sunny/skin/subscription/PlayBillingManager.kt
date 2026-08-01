package com.sunny.skin.subscription

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.sunny.skin.BuildConfig
import com.sunny.skin.inference.ModelProvider
import com.sunny.skin.inference.download.ModelDownloadManager
import com.sunny.skin.inference.tier.SunnyPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BillingState {
    data object NotConfigured : BillingState
    data object Connecting : BillingState
    data object Ready : BillingState
    data class Error(val message: String) : BillingState
}

data class StorePlan(
    val plan: SunnyPlan,
    val title: String,
    val recurringPrice: String,
    val introPrice: String?,
    val basePlanId: String,
    val billingPeriod: String,
    val offerToken: String,
    val includesIntroOffer: Boolean,
)

/**
 * Single process-wide Play Billing connection. A Play callback never grants
 * access directly: each PURCHASED token must receive a short-lived backend
 * verdict first, and only then is it acknowledged and published to the app.
 */
class PlayBillingManager(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val verifier = PlayEntitlementVerifier()
    private val installationId = InstallationIdentity(appContext).value
    private var entitlementRefreshJob: Job? = null
    private val detailsByPlan = mutableMapOf<SunnyPlan, ProductDetails>()
    private val _state = MutableStateFlow<BillingState>(BillingState.NotConfigured)
    val state: StateFlow<BillingState> = _state.asStateFlow()
    private val _plans = MutableStateFlow<List<StorePlan>>(emptyList())
    val plans: StateFlow<List<StorePlan>> = _plans.asStateFlow()

    private val client = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        if (BuildConfig.SUNNY_ENTITLEMENT_API_URL.isBlank()) {
            clearAccess()
            _state.value = BillingState.NotConfigured
            return
        }
        refreshFreeAccess()
        if (client.isReady) {
            refresh()
            return
        }
        _state.value = BillingState.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = BillingState.Ready
                    queryProducts()
                    refresh()
                } else {
                    fail("Play Billing unavailable (${result.responseCode}).")
                }
            }

            override fun onBillingServiceDisconnected() {
                _state.value = BillingState.Connecting
            }
        })
    }

    /** Re-query Play whenever the app returns to the foreground. */
    fun refresh() {
        if (!client.isReady) {
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                verifyAndPublish(purchases)
            } else {
                fallBackToFreeAccess()
                fail("Could not restore purchases (${result.responseCode}).")
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> verifyAndPublish(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> fail("Purchase was not completed (${result.responseCode}).")
        }
    }

    fun launchPurchase(activity: Activity, offer: StorePlan): BillingResult {
        require(offer.plan == SunnyPlan.PRO)
        val details = detailsByPlan[offer.plan]
            ?: return errorResult("Subscription details are still loading.")
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        return client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build(),
        )
    }

    private fun verifyAndPublish(purchases: List<Purchase>) {
        val purchased = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased.isEmpty()) {
            fallBackToFreeAccess()
            return
        }
        scope.launch {
            val verified = purchased.mapNotNull { purchase ->
                verifier.verify(purchase.purchaseToken, purchase.products, installationId).getOrNull()
                    ?.let { purchase to it }
            }
            val best = verified.maxByOrNull { (_, result) -> result.entitlement.paidPlan.ordinal }
            if (best == null) {
                fallBackToFreeAccess()
                fail("Play purchase verification failed.")
                return@launch
            }
            val (_, result) = best
            runCatching {
                SubscriptionEntitlements.publishVerified(
                    result.entitlement,
                    result.cloudInference,
                    result.modelDownloads,
                )
                ModelProvider.reset()
                ModelDownloadManager.refresh()
                scheduleRefresh(
                    minOf(
                        result.entitlement.verifiedUntilMillis,
                        result.cloudInference?.quota?.dayResetsAtMillis ?: Long.MAX_VALUE,
                    ),
                )
            }.onFailure {
                fallBackToFreeAccess()
                fail("The entitlement response was invalid.")
                return@launch
            }
            verified.map { it.first }.filterNot { it.isAcknowledged }.forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(params) { acknowledgement ->
                    if (acknowledgement.responseCode != BillingClient.BillingResponseCode.OK) {
                        fail("Purchase verified, but acknowledgement is pending.")
                    }
                }
            }
            _state.value = BillingState.Ready
        }
    }

    private fun queryProducts() {
        val requested = listOf(
            SunnyPlan.PRO to BuildConfig.SUNNY_PRO_PRODUCT_ID,
        )
        val products = requested.map { (_, id) ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build(),
        ) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                fail("Could not load subscription plans (${result.responseCode}).")
                return@queryProductDetailsAsync
            }
            detailsByPlan.clear()
            queryResult.productDetailsList.forEach { details ->
                val plan = requested.firstOrNull { it.second == details.productId }?.first
                    ?: return@forEach
                detailsByPlan[plan] = details
            }
            _plans.value = detailsByPlan.flatMap { (plan, details) ->
                details.subscriptionOfferDetails.orEmpty()
                    .groupBy { it.basePlanId }
                    .values
                    .mapNotNull { offers ->
                        val selected = offers.firstOrNull {
                            BuildConfig.SUNNY_PRO_INTRO_OFFER_TAG in it.offerTags
                        } ?: offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
                        selected?.let { offer ->
                            val phases = offer.pricingPhases.pricingPhaseList
                            val recurring = phases.lastOrNull { it.priceAmountMicros > 0 }
                                ?: phases.lastOrNull()
                                ?: return@let null
                            StorePlan(
                                plan = plan,
                                title = details.name,
                                recurringPrice = recurring.formattedPrice,
                                introPrice = phases.dropLast(1)
                                    .firstOrNull { it.priceAmountMicros > 0 }
                                    ?.formattedPrice,
                                basePlanId = offer.basePlanId,
                                billingPeriod = recurring.billingPeriod,
                                offerToken = offer.offerToken,
                                includesIntroOffer = BuildConfig.SUNNY_PRO_INTRO_OFFER_TAG in
                                    offer.offerTags,
                            )
                        }
                    }
            }.sortedWith(compareBy({ it.plan.ordinal }, { it.billingPeriod != "P1Y" }))
        }
    }

    private fun fail(message: String) {
        _state.value = BillingState.Error(message)
    }

    private fun clearAccess() {
        entitlementRefreshJob?.cancel()
        entitlementRefreshJob = null
        SubscriptionEntitlements.clear()
        ModelProvider.reset()
        ModelDownloadManager.refresh()
    }

    private fun fallBackToFreeAccess() {
        entitlementRefreshJob?.cancel()
        entitlementRefreshJob = null
        SubscriptionEntitlements.clear()
        ModelProvider.reset()
        ModelDownloadManager.refresh()
        refreshFreeAccess()
    }

    private fun refreshFreeAccess() {
        if (BuildConfig.SUNNY_ENTITLEMENT_API_URL.isBlank()) return
        scope.launch {
            verifier.issueFree(installationId).onSuccess { result ->
                val now = System.currentTimeMillis()
                if (SubscriptionEntitlements.current.value.effectivePlan(now) == SunnyPlan.PRO) {
                    return@onSuccess
                }
                runCatching {
                    SubscriptionEntitlements.publishVerified(
                        result.entitlement,
                        result.cloudInference,
                        result.modelDownloads,
                    )
                    ModelProvider.reset()
                    ModelDownloadManager.refresh()
                    scheduleRefresh(
                        minOf(
                            result.entitlement.verifiedUntilMillis,
                            result.cloudInference?.quota?.dayResetsAtMillis ?: Long.MAX_VALUE,
                        ),
                    )
                }.onFailure { fail("Free cloud access response was invalid.") }
            }.onFailure {
                if (SubscriptionEntitlements.current.value.paidPlan != SunnyPlan.PRO) {
                    fail("Sunny AI Cloud is temporarily unavailable.")
                }
            }
        }
    }

    private fun scheduleRefresh(verifiedUntilMillis: Long) {
        entitlementRefreshJob?.cancel()
        val delayMillis = (verifiedUntilMillis - System.currentTimeMillis() - 60_000L)
            .coerceAtLeast(60_000L)
        entitlementRefreshJob = scope.launch {
            delay(delayMillis)
            refresh()
        }
    }

    private fun errorResult(message: String): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.ERROR)
        .setDebugMessage(message)
        .build()

    fun close() = client.endConnection()
}
