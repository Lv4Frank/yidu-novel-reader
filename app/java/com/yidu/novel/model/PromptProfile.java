package com.yidu.novel.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class PromptProfile {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String quality = "custom";
    public String content = "";
    public boolean builtIn = false;
    public String builtInKey = "";
    public long updatedAt = System.currentTimeMillis();

    public PromptProfile() {
    }

    public PromptProfile(String id, String name, String quality, String content, boolean builtIn, String builtInKey) {
        this.id = id;
        this.name = name;
        this.quality = quality;
        this.content = content;
        this.builtIn = builtIn;
        this.builtInKey = builtInKey;
    }

    public String qualityName() {
        if ("high".equals(quality)) return "高质量";
        if ("medium".equals(quality)) return "标准";
        if ("low".equals(quality)) return "快速";
        return "自定义";
    }

    public int qualityColor() {
        if ("high".equals(quality)) return 0xFF7C3AED;
        if ("medium".equals(quality)) return 0xFF2563EB;
        if ("low".equals(quality)) return 0xFF059669;
        return 0xFFB45309;
    }

    public PromptProfile copy() {
        PromptProfile p = new PromptProfile();
        p.id = id;
        p.name = name;
        p.quality = quality;
        p.content = content;
        p.builtIn = builtIn;
        p.builtInKey = builtInKey;
        p.updatedAt = updatedAt;
        return p;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("quality", quality);
        o.put("content", content);
        o.put("builtIn", builtIn);
        o.put("builtInKey", builtInKey);
        o.put("updated", updatedAt);
        return o;
    }

    public static PromptProfile fromJson(JSONObject o) {
        PromptProfile p = new PromptProfile();
        p.id = o.optString("id", p.id);
        p.name = o.optString("name", "");
        p.quality = o.optString("quality", "custom");
        p.content = o.optString("content", "");
        p.builtIn = o.optBoolean("builtIn", false);
        p.builtInKey = o.optString("builtInKey", "");
        p.updatedAt = o.optLong("updated", System.currentTimeMillis());
        return p;
    }
}
