package qa;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.net.LlmClient;
import com.yidu.novel.net.LlmException;
import com.yidu.novel.net.TokenLimiter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * LlmClient 三协议（OpenAI/Anthropic/Gemini）+ SSE 流式 + DeepSeek thinking +
 * max_tokens + 自定义头/端点 + 取消 的 JVM 端到端验证。
 * 依赖 NetMock 提供的本地随机端口 HTTP / SSE 服务。
 *
 * 运行（Windows）：java -cp "tools/qa-core/classes;tools/lib/json-20240303.jar" qa.NetHarness
 */
public final class NetHarness {

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> observations = new ArrayList<>();
    private static String section = "";
    private static NetMock mock;
    private static long startedAt = System.currentTimeMillis();

    public static void main(String[] args) throws Exception {
        System.out.println("=== LlmClient 三协议 JVM 端到端验证 ===");
        startedAt = System.currentTimeMillis();
        try (NetMock server = new NetMock()) {
            mock = server;
            mock.setHandler(NetHarness::autoReply);
            run("prepare 参数校验", NetHarness::testPrepare);
            run("端点拼接（OpenAI）", NetHarness::testEndpointOpenAi);
            run("端点拼接（Anthropic）", NetHarness::testEndpointAnthropic);
            run("端点拼接（Gemini）", NetHarness::testEndpointGemini);
            run("OpenAI 流式（SSE/推理字段）", NetHarness::testOpenAiStream);
            run("OpenAI 非流式与容错", NetHarness::testOpenAiComplete);
            run("HTTP 错误状态码", NetHarness::testHttpErrors);
            run("Anthropic 协议", NetHarness::testAnthropic);
            run("Gemini 协议", NetHarness::testGemini);
            run("DeepSeek 思考模式字段", NetHarness::testThinking);
            run("单次最大输出 max_tokens", NetHarness::testMaxTokens);
            run("请求头与自定义端点", NetHarness::testHeaders);
            run("取消 cancel()", NetHarness::testCancel);
            run("testConnection", NetHarness::testTestConnection);
            run("TokenLimiter（依赖类轻量验证）", NetHarness::testTokenLimiter);
            run("网络错误与读超时（约 30s）", NetHarness::testNetworkAndTimeout);
        } catch (Throwable t) {
            failed++;
            failures.add("[初始化/收尾] " + t.getClass().getSimpleName() + ": " + t.getMessage());
            System.out.println("FAIL 初始化/收尾异常: " + t);
        }
        summary();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ==================== 测试项 ====================

    private static void testPrepare() throws Exception {
        section = "prepare 参数校验";
        mock.clearRequests();
        LlmException e = expectError("api=null", () -> new LlmClient(null).complete("s", "u", 0));
        if (e != null) {
            eq("api=null status", 0, e.status);
            check("api=null 文案", e.getMessage().contains("尚未配置 API"), msg(e));
            check("api=null 不重试/非取消", !e.retryable && !e.cancelled, "");
        }
        final ApiProfile blankBase = profile("openai");
        blankBase.baseUrl = "   ";
        LlmException e2 = expectError("baseUrl 为空", () -> new LlmClient(blankBase).complete("s", "u", 0));
        if (e2 != null) check("baseUrl 为空文案", e2.getMessage().contains("Base URL 为空"), msg(e2));
        final ApiProfile blankModel = profile("openai");
        blankModel.model = " ";
        LlmException e3 = expectError("model 为空", () -> new LlmClient(blankModel).complete("s", "u", 0));
        if (e3 != null) check("model 为空文案", e3.getMessage().contains("模型名为空"), msg(e3));
        eq("prepare 失败不发送请求", 0, mock.requestCount());
    }

    private static void testEndpointOpenAi() throws Exception {
        section = "端点拼接（OpenAI）";
        mock.setHandler(NetHarness::autoReply);
        ApiProfile p = profile("openai");
        mock.clearRequests();
        eq("OpenAI 非流式返回", "OPENAI-OK", new LlmClient(p).complete("系统提示", "原文", 0));
        NetMock.Request req = last();
        eq("默认 base=/v1 + path=/chat/completions", "/v1/chat/completions", req.path);
        eq("HTTP 方法 POST", "POST", req.method);
        eq("默认无 query", "", req.query);

        p = profile("openai");
        p.baseUrl = mock.baseUrl() + "/v1/";
        new LlmClient(p).complete("s", "u", 0);
        eq("base 末尾斜杠", "/v1/chat/completions", last().path);

        p = profile("openai");
        p.baseUrl = mock.baseUrl() + "/v1/chat/completions";
        p.endpointPath = "/chat/completions";
        new LlmClient(p).complete("s", "u", 0);
        eq("base 已含完整路径不重复拼接", "/v1/chat/completions", last().path);

        p = profile("openai");
        p.endpointPath = "chat/completions";
        new LlmClient(p).complete("s", "u", 0);
        eq("相对 endpointPath（无前导斜杠）", "/v1/chat/completions", last().path);

        p = profile("openai");
        p.endpointPath = "";
        new LlmClient(p).complete("s", "u", 0);
        eq("空 endpointPath 自动补 /chat/completions", "/v1/chat/completions", last().path);

        p = profile("openai");
        p.endpointPath = mock.baseUrl() + "/custom/openai";
        new LlmClient(p).complete("s", "u", 0);
        eq("完整 URL endpointPath 优先", "/custom/openai", last().path);

        p = profile("openai");
        p.endpointPath = mock.baseUrl() + "/custom/query?x=1&y=2";
        new LlmClient(p).complete("s", "u", 0);
        req = last();
        eq("完整 URL 带 query 路径", "/custom/query", req.path);
        eq("完整 URL 带 query 参数 x", "1", req.queryParam("x"));
        eq("完整 URL 带 query 参数 y", "2", req.queryParam("y"));

        p = profile("openai");
        p.baseUrl = mock.hostPort() + "/v1";
        new LlmClient(p).complete("s", "u", 0);
        eq("无 scheme 的 127.0.0.1 自动补 http://", "/v1/chat/completions", last().path);
    }

    private static void testEndpointAnthropic() throws Exception {
        section = "端点拼接（Anthropic）";
        mock.setHandler(NetHarness::autoReply);
        ApiProfile p = profile("anthropic");
        mock.clearRequests();
        eq("Anthropic 非流式返回", "ANTHROPIC-OK", new LlmClient(p).complete("系统提示", "原文", 0));
        NetMock.Request req = last();
        eq("Anthropic 默认路径", "/v1/messages", req.path);
        eq("Anthropic x-api-key", "test-key-123", req.header("x-api-key"));
        eq("Anthropic anthropic-version", "2023-06-01", req.header("anthropic-version"));
        eq("Anthropic 无 Authorization", null, req.header("authorization"));

        p = profile("anthropic");
        p.endpointPath = "";
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic 空 endpointPath 补 /messages", "/v1/messages", last().path);

        p = profile("anthropic");
        p.baseUrl = mock.baseUrl() + "/v1/messages";
        p.endpointPath = "/messages";
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic base 已含 /messages", "/v1/messages", last().path);

        p = profile("anthropic");
        p.endpointPath = mock.baseUrl() + "/v1/messages";
        StreamResult stream = doStream(p);
        eq("Anthropic 完整 URL 流式文本", "ANTHROPIC-OK", join(stream.deltas));
        req = last();
        eq("Anthropic 完整 URL 流式路径", "/v1/messages", req.path);
        eq("Anthropic 完整 URL 流式补 stream=true", "true", req.queryParam("stream"));

        p = profile("anthropic");
        p.endpointPath = mock.baseUrl() + "/v1/messages?stream=true";
        StreamResult again = doStream(p);
        eq("Anthropic 已有 stream=true 时文本正常", "ANTHROPIC-OK", join(again.deltas));
        eq("Anthropic stream 参数不重复", 1, last().queryParamCount("stream"));

        p = profile("anthropic");
        p.endpointPath = mock.baseUrl() + "/v1/messages";
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic 完整 URL 非流式不加 stream=true", null, last().queryParam("stream"));
    }

    private static void testEndpointGemini() throws Exception {
        section = "端点拼接（Gemini）";
        mock.setHandler(NetHarness::autoReply);
        ApiProfile p = profile("gemini");
        mock.clearRequests();
        eq("Gemini 非流式返回", "GEMINI-OK", new LlmClient(p).complete("系统提示", "原文", 0));
        NetMock.Request req = last();
        eq("Gemini generateContent 路径", "/v1beta/models/gemini-2.5-flash:generateContent", req.path);
        eq("Gemini 非流式无 alt=sse", null, req.queryParam("alt"));

        p = profile("gemini");
        StreamResult stream = doStream(p);
        eq("Gemini 流式文本", "GEMINI-OK", join(stream.deltas));
        req = last();
        eq("Gemini streamGenerateContent 路径", "/v1beta/models/gemini-2.5-flash:streamGenerateContent", req.path);
        eq("Gemini 流式 alt=sse", "sse", req.queryParam("alt"));

        p = profile("gemini");
        p.model = "models/gemini-2.5-flash";
        new LlmClient(p).complete("s", "u", 0);
        eq("Gemini model 的 models/ 前缀被去掉", "/v1beta/models/gemini-2.5-flash:generateContent", last().path);

        p = profile("gemini");
        p.baseUrl = mock.baseUrl() + "/v1beta/models";
        p.model = "gemini-2.5-flash";
        new LlmClient(p).complete("s", "u", 0);
        eq("Gemini base 以 /models 结尾", "/v1beta/models/gemini-2.5-flash:generateContent", last().path);

        p = profile("gemini");
        p.baseUrl = mock.baseUrl() + "/v1beta/";
        new LlmClient(p).complete("s", "u", 0);
        eq("Gemini base 末尾斜杠", "/v1beta/models/gemini-2.5-flash:generateContent", last().path);

        // 自定义完整 URL endpointPath 优先：Gemini 不再自动拼 models 路径
        mock.setHandler(request -> NetMock.Response.json(200,
                geminiJsonBody(new String[]{"GEMINI-CUSTOM"}, new boolean[]{false}, "STOP")));
        p = profile("gemini");
        p.endpointPath = mock.baseUrl() + "/custom/gemini";
        mock.clearRequests();
        eq("Gemini 完整 URL endpointPath 优先返回文本", "GEMINI-CUSTOM",
                new LlmClient(p).complete("系统提示", "原文", 0));
        eq("Gemini 完整 URL endpointPath 优先路径", "/custom/gemini", last().path);
        check("Gemini 完整 URL 时不再拼 models 路径",
                !last().path.contains("/models/") && !last().path.contains(":generateContent"), "path=" + last().path);
        JSONObject customBody = lastJson();
        check("Gemini 完整 URL 时请求体仍为 Gemini 结构",
                customBody.has("contents") && customBody.has("systemInstruction"), "body=" + customBody);

        mock.setHandler(request -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(sseData(geminiCandidate(new String[]{"GEMINI-CUSTOM-STREAM"}, new boolean[]{false}, "STOP")));
            return r;
        });
        p = profile("gemini");
        p.endpointPath = mock.baseUrl() + "/custom/gemini-stream?trace=qa";
        StreamResult customStream = doStream(p);
        eq("Gemini 完整 URL 流式文本", "GEMINI-CUSTOM-STREAM", join(customStream.deltas));
        eq("Gemini 完整 URL 流式路径", "/custom/gemini-stream", last().path);
        eq("Gemini 完整 URL 流式保留自定义 query", "qa", last().queryParam("trace"));
        eq("Gemini 完整 URL 流式不自动加 alt", null, last().queryParam("alt"));
    }

    private static void testOpenAiStream() throws Exception {
        section = "OpenAI 流式（SSE/推理字段）";
        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(": keep-alive comment\n\n");
            r.push(sseData(openAiChoice(new JSONObject().put("role", "assistant").put("content", ""), null)));
            r.push(sseData(openAiChoice(new JSONObject().put("reasoning_content", "先想一想"), null)));
            r.push(sseData(openAiChoice(new JSONObject().put("reasoning", "再推理"), null)));
            r.push(sseData(openAiChoice(new JSONObject().put("content", "你"), null)));
            r.push(sseData(openAiChoice(new JSONObject().put("content", "好，世界"), null)));
            r.push(sseData(openAiChoice(new JSONObject(), "stop")));
            r.push("data: [DONE]\n\n");
            return r;
        });
        ApiProfile p = profile("openai");
        StreamResult result = doStream(p);
        eq("onDelta 拼接", "你好，世界", join(result.deltas));
        eq("空 content 不触发 onDelta（次数）", 2, result.deltas.size());
        eq("onReasoning 拼接（reasoning_content + reasoning）", "先想一想再推理", result.reasoning.toString());
        eq("finishReason=stop", "stop", result.client.getFinishReason());
        check("未截断", !result.client.isTruncated(), "");
        check("未取消", !result.client.isCancelled(), "");

        JSONObject body = lastJson();
        eq("请求 stream=true", true, body.optBoolean("stream"));
        JSONArray messages = body.optJSONArray("messages");
        eq("messages 数量", 2, messages == null ? -1 : messages.length());
        if (messages != null && messages.length() == 2) {
            eq("system 消息", "系统提示", messages.getJSONObject(0).optString("content"));
            eq("user 消息", "原文", messages.getJSONObject(1).optString("content"));
        }

        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(sseData(openAiChoice(new JSONObject().put("content", "部分"), null)));
            r.push(sseData(openAiChoice(new JSONObject(), "length")));
            return r;
        });
        StreamResult truncated = doStream(profile("openai"));
        eq("流式 finish_reason=length 文本", "部分", join(truncated.deltas));
        check("流式 length 标记 truncated", truncated.client.isTruncated(), "");
        eq("流式 finishReason=length", "length", truncated.client.getFinishReason());
    }

    private static void testOpenAiComplete() throws Exception {
        section = "OpenAI 非流式与容错";
        ApiProfile p = profile("openai");

        mock.setHandler(req -> NetMock.Response.json(200, openAiJsonBody("完整译文")));
        LlmClient client = new LlmClient(p);
        eq("complete 返回文本", "完整译文", client.complete("系统提示", "原文", 0));
        eq("非流式 finishReason=stop", "stop", client.getFinishReason());
        check("非流式未截断", !client.isTruncated(), "");
        eq("非流式请求 stream=false", false, lastJson().optBoolean("stream"));

        mock.setHandler(req -> NetMock.Response.json(200,
                "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"数组\"},{\"type\":\"text\",\"text\":\"内容\"}]},\"finish_reason\":\"stop\"}]}"));
        eq("content 为数组时提取文本", "数组内容", new LlmClient(p).complete("s", "u", 0));

        mock.setHandler(req -> NetMock.Response.json(200,
                "{\"choices\":[{\"message\":{\"content\":null,\"reasoning_content\":\"思考中\"},\"finish_reason\":\"stop\"}]}"));
        LlmException reasoningOnly = expectError("仅推理内容无正文", () -> new LlmClient(p).complete("s", "u", 0));
        if (reasoningOnly != null) check("仅推理内容文案", reasoningOnly.getMessage().contains("推理内容"), msg(reasoningOnly));

        mock.setHandler(req -> NetMock.Response.json(200,
                "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}"));
        LlmClient truncClient = new LlmClient(p);
        LlmException lengthError = null;
        try {
            truncClient.complete("s", "u", 0);
        } catch (LlmException e) {
            lengthError = e;
        }
        check("finish_reason=length 抛 LlmException", lengthError != null, "未抛异常");
        if (lengthError != null) {
            check("length 错误文案含最大 token", lengthError.getMessage().contains("最大 token"), msg(lengthError));
            check("length 错误不可重试", !lengthError.retryable, "");
            eq("length 错误 status", 0, lengthError.status);
        }
        check("length 错误后 isTruncated=true", truncClient.isTruncated(), "");

        mock.setHandler(req -> NetMock.Response.json(200, ""));
        LlmException empty = expectError("空响应", () -> new LlmClient(p).complete("s", "u", 0));
        if (empty != null) {
            check("空响应文案可读", empty.getMessage().contains("空响应"), msg(empty));
            check("空响应可重试", empty.retryable, "");
        }

        mock.setHandler(req -> NetMock.Response.json(200, "<html>oops</html>"));
        LlmException notJson = expectError("非 JSON 响应", () -> new LlmClient(p).complete("s", "u", 0));
        if (notJson != null) check("非 JSON 文案", notJson.getMessage().contains("不是合法 JSON"), msg(notJson));

        mock.setHandler(req -> NetMock.Response.json(200, "{\"error\":{\"message\":\"quota exceeded\"}}"));
        LlmException errorObject = expectError("200 但带 error 对象", () -> new LlmClient(p).complete("s", "u", 0));
        if (errorObject != null) {
            check("error 对象文案", errorObject.getMessage().contains("接口返回错误"), msg(errorObject));
            check("error 对象原因保留", errorObject.getMessage().contains("quota exceeded"), msg(errorObject));
        }

        mock.setHandler(req -> NetMock.Response.json(200, openAiJsonBody("非流式JSON文本")));
        StreamResult nonSse = doStream(p);
        eq("服务端忽略 stream 返回 JSON 时 onDelta 全文", "非流式JSON文本", join(nonSse.deltas));
        eq("服务端忽略 stream 时 onDelta 次数", 1, nonSse.deltas.size());
        eq("该请求体仍声明 stream=true", true, lastJson().optBoolean("stream"));

        mock.setHandler(req -> NetMock.Response.json(200, ""));
        LlmException emptyStream = expectError("流式空响应", () -> doStream(profile("openai")));
        if (emptyStream != null) check("流式空响应文案可读", emptyStream.getMessage() != null
                && !emptyStream.getMessage().isEmpty(), msg(emptyStream));
    }

    private static void testHttpErrors() throws Exception {
        section = "HTTP 错误状态码";
        mock.setHandler(req -> NetMock.Response.json(429, "{\"error\":{\"message\":\"rate limited\"}}"));
        LlmException e429 = expectError("429 complete 抛异常", () -> new LlmClient(profile("openai")).complete("s", "u", 0));
        if (e429 != null) {
            eq("429 status", 429, e429.status);
            check("429 retryable=true", e429.retryable, "");
            check("429 文案含限流", e429.getMessage().contains("限流"), msg(e429));
            check("429 保留服务端原因", e429.getMessage().contains("rate limited"), msg(e429));
        }
        LlmException e429s = expectError("429 stream 抛异常", () -> doStream(profile("openai")));
        if (e429s != null) {
            eq("429 流式 status", 429, e429s.status);
            check("429 流式 retryable=true", e429s.retryable, "");
        }

        mock.setHandler(req -> NetMock.Response.json(401, "{\"error\":{\"message\":\"invalid key\"}}"));
        LlmException e401 = expectError("401 抛异常", () -> new LlmClient(profile("openai")).complete("s", "u", 0));
        if (e401 != null) {
            eq("401 status", 401, e401.status);
            check("401 不重试", !e401.retryable, "");
            check("401 文案含认证失败", e401.getMessage().contains("认证失败"), msg(e401));
        }

        mock.setHandler(req -> NetMock.Response.json(500, "<html><body>Bad Gateway</body></html>"));
        LlmException e500 = expectError("500 抛异常", () -> new LlmClient(profile("openai")).complete("s", "u", 0));
        if (e500 != null) {
            eq("500 status", 500, e500.status);
            check("500 retryable=true", e500.retryable, "");
            check("500 文案含服务商不可用", e500.getMessage().contains("服务商暂时不可用"), msg(e500));
            check("500 HTML 错误体可读", e500.getMessage().contains("Bad Gateway"), msg(e500));
        }

        mock.setHandler(req -> NetMock.Response.json(404, "{\"error\":{\"message\":\"model not found\"}}"));
        LlmException e404 = expectError("404 抛异常", () -> new LlmClient(profile("openai")).complete("s", "u", 0));
        if (e404 != null) {
            eq("404 status", 404, e404.status);
            check("404 不重试", !e404.retryable, "");
            check("404 文案", e404.getMessage().contains("接口或模型不存在"), msg(e404));
        }

        mock.setHandler(req -> NetMock.Response.json(402, "{\"error\":{\"message\":\"balance\"}}"));
        LlmException e402 = expectError("402 抛异常", () -> new LlmClient(profile("openai")).complete("s", "u", 0));
        if (e402 != null) {
            check("402 文案含额度不足", e402.getMessage().contains("账户额度不足"), msg(e402));
            check("402 不重试", !e402.retryable, "");
        }
    }

    private static void testAnthropic() throws Exception {
        section = "Anthropic 协议";
        mock.setHandler(req -> anthropicSseReply("end_turn", "你好，", "世界"));
        ApiProfile p = profile("anthropic");
        StreamResult stream = doStream(p);
        eq("Anthropic 流式文本", "你好，世界", join(stream.deltas));
        eq("Anthropic 流式 stop_reason", "end_turn", stream.client.getFinishReason());
        check("Anthropic 流式未截断", !stream.client.isTruncated(), "");
        JSONObject streamBody = lastJson();
        eq("Anthropic 流式 body stream=true", true, streamBody.optBoolean("stream"));
        check("Anthropic 流式 body 含 max_tokens", streamBody.has("max_tokens"), "body=" + streamBody);

        mock.setHandler(req -> NetMock.Response.json(200, anthropicJsonBody("完整文本", "end_turn")));
        LlmClient client = new LlmClient(p);
        eq("Anthropic 非流式文本", "完整文本", client.complete("系统提示", "原文", 0));
        eq("Anthropic 非流式 stop_reason", "end_turn", client.getFinishReason());
        check("Anthropic 非流式未截断", !client.isTruncated(), "");
        JSONObject body = lastJson();
        eq("Anthropic model", "claude-sonnet-4-5", body.optString("model"));
        eq("Anthropic system", "系统提示", body.optString("system"));
        JSONArray messages = body.optJSONArray("messages");
        eq("Anthropic messages 数量", 1, messages == null ? -1 : messages.length());
        if (messages != null && messages.length() == 1) {
            eq("Anthropic user 内容", "原文", messages.getJSONObject(0).optString("content"));
            eq("Anthropic user role", "user", messages.getJSONObject(0).optString("role"));
        }
        eq("Anthropic unlimited 时 max_tokens=64000（必填）", 64000, body.optInt("max_tokens"));
        eq("Anthropic temperature", 0.3, body.optDouble("temperature"));
        check("Anthropic 非流式 body 无 stream", !body.has("stream"), "body=" + body);

        mock.setHandler(req -> NetMock.Response.json(200,
                "{\"content\":[{\"type\":\"text\",\"text\":\"部分A\"},{\"type\":\"tool_use\",\"id\":\"x\"}],\"stop_reason\":\"end_turn\"}"));
        eq("Anthropic 非 text 块被忽略", "部分A", new LlmClient(p).complete("s", "u", 0));

        p = profile("anthropic");
        p.unlimitedOutput = false;
        p.maxOutputTokens = 1234;
        mock.setHandler(req -> NetMock.Response.json(200, anthropicJsonBody("ok", "end_turn")));
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic 限制输出时 max_tokens=1234", 1234, lastJson().optInt("max_tokens"));
        new LlmClient(p).complete("s", "u", 777);
        eq("Anthropic maxOverride=777", 777, lastJson().optInt("max_tokens"));

        mock.setHandler(req -> anthropicSseReply("max_tokens", "截断"));
        StreamResult trunc = doStream(profile("anthropic"));
        check("Anthropic stop_reason=max_tokens 截断", trunc.client.isTruncated(), "");
        eq("Anthropic max_tokens finishReason", "max_tokens", trunc.client.getFinishReason());

        mock.setHandler(req -> NetMock.Response.json(200, anthropicJsonBody("ANTHROPIC-JSON", "end_turn")));
        StreamResult nonSse = doStream(profile("anthropic"));
        eq("Anthropic 忽略 stream 返回 JSON 时 onDelta 全文", "ANTHROPIC-JSON", join(nonSse.deltas));
        eq("Anthropic 忽略 stream 时 onDelta 次数", 1, nonSse.deltas.size());

        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push("event: error\ndata: {\"type\":\"error\",\"error\":{\"message\":\"overloaded\"}}\n\n");
            return r;
        });
        LlmException errorEvent = expectError("Anthropic SSE error 事件", () -> doStream(profile("anthropic")));
        if (errorEvent != null) {
            check("Anthropic SSE error 文案", errorEvent.getMessage().contains("接口返回错误"), msg(errorEvent));
            check("Anthropic SSE error 原因", errorEvent.getMessage().contains("overloaded"), msg(errorEvent));
        }

        mock.setHandler(req -> NetMock.Response.json(200, "{\"content\":[],\"stop_reason\":\"end_turn\"}"));
        LlmException noText = expectError("Anthropic 无文本内容", () -> new LlmClient(profile("anthropic")).complete("s", "u", 0));
        if (noText != null) check("Anthropic 无文本文案", noText.getMessage().contains("没有文本内容"), msg(noText));
    }

    private static void testGemini() throws Exception {
        section = "Gemini 协议";
        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(sseData(geminiCandidate(new String[]{"你好", "内部思考"}, new boolean[]{false, true}, null)));
            r.push(sseData(geminiCandidate(new String[]{"，世界"}, new boolean[]{false}, "STOP")));
            return r;
        });
        ApiProfile p = profile("gemini");
        StreamResult stream = doStream(p);
        eq("Gemini 流式文本（thought 块被忽略）", "你好，世界", join(stream.deltas));
        eq("Gemini 流式 finishReason", "STOP", stream.client.getFinishReason());
        check("Gemini 流式未截断", !stream.client.isTruncated(), "");

        mock.setHandler(req -> NetMock.Response.json(200,
                geminiJsonBody(new String[]{"完整", "思考块", "文本"}, new boolean[]{false, true, false}, "STOP")));
        LlmClient client = new LlmClient(p);
        eq("Gemini 非流式文本", "完整文本", client.complete("系统提示", "原文", 0));
        eq("Gemini 非流式 finishReason", "STOP", client.getFinishReason());
        JSONObject body = lastJson();
        JSONObject sys = body.optJSONObject("systemInstruction");
        check("Gemini systemInstruction 存在", sys != null, "body=" + body);
        if (sys != null) {
            JSONArray sysParts = sys.optJSONArray("parts");
            eq("Gemini systemInstruction 文本", "系统提示",
                    sysParts == null ? null : sysParts.getJSONObject(0).optString("text"));
        }
        JSONArray contents = body.optJSONArray("contents");
        eq("Gemini contents 数量", 1, contents == null ? -1 : contents.length());
        if (contents != null && contents.length() == 1) {
            eq("Gemini user role", "user", contents.getJSONObject(0).optString("role"));
            eq("Gemini user 文本", "原文",
                    contents.getJSONObject(0).optJSONArray("parts").getJSONObject(0).optString("text"));
        }
        JSONObject generation = body.optJSONObject("generationConfig");
        check("Gemini generationConfig 存在", generation != null, "body=" + body);
        if (generation != null) {
            eq("Gemini temperature", 0.3, generation.optDouble("temperature"));
            check("Gemini unlimited 时无 maxOutputTokens", !generation.has("maxOutputTokens"), "generation=" + generation);
        }

        p = profile("gemini");
        p.unlimitedOutput = false;
        p.maxOutputTokens = 1234;
        mock.setHandler(req -> NetMock.Response.json(200, geminiJsonBody(new String[]{"ok"}, new boolean[]{false}, "STOP")));
        new LlmClient(p).complete("s", "u", 0);
        JSONObject gen = lastJson().optJSONObject("generationConfig");
        eq("Gemini 限制输出 maxOutputTokens=1234", 1234, gen == null ? -1 : gen.optInt("maxOutputTokens"));
        new LlmClient(p).complete("s", "u", 777);
        gen = lastJson().optJSONObject("generationConfig");
        eq("Gemini maxOverride=777", 777, gen == null ? -1 : gen.optInt("maxOutputTokens"));

        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(sseData(geminiCandidate(new String[]{"被截断"}, new boolean[]{false}, "MAX_TOKENS")));
            return r;
        });
        StreamResult truncated = doStream(profile("gemini"));
        check("Gemini MAX_TOKENS 截断标记", truncated.client.isTruncated(), "");
        eq("Gemini MAX_TOKENS finishReason", "MAX_TOKENS", truncated.client.getFinishReason());

        mock.setHandler(req -> NetMock.Response.json(200,
                geminiJsonBody(new String[]{"GEMINI-JSON"}, new boolean[]{false}, "STOP")));
        StreamResult nonSse = doStream(profile("gemini"));
        eq("Gemini 忽略 stream 返回 JSON 时 onDelta 全文", "GEMINI-JSON", join(nonSse.deltas));
        eq("Gemini 忽略 stream 时 onDelta 次数", 1, nonSse.deltas.size());

        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push("data: {\"error\":{\"message\":\"gemini boom\"}}\n\n");
            return r;
        });
        LlmException geminiError = expectError("Gemini SSE error", () -> doStream(profile("gemini")));
        if (geminiError != null) check("Gemini SSE error 文案", geminiError.getMessage().contains("gemini boom"), msg(geminiError));

        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push("data: {\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}\n\n");
            return r;
        });
        LlmException blocked = expectError("Gemini promptFeedback 阻断", () -> doStream(profile("gemini")));
        if (blocked != null) {
            check("Gemini 阻断文案", blocked.getMessage().contains("Gemini 拒绝"), msg(blocked));
            check("Gemini 阻断原因", blocked.getMessage().contains("SAFETY"), msg(blocked));
        }

        mock.setHandler(req -> NetMock.Response.json(200, "{\"candidates\":[]}"));
        LlmException noCandidate = expectError("Gemini 无候选", () -> new LlmClient(profile("gemini")).complete("s", "u", 0));
        if (noCandidate != null) check("Gemini 无候选文案", noCandidate.getMessage().contains("候选"), msg(noCandidate));

        NetMock.Request req = last();
        eq("Gemini x-goog-api-key", "test-key-123", req.header("x-goog-api-key"));
        eq("Gemini 无 Authorization", null, req.header("authorization"));
    }

    private static void testThinking() throws Exception {
        section = "DeepSeek 思考模式字段";
        mock.setHandler(NetHarness::autoReply);

        String[][] efforts = {{"high", "high"}, {"max", "max"}, {"low", "low"}, {"ultra", "high"}};
        for (String[] pair : efforts) {
            ApiProfile p = profile("openai");
            p.providerId = "deepseek";
            p.model = "deepseek-reasoner";
            p.thinkingEnabled = true;
            p.thinkingEffort = pair[0];
            new LlmClient(p).complete("s", "u", 0);
            JSONObject body = lastJson();
            JSONObject thinking = body.optJSONObject("thinking");
            check("effort=" + pair[0] + " thinking 对象存在", thinking != null, "body=" + body);
            if (thinking != null) eq("effort=" + pair[0] + " thinking.type", "enabled", thinking.optString("type"));
            eq("effort=" + pair[0] + " reasoning_effort=" + pair[1], pair[1], body.optString("reasoning_effort"));
            check("开启思考不发 temperature（effort=" + pair[0] + "）", !body.has("temperature"), "body=" + body);
        }

        ApiProfile p = profile("openai");
        p.model = "gpt-4o-mini";
        p.thinkingEnabled = true;
        p.thinkingEffort = "high";
        new LlmClient(p).complete("s", "u", 0);
        JSONObject customThinking = lastJson();
        JSONObject t1 = customThinking.optJSONObject("thinking");
        check("非 deepseek 自定义 API 开启思考仍发 thinking", t1 != null && "enabled".equals(t1.optString("type")),
                "body=" + customThinking);
        eq("非 deepseek 自定义 API reasoning_effort", "high", customThinking.optString("reasoning_effort"));
        check("非 deepseek 自定义 API 开启思考不发 temperature", !customThinking.has("temperature"), "");

        p = profile("openai");
        p.providerId = "deepseek";
        p.model = "deepseek-chat";
        p.thinkingEnabled = false;
        new LlmClient(p).complete("s", "u", 0);
        JSONObject disabled = lastJson();
        JSONObject t2 = disabled.optJSONObject("thinking");
        check("关闭思考且 provider=deepseek 发 disabled", t2 != null && "disabled".equals(t2.optString("type")),
                "body=" + disabled);
        check("disabled 不发 reasoning_effort", !disabled.has("reasoning_effort"), "body=" + disabled);
        eq("disabled 正常发送 temperature", 0.3, disabled.optDouble("temperature"));

        p = profile("openai");
        p.model = "deepseek-chat";
        p.thinkingEnabled = false;
        new LlmClient(p).complete("s", "u", 0);
        JSONObject byModel = lastJson();
        JSONObject t3 = byModel.optJSONObject("thinking");
        check("关闭思考且 model 含 deepseek 发 disabled", t3 != null && "disabled".equals(t3.optString("type")),
                "body=" + byModel);

        p = profile("openai");
        p.model = "gpt-4o-mini";
        p.thinkingEnabled = false;
        new LlmClient(p).complete("s", "u", 0);
        JSONObject normal = lastJson();
        check("普通自定义 API 关闭思考不传 thinking", !normal.has("thinking"), "body=" + normal);
        check("普通自定义 API 关闭思考不传 reasoning_effort", !normal.has("reasoning_effort"), "");
        eq("普通自定义 API temperature", 0.3, normal.optDouble("temperature"));

        check("isDeepSeek：providerId=deepseek", isDeepSeek("deepseek", "gpt-4o-mini"), "");
        check("isDeepSeek：model 含 deepseek", isDeepSeek("custom", "deepseek-reasoner"), "");
        check("isDeepSeek：普通模型为 false", !isDeepSeek("custom", "gpt-4o-mini"), "");

        p = profile("anthropic");
        p.thinkingEnabled = true;
        p.thinkingEffort = "max";
        new LlmClient(p).complete("s", "u", 0);
        JSONObject anthropicBody = lastJson();
        check("Anthropic 协议不发 OpenAI thinking 字段",
                !anthropicBody.has("thinking") && !anthropicBody.has("reasoning_effort"), "body=" + anthropicBody);

        p = profile("gemini");
        p.thinkingEnabled = true;
        p.thinkingEffort = "max";
        new LlmClient(p).complete("s", "u", 0);
        JSONObject geminiBody = lastJson();
        check("Gemini 协议不发 OpenAI thinking 字段",
                !geminiBody.has("thinking") && !geminiBody.has("reasoning_effort"), "body=" + geminiBody);
    }

    private static void testMaxTokens() throws Exception {
        section = "单次最大输出 max_tokens";
        mock.setHandler(NetHarness::autoReply);

        ApiProfile p = profile("openai");
        p.unlimitedOutput = true;
        p.maxOutputTokens = 4096;
        new LlmClient(p).complete("s", "u", 0);
        JSONObject body = lastJson();
        check("OpenAI unlimited 不带 max_tokens", !body.has("max_tokens"), "body=" + body);
        eq("OpenAI model", "gpt-4o-mini", body.optString("model"));

        p = profile("openai");
        p.unlimitedOutput = false;
        p.maxOutputTokens = 1234;
        new LlmClient(p).complete("s", "u", 0);
        eq("OpenAI 限制输出 max_tokens=1234", 1234, lastJson().optInt("max_tokens"));

        p.maxOutputTokens = 10;
        new LlmClient(p).complete("s", "u", 0);
        eq("OpenAI 限制输出下限 64", 64, lastJson().optInt("max_tokens"));

        p = profile("openai");
        p.unlimitedOutput = true;
        new LlmClient(p).complete("s", "u", 777);
        eq("OpenAI maxOverride=777 覆盖 unlimited", 777, lastJson().optInt("max_tokens"));

        mock.setHandler(NetHarness::autoReply);
        StreamResult stream = doStream(p);
        eq("OpenAI 流式 unlimited 文本", "OPENAI-OK", join(stream.deltas));
        check("OpenAI 流式 unlimited 不带 max_tokens", !lastJson().has("max_tokens"), "body=" + lastJson());

        p = profile("anthropic");
        p.unlimitedOutput = true;
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic unlimited max_tokens=64000", 64000, lastJson().optInt("max_tokens"));

        p = profile("anthropic");
        p.unlimitedOutput = false;
        p.maxOutputTokens = 1234;
        new LlmClient(p).complete("s", "u", 0);
        eq("Anthropic 限制输出 max_tokens=1234", 1234, lastJson().optInt("max_tokens"));
        new LlmClient(p).complete("s", "u", 777);
        eq("Anthropic maxOverride=777", 777, lastJson().optInt("max_tokens"));

        p = profile("gemini");
        p.unlimitedOutput = true;
        new LlmClient(p).complete("s", "u", 0);
        JSONObject gen = lastJson().optJSONObject("generationConfig");
        check("Gemini unlimited 无 maxOutputTokens", gen != null && !gen.has("maxOutputTokens"), "body=" + lastJson());

        p = profile("gemini");
        p.unlimitedOutput = false;
        p.maxOutputTokens = 1234;
        new LlmClient(p).complete("s", "u", 0);
        gen = lastJson().optJSONObject("generationConfig");
        eq("Gemini 限制输出 maxOutputTokens=1234", 1234, gen == null ? -1 : gen.optInt("maxOutputTokens"));
        new LlmClient(p).complete("s", "u", 777);
        gen = lastJson().optJSONObject("generationConfig");
        eq("Gemini maxOverride=777", 777, gen == null ? -1 : gen.optInt("maxOutputTokens"));
    }

    private static void testHeaders() throws Exception {
        section = "请求头与自定义端点";
        mock.setHandler(NetHarness::autoReply);

        ApiProfile p = profile("openai");
        p.extraHeaders = "{\"X-Test-Header\":\"v1\",\"X-Num\":42,\"X-Flag\":true}";
        new LlmClient(p).complete("s", "u", 0);
        NetMock.Request req = last();
        eq("自定义字符串头", "v1", req.header("X-Test-Header"));
        eq("自定义数字头按字符串发送", "42", req.header("X-Num"));
        eq("自定义布尔头按字符串发送", "true", req.header("X-Flag"));
        eq("默认 Authorization", "Bearer test-key-123", req.header("authorization"));
        check("默认 Content-Type", contains(req.header("content-type"), "application/json"), "content-type=" + req.header("content-type"));
        check("默认 User-Agent", contains(req.header("user-agent"), "YiDuNovel"), "user-agent=" + req.header("user-agent"));
        eq("非流式 Accept", "application/json", req.header("accept"));
        eq("默认 Accept-Encoding", "identity", req.header("accept-encoding"));

        p.extraHeaders = "{\"Authorization\":\"Bearer override-key\"}";
        new LlmClient(p).complete("s", "u", 0);
        eq("自定义头覆盖 Authorization", "Bearer override-key", last().header("authorization"));

        p.extraHeaders = "not-a-json";
        eq("非法 extraHeaders JSON 不影响请求", "OPENAI-OK", new LlmClient(p).complete("s", "u", 0));
        eq("非法 extraHeaders 时默认 Authorization 仍在", "Bearer test-key-123", last().header("authorization"));

        p.extraHeaders = "";
        eq("空 extraHeaders 请求成功", "OPENAI-OK", new LlmClient(p).complete("s", "u", 0));

        p = profile("anthropic");
        p.extraHeaders = "{\"X-Anthropic-Extra\":\"yes\"}";
        new LlmClient(p).complete("s", "u", 0);
        req = last();
        eq("Anthropic 自定义头附加", "yes", req.header("X-Anthropic-Extra"));
        eq("Anthropic 自定义头下 x-api-key 仍在", "test-key-123", req.header("x-api-key"));

        p = profile("openai");
        p.endpointPath = mock.baseUrl() + "/priority/endpoint";
        eq("完整 URL endpointPath 优先返回", "OPENAI-OK", new LlmClient(p).complete("s", "u", 0));
        eq("完整 URL endpointPath 优先路径", "/priority/endpoint", last().path);

        p = profile("openai");
        StreamResult stream = doStream(p);
        eq("流式返回正常", "OPENAI-OK", join(stream.deltas));
        check("流式 Accept 含 text/event-stream",
                contains(last().header("accept"), "text/event-stream"), "accept=" + last().header("accept"));
    }

    private static void testCancel() throws Exception {
        section = "取消 cancel()";
        mock.setHandler(req -> {
            NetMock.Response r = NetMock.Response.sse();
            r.push(sseData(openAiChoice(new JSONObject().put("content", "开始"), null)));
            r.push(sseData(openAiChoice(new JSONObject().put("content", "继续"), null)), 3000L);
            r.push("data: [DONE]\n\n");
            return r;
        });
        ApiProfile p = profile("openai");
        CountDownLatch firstDelta = new CountDownLatch(1);
        List<String> deltas = Collections.synchronizedList(new ArrayList<>());
        LlmClient client = new LlmClient(p);
        ExecutorService executor = newExecutor();
        Future<LlmException> future = executor.submit(() -> {
            try {
                client.stream("系统提示", "原文", 0, new LlmClient.StreamCallback() {
                    @Override
                    public void onDelta(String text) {
                        deltas.add(text);
                        firstDelta.countDown();
                    }
                });
                return null;
            } catch (LlmException e) {
                return e;
            }
        });
        boolean gotFirst = false;
        try {
            gotFirst = firstDelta.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        check("取消前收到首个 delta", gotFirst, "deltas=" + deltas);
        long start = System.nanoTime();
        client.cancel();
        LlmException cancelError = null;
        try {
            cancelError = future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            check("cancel 后流式在 5s 内结束", false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        check("cancel 后流式抛 cancelled", cancelError != null && cancelError.cancelled,
                cancelError == null ? "流式正常结束（未抛异常）" : "cancelled=" + cancelError.cancelled + " msg=" + cancelError.getMessage());
        if (cancelError != null) eq("cancelled 错误消息", "已取消", cancelError.getMessage());
        check("cancel 后 2s 内返回", elapsedMs < 2000, "elapsed=" + elapsedMs + "ms");
        check("isCancelled()=true", client.isCancelled(), "");
        executor.shutdownNow();

        mock.setHandler(NetHarness::autoReply);
        eq("取消后同一 client 可再次请求", "OPENAI-OK", client.complete("系统提示", "原文", 0));
        check("再次请求成功后 isCancelled=false", !client.isCancelled(), "");

        mock.setHandler(req -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return NetMock.Response.json(200, openAiJsonBody("慢响应"));
        });
        LlmClient slowClient = new LlmClient(profile("openai"));
        ExecutorService executor2 = newExecutor();
        Future<LlmException> future2 = executor2.submit(() -> {
            try {
                slowClient.complete("系统提示", "原文", 0);
                return null;
            } catch (LlmException e) {
                return e;
            }
        });
        try {
            Thread.sleep(400L);
        } catch (InterruptedException ignored) {
        }
        slowClient.cancel();
        LlmException completeCancel = null;
        try {
            completeCancel = future2.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            check("complete 取消后 5s 内结束", false, e.getClass().getSimpleName());
        }
        check("complete 进行中 cancel 抛 cancelled", completeCancel != null && completeCancel.cancelled,
                completeCancel == null ? "未抛异常（响应已完成）" : "msg=" + completeCancel.getMessage());
        executor2.shutdownNow();
    }

    private static void testTestConnection() throws Exception {
        section = "testConnection";
        mock.setHandler(req -> NetMock.Response.json(200, openAiJsonBody("OK")));
        eq("testConnection 正常返回文本", "OK", LlmClient.testConnection(profile("openai")));

        mock.setHandler(req -> NetMock.Response.json(200,
                "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"length\"}]}"));
        String truncated = LlmClient.testConnection(profile("openai"));
        check("testConnection 截断视为连接成功", truncated.contains("连接成功"), "返回=" + show(truncated));

        mock.setHandler(req -> NetMock.Response.json(401, "{\"error\":{\"message\":\"invalid key\"}}"));
        LlmException auth = expectError("testConnection 401 透传", () -> LlmClient.testConnection(profile("openai")));
        if (auth != null) {
            eq("testConnection 401 status", 401, auth.status);
            check("testConnection 401 不重试", !auth.retryable, "");
            check("testConnection 401 文案", auth.getMessage().contains("认证失败"), msg(auth));
        }
    }

    private static void testNetworkAndTimeout() throws Exception {
        section = "网络错误与读超时";
        int closedPort;
        try (ServerSocket temp = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closedPort = temp.getLocalPort();
        } catch (Exception e) {
            check("准备关闭端口", false, e.getClass().getSimpleName());
            return;
        }
        ApiProfile p = profile("openai");
        p.baseUrl = "http://127.0.0.1:" + closedPort + "/v1";
        LlmException refused = expectError("连接被拒绝 complete", () -> new LlmClient(p).complete("s", "u", 0));
        if (refused != null) {
            eq("网络错误 status=0", 0, refused.status);
            check("网络错误可重试", refused.retryable, "");
            check("网络错误文案含 网络请求失败", refused.getMessage().contains("网络请求失败"), msg(refused));
        }
        LlmException refusedStream = expectError("连接被拒绝 stream", () -> new LlmClient(p).stream("s", "u", 0,
                new LlmClient.StreamCallback() {
                    @Override
                    public void onDelta(String text) {
                    }
                }));
        if (refusedStream != null) {
            check("流式网络错误文案含 网络中断", refusedStream.getMessage().contains("网络中断"), msg(refusedStream));
        }

        // 读超时：mock 先回响应头，40s 后才回包体；LlmClient 读超时下限为 30s。
        mock.setHandler(req -> NetMock.Response.json(200, openAiJsonBody("超时后")).delayBeforeBody(40_000L));
        ApiProfile timeoutProfile = profile("openai");
        timeoutProfile.timeoutSec = 30;
        long started = System.currentTimeMillis();
        LlmException timeout = expectError("读超时 complete", () -> new LlmClient(timeoutProfile).complete("s", "u", 0));
        long elapsed = System.currentTimeMillis() - started;
        if (timeout != null) {
            check("超时文案含 模型响应超时", timeout.getMessage().contains("模型响应超时"), msg(timeout));
            check("超时可重试", timeout.retryable, "");
            eq("超时 status=0", 0, timeout.status);
        }
        check("超时约 30s 触发（25s~45s）", elapsed >= 25_000L && elapsed <= 45_000L, "elapsed=" + elapsed + "ms");
    }

    private static void testTokenLimiter() throws Exception {
        section = "TokenLimiter（依赖类轻量验证）";
        check("tpm<=0 直接放行", TokenLimiter.acquire("qa-tpm-unlimited", 0, 5000, null, null), "");
        String key = "qa-tpm-" + System.nanoTime();
        check("tpm 内第一次放行", TokenLimiter.acquire(key, 100, 10, null, null), "");
        check("tpm 内第二次放行（累计未超）", TokenLimiter.acquire(key, 100, 10, null, null), "");
        check("已取消时 acquire 返回 false", !TokenLimiter.acquire(key, 100, 10, () -> true, null), "");

        String limitedKey = "qa-tpm-limited-" + System.nanoTime();
        check("填满窗口", TokenLimiter.acquire(limitedKey, 10, 10, null, null), "");
        final java.util.concurrent.atomic.AtomicBoolean cancelFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
        final long[] waitedMs = {0L};
        boolean got = TokenLimiter.acquire(limitedKey, 10, 10, () -> cancelFlag.get(), millis -> {
            waitedMs[0] = millis;
            cancelFlag.set(true);
        });
        check("窗口不足时触发 Waiter 并在取消后返回 false", !got && waitedMs[0] > 0, "waited=" + waitedMs[0] + "ms");
    }

    // ==================== mock 默认应答 ====================

    private static NetMock.Response autoReply(NetMock.Request request) {
        boolean bodyStream = false;
        try {
            if (!request.body.isEmpty()) bodyStream = new JSONObject(request.body).optBoolean("stream", false);
        } catch (Exception ignored) {
        }
        String path = request.path;
        if (path.contains(":streamGenerateContent")) {
            return geminiSseReply("GEMINI-OK");
        }
        if (path.contains(":generateContent")) {
            return NetMock.Response.json(200, geminiJsonBody(new String[]{"GEMINI-OK"}, new boolean[]{false}, "STOP"));
        }
        if (path.endsWith("/messages")) {
            return bodyStream ? anthropicSseReply("end_turn", "ANTHROPIC-OK")
                    : NetMock.Response.json(200, anthropicJsonBody("ANTHROPIC-OK", "end_turn"));
        }
        return bodyStream ? openAiSseReply("OPENAI-OK")
                : NetMock.Response.json(200, openAiJsonBody("OPENAI-OK"));
    }

    private static NetMock.Response openAiSseReply(String... contents) {
        NetMock.Response response = NetMock.Response.sse();
        for (String content : contents) {
            response.push(sseData(openAiChoice(new JSONObject().put("content", content), null)));
        }
        response.push(sseData(openAiChoice(new JSONObject(), "stop")));
        response.push("data: [DONE]\n\n");
        return response;
    }

    private static NetMock.Response anthropicSseReply(String stopReason, String... texts) {
        NetMock.Response response = NetMock.Response.sse();
        response.push("event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_1\"}}\n\n");
        for (String text : texts) {
            response.push("event: content_block_delta\ndata: "
                    + new JSONObject().put("type", "content_block_delta")
                    .put("delta", new JSONObject().put("type", "text_delta").put("text", text))
                    + "\n\n");
        }
        response.push("event: message_delta\ndata: "
                + new JSONObject().put("type", "message_delta")
                .put("delta", new JSONObject().put("stop_reason", stopReason))
                + "\n\n");
        response.push("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n");
        return response;
    }

    private static NetMock.Response geminiSseReply(String text) {
        NetMock.Response response = NetMock.Response.sse();
        response.push(sseData(geminiCandidate(new String[]{text}, new boolean[]{false}, "STOP")));
        return response;
    }

    private static JSONObject openAiChoice(JSONObject delta, String finishReason) {
        JSONObject choice = new JSONObject();
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
        return new JSONObject().put("choices", new JSONArray().put(choice));
    }

    private static String sseData(JSONObject data) {
        return "data: " + data + "\n\n";
    }

    private static JSONObject geminiCandidate(String[] texts, boolean[] thoughts, String finishReason) {
        JSONArray parts = new JSONArray();
        for (int i = 0; i < texts.length; i++) {
            JSONObject part = new JSONObject().put("text", texts[i]);
            if (i < thoughts.length && thoughts[i]) part.put("thought", true);
            parts.put(part);
        }
        JSONObject candidate = new JSONObject().put("content", new JSONObject().put("parts", parts));
        if (finishReason != null) candidate.put("finishReason", finishReason);
        return new JSONObject().put("candidates", new JSONArray().put(candidate));
    }

    private static String openAiJsonBody(String content) {
        JSONObject message = new JSONObject().put("role", "assistant").put("content", content);
        JSONObject choice = new JSONObject().put("message", message).put("finish_reason", "stop");
        return new JSONObject().put("choices", new JSONArray().put(choice)).toString();
    }

    private static String anthropicJsonBody(String text, String stopReason) {
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", text));
        return new JSONObject().put("content", content).put("stop_reason", stopReason).toString();
    }

    private static String geminiJsonBody(String[] texts, boolean[] thoughts, String finishReason) {
        return geminiCandidate(texts, thoughts, finishReason).toString();
    }

    // ==================== 断言/工具 ====================

    private interface Body {
        void run() throws Exception;
    }

    private interface Call {
        void run() throws LlmException;
    }

    private static final class StreamResult {
        List<String> deltas = new ArrayList<>();
        StringBuilder reasoning = new StringBuilder();
        LlmClient client;
    }

    private static void run(String name, Body body) {
        section = name;
        System.out.println();
        System.out.println("---- " + name + " ----");
        try {
            body.run();
        } catch (Throwable t) {
            check("[" + name + "] 未抛出未预期异常", false,
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            String message = "[" + section + "] " + name + (detail == null || detail.isEmpty() ? "" : "  -> " + detail);
            failures.add(message);
            System.out.println("FAIL " + name + (detail == null || detail.isEmpty() ? "" : "  -> " + detail));
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        check(name, ok, ok ? "" : "expected=" + show(expected) + " actual=" + show(actual));
    }

    private static String show(Object value) {
        if (value == null) return "null";
        String s = String.valueOf(value);
        if (s.length() > 400) s = s.substring(0, 400) + "…(len=" + s.length() + ")";
        return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String msg(LlmException e) {
        return e == null ? "null" : show(e.getMessage());
    }

    private static boolean contains(String text, String part) {
        return text != null && text.contains(part);
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) sb.append(part);
        return sb.toString();
    }

    private static LlmException expectError(String name, Call call) {
        try {
            call.run();
            check(name, false, "未抛 LlmException");
            return null;
        } catch (LlmException e) {
            check(name, true, "");
            return e;
        } catch (Throwable t) {
            check(name, false, "抛出了 " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    private static ApiProfile profile(String protocol) {
        ApiProfile p = new ApiProfile();
        p.protocol = protocol;
        p.providerId = "custom";
        p.apiKey = "test-key-123";
        p.temperature = 0.3;
        p.unlimitedOutput = true;
        p.maxOutputTokens = 4096;
        p.thinkingEnabled = false;
        p.thinkingEffort = "high";
        p.timeoutSec = 180;
        if ("anthropic".equals(protocol)) {
            p.baseUrl = mock.baseUrl() + "/v1";
            p.endpointPath = "/messages";
            p.model = "claude-sonnet-4-5";
        } else if ("gemini".equals(protocol)) {
            p.baseUrl = mock.baseUrl() + "/v1beta";
            p.endpointPath = "";
            p.model = "gemini-2.5-flash";
        } else {
            p.baseUrl = mock.baseUrl() + "/v1";
            p.endpointPath = "/chat/completions";
            p.model = "gpt-4o-mini";
        }
        return p;
    }

    private static StreamResult doStream(ApiProfile profile) throws LlmException {
        mock.clearRequests();
        StreamResult result = new StreamResult();
        result.client = new LlmClient(profile);
        result.client.stream("系统提示", "原文", 0, new LlmClient.StreamCallback() {
            @Override
            public void onDelta(String text) {
                result.deltas.add(text);
            }

            @Override
            public void onReasoning(String text) {
                result.reasoning.append(text);
            }
        });
        return result;
    }

    private static boolean isDeepSeek(String providerId, String model) {
        ApiProfile p = profile("openai");
        p.providerId = providerId;
        p.model = model;
        return p.isDeepSeek();
    }

    private static NetMock.Request last() {
        NetMock.Request request = mock.lastRequest();
        if (request == null) throw new IllegalStateException("mock 没有收到请求");
        return request;
    }

    private static JSONObject lastJson() {
        return new JSONObject(last().body);
    }

    private static ExecutorService newExecutor() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "net-harness");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(factory);
    }

    private static void observe(String name, boolean ok, String detail) {
        String line = name + " -> " + (ok ? "符合预期" : "需要关注") + "：" + detail;
        observations.add(line);
        System.out.println("OBSERVE " + line);
    }

    private static void summary() {
        System.out.println();
        System.out.println("================ 汇总 ================");
        System.out.println("测试项: " + (passed + failed) + "    PASS: " + passed + "    FAIL: " + failed);
        if (!failures.isEmpty()) {
            System.out.println("失败清单:");
            for (String failure : failures) System.out.println("  - " + failure);
        }
        if (!observations.isEmpty()) {
            System.out.println("附加观察:");
            for (String observation : observations) System.out.println("  - " + observation);
        }
        System.out.println("耗时: " + String.format(java.util.Locale.US, "%.2fs",
                (System.currentTimeMillis() - startedAt) / 1000.0));
        System.out.println(failed == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        System.out.flush();
    }
}
