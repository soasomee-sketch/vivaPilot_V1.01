package com.iptvplayer.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.ui.theme.Border
import com.iptvplayer.app.ui.theme.Bg
import com.iptvplayer.app.ui.theme.TextDim
import com.iptvplayer.app.ui.tv.tvFocusFrame

/**
 * Odpowiednik <nav> z iptv-web-player.html (data-tab="home|live|movies|series|…|settings") —
 * te same etykiety/kolejność, z jedną zmianą względem wersji web: zakładka Playlists została
 * przeniesiona do wnętrza Settings (patrz SettingsScreen.onManagePlaylists), a jej miejsce na
 * dolnym pasku zajęła zakładka Folder — przeglądanie i odtwarzanie własnych filmów zapisanych
 * lokalnie na urządzeniu (patrz LocalFolderScreen).
 */
// `labelKey` odsyła do tabeli tłumaczeń (patrz i18n/Strings.kt) zamiast trzymać gotowy
// tekst na sztywno — w miejscu użycia zawsze woła się tr(tab.labelKey), żeby etykieta
// odświeżała się razem ze zmianą języka w Ustawieniach.
enum class Tab(val route: String, val labelKey: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("home", "tab.home", Icons.Outlined.Home),
    MOVIES("movies", "tab.movies", Icons.Outlined.Movie),
    SERIES("series", "tab.series", Icons.Outlined.Tv),
    LIVE("live", "tab.live", Icons.Outlined.LiveTv),
    FOLDER("folder", "tab.folder", Icons.Outlined.Folder),
    SETTINGS("settings", "tab.settings", Icons.Outlined.Settings),
}

@Composable
fun IptvBottomNav(
    current: Tab,
    onSelect: (Tab) -> Unit,
    // Domyślnie tworzy WŁASNY FocusRequester (jak dotychczas) — ale wywołujący (RootScreen w
    // MainActivity.kt) może podać WŁASNY, żeby móc jawnie zażądać fokusu na tym pasku Z
    // ZEWNĄTRZ, w dowolnym momencie (nie tylko raz, przy pierwszym wejściu). Patrz przycisk
    // Wstecz w RootScreen: bez tego jedynym sposobem dotarcia pilotem do dolnego paska było
    // przewinięcie strzałkami przez CAŁĄ zawartość ekranu od góry do dołu — przy dłuższych
    // listach (dziesiątki kanałów/filmów) realnie uciążliwe.
    focusRequester: FocusRequester = remember { FocusRequester() },
    // Jawny cel zejścia pilotem W DÓŁ z AKTUALNIE WYBRANEJ zakładki — RootScreen podaje tu
    // np. pole wyszukiwania danej zakładki (zawsze na górze ekranu, niezależnie od
    // przewinięcia listy niżej). Bez tego strzałka GÓRA (jedyna dotychczasowa droga z paska
    // nawigacji w treść) przez domyślne, geometryczne wyszukiwanie kierunkowe Compose
    // czasem lądowała gdzieś w środku przewiniętej listy, zamiast na samej górze
    // (zgłoszenie) — DÓŁ z paska nawigacji to teraz przewidywalna, dodatkowa droga "zawsze
    // na górę". Null (domyślnie) = ta zakładka nie ma jeszcze takiego celu podłączonego,
    // DÓŁ z paska nawigacji po prostu nic nie robi dla niej (jak dotychczas).
    downFocusRequester: FocusRequester? = null,
) {
    // Punkt startowy dla pilota: przy pierwszym wejściu w ten ekran (np. po uruchomieniu
    // appki albo po powrocie z ekranu szczegółów filmu/serialu) fokus musi na czymś
    // wylądować — inaczej pierwsze naciśnięcie strzałki na pilocie nic by nie robiło.
    // Ląduje na aktualnie wybranej zakładce, żeby strzałka w górę prowadziła w treść.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .border(topWidth = 1.dp, color = Border)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val label = com.iptvplayer.app.i18n.tr(tab.labelKey)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .then(if (selected) Modifier.focusRequester(focusRequester) else Modifier)
                    .then(
                        if (selected && downFocusRequester != null) {
                            Modifier.focusProperties { down = downFocusRequester }
                        } else {
                            Modifier
                        },
                    )
                    .tvFocusFrame(shape = RoundedCornerShape(10.dp))
                    .selectable(selected = selected, onClick = { onSelect(tab) })
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = label,
                    tint = if (selected) Accent else TextDim,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    color = if (selected) Accent else TextDim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// Mały helper, bo Modifier.border ze zwykłego API rysuje ramkę ze wszystkich stron —
// tu chcemy tylko górną krawędź, tak jak `nav { border-top: 1px solid var(--border) }` w CSS.
private fun Modifier.border(topWidth: Dp, color: Color): Modifier =
    this.drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = topWidth.toPx(),
        )
    }
