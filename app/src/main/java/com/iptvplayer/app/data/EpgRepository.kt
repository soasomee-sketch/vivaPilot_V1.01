package com.iptvplayer.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Odpowiednik epgCache z app.js, ale przebudowany pod pełny przewodnik: zamiast pobierać
 * i parsować CAŁY plik XMLTV osobno dla każdego kanału (jak w poprzedniej wersji — bardzo
 * wolne przy playlistach z kilkuset kanałami), teraz parsujemy plik RAZ na epgUrl i budujemy
 * mapę channelId -> lista programów, buforowaną 5 minut. Każde zapytanie o dany kanał
 * (teraz leciący / następny / cały dzień) korzysta z tej samej, jednorazowo sparsowanej mapy.
 */
class EpgRepository(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private data class FullCacheEntry(val byChannel: Map<String, List<EpgProgram>>, val fetchedAt: Long)

    private val cacheLock = Any()
    // Cache per URL EPG — playlisty mogą mieć różne źródła EPG (patrz PlaylistSource.epgUrl),
    // więc trzymamy osobny wpis dla każdego adresu, nie tylko jeden globalny.
    //
    // LinkedHashMap(accessOrder=true) + removeEldestEntry zamiast zwykłej HashMap — bez tego
    // ograniczenia cache rósł BEZ KOŃCA: TTL niżej (5 minut) oznacza wpis jako "nieaktualny"
    // do PONOWNEGO pobrania, ale NIGDY nie usuwał starych wpisów z pamięci. Przy playliście z
    // tysiącami kanałów rozrzuconych po wielu RÓŻNYCH źródłach EPG (np. 3817 kanałów w
    // zgłoszonym logu awarii), samo przewijanie listy — każdy wiersz kanału sprawdza "co
    // teraz leci" dla SWOJEGO epgUrl (patrz LiveChannelRow) — potrafiło w krótkim czasie
    // dorzucić do pamięci kilkadziesiąt osobnych, sparsowanych plików XMLTV, z których
    // WIĘKSZOŚĆ nie była już nawet oglądana (użytkownik dawno przewinął dalej). To realny,
    // potwierdzony wyciek pamięci prowadzący do OutOfMemoryError. MAX_CACHED_URLS ogranicza
    // to do garści NAJŚWIEŻEJ używanych źródeł — wystarczające dla normalnego przeglądania
    // (użytkownik i tak w danej chwili patrzy na kanały z bardzo niewielu źródeł naraz),
    // a stare, nieużywane wpisy same się wypierają zamiast rosnąć bez ograniczeń.
    private val cacheByUrl = object : LinkedHashMap<String, FullCacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FullCacheEntry>?): Boolean =
            size > MAX_CACHED_URLS
    }
    private val cacheTtlMillis = 5 * 60 * 1000L
    // Ogranicza, ile RÓŻNYCH plików XMLTV pobiera się i parsuje NARAZ — patrz obszerny
    // komentarz przy getProgramsForChannel niżej. 2 wystarcza, żeby dalej mieć płynne
    // przewijanie (kolejne żądania po prostu czekają chwilę w kolejce), a jednocześnie
    // realnie ograniczyć szczytowe zużycie pamięci przy bardzo szybkim przewijaniu.
    private val fetchSemaphore = java.util.concurrent.Semaphore(2)

    // isLenient = false — DOMYŚLNIE SimpleDateFormat jest "wyrozumiały" i przy niepoprawnym
    // formacie potrafi po cichu zwrócić BŁĘDNĄ, ale NIE null, datę (np. "poprawiając"
    // nieprawidłowy dzień/miesiąc zamiast rzucić wyjątek) — zamiast rzucić ParseException,
    // które i tak jest już bezpiecznie łapane niżej (parseXmltvDate zwraca wtedy null).
    // Realny, potwierdzony skutek: pojedynczy zniekształcony wpis w pliku XMLTV od
    // dostawcy (zdarza się, te pliki rzadko są rygorystycznie walidowane) prowadził do
    // ASTRONOMICZNIE błędnego czasu zakończenia programu — a różnica dwóch takich znaczników
    // czasu, przeliczona na piksele szerokości bloku w gridzie EPG, dawała wartość w setkach
    // milionów pikseli, czego Compose fizycznie nie potrafi zapisać w swoich Constraints
    // (crash: "Can't represent a size... in Constraints"). Ścisłe parsowanie sprawia, że
    // TAKI zniekształcony wpis zostaje po prostu POMINIĘTY (null), zamiast wywalić całą appkę.
    private val xmltvDateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply { isLenient = false }

    /** Program aktualnie leciący na danym kanale (tvg-id). */
    fun getNowPlaying(epgUrl: String, channelId: String): EpgProgram? {
        val now = System.currentTimeMillis()
        return getProgramsForChannel(epgUrl, channelId).find { now in it.startMillis..it.endMillis }
    }

    /** Program następujący zaraz po aktualnym — do podpisu "Dalej: ..." w wierszu kanału. */
    fun getNextProgram(epgUrl: String, channelId: String): EpgProgram? {
        val now = System.currentTimeMillis()
        return getProgramsForChannel(epgUrl, channelId)
            .filter { it.startMillis > now }
            .minByOrNull { it.startMillis }
    }

    /** Wszystkie programy danego kanału nakładające się na DZISIEJSZY dzień kalendarzowy
     * (wg lokalnej strefy urządzenia), posortowane chronologicznie — do pełnego przewodnika
     * pokazywanego po dotknięciu ikony EPG przy kanale. */
    fun getTodayPrograms(epgUrl: String, channelId: String): List<EpgProgram> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L

        return getProgramsForChannel(epgUrl, channelId)
            .filter { it.endMillis > dayStart && it.startMillis < dayEnd }
            .sortedBy { it.startMillis }
    }

    /** Jak getTodayPrograms, ale dla WIELU kanałów naraz — do pełnej siatki EPG (grid),
     * gdzie trzeba naraz pokazać program wielu wierszy/kanałów. Plik XMLTV pod danym
     * epgUrl parsuje się i tak tylko RAZ (patrz getProgramsForChannel/cacheByUrl) — to
     * tylko wygodne opakowanie zwracające wynik dla listy kanałów jednym wywołaniem,
     * zamiast osobnego wywołania getTodayPrograms per kanał. */
    fun getTodayProgramsForChannels(epgUrl: String, channelIds: List<String>): Map<String, List<EpgProgram>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L

        return channelIds.associateWith { channelId ->
            getProgramsForChannel(epgUrl, channelId)
                .filter { it.endMillis > dayStart && it.startMillis < dayEnd }
                .sortedBy { it.startMillis }
        }
    }

    private fun getProgramsForChannel(epgUrl: String, channelId: String): List<EpgProgram> {
        val now = System.currentTimeMillis()
        val cached = synchronized(cacheLock) {
            cacheByUrl[epgUrl]?.takeIf { now - it.fetchedAt < cacheTtlMillis }
        }
        val entry = cached ?: run {
            // Semafor ograniczający liczbę JEDNOCZESNYCH pobrań+parsowań plików XMLTV —
            // ograniczony rozmiar cache (patrz cacheByUrl wyżej) chroni przed NARASTAJĄCYM w
            // czasie zużyciem pamięci, ale NIE przed CHWILOWYM skokiem, gdy szybkie
            // przewijanie długiej listy kanałów (zgłoszony log awarii: 3817 kanałów) odpala
            // wiele NIEZALEŻNYCH pobrań różnych źródeł EPG w bardzo krótkim odstępie czasu —
            // każde z nich chwilowo trzyma WŁASNY, świeżo sparsowany wynik w pamięci, zanim
            // trafi do cache. acquire() blokuje wątek (bezpieczne — te funkcje są i tak
            // wołane z tła przez withContext(Dispatchers.IO) w miejscach wywołania), dopóki
            // nie zwolni się miejsce.
            fetchSemaphore.acquire()
            try {
                // Ponowne sprawdzenie PO uzyskaniu zezwolenia — jeśli inny wątek zdążył w
                // międzyczasie pobrać ten sam adres, nie ma sensu robić tego drugi raz.
                val recheck = synchronized(cacheLock) {
                    cacheByUrl[epgUrl]?.takeIf { System.currentTimeMillis() - it.fetchedAt < cacheTtlMillis }
                }
                recheck ?: run {
                    val parsed = fetchAndParseAll(epgUrl)
                    val fresh = FullCacheEntry(parsed, System.currentTimeMillis())
                    synchronized(cacheLock) { cacheByUrl[epgUrl] = fresh }
                    fresh
                }
            } finally {
                fetchSemaphore.release()
            }
        }
        return entry.byChannel[channelId].orEmpty()
    }

    /** Pobiera i parsuje CAŁY plik XMLTV jednym przejściem, grupując programy po id kanału —
     * odpowiednik dawnego fetchNowPlayingUncached(), ale zamiast szukać jednego dopasowania
     * i odrzucać resztę, zbiera WSZYSTKIE programy wszystkich kanałów naraz do bufora.
     *
     * KRYTYCZNA zmiana: parser czyta BEZPOŚREDNIO ze strumienia odpowiedzi HTTP
     * (response.body.charStream()), a NIE z całego tekstu wczytanego najpierw do jednego
     * Stringa (`.body?.string()`). Ta pozornie nieszkodliwa różnica miała realny koszt:
     * plik XMLTV dla playlisty z kilkuset kanałami i programem na cały dzień potrafi mieć
     * dziesiątki, a nawet setki megabajtów — a jako pojedynczy String w Kotlinie (UTF-16,
     * 2 bajty na znak) to w pamięci NIEMAL PODWÓJNY rozmiar samego pliku. Przy limicie
     * sterty ~256MB i appce, która i tak trzyma już w pamięci setki kanałów/plakatów, takie
     * JEDNORAZOWE przydzielenie potrafiło samo w sobie wywalić OutOfMemoryError — dokładnie
     * to zgłoszenie ("zamyka aplikację kiedy poruszam się w Live"), potwierdzone logiem
     * awarii wskazującym na alokację w trakcie otwierania grida EPG. Parsowanie strumieniowe
     * (XmlPullParser czyta kawałkami bezpośrednio z sieci) nigdy nie trzyma całego pliku w
     * pamięci naraz — tylko wynikową mapę programów, która i tak była budowana identycznie
     * jak dotychczas. */
    private fun fetchAndParseAll(epgUrl: String): Map<String, List<EpgProgram>> {
        val result = HashMap<String, MutableList<EpgProgram>>()
        try {
            val req = Request.Builder().url(epgUrl).build()
            http.newCall(req).execute().use { response ->
                val body = response.body ?: return emptyMap()
                body.charStream().use { reader ->
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(reader)

                    var currentChannel: String? = null
                    var start: Long? = null
                    var end: Long? = null
                    var title: String? = null
                    var desc: String? = null
                    var inTitle = false
                    var inDesc = false

                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "programme" -> {
                                    currentChannel = parser.getAttributeValue(null, "channel")
                                    start = parseXmltvDate(parser.getAttributeValue(null, "start"))
                                    end = parseXmltvDate(parser.getAttributeValue(null, "stop"))
                                    title = null; desc = null
                                }
                                "title" -> inTitle = true
                                "desc" -> inDesc = true
                            }
                            XmlPullParser.TEXT -> {
                                if (inTitle) title = parser.text
                                if (inDesc) desc = parser.text
                            }
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "title" -> inTitle = false
                                "desc" -> inDesc = false
                                "programme" -> {
                                    val ch = currentChannel
                                    // Odrzucenie programów o NIEROZSĄDNYM czasie trwania (ujemny
                                    // — koniec przed początkiem, albo dłuższy niż 48h) — to
                                    // prawie na pewno uszkodzone dane u źródła (np. literówka w
                                    // roku w pliku XMLTV dostawcy, dalej poprawnie sparsowana
                                    // przez SimpleDateFormat, ale semantycznie bezsensowna), nie
                                    // realny program. Filtrowanie TUTAJ, u źródła — zamiast
                                    // polegać wyłącznie na ograniczeniach szerokości w
                                    // EpgGridScreen.kt — usuwa złe dane, zanim w ogóle trafią do
                                    // cache'u, więc żaden inny odbiorca (podgląd "teraz leci" w
                                    // odtwarzaczu, wiersz kanału na Live) też ich nie zobaczy.
                                    val durationMillis = if (start != null && end != null) end - start else null
                                    if (ch != null && start != null && end != null &&
                                        durationMillis != null && durationMillis in 1..48 * 3_600_000L
                                    ) {
                                        result.getOrPut(ch) { mutableListOf() }.add(
                                            EpgProgram(
                                                channelId = ch,
                                                title = title ?: "—",
                                                description = desc,
                                                startMillis = start,
                                                endMillis = end,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        event = parser.next()
                    }
                }
            }
        } catch (e: Exception) {
            // Częściowo sparsowany wynik jest lepszy niż żaden — zwracamy to, co zdążyliśmy zebrać.
        }
        return result
    }

    private fun parseXmltvDate(raw: String?): Long? {
        if (raw == null) return null
        return try {
            xmltvDateFormat.parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }

    fun clearCache() {
        synchronized(cacheLock) { cacheByUrl.clear() }
    }

    private companion object {
        // Ile OSOBNYCH źródeł EPG (adresów URL) trzymać jednocześnie w pamięci — patrz
        // obszerny komentarz przy cacheByUrl wyżej. 8 to margines bezpieczny dla normalnego
        // przeglądania (użytkownik ogląda kanały z góra kilku playlist/kategorii na raz),
        // a jednocześnie wystarczająco mały, żeby NIE dopuścić do powtórki zgłoszonego
        // OutOfMemoryError przy playlistach z dziesiątkami różnych źródeł EPG naraz.
        const val MAX_CACHED_URLS = 8
    }
}
