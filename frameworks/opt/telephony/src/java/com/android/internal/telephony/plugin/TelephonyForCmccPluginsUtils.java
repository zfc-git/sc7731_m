package com.android.internal.telephony.plugin;

import android.app.AddonManager;
import android.util.Log;
import com.android.internal.R;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccFileHandler;

public class TelephonyForCmccPluginsUtils {

    private static final String LOGTAG = "TelephonyForCmccPluginsUtils";
    private static TelephonyForCmccPluginsUtils mInstance;

    public static TelephonyForCmccPluginsUtils getInstance() {
        if (mInstance != null) return mInstance;
        Log.d(LOGTAG, "TelephonyForCmccPluginsUtils getInstance");
        mInstance = (TelephonyForCmccPluginsUtils) AddonManager.getDefault().getAddon(R.string.feature_plugin_for_telephony, TelephonyForCmccPluginsUtils.class);
        return mInstance;
    }

    public TelephonyForCmccPluginsUtils() {
    }

    public void updateUidForAdn(UsimPhoneBookManager mUsimPhoneBookManager,int adnEf,int recNum,int adnIndex,AdnRecord adn,IccFileHandler mFh) {

    }
}