package com.yidu.novel.net;

import com.yidu.novel.util.Lang;

import java.util.List;

/** 组装翻译提示词：质量风格 + 语种 + 术语表 + 标点保留 + JSON 输出约束。 */
public final class PromptBuilder {
    private PromptBuilder() {
    }

    public static String buildSystem(String basePrompt, String sourceLang, String targetLang,
                                     String glossary, boolean punctuationPreserve, int expectedCount,
                                     String chapterTitle) {
        return buildSystem(basePrompt, sourceLang, targetLang, glossary, punctuationPreserve,
                "", expectedCount, chapterTitle);
    }

    public static String buildSystem(String basePrompt, String sourceLang, String targetLang,
                                     String glossary, boolean punctuationPreserve, String punctuationHint,
                                     int expectedCount, String chapterTitle) {
        String source = Lang.promptName(sourceLang);
        String target = Lang.promptName(targetLang);
        String style = basePrompt == null ? "" : basePrompt.trim();
        style = style.replace("{source_lang}", source)
                .replace("{target_lang}", target)
                .replace("{chapter}", chapterTitle == null ? "" : chapterTitle)
                .replace("{glossary}", glossary == null ? "" : glossary);
        StringBuilder sb = new StringBuilder();
        sb.append(style).append("\n\n");
        sb.append("【本次任务】\n");
        sb.append("把下面的小说原文逐段翻译成 ").append(target).append("。\n");
        if ("auto".equals(sourceLang)) {
            sb.append("原文语言：自动识别（请根据文本实际语言判断）。\n");
        } else {
            sb.append("原文语言：").append(source).append("。\n");
        }
        if (chapterTitle != null && !chapterTitle.isEmpty()) {
            sb.append("当前章节：").append(chapterTitle).append("。\n");
        }
        if (punctuationPreserve) {
            sb.append("标点要求：保留原文的标点符号写法与样式（引号、省略号、破折号、波浪号等），不要替换成目标语言的标点习惯；原文的省略号、书名号、间隔号等尽量沿用。\n");
            if (punctuationHint != null && !punctuationHint.trim().isEmpty()) {
                sb.append("原文实际使用的主要标点：").append(punctuationHint.trim())
                        .append("。译文必须逐字沿用这些标点，不要改成目标语言的常见写法。\n");
            }
        } else {
            sb.append("标点要求：按目标语言的标准标点习惯重新组织标点，使译文读起来自然。\n");
        }
        if (glossary != null && !glossary.trim().isEmpty()) {
            sb.append("\n【术语表（必须严格遵守）】\n").append(glossary.trim()).append("\n");
        }
        sb.append("\n【输出格式（必须严格遵守）】\n");
        sb.append("1. 只输出一个 JSON 字符串数组，不要输出 Markdown、代码块、解释、注释或任何多余文字。\n");
        sb.append("2. 数组必须恰好包含 ").append(expectedCount).append(" 个字符串，顺序与输入段落一一对应。\n");
        sb.append("3. 每个数组元素只放对应段落的译文，不要合并段落、不要拆分段落、不要添加序号。\n");
        sb.append("4. 段落内部的换行请写成 \\n 转义；引号必须按 JSON 规则转义。\n");
        sb.append("5. 每段只翻译一次，严禁重复输出同一段内容或陷入循环。\n");
        sb.append("6. 若原文中混有已经是目标语言的段落，也原样返回该段落。\n");
        return sb.toString();
    }

    public static String buildUser(List<String> texts, String context) {
        StringBuilder sb = new StringBuilder();
        if (context != null && !context.trim().isEmpty()) {
            sb.append("【上文参考（仅帮助理解，不要翻译）】\n");
            sb.append(context.trim()).append("\n\n");
        }
        sb.append("【待翻译原文】\n");
        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }
        sb.append("\n请按 JSON 数组格式输出 ").append(texts.size()).append(" 段译文。");
        return sb.toString();
    }
}
