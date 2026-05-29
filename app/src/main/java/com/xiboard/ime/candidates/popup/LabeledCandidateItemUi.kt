/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.candidates.popup

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import androidx.annotation.ColorInt
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.xiboard.core.CandidateProto
import com.xiboard.data.theme.ColorManager
import com.xiboard.data.theme.FontManager
import com.xiboard.data.theme.Theme
import com.xiboard.util.sp
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.textView

class LabeledCandidateItemUi(
    override val ctx: Context,
    val theme: Theme,
) : Ui {
    private val labelSize = theme.window.foreground.labelFontSize
    private val textSize = theme.window.foreground.textFontSize
    private val commentSize = theme.window.foreground.commentFontSize
    private val labelFont = FontManager.getTypeface("label_font")
    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")
    private val labelColor = ColorManager.getColor("label_color")
    private val textColor = ColorManager.getColor("candidate_text_color")
    private val commentColor = ColorManager.getColor("comment_text_color")
    private val highlightLabelColor = ColorManager.getColor("hilited_label_color")
    private val highlightCommentTextColor = ColorManager.getColor("hilited_comment_text_color")
    private val highlightCandidateTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val highlightCandidateBackColor = ColorManager.getColor("hilited_candidate_back_color")

    override val root =
        textView {
            val v = dp(theme.window.itemPadding.vertical)
            val h = dp(theme.window.itemPadding.horizontal)
            setPadding(h, v, h, v)
        }

    private inline fun SpannableStringBuilder.inSpanWith(
        @ColorInt color: Int,
        textSize: Float,
        typeface: Typeface,
        builderAction: SpannableStringBuilder.() -> Unit,
    ) = inSpans(CandidateItemSpan(color, textSize, typeface), builderAction)

    fun update(
        candidate: CandidateProto,
        highlighted: Boolean,
    ) {
        val labelFg = if (highlighted) highlightLabelColor else labelColor
        val textFg = if (highlighted) highlightCandidateTextColor else textColor
        val commentFg = if (highlighted) highlightCommentTextColor else commentColor
        root.text =
            buildSpannedString {
                inSpanWith(labelFg, ctx.sp(labelSize), labelFont) { append(candidate.label) }
                append(" ")
                inSpanWith(textFg, ctx.sp(textSize), textFont) { append(candidate.text) }
                val displayComment = candidate.comment.orEmpty().toDisplayComment()
                if (displayComment.isNotBlank()) {
                    append(" ")
                    inSpanWith(commentFg, ctx.sp(commentSize), commentFont) { append(displayComment) }
                }
            }
        val bg =
            GradientDrawable().apply {
                if (highlighted) {
                    setColor(highlightCandidateBackColor)
                    cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)
                } else {
                    setColor(Color.TRANSPARENT)
                }
            }
        root.background = bg
    }

    private fun String.toDisplayComment(): String = when {
        startsWith(CORRECTION_COMMENT_PREFIX) || startsWith(STRONG_CORRECTION_COMMENT_PREFIX) -> CORRECTION_COMMENT_LABEL
        startsWith(ASCII_CORRECTION_COMMENT_PREFIX) || startsWith(ASCII_STRONG_CORRECTION_COMMENT_PREFIX) -> CORRECTION_COMMENT_LABEL
        startsWith(ASCII_SENTENCE_COMMENT_PREFIX) || startsWith(ASCII_STRONG_SENTENCE_COMMENT_PREFIX) -> SENTENCE_COMMENT_LABEL
        else -> this
    }

    private companion object {
        private const val CORRECTION_COMMENT_PREFIX = "纠 "
        private const val STRONG_CORRECTION_COMMENT_PREFIX = "纠! "
        private const val ASCII_CORRECTION_COMMENT_PREFIX = "@typo "
        private const val ASCII_STRONG_CORRECTION_COMMENT_PREFIX = "@typo! "
        private const val ASCII_SENTENCE_COMMENT_PREFIX = "@sent "
        private const val ASCII_STRONG_SENTENCE_COMMENT_PREFIX = "@sent! "
        private const val CORRECTION_COMMENT_LABEL = "纠"
        private const val SENTENCE_COMMENT_LABEL = "句"
    }
}
