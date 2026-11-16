package com.yidu.novel.net;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 60 秒滑动窗口 TPM 限流器。每个 API 配置独立计数。
 * 估算 token 在请求发出前先“占用”，等待窗口腾出空间后再发送。
 */
public final class TokenLimiter {
    private static final long WINDOW_MS = 60_000L;
    private static final Map<String, Deque<Entry>> WINDOWS = new HashMap<>();

    private TokenLimiter() {
    }

    public interface CancelCheck {
        boolean isCancelled();
    }

    public interface Waiter {
        void onWaiting(long millis);
    }

    public static boolean acquire(String key, int tpm, int tokens, CancelCheck cancel, Waiter waiter) {
        if (tpm <= 0) return true;
        int reserve = Math.max(1, Math.min(tokens, tpm));
        Deque<Entry> queue;
        synchronized (WINDOWS) {
            queue = WINDOWS.get(key);
            if (queue == null) {
                queue = new ArrayDeque<>();
                WINDOWS.put(key, queue);
            }
        }
        while (true) {
            if (cancel != null && cancel.isCancelled()) return false;
            long waitMs;
            synchronized (queue) {
                long now = System.currentTimeMillis();
                while (!queue.isEmpty() && now - queue.peekFirst().at >= WINDOW_MS) {
                    queue.pollFirst();
                }
                long used = 0;
                for (Entry e : queue) used += e.tokens;
                if (queue.isEmpty() || used + reserve <= tpm) {
                    queue.addLast(new Entry(now, reserve));
                    return true;
                }
                waitMs = WINDOW_MS - (now - queue.peekFirst().at) + 80L;
            }
            if (waitMs < 80) waitMs = 80;
            if (waiter != null) waiter.onWaiting(waitMs);
            long end = System.currentTimeMillis() + Math.min(waitMs, 1000L);
            while (System.currentTimeMillis() < end) {
                if (cancel != null && cancel.isCancelled()) return false;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
    }

    private static final class Entry {
        final long at;
        final int tokens;

        Entry(long at, int tokens) {
            this.at = at;
            this.tokens = tokens;
        }
    }
}
