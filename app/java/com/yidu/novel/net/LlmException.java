package com.yidu.novel.net;

public class LlmException extends Exception {
    public final int status;
    public final boolean retryable;
    public final boolean cancelled;

    public LlmException(String message, int status, boolean retryable, boolean cancelled) {
        super(message);
        this.status = status;
        this.retryable = retryable;
        this.cancelled = cancelled;
    }

    public static LlmException cancelled() {
        return new LlmException("已取消", 0, false, true);
    }

    public static LlmException network(String message) {
        return new LlmException(message, 0, true, false);
    }
}
