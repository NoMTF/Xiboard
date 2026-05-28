/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.candidates.unrolled.window

import android.view.Gravity
import android.view.ViewGroup
import androidx.transition.Slide
import androidx.transition.Transition
import com.google.android.flexbox.AlignItems
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.xiboard.ime.candidates.CandidateViewHolder
import com.xiboard.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.xiboard.ime.candidates.unrolled.UnrolledCandidateLayout
import com.xiboard.ime.candidates.unrolled.decoration.FlexboxHorizontalDecoration
import com.xiboard.ime.window.BoardWindow
import splitties.dimensions.dp
import splitties.views.dsl.core.wrapContent

class FlexboxUnrolledCandidateWindow : BaseUnrolledCandidateWindow() {
    override fun exitAnimation(nextWindow: BoardWindow): Transition = Slide().apply {
        slideEdge = Gravity.TOP
    }

    override val adapter by lazy {
        object : PagingCandidateViewAdapter(theme) {
            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): CandidateViewHolder = super.onCreateViewHolder(parent, viewType).apply {
                itemView.apply {
                    minimumWidth = dp(40)
                    val itemHeight = dp(theme.generalStyle.run { candidateViewHeight + commentHeight })
                    layoutParams =
                        FlexboxLayoutManager
                            .LayoutParams(wrapContent, itemHeight)
                            .apply { flexGrow = 1f }
                }
            }

            override fun onBindViewHolder(
                holder: CandidateViewHolder,
                position: Int,
            ) {
                super.onBindViewHolder(holder, position)
                bindCandidateUiViewHolder(holder)
            }
        }
    }

    override val layoutManager by lazy {
        FlexboxLayoutManager(context).apply {
            justifyContent = JustifyContent.SPACE_AROUND
            alignItems = AlignItems.FLEX_START
        }
    }

    override fun onCreateCandidateLayout(): UnrolledCandidateLayout = UnrolledCandidateLayout(context, theme).apply {
        recyclerView.apply {
            adapter = this@FlexboxUnrolledCandidateWindow.adapter
            layoutManager = this@FlexboxUnrolledCandidateWindow.layoutManager
            addItemDecoration(FlexboxHorizontalDecoration(separatorDrawable))
        }
    }
}
