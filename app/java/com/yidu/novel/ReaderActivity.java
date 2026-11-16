package com.yidu.novel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.yidu.novel.model.ApiProfile;
import com.yidu.novel.model.Book;
import com.yidu.novel.model.Bookmark;
import com.yidu.novel.model.PromptProfile;
import com.yidu.novel.net.TranslationEngine;
import com.yidu.novel.store.AppStore;
import com.yidu.novel.store.BookRepository;
import com.yidu.novel.store.TranslationStore;
import com.yidu.novel.util.Exporter;
import com.yidu.novel.util.Lang;
import com.yidu.novel.util.Novel;
import com.yidu.novel.util.TextUtil;
import com.yidu.novel.util.Ui;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 阅读页：分页阅读、样式设置、书签、目录，以及页面级流式翻译。 */
public class ReaderActivity extends Activity {
    private static final int REQ_EXPORT = 2001;

    private AppStore store;
    private Book book;
    private Novel novel;
    private TranslationStore translations;
    private TranslationEngine engine;
    private ApiProfile api;
    private PromptProfile prompt;

    private FrameLayout root;
    private LinearLayout pageColumn;
    private TextView pageText;
    private FrameLayout readArea;
    private LinearLayout bilingualContainer;
    private ScrollView bilingualScroll;
    private LinearLayout bilingualRows;
    private TextView bilingualStatus;
    private TextView chapterLabel;
    private TextView pageLabel;
    private TextView prevLabel;
    private TextView nextLabel;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView favoriteBtn;
    private TextView bookmarkBtn;
    private TextView translateBtn;
    private TextView translateLabel;
    private TextView autoBtn;
    private TextView autoLabel;
    private FrameLayout panel;
    private LinearLayout panelHeader;
    private TextView panelTitle;
    private TextView panelStatus;
    private TextView panelEditBtn;
    private TextView panelCloseBtn;
    private ScrollView panelScroll;
    private LinearLayout cardsBox;
    private TextView emptyPanel;

    private final List<Page> pages = new ArrayList<>();
    private int pageIndex;
    private int currentBlock;
    private int chapterIndex;
    private Novel.Chapter chapter;
    private boolean translating;
    private boolean editMode;
    private boolean panelVisible;
    private boolean barsVisible = true;
    private boolean panelUserClosed;
    private int translateSession;
    private boolean loading;
    private boolean exportedFromIntent;

    private final Map<Integer, TextView> cardViews = new HashMap<>();
    private final Map<Integer, TextView> paragraphTranslationViews = new HashMap<>();
    private final Map<Integer, String> blockPartials = new HashMap<>();
    private String bilingualNote = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yidu-reader-io");
        t.setDaemon(true);
        return t;
    });
    private Runnable pendingPaginate;
    private Runnable pendingAuto;
    private String styleSignature = "";
    private boolean initialHintShown;

    private static class Page {
        int startBlock;
        int startChar;
        int endBlock;
        int endChar;

        Page(int startBlock, int startChar, int endBlock, int endChar) {
            this.startBlock = startBlock;
            this.startChar = startChar;
            this.endBlock = endBlock;
            this.endChar = endChar;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = App.store(this);
        String bookId = getIntent().getStringExtra("book_id");
        if (bookId == null) {
            Book last = store.getLastBook();
            bookId = last == null ? null : last.id;
        }
        exportedFromIntent = getIntent().getBooleanExtra("open_export", false);
        if (bookId == null) {
            Ui.toast(this, "没有可打开的书");
            finish();
            return;
        }
        book = store.getBook(bookId);
        if (book == null) {
            Ui.toast(this, "书籍不存在");
            finish();
            return;
        }
        translations = TranslationStore.open(this, book.id);
        int startBlock = getIntent().getIntExtra("start_block", -1);
        if (startBlock >= 0) book.lastBlock = startBlock;

        root = new FrameLayout(this);
        setContentView(root);
        showLoading();
        loadNovel();
    }

    private void loadNovel() {
        loading = true;
        io.execute(() -> {
            try {
                Novel parsed = BookRepository.load(ReaderActivity.this, book);
                main.post(() -> {
                    loading = false;
                    novel = parsed;
                    onNovelLoaded();
                });
            } catch (Exception e) {
                main.post(() -> {
                    loading = false;
                    Ui.showInfo(this, "打开失败", e.getMessage() == null ? "无法读取文件" : e.getMessage());
                    finish();
                });
            }
        });
    }

    private void onNovelLoaded() {
        if (novel == null || novel.blocks.isEmpty()) {
            Ui.showInfo(this, "打开失败", "这本书没有解析到正文内容，可能不是有效的 TXT 小说。");
            finish();
            return;
        }
        currentBlock = Math.max(0, Math.min(novel.blocks.size() - 1, book.lastBlock));
        chapterIndex = novel.chapterOfBlock(currentBlock);
        chapter = novel.chapter(chapterIndex);
        buildUi();
        applyReaderStyle();
        pageText.post(this::paginate);
        store.setLastBookId(book.id);
        if (exportedFromIntent) {
            exportedFromIntent = false;
            main.postDelayed(this::startExportFlow, 350);
        } else {
            showFirstRunHint();
        }
    }

    private void buildUi() {
        root.removeAllViews();
        root.setBackgroundColor(store.getBgColor());

        pageColumn = Ui.vertical(this);
        pageColumn.setPadding(Ui.dp(this, 22), Ui.dp(this, 60), Ui.dp(this, 22), Ui.dp(this, 76));
        pageColumn.setBackgroundColor(store.getBgColor());
        pageColumn.setClickable(true);
        FrameLayout.LayoutParams columnParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(pageColumn, columnParams);

        readArea = new FrameLayout(this);
        pageText = new TextView(this);
        pageText.setTextColor(store.getTextColor());
        pageText.setIncludeFontPadding(false);
        pageText.setLineSpacing(0, store.getLineSpacing());
        pageText.setGravity(Gravity.TOP | Gravity.START);
        pageText.setTextIsSelectable(false);
        pageText.setBackgroundColor(store.getBgColor());
        readArea.addView(pageText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bilingualContainer = Ui.vertical(this);
        bilingualContainer.setVisibility(View.GONE);
        bilingualStatus = Ui.text(this, "", 10.5f, Ui.MUTED, false);
        bilingualStatus.setPadding(0, 0, 0, Ui.dp(this, 8));
        bilingualStatus.setMaxLines(3);
        bilingualContainer.addView(bilingualStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bilingualScroll = new ScrollView(this);
        bilingualScroll.setFillViewport(false);
        bilingualScroll.setClipToPadding(false);
        bilingualRows = Ui.vertical(this);
        bilingualRows.setPadding(0, 0, 0, Ui.dp(this, 18));
        bilingualScroll.addView(bilingualRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bilingualContainer.addView(bilingualScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        bilingualScroll.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            long downAt;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    downAt = System.currentTimeMillis();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    String mode = store.getPageTurnMode();
                    if (Math.abs(dx) > Ui.dp(ReaderActivity.this, 52)
                            && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        if ("swipe".equals(mode) || "tap".equals(mode)) {
                            if (dx < 0) nextPage();
                            else prevPage();
                        }
                    } else if (Math.abs(dx) < Ui.dp(ReaderActivity.this, 14)
                            && Math.abs(dy) < Ui.dp(ReaderActivity.this, 14)
                            && System.currentTimeMillis() - downAt < 450
                            && "tap".equals(mode)) {
                        float width = v.getWidth();
                        if (event.getX() < width / 3f) prevPage();
                        else if (event.getX() > width * 2f / 3f) nextPage();
                    }
                }
                return false;
            }
        });
        readArea.addView(bilingualContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageColumn.addView(readArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        applyModeVisibility();

        LinearLayout statusRow = Ui.horizontal(this);
        statusRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        chapterLabel = Ui.text(this, "", 10.5f, Ui.MUTED, false);
        chapterLabel.setSingleLine(true);
        chapterLabel.setEllipsize(TextUtils.TruncateAt.END);
        chapterLabel.setMaxWidth(Ui.dp(this, 130));
        statusRow.addView(chapterLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        prevLabel = Ui.text(this, "‹", 18, Ui.MUTED, false);
        prevLabel.setGravity(Gravity.CENTER);
        prevLabel.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        prevLabel.setClickable(true);
        prevLabel.setOnClickListener(v -> prevPage());
        statusRow.addView(prevLabel);

        pageLabel = Ui.text(this, "", 10.5f, Ui.MUTED, false);
        pageLabel.setGravity(Gravity.CENTER);
        statusRow.addView(pageLabel);

        nextLabel = Ui.text(this, "›", 18, Ui.MUTED, false);
        nextLabel.setGravity(Gravity.CENTER);
        nextLabel.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        nextLabel.setClickable(true);
        nextLabel.setOnClickListener(v -> nextPage());
        statusRow.addView(nextLabel);
        pageColumn.addView(statusRow);

        pageColumn.setOnTouchListener(new View.OnTouchListener() {
            float downX;
            float downY;
            long downAt;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    downAt = System.currentTimeMillis();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    long duration = System.currentTimeMillis() - downAt;
                    String mode = store.getPageTurnMode();
                    if (Math.abs(dx) > Ui.dp(ReaderActivity.this, 46)
                            && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        if ("swipe".equals(mode)) {
                            if (dx < 0) nextPage();
                            else prevPage();
                        }
                        return true;
                    }
                    if (Math.abs(dx) < Ui.dp(ReaderActivity.this, 16)
                            && Math.abs(dy) < Ui.dp(ReaderActivity.this, 16)
                            && duration < 500) {
                        if ("tap".equals(mode)) {
                            float width = v.getWidth();
                            if (event.getX() < width / 3f) prevPage();
                            else if (event.getX() > width * 2f / 3f) nextPage();
                            else toggleBars();
                        } else {
                            toggleBars();
                        }
                    }
                    return true;
                }
                return false;
            }
        });

        buildTopBar();
        buildBottomBar();
        buildPanel();
        updateBarStyle();
        updatePageLabels();
        setUiVisible(barsVisible);
    }

    private void buildTopBar() {
        topBar = Ui.horizontal(this);
        topBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        topBar.setElevation(Ui.dp(this, 4));

        TextView back = Ui.iconButton(this, "‹", this::finishReader);
        back.setTextSize(28);
        topBar.addView(back, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));

        TextView title = Ui.text(this, book.title, 14.5f, Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        favoriteBtn = Ui.iconButton(this, "☆", this::toggleFavorite);
        topBar.addView(favoriteBtn, Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));

        bookmarkBtn = Ui.iconButton(this, "🔖", this::toggleBookmark);
        LinearLayout.LayoutParams bp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
        bp.leftMargin = Ui.dp(this, 4);
        topBar.addView(bookmarkBtn, bp);

        TextView menu = Ui.iconButton(this, "☰", this::showMenuSheet);
        LinearLayout.LayoutParams mp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38));
        mp.leftMargin = Ui.dp(this, 4);
        topBar.addView(menu, mp);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP;
        root.addView(topBar, params);
    }

    private void buildBottomBar() {
        bottomBar = Ui.horizontal(this);
        bottomBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        bottomBar.setElevation(Ui.dp(this, 8));
        bottomBar.setGravity(Gravity.CENTER);

        bottomBar.addView(bottomItem("☰", "目录", this::showToc), weightParams());
        bottomBar.addView(bottomItem("文", "翻译", this::onTranslateClick), weightParams());
        bottomBar.addView(bottomItem("↻", "重翻", () -> translatePage(true)), weightParams());
        bottomBar.addView(bottomItem("⟳", "自动", () -> {
            boolean value = !store.isAutoTranslate();
            store.setAutoTranslate(value);
            panelUserClosed = false;
            updateAutoButton();
            if (value) translatePage(false);
            else Ui.toast(this, "已关闭自动翻译");
        }), weightParams());
        bottomBar.addView(bottomItem("Aa", "设置", this::showQuickSettings), weightParams());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60));
        params.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, params);
    }

    private LinearLayout.LayoutParams weightParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private LinearLayout bottomItem(String icon, String label, Runnable runnable) {
        LinearLayout item = Ui.vertical(this);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setBackground(Ui.ripple(Ui.round(this, 0x00000000, 12), 0x1A000000));
        TextView iconView = Ui.text(this, icon, 17, Ui.TEXT, false);
        iconView.setGravity(Gravity.CENTER);
        TextView labelView = Ui.text(this, label, 10, Ui.MUTED, false);
        labelView.setGravity(Gravity.CENTER);
        item.addView(iconView);
        item.addView(labelView);
        item.setOnClickListener(v -> runnable.run());
        if ("文".equals(icon)) {
            translateBtn = iconView;
            translateLabel = labelView;
        } else if ("⟳".equals(icon)) {
            autoBtn = iconView;
            autoLabel = labelView;
        }
        return item;
    }

    private void buildPanel() {
        panel = new FrameLayout(this);
        panel.setBackground(Ui.round(this, Ui.SURFACE, 20));
        panel.setElevation(Ui.dp(this, 16));
        panel.setVisibility(View.GONE);

        LinearLayout content = Ui.vertical(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        panel.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View handle = new View(this);
        handle.setBackground(Ui.round(this, 0xFFD8CFC0, 99));
        LinearLayout.LayoutParams hp = Ui.lp(Ui.dp(this, 36), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(this, 8);
        content.addView(handle, hp);

        panelHeader = Ui.horizontal(this);
        LinearLayout titles = Ui.vertical(this);
        panelTitle = Ui.text(this, "本页译文", 15, Ui.TEXT, true);
        panelStatus = Ui.text(this, "", 11, Ui.MUTED, false);
        panelStatus.setPadding(0, Ui.dp(this, 2), 0, 0);
        titles.addView(panelTitle);
        titles.addView(panelStatus);
        panelHeader.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        panelEditBtn = Ui.smallButton(this, "编辑", Ui.PRIMARY, this::toggleEditMode);
        panelHeader.addView(panelEditBtn);
        panelCloseBtn = Ui.iconButton(this, "×", this::closePanel);
        LinearLayout.LayoutParams cp = Ui.lp(Ui.dp(this, 32), Ui.dp(this, 32));
        cp.leftMargin = Ui.dp(this, 6);
        panelHeader.addView(panelCloseBtn, cp);
        content.addView(panelHeader);

        panelScroll = new ScrollView(this);
        panelScroll.setFillViewport(false);
        panelScroll.setClipToPadding(false);
        cardsBox = Ui.vertical(this);
        cardsBox.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        panelScroll.addView(cardsBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(panelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        emptyPanel = Ui.text(this, "点击底部「翻译」开始翻译本页\n译文会以流式方式显示，完成后可点按段落编辑。", 12.5f, Ui.FAINT, false);
        emptyPanel.setGravity(Gravity.CENTER);
        emptyPanel.setPadding(Ui.dp(this, 20), Ui.dp(this, 30), Ui.dp(this, 20), Ui.dp(this, 30));
        content.addView(emptyPanel);

        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.46f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.gravity = Gravity.BOTTOM;
        params.bottomMargin = Ui.dp(this, 66);
        params.leftMargin = Ui.dp(this, 8);
        params.rightMargin = Ui.dp(this, 8);
        root.addView(panel, params);
    }

    // ---------------- 分页与渲染 ----------------

    private void paginate() {
        if (novel == null || pageText == null) return;
        int width = pageText.getWidth();
        int height = pageText.getHeight();
        if (width <= 0 || height <= 0) {
            // 双语模式下 pageText 为 GONE，用外层阅读区尺寸分页
            width = readArea.getWidth();
            height = readArea.getHeight();
        }
        if (width <= 0 || height <= 0) {
            pageText.postDelayed(this::paginate, 120);
            return;
        }
        int chapterOfBlock = novel.chapterOfBlock(currentBlock);
        chapter = novel.chapter(chapterOfBlock);
        chapterIndex = chapter.index;
        TextPaint paint = new TextPaint(pageText.getPaint());
        List<Page> result = new ArrayList<>();
        int block = chapter.startBlock;
        int charStart = 0;
        int guard = 0;
        while (block < chapter.endBlock && guard++ < 20000) {
            int startBlock = block;
            int startChar = charStart;
            int bestEndBlock = -1;
            for (int endBlock = block; endBlock < chapter.endBlock; endBlock++) {
                Page candidate = new Page(startBlock, startChar, endBlock + 1, 0);
                String candidateText = buildPageText(candidate);
                if (measure(candidateText, paint, width) <= height) {
                    bestEndBlock = endBlock + 1;
                } else {
                    break;
                }
            }
            if (bestEndBlock > 0) {
                result.add(new Page(startBlock, startChar, bestEndBlock, 0));
                block = bestEndBlock;
                charStart = 0;
                continue;
            }
            String text = novel.blocks.get(startBlock).text;
            int from = startChar;
            if (from >= text.length()) {
                block = startBlock + 1;
                charStart = 0;
                continue;
            }
            int fit = findFittingPrefix(startBlock, startChar, text, from, height, paint, width);
            if (fit <= from) fit = Math.min(text.length(), from + 1);
            result.add(new Page(startBlock, startChar, startBlock + 1, fit));
            if (fit >= text.length()) {
                block = startBlock + 1;
                charStart = 0;
            } else {
                block = startBlock;
                charStart = fit;
            }
        }
        if (result.isEmpty()) {
            result.add(new Page(chapter.startBlock, 0, Math.min(chapter.endBlock, chapter.startBlock + 1), 0));
        }
        pages.clear();
        pages.addAll(result);
        pageIndex = findPage(currentBlock);
        currentBlock = pages.get(pageIndex).startBlock;
        renderPage(true);
    }

    private int findFittingPrefix(int startBlock, int startChar, String text, int from,
                                  int height, TextPaint paint, int width) {
        int low = from + 1;
        int high = text.length();
        int best = from;
        while (low <= high) {
            int mid = (low + high) / 2;
            Page candidate = new Page(startBlock, startChar, startBlock + 1, mid);
            if (measure(buildPageText(candidate), paint, width) <= height) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private int measure(String text, TextPaint paint, int width) {
        if (text == null || text.isEmpty()) return 0;
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setIncludePad(false)
                .setLineSpacing(0, store.getLineSpacing())
                .build();
        return layout.getHeight();
    }

    private String buildPageText(Page page) {
        StringBuilder sb = new StringBuilder();
        boolean spacing = store.isParagraphSpacing();
        boolean indent = store.isIndent();
        int end = Math.min(page.endBlock, novel.blocks.size());
        for (int i = page.startBlock; i < end; i++) {
            Novel.Block block = novel.blocks.get(i);
            int from = i == page.startBlock ? page.startChar : 0;
            int to = i == page.endBlock - 1 && page.endChar > 0
                    ? Math.min(page.endChar, block.text.length()) : block.text.length();
            if (from >= to) continue;
            boolean continuing = i == page.startBlock && page.startChar > 0;
            if (!continuing) {
                if (i > page.startBlock && block.firstInParagraph) {
                    sb.append(spacing ? "\n\n" : "\n");
                }
                if (block.firstInParagraph && indent) sb.append("　　");
            }
            sb.append(block.text, from, to);
        }
        return sb.toString();
    }

    private int findPage(int blockIndex) {
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            if (blockIndex >= page.startBlock && blockIndex < page.endBlock) return i;
        }
        for (int i = pages.size() - 1; i >= 0; i--) {
            if (pages.get(i).startBlock <= blockIndex) return i;
        }
        return 0;
    }

    private void renderPage(boolean resetPanel) {
        if (pages.isEmpty()) return;
        Page page = pages.get(pageIndex);
        currentBlock = page.startBlock;
        if (isBilingual()) {
            renderBilingualPage();
        } else {
            pageText.setText(buildPageText(page));
        }
        updatePageLabels();
        updateBottomButtons();
        updateFavoriteButton();
        updateBookmarkButton();
        if (resetPanel) resetPanelForPage();
        saveProgress();
        scheduleAutoTranslate();
    }

    private boolean isBilingual() {
        return store.isBilingualReading();
    }

    private void applyModeVisibility() {
        if (pageText == null || bilingualContainer == null) return;
        boolean bilingual = isBilingual();
        pageText.setVisibility(bilingual ? View.GONE : View.VISIBLE);
        bilingualContainer.setVisibility(bilingual ? View.VISIBLE : View.GONE);
    }

    /** 双语对照：按段落渲染“原文在上、译文在下”，译文随流式翻译实时回填。 */
    private void renderBilingualPage() {
        if (bilingualRows == null || novel == null || pages.isEmpty()) return;
        bilingualRows.removeAllViews();
        paragraphTranslationViews.clear();
        blockPartials.clear();
        bilingualNote = "";
        List<Integer> pageBlockList = pageBlocks();
        if (pageBlockList.isEmpty()) {
            bilingualNote = "本页没有可翻译的文字";
            updateBilingualStatus();
            return;
        }
        LinkedHashMap<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (Integer blockIndex : pageBlockList) {
            int paragraphIndex = novel.blocks.get(blockIndex).paragraphIndex;
            List<Integer> list = groups.get(paragraphIndex);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(paragraphIndex, list);
            }
            list.add(blockIndex);
        }
        boolean dark = isDark(store.getBgColor());
        int translationColor = dark ? 0xFF7DD3C7 : 0xFF0F766E;
        int translationBg = dark ? 0xFF22302F : 0xFFF1F7F4;
        for (Map.Entry<Integer, List<Integer>> entry : groups.entrySet()) {
            final int paragraphIndex = entry.getKey();
            String original = novel.paragraphText(paragraphIndex);
            if (original == null || original.trim().isEmpty()) continue;
            LinearLayout row = Ui.vertical(this);
            row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 16));

            TextView originalView = Ui.text(this, original, store.getFontSize(), store.getTextColor(), false);
            originalView.setLineSpacing(0, store.getLineSpacing());
            originalView.setIncludeFontPadding(false);
            applyTextTypeface(originalView);
            row.addView(originalView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView translationView = Ui.text(this, "译文待生成…",
                    Math.max(12.5f, store.getFontSize() * 0.88f), translationColor, false);
            translationView.setLineSpacing(Ui.dp(this, 2), 1.45f);
            translationView.setPadding(Ui.dp(this, 10), Ui.dp(this, 7), Ui.dp(this, 10), Ui.dp(this, 7));
            translationView.setBackground(Ui.round(this, translationBg, 8));
            translationView.setVisibility(View.VISIBLE);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = Ui.dp(this, 5);
            row.addView(translationView, tp);
            paragraphTranslationViews.put(paragraphIndex, translationView);
            translationView.setTextColor(translationColor);
            if (translationTextForParagraph(paragraphIndex).isEmpty()) {
                translationView.setTextColor(Ui.alpha(translationColor, 150));
            }
            final int pIndex = paragraphIndex;
            translationView.setOnLongClickListener(v -> {
                showBilingualParagraphActions(pIndex);
                return true;
            });
            bilingualRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            refreshBilingualParagraph(paragraphIndex, translationColor);
        }
        updateBilingualStatus();
        bilingualScroll.scrollTo(0, 0);
    }

    private void applyTextTypeface(TextView view) {
        String family = store.getFontFamily();
        if ("sans".equals(family)) view.setTypeface(Typeface.SANS_SERIF);
        else if ("mono".equals(family)) view.setTypeface(Typeface.MONOSPACE);
        else view.setTypeface(Typeface.SERIF);
    }

    private String translationTextForParagraph(int paragraphIndex) {
        StringBuilder sb = new StringBuilder();
        for (Novel.Block block : novel.blocks) {
            if (block.paragraphIndex != paragraphIndex) continue;
            String translated = translations.get(block.index, block.text);
            if (translated != null) {
                sb.append(translated);
            } else {
                String partial = blockPartials.get(block.index);
                if (partial != null && !partial.isEmpty()) sb.append(partial).append("▍");
            }
        }
        return sb.toString();
    }

    private void refreshBilingualParagraph(int paragraphIndex, int normalColor) {
        TextView view = paragraphTranslationViews.get(paragraphIndex);
        if (view == null) return;
        String text = translationTextForParagraph(paragraphIndex);
        if (text.isEmpty()) {
            view.setText("译文待生成…");
            view.setTextColor(Ui.alpha(normalColor, 150));
        } else {
            view.setText(text);
            view.setTextColor(normalColor);
        }
    }

    private void refreshBilingualBlock(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= novel.blocks.size()) return;
        if (!isBilingual()) return;
        int paragraphIndex = novel.blocks.get(blockIndex).paragraphIndex;
        boolean dark = isDark(store.getBgColor());
        int color = dark ? 0xFF7DD3C7 : 0xFF0F766E;
        refreshBilingualParagraph(paragraphIndex, color);
        updateBilingualStatus();
    }

    private void refreshAllBilingualRows() {
        if (!isBilingual()) return;
        boolean dark = isDark(store.getBgColor());
        int color = dark ? 0xFF7DD3C7 : 0xFF0F766E;
        for (Integer paragraphIndex : new ArrayList<>(paragraphTranslationViews.keySet())) {
            refreshBilingualParagraph(paragraphIndex, color);
        }
        updateBilingualStatus();
    }

    private void updateBilingualStatus() {
        if (bilingualStatus == null) return;
        int total = pageBlocks().size();
        int done = pageTranslatedCount();
        StringBuilder sb = new StringBuilder();
        sb.append("原文在上 · 译文在下 · 本页已译 ").append(done).append("/").append(total).append(" 段");
        if (translating) sb.append(" · 翻译中");
        if (bilingualNote != null && !bilingualNote.isEmpty()) sb.append(" · ").append(bilingualNote);
        bilingualStatus.setText(sb.toString());
    }

    private void showBilingualParagraphActions(int paragraphIndex) {
        if (translating) {
            Ui.toast(this, "翻译进行中，请稍候再编辑");
            return;
        }
        int blocks = 0;
        int translated = 0;
        for (Novel.Block block : novel.blocks) {
            if (block.paragraphIndex != paragraphIndex) continue;
            blocks++;
            if (translations.get(block.index, block.text) != null) translated++;
        }
        if (translated == 0) {
            Ui.toast(this, "本段还没有译文，可先点底部「翻译」");
            return;
        }
        Ui.sheet(this, "本段译文", new String[]{
                "打开译文面板编辑", "重翻本页", "复制本段译文"
        }, index -> {
            if (index == 0) {
                openPanel();
            } else if (index == 1) {
                translatePage(true);
            } else {
                StringBuilder sb = new StringBuilder();
                for (Novel.Block block : novel.blocks) {
                    if (block.paragraphIndex != paragraphIndex) continue;
                    String t = translations.get(block.index, block.text);
                    if (t != null) sb.append(t);
                }
                ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (manager != null) {
                    manager.setPrimaryClip(ClipData.newPlainText("本段译文", sb.toString()));
                    Ui.toast(this, "本段译文已复制");
                }
            }
        });
    }

    private void resetPanelForPage() {
        if (!panelVisible) return;
        renderPanelCards();
        updatePanelStats();
        panelStatus.setText(translating ? "翻译中…" : (pageAllTranslated() ? "本页已全部翻译" : "本页尚未翻译"));
    }

    private void updatePageLabels() {
        if (chapterLabel == null) return;
        String title = chapter == null ? "" : chapter.title;
        chapterLabel.setText(title);
        int percent = novel.percentOfBlock(currentBlock);
        String mode = isBilingual() ? " · 双语" : "";
        pageLabel.setText("第 " + (pageIndex + 1) + "/" + pages.size() + " 页 · " + percent + "%" + mode);
    }

    private void updateBottomButtons() {
        if (translateBtn == null) return;
        if (translating) {
            translateBtn.setText("■");
            translateLabel.setText("停止");
        } else if (pageAllTranslated()) {
            translateBtn.setText("✓");
            translateLabel.setText("已译");
        } else {
            translateBtn.setText("文");
            translateLabel.setText("翻译");
        }
        updateAutoButton();
    }

    private void updateAutoButton() {
        if (autoBtn == null) return;
        boolean on = store.isAutoTranslate();
        autoBtn.setTextColor(on ? Ui.PRIMARY : Ui.TEXT);
        autoLabel.setTextColor(on ? Ui.PRIMARY : Ui.MUTED);
        autoLabel.setText(on ? "自动·开" : "自动");
    }

    private void updateFavoriteButton() {
        if (favoriteBtn == null) return;
        favoriteBtn.setText(book.favorite ? "★" : "☆");
        favoriteBtn.setTextColor(book.favorite ? 0xFFD97706 : Ui.TEXT);
    }

    private void updateBookmarkButton() {
        if (bookmarkBtn == null) return;
        Bookmark bookmark = store.findBookmark(book.id, currentBlock);
        bookmarkBtn.setTextColor(bookmark != null ? Ui.PRIMARY : Ui.MUTED);
        bookmarkBtn.setText(bookmark != null ? "🔖" : "🔖");
        bookmarkBtn.setAlpha(bookmark != null ? 1f : 0.55f);
    }

    private void saveProgress() {
        if (book == null || novel == null) return;
        book.lastBlock = currentBlock;
        book.lastChapter = chapterIndex;
        book.lastReadAt = System.currentTimeMillis();
        store.saveBook(book);
        store.setLastBookId(book.id);
    }

    // ---------------- 翻译 ----------------

    private List<Integer> pageBlocks() {
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        if (pages.isEmpty()) return new ArrayList<>();
        Page page = pages.get(pageIndex);
        for (int i = page.startBlock; i < page.endBlock; i++) {
            if (i >= 0 && i < novel.blocks.size()) set.add(i);
        }
        return new ArrayList<>(set);
    }

    private boolean pageAllTranslated() {
        for (Integer index : pageBlocks()) {
            if (!translations.has(index, novel.blocks.get(index).text)) return false;
        }
        return true;
    }

    private int pageTranslatedCount() {
        int count = 0;
        for (Integer index : pageBlocks()) {
            if (translations.has(index, novel.blocks.get(index).text)) count++;
        }
        return count;
    }

    private void onTranslateClick() {
        if (translating) {
            if (engine != null) engine.cancel();
            translating = false;
            translateSession++;
            updateBottomButtons();
            panelStatus.setText("已停止翻译，已完成内容已保留");
            Ui.toast(this, "已停止翻译");
            return;
        }
        translatePage(false);
    }

    private void translatePage(boolean force) {
        translatePage(force, false);
    }

    private void translatePage(boolean force, boolean fromAuto) {
        if (novel == null || pages.isEmpty()) return;
        if (translating) {
            translatePageCancel();
            return;
        }
        api = store.getActiveApi();
        prompt = store.getActivePrompt();
        if (api == null || api.baseUrl == null || api.baseUrl.trim().isEmpty()
                || api.model == null || api.model.trim().isEmpty()) {
            Ui.confirm(this, "需要配置 API", "还没有可用的翻译接口，请先添加 API 配置（支持硅基流动、A6API、DeepSeek、自定义等）。",
                    "去配置", () -> startActivity(new Intent(this, ApiProfilesActivity.class)));
            return;
        }
        if (apiNeedsKey(api)) {
            Ui.confirm(this, "缺少 API Key", "当前接口「" + api.name + "」没有填写 API Key。", "去填写",
                    () -> startActivity(new Intent(this, ApiProfilesActivity.class)));
            return;
        }
        if (prompt == null) {
            Ui.toast(this, "没有可用的翻译提示词，请先到设置中检查");
            return;
        }
        if (!force && pageAllTranslated()) {
            if (isBilingual()) {
                Ui.toast(this, "本页已经翻译过了，可用「重翻」重新翻译");
            } else {
                openPanel();
                Ui.toast(this, "本页已经翻译过了，可用「重翻」重新翻译");
            }
            return;
        }
        List<Integer> blocks = pageBlocks();
        if (blocks.isEmpty()) {
            Ui.toast(this, "本页没有可翻译的内容");
            return;
        }
        if (force) {
            // 先同步清掉旧译文，避免面板仍显示上一轮结果
            for (Integer index : blocks) translations.delete(index);
            if (isBilingual()) renderBilingualPage();
        }
        if (engine != null) engine.shutdown();
        final int session = ++translateSession;
        engine = new TranslationEngine(novel, translations, store, api, prompt, new TranslationEngine.Listener() {
            @Override
            public void onPageStart(int pageBlocks, int pendingBlocks) {
                main.post(() -> {
                    if (session != translateSession) return;
                    panelStatus.setText(pendingBlocks == 0 ? "本页译文已存在" : "准备翻译 " + pendingBlocks + " 段…");
                    bilingualNote = pendingBlocks == 0 ? "本页译文已存在" : "准备翻译 " + pendingBlocks + " 段…";
                    updateBilingualStatus();
                });
            }

            @Override
            public void onStatus(String status) {
                main.post(() -> {
                    if (session != translateSession) return;
                    panelStatus.setText(status);
                    bilingualNote = status;
                    updateBilingualStatus();
                });
            }

            @Override
            public void onPartial(int blockIndex, String text) {
                main.post(() -> {
                    if (session != translateSession) return;
                    blockPartials.put(blockIndex, text);
                    TextView view = cardViews.get(blockIndex);
                    if (view != null) {
                        view.setTextColor(store.getTextColor());
                        view.setText(text + "▍");
                    }
                    refreshBilingualBlock(blockIndex);
                });
            }

            @Override
            public void onBlockDone(int blockIndex, String text) {
                main.post(() -> {
                    if (session != translateSession) return;
                    blockPartials.remove(blockIndex);
                    TextView view = cardViews.get(blockIndex);
                    if (view != null) {
                        view.setTextColor(store.getTextColor());
                        view.setText(text);
                    }
                    updatePanelStats();
                    updateBottomButtons();
                    refreshBilingualBlock(blockIndex);
                });
            }

            @Override
            public void onProgress(int done, int total) {
                main.post(() -> {
                    if (session != translateSession) return;
                    panelStatus.setText("翻译中… " + done + " / " + total + " 段");
                    bilingualNote = "翻译中… " + done + " / " + total + " 段";
                    updateBilingualStatus();
                });
            }

            @Override
            public void onFinished(int done, int failed, boolean cancelled, String note) {
                main.post(() -> {
                    if (session != translateSession) return;
                    translating = false;
                    blockPartials.clear();
                    updateBottomButtons();
                    updatePanelStats();
                    renderPanelCards();
                    panelStatus.setText(note);
                    bilingualNote = note;
                    refreshAllBilingualRows();
                    if (failed > 0 && !cancelled) Ui.toast(ReaderActivity.this, note);
                });
            }
        });
        translating = true;
        boolean keepPanelHidden = isBilingual() || (fromAuto && panelUserClosed);
        openPanelIfNeeded(!keepPanelHidden);
        renderPanelCards();
        updateBottomButtons();
        panelStatus.setText("正在连接模型…");
        if (isBilingual()) {
            bilingualNote = "正在连接模型…";
            updateBilingualStatus();
        }
        engine.start(blocks, force);
    }

    private void openPanelIfNeeded(boolean show) {
        if (show) {
            openPanel();
        } else if (panelVisible) {
            panel.setVisibility(View.GONE);
            panelVisible = false;
        }
    }

    private boolean apiNeedsKey(ApiProfile profile) {
        if (profile.apiKey != null && !profile.apiKey.trim().isEmpty()) return false;
        String base = profile.baseUrl == null ? "" : profile.baseUrl.toLowerCase(Locale.US);
        if ("ollama".equals(profile.providerId)) return false;
        return !(base.contains("127.0.0.1") || base.contains("localhost")
                || base.contains("192.168.") || base.contains("10.") || base.contains("172.16."));
    }

    private void translatePageCancel() {
        if (engine != null) engine.cancel();
        translateSession++;
        translating = false;
        updateBottomButtons();
        panelStatus.setText("已停止翻译");
    }

    private void scheduleAutoTranslate() {
        if (!store.isAutoTranslate() || translating) return;
        if (pages.isEmpty()) return;
        if (pageAllTranslated()) {
            if (panelVisible) resetPanelForPage();
            return;
        }
        final int expectedPage = pageIndex;
        if (pendingAuto != null) main.removeCallbacks(pendingAuto);
        pendingAuto = () -> {
            if (expectedPage != pageIndex || translating) return;
            if (store.isAutoTranslate() && !pageAllTranslated()) translatePage(false, true);
        };
        main.postDelayed(pendingAuto, 280);
    }

    private void openPanel() {
        panelVisible = true;
        panelUserClosed = false;
        panel.setVisibility(View.VISIBLE);
        if (translating) panelStatus.setText("翻译中…");
        renderPanelCards();
        updatePanelStats();
        if (!translating && pageAllTranslated()) panelStatus.setText("本页已全部翻译");
    }

    private void closePanel() {
        panelVisible = false;
        panelUserClosed = true;
        panel.setVisibility(View.GONE);
    }

    private void updatePanelStats() {
        if (panelTitle == null) return;
        int count = pageBlocks().size();
        int done = pageTranslatedCount();
        panelTitle.setText("本页译文 · " + done + "/" + count + " 段");
        emptyPanel.setVisibility(count > 0 && done == 0 && !translating ? View.GONE : View.GONE);
    }

    private void renderPanelCards() {
        if (cardsBox == null) return;
        cardViews.clear();
        cardsBox.removeAllViews();
        List<Integer> blocks = pageBlocks();
        if (blocks.isEmpty()) {
            TextView empty = Ui.text(this, "本页没有可翻译的段落", 13, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, Ui.dp(this, 24), 0, Ui.dp(this, 24));
            cardsBox.addView(empty);
            return;
        }
        int number = 0;
        boolean darkTheme = isDark(store.getBgColor());
        int cardBg = darkTheme ? 0xFF2E2E2E : 0xFFFBF7EF;
        int cardStroke = darkTheme ? 0xFF3F3F3F : 0xFFEFE5D6;
        int labelColor = darkTheme ? 0xFFA8A29E : Ui.FAINT;
        int placeholderColor = darkTheme ? 0xFF8C8C8C : Ui.FAINT;
        for (Integer index : blocks) {
            number++;
            Novel.Block block = novel.blocks.get(index);
            String translation = translations.get(index, block.text);
            LinearLayout card = Ui.vertical(this);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
            card.setBackground(Ui.roundStroke(this, cardBg, 12, cardStroke, 1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, Ui.dp(this, 7));

            TextView label = Ui.text(this, "第 " + number + " 段" + (editMode ? " · 点按编辑" : ""), 9.5f, labelColor, false);
            card.addView(label);
            TextView body = Ui.text(this, translation != null ? translation : "（本段尚未翻译）",
                    14.5f, translation != null ? store.getTextColor() : placeholderColor, false);
            body.setLineSpacing(Ui.dp(this, 3), 1.5f);
            body.setPadding(0, Ui.dp(this, 3), 0, 0);
            card.addView(body);
            cardViews.put(index, body);

            final int blockIndex = index;
            card.setOnClickListener(v -> {
                if (editMode || translations.has(blockIndex, novel.blocks.get(blockIndex).text)) {
                    editBlockTranslation(blockIndex);
                }
            });
            card.setOnLongClickListener(v -> {
                editBlockTranslation(blockIndex);
                return true;
            });
            cardsBox.addView(card, cp);
        }
    }

    private void editBlockTranslation(final int blockIndex) {
        final Novel.Block block = novel.blocks.get(blockIndex);
        final String current = translations.get(blockIndex, block.text);
        String preview = TextUtil.shortTitle(block.text, 60);
        Ui.editDialog(this, "编辑译文 · " + preview, current == null ? "" : current, true,
                "输入该段译文，保存后立即生效并用于导出", text -> {
                    String value = text.trim();
                    if (value.isEmpty()) {
                        Ui.toast(this, "译文不能为空");
                        return;
                    }
                    translations.put(blockIndex, block.text, value);
                    TextView view = cardViews.get(blockIndex);
                    if (view != null) {
                        view.setText(value);
                        view.setTextColor(store.getTextColor());
                    }
                    if (isBilingual()) refreshBilingualBlock(blockIndex);
                    updatePanelStats();
                    updateBottomButtons();
                    Ui.toast(this, "译文已保存");
                });
    }

    private void toggleEditMode() {
        editMode = !editMode;
        panelEditBtn.setText(editMode ? "完成" : "编辑");
        panelEditBtn.setTextColor(editMode ? Ui.ACCENT_DARK : Ui.PRIMARY);
        renderPanelCards();
        if (editMode) Ui.toast(this, "点按任意段落即可编辑译文");
    }

    private void copyPageTranslation() {
        StringBuilder sb = new StringBuilder();
        for (Integer index : pageBlocks()) {
            String value = translations.get(index, novel.blocks.get(index).text);
            if (value != null) sb.append(value).append('\n').append('\n');
            else sb.append(novel.blocks.get(index).text).append('\n').append('\n');
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText("本页译文", sb.toString().trim()));
        Ui.toast(this, "本页译文已复制");
    }

    // ---------------- 页面操作 ----------------

    private void nextPage() {
        if (pages.isEmpty()) return;
        if (translating) translatePageCancel();
        if (pageIndex < pages.size() - 1) {
            pageIndex++;
            renderPage(true);
        } else if (chapterIndex + 1 < novel.chapters.size()) {
            jumpChapter(chapterIndex + 1);
            Ui.toast(this, "进入下一章");
        } else {
            Ui.toast(this, "已经是全书最后一页");
        }
    }

    private void prevPage() {
        if (pages.isEmpty()) return;
        if (translating) translatePageCancel();
        if (pageIndex > 0) {
            pageIndex--;
            renderPage(true);
        } else if (chapterIndex > 0) {
            jumpChapter(chapterIndex - 1);
            pageIndex = pages.size() - 1;
            renderPage(true);
        } else {
            Ui.toast(this, "已经是全书第一页");
        }
    }

    private void jumpChapter(int index) {
        Novel.Chapter target = novel.chapter(index);
        if (target == null) return;
        if (translating) translatePageCancel();
        chapterIndex = target.index;
        chapter = target;
        currentBlock = target.startBlock;
        paginate();
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        setUiVisible(barsVisible);
    }

    private void setUiVisible(boolean visible) {
        if (topBar != null) topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (bottomBar != null) bottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (panelVisible && panel != null) {
            // 面板保持显示，但跟随底部栏留出空间
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) panel.getLayoutParams();
            params.bottomMargin = visible ? Ui.dp(this, 66) : Ui.dp(this, 8);
            panel.setLayoutParams(params);
        }
    }

    private void finishReader() {
        saveProgress();
        if (engine != null) engine.cancel();
        finish();
    }

    private void toggleFavorite() {
        book.favorite = !book.favorite;
        store.saveBook(book);
        updateFavoriteButton();
        Ui.toast(this, book.favorite ? "已加入收藏夹" : "已取消收藏");
    }

    private void toggleBookmark() {
        Bookmark existing = store.findBookmark(book.id, currentBlock);
        if (existing != null) {
            store.deleteBookmark(existing.id);
            Ui.toast(this, "已移除本页书签");
        } else {
            Bookmark bookmark = new Bookmark();
            bookmark.bookId = book.id;
            bookmark.bookTitle = book.title;
            bookmark.blockIndex = currentBlock;
            bookmark.chapterIndex = chapterIndex;
            bookmark.chapterTitle = chapter == null ? "" : chapter.title;
            bookmark.percent = novel.percentOfBlock(currentBlock);
            String pagePreview = buildPageText(pages.get(pageIndex)).replace('\n', ' ').trim();
            bookmark.snippet = TextUtil.shortTitle(pagePreview, 46);
            store.saveBookmark(bookmark);
            Ui.toast(this, "已添加书签");
        }
        updateBookmarkButton();
    }

    // ---------------- 菜单 / 目录 / 设置 ----------------

    private void showMenuSheet() {
        final String[] items = {
                "目录", "书签列表", "翻译设置", "阅读设置", "导出译文",
                "复制本页译文", "切换译文语种", "切换原文语种",
                "显示/隐藏本页译文", "重翻本页",
                isBilingual() ? "切换为原文模式" : "切换为双语对照模式（原文在上·译文在下）",
                book.favorite ? "取消收藏" : "加入收藏夹"
        };
        Ui.sheet(this, "阅读菜单", items, index -> {
            switch (index) {
                case 0: showToc(); break;
                case 1: startActivity(new Intent(this, BookmarksActivity.class)); break;
                case 2: startActivity(new Intent(this, SettingsActivity.class)); break;
                case 3: showQuickSettings(); break;
                case 4: startExportFlow(); break;
                case 5: copyPageTranslation(); break;
                case 6: chooseTargetLang(); break;
                case 7: chooseSourceLang(); break;
                case 8:
                    if (panelVisible) closePanel();
                    else openPanel();
                    break;
                case 9: translatePage(true); break;
                case 10: toggleReadingMode(); break;
                case 11: toggleFavorite(); break;
                default: break;
            }
        });
    }

    /** 在“原文模式”和“双语对照（原文在上、译文在下）”之间切换。 */
    private void toggleReadingMode() {
        boolean toBilingual = !isBilingual();
        store.setReadingMode(toBilingual ? "bilingual" : "original");
        if (panelVisible) closePanel();
        applyModeVisibility();
        renderPage(true);
        Ui.toast(this, toBilingual ? "已切换到双语对照：原文在上，译文在下" : "已切换到原文模式");
    }

    private void showToc() {
        if (novel == null || novel.chapters.isEmpty()) return;
        String[] items = new String[novel.chapters.size()];
        for (int i = 0; i < novel.chapters.size(); i++) {
            Novel.Chapter chapter = novel.chapters.get(i);
            items[i] = (i == chapterIndex ? "▶ " : "   ") + (i + 1) + ". " + chapter.title;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("目录 · 共 " + items.length + " 章");
        builder.setItems(items, (dialog, which) -> jumpChapter(which));
        builder.setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            if (dialog.getListView() != null) {
                dialog.getListView().setSelection(Math.max(0, chapterIndex - 3));
            }
        });
        dialog.show();
    }

    private void chooseTargetLang() {
        Ui.listDialog(this, "译文语种", Lang.targetLabels(), Lang.targetIndex(store.getTargetLang()),
                index -> {
                    String code = Lang.targetCodes()[index];
                    store.setTargetLang(code);
                    Ui.toast(this, "译文语种：" + Lang.name(code) + "（已译内容不受影响，可重翻本页）");
                });
    }

    private void chooseSourceLang() {
        Ui.listDialog(this, "原文语种", Lang.sourceLabels(), Lang.sourceIndex(store.getSourceLang()),
                index -> {
                    String code = Lang.sourceCodes()[index];
                    store.setSourceLang(code);
                    Ui.toast(this, "原文语种：" + Lang.name(code));
                });
    }

    private void showQuickSettings() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.vertical(this);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 16));
        box.setBackground(Ui.round(this, Ui.SURFACE, 22));

        View handle = new View(this);
        handle.setBackground(Ui.round(this, 0xFFD8CFC0, 99));
        LinearLayout.LayoutParams hp = Ui.lp(Ui.dp(this, 38), Ui.dp(this, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Ui.dp(this, 12);
        box.addView(handle, hp);

        TextView title = Ui.text(this, "阅读设置", 16, Ui.TEXT, true);
        box.addView(title);

        LinearLayout fontRow = Ui.horizontal(this);
        fontRow.setPadding(0, Ui.dp(this, 12), 0, 0);
        TextView fontLabel = Ui.text(this, "字号  " + store.getFontSize() + "sp", 13.5f, Ui.TEXT, false);
        fontRow.addView(fontLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView minus = Ui.smallButton(this, "A−", Ui.TEXT, () -> {
            store.setFontSize(store.getFontSize() - 1);
            fontLabel.setText("字号  " + store.getFontSize() + "sp");
            applyStyleAndRepaginate();
        });
        fontRow.addView(minus);
        TextView plus = Ui.smallButton(this, "A＋", Ui.TEXT, () -> {
            store.setFontSize(store.getFontSize() + 1);
            fontLabel.setText("字号  " + store.getFontSize() + "sp");
            applyStyleAndRepaginate();
        });
        LinearLayout.LayoutParams plusParams = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plusParams.leftMargin = Ui.dp(this, 6);
        fontRow.addView(plus, plusParams);
        box.addView(fontRow);

        TextView bgLabel = Ui.label(this, "背景颜色");
        box.addView(bgLabel);
        final int[] bgPresets = {0xFFF6F1E7, 0xFFFFFFFF, 0xFFE8F0E3, 0xFF2B2B2B, 0xFF000000, 0xFF1E293B};
        box.addView(swatchRow(bgPresets, store.getBgColor(), index -> {
            int color = bgPresets[index];
            store.setBgColor(color);
            if (isDark(color) && !isDark(store.getTextColor())) store.setTextColor(0xFFEDE9E3);
            else if (!isDark(color) && isDark(store.getTextColor())) store.setTextColor(0xFF2B2118);
            applyStyleAndRepaginate();
            dialog.dismiss();
            main.postDelayed(this::showQuickSettings, 120);
        }));

        TextView textColorLabel = Ui.label(this, "文字颜色");
        box.addView(textColorLabel);
        final int[] textPresets = {0xFF2B2118, 0xFF000000, 0xFF3F3F46, 0xFF1E3A5F,
                0xFF7C2D12, 0xFFEDE9E3, 0xFFF5F5F4, 0xFF93C5FD};
        box.addView(swatchRow(textPresets, store.getTextColor(), index -> {
            store.setTextColor(textPresets[index]);
            applyStyleAndRepaginate();
            dialog.dismiss();
            main.postDelayed(this::showQuickSettings, 120);
        }));

        LinearLayout lineRow = Ui.horizontal(this);
        lineRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView lineLabel = Ui.text(this, "行距  " + String.format(Locale.US, "%.2f", store.getLineSpacing()), 13.5f, Ui.TEXT, false);
        lineRow.addView(lineLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView lineMinus = Ui.smallButton(this, "−", Ui.TEXT, () -> {
            store.setLineSpacing(store.getLineSpacing() - 0.05f);
            lineLabel.setText("行距  " + String.format(Locale.US, "%.2f", store.getLineSpacing()));
            applyStyleAndRepaginate();
        });
        lineRow.addView(lineMinus);
        TextView linePlus = Ui.smallButton(this, "＋", Ui.TEXT, () -> {
            store.setLineSpacing(store.getLineSpacing() + 0.05f);
            lineLabel.setText("行距  " + String.format(Locale.US, "%.2f", store.getLineSpacing()));
            applyStyleAndRepaginate();
        });
        LinearLayout.LayoutParams linePlusParams = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linePlusParams.leftMargin = Ui.dp(this, 6);
        lineRow.addView(linePlus, linePlusParams);
        box.addView(lineRow);

        Switch paragraphSwitch = Ui.toggle(this, store.isParagraphSpacing());
        LinearLayout paragraphRow = Ui.settingRow(this, "段间距（空一行）", "关闭后段落更紧凑", paragraphSwitch);
        paragraphSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setParagraphSpacing(isChecked);
            applyStyleAndRepaginate();
        });
        box.addView(paragraphRow);

        Switch indentSwitch = Ui.toggle(this, store.isIndent());
        LinearLayout indentRow = Ui.settingRow(this, "首行缩进", "每段开头缩进两个字符", indentSwitch);
        indentSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setIndent(isChecked);
            applyStyleAndRepaginate();
        });
        box.addView(indentRow);

        Switch bilingualSwitch = Ui.toggle(this, isBilingual());
        LinearLayout bilingualRow = Ui.settingRow(this, "双语对照",
                "原文在上 · 译文在下，译文流式回填，方便逐段核对质量", bilingualSwitch);
        bilingualSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setReadingMode(isChecked ? "bilingual" : "original");
            dialog.dismiss();
            if (panelVisible) closePanel();
            applyModeVisibility();
            renderPage(true);
            Ui.toast(this, isChecked ? "已开启双语对照模式" : "已切换回原文模式");
        });
        box.addView(bilingualRow);

        LinearLayout actions = Ui.horizontal(this);
        actions.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView more = Ui.ghostButton(this, "更多设置", () -> {
            dialog.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        actions.addView(more, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView done = Ui.primaryButton(this, "完成", dialog::dismiss);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        doneParams.leftMargin = Ui.dp(this, 10);
        actions.addView(done, doneParams);
        box.addView(actions);

        dialog.setContentView(box);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.4f);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private LinearLayout swatchRow(int[] colors, int current, Ui.OnIndex onIndex) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            View dot = new View(this);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(colors[i]);
            drawable.setStroke(Ui.dp(this, colors[i] == current ? 3 : 1),
                    colors[i] == current ? Ui.PRIMARY : 0xFFD6CCBC);
            dot.setBackground(drawable);
            dot.setOnClickListener(v -> onIndex.onIndex(index));
            LinearLayout.LayoutParams params = Ui.lp(Ui.dp(this, 34), Ui.dp(this, 34));
            params.rightMargin = Ui.dp(this, 12);
            row.addView(dot, params);
        }
        return row;
    }

    private void applyStyleAndRepaginate() {
        applyReaderStyle();
        if (pendingPaginate != null) main.removeCallbacks(pendingPaginate);
        pendingPaginate = this::paginate;
        main.postDelayed(pendingPaginate, 120);
    }

    private void applyReaderStyle() {
        if (root == null) return;
        int bg = store.getBgColor();
        root.setBackgroundColor(bg);
        pageColumn.setBackgroundColor(bg);
        pageText.setBackgroundColor(bg);
        pageText.setTextSize(store.getFontSize());
        pageText.setTextColor(store.getTextColor());
        pageText.setLineSpacing(0, store.getLineSpacing());
        String family = store.getFontFamily();
        if ("sans".equals(family)) pageText.setTypeface(Typeface.SANS_SERIF);
        else if ("mono".equals(family)) pageText.setTypeface(Typeface.MONOSPACE);
        else pageText.setTypeface(Typeface.SERIF);
        chapterLabel.setTextColor(Ui.alpha(store.getTextColor(), 150));
        pageLabel.setTextColor(Ui.alpha(store.getTextColor(), 150));
        prevLabel.setTextColor(Ui.alpha(store.getTextColor(), 150));
        nextLabel.setTextColor(Ui.alpha(store.getTextColor(), 150));
        if (bilingualStatus != null) {
            bilingualStatus.setTextColor(Ui.alpha(store.getTextColor(), 170));
            bilingualContainer.setBackgroundColor(bg);
        }
        applyModeVisibility();
        updateBarStyle();
        updatePageLabels();
        if (isBilingual() && !pages.isEmpty()) {
            renderBilingualPage();
        }
        if (panelVisible) {
            renderPanelCards();
            updatePanelStats();
        }
    }

    private void updateBarStyle() {
        boolean dark = isDark(store.getBgColor());
        int barColor = dark ? 0xEE1C1C1C : 0xF7FFFFFF;
        int textColor = dark ? 0xFFEDE9E3 : Ui.TEXT;
        int muted = dark ? 0xFFA8A29E : Ui.MUTED;
        if (topBar != null) {
            topBar.setBackgroundColor(barColor);
            for (int i = 0; i < topBar.getChildCount(); i++) {
                View child = topBar.getChildAt(i);
                if (!(child instanceof TextView)) continue;
                TextView tv = (TextView) child;
                if (i == 1) tv.setTextColor(textColor);
                else if (i == 0 || i == 4) tv.setTextColor(muted);
                // 收藏与书签按钮颜色由 updateFavoriteButton / updateBookmarkButton 维护
            }
        }
        if (bottomBar != null) {
            bottomBar.setBackgroundColor(barColor);
            for (int i = 0; i < bottomBar.getChildCount(); i++) {
                View child = bottomBar.getChildAt(i);
                if (child instanceof LinearLayout) {
                    LinearLayout item = (LinearLayout) child;
                    for (int j = 0; j < item.getChildCount(); j++) {
                        if (item.getChildAt(j) instanceof TextView) {
                            TextView tv = (TextView) item.getChildAt(j);
                            tv.setTextColor(j == 0 ? textColor : muted);
                        }
                    }
                }
            }
            updateAutoButton();
        }
        if (panel != null) {
            panel.setBackground(Ui.round(this, dark ? 0xFF232323 : Ui.SURFACE, 20));
            panelTitle.setTextColor(textColor);
            panelStatus.setTextColor(muted);
            emptyPanel.setTextColor(muted);
        }
        Ui.statusBar(this, store.getBgColor(), !dark, barColor, !dark);
    }

    private boolean isDark(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance < 0.45;
    }

    // ---------------- 导出 ----------------

    private void startExportFlow() {
        if (novel == null) return;
        final int missing = Exporter.countMissing(novel, (index, original) -> translations.get(index, original));
        String stats = Exporter.exportStats(novel, (index, original) -> translations.get(index, original));
        String message = stats + "\n\n导出文件会按原文的段落、空行和章节结构生成。"
                + (missing > 0 ? "\n未翻译的 " + missing + " 段将保留原文。" : "");
        new AlertDialog.Builder(this)
                .setTitle("导出译文")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("仅译文", (dialog, which) -> beginExport(0))
                .setNeutralButton("中英双语", (dialog, which) -> beginExport(1))
                .show();
    }

    private void beginExport(int mode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, BookRepository.suggestExportName(book, store.getTargetLang()));
        try {
            startActivityForResult(intent, REQ_EXPORT + mode);
        } catch (Exception e) {
            Ui.toast(this, "无法打开保存位置选择器：" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode >= REQ_EXPORT && requestCode <= REQ_EXPORT + 1 && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            writeExport(data.getData(), requestCode - REQ_EXPORT);
        }
    }

    private void writeExport(Uri uri, int mode) {
        final LinearLayout overlay = showLoadingOverlay("正在生成译文文件…");
        io.execute(() -> {
            try {
                String text = Exporter.build(novel, (index, original) -> translations.get(index, original),
                        mode, true);
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new java.io.IOException("无法写入文件");
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
                main.post(() -> {
                    if (overlay.getParent() != null) root.removeView(overlay);
                    Ui.showInfo(this, "导出完成", "文件已保存。\n" + Exporter.exportStats(novel,
                            (index, original) -> translations.get(index, original))
                            + "\n格式：" + (mode == 1 ? "中英双语对照" : "仅译文（保持原文段落结构）"));
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (overlay.getParent() != null) root.removeView(overlay);
                    Ui.showInfo(this, "导出失败", e.getMessage() == null ? "写入失败" : e.getMessage());
                });
            }
        });
    }

    // ---------------- 其他 ----------------

    private void showLoadingOverlay() {
        showLoading();
    }

    private void showLoading() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(store.getBgColor());
        ProgressBar bar = Ui.progress(this, 38);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.CENTER;
        overlay.addView(bar, bp);
        TextView label = Ui.text(this, "正在载入小说…", 13, Ui.MUTED, false);
        label.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        lp.topMargin = Ui.dp(this, 64);
        overlay.addView(label, lp);
        overlay.setTag("loading");
        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout showLoadingOverlay(String message) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if ("loading".equals(child.getTag())) {
                root.removeView(child);
                break;
            }
        }
        LinearLayout box = Ui.vertical(this);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xCC12100E);
        box.setClickable(true);
        ProgressBar bar = Ui.progress(this, 38);
        box.addView(bar);
        TextView label = Ui.text(this, message, 13, 0xFFFFFFFF, false);
        label.setPadding(0, Ui.dp(this, 12), 0, 0);
        label.setGravity(Gravity.CENTER);
        box.addView(label);
        box.setTag("loading");
        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return box;
    }

    private void showFirstRunHint() {
        if (initialHintShown) return;
        initialHintShown = true;
        android.content.SharedPreferences prefs = getSharedPreferences("yidu_ui", MODE_PRIVATE);
        if (prefs.getBoolean("reader_hint_v1", false)) return;
        prefs.edit().putBoolean("reader_hint_v1", true).apply();
        main.postDelayed(() -> {
            if (isFinishing()) return;
            Ui.sheet(this, "阅读提示", new String[]{
                    "点击屏幕左侧 / 右侧：上一页 / 下一页",
                    "点击屏幕中间：显示或隐藏菜单",
                    "底部「翻译」：流式翻译本页",
                    "底部「重翻」：丢弃本页译文重新翻译",
                    "底部「自动」：翻页后自动翻译",
                    "底部「Aa」：字号 / 背景 / 文字颜色"
            }, index -> {
            });
        }, 500);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            nextPage();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            prevPage();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (panelVisible) {
            closePanel();
            return;
        }
        if (!barsVisible) {
            barsVisible = true;
            setUiVisible(true);
            return;
        }
        finishReader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (novel == null) return;
        String signature = styleSignature();
        if (!signature.equals(styleSignature)) {
            styleSignature = signature;
            applyReaderStyle();
            applyStyleAndRepaginate();
        }
        updateFavoriteButton();
        updateBookmarkButton();
        updateAutoButton();
    }

    private String styleSignature() {
        return store.getFontSize() + "|" + store.getFontFamily() + "|" + store.getTextColor()
                + "|" + store.getBgColor() + "|" + store.getLineSpacing() + "|"
                + store.isParagraphSpacing() + "|" + store.isIndent()
                + "|" + store.getReadingMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.shutdown();
        if (pendingPaginate != null) main.removeCallbacks(pendingPaginate);
        if (pendingAuto != null) main.removeCallbacks(pendingAuto);
        io.shutdownNow();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        main.postDelayed(this::applyStyleAndRepaginate, 150);
    }
}
