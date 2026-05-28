<!--
SPDX-FileCopyrightText: 2026 MTF Input Method contributors

SPDX-License-Identifier: GPL-3.0-or-later
-->

## Privacy Policy

**MTF Input Method** is an offline Android input method based on Trime/Rime.

The app does not request Internet permission. Keyboard input, Rime dictionaries, user
configuration, clipboard helpers, and offline voice recognition run locally on the
device. The app does not upload typing history, voice audio, recognition text, or
personal data to any server.

Permissions used by the app:

* Storage: used to keep local Rime dictionaries, user data, themes, and configuration.
* Microphone: used only when the user starts built-in offline voice input.
* Notifications: used for local maintenance/deployment status notifications.
* Vibration: used for optional key feedback.

Offline speech recognition uses a bundled sherpa-onnx Mandarin model stored in the
APK assets. No model download or cloud speech service is used at runtime.
