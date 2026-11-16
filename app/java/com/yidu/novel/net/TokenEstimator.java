package com.yidu.novel.net;

public final class TokenEstimator {
    private TokenEstimator() {
    }

    public static int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        int other = 0;
        int limit = text.length();
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0x3400 && c <= 0x9FFF)
                    || (c >= 0xAC00 && c <= 0xD7AF) || (c >= 0xF900 && c <= 0xFAFF)) {
                cjk++;
            } else {
                other++;
            }
        }
        return Math.max(1, (int) Math.ceil(cjk * 1.45 + other / 3.4) + 6);
    }

    public static int estimateOutput(String input) {
        return Math.max(24, (int) (estimate(input) * 1.25));
    }
}
