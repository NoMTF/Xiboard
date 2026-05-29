<p align="center">
  <img src="./app/src/main/res/mipmap-xxxhdpi/ic_app_icon.png" width="128" height="128" alt="老习输入法图标" />
</p>

<h1 align="center">老习输入法</h1>

<p align="center">
  全离线、默认简体中文、开箱即用的 Android 输入法。
</p>

<p align="center">
  <a href="https://github.com/NoMTF/Xiboard/releases/latest/download/xiboard-release.apk">
    <img alt="下载 APK" src="https://img.shields.io/badge/%E4%B8%80%E9%94%AE%E4%B8%8B%E8%BD%BD-APK-5BCEFA?style=for-the-badge&labelColor=FFB3C7&color=5BCEFA" />
  </a>
  <a href="https://github.com/NoMTF/Xiboard/releases/latest">
    <img alt="最新版本" src="https://img.shields.io/github/v/release/NoMTF/Xiboard?style=for-the-badge&label=%E6%9C%80%E6%96%B0%E7%89%88&labelColor=101923&color=FFB3C7" />
  </a>
  <img alt="无网络权限" src="https://img.shields.io/badge/%E7%BD%91%E7%BB%9C%E6%9D%83%E9%99%90-%E4%B8%8D%E7%94%B3%E8%AF%B7-ffffff?style=for-the-badge&labelColor=101923&color=8BDCFF" />
</p>

## 为什么做

输入法拿到的是你每天最私密、最完整的文字流：聊天、搜索、账号、验证码、工作内容、情绪表达，很多信息在输入阶段就已经足够敏感。我们不希望任何组织、平台或政府通过输入法侵犯用户隐私，也不希望用户为了沟通表达付出“默认上传、默认分析、默认被看见”的成本。

老习输入法坚持本地优先：不申请 `INTERNET` 权限，不上传音频，不上传输入内容，不依赖云端候选，不做在线模型下载。你输入什么，只应留在你的设备上。

## 核心特性

- 全程离线：拼音、词库、emoji、语音识别、候选纠错都在本地运行。
- 默认简体中文：开箱即用，不需要先处理繁体布局和复杂配置。
- 内置 Rime-ice 雾凇拼音：默认启用基础词库、扩展词库和高频词库。
- 离线语音输入：长按空格说话，松开后收尾修正并上屏。
- 移动端纠错：支持邻键误触、常见错拼、模糊拼音、短语召回和整句纠错。
- 本地学习：用户选择过的纠错会在本机形成轻量统计，不上传。
- 轻量重排：根据本地上下文重排少量候选，提升日常句子的自然度。
- 常用输入能力：emoji、符号、剪贴板、日期、农历、UUID、金额大写、计算器等。
- 默认同文风键盘：粉蓝白配色，支持跟随系统切换暗色模式。

## 产品演示

### 错拼和模糊拼音

| 输入 | 候选优先 |
| --- | --- |
| `haiduo` | 好多 |
| `lihao` | 你好 |
| `zongguo` | 中国 |
| `fen/feng` | 有限模糊召回 |
| `ni/li` | 高频场景纠错 |

### 整句纠错

整句输入不会只依赖逐字拼音。老习输入法加入了本地短语图和高频句子召回，尽量接近主流输入法的整句体验，同时严格限制耗时和候选数量，避免打字延迟。

| 输入 | 候选优先 |
| --- | --- |
| `jinriantianqibucuo` | 今天天气不错 |
| `jinriantianqibucuowomenchuquchifan` | 今天天气不错我们出去吃饭 |
| `lihaowoxiangwenyixia` | 你好我想问一下 |
| `libangwofayixia` | 你帮我发一下 |
| `xianzaiyoumeiyoushijian` | 现在有没有时间 |

### 语音输入

长按空格开始录音，松开后进入短暂收尾修正。语音识别使用本地模型，不需要网络；没有云端语音，也不会上传音频。

## 我们补齐了什么

同文输入法和 Rime/librime 给 Android 中文输入留下了非常强的开源基础，但原版默认体验更偏向手动配置，也没有内置离线语音输入。老习输入法把这些能力整理成更适合简体中文用户的默认体验：

- 默认只保留简体中文主方案和英文切换。
- 移除普通界面中容易误触的方案选择入口。
- 检查并移除网络权限残留。
- 内置离线语音输入，不依赖第三方云服务。
- 恢复并启用雾凇拼音常用词库组合。
- 增加手机键盘常见误触的候选纠错。
- 增加整句级纠错和本地上下文重排。

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
.\gradlew.bat --no-daemon :app:assembleDebug --console=plain
```

Debug APK 会生成在：

```text
app/build/outputs/apk/debug/
```

发布签名、商店渠道包和可选模型包会在后续版本继续完善。

## 技术路线

- 输入法基座：Android IME + librime。
- 中文方案：Rime-ice 雾凇拼音。
- 语音识别：sherpa-onnx 本地模型。
- 纠错策略：Rime 原生候选、本地短语召回、有限模糊拼音、轻量上下文重排、本地选择学习。
- 隐私边界：无网络权限，无云端候选，无云端语音，无输入内容上传。

## 开源与致谢

本项目遵循 GPL-3.0-or-later。应用普通界面不使用上游品牌名；“关于”页会清楚列出本项目基于同文输入法、Rime/librime、sherpa-onnx 及其他第三方开源组件构建，并展示相应许可信息。
