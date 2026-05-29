<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_app_icon.png" width="132" alt="老习输入法图标" />
</p>

<h1 align="center">老习输入法</h1>

<p align="center">
  全离线、开箱即用、默认简体中文的 Android 输入法。
</p>

<p align="center">
  <a href="https://github.com/NoMTF/Xiboard/releases/latest/download/xiboard-release.apk">
    <img alt="一键下载 APK" src="https://img.shields.io/badge/一键下载-正式 APK-5BCEFA?style=for-the-badge&labelColor=FFB3C7&color=5BCEFA" />
  </a>
  <a href="https://github.com/NoMTF/Xiboard/releases/latest">
    <img alt="最新版本" src="https://img.shields.io/github/v/release/NoMTF/Xiboard?style=for-the-badge&label=最新版&labelColor=101923&color=FFB3C7" />
  </a>
  <img alt="无网络权限" src="https://img.shields.io/badge/网络权限-不申请-ffffff?style=for-the-badge&labelColor=101923&color=8BDCFF" />
</p>

## 为什么做

输入法拿到的是你每天最私密、最完整的文字流。聊天、搜索、账号、验证码、工作内容、情绪表达，很多信息在输入阶段就已经足够敏感。我们不希望任何组织、平台或政府通过输入法侵犯用户的隐私权，也不希望用户为了表达和沟通付出“被上传、被分析、被审查”的默认成本。

老习输入法坚持本地优先：不申请 `INTERNET` 权限，不上传音频，不上传输入内容，不依赖云端候选，不做在线模型下载。你输入什么，只应留在你的设备上。

## 我们补齐了什么

同文输入法和 Rime/librime 给 Android 中文输入留下了非常强的开源基础，但原版默认体验更偏向繁体和手动配置，也没有内置离线语音输入。老习输入法把这些能力重新整理成更适合简体中文用户的开箱即用版本：

- 默认简体中文，内置 Rime-ice 雾凇拼音和扩展词库。
- 长按空格即可离线语音输入，松开上屏，全程本地识别。
- 保留 Rime 的可配置能力，同时把普通入口收敛到更易用的默认体验。
- 检查并移除网络权限残留，正式包 manifest 不包含 `android.permission.INTERNET`。
- 内置 emoji、符号、剪贴板、常用语、日期、农历、UUID、金额大写、键盘计算器等常用离线功能。
- 默认同文风键盘，粉蓝白配色，并支持跟随系统自动切换暗色模式。

## 快速安装

在 Android 手机上打开项目发布页，下载最新的 `xiboard-release.apk` 并安装：

[一键下载最新版](https://github.com/NoMTF/Xiboard/releases/latest/download/xiboard-release.apk)

安装后进入系统设置启用“老习输入法”，再在输入框中切换到它即可使用。

## 权限说明

老习输入法只保留输入法运行所需权限：

- `RECORD_AUDIO`：用于离线语音输入。
- `VIBRATE`：用于按键震动反馈。

不会申请 `INTERNET`，不会申请网络状态权限，不需要联网也可以完成中文输入、候选、emoji、词库和语音识别。

## 构建

```powershell
git submodule update --init --recursive
.\gradlew.bat --no-daemon :app:assembleRelease --console=plain
```

Release APK 会生成在：

```text
app/build/outputs/apk/release/
```

如果本机没有配置发布签名，Gradle 会生成未签名 release 包；正式发布请通过 `keystore.properties` 配置自己的 release keystore。

## 开源与致谢

本项目遵循 GPL-3.0-or-later。应用普通界面不使用上游品牌名；“关于”页会清楚列出本项目基于同文输入法、Rime/librime、sherpa-onnx 及其他第三方开源组件构建，并展示相应许可信息。
