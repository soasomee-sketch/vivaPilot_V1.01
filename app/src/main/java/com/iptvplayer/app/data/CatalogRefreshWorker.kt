package com.iptvplayer.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Automatyczne odświeżanie katalogu (kanały, filmy, seriale) W TLE — bez otwierania appki.
 *
 * Po co: appka przy starcie od razu pokazuje katalog z lokalnej pamięci (patrz
 * LocalStore.getCatalogCache), a świeże dane z serwera dociąga dopiero równolegle. Bez tego
 * zadania "od razu widoczny" katalog był z ostatniego uruchomienia — czasem sprzed kilku dni
 * (brak nowych filmów/odcinków). Teraz system co ok. 12 godzin, gdy jest połączenie z siecią
 * (i bateria nie jest na wyczerpaniu), sam pobiera świeży katalog i zapisuje go lokalnie.
 *
 * WorkManager sam wybiera dogodny moment (np. gdy urządzenie i tak jest aktywne) i ponawia
 * próbę później, jeśli akurat nie było sieci. Program TV (EPG) celowo NIE jest tu
 * pobierany — appka trzyma go tylko w pamięci podczas działania, więc nie ma gdzie go zapisać.
 */
class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = LocalStore(applicationContext)
        // Bramka akceptacji (sekcja 2 specyfikacji: "Bez akceptacji aplikacja nie działa"):
        // zadanie mogło zostać zaplanowane przez wcześniejszą wersję appki, zanim istniał
        // ekran powitalny. Dopóki Użytkownik nie zaakceptował Regulaminu, NIE wykonujemy
        // żadnych połączeń sieciowych — kończymy od razu "sukcesem" (kolejna próba za ~12 h).
        if (!store.hasAcceptedOnboarding()) return@withContext Result.success()
        val playlists = store.getPlaylists()
        if (playlists.isEmpty()) return@withContext Result.success()
        val result = runCatching { CatalogFetcher.fetchAll(playlists) }.getOrNull()
            ?: return@withContext Result.retry()
        // Nie nadpisujemy dobrego katalogu PUSTYM wynikiem (np. wszystkie playlisty
        // chwilowo nie odpowiedziały) — wtedy lepiej spróbować później.
        if (result.live.isEmpty() && result.movies.isEmpty() && result.series.isEmpty()) {
            return@withContext Result.retry()
        }
        runCatching {
            store.saveCatalogCache(result.live, result.movies, result.series)
            store.setLastRefreshAt(result.checkedAt)
        }
        CrashLogger.breadcrumb("bgRefresh", "live=${result.live.size} movies=${result.movies.size} series=${result.series.size}")
        Result.success()
    }

    companion object {
        private const val WORK_NAME = "catalog_background_refresh"

        /** Wołane przy każdym starcie appki — KEEP: jeśli zadanie już jest zaplanowane, nic
         * się nie zmienia (nie resetuje odliczania przy każdym uruchomieniu). */
        fun schedule(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInitialDelay(3, TimeUnit.HOURS)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
