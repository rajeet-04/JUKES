package com.example.juke.ui.components.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.juke.R
import com.example.juke.ui.components.SwipeRevealBackground
import com.example.juke.ui.components.swipeGlass
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassCard
import com.example.juke.ui.theme.pressScale
import androidx.compose.ui.semantics.Role
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics

@Composable
fun QueueBottomSheetContent(
    currentTrack: com.example.juke.models.Track,
    queue: List<com.example.juke.models.Track>,
    queueIndex: Int,
    uiState: com.example.juke.viewmodels.MusicUiState,
    onClose: () -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveTrack: (trackId: String, index: Int) -> Unit,
    onPlayTrack: (track: com.example.juke.models.Track, index: Int) -> Unit,
    statusText: String? = null,
    pending: List<com.example.juke.services.QueueManager.PendingSong> = emptyList(),
    onPlayPending: (id: String) -> Unit = {},
    onRemovePending: (id: String) -> Unit = {},
    heldNext: List<com.example.juke.services.QueueManager.PendingSong> = emptyList(),
    onPlayNextCopy: (track: com.example.juke.models.Track) -> Unit = {},
    onPlayNextPending: (id: String) -> Unit = {},
    onShuffleUpcoming: () -> Unit = {},
    onSortUpcoming: () -> Unit = {},
    onClearPlayed: () -> Unit = {},
    onSaveAsPlaylist: (name: String, includeWaiting: Boolean) -> Unit = { _, _ -> },
    suggestedPlaylistName: () -> String = { "Queue" }
) {
    val haptic = rememberJukeHaptics()
    // Songs already loaded when the sheet opened don't replay the colour-up.
    val openedWith = remember { queue.drop(queueIndex + 1).mapTo(HashSet()) { it.uuid } }
    var showSave by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
    ) {
        // Header with close button and title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(JukeIcons.Back, "Close")
            }
            Text(
                "Up Next",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            var showTools by remember { mutableStateOf(false) }

            if (showSave) {
                SaveQueueDialog(
                    initialName = remember { suggestedPlaylistName() },
                    readyCount = queue.distinctBy { it.uuid }.size,
                    waitingCount = pending.size,
                    onDismiss = { showSave = false },
                    onSave = { name, includeWaiting ->
                        showSave = false
                        onSaveAsPlaylist(name, includeWaiting)
                    }
                )
            }
            Box {
                IconButton(onClick = { haptic.click(); showTools = true }) {
                    Icon(JukeIcons.More, "Queue tools")
                }
                androidx.compose.material3.DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Shuffle upcoming") },
                        onClick = { showTools = false; onShuffleUpcoming() }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Sort upcoming A–Z") },
                        onClick = { showTools = false; onSortUpcoming() }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Clear played") },
                        onClick = { showTools = false; onClearPlayed() }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Save queue as playlist") },
                        onClick = { showTools = false; showSave = true }
                    )
                }
            }
        }
        if (statusText != null) {
            Text(
                statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Queue list with swipe-to-remove
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Current track indicator
            item(key = "now_playing_card") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playing indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_play_24),
                                contentDescription = "Now Playing",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentTrack.thumbnailUri != null) {
                                AsyncImage(
                                    model = currentTrack.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = JukeIcons.Play,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Now Playing",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                currentTrack.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Text(
                                currentTrack.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            // Upcoming: fetched songs first, then the ones still waiting to be fetched.
            val upcomingTracks = queue.drop(queueIndex + 1)
            if (upcomingTracks.isNotEmpty() || pending.isNotEmpty() || heldNext.isNotEmpty()) {
                item(key = "queue_header_text") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            buildString {
                                append("${upcomingTracks.size} ready")
                                if (pending.isNotEmpty() || heldNext.isNotEmpty()) {
                                    append(" · ${pending.size + heldNext.size} waiting")
                                }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // A queue worth keeping: save it where the list starts.
                        Row(
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .glassPane(GlassShapes.Pill, GlassLevel.Thin)
                                .clickable(role = Role.Button) { haptic.click(); showSave = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                JukeIcons.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Save as playlist",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // "Play next" picks still loading: they go right after the playing song once ready.
                items(items = heldNext, key = { "queue_held_${it.id}" }) { song ->
                    QueueSongRow(
                        imageUrl = song.thumbnailUrl,
                        title = song.title,
                        artist = "Plays next · ${song.artist}",
                        ready = false,
                        onClick = {},
                        modifier = Modifier.animateItem()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = upcomingTracks,
                    key = { index, track -> "queue_item_${track.uuid}_$index" }
                ) { index, track ->
                    val actualQueueIndex = queueIndex + 1 + index
                    val density = LocalDensity.current

                    // Drag state for visual feedback
                    var dragOffset by remember { mutableFloatStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }
                    var lastDragPosition by remember { mutableIntStateOf(0) }
                    val itemHeightPx = with(density) { QueueRowHeight.toPx() }

                    // The swipe callback can fire repeatedly while past the threshold: one copy per swipe.
                    val copyQueued = remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { distance -> distance * 0.5f },
                        confirmValueChange = { dismissValue ->
                            // Allow swipe dismissal only when the item is not being reordered.
                            when {
                                isDragging -> false
                                dismissValue == SwipeToDismissBoxValue.Settled -> {
                                    copyQueued.value = false
                                    false
                                }
                                dismissValue == SwipeToDismissBoxValue.EndToStart -> {
                                    haptic.reject()
                                    onRemoveTrack(track.uuid, actualQueueIndex)
                                    true
                                }
                                // Swipe right: a copy plays next; this one keeps its place.
                                dismissValue == SwipeToDismissBoxValue.StartToEnd -> {
                                    if (!copyQueued.value) {
                                        copyQueued.value = true
                                        haptic.confirm()
                                        onPlayNextCopy(track)
                                    }
                                    false
                                }
                                else -> false
                            }
                        }
                    )

                    // Reset dismiss state when dragging starts
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }

                    // Songs that finish loading while the sheet is open colour up from the greyed state.
                    val arrived = track.uuid !in openedWith
                    var ready by remember { mutableStateOf(!arrived) }
                    LaunchedEffect(Unit) { ready = true }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = !isDragging,
                        enableDismissFromEndToStart = !isDragging,
                        backgroundContent = {
                            if (!isDragging) {
                                SwipeRevealBackground(
                                    dismissState = dismissState,
                                    startLabel = "Play next",
                                    startIcon = JukeIcons.Queue,
                                    endLabel = "Remove",
                                    endIcon = JukeIcons.Delete
                                )
                            }
                        },
                        modifier = Modifier
                            .animateItem()
                            .zIndex(if (dragOffset != 0f) 100f else 0f)
                    ) {
                        val scale by animateFloatAsState(
                            if (isDragging) 1.03f else 1f,
                            label = "DragScale"
                        )

                        QueueSongRow(
                            imageUrl = track.thumbnailUri,
                            title = track.title,
                            artist = track.artist,
                            ready = ready,
                            onClick = {
                                haptic.click()
                                onPlayTrack(track, actualQueueIndex)
                            },
                            modifier = Modifier
                                .graphicsLayer {
                                    translationY = dragOffset
                                    shadowElevation = if (dragOffset != 0f) 12f else 0f
                                    this.scaleX = scale
                                    this.scaleY = scale
                                }
                                .swipeGlass(dismissState)
                        ) {
                            // Drag handle
                            Icon(
                                imageVector = JukeIcons.List,
                                contentDescription = "Drag to reorder",
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(24.dp)
                                    .pointerInput(Unit) {
                                        if (!uiState.isQueueOperationInProgress) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    haptic.gestureStart()
                                                    dragOffset = 0f
                                                    lastDragPosition = 0
                                                    isDragging = true
                                                },
                                                onDragEnd = {
                                                    val positionsToMove =
                                                        (dragOffset / itemHeightPx).toInt()

                                                    if (positionsToMove != 0) {
                                                        val targetIndex =
                                                            (actualQueueIndex + positionsToMove).coerceIn(
                                                                queueIndex + 1,
                                                                queue.size - 1
                                                            )
                                                        if (targetIndex != actualQueueIndex) {
                                                            onMoveTrack(
                                                                actualQueueIndex,
                                                                targetIndex
                                                            )
                                                        }
                                                    }
                                                    dragOffset = 0f
                                                    isDragging = false
                                                    haptic.gestureEnd()
                                                },
                                                onDragCancel = {
                                                    dragOffset = 0f
                                                    isDragging = false
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount.y

                                                    // Ratchet feedback when dragged item crosses another item's boundary.
                                                    val currentPosition = (dragOffset / itemHeightPx).toInt()
                                                    if (currentPosition != lastDragPosition) {
                                                        haptic.tick()
                                                        lastDragPosition = currentPosition
                                                    }
                                                }
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }

                items(items = pending, key = { "queue_pending_${it.id}" }) { song ->
                    val copyQueued = remember { mutableStateOf(false) }
                    val dismissState = rememberSwipeToDismissBoxState(
                        positionalThreshold = { distance -> distance * 0.5f },
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.EndToStart -> if (!song.loading) {
                                    haptic.reject()
                                    onRemovePending(song.id)
                                    true
                                } else false
                                // Swipe right: fetch a copy now and play it next once it is ready.
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (!copyQueued.value) {
                                        copyQueued.value = true
                                        haptic.confirm()
                                        onPlayNextPending(song.id)
                                    }
                                    false
                                }
                                SwipeToDismissBoxValue.Settled -> {
                                    copyQueued.value = false
                                    false
                                }
                            }
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = !song.loading,
                        backgroundContent = {
                            SwipeRevealBackground(
                                dismissState = dismissState,
                                startLabel = "Play next",
                                startIcon = JukeIcons.Queue,
                                endLabel = if (song.loading) null else "Remove",
                                endIcon = JukeIcons.Delete
                            )
                        },
                        modifier = Modifier.animateItem()
                    ) {
                        QueueSongRow(
                            imageUrl = song.thumbnailUrl,
                            title = song.title,
                            artist = song.artist,
                            ready = false,
                            onClick = {
                                haptic.click()
                                onPlayPending(song.id)
                            },
                            modifier = Modifier.swipeGlass(dismissState)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (song.duration != null) {
                                    Text(
                                        song.duration,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Queue is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Height of one queue row including its hairline; drag-to-reorder steps by it. */
private val QueueRowHeight = 61.dp

/**
 * Flat queue row in the Library / Search style: artwork, title and artist, a trailing slot and a
 * hairline underneath. A song that isn't fetched yet is greyed (desaturated artwork, dimmed text)
 * and colours up when it becomes ready.
 */
@Composable
private fun QueueSongRow(
    imageUrl: String?,
    title: String,
    artist: String,
    ready: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    val readiness by animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
        label = "QueueRowReady"
    )
    val source = remember { MutableInteractionSource() }
    val artFilter = remember(readiness) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(readiness) })
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(source)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = source,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .graphicsLayer { alpha = 0.5f + 0.5f * readiness },
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (readiness < 1f) artFilter else null
                    )
                } else {
                    Icon(
                        imageVector = JukeIcons.Play,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = 0.45f + 0.55f * readiness }
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}

/** Name the saved queue; optionally bring the greyed waiting songs along (they load in the background). */
@Composable
internal fun SaveQueueDialog(
    initialName: String,
    readyCount: Int,
    waitingCount: Int,
    onDismiss: () -> Unit,
    onSave: (name: String, includeWaiting: Boolean) -> Unit
) {
    val haptic = rememberJukeHaptics()
    var name by remember { mutableStateOf(initialName) }
    var includeWaiting by remember { mutableStateOf(waitingCount > 0) }
    com.example.juke.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save queue as playlist") },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "$readyCount ${if (readyCount == 1) "song" else "songs"} from the queue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (waitingCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { includeWaiting = !includeWaiting }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = includeWaiting,
                            onCheckedChange = { includeWaiting = it }
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Also add $waitingCount waiting songs", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "They download in the background",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    haptic.heavyClick()
                    onSave(name, includeWaiting)
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { haptic.click(); onDismiss() }) { Text("Cancel") }
        }
    )
}
