package com.android.internal.telephony.plugin;

import android.app.Activity;

import com.android.internal.telephony.InboundSmsTracker;
import com.android.internal.telephony.InboundSmsHandler;
import android.app.AddonManager;
import android.content.Context;
import android.content.Intent;
import com.android.internal.R;
import android.util.Log;
import android.telephony.Rlog;

public class BlockInboundSmsHandlerUtils {

    private static final String LOGTAG = "BlockInboundSmsHandlerUtils";
    static BlockInboundSmsHandlerUtils sInstance;

    public static BlockInboundSmsHandlerUtils getInstance(Context context) {
        if (sInstance != null) return sInstance;
        Log.d(LOGTAG, "BlockInboundSmsHandlerUtils getInstance");
        AddonManager addonManager = new AddonManager(context);
        sInstance = (BlockInboundSmsHandlerUtils) addonManager.getAddon(R.string.feature_Firewall_sms, BlockInboundSmsHandlerUtils.class);
        Log.d(LOGTAG, "BlockInboundSmsHandlerUtils getInstance: plugin = " + context.getString(R.string.feature_Firewall_sms));

        return sInstance;

    }

    public BlockInboundSmsHandlerUtils() {
    }

    public boolean plugin_sms(String address,Intent intent){
        Log.d(LOGTAG, "Don't join AddonBlockInboundSmsHandlerUtils");
        /* Modify by SPRD for Bug:521777 2016.02.01 Start */
//        return true;
        return false;
        /* Modify by SPRD for Bug:521777 2016.02.01 End */

   }

}
