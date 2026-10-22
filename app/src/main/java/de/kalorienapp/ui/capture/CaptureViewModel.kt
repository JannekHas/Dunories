package de.kalorienapp.ui.capture

import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import de.kalorienapp.capture.AnalysisResult
import de.kalorienapp.capture.AnalysisStage
import de.kalorienapp.capture.FoodAnalyzer
import de.kalorienapp.data.remote.gemini.GeminiError
import de.kalorienapp.data.remote.gemini.GeminiException
import de.kalorienapp.data.repository.FoodEntryRepository
import de.kalorienapp.data.repository.FoodLibraryRepository
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.FrequentFood
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.ui.food.FoodForm
import de.kalorienapp.ui.navigation.CaptureRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

/** Eine Komponente im Vorschlag (bearbeitbar, erst nach Bestätigung gespeichert). */
data class ProposalItem(val id: Long, val form: FoodForm, val source: EntrySource)

enum class CaptureError { NO_API_KEY, REJECTED, NETWORK, QUOTA, OVERLOADED, INVALID_RESPONSE, NO_FOOD, IMAGE, UNKNOWN }

sealed interface CaptureStep {
    data object Camera : CaptureStep
    data object TextInput : CaptureStep
    data class Analyzing(val stage: AnalysisStage) : CaptureStep
    data class UnknownBarcode(val barcode: String) : CaptureStep
    data class QuickPick(val selected: Set<String> = emptySet()) : CaptureStep
    data class Failed(val error: CaptureError) : CaptureStep
    data class Proposal(val items: List<ProposalItem>, val expandedId: Long?) : CaptureStep {
        val canSave: Boolean get() = items.isNotEmpty() && items.all { it.form.isValid }
    }
}

data class CaptureUiState(
    val step: CaptureStep = CaptureStep.Camera,
    val mealType: MealType,
    val description: String = "",
)

class CaptureViewModel(
    savedStateHandle: SavedStateHandle,
    private val analyzer: FoodAnalyzer,
    private val repository: FoodEntryRepository,
    private val library: FoodLibraryRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CaptureRoute>()

    private val _state = MutableStateFlow(
        CaptureUiState(mealType = MealType.fromRouteArg(route.mealType) ?: MealType.forHour(LocalTime.now().hour)),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    val favorites: StateFlow<List<FavoriteFood>> =
        library.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Häufig gegessen – ohne Lebensmittel, die schon Favorit sind. */
    val frequent: StateFlow<List<FrequentFood>> =
        combine(library.frequent(), library.favorites) { frequent, favorites ->
            val favoriteNames = favorites.map { it.name.lowercase() }.toSet()
            frequent.filterNot { it.name.lowercase() in favoriteNames }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Letzte Eingabe – für „Erneut versuchen“. */
    private sealed interface Input {
        data class Photo(val uri: Uri, val skipBarcode: Boolean = false) : Input
        data class Text(val description: String) : Input
    }

    private var lastInput: Input? = null
    private var analysisJob: Job? = null
    private var nextItemId = 1L

    fun onPhoto(uri: Uri) = analyze(Input.Photo(uri))

    /** Kamera konnte kein Foto speichern. */
    fun onCaptureFailed() = setStep(CaptureStep.Failed(CaptureError.IMAGE))

    fun analyzeLastPhotoWithAi() {
        val photo = lastInput as? Input.Photo ?: return
        analyze(photo.copy(skipBarcode = true))
    }

    fun showTextInput() = setStep(CaptureStep.TextInput)

    // --- Schnellauswahl (Favoriten / häufig gegessen) ---

    fun showQuickPick() = setStep(CaptureStep.QuickPick())

    fun toggleQuickPick(key: String) = _state.update { s ->
        val step = s.step as? CaptureStep.QuickPick ?: return@update s
        val selected = if (key in step.selected) step.selected - key else step.selected + key
        s.copy(step = step.copy(selected = selected))
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch { library.removeFavorite(id) }
    }

    fun confirmQuickPick() {
        val step = _state.value.step as? CaptureStep.QuickPick ?: return
        val drafts = favorites.value.filter { favoriteKey(it) in step.selected }.map {
            FoodDraft(it.name, it.amount, it.unit, it.per100, EntrySource.MANUAL)
        } + frequent.value.filter { frequentKey(it) in step.selected }.map {
            FoodDraft(it.name, it.amount, it.unit, it.per100, EntrySource.MANUAL)
        }
        if (drafts.isNotEmpty()) setStep(proposalOf(drafts))
    }

    fun onDescriptionChange(text: String) = _state.update { it.copy(description = text) }

    fun submitDescription() {
        val text = _state.value.description.trim()
        if (text.isNotEmpty()) analyze(Input.Text(text))
    }

    fun retry() {
        lastInput?.let(::analyze)
    }

    /** Zurück zur Kamera; laufende Analyse wird abgebrochen. */
    fun backToCamera() {
        analysisJob?.cancel()
        setStep(CaptureStep.Camera)
    }

    fun onMealTypeChange(mealType: MealType) = _state.update { it.copy(mealType = mealType) }

    // --- Vorschlag bearbeiten ---

    fun updateItem(id: Long, form: FoodForm) = updateProposal { p ->
        p.copy(items = p.items.map { if (it.id == id) it.copy(form = form) else it })
    }

    fun removeItem(id: Long) = updateProposal { p -> p.copy(items = p.items.filterNot { it.id == id }) }

    fun addItem() = updateProposal { p ->
        val item = ProposalItem(nextItemId++, FoodForm(), EntrySource.MANUAL)
        p.copy(items = p.items + item, expandedId = item.id)
    }

    fun toggleExpanded(id: Long) = updateProposal { p -> p.copy(expandedId = if (p.expandedId == id) null else id) }

    fun saveAll(onDone: () -> Unit) {
        val current = _state.value
        val proposal = current.step as? CaptureStep.Proposal ?: return
        if (!proposal.canSave) return
        val date = LocalDate.ofEpochDay(route.epochDay)
        val now = System.currentTimeMillis()
        val entries = proposal.items.mapIndexedNotNull { index, item ->
            val total = item.form.totalNutrients() ?: return@mapIndexedNotNull null
            FoodEntry(
                date = date,
                mealType = current.mealType,
                name = item.form.name.trim(),
                amount = item.form.amountValue,
                unit = item.form.unit,
                nutrients = total,
                source = item.source,
                createdAt = now + index, // Reihenfolge des Vorschlags beibehalten
            )
        }
        viewModelScope.launch {
            entries.forEach { repository.save(it) }
            onDone()
        }
    }

    // --- intern ---

    private fun analyze(input: Input) {
        lastInput = input
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val onStage: (AnalysisStage) -> Unit = { stage -> setStep(CaptureStep.Analyzing(stage)) }
            try {
                val step = when (input) {
                    is Input.Text -> proposalOf(analyzer.analyzeText(input.description, onStage))
                    is Input.Photo -> if (input.skipBarcode) {
                        proposalOf(analyzer.analyzePhotoWithAi(input.uri, onStage))
                    } else {
                        when (val result = analyzer.analyzePhoto(input.uri, onStage)) {
                            is AnalysisResult.Drafts -> proposalOf(result.drafts)
                            is AnalysisResult.UnknownBarcode -> CaptureStep.UnknownBarcode(result.barcode)
                        }
                    }
                }
                setStep(step)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStep(CaptureStep.Failed(e.toCaptureError()))
            }
        }
    }

    private fun proposalOf(drafts: List<FoodDraft>): CaptureStep.Proposal {
        val items = drafts.map { d ->
            ProposalItem(nextItemId++, FoodForm.of(d.name, d.amount, d.unit, d.per100), d.source)
        }
        // Einzelne Komponente (z. B. Barcode) direkt aufgeklappt, damit die Menge sofort eingetragen werden kann.
        return CaptureStep.Proposal(items, expandedId = items.singleOrNull()?.id)
    }

    companion object {
        fun favoriteKey(f: FavoriteFood) = "fav:${f.id}"
        fun frequentKey(f: FrequentFood) = "freq:${f.name.lowercase()}|${f.unit}"
    }

    private fun setStep(step: CaptureStep) = _state.update { it.copy(step = step) }

    private fun updateProposal(transform: (CaptureStep.Proposal) -> CaptureStep.Proposal) = _state.update { s ->
        val p = s.step as? CaptureStep.Proposal ?: return@update s
        s.copy(step = transform(p))
    }

    private fun Exception.toCaptureError(): CaptureError = when (this) {
        is GeminiException -> when (error) {
            GeminiError.NO_API_KEY -> CaptureError.NO_API_KEY
            GeminiError.REJECTED -> CaptureError.REJECTED
            GeminiError.NETWORK -> CaptureError.NETWORK
            GeminiError.QUOTA -> CaptureError.QUOTA
            GeminiError.OVERLOADED -> CaptureError.OVERLOADED
            GeminiError.INVALID_RESPONSE -> CaptureError.INVALID_RESPONSE
            GeminiError.NO_FOOD -> CaptureError.NO_FOOD
        }
        is ImageDecoder.DecodeException -> CaptureError.IMAGE
        is IOException -> CaptureError.NETWORK
        else -> CaptureError.UNKNOWN
    }
}
