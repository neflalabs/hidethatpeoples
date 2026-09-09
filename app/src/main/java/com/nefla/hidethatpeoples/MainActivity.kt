package com.nefla.hidethatpeoples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nefla.hidethatpeoples.privilege.PrivilegeManager
import com.nefla.hidethatpeoples.ui.HomeScreen
import com.nefla.hidethatpeoples.ui.theme.HideThatPeoplesTheme

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
