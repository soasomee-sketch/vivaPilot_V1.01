package com.iptvplayer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.LegalDocType
import com.iptvplayer.app.data.LegalDocuments
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.ui.theme.Bg
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.TextMain
import com.iptvplayer.app.ui.tv.tvFocusFrame

/** Numer wersji Regulaminu zapisywany przy akceptacji ekranu powitalnego (patrz
 * LocalStore.setOnboardingAccepted) — czytany z wbudowanego pliku assets/legal/regulamin.txt
 * w chwili akceptacji, więc ZAWSZE zgadza się z tym, co Użytkownik faktycznie mógł otworzyć
 * i przeczytać pod linkiem "Regulamin" na tym samym ekranie (patrz LegalDocuments.kt). */

/**
 * Ekran powitalny pokazywany JEDNORAZOWO, przy pierwszym uruchomieniu appki, przed
 * czymkolwiek innym — zastępuje dawny WithdrawalWaiverScreen/"Zanim zaczniesz" (patrz
 * sekcja 2 specyfikacji zgodności prawnej z 09.10).
 *
 * Jeden checkbox obejmujący ŁĄCZNIE trzy fakty:
 * 1) Użytkownik jest pełnoletni (ma ukończone 18 lat; grupa docelowa Aplikacji w Google Play),
 * 2) akceptuje Regulamin (link otwiera pełny tekst, patrz LegalDocumentScreen),
 * 3) zapoznał się z Polityką prywatności (link otwiera pełny tekst).
 * Przycisk "Rozpocznij" aktywny dopiero po zaznaczeniu — bez akceptacji nie można
 * korzystać z Aplikacji. Zapisujemy lokalnie, w prywatnej pamięci appki, zdarzenie 1
 * trwałego, zaszyfrowanego rejestru zgód: datę i godzinę akceptacji, numery wersji Regulaminu i Polityki
 * (np. "1.12_PL"), oświadczenie o pełnoletności i numer wersji appki (patrz
 * LocalStore.setOnboardingAccepted, ConsentEvent.kt, ConsentVault.kt i LegalDocuments.kt). Rejestr nigdzie nie jest wysyłany; Użytkownik widzi go (tylko do
 * odczytu) w Ustawieniach › Informacje prawne › Moje zgody.
 *
 * "Bez akceptacji aplikacja nie działa" (sekcja 2 specyfikacji): dopóki Użytkownik nie
 * zaznaczy pola i nie naciśnie "Rozpocznij", NIE pokazujemy żadnego innego ekranu, nie
 * startuje odtwarzanie ani pobieranie listy i nie ma żadnych połączeń sieciowych (patrz
 * IptvApp.startNetworkServices, AppViewModel.onOnboardingAccepted, CatalogRefreshWorker).
 * Przycisk Wstecz oraz przycisk "Nie akceptuję – zamknij aplikację" zamykają aplikację
 * (finishAffinity) i nic nie zapisują. Wstecz z otwartego tekstu Regulaminu/Polityki wraca
 * na ten ekran, nie zamyka aplikacji.
 *
 * W pełni obsługiwalny pilotem: checkbox, oba linki (Regulamin/Polityka) i przycisk
 * Rozpocznij to CZTERY osobne, niezależnie fokusowalne elementy (patrz tvFocusFrame na
 * każdym z nich) — celowo NIE są to linki osadzone w treści jednego akapitu, bo pojedynczych
 * słów w bloku tekstu nie da się nawigować pilotem D-pad.
 */
@Composable
fun OnboardingScreen(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    var openedDoc by remember { mutableStateOf<LegalDocType?>(null) }

    val doc = openedDoc
    if (doc != null) {
        // Wstecz z tekstu dokumentu = powrót na ekran powitalny (a nie zamknięcie appki).
        BackHandler { openedDoc = null }
        LegalDocumentScreen(type = doc, onBack = { openedDoc = null })
        return
    }

    val context = LocalContext.current
    // Wstecz na ekranie powitalnym = zamknięcie aplikacji (sekcja 2: "Bez akceptacji
    // aplikacja nie działa"). finishAffinity() zamyka całe zadanie, a nie tylko tę Activity.
    val closeApp: () -> Unit = { (context as? android.app.Activity)?.finishAffinity() }
    BackHandler { closeApp() }
    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                tr("onboarding.title"),
                color = TextMain,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                // Wariant z reklamami (onboarding.bodyAds) — sekcja 9 specyfikacji z 09.10,
                // wydanie `ads` (patrz flavorDimensions w app/build.gradle.kts i
                // com.iptvplayer.app.ads.AdsController).
                tr(if (com.iptvplayer.app.BuildConfig.FLAVOR == "ads") "onboarding.bodyAds" else "onboarding.body"),
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { checked = !checked }
                    .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
            ) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("onboarding.checkboxLabel"),
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            // Mała czcionka pod polem akceptacji (sekcja 2 specyfikacji): "Bez akceptacji nie
            // możesz korzystać z aplikacji. Jeśli po akceptacji przestaniesz zgadzać się z
            // Regulaminem, zaprzestań korzystania z aplikacji i ją odinstaluj."
            Spacer(Modifier.height(4.dp))
            Text(
                tr("onboarding.smallPrint"),
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            LegalLinkRow(
                label = tr("onboarding.regulaminLink"),
                onClick = { openedDoc = LegalDocType.REGULAMIN },
            )
            Spacer(Modifier.height(8.dp))
            LegalLinkRow(
                label = tr("onboarding.politykaLink"),
                onClick = { openedDoc = LegalDocType.POLITYKA },
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    // Wersje Regulaminu i Polityki czytane z wbudowanych plików w chwili
                    // akceptacji — ZAWSZE zgadzają się z tym, co Użytkownik mógł otworzyć pod
                    // linkami powyżej (patrz LegalDocuments.kt). Gdyby plik nie dał się
                    // odczytać, zapisujemy numer wersji appki (wersja dokumentów = wersja appki).
                    val appVersion = com.iptvplayer.app.BuildConfig.VERSION_NAME
                    val regulaminVersion = runCatching {
                        LegalDocuments.load(context, LegalDocType.REGULAMIN).version
                    }.getOrDefault(appVersion)
                    val polityVersion = runCatching {
                        LegalDocuments.load(context, LegalDocType.POLITYKA).version
                    }.getOrDefault(appVersion)
                    val localStore = LocalStore(context)
                    // Zdarzenie 1 w zaszyfrowanym rejestrze zgód w prywatnej pamięci appki (data i
                    // godzina UTC, wersje dokumentów, oświadczenie o pełnoletności, wersja appki) —
                    // patrz ConsentEvent.kt i ConsentVault.kt. Nigdzie nie jest wysyłane i nie da się go edytować.
                    localStore.setOnboardingAccepted(regulaminVersion, polityVersion, appVersion)
                    // Punkt odniesienia dla mechanizmu powiadomienia o zmianie dokumentów
                    // (sekcja 10 — patrz LegalChangeNotice w MainActivity.kt): przy PIERWSZEJ
                    // akceptacji zapisujemy wersję OBU dokumentów jako już "zaakceptowaną",
                    // żeby appka nie pokazała fałszywego powiadomienia "zmieniamy Regulamin"
                    // zaraz po ekranie powitalnym, w którym użytkownik dopiero co go
                    // zaakceptował.
                    LegalDocType.entries.forEach { type ->
                        val version = runCatching { LegalDocuments.load(context, type).version }.getOrNull()
                        if (version != null) {
                            localStore.setAcknowledgedLegalDocVersion(type.legalDocVersionKey, version)
                        }
                    }
                    onAccept()
                },
                enabled = checked,
                modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("onboarding.startButton"))
            }
            Spacer(Modifier.height(8.dp))
            // Odmowa: zamyka aplikację, nic nie zapisuje (przy następnym uruchomieniu ekran
            // powitalny pokaże się ponownie) i nie uruchamia żadnych usług sieciowych.
            OutlinedButton(
                onClick = closeApp,
                modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("onboarding.declineButton"))
            }
        }
    }
}

/** Jeden "link" (Regulamin / Polityka prywatności) jako osobny, w pełni fokusowalny
 * wiersz — patrz obszerny komentarz przy OnboardingScreen o tym, dlaczego nie są to
 * zamiast tego klikalne słowa osadzone w zdaniu. */
@Composable
private fun LegalLinkRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusFrame(shape = RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Text(
            label,
            color = Accent,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Accent)
    }
}

/**
 * Pełny tekst Regulaminu albo Polityki prywatności, wbudowany w aplikację (sekcja 7
 * specyfikacji z 09.10) — czytany z `assets/legal/*.txt` przez LegalDocuments.load().
 * Przewijalny, czytelny na telewizorze, z numerem wersji i datą wejścia w życie na górze.
 * Używany zarówno z linków na ekranie powitalnym (OnboardingScreen), jak i z Ustawień ->
 * Informacje prawne (SettingsScreen.kt).
 */
@Composable
fun LegalDocumentScreen(type: LegalDocType, onBack: () -> Unit) {
    val context = LocalContext.current
    val doc = remember(type) { LegalDocuments.load(context, type) }
    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(tr(type.titleKey), color = TextMain, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                tr("legal.versionLabel", doc.version, doc.effectiveDate),
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
            // Interfejs w innym języku niż polski: nad tekstem notka, że wiążąca jest polska
            // wersja dokumentu (teksty Regulaminu i Polityki są wbudowane tylko po polsku;
            // ewentualne tłumaczenia służą wyłącznie ułatwieniu zrozumienia).
            if (LocalLang.current != "pl") {
                Spacer(Modifier.height(12.dp))
                Text(
                    tr("legal.translationNotice"),
                    color = TextMain,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(doc.body, color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("common.back"))
            }
        }
    }
}
