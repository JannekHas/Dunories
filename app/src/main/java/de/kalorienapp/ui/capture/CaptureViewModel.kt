package de.kalorienapp.ui.capture

import de.kalorienapp.data.AppSettings
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
import de.kalorienapp.data.repository.MealPlanRepository
import de.kalorienapp.domain.model.EntrySource
import de.kalorienapp.domain.model.FavoriteFood
import de.kalorienapp.domain.model.MealCombo
import de.kalorienapp.domain.model.FrequentFood
import de.kalorienapp.domain.model.FoodDraft
import de.kalorienapp.domain.model.FoodEntry
import de.kalorienapp.domain.model.MealType
import de.kalorienapp.domain.model.Nutrients
import de.kalorienapp.ui.food.DishForm
import de.kalorienapp.ui.food.DishPart
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
data class ProposalItem(
    val id: Long,
    val form: FoodForm,
    val source: EntrySource,
    /** Gericht mit Zutaten (z. B. Spaghetti Bolognese); null = einzelnes Lebensmittel in [form]. */
    val dish: DishForm? = null,
    /** Beim Speichern als eigener Barcode merken (Open Food Facts kennt ihn nicht bzw. schon eigener). */
    val barcode: String? = null,
    /** Aus der gemeinsamen Sammlung (von anderen Nutzern) → Hinweis „kurz prüfen“. */
    val community: Boolean = false,
) {
    val isValid: Boolean get() = dish?.isValid ?: form.isValid

    fun totalNutrients(): Nutrients? = dish?.totalNutrients() ?: form.totalNutrients()
}

enum class CaptureError { NO_API_KEY, REJECTED, NETWORK, QUOTA, DAILY_LIMIT, NEEDS_ACCOUNT, NEEDS_PREMIUM, OVERLOADED, INVALID_RESPONSE, NO_FOOD, IMAGE, UNKNOWN }

sealed interface CaptureStep {
    data object Camera : CaptureStep
    data object TextInput : CaptureStep
    data class Analyzing(val stage: AnalysisStage) : CaptureStep
    data class UnknownBarcode(val barcode: String) : CaptureStep
    data class QuickPick(val selected: Set<String> = emptySet()) : CaptureStep
    data class Failed(val error: CaptureError) : CaptureStep
    /** Foto ansehen, bevor es zur KI geht: neu machen, etwas dazu sagen oder abschicken (Tester-Wunsch Marek). */
    data class Review(val uri: Uri, val label: Boolean) : CaptureStep
    data class Proposal(val items: List<ProposalItem>, val expandedId: Long?) : CaptureStep {
        val canSave: Boolean get() = items.isNotEmpty() && items.all { it.isValid }
    }
}

data class CaptureUiState(
    val step: CaptureStep = CaptureStep.Camera,
    val mealType: MealType,
    val description: String = "",
    /** Kamera/Galerie lesen eine Nährwerttabelle ab statt Essen zu schätzen. */
    val labelMode: Boolean = false,
    /** Im Vorschlag wird gerade ein weiteres Foto erkannt. */
    val adding: Boolean = false,
    val addError: CaptureError? = null,
    /** Was der Nutzer in der Foto-Vorschau dazu sagt („Vollkorntoast mit Frischkäse“). */
    val hint: String = "",
)

class CaptureViewModel(
    savedStateHandle: SavedStateHandle,
    private val analyzer: FoodAnalyzer,
    private val repository: FoodEntryRepository,
    private val library: FoodLibraryRepository,
    private val mealPlan: MealPlanRepository,
    private val settings: AppSettings? = null,
    /** Eigene Barcodes (Produkte, die Open Food Facts nicht kennt) – gemerkt beim Speichern. */
    private val ownBarcodes: de.kalorienapp.data.db.dao.OwnBarcodeDao? = null,
    /** Gemeinsame Barcode-Sammlung: Etikett-Werte unbekannter Produkte teilen. */
    private val community: de.kalorienapp.data.remote.BarcodeCommunityClient? = null,
    /** Reste / offene Mengen – in der Schnellauswahl oben (nicht im Ernährungsplan). */
    private val leftoverRepository: de.kalorienapp.data.repository.LeftoverRepository? = null,
    /** KI-Warteschlange: bei Überlastung/ohne Netz im Hintergrund weiter versuchen. */
    private val aiQueue: de.kalorienapp.capture.AiQueue? = null,
) : ViewModel() {

    /** Im Vorschlag selbst angelegte (abgetippte) Lebensmittel – zählen für den Tipp „Barcode statt Abtippen“. */
    private val typedItemIds = mutableSetOf<Long>()

    private val route = savedStateHandle.toRoute<CaptureRoute>()

    /** Erfasstes landet im Ernährungsplan (Plan-Tag [CaptureRoute.planDay]) statt im Tagebuch. */
    val planMode: Boolean = route.planDay >= 0 && !route.fromPlan

    /** „Manuell“ im Plan: leeres Lebensmittel im Vorschlag statt des Eintrag-Editors. */
    fun startManual() {
        setStep(proposalOf(emptyList()))
        addItem()
    }

    private val _state = MutableStateFlow(
        CaptureUiState(mealType = MealType.fromRouteArg(route.mealType) ?: MealType.forHour(LocalTime.now().hour)),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** Offene Reste (auch eingefrorene) – nur beim normalen Erfassen, nicht im Ernährungsplan. */
    val leftovers: StateFlow<List<de.kalorienapp.domain.model.Leftover>> =
        (leftoverRepository?.open?.takeIf { !planMode } ?: kotlinx.coroutines.flow.flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Rest (ganz oder teilweise) direkt in den Tag/die Mahlzeit dieses Erfassens eintragen. */
    fun takeLeftover(leftover: de.kalorienapp.domain.model.Leftover, amount: Double, onDone: () -> Unit) {
        val repository = leftoverRepository ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            repository.take(leftover, amount, LocalDate.ofEpochDay(route.epochDay), _state.value.mealType)
            onDone()
        }
    }

    fun freezeLeftover(leftover: de.kalorienapp.domain.model.Leftover, frozen: Boolean) {
        viewModelScope.launch { leftoverRepository?.update(leftover.copy(frozen = frozen)) }
    }

    fun discardLeftover(leftover: de.kalorienapp.domain.model.Leftover) {
        viewModelScope.launch { leftoverRepository?.discard(leftover) }
    }

    val combos: StateFlow<List<MealCombo>> =
        library.combos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        data class Photo(val uri: Uri, val skipBarcode: Boolean = false, val hint: String? = null) : Input
        data class Text(val description: String) : Input
        data class Label(val uri: Uri) : Input
    }

    private var lastInput: Input? = null

    /** Aus der KI-Warteschlange geöffnet → beim Speichern dort entfernen. */
    private var queuedId: String? = null

    /** Gerade nicht erkannt → „Im Hintergrund erkennen lassen“ möglich (nicht im Ernährungsplan). */
    val canQueue: Boolean get() = aiQueue != null && !planMode && lastInput != null

    /**
     * Foto/Beschreibung ablegen und im Hintergrund weiter versuchen (KI überlastet, kein Netz). Fertig →
     * Benachrichtigung und Zeile in der Mahlzeit auf „Heute“; gespeichert wird erst nach dem Prüfen.
     */
    fun queueInBackground(onDone: () -> Unit) {
        val queue = aiQueue ?: return
        val input = lastInput ?: return
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                val id = queuedId
                if (id != null && queue.get(id) != null) {
                    queue.requeue(id)
                } else {
                    val date = LocalDate.ofEpochDay(route.epochDay)
                    val meal = _state.value.mealType
                    when (input) {
                        is Input.Photo -> queue.enqueue(de.kalorienapp.capture.QueuedKind.PHOTO, input.uri, null, input.hint, date, meal)
                        is Input.Label -> queue.enqueue(de.kalorienapp.capture.QueuedKind.LABEL, input.uri, null, null, date, meal)
                        is Input.Text -> queue.enqueue(de.kalorienapp.capture.QueuedKind.TEXT, null, input.description, null, date, meal)
                    }
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saving = false
                setStep(CaptureStep.Failed(CaptureError.IMAGE))
            }
        }
    }

    /** Eintrag aus der Warteschlange: fertigen Vorschlag zeigen, Fehler anzeigen bzw. gleich nochmal versuchen. */
    private fun openQueued(id: String) {
        val queue = aiQueue ?: return
        val job = queue.get(id) ?: return
        queuedId = id
        _state.update { it.copy(mealType = job.mealType, description = job.text.orEmpty()) }
        val uri = queue.photoUri(job)
        val input = when (job.kind) {
            de.kalorienapp.capture.QueuedKind.TEXT -> Input.Text(job.text.orEmpty())
            de.kalorienapp.capture.QueuedKind.LABEL -> Input.Label(uri)
            de.kalorienapp.capture.QueuedKind.PHOTO -> Input.Photo(uri, skipBarcode = true, hint = job.hint)
        }
        lastInput = input
        when (job.status) {
            de.kalorienapp.capture.QueuedStatus.DONE -> setStep(proposalOf(job.drafts))
            de.kalorienapp.capture.QueuedStatus.FAILED -> setStep(
                CaptureStep.Failed(
                    GeminiException(GeminiError.entries.find { it.name == job.error } ?: GeminiError.INVALID_RESPONSE).toCaptureError(),
                ),
            )
            de.kalorienapp.capture.QueuedStatus.WAITING -> analyze(input)
        }
    }

    /** Zuletzt unbekannter Barcode: Was danach per Etikett/KI erkannt wird, merkt sich Dunories dafür. */
    private var pendingBarcode: String? = null
    private var analysisJob: Job? = null
    private var nextItemId = 1L

    init {
        // Plan-Punkt bearbeiten bzw. „Anpassen“ auf „Heute“ → gleich als bearbeitbarer Vorschlag.
        if (route.planDay >= 0 && (route.planItemId != 0L || route.fromPlan)) {
            viewModelScope.launch {
                val items = if (route.planItemId != 0L) {
                    listOfNotNull(mealPlan.get(route.planItemId))
                } else {
                    mealPlan.forDay(route.planDay).filter { it.mealType.name == route.mealType }
                }
                if (items.isNotEmpty()) setStep(proposalOf(items.map { it.toDraft() }))
            }
        }
        route.queueId?.let(::openQueued)
    }

    /**
     * Neues Foto (Kamera/Galerie): erst die Vorschau zum Prüfen bzw. Dazusagen. Ist ein Barcode drauf, geht es
     * ohne Vorschau weiter – beim Scannen bringt ein Hinweis nichts und es soll kein Schritt dazukommen.
     */
    fun onPhoto(uri: Uri, hint: String = "") {
        val label = _state.value.labelMode
        _state.update { it.copy(hint = hint.trim()) }
        if (label) {
            setStep(CaptureStep.Review(uri, label = true))
            return
        }
        pendingBarcode = null
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            setStep(CaptureStep.Analyzing(AnalysisStage.BARCODE))
            val barcode = try {
                analyzer.scanBarcode(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (barcode != null) analyze(Input.Photo(uri)) else setStep(CaptureStep.Review(uri, label = false))
        }
    }

    /**
     * Auslöser gedrückt halten und sprechen (versteckter Schnellweg): landet in der Foto-Vorschau, das Gesagte steht
     * schon im Feld – noch korrigierbar, dann „Erkennen lassen“ (Wunsch aus dem Handy-Test 26.11.).
     */
    fun onPhotoWithHint(uri: Uri, hint: String) = onPhoto(uri, if (_state.value.labelMode) "" else hint)

    fun onHintChange(text: String) = _state.update { it.copy(hint = text) }

    /** Vorschau → abschicken (Barcode wurde schon geprüft). */
    fun submitReview() {
        val review = _state.value.step as? CaptureStep.Review ?: return
        if (review.label) {
            analyze(Input.Label(review.uri))
        } else {
            analyze(Input.Photo(review.uri, skipBarcode = true, hint = _state.value.hint.trim().ifBlank { null }))
        }
    }

    fun setLabelMode(enabled: Boolean) = _state.update { it.copy(labelMode = enabled) }

    /** Unbekannter Barcode → zurück zur Kamera im Etikett-Modus. */
    fun photographLabel() {
        setLabelMode(true)
        backToCamera()
    }

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

    fun removeCombo(id: Long) {
        viewModelScope.launch { library.deleteCombo(id) }
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch { library.removeFavorite(id) }
    }

    fun confirmQuickPick() {
        val step = _state.value.step as? CaptureStep.QuickPick ?: return
        // Kombis zuerst (ganze Mahlzeit), dann einzelne Favoriten/Häufiges.
        val drafts = combos.value.filter { comboKey(it) in step.selected }.flatMap { combo ->
            combo.items.map { FoodDraft(it.name, it.amount, it.unit, it.per100, EntrySource.MANUAL) }
        } + favorites.value.filter { favoriteKey(it) in step.selected }.map {
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

    /**
     * KI-Vorschlag mit einem Hinweis des Nutzers neu schätzen lassen: beim Foto Bild + Hinweis
     * (Hinweis hat Vorrang), bei einer Beschreibung wird der Hinweis als Korrektur angehängt.
     */
    fun refine(hint: String) {
        if (hint.isBlank()) return
        when (val input = lastInput) {
            is Input.Photo -> analyze(input.copy(skipBarcode = true, hint = hint.trim()))
            is Input.Text -> analyze(Input.Text(input.description.trim() + "\nKorrektur: " + hint.trim()))
            else -> Unit
        }
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
        p.copy(
            items = p.items.map { item ->
                if (item.id != id) return@map item
                // Nährwerte selbst korrigiert → nicht mehr „geschätzt“.
                val edited = form.per100 != item.form.per100 || form.micros != item.form.micros
                item.copy(form = form, source = if (edited) item.source.afterNutrientEdit() else item.source)
            },
        )
    }

    fun updateDish(id: Long, dish: DishForm) = updateProposal { p ->
        p.copy(items = p.items.map { if (it.id == id) it.copy(dish = dish) else it })
    }

    fun addDishPart(id: Long) = updateProposal { p ->
        p.copy(items = p.items.map { if (it.id == id && it.dish != null) it.copy(dish = it.dish.withNewPart(nextItemId++)) else it })
    }

    /** Gericht doch lieber als einzelne Einträge (KI hat zu viel zusammengefasst). */
    fun splitDish(id: Long) = updateProposal { p ->
        p.copy(
            items = p.items.flatMap { item ->
                val dish = item.dish
                if (item.id != id || dish == null) listOf(item)
                else dish.parts.map { ProposalItem(nextItemId++, it.form, item.source) }
            },
            expandedId = null,
        )
    }

    fun removeItem(id: Long) = updateProposal { p -> p.copy(items = p.items.filterNot { it.id == id }) }

    fun addItem() = updateProposal { p ->
        val item = ProposalItem(nextItemId++, FoodForm(), EntrySource.MANUAL)
        typedItemIds += item.id
        p.copy(items = p.items + item, expandedId = item.id)
    }

    fun toggleExpanded(id: Long) = updateProposal { p -> p.copy(expandedId = if (p.expandedId == id) null else id) }

    /** Schutz gegen Doppeltippen: sonst würde dasselbe Essen zweimal gespeichert. */
    private var saving = false

    fun saveAll(onDone: () -> Unit) {
        val current = _state.value
        val proposal = current.step as? CaptureStep.Proposal ?: return
        if (!proposal.canSave || saving) return
        saving = true
        val date = LocalDate.ofEpochDay(route.epochDay)
        val now = System.currentTimeMillis()
        val entries = proposal.items.mapIndexedNotNull { index, item ->
            item.dish?.let { dish ->
                return@mapIndexedNotNull FoodEntry(
                    date = date,
                    mealType = current.mealType,
                    name = dish.name.trim(),
                    amount = dish.totalAmount,
                    unit = dish.unit,
                    nutrients = dish.totalNutrients(),
                    source = item.source,
                    createdAt = now + index,
                    components = dish.components(),
                )
            }
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
            if (planMode) {
                rememberBarcodes(proposal.items)
                if (route.planItemId != 0L) mealPlan.delete(route.planItemId)
                mealPlan.addFromEntries(route.planDay, entries)
                onDone()
                return@launch
            }
            rememberBarcodes(proposal.items)
            // Gleiches von eben (z. B. zweiter Salami-Stick) fließt in den vorhandenen Eintrag ein.
            entries.forEach { repository.add(it) }
            de.kalorienapp.ui.entry.countTyped(settings, proposal.items.count { it.id in typedItemIds && it.dish == null })
            queuedId?.let { aiQueue?.remove(it) }
            onDone()
        }
    }

    /**
     * Weiteres Foto im Vorschlag (Tester-Wunsch: nicht alles eintippen): Essen, Barcode oder – mit [label] – eine
     * Nährwerttabelle. Das Ergebnis wird angehängt; mit [dishId] als weitere Zutaten dieses Gerichts.
     * Unbekannter Barcode → Etikett auf demselben Foto lesen, sonst schätzen; der Barcode wird dafür gemerkt.
     */
    fun addPhoto(uri: Uri, dishId: Long?, label: Boolean) {
        if (_state.value.adding) return
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            try {
                val noStage: (AnalysisStage) -> Unit = {}
                val (drafts, barcode) = if (label) {
                    analyzer.analyzeLabel(uri, noStage) to null
                } else {
                    when (val result = analyzer.analyzePhoto(uri, noStage)) {
                        is AnalysisResult.Drafts -> result.drafts to result.ownBarcode
                        is AnalysisResult.UnknownBarcode -> {
                            val read = try {
                                analyzer.analyzeLabel(uri, noStage)
                            } catch (e: de.kalorienapp.data.remote.gemini.GeminiException) {
                                if (e.error != de.kalorienapp.data.remote.gemini.GeminiError.NO_FOOD) throw e
                                emptyList()
                            }
                            read.ifEmpty { analyzer.analyzePhotoWithAi(uri, noStage) } to result.barcode
                        }
                    }
                }
                appendDrafts(drafts, barcode, dishId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(addError = e.toCaptureError()) }
            } finally {
                _state.update { it.copy(adding = false) }
            }
        }
    }

    fun clearAddError() = _state.update { it.copy(addError = null) }

    private fun appendDrafts(drafts: List<FoodDraft>, barcode: String?, dishId: Long?) = updateProposal { p ->
        if (dishId != null) {
            // Als Zutaten: ein erkanntes Gericht wird in seine Zutaten aufgelöst (nur eine Ebene).
            val parts = drafts.flatMap { d -> d.components.ifEmpty { listOf(d) } }
                .map { DishPart(nextItemId++, FoodForm.of(it.name, it.amount, it.unit, it.per100)) }
            p.copy(items = p.items.map { if (it.id == dishId && it.dish != null) it.copy(dish = it.dish.withAddedParts(parts)) else it })
        } else {
            val added = itemsOf(drafts, barcode)
            p.copy(items = p.items + added, expandedId = added.lastOrNull()?.id ?: p.expandedId)
        }
    }

    /** Eigene Barcodes merken bzw. aktualisieren (nur einzelne Lebensmittel, keine Gerichte). */
    private suspend fun rememberBarcodes(items: List<ProposalItem>) {
        val dao = ownBarcodes ?: return
        items.filter { it.barcode != null && it.dish == null && it.form.isValid }.forEach { item ->
            val f = item.form
            val per100 = f.per100Nutrients()
            dao.upsert(de.kalorienapp.data.db.entity.OwnBarcodeEntity.of(item.barcode!!, f.name.trim(), f.amountValue, f.unit, per100))
            // Nur abgelesene Nährwerttabellen gehen in die gemeinsame Sammlung (keine Schätzungen, nichts Getipptes).
            if (item.source == EntrySource.LABEL) community?.submit(item.barcode, f.name.trim(), f.unit, per100)
        }
    }

    // --- intern ---

    private fun analyze(input: Input) {
        lastInput = input
        // Neues Foto/neue Beschreibung: ein früher unbekannter Barcode gilt nicht mehr (Etikett/KI danach schon).
        if ((input is Input.Photo && !input.skipBarcode) || input is Input.Text) pendingBarcode = null
        analysisJob?.cancel()
        // Ohne Netz sofort „Im Hintergrund erkennen lassen“ anbieten statt erst auf die KI zu warten.
        // (Foto mit möglichem Barcode darf weiter: eigene Barcodes und der Barcode-Zwischenspeicher gehen offline.)
        val needsAi = input is Input.Text || input is Input.Label || (input is Input.Photo && input.skipBarcode)
        if (needsAi && !analyzer.isOnline()) {
            setStep(CaptureStep.Failed(CaptureError.NETWORK))
            return
        }
        analysisJob = viewModelScope.launch {
            val onStage: (AnalysisStage) -> Unit = { stage -> setStep(CaptureStep.Analyzing(stage)) }
            try {
                val step = when (input) {
                    is Input.Text -> proposalOf(analyzer.analyzeText(input.description, onStage))
                    is Input.Label -> proposalOf(analyzer.analyzeLabel(input.uri, onStage), pendingBarcode)
                    is Input.Photo -> if (input.skipBarcode) {
                        proposalOf(analyzer.analyzePhotoWithAi(input.uri, onStage, input.hint), pendingBarcode)
                    } else {
                        when (val result = analyzer.analyzePhoto(input.uri, onStage, input.hint)) {
                            is AnalysisResult.Drafts -> proposalOf(result.drafts, result.ownBarcode, result.community)
                            is AnalysisResult.UnknownBarcode -> {
                                pendingBarcode = result.barcode
                                CaptureStep.UnknownBarcode(result.barcode)
                            }
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

    private fun proposalOf(drafts: List<FoodDraft>, barcode: String? = null, community: Boolean = false): CaptureStep.Proposal {
        val items = itemsOf(drafts, barcode, community)
        // Einzelne Komponente (z. B. Barcode) direkt aufgeklappt, damit die Menge sofort eingetragen werden kann.
        return CaptureStep.Proposal(items, expandedId = items.singleOrNull()?.id)
    }

    /** Erkanntes → Vorschlag; ein einzelnes Lebensmittel bekommt den Barcode zum Merken. */
    private fun itemsOf(drafts: List<FoodDraft>, barcode: String?, community: Boolean = false): List<ProposalItem> {
        val single = drafts.singleOrNull()?.takeIf { it.components.size < 2 }
        return drafts.map { d ->
            val dish = d.takeIf { it.components.size >= 2 }?.let { DishForm.ofDraft(it) { nextItemId++ } }
            val mine = d === single
            ProposalItem(nextItemId++, FoodForm.of(d.name, d.amount, d.unit, d.per100), d.source, dish, barcode.takeIf { mine }, community && mine)
        }
    }

    companion object {
        fun comboKey(c: MealCombo) = "combo:${c.id}"
        fun favoriteKey(f: FavoriteFood) = "fav:${f.id}"
        fun frequentKey(f: FrequentFood) = "freq:${f.name.lowercase()}|${f.unit}"
    }

    private fun setStep(step: CaptureStep) = _state.update { it.copy(step = step) }

    private fun updateProposal(transform: (CaptureStep.Proposal) -> CaptureStep.Proposal) = _state.update { s ->
        val p = s.step as? CaptureStep.Proposal ?: return@update s
        s.copy(step = transform(p))
    }

}

/** Fehler einer KI-/Bildanalyse → Meldung für die UI. */
internal fun Exception.toCaptureError(): CaptureError = when (this) {
    is GeminiException -> when (error) {
        GeminiError.NO_API_KEY -> CaptureError.NO_API_KEY
        GeminiError.REJECTED -> CaptureError.REJECTED
        GeminiError.NETWORK -> CaptureError.NETWORK
        GeminiError.QUOTA -> CaptureError.QUOTA
        GeminiError.DAILY_LIMIT -> CaptureError.DAILY_LIMIT
        GeminiError.NEEDS_ACCOUNT -> CaptureError.NEEDS_ACCOUNT
        GeminiError.NEEDS_PREMIUM -> CaptureError.NEEDS_PREMIUM
        GeminiError.OVERLOADED -> CaptureError.OVERLOADED
        GeminiError.INVALID_RESPONSE -> CaptureError.INVALID_RESPONSE
        GeminiError.NO_FOOD -> CaptureError.NO_FOOD
    }
    is ImageDecoder.DecodeException, is de.kalorienapp.capture.ImageReadException -> CaptureError.IMAGE
    is IOException -> CaptureError.NETWORK
    else -> CaptureError.UNKNOWN
}
