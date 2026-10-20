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
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.LegalDocType
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.ui.theme.Bg
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.TextMain
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.util.isTvDevice

/**
 * Ekran "przed zakupem" (sekcja 4 specyfikacji zgodności prawnej z 09.10; teksty z
 * VivaPilot 1.12, punkty 3–5 promptu) — pokazuje się po KAŻDYM naciśnięciu "Kup Pełną wersję"
 * w PurchasePromptDialog, zawsze PRZED oknem płatności Google Play (patrz
 * AppViewModel.openPrePurchaseScreen/confirmPrePurchaseAndLaunchBilling — to JEDYNA droga do
 * launchPurchaseFlow w całej appce).
 *
 * Dwa OSOBNE pola, OBA domyślnie odznaczone, OBA wymagane do aktywowania przycisku "Przejdź do
 * płatności":
 * 1) zależność funkcji appki od źródła treści Użytkownika, usług zewnętrznych i urządzenia
 *    (appka nie gwarantuje ich działania — Wydawca nie ma nad nimi kontroli),
 * 2) zgoda na natychmiastowe rozpoczęcie świadczenia (odblokowanie Pełnej wersji) przed
 *    upływem 14-dniowego terminu na odstąpienie od umowy i utrata prawa odstąpienia (to samo
 *    zastrzeżenie co na ekranie powitalnym, powtórzone bezpośrednio przy zakupie).
 * Data i godzina zaznaczenia KAŻDEGO pola z osobna jest przekazywana do onProceed i zapisywana
 * w AppViewModel.confirmPrePurchaseAndLaunchBilling (patrz LocalStore.setPendingPurchaseConsents);
 * po udanym zakupie trafia do rejestru zgód jako zdarzenie 2 razem z numerem zamówienia (patrz
 * ConsentEvent.kt) i jest widoczna w Ustawieniach › Informacje prawne › Moje zgody.
 *
 * Pod treścią ZAWSZE widnieje linia z danymi Wydawcy (klucz purchaseConfirmation.publisherRow).
 *
 * POD przyciskiem "Przejdź do płatności" stoi mała czcionka (prepurchase.smallPrint): "Klikając
 * „Przejdź do płatności", potwierdzasz, że zapoznałeś się z [Regulaminem] i [Polityką
 * prywatności]. Płatność obsługuje Google Play. Szczegóły: § 4, § 8 i § 9 Regulaminu." — słowa w
 * nawiasach kwadratowych w kluczu i18n są klikalnymi linkami (1. = Regulamin, 2. = Polityka
 * prywatności; nawiasów nie widać). Ostateczny przycisk płatności i jego napis "zamówienie z
 * obowiązkiem zapłaty" pochodzą z okna sklepu Google Play — nie zastępujemy ich własnymi.
 * Na telewizorze (pilot D-pad nie nawiguje po pojedynczych słowach w bloku tekstu — patrz
 * obszerny komentarz w OnboardingScreen.kt) pod zdaniem dodatkowo są dwa osobne, fokusowalne
 * wiersze z tymi samymi linkami.
 */
@Composable
fun PrePurchaseScreen(onProceed: (check1AtMillis: Long, check2AtMillis: Long) -> Unit, onCancel: () -> Unit) {
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    // Data i godzina zaznaczenia KAŻDEGO z dwóch pól osobno (sekcja 2 specyfikacji, "Trwały
    // zapis zgód": po zakupie do rekordu zgód dopisywane są momenty zaznaczenia pola 1 i
    // pola 2). Odznaczenie pola zeruje jego znacznik; ponowne zaznaczenie ustawia nowy.
    var check1At by remember { mutableStateOf(0L) }
    var check2At by remember { mutableStateOf(0L) }
    val setCheck1: (Boolean) -> Unit = { checked ->
        check1 = checked
        check1At = if (checked) System.currentTimeMillis() else 0L
    }
    val setCheck2: (Boolean) -> Unit = { checked ->
        check2 = checked
        check2At = if (checked) System.currentTimeMillis() else 0L
    }
    var openedDoc by remember { mutableStateOf<LegalDocType?>(null) }
    val isTv = LocalContext.current.isTvDevice()

    val doc = openedDoc
    if (doc != null) {
        // Wstecz z tekstu Regulaminu/Polityki wraca na ekran przed zakupem (a nie zamyka go w całości).
        BackHandler { openedDoc = null }
        LegalDocumentScreen(type = doc, onBack = { openedDoc = null })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(tr("prepurchase.title"), color = TextMain, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(
                // Wariant z reklamami (prepurchase.bodyAds) — sekcja 9, wydanie `ads` (patrz
                // com.iptvplayer.app.ads.AdsController).
                tr(if (com.iptvplayer.app.BuildConfig.FLAVOR == "ads") "prepurchase.bodyAds" else "prepurchase.body"),
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            // Dane Wydawcy — ZAWSZE widoczne na tym ekranie (sekcja 4 specyfikacji: wymóg prawa
            // konsumenckiego, żeby przed zakupem było jasne, KTO sprzedaje). Ten sam tekst co
            // wiersz "Wydawca" na ekranie potwierdzenia zakupu i w Ustawieniach; bez żadnego
            // warunku — nie zależy od flavora, języka ani od stanu checkboxów.
            Text(
                tr("purchaseConfirmation.publisherRow"),
                color = TextMain,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setCheck1(!check1) }
                    .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
            ) {
                Checkbox(checked = check1, onCheckedChange = setCheck1)
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("prepurchase.checkbox1Label"),
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setCheck2(!check2) }
                    .tvFocusFrame(shape = RoundedCornerShape(8.dp))
                    .padding(vertical = 8.dp),
            ) {
                Checkbox(checked = check2, onCheckedChange = setCheck2)
                Spacer(Modifier.width(8.dp))
                Text(
                    tr("prepurchase.checkbox2Label"),
                    color = TextMain,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                ) {
                    Text(tr("prepurchase.cancelButton"))
                }
                Button(
                    onClick = { onProceed(check1At, check2At) },
                    enabled = check1 && check2,
                    modifier = Modifier.weight(1f).tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                ) {
                    Text(tr("prepurchase.payButton"))
                }
            }
            Spacer(Modifier.height(12.dp))
            // Nowa informacja POD przyciskiem płatności (punkt 4 promptu): małą czcionką, ze
            // słowami [Regulaminem] i [Polityką prywatności] jako linkami.
            val smallPrint = tr("prepurchase.smallPrint")
            val smallPrintStyle = MaterialTheme.typography.bodySmall.copy(color = TextDim)
            val annotated = buildAnnotatedString {
                var cursor = 0
                var linkIndex = 0
                for (m in Regex("\\[([^\\]]+)\\]").findAll(smallPrint)) {
                    append(smallPrint.substring(cursor, m.range.first))
                    pushStringAnnotation(tag = "DOC", annotation = if (linkIndex == 0) "REGULAMIN" else "POLITYKA")
                    withStyle(SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)) {
                        append(m.groupValues[1])
                    }
                    pop()
                    cursor = m.range.last + 1
                    linkIndex++
                }
                append(smallPrint.substring(cursor))
            }
            ClickableText(
                text = annotated,
                style = smallPrintStyle,
                onClick = { offset ->
                    annotated.getStringAnnotations(tag = "DOC", start = offset, end = offset).firstOrNull()?.let {
                        openedDoc = if (it.item == "REGULAMIN") LegalDocType.REGULAMIN else LegalDocType.POLITYKA
                    }
                },
            )
            if (isTv) {
                Spacer(Modifier.height(8.dp))
                LegalLinkRow(tr("onboarding.regulaminLink")) { openedDoc = LegalDocType.REGULAMIN }
                Spacer(Modifier.height(4.dp))
                LegalLinkRow(tr("onboarding.politykaLink")) { openedDoc = LegalDocType.POLITYKA }
            }
        }
    }
}

/** Fokusowalny wiersz-link do pełnego tekstu dokumentu (dla pilota na telewizorze) — ten sam
 * wzorzec co w OnboardingScreen.kt. */
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
        Text(label, color = Accent, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Accent)
    }
}
