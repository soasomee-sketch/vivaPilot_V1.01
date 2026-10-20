package com.iptvplayer.app.data

import android.content.Context

/**
 * Adres strony internetowej z dokumentami prawnymi Aplikacji — wymagany przez Google Play
 * (sekcja "App content" w Play Console potrzebuje żywego linku do Polityki Prywatności,
 * patrz SettingsScreen.kt -> sekcja "Informacje prawne"). Damian uzupełni docelowy adres
 * po opublikowaniu strony; do tego czasu stała zostaje jako placeholder — appka działa
 * poprawnie również bez tego (pełne teksty są i tak wbudowane w aplikację, patrz niżej),
 * to tylko dodatkowy, wymagany przez Google, punkt odniesienia w sieci.
 */
const val LEGAL_WEBSITE_URL = "https://vivapilot.example.com/legal"

/** Który z dwóch wbudowanych dokumentów prawnych — patrz LegalDocuments.load(). */
enum class LegalDocType(val assetPath: String, val titleKey: String, val legalDocVersionKey: String) {
    REGULAMIN("legal/regulamin.txt", "legal.regulaminTitle", "regulamin"),
    POLITYKA("legal/polityka_prywatnosci.txt", "legal.politykaTitle", "polityka"),
}

/** Wynik wczytania jednego z plików w assets/legal/ — patrz LegalDocuments.load(). */
data class LegalDocument(
    val version: String,
    val effectiveDate: String,
    val changelogSummary: String,
    val body: String,
)

/**
 * Wczytuje wbudowane pełne teksty Regulaminu i Polityki prywatności z plików
 * tekstowych w `app/src/main/assets/legal/` (sekcja 7 specyfikacji zgodności prawnej z 09.10:
 * "pełne teksty wbudowane w aplikację (pliki w assets, przewijane, czytelne na
 * telewizorze)"). Używane przez: OnboardingScreen.kt (linki "Regulamin"/"Polityka
 * prywatności" przy pierwszym uruchomieniu), SettingsScreen.kt (Ustawienia -> Informacje
 * prawne) i mechanizm powiadomienia o zmianie wersji dokumentu (sekcja 10 — patrz
 * LocalStore.getAcknowledgedLegalDocVersion/setAcknowledgedLegalDocVersion).
 *
 * TREŚĆ PLIKÓW TO NA RAZIE ZAŚLEPKA — Damian dostarczy docelowe, zweryfikowane prawnie
 * teksty jako gotowe pliki; wtedy wystarczy podmienić zawartość plików w assets/legal/,
 * bez zmiany kodu (byle zachować format nagłówka opisany niżej).
 *
 * Format pliku (3 linie nagłówka, separator, treść):
 *   WERSJA: <numer, np. 1.12 — ZAWSZE taki sam jak versionName aplikacji, patrz app/build.gradle.kts>
 *   DATA_WEJSCIA_W_ZYCIE: <YYYY-MM-DD>
 *   ZMIANY: <krótki opis zmian w tej wersji — pokazywany w komunikacie o zmianie, sekcja 10>
 *   ---
 *   <pełna treść dokumentu, może być wieloliniowa>
 *
 * Numer wersji i data CELOWO pochodzą z pliku, nie są hardkodowane w Kotlinie (sekcja 10:
 * "Treść „Najważniejsze zmiany” i data mają pochodzić z pliku z dokumentem, żeby nie trzeba
 * było zmieniać kodu") — podmiana pliku w nowej wersji appki z wyższym numerem WERSJA
 * automatycznie uruchamia komunikat o zmianie, bez dotykania kodu Kotlin.
 */
object LegalDocuments {
    private const val HEADER_SEPARATOR = "\n---\n"

    fun load(context: Context, type: LegalDocType): LegalDocument {
        val raw = context.assets.open(type.assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val separatorIndex = raw.indexOf(HEADER_SEPARATOR)
        val header = if (separatorIndex >= 0) raw.substring(0, separatorIndex) else ""
        val body = if (separatorIndex >= 0) {
            raw.substring(separatorIndex + HEADER_SEPARATOR.length).trimStart('\n')
        } else {
            raw
        }
        var version = com.iptvplayer.app.BuildConfig.VERSION_NAME
        var effectiveDate = ""
        var changelog = ""
        header.lineSequence().forEach { line ->
            when {
                line.startsWith("WERSJA:") -> version = line.removePrefix("WERSJA:").trim()
                line.startsWith("DATA_WEJSCIA_W_ZYCIE:") -> effectiveDate = line.removePrefix("DATA_WEJSCIA_W_ZYCIE:").trim()
                line.startsWith("ZMIANY:") -> changelog = line.removePrefix("ZMIANY:").trim()
            }
        }
        return LegalDocument(version = version, effectiveDate = effectiveDate, changelogSummary = changelog, body = body)
    }
}
