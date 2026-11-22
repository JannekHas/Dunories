package de.kalorienapp.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.account.AccountManager
import de.kalorienapp.account.AiStatus
import de.kalorienapp.account.PremiumClient
import de.kalorienapp.account.PremiumSource
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.GameRepository
import de.kalorienapp.data.repository.GameState
import de.kalorienapp.domain.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SocialUiState(
    val game: GameState? = null,
    val gamificationEnabled: Boolean = true,
    val name: String = "",
    val credits: Boolean = false,
    val creditsEver: Boolean = false,
    val favoriteBadge: String? = null,
    val testerPoints: Int = 0,
    /** Testphase läuft (Server) oder schon Tester-Punkte → Tester-Bereich zeigen. */
    val testPhase: Boolean = false,
)

/** Social-Reiter: Profil (Name, Lieblings-Abzeichen), Level & Erfolge, Tester-Bereich. */
class SocialViewModel(
    private val game: GameRepository,
    private val settings: AppSettings,
    private val account: AccountManager,
    private val premium: PremiumClient,
) : ViewModel() {

    private val status = MutableStateFlow<AiStatus?>(null)

    val state: StateFlow<SocialUiState> = combine(
        game.state,
        combine(settings.gamificationEnabled, settings.testerName, settings.testerCredits, settings.favoriteBadge) { on, name, credits, badge ->
            listOf(on, name, credits, badge)
        },
        settings.testerPoints,
        status,
    ) { g, (on, name, credits, badge), points, s ->
        SocialUiState(
            game = g,
            gamificationEnabled = on as Boolean,
            name = name as String,
            credits = credits as Boolean,
            creditsEver = settings.creditsEverOn,
            favoriteBadge = (badge as String?)?.takeIf { it in g.badges },
            testerPoints = points,
            testPhase = s?.source == PremiumSource.TEST || points > 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SocialUiState())

    /** Beim Öffnen: Testphase und Tester-Punkte vom Server (nur wenn schon angemeldet – kein Gast-Konto nur dafür). */
    fun refresh() {
        if (!account.hasAnyUser) return
        viewModelScope.launch {
            premium.status()?.let {
                status.value = it
                settings.setTesterPoints(it.testerPoints)
            }
        }
    }

    fun setFavoriteBadge(key: String) = settings.setFavoriteBadge(if (settings.favoriteBadge.value == key) null else key)

    fun saveName(name: String) {
        val clean = name.trim().take(Feedback.MAX_NAME)
        settings.setFeedbackName(clean)
        if (clean.isBlank()) settings.setFeedbackCredits(false)
        pushTester()
    }

    fun setCredits(on: Boolean) {
        settings.setFeedbackCredits(on && settings.feedbackName.isNotBlank())
        pushTester()
    }

    private fun pushTester() {
        if (!account.hasAnyUser) return
        viewModelScope.launch { premium.saveTester(settings.feedbackName, settings.feedbackCredits) }
    }
}
