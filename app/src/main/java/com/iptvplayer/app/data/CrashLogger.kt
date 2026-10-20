package com.iptvplayer.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Prosty mechanizm logowania awarii — zgłoszenie użytkownika: powtarzające się, trudne do
 * zdiagnozowania crashe w Live, bez żadnego sposobu, żeby dowiedzieć się CO dokładnie się
 * dzieje w momencie awarii. Zamiast dalej zgadywać na podstawie samych opisów, appka teraz
 * sama zapisuje:
 * 1. "Okruszki" (breadcrumbs) — krótki ślad OSTATNICH kilkudziesięciu zdarzeń w appce
 *    (zmiana ekranu, wybór kategorii, start odtwarzania itd.) z osobnym znacznikiem czasu
 *    dla każdego — wołane z kluczowych miejsc w kodzie (patrz breadcrumb() w LiveScreen,
 *    PlayerActivity itd.).
 * 2. Pełny stos wywołań (stack trace) w momencie NIEOBSŁUŻONEGO wyjątku (awarii appki).
 *
 * Log zapisywany lokalnie na urządzeniu (bez wysyłania GDZIEKOLWIEK automatycznie —
 * prywatność) — użytkownik może go ręcznie udostępnić (Ustawienia -> Log błędów -> Wyślij)
 * przez dowolną appkę do wysyłania (mail, komunikator), kiedy zechce.
 */
object CrashLogger {

    private const val MAX_BREADCRUMBS = 60
    private val breadcrumbs = ConcurrentLinkedDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Wołane raz, z IptvApp.onCreate() — podpina globalny handler nieobsłużonych wyjątków. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashLog(appContext, thread, throwable) }
            // ZAWSZE przekazuje dalej do poprzedniego handlera (domyślne zachowanie systemu:
            // pokazanie "Aplikacja się zatrzymała" i zamknięcie procesu) — logCrashLog to
            // TYLKO zapis na dysk, appka nie próbuje "połknąć" awarii i iść dalej, co
            // mogłoby zostawić ją w niespójnym, jeszcze trudniejszym do zdiagnozowania
            // stanie.
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Dodaje jedno zdarzenie do śladu — tanie, można wołać często, bez zapisu na dysk
     * (trzyma się tylko w pamięci, dopóki nie dojdzie do awarii). Automatycznie obcina do
     * MAX_BREADCRUMBS najnowszych, żeby nie rosło bez ograniczeń przez długą sesję. */
    fun breadcrumb(tag: String, message: String) {
        breadcrumbs.addLast("${timeFormat.format(Date())} [$tag] $message")
        while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.pollFirst()
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, "crash_logs")
        dir.mkdirs()
        // Jeden plik na cały log (nie jeden per awaria) — celowo NADPISYWANY przy każdej
        // kolejnej awarii, nie dopisywany: użytkownika interesuje NAJNOWSZA awaria do
        // wysłania, nie archiwum wszystkich poprzednich (które i tak rosłoby bez końca).
        val file = File(dir, "latest_crash.txt")
        val sb = StringBuilder()
        sb.appendLine("=== VivaPilot — log awarii ===")
        sb.appendLine("Czas: ${Date()}")
        sb.appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
        sb.appendLine("Urządzenie: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        sb.appendLine()
        sb.appendLine("--- Ostatnie zdarzenia przed awarią ---")
        breadcrumbs.forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("--- Wątek: ${thread.name} ---")
        sb.appendLine(android.util.Log.getStackTraceString(throwable))
        file.writeText(sb.toString())
    }

    /** Zwraca zawartość najnowszego zapisanego loga awarii, albo null, jeśli appka jeszcze
     * się nie wysypała (albo log został już wyczyszczony). */
    fun getLatestCrashLog(context: Context): String? {
        val file = File(File(context.filesDir, "crash_logs"), "latest_crash.txt")
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }

    fun clearLatestCrashLog(context: Context) {
        File(File(context.filesDir, "crash_logs"), "latest_crash.txt").delete()
    }
}
