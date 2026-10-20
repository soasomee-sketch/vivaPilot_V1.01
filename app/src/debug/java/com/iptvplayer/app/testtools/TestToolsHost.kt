package com.iptvplayer.app.testtools

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptvplayer.app.BuildConfig
import com.iptvplayer.app.billing.BillingState
import com.iptvplayer.app.billing.TrialGate
import com.iptvplayer.app.data.ConsentEvent
import com.iptvplayer.app.data.LegalDocType
import com.iptvplayer.app.data.LegalDocuments
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.ui.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MODUŁ TESTOWY (TYMCZASOWY) — interfejs. Tylko wersja debug (app/src/debug/); w release jest
 * pusty odpowiednik. Pływający znacznik "TEST" przy prawej krawędzi ekranu otwiera panel,
 * w którym da się (bez kodowania, samymi przyciskami): przesuwać czas okresu próbnego,
 * symulować zakup / płatność oczekującą / zwrot, czyścić zgody, wywoływać ponownie ostrzeżenia
 * i przygotować gotowe scenariusze. Opis krok po kroku: TESTY_NA_TELEFONIE.md.
 */
@Composable
fun TestToolsHost(viewModel: AppViewModel) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // Mały znacznik przy prawej krawędzi, w połowie wysokości — nie zasłania paska ani
        // dolnej nawigacji. Wyraźnie pomarańczowy, żeby było jasne, że to wersja testowa.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .background(Color(0xE6FF6D00))
                .clickable { open = true }
                .padding(horizontal = 7.dp, vertical = 16.dp),
        ) {
            Text("TEST", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (open) {
            BackHandler { open = false }
            TestPanel(viewModel = viewModel, onClose = { open = false })
        }
    }
}

private val PanelBg = Color(0xFF101014)
private val CardBg = Color(0xFF1B1B22)
private val Orange = Color(0xFFFF6D00)
private val TextLight = Color(0xFFF2F2F7)
private val TextGray = Color(0xFF9A9AA8)
private val Green = Color(0xFF4CD97B)
private const val DAY = 24L * 60 * 60 * 1000

@Composable
private fun TestPanel(viewModel: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val billing by viewModel.billingState.collectAsState()
    var tick by remember { mutableStateOf(0) }
    var info by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }
    // Odczyt stanu przy każdej zmianie `tick` (po każdym naciśnięciu przycisku) i zmianie stanu zakupu.
    val status = remember(tick, billing) { readStatus(context, billing) }
    val store = remember { LocalStore(context) }

    fun done(message: String, block: () -> Unit) {
        block()
        info = "✔ $message"
        tick++
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(PanelBg)
            // pochłania dotknięcia, żeby nic pod panelem nie reagowało
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PANEL TESTOWY", color = Orange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("tymczasowy – tylko w wersji testowej", color = TextGray, fontSize = 12.sp)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                ) { Text("Zamknij panel") }
            }
            if (info.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(info, color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // ---------------- STAN ----------------
            SectionTitle("1. STAN (co aplikacja teraz „wie”)")
            Card {
                status.forEach { (label, value) ->
                    Text(label, color = TextGray, fontSize = 11.sp)
                    Text(value, color = TextLight, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                }
            }

            // ---------------- CZAS ----------------
            SectionTitle("2. CZAS – przyspieszanie okresu próbnego (5 dni)")
            Hint("Przesuwa „zegar próby” do przodu. Po przesunięciu zamknij panel i spróbuj użyć wyszukiwania, " +
                "Moich list albo pobierania – po końcu próby pojawi się okno zakupu.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton("+1 dzień", Modifier.weight(1f)) {
                    done("Czas przesunięty o 1 dzień") { store.addDebugTrialOffsetDays(1) }
                }
                PanelButton("+4 dni", Modifier.weight(1f)) {
                    done("Czas przesunięty o 4 dni") { store.addDebugTrialOffsetDays(4) }
                }
                PanelButton("+5 dni", Modifier.weight(1f)) {
                    done("Czas przesunięty o 5 dni – okres próbny powinien się skończyć") { store.addDebugTrialOffsetDays(5) }
                }
            }
            PanelButton("+30 dni") { done("Czas przesunięty o 30 dni") { store.addDebugTrialOffsetDays(30) } }
            PanelButton("Cofnij czas do normalnego (przesunięcie 0)") {
                done("Przesunięcie czasu wyzerowane") { store.resetDebugTrialOffset() }
            }
            PanelButton("Zacznij okres próbny od nowa (dzień 1)") {
                done("Okres próbny zaczyna się od nowa, przesunięcie 0") { store.restartTrialForTesting() }
            }

            // ---------------- ZAKUP ----------------
            SectionTitle("3. ZAKUP – symulacja płatności")
            Hint("Aplikacja zainstalowana z pliku nie może kupować w Google Play, więc płatność jest " +
                "symulowana: nic nie jest pobierane. Cała reszta (ekran przed zakupem, zapis zgód, " +
                "ekran potwierdzenia, odblokowanie funkcji) działa tak jak przy prawdziwym zakupie.")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Symulacja płatności włączona",
                    color = TextLight,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = TestTools.isSimulationEnabled(context),
                    onCheckedChange = { on ->
                        done(if (on) "Symulacja WŁĄCZONA – zamknij i otwórz aplikację ponownie" else "Symulacja WYŁĄCZONA (prawdziwy Google Play) – zamknij i otwórz aplikację ponownie") {
                            TestTools.setSimulationEnabled(context, on)
                        }
                    },
                )
            }
            PanelButton("Kup Pełną wersję – jak użytkownik (ekran przed zakupem → atrapa płatności)", accent = true) {
                onClose()
                viewModel.openPrePurchaseScreen()
            }
            PanelButton("Sprawdź zakup ponownie (jak przycisk w Ustawieniach)") {
                done("Odpytano o zakupy") { viewModel.refreshPurchases() }
            }
            PanelButton("Pokaż ekran potwierdzenia zakupu") {
                onClose()
                viewModel.openPurchaseConfirmationScreen()
            }
            PanelButton("Ustaw: PŁATNOŚĆ OCZEKUJĄCA (pending)") {
                done("Płatność oczekująca – pełna wersja NIE jest odblokowana") {
                    TestTools.setSimulatedPending(context)
                    viewModel.refreshPurchases()
                }
            }
            PanelButton("Ustaw: ZAKUP PRZYWRÓCONY (bez ekranu przed zakupem – jak po reinstalacji)") {
                done("Zakup przywrócony – bez automatycznego ekranu potwierdzenia") {
                    TestTools.setSimulatedPurchased(context)
                    viewModel.refreshPurchases()
                }
            }
            PanelButton("ZWROT / brak zakupu (pełna wersja znów zablokowana po próbie)") {
                done("Zakup wycofany (zwrot) – brak zakupu") {
                    TestTools.setSimulatedNone(context)
                    viewModel.refreshPurchases()
                }
            }

            // ---------------- ZGODY I OSTRZEŻENIA ----------------
            SectionTitle("4. ZGODY I OSTRZEŻENIA")
            PanelButton("Wyczyść zapisane zgody → ekran powitalny po ponownym uruchomieniu") {
                done("Zgody wyczyszczone (playlisty zostają). Aplikacja się zamyka – otwórz ją ponownie z ikony.") {
                    store.clearConsentRecordForTesting()
                    closeApp(context)
                }
            }
            PanelButton("Udawaj, że Regulamin i Polityka się zmieniły → informacja o zmianie") {
                done("Ustawiono starsze, „potwierdzone” wersje dokumentów. Aplikacja się zamyka – otwórz ją ponownie z ikony.") {
                    store.setAcknowledgedLegalDocVersion(LegalDocType.REGULAMIN.legalDocVersionKey, "0.99")
                    store.setAcknowledgedLegalDocVersion(LegalDocType.POLITYKA.legalDocVersionKey, "0.99")
                    closeApp(context)
                }
            }
            PanelButton("Pokaż znów ostrzeżenie o rzutowaniu („Wyślij do TV”)") {
                done("Ostrzeżenie o rzutowaniu pokaże się przy następnej próbie rzutowania") { store.resetCastWarningAckForTesting() }
            }
            PanelButton("Pokaż znów wstęp o reklamach (wersja z reklamami)") {
                done("Wstęp o reklamach pokaże się znów przy następnym uruchomieniu") { store.resetAdsConsentIntroForTesting() }
            }

            // ---------------- SCENARIUSZE ----------------
            SectionTitle("5. GOTOWE SCENARIUSZE (ustawiają stan i zamykają aplikację)")
            Hint("Po naciśnięciu aplikacja zamknie się sama. Otwórz ją ponownie z ikony – będzie w opisanym stanie.")
            PanelButton("Ostatni dzień próby (jutro koniec), brak zakupu") {
                val first = store.getFirstRunTimestamp()
                if (first == null) {
                    info = "✖ Brak daty pierwszego uruchomienia – uruchom aplikację normalnie i spróbuj ponownie"
                } else {
                    done("Ustawiono: ostatni dzień próby. Aplikacja się zamyka – otwórz ją ponownie z ikony.") {
                        TestTools.setSimulatedNone(context)
                        store.setDebugTrialOffsetMillis(4 * DAY + 2 * 60 * 60 * 1000L - (System.currentTimeMillis() - first))
                        closeApp(context)
                    }
                }
            }
            PanelButton("Okres próbny SKOŃCZONY, brak zakupu") {
                val first = store.getFirstRunTimestamp()
                if (first == null) {
                    info = "✖ Brak daty pierwszego uruchomienia – uruchom aplikację normalnie i spróbuj ponownie"
                } else {
                    done("Ustawiono: próba skończona. Aplikacja się zamyka – otwórz ją ponownie z ikony.") {
                        TestTools.setSimulatedNone(context)
                        store.setDebugTrialOffsetMillis(5 * DAY + 2 * 60 * 60 * 1000L - (System.currentTimeMillis() - first))
                        closeApp(context)
                    }
                }
            }

            // ---------------- APLIKACJA ----------------
            SectionTitle("6. APLIKACJA")
            PanelButton("Zamknij aplikację całkiem (potem otwórz ją ponownie z ikony)") {
                done("Zamykam aplikację…") { closeApp(context) }
            }
            PanelButton("Zupełnie nowy użytkownik – WYCZYŚĆ WSZYSTKO (jak świeża instalacja)", danger = true) {
                confirmWipe = true
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wyczyścić wszystko?") },
            text = {
                Text(
                    "Usunie playlisty, ustawienia, zgody, postęp oglądania i stan symulacji – " +
                        "aplikacja będzie jak świeżo zainstalowana (pokaże ekran powitalny, a okres próbny " +
                        "zacznie się od nowa). Aplikacja się zamknie.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    val ok = runCatching {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
                    }.getOrDefault(false)
                    if (!ok) info = "✖ Nie udało się wyczyścić – w ustawieniach telefonu: Aplikacje → VivaPilot → Pamięć → Wyczyść dane"
                }) { Text("Wyczyść wszystko") }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Anuluj") } },
        )
    }
}

/** Zamyka aplikację całkiem (proces zostaje zabity, więc stan Billing w pamięci też znika —
 * dokładnie jak „zamknięcie aplikacji z listy ostatnich”). Użytkownik otwiera ją ponownie z ikony. */
private fun closeApp(context: Context) {
    Toast.makeText(context, "Zamykam aplikację – otwórz ją ponownie z ikony", Toast.LENGTH_LONG).show()
    val activity = findActivity(context)
    Handler(Looper.getMainLooper()).postDelayed({
        activity?.finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }, 1200L)
}

private fun findActivity(context: Context): Activity? {
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private fun fmt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

private fun readStatus(context: Context, billing: BillingState): List<Pair<String, String>> {
    val store = LocalStore(context)
    val now = System.currentTimeMillis()
    val first = store.getFirstRunTimestamp()
    val offsetDays = store.getDebugTrialOffsetMillis() / DAY
    val expired = TrialGate.isExpired(context)

    val trial = when {
        billing.isPurchased -> "Kupione – okres próbny nie ma znaczenia"
        first == null -> "jeszcze nie wystartował"
        else -> {
            val elapsed = (now + store.getDebugTrialOffsetMillis() - first) / DAY
            if (expired) "MINĄŁ (od pierwszego uruchomienia upłynęło $elapsed dni)"
            else "TRWA – dzień ${elapsed + 1} z ${TrialGate.TRIAL_DAYS}"
        }
    }
    val purchase = when {
        billing.isPurchased -> "KUPIONE (zamówienie: ${billing.orderId ?: "brak numeru"})"
        billing.isPending -> "OCZEKUJE na potwierdzenie płatności"
        else -> "brak zakupu"
    }
    val sim = if (TestTools.isSimulationEnabled(context)) {
        "WŁĄCZONA (wynik: ${TestTools.simulatedResult(context)})"
    } else {
        "WYŁĄCZONA – prawdziwy Google Play"
    }
    val events = store.getConsentEvents()
    val consent = if (events.isEmpty()) {
        "brak zapisu – aplikacja pokaże ekran powitalny"
    } else {
        events.joinToString(" | ") { e ->
            when (e.eventType) {
                ConsentEvent.TYPE_ONBOARDING ->
                    "zdarzenie ${e.consentEventId.take(8)}: ekran powitalny ${e.timestampUtc}, Regulamin ${e.termsVersion}, " +
                        "Polityka ${e.privacyPolicyVersion}, 18+: ${if (e.age18DeclaredAtUtc != null) "tak" else "nie"}"
                else ->
                    "zdarzenie ${e.consentEventId.take(8)}: zakup ${e.timestampUtc}, zamówienie ${e.storeTransactionId ?: "brak numeru"}, " +
                        "pole 1: ${if (e.externalFeaturesAcknowledgedAtUtc != null) "tak" else "nie"}, " +
                        "pole 2: ${if (e.digitalContentWaiverAcceptedAtUtc != null) "tak" else "nie"}" +
                        if (e.eventType == ConsentEvent.TYPE_PURCHASE_RESTORED) " (przywrócony)" else ""
            }
        }
    }
    val regV = runCatching { LegalDocuments.load(context, LegalDocType.REGULAMIN).version }.getOrDefault("?")
    val polV = runCatching { LegalDocuments.load(context, LegalDocType.POLITYKA).version }.getOrDefault("?")
    val ackReg = store.getAcknowledgedLegalDocVersion(LegalDocType.REGULAMIN.legalDocVersionKey) ?: "—"
    val ackPol = store.getAcknowledgedLegalDocVersion(LegalDocType.POLITYKA.legalDocVersionKey) ?: "—"

    return listOf(
        "Wersja aplikacji" to "${BuildConfig.VERSION_NAME} (kod ${BuildConfig.VERSION_CODE})",
        "Okres próbny" to trial,
        "Pierwsze uruchomienie" to (first?.let { fmt(it) } ?: "—"),
        "Przesunięcie czasu (test)" to "+$offsetDays dni",
        "Zakup (to widzi aplikacja)" to purchase,
        "Symulacja płatności" to sim,
        "Rejestr zgód (zaszyfrowany)" to consent,
        "Dokumenty w aplikacji" to "Regulamin v$regV, Polityka v$polV",
        "Potwierdzone przez użytkownika (okno zmian)" to "Regulamin $ackReg, Polityka $ackPol",
    )
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(18.dp))
    Text(text, color = Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, color = TextGray, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(12.dp),
    ) { content() }
}

@Composable
private fun PanelButton(
    text: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                danger -> Color(0xFFB3261E)
                accent -> Orange
                else -> Color(0xFF2A2A35)
            },
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.padding(vertical = 3.dp),
    ) {
        Text(text, fontSize = 13.sp)
    }
}
