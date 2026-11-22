package de.kalorienapp.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
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
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.LocalBottomBarPadding
import de.kalorienapp.ui.components.SettingSwitchRow
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.theme.Neon

/**
 * Social-Reiter (früher Platzhalter „Bald“): eigenes Profil mit Name und Lieblings-Abzeichen, Level & Erfolge
 * (abschaltbar unter Profil → Bereiche) und in der Testphase der Tester-Bereich. Später: Profilbild, Ranglisten,
 * kosmetische Belohnungen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialScreen(viewModel: SocialViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
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
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 24.dp + LocalBottomBarPadding.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.social_title), style = MaterialTheme.typography.headlineMedium)

        // ---------------- Profil-Kopf ----------------
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(state.name, level = game?.level?.level.takeIf { state.gamificationEnabled })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            state.name.ifBlank { stringResource(R.string.social_no_name) },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (state.name.isBlank()) Neon.TextSecondary else Neon.TextPrimary,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        state.favoriteBadge?.takeIf { state.gamificationEnabled }?.let { BadgeIcon(it, size = 26) }
                        IconButton(onClick = { editName = true }) {
                            Icon(Icons.Filled.Edit, stringResource(R.string.social_edit_name), tint = Neon.Cyan)
                        }
                    }
                    if (state.gamificationEnabled && game != null) {
                        val l = game.level
                        Text(stringResource(R.string.social_level, l.level), style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
                        LinearProgressIndicator(
                            progress = { l.fraction },
                            color = Neon.Yellow,
                            trackColor = Neon.Track,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Text(
                            stringResource(R.string.social_xp, l.totalXp.format(), (l.xpForNext - l.xpInLevel).format(), l.level + 1),
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
            // ---------------- Abzeichen ----------------
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.social_badges), style = MaterialTheme.typography.titleMedium)
                    if (game.badges.isEmpty()) {
                        Text(stringResource(R.string.social_badges_empty), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                    } else {
                        Text(stringResource(R.string.social_badges_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            game.badges.forEach { key ->
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
                                            .border(BorderStroke(2.dp, if (selected) Neon.Yellow else Color.Transparent), CircleShape)
                                            .padding(3.dp),
                                    ) { BadgeIcon(key, size = 48) }
                                    Spacer(Modifier.height(4.dp))
                                    Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                                }
                            }
                        }
                    }
                }
            }

            // ---------------- Erfolge ----------------
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.social_achievements), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.social_achievements_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                    game.progress.sortedBy { !it.unlocked }.forEach { p -> AchievementRow(p) }
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

        Text(stringResource(R.string.social_more_soon), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
    }

    if (editName) {
        NameDialog(state.name, onSave = { viewModel.saveName(it); editName = false }, onCancel = { editName = false })
    }
}

@Composable
private fun Avatar(name: String, level: Int?) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(72.dp)
                .border(3.dp, Neon.Cyan, CircleShape)
                .padding(5.dp)
                .background(Neon.SurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.headlineMedium,
                color = Neon.Cyan,
            )
        }
        if (level != null) {
            Text(
                level.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Neon.OnNeon,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Neon.Yellow, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun AchievementRow(p: Gamification.Progress) {
    val a = p.achievement
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.alpha(if (p.unlocked) 1f else 0.35f)) { BadgeIcon(a.key, size = 44, locked = !p.unlocked) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(badgeTitle(a.key), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.social_xp_reward, a.xp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (p.unlocked) Neon.Yellow else Neon.TextSecondary,
                )
            }
            Text(stringResource(achievementText(a)), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            if (!p.unlocked && a.target > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { p.shown.toFloat() / a.target },
                        color = badgeStyle(a.key).color,
                        trackColor = Neon.Track,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Text("${p.shown} / ${a.target}", style = MaterialTheme.typography.labelSmall, color = Neon.TextSecondary)
                }
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

// ---------------- Abzeichen-Aussehen ----------------

private data class BadgeStyle(val icon: ImageVector, val color: Color, val label: String?)

private fun badgeStyle(key: String): BadgeStyle = when (Achievement.byKey(key)) {
    Achievement.FIRST_ENTRY -> BadgeStyle(Icons.Filled.Edit, Neon.Cyan, "1")
    Achievement.ENTRIES_100 -> BadgeStyle(Icons.Filled.Edit, Neon.Violet, "100")
    Achievement.DAYS_7 -> BadgeStyle(Icons.Filled.DateRange, Neon.Cyan, "7")
    Achievement.DAYS_30 -> BadgeStyle(Icons.Filled.DateRange, Neon.Green, "30")
    Achievement.DAYS_100 -> BadgeStyle(Icons.Filled.DateRange, Neon.Yellow, "100")
    Achievement.STREAK_7 -> BadgeStyle(Icons.Filled.CheckCircle, Neon.Orange, "7")
    Achievement.STREAK_30 -> BadgeStyle(Icons.Filled.CheckCircle, Neon.Red, "30")
    Achievement.FIRST_WEIGHT -> BadgeStyle(Icons.Filled.Favorite, Neon.Magenta, "1")
    Achievement.WEIGHTS_30 -> BadgeStyle(Icons.Filled.Favorite, Neon.Violet, "30")
    Achievement.GOAL_REACHED -> BadgeStyle(Icons.Filled.Star, Neon.Yellow, null)
    Achievement.FIRST_REPORT -> BadgeStyle(Icons.Filled.Star, Neon.Blue, null)
    Achievement.FIRST_FEEDBACK -> BadgeStyle(Icons.Filled.ThumbUp, Neon.Green, null)
    null -> BadgeStyle(Icons.Filled.ThumbUp, Neon.Yellow, "T") // Tester
}

/** Rundes Abzeichen: Symbol in Neon-Farbe, kleine Zahl unten (z. B. „7“ Tage). */
@Composable
internal fun BadgeIcon(key: String, size: Int, locked: Boolean = false) {
    val style = badgeStyle(key)
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size.dp)
                .background(style.color.copy(alpha = 0.18f), CircleShape)
                .border(2.dp, style.color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (locked) Icons.Filled.Lock else style.icon, contentDescription = null, tint = style.color, modifier = Modifier.size((size * 0.5f).dp))
        }
        if (style.label != null && !locked && size >= 40) {
            Text(
                style.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Neon.OnNeon,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(style.color, RoundedCornerShape(50))
                    .padding(horizontal = 5.dp),
            )
        }
    }
}

@Composable
internal fun badgeTitle(key: String): String = stringResource(
    when (Achievement.byKey(key)) {
        Achievement.FIRST_ENTRY -> R.string.ach_first_entry
        Achievement.ENTRIES_100 -> R.string.ach_entries_100
        Achievement.DAYS_7 -> R.string.ach_days_7
        Achievement.DAYS_30 -> R.string.ach_days_30
        Achievement.DAYS_100 -> R.string.ach_days_100
        Achievement.STREAK_7 -> R.string.ach_streak_7
        Achievement.STREAK_30 -> R.string.ach_streak_30
        Achievement.FIRST_WEIGHT -> R.string.ach_first_weight
        Achievement.WEIGHTS_30 -> R.string.ach_weights_30
        Achievement.GOAL_REACHED -> R.string.ach_goal_reached
        Achievement.FIRST_REPORT -> R.string.ach_first_report
        Achievement.FIRST_FEEDBACK -> R.string.ach_first_feedback
        null -> R.string.ach_tester
    },
)

private fun achievementText(a: Achievement): Int = when (a) {
    Achievement.FIRST_ENTRY -> R.string.ach_first_entry_text
    Achievement.ENTRIES_100 -> R.string.ach_entries_100_text
    Achievement.DAYS_7 -> R.string.ach_days_7_text
    Achievement.DAYS_30 -> R.string.ach_days_30_text
    Achievement.DAYS_100 -> R.string.ach_days_100_text
    Achievement.STREAK_7 -> R.string.ach_streak_7_text
    Achievement.STREAK_30 -> R.string.ach_streak_30_text
    Achievement.FIRST_WEIGHT -> R.string.ach_first_weight_text
    Achievement.WEIGHTS_30 -> R.string.ach_weights_30_text
    Achievement.GOAL_REACHED -> R.string.ach_goal_reached_text
    Achievement.FIRST_REPORT -> R.string.ach_first_report_text
    Achievement.FIRST_FEEDBACK -> R.string.ach_first_feedback_text
}
