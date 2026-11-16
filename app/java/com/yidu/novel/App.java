package com.yidu.novel;

import android.app.Application;

import com.yidu.novel.store.AppStore;

public class App extends Application {
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static AppStore store(android.content.Context context) {
        return AppStore.get(context);
    }

    public static App get() {
        return instance;
    }
}
