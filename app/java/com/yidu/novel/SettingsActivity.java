package com.yidu.novel;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.Glossary;
import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.util.Lang;
import com.yidu.novel.util.Ui;

import java.util.List;
import java.util.Locale;

/**
 * 设置总入口：翻译服务状态、翻译设置、阅读设置与关于信息。
 * 所有修改实时写入 AppStore，ReaderActivity 会在 onResume 时重新读取。
 */
public class SettingsActivity extends BaseActivity {
    private static final String THINKING_DOC = "https://api-docs.deepseek.com/zh-cn/guides/thinking_mode";
    private static final int[] TEXT_PRESETS = {0xFF2B2118, 0xFF000000, 0xFF3F3F46, 0xFF1E3A5F, 0xFFEDE9E3, 0xFFF5F5F4};
    private static final int[] BG_PRESETS = {0xFFF6F1E7, 0xFFFFFFFF, 0xFFE8F0E3, 0xFF2B2B2B, 0xFF000000, 0xFF1E293B};

    private static final String[] FAMILIES = {"serif", "sans", "mono"};
    private static final String[] FAMILY_LABELS = {"宋体", "黑体", "等宽"};
    private static final String[] TURN_MODES = {"tap", "swipe", "volume"};
    private static final String[] TURN_LABELS = {"点击左右", "滑动", "音量键"};

    private boolean firstResume = true;

    private interface ColorPick {
        void onPick(int color);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        // 从 API / 提示词 / 术语表等子页面返回后刷新显示。
        render();
    }

    private void render() {
        LinearLayout body = Ui.vertical(this);
        body.addView(apiCard(), Ui.cardMargin(this));
        body.addView(translationCard(), Ui.cardMargin(this));
        body.addView(readingCard(), Ui.cardMargin(this));
        body.addView(aboutCard(), Ui.cardMargin(this));

        setPage("设置", "翻译与阅读的全部选项都在这里", body, true);
        setTopAction("API", this::openApiProfiles);
        setFooter(null);
    }

    // ---------------- 翻译服务 ----------------

    private View apiCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, "翻译服务"));
        ApiProfile api = store.getActiveApi();
        if (api == null) {
            card.addView(Ui.text(this, "未配置", 17, Ui.DANGER, true));
            card.addView(Ui.hint(this, "还没有可用的翻译接口。点击这里添加 DeepSeek 或其他 OpenAI 兼容接口。"));
        } else {
            boolean hasKey = api.apiKey != null && !api.apiKey.isEmpty();
            boolean needsKey = needsKey(api);
            LinearLayout head = Ui.horizontal(this);
            head.addView(Ui.text(this, api.name, 17, Ui.TEXT, true),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            String badgeText = !needsKey ? "本地接口" : (hasKey ? "Key 已保存" : "缺少 Key");
            head.addView(Ui.badge(this, badgeText,
                    !needsKey || hasKey ? 0xFFE7F6F3 : 0xFFFDE8E8,
                    !needsKey || hasKey ? Ui.ACCENT_DARK : Ui.DANGER));
            card.addView(head);

            String model = api.model == null || api.model.isEmpty() ? "未填写模型" : api.model;
            TextView modelView = Ui.text(this, "模型 · " + model, 13, Ui.MUTED, false);
            modelView.setPadding(0, dp(5), 0, 0);
            card.addView(modelView);

            LinearLayout meta = Ui.horizontal(this);
            meta.setPadding(0, dp(10), 0, 0);
            meta.addView(Ui.badge(this, api.tpmLabel(), 0xFFF5F0E7, Ui.MUTED));
            meta.addView(Ui.badge(this, api.outputLabel(), 0xFFF5F0E7, Ui.MUTED), gapLp(6));
            if (api.isDeepSeek()) {
                meta.addView(Ui.badge(this, "思考 " + api.thinkingLabel(), 0xFFF3EEFF, Ui.PURPLE), gapLp(6));
            }
            card.addView(meta);
        }

        LinearLayout manage = Ui.settingRow(this, "管理 API 配置", "添加、切换或删除接口与 Key", Ui.chevron(this));
        manage.setOnClickListener(v -> openApiProfiles());
        card.addView(manage);
        card.setOnClickListener(v -> openApiProfiles());
        return card;
    }

    private void openApiProfiles() {
        startActivity(new Intent(this, ApiProfilesActivity.class));
    }

    private boolean needsKey(ApiProfile api) {
        if (api == null) return false;
        if (api.apiKey != null && !api.apiKey.trim().isEmpty()) return false;
        String base = api.baseUrl == null ? "" : api.baseUrl.toLowerCase(Locale.US);
        if ("ollama".equals(api.providerId)) return false;
        return !(base.contains("127.0.0.1") || base.contains("localhost")
                || base.contains("192.168.") || base.contains("10.") || base.contains("172.16."));
    }

    // ---------------- 翻译设置 ----------------

    private View translationCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, "翻译设置"));

        card.addView(navRow("原文语种", "自动识别，或手动指定原文语言",
                Lang.name(store.getSourceLang()), this::pickSourceLang));
        card.addView(Ui.divider(this));
        card.addView(navRow("译文语种", "翻译成哪种语言",
                Lang.name(store.getTargetLang()), this::pickTargetLang));
        card.addView(Ui.divider(this));

        PromptProfile prompt = store.getActivePrompt();
        card.addView(navRow("提示词 / 翻译质量", "点击选择内置档位或自定义提示词",
                prompt == null ? "默认提示词" : prompt.name,
                () -> startActivity(new Intent(this, PromptProfilesActivity.class))));
        card.addView(Ui.divider(this));

        Switch auto = Ui.toggle(this, store.isAutoTranslate());
        auto.setOnCheckedChangeListener((buttonView, checked) -> store.setAutoTranslate(checked));
        card.addView(Ui.settingRow(this, "自动翻译", "翻页后自动翻译当前页", auto));
        card.addView(Ui.divider(this));

        Switch punctuation = Ui.toggle(this, store.isPunctuationPreserve());
        punctuation.setOnCheckedChangeListener((buttonView, checked) -> store.setPunctuationPreserve(checked));
        card.addView(Ui.settingRow(this, "保留原文标点", "尽量维持原文的标点与格式", punctuation));
        card.addView(Ui.divider(this));

        List<Glossary> glossaries = store.getGlossaries();
        int enabled = 0;
        for (Glossary glossary : glossaries) {
            if (glossary.enabled) enabled++;
        }
        String glossaryValue;
        if (glossaries.isEmpty()) {
            glossaryValue = "未添加";
        } else {
            glossaryValue = glossaries.size() + " 组 · " + enabled + " 组启用";
        }
        card.addView(navRow("术语表", "统一人名、地名与专有名词译法", glossaryValue,
                () -> startActivity(new Intent(this, GlossaryListActivity.class))));
        return card;
    }

    private void pickSourceLang() {
        String[] labels = Lang.sourceLabels();
        String[] codes = Lang.sourceCodes();
        Ui.listDialog(this, "原文语种", labels, Lang.sourceIndex(store.getSourceLang()), index -> {
            store.setSourceLang(codes[index]);
            render();
        });
    }

    private void pickTargetLang() {
        String[] labels = Lang.targetLabels();
        String[] codes = Lang.targetCodes();
        Ui.listDialog(this, "译文语种", labels, Lang.targetIndex(store.getTargetLang()), index -> {
            store.setTargetLang(codes[index]);
            render();
        });
    }

    // ---------------- 阅读设置 ----------------

    private View readingCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, "阅读设置"));

        card.addView(navRow("阅读模式",
                store.isBilingualReading() ? "双语对照：原文在上、译文在下，译文流式回填" : "原文模式：整页原文分页阅读",
                store.isBilingualReading() ? "双语对照" : "原文模式",
                this::pickReadingMode));
        card.addView(Ui.divider(this));

        // 字号 14 - 34
        LinearLayout fontHead = Ui.horizontal(this);
        fontHead.addView(Ui.text(this, "字号", 14.5f, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView fontSizeValue = Ui.valueText(this, store.getFontSize() + " sp", Ui.PRIMARY);
        fontHead.addView(fontSizeValue);
        card.addView(fontHead);

        SeekBar fontBar = Ui.seekBar(this, 34, store.getFontSize());
        fontBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 14) {
                    seekBar.setProgress(14);
                    return;
                }
                fontSizeValue.setText(progress + " sp");
                store.setFontSize(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        card.addView(fontBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 字体三选
        card.addView(Ui.label(this, "字体"));
        LinearLayout familyRow = Ui.horizontal(this);
        final TextView[] familyChips = new TextView[FAMILIES.length];
        for (int i = 0; i < FAMILIES.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(this, FAMILY_LABELS[i]);
            familyChips[i] = chip;
            Ui.chipSelected(chip, FAMILIES[i].equals(store.getFontFamily()));
            chip.setOnClickListener(v -> {
                store.setFontFamily(FAMILIES[index]);
                for (int j = 0; j < familyChips.length; j++) {
                    Ui.chipSelected(familyChips[j], j == index);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) p.setMargins(dp(6), 0, 0, 0);
            familyRow.addView(chip, p);
        }
        card.addView(familyRow);

        // 文字颜色 / 背景颜色
        card.addView(colorRow("文字颜色", "预设色板，或输入自定义 hex",
                store.getTextColor(), () -> pickColor("文字颜色", store.getTextColor(),
                        TEXT_PRESETS, color -> {
                            store.setTextColor(color);
                            render();
                        })));
        card.addView(Ui.divider(this));
        card.addView(colorRow("背景颜色", "纸黄、纯白、护眼绿或夜间深色",
                store.getBgColor(), () -> pickColor("背景颜色", store.getBgColor(),
                        BG_PRESETS, color -> {
                            store.setBgColor(color);
                            if (isDark(color) && !isDark(store.getTextColor())) {
                                store.setTextColor(0xFFEDE9E3);
                            } else if (!isDark(color) && isDark(store.getTextColor())) {
                                store.setTextColor(0xFF2B2118);
                            }
                            render();
                        })));
        card.addView(Ui.divider(this));

        // 行距 20 - 52 -> 1.0 - 2.6（0.05 步进）
        LinearLayout lineHead = Ui.horizontal(this);
        lineHead.addView(Ui.text(this, "行距", 14.5f, Ui.TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView lineSpacingValue = Ui.valueText(this,
                String.format(Locale.US, "%.2f 倍", store.getLineSpacing()), Ui.PRIMARY);
        lineHead.addView(lineSpacingValue);
        card.addView(lineHead);

        SeekBar lineBar = Ui.seekBar(this, 52, lineProgress(store.getLineSpacing()));
        lineBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 20) {
                    seekBar.setProgress(20);
                    return;
                }
                if (progress > 52) {
                    seekBar.setProgress(52);
                    return;
                }
                float value = 1.0f + (progress - 20) * 0.05f;
                value = Math.round(value * 100f) / 100f;
                lineSpacingValue.setText(String.format(Locale.US, "%.2f 倍", value));
                store.setLineSpacing(value);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        card.addView(lineBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Switch paragraph = Ui.toggle(this, store.isParagraphSpacing());
        paragraph.setOnCheckedChangeListener((buttonView, checked) -> store.setParagraphSpacing(checked));
        card.addView(Ui.settingRow(this, "段间距", "段落之间留出空白", paragraph));
        card.addView(Ui.divider(this));

        Switch indent = Ui.toggle(this, store.isIndent());
        indent.setOnCheckedChangeListener((buttonView, checked) -> store.setIndent(checked));
        card.addView(Ui.settingRow(this, "首行缩进", "每段开头缩进两个字符", indent));

        // 翻页方式
        card.addView(Ui.label(this, "翻页方式"));
        LinearLayout turnRow = Ui.horizontal(this);
        final TextView[] turnChips = new TextView[TURN_MODES.length];
        for (int i = 0; i < TURN_MODES.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(this, TURN_LABELS[i]);
            turnChips[i] = chip;
            Ui.chipSelected(chip, TURN_MODES[i].equals(store.getPageTurnMode()));
            chip.setOnClickListener(v -> {
                store.setPageTurnMode(TURN_MODES[index]);
                for (int j = 0; j < turnChips.length; j++) {
                    Ui.chipSelected(turnChips[j], j == index);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) p.setMargins(dp(6), 0, 0, 0);
            turnRow.addView(chip, p);
        }
        card.addView(turnRow);
        return card;
    }

    private int lineProgress(float value) {
        int progress = 20 + Math.round((value - 1.0f) / 0.05f);
        return Math.max(20, Math.min(52, progress));
    }

    private void pickReadingMode() {
        String[] options = {"原文模式", "双语对照（原文在上 · 译文在下）"};
        int selected = store.isBilingualReading() ? 1 : 0;
        Ui.listDialog(this, "阅读模式", options, selected, index -> {
            store.setReadingMode(index == 1 ? "bilingual" : "original");
            render();
        });
    }

    private boolean isDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance < 0.45;
    }

    private LinearLayout colorRow(String title, String subtitle, int color, Runnable action) {
        LinearLayout right = Ui.horizontal(this);
        View dot = new View(this);
        dot.setBackground(Ui.roundStroke(this, color, 99, Ui.BORDER, 1));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        dotLp.setMargins(0, 0, dp(8), 0);
        right.addView(dot, dotLp);
        right.addView(Ui.valueText(this, Ui.colorHex(color), Ui.MUTED));
        LinearLayout row = Ui.settingRow(this, title, subtitle, right);
        row.addView(Ui.chevron(this));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    /** 预设色板 + 自定义 hex 输入的颜色选择对话框。 */
    private void pickColor(String title, int current, int[] presets, ColorPick onPick) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = Ui.vertical(this);
        box.setPadding(dp(20), dp(18), dp(20), dp(16));
        box.setBackground(Ui.round(this, Ui.SURFACE, 22));

        box.addView(Ui.text(this, title, 17, Ui.TEXT, true));
        TextView subtitle = Ui.text(this, "当前 " + Ui.colorHex(current), 12, Ui.MUTED, false);
        subtitle.setPadding(0, dp(4), 0, 0);
        box.addView(subtitle);

        LinearLayout swatches = Ui.horizontal(this);
        swatches.setPadding(0, dp(14), 0, 0);
        for (int i = 0; i < presets.length; i++) {
            final int color = presets[i];
            boolean selected = (color & 0x00FFFFFF) == (current & 0x00FFFFFF);
            View dot = new View(this);
            dot.setBackground(Ui.roundStroke(this, color, 99,
                    selected ? Ui.PRIMARY : Ui.BORDER, selected ? 2 : 1));
            dot.setClickable(true);
            dot.setContentDescription(Ui.colorHex(color));
            dot.setOnClickListener(v -> {
                dialog.dismiss();
                onPick.onPick(color);
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46), dp(46));
            if (i > 0) p.setMargins(dp(10), 0, 0, 0);
            swatches.addView(dot, p);
        }
        box.addView(swatches);

        box.addView(Ui.label(this, "自定义颜色（#RRGGBB）"));
        final EditText hex = Ui.input(this, "#RRGGBB");
        hex.setText(Ui.colorHex(current));
        box.addView(hex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = Ui.horizontal(this);
        buttons.setPadding(0, dp(16), 0, 0);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        buttons.addView(Ui.ghostButton(this, "取消", dialog::dismiss), cancelLp);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        applyLp.setMargins(dp(10), 0, 0, 0);
        buttons.addView(Ui.primaryButton(this, "使用此颜色", () -> {
            String raw = hex.getText().toString().trim();
            String normalized = raw.startsWith("#") ? raw : "#" + raw;
            if (!normalized.matches("#[0-9a-fA-F]{6}") && !normalized.matches("#[0-9a-fA-F]{8}")) {
                Ui.toast(this, "请输入 #RRGGBB 格式的颜色值");
                return;
            }
            int parsed = Ui.parseColor(normalized, current);
            dialog.dismiss();
            onPick.onPick(parsed);
        }), applyLp);
        box.addView(buttons);

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        dialog.show();
        if (window != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.88f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // ---------------- 关于 ----------------

    private View aboutCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, "关于"));

        card.addView(Ui.settingRow(this, "版本", null, Ui.valueText(this, "1.0.0", Ui.MUTED)));
        card.addView(Ui.divider(this));

        LinearLayout bookmarks = Ui.settingRow(this, "书签", "查看全部阅读书签", Ui.chevron(this));
        bookmarks.setOnClickListener(v -> startActivity(new Intent(this, BookmarksActivity.class)));
        card.addView(bookmarks);
        card.addView(Ui.divider(this));

        card.addView(Ui.label(this, "API Key 说明"));
        card.addView(Ui.hint(this,
                "API Key 只保存在本机，使用 Android Keystore 中的 AES-256-GCM 密钥加密后写入本地存储，"
                        + "不会上传到任何服务器。翻译请求只会发送到你配置的接口地址。"
                        + "卸载应用后，Key 与全部本地数据会一并清除。"));
        card.addView(Ui.divider(this));

        LinearLayout doc = Ui.settingRow(this, "DeepSeek 思考模式文档",
                "在浏览器中查看官方说明", Ui.chevron(this));
        doc.setOnClickListener(v -> Ui.openUrl(this, THINKING_DOC));
        card.addView(doc);
        card.addView(Ui.hint(this,
                "提示：DeepSeek 的思考模式会先输出推理过程，翻译更稳但更慢、更耗 token，"
                        + "可在接口配置里开关。"));
        return card;
    }

    // ---------------- 通用小工具 ----------------

    private LinearLayout navRow(String title, String subtitle, String value, Runnable action) {
        LinearLayout row = Ui.settingRow(this, title, subtitle, Ui.valueText(this, value, Ui.MUTED));
        row.addView(Ui.chevron(this));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private LinearLayout.LayoutParams gapLp(int leftDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(leftDp), 0, 0, 0);
        return p;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
