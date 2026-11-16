package com.yidu.novel.util;

import com.yidu.novel.model.PromptProfile;

import java.util.ArrayList;
import java.util.List;

public final class Defaults {
    public static final String HIGH = "builtin_high";
    public static final String MEDIUM = "builtin_medium";
    public static final String LOW = "builtin_low";

    private Defaults() {
    }

    public static String promptFor(String key) {
        if (HIGH.equals(key)) {
            return "你是一位资深文学翻译家，任务是把小说原文翻译成目标语言，并达到正式出版级水准。\n"
                    + "要求：\n"
                    + "1. 先理解上下文、人物关系、语气与伏笔，再把每个段落译成地道、自然、有文学感的译文，避免翻译腔和逐字硬译。\n"
                    + "2. 完整保留原文信息、情节、语气、人物口吻、修辞、动作细节和情绪强弱；不增删、不概括、不解释、不评价。\n"
                    + "3. 对话要符合人物身份与说话习惯；叙述保持原文的时态与视角。\n"
                    + "4. 人名、地名、组织名、招式名、专有名词前后统一；术语表给出的译法必须严格遵守。\n"
                    + "5. 数字、日期、单位、符号、书信、诗歌、标题等格式尽量保持原样。\n"
                    + "6. 输出必须与输入段落一一对应，段落数量与顺序不得改变。";
        }
        if (LOW.equals(key)) {
            return "快速翻译小说文本。忠实传达主要情节与对话，语句通顺易读，保持原有段落数量与顺序。\n"
                    + "不要解释、总结、扩写或添加内容；人名与术语保持前后一致。";
        }
        return "你是专业文学译者。请把小说原文准确、通顺地翻译成目标语言：\n"
                + "1. 保留全部信息、情节、对话与情绪，不增删、不概括、不解释。\n"
                + "2. 译文自然流畅，符合目标语言阅读习惯，避免生硬直译。\n"
                + "3. 人名、地名、专有名词与术语前后统一；术语表给出的译法必须遵守。\n"
                + "4. 保持原有段落数量与顺序，数字、符号与格式尽量原样保留。";
    }

    public static List<PromptProfile> prompts() {
        List<PromptProfile> list = new ArrayList<>();
        list.add(new PromptProfile(HIGH, "高质量 · 文学精译", "high", promptFor(HIGH), true, HIGH));
        list.add(new PromptProfile(MEDIUM, "标准 · 通顺可靠", "medium", promptFor(MEDIUM), true, MEDIUM));
        list.add(new PromptProfile(LOW, "快速 · 省流", "low", promptFor(LOW), true, LOW));
        return list;
    }

    public static String qualityName(String quality) {
        if ("high".equals(quality)) return "高质量";
        if ("medium".equals(quality)) return "标准";
        if ("low".equals(quality)) return "快速";
        return "自定义";
    }
}
