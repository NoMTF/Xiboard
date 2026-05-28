// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.xiboard.ime.symbol

import android.annotation.SuppressLint
import android.content.Context
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import com.xiboard.data.theme.KeyActionManager
import com.xiboard.data.theme.Theme
import com.xiboard.data.theme.model.LiquidKeyboard
import com.xiboard.ime.core.AutoScaleTextView
import com.xiboard.ime.keyboard.CommonKeyboardActionListener
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class LiquidLayout(
    context: Context,
    private val theme: Theme,
    commonKeyboardActionListener: CommonKeyboardActionListener,
) : ConstraintLayout(context) {
    // TODO: 继承一个键盘视图嵌入到这里，而不是自定义一个视图
    private val fixedKeyBar =
        constraintLayout {
            val fixedKeys =
                theme.liquidKeyboard.fixedKeyBar.keys
            if (fixedKeys.isNotEmpty()) {
                val btns =
                    Array(fixedKeys.size) { index ->
                        val presetKeyName = fixedKeys[index]
                        val presetKey = theme.presetKeys[presetKeyName]
                        val ui = LiquidItemUi(context, theme)
                        ui.mainText.apply {
                            text = presetKey?.label ?: ""
                            scaleMode = AutoScaleTextView.Mode.Proportional
                            textSize = FIXED_KEY_TEXT_SIZE_SP
                            maxLines = 1
                        }
                        ui.root.apply {
                            isRepeatable = presetKey?.repeatable ?: false
                            onClick = {
                                commonKeyboardActionListener.listener.onAction(
                                    KeyActionManager.getAction(presetKeyName),
                                )
                            }
                        }
                        return@Array ui.root
                    }
                val marginX = theme.liquidKeyboard.marginX
                when (theme.liquidKeyboard.fixedKeyBar.position) {
                    LiquidKeyboard.KeyBar.Position.LEFT,
                    LiquidKeyboard.KeyBar.Position.RIGHT,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(context.dp(fixedKeyWidthDp()), matchConstraints) {
                                    if (i == 0) {
                                        topOfParent()
                                    } else {
                                        topMargin = dp(marginX).toInt()
                                        below(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        bottomOfParent()
                                    } else {
                                        bottomMargin = dp(marginX).toInt()
                                        above(btns[i + 1])
                                    }
                                },
                            )
                        }
                    }
                    LiquidKeyboard.KeyBar.Position.TOP,
                    LiquidKeyboard.KeyBar.Position.BOTTOM,
                    -> {
                        btns.forEachIndexed { i, btn ->
                            add(
                                btn,
                                lParams(context.dp(fixedKeyWidthDp()), context.dp(fixedKeyHeightDp())) {
                                    if (i == 0) {
                                        startOfParent()
                                    } else {
                                        leftMargin = dp(marginX).toInt()
                                        after(btns[i - 1])
                                    }
                                    if (i == btns.size - 1) {
                                        endOfParent()
                                    } else {
                                        rightMargin = dp(marginX).toInt()
                                        before(btns[i + 1])
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

    val recyclerView =
        recyclerView {
            val space = dp(theme.liquidKeyboard.marginX.toInt().coerceAtMost(3))
            addItemDecoration(SpacesItemDecoration(space))
            setPadding(space)
        }

    val root = view(::FrameLayout) {
        add(recyclerView, lParams(matchParent, matchParent))
    }

    val tabsUi = LiquidTabsUi(context, theme)

    init {
        when (theme.liquidKeyboard.fixedKeyBar.position) {
            LiquidKeyboard.KeyBar.Position.TOP -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topToBottomOf(tabsUi.root)
                        bottomOfParent()
                    },
                )
                add(
                    tabsUi.root,
                    lParams(matchParent, context.dp(38)) {
                        topToBottomOf(fixedKeyBar)
                        centerHorizontally()
                        bottomToTopOf(root)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, context.dp(fixedKeyHeightDp())) {
                        centerHorizontally()
                        topOfParent()
                        bottomToTopOf(tabsUi.root)
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.BOTTOM -> {
                add(
                    root,
                    lParams {
                        centerHorizontally()
                        topToBottomOf(tabsUi.root)
                        bottomToTopOf(fixedKeyBar)
                    },
                )
                add(
                    tabsUi.root,
                    lParams(matchParent, context.dp(38)) {
                        topOfParent()
                        centerHorizontally()
                        bottomToTopOf(root)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, context.dp(fixedKeyHeightDp())) {
                        centerHorizontally()
                        topToBottomOf(root)
                        bottomOfParent()
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.LEFT -> {
                add(
                    root,
                    lParams {
                        topToBottomOf(tabsUi.root)
                        startToEndOf(fixedKeyBar)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    tabsUi.root,
                    lParams(matchParent, context.dp(38)) {
                        topOfParent()
                        startToEndOf(fixedKeyBar)
                        endOfParent()
                        bottomToTopOf(root)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        startOfParent()
                        endToStartOf(tabsUi.root)
                        topOfParent()
                        bottomOfParent()
                    },
                )
            }
            LiquidKeyboard.KeyBar.Position.RIGHT -> {
                add(
                    root,
                    lParams {
                        topToBottomOf(tabsUi.root)
                        startOfParent()
                        endToStartOf(fixedKeyBar)
                        bottomOfParent()
                    },
                )
                add(
                    tabsUi.root,
                    lParams(matchParent, context.dp(38)) {
                        topOfParent()
                        startOfParent()
                        endToStartOf(fixedKeyBar)
                        bottomToTopOf(root)
                    },
                )
                add(
                    fixedKeyBar,
                    lParams(wrapContent, matchConstraints) {
                        startToEndOf(tabsUi.root)
                        endOfParent()
                        topOfParent()
                        bottomOfParent()
                    },
                )
            }
        }
    }

    private fun fixedKeyWidthDp() = (theme.liquidKeyboard.singleWidth - 10).coerceIn(38, 46)

    private fun fixedKeyHeightDp() = (theme.liquidKeyboard.keyHeight - 6).coerceIn(28, 32)

    companion object {
        private const val FIXED_KEY_TEXT_SIZE_SP = 15f
    }
}
