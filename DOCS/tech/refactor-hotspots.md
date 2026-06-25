> 当前软件版本：1.6.1（versionCode 22）
> 文档更新日期：2026-06-26

# 重构热点清单

> 全部热点已落地，1.6.1 重构清单收口。最新一轮 `:app:compileDebugKotlin` + `:app:testDebugUnitTest`（202 项）全绿，`git diff --check` 干净。

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
| **D3** | 组合根 `ASMRPlayerApp.kt` 降至约 340 行，launcher / state / effect host 已抽出（`AppActivityLaunchers` / `AppRootState` / `AppScaffold` / `AppTabHost` / `AppDialogHost`） |
| **D4** | 巨型 Screen 文件拆分按 500 行标准已达成，最终复审见下方；后续不为 400 行目标继续拆 |
| **D1** | 评估结论：三份快照**不合并**（service=IPC 可序列化契约 / controller=客户端叠加实时播放字段 / uiState=渲染投影，是三个真实边界）；重复的 `connected && serviceSnapshot.connected` 判断已抽成 `activeServiceSnapshotOrNull()` |
| **C5** | 网络/下载/播放高风险路径已补强：取消传播、批量暂停不中断、删除收尾、中断重抛、controller 重试、`SceneContextBuilder` 情景卡降级日志；单测开 `unitTests.isReturnDefaultValues` 以兼容生产 `Log` 调用 |

---

### D4 · [P2] 巨型 Screen 文件（已达成）

完成标准已调整为单个大文件不超过 500 行，不再要求 400 行。最终复审通过行数：

| 文件 | 行数 |
|---|---:|
| `ASMRPlayerApp.kt` | 339 |
| `AppDialogHost.kt` | 370 |
| `AppScaffold.kt` | 102 |
| `AppTabHost.kt` | 155 |
| `AppActivityLaunchers.kt` | 106 |
| `AppRootState.kt` | 46 |
| `SettingsScreen.kt` | 252 |
| `SettingsRows.kt` | 456 |
| `SettingsAiPage.kt` | 403 |
| `SettingsUpdateSection.kt` | 267 |
| `PlayerScreen.kt` | 144 |
| `PlayerControls.kt` | 300 |
| `PlayerMoreMenu.kt` | 386 |
| `PlayerTransport.kt` | 267 |
| `LibraryScreen.kt` | 260 |
| `LibraryRows.kt` | 495 |
| `LibrarySheets.kt` | 422 |
| `DlsiteScreen.kt` | 116 |
| `DlsiteRows.kt` | 399 |
| `DlsiteDialogs.kt` | 237 |
| `SleepScreen.kt` | 162 |
| `SleepComponents.kt` | 366 |

结论：D4 按当前 500 行标准已达成；`app/src/main/java` Kotlin 源文件无超过 500 行项；后续只在功能变化或可维护性需要时继续拆分，不再为了 400 行目标继续拆。

---

## 收口说明

- **D1**：评估完成，结论是**不合并**三份快照。`PlaybackServiceSnapshot`（`sessionExtras` IPC wire DTO，必须可序列化）/ `PlaybackControllerSnapshot`（客户端把 IPC 数据叠加 `isPlaying`/`position`/`duration` 等不可序列化的实时播放字段）/ `PlaybackUiState`（Compose 渲染投影）是三个真实边界，强行合并会破坏 IPC 契约。重复的有效 service snapshot 判断已抽成 `activeServiceSnapshotOrNull()`；sleep remaining 由 Controller 客户端按 `elapsedRealtime` 派生，不走 wire。
- **D3**：launcher（`AppActivityLaunchers`）、根 mutable state + `BackHandler`（`AppRootState`）、根 scaffold/tab/dialog（`AppScaffold`/`AppTabHost`/`AppDialogHost`）均已抽出，`ASMRPlayerApp.kt` 约 340 行。剩余的 VM collect / `LaunchedEffect` / derived state 属正常组合根职责，不再继续拆。
- **C5**：网络/下载/播放高风险路径已补强（取消传播、批量暂停不中断、删除收尾、中断重抛、controller 重试、下载 worker 中断识别 `DlsiteDownloadWorkerErrors`、`SceneContextBuilder` 情景卡降级日志）。单测开 `unitTests.isReturnDefaultValues = true` 以兼容生产 `Log` 调用。剩余宽泛 `catch`/`runCatching` 多为字段级解析与可选缓存兜底，均会规范重抛或退化，不计入高风险；普通集合 `getOrNull(index)` 不计入 C5。

---

## 不必再动

- Room 迁移全为 `ALTER/CREATE`，无 `fallbackToDestructiveMigration`
- `coverUrl`（远程 URL）与 `coverUri`（本地缓存 URI）是两列不同用途，保留
- `DlsiteDownloadBlockingAdapter` 的 `runBlocking`：Java 下载 Service 互操作边界，后台线程，无 ANR 风险，随 C2（已完成）一起可考虑迁 Kotlin 协程，但不紧急

> 按 AGENTS.md 约定，本文定位问题与方向；落地计划归档进 `DOCS/TODOList.md`。
