plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Generuje listę bibliotek open source + pełne teksty licencji (Ustawienia ->
    // Informacje prawne -> Licencje open source, sekcja 7 specyfikacji zgodności prawnej
    // z 09.10) — moduł wskazany w settings.gradle.kts (resolutionStrategy.useModule), bo
    // ta wtyczka nie ma standardowego "plugin marker" artefaktu. Dostarcza gotową
    // OssLicensesMenuActivity, otwieraną z SettingsScreen.kt.
    id("com.google.android.gms.oss-licenses-plugin")
}

android {
    namespace = "com.iptvplayer.app"
    compileSdk = 36

    defaultConfig {
        // Identyfikator pakietu (applicationId) zmieniony przy zmianie nazwy aplikacji na VivaPilot
        // (wersja 1.10), bo aplikacja nie była jeszcze opublikowana w Google Play. `namespace` (wyżej) i
        // nazwy pakietów w kodzie (com.iptvplayer.app...) zostają BEZ zmian — to wewnętrzna nazwa
        // modułu, niewidoczna dla użytkownika ani w sklepie; jej zmiana wymagałaby przepisania
        // wszystkich plików źródłowych i nie daje żadnej korzyści.
        applicationId = "com.vivapilot.iptv"
        minSdk = 24
        targetSdk = 36
        // NUMER WERSJI APLIKACJI = numer wersji Regulaminu i Polityki prywatności (sekcja 2
        // specyfikacji, "Numer wersji"). Przy KAŻDEJ kolejnej zmianie zleconej przez Wydawcę:
        // versionName +0.01 (1.12 -> 1.13 -> 1.14 ...), versionCode +1 (6 -> 7 -> 8 ...) ORAZ
        // taki sam numer w nagłówkach `WERSJA:` obu plików w app/src/main/assets/legal/
        // (regulamin.txt, polityka_prywatnosci.txt) — patrz LegalDocuments.kt.
        versionCode = 6
        versionName = "1.12"

        // libvlc-all pakuje natywne biblioteki dla WSZYSTKICH 4 architektur Androida
        // (armeabi-v7a, arm64-v8a, x86, x86_64) — stąd APK ważący 206 MB. x86/x86_64 są
        // realnie potrzebne tylko na emulatorach i garstce tabletów z procesorem Intela —
        // każdy telefon i każdy box telewizyjny (w tym Xiaomi) jest ARM-owy. Ograniczenie
        // do dwóch architektur ARM tnie rozmiar APK mniej więcej o połowę bez utraty
        // wsparcia dla jakiegokolwiek realnego urządzenia z tej kategorii — bezpośrednio
        // adresuje zgłoszony błąd instalacji przerywanej w 80-90% (typowy objaw
        // wyczerpania miejsca na urządzeniu podczas rozpakowywania dużych bibliotek
        // natywnych, a boxy telewizyjne notorycznie mają mało wolnej pamięci wewnętrznej).
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // Dwa wydania z tego samego kodu (sekcja 9 specyfikacji zgodności prawnej z 09.10):
    // `noads` (BEZPŁATNA przeglądarka funkcji bez reklam — zobacz TrialGate: wyszukiwanie/
    // Moje listy/pobieranie i tak wymagają zakupu po próbie, niezależnie od reklam) i `ads`
    // (te same funkcje, ale w bezpłatnym okresie PO próbie pokazuje reklamy zamiast wymagać
    // zakupu od razu). Biblioteka Google Mobile Ads + UMP są zależnością WYŁĄCZNIE wariantu
    // `ads` (`adsImplementation` niżej w dependencies{}) — `noads` nie ma ich w ogóle w
    // classpath, więc nie może zawierać żadnego kodu reklamowego ani uprawnienia
    // com.google.android.gms.permission.AD_ID (ono pochodzi WYŁĄCZNIE ze scalonego manifestu
    // biblioteki play-services-ads — bez tej zależności nie ma skąd się wziąć).
    //
    // Zwykłe `./gradlew assembleDebug` / `assembleRelease` (bez podawania nazwy wariantu)
    // budują OBA warianty naraz — wynikowe pliki to app-noads-debug.apk i app-ads-debug.apk
    // (odpowiednio release) w app/build/outputs/apk/. Nic w CI nie trzeba zmieniać, żeby
    // dostać obie wersje z jednego builda. Kod rozróżniający wydania w czasie działania:
    // BuildConfig.FLAVOR == "ads", oraz com.iptvplayer.app.ads.AdsController (osobna
    // implementacja w app/src/ads i app/src/noads, patrz komentarze w tych plikach).
    flavorDimensions += "ads"
    productFlavors {
        create("noads") {
            dimension = "ads"
        }
        create("ads") {
            dimension = "ads"
        }
    }

    // WAŻNE: bez tej jawnej konfiguracji Gradle sam generuje losowy klucz debugowy
    // (~/.android/debug.keystore) przy KAŻDYM uruchomieniu na maszynie, która go jeszcze
    // nie ma — a każdy build na GitHub Actions startuje na świeżej, "czystej" maszynie
    // (bez zapisanego wcześniej klucza). Efekt: każdy build z CI miał swój WŁASNY, inny
    // losowy podpis, więc instalacja nowszej wersji na urządzeniu, gdzie już jest
    // zainstalowana starsza (podpisana innym kluczem), kończyła się błędem "Aplikacja nie
    // została zainstalowana" — Android wymaga zgodnego podpisu przy aktualizacji tego
    // samego pakietu. app/debug.keystore jest teraz na stałe w repozytorium (to zwykły
    // plik debugowy, nie ma nic tajnego do ochrony — w przeciwieństwie do klucza release,
    // który NIGDY nie powinien trafić do repo), więc od teraz każdy build z CI ma ten sam
    // podpis i aktualizacje instalują się bez konieczności odinstalowywania poprzedniej wersji.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Kompresja kodu i usuwanie nieużywanych zasobów — mniejszy plik appki do
            // Sklepu Play i nieco szybsze działanie. Reguły chroniące zapis danych i silnik
            // VLC: app/proguard-rules.pro. Dotyczy TYLKO wersji release — testowa wersja
            // debug (z GitHub Actions) buduje się bez zmian.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG — używane WYŁĄCZNIE do ukrycia debugowego przesuwania zegara
        // okresu próbnego (patrz LocalStore.getDebugTrialOffsetMillis/TrialGate.isExpired i
        // SettingsScreen.kt) poza wersją release. AGP 8.x wymaga jawnego włączenia tej flagi
        // (domyślnie wyłączona), inaczej BuildConfig w ogóle się nie wygeneruje.
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        // libVLC ships its own native libs per-ABI; avoid duplicate META-INF clashes
        resources.excludes.add("META-INF/*")
    }
}

// Wariant `ads`: biblioteka Google Mobile Ads 25.x (adsImplementation niżej) ściąga do
// classpathu kotlin-stdlib 2.2.x i własne moduły Kotlina 2.1+, a projekt jest kompilowany
// kompilatorem Kotlin 1.9.24 — bez tego flagi kompilator odrzuca je błędem "was compiled with
// an incompatible version of Kotlin" (u nas: cała kompilacja compileAdsDebugKotlin, ~10 tys.
// błędów-kaskad typu "Unresolved reference: listOf"). Flaga -Xskip-metadata-version-check
// wyłącza TYLKO to sprawdzenie wersji metadanych, i to wyłącznie dla zadań kompilacji
// wariantu `ads` (compileAdsDebugKotlin, compileAdsReleaseKotlin…). Wariant `noads` nie ma tych
// bibliotek w ogóle, więc jego kompilacja zostaje dokładnie taka jak była.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (this.name.startsWith("compileAds")) {
        compilerOptions.freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // --- Jetpack Compose ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    // WindowInsetsControllerCompat — do trybu pełnoekranowego/immersyjnego w PlayerActivity
    implementation("androidx.core:core-ktx:1.13.1")
    // MediaSessionCompat — kontrolki na ekranie blokady i słuchawkach Bluetooth (patrz
    // PlayerActivity: mediaSession, buildNotification). Świadomie androidx.media (nie
    // media3/ExoPlayer) — silnikiem odtwarzania jest tu libVLC, nie ExoPlayer, więc media3
    // byłoby złym dopasowaniem architektonicznym.
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Playback engine: libVLC (same decoding core as desktop VLC) ---
    // Handles H.264/H.265(HEVC)/AV1/VP8/VP9/MPEG2, AC-3/E-AC-3/DTS/TrueHD audio,
    // MKV/TS/MP4/AVI containers, HLS/DASH streams — all in software/hw-accelerated,
    // no dependency on device's built-in MediaCodec codec list.
    implementation("org.videolan.android:libvlc-all:3.6.0")

    // --- Networking (Xtream Codes API + M3U fetch) ---
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // --- Local persistence (playlists, favorites, watch progress) ---
    // Simple JSON-file based store (mirrors the localStorage approach of the HTML app)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.11.0")
    // Google Play Billing — mechanizm zakupu pełnej wersji (jednorazowy zakup, patrz
    // BillingManager.kt). Wymóg Google Play dla sprzedaży czegokolwiek wewnątrz appki — nie
    // da się tego zastąpić własnym systemem płatności. Celowo BEZ żadnego dodatkowego
    // "znacznika próby" powiązanego z kontem Google — okres próbny jest liczony wyłącznie
    // lokalnie (patrz LocalStore/TrialGate), zgodnie z wyraźnym wymogiem.
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // --- Reklamy (sekcja 9) — WYŁĄCZNIE wariant `ads`, patrz obszerny komentarz przy
    // flavorDimensions wyżej. `adsImplementation` to specjalny, automatycznie wygenerowany
    // przez Gradle konfig zależności "tylko dla product flavoru o nazwie ads" — standardowy
    // mechanizm Android Gradle Plugin, nic dodatkowego nie trzeba konfigurować. ---
    // Google Mobile Ads SDK (AdMob) — banery i reklamy pełnoekranowe (interstitial), patrz
    // com.iptvplayer.app.ads.AdsController w app/src/ads.
    "adsImplementation"("com.google.android.gms:play-services-ads:25.5.0")
    // Google User Messaging Platform (UMP) — okno zgody RODO/UE na reklamy dopasowane,
    // wymagane PRZED pierwszą reklamą (patrz AdsController.AdsGate).
    "adsImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")

    // Lista licencji open source (Ustawienia -> Informacje prawne -> Licencje open source,
    // sekcja 7) — dostarcza OssLicensesMenuActivity, wypełnianą automatycznie przy
    // kompilacji przez wtyczkę com.google.android.gms.oss-licenses-plugin (patrz plugins{}
    // wyżej i settings.gradle.kts). Otwierana z SettingsScreen.kt.
    implementation("com.google.android.gms:play-services-oss-licenses:17.5.2")

    // --- Local folder browsing (Storage Access Framework) — podłączanie i skanowanie
    // własnych folderów z filmami na urządzeniu, patrz LocalFolderScanner. ---
    implementation("androidx.documentfile:documentfile:1.0.1")

    // --- Odświeżanie katalogu w tle (CatalogRefreshWorker) ---
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // --- Rząd "Kontynuuj oglądanie" (Watch Next) na ekranie głównym Android TV ---
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    // --- Image loading (channel/movie logos) ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- XMLTV / EPG parsing ---
    implementation("org.jsoup:jsoup:1.17.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
