package com.neflalabs.hidethatpeoples.privilege

import android.content.Context
import com.neflalabs.hidethatpeoples.data.AppPreferences
import com.neflalabs.hidethatpeoples.privilege.adb.LocalAdbPrivilegeProvider
import com.neflalabs.hidethatpeoples.privilege.root.RootPrivilegeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrivilegeManager private constructor(private val context: Context) {

    private val prefs = AppPreferences(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    val localAdbProvider = LocalAdbPrivilegeProvider(context)
    val rootProvider = RootPrivilegeProvider()

    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable

    init {
        scope.launch {
            val rootOk = rootProvider.checkRootAvailability()
            _isRootAvailable.value = rootOk
        }
    }

    // Combine states based on preferred provider or availability
    val activeState: StateFlow<PrivilegeState> = combine(
        localAdbProvider.state,
        rootProvider.state
    ) { adbState, rootState ->
        val pref = prefs.preferredPrivilegeType
        when {
            pref == "ROOT" && rootState is PrivilegeState.Ready -> rootState
            pref == "LOCAL_ADB" -> adbState
            // AUTO mode:
            rootState is PrivilegeState.Ready && adbState !is PrivilegeState.Ready -> rootState
            adbState is PrivilegeState.Ready -> adbState
            rootState is PrivilegeState.Ready -> rootState
            else -> adbState
        }
    }.stateIn(scope, SharingStarted.Eagerly, PrivilegeState.Disconnected)

    fun isReady(): Boolean = activeState.value is PrivilegeState.Ready

    fun getActiveProvider(): PrivilegeProvider {
        val state = activeState.value
        return if (state is PrivilegeState.Ready && state.type == PrivilegeType.ROOT) {
            rootProvider
        } else {
            localAdbProvider
        }
    }

    suspend fun clearShortcuts(packageName: String): Result<String> {
        return getActiveProvider().clearShortcuts(packageName)
    }

    suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> {
        return getActiveProvider().clearMultipleShortcuts(packages)
    }

    suspend fun executeCommand(command: String): Result<String> {
        return getActiveProvider().executeCommand(command)
    }

    suspend fun disableApp(packageName: String): Result<String> {
        return getActiveProvider().disableApp(packageName)
    }

    suspend fun enableApp(packageName: String): Result<String> {
        return getActiveProvider().enableApp(packageName)
    }

    suspend fun uninstallUser0(packageName: String): Result<String> {
        return getActiveProvider().uninstallUser0(packageName)
    }

    suspend fun reinstallUser0(packageName: String): Result<String> {
        return getActiveProvider().reinstallUser0(packageName)
    }

    suspend fun pairLocalAdb(code: String, port: Int? = null): Result<Unit> {
        return localAdbProvider.pair(code, port)
    }

    fun reconnect(targetPort: Int? = null) {
        scope.launch {
            val rootOk = rootProvider.checkRootAvailability()
            _isRootAvailable.value = rootOk
        }
        localAdbProvider.startConnectPortDiscoveryAndConnect(targetPort)
    }

    fun cancelConnecting() {
        localAdbProvider.cancelConnecting()
    }

    fun disconnect() {
        localAdbProvider.disconnectAdb()
    }

    fun release() {
        localAdbProvider.release()
        rootProvider.release()
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
