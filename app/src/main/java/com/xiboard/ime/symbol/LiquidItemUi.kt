/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.symbol

import android.content.Context
import android.graphics.Typeface
import com.xiboard.data.theme.ColorManager
import com.xiboard.data.theme.FontManager
import com.xiboard.data.theme.Theme
import com.xiboard.ime.core.AutoScaleTextView
import com.xiboard.ime.keyboard.GestureFrame
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp

class LiquidItemUi(
    override val ctx: Context,
    private val theme: Theme,
) : Ui {
    val defaultTypeface: Typeface = FontManager.getTypeface("key_font")
    val emojiFallbackTypeface: Typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) ?: defaultTypeface

    val mainText = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        background = null
        textSize = theme.generalStyle.keyTextSize
        typeface = defaultTypeface
        includeFontPadding = false
        setPaddingDp(4, 2, 4, 2)
        setTextColor(ColorManager.getColor("key_text_color"))
    }

    override val root = view(::GestureFrame) {
        val content = constraintLayout {
            background = ColorManager.getDecorDrawable(
                "key_back_color",
                "key_border_color",
                dp(theme.generalStyle.keyBorder),
                dp(theme.generalStyle.roundCorner),
            )
            add(
                mainText,
                lParams(wrapContent, wrapContent) {
                    centerInParent()
                },
            )
        }
        add(content, lParams(matchParent, matchParent))
    }
}
