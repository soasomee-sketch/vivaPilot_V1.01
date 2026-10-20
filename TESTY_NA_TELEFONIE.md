# VivaPilot 1.12 – testy na telefonie z panelem testowym

Ten plik jest dla Ciebie, bez żargonu. Wersja **1.12** to aplikacja **VivaPilot** (nowa nazwa) z nowymi tekstami prawnymi + **tymczasowy panel testowy**, dzięki któremu wszystko sprawdzisz **samymi przyciskami na telefonie** – bez Play Console, bez kont testerów, bez czekania 5 dni.

---

## 1. Co zainstalować

Z GitHub Actions pobierz plik APK (ten sam sposób co zwykle):

- `app-noads-debug.apk` – zwykła wersja (bez reklam) – **zacznij od niej**,
- `app-ads-debug.apk` – wersja z reklamami (tylko do testu reklam).

**Ważne – nowy identyfikator aplikacji.** VivaPilot ma teraz identyfikator `com.vivapilot.iptv` (wcześniej `com.iptvplayer.app`). Telefon traktuje go jako **zupełnie nową aplikację**: instalacja **nie nadpisze** starej wersji 1.05 – obie będą obok siebie. **Odinstaluj starą aplikację** (ta ze starą nazwą pod ikoną), żeby ich nie mylić. Nowa aplikacja startuje od zera: ekran powitalny, nowy okres próbny, puste listy (playlisty dodasz ponownie albo wczytasz z kopii zapasowej z Ustawień).

> Zgody z wersji 1.05 **nie** przechodzą do 1.12 – rejestr zgód jest nowy (zaszyfrowany), więc zobaczysz ekran powitalny i zaakceptujesz Regulamin 1.12 od nowa. To zamierzone.

**Jak poznać, że to wersja testowa:** przy prawej krawędzi ekranu, mniej więcej w połowie wysokości, jest mały **pomarańczowy znacznik „TEST”**. Dotknij go – otwiera się **panel testowy**. W wersji, którą wrzucisz do Google Play, tego znacznika **nie będzie** (kod panelu w ogóle się tam nie kompiluje).

---

## 2. Jak czytać panel

Panel ma 6 części (przewijasz palcem w dół):

| Część | Do czego służy |
|---|---|
| **1. STAN** | Podgląd, co aplikacja teraz „wie”: dzień okresu próbnego, czy kupione, jakie zdarzenia zgody są zapisane w rejestrze, wersje dokumentów. Zawsze tu zajrzyj, żeby sprawdzić wynik testu. |
| **2. CZAS** | Przyspieszanie okresu próbnego: +1 / +4 / +5 / +30 dni, cofnięcie czasu, start próby od nowa. |
| **3. ZAKUP** | Symulacja płatności: zakup „jak użytkownik”, płatność oczekująca, przywrócony zakup, zwrot. |
| **4. ZGODY I OSTRZEŻENIA** | Czyszczenie zgód (ekran powitalny wraca), udawanie zmiany Regulaminu, ponowne pokazanie ostrzeżeń. |
| **5. GOTOWE SCENARIUSZE** | Jedno kliknięcie = „ostatni dzień próby” albo „próba skończona”. |
| **6. APLIKACJA** | Zamknięcie aplikacji całkiem; wyczyszczenie wszystkiego (jak świeża instalacja). |

Po każdym naciśnięciu przycisku u góry panelu pojawia się zielony napis **✔ co zostało zrobione**. Niektóre przyciski **same zamykają aplikację** (napisane jest to przy nich) – wtedy po prostu **otwórz ją ponownie z ikony**. To jest dokładnie to samo, co „zamknięcie aplikacji i uruchomienie na nowo” u prawdziwego użytkownika.

**Symulowana płatność:** aplikacja zainstalowana z pliku nie może kupować w Google Play, więc przy „Przejdź do płatności” zobaczysz **atrapę okna płatności** (napis „SYMULACJA okna płatności Google Play”) z wyborem: *Zapłać – sukces* / *Płatność oczekująca* / *Anuluj*. Żadne pieniądze nie są pobierane. Wszystko po stronie aplikacji (ekran przed zakupem, zapis zgód do rejestru, ekran potwierdzenia, odblokowanie funkcji) działa **tak samo jak przy prawdziwym zakupie**.

---

## 3. Scenariusze – „kliknij to, zobacz tamto”

Rób je po kolei – każdy zaczyna się od stanu, w którym skończył poprzedni (gdzie trzeba zacząć od zera, jest to napisane).

### Scenariusz 1 – Świeży użytkownik, ekran powitalny *(Test 1 i 1a)*
1. Dotknij **TEST** → przewiń do **6. APLIKACJA** → **„Zupełnie nowy użytkownik – WYCZYŚĆ WSZYSTKO”** → potwierdź. Aplikacja się zamknie.
2. Otwórz aplikację z ikony.
   → **Tylko** ekran „Witaj w VivaPilot”. Przycisk „Rozpocznij” szary. Pod polem mała czcionka „Bez akceptacji nie możesz korzystać z aplikacji…”. Żadnych list, kanałów ani okna zakupu.
3. Naciśnij **Wstecz** → aplikacja się zamyka. Otwórz znów → znów ekran powitalny.
4. Naciśnij „Nie akceptuję – zamknij aplikację” → zamyka się. Otwórz znów → znów ekran powitalny.
5. Dotknij „Regulamin” i „Polityka prywatności” → otwierają się teksty (Regulamin i Polityka w wersji 1.12) z linią „Wersja aplikacji i dokumentów: 1.12”; Wstecz wraca na ekran powitalny.
6. Zaznacz pole → „Rozpocznij” robi się aktywne → naciśnij. Wchodzisz do aplikacji.
7. Dotknij **TEST** → część **1. STAN** → „Rejestr zgód (zaszyfrowany)” pokazuje jedno zdarzenie: ekran powitalny z datą (UTC), Regulamin 1.12_PL, Polityka 1.12_PL, 18+: tak.

### Scenariusz 2 – Podgląd „Moje zgody” i pobranie potwierdzenia *(Test 11)*
1. Ustawienia › Informacje prawne › **Moje zgody** → „Zdarzenie 1: akceptacja na ekranie powitalnym”: data i godzina (UTC), wersja aplikacji 1.12, Regulamin 1.12_PL, Polityka 1.12_PL, platforma GOOGLE_PLAY, „Oświadczenie o pełnoletności: złożone” i „Akceptacja Regulaminu…” (każde z własną godziną) oraz identyfikator zdarzenia. Niżej: „Brak zdarzenia zakupu…”.
2. Pod zapisem jest tekst „**Zapisu nie można zmienić. Jeśli przestajesz zgadzać się z Regulaminem, zaprzestań korzystania z aplikacji i ją odinstaluj.**”. Ekran jest tylko do odczytu – nie ma żadnego przycisku edycji, cofnięcia ani kasowania (są tylko „Pobierz potwierdzenie zgód” i „Wstecz”).
3. Naciśnij **„Pobierz potwierdzenie zgód”** → system pyta, gdzie zapisać plik (`VivaPilot_potwierdzenie_zgod.txt`). Zapisz i otwórz plik w telefonie: na górze czytelny opis zdarzeń (po polsku, w języku aplikacji), na dole ten sam zapis w formacie JSON. Pojawia się komunikat „Potwierdzenie zgód zapisane.”.

### Scenariusz 3 – Koniec okresu próbnego *(Test 2)*
1. **TEST** → **2. CZAS** → **„+5 dni”**. W STAN: „Okres próbny: MINĄŁ”.
2. Zamknij panel („Zamknij panel” albo Wstecz). Spróbuj użyć wyszukiwania, „Moich list” albo pobierania.
   → Okno „Okres próbny zakończony” z „Kup Pełną wersję” i „Nie teraz”. Te trzy funkcje są zablokowane.
3. Oglądanie kanałów/filmów/seriali i przeglądanie działają **bez ograniczeń**.
4. Wariant krok po kroku: **TEST** → **2. CZAS** → „Zacznij okres próbny od nowa”, potem „+1 dzień” kilka razy i patrz w STAN: „TRWA – dzień 2 z 5”, „dzień 3 z 5”… aż „MINĄŁ”.

### Scenariusz 4 – Zakup od początku do końca *(Test 3)*
Zacznij ze stanu po Scenariuszu 3 (próba skończona, okno zakupu widoczne).
1. Kliknij **„Kup Pełną wersję”**.
   → Ekran „Pełna wersja VivaPilot”: **dwa puste pola** i zawsze widoczna linia „Wydawca: Damian Gałka, adres przedsiębiorstwa i do korespondencji: ul. Przybyszewskiego 209B/9, 93-120 Łódź, dg.komunikacja@gmail.com”. „Przejdź do płatności” nieaktywne.
   Pole 1 zaczyna się od „Rozumiem i osobno akceptuję, że działanie odtwarzania i katalogu…”, pole 2 od „Wyrażam zgodę na natychmiastowe rozpoczęcie świadczenia…”.
2. Zaznacz tylko jedno pole → nadal nieaktywne (sprawdź oba warianty: samo pole 1 i samo pole 2). Zaznacz oba → aktywne.
   **Pod przyciskiem** „Przejdź do płatności” jest drobny druk: „Klikając „Przejdź do płatności”, potwierdzasz, że zapoznałeś się z Regulaminem i Polityką prywatności. Płatność obsługuje Google Play. Szczegóły: § 4, § 8 i § 9 Regulaminu.” Słowa **Regulaminem** i **Polityką prywatności** są podkreślone i klikalne – dotknij każde: otwiera się odpowiedni dokument, Wstecz wraca na ekran zakupu (pola zostają zaznaczone).
3. Naciśnij **„Przejdź do płatności”** → pojawia się atrapa okna płatności.
4. Najpierw wybierz **„Anuluj”** → wracasz bez zakupu (STAN: „brak zakupu”, funkcje nadal zablokowane).
5. Powtórz 1–3 i wybierz **„Płatność oczekująca”** → STAN: „OCZEKUJE na potwierdzenie płatności”, funkcje **nadal zablokowane** (płatność niezatwierdzona nie odblokowuje).
6. Powtórz 1–3 i wybierz **„Zapłać – sukces”**.
   → Pełna wersja odblokowuje się **od razu**. Ekran „Pełna wersja odblokowana” pokazuje numer zamówienia (zaczyna się od `GPA.TEST-`), datę, linię „Wydawca: …dg.komunikacja@gmail.com”, tekst „Przed zakupem: (1) odrębnie zaakceptowałeś… (Regulamin § 4 ust. 4–5); (2) wyraziłeś zgodę na natychmiastowe rozpoczęcie świadczenia… (art. 38 ust. 1 pkt 13 ustawy o prawach konsumenta).” oraz wersję Regulaminu. Są dwa przyciski zapisu: „Zapisz potwierdzenie” (plik `VivaPilot_potwierdzenie_zakupu.txt`: potwierdzenie + tekst Regulaminu) i **„Pobierz potwierdzenie zgód”** (plik `VivaPilot_potwierdzenie_zgod.txt`). Wypróbuj oba.
7. Zamknij ekran. Wyszukiwanie, Moje listy i pobieranie działają. STAN: „KUPIONE”, a w „Rejestrze zgód” są **dwa zdarzenia** (ekran powitalny i zakup z numerem zamówienia).
8. Ustawienia › Informacje prawne › **Moje zgody** → „Zdarzenie 2: zakup Pełnej wersji”: numer zamówienia (transakcji), Regulamin/Polityka 1.12_PL oraz **dwie osobne godziny** zgód: „Odrębna akceptacja: działanie funkcji zależnych od źródła treści…” i „Zgoda na natychmiastowe rozpoczęcie świadczenia…”. Przycisk „Pobierz potwierdzenie zgód” zapisuje teraz oba zdarzenia (sprawdź w pliku: sekcja „Zdarzenie 2” z numerem zamówienia i JSON z `store_transaction_id`).

### Scenariusz 5 – Zamknięcie i ponowne otwarcie po zakupie *(Test 14, pkt 1)*
1. **TEST** → **6. APLIKACJA** → **„Zamknij aplikację całkiem”**, potem otwórz z ikony.
   → Aplikacja od razu jest „kupiona” (STAN: KUPIONE), funkcje działają, **ekran potwierdzenia NIE wyskakuje ponownie** (pokazuje się tylko raz, zaraz po zakupie).
2. „Moje zgody” nadal pokazują zdarzenie zakupu (te same godziny i numer – nic się nie nadpisało, nie ma też trzeciego zdarzenia).
3. Ustawienia › Informacje prawne › Zakup › „Pokaż potwierdzenie” (albo w panelu **3. ZAKUP** → „Pokaż ekran potwierdzenia zakupu”) → ekran potwierdzenia na żądanie.

### Scenariusz 6 – Zwrot *(Test 4 i Test 14, pkt 2)*
1. **TEST** → **3. ZAKUP** → **„ZWROT / brak zakupu”**. STAN: „brak zakupu”.
2. Zamknij panel i spróbuj wyszukać.
   → Pełna wersja znów **zablokowana** (okno zakupu), bo okres próbny minął.
3. Opcjonalnie: **6. APLIKACJA** → „Zamknij aplikację całkiem”, otwórz ponownie → nadal brak zakupu (zwrot „przetrwał” restart). W „Moje zgody” stare zdarzenie zakupu **zostaje** – to historia, nie odblokowanie.

### Scenariusz 7 – Zakup przywrócony po reinstalacji / na nowym urządzeniu *(Test 5)*
1. **TEST** → **3. ZAKUP** → **„Ustaw: ZAKUP PRZYWRÓCONY”**. (Udaje, że Google Play mówi „to konto już kiedyś kupiło” – bez ekranu przed zakupem.)
   → STAN: KUPIONE, funkcje odblokowane, ekran potwierdzenia **nie** wyskakuje sam.
2. Ustawienia › Informacje prawne › Zakup › „Pokaż potwierdzenie” – ekran dostępny na żądanie.
3. „Moje zgody” pokazują wtedy zdarzenie „zakup wykryty na koncie Google (bez ekranu przed zakupem w tej instalacji)” – z numerem zamówienia, ale **bez** zgód zakupowych (w tej instalacji nikt ich nie składał).
4. Przycisk **„Sprawdź zakup ponownie”** (w panelu lub w Ustawieniach) – bez błędu, zakup nadal wykryty.

### Scenariusz 8 – Zmiana Regulaminu *(część 10)*
1. **TEST** → **4. ZGODY I OSTRZEŻENIA** → **„Udawaj, że Regulamin i Polityka się zmieniły…”**. Aplikacja się zamknie.
2. Otwórz z ikony.
   → Raz pojawia się „Zmieniamy Regulamin od [data]. Najważniejsze zmiany: …” (data i opis zmian pochodzą z nagłówka pliku z Regulaminem 1.12) z „Przeczytaj” i „OK”. Po „OK” **nie wraca** (zamknij i otwórz jeszcze raz, żeby się upewnić).

### Scenariusz 9 – Ostrzeżenia *(część 8)*
- „Wyślij do TV” (rzutowanie): ostrzeżenie tylko przy pierwszym użyciu, przycisk „Rozumiem”. Żeby zobaczyć je znowu: **TEST** → **4.** → „Pokaż znów ostrzeżenie o rzutowaniu”.
- Eksport kopii zapasowej: ostrzeżenie **za każdym razem**, „Eksportuj” / „Anuluj”.
- „Wyślij log awarii”: ostrzeżenie **za każdym razem**, „Udostępnij” / „Anuluj”.
- Pobrane › czerwony status „Nieudane” → okno z wyjaśnieniem i „OK”.

### Scenariusz 10 – Wersja i język *(Test 10 i 13)*
- Ustawienia › Informacje prawne: „Wersja aplikacji i dokumentów: **1.12**”; ten sam numer na górze Regulaminu i Polityki; w panelu STAN: „Wersja aplikacji 1.12 (kod 6)”.
- Przełącz język aplikacji na np. angielski i otwórz Regulamin/Politykę → **nad** tekstem jest notka „The Polish version of the document is the binding one…”. Po polsku notki **nie ma**.

### Scenariusz 10a – Nazwa VivaPilot i dane wydawcy *(punkty 0 i 5)*
- Nazwa pod ikoną na telefonie: **VivaPilot**. Ekran startowy, baner na Android TV, ekran powitalny, Ustawienia › O aplikacji i „Informacje prawne” – wszędzie widać nowe logo/napis **VivaPilot**; nigdzie nie ma starej nazwy ani napisu „IPTV Player”.
- Linia „Wydawca: Damian Gałka, adres przedsiębiorstwa i do korespondencji: ul. Przybyszewskiego 209B/9, 93-120 Łódź, dg.komunikacja@gmail.com” jest w **trzech** miejscach: na ekranie przed zakupem, na ekranie potwierdzenia zakupu i w Ustawieniach › Informacje prawne (pod listą licencji). Starego adresu (skrytki pocztowej) ani starego e-maila **nie ma nigdzie** – sprawdź też teksty Regulaminu i Polityki oraz wpis o libVLC pod „Licencje” (e-mail w nim to dg.komunikacja@gmail.com).

### Scenariusz 11 – Skracanie dróg (gotowe scenariusze)
W części **5. GOTOWE SCENARIUSZE**:
- **„Ostatni dzień próby”** → aplikacja się zamyka; po otwarciu STAN: „TRWA – dzień 5 z 5”. Dodaj „+1 dzień” w CZAS i próba się kończy.
- **„Okres próbny SKOŃCZONY, brak zakupu”** → po otwarciu od razu stan z okna zakupu.

### Scenariusz 12 – Wariant `ads` *(Test 6, część dostępna na telefonie)*
Zainstaluj `app-ads-debug.apk`, zaakceptuj ekran powitalny, w panelu ustaw **2. CZAS → „+5 dni”** (brak zakupu). Sprawdź: baner nad dolnym paskiem na ekranach przeglądania, reklama pełnoekranowa co jakiś czas przy zmianie zakładki, **brak** reklam w odtwarzaczu. Potem zrób Scenariusz 4 (zakup) → **reklamy znikają natychmiast**. Wstęp o reklamach pokażesz znów przyciskiem w **4.** („Pokaż znów wstęp o reklamach”).

---

## 4. Czego panel NIE sprawdzi (i jak to zrobić inaczej)

- **Prawdziwa płatność Google Play** – wymaga instalacji przez Google Play (ścieżka testów wewnętrznych) i konta testera licencji. Panel ma przełącznik „Symulacja płatności włączona” (część 3) – po wyłączeniu aplikacja rozmawia z prawdziwym Google Play, ale z pliku APK kupić się nie da. Prawdziwy zakup testowy zrobisz dopiero na wersji z Google Play.
- **Okno zgody Google (UMP) w wersji `ads`** – Google pokazuje je tylko w UE/UK albo na zarejestrowanym urządzeniu testowym (opis w komentarzu w `AdsController.kt`).
- **Android TV / pilot (D-pad)** i **brak uprawnienia AD_ID w `noads`** – ten panel jest pod dotyk; pilota sprawdź na TV, a AD_ID narzędziem `aapt dump permissions` (Test 7 w głównym dokumencie).
- **Brak wysyłania czegokolwiek przed akceptacją** (Test 1a, pkt 2) – jeśli chcesz to udowodnić, użyj darmowej aplikacji PCAPdroid.

---

## 5. Jak to potem usunąć

Gdy skończysz testy, napisz mi **„usuń moduł testowy”**. Wtedy:
- kasuję katalog `app/src/debug/.../testtools` i pusty odpowiednik w `app/src/release/`,
- usuwam trzy wywołania modułu w `BillingManager.kt` i jedno w `MainActivity.kt`,
- usuwam pomocnicze funkcje testowe z `LocalStore.kt`,
- podnoszę wersję zgodnie z regułą (+0,01 i `versionCode` +1) i oddaję czystą paczkę.

Do tego czasu wersja przeznaczona do Google Play (`release`) **i tak nie zawiera ani linijki** kodu panelu – znacznik „TEST” istnieje wyłącznie w plikach `*-debug.apk`.

---

## 6. Uczciwa uwaga

Nie mam tu Android SDK, więc **nie skompilowałem** tej wersji – sprawdziłem ją statycznie (nawiasy, importy, tłumaczenia, zgodność wersji debug/release). Jeśli GitHub Actions pokaże błąd kompilacji, wklej mi log – poprawię od razu.
