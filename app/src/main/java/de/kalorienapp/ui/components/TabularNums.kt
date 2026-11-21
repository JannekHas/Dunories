package de.kalorienapp.ui.components

import androidx.compose.ui.text.TextStyle

/**
 * Gleich breite Ziffern („tnum“, Bricolage unterstützt das): zählende Zahlen (Ringe, Kacheln, Fasten-Countdown)
 * wackeln dann nicht hin und her, weil eine 1 so breit ist wie eine 0. Idee aus UnSlop, vom Nutzer übernommen (Nov. 2026).
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")
