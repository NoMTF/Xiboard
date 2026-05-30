/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.xiboard.ime.keyboard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.xiboard.R
import com.xiboard.core.KeyModifier
import com.xiboard.core.KeyModifiers
import com.xiboard.core.RimeApi
import com.xiboard.core.RimeKeyEvent
import com.xiboard.core.RimeKeyMapping
import com.xiboard.daemon.RimeSession
import com.xiboard.daemon.launchOnReady
import com.xiboard.data.prefs.AppPrefs
import com.xiboard.data.theme.ColorManager
import com.xiboard.data.theme.KeyActionManager
import com.xiboard.data.theme.ThemeManager
import com.xiboard.ime.clipboard.ClipboardWindow
import com.xiboard.ime.core.TrimeInputMethodService
import com.xiboard.ime.correction.TypingCorrectionStats
import com.xiboard.ime.dependency.InputDependencyManager
import com.xiboard.ime.enums.Keycode
import com.xiboard.ime.switches.SwitchOptionWindow
import com.xiboard.ime.symbol.LiquidData
import com.xiboard.ime.symbol.LiquidWindow
import com.xiboard.ime.voice.OfflineVoiceInputController
import com.xiboard.ime.window.BoardWindowManager
import com.xiboard.ui.main.settings.ColorPickerDialog
import com.xiboard.ui.main.settings.SoundEffectPickerDialog
import com.xiboard.ui.main.settings.ThemePickerDialog
import com.xiboard.util.AppUtils
import com.xiboard.util.InputMethodUtils
import com.xiboard.util.buildIntentFromAction
import com.xiboard.util.buildIntentFromArgument
import com.xiboard.util.customFormatDateTime
import com.xiboard.util.isAsciiPrintable
import com.xiboard.util.toast
import kotlinx.coroutines.launch
import org.kodein.di.instance
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber

class CommonKeyboardActionListener {
    private val di = InputDependencyManager.getInstance().di

    private val context: Context by di.instance()
    private val service: TrimeInputMethodService by di.instance()
    private val rime: RimeSession by di.instance()
    private val windowManager: BoardWindowManager by di.instance()
    private val keyboardWindow: KeyboardWindow by di.instance()
    private val liquidWindow: LiquidWindow by di.instance()
    private val offlineVoiceInputController: OfflineVoiceInputController by di.instance()
    private val typingCorrectionStats: TypingCorrectionStats by di.instance()

    private val prefs = AppPrefs.defaultInstance()

    private var shouldReleaseKey: Boolean = false

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                service.showDialog(dialog(api))
            }
        }
    }

    private fun showThemePicker() {
        showDialog { api ->
            ThemePickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showColorPicker() {
        showDialog { api ->
            ColorPickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showSoundEffectPicker() {
        showDialog {
            SoundEffectPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun expandActiveText(input: String): String = if (input.matches(PLACEHOLDER_PATTERN)) {
        input.format(
            service.getActiveText(1),
            service.getActiveText(2),
            service.getActiveText(3),
            service.getActiveText(4),
        )
    } else {
        input
    }

    val listener by lazy {
        object : KeyboardActionListener {
            override fun onPress(keyEventCode: Int) {
                InputFeedbackManager.run {
                    keyPressSound(keyEventCode)
                    keyPressSpeak(keyEventCode)
                }
            }

            override fun onLongPress(keyEventCode: Int) {
                if (keyEventCode.isHoldToTalkKey() && prefs.general.offlineVoiceInput.getValue()) {
                    offlineVoiceInputController.startHoldToTalk()
                }
            }

            override fun onRelease(keyEventCode: Int) {
                if (keyEventCode.isHoldToTalkKey() && prefs.general.offlineVoiceInput.getValue()) {
                    if (offlineVoiceInputController.isHoldToTalkActive) {
                        offlineVoiceInputController.stopHoldToTalk()
                    }
                    return
                }
                if (shouldReleaseKey) {
                    // FIXME: 释放按键可能不对
                    val value = RimeKeyMapping.keyCodeToVal(keyEventCode)
                    if (value != RimeKeyMapping.RimeKey_VoidSymbol) {
                        service.postRimeJob {
                            processKey(value, KeyModifier.Release.modifier)
                        }
                    }
                }
            }

            override fun onCancel(keyEventCode: Int) {
                if (keyEventCode.isHoldToTalkKey() && prefs.general.offlineVoiceInput.getValue()) {
                    offlineVoiceInputController.cancelHoldToTalk()
                }
            }

            override fun onAction(action: KeyAction) {
                val shouldHandle = when {
                    action.commit.isNotEmpty() -> {
                        service.commitText(action.commit)
                        false
                    }
                    KeyboardSwitcher.currentKeyboard.let { keyboard ->
                        action.getText(keyboard).isNotEmpty()
                    } -> {
                        onText(action.getText(KeyboardSwitcher.currentKeyboard))
                        false
                    }
                    else -> true
                }

                if (shouldHandle) {
                    when (action.code) {
                        KeyEvent.KEYCODE_SWITCH_CHARSET -> handleSwitchCharset(action)
                        KeyEvent.KEYCODE_EISU -> keyboardWindow.switchKeyboard(action.select)
                        KeyEvent.KEYCODE_LANGUAGE_SWITCH -> handleLanguageSwitch(action)
                        KeyEvent.KEYCODE_FUNCTION -> handleFunctionCommand(action)
                        KeyEvent.KEYCODE_SETTINGS -> handleSettings(action)
                        KeyEvent.KEYCODE_PROG_RED -> Unit
                        KeyEvent.KEYCODE_MENU -> Unit
                        KeyEvent.KEYCODE_VOICE_ASSIST -> handleVoiceInput()
                        else -> handleDefaultKeyAction(action)
                    }
                }
            }

            private fun handleSwitchCharset(action: KeyAction) {
                val option = action.toggle.ifEmpty {
                    toggleAsciiMode()
                    return
                }

                rime.launchOnReady { api ->
                    service.lifecycleScope.launch {
                        val status = api.getRuntimeOption(option)
                        api.setRuntimeOption(option, !status)
                        api.commitComposition()
                    }
                }
            }

            private fun handleLanguageSwitch(action: KeyAction) {
                toggleAsciiMode()
            }

            private fun toggleAsciiMode() {
                rime.launchOnReady { api ->
                    service.lifecycleScope.launch {
                        val status = api.getRuntimeOption("ascii_mode")
                        api.setRuntimeOption("ascii_mode", !status)
                        api.commitComposition()
                    }
                }
            }

            private fun handleFunctionCommand(action: KeyAction) {
                val arg = expandActiveText(action.option)

                when (action.command) {
                    "liquid_keyboard" -> handleLiquidKeyboard(arg)
                    "menu_keyboard" -> handleLiquidKeyboard("emoji")
                    "clipboard_window" -> handleClipboardWindow(arg)
                    "set_color_scheme" -> handleColorScheme(arg)
                    "set_theme" -> handleTheme(arg)
                    "broadcast" -> service.sendBroadcast(Intent(arg))
                    "clipboard" -> handleClipboard()
                    "commit" -> service.commitText(arg)
                    "date" -> service.commitText(customFormatDateTime(arg))
                    "run" -> handleRunCommand(arg)
                    "apply" -> handleApplyCommand(arg)
                    "share_text" -> service.shareText()
                    "select_candidate" -> handleSelectCandidate(arg)
                    else -> handleIntentAction(action.command, arg)
                }
            }

            private fun handleLiquidKeyboard(arg: String) {
                // for compatibility
                if (arg == "剪贴" || arg == "clipboard") {
                    windowManager.attachWindow(ClipboardWindow())
                    return
                }
                val liquidTagList = LiquidData.getTagList()
                val index = liquidTagList.indexOfFirst { tag ->
                    tag.id == arg || tag.label == arg || runCatching {
                        LiquidData.Type.valueOf(arg.uppercase())
                    }.getOrNull() == tag.type
                }

                if (index >= 0) {
                    if (
                        arg == "emoji" &&
                        windowManager.isAttached(liquidWindow) &&
                        liquidWindow.currentTagId == "emoji"
                    ) {
                        windowManager.attachWindow(KeyboardWindow)
                        return
                    }
                    windowManager.attachWindow(LiquidWindow)
                    liquidWindow.setDataByIndex(index)
                } else {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            private fun handleClipboardWindow(arg: String) {
                val tabIndex = arg.toIntOrNull()?.coerceIn(0, 1) ?: 0
                windowManager.attachWindow(ClipboardWindow(tabIndex))
            }

            private fun handleColorScheme(arg: String) {
                ThemeManager.activeTheme.colorSchemes
                    .find { it.id == arg }
                    ?.let { ColorManager.setColorScheme(it) }
            }

            private fun handleTheme(arg: String) {
                if (arg.isEmpty()) {
                    // 参数为空时，刷新当前主题
                    ThemeManager.selectTheme(ThemeManager.prefs.selectedTheme.getValue())
                } else {
                    // 通过主题名称查找对应的配置ID并切换主题
                    ThemeManager.getAllThemes()
                        .find { it.name.equals(arg, ignoreCase = true) }?.let {
                            ThemeManager.selectTheme(it.configId)
                        }
                }
            }

            private fun handleClipboard() {
                clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(service)
                    ?.let { service.commitText(it.toString()) }
            }

            private fun handleRunCommand(arg: String) {
                buildIntentFromArgument(arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleApplyCommand(arg: String) {
                when (arg) {
                    "DEPLOY" -> {
                        Timber.i("try to start maintenance via command ...")
                        rime.launchOnReady { api -> api.deploy() }
                    }
                    "SYNC_USER_DATA" -> {
                        Timber.i("try to sync rime user data via command ...")
                        rime.launchOnReady { api -> api.syncUserData() }
                    }
                    "UPDATE_CONFIG" -> {
                        Timber.i("try to update rime config via command ...")
                        rime.launchOnReady { api ->
                            api.updateConfig()
                            service.lifecycleScope.launch {
                                Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> Timber.w("Unknown apply method: $arg")
                }
            }

            private fun handleIntentAction(command: String, arg: String) {
                buildIntentFromAction(command, arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleSelectCandidate(arg: String) {
                val index = arg.toIntOrNull() ?: return
                rime.launchOnReady { api ->
                    service.lifecycleScope.launch {
                        api.selectCandidate(index, false)
                    }
                }
            }

            private fun handleSettings(action: KeyAction) {
                when (action.option) {
                    "theme", "color" -> Unit
                    "schema" -> Unit
                    "sound" -> showSoundEffectPicker()
                    else -> AppUtils.launchMainActivity(service)
                }
            }

            private fun handleVoiceInput() {
                if (prefs.general.offlineVoiceInput.getValue()) {
                    offlineVoiceInputController.toggle()
                } else {
                    switchToVoiceInputMethod()
                }
            }

            private fun switchToVoiceInputMethod() {
                val pkgName = prefs.general.preferredVoiceInput.getValue()
                val voiceInputSubType = if (pkgName.isNotEmpty()) {
                    InputMethodUtils.voiceInputMethods().find {
                        it.first.packageName == pkgName
                    }?.let {
                        it.first.id to it.second
                    } ?: InputMethodUtils.firstVoiceInput()
                } else {
                    InputMethodUtils.firstVoiceInput()
                }
                if (voiceInputSubType != null) {
                    val (id, subType) = voiceInputSubType
                    InputMethodUtils.switchInputMethod(service, id, subType)
                } else {
                    service.toast(R.string.no_voice_input_installed)
                }
            }

            private fun handleDefaultKeyAction(action: KeyAction) {
                val shouldHookShiftKey = when {
                    prefs.keyboard.hookShiftSpace.getValue() && action.code == KeyEvent.KEYCODE_SPACE -> true
                    prefs.keyboard.hookShiftNum.getValue() && action.code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in setOf(KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD) -> true
                    else -> false
                }

                if (action.modifier == 0 && KeyboardSwitcher.currentKeyboard.isOnlyShiftOn && shouldHookShiftKey) {
                    onKey(action.code, 0)
                    return
                }

                val modifier = when {
                    action.modifier == 0 -> KeyboardSwitcher.currentKeyboard.modifier
                    (action.modifier and KeyEvent.META_CTRL_ON) != 0 && isNavigationKey(action.code) ->
                        action.modifier or KeyboardSwitcher.currentKeyboard.modifier
                    else -> action.modifier
                }

                onKey(action.code, modifier)
            }

            private fun isNavigationKey(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
                keyCode == KeyEvent.KEYCODE_MOVE_END

            override fun onKey(
                keyEventCode: Int,
                metaState: Int,
            ) {
                shouldReleaseKey = false
                if (keyEventCode == KeyEvent.KEYCODE_DEL) {
                    typingCorrectionStats.onBackspace()
                }
                val value =
                    RimeKeyMapping
                        .keyCodeToVal(keyEventCode)
                        .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
                        ?: RimeKeyEvent.getKeycodeByName(Keycode.keyNameOf(keyEventCode))
                val m = if (keyEventCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                    metaState or KeyEvent.META_NUM_LOCK_ON
                } else {
                    metaState
                }
                val modifiers = KeyModifiers.fromMetaState(m).modifiers
                service.postRimeJob {
                    if (keyEventCode == KeyEvent.KEYCODE_F4) {
                        Timber.d("handleKey: ignore schema switcher")
                        return@postRimeJob
                    }
                    if (service.hookKeyboard(keyEventCode, m)) {
                        Timber.d("handleKey: hook")
                        return@postRimeJob
                    }
                    if (processKey(value, modifiers)) {
                        shouldReleaseKey = true
                        Timber.d("handleKey: processKey")
                        return@postRimeJob
                    }
                    if (AppUtils.launchKeyCategory(service, keyEventCode)) {
                        Timber.d("handleKey: openCategory")
                        return@postRimeJob
                    }
                    // other special cases
                    if (keyEventCode == KeyEvent.KEYCODE_BACK) {
                        service.requestHideSelf(0)
                    }
                    shouldReleaseKey = false
                }
            }

            override fun onText(text: String) {
                if (text.isEmpty()) return
                val status = rime.run { statusCached }
                if (!text[0].isAsciiPrintable() && status.isComposing) {
                    service.postRimeJob { commitComposition() }
                }

                var sequence = text
                while (sequence.isNotEmpty()) {
                    val slice =
                        when {
                            UNBRACED_CHAR.matches(sequence) -> UNBRACED_CHAR.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            BRACED_KEY_EVENT.matches(sequence) -> BRACED_KEY_EVENT.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            else -> sequence[0].toString()
                        }

                    service.postRimeJob {
                        if (slice.run { startsWith('{') && endsWith('}') }) {
                            onAction(KeyActionManager.getAction(slice))
                        } else if (!slice[0].isAsciiPrintable()) {
                            service.commitText(slice)
                        } else {
                            val escapedSlice = slice.replace("{}", "{braceleft}{braceright}")
                            simulateKeySequence(escapedSlice)
                        }
                    }

                    sequence = sequence.substring(slice.length)
                }
                shouldReleaseKey = false
            }
        }
    }

    companion object {
        /** Pattern for braced key event like `{Left}`, `{Right}`, etc. */
        private val BRACED_KEY_EVENT = """^(\{[^{}]+\}).*$""".toRegex()

        /** Pattern for unbraced characters (including {Escape}) like `abc`, `{Escape}jk` etc. */
        private val UNBRACED_CHAR = """^((\{Escape\})?[^{}]+).*$""".toRegex()

        private val PLACEHOLDER_PATTERN = Regex(".*(%([1-4]\\$)?s).*")

        private fun Int.isHoldToTalkKey(): Boolean = this == KeyEvent.KEYCODE_SPACE || this == KeyEvent.KEYCODE_VOICE_ASSIST
    }
}
