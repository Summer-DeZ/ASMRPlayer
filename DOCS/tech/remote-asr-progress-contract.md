> 当前软件版本：1.6.0（versionCode 21）
> 文档更新日期：2026-06-24

# Remote ASR Progress Contract

本文件是 ASMRPlayer 与远程 ASR 服务端的严格接口契约。App 只会发送本文列出的请求字段；服务端也应按本文字段返回状态、错误和结果。本文不引用任何本机外部服务仓库路径。

## 基线模型

- ASR：`Qwen3-ASR-0.6B`，负责把日语音频识别为文本。
- Forced Aligner：`Qwen3-ForcedAligner-0.6B`，负责把识别文本对齐回音频时间轴。
- 服务端可以提供其它 profile/model，但必须保持本文的请求字段、状态字段、错误码和 result segments 结构不变。
- App 设置页只把远程 `model` 作为可选 profile/model 字段传给服务端；App 不新增本地 Qwen 模型控制。

## 健康检查

### `GET /health`

用于设置页「测试连接」。请求头：

- `Accept: application/json`
- `User-Agent: ASMRPlayer-Android`
- 可选 `Authorization: Bearer <token>`

成功响应 `200`：

```json
{
  "status": "ok",
  "service": "asrm-remote-asr",
  "version": "1.0.0",
  "device": "cuda",
  "default_model": "Qwen3-ASR-0.6B",
  "models_ready": true
}
```

字段要求：

- `status`：`ok` 表示服务可用；其它值按不可用处理。
- `service` / `version` / `device` / `default_model`：用于诊断显示。
- `models_ready`：`true` 表示模型已加载或可立即处理任务；`false` 时 App 显示“模型未就绪”。

## 创建任务

### `POST /transcriptions`

请求类型：`multipart/form-data`

请求字段严格限定为：

- `file`：必填，整段音频文件。
- `language`：必填，固定 `ja`。
- `task`：必填，固定 `transcribe`。
- `model`：可选，非空时由 App 原样传入用户配置的远程 profile/model。

App 不会发送 `vad` 或其它未列出的字段。服务端若启用 VAD 或其它策略，应在服务端配置内部决定，不要求 App 传参。

成功响应 `200` 或 `202`：

```json
{
  "job_id": "asr-20260624-0001"
}
```

字段要求：

- `job_id`：必填，非空字符串，后续轮询、取结果和取消都使用该值。

## 查询任务状态

### `GET /transcriptions/{job_id}`

请求头：

- `Accept: application/json`
- `User-Agent: ASMRPlayer-Android`
- 可选 `Authorization: Bearer <token>`

成功响应 `200`：

```json
{
  "status": "running",
  "stage": "asr",
  "processed_ms": 115000,
  "duration_ms": 302000,
  "progress": 38,
  "message": "正在语音识别",
  "updated_at": "2026-06-24T10:00:00Z",
  "preview_segments": [
    {
      "id": 1,
      "start_ms": 0,
      "end_ms": 2380,
      "text": "おはよう"
    }
  ]
}
```

字段要求：

- `status`：必填，建议值为 `queued`、`running`、`succeeded`、`failed`、`canceled`。
- `stage`：必填，建议值为 `queued`、`asr`、`aligning`、`finalizing`、`done`、`failed`、`canceled`。
- `processed_ms`：ASR / 对齐阶段已处理到的音频时间位置，单位毫秒；未知时可为 `null`。
- `duration_ms`：音频总时长，单位毫秒；未知时可为 `null`。
- `progress`：可选整体进度；可以返回 `0..1` 或 `0..100`，App 会归一化并保证 UI 不回退。
- `message`：可选短文案；非空时 App 优先展示该文案。
- `updated_at`：建议返回 ISO-8601 时间戳，用于诊断轮询是否有新状态。
- `preview_segments`：可选预览片段；结构与 result 的 `segments` 相同。

进度要求：

- 服务端能提供 `processed_ms` / `duration_ms` 时，应优先更新这两个字段，使 App 能显示类似 `正在语音识别 01:55/05:02` 的时间进度。
- 当 `processed_ms` 不可用时，可用 `progress` 表示粗粒度阶段进度。
- `processed_ms` 和 `progress` 不应回退；服务端若重试内部阶段，应继续返回最近一次可确认进度。

## 获取结果

### `GET /transcriptions/{job_id}/result`

仅在 `status=succeeded` 或 `stage=done` 后调用。

成功响应 `200`：

```json
{
  "segments": [
    {
      "id": 1,
      "start_ms": 0,
      "end_ms": 2380,
      "text": "おはよう"
    }
  ]
}
```

字段要求：

- `segments`：必填数组，按 `start_ms` 升序排列。
- `id`：可选；缺失或 `null` 时 App 按 1-based 顺序补齐。
- `start_ms` / `end_ms`：必填，毫秒；`end_ms` 必须大于 `start_ms`。
- `text`：必填，非空日文识别文本。

App 会拒绝空数组、空文本、时间倒序、结束时间不大于开始时间的结果。

## 取消任务

### `DELETE /transcriptions/{job_id}`

成功响应可为 `200`、`202` 或 `204`。App 取消本地生成时会尽力调用该接口；网络失败不会自动重试取消请求。

## 错误响应

错误响应建议使用：

```json
{
  "error": {
    "code": "MODEL_NOT_READY",
    "message": "model is loading"
  }
}
```

兼容顶层字段：

```json
{
  "code": "MODEL_NOT_READY",
  "message": "model is loading"
}
```

错误码：

- `INVALID_REQUEST`：请求字段、音频文件或 job id 无效。
- `UNAUTHORIZED`：Bearer Token 缺失或错误。
- `QUEUE_FULL`：服务端队列已满。
- `MODEL_NOT_READY`：模型尚未就绪。
- `ASR_FAILED`：Qwen ASR 识别失败。
- `ALIGNMENT_FAILED`：Qwen Forced Aligner 对齐失败。
- `JOB_NOT_FOUND`：任务不存在或已过期。
- `JOB_NOT_READY`：结果尚未准备好。
- `RESULT_EXPIRED`：结果已过期。

## 轮询和停滞

- App 默认约 2 秒轮询一次状态。
- 每次轮询都是短请求；远程任务没有固定总超时。
- 只要 `status`、`stage`、`progress`、`processed_ms` 或 `updated_at` 有变化，App 认为任务仍在推进。
- 连续约 10 分钟没有可见状态变化时，App 报“远程转写进度停滞”。
