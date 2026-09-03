package com.neteinstein.donaclone.feature.devices

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neteinstein.donaclone.core.designsystem.component.DeviceGridTile
import com.neteinstein.donaclone.core.designsystem.component.DeviceTileVisualState
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceDisplayItem
import com.neteinstein.donaclone.core.model.Division
import kotlin.math.abs

/** How close (in raw pixels) the dragged header's center has to get to another header's center
 * before the two rooms swap places. A rough, un-tuned heuristic — there's no scroll compensation,
 * so this is meant for the common case of a handful of on-screen room sections, not a very long
 * list requiring the grid to auto-scroll mid-drag. */
private const val HEADER_SWAP_THRESHOLD_PX = 60f

/**
 * The room-sectioned device grid shared by the Home tab ([DevicesScreen]) and the Sensors tab
 * (`SensorsScreen`): a collapsible, drag-to-reorder list of room sections, each a 2-column grid of
 * [DeviceGridTile]s. [itemsByRoom] must already be in the desired display order (its iteration
 * order drives both rendering and drag-and-drop) — see [com.neteinstein.donaclone.feature.devices.DevicesUiState.roomOrder].
 *
 * The interactive callbacks ([onToggleBinaryOutput], [onFirePulse], etc.) are only ever invoked
 * for a [DeviceDisplayItem] whose primary device is of the matching type — a read-only-sensor
 * caller (the Sensors tab) can safely pass no-ops for all of them, since [DeviceCell] never
 * dispatches through them for a device kind that doesn't apply.
 */
@Composable
internal fun DeviceRoomGrid(
    itemsByRoom: Map<Int, List<DeviceDisplayItem>>,
    roomsById: Map<Int, Division>,
    collapsedRoomIds: Set<Int>,
    recentlyFiredDeviceIds: Set<Int>,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
    onGroupedTap: (DeviceDisplayItem.Grouped) -> Unit,
    onToggleRoomCollapsed: (Int) -> Unit,
    onToggleAllRooms: () -> Unit,
    onMoveRoom: (roomKey: Int, targetRoomKey: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Root-coordinate vertical center of each on-screen header, refreshed on every layout pass —
    // the drag math below compares the dragged header's live position against these.
    val headerCenters = remember { mutableStateMapOf<Int, Float>() }
    var draggingRoomKey by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    fun onHeaderDragStart(roomKey: Int) {
        draggingRoomKey = roomKey
        dragOffsetY = 0f
    }

    fun onHeaderDragBy(delta: Float) {
        dragOffsetY += delta
        val draggedKey = draggingRoomKey ?: return
        val baseCenter = headerCenters[draggedKey] ?: return
        val currentCenter = baseCenter + dragOffsetY
        val nearestOther =
            headerCenters.entries
                .filter { it.key != draggedKey }
                .minByOrNull { abs(it.value - currentCenter) }
        if (nearestOther != null && abs(nearestOther.value - currentCenter) < HEADER_SWAP_THRESHOLD_PX) {
            onMoveRoom(draggedKey, nearestOther.key)
        }
    }

    fun onHeaderDragEnd() {
        draggingRoomKey = null
        dragOffsetY = 0f
    }

    val allCollapsed = itemsByRoom.keys.isNotEmpty() && itemsByRoom.keys.all { it in collapsedRoomIds }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onToggleAllRooms) {
                Icon(
                    imageVector = if (allCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (allCollapsed) "Expand all" else "Collapse all")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            itemsByRoom.forEach { (roomKey, itemsInRoom) ->
                val isCollapsed = roomKey in collapsedRoomIds
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$roomKey") {
                    RoomSectionHeader(
                        title = roomsById[roomKey]?.name ?: "Unassigned",
                        collapsed = isCollapsed,
                        isDragging = draggingRoomKey == roomKey,
                        dragOffsetY = if (draggingRoomKey == roomKey) dragOffsetY else 0f,
                        onToggle = { onToggleRoomCollapsed(roomKey) },
                        onPositioned = { centerY -> headerCenters[roomKey] = centerY },
                        onDragStart = { onHeaderDragStart(roomKey) },
                        onDragBy = ::onHeaderDragBy,
                        onDragEnd = ::onHeaderDragEnd,
                    )
                }
                if (!isCollapsed) {
                    items(itemsInRoom, key = { it.primary.id }) { displayItem ->
                        DeviceCell(
                            item = displayItem,
                            recentlyFired = displayItem.primary.id in recentlyFiredDeviceIds,
                            onOpenDeviceDetail = onOpenDeviceDetail,
                            onToggleBinaryOutput = onToggleBinaryOutput,
                            onFirePulse = onFirePulse,
                            onShutterTap = onShutterTap,
                            onDimmerTap = onDimmerTap,
                            onGroupedTap = onGroupedTap,
                        )
                    }
                }
            }
        }
    }
}

/** A large, full-width tap target so collapsing a room is easy to hit — bigger type than a plain
 * section label, since this is also the room's primary click affordance, not just a heading. The
 * trailing [Icons.Filled.DragHandle] is the only part that starts a reorder drag (long-press then
 * drag), so it never fights the header's own tap-to-collapse gesture. */
@Composable
private fun RoomSectionHeader(
    title: String,
    collapsed: Boolean,
    isDragging: Boolean,
    dragOffsetY: Float,
    onToggle: () -> Unit,
    onPositioned: (centerY: Float) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(targetValue = if (collapsed) -90f else 0f, label = "room-chevron")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val topY = coordinates.positionInRoot().y
                    onPositioned(topY + coordinates.size.height / 2f)
                }
                .graphicsLayer { translationY = dragOffsetY }
                .zIndex(if (isDragging) 1f else 0f)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ExpandMore,
            contentDescription = if (collapsed) "Expand $title" else "Collapse $title",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(chevronRotation),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Reorder $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .padding(start = 12.dp)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragBy(dragAmount.y)
                            },
                        )
                    },
        )
    }
}

@Composable
private fun DeviceCell(
    item: DeviceDisplayItem,
    recentlyFired: Boolean,
    onOpenDeviceDetail: (Int) -> Unit,
    onToggleBinaryOutput: (Device.BinaryOutput) -> Unit,
    onFirePulse: (Device.Pulse) -> Unit,
    onShutterTap: (Device.Shutter) -> Unit,
    onDimmerTap: (Device.Dimmer) -> Unit,
    onGroupedTap: (DeviceDisplayItem.Grouped) -> Unit,
) {
    val device = item.primary
    val visualState: DeviceTileVisualState =
        when (device) {
            is Device.BinaryOutput -> DeviceTileVisualState.Toggle(device.isOn)
            is Device.Pulse -> DeviceTileVisualState.Toggle(recentlyFired)
            is Device.Shutter -> DeviceTileVisualState.FillLevel(device.percentage)
            // Lights only ever show On/Off on the Home tab — Google Home style, no separate
            // brightness readout cluttering the tile; the exact level lives in the detail screen.
            is Device.Dimmer -> DeviceTileVisualState.FillLevel(device.percentage, showPercentageLabel = false)
            else -> DeviceTileVisualState.ReadOnly(stateLabelFor(device))
        }

    val hasGroupedActions = item is DeviceDisplayItem.Grouped && (item.openAction != null || item.closeAction != null)
    val onClick: () -> Unit =
        if (item is DeviceDisplayItem.Grouped && device !is Device.Shutter && hasGroupedActions) {
            { onGroupedTap(item) }
        } else {
            when (device) {
                is Device.BinaryOutput -> {
                    { onToggleBinaryOutput(device) }
                }
                is Device.Pulse -> {
                    { onFirePulse(device) }
                }
                is Device.Shutter -> {
                    { onShutterTap(device) }
                }
                is Device.Dimmer -> {
                    { onDimmerTap(device) }
                }
                else -> {
                    {}
                }
            }
        }

    DeviceGridTile(
        name = item.displayName,
        icon = iconFor(device),
        online = device.online,
        visualState = visualState,
        onClick = onClick,
        onLongClick = { onOpenDeviceDetail(device.id) },
    )
}
