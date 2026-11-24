package de.kalorienapp.ui.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 * „Hinzufügen“ mit Auswahl (Tester-Wunsch Nov. 2026: nicht jedes Mal eintippen): Foto von Essen oder Barcode,
 * Nährwerttabelle, aus der Galerie oder wie bisher eintippen.
 */
@Composable
fun AddWithPhotoButton(
    text: String,
    enabled: Boolean,
    launcher: AddPhotoLauncher,
    onType: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(text, modifier = Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.add_photo_food)) }, onClick = { open = false; launcher.camera(false) })
            DropdownMenuItem(text = { Text(stringResource(R.string.add_photo_label)) }, onClick = { open = false; launcher.camera(true) })
            DropdownMenuItem(text = { Text(stringResource(R.string.add_photo_gallery)) }, onClick = { open = false; launcher.gallery() })
            DropdownMenuItem(text = { Text(stringResource(R.string.add_type)) }, onClick = { open = false; onType() })
        }
    }
}

/** Ziel für Kamerafotos (Cache, über FileProvider freigegeben – wie bei der Körperanalyse). */
private fun addPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "add_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
