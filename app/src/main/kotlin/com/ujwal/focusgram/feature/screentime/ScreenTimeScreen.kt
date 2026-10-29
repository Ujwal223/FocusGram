package com.ujwal.focusgram.feature.screentime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import com.ujwal.focusgram.feature.history.ReelHistory
import com.ujwal.focusgram.core.utils.TimeFormatters
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.database.ScreenTimeDao
import com.ujwal.focusgram.core.database.ScreenTimeRecord
import com.ujwal.focusgram.core.ui.components.IOSBg
import com.ujwal.focusgram.core.ui.components.IOSBlue
import com.ujwal.focusgram.core.ui.components.IOSGroupedCard
import com.ujwal.focusgram.core.ui.components.IOSOrange
import com.ujwal.focusgram.core.ui.components.IOSPurple
import com.ujwal.focusgram.core.ui.components.IOSTeal
import com.ujwal.focusgram.core.ui.components.IOSTextPrimary
import com.ujwal.focusgram.core.ui.components.IOSTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.mutableStateOf
import com.ujwal.focusgram.core.ui.components.IOSDivider
import com.ujwal.focusgram.core.ui.components.IOSSectionTitle
import kotlin.math.max

enum class DailyBreakdownRange(val label: String) {
    PAST_7_DAYS("7 Days"),
    PAST_14_DAYS("14 Days"),
    PAST_30_DAYS("30 Days"),
    CUSTOM("Custom")
}

/**
 * Screen time analytics screen displaying daily usage charts, Reels consumption stats, and log history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTimeScreen(
    screenTimeDao: ScreenTimeDao,
    dailyLimitMinutes: Int = 60,
    onBack: () -> Unit,
    reelHistory: ReelHistory? = null,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBack()
    }

    val records by screenTimeDao.getAll().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dayLabelFormat = remember { SimpleDateFormat("EEE", Locale.US) }
    val todayStr = remember { dateFormat.format(Date()) }

    val todayRecord = records.firstOrNull { it.date == todayStr }
    val todayWatchedFromHistory by (reelHistory?.todayCount ?: flowOf(0)).collectAsState(initial = 0)
    val todaySeconds = todayRecord?.seconds ?: 0
    val todayReelsSeconds = todayRecord?.reelsSeconds ?: 0
    val todayMinutes = todaySeconds / 60
    val todayReelsMinutes = todayReelsSeconds / 60
    val todayReelsWatched = if (reelHistory != null) todayWatchedFromHistory else (todayRecord?.reelsWatched ?: 0)
    val limitFraction = if (dailyLimitMinutes > 0) (todayMinutes.toFloat() / dailyLimitMinutes).coerceIn(0f, 1f) else 0f

    var selectedRange by remember { mutableStateOf(DailyBreakdownRange.PAST_7_DAYS) }
    var customStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var customEndDateMillis by remember { mutableStateOf<Long?>(null) }
    var showCustomPicker by remember { mutableStateOf(false) }

    val todayUsageText = when {
        todaySeconds < 60 -> "${todaySeconds}s"
        todaySeconds % 60 == 0 -> "${todayMinutes}m"
        else -> "${todayMinutes}m ${todaySeconds % 60}s"
    }

    val todayReelsText = when {
        todayReelsSeconds == 0 -> "0m"
        todayReelsSeconds < 60 -> "${todayReelsSeconds}s"
        todayReelsSeconds % 60 == 0 -> "${todayReelsMinutes}m"
        else -> "${todayReelsMinutes}m ${todayReelsSeconds % 60}s"
    }

    val last7Days = remember(records) {
        val list = mutableListOf<Triple<String, Int, Boolean>>() // DayLabel, Minutes, isToday
        for (i in 6 downTo 0) {
            val dCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dStr = dateFormat.format(dCal.time)
            val dLabel = dayLabelFormat.format(dCal.time)
            val mins = ((records.firstOrNull { it.date == dStr }?.seconds) ?: 0) / 60
            list.add(Triple(dLabel, mins, i == 0))
        }
        list
    }

    val maxDayMinutes = remember(last7Days) {
        max(60, last7Days.maxOfOrNull { it.second } ?: 60)
    }

    val maxScale = remember(maxDayMinutes, dailyLimitMinutes) {
        max(maxDayMinutes, if (dailyLimitMinutes in 1..180) dailyLimitMinutes else maxDayMinutes)
    }

    val weeklyTotalMinutes = last7Days.sumOf { it.second }
    val weeklyDailyAverage = weeklyTotalMinutes / 7

    val displayedRecords = remember(records, selectedRange, customStartDateMillis, customEndDateMillis) {
        when (selectedRange) {
            DailyBreakdownRange.PAST_7_DAYS -> {
                val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
                val cutoffStr = dateFormat.format(cutoffCal.time)
                records.filter { it.date >= cutoffStr && it.date <= todayStr }.sortedByDescending { it.date }
            }
            DailyBreakdownRange.PAST_14_DAYS -> {
                val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -13) }
                val cutoffStr = dateFormat.format(cutoffCal.time)
                records.filter { it.date >= cutoffStr && it.date <= todayStr }.sortedByDescending { it.date }
            }
            DailyBreakdownRange.PAST_30_DAYS -> {
                val cutoffCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -29) }
                val cutoffStr = dateFormat.format(cutoffCal.time)
                records.filter { it.date >= cutoffStr && it.date <= todayStr }.sortedByDescending { it.date }
            }
            DailyBreakdownRange.CUSTOM -> {
                val startStr = customStartDateMillis?.let { dateFormat.format(Date(it)) } ?: "0000-00-00"
                val endStr = customEndDateMillis?.let { dateFormat.format(Date(it)) } ?: "9999-99-99"
                records.filter { it.date in startStr..endStr }.sortedByDescending { it.date }
            }
        }
    }

    val distinctYears = remember(displayedRecords) {
        displayedRecords.mapNotNull { it.date.split("-").getOrNull(0) }.distinct()
    }
    val distinctMonths = remember(displayedRecords) {
        displayedRecords.mapNotNull { 
            val parts = it.date.split("-")
            if (parts.size >= 2) "${parts[0]}-${parts[1]}" else null
        }.distinct()
    }
    val hasMultipleYears = distinctYears.size > 1
    val hasMultipleMonths = distinctMonths.size > 1

    val yesterdayStr = remember {
        val yCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        dateFormat.format(yCal.time)
    }
    val sevenDaysAgoStr = remember {
        val sCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        dateFormat.format(sCal.time)
    }

    val fullDayFormat = remember { SimpleDateFormat("EEEE", Locale.US) }
    val monthDayFormat = remember { SimpleDateFormat("MMM d", Locale.US) }
    val monthDayYearFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    fun formatRecordDate(dateStr: String): String {
        if (dateStr == todayStr) return "Today"
        if (dateStr == yesterdayStr) return "Yesterday"
        val parsed = try { dateFormat.parse(dateStr) } catch (_: Exception) { null } ?: return dateStr

        // Within past 7 days: show day of the week
        if (dateStr >= sevenDaysAgoStr && dateStr <= todayStr) {
            return fullDayFormat.format(parsed)
        }

        // Only show month & year if it includes multiple months and years
        return when {
            hasMultipleYears -> monthDayYearFormat.format(parsed)
            hasMultipleMonths -> monthDayFormat.format(parsed)
            else -> fullDayFormat.format(parsed)
        }
    }

    if (showCustomPicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        val end = pickerState.selectedEndDateMillis ?: start
                        if (start != null) {
                            customStartDateMillis = start
                            customEndDateMillis = end
                            selectedRange = DailyBreakdownRange.CUSTOM
                        }
                        showCustomPicker = false
                    }
                ) {
                    Text("Apply", color = IOSBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPicker = false }) {
                    Text("Cancel", color = IOSTextSecondary)
                }
            }
        ) {
            DateRangePicker(
                state = pickerState,
                title = { Text("Select Date Range", modifier = Modifier.padding(16.dp), color = IOSTextPrimary) },
                headline = { },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IOSBg)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = IOSTextPrimary
                )
            }
            Text(
                text = "Screen Time",
                color = IOSTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                IOSGroupedCard(modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "DAILY AVERAGE",
                                color = IOSTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${weeklyDailyAverage}m",
                                color = IOSTextPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Today: $todayUsageText" + if (todayReelsSeconds > 0) " • $todayReelsText in Reels" else "",
                                color = IOSTextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Apple-style bar chart area with fixed track heights and no clipping
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        ) {
                            // Subtle limit guideline when applicable
                            if (dailyLimitMinutes in 1..maxScale) {
                                val limitFraction = (dailyLimitMinutes.toFloat() / maxScale).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = (limitFraction * 80).dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(0.5.dp)
                                            .background(Color(0xFF3A3A3C))
                                    )
                                    Text(
                                        text = "Limit",
                                        color = IOSTextSecondary.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(bottom = 2.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                last7Days.forEach { (_, minutes, isToday) ->
                                    val barFraction = if (minutes > 0) (minutes.toFloat() / maxScale).coerceIn(0.06f, 1f) else 0f
                                    val animatedHeight by animateFloatAsState(
                                        targetValue = barFraction,
                                        animationSpec = tween(500),
                                        label = "barHeight"
                                    )

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (minutes > 0) {
                                            Text(
                                                text = "${minutes}m",
                                                color = if (isToday) IOSTextPrimary else IOSTextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(14.dp))
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Bar track container with constant 80dp height
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(80.dp),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            // Subtle background column track
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF2C2C2E))
                                            )

                                            // Active filled bar
                                            if (barFraction > 0f) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .fillMaxHeight(animatedHeight)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (dailyLimitMinutes > 0 && minutes >= dailyLimitMinutes) Color(0xFFFF453A)
                                                            else IOSBlue
                                                        )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Dedicated X-Axis day labels row: completely decoupled from bar height to prevent clipping
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            last7Days.forEach { (dayLabel, _, isToday) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = dayLabel,
                                        color = if (isToday) IOSTextPrimary else IOSTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                    if (isToday) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(3.dp)
                                                .clip(CircleShape)
                                                .background(IOSBlue)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(5.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = IOSDivider, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Glanceable metrics row following Apple HIG typography
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "USAGE TODAY",
                                    color = IOSTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = todayUsageText,
                                    color = IOSTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "REELS TIME",
                                    color = IOSTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = todayReelsText,
                                    color = IOSTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "WATCHED",
                                    color = IOSTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$todayReelsWatched reels",
                                    color = IOSTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item {
                IOSGroupedCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (dailyLimitMinutes > 0) "Daily Limit (${TimeFormatters.formatMinutes(dailyLimitMinutes)})" else "Daily Limit (Off)",
                                color = IOSTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (dailyLimitMinutes > 0) "${(limitFraction * 100).toInt()}% used" else "Off",
                                color = if (dailyLimitMinutes > 0 && limitFraction >= 1f) Color(0xFFFF453A) else IOSTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (dailyLimitMinutes > 0) "${TimeFormatters.formatMinutes(dailyLimitMinutes)} daily allowance" else "No daily time limit active",
                            color = IOSTextSecondary,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { limitFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (limitFraction >= 1f) Color(0xFFFF453A) else IOSBlue,
                            trackColor = Color(0xFF2C2C2E),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            item {
                IOSGroupedCard {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IOSOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = null,
                                tint = IOSOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Mindful Insight",
                                color = IOSTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    dailyLimitMinutes <= 0 -> "You have no daily limit set. Monitor your daily usage breakdown below to maintain mindful habits."
                                    weeklyDailyAverage <= dailyLimitMinutes -> "Great job! You are averaging ${TimeFormatters.formatMinutes(weeklyDailyAverage)} per day, well within your ${TimeFormatters.formatMinutes(dailyLimitMinutes)} daily limit."
                                    else -> "You are averaging ${TimeFormatters.formatMinutes(weeklyDailyAverage)} per day, which exceeds your planned ${TimeFormatters.formatMinutes(dailyLimitMinutes)} limit. Take regular breaks to recharge."
                                },
                                color = IOSTextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // iOS-style Segmented Control for time range
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(0.5.dp, Color(0xFF2C2C2E), RoundedCornerShape(9.dp))
                        .padding(2.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        DailyBreakdownRange.values().forEach { range ->
                            val isSelected = selectedRange == range
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (isSelected) Color(0xFF3A3A3C) else Color.Transparent)
                                    .clickable {
                                        if (range == DailyBreakdownRange.CUSTOM) {
                                            showCustomPicker = true
                                        } else {
                                            selectedRange = range
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = range.label,
                                    color = if (isSelected) Color.White else IOSTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            item {
                IOSSectionTitle("ACTIVITY BREAKDOWN")
            }

            item {
                IOSGroupedCard {
                    Column {
                        if (displayedRecords.isEmpty()) {
                            Text(
                                text = "No screen time recorded for this period.",
                                color = IOSTextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            displayedRecords.forEachIndexed { idx, record ->
                                val mins = record.seconds / 60
                                val reelsM = record.reelsSeconds / 60
                                val reelsS = record.reelsSeconds % 60
                                val totalDisplay = if (record.seconds < 60) "${record.seconds}s" else "$mins min"
                                val dayRatio = if (maxDayMinutes > 0) (mins.toFloat() / maxDayMinutes).coerceIn(0.04f, 1f) else 0f

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatRecordDate(record.date),
                                            color = IOSTextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val subtitleParts = mutableListOf<String>()
                                        if (record.reelsSeconds > 0) {
                                            val reelsLabel = if (record.reelsSeconds < 60) "${reelsS}s reels" else "${reelsM}m reels"
                                            subtitleParts.add(reelsLabel)
                                        }
                                        val watchedCount = if (record.date == todayStr && reelHistory != null) todayReelsWatched else record.reelsWatched
                                        if (watchedCount > 0) {
                                            subtitleParts.add("$watchedCount watched")
                                        }
                                        if (subtitleParts.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = subtitleParts.joinToString(" • "),
                                                color = IOSTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Apple-style horizontal proportion bar
                                        Box(
                                            modifier = Modifier
                                                .width(36.dp)
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFF2C2C2E)),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(dayRatio)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(
                                                        if (dailyLimitMinutes > 0 && mins >= dailyLimitMinutes) Color(0xFFFF453A)
                                                        else IOSBlue
                                                    )
                                            )
                                        }

                                        Text(
                                            text = totalDisplay,
                                            color = if (mins >= dailyLimitMinutes && dailyLimitMinutes > 0) Color(0xFFFF453A) else IOSTextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                if (idx < displayedRecords.lastIndex) {
                                    HorizontalDivider(
                                        color = IOSDivider,
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
