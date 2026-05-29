/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.xiboard.data.db.ClipboardHelper
import com.xiboard.data.db.CollectionHelper
import com.xiboard.data.prefs.AppPrefs
import com.xiboard.data.theme.ColorManager
import com.xiboard.data.theme.ThemePrefs
import com.xiboard.receiver.RimeIntentReceiver
import com.xiboard.ui.main.LogActivity
import com.xiboard.util.isNightMode
import com.xiboard.worker.BackgroundSyncWork
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * Custom Application class.
 * Application class will only be created once when the app run,
 * so you can init a "global" class here, whose methods serve other
 * classes everywhere.
 */
class TrimeApplication : Application() {
    val coroutineScope = MainScope() + CoroutineName("TrimeApplication")

    private val rimeIntentReceiver = RimeIntentReceiver()

    private fun registerBroadcastReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            PERMISSION_TEST_INPUT_METHOD,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.DEBUG) {
            val defaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, e ->
                val crashTime = System.currentTimeMillis()
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                val lastCrashTimePrefKey = "last_crash_time"
                val lastCrashTime = sharedPrefs.getLong(lastCrashTimePrefKey, -1L)
                sharedPrefs.edit(commit = true) {
                    putLong(lastCrashTimePrefKey, crashTime)
                }
                if (crashTime - lastCrashTime <= 10_000L) {
                    defaultCrashHandler?.uncaughtException(thread, e) ?: exitProcess(10)
                }
                startActivity(
                    Intent(applicationContext, LogActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(LogActivity.FROM_CRASH, true)
                        // avoid transaction overflow
                        val truncated =
                            e.stackTraceToString().let {
                                if (it.length > MAX_STACKTRACE_SIZE) {
                                    it.take(MAX_STACKTRACE_SIZE) + "<truncated>"
                                } else {
                                    it
                                }
                            }
                        putExtra(LogActivity.CRASH_STACK_TRACE, truncated)
                    },
                )
                defaultCrashHandler?.uncaughtException(thread, e) ?: exitProcess(10)
            }
        }
        instance = this
        try {
            if (BuildConfig.DEBUG) {
                Timber.plant(
                    object : Timber.DebugTree() {
                        override fun createStackElementTag(element: StackTraceElement): String = "${super.createStackElementTag(element)}|${element.fileName}:${element.lineNumber}"

                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            super.log(
                                priority,
                                "[${Thread.currentThread().name}] ${tag?.substringBefore('|')}",
                                "${tag?.substringAfter('|')}] $message",
                                t,
                            )
                        }
                    },
                )
            } else {
                Timber.plant(
                    object : Timber.Tree() {
                        override fun log(
                            priority: Int,
                            tag: String?,
                            message: String,
                            t: Throwable?,
                        ) {
                            if (priority < Log.INFO) return
                            Log.println(priority, "[${Thread.currentThread().name}]", message)
                        }
                    },
                )
            }
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val appPrefs = AppPrefs.initDefault(sharedPreferences)
            migrateXiboardDefaults(sharedPreferences)
            // record last pid for crash logs
            appPrefs.internal.pid.apply {
                val currentPid = Process.myPid()
                lastPid = getValue()
                Timber.d("Last pid is $lastPid. Set it to current pid: $currentPid")
                setValue(currentPid)
            }
            ClipboardHelper.init(applicationContext)
            CollectionHelper.init(applicationContext)
            registerBroadcastReceiver()
            startWorkManager()
        } catch (e: Exception) {
            e.fillInStackTrace()
            return
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            ColorManager.onSystemNightModeChange(newConfig.isNightMode())
        } catch (e: Exception) {
            Timber.w(e, "Something wrong on configuration changed")
        }
    }

    private fun startWorkManager() {
        coroutineScope.launch {
            BackgroundSyncWork.start(applicationContext)
        }
    }

    private fun migrateXiboardDefaults(sharedPreferences: android.content.SharedPreferences) {
        val marker = "xiboard_default_theme_migration_v1"
        if (!sharedPreferences.getBoolean(marker, false)) {
            val selectedTheme = sharedPreferences.getString(ThemePrefs.SELECTED_THEME, null)
            val shouldMoveToTongwenfeng =
                selectedTheme.isNullOrBlank() ||
                    selectedTheme == "trime" ||
                    selectedTheme == "default" ||
                    selectedTheme == "tongwenfeng"

            sharedPreferences.edit {
                if (shouldMoveToTongwenfeng) {
                    putString(ThemePrefs.SELECTED_THEME, ThemePrefs.XIBOARD_DEFAULT_THEME)
                    putString(ThemePrefs.NORMAL_MODE_COLOR, ThemePrefs.XIBOARD_DEFAULT_COLOR)
                }
                putBoolean(marker, true)
            }
        }

        val dayNightMarker = "xiboard_follow_system_day_night_migration_v1"
        if (!sharedPreferences.getBoolean(dayNightMarker, false)) {
            sharedPreferences.edit {
                if (!sharedPreferences.contains(ThemePrefs.FOLLOW_SYSTEM_DAY_NIGHT)) {
                    putBoolean(ThemePrefs.FOLLOW_SYSTEM_DAY_NIGHT, true)
                }
                putBoolean(dayNightMarker, true)
            }
        }
    }

    companion object {
        private var instance: TrimeApplication? = null
        private var lastPid: Int? = null

        fun getInstance() = instance ?: throw IllegalStateException("Trime application is not created!")

        fun getLastPid() = lastPid

        private const val MAX_STACKTRACE_SIZE = 128000

        /**
         * This permission is requested by com.android.shell, makes it possible to start
         * deploy from `adb shell am` command:
         * ```sh
         * adb shell am broadcast -a com.xiboard.action.DEPLOY
         * ```
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-7.0.0_r1/packages/Shell/AndroidManifest.xml#67
         *
         * other candidate: android.permission.TEST_INPUT_METHOD requires Android 14
         * https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r1/packages/Shell/AndroidManifest.xml#628
         */
        const val PERMISSION_TEST_INPUT_METHOD = "android.permission.READ_INPUT_STATE"
    }
}
