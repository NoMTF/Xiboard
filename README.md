# 老习输入法

老习输入法是一个全程离线运行的 Android 中文输入法，基于同文输入法与
Rime/librime 构建，并内置 sherpa-onnx 离线语音识别模型。

## 功能

* 包名：`com.xiboard.inputmethod`。
* 默认简体中文方案，内置雾凇拼音词库与常用扩展词库。
* 支持拼音纠错、模糊音、候选、英文混输、符号、emoji、剪贴板、常用语。
* 支持长按空格离线语音输入；不上传音频和输入内容。
* 内置日期、农历、UUID、数字金额大写、键盘计算器等离线快捷输入能力。
* Android manifest 不申请 `INTERNET` 权限。

## 构建

```powershell
git submodule update --init --recursive
.\gradlew.bat :app:assembleDebug --console=plain
```

Debug APK 会生成在：

```text
app/build/outputs/apk/debug/
```

## 许可

本项目遵循 GPL-3.0-or-later。关于页会列出基于同文输入法、Rime/librime、
sherpa-onnx 及其他第三方开源组件的说明与许可信息。
