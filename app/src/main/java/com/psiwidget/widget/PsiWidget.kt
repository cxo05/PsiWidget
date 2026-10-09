package com.psiwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.psiwidget.data.AppJson
import com.psiwidget.data.Band
import com.psiwidget.data.Metric
import com.psiwidget.data.PsiSnapshot
import com.psiwidget.data.Region
import com.psiwidget.location.LocationActivity
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PsiWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }
}

class SelectMetricAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val metric = parameters[METRIC_PARAM] ?: return
        updateAppWidgetState(context, glanceId) { it[Keys.METRIC] = metric }
        PsiWidget().update(context, glanceId)
    }

    companion object {
        val METRIC_PARAM = ActionParameters.Key<String>("metric")
    }
}

private object Palette {
    val bg = ColorProvider(day = Color(0xFFFBFCFB), night = Color(0xFF1B2622))
    val fg = ColorProvider(day = Color(0xFF14201C), night = Color(0xFFEEF4F1))
    val muted = ColorProvider(day = Color(0xFF5F706A), night = Color(0xFF9FB2AB))
    val line = ColorProvider(day = Color(0xFFE1E7E4), night = Color(0xFF2D3C36))
    val seg = ColorProvider(day = Color(0xFFE9EEEC), night = Color(0xFF26332E))
    val segOn = ColorProvider(day = Color(0xFF14201C), night = Color(0xFFEEF4F1))
    val segOnFg = ColorProvider(day = Color(0xFFFBFCFB), night = Color(0xFF1B2622))
    val warn = ColorProvider(day = Color(0xFFE8822A), night = Color(0xFFF19A4D))
    val onBand = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF0E1513))
    val clear = ColorProvider(Color.Transparent)

    fun band(band: Band?) = when (band) {
        Band.GOOD -> ColorProvider(day = Color(0xFF1F9D55), night = Color(0xFF3FC07A))
        Band.MODERATE -> ColorProvider(day = Color(0xFF2F7FD1), night = Color(0xFF5AA2EE))
        Band.UNHEALTHY -> ColorProvider(day = Color(0xFFE8822A), night = Color(0xFFF19A4D))
        Band.VERY_UNHEALTHY -> ColorProvider(day = Color(0xFFD23B3B), night = Color(0xFFEE6464))
        Band.HAZARDOUS -> ColorProvider(day = Color(0xFF8A1F3D), night = Color(0xFFC8466A))
        null -> line
    }
}

/** Readings older than this are shown dimmed even if the last fetch succeeded. */
private val STALE_AFTER: Duration = Duration.ofMinutes(90)
private val SG = ZoneId.of("Asia/Singapore")
private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
private fun Content() {
    val prefs = currentState<Preferences>()
    val metric = Metric.fromKey(prefs[Keys.METRIC])
    val snapshot = prefs[Keys.SNAPSHOT]?.let { runCatching { AppJson.decodeFromString<PsiSnapshot>(it) }.getOrNull() }
    val updated = snapshot?.updated?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
    val offline = prefs[Keys.OFFLINE] ?: false
    val stale = offline || (updated != null && Duration.between(updated, OffsetDateTime.now()) > STALE_AFTER)
    val compact = LocalSize.current.height < 160.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(Palette.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Header(metric, updated, offline)
        Spacer(GlanceModifier.height(if (compact) 6.dp else 10.dp))
        if (snapshot == null) {
            Loading()
        } else {
            Body(
                snapshot = snapshot,
                metric = metric,
                nearest = Region.fromKey(prefs[Keys.REGION]),
                locationGranted = prefs[Keys.LOCATION_GRANTED] ?: false,
                stale = stale,
                compact = compact,
            )
        }
    }
}

@Composable
private fun Header(selected: Metric, updated: OffsetDateTime?, offline: Boolean) {
    val context = LocalContext.current
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = GlanceModifier.background(Palette.seg).cornerRadius(14.dp).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Metric.entries.forEach { metric ->
                val on = metric == selected
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(11.dp)
                        .background(if (on) Palette.segOn else Palette.clear)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .clickable(actionRunCallback<SelectMetricAction>(actionParametersOf(SelectMetricAction.METRIC_PARAM to metric.key))),
                ) {
                    Text(
                        text = metric.label,
                        style = TextStyle(
                            color = if (on) Palette.segOnFg else Palette.muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        val time = updated?.atZoneSameInstant(SG)?.format(TIME)?.lowercase()
        Text(
            text = when {
                offline && time != null -> "Offline · $time"
                offline -> "Offline"
                time != null -> "24h · $time"
                else -> ""
            },
            maxLines = 1,
            style = TextStyle(
                color = if (offline) Palette.warn else Palette.muted,
                fontSize = 11.sp,
                fontWeight = if (offline) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        Text(
            text = "↻",
            modifier = GlanceModifier
                .padding(start = 8.dp, end = 2.dp)
                .clickable(actionStartActivity(LocationActivity.intent(context, LocationActivity.MODE_REFRESH))),
            style = TextStyle(color = Palette.muted, fontSize = 16.sp),
        )
    }
}

@Composable
private fun ColumnScope.Loading() {
    Column(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("--", style = TextStyle(color = Palette.line, fontSize = 40.sp, fontWeight = FontWeight.Bold))
        Text("Loading first reading…", style = TextStyle(color = Palette.muted, fontSize = 12.sp))
    }
}

@Composable
private fun ColumnScope.Body(
    snapshot: PsiSnapshot,
    metric: Metric,
    nearest: Region?,
    locationGranted: Boolean,
    stale: Boolean,
    compact: Boolean,
) {
    val context = LocalContext.current
    val focus = nearest?.takeIf { snapshot.value(metric, it) != null } ?: snapshot.highest(metric)
    val isNear = focus != null && focus == nearest
    val focusBand = focus?.let { snapshot.band(metric, it) }
    val numberColor = if (stale) Palette.muted else Palette.fg

    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
        // Left: the region the user cares about.
        var left = GlanceModifier.width(if (compact) 96.dp else 108.dp).fillMaxHeight()
        if (!locationGranted) {
            left = left.clickable(actionStartActivity(LocationActivity.intent(context, LocationActivity.MODE_REQUEST)))
        }
        Column(modifier = left, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    focus == null -> ""
                    isNear -> "${focus.label} · near you"
                    else -> "Highest · ${focus.label}"
                },
                maxLines = 1,
                style = TextStyle(color = Palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = focus?.let { snapshot.value(metric, it)?.toString() } ?: "--",
                maxLines = 1,
                style = TextStyle(color = numberColor, fontSize = if (compact) 40.sp else 52.sp, fontWeight = FontWeight.Bold),
            )
            metric.unit?.let {
                Text(it, style = TextStyle(color = Palette.muted, fontSize = 10.sp))
            }
            if (focusBand != null) {
                Spacer(GlanceModifier.height(4.dp))
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(10.dp)
                        .background(Palette.band(focusBand))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = focusBand.label,
                        maxLines = 1,
                        style = TextStyle(color = Palette.onBand, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            if (!locationGranted) {
                Spacer(GlanceModifier.height(4.dp))
                Text("Tap to use location", maxLines = 1, style = TextStyle(color = Palette.muted, fontSize = 9.sp))
            }
        }

        Box(modifier = GlanceModifier.width(1.dp).fillMaxHeight().background(Palette.line)) {}
        Spacer(GlanceModifier.width(10.dp))

        // Right: all five regions laid out like a compass.
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
            val cell: @Composable RowScope.(Region?) -> Unit = { region ->
                RegionCell(snapshot, metric, region, region != null && region == nearest, stale, compact)
            }
            CompassRow { cell(null); cell(Region.NORTH); cell(null) }
            CompassRow { cell(Region.WEST); cell(Region.CENTRAL); cell(Region.EAST) }
            CompassRow { cell(null); cell(Region.SOUTH); cell(null) }
        }
    }
}

@Composable
private fun ColumnScope.CompassRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.RegionCell(
    snapshot: PsiSnapshot,
    metric: Metric,
    region: Region?,
    highlighted: Boolean,
    stale: Boolean,
    compact: Boolean,
) {
    val modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 4.dp)
    if (region == null) {
        Box(modifier = modifier) {}
        return
    }
    Row(
        modifier = modifier.cornerRadius(6.dp).background(if (highlighted) Palette.seg else Palette.clear),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = GlanceModifier.width(3.dp).fillMaxHeight().background(Palette.band(snapshot.band(metric, region)))) {}
        Spacer(GlanceModifier.width(6.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = snapshot.value(metric, region)?.toString() ?: "--",
                maxLines = 1,
                style = TextStyle(
                    color = if (stale) Palette.muted else Palette.fg,
                    fontSize = if (compact) 15.sp else 19.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = region.label.uppercase(),
                maxLines = 1,
                style = TextStyle(color = Palette.muted, fontSize = if (compact) 8.sp else 9.sp),
            )
        }
    }
}
