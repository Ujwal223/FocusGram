package com.ujwal.focusgram.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ujwal.focusgram.core.ui.components.FocusGramCard
import com.ujwal.focusgram.core.ui.components.FocusGramTopBar
import com.ujwal.focusgram.core.ui.theme.AlertRed
import com.ujwal.focusgram.core.ui.theme.DarkBorder
import com.ujwal.focusgram.core.ui.theme.DarkCard
import com.ujwal.focusgram.core.ui.theme.DarkCardElevated
import com.ujwal.focusgram.core.ui.theme.InstagramBlue
import com.ujwal.focusgram.core.ui.theme.PureBlack
import com.ujwal.focusgram.core.ui.theme.TextMuted
import com.ujwal.focusgram.core.ui.theme.TextPrimary
import com.ujwal.focusgram.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UI representation of a watched reel with aggregated rewatch counts.
 */
data class DisplayReelItem(
    val entry: ReelEntry,
    val rewatchCount: Int,
    val latestTimestamp: Long
)

/**
 * Searchable on-device viewer for watched Reels history.
 */
@Composable
fun ReelsHistoryScreen(
    reelHistory: ReelHistory,
    onBack: () -> Unit,
    onOpenReel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler {
        onBack()
    }

    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val allReels by reelHistory.items.collectAsState()
    val reels = remember(allReels, searchQuery) {
        val groups = LinkedHashMap<String, MutableList<ReelEntry>>()
        for (item in allReels) {
            val cleanCode = item.code.trim()
                .removePrefix("reel_")
                .removePrefix("/")
                .removeSuffix("/")
                .substringAfterLast("/reel/")
                .substringAfterLast("/reels/")
                .substringBefore("?")
                .substringBefore("&")
                .substringBefore("/")
                .trim()
                .lowercase()

            val ignoredCodes = setOf("reels", "reel", "videos", "audio", "clips", "home", "explore", "direct", "stories", "")
            val cleanAuthor = item.author.trim().lowercase().removePrefix("@")
            val cleanCaption = item.caption.trim().lowercase()
            val isUsernameCode = cleanCode == cleanAuthor

            val validCode = cleanCode.length >= 4 && cleanCode !in ignoredCodes && !isUsernameCode
            // Prioritize unique shortcode first so cards don't duplicate when metadata arrives
            val key = if (validCode) {
                "code_$cleanCode"
            } else if (cleanAuthor.isNotBlank() && cleanCaption.isNotBlank()) {
                "meta_${cleanAuthor}___${cleanCaption.take(80)}"
            } else {
                "url_${item.url.trim().substringBefore("?").trimEnd('/')}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(item)
        }

        val deduplicated = groups.values.map { list ->
            val latestSeen = list.maxOf { it.lastSeen }
            val maxViews = list.maxOf { it.views }
            val totalRewatchCount = maxOf(maxViews, list.size)
            val bestAuthor = list.firstOrNull { it.author.isNotBlank() }?.author ?: list.first().author
            val bestCaption = list.firstOrNull { it.caption.isNotBlank() }?.caption ?: list.first().caption
            val bestCode = list.firstOrNull {
                it.code.isNotBlank() && it.code.length >= 4 && it.code.lowercase() != it.author.trim().lowercase().removePrefix("@")
            }?.code ?: list.first().code

            val representative = list.first().copy(
                code = bestCode,
                author = bestAuthor,
                caption = bestCaption,
                lastSeen = latestSeen,
                views = totalRewatchCount
            )
            DisplayReelItem(
                entry = representative,
                rewatchCount = totalRewatchCount,
                latestTimestamp = latestSeen
            )
        }.sortedByDescending { it.latestTimestamp }

        if (searchQuery.isBlank()) {
            deduplicated
        } else {
            val q = searchQuery.trim().lowercase()
            deduplicated.filter {
                it.entry.author.lowercase().contains(q) ||
                it.entry.caption.lowercase().contains(q) ||
                it.entry.code.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(WindowInsets.statusBars.asPaddingValues())
    ) {
        FocusGramTopBar(
            title = "Reels History",
            onBack = onBack,
            actions = {
                if (allReels.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All",
                            tint = AlertRed
                        )
                    }
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by author or caption...", color = TextMuted) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard,
                focusedBorderColor = InstagramBlue,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (reels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = DarkCardElevated,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No Reels history yet" else "No matching reels found",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Watched reels will automatically record author and caption here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reels, key = { "${it.entry.code}_${it.entry.author}_${it.latestTimestamp}" }) { item ->
                    ReelHistoryCard(
                        item = item,
                        onClick = { onOpenReel(item.entry.url) },
                        onDelete = {
                            reelHistory.deleteMatching(item.entry.code, item.entry.author, item.entry.caption)
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Reels History",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose the timeframe you would like to clear:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    ClearOptionRow(
                        label = "Today",
                        subtitle = "Clear reels watched today",
                        onClick = {
                            reelHistory.clearToday()
                            showClearDialog = false
                        }
                    )

                    ClearOptionRow(
                        label = "3 Days",
                        subtitle = "Clear reels from the past 3 days",
                        onClick = {
                            reelHistory.clearPastDays(3)
                            showClearDialog = false
                        }
                    )

                    ClearOptionRow(
                        label = "Last Week",
                        subtitle = "Clear reels from the past 7 days",
                        onClick = {
                            reelHistory.clearPastDays(7)
                            showClearDialog = false
                        }
                    )

                    ClearOptionRow(
                        label = "This Month",
                        subtitle = "Clear reels from this calendar month",
                        onClick = {
                            reelHistory.clearThisMonth()
                            showClearDialog = false
                        }
                    )

                    ClearOptionRow(
                        label = "All History",
                        subtitle = "Permanently remove all recorded reel history",
                        isDestructive = true,
                        onClick = {
                            reelHistory.clear()
                            showClearDialog = false
                        }
                    )
                }
            },
            containerColor = DarkCardElevated,
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ClearOptionRow(
    label: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDestructive) AlertRed.copy(alpha = 0.12f) else DarkCard)
            .border(
                width = 1.dp,
                color = if (isDestructive) AlertRed.copy(alpha = 0.4f) else DarkBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = if (isDestructive) AlertRed else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = if (isDestructive) AlertRed else TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ReelHistoryCard(
    item: DisplayReelItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    FocusGramCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCardElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = InstagramBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (item.entry.author.isNotBlank()) {
                    Text(
                        text = "@${item.entry.author}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = InstagramBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = if (item.entry.caption.isNotBlank()) item.entry.caption else "Reel /${item.entry.code}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val timeStr = formatRelativeTime(item.latestTimestamp)
                    Text(
                        text = if (item.rewatchCount > 1) "$timeStr • x${item.rewatchCount}" else timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (item.rewatchCount > 1) InstagramBlue else TextMuted
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))
    }
}
