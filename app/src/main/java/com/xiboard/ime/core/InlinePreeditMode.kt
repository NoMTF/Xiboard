/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.core

import androidx.annotation.StringRes
import com.xiboard.R
import com.xiboard.data.prefs.PreferenceDelegateEnum

enum class InlinePreeditMode(
    @StringRes override val stringRes: Int,
) : PreferenceDelegateEnum {
    DISABLE(R.string.disable),
    COMPOSING_TEXT(R.string.composing_text),
    COMMIT_TEXT_PREVIEW(R.string.commit_text_preview),
}
