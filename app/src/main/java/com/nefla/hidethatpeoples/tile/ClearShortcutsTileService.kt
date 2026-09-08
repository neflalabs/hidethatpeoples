package com.nefla.hidethatpeoples.tile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.shizuku.ShizukuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClearShortcutsTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState(subtitle: String? = null) {
        val tile = qsTile ?: return
        if (ShizukuManager.isReady()) {
            tile.state = Tile.STATE_INACTIVE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = subtitle ?: "Tap to clear"
            }
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = "Shizuku required"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (!ShizukuManager.isReady()) {
            Toast.makeText(this, "Shizuku is not running or permission denied!", Toast.LENGTH_SHORT).show()
            updateTileState()
            return
        }

        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_ACTIVE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = "Clearing..."
            }
            tile.updateTile()
        }

        scope.launch {
            val prefs = AppPreferences(applicationContext)
            val targets = prefs.enabledPackages

            val results = withContext(Dispatchers.IO) {
                ShizukuManager.clearMultipleShortcuts(targets)
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
