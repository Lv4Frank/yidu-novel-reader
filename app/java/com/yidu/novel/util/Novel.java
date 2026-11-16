package com.yidu.novel.util;

import com.yidu.novel.model.Book;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析后的整本小说：段落、翻译分块与章节。
 * 翻译以“块”为最小单位，块索引在本书内稳定，用于保存译文与阅读进度。
 */
public class Novel {
    public Book book;
    public final List<String> paragraphs = new ArrayList<>();
    public final List<Block> blocks = new ArrayList<>();
    public final List<Chapter> chapters = new ArrayList<>();

    public static class Block {
        public int index;
        public int paragraphIndex;
        public String text = "";
        public boolean firstInParagraph;
        public boolean heading;
    }

    public static class Chapter {
        public int index;
        public String title = "";
        public int startBlock;
        public int endBlock;
        public int startParagraph;
    }

    public int blockCount() {
        return blocks.size();
    }

    public static Novel parse(Book book, String normalizedText) {
        Novel novel = new Novel();
        novel.book = book;
        String text = normalizedText == null ? "" : normalizedText;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            String paragraph = line.trim();
            novel.paragraphs.add(paragraph);
        }
        boolean cjk = TextUtil.looksCjk(text);
        int maxBlock = cjk ? 420 : 900;
        int paragraphIndex = 0;
        int blockIndex = 0;
        for (String paragraph : novel.paragraphs) {
            paragraphIndex++;
            if (paragraph.isEmpty()) continue;
            boolean heading = TextUtil.isChapterTitle(paragraph);
            List<String> pieces = splitBlock(paragraph, maxBlock);
            for (int i = 0; i < pieces.size(); i++) {
                Block block = new Block();
                block.index = blockIndex++;
                block.paragraphIndex = paragraphIndex - 1;
                block.text = pieces.get(i);
                block.firstInParagraph = i == 0;
                block.heading = heading;
                novel.blocks.add(block);
            }
        }
        novel.buildChapters();
        return novel;
    }

    private static List<String> splitBlock(String paragraph, int max) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        int length = paragraph.length();
        while (length - start > max) {
            int cut = findCut(paragraph, start, max);
            if (cut <= start) cut = Math.min(length, start + max);
            // 不 trim 分块边界，避免英文等空格分词语言在分块处丢失空格
            pieces.add(paragraph.substring(start, cut));
            start = cut;
        }
        if (start < length) {
            String tail = paragraph.substring(start);
            if (!tail.isEmpty()) pieces.add(tail);
        }
        return pieces;
    }

    private static int findCut(String text, int start, int max) {
        int end = Math.min(text.length(), start + max);
        int floor = start + Math.max(80, max * 3 / 5);
        for (int i = end - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '…' || c == '.' || c == '!'
                    || c == '?' || c == ';' || c == '”' || c == '」' || c == '』') {
                return i + 1;
            }
        }
        for (int i = end - 1; i >= floor; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == '，' || c == '、' || c == ',' || c == '：' || c == ':') {
                return i + 1;
            }
        }
        return end;
    }

    private void buildChapters() {
        List<int[]> starts = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.firstInParagraph && block.heading) {
                starts.add(new int[]{i, block.paragraphIndex});
            }
        }
        if (starts.isEmpty()) {
            Chapter chapter = new Chapter();
            chapter.index = 0;
            chapter.title = "全文";
            chapter.startBlock = 0;
            chapter.endBlock = blocks.size();
            chapter.startParagraph = 0;
            chapters.add(chapter);
            return;
        }
        if (starts.get(0)[0] > 0) {
            Chapter preface = new Chapter();
            preface.index = 0;
            preface.title = "开篇";
            preface.startBlock = 0;
            preface.endBlock = starts.get(0)[0];
            preface.startParagraph = 0;
            chapters.add(preface);
        }
        for (int i = 0; i < starts.size(); i++) {
            Chapter chapter = new Chapter();
            chapter.index = chapters.size();
            int startBlock = starts.get(i)[0];
            chapter.startBlock = startBlock;
            chapter.endBlock = i + 1 < starts.size() ? starts.get(i + 1)[0] : blocks.size();
            chapter.startParagraph = starts.get(i)[1];
            chapter.title = TextUtil.shortTitle(paragraphs.get(starts.get(i)[1]), 36);
            if (chapter.title.isEmpty()) chapter.title = "第 " + (i + 1) + " 章";
            chapters.add(chapter);
        }
    }

    public int chapterOfBlock(int block) {
        if (chapters.isEmpty()) return 0;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            if (block >= chapter.startBlock && block < chapter.endBlock) return i;
        }
        return block < 0 ? 0 : chapters.size() - 1;
    }

    public Chapter chapter(int index) {
        if (chapters.isEmpty()) return null;
        int safe = Math.max(0, Math.min(chapters.size() - 1, index));
        return chapters.get(safe);
    }

    public String paragraphText(int paragraphIndex) {
        if (paragraphIndex < 0 || paragraphIndex >= paragraphs.size()) return "";
        return paragraphs.get(paragraphIndex);
    }

    public String chapterTitleOfBlock(int block) {
        Chapter chapter = chapter(chapterOfBlock(block));
        return chapter == null ? "" : chapter.title;
    }

    /** 观察当前渲染页面文本，供阅读器 TextView 显示。 */
    public String pageText(int from, int to, boolean paragraphSpacing, boolean indent) {
        if (from < 0) from = 0;
        if (to > blocks.size()) to = blocks.size();
        if (from >= to) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            Block block = blocks.get(i);
            if (block.firstInParagraph && i > from) {
                sb.append(paragraphSpacing ? "\n\n" : "\n");
            }
            if (block.firstInParagraph && indent) {
                sb.append("　　");
            }
            sb.append(block.text);
        }
        return sb.toString();
    }

    public int percentOfBlock(int block) {
        if (blocks.isEmpty()) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(block * 100.0 / blocks.size())));
    }
}
