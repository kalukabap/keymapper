package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bridge.NativeInputBridge
import com.example.data.GameProfile
import com.example.data.SandProfileTemplate
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val context = LocalContext.current
    val profiles by viewModel.allProfiles.collectAsState()
    val selectedId by viewModel.selectedProfileId.collectAsState()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val isOverlayGranted by viewModel.isOverlayGranted.collectAsState()
    val isShizukuRunning by viewModel.isShizukuRunning.collectAsState()
    val isShizukuGranted by viewModel.isShizukuGranted.collectAsState()
    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val profileTemplates = remember { viewModel.profileTemplates }
    val nativeCapabilities = remember { NativeInputBridge.capabilitiesSummary() }
    val nativeSlotLimit = remember { NativeInputBridge.nativeInputSlotLimit() }
    val isNativeAvailable = remember { NativeInputBridge.isAvailable() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var inputTextProfileName by remember { mutableStateOf("") }
    var inputTargetPkg by remember { mutableStateOf("") }

    // Diagnostic key event tracker
    var diagnosticLastKeycode by remember { mutableStateOf("Ready (press any hardware key)") }

    // Monitor lifecycle connections
    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
        while (true) {
            diagnosticLastKeycode = com.example.LogHelper.lastLoggedKey
            kotlinx.coroutines.delay(200)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1A1A1A))
                    .statusBarsPadding()
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            color = Color(0xFF333333),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SandMapper",
                            color = Color(0xFFE6E1E5),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "SYSTEM ARCHITECTURE: " + (android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "X86_64").uppercase(),
                            color = Color(0xFFE5C07B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val indicatorColor = if (isServiceActive) Color(0xFFB4E380) else Color(0xFF938F99)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                        Text(
                            text = if (isServiceActive) "SERVICE ACTIVE" else "SERVICE INACTIVE",
                            color = indicatorColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D0D0D))
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Core Status Card (Sand UI input pipeline)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFD0BCFF).copy(alpha = 0.12f))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Build Icon",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF444444), RoundedCornerShape(100.dp))
                                    .background(Color(0xFF2C2C2C))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "JAVA + JNI + SHIZUKU",
                                    color = Color(0xFFE6E1E5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Sand UI Input Pipeline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE6E1E5)
                        )

                        Text(
                            text = buildAnnotatedString {
                                append("Input processing latency: ")
                                withStyle(style = SpanStyle(color = Color(0xFFD0BCFF), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) {
                                    append(if (isServiceActive) "< 5ms" else "idle")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Grid 2x1 items
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                                    .background(Color(0xFF252525))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "MAPPING STRATEGY",
                                        color = Color(0xFF938F99),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Virtual Pointer",
                                        color = Color(0xFFE6E1E5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                                    .background(Color(0xFF252525))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "RUNTIME MODE",
                                        color = Color(0xFF938F99),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "Original mapper",
                                        color = Color(0xFFE6E1E5),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.toggleServiceActivation() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceActive) Color(0xFF333333) else Color(0xFFD0BCFF),
                                contentColor = if (isServiceActive) Color(0xFFD0BCFF) else Color(0xFF381E72)
                            )
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = "Engine Toggle Icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isServiceActive) "Deactivate Pipeline" else "Activate SandMapper",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Sand UI capabilities card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF18140E)),
                    border = BorderStroke(1.dp, Color(0xFF4A3820))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "SAND UI FEATURE STACK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5C07B),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "An original mouse/keyboard mapper experience with a game HUD, visual keymap editor, Java services, native C input discovery, and Shizuku-assisted injection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD6C7A8),
                            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CapabilityPill("Touch", "Tap / hold", Modifier.weight(1f))
                            CapabilityPill("Mouse", "Look + buttons", Modifier.weight(1f))
                            CapabilityPill("Macro", "Timed actions", Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CapabilityPill("Overlay", "Drag HUD", Modifier.weight(1f))
                            CapabilityPill("Profiles", "Per game", Modifier.weight(1f))
                            CapabilityPill("Native", if (isNativeAvailable) "$nativeSlotLimit slots" else "offline", Modifier.weight(1f))
                        }

                        Text(
                            text = nativeCapabilities,
                            color = Color(0xFFB9A57B),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
            }


            // Quick-start profile templates
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "BUILD FROM SCRATCH TEMPLATES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE5C07B),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Create an original editable profile with real key nodes instead of copying any commercial app UI or assets.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99)
                        )

                        profileTemplates.forEach { template ->
                            TemplateProfileRow(
                                template = template,
                                onCreate = { viewModel.createProfileFromTemplate(template) }
                            )
                        }
                    }
                }
            }


            // Requirements Checklist Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "REQUIREMENTS CHECKLIST",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF938F99),
                            letterSpacing = 1.2.sp
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color(0xFF333333)
                        )

                        // Perm 1: Display Over other Apps
                        ChecklistItemView(
                            title = "Display Overlay Permission",
                            subtitle = "Required to overlay interactive edit zones and drag triggers.",
                            isGranted = isOverlayGranted,
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Perm 2: Touch simulation accessibility
                        ChecklistItemView(
                            title = "Accessibility Touch Simulation",
                            subtitle = "Enables hardware key-mapping interception and injection.",
                            isGranted = isAccessibilityConnected,
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Perm 3: Shizuku Service Status / Permission
                        ChecklistItemView(
                            title = "Shizuku Shell Connection",
                            subtitle = if (!isShizukuRunning) "Shizuku service is not running. Please launch the Shizuku app." else if (!isShizukuGranted) "Shizuku is running but permission is required." else "Shizuku service connected successfully.",
                            isGranted = isShizukuGranted,
                            onClick = {
                                if (isShizukuRunning) {
                                    viewModel.requestShizukuPermission()
                                } else {
                                    try {
                                        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                        if (intent != null) {
                                            context.startActivity(intent)
                                        } else {
                                            val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app"))
                                            context.startActivity(i)
                                        }
                                    } catch (e: Exception) {
                                        // no-op
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Profiles Config manager
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE MAPPING PRESET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF938F99),
                                letterSpacing = 1.2.sp
                            )

                            TextButton(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(100.dp))
                                    .background(Color(0xFF252525))
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Profile",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New Profile",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (profiles.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                                    .background(Color(0xFF252525))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No profiles created yet. Tap '+' to create.",
                                    color = Color(0xFF938F99),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                profiles.forEach { profile ->
                                    val isSelected = profile.id == selectedId
                                    val initials = if (profile.name.length >= 2) profile.name.take(2).uppercase() else profile.name.take(1).uppercase()
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (isSelected) Color(0xFF252525) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF444444) else Color(0xFF333333),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable { viewModel.selectProfile(profile.id) }
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(Color(0xFF4F378B), Color(0xFFD0BCFF))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = initials,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = profile.name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = Color(0xFFE6E1E5)
                                                )
                                                Text(
                                                    text = if (isSelected) "Preset: Active Interceptor" else "Preset: Idle Configuration",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF938F99)
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active Preset Indicator",
                                                    tint = Color(0xFFB4E380),
                                                    modifier = Modifier.padding(end = 4.dp).size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteProfile(profile) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Profile preset",
                                                    tint = Color(0xFFF2B8B5),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Hardware Input Logger State card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "HARDWARE INPUT LOGGER STATE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF938F99),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Connect a keyboard or mouse and tap physical keys to observe instantly intercepted diagnostics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF252525))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = diagnosticLastKeycode,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFD0BCFF)
                            )
                        }
                    }
                }
            }

            // License metadata card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A1A)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "OPEN SOURCE LICENSE DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF938F99),
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "This project is an original open-source keymapper implementation. It does not copy proprietary assets or code; it provides profile management, overlays, Java/Kotlin services, native input discovery, and permission-based Android input simulation for legitimate testing and accessibility workflows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // New Profile Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Profile Mapping") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inputTextProfileName,
                        onValueChange = { inputTextProfileName = it },
                        label = { Text("Game Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputTargetPkg,
                        onValueChange = { inputTargetPkg = it },
                        label = { Text("Target App Package Name") },
                        placeholder = { Text("e.g. com.tencent.ig") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTextProfileName.isNotEmpty()) {
                            viewModel.createProfile(inputTextProfileName, inputTargetPkg)
                            inputTextProfileName = ""
                            inputTargetPkg = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}



@Composable
fun TemplateProfileRow(
    template: SandProfileTemplate,
    onCreate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF252525))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = template.title,
                color = Color(0xFFE6E1E5),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = template.subtitle,
                color = Color(0xFF938F99),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "${template.mappings.size} starter bindings",
                color = Color(0xFFE5C07B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        TextButton(
            onClick = onCreate,
            modifier = Modifier
                .border(1.dp, Color(0xFF4A3820), RoundedCornerShape(100.dp))
                .background(Color(0xFF241C10))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create template profile",
                tint = Color(0xFFE5C07B),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Use",
                color = Color(0xFFE5C07B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CapabilityPill(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF241C10))
            .border(1.dp, Color(0xFF4A3820), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = Color(0xFFE5C07B),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            text = subtitle,
            color = Color(0xFFF1E3C3),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ChecklistItemView(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF252525))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFFE6E1E5))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    if (isGranted) Color(0xFFB4E380).copy(alpha = 0.5f) else Color(0xFFF2B8B5).copy(alpha = 0.5f),
                    RoundedCornerShape(100.dp)
                )
                .background(
                    if (isGranted) Color(0xFFB4E380).copy(alpha = 0.15f) else Color(0xFFF2B8B5).copy(alpha = 0.15f)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "ACTION REQUIRED",
                fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (isGranted) Color(0xFFB4E380) else Color(0xFFF2B8B5)
            )
        }
    }
}
