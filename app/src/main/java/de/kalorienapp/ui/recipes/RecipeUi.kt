package de.kalorienapp.ui.recipes

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.domain.model.Effort
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.domain.model.Recipe
import de.kalorienapp.domain.model.RecipeIngredient
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.format
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

/** Farben wie in Tagesübersicht und Statistik. */
object MacroColors {
    val Kcal = Neon.Orange
    val Protein = Neon.Magenta
    val Carbs = Neon.Yellow
    val Fat = Neon.Green
}

@get:androidx.annotation.StringRes
val Effort.labelRes: Int
    get() = when (this) {
        Effort.EASY -> R.string.effort_easy
        Effort.MEDIUM -> R.string.effort_medium
        Effort.HARD -> R.string.effort_hard
    }

fun Effort.color(): Color = when (this) {
    Effort.EASY -> Neon.Green
    Effort.MEDIUM -> Neon.Yellow
    Effort.HARD -> Neon.Red
}

@Composable
fun minutesText(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.recipe_minutes, minutes)
        rest == 0 -> stringResource(R.string.recipe_hours, hours)
        else -> stringResource(R.string.recipe_hours_minutes, hours, rest)
    }
}

/** „2 Stück“, „200 g“ – mit Faktor für andere Portionszahlen. */
fun RecipeIngredient.quantityText(factor: Double = 1.0): String {
    val q = quantity?.let { formatQuantity(it * factor) }
    return listOfNotNull(q, quantityUnit.takeIf { it.isNotBlank() }).joinToString(" ")
}

/** Kleine Mengen mit einer Nachkommastelle, große bzw. fast ganze gerundet („1,5“, „3“, „350“). */
fun formatQuantity(value: Double): String =
    if (value >= 20 || abs(value - Math.round(value)) < 0.05) Math.round(value).toString() else value.toInputString(1)

private val imageCache = object : LruCache<String, ImageBitmap>(24) {}

/** Lädt ein Rezeptbild aus dem App-Speicher (verkleinert, zwischengespeichert). */
@Composable
fun rememberRecipeImage(path: String?, maxSide: Int = 1280): ImageBitmap? {
    val key = path?.let { "$it@$maxSide" }
    val state by produceState(initialValue = key?.let { imageCache.get(it) }, key) {
        if (key == null || path == null) {
            value = null
            return@produceState
        }
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }?.also { imageCache.put(key, it) }
    }
    return state
}

/** Rezeptbild oder Neon-Platzhalter mit Anfangsbuchstaben. */
@Composable
fun RecipeImage(recipe: Recipe, modifier: Modifier = Modifier, maxSide: Int = 1280) {
    val image = rememberRecipeImage(recipe.imagePath, maxSide)
    if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        val colors = Neon.Rainbow
        val start = (recipe.name.hashCode() and 0x7fffffff) % colors.size
        Box(
            modifier.background(Brush.linearGradient(listOf(colors[start], colors[(start + 2) % colors.size]))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_restaurant),
                contentDescription = null,
                tint = Neon.OnNeon.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** Kompakte Makrozeile für Listen: „575 kcal · P 53 · KH 63 · F 9“. */
@Composable
fun MacroLine(n: Nutrients, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.kcal_value, n.kcal.format()), color = MacroColors.Kcal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        MacroDot(stringResource(R.string.recipe_macro_p, n.proteinG.format()), MacroColors.Protein)
        MacroDot(stringResource(R.string.recipe_macro_c, n.carbsG.format()), MacroColors.Carbs)
        MacroDot(stringResource(R.string.recipe_macro_f, n.fatG.format()), MacroColors.Fat)
    }
}

@Composable
private fun MacroDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary, modifier = Modifier.padding(start = 4.dp))
    }
}

/**
 * Gesamtübersicht: kcal und Makros pro Portion als Neon-Kacheln, aufklappbar alle Nährwerte
 * pro Portion und für das ganze Rezept.
 */
@Composable
fun NutritionOverviewCard(recipe: Recipe, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val perServing = recipe.perServing
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                pluralStringResource(R.plurals.recipe_per_serving_of, recipe.safeServings, recipe.safeServings),
                style = MaterialTheme.typography.labelLarge,
                color = Neon.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonPill(perServing.kcal.format(), stringResource(R.string.unit_kcal), MacroColors.Kcal, Modifier.weight(1f))
                NeonPill(perServing.proteinG.format(), stringResource(R.string.protein), MacroColors.Protein, Modifier.weight(1f))
                NeonPill(perServing.carbsG.format(), stringResource(R.string.carbs_short), MacroColors.Carbs, Modifier.weight(1f))
                NeonPill(perServing.fatG.format(), stringResource(R.string.fat), MacroColors.Fat, Modifier.weight(1f))
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Spacer1()
                        HeaderCell(stringResource(R.string.recipe_col_serving))
                        HeaderCell(stringResource(R.string.recipe_col_total))
                    }
                    val total = recipe.totalNutrients
                    NutrientRow(stringResource(R.string.nutrient_energy), perServing.kcal, total.kcal, R.string.unit_kcal, MacroColors.Kcal)
                    NutrientRow(stringResource(R.string.fat), perServing.fatG, total.fatG, color = MacroColors.Fat)
                    NutrientRow(stringResource(R.string.nutrient_saturated_fat), perServing.saturatedFatG, total.saturatedFatG, sub = true)
                    NutrientRow(stringResource(R.string.carbs), perServing.carbsG, total.carbsG, color = MacroColors.Carbs)
                    NutrientRow(stringResource(R.string.nutrient_sugar), perServing.sugarG, total.sugarG, sub = true)
                    NutrientRow(stringResource(R.string.nutrient_fiber), perServing.fiberG, total.fiberG)
                    NutrientRow(stringResource(R.string.protein), perServing.proteinG, total.proteinG, color = MacroColors.Protein)
                    NutrientRow(stringResource(R.string.nutrient_salt), perServing.saltG, total.saltG, decimals = 1)
                }
            }
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (expanded) R.string.nutrient_details_hide else R.string.nutrient_details_show))
                Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Spacer1() {
    Box(Modifier.weight(1.6f))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
}

@Composable
private fun NutrientRow(
    label: String,
    serving: Double?,
    total: Double?,
    unit: Int = R.string.unit_g,
    color: Color? = null,
    sub: Boolean = false,
    decimals: Int = 0,
) {
    val unitText = stringResource(unit)
    fun text(v: Double?) = v?.let { "${it.format(decimals)} $unitText" } ?: "–"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1.6f), verticalAlignment = Alignment.CenterVertically) {
            if (sub) Box(Modifier.width(14.dp))
            if (color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(
                label,
                style = if (sub) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (sub) Neon.TextSecondary else Neon.TextPrimary,
                modifier = Modifier.padding(start = if (color != null) 6.dp else 0.dp),
            )
        }
        Text(text(serving), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        Text(text(total), style = MaterialTheme.typography.bodyMedium, color = Neon.TextSecondary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Kurzinfos: Dauer · Aufwand. */
@Composable
fun RecipeMetaLine(recipe: Recipe, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        recipe.totalMinutes?.let {
            Text(stringResource(R.string.recipe_duration, minutesText(it)), style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
        }
        recipe.effort?.let {
            Text(stringResource(it.labelRes), style = MaterialTheme.typography.labelLarge, color = it.color())
        }
    }
}

/** Klickbare Zeile ohne Ripple-Probleme in Listen. */
fun Modifier.clickableIf(onClick: (() -> Unit)?): Modifier = if (onClick != null) clickable(onClick = onClick) else this
