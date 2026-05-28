/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.broadcast

import android.view.inputmethod.EditorInfo
import com.xiboard.core.CompositionProto
import com.xiboard.core.MenuProto
import com.xiboard.core.RimeMessage
import com.xiboard.core.SchemaItem
import com.xiboard.core.StatusProto
import com.xiboard.ime.window.BoardWindow

interface InputBroadcastReceiver {
    fun onStartInput(info: EditorInfo) {}

    fun onSelectionUpdate(start: Int, end: Int) {}

    fun onRimeSchemaUpdated(schema: SchemaItem) {}

    fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {}

    fun onCandidateListUpdate(data: RimeMessage.CandidateListMessage.Data) {}

    fun onCompositionUpdate(data: CompositionProto) {}

    fun onCandidateMenuUpdate(data: MenuProto) {}

    fun onKeyAppearanceUpdate(composing: Boolean, menu: Boolean, paging: Boolean) {}

    fun onInputStatusUpdate(value: StatusProto) {}

    fun onWindowAttached(window: BoardWindow) {}

    fun onWindowDetached(window: BoardWindow) {}

    fun onEnterKeyLabelUpdate(label: String) {}
}
