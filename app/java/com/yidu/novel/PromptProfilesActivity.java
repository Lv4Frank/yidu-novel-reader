package com.yidu.novel;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.util.Ui;

import java.util.List;

/** 提示词管理：内置三档 + 自定义，可设为当前、编辑、复制、删除与新建。 */
public class PromptProfilesActivity extends BaseActivity {
    private boolean firstResume = true;

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
        // 从编辑页返回后刷新列表与“使用中”标记。
        render();
    }

    private void render() {
        List<PromptProfile> prompts = store.getPrompts();
        PromptProfile active = store.getActivePrompt();

        LinearLayout body = Ui.vertical(this);
        body.addView(Ui.hint(this,
                "提示词决定翻译风格与质量。内置三档可随时恢复默认；自定义提示词支持 "
                        + "{source_lang}、{target_lang}、{chapter}、{glossary} 变量。"));
        for (PromptProfile prompt : prompts) {
            body.addView(promptCard(prompt, active != null && prompt.id.equals(active.id)));
        }
        if (prompts.isEmpty()) {
            body.addView(emptyCard());
        }

        String subtitle;
        if (active == null) {
            subtitle = "共 " + prompts.size() + " 组";
        } else {
            subtitle = "共 " + prompts.size() + " 组 · 当前：" + active.name;
        }
        setPage("提示词", subtitle, body, true);
        setTopAction(null, null);

        TextView add = Ui.primaryButton(this, "＋ 新建提示词", this::newPrompt);
        add.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setFooter(add);
    }

    private View promptCard(PromptProfile prompt, boolean active) {
        LinearLayout card = Ui.card(this);

        LinearLayout head = Ui.horizontal(this);
        head.addView(Ui.text(this, prompt.name, 16, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int qualityColor = prompt.qualityColor();
        head.addView(Ui.badge(this, prompt.qualityName(),
                Ui.alpha(qualityColor, 0x22), qualityColor));
        if (active) {
            head.addView(Ui.badge(this, "使用中", Ui.PRIMARY, Color.WHITE), gapLp(6));
        }
        card.addView(head);

        TextView meta = Ui.text(this, prompt.builtIn ? "系统内置 · 可恢复默认" : "自定义提示词",
                11.5f, Ui.FAINT, false);
        meta.setPadding(0, dp(4), 0, 0);
        card.addView(meta);

        TextView preview = Ui.text(this, previewOf(prompt.content), 12.5f, Ui.MUTED, false);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setLineSpacing(dp(2), 1.0f);
        preview.setPadding(0, dp(8), 0, 0);
        card.addView(preview);

        LinearLayout actions = Ui.horizontal(this);
        actions.setPadding(0, dp(10), 0, 0);
        boolean firstAction = true;
        if (!active) {
            actions.addView(Ui.smallButton(this, "设为当前", Ui.PRIMARY, () -> {
                store.setActivePrompt(prompt.id);
                Ui.toast(this, "已设为当前提示词");
                render();
            }));
            firstAction = false;
        }
        actions.addView(Ui.smallButton(this, "编辑", Ui.TEXT, () -> editPrompt(prompt.id)),
                actionLp(firstAction));
        actions.addView(Ui.smallButton(this, "复制", Ui.ACCENT_DARK, () -> duplicate(prompt)),
                actionLp(false));
        if (!prompt.builtIn) {
            actions.addView(Ui.smallButton(this, "删除", Ui.DANGER, () -> confirmDelete(prompt)),
                    actionLp(false));
        }
        card.addView(actions);
        return card;
    }

    private View emptyCard() {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(16), dp(28), dp(16), dp(28));
        TextView title = Ui.text(this, "还没有提示词", 15, Ui.TEXT, true);
        title.setGravity(android.view.Gravity.CENTER);
        card.addView(title);
        TextView hint = Ui.hint(this, "点击下方按钮新建一组自定义提示词。");
        hint.setGravity(android.view.Gravity.CENTER);
        card.addView(hint);
        return card;
    }

    private String previewOf(String content) {
        if (content == null || content.trim().isEmpty()) return "（内容为空）";
        String[] lines = content.replace("\r", "").split("\n");
        StringBuilder sb = new StringBuilder();
        int taken = 0;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
            taken++;
            if (taken >= 2) break;
        }
        return Ui.ellipsize(sb.toString(), 100);
    }

    private void newPrompt() {
        // 不传 prompt_id，由编辑页生成新的自定义提示词。
        startActivity(new Intent(this, PromptEditActivity.class));
    }

    private void editPrompt(String id) {
        Intent intent = new Intent(this, PromptEditActivity.class);
        intent.putExtra("prompt_id", id);
        startActivity(intent);
    }

    private void duplicate(PromptProfile source) {
        PromptProfile copy = new PromptProfile();
        copy.name = source.name + " 副本";
        copy.quality = source.quality;
        copy.content = source.content;
        copy.builtIn = false;
        copy.builtInKey = "";
        store.savePrompt(copy);
        Ui.toast(this, "已复制为自定义提示词");
        render();
    }

    private void confirmDelete(PromptProfile prompt) {
        Ui.confirm(this, "删除提示词",
                "确定删除“" + prompt.name + "”？删除后不可恢复。",
                "删除", () -> {
                    store.deletePrompt(prompt.id);
                    Ui.toast(this, "已删除提示词");
                    render();
                });
    }

    private LinearLayout.LayoutParams actionLp(boolean first) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!first) p.setMargins(dp(6), 0, 0, 0);
        return p;
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
