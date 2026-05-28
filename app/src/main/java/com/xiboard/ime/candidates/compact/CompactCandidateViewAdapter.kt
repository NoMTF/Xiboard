/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.candidates.compact

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.xiboard.core.CandidateItem
import com.xiboard.data.theme.Theme
import com.xiboard.ime.candidates.CandidateItemUi
import com.xiboard.ime.candidates.CandidateViewHolder
import splitties.dimensions.dp
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent

open class CompactCandidateViewAdapter(
    val theme: Theme,
) : BaseQuickAdapter<CandidateItem, CandidateViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = items.getOrNull(position).hashCode().toLong()

    var total: Int = -1
        private set

    var highlightedIdx: Int = -1
        private set

    var layoutMinWidth: Int = 0
        private set

    var layoutFlexGrow: Float = 0f
        private set

    fun updateLayoutParams(minWidth: Int, flexGrow: Float) {
        layoutMinWidth = minWidth
        layoutFlexGrow = flexGrow
    }

    fun updateCandidates(
        data: Array<CandidateItem>,
        total: Int,
        highlightedIndex: Int,
    ) {
        super.submitList(data.toList(), null)
        this.total = total
        this.highlightedIdx = highlightedIndex
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder {
        val ui = CandidateItemUi(context, theme)
        ui.root.apply {
            minimumWidth = dp(40)
            layoutParams = FlexboxLayoutManager.LayoutParams(wrapContent, matchParent)
        }
        return CandidateViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
        item: CandidateItem?,
    ) {
        item ?: return
        val isHighlighted = position == highlightedIdx
        holder.ui.update(item, isHighlighted)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = position // unused
        holder.ui.root.updateLayoutParams<FlexboxLayoutManager.LayoutParams> {
            minWidth = this@CompactCandidateViewAdapter.layoutMinWidth
            flexGrow = this@CompactCandidateViewAdapter.layoutFlexGrow
        }
    }
}
