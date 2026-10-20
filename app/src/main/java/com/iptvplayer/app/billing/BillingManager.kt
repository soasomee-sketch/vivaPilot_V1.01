package com.iptvplayer.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

// ============================================================================================
// UCZCIWE ZASTRZEŻENIE NA START: to jedyna część tej zmiany, której NIE dało się
// przetestować w żaden sposób dostępny w tym środowisku (żaden build/APK tego nie
// zweryfikuje) — wywołania do serwerów Google Play wymagają PRAWDZIWEGO wpisu appki w
// Google Play Console (z dokładnie tym ID produktu skonfigurowanym), czego jeszcze nie
// ma. Struktura poniżej odpowiada dobrze udokumentowanemu, aktualnemu na dziś kształtowi
// Google Play Billing Library (wersja 7.x) — ale realnie zadziała/da się zweryfikować
// dopiero po: (1) założeniu konta dewelopera, (2) wgraniu podpisanej appki choćby na
// wewnętrzny tor testowy, (3) skonfigurowaniu PRODUCT_ID_FULL_VERSION jako produktu
// jednorazowego w Play Console. Do tego czasu appka i tak bezpiecznie DZIAŁA — po prostu
// traktuje "nie mogę połączyć się z Billing" jako "nie kupiono", nie blokuje się przez to.
// ============================================================================================

/** ID produktu — MUSI dokładnie odpowiadać temu, co zostanie skonfigurowane w Google
 * Play Console. Zmiana wartości tutaj bez odpowiedniej zmiany w Konsoli spowoduje, że
 * appka nie znajdzie produktu (zapytanie zwróci pustą listę, zakup będzie niemożliwy).
 *
 * WAŻNE: celowo TYLKO jeden produkt, bez żadnego dodatkowego "znacznika próby"/produktu
 * za 0 zł — okres próbny jest liczony WYŁĄCZNIE lokalnie (patrz LocalStore.getFirstRunTimestamp
 * / TrialGate), zgodnie z wyraźnym wymogiem: appka nie ma śledzić próby przez Sklep. */
object BillingProducts {
    const val FULL_VERSION = "vivapilot_full_version"
}

/** Stan znany appce na podstawie tego, co Billing zdążył/zdołał sprawdzić. `unknown` to
 * stan "jeszcze nie wiadomo" (np. appka offline przy starcie) — w tym stanie appka NIE
 * blokuje niczego dodatkowo ponad to, co i tak wynika z lokalnej daty pierwszego
 * uruchomienia, żeby brak internetu nigdy nie zablokował legalnie kupionej appki. */
data class BillingState(
    val isPurchased: Boolean = false,
    // Zakup wysłany, ale Google jeszcze go nie rozliczył (np. płatność gotówkowa/opóźniona
    // metoda płatności) — Purchase.PurchaseState.PENDING. Dopóki to true, `isPurchased`
    // NIGDY nie jest ustawione na true (patrz handlePurchase) — appka nie odblokowuje
    // pełnej wersji, dopóki stan nie zmieni się na PURCHASED. UI może pokazać użytkownikowi
    // informację "płatność oczekuje na potwierdzenie" na podstawie tej flagi.
    val isPending: Boolean = false,
    val connectionEstablished: Boolean = false,
    // Dane z OSTATNIEGO wykrytego zakupu FULL_VERSION w stanie PURCHASED — do ekranu
    // potwierdzenia zakupu (sekcja 6, patrz PurchaseConfirmationScreen.kt): "Data zakupu",
    // "Numer zamówienia". Zostają wypełnione raz i NIE są czyszczone przy zwykłych
    // aktualizacjach (isFullRefresh=false) — tylko nadpisywane nowszymi, jeśli pojawi się
    // kolejny zakup tego samego produktu (w praktyce nie powinno się zdarzyć dla produktu
    // jednorazowego, ale nie ma powodu tracić te dane przy zwykłym odświeżeniu).
    val orderId: String? = null,
    val purchaseTimeMillis: Long? = null,
)

class BillingManager(private val context: Context) {
    private val TAG = "BillingManager"
    private var billingClient: BillingClient? = null

    var state = BillingState()
        private set

    // true od wywołania startConnection do odpowiedzi (udanej lub nie) albo rozłączenia —
    // chroni przed zamknięciem połączenia, które właśnie się nawiązuje (np. gdy użytkownik
    // naciśnie "Sprawdź zakup ponownie" w trakcie łączenia). Patrz start()/refreshPurchases().
    @Volatile
    private var connecting = false

    /** Wołane za każdym razem, gdy stan (zakup / oczekiwanie) mógł się zmienić — appka
     * wywołująca powinna wtedy odświeżyć swój UI na podstawie `state`. */
    var onStateChanged: (() -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchasesUpdate(purchases.orEmpty())
        } else {
            Log.w(TAG, "onPurchasesUpdated: kod=${billingResult.responseCode} ${billingResult.debugMessage}")
        }
    }

    /** Wołane raz, przy starcie appki (np. z Application.onCreate). Bezpieczne do wołania
     * wielokrotnie — kolejne wywołania są no-opem, jeśli połączenie już istnieje. */
    fun start() {
        // PANEL TESTOWY (tylko build debug — w release TestTools.simulatedBillingState zawsze
        // zwraca null i ta gałąź nigdy się nie wykonuje): zamiast łączyć się z Google Play,
        // stan zakupu pochodzi z symulacji wybranej w panelu. Patrz app/src/debug/.../testtools.
        if (applySimulatedStateIfActive(postToMainThread = true)) return
        if (billingClient?.isReady == true || connecting) return
        // Poprzedni, rozłączony klient (jeśli był) jest zamykany, zanim powstanie nowy —
        // inaczej każde ponowne połączenie zostawiałoby po sobie nieużywany obiekt.
        billingClient?.endConnection()
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()
        billingClient = client
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    state = state.copy(connectionEstablished = true)
                    refreshPurchases()
                } else {
                    Log.w(TAG, "Billing setup nieudany: ${billingResult.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false
                // Celowo bez automatycznego ponawiania połączenia tutaj — appka i tak
                // sprawdzi ponownie przy następnym uruchomieniu, a w międzyczasie polega
                // na lokalnej dacie pierwszego uruchomienia (patrz komentarz przy
                // BillingState wyżej: brak połączenia NIGDY nie blokuje appki dodatkowo).
                state = state.copy(connectionEstablished = false)
            }
        })
    }

    /** Odpytuje Google Play o WSZYSTKIE aktualne zakupy użytkownika (konto Google, nie samo
     * urządzenie — przetrwa reinstalację appki i działa na innym urządzeniu z tym samym
     * kontem). Wołane automatycznie przy KAŻDYM starcie appki (patrz start() powyżej) oraz
     * ręcznie z przycisku "Sprawdź zakup ponownie" w Ustawieniach › Zakup (patrz
     * AppViewModel.refreshPurchases). Jeśli pełna wersja NIE pojawia się już na liście (np.
     * zakup został zwrócony/unieważniony w Play Console), appka cofa `isPurchased` z powrotem
     * na false — funkcje premium blokują się ponownie przy następnym sprawdzeniu gate'u. */
    fun refreshPurchases() {
        // PANEL TESTOWY (tylko build debug) — patrz start().
        if (applySimulatedStateIfActive(postToMainThread = false)) return
        val client = billingClient
        // Brak połączenia z Google Play (nieudany start albo rozłączenie w trakcie działania):
        // zamiast po cichu nic nie robić, łączymy się od nowa — po udanym połączeniu
        // onBillingSetupFinished (patrz start()) samo woła refreshPurchases() jeszcze raz.
        // Dzięki temu przycisk "Sprawdź zakup ponownie" naprawdę ponawia weryfikację.
        if (client == null || !client.isReady) {
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchasesUpdate(purchases, isFullRefresh = true)
            } else {
                Log.w(TAG, "queryPurchasesAsync nieudane: ${billingResult.debugMessage}")
            }
        }
    }

    /** Przelicza stan na podstawie zestawu zakupów. `isFullRefresh=true` (tylko z
     * refreshPurchases()) oznacza "to KOMPLETNA, aktualna lista zakupów użytkownika" — więc
     * brak FULL_VERSION na tej liście aktywnie COFA isPurchased. Przy zwykłych aktualizacjach
     * z purchasesUpdatedListener (isFullRefresh=false, dotyczy pojedynczej, świeżo zmienionej
     * transakcji) brak FULL_VERSION na liście nic nie znaczy (ta transakcja mogła dotyczyć
     * np. innego produktu) — więc isPurchased NIGDY nie jest tam cofane. */
    private fun handlePurchasesUpdate(purchases: List<Purchase>, isFullRefresh: Boolean = false) {
        var nextPurchased = if (isFullRefresh) false else state.isPurchased
        var nextPending = false
        var nextOrderId = state.orderId
        var nextPurchaseTime = state.purchaseTimeMillis
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    if (purchase.products.contains(BillingProducts.FULL_VERSION)) {
                        nextPurchased = true
                        nextOrderId = purchase.orderId
                        nextPurchaseTime = purchase.purchaseTime
                    }
                    acknowledgeIfNeeded(purchase)
                }
                Purchase.PurchaseState.PENDING -> {
                    if (purchase.products.contains(BillingProducts.FULL_VERSION)) {
                        nextPending = true
                    }
                }
                else -> Unit // UNSPECIFIED_STATE — nic nie robimy
            }
        }
        val changed = nextPurchased != state.isPurchased || nextPending != state.isPending ||
            nextOrderId != state.orderId || nextPurchaseTime != state.purchaseTimeMillis
        if (changed) {
            state = state.copy(
                isPurchased = nextPurchased,
                isPending = nextPending,
                orderId = nextOrderId,
                purchaseTimeMillis = nextPurchaseTime,
            )
            onStateChanged?.invoke()
        }
    }

    /**
     * Tryb symulacji z panelu testowego (build debug). Jeśli aktywny, ustawia `state` tak, jakby
     * Google Play właśnie zwrócił taki wynik, i woła onStateChanged — dokładnie tą samą drogą co
     * prawdziwa odpowiedź (AppViewModel zapisuje wtedy rekord zgód, pokazuje ekran potwierdzenia
     * itd.). Zwraca true, gdy symulacja jest aktywna (wtedy prawdziwe Billing nie jest używane).
     */
    private fun applySimulatedStateIfActive(postToMainThread: Boolean): Boolean {
        val sim = com.iptvplayer.app.testtools.TestTools.simulatedBillingState(context) ?: return false
        val applyNow: () -> Unit = {
            val changed = !state.connectionEstablished || sim.isPurchased != state.isPurchased ||
                sim.isPending != state.isPending || sim.orderId != state.orderId ||
                sim.purchaseTimeMillis != state.purchaseTimeMillis
            state = state.copy(
                isPurchased = sim.isPurchased,
                isPending = sim.isPending,
                orderId = sim.orderId,
                purchaseTimeMillis = sim.purchaseTimeMillis,
                connectionEstablished = true,
            )
            if (changed) onStateChanged?.invoke()
        }
        if (postToMainThread) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { applyNow() }
        } else {
            applyNow()
        }
        return true
    }

    /** Potwierdzenie zakupu (acknowledge) — WYMAGANE przez Google Play w ciągu 3 dni od
     * zakupu, inaczej zakup jest automatycznie zwracany użytkownikowi. Wołane TYLKO dla
     * zakupów w stanie PURCHASED (PENDING nie są jeszcze rozliczone, acknowledge wtedy
     * nie ma sensu i Billing Library by go odrzucił). */
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.acknowledgePurchase(ackParams) { ackResult ->
            if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Potwierdzenie zakupu nieudane: ${ackResult.debugMessage}")
            }
        }
    }

    /** Otwiera oficjalny ekran zakupu Google Play dla pełnej wersji. Wymaga Activity (nie
     * samego Context) — to wymóg samego API, nie coś, co dałoby się obejść. */
    fun launchPurchaseFlow(activity: Activity) {
        // PANEL TESTOWY (tylko build debug): w trybie symulacji zamiast okna Google Play
        // pokazuje się atrapa okna płatności z wyborem wyniku (sukces / oczekująca / anuluj).
        // W release TestTools.interceptPurchaseFlow zawsze zwraca false.
        if (com.iptvplayer.app.testtools.TestTools.interceptPurchaseFlow(activity) { refreshPurchases() }) return
        val client = billingClient ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingProducts.FULL_VERSION)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails nieudane: ${billingResult.debugMessage}")
                return@queryProductDetailsAsync
            }
            val details: ProductDetails = productDetailsList.firstOrNull() ?: run {
                Log.w(TAG, "Nie znaleziono produktu ${BillingProducts.FULL_VERSION} — sprawdź, czy jest skonfigurowany w Play Console")
                return@queryProductDetailsAsync
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build()
            client.launchBillingFlow(activity, flowParams)
        }
    }
}
