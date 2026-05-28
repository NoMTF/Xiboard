/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.candidates.unrolled.window

import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.RecyclerView
import com.xiboard.daemon.RimeSession
import com.xiboard.daemon.launchOnReady
import com.xiboard.data.theme.ColorManager
import com.xiboard.data.theme.Theme
import com.xiboard.ime.bar.InputBarDelegate
import com.xiboard.ime.bar.UnrollButtonStateMachine
import com.xiboard.ime.broadcast.InputBroadcastReceiver
import com.xiboard.ime.candidates.CandidateViewHolder
import com.xiboard.ime.candidates.compact.CompactCandidateDelegate
import com.xiboard.ime.candidates.unrolled.CandidatesPagingSource
import com.xiboard.ime.candidates.unrolled.PagingCandidateViewAdapter
import com.xiboard.ime.candidates.unrolled.UnrolledCandidateLayout
import com.xiboard.ime.core.InputView
import com.xiboard.ime.core.TrimeInputMethodService
import com.xiboard.ime.keyboard.KeyboardWindow
import com.xiboard.ime.window.BoardWindow
import com.xiboard.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp
import kotlin.math.max

abstract class BaseUnrolledCandidateWindow :
    BoardWindow.NoBarBoardWindow(),
    InputBroadcastReceiver {
    protected val service: TrimeInputMethodService by di.instance()
    protected val rime: RimeSession by di.instance()
    protected val theme: Theme by di.instance()
    private val inputView: InputView by di.instance()
    private val bar: InputBarDelegate by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val compactCandidate: CompactCandidateDelegate by di.instance()

    private lateinit var lifecycleCoroutineScope: LifecycleCoroutineScope
    private lateinit var candidateLayout: UnrolledCandidateLayout

    protected val separatorDrawable by lazy {
        ShapeDrawable(RectShape()).apply {
            val spacing = theme.generalStyle.candidateSpacing
            val intrinsicSize = max(spacing, context.dp(spacing)).toInt()
            intrinsicWidth = intrinsicSize
            intrinsicHeight = intrinsicSize
            paint.color = ColorManager.getColor("candidate_separator_color")
        }
    }

    abstract fun onCreateCandidateLayout(): UnrolledCandidateLayout

    final override fun onCreateView(): View {
        candidateLayout =
            onCreateCandidateLayout().apply {
                recyclerView.apply {
                    // disable item cross-fade animation
                    itemAnimator = null
                }
            }
        return candidateLayout
    }

    abstract val adapter: PagingCandidateViewAdapter
    abstract val layoutManager: RecyclerView.LayoutManager

    private var offsetJob: Job? = null

    private val candidatesPager by lazy {
        Pager(
            config = PagingConfig(
                pageSize = 48,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                CandidatesPagingSource(
                    rime,
                    total = compactCandidate.adapter.total,
                    offset = adapter.offset,
                )
            },
        )
    }

    private var candidatesSubmitJob: Job? = null

    override fun onAttached() {
        lifecycleCoroutineScope = candidateLayout.findViewTreeLifecycleOwner()!!.lifecycleScope
        bar.unrollButtonStateMachine.push(UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesAttached)
        offsetJob =
            lifecycleCoroutineScope.launch {
                compactCandidate.unrolledCandidateOffset.collect {
                    if (it <= 0) {
                        windowManager.attachWindow(KeyboardWindow)
                    } else {
                        candidateLayout.resetPosition()
                        adapter.refreshWith(
                            offset = it,
                            highlightedIndex = compactCandidate.adapter.highlightedIdx,
                        )
                    }
                }
            }
        candidatesSubmitJob =
            lifecycleCoroutineScope.launch {
                candidatesPager.flow.collectLatest {
                    adapter.submitData(it)
                }
            }
    }

    fun bindCandidateUiViewHolder(holder: CandidateViewHolder) {
        holder.itemView.run {
            setOnClickListener { _ ->
                rime.launchOnReady { it.selectCandidate(holder.idx, global = true) }
            }
            setOnLongClickListener { view ->
                inputView.showCandidateActionMenu(holder.idx, holder.text, view, global = true)
                true
            }
        }
    }

    override fun onDetached() {
        bar.unrollButtonStateMachine.push(
            UnrollButtonStateMachine.TransitionEvent.UnrolledCandidatesDetached,
            UnrollButtonStateMachine.BooleanKey.UnrolledCandidatesEmpty to
                (compactCandidate.adapter.total == adapter.offset),
        )
        offsetJob?.cancel()
        candidatesSubmitJob?.cancel()
    }
}
