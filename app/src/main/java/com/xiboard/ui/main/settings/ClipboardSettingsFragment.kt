/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ui.main.settings

import com.xiboard.data.prefs.AppPrefs
import com.xiboard.data.prefs.PreferenceDelegateFragment

class ClipboardSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().clipboard)
