package de.kalorienapp.ui.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon

/**
 * Avatare zum Auswählen (Claude Design, Nov. 2026 – bunt gemischt statt nach Geschlecht: Menschen, Tiere, Roboter,
 * Alien, Essen mit Gesicht). Kein Foto-Upload (rechtswidrige Inhalte). Schlüssel dauerhaft (Sicherung, Freunde).
 */
internal val AVATARS: List<Pair<String, Int>> = listOf(
    "01_fuchs" to R.drawable.avatar_01_fuchs,
    "02_mensch_kurzhaar" to R.drawable.avatar_02_mensch_kurzhaar,
    "03_avocado" to R.drawable.avatar_03_avocado,
    "04_roboter" to R.drawable.avatar_04_roboter,
    "05_mensch_kopftuch" to R.drawable.avatar_05_mensch_kopftuch,
    "06_panda" to R.drawable.avatar_06_panda,
    "07_mensch_muetze" to R.drawable.avatar_07_mensch_muetze,
    "08_banane" to R.drawable.avatar_08_banane,
    "09_alien" to R.drawable.avatar_09_alien,
    "10_mensch_brille" to R.drawable.avatar_10_mensch_brille,
    "11_frosch" to R.drawable.avatar_11_frosch,
    "12_mensch_langhaar" to R.drawable.avatar_12_mensch_langhaar,
    "13_erdbeere" to R.drawable.avatar_13_erdbeere,
    "14_mensch_undercut" to R.drawable.avatar_14_mensch_undercut,
    "15_katze" to R.drawable.avatar_15_katze,
    "16_brokkoli" to R.drawable.avatar_16_brokkoli,
)

private fun avatarRes(key: String?): Int? = AVATARS.firstOrNull { it.first == key }?.second

/** Avatar oder – ohne Auswahl – Anfangsbuchstabe im Neon-Ring. */
@Composable
internal fun AvatarImage(avatar: String?, name: String, size: Int, modifier: Modifier = Modifier) {
    val res = avatarRes(avatar)
    if (res != null) {
        Image(painterResource(res), contentDescription = null, modifier = modifier.size(size.dp))
    } else {
        Box(
            modifier
                .size(size.dp)
                .border((size / 24f).coerceAtLeast(1.5f).dp, Neon.Cyan, CircleShape)
                .padding((size / 14).dp)
                .background(Neon.SurfaceHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = if (size >= 56) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleSmall,
                color = Neon.Cyan,
            )
        }
    }
}

/** Raster aller Avatare + „Anfangsbuchstabe“; Antippen wählt und schließt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AvatarPicker(current: String?, name: String, onPick: (String?) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.social_avatar_title)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val letter = stringResource(R.string.social_avatar_letter)
                Choice(selected = current == null, description = letter, onClick = { onPick(null) }) {
                    AvatarImage(null, name, 56)
                }
                AVATARS.forEachIndexed { index, (key, _) ->
                    Choice(selected = current == key, description = "${index + 1}", onClick = { onPick(key) }) {
                        AvatarImage(key, name, 56)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Choice(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier
            .border(2.dp, if (selected) Neon.TextPrimary else Color.Transparent, CircleShape)
            .padding(3.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
    ) { content() }
}
