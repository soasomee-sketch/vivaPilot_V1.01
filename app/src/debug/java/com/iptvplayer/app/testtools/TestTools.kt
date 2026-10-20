package com.iptvplayer.app.testtools

import android.app.Activity
import android.content.Context
import com.iptvplayer.app.billing.BillingState
import kotlin.random.Random

/**
 * MODUŁ TESTOWY (TYMCZASOWY) — logika. Istnieje WYŁĄCZNIE w wersji debug (app/src/debug/);
 * wersja release ma pusty odpowiednik w app/src/release/. Po zakończeniu testów moduł usuwa się
 * w całości (patrz TESTY_NA_TELEFONIE.md, rozdział "Jak to potem usunąć").
 *
 * Co robi:
 * - SYMULACJA ZAKUPU. Aplikacja zainstalowana "z pliku APK" nie może kupić niczego w Google Play
 *   (sklep nie zna takiej instalacji), więc w trybie symulacji (domyślnie WŁĄCZONYM) BillingManager
 *   nie pyta Google, tylko pyta tego obiektu o "wynik zakupu" (brak / oczekuje / kupione). Reszta
 *   appki — ekran przed zakupem, rekord zgód, ekran potwierdzenia, blokady funkcji, reklamy —
 *   działa dokładnie tak samo jak przy prawdziwym zakupie, bo wynik trafia tą samą drogą
 *   (BillingManager.applySimulatedStateIfActive -> onStateChanged -> AppViewModel).
 * - ATRAPA OKNA PŁATNOŚCI. "Przejdź do płatności" pokazuje proste okno z wyborem wyniku.
 *
 * Wynik symulacji jest zapamiętany na dysku (osobny plik "testtools"), więc po zamknięciu i
 * ponownym otwarciu appki Billing "dostaje" ten sam wynik — tak jak Google Play oddałby zakup
 * po restarcie. "Zwrot" w panelu ustawia wynik "brak".
 */
object TestTools {
    private const val PREFS = "testtools"
    private const val K_SIM_ENABLED = "sim_enabled"
    private const val K_SIM_STATE = "sim_state"
    private const val K_SIM_ORDER = "sim_order_id"
    private const val K_SIM_TIME = "sim_purchase_time"

    const val SIM_NONE = "none"
    const val SIM_PENDING = "pending"
    const val SIM_PURCHASED = "purchased"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Czy symulacja płatności jest włączona (domyślnie TAK). */
    fun isSimulationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(K_SIM_ENABLED, true)

    fun setSimulationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_SIM_ENABLED, enabled).commit()
    }

    /** Aktualny wynik symulacji: SIM_NONE / SIM_PENDING / SIM_PURCHASED. */
    fun simulatedResult(context: Context): String =
        prefs(context).getString(K_SIM_STATE, SIM_NONE) ?: SIM_NONE

    fun setSimulatedNone(context: Context) {
        prefs(context).edit().putString(K_SIM_STATE, SIM_NONE).commit()
    }

    fun setSimulatedPending(context: Context) {
        prefs(context).edit().putString(K_SIM_STATE, SIM_PENDING).commit()
    }

    fun setSimulatedPurchased(context: Context) {
        // Numer zamówienia wygląda jak prawdziwy z Google Play (GPA.xxxx-xxxx-xxxx-xxxxx), ale ma
        // przedrostek TEST, żeby nie dało się go pomylić z prawdziwym.
        val order = "GPA.TEST-" + (1..3).joinToString("-") { Random.nextInt(1000, 10000).toString() }
        prefs(context).edit()
            .putString(K_SIM_STATE, SIM_PURCHASED)
            .putString(K_SIM_ORDER, order)
            .putLong(K_SIM_TIME, System.currentTimeMillis())
            .commit()
    }

    /** Wynik pytania "co zwróciłby Google Play" — null, gdy symulacja jest wyłączona. */
    fun simulatedBillingState(context: Context): BillingState? {
        if (!isSimulationEnabled(context)) return null
        return when (simulatedResult(context)) {
            SIM_PURCHASED -> BillingState(
                isPurchased = true,
                connectionEstablished = true,
                orderId = prefs(context).getString(K_SIM_ORDER, null),
                purchaseTimeMillis = prefs(context).getLong(K_SIM_TIME, 0L).takeIf { it > 0L },
            )
            SIM_PENDING -> BillingState(isPending = true, connectionEstablished = true)
            else -> BillingState(connectionEstablished = true)
        }
    }

    /**
     * Atrapa okna płatności Google Play. Zwraca true, gdy przechwyciła zakup (symulacja włączona).
     * Prosty systemowy dialog (nie Compose) — działa niezależnie od reszty interfejsu.
     */
    fun interceptPurchaseFlow(activity: Activity, onFinished: () -> Unit): Boolean {
        if (!isSimulationEnabled(activity)) return false
        activity.runOnUiThread {
            android.app.AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("SYMULACJA okna płatności Google Play")
                .setMessage(
                    "VivaPilot – Pełna wersja (zakup jednorazowy)\n\n" +
                        "To atrapa do testów – żadne pieniądze nie są pobierane. " +
                        "Wybierz, jak ma się zakończyć płatność:",
                )
                .setPositiveButton("Zapłać – sukces") { _, _ ->
                    setSimulatedPurchased(activity)
                    onFinished()
                }
                .setNeutralButton("Płatność oczekująca") { _, _ ->
                    setSimulatedPending(activity)
                    onFinished()
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
        return true
    }
}
