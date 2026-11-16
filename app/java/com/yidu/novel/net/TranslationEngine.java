package com.yidu.novel.net;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.store.AppStore;
import com.yidu.novel.store.TranslationStore;
import com.yidu.novel.util.Lang;
import com.yidu.novel.util.Novel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 页面翻译引擎：按块分批请求，SSE 流式回填，逐块落盘；
 * 输出被截断或段落数不一致时自动拆分重试。
 */
public class TranslationEngine {
    public interface Listener {
        void onPageStart(int pageBlocks, int pendingBlocks);

        void onStatus(String status);

        void onPartial(int blockIndex, String text);

        void onBlockDone(int blockIndex, String text);

        void onProgress(int done, int total);

        void onFinished(int done, int failed, boolean cancelled, String note);
    }

    private final Novel novel;
    private final TranslationStore translations;
    private final AppStore store;
    private final ApiProfile api;
    private final PromptProfile prompt;
    private final Listener listener;
    private final ExecutorService executor;
    private final AtomicInteger generation = new AtomicInteger(0);
    private volatile boolean cancelled;
    private volatile LlmClient activeClient;

    public TranslationEngine(Novel novel, TranslationStore translations, AppStore store,
                             ApiProfile api, PromptProfile prompt, Listener listener) {
        this.novel = novel;
        this.translations = translations;
        this.store = store;
        this.api = api;
        this.prompt = prompt;
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "yidu-translate");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(List<Integer> pageBlocks, boolean force) {
        final int gen = generation.incrementAndGet();
        cancelled = false;
        LlmClient client = activeClient;
        if (client != null) client.cancel();
        executor.execute(() -> {
            try {
                run(gen, pageBlocks, force);
            } catch (Throwable e) {
                if (gen == generation.get()) {
                    listener.onStatus("翻译线程异常：" + safe(e.getMessage()));
                    listener.onFinished(0, 0, cancelled, "翻译失败");
                }
            }
        });
    }

    public void cancel() {
        cancelled = true;
        generation.incrementAndGet();
        LlmClient client = activeClient;
        if (client != null) client.cancel();
    }

    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    private void run(int gen, List<Integer> pageBlocks, boolean force) {
        if (gen != generation.get()) return;
        List<Integer> valid = new ArrayList<>();
        if (pageBlocks != null) {
            for (Integer index : pageBlocks) {
                if (index == null || index < 0 || index >= novel.blocks.size()) continue;
                String text = novel.blocks.get(index).text;
                if (text == null || text.trim().isEmpty()) continue;
                valid.add(index);
            }
        }
        int pending = 0;
        for (Integer index : valid) {
            if (force || !translations.has(index, novel.blocks.get(index).text)) pending++;
        }
        listener.onPageStart(valid.size(), pending);
        if (valid.isEmpty()) {
            listener.onFinished(0, 0, false, "本页没有可翻译的文字");
            return;
        }
        if (pending == 0) {
            listener.onFinished(0, 0, false, "本页译文已存在");
            return;
        }
        if (api == null) {
            listener.onFinished(0, valid.size(), false, "尚未配置翻译 API，请先到「API 设置」添加接口");
            return;
        }
        if (store.getTargetLang() == null || store.getTargetLang().isEmpty()) {
            listener.onFinished(0, valid.size(), false, "尚未设置译文语种");
            return;
        }

        String pageText = pageText(valid);
        String detected = Lang.detect(pageText);
        String sourceLang = store.getSourceLang();
        if ("auto".equals(sourceLang)) sourceLang = detected;
        if (!"auto".equals(sourceLang) && Lang.sameBase(sourceLang, store.getTargetLang())
                && !"auto".equals(detected)) {
            listener.onFinished(0, 0, false, "当前页已经是目标语言，无需翻译");
            return;
        }

        if (force) {
            for (Integer index : valid) translations.delete(index);
        }

        String context = contextBefore(valid);
        List<List<Integer>> batches = planBatches(valid);
        int done = 0;
        int failed = 0;
        int total = pending;
        for (int i = 0; i < batches.size(); i++) {
            if (cancelled || gen != generation.get()) break;
            List<Integer> batch = batches.get(i);
            List<Integer> todo = new ArrayList<>();
            for (Integer index : batch) {
                if (!translations.has(index, novel.blocks.get(index).text)) todo.add(index);
            }
            if (todo.isEmpty()) continue;
            listener.onStatus("正在翻译第 " + (i + 1) + " / " + batches.size() + " 批（"
                    + todo.size() + " 段）");
            BatchResult result = translateBatch(gen, todo, context, 0, sourceLang);
            done += result.done;
            failed += result.failed;
            listener.onProgress(Math.min(done, total), total);
            if (result.fatal != null) {
                listener.onFinished(done, Math.max(0, total - done), false, result.fatal);
                return;
            }
        }
        boolean wasCancelled = cancelled || gen != generation.get();
        String note;
        if (wasCancelled) {
            note = "已停止翻译，已完成的译文保留";
        } else if (failed > 0) {
            note = "完成，有 " + failed + " 段未翻译成功，可点「重翻本页」再试";
        } else {
            note = "本页翻译完成";
        }
        listener.onFinished(done, failed, wasCancelled, note);
    }

    private static final class BatchResult {
        int done;
        int failed;
        String fatal;
    }

    private BatchResult translateBatch(int gen, List<Integer> todo, String context, int depth, String sourceLang) {
        BatchResult result = new BatchResult();
        if (cancelled || gen != generation.get()) return result;
        List<String> texts = new ArrayList<>();
        for (Integer index : todo) texts.add(novel.blocks.get(index).text);
        int expected = todo.size();
        String glossary = store.glossaryPrompt(1600);
        String chapterTitle = novel.chapterTitleOfBlock(todo.get(0));
        String system = PromptBuilder.buildSystem(prompt == null ? "" : prompt.content,
                sourceLang, store.getTargetLang(), glossary, store.isPunctuationPreserve(),
                expected, chapterTitle);
        String user = PromptBuilder.buildUser(texts, context);

        int requestMax = api.unlimitedOutput ? 0 : Math.max(64, api.maxOutputTokens);
        int estimate = TokenEstimator.estimate(system + user)
                + (requestMax > 0 ? requestMax : TokenEstimator.estimateOutput(concat(texts)));
        try {
            boolean acquired = TokenLimiter.acquire(api.id + "@" + Math.max(0, api.tpm), api.tpm, estimate,
                    () -> cancelled || gen != generation.get(),
                    millis -> listener.onStatus("已触发 TPM 限流：等待约 "
                            + Math.max(1, (millis + 999) / 1000) + " 秒后自动继续…"));
            if (!acquired) return result;
        } catch (Exception ignored) {
            return result;
        }

        String accum = "";
        boolean truncated = false;
        String finishReason = "";
        LlmException fatalError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (cancelled || gen != generation.get()) return result;
            LlmClient client = new LlmClient(api);
            activeClient = client;
            final StringBuilder buffer = new StringBuilder();
            final int[] emitted = {0};
            final int[] partialBlock = {-1};
            try {
                client.stream(system, user, requestMax, new LlmClient.StreamCallback() {
                    @Override
                    public void onDelta(String text) {
                        if (text == null || text.isEmpty()) return;
                        buffer.append(text);
                        StreamJsonParser.Result parsed = StreamJsonParser.parsePartial(buffer.toString());
                        while (emitted[0] < parsed.items.size() && emitted[0] < todo.size()) {
                            String value = parsed.items.get(emitted[0]).trim();
                            int index = todo.get(emitted[0]);
                            if (!value.isEmpty()) {
                                translations.put(index, novel.blocks.get(index).text, value);
                                listener.onBlockDone(index, value);
                            }
                            emitted[0]++;
                        }
                        if (parsed.items.size() < todo.size() && !parsed.partial.isEmpty()) {
                            int index = todo.get(Math.min(parsed.items.size(), todo.size() - 1));
                            if (index != partialBlock[0] || parsed.partial.length() > 4) {
                                partialBlock[0] = index;
                                listener.onPartial(index, parsed.partial);
                            }
                        }
                    }
                });
                truncated = client.isTruncated();
                finishReason = client.getFinishReason();
                accum = buffer.toString();
                activeClient = null;
                break;
            } catch (LlmException e) {
                activeClient = null;
                if (e.cancelled || cancelled || gen != generation.get()) return result;
                if (e.retryable && attempt == 0) {
                    listener.onStatus("请求失败，2 秒后自动重试：" + firstLine(e.getMessage()));
                    sleep(2000);
                    continue;
                }
                fatalError = e;
                break;
            } catch (Exception e) {
                activeClient = null;
                if (cancelled || gen != generation.get()) return result;
                fatalError = new LlmException("翻译请求异常：" + safe(e.getMessage()), 0, true, false);
                break;
            }
        }
        if (cancelled || gen != generation.get()) return result;
        if (fatalError != null) {
            listener.onStatus(firstLine(fatalError.getMessage()));
            result.fatal = firstLine(fatalError.getMessage());
            result.failed = expected;
            return result;
        }
        if (accum.isEmpty()) {
            result.failed = expected;
            return result;
        }

        // 最终再解析一次，补齐流式解析可能漏掉的条目
        List<String> finalItems = StreamJsonParser.parseFinal(accum);
        int saveCount = Math.min(expected, finalItems.size());
        int alreadySaved = 0;
        for (Integer unused : todo) {
            // 计数由 translations 判断
            break;
        }
        for (int i = 0; i < saveCount; i++) {
            String value = finalItems.get(i).trim();
            int index = todo.get(i);
            if (value.isEmpty()) continue;
            if (!translations.has(index, novel.blocks.get(index).text)) {
                translations.put(index, novel.blocks.get(index).text, value);
                listener.onBlockDone(index, value);
            }
        }
        for (Integer index : todo) {
            if (translations.has(index, novel.blocks.get(index).text)) alreadySaved++;
        }
        if (alreadySaved >= expected) {
            result.done = alreadySaved;
            return result;
        }

        int remaining = expected - alreadySaved;
        if (depth < 2 && expected > 1) {
            listener.onStatus((truncated ? "模型输出被截断" : "模型返回的段落数不一致")
                    + "，已自动拆分后重试剩余 " + remaining + " 段…");
            List<Integer> missing = new ArrayList<>();
            for (Integer index : todo) {
                if (!translations.has(index, novel.blocks.get(index).text)) missing.add(index);
            }
            int mid = missing.size() / 2;
            BatchResult left = translateBatch(gen, new ArrayList<>(missing.subList(0, mid)), "", depth + 1, sourceLang);
            BatchResult right = translateBatch(gen, new ArrayList<>(missing.subList(mid, missing.size())), "", depth + 1, sourceLang);
            result.done = left.done + right.done;
            result.failed = left.failed + right.failed;
            result.fatal = left.fatal != null ? left.fatal : right.fatal;
            return result;
        }
        listener.onStatus("模型返回不完整（" + alreadySaved + "/" + expected
                + "），可尝试降低 TPM 或提高「单次最大输出」");
        result.done = alreadySaved;
        result.failed = remaining;
        return result;
    }

    private List<List<Integer>> planBatches(List<Integer> blocks) {
        List<List<Integer>> batches = new ArrayList<>();
        int budget = batchCharBudget();
        List<Integer> current = new ArrayList<>();
        int chars = 0;
        for (Integer index : blocks) {
            int length = novel.blocks.get(index).text.length();
            if (!current.isEmpty() && chars + length > budget) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
            current.add(index);
            chars += length;
            if (length >= budget && current.size() == 1) {
                batches.add(current);
                current = new ArrayList<>();
                chars = 0;
            }
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private int batchCharBudget() {
        int maxOut = api.unlimitedOutput ? 4096 : Math.max(128, api.maxOutputTokens);
        double factor = "high".equals(prompt == null ? "" : prompt.quality) ? 0.65
                : "low".equals(prompt == null ? "" : prompt.quality) ? 1.05 : 0.85;
        int budget = (int) (maxOut * factor);
        if (api.tpm > 0) {
            int maxInputTokens = Math.max(160, api.tpm / 2 - Math.min(maxOut, api.tpm / 2));
            int tpmChars = (int) (maxInputTokens / 1.55);
            budget = Math.min(budget, Math.max(180, tpmChars));
        }
        return Math.max(180, Math.min(4000, budget));
    }

    private String contextBefore(List<Integer> pageBlocks) {
        int first = pageBlocks.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, first - 2); i < first; i++) {
            String text = novel.blocks.get(i).text;
            if (text != null && !text.trim().isEmpty()) sb.append(text).append(' ');
        }
        String context = sb.toString().trim();
        if (context.length() > 700) context = context.substring(context.length() - 700);
        return context;
    }

    private String pageText(List<Integer> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Integer index : blocks) sb.append(novel.blocks.get(index).text).append('\n');
        return sb.toString();
    }

    private static String concat(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) sb.append(value);
        return sb.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String firstLine(String value) {
        if (value == null) return "";
        int newline = value.indexOf('\n');
        String line = newline < 0 ? value : value.substring(0, newline);
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
