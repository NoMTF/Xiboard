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
import java.util.concurrent.ConcurrentLinkedQueue

class InputBroadcaster : InputBroadcastReceiver {
    private val receivers = ConcurrentLinkedQueue<InputBroadcastReceiver>()

    fun <T> addReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            if (!receivers.contains(receiver)) {
                receivers.add(receiver)
            }
        }
    }

    fun <T> removeReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            receivers.remove(receiver)
        }
    }

    fun clear() {
        receivers.clear()
    }

    override fun onStartInput(info: EditorInfo) {
        receivers.forEach { it.onStartInput(info) }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        receivers.forEach { it.onSelectionUpdate(start, end) }
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        receivers.forEach { it.onRimeSchemaUpdated(schema) }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        receivers.forEach { it.onRimeOptionUpdated(value) }
    }

    override fun onCandidateListUpdate(data: RimeMessage.CandidateListMessage.Data) {
        receivers.forEach { it.onCandidateListUpdate(data) }
    }

    override fun onCompositionUpdate(data: CompositionProto) {
        receivers.forEach { it.onCompositionUpdate(data) }
    }

    override fun onCandidateMenuUpdate(data: MenuProto) {
        receivers.forEach { it.onCandidateMenuUpdate(data) }
    }

    override fun onKeyAppearanceUpdate(composing: Boolean, menu: Boolean, paging: Boolean) {
        receivers.forEach { it.onKeyAppearanceUpdate(composing, menu, paging) }
    }

    override fun onInputStatusUpdate(value: StatusProto) {
        receivers.forEach { it.onInputStatusUpdate(value) }
    }

    override fun onWindowAttached(window: BoardWindow) {
        receivers.forEach { it.onWindowAttached(window) }
    }

    override fun onWindowDetached(window: BoardWindow) {
        receivers.forEach { it.onWindowDetached(window) }
    }

    override fun onEnterKeyLabelUpdate(label: String) {
        receivers.forEach { it.onEnterKeyLabelUpdate(label) }
    }
}
