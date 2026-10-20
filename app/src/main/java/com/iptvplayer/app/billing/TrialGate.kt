package com.iptvplayer.app.billing

import android.content.Context
import com.iptvplayer.app.BuildConfig
import com.iptvplayer.app.IptvApp
import com.iptvplayer.app.data.LocalStore

/**
 * Centralny, bezstanowy punkt sprawdzania, czy okres próbny (5 dni) minął i appka nie
 * została kupiona. Wcześniej ta logika żyła WYŁĄCZNIE w AppViewModel.isTrialExpired, co
 * działało dla wszystkich ekranów pod MainActivity — ale PlayerActivity to OSOBNA Activity
 * (bez dostępu do tej samej instancji AppViewModel), a mimo to musi wiedzieć, czy pewne
 * funkcje są zablokowane. Stąd wydzielenie do jednego, prostego obiektu, którego może użyć
 * KAŻDY fragment appki mający tylko Context (Activity, Application, ViewModel) — bez
 * duplikowania samej reguły "5 dni od pierwszego uruchomienia, chyba że kupione" w dwóch
 * miejscach, które mogłyby się z czasem rozjechać.
 *
 * Model (zaktualizowany — wymóg zgodności prawnej/Regulaminu, supersedes ustalenia z 24.09):
 * po upływie okresu próbnego BEZ zakupu zablokowane są TYLKO: wyszukiwanie (Filmy/Seriale/
 * Na żywo), pełny widok "Moje listy" oraz pobieranie na urządzenie. WSZYSTKO INNE —
 * przeglądanie i odtwarzanie Live/Filmy/Seriale/Folder (w tym z już skonfigurowanych
 * wcześniej playlist), zarządzanie źródłami (dodawanie/edycja/usuwanie playlist), gwiazdka
 * ulubionych, wybór ścieżki audio/napisów, kontrola rodzicielska, mini-EPG ("teraz leci"),
 * pełny przewodnik po programie z archiwum (EPG+catch-up), PiP, "Wyślij do TV" (Cast) oraz
 * kopia zapasowa — działa ZAWSZE, niezależnie od zakupu. (TMDB i Watch Next, dawniej też
 * blokowane, zostały z aplikacji usunięte/nie są już objęte blokadą próby). Patrz
 * AppViewModel.requestPremiumAccess() (pokazuje dialog zakupu — do świadomych akcji
 * użytkownika: wyszukiwanie, Moje listy, pobieranie).
 */
object TrialGate {
    const val TRIAL_DAYS = 5

    fun isExpired(context: Context): Boolean {
        val app = context.applicationContext as IptvApp
        if (app.billingManager.state.isPurchased) return false
        val firstRun = LocalStore(context).getFirstRunTimestamp() ?: return false
        // Przesunięcie zegara TYLKO w buildzie debug (patrz LocalStore.getDebugTrialOffsetMillis
        // i SettingsScreen.kt) — w release ta gałąź nigdy się nie wykonuje, więc appka
        // wgrana do Google Play zawsze liczy rzeczywisty, lokalny upływ czasu.
        val debugOffset = if (BuildConfig.DEBUG) LocalStore(context).getDebugTrialOffsetMillis() else 0L
        val elapsedDays = (System.currentTimeMillis() + debugOffset - firstRun) / (24L * 60 * 60 * 1000)
        return elapsedDays >= TRIAL_DAYS
    }
}
