package com.nefla.hidethatpeoples

import android.app.Application
import com.nefla.hidethatpeoples.data.AppPreferences
import com.nefla.hidethatpeoples.worker.AutoCleanWorker

class HideThatPeoplesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = AppPreferences(this)
        if (prefs.isAutoCleanEnabled) {
            AutoCleanWorker.schedule(this, prefs.autoCleanIntervalMinutes)
        }
    }
}
