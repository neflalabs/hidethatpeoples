package com.nefla.hidethatpeoples.privilege.shizuku

import com.nefla.hidethatpeoples.privilege.PrivilegeProvider
import com.nefla.hidethatpeoples.privilege.PrivilegeState
import com.nefla.hidethatpeoples.privilege.PrivilegeType
import com.nefla.hidethatpeoples.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuPrivilegeProvider : PrivilegeProvider {
    override val type: PrivilegeType = PrivilegeType.SHIZUKU

    private val _state = MutableStateFlow<PrivilegeState>(PrivilegeState.Disconnected)
    override val state: StateFlow<PrivilegeState> = _state.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateState()
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            updateState()
        }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {
            // In case Shizuku provider is not accessible yet
        }
        updateState()
    }

    fun updateState() {
        _state.value = when (ShizukuManager.getShizukuState()) {
            ShizukuManager.ShizukuState.READY -> PrivilegeState.Ready(PrivilegeType.SHIZUKU, "Shizuku IPC Active")
            ShizukuManager.ShizukuState.PERMISSION_REQUIRED -> PrivilegeState.PairingRequired(null)
            ShizukuManager.ShizukuState.NOT_RUNNING -> PrivilegeState.Disconnected
        }
    }

    fun requestPermission(requestCode: Int = 2001) {
        ShizukuManager.requestPermission(requestCode)
    }

    override suspend fun clearShortcuts(packageName: String): Result<String> {
        return ShizukuManager.clearShortcuts(packageName)
    }

    override suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> {
        return ShizukuManager.clearMultipleShortcuts(packages)
    }

    override fun release() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Throwable) {}
    }
}
