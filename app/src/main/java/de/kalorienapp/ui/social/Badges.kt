package de.kalorienapp.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.data.repository.GameRepository
import de.kalorienapp.domain.Gamification
import de.kalorienapp.domain.Gamification.Achievement
import de.kalorienapp.domain.Gamification.Rarity
import de.kalorienapp.ui.theme.Neon

/*
 * Abzeichen = Rahmen je Seltenheit + Motiv je Erfolg (Claude Design, Nov. 2026 – vorerst KI-Entwürfe, der Nutzer
 * lässt sie evtl. später von Hand gestalten). Quellen in design/abzeichen, umgewandelt mit svg2vd_full.py.
 */

internal fun rarityColor(r: Rarity): Color = when (r) {
    Rarity.COMMON -> Color(0xFF9CA3AF)
    Rarity.UNCOMMON -> Neon.Green
    Rarity.RARE -> Neon.Blue
    Rarity.EPIC -> Color(0xFFFF4D6D)
    Rarity.LEGENDARY -> Neon.Yellow
    Rarity.GALAXY -> Neon.Violet
    Rarity.EVENT -> Color(0xFFE8E8F0)
}

/** Galaxy = Farbverlauf, sonst einfarbig. */
internal fun rarityBrush(r: Rarity): Brush =
    if (r == Rarity.GALAXY) Brush.sweepGradient(listOf(Neon.Violet, Neon.Magenta, Neon.Cyan, Neon.Blue, Neon.Violet))
    else Brush.linearGradient(listOf(rarityColor(r), rarityColor(r)))

internal fun rarityName(r: Rarity): Int = when (r) {
    Rarity.COMMON -> R.string.rarity_common
    Rarity.UNCOMMON -> R.string.rarity_uncommon
    Rarity.RARE -> R.string.rarity_rare
    Rarity.EPIC -> R.string.rarity_epic
    Rarity.LEGENDARY -> R.string.rarity_legendary
    Rarity.GALAXY -> R.string.rarity_galaxy
    Rarity.EVENT -> R.string.rarity_event
}

/** Seltenheit eines Abzeichens bei Stufe [level]; das Tester-Abzeichen zählt als legendär. */
internal fun badgeRarity(key: String, level: Int): Rarity =
    Achievement.byKey(key)?.let { Gamification.rarityOf(it, level.coerceAtLeast(1)) } ?: Rarity.LEGENDARY

private fun frameRes(r: Rarity): Int = when (r) {
    Rarity.COMMON -> R.drawable.badge_frame_1
    Rarity.UNCOMMON -> R.drawable.badge_frame_2
    Rarity.RARE -> R.drawable.badge_frame_3
    Rarity.EPIC -> R.drawable.badge_frame_4
    Rarity.LEGENDARY -> R.drawable.badge_frame_5
    Rarity.GALAXY -> R.drawable.badge_frame_galaxy
    Rarity.EVENT -> R.drawable.badge_frame_event
}

private fun motifRes(key: String): Int = when (Achievement.byKey(key)) {
    Achievement.ENTRIES -> R.drawable.badge_motif_eintragen
    Achievement.DAYS -> R.drawable.badge_motif_dabei
    Achievement.STREAK -> R.drawable.badge_motif_serie
    Achievement.PROTEIN -> R.drawable.badge_motif_proteinziel
    Achievement.PHOTO -> R.drawable.badge_motif_foto_profi
    Achievement.BARCODE -> R.drawable.badge_motif_barcode_jaeger
    Achievement.WEIGHT -> R.drawable.badge_motif_waage
    Achievement.WATER -> R.drawable.badge_motif_wasser
    Achievement.REPORTS -> R.drawable.badge_motif_rueckblick
    Achievement.FEEDBACK -> R.drawable.badge_motif_mitgestalter
    Achievement.FRIENDS -> R.drawable.badge_motif_freunde
    Achievement.GOAL -> R.drawable.badge_motif_ziel_erreicht
    Achievement.NIGHT -> R.drawable.badge_motif_geheim_mitternachtssnack
    Achievement.EARLY -> R.drawable.badge_motif_geheim_fruehaufsteher
    Achievement.COMEBACK -> R.drawable.badge_motif_geheim_comeback
    Achievement.WEEKEND -> R.drawable.badge_motif_geheim_wochenend_held
    Achievement.CHRISTMAS -> R.drawable.badge_motif_event_weihnachten
    Achievement.NEW_YEARS_EVE -> R.drawable.badge_motif_event_silvester
    Achievement.RESOLUTION -> R.drawable.badge_motif_event_neujahrsvorsatz
    Achievement.EASTER -> R.drawable.badge_motif_event_ostern
    Achievement.HALLOWEEN -> R.drawable.badge_motif_event_halloween
    null -> R.drawable.badge_motif_mitgestalter // Tester
}

/**
 * Rundes Abzeichen: Rahmen der Seltenheit (Stufe [level]) und das Motiv in derselben Farbe.
 * [locked] = noch nicht geschafft → grauer Rahmen, blasses Motiv.
 */
@Composable
internal fun BadgeIcon(key: String, level: Int, size: Int, locked: Boolean = false) {
    val rarity = if (locked) Rarity.COMMON else badgeRarity(key, level)
    val tint = when (rarity) {
        Rarity.GALAXY, Rarity.EVENT -> Color.White
        else -> rarityColor(rarity)
    }
    Box(Modifier.size(size.dp).alpha(if (locked) 0.4f else 1f), contentAlignment = Alignment.Center) {
        Image(painterResource(frameRes(rarity)), contentDescription = null, modifier = Modifier.size(size.dp))
        Icon(
            painterResource(motifRes(key)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

/** Geheimer, noch nicht geschaffter Erfolg: grauer Kreis mit „?“. */
@Composable
internal fun SecretIcon(size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .background(Neon.SurfaceHighest, CircleShape)
            .border(2.dp, Neon.TextSecondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("?", style = MaterialTheme.typography.titleLarge, color = Neon.TextSecondary) }
}

@Composable
internal fun badgeTitle(key: String): String = stringResource(
    when (Achievement.byKey(key)) {
        Achievement.ENTRIES -> R.string.ach_eintragen
        Achievement.DAYS -> R.string.ach_dabei
        Achievement.STREAK -> R.string.ach_serie
        Achievement.PROTEIN -> R.string.ach_proteinziel
        Achievement.PHOTO -> R.string.ach_foto_profi
        Achievement.BARCODE -> R.string.ach_barcode_jaeger
        Achievement.WEIGHT -> R.string.ach_waage
        Achievement.WATER -> R.string.ach_wasser
        Achievement.REPORTS -> R.string.ach_rueckblick
        Achievement.FEEDBACK -> R.string.ach_mitgestalter
        Achievement.FRIENDS -> R.string.ach_freunde
        Achievement.GOAL -> R.string.ach_ziel_erreicht
        Achievement.NIGHT -> R.string.ach_mitternachtssnack
        Achievement.EARLY -> R.string.ach_fruehaufsteher
        Achievement.COMEBACK -> R.string.ach_comeback
        Achievement.WEEKEND -> R.string.ach_wochenend_held
        Achievement.CHRISTMAS -> R.string.ach_weihnachten
        Achievement.NEW_YEARS_EVE -> R.string.ach_silvester
        Achievement.RESOLUTION -> R.string.ach_neujahrsvorsatz
        Achievement.EASTER -> R.string.ach_ostern
        Achievement.HALLOWEEN -> R.string.ach_halloween
        null -> R.string.ach_tester
    },
)

/** Beschreibung: Stufen-Erfolge mit dem Ziel der nächsten Stufe, Events mit der Zahl der Jahre. */
@Composable
internal fun achievementText(p: Gamification.Progress): String {
    val a = p.achievement
    val years = p.value.coerceAtLeast(1)
    return when (a) {
        Achievement.ENTRIES -> pluralStringResource(R.plurals.ach_eintragen_text, p.target, p.target)
        Achievement.DAYS -> pluralStringResource(R.plurals.ach_dabei_text, p.target, p.target)
        Achievement.STREAK -> pluralStringResource(R.plurals.ach_serie_text, p.target, p.target)
        Achievement.PROTEIN -> pluralStringResource(R.plurals.ach_proteinziel_text, p.target, p.target)
        Achievement.PHOTO -> pluralStringResource(R.plurals.ach_foto_profi_text, p.target, p.target)
        Achievement.BARCODE -> pluralStringResource(R.plurals.ach_barcode_jaeger_text, p.target, p.target)
        Achievement.WEIGHT -> pluralStringResource(R.plurals.ach_waage_text, p.target, p.target)
        Achievement.WATER -> pluralStringResource(R.plurals.ach_wasser_text, p.target, p.target)
        Achievement.REPORTS -> pluralStringResource(R.plurals.ach_rueckblick_text, p.target, p.target)
        Achievement.FEEDBACK -> pluralStringResource(R.plurals.ach_mitgestalter_text, p.target, p.target)
        Achievement.FRIENDS -> pluralStringResource(R.plurals.ach_freunde_text, p.target, p.target)
        Achievement.GOAL -> pluralStringResource(R.plurals.ach_ziel_erreicht_text, p.target, p.target)
        Achievement.NIGHT -> stringResource(R.string.ach_mitternachtssnack_text)
        Achievement.EARLY -> stringResource(R.string.ach_fruehaufsteher_text)
        Achievement.COMEBACK -> stringResource(R.string.ach_comeback_text)
        Achievement.WEEKEND -> stringResource(R.string.ach_wochenend_held_text)
        Achievement.CHRISTMAS -> pluralStringResource(R.plurals.ach_weihnachten_text, years, years)
        Achievement.NEW_YEARS_EVE -> pluralStringResource(R.plurals.ach_silvester_text, years, years)
        Achievement.RESOLUTION -> pluralStringResource(R.plurals.ach_neujahrsvorsatz_text, years, years)
        Achievement.EASTER -> pluralStringResource(R.plurals.ach_ostern_text, years, years)
        Achievement.HALLOWEEN -> pluralStringResource(R.plurals.ach_halloween_text, years, years)
    }
}

/** Stufe eines Abzeichens aus dem Spielstand (Tester-Abzeichen: 1). */
internal fun levelOf(levels: Map<String, Int>, key: String): Int =
    if (key == GameRepository.TESTER_BADGE) 1 else levels[key] ?: 1

// ---------------- Abzeichen von Freunden („serie_3“, alte Versionen „streak_30“, „tester“) ----------------

internal fun sharedRarity(shared: String): Rarity =
    Gamification.parseShared(shared)?.let { (a, level) -> Gamification.rarityOf(a, level) } ?: Rarity.LEGENDARY

@Composable
internal fun SharedBadge(shared: String, size: Int) {
    val parsed = Gamification.parseShared(shared)
    if (parsed != null) BadgeIcon(parsed.first.key, parsed.second, size) else BadgeIcon(shared, 1, size)
}

@Composable
internal fun sharedTitle(shared: String): String = badgeTitle(Gamification.parseShared(shared)?.first?.key ?: shared)
