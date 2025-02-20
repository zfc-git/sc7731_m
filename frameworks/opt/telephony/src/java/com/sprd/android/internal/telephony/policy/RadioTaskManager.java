
package com.android.internal.telephony.policy;

import android.app.AddonManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.RadioCapbility;
import android.telephony.TelephonyManager.RadioFeatures;
import android.util.Log;

import com.android.internal.telephony.policy.IccPolicy.SimPriority;
import com.android.internal.telephony.policy.RadioTaskExecutor.SubTask;
import com.android.internal.telephony.policy.RadioTaskExecutor.Task;

public class RadioTaskManager {

    enum Event {
        AIRPLANE_MODE,
        STAND_BY_SET,
        SET_PRIMARY_CARD,
        LTE_ENABLE,
        MANUAL_NETWORK_MODE,
        UNKNOWN
    }

    private static final String LOG_TAG = "RadioTaskManager";
    private static final String CHANGE_NETMODE_BY_EM = "persist.sys.cmccpolicy.disable";
    private static final String NETWORK_TYPE_PREF_NAME = "network.type.info";
    private static final String NETWORK_TYPE = "network_type";
    /** SPRD: Add NetworkMode for VoLTE. @{ */
    private static final String MANNUL_SET_NETWORK_TYPE = "network.type.manual";
    public SharedPreferences mManuSetNetworkType;
    // private boolean isVolteEnabled = TelephonyManager.getVolteEnabled();FIXME
    /** @} */
    private static IccPolicy mIccPolicy;
    private static RadioFeatures[] mRadioFeatures;
    private static RadioTaskManager mInstance;
    private static RadioTaskManager sMe;
    private RadioTaskExecutor mRadioTaskExecutor;
    private SharedPreferences mNetworkTypePref;
    private TelephonyManager mTelephonyManager;
    private boolean mDefaultDataPhoneIdNeedUpdate = true;
    private int mPhoneCount = 1;
    private Context mContext;

    private static final int PREFERRED_NETWORK_MODE_4G_3G_2G = 0;
    private static final int PREFERRED_NETWORK_MODE_3G_2G = 1;
    private static final int PREFERRED_NETWORK_MODE_4G_ONLY = 2;
    private static final int PREFERRED_NETWORK_MODE_3G_ONLY = 3;
    private static final int PREFERRED_NETWORK_MODE_2G_ONLY = 4;
    private static final int PREFERRED_NETWORK_MODE_UNKNOWN = -1;

    public RadioTaskManager(Context context) {
        mContext = context;
        sMe = this;
    }

    public void onCreate(int featureId) {
        mTelephonyManager = (TelephonyManager) TelephonyManager.from(mContext);
        mPhoneCount = mTelephonyManager.getPhoneCount();
        mRadioFeatures = new RadioFeatures[mPhoneCount];
        mNetworkTypePref = mContext.getSharedPreferences(NETWORK_TYPE_PREF_NAME, 0);
        mRadioTaskExecutor = RadioTaskExecutor.getInstance(mContext);
        if (mIccPolicy == null) {
            mIccPolicy = getInstance(mContext, featureId).createIccPolicy();
        }
        /** SPRD: Add NetworkMode for VoLTE. @{ */
        mManuSetNetworkType = mContext.getSharedPreferences(MANNUL_SET_NETWORK_TYPE, 0);
        /** @} */
    }

    public static RadioTaskManager getDefault() {
        return sMe;
    }

    public RadioTaskManager() {
    }

    private static RadioTaskManager getInstance(Context context, int featureId) {
        if (mInstance != null)
            return mInstance;
        mInstance = (RadioTaskManager) new AddonManager(context)
        .getAddon(featureId, RadioTaskManager.class);
        // addon return null point ,so FIXME
        if (mInstance == null) {
            mInstance = new RadioTaskManager();
        }
        // end
        return mInstance;
    }

    public IccPolicy createIccPolicy() {
        Log.d(LOG_TAG, "create general policy");
        return new GeneralPolicy();
    }

    public void setAirplaneMode(boolean airplane) {
        Log.d(LOG_TAG, "setAirplaneMode : " + airplane);
        Task task = new Task(Event.AIRPLANE_MODE, !airplane);
        addTask(task);
    }

    public void setSimStandby(int phoneId, boolean standby) {
        Log.d(LOG_TAG, "setSimStandby[" + phoneId + "]: " + standby);
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            Settings.Global.putInt(mContext.getContentResolver(),
                    Settings.Global.SIM_STANDBY + phoneId, standby ? 1 : 0);

            if (isSwitchPrimaryCardNecessary()) {
                autoSetPrimaryCardAccordingToPolicy();
            } else {
                Task task = new Task(Event.STAND_BY_SET, phoneId, standby);
                addTask(task);
            }
        }
    }

    private boolean isSwitchPrimaryCardNecessary() {
        mIccPolicy.updateSimPriorities();
        int primaryCard = mTelephonyManager.getPrimaryCard();
        if (!mTelephonyManager.isSimStandby(primaryCard)) {
            SimPriority[] simPriorities = mIccPolicy.getSimPriorities();
            for (int i = 0; i < mPhoneCount; i++) {
                if (i != primaryCard && simPriorities[i].compareTo(SimPriority.DISABLE) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setLteEnabled(boolean enabled) {
        Log.d(LOG_TAG, "setLteEnabled: " + enabled);
        int mode = enabled ? PREFERRED_NETWORK_MODE_4G_3G_2G : PREFERRED_NETWORK_MODE_3G_2G;
        int primaryCard = mTelephonyManager.getPrimaryCard();
        if (SubscriptionManager.isValidPhoneId(primaryCard)) {
            int internalNetType = transferToInternalNetworkTpye(mode);
            savePreferredNetworkType(internalNetType);
            updateRadioFeatures(primaryCard);
        }
        Task task = new Task(Event.LTE_ENABLE, primaryCard);
        addTask(task);
    }

    /* SPRD: modify by bug450244 @{ */
    public boolean getLteEnabled() {
        int networkMode = getPreferredNetworkModeForPhone(mTelephonyManager.getPrimaryCard());
        return networkMode == PREFERRED_NETWORK_MODE_4G_3G_2G
                || networkMode == PREFERRED_NETWORK_MODE_4G_ONLY;
    }

    /* @} */

    public void manualSetPrimaryCard(int phoneId) {
        Log.d(LOG_TAG, "Manual set primary card. phoneId = " + phoneId);
        savePrimaryCardValue(phoneId);
        updateRadioFeatures(phoneId);
        Task task = new Task(Event.SET_PRIMARY_CARD, phoneId);
        addTask(task);
    }

    public void autoSetPrimaryCardAccordingToPolicy() {
        mIccPolicy.updateSimPriorities();
        if (mIccPolicy.isPrimaryCardNeedManualSet()) {
            Log.d(LOG_TAG, "Can't auto set primary card according to policy.");
            return;
        }
        int autoSetPrimaryCard = mIccPolicy.getPrimaryCardAccordingToPolicy();
        Log.d(LOG_TAG, "Auto set primary card according to policy. autoSetprimaryCard = "
                + autoSetPrimaryCard);
        savePrimaryCardValue(autoSetPrimaryCard);
        updateRadioFeatures(autoSetPrimaryCard);
        Task task = new Task(Event.SET_PRIMARY_CARD, autoSetPrimaryCard);
        addTask(task);
    }

    /**
     * Update radio features according to giving primary card and radio cap.
     *
     * @param primaryCard
     */
    private void updateRadioFeatures(int primaryCard) {
        RadioCapbility radioCap = TelephonyManager.getRadioCapbility();
        Log.d(LOG_TAG, "updateRadioFeatures: radioCap = " + radioCap.toString());
        /** SPRD: Add NetworkMode for VoLTE. @{ */
        int networkType = getPreferredNetworkType();
        Log.d(LOG_TAG, "PreferredNetworkType = " + networkType);
        /** @} */
        /*SPRD: Bug 474688 Add for SIM hot plug feature @{*/
        boolean isHotSwapSupported = mContext.getResources().getBoolean( com.android.internal.R.bool.config_hotswapCapable) &&
                SystemProperties.getBoolean("persist.sys.hotswap.enable", true);
        /*@}*/
        for (int i = 0; i < mPhoneCount; i++) {
            if (i == primaryCard) {
                if(networkType != TelephonyManager.NT_UNKNOWN && TelephonyManager.isDeviceSupportLte()) {
                    mRadioFeatures[i] = getRadioFeatureByNetworkType(networkType);
                } else {
                    if (radioCap == RadioCapbility.CSFB) {
                        mRadioFeatures[i] = RadioFeatures.TD_LTE_AND_LTE_FDD_AND_W_AND_TD_AND_GSM_CSFB;
                    } else if (radioCap == RadioCapbility.TDD_CSFB) {
                        mRadioFeatures[i] = RadioFeatures.TD_LTE_AND_TD_AND_GSM_CSFB;
                    } else if (radioCap == RadioCapbility.FDD_CSFB) {
                        mRadioFeatures[i] = RadioFeatures.TD_LTE_AND_LTE_FDD_AND_W_AND_GSM_CSFB;
                    } else {
                        mRadioFeatures[i] = RadioFeatures.WCDMA_AND_GSM;
                    }
                    networkType = resetNetworkType(primaryCard);
                    savePreferredNetworkType(networkType);
                }
            } else {
                if (!mTelephonyManager.hasIccCard(i) && !isHotSwapSupported) {
                    mRadioFeatures[i] = RadioFeatures.NONE;
                } else {
                    mRadioFeatures[i] = RadioFeatures.GSM_ONLY;
                }
            }
        }
    }

    private void addTask(Task task) {
        fillSubTasks(task);
        mRadioTaskExecutor.addTask(task);
    }

    private void fillSubTasks(Task task) {
        Log.d(LOG_TAG, task.mEvent.toString());
        switch (task.mEvent) {
            case AIRPLANE_MODE:
                task.addSubTask(task.mEnable ?
                        SubTask.RADIO_POWER_ON : SubTask.RADIO_POWER_OFF);
                break;
            case STAND_BY_SET:
                task.addSubTask(task.mEnable ?
                        SubTask.RADIO_POWER_ON : SubTask.RADIO_POWER_OFF);
                task.addSubTask(SubTask.AUTO_SET_DEFAULT_PHONE);
                task.addSubTask(SubTask.UPDATA_DEFAULT_DATA);
                break;

            case SET_PRIMARY_CARD:

                if (!needPowerRadioOnDirectly()) {
                    task.addSubTask(SubTask.RADIO_POWER_OFF,
                            RadioTaskExecutor.ALL_RADIOS_NEED_OPERATION);

                    task.addSubTask(SubTask.SEND_TESTMODE);
                }
                // SPRD: modify for bug 439476
                if (mDefaultDataPhoneIdNeedUpdate
                        || SubscriptionManager.from(mContext).getDefaultDataSubId()
                        == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {

                    task.addSubTask(SubTask.UPDATA_DEFAULT_DATA);
                }
                mDefaultDataPhoneIdNeedUpdate = true;

                task.addSubTask(SubTask.AUTO_SET_DEFAULT_PHONE);

                task.addSubTask(SubTask.RADIO_POWER_ON, RadioTaskExecutor.ALL_RADIOS_NEED_OPERATION);

                task.addSubTask(SubTask.BROADCAST_SET_PRIMARY_CARD_COMPLETE);
                break;

            case LTE_ENABLE:
                task.addSubTask(SubTask.RADIO_POWER_OFF);
                task.addSubTask(SubTask.SEND_TESTMODE);
                task.addSubTask(SubTask.RADIO_POWER_ON);
                break;

            case MANUAL_NETWORK_MODE:
                task.addSubTask(SubTask.RADIO_POWER_OFF);
                task.addSubTask(SubTask.SEND_TESTMODE);
                task.addSubTask(SubTask.RADIO_POWER_ON);
                break;

            default:
                Log.d(LOG_TAG, "Unknown event.");
                break;
        }
    }

    /**
     * It is required to reset radios only when radio features has been changed.
     *
     * @return
     */
    private boolean needPowerRadioOnDirectly() {
        return !hasRadioFeaturesChanged();
    }

    private boolean hasRadioFeaturesChanged() {
        boolean hasChanged = false;
        if (getPreferredNetworkType() == TelephonyManager.NT_UNKNOWN) {
            return true;
        }
        for (int i = 0; i < mPhoneCount; i++) {
            RadioFeatures lastRadioFeatures = TelephonyManager.getRadioFeatures(i);
            if (lastRadioFeatures != mRadioFeatures[i]) {
                hasChanged = true;
                break;
            }
        }
        Log.d(LOG_TAG, "Has radio features changed : " + hasChanged);
        return hasChanged;
    }

    /**
     * Save preferred network type set by subscriber.This value will be cleared after restored
     * factory settings, then reset network type to default(4G/3G/2G).
     *
     * @param enabled
     */
    public void savePreferredNetworkType(int mode) {
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE_TYPE,mode);
    }

    private int  getPreferredNetworkType() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE_TYPE,-1);
    }

    private void savePrimaryCardValue(int phoneId) {
        Log.d(LOG_TAG, "setMainSlot phoneId = " + phoneId);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.SERVICE_PRIMARY_CARD, phoneId);
    }

    static RadioFeatures[] getRadioFeatures() {
        return mRadioFeatures;
    }

    boolean canPowerRadioOn(int phoneId) {
        int primaryCard = mTelephonyManager.getPrimaryCard();
        return primaryCard != SubscriptionManager.INVALID_SIM_SLOT_INDEX
                && mIccPolicy.getSimPriorities()[phoneId].isAllowedPowerRadioOn();
    }

    public boolean isPrimaryCardNeedManualSet() {
        mIccPolicy.updateSimPriorities();
        return mIccPolicy.isPrimaryCardNeedManualSet();
    }

    public void setDefaultDataPhoneIdNeedUpdate(boolean need) {
        mDefaultDataPhoneIdNeedUpdate = need;
    }

    public boolean hasUsimCard(int phoneId) {
        return mIccPolicy.hasUsimCard(phoneId);
    }

    public boolean hasSimLocked() {
        return mIccPolicy.hasSimLocked();
    }

    public void clearManualPreferenceNetwork() {
        Log.d(LOG_TAG, "--clearManualPreferenceNetwork--");
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.PREFERRED_NETWORK_MODE_TYPE,-1);
    }
    /** @} */

    /*SPRD: Bug 474688 Add for SIM hot plug feature @{*/
    public void handleHotPlug(int phoneId, boolean in){
        Log.d(LOG_TAG, "handleHotPlug[" + phoneId + "]: " + in);
        mIccPolicy.updateSimPriorities();
        int primaryCard = mTelephonyManager.getPrimaryCard();
        updateRadioFeatures(primaryCard);
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            Task task = new Task(Event.STAND_BY_SET, phoneId, in);
            addTask(task);
        }
    }
    public void updateSimPrioritiesHotPlug() {
        mIccPolicy.updateSimPriorities();
    }
    /*@}*/

    /* SPRD: [Bug543427] Add interfaces for setting or getting internal preferred network type. @{ */
    public void setInternalPreferredNetworkTypeForPhone(int phoneId, int networkType) {
        Log.d(LOG_TAG, "setExtendedPreferredNetworkTypeForPhone[" + phoneId + "]: " + networkType);
        if (!SubscriptionManager.isValidPhoneId(phoneId)
                || phoneId != mTelephonyManager.getPrimaryCard()) {
            return;
        }

        savePreferredNetworkType(networkType);
        updateRadioFeatures(phoneId);

        Task task = new Task(Event.MANUAL_NETWORK_MODE, phoneId);
        addTask(task);
    }

    public int getInternalPreferredNetworkTypeForPhone(int phoneId) {
        int networkType = TelephonyManager.NT_UNKNOWN;
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            if (phoneId == mTelephonyManager.getPrimaryCard()) {
                networkType = getPreferredNetworkType();
            } else {
                networkType = TelephonyManager.NT_GSM;
            }
        }
        return networkType;
    }

    private int resetNetworkType(int phoneId) {
        int networkType = TelephonyManager.NT_UNKNOWN;
        if (mRadioFeatures[phoneId] == RadioFeatures.GSM_ONLY
                || mRadioFeatures[phoneId] == RadioFeatures.PRIMARY_GSM_ONLY) {
            networkType = TelephonyManager.NT_GSM;
        } else if (mRadioFeatures[phoneId] != RadioFeatures.NONE) {
            networkType = mRadioFeatures[phoneId].ordinal();
        }
        return networkType;
    }

    private RadioFeatures getRadioFeatureByNetworkType(int networkType) {
        RadioFeatures radioFeature = RadioFeatures.NONE;
        if (networkType == TelephonyManager.NT_GSM) {
            radioFeature = mTelephonyManager.isMultiSimEnabled() ? RadioFeatures.PRIMARY_GSM_ONLY
                    : RadioFeatures.GSM_ONLY;
        } else {
            RadioFeatures[] radioFeatures = RadioFeatures.values();
            if (networkType > 0 && networkType < radioFeatures.length) {
                radioFeature = radioFeatures[networkType];
            }
        }
        return radioFeature;
    }

    public void setPreferredNetworkModeForPhone(int phoneId, int mode) {
        setInternalPreferredNetworkTypeForPhone(phoneId, transferToInternalNetworkTpye(mode));
    }

    public int getPreferredNetworkModeForPhone(int phoneId) {
        int internalNetType = getInternalPreferredNetworkTypeForPhone(phoneId);
        switch (internalNetType) {
            case TelephonyManager.NT_LTE_FDD_TD_LTE_WCDMA_TDSCDMA_GSM:
            case TelephonyManager.NT_LTE_FDD_TD_LTE_TDSCDMA_GSM:
            case TelephonyManager.NT_LTE_FDD_TD_LTE_WCDMA_GSM:
            case TelephonyManager.NT_TD_LTE_TDSCDMA_GSM:
            case TelephonyManager.NT_LTE_FDD_WCDMA_GSM:
            case TelephonyManager.NT_TD_LTE_WCDMA_GSM:
                return PREFERRED_NETWORK_MODE_4G_3G_2G;
            case TelephonyManager.NT_WCDMA_TDSCDMA_GSM:
            case TelephonyManager.NT_WCDMA_GSM:
            case TelephonyManager.NT_TDSCDMA_GSM:
                return PREFERRED_NETWORK_MODE_3G_2G;
            case TelephonyManager.NT_LTE_FDD_TD_LTE:
            case TelephonyManager.NT_LTE_FDD:
            case TelephonyManager.NT_TD_LTE:
                return PREFERRED_NETWORK_MODE_4G_ONLY;
            case TelephonyManager.NT_WCDMA:
            case TelephonyManager.NT_TDSCDMA:
                return PREFERRED_NETWORK_MODE_3G_ONLY;
            case TelephonyManager.NT_GSM:
                return PREFERRED_NETWORK_MODE_2G_ONLY;
            default:
                return PREFERRED_NETWORK_MODE_UNKNOWN;
        }
    }

    private int transferToInternalNetworkTpye(int mode) {
        RadioCapbility rCapbility = TelephonyManager.getRadioCapbility();
        int networkType = -1;
        switch (mode) {
            case PREFERRED_NETWORK_MODE_4G_3G_2G:
                if (rCapbility == RadioCapbility.CSFB) {
                    networkType = TelephonyManager.NT_LTE_FDD_TD_LTE_WCDMA_TDSCDMA_GSM;
                } else if (rCapbility == RadioCapbility.FDD_CSFB) {
                    networkType = TelephonyManager.NT_LTE_FDD_TD_LTE_WCDMA_GSM;
                } else if (rCapbility == RadioCapbility.TDD_CSFB) {
                    networkType = TelephonyManager.NT_TD_LTE_TDSCDMA_GSM;
                }
                break;
            case PREFERRED_NETWORK_MODE_3G_2G:
                if (rCapbility == RadioCapbility.CSFB) {
                    networkType = TelephonyManager.NT_WCDMA_TDSCDMA_GSM;
                } else if (rCapbility == RadioCapbility.FDD_CSFB) {
                    networkType = TelephonyManager.NT_WCDMA_GSM;
                } else if (rCapbility == RadioCapbility.TDD_CSFB) {
                    networkType = TelephonyManager.NT_TDSCDMA_GSM;
                }
                break;
            case PREFERRED_NETWORK_MODE_4G_ONLY:
                if (rCapbility == RadioCapbility.CSFB ||
                        rCapbility == RadioCapbility.FDD_CSFB) {
                    networkType = TelephonyManager.NT_LTE_FDD_TD_LTE;
                } else if (rCapbility == RadioCapbility.TDD_CSFB) {
                    networkType = TelephonyManager.NT_TD_LTE;
                }
                break;
            case PREFERRED_NETWORK_MODE_3G_ONLY:
                networkType = TelephonyManager.NT_WCDMA;
                break;
            case PREFERRED_NETWORK_MODE_2G_ONLY:
                networkType = TelephonyManager.NT_GSM;
                break;
        }
        return networkType;
    }
    /* @} */
}
