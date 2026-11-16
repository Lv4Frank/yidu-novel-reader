package com.yidu.novel.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class TextUtil {
    private TextUtil() {
    }

    public static class Decoded {
        public final String text;
        public final String charset;

        public Decoded(String text, String charset) {
            this.text = text;
            this.charset = charset;
        }
    }

    /** 统一换行、去掉 BOM 与不可见字符、折叠多余空行。 */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String text = raw.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\u0000", "");
        StringBuilder sb = new StringBuilder(text.length());
        int blankRun = 0;
        for (String line : text.split("\n", -1)) {
            String trimmed = rstrip(line);
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun > 1) continue;
                sb.append('\n');
            } else {
                blankRun = 0;
                sb.append(trimmed).append('\n');
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && Character.isWhitespace(sb.charAt(start))) start++;
        while (end > start && Character.isWhitespace(sb.charAt(end - 1))) end--;
        return sb.substring(start, end);
    }

    private static String rstrip(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }

    public static Decoded decode(byte[] data) {
        if (data == null || data.length == 0) return new Decoded("", "UTF-8");
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new Decoded(new String(data, 3, data.length - 3, StandardCharsets.UTF_8), "UTF-8");
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }
        int zeros = 0;
        int sample = Math.min(data.length, 4000);
        for (int i = 0; i < sample; i++) {
            if (data[i] == 0) zeros++;
        }
        if (zeros > sample / 8) {
            String utf16 = tryDecode(data, StandardCharsets.UTF_16LE);
            if (utf16 != null) return new Decoded(utf16, "UTF-16LE");
        }
        String utf8 = tryDecode(data, StandardCharsets.UTF_8);
        if (utf8 != null) return new Decoded(utf8, "UTF-8");
        try {
            return new Decoded(new String(data, Charset.forName("GB18030")), "GB18030");
        } catch (Exception e) {
            return new Decoded(new String(data, StandardCharsets.UTF_8), "UTF-8");
        }
    }

    private static String tryDecode(byte[] data, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    public static byte[] readAll(java.io.InputStream in, int maxBytes) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > maxBytes) {
                throw new java.io.IOException("文件超过 " + (maxBytes / 1024 / 1024) + "MB，无法导入");
            }
        }
        return out.toByteArray();
    }

    public static boolean isChapterTitle(String line) {
        if (line == null) return false;
        String text = line.trim();
        if (text.isEmpty() || text.length() > 40) return false;
        if (text.matches("^第[0-9０-９零〇一二三四五六七八九十百千万两]+[章回節节卷幕篇集部话話]([\\s:：.．、\\-—_].{0,30})?$")) return true;
        if (text.matches("^(序章|楔子|引子|前言|序言|后记|後記|尾声|尾聲|终章|終章|番外|外传|外傳|正文|目录|目錄)([\\s:：.．、\\-—_].{0,30})?$")) return true;
        if (text.matches("^(?i)(chapter|part|volume|book)\\s+([0-9]{1,4}|[ivxlcdm]{1,8})([\\s:：.．、\\-—_].{0,30})?$")) return true;
        if (text.matches("^[0-9]{1,4}[、.．]\\s*\\S.{0,25}$") && !text.matches(".*[。！？!?].*")) return true;
        return false;
    }

    public static boolean looksCjk(String text) {
        if (text == null || text.isEmpty()) return true;
        int cjk = 0;
        int total = 0;
        int limit = Math.min(text.length(), 20000);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isDigit(c)) total++;
            if (c >= 0x3400 && c <= 0x9FFF) cjk++;
        }
        return total == 0 || cjk * 100 / total >= 20;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    public static String shortTitle(String value, int max) {
        if (value == null) return "";
        String text = value.replace('\n', ' ').trim();
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }
}
