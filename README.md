# MTF Input Method

MTF Input Method is an offline Android keyboard based on Trime/Rime. It keeps the
full local Rime input platform and adds bundled offline Mandarin voice typing via
sherpa-onnx.

## Features

* Android input method based on Trime v3.3.10 and librime.
* Chinese-first offline typing with Rime schemas, dictionaries, themes, and user data.
* Built-in offline Mandarin speech recognition model:
  `sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01`.
* No `INTERNET` permission in the Android manifest.
* App package name: `com.xiboard.inputmethod`.

## Build

Initialize submodules before building:

```powershell
git submodule update --init --recursive
.\gradlew.bat :app:assembleDebug --console=plain
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/
```

## License

MTF Input Method is distributed under GPL-3.0-or-later because it is derived from
Trime. sherpa-onnx is Apache-2.0 licensed and bundled as a third-party offline
speech recognition component.
