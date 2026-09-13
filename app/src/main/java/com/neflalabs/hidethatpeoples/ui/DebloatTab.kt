package com.neflalabs.hidethatpeoples.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.neflalabs.hidethatpeoples.data.DebloatApp
import com.neflalabs.hidethatpeoples.data.DebloatCategory
import com.neflalabs.hidethatpeoples.data.DebloatManager
import com.neflalabs.hidethatpeoples.data.DebloatSafety
import com.neflalabs.hidethatpeoples.privilege.PrivilegeManager
import com.neflalabs.hidethatpeoples.privilege.PrivilegeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatTab(
    privilegeManager: PrivilegeManager,
    privilegeState: PrivilegeState,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val debloatManager = remember { DebloatManager(context) }
    val scope = rememberCoroutineScope()

    var debloatApps by remember { mutableStateOf<List<DebloatApp>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var actionInProgressPackage by remember { mutableStateOf<String?>(null) }
    var appPendingAction by remember { mutableStateOf<Pair<DebloatApp, String>?>(null) }

    fun refreshList() {
        scope.launch {
            isLoading = true
            debloatApps = debloatManager.loadDebloatApps()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    val categories = listOf(
        DebloatCategory.PRESET_POPULAR to "Popular",
        DebloatCategory.OEM_CARRIER to "OEM/Vendor",
        DebloatCategory.GOOGLE to "Google",
        DebloatCategory.ALL_SYSTEM to "All System"
    )

    val currentCategory = categories[selectedCategoryIndex].first

    val filteredList = remember(debloatApps, searchQuery, selectedCategoryIndex) {
        debloatApps.filter { app ->
            val matchesCategory = if (selectedCategoryIndex == 3) {
                true // All System displays everything
            } else {
                app.category == currentCategory
            }

            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    // Confirmation dialog for Debloat Actions
    if (appPendingAction != null) {
        val (app, actionType) = appPendingAction!!
        AlertDialog(
            onDismissRequest = { appPendingAction = null },
            icon = {
                Icon(
                    imageVector = if (actionType == "uninstall") Icons.Default.DeleteForever else Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (actionType == "uninstall") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = when (actionType) {
                        "uninstall" -> "Uninstall for User 0?"
                        "reinstall" -> "Reinstall Application?"
                        "disable" -> "Disable / Freeze App?"
                        "enable" -> "Enable Application?"
                        else -> "Confirm Action"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "${app.appName} (${app.packageName})",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (actionType) {
                            "uninstall" -> "This removes the app for the current user profile (pm uninstall -k --user 0). It can be restored later with 'Reinstall'."
                            "reinstall" -> "This restores the original system package for the current user (cmd package install-existing)."
                            "disable" -> "This freezes the app so it will not run or consume battery (pm disable-user --user 0)."
                            "enable" -> "This unfreezes the application (pm enable)."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.safetyLevel == DebloatSafety.CAUTION) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Caution: This is a core component. Modifying it may affect system features.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPkg = app.packageName
                        val action = actionType
                        appPendingAction = null
                        scope.launch {
                            actionInProgressPackage = targetPkg
                            try {
                                val res = when (action) {
                                    "disable" -> privilegeManager.disableApp(targetPkg)
                                    "enable" -> privilegeManager.enableApp(targetPkg)
                                    "uninstall" -> privilegeManager.uninstallUser0(targetPkg)
                                    "reinstall" -> privilegeManager.reinstallUser0(targetPkg)
                                    else -> Result.failure(Exception("Unknown action"))
                                }
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Success: ${res.getOrNull()?.ifBlank { "Done" }}")
                                    refreshList()
                                } else {
                                    snackbarHostState.showSnackbar("Failed: ${res.exceptionOrNull()?.message}")
                                }
                            } finally {
                                actionInProgressPackage = null
                            }
                        }
                    },
                    colors = if (actionType == "uninstall") ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Text("Execute")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { appPendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Debloat Hero Info Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Debloat Assistant",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Clean OEM ads, telemetry & unwanted system packages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { refreshList() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh list")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${debloatApps.size} apps detected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (privilegeState is PrivilegeState.Ready) "ADB / Root Active" else "Privilege Disconnected",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (privilegeState is PrivilegeState.Ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Search & Category Tabs
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search bloatware or package...", fontSize = 13.sp) },
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
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEachIndexed { index, pair ->
                        FilterChip(
                            selected = selectedCategoryIndex == index,
                            onClick = { selectedCategoryIndex = index },
                            label = { Text(pair.second, fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // List
        if (isLoading) {
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
        } else if (filteredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No packages matching '$searchQuery'" else "No bloatware found in this category.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredList, key = { it.packageName }) { app ->
                val isBusy = actionInProgressPackage == app.packageName
                val iconBitmap = remember(app.packageName) {
                    try {
                        debloatManager.getAppIcon(app.packageName)?.toBitmap(width = 96, height = 96)?.asImageBitmap()
                    } catch (_: Throwable) {
                        null
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!app.isEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Android,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (app.safetyLevel == DebloatSafety.CAUTION) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    "Caution",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (app.description.isNotBlank()) {
                                        Text(
                                            text = app.description,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // State Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (app.isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = if (app.isEnabled) "Enabled" else "Disabled",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (app.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Quick Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Executing...", fontSize = 11.sp)
                            } else {
                                if (app.isEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!privilegeManager.isReady()) {
                                                Toast.makeText(context, "Connect Wireless ADB or Root first", Toast.LENGTH_SHORT).show()
                                                return@OutlinedButton
                                            }
                                            appPendingAction = app to "disable"
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Freeze", fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            if (!privilegeManager.isReady()) {
                                                Toast.makeText(context, "Connect Wireless ADB or Root first", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            appPendingAction = app to "enable"
                                        },
                                        modifier = Modifier.height(30.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Unfreeze", fontSize = 11.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!privilegeManager.isReady()) {
                                            Toast.makeText(context, "Connect Wireless ADB or Root first", Toast.LENGTH_SHORT).show()
                                            return@OutlinedButton
                                        }
                                        appPendingAction = app to "uninstall"
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Uninstall", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!privilegeManager.isReady()) {
                                            Toast.makeText(context, "Connect Wireless ADB or Root first", Toast.LENGTH_SHORT).show()
                                            return@OutlinedButton
                                        }
                                        appPendingAction = app to "reinstall"
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom spacing for floating navigation bar
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
