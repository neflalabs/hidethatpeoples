package com.nefla.hidethatpeoples.ui.components

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nefla.hidethatpeoples.ui.theme.GreenOnline
import com.nefla.hidethatpeoples.ui.theme.OrangeWarning
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
    onDismissRequest: () -> Unit,
    detectedPort: Int?,
    onPair: suspend (code: String, port: Int?) -> Result<Unit>
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var pairingCode by remember { mutableStateOf("") }
    var customPortText by remember { mutableStateOf(detectedPort?.toString() ?: "") }
    var isManualPortEnabled by remember { mutableStateOf(false) }
    var isPairing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detectedPort) {
        if (detectedPort != null && customPortText.isEmpty()) {
            customPortText = detectedPort.toString()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wireless ADB Pairing",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "100% Standalone & Non-Root",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // Instructions Step Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StepItem(
                        number = "1",
                        text = "Hubungkan ponsel ke jaringan Wi-Fi atau aktifkan Hotspot pribadi."
                    )
                    StepItem(
                        number = "2",
                        text = "Buka Developer Options -> Wireless Debugging -> ketuk 'Pair device with pairing code'."
                    )
                    StepItem(
                        number = "3",
                        text = "Masukkan 6 angka pairing code ke dalam kotak di bawah."
                    )
                }
            }

            // Quick Open Developer Options Button
            OutlinedButton(
                onClick = { openWirelessDebuggingSettings(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buka Wireless Debugging Settings")
            }

            // Port Status Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (detectedPort != null && !isManualPortEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GreenOnline)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Port Terdeteksi: $detectedPort",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenOnline,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { isManualPortEnabled = true }) {
                        Text("Ubah Port", fontSize = 12.sp)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(OrangeWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isManualPortEnabled) "Input Port Manual" else "Mencari port via mDNS...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isManualPortEnabled) {
                        TextButton(onClick = { isManualPortEnabled = true }) {
                            Text("Input Manual", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Manual Port Field if enabled
            AnimatedVisibility(visible = isManualPortEnabled) {
                OutlinedTextField(
                    value = customPortText,
                    onValueChange = { customPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Port Pairing (5 digit)") },
                    placeholder = { Text("Contoh: 39481") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // 6-Digit Pairing Code Input Field
            OutlinedTextField(
                value = pairingCode,
                onValueChange = {
                    if (it.length <= 6) pairingCode = it.filter { ch -> ch.isDigit() }
                },
                label = { Text("Pairing Code (6 Digit)") },
                placeholder = { Text("000000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (pairingCode.length == 6 && !isPairing) {
                            scope.launch {
                                executePair(
                                    code = pairingCode,
                                    port = customPortText.toIntOrNull() ?: detectedPort,
                                    onPair = onPair,
                                    setPairing = { isPairing = it },
                                    setError = { errorMessage = it },
                                    onSuccess = onDismissRequest
                                )
                            }
                        }
                    }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )

            // Error Display
            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Pair & Connect Button
            Button(
                onClick = {
                    scope.launch {
                        executePair(
                            code = pairingCode,
                            port = customPortText.toIntOrNull() ?: detectedPort,
                            onPair = onPair,
                            setPairing = { isPairing = it },
                            setError = { errorMessage = it },
                            onSuccess = onDismissRequest
                        )
                    }
                },
                enabled = pairingCode.length == 6 && !isPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memproses Pairing...")
                } else {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pair & Hubungkan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private suspend fun executePair(
    code: String,
    port: Int?,
    onPair: suspend (String, Int?) -> Result<Unit>,
    setPairing: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    onSuccess: () -> Unit
) {
    if (port == null || port <= 0) {
        setError("Port belum terdeteksi. Silakan buka dialog 'Pair device with pairing code' atau isi port manual.")
        return
    }

    setError(null)
    setPairing(true)
    val result = onPair(code, port)
    setPairing(false)

    if (result.isSuccess) {
        onSuccess()
    } else {
        setError(result.exceptionOrNull()?.message ?: "Gagal melakukan pairing. Periksa kembali kodenya.")
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun openWirelessDebuggingSettings(context: Context) {
    val intents = listOf(
        Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"),
        Intent("android.service.quicksettings.action.QS_TILE_PREFERENCES").apply {
            putExtra(
                Intent.EXTRA_COMPONENT_NAME,
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.development.qstile.DevelopmentTiles\$WirelessDebugging"
                )
            )
        },
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {}
    }
}
