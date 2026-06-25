> 当前软件版本：1.6.2（versionCode 23）
> 文档更新日期：2026-06-26

# ASMRPlayer AI 架构速览

本目录是本地内部文档目录，根 `.gitignore` 会排除 `DOCS/`，用于设计、规划和 AI 协作。

## 固定入口

| 文档 | 职责 |
| --- | --- |
| `README.md` | 当前架构、目录边界、数据流和验证命令 |
| `UI-Design.md` | 当前 UI 蓝图与 `design/` 设计资产索引 |
| `Feature-Design.md` | 当前已经实现的软件特性和功能行为 |
| `TODOList.md` | 后续要做、暂不做或已完成的规划项 |
| `tech/` | 临时或阶段性的技术方案、实现路线、调研和问题分析 |

## AI 协作 Skill

- `$asrmplayer-ui-probe`：位于 `/home/summer/.codex/skills/asrmplayer-ui-probe/SKILL.md`，用于 ASRMPlayer UI 点选定位、可见模拟器验证、UI Probe JSON/Logcat/截图取证，以及把 `selected.id` / `sourceHint` 映射到 Compose 源文件后再修改 UI。后续涉及 UI-Design 或前端 UI 调整时优先使用这个 Skill。

## 当前技术栈

- Android 应用模块：`:app`
- 包名：`io.github.summerdez.asmrplayer`
- 版本：`versionName 1.6.2`，`versionCode 23`
- SDK：`minSdk 26`，`compileSdk 36`，`targetSdk 36`
- UI：Jetpack Compose + Material 3，自定义暖夜琥珀主题
- 状态层：ViewModel + `StateFlow` / `SharedFlow`
- 数据层：Room，数据库 `asrmplayer.db`，schema version 6；Repository 读状态优先暴露 `Flow`，写入和必要快照读取为 `suspend` IO 调用
- 播放层：Media3 `MediaSessionService` + `ExoPlayer`
- 网络：OkHttp + WebView Cookie，服务于 DLsite 登录态、同步、封面、下载和 GitHub Release 更新检查
- AI 字幕：sherpa-onnx 1.13.3 Android AAR + Silero VAD CPU 默认本机转写 / 可选远程异步 ASR 真实进度转写服务 + OpenAI 兼容 `chat/completions` 翻译
- 依赖注入：手写 `AppContainer` / `AppGraph`
- 内部 UI Probe：`uiProbe` build type 独立包名 `io.github.summerdez.asmrplayer.uiprobe`，仅在 `BuildConfig.UI_PROBE_ENABLED=true` 时启用点选高亮、Logcat 和 JSON 输出，不进入 debug/release 正式包行为。

## 目录边界

| 路径 | 作用 |
| --- | --- |
| `app/src/main/java/io/github/summerdez/asmrplayer/ui/` | Compose 应用入口、屏幕、组件、主题和 UI 工具 |
| `app/src/main/java/io/github/summerdez/asmrplayer/presentation/` | ViewModel 与 UI State/Event |
| `app/src/main/java/io/github/summerdez/asmrplayer/domain/` | 纯业务逻辑、选择状态、播放导航、下载规划 |
| `app/src/main/java/io/github/summerdez/asmrplayer/domain/model/` | 播放列表、曲目、DLsite 作品等模型 |
| `app/src/main/java/io/github/summerdez/asmrplayer/data/` | Room、Repository、设置、下载状态 |
| `app/src/main/java/io/github/summerdez/asmrplayer/data/remote/` | DLsite HTTP、JSON/HTML 解析、封面和作品接口 |
| `app/src/main/java/io/github/summerdez/asmrplayer/data/download/` | DLsite 下载队列 Service、内容级增量任务和汇总通知 |
| `app/src/main/java/io/github/summerdez/asmrplayer/data/update/` | GitHub Release 更新检查、版本比较、APK 前台下载服务和更新通知 |
| `app/src/main/java/io/github/summerdez/asmrplayer/data/ai/` | AI 字幕模型下载、sherpa-onnx 本机转写、远程异步 ASR 转写、OpenAI 兼容翻译、VTT 输出和生成 Service |
| `app/src/main/java/io/github/summerdez/asmrplayer/playback/` | Media3 播放服务、命令客户端、字幕解析和悬浮字幕 |
| `app/src/main/java/io/github/summerdez/asmrplayer/di/` | `Application`、容器和 ViewModel Factory |
| `app/src/test/java/io/github/summerdez/asmrplayer/` | 纯逻辑、数据规划和状态测试 |
| `app/src/uiProbe/` | UI Probe 构建变体资源覆盖，例如独立应用名，避免和正式包混淆 |
| `scripts/` | 随仓库提交的辅助脚本，如 `ui-probe.sh`（UI Probe 点选取证），详见「UI Probe 工作流」 |

## 应用数据流

1. `MainActivity` 创建 Compose UI，并通过 `AppGraph.container(this).viewModelFactory` 注入 ViewModel。
2. `AppContainer` 持有 Room、Repository、DLsite API、DLsite 下载队列仓库、状态 store、播放命令客户端和 ViewModel Factory。
3. `LibraryRepository`、`DlsiteRepository`、`SettingsRepository` 负责持久化与外部数据源；Room 表状态经 `Flow` 自动推送到 ViewModel，写入与必要的单次读取在 `Dispatchers.IO` 上以 `suspend` 执行，不再通过主线程同步 DB 桥接刷新 UI。`RoomDlsiteRepository` 只协调 DLsite 本地存储、远程源与下载状态，不再持有或代理持久下载队列状态机。
4. ViewModel 输出不可变 UI state，并通过 `SharedFlow` 发送导入结果、字幕绑定、曲目移动、DLsite 提示和设置提示等一次性事件；Compose 通过 lifecycle-aware collect 渲染。
5. 播放控制经 `PlaybackCommandClient` 发送给 `PlaybackService`；Media3 `MediaController` 提供播放中、时长、位置和队列能力，`MediaSession.sessionExtras` 下发 playlist identity、字幕、悬浮窗、错误和睡眠定时 deadline 等自定义低频状态并并入 `PlaybackControllerSnapshot`。Service 端字幕状态按下一条 cue 边界定时刷新，睡眠倒计时剩余时间由客户端按 `elapsedRealtime` 本地派生，全局播放状态 bus 已删除。播放服务同时收集 `SettingsRepository.appBehaviorSettingsFlow`，用于按设置启停双耳增强 `Virtualizer`、播放/切歌短淡入和睡眠结束前 30 秒淡出。
6. DLsite 登录使用 `DlsiteLoginActivity` WebView，后续网络请求复用 WebView Cookie。
7. 主题设置通过 `SettingsRepository.themeModeFlow` 从 `app_settings` 的 `app_theme_mode` 响应式恢复；播放、下载和睡眠行为设置通过 `SettingsRepository.appBehaviorSettingsFlow` 从同一设置表恢复，供设置页、播放服务、睡眠定时 ViewModel 和 DLsite 下载入口共享；`SettingsViewModel` 只收集并保存 `domain.model.AppThemeMode` / `AppBehaviorSettings`，`AppUi` 只在 Compose 入口和 Activity 侧应用 palette 与 system bars，不被 data 层调用。
8. 设置页检查更新经 `GitHubAppUpdateRepository` 请求 GitHub Release；APK 下载交给 `AppUpdateDownloadService` 以前台 `dataSync` 服务写入 `cacheDir/updates/`，更新下载状态由 `AppContainer` 持有的 `AppUpdateDownloadStateStore` 注入给 Service 与设置页，负责同步进度、速度和完成/失败/取消状态；通知渠道“应用更新”在后台持续显示下载进度；下载完成后再通过 `FileProvider` 交给系统安装器。更新缓存下载前会清理旧 APK 和 `.part`，安装成功收到 `ACTION_MY_PACKAGE_REPLACED` 后清理已安装版本及更旧 APK 和全部 `.part`，应用启动时也会做同样的保守清理。
9. DLsite 作品菜单的载入入口会先请求 DLsite Play `ziptree.json`；remote parser 只负责解析 JSON 并产出 `domain.model.DlsiteZiptree` / `DlsiteContentFile`，`DlsiteDownloadPlanner` 再按文件目录树把音频归入目录/根目录音频选项。用户在下载内容 sheet 中手动勾选后，点击加入下载只通过 `DlsiteDownloadQueueRepository` 把任务持久化写入 `dlsite_download_queue` 并启动 `DlsiteDownloadService`，同一 work 的 active `pending` / `running` 入队会去重，且不刷新 `DlsiteWork` / `DlsiteContent` 的 `updatedAt`。
10. `DlsiteDownloadService` 启动时会把遗留 `running` 任务重置为 `pending`，按 FIFO 调度，最多 2 个作品并发；`DlsiteDownloadQueueRepository` 显式注入 `DlsiteViewModel`、`DlsiteDownloadServiceDependencies` 与 `DlsiteDownloadBlockingAdapter`，下载服务通过调度线程和下载 worker 调用临时 adapter 过渡到 suspend Repository，阻塞边界不回到 UI 主线程；Android Intent 入参在 Service 边界归一化，进入 adapter、worker 和任务回调后的 required 下载数据保持非空；异常重复 `pending` 不会阻塞后续队列，`AppContainer` 持有的 `DlsiteDownloadStateStore` 注入给 Repository 与 Service，暴露多任务 Map 与字节加权总进度。`settingsRepository` 同时注入下载入口和下载服务，用于在“仅 Wi-Fi 下载”开启时阻止非 Wi-Fi 新任务入队或调度。
11. DLsite 下载任务不会在每个内容包完成时立刻导入资料库；单次选择的所有内容下载成功后，才统一导入或复用播放列表，再按内容回填 `trackIds`，避免用户在整次下载完成前看到半成品资料库条目。
12. AI 字幕请求进入 `AiSubtitleGenerationService`：任务状态由 `AppContainer` 持有的 `AiSubtitleTaskStateStore` 注入给 Service 与 UI 共享，Service 读取转写后端设置，默认使用 sherpa-onnx CPU 后端在本机完成日语转写；用户选择远程后，整段音频上传到用户配置的远程 ASR 转写服务，以异步 job 形式获取真实转写进度，再进入 OpenAI 兼容翻译阶段。
13. 本机转写使用 Android `MediaExtractor` / `MediaCodec` 解码音频为 16k mono PCM，交给 Silero VAD 流式切段；producer 端用 0.6s 邻近阈值与 `maxSpeechDuration` 上限合并相邻短段，合并段保留首段 `startMs` 与末段真实 `endMs`，再通过 `Channel` 送入 Whisper worker 识别；worker 数和每 worker 线程数按模型体量、可用内存、低内存状态和 CPU 核数规划，ONNX provider 固定为 `cpu`。
14. 远程转写设置页的「测试连接」调用用户配置服务器的 `GET /health`，确认服务可达、鉴权可用和 `models_ready`；真正生成字幕时再以 `multipart/form-data` 调用 `POST /transcriptions` 创建任务，服务端返回 `job_id` 后轮询 `GET /transcriptions/{job_id}`；完成后通过 `GET /transcriptions/{job_id}/result` 取回 `segments`，取消时调用 `DELETE /transcriptions/{job_id}`。请求可附带 `Authorization: Bearer <token>`，为支持局域网 `http://host:port`，应用允许 cleartext traffic。
15. 远程 ASR 合约以 `Qwen3-ASR-0.6B` + `Qwen3-ForcedAligner-0.6B` 为基线模型组合；状态字段、错误码、进度单调性和轮询要求见 `DOCS/tech/remote-asr-progress-contract.md`。远程阶段 UI 进度来自服务端 `progress` / `stage`，不再只用上传完成或本地估算表示转写进度。
16. 转写完成后，分段时间轴与日文原文会缓存到 `cacheDir/ai-subtitles/segments/`。缓存键包含转写后端、本机 Whisper 模型或远程地址/端口/model，避免本机 base 与远程服务结果串用；翻译失败后重试会优先复用匹配缓存。
17. AI 字幕翻译阶段只发送字幕文本到 OpenAI 兼容接口；本地 Ollama 不需要 API Key，云端 OpenAI 兼容接口可配置 baseUrl、模型名和 API Key，默认预设为 DeepSeek `https://api.deepseek.com` + `deepseek-v4-flash`，但用户填写的任意非空模型名会原样保存。翻译请求使用较长的 LLM 超时并把网络异常转为中文提示。
18. 翻译前会按模型、标题、音轨、源字幕签名生成或复用全局情景卡，情景卡包含场景、说话者、听者称呼、语气和术语表；生成失败时降级为空情景卡，不阻断翻译。
19. 翻译按约 80 行批次执行，每批携带只读前 4 行“日文 + 已定中文”和后 4 行日文窗口；云端 OpenAI 兼容接口会额外携带完整日文字幕全文作为只读全局上下文，Ollama 仍使用精简上下文。请求和响应协议为有序 `lines` 数组，模型只接触 `{id, ja}` 文本，不接触时间轴。
20. 翻译响应必须通过结构、id 顺序/唯一性、行数、非空译文和日文假名残留校验；完整字幕全文只允许用于理解作品整体、称呼、语气、术语和音效，输出仍限制为当前批次。拟声和无实义短段也要求输出中文拟声或中文保守占位，舔舐、摩擦、呼吸等 ASMR 非台词声音优先输出「（舔舐声）」「（摩擦声）」这类中文音效描述。整批连续失败后会逐句兜底翻译，缺一行不会继续写出可能错位的 VTT。
21. 已完成批次缓存到 `cacheDir/ai-subtitles/translations/`；重试会校验曲目、音频 URI、转写签名、翻译引擎、baseUrl、model、情景卡签名、协议版本、批策略版本和源字幕签名，匹配后跳过已完成批次。翻译完成后写出仅含中文译文的 `.vtt` 并重新绑定；生成文件名包含曲目 ID，避免外部导入的多条音频在标题清洗后同名时共用同一个 VTT 文件。
22. 生成字幕 sheet 的“重新分片并翻译”会以 `forceRegenerate` 启动 Service，先清除该曲目的分段缓存、情景卡缓存和翻译缓存，再按当前后端重新转写与翻译；普通失败重试仍优先复用可匹配缓存。
23. AI 字幕生成以前台 Service 保活，通知更新做限频，避免高频转写进度回调触发系统通知队列限流或前台服务超时。
24. UI Probe 只在 `uiProbe` 构建启用：根 Compose 树由 `UiProbeHost` 包裹，带 `Modifier.uiProbe(id, label, sourceHint, metadata)` 的节点会登记屏幕内 bounds。点右上角“UI 探针”后再次点选界面，命中规则选择包含点击点的最小 bounds；同面积时选择最新登记的节点。结果写入应用私有目录 `files/ui-probe/latest-selection.json`，并用 `ASRM_UI_PROBE` 输出到 Logcat，便于根据真实点选区域定位 Compose 源文件和业务 ID。

## Release 更新规范

- 更新源固定为 `https://api.github.com/repos/Summer-DeZ/ASMRPlayer/releases/latest`。
- GitHub Release 的 `tag_name` 必须与 `app/build.gradle.kts` 中的 `versionName` 一致；允许带 `v` 前缀，例如 `v1.0.2` 或 `1.0.2`。
- Release `body` 作为应用内更新日志展示。
- Release assets 必须上传 APK；检查更新会选择第一个 `.apk` asset 的 `browser_download_url` 作为下载地址。
- APK 下载由 `AppUpdateDownloadService` 执行，通知显示百分比、速度、已下载/总大小，并提供取消操作；完成/失败会更新终态通知，取消会移除下载通知。
- 下载前会清理 `cacheDir/updates/` 下非当前目标版本的 `ASMRPlayer-*.apk` 和所有 `.part`；下载失败、普通异常或取消后会删除当前 `.part`，不做断点续传。
- APK 下载完成后走系统安装器。Android 8 及以上如果未允许“安装未知应用”，会先打开对应系统授权页，返回后继续安装；拉起安装器后不立刻删除 APK，安装成功或下次启动时再删除已安装版本及更旧 APK，并保留比当前安装版本更新的 APK。

## 主要验证命令

```bash
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:testDebugUnitTest :app:assembleDebug
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:compileUiProbeKotlin :app:installUiProbe
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:assembleRelease
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:bundleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

涉及 UI 或设备行为时，优先安装 debug 包并在真机/模拟器上验证播放、悬浮字幕、DLsite 多任务下载、睡眠定时、设置页检查更新、更新 APK 后台下载通知和 AI 字幕生成主流程；发布验证安装 release APK。设置与睡眠回归重点覆盖行为开关重启恢复、已用存储刷新、双耳增强设备降级、播放/切歌短淡入、睡眠最后 30 秒淡出和取消后音量恢复。更新下载回归重点覆盖切后台后通知仍显示进度/速度、通知取消、设置页进度同步、失败重试、下载完成安装弹窗，以及安装成功或重启后 `cacheDir/updates/` 缓存清理。DLsite 回归重点覆盖目录树选择、仅 Wi-Fi 下载在非 Wi-Fi 下阻止入队/调度、加入下载后作品列表排序不变、服务重启后 `running` 恢复为 `pending`、FIFO / 2 并发、active 任务去重、异常重复 `pending` 不阻塞，以及单次选择全部完成后才导入资料库。AI 字幕完整验证需要准备可读取的本地音频：本机路径需先下载 Whisper base 或 small 模型，远程路径需配置实现 `DOCS/tech/remote-asr-progress-contract.md` 的 ASR 服务。CPU 流水线切片路线重点记录实时率、峰值内存、温升、失败率、字幕准确率、进度条单调性、取消响应和 UI 流畅度；远程路线重点覆盖 `POST /transcriptions` 创建 job、`GET /transcriptions/{job_id}` 真实进度轮询、`GET /transcriptions/{job_id}/result` 结果获取、`DELETE /transcriptions/{job_id}` 取消、错误码中文提示、返回 segments 校验和缓存隔离。OpenAI 兼容翻译回归重点覆盖默认 DeepSeek `deepseek-v4-flash`、自定义模型名保存、DeepSeek thinking 控制不污染通用端点、ordered-lines 严格对齐、完整日文字幕全文只读上下文、短拟声 1:1、情景卡缓存、滑动上下文、整批失败后的逐句兜底、翻译失败后复用切片与翻译批次缓存重试，以及最终绑定字幕只保留中文译文。

## UI Probe 工作流

```bash
# 仅首次需要：创建官方 x86_64 Android 36.1 UI 验证 AVD。当前本机已存在 asrm-ui-x86-api36。
# 官方 Android Emulator 在 x86_64 Linux 主机上不能直接启动 arm64-v8a system image。
/home/summer/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/home/summer/Android/Sdk "system-images;android-36.1;google_apis;x86_64"
/home/summer/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd -n asrm-ui-x86-api36 -k "system-images;android-36.1;google_apis;x86_64" -d pixel_7
/home/summer/Android/Sdk/emulator/emulator -avd asrm-ui-x86-api36 -gpu swiftshader_indirect -no-audio

# Codex 需要把窗口留给用户继续操作时，使用独立 systemd 单元启动可见模拟器。
# 若宿主机键盘不能输入，先确认 AVD 配置中 `hw.keyboard=yes`，然后重启该模拟器。
systemd-run --user --unit=asrm-ui-emulator-visible --collect \
  --setenv=DISPLAY=:0 \
  --setenv=WAYLAND_DISPLAY=wayland-0 \
  --setenv=XDG_RUNTIME_DIR=/run/user/1000 \
  /home/summer/Android/Sdk/emulator/emulator \
    -avd asrm-ui-x86-api36 \
    -gpu swiftshader_indirect \
    -no-audio \
    -no-snapshot-load \
    -netdelay none \
    -netspeed full

# 安装独立 UI Probe 包，不覆盖正式包。
GRADLE_USER_HOME=/tmp/asrm-gradle ./gradlew :app:installUiProbe

# 在应用内点右上角“UI 探针”，再点想改的 UI 区域，然后读取反馈。
adb shell run-as io.github.summerdez.asmrplayer.uiprobe cat files/ui-probe/latest-selection.json
adb logcat -s ASRM_UI_PROBE
adb exec-out screencap -p > /tmp/asrm-ui-probe-selection.png
```

上面这些点选取证命令已固化进 `scripts/ui-probe.sh`（随仓库提交，非 `DOCS/` 本地文档），日常协作优先用脚本子命令：

```bash
scripts/ui-probe.sh install        # 构建并安装 uiProbe 包，然后拉起
# 在应用内点右上角“UI 探针”，再点目标区域
scripts/ui-probe.sh read           # 读取并美化 latest-selection.json（默认子命令）
scripts/ui-probe.sh field selected.id        # 只取单个字段（需要 jq）
scripts/ui-probe.sh watch          # 等待下一次点选写入后自动打印
scripts/ui-probe.sh shot           # 截图到 /tmp/asrm-ui-probe-selection.png
scripts/ui-probe.sh log            # 跟随 ASRM_UI_PROBE Logcat
scripts/ui-probe.sh help           # 查看全部子命令与环境变量（ADB / ANDROID_SERIAL / GRADLE_USER_HOME）
```

UI Probe 输出中的 `selected.id` / `label` / `sourceHint` / `metadata` 是后续修改 UI 的入口依据；截图用于确认视觉区域，JSON 用于定位 Compose 源文件和业务对象。若需要验证发布行为，仍使用 release APK，不能用 `uiProbe` 变体替代。

已验证的基准点选链路：在可见模拟器中打开 `ASMRPlayer UI Probe`，点 `UI 探针` 后点资料库搜索框，`latest-selection.json` 应返回 `selected.id=library.search`、`label=资料库搜索框`、`sourceHint=LibraryScreen.kt`，截图中搜索框出现橙色高亮并在底部信息面板显示同样 ID 与来源。

键盘输入基准：`/home/summer/.android/avd/asrm-ui-x86-api36.avd/config.ini` 中 `hw.keyboard` 必须为 `yes`；若修改过此配置，需要重启 `asrm-ui-emulator-visible`。Android 侧可用 `adb shell settings put secure show_ime_with_hard_keyboard 1` 让启用硬件键盘时仍显示软键盘。
