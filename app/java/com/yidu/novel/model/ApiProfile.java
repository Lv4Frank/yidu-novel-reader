package com.yidu.novel.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ApiProfile {
    public String id = UUID.randomUUID().toString();
    public String name = "未命名接口";
    public String providerId = "custom";
    public String protocol = "openai";
    public String baseUrl = "";
    public String endpointPath = "/chat/completions";
    public String apiKey = "";
    public String model = "";
    public int tpm = 0;
    public boolean unlimitedOutput = true;
    public int maxOutputTokens = 4096;
    public double temperature = 0.3;
    public String extraHeaders = "";
    public boolean thinkingEnabled = false;
    public String thinkingEffort = "high";
    public int timeoutSec = 180;
    public long updatedAt = System.currentTimeMillis();

    public String providerName() {
        return ProviderPreset.byId(providerId).name;
    }

    public boolean isDeepSeek() {
        String base = baseUrl == null ? "" : baseUrl.toLowerCase();
        String modelLower = model == null ? "" : model.toLowerCase();
        return "deepseek".equals(providerId) || base.contains("deepseek.com")
                || modelLower.contains("deepseek");
    }

    public boolean isOpenAiCompatible() {
        return "openai".equals(protocol);
    }

    public String tpmLabel() {
        if (tpm <= 0) return "TPM 不限制";
        if (tpm % 1000 == 0) return "TPM " + (tpm / 1000) + "k";
        return "TPM " + tpm;
    }

    public String outputLabel() {
        return unlimitedOutput ? "输出不限制" : "最多 " + maxOutputTokens + " tokens";
    }

    public String thinkingLabel() {
        if (!thinkingEnabled) return "关";
        if ("low".equals(thinkingEffort)) return "开 · 低";
        if ("max".equals(thinkingEffort)) return "开 · 最高";
        return "开 · 高";
    }

    public String maskedKey() {
        if (apiKey == null || apiKey.isEmpty()) return "未填写 Key";
        if (apiKey.length() <= 8) return "••••••••";
        return apiKey.substring(0, 4) + "••••" + apiKey.substring(apiKey.length() - 4);
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(protocolLabel());
        if (model != null && !model.isEmpty()) sb.append(" · ").append(model);
        sb.append(" · ").append(tpmLabel());
        return sb.toString();
    }

    public String protocolLabel() {
        return ProviderPreset.protocolName(protocol);
    }

    public ApiProfile copy() {
        ApiProfile p = new ApiProfile();
        p.id = id;
        p.name = name;
        p.providerId = providerId;
        p.protocol = protocol;
        p.baseUrl = baseUrl;
        p.endpointPath = endpointPath;
        p.apiKey = apiKey;
        p.model = model;
        p.tpm = tpm;
        p.unlimitedOutput = unlimitedOutput;
        p.maxOutputTokens = maxOutputTokens;
        p.temperature = temperature;
        p.extraHeaders = extraHeaders;
        p.thinkingEnabled = thinkingEnabled;
        p.thinkingEffort = thinkingEffort;
        p.timeoutSec = timeoutSec;
        p.updatedAt = updatedAt;
        return p;
    }

    public JSONObject toJson(String keyCipher) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("provider", providerId);
        o.put("protocol", protocol);
        o.put("baseUrl", baseUrl);
        o.put("endpoint", endpointPath);
        o.put("key", keyCipher);
        o.put("model", model);
        o.put("tpm", tpm);
        o.put("unlimited", unlimitedOutput);
        o.put("maxOut", maxOutputTokens);
        o.put("temperature", temperature);
        o.put("headers", extraHeaders);
        o.put("thinking", thinkingEnabled);
        o.put("effort", thinkingEffort);
        o.put("timeout", timeoutSec);
        o.put("updated", updatedAt);
        return o;
    }

    public static ApiProfile fromJson(JSONObject o, String apiKey) {
        ApiProfile p = new ApiProfile();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "未命名接口");
        p.providerId = o.optString("provider", "custom");
        p.protocol = o.optString("protocol", "openai");
        p.baseUrl = o.optString("baseUrl", "");
        p.endpointPath = o.optString("endpoint", "/chat/completions");
        p.apiKey = apiKey;
        p.model = o.optString("model", "");
        p.tpm = o.optInt("tpm", 0);
        p.unlimitedOutput = o.optBoolean("unlimited", true);
        p.maxOutputTokens = o.optInt("maxOut", 4096);
        p.temperature = o.optDouble("temperature", 0.3);
        p.extraHeaders = o.optString("headers", "");
        p.thinkingEnabled = o.optBoolean("thinking", false);
        p.thinkingEffort = o.optString("effort", "high");
        p.timeoutSec = o.optInt("timeout", 180);
        p.updatedAt = o.optLong("updated", System.currentTimeMillis());
        return p;
    }
}
