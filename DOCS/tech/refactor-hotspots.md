> 当前软件版本：1.6.1（versionCode 22）
> 文档更新日期：2026-06-25

# 重构热点清单

---

## 已完成

| 项 | 内容 |
|---|---|
| **A1** | `DbIo.runBlocking` 桥接层已删，仓库读写全 `suspend` + `Dispatchers.IO` |
| **A2** | 命令式 `syncStateFromRepository/refresh` DB 拉取已清，Flow 是唯一状态源 |
| **A3** | UI 主线程阻塞写已消除（随 A1/A2） |
| **A4** | 全局 `PlaybackServiceState` object + 19 参 `publish` 已删，自定义状态走 `sessionExtras` |
| **A5** | 全局可变 bus 清零，4 个 StateStore 全进 `AppContainer` DI |
| **A6** | 仓库接口双轨 API 清除，读全 `Flow`，写全 `suspend` |
| **A7** | `DlsiteRepository` 队列 API 已摘除，`RoomDlsiteRepository` 只协调本地/远程/下载状态；`DlsiteDownloadQueueRepository` 显式注入 VM、Service dependencies 与 blocking adapter |
| **C1** | 全仓 wildcard `.*` import 清零，含 `ExampleInstrumentedTest.kt` 的 `org.junit.Assert.*` |
| **C2** | `app/src` Java 清零（main + test 皆 0） |
| **C3** | nullable 参数收紧（仓库/model/DTO/remote 全链路），`!!` 清零，`@JvmField/@JvmStatic/@JvmOverloads` 清零 |
| **C4** | `SubtitleCueScheduler` 纯计算已抽出；UI ticker 与 cue-boundary scheduler 定性为**不合并** |
| **C6** | 全限定 coroutine 类型随 C1 清除 |
| **C7** | DLsite ziptree/content-file DTO 已分层为 `domain.model.DlsiteZiptree` / `DlsiteContentFile`，remote parser 只解析并产出 domain 模型，下载规划不再依赖 parser 内部 DTO |
| **S1** | 主题模式持久化与分层已完成：`AppThemeMode` 位于 `domain.model`，`SettingsRepository.themeModeFlow` 读取 `app_settings.app_theme_mode`，`AppUi` 不再参与 data 层持久化 |
| **D2** | 文件导入下沉 `LibraryFileImportUseCase`（含 `LibraryImportFiles<T>` 端口/适配器），VM 已不碰 `DocumentFiles` |
| **D4** | 巨型 Screen 文件拆分按 500 行标准已达成，最终复审见下方；后续不为 400 行目标继续拆 |

---

### D4 · [P2] 巨型 Screen 文件（已达成）

完成标准已调整为单个大文件不超过 500 行，不再要求 400 行。最终复审通过行数：

| 文件 | 行数 |
|---|---:|
| `ASMRPlayerApp.kt` | 407 |
| `AppDialogHost.kt` | 370 |
| `AppScaffold.kt` | 102 |
| `AppTabHost.kt` | 155 |
| `SettingsScreen.kt` | 252 |
| `SettingsRows.kt` | 456 |
| `SettingsAiPage.kt` | 403 |
| `SettingsUpdateSection.kt` | 267 |
| `PlayerScreen.kt` | 144 |
| `PlayerControls.kt` | 300 |
| `PlayerMoreMenu.kt` | 386 |
| `PlayerTransport.kt` | 267 |
| `LibraryScreen.kt` | 260 |
| `LibraryRows.kt` | 444 |
| `LibrarySheets.kt` | 422 |
| `DlsiteScreen.kt` | 116 |
| `DlsiteRows.kt` | 354 |
| `DlsiteDialogs.kt` | 210 |
| `SleepScreen.kt` | 162 |
| `SleepComponents.kt` | 366 |

结论：D4 按当前 500 行标准已达成；`app/src/main/java` Kotlin 源文件无超过 500 行项；后续只在功能变化或可维护性需要时继续拆分，不再为了 400 行目标继续拆。

---

## 待完成

### D1 · [P2] 三份边界快照类

`PlaybackPresentationState.kt` 协调器已落地，负责快照聚合与 `PlaybackUiState` 投影。但 `PlaybackServiceSnapshot`（`PlaybackServiceState.kt`）/ `PlaybackControllerSnapshot`（`PlaybackCommandClient.kt`）/ `PlaybackUiState`（`PlaybackPresentationState.kt`）仍是三份独立数据结构，字幕/sleep 状态仍要经两次边界转换才到 UI。

方向：评估是否合并 `PlaybackServiceSnapshot` 与 `PlaybackControllerSnapshot`，或进一步将 `PlaybackPresentationState` 接管全部映射逻辑；目前三快照格局功能正确，优先级低于 D3/D4。

---

### D3 · [P2] `ASMRPlayerApp.kt` 组合根（`ASMRPlayerApp.kt` 407 行 + `AppDialogHost.kt` 370 行 + `AppScaffold.kt` 102 行 + `AppTabHost.kt` 155 行）

第一刀已完成：顶层 dialog/sheet 渲染已抽到 `AppDialogHost.kt`，`ASMRPlayerApp.kt` 只保留一次 `AppDialogHost(...)` 调用。

第二刀已完成：`AppScaffold.kt` 抽出根 `UiProbeHost` / `Box` / `Scaffold` / `PageHeader` / `BottomPlaybackArea` / `content` / `dialogHost` slot；`AppTabHost.kt` 抽出 MEDIA / SETTINGS / SLEEP / DLSITE tab 分发。

剩余问题：`ASMRPlayerApp.kt` 仍承担 VM collect、`LaunchedEffect`、ActivityResult launcher、`BackHandler`、dialog state、derived state、`AppDialogHost` wiring 与业务 callback；dialog/sheet、根 scaffold 与 tab 分发已移出，但组合根仍偏向状态/副作用编排中心。

方向：后续优先拆 launcher / state / effect host，并评估哪些局部状态可以继续下沉为 `rememberSaveable`。纯 UI 壳已完成，不再把导航/底栏作为 D3 主要问题。

---

### C5 · [P3] 异常处理抽样（非阻塞）

第一刀已完成：下载/远程路径静默异常已补 Log 线索；远程转写轮询 `IOException` 会通过 `detailText` 显示「远程状态请求失败，稍后重试」，控制流保持重试/超时策略不变；`DlsiteContentRemote.kt` 字幕下载降级 `catch` 现在会先重抛 `InterruptedIOException`，避免暂停/删除中断被普通字幕 `IOException` 降级吞掉，普通字幕失败仍 `Log.w` 并继续无字幕导入。

本轮补充：`DlsiteViewModel.kt` 取消传播已修补，`pauseAllDownloads` 单项失败不再中断全部；`DlsiteDownloadService.kt` 运行中/排队/普通删除只有物理删除成功才清状态，删除失败会 failed 并收尾，pause/delete 接管与终态提交收进同一把锁，stopRequest 下的晚到进度回调会被忽略；DocumentFiles 权限/非法 URI 增加降级日志；`DlsiteCoverRemote.kt` 与 `DlsiteContentRemote.kt` 中断重抛；`PlaybackCommandClient.kt` 的 controller 构建失败已记录日志并清理 `controller` / `controllerFuture`，允许后续 `connect` 重试；`AiSubtitleGenerationService.kt` job 生命周期加锁，避免启动/收尾竞态；`AudioPcmDecoder.kt` 补齐 configure/start 失败释放路径。

上一轮统计仍记录 82 处 `catch`、31 处 `runCatching`，本轮只修高风险样本，没有把 C5 清零。继续重点抽查网络与下载路径的 `runCatching{}.getOrNull()`。

---

## 不必再动

- Room 迁移全为 `ALTER/CREATE`，无 `fallbackToDestructiveMigration`
- `coverUrl`（远程 URL）与 `coverUri`（本地缓存 URI）是两列不同用途，保留
- `DlsiteDownloadBlockingAdapter` 的 `runBlocking`：Java 下载 Service 互操作边界，后台线程，无 ANR 风险，随 C2（已完成）一起可考虑迁 Kotlin 协程，但不紧急

> 按 AGENTS.md 约定，本文定位问题与方向；落地计划归档进 `DOCS/TODOList.md`。
