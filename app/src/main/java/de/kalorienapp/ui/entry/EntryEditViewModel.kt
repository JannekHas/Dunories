package de.kalorienapp.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.food.FoodForm
import de.kalorienapp.ui.navigation.EntryEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class EntryEditState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val mealType: MealType = MealType.SNACK,
    val form: FoodForm = FoodForm(),
)

class EntryEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: FoodEntryRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<EntryEditRoute>()
    private var existing: FoodEntry? = null

    private val _state = MutableStateFlow(EntryEditState())
    val state: StateFlow<EntryEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = route.entryId.takeIf { it != 0L }?.let { repository.get(it) }
            existing = entry
            _state.value = if (entry != null) {
                // Gespeichert sind Absolutwerte → für den Editor auf 100 g/ml umrechnen.
                // Alt-Einträge ohne Menge: als 100 g behandeln, damit die Summe gleich bleibt.
                val amount = entry.amount?.takeIf { it > 0 } ?: 100.0
                EntryEditState(
                    isLoading = false,
                    isNew = false,
                    mealType = entry.mealType,
                    form = FoodForm.of(entry.name, amount, entry.unit, entry.nutrients.amountToPer100(amount)),
                )
            } else {
                EntryEditState(
                    isLoading = false,
                    mealType = MealType.fromRouteArg(route.mealType) ?: MealType.forHour(LocalTime.now().hour),
                )
            }
        }
    }

    fun onFormChange(form: FoodForm) = _state.update { it.copy(form = form) }

    fun onMealTypeChange(value: MealType) = _state.update { it.copy(mealType = value) }

    /** Schutz gegen Doppeltippen: sonst würde dasselbe Essen zweimal gespeichert. */
    private var saving = false

    fun save(onDone: () -> Unit) {
        val current = _state.value
        val form = current.form
        val total = form.totalNutrients()
        if (!form.isValid || total == null || saving) return
        saving = true
        val entry = FoodEntry(
            id = existing?.id ?: 0,
            date = existing?.date ?: LocalDate.ofEpochDay(route.epochDay),
            mealType = current.mealType,
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
