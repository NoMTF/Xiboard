/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.clipboard

import android.content.Context
import com.xiboard.R
import com.xiboard.data.theme.Theme
import com.xiboard.ime.bar.ui.ToolButton
import com.xiboard.ime.core.InputTabLayout
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.wrapContent

class ClipboardTitleUi(override val ctx: Context, private val theme: Theme) : Ui {

    val tabLayout = InputTabLayout(ctx)

    val deleteAllButton = ToolButton(ctx, R.drawable.ic_baseline_delete_sweep_24)

    private val size = theme.generalStyle.run { candidateViewHeight + commentHeight }

    override val root = constraintLayout {
        add(
            tabLayout,
            lParams(wrapContent, dp(size)) {
                startOfParent()
                centerVertically()
            },
        )
        add(
            deleteAllButton,
            lParams(dp(size), dp(size)) {
                centerVertically()
                endOfParent()
            },
        )
    }
}
