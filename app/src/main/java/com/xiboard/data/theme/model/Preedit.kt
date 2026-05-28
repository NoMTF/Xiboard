/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.data.theme.model

import android.os.Parcelable
import com.xiboard.util.yaml.Node
import com.xiboard.util.yaml.float
import com.xiboard.util.yaml.int
import com.xiboard.util.yaml.mapping
import kotlinx.parcelize.Parcelize

@Parcelize
data class Preedit(
    val horizontalPadding: Int = 8,
    val topEndRadius: Float = 0f,
    val alpha: Float = 0.8f,
    val foreground: Foreground = Foreground(),
) : Parcelable {

    @Parcelize
    data class Foreground(
        val fontSize: Float = 16f,
    ) : Parcelable {
        companion object {
            fun decode(node: Node.Mapping?): Foreground = Foreground(
                fontSize = node?.get("font_size")?.float ?: 16f,
            )
        }
    }

    companion object {
        fun decode(node: Node.Mapping?): Preedit = Preedit(
            horizontalPadding = node?.get("horizontal_padding")?.int ?: 8,
            topEndRadius = node?.get("top_end_radius")?.float ?: 0f,
            alpha = node?.get("alpha")?.float ?: 0.8f,
            foreground = Foreground.decode(node?.get("foreground")?.mapping),
        )
    }
}
