package com.yidu.novel;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.ProviderPreset;
import com.yidu.novel.util.Ui;

import java.util.List;
import java.util.UUID;

/**
 * API 接口配置列表页：展示多套接口、启用当前接口、编辑 / 复制 / 删除，以及按服务商预设新增。
 */
public class ApiProfilesActivity extends BaseActivity {
    private static final int REQ_EDIT = 1001;

    private LinearLayout bodyBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bodyBox = Ui.vertical(this);
        setPage("API 接口", "多套模型接口随时切换，翻译时使用当前启用的接口", bodyBox, true);
        setFooter(Ui.primaryButton(this, "＋ 添加 API 接口", this::showPresetPicker));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT && resultCode == RESULT_OK) render();
    }

    // ---------------- 列表 ----------------

    private void render() {
        if (bodyBox == null) return;
        bodyBox.removeAllViews();
        List<ApiProfile> profiles = store.getApis();
        ApiProfile active = store.getActiveApi();
        if (profiles.isEmpty()) {
            bodyBox.addView(emptyCard(), Ui.cardMargin(this));
            bodyBox.addView(Ui.hint(this, "可选预设：硅基流动、A6API 中转站、DeepSeek 官方、OpenAI、Anthropic Claude、"
                    + "Gemini、Kimi、GLM、Qwen、火山方舟、百度千帆、腾讯混元、MiniMax、OpenRouter、Groq、Ollama 与自定义 API。"));
            return;
        }
        bodyBox.addView(Ui.section(this, "接口配置 · " + profiles.size() + " 个"));
        for (ApiProfile profile : profiles) {
            boolean isActive = active != null && active.id.equals(profile.id);
            bodyBox.addView(profileCard(profile, isActive), Ui.cardMargin(this));
        }
        bodyBox.addView(Ui.hint(this, "提示：点「启用」切换翻译使用的接口；「复制」会复制全部参数（含 Key），方便创建同服务商的备用配置。"));
    }

    private LinearLayout emptyCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "还没有 API 接口", 16, Ui.TEXT, true));
        TextView desc = Ui.text(this, "先添加一个接口并填写 API Key，才能开始翻译。第一次使用推荐硅基流动或 A6API 中转站。",
                13, Ui.MUTED, false);
        desc.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(desc);
        return card;
    }

    private LinearLayout profileCard(ApiProfile profile, boolean isActive) {
        LinearLayout card = Ui.card(this);

        LinearLayout head = Ui.horizontal(this);
        TextView nameView = Ui.text(this, profile.name, 16, Ui.TEXT, true);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        head.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (isActive) {
            head.addView(Ui.badge(this, "当前使用", Ui.alpha(Ui.PRIMARY, 0x1F), Ui.PRIMARY));
        }
        card.addView(head);

        TextView provider = Ui.text(this, profile.providerName(), 12, Ui.MUTED, false);
        provider.setPadding(0, Ui.dp(this, 4), 0, 0);
        card.addView(provider);

        LinearLayout modelRow = Ui.horizontal(this);
        modelRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        modelRow.addView(Ui.badge(this, profile.protocolLabel(), Ui.alpha(Ui.ACCENT, 0x1A), Ui.ACCENT_DARK));
        String modelText = profile.model == null || profile.model.trim().isEmpty()
                ? "未填写模型" : profile.model.trim();
        TextView modelView = Ui.text(this, modelText, 13, Ui.TEXT, false);
        modelView.setSingleLine(true);
        modelView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mp.leftMargin = Ui.dp(this, 8);
        modelRow.addView(modelView, mp);
        card.addView(modelRow);

        LinearLayout meta = Ui.horizontal(this);
        meta.setPadding(0, Ui.dp(this, 8), 0, 0);
        meta.addView(Ui.badge(this, profile.tpmLabel(), Ui.SURFACE_ALT, Ui.MUTED));
        addBadge(meta, profile.outputLabel(), Ui.SURFACE_ALT, Ui.MUTED, Ui.dp(this, 6));
        boolean thinkingOn = profile.thinkingEnabled;
        addBadge(meta, "思考 " + profile.thinkingLabel(),
                thinkingOn ? Ui.alpha(Ui.PURPLE, 0x1F) : Ui.SURFACE_ALT,
                thinkingOn ? Ui.PURPLE : Ui.MUTED, Ui.dp(this, 6));
        card.addView(meta);

        TextView keyView = Ui.text(this, (profile.apiKey == null || profile.apiKey.trim().isEmpty() ? "未填写 Key" : "Key " + profile.maskedKey()), 11.5f, Ui.FAINT, false);
        keyView.setTypeface(Typeface.MONOSPACE);
        keyView.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(keyView);

        View divider = Ui.divider(this);
        LinearLayout.LayoutParams dlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.7f)));
        dlp.topMargin = Ui.dp(this, 12);
        card.addView(divider, dlp);

        LinearLayout actions = Ui.horizontal(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        if (!isActive) {
            addAction(actions, Ui.smallButton(this, "启用", Ui.PRIMARY, () -> enable(profile)));
        }
        addAction(actions, Ui.smallButton(this, "编辑", Ui.ACCENT, () -> openEdit(profile.id)));
        addAction(actions, Ui.smallButton(this, "复制", Ui.TEXT, () -> duplicate(profile)));
        addAction(actions, Ui.smallButton(this, "删除", Ui.DANGER, () -> confirmDelete(profile)));
        card.addView(actions);
        return card;
    }

    private void addBadge(LinearLayout row, String text, int bgColor, int textColor, int leftMargin) {
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(leftMargin, 0, 0, 0);
        row.addView(Ui.badge(this, text, bgColor, textColor), p);
    }

    private void addAction(LinearLayout row, TextView button) {
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, Ui.dp(this, 8), 0);
        row.addView(button, p);
    }

    // ---------------- 操作 ----------------

    private void enable(ApiProfile profile) {
        store.setActiveApi(profile.id);
        render();
        Ui.toast(this, "已启用「" + profile.name + "」");
    }

    private void openEdit(String apiId) {
        Intent intent = new Intent(this, ApiEditActivity.class);
        intent.putExtra("api_id", apiId);
        startActivityForResult(intent, REQ_EDIT);
    }

    private void duplicate(ApiProfile profile) {
        ApiProfile copy = profile.copy();
        copy.id = UUID.randomUUID().toString();
        copy.name = profile.name + " 副本";
        store.saveApi(copy);
        render();
        Ui.toast(this, "已复制为「" + copy.name + "」");
    }

    private void confirmDelete(ApiProfile profile) {
        Ui.confirm(this, "删除接口", "确定删除「" + profile.name + "」吗？删除后不可恢复。", "删除", () -> {
            store.deleteApi(profile.id);
            render();
            Ui.toast(this, "已删除「" + profile.name + "」");
        });
    }

    // ---------------- 添加 ----------------

    private void showPresetPicker() {
        List<ProviderPreset> presets = ProviderPreset.all();
        String[] items = new String[presets.size()];
        for (int i = 0; i < presets.size(); i++) {
            ProviderPreset preset = presets.get(i);
            items[i] = preset.name + "  ·  " + preset.vendor;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择服务商预设")
                .setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    startCreate(presets.get(which));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startCreate(ProviderPreset preset) {
        // 先按预设构造一份新配置（id 新生成），编辑页确认后再持久化。
        ApiProfile draft = new ApiProfile();
        draft.providerId = preset.id;
        draft.name = preset.name;
        draft.protocol = preset.protocol;
        draft.baseUrl = preset.baseUrl;
        draft.endpointPath = preset.endpointPath;
        draft.model = preset.model;
        Intent intent = new Intent(this, ApiEditActivity.class);
        intent.putExtra("preset_id", preset.id);
        intent.putExtra("draft_id", draft.id);
        startActivityForResult(intent, REQ_EDIT);
    }
}
