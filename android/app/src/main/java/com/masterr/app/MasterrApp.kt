package com.masterr.app

import android.app.Application

class MasterrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        // Initialize the default FirebaseApp early so the browser sign-in return flow
        // (which may run in a fresh process) always finds it.
        try {
            val cfg = Prefs.readConfig(this)
            if (cfg.firebaseReady) Repo.auth(this, cfg)
        } catch (e: Exception) { }
        Scheduler.schedule(this)
    }
}
