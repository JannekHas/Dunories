package de.kalorienapp.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.components.parseDecimal
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.navigation.EntryEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

enum class NutrientField { KCAL, PROTEIN, CARBS, FAT }

/** Bezugswerte für das proportionale Skalieren der Nährwerte bei Mengenänderung. */
data class ScaleBase(val amountG: Double, val nutrients: Nutrients)

data class EntryFormState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val mealType: MealType = MealType.SNACK,
    val amount: String = "",
    val kcal: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val scaleBase: ScaleBase? = null,
) {
    val amountValue: Double? get() = amount.parseDecimal()
    val amountError get() = amount.isNotBlank() && (amountValue == null || amountValue!! <= 0)
    val kcalError get() = kcal.isNotBlank() && kcal.parseDecimal() == null
    val proteinError get() = macroError(protein)
    val carbsError get() = macroError(carbs)
    val fatError get() = macroError(fat)

    val isValid: Boolean
        get() = name.isNotBlank() && kcal.parseDecimal() != null &&
            !amountError && !kcalError && !proteinError && !carbsError && !fatError

    /** Leere Makrofelder zählen als 0. */
    fun nutrients() = Nutrients(
        kcal = kcal.parseDecimal() ?: 0.0,
        proteinG = protein.parseDecimal() ?: 0.0,
        carbsG = carbs.parseDecimal() ?: 0.0,
        fatG = fat.parseDecimal() ?: 0.0,
    )

    fun currentScaleBase(): ScaleBase? =
        amountValue?.takeIf { it > 0 && !amountError }?.let { ScaleBase(it, nutrients()) }

    private fun macroError(text: String) = text.isNotBlank() && text.parseDecimal() == null
}

class EntryEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodEntryRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryEditRoute>()
    private var existing: FoodEntry? = null

    private val _state = MutableStateFlow(EntryFormState())
    val state: StateFlow<EntryFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = route.entryId.takeIf { it != 0L }?.let { repository.get(it) }
            existing = entry
            _state.value = if (entry != null) {
                val n = entry.nutrients
                EntryFormState(
                    isLoading = false,
                    isNew = false,
                    name = entry.name,
                    mealType = entry.mealType,
                    amount = entry.amountG?.toInputString() ?: "",
                    kcal = n.kcal.toInputString(0),
                    protein = n.proteinG.toInputString(),
                    carbs = n.carbsG.toInputString(),
                    fat = n.fatG.toInputString(),
                    scaleBase = entry.amountG?.takeIf { it > 0 }?.let { ScaleBase(it, n) },
                )
            } else {
                EntryFormState(
                    isLoading = false,
                    mealType = route.mealType?.let { name -> MealType.entries.find { it.name == name } }
                        ?: MealType.forHour(LocalTime.now().hour),
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun onMealTypeChange(value: MealType) = _state.update { it.copy(mealType = value) }

    /** Bei bekannten Bezugswerten werden die Nährwerte proportional zur neuen Menge skaliert. */
    fun onAmountChange(value: String) = _state.update { current ->
        val updated = current.copy(amount = value)
        val base = current.scaleBase
        val newAmount = updated.amountValue
        if (base != null && newAmount != null && newAmount > 0) {
            val scaled = base.nutrients * (newAmount / base.amountG)
            updated.copy(
                kcal = scaled.kcal.toInputString(0),
                protein = scaled.proteinG.toInputString(),
                carbs = scaled.carbsG.toInputString(),
                fat = scaled.fatG.toInputString(),
            )
        } else {
            updated
        }
    }

    /** Manuelle Änderung eines Nährwerts setzt neue Bezugswerte für das Skalieren. */
    fun onNutrientChange(field: NutrientField, value: String) = _state.update { current ->
        val updated = when (field) {
            NutrientField.KCAL -> current.copy(kcal = value)
            NutrientField.PROTEIN -> current.copy(protein = value)
            NutrientField.CARBS -> current.copy(carbs = value)
            NutrientField.FAT -> current.copy(fat = value)
        }
        updated.copy(scaleBase = updated.currentScaleBase())
    }

    fun save(onDone: () -> Unit) {
        val form = _state.value
        if (!form.isValid) return
        val entry = FoodEntry(
            id = existing?.id ?: 0,
            date = existing?.date ?: LocalDate.ofEpochDay(route.epochDay),
            mealType = form.mealType,
            name = form.name.trim(),
            amountG = form.amountValue,
            nutrients = form.nutrients(),
            source = existing?.source ?: EntrySource.MANUAL,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            repository.save(entry)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = existing?.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}
