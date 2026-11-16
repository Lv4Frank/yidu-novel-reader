package com.yidu.novel;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yidu.novel.store.AppStore;
import com.yidu.novel.util.Ui;

public abstract class BaseActivity extends Activity {
    protected AppStore store;
    protected LinearLayout root;
    protected LinearLayout bodyArea;
    protected FrameLayout bodyHost;
    protected LinearLayout footerHost;
    protected TextView titleView;
    protected TextView subtitleView;
    protected TextView topAction;
    protected TextView backView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = App.store(this);
        Ui.statusBar(this, Ui.SURFACE, true, Ui.SURFACE, true);

        root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = Ui.horizontal(this);
        header.setBackgroundColor(Ui.SURFACE);
        header.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        header.setElevation(Ui.dp(this, 1));

        backView = Ui.text(this, "‹", 30, Ui.TEXT, false);
        backView.setGravity(Gravity.CENTER);
        backView.setBackground(Ui.ripple(Ui.round(this, Ui.SURFACE_ALT, 99), 0x1A0F172A));
        backView.setClickable(true);
        backView.setContentDescription("返回");
        backView.setOnClickListener(v -> onBackPressed());
        header.addView(backView, Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40)));

        LinearLayout titles = Ui.vertical(this);
        titles.setPadding(Ui.dp(this, 10), 0, 0, 0);
        titleView = Ui.text(this, "", 17.5f, Ui.TEXT, true);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleView.setSingleLine(true);
        subtitleView = Ui.text(this, "", 11.5f, Ui.MUTED, false);
        subtitleView.setSingleLine(true);
        subtitleView.setVisibility(View.GONE);
        titles.addView(titleView);
        titles.addView(subtitleView);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        topAction = Ui.text(this, "", 15, Ui.PRIMARY, true);
        topAction.setGravity(Gravity.CENTER);
        topAction.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        topAction.setVisibility(View.GONE);
        topAction.setClickable(true);
        topAction.setMinWidth(Ui.dp(this, 52));
        header.addView(topAction, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40)));

        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(Ui.divider(this));

        bodyArea = Ui.vertical(this);
        bodyArea.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        bodyHost = new FrameLayout(this);
        bodyHost.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        bodyArea.addView(bodyHost);
        footerHost = Ui.vertical(this);
        footerHost.setVisibility(View.GONE);
        bodyArea.addView(footerHost);
        root.addView(bodyArea);

        setContentView(root);
    }

    protected void setPage(String title, String subtitle, View body, boolean scroll) {
        titleView.setText(title);
        if (subtitle == null || subtitle.length() == 0) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        bodyHost.removeAllViews();
        if (scroll) {
            ScrollView scrollView = Ui.scroll(this, body);
            bodyHost.addView(scrollView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            bodyHost.addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    protected void setFooter(View footer) {
        footerHost.removeAllViews();
        if (footer == null) {
            footerHost.setVisibility(View.GONE);
            return;
        }
        footerHost.setVisibility(View.VISIBLE);
        footerHost.setBackgroundColor(Ui.SURFACE);
        footerHost.setElevation(Ui.dp(this, 8));
        footerHost.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 14));
        footerHost.addView(footer);
    }

    protected void setTopAction(String text, Runnable action) {
        if (text == null) {
            topAction.setVisibility(View.GONE);
            return;
        }
        topAction.setText(text);
        topAction.setVisibility(View.VISIBLE);
        topAction.setOnClickListener(v -> action.run());
    }

    protected void setSubtitle(String subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
