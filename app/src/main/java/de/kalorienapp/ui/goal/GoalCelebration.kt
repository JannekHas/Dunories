package de.kalorienapp.ui.goal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.kalorienapp.R
import de.kalorienapp.domain.GoalSuccess
import de.kalorienapp.domain.model.GoalEndReason
import de.kalorienapp.ui.AppViewModelProvider
import de.kalorienapp.ui.components.ChoiceChips
import de.kalorienapp.ui.components.ConcentricRings
import de.kalorienapp.ui.components.GlassCard
import de.kalorienapp.ui.components.GlowBackground
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
 * Kennzahlen als Kacheln, kleiner Gewichtsverlauf. Keine Vibration (Wunsch des Nutzers).
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
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                modifier = Modifier.fillMaxWidth(0.55f),
                strokeWidth = 12.dp,
                gap = 5.dp,
            ) {
                Text(
                    signed(s.changeKg),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonPill(signed(s.changeKg) + " kg", stringResource(R.string.goal_celebrate_change_label), Neon.Green, Modifier.weight(1f))
                NeonPill(s.days.toString(), pluralStringResource(R.plurals.goal_celebrate_days_label, s.days), Neon.Cyan, Modifier.weight(1f))
                s.onTargetRate?.let {
                    NeonPill("${(it * 100).roundToInt()} %", stringResource(R.string.goal_celebrate_ontarget_label), Neon.Yellow, Modifier.weight(1f))
                }
            }
            if (s.weightPoints.size >= 2) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.goal_celebrate_chart), style = MaterialTheme.typography.labelLarge)
                        MiniWeightChart(s.weightPoints, Modifier.fillMaxWidth().height(80.dp))
                    }
                }
            }
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
            Spacer(Modifier.height(8.dp))
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

/** Kleiner Gewichtsverlauf ohne Achsen: violette Linie, Start cyan, Ende grün. */
@Composable
private fun MiniWeightChart(points: List<Pair<java.time.LocalDate, Double>>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val first = points.first().first.toEpochDay()
        val span = (points.last().first.toEpochDay() - first).coerceAtLeast(1).toFloat()
        val min = points.minOf { it.second }
        val range = (points.maxOf { it.second } - min).coerceAtLeast(0.5)
        val pad = 8.dp.toPx()
        fun at(p: Pair<java.time.LocalDate, Double>) = Offset(
            pad + (p.first.toEpochDay() - first) / span * (size.width - 2 * pad),
            pad + ((1 - (p.second - min) / range) * (size.height - 2 * pad)).toFloat(),
        )
        val path = Path().apply {
            points.forEachIndexed { i, p -> at(p).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        drawPath(path, Neon.Violet, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Neon.Cyan, 5.dp.toPx(), at(points.first()))
        drawCircle(Neon.Green, 6.dp.toPx(), at(points.last()))
    }
}

/**
 * „Erfolg teilen“: Baukasten aus Kacheln (an/aus) und Format; daraus entsteht ein Bild mit
 * Dunories-Schriftzug, das über das Teilen-Menü verschickt wird.
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
    val layer = rememberGraphicsLayer()
    val chooserTitle = stringResource(R.string.share_chooser)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goal_share)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val s = success ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Vorschau = das spätere Bild (wird aus genau dieser Ebene aufgenommen).
            Box(
                Modifier
                    .fillMaxWidth(if (options.story) 0.62f else 0.85f)
                    .align(Alignment.CenterHorizontally)
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) { ShareCard(s, options) }
            Text(stringResource(R.string.share_what), style = MaterialTheme.typography.titleMedium)
            ShareSwitch(stringResource(R.string.share_opt_change), options.change) { v -> viewModel.update { it.copy(change = v) } }
            ShareSwitch(stringResource(R.string.share_opt_weights), options.weights) { v -> viewModel.update { it.copy(weights = v) } }
            if (s.weightPoints.size >= 2) {
                ShareSwitch(stringResource(R.string.share_opt_chart), options.chart) { v -> viewModel.update { it.copy(chart = v) } }
            }
            ShareSwitch(stringResource(R.string.share_opt_duration), options.duration) { v -> viewModel.update { it.copy(duration = v) } }
            if (s.onTargetRate != null) {
                ShareSwitch(stringResource(R.string.share_opt_ontarget), options.onTarget) { v -> viewModel.update { it.copy(onTarget = v) } }
            }
            Text(stringResource(R.string.share_format), style = MaterialTheme.typography.titleMedium)
            ChoiceChips(
                options = listOf(false, true),
                selected = options.story,
                onSelect = { story -> viewModel.update { it.copy(story = story) } },
                label = { stringResource(if (it) R.string.share_format_story else R.string.share_format_square) },
            )
            Button(
                onClick = {
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, chooserTitle)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Neon.Cyan, contentColor = Neon.OnNeon),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(stringResource(R.string.share_button), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShareSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Neon.Cyan, checkedThumbColor = Neon.OnNeon),
        )
    }
}

/** Das teilbare Bild: Schriftzug, große Veränderung, gewählte Kacheln – auf dem Neon-Hintergrund der App. */
@Composable
private fun ShareCard(s: GoalSuccess, o: ShareOptions) {
    val reached = s.goal.endReason == GoalEndReason.REACHED
    Box(Modifier.fillMaxWidth().aspectRatio(if (o.story) 9f / 16f else 1f).clip(RoundedCornerShape(24.dp))) {
        GlowBackground {
            // Die vier vollen Ringe groß und angeschnitten in der Ecke – wie auf einem Poster.
            ConcentricRings(
                rings = listOf(
                    RingSpec(1f, Neon.Rainbow),
                    RingSpec(1f, listOf(Neon.Magenta)),
                    RingSpec(1f, listOf(Neon.Yellow)),
                    RingSpec(1f, listOf(Neon.Green)),
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth(0.62f)
                    .offset(x = 48.dp, y = 48.dp)
                    .alpha(0.9f),
                strokeWidth = 14.dp,
                gap = 6.dp,
            )
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painterResource(R.drawable.wordmark_dunories),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.height(26.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(if (reached) R.string.share_card_reached else R.string.share_card_date),
                    style = MaterialTheme.typography.labelLarge,
                    color = Neon.Cyan,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (o.change) {
                    Text(signed(s.changeKg) + " kg", style = MaterialTheme.typography.displayMedium)
                }
                if (o.weights) {
                    Text(
                        stringResource(R.string.goal_celebrate_route, s.goal.startWeightKg.toInputString(), s.endWeightKg.toInputString()),
                        style = MaterialTheme.typography.titleMedium,
                        color = Neon.TextSecondary,
                    )
                }
                if (o.chart && s.weightPoints.size >= 2) {
                    MiniWeightChart(s.weightPoints, Modifier.fillMaxWidth().height(if (o.story) 110.dp else 64.dp))
                }
                if (o.duration || (o.onTarget && s.onTargetRate != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (o.duration) ShareChip(pluralStringResource(R.plurals.share_card_days, s.days, s.days), Neon.Cyan)
                        if (o.onTarget) s.onTargetRate?.let {
                            ShareChip(stringResource(R.string.share_card_ontarget, (it * 100).roundToInt()), Neon.Yellow)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShareChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Neon.OnNeon,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .drawWithContent { drawRect(color); drawContent() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
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
