package com.iptvplayer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptvplayer.app.data.Channel
import com.iptvplayer.app.data.MediaKind
import com.iptvplayer.app.data.SeriesInfo
import com.iptvplayer.app.i18n.tr
import com.iptvplayer.app.ui.theme.*
import com.iptvplayer.app.ui.tv.tvFocusFrame
import com.iptvplayer.app.util.containsFolded

/**
 * Prosty słownik "nastrój/gatunek po polsku -> fragment nazwy kategorii" do wyszukiwania
 * "na co masz dziś ochotę?" na Home (patrz moodMatchKeywords niżej). CELOWO nie prawdziwe
 * rozumienie języka naturalnego (appka nie ma dostępu do żadnego modelu językowego) — to
 * dopasowanie słów kluczowych do NAZW KATEGORII, które playlist i tak już ma (np. "|PL|
 * FILMY AKCJI", "|PL| KOMEDIE") — to jedyne dane o "gatunku" dostępne dla CAŁEGO katalogu
 * bez kosztownego zapytania na każdą pozycję z osobna (ten sam powód co przy sortowaniu
 * po roku zamiast po ocenie, patrz komentarz przy SortMode w GridScreens.kt).
 */
private data class MoodRule(val triggers: List<String>, val groupKeywords: List<String>)

private val MOOD_RULES = listOf(
    MoodRule(listOf("akcj"), listOf("akcj")),
    MoodRule(listOf("komedi", "śmieszn", "zabawn", "humor"), listOf("komedi")),
    MoodRule(listOf("straszn", "horror", "strach"), listOf("horror")),
    MoodRule(listOf("romantyczn", "romans", "miłosn", "walentynk"), listOf("roman")),
    MoodRule(listOf("sensacyjn", "thriller", "kryminal", "kryminał"), listOf("sensacj", "thriller", "krymina")),
    MoodRule(listOf("animowan", "bajk", "dla dzieci", "kreskówk"), listOf("animowan", "bajk", "dzieci", "kreskówk")),
    MoodRule(listOf("dokumentaln", "dokument"), listOf("dokument")),
    MoodRule(listOf("fantasy", "fantastyczn", "sci-fi", "scifi", "science fiction"), listOf("fantasy", "fantastyk", "sci-fi", "scifi")),
    MoodRule(listOf("wojenn", "o wojnie"), listOf("wojenn", "wojn")),
    MoodRule(listOf("familijn", "rodzinn", "dla całej rodziny"), listOf("familijn", "rodzinn")),
    MoodRule(listOf("przygodow", "przygod"), listOf("przygod")),
    MoodRule(listOf("muzyczn", "musical"), listOf("muzyczn", "musical")),
)

/** Rozpoznaje odniesienie do dekady w zapytaniu ("z lat 80", "lata 90-te", "lata 2000") i
 * zwraca odpowiadający jej zakres lat — używa TEJ SAMEJ funkcji yearFromTitle co
 * sortowanie po roku (GridScreens.kt), więc dopasowuje się do roku wyciągniętego z tytułu. */
private fun moodYearRange(query: String): IntRange? {
    val m = Regex("""lat(?:a|ach)?\s*(\d{2,4})""").find(query.lowercase()) ?: return null
    val raw = m.groupValues[1].toIntOrNull() ?: return null
    val decadeStart = when {
        raw >= 1900 -> raw - (raw % 10)
        raw < 30 -> 2000 + raw
        else -> 1900 + raw
    }
    return decadeStart until decadeStart + 10
}

/** Zwraca fragmenty nazw kategorii pasujące do NASTROJU wykrytego w zapytaniu (może być
 * pusta lista — wtedy mood-matching po prostu nic dodatkowo nie znajduje, zwykłe
 * dopasowanie po tytule działa jak dotychczas). */
private fun moodGroupKeywords(query: String, customRules: List<Pair<String, String>> = emptyList()): List<String> {
    val lower = query.lowercase()
    val builtIn = MOOD_RULES.filter { rule -> rule.triggers.any { lower.contains(it) } }
        .flatMap { it.groupKeywords }
    // Własne reguły użytkownika (Ustawienia -> Wyszukiwanie) — ten sam mechanizm dopasowania
    // co wbudowany słownik wyżej, tylko para (hasło, słowo kluczowe) zamiast listy triggerów
    // + listy słów kluczowych na regułę.
    val custom = customRules.filter { (trigger, _) -> lower.contains(trigger) }.map { it.second }
    return (builtIn + custom).distinct()
}

/**
 * Odpowiednik "global-search-toggle" z renderToolbar() w app.js: przełącznik pod
 * chipami kategorii, który rozszerza wyszukiwanie na WSZYSTKIE zakładki naraz
 * (Live + Movies + Series), a nie tylko bieżącą listę.
 */
@Composable
fun GlobalSearchToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tr("globalSearch.toggleLabel"), color = TextDim, style = MaterialTheme.typography.bodySmall)
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Bg),
        )
    }
}

/** Jeden ujednolicony wpis wyniku, odpowiednik obiektów mieszanych w getGlobalSearchResults(). */
private sealed class GlobalItem {
    abstract val name: String
    abstract val group: String?
    abstract val logo: String?

    data class Live(val channel: Channel) : GlobalItem() {
        override val name get() = channel.name
        override val group get() = channel.group
        override val logo get() = channel.logoUrl
    }
    data class Movie(val channel: Channel) : GlobalItem() {
        override val name get() = channel.name
        override val group get() = channel.group
        override val logo get() = channel.logoUrl
    }
    data class Series(val series: SeriesInfo) : GlobalItem() {
        override val name get() = series.name
        override val group get() = series.group
        override val logo get() = series.posterUrl
    }
}

/**
 * Lista wyników wyszukiwania globalnego — odpowiednik renderGlobalResults() z app.js:
 * płaska lista wierszy (nie siatka), z ikoną typu (📺/🎬/📀) przed nazwą, nazwą kategorii
 * pod spodem i gwiazdką ulubionych (poza seriami, tak jak w oryginale).
 */
@Composable
fun GlobalSearchResults(
    query: String,
    liveChannels: List<Channel>,
    movies: List<Channel>,
    series: List<SeriesInfo>,
    favorites: Set<String>,
    // Dodawanie do dowolnej listy (Ulubione i inne) — ta sama droga co wszędzie indziej w
    // appce ("+" -> okno "Dodaj do listy"), zamiast osobnej, szybkiej gwiazdki tylko pod
    // Ulubione. Przy okazji naprawia też brak działającej gwiazdki dla seriali w wynikach
    // (wcześniej był to no-op — serial nie da się było dodać do Ulubionych z poziomu
    // wyszukiwania globalnego wcale).
    onAddToList: (id: String, kind: MediaKind) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onOpenSeries: (SeriesInfo) -> Unit,
    // Filmy z wyników globalnych otwierają ekran informacji zamiast odtwarzać od razu —
    // domyślnie taki sam jak onPlayChannel, żeby nie łamać istniejących wywołań.
    onOpenMovie: (Channel) -> Unit = onPlayChannel,
    // Własne reguły wyszukiwania nastrojowego zdefiniowane przez użytkownika (Ustawienia ->
    // Wyszukiwanie) — domyślnie puste, żeby nie łamać istniejących wywołań tam, gdzie
    // konkretny ekran jeszcze nie przekazuje tego dalej.
    customMoodRules: List<Pair<String, String>> = emptyList(),
) {
    val q = query.trim()
    if (q.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("globalSearch.prompt"), color = TextDim)
        }
        return
    }

    val results = remember(q, liveChannels, movies, series) {
        // Wyszukiwanie "na co masz dziś ochotę?" — poza zwykłym dopasowaniem po tytule,
        // TAKŻE dopasowuje po nazwie kategorii i po roku wyciągniętym z tytułu, jeśli
        // zapytanie zawiera rozpoznany nastrój/gatunek/dekadę (patrz MOOD_RULES i
        // moodYearRange wyżej) — np. "chciałbym coś strasznego" znajdzie filmy z kategorii
        // zawierającej "horror", nawet jeśli żaden tytuł dosłownie nie zawiera słowa
        // "straszny". Dla kanałów Live pomijamy mood-matching — nie mają "gatunku" w tym
        // sensie, tylko zwykłe kategorie tematyczne (sport/informacyjne itd.), które nie
        // pasują do słownika nastrojów budowanego z myślą o filmach/serialach.
        val moodKeywords = moodGroupKeywords(q, customMoodRules)
        val yearRange = moodYearRange(q)
        val moodMatch: (String?, String) -> Boolean = { group, title ->
            (moodKeywords.isNotEmpty() && group != null && moodKeywords.any { kw -> group.lowercase().contains(kw) }) ||
                (yearRange != null && yearFromTitle(title)?.let { it in yearRange } == true)
        }
        buildList {
            liveChannels.filter { it.name.containsFolded(q) }.forEach { add(GlobalItem.Live(it)) }
            movies.filter { it.name.containsFolded(q) || moodMatch(it.group, it.name) }.forEach { add(GlobalItem.Movie(it)) }
            series.filter { it.name.containsFolded(q) || moodMatch(it.group, it.name) }.forEach { add(GlobalItem.Series(it)) }
        }
    }

    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("globalSearch.noResults"), color = TextDim)
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(results) { result ->
            GlobalResultRow(
                result = result,
                isFavorite = when (result) {
                    is GlobalItem.Live -> result.channel.id in favorites
                    is GlobalItem.Movie -> result.channel.id in favorites
                    is GlobalItem.Series -> false
                },
                onAddToList = {
                    when (result) {
                        is GlobalItem.Live -> onAddToList(result.channel.id, MediaKind.LIVE)
                        is GlobalItem.Movie -> onAddToList(result.channel.id, MediaKind.MOVIE)
                        is GlobalItem.Series -> onAddToList(result.series.id, MediaKind.SERIES)
                    }
                },
                onClick = {
                    when (result) {
                        is GlobalItem.Live -> onPlayChannel(result.channel)
                        is GlobalItem.Movie -> onOpenMovie(result.channel)
                        is GlobalItem.Series -> onOpenSeries(result.series)
                    }
                },
            )
        }
    }
}

@Composable
private fun GlobalResultRow(
    result: GlobalItem,
    isFavorite: Boolean,
    onAddToList: () -> Unit,
    onClick: () -> Unit,
) {
    val typeIcon = when (result) {
        is GlobalItem.Live -> "\uD83D\uDCFA "   // 📺
        is GlobalItem.Movie -> "\uD83C\uDFAC "  // 🎬
        is GlobalItem.Series -> "\uD83D\uDCC0 " // 📀
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusFrame(shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface2),
            contentAlignment = Alignment.Center,
        ) {
            if (result.logo != null) {
                AsyncImage(model = result.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(result.name.take(2).uppercase(), color = TextDim, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                typeIcon + result.name,
                color = TextMain,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                result.group ?: tr("globalSearch.noCategory"),
                color = TextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // "+" zamiast gwiazdki — ta sama droga co wszędzie indziej w appce (patrz komentarz
        // przy onAddToList w GlobalSearchResults wyżej). Teraz dostępne dla WSZYSTKICH
        // typów wyników, także seriali (wcześniej gwiazdka dla seriali nic nie robiła).
        IconButton(onClick = onAddToList) {
            Icon(
                Icons.Default.Add,
                contentDescription = tr("common.addToList"),
                tint = if (isFavorite) Accent2 else TextDim,
                )
            }
    }
}
