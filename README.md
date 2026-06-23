# ASMRPlayer

ASMRPlayer 是一个面向 ASMR 音声收听场景的 Android 本地播放器。它主要用于管理本地音频库、播放带字幕的音声、设置睡眠定时，并可选择登录 DLsite 来同步已购买作品和下载作品内容。

## 主要功能

- 管理本地播放列表和导入的音频文件。
- 通过 Android 系统文件选择器导入文件夹。
- 绑定字幕文件，并在播放时同步显示字幕。
- 支持 AI 自动生成字幕：默认本机 Whisper 转写，也可配置自有远程 Whisper 服务转写，再用可自定义模型的 OpenAI 兼容接口翻译为中文字幕。
- 支持可选的桌面悬浮字幕。
- 支持睡眠定时，到点后自动停止播放。
- 支持通过 WebView 登录 DLsite，同步已购买作品；下载前会解析文件目录树，由用户选择要下载的内容，下载任务会持久化排队，并在本次选择全部完成后统一导入资料库。
- 支持应用内检查 GitHub Release 更新、下载 APK 并交给系统安装器；更新包缓存会在下载前、安装成功后和应用启动时自动清理。
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
- 在音频菜单中使用“自动生成字幕”；如需远程转写，先在设置页配置 Whisper 服务器地址、模型和可选 token。
- 在“DLsite”页面登录后，可以同步已购买作品并下载到应用内播放。
- 在“睡眠模式”中设置定时停止播放。

## 开源许可

本项目使用 MIT License 开源，详见 [LICENSE](LICENSE)。
