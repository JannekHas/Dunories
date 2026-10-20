package de.kalorienapp.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.domain.model.AmountUnit
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

/** Nährwertfelder im Editor; [extra] = nur im ausklappbaren Bereich „Weitere Nährwerte“. */
enum class NutrientField(val extra: Boolean = false) {
    KCAL, PROTEIN, CARBS, FAT,
    SATURATED_FAT(extra = true), SUGAR(extra = true), FIBER(extra = true), SALT(extra = true),
}

/** Eingabezustand. Alle Nährwertfelder gelten pro 100 g bzw. 100 ml. */
data class EntryFormState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val mealType: MealType = MealType.SNACK,
    val amount: String = "",
    val unit: AmountUnit = AmountUnit.GRAM,
    val per100: Map<NutrientField, String> = emptyMap(),
    val showExtras: Boolean = false,
) {
    val amountValue: Double? get() = amount.parseDecimal()?.takeIf { it > 0 }
    val amountError get() = amount.isNotBlank() && amountValue == null

    fun value(field: NutrientField): String = per100[field].orEmpty()

    fun isError(field: NutrientField): Boolean {
        val text = value(field)
        return text.isNotBlank() && text.parseDecimal() == null
    }

    val isValid: Boolean
        get() = name.isNotBlank() && amountValue != null &&
            value(NutrientField.KCAL).parseDecimal() != null &&
            NutrientField.entries.none(::isError)

    /** Werte pro 100 g/ml; leere Hauptfelder zählen als 0, leere Zusatzfelder als „nicht angegeben“. */
    fun per100Nutrients() = Nutrients(
        kcal = number(NutrientField.KCAL) ?: 0.0,
        proteinG = number(NutrientField.PROTEIN) ?: 0.0,
        carbsG = number(NutrientField.CARBS) ?: 0.0,
        fatG = number(NutrientField.FAT) ?: 0.0,
        saturatedFatG = number(NutrientField.SATURATED_FAT),
        sugarG = number(NutrientField.SUGAR),
        fiberG = number(NutrientField.FIBER),
        saltG = number(NutrientField.SALT),
    )

    /** Werte für die eingegebene Menge, oder null solange die Menge fehlt. */
    fun totalNutrients(): Nutrients? = amountValue?.let { per100Nutrients().per100ToAmount(it) }

    private fun number(field: NutrientField) = value(field).parseDecimal()
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
            _state.value = if (entry != null) formFor(entry) else EntryFormState(
                isLoading = false,
                mealType = route.mealType?.let { name -> MealType.entries.find { it.name == name } }
                    ?: MealType.forHour(LocalTime.now().hour),
            )
        }
    }

    /** Gespeichert sind Absolutwerte → für den Editor auf 100 g/ml umrechnen. */
    private fun formFor(entry: FoodEntry): EntryFormState {
        // Alt-Einträge ohne Menge: als 100 g behandeln, damit die Summe gleich bleibt.
        val amount = entry.amount?.takeIf { it > 0 } ?: 100.0
        val p = entry.nutrients.amountToPer100(amount)
        val per100 = buildMap {
            put(NutrientField.KCAL, p.kcal.toInputString())
            put(NutrientField.PROTEIN, p.proteinG.toInputString())
            put(NutrientField.CARBS, p.carbsG.toInputString())
            put(NutrientField.FAT, p.fatG.toInputString())
            p.saturatedFatG?.let { put(NutrientField.SATURATED_FAT, it.toInputString()) }
            p.sugarG?.let { put(NutrientField.SUGAR, it.toInputString()) }
            p.fiberG?.let { put(NutrientField.FIBER, it.toInputString()) }
            p.saltG?.let { put(NutrientField.SALT, it.toInputString(2)) }
        }
        return EntryFormState(
            isLoading = false,
            isNew = false,
            name = entry.name,
            mealType = entry.mealType,
            amount = amount.toInputString(),
            unit = entry.unit,
            per100 = per100,
            showExtras = per100.keys.any { it.extra },
        )
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }

    fun onMealTypeChange(value: MealType) = _state.update { it.copy(mealType = value) }

    fun onAmountChange(value: String) = _state.update { it.copy(amount = value) }

    fun onUnitChange(value: AmountUnit) = _state.update { it.copy(unit = value) }

    fun onNutrientChange(field: NutrientField, value: String) =
        _state.update { it.copy(per100 = it.per100 + (field to value)) }

    fun toggleExtras() = _state.update { it.copy(showExtras = !it.showExtras) }

    fun save(onDone: () -> Unit) {
        val form = _state.value
        val total = form.totalNutrients()
        if (!form.isValid || total == null) return
        val entry = FoodEntry(
            id = existing?.id ?: 0,
            date = existing?.date ?: LocalDate.ofEpochDay(route.epochDay),
            mealType = form.mealType,
            name = form.name.trim(),
            amount = form.amountValue,
            unit = form.unit,
            nutrients = total,
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
