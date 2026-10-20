package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.IptvApp
import com.iptvplayer.app.billing.BillingState
import com.iptvplayer.app.data.LegalDocType
import com.iptvplayer.app.data.LegalDocuments
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.ui.theme.Bg
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.TextMain
import com.iptvplayer.app.ui.tv.tvFocusFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val UNKNOWN_VALUE = "—"

private fun formatPurchaseDate(millis: Long?, lang: String): String =
    millis?.let { SimpleDateFormat("d MMM yyyy, HH:mm", Locale(lang)).format(Date(it)) } ?: UNKNOWN_VALUE

/**
 * Ekran potwierdzenia zakupu (sekcja 6 specyfikacji zgodności prawnej z 09.10) — pokazywany
 * od razu po udanym zakupie ORAZ później, na żądanie, z Ustawień -> Informacje prawne ->
 * Zakup -> "Pokaż potwierdzenie" (patrz SettingsScreen.kt). Dane pochodzą z:
 * - `billingState.orderId`/`purchaseTimeMillis` — ostatni wykryty zakup FULL_VERSION
 *   (patrz BillingManager.handlePurchasesUpdate),
 * - `LocalStore.getPurchaseRegulaminVersion/Date` — migawka wersji Regulaminu z CHWILI
 *   zakupu (patrz AppViewModel.billingManager.onStateChanged), celowo NIE odczyt na żywo.
 *
 * "Zapisz potwierdzenie" woła `onSaveConfirmation` — faktyczny zapis pliku (SAF
 * `ACTION_CREATE_DOCUMENT`, patrz buildPurchaseConfirmationFileContent niżej) dzieje się w
 * MainActivity (SAF pickery nie da się wywołać z poziomu Composable, patrz istniejący wzorzec
 * przy eksporcie kopii zapasowej).
 */
@Composable
fun PurchaseConfirmationScreen(
    billingState: BillingState,
    onSaveConfirmation: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val store = remember { LocalStore(context) }

    val dateStr = formatPurchaseDate(billingState.purchaseTimeMillis, lang)
    val orderId = billingState.orderId ?: UNKNOWN_VALUE
    val regulaminVersion = store.getPurchaseRegulaminVersion() ?: UNKNOWN_VALUE
    val regulaminDate = store.getPurchaseRegulaminDate() ?: UNKNOWN_VALUE
    // "Pobierz potwierdzenie zgód" (VivaPilot 1.12, punkt 6 promptu) — ten sam eksport co na
    // ekranie Moje zgody (patrz ConsentRecordScreen.kt).
    val exportConsents = rememberConsentExportAction()

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(tr("purchaseConfirmation.title"), color = TextMain, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(tr("purchaseConfirmation.thanks"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(tr("purchaseConfirmation.heading"), color = TextMain, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(tr("purchaseConfirmation.productRow"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Text("${tr("purchaseConfirmation.dateLabel")} $dateStr", color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Text("${tr("purchaseConfirmation.orderLabel")} $orderId", color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Text(tr("purchaseConfirmation.channelRow"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Text(tr("purchaseConfirmation.publisherRow"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(tr("purchaseConfirmation.priorConfirmationText"), color = TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                tr("purchaseConfirmation.regulaminVersionRow", regulaminVersion, regulaminDate),
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Text(tr("purchaseConfirmation.emailNote"), color = TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = exportConsents,
                modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("consents.exportButton"))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onSaveConfirmation,
                    modifier = Modifier.weight(1f).tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                ) {
                    Text(tr("purchaseConfirmation.saveButton"))
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f).tvFocusFrame(shape = RoundedCornerShape(8.dp)),
                ) {
                    Text(tr("common.done"))
                }
            }
        }
    }
}

/**
 * Buduje pełną treść pliku zapisywanego przez "Zapisz potwierdzenie" — potwierdzenie zakupu
 * (ten sam tekst co na ekranie, w bieżącym języku appki) PLUS pełny tekst Regulaminu (sekcja
 * 6: "zapisuje plik tekstowy... z powyższą treścią oraz pełnym tekstem Regulaminu w wersji z
 * dnia zakupu"). Regulamin jest wbudowany w appkę tylko w JEDNEJ, AKTUALNEJ wersji (patrz
 * LegalDocuments.kt) — nie trzymamy osobno historycznych wersji dokumentu, więc gdy aktualna
 * wbudowana wersja różni się od migawki zapisanej w chwili zakupu (dokument został od tego
 * czasu zaktualizowany w nowszej wersji appki, patrz sekcja 10), dopisujemy o tym jawną
 * notkę zamiast milcząco podstawiać nowszy tekst pod starszy numer wersji.
 */
fun buildPurchaseConfirmationFileContent(context: android.content.Context, lang: String): String {
    val store = LocalStore(context)
    val billingState = (context.applicationContext as IptvApp).billingManager.state
    val billingDateStr = formatPurchaseDate(billingState.purchaseTimeMillis, lang)
    val orderId = billingState.orderId ?: UNKNOWN_VALUE
    val regulaminVersion = store.getPurchaseRegulaminVersion() ?: UNKNOWN_VALUE
    val regulaminDate = store.getPurchaseRegulaminDate() ?: UNKNOWN_VALUE
    val currentRegulamin = runCatching { LegalDocuments.load(context, LegalDocType.REGULAMIN) }.getOrNull()

    val sb = StringBuilder()
    sb.appendLine(trFor(lang, "purchaseConfirmation.title"))
    sb.appendLine()
    sb.appendLine(trFor(lang, "purchaseConfirmation.thanks"))
    sb.appendLine()
    sb.appendLine(trFor(lang, "purchaseConfirmation.heading"))
    sb.appendLine(trFor(lang, "purchaseConfirmation.productRow"))
    sb.appendLine("${trFor(lang, "purchaseConfirmation.dateLabel")} $billingDateStr")
    sb.appendLine("${trFor(lang, "purchaseConfirmation.orderLabel")} $orderId")
    sb.appendLine(trFor(lang, "purchaseConfirmation.channelRow"))
    sb.appendLine(trFor(lang, "purchaseConfirmation.publisherRow"))
    sb.appendLine()
    sb.appendLine(trFor(lang, "purchaseConfirmation.priorConfirmationText"))
    sb.appendLine(trFor(lang, "purchaseConfirmation.regulaminVersionRow", regulaminVersion, regulaminDate))
    sb.appendLine()
    sb.appendLine(trFor(lang, "purchaseConfirmation.emailNote"))
    if (currentRegulamin != null) {
        sb.appendLine()
        sb.appendLine("----------------------------------------")
        sb.appendLine()
        sb.appendLine(trFor(lang, "purchaseConfirmation.fullTextNote"))
        sb.appendLine()
        // Jak w LegalDocumentScreen: poza językiem polskim notka, że wiążąca jest wersja polska.
        if (lang != "pl") {
            sb.appendLine(trFor(lang, "legal.translationNotice"))
            sb.appendLine()
        }
        sb.appendLine(currentRegulamin.body)
    }
    return sb.toString()
}
