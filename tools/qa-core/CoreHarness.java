package qa;

import com.yidu.novel.model.Book;
import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.net.OutputGuard;
import com.yidu.novel.net.PromptBuilder;
import com.yidu.novel.net.PunctuationPreserver;
import com.yidu.novel.net.StreamJsonParser;
import com.yidu.novel.net.TokenEstimator;
import com.yidu.novel.util.Defaults;
import com.yidu.novel.util.Exporter;
import com.yidu.novel.util.Lang;
import com.yidu.novel.util.Novel;
import com.yidu.novel.util.TextUtil;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 纯 JVM 核心逻辑验证程序（不依赖 Android）。
 * 直接编译 app/java 下的工具类与 net 类，覆盖 task-3 列出的每一项。
 *
 * 运行：java -cp "tools/qa-core/classes;tools/lib/json-20240303.jar" qa.CoreHarness
 */
public final class CoreHarness {

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> observations = new ArrayList<>();
    private static String section = "";

    public static void main(String[] args) {
        System.out.println("=== 核心逻辑 JVM 验证（app/java 源码在 JVM 上直接编译运行）===");
        run("TextUtil.decode", CoreHarness::testDecode);
        run("TextUtil.normalize", CoreHarness::testNormalize);
        run("TextUtil.isChapterTitle", CoreHarness::testChapterTitle);
        run("Novel.parse 样例小说", CoreHarness::testNovelSample);
        run("Novel.parse 超长段落/空段落/分页", CoreHarness::testNovelLongParagraph);
        run("StreamJsonParser.parsePartial", CoreHarness::testStreamPartial);
        run("StreamJsonParser.parseFinal", CoreHarness::testStreamFinal);
        run("PromptBuilder", CoreHarness::testPromptBuilder);
        run("PunctuationPreserver", CoreHarness::testPunctuationPreserver);
        run("Exporter", CoreHarness::testExporter);
        run("OutputGuard", CoreHarness::testOutputGuard);
        run("TokenEstimator", CoreHarness::testTokenEstimator);
        run("附加观察（不计入 PASS/FAIL）", CoreHarness::testObservations);

        System.out.println();
        System.out.println("================ 汇总 ================");
        System.out.println("测试项: " + (passed + failed) + "    PASS: " + passed + "    FAIL: " + failed);
        if (!failures.isEmpty()) {
            System.out.println("失败清单:");
            for (String f : failures) System.out.println("  - " + f);
        }
        if (!observations.isEmpty()) {
            System.out.println("附加观察:");
            for (String o : observations) System.out.println("  - " + o);
        }
        System.out.println(failed == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        System.out.flush();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ==================== 测试项 ====================

    private static void testDecode() {
        section = "TextUtil.decode";
        String zh = "中文测试：长夜将尽，天亮了。";
        TextUtil.Decoded d = TextUtil.decode(zh.getBytes(StandardCharsets.UTF_8));
        eq("UTF-8 中文 charset", "UTF-8", d.charset);
        eq("UTF-8 中文文本", zh, d.text);

        byte[] withBom = concat(bytes(0xEF, 0xBB, 0xBF), zh.getBytes(StandardCharsets.UTF_8));
        d = TextUtil.decode(withBom);
        eq("UTF-8 BOM charset", "UTF-8", d.charset);
        eq("UTF-8 BOM 去掉 BOM", zh, d.text);
        check("UTF-8 BOM 结果不含 U+FEFF", !d.text.contains("\uFEFF"), "");

        String gbText = "国产小说：长夜将尽，天亮了！";
        byte[] gbBytes = gbText.getBytes(Charset.forName("GB18030"));
        d = TextUtil.decode(gbBytes);
        eq("GB18030 charset", "GB18030", d.charset);
        eq("GB18030 中文文本", gbText, d.text);

        String u16Mixed = "Lantern Keeper 灯笼守夜人";
        byte[] u16leWithBom = concat(bytes(0xFF, 0xFE), u16Mixed.getBytes(StandardCharsets.UTF_16LE));
        d = TextUtil.decode(u16leWithBom);
        eq("UTF-16LE 中文/英文 charset", "UTF-16LE", d.charset);
        eq("UTF-16LE 中文/英文文本", u16Mixed, d.text);

        String u16en = "Hello world, the lantern is lit.";
        byte[] u16enBom = concat(bytes(0xFF, 0xFE), u16en.getBytes(StandardCharsets.UTF_16LE));
        d = TextUtil.decode(u16enBom);
        eq("UTF-16LE 英文 charset", "UTF-16LE", d.charset);
        eq("UTF-16LE 英文文本", u16en, d.text);

        d = TextUtil.decode(u16en.getBytes(StandardCharsets.UTF_16LE));
        eq("UTF-16LE 无 BOM 英文（零字节启发式）charset", "UTF-16LE", d.charset);
        eq("UTF-16LE 无 BOM 英文文本", u16en, d.text);

        d = TextUtil.decode(new byte[0]);
        eq("空字节 charset", "UTF-8", d.charset);
        eq("空字节文本", "", d.text);
        eq("null 输入文本", "", TextUtil.decode(null).text);
    }

    private static void testNormalize() {
        section = "TextUtil.normalize";
        eq("null -> 空串", "", TextUtil.normalize(null));
        eq("空串 -> 空串", "", TextUtil.normalize(""));
        eq("CRLF 统一为 \\n", "第一行\n第二行", TextUtil.normalize("第一行\r\n第二行\r\n"));
        eq("孤立 CR 统一为 \\n", "a\nb", TextUtil.normalize("a\rb"));
        eq("去掉 BOM", "hello", TextUtil.normalize("\uFEFFhello"));
        eq("去掉 NUL", "ab", TextUtil.normalize("a\u0000b"));
        eq("连续空行折叠为 1 个空行", "a\n\nb\n\nc", TextUtil.normalize("a\n\n\n\nb\n\n\n\nc"));
        eq("保留单个空行", "a\n\nb", TextUtil.normalize("a\n\nb"));
        eq("行尾空白去掉", "a\nb", TextUtil.normalize("a   \nb\t \n"));
        eq("首尾空白（含全角空格/空行）去掉", "a", TextUtil.normalize("\n\n   　a  \n\n\n"));
        eq("多行样例块间隔保持", "A\n\nB\n\nC", TextUtil.normalize("\uFEFFA\r\n\r\n\r\nB  \r\n\r\nC\r\n"));
    }

    private static void testChapterTitle() {
        section = "TextUtil.isChapterTitle";
        String[] positives = {
                "第一章 开始", "第12章 归途", "第十二回 风起", "第3卷", "序章", "楔子",
                "Chapter 1", "CHAPTER IV", "Part 2  The Sea", "3. 归途", "1、开端"
        };
        for (String s : positives) {
            check("正例: " + show(s), TextUtil.isChapterTitle(s), "期望 true 实际 false");
        }
        String[] negatives = {
                "他说：第一章已经结束。", "", "   ", "这是一段普通文字。", "第一章的开始",
                "The Lantern Keeper", "2. 他说：这已经结束。", "12345",
                "第一章 开始但这句话特别特别长已经超过了四十个字符所以不应该被识别为章节标题对吧对吧"
        };
        for (String s : negatives) {
            check("反例: " + show(s), !TextUtil.isChapterTitle(s), "期望 false 实际 true");
        }
        check("null 标题", !TextUtil.isChapterTitle(null), "");
    }

    private static void testNovelSample() {
        section = "Novel.parse（TheLanternKeeper_en.txt）";
        String raw = readSample();
        String normalized = TextUtil.normalize(raw);
        check("样例原文长度 > 1000", normalized.length() > 1000, "len=" + normalized.length());

        Novel novel = Novel.parse(new Book(), normalized);
        check("parse 保留 book 引用", novel.book != null, "book=null");
        check("解析出的 paragraphs 非空", !novel.paragraphs.isEmpty(), "paragraphs=0");
        check("解析出的 blocks 非空", !novel.blocks.isEmpty(), "blocks=0");

        List<String> headings = new ArrayList<>();
        for (Novel.Block b : novel.blocks) {
            if (b.heading) headings.add(b.text);
        }
        eq("标题 block 数量 = 4", 4, headings.size());
        check("识别 Chapter 1-4", headingsContain(headings), "headings=" + headings);

        List<String> chapterNames = new ArrayList<>();
        int chapterTitles = 0;
        for (Novel.Chapter c : novel.chapters) {
            chapterNames.add(c.title);
            if (c.title.startsWith("Chapter ") || c.title.startsWith("CHAPTER ")) chapterTitles++;
        }
        eq("Chapter 章节数量 = 4", 4, chapterTitles);
        check("chapters >= 4", novel.chapters.size() >= 4, "chapters=" + novel.chapters.size() + " " + chapterNames);
        check("正文前有非标题内容时生成“开篇”", novel.chapters.size() == 5
                && "开篇".equals(novel.chapters.get(0).title), "章节=" + chapterNames);

        boolean coverage = !novel.chapters.isEmpty();
        if (coverage) {
            if (novel.chapters.get(0).startBlock != 0
                    || novel.chapters.get(novel.chapters.size() - 1).endBlock != novel.blockCount()) {
                coverage = false;
            }
            for (int i = 1; i < novel.chapters.size(); i++) {
                if (novel.chapters.get(i - 1).endBlock != novel.chapters.get(i).startBlock) coverage = false;
            }
            for (Novel.Chapter c : novel.chapters) {
                for (int b = c.startBlock; b < c.endBlock; b++) {
                    if (novel.chapterOfBlock(b) != c.index) coverage = false;
                }
            }
        }
        check("章节区间连续覆盖全部 block", coverage, "chapters=" + novel.chapters.size() + " blocks=" + novel.blockCount());
        check("chapter() 越界安全", novel.chapter(-3) == novel.chapters.get(0)
                && novel.chapter(999) == novel.chapters.get(novel.chapters.size() - 1), "");

        // 段落 <-> blocks 覆盖
        int cursor = 0;
        int nonEmptyParagraphs = 0;
        String mismatch = null;
        for (int p = 0; p < novel.paragraphs.size(); p++) {
            String para = novel.paragraphs.get(p);
            StringBuilder joined = new StringBuilder();
            while (cursor < novel.blockCount() && novel.blocks.get(cursor).paragraphIndex == p) {
                joined.append(novel.blocks.get(cursor).text);
                cursor++;
            }
            if (para.isEmpty()) {
                if (joined.length() != 0) {
                    mismatch = "空段落 #" + p + " 产生 block: " + show(joined.toString());
                    break;
                }
            } else {
                nonEmptyParagraphs++;
                if (!joined.toString().equals(para)) {
                    mismatch = "段落 #" + p + " 期望=" + show(para) + " 实际=" + show(joined.toString());
                    break;
                }
            }
        }
        if (cursor != novel.blockCount()) {
            mismatch = mismatch == null ? "存在未被任何段落覆盖的 block" : mismatch;
        }
        check("每段文本可由 blocks 完整还原", mismatch == null, mismatch == null ? "" : mismatch);
        eq("block 数 = 非空段落数（样例段落都不到切块阈值）", nonEmptyParagraphs, novel.blockCount());

        // pageText
        String page = novel.pageText(0, novel.blockCount(), false, false);
        StringBuilder expPage = new StringBuilder();
        StringBuilder expPageSpacing = new StringBuilder();
        for (int i = 0; i < novel.blockCount(); i++) {
            if (i > 0 && novel.blocks.get(i).firstInParagraph) {
                expPage.append('\n');
                expPageSpacing.append("\n\n");
            }
            expPage.append(novel.blocks.get(i).text);
            expPageSpacing.append(novel.blocks.get(i).text);
        }
        eq("pageText(0,n) 与块顺序拼接一致", expPage.toString(), page);
        eq("pageText paragraphSpacing=true", expPageSpacing.toString(),
                novel.pageText(0, novel.blockCount(), true, false));

        StringBuilder expStripped = new StringBuilder();
        for (String para : novel.paragraphs) {
            if (!para.isEmpty()) expStripped.append(para);
        }
        String pageStripped = page.replace("\n", "").replace("　", "");
        eq("pageText 不丢字", expStripped.toString(), pageStripped);

        int from = 3;
        int to = 9;
        StringBuilder expWindow = new StringBuilder();
        for (int i = from; i < to && i < novel.blockCount(); i++) {
            if (i > from && novel.blocks.get(i).firstInParagraph) expWindow.append('\n');
            expWindow.append(novel.blocks.get(i).text);
        }
        eq("pageText(3,9) 分页窗口", expWindow.toString(), novel.pageText(from, to, false, false));
        eq("pageText 越界参数钳制", page, novel.pageText(-5, novel.blockCount() + 50, false, false));
        eq("pageText from == to 返回空", "", novel.pageText(5, 5, false, false));
        eq("pageText from > to 返回空", "", novel.pageText(9, 3, false, false));
        String indented = novel.pageText(0, novel.blockCount(), false, true);
        check("pageText indent=true 段首缩进", indented.startsWith("　　"), "开头=" + show(abbreviate(indented)));
    }

    private static void testNovelLongParagraph() {
        section = "Novel.parse（超长段落/空段落）";
        StringBuilder longPara = new StringBuilder();
        for (int i = 0; i < 1000; i++) longPara.append('啊');
        String text = "第一段。\n\n" + longPara + "\n\n最后一段。";
        Novel novel = Novel.parse(new Book(), text);
        eq("段落数", 5, novel.paragraphs.size());
        eq("block 数（1 + 3 + 1）", 5, novel.blockCount());
        eq("空段落不产生 block", 0, Exporter.blocksOfParagraph(novel, 1).size());
        eq("空段落不产生 block（第二处）", 0, Exporter.blocksOfParagraph(novel, 3).size());

        List<Novel.Block> pieces = Exporter.blocksOfParagraph(novel, 2);
        check("超长段落被切块（>=2）", pieces.size() >= 2, "pieces=" + pieces.size());
        StringBuilder joined = new StringBuilder();
        boolean maxLenOk = true;
        boolean headOk = pieces.get(0).firstInParagraph && pieces.get(0).heading == false;
        for (int i = 0; i < pieces.size(); i++) {
            joined.append(pieces.get(i).text);
            if (pieces.get(i).text.length() > 420) maxLenOk = false;
            if (i > 0 && pieces.get(i).firstInParagraph) headOk = false;
            if (pieces.get(i).paragraphIndex != 2) headOk = false;
        }
        eq("切块不丢字", longPara.toString(), joined.toString());
        check("切块长度 <= 420（CJK 上限）", maxLenOk, "长度=" + pieces.stream().mapToInt(p -> p.text.length()).boxed().collect(java.util.stream.Collectors.toList()));
        check("块归属/首块标记正确", headOk, "");

        String page = novel.pageText(0, novel.blockCount(), false, false);
        StringBuilder stripped = new StringBuilder();
        for (String para : novel.paragraphs) {
            if (!para.isEmpty()) stripped.append(para);
        }
        eq("多块段落 pageText 不丢字", stripped.toString(), page.replace("\n", "").replace("　", ""));
        int longStart = Exporter.blocksOfParagraph(novel, 2).get(0).index;
        eq("超长段落分页窗口不丢字", longPara.toString(),
                novel.pageText(longStart, longStart + pieces.size(), false, false));
        eq("空段落 text 解析为空串", "", novel.paragraphText(1));
        eq("越界 paragraphText", "", novel.paragraphText(-1));
    }

    private static void testStreamPartial() {
        section = "StreamJsonParser.parsePartial";
        String raw = "[\"甲\",\"乙\\n丙\",\"丁\"]";
        List<String> expected = Arrays.asList("甲", "乙\n丙", "丁");
        int prev = 0;
        boolean monotonic = true;
        boolean sawFirst = false;
        boolean sawPartialSecond = false;
        boolean sawPartialThird = false;
        boolean sawAll = false;
        StringBuilder acc = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            acc.append(raw.charAt(i));
            StreamJsonParser.Result r = StreamJsonParser.parsePartial(acc.toString());
            if (r.items.size() < prev) monotonic = false;
            prev = r.items.size();
            if (r.items.size() == 1) sawFirst = true;
            if (r.items.size() == 1 && "乙\n丙".equals(r.partial)) sawPartialSecond = true;
            if (r.items.size() == 2 && "丁".equals(r.partial)) sawPartialThird = true;
            if (r.items.size() == 3 && r.partial.isEmpty()) sawAll = true;
        }
        check("分块 feed 时 items 数量单调不减", monotonic, "");
        check("逐步出现已完成 items", sawFirst, "");
        check("当前条 partial = 乙\\n丙（含转义换行）", sawPartialSecond, "");
        check("当前条 partial = 丁", sawPartialThird, "");
        check("数组收尾后 partial 为空且 items=3", sawAll, "");

        StreamJsonParser.Result r = StreamJsonParser.parsePartial(raw);
        eq("完整数组 items", expected, r.items);
        eq("完整数组 partial", "", r.partial);

        eq("null 无 items", 0, StreamJsonParser.parsePartial(null).items.size());
        eq("空串无 items", 0, StreamJsonParser.parsePartial("").items.size());
        eq("无 '[' 时无 items", 0, StreamJsonParser.parsePartial("好的，马上输出译文。").items.size());
        eq("对象包裹数组 items", Arrays.asList("一", "二"),
                StreamJsonParser.parsePartial("{\"translations\":[\"一\",\"二\"]}").items);
    }

    private static void testStreamFinal() {
        section = "StreamJsonParser.parseFinal";
        String fenced = "好的，以下是译文：\n```json\n[\"A\",\"B\"]\n```\n";
        eq("```json 围栏解析", Arrays.asList("A", "B"), StreamJsonParser.parseFinal(fenced));

        eq("{\"translations\":[...]} 对象解析", Arrays.asList("一", "二"),
                StreamJsonParser.parseFinal("{\"translations\":[\"一\",\"二\"]}"));

        boolean noThrow = true;
        List<String> truncated = null;
        try {
            truncated = StreamJsonParser.parseFinal("[\"甲\",\"乙");
        } catch (RuntimeException e) {
            noThrow = false;
        }
        check("非法截断输入不抛异常", noThrow, "");
        if (truncated != null) eq("截断输入保留已完成项", Arrays.asList("甲"), truncated);

        eq("对象数组元素按 translation/text 提取", Arrays.asList("甲", "乙"),
                StreamJsonParser.parseFinal("[{\"translation\":\"甲\"},{\"text\":\"乙\"},{\"other\":1}]"));

        eq("无 JSON 时按行回退并去序号", Arrays.asList("第一句。", "第二句。"),
                StreamJsonParser.parseFinal("1. 第一句。\n2. 第二句。"));

        eq("null 返回空列表", 0, StreamJsonParser.parseFinal(null).size());
        eq("纯说明文字不会崩溃且返回行文本", Arrays.asList("没有数组"),
                StreamJsonParser.parseFinal("没有数组"));
    }

    private static void testPromptBuilder() {
        section = "PromptBuilder";
        List<PromptProfile> profiles = Defaults.prompts();
        eq("内置提示词数量 = 3", 3, profiles.size());
        check("Defaults.promptFor(HIGH)", Defaults.promptFor(Defaults.HIGH).contains("文学翻译"), "");
        check("Defaults.promptFor(MEDIUM)", Defaults.promptFor(Defaults.MEDIUM).contains("专业文学译者"), "");
        check("Defaults.promptFor(LOW)", Defaults.promptFor(Defaults.LOW).contains("快速翻译"), "");
        check("Defaults.promptFor 未知 key 回退到 MEDIUM 文案",
                Defaults.promptFor("no_such_key").equals(Defaults.promptFor(Defaults.MEDIUM)), "");
        String glossary = "Mira → 米拉\nlantern → 灯笼";
        String chapter = "第一章 开始";
        for (PromptProfile p : profiles) {
            String sys = PromptBuilder.buildSystem(p.content, "en", "zh-CN", glossary, true, 3, chapter);
            check("[" + p.name + "] 含目标语言", sys.contains(Lang.promptName("zh-CN")), "target=" + Lang.promptName("zh-CN"));
            check("[" + p.name + "] 含源语言", sys.contains("原文语言：English"), "");
            check("[" + p.name + "] 含 JSON 数组输出约束",
                    sys.contains("只输出一个 JSON 字符串数组") && sys.contains("数组必须恰好包含 3 个字符串"), "");
            check("[" + p.name + "] 含章节上下文", sys.contains("当前章节：第一章 开始。"), "");
            check("[" + p.name + "] 含术语表内容", sys.contains("Mira → 米拉") && sys.contains("lantern → 灯笼"), "");
            check("[" + p.name + "] 含术语表区块标题", sys.contains("【术语表（必须严格遵守）】"), "");
            check("[" + p.name + "] 保留提示词正文", sys.contains(p.content.trim()), "");
        }

        String pOn = PromptBuilder.buildSystem("基础提示", "en", "zh-CN", "", true, 2, null);
        String pOff = PromptBuilder.buildSystem("基础提示", "en", "zh-CN", "", false, 2, null);
        check("标点开关 true 文案", pOn.contains("保留原文的标点符号写法与样式"), "");
        check("标点开关 false 文案", pOff.contains("按目标语言的标准标点习惯重新组织标点"), "");
        check("标点开关两种文案不同", !pOn.equals(pOff), "");
        check("无术语表时不出现术语区块", !pOn.contains("【术语表"), "");

        String autoSys = PromptBuilder.buildSystem("基础", "auto", "ja", null, false, 1, null);
        check("sourceLang=auto 提示自动识别",
                autoSys.contains("原文语言：自动识别（请根据文本实际语言判断）。"), "");
        check("目标语言日语", autoSys.contains(Lang.promptName("ja")), "target=" + Lang.promptName("ja"));

        String tpl = PromptBuilder.buildSystem("{source_lang}|{target_lang}|{chapter}|{glossary}",
                "en", "ko", "术语X", false, 2, "第五章");
        check("模板占位符替换", tpl.contains("English|") && tpl.contains(Lang.promptName("ko"))
                && tpl.contains("第五章") && tpl.contains("术语X"), abbreviate(tpl));

        String user = PromptBuilder.buildUser(Arrays.asList("甲段", "乙段"), "上一段内容");
        check("buildUser 含上文参考", user.contains("【上文参考（仅帮助理解，不要翻译）】") && user.contains("上一段内容"), "");
        check("buildUser 编号原文", user.contains("1. 甲段") && user.contains("2. 乙段"), "");
        check("buildUser 要求 JSON 数组", user.contains("请按 JSON 数组格式输出 2 段译文。"), "");

        String inventory = PunctuationPreserver.inventory("「你好」……——〜・《书》！？");
        String hinted = PromptBuilder.buildSystem("基础提示", "ja", "zh-CN", "", true,
                inventory, 1, null);
        check("标点提示包含原文实际标点清单", !inventory.isEmpty() && hinted.contains(inventory), "inventory=" + inventory);
        check("标点提示包含逐字沿用要求", hinted.contains("译文必须逐字沿用这些标点"), "");
    }

    private static void testPunctuationPreserver() {
        section = "PunctuationPreserver";

        String jpSource = "「おはよう」……そう言った——本当に〜・《本》！？";
        String jpWrong = "“早上好”...そう言った--本当に~·《本》!?";
        String jpExpected = "「早上好」……そう言った——本当に〜・《本》！？";
        eq("日文引号/省略号/破折号/波浪号/间隔号/书名号/全角感叹问号",
                jpExpected, PunctuationPreserver.apply(jpSource, jpWrong));
        eq("重复应用保持幂等", jpExpected, PunctuationPreserver.apply(jpSource, jpExpected));

        eq("日文嵌套引号", "「外『内』外」",
                PunctuationPreserver.apply("「外『内』外」", "“外‘内’外”"));
        eq("日文横线 U+2500 样式保留", "「──」",
                PunctuationPreserver.apply("「──」", "「--」"));
        eq("长破折号样式保留", "「——」",
                PunctuationPreserver.apply("「——」", "「──」"));
        eq("英文直引号保留为 ASCII", "\"你好，\"她说...",
                PunctuationPreserver.apply("\"Hello,\" she said...", "“你好，”她说……"));
        eq("英文撇号不被当作引号", "\"It's fine.\"",
                PunctuationPreserver.apply("\"It's fine.\"", "“It's fine.”"));
        eq("书名号样式保留", "〈书〉",
                PunctuationPreserver.apply("〈书〉", "《书》"));
        eq("单问号全角样式保留", "真的？",
                PunctuationPreserver.apply("本当？", "真的?"));
        eq("原文无特殊标点时不改动", "你好，世界。",
                PunctuationPreserver.apply("普通文本", "你好，世界。"));
        eq("原文无引号时译文引号保持原样", "他说“你好”",
                PunctuationPreserver.apply("他说你好", "他说“你好”"));
        eq("不完整引号也能转换", "「未完",
                PunctuationPreserver.apply("「未完", "“未完"));

        String inventory = PunctuationPreserver.inventory(jpSource);
        check("标点清单含「」", inventory.contains("「」"), "inventory=" + inventory);
        check("标点清单含省略号", inventory.contains("……"), "inventory=" + inventory);
        check("标点清单含破折号", inventory.contains("——"), "inventory=" + inventory);
        eq("无特殊标点时清单为空", "", PunctuationPreserver.inventory("普通文本"));
    }

    private static void testExporter() {
        section = "Exporter";
        String text = "第一章 开始\n\n这是第一段。\n\n这是第二段。\n\n第二章 继续\n\n这是第三段。";
        Novel novel = Novel.parse(new Book(), text);
        eq("构造数据段落数", 9, novel.paragraphs.size());
        eq("构造数据 block 数", 5, novel.blockCount());
        check("章节标题块标记", novel.blocks.get(0).heading && novel.blocks.get(3).heading, "");

        Map<Integer, String> tr = new LinkedHashMap<>();
        for (Novel.Block b : novel.blocks) tr.put(b.index, "译" + (b.index + 1));
        Exporter.Lookup all = (index, original) -> tr.get(index);

        String expectedTrans = "译1\n\n译2\n\n译3\n\n译4\n\n译5";
        String actualTrans = Exporter.build(novel, all, Exporter.MODE_TRANSLATION_ONLY, true);
        eq("MODE_TRANSLATION_ONLY 精确输出（含空行/标题行位置）", expectedTrans, actualTrans);

        String[] transLines = actualTrans.split("\n", -1);
        eq("译文模式行数 = 原段落数", novel.paragraphs.size(), transLines.length);
        boolean blankPositions = transLines[1].isEmpty() && transLines[3].isEmpty()
                && transLines[5].isEmpty() && transLines[7].isEmpty();
        check("译文模式空行位置与原文一致", blankPositions, "lines=" + Arrays.toString(transLines));
        eq("章节标题行（第 1 行）", "译1", transLines[0]);
        eq("章节标题行（第 7 行）", "译4", transLines[6]);
        eq("译文模式非空行数 = 非空段落数", 5,
                (int) Arrays.stream(transLines).filter(s -> !s.isEmpty()).count());

        String expectedBilingual = "第一章 开始\n译1\n\n这是第一段。\n译2\n\n这是第二段。\n译3\n\n"
                + "第二章 继续\n译4\n\n这是第三段。\n译5";
        String actualBilingual = Exporter.build(novel, all, Exporter.MODE_BILINGUAL, true);
        eq("MODE_BILINGUAL 精确输出（每段两行）", expectedBilingual, actualBilingual);

        String[] biLines = actualBilingual.split("\n", -1);
        eq("双语模式行数 = 2*非空段落 + 空段落", 14, biLines.length);
        eq("双语模式原文行 1", "第一章 开始", biLines[0]);
        eq("双语模式译文行 1", "译1", biLines[1]);
        eq("双语模式空行", "", biLines[2]);
        eq("双语模式原文行 2", "这是第一段。", biLines[3]);
        eq("双语模式译文行 2", "译2", biLines[4]);
        eq("双语模式原文行 5", "这是第三段。", biLines[12]);
        eq("双语模式译文行 5", "译5", biLines[13]);
        eq("双语模式章节标题行（第 10 行）", "第二章 继续", biLines[9]);

        Map<Integer, String> partial = new HashMap<>(tr);
        partial.remove(2);
        Exporter.Lookup mixed = (index, original) -> partial.get(index);
        String fallback = Exporter.build(novel, mixed, Exporter.MODE_TRANSLATION_ONLY, true);
        String[] fallbackLines = fallback.split("\n", -1);
        eq("未翻译段落 fallback 原文", "这是第二段。", fallbackLines[4]);
        eq("已翻译段落使用译文", "译2", fallbackLines[2]);
        eq("countTranslated = 4", 4, Exporter.countTranslated(novel, mixed));
        eq("countMissing = 1", 1, Exporter.countMissing(novel, mixed));
        eq("exportStats 统计", "已译 4 / 5 段（80%）", Exporter.exportStats(novel, mixed));

        eq("lookup=null + fallbackOriginal=false 输出原文", text,
                Exporter.build(novel, null, Exporter.MODE_TRANSLATION_ONLY, false));
        eq("lookup=null + fallbackOriginal=true 输出原文", text,
                Exporter.build(novel, null, Exporter.MODE_TRANSLATION_ONLY, true));
        eq("novel=null 返回空串", "", Exporter.build(null, all, Exporter.MODE_TRANSLATION_ONLY, true));

        // 多块段落的部分翻译：fallbackOriginal 语义
        StringBuilder longPara = new StringBuilder();
        for (int i = 0; i < 900; i++) longPara.append('啊');
        Novel n2 = Novel.parse(new Book(), "开篇\n\n" + longPara + "\n\n结束。");
        List<Novel.Block> longBlocks = Exporter.blocksOfParagraph(n2, 2);
        eq("长段落 block 数 = 3", 3, longBlocks.size());
        Map<Integer, String> tr2 = new HashMap<>();
        tr2.put(longBlocks.get(0).index, "A");
        Exporter.Lookup partialLong = (index, original) -> tr2.get(index);
        String[] outFb = Exporter.build(n2, partialLong, Exporter.MODE_TRANSLATION_ONLY, true).split("\n", -1);
        eq("部分翻译 + fallback 原文补齐", "A" + longBlocks.get(1).text + longBlocks.get(2).text, outFb[2]);
        String[] outNoFb = Exporter.build(n2, partialLong, Exporter.MODE_TRANSLATION_ONLY, false).split("\n", -1);
        eq("部分翻译 + fallbackOriginal=false 仅保留译文", "A", outNoFb[2]);

        eq("preview 行数限制", "a\nb", Exporter.preview("a\nb\nc\nd", 2));
    }

    private static void testOutputGuard() {
        section = "OutputGuard";
        String repeatedLine = "这是一行会不断重复出现的完全相同的内容，用于检测模型复读失控。";
        StringBuilder repeated = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) repeated.append('\n');
            repeated.append(repeatedLine);
        }
        check("重复行循环 -> true", OutputGuard.isDegenerate(repeated.toString(), 100), "len=" + repeated.length());

        String unit = "重复文本重复文本重复文本重复文本";
        check("短周期重复 -> true", OutputGuard.isDegenerate(unit.repeat(20), 50), "len=" + unit.length() * 20);

        check("超长同字符连续 -> true", OutputGuard.isDegenerate("啊".repeat(400), 10), "");

        StringBuilder normal = new StringBuilder();
        String[] phrases = {
                "雨点落在旧木桥上，发出细碎的声响。",
                "远处灯塔亮起，海面像一块冷冷的铁。",
                "她把信折好，放进上衣口袋，转身离开。"
        };
        for (int i = 1; i <= 80; i++) {
            normal.append("第").append(i).append("段[")
                    .append(Integer.toHexString(i * 40503)).append("]：")
                    .append(phrases[i % phrases.length]).append('\n');
        }
        check("正常长文本 -> false", !OutputGuard.isDegenerate(normal.toString(), normal.length() / 3),
                "len=" + normal.length());

        StringBuilder normalEn = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            normalEn.append("Paragraph ").append(i).append(" [").append(Integer.toHexString(i * 7919))
                    .append("]: The keeper walked along the shore and counted the lights.\n");
        }
        check("正常英文长文本 -> false", !OutputGuard.isDegenerate(normalEn.toString(), 400),
                "len=" + normalEn.length());

        check("空文本 -> false", !OutputGuard.isDegenerate("", 0), "");
        check("短文本 -> false", !OutputGuard.isDegenerate("你好，世界。", 5), "");
        check("null -> false", !OutputGuard.isDegenerate(null, 0), "");

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 20000; i++) huge.append((char) ('a' + (i % 26)));
        check("超过硬上限的输出 -> true", OutputGuard.isDegenerate(huge.toString(), 0), "len=" + huge.length());
    }

    private static void testTokenEstimator() {
        section = "TokenEstimator";
        eq("空串估算 = 0", 0, TokenEstimator.estimate(""));
        check("null 估算 = 0", TokenEstimator.estimate(null) == 0, "");
        check("中文估算 > 0", TokenEstimator.estimate("你好，世界") > 0, "");
        check("英文估算 > 0", TokenEstimator.estimate("Hello, world!") > 0, "");

        int shortZh = TokenEstimator.estimate("这是短文本。");
        int longZh = TokenEstimator.estimate("这是较长的段落内容，包含许多汉字，用于验证估算随长度增长。".repeat(20));
        check("随长度增长（中文）", longZh > shortZh, "short=" + shortZh + " long=" + longZh);

        int shortEn = TokenEstimator.estimate("Short text.");
        int longEn = TokenEstimator.estimate("The keeper walked along the shore and counted the lights.".repeat(20));
        check("随长度增长（英文）", longEn > shortEn, "short=" + shortEn + " long=" + longEn);
        check("同字符数中文估算高于英文",
                TokenEstimator.estimate("一二三四五六七八九十") > TokenEstimator.estimate("abcdefghij"),
                "zh=" + TokenEstimator.estimate("一二三四五六七八九十")
                        + " en=" + TokenEstimator.estimate("abcdefghij"));

        String text = "这是一段用于估算输出长度的文本。";
        int out = TokenEstimator.estimateOutput(text);
        check("estimateOutput >= 24", out >= 24, "out=" + out);
        check("estimateOutput >= estimate", out >= TokenEstimator.estimate(text), "");
        check("estimateOutput 随输入增长",
                TokenEstimator.estimateOutput(text.repeat(30)) > out, "");
    }

    private static void testObservations() {
        section = "附加观察（不计入 PASS/FAIL）";

        // 观察 1（非 task-3 必测项）：英文超长段落切块后，块拼接会丢失切分处空格
        // 影响 pageText（阅读页原文）与 Exporter 双语导出的原文行；CJK 段落无此问题。
        StringBuilder en = new StringBuilder();
        for (int i = 0; i < 30; i++) en.append("The lantern keeper walked along the shore. ");
        String enText = en.toString().trim();
        Novel enNovel = Novel.parse(new Book(), enText);
        StringBuilder joined = new StringBuilder();
        for (Novel.Block b : enNovel.blocks) joined.append(b.text);
        String enPage = enNovel.pageText(0, enNovel.blockCount(), false, false);
        boolean enPreserved = joined.toString().equals(enText) && enPage.equals(enText);
        observe("英文超长段落（>900 字符）切块拼接后是否保真", enPreserved,
                "原文长度=" + enText.length() + " block 数=" + enNovel.blockCount()
                        + " 拼接后长度=" + joined.length()
                        + " 首个差异=" + firstDiff(enText, joined.toString()));
    }

    // ==================== 断言/工具 ====================

    private interface Body {
        void run();
    }

    private static void run(String name, Body body) {
        System.out.println();
        System.out.println("---- " + name + " ----");
        try {
            body.run();
        } catch (Throwable t) {
            check("[" + name + "] 未抛出未预期异常", false,
                    t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
        }
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            String msg = "[" + section + "] " + name + (detail == null || detail.isEmpty() ? "" : "  -> " + detail);
            failures.add(msg);
            System.out.println("FAIL " + name + (detail == null || detail.isEmpty() ? "" : "  -> " + detail));
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        boolean ok = Objects.equals(expected, actual);
        check(name, ok, ok ? "" : "expected=" + show(expected) + " actual=" + show(actual));
    }

    private static void observe(String name, boolean ok, String detail) {
        String line = name + " -> " + (ok ? "符合预期" : "需要关注") + "：" + detail;
        observations.add(line);
        System.out.println("OBSERVE " + line);
    }

    private static String show(Object value) {
        if (value == null) return "null";
        String s = String.valueOf(value);
        if (s.length() > 260) s = s.substring(0, 260) + "…(len=" + s.length() + ")";
        return "\"" + s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private static String abbreviate(String s) {
        if (s == null) return "null";
        return s.length() <= 120 ? s : s.substring(0, 120) + "…(len=" + s.length() + ")";
    }

    private static String firstDiff(String a, String b) {
        if (a.equals(b)) return "无差异（完全一致）";
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                int from = Math.max(0, i - 20);
                int to = Math.min(a.length(), i + 20);
                return "index=" + i + " 原文=" + show(a.substring(from, to))
                        + " 实际=" + show(b.substring(Math.max(0, i - 20), Math.min(b.length(), i + 20)));
            }
        }
        return "长度相同但内容不同 a=" + a.length() + " b=" + b.length();
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) b[i] = (byte) values[i];
        return b;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static boolean headingsContain(List<String> headings) {
        for (String s : headings) {
            for (int i = 1; i <= 4; i++) {
                if (s.startsWith("Chapter " + i)) return true;
            }
        }
        // 4 个标题必须覆盖 Chapter 1..4
        for (int i = 1; i <= 4; i++) {
            boolean found = false;
            for (String s : headings) if (s.startsWith("Chapter " + i)) found = true;
            if (!found) {
                System.out.println("  缺少标题 Chapter " + i + "，实际=" + headings);
                return false;
            }
        }
        return true;
    }

    private static String readSample() {
        String[] candidates = {
                "tools/samples/TheLanternKeeper_en.txt",
                "../tools/samples/TheLanternKeeper_en.txt"
        };
        for (String c : candidates) {
            Path p = Paths.get(c);
            if (Files.exists(p)) {
                try {
                    return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("读取样例失败: " + p.toAbsolutePath(), e);
                }
            }
        }
        throw new IllegalStateException("找不到 tools/samples/TheLanternKeeper_en.txt，请在项目根目录运行");
    }
}
