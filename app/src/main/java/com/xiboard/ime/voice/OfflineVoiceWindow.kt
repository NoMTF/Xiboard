/*
 * SPDX-FileCopyrightText: 2026 Xiboard contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.voice

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.xiboard.R
import com.xiboard.data.theme.ColorManager
import com.xiboard.ime.window.BoardWindow
import com.xiboard.ime.window.ResidentWindow
import com.xiboard.util.roundedRippleDrawable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.imageResource
import splitties.views.verticalPadding

class OfflineVoiceWindow :
    BoardWindow.NoBarBoardWindow(),
    ResidentWindow {
    private val controller: OfflineVoiceInputController by di.instance()
    private var collectJob: Job? = null
    private var titleView: TextView? = null
    private var hintView: TextView? = null
    private var liveTextView: TextView? = null
    private var modeView: TextView? = null

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = OfflineVoiceWindow

    override fun onCreateView(): View {
        val textColor = ColorManager.getColor("key_text_color")
        val subTextColor = ColorManager.getColor("comment_text_color")
        val highlightColor = ColorManager.getColor("hilited_candidate_text_color")
        val rippleColor = ColorManager.getColor("hilited_candidate_back_color")

        val hint = context.textView {
            text = context.getString(R.string.offline_voice_panel_hint)
            textSize = 15f
            setTextColor(subTextColor)
            gravity = Gravity.CENTER
        }.also { hintView = it }

        val title = context.textView {
            text = context.getString(R.string.offline_voice_panel_title)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            gravity = Gravity.CENTER
        }.also { titleView = it }

        val liveText = context.textView {
            textSize = 19f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            maxLines = 2
            horizontalPadding = dp(24)
        }.also { liveTextView = it }

        val mode = context.textView {
            text = context.getString(R.string.offline_voice_panel_mode)
            textSize = 13f
            setTextColor(textColor)
            gravity = Gravity.CENTER
            horizontalPadding = dp(14)
            verticalPadding = dp(7)
            background = roundedRippleDrawable(rippleColor, dp(18).toFloat(), subtleSurface())
        }.also { modeView = it }

        val micButton = context.imageView {
            imageResource = R.drawable.ic_baseline_keyboard_voice_24
            imageTintList = ColorStateList.valueOf(highlightColor)
            scaleType = ImageView.ScaleType.CENTER
            alpha = 0.92f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(subtleSurface())
                alpha = 120
            }
            setOnClickListener { controller.toggle() }
        }

        val leftWave = waveLine(highlightColor)
        val rightWave = waveLine(highlightColor)

        return context.constraintLayout {
            isClickable = true
            setOnClickListener { controller.toggle() }
            background = ColorManager.getDrawable("liquid_keyboard_background")
                ?: ColorManager.getDrawable("keyboard_background")

            add(
                mode,
                lParams(wrapContent, wrapContent) {
                    startOfParent(dp(16))
                    topOfParent(dp(18))
                },
            )
            add(
                title,
                lParams(wrapContent, wrapContent) {
                    centerHorizontally()
                    topOfParent(dp(92))
                },
            )
            add(
                hint,
                lParams(wrapContent, wrapContent) {
                    centerHorizontally()
                    topToBottomOf(title, dp(12))
                },
            )
            add(
                liveText,
                lParams(matchConstraints, wrapContent) {
                    startOfParent(dp(18))
                    endOfParent(dp(18))
                    topToBottomOf(hint, dp(20))
                },
            )
            add(
                micButton,
                lParams(dp(82), dp(82)) {
                    centerHorizontally()
                    bottomOfParent(dp(46))
                },
            )
            add(
                leftWave,
                lParams(matchConstraints, dp(28)) {
                    centerHorizontally()
                    bottomOfParent(dp(72))
                    startOfParent(dp(30))
                    endToStartOf(micButton)
                    rightMargin = dp(20)
                },
            )
            add(
                rightWave,
                lParams(matchConstraints, dp(28)) {
                    centerHorizontally()
                    bottomOfParent(dp(72))
                    startToEndOf(micButton)
                    leftMargin = dp(20)
                    endOfParent(dp(30))
                },
            )
        }
    }

    override fun onAttached() {
        collectJob?.cancel()
        collectJob = controller.serviceScope.launch {
            controller.state.collect { state ->
                titleView?.text = when (state.status) {
                    VoiceUiStatus.IDLE -> context.getString(R.string.offline_voice_panel_title)
                    VoiceUiStatus.LISTENING -> context.getString(R.string.offline_voice_panel_listening)
                    VoiceUiStatus.FINALIZING -> context.getString(R.string.offline_voice_panel_finalizing)
                }
                hintView?.text = when (state.status) {
                    VoiceUiStatus.FINALIZING -> context.getString(R.string.offline_voice_panel_finalizing_hint)
                    else -> context.getString(R.string.offline_voice_panel_hint)
                }
                liveTextView?.text = state.text
                liveTextView?.visibility = if (state.text.isEmpty()) View.INVISIBLE else View.VISIBLE
            }
        }
    }

    override fun onDetached() {
        collectJob?.cancel()
        collectJob = null
    }

    private fun waveLine(color: Int): View = context.horizontalLayout {
        gravity = Gravity.CENTER
        repeat(18) { index ->
            add(
                view(::View) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(2).toFloat()
                        setColor(color)
                        alpha = if (index == 11 || index == 12) 255 else 180
                    }
                },
                android.widget.LinearLayout.LayoutParams(
                    dp(if (index == 11 || index == 12) 3 else 2),
                    dp(if (index == 11 || index == 12) 27 else 5),
                ).apply {
                    marginStart = dp(4)
                    marginEnd = dp(4)
                },
            )
        }
    }

    private fun subtleSurface(): Int = ColorManager
        .getColor("key_back_color")
        .let { Color.argb(150, Color.red(it), Color.green(it), Color.blue(it)) }
}
