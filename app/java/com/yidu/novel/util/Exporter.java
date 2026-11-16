package com.yidu.novel.util;

import java.util.List;

/** 按原文段落/空行/章节结构生成导出文本。纯 Java，便于 JVM 单测。 */
public final class Exporter {
    private Exporter() {
    }

    public interface Lookup {
        /** 返回译文；没有译文时返回 null。 */
        String translation(int blockIndex, String originalText);
    }

    public static final int MODE_TRANSLATION_ONLY = 0;
    public static final int MODE_BILINGUAL = 1;

    public static String build(Novel novel, Lookup lookup, int mode, boolean fallbackOriginal) {
        if (novel == null) return "";
        StringBuilder sb = new StringBuilder();
        int blockCursor = 0;
        int paragraphCount = novel.paragraphs.size();
        for (int p = 0; p < paragraphCount; p++) {
            String paragraph = novel.paragraphs.get(p);
            if (paragraph.isEmpty()) {
                sb.append('\n');
                continue;
            }
            StringBuilder original = new StringBuilder();
            StringBuilder translated = new StringBuilder();
            boolean hasTranslation = false;
            while (blockCursor < novel.blocks.size()
                    && novel.blocks.get(blockCursor).paragraphIndex == p) {
                Novel.Block block = novel.blocks.get(blockCursor);
                original.append(block.text);
                String value = lookup == null ? null : lookup.translation(block.index, block.text);
                if (value != null) {
                    translated.append(value);
                    hasTranslation = true;
                } else if (fallbackOriginal) {
                    translated.append(block.text);
                }
                blockCursor++;
            }
            if (original.length() == 0) {
                sb.append('\n');
                continue;
            }
            String out = hasTranslation || fallbackOriginal ? translated.toString() : original.toString();
            if (mode == MODE_BILINGUAL) {
                sb.append(original).append('\n').append(out).append('\n');
            } else {
                sb.append(out).append('\n');
            }
        }
        String result = sb.toString();
        if (result.endsWith("\n")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static int countTranslated(Novel novel, Lookup lookup) {
        if (novel == null || lookup == null) return 0;
        int count = 0;
        for (Novel.Block block : novel.blocks) {
            if (lookup.translation(block.index, block.text) != null) count++;
        }
        return count;
    }

    public static int countMissing(Novel novel, Lookup lookup) {
        if (novel == null) return 0;
        if (lookup == null) return novel.blocks.size();
        int count = 0;
        for (Novel.Block block : novel.blocks) {
            if (lookup.translation(block.index, block.text) == null) count++;
        }
        return count;
    }

    public static String exportStats(Novel novel, Lookup lookup) {
        int total = novel == null ? 0 : novel.blocks.size();
        int done = countTranslated(novel, lookup);
        int percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return "已译 " + done + " / " + total + " 段（" + percent + "%）";
    }

    /** 将译文按原文结构写入，返回用于展示的前若干行。 */
    public static String preview(String text, int lines) {
        if (text == null) return "";
        String[] all = text.split("\n", lines + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines, all.length); i++) {
            sb.append(all[i]);
            if (i < lines - 1) sb.append('\n');
        }
        return sb.toString();
    }

    public static List<Novel.Block> blocksOfParagraph(Novel novel, int paragraphIndex) {
        java.util.ArrayList<Novel.Block> blocks = new java.util.ArrayList<>();
        for (Novel.Block block : novel.blocks) {
            if (block.paragraphIndex == paragraphIndex) blocks.add(block);
        }
        return blocks;
    }
}
