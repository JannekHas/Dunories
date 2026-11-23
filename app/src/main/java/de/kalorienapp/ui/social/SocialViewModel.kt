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
import de.kalorienapp.domain.Gamification
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
    /** Gewählter Avatar (null = Anfangsbuchstabe). */
    val avatar: String? = null,
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
        settings.gamificationEnabled, settings.testerName, settings.testerCredits, settings.favoriteBadge, settings.avatar,
    ) { on, name, credits, badge, avatar -> Profile(on, name, credits, Gamification.canonicalBadge(badge), avatar) }

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
            avatar = p.avatar,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SocialUiState())

    private data class Profile(val on: Boolean, val name: String, val credits: Boolean, val badge: String?, val avatar: String?)

    // ---------------- Freunde ----------------

    private val _friends = MutableStateFlow<SocialData?>(null)
    val friends: StateFlow<SocialData?> = _friends.asStateFlow()

    private val _friendsBusy = MutableStateFlow(false)
    val friendsBusy: StateFlow<Boolean> = _friendsBusy.asStateFlow()

    /** Rückmeldung nach „Eingeladen von“. */
    private val _inviteResult = MutableStateFlow<de.kalorienapp.account.InviteResult?>(null)
    val inviteResult: StateFlow<de.kalorienapp.account.InviteResult?> = _inviteResult.asStateFlow()

    fun enterInvite(code: String) = friendAction {
        val (result, data) = social.invited(code)
        _inviteResult.value = result
        data?.let(::applyData)
    }

    fun clearInviteResult() {
        _inviteResult.value = null
    }

    /** Neuer Stand vom Server: Freunde, Einladungen (Erfolge „Freunde“/„Botschafter“), eigene Einladung. */
    private fun applyData(data: SocialData) {
        _friends.value = data
        settings.setFriendsCount(data.friends.size)
        settings.setInvitesCount(data.invites)
        settings.setInvitePending(data.invitedBy?.rewarded == false)
    }

    /** Rückmeldung nach „Freund hinzufügen“. */
    private val _requestResult = MutableStateFlow<FriendRequestResult?>(null)
    val requestResult: StateFlow<FriendRequestResult?> = _requestResult.asStateFlow()

    /** Beim Öffnen: Testphase/Tester-Punkte (wenn angemeldet, auch Gast) und Freunde (nur Konto, Profil vorher aktualisieren). */
    fun refresh() {
        if (account.hasAnyUser) viewModelScope.launch {
            premium.status()?.let {
                status.value = it
                settings.setTesterPoints(it.testerPoints)
                settings.setRatedFeedback(it.ratedFeedback)
                settings.setPremiumSeen(it.premium)
            }
        }
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
    }

    /** Eigenes Profil (nur Freigegebenes wird Freunden gezeigt) senden und Freunde laden. */
    private suspend fun pushProfile() {
        val g = game.state.first()
        val favorite = Gamification.canonicalBadge(settings.favoriteBadge.value)?.takeIf { it in g.badges }
        val result = social.saveProfile(
            SocialProfile(
                name = settings.feedbackName,
                // Freunde bekommen „schlüssel_stufe“ (z. B. serie_3), damit sie Rahmen und Farbe sehen.
                badge = favorite?.let { key -> Gamification.Achievement.byKey(key)?.let { Gamification.shareKey(it, levelOf(g.levels, key)) } ?: key },
                avatar = settings.avatar.value,
                streak = g.stats.currentStreak,
                best = g.stats.longestStreak,
                achievements = g.progress.filter { it.unlocked }.map { Gamification.shareKey(it.achievement, it.level) },
                shareBadges = settings.shareBadges.value,
                shareAchievements = settings.shareAchievements.value,
                shareStreak = settings.shareStreak.value,
            ),
        )
        if (result != null) applyData(result)
    }

    fun sendRequest(code: String) = friendAction {
        val (result, data) = social.request(code)
        _requestResult.value = result
        data?.let(::applyData)
    }

    fun clearRequestResult() {
        _requestResult.value = null
    }

    fun respond(code: String, accept: Boolean) = friendAction { social.respond(code, accept)?.let(::applyData) }

    fun remove(code: String) = friendAction { social.remove(code)?.let(::applyData) }

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
        settings.setFavoriteBadge(if (Gamification.canonicalBadge(settings.favoriteBadge.value) == key) null else key)
        if (account.user.value != null) viewModelScope.launch { pushProfile() }
    }

    fun setAvatar(key: String?) {
        settings.setAvatar(key)
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
