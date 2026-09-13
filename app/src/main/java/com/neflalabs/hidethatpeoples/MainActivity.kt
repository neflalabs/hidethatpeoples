package com.neflalabs.hidethatpeoples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neflalabs.hidethatpeoples.privilege.PrivilegeManager
import com.neflalabs.hidethatpeoples.ui.HomeScreen
import com.neflalabs.hidethatpeoples.ui.theme.HideThatPeoplesTheme

class MainActivity : ComponentActivity() {

    private val privilegeManager by lazy {
        PrivilegeManager.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HideThatPeoplesTheme {
                HomeScreen(
                    privilegeManager = privilegeManager,
                    onRefreshState = {
                        privilegeManager.reconnect()
                    }
                )
            }
        }
    }
}
