package com.iptvplayer.app.util

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * Rozpoznaje, czy appka aktualnie działa na Android TV / Google TV (a nie na telefonie czy
 * tablecie podłączonym np. przez Chromecast/HDMI). Używane do zachowań, które mają sens
 * TYLKO na jednym typie urządzenia:
 * - orientacja odtwarzacza (PlayerActivity) — telewizor nigdy się fizycznie nie obraca,
 *   więc tam zostaje sztywno landscape; telefon/tablet dostaje pełną orientację pionową
 *   i poziomą, podążającą za trzymaniem urządzenia,
 * - Picture-in-Picture — ma sens głównie na telefonie/tablecie (zminimalizowanie odtwarzacza
 *   do małego okienka i dalsze korzystanie z appki w tle); na telewizorze przycisk PiP i tak
 *   pozostaje ukryty niezależnie od tego rozpoznania, bo opiera się dodatkowo na tym, czy
 *   system faktycznie zgłasza `FEATURE_PICTURE_IN_PICTURE` (patrz PlayerActivity).
 *
 * `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` to oficjalny, zalecany przez
 * Google sposób odróżnienia Android TV od telefonu/tabletu — dokładnie to samo pole, po
 * którym system decyduje, czy pokazać kanał Leanback na urządzeniu (patrz AndroidManifest.xml,
 * `android.software.leanback`).
 */
fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/**
 * Sprawdza DOKŁADNIE to, czego wymaga sekcja 9 specyfikacji zgodności prawnej z 09.10 przy
 * bramkowaniu reklam: `PackageManager.FEATURE_LEANBACK` ("SDK AdMob nie obsługuje Android TV").
 * Celowo OSOBNA od [isTvDevice] wyżej (który sprawdza `UiModeManager` — tryb UI w czasie
 * działania) — to tu sprawdza zadeklarowaną CECHĘ urządzenia/systemu (ten sam sygnał, po
 * którym Google Play i sam system rozpoznają urządzenia telewizyjne/Leanback, patrz też
 * `android.software.leanback` w AndroidManifest.xml). W praktyce oba zwracają to samo na
 * każdym realnym urządzeniu Android TV/Google TV — rozdzielenie odzwierciedla wprost
 * literalny wymóg specyfikacji, nie zmienia zachowania aplikacji.
 */
fun Context.isLeanbackDevice(): Boolean =
    packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
