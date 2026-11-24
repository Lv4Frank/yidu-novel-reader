package com.yidu.novel.net;

import java.util.ArrayList;
import java.util.List;

/**
 * 「保留原文标点」的确定性兜底：
 * 模型经常把日文「」改成中文“”、把……改成...、把——改成--，
 * 这里在译文生成/显示时按原文实际使用的标点风格做一次可逆、幂等的替换。
 * 只处理原文中确实出现过的标点，避免凭空改动译文。
 */
public final class PunctuationPreserver {
    private PunctuationPreserver() {
    }

    /**
     * 按原文标点风格修正译文。source 中没有的特征不会被改动；
     * 对已经符合原文风格的译文重复调用不会产生变化。
     */
    public static String apply(String source, String translation) {
        if (source == null || source.isEmpty() || translation == null || translation.isEmpty()) {
            return translation;
        }
        Style style = Style.detect(source);
        if (style == null) return translation;
        String result = translation;
        if (style.doubleOpen != null) result = rewriteQuotes(result, style);
        if (style.ellipsis != null) result = rewriteEllipsis(result, style.ellipsis);
        if (style.dash != null) result = rewriteDash(result, style.dash);
        if (style.wave != '\0') result = replaceAny(result, "~～〜∼", style.wave);
        if (style.middleDot != '\0') result = replaceAny(result, "·・•‧", style.middleDot);
        if (style.bookOpen != '\0') result = rewriteBookBrackets(result, style.bookOpen, style.bookClose);
        if (style.fullExclaim) result = result.replace('!', '！');
        else if (style.asciiExclaim) result = result.replace('！', '!');
        if (style.fullQuestion) result = result.replace('?', '？');
        else if (style.asciiQuestion) result = result.replace('？', '?');
        return result;
    }

    /** 给提示词用的原文标点清单；没有可保留标点时返回空串。 */
    public static String inventory(String source) {
        if (source == null || source.isEmpty()) return "";
        Style style = Style.detect(source);
        if (style == null) return "";
        List<String> parts = new ArrayList<>();
        if (style.doubleOpen != null) {
            if ("\"".equals(style.doubleOpen)) {
                parts.add("英文直引号 \"\"");
            } else {
                parts.add(style.doubleOpen + style.doubleClose);
            }
        }
        if (style.singleOpen != null && !style.singleOpen.equals(style.doubleOpen)) {
            parts.add(style.singleOpen + style.singleClose);
        }
        if (style.ellipsis != null) parts.add(style.ellipsis);
        if (style.dash != null) parts.add(style.dash);
        if (style.wave != '\0') parts.add(String.valueOf(style.wave));
        if (style.middleDot != '\0') parts.add(String.valueOf(style.middleDot));
        if (style.bookOpen != '\0') parts.add(style.bookOpen + String.valueOf(style.bookClose));
        if (style.fullExclaim && style.fullQuestion) parts.add("！？");
        else if (style.fullExclaim) parts.add("！");
        else if (style.fullQuestion) parts.add("？");
        else if (style.asciiExclaim && style.asciiQuestion) parts.add("!?");
        else if (style.asciiExclaim) parts.add("!");
        else if (style.asciiQuestion) parts.add("?");
        return String.join("、", parts);
    }

    private static String rewriteQuotes(String text, Style style) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        int asciiOpen = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '“':
                    sb.append(style.doubleOpen);
                    break;
                case '”':
                    sb.append(style.doubleClose);
                    break;
                case '「':
                    sb.append(style.doubleOpen);
                    break;
                case '」':
                    sb.append(style.doubleClose);
                    break;
                case '『':
                    sb.append(style.cornerOpen());
                    break;
                case '』':
                    sb.append(style.cornerClose());
                    break;
                case '‘':
                    sb.append(style.innerOpen());
                    break;
                case '’':
                    sb.append(style.innerClose());
                    break;
                case '"':
                    // ASCII 直引号没有方向，按出现顺序交替开闭。
                    if ("\"".equals(style.doubleOpen)) {
                        sb.append('"');
                    } else {
                        sb.append((asciiOpen++ % 2 == 0) ? style.doubleOpen : style.doubleClose);
                    }
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private static String rewriteEllipsis(String text, String style) {
        StringBuilder sb = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '…' || c == '．') {
                int j = i;
                while (j < text.length() && text.charAt(j) == c) j++;
                if (c == '…' || j - i >= 2) {
                    sb.append(style);
                    i = j;
                    continue;
                }
            }
            if (c == '.' && i + 1 < text.length() && text.charAt(i + 1) == '.') {
                int j = i;
                while (j < text.length() && text.charAt(j) == '.') j++;
                sb.append(style);
                i = j;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String rewriteDash(String text, String style) {
        StringBuilder sb = new StringBuilder(text.length() + 4);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (isDash(c)) {
                int j = i + 1;
                while (j < text.length() && isDash(text.charAt(j))) j++;
                sb.append(style);
                i = j;
                continue;
            }
            if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                int j = i + 1;
                while (j < text.length() && text.charAt(j) == '-') j++;
                sb.append(style);
                i = j;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String replaceAny(String text, String from, char to) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(from.indexOf(c) >= 0 ? to : c);
        }
        return sb.toString();
    }

    private static String rewriteBookBrackets(String text, char open, char close) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '《' || c == '〈') sb.append(open);
            else if (c == '》' || c == '〉') sb.append(close);
            else sb.append(c);
        }
        return sb.toString();
    }

    private static boolean isDash(char c) {
        return c == '—' || c == '–' || c == '―' || c == '─' || c == '━';
    }

    private static int count(String text, char c) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) total++;
        }
        return total;
    }

    private static final class Style {
        String doubleOpen;
        String doubleClose;
        String singleOpen;
        String singleClose;
        String ellipsis;
        String dash;
        char wave;
        char middleDot;
        char bookOpen;
        char bookClose;
        boolean fullExclaim;
        boolean asciiExclaim;
        boolean fullQuestion;
        boolean asciiQuestion;
        boolean japaneseCorner;

        static Style detect(String source) {
            Style s = new Style();
            boolean hasCornerOpen = source.indexOf('「') >= 0 || source.indexOf('」') >= 0;
            boolean hasCornerInner = source.indexOf('『') >= 0 || source.indexOf('』') >= 0;
            boolean hasCurlyDouble = source.indexOf('“') >= 0 || source.indexOf('”') >= 0;
            boolean hasCurlySingle = source.indexOf('‘') >= 0 || source.indexOf('’') >= 0;
            boolean hasAsciiDouble = count(source, '"') > 0;
            boolean hasAsciiSingle = count(source, '\'') > 0;
            s.japaneseCorner = hasCornerOpen || hasCornerInner;

            if (hasCornerOpen) {
                s.doubleOpen = "「";
                s.doubleClose = "」";
            } else if (hasCornerInner) {
                s.doubleOpen = "『";
                s.doubleClose = "』";
            } else if (hasCurlyDouble) {
                s.doubleOpen = "“";
                s.doubleClose = "”";
            } else if (hasAsciiDouble) {
                s.doubleOpen = "\"";
                s.doubleClose = "\"";
            } else if (hasCurlySingle) {
                s.doubleOpen = "‘";
                s.doubleClose = "’";
            }

            if (hasCornerOpen) {
                s.singleOpen = hasCornerInner ? "『" : "『";
                s.singleClose = hasCornerInner ? "』" : "』";
            } else if (hasCornerInner) {
                // 原文只用 『』 时它是主引号，不再假设另一层引号。
                s.singleOpen = null;
                s.singleClose = null;
            } else if (hasCurlySingle) {
                s.singleOpen = "‘";
                s.singleClose = "’";
            } else if ("“".equals(s.doubleOpen)) {
                s.singleOpen = "‘";
                s.singleClose = "’";
            } else if (hasAsciiSingle) {
                // 英文撇号很常见，不把 ' 当引号批量替换。
                s.singleOpen = null;
                s.singleClose = null;
            }

            if (source.contains("……")) s.ellipsis = "……";
            else if (source.contains("...")) s.ellipsis = "...";
            else if (source.contains("..")) s.ellipsis = "..";
            else if (source.contains("…")) s.ellipsis = "…";
            else if (source.contains("．．．")) s.ellipsis = "．．．";

            if (source.contains("──")) s.dash = "──";
            else if (source.contains("――")) s.dash = "――";
            else if (source.contains("——")) s.dash = "——";
            else if (source.contains("—")) s.dash = "—";
            else if (source.contains("--")) s.dash = "--";
            else if (source.contains("–")) s.dash = "–";
            else if (source.contains("―")) s.dash = "―";
            else if (source.contains("━")) s.dash = "━";
            else if (source.contains("─")) s.dash = "─";

            if (source.indexOf('〜') >= 0) s.wave = '〜';
            else if (source.indexOf('～') >= 0) s.wave = '～';
            else if (source.indexOf('∼') >= 0) s.wave = '∼';
            else if (source.indexOf('~') >= 0) s.wave = '~';

            if (source.indexOf('・') >= 0) s.middleDot = '・';
            else if (source.indexOf('·') >= 0) s.middleDot = '·';
            else if (source.indexOf('•') >= 0) s.middleDot = '•';
            else if (source.indexOf('‧') >= 0) s.middleDot = '‧';

            if (source.indexOf('《') >= 0 || source.indexOf('》') >= 0) {
                s.bookOpen = '《';
                s.bookClose = '》';
            } else if (source.indexOf('〈') >= 0 || source.indexOf('〉') >= 0) {
                s.bookOpen = '〈';
                s.bookClose = '〉';
            }

            boolean fullEx = source.indexOf('！') >= 0;
            boolean asciiEx = source.indexOf('!') >= 0;
            boolean fullQ = source.indexOf('？') >= 0;
            boolean asciiQ = source.indexOf('?') >= 0;
            s.fullExclaim = fullEx && !asciiEx;
            s.asciiExclaim = asciiEx && !fullEx;
            s.fullQuestion = fullQ && !asciiQ;
            s.asciiQuestion = asciiQ && !fullQ;

            boolean empty = s.doubleOpen == null && s.ellipsis == null && s.dash == null
                    && s.wave == '\0' && s.middleDot == '\0' && s.bookOpen == '\0'
                    && !s.fullExclaim && !s.asciiExclaim && !s.fullQuestion && !s.asciiQuestion;
            return empty ? null : s;
        }

        String cornerOpen() {
            return japaneseCorner && singleOpen != null ? singleOpen : doubleOpen;
        }

        String cornerClose() {
            return japaneseCorner && singleClose != null ? singleClose : doubleClose;
        }

        String innerOpen() {
            return singleOpen != null ? singleOpen : doubleOpen;
        }

        String innerClose() {
            return singleClose != null ? singleClose : doubleClose;
        }
    }
}
