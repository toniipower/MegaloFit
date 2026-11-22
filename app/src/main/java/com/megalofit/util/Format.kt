package com.megalofit.util

import java.text.Normalizer

/** Segundos que se muestran en una cuenta atrás (4,2 s → "5"). */
fun ceilSeconds(ms: Long): Long = (ms.coerceAtLeast(0) + 999) / 1000

fun formatClock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Minúsculas y sin tildes, para que "biceps" encuentre "Bíceps". */
fun String.foldForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun plural(n: Int, one: String, many: String) = if (n == 1) "$n $one" else "$n $many"
