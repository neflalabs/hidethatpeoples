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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.data.TargetApp
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import com.nefla.hidethatpeoples.privilege.PrivilegeType
import com.nefla.hidethatpeoples.ui.components.PairingBottomSheet
import com.nefla.hidethatpeoples.ui.components.openWirelessDebuggingSettings
import com.nefla.hidethatpeoples.ui.notification.PairingNotificationHelper
import com.nefla.hidethatpeoples.ui.theme.GreenOnline
import com.nefla.hidethatpeoples.ui.theme.OrangeWarning
import com.nefla.hidethatpeoples.ui.theme.RedOffline
import com.nefla.hidethatpeoples.worker.AutoCleanWorker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    privilegeManager: PrivilegeManager,
    onRefreshState: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val privilegeState by privilegeManager.activeState.collectAsStateWithLifecycle()
    val preferredProvider by privilegeManager.preferredProviderType.collectAsStateWithLifecycle()

    var isClearing by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    var enabledPackages by remember { mutableStateOf(prefs.enabledPackages) }
    var autoCleanEnabled by remember { mutableStateOf(prefs.isAutoCleanEnabled) }
    var lastClearedTimestamp by remember { mutableStateOf(prefs.lastClearedTimestamp) }

    val triggerNotificationPairing: () -> Unit = {
        privilegeManager.localAdbProvider.startPairingPortDiscovery()
        PairingNotificationHelper.showPairingNotification(context)
        openWirelessDebuggingSettings(context)
        scope.launch {
            snackbarHostState.showSnackbar("Notifikasi pairing dikirim! Tarik status bar ke bawah saat dialog kode muncul.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerNotificationPairing()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Izin notifikasi dibutuhkan untuk pairing tanpa menutup aplikasi Settings.")
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
            // 1. Privilege Status Card (Local ADB / Shizuku)
            item {
                PrivilegeStatusCard(
                    state = privilegeState,
                    preferredType = preferredProvider,
                    onStartNotificationPairing = startNotificationPairingFlow,
                    onOpenPairingSheet = { showPairingSheet = true },
                    onConnectAdb = { privilegeManager.localAdbProvider.startConnectPortDiscoveryAndConnect() },
                    onRequestShizukuPermission = { privilegeManager.shizukuProvider.requestPermission() },
                    onOpenShizukuApp = {
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
                    },
                    onSwitchProvider = { newType ->
                        privilegeManager.setPreferredProvider(newType)
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
                                if (!privilegeManager.isReady()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Setup Wireless ADB or Shizuku first!")
                                    }
                                    return@Button
                                }
                                scope.launch {
                                    isClearing = true
                                    val results = privilegeManager.clearMultipleShortcuts(enabledPackages)
                                    val count = results.values.count { it }
                                    prefs.lastClearedTimestamp = System.currentTimeMillis()
                                    lastClearedTimestamp = prefs.lastClearedTimestamp
                                    isClearing = false
                                    snackbarHostState.showSnackbar("Cleared shortcuts for $count apps!")
                                }
                            },
                            enabled = !isClearing && privilegeState is PrivilegeState.Ready,
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
                                text = "Add the 'Hide Peoples' tile to your notification panel for 1-tap quick cleaning anytime.",
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

    // Modal Pairing BottomSheet for Wireless ADB
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
                        "HideThatPeoples uses Built-in Wireless ADB (Android 11+) to invoke the system shortcut cleaner safely and completely standalone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "• 100% Standalone (No extra app needed)\n• No Root required\n• Safe for Mobile Banking apps\n• Zero system modifications",
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
fun PrivilegeStatusCard(
    state: PrivilegeState,
    preferredType: PrivilegeType,
    onStartNotificationPairing: () -> Unit,
    onOpenPairingSheet: () -> Unit,
    onConnectAdb: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onSwitchProvider: (PrivilegeType) -> Unit
) {
    val (statusColor, title, subtitle) = when (state) {
        is PrivilegeState.Ready -> Triple(
            GreenOnline,
            "${state.type.displayName} Terhubung",
            "Siap membersihkan target Direct Share secara mandiri."
        )
        is PrivilegeState.PairingRequired -> Triple(
            OrangeWarning,
            "Perlu Pairing Wireless ADB",
            "Pairing 1x dengan 6-digit kode via notifikasi tanpa menutup dialog Settings."
        )
        is PrivilegeState.Connecting -> Triple(
            MaterialTheme.colorScheme.primary,
            "Menghubungkan ke ADB...",
            "Mencari port dan membangun koneksi aman via local loopback."
        )
        is PrivilegeState.Disconnected -> Triple(
            RedOffline,
            "Layanan Terputus",
            "Wireless Debugging sedang nonaktif atau belum terhubung."
        )
        is PrivilegeState.Error -> Triple(
            RedOffline,
            "Koneksi Gagal",
            state.message
        )
    }

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
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Action Buttons based on state
            AnimatedVisibility(visible = state !is PrivilegeState.Ready) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (state) {
                        is PrivilegeState.PairingRequired -> {
                            OutlinedButton(onClick = onOpenPairingSheet) {
                                Text("Manual")
                            }
                            Button(onClick = onStartNotificationPairing) {
                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pair via Notifikasi")
                            }
                        }
                        is PrivilegeState.Disconnected -> {
                            if (preferredType == PrivilegeType.SHIZUKU) {
                                Button(onClick = onOpenShizukuApp) {
                                    Text("Buka Shizuku")
                                }
                            } else {
                                OutlinedButton(onClick = onConnectAdb) {
                                    Text("Hubungkan")
                                }
                                Button(onClick = onStartNotificationPairing) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pairing")
                                }
                            }
                        }
                        is PrivilegeState.Error -> {
                            if (preferredType == PrivilegeType.SHIZUKU) {
                                Button(onClick = onRequestShizukuPermission) {
                                    Text("Minta Izin")
                                }
                            } else {
                                OutlinedButton(onClick = onConnectAdb) {
                                    Text("Coba Lagi")
                                }
                                Button(onClick = onStartNotificationPairing) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pair Ulang")
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
