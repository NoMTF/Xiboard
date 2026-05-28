// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.xiboard.ime.dialog

import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import com.xiboard.R
import com.xiboard.core.RimeApi

object EnabledSchemaPickerDialog {
    suspend fun build(
        @Suppress("UNUSED_PARAMETER") rime: RimeApi,
        @Suppress("UNUSED_PARAMETER") scope: LifecycleCoroutineScope,
        context: Context,
        extensions: (AlertDialog.Builder.() -> AlertDialog.Builder)? = null,
    ): AlertDialog {
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.select_current_schema)
                setMessage(R.string.no_schema_to_select)
                extensions?.invoke(this)
            }.create()
    }
}
