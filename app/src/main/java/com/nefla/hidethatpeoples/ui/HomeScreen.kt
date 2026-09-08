package com.nefla.hidethatpeoples.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.data.TargetApp
import com.nefla.hidethatpeoples.shizuku.ShizukuManager
import com.nefla.hidethatpeoples.ui.theme.GreenOnline
import com.nefla.hidethatpeoples.ui.theme.OrangeWarning
import com.nefla.hidethatpeoples.ui.theme.RedOffline
import com.nefla.hidethatpeoples.worker.AutoCleanWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    shizukuState: ShizukuManager.ShizukuState,
    onRequestShizukuPermission: () -> Unit,
    onRefreshState: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isClearing by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var enabledPackages by remember { mutableStateOf(prefs.enabledPackages) }
    var autoCleanEnabled by remember { mutableStateOf(prefs.isAutoCleanEnabled) }
    var lastClearedTimestamp by remember { mutableStateOf(prefs.lastClearedTimestamp) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("HideThatPeoples", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { onRefreshState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Help")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Shizuku Status Banner
            item {
                ShizukuStatusCard(
                    state = shizukuState,
                    onRequestPermission = onRequestShizukuPermission,
                    onOpenShizuku = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            val playStoreIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                            )
                            context.startActivity(playStoreIntent)
                        }
                    }
                )
            }

            // 2. Clear Action Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Direct Share Contact Cleaner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Instantly clears dynamic share targets from WhatsApp, Telegram, and other chat apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (!ShizukuManager.isReady()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Shizuku is not running or unauthorized!")
                                    }
                                    return@Button
                                }
                                scope.launch {
                                    isClearing = true
                                    val results = ShizukuManager.clearMultipleShortcuts(enabledPackages)
                                    val count = results.values.count { it }
                                    prefs.lastClearedTimestamp = System.currentTimeMillis()
                                    lastClearedTimestamp = prefs.lastClearedTimestamp
                                    isClearing = false
                                    snackbarHostState.showSnackbar("Cleared shortcuts for $count apps!")
                                }
                            },
                            enabled = !isClearing && shizukuState == ShizukuManager.ShizukuState.READY,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isClearing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clearing...")
                            } else {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear Direct Share Contacts Now")
                            }
                        }

                        if (lastClearedTimestamp > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val relativeTime = DateUtils.getRelativeTimeSpanString(
                                lastClearedTimestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            )
                            Text(
                                text = "Last cleared: $relativeTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 3. Quick Settings Tile Tip
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Quick Settings Tile",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Add the 'Hide Peoples' tile to your phone's notification panel for 1-tap quick cleaning anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // 4. Background Auto-Clean Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-Clean in Background",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Periodically sweep shortcuts so contacts rarely linger in your share sheet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCleanEnabled,
                                onCheckedChange = { isChecked ->
                                    autoCleanEnabled = isChecked
                                    prefs.isAutoCleanEnabled = isChecked
                                    if (isChecked) {
                                        AutoCleanWorker.schedule(context, prefs.autoCleanIntervalMinutes)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Background auto-clean scheduled!")
                                        }
                                    } else {
                                        AutoCleanWorker.cancel(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Background auto-clean disabled.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 5. Target Apps Section Header
            item {
                Text(
                    text = "Target Messaging Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // List of target apps
            items(TargetApp.DEFAULT_TARGETS) { app ->
                val isSelected = enabledPackages.contains(app.packageName)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = app.displayName,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                prefs.setPackageEnabled(app.packageName, checked)
                                enabledPackages = prefs.enabledPackages
                            }
                        )
                    }
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("About HideThatPeoples") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Android creates dynamic shortcuts whenever you chat with people on WhatsApp, Telegram, or SMS. These contacts then automatically appear on top of your Share Sheet (Direct Share).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Because Android restricts normal apps from tampering with other apps, HideThatPeoples uses Shizuku (ADB API via Wireless Debugging) to safely invoke the system shortcut cleaner.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• No Root required\n• Safe for Mobile Banking apps\n• Your device warranty remains intact",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
fun ShizukuStatusCard(
    state: ShizukuManager.ShizukuState,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit
) {
    val (statusColor, title, subtitle) = when (state) {
        ShizukuManager.ShizukuState.READY -> Triple(
            GreenOnline,
            "Shizuku Connected",
            "Ready to manage and clear direct share targets."
        )
        ShizukuManager.ShizukuState.PERMISSION_REQUIRED -> Triple(
            OrangeWarning,
            "Permission Required",
            "Shizuku service is active, but HideThatPeoples needs your authorization."
        )
        ShizukuManager.ShizukuState.NOT_RUNNING -> Triple(
            RedOffline,
            "Shizuku Not Running",
            "Please start Shizuku via Wireless Debugging or launch the Shizuku app."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = state != ShizukuManager.ShizukuState.READY) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (state == ShizukuManager.ShizukuState.PERMISSION_REQUIRED) {
                        Button(onClick = onRequestPermission) {
                            Text("Grant Permission")
                        }
                    } else if (state == ShizukuManager.ShizukuState.NOT_RUNNING) {
                        OutlinedButton(onClick = onOpenShizuku) {
                            Text("Open Shizuku App")
                        }
                    }
                }
            }
        }
    }
}
