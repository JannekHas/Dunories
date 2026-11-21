package de.kalorienapp.ui.onboarding

import de.kalorienapp.ui.components.tabularNums
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.data.repository.GoalState
import de.kalorienapp.domain.model.DailyTargets
import de.kalorienapp.domain.model.Goal
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.RingSpec
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.goal.formatGoalDate
import de.kalorienapp.ui.theme.Neon
import kotlin.math.abs

/**
 * Abschluss der Einrichtung: „Dein Plan steht“ – die vier Ringe laufen voll, das Tagesziel zählt hoch,
 * darunter Makros und der Weg zum Wunschgewicht. Schneller Erfolgsmoment ohne App-Tour und ohne Push
 * (Entscheidung des Nutzers); der erste Eintrag wird bewusst nicht verlangt – man richtet die App selten
 * beim Essen ein.
 */
@Composable
fun PlanReveal(targets: DailyTargets, goalState: GoalState?, onStart: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.plan_reveal_title),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                ConcentricRings(
                    rings = listOf(
                        RingSpec(1f, Neon.Rainbow),
                        RingSpec(1f, listOf(Neon.Magenta)),
                        RingSpec(1f, listOf(Neon.Yellow)),
                        RingSpec(1f, listOf(Neon.Green)),
                    ),
                    modifier = Modifier.fillMaxWidth(0.66f).padding(vertical = 8.dp),
                    strokeWidth = 12.dp,
                    gap = 5.dp,
                ) {
                    // Zahl zählt mit den Ringen hoch.
                    var target by remember { mutableIntStateOf(0) }
                    LaunchedEffect(targets.kcal) { target = targets.kcal }
                    val shown by animateIntAsState(target, tween(1200), label = "kcal")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(shown.format(), style = MaterialTheme.typography.headlineLarge.tabularNums(), fontWeight = FontWeight.ExtraBold)
                        Text(
                            stringResource(R.string.plan_reveal_kcal_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = Neon.TextSecondary,
                        )
                    }
                }
                Text(
                    stringResource(
                        if (goalState?.direction == Goal.MAINTAIN) R.string.plan_reveal_text_maintain else R.string.plan_reveal_text,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Neon.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonPill("${targets.proteinG} g", stringResource(R.string.protein), Neon.Magenta, Modifier.weight(1f))
                    NeonPill("${targets.carbsG} g", stringResource(R.string.carbs_short), Neon.Yellow, Modifier.weight(1f))
                    NeonPill("${targets.fatG} g", stringResource(R.string.fat), Neon.Green, Modifier.weight(1f))
                }
                goalState?.let { GoalLine(it) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.plan_reveal_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neon.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(stringResource(R.string.plan_reveal_start), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Weg zum Wunschgewicht in einem Satz, z. B. „98 → 80 kg bis 12. März 2027 · 0,5 kg pro Woche“. */
@Composable
private fun GoalLine(state: GoalState) {
    val goal = state.goal ?: return
    val plan = state.plan
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.direction == Goal.MAINTAIN || goal.targetDate == null) {
                Text(
                    stringResource(R.string.plan_reveal_goal_maintain, goal.targetWeightKg.toInputString()),
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    stringResource(
                        R.string.plan_reveal_goal,
                        goal.startWeightKg.toInputString(),
                        goal.targetWeightKg.toInputString(),
                        goal.targetDate.formatGoalDate(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (plan != null) {
                    Text(
                        stringResource(
                            if (state.direction == Goal.GAIN) R.string.plan_reveal_pace_gain else R.string.plan_reveal_pace_lose,
                            abs(plan.kgPerWeek).toInputString(2),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.TextSecondary,
                    )
                }
            }
        }
    }
}
