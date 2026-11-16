# LlmClient 三协议网络层 JVM 端到端验证报告（task-4）

## 结论

- 测试项：**263 项**，**PASS 263 / FAIL 0**（`RESULT: PASS`，`java` 进程退出码 0）
- 耗时：约 **30.6s**（其中约 30s 用于真实读超时用例，见下）
- 缺陷清单：**必测项未发现 app 缺陷**；**无剩余观察项**（上一轮 Gemini 完整 URL endpointPath 观察已由 lead 修复，并已转为必测断言）
- 未修改 `app/` 下任何源码、`build.ps1`、`README`；未运行 `build.ps1`；写文件仅 `tools/qa-core/**`

## 环境与命令

| 项 | 值 |
| --- | --- |
| JDK | `.tools/jdk/jdk-17.0.21+9`（Temurin 17.0.21，`javac --release 11`） |
| org.json | `tools/lib/json-20240303.jar`（上轮 task-3 已下载的官方 jar） |
| mock 服务 | `tools/qa-core/NetMock.java`：JDK 原生 `ServerSocket` 手写 HTTP/1.1 mock，绑定 **127.0.0.1 随机端口**，测试结束关闭 |
| 工作目录 | 项目根目录 `C:\AI工作区\txt实时翻译apk` |

编译（app 网络层源码与测试源码一起编译，不复制源码）：

```powershell
$jdk = '.tools\jdk\jdk-17.0.21+9'
& "$jdk\bin\javac.exe" --release 11 -encoding UTF-8 `
  -classpath "tools/lib/json-20240303.jar" -d tools/qa-core/classes `
  app/java/com/yidu/novel/net/LlmClient.java `
  app/java/com/yidu/novel/net/LlmException.java `
  app/java/com/yidu/novel/net/StreamJsonParser.java `
  app/java/com/yidu/novel/net/TokenLimiter.java `
  app/java/com/yidu/novel/model/ApiProfile.java `
  app/java/com/yidu/novel/model/ProviderPreset.java `
  tools/qa-core/NetMock.java `
  tools/qa-core/NetHarness.java
```

运行（Windows classpath 分隔符 `;`；本组含真实 30s 读超时用例）：

```powershell
& "$jdk\bin\java.exe" "-Dfile.encoding=UTF-8" -cp "tools/qa-core/classes;tools/lib/json-20240303.jar" qa.NetHarness
```

最近一次完整输出：`tools/qa-core/net-last-run.log`。

## 覆盖与结果（按测试分组）

| 分组 | 测试项 | 结果 |
| --- | --- | --- |
| prepare 参数校验 | 9 | 全部 PASS |
| 端点拼接（OpenAI） | 13 | 全部 PASS |
| 端点拼接（Anthropic） | 13 | 全部 PASS |
| 端点拼接（Gemini） | 17 | 全部 PASS |
| OpenAI 流式（SSE/推理字段） | 13 | 全部 PASS |
| OpenAI 非流式与容错 | 25 | 全部 PASS |
| HTTP 错误状态码 | 24 | 全部 PASS |
| Anthropic 协议 | 28 | 全部 PASS |
| Gemini 协议 | 28 | 全部 PASS |
| DeepSeek 思考模式字段 | 31 | 全部 PASS |
| 单次最大输出 max_tokens | 13 | 全部 PASS |
| 请求头与自定义端点 | 18 | 全部 PASS |
| 取消 cancel() | 8 | 全部 PASS |
| testConnection | 6 | 全部 PASS |
| TokenLimiter（依赖类轻量验证） | 6 | 全部 PASS |
| 网络错误与读超时（约 30s） | 11 | 全部 PASS |
| **合计** | **263** | **PASS 263 / FAIL 0** |

### 1. prepare 参数校验（9）

- `api == null` → `LlmException("尚未配置 API", 0, false, false)`；`baseUrl` 空白 → “Base URL 为空”；`model` 空白 → “模型名为空”。
- 校验失败时**不发送任何 HTTP 请求**（mock 记录数 0）。

### 2. 端点拼接（OpenAI / Anthropic / Gemini，共 35 项）

- OpenAI：`baseUrl=/v1` + `/chat/completions`；base 末尾斜杠；base 已含完整路径不重复拼接；相对 endpointPath（无前导 `/`）；endpointPath 为空时自动补 `/chat/completions`；**完整 http URL endpointPath 优先生效**；完整 URL 带 query 参数原样保留；`127.0.0.1:port` 无 scheme 自动补 `http://`。
- Anthropic：`/v1` + `/messages`；endpointPath 为空补 `/messages`；base 已含 `/messages`；**完整 URL endpointPath 优先**；流式时完整 URL 自动补 `?stream=true`，已带 `stream=true` 时不重复；非流式完整 URL 不加 `stream=true`。
- Gemini：`/v1beta/models/{model}:generateContent` 与 `:streamGenerateContent?alt=sse`；model 的 `models/` 前缀被去掉；base 以 `/models` 结尾；base 末尾斜杠；**自定义完整 URL endpointPath 优先**（不再自动拼 models 路径，非流式/流式均验证，保留自定义 query，请求体仍为 Gemini 结构）。
- 所有请求均为 `POST`，mock 实际收到的 path/query 与期望逐字符比对。

### 3. OpenAI 流式 / 非流式（38 项）

- SSE 分块 feed：`content` 增量拼接 = `你好，世界`；空 content 不触发 `onDelta`；`reasoning_content` 与 `reasoning` 两种推理字段走 `onReasoning` 且不污染正文；`: keep-alive` 注释行与空行被忽略；`finish_reason`/`[DONE]` 正确处理。
- 流式 `finish_reason=length` → `isTruncated()=true`、`getFinishReason()="length"`。
- 非流式 `complete`：返回 `message.content`；content 为数组时拼接 `text` 片段；仅推理内容无正文 → 抛“模型只返回了推理内容”；`finish_reason=length` 无正文 → 抛“模型输出达到最大 token 上限”且 `isTruncated()=true`；空响应 → “接口返回了空响应…”（retryable）；非 JSON → “接口返回的不是合法 JSON”；200 但带 `error` 对象 → “接口返回错误：…”。
- **服务端忽略 stream 返回非 SSE JSON**：`onDelta` 全文回调一次，且请求体仍声明 `stream=true`。
- 空响应/异常均不抛 500 行堆栈，错误文案可读。

### 4. HTTP 错误状态码（24 项）

- 429（complete + stream）：`status=429`、`retryable=true`、文案含“请求被限流”且保留服务端 message。
- 401：`status=401`、`retryable=false`、文案含“认证失败”。
- 500 + HTML 错误体：`retryable=true`、文案含“服务商暂时不可用”，HTML 被清洗后仍保留可读片段 `Bad Gateway`。
- 404：`retryable=false`、“接口或模型不存在”；402：`retryable=false`、“账户额度不足”。

### 5. Anthropic 协议（28 项）

- SSE：`message_start` / `content_block_delta` / `message_delta(stop_reason)` / `message_stop` 全链路，正文拼接 `你好，世界`，`finishReason=end_turn`。
- `stop_reason=max_tokens` → `isTruncated()=true`；SSE `event: error` 事件 → 抛“接口返回错误：overloaded”。
- 非流式：`content[]` 中 `type=text` 片段拼接，非 text 块（tool_use）被忽略；无文本 → “Anthropic 响应中没有文本内容”。
- 请求：`x-api-key`、`anthropic-version: 2023-06-01`、`Authorization` 不发送；body 含 `model` / `system` / `messages[0].role=user` / `max_tokens` 必填；非流式 body 无 `stream`，流式 body `stream=true` + `max_tokens`。
- `unlimitedOutput=true` 时 `max_tokens=64000`（协议必填），限制时=`maxOutputTokens`，`maxOverride` 覆盖。
- 服务端忽略 stream 返回 Anthropic JSON 时 `onDelta` 全文回调一次。

### 6. Gemini 协议（28 项）

- SSE：`candidates[].content.parts[].text` 拼接；`thought:true` 思考块被忽略；`finishReason=STOP`；`MAX_TOKENS` → `isTruncated()=true`。
- 非流式：parts 拼接（thought 忽略）、`systemInstruction.parts[0].text`、`contents[0].role=user`、`generationConfig.temperature`。
- 请求头 `x-goog-api-key`，不发送 `Authorization`。
- 错误：SSE `error` 对象 → “接口返回错误：gemini boom”；`promptFeedback.blockReason=SAFETY` → “Gemini 拒绝了该请求（blockReason=SAFETY）”；无 candidates → “Gemini 响应中没有候选结果”。
- 服务端忽略 stream 返回 Gemini JSON 时 `onDelta` 全文回调一次。

### 7. DeepSeek 思考模式字段（31 项）

- `thinkingEnabled=true`：OpenAI 兼容 body 含 `"thinking":{"type":"enabled"}` + `reasoning_effort`（high/max/low 原样；非法值回退 high），且**不发送 temperature**。
- `thinkingEnabled=false` 且 DeepSeek（providerId=deepseek 或 model 含 deepseek）：body 含 `"thinking":{"type":"disabled"}`，不发 `reasoning_effort`，正常发送 temperature。
- 普通自定义 API（非 DeepSeek）关闭思考：body **不含** `thinking`/`reasoning_effort`，temperature 正常。
- `ApiProfile.isDeepSeek()` 三种判定路径（providerId / 模型名 / 普通模型 false）。
- Anthropic / Gemini 协议下即使 `thinkingEnabled=true` 也不发送 OpenAI 的 `thinking`/`reasoning_effort` 字段（各协议独立，边界行为符合实现）。

### 8. 单次最大输出 max_tokens（13 项）

- OpenAI：`unlimitedOutput=true` → 请求体**无** `max_tokens`（流式/非流式均验证）；`false` → `max_tokens = max(64, maxOutputTokens)`（1234 与 10→64 两种）；`maxOverride>0` 覆盖 unlimited（777）。
- Anthropic：unlimited → 64000（协议必填）；限制 → maxOutputTokens；maxOverride 覆盖。
- Gemini：unlimited → `generationConfig` 无 `maxOutputTokens`；限制 → 有；maxOverride 覆盖。

### 9. 请求头与自定义端点（18 项）

- `extraHeaders` JSON 正确附加：字符串 `v1`、数字 `42`→"42"、布尔 `true`→"true"。
- 自定义头可覆盖默认 `Authorization`（Bearer override-key）。
- 非法 JSON / 空字符串 extraHeaders 不崩溃，请求正常且默认认证头保留。
- Anthropic 自定义头与 `x-api-key` 共存。
- 默认头：`Content-Type: application/json; charset=utf-8`、`User-Agent: YiDuNovel/1.0 (Android)`、非流式 `Accept: application/json`、流式 `Accept` 含 `text/event-stream`、`Accept-Encoding: identity`。
- 完整 URL endpointPath 优先于 baseUrl 拼接（OpenAI 已验证 `/priority/endpoint`）。

### 10. 取消 cancel()（8 项）

- 慢速 SSE（首块后延迟 3s）：收到首个 delta 后 `cancel()`，流式线程 **2s 内** 抛 `LlmException(cancelled=true, message="已取消")`，`isCancelled()=true`。
- 取消后同一 client 再次 `complete` 成功，`prepare()` 重置取消状态（`isCancelled()=false`）。
- `complete` 进行中（mock 延迟 3s）`cancel()` → 同样抛 cancelled。

### 11. 网络错误与读超时（11 项）

- 连接被拒绝（临时占用后关闭的端口）：complete → “网络请求失败：…”、`status=0`、retryable；stream → “网络中断：…”。
- **真实读超时**：mock 先回响应头、40s 后才回包体；`LlmClient` 读超时下限为 `Math.max(30, api.timeoutSec)`（`LlmClient.java:191`），因此约 30s 后抛 “模型响应超时，请检查网络或稍后重试。”、`status=0`、retryable；实测耗时在 25s~45s 窗口内（本机约 30.6s，占整组耗时主体）。
- 说明：如果只想快速回归，可跳过本组（约省 30s）；当前默认执行以覆盖超时真实路径。

### 12. TokenLimiter 依赖类（6 项，轻量）

- `tpm<=0` 直接放行；窗口内累计未超可连续获取；已取消时 `acquire` 返回 false；窗口不足触发 `Waiter.onWaiting` 并在取消后立即返回 false。

## 缺陷清单

**必测项：未发现 app 源码缺陷。** 263 项断言全部 PASS，`RESULT: PASS`。

### 原附加观察（已修复并转为必测）

- 上一轮报告的“Gemini 协议下完整 URL endpointPath 不生效”已由 lead 修复：`app/java/com/yidu/novel/net/LlmClient.java:311-321` 现将 `http://` / `https://` 开头的 endpointPath 提到 `buildEndpoint` 最前面，Gemini 也走该优先分支（Anthropic 流式仍补 `stream=true`，并改用 `!path.contains("stream=")` 避免重复追加）。
- 本轮已将该观察改为 **8 项必测断言**：`protocol=gemini` + 完整 URL endpointPath 时请求该完整 URL（不再自动拼 models 路径，非流式/流式均验证）、保留自定义 query、不自动追加 `alt=sse`、请求体仍为 Gemini 结构；实测全部 PASS。
- **当前无剩余观察项。**

## 本轮变更（Gemini 完整 URL 修复后的回归）

- `NetHarness.testEndpointGemini` 新增 8 项必测断言（原附加观察转正），测试项 255 → **263**。
- 移除“附加观察”分组（`testObservations` 已无观察项），汇总不再有 `OBSERVE` 行。
- 仅修改 `tools/qa-core/**`，未改 `app/`。

## 文件

- `tools/qa-core/NetMock.java` — 本地 HTTP/SSE mock（随机端口、请求记录、错误码、延迟、SSE 分块）
- `tools/qa-core/NetHarness.java` — 测试主体（263 断言，无附加观察）
- `tools/qa-core/net-last-run.log` — 最近一次完整运行输出（`RESULT: PASS`，耗时 30.64s）
- `tools/qa-core/classes/` — 编译产物（含 app 网络层类与 qa.NetMock / qa.NetHarness）

## 合规说明

- 仅创建/修改 `tools/qa-core/**`；未改 `app/`、`build.ps1`、`README`；未运行 `build.ps1`；未连接真机。
- 测试通过 `--release 11` 编译 app 源码并直接运行在 JVM（`LlmClient` 无 Android 依赖）；mock 服务使用随机端口并在 `try-with-resources` 中关闭。
