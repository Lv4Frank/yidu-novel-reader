package com.yidu.novel.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class Ui {
    public static final int INK = 0xFF12100E;
    public static final int PRIMARY = 0xFFB45309;
    public static final int PRIMARY_DARK = 0xFF92400E;
    public static final int ACCENT = 0xFF0D9488;
    public static final int ACCENT_DARK = 0xFF0F766E;
    public static final int BG = 0xFFF6F1E7;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int SURFACE_ALT = 0xFFF7F3EB;
    public static final int BORDER = 0xFFE7DFD2;
    public static final int TEXT = 0xFF1C1917;
    public static final int MUTED = 0xFF78716C;
    public static final int FAINT = 0xFFA8A29E;
    public static final int DANGER = 0xFFDC2626;
    public static final int WARNING = 0xFFD97706;
    public static final int PURPLE = 0xFF7C3AED;

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    public static LinearLayout.LayoutParams weighted(int weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    public static void margin(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) raw).setMargins(left, top, right, bottom);
            view.setLayoutParams(raw);
        }
    }

    public static void pad(View view, int left, int top, int right, int bottom) {
        view.setPadding(left, top, right, bottom);
    }

    public static GradientDrawable round(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundStroke(Context context, int color, float radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = round(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, strokeWidthDp)), strokeColor);
        return drawable;
    }

    public static GradientDrawable gradient(Context context, int[] colors, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static Drawable ripple(Drawable content, int color) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static void bg(View view, Drawable drawable) {
        view.setBackground(drawable);
    }

    public static TextView text(Context context, CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLineSpacing(dp(context, 2), 1.0f);
        return view;
    }

    public static TextView title(Context context, CharSequence value) {
        TextView view = text(context, value, 21, TEXT, true);
        view.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        return view;
    }

    public static TextView section(Context context, CharSequence value) {
        TextView view = text(context, value, 12, MUTED, true);
        view.setLetterSpacing(0.06f);
        view.setPadding(dp(context, 2), dp(context, 18), 0, dp(context, 8));
        return view;
    }

    public static TextView label(Context context, CharSequence value) {
        TextView view = text(context, value, 12, MUTED, false);
        view.setPadding(dp(context, 2), dp(context, 4), 0, dp(context, 6));
        return view;
    }

    public static TextView hint(Context context, CharSequence value) {
        TextView view = text(context, value, 11.5f, FAINT, false);
        view.setLineSpacing(dp(context, 2.5f), 1.0f);
        view.setPadding(dp(context, 2), dp(context, 6), dp(context, 2), 0);
        return view;
    }

    public static TextView action(Context context, CharSequence value, Runnable runnable) {
        TextView view = text(context, value, 13.5f, PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 12), dp(context, 9), dp(context, 12), dp(context, 9));
        view.setBackground(ripple(round(context, 0xFFFDF3E7, 10), 0x33B45309));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        return view;
    }

    public static TextView primaryButton(Context context, CharSequence value, Runnable runnable) {
        TextView view = text(context, value, 15, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 50));
        view.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        view.setBackground(ripple(gradient(context, new int[]{0xFFD97706, PRIMARY_DARK}, 14), 0x44FFFFFF));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        view.setElevation(dp(context, 2));
        return view;
    }

    public static TextView accentButton(Context context, CharSequence value, Runnable runnable) {
        TextView view = text(context, value, 15, Color.WHITE, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 50));
        view.setPadding(dp(context, 18), dp(context, 13), dp(context, 18), dp(context, 13));
        view.setBackground(ripple(gradient(context, new int[]{0xFF14B8A6, ACCENT_DARK}, 14), 0x44FFFFFF));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        view.setElevation(dp(context, 2));
        return view;
    }

    public static TextView ghostButton(Context context, CharSequence value, Runnable runnable) {
        TextView view = text(context, value, 14.5f, TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(context, 46));
        view.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        view.setBackground(ripple(roundStroke(context, SURFACE, 13, BORDER, 1), 0x142563EB));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        return view;
    }

    public static TextView smallButton(Context context, CharSequence value, int color, Runnable runnable) {
        TextView view = text(context, value, 12.5f, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        view.setBackground(ripple(round(context, 0xFFF5F0E7, 10), 0x22B45309));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        return view;
    }

    public static TextView iconButton(Context context, CharSequence icon, Runnable runnable) {
        TextView view = text(context, icon, 20, TEXT, false);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ripple(round(context, SURFACE_ALT, 99), 0x1A0F172A));
        view.setClickable(true);
        view.setOnClickListener(v -> runnable.run());
        return view;
    }

    public static TextView chip(Context context, CharSequence value) {
        TextView view = text(context, value, 12.5f, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 12), dp(context, 9), dp(context, 12), dp(context, 9));
        view.setBackground(roundStroke(context, SURFACE, 12, BORDER, 1));
        view.setClickable(true);
        return view;
    }

    public static void chipSelected(TextView view, boolean selected) {
        Context context = view.getContext();
        if (selected) {
            view.setTextColor(Color.WHITE);
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            view.setBackground(ripple(round(context, PRIMARY, 12), 0x44FFFFFF));
        } else {
            view.setTextColor(MUTED);
            view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            view.setBackground(ripple(roundStroke(context, SURFACE, 12, BORDER, 1), 0x14B45309));
        }
    }

    public static TextView badge(Context context, CharSequence value, int bgColor, int textColor) {
        TextView view = text(context, value, 10.5f, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        view.setBackground(round(context, bgColor, 999));
        return view;
    }

    public static EditText input(Context context, String hintText) {
        EditText edit = new EditText(context);
        edit.setHint(hintText);
        edit.setHintTextColor(FAINT);
        edit.setTextColor(TEXT);
        edit.setTextSize(14.5f);
        edit.setSingleLine(true);
        edit.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
        edit.setBackground(roundStroke(context, SURFACE_ALT, 12, 0xFFEADFD0, 1));
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        return edit;
    }

    public static EditText secretInput(Context context, String hintText) {
        EditText edit = input(context, hintText);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setTypeface(Typeface.MONOSPACE);
        return edit;
    }

    public static EditText multiline(Context context, String hintText) {
        EditText edit = new EditText(context);
        edit.setHint(hintText);
        edit.setHintTextColor(FAINT);
        edit.setTextColor(TEXT);
        edit.setTextSize(14.5f);
        edit.setGravity(Gravity.TOP | Gravity.START);
        edit.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
        edit.setBackground(roundStroke(context, SURFACE_ALT, 12, 0xFFEADFD0, 1));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edit.setMinLines(5);
        edit.setLineSpacing(dp(context, 3), 1.0f);
        return edit;
    }

    public static Switch toggle(Context context, boolean checked) {
        Switch toggle = new Switch(context);
        toggle.setChecked(checked);
        toggle.setButtonTintList(null);
        return toggle;
    }

    public static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(BORDER);
        view.setLayoutParams(lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(context, 0.7f))));
        return view;
    }

    public static View space(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(lp(1, dp(context, heightDp)));
        return view;
    }

    public static LinearLayout card(Context context) {
        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        card.setBackground(roundStroke(context, SURFACE, 18, 0xFFEADFD0, 1));
        card.setElevation(dp(context, 1));
        card.setClipToOutline(true);
        return card;
    }

    public static LinearLayout.LayoutParams cardMargin(Context context) {
        LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(context, 12));
        return p;
    }

    public static ScrollView scroll(Context context, View child) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 24));
        scroll.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /** 左标题右控件的设置行。 */
    public static LinearLayout settingRow(Context context, String title, String subtitle, View right) {
        LinearLayout row = horizontal(context);
        row.setPadding(dp(context, 2), dp(context, 11), dp(context, 2), dp(context, 11));
        LinearLayout texts = vertical(context);
        TextView titleView = text(context, title, 14.5f, TEXT, false);
        texts.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = text(context, subtitle, 11.5f, MUTED, false);
            sub.setPadding(0, dp(context, 3), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right != null) {
            row.addView(right, lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }

    public static TextView chevron(Context context) {
        TextView view = text(context, "›", 22, FAINT, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 6), 0, dp(context, 2), 0);
        return view;
    }

    public static TextView valueText(Context context, String value, int color) {
        TextView view = text(context, value, 13, color, false);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        view.setMaxWidth(dp(context, 190));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    public static SeekBar seekBar(Context context, int max, int progress) {
        SeekBar bar = new SeekBar(context);
        bar.setMax(Math.max(1, max));
        bar.setProgress(progress);
        bar.setProgressTintList(ColorStateList.valueOf(PRIMARY));
        bar.setThumbTintList(ColorStateList.valueOf(PRIMARY));
        return bar;
    }

    public static ProgressBar progress(Context context, int sizeDp) {
        ProgressBar bar = new ProgressBar(context);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(PRIMARY));
        int size = dp(context, sizeDp);
        bar.setLayoutParams(lp(size, size));
        return bar;
    }

    public static void showInfo(Activity activity, String title, String message) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    public static void confirm(Activity activity, String title, String message, String positive, Runnable onOk) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton(positive, (dialog, which) -> onOk.run())
                .show();
    }

    public static void listDialog(Activity activity, String title, String[] items, int selected, OnIndex onIndex) {
        if (activity == null || activity.isFinishing()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity).setTitle(title);
        builder.setSingleChoiceItems(items, selected, (dialog, which) -> {
            dialog.dismiss();
            onIndex.onIndex(which);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public static void sheet(Activity activity, String title, String[] items, OnIndex onIndex) {
        if (activity == null || activity.isFinishing()) return;
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = vertical(activity);
        box.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 18));
        box.setBackground(round(activity, SURFACE, 24));

        View handle = new View(activity);
        handle.setBackground(round(activity, 0xFFD8CFC0, 99));
        LinearLayout.LayoutParams hp = lp(dp(activity, 38), dp(activity, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(activity, 2);
        hp.bottomMargin = dp(activity, 14);
        box.addView(handle, hp);

        if (!TextUtils.isEmpty(title)) {
            TextView tv = text(activity, title, 12, MUTED, true);
            tv.setLetterSpacing(0.04f);
            tv.setPadding(dp(activity, 4), 0, 0, dp(activity, 8));
            box.addView(tv);
        }

        ScrollView scroll = new ScrollView(activity);
        LinearLayout rows = vertical(activity);
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView row = text(activity, items[i], 15, TEXT, false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
            row.setMinHeight(dp(activity, 50));
            row.setBackground(ripple(round(activity, 0xFFF8F3EA, 13), 0x1AB45309));
            LinearLayout.LayoutParams rp = lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, dp(activity, 2), 0, dp(activity, 2));
            rows.addView(row, rp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                onIndex.onIndex(index);
            });
        }
        scroll.addView(rows);
        int desired = items.length * dp(activity, 54) + dp(activity, 16);
        int max = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.66f);
        int sheetHeight = Math.max(dp(activity, 90), Math.min(desired, max));
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight));

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.45f);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(WindowGroupMatch(), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static int WindowGroupMatch() {
        return ViewGroup.LayoutParams.MATCH_PARENT;
    }

    public static AlertDialog editDialog(Activity activity, String title, String initial, boolean multiline,
                                         String hint, OnText onSave) {
        if (activity == null || activity.isFinishing()) return null;
        LinearLayout box = vertical(activity);
        box.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 4));
        EditText edit = multiline ? Ui.multiline(activity, hint) : Ui.input(activity, hint);
        edit.setText(initial == null ? "" : initial);
        if (multiline) {
            edit.setMinLines(6);
            edit.setMaxLines(14);
        }
        box.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, which) -> {
                    if (onSave != null) onSave.onText(edit.getText().toString());
                })
                .create();
        dialog.show();
        return dialog;
    }

    public static void openUrl(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast(context, "没有可打开该链接的应用");
        }
    }

    public static void toast(Context context, String message) {
        if (context == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    public static void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void showKeyboard(View view) {
        if (view == null) return;
        view.requestFocus();
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void statusBar(Activity activity, int statusColor, boolean darkStatusIcons, int navColor, boolean darkNavIcons) {
        Window window = activity.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(statusColor);
        window.setNavigationBarColor(navColor);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (darkStatusIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (darkNavIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static String ellipsize(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String colorHex(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    public interface OnIndex {
        void onIndex(int index);
    }

    public interface OnText {
        void onText(String text);
    }
}
