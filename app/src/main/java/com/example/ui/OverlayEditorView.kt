package com.example.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeyMapping
import com.example.data.KeyMapperRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun OverlayEditorView(
    profileId: Int,
    onDismiss: () -> Unit,
    onMappingsUpdated: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { KeyMapperRepository(context) }
    val scope = rememberCoroutineScope()

    var mappings by remember { mutableStateOf(emptyList<KeyMapping>()) }
    var selectedMapping by remember { mutableStateOf<KeyMapping?>(null) }
    var listenKeyMode by remember { mutableStateOf(false) }

    // Fetch mappings asynchronously
    LaunchedEffect(profileId) {
        if (profileId != -1) {
            repository.getMappingsFlow(profileId).collect {
                mappings = it
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        // Overlay Title and Top Panel
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // First Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screen Keymapper Overlay Editor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = "Drag keys. Tap a key node to change bind or delete.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onDismiss() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit Editor")
                }
            }

            // Second Row: Action Buttons spanning equally
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val newMap = KeyMapping(
                                profileId = profileId,
                                keyName = "New",
                                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                                xPercent = 50f,
                                yPercent = 50f
                            )
                            repository.saveMapping(newMap)
                            onMappingsUpdated()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add key")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Key")
                }

                Button(
                    onClick = {
                        scope.launch {
                            repository.seedSampleMappings(profileId)
                            onMappingsUpdated()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset default controls")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Setup")
                }
            }
        }

        // Draggable Nodes layer - Fullscreen match for absolute coordinate parity
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasW = maxWidth.value
            val canvasH = maxHeight.value

            for (mapping in mappings) {
                // Compute current visual coordinate
                val posX = (mapping.xPercent / 100f) * canvasW
                val posY = (mapping.yPercent / 100f) * canvasH

                var offsetX by remember(mapping.id) { mutableFloatStateOf(posX) }
                var offsetY by remember(mapping.id) { mutableFloatStateOf(posY) }

                val isSelected = selectedMapping?.id == mapping.id
                val nodeColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    Color.Black.copy(alpha = 0.65f)
                }
                val borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.8f)
                }
                val textColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.dp.roundToPx(), offsetY.dp.roundToPx()) }
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(nodeColor)
                        .border(
                            width = if (isSelected) 3.dp else 2.dp,
                            color = borderColor,
                            shape = CircleShape
                        )
                        .pointerInput(mapping.id) {
                            detectDragGestures(
                                onDragStart = {
                                    selectedMapping = mapping
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX = (offsetX + dragAmount.x / density).coerceIn(0f, canvasW - 64f)
                                    offsetY = (offsetY + dragAmount.y / density).coerceIn(0f, canvasH - 64f)
                                },
                                onDragEnd = {
                                    // Save new percentage
                                    val newXPercent = (offsetX / canvasW) * 100f
                                    val newYPercent = (offsetY / canvasH) * 100f
                                    scope.launch {
                                        repository.saveMapping(
                                            mapping.copy(
                                                xPercent = newXPercent.coerceIn(0f, 100f),
                                                yPercent = newYPercent.coerceIn(0f, 100f)
                                            )
                                        )
                                        onMappingsUpdated()
                                    }
                                }
                            )
                        }
                        .clickable {
                            selectedMapping = mapping
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .border(1.dp, borderColor.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = mapping.keyName,
                                style = MaterialTheme.typography.titleSmall,
                                color = textColor,
                                fontSize = 12.sp,
                                maxLines = 1,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = if (mapping.keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                                        (KeyEvent.keyCodeToString(mapping.keyCode) ?: "KEY_${mapping.keyCode}").replace("KEYCODE_", "")
                                    } else {
                                        mapping.mappingType ?: "TAP"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Configuration Details Menu (Visible only when node selected)
        selectedMapping?.let { currentMapping ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 440.dp),
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configure Node: ${currentMapping.keyName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        IconButton(
                            onClick = { selectedMapping = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close config", modifier = Modifier.size(16.dp))
                        }
                    }

                    // Key details trigger mapping type selection with safe layout stack
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Action Category:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(KeyMapping.TYPE_TAP, KeyMapping.TYPE_DPAD, KeyMapping.TYPE_MOUSE_LOOK, KeyMapping.TYPE_MACRO).forEach { type ->
                                FilterChip(
                                    selected = currentMapping.mappingType == type,
                                    onClick = {
                                        scope.launch {
                                            val updated = currentMapping.copy(mappingType = type)
                                            repository.saveMapping(updated)
                                            selectedMapping = updated
                                            onMappingsUpdated()
                                        }
                                    },
                                    label = { Text(type, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Listen key binding section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (listenKeyMode) {
                            Text(
                                text = "PRESS PHYSICAL KEY NOW...",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Pressing Esc will clear, or press any other button to map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick mock physical binder input for preview or container limitations
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("W", "Shift", "C", "F", "Space", "R").forEach { name ->
                                    Button(
                                        onClick = {
                                            val mockCode = when (name) {
                                                "W" -> KeyEvent.KEYCODE_W
                                                "Shift" -> KeyEvent.KEYCODE_SHIFT_LEFT
                                                "C" -> KeyEvent.KEYCODE_C
                                                "F" -> KeyEvent.KEYCODE_F
                                                "Space" -> KeyEvent.KEYCODE_SPACE
                                                "R" -> KeyEvent.KEYCODE_R
                                                else -> KeyEvent.KEYCODE_UNKNOWN
                                            }
                                            scope.launch {
                                                val updated = currentMapping.copy(
                                                    keyName = name,
                                                    keyCode = mockCode
                                                )
                                                repository.saveMapping(updated)
                                                selectedMapping = updated
                                                listenKeyMode = false
                                                onMappingsUpdated()
                                            }
                                        }
                                    ) { Text(name, fontSize = 11.sp) }
                                }
                            }
                        } else {
                            Text(
                                text = "Mapped key: " + if (currentMapping.keyCode != KeyEvent.KEYCODE_UNKNOWN)
                                    (KeyEvent.keyCodeToString(currentMapping.keyCode) ?: "KEY_${currentMapping.keyCode}").replace("KEYCODE_", "")
                                else
                                    "NOT MAPPED YET",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { listenKeyMode = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Icon(Icons.Default.Build, contentDescription = "Change binding")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bind Key")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    repository.deleteMapping(currentMapping)
                                    selectedMapping = null
                                    onMappingsUpdated()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete key")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Node")
                        }

                        Button(
                            onClick = { selectedMapping = null }
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

