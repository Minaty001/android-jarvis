// SPDX-License-Identifier: MIT
package com.jarvis.util

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import java.util.concurrent.Executor

/**
 * Minimal stub Context implementation for unit tests.
 * Extends ContextWrapper to delegate to a real Context if provided, otherwise provides
 * harmless default implementations for required methods.
 */
class MockContext(base: Context? = null) : ContextWrapper(base) {
    // Use reflection to instantiate AssetManager which has a package‑private constructor.
    private val dummyAssetManager: AssetManager = run {
        val ctor = AssetManager::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        ctor.newInstance()
    }

    // Provide a dummy Resources instance with minimal configuration.
    private val dummyResources: Resources = Resources(dummyAssetManager, DisplayMetrics(), null)

    private val handlerThread = HandlerThread("MockContextThread").apply { start() }
    private val handler = Handler(handlerThread.looper)

    override fun getResources(): Resources = dummyResources
    override fun getAssets(): AssetManager = dummyAssetManager
    override fun getPackageName(): String = "com.jarvis.mock"
    override fun getApplicationContext(): Context = this
    override fun getSystemService(name: String): Any? = null
    override fun getClassLoader(): ClassLoader = javaClass.classLoader
    override fun getMainLooper(): Looper = Looper.getMainLooper()
    override fun getMainExecutor(): Executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences? = null
    // The following methods are optional stubs that may be called in code paths.
    override fun startActivity(intent: android.content.Intent) {}
    override fun startService(service: android.content.Intent): ComponentName? = null
    override fun bindService(service: android.content.Intent, conn: android.content.ServiceConnection, flags: Int) = false
    // No UI related methods are required for headless unit tests.
}
