
package com.android.internal.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;

import java.util.ArrayList;

/**
 * This class manages cached copies of all the telephony configuration for each phone ID. A phone ID
 * loosely corresponds to a particular SIM or network.
 */
public class GlobalConfigManager {
    private static final String TAG = "GlobalConfigManager";

    public static final String GLO_CONF_VOICEMAIL_NUMBER = CarrierConfigManager.KEY_GLO_CONF_VOICEMAIL_NUMBER;
    public static final String GLO_CONF_VOICEMAIL_TAG = CarrierConfigManager.KEY_GLO_CONF_VOICEMAIL_TAG;
    public static final String GLO_CONF_ROAMING_VOICEMAIL_NUMBER = CarrierConfigManager.KEY_GLO_CONF_ROAMING_VOICEMAIL_NUMBER;
    public static final String GLO_CONF_ECC_LIST_NO_CARD = CarrierConfigManager.KEY_GLO_CONF_ECC_LIST_NO_CARD;
    public static final String GLO_CONF_ECC_LIST_WITH_CARD = CarrierConfigManager.KEY_GLO_CONF_ECC_LIST_WITH_CARD;
    public static final String GLO_CONF_FAKE_ECC_LIST_WITH_CARD = CarrierConfigManager.KEY_GLO_CONF_FAKE_ECC_LIST_WITH_CARD;
    public static final String GLO_CONF_NUM_MATCH = CarrierConfigManager.KEY_GLO_CONF_NUM_MATCH;
    public static final String GLO_CONF_NUM_MATCH_RULE = CarrierConfigManager.KEY_GLO_CONF_NUM_MATCH_RULE;
    public static final String GLO_CONF_NUM_MATCH_SHORT = CarrierConfigManager.KEY_GLO_CONF_NUM_MATCH_SHORT;
    public static final String GLO_CONF_SPN = CarrierConfigManager.KEY_GLO_CONF_SPN;
    public static final String GLO_CONF_SMS_7BIT_ENABLED = CarrierConfigManager.KEY_GLO_CONF_SMS_7BIT_ENABLED;
    public static final String GLO_CONF_SMS_CODING_NATIONAL = CarrierConfigManager.KEY_GLO_CONF_SMS_CODING_NATIONAL;
    public static final String GLO_CONF_MVNO = CarrierConfigManager.KEY_GLO_CONF_MVNO;

    // Each emergency call code is coded on three bytes From TS 51.011 EF[ECC] section.
    private static final int ECC_BYTES_COUNT = 3;

    private static volatile GlobalConfigManager sInstance = new GlobalConfigManager();

    public static GlobalConfigManager getInstance() {
        return sInstance;
    }

    private static final int EF_ECC = 0x6FB7;
    private static final int EVENT_GET_SIM_ECC_DONE = 100;
    private static final int EVENT_GET_USIM_ECC_DONE = 101;

    private String[] mSimEccLists;
    private Bundle[] mGlobalConfig;
    private Context mContext;
    private SubscriptionController mSubscriptionController;
    private TelephonyManager mTelephonyManager;

    /**
     * This receiver listens for changes made to carrier config and for a broadcast telling us the
     * CarrierConfigLoader has loaded or updated the carrier config information when sim loaded or
     * network registered. When either of these broadcasts are received, we rebuild the TeleConfig
     * table.
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "Receiver action: " + action);
            if (action.equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                loadInBackground();
            } else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.DEFAULT_PHONE_INDEX);
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);

                if (IccCardConstants.INTENT_VALUE_ICC_READY.equals(simState)) {
                    loadEccListFromSim(phoneId);
                }
            }
        }
    };

    public void init(final Context context) {
        mContext = context;
        mSubscriptionController = SubscriptionController.getInstance();
        mTelephonyManager = TelephonyManager.from(context);
        int phoneCount = mTelephonyManager.getPhoneCount();
        mGlobalConfig = new Bundle[phoneCount];
        mSimEccLists = new String[phoneCount];

        final IntentFilter intentFilterLoaded =
                new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intentFilterLoaded.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        context.registerReceiver(mReceiver, intentFilterLoaded);
    }

    private void loadInBackground() {
        new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "Load global config in background...");
                load();
                updateEccProperties();
                updateSpnFromCarrierConfig();
            }
        }.start();
    }

    public Bundle getGlobalConfigBySubId(int subId) {
        Log.d(TAG, "get tele config by sub ID : " + subId);
        int phoneId = mSubscriptionController.getPhoneId(subId);
        return getGlobalConfigByPhoneId(phoneId);
    }

    /**
     * Find and return the global config for a particular phone id.
     *
     * @param phoneId Phone id of the desired global config bundle
     * @return Global config bundle for the particular phone id.
     */
    public Bundle getGlobalConfigByPhoneId(int phoneId) {
        Bundle teleConfig = null;
        if (SubscriptionManager.isValidPhoneId(phoneId) && phoneId < mGlobalConfig.length) {
            synchronized (mGlobalConfig) {
                teleConfig = mGlobalConfig[phoneId];
            }
        }
        Log.d(TAG, "tele config for phone " + phoneId + ": " + teleConfig);
        // Return a copy so that callers can mutate it.
        if (teleConfig != null) {
            return new Bundle(teleConfig);
        }
        return null;
    }

    /**
     * This loads the global config for each phone. Global config is fetched from
     * GlobalConfigManager and filtered to only include global config variables. The resulting
     * bundles are stored in mGlobalConfig.
     */
    private void load() {
        // Load all the config bundles into a new array and then swap it with the real array to avoid
        // blocking.
        final int phoneCount = mTelephonyManager.getPhoneCount();
        final Bundle[] globalConfig = new Bundle[phoneCount];
        final CarrierConfigManager configManager =
                (CarrierConfigManager) mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        for (int i = 0; i < phoneCount; i++) {
            PersistableBundle config = configManager.getConfigForPhoneId(i);
            globalConfig[i] = getGlobalConfig(config);
        }

        synchronized (mGlobalConfig) {
            mGlobalConfig = globalConfig;
        }
    }

    /* SPRD: [bug475736] Write system property for ecc after get ecclist config from carrier config service. @{ */
    private void updateEccProperties() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        if (mSimEccLists.length >= phoneCount) {
        for (int i = 0; i < phoneCount; i++) {
            String eccListFromCarrierConfig = getEccListFromCarrierConfig(i);
            Log.d(TAG, "updateEccProperties ECC[" + i + "]:" + " eccListFromSim = " + mSimEccLists[i]
                            + " eccListFromCarrierConfig = " + eccListFromCarrierConfig);

            SystemProperties.set(i == 0 ? "ril.ecclist" : ("ril.ecclist" + i),
                    TeleUtils.concatenateEccList(mSimEccLists[i], eccListFromCarrierConfig));
            }
        }

        // ECC plugin
        EccPluginHelper.getInstance().customizedEccList(mSimEccLists);
    }
    /* @} */

    private void updateSpnFromCarrierConfig() {
        int phoneCount = mTelephonyManager.getPhoneCount();
        if (phoneCount != mGlobalConfig.length) {
            return;
        }
        for (int i = 0; i < phoneCount; i++) {
            // Update SPN specially for MVNO
            String spn = mGlobalConfig[i].getString(GLO_CONF_SPN);
            if (!TextUtils.isEmpty(spn)) {
                mTelephonyManager.setSimOperatorNameForPhone(i, spn);
                SubscriptionController controller = SubscriptionController.getInstance();
                int subId = controller.getSubIdUsingPhoneId(i);
                if (SubscriptionManager.isValidSubscriptionId(subId)) {
                    SubscriptionInfo subInfo = controller.getActiveSubscriptionInfoForSimSlotIndex(
                            i, mContext.getOpPackageName());
                    if (subInfo != null
                            && subInfo.getNameSource() != SubscriptionManager.NAME_SOURCE_USER_INPUT
                            && subInfo.getNameSource() != SubscriptionManager.NAME_SOURCE_USER_INPUT_NULL
                            && !spn.equals(subInfo.getDisplayName())) {
                        Log.d(TAG, "updateSpnFromCarrierConfig SPN[" + i + "]: " + spn);
                        controller.setDisplayName(spn, subId);
                    }
                }
            }
        }
    }

    private void loadEccListFromSim(int phoneId) {
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return;
        }

        Phone phone = PhoneFactory.getPhone(phoneId);
        IccFileHandler fileHandler = phone.getIccCard() == null ? null
                : phone.getIccCard().getIccFileHandler();
        if (fileHandler != null) {
            UiccCard uiccCard = phone.getUiccCard();
            if (uiccCard != null) {
                UiccCardApplication uiccCardApp = uiccCard
                        .getApplication(UiccController.APP_FAM_3GPP);
                if (uiccCardApp != null) {
                    // EF_ECC is a transparent EF in SIM card while it is a linear fixed EF in USIM card
                    if (uiccCardApp.getType() == AppType.APPTYPE_USIM) {
                        Log.d(TAG, "Load USIM eccList for phoneId " + phoneId);
                        fileHandler.loadEFLinearFixedAll(EF_ECC,
                                mHandler.obtainMessage(EVENT_GET_USIM_ECC_DONE, phoneId, -1));
                    } else {
                        Log.d(TAG, "Load SIM eccList for phoneId " + phoneId);
                        fileHandler.loadEFTransparent(EF_ECC,
                                mHandler.obtainMessage(EVENT_GET_SIM_ECC_DONE, phoneId, -1));
                    }
                }
            }
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            AsyncResult ar;
            int phoneId = msg.arg1;
            switch (msg.what) {
                case EVENT_GET_USIM_ECC_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        return;
                    }
                    handleUsimEccResponse(phoneId, ar);
                    break;
                case EVENT_GET_SIM_ECC_DONE:
                    ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        return;
                    }
                    handleSimEccResponse(phoneId, ar);
                    break;
                default:
                    break;
            }
        };
    };

    private void handleUsimEccResponse(int phoneId, AsyncResult ar) {
        String eccList = "";
        // Linear fixed EF: ((AsyncResult)(onLoaded.obj)).result is an ArrayList<byte[]>
        ArrayList<byte[]> results = (ArrayList<byte[]>) ar.result;
        if (results != null) {
            for (int i = 0; i < results.size(); i++) {
                String number = PhoneNumberUtils.calledPartyBCDFragmentToString(
                        results.get(i), 0, ECC_BYTES_COUNT);
                eccList = TeleUtils.concatenateEccList(eccList, number);
            }
            Log.d(TAG, "USIM ECC List: " + eccList);
        }

        mSimEccLists[phoneId] = eccList;
        updateEccProperties();
    }

    private void handleSimEccResponse(int phoneId, AsyncResult ar) {
        String eccList = "";
        // Transparent EF: ((AsyncResult)(onLoaded.obj)).result is the byte[]
        byte[] numbers = (byte[]) ar.result;
        if (numbers != null) {
            for (int offSet = 0; offSet < numbers.length / ECC_BYTES_COUNT; offSet++) {
                String number = PhoneNumberUtils.calledPartyBCDFragmentToString(numbers,
                        offSet * ECC_BYTES_COUNT, ECC_BYTES_COUNT);
                eccList = TeleUtils.concatenateEccList(eccList, number);
            }
            Log.d(TAG, "ECC list: " + eccList);
        }

        mSimEccLists[phoneId] = eccList;
        updateEccProperties();
    }

    private String getEccListFromCarrierConfig(int phoneId) {
        String eccList = "";
        Bundle config = getGlobalConfigByPhoneId(phoneId);
        if (config != null) {
            if (TelephonyManager.from(mContext).hasIccCard(phoneId)) {
                eccList = config.getString(GLO_CONF_ECC_LIST_WITH_CARD);
            } else {
                eccList = config.getString(GLO_CONF_ECC_LIST_NO_CARD);
            }
        }
        return eccList;
    }

    private Bundle getGlobalConfig(BaseBundle config) {
        Bundle filtered = new Bundle();
        filtered.putString(GLO_CONF_VOICEMAIL_NUMBER, config.getString(GLO_CONF_VOICEMAIL_NUMBER));
        filtered.putString(GLO_CONF_VOICEMAIL_TAG, config.getString(GLO_CONF_VOICEMAIL_TAG));
        filtered.putString(GLO_CONF_ROAMING_VOICEMAIL_NUMBER, config.getString(GLO_CONF_ROAMING_VOICEMAIL_NUMBER));
        filtered.putString(GLO_CONF_ECC_LIST_NO_CARD, config.getString(GLO_CONF_ECC_LIST_NO_CARD));
        filtered.putString(GLO_CONF_ECC_LIST_WITH_CARD, config.getString(GLO_CONF_ECC_LIST_WITH_CARD));
        filtered.putString(GLO_CONF_FAKE_ECC_LIST_WITH_CARD, config.getString(GLO_CONF_FAKE_ECC_LIST_WITH_CARD));
        filtered.putInt(GLO_CONF_NUM_MATCH, config.getInt(GLO_CONF_NUM_MATCH));
        filtered.putInt(GLO_CONF_NUM_MATCH_RULE, config.getInt(GLO_CONF_NUM_MATCH_RULE));
        filtered.putInt(GLO_CONF_NUM_MATCH_SHORT, config.getInt(GLO_CONF_NUM_MATCH_SHORT));
        filtered.putString(GLO_CONF_SPN, config.getString(GLO_CONF_SPN));
        filtered.putBoolean(GLO_CONF_SMS_7BIT_ENABLED, config.getBoolean(GLO_CONF_SMS_7BIT_ENABLED));
        filtered.putString(GLO_CONF_SMS_CODING_NATIONAL, config.getString(GLO_CONF_SMS_CODING_NATIONAL));
        filtered.putBoolean(GLO_CONF_MVNO, config.getBoolean(GLO_CONF_MVNO));
        return filtered;
    }
}
