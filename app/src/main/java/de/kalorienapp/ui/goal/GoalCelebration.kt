package de.kalorienapp.ui.goal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.capture.ImageLoader
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.NeonPill
import de.kalorienapp.ui.components.RingSpec
import de.kalorienapp.ui.components.toInputString
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Großer Moment, wenn ein Ziel endet: Konfetti (nur bei erreichtem Gewicht), Ringe laufen voll,
 * Kennzahlen als Kacheln, Gewichtsverlauf über den ganzen Zielzeitraum. Nutzt die ganze Bildschirmhöhe
 * (oben Ringe + Titel, Mitte Zahlen + Verlauf, unten Knöpfe). Keine Vibration (Wunsch des Nutzers).
 */
@Composable
fun GoalCelebrationScreen(
    onShare: () -> Unit,
    onNewGoal: () -> Unit,
    onClose: () -> Unit,
    viewModel: GoalSuccessViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val success by viewModel.success.collectAsStateWithLifecycle()
    val s = success ?: return
    val reached = s.goal.endReason == GoalEndReason.REACHED
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.goal_celebrate_close)) }
                }
                // Alle vier Ringe laufen bis zum Anschlag voll – bzw. so weit, wie der Weg geschafft wurde.
                val fill = if (reached) 1f else s.progress.toFloat()
                ConcentricRings(
                    rings = listOf(
                        RingSpec(fill, Neon.Rainbow),
                        RingSpec(fill, listOf(Neon.Magenta)),
                        RingSpec(fill, listOf(Neon.Yellow)),
                        RingSpec(fill, listOf(Neon.Green)),
                    ),
                    modifier = Modifier.fillMaxWidth(0.6f),
                    strokeWidth = 12.dp,
                    gap = 5.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(signed(s.changeKg), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.unit_kg), style = MaterialTheme.typography.titleSmall, color = Neon.TextSecondary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(if (reached) R.string.goal_celebrate_reached else R.string.goal_celebrate_date),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (reached) {
                        stringResource(R.string.goal_celebrate_route, s.goal.startWeightKg.toInputString(), s.endWeightKg.toInputString())
                    } else {
                        stringResource(
                            R.string.goal_celebrate_progress,
                            abs(s.changeKg).toInputString(),
                            abs(s.goal.targetWeightKg - s.goal.startWeightKg).toInputString(),
                        )
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Neon.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeonPill(
                        signed(s.changeKg) + " " + stringResource(R.string.unit_kg),
                        stringResource(R.string.goal_celebrate_change_label),
                        Neon.Green,
                        Modifier.weight(1f),
                    )
                    NeonPill(s.days.toString(), pluralStringResource(R.plurals.goal_celebrate_days_label, s.days), Neon.Cyan, Modifier.weight(1f))
                    s.onTargetRate?.let {
                        NeonPill("${(it * 100).roundToInt()} %", stringResource(R.string.goal_celebrate_ontarget_label), Neon.Yellow, Modifier.weight(1f))
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.goal_celebrate_chart), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(rangeText(s), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
                        }
                        WeightChart(s.weightPoints, Modifier.fillMaxWidth().height(140.dp), labels = true, strokeWidth = 3.5.dp)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.goal_share), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onNewGoal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.goal_new)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.goal_celebrate_later), color = Neon.TextSecondary) }
            }
        }
        if (reached) Confetti(Modifier.fillMaxSize())
    }
}

/** Neon-Konfetti, das einmal von oben herabfällt (~4 s). Folgt der System-Animationsgeschwindigkeit. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        val random = Random(7)
        List(110) {
            ConfettiPiece(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                speed = 0.7f + random.nextFloat() * 0.6f,
                width = 6f + random.nextFloat() * 6f,
                height = 12f + random.nextFloat() * 10f,
                color = Neon.Rainbow[it % Neon.Rainbow.size],
                rotation = random.nextFloat() * 360f,
                spin = 180f + random.nextFloat() * 540f,
                sway = 2f + random.nextFloat() * 4f,
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(4200, easing = LinearEasing)) }
    Canvas(modifier) {
        val t = time.value
        if (t >= 1f) return@Canvas
        val scale = density
        pieces.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -40f + local * p.speed * (size.height + 120f)
            val x = p.x * size.width + sin(local * p.sway * 6f) * 18f * scale
            val alpha = if (local > 0.85f) (1f - local) / 0.15f else 1f
            rotate(p.rotation + local * p.spin, Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.width * scale / 2, y - p.height * scale / 2),
                    size = Size(p.width * scale, p.height * scale),
                    alpha = alpha.coerceIn(0f, 1f),
                )
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float, val delay: Float, val speed: Float, val width: Float, val height: Float,
    val color: Color, val rotation: Float, val spin: Float, val sway: Float,
)

/** Breite des geteilten Bildes in Pixeln (Höhe nach Format). */
private const val EXPORT_WIDTH = 1080f

/**
 * „Erfolg teilen“ wie bei Strava/Runna: oben eine Galerie aus Designs zum Durchwischen, darunter
 * Feintuning (Format, Hintergrund inkl. eigenem Foto oder transparent, Überschrift, Daten).
 * Das Bild wird in voller Auflösung (1080 px breit) aus derselben Zeichnung wie die Vorschau erzeugt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalShareScreen(
    onBack: () -> Unit,
    viewModel: GoalSuccessViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val success by viewModel.success.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLayer = rememberGraphicsLayer()
    val chooserTitle = stringResource(R.string.share_chooser)
    val photo by produceState<ImageBitmap?>(null, options.photo) {
        value = options.photo?.let { uri ->
            runCatching { ImageLoader.load(context, uri, maxSide = 1600).asImageBitmap() }.getOrNull()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.update { it.copy(photo = uri, background = ShareBackground.PHOTO) }
    }
    val templates = ShareTemplate.entries
    val pager = rememberPagerState(initialPage = options.template.ordinal) { templates.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page -> viewModel.update { it.copy(template = templates[page]) } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_share)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val bitmap = exportLayer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, chooserTitle)
                        }
                    },
                    enabled = success != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.share_button), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        val s = success ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Seitenbreite = Bildbreite, damit links und rechts die Nachbar-Designs hervorschauen.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = minOf(maxWidth - 72.dp, 440.dp * options.format.ratio)
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().height(440.dp),
                pageSize = PageSize.Fixed(cardWidth),
                contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                pageSpacing = 14.dp,
            ) { page ->
                BoxWithConstraints(
                    Modifier.fillMaxSize().graphicsLayer {
                        val distance = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val scale = 1f - 0.1f * distance
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.45f * distance
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    val ratio = options.format.ratio
                    val width = minOf(maxWidth, maxHeight * ratio)
                    val current = page == pager.currentPage
                    Box(
                        Modifier
                            .size(width, width / ratio)
                            .clip(RoundedCornerShape(20.dp))
                            .then(if (options.background == ShareBackground.TRANSPARENT) Modifier.checkerboard() else Modifier)
                            .drawWithContent {
                                drawContent()
                                // Das aktuelle Design zusätzlich in Exportgröße aufnehmen (ohne Schachbrett und Ecken).
                                if (current) {
                                    val k = EXPORT_WIDTH / size.width
                                    exportLayer.record(size = IntSize(EXPORT_WIDTH.toInt(), (EXPORT_WIDTH / ratio).roundToInt())) {
                                        scale(k, k, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
                                    }
                                }
                            },
                    ) {
                        // Entwurfsfläche (360 dp breit) auf die Vorschaubreite skalieren, Schriftgröße des Systems ignorieren.
                        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density * (width / ShareDesignWidth), 1f)) {
                            ShareCard(s, options, templates[page], photo)
                        }
                    }
                }
            }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(templates[pager.currentPage].labelRes), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    templates.indices.forEach { i ->
                        val selected = i == pager.currentPage
                        Box(
                            Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (selected) Neon.Cyan else Neon.TextSecondary.copy(alpha = 0.4f)),
                        )
                    }
                }
                Text(stringResource(R.string.share_swipe_hint), style = MaterialTheme.typography.labelMedium, color = Neon.TextSecondary)
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(R.string.share_format)
                ChoiceChips(
                    options = ShareFormat.entries,
                    selected = options.format,
                    onSelect = { format -> viewModel.update { it.copy(format = format) } },
                    label = { stringResource(it.labelRes) },
                )
                SectionTitle(R.string.share_background)
                ChoiceChips(
                    options = ShareBackground.entries,
                    selected = options.background,
                    onSelect = { bg ->
                        if (bg == ShareBackground.PHOTO && options.photo == null) {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            viewModel.update { it.copy(background = bg) }
                        }
                    },
                    label = { stringResource(it.labelRes) },
                )
                if (options.background == ShareBackground.PHOTO) {
                    TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.share_bg_change_photo))
                    }
                }
                if (options.background == ShareBackground.TRANSPARENT) {
                    Text(stringResource(R.string.share_bg_transparent_hint), style = MaterialTheme.typography.bodySmall, color = Neon.TextSecondary)
                }
                SectionTitle(R.string.share_headline)
                val reached = s.goal.endReason == GoalEndReason.REACHED
                OutlinedTextField(
                    value = options.headline,
                    onValueChange = { v -> viewModel.update { it.copy(headline = v.take(32)) } },
                    placeholder = { Text(stringResource(if (reached) R.string.share_card_reached else R.string.share_card_date)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val fields = options.template.fields.filter { it != ShareField.ON_TARGET || s.onTargetRate != null }
                if (fields.isNotEmpty()) {
                    SectionTitle(R.string.share_data)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareField.entries.filter { it in fields }.forEach { field ->
                            val on = field in options.fields
                            FilterChip(
                                selected = on,
                                onClick = {
                                    viewModel.update { it.copy(fields = if (on) it.fields - field else it.fields + field) }
                                },
                                label = { Text(stringResource(field.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Neon.Cyan,
                                    selectedLabelColor = Neon.OnNeon,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

/** Schachbrett hinter transparenten Bildern (nur Vorschau, nicht im Export). */
private fun Modifier.checkerboard() = drawBehind {
    val cell = 10.dp.toPx()
    drawRect(Color(0xFF2A2A33))
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(Color(0xFF1E1E26), Offset(x, y), Size(cell, cell))
            x += 2 * cell
        }
        y += cell
        row++
    }
}

/** PNG in den App-Cache (`share/`) und über das Teilen-Menü anbieten. */
private suspend fun shareImage(context: Context, bitmap: Bitmap, chooserTitle: String) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "share").apply { mkdirs() }.resolve("dunories-erfolg.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
