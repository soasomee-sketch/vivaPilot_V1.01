package com.iptvplayer.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Jedno ZDARZENIE ZGODY w rejestrze zgód Użytkownika (VivaPilot 1.12, punkt 7 promptu;
 * zastępuje dawny "trwały zapis zgód" ConsentRecord). Rejestr to lista takich zdarzeń,
 * dopisywanych wyłącznie NA KOŃCU (append-only) i przechowywanych w ZASZYFROWANYM pliku w
 * prywatnej pamięci appki — patrz ConsentVault.kt. Nigdzie nie są wysyłane; w appce nie ma
 * funkcji edycji ani cofnięcia zgody (cofnięcie = odinstalowanie).
 *
 * Nazwy pól w JSON są dokładnie takie, jak w specyfikacji: consent_event_id, timestamp_utc,
 * app_version, terms_version, privacy_policy_version, platform, store_transaction_id,
 * consents { age_18_declared, terms_accepted, external_features_acknowledged,
 * digital_content_waiver_accepted } — każda zgoda ma własny znacznik czasu. Dodatkowe,
 * pomocnicze pole event_type mówi, które to zdarzenie (1 = ekran powitalny, 2 = zakup).
 */
data class ConsentEvent(
    /** UUID v4. */
    val consentEventId: String,
    /** Typ zdarzenia: [TYPE_ONBOARDING], [TYPE_PURCHASE] albo [TYPE_PURCHASE_RESTORED]. */
    val eventType: String,
    /** Moment zdarzenia, UTC, ISO-8601 ("2026-10-20T09:15:31Z"). */
    val timestampUtc: String,
    val appVersion: String,
    /** Wersja Regulaminu z językiem, np. "1.12_PL". */
    val termsVersion: String,
    /** Wersja Polityki prywatności z językiem, np. "1.12_PL". */
    val privacyPolicyVersion: String,
    /** Platforma sprzedaży — [PLATFORM_GOOGLE_PLAY]. */
    val platform: String,
    /** Numer zamówienia ze sklepu — tylko zdarzenia zakupu (i tylko jeśli sklep go podał). */
    val storeTransactionId: String?,
    /** Złożone zgody z osobnymi znacznikami czasu (UTC, ISO-8601); null = tej zgody nie ma
     * w tym zdarzeniu. Zdarzenie 1: age_18_declared + terms_accepted. Zdarzenie 2 (zakup):
     * external_features_acknowledged + digital_content_waiver_accepted. */
    val age18DeclaredAtUtc: String? = null,
    val termsAcceptedAtUtc: String? = null,
    val externalFeaturesAcknowledgedAtUtc: String? = null,
    val digitalContentWaiverAcceptedAtUtc: String? = null,
) {
    fun toJson(): JSONObject {
        val consents = JSONObject()
        fun addConsent(name: String, at: String?) {
            if (at != null) consents.put(name, JSONObject().put("accepted", true).put("timestamp_utc", at))
        }
        addConsent("age_18_declared", age18DeclaredAtUtc)
        addConsent("terms_accepted", termsAcceptedAtUtc)
        addConsent("external_features_acknowledged", externalFeaturesAcknowledgedAtUtc)
        addConsent("digital_content_waiver_accepted", digitalContentWaiverAcceptedAtUtc)
        return JSONObject()
            .put("consent_event_id", consentEventId)
            .put("event_type", eventType)
            .put("timestamp_utc", timestampUtc)
            .put("app_version", appVersion)
            .put("terms_version", termsVersion)
            .put("privacy_policy_version", privacyPolicyVersion)
            .put("platform", platform)
            .put("store_transaction_id", storeTransactionId ?: JSONObject.NULL)
            .put("consents", consents)
    }

    companion object {
        const val TYPE_ONBOARDING = "ONBOARDING_ACCEPTANCE"
        const val TYPE_PURCHASE = "PURCHASE"
        /** Zakup wykryty na koncie Google bez przejścia przez ekran przed zakupem w tej instalacji
         * (np. po reinstalacji) — zdarzenie zawiera numer zamówienia, ale BEZ zgód zakupowych,
         * bo w tej instalacji Użytkownik ich nie składał. */
        const val TYPE_PURCHASE_RESTORED = "PURCHASE_RESTORED"
        const val PLATFORM_GOOGLE_PLAY = "GOOGLE_PLAY"

        fun fromJson(o: JSONObject): ConsentEvent {
            val c = o.optJSONObject("consents")
            fun at(name: String): String? = c?.optJSONObject(name)?.optString("timestamp_utc")?.takeIf { it.isNotEmpty() }
            return ConsentEvent(
                consentEventId = o.getString("consent_event_id"),
                eventType = o.optString("event_type", TYPE_ONBOARDING),
                timestampUtc = o.getString("timestamp_utc"),
                appVersion = o.optString("app_version"),
                termsVersion = o.optString("terms_version"),
                privacyPolicyVersion = o.optString("privacy_policy_version"),
                platform = o.optString("platform", PLATFORM_GOOGLE_PLAY),
                storeTransactionId = if (o.isNull("store_transaction_id")) null else o.optString("store_transaction_id").takeIf { it.isNotEmpty() },
                age18DeclaredAtUtc = at("age_18_declared"),
                termsAcceptedAtUtc = at("terms_accepted"),
                externalFeaturesAcknowledgedAtUtc = at("external_features_acknowledged"),
                digitalContentWaiverAcceptedAtUtc = at("digital_content_waiver_accepted"),
            )
        }

        fun listToJson(events: List<ConsentEvent>): JSONArray = JSONArray().also { arr -> events.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray): List<ConsentEvent> =
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }

        /** Moment w formacie zapisu zdarzeń: UTC, ISO-8601, bez ułamków sekund. */
        fun formatUtc(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))
    }
}
