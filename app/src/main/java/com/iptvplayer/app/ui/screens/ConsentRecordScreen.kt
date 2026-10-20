package com.iptvplayer.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.ConsentEvent
import com.iptvplayer.app.data.LocalStore
import com.iptvplayer.app.i18n.LocalLang
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.i18n.trFor
import com.iptvplayer.app.ui.theme.Bg
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.theme.TextMain
import com.iptvplayer.app.ui.tv.tvFocusFrame

/** Nazwa pliku proponowana w systemowym oknie "Zapisz jako" przy "Pobierz potwierdzenie zgód". */
private const val CONSENT_EXPORT_FILE_NAME = "VivaPilot_potwierdzenie_zgod.txt"

/**
 * Ekran "Moje zgody" (Ustawienia › Informacje prawne › Moje zgody; VivaPilot 1.12, punkt 8
 * promptu) — TYLKO DO ODCZYTU: pokazuje zdarzenia zgody zapisane w zaszyfrowanym rejestrze
 * prywatnej pamięci appki (patrz ConsentEvent.kt i ConsentVault.kt): zdarzenie 1 (akceptacja
 * na ekranie powitalnym) i — po zakupie — zdarzenie 2 (zakup, z numerem transakcji i zgodami
 * zakupowymi, każda z własnym znacznikiem czasu).
 *
 * Celowo BEZ jakichkolwiek przycisków edycji, cofania lub usuwania zgody. Jedyne akcje to
 * "Pobierz potwierdzenie zgód" (zapis czytelnego pliku w miejscu wybranym przez Użytkownika w
 * systemowym oknie zapisu) i "Wstecz". Pod rekordem stoi wymagany tekst "Zapisu nie można
 * zmienić…" — cofnięcie zgody to odinstalowanie appki. Rejestr nigdzie nie jest wysyłany.
 */
@Composable
fun ConsentRecordScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // Czytane RAZ przy otwarciu ekranu — rejestr się nie zmienia, gdy ekran jest otwarty.
    val events: List<ConsentEvent> = remember { LocalStore(context).getConsentEvents() }
    val export = rememberConsentExportAction()

    Box(modifier = Modifier.fillMaxSize().background(Bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(tr("consents.title"), color = TextMain, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (events.isEmpty()) {
                Text(tr("consents.noRecord"), color = TextDim, style = MaterialTheme.typography.bodyMedium)
            } else {
                events.forEachIndexed { index, event ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    ConsentEventBlock(index + 1, event)
                }
                if (events.none { it.eventType != ConsentEvent.TYPE_ONBOARDING }) {
                    Spacer(Modifier.height(12.dp))
                    ConsentLine(tr("consents.noPurchase"), dim = true)
                }
            }

            Spacer(Modifier.height(20.dp))
            // Wymagany tekst pod rekordem (punkt 8 promptu).
            Text(tr("consents.readOnlyNote"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(tr("consents.localOnlyNote"), color = TextDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
            // Jedyny przycisk eksportu — zapis do pliku wybranego przez Użytkownika; nic nie jest wysyłane.
            Button(
                onClick = export,
                enabled = events.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("consents.exportButton"))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().tvFocusFrame(shape = RoundedCornerShape(8.dp)),
            ) {
                Text(tr("common.back"))
            }
        }
    }
}

@Composable
private fun ConsentEventBlock(number: Int, event: ConsentEvent) {
    val typeKey = when (event.eventType) {
        ConsentEvent.TYPE_ONBOARDING -> "consents.typeOnboarding"
        ConsentEvent.TYPE_PURCHASE -> "consents.typePurchase"
        else -> "consents.typePurchaseRestored"
    }
    Text(
        tr("consents.eventHeader", number.toString(), tr(typeKey)),
        color = TextMain,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    ConsentLine(tr("consents.timestampUtc", event.timestampUtc))
    ConsentLine(tr("consents.appVersion", event.appVersion))
    ConsentLine(tr("consents.termsVersion", event.termsVersion))
    ConsentLine(tr("consents.privacyVersion", event.privacyPolicyVersion))
    ConsentLine(tr("consents.platform", event.platform))
    event.storeTransactionId?.let { ConsentLine(tr("consents.purchaseOrderId", it)) }
    event.age18DeclaredAtUtc?.let { ConsentLine(tr("consents.consentAge18", it)) }
    event.termsAcceptedAtUtc?.let { ConsentLine(tr("consents.consentTerms", it)) }
    event.externalFeaturesAcknowledgedAtUtc?.let { ConsentLine(tr("consents.consentExternal", it)) }
    event.digitalContentWaiverAcceptedAtUtc?.let { ConsentLine(tr("consents.consentWaiver", it)) }
    ConsentLine(tr("consents.eventId", event.consentEventId), dim = true)
}

@Composable
private fun ConsentLine(text: String, dim: Boolean = false) {
    Text(
        text,
        color = if (dim) TextDim else TextMain,
        style = if (dim) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

/**
 * Akcja "Pobierz potwierdzenie zgód": otwiera systemowe okno "Zapisz jako" (SAF
 * ACTION_CREATE_DOCUMENT — Użytkownik sam wybiera miejsce zapisu, appka nie ma dostępu do
 * pamięci publicznej) i zapisuje tam czytelny plik z wszystkimi zdarzeniami zgody (patrz
 * [buildConsentExportContent]). Używana na ekranie "Moje zgody" i na ekranie potwierdzenia
 * zakupu. Nic nie jest nigdzie wysyłane — plik trafia tylko tam, gdzie wskaże Użytkownik.
 */
@Composable
fun rememberConsentExportAction(): () -> Unit {
    val context = LocalContext.current
    val lang by rememberUpdatedState(LocalLang.current)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val content = buildConsentExportContent(context, lang)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("openOutputStream zwrócił null")
            Toast.makeText(context, trFor(lang, "consents.exportSuccess"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, trFor(lang, "consents.exportError", e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
    return { launcher.launch(CONSENT_EXPORT_FILE_NAME) }
}

/**
 * Treść pliku "Pobierz potwierdzenie zgód": najpierw część czytelna dla człowieka (w bieżącym
 * języku appki — wszystkie zdarzenia, pole po polu), potem ten sam rejestr w formacie JSON
 * (dokładnie taki, jaki jest zapisany w appce, z nazwami pól ze specyfikacji).
 */
fun buildConsentExportContent(context: Context, lang: String): String {
    val events = LocalStore(context).getConsentEvents()
    val sb = StringBuilder()
    sb.appendLine(trFor(lang, "consents.exportFileTitle"))
    sb.appendLine(trFor(lang, "consents.exportAppLine", com.iptvplayer.app.BuildConfig.VERSION_NAME))
    sb.appendLine(trFor(lang, "consents.exportedAt", ConsentEvent.formatUtc(System.currentTimeMillis())))
    sb.appendLine(trFor(lang, "purchaseConfirmation.publisherRow"))
    sb.appendLine()
    sb.appendLine(trFor(lang, "consents.exportIntro"))
    events.forEachIndexed { index, event ->
        val typeKey = when (event.eventType) {
            ConsentEvent.TYPE_ONBOARDING -> "consents.typeOnboarding"
            ConsentEvent.TYPE_PURCHASE -> "consents.typePurchase"
            else -> "consents.typePurchaseRestored"
        }
        sb.appendLine()
        sb.appendLine(trFor(lang, "consents.eventHeader", (index + 1).toString(), trFor(lang, typeKey)))
        sb.appendLine(trFor(lang, "consents.eventId", event.consentEventId))
        sb.appendLine(trFor(lang, "consents.timestampUtc", event.timestampUtc))
        sb.appendLine(trFor(lang, "consents.appVersion", event.appVersion))
        sb.appendLine(trFor(lang, "consents.termsVersion", event.termsVersion))
        sb.appendLine(trFor(lang, "consents.privacyVersion", event.privacyPolicyVersion))
        sb.appendLine(trFor(lang, "consents.platform", event.platform))
        event.storeTransactionId?.let { sb.appendLine(trFor(lang, "consents.purchaseOrderId", it)) }
        event.age18DeclaredAtUtc?.let { sb.appendLine(trFor(lang, "consents.consentAge18", it)) }
        event.termsAcceptedAtUtc?.let { sb.appendLine(trFor(lang, "consents.consentTerms", it)) }
        event.externalFeaturesAcknowledgedAtUtc?.let { sb.appendLine(trFor(lang, "consents.consentExternal", it)) }
        event.digitalContentWaiverAcceptedAtUtc?.let { sb.appendLine(trFor(lang, "consents.consentWaiver", it)) }
    }
    sb.appendLine()
    sb.appendLine(trFor(lang, "consents.readOnlyNote"))
    sb.appendLine()
    sb.appendLine("----------------------------------------")
    sb.appendLine(trFor(lang, "consents.exportJsonHeader"))
    sb.appendLine(ConsentEvent.listToJson(events).toString(2))
    return sb.toString()
}
