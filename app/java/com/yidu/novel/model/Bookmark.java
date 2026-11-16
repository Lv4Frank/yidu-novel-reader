package com.yidu.novel.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Bookmark {
    public String id = UUID.randomUUID().toString();
    public String bookId = "";
    public String bookTitle = "";
    public int blockIndex = 0;
    public int chapterIndex = 0;
    public String chapterTitle = "";
    public String snippet = "";
    public int percent = 0;
    public long createdAt = System.currentTimeMillis();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("bookId", bookId);
        o.put("bookTitle", bookTitle);
        o.put("block", blockIndex);
        o.put("chapter", chapterIndex);
        o.put("chapterTitle", chapterTitle);
        o.put("snippet", snippet);
        o.put("percent", percent);
        o.put("created", createdAt);
        return o;
    }

    public static Bookmark fromJson(JSONObject o) {
        Bookmark b = new Bookmark();
        b.id = o.optString("id", b.id);
        b.bookId = o.optString("bookId", "");
        b.bookTitle = o.optString("bookTitle", "");
        b.blockIndex = o.optInt("block", 0);
        b.chapterIndex = o.optInt("chapter", 0);
        b.chapterTitle = o.optString("chapterTitle", "");
        b.snippet = o.optString("snippet", "");
        b.percent = o.optInt("percent", 0);
        b.createdAt = o.optLong("created", System.currentTimeMillis());
        return b;
    }
}
