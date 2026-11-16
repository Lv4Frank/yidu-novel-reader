package com.yidu.novel;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.yidu.novel.model.Glossary;
import com.yidu.novel.util.Ui;

import java.util.List;

/** 术语表管理：多组术语表，可启用、编辑、删除与新建。 */
public class GlossaryListActivity extends BaseActivity {
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
        // 从编辑页返回后刷新条数与名称。
        render();
    }

    private void render() {
        List<Glossary> glossaries = store.getGlossaries();
        LinearLayout body = Ui.vertical(this);
        body.addView(Ui.hint(this,
                "术语表用来固定人名、地名、专有名词的译法。启用中的术语表会附加到翻译提示词中。"));
        if (glossaries.isEmpty()) {
            body.addView(emptyCard());
        } else {
            for (Glossary glossary : glossaries) {
                body.addView(glossaryCard(glossary));
            }
        }

        setPage("术语表", summaryOf(glossaries), body, true);
        setTopAction(null, null);

        TextView add = Ui.primaryButton(this, "＋ 新建术语表", this::newGlossary);
        add.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setFooter(add);
    }

    private View glossaryCard(Glossary glossary) {
        LinearLayout card = Ui.card(this);

        LinearLayout head = Ui.horizontal(this);
        head.addView(Ui.text(this, glossary.name, 16, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = Ui.toggle(this, glossary.enabled);
        head.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(head);

        TextView summary = Ui.text(this, enabledLabel(glossary.terms.size(), glossary.enabled),
                12, Ui.MUTED, false);
        summary.setPadding(0, dp(4), 0, 0);
        card.addView(summary);

        toggle.setOnCheckedChangeListener((buttonView, checked) -> {
            store.setGlossaryEnabled(glossary.id, checked);
            summary.setText(enabledLabel(glossary.terms.size(), checked));
            setSubtitle(summaryOf(store.getGlossaries()));
            Ui.toast(this, checked ? "已启用术语表" : "已停用术语表");
        });

        LinearLayout actions = Ui.horizontal(this);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(Ui.smallButton(this, "编辑", Ui.PRIMARY, () -> openEditor(glossary.id)));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteLp.setMargins(dp(6), 0, 0, 0);
        actions.addView(Ui.smallButton(this, "删除", Ui.DANGER, () -> confirmDelete(glossary)), deleteLp);
        card.addView(actions);

        card.setOnClickListener(v -> openEditor(glossary.id));
        return card;
    }

    private View emptyCard() {
        LinearLayout card = Ui.card(this);
        card.setPadding(dp(16), dp(28), dp(16), dp(28));
        TextView title = Ui.text(this, "还没有术语表", 15, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView hint = Ui.hint(this, "新建一组“原文=译文”对照，翻译时会自动用上。");
        hint.setGravity(Gravity.CENTER);
        card.addView(hint);
        return card;
    }

    private String enabledLabel(int count, boolean enabled) {
        return count + " 条术语 · " + (enabled ? "已启用" : "已停用");
    }

    private String summaryOf(List<Glossary> glossaries) {
        if (glossaries.isEmpty()) return "未添加术语表";
        int enabled = 0;
        for (Glossary glossary : glossaries) {
            if (glossary.enabled) enabled++;
        }
        return glossaries.size() + " 组 · " + enabled + " 组启用";
    }

    private void newGlossary() {
        startActivity(new Intent(this, GlossaryEditActivity.class));
    }

    private void openEditor(String id) {
        Intent intent = new Intent(this, GlossaryEditActivity.class);
        intent.putExtra("glossary_id", id);
        startActivity(intent);
    }

    private void confirmDelete(Glossary glossary) {
        Ui.confirm(this, "删除术语表",
                "确定删除“" + glossary.name + "”？此操作不可恢复。",
                "删除", () -> {
                    store.deleteGlossary(glossary.id);
                    Ui.toast(this, "已删除术语表");
                    render();
                });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
