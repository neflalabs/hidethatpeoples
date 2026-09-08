package com.nefla.hidethatpeoples

import android.app.Application
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.worker.AutoCleanWorker
import org.conscrypt.Conscrypt
import java.security.Security

class HideThatPeoplesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (_: Throwable) {}

        val prefs = AppPreferences(this)
        if (prefs.isAutoCleanEnabled) {
            AutoCleanWorker.schedule(this, prefs.autoCleanIntervalMinutes)
        }
    }
}
