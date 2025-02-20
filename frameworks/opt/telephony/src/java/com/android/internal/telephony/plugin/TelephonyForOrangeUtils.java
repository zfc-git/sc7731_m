package com.android.internal.telephony.plugin;

import com.android.internal.R;
import android.app.AddonManager;
import android.content.Context;
import android.util.Log;

public class TelephonyForOrangeUtils {
    private static final String LOGTAG = "TelephonyForOrangeUtils";
    static TelephonyForOrangeUtils sInstance;

    public static TelephonyForOrangeUtils getInstance() {
        if (sInstance != null) return sInstance;
        Log.d(LOGTAG, "TelephonyForOrangeUtils getInstance");
        sInstance = (TelephonyForOrangeUtils) AddonManager.getDefault().getAddon(R.string.feature_for_orange, TelephonyForOrangeUtils.class);
        return sInstance;
    }

    public TelephonyForOrangeUtils() {
    }

    public boolean IsSupportOrange(){
        Log.d(LOGTAG, "IsSupportOrange empty method");
        return false;
   }
    public void setSystemLocaleLock(String prefLang,String imsi,Context context){
    }
}
