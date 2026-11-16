package com.yidu.novel.net;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 从流式返回的文本中增量提取 JSON 字符串数组。
 * 模型被要求输出 ["译文1","译文2",...]，但实际可能带代码块、解释或对象，
 * 因此提供容错解析，并支持把“正在接收的当前条”显示出来。
 */
public final class StreamJsonParser {
    public static class Result {
        public final List<String> items = new ArrayList<>();
        public String partial = "";
    }

    private StreamJsonParser() {
    }

    /** 重新扫描累计文本，返回已完成的字符串与当前正在接收的字符串。 */
    public static Result parsePartial(String raw) {
        Result result = new Result();
        if (raw == null || raw.isEmpty()) return result;
        int start = raw.indexOf('[');
        if (start < 0) {
            // 可能模型先输出对象/说明，暂不显示
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escape = false;
        int i = start + 1;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (!inString) {
                if (c == '"') {
                    inString = true;
                    current.setLength(0);
                } else if (c == ']') {
                    break;
                }
                i++;
                continue;
            }
            if (escape) {
                if (c == 'u') {
                    if (i + 4 >= raw.length()) break;
                    try {
                        current.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    } catch (Exception ignored) {
                    }
                    i += 5;
                    escape = false;
                    continue;
                }
                switch (c) {
                    case 'n': current.append('\n'); break;
                    case 't': current.append('\t'); break;
                    case 'r': current.append('\r'); break;
                    case 'b': current.append('\b'); break;
                    case 'f': current.append('\f'); break;
                    default: current.append(c); break;
                }
                escape = false;
                i++;
                continue;
            }
            if (c == '\\') {
                escape = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = false;
                result.items.add(current.toString());
                current.setLength(0);
                i++;
                continue;
            }
            current.append(c);
            i++;
        }
        if (inString && current.length() > 0) {
            result.partial = current.toString();
        }
        return result;
    }

    /** 最终解析：优先严格 JSON，失败时回退到逐行文本。 */
    public static List<String> parseFinal(String raw) {
        List<String> strict = parseStrict(raw);
        if (!strict.isEmpty()) return strict;
        List<String> partial = parsePartial(raw).items;
        if (!partial.isEmpty()) return partial;
        return parseLines(raw);
    }

    private static List<String> parseStrict(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int end = text.indexOf("```", fence + 3);
            if (end > fence) {
                text = text.substring(fence + 3, end);
                int newline = text.indexOf('\n');
                if (newline >= 0 && newline < 12) text = text.substring(newline + 1);
            }
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            int objStart = text.indexOf('{');
            int objEnd = text.lastIndexOf('}');
            if (objStart < 0 || objEnd <= objStart) return result;
            text = text.substring(objStart, objEnd + 1);
            try {
                JSONObject object = new JSONObject(text);
                JSONArray array = firstArray(object);
                if (array == null) return result;
                text = array.toString();
            } catch (Exception e) {
                return result;
            }
        } else {
            text = text.substring(start, end + 1);
        }
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof String) {
                    result.add(((String) item).trim());
                } else if (item instanceof JSONObject) {
                    JSONObject o = (JSONObject) item;
                    String value = firstString(o, "translation", "text", "target", "zh", "t", "content", "value");
                    if (!value.isEmpty()) result.add(value);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static JSONArray firstArray(JSONObject object) {
        String[] keys = {"translations", "translation", "result", "results", "data", "items", "output", "content"};
        for (String key : keys) {
            JSONArray array = object.optJSONArray(key);
            if (array != null) return array;
        }
        java.util.Iterator<String> it = object.keys();
        while (it.hasNext()) {
            JSONArray array = object.optJSONArray(it.next());
            if (array != null) return array;
        }
        return null;
    }

    private static String firstString(JSONObject o, String... keys) {
        for (String key : keys) {
            String value = o.optString(key, "");
            if (!value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static List<String> parseLines(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) return result;
        String text = raw.replace("```json", "```").replace("```", "");
        for (String rawLine : text.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.equals("[") || line.equals("]")) continue;
            line = line.replaceAll("^\\s*\\d+\\s*[.、:：)\\]】]\\s*", "");
            line = line.replaceAll("^[\\[\\{\"]+", "").replaceAll("[\\]\\}\",]+$", "").trim();
            line = line.replace("\\n", "\n").replace("\\\"", "\"");
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }
}
