package com.diego.mibiblioteca

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Bookhollow Premium: un pago único (producto «bookhollow_premium» de Google Play) que
 * desbloquea vincular y sincronizar una carpeta en la nube (Google Drive, Dropbox, OneDrive…).
 * Las carpetas del propio teléfono o tablet (Descargas, memoria interna, tarjeta SD) son
 * gratis, igual que todo lo demás.
 *
 * El estado se guarda en el teléfono para que funcione sin conexión y se confirma con
 * Google Play cada vez que se abre la app (así se respetan los reembolsos). Las compras se
 * reconocen (acknowledge) al momento: si no, Google las reembolsa a los tres días.
 */
internal object Premium {
    const val PRODUCT_ID = "bookhollow_premium"
    private const val PREFS = "premium"
    // Versión 1.0.94 guardaba simples verdadero/falso; desde la 1.0.95 se guarda una firma
    // ligada a este dispositivo, para que copiar o editar el archivo de ajustes no active Premium.
    private const val KEY_UNLOCKED_OLD = "unlocked"
    private const val KEY_LEGACY_OLD = "legacy_folder"
    private const val KEY_UNLOCKED = "unlock_token"
    private const val KEY_LEGACY = "legacy_token"
    private const val KEY_LEGACY_CHECKED = "legacy_checked"

    /** ¿Está desbloqueado? (compra en Google Play o instalación anterior con carpeta). */
    var unlocked by mutableStateOf(false); private set
    /** Precio con su moneda, tal como lo da Google Play (p. ej. «1,99 €»). Null si no se sabe. */
    var price by mutableStateOf<String?>(null); private set
    /** false cuando Google Play no está disponible (APK instalada fuera de la tienda). */
    var storeAvailable by mutableStateOf(true); private set
    var purchasing by mutableStateOf(false); private set
    /** Aviso para el usuario tras comprar o restaurar (se muestra una vez). */
    var notice by mutableStateOf<String?>(null)

    /** Proveedores de Android para carpetas del propio dispositivo: siempre gratis. */
    private val LOCAL_AUTHORITIES = setOf(
        "com.android.externalstorage.documents",     // memoria interna y tarjeta SD
        "com.android.providers.downloads.documents", // Descargas
        "com.android.providers.media.documents"      // Imágenes, audio y documentos del teléfono
    )

    /** ¿La carpeta está en el propio teléfono o tablet? (Cualquier otro proveedor es una nube.) */
    fun isLocalAuthority(authority: String?): Boolean = authority in LOCAL_AUTHORITIES

    fun isCloudFolder(uri: Uri?): Boolean = uri != null && !isLocalAuthority(uri.authority)

    /** ¿Se puede vincular y sincronizar esta carpeta? Locales siempre; nube, con Premium. */
    fun canUseFolder(uri: Uri?): Boolean = unlocked || !isCloudFolder(uri)

    private var client: BillingClient? = null
    private var product: ProductDetails? = null
    private var appContext: Context? = null

    fun init(context: Context, folderAlreadyLinked: Boolean) {
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        deviceId = runCatching {
            android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val edit = prefs.edit()
        // Quien ya tenía una carpeta vinculada antes de existir Premium la conserva.
        if (!prefs.getBoolean(KEY_LEGACY_CHECKED, false)) {
            val legacy = if (prefs.contains(KEY_LEGACY_OLD)) prefs.getBoolean(KEY_LEGACY_OLD, false) else folderAlreadyLinked
            edit.putBoolean(KEY_LEGACY_CHECKED, true)
            if (legacy) edit.putString(KEY_LEGACY, token("legacy"))
        }
        if (prefs.getBoolean(KEY_UNLOCKED_OLD, false)) edit.putString(KEY_UNLOCKED, token("purchase"))
        edit.remove(KEY_UNLOCKED_OLD).remove(KEY_LEGACY_OLD).apply()
        unlocked = hasToken(prefs.getString(KEY_UNLOCKED, null), "purchase") || legacyGranted(prefs)
        if (client != null) return
        client = BillingClient.newBuilder(app)
            .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        connect()
    }

    private fun connect() {
        val billing = client ?: return
        try {
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    storeAvailable = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (storeAvailable) {
                        refreshPurchases()
                        loadProduct()
                    }
                }
                override fun onBillingServiceDisconnected() = Unit
            })
        } catch (_: Exception) { storeAvailable = false }
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build())).build()
        client?.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val found = details.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
            product = found
            price = found?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                ?: found?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    /** Comprueba con Google Play qué se ha comprado (también sirve para «Restaurar compra»). */
    fun refreshPurchases(fromUser: Boolean = false) {
        val billing = client
        if (billing == null || !storeAvailable) {
            if (fromUser) notice = tr("La compra solo está disponible en la versión de Google Play.")
            return
        }
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.filter { PRODUCT_ID in it.products }
            owned.forEach(::grantIfPurchased)
            val bought = owned.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            save(bought)
            if (fromUser) notice = if (bought) tr("Compra restaurada. ¡Gracias por apoyar Bookhollow!")
                else tr("No hay ninguna compra de Bookhollow Premium en esta cuenta de Google.")
        }
    }

    fun buy(activity: Activity) {
        val billing = client
        val details = product
        if (billing == null || !storeAvailable || details == null) {
            notice = tr("La compra solo está disponible en la versión de Google Play.")
            if (billing != null && details == null) loadProduct()
            return
        }
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (offerToken != null) setOfferToken(offerToken) }
            .build()
        purchasing = true
        val result = billing.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) purchasing = false
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        purchasing = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases.orEmpty().filter { PRODUCT_ID in it.products }.forEach(::grantIfPurchased)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refreshPurchases(fromUser = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> notice = tr("No se pudo completar la compra. Inténtalo de nuevo.")
        }
    }

    private fun grantIfPurchased(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!unlocked) notice = tr("¡Gracias! Bookhollow Premium está activado.")
                save(true)
                if (!purchase.isAcknowledged) client?.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) { }
            }
            Purchase.PurchaseState.PENDING ->
                notice = tr("El pago está pendiente. Premium se activará cuando Google lo confirme.")
            else -> Unit
        }
    }

    private fun save(bought: Boolean) {
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE) ?: return
        prefs.edit().apply { if (bought) putString(KEY_UNLOCKED, token("purchase")) else remove(KEY_UNLOCKED) }.apply()
        unlocked = bought || legacyGranted(prefs)
    }

    private var deviceId = ""

    private fun legacyGranted(prefs: android.content.SharedPreferences) =
        hasToken(prefs.getString(KEY_LEGACY, null), "legacy")

    private fun token(kind: String): String = premiumToken(deviceId, kind)

    private fun hasToken(stored: String?, kind: String): Boolean = stored != null && stored == token(kind)
}

/** Firma de Premium para este dispositivo: no sirve copiada a otro ni escrita a mano. */
internal fun premiumToken(deviceId: String, kind: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest("bookhollow|$kind|$deviceId|${Premium.PRODUCT_ID}|9f3c".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
