package com.iptvplayer.app.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Implementacja WYŁĄCZNIE wariantu `noads` (domyślnego) — patrz flavorDimensions w
 * app/build.gradle.kts. Ten plik świadomie nie zawiera ŻADNEGO kodu reklamowego: żadnej
 * zależności od Google Mobile Ads/UMP, żadnego wywołania sieciowego, żadnej logiki —
 * wyłącznie puste funkcje o DOKŁADNIE tej samej nazwie klasy i sygnaturach co prawdziwa
 * implementacja w app/src/ads/java/.../ads/AdsController.kt. Dzięki temu wspólny kod appki
 * (MainActivity.kt, SettingsScreen.kt) może wołać `AdsController.cokolwiek(...)` bez ANI
 * JEDNEGO warunku `if (BuildConfig.FLAVOR == "ads")` rozsianego po całej appce — Gradle sam
 * dobiera WŁAŚCIWY plik (ten tutaj albo tamten) w zależności od budowanego wariantu
 * (standardowy mechanizm Android "source set" per-flavor, ten sam co np. różne
 * implementacje backendu mock/prawdziwy w innych aplikacjach).
 *
 * Sekcja 9 specyfikacji zgodności prawnej z 09.10: "W wariancie noads nie może być żadnego
 * kodu reklamowego ani uprawnienia AD_ID" — spełnione: brak zależności od SDK reklamowego w
 * ogóle (patrz app/build.gradle.kts, "adsImplementation" istnieje TYLKO dla wariantu `ads`),
 * więc uprawnienie com.google.android.gms.permission.AD_ID (pochodzące wyłącznie ze
 * scalonego manifestu play-services-ads) nie ma skąd się wziąć w scalonym manifeście tego
 * wariantu.
 */
object AdsController {
    @Composable
    fun AdsGate(activity: Activity, trialExpired: Boolean, isPurchased: Boolean) {
        // Nic — wariant bez reklam nigdy nie pyta o zgodę UMP ani nie inicjalizuje SDK.
    }

    @Composable
    fun BannerAdStrip(trialExpired: Boolean, isPurchased: Boolean) {
        // Nic — brak paska banera w tym wariancie.
    }

    fun maybeShowInterstitial(activity: Activity, trialExpired: Boolean, isPurchased: Boolean) {
        // Nic.
    }

    fun isPrivacyOptionsAvailable(context: Context): Boolean = false

    fun openPrivacyOptionsForm(activity: Activity) {
        // Nic — wiersz "Prywatność reklam" jest ukryty w tym wariancie (patrz
        // isPrivacyOptionsAvailable wyżej), więc ta funkcja nie powinna być nigdy wołana,
        // ale zostaje jako bezpieczny no-op na wszelki wypadek.
    }
}
