> 规则更新日期：2026-06-22

# ASMRPlayer 项目规则

## 文档目录

- 所有设计文档、设计图、原型图、截图蓝图和内部规划资料必须放在 `DOCS/` 下。
- 所有可视化设计资产一律放在 `DOCS/design/` 下，包括 UI 设计图、HTML mockup、截图、原型图、架构/流程图表、图标设计稿等；以后新增的图表也都放这里，不要散落到其它目录。
- `DOCS/design/` 下的文件名统一使用小写 kebab-case（如 `amber-dark-player.png`、`amber-settings-update.html`），不使用大写、空格或中文，确保跨平台与版本控制一致。
- 例外：图标包 `DOCS/design/icon/` 内的 Android 资源文件（`drawable/`、`mipmap-*/` 下的 `ic_*.xml` 等）沿用 Android 要求的 snake_case，不套用 kebab-case，以便可直接拷入 `app/src/main/res/`。
- 不要在根目录、`app/`、小写 `docs/` 或其它目录新增设计文档、设计图或规划文档。
- 根目录 `README.md` 是对外项目说明；`DOCS/README.md` 才是给 AI 快速理解架构的内部入口。

## 固定文档

`DOCS/` 下固定维护这四个 Markdown 入口：

- `DOCS/README.md`
- `DOCS/UI-Design.md`
- `DOCS/Feature-Design.md`
- `DOCS/TODOList.md`

如无用户明确要求，不要新增平行的设计说明、架构计划、重构计划等 Markdown 文件；应把内容归并到上述四个入口。

## 版本规则

- 每个 `DOCS/*.md` 文档开头必须先写当前软件版本，例如：`> 当前软件版本：1.0.1（versionCode 2）`。
- 文档开头同时写更新日期。
- 如果 `app/build.gradle.kts` 中的 `versionName` 或 `versionCode` 变化，同一次修改必须同步更新 `DOCS/` 文档开头的版本号。
- 面向未来的计划必须在 `TODOList.md` 中按版本归档；不要把未来计划混入当前 UI 蓝图或当前功能说明。

## 文档职责

- `DOCS/README.md`：让 AI 快速了解当前软件架构、目录边界、主要数据流和常用验证命令。
- `DOCS/UI-Design.md`：只写当前 UI 设计蓝图，包括有哪些 UI、布局/视觉规则、交互形态，以及相关设计图在 `DOCS/design/` 中的位置；不写执行步骤、改造计划或实现清单。
- `DOCS/Feature-Design.md`：写当前软件已经具备的特性和功能行为。新增或修改软件特性后，同步更新此文件。
- `DOCS/TODOList.md`：写接下来要实现但尚未实现的内容。一级标题必须是版本号，二级标题必须是日期，条目必须使用 Markdown 可勾选列表，并明确哪些更新需要做、哪些暂不做。

## 更新要求

- 改动 UI 时，同时检查 `DOCS/UI-Design.md` 和 `DOCS/design/` 是否需要同步。
- 改动功能行为时，同时检查 `DOCS/Feature-Design.md` 是否需要同步。
- 改动架构、依赖注入、数据层、播放层或验证路径时，同时检查 `DOCS/README.md` 是否需要同步。
- 新增未实现事项、推迟事项或明确不做事项时，只更新 `DOCS/TODOList.md`。
- 不允许使用AI名字提交，使用环境已经登陆的git进行提交
- 资源发布信息需要由人来审核，通过后才行
