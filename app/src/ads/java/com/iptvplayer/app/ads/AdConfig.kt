package com.iptvplayer.app.ads

/**
 * Identyfikatory jednostek reklamowych i inne stałe konfiguracyjne reklam — wyłącznie
 * wariant `ads` (sekcja 9 specyfikacji zgodności prawnej z 09.10: "Identyfikatory jednostek
 * reklamowych wpisz jako stałe konfiguracyjne, uzupełnię je później").
 *
 * WAŻNE DLA DAMIANA: poniższe wartości to OFICJALNE, TESTOWE identyfikatory Google (te same,
 * które Google podaje w swojej własnej dokumentacji AdMob jako bezpieczne do budowania i
 * testowania) — nigdy nie wyświetlają prawdziwych reklam i nie generują przychodu. Przed
 * publikacją w Google Play PODMIEŃ je na własne identyfikatory jednostek reklamowych z konta
 * AdMob (admob.google.com -> Aplikacje -> Twoja aplikacja -> Jednostki reklamowe -> utwórz
 * jednostkę typu "Baner" i osobną typu "Pełnoekranowa" (interstitial) -> skopiuj
 * "Identyfikator jednostki reklamowej" każdej z nich tutaj). Zobacz też
 * app/src/ads/AndroidManifest.xml — tam jest identyfikator APLIKACJI (inny niż identyfikator
 * jednostki reklamowej), też do podmiany.
 */
object AdConfig {
    /** Testowy identyfikator Google jednostki typu baner (Android). TODO: podmienić na własny. */
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111"

    /** Testowy identyfikator Google jednostki pełnoekranowej/interstitial (Android).
     * TODO: podmienić na własny. */
    const val INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712"

    /** Minimalny odstęp między dwiema reklamami pełnoekranowymi — żeby zmiana zakładki w
     * nawigacji nie pokazywała reklamy za KAŻDYM razem (to byłoby uciążliwe dla użytkownika
     * i niezgodne z zasadami AdMob dot. nadmiernej częstotliwości reklam). Spécyfikacja nie
     * narzuca konkretnej wartości — 90 sekund to rozsądny, bezpieczny punkt startowy; Damian
     * może tę wartość swobodnie zmienić. */
    const val MIN_INTERSTITIAL_INTERVAL_MILLIS: Long = 90_000L
}
