> 当前软件版本：1.6.2（versionCode 23）
> 文档更新日期：2026-06-26

# 1.6.2

## 2026-06-26

界面与行为更新：重做黑白主题层级与粉色选中态；播放器背景改为从播放列表封面取色并贯穿整屏；mini player、设置二级页、DLsite 作品卡展开/收起动画统一为跟随系统 vsync 的 60-120Hz 动效；设置页、睡眠定时、DLsite 下载入口和更新检查等未落地行为补齐；移除资料库空态、注册入口、开关 toast、播放器多余占位和多个外框/填充状态；版本号同步至 1.6.2 / versionCode 23。

---

# 1.6.1

## 2026-06-25

架构重构：P0–P1 架构债已还清：删 `DbIo.runBlocking`、仓库全 suspend/Flow、命令式双源清除、全局 bus 收进 DI、播放状态统一走 `sessionExtras`、接口双轨 API 清除。

C 代码层：全仓 wildcard import 清零（含 `ExampleInstrumentedTest.kt` 的 `org.junit.Assert.*`）、Java 清零、nullable 参数全链路收紧（仓库/model/DTO/remote）、`!!` 清零、`@JvmField/@JvmStatic/@JvmOverloads` 清零、`SubtitleCueScheduler` 抽出；DLsite ziptree/content-file DTO 下沉到 `domain.model.DlsiteZiptree` / `DlsiteContentFile`，下载规划不再依赖 remote parser 内部 DTO。

D 设计层：`LibraryFileImportUseCase` 完成（含 `LibraryImportFiles<T>` 端口/适配器）、`PlaybackPresentationState` 协调器落地。A7 尾段完成：`DlsiteRepository` 不再暴露/代理下载队列 API，`RoomDlsiteRepository` 只协调本地/远程/下载状态；`DlsiteDownloadQueueRepository` 显式注入 `DlsiteViewModel`、`DlsiteDownloadServiceDependencies` 和 `DlsiteDownloadBlockingAdapter`。主题设置完成持久化分层：`AppThemeMode` 归入 `domain.model`，`SettingsRepository` 通过 `app_settings.app_theme_mode` 暴露 `themeModeFlow`，重启后可恢复主题，`AppUi` 只负责 palette/system bars。D1 前置拆分完成：`PlaybackControllerSnapshot.activeServiceSnapshotOrNull()` 统一有效 service snapshot 判断，Service 端不再构造/发布 `sleepTimerRemainingMs`，睡眠倒计时 remaining 继续由客户端按锚点派生。

D3 大粒度拆分继续落地：`AppScaffold.kt` 抽出根 `UiProbeHost` / `Box` / `Scaffold` / `PageHeader` / `BottomPlaybackArea` / `content` / `dialogHost` slot，`AppTabHost.kt` 抽出 MEDIA / SETTINGS / SLEEP / DLSITE tab 分发，`AppActivityLaunchers.kt` 抽出音频/文件夹/封面/字幕/DLsite 登录 launcher，`AppRootState.kt` 抽出根 overlay/dialog 状态与播放器/队列 `BackHandler`；组合根仍保留 VM collect、`LaunchedEffect`、derived state、host wiring 与业务 callback 编排。

D4 大文件拆分按新完成标准达成：单个大文件不超过 500 行，不再要求 400 行。最终复审行数：`ASMRPlayerApp.kt` 339、`AppDialogHost.kt` 370、`AppScaffold.kt` 102、`AppTabHost.kt` 155、`AppActivityLaunchers.kt` 106、`AppRootState.kt` 46、`SettingsScreen.kt` 252、`SettingsRows.kt` 456、`SettingsAiPage.kt` 403、`SettingsUpdateSection.kt` 267、`PlayerScreen.kt` 144、`PlayerControls.kt` 300、`PlayerMoreMenu.kt` 386、`PlayerTransport.kt` 267、`LibraryScreen.kt` 260、`LibraryRows.kt` 495、`LibrarySheets.kt` 422、`DlsiteScreen.kt` 116、`DlsiteRows.kt` 399、`DlsiteDialogs.kt` 237、`SleepScreen.kt` 162、`SleepComponents.kt` 366；`app/src/main/java` Kotlin 源文件无超过 500 行项，后续不为 400 行目标继续拆。

C5 高风险异常路径补强：DLsite 取消传播、批量暂停、删除收尾、下载 worker 中断识别/清理后恢复 interrupt、下载失败原始异常日志、DocumentFiles 降级日志、远程下载中断重抛、播放 controller 构建失败重试、AI 字幕 job 生命周期加锁、音频 decoder configure/start 失败释放路径、`SceneContextBuilder` 情景卡网络失败降级日志均已修补；单测开 `unitTests.isReturnDefaultValues = true` 以兼容生产 `Log` 调用。

D1/D3/C5 收口：D1 评估结论为**不合并**三份快照（IPC wire DTO / 客户端 runtime snapshot / UI 投影是三个真实边界），重复判断已抽成 `activeServiceSnapshotOrNull()`；D3 launcher/state/effect host 已抽出，组合根剩 VM collect 与 effect 属正常职责；C5 高风险路径已补强，剩余宽泛 `catch`/`runCatching` 多为字段级解析与可选缓存兜底，不计入高风险。

收尾修复（衔接上一轮未编译完的中间态）：`DlsiteDialogs.kt`/`DlsiteRows.kt`/`LibraryRows.kt` 误生成的 `layout.weight`、`layout.align` 顶层 import 删除（scope 成员无需 import），`progressPercent` 智能转换改局部 val，`LibraryRows.kt` 补 `transcriptionDetailLabel` import，`PlaybackTrackSnapshotMapper.kt` 跟进 `QueueIdentity` 改名，`appUpdateDownloadUiStatus` 由 `private` 放宽为 `internal` 供测试访问。

验证：`:app:compileDebugKotlin` 通过；`:app:testDebugUnitTest` 全绿（202 项）；`git diff --check` 通过。

**待完成：**

- [ ] 暂不做：本轮不引入 ktlint/Spotless；如后续需要再单独评估。

---

## 2026-06-24

缺陷修复：AI 字幕翻译：片假名残留改为完成态质量提示（不阻断写入/绑定/预览）；版本号同步至 1.6.1 / versionCode 22。

---

# 1.6.0

## 2026-06-24

远程 ASR 升级为异步任务合约：`POST /transcriptions` 创建 job → 轮询状态/进度 → `GET .../result` 获取 segments → `DELETE` 取消；错误码映射中文提示；服务端基线 Qwen3-ASR + ForcedAligner。

- [ ] 待评估：轮询间隔是否按服务端 `retry_after_ms` 动态调整。
- [ ] 暂不做：内置官方公共 ASR 地址；暂不做：App 仓库内放服务端脚手架。

---

# 1.3.5

## 2026-06-23

AI 字幕文件名加入 `trackId` 防同名覆盖；远程转写设置拆分「服务器地址」与「端口」；用户可见文案统一为「远程转写服务」；OpenAI 兼容接口不发 DeepSeek 专属 thinking 字段。

---

# 1.3.3

## 2026-06-23

应用内更新缓存清理：下载前/失败/成功安装后清理旧 APK 和 `.part`；`ACTION_MY_PACKAGE_REPLACED` receiver；启动时保守清理。

---

# 1.3.2

## 2026-06-23

APK 下载迁移到前台服务（`AppUpdateDownloadService`）；下载状态 store；通知显示进度/速度；`SettingsViewModel` 改为启动服务。

---

# 1.3.1

## 2026-06-23

DLsite 下载选项按 `ziptree.json` 目录树生成（取代版本猜测）；下载队列改为 Room 持久化（`dlsite_download_queue`，schema 6），FIFO 调度，最多 2 并发；AI 翻译模型名不再强制替换用户自定义值；OpenAI 兼容文案。

---

# 1.3.0

## 2026-06-23

新增可选远程 ASR 后端（整段音频上传，服务端全包 VAD+识别）；`RemoteWhisperTranscriber`；设置页「转写后端」分段；v1.6.0 起接口以异步 job 合约为准。

- [ ] 待做（发布后）：根 `README.md` 补服务端开源项目链接（待该 repo 公开后人工填写）。

---

# 1.2.5

## 2026-06-23

DeepSeek 翻译加入完整日文字幕全文只读上下文；翻译批次从 60 行调至 80 行；Ollama 不携带全文上下文。

---

# 1.2.4

## 2026-06-23

翻译 prompt 改为中文保守占位（短碎片用「（听不清）」）；ASMR 音效按标签翻译；禁止 `zh` 残留日文假名；新增「成人内容直译」开关。

---

# 1.2.3

## 2026-06-23

VAD 切段后增加「相邻近段合并」（gap < 0.6s）消除句中停顿切碎；1 段前瞻缓冲 producer-consumer 流水线；生成字幕 sheet 新增「重新分片并翻译」入口。

---

# 1.2.2

## 2026-06-23

翻译 prompt 增加「逐行保守翻译」规则，禁止情景卡臆断；情景卡 glossary key 必须是字幕实际出现的日文原词。

---

# 1.2.1

## 2026-06-23

修复 Android 运行时正则 `}` 转义崩溃；放宽 ordered-lines 字段顺序兼容。

---

# 1.2.0

## 2026-06-23

翻译请求改为有序数组 `{"lines":[{"id","ja"/"zh"}]}`；全局情景卡预调用；滑动上下文窗口（补上 1.1.0 未兑现项）；三层校验 + 逐句兜底自愈；`temperature` 0.2–0.3。

---

# 1.1.3

## 2026-06-23

修复 DLsite 单内容下载后导入成两个同名播放列表（`playlistId` 未回写任务快照）。

---

# 1.1.2

## 2026-06-23

本机 Whisper 性能优化：VAD 与识别流水线化（producer-consumer，消除解码阶段空转）；并发数/线程数按模型与可用内存自适应（base 最多 3 worker，small 最多 2 worker）。

---

# 1.1.0

## 2026-06-22

新功能：AI 自动生成字幕（本机 Whisper + Silero VAD 转写、OpenAI 兼容翻译，本地 Ollama / 云端 DeepSeek）；前台 Service 两阶段进度；分段缓存与断点续做。

---

# 1.0.3

## 2026-06-22

DLsite 多任务下载队列（并发上限 2）；总进度卡；作品行多态（下载中/排队/暂停/失败/完成）；内容级增量下载；下载管理 sheet。

---

# 1.0.2

## 2026-06-22

设置页版本号显示 + GitHub Release 检查更新 + 应用内 APK 下载（前台进度条 + 系统安装器）。

---

# 1.0.1

## 2026-06-22

基线：import 清理、Java 迁移、ViewModel 测试、Media3 媒体会话验证、DLsite 下载异常恢复等基线质量项。单模块 `:app`、手写 `AppContainer`、暖夜琥珀 UI 方向保持不变。
