package com.android.internal.telephony.plugin;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.DctConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.dataconnection.DataConnection;
import com.android.internal.telephony.dataconnection.DcFailCause;
import com.android.internal.telephony.dataconnection.DcTracker;

public class ClearCodeRetryController extends Handler {
    private static final String TAG = "ClearCode";
    // when the data can't register to LTE in 40s, we will retry again.
    private static final int SWITCH_TIMEOUT = 40 * 1000;

    private static final int IDLE = 0;
    private static final int SWITCHING_TO_3G = 1;
    private static final int SWITCHING_TO_4G = 2;
    private static final int MAX_PDN_REJ_TIMES = 3;

    private PhoneBase mPhone;
    private DcTracker mDct;
    private Handler mAtH;
    private HandlerThread mHt;
    private boolean mLteDisabled = false;
    // PDN reject count reported by Modem
    private int mPDNRejTimes = 0;
    private int mState = IDLE;
    private MobilePhoneStateListener mPhoneStateListener;

    public ClearCodeRetryController(PhoneBase phone, DcTracker dct) {
        mPhone = phone;
        mDct =  dct;
        mPhone.mCi.registerForRauSuccess(this, DctConstants.EVENT_RAU_SUCCESS, null);
        mPhone.mCi.registerForOn(this, DctConstants.EVENT_RADIO_ON, null);
        mPhone.mCi.registerForClearCodeFallBack(this, DctConstants.EVENT_CLEAR_CODE_FALLBACK,
                null);
        mHt = new HandlerThread(TAG);
        mHt.start();
        mAtH = new AtHandler(mHt.getLooper(), phone);
    }

    public void dispose() {
        logcc("ClearCodeRetryController.dispose()");
        mPhone.mCi.unregisterForRauSuccess(this);
        mPhone.mCi.unregisterForOn(this);
        mPhone.mCi.unregisterForClearCodeFallBack(this);
        mHt.quit();
        mAtH = null;
    }

    private class MobilePhoneStateListener extends PhoneStateListener {
        public MobilePhoneStateListener(int subId) {
            super(subId);
        }

        @Override
        public void onServiceStateChanged(ServiceState state) {
            int voiceNetType = state.getVoiceNetworkType();
            int voiceRegState = state.getVoiceRegState();
            logcc("onServiceStateChanged voiceNetType=" + voiceNetType
                    + " voiceRegState=" + voiceRegState);
            handleVoiceServiceChange(voiceNetType, voiceRegState);
        }
    }

    public void registerVoiceStateListener() {
        if (mPhoneStateListener == null) {
            mPhoneStateListener = new MobilePhoneStateListener(mPhone.getSubId());
        }
        logcc("registerVoiceStateListener subId = " + mPhone.getSubId());
        TelephonyManager.from(mPhone.getContext()).listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SERVICE_STATE);
    }

    public void unregisterVoiceStateListener() {
        logcc("unregisterVocieStateListener");
        TelephonyManager.from(mPhone.getContext()).listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_NONE);
        mPhoneStateListener = null;
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case DctConstants.EVENT_TRY_SETUP_DATA:
                logcc("EVENT_TRY_SETUP_DATA");
                mDct.clearPreFailCause();
                mState = IDLE;
                mDct.sendMessage(mDct.obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, msg.obj));
                break;

            case DctConstants.EVENT_RAU_SUCCESS:
                logcc("EVENT_RAU_SUCCESS");
                //mPhone.getContext().sendBroadcast(new Intent("android.network.RAU_SUCCESS"));
                onDataConnectionRau();
                break;

            case DctConstants.EVENT_RADIO_ON:
                logcc("EVENT_RADIO_ON");
                mDct.clearPreFailCause();
                mState = IDLE;
                // in case the lte is disabled before, enable it now
                if (mLteDisabled || mPDNRejTimes >= MAX_PDN_REJ_TIMES) {
                    mPDNRejTimes = 0;
                    enableLte(true);
                    mLteDisabled = false;
                }
                break;

            case DctConstants.EVENT_CLEAR_CODE_FALLBACK:
                logcc("EVENT_CLEAR_CODE_FALLBACK");
                mPDNRejTimes++;
                break;

            case DctConstants.EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                logcc("EVENT_RADIO_OFF_OR_NOT_AVAILABLE");
                // clear flags when radio is off
                mDct.clearPreFailCause();
                mState = IDLE;
                break;
        }
    }

    public void switchTo3G() {
        logcc("Start switching to 3G...");
        dataConnectionAttach(false);
        enableLte(false);
        mLteDisabled = true;
        mState = SWITCHING_TO_3G;
        registerVoiceStateListener();
    }

    // Wait for the RAT we want to register to
    public void handleDataServiceChange(int rat, int regState) {
        logcc("Data RAT=" + rat + ", regState=" + regState + ", mState = " + mState);
        if (mState == SWITCHING_TO_3G) {
            if (rat != ServiceState.RIL_RADIO_TECHNOLOGY_LTE &&
                    rat != ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN) {
                // retry in 3G, so clear mPreFailcause
                mDct.clearPreFailCause();
                mState = IDLE;
            }
        } else if (mState == SWITCHING_TO_4G) {
            if (rat == ServiceState.RIL_RADIO_TECHNOLOGY_LTE) {
                // restart from beginning, so clear mPreFailcause
                mPDNRejTimes = 0;
                mDct.clearPreFailCause();
                mState = IDLE;
                this.removeMessages(DctConstants.EVENT_TRY_SETUP_DATA);
            }
        }
    }

    // Finish the "detach -> disable lte -> attach" sequence
    public void handleVoiceServiceChange(int rat, int regState) {
        if (rat != TelephonyManager.NETWORK_TYPE_LTE
                && regState == ServiceState.STATE_IN_SERVICE) {
            logcc("Voice registerd on " + rat + ", switching to 3G completed");
            dataConnectionAttach(true);
            unregisterVoiceStateListener();
        }
    }

    private void onDataConnectionRau() {
        restartCycle(Phone.REASON_ROUTING_AREA_UPDATE);
        // We will restart from begin, so stop all retry alarms
        mDct.stopFailRetryAlarm();
        mDct.stopRestartSetupDataAlarm();
    }

    // After 2 hours or RAU, restart from beginning
    public void restartCycle(Object obj) {
        logcc("restartCycle: " + obj + ", mLteDisabled = " + mLteDisabled
                + ", mPDNRejTimes = " + mPDNRejTimes);
        mState = IDLE;
        if (mLteDisabled || mPDNRejTimes >= MAX_PDN_REJ_TIMES) {
            mPDNRejTimes = 0;
            enableLte(true);
            mState = SWITCHING_TO_4G;
            mLteDisabled = false;
            // wait for RAT change to 4G or timeout
            sendMessageDelayed(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, obj),
                    SWITCH_TIMEOUT);
        } else {
            sendMessage(obtainMessage(DctConstants.EVENT_TRY_SETUP_DATA, obj));
        }
    }

    private void enableLte(boolean enable) {
        if (mAtH != null) {
            mAtH.sendMessage(mAtH.obtainMessage(AtHandler.MSG_ENABLE_LTE, enable ? 1 : 0, 0));
        }
    }

    private void dataConnectionAttach(boolean attach) {
        if (mAtH != null) {
            mAtH.sendMessage(mAtH.obtainMessage(AtHandler.MSG_ATTACH_DATA, attach ? 1 : 0, 0));
        }
    }

    private void logcc(String s) {
        Rlog.d(TAG, "[" + mPhone.getPhoneId() + "]" + s);
    }
}

/**
 * Helper class to send AT commands in dedicate thread
 */
class AtHandler extends Handler {
    public static final int MSG_ATTACH_DATA = 0;
    public static final int MSG_ENABLE_LTE = 1;

    private final String[] AT_ENABLE_LTE  = new String[] { "AT+SPEUTRAN=1" };
    private final String[] AT_DISABLE_LTE = new String[] { "AT+SPEUTRAN=0" };
    private final String[] AT_DETACH_DATA = new String[] { "AT+CGATT=0" };
    private final String[] AT_ATTACH_DATA = new String[] { "AT+CGATT=1" };

    private PhoneBase mPhone;
    public AtHandler(Looper looper, PhoneBase phone) {
        super(looper);
        mPhone = phone;
    }

    public void handleMessage(Message msg) {
        String[] atCommand = null;
        switch (msg.what) {
            case MSG_ATTACH_DATA:
                boolean attach = msg.arg1 == 1;
                atCommand = attach ? AT_ATTACH_DATA : AT_DETACH_DATA;
                break;
            case MSG_ENABLE_LTE:
                boolean enable = msg.arg1 == 1;
                atCommand = enable ? AT_ENABLE_LTE : AT_DISABLE_LTE;
                break;
        }

        if (atCommand != null) {
            mPhone.invokeOemRilRequestStrings(atCommand, null);
        }
    }
}
