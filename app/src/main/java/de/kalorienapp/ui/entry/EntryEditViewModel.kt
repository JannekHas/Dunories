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
import de.kalorienapp.ui.food.DishForm
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
    /** Gericht mit Zutaten; null = einzelnes Lebensmittel in [form]. */
    val dish: DishForm? = null,
) {
    val isValid: Boolean get() = dish?.isValid ?: form.isValid
}

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
                    dish = entry.takeIf { it.isDish }?.let { DishForm.of(it.name, it.components) { nextPartId++ } },
                )
            } else {
                EntryEditState(
                    isLoading = false,
                    mealType = MealType.fromRouteArg(route.mealType) ?: MealType.forHour(LocalTime.now().hour),
                )
            }
        }
    }

    private var nextPartId = 1L

    fun onFormChange(form: FoodForm) = _state.update { it.copy(form = form) }

    fun onDishChange(dish: DishForm) = _state.update { it.copy(dish = dish) }

    fun addDishPart() = _state.update { s -> s.copy(dish = s.dish?.withNewPart(nextPartId++)) }

    fun onMealTypeChange(value: MealType) = _state.update { it.copy(mealType = value) }

    /** Schutz gegen Doppeltippen: sonst würde dasselbe Essen zweimal gespeichert. */
    private var saving = false

    fun save(onDone: () -> Unit) {
        val current = _state.value
        if (!current.isValid || saving) return
        val dish = current.dish
        val entry = if (dish != null) {
            base(current).copy(
                name = dish.name.trim(),
                amount = dish.totalAmount,
                unit = dish.unit,
                nutrients = dish.totalNutrients(),
                components = dish.components(),
            )
        } else {
            val form = current.form
            val total = form.totalNutrients() ?: return
            base(current).copy(name = form.name.trim(), amount = form.amountValue, unit = form.unit, nutrients = total)
        }
        saving = true
        viewModelScope.launch {
            // Neue Einträge können in einen gleichen von eben einfließen (Zusammenfassen), bearbeitete nicht.
            if (existing == null) repository.add(entry) else repository.save(entry)
            onDone()
        }
    }

    /** Vorhandener Eintrag (Reihenfolge, Erfassungszeit bleiben) bzw. Grundgerüst für einen neuen. */
    private fun base(current: EntryEditState): FoodEntry {
        val old = existing ?: return FoodEntry(
            date = LocalDate.ofEpochDay(route.epochDay),
            mealType = current.mealType,
            name = "",
            amount = null,
            nutrients = Nutrients.ZERO,
            source = EntrySource.MANUAL,
        )
        // Andere Mahlzeit → hinten anstellen statt an der alten Position einsortieren.
        return old.copy(mealType = current.mealType, sortKey = old.sortKey.takeIf { current.mealType == old.mealType })
    }

    /** Gericht in einzelne Einträge aufteilen (z. B. wenn die KI zu viel zusammengefasst hat). */
    fun splitDish(onDone: () -> Unit) {
        val old = existing ?: return
        val dish = _state.value.dish ?: return
        if (saving) return
        saving = true
        val mealType = _state.value.mealType
        val parts = dish.components().mapIndexed { index, c ->
            FoodEntry(
                date = old.date,
                mealType = mealType,
                name = c.name,
                amount = c.amount,
                unit = c.unit,
                nutrients = c.nutrients,
                source = old.source,
                createdAt = old.createdAt + index,
                sortKey = old.sortKey?.takeIf { mealType == old.mealType }?.plus(index),
            )
        }
        viewModelScope.launch {
            repository.delete(old.id)
            repository.saveAll(parts)
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
