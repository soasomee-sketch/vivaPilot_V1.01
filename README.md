# VivaPilot — Android (Kotlin + Jetpack Compose + libVLC)

Natywna aplikacja Android będąca odpowiednikiem `iptv-web-player.html`, ale z pełną obsługą
kodeków dzięki silnikowi **libVLC** (ten sam core co desktopowy VLC) zamiast ograniczonego
`<video>` z przeglądarki.

## Co zostało przeniesione z wersji HTML (layout / UX 1:1)

- **Paleta kolorów** — dokładnie te same wartości co CSS custom properties
  (`--bg #0b0b0f`, `--surface #15151c`, `--accent #4f9dff`, `--accent-2 #ffcf4f`, `--danger #ff6868`
  itd.) — patrz `ui/theme/Color.kt`.
- **Nawigacja dolna** — 6 zakładek w tej samej kolejności co `<nav>`: Home, Live, Movies, Series,
  Playlists, Settings (`ui/nav/BottomNav.kt`).
- **Header** — tytuł + przycisk odświeżania + status pill, jak `<header>` w HTML.
- **Home** — zegar, karta statusu playlisty, szybka nawigacja kafelkowa, sekcja "kontynuuj oglądanie".
- **Live** — pasek wyszukiwania, chipy kategorii/ulubionych, lista kanałów z logo i EPG "teraz leci"
  (cache 5 min, tak jak `epgCache` w app.js).
- **Movies / Series** — siatka plakatów.
- **Playlists** — dodawanie źródeł Xtream Codes lub M3U/M3U8, obsługa `#EXTVLCOPT` (User-Agent/Referer).

## Co jest nowe (silnik kodeków)

`player/VlcPlayerManager.kt` opakowuje `org.videolan.android:libvlc-all` i obsługuje:
H.264, H.265/HEVC (także 10-bit), AV1, VP8/VP9, MPEG-2, AC-3, E-AC-3, DTS, TrueHD,
kontenery MKV/TS/MP4/AVI, strumienie HLS/DASH — bez transkodowania, niezależnie od tego,
co dany telefon wspiera natywnie w MediaCodec.

## Budowanie lokalnie

Wymagania: Android Studio (Koala lub nowszy), JDK 17, Android SDK 34.

**Uwaga o Gradle Wrapper:** ze względu na brak dostępu do sieci w tym środowisku, plik
`gradle/wrapper/gradle-wrapper.jar` (binarny) nie został dołączony — jest tylko
`gradle-wrapper.properties`. Najprościej: otwórz folder w Android Studio, ono samo
dogeneruje/pobierze wrapper przy pierwszym sync. Alternatywnie z terminala: `gradle wrapper`
(jeśli masz zainstalowane Gradle globalnie), co uzupełni brakujący jar.

```bash
git clone <to repo albo rozpakuj ZIP>
cd IPTVPlayerAndroid
./gradlew assembleDebug
```

APK znajdziesz w `app/build/outputs/apk/debug/app-debug.apk`. Możesz też po prostu otworzyć
folder w Android Studio → "Open" → poczekać na Gradle sync → Run.

**Uwaga:** to środowisko (sandbox Claude) nie ma dostępu do Android SDK / Maven Central,
więc nie mogłem realnie skompilować i przetestować projektu — kod jest napisany starannie
i powinien się zbudować, ale przy pierwszym Gradle sync mogą wyskoczyć drobne niezgodności
wersji (AGP/Kotlin/Compose Compiler) w zależności od wersji Android Studio, które masz
zainstalowane. Jeśli coś nie zbuduje się od razu — wklej mi log błędu z Android Studio,
poprawię.

## Struktura projektu

```
app/src/main/java/com/iptvplayer/app/
  MainActivity.kt          — header + bottom nav + routing zakładek
  IptvApp.kt
  data/                     — modele, parser M3U, klient Xtream Codes, EPG (XMLTV), LocalStore
  player/                   — VlcPlayerManager (silnik libVLC) + PlayerActivity (pełny ekran)
  ui/theme/                 — kolory i typografia 1:1 z CSS
  ui/nav/                   — dolna nawigacja
  ui/screens/               — Home, Live, Movies/Series (grid), Playlists, Settings
```

## Obsługa pilota (Android TV / Google TV)

Aplikacja jest w pełni nawigowalna pilotem — zarówno klasycznym (strzałki + OK), jak i
okrągłym pilotem Google TV bez klawiatury numerycznej (np. Chromecast z Google TV, boxy
Xiaomi z Google TV). Oba typy wysyłają te same standardowe klawisze D-pad, więc appka nie
rozróżnia ich na poziomie kodu.

- `AndroidManifest.xml` — `uses-feature` dla `leanback`/`touchscreen required=false` (appka
  nie jest filtrowana na urządzeniach bez dotyku) + własny banner TV (`res/drawable-xhdpi/tv_banner.png`).
- `ui/tv/TvFocus.kt` — wspólne narzędzia: `tvFocusFrame()` (widoczne obramowanie + lekkie
  powiększenie fokusowanego elementu, bo domyślny ripple jest z 3 metrów praktycznie
  niewidoczny) i `notDpadFocusable()` (wyłącza z nawigacji strzałkami drugorzędne mini-
  ikony nałożone na kartę — np. gwiazdkę ulubionych na plakacie — zachowując dotyk;
  na pilocie te akcje są dostępne przez długie przytrzymanie OK na karcie).
- Dolna nawigacja ma gwarantowany punkt startowy fokusu (zakładka Home), żeby pierwsze
  naciśnięcie strzałki po uruchomieniu appki na pewno coś zrobiło.
- Fizyczny przycisk Wstecz: zamyka ekran szczegółów filmu/serialu zamiast wychodzić z appki,
  a z dowolnej zakładki innej niż Home wraca najpierw na Home (jak w YouTube/Netflix na TV).
- `PlayerActivity` — najważniejsza zmiana: kontrolki odtwarzacza (wcześniej chowały się po
  4s i dało się je przywołać TYLKO dotykiem) teraz reagują na dowolny klawisz pilota.
  Strzałki lewo/prawo = przewijanie ±10s (globalny skrót, jak w YouTube/Netflix na TV),
  klawisz multimedialny play/pauza na pilocie działa bezpośrednio, OK pokazuje kontrolki.
  Jedyne fokusowalne przyciski w odtwarzaczu to Wstecz i Play/Pauza — pobieranie i "otwórz
  w VLC" zostały świadomo touch-only, żeby nawigacja strzałkami była przewidywalna.

## Własne listy ("Moje listy")

Użytkownik może tworzyć dowolną liczbę nazwanych, ręcznie budowanych kolekcji treści
(np. "Święta", "Wieczór z akcją") mieszających kanały live, filmy i seriale w jednej
liście — coś jak "Moja lista" na Netflixie, tylko że może ich być wiele naraz.

- **Ulubione (gwiazdka) to teraz jedna z tych list** — wbudowana, zawsze pierwsza,
  nie da się jej skasować ani zmienić nazwy. Stara logika gwiazdek (dotyk = przełącz,
  długi OK na pilocie = przełącz) działa dokładnie tak jak wcześniej, tylko pod spodem
  zapisuje się teraz do tego samego mechanizmu co listy własne — patrz
  `AppViewModel.migrateLegacyFavoritesIfNeeded()` (migracja starych ulubionych
  jednorazowo dopasowuje typ każdego id do aktualnie wczytanej treści).
- **Gdzie żyją**: sekcja "MOJE LISTY" na Home (`ui/screens/HomeScreen.kt`,
  `MyListsSection`) — pozioma "półka" kart, każda karta otwiera pełny widok listy
  (`ui/screens/ListDetailScreen.kt`).
- **Dodawanie pozycji**: ikonka "+" na każdym plakacie (obok gwiazdki, ale OSOBNO —
  gwiazdka zawsze znaczy tylko Ulubione) i w pasku szczegółów filmu/serialu; tryb
  wielokrotnego zaznaczania ("Zaznacz" nad siatką Movies/Series) do hurtowego dodania
  wielu pozycji naraz — dokładnie pod Twój przykład ze świąteczną listą.
- **Model danych**: `data/Models.kt` — `CustomList` (id, nazwa, emoji, kolor opcjonalny,
  lista `ListItemRef`) + `ListItemRef` (id + typ MediaKind, żeby lista mogła mieszać
  live/movie/series). Zapis w `LocalStore.kt` jako osobny klucz JSON, uwzględniony też
  w eksporcie/imporcie kopii zapasowej (wersja formatu podniesiona do 2, wstecznie
  kompatybilna ze starymi plikami kopii).
- **Czego świadomie nie ma w tej wersji**: ręcznego przeciągania kolejności list/pozycji
  (kolejność = kolejność dodania), pełnego selektora kolorów (jest 6 gotowych próbek),
  multi-select w zakładce Live (tam tylko pojedyncze "+" na wierszu kanału) i dodawania
  do list bezpośrednio z wyników wyszukiwania globalnego. Wszystko to dałoby się dodać
  w kolejnym kroku, jeśli się przyda.

## Ścieżki audio i napisy w odtwarzaczu

Ikonka nutki w dolnym pasku odtwarzacza (obok czasu trwania) otwiera okno wyboru ścieżki
dźwięku i napisów — dla filmów/seriali z więcej niż jedną ścieżką audio (np. lektor +
oryginał) albo z dostępnymi napisami w kilku językach. `VlcPlayerManager` już wcześniej
miał gotowe metody `audioTracks()`/`setAudioTrack()`/`subtitleTracks()`/`setSubtitleTrack()`
(dopisane pod tę funkcję, ale nigdy nie podłączone do UI) — dodałem tylko brakujące
gettery bieżącej ścieżki (`currentAudioTrack()`/`currentSubtitleTrack()`) i sam ekran wyboru.

Ikonka celowo siedzi w DOLNYM pasku (obok czasu), a nie w górnym obok Wstecz — dzięki
temu jest dostępna pilotem przez strzałkę w DÓŁ z przycisku play/pauza, bez kolidowania
z lewo/prawo, które jest globalnie zarezerwowane pod przewijanie (patrz sekcja o pilocie
wyżej). Napisy mają zawsze doklejoną ręcznie pozycję "Wyłączone", niezależnie od tego,
czy strumień w ogóle ma jakieś napisy.

## Co warto dopracować w kolejnym kroku

- Ekran szczegółów serialu (sezony/odcinki) — na razie tylko lista seriali, kliknięcie w serial
  nie otwiera jeszcze widoku odcinków (`TODO` w `MainActivity.kt`).
- Pełne i18n na 7 języków (obecnie UI jest po polsku na sztywno — struktura pod tłumaczenia
  w `res/values-*` jest przygotowana, ale stringi trzeba jeszcze wydzielić z kodu do `strings.xml`).
- Picture-in-Picture na Android TV — osobny temat od nawigacji pilotem (już zrobionej,
  patrz wyżej), wymaga `android:supportsPictureInPicture` + obsługi cyklu życia PiP.
- Zapis pozycji odtwarzania per-odcinek serialu (obecnie WatchProgress działa dla kanałów/filmów).
- Nawigacja pilotem nie była testowana na fizycznym urządzeniu (ten sandbox nie ma dostępu
  do Android SDK/emulatora) — kod jest napisany w oparciu o standardowe, udokumentowane API
  Compose Foundation (`clickable`/`focusable`/`onKeyEvent`/`focusProperties`), ale przy
  pierwszym teście na boxie warto sprawdzić zwłaszcza: focus w zagnieżdżonych listach
  (Home) i płynność przewijania w odtwarzaczu.
