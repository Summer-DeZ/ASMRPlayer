# ASMRPlayer

ASMRPlayer 是一个面向 ASMR 音声收听场景的 Android 本地播放器。它主要用于管理本地音频库、播放带字幕的音声、设置睡眠定时，并可选择登录 DLsite 来同步已购买作品和下载作品内容。

## 主要功能

- 管理本地播放列表和导入的音频文件。
- 通过 Android 系统文件选择器导入文件夹。
- 绑定字幕文件，并在播放时同步显示字幕。
- 支持可选的桌面悬浮字幕。
- 支持睡眠定时，到点后自动停止播放。
- 支持通过 WebView 登录 DLsite，同步已购买作品并下载到应用私有目录。
- 支持浅色、深色和跟随系统的主题模式。

## 支持的安卓版本

- 最低支持：Android 8.0 Oreo，API 26。
- 当前适配目标：Android 16，API 36。
- 换句话说，本应用支持 Android 8.0 及以上版本的设备。

## 构建

项目使用 Gradle 和 Android Gradle Plugin 构建。
```bash
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:assembleDebug :app:testDebugUnitTest
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:assembleRelease
```

## 使用

- 在“资料库”中导入本地音频文件或文件夹。
- 在音频菜单中绑定字幕文件。
- 在“DLsite”页面登录后，可以同步已购买作品并下载到应用内播放。
- 在“睡眠模式”中设置定时停止播放。

## 开源许可

本项目使用 MIT License 开源，详见 [LICENSE](LICENSE)。
