// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.xiboard.util

val String.Companion.EMPTY: String
    get() = ""

private const val SECTION_DIVIDER = ",.?!~:，。：～？！…\t\r\n\\/"

fun CharSequence.findSectionFrom(
    start: Int,
    forward: Boolean = false,
): Int {
    if (start !in 0..lastIndex) return -1
    return if (forward) {
        val subSequence = subSequence(0, start)
        subSequence.indexOfLast { SECTION_DIVIDER.contains(it) }
    } else {
        val subSequence = subSequence(start, length)
        start + subSequence.indexOfFirst { SECTION_DIVIDER.contains(it) }
    }
}

fun CharSequence.splitWithSurrogates(): List<String> = buildList {
    var index = 0
    while (index < length) {
        val first = codePointAtCompat(index)
        if (first.isInvalidReplacement()) {
            index += Character.charCount(first)
            continue
        }

        val item = StringBuilder()
        index = appendCodePointAt(item, index)

        if (first.isRegionalIndicator() && index < length && codePointAtCompat(index).isRegionalIndicator()) {
            index = appendCodePointAt(item, index)
        } else {
            index = appendEmojiTail(item, index)
        }

        if (item.isNotEmpty()) add(item.toString())
    }
}

private fun CharSequence.appendEmojiTail(
    target: StringBuilder,
    startIndex: Int,
): Int {
    var index = startIndex
    while (index < length) {
        val codePoint = codePointAtCompat(index)
        when {
            codePoint.isVariationSelector() ||
                codePoint.isEmojiModifier() ||
                codePoint.isCombiningKeycap() ||
                codePoint.isTagCharacter() -> {
                index = appendCodePointAt(target, index)
            }
            codePoint == ZERO_WIDTH_JOINER && index + Character.charCount(codePoint) < length -> {
                index = appendCodePointAt(target, index)
                index = appendCodePointAt(target, index)
            }
            else -> return index
        }
    }
    return index
}

private fun CharSequence.appendCodePointAt(
    target: StringBuilder,
    index: Int,
): Int {
    val codePoint = codePointAtCompat(index)
    target.appendCodePoint(codePoint)
    return index + Character.charCount(codePoint)
}

private fun CharSequence.codePointAtCompat(index: Int): Int = Character.codePointAt(this, index)

private fun Int.isVariationSelector(): Boolean = this in 0xFE00..0xFE0F || this in 0xE0100..0xE01EF

private fun Int.isEmojiModifier(): Boolean = this in 0x1F3FB..0x1F3FF

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.isCombiningKeycap(): Boolean = this == 0x20E3

private fun Int.isTagCharacter(): Boolean = this in 0xE0020..0xE007F

private fun Int.isInvalidReplacement(): Boolean = this == 0xFFFC || this == 0xFFFD

private const val ZERO_WIDTH_JOINER = 0x200D
