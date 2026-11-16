package com.yidu.novel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.Book;
import com.yidu.novel.store.AppStore;
import com.yidu.novel.store.BookRepository;
import com.yidu.novel.util.TextUtil;
import com.yidu.novel.util.Ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 书库首页：导入 TXT、继续阅读、收藏夹与书籍管理。 */
public class MainActivity extends Activity {
    private static final int REQ_IMPORT = 1001;

    private AppStore store;
    private FrameLayout root;
    private LinearLayout listArea;
    private TextView headerSubtitle;
    private TextView apiChip;
    private TextView favoriteFilter;
    private TextView allFilter;
    private boolean onlyFavorite = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean importing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = App.store(this);
        buildUi();
        refresh();
        handleExternalIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleExternalIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        Ui.statusBar(this, Ui.BG, true, Ui.SURFACE, true);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout column = Ui.vertical(this);
        column.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = Ui.horizontal(this);
        header.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout titles = Ui.vertical(this);
        TextView appTitle = Ui.text(this, "译读", 26, Ui.TEXT, true);
        appTitle.setTypeface(Typeface.create("serif", Typeface.BOLD));
        headerSubtitle = Ui.text(this, "", 11.5f, Ui.MUTED, false);
        headerSubtitle.setPadding(0, Ui.dp(this, 2), 0, 0);
        titles.addView(appTitle);
        titles.addView(headerSubtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        apiChip = Ui.text(this, "", 12, Ui.PRIMARY, true);
        apiChip.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 7));
        apiChip.setBackground(Ui.ripple(Ui.round(this, Ui.SURFACE, 99), 0x22B45309));
        apiChip.setClickable(true);
        apiChip.setOnClickListener(v -> startActivity(new Intent(this, ApiProfilesActivity.class)));
        header.addView(apiChip, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView settings = Ui.iconButton(this, "⚙", () -> startActivity(new Intent(this, SettingsActivity.class)));
        LinearLayout.LayoutParams sp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
        sp.leftMargin = Ui.dp(this, 8);
        header.addView(settings, sp);
        column.addView(header);

        LinearLayout filters = Ui.horizontal(this);
        filters.setPadding(Ui.dp(this, 18), Ui.dp(this, 2), Ui.dp(this, 18), Ui.dp(this, 8));
        allFilter = Ui.chip(this, "全部书籍");
        allFilter.setOnClickListener(v -> {
            onlyFavorite = false;
            refresh();
        });
        favoriteFilter = Ui.chip(this, "★ 收藏夹");
        LinearLayout.LayoutParams fp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.leftMargin = Ui.dp(this, 8);
        favoriteFilter.setOnClickListener(v -> {
            onlyFavorite = true;
            refresh();
        });
        filters.addView(allFilter, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        filters.addView(favoriteFilter, fp);
        column.addView(filters);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        listArea = Ui.vertical(this);
        scroll.addView(listArea, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout footer = Ui.vertical(this);
        footer.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        footer.setBackgroundColor(Ui.SURFACE);
        footer.setElevation(Ui.dp(this, 10));
        TextView importButton = Ui.primaryButton(this, "＋  导入 TXT 小说", this::pickTxtFile);
        footer.addView(importButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView hint = Ui.text(this, "支持 UTF-8 / GBK / UTF-16 编码的 .txt 文件", 10.5f, Ui.FAINT, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 6), 0, 0);
        footer.addView(hint);
        column.addView(footer);

        root.addView(column);
        setContentView(root);
    }

    private void refresh() {
        if (listArea == null) return;
        updateHeader();
        Ui.chipSelected(allFilter, !onlyFavorite);
        Ui.chipSelected(favoriteFilter, onlyFavorite);
        listArea.removeAllViews();

        List<Book> books = new ArrayList<>(store.getBooks());
        books.sort((a, b) -> {
            if (a.favorite != b.favorite && onlyFavorite) return 0;
            long ta = Math.max(a.lastReadAt, a.addedAt);
            long tb = Math.max(b.lastReadAt, b.addedAt);
            return Long.compare(tb, ta);
        });
        if (onlyFavorite) {
            List<Book> filtered = new ArrayList<>();
            for (Book book : books) if (book.favorite) filtered.add(book);
            books = filtered;
        }

        Book last = store.getLastBook();
        if (last != null && !onlyFavorite) {
            listArea.addView(continueCard(last), Ui.cardMargin(this));
        }

        if (store.getActiveApi() == null) {
            listArea.addView(apiWarningCard(), Ui.cardMargin(this));
        } else {
            ApiProfile api = store.getActiveApi();
            if (needsKey(api)) {
                listArea.addView(apiWarningCard(), Ui.cardMargin(this));
            }
        }

        if (books.isEmpty()) {
            listArea.addView(emptyCard(), Ui.cardMargin(this));
            return;
        }

        TextView section = Ui.section(this, onlyFavorite ? "收藏夹 · " + books.size() + " 本" : "全部书籍 · " + books.size() + " 本");
        listArea.addView(section);
        for (Book book : books) {
            listArea.addView(bookCard(book), Ui.cardMargin(this));
        }
    }

    private boolean needsKey(ApiProfile api) {
        if (api.apiKey != null && !api.apiKey.trim().isEmpty()) return false;
        String base = api.baseUrl == null ? "" : api.baseUrl.toLowerCase(java.util.Locale.US);
        if ("ollama".equals(api.providerId)) return false;
        return !(base.contains("127.0.0.1") || base.contains("localhost")
                || base.contains("192.168.") || base.contains("10.") || base.contains("172.16."));
    }

    private void updateHeader() {
        int books = store.getBooks().size();
        ApiProfile api = store.getActiveApi();
        String apiText;
        if (api == null) {
            apiText = "未配置 API";
        } else if (needsKey(api)) {
            apiText = "API 待填写";
        } else {
            apiText = TextUtil.shortTitle(api.name, 12);
        }
        apiChip.setText("⚙ " + apiText);
        headerSubtitle.setText(books == 0 ? "导入一本 TXT，开始逐页翻译阅读"
                : books + " 本小说 · " + TextUtil.shortTitle(api == null ? "未配置翻译接口" : api.model, 22));
    }

    private View continueCard(Book book) {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16));
        card.setBackground(Ui.gradient(this, new int[]{0xFFB45309, 0xFF92400E}, 20));
        card.setElevation(Ui.dp(this, 3));
        card.setClipToOutline(true);

        LinearLayout top = Ui.horizontal(this);
        TextView label = Ui.text(this, "继续上次阅读", 11.5f, 0xFFFDE68A, true);
        label.setLetterSpacing(0.05f);
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView percent = Ui.text(this, book.percent() + "%", 12, 0xFFFFF7ED, true);
        top.addView(percent);
        card.addView(top);

        TextView title = Ui.text(this, book.title, 19, 0xFFFFFFFF, true);
        title.setPadding(0, Ui.dp(this, 8), 0, 0);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);

        TextView meta = Ui.text(this, book.progressLabel() + " · " + book.chapterCount + " 章 · "
                + TextUtil.formatSize(new File(book.path).length()), 11.5f, 0xFFFDE68A, false);
        meta.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
        card.addView(meta);

        TextView button = Ui.text(this, "继续阅读  →", 14, 0xFF7C2D12, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        button.setBackground(Ui.ripple(Ui.round(this, 0xFFFFF7ED, 12), 0x33FFFFFF));
        button.setClickable(true);
        button.setOnClickListener(v -> openBook(book));
        card.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View apiWarningCard() {
        LinearLayout card = Ui.card(this);
        card.setBackground(Ui.roundStroke(this, 0xFFFFFBEB, 18, 0xFFFCD34D, 1));
        TextView title = Ui.text(this, "还没有配置翻译接口", 15, 0xFF92400E, true);
        card.addView(title);
        TextView desc = Ui.text(this, "要使用「翻译本页」，请先添加一个大模型 API，例如硅基流动、A6API、DeepSeek 官方或自定义接口。", 12, 0xFFB45309, false);
        desc.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        desc.setLineSpacing(Ui.dp(this, 2), 1.0f);
        card.addView(desc);
        LinearLayout actions = Ui.horizontal(this);
        TextView go = Ui.smallButton(this, "去配置 API  →", Ui.PRIMARY_DARK, () ->
                startActivity(new Intent(this, ApiProfilesActivity.class)));
        actions.addView(go);
        card.addView(actions);
        return card;
    }

    private View emptyCard() {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 36), Ui.dp(this, 20), Ui.dp(this, 36));
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.roundStroke(this, Ui.SURFACE, 20, Ui.BORDER, 1));
        TextView icon = Ui.text(this, "📖", 42, Ui.MUTED, false);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);
        TextView title = Ui.text(this, onlyFavorite ? "收藏夹还是空的" : "书库还是空的", 16, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(title);
        TextView desc = Ui.text(this, onlyFavorite ? "在书库列表中点击书籍右上角的 ☆ 即可收藏。"
                : "点击下方「导入 TXT 小说」选择手机里的 .txt 文件。\n导入后可以边读边翻译，译文与进度会自动保存。", 12.5f, Ui.MUTED, false);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, Ui.dp(this, 8), 0, 0);
        desc.setLineSpacing(Ui.dp(this, 3), 1.0f);
        card.addView(desc);
        return card;
    }

    private View bookCard(Book book) {
        LinearLayout card = Ui.card(this);
        card.setClickable(true);
        card.setOnClickListener(v -> openBook(book));
        card.setOnLongClickListener(v -> {
            showBookMenu(book);
            return true;
        });

        LinearLayout top = Ui.horizontal(this);
        LinearLayout texts = Ui.vertical(this);
        TextView title = Ui.text(this, book.title, 16.5f, Ui.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        String metaText = book.chapterCount + " 章 · " + TextUtil.formatSize(new File(book.path).length())
                + " · " + (book.charset == null ? "UTF-8" : book.charset);
        TextView meta = Ui.text(this, metaText, 11.5f, Ui.MUTED, false);
        meta.setPadding(0, Ui.dp(this, 4), 0, 0);
        texts.addView(meta);
        top.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView star = Ui.text(this, book.favorite ? "★" : "☆", 21, book.favorite ? 0xFFD97706 : Ui.FAINT, false);
        star.setGravity(Gravity.CENTER);
        star.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 2), 0);
        star.setClickable(true);
        star.setOnClickListener(v -> {
            book.favorite = !book.favorite;
            store.saveBook(book);
            refresh();
        });
        top.addView(star);
        card.addView(top);

        LinearLayout progressRow = Ui.horizontal(this);
        progressRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(book.percent());
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEADFD0));
        progressRow.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(this, 5), 1));
        TextView percent = Ui.text(this, book.percent() + "%", 12, Ui.MUTED, true);
        percent.setPadding(Ui.dp(this, 10), 0, 0, 0);
        progressRow.addView(percent);
        card.addView(progressRow);

        String readText = book.lastReadAt <= 0 ? "尚未开始阅读" : "上次阅读 " + formatTime(book.lastReadAt);
        TextView last = Ui.text(this, readText + " · " + book.progressLabel(), 11, Ui.FAINT, false);
        last.setPadding(0, Ui.dp(this, 7), 0, 0);
        card.addView(last);
        return card;
    }

    private void showBookMenu(Book book) {
        List<String> items = new ArrayList<>();
        items.add(book.favorite ? "取消收藏" : "加入收藏夹");
        items.add("导出译文");
        items.add("删除这本书");
        new AlertDialog.Builder(this)
                .setTitle(book.title)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) {
                        book.favorite = !book.favorite;
                        store.saveBook(book);
                        refresh();
                    } else if (which == 1) {
                        startActivity(new Intent(this, ReaderActivity.class)
                                .putExtra("book_id", book.id)
                                .putExtra("open_export", true));
                    } else {
                        Ui.confirm(this, "删除书籍", "将删除《" + book.title + "》及其已保存的译文，无法恢复。", "删除", () -> {
                            store.deleteBook(book.id);
                            BookRepository.delete(this, book);
                            refresh();
                        });
                    }
                })
                .show();
    }

    private void openBook(Book book) {
        startActivity(new Intent(this, ReaderActivity.class).putExtra("book_id", book.id));
    }

    private void pickTxtFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/octet-stream", "text/*"});
        try {
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Exception e) {
            Ui.toast(this, "无法打开文件选择器：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importBook(data.getData());
        }
    }

    private void importBook(Uri uri) {
        if (importing) return;
        importing = true;
        final LinearLayout overlay = showLoading("正在导入并解析小说…");
        new Thread(() -> {
            try {
                Book book = BookRepository.importUri(MainActivity.this, uri);
                store.saveBook(book);
                store.setLastBookId(book.id);
                handler.post(() -> {
                    importing = false;
                    root.removeView(overlay);
                    refresh();
                    openBook(book);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    importing = false;
                    root.removeView(overlay);
                    Ui.showInfo(MainActivity.this, "导入失败", e.getMessage() == null ? "未知错误" : e.getMessage());
                });
            }
        }, "yidu-import").start();
    }

    private LinearLayout showLoading(String text) {
        LinearLayout box = Ui.vertical(this);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xCC12100E);
        box.setClickable(true);
        ProgressBar bar = Ui.progress(this, 40);
        box.addView(bar);
        TextView label = Ui.text(this, text, 14, 0xFFFFFFFF, false);
        label.setPadding(0, Ui.dp(this, 12), 0, 0);
        label.setGravity(Gravity.CENTER);
        box.addView(label);
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return box;
    }

    private void handleExternalIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            uri = intent.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri != null) {
            importBook(uri);
        }
    }

    private String formatTime(long time) {
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000L) return "刚刚";
        if (diff < 3_600_000L) return (diff / 60_000L) + " 分钟前";
        if (diff < 86_400_000L) return (diff / 3_600_000L) + " 小时前";
        if (diff < 7 * 86_400_000L) return (diff / 86_400_000L) + " 天前";
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA);
        return format.format(new java.util.Date(time));
    }
}
