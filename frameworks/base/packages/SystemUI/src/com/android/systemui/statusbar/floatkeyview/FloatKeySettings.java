
package com.android.systemui.statusbar;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.view.KeyEvent;

/**
 * SPRD: add for assistant touch
 */
public class FloatKeySettings {
    public static final int DEFAULT_SHORTCUT_TOP = KeyEvent.KEYCODE_SEARCH;
    public static final int DEFAULT_SHORTCUT_BOTTOM = KeyEvent.KEYCODE_HOME;
    public static final int DEFAULT_SHORTCUT_LEFT = KeyEvent.KEYCODE_BACK;
    public static final int DEFAULT_SHORTCUT_RIGHT = KeyEvent.KEYCODE_MENU;

    public static final String DEFAULT_APP_TOP = "com.android.music:com.android.music.MusicBrowserActivity";
    public static final String DEFAULT_APP_BOTTOM = "com.android.contacts:com.android.contacts.activities.PeopleActivity";
    public static final String DEFAULT_APP_LEFT = "com.android.browser:com.android.browser.BrowserActivity";
    public static final String DEFAULT_APP_RIGHT = "com.android.mms:com.android.mms.ui.ConversationList";

    private ContentResolver mContentResolver;

    public FloatKeySettings(Context context) {
        this.mContentResolver = context.getContentResolver();
    }

    public int getAssistantStatus() {
        return getIntSecure(Settings.Secure.ASSISTANT_ON, 0);
    }

    public int getShortcutTopValue() {
        return getIntSecure(Settings.Secure.ASSISTANT_SHORTCUT_TOP, DEFAULT_SHORTCUT_TOP);
    }

    public int getShortcutBottomValue() {
        return getIntSecure(Settings.Secure.ASSISTANT_SHORTCUT_BOTTOM, DEFAULT_SHORTCUT_BOTTOM);
    }

    public int getShortcutLeftValue() {
        return getIntSecure(Settings.Secure.ASSISTANT_SHORTCUT_LEFT, DEFAULT_SHORTCUT_LEFT);
    }

    public int getShortcutRightValue() {
        return getIntSecure(Settings.Secure.ASSISTANT_SHORTCUT_RIGHT, DEFAULT_SHORTCUT_RIGHT);
    }

    public String getAppTopValue() {
        return getStringSecure(Settings.Secure.ASSISTANT_APP_TOP, DEFAULT_APP_TOP);
    }

    public String getAppBottomValue() {
        return getStringSecure(Settings.Secure.ASSISTANT_APP_BOTTOM, DEFAULT_APP_BOTTOM);
    }

    public String getAppLeftValue() {
        return getStringSecure(Settings.Secure.ASSISTANT_APP_LEFT, DEFAULT_APP_LEFT);
    }

    public String getAppRightValue() {
        return getStringSecure(Settings.Secure.ASSISTANT_APP_RIGHT, DEFAULT_APP_RIGHT);
    }

    private int getIntSecure(String name, int def) {
        return Settings.Secure.getIntForUser(mContentResolver, name, def, ActivityManager.getCurrentUser());
    }

    private String getStringSecure(String name, String def) {
        String ret = Settings.Secure.getStringForUser(mContentResolver, name, ActivityManager.getCurrentUser());
        return ret == null ? def : ret;
    }
}
