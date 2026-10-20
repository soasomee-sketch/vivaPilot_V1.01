package com.iptvplayer.app.testtools

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import com.iptvplayer.app.billing.BillingState
import com.iptvplayer.app.ui.AppViewModel

/**
 * WERSJA RELEASE modułu testowego: CAŁKOWICIE PUSTA. Prawdziwy panel testowy (symulacja płatności,
 * przyspieszanie czasu, resety) istnieje wyłącznie w app/src/debug/ i jest kompilowany tylko do
 * wersji debug — wersja release (ta, która trafia do Google Play) nie zawiera ani linijki tego
 * kodu, więc nie da się go uruchomić ani przypadkiem wypuścić.
 *
 * Ten plik istnieje po to, żeby wspólny kod (BillingManager, MainActivity) kompilował się w obu
 * wersjach z tymi samymi wywołaniami. Wszystko poniżej to no-op.
 */
object TestTools {
    /** Zawsze null: w release nie ma symulacji, Billing łączy się z prawdziwym Google Play. */
    @Suppress("UNUSED_PARAMETER")
    fun simulatedBillingState(context: Context): BillingState? = null

    /** Zawsze false: w release okno płatności Google Play nigdy nie jest podmieniane. */
    @Suppress("UNUSED_PARAMETER")
    fun interceptPurchaseFlow(activity: Activity, onFinished: () -> Unit): Boolean = false
}

/** Pusty odpowiednik pływającego znacznika "TEST" i panelu testowego. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TestToolsHost(viewModel: AppViewModel) {
}
