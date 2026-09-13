package com.neflalabs.hidethatpeoples.privilege

import kotlinx.coroutines.flow.StateFlow

interface PrivilegeProvider {
    val type: PrivilegeType
    val state: StateFlow<PrivilegeState>

    fun isReady(): Boolean = state.value is PrivilegeState.Ready

    suspend fun clearShortcuts(packageName: String): Result<String>

    suspend fun clearMultipleShortcuts(packages: Collection<String>): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        for (pkg in packages) {
            val result = clearShortcuts(pkg)
            results[pkg] = result.isSuccess
        }
        return results
    }

    suspend fun executeCommand(command: String): Result<String>

    suspend fun disableApp(packageName: String): Result<String> {
        return executeCommand("pm disable-user --user 0 $packageName")
    }

    suspend fun enableApp(packageName: String): Result<String> {
        return executeCommand("pm enable $packageName")
    }

    suspend fun uninstallUser0(packageName: String): Result<String> {
        return executeCommand("pm uninstall -k --user 0 $packageName")
    }

    suspend fun reinstallUser0(packageName: String): Result<String> {
        return executeCommand("cmd package install-existing $packageName")
    }

    fun release() {}
}
