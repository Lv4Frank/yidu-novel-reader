package com.yidu.novel.store;

import android.content.Context;
import android.content.SharedPreferences;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.Book;
import com.yidu.novel.model.Bookmark;
import com.yidu.novel.model.Glossary;
import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.util.Defaults;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AppStore {
    private static final String PREFS = "yidu_prefs_v1";
    private static final String K_APIS = "api_profiles_v1";
    private static final String K_PROMPTS = "prompt_profiles_v1";
    private static final String K_GLOSSARIES = "glossaries_v1";
    private static final String K_BOOKS = "books_v1";
    private static final String K_BOOKMARKS = "bookmarks_v1";
    private static final String K_ACTIVE_API = "active_api";
    private static final String K_ACTIVE_PROMPT = "active_prompt";
    private static final String K_SEEDED = "seeded_v2";
    private static AppStore instance;

    private final SharedPreferences prefs;

    private AppStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seed();
    }

    public static synchronized AppStore get(Context context) {
        if (instance == null) instance = new AppStore(context);
        return instance;
    }

    private void seed() {
        if (!prefs.getBoolean(K_SEEDED, false)) {
            List<PromptProfile> prompts = Defaults.prompts();
            persistPrompts(prompts);
            prefs.edit()
                    .putBoolean(K_SEEDED, true)
                    .putString(K_ACTIVE_PROMPT, Defaults.MEDIUM)
                    .putString("source_lang", "auto")
                    .putString("target_lang", "zh-CN")
                    .putBoolean("auto_translate", false)
                    .putBoolean("punctuation", true)
                    .putInt("font_size", 19)
                    .putString("font_family", "serif")
                    .putInt("text_color", 0xFF2B2118)
                    .putInt("bg_color", 0xFFF6F1E7)
                    .putFloat("line_spacing", 1.55f)
                    .putBoolean("paragraph_spacing", true)
                    .putBoolean("indent", true)
                    .apply();
        }
        if (getPrompts().isEmpty()) {
            persistPrompts(Defaults.prompts());
            prefs.edit().putString(K_ACTIVE_PROMPT, Defaults.MEDIUM).apply();
        }
    }

    // ---------------- API 配置 ----------------

    public synchronized List<ApiProfile> getApis() {
        List<ApiProfile> result = new ArrayList<>();
        String raw = prefs.getString(K_APIS, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String key = SecretBox.decrypt(o.optString("key", ""));
                result.add(ApiProfile.fromJson(o, key));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void saveApi(ApiProfile profile) {
        List<ApiProfile> list = getApis();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(profile.id)) {
                list.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(profile);
        profile.updatedAt = System.currentTimeMillis();
        persistApis(list);
        String active = prefs.getString(K_ACTIVE_API, "");
        if (active == null || active.isEmpty()) prefs.edit().putString(K_ACTIVE_API, profile.id).apply();
    }

    public synchronized void deleteApi(String id) {
        List<ApiProfile> list = getApis();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        persistApis(list);
        if (id.equals(prefs.getString(K_ACTIVE_API, ""))) {
            prefs.edit().putString(K_ACTIVE_API, list.isEmpty() ? "" : list.get(0).id).apply();
        }
    }

    public synchronized ApiProfile getActiveApi() {
        String id = prefs.getString(K_ACTIVE_API, "");
        List<ApiProfile> list = getApis();
        for (ApiProfile p : list) {
            if (p.id.equals(id)) return p;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized ApiProfile getApi(String id) {
        for (ApiProfile p : getApis()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public void setActiveApi(String id) {
        prefs.edit().putString(K_ACTIVE_API, id).apply();
    }

    public String getActiveApiId() {
        return prefs.getString(K_ACTIVE_API, "");
    }

    private void persistApis(List<ApiProfile> list) {
        JSONArray array = new JSONArray();
        for (ApiProfile p : list) {
            try {
                array.put(p.toJson(SecretBox.encrypt(p.apiKey)));
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(K_APIS, array.toString()).apply();
    }

    // ---------------- 提示词 ----------------

    public synchronized List<PromptProfile> getPrompts() {
        List<PromptProfile> result = new ArrayList<>();
        String raw = prefs.getString(K_PROMPTS, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(PromptProfile.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void savePrompt(PromptProfile prompt) {
        List<PromptProfile> list = getPrompts();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(prompt.id)) {
                list.set(i, prompt);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(prompt);
        prompt.updatedAt = System.currentTimeMillis();
        persistPrompts(list);
        String active = prefs.getString(K_ACTIVE_PROMPT, "");
        if (active == null || active.isEmpty()) prefs.edit().putString(K_ACTIVE_PROMPT, prompt.id).apply();
    }

    public synchronized void deletePrompt(String id) {
        List<PromptProfile> list = getPrompts();
        for (int i = 0; i < list.size(); i++) {
            PromptProfile p = list.get(i);
            if (p.id.equals(id) && !p.builtIn) {
                list.remove(i);
                break;
            }
        }
        persistPrompts(list);
        if (id.equals(prefs.getString(K_ACTIVE_PROMPT, ""))) {
            String fallback = Defaults.MEDIUM;
            for (PromptProfile p : list) {
                if (p.id.equals(Defaults.MEDIUM)) {
                    fallback = p.id;
                    break;
                }
            }
            prefs.edit().putString(K_ACTIVE_PROMPT, fallback).apply();
        }
    }

    public synchronized PromptProfile getActivePrompt() {
        String id = prefs.getString(K_ACTIVE_PROMPT, Defaults.MEDIUM);
        List<PromptProfile> list = getPrompts();
        for (PromptProfile p : list) {
            if (p.id.equals(id)) return p;
        }
        for (PromptProfile p : list) {
            if (p.id.equals(Defaults.MEDIUM)) return p;
        }
        return list.isEmpty() ? null : list.get(0);
    }

    public synchronized PromptProfile getPrompt(String id) {
        for (PromptProfile p : getPrompts()) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    public void setActivePrompt(String id) {
        prefs.edit().putString(K_ACTIVE_PROMPT, id).apply();
    }

    public String getActivePromptId() {
        return prefs.getString(K_ACTIVE_PROMPT, Defaults.MEDIUM);
    }

    public void resetPrompt(PromptProfile prompt) {
        if (prompt == null || !prompt.builtIn) return;
        prompt.content = Defaults.promptFor(prompt.builtInKey);
        savePrompt(prompt);
    }

    private void persistPrompts(List<PromptProfile> list) {
        JSONArray array = new JSONArray();
        for (PromptProfile p : list) {
            try {
                array.put(p.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(K_PROMPTS, array.toString()).apply();
    }

    // ---------------- 术语表 ----------------

    public synchronized List<Glossary> getGlossaries() {
        List<Glossary> result = new ArrayList<>();
        String raw = prefs.getString(K_GLOSSARIES, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(Glossary.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void saveGlossary(Glossary glossary) {
        List<Glossary> list = getGlossaries();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(glossary.id)) {
                list.set(i, glossary);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(glossary);
        glossary.updatedAt = System.currentTimeMillis();
        persistGlossaries(list);
    }

    public synchronized void deleteGlossary(String id) {
        List<Glossary> list = getGlossaries();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        persistGlossaries(list);
    }

    public synchronized Glossary getGlossary(String id) {
        for (Glossary g : getGlossaries()) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }

    public synchronized void setGlossaryEnabled(String id, boolean enabled) {
        List<Glossary> list = getGlossaries();
        for (Glossary g : list) {
            if (g.id.equals(id)) {
                g.enabled = enabled;
                break;
            }
        }
        persistGlossaries(list);
    }

    /** 把所有启用中的术语表拼成一段提示词，限制总长度避免占用过多上下文。 */
    public synchronized String glossaryPrompt(int maxChars) {
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (Glossary g : getGlossaries()) {
            if (!g.enabled || g.terms.isEmpty()) continue;
            String block = g.promptBlock(Math.max(0, Math.min(1200, maxChars - used)));
            if (block.isEmpty()) continue;
            String header = "【" + g.name + "】\n";
            if (used + header.length() + block.length() > maxChars) break;
            sb.append(header).append(block).append("\n\n");
            used += header.length() + block.length() + 2;
        }
        return sb.toString().trim();
    }

    private void persistGlossaries(List<Glossary> list) {
        JSONArray array = new JSONArray();
        for (Glossary g : list) {
            try {
                array.put(g.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(K_GLOSSARIES, array.toString()).apply();
    }

    // ---------------- 书籍 ----------------

    public synchronized List<Book> getBooks() {
        List<Book> result = new ArrayList<>();
        String raw = prefs.getString(K_BOOKS, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(Book.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized void saveBook(Book book) {
        List<Book> list = getBooks();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(book.id)) {
                list.set(i, book);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(book);
        persistBooks(list);
        if (getLastBookId().isEmpty()) prefs.edit().putString("last_book", book.id).apply();
    }

    public synchronized void deleteBook(String id) {
        List<Book> list = getBooks();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        persistBooks(list);
        if (id.equals(getLastBookId())) {
            prefs.edit().putString("last_book", list.isEmpty() ? "" : list.get(0).id).apply();
        }
        for (Bookmark b : getBookmarks()) {
            if (b.bookId.equals(id)) deleteBookmark(b.id);
        }
    }

    public synchronized Book getBook(String id) {
        for (Book b : getBooks()) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }

    private void persistBooks(List<Book> list) {
        JSONArray array = new JSONArray();
        for (Book b : list) {
            try {
                array.put(b.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(K_BOOKS, array.toString()).apply();
    }

    public Book getLastBook() {
        Book book = getBook(getLastBookId());
        if (book != null) return book;
        Book best = null;
        for (Book b : getBooks()) {
            if (b.lastReadAt <= 0) continue;
            if (best == null || b.lastReadAt > best.lastReadAt) best = b;
        }
        return best;
    }

    public String getLastBookId() {
        return prefs.getString("last_book", "");
    }

    public void setLastBookId(String id) {
        prefs.edit().putString("last_book", id).apply();
    }

    // ---------------- 书签 ----------------

    public synchronized List<Bookmark> getBookmarks() {
        List<Bookmark> result = new ArrayList<>();
        String raw = prefs.getString(K_BOOKMARKS, "");
        if (raw == null || raw.isEmpty()) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                result.add(Bookmark.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public synchronized List<Bookmark> getBookmarks(String bookId) {
        List<Bookmark> result = new ArrayList<>();
        for (Bookmark b : getBookmarks()) {
            if (b.bookId.equals(bookId)) result.add(b);
        }
        return result;
    }

    public synchronized void saveBookmark(Bookmark bookmark) {
        List<Bookmark> list = getBookmarks();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(bookmark.id)) {
                list.set(i, bookmark);
                persistBookmarks(list);
                return;
            }
        }
        list.add(0, bookmark);
        persistBookmarks(list);
    }

    public synchronized void deleteBookmark(String id) {
        List<Bookmark> list = getBookmarks();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        persistBookmarks(list);
    }

    public synchronized Bookmark findBookmark(String bookId, int block) {
        for (Bookmark b : getBookmarks()) {
            if (b.bookId.equals(bookId) && b.blockIndex == block) return b;
        }
        return null;
    }

    private void persistBookmarks(List<Bookmark> list) {
        JSONArray array = new JSONArray();
        for (Bookmark b : list) {
            try {
                array.put(b.toJson());
            } catch (Exception ignored) {
            }
        }
        prefs.edit().putString(K_BOOKMARKS, array.toString()).apply();
    }

    // ---------------- 阅读 / 翻译设置 ----------------

    public String getSourceLang() {
        return prefs.getString("source_lang", "auto");
    }

    public void setSourceLang(String code) {
        prefs.edit().putString("source_lang", code).apply();
    }

    public String getTargetLang() {
        return prefs.getString("target_lang", "zh-CN");
    }

    public void setTargetLang(String code) {
        prefs.edit().putString("target_lang", code).apply();
    }

    public boolean isAutoTranslate() {
        return prefs.getBoolean("auto_translate", false);
    }

    public void setAutoTranslate(boolean value) {
        prefs.edit().putBoolean("auto_translate", value).apply();
    }

    public boolean isPunctuationPreserve() {
        return prefs.getBoolean("punctuation", true);
    }

    public void setPunctuationPreserve(boolean value) {
        prefs.edit().putBoolean("punctuation", value).apply();
    }

    public int getFontSize() {
        int size = prefs.getInt("font_size", 19);
        if (size < 14) size = 14;
        if (size > 34) size = 34;
        return size;
    }

    public void setFontSize(int size) {
        prefs.edit().putInt("font_size", Math.max(14, Math.min(34, size))).apply();
    }

    public String getFontFamily() {
        return prefs.getString("font_family", "serif");
    }

    public void setFontFamily(String family) {
        prefs.edit().putString("font_family", family).apply();
    }

    public int getTextColor() {
        return prefs.getInt("text_color", 0xFF2B2118);
    }

    public void setTextColor(int color) {
        prefs.edit().putInt("text_color", color).apply();
    }

    public int getBgColor() {
        return prefs.getInt("bg_color", 0xFFF6F1E7);
    }

    public void setBgColor(int color) {
        prefs.edit().putInt("bg_color", color).apply();
    }

    public float getLineSpacing() {
        float value = prefs.getFloat("line_spacing", 1.55f);
        if (value < 1.0f) value = 1.0f;
        if (value > 2.6f) value = 2.6f;
        return value;
    }

    public void setLineSpacing(float value) {
        prefs.edit().putFloat("line_spacing", Math.max(1.0f, Math.min(2.6f, value))).apply();
    }

    public boolean isParagraphSpacing() {
        return prefs.getBoolean("paragraph_spacing", true);
    }

    public void setParagraphSpacing(boolean value) {
        prefs.edit().putBoolean("paragraph_spacing", value).apply();
    }

    public boolean isIndent() {
        return prefs.getBoolean("indent", true);
    }

    public void setIndent(boolean value) {
        prefs.edit().putBoolean("indent", value).apply();
    }

    public int getExportMode() {
        return prefs.getInt("export_mode", 0);
    }

    public void setExportMode(int mode) {
        prefs.edit().putInt("export_mode", mode).apply();
    }

    /** 阅读模式：original = 原文分页；bilingual = 双语对照分页；scroll = 自由浏览（连续滚动）。 */
    public String getReadingMode() {
        String mode = prefs.getString("reading_mode", "original");
        if ("bilingual".equals(mode) || "scroll".equals(mode)) return mode;
        return "original";
    }

    public void setReadingMode(String mode) {
        String value = "bilingual".equals(mode) || "scroll".equals(mode) ? mode : "original";
        prefs.edit().putString("reading_mode", value).apply();
    }

    public boolean isBilingualReading() {
        return "bilingual".equals(getReadingMode());
    }

    public boolean isFreeBrowseReading() {
        return "scroll".equals(getReadingMode());
    }
}
