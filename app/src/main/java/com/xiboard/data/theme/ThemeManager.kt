/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.data.theme

import android.content.res.Configuration
import com.xiboard.core.Rime
import com.xiboard.data.base.DataManager
import com.xiboard.data.prefs.AppPrefs
import com.xiboard.ime.symbol.LiquidData
import com.xiboard.util.WeakHashSet
import com.xiboard.util.appContext
import com.xiboard.util.yaml.Yaml
import com.xiboard.util.yaml.mapping
import timber.log.Timber
import java.io.File

object ThemeManager {
    fun interface OnThemeChangeListener {
        fun onThemeChange(theme: Theme)
    }

    fun getAllThemes(): List<ThemeItem> {
        val sharedThemes = ThemeFilesManager.listThemes(DataManager.sharedDataDir)
        val userThemes = ThemeFilesManager.listThemes(DataManager.userDataDir)
        return sharedThemes + userThemes
    }

    private lateinit var _activeTheme: Theme

    var activeTheme: Theme
        get() = _activeTheme
        private set(value) {
            if (::_activeTheme.isInitialized && _activeTheme == value) return
            _activeTheme = value
            fireChange()
        }

    private val onChangeListeners = WeakHashSet<OnThemeChangeListener>()

    fun addOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.add(listener)
    }

    fun removeOnChangedListener(listener: OnThemeChangeListener) {
        onChangeListeners.remove(listener)
    }

    private fun fireChange() {
        onChangeListeners.forEach { it.onThemeChange(_activeTheme) }
    }

    val prefs = AppPrefs.defaultInstance().registerProvider(::ThemePrefs)

    private data class ResolvedTheme(
        val configId: String,
        val theme: Theme,
    )

    private fun decodeTheme(
        id: String,
        source: String,
        readText: () -> String,
    ): Theme? =
        try {
            val node = Yaml.parseToYamlNode(readText())
            val mapping = node.mapping
            if (mapping == null) {
                Timber.w("Failed to load theme '$id' from $source: YAML root is not a mapping")
                null
            } else {
                Theme.decode(mapping)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load theme '$id' from $source")
            null
        }

    private fun loadThemeByIdOrNull(id: String): Theme? {
        if (!Rime.deployRimeConfigFile(id, "config_version")) {
            Timber.w("Failed to deploy theme config file '$id.yaml'")
        }
        val deployedFile = File(DataManager.resolveDeployedResourcePath(id))
        if (deployedFile.exists()) {
            return decodeTheme(id, deployedFile.absolutePath) { deployedFile.readText() }
        }

        val userFile = File(DataManager.userDataDir, "$id.yaml")
        if (userFile.exists()) {
            return decodeTheme(id, userFile.absolutePath) { userFile.readText() }
        }

        val sharedFile = File(DataManager.sharedDataDir, "$id.yaml")
        if (sharedFile.exists()) {
            return decodeTheme(id, sharedFile.absolutePath) { sharedFile.readText() }
        }

        return decodeTheme(id, "assets/shared/$id.yaml") {
            appContext.assets.open("shared/$id.yaml").bufferedReader().use { it.readText() }
        } ?: run {
            Timber.w("Theme file not found for '$id'")
            null
        }
    }

    private fun getThemeById(id: String): ResolvedTheme {
        loadThemeByIdOrNull(id)?.let { return ResolvedTheme(id, it) }

        if (id != "trime") {
            loadThemeByIdOrNull("trime")?.let {
                Timber.w("Theme '$id' is unavailable, fallback to default theme 'trime'")
                return ResolvedTheme("trime", it)
            }
        }

        for (fallbackId in getAllThemes().map { it.configId }.distinct()) {
            loadThemeByIdOrNull(fallbackId)?.let {
                Timber.w("Theme '$id' is unavailable, fallback to available theme '$fallbackId'")
                return ResolvedTheme(fallbackId, it)
            }
        }

        error("No valid theme available")
    }

    private fun evaluateActiveTheme(): Theme {
        val selectedThemeId = prefs.selectedTheme.getValue()
        val resolvedTheme = getThemeById(selectedThemeId)
        val newTheme = resolvedTheme.theme
        if (resolvedTheme.configId != selectedThemeId) {
            prefs.selectedTheme.setValue(resolvedTheme.configId)
        }
        KeyActionManager.resetCache()
        FontManager.resetCache(newTheme)
        ColorManager.switchTheme(newTheme)
        LiquidData.init(newTheme)
        return newTheme
    }

    fun init(configuration: Configuration) {
        _activeTheme = evaluateActiveTheme()
        ColorManager.init(configuration)
    }

    fun selectTheme(configId: String) {
        val resolvedTheme = getThemeById(configId)
        val theme = resolvedTheme.theme
        KeyActionManager.resetCache()
        FontManager.resetCache(theme)
        ColorManager.switchTheme(theme)
        LiquidData.init(theme)
        activeTheme = theme
        prefs.selectedTheme.setValue(resolvedTheme.configId)
    }
}
