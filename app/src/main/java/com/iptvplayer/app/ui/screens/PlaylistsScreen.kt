package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.ui.tv.tvKeyboardOnDemand
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.iptvplayer.app.data.PlaylistSource
import com.iptvplayer.app.data.PlaylistStatus
import com.iptvplayer.app.data.PlaylistType
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*

/** Odrzuca zmianę, jeśli tekst nagle wydłużył się o więcej niż jeden znak naraz — tak
 * zachowuje się wklejenie (ze schowka, przeciągnięcie, autouzupełnienie), podczas gdy
 * normalne pisanie (nawet szybkie) dodaje po jednym znaku na raz. Celowo utrudnia
 * wklejanie adresu serwera/loginu/hasła do playlisty — wymusza ręczne przepisanie za
 * każdym razem, co jest dodatkowym (niedoskonałym, ale tanim) utrudnieniem przy próbie
 * ominięcia limitu próbnego przez odinstalowanie i ponowną instalację appki. Nie blokuje
 * normalnego usuwania/zaznaczania i zastępowania fragmentu tekstu pojedynczym znakiem —
 * to może SKRÓCIĆ długość, więc nie jest tu w ogóle sprawdzane. */
private fun blockPaste(old: String, new: String): String =
    if (new.length > old.length + 1) old else new

/** Odpowiednik zakładki Playlists: lista dodanych źródeł + formularz dodawania/edycji M3U lub Xtream. */
@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistSource>,
    // Status ostatniej próby połączenia per playlista — patrz AppViewModel.playlistStatuses.
    // Ten sam wskaźnik zielony/żółty/czerwony co w headerze, tu jednak rozbity na pojedyncze
    // playlisty, więc widać KTÓRA konkretnie nie odpowiada, wraz z treścią błędu.
    statuses: Map<String, PlaylistStatus> = emptyMap(),
    onAddM3U: (name: String, url: String, userAgent: String?) -> Unit,
    onAddXtream: (name: String, host: String, user: String, pass: String, epg: String?, userAgent: String?) -> Unit,
    // Edycja istniejącej playlisty — te same pola co przy dodawaniu, ale nadpisują dane
    // playlisty o danym id zamiast tworzyć nową (patrz AppViewModel.updateM3UPlaylist/updateXtreamPlaylist).
    onEditM3U: (id: String, name: String, url: String, userAgent: String?) -> Unit = { _, _, _, _ -> },
    onEditXtream: (id: String, name: String, host: String, user: String, pass: String, epg: String?, userAgent: String?) -> Unit =
        { _, _, _, _, _, _, _ -> },
    onRemove: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // Licznik otwarć okna dodawania — każde otwarcie dostaje ŚWIEŻY formularz (patrz
    // key(addDialogNonce) niżej), więc w polach nigdy nie zostają dane z poprzedniej próby.
    var addDialogNonce by remember { mutableStateOf(0) }
    // Playlista aktualnie otwarta do edycji (null = dialog dodawania, nie edycji)
    var editingPlaylist by remember { mutableStateOf<PlaylistSource?>(null) }
    // Playlista czekająca na potwierdzenie usunięcia — wcześniej ikona kosza usuwała od
    // razu, bez pytania (łatwo o przypadkowe usunięcie pilotem).
    var pendingDelete by remember { mutableStateOf<PlaylistSource?>(null) }

    // Przycisk "Dodaj playlistę" jako PIERWSZY element ekranu zamiast pływającego przycisku
    // w prawym dolnym rogu. Zgłoszenie: "przy dodawaniu nowej listy od razu kopiuje dane z
    // pierwszej listy" — na pilocie pływający przycisk był trudno osiągalny, a OK na
    // wierszu istniejącej playlisty otwiera jej EDYCJĘ (z wypełnionymi danymi), co wyglądało
    // jak "nowa lista z cudzymi danymi". Teraz dodawanie ma własny, wyraźny przycisk na górze.
    val openAddDialog = { addDialogNonce++; showAddDialog = true }

    Box(Modifier.fillMaxSize().background(Bg)) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            item(key = "add_playlist_button") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent.copy(alpha = 0.18f))
                        .tvFocusFrame(shape = RoundedCornerShape(12.dp))
                        .clickable { openAddDialog() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("playlists.addDesc"), color = TextMain, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (playlists.isEmpty()) {
                item(key = "empty") {
                    Text(tr("playlists.empty"), color = TextDim, modifier = Modifier.padding(top = 24.dp))
                }
            }
                items(playlists, key = { it.id }) { p ->
                    val status = statuses[p.id]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Surface)
                            .tvFocusFrame(shape = RoundedCornerShape(12.dp))
                            // Cały wiersz też otwiera edycję — nie tylko ikona ołówka —
                            // żeby na pilocie (bez celowania w mały przycisk) wystarczyło OK.
                            .clickable { editingPlaylist = p }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Kropka statusu: zielona = ostatnie sprawdzenie OK, czerwona = błąd,
                        // szara = jeszcze nie sprawdzano (np. dopiero co dodana playlista).
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (status?.ok) {
                                        true -> Success
                                        false -> Danger
                                        null -> TextDim
                                    }
                                ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = TextMain, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (p.type == PlaylistType.XTREAM) "Xtream Codes" else "M3U/M3U8",
                                color = TextDim, style = MaterialTheme.typography.bodySmall,
                            )
                            // Data ważności konta — tylko Xtream (M3U nie ma pojęcia "konta") i
                            // tylko gdy panel faktycznie zwrócił te dane (patrz
                            // XtreamClient.fetchAccountInfo, celowo nie rzuca wyjątku, więc czasem
                            // po prostu nic tu nie będzie — to nie błąd, tylko brak wsparcia u
                            // dostawcy). expiresAtMillis == null przy accountStatus != null =
                            // konto bezterminowe, nie "brak informacji".
                            if (p.type == PlaylistType.XTREAM && status?.ok == true) {
                                ExpiryLabel(expiresAtMillis = status.expiresAtMillis, accountStatus = status.accountStatus)
                            }
                            if (status?.ok == false) {
                                Text(
                                    status.errorMessage ?: tr("root.connection.noResponse"),
                                    color = Danger, style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        IconButton(
                            onClick = { editingPlaylist = p },
                            modifier = Modifier.tvFocusFrame(shape = CircleShape),
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = tr("playlists.editDesc"), tint = TextDim)
                        }
                        IconButton(
                            onClick = { pendingDelete = p },
                            modifier = Modifier.tvFocusFrame(shape = CircleShape),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = tr("common.delete"), tint = Danger)
                        }
                    }
                }
        }
    }

    if (showAddDialog) {
        key(addDialogNonce) {
            PlaylistFormDialog(
                existing = null,
                onDismiss = { showAddDialog = false },
                onSaveM3U = { _, n, u, ua -> onAddM3U(n, u, ua); showAddDialog = false },
                onSaveXtream = { _, n, h, us, pa, e, ua -> onAddXtream(n, h, us, pa, e, ua); showAddDialog = false },
            )
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("playlists.deleteConfirmTitle", p.name)) },
            text = { Text(tr("playlists.deleteConfirmText")) },
            confirmButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.delete"),
                    color = Danger,
                    onClick = { pendingDelete = null; onRemove(p.id) },
                )
            },
            dismissButton = {
                com.iptvplayer.app.ui.tv.TvDialogButton(
                    text = tr("common.cancel"),
                    initialFocus = true,
                    onClick = { pendingDelete = null },
                )
            },
        )
    }

    editingPlaylist?.let { p ->
        // key(p.id) — przełączenie edycji z jednej playlisty na drugą zawsze daje formularz
        // z danymi TEJ playlisty, a nie resztki stanu poprzednio edytowanej.
        key(p.id) {
        PlaylistFormDialog(
            existing = p,
            onDismiss = { editingPlaylist = null },
            onSaveM3U = { id, n, u, ua -> onEditM3U(id, n, u, ua); editingPlaylist = null },
            onSaveXtream = { id, n, h, us, pa, e, ua -> onEditXtream(id, n, h, us, pa, e, ua); editingPlaylist = null },
        )
        }
    }
}

/**
 * Formularz playlisty — jeden i ten sam dialog do dodawania NOWEJ playlisty (existing = null)
 * i do edycji ISTNIEJĄCEJ (existing != null, pola od razu wypełnione bieżącymi danymi, żeby
 * dało się je w ogóle zobaczyć/poprawić — wcześniej po dodaniu nie było ich gdzie podejrzeć).
 * Callbacki dostają id ("" dla nowej playlisty, żeby AppViewModel wiedział, czy tworzyć czy
 * nadpisywać — patrz onAddM3U/onEditM3U w PlaylistsScreen).
 */
@Composable
private fun PlaylistFormDialog(
    existing: PlaylistSource?,
    onDismiss: () -> Unit,
    onSaveM3U: (id: String, name: String, url: String, userAgent: String?) -> Unit,
    onSaveXtream: (id: String, name: String, host: String, user: String, pass: String, epg: String?, userAgent: String?) -> Unit,
) {
    var isXtream by remember { mutableStateOf(existing?.type != PlaylistType.M3U) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.url.orEmpty()) }
    var user by remember { mutableStateOf(existing?.username.orEmpty()) }
    var pass by remember { mutableStateOf(existing?.password.orEmpty()) }
    var epg by remember { mutableStateOf(existing?.epgUrl.orEmpty()) }
    // Domyślny User-Agent podszywający się pod VLC — wiele paneli Xtream Codes blokuje albo
    // ogranicza zapytania z domyślnym nagłówkiem biblioteki HTTP (np. "okhttp/4.12.0"), bo to
    // od razu zdradza, że to nie prawdziwy odtwarzacz, tylko coś do automatycznego pobierania
    // treści — VLC to najpowszechniej "zaufany" klient wśród paneli IPTV, stąd ten wybór jako
    // bezpieczny punkt startowy. Użytkownik może to nadpisać, jeśli jego dostawca wymaga
    // czegoś innego. Ten sam wzorzec auto-uzupełniania + śledzenia ręcznej edycji co przy
    // adresie EPG niżej.
    var userAgent by remember { mutableStateOf(existing?.userAgent.orEmpty()) }
    var userAgentManuallyEdited by remember { mutableStateOf(existing != null) }
    LaunchedEffect(Unit) {
        if (!userAgentManuallyEdited && userAgent.isBlank()) {
            userAgent = "VLC/3.0.18 LibVLC/3.0.18"
        }
    }
    // Auto-podpowiedź adresu EPG dla NOWEJ playlisty Xtream — większość paneli Xtream Codes
    // udostępnia XMLTV pod dokładnie tym samym, przewidywalnym adresem (to część specyfikacji
    // Xtream Codes, nie coś specyficznego dla jednego dostawcy). Bez tego pole zostawało puste
    // i EPG nigdy się nie włączało, dopóki ktoś sam nie odkrył i nie wpisał tego adresu ręcznie.
    // `epgManuallyEdited` pilnuje, żeby auto-uzupełnianie NIE nadpisywało własnoręcznie
    // wpisanego adresu (np. gdy panel dostawcy jednak używa innego wzorca) ani nie ruszało
    // już zapisanej wartości przy EDYCJI istniejącej playlisty (tam zostaje to, co jest).
    var epgManuallyEdited by remember { mutableStateOf(existing != null) }
    LaunchedEffect(isXtream, url, user, pass) {
        if (isXtream && !epgManuallyEdited && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            epg = "${url.trimEnd('/')}/xmltv.php?username=$user&password=$pass"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) tr("playlists.editTitle") else tr("playlists.addTitle")) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Typ playlisty da się zmienić też przy edycji — gdyby ktoś np. pomylił
                    // format przy dodawaniu, nie trzeba usuwać wpisu i zaczynać od nowa.
                    TypeToggle("Xtream Codes", isXtream) { isXtream = true }
                    TypeToggle("M3U / M3U8", !isXtream) { isXtream = false }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("playlists.nameLabel")) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand())
                Spacer(Modifier.height(8.dp))
                if (isXtream) {
                    OutlinedTextField(value = url, onValueChange = { url = blockPaste(url, it) }, label = { Text(tr("playlists.serverAddressLabel")) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = user, onValueChange = { user = blockPaste(user, it) }, label = { Text(tr("playlists.usernameLabel")) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pass, onValueChange = { pass = blockPaste(pass, it) }, label = { Text(tr("playlists.passwordLabel")) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = epg,
                        onValueChange = { epg = it; epgManuallyEdited = true },
                        label = { Text(tr("playlists.epgUrlLabel")) },
                        // Podpowiedź WYŁĄCZNIE, dopóki pole trzyma auto-uzupełnioną wartość —
                        // znika, gdy użytkownik zacznie edytować, żeby nie zaśmiecać formularza
                        // po tym, jak i tak już wie, skąd wzięła się ta wartość.
                        supportingText = if (!epgManuallyEdited && epg.isNotBlank()) {
                            { Text(tr("playlists.epgUrlAutofilled"), style = MaterialTheme.typography.labelSmall) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
                    )
                } else {
                    OutlinedTextField(value = url, onValueChange = { url = blockPaste(url, it) }, label = { Text(tr("playlists.m3uUrlLabel")) }, singleLine = true, modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand())
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it; userAgentManuallyEdited = true },
                    label = { Text(tr("playlists.userAgentLabel")) },
                    // Ta sama zasada co przy EPG — podpowiedź znika, gdy użytkownik zacznie
                    // ręcznie edytować, bo wtedy już wie, skąd wzięła się ta wartość.
                    supportingText = if (!userAgentManuallyEdited && userAgent.isNotBlank()) {
                        { Text(tr("playlists.userAgentAutofilled"), style = MaterialTheme.typography.labelSmall) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().tvKeyboardOnDemand(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = existing?.id ?: ""
                val ua = userAgent.ifBlank { null }
                if (isXtream) onSaveXtream(id, name.ifBlank { "Xtream" }, url, user, pass, epg.ifBlank { null }, ua)
                else onSaveM3U(id, name.ifBlank { "M3U" }, url, ua)
            }) { Text(if (existing != null) tr("common.save") else tr("common.add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}

@Composable
private fun TypeToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) Accent else Surface)
            .tvFocusFrame(shape = RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, color = if (selected) androidx.compose.ui.graphics.Color.White else TextDim, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Data ważności konta Xtream pod nazwą playlisty. Trzy stany:
 * - brak jakichkolwiek danych (panel nie wspiera / fetchAccountInfo zawiodło) -> nic nie
 *   pokazujemy, to nie błąd, tylko brak informacji u źródła,
 * - expiresAtMillis == null, ale accountStatus != null -> konto BEZTERMINOWE (Xtream
 *   zwraca wtedy exp_date="0"), pokazujemy to wprost, żeby nie wyglądało na brakujące dane,
 * - expiresAtMillis != null -> konkretna data, kolorowana wg pilności (wygasłe/wygasa
 *   w ciągu 7 dni/normalnie) tak samo jak reszta wskaźników statusu w tej appce.
 */
@Composable
private fun ExpiryLabel(expiresAtMillis: Long?, accountStatus: String?) {
    if (expiresAtMillis == null && accountStatus == null) return
    val lang = com.iptvplayer.app.i18n.LocalLang.current
    if (expiresAtMillis == null) {
        Text(tr("playlists.expiry.unlimited"), color = TextDim, style = MaterialTheme.typography.bodySmall)
        return
    }
    val now = System.currentTimeMillis()
    val daysLeft = (expiresAtMillis - now) / (24L * 60 * 60 * 1000)
    val color = when {
        expiresAtMillis <= now -> Danger
        daysLeft <= 7 -> Warning
        else -> TextDim
    }
    val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale(lang)).format(java.util.Date(expiresAtMillis))
    Text(
        if (expiresAtMillis <= now) tr("playlists.expiry.expired", dateStr) else tr("playlists.expiry.until", dateStr),
        color = color,
        style = MaterialTheme.typography.bodySmall,
    )
}
