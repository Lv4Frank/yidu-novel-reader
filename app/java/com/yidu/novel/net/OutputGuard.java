package com.yidu.novel.net;

import java.util.HashMap;
import java.util.Map;

/** 轻量级“模型复读/失控输出”检测，避免界面长时间卡死。 */
public final class OutputGuard {
    private OutputGuard() {
    }

    public static boolean isDegenerate(String raw, int inputChars) {
        if (raw == null || raw.isEmpty()) return false;
        int hardLimit = Math.max(12_000, inputChars * 8 + 4_000);
        if (raw.length() > hardLimit) return true;

        if (hasLongCharRun(raw, 320)) return true;

        String[] lines = raw.split("\\r?\\n");
        if (lines.length >= 8) {
            Map<String, Integer> counts = new HashMap<>();
            for (String line : lines) {
                String key = line.trim();
                if (key.length() < 24) continue;
                int count = counts.getOrDefault(key, 0) + 1;
                if (count >= 8) return true;
                counts.put(key, count);
            }
        }

        int limit = Math.min(raw.length(), 200_000);
        int[] windows = {32, 64, 128, 256};
        for (int window : windows) {
            int step = Math.max(8, window / 2);
            for (int start = 0; start + window * 6 <= limit; start += step) {
                int repeats = 1;
                while (start + window * (repeats + 1) <= limit
                        && raw.regionMatches(start, raw, start + window * repeats, window)) {
                    repeats++;
                }
                if (repeats >= 6) return true;
            }
        }
        return false;
    }

    private static boolean hasLongCharRun(String raw, int threshold) {
        int run = 1;
        char previous = raw.charAt(0);
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == previous && !Character.isWhitespace(c)) {
                run++;
                if (run >= threshold) return true;
            } else {
                run = 1;
                previous = c;
            }
        }
        return false;
    }
}
