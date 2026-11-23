package de.kalorienapp.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.Feedback
import de.kalorienapp.domain.Gamification
import de.kalorienapp.domain.Gamification.Achievement
import de.kalorienapp.domain.Gamification.Rarity
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ExpandableCard
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.SettingSwitchRow
import de.kalorienapp.ui.components.TabbedPages
import de.kalorienapp.ui.theme.Neon

/**
 * Social-Reiter (früher Platzhalter „Bald“): eigenes Profil mit Name und Lieblings-Abzeichen, Erfolge & Abzeichen
 * mit Seltenheit (abschaltbar unter Profil → Bereiche) und in der Testphase der Tester-Bereich.
 * Bewusst **ohne Level/XP** (Entscheidung des Nutzers). Reiter „Ich“ (Profil, Serie, Erfolge) und „Freunde“.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialScreen(
    onPrivacy: () -> Unit = {},
    viewModel: SocialViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    var editName by rememberSaveable { mutableStateOf(false) }
    val game = state.game

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            stringResource(R.string.social_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 8.dp),
        )
        // Zwei Reiter: man selbst | Freunde (Wunsch des Nutzers, Nov. 2026).
        TabbedPages(
            tabs = listOf(stringResource(R.string.social_tab_me), stringResource(R.string.social_tab_friends)),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            if (page == 0) {
                MePage(state, viewModel, onEditName = { editName = true })
            } else {
                FriendsSection(
                    viewModel = viewModel,
                    signedIn = state.signedIn,
                    myName = state.name,
                    myStreak = game?.stats?.currentStreak ?: 0,
                    myAvatar = state.avatar,
                    onPrivacy = onPrivacy,
                )
                if (state.signedIn) ShareSettingsCard(viewModel, state.share)
            }
        }
    }

    if (editName) {
        NameDialog(state.name, onSave = { viewModel.saveName(it); editName = false }, onCancel = { editName = false })
    }
}

/** Reiter „Ich“: Profil, Serie, Abzeichen, Erfolge, Tester-Bereich. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MePage(state: SocialUiState, viewModel: SocialViewModel, onEditName: () -> Unit) {
    val game = state.game
    var pickAvatar by rememberSaveable { mutableStateOf(false) }
    // ---------------- Profil-Kopf ----------------
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val changeAvatar = stringResource(R.string.social_avatar_change)
            AvatarImage(
                state.avatar,
                state.name,
                72,
                Modifier.clickable(role = Role.Button, onClickLabel = changeAvatar) { pickAvatar = true }
                    .semantics { contentDescription = changeAvatar },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.name.ifBlank { stringResource(R.string.social_no_name) },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.name.isBlank()) Neon.TextSecondary else Neon.TextPrimary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (state.gamificationEnabled && game != null) state.favoriteBadge?.let { BadgeIcon(it, levelOf(game.levels, it), size = 28) }
                    IconButton(onClick = onEditName) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.social_edit_name), tint = Neon.Cyan)
                    }
                }
                if (state.gamificationEnabled && game != null) {
                    Text(
                        stringResource(R.string.social_count, game.progress.count { it.unlocked }, game.progress.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                }
            }
        }
    }

    if (!state.gamificationEnabled) {
        Text(stringResource(R.string.social_game_off), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
    } else if (game != null) {
        StreakCard(current = game.stats.currentStreak, longest = game.stats.longestStreak, imported = state.importedStreak)

        // ---------------- Abzeichen (ausklappbar – Wunsch des Nutzers: weniger scrollen) ----------------
        ExpandableCard(
            title = stringResource(R.string.social_badges),
            summary = pluralStringResource(R.plurals.social_badges_count, game.badges.size, game.badges.size),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (game.badges.isEmpty()) {
                    Text(stringResource(R.string.social_badges_empty), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                } else {
                    Text(stringResource(R.string.social_badges_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        game.badges.sortedByDescending { badgeRarity(it, levelOf(game.levels, it)).ordinal }.forEach { key ->
                            val selected = key == state.favoriteBadge
                            val name = badgeTitle(key)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable(role = Role.Button) { viewModel.setFavoriteBadge(key) }
                                    .semantics { contentDescription = name },
                            ) {
                                Box(
                                    Modifier
                                        .border(BorderStroke(2.dp, if (selected) Neon.TextPrimary else Color.Transparent), CircleShape)
                                        .padding(3.dp),
                                ) { BadgeIcon(key, levelOf(game.levels, key), size = 48) }
                                Spacer(Modifier.height(4.dp))
                                Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                    RarityLegend()
                }
            }
        }

        // ---------------- Erfolge (ausklappbar) ----------------
        ExpandableCard(
            title = stringResource(R.string.social_achievements),
            summary = stringResource(R.string.social_count, game.progress.count { it.unlocked }, game.progress.size),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.social_achievements_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                game.progress.forEach { p -> AchievementRow(p) }
            }
        }
    }

    // ---------------- Tester-Bereich (nur Testphase) ----------------
    if (state.testPhase) {
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.social_tester_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.tester_points, state.testerPoints),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Neon.Green,
                )
                Text(stringResource(R.string.social_tester_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                if (state.creditsEver && state.name.isNotBlank()) {
                    SettingSwitchRow(
                        title = stringResource(R.string.feedback_credits),
                        text = stringResource(R.string.feedback_credits_text),
                        checked = state.credits,
                        onChange = viewModel::setCredits,
                    )
                }
            }
        }
    }

    if (pickAvatar) {
        AvatarPicker(state.avatar, state.name, onPick = { viewModel.setAvatar(it); pickAvatar = false }, onClose = { pickAvatar = false })
    }
}

/**
 * Serie: aktuelle und längste nebeneinander, nicht antippbar (Wunsch des Nutzers – bewusst ohne Wochenleiste).
 * Zählt Tage mit Einträgen; eine aus YAZIO übernommene Serie ist eingerechnet.
 */
@Composable
private fun StreakCard(current: Int, longest: Int, imported: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.social_streak_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreakNumber(current, stringResource(R.string.social_streak_current), Neon.Orange, Modifier.weight(1f))
                StreakNumber(longest, stringResource(R.string.social_streak_longest), Neon.Yellow, Modifier.weight(1f))
            }
            Text(
                stringResource(if (imported) R.string.social_streak_hint_imported else R.string.social_streak_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
        }
    }
}

@Composable
private fun StreakNumber(days: Int, label: String, color: Color, modifier: Modifier) {
    val spoken = pluralStringResource(R.plurals.social_streak_days, days, days)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $spoken" }) {
        Text(days.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
    }
}

@Composable
private fun AchievementRow(p: Gamification.Progress) {
    val a = p.achievement
    val hidden = a.kind == Gamification.Kind.SECRET && !p.unlocked
    val tiered = a.kind == Gamification.Kind.TIERED && a.tiers.size > 1
    // Noch nicht erreicht: Farbe der nächsten Stufe; erreicht: Farbe der aktuellen.
    val rarity = if (p.unlocked) p.rarity else Gamification.rarityOf(a, 1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (hidden) SecretIcon(44) else BadgeIcon(a.key, p.level, size = 44, locked = !p.unlocked)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (hidden) "???" else badgeTitle(a.key),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (!hidden) {
                    Text(stringResource(rarityName(rarity)), style = MaterialTheme.typography.labelMedium, color = rarityColor(rarity))
                }
            }
            Text(
                if (hidden) stringResource(R.string.social_secret) else achievementText(p),
                style = MaterialTheme.typography.bodySmall,
                color = Neon.TextSecondary,
            )
            if (tiered && p.unlocked) {
                Text(
                    if (p.maxed) stringResource(R.string.social_maxed) else stringResource(R.string.social_level, p.level, a.tiers.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = rarityColor(p.rarity),
                )
            }
            if (!hidden && !p.maxed && p.target > 1 && a.kind != Gamification.Kind.EVENT) {
                val next = Gamification.rarityOf(a, p.level + 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { p.shown.toFloat() / p.target },
                        color = rarityColor(next),
                        trackColor = Neon.Track,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Text("${p.shown} / ${p.target}", style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary)
                }
            }
        }
    }
}

/** Kleine Legende der Seltenheiten unter den Abzeichen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RarityLegend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Rarity.entries.filter { it != Rarity.EVENT }.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).background(rarityBrush(r), CircleShape))
                Text(stringResource(rarityName(r)), style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary)
            }
        }
    }
}

@Composable
private fun NameDialog(current: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.social_edit_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.social_name_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(Feedback.MAX_NAME) },
                    label = { Text(stringResource(R.string.social_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.tester_name_save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}
