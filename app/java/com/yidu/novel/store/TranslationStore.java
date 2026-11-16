package com.yidu.novel.store;

import android.content.Context;

import com.yidu.novel.util.TextUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每本书一个追加式译文日志（JSONL），流式翻译完成一块立即落盘，
 * 退出 App 后再次打开可直接恢复全部已完成译文。
 */
public final class TranslationStore {
    private static final Map<String, TranslationStore> CACHE = new HashMap<>();

    private final File file;
    private final Map<Integer, Item> items = new HashMap<>();
    private int journalLines = 0;

    public static class Item {
        public String text = "";
        public int hash;
        public long updatedAt;
    }

    private TranslationStore(File file) {
        this.file = file;
        load();
    }

    public static synchronized TranslationStore open(Context context, String bookId) {
        TranslationStore store = CACHE.get(bookId);
        if (store == null) {
            store = new TranslationStore(fileFor(context, bookId));
            CACHE.put(bookId, store);
        }
        return store;
    }

    public static File fileFor(Context context, String bookId) {
        return new File(BookRepository.translationDir(context), bookId + ".jsonl");
    }

    public static synchronized void forget(String bookId) {
        CACHE.remove(bookId);
    }

    private void load() {
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                journalLines++;
                try {
                    JSONObject o = new JSONObject(line);
                    int index = o.optInt("i", -1);
                    if (index < 0) continue;
                    if (o.optBoolean("d", false)) {
                        items.remove(index);
                        continue;
                    }
                    Item item = new Item();
                    item.text = o.optString("t", "");
                    item.hash = o.optInt("h", 0);
                    item.updatedAt = o.optLong("u", 0L);
                    items.put(index, item);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static int hash(String text) {
        return text == null ? 0 : text.hashCode() ^ (text.length() * 31);
    }

    /** 只有原文哈希一致才认为译文有效，避免重新解析后错位。 */
    public synchronized String get(int blockIndex, String originalText) {
        Item item = items.get(blockIndex);
        if (item == null) return null;
        if (originalText != null && item.hash != hash(originalText)) return null;
        return item.text;
    }

    public synchronized boolean has(int blockIndex, String originalText) {
        return get(blockIndex, originalText) != null;
    }

    public synchronized void put(int blockIndex, String originalText, String translation) {
        if (translation == null) return;
        Item item = new Item();
        item.text = translation;
        item.hash = hash(originalText);
        item.updatedAt = System.currentTimeMillis();
        items.put(blockIndex, item);
        append(indexJson(blockIndex, item.hash, translation, false));
    }

    public synchronized void delete(int blockIndex) {
        if (!items.containsKey(blockIndex)) return;
        items.remove(blockIndex);
        append(indexJson(blockIndex, 0, "", true));
    }

    public synchronized void delete(Collection<Integer> blockIndexes) {
        for (Integer index : blockIndexes) delete(index);
    }

    public synchronized void clear() {
        items.clear();
        journalLines = 0;
        try {
            if (file.exists()) file.delete();
        } catch (Exception ignored) {
        }
    }

    public synchronized int count() {
        return items.size();
    }

    public synchronized boolean isEmpty() {
        return items.isEmpty();
    }

    /** 已翻译的块索引，用于导出时遍历。 */
    public synchronized List<Integer> translatedIndexes() {
        return new ArrayList<>(items.keySet());
    }

    private JSONObject indexJson(int index, int hash, String text, boolean deleted) {
        JSONObject o = new JSONObject();
        try {
            o.put("i", index);
            if (deleted) {
                o.put("d", true);
            } else {
                o.put("h", hash);
                o.put("t", text);
                o.put("u", System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    private void append(JSONObject o) {
        try {
            boolean fresh = !file.exists();
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(file, true), StandardCharsets.UTF_8))) {
                if (!fresh) writer.newLine();
                writer.write(o.toString());
            }
            journalLines++;
            if (journalLines > items.size() * 3 + 200) compact();
        } catch (Exception ignored) {
        }
    }

    private void compact() {
        try {
            File temp = new File(file.getAbsolutePath() + ".tmp");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(temp), StandardCharsets.UTF_8))) {
                boolean first = true;
                for (Map.Entry<Integer, Item> entry : items.entrySet()) {
                    if (!first) writer.newLine();
                    first = false;
                    writer.write(indexJson(entry.getKey(), entry.getValue().hash, entry.getValue().text, false).toString());
                }
            }
            if (file.exists() && !file.delete()) return;
            if (!temp.renameTo(file)) {
                // 某些文件系统 rename 失败时改用复制，绝不能写坏译文文件
                try {
                    java.nio.file.Files.copy(temp.toPath(), file.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    if (temp.exists()) temp.delete();
                }
            }
            journalLines = items.size();
        } catch (Exception ignored) {
        }
    }
}
