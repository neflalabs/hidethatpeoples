package com.nefla.hidethatpeoples.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectPortDialog(
    initialPort: Int? = null,
    onDismissRequest: () -> Unit,
    onConnect: (port: Int) -> Unit
) {
    val context = LocalContext.current
    var portText by remember {
        mutableStateOf(if (initialPort != null && initialPort > 0) initialPort.toString() else "")
    }

    val parsedPort = portText.toIntOrNull()
    val isPortValid = parsedPort != null && parsedPort in 1024..65535

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.WifiTethering,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Manual Wireless ADB Port",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "If auto-discovery failed, check the active port in Developer Options -> Wireless Debugging -> 'IP address & Port' (e.g. 192.168.1.15:43829).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { openWirelessDebuggingSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Wireless Debugging Settings", fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = portText,
                    onValueChange = { input ->
                        portText = input.filter { it.isDigit() }.take(5)
                    },
                    label = { Text("Active Port (e.g. 43829)") },
                    placeholder = { Text("43829") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isPortValid) {
                                onConnect(parsedPort!!)
                                onDismissRequest()
                            }
                        }
                    ),
                    isError = portText.isNotBlank() && !isPortValid,
                    supportingText = {
                        if (portText.isNotBlank() && !isPortValid) {
                            Text("Port must be between 1024 and 65535", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPortValid) {
                        onConnect(parsedPort!!)
                        onDismissRequest()
                    }
                },
                enabled = isPortValid
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
