/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.candidates.unrolled

import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.xiboard.core.CandidateItem
import com.xiboard.data.theme.Theme
import com.xiboard.ime.candidates.CandidateItemUi
import com.xiboard.ime.candidates.CandidateViewHolder

open class PagingCandidateViewAdapter(
    val theme: Theme,
) : PagingDataAdapter<CandidateItem, CandidateViewHolder>(diffCallback) {
    companion object {
        private val diffCallback =
            object : DiffUtil.ItemCallback<CandidateItem>() {
                override fun areItemsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem === newItem

                override fun areContentsTheSame(
                    oldItem: CandidateItem,
                    newItem: CandidateItem,
                ): Boolean = oldItem == newItem
            }
    }

    var offset: Int = 0
        private set

    var highlightedIndex: Int = -1
        private set

    fun refreshWith(offset: Int, highlightedIndex: Int) {
        this.offset = offset
        this.highlightedIndex = highlightedIndex
        refresh()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CandidateViewHolder = CandidateViewHolder(CandidateItemUi(parent.context, theme))

    override fun onBindViewHolder(
        holder: CandidateViewHolder,
        position: Int,
    ) {
        val item = getItem(position) ?: return
        val idx = position + offset
        val highlighted = idx == highlightedIndex
        holder.ui.update(item, highlighted)
        holder.text = item.text
        holder.comment = item.comment
        holder.idx = idx
    }
}
