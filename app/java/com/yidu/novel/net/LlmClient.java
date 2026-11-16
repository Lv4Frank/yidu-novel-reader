package com.yidu.novel.net;

import com.yidu.novel.model.ApiProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/**
 * 大模型接口客户端：支持 OpenAI 兼容 / Anthropic / Gemini 三种协议，
 * 支持 SSE 流式输出，并按 DeepSeek 思考模式文档发送 thinking / reasoning_effort。
 */
public class LlmClient {
    private final ApiProfile api;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    private volatile String finishReason = "";
    private volatile boolean truncated;

    public interface StreamCallback {
        void onDelta(String text);

        default void onReasoning(String text) {
        }
    }

    public LlmClient(ApiProfile api) {
        this.api = api;
    }

    public String complete(String system, String user, int maxTokensOverride) throws LlmException {
        prepare();
        HttpURLConnection conn = null;
        try {
            String body = buildBody(system, user, maxTokensOverride, false);
            conn = send(body, false);
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String response = stream == null ? "" : readAll(stream);
            if (status < 200 || status >= 300) throw httpError(status, response);
            if (response.trim().isEmpty()) {
                throw new LlmException("接口返回了空响应，请检查模型是否可用", status, true, false);
            }
            return parseResponse(response);
        } catch (LlmException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            if (cancelled) throw LlmException.cancelled();
            throw new LlmException("模型响应超时，请检查网络或稍后重试。", 0, true, false);
        } catch (IOException e) {
            if (cancelled) throw LlmException.cancelled();
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw LlmException.network("网络请求失败：" + msg + "\n请检查网络、Base URL 是否可访问。");
        } catch (Exception e) {
            if (cancelled) throw LlmException.cancelled();
            throw new LlmException("请求处理失败：" + safe(e.getMessage()), 0, false, false);
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
            connection = null;
        }
    }

    public void stream(String system, String user, int maxTokensOverride, StreamCallback callback) throws LlmException {
        prepare();
        HttpURLConnection conn = null;
        try {
            String body = buildBody(system, user, maxTokensOverride, true);
            conn = send(body, true);
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                String error = conn.getErrorStream() == null ? "" : readAll(conn.getErrorStream());
                throw httpError(status, error);
            }
            InputStream raw = conn.getInputStream();
            if (raw == null) throw new LlmException("接口没有返回内容", status, true, false);
            BufferedInputStream buffered = new BufferedInputStream(raw, 16384);
            String contentType = conn.getContentType();
            boolean sse = looksLikeSse(buffered, contentType);
            if (!sse) {
                String response = readAll(buffered);
                if (cancelled) throw LlmException.cancelled();
                String text = parseResponse(response);
                if (!text.isEmpty()) callback.onDelta(text);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(buffered, StandardCharsets.UTF_8));
            if ("anthropic".equals(api.protocol)) {
                readAnthropicSse(reader, callback);
            } else if ("gemini".equals(api.protocol)) {
                readGeminiSse(reader, callback);
            } else {
                readOpenAiSse(reader, callback);
            }
            if (cancelled) throw LlmException.cancelled();
        } catch (LlmException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            if (cancelled) throw LlmException.cancelled();
            throw new LlmException("等待模型输出超时，已中止本次请求。可尝试降低单次输出、关闭思考模式或更换模型。", 0, true, false);
        } catch (IOException e) {
            if (cancelled) throw LlmException.cancelled();
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw LlmException.network("网络中断：" + msg);
        } catch (Exception e) {
            if (cancelled) throw LlmException.cancelled();
            throw new LlmException("流式解析失败：" + safe(e.getMessage()), 0, false, false);
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception ignored) {
                }
            }
            connection = null;
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection current = connection;
        if (current != null) {
            try {
                current.disconnect();
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public static String testConnection(ApiProfile profile) throws LlmException {
        LlmClient client = new LlmClient(profile);
        try {
            String text = client.complete("你是接口连接测试助手。", "请只回复两个大写字母：OK。不要解释，不要思考。", 1024);
            if (text == null || text.trim().isEmpty()) return "连接成功（接口未返回文本）";
            return text.trim();
        } catch (LlmException e) {
            if (client.isTruncated() || (e.getMessage() != null
                    && (e.getMessage().contains("最大 token") || e.getMessage().contains("推理内容")))) {
                return "连接成功（接口可用；测试响应因思考模式或输出上限被截断）";
            }
            throw e;
        } finally {
            client.cancel();
        }
    }

    private void prepare() throws LlmException {
        cancelled = false;
        finishReason = "";
        truncated = false;
        if (api == null) throw new LlmException("尚未配置 API", 0, false, false);
        if (isBlank(api.baseUrl)) throw new LlmException("API Base URL 为空，请先在 API 设置中填写", 0, false, false);
        if (isBlank(api.model)) throw new LlmException("模型名为空，请先在 API 设置中填写", 0, false, false);
    }

    private HttpURLConnection send(String body, boolean stream) throws Exception {
        String endpoint = buildEndpoint(stream);
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        connection = conn;
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(25_000);
        conn.setReadTimeout(Math.max(30, api.timeoutSec) * 1000);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", stream ? "text/event-stream, application/json" : "application/json");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("User-Agent", "YiDuNovel/1.0 (Android)");
        applyAuth(conn);
        applyExtraHeaders(conn);

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }
        return conn;
    }

    private void applyAuth(HttpURLConnection conn) {
        String key = api.apiKey == null ? "" : api.apiKey.trim();
        if ("anthropic".equals(api.protocol)) {
            if (!key.isEmpty()) conn.setRequestProperty("x-api-key", key);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else if ("gemini".equals(api.protocol)) {
            if (!key.isEmpty()) conn.setRequestProperty("x-goog-api-key", key);
        } else {
            if (!key.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + key);
        }
    }

    private void applyExtraHeaders(HttpURLConnection conn) {
        if (isBlank(api.extraHeaders)) return;
        try {
            JSONObject headers = new JSONObject(api.extraHeaders.trim());
            Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = headers.opt(key);
                if (value == null || value == JSONObject.NULL) continue;
                try {
                    conn.setRequestProperty(key, String.valueOf(value));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String buildBody(String system, String user, int maxOverride, boolean stream) throws LlmException {
        try {
            int maxTokens = maxOverride > 0 ? maxOverride
                    : (api.unlimitedOutput ? 0 : Math.max(64, api.maxOutputTokens));
            if ("anthropic".equals(api.protocol)) {
                JSONObject body = new JSONObject();
                body.put("model", api.model.trim());
                body.put("system", system == null ? "" : system);
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "user").put("content", user));
                body.put("messages", messages);
                body.put("max_tokens", maxTokens > 0 ? maxTokens : 64000);
                if (api.temperature >= 0 && api.temperature <= 2) body.put("temperature", api.temperature);
                if (stream) body.put("stream", true);
                return body.toString();
            }
            if ("gemini".equals(api.protocol)) {
                JSONObject body = new JSONObject();
                JSONObject sys = new JSONObject();
                JSONArray sysParts = new JSONArray();
                sysParts.put(new JSONObject().put("text", system == null ? "" : system));
                sys.put("parts", sysParts);
                body.put("systemInstruction", sys);
                JSONArray contents = new JSONArray();
                JSONObject userContent = new JSONObject();
                userContent.put("role", "user");
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", user));
                userContent.put("parts", parts);
                contents.put(userContent);
                body.put("contents", contents);
                JSONObject generation = new JSONObject();
                generation.put("temperature", api.temperature);
                if (maxTokens > 0) generation.put("maxOutputTokens", maxTokens);
                body.put("generationConfig", generation);
                return body.toString();
            }

            JSONObject body = new JSONObject();
            body.put("model", api.model.trim());
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", system == null ? "" : system));
            messages.put(new JSONObject().put("role", "user").put("content", user));
            body.put("messages", messages);
            body.put("stream", stream);
            if (maxTokens > 0) body.put("max_tokens", maxTokens);

            boolean thinking = api.thinkingEnabled;
            if (thinking) {
                JSONObject thinkingObj = new JSONObject();
                thinkingObj.put("type", "enabled");
                body.put("thinking", thinkingObj);
                String effort = api.thinkingEffort;
                if (!"low".equals(effort) && !"high".equals(effort) && !"max".equals(effort)) {
                    effort = "high";
                }
                body.put("reasoning_effort", effort);
            } else if (api.isDeepSeek()) {
                JSONObject thinkingObj = new JSONObject();
                thinkingObj.put("type", "disabled");
                body.put("thinking", thinkingObj);
            }
            // 思考模式下 temperature 不生效，官方文档建议不传；关闭思考时正常发送。
            if (!thinking) body.put("temperature", api.temperature);
            return body.toString();
        } catch (Exception e) {
            throw new LlmException("构建请求失败：" + safe(e.getMessage()), 0, false, false);
        }
    }

    private String buildEndpoint(boolean stream) throws LlmException {
        try {
            String base = normalizeBase(api.baseUrl);
            String path = api.endpointPath == null ? "" : api.endpointPath.trim();
            // 自定义完整接口 URL 优先使用（兼容不常见网关/自建服务的完整地址）
            if (path.startsWith("http://") || path.startsWith("https://")) {
                if (stream && "anthropic".equals(api.protocol) && !path.contains("stream=")) {
                    return path + (path.contains("?") ? "&" : "?") + "stream=true";
                }
                return path;
            }
            if ("gemini".equals(api.protocol)) {
                String model = api.model.trim();
                if (model.startsWith("models/")) model = model.substring("models/".length());
                String action = stream ? ":streamGenerateContent?alt=sse" : ":generateContent";
                if (base.contains(":generateContent")) {
                    base = base.replace(":generateContent", "");
                }
                if (base.contains(":streamGenerateContent")) {
                    int cut = base.indexOf(":streamGenerateContent");
                    base = base.substring(0, cut);
                }
                if (base.endsWith("/models")) return base + "/" + model + action;
                return base + "/models/" + model + action;
            }
            if (path.isEmpty()) {
                if ("anthropic".equals(api.protocol)) {
                    return base.endsWith("/messages") ? base : base + "/messages";
                }
                return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            }
            if (base.endsWith(path)) return base;
            if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
            if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
            return base + path;
        } catch (Exception e) {
            throw new LlmException("接口地址无效：" + safe(e.getMessage()), 0, false, false);
        }
    }

    private String normalizeBase(String input) {
        String base = input == null ? "" : input.trim();
        if (base.isEmpty()) return "";
        String lower = base.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            if (base.startsWith("localhost") || base.startsWith("127.") || base.startsWith("10.")
                    || base.startsWith("192.168.") || base.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*")) {
                base = "http://" + base;
            } else {
                base = "https://" + base;
            }
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private boolean looksLikeSse(BufferedInputStream stream, String contentType) throws IOException {
        if (contentType != null && contentType.toLowerCase().contains("event-stream")) return true;
        stream.mark(8192);
        byte[] head = new byte[4096];
        int read = 0;
        try {
            read = stream.read(head);
        } catch (IOException e) {
            stream.reset();
            throw e;
        }
        stream.reset();
        if (read <= 0) return false;
        String text = new String(head, 0, read, StandardCharsets.UTF_8);
        return text.contains("data:") || text.startsWith("event:") || text.startsWith(":")
                || (contentType != null && contentType.toLowerCase().contains("stream"));
    }

    private void readOpenAiSse(BufferedReader reader, StreamCallback callback) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled) throw LlmException.cancelled();
            line = line.trim();
            if (line.isEmpty() || line.startsWith(":")) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty()) continue;
            if ("[DONE]".equals(data)) break;
            JSONObject root;
            try {
                root = new JSONObject(data);
            } catch (Exception e) {
                continue;
            }
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                throw new LlmException("接口返回错误：" + safe(error.optString("message", error.toString())), 0, false, false);
            }
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) continue;
            JSONObject choice = choices.optJSONObject(0);
            if (choice == null) continue;
            String reason = choice.optString("finish_reason", "");
            if (!reason.isEmpty()) {
                finishReason = reason;
                truncated = "length".equals(reason) || "max_tokens".equals(reason);
            }
            JSONObject delta = choice.optJSONObject("delta");
            if (delta == null) delta = choice.optJSONObject("message");
            if (delta == null) continue;
            String reasoning = delta.optString("reasoning_content", "");
            if (reasoning.isEmpty()) reasoning = delta.optString("reasoning", "");
            if (!reasoning.isEmpty()) callback.onReasoning(reasoning);
            String content = extractOpenAiContent(delta);
            if (content != null && !content.isEmpty()) callback.onDelta(content);
        }
    }

    private void readAnthropicSse(BufferedReader reader, StreamCallback callback) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled) throw LlmException.cancelled();
            line = line.trim();
            if (line.isEmpty() || line.startsWith(":") || line.startsWith("event:")) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JSONObject root;
            try {
                root = new JSONObject(data);
            } catch (Exception e) {
                continue;
            }
            String type = root.optString("type", "");
            if ("error".equals(type)) {
                JSONObject error = root.optJSONObject("error");
                String message = error == null ? root.toString() : error.optString("message", error.toString());
                throw new LlmException("接口返回错误：" + safe(message), 0, false, false);
            }
            if ("content_block_delta".equals(type)) {
                JSONObject delta = root.optJSONObject("delta");
                if (delta != null) {
                    String text = delta.optString("text", "");
                    if (!text.isEmpty()) callback.onDelta(text);
                }
            } else if ("message_delta".equals(type)) {
                JSONObject delta = root.optJSONObject("delta");
                if (delta != null) {
                    String reason = delta.optString("stop_reason", "");
                    if (!reason.isEmpty()) {
                        finishReason = reason;
                        truncated = "max_tokens".equals(reason);
                    }
                }
            } else if ("message_stop".equals(type)) {
                break;
            }
        }
    }

    private void readGeminiSse(BufferedReader reader, StreamCallback callback) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (cancelled) throw LlmException.cancelled();
            line = line.trim();
            if (line.isEmpty() || line.startsWith(":")) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;
            JSONObject root;
            try {
                root = new JSONObject(data);
            } catch (Exception e) {
                continue;
            }
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                throw new LlmException("接口返回错误：" + safe(error.optString("message", error.toString())), 0, false, false);
            }
            JSONObject feedback = root.optJSONObject("promptFeedback");
            if (feedback != null) {
                String block = feedback.optString("blockReason", "");
                if (!block.isEmpty()) throw new LlmException("Gemini 拒绝了该请求（blockReason=" + block + "）", 0, false, false);
            }
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) continue;
            JSONObject candidate = candidates.optJSONObject(0);
            if (candidate == null) continue;
            String reason = candidate.optString("finishReason", "");
            if (!reason.isEmpty()) {
                finishReason = reason;
                truncated = "MAX_TOKENS".equalsIgnoreCase(reason);
            }
            JSONObject content = candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            if (parts == null) continue;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null || part.optBoolean("thought", false)) continue;
                String text = part.optString("text", "");
                if (!text.isEmpty()) callback.onDelta(text);
            }
        }
    }

    private String parseResponse(String response) throws LlmException {
        JSONObject root;
        try {
            root = new JSONObject(response);
        } catch (Exception e) {
            throw new LlmException("接口返回的不是合法 JSON，请确认 Base URL 指向正确的接口。\n"
                    + preview(response), 0, false, false);
        }
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            String msg = error.optString("message", error.toString());
            throw new LlmException("接口返回错误：" + safe(msg), 0, false, false);
        }
        try {
            if ("anthropic".equals(api.protocol)) {
                finishReason = root.optString("stop_reason", "");
                truncated = "max_tokens".equals(finishReason);
                JSONArray content = root.optJSONArray("content");
                if (content != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject part = content.optJSONObject(i);
                        if (part == null) continue;
                        if ("text".equals(part.optString("type"))) sb.append(part.optString("text", ""));
                    }
                    if (sb.length() > 0) return sb.toString().trim();
                }
                throw new LlmException("Anthropic 响应中没有文本内容：" + preview(response), 0, false, false);
            }
            if ("gemini".equals(api.protocol)) {
                JSONArray candidates = root.optJSONArray("candidates");
                if (candidates != null && candidates.length() > 0) {
                    JSONObject candidate = candidates.optJSONObject(0);
                    finishReason = candidate == null ? "" : candidate.optString("finishReason", "");
                    truncated = "MAX_TOKENS".equalsIgnoreCase(finishReason);
                    JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
                    JSONArray parts = content == null ? null : content.optJSONArray("parts");
                    StringBuilder sb = new StringBuilder();
                    if (parts != null) {
                        for (int i = 0; i < parts.length(); i++) {
                            JSONObject part = parts.optJSONObject(i);
                            if (part == null || part.optBoolean("thought", false)) continue;
                            sb.append(part.optString("text", ""));
                        }
                    }
                    if (sb.length() > 0) return sb.toString().trim();
                    throw new LlmException("Gemini 没有返回文本" + (finishReason.isEmpty() ? "" : "（finishReason=" + finishReason + "）")
                            + "，可能触发了安全限制或输出上限。", 0, false, false);
                }
                throw new LlmException("Gemini 响应中没有候选结果：" + preview(response), 0, false, false);
            }

            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.optJSONObject(0);
                finishReason = choice == null ? "" : choice.optString("finish_reason", "");
                truncated = "length".equals(finishReason) || "max_tokens".equals(finishReason);
                JSONObject message = choice == null ? null : choice.optJSONObject("message");
                String content = extractOpenAiContent(message);
                if (content != null && !content.trim().isEmpty()) return content.trim();
                if (choice != null) {
                    String text = choice.optString("text", "");
                    if (!text.trim().isEmpty()) return text.trim();
                    if ("length".equals(finishReason)) {
                        throw new LlmException("模型输出达到最大 token 上限，请提高「单次最大输出」或开启不限制。", 0, false, false);
                    }
                }
                String reasoning = message == null ? "" : message.optString("reasoning_content", "");
                if (!reasoning.isEmpty()) {
                    throw new LlmException("模型只返回了推理内容，没有最终译文。请关闭思考模式或调高最大输出后重试。", 0, false, false);
                }
            }
            String outputText = root.optString("output_text", "");
            if (!outputText.trim().isEmpty()) return outputText.trim();
            throw new LlmException("接口响应中没有可读取的文本：" + preview(response), 0, false, false);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("解析接口响应失败：" + safe(e.getMessage()), 0, false, false);
        }
    }

    private String extractOpenAiContent(JSONObject message) {
        if (message == null) return null;
        Object content = message.opt("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray array = (JSONArray) content;
            for (int i = 0; i < array.length(); i++) {
                JSONObject part = array.optJSONObject(i);
                if (part != null) sb.append(part.optString("text", ""));
            }
            return sb.toString();
        }
        return null;
    }

    private LlmException httpError(int status, String body) {
        String message = extractErrorMessage(body);
        String prefix;
        boolean retry = false;
        if (status == 401 || status == 403) {
            prefix = "认证失败（HTTP " + status + "）：API Key 无效、权限不足或未授权。";
        } else if (status == 402) {
            prefix = "账户额度不足（HTTP 402）：请检查余额或令牌额度。";
        } else if (status == 404) {
            prefix = "接口或模型不存在（HTTP 404）：请检查 Base URL、接口路径与模型名。";
        } else if (status == 429) {
            prefix = "请求被限流（HTTP 429）：请降低 TPM、稍后重试，或检查账户配额。";
            retry = true;
        } else if (status >= 500) {
            prefix = "服务商暂时不可用（HTTP " + status + "）。";
            retry = true;
        } else {
            prefix = "请求失败（HTTP " + status + "）。";
        }
        String detail = message.isEmpty() ? "" : "\n" + message;
        return new LlmException(prefix + detail, status, retry, false);
    }

    private String extractErrorMessage(String body) {
        if (body == null || body.isEmpty()) return "";
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String msg = error.optString("message", "");
                if (!msg.isEmpty()) return preview(msg);
                return preview(error.toString());
            }
            String msg = root.optString("message", root.optString("msg", ""));
            if (!msg.isEmpty()) return preview(msg);
        } catch (Exception ignored) {
        }
        return preview(body);
    }

    private static String readAll(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = stream) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > 16 * 1024 * 1024) break;
            }
        }
        return out.toString("UTF-8");
    }

    private static String preview(String value) {
        if (value == null) return "";
        String v = value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        if (v.length() > 500) v = v.substring(0, 500) + "…";
        return v;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim();
    }
}
