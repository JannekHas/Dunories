package de.kalorienapp.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import de.kalorienapp.ui.components.FitText
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.pressable
import de.kalorienapp.ui.theme.Neon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.kalorienapp.R
import java.io.File

/** Startet Kamera bzw. Galerie für ein weiteres Foto im Vorschlag; [onPicked] bekommt das Bild und ob es ein Etikett ist. */
class AddPhotoLauncher(
    val camera: (label: Boolean) -> Unit,
    val gallery: () -> Unit,
)

@Composable
fun rememberAddPhotoLauncher(onPicked: (Uri, Boolean) -> Unit): AddPhotoLauncher {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var label by rememberSaveable { mutableStateOf(false) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { onPicked(it, label) }
    }
    fun launchCamera() {
        val uri = addPhotoUri(context)
        cameraUri = uri
        takePhoto.launch(uri)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri, false)
    }
    return remember(takePhoto, permission, picker) {
        AddPhotoLauncher(
            camera = { isLabel ->
                label = isLabel
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else permission.launch(Manifest.permission.CAMERA)
            },
            gallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

/**
 * „Hinzufügen“ mit Auswahl (Tester-Wunsch Nov. 2026: nicht jedes Mal eintippen). Im Dunories-Stil statt
 * System-Menü (Hinweis des Nutzers): Antippen klappt eine Glas-Karte mit den runden Neon-Knöpfen der Kameraleiste
 * auf – Foto (Essen oder Barcode), Etikett (Nährwerttabelle), Galerie, Eintippen.
 */
@Composable
fun AddWithPhotoButton(
    text: String,
    enabled: Boolean,
    launcher: AddPhotoLauncher,
    onType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { open = !open }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(if (open) Icons.Filled.Close else Icons.Filled.Add, contentDescription = null)
            Text(text, modifier = Modifier.padding(start = 8.dp))
        }
        AnimatedVisibility(visible = open && enabled) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AddAction(stringResource(R.string.add_photo_food), R.drawable.ic_capture_camera, Neon.Cyan, Modifier.weight(1f)) {
                        open = false; launcher.camera(false)
                    }
                    AddAction(stringResource(R.string.add_photo_label), R.drawable.ic_bar_chart, Neon.Yellow, Modifier.weight(1f)) {
                        open = false; launcher.camera(true)
                    }
                    AddAction(stringResource(R.string.add_photo_gallery), R.drawable.ic_capture_gallery, Neon.Magenta, Modifier.weight(1f)) {
                        open = false; launcher.gallery()
                    }
                    AddAction(stringResource(R.string.add_type), R.drawable.ic_capture_manual, Neon.Green, Modifier.weight(1f)) {
                        open = false; onType()
                    }
                }
            }
        }
    }
}

/** Runder, farbig getönter Glasknopf mit Beschriftung – wie in der Kameraleiste. */
@Composable
private fun AddAction(label: String, @DrawableRes icon: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .pressable(onClick, MaterialTheme.shapes.medium)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        }
        FitText(label, style = MaterialTheme.typography.labelSmall, minFontSize = 7.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Ziel für Kamerafotos (Cache, über FileProvider freigegeben – wie bei der Körperanalyse). */
private fun addPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "add_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
