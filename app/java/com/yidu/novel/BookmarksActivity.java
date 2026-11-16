package com.yidu.novel;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yidu.novel.model.Bookmark;
import com.yidu.novel.util.Ui;

import java.util.List;

/** 书签列表：全部书签，点击回到阅读位置，可单条删除。 */
public class BookmarksActivity extends BaseActivity {
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
        // 从阅读页返回后可能有新增或删除，刷新列表。
        render();
    }

    private void render() {
        List<Bookmark> bookmarks = store.getBookmarks();
        LinearLayout body = Ui.vertical(this);
        if (bookmarks.isEmpty()) {
            body.addView(emptyView());
        } else {
            body.addView(Ui.hint(this, "点击书签即可回到对应的阅读位置。"));
            for (Bookmark bookmark : bookmarks) {
                body.addView(bookmarkCard(bookmark));
            }
        }
        setPage("书签", bookmarks.isEmpty() ? "还没有书签" : "共 " + bookmarks.size() + " 个书签",
                body, true);
        setTopAction(null, null);
        setFooter(null);
    }

    private View emptyView() {
        LinearLayout wrap = Ui.vertical(this);
        wrap.setPadding(dp(16), dp(48), dp(16), dp(16));

        TextView icon = Ui.text(this, "☆", 44, Ui.PRIMARY, false);
        icon.setGravity(Gravity.CENTER);
        wrap.addView(icon);

        TextView title = Ui.text(this, "还没有书签，阅读时点击 🔖 添加", 15, Ui.MUTED, false);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(12), 0, 0);
        wrap.addView(title);

        TextView hint = Ui.hint(this, "书签会记录你读到的位置，方便随时回来继续阅读。");
        hint.setGravity(Gravity.CENTER);
        wrap.addView(hint);
        return wrap;
    }

    private View bookmarkCard(Bookmark bookmark) {
        LinearLayout card = Ui.card(this);

        LinearLayout head = Ui.horizontal(this);
        String bookTitle = bookmark.bookTitle == null || bookmark.bookTitle.isEmpty()
                ? "未知书籍" : bookmark.bookTitle;
        head.addView(Ui.text(this, bookTitle, 15.5f, Ui.TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(Ui.badge(this, Math.max(0, Math.min(100, bookmark.percent)) + "%",
                0xFFF5F0E7, Ui.PRIMARY_DARK));
        card.addView(head);

        String chapter = bookmark.chapterTitle == null || bookmark.chapterTitle.isEmpty()
                ? "第 " + (bookmark.chapterIndex + 1) + " 章" : bookmark.chapterTitle;
        TextView chapterView = Ui.text(this, chapter, 12.5f, Ui.PRIMARY_DARK, false);
        chapterView.setPadding(0, dp(5), 0, 0);
        card.addView(chapterView);

        if (bookmark.snippet != null && !bookmark.snippet.trim().isEmpty()) {
            TextView snippet = Ui.text(this, Ui.ellipsize(bookmark.snippet.trim(), 120),
                    12.5f, Ui.MUTED, false);
            snippet.setMaxLines(2);
            snippet.setEllipsize(TextUtils.TruncateAt.END);
            snippet.setLineSpacing(dp(2), 1.0f);
            snippet.setPadding(0, dp(7), 0, 0);
            card.addView(snippet);
        }

        LinearLayout actions = Ui.horizontal(this);
        actions.setPadding(0, dp(10), 0, 0);
        actions.addView(Ui.smallButton(this, "继续阅读", Ui.PRIMARY, () -> openBookmark(bookmark)));
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        deleteLp.setMargins(dp(6), 0, 0, 0);
        actions.addView(Ui.smallButton(this, "删除", Ui.DANGER, () -> confirmDelete(bookmark)), deleteLp);
        card.addView(actions);

        card.setOnClickListener(v -> openBookmark(bookmark));
        return card;
    }

    private void openBookmark(Bookmark bookmark) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("book_id", bookmark.bookId);
        intent.putExtra("start_block", bookmark.blockIndex);
        startActivity(intent);
    }

    private void confirmDelete(Bookmark bookmark) {
        Ui.confirm(this, "删除书签",
                "确定删除这条书签？此操作不可恢复。",
                "删除", () -> {
                    store.deleteBookmark(bookmark.id);
                    Ui.toast(this, "已删除书签");
                    render();
                });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
