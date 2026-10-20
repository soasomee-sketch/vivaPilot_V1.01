package com.iptvplayer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta 1:1 skopiowana z CSS custom properties w iptv-web-player.html
 * (:root { --bg, --surface, --surface-2, --surface-3, --accent, --accent-2, --danger, --text, --text-dim, --border })
 * Zachowujemy te same nazwy semantyczne, żeby UI wyglądał identycznie jak w wersji webowej.
 */
val Bg = Color(0xFF0B0B0F)
val Surface = Color(0xFF15151C)
val Surface2 = Color(0xFF1E1E28)
val Surface3 = Color(0xFF26262F)
val Accent = Color(0xFF4F9DFF)
val Accent2 = Color(0xFFFFCF4F)      // złoty akcent (ulubione, VLC badge)
val Danger = Color(0xFFFF6868)
val Success = Color(0xFF4CD97B)      // zielony — status "wszystkie playlisty aktywne"
val Warning = Color(0xFFFFC24C)      // żółty — status "część playlist ma problem"
val TextMain = Color(0xFFF2F2F7)
val TextDim = Color(0xFF9A9AA8)
val Border = Color(0xFF26262F)

val Accent2OnColor = Color(0xFF1A1400) // tekst na tle --accent-2 (jak w CSS .chip.fav-chip.active)
