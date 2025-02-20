package com.android.internal.telephony;

import android.app.ActivityThread;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.util.Log;

/**
 * Custom Utils with common methods defined by tele
 * @author SPRD
 *
 */
public class TeleUtils {
    private static final String LOG_TAG = "TeleUtils";

    /**
     * Supply method to change operators
     * @param value This value can either be an old operator name or an operator numeric
     * @param arrayName ArrayName is the name of an specified array which has been defined in custom xmls
     * @return expected operator name
     */
    public static String updateOperator(String value, String arrayName) {
        Resources r = Resources.getSystem();
        String newName = value;
        Log.d(LOG_TAG, " changeOperator: old value= " + value);
        int identify = r.getIdentifier(arrayName, "array", "android");
        String itemList[] = r.getStringArray(identify);
        Log.d(LOG_TAG, " changeOperator: itemList length is " + itemList.length);
        for (String item : itemList) {
            String parts[] = item.split("=");
            if (parts[0].equalsIgnoreCase(value)) {
                newName = parts[1];
                Log.d(LOG_TAG, "itemList found: parts[0]= " + parts[0] +
                        " parts[1]= " + parts[1] + "  newName= " + newName);
                return newName;
            }
        }
        Log.d(LOG_TAG, "changeOperator not found: original value= " + value + " newName= " + newName);
        return newName;
    }

    public static String concatenateEccList (String eccList, String number) {
        if (!TextUtils.isEmpty(number)) {
            if (!TextUtils.isEmpty(eccList)) {
                eccList += "," + number;
            } else {
                eccList = number;
            }
        }
        return eccList;
    }

    /**
     * some customer requirements, when set default data card, the primary card
     * following data card changes, set the primary card to the selected data
     * card, the primary card consistent with the phoneId of data card.
     * @return get the switching state
     * SPRD: modify for bug513637
     */
    public static boolean isSuppSetDataAndPrimaryCardBind() {
        if (getPersistableBundle() != null) {
            return getPersistableBundle().getBoolean(CarrierConfigManager.KEY_FEATURE_STE_DATA_AND_STE_PRIMARYCARD_MERGE_BOOL);
        }
        return false;
    }

    private static PersistableBundle getPersistableBundle() {
        Context context = ActivityThread.currentApplication().getApplicationContext();
        if (context != null) {
            CarrierConfigManager configManager = (CarrierConfigManager) context
                    .getSystemService(Context.CARRIER_CONFIG_SERVICE);
            return configManager.getConfigForDefaultPhone();
        }
        return null;
    }
}
