package de.kalorienapp.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.data.AppSettings
import de.kalorienapp.domain.model.Budget
import de.kalorienapp.domain.model.CookingTime
import de.kalorienapp.domain.model.DietStyle
import de.kalorienapp.domain.model.ExcludedFood
import de.kalorienapp.domain.model.FoodPreferences
import de.kalorienapp.domain.model.Intolerance
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.flow.StateFlow

class FoodPreferencesViewModel(private val settings: AppSettings) : ViewModel() {
    val preferences: StateFlow<FoodPreferences> = settings.foodPreferences

    fun update(transform: (FoodPreferences) -> FoodPreferences) = settings.updateFoodPreferences(transform)

    fun removeDislike(name: String) = settings.removeDislike(name)
}

/** Bereiche der Vorlieben (zweite Ebene). */
private enum class PrefSection(val titleRes: Int) {
    STYLE(R.string.prefs_style),
    ALLERGIES(R.string.prefs_allergies),
    EVERYDAY(R.string.prefs_everyday),
    DISLIKES(R.string.prefs_dislikes_title),
}

/**
 * Vorlieben für die KI in Ebenen (Wunsch des Nutzers: nicht alles auf einmal):
 * oben vier Bereiche mit Zusammenfassung, antippen → Auswahl dieses Bereichs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPreferencesScreen(
    onBack: () -> Unit,
    viewModel: FoodPreferencesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val section = PrefSection.entries.find { it.name == sectionName }
    BackHandler(enabled = section != null) { sectionName = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section?.titleRes ?: R.string.prefs_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (section != null) sectionName = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally(tween(250)) { if (forward) it else -it }) togetherWith
                    (slideOutHorizontally(tween(250)) { if (forward) -it else it })
            },
            label = "prefsSection",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { current ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (current) {
                    null -> Overview(prefs) { sectionName = it.name }
                    PrefSection.STYLE -> StyleSection(prefs, viewModel::update)
                    PrefSection.ALLERGIES -> AllergySection(prefs, viewModel::update)
                    PrefSection.EVERYDAY -> EverydaySection(prefs, viewModel::update)
                    PrefSection.DISLIKES -> DislikeSection(prefs, viewModel::removeDislike)
                }
            }
        }
    }
}

@Composable
private fun Overview(prefs: FoodPreferences, onOpen: (PrefSection) -> Unit) {
    Text(stringResource(R.string.prefs_text), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
    PrefSection.entries.forEach { section ->
        GlassCard(Modifier.fillMaxWidth(), onClick = { onOpen(section) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        sectionSummary(section, prefs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Neon.TextSecondary)
            }
        }
    }
}

@Composable
private fun sectionSummary(section: PrefSection, prefs: FoodPreferences): String {
    val none = stringResource(R.string.prefs_none)
    return when (section) {
        PrefSection.STYLE -> (
            listOf(stringResource(prefs.style.labelRes)) +
                prefs.relevantExcluded.sortedBy { it.ordinal }.map { stringResource(R.string.prefs_no_x, stringResource(it.labelRes)) }
            ).joinToString(" · ")
        PrefSection.ALLERGIES -> prefs.intolerances.sortedBy { it.ordinal }.map { stringResource(it.labelRes) }
            .joinToString(", ").ifEmpty { none }
        PrefSection.EVERYDAY -> listOfNotNull(
            stringResource(prefs.cooking.labelRes).takeIf { prefs.cooking != CookingTime.NORMAL },
            stringResource(R.string.budget_small).takeIf { prefs.budget == Budget.SMALL },
            stringResource(R.string.prefs_meal_prep).takeIf { prefs.mealPrep },
        ).joinToString(" · ").ifEmpty { none }
        PrefSection.DISLIKES -> prefs.dislikes.joinToString(", ").ifEmpty { none }
    }
}

/** Kurze Zusammenfassung für die Karte in der Auswertung. */
@Composable
fun preferencesSummary(prefs: FoodPreferences): String {
    if (prefs.isEmpty) return stringResource(R.string.prefs_empty_summary)
    val excluded = prefs.relevantExcluded.sortedBy { it.ordinal }.map { stringResource(it.labelRes) }
    val intolerances = prefs.intolerances.sortedBy { it.ordinal }.map { stringResource(it.labelRes) }
    val noText = stringResource(R.string.prefs_no_x, excluded.joinToString(", "))
    val withoutText = stringResource(R.string.prefs_without_x, intolerances.joinToString(", "))
    return listOfNotNull(
        stringResource(prefs.style.labelRes).takeIf { prefs.style != DietStyle.ALL },
        noText.takeIf { excluded.isNotEmpty() },
        withoutText.takeIf { intolerances.isNotEmpty() },
        stringResource(prefs.cooking.labelRes).takeIf { prefs.cooking != CookingTime.NORMAL },
        stringResource(R.string.budget_small).takeIf { prefs.budget == Budget.SMALL },
        stringResource(R.string.prefs_meal_prep).takeIf { prefs.mealPrep },
        prefs.dislikes.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.prefs_dislike_count, it.size) },
    ).joinToString(" · ")
}

@Composable
private fun StyleSection(prefs: FoodPreferences, update: ((FoodPreferences) -> FoodPreferences) -> Unit) {
    DietStyle.entries.forEach { style ->
        RadioRow(stringResource(style.labelRes), stringResource(style.descriptionRes), prefs.style == style) {
            update { it.copy(style = style) }
        }
    }
    val allowed = FoodPreferences.allowedExclusions(prefs.style)
    if (allowed.isNotEmpty()) {
        Header(stringResource(R.string.prefs_excluded_title))
        allowed.forEach { food ->
            CheckRow(stringResource(food.labelRes), food in prefs.excluded) { checked ->
                update { it.copy(excluded = if (checked) it.excluded + food else it.excluded - food) }
            }
        }
    }
}

@Composable
private fun AllergySection(prefs: FoodPreferences, update: ((FoodPreferences) -> FoodPreferences) -> Unit) {
    val (allergens, intolerances) = Intolerance.entries.partition { it.allergen }
    Header(stringResource(R.string.prefs_intolerances_title))
    intolerances.forEach { CheckIntolerance(it, prefs, update) }
    Header(stringResource(R.string.prefs_allergens_title))
    allergens.forEach { CheckIntolerance(it, prefs, update) }
    Text(stringResource(R.string.prefs_allergy_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
}

@Composable
private fun CheckIntolerance(item: Intolerance, prefs: FoodPreferences, update: ((FoodPreferences) -> FoodPreferences) -> Unit) {
    CheckRow(stringResource(item.labelRes), item in prefs.intolerances) { checked ->
        update { it.copy(intolerances = if (checked) it.intolerances + item else it.intolerances - item) }
    }
}

@Composable
private fun EverydaySection(prefs: FoodPreferences, update: ((FoodPreferences) -> FoodPreferences) -> Unit) {
    Header(stringResource(R.string.prefs_cooking_title))
    CookingTime.entries.forEach { time ->
        RadioRow(stringResource(time.labelRes), null, prefs.cooking == time) { update { it.copy(cooking = time) } }
    }
    Header(stringResource(R.string.prefs_budget_title))
    Budget.entries.forEach { budget ->
        RadioRow(stringResource(budget.labelRes), null, prefs.budget == budget) { update { it.copy(budget = budget) } }
    }
    Header(stringResource(R.string.prefs_meal_prep_title))
    Row(
        Modifier.fillMaxWidth().clickable { update { it.copy(mealPrep = !it.mealPrep) } }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.prefs_meal_prep), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = prefs.mealPrep, onCheckedChange = { checked -> update { it.copy(mealPrep = checked) } })
    }
}

@Composable
private fun DislikeSection(prefs: FoodPreferences, onRemove: (String) -> Unit) {
    if (prefs.dislikes.isEmpty()) {
        Text(stringResource(R.string.prefs_dislikes_empty), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary)
    }
    prefs.dislikes.forEach { name ->
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(name) }) { Icon(Icons.Filled.Close, stringResource(R.string.prefs_remove)) }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Neon.Violet, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Neon.Cyan))
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
        }
    }
}

@Composable
private fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Neon.Cyan, checkmarkColor = Neon.OnNeon),
        )
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
    }
}

private val DietStyle.labelRes: Int
    get() = when (this) {
        DietStyle.ALL -> R.string.style_all
        DietStyle.FLEXITARIAN -> R.string.style_flexitarian
        DietStyle.PESCETARIAN -> R.string.style_pescetarian
        DietStyle.VEGETARIAN -> R.string.style_vegetarian
        DietStyle.VEGAN -> R.string.style_vegan
    }

private val DietStyle.descriptionRes: Int
    get() = when (this) {
        DietStyle.ALL -> R.string.style_all_desc
        DietStyle.FLEXITARIAN -> R.string.style_flexitarian_desc
        DietStyle.PESCETARIAN -> R.string.style_pescetarian_desc
        DietStyle.VEGETARIAN -> R.string.style_vegetarian_desc
        DietStyle.VEGAN -> R.string.style_vegan_desc
    }

private val ExcludedFood.labelRes: Int
    get() = when (this) {
        ExcludedFood.PORK -> R.string.food_pork
        ExcludedFood.BEEF -> R.string.food_beef
        ExcludedFood.POULTRY -> R.string.food_poultry
        ExcludedFood.FISH -> R.string.food_fish
        ExcludedFood.SEAFOOD -> R.string.food_seafood
    }

private val Intolerance.labelRes: Int
    get() = when (this) {
        Intolerance.LACTOSE -> R.string.intol_lactose
        Intolerance.GLUTEN -> R.string.intol_gluten
        Intolerance.FRUCTOSE -> R.string.intol_fructose
        Intolerance.HISTAMINE -> R.string.intol_histamine
        Intolerance.NUTS -> R.string.allergen_nuts
        Intolerance.PEANUTS -> R.string.allergen_peanuts
        Intolerance.EGG -> R.string.allergen_egg
        Intolerance.SOY -> R.string.allergen_soy
        Intolerance.FISH -> R.string.allergen_fish
        Intolerance.SHELLFISH -> R.string.allergen_shellfish
        Intolerance.CELERY -> R.string.allergen_celery
        Intolerance.MUSTARD -> R.string.allergen_mustard
        Intolerance.SESAME -> R.string.allergen_sesame
    }

private val CookingTime.labelRes: Int
    get() = when (this) {
        CookingTime.LITTLE -> R.string.cooking_little
        CookingTime.NORMAL -> R.string.cooking_normal
        CookingTime.LIKES -> R.string.cooking_likes
    }

private val Budget.labelRes: Int
    get() = when (this) {
        Budget.SMALL -> R.string.budget_small
        Budget.NORMAL -> R.string.budget_normal
    }
