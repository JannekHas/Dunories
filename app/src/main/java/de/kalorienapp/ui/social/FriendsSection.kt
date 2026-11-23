package de.kalorienapp.ui.social

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.account.Friend
import de.kalorienapp.account.FriendRequestResult
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ExpandableCard
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.SettingSwitchRow
import de.kalorienapp.ui.profile.AccountDialogs
import de.kalorienapp.ui.profile.AccountViewModel
import de.kalorienapp.ui.profile.findActivity
import de.kalorienapp.ui.theme.Neon

/**
 * Freunde (nur mit Konto): eigener Freundescode, Freund per Code anfragen, Anfragen annehmen/ablehnen,
 * Freunde als Rangliste nach Serie, Freund antippen → freigegebene Abzeichen/Erfolge, sofort entfernbar.
 */
@Composable
internal fun FriendsSection(
    viewModel: SocialViewModel,
    signedIn: Boolean,
    myName: String,
    myStreak: Int,
    myAvatar: String?,
    onPrivacy: () -> Unit,
) {
    val data by viewModel.friends.collectAsStateWithLifecycle()
    val busy by viewModel.friendsBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var openFriend by remember { mutableStateOf<Friend?>(null) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.friends_title), style = MaterialTheme.typography.titleMedium)
            if (!signedIn) {
                SignInForFriends(onPrivacy)
                return@Column
            }
            val d = data
            if (d == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Neon.Cyan)
                    Text(stringResource(R.string.friends_loading), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                }
                return@Column
            }
            // Eigener Code
            d.code?.let { code ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.friends_my_code), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
                        Text(code, style = MaterialTheme.typography.titleLarge, color = Neon.Yellow, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        val text = context.getString(R.string.friends_share_text, code)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    }) { Text(stringResource(R.string.friends_share)) }
                }
            }
            Button(
                onClick = { viewModel.clearRequestResult(); adding = true },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.friends_add)) }

            // Eingehende Anfragen
            if (d.incoming.isNotEmpty()) {
                Text(stringResource(R.string.friends_incoming), style = MaterialTheme.typography.labelLarge, color = Neon.Yellow)
                d.incoming.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(null, r.name, 34)
                        Text(r.name.ifBlank { r.code }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        TextButton(onClick = { viewModel.respond(r.code, false) }, enabled = !busy) {
                            Text(stringResource(R.string.friends_decline), color = Neon.TextSecondary)
                        }
                        TextButton(onClick = { viewModel.respond(r.code, true) }, enabled = !busy) { Text(stringResource(R.string.friends_accept)) }
                    }
                }
            }

            // Rangliste nach Serie (du + Freunde)
            if (d.friends.isEmpty()) {
                Text(stringResource(R.string.friends_empty), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            } else {
                HorizontalDivider(color = Neon.Track)
                Text(stringResource(R.string.friends_ranking), style = MaterialTheme.typography.labelLarge, color = Neon.TextSecondary)
                val rows = (d.friends.map { RankRow(it.name.ifBlank { it.code }, it.streak, it.badge, it.avatar, it) } +
                    RankRow(myName.ifBlank { stringResource(R.string.friends_you) }, myStreak, null, myAvatar, null, me = true))
                    .sortedWith(compareByDescending<RankRow> { it.streak ?: -1 }.thenBy { it.name.lowercase() })
                rows.forEachIndexed { i, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (row.friend != null) Modifier.clickable(role = Role.Button) { openFriend = row.friend } else Modifier)
                            .padding(vertical = 4.dp),
                    ) {
                        Text("${i + 1}.", style = MaterialTheme.typography.labelLarge, color = Neon.TextSecondary, modifier = Modifier.width(28.dp))
                        AvatarImage(row.avatar, row.name, 34)
                        Text(
                            if (row.me) stringResource(R.string.friends_you_named, row.name) else row.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (row.me) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        row.badge?.let { SharedBadge(it, size = 26) }
                        Text(
                            row.streak?.let { pluralStringResource(R.plurals.friends_streak, it, it) } ?: stringResource(R.string.friends_private),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (row.streak != null) Neon.Orange else Neon.TextSecondary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // Ausgehende Anfragen
            d.outgoing.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.friends_outgoing, r.name.ifBlank { r.code }),
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.remove(r.code) }, enabled = !busy) { Text(stringResource(R.string.friends_withdraw)) }
                }
            }
        }
    }

    if (adding) AddFriendDialog(viewModel, onClose = { adding = false })
    openFriend?.let { f -> FriendDialog(f, onRemove = { viewModel.remove(f.code); openFriend = null }, onClose = { openFriend = null }) }
}

private data class RankRow(val name: String, val streak: Int?, val badge: String?, val avatar: String?, val friend: Friend?, val me: Boolean = false)

/** Was Freunde von dir sehen – jedes einzeln, standardmäßig privat. */
@Composable
internal fun ShareSettingsCard(viewModel: SocialViewModel, share: ShareSettings) {
    val summary = listOfNotNull(
        stringResource(R.string.share_badges).takeIf { share.badges },
        stringResource(R.string.share_achievements).takeIf { share.achievements },
        stringResource(R.string.share_streak).takeIf { share.streak },
    ).ifEmpty { listOf(stringResource(R.string.share_only_name)) }.joinToString(" · ")
    ExpandableCard(title = stringResource(R.string.share_title), summary = summary) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.share_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
            SettingSwitchRow(
                stringResource(R.string.share_badges),
                stringResource(R.string.share_badges_text),
                share.badges,
                onChange = { viewModel.setShare(share.copy(badges = it)) },
            )
            SettingSwitchRow(
                stringResource(R.string.share_achievements),
                stringResource(R.string.share_achievements_text),
                share.achievements,
                onChange = { viewModel.setShare(share.copy(achievements = it)) },
            )
            SettingSwitchRow(
                stringResource(R.string.share_streak),
                stringResource(R.string.share_streak_text),
                share.streak,
                onChange = { viewModel.setShare(share.copy(streak = it)) },
            )
        }
    }
}

@Composable
private fun SignInForFriends(onPrivacy: () -> Unit) {
    val account: AccountViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val busy by account.busy.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    Text(stringResource(R.string.friends_need_account), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
    Button(
        onClick = { activity?.let(account::startGoogle) },
        enabled = busy == null,
        colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.account_google)) }
    AccountDialogs(account, onPrivacy)
}

@Composable
private fun AddFriendDialog(viewModel: SocialViewModel, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val result by viewModel.requestResult.collectAsStateWithLifecycle()
    val busy by viewModel.friendsBusy.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.friends_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.friends_add_text), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    label = { Text(stringResource(R.string.friends_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                result?.let {
                    val ok = it == FriendRequestResult.SENT || it == FriendRequestResult.ACCEPTED
                    Text(stringResource(it.messageRes), style = MaterialTheme.typography.bodySmall, color = if (ok) Neon.Green else Neon.Yellow)
                }
            }
        },
        confirmButton = {
            val done = result == FriendRequestResult.SENT || result == FriendRequestResult.ACCEPTED
            if (done) {
                TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) }
            } else {
                TextButton(onClick = { viewModel.sendRequest(code) }, enabled = !busy && code.count(Char::isLetterOrDigit) == 8) {
                    Text(stringResource(R.string.friends_send))
                }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

private val FriendRequestResult.messageRes: Int
    get() = when (this) {
        FriendRequestResult.SENT -> R.string.friends_sent
        FriendRequestResult.ACCEPTED -> R.string.friends_now_friends
        FriendRequestResult.UNKNOWN_CODE -> R.string.friends_unknown_code
        FriendRequestResult.SELF -> R.string.friends_self
        FriendRequestResult.ALREADY -> R.string.friends_already
        FriendRequestResult.TOO_MANY -> R.string.friends_too_many
        FriendRequestResult.NETWORK -> R.string.account_error
    }

/** Freund antippen: freigegebene Abzeichen/Erfolge und Serie; „Entfernen“ wirkt sofort für beide. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendDialog(friend: Friend, onRemove: () -> Unit, onClose: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(friend.name.ifBlank { friend.code }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    friend.streak?.let { pluralStringResource(R.plurals.friends_streak, it, it) } ?: stringResource(R.string.friends_streak_private),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (friend.streak != null) Neon.Orange else Neon.TextSecondary,
                )
                // Längste Serie kommt mit der Freigabe „Serie“ (ältere App-Versionen schicken sie noch nicht).
                friend.best?.takeIf { friend.streak != null }?.let {
                    Text(
                        pluralStringResource(R.plurals.friends_best, it, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neon.Yellow,
                    )
                }
                val keys = friend.achievements
                if (keys == null) {
                    Text(stringResource(R.string.friends_achievements_private), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                } else if (keys.isEmpty()) {
                    Text(stringResource(R.string.friends_no_achievements), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        keys.sortedByDescending { sharedRarity(it).ordinal }.forEach { key ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                                SharedBadge(key, size = 40)
                                Text(sharedTitle(key), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
                if (confirm) {
                    Text(stringResource(R.string.friends_remove_confirm), style = MaterialTheme.typography.bodySmall, color = Neon.Red)
                }
            }
        },
        confirmButton = {
            if (confirm) TextButton(onClick = onRemove) { Text(stringResource(R.string.friends_remove), color = Neon.Red) }
            else TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            if (confirm) TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            else OutlinedButton(onClick = { confirm = true }) { Text(stringResource(R.string.friends_remove), color = Neon.Red) }
        },
    )
}
