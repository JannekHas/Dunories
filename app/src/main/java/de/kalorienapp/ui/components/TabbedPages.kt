package de.kalorienapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.kalorienapp.ui.theme.Neon
import kotlinx.coroutines.launch

/**
 * Reiter oben + seitlich wischbare Seiten; jede Seite scrollt für sich.
 * Damit lange Screens (Statistik, Profil) in überschaubare Teile zerfallen.
 * Reiter sind gleich breit (z. B. Statistik: „Kalender“ genau mittig). Nur wenn ein Text dann nicht
 * passt (Profil: „Einstellungen“), teilen sie sich die Breite nach Textlänge.
 */
@Composable
fun TabbedPages(
    tabs: List<String>,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { tabs.size },
    page: @Composable ColumnScope.(Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Passen alle Texte (+ Rand) in gleich breite Reiter?
        val equalWidth = maxWidth / tabs.size
        val allFit = remember(tabs, labelStyle, equalWidth) {
            tabs.all { title ->
                val textWidth = with(density) { measurer.measure(title, labelStyle).size.width.toDp() }
                textWidth + TAB_SIDE_SPACE * 2 <= equalWidth
            }
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val selected = pagerState.currentPage == index
                val color by animateColorAsState(if (selected) Neon.Cyan else Neon.TextSecondary, label = "tab")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        // gleich breit, sonst Textlänge + fester Anteil für den Rand
                        .weight(if (allFit) 1f else title.length + 6f)
                        .height(48.dp)
                        .selectable(selected = selected, role = Role.Tab) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                ) {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, softWrap = false)
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (selected) Neon.Cyan else Color.Transparent),
                        )
                    }
                }
            }
        }
        }
        HorizontalDivider(color = Neon.TextSecondary.copy(alpha = 0.25f))
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 24.dp + LocalBottomBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { page(index) }
        }
    }
}

/** Mindestabstand links und rechts vom Reitertext. */
private val TAB_SIDE_SPACE = 12.dp
