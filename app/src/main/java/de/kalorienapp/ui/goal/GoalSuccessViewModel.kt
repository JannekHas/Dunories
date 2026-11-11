package de.kalorienapp.ui.goal

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.R
import de.kalorienapp.data.repository.GoalRepository
import de.kalorienapp.data.repository.StatsRepository
import de.kalorienapp.data.repository.WeightRepository
import de.kalorienapp.domain.GoalSuccess
import de.kalorienapp.domain.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Designs in der Teilen-Galerie (zum Durchwischen). */
enum class ShareTemplate(val labelRes: Int, val fields: Set<ShareField>) {
    POSTER(R.string.share_template_poster, ShareField.entries.toSet()),
    NUMBER(R.string.share_template_number, setOf(ShareField.WEIGHTS, ShareField.DURATION, ShareField.ON_TARGET, ShareField.DATES)),
    CHART(R.string.share_template_chart, setOf(ShareField.CHANGE, ShareField.WEIGHTS, ShareField.DURATION, ShareField.DATES)),
    TILES(R.string.share_template_tiles, ShareField.entries.toSet()),
    BADGE(R.string.share_template_badge, setOf(ShareField.CHANGE, ShareField.DURATION, ShareField.DATES)),
    STICKER(R.string.share_template_sticker, setOf(ShareField.DURATION, ShareField.ON_TARGET)),
}

/** Seitenverhältnis Breite/Höhe. */
enum class ShareFormat(val ratio: Float, val labelRes: Int) {
    SQUARE(1f, R.string.share_format_square),
    POST(4f / 5f, R.string.share_format_post),
    STORY(9f / 16f, R.string.share_format_story),
}

enum class ShareBackground(val labelRes: Int) {
    GLOW(R.string.share_bg_glow),
    PHOTO(R.string.share_bg_photo),
    TRANSPARENT(R.string.share_bg_transparent),
}

/** Einzelne Daten, die ein Design zeigen kann (welche, legt [ShareTemplate.fields] fest). */
enum class ShareField(val labelRes: Int) {
    CHANGE(R.string.share_opt_change),
    WEIGHTS(R.string.share_opt_weights),
    CHART(R.string.share_opt_chart),
    DURATION(R.string.share_opt_duration),
    ON_TARGET(R.string.share_opt_ontarget),
    DATES(R.string.share_opt_dates),
}

/**
 * Was aufs teilbare Bild kommt: Design, Format, Hintergrund, Überschrift und Daten.
 * Standardmäßig alle Daten an (Wunsch des Nutzers: mehr Zahlen), jede einzeln abschaltbar.
 */
data class ShareOptions(
    val template: ShareTemplate = ShareTemplate.POSTER,
    val format: ShareFormat = ShareFormat.STORY,
    val background: ShareBackground = ShareBackground.GLOW,
    val photo: Uri? = null,
    /** Leer = Standard („ZIEL ERREICHT“ bzw. „GESCHAFFT“). */
    val headline: String = "",
    val fields: Set<ShareField> = ShareField.entries.toSet(),
) {
    fun shows(field: ShareField) = field in fields && field in template.fields
}

/** Daten für Feier-Bildschirm und „Erfolg teilen“ (beide nutzen die Ziel-ID aus der Route). */
class GoalSuccessViewModel(
    savedStateHandle: SavedStateHandle,
    private val goals: GoalRepository,
    private val weights: WeightRepository,
    private val stats: StatsRepository,
) : ViewModel() {
    private val goalId: Long = savedStateHandle["goalId"] ?: 0L

    private val _success = MutableStateFlow<GoalSuccess?>(null)
    val success: StateFlow<GoalSuccess?> = _success.asStateFlow()

    private val _options = MutableStateFlow(ShareOptions())
    val options: StateFlow<ShareOptions> = _options.asStateFlow()

    init {
        viewModelScope.launch {
            val goal = goals.get(goalId) ?: return@launch
            val zone = ZoneId.systemDefault()
            val measurements = weights.entries.first().map { it.measuredAt.atZone(zone).toLocalDate() to it.weightKg }
            val end = goal.endedDate ?: LocalDate.now()
            val onTarget = StatsCalculator.period(stats.days(goal.startDate, end).first()).onTargetRate
            _success.value = GoalSuccess.of(goal, measurements, onTarget)
        }
    }

    fun update(transform: (ShareOptions) -> ShareOptions) = _options.update(transform)
}
