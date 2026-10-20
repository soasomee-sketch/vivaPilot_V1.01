package com.iptvplayer.app.util

import java.text.Normalizer

/**
 * Wyszukiwanie odporne na polskie znaki diakrytyczne — użytkownik nie musi już trafić
 * dokładnie w "ł"/"ą"/"ę" itd., wystarczy zwykłe "l"/"a"/"e" (i odwrotnie: wpisanie "ł"
 * nadal znajdzie tytuły zapisane bez ogonków). Używane wszędzie tam, gdzie dotąd był
 * zwykły `.contains(query, ignoreCase = true)` — patrz `containsFolded` niżej.
 *
 * Dwuetapowe usuwanie akcentów:
 * 1) Normalizer.normalize(NFD) rozkłada większość akcentowanych liter (np. niemieckie ü/ö,
 *    francuskie é/è, hiszpańskie ñ, a przy okazji też polskie ą/ć/ę/ń/ó/ś/ź) na literę
 *    bazową + oddzielny znak łączący akcentu, który potem wycinamy regexem \p{Mn}.
 * 2) Polskie "ł"/"Ł" tego sposobu NIE obsługują — w Unicode kreska przez "l" nie jest
 *    zapisana jako osobny znak łączący, tylko "ł" jest samodzielnym, w pełni odrębnym
 *    znakiem — stąd ręczna mapa DIACRITIC_FOLD_MAP jako dogrywka po normalizacji.
 */
private val DIACRITIC_FOLD_MAP: Map<Char, Char> = mapOf(
    'ą' to 'a', 'ć' to 'c', 'ę' to 'e', 'ł' to 'l', 'ń' to 'n',
    'ó' to 'o', 'ś' to 's', 'ź' to 'z', 'ż' to 'z',
)

private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")

/** Sprowadza tekst do postaci porównywalnej bez względu na wielkość liter i akcenty. */
fun String.searchNormalize(): String {
    val decomposed = Normalizer.normalize(this.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
    return buildString(decomposed.length) {
        for (c in decomposed) append(DIACRITIC_FOLD_MAP[c] ?: c)
    }
}

/** Zamiennik `this.contains(query, ignoreCase = true)`, dodatkowo ignorujący akcenty
 * (polskie i nie tylko) po obu stronach porównania. */
fun String.containsFolded(query: String): Boolean =
    this.searchNormalize().contains(query.searchNormalize())
