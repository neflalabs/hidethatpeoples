package com.nefla.hidethatpeoples.privilege

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

    fun release() {}
}
