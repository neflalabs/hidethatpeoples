package com.nefla.hidethatpeoples.tile

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.nefla.hidethatpeoples.MainActivity
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClearShortcutsTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val privilegeManager by lazy { PrivilegeManager.getInstance(applicationContext) }
    private var stateObserverJob: kotlinx.coroutines.Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
        stateObserverJob?.cancel()
        stateObserverJob = scope.launch {
            privilegeManager.activeState.collect {
                updateTileState()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateObserverJob?.cancel()
        stateObserverJob = null
    }

    private fun updateTileState(subtitle: String? = null) {
        val tile = qsTile ?: return
        val state = privilegeManager.activeState.value

        if (state is PrivilegeState.Ready) {
            tile.state = Tile.STATE_INACTIVE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = subtitle ?: "Tap to clear"
            }
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = when (state) {
                    is PrivilegeState.PairingRequired -> "Pairing required"
                    is PrivilegeState.Connecting -> "Connecting..."
                    else -> "Setup required"
                }
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = AppPreferences(applicationContext)

        scope.launch {
            val tile = qsTile
            var isReady = privilegeManager.isReady()

            // If not immediately ready, check if device was previously paired
            if (!isReady && prefs.isAdbPaired) {
                if (tile != null) {
                    tile.state = Tile.STATE_ACTIVE
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        tile.subtitle = "Connecting..."
                    }
                    tile.updateTile()
                }

                privilegeManager.reconnect()
                val timeoutMs = 4000L
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (privilegeManager.isReady()) {
                        isReady = true
                        break
                    }
                    kotlinx.coroutines.delay(200)
                }
            }

            if (!isReady) {
                val msg = if (prefs.isAdbPaired) {
                    "Wireless ADB connection timed out. Open app to reconnect."
                } else {
                    "Wireless ADB setup required!"
                }
                Toast.makeText(this@ClearShortcutsTileService, msg, Toast.LENGTH_SHORT).show()
                try {
                    val appIntent = Intent(this@ClearShortcutsTileService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivityAndCollapse(appIntent)
                } catch (_: Throwable) {}
                updateTileState()
                return@launch
            }

            if (tile != null) {
                tile.state = Tile.STATE_ACTIVE
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    tile.subtitle = "Clearing..."
                }
                tile.updateTile()
            }

            val targets = prefs.enabledPackages
            val results = withContext(Dispatchers.IO) {
                privilegeManager.clearMultipleShortcuts(targets)
            }

            prefs.lastClearedTimestamp = System.currentTimeMillis()
            val successCount = results.values.count { it }

            Toast.makeText(
                this@ClearShortcutsTileService,
                "Cleared contacts for $successCount app(s)",
                Toast.LENGTH_SHORT
            ).show()

            if (tile != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = "Cleared!"
                tile.updateTile()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                updateTileState()
            }, 1500)
        }
    }
}
