package com.yidu.novel.util;

import java.util.ArrayList;
import java.util.List;

public final class Lang {
    private static final String[][] TARGETS = {
            {"zh-CN", "简体中文", "简体中文（Simplified Chinese）"},
            {"zh-TW", "繁體中文", "繁體中文（Traditional Chinese）"},
            {"en", "English", "English"},
            {"ja", "日本語", "日本語（Japanese）"},
            {"ko", "한국어", "한국어（Korean）"},
            {"fr", "Français", "Français（French）"},
            {"de", "Deutsch", "Deutsch（German）"},
            {"es", "Español", "Español（Spanish）"},
            {"pt", "Português", "Português（Portuguese）"},
            {"ru", "Русский", "Русский（Russian）"},
            {"ar", "العربية", "العربية（Arabic）"},
            {"it", "Italiano", "Italiano（Italian）"},
            {"th", "ไทย", "ไทย（Thai）"},
            {"vi", "Tiếng Việt", "Tiếng Việt（Vietnamese）"},
            {"tr", "Türkçe", "Türkçe（Turkish）"},
            {"id", "Bahasa Indonesia", "Bahasa Indonesia（Indonesian）"},
            {"hi", "हिन्दी", "हिन्दी（Hindi）"},
            {"nl", "Nederlands", "Nederlands（Dutch）"},
            {"pl", "Polski", "Polski（Polish）"},
            {"uk", "Українська", "Українська（Ukrainian）"}
    };

    private Lang() {
    }

    public static String[] targetCodes() {
        String[] codes = new String[TARGETS.length];
        for (int i = 0; i < TARGETS.length; i++) codes[i] = TARGETS[i][0];
        return codes;
    }

    public static String[] targetLabels() {
        String[] labels = new String[TARGETS.length];
        for (int i = 0; i < TARGETS.length; i++) labels[i] = TARGETS[i][1];
        return labels;
    }

    public static String[] sourceLabels() {
        String[] labels = new String[TARGETS.length + 1];
        labels[0] = "自动识别";
        System.arraycopy(targetLabels(), 0, labels, 1, TARGETS.length);
        return labels;
    }

    public static String[] sourceCodes() {
        String[] codes = new String[TARGETS.length + 1];
        codes[0] = "auto";
        System.arraycopy(targetCodes(), 0, codes, 1, TARGETS.length);
        return codes;
    }

    public static String name(String code) {
        if (code == null || "auto".equals(code)) return "自动识别";
        for (String[] row : TARGETS) {
            if (row[0].equals(code)) return row[1];
        }
        return code;
    }

    public static String promptName(String code) {
        if (code == null || "auto".equals(code)) return "自动识别（根据文本实际语言判断）";
        for (String[] row : TARGETS) {
            if (row[0].equals(code)) return row[2];
        }
        return code;
    }

    public static int targetIndex(String code) {
        for (int i = 0; i < TARGETS.length; i++) {
            if (TARGETS[i][0].equals(code)) return i;
        }
        return 0;
    }

    public static int sourceIndex(String code) {
        if (code == null || "auto".equals(code)) return 0;
        return targetIndex(code) + 1;
    }

    public static List<String> targetCodeList() {
        List<String> list = new ArrayList<>();
        for (String code : targetCodes()) list.add(code);
        return list;
    }

    public static String detect(String text) {
        if (text == null || text.isEmpty()) return "auto";
        int cjk = 0;
        int hiragana = 0;
        int katakana = 0;
        int hangul = 0;
        int cyrillic = 0;
        int arabic = 0;
        int latin = 0;
        int devanagari = 0;
        int thai = 0;
        int limit = Math.min(text.length(), 8000);
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
            else if (c >= 0x3040 && c <= 0x309F) hiragana++;
            else if (c >= 0x30A0 && c <= 0x30FF) katakana++;
            else if (c >= 0xAC00 && c <= 0xD7AF) hangul++;
            else if (c >= 0x0400 && c <= 0x04FF) cyrillic++;
            else if (c >= 0x0600 && c <= 0x06FF) arabic++;
            else if (c >= 0x0900 && c <= 0x097F) devanagari++;
            else if (c >= 0x0E00 && c <= 0x0E7F) thai++;
            else if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) latin++;
        }
        if (hiragana + katakana > 2) return "ja";
        if (hangul > 2) return "ko";
        if (cjk > latin && cjk > 4) return "zh-CN";
        if (arabic > 2) return "ar";
        if (cyrillic > 2) return "ru";
        if (devanagari > 2) return "hi";
        if (thai > 2) return "th";
        if (latin > 20) {
            String lower = text.toLowerCase();
            int hits = 0;
            String[] markers = {" the ", " and ", " of ", " to ", " is ", " that ", " with "};
            for (String marker : markers) {
                int from = 0;
                while (from >= 0 && from < lower.length()) {
                    int index = lower.indexOf(marker, from);
                    if (index < 0) break;
                    hits++;
                    from = index + marker.length();
                }
            }
            if (hits >= 4) return "en";
        }
        return "auto";
    }

    public static boolean sameBase(String a, String b) {
        if (a == null || b == null) return false;
        String aa = a.startsWith("zh") ? "zh" : a;
        String bb = b.startsWith("zh") ? "zh" : b;
        return aa.equals(bb);
    }
}
