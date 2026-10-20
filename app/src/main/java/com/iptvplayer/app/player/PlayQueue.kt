package com.iptvplayer.app.player

import com.iptvplayer.app.data.MediaKind
import java.io.Serializable

/**
 * Pojedyncza pozycja w kolejce nawigacji odtwarzacza — w pełni rozwiązana i gotowa do
 * odtworzenia (ma już streamUrl/UA/referer), żeby PlayerActivity mogła przełączać się
 * między pozycjami bez żadnych dodatkowych zapytań do API.
 */
data class QueueEntry(
    val id: String,
    val kind: MediaKind,
    val title: String,
    val streamUrl: String,
    val posterUrl: String?,
    val userAgent: String?,
    val referer: String?,
    // Wypełnione TYLKO dla odcinków serialu (SeriesInfo.id) — pozwala pogrupować wpisy
    // "Kontynuuj oglądanie" po serialu zamiast pokazywać osobny kafelek na KAŻDY zaczęty
    // odcinek (patrz WatchProgress.seriesId i Models.forContinueWatching niżej). Null dla
    // filmów/kanałów live/plików lokalnych — te zawsze zostają własną, osobną pozycją.
    val seriesId: String? = null,
    // Wypełnione TYLKO dla kanałów live z faktycznym źródłem EPG (patrz queueEntryFromChannel
    // w MainActivity) — pozwala pokazać przewodnik programowy bezpośrednio w PlayerActivity
    // (przycisk w górnym pasku), bez wychodzenia do listy kanałów. epgChannelId to tvg-id z
    // playlisty, epgUrl to adres XMLTV playlisty, do której należy ten kanał.
    val epgChannelId: String? = null,
    val epgUrl: String? = null,
    // Numer sezonu, do którego należy odcinek (tylko SERIES) — pozwala QueueBrowserRow w
    // odtwarzaczu pokazać małe separatory "Sezon N" między odcinkami z różnych sezonów,
    // zamiast płaskiej listy wszystkich odcinków pod rząd bez żadnego podziału.
    val seasonNumber: Int? = null,
) : Serializable

/** Skąd wzięła się kolejka — używane tylko do opisu w podglądzie "następny/poprzedni". */
enum class QueueKind { LIVE_GROUP, SERIES_EPISODES, SIMILAR_MOVIES, CUSTOM_LIST, LOCAL_FOLDER }

/**
 * Kolejka nawigacji przekazywana do PlayerActivity jednym ekstrą (Serializable) —
 * odpowiada regule: LIVE -> następny kanał w grupie, MOVIE -> następny "podobny" film,
 * SERIES -> zawsze następny odcinek, a jeśli materiał (LIVE/MOVIE) został uruchomiony
 * z niestandardowej listy użytkownika -> następny materiał z tej listy (kolejność listy
 * nadpisuje grupę/podobne, ale NIE nadpisuje serialu — stąd osobny CUSTOM_LIST z
 * `seriesSubQueues`: gdy nawigacja trafi w liście na pozycję-serial, PlayerActivity trwale
 * zamienia aktywną kolejkę na odcinki tego serialu, bo serial zawsze odtwarza kolejny odcinek).
 */
data class NavQueue(
    val kind: QueueKind,
    val items: List<QueueEntry>,
    val startIndex: Int,
    // Klucz = QueueEntry.id pozycji-serialu w `items`. Wartość = (spłaszczone odcinki serialu,
    // indeks odcinka od którego zacząć — wznowienie jeśli jest, inaczej pierwszy odcinek).
    val seriesSubQueues: Map<String, SeriesSubQueue> = emptyMap(),
) : Serializable

data class SeriesSubQueue(
    val episodes: List<QueueEntry>,
    val startIndex: Int,
) : Serializable
