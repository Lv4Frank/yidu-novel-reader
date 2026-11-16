package com.yidu.novel;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.yidu.novel.model.Glossary;
import com.yidu.novel.util.Ui;

/** 术语表编辑：名称、启用开关、按行“原文=译文”的术语文本，保存时解析。 */
public class GlossaryEditActivity extends BaseActivity {
    private Glossary glossary;
    private boolean isNew;

    private EditText nameInput;
    private EditText termsInput;
    private TextView countView;
    private Switch enableSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String id = getIntent() == null ? null : getIntent().getStringExtra("glossary_id");
        glossary = (id == null || id.isEmpty()) ? null : store.getGlossary(id);
        isNew = glossary == null;
        if (glossary == null) {
            glossary = new Glossary();
            if (id != null && !id.isEmpty()) glossary.id = id;
            glossary.name = "";
            glossary.enabled = true;
        }
        buildUi();
    }

    private void buildUi() {
        LinearLayout body = Ui.vertical(this);
        body.setPadding(dp(16), dp(12), dp(16), dp(14));

        body.addView(Ui.label(this, "名称"));
        nameInput = Ui.input(this, "例如：斗罗大陆 · 人名对照");
        nameInput.setText(glossary.name);
        body.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        enableSwitch = Ui.toggle(this, glossary.enabled);
        body.addView(Ui.settingRow(this, "启用该术语表", "启用后会附加到翻译提示词", enableSwitch));

        LinearLayout termsHead = Ui.horizontal(this);
        termsHead.addView(Ui.text(this, "术语（每行一条：原文=译文）", 12, Ui.MUTED, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        countView = Ui.valueText(this, "", Ui.PRIMARY);
        termsHead.addView(countView);
        body.addView(termsHead);

        termsInput = Ui.multiline(this, "原文=译文\n例如：\n哈利·波特=Harry Potter");
        termsInput.setMinLines(8);
        if (!isNew) termsInput.setText(glossary.toText());
        body.addView(termsInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        body.addView(Ui.hint(this,
                "支持“原文=译文”“原文<TAB>译文”“原文,译文”三种写法；# 开头的行会被忽略；最多 500 条。"));

        termsInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateCount();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateCount();

        setPage(isNew ? "新建术语表" : "编辑术语表",
                isNew ? "按行填写术语，保存后即可用于翻译"
                        : "共 " + glossary.terms.size() + " 条术语",
                body, false);

        setTopAction(isNew ? null : "删除", this::confirmDelete);

        TextView save = Ui.primaryButton(this, "保存", this::save);
        save.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setFooter(save);
    }

    private void updateCount() {
        if (countView == null || termsInput == null) return;
        Glossary scratch = new Glossary();
        scratch.parseText(termsInput.getText().toString());
        countView.setText("已识别 " + scratch.terms.size() + " 条");
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Ui.toast(this, "请填写术语表名称");
            nameInput.requestFocus();
            return;
        }
        glossary.parseText(termsInput.getText().toString());
        if (glossary.terms.isEmpty()) {
            Ui.toast(this, "至少填写一条“原文=译文”术语");
            termsInput.requestFocus();
            return;
        }
        glossary.name = name;
        glossary.enabled = enableSwitch.isChecked();
        store.saveGlossary(glossary);
        Ui.hideKeyboard(termsInput);
        Ui.toast(this, "已保存 " + glossary.terms.size() + " 条术语");
        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        Ui.confirm(this, "删除术语表",
                "确定删除“" + glossary.name + "”？此操作不可恢复。",
                "删除", () -> {
                    store.deleteGlossary(glossary.id);
                    Ui.toast(this, "已删除术语表");
                    setResult(RESULT_OK);
                    finish();
                });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
