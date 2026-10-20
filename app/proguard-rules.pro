# Reguły R8 (kompresja/zaciemnianie kodu) — dotyczą TYLKO wersji "release" (do Sklepu Play).
# Wersja testowa (debug) z GitHub Actions nie jest kompresowana i działa jak dotychczas.

# --- Zapis danych (Gson) i przekazywanie kolejki do odtwarzacza (Serializable) ---
# Gson odczytuje/zapisuje pola po NAZWACH — gdyby R8 skrócił nazwy pól (np. "title" -> "a"),
# po aktualizacji appki zapisane playlisty, listy i "Kontynuuj oglądanie" przestałyby się
# wczytywać. Zachowujemy więc pola wszystkich klas modeli appki.
-keepclassmembers class com.iptvplayer.app.** {
    <fields>;
    <init>(...);
}
-keep class com.iptvplayer.app.data.** { *; }
-keep class com.iptvplayer.app.player.QueueEntry { *; }
-keep class com.iptvplayer.app.player.NavQueue { *; }
-keep class com.iptvplayer.app.player.SeriesSubQueue { *; }
-keep class com.iptvplayer.app.cast.** { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- libVLC: natywny silnik wywołuje klasy Javy przez JNI (po nazwach) ---
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }
-dontwarn org.videolan.**

# --- Pozostałe biblioteki ---
-dontwarn org.jsoup.**
-dontwarn okhttp3.**
-dontwarn okio.**
