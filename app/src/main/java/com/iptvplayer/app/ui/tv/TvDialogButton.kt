package com.iptvplayer.app.ui.tv

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Przycisk do okien dialogowych (AlertDialog) z WYRAŹNIE widocznym zaznaczeniem pod pilota.
 *
 * Zgłoszenie: w oknie "Czy na pewno usunąć?" naciśnięcie strzałki w prawo i OK zamykało
 * okno BEZ usuwania. Przyczyna: zwykłe TextButton nie pokazują na telewizorze, który z nich
 * ma fokus, a po otwarciu okna fokus nie jest ustawiony na żadnym — pierwsza strzałka
 * (niezależnie od kierunku) ląduje wtedy na PIERWSZYM przycisku w rzędzie, czyli "Anuluj".
 * Użytkownik był przekonany, że zaznaczył "Usuń" (bo jest po prawej), a faktycznie
 * zatwierdzał "Anuluj". Tu: obramowanie fokusu (to samo co w całej appce) + opcjonalny
 * fokus startowy, więc zawsze widać, co zostanie zatwierdzone.
 */
@Composable
fun TvDialogButton(
    text: String,
    onClick: () -> Unit,
    color: Color = Color.Unspecified,
    initialFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    if (initialFocus) {
        LaunchedEffect(Unit) {
            // Okno dialogowe to osobne okno systemowe — potrzebuje chwili na pierwszą
            // klatkę, zanim da się w nim ustawić fokus. runCatching: jeśli z jakiegoś powodu
            // się nie uda, przycisk po prostu działa jak zwykły, bez startowego fokusu.
            kotlinx.coroutines.delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .tvFocusFrame(shape = RoundedCornerShape(20.dp)),
    ) {
        Text(text, color = color)
    }
}
