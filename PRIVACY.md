<!--
SPDX-FileCopyrightText: 2026 Xiboard contributors

SPDX-License-Identifier: GPL-3.0-or-later
-->

## 隐私说明

**老习输入法** 是离线 Android 输入法。

应用不申请 `INTERNET` 权限。键盘输入、候选词、词库、剪贴板快捷入口、
常用语、emoji、离线语音识别和本地配置都在设备本机运行。应用不会上传
输入内容、语音音频、识别文本、剪贴板内容或个人数据到任何服务器。

当前使用的权限：

* 麦克风：仅在用户主动启动离线语音输入时使用。
* 震动：用于按键触感反馈。

离线语音识别使用 APK 内置的 sherpa-onnx 模型文件，运行时不下载模型，
不调用云端语音服务。
