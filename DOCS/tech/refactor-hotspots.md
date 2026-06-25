> 当前软件版本：1.6.1（versionCode 22）
> 文档更新日期：2026-06-25
> 性质：阶段性架构/代码/设计问题审查，定位重构热点，供后续按 Phase 推进

# 重构热点清单（refactor-hotspots）

## 总体判断

当前是一套 **「响应式骨架 + 命令式 runBlocking 内核」的半成品混合架构**。

上一轮重构已落地相当一部分：Media3 `MediaSession`/`MediaController` 取代了 `MediaPlayer`，仓库接口化 + 手写 DI（`AppContainer`）已建立，Room DAO 暴露 `Flow`，UI 走 `collectAsStateWithLifecycle`，500ms 全量 UI 轮询和 `allowMainThreadQueries` 已移除。

但**旧的命令式 pull、主线程阻塞、全局可变状态没有删干净**，与新引入的 `Flow` 响应式并存。结果是大量「一份数据两条路径」「一个状态两个来源」的冗余与潜在冲突。最核心的退化是：`allowMainThreadQueries` 表面去掉了，却被 `DbIo.runBlocking` 换了个马甲——主线程阻塞 DB 的本质问题仍在。

下面按 **架构 (A) / 代码 (C) / 设计 (D)** 三层、配优先级（P0 正确性/ANR > P1 架构一致性 > P2 可维护性 > P3 卫生）列出每个需要重构的位置，均附 `file:line` 证据。

---

## A. 架构层

### A1 · [P0] `DbIo.runBlocking`：主线程阻塞 DB 的「伪响应式」内核

- **位置**：`data/DbIo.kt:25-46`；被仓库调用 **37 处**（`DlsiteRepository` 20、`LibraryRepository` 15、`SettingsRepository` 2）。
- **现状**：仓库的写/读用 `DbIo.run { runBlocking(Dispatchers.IO) { ... } }` 把挂起操作变回**同步阻塞调用**，还用 `ThreadLocal<Boolean>` 检测嵌套以躲 `runBlocking` 死锁。
- **问题**：`runBlocking` 会阻塞**调用线程**直到 IO 完成；从主线程调用即等价于主线程同步等 DB。协程被「用了又退化回阻塞」，`ThreadLocal` 防嵌套本身就是架构错误的信号。
- **影响**：ANR 风险（见 A3）、协程取消/背压全失效、与 `Flow` 响应式路径冲突。
- **方向**：仓库读写一律改 `suspend` + `Dispatchers.IO`；读统一走 DAO 的 `Flow`。删除 `DbIo` 整个桥接层。这是本轮重构的**根因**，应最先做。

### A2 · [P0] 双数据源并存：`Flow` 响应式与命令式 `get/refresh` 重复拉取

- **位置**：
  - `presentation/LibraryViewModel.kt:82-93`（`observeLibrary()` 响应式）vs `:76-80 syncStateFromRepository()`（命令式同步读），且**每个写操作后都手动再 `syncStateFromRepository()`**（`:116/125/131/145/151/157/...`）。
  - `presentation/DlsiteViewModel.kt:68-95`（`observeRepository()` combine 4 Flow）vs `:97-112 refresh()`（命令式同步读）。
- **证据**：`worksFlow` 与 `getWorks()` 查的是**同一张表** `dlsiteDao.works()`（`DlsiteRepository.kt:102` 与 `:130-132`）。
- **问题**：Room `Flow` 在写入后**本就会自动 emit**，命令式 `refresh()/sync` 是完全冗余的二次拉取，两条路径竞争写同一个 `_state`，且命令式那条还是主线程阻塞（A1）。
- **方向**：删除所有 `syncStateFromRepository()/refresh()` 命令式读路径，只保留 `Flow` 订阅作为**唯一**状态来源。

### A3 · [P0] UI 主线程直接调用同步阻塞写方法 → ANR

- **位置**：`ui/ASMRPlayerApp.kt` 的 Composable 回调里直接调用：`addAudioUris`(:129)、`importFolder`(:139)、`handleSubtitleUri`(:161)、`createPlaylist`(:556)、`deletePlaylist`(:580)、`renameTrack`(:592)、`removeTrack`(:605)、`moveTrack`(:617)。这些 VM 方法是普通 `fun`（非 `suspend`），内部走 A1 的 `runBlocking`。
- **最危险**：`LibraryViewModel.importFolder/addAudioUris`（`:187-238`）在主线程**同步遍历 SAF**、逐条 `DocumentFiles.audioDurationMs()` 解析时长、**逐条** `addTrack`（每条一次 `runBlocking`）。导入大文件夹必然 ANR。
- **其次**：`DlsiteViewModel.refresh()`（`:99-103`）对每个 work **同步阻塞**查一次 contents（N+1 次 `runBlocking` DB 查询，全在主线程）；`LibraryViewModel.init`（`:70-74`）构造期就主线程阻塞读 DB。
- **方向**：随 A1/A2 一并解决——写操作走 `viewModelScope.launch`，IO 不回主线程。

### A4 · [P1] 播放状态双源：全局 `PlaybackServiceState` vs 注入的 `PlaybackCommandClient`

- **位置**：`playback/PlaybackServiceState.kt:62-116`（全局 `object`，`publish()` 是 **19 参数**巨型方法，Service 通过它 push）；`playback/PlaybackCommandClient.kt:54-61`（注入，`MediaController` 拉 `snapshots`）。
- **问题**：`isPlaying / durationMs / positionMs` 三个字段**两个源都有**，`PlaybackViewModel` 同时 collect 两者（`:195-212`），并在 `currentIsPlaying/currentDurationMs/currentPositionMs`（`:286-297`）手动二选一 fallback。Media3 架构里 `MediaController` 本应是**唯一**播放状态源。
- **方向**：字幕/playlist/sleep 等自定义状态通过 `MediaSession` 的 `sessionExtras` / custom command 下发，统一从 `MediaController` 出；删除全局 `PlaybackServiceState` 与 19 参数 `publish`。

### A5 · [P1] 全局 static StateFlow bus（4 个）+ service-locator（4 处）：绕过 DI 的全局可变状态

- **位置**：全局 `object` + `MutableStateFlow` 证据已部分收敛：AI 字幕任务状态已统一为 `data/ai/AiSubtitleTaskStateStore` 并由 `AppContainer` 持有；更新下载和 DLsite 下载状态也已改为注入的 store。剩余 service-locator 证据集中在 `AppGraph.container(...)` 的前台 Service / Activity 依赖获取路径（如 `PlaybackService`、`AppUpdateDownloadService`、`AiSubtitleGenerationService`、`MainActivity`）。
- **问题**：全局可变单例任意处可读写，破坏单一数据流与可测试性，是 memory 记录的「C4 数据源分裂」的残留形态。
- **方向**：这些 bus 收敛为仓库持有的 `StateFlow` 经 `AppContainer` 注入；Service 通过显式依赖而非全局图获取协作者。

### A6 · [P1] 仓库接口「双轨 API」：`Flow` 与同步 `get` 方法并存

- **位置**：`data/LibraryRepository.kt:27-45`、`data/DlsiteRepository.kt:48-61`、`data/SettingsRepository.kt`。接口同时声明 `xxxFlow` 与 `getXxx(): T`。
- **问题**：把「响应式订阅」和「一次性命令读」两种范式同时暴露给上层，直接诱发 A2 的双路径。
- **方向**：接口只保留 `Flow`（读）+ `suspend`（写）；删除所有同步 `getXxx()`。

### A7 · [P2] `DlsiteRepository` god object：网络 + 持久化 + 两套下载状态机

- **位置**：`data/DlsiteRepository.kt`（576 行，~40 个方法）。一个类同时承担：网络（`fetchPurchasedWorks/fetchDownloadOptions/downloadCover/hasLoginCookie`）、DB 读 + Flow、**下载队列状态机**（`enqueueDownload`、`pending/activeDownloadQueueTasks`、`resetRunningDownloadQueue`、`markDownloadQueueTask{Running,Completed,Failed,Paused,Canceled,Pending}`、`pause/cancelQueuedDownload` 等 13+ 方法）、**作品下载状态机**（`markDownloading×2/markQueued/markPaused/markDownloaded/markImported/markFailed/markInterruptedDownloads`）。
- **方向**：拆分为 `DlsiteRemoteSource`（网络）/ `DlsiteLocalStore`（DB+Flow）/ `DownloadQueueRepository`（队列状态机），单一职责。

---

## C. 代码层

### C1 · [P0-卫生] 16 行「全家桶 wildcard import」废弃块，覆盖 36/70 文件

- **位置**：`DbIo.kt:3-18`、`LibraryRepository.kt:3-18`、`PlaybackCommandClient.kt:3-18`、`PlaybackViewModel.kt:3-18` 等共 **36 个文件**顶部同一坨：
  ```
  import io.github.summerdez.asmrplayer.R
  import io.github.summerdez.asmrplayer.data.*
  ... // ui.*  playback.*  presentation.*  di.* 等全家桶，含 import 自己所在的包
  ```
- **问题**：绝大多数未使用（如 `DbIo` 只需 `runBlocking/Dispatchers`），且让 **data 层 `import ui.*`、domain 层 `import ui.*`**，制造跨层耦合假象、可能掩盖真实非法依赖。这是「大量废弃代码」的**最大单一来源**，疑似某次批量移动文件自动生成。
- **方向**：全仓库一次性清理为精确 import（IDE Optimize Imports / ktlint）。低风险高收益，可独立先做。

### C2 · [P1] Java 文件已完成 Kotlin 化（Java 0 / Kotlin 105，0%）

- **位置**：`app/src/main/java/` 下主源码 Java 文件已清零；核心 model、`data/remote`、`data/files/DocumentFiles`、`data/download`、`playback/`、旧 `ui/theme` 与 `DlsiteLoginActivity` 均已迁移到 Kotlin，并保留迁移期 Java/ABI 兼容入口。
- **问题**：语言统一阶段完成，但部分迁移期 API 仍保留 Java null 宽松边界、`@JvmField` 字段 ABI 和平台值兼容写法，尚未完全发挥 Kotlin 空安全和数据建模能力。
- **方向**：C2 已完成；下一步转入 C3，按调用链收紧 nullable API，再单独做全仓 import cleanup 和迁移期兼容点清理。

### C3 · [P2] 接口/方法 nullable 参数泛滥 + 防御式 `if-null-return`（Java 移植味）

- **位置**：`LibraryRepository`/`DlsiteRepository` 几乎每个方法签名都是 `String?`，方法体开头 `if (xxx.isNullOrEmpty()) return`（如 `LibraryRepository.kt:101-104/112-114/122-124/...`、`DlsiteRepository` 多处 `workId: String?`）。
- **问题**：Kotlin 侧本可用非空类型在编译期挡住，现在把校验下沉到运行时、每个方法重复。源头是 C2 的 Java model/调用约定。
- **方向**：随 C2 迁移把参数收紧为非空类型，删除重复防御分支。

### C4 · [P2] 字幕/位置「双 ticker」重复轮询

- **位置**：`playback/PlaybackService.kt:34-40`（`Handler.postDelayed` 每 250ms `SUBTITLE_REFRESH_MS`，`:570`）刷字幕；`playback/PlaybackCommandClient.kt:206-213`（协程 ticker 每 250ms `CONTROLLER_POSITION_REFRESH_MS`，`:239`）拉播放位置。
- **问题**：两处独立的 250ms 轮询都为「按播放位置刷新」，职责重叠、各刷各的。注：因 ExoPlayer 不 push 位置/字幕，按位置轮询本身**合理**，问题在**两套并存**。
- **方向**：A4 统一状态源后，位置/字幕刷新合并为单一驱动。

### C5 · [P3] 异常处理待审：35 处 `catch` / 30 处 `runCatching`

- **现状**：全仓库 35 处 `catch (Exception/Throwable)`、30 处 `runCatching`（无 `printStackTrace`、仅 1 处 `!!`，这两点是好的）。
- **问题**：数量偏多，存在 `runCatching{}.getOrNull()` 静默吞错误、掩盖失败的可能。
- **方向**：抽样审查，确认是否有应上报/重试却被吞掉的异常（重点看网络与下载路径）。非阻塞项。

### C6 · [P3] `PlaybackViewModel` 用全限定 `kotlinx...MutableStateFlow`

- **位置**：`presentation/PlaybackViewModel.kt:59-60`。
- **问题**：因 C1 的 wildcard 块没 import 具体类，写代码时图省事用全限定名——是 C1 的副作用证据，随 C1 清理一并消除。

---

## D. 设计层

### D1 · [P1] 三个高度重叠的播放数据类，字段靠手工搬运

- **位置**：`PlaybackUiState`（`PlaybackViewModel.kt:26-47`，**32 字段**）、`PlaybackServiceSnapshot`（`PlaybackServiceState.kt:23-44`，**20 字段**）、`PlaybackControllerSnapshot`（`PlaybackCommandClient.kt:45-52`，6 字段）。
- **问题**：字幕(`previous/current/nextSubtitle/subtitleLines/subtitleIndex`)、playlist、sleep 等字段在多个类重复定义，`emitState()`（`:245-270`）逐字段手工搬运三个源 → 极易漏改/不一致。
- **方向**：A4 统一状态源后收敛为一份领域状态 + 一个 UI 映射，消除中间快照类。

### D2 · [P2] ViewModel 职责过载：直接编排文件 IO + 命令式返回业务结果给 UI

- **位置**：`LibraryViewModel.importFolder/addAudioUris/handleSubtitleUri`（`:187-278`）直接依赖 `DocumentFiles`、`android.content.Intent`、`Context` 做 SAF/文件导入编排；方法**返回** `FolderImportResult/SubtitleBindingResult/MoveTrackResult` 让 UI 据此 `toast`（命令式交互，而非状态驱动）。
- **问题**：VM 同时管 UI 状态 + 文件导入业务 + 选择态（`librarySelection`/`pendingLibrarySelection`），违背单一职责；命令式返回值使 UI 与 VM 时序耦合。
- **方向**：文件导入下沉到 repository / use-case；结果通过 `state` 或一次性 `SharedFlow<Event>`（如 DLsite 已有的 `events`）表达。

### D3 · [P2] `ASMRPlayerApp.kt` 巨石组合根（701 行）

- **位置**：`ui/ASMRPlayerApp.kt`。单个 `@Composable` 接收 **6 个 VM + 6 个回调**，collect 全部 6 个 VM state + 注入的 AI 字幕任务状态 store 对应 ViewModel，并持有全部顶层导航状态（`playerOpen/queueOpen/downloadManagerOpen/...`）、全部对话框、全部 `ActivityResult` launcher、toast 编排、`AiSettingField` enum。
- **方向**：拆为 `AppScaffold`（导航/底栏）+ 各 Tab 宿主 + 独立的 dialog host / activity-result host，状态用 `rememberSaveable` 局部化或上提到导航层。

### D4 · [P2] 巨型 Screen 文件

- **位置**：`DlsiteScreen.kt` 1559 行（22 个 `@Composable`）、`LibraryScreen.kt` 1336、`SettingsScreen.kt` 1296（17）、`PlayerScreen.kt` 980。
- **问题**：单文件混了页面骨架 + 多个对话框/Sheet + 行级子组件 + 局部状态，难定位、易冲突（UI Probe 埋点也集中于此）。
- **方向**：按 section / dialog / row 拆分到子文件，单文件目标 < 400 行。

---

## 已改善 / 不必再动（避免重复劳动）

- ✅ Media3 `MediaSession`/`MediaController` 已替代 `MediaPlayer`（原 C3 已解决）。
- ✅ `allowMainThreadQueries` 已移除——但被 `DbIo.runBlocking` 取代，本质问题转移到 **A1**，仍需处理。
- ✅ Room 迁移规范：`MIGRATION_1_2 … 5_6` 全为 `ALTER/CREATE`，无 `fallbackToDestructiveMigration`（原「破坏性迁移」已修，`AsrmDatabase.kt:54-123`）。
- ✅ 500ms 全量 UI 轮询已删，UI 走 `collectAsStateWithLifecycle`。
- ℹ️ `dlsite_works` 的 `coverUrl`（远程 URL）与 `coverUri`（本地缓存 URI）**并非**废弃冗余列，用途不同（`DlsiteDao.kt:35-36`、`EntityMappers.kt:46-47`），保留。

---

## 建议推进顺序（Phase）

1. **Phase A（卫生，低风险先行）**：C1 清 wildcard import → C6 自然消除。可独立成一个 PR，便于后续 diff 干净。
2. **Phase B（根因，P0）**：A1 删 `DbIo` → 仓库读写转 `suspend`/`Flow`（A6）→ 删命令式 `refresh/sync`（A2）→ UI 写操作进协程（A3）。一条主线，解决 ANR 与双路径。
3. **Phase C（播放状态收敛，P1）**：A4 统一到 `MediaController` → D1 合并数据类 → C4 合并 ticker。
4. **Phase D（全局状态去耦，P1）**：A5 把 4 个 bus + service-locator 收敛进 DI。
5. **Phase E（职责/体量，P2）**：A7 拆 `DlsiteRepository`、D2 下沉文件 IO、D3 拆组合根、D4 拆巨型 Screen。
6. **Phase F（语言统一，P1/P2，可与上并行）**：C2 迁 Java→Kotlin（先 model 与 `data/remote`）→ C3 收紧 nullable。

> 说明：本文是问题定位与方向，不含逐行实现。按 AGENTS.md 约定，落地计划归档进 `DOCS/TODOList.md` 的版本规划，不另立平行文档。
