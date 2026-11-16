package com.yidu.novel;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.ProviderPreset;
import com.yidu.novel.net.LlmClient;
import com.yidu.novel.net.LlmException;
import com.yidu.novel.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * API 接口编辑页：支持按服务商预设新建，以及编辑已有配置（测试连接 / 保存并使用 / 删除）。
 */
public class ApiEditActivity extends BaseActivity {
    private ApiProfile profile;
    private boolean isNew;
    private ProviderPreset preset;

    private String protocol = "openai";
    private boolean thinking = false;
    private String effort = "high";
    private boolean unlimitedOutput = true;
    private int maxOutput = 4096;
    private int tpm = 0;
    private double temperature = 0.3;

    private EditText nameInput;
    private EditText baseUrlInput;
    private EditText endpointInput;
    private EditText modelInput;
    private EditText keyInput;
    private EditText timeoutInput;
    private EditText headersInput;

    private TextView protocolOpenAi;
    private TextView protocolAnthropic;
    private TextView protocolGemini;

    private TextView tempValue;
    private TextView outputValue;
    private TextView tpmValue;

    private Switch thinkingSwitch;
    private LinearLayout effortRow;
    private TextView effortLow;
    private TextView effortHigh;
    private TextView effortMax;

    private TextView testButton;
    private boolean keyVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String apiId = trim(getIntent().getStringExtra("api_id"));
        String presetId = trim(getIntent().getStringExtra("preset_id"));
        if (!apiId.isEmpty()) profile = store.getApi(apiId);
        isNew = profile == null;
        if (profile == null) {
            profile = new ApiProfile();
            String draftId = trim(getIntent().getStringExtra("draft_id"));
            if (!draftId.isEmpty()) profile.id = draftId;
            preset = ProviderPreset.byId(presetId.isEmpty() ? "custom" : presetId);
            applyPreset(profile, preset);
        } else {
            preset = ProviderPreset.byId(profile.providerId);
        }

        protocol = normalizeProtocol(profile.protocol);
        thinking = profile.thinkingEnabled;
        effort = normalizeEffort(profile.thinkingEffort);
        unlimitedOutput = profile.unlimitedOutput;
        maxOutput = profile.maxOutputTokens > 0 ? profile.maxOutputTokens : 4096;
        tpm = Math.max(0, profile.tpm);
        temperature = sanitizeTemperature(profile.temperature);

        if (!isNew) setTopAction("删除", this::confirmDelete);
        buildPage();
    }

    private void applyPreset(ApiProfile target, ProviderPreset source) {
        target.providerId = source.id;
        target.name = source.name;
        target.protocol = source.protocol;
        target.baseUrl = source.baseUrl;
        target.endpointPath = source.endpointPath;
        target.model = source.model;
    }

    // ---------------- 页面搭建 ----------------

    private void buildPage() {
        LinearLayout body = Ui.vertical(this);

        body.addView(Ui.section(this, "基本信息"));
        body.addView(basicCard(), Ui.cardMargin(this));

        body.addView(Ui.section(this, "连接配置"));
        body.addView(connectionCard(), Ui.cardMargin(this));

        body.addView(Ui.section(this, "密钥与性能"));
        body.addView(performanceCard(), Ui.cardMargin(this));

        body.addView(Ui.section(this, "思考模式"));
        body.addView(thinkingCard(), Ui.cardMargin(this));

        body.addView(Ui.section(this, "高级"));
        body.addView(advancedCard(), Ui.cardMargin(this));

        body.addView(Ui.hint(this, "保存后该接口会立即设为「当前使用」；返回列表即可看到「当前使用」徽标。"));

        setPage(isNew ? "添加 API 接口" : "编辑 API 接口", subtitle(), body, true);
        setFooter(footer());
    }

    private LinearLayout basicCard() {
        LinearLayout card = Ui.card(this);

        card.addView(Ui.label(this, "名称"));
        nameInput = Ui.input(this, "给这套接口起个名字");
        nameInput.setText(nullSafe(profile.name));
        card.addView(nameInput);

        card.addView(Ui.label(this, "服务商预设"));
        LinearLayout providerRow = Ui.horizontal(this);
        TextView providerView = Ui.text(this, preset.name + " · " + preset.vendor, 13.5f, Ui.TEXT, false);
        providerView.setSingleLine(true);
        providerView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        providerRow.addView(providerView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!isBlank(preset.consoleUrl)) {
            providerRow.addView(Ui.smallButton(this, "打开控制台", Ui.PRIMARY,
                    () -> Ui.openUrl(this, preset.consoleUrl)));
        }
        LinearLayout.LayoutParams prp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prp.topMargin = Ui.dp(this, 6);
        card.addView(providerRow, prp);
        if (!isBlank(preset.note)) card.addView(Ui.hint(this, preset.note));
        return card;
    }

    private LinearLayout connectionCard() {
        LinearLayout card = Ui.card(this);

        card.addView(Ui.label(this, "协议"));
        LinearLayout protocolRow = Ui.horizontal(this);
        protocolOpenAi = Ui.chip(this, "OpenAI 兼容");
        protocolAnthropic = Ui.chip(this, "Anthropic");
        protocolGemini = Ui.chip(this, "Gemini");
        protocolRow.addView(protocolOpenAi, chipParams(false));
        protocolRow.addView(protocolAnthropic, chipParams(true));
        protocolRow.addView(protocolGemini, chipParams(true));
        protocolOpenAi.setOnClickListener(v -> selectProtocol("openai"));
        protocolAnthropic.setOnClickListener(v -> selectProtocol("anthropic"));
        protocolGemini.setOnClickListener(v -> selectProtocol("gemini"));
        card.addView(protocolRow);

        card.addView(Ui.label(this, "Base URL"));
        baseUrlInput = Ui.input(this, "https://api.example.com/v1");
        baseUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        baseUrlInput.setText(nullSafe(profile.baseUrl));
        card.addView(baseUrlInput);
        card.addView(Ui.hint(this, "只填到版本号，例如 https://api.deepseek.com 或 https://api.openai.com/v1；不要带 /chat/completions。"));

        card.addView(Ui.label(this, "接口路径"));
        endpointInput = Ui.input(this, "/chat/completions");
        endpointInput.setText(nullSafe(profile.endpointPath));
        card.addView(endpointInput);
        card.addView(Ui.hint(this, "OpenAI 兼容与 Anthropic 协议会拼接此路径；Gemini 协议留空即可，App 会按模型名自动拼接。"));

        card.addView(Ui.label(this, "模型"));
        modelInput = Ui.input(this, "模型名，例如 deepseek-chat");
        modelInput.setText(nullSafe(profile.model));
        card.addView(modelInput);
        if (preset.models != null && preset.models.length > 0) {
            card.addView(Ui.hint(this, "常用模型（点击填入）："));
            card.addView(modelChips());
        } else {
            card.addView(Ui.hint(this, "请填写服务商文档中的模型名，需与接口要求的名称完全一致。"));
        }

        selectProtocol(protocol);
        return card;
    }

    private View modelChips() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = Ui.horizontal(this);
        for (String model : preset.models) {
            TextView chip = Ui.chip(this, model);
            chip.setOnClickListener(v -> {
                modelInput.setText(model);
                modelInput.setSelection(modelInput.getText().length());
            });
            row.addView(chip, chipParams(row.getChildCount() > 0));
        }
        scroll.addView(row);
        return scroll;
    }

    private LinearLayout performanceCard() {
        LinearLayout card = Ui.card(this);

        card.addView(Ui.label(this, "API Key"));
        LinearLayout keyRow = Ui.horizontal(this);
        keyInput = Ui.secretInput(this, "sk-...");
        keyInput.setText(nullSafe(profile.apiKey));
        keyInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        keyRow.addView(keyInput);
        TextView toggleKeyButton = Ui.smallButton(this, "显示", Ui.ACCENT, () -> {
        });
        toggleKeyButton.setOnClickListener(v -> {
            keyVisible = !keyVisible;
            toggleKeyButton.setText(keyVisible ? "隐藏" : "显示");
            keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                    | (keyVisible ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            keyInput.setSelection(keyInput.getText().length());
        });
        LinearLayout.LayoutParams kbp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kbp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        keyRow.addView(toggleKeyButton, kbp);
        card.addView(keyRow);
        card.addView(Ui.hint(this, "Key 只保存在本机；列表页只显示首尾各 4 位掩码。"));

        card.addView(Ui.label(this, "温度 temperature"));
        LinearLayout tempRow = Ui.horizontal(this);
        SeekBar bar = Ui.seekBar(this, 200, (int) Math.round(temperature * 100));
        bar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tempValue = Ui.valueText(this, formatTemperature(temperature), Ui.PRIMARY);
        tempValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tempValue.setMinWidth(Ui.dp(this, 54));
        LinearLayout.LayoutParams tvp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvp.setMargins(Ui.dp(this, 10), 0, 0, 0);
        tempRow.addView(bar);
        tempRow.addView(tempValue, tvp);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                temperature = progress / 100.0;
                tempValue.setText(formatTemperature(temperature));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        card.addView(tempRow);
        card.addView(Ui.hint(this, "0 更稳定、2 更发散；思考模式开启时不发送该参数。"));

        card.addView(Ui.label(this, "超时秒数"));
        timeoutInput = Ui.input(this, "180");
        timeoutInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        timeoutInput.setText(String.valueOf(profile.timeoutSec > 0 ? profile.timeoutSec : 180));
        card.addView(timeoutInput);

        outputValue = Ui.valueText(this, outputLabel(), Ui.TEXT);
        card.addView(Ui.settingRow(this, "单次最大输出", "模型一次回复允许的最大 token 数",
                clickableValue(outputValue, this::showOutputPicker)));

        tpmValue = Ui.valueText(this, tpmLabel(), Ui.TEXT);
        card.addView(Ui.settingRow(this, "TPM 限制", "每分钟 token 上限，用于限流",
                clickableValue(tpmValue, this::showTpmPicker)));
        return card;
    }

    private LinearLayout thinkingCard() {
        LinearLayout card = Ui.card(this);

        thinkingSwitch = Ui.toggle(this, thinking);
        card.addView(Ui.settingRow(this, "DeepSeek 思考模式", "开启后模型先推理再回答，响应更慢但更稳", thinkingSwitch));

        effortRow = Ui.horizontal(this);
        effortRow.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 2));
        effortLow = Ui.chip(this, "低 low");
        effortHigh = Ui.chip(this, "高 high");
        effortMax = Ui.chip(this, "最高 max");
        effortRow.addView(effortLow, chipParams(false));
        effortRow.addView(effortHigh, chipParams(true));
        effortRow.addView(effortMax, chipParams(true));
        effortLow.setOnClickListener(v -> selectEffort("low"));
        effortHigh.setOnClickListener(v -> selectEffort("high"));
        effortMax.setOnClickListener(v -> selectEffort("max"));
        card.addView(effortRow);

        card.addView(Ui.hint(this, "按 DeepSeek 官方文档（https://api-docs.deepseek.com/zh-cn/guides/thinking_mode）"
                + "发送 {\"thinking\":{\"type\":\"enabled\"}} 与 reasoning_effort（low / high / max）。"
                + "中转站与自定义接口只要模型本身支持，同样会生效；思考模式开启后不发送 temperature。"));
        card.addView(Ui.hint(this, "若模型不支持思考模式，请保持关闭，否则接口可能返回参数错误。"));

        thinkingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            thinking = isChecked;
            updateThinkingUi();
        });
        updateThinkingUi();
        return card;
    }

    private LinearLayout advancedCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.label(this, "自定义请求头（JSON，可留空）"));
        headersInput = Ui.multiline(this, "{\"HTTP-Referer\":\"https://example.com\"}");
        headersInput.setMinLines(4);
        headersInput.setMaxLines(8);
        headersInput.setText(nullSafe(profile.extraHeaders));
        card.addView(headersInput);
        card.addView(Ui.hint(this, "示例：{\"HTTP-Referer\":\"https://example.com\",\"X-Title\":\"易读翻译\"}。"
                + "仅 OpenRouter 或特殊网关需要时填写，格式错误的请求头会被忽略。"));
        return card;
    }

    private View footer() {
        LinearLayout footer = Ui.horizontal(this);
        testButton = Ui.ghostButton(this, "测试连接", this::testConnection);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.rightMargin = Ui.dp(this, 10);
        footer.addView(testButton, tp);
        TextView saveButton = Ui.primaryButton(this, "保存并使用", this::save);
        footer.addView(saveButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f));
        return footer;
    }

    // ---------------- 表单交互 ----------------

    private void selectProtocol(String value) {
        protocol = normalizeProtocol(value);
        Ui.chipSelected(protocolOpenAi, "openai".equals(protocol));
        Ui.chipSelected(protocolAnthropic, "anthropic".equals(protocol));
        Ui.chipSelected(protocolGemini, "gemini".equals(protocol));
        // 若接口路径还是默认值，切换协议时同步成该协议的默认路径。
        if (endpointInput != null) {
            String current = textOf(endpointInput).trim();
            if (current.isEmpty() || "/chat/completions".equals(current) || "/messages".equals(current)) {
                String next = "openai".equals(protocol) ? "/chat/completions"
                        : ("anthropic".equals(protocol) ? "/messages" : "");
                if (!next.equals(current)) endpointInput.setText(next);
            }
        }
        setSubtitle(subtitle());
    }

    private void selectEffort(String value) {
        effort = normalizeEffort(value);
        applyEffortChips();
    }

    private void applyEffortChips() {
        if (effortLow == null) return;
        Ui.chipSelected(effortLow, "low".equals(effort));
        Ui.chipSelected(effortHigh, "high".equals(effort));
        Ui.chipSelected(effortMax, "max".equals(effort));
    }

    private void updateThinkingUi() {
        if (effortRow == null) return;
        effortRow.setVisibility(thinking ? View.VISIBLE : View.GONE);
        applyEffortChips();
    }

    private View clickableValue(TextView value, Runnable action) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
        row.setBackground(Ui.ripple(Ui.round(this, Ui.SURFACE_ALT, 10), 0x1AB45309));
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        row.addView(value);
        row.addView(Ui.chevron(this));
        return row;
    }

    private void showOutputPicker() {
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        labels.add("不限制");
        values.add(0);
        int[] options = {512, 1024, 2048, 4096, 8192, 16384, 32768, 65536};
        for (int option : options) {
            labels.add(String.valueOf(option));
            values.add(option);
        }
        int selected = 0;
        if (!unlimitedOutput) {
            int index = values.indexOf(maxOutput);
            if (index < 0) {
                labels.add(maxOutput + "（当前）");
                values.add(maxOutput);
                index = labels.size() - 1;
            }
            selected = index;
        }
        Ui.listDialog(this, "单次最大输出 token", labels.toArray(new String[0]), selected, which -> {
            int value = values.get(which);
            if (value <= 0) {
                unlimitedOutput = true;
            } else {
                unlimitedOutput = false;
                maxOutput = value;
            }
            outputValue.setText(outputLabel());
        });
    }

    private void showTpmPicker() {
        final String[] labels = {"不限制", "2k", "5k", "10k", "20k", "50k", "100k", "自定义…"};
        final int[] values = {0, 2000, 5000, 10000, 20000, 50000, 100000, -1};
        int selected = 0;
        if (tpm > 0) {
            selected = labels.length - 1;
            for (int i = 1; i < values.length - 1; i++) {
                if (values[i] == tpm) selected = i;
            }
        }
        Ui.listDialog(this, "TPM 限制（每分钟 token）", labels, selected, which -> {
            if (values[which] == -1) {
                Ui.editDialog(this, "自定义 TPM", tpm > 0 ? String.valueOf(tpm) : "", false,
                        "输入每分钟 token 上限，例如 8000", text -> {
                            tpm = parseInt(text, tpm, 0, 100000000);
                            tpmValue.setText(tpmLabel());
                        });
            } else {
                tpm = values[which];
                tpmValue.setText(tpmLabel());
            }
        });
    }

    // ---------------- 测试连接 ----------------

    private void testConnection() {
        ApiProfile test = collectProfile();
        if (isBlank(test.baseUrl)) {
            Ui.toast(this, "请先填写 Base URL");
            return;
        }
        if (isBlank(test.model)) {
            Ui.toast(this, "请先填写模型名");
            return;
        }
        Ui.hideKeyboard(getCurrentFocus());
        testButton.setEnabled(false);
        testButton.setAlpha(0.6f);
        testButton.setText("测试中…");
        new Thread(() -> {
            boolean success;
            String message;
            try {
                String text = LlmClient.testConnection(test);
                String reply = text == null ? "" : text.trim();
                success = true;
                message = reply.isEmpty() ? "连接成功，接口可用。" : "连接成功，模型返回：" + reply;
            } catch (LlmException e) {
                success = false;
                message = isBlank(e.getMessage()) ? "连接失败" : e.getMessage();
            } catch (Exception e) {
                success = false;
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                message = "连接失败：" + detail;
            }
            final boolean ok = success;
            final String result = message;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                testButton.setEnabled(true);
                testButton.setAlpha(1f);
                testButton.setText("测试连接");
                Ui.showInfo(ApiEditActivity.this, ok ? "连接成功" : "连接失败", result);
            });
        }, "api-test").start();
    }

    // ---------------- 保存 / 删除 ----------------

    private void save() {
        ApiProfile edited = collectProfile();
        if (edited.name.isEmpty()) {
            Ui.toast(this, "请填写接口名称");
            nameInput.requestFocus();
            return;
        }
        if (edited.baseUrl.isEmpty()) {
            Ui.toast(this, "请填写 Base URL");
            baseUrlInput.requestFocus();
            return;
        }
        if (edited.model.isEmpty()) {
            Ui.toast(this, "请填写模型名");
            modelInput.requestFocus();
            return;
        }
        if (!"openai".equals(edited.protocol) && !"anthropic".equals(edited.protocol) && !"gemini".equals(edited.protocol)) {
            edited.protocol = "openai";
        }
        store.saveApi(edited);
        store.setActiveApi(edited.id);
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        if (isNew || profile == null) return;
        Ui.confirm(this, "删除接口", "确定删除「" + profile.name + "」吗？删除后不可恢复。", "删除", () -> {
            store.deleteApi(profile.id);
            setResult(RESULT_OK);
            finish();
        });
    }

    private ApiProfile collectProfile() {
        ApiProfile target = profile == null ? new ApiProfile() : profile;
        target.name = textOf(nameInput).trim();
        target.protocol = normalizeProtocol(protocol);
        target.baseUrl = textOf(baseUrlInput).trim();
        target.endpointPath = textOf(endpointInput).trim();
        target.model = textOf(modelInput).trim();
        target.apiKey = textOf(keyInput).trim();
        target.temperature = temperature;
        target.timeoutSec = parseInt(textOf(timeoutInput), 180, 5, 3600);
        target.unlimitedOutput = unlimitedOutput;
        target.maxOutputTokens = maxOutput;
        target.tpm = tpm;
        target.extraHeaders = textOf(headersInput).trim();
        target.thinkingEnabled = thinking;
        target.thinkingEffort = normalizeEffort(effort);
        target.updatedAt = System.currentTimeMillis();
        return target;
    }

    // ---------------- 工具 ----------------

    private String subtitle() {
        return preset.name + " · " + ProviderPreset.protocolName(protocol);
    }

    private LinearLayout.LayoutParams chipParams(boolean withLeftMargin) {
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (withLeftMargin) p.leftMargin = Ui.dp(this, 8);
        return p;
    }

    private String outputLabel() {
        return unlimitedOutput ? "不限制" : String.valueOf(maxOutput);
    }

    private String tpmLabel() {
        if (tpm <= 0) return "不限制";
        if (tpm % 1000 == 0) return (tpm / 1000) + "k";
        return String.valueOf(tpm);
    }

    private static String formatTemperature(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String normalizeProtocol(String value) {
        if ("anthropic".equals(value) || "gemini".equals(value) || "openai".equals(value)) return value;
        return "openai";
    }

    private static String normalizeEffort(String value) {
        if ("low".equals(value) || "max".equals(value) || "high".equals(value)) return value;
        return "high";
    }

    private static double sanitizeTemperature(double value) {
        if (Double.isNaN(value)) return 0.3;
        return Math.max(0d, Math.min(2d, value));
    }

    private static int parseInt(String raw, int fallback, int min, int max) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return fallback;
        try {
            int value = Integer.parseInt(text);
            if (value < min) value = min;
            if (value > max) value = max;
            return value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String textOf(EditText edit) {
        if (edit == null || edit.getText() == null) return "";
        return edit.getText().toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
