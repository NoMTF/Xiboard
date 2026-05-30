/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.symbol

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.xiboard.daemon.RimeSession
import com.xiboard.daemon.launchOnReady
import com.xiboard.data.SymbolHistory
import com.xiboard.data.theme.Theme
import com.xiboard.data.theme.model.LiquidKeyboard
import com.xiboard.ime.core.TrimeInputMethodService
import com.xiboard.ime.keyboard.CommonKeyboardActionListener
import com.xiboard.ime.keyboard.KeyboardWindow
import com.xiboard.ime.window.BoardWindow
import com.xiboard.ime.window.BoardWindowManager
import com.xiboard.ime.window.ResidentWindow
import org.kodein.di.instance

class LiquidWindow :
    BoardWindow.NoBarBoardWindow(),
    ResidentWindow {
    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val theme: Theme by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by di.instance()

    private lateinit var liquidLayout: LiquidLayout
    private val symbolHistory = SymbolHistory(180)
    var currentDataType: LiquidData.Type = LiquidData.Type.SINGLE
        private set
    var currentTagId: String = ""
        private set

    private val adapter by lazy {
        LiquidAdapter(theme) {
            when (currentDataType) {
                LiquidData.Type.SYMBOL -> triggerSymbolInput(this.altText)
                LiquidData.Type.TABS -> {
                    val realPosition = LiquidData.getTagList()
                        .indexOfFirst { it.label == this.text }
                    setDataByIndex(realPosition)
                }
                else -> {
                    service.commitText(this.text)
                    if (currentDataType != LiquidData.Type.HISTORY) {
                        symbolHistory.insert(this.text)
                        symbolHistory.save()
                    }
                }
            }
        }
    }

    private val mainLayoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = LiquidWindow

    override fun onCreateView(): View = LiquidLayout(context, theme, commonKeyboardActionListener).apply {
        liquidLayout = this
        tabsUi.apply {
            setTags(LiquidData.getTagList())
            setOnTabClickListener { i ->
                setDataByIndex(i)
            }
        }
        recyclerView.apply {
            layoutManager = mainLayoutManager
            this.adapter = this@LiquidWindow.adapter
        }
    }

    override fun onAttached() {}

    override fun onDetached() {}

    fun setDataByIndex(i: Int) {
        val tag = LiquidData.getTagList()[i]
        currentDataType = tag.type
        currentTagId = tag.id
        liquidLayout.tabsUi.activateTab(i)
        when (tag.type) {
            LiquidData.Type.HISTORY -> {
                symbolHistory.load()
                submitData(symbolHistory.toOrderedList().map { LiquidKeyboard.KeyItem(it) })
            }
            else -> {
                val data = LiquidData.getDataByIndex(i)
                submitData(data)
            }
        }
    }

    private fun submitData(data: List<LiquidKeyboard.KeyItem>) {
        adapter.submitList(data)
    }

    private fun triggerSymbolInput(symbol: String) {
        rime.launchOnReady {
            val (isAsciiMode, isAsciiPunch) = it.statusCached.run { isAsciiMode to isAsciiPunct }
            if (isAsciiMode) it.setRuntimeOption("ascii_mode", false)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", false)
            it.clearComposition()
            it.simulateKeySequence(symbol)
            if (isAsciiPunch) it.setRuntimeOption("ascii_punch", true)
            ContextCompat.getMainExecutor(service).execute {
                windowManager.attachWindow(KeyboardWindow)
            }
        }
    }
}
