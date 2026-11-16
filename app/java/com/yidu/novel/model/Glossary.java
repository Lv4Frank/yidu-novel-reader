package com.yidu.novel.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 术语表：一组“原文=译文”对照，可保存多组并按需启用。 */
public class Glossary {
    public String id = UUID.randomUUID().toString();
    public String name = "未命名术语表";
    public boolean enabled = true;
    public final List<Term> terms = new ArrayList<>();
    public long updatedAt = System.currentTimeMillis();

    public static class Term {
        public String source;
        public String target;

        public Term() {
        }

        public Term(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    public String summary() {
        return terms.size() + " 条术语" + (enabled ? " · 已启用" : " · 已停用");
    }

    /** 解析 “原文=译文” / “原文<TAB>译文” / “原文,译文” 三种写法。 */
    public void parseText(String text) {
        terms.clear();
        if (text == null) return;
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String source = null;
            String target = null;
            int tab = line.indexOf('\t');
            int eq = line.indexOf('=');
            int comma = line.indexOf(',');
            if (tab > 0) {
                source = line.substring(0, tab).trim();
                target = line.substring(tab + 1).trim();
            } else if (eq > 0) {
                source = line.substring(0, eq).trim();
                target = line.substring(eq + 1).trim();
            } else if (comma > 0) {
                source = line.substring(0, comma).trim();
                target = line.substring(comma + 1).trim();
            }
            if (source != null && target != null && !source.isEmpty() && !target.isEmpty()
                    && terms.size() < 500) {
                terms.add(new Term(source, target));
            }
        }
        updatedAt = System.currentTimeMillis();
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Term term : terms) {
            sb.append(term.source).append('=').append(term.target).append('\n');
        }
        return sb.toString();
    }

    /** 生成给模型的术语提示，限制长度，避免吞掉太多上下文。 */
    public String promptBlock(int maxChars) {
        if (!enabled || terms.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (Term term : terms) {
            if (term.source == null || term.target == null) continue;
            String line = term.source + " = " + term.target + "\n";
            if (used + line.length() > maxChars) break;
            sb.append(line);
            used += line.length();
        }
        return sb.toString().trim();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("enabled", enabled);
            o.put("updated", updatedAt);
            JSONArray array = new JSONArray();
            for (Term term : terms) {
                JSONObject t = new JSONObject();
                t.put("s", term.source);
                t.put("t", term.target);
                array.put(t);
            }
            o.put("terms", array);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Glossary fromJson(JSONObject o) {
        Glossary g = new Glossary();
        g.id = o.optString("id", g.id);
        g.name = o.optString("name", "未命名术语表");
        g.enabled = o.optBoolean("enabled", true);
        g.updatedAt = o.optLong("updated", System.currentTimeMillis());
        JSONArray array = o.optJSONArray("terms");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject t = array.optJSONObject(i);
                if (t == null) continue;
                String s = t.optString("s", "");
                String target = t.optString("t", "");
                if (!s.isEmpty() && !target.isEmpty()) g.terms.add(new Term(s, target));
            }
        }
        return g;
    }

    public Glossary copy() {
        Glossary g = new Glossary();
        g.id = id;
        g.name = name;
        g.enabled = enabled;
        for (Term t : terms) g.terms.add(new Term(t.source, t.target));
        g.updatedAt = updatedAt;
        return g;
    }
}
