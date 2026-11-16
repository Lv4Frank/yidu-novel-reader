package com.yidu.novel.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Book {
    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String fileName = "";
    public String path = "";
    public String charset = "UTF-8";
    public int charCount = 0;
    public int blockCount = 0;
    public int chapterCount = 0;
    public int lastBlock = 0;
    public int lastChapter = 0;
    public boolean favorite = false;
    public long addedAt = System.currentTimeMillis();
    public long lastReadAt = 0L;

    public int percent() {
        if (blockCount <= 0) return 0;
        int value = (int) Math.round(lastBlock * 100.0 / blockCount);
        return Math.max(0, Math.min(100, value));
    }

    public String progressLabel() {
        if (lastReadAt <= 0) return "未开始";
        return "已读 " + percent() + "%";
    }

    public Book copy() {
        Book b = new Book();
        b.id = id;
        b.title = title;
        b.fileName = fileName;
        b.path = path;
        b.charset = charset;
        b.charCount = charCount;
        b.blockCount = blockCount;
        b.chapterCount = chapterCount;
        b.lastBlock = lastBlock;
        b.lastChapter = lastChapter;
        b.favorite = favorite;
        b.addedAt = addedAt;
        b.lastReadAt = lastReadAt;
        return b;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("fileName", fileName);
        o.put("path", path);
        o.put("charset", charset);
        o.put("chars", charCount);
        o.put("blocks", blockCount);
        o.put("chapters", chapterCount);
        o.put("lastBlock", lastBlock);
        o.put("lastChapter", lastChapter);
        o.put("favorite", favorite);
        o.put("added", addedAt);
        o.put("lastRead", lastReadAt);
        return o;
    }

    public static Book fromJson(JSONObject o) {
        Book b = new Book();
        b.id = o.optString("id", b.id);
        b.title = o.optString("title", "");
        b.fileName = o.optString("fileName", "");
        b.path = o.optString("path", "");
        b.charset = o.optString("charset", "UTF-8");
        b.charCount = o.optInt("chars", 0);
        b.blockCount = o.optInt("blocks", 0);
        b.chapterCount = o.optInt("chapters", 0);
        b.lastBlock = o.optInt("lastBlock", 0);
        b.lastChapter = o.optInt("lastChapter", 0);
        b.favorite = o.optBoolean("favorite", false);
        b.addedAt = o.optLong("added", System.currentTimeMillis());
        b.lastReadAt = o.optLong("lastRead", 0L);
        return b;
    }
}
