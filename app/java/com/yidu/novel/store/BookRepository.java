package com.yidu.novel.store;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.yidu.novel.model.Book;
import com.yidu.novel.util.Novel;
import com.yidu.novel.util.TextUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** 书籍文件与解析：TXT 导入、编码识别、整本解析。 */
public final class BookRepository {
    private static final int MAX_IMPORT_BYTES = 30 * 1024 * 1024;

    private BookRepository() {
    }

    private static File booksDir(Context context) {
        File dir = new File(context.getFilesDir(), "books");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File translationDir(Context context) {
        File dir = new File(context.getFilesDir(), "translations");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static Book importUri(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String displayName = queryName(resolver, uri);
        byte[] bytes;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("无法读取所选文件");
            bytes = TextUtil.readAll(in, MAX_IMPORT_BYTES);
        }
        TextUtil.Decoded decoded = TextUtil.decode(bytes);
        String normalized = TextUtil.normalize(decoded.text);
        if (normalized.trim().isEmpty()) {
            throw new IOException("文件内容为空，或不是可识别的文本编码");
        }
        Book book = new Book();
        book.fileName = displayName;
        book.title = titleFromName(displayName, normalized);
        book.charset = decoded.charset;
        book.charCount = normalized.length();
        book.path = new File(booksDir(context), book.id + ".txt").getAbsolutePath();
        writeText(new File(book.path), normalized);
        Novel novel = Novel.parse(book, normalized);
        book.blockCount = novel.blockCount();
        book.chapterCount = novel.chapters.size();
        return book;
    }

    private static String queryName(ContentResolver resolver, Uri uri) {
        String name = null;
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            String last = uri.getLastPathSegment();
            name = last == null ? "导入的小说.txt" : last;
        }
        return name;
    }

    private static String titleFromName(String fileName, String text) {
        String title = fileName == null ? "" : fileName.trim();
        int dot = title.lastIndexOf('.');
        if (dot > 0) title = title.substring(0, dot);
        title = title.replace('_', ' ').trim();
        if (title.isEmpty() || title.matches("(?i)book|novel|untitled|txt|text|下载")) {
            String firstLine = text.split("\n", 2)[0].trim();
            if (!firstLine.isEmpty() && firstLine.length() <= 40) title = firstLine;
        }
        return title.isEmpty() ? "未命名小说" : title;
    }

    public static String readText(Book book) throws IOException {
        File file = new File(book.path);
        if (!file.exists()) throw new IOException("书籍文件不存在，可能已被删除");
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public static Novel load(Context context, Book book) throws IOException {
        String text = readText(book);
        Novel novel = Novel.parse(book, text);
        if (book.blockCount != novel.blockCount() || book.chapterCount != novel.chapters.size()) {
            book.blockCount = novel.blockCount();
            book.chapterCount = novel.chapters.size();
            book.charCount = text.length();
            AppStore.get(context).saveBook(book);
        }
        return novel;
    }

    public static void writeText(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void delete(Context context, Book book) {
        try {
            File file = new File(book.path);
            if (file.exists()) file.delete();
        } catch (Exception ignored) {
        }
        try {
            File trans = TranslationStore.fileFor(context, book.id);
            if (trans.exists()) trans.delete();
        } catch (Exception ignored) {
        }
    }

    public static String suggestExportName(Book book, String targetLang) {
        String name = book.title == null ? "小说" : book.title;
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.isEmpty()) name = "小说";
        String lang = targetLang == null ? "" : targetLang.toLowerCase(Locale.US);
        String suffix = lang.startsWith("zh") ? "中文" : lang;
        return name + "_译文_" + suffix + ".txt";
    }
}
