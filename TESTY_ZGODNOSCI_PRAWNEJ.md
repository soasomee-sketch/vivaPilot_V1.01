# VivaPilot – zgodność prawna: co zrobiono, co musisz uzupełnić, jak przetestować

Dotyczy specyfikacji z 09.10 (12 części) wraz z aktualizacją **do wersji 1.12** z 20.10 (nowa nazwa **VivaPilot**, nowe teksty Regulaminu i Polityki, nowe pola przed zakupem, dane wydawcy, zaszyfrowany rejestr zgód z eksportem). Ten plik jest dla Ciebie – bez żargonu.

> **Wersja 1.12 = aplikacja VivaPilot + tymczasowy panel testowy** (panel pochodzi z wersji 1.05; pomarańczowy znacznik „TEST” przy prawej krawędzi ekranu; tylko w plikach `*-debug.apk`, w wersji release go nie ma). Dzięki niemu testujesz samymi przyciskami na telefonie: przyspieszanie czasu, symulacja zakupu/zwrotu, czyszczenie zgód. **Instrukcja krok po kroku: `TESTY_NA_TELEFONIE.md`.** Poniższe testy 1–14 zostają jako pełna lista; przy każdym, który panel ułatwia, jest dopisek „panel”.

---

## 0. Co się zmieniło w 1.12 (według promptu „aktualizacja 1.04 → 1.12”)

| Punkt promptu | Co | Gdzie to zobaczysz |
|---|---|---|
| 0 | Nazwa **VivaPilot** wszędzie (kod, zasoby, tłumaczenia, manifest, Gradle, README, nazwa kanału „Wyślij do TV”, nazwa pliku potwierdzenia, identyfikator produktu `vivapilot_full_version`). Nowe logo (prostokątne) w Ustawieniach, na ekranie startowym i jako baner Android TV. Identyfikator aplikacji zmieniony na `com.vivapilot.iptv` (aplikacja nie była jeszcze w Google Play) | Ikona/nazwa, ekran startowy, Ustawienia |
| 1 | `versionName 1.12`, `versionCode 6`; „Wersja aplikacji i dokumentów: 1.12”; przy akceptacji zapisuje się „1.12_PL” | Ustawienia › Informacje prawne, Moje zgody |
| 2 | Regulamin i Polityka prywatności w wersji 1.12 (Google Play) – treść bez zmian, wklejona z Twoich plików | Ustawienia › Informacje prawne, ekran powitalny |
| 3 | Nowe teksty dwóch pól przed zakupem (dokładnie z promptu); „Przejdź do płatności” nieaktywne, dopóki oba pola nie są zaznaczone | Ekran „Pełna wersja VivaPilot” |
| 4 | Drobny druk **pod** przyciskiem płatności z linkami do Regulaminu i Polityki; przycisk końcowy i napis „zamówienie z obowiązkiem zapłaty” zostają po stronie okna Google Play | Ten sam ekran |
| 5 | Nowa linia wydawcy (adres: ul. Przybyszewskiego 209B/9, 93-120 Łódź; e-mail dg.komunikacja@gmail.com) – ekran przed zakupem, potwierdzenie zakupu, Ustawienia › Informacje prawne; ten sam e-mail we wpisie o libVLC | Trzy miejsca + Licencje |
| 6 | Nowy tekst „Przed zakupem: (1)… (2)…” na ekranie potwierdzenia + przycisk **„Pobierz potwierdzenie zgód”** | Po zakupie |
| 7 | **Rejestr zgód** (zastępuje dawny „rekord”): zdarzenia z identyfikatorem UUID, godziną UTC, wersjami (np. 1.12_PL), platformą GOOGLE_PLAY, numerem transakcji (po zakupie) i zgodami z osobnymi godzinami. Zapis zaszyfrowany (AES-256, klucz w Android Keystore), w prywatnej pamięci appki; czyszczenie pamięci podręcznej go nie rusza; nigdzie nie jest wysyłany | Moje zgody |
| 8 | „Moje zgody”: tylko odczyt, jeden przycisk **„Pobierz potwierdzenie zgód”** (plik tekstowy z częścią czytelną i JSON-em, systemowe okno zapisu), pod zapisem tekst „Zapisu nie można zmienić…” | Ustawienia › Informacje prawne › Moje zgody |

---

## 1. Co zostało zrobione (po kolei wg specyfikacji)

| Część | Co | Gdzie to zobaczysz w appce |
|---|---|---|
| 1 | TMDB całkowicie usunięty (UI, połączenia, zapisany klucz kasowany przy pierwszym uruchomieniu nowej wersji) | Brak pola TMDB w Ustawieniach |
| 2 | Nowy ekran powitalny „Witaj w VivaPilot” (checkbox **pełnoletność 18+** / Regulamin / Polityka, przycisk „Rozpocznij”, przycisk „Nie akceptuję – zamknij aplikację”, pilot D-pad). **Bez akceptacji appka nie działa**: żaden inny ekran, żadne odtwarzanie, żadne pobieranie listy, żadne połączenie sieciowe, a Wstecz zamyka appkę. Numer wersji appki 1.12 = numer wersji Regulaminu i Polityki. Pod polem mała czcionka „Bez akceptacji nie możesz korzystać z aplikacji…”. **Zaszyfrowany rejestr zgód** w prywatnej pamięci appki (zdarzenie 1: akceptacja – data i godzina UTC, wersje Regulaminu i Polityki „1.12_PL”, oświadczenie o pełnoletności, wersja appki; po zakupie zdarzenie 2: numer zamówienia i dwie zgody zakupowe z osobnymi godzinami) – nigdzie nie wysyłany, bez możliwości edycji | Pierwsze uruchomienie |
| 3 | Okres próbny 5 dni (liczony lokalnie) + blokada: wyszukiwanie, Moje listy, pobieranie. Jednorazowe okno „Okres próbny zakończony”. Opcja debug do przesunięcia zegara | Po 5 dniach / Ustawienia (tylko wersja debug) |
| 4 | Ekran „Pełna wersja VivaPilot” przed płatnością – 2 checkboxy domyślnie puste, „Przejdź do płatności” aktywne dopiero po zaznaczeniu obu. **Zawsze widoczna linia „Wydawca: Damian Gałka, adres przedsiębiorstwa i do korespondencji: ul. Przybyszewskiego 209B/9, 93-120 Łódź, dg.komunikacja@gmail.com”**, drobny druk z linkami pod przyciskiem płatności | Po „Kup Pełną wersję” |
| 5 | Zakup: acknowledge, obsługa PENDING, sprawdzanie zakupów przy każdym starcie, przycisk „Sprawdź zakup ponownie” | Ustawienia › Informacje prawne › Zakup |
| 6 | Ekran „Pełna wersja odblokowana” (potwierdzenie) + „Zapisz potwierdzenie” do pliku .txt (okno wyboru miejsca) i „Pobierz potwierdzenie zgód” | Po zakupie i w Ustawieniach › „Pokaż potwierdzenie” |
| 7 | Ustawienia › Informacje prawne: Regulamin, Polityka, strona www, licencje open source, wpis o libVLC, wydawca, zakup, „Moje zgody” (podgląd rejestru, tylko do odczytu, z eksportem), „Wersja aplikacji i dokumentów: 1.12” | Ustawienia (na dole) |
| 8 | 4 ostrzeżenia: Wyślij do TV (raz), eksport kopii, udostępnienie logu awarii, pobieranie niemożliwe | Odpowiednie miejsca w appce |
| 9 | Dwa wydania: `noads` i `ads` (Google Mobile Ads + UMP tylko w `ads`) | Patrz sekcja 3 poniżej |
| 10 | Powiadomienie o zmianie Regulaminu/Polityki (treść i data z pliku dokumentu) | Pojawia się po aktualizacji z nowszą wersją dokumentu |
| 11 | Zweryfikowano: brak analityki, brak śledzenia, brak automatycznego wysyłania awarii, brak kont i serwerów wydawcy | – |
| 12 | Scenariusze testów – niżej | – |

---

## 2. CO MUSISZ UZUPEŁNIĆ SAM (zaślepki)

1. **Teksty prawne** – w pliku są teraz **Twoje teksty 1.12** (wersja dla Google Play, treść bez zmian; zamienione tylko formatowanie z Worda na zwykły tekst). Gdyby kiedyś trzeba było je podmienić, zmieniasz dwa pliki:
   - `app/src/main/assets/legal/regulamin.txt`
   - `app/src/main/assets/legal/polityka_prywatnosci.txt`

   Format nagłówka (3 linie, potem linia `---`, potem pełna treść):
   ```
   WERSJA: 1.12
   DATA_WEJSCIA_W_ZYCIE: 2026-10-19
   ZMIANY: krótki opis zmian w tej wersji
   ---
   (pełny tekst dokumentu)
   ```
   **Numer `WERSJA` w obu plikach zawsze musi być taki sam jak numer wersji appki** (teraz 1.12; ustawia go `versionName` w `app/build.gradle.kts`). Przy kolejnej zmianie, którą mi zlecisz, podnoszę wszystko razem o 0,01 (1.13, 1.14…), a `versionCode` o 1 – tego nie musisz robić sam. Datę wejścia w życie (2026-10-19) i krótki opis zmian w nagłówku ustawiłem sam – popraw, jeśli ma być inaczej. W samych tekstach **nie ruszałem** m.in. miejsc „[ADRES STRONY]” (do uzupełnienia przez Ciebie) i sformułowania „Google lub Samsunga” w § 4 ust. 4 Regulaminu – to Twoja decyzja, czy zostawić.
2. **Adres strony z dokumentami** – stała `LEGAL_WEBSITE_URL` w pliku `app/src/main/java/com/iptvplayer/app/data/LegalDocuments.kt` (teraz zaślepka: `https://vivapilot.example.com/legal`).
3. **Reklamy (tylko wydanie `ads`)** – obecnie są **testowe identyfikatory Google** (bezpieczne, nie pokazują prawdziwych reklam). Przed publikacją podmień na swoje z konta AdMob:
   - identyfikator aplikacji: `app/src/ads/AndroidManifest.xml`
   - identyfikatory jednostek (baner, pełnoekranowa): `app/src/ads/java/com/iptvplayer/app/ads/AdConfig.kt`
4. **Produkt w Google Play Console** – identyfikator produktu jednorazowego musi nazywać się dokładnie tak, jak w kodzie: **`vivapilot_full_version`** (zmienił się razem z nazwą aplikacji; jeśli w konsoli miałeś produkt ze starą nazwą, utwórz nowy produkt o tym identyfikatorze). Nazwa produktu w sklepie: „VivaPilot – Pełna wersja (zakup jednorazowy)”. Aplikacja ma też nowy identyfikator pakietu `com.vivapilot.iptv` – w Play Console zakładasz ją od nowa pod tym identyfikatorem (nie była jeszcze opublikowana, więc nic nie tracisz).
5. **Tłumaczenia** – wszystkie nowe teksty są po polsku, angielsku, niemiecku, rosyjsku, ukraińsku, hiszpańsku i francusku. Teksty prawne (Regulamin/Polityka) są jednym plikiem – jeśli chcesz wersji w innych językach, to osobna decyzja.

---

## 3. WAŻNE: zmiana w budowaniu (GitHub Actions)

Dodano dwa warianty appki: **`noads`** (bez reklam) i **`ads`** (z reklamami). Zwykły build (`assembleDebug` / `assembleRelease`) buduje teraz **OBA**, więc zamiast jednego pliku `app-debug.apk` dostaniesz:

- `app-noads-debug.apk` i `app-ads-debug.apk` (oraz `...-release.apk`)

W paczce jest gotowy plik workflow **`.github/workflows/build.yml`** (dostosowany do dwóch wariantów: po zakończonym buildzie w zakładce Actions są dwa artefakty – `vivapilot-noads-debug-apk` i `vivapilot-ads-debug-apk`). Stary plik workflow szukał dokładnie `app-debug.apk`, więc po dodaniu wariantów przestałby go znajdować – **podmień go na ten z paczki**.

**Uwaga:** przeglądarka GitHuba często gubi foldery zaczynające się od kropki przy przeciąganiu plików. Jeśli po wgraniu paczki nie ma folderu `.github`: na stronie repozytorium **Add file → Create new file**, w polu nazwy wpisz `.github/workflows/build.yml`, wklej zawartość pliku z paczki i zatwierdź (**Commit changes**).

Do Sklepu Play zwykle idzie wariant `noads` (domyślny). Wariant `ads` to osobna wersja appki.

**Kolejność budowania w GitHub Actions (po pierwszym nieudanym buildzie):** workflow buduje **najpierw `noads`** (zwykła wersja – to ją instalujesz do testów; jeśli ten krok się nie uda, build jest czerwony), a **dopiero potem `ads`** w osobnym kroku, którego niepowodzenie nie psuje całego buildu (artefakt `vivapilot-ads-debug-apk` wtedy może się nie pojawić – to nie przeszkadza w testach wersji `noads`). Powód: biblioteka reklam Google (Mobile Ads 25.5.0) jest zbudowana nowszym Kotlinem niż używany w projekcie (1.9.24), więc dla wariantu `ads` w `app/build.gradle.kts` włączono flagę kompilatora `-Xskip-metadata-version-check` (tylko dla zadań `compileAds…`). Nie mogłem tego sprawdzić kompilatorem – jeśli krok `ads` nadal pokaże błąd, wklej log tego kroku.

Instalacja na telefonie: wariant `ads` i `noads` mają ten sam identyfikator pakietu, więc jeden nadpisuje drugi (czasem trzeba odinstalować poprzedni). Wersja 1.12 ma **nowy** identyfikator (`com.vivapilot.iptv`), więc **nie nadpisuje** wersji 1.05 – obie będą na telefonie obok siebie; starą odinstaluj.

---

## 4. TESTY (część 12 specyfikacji)

Każdy test: **co zrobić → czego oczekiwać**.

### Test 1 – Czysta instalacja
1. Odinstaluj appkę (albo w Ustawieniach telefonu „Wyczyść dane”), zainstaluj świeży build. *(Od wersji 1.02 stara akceptacja z wersji 1.0/1.01 – ze starym tekstem „16 lat” – jest ignorowana, więc po aktualizacji ekran powitalny i tak pokaże się ponownie.)*
2. Uruchom.
→ Widzisz ekran „Witaj w VivaPilot” z polem „Jestem pełnoletni (mam ukończone 18 lat) i akceptuję Regulamin. Zapoznałem się z Polityką prywatności.” Pod polem jest mała czcionka: „Bez akceptacji nie możesz korzystać z aplikacji. Jeśli po akceptacji przestaniesz zgadzać się z Regulaminem, zaprzestań korzystania z aplikacji i ją odinstaluj.” Przycisk „Rozpocznij” jest **szary**, dopóki nie zaznaczysz pola. Linki „Regulamin” i „Polityka prywatności” otwierają pełne teksty (1.12); u góry każdego jest linia „Wersja aplikacji i dokumentów: 1.12 (z dnia …)”, a Wstecz z tekstu wraca na ekran powitalny. Po zaznaczeniu pola i „Rozpocznij” wchodzisz do appki.

### Test 1a – Bez akceptacji appka nie działa
1. Na czystej instalacji uruchom appkę i **niczego nie zaznaczaj**.
→ Widać tylko ekran powitalny – żadnego innego ekranu, brak kanałów, brak ładowania list, brak okna zakupu/reklam.
2. (Opcjonalnie, dla pewności) Przy wyłączonym Wi-Fi i danych oraz przy włączonym – ekran wygląda tak samo. Jeśli masz narzędzie do podglądu ruchu sieciowego (np. PCAPdroid, bez roota), sprawdź, że appka **nie wysyła nic** do czasu naciśnięcia „Rozpocznij”.
3. Naciśnij **Wstecz** → appka się zamyka.
4. Uruchom ponownie → znów ekran powitalny (nic nie zostało zapisane).
5. Naciśnij **„Nie akceptuję – zamknij aplikację”** → appka się zamyka; po ponownym uruchomieniu znów ekran powitalny.
6. Zaznacz pole i naciśnij „Rozpocznij” → dopiero teraz startuje wczytywanie list, połączenie z Google Play i reszta.
7. Na Android TV przejdź pilotem: pole → Regulamin → Polityka → „Rozpocznij” → „Nie akceptuję…”; wszystko ma być podświetlane i wybierane OK.

### Test 2 – Koniec okresu próbnego (opcja debug)
**Panel (łatwiej):** pomarańczowe „TEST” → 2. CZAS → „+5 dni”; potem spróbuj wyszukać. Poniżej stara metoda z Ustawień (nadal działa).

1. Zainstaluj build **debug**. W Ustawieniach, w sekcji z dziennikiem awarii, jest linia „DEBUG: przesunięcie zegara próby o X dni” z przyciskami „+1 dzień” i „Reset”. Kliknij „+1 dzień” 5 razy. Ta opcja istnieje tylko w buildzie debug – w release jej nie ma.
2. Spróbuj użyć wyszukiwania / Moich list / pobierania.
→ Pojawia się okno „Okres próbny zakończony” z przyciskami „Kup Pełną wersję” i „Nie teraz”. Wyszukiwanie, Moje listy i pobieranie są zablokowane. Oglądanie kanałów/filmów/seriali i przeglądanie działają **bez ograniczeń**.

### Test 3 – Zakup testowy (konto testera licencji)
**Panel (łatwiej):** zakup przejdziesz do końca bez konta testera – „Przejdź do płatności” pokaże atrapę okna płatności (Scenariusz 4 w `TESTY_NA_TELEFONIE.md`). Poniżej wersja z prawdziwym kontem testera (na wersji z Google Play).

1. Kliknij „Kup Pełną wersję”.
→ Ekran „Pełna wersja VivaPilot” z dwoma **pustymi** polami (pole 1: „Rozumiem i osobno akceptuję, że działanie odtwarzania i katalogu…”, pole 2: „Wyrażam zgodę na natychmiastowe rozpoczęcie świadczenia…”) i linią „Wydawca: Damian Gałka, adres przedsiębiorstwa i do korespondencji: ul. Przybyszewskiego 209B/9, 93-120 Łódź, dg.komunikacja@gmail.com” (widoczną zawsze, niezależnie od zaznaczeń). „Przejdź do płatności” jest nieaktywne.
2. Zaznacz tylko jedno pole → przycisk nadal nieaktywny (sprawdź pole 1 samo i pole 2 samo). Zaznacz oba → aktywny. Pod przyciskiem jest drobny druk „Klikając „Przejdź do płatności”, potwierdzasz, że zapoznałeś się z Regulaminem i Polityką prywatności. Płatność obsługuje Google Play. Szczegóły: § 4, § 8 i § 9 Regulaminu.” – słowa „Regulaminem” i „Polityką prywatności” są linkami (otwierają dokumenty; Wstecz wraca na ekran zakupu).
3. „Przejdź do płatności” → okno płatności Google Play. (Sprawdź też „Anuluj” – wraca bez płatności.)
4. Dokończ zakup testowy.
→ Funkcje odblokowane od razu. Ekran „Pełna wersja odblokowana” z numerem zamówienia, datą, linią wydawcy, tekstem „Przed zakupem: (1)… (2)…” i wersją Regulaminu. „Zapisz potwierdzenie” otwiera okno wyboru miejsca, domyślna nazwa `VivaPilot_potwierdzenie_zakupu.txt`; plik zawiera potwierdzenie + tekst Regulaminu. „Pobierz potwierdzenie zgód” zapisuje `VivaPilot_potwierdzenie_zgod.txt` (zdarzenia zgody, w tym numer zamówienia).
5. W Play Console / w Ustawieniach sprawdź, że zakup jest potwierdzony (acknowledged) – nie wraca zwrot po 3 dniach.
6. Przy zakupie w stanie „oczekujący” (PENDING) funkcje **nie** odblokowują się do czasu zatwierdzenia płatności.

### Test 4 – Zwrot zakupu
**Panel:** TEST → 3. ZAKUP → „ZWROT / brak zakupu” (Scenariusz 6).

1. W Play Console zrób zwrot testowego zakupu.
2. Zamknij appkę całkowicie i uruchom ponownie (ewentualnie Ustawienia › „Sprawdź zakup ponownie”).
→ Pełna wersja ponownie **zablokowana**.

### Test 5 – Reinstalacja / nowe urządzenie
**Panel:** TEST → 3. ZAKUP → „Ustaw: ZAKUP PRZYWRÓCONY” (Scenariusz 7).

1. Z kupioną wersją odinstaluj appkę i zainstaluj ponownie (albo zainstaluj na innym urządzeniu z tym samym kontem Google).
→ Po uruchomieniu zakup jest przywrócony (w razie czego: „Sprawdź zakup ponownie”).

### Test 6 – Wariant `ads`
Telefon:
1. Zainstaluj `app-ads-...apk`, przesuń zegar próby (debug), nie kupuj.
→ Najpierw krótka informacja o reklamach z przyciskiem „Dalej”, potem okno zgody Google (UMP). Uwaga: Google pokazuje formularz tylko użytkownikom z UE/UK albo zarejestrowanym urządzeniom testowym – jak to wymusić na swoim telefonie jest opisane w komentarzu w `AdsController.kt`, funkcja `requestConsentAndInitIfNeeded`.
2. Po zgodzie: baner nad dolnym paskiem na ekranach przeglądania; reklama pełnoekranowa co jakiś czas przy zmianie zakładki (min. 90 s odstępu).
→ **Brak** reklam w odtwarzaczu i w trybie obraz-w-obrazie. Po zakupie reklamy znikają natychmiast.
→ Ustawienia › Informacje prawne: wiersz „Prywatność reklam” otwiera ponownie formularz Google.

Android TV:
→ **Brak** reklam, brak okna zgody, SDK reklam się nie uruchamia.

### Test 7 – Wariant `noads`: brak uprawnienia AD_ID
1. Zbuduj `noads` i sprawdź scalony manifest. Sposób bez Android Studio: po buildzie rozpakuj APK i sprawdź plik manifestu narzędziem `aapt dump permissions app-noads-debug.apk` (lub wrzuć APK do dowolnego analizatora APK online).
→ Na liście **nie ma** `com.google.android.gms.permission.AD_ID`.
(W wariancie `ads` ten wpis jest – to prawidłowe.)

### Test 8 – Brak połączeń z TMDB
→ W Ustawieniach nie ma żadnego pola TMDB. W kodzie nie ma adresów `themoviedb.org` ani `tmdb.org` (sprawdzone przy tym wydaniu: jedyne adresy to schematy Androida, przykład „http://host” w podpowiedzi, link do VLC w Sklepie Play i zaślepka strony prawnej). Plakaty i opisy pochodzą wyłącznie z Twojego źródła.

### Test 9 – Pilot (D-pad) na wszystkich nowych ekranach
Na Android TV (albo emulatorze TV) przejdź samym pilotem (strzałki + OK + Wstecz):
- ekran powitalny: checkbox → link Regulamin → link Polityka → „Rozpocznij” → „Nie akceptuję – zamknij aplikację”
- ekran przed zakupem: pole 1 → pole 2 → „Anuluj” → „Przejdź do płatności” → (pod przyciskiem, tylko na TV) wiersze „Regulamin” i „Polityka prywatności”
- ekran potwierdzenia zakupu: „Pobierz potwierdzenie zgód” → „Zapisz potwierdzenie” → „Gotowe”
- Moje zgody: „Pobierz potwierdzenie zgód” → „Wstecz”
- Ustawienia › Informacje prawne: wszystkie wiersze i przyciski
- ostrzeżenia (Wyślij do TV, eksport kopii, wyślij log, status „Nieudane” w Pobranych)
→ Każdy element da się podświetlić i wybrać pilotem; Wstecz zamyka ekran (na ekranie powitalnym zamyka całą appkę).

### Test 10 – Numer wersji
→ Ustawienia › Informacje prawne: wiersz „Wersja aplikacji i dokumentów: 1.12”. Ten sam numer u góry tekstu Regulaminu i Polityki. W informacjach o appce w telefonie (Ustawienia systemu › Aplikacje › VivaPilot) wersja 1.12.

### Test 11 – Moje zgody (rejestr zgód)
1. Po zaakceptowaniu ekranu powitalnego wejdź w Ustawienia › Informacje prawne › **Moje zgody**.
→ Widzisz „Zdarzenie 1: akceptacja na ekranie powitalnym”: identyfikator zdarzenia, datę i godzinę (UTC), „Wersja aplikacji: 1.12”, „Regulamin – wersja: 1.12_PL”, „Polityka prywatności – wersja: 1.12_PL”, „Platforma: GOOGLE_PLAY”, „Oświadczenie o pełnoletności: złożone, [godzina]” i „Akceptacja Regulaminu i zapoznanie się z Polityką prywatności: [godzina]”. Niżej: „Brak zdarzenia zakupu – w tej instalacji nie dokonano zakupu.”
→ Pod zapisem tekst: „Zapisu nie można zmienić. Jeśli przestajesz zgadzać się z Regulaminem, zaprzestań korzystania z aplikacji i ją odinstaluj.” Przyciski: „Pobierz potwierdzenie zgód” i „Wstecz” – **nie ma** żadnego przycisku edycji, usuwania ani cofania zgody (ani tu, ani nigdzie indziej w appce).
2. Zamknij appkę i uruchom ponownie → zdarzenie 1 ma **ten sam** identyfikator i godzinę (zapis powstaje raz).
3. Zrób zakup testowy (Test 3) i wejdź ponownie w „Moje zgody”.
→ Dochodzi „Zdarzenie 2: zakup Pełnej wersji” z numerem zamówienia (takim samym jak na ekranie potwierdzenia), wersjami 1.12_PL oraz dwiema **osobnymi godzinami**: „Odrębna akceptacja: działanie funkcji zależnych od źródła treści…” (pole 1) i „Zgoda na natychmiastowe rozpoczęcie świadczenia…” (pole 2) – zwykle różnią się o kilka sekund.
4. Naciśnij „Pobierz potwierdzenie zgód”, wybierz miejsce zapisu.
→ Plik `VivaPilot_potwierdzenie_zgod.txt` zawiera na górze czytelny opis obu zdarzeń (w języku aplikacji), na dole ten sam rejestr jako JSON (pola: `consent_event_id`, `timestamp_utc`, `app_version`, `terms_version`, `privacy_policy_version`, `platform`, `store_transaction_id`, `consents`). Komunikat „Potwierdzenie zgód zapisane.”
5. Odinstaluj i zainstaluj appkę ponownie na tym samym koncie z kupioną wersją (Test 5) i zaakceptuj ekran powitalny.
→ Zakup jest przywrócony, a w „Moje zgody” zdarzenie 2 ma opis „zakup wykryty na koncie Google (bez ekranu przed zakupem w tej instalacji)”, numer zamówienia i **bez** zgód zakupowych (w tej instalacji nikt ich nie składał). Ekran „Pełna wersja odblokowana” **nie** wyskakuje sam po takim przywróceniu (jest dostępny w Ustawieniach › „Pokaż potwierdzenie”).

### Test 12 – Rejestr zgód nie jest nigdzie wysyłany i jest zaszyfrowany
→ Rejestr leży w pliku `consent_vault.bin` w prywatnej pamięci appki (nie w pamięci publicznej i nie w pamięci podręcznej), zaszyfrowany AES-256-GCM kluczem z Android Keystore – po zwykłym „Wyczyść pamięć podręczną” (Ustawienia systemu › Aplikacje › VivaPilot › Pamięć) zgody **zostają** (appka nie pokazuje znów ekranu powitalnego). „Wyczyść dane”/odinstalowanie usuwa je razem z kluczem. Plik jest wyłączony z automatycznej kopii zapasowej Androida (pliki `res/xml/backup_rules.xml` i `data_extraction_rules.xml`) i nie wchodzi do kopii z Ustawień (eksport obejmuje tylko playlisty, ulubione, kategorie, postęp i listy). Skutek: po przywróceniu appki z kopii Androida na nowym telefonie ekran powitalny pokaże się ponownie – to celowe (nowa instalacja = nowa akceptacja). Z appki nic nie jest wysyłane – plik z „Pobierz potwierdzenie zgód” trafia tylko tam, gdzie sam go zapiszesz.

### Test 13 – Notka o wersji wiążącej (inny język niż polski)
1. Ustaw język appki na inny niż polski (np. angielski) w Ustawieniach.
2. Otwórz Regulamin (z ekranu powitalnego albo z Ustawień › Informacje prawne), potem Politykę prywatności.
→ Pod tytułem i linią „Wersja aplikacji i dokumentów: 1.12 (z dnia …)”, a **nad** tekstem dokumentu jest notka w wybranym języku, np. po angielsku: „The Polish version of the document is the binding one. Translations (if any) are provided only to help you understand it.” (po polsku: „Wiążąca jest polska wersja dokumentu. Tłumaczenia (jeśli są) służą ułatwieniu zrozumienia.”).
3. Przełącz język na polski i otwórz te same dokumenty.
→ Notki **nie ma**.
4. (Przy kupionej wersji, język inny niż polski) „Zapisz potwierdzenie” → w zapisanym pliku ta sama notka stoi tuż nad tekstem Regulaminu.

### Test 14 – Weryfikacja zakupu przy uruchomieniu (kontrola z wersji 1.04)
**Panel:** pkt 1 i 2 = Scenariusze 5 i 6 w `TESTY_NA_TELEFONIE.md`; pkt 3 (brak internetu) wymaga prawdziwego Google Play.

1. Z kupioną wersją (konto testera) zamknij appkę całkowicie i uruchom ponownie → Ustawienia › Informacje prawne › Zakup pokazuje „kupiona” (po chwili, gdy Google Play odpowie).
2. Zrób zwrot w Play Console i uruchom appkę ponownie → po sprawdzeniu zakup znika, funkcje Pełnej wersji (wyszukiwanie, Moje listy, pobieranie) są zablokowane, jeśli okres próbny minął.
3. Wyłącz internet, uruchom appkę z kupioną wersją, potem włącz internet i w Ustawieniach naciśnij „Sprawdź zakup ponownie” → zakup zostaje wykryty (od 1.04 przycisk sam nawiązuje połączenie z Google Play od nowa, wcześniej w takiej sytuacji nic nie robił).

### Test 15 – Nazwa VivaPilot i dane wydawcy (punkty 0 i 5 promptu)
1. Nazwa pod ikoną i na liście aplikacji: **VivaPilot**. Ekran startowy, baner Android TV, ekran powitalny, Ustawienia (logo na górze), Informacje prawne i ekran potwierdzenia pokazują nową nazwę/logo.
2. Linia „Wydawca: Damian Gałka, adres przedsiębiorstwa i do korespondencji: ul. Przybyszewskiego 209B/9, 93-120 Łódź, dg.komunikacja@gmail.com” jest w trzech miejscach: ekran przed zakupem, ekran potwierdzenia zakupu, Ustawienia › Informacje prawne › (Wydawca i kontakt). Wpis o libVLC (Ustawienia › Informacje prawne) mówi „…wysłane na dg.komunikacja@gmail.com…”.
3. **Nigdzie** nie ma starego adresu (skrytki pocztowej) ani starego e-maila – sprawdź także oba dokumenty prawne.
4. Kontrola w kodzie (zrobiona przy tym wydaniu): wyszukanie starej nazwy (bez rozróżniania wielkości liter) w całym projekcie nie zwraca wyników.

### Dodatkowo – ostrzeżenia z części 8
- „Wyślij do TV”: ostrzeżenie pojawia się **tylko przy pierwszym użyciu**, przycisk „Rozumiem”.
- Eksport kopii: ostrzeżenie **za każdym razem**, „Eksportuj” / „Anuluj”.
- Wyślij log awarii: ostrzeżenie **za każdym razem**, „Udostępnij” / „Anuluj”.
- Pobieranie: kliknij czerwony status „Nieudane” w ekranie Pobrane → okno z wyjaśnieniem i „OK”.

### Dodatkowo – zmiana Regulaminu (część 10)
**Panel:** TEST → 4. ZGODY I OSTRZEŻENIA → „Udawaj, że Regulamin i Polityka się zmieniły” (Scenariusz 8) – bez przebudowy appki. Poniżej metoda „prawdziwa”.

1. Zainstaluj wersję, zaakceptuj ekran powitalny.
2. Podnieś `WERSJA` w `regulamin.txt` (np. o 0,01 w górę, razem z `versionName` w `app/build.gradle.kts`), uzupełnij `DATA_WEJSCIA_W_ZYCIE` i `ZMIANY`, zbuduj i zainstaluj **jako aktualizację** (bez odinstalowywania).
→ Raz pojawia się „Zmieniamy Regulamin od [data]. Najważniejsze zmiany: [z pliku]…” z przyciskami „Przeczytaj” i „OK”. Po „OK” nie wraca.

---

## 5. Ograniczenia, o których warto wiedzieć

- **Panel testowy (z wersji 1.05, dostosowany do 1.12) jest tymczasowy.** Istnieje tylko w buildach `debug` (osobny katalog `app/src/debug`); w `release` jest pusty odpowiednik, więc nie da się go wypuścić do Google Play przez pomyłkę. Gdy skończysz testy, napisz „usuń moduł testowy” – usunę go i podniosę wersję (zasada +0,01). Symulacja płatności używa tej samej drogi co prawdziwy zakup, ale **nie zastępuje** jednego testu na prawdziwym Google Play (konto testera licencji, wersja z Google Play) przed publikacją.
- **Nie mogłem skompilować appki w tym środowisku** (brak Android SDK). Wszystko sprawdzone statycznie (nawiasy, importy, klucze tłumaczeń we wszystkich 7 językach). Pierwszy build w GitHub Actions (20.10) wykazał błąd wariantu `ads` (niezgodność wersji Kotlina z biblioteką reklam) – poprawiony, patrz sekcja 3, ale poprawka też nie była kompilowana. Wariant `noads` w tamtym logu w ogóle nie został jeszcze zbudowany, więc mogą w nim wyjść kolejne drobne błędy – jeśli tak, wklej mi log błędu, poprawię od razu.
- Plik „Zapisz potwierdzenie” zawiera **aktualnie wbudowany** tekst Regulaminu. Jeśli po zakupie wyjdzie nowsza wersja Regulaminu, w pliku znajdzie się o tym adnotacja (numer wersji z dnia zakupu jest zapamiętany osobno). Pełnego archiwum starych wersji appka nie przechowuje – jeśli chcesz, to osobny krok (np. dopisywanie starych wersji do `assets/legal`).
- SDK reklam i okno zgody Google w ścieżce `ads` zostały napisane wg oficjalnej dokumentacji Google (Mobile Ads 25.5.0, UMP 4.0.0), ale nie były uruchamiane – test 6 jest tu kluczowy.
- **Wynik kontroli weryfikacji zakupu (1.04):** zakup jest sprawdzany w Google Play (`queryPurchasesAsync`) przy każdym uruchomieniu – zaraz po nawiązaniu połączenia z Billing (`BillingManager.start`, wołane z `IptvApp` dla użytkownika, który zaakceptował ekran powitalny) – oraz na żądanie z przycisku „Sprawdź zakup ponownie”. **Nie ma żadnej lokalnej flagi odblokowującej Pełną wersję:** jedyne źródło prawdy to pole `isPurchased` w pamięci operacyjnej (po każdym starcie zaczyna od „niekupione”, ustawiane wyłącznie na podstawie odpowiedzi Google Play ze stanem PURCHASED dla produktu `vivapilot_full_version`); `TrialGate`, wyszukiwanie, Moje listy, pobieranie, reklamy i ekrany Ustawień czytają tylko to pole. Dane zapisane na dysku (rejestr zgód: numer zamówienia w zdarzeniu zakupu; migawka Regulaminu) służą tylko do wyświetlania i do jednorazowości zapisu, nigdy do odblokowania. PENDING nie odblokowuje; brak produktu na liście po pełnym sprawdzeniu (np. zwrot) blokuje ponownie. Przesunięcie zegara próby istnieje tylko w buildzie debug i wpływa wyłącznie na okres próbny, nie na zakup. **Znaleziona luka (naprawiona):** gdy połączenie z Google Play nie powiodło się przy starcie albo zostało zerwane, „Sprawdź zakup ponownie” po cichu nic nie robiło – teraz łączy się od nowa i dopiero wtedy sprawdza. **Do wiedzenia:** weryfikacja odbywa się na urządzeniu (appka nie ma własnego serwera, więc nie sprawdza podpisu zakupu po stronie serwera); zakup bez połączenia z usługą Google Play na urządzeniu nie zostanie rozpoznany, a po okresie próbnym funkcje pozostaną zablokowane do czasu udanego sprawdzenia (Google Play zwykle odpowiada z własnej pamięci podręcznej, więc często działa to też bez internetu).
- **Naprawiony błąd z wersji 1.01:** ekran „Pełna wersja odblokowana” i zapis Regulaminu „z dnia zakupu” odpalały się przy KAŻDYM uruchomieniu kupionej appki (stan zakupu liczy się od nowa po każdym starcie). Od 1.02 dzieje się to dokładnie raz dla danego numeru zamówienia, a ekran potwierdzenia pokazuje się sam tylko po zakupie zrobionym przez ekran „przed zakupem”.
- W wariancie `ads` do manifestu dodano wpis opóźniający start pomiarów SDK reklam do ręcznej inicjalizacji (po akceptacji, po próbie i po zgodzie Google). Przy teście 1a w wariancie `ads` warto sprawdzić ruch sieciowy szczególnie uważnie – nie mogłem tego uruchomić.
- Regulamin i Polityka w appce to **Twoje teksty 1.12** (wersja dla Google Play, wklejone bez zmian treści). Zostały w nich miejsca, które musisz sam rozstrzygnąć przed publikacją: „[ADRES STRONY]” oraz sformułowanie „Google lub Samsunga” w § 4 ust. 4 Regulaminu.
- **Rejestr zgód z 1.05 nie jest przenoszony** do 1.12 (nowy format i nowy identyfikator aplikacji) – każdy użytkownik zaakceptuje ekran powitalny 1.12 od nowa.
- **Wyślij do TV:** nazwa usługi wykrywania w sieci zmieniła się razem z nazwą aplikacji, więc 1.12 nie zobaczy telewizora z wersją 1.05 i odwrotnie (po instalacji 1.12 na obu urządzeniach działa normalnie).
- Ikony startowe (symbol bez napisu) pozostały bez zmian; wymienione zostały logo z napisem, ekran startowy i baner TV.
