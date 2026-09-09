package com.nefla.hidethatpeoples.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.data.InstalledTargetApp
import com.nefla.hidethatpeoples.data.TargetAppManager
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import com.nefla.hidethatpeoples.ui.components.ConnectPortDialog
import com.nefla.hidethatpeoples.ui.components.PairingBottomSheet
import com.nefla.hidethatpeoples.ui.components.openWirelessDebuggingSettings
import com.nefla.hidethatpeoples.ui.notification.PairingNotificationHelper
import com.nefla.hidethatpeoples.ui.theme.GreenOnline
import com.nefla.hidethatpeoples.ui.theme.OrangeWarning
import com.nefla.hidethatpeoples.ui.theme.RedOffline
import com.nefla.hidethatpeoples.worker.AutoCleanWorker
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    privilegeManager: PrivilegeManager,
    onRefreshState: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val targetAppManager = remember { TargetAppManager(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val privilegeState by privilegeManager.activeState.collectAsStateWithLifecycle()

    var isClearing by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    var showConnectDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    val enabledPackages by prefs.enabledPackagesFlow.collectAsStateWithLifecycle(initialValue = prefs.enabledPackages)

    val autoCleanEnabled by prefs.isAutoCleanEnabledFlow.collectAsStateWithLifecycle(initialValue = prefs.isAutoCleanEnabled)
    val autoCleanInterval by prefs.autoCleanIntervalMinutesFlow.collectAsStateWithLifecycle(initialValue = prefs.autoCleanIntervalMinutes)
    val lastClearedTimestamp by prefs.lastClearedTimestampFlow.collectAsStateWithLifecycle(initialValue = prefs.lastClearedTimestamp)
    var currentTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(privilegeState) {
        if (privilegeState is PrivilegeState.ConnectPortRequired) {
            showConnectDialog = true
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                currentTicker = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Dynamic Installed Target Apps State
    var installedApps by remember { mutableStateOf<List<InstalledTargetApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: Chat & Social, 1: All, 2: Selected

    // Load installed apps
    LaunchedEffect(Unit) {
        val apps = targetAppManager.getInstalledTargets()
        installedApps = apps
        isLoadingApps = false

        // On first run, enable all potential Direct Share apps automatically
        if (!prefs.isConfigured()) {
            val potential = apps.filter { it.isPotentialDirectShare }.map { it.packageName }.toSet()
            prefs.initializeWithDefaultsIfFirstRun(if (potential.isNotEmpty()) potential else apps.map { it.packageName }.toSet())
        }
    }

    val triggerNotificationPairing: () -> Unit = {
        privilegeManager.localAdbProvider.startPairingPortDiscovery()
        PairingNotificationHelper.showPairingNotification(context)
        openWirelessDebuggingSettings(context)
        scope.launch {
            snackbarHostState.showSnackbar("Pairing notification sent! Pull down status bar to enter pairing code.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerNotificationPairing()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Notification permission is required to pair without leaving Settings.")
            }
        }
    }

    val startNotificationPairingFlow: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                triggerNotificationPairing()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            triggerNotificationPairing()
        }
    }

    // Filter apps based on search query and selected filter tab
    val filteredApps = remember(installedApps, searchQuery, selectedFilterIndex, enabledPackages) {
        installedApps.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilterIndex) {
                0 -> app.isPotentialDirectShare
                1 -> true
                2 -> enabledPackages.contains(app.packageName)
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val chatCount = remember(installedApps) { installedApps.count { it.isPotentialDirectShare } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "HideThatPeoples",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Direct Share Contact Cleaner",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    onRefreshState()
                    privilegeManager.reconnect()
                    val apps = targetAppManager.getInstalledTargets()
                    installedApps = apps
                    currentTicker = System.currentTimeMillis()
                    isRefreshing = false
                    snackbarHostState.showSnackbar("Targets & connection status refreshed")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
            // 1. Unified Compact Hero Dashboard Card (Status + Direct Share Cleaner)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Status Row
                        val (statusColor, title, subtitle) = when (val state = privilegeState) {
                            is PrivilegeState.Ready -> Triple(
                                GreenOnline,
                                if (state.type == com.nefla.hidethatpeoples.privilege.PrivilegeType.ROOT) "Root Access Active" else "Wireless ADB Ready",
                                state.details
                            )
                            is PrivilegeState.PairingRequired -> Triple(
                                OrangeWarning,
                                "Pairing Required",
                                "Open Wireless Debugging in Developer Options."
                            )
                            is PrivilegeState.ConnectPortRequired -> Triple(
                                OrangeWarning,
                                "Active Port Required",
                                state.message ?: "Pairing succeeded! Enter active port from Developer Options."
                            )
                            is PrivilegeState.Connecting -> Triple(
                                MaterialTheme.colorScheme.primary,
                                "Connecting...",
                                "Connecting to local ADB daemon..."
                            )
                            is PrivilegeState.Disconnected -> Triple(
                                RedOffline,
                                "Service Disconnected",
                                "Wireless Debugging is currently inactive."
                            )
                            is PrivilegeState.Error -> Triple(
                                RedOffline,
                                "Connection Issue",
                                state.message
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                val badgeText = when (val s = privilegeState) {
                                    is PrivilegeState.Ready -> s.type.displayName
                                    else -> "Internal ADB"
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Inline Action Buttons for all states (Never hides or disappears unexpectedly)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (val state = privilegeState) {
                                is PrivilegeState.Ready -> {
                                    if (state.type == com.nefla.hidethatpeoples.privilege.PrivilegeType.LOCAL_ADB) {
                                        OutlinedButton(
                                            onClick = { openWirelessDebuggingSettings(context) },
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Settings", fontSize = 11.sp)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                privilegeManager.reconnect()
                                                snackbarHostState.showSnackbar("Refreshed status")
                                            }
                                        },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Refresh", fontSize = 11.sp)
                                    }
                                }
                                is PrivilegeState.Connecting -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Connecting...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { showConnectDialog = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Manual Port", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { privilegeManager.cancelConnecting() },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Cancel", fontSize = 11.sp)
                                    }
                                }
                                is PrivilegeState.PairingRequired -> {
                                    OutlinedButton(
                                        onClick = { openWirelessDebuggingSettings(context) },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { showPairingSheet = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text("Manual", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = startNotificationPairingFlow,
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pair Notification", fontSize = 12.sp)
                                    }
                                }
                                is PrivilegeState.ConnectPortRequired -> {
                                    OutlinedButton(
                                        onClick = { openWirelessDebuggingSettings(context) },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                    Button(
                                        onClick = { showConnectDialog = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Enter Port", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                is PrivilegeState.Disconnected -> {
                                    OutlinedButton(
                                        onClick = { openWirelessDebuggingSettings(context) },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { showConnectDialog = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Enter Port", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = startNotificationPairingFlow,
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pair", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { privilegeManager.reconnect() },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                is PrivilegeState.Error -> {
                                    OutlinedButton(
                                        onClick = { openWirelessDebuggingSettings(context) },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                    OutlinedButton(
                                        onClick = { showConnectDialog = true },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Enter Port", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = startNotificationPairingFlow,
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pair", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { privilegeManager.reconnect() },
                                        modifier = Modifier.height(34.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reconnect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        // Clear Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Direct Share Cleaner",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${enabledPackages.size} target apps selected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Button(
                                onClick = {
                                    if (!privilegeManager.isReady()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Setup Wireless ADB first!")
                                        }
                                        return@Button
                                    }
                                    if (enabledPackages.isEmpty()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Select at least 1 target app!")
                                        }
                                        return@Button
                                    }
                                    scope.launch {
                                        try {
                                            isClearing = true
                                            val results = withTimeoutOrNull(15000L) {
                                                privilegeManager.clearMultipleShortcuts(enabledPackages)
                                            } ?: emptyMap()
                                            val count = results.values.count { it }
                                            val now = System.currentTimeMillis()
                                            prefs.lastClearedTimestamp = now
                                            currentTicker = now
                                            if (count > 0) {
                                                snackbarHostState.showSnackbar("Successfully cleared shortcuts for $count apps!")
                                            } else {
                                                snackbarHostState.showSnackbar("Clear finished ($count shortcuts cleared).")
                                            }
                                        } catch (e: Throwable) {
                                            snackbarHostState.showSnackbar("Error clearing shortcuts: ${e.message}")
                                        } finally {
                                            isClearing = false
                                        }
                                    }
                                },
                                enabled = !isClearing && privilegeState is PrivilegeState.Ready && enabledPackages.isNotEmpty(),
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isClearing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clearing...", fontSize = 13.sp)
                                } else {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear Contacts Now", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (lastClearedTimestamp > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val relativeTime = remember(lastClearedTimestamp, currentTicker) {
                                DateUtils.getRelativeTimeSpanString(
                                    lastClearedTimestamp,
                                    currentTicker,
                                    DateUtils.MINUTE_IN_MILLIS
                                )
                            }
                            Text(
                                text = "Last cleared: $relativeTime",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // 2. Compact Control Strip (Auto-Clean & Quick Settings Tile side-by-side)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card Auto-Clean
                    val intervalText = remember(autoCleanInterval) {
                        when {
                            autoCleanInterval < 60 -> "${autoCleanInterval}m"
                            autoCleanInterval % 60 == 0L -> "${autoCleanInterval / 60}h"
                            else -> "${autoCleanInterval}m"
                        }
                    }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showIntervalDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Auto-Clean",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Schedule",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                Text(
                                    text = if (autoCleanEnabled) "Active (Every $intervalText)" else "Disabled (Tap to set)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (autoCleanEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoCleanEnabled,
                                onCheckedChange = { isChecked ->
                                    prefs.isAutoCleanEnabled = isChecked
                                    if (isChecked) {
                                        AutoCleanWorker.schedule(context, autoCleanInterval)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Auto-clean enabled! Sweeping now & every $intervalText.")
                                        }
                                    } else {
                                        AutoCleanWorker.cancel(context)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Background auto-clean disabled.")
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Card Quick Tile
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Widgets,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Quick Tile",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "1-Tap from Notification Shade",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Target Apps Section Header with Batch Actions
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Target Applications",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "${enabledPackages.size} active",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val all = filteredApps.map { it.packageName }
                                    prefs.setAllPackagesEnabled(all, true)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("All", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    val all = filteredApps.map { it.packageName }
                                    prefs.setAllPackagesEnabled(all, false)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 11.sp)
                            }
                        }
                    }

                    // Compact Modern Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps or package name...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Filter Chips with Accent Icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilterIndex == 0,
                            onClick = { selectedFilterIndex = 0 },
                            leadingIcon = {
                                Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(13.dp))
                            },
                            label = { Text("Chat & Social ($chatCount)", fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        FilterChip(
                            selected = selectedFilterIndex == 1,
                            onClick = { selectedFilterIndex = 1 },
                            leadingIcon = {
                                Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(13.dp))
                            },
                            label = { Text("All (${installedApps.size})", fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                        FilterChip(
                            selected = selectedFilterIndex == 2,
                            onClick = { selectedFilterIndex = 2 },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                            },
                            label = { Text("Selected (${enabledPackages.size})", fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // Target Apps Listing
            if (isLoadingApps) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No apps matching '$searchQuery'" else "No target apps found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = enabledPackages.contains(app.packageName)
                    val iconBitmap = remember(app.packageName) {
                        try {
                            targetAppManager.getAppIcon(app.packageName)?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                prefs.setPackageEnabled(app.packageName, !isSelected)
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // App Icon
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Android,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (app.isPotentialDirectShare) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ChatBubble,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(9.dp),
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = "Chat & Social",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = app.packageName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    prefs.setPackageEnabled(app.packageName, checked)
                                },
                                modifier = Modifier.scale(0.82f)
                            )
                        }
                    }
                }
            }
        }
    }
    }

    // Modal Pairing BottomSheet for Manual Wireless ADB
    if (showPairingSheet) {
        val detectedPort = (privilegeState as? PrivilegeState.PairingRequired)?.detectedPort
        PairingBottomSheet(
            onDismissRequest = { showPairingSheet = false },
            detectedPort = detectedPort,
            onPair = { code, port ->
                privilegeManager.pairLocalAdb(code, port)
            }
        )
    }

    // Modal Connect Dialog for Manual Wireless ADB Port
    if (showConnectDialog) {
        ConnectPortDialog(
            initialPort = prefs.lastAdbConnectPort,
            onDismissRequest = { showConnectDialog = false },
            onConnect = { port ->
                scope.launch {
                    privilegeManager.reconnect(port)
                    snackbarHostState.showSnackbar("Connecting to port $port...")
                }
            }
        )
    }

    // Modal Interval Selection Dialog for Auto-Clean
    if (showIntervalDialog) {
        val intervalOptions = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
        val intervalLabels = listOf(
            "Every 15 minutes",
            "Every 30 minutes (Recommended)",
            "Every 1 hour",
            "Every 3 hours",
            "Every 6 hours",
            "Every 12 hours",
            "Every 24 hours"
        )

        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text("Auto-Clean Schedule", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Choose how often HideThatPeoples automatically clears direct share contacts in the background:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    intervalOptions.forEachIndexed { idx, minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    prefs.autoCleanIntervalMinutes = minutes
                                    if (autoCleanEnabled) {
                                        AutoCleanWorker.schedule(context, minutes)
                                    }
                                    showIntervalDialog = false
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = autoCleanInterval == minutes,
                                onClick = {
                                    prefs.autoCleanIntervalMinutes = minutes
                                    if (autoCleanEnabled) {
                                        AutoCleanWorker.schedule(context, minutes)
                                    }
                                    showIntervalDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = intervalLabels[idx],
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (autoCleanInterval == minutes) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modern Clean Material 3 About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Surface(
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "HideThatPeoples",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "v1.0.0 • Privacy Utility",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Instantly clears direct share contact recommendations from your Android share sheet using on-device Wireless ADB or Root privilege.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Unified Grouped Details Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // 1. Developer Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Developer",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "neflalabs",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )

                            // 2. Instagram Row (Clickable)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/neflalabs"))
                                            context.startActivity(intent)
                                        } catch (_: Throwable) {}
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Instagram",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "@neflalabs",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open Instagram",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )

                            // 3. License Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "License",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "MIT License (Open Source)",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { showAboutDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
