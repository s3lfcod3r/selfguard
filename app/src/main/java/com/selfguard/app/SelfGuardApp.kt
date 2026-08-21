package com.selfguard.app

import android.app.Application
import com.selfguard.app.data.UpdateWorker

class SelfGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Indikatoren und Sperrlisten täglich auffrischen, sobald WLAN da ist.
        UpdateWorker.schedule(this)
    }
}
