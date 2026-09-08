package com.nefla.hidethatpeoples.privilege

import android.content.Context
import com.nefla.hidethatpeoples.privilege.adb.LocalAdbPrivilegeProvider
import com.nefla.hidethatpeoples.privilege.shizuku.ShizukuPrivilegeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PrivilegeManager private constructor(context: Context) {

    val localAdbProvider = LocalAdbPrivilegeProvider(context)
    val shizukuProvider = ShizukuPrivilegeProvider()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _preferredProviderType = MutableStateFlow(PrivilegeType.LOCAL_ADB)
    val preferredProviderType: StateFlow<PrivilegeType> = _preferredProviderType.asStateFlow()

    // Dynamically emits the active state based on provider priority and readiness
    val activeState: StateFlow<PrivilegeState> = combine(
        localAdbProvider.state,
        shizukuProvider.state,
        _preferredProviderType
    ) { adbState, shizukuState, preferred ->
        when {
            adbState is PrivilegeState.Ready -> adbState
            shizukuState is PrivilegeState.Ready && preferred == PrivilegeType.SHIZUKU -> shizukuState
            adbState is PrivilegeState.PairingRequired -> adbState
            adbState is PrivilegeState.Connecting -> adbState
            shizukuState is PrivilegeState.Ready -> shizukuState
            else -> adbState
        }
    }.stateIn(scope, SharingStarted.Eagerly, PrivilegeState.Disconnected)

    fun isReady(): Boolean = activeState.value is PrivilegeState.Ready

    fun getActiveProvider(): PrivilegeProvider {
        return when (val state = activeState.value) {
            is PrivilegeState.Ready -> {
                if (state.type == PrivilegeType.SHIZUKU) shizukuProvider else localAdbProvider
            }
            else -> {
                if (_preferredProviderType.value == PrivilegeType.SHIZUKU) shizukuProvider else localAdbProvider
            }
        }
    }

    suspend fun clearShortcuts(packageName: String): Result<String> {
        val provider = getActiveProvider()
        return provider.clearShortcuts(packageName)
    }

    suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> {
        val provider = getActiveProvider()
        return provider.clearMultipleShortcuts(packages)
    }

    suspend fun pairLocalAdb(code: String, port: Int? = null): Result<Unit> {
        return localAdbProvider.pair(code, port)
    }

    fun setPreferredProvider(type: PrivilegeType) {
        _preferredProviderType.value = type
    }

    fun release() {
        localAdbProvider.release()
        shizukuProvider.release()
    }

    companion object {
        @Volatile
        private var INSTANCE: PrivilegeManager? = null

        fun getInstance(context: Context): PrivilegeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrivilegeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
