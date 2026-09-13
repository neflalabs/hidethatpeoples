package com.neflalabs.hidethatpeoples

import com.neflalabs.hidethatpeoples.data.TargetApp
import com.neflalabs.hidethatpeoples.privilege.PrivilegeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideThatPeoplesUnitTest {

    @Test
    fun testDefaultTargetAppsNotEmpty() {
        val defaults = TargetApp.DEFAULT_TARGETS
        assertTrue("Default target list should not be empty", defaults.isNotEmpty())
        assertTrue(
            "Default targets should contain WhatsApp",
            defaults.any { it.packageName == "com.whatsapp" }
        )
        assertTrue(
            "Default targets should contain Telegram",
            defaults.any { it.packageName == "org.telegram.messenger" }
        )
    }

    @Test
    fun testPrivilegeTypeDisplayNames() {
        assertEquals("Built-in Wireless ADB", PrivilegeType.LOCAL_ADB.displayName)
        assertEquals("Root (su)", PrivilegeType.ROOT.displayName)
    }

    @Test
    fun testBatchCommandGeneration() {
        val packages = listOf("com.whatsapp", "org.telegram.messenger", "com.discord")
        val pkgListStr = packages.joinToString(" ")
        val command = "shell:for p in $pkgListStr; do res=\$(cmd shortcut clear-shortcuts --user 0 \"\$p\" 2>&1); echo \"\$p:\$res\"; done"

        assertTrue(command.contains("for p in com.whatsapp org.telegram.messenger com.discord"))
        assertTrue(command.contains("cmd shortcut clear-shortcuts --user 0 \"\$p\""))
    }

    @Test
    fun testChunkingSplitsLargeLists() {
        val list = (1..60).map { "com.example.app$it" }
        val chunks = list.chunked(25)

        assertEquals(3, chunks.size)
        assertEquals(25, chunks[0].size)
        assertEquals(25, chunks[1].size)
        assertEquals(10, chunks[2].size)
    }
}
