package com.iptvplayer.app.ads

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.util.isLeanbackDevice

/**
 * Implementacja WYŁĄCZNIE wariantu `ads` (patrz flavorDimensions w app/build.gradle.kts) —
 * sekcja 9 specyfikacji zgodności prawnej z 09.10. Odpowiednik-atrapa bez żadnego kodu
 * reklamowego: app/src/noads/java/.../ads/AdsController.kt (ta sama nazwa klasy i sygnatury
 * funkcji — wspólny kod appki, np. MainActivity.kt, woła `AdsController.cokolwiek(...)` bez
 * żadnego `if (BuildConfig.FLAVOR == "ads")`; Gradle sam dobiera właściwy plik).
 *
 * Reguła widoczności reklam, sprawdzana PRZY KAŻDYM wywołaniu (nigdy nie zapamiętywana na
 * stałe) — patrz [isEligible]: okres próbny zakończony, Pełna wersja NIE kupiona, urządzenie
 * NIE jest telewizorem (`PackageManager.FEATURE_LEANBACK`, bo AdMob SDK nie obsługuje Android
 * TV). Dodatkowo [mobileAdsInitialized] musi być prawdą — a to pole staje się prawdą DOPIERO
 * po przejściu przez zgodę Google UMP (patrz [AdsGate]), więc żadna reklama nie może się
 * pojawić przed obsłużeniem zgody, niezależnie od powyższych trzech warunków.
 */
object AdsController {
    @Volatile private var mobileAdsInitialized = false
    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var loadingInterstitial = false
    @Volatile private var lastInterstitialShownAt = 0L

    private fun isEligible(context: Context, trialExpired: Boolean, isPurchased: Boolean): Boolean =
        trialExpired && !isPurchased && !context.isLeanbackDevice()

    /**
     * Woła się raz z poziomu RootScreen (MainActivity.kt) — reaguje na zmianę `trialExpired`/
     * `isPurchased` (np. zakup w trakcie sesji) i, gdy warunki z [isEligible] są spełnione,
     * przeprowadza zgodę UMP i inicjalizuje SDK. Nie rysuje nic poza ewentualnym,
     * jednorazowym oknem informacyjnym PRZED pierwszą zgodą (sekcja 9: "Opcjonalnie przed
     * nim krótka informacja (...) [Dalej]").
     */
    @Composable
    fun AdsGate(activity: Activity, trialExpired: Boolean, isPurchased: Boolean) {
        val eligible = isEligible(activity, trialExpired, isPurchased)
        var showIntro by remember { mutableStateOf(false) }

        LaunchedEffect(eligible) {
            if (!eligible) return@LaunchedEffect
            if (LocalStore(activity).hasShownAdsConsentIntro()) {
                requestConsentAndInitIfNeeded(activity)
            } else {
                showIntro = true
            }
        }

        if (showIntro) {
            AlertDialog(
                // Celowo bez onDismissRequest zamykającego przez kliknięcie poza oknem/
                // przycisk Wstecz — to jednorazowa, krótka informacja prowadząca wprost do
                // okna zgody UMP, nie zwykły dialog do odrzucenia bez przeczytania.
                onDismissRequest = {},
                text = { Text(tr("ads.consentIntro.body")) },
                confirmButton = {
                    TextButton(onClick = {
                        showIntro = false
                        LocalStore(activity).setShownAdsConsentIntro()
                        requestConsentAndInitIfNeeded(activity)
                    }) { Text(tr("ads.consentIntro.continueButton")) }
                },
            )
        }
    }

    /**
     * `requestConsentInfoUpdate` + `loadAndShowConsentFormIfRequired` — dokładny wzorzec z
     * oficjalnej dokumentacji Google UMP (developers.google.com/admob/android/privacy).
     * `canRequestAds()` sprawdzone RAZEM: raz od razu (na podstawie stanu zapisanego z
     * poprzedniej sesji, żeby SDK mógł zainicjalizować się jak najszybciej), raz w callbacku
     * `loadAndShowConsentFormIfRequired` (na wypadek, gdyby zgoda zmieniła się WŁAŚNIE teraz).
     */
    private fun requestConsentAndInitIfNeeded(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()
        // Do testowania na własnym urządzeniu (Test #6 z sekcji 12 specyfikacji z 09.10):
        // Google pokazuje okno zgody TYLKO urządzeniom w regionie UE/Wielkiej Brytanii (albo
        // zarejestrowanym urządzeniom testowym). Żeby wymusić okno zgody na SWOIM telefonie
        // niezależnie od położenia, odkomentuj poniższe (hashedId z Logcat — SDK sam go
        // wypisuje przy pierwszym uruchomieniu, szukaj "Use new ConsentDebugSettings...").
        //
        // val debugSettings = com.google.android.ump.ConsentDebugSettings.Builder(activity)
        //     .addTestDeviceHashedId("TU_WKLEJ_HASHED_ID_Z_LOGCAT")
        //     .setDebugGeography(com.google.android.ump.ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        //     .build()
        // val params = ConsentRequestParameters.Builder()
        //     .setConsentDebugSettings(debugSettings)
        //     .build()
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        if (consentInformation.canRequestAds()) {
            initMobileAdsIfNeeded(activity)
        }
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _: FormError? ->
                    if (consentInformation.canRequestAds()) {
                        initMobileAdsIfNeeded(activity)
                    }
                }
            },
            { _: FormError ->
                // Błąd pobrania informacji o zgodzie (np. brak sieci) — po prostu nie
                // pokazujemy reklam w tej sesji; spróbujemy ponownie przy następnym
                // uruchomieniu AdsGate (np. kolejne wejście w stan "okres próbny minął").
            },
        )
    }

    private fun initMobileAdsIfNeeded(context: Context) {
        if (mobileAdsInitialized) return
        mobileAdsInitialized = true
        val appContext = context.applicationContext
        MobileAds.initialize(appContext) {
            preloadInterstitial(appContext)
        }
    }

    /** Baner — WYŁĄCZNIE na ekranach przeglądania (patrz wywołanie w MainActivity.RootScreen,
     * tuż nad IptvBottomNav). Nigdy nie jest wołany z PlayerActivity (osobna Activity, bez
     * importu com.iptvplayer.app.ads w ogóle) — "nigdy nad odtwarzaczem ani w PiP" z sekcji 9
     * jest więc spełnione samą strukturą kodu, nie dodatkowym warunkiem. */
    @Composable
    fun BannerAdStrip(trialExpired: Boolean, isPurchased: Boolean) {
        val context = LocalContext.current
        if (!isEligible(context, trialExpired, isPurchased) || !mobileAdsInitialized) return
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdConfig.BANNER_AD_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() },
        )
    }

    /** Reklama pełnoekranowa — TYLKO przy przejściu między ekranami (patrz wywołanie w
     * MainActivity.RootScreen, w `selectTab`, czyli dokładnie przy zmianie zakładki
     * nawigacji), nigdy podczas samego oglądania. Własny odstęp czasowy
     * ([com.iptvplayer.app.ads.AdConfig.MIN_INTERSTITIAL_INTERVAL_MILLIS]), żeby nie pokazywać
     * się za każdym pojedynczym kliknięciem. */
    fun maybeShowInterstitial(activity: Activity, trialExpired: Boolean, isPurchased: Boolean) {
        if (!isEligible(activity, trialExpired, isPurchased) || !mobileAdsInitialized) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownAt < AdConfig.MIN_INTERSTITIAL_INTERVAL_MILLIS) return
        val ad = interstitialAd
        if (ad == null) {
            // Jeszcze się nie załadowała (albo się właśnie ładuje) — pomijamy tym razem,
            // spróbujemy pokazać przy KOLEJNYM przejściu między ekranami.
            preloadInterstitial(activity.applicationContext)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                preloadInterstitial(activity.applicationContext)
            }
        }
        lastInterstitialShownAt = now
        ad.show(activity)
    }

    private fun preloadInterstitial(context: Context) {
        if (loadingInterstitial || interstitialAd != null) return
        loadingInterstitial = true
        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loadingInterstitial = false
                    interstitialAd = ad
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loadingInterstitial = false
                    interstitialAd = null
                }
            },
        )
    }

    /** Wiersz "Prywatność reklam" w Ustawieniach (sekcja 7) — tylko gdy UMP faktycznie
     * wymaga/pozwala na ponowne otwarcie formularza prywatności (czyli reklamy były już
     * kiedyś aktywne na tym urządzeniu; na TV nigdy, bo tam reklamy nigdy nie startują). */
    fun isPrivacyOptionsAvailable(context: Context): Boolean {
        if (context.isLeanbackDevice()) return false
        val status = UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus
        return status == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun openPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _: FormError? -> }
    }
}
