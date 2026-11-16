package com.yidu.novel;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.util.Defaults;
import com.yidu.novel.util.Ui;

/** 提示词编辑：名称、质量档、大文本内容；内置可恢复默认，自定义可删除。 */
public class PromptEditActivity extends BaseActivity {
    private static final String[] QUALITIES = {"high", "medium", "low", "custom"};
    private static final String[] QUALITY_LABELS = {"高质量", "标准", "快速", "自定义"};

    private PromptProfile prompt;
    private boolean isNew;
    private String selectedQuality = "medium";

    private EditText nameInput;
    private EditText contentInput;
    private final TextView[] qualityChips = new TextView[QUALITIES.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent() == null ? null : getIntent().getStringExtra("prompt_id");
        prompt = (id == null || id.isEmpty()) ? null : store.getPrompt(id);
        isNew = prompt == null;
        if (prompt == null) {
            prompt = new PromptProfile();
            if (id != null && !id.isEmpty()) prompt.id = id;
            prompt.name = "";
            prompt.quality = "medium";
            prompt.content = "";
        }
        selectedQuality = normalizeQuality(prompt.quality);
        buildUi();
    }

    private String normalizeQuality(String quality) {
        for (String value : QUALITIES) {
            if (value.equals(quality)) return value;
        }
        return "custom";
    }

    private void buildUi() {
        LinearLayout body = Ui.vertical(this);
        body.setPadding(dp(16), dp(12), dp(16), dp(14));

        body.addView(Ui.label(this, "名称"));
        nameInput = Ui.input(this, "例如：轻小说 · 口语化译法");
        nameInput.setText(prompt.name);
        body.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body.addView(Ui.label(this, "质量档"));
        LinearLayout qualityRow = Ui.horizontal(this);
        for (int i = 0; i < QUALITIES.length; i++) {
            final int index = i;
            TextView chip = Ui.chip(this, QUALITY_LABELS[i]);
            qualityChips[i] = chip;
            Ui.chipSelected(chip, QUALITIES[i].equals(selectedQuality));
            chip.setOnClickListener(v -> selectQuality(index));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) p.setMargins(dp(6), 0, 0, 0);
            qualityRow.addView(chip, p);
        }
        body.addView(qualityRow);

        body.addView(Ui.hint(this,
                "可用变量：{source_lang} 原文语种 · {target_lang} 译文语种 · "
                        + "{chapter} 当前章节标题 · {glossary} 已启用术语表（发送时自动替换）。"));

        body.addView(Ui.label(this, "提示词内容"));
        contentInput = Ui.multiline(this, "在这里写下你的翻译要求…");
        contentInput.setText(prompt.content);
        contentInput.setMinLines(8);
        body.addView(contentInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        String title;
        String subtitle;
        if (isNew) {
            title = "新建提示词";
            subtitle = "自定义提示词 · " + Defaults.qualityName(selectedQuality);
        } else if (prompt.builtIn) {
            title = "编辑内置提示词";
            subtitle = "系统内置 · " + prompt.qualityName() + " · 可恢复默认";
        } else {
            title = "编辑提示词";
            subtitle = "自定义 · " + prompt.qualityName();
        }
        setPage(title, subtitle, body, false);

        if (prompt.builtIn) {
            setTopAction("恢复默认", this::restoreDefault);
        } else if (isNew) {
            setTopAction(null, null);
        } else {
            setTopAction("删除", this::confirmDelete);
        }

        TextView save = Ui.primaryButton(this, "保存", this::save);
        save.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setFooter(save);
    }

    private void selectQuality(int index) {
        selectedQuality = QUALITIES[index];
        for (int i = 0; i < qualityChips.length; i++) {
            Ui.chipSelected(qualityChips[i], i == index);
        }
        if (isNew) {
            setSubtitle("自定义提示词 · " + Defaults.qualityName(selectedQuality));
        }
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Ui.toast(this, "请填写提示词名称");
            nameInput.requestFocus();
            return;
        }
        String rawContent = contentInput.getText().toString();
        if (rawContent.trim().isEmpty()) {
            Ui.toast(this, "请填写提示词内容");
            contentInput.requestFocus();
            return;
        }
        prompt.name = name;
        prompt.quality = selectedQuality;
        prompt.content = rawContent;
        store.savePrompt(prompt);
        Ui.hideKeyboard(contentInput);
        Ui.toast(this, "已保存提示词");
        setResult(RESULT_OK);
        finish();
    }

    private void restoreDefault() {
        Ui.confirm(this, "恢复默认",
                "将丢弃当前编辑的内容，把提示词恢复为内置默认版本。",
                "恢复", () -> {
                    store.resetPrompt(prompt);
                    prompt.content = Defaults.promptFor(prompt.builtInKey);
                    contentInput.setText(prompt.content);
                    Ui.toast(this, "已恢复默认内容，如需要请点击保存");
                });
    }

    private void confirmDelete() {
        Ui.confirm(this, "删除提示词",
                "确定删除“" + prompt.name + "”？删除后不可恢复。",
                "删除", () -> {
                    store.deletePrompt(prompt.id);
                    Ui.toast(this, "已删除提示词");
                    setResult(RESULT_OK);
                    finish();
                });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
