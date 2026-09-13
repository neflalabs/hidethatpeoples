package com.neflalabs.hidethatpeoples.privilege

sealed interface PrivilegeState {
    data object Disconnected : PrivilegeState
    data class PairingRequired(
        val detectedPort: Int? = null,
        val hostIp: String = "127.0.0.1"
    ) : PrivilegeState
    data class ConnectPortRequired(
        val lastPort: Int? = null,
        val message: String? = null
    ) : PrivilegeState
    data object Connecting : PrivilegeState
    data class Ready(
        val type: PrivilegeType,
        val details: String
    ) : PrivilegeState
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : PrivilegeState
}
