package com.nefla.hidethatpeoples

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nefla.hidethatpeoples.shizuku.ShizukuManager
import com.nefla.hidethatpeoples.ui.HomeScreen
import com.nefla.hidethatpeoples.ui.theme.HideThatPeoplesTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuState by mutableStateOf(ShizukuManager.ShizukuState.NOT_RUNNING)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuState()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                updateShizukuState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        updateShizukuState()

        setContent {
            HideThatPeoplesTheme {
                HomeScreen(
                    shizukuState = shizukuState,
                    onRequestShizukuPermission = {
                        ShizukuManager.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                    },
                    onRefreshState = {
                        updateShizukuState()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun updateShizukuState() {
        shizukuState = ShizukuManager.getShizukuState()
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 2001
    }
}
