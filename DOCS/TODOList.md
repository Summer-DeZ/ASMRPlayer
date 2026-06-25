> 当前软件版本：1.6.1（versionCode 22）
> 文档更新日期：2026-06-25

# TODOList

## 1.6.1

### 2026-06-25

架构重构：按 `DOCS/tech/refactor-hotspots.md` 推进 P0 主线程阻塞与双数据源问题，先完成低风险 import 清理和 Repository suspend/Flow 主线。

- [x] 需要做：删除 `DbIo.runBlocking` 桥接层，Repository DB 写入和必要快照读取改为 `suspend` + IO 调度。
- [x] 需要做：资料库和 DLsite ViewModel 删除命令式 `sync/refresh` DB 二次拉取，状态来源改为 Room `Flow`。
- [x] 需要做：文件导入、文件夹导入、字幕绑定、曲目移动、DLsite 下载操作和 AI 设置写入改为 ViewModel coroutine 调用，并用事件回传一次性 UI 结果。
- [x] 需要做：DLsite Java 下载服务/Task 通过明确的后台 blocking adapter 过渡，阻塞边界放在调度线程或下载 worker，不回到 UI 主线程。
- [x] 需要做：本轮触碰的热点 Kotlin 文件清理全家桶 wildcard import，并删除旧 `DbIoTest`。
- [x] 需要做：Phase C 第一切片：`MediaSession.sessionExtras` 承载 playlist identity、字幕、悬浮窗和错误等自定义低频播放状态，`PlaybackViewModel` 优先使用 `PlaybackCommandClient` 快照。
- [x] 需要做：Phase C 设置页切片：`SettingsViewModel` 的悬浮窗状态改为从 `PlaybackCommandClient.snapshots` 派生。
- [x] 需要做：Phase C 睡眠定时切片：`SleepTimerViewModel` 改为从 `PlaybackCommandClient.snapshots` 派生，`sessionExtras` 只下发低频 sleep deadline，客户端本地计算剩余时间。
- [x] 需要做：Phase C 全局播放状态 bus 切片：`PlaybackViewModel` 移除旧 fallback 订阅，`PlaybackService` 只发布 `MediaSession.sessionExtras`，全局播放状态 bus 删除。
- [x] 需要做：Phase C C4 字幕调度切片：`PlaybackService` 固定 250ms 字幕轮询改为 cue 边界定时，悬浮窗和 `sessionExtras` 字幕状态仍由 Service 维护。
- [x] 需要做：Phase C/D1 快照去重切片：`PlaybackServiceSnapshot` 不再重复承载 `isPlaying`、`durationMs`、`positionMs`，播放三字段保留在 `PlaybackControllerSnapshot`。
- [x] 需要做：Phase D/A5 应用更新下载 bus 切片：`AppUpdateDownloadStateStore` 由 `AppContainer` 持有并注入 `SettingsViewModel` 与 `AppUpdateDownloadService`，设置页和前台下载服务共享同一个更新下载状态 store。
- [x] 需要做：Phase D/A5 AI 字幕任务状态 store 命名收束：文件名、类名和文档概念统一为 `AiSubtitleTaskStateStore`，并继续由 `AppContainer` 注入 `AiSubtitleGenerationService` 与 `AiSubtitleTaskViewModel`。
- [x] 需要做：Phase D/A5 DLsite 下载状态 bus 切片：`DlsiteDownloadStateStore` 由 `AppContainer` 持有并注入 `RoomDlsiteRepository` 与 `DlsiteDownloadService`，下载页和 Java 前台下载服务共享同一个多任务下载状态 store。
- [x] 需要做：Phase D/A5 PlaybackService service-locator 切片：新增 `PlaybackPlaylistResolver` 封装播放服务唯一的 `LibraryRepository.getPlaylist()` 读取与异常分类，由 `AppContainer` 构造后注入 Service。
- [x] 需要做：Phase D/A5 前台 Service dependency bundle 切片：`AppContainer` 为更新下载、AI 字幕生成和 DLsite 下载分别提供只含所需协作者的 Service dependencies，Service 不再逐个从容器挑 repository/store/API。
- [x] 需要做：Phase D/A5 PlaybackService dependency bundle 切片：`AppContainer` 提供 `PlaybackServiceDependencies`，`PlaybackService` 通过该 bundle 获取 `PlaybackPlaylistResolver`，不再直接从容器挑 resolver。
- [x] 需要做：Phase E/A7 第一切片：抽出 `DlsiteDownloadQueueRepository` 管理 DLsite 持久下载队列状态机，`RoomDlsiteRepository` 暂时保留队列 API 并委托给新 repository。
- [x] 需要做：Phase E/A7 远程源切片：抽出 `DlsiteRemoteSource` 承接 DLsite 网络 adapter，`RoomDlsiteRepository` 只依赖远程源接口，并保留 `DlsiteApi` 作为 Java 下载服务/Task 兼容门面。
- [x] 需要做：Phase E/A7 第二切片：抽出 `DlsiteLocalStore` 承接 DLsite Room/DB/Flow 本地存储职责，`RoomDlsiteRepository` 只保留协调门面并委托本地、远程、队列和下载状态依赖。
- [x] 需要做：Phase E/D2 文件导入 use-case 第一片：`LibraryFileImportUseCase` 承接音频多选导入和文件夹导入 IO/业务编排，`LibraryViewModel` 保留原 public API 并只负责事件映射。
- [x] 需要做：Phase E/D2 文件导入 use-case 第二片：字幕绑定 IO 编排已下沉到 `LibraryFileImportUseCase`，`LibraryViewModel` 保留原 public API 并只负责 pending target、选中列表刷新和 `SubtitleBound` 事件映射。
- [x] 需要做：Phase E/D2 文件导入 use-case 第三片：封面选择的 SAF 读取权限和播放列表封面写入已下沉到 `LibraryFileImportUseCase`，`ASMRPlayerApp` 不再直接持久化封面 URI 权限。
- [x] 需要做：Phase F 核心 model 小切片：`DlsiteDownloadOption` 迁移为 Kotlin，并通过 `@JvmField` 保持 Java 下载链路的直接字段访问兼容。
- [x] 需要做：Phase F 核心 model 小切片：`TrackItem` 迁移为 Kotlin，并通过 `@JvmField`、`@JvmOverloads` 和 `@JvmStatic` 保持 Java 字段访问、构造器和 `fromJson` 静态入口兼容。
- [x] 需要做：Phase F 核心 model 小切片：`Playlist` 迁移为普通 Kotlin `class`（非 data class），构造器参数接受可空 Java 输入并归一化（`coverUri == null` → `""`），默认 `tracks` 仍保留可变 `ArrayList` 兼容旧 Java 字段语义，通过 `@JvmField` 保持 Java 直接字段访问、`@JvmOverloads` + `(id, name, tracks)` 次构造器覆盖原三种构造方式、`@JvmStatic` + `@Throws(JSONException)` 保持 `fromJson`/`toJson` 兼容，并手写 `copy(...)` 让 Kotlin 侧 `copy(name=…)`、`copy(coverUri=…)` 继续可用。
- [x] 需要做：Phase F `data/remote` Kotlin 化第一切片：`DlsiteClient`、`DlsiteContentProgressListener`、`DlsiteRemoteConstants` 迁移为 Kotlin，保留 `DlsiteClient.LOGIN_URL` 与 `DlsiteRemoteConstants.*` Java 静态字段访问，回调接口仍保持 Java 单方法接口 ABI。
- [x] 需要做：Phase F `data/remote` Kotlin 化第二切片：`DlsiteRemoteFiles`、`DlsiteDownloadJsonParser` 迁移为 Kotlin `object`，通过 `@JvmStatic` 保持 Java 静态工具方法调用形态，并保留文件下载工具的 null 宽松边界、checked exception 与 DLsite 下载 JSON 解析异常包装。
- [x] 需要做：Phase F `data/remote` Kotlin 化第三切片：`DlsiteHtmlParser`、`DlsiteWebvttJsonParser` 迁移为 Kotlin `object`，通过 `@JvmStatic` 保持 Java/Kotlin 静态解析入口，保留 HTML 链接/封面解析、WebVTT JSON 时间轴转换和字幕解析异常包装语义。
- [x] 需要做：Phase F `data/remote` Kotlin 化第四切片：`DlsiteJsonParser` facade 与 `DlsiteHttpClient` 迁移为 Kotlin，保留 Java 静态 parser 入口、嵌套结果类字段/构造器、HTTP 实例方法和 `successfulText` / `bodyString` 静态 helper，并补充 `parseWorkDetail` 可返回 null 的兼容测试。
- [x] 需要做：Phase F `data/remote` Kotlin 化第五切片：`DlsiteLibraryJsonParser`、`DlsiteWorkRemote` 迁移为 Kotlin，保留购买记录 JSON 解析异常包装、可下载音声过滤、封面 URL 归一化、购买列表分块拉取、去重合并和最近浏览 fallback 语义。
- [x] 需要做：Phase F `data/remote` Kotlin 化第六切片：`DlsiteJsonSupport`、`DlsiteCoverRemote` 迁移为 Kotlin，保留 JSON reader/类型转换/escape 行为和封面下载 resolve、sniff、extension、interrupt 语义。
- [ ] 后续做：Phase C 继续评估 `PlaybackCommandClient` 播放位置 ticker 的收敛方式，并收敛重复快照数据类和手工字段搬运。
- [ ] 后续做：Phase D 继续处理其它剩余 service-locator 去耦，逐步收敛到注入的仓库/服务边界。
- [ ] 后续做：Phase E 继续拆分 `ASMRPlayerApp.kt` 组合根和巨型 Screen 文件。
- [ ] 后续做：Phase F 优先迁移核心 model 与 `data/remote/` Java 文件到 Kotlin，再收紧 nullable API。
- [ ] 暂不做：本轮全仓 Optimize Imports；当前工作区已有多处 UI Probe 和界面改动，后续应单独做纯 import cleanup 变更。

### 2026-06-24

缺陷修复：AI 字幕翻译结果中出现片假名时不再直接判定生成失败，改为完成字幕保存和绑定后提示用户人工检查。

- [x] 需要做：把当前版本号同步到 `versionName 1.6.1`、`versionCode 22`，并更新 `DOCS/` 当前版本头。
- [x] 需要做：片假名残留改为完成态质量提示，不阻断字幕写入、绑定和预览。
- [x] 需要做：保留平假名残留、JSON 结构错误、缺句、乱序、重复 id、空译文、网络和文件错误的失败逻辑。
- [x] 需要做：补充单元测试覆盖“片假名结果成功但带 warning”的流程。

## 1.6.0

### 2026-06-24

功能规划：远程 ASR 从同步提交升级为异步任务合约，让 App 在长音频转写期间显示服务端真实进度，而不是只显示上传进度或本地估算。

- [x] 需要做：把当前版本号同步到 `versionName 1.6.0`、`versionCode 21`，并更新 `DOCS/` 当前版本头。
- [x] 需要做：新增 `DOCS/tech/remote-asr-progress-contract.md`，作为 App 与远程 ASR 服务共同遵守的异步任务接口契约。
- [x] 需要做：App 远程转写客户端改为 `POST /transcriptions` 创建 job，读取 `job_id` 后轮询 `GET /transcriptions/{job_id}`。
- [x] 需要做：远程生成进度使用服务端 `status`、`stage`、`progress`、`message`，覆盖排队、ASR 转写、强制对齐、写出结果、失败和取消状态，并保证 UI 进度不回退。
- [x] 需要做：任务完成后调用 `GET /transcriptions/{job_id}/result` 获取 `segments[{id,start_ms,end_ms,text}]`，继续复用现有翻译、缓存、VTT 写出和绑定流程。
- [x] 需要做：取消远程转写时调用 `DELETE /transcriptions/{job_id}`，并把服务端取消结果同步到生成字幕 sheet 与通知。
- [x] 需要做：错误处理按契约映射 `INVALID_REQUEST`、`UNAUTHORIZED`、`QUEUE_FULL`、`MODEL_NOT_READY`、`ASR_FAILED`、`ALIGNMENT_FAILED`、`JOB_NOT_FOUND`、`JOB_NOT_READY`、`RESULT_EXPIRED` 等错误码，保持中文可操作提示。
- [x] 需要做：服务端基线采用 `Qwen3-ASR-0.6B` + `Qwen3-ForcedAligner-0.6B`；若服务端提供其它模型，也必须保持同一 job 状态与 result segments 契约。
- [ ] 待评估：轮询间隔是否根据服务端 `retry_after_ms` 动态调整；默认先按 1-2 秒轮询，进入长时间 ASR 时允许后端建议更慢频率。
- [ ] 暂不做：内置官方公共远程 ASR 地址；1.6.0 仍只支持用户配置自己的局域网或自有云服务。
- [ ] 暂不做：在 ASMRPlayer 仓库内放置远程 ASR 服务端脚手架；本仓库只维护客户端接入和接口契约。

## 1.3.5

### 2026-06-24

缺陷修复：外部导入多条音频后，AI 字幕生成文件名只按清洗后的标题命名；中文/日文标题可能都清洗成类似 `asmr-.mp3`，导致后生成的字幕覆盖前一条曲目已经绑定的 VTT。

- [x] 需要做：AI 字幕生成的 `.vtt` 文件名加入 `trackId`，让不同曲目即使标题清洗后相同也写入不同文件。
- [x] 需要做：保留数据库绑定按 `trackId` + `playlistId` 更新单条曲目的逻辑，不改播放列表/导入数据结构。
- [x] 需要做：补充单元测试覆盖“美咲/诺亚”这类标题清洗后同名但 `trackId` 不同的外部音频。

功能优化：远程转写后端不再在 UI 和错误提示里绑定 Whisper，并把远程服务器地址和端口拆成两个输入框。

- [x] 需要做：设置页远程服务器模式拆分「服务器地址」和「端口」两行，旧的 `baseUrl` 配置读取时自动拆分，保存时再组合成服务 base URL。
- [x] 需要做：远程模型改为「转写模型」且允许留空；在历史同步接口中留空时不发送 `model` 字段，由服务端自行选择默认 ASR 模型。
- [x] 需要做：用户可见错误提示统一为“远程转写服务”，测试连接可用时只返回 `OK`，失败或模型未就绪时显示具体原因。
- [x] 需要做：补充单元测试覆盖旧 `baseUrl` 拆分、地址端口组合和远程模型留空。

## 1.3.3

### 2026-06-23

缺陷修复：应用内更新安装包缓存不会在安装成功后自动清理，可能在 `cacheDir/updates/` 留下已安装 APK 或失败下载的 `.part`。

- [x] 需要做：下载更新前清理 `cacheDir/updates/` 下非当前目标版本的 `ASMRPlayer-*.apk` 和全部遗留 `.part`，保留当前目标版本 APK，不做断点续传。
- [x] 需要做：下载失败、普通异常或取消时删除当前 `.part`，避免失败下载残留临时文件。
- [x] 需要做：新增非导出的 `ACTION_MY_PACKAGE_REPLACED` receiver，应用被更新安装成功后删除当前已安装版本及更旧的 `ASMRPlayer-*.apk` 和全部 `.part`。
- [x] 需要做：应用启动时执行同样的保守清理，防止 receiver 未触发或旧版本遗留；保留比当前安装版本更新的 APK。
- [x] 需要做：保持 `FileProvider` 安装链路不变，拉起系统安装器后不立即删除 APK。
- [x] 需要做：补充更新缓存清理单元测试，覆盖目标版本保留、旧 APK 删除、`.part` 删除、按已安装版本删除小于等于当前版本但保留更新版本。

## 1.3.2

### 2026-06-23

功能优化：应用内检查更新后的 APK 下载迁移到前台服务，切后台后仍能通过通知看到进度和速度。

- [x] 需要做：新增 `AppUpdateDownloadService`，以前台 `dataSync` 服务执行 APK 下载，支持下载和取消 action。
- [x] 需要做：新增“应用更新”通知渠道，下载中通知显示版本号、百分比、速度、已下载/总大小和取消操作；完成/失败更新终态通知，取消移除通知。
- [x] 需要做：新增更新下载状态 store，把 release、状态、字节进度、速度、APK 路径和错误同步给设置页。
- [x] 需要做：`SettingsViewModel` 不再直接在 `viewModelScope` 下载 APK，改为启动前台服务；取消改为发送 cancel intent。
- [x] 需要做：`GitHubAppUpdateRepository.downloadReleaseApk()` 进度回调携带速度，并在协程取消时取消 OkHttp Call，提高阻塞读流时的取消可靠性。
- [x] 需要做：设置页下载卡副文案显示「速度/s · 已下载/总大小」。
- [x] 需要做：补充单元测试覆盖更新下载状态 store 速度计算、取消状态和设置页状态映射。

## 1.3.1

### 2026-06-23

功能优化：DLsite 下载请求后先解析文件目录树，不再只靠版本猜测；用户在下载内容 sheet 中自行选择要下载的目录或根目录音频。

- [x] 需要做：`DlsiteDownloadPlanner` 改为按 `ziptree.json` 中音频文件的目录父路径生成 `DlsiteDownloadOption`，剥掉公共目录前缀，保留目录树中的叶子路径作为可下载内容。
- [x] 需要做：普通嵌套目录（如本編/特典）也作为用户可选内容，不再被合并成默认版本；根目录音频单独显示为「根目录音频」。
- [x] 需要做：下载内容 sheet 标题和说明改为目录树选择，默认不预选全部，提供全选/清空，让用户明确勾选后再加入下载队列。
- [x] 需要做：补充目录树分组单元测试，覆盖叶子目录、公共前缀剥离、普通嵌套目录和根目录音频。

功能优化：DLsite 下载队列改为 Room 持久化队列，保证重启恢复、FIFO 调度和资料库延迟导入。

- [x] 需要做：新增 `dlsite_download_queue` 持久化队列表，Room schema 升至 6；服务重启后把遗留 `running` 任务重置为 `pending`。
- [x] 需要做：下载队列按 FIFO 调度，最多 2 个作品并发；同一 work 的 active `pending` / `running` 入队去重，异常重复 `pending` 不阻塞后续队列。
- [x] 需要做：点击加入下载只持久化入队并启动 service，queued / downloading 状态不刷新 `DlsiteWork` / `DlsiteContent` 的 `updatedAt`，避免 DLsite 作品列表因入队或开始下载立即置顶。
- [x] 需要做：单次选择的所有内容下载成功后统一导入或复用播放列表，再按内容回填 `trackIds`；单个内容包完成时不提前导入资料库，避免半成品可见。

缺陷修复：AI 字幕云端翻译模型应支持任意 OpenAI 兼容模型名，DeepSeek 只是默认预设；此前 `deepseek-v4-pro` 会在读取设置时被当成旧默认值替换回 `deepseek-v4-flash`。

- [x] 需要做：模型设置只在留空时回退 `deepseek-v4-flash`，用户保存的 `deepseek-v4-pro`、`gpt-4.1` 或其它非空模型名都原样保留。
- [x] 需要做：设置页用户可见文案从“云端 DeepSeek”改为“OpenAI 兼容”，DeepSeek 保留为默认接口地址和默认模型。
- [x] 需要做：通用 OpenAI 兼容接口不发送 DeepSeek 专属 thinking 控制字段；只有 DeepSeek 地址或 DeepSeek 模型名才附带关闭 thinking 参数。
- [x] 需要做：补充回归测试，覆盖自定义模型保存和通用 OpenAI 兼容模型请求参数。

## 1.2.5

### 2026-06-23

质量修复：利用 DeepSeek 1M 上下文，让模型在翻译当前批次前看到完整日文字幕背景，但仍保持分批输出和严格对齐校验。

- [x] 需要做：DeepSeek 翻译请求加入完整日文字幕全文只读上下文，作为每批请求的稳定前缀；只允许用于理解全文背景、称呼、语气、术语和音效。
- [x] 需要做：当前输出目标仍限制为待翻译批次，不允许模型翻译全文或输出批次外 id；保留 ordered-lines 行数、顺序、空译和日文假名残留校验。
- [x] 需要做：默认翻译批次从 60 行调整为 80 行；缓存策略版本升级，避免复用旧的短上下文批次缓存。
- [x] 需要做：Ollama 保持精简上下文，不携带完整字幕全文，避免本地小上下文模型失稳。

## 1.2.4

### 2026-06-23

质量修复：AI 字幕翻译过度保留日文短碎片，导致最终中文字幕中出现大量日文；同时成人向作品需要可选的直译提示，避免模型因敏感词自行弱化或省略原文已有内容。

- [x] 需要做：翻译 prompt 从“宁可保留日文”改为“输出中文保守占位”，短碎片、疑似 ASR 误识别和无法确定含义时使用「（听不清）」或「（含混）」等中文表达。
- [x] 需要做：ASMR 非台词声音按音效标签翻译；舔舐、摩擦、衣料/耳边摩擦、呼吸等拟声优先输出「（舔舐声）」「（摩擦声）」这类中文描述，避免硬译成莫名其妙的词。
- [x] 需要做：翻译结果校验禁止 `zh` 中残留日文假名；残留时触发重试/逐句兜底，不写入最终 VTT。
- [x] 需要做：设置页 AI 字幕分组新增“成人内容直译”开关；开启后 prompt 明确允许按原文直译成人向敏感词，但不得添加当前日文没有的成人内容。
- [x] 需要做：翻译缓存签名加入成人内容直译开关，并升级 prompt 版本，避免复用旧的日文残留或弱化译文缓存。

## 1.3.0

### 2026-06-23

新功能：可选远程 ASR 转写后端——手机本地 Whisper 切分+识别太慢，新增「把整段音频交给局域网/云上的 ASR 转写服务处理」的能力，识别在服务端完成，App 仅负责上传音频与接收带时间戳字幕，随后复用既有翻译流程。

定调：

- 瓶颈在 Whisper 识别（encoder/decoder）而非 VAD 切分，故采用**整段压缩音频上传、服务端全包 VAD+识别**；传输小、服务端可用 GPU/faster-whisper 极快、协议简单。
- 远程是**可选**后端，**本地 sherpa-onnx 仍为默认**；与既有「音频只在本机、绝不上传」承诺冲突，故远程模式必须在 UI 与文档明示「音频将上传到你配置的服务器」。
- 服务端是**独立开源项目**；ASMRPlayer 仓库只接入客户端能力和接口契约，不把服务端脚手架放进 App 仓库。
- 旧同步接口规划已被 1.6.0 的异步 job 真实进度合约替代；当前接口以 `DOCS/tech/remote-asr-progress-contract.md` 为准。

App 端接入：

- [x] 需要做：新增 `RemoteWhisperTranscriber`，`AiSubtitleGenerationService` 按设置选择本地/远程后端，复用现有 `SubtitleLine`、分段缓存与后续翻译/绑定流程。
- [x] 需要做：设置页「AI 字幕与翻译」二级页新增「转写后端」分段——本地（默认）/ 远程服务器；远程填写服务器地址、端口、可选 Bearer Token、可选模型名；含「测试连接」（打 `/health`）。
- [x] 需要做：远程转写——v1.3.0 历史实现把曲目整段音频以 `multipart/form-data` 提交到同步端点，解析返回的 `segments[{id,start_ms,end_ms,text}]` 映射为 `SubtitleLine`（时间轴用服务端返回值）；v1.6.0 起当前端点以 `DOCS/tech/remote-asr-progress-contract.md` 为准。
- [x] 需要做：状态与超时——远程转写阶段显示上传、排队、语音识别、对齐、写出结果等阶段文案，并为大文件上传、长时间转写设置足够的连接/写入/读取/总超时；网络、HTTP 和 JSON 结构异常转中文提示。
- [x] 需要做：生成字幕 sheet 的阶段标题随后端变化：本机显示“本机转写”，远程显示“远程转写”，不在阶段卡显示解释性 meta。
- [x] 需要做：分段缓存键加入「后端类型 + 本机模型或远程 baseUrl/model」，避免本地 base 与远程 large 转写结果串用。
- [ ] 后续优化：远程不可达/报错时增加“一键切回本机并重试”入口；v1.3.0 先明确中文提示，用户可在设置页手动切回本机。
- [x] 后续优化：服务端异步任务阶段状态已进入 1.6.0 规划，当前以 `POST /transcriptions` + 轮询 status/result + cancel 合约替代 v1.3.0 同步提交方案。

历史同步接口契约（v1.3.0 归档；当前契约见 `DOCS/tech/remote-asr-progress-contract.md`）：

- [x] 需要做：同步提交端点——入参整段音频文件 + `language`/`task`/`model`（可选 `vad` 开关）；出参 JSON `{segments:[{id,start_ms,end_ms,text}], model, language}`，时间单位、字段名以本契约为准。
- [x] 需要做：分句粒度与本地对齐——契约要求服务端返回的 `segments` 为**完整句粒度，不得被句中停顿切碎**（与本地 1.2.3「相邻近段合并」目标一致）；本地合并逻辑依赖端上 PCM 段、无法复用到远程，故远程的分句完整性由服务端保证，避免本地/远程字幕分句质量不一致。
- [x] 需要做：`GET /health`（存活+模型就绪）、`GET /models`（列可用模型）；鉴权用可选 `Authorization: Bearer <token>`（局域网可关、公网建议开）。
- [x] 需要做：错误约定——统一 JSON 错误体 `{error: msg}` + 合理 HTTP 码，便于 App 转中文提示。

服务端开源项目（独立 repo）：

- [x] 需要做：技术栈 Python + FastAPI + faster-whisper（CTranslate2，CPU/CUDA 自适应）+ 内置 VAD / 相邻片段合并；实现历史同步转写、健康检查和模型列表接口。
- [x] 需要做：部署脚本——`Dockerfile`、`docker-compose.yml`、`run.sh`、`systemd` unit 示例、`.env.example`（端口、模型、设备、token、模型缓存目录）。
- [x] 需要做：README——写明局域网部署、鉴权、模型下载缓存、App 填写方式和接口契约；独立仓库公开与许可证信息后再同步到对外项目说明。

隐私与文档：

- [x] 需要做：更新 `Feature-Design.md` / `README.md`——把转写表述改为本地默认、远程可选且由用户配置服务器；UI 仅保留简短状态。
- [ ] 需要做：本版完成并发布后，在根 `README.md` 写好服务端开源项目的 GitHub 链接（待该 repo 公开，并由人工审核发布信息）。

待确认 / 暂不做：

- [ ] 暂不做：端上 VAD 切分 + 仅上传 PCM 语音段——本版整段上传、服务端全包，PCM 段路线后续按需评估。
- [ ] 暂不做：真正的流式/边播边转实时转写——先一次性「提交-返回」，进度用流式/长轮询或时长估算。
- [ ] 暂不做：在 ASMRPlayer 仓库内创建服务端项目脚手架——服务端独立建仓与开发。
- [ ] 暂不做：内置/官方公共转写服务器——只支持用户自配的局域网/自有云地址，不提供官方后端。

## 1.2.3

### 2026-06-23

质量修复：一句话被 VAD 按句中停顿切成两段，两半各自独立成行并独立翻译，导致两行译文都缺主干、被模型硬猜出错（A 类「切得太碎」）。根源在转写阶段的切分粒度，翻译层 1:1 + 保守策略修不了。

改动前现状：`OnDeviceTranscriber`（`data/ai/OnDeviceTranscriber.kt`）中每个 Silero VAD 语音段直接成为一条 `SubtitleLine`；`buildSileroVad` 的 `minSilenceDuration=0.45s` 偏小，ASMR 耳语句中停顿常超过它而被当成句尾切开。本版在转写阶段做「相邻近段合并」，让 Whisper 看到完整句子。

- [x] 需要做：在 VAD 段产出后、送 Whisper 识别前增加「相邻近段合并」——当 `gap = 下段startMs − 上段真实endMs` 小于合并阈值（默认约 0.6s）且合并后语音时长 ≤ `maxSpeechDuration` 时，把相邻段合并为一段一起识别。
- [x] 需要做：合并在流式 producer 端用「1 段前瞻缓冲」实现——拿到段 N 先暂存，看到段 N+1 再决定与 N 合并或先 flush N；解码结束 `vad.flush()` 后再 flush 末段。保持现有 producer-consumer 流水线、`ensureActive` 取消、进度与 `previewLines` 语义。
- [x] 需要做：时间轴正确性（易踩坑）——合并段 `startMs` 取首段 start，`endMs` 取末段真实 end；注意「首段 start + 合并样本时长」会因丢失中间静音而偏小，建议给 `VadSpeechSegment` 增加显式 `endMs` 字段，识别时用它而非按样本数 `whisperSampleCountToMs` 重算。
- [x] 需要做：把合并逻辑抽成纯函数便于单测，覆盖 gap 在阈值上下、合并触顶 `maxSpeechDuration` 不再合并、单段不合并、连续多段链式合并、合并后时间轴正确。
- [x] 需要做：合并阈值与 `minSilenceDuration` 等参数集中为可调常量，便于真机调参；合并改变行数会让翻译源签名变化、自然触发重译，确认不串用旧分段/翻译缓存。
- [x] 需要做：在生成字幕 sheet 的完成/失败状态提供“重新分片并翻译”入口，强制清除该曲目的分段、情景卡和翻译缓存后重跑本机转写与翻译；普通失败重试仍保留复用缓存的快速路径。
- [x] 已确认：合并段默认不插入等长静音填充，改用显式 `endMs` 保留真实时间轴；如果真机识别停顿感不自然，后续再评估填充路线。
- [x] 暂不做：段内按标点 / Whisper segment 时间戳二次切分（把长段拆成更细句行）——与本版「合并」方向相反，单列后续按需评估。

## 1.2.2

### 2026-06-23

质量修复：AI 字幕可以成功翻译后，短碎片和 ASR 疑似误识别行仍可能被模型按情景卡硬猜，生成“很冷吧”“女孩子”等与当前原文不对应的译文。

改动前现状：v1.2.1 修复了 Android 正则解析崩溃，但 prompt 仍允许模型过度使用情景卡、标题、术语表和后文来补当前行；对 `かせちゃ` 一类短碎片，模型可能把不确定片段臆断成完整中文句子。

- [x] 需要做：翻译 prompt 增加“逐行保守翻译”规则，要求 `zh` 主要来自同一 id 的 `ja`，上下文只能消歧，不能替代当前行。
- [x] 需要做：短碎片、半个词、疑似 ASR 误识别或无法确定含义时，优先保留原日文或给出保守片段，不再硬猜成完整中文句子。
- [x] 需要做：明确禁止当前行不含对应日文词时输出“女孩子”“很冷”等由情景卡或后文带入的内容。
- [x] 需要做：情景卡 prompt 改为只总结标题和字幕中明确出现的信息；`glossary` 的 key 必须是字幕实际出现的日文原词，避免术语表带偏。
- [x] 需要做：升级翻译 prompt 版本和情景卡版本，强制失效旧的坏翻译缓存与旧情景卡缓存。

## 1.2.1

### 2026-06-23

缺陷修复：AI 字幕翻译在 Android 真机上第一句直接失败，提示 `Syntax error in regexp pattern`。

改动前现状：v1.2.0 的 ordered-lines 解析器在 JVM 单测通过，但 Android 运行时正则对未转义的 `}` 更严格，导致解析 `{"lines":[...]}` 前就抛出正则语法错误，并被逐句兜底包装成“第 1 句无法稳定翻译”。

- [x] 需要做：转义 ordered-lines / glossary / item 解析正则中的字面量右花括号，保证 Android 运行时可编译。
- [x] 需要做：放宽单项字段解析，兼容模型返回 `id`/`zh` 字段顺序变化、数字 id 和额外只读 `ja` 字段，但仍严格要求行数、id 顺序、id 唯一和非空 `zh`。
- [x] 需要做：补充单元测试覆盖数字 id、字段乱序和空译文仍失败。

## 1.2.0

### 2026-06-23

功能优化：提升 AI 字幕翻译质量，并把「字幕错位」从概率问题改造成可检测、可自愈的确定性问题。实现行为已归并到 `Feature-Design.md` 和 `README.md`，阶段性技术方案已删除。

改动前现状：翻译以 `{id:译文}` 扁平 map、`chunked(40)` 每批独立请求，情景只有作品标题 `playlist.name`，批间零上下文（1.1.0 声称的「前后文窗口」实际未实现）；解析仅靠 `size == sourceLines.size` 整批重试。接口统一以 OpenAI 兼容 `chat/completions` 为准，本版**不做 Gemini 原生接入**。

总原则与对齐（防错位核心）：

- [x] 需要做：守住「翻译与时间轴解耦」——模型只译文本，绝不输出/感知时间；最终 VTT 完全沿用源行 `startMs/endMs`，prompt 中不出现任何时间信息。
- [x] 需要做：把翻译请求/响应从 `{id:译文}` 扁平 map 改为**有序数组** `{"lines":[{"id","ja"} → {"id","zh"}]}`；保留 DeepSeek `response_format=json_object`，`temperature` 降到 0.2–0.3，`max_tokens` 给足防截断。
- [x] 需要做：极短/拟声段强制 1:1——prompt 写死「每个 id 必须恰好一条 zh，叹息/喘息/拟声/无实义也要给中文拟声或原样保留，不得省略/合并/拆分」；校验允许 `zh == ja` 合法，避免拟声段触发整批重试死循环。

情景注入（质量）：

- [x] 需要做：第一层·全局情景卡——开译前一次预调用，把整篇日文 + 标题 + 单曲名压成结构化情景（scene/speaker/address/tone/glossary），注入之后每批 system；缓存键含 模型 + 标题 + 源文签名，可复用（新增 `SceneContextBuilder`，复用 `AiSubtitleTranslationCache` 机制）。
- [x] 需要做：第二层·术语表一致性——情景卡 `glossary` 随每批下发，保证全片固定称呼/角色名尽量同一译法。
- [x] 需要做：第三层·滑动上下文窗口（补上 1.1.0 未兑现项）——每批附带前 K 句「日文 + 已定中文」+ 后 K 句「日文原文」，标注只读、不翻译、不输出其 id。

批策略与校验自愈（结果正确）：

- [x] 需要做：批大小默认 40 句左右、可上探约 60 句 + 上下文窗口（强模型注意力足够时取较大批以减少调用与窗口重叠，软上限防漏句/串位，靠 §对齐校验 + 逐句兜底兜底）；`AiSubtitleGenerationService.translate` 传入前后窗口与情景卡，`TranslationBatch` 扩字段承载情景卡/glossary/前后窗口。
- [x] 需要做：三层校验——结构（合法 JSON + lines 数组）、对齐（返回 id 序列与本批源 id 序列长度/顺序/唯一/无缺失一致）、内容（zh 非空 + 可选日文残留检测）。
- [x] 需要做：失败自愈——先整批重试（现有逻辑 + 更严格 prompt），连续失败降级**逐句翻译**问题 id（单句 1:1 绝不错位），落实「宁可慢、不可错位」。
- [x] 需要做：翻译缓存签名加入情景卡与批策略版本，避免旧缓存与新流程串用；保留现有批次级断点续做与截断/空响应整批重试。

待确认 / 暂不做：

- [x] 暂不做：动态增量术语学习——本版固定使用情景卡 `glossary` 随每批下发，不在翻译推进中自动合并新术语，避免缓存签名和回放结果变得不确定。
- [x] 暂不做：Gemini 原生 `responseSchema` 接入——本版统一走 OpenAI 兼容端点，强模型用 DeepSeek v4 Pro 等即可。
- [x] 暂不做：接入 DLsite 分类/标签/声优作为情景源——当前 `DlsiteWork` 未存 genre/CV，需扩 parser 与 Room schema，单列后续版本。

## 1.1.3

### 2026-06-23

缺陷修复：DLsite 单内容下载后被导入成两个同名播放列表。

改动前现状：`DlsiteDownloadTask.downloadAndImport` 在每个 content 下载完成后会调用一次 `importPlaylist`，循环结束后又对同一批 `importedAudioFiles` 调一次 `importPlaylist`。当新作品的 `playlistId` 为空时，两次导入都会创建新播放列表，导致同一音频 URI 在 `tracks` 中出现两条记录，但磁盘上音频文件只有一份。

- [x] 需要做：同一下载任务内，第一次导入得到的 `playlistId` 要写回本次任务的 `DlsiteWork` 快照，后续 content 导入和最终汇总都复用同一个播放列表。
- [x] 需要做：真机诊断区分文件重复与数据库重复；确认当前问题是重复导入记录，不是音频文件重复下载。
- [ ] 待评估：提供一次性清理工具，删除历史上已经生成的重复同名播放列表/重复 track 记录。

## 1.1.2

### 2026-06-23

性能优化：加速本机 Whisper 语音识别分片，缩短 AI 字幕转写阶段（阶段①）墙钟耗时。

改动前现状：`WhisperRuntimeTranscriber.transcribeJapanese`（`data/ai/OnDeviceTranscriber.kt`）分三个**串行**阶段——①`AndroidAudioPcmDecoder.decodeToMono16k` 解码为 16k mono PCM；②Silero VAD 流式切段、把所有 `VadSpeechSegment` 收进 `speechSegments` 列表；③全部切完后才进入 `recognizeSegmentsInParallel` 用 2 个 worker 并行识别。整个①②阶段（进度权重 0.2）期间 Whisper worker 完全空转；并发数 `cpuWorkerCount` 写死 `min(2, cores, segmentCount)`，且 worker>1 时每个 `OfflineRecognizer` 固定 `numThreads=1`，最多只吃 2 核，且不区分模型体量（base ≈ 160MB/份，small ≈ 374MB/份）。本版完成两项不影响识别精度的工程优化。

要点 #2：解码/VAD 与识别流水线化（producer-consumer）

- [x] 需要做：把「先全量切段、再并行识别」改为流水线——VAD `onSegment` 产出片段即送入 `Channel<VadSpeechSegment>`，N 个 worker 边解码边消费识别；解码结束 `vad.flush()` 收尾后 `close()` channel。消除①②阶段 worker 空转。
- [x] 需要做：结果收集去掉定长 `arrayOfNulls(segments.size)`（流式下总段数未知）——改用并发安全收集（按到达顺序入 list 或 `Map<index, SubtitleLine>`），最终 `sortedBy { startMs }` 重排并重编号 id，保持与现有 `orderedLines` 一致的输出。
- [x] 需要做：重做进度计算。现在 `totalSamples` 依赖识别前已知的全部片段；流式下改为用解码进度（`durationUs`/已解码时长）驱动 VAD 段产出权重 + 已识别音频时长占比驱动识别权重，保持 `VAD_PROGRESS_WEIGHT`/`RECOGNITION_PROGRESS_WEIGHT` 语义且**进度单调不回退、不跳变**。
- [x] 需要做：保留现有取消（`ensureActive`）、`resultMutex`、`previewLines` 实时预览和异常语义；空结果仍抛「未识别到可用语音片段」。

要点 #3：识别并发数 / 线程数按模型与设备自适应

- [x] 需要做：把并发策略从写死的 `min(2, cores)` 改为纯函数 `(modelSpec, availMemBytes, cores, segmentCount) -> (workerCount, threadsPerWorker)`，便于单测；通过 `ActivityManager.MemoryInfo`（`availMem` / `lowMemory` / `totalMem`）估算可并行加载的模型份数（每份内存量级 ≈ encoder+decoder 文件大小，留安全余量）。
- [x] 需要做：策略目标——base 模型在多核充裕内存设备放宽到 3–4 worker（或 2 worker × 2 threads）；small 模型维持 1–2 worker，低内存设备降到 1 worker × 多线程，**绝不因并发加载多份模型而 OOM**；约束 `workerCount × threadsPerWorker ≤ cores`，单 worker 时给足线程、多 worker 时线程数 = `max(1, cores / workerCount)`。
- [x] 需要做：为该纯函数补单元测试，覆盖 base/small × 高/低可用内存 × 不同核数 × segmentCount=0/1/多。

验证：真机分别用 base 与 small、可读本地音频，记录实时率（RTF）、峰值内存、温升、失败率、字幕准确率与 UI 流畅度；重点回归低内存设备不 OOM、进度条不回退、取消即时生效、最终时间轴与原 VAD 切段一致。

复审跟进（待做）：

- [x] 需要做：给 `planWhisperRecognitionConcurrency` 的 `threadsPerWorker` 加上限（如 `coerceAtMost(4)`，4~6 间按真机择优）——当前单 worker 时 `numThreads` 会顶到核数（8 核→8），而旧实现把 ONNX 线程 `coerceIn(1,4)`；ONNX Runtime 在手机 big.LITTLE 上 intra-op 线程过 4~6 收益递减甚至倒退。改完同步修正对应单测里 `threadsPerWorker` 的期望值。
- [x] 需要做：真机校准内存系数 `modelCopyBudgetBytes`（当前 `模型字节 × 2 + 128MB/份`）——用 base@4worker、small@2worker 实测常驻峰值 RSS，按结果校正系数并确认低内存设备不 OOM。PLG110 真机实测：small@2worker 峰值约 PSS 2,321,950 KB / RSS 2,404,656 KB；base@4worker 峰值约 PSS 2,745,282 KB / RSS 2,893,952 KB。已按实测调整为 compact 模型 `encoder+decoder × 3 + 96MiB`、large 模型 `encoder+decoder × 2 + 192MiB`，使同类可用内存下 base 收敛到 3 worker、small 保持 2 worker。
- [ ] 待评估：状态更新热路径优化——转写状态在每次解码回调/产段都持 `resultMutex` 且 `previewLines` 全量 sort+map；结果集变大后考虑节流或缓存预览，降低与 worker 争锁与重复排序开销（功能无误，纯性能）。
- [ ] 待评估：把识别 worker 循环从继承的 `Dispatchers.IO` 迁到 `Dispatchers.Default`（CPU 密集更合适）；native 线程预算已由 sherpa `numThreads` 控制，属改动前就有的非回归现状，确认无明显收益可不动。

待确认 / 暂不做：

- [x] 暂不做：短片段打包（利用 Whisper 固定 30s 窗口把相邻短段拼进同一窗口，理论 2–5×）——涉及窗口内时间轴回切、影响行粒度与精度，单列后续版本评估，本版不做。
- [x] 暂不做：在 worker 间共享单份模型（sherpa-onnx `OfflineRecognizer`/stream 不易跨线程共享），仍每 worker 各加载一份。

## 1.1.0

### 2026-06-22

新功能（重大更新）：AI 自动生成字幕（本机 Whisper 转写 + OpenAI 兼容翻译，本地 Ollama / 云端 DeepSeek）。

现状：字幕只能手动绑定外部 `.vtt`，没有任何语音识别或翻译能力。本版新增「为无字幕音声自动生成中文字幕」的完整流水线。音频只在本机转写，仅文本上云翻译。

转写层（本机离线 ASR）：

- [x] 需要做：集成本机 Whisper（sherpa-onnx / ONNX Runtime Mobile）做日语语音识别，输出带时间戳原文。
- [x] 需要做：用 Silero VAD 切分语音段得到 `[start,end]` 时间戳，逐段转写生成原文 `.vtt`。
- [x] 需要做：Whisper 模型按需下载与管理（复用更新/下载能力的下载逻辑），提供模型选择与下载状态。
- [x] 需要做：音频只在本机处理、绝不上传，UI 明示。

翻译层（OpenAI 兼容 `chat/completions`，本地/云端可切换）：

- [x] 需要做：抽象 OpenAI 兼容翻译 client（OkHttp + `JSONObject`，不引入厂商 SDK），配置 `baseUrl` / `apiKey` / `model`。
- [x] 需要做：内置引擎预设——本地 Ollama（`http://<主机>:11434/v1`，无需 key）与云端 DeepSeek（`https://api.deepseek.com`，model `deepseek-v4-flash`，需用户自填 key）。
- [x] 需要做：上下文情境翻译——system 注入作品情境/亲密轻柔口语语气/ASMR 术语与拟声词规则；批量带前后文窗口；要求 `{id: 译文}` 逐句 JSON 输出以对齐时间轴。
- [x] 需要做：DeepSeek JSON 模式要求 prompt 含 `json` 字样 + 输出示例，启用 `response_format: {type:"json_object"}`；使用 `deepseek-v4-flash` 并关闭 thinking；`max_tokens` 给足防截断；按固定批量切批（当前每批 40 句）。
- [x] 需要做：为 DeepSeek / Ollama 翻译请求设置适合 LLM 响应的连接、读取和总调用超时，并将 `timeout`、域名解析失败、HTTP 错误转为中文提示。
- [x] 需要做：JSON 解析失败时退回宽松解析，兼容模型额外输出说明文字或 Markdown 代码块。
- [x] 已验证：DeepSeek API Key 配置后，`deepseek-v4-flash` + 关闭 thinking 可完成 JSON 字幕翻译，不再触发 `Not a JSON object`。
- [x] 需要做：容错——空 content / 截断 / 宽松解析仍失败 → 整批重试；该容错对 Ollama 同样适用。
- [x] 需要做：API Key 不内置、不硬编码，用户在设置里自填（云端方案不做自建后端）。

编排与产物：

- [x] 需要做：后台生成流程使用前台 Service 承载，提供两阶段进度、暂停、取消和生成状态预览。
- [x] 需要做：前台 Service 通知更新限频，避免高频转写进度回调触发系统通知队列限流或前台服务超时。
- [x] 需要做：转写完成后缓存分段时间轴与日文原文；翻译失败后重试优先复用缓存，跳过 Whisper 转写。
- [x] 需要做：翻译批次级断点续做与整批自动重试。
- [x] 需要做：阶段① 转写完成即可先看日文单语中间字幕；阶段② 翻译完成后生成仅含中文译文的 `.vtt`，自动绑定为该曲目字幕，复用现有播放页字幕展示。
- [x] 已验证：翻译失败后重新生成可复用已缓存的切片信息，最终绑定字幕只保留中文译文。
- [x] 需要做：release 包本地测试修复——保留 sherpa-onnx JNI 反射字段的 R8 keep 规则，修复流式重采样边界样本越界。

UI：

- [x] 需要做：设置页新增「AI 字幕」分组——引擎分段（本地 Ollama / 云端 DeepSeek）+ 接口地址/模型/API Key 行 + 本机 Whisper 转写模型管理。
- [x] 需要做：曲目「更多」菜单或字幕入口新增「自动生成字幕」；生成进度页展示两阶段进度 + 实时片段预览 + 暂停/取消。
- [x] 需要做：新 UI 按 `DOCS/design/amber-subtitle-ai.html`（引擎配置 / 两阶段生成进度，含黑夜白天双色板）实现 Compose 界面。

待确认 / 暂不做：

- [x] 已确认：Whisper 模型规格为 `base` 默认，`small` 作为可选模型；仍需后续用真实 ASMR 样本压测识别率。
- [x] 暂不做：云端 ASR——音频一律只在本机转写，不上传。
- [x] 暂不做：中日以外的多语言，先支持日文转写 + 中文翻译。

## 1.0.3

### 2026-06-22

新功能：DLsite 多任务下载 + 总下载进度条。

现状：`DlsiteDownloadService` 是严格单任务串行（`static activeDownload` 标志，下载进行中时新请求被 `START_NOT_STICKY` 直接丢弃、不排队）；旧下载状态通道只持有单个 `DlsiteDownloadState`。本版改造为下载队列 + 并发执行 + 多任务状态。

- [x] 需要做：把下载从「单任务丢弃」改为「下载队列」——新下载按加入顺序入队，并发上限内同时下载，其余排队（显示「排队中 · 第 N 位」）。
- [x] 需要做：改造 `DlsiteDownloadService` 支持多任务并发（多线程/协程 + 槽位管理），保留按 workId 的暂停/继续/取消/删除缓存语义。
- [x] 需要做：把下载状态从单个 `DlsiteDownloadState` 升级为多任务状态（`Map<workId, 任务状态/进度>`）并对外暴露汇总。
- [x] 需要做：计算总下载进度——按各任务字节加权汇总（已确认）；大小未知的任务不计入百分比，改为展示其当前下载速度与已下大小，避免百分比跳变。
- [x] 需要做：DLsite 页顶部总下载进度卡——「正在下载 N / 总数 项」+ 总进度细线条 + 速度 + 已下载/总大小 + 「全部暂停/继续」；无任务时隐藏。
- [x] 需要做：作品行多态——下载中（行内细线进度 + 百分比 + 暂停）、排队中（取消）、已暂停（继续）、失败（accent2 + 重试 + 简短原因）、已完成（导入/删除缓存）。
- [x] 需要做：下载管理 sheet——从总进度卡点入，统一控制全部任务（全部暂停/继续、清除已完成、单任务暂停/继续/取消）。
- [x] 需要做：前台通知显示总进度与「正在下载 N 项」，取代当前的单个作品标题。
- [x] 需要做：增量下载（内容级）——为作品的每个内容（`DlsiteDownloadOption`）记录独立的已下载状态（现状只有作品级 `isDownloaded()` 布尔，需扩展持久化到内容粒度）。
- [x] 需要做：把「多内容选一个下载」改为内容清单多选 + 增量；`DlsiteDownloadTask.downloadAndImport` 不再 `deleteRecursively(workDir)` 全量重下，已下内容跳过，仅下载选中的未下载内容。
- [x] 需要做：增量内容的音轨追加到该作品已有播放列表，而非每次 `createPlaylist` 新建重复列表；删除单个内容时同步移除其音轨与缓存文件。
- [x] 需要做：下载内容清单 sheet——列出作品各内容的标题、首数、已下载（✓ + 删除）/未下载（可勾选）/下载中状态，底部「加入下载」把选中未下载内容批量进入下载队列。
- [x] 需要做：新 UI 按 `DOCS/design/amber-dlsite-downloads.html`（总进度卡 / 作品行多态 / 下载管理 sheet / 下载内容清单增量下载，含黑夜白天双色板）实现 Compose 界面。
- [x] 需要做：并发下载上限固定默认 2（本版暂不提供用户可调设置项）。
- [x] 暂不做：跨 Tab 的全局下载指示（先只在 DLsite 页内呈现总进度与下载管理）。
- [x] 暂不做：字节级断点续传（沿用现有作品级暂停/恢复，从作品起点重下）。

## 1.0.2

### 2026-06-22

新功能：设置页显示软件版本号 + 检查更新。

- [x] 需要做：设置页新增「关于」分组，显示当前版本号（取 `versionName`）。
- [x] 需要做：检查更新——以 GitHub Release 为更新源，请求 `https://api.github.com/repos/Summer-DeZ/ASMRPlayer/releases/latest`，从 `tag_name` 解析版本号、`body` 取更新日志、`assets` 里的 APK 取下载地址，与本地 `versionName` 比对判定是否有新版本。
- [x] 需要做：检查更新多态——点击后显示「正在检查…」+ accent 转圈；无更新显示「已是最新」；有更新显示更新点 + 「有新版本 x.y.z」。
- [x] 需要做：发现新版本弹更新详情 Alert（暖夜琥珀风格，含版本号、更新内容、「稍后 / 立即更新」）。
- [x] 需要做：下载更新包——从 Release asset 的 APK 直链下载，设置页内细线进度条（accent 填充、无 thumb），显示已下载/总大小与「取消」；失败变 accent2 显示「重试」；完成后弹 Alert 询问是否立即安装（拉起系统安装器，需处理未知来源安装权限）。
- [x] 需要做：新 UI 按 `DOCS/design/amber-settings-update.html`（关于分组 / 检查中 / 更新详情 Alert / 下载进度，含黑夜白天双色板）实现对应 Compose 界面。
- [x] 需要做：约定 Release 发布规范——`tag_name` 与 `versionName` 一致（如 `v1.0.2` 或 `1.0.2`），并在 assets 上传 release APK，确保检查更新能取到下载地址。
- [x] 暂不做：应用内静默自动更新，当前仅支持手动点击「检查更新」。

## 1.0.1

### 2026-06-22

- [ ] 需要做：检查并清理生成式通配导入，避免每个 Kotlin/Java 文件都导入无关包。
- [ ] 需要做：继续减少 Java 遗留类，把纯业务模型和逻辑迁移到 Kotlin。
- [ ] 需要做：补充 ViewModel 层测试，覆盖资料库、播放、设置、睡眠、DLsite 主流程状态变化。
- [ ] 需要做：验证 Media3 媒体会话在蓝牙耳机、锁屏媒体控制和通知控制中的行为。
- [ ] 需要做：完善 DLsite 下载异常恢复，确认中断、暂停、恢复、删除缓存后的状态一致性。
- [ ] 需要做：整理 `DOCS/design/` 中命名不统一的设计资产，保留可追溯的当前设计图。
- [x] 暂不做：多模块拆分，当前规模先保持单一 `:app` 模块。
- [x] 暂不做：完整重做视觉风格，当前继续使用暖夜琥珀方向。
- [x] 暂不做：引入 Hilt，当前沿用手写 `AppContainer`。
