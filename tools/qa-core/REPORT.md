# 核心逻辑 JVM 验证报告（task-3）

## 结论

- 测试项：**204 项**，**PASS 204 / FAIL 0**（`RESULT: PASS`，进程退出码 0）
- 未修改 `app/` 下任何源码、`build.ps1`、`README`；未运行 `build.ps1`；未连接真机。
- 写文件范围仅 `tools/qa-core/`、`tools/lib/`。

## 环境与命令

| 项 | 值 |
| --- | --- |
| JDK | `.tools/jdk/jdk-17.0.21+9`（Temurin 17.0.21，`javac --release 11`） |
| org.json | `tools/lib/json-20240303.jar`（Maven Central 官方 jar，78332 字节，curl.exe 下载成功） |
| 工作目录 | 项目根目录 `C:\AI工作区\txt实时翻译apk` |

编译（把 app 源码与测试源码一起编译，不复制源码）：

```powershell
$jdk = '.tools\jdk\jdk-17.0.21+9'
& "$jdk\bin\javac.exe" --release 11 -encoding UTF-8 `
  -classpath "tools/lib/json-20240303.jar" -d tools/qa-core/classes `
  app/java/com/yidu/novel/util/TextUtil.java `
  app/java/com/yidu/novel/util/Novel.java `
  app/java/com/yidu/novel/util/Exporter.java `
  app/java/com/yidu/novel/util/Defaults.java `
  app/java/com/yidu/novel/model/Book.java `
  app/java/com/yidu/novel/model/PromptProfile.java `
  app/java/com/yidu/novel/util/Lang.java `
  app/java/com/yidu/novel/net/StreamJsonParser.java `
  app/java/com/yidu/novel/net/PromptBuilder.java `
  app/java/com/yidu/novel/net/PunctuationPreserver.java `
  app/java/com/yidu/novel/net/OutputGuard.java `
  app/java/com/yidu/novel/net/TokenEstimator.java `
  tools/qa-core/CoreHarness.java
```

运行（Windows classpath 分隔符 `;`）：

```powershell
& "$jdk\bin\java.exe" "-Dfile.encoding=UTF-8" -cp "tools/qa-core/classes;tools/lib/json-20240303.jar" qa.CoreHarness
```

完整输出：`tools/qa-core/last-run.log`。

## 覆盖与结果（按测试分组）

| 分组 | 测试项 | 结果 |
| --- | --- | --- |
| TextUtil.decode | 16 | 全部 PASS |
| TextUtil.normalize | 11 | 全部 PASS |
| TextUtil.isChapterTitle | 21 | 全部 PASS |
| Novel.parse（样例 TheLanternKeeper_en.txt） | 21 | 全部 PASS |
| Novel.parse（超长段落/空段落/分页） | 12 | 全部 PASS |
| StreamJsonParser.parsePartial | 11 | 全部 PASS |
| StreamJsonParser.parseFinal | 8 | 全部 PASS |
| PunctuationPreserver | 18 | 全部 PASS |
| PromptBuilder | 36 | 全部 PASS |
| Exporter | 31 | 全部 PASS |
| OutputGuard | 9 | 全部 PASS |
| TokenEstimator | 10 | 全部 PASS |
| **合计** | **204** | **PASS 204 / FAIL 0** |

### 关键验证点

- **TextUtil.decode**：UTF-8、UTF-8 BOM（结果不含 U+FEFF）、GB18030 中文、UTF-16LE 中文/英文（带 BOM）、UTF-16LE 无 BOM 英文（零字节启发式）、空字节/null。
- **normalize**：CRLF/孤立 CR、BOM、NUL、连续空行折叠为 1 行、行尾空白与首尾空白（含全角空格）去掉、多行块间隔保持。
- **isChapterTitle**：`第一章 开始`、`第12章 归途`、`第3卷`、`序章`、`Chapter 1`、`CHAPTER IV`、`3. 归途` 等为 true；`他说：第一章已经结束。`、`第一章的开始`、`The Lantern Keeper`、超 40 字、空串/null 等为 false。
- **Novel.parse 样例**：识别 4 个标题 block（Chapter 1–4）、4 个 Chapter 章节 + “开篇”前言章节；章节区间连续覆盖全部 block；每段文本可由 blocks 完整还原；空段落不产生 block；`pageText(0,n)`/分页窗口/paragraphSpacing/indent/越界参数均不丢字。
- **Novel.parse 超长段落**：1000 字 CJK 段落切成 3 块（每块 ≤420），拼接与原文完全一致；多块段落 pageText 不丢字；空段落无 block。
- **StreamJsonParser.parsePartial**：按字符逐步 feed `["甲","乙\n丙","丁"]`，items 单调递增，依次出现已完成项与当前条 partial（含 `\n` 转义），收尾 partial 为空；对象包裹数组可用；null/空串/无 `[` 安全。
- **parseFinal**：```json 围栏、`{"translations":[...]}` 对象、`["甲","乙` 非法截断（不抛异常、保留“甲”）、数组元素为对象（translation/text 提取）、无 JSON 时按行回退并去序号、null。
- **PromptBuilder**：三个内置提示词（`Defaults.promptFor` HIGH/MEDIUM/LOW）经 `buildSystem` 后都含目标语言、源语言、JSON 数组输出约束（数量）、章节上下文、术语表内容；标点开关两种文案不同；`{source_lang}/{target_lang}/{chapter}/{glossary}` 占位符替换；`buildUser` 上下文/编号/JSON 要求。
- **Exporter**：`MODE_TRANSLATION_ONLY` 输出行数=原段落数、空行位置与章节标题行位置精确匹配；`MODE_BILINGUAL` 每段两行、行数=2×非空段落+空段落；未翻译段落 fallback 原文；多块段落部分翻译时 fallbackOriginal 语义；`countTranslated/countMissing/exportStats/preview`。
- **OutputGuard**：重复行循环、短周期重复、超长同字符连续、超硬上限 → true；正常中英文长文本、空文本、短文本、null → false。
- **TokenEstimator**：中英文估算均 > 0、随长度增长、同字符数中文高于英文、estimateOutput ≥24 且 ≥estimate 并随输入增长。

## 缺陷清单

**必测项：无 FAIL（未发现 app 源码行为与 task-3 期望不符）。**

### 附加观察（非 task-3 必测项，不计入 PASS/FAIL，未修改 app）

- **现象**：`Novel.parse` 切分超长英文段落时，在切分处丢弃一个空格；由于 `pageText()` 与 `Exporter` 拼接 block 时不补分隔符，拼接结果出现词与词直接相连。
- **位置**：
  - `app/java/com/yidu/novel/util/Novel.java:70-83`（`splitBlock`：`pieces.add(... .trim())` 第 76 行、跳过空白第 78 行）
  - `app/java/com/yidu/novel/util/Novel.java:170-186`（`pageText` 第 183 行直接 `append(block.text)`，无分隔符）
  - `app/java/com/yidu/novel/util/Exporter.java:32-44`（按 block 累加 `original.append(block.text)`，无双语/原文分隔符）
  - 阅读器同样按 block 直接拼接：`app/java/com/yidu/novel/ReaderActivity.java:541-562`
- **复现输入**：`"The lantern keeper walked along the shore. "` 重复 30 次后 `trim()`（1289 字符，超过英文 900 字符切块阈值；阈值见 `Novel.java:47-48`）。
- **期望**：`Novel.parse` 后 blocks 拼接、`pageText(0,n)` 均等于原文 1289 字符。
- **实际**：blocks 数=2，拼接后 1288 字符，首个差异 `index=859`：原文 `"…shore. The lantern…"` → 实际 `"…shore.The lantern…"`（每个切分处丢 1 个空格）。
- **影响范围**：仅“英文等空格分词语言且单段超过 900 字符（CJK 为 420）”的超长段落；阅读页原文、双语导出的原文行会出现单词粘连。样例小说与常规长度段落不受影响；中文段落无空格，不受影响。
- **修复建议（仅供参考，未改 app）**：`splitBlock` 保留切分处的空白到块尾，或 `pageText`/`Exporter` 在拼接同一段落后续 block 时按需补 1 个空格；若按语种处理，可复用 `TextUtil.looksCjk`。
- **严重度**：低（触发条件为超长段落；不影响 task-3 必测行为）。

## 测试过程中的测试自身修正（透明记录）

首轮运行出现 4 个 FAIL，核对后确认均为**测试断言写错**而非 app 缺陷，已修正测试：

1. `parsePartial` 的 partial 判断把“已完成条数”多算了 1（第 2 条 partial 对应 items=1，第 3 条对应 items=2）。
2. Exporter fallback 用例行号映射错位（`fallbackLines[2]` 对应第 3 个非空段落，值是“译2”）。
3. OutputGuard 重复行用例的行长 20 字 < 24 字阈值，未触发规则；加长到 30 字后符合预期。

修正后 204 项全部 PASS。

## 文件

- `tools/qa-core/CoreHarness.java` — 测试主体（204 断言 + 1 附加观察）
- `tools/qa-core/last-run.log` — 最近一次完整运行输出
- `tools/qa-core/classes/` — 编译产物（app 源码类 + qa.CoreHarness）
- `tools/lib/json-20240303.jar` — org.json 官方 jar
