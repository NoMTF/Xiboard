/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.symbol

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.google.android.flexbox.FlexboxLayoutManager
import com.xiboard.data.theme.Theme
import com.xiboard.data.theme.model.LiquidKeyboard
import com.xiboard.ime.core.AutoScaleTextView
import splitties.dimensions.dp
import splitties.views.gravityCenter

class LiquidAdapter(
    private val theme: Theme,
    private val onItemClick: LiquidKeyboard.KeyItem.(Int) -> Unit,
) : BaseQuickAdapter<LiquidKeyboard.KeyItem, LiquidAdapter.ViewHolder>() {
    inner class ViewHolder(
        val ui: LiquidItemUi,
    ) : RecyclerView.ViewHolder(ui.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val ui = LiquidItemUi(context, theme)
        ui.mainText.apply {
            scaleMode = AutoScaleTextView.Mode.Proportional
            gravity = gravityCenter
            maxLines = 1
            includeFontPadding = false
            updateLayoutParams {
                width = context.dp(defaultItemWidthDp())
            }
        }
        return ViewHolder(ui)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: LiquidKeyboard.KeyItem?,
    ) {
        item ?: return
        val isEmoji = item.text.looksLikeEmoji()
        holder.ui.mainText.text = item.text
        holder.ui.mainText.typeface = if (isEmoji) {
            getEmojiTypeface(context) ?: holder.ui.emojiFallbackTypeface
        } else {
            holder.ui.defaultTypeface
        }
        holder.ui.mainText.gravity = Gravity.CENTER
            holder.ui.mainText.textSize = if (isEmoji) EMOJI_TEXT_SIZE_SP else TEXT_KEY_SIZE_SP
        holder.ui.mainText.updateLayoutParams {
            width = context.dp(if (isEmoji) emojiItemWidthDp() else defaultItemWidthDp())
            height = context.dp(if (isEmoji) emojiItemHeightDp() else defaultItemHeightDp())
        }
        val itemWidth = context.dp(if (isEmoji) emojiItemWidthDp() else defaultItemWidthDp())
        val itemHeight = context.dp(if (isEmoji) emojiItemHeightDp() else defaultItemHeightDp())
        holder.ui.root.layoutParams =
            (holder.ui.root.layoutParams as? FlexboxLayoutManager.LayoutParams)?.apply {
                width = itemWidth
                height = itemHeight
                flexBasisPercent = -1f
            } ?: FlexboxLayoutManager.LayoutParams(itemWidth, itemHeight).apply {
                flexBasisPercent = -1f
            }
        holder.ui.root.setOnClickListener {
            onItemClick.invoke(item, position)
        }
    }

    private fun defaultItemWidthDp() = theme.liquidKeyboard.singleWidth.takeIf { it > 0 } ?: DEFAULT_KEY_WIDTH_DP

    private fun defaultItemHeightDp() = theme.liquidKeyboard.keyHeight.takeIf { it > 0 } ?: DEFAULT_KEY_HEIGHT_DP

    private fun emojiItemWidthDp() = (defaultItemWidthDp() - 20).coerceIn(32, 40)

    private fun emojiItemHeightDp() = (defaultItemHeightDp() - 8).coerceIn(30, 36)

    private fun String.looksLikeEmoji(): Boolean {
        var index = 0
        while (index < length) {
            val codePoint = Character.codePointAt(this, index)
            if (codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF) return true
            index += Character.charCount(codePoint)
        }
        return false
    }

    companion object {
        private const val DEFAULT_KEY_WIDTH_DP = 52
        private const val DEFAULT_KEY_HEIGHT_DP = 38
        private const val EMOJI_TEXT_SIZE_SP = 19f
        private const val TEXT_KEY_SIZE_SP = 18f
        private var emojiTypeface: Typeface? = null
        private var emojiTypefaceLoaded = false

        private fun getEmojiTypeface(context: Context): Typeface? {
            if (!emojiTypefaceLoaded) {
                emojiTypeface = runCatching {
                    Typeface.createFromAsset(context.assets, "fonts/NotoColorEmoji.ttf")
                }.getOrNull()
                emojiTypefaceLoaded = true
            }
            return emojiTypeface
        }
    }
}
