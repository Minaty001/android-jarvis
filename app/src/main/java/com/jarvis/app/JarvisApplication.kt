package com.jarvis.app

import android.app.Application
import android.util.Log
import com.jarvis.storage.JarvisStorageHub
import com.jarvis.ui.reliability.GlobalErrorHandler

class JarvisApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GlobalErrorHandler.install(this)
        JarvisStorageHub.initStorage(this)
        Log.i("JarvisApp", "JarvisApplication initialized")
    }
}
