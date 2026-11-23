package de.kalorienapp.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.kalorienapp.account.AccountManager
import de.kalorienapp.account.AiStatus
import de.kalorienapp.account.FriendRequestResult
import de.kalorienapp.account.PremiumClient
import de.kalorienapp.account.PremiumSource
import de.kalorienapp.account.SocialClient
import de.kalorienapp.account.SocialData
import de.kalorienapp.account.SocialProfile
import de.kalorienapp.data.AppSettings
import de.kalorienapp.data.repository.GameRepository
import de.kalorienapp.data.repository.GameState
import de.kalorienapp.domain.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Freigaben an Freunde – jedes einzeln, standardmäßig privat. */
data class ShareSettings(val badges: Boolean = false, val achievements: Boolean = false, val streak: Boolean = false)

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
    /** Mit Google angemeldet (Freunde nur mit Konto). */
    val signedIn: Boolean = false,
    val share: ShareSettings = ShareSettings(),
    /** Serie enthält eine aus YAZIO übernommene. */
    val importedStreak: Boolean = false,
)

/** Social-Reiter: Profil, Erfolge & Abzeichen, Freunde (Konto), Tester-Bereich. */
class SocialViewModel(
    private val game: GameRepository,
    private val settings: AppSettings,
    private val account: AccountManager,
    private val premium: PremiumClient,
    private val social: SocialClient,
) : ViewModel() {

    private val status = MutableStateFlow<AiStatus?>(null)

    private val profileFlags = combine(
        settings.gamificationEnabled, settings.testerName, settings.testerCredits, settings.favoriteBadge,
    ) { on, name, credits, badge -> Profile(on, name, credits, badge) }

    private val shareFlags = combine(settings.shareBadges, settings.shareAchievements, settings.shareStreak) { b, a, s ->
        ShareSettings(b, a, s)
    }

    val state: StateFlow<SocialUiState> = combine(
        game.state,
        profileFlags,
        combine(settings.testerPoints, status, settings.importedStreak) { points, s, imported -> Triple(points, s, imported != null) },
        account.user,
        shareFlags,
    ) { g, p, (points, s, imported), user, share ->
        SocialUiState(
            game = g,
            gamificationEnabled = p.on,
            name = p.name,
            credits = p.credits,
            creditsEver = settings.creditsEverOn,
            favoriteBadge = p.badge?.takeIf { it in g.badges },
            testerPoints = points,
            testPhase = s?.source == PremiumSource.TEST || points > 0,
            signedIn = user != null,
            share = share,
            importedStreak = imported,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SocialUiState())

    private data class Profile(val on: Boolean, val name: String, val credits: Boolean, val badge: String?)

    // ---------------- Freunde ----------------

    private val _friends = MutableStateFlow<SocialData?>(null)
    val friends: StateFlow<SocialData?> = _friends.asStateFlow()

    private val _friendsBusy = MutableStateFlow(false)
    val friendsBusy: StateFlow<Boolean> = _friendsBusy.asStateFlow()

    /** Rückmeldung nach „Freund hinzufügen“. */
    private val _requestResult = MutableStateFlow<FriendRequestResult?>(null)
    val requestResult: StateFlow<FriendRequestResult?> = _requestResult.asStateFlow()

    /** Beim Öffnen: Testphase/Tester-Punkte (wenn angemeldet, auch Gast) und Freunde (nur Konto, Profil vorher aktualisieren). */
    fun refresh() {
        if (account.hasAnyUser) viewModelScope.launch {
            premium.status()?.let {
                status.value = it
                settings.setTesterPoints(it.testerPoints)
            }
        }
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
    }

    /** Eigenes Profil (nur Freigegebenes wird Freunden gezeigt) senden und Freunde laden. */
    private suspend fun pushProfile() {
        val g = game.state.first()
        val result = social.saveProfile(
            SocialProfile(
                name = settings.feedbackName,
                badge = settings.favoriteBadge.value?.takeIf { it in g.badges },
                streak = g.stats.currentStreak,
                best = g.stats.longestStreak,
                achievements = g.progress.filter { it.unlocked }.map { it.achievement.key },
                shareBadges = settings.shareBadges.value,
                shareAchievements = settings.shareAchievements.value,
                shareStreak = settings.shareStreak.value,
            ),
        )
        if (result != null) _friends.value = result
    }

    fun sendRequest(code: String) = friendAction {
        val (result, data) = social.request(code)
        _requestResult.value = result
        data?.let { _friends.value = it }
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    fun respond(code: String, accept: Boolean) = friendAction { social.respond(code, accept)?.let { _friends.value = it } }

    fun remove(code: String) = friendAction { social.remove(code)?.let { _friends.value = it } }

    private fun friendAction(block: suspend () -> Unit) {
        if (_friendsBusy.value) return
        _friendsBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _friendsBusy.value = false
            }
        }
    }

    fun setShare(share: ShareSettings) {
        settings.setShareBadges(share.badges)
        settings.setShareAchievements(share.achievements)
        settings.setShareStreak(share.streak)
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
    }

    // ---------------- Profil ----------------

    fun setFavoriteBadge(key: String) {
        settings.setFavoriteBadge(if (settings.favoriteBadge.value == key) null else key)
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
    }

    fun saveName(name: String) {
        val clean = name.trim().take(Feedback.MAX_NAME)
        settings.setFeedbackName(clean)
        if (clean.isBlank()) settings.setFeedbackCredits(false)
        pushTester()
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
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
