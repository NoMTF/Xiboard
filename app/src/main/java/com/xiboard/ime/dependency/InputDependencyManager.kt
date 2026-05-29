/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.dependency

import android.content.Context
import com.xiboard.daemon.RimeSession
import com.xiboard.data.theme.Theme
import com.xiboard.ime.bar.InputBarDelegate
import com.xiboard.ime.broadcast.EnterKeyDisplayDelegate
import com.xiboard.ime.broadcast.InputBroadcastReceiver
import com.xiboard.ime.broadcast.InputBroadcaster
import com.xiboard.ime.candidates.compact.CompactCandidateDelegate
import com.xiboard.ime.composition.PreeditDelegate
import com.xiboard.ime.correction.TypingCorrectionStats
import com.xiboard.ime.core.InputView
import com.xiboard.ime.core.TrimeInputMethodService
import com.xiboard.ime.keyboard.CommonKeyboardActionListener
import com.xiboard.ime.keyboard.KeyboardWindow
import com.xiboard.ime.popup.PopupDelegate
import com.xiboard.ime.symbol.LiquidWindow
import com.xiboard.ime.voice.OfflineVoiceInputController
import com.xiboard.ime.voice.OfflineVoiceWindow
import com.xiboard.ime.window.BoardWindowManager
import org.kodein.di.DI
import org.kodein.di.allInstances
import org.kodein.di.bindSingleton
import org.kodein.di.instance

class InputDependencyManager(
    inputView: InputView,
    context: Context,
    theme: Theme,
    service: TrimeInputMethodService,
    rime: RimeSession,
) {
    val inputModule = DI.Module("input") {
        bindSingleton { inputView }
        bindSingleton { context }
        bindSingleton { theme }
        bindSingleton { service }
        bindSingleton { rime }
        bindSingleton { InputBroadcaster() }
        bindSingleton { PopupDelegate() }
        bindSingleton { EnterKeyDisplayDelegate() }
        bindSingleton { PreeditDelegate() }
        bindSingleton { CommonKeyboardActionListener() }
        bindSingleton { TypingCorrectionStats() }
        bindSingleton { BoardWindowManager() }
        bindSingleton { InputBarDelegate() }
        bindSingleton { CompactCandidateDelegate() }
        bindSingleton { KeyboardWindow() }
        bindSingleton { LiquidWindow() }
        bindSingleton { OfflineVoiceWindow() }
        bindSingleton { OfflineVoiceInputController() }
    }

    val di = DI {
        import(inputModule)
    }

    private val broadcaster: InputBroadcaster by di.instance()

    fun start() {
        val receivers: List<InputBroadcastReceiver> by di.allInstances()
        receivers.forEach { broadcaster.addReceiver(it) }
    }

    fun stop() {
        broadcaster.clear()
    }

    companion object Factory {
        private var instance: InputDependencyManager? = null

        fun initialize(
            inputView: InputView,
            context: Context,
            theme: Theme,
            service: TrimeInputMethodService,
            rime: RimeSession,
        ): InputDependencyManager = InputDependencyManager(inputView, context, theme, service, rime).also {
            instance = it
        }

        fun getInstance(): InputDependencyManager = instance ?: throw IllegalStateException(
            "InputDependencyManager is not initialized. Call InputDependencyManager.initialize(...) before getInstance().",
        )
    }
}
