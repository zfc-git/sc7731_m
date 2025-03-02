/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.browser;

import android.R.bool;
import android.R.integer;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Browser;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebIconDatabase;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebSettings.TextSize;
import android.webkit.WebSettings.ZoomDensity;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;
import android.widget.Toast;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import com.android.browser.homepages.HomeProvider;
import com.android.browser.provider.BrowserProvider;
import com.android.browser.search.SearchEngine;
import com.android.browser.search.SearchEngineInfo;
import com.android.browser.search.SearchEngines;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.WeakHashMap;
import android.util.Log;
import com.sprd.useragent.SprdBrowserUserAgentAddonStub;
import android.os.Environment;
import android.content.ContentValues;
import android.net.Uri;
import com.android.browser.util.Util;
import java.io.File;
import android.database.sqlite.SQLiteException;

/**
 * Class for managing settings
 */
public class BrowserSettings implements OnSharedPreferenceChangeListener,
        PreferenceKeys {

    // TODO: Do something with this UserAgent stuff
    private static final String DESKTOP_USERAGENT = "Mozilla/5.0 (X11; " +
        "Linux x86_64) AppleWebKit/534.24 (KHTML, like Gecko) " +
        "Chrome/11.0.696.34 Safari/534.24";

    private static final String USER_AGENTS[] = { null,
            DESKTOP_USERAGENT,
    };

    // The minimum min font size
    // Aka, the lower bounds for the min font size range
    // which is 1:5..24
    private static final int MIN_FONT_SIZE_OFFSET = 5;
    // The initial value in the text zoom range
    // This is what represents 100% in the SeekBarPreference range
    private static final int TEXT_ZOOM_START_VAL = 10;
    // The size of a single step in the text zoom range, in percent
    private static final int TEXT_ZOOM_STEP = 5;
    // The initial value in the double tap zoom range
    // This is what represents 100% in the SeekBarPreference range
    private static final int DOUBLE_TAP_ZOOM_START_VAL = 5;
    // The size of a single step in the double tap zoom range, in percent
    private static final int DOUBLE_TAP_ZOOM_STEP = 5;

    private static BrowserSettings sInstance;

    private Context mContext;
    private SharedPreferences mPrefs;
    private LinkedList<WeakReference<WebSettings>> mManagedSettings;
    private Controller mController;
    private WebStorageSizeManager mWebStorageSizeManager;
    private WeakHashMap<WebSettings, String> mCustomUserAgents;
    private static boolean sInitialized = false;
    private boolean mNeedsSharedSync = true;
    private float mFontSizeMult = 1.0f;

    // Current state of network-dependent settings
    private boolean mLinkPrefetchAllowed = true;

    // Cached values
    private int mPageCacheCapacity = 1;
    private String mAppCachePath;
    /**
     * Add for Browser UA
     *@{
    */
    private final static String LOGTAG = "browser";
    private final static String UA_TAG = "UA";
    /*@}*/

    /**
     * Add for text format
     *@{
    */
    private static String textFormat = "sans-serif";
    /*@}*/

    /**
     *Add for setting homepage via MCC\MNC
     *@{
     */
    private final static String HOMEPAGE_CARRIER = "carrier_homepage";
    /*@}*/

    // Cached settings
    private SearchEngine mSearchEngine;

    private static String sFactoryResetUrl;
    private final static String DATABASE_SEARCH_ENGINE = "myengine";
    private static String sPasswordPath = "/data/data/com.android.browser/databases/password.db";

    public static void initialize(final Context context) {
        sInstance = new BrowserSettings(context);
    }

    public static BrowserSettings getInstance() {
        return sInstance;
    }

    private BrowserSettings(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mManagedSettings = new LinkedList<WeakReference<WebSettings>>();
        mCustomUserAgents = new WeakHashMap<WebSettings, String>();
        BackgroundHandler.execute(mSetup);
    }

    public void setController(Controller controller) {
        mController = controller;
        if (sInitialized) {
            syncSharedSettings();
        }

        /**
         *Add for setting homepage via MCC\MNC
         *@{
         */
        setHomePage();
        /*@}*/
    }

    public void startManagingSettings(WebSettings settings) {

        if (mNeedsSharedSync) {
            syncSharedSettings();
        }

        synchronized (mManagedSettings) {
            syncStaticSettings(settings);
            syncSetting(settings);
            mManagedSettings.add(new WeakReference<WebSettings>(settings));
        }
    }

    public void stopManagingSettings(WebSettings settings) {
        Iterator<WeakReference<WebSettings>> iter = mManagedSettings.iterator();
        while (iter.hasNext()) {
            WeakReference<WebSettings> ref = iter.next();
            if (ref.get() == settings) {
                iter.remove();
                return;
            }
        }
    }

    private Runnable mSetup = new Runnable() {

        @Override
        public void run() {
            /* scaledDensity do not affact font size in browser
            DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
            mFontSizeMult = metrics.scaledDensity / metrics.density;
            */
            // the cost of one cached page is ~3M (measured using nytimes.com). For
            // low end devices, we only cache one page. For high end devices, we try
            // to cache more pages, currently choose 5.
            if (ActivityManager.staticGetMemoryClass() > 16) {
                mPageCacheCapacity = 5;
            }
            mWebStorageSizeManager = new WebStorageSizeManager(mContext,
                    new WebStorageSizeManager.StatFsDiskInfo(getAppCachePath()),
                    new WebStorageSizeManager.WebKitAppCacheInfo(getAppCachePath()));
            // Workaround b/5254577
            mPrefs.registerOnSharedPreferenceChangeListener(BrowserSettings.this);
            if (Build.VERSION.CODENAME.equals("REL")) {
                // This is a release build, always startup with debug disabled
                setDebugEnabled(false);
            }
            if (mPrefs.contains(PREF_TEXT_SIZE)) {
                /*
                 * Update from TextSize enum to zoom percent
                 * SMALLEST is 50%
                 * SMALLER is 75%
                 * NORMAL is 100%
                 * LARGER is 150%
                 * LARGEST is 200%
                 */
                switch (getTextSize()) {
                case SMALLEST:
                    setTextZoom(50);
                    break;
                case SMALLER:
                    setTextZoom(75);
                    break;
                case LARGER:
                    setTextZoom(150);
                    break;
                case LARGEST:
                    setTextZoom(200);
                    break;
                }
                mPrefs.edit().remove(PREF_TEXT_SIZE).apply();
            }

            synchronized (BrowserSettings.class) {
                sInitialized = true;
                BrowserSettings.class.notifyAll();
            }
        }
    };

    /**
     *Add for setting homepage via MCC\MNC
     *@{
     */
    private void setHomePage() {
        boolean sCarrierConfiged = false;
        /**
         *SPRD
         *add for homepage customise
         *
         *@{
        */
        if (Util.HOMEPAGE_OPERATOR_CUST.equalsIgnoreCase("cucc")) {
            sFactoryResetUrl = mContext.getResources().getString(R.string.homepage_base_cucc);
        } else if (Util.HOMEPAGE_OPERATOR_CUST.equalsIgnoreCase("cmcc")){
            sFactoryResetUrl = mContext.getResources().getString(R.string.homepage_base_cmcc);
        } else if (Util.SUPPORT_CARRIER_HOMEPAGE) {
            String carrierConfig = Settings.Global.getString(mContext.getContentResolver(),
                    HOMEPAGE_CARRIER);
            if (TextUtils.isEmpty(carrierConfig)) {
                CarrierConfigManager cfgManager = (CarrierConfigManager)
                mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
                if (cfgManager != null) {
                    int defaultDataPhoneId = SubscriptionManager.getPhoneId(
                            SubscriptionManager.getDefaultDataSubId());
                    if (!SubscriptionManager.isValidPhoneId(defaultDataPhoneId)) {
                        defaultDataPhoneId = 0;
                    }
                    if (cfgManager.getConfigForPhoneId(defaultDataPhoneId) != null) {
                        String configUrl = cfgManager.getConfigForPhoneId(defaultDataPhoneId)
                                .getString(CarrierConfigManager.KEY_GLO_CONF_HOMEPAGE_URL);
                        if(!TextUtils.isEmpty(configUrl)) {
                            carrierConfig = configUrl;
                            sCarrierConfiged = true;
                        }
                        Log.i(LOGTAG,"SUPPORT_CARRIER_HOMEPAGE carrierConfig = "+carrierConfig);
                    }
                }
            }
            sFactoryResetUrl = carrierConfig;
        }

        if (TextUtils.isEmpty(sFactoryResetUrl)) {
            sFactoryResetUrl = mContext.getResources().getString(R.string.homepage_base);
        }
        Log.i(LOGTAG,"sFactoryResetUrl = "+sFactoryResetUrl);

        if (sFactoryResetUrl.indexOf("{CID}") != -1) {
            sFactoryResetUrl = sFactoryResetUrl.replace("{CID}",
                BrowserProvider.getClientId(mContext.getContentResolver()));
        }

        if (sCarrierConfiged) {
            Settings.Global.putString(mContext.getContentResolver(),
                    HOMEPAGE_CARRIER, sFactoryResetUrl);
        }
    }
    /*@}*/

    private static void requireInitialization() {
        synchronized (BrowserSettings.class) {
            while (!sInitialized) {
                try {
                    BrowserSettings.class.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Syncs all the settings that have a Preference UI
     */
    private void syncSetting(WebSettings settings) {
        Log.i(LOGTAG, "BrowserSettings syncSetting --> Start");
        settings.setGeolocationEnabled(enableGeolocation());
        settings.setJavaScriptEnabled(enableJavascript());
        settings.setLightTouchEnabled(enableLightTouch());
        settings.setNavDump(enableNavDump());
        settings.setDefaultTextEncodingName(getDefaultTextEncoding());
        settings.setDefaultZoom(getDefaultZoom());
        settings.setMinimumFontSize(getMinimumFontSize());
        settings.setMinimumLogicalFontSize(getMinimumFontSize());
        settings.setPluginState(getPluginState());
        settings.setTextZoom(getTextZoom());
        settings.setLayoutAlgorithm(getLayoutAlgorithm());
        settings.setJavaScriptCanOpenWindowsAutomatically(!blockPopupWindows());
        settings.setLoadsImagesAutomatically(loadImages());
        settings.setLoadWithOverviewMode(loadPageInOverviewMode());
        settings.setSavePassword(rememberPasswords());
        settings.setSaveFormData(saveFormdata());
        settings.setUseWideViewPort(isWideViewport());
        /* SPRD: Add the interface to sync the settings of force enable scalable. @{ */
        settings.setForceUserScalable(forceEnableUserScalable());
        /* @} */

        /**
         *Add for Browser UA
         *Original Android code:
         * String ua = mCustomUserAgents.get(settings);
         * if (ua != null) {
         *     settings.setUserAgentString(ua);
         * } else {
         *     settings.setUserAgentString(USER_AGENTS[getUserAgent()]);
         * }
         *@{
        */

        String ua = getUserAgentString();
        if (ua == null) {
            ua = mCustomUserAgents.get(settings);
        }
        int uaNum = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.USER_AGENT_CHOICE, WebSettings.UA_CHOICE_DEFAULT);
        if (WebSettings.UA_CHOICE_OTHER == uaNum) {
            ua = Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.OTHER_USER_AGENT_STRING);
            settings.setUserAgentString(ua);
        } else if (WebSettings.UA_CHOICE_CUSTOM == uaNum) {
            ua = Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.CUSTOM_USER_AGENT_STRING);
            settings.setUserAgentString(ua);
        } else {
            settings.setUserAgentString(ua);
        }

        Log.i(LOGTAG, "syncSetting --> UA -> ua = " + ua);
        Log.i(LOGTAG, "BrowserSettings syncSetting --> End");
        /*@}*/

        /**
         * Add for text format
         *@{
        */
        setTextFormat(settings,textFormat);
        /*@}*/
    }

    /**
     * Add for Browser UA
     *@{
    */
    public String getUserAgentString(){
        return SprdBrowserUserAgentAddonStub.getInstance().getUserAgentString();
    }
    /*@}*/

    /**
     * Syncs all the settings that have no UI
     * These cannot change, so we only need to set them once per WebSettings
     */
    private void syncStaticSettings(WebSettings settings) {
        settings.setDefaultFontSize(16);
        settings.setDefaultFixedFontSize(13);

        // WebView inside Browser doesn't want initial focus to be set.
        settings.setNeedInitialFocus(false);
        // Browser supports multiple windows
        settings.setSupportMultipleWindows(true);
        // enable smooth transition for better performance during panning or
        // zooming
        settings.setEnableSmoothTransition(true);
        // disable content url access
        settings.setAllowContentAccess(false);

        // HTML5 API flags
        settings.setAppCacheEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);

        // HTML5 configuration parametersettings.
        settings.setAppCacheMaxSize(getWebStorageSizeManager().getAppCacheMaxSize());
        settings.setAppCachePath(getAppCachePath());
        settings.setDatabasePath(mContext.getDir("databases", 0).getPath());
        settings.setGeolocationDatabasePath(mContext.getDir("geolocation", 0).getPath());
        // origin policy for file access
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);
    }

    private void syncSharedSettings() {
        mNeedsSharedSync = false;
        CookieManager.getInstance().setAcceptCookie(acceptCookies());
        if (mController != null) {
            for (Tab tab : mController.getTabs()) {
                tab.setAcceptThirdPartyCookies(acceptCookies());
            }
            mController.setShouldShowErrorConsole(enableJavascriptConsole());
        }
    }

    private void syncManagedSettings() {
        syncSharedSettings();
        synchronized (mManagedSettings) {
            Iterator<WeakReference<WebSettings>> iter = mManagedSettings.iterator();
            while (iter.hasNext()) {
                WeakReference<WebSettings> ref = iter.next();
                WebSettings settings = ref.get();
                if (settings == null) {
                    iter.remove();
                    continue;
                }
                syncSetting(settings);
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(
            SharedPreferences sharedPreferences, String key) {
        /**
         * Add for text format
         *@{
        */
        if(PreferenceKeys.PREF_TEXT_FORMAT.equals(key) && sharedPreferences != null){
            textFormat = sharedPreferences.getString(PREF_TEXT_FORMAT, textFormat);
        }
        /*@}*/
        syncManagedSettings();
        if (PREF_SEARCH_ENGINE.equals(key)) {
            updateSearchEngine(false);
        } else if (PREF_FULLSCREEN.equals(key)) {
            if (mController != null && mController.getUi() != null) {
                mController.getUi().setFullscreen(useFullscreen());
            }
        } else if (PREF_ENABLE_QUICK_CONTROLS.equals(key)) {
            if (mController != null && mController.getUi() != null && sharedPreferences != null) {
                mController.getUi().setUseQuickControls(sharedPreferences.getBoolean(key, false));
            }
        } else if (PREF_LINK_PREFETCH.equals(key)) {
            updateConnectionType();
        }
        /**
         * Add for horizontal screen
         *@{
         */
        else if (PREF_HORIZONTAL_SCREEN.equals(key)) {
            if(mController != null){
                mController.updateHorizontalToUnspecified();
            }
        }
        /*@}*/
    }

    public static String getFactoryResetHomeUrl(Context context) {
        requireInitialization();
        return sFactoryResetUrl;
    }

    public LayoutAlgorithm getLayoutAlgorithm() {
        LayoutAlgorithm layoutAlgorithm = LayoutAlgorithm.NORMAL;
        final LayoutAlgorithm autosize = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                LayoutAlgorithm.TEXT_AUTOSIZING : LayoutAlgorithm.NARROW_COLUMNS;

        if (autofitPages()) {
            layoutAlgorithm = autosize;
        }
        if (isDebugEnabled()) {
            if (isNormalLayout()) {
                layoutAlgorithm = LayoutAlgorithm.NORMAL;
            } else {
                layoutAlgorithm = autosize;
            }
        }
        return layoutAlgorithm;
    }

    public int getPageCacheCapacity() {
        requireInitialization();
        return mPageCacheCapacity;
    }

    public WebStorageSizeManager getWebStorageSizeManager() {
        requireInitialization();
        return mWebStorageSizeManager;
    }

    private String getAppCachePath() {
        if (mAppCachePath == null) {
            mAppCachePath = mContext.getDir("appcache", 0).getPath();
        }
        return mAppCachePath;
    }

    private void updateSearchEngine(boolean force) {
        String searchEngineName = getSearchEngineName();

        /* add for Bug493562: sync the search engine between Browser and quicksearch,begin*/
        if (searchEngineName != null){
            String saveEngine;
            if (searchEngineName.contains("_")){
                int index = searchEngineName.indexOf("_");
                saveEngine = searchEngineName.substring(0, index);
            }
            else{
                saveEngine = searchEngineName;
            }
            Settings.Global.putString(mContext.getContentResolver(),
                    DATABASE_SEARCH_ENGINE,
                    saveEngine.toUpperCase());
        }
        /* add for Bug493562: sync the search engine between Browser and quicksearch,end*/

        if (force || mSearchEngine == null ||
                !mSearchEngine.getName().equals(searchEngineName)) {
            mSearchEngine = SearchEngines.get(mContext, searchEngineName);
         }
    }

    public SearchEngine getSearchEngine() {
        if (mSearchEngine == null) {
            updateSearchEngine(false);
        }
        return mSearchEngine;
    }

    public boolean isDebugEnabled() {
        requireInitialization();
        return mPrefs.getBoolean(PREF_DEBUG_MENU, false);
    }

    public void setDebugEnabled(boolean value) {
        Editor edit = mPrefs.edit();
        edit.putBoolean(PREF_DEBUG_MENU, value);
        if (!value) {
            // Reset to "safe" value
            edit.putBoolean(PREF_ENABLE_HARDWARE_ACCEL_SKIA, false);
        }
        edit.apply();
    }

    public void clearCache() {
        WebIconDatabase.getInstance().removeAllIcons();
        if (mController != null) {
            WebView current = mController.getCurrentWebView();
            if (current != null) {
                current.clearCache(true);
            }
        }
    }

    public void clearCookies() {
        CookieManager.getInstance().removeAllCookie();
    }

    public void clearHistory() {
        ContentResolver resolver = mContext.getContentResolver();
        Util.clearHistory(resolver);
        Util.clearSearches(resolver);
    }

    public void clearFormData() {
        WebViewDatabase.getInstance(mContext).clearFormData();
        if (mController!= null) {
            WebView currentTopView = mController.getCurrentTopWebView();
            if (currentTopView != null) {
                currentTopView.clearFormData();
            }
        }
    }

    public void clearPasswords() {
        WebViewDatabase db = WebViewDatabase.getInstance(mContext);
        db.clearUsernamePassword();
        db.clearHttpAuthUsernamePassword();
        //SPRD:Bug 521638 Delete password file when click clear passwords item.
        File passwordFile = new File(sPasswordPath);

        if ((passwordFile != null) && (passwordFile.exists())) {
            if(!passwordFile.delete()){
                Log.i(LOGTAG, "Delete password file fail");
            }
        } else {
            Log.i(LOGTAG, "There is not password file");
        }
    }

    public void clearDatabases() {
        WebStorage.getInstance().deleteAllData();
    }

    public void clearLocationAccess() {
        GeolocationPermissions.getInstance().clearAll();
    }

    public void resetDefaultPreferences() {
        // Preserve autologin setting
        long gal = mPrefs.getLong(GoogleAccountLogin.PREF_AUTOLOGIN_TIME, -1);
        mPrefs.edit()
                .clear()
                .putLong(GoogleAccountLogin.PREF_AUTOLOGIN_TIME, gal)
                .apply();
        resetCachedValues();

        /*
         * for download_storage_save_path
         *@{
         */
        String path = "";
        path = StorageUtils.getDefaultStoragePath();
        ContentValues argsSavepath = new ContentValues();
        argsSavepath.put("savepath", path);
        try {
            mContext.getContentResolver().update(Uri.parse("content://browser/filesavepath"), argsSavepath, "_id = 1", null);
        } catch (SQLiteException e) {
            Log.e(LOGTAG, "resetDefaultPreferences: " + e);
        }
        /*@}*/
        syncManagedSettings();
    }

    private void resetCachedValues() {
        updateSearchEngine(false);
    }

    public void toggleDebugSettings() {
        setDebugEnabled(!isDebugEnabled());
    }

    public boolean hasDesktopUseragent(WebView view) {
        return view != null && mCustomUserAgents.get(view.getSettings()) != null;
    }

    public void toggleDesktopUseragent(WebView view) {
        if (view == null) {
            return;
        }
        WebSettings settings = view.getSettings();
        if (mCustomUserAgents.get(settings) != null) {
            mCustomUserAgents.remove(settings);
            settings.setUserAgentString(USER_AGENTS[getUserAgent()]);
        } else {
            mCustomUserAgents.put(settings, DESKTOP_USERAGENT);
            settings.setUserAgentString(DESKTOP_USERAGENT);
        }
    }

    public static int getAdjustedMinimumFontSize(int rawValue) {
        rawValue++; // Preference starts at 0, min font at 1
        if (rawValue > 1) {
            rawValue += (MIN_FONT_SIZE_OFFSET - 2);
        }
        return rawValue;
    }

    public int getAdjustedTextZoom(int rawValue) {
        rawValue = (rawValue - TEXT_ZOOM_START_VAL) * TEXT_ZOOM_STEP;
        return (int) ((rawValue + 100) * mFontSizeMult);
    }

    static int getRawTextZoom(int percent) {
        return (percent - 100) / TEXT_ZOOM_STEP + TEXT_ZOOM_START_VAL;
    }

    public int getAdjustedDoubleTapZoom(int rawValue) {
        rawValue = (rawValue - DOUBLE_TAP_ZOOM_START_VAL) * DOUBLE_TAP_ZOOM_STEP;
        return (int) ((rawValue + 100) * mFontSizeMult);
    }

    static int getRawDoubleTapZoom(int percent) {
        return (percent - 100) / DOUBLE_TAP_ZOOM_STEP + DOUBLE_TAP_ZOOM_START_VAL;
    }

    public SharedPreferences getPreferences() {
        return mPrefs;
    }

    // update connectivity-dependent options
    public void updateConnectionType() {
        ConnectivityManager cm = (ConnectivityManager)
            mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        String linkPrefetchPreference = getLinkPrefetchEnabled();
        boolean linkPrefetchAllowed = linkPrefetchPreference.
            equals(getLinkPrefetchAlwaysPreferenceString(mContext));
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (ni != null) {
            switch (ni.getType()) {
                case ConnectivityManager.TYPE_WIFI:
                case ConnectivityManager.TYPE_ETHERNET:
                case ConnectivityManager.TYPE_BLUETOOTH:
                    linkPrefetchAllowed |= linkPrefetchPreference.
                        equals(getLinkPrefetchOnWifiOnlyPreferenceString(mContext));
                    break;
                case ConnectivityManager.TYPE_MOBILE:
                case ConnectivityManager.TYPE_MOBILE_DUN:
                case ConnectivityManager.TYPE_MOBILE_MMS:
                case ConnectivityManager.TYPE_MOBILE_SUPL:
                case ConnectivityManager.TYPE_WIMAX:
                default:
                    break;
            }
        }
        if (mLinkPrefetchAllowed != linkPrefetchAllowed) {
            mLinkPrefetchAllowed = linkPrefetchAllowed;
            syncManagedSettings();
        }
    }

    // -----------------------------
    // getter/setters for accessibility_preferences.xml
    // -----------------------------

    @Deprecated
    private TextSize getTextSize() {
        String textSize = mPrefs.getString(PREF_TEXT_SIZE, "NORMAL");
        return TextSize.valueOf(textSize);
    }

    public int getMinimumFontSize() {
        int minFont = mPrefs.getInt(PREF_MIN_FONT_SIZE, 0);
        return getAdjustedMinimumFontSize(minFont);
    }

    public boolean forceEnableUserScalable() {
        return mPrefs.getBoolean(PREF_FORCE_USERSCALABLE, false);
    }

    public int getTextZoom() {
        requireInitialization();
        int textZoom = mPrefs.getInt(PREF_TEXT_ZOOM, 10);
        return getAdjustedTextZoom(textZoom);
    }

    public void setTextZoom(int percent) {
        mPrefs.edit().putInt(PREF_TEXT_ZOOM, getRawTextZoom(percent)).apply();
    }

    public int getDoubleTapZoom() {
        requireInitialization();
        int doubleTapZoom = mPrefs.getInt(PREF_DOUBLE_TAP_ZOOM, 5);
        return getAdjustedDoubleTapZoom(doubleTapZoom);
    }

    public void setDoubleTapZoom(int percent) {
        mPrefs.edit().putInt(PREF_DOUBLE_TAP_ZOOM, getRawDoubleTapZoom(percent)).apply();
    }

    // -----------------------------
    // getter/setters for advanced_preferences.xml
    // -----------------------------

    public String getSearchEngineName() {
        /**
         *Add for customise SearchEngine
         *Original Android code:
         * return mPrefs.getString(PREF_SEARCH_ENGINE, SearchEngine.GOOGLE);
         *@{
         */
        if (Util.ENGINE_OPERATOR_CUST.equalsIgnoreCase("cucc")|| Util.ENGINE_OPERATOR_CUST.equalsIgnoreCase("cmcc")) {
            return mPrefs.getString(PREF_SEARCH_ENGINE, SearchEngine.BAIDU);
        }
        return mPrefs.getString(PREF_SEARCH_ENGINE, SearchEngine.GOOGLE);
    }

    public boolean allowAppTabs() {
        return mPrefs.getBoolean(PREF_ALLOW_APP_TABS, false);
    }

    public boolean openInBackground() {
        return mPrefs.getBoolean(PREF_OPEN_IN_BACKGROUND, false);
    }

    public boolean enableJavascript() {
        return mPrefs.getBoolean(PREF_ENABLE_JAVASCRIPT, true);
    }

    // TODO: Cache
    public PluginState getPluginState() {
        String state = mPrefs.getString(PREF_PLUGIN_STATE, "ON");
        return PluginState.valueOf(state);
    }

    // TODO: Cache
    public ZoomDensity getDefaultZoom() {
        String zoom = mPrefs.getString(PREF_DEFAULT_ZOOM, "MEDIUM");
        return ZoomDensity.valueOf(zoom);
    }

    public boolean loadPageInOverviewMode() {
        return mPrefs.getBoolean(PREF_LOAD_PAGE, true);
    }

    public boolean autofitPages() {
        return mPrefs.getBoolean(PREF_AUTOFIT_PAGES, false);
    }

    public boolean blockPopupWindows() {
        return mPrefs.getBoolean(PREF_BLOCK_POPUP_WINDOWS, true);
    }

    public boolean loadImages() {
        return mPrefs.getBoolean(PREF_LOAD_IMAGES, true);
    }

    public String getDefaultTextEncoding() {
        return mPrefs.getString(PREF_DEFAULT_TEXT_ENCODING, null);
    }

    // -----------------------------
    // getter/setters for general_preferences.xml
    // -----------------------------

    public String getHomePage() {
        return mPrefs.getString(PREF_HOMEPAGE, getFactoryResetHomeUrl(mContext));
    }

    public void setHomePage(String value) {
        /* SPRD:539903 should not set snapshot as homepage@{ */
        if (value != null && value.startsWith("file:")) {
            Toast.makeText(mContext, R.string.homepage_snapshot, Toast.LENGTH_LONG)
                .show();
            return;
        }
        /*@}*/
        mPrefs.edit().putString(PREF_HOMEPAGE, value).apply();
    }

    public boolean isAutofillEnabled() {
        return mPrefs.getBoolean(PREF_AUTOFILL_ENABLED, true);
    }

    public void setAutofillEnabled(boolean value) {
        mPrefs.edit().putBoolean(PREF_AUTOFILL_ENABLED, value).apply();
    }

    // -----------------------------
    // getter/setters for debug_preferences.xml
    // -----------------------------

    public boolean isHardwareAccelerated() {
        if (!isDebugEnabled()) {
            return true;
        }
        return mPrefs.getBoolean(PREF_ENABLE_HARDWARE_ACCEL, true);
    }

    public boolean isSkiaHardwareAccelerated() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_HARDWARE_ACCEL_SKIA, false);
    }

    public int getUserAgent() {
        if (!isDebugEnabled()) {
            return 0;
        }
        return Integer.parseInt(mPrefs.getString(PREF_USER_AGENT, "0"));
    }

    // -----------------------------
    // getter/setters for hidden_debug_preferences.xml
    // -----------------------------

    public boolean enableVisualIndicator() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_VISUAL_INDICATOR, false);
    }

    public boolean enableCpuUploadPath() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_CPU_UPLOAD_PATH, false);
    }

    public boolean enableJavascriptConsole() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_JAVASCRIPT_CONSOLE, true);
    }

    public boolean isWideViewport() {
        if (!isDebugEnabled()) {
            return true;
        }
        return mPrefs.getBoolean(PREF_WIDE_VIEWPORT, true);
    }

    public boolean isNormalLayout() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_NORMAL_LAYOUT, false);
    }

    public boolean isTracing() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_TRACING, false);
    }

    public boolean enableLightTouch() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_LIGHT_TOUCH, false);
    }

    public boolean enableNavDump() {
        if (!isDebugEnabled()) {
            return false;
        }
        return mPrefs.getBoolean(PREF_ENABLE_NAV_DUMP, false);
    }

    public String getJsEngineFlags() {
        if (!isDebugEnabled()) {
            return "";
        }
        return mPrefs.getString(PREF_JS_ENGINE_FLAGS, "");
    }

    // -----------------------------
    // getter/setters for lab_preferences.xml
    // -----------------------------

    public boolean useQuickControls() {
        return mPrefs.getBoolean(PREF_ENABLE_QUICK_CONTROLS, false);
    }

    public boolean useMostVisitedHomepage() {
        return HomeProvider.MOST_VISITED.equals(getHomePage());
    }

    public boolean useFullscreen() {
        return mPrefs.getBoolean(PREF_FULLSCREEN, false);
    }

    public boolean useInvertedRendering() {
        return mPrefs.getBoolean(PREF_INVERTED, false);
    }

    public float getInvertedContrast() {
        return 1 + (mPrefs.getInt(PREF_INVERTED_CONTRAST, 0) / 10f);
    }

    // -----------------------------
    // getter/setters for privacy_security_preferences.xml
    // -----------------------------

    public boolean showSecurityWarnings() {
        return mPrefs.getBoolean(PREF_SHOW_SECURITY_WARNINGS, true);
    }

    public boolean acceptCookies() {
        return mPrefs.getBoolean(PREF_ACCEPT_COOKIES, true);
    }

    public boolean saveFormdata() {
        return mPrefs.getBoolean(PREF_SAVE_FORMDATA, true);
    }

    public boolean enableGeolocation() {
        return mPrefs.getBoolean(PREF_ENABLE_GEOLOCATION, true);
    }

    public boolean rememberPasswords() {
        return mPrefs.getBoolean(PREF_REMEMBER_PASSWORDS, true);
    }

    // -----------------------------
    // getter/setters for bandwidth_preferences.xml
    // -----------------------------

    public static String getPreloadOnWifiOnlyPreferenceString(Context context) {
        return context.getResources().getString(R.string.pref_data_preload_value_wifi_only);
    }

    public static String getPreloadAlwaysPreferenceString(Context context) {
        return context.getResources().getString(R.string.pref_data_preload_value_always);
    }

    private static final String DEAULT_PRELOAD_SECURE_SETTING_KEY =
            "browser_default_preload_setting";

    public String getDefaultPreloadSetting() {
        String preload = Settings.Secure.getString(mContext.getContentResolver(),
                DEAULT_PRELOAD_SECURE_SETTING_KEY);
        if (preload == null) {
            preload = mContext.getResources().getString(R.string.pref_data_preload_default_value);
        }
        return preload;
    }

    public String getPreloadEnabled() {
        return mPrefs.getString(PREF_DATA_PRELOAD, getDefaultPreloadSetting());
    }

    public static String getLinkPrefetchOnWifiOnlyPreferenceString(Context context) {
        return context.getResources().getString(R.string.pref_link_prefetch_value_wifi_only);
    }

    public static String getLinkPrefetchAlwaysPreferenceString(Context context) {
        return context.getResources().getString(R.string.pref_link_prefetch_value_always);
    }

    private static final String DEFAULT_LINK_PREFETCH_SECURE_SETTING_KEY =
            "browser_default_link_prefetch_setting";

    public String getDefaultLinkPrefetchSetting() {
        String preload = Settings.Secure.getString(mContext.getContentResolver(),
            DEFAULT_LINK_PREFETCH_SECURE_SETTING_KEY);
        if (preload == null) {
            preload = mContext.getResources().getString(R.string.pref_link_prefetch_default_value);
        }
        return preload;
    }

    public String getLinkPrefetchEnabled() {
        return mPrefs.getString(PREF_LINK_PREFETCH, getDefaultLinkPrefetchSetting());
    }

    // -----------------------------
    // getter/setters for browser recovery
    // -----------------------------
    /**
     * The last time browser was started.
     * @return The last browser start time as System.currentTimeMillis. This
     * can be 0 if this is the first time or the last tab was closed.
     */
    public long getLastRecovered() {
        return mPrefs.getLong(KEY_LAST_RECOVERED, 0);
    }

    /**
     * Sets the last browser start time.
     * @param time The last time as System.currentTimeMillis that the browser
     * was started. This should be set to 0 if the last tab is closed.
     */
    public void setLastRecovered(long time) {
        mPrefs.edit()
            .putLong(KEY_LAST_RECOVERED, time)
            .apply();
    }

    /**
     * Used to determine whether or not the previous browser run crashed. Once
     * the previous state has been determined, the value will be set to false
     * until a pause is received.
     * @return true if the last browser run was paused or false if it crashed.
     */
    public boolean wasLastRunPaused() {
        return mPrefs.getBoolean(KEY_LAST_RUN_PAUSED, false);
    }

    /**
     * Sets whether or not the last run was a pause or crash.
     * @param isPaused Set to true When a pause is received or false after
     * resuming.
     */
    public void setLastRunPaused(boolean isPaused) {
        mPrefs.edit()
            .putBoolean(KEY_LAST_RUN_PAUSED, isPaused)
            .apply();
    }
    /**
     * Add for horizontal screen
     *@{
     */
    public boolean isHorizontal(){
        return mPrefs.getBoolean(PREF_HORIZONTAL_SCREEN, false);
    }
    /*@}*/

    /* add for Bug493562: sync the search engine between Browser and quicksearch,begin*/
    public void setSearchEnginerName(String value){
        String engine ;
        if (value != null){
            engine = value.toLowerCase();
            if (!mPrefs.getString(PREF_SEARCH_ENGINE, SearchEngine.GOOGLE).contains(engine)){
                Resources res = mContext.getResources();
                String[] searchEngines = res.getStringArray(R.array.search_engines);
                for (int i = 0; i < searchEngines.length; i++) {
                    String name = searchEngines[i];
                    if (name.contains(engine)){
                        Log.i(LOGTAG, "setSearchEnginerName:" + name);
                        mPrefs.edit().putString(PREF_SEARCH_ENGINE, name).commit();
                        break;
                    }
                }
            }
        }
    }
    /* add for Bug493562: sync the search engine between Browser and quicksearch,end*/

    /**
     * Add for text format
     *@{
    */
    private static void setTextFormat(WebSettings s, String format) {
        s.setStandardFontFamily(format);
        s.setSerifFontFamily(format);
        s.setFixedFontFamily(format);
        s.setSansSerifFontFamily(format);
    }
    /*@}*/
}
