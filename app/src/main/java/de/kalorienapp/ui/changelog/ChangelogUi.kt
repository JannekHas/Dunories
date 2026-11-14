package de.kalorienapp.ui.changelog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.kalorienapp.R
import de.kalorienapp.ui.theme.Neon

/** Patchnotes einer Version: versionCode, Anzeige-Name, Inhalt (res/values/changelog.xml). */
data class ChangelogVersion(val code: Int, val name: String, val arrayRes: Int)

object ChangelogUi {
    /** Alle Versionen mit Patchnotes in der App – neue Version hier ergänzen. */
    val VERSIONS = listOf(
        ChangelogVersion(6, "0.5.1", R.array.changelog_6),
    )

    fun byCodes(codes: List<Int>) = codes.mapNotNull { code -> VERSIONS.find { it.code == code } }
}

private val accents = listOf(Neon.Cyan, Neon.Orange, Neon.Magenta, Neon.Green, Neon.Blue, Neon.Yellow)

/**
 * „Neu in Dunories“: großes Fenster über „Heute“ nach einem Update (bis es einmal geschlossen wurde),
 * später auch über Profil → Einstellungen → „Was ist neu?“. Jeder Punkt als kleine Karte mit Emoji.
 */
@Composable
fun ChangelogDialog(versions: List<ChangelogVersion>, onClose: () -> Unit) {
    if (versions.isEmpty()) return
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Neon.SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxHeight),
        ) {
            Column(Modifier.padding(top = 24.dp, bottom = 16.dp)) {
                // Kopf
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 24.dp)) {
                    Icon(painterResource(R.drawable.ic_sparkle), contentDescription = null, tint = Neon.Cyan, modifier = Modifier.size(28.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.changelog_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(
                            stringResource(R.string.changelog_version, versions.first().name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Neon.Cyan,
                        )
                    }
                }
                // Inhalt (scrollt, wenn es viel ist)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    versions.forEachIndexed { index, version ->
                        if (index > 0) {
                            Text(
                                stringResource(R.string.changelog_version, version.name),
                                style = MaterialTheme.typography.titleSmall,
                                color = Neon.TextSecondary,
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                            )
                        }
                        stringArrayResource(version.arrayRes).forEachIndexed { i, raw ->
                            val parts = raw.split('|', limit = 3)
                            if (parts.size == 3) ChangelogItem(parts[0], parts[1], parts[2], accents[i % accents.size])
                        }
                    }
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) { Text(stringResource(R.string.changelog_close), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ChangelogItem(emoji: String, title: String, text: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.2f), CircleShape),
        ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = accent)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
