/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.util

import android.content.Context
import com.xiboard.R
import com.xiboard.data.base.DataManager

@Suppress("NOTHING_TO_INLINE")
inline fun Context.isStorageAvailable(): Boolean = runCatching {
    DataManager.defaultDataDir.mkdirs()
    DataManager.defaultDataDir.canWrite()
}.getOrDefault(false)

fun Context.prepareLocalStorage() {
    if (isStorageAvailable()) {
        toast(R.string.local_storage_available)
    } else {
        toast(R.string.local_storage_not_available)
    }
}
