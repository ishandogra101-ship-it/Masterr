package com.masterr.app

import android.app.Application

class MasterrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        Scheduler.schedule(this)
    }
}
