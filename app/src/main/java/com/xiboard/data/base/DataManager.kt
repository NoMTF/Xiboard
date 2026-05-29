// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.xiboard.data.base

import android.content.res.AssetManager
import android.os.Build
import com.xiboard.data.prefs.AppPrefs
import com.xiboard.util.FileUtils
import com.xiboard.util.ResourceUtils
import com.xiboard.util.appContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DataManager {
    private const val DEFAULT_CUSTOM_FILE_NAME = "default.custom.yaml"

    private const val DATA_CHECKSUMS_NAME = "checksums.json"
    private const val XIBOARD_RIME_MIGRATION_NAME = "xiboard-rime-migration.txt"
    private const val XIBOARD_RIME_MIGRATION_VERSION = "rime-ice-mobile-v6"

    private const val SCHEMA_LIST_CUSTOM_PATCH = """
      patch:
        "schema_list/@0":
          schema: rime_ice
        "schema_list/@1":
        "schema_list/@2":
        "schema_list/@3":
        "schema_list/@4":
        "schema_list/@5":
        "schema_list/@6":
        "schema_list/@7":
    """

    private val lock = ReentrantLock()

    private val json by lazy { Json }

    private fun deserializeDataChecksums(raw: String): DataChecksums = json.decodeFromString<DataChecksums>(raw)

    private fun serializeDataChecksums(checksums: DataChecksums): String = json.encodeToString(checksums)

    // If Android version supports direct boot, we put the hierarchy in device encrypted storage
    // instead of credential encrypted storage so that data can be accessed before user unlock
    private val dataDir: File =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Timber.d("Using device protected storage")
            appContext.createDeviceProtectedStorageContext().dataDir
        } else {
            File(appContext.applicationInfo.dataDir)
        }

    private fun AssetManager.dataChecksums(): DataChecksums = open(DATA_CHECKSUMS_NAME)
        .bufferedReader()
        .use { it.readText() }
        .let { deserializeDataChecksums(it) }

    private val prefs by lazy { AppPrefs.defaultInstance() }

    private val privateExternalDir: File = appContext.getExternalFilesDir(null) ?: dataDir

    val defaultDataDir = File(privateExternalDir, "rime")

    val sharedDataDir = File(privateExternalDir, "shared").also { it.mkdirs() }

    val userDataDir
        get() = File(prefs.profile.userDataDir.getValue()).also { it.mkdirs() }

    val prebuiltDataDir = File(sharedDataDir, "build")
    val stagingDir get() = File(userDataDir, "build")

    /**
     * Return the absolute path of the compiled config file
     * based on given resource id.
     *
     * @param resourceId usually equals the config file name without the extension
     * @return the absolute path of the compiled config file
     */
    @JvmStatic
    fun resolveDeployedResourcePath(resourceId: String): String {
        val defaultPath = File(stagingDir, "$resourceId.yaml")
        if (!defaultPath.exists()) {
            val fallbackPath = File(prebuiltDataDir, "$resourceId.yaml")
            if (fallbackPath.exists()) return fallbackPath.absolutePath
        }
        return defaultPath.absolutePath
    }

    fun sync() = lock.withLock {
        val oldChecksumsFile = File(dataDir, DATA_CHECKSUMS_NAME)
        val oldChecksums =
            oldChecksumsFile
                .runCatching { deserializeDataChecksums(bufferedReader().use { it.readText() }) }
                .getOrElse { DataChecksums("", emptyMap()) }

        val newChecksums = appContext.assets.dataChecksums().withXiboardVersionStamp()

        DataDiff.diff(oldChecksums, newChecksums).sortedByDescending { it.ordinal }.forEach {
            Timber.d("Diff: $it")
            when (it) {
                is DataDiff.CreateFile,
                is DataDiff.UpdateFile,
                -> {
                    val destPath = sharedDataDir.resolveSibling(it.path).absolutePath
                    ResourceUtils.copyFile(it.path, destPath)
                }
                is DataDiff.DeleteDir,
                is DataDiff.DeleteFile,
                -> FileUtils.delete(sharedDataDir.resolveSibling(it.path)).getOrThrow()
            }
        }

        dataDir.resolve(DATA_CHECKSUMS_NAME).writeText(serializeDataChecksums(newChecksums))

        val custom = userDataDir.resolve(DEFAULT_CUSTOM_FILE_NAME)
        if (!custom.exists()) {
            if (custom.createNewFile()) {
                custom.writeText(SCHEMA_LIST_CUSTOM_PATCH.trimIndent())
            }
        } else if (custom.isXiboardGeneratedSchemaPatch()) {
            custom.writeText(SCHEMA_LIST_CUSTOM_PATCH.trimIndent())
        }

        cleanStaleRimeBuildIfNeeded(oldChecksums, newChecksums)
        mirrorRuntimeSharedFiles()

        Timber.d("Synced!")
    }

    private fun mirrorRuntimeSharedFiles() {
        val lunarDb = sharedDataDir.resolve("lua/lunar.db")
        if (!lunarDb.exists()) return

        val userLunarDb = userDataDir.resolve("lua/lunar.db")
        if (userLunarDb.exists() && userLunarDb.length() == lunarDb.length()) return

        userLunarDb.parentFile?.mkdirs()
        lunarDb.copyTo(userLunarDb, overwrite = true)
    }

    private fun cleanStaleRimeBuildIfNeeded(
        oldChecksums: DataChecksums,
        newChecksums: DataChecksums,
    ) {
        val marker = dataDir.resolve(XIBOARD_RIME_MIGRATION_NAME)
        val buildDir = userDataDir.resolve("build")
        val hasStaleBuild =
            buildDir
                .listFiles()
                ?.any {
                    it.name.startsWith("luna_pinyin") ||
                        it.name.startsWith("stroke") ||
                        it.name.startsWith("melt_eng")
                } == true
        val needsClean =
            marker.runCatching { readText() }.getOrNull() != XIBOARD_RIME_MIGRATION_VERSION ||
                oldChecksums.sha256 != newChecksums.sha256 ||
                hasStaleBuild

        if (!needsClean) return

        FileUtils.delete(buildDir).getOrThrow()
        marker.writeText(XIBOARD_RIME_MIGRATION_VERSION)
        Timber.i("Cleared stale input build cache for Xiboard mobile schema")
    }

    private fun File.isXiboardGeneratedSchemaPatch(): Boolean = runCatching {
        val text = readText()
        text.contains("\"schema_list/@0\"") &&
            (text.contains("luna_pinyin") || text.contains("schema: rime_ice"))
    }.getOrDefault(false)

    private fun DataChecksums.withXiboardVersionStamp(): DataChecksums {
        val marker = runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .longVersionCodeCompat()
                .toString()
        }.getOrDefault("dev")
        return copy(sha256 = sha256("$sha256:$marker"))
    }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        @Suppress("DEPRECATION")
        versionCode.toLong()
    }

    private fun sha256(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
