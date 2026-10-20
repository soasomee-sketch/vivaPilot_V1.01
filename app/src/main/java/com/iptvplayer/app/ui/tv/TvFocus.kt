package com.iptvplayer.app.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.ui.theme.Accent
import com.iptvplayer.app.util.isTvDevice
import kotlinx.coroutines.launch

/**
 * Wspólne narzędzia do obsługi nawigacji pilotem (D-pad) na Android TV / Google TV —
 * zarówno klasycznym pilotem (strzałki + OK), jak i okrągłym pilotem bez klawiatury
 * numerycznej (np. pilot Google TV / Chromecast, pilot Xiaomi Google TV Box). Oba typy
 * pilotów wysyłają do systemu te same standardowe klawisze (KEYCODE_DPAD_UP/DOWN/LEFT/
 * RIGHT/CENTER, KEYCODE_BACK) niezależnie od tego, czy fizycznie mają osobne przyciski
 * strzałek czy okrągły touchpad — Android tłumaczy ruch po touchpadzie na te same
 * zdarzenia klawiszowe, więc nie trzeba niczego specjalnie rozróżniać na poziomie appki.
 *
 * `Modifier.clickable{}` / `.selectable{}` w Compose Foundation same w sobie już są
 * fokusowalne i reagują na OK/Enter/DPAD_CENTER — ten plik dokłada tylko to, czego im
 * brakuje do wygodnej obsługi pilotem: WYRAŹNE oznaczenie, który element ma aktualnie
 * fokus (obramowanie + lekkie powiększenie — domyślny podświetlający "ripple" jest na
 * telewizorze z odległości 3 metrów praktycznie niewidoczny), oraz automatyczne
 * doscrollowanie listy tak, żeby podświetlony element zawsze był widoczny.
 */

/**
 * Dokłada widoczne oznaczenie fokusu pod nawigację pilotem: kolorowe obramowanie +
 * delikatne powiększenie elementu, gdy zyskuje fokus klawiaturowy/D-pad. Nie zmienia
 * zachowania dotykowego — to czysto wizualna warstwa NAD istniejącym `.clickable{}` /
 * `.selectable{}`, więc w łańcuchu modifierów musi iść PRZED nim (bliżej `Modifier`,
 * tak samo jak np. `.clip()`/`.background()`), np.:
 *
 * ```
 * Modifier
 *     .clip(RoundedCornerShape(10.dp))
 *     .background(Surface)
 *     .tvFocusFrame(shape = RoundedCornerShape(10.dp))
 *     .clickable { ... }
 * ```
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvFocusFrame(
    shape: Shape = RoundedCornerShape(10.dp),
    focusColor: Color = Accent,
    // CELOWO domyślnie BEZ powiększenia (1f = brak zmiany rozmiaru) — zgłoszony błąd:
    // elementy zajmujące już całą szerokość ekranu (fillMaxWidth Rows — a takich jest w
    // całej appce sporo: wiersze w Ustawieniach, playlisty, wyniki wyszukiwania itd.) przy
    // powiększeniu WOKÓŁ ŚRODKA nieuchronnie wystają poza krawędzie ekranu po obu stronach,
    // niezależnie od tego, jak MAŁA byłaby wartość powiększenia — to nie kwestia dobrania
    // "bezpiecznej" liczby, tylko fundamentalna właściwość skalowania elementu, który już
    // wypełnia sobą dostępną szerokość. Samo obramowanie (2.5dp, jaskrawy kolor akcentu)
    // jest wystarczająco widoczne z typowej odległości oglądania telewizora bez potrzeby
    // dodatkowego "pop" efektu powiększenia. Elementy o STAŁEJ, węższej szerokości (kafelki
    // na Home, plakaty w gridach) mogą nadal jawnie podać scaleWhenFocused > 1f przy
    // wywołaniu, tam gdzie jest miejsce, żeby bezpiecznie urosnąć.
    scaleWhenFocused: Float = 1f,
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) scaleWhenFocused else 1f,
        animationSpec = tween(120),
        label = "tvFocusScale",
    )
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { state ->
            val focused = state.isFocused || state.hasFocus
            if (focused != isFocused) {
                isFocused = focused
                if (focused) {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                }
            }
        }
        .scale(scale)
        .then(if (isFocused) Modifier.border(2.5.dp, focusColor, shape) else Modifier)
}

/**
 * Wyłącza dany element z nawigacji pilotem/klawiaturą (D-pad directional focus search),
 * zachowując pełną obsługę dotyku/myszy bez zmian. Do użycia dla drugorzędnych "mini-
 * przycisków" nałożonych na kartę, która sama jest już osobnym przystankiem fokusu
 * (np. gwiazdka ulubionych / ikona pobierania na plakacie filmu) — bez tego pilot
 * próbowałby zatrzymywać się osobno na każdej takiej mini-ikonie leżącej wewnątrz już
 * klikalnej karty, co przy nawigacji samymi strzałkami jest mylące i nieprzewidywalne.
 * Te akcje pozostają dostępne dotykiem tak jak wcześniej; z pilota część kart udostępnia
 * te same akcje przez długie przytrzymanie OK (patrz `combinedClickable` w PosterCard).
 *
 * Musi iść PRZED elementem fokusowalnym w łańcuchu (tak jak w dokumentacji Compose):
 * `Modifier.notDpadFocusable().clickable { ... }`.
 */
fun Modifier.notDpadFocusable(): Modifier = this.focusProperties { canFocus = false }

/**
 * Prosty helper: żąda fokusu na danym elemencie przy pierwszym wejściu do kompozycji —
 * używany tam, gdzie ekran powinien mieć od razu jakiś "punkt startowy" dla pilota
 * (np. pierwsza zakładka dolnej nawigacji, przycisk play/pauza w odtwarzaczu).
 */
@Composable
fun rememberInitialFocusRequester(): FocusRequester = remember { FocusRequester() }

/** Skrót do `Modifier.focusRequester(requester)`, dla czytelności w miejscach wywołania. */
fun Modifier.tvInitialFocus(requester: FocusRequester): Modifier = this.focusRequester(requester)

/**
 * Na Android TV: wymaga JAWNEGO naciśnięcia OK, żeby otworzyć klawiaturę ekranową — samo
 * przejście fokusem pilotem PRZEZ pole tekstowe (np. przy przewijaniu formularza strzałką w
 * dół) domyślnie od razu wyskakiwało z klawiaturą ekranową, co przy formularzu z kilkoma
 * polami pod rząd (patrz PlaylistFormDialog) oznaczało klawiaturę pojawiającą się i znikającą
 * przy każdym pojedynczym naciśnięciu strzałki, zanim użytkownik zdążył cokolwiek wpisać.
 *
 * Na telefonie/tablecie (dotyk) zachowanie zostaje BEZ ZMIAN — to WYŁĄCZNIE telewizyjna
 * poprawka (sprawdzana przez Context.isTvDevice()), bo na dotyku "stuknięcie w pole = od razu
 * chcę pisać" jest naturalnym, oczekiwanym zachowaniem, którego nie ma sensu zmieniać.
 *
 * Musi iść PO `Modifier.focusRequester(...)`, jeśli taki jest w łańcuchu — tak jak inne
 * modifiery reagujące na zmianę fokusu.
 */
@Composable
fun Modifier.tvKeyboardOnDemand(): Modifier {
    val context = LocalContext.current
    if (!context.isTvDevice()) return this
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasFocus by remember { mutableStateOf(false) }
    // Śledzone OSOBNO od samego fokusu — bez tego drugie i kolejne naciśnięcie OK/Enter
    // (np. żeby ZATWIERDZIĆ już wpisany tekst, kiedy klawiatura jest już otwarta) też
    // zostawałoby przechwycone przez ten modifier zamiast trafić normalnie do klawiatury/pola
    // — użytkownik nigdy nie mógłby "zamknąć" klawiatury swoim własnym Enterem. Reset przy
    // KAŻDEJ zmianie fokusu (zarówno zyskanie, jak i utrata), żeby przy powrocie na to samo
    // pole znowu wymagało jawnego naciśnięcia, tak jak za pierwszym razem.
    var keyboardRequested by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { state ->
            hasFocus = state.isFocused
            keyboardRequested = false
            // Samo ZYSKANIE fokusu (przejście pilotem) ma NIE otwierać klawiatury — dopiero
            // jawne naciśnięcie OK/Enter niżej. Utrata fokusu też ją chowa, żeby nie
            // zostawała otwarta po przejściu dalej strzałkami.
            keyboardController?.hide()
        }
        .onKeyEvent { keyEvent ->
            if (hasFocus && !keyboardRequested &&
                keyEvent.type == KeyEventType.KeyDown &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
            ) {
                keyboardController?.show()
                keyboardRequested = true
                true
            } else {
                false
            }
        }
}
