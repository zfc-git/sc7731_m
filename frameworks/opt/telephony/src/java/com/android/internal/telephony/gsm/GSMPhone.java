/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.gsm;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.telecom.VideoProfile;
import android.telephony.CellLocation;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CallTracker;

import android.text.TextUtils;
import android.telephony.Rlog;
import android.util.Log;

import com.android.ims.ImsManager;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.SERVICE_CLASS_VOICE;
import com.android.internal.telephony.dataconnection.DcTracker;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.RilVideoEx;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.test.SimulatedRadioControl;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.IccVmNotSupportedException;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;
import com.android.internal.telephony.uicc.UiccController;
import com.android.internal.telephony.ServiceStateTracker;
import com.android.internal.telephony.uicc.IsimRecords;
import com.android.internal.telephony.uicc.IsimUiccRecords;
import com.android.internal.telephony.GlobalConfigManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import android.telecom.VideoProfile;


/**
 * {@hide}
 */
public class GSMPhone extends PhoneBase {
    // NOTE that LOG_TAG here is "GSM", which means that log messages
    // from this file will go into the radio log rather than the main
    // log.  (Use "adb logcat -b radio" to see them.)
    static final String LOG_TAG = "GSMPhone";
    private static final boolean LOCAL_DEBUG = true;
    private static final boolean VDBG = false; /* STOPSHIP if true */

    // Key used to read/write current ciphering state
    public static final String CIPHERING_KEY = "ciphering_key";
    // Key used to read/write voice mail number
    public static final String VM_NUMBER = "vm_number_key";
    // Key used to read/write the SIM IMSI used for storing the voice mail
    public static final String VM_SIM_IMSI = "vm_sim_imsi_key";

    // Instance Variables
    GsmCallTracker mCT;
    GsmServiceStateTracker mSST;
    ArrayList <GsmMmiCode> mPendingMMIs = new ArrayList<GsmMmiCode>();
    SimPhoneBookInterfaceManager mSimPhoneBookIntManager;
    PhoneSubInfo mSubInfo;


    Registrant mPostDialHandler;

    /** List of Registrants to receive Supplementary Service Notifications. */
    RegistrantList mSsnRegistrants = new RegistrantList();

    // mEcmTimerResetRegistrants are informed after Ecm timer is canceled or re-started
    private final RegistrantList mEcmTimerResetRegistrants = new RegistrantList();

    private String mImei;
    private String mImeiSv;
    private String mVmNumber;

    private IsimUiccRecords mIsimUiccRecords;

    /* SPRD: Add interfaces for pin/pin2/puk/puk2 remain times. @{ */
    private final Object mLock = new Object();
    private int mRemainTimes = -1;
    SyncHandler mHandler;
    /* @} */
    /* SPRD: add for bug479433 @{ */
    public static final String VIDEO_STATE = "video_state";
    protected static final int EVENT_FALLBACK = RILConstants.RIL_UNSOL_VIDEOPHONE_DSCI;
    protected static final int EVENT_VIDEOCALLFAIL = RILConstants.RIL_UNSOL_VIDEOPHONE_RELEASING;
    protected static final int EVENT_VIDEOCALLCODEC = RILConstants.RIL_UNSOL_VIDEOPHONE_CODEC;
    /* @} */

    private int mSimlockRemainTimes = -1;//add for simlock

    private boolean isSimLocked = false;

    // Create Cfu (Call forward unconditional) so that dialing number &
    // mOnComplete (Message object passed by client) can be packed &
    // given as a single Cfu object as user data to RIL.
    private static class Cfu {
        final String mSetCfNumber;
        final Message mOnComplete;

        Cfu(String cfNumber, Message onComplete) {
            mSetCfNumber = cfNumber;
            mOnComplete = onComplete;
        }
    }

    // Constructors

    public
    GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super("GSM", notifier, context, ci, unitTestMode);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);
        mDcTracker = new DcTracker(this);

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        mCi.setOnSs(this, EVENT_SS, null);
        setProperties();
        /* SPRD: add for bug479433 @{ */
        mCi.registerForOemHookRaw(this, EVENT_FALLBACK, null);
        mCi.registerForOemHookRaw(this, EVENT_VIDEOCALLFAIL, null);
        mCi.registerForOemHookRaw(this, EVENT_VIDEOCALLCODEC, null);
        /* @} */
    }

    public
    GSMPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, int phoneId) {
        this(context, ci, notifier, false, phoneId);
    }

    public
    GSMPhone(Context context, CommandsInterface ci,
            PhoneNotifier notifier, boolean unitTestMode, int phoneId) {
        super("GSM", notifier, context, ci, unitTestMode, phoneId);

        if (ci instanceof SimulatedRadioControl) {
            mSimulatedRadioControl = (SimulatedRadioControl) ci;
        }

        mCi.setPhoneType(PhoneConstants.PHONE_TYPE_GSM);
        mCT = new GsmCallTracker(this);

        mSST = new GsmServiceStateTracker(this);
        mDcTracker = new DcTracker(this);

        if (!unitTestMode) {
            mSimPhoneBookIntManager = new SimPhoneBookInterfaceManager(this);
            mSubInfo = new PhoneSubInfo(this);
        }

        mCi.registerForAvailable(this, EVENT_RADIO_AVAILABLE, null);
        mCi.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.setOnUSSD(this, EVENT_USSD, null);
        mCi.setOnSuppServiceNotification(this, EVENT_SSN, null);
        mSST.registerForNetworkAttached(this, EVENT_REGISTERED_TO_NETWORK, null);
        mCi.setOnSs(this, EVENT_SS, null);
        setProperties();
        /* SPRD: Add interfaces for pin/pin2/puk/puk2 remain times. @{ */
        HandlerThread thread = new HandlerThread("SyncSender");
        thread.start();
        mHandler = new SyncHandler(thread.getLooper());
        /* @} */
        log("GSMPhone: constructor: sub = " + mPhoneId);

        setProperties();
        /* SPRD: add for bug479433 @{ */
        mCi.registerForOemHookRaw(this, EVENT_FALLBACK, null);
        mCi.registerForOemHookRaw(this, EVENT_VIDEOCALLFAIL, null);
        mCi.registerForOemHookRaw(this, EVENT_VIDEOCALLCODEC, null);
        /* @} */
    }

    protected void setProperties() {
        TelephonyManager.from(mContext).setPhoneType(getPhoneId(), PhoneConstants.PHONE_TYPE_GSM);
    }

    @Override
    public void dispose() {
        synchronized(PhoneProxy.lockForRadioTechnologyChange) {
            super.dispose();

            //Unregister from all former registered events
            mCi.unregisterForAvailable(this); //EVENT_RADIO_AVAILABLE
            unregisterForSimRecordEvents();
            mCi.unregisterForOffOrNotAvailable(this); //EVENT_RADIO_OFF_OR_NOT_AVAILABLE
            mCi.unregisterForOn(this); //EVENT_RADIO_ON
            mSST.unregisterForNetworkAttached(this); //EVENT_REGISTERED_TO_NETWORK
            mCi.unSetOnUSSD(this);
            mCi.unSetOnSuppServiceNotification(this);
            mCi.unSetOnSs(this);

            mPendingMMIs.clear();

            //Force all referenced classes to unregister their former registered events
            mCT.dispose();
            mDcTracker.dispose();
            mSST.dispose();
            mSimPhoneBookIntManager.dispose();
            mSubInfo.dispose();
            // SPRD: add for bug479433
            mCi.unregisterForOemHookRaw(this);
        }
    }

    @Override
    public void removeReferences() {
        Rlog.d(LOG_TAG, "removeReferences");
        mSimulatedRadioControl = null;
        mSimPhoneBookIntManager = null;
        mSubInfo = null;
        mCT = null;
        mSST = null;

        super.removeReferences();
    }

    @Override
    protected void finalize() {
        if(LOCAL_DEBUG) Rlog.d(LOG_TAG, "GSMPhone finalized");
    }

    @Override
    public ServiceState
    getServiceState() {
        if (mSST == null || mSST.mSS.getState() != ServiceState.STATE_IN_SERVICE) {
            if (mImsPhone != null) {
                return ServiceState.mergeServiceStates(
                        (mSST == null) ? new ServiceState() : mSST.mSS,
                        mImsPhone.getServiceState());
            }
        }

        if (mSST != null) {
            return mSST.mSS;
        } else {
            // avoid potential NPE in EmergencyCallHelper during Phone switch
            return new ServiceState();
        }
    }

    @Override
    public CellLocation getCellLocation() {
        return mSST.getCellLocation();
    }

    @Override
    public PhoneConstants.State getState() {
        if (mImsPhone != null) {
            PhoneConstants.State imsState = mImsPhone.getState();
            if (imsState != PhoneConstants.State.IDLE) {
                return imsState;
            }
        }

        return mCT.mState;
    }

    @Override
    public int getPhoneType() {
        return PhoneConstants.PHONE_TYPE_GSM;
    }

    @Override
    public ServiceStateTracker getServiceStateTracker() {
        return mSST;
    }

    @Override
    public CallTracker getCallTracker() {
        return mCT;
    }

    // pending voice mail count updated after phone creation
    private void updateVoiceMail() {
        int countVoiceMessages = 0;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            // get voice mail count from SIM
            countVoiceMessages = r.getVoiceMessageCount();
        }
        int countVoiceMessagesStored = getStoredVoiceMessageCount();
        if (countVoiceMessages == -1 && countVoiceMessagesStored != 0) {
            countVoiceMessages = countVoiceMessagesStored;
        }
        Rlog.d(LOG_TAG, "updateVoiceMail countVoiceMessages = " + countVoiceMessages
                +" subId "+getSubId());
        setVoiceMessageCount(countVoiceMessages);
    }

    @Override
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        return mPendingMMIs;
    }

    @Override
    public PhoneConstants.DataState getDataConnectionState(String apnType) {
        PhoneConstants.DataState ret = PhoneConstants.DataState.DISCONNECTED;

        if (mSST == null) {
            // Radio Technology Change is ongoning, dispose() and removeReferences() have
            // already been called

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (!apnType.equals(PhoneConstants.APN_TYPE_EMERGENCY) &&
                mSST.getCurrentDataConnectionState() != ServiceState.STATE_IN_SERVICE) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow

            // Emergency APN is available even in Out Of Service
            // Pass the actual State of EPDN

            ret = PhoneConstants.DataState.DISCONNECTED;
        } else if (mDcTracker.isApnTypeEnabled(apnType) == false ||
                mDcTracker.isApnTypeActive(apnType) == false) {
            //TODO: isApnTypeActive() is just checking whether ApnContext holds
            //      Dataconnection or not. Checking each ApnState below should
            //      provide the same state. Calling isApnTypeActive() can be removed.
            ret = PhoneConstants.DataState.DISCONNECTED;
        } else { /* mSST.gprsState == ServiceState.STATE_IN_SERVICE */
            switch (mDcTracker.getState(apnType)) {
                case RETRYING:
                case FAILED:
                case IDLE:
                    ret = PhoneConstants.DataState.DISCONNECTED;
                break;

                case CONNECTED:
                case DISCONNECTING:
                    if ((mCT.mState != PhoneConstants.State.IDLE ||
                        (getImsPhone() != null &&
                    ((ImsPhone)getImsPhone()).getCallTracker().getState() != PhoneConstants.State.IDLE))
                            && !mSST.isConcurrentVoiceAndDataAllowed()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    /* SPRD: Bug 426185S Notify SUSPENDED when other phone is in call @{ */
                    } else if (mCT.mState == PhoneConstants.State.IDLE &&
                            mDcTracker.isOtherPhoneInVoiceCall()) {
                        ret = PhoneConstants.DataState.SUSPENDED;
                    /* @} */
                    } else {
                        ret = PhoneConstants.DataState.CONNECTED;
                    }
                break;

                case CONNECTING:
                case SCANNING:
                    ret = PhoneConstants.DataState.CONNECTING;
                break;
            }
        }

        return ret;
    }

    @Override
    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;

        if (mSST.getCurrentDataConnectionState() == ServiceState.STATE_IN_SERVICE) {
            switch (mDcTracker.getActivity()) {
                case DATAIN:
                    ret = DataActivityState.DATAIN;
                break;

                case DATAOUT:
                    ret = DataActivityState.DATAOUT;
                break;

                case DATAINANDOUT:
                    ret = DataActivityState.DATAINANDOUT;
                break;

                case DORMANT:
                    ret = DataActivityState.DORMANT;
                break;

                default:
                    ret = DataActivityState.NONE;
                break;
            }
        }

        return ret;
    }

    /**
     * Notify any interested party of a Phone state change
     * {@link com.android.internal.telephony.PhoneConstants.State}
     */
    /*package*/ void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }

    /**
     * Notify registrants of a change in the call state. This notifies changes in
     * {@link com.android.internal.telephony.Call.State}. Use this when changes
     * in the precise call state are needed, else use notifyPhoneStateChanged.
     */
    /*package*/ void notifyPreciseCallStateChanged() {
        /* we'd love it if this was package-scoped*/
        super.notifyPreciseCallStateChangedP();
    }

    public void notifyNewRingingConnection(Connection c) {
        super.notifyNewRingingConnectionP(c);
    }

    /*package*/ void
    notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);

        mNotifier.notifyDisconnectCause(cn.getDisconnectCause(), cn.getPreciseDisconnectCause());
    }

    //SPRD:modify access authority for VoLTE
    public void notifyUnknownConnection(Connection cn) {
        mUnknownConnectionRegistrants.notifyResult(cn);
    }

    void notifySuppServiceFailed(SuppService code) {
        mSuppServiceFailedRegistrants.notifyResult(code);
    }

    /*package*/ void
    notifyServiceStateChanged(ServiceState ss) {
        super.notifyServiceStateChangedP(ss);
    }

    /*package*/
    void notifyLocationChanged() {
        mNotifier.notifyCellLocation(this);
    }

    @Override
    public void
    notifyCallForwardingIndicator() {
        mNotifier.notifyCallForwardingChanged(this);
    }

    // override for allowing access from other classes of this package
    /**
     * {@inheritDoc}
     */
    @Override
    public void
    setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(mPhoneId, property, value);
    }

    @Override
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        mSsnRegistrants.addUnique(h, what, obj);
        if (mSsnRegistrants.size() == 1) mCi.setSuppServiceNotifications(true, null);
    }

    @Override
    public void unregisterForSuppServiceNotification(Handler h) {
        mSsnRegistrants.remove(h);
        if (mSsnRegistrants.size() == 0) mCi.setSuppServiceNotifications(false, null);
    }

    @Override
    public void registerForSimRecordsLoaded(Handler h, int what, Object obj) {
        mSimRecordsLoadedRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForSimRecordsLoaded(Handler h) {
        mSimRecordsLoadedRegistrants.remove(h);
    }

    @Override
    public void
    acceptCall(int videoState) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if ( imsPhone != null && imsPhone.getRingingCall().isRinging() ) {
            imsPhone.acceptCall(videoState);
        } else {
            /* SPRD: call VT ,answered by voice ,callFallBack fix bug 503193 @{ */
            for (Connection c : getRingingCall().getConnections()) {
                if (videoState == 0 && c != null
                        && c.getVideoState() == VideoProfile.STATE_BIDIRECTIONAL) {
                    mCT.fallBack();
                    return;
                }
            }
            /* @} */
            mCT.acceptCall();
        }
    }

    /**
     * SPRD: Support multi-part-call mode.
     */
    public void
    acceptCall(int videoState, int mpcMode) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if ( imsPhone != null && imsPhone.getRingingCall().isRinging() ) {
            imsPhone.acceptCall(videoState);
        } else {
            /* SPRD: call VT ,answered by voice ,callFallBack fix bug 503193 @{ */
            for (Connection c : getRingingCall().getConnections()) {
                if (videoState == 0 && c != null
                        && c.getVideoState() == VideoProfile.STATE_BIDIRECTIONAL) {
                    mCT.fallBack();
                    return;
                }
            }
            /* @} */
            mCT.acceptCall(mpcMode);
        }
    }

    @Override
    public void
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    @Override
    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }

    @Override
    public boolean canConference() {
        boolean canImsConference = false;
        if (mImsPhone != null) {
            canImsConference = mImsPhone.canConference();
        }
        return mCT.canConference() || canImsConference;
    }

    public boolean canDial() {
        return mCT.canDial();
    }

    @Override
    public void conference() {
        if (mImsPhone != null && mImsPhone.canConference()) {
            log("conference() - delegated to IMS phone");
            mImsPhone.conference();
            return;
        }
        mCT.conference();
    }

    @Override
    public void clearDisconnected() {
        mCT.clearDisconnected();
    }

    @Override
    public boolean canTransfer() {
        return mCT.canTransfer();
    }

    @Override
    public void explicitCallTransfer() {
        mCT.explicitCallTransfer();
    }

    @Override
    public GsmCall
    getForegroundCall() {
        return mCT.mForegroundCall;
    }

    @Override
    public GsmCall
    getBackgroundCall() {
        return mCT.mBackgroundCall;
    }

    @Override
    public Call getRingingCall() {
        ImsPhone imsPhone = mImsPhone;
        if ( mCT.mRingingCall != null && mCT.mRingingCall.isRinging() ) {
            return mCT.mRingingCall;
        } else if ( imsPhone != null ) {
            return imsPhone.getRingingCall();
        }
        return mCT.mRingingCall;
    }

    private boolean handleCallDeflectionIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (getRingingCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 0: rejectCall");
            try {
                mCT.rejectCall();
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "reject failed", e);
                notifySuppServiceFailed(Phone.SuppService.REJECT);
            }
        } else if (getBackgroundCall().getState() != GsmCall.State.IDLE) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 0: hangupWaitingOrBackground");
            mCT.hangupWaitingOrBackground();
        }

        return true;
    }

    private boolean handleCallWaitingIncallSupplementaryService(
            String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        try {
            if (len > 1) {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';

                if (callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangupConnectionByIndex " +
                            callIndex);
                    mCT.hangupConnectionByIndex(call, callIndex);
                }
            } else {
                if (call.getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: hangup foreground");
                    //mCT.hangupForegroundResumeBackground();
                    mCT.hangup(call);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                            "MmiCode 1: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            }
        } catch (CallStateException e) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                "hangup failed", e);
            notifySuppServiceFailed(Phone.SuppService.HANGUP);
        }

        return true;
    }

    private boolean handleCallHoldIncallSupplementaryService(String dialString) {
        int len = dialString.length();

        if (len > 2) {
            return false;
        }

        GsmCall call = getForegroundCall();

        if (len > 1) {
            try {
                char ch = dialString.charAt(1);
                int callIndex = ch - '0';
                GsmConnection conn = mCT.getConnectionByIndex(call, callIndex);

                // gsm index starts at 1, up to 5 connections in a call,
                if (conn != null && callIndex >= 1 && callIndex <= GsmCallTracker.MAX_CONNECTIONS) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 2: separate call "+
                            callIndex);
                    mCT.separate(conn);
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "separate: invalid call index "+
                            callIndex);
                    notifySuppServiceFailed(Phone.SuppService.SEPARATE);
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "separate failed", e);
                notifySuppServiceFailed(Phone.SuppService.SEPARATE);
            }
        } else {
            try {
                if (getRingingCall().getState() != GsmCall.State.IDLE) {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: accept ringing call");
                    mCT.acceptCall();
                } else {
                    if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "MmiCode 2: switchWaitingOrHoldingAndActive");
                    mCT.switchWaitingOrHoldingAndActive();
                }
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                    "switch failed", e);
                notifySuppServiceFailed(Phone.SuppService.SWITCH);
            }
        }

        return true;
    }

    private boolean handleMultipartyIncallSupplementaryService(
            String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 3: merge calls");
        conference();
        return true;
    }

    private boolean handleEctIncallSupplementaryService(String dialString) {

        int len = dialString.length();

        if (len != 1) {
            return false;
        }

        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "MmiCode 4: explicit call transfer");
        explicitCallTransfer();
        return true;
    }

    private boolean handleCcbsIncallSupplementaryService(String dialString) {
        if (dialString.length() > 1) {
            return false;
        }

        Rlog.i(LOG_TAG, "MmiCode 5: CCBS not supported!");
        // Treat it as an "unknown" service.
        notifySuppServiceFailed(Phone.SuppService.UNKNOWN);
        return true;
    }

    @Override
    public boolean handleInCallMmiCommands(String dialString) throws CallStateException {
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null
                && imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE) {
            return imsPhone.handleInCallMmiCommands(dialString);
        }

        if (!isInCall()) {
            return false;
        }

        if (TextUtils.isEmpty(dialString)) {
            return false;
        }

        boolean result = false;
        char ch = dialString.charAt(0);
        switch (ch) {
            case '0':
                result = handleCallDeflectionIncallSupplementaryService(
                        dialString);
                break;
            case '1':
                result = handleCallWaitingIncallSupplementaryService(
                        dialString);
                break;
            case '2':
                result = handleCallHoldIncallSupplementaryService(dialString);
                break;
            case '3':
                result = handleMultipartyIncallSupplementaryService(dialString);
                break;
            case '4':
                result = handleEctIncallSupplementaryService(dialString);
                break;
            case '5':
                result = handleCcbsIncallSupplementaryService(dialString);
                break;
            default:
                break;
        }

        return result;
    }

    boolean isInCall() {
        GsmCall.State foregroundCallState = getForegroundCall().getState();
        GsmCall.State backgroundCallState = getBackgroundCall().getState();
        GsmCall.State ringingCallState = getRingingCall().getState();

       return (foregroundCallState.isAlive() ||
                backgroundCallState.isAlive() ||
                ringingCallState.isAlive());
    }

    @Override
    public Connection
    dial(String dialString, int videoState) throws CallStateException {
        return dial(dialString, null, videoState, null);
    }

    @Override
    public Connection
    dial (String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
            throws CallStateException {
        boolean isEmergency = PhoneNumberUtils.isEmergencyNumber(dialString);
        ImsPhone imsPhone = mImsPhone;

        boolean imsUseEnabled = isImsUseEnabled()
                 && imsPhone != null
                 && (imsPhone.isVolteEnabled() || imsPhone.isVowifiEnabled())
                 && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE);

        boolean useImsForEmergency = ImsManager.isVolteEnabledByPlatform(mContext)
                && imsPhone != null
                && isEmergency
                &&  mContext.getResources().getBoolean(
                        com.android.internal.R.bool.useImsAlwaysForEmergencyCall)
                && ImsManager.isNonTtyOrTtyOnVolteEnabled(mContext)
                && (imsPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF);

        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "imsUseEnabled=" + imsUseEnabled
                    + ", useImsForEmergency=" + useImsForEmergency
                    + ", imsPhone=" + imsPhone
                    + ", imsPhone.isVolteEnabled()="
                    + ((imsPhone != null) ? imsPhone.isVolteEnabled() : "N/A")
                    + ", imsPhone.isVowifiEnabled()="
                    + ((imsPhone != null) ? imsPhone.isVowifiEnabled() : "N/A")
                    + ", imsPhone.getServiceState().getState()="
                    + ((imsPhone != null) ? imsPhone.getServiceState().getState() : "N/A"));
        }

        ImsPhone.checkWfcWifiOnlyModeBeforeDial(mImsPhone, mContext);

        if (imsUseEnabled || useImsForEmergency) {
            try {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying IMS PS call");
                return imsPhone.dial(dialString, uusInfo, videoState, intentExtras);
            } catch (CallStateException e) {
                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "IMS PS call exception " + e +
                        "imsUseEnabled =" + imsUseEnabled + ", imsPhone =" + imsPhone);
                if (!ImsPhone.CS_FALLBACK.equals(e.getMessage())) {
                    CallStateException ce = new CallStateException(e.getMessage());
                    ce.setStackTrace(e.getStackTrace());
                    throw ce;
                }
            }
        }

        if (mSST != null && mSST.mSS.getState() == ServiceState.STATE_OUT_OF_SERVICE
                && mSST.mSS.getDataRegState() != ServiceState.STATE_IN_SERVICE && !isEmergency) {
            throw new CallStateException("cannot dial in current state");
        }
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Trying (non-IMS) CS call");
        /* SPRD: add for bug479433 @{ */
        // return dialInternal(dialString, null, VideoProfile.STATE_AUDIO_ONLY, intentExtras);
        return dialInternal(dialString, null, videoState, intentExtras);
        /* }@ */
    }

    @Override
    protected Connection
    dialInternal (String dialString, UUSInfo uusInfo, int videoState, Bundle intentExtras)
            throws CallStateException {

        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);

        // handle in-call MMI first if applicable
        if (handleInCallMmiCommands(newDialString)) {
            return null;
        }

        // Only look at the Network portion for mmi
        String networkPortion = PhoneNumberUtils.extractNetworkPortionAlt(newDialString);
        GsmMmiCode mmi =
                GsmMmiCode.newFromDialString(networkPortion, this, mUiccApplication.get());
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG,
                               "dialing w/ mmi '" + mmi + "'...");

        // SPRD: add for bug479433
        intentExtras.putShort(VIDEO_STATE, (short) videoState);
        if (mmi == null) {
            return mCT.dial(newDialString, uusInfo, intentExtras);
        } else if (mmi.isTemporaryModeCLIR()) {
            return mCT.dial(mmi.mDialingNumber, mmi.getCLIRMode(), uusInfo, intentExtras);
        } else {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();

            // FIXME should this return null or something else?
            return null;
        }
    }

    @Override
    public boolean handlePinMmi(String dialString) {
        GsmMmiCode mmi = GsmMmiCode.newFromDialString(dialString, this, mUiccApplication.get());

        if (mmi != null && mmi.isPinPukCommand()) {
            mPendingMMIs.add(mmi);
            mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
            mmi.processCode();
            return true;
        }

        return false;
    }

    @Override
    public void sendUssdResponse(String ussdMessge) {
        GsmMmiCode mmi = GsmMmiCode.newFromUssdUserInput(ussdMessge, this, mUiccApplication.get());
        mPendingMMIs.add(mmi);
        mMmiRegistrants.notifyRegistrants(new AsyncResult(null, mmi, null));
        mmi.sendUssd(ussdMessge);
    }

    @Override
    public void
    sendDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                    "sendDtmf called with invalid character '" + c + "'");
        } else {
            if (mCT.mState ==  PhoneConstants.State.OFFHOOK) {
                mCi.sendDtmf(c, null);
            }
        }
    }

    @Override
    public void
    startDtmf(char c) {
        if (!PhoneNumberUtils.is12Key(c)) {
            Rlog.e(LOG_TAG,
                "startDtmf called with invalid character '" + c + "'");
        } else {
            mCi.startDtmf(c, null);
        }
    }

    @Override
    public void
    stopDtmf() {
        mCi.stopDtmf(null);
    }

    public void
    sendBurstDtmf(String dtmfString) {
        Rlog.e(LOG_TAG, "[GSMPhone] sendBurstDtmf() is a CDMA method");
    }

    @Override
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
    }

    private void storeVoiceMailNumber(String number) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_NUMBER + getPhoneId(), number);
        editor.apply();
        setVmSimImsi(getSubscriberId());
    }

    @Override
    public String getVoiceMailNumber() {
        // Read from the SIM. If its null, try reading from the shared preference area.
        IccRecords r = mIccRecords.get();
        String number = (r != null) ? r.getVoiceMailNumber() : "";
        if (TextUtils.isEmpty(number)) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            number = sp.getString(VM_NUMBER + getPhoneId(), null);
        }

        /* SPRD: [bug476017] Load voicemail number from carrier config. @{ */
        if (TextUtils.isEmpty(number)) {
            Bundle globalConfig = GlobalConfigManager.getInstance().getGlobalConfigByPhoneId(getPhoneId());
            if (getServiceState().getRoaming()) {
                number = globalConfig.getString(GlobalConfigManager.GLO_CONF_ROAMING_VOICEMAIL_NUMBER);
            } else {
                number = globalConfig.getString(GlobalConfigManager.GLO_CONF_VOICEMAIL_NUMBER);
            }
            Log.d(LOG_TAG, "Get global config vm number[" + getPhoneId() + "]: " + number);
        }
        /* @} */

        if (TextUtils.isEmpty(number)) {
            String[] listArray = getContext().getResources()
                .getStringArray(com.android.internal.R.array.config_default_vm_number);
            if (listArray != null && listArray.length > 0) {
                for (int i=0; i<listArray.length; i++) {
                    if (!TextUtils.isEmpty(listArray[i])) {
                        String[] defaultVMNumberArray = listArray[i].split(";");
                        if (defaultVMNumberArray != null && defaultVMNumberArray.length > 0) {
                            if (defaultVMNumberArray.length == 1) {
                                number = defaultVMNumberArray[0];
                            } else if (defaultVMNumberArray.length == 2 &&
                                    !TextUtils.isEmpty(defaultVMNumberArray[1]) &&
                                    defaultVMNumberArray[1].equalsIgnoreCase(getGroupIdLevel1())) {
                                number = defaultVMNumberArray[0];
                                break;
                            }
                        }
                    }
                }
            }
        }
        return number;
    }

    private String getVmSimImsi() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(VM_SIM_IMSI + getPhoneId(), null);
    }

    private void setVmSimImsi(String imsi) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putString(VM_SIM_IMSI + getPhoneId(), imsi);
        editor.apply();
    }

    @Override
    public String getVoiceMailAlphaTag() {
        String ret;
        IccRecords r = mIccRecords.get();

        ret = (r != null) ? r.getVoiceMailAlphaTag() : "";

        if (ret == null || ret.length() == 0) {
            return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
        }

        return ret;
    }

    @Override
    public String getDeviceId() {
        return mImei;
    }

    @Override
    public String getDeviceSvn() {
        return mImeiSv;
    }

    @Override
    public IsimRecords getIsimRecords() {
        return mIsimUiccRecords;
    }

    @Override
    public String getImei() {
        return mImei;
    }

    @Override
    public String getEsn() {
        Rlog.e(LOG_TAG, "[GSMPhone] getEsn() is a CDMA method");
        return "0";
    }

    @Override
    public String getMeid() {
        Rlog.e(LOG_TAG, "[GSMPhone] getMeid() is a CDMA method");
        return "0";
    }

    @Override
    public String getNai() {
        IccRecords r = mUiccController.getIccRecords(mPhoneId, UiccController.APP_FAM_3GPP2);
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Rlog.v(LOG_TAG, "IccRecords is " + r);
        }
        return (r != null) ? r.getNAI() : null;
    }

    @Override
    public String getSubscriberId() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getIMSI() : null;
    }

    @Override
    public String getGroupIdLevel1() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid1() : null;
    }

    @Override
    public String getGroupIdLevel2() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getGid2() : null;
    }

    @Override
    public String getLine1Number() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getMsisdn() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnNumber() : null;
    }

    @Override
    public String getLine1AlphaTag() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.getMsisdnAlphaTag() : null;
    }

    @Override
    public boolean setLine1Number(String alphaTag, String number, Message onComplete) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setMsisdnNumber(alphaTag, number, onComplete);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void setVoiceMailNumber(String alphaTag,
                            String voiceMailNumber,
                            Message onComplete) {

        Message resp;
        mVmNumber = voiceMailNumber;
        resp = obtainMessage(EVENT_SET_VM_NUMBER_DONE, 0, 0, onComplete);
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMailNumber(alphaTag, mVmNumber, resp);
        }
    }

    private boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    @Override
    public String getSystemProperty(String property, String defValue) {
        if(getUnitTestMode()) {
            return null;
        }
        return TelephonyManager.getTelephonyProperty(mPhoneId, property, defValue);
    }

    private boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    public void updateDataConnectionTracker() {
        ((DcTracker)mDcTracker).update();
    }

    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, onComplete);
            return;
        }

        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }
            mCi.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }

    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,
            String dialingNumber,
            int timerSeconds,
            Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.setCallForwardingOption(commandInterfaceCFAction,
                    commandInterfaceCFReason, dialingNumber, timerSeconds, onComplete);
            return;
        }

        if (    (isValidCommandInterfaceCFAction(commandInterfaceCFAction)) &&
                (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                Cfu cfu = new Cfu(dialingNumber, onComplete);
                resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                        isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, cfu);
            } else {
                resp = onComplete;
            }
            mCi.setCallForward(commandInterfaceCFAction,
                    commandInterfaceCFReason,
                    CommandsInterface.SERVICE_CLASS_VOICE,
                    dialingNumber,
                    timerSeconds,
                    resp);
        }
    }

    @Override
    public void getOutgoingCallerIdDisplay(Message onComplete) {
        mCi.getCLIR(onComplete);
    }

    @Override
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode,
                                           Message onComplete) {
        mCi.setCLIR(commandInterfaceCLIRMode,
                obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }

    @Override
    public void getCallWaiting(Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.getCallWaiting(onComplete);
            return;
        }

        //As per 3GPP TS 24.083, section 1.6 UE doesn't need to send service
        //class parameter in call waiting interrogation  to network
        mCi.queryCallWaiting(CommandsInterface.SERVICE_CLASS_NONE, onComplete);
    }

    @Override
    public void setCallWaiting(boolean enable, Message onComplete) {
        ImsPhone imsPhone = mImsPhone;
        if ((imsPhone != null)
                && (imsPhone.getServiceState().getState() == ServiceState.STATE_IN_SERVICE)) {
            imsPhone.setCallWaiting(enable, onComplete);
            return;
        }

        mCi.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }

    @Override
    public void
    getAvailableNetworks(Message response) {
        mCi.getAvailableNetworks(response);
    }

    @Override
    public void
    getNeighboringCids(Message response) {
        mCi.getNeighboringCids(response);
    }

    @Override
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        mPostDialHandler = new Registrant(h, what, obj);
    }

    @Override
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {
       if (mImsPhone != null) {
           mImsPhone.setUiTTYMode(uiTtyMode, onComplete);
       }
    }

    @Override
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }

    @Override
    public boolean getMute() {
        return mCT.getMute();
    }

    @Override
    public void getDataCallList(Message response) {
        mCi.getDataCallList(response);
    }

    @Override
    public void updateServiceLocation() {
        mSST.enableSingleLocationUpdate();
    }

    @Override
    public void enableLocationUpdates() {
        mSST.enableLocationUpdates();
    }

    @Override
    public void disableLocationUpdates() {
        mSST.disableLocationUpdates();
    }

    @Override
    public boolean getDataRoamingEnabled() {
        return mDcTracker.getDataOnRoamingEnabled();
    }

    @Override
    public void setDataRoamingEnabled(boolean enable) {
        mDcTracker.setDataOnRoamingEnabled(enable);
    }

    @Override
    public boolean getDataEnabled() {
        return mDcTracker.getDataEnabled();
    }

    @Override
    public void setDataEnabled(boolean enable) {
        mDcTracker.setDataEnabled(enable);
    }

    /**
     * Removes the given MMI from the pending list and notifies
     * registrants that it is complete.
     * @param mmi MMI that is done
     */
    /*package*/ void
    onMMIDone(GsmMmiCode mmi) {
        /* Only notify complete if it's on the pending list.
         * Otherwise, it's already been handled (eg, previously canceled).
         * The exception is cancellation of an incoming USSD-REQUEST, which is
         * not on the list.
         */
        if (mPendingMMIs.remove(mmi) || mmi.isUssdRequest() || mmi.isSsInfo()) {
            mMmiCompleteRegistrants.notifyRegistrants(
                new AsyncResult(null, mmi, null));
        }
    }


    private void
    onNetworkInitiatedUssd(GsmMmiCode mmi) {
        mMmiCompleteRegistrants.notifyRegistrants(
            new AsyncResult(null, mmi, null));
    }


    /** ussdMode is one of CommandsInterface.USSD_MODE_* */
    private void
    onIncomingUSSD (int ussdMode, String ussdMessage) {
        boolean isUssdError;
        boolean isUssdRequest;
        boolean isUssdRelease;

        isUssdRequest
            = (ussdMode == CommandsInterface.USSD_MODE_REQUEST);

        isUssdError
            = (ussdMode != CommandsInterface.USSD_MODE_NOTIFY
                && ussdMode != CommandsInterface.USSD_MODE_REQUEST
                // SPRD: add for bug494828
                && ussdMode != CommandsInterface.USSD_MODE_NW_RELEASE);

        isUssdRelease = (ussdMode == CommandsInterface.USSD_MODE_NW_RELEASE);

        // See comments in GsmMmiCode.java
        // USSD requests aren't finished until one
        // of these two events happen
        GsmMmiCode found = null;
        for (int i = 0, s = mPendingMMIs.size() ; i < s; i++) {
            if(mPendingMMIs.get(i).isPendingUSSD()) {
                found = mPendingMMIs.get(i);
                break;
            }
        }

        if (LOCAL_DEBUG) {
            Rlog.d(LOG_TAG, "ussdMode = " + ussdMode + ", found = " + found + ", ussdMessage = "
                    + ussdMessage);
        }

        if (found != null) {
            // Complete pending USSD
            if (LOCAL_DEBUG) {
                Rlog.d(LOG_TAG, "USSD state = " + found.getState());
            }

            if (isUssdRelease) {
                found.onUssdRelease();
            } else if (isUssdError) {
                found.onUssdFinishedError();
            } else {
                found.onUssdFinished(ussdMessage, isUssdRequest);
            }
        } else { // pending USSD not found
            // The network may initiate its own USSD request

            // ignore everything that isnt a Notify or a Request
            // also, discard if there is no message to present
            if (!isUssdError && ussdMessage != null) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssd(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
            } /* SPRD: Porting USSD. @{ */else if (isUssdError) {
                GsmMmiCode mmi;
                mmi = GsmMmiCode.newNetworkInitiatedUssdError(ussdMessage,
                                                   isUssdRequest,
                                                   GSMPhone.this,
                                                   mUiccApplication.get());
                onNetworkInitiatedUssd(mmi);
                /* @} */
            }
        }
    }

    /**
     * Make sure the network knows our preferred setting.
     */
    protected  void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        int clirSetting = sp.getInt(CLIR_KEY + getPhoneId(), -1);
        if (clirSetting >= 0) {
            mCi.setCLIR(clirSetting, null);
        }
    }

    @Override
    public void handleMessage (Message msg) {
        AsyncResult ar;
        Message onComplete;

        // messages to be handled whether or not the phone is being destroyed
        // should only include messages which are being re-directed and do not use
        // resources of the phone being destroyed
        switch (msg.what) {
            // handle the select network completion callbacks.
            case EVENT_SET_NETWORK_MANUAL_COMPLETE:
            case EVENT_SET_NETWORK_AUTOMATIC_COMPLETE:
                super.handleMessage(msg);
                return;
        }

        if (!mIsTheCurrentActivePhone) {
            Rlog.e(LOG_TAG, "Received message " + msg +
                    "[" + msg.what + "] while being destroyed. Ignoring.");
            return;
        }
        switch (msg.what) {
            case EVENT_RADIO_AVAILABLE: {
                mCi.getBasebandVersion(
                        obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                mCi.getIMEI(obtainMessage(EVENT_GET_IMEI_DONE));
                mCi.getIMEISV(obtainMessage(EVENT_GET_IMEISV_DONE));
                mCi.getRadioCapability(obtainMessage(EVENT_GET_RADIO_CAPABILITY));
                startLceAfterRadioIsAvailable();
            }
            break;

            case EVENT_RADIO_ON:
                // If this is on APM off, SIM may already be loaded. Send setPreferredNetworkType
                // request to RIL to preserve user setting across APM toggling
                setPreferredNetworkTypeIfSimLoaded();
                break;

            case EVENT_REGISTERED_TO_NETWORK:
                syncClirSetting();
                break;

            case EVENT_SIM_RECORDS_LOADED:
                updateCurrentCarrierInProvider();

                // Check if this is a different SIM than the previous one. If so unset the
                // voice mail number.
                String imsi = getVmSimImsi();
                String imsiFromSIM = getSubscriberId();
                if (imsi != null && imsiFromSIM != null && !imsiFromSIM.equals(imsi)) {
                    storeVoiceMailNumber(null);
                    setVmSimImsi(null);
                }

                mSimRecordsLoadedRegistrants.notifyRegistrants();
                updateVoiceMail();
            break;

            case EVENT_GET_BASEBAND_VERSION_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "Baseband version: " + ar.result);
                TelephonyManager.from(mContext).setBasebandVersionForPhone(getPhoneId(),
                        (String)ar.result);
            break;

            case EVENT_GET_IMEI_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mImei = (String)ar.result;
            break;

            case EVENT_GET_IMEISV_DONE:
                ar = (AsyncResult)msg.obj;

                if (ar.exception != null) {
                    break;
                }

                mImeiSv = (String)ar.result;
            break;

            case EVENT_USSD:
                /* SPRD: Porting USSD. @{
                * @orig
                ar = (AsyncResult)msg.obj;

                String[] ussdResult = (String[]) ar.result;

                if (ussdResult.length > 1) {
                    try {
                        onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                    } catch (NumberFormatException e) {
                        Rlog.w(LOG_TAG, "error parsing USSD");
                    }
                } */
                ar = (AsyncResult) msg.obj;
                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "EVENT_USSD  ar.exception != null");
                    GsmMmiCode found = null;
                    for (int i = 0, s = mPendingMMIs.size(); i < s; i++) {
                        if (mPendingMMIs.get(i).isPendingUSSD()) {
                            found = mPendingMMIs.get(i);
                            break;
                        }
                    }
                    if (found != null) {
                        Rlog.d(LOG_TAG, "EVENT_USSD found != null");
                        found.onUssdFinishedError();
                    }
                } else {
                    String[] ussdResult = (String[]) ar.result;

                    if (ussdResult.length > 1) {
                        try {
                            onIncomingUSSD(Integer.parseInt(ussdResult[0]), ussdResult[1]);
                        } catch (NumberFormatException e) {
                            Rlog.w(LOG_TAG, "error parsing USSD");
                        }
                    }
                }
                /* @} */
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE: {
                // Some MMI requests (eg USSD) are not completed
                // within the course of a CommandsInterface request
                // If the radio shuts off or resets while one of these
                // is pending, we need to clean up.

                for (int i = mPendingMMIs.size() - 1; i >= 0; i--) {
                    if (mPendingMMIs.get(i).isPendingUSSD()) {
                        mPendingMMIs.get(i).onUssdFinishedError();
                    }
                }
                ImsPhone imsPhone = mImsPhone;
                if (imsPhone != null) {
                    imsPhone.getServiceState().setStateOff();
                }
                mRadioOffOrNotAvailableRegistrants.notifyRegistrants();
                break;
            }

            case EVENT_SSN:
                ar = (AsyncResult)msg.obj;
                SuppServiceNotification not = (SuppServiceNotification) ar.result;
                mSsnRegistrants.notifyRegistrants(ar);
            break;

            case EVENT_SET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                IccRecords r = mIccRecords.get();
                Cfu cfu = (Cfu) ar.userObj;
                if (ar.exception == null && r != null) {
                    r.setVoiceCallForwardingFlag(1, msg.arg1 == 1, cfu.mSetCfNumber);
                }
                if (cfu.mOnComplete != null) {
                    AsyncResult.forMessage(cfu.mOnComplete, ar.result, ar.exception);
                    cfu.mOnComplete.sendToTarget();
                }
                break;

            case EVENT_SET_VM_NUMBER_DONE:
                ar = (AsyncResult)msg.obj;
                if (IccVmNotSupportedException.class.isInstance(ar.exception)) {
                    storeVoiceMailNumber(mVmNumber);
                    ar.exception = null;
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;


            case EVENT_GET_CALL_FORWARD_DONE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    handleCfuQueryResult((CallForwardInfo[])ar.result);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SET_NETWORK_AUTOMATIC:
                // Automatic network selection from EF_CSP SIM record
                ar = (AsyncResult) msg.obj;
                if (mSST.mSS.getIsManualSelection()) {
                    setNetworkSelectionModeAutomatic((Message) ar.result);
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: set to automatic");
                } else {
                    // prevent duplicate request which will push current PLMN to low priority
                    Rlog.d(LOG_TAG, "SET_NETWORK_SELECTION_AUTOMATIC: already automatic, ignore");
                }
                break;

            case EVENT_ICC_RECORD_EVENTS:
                ar = (AsyncResult)msg.obj;
                processIccRecordEvents((Integer)ar.result);
                break;

            case EVENT_SET_CLIR_COMPLETE:
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    saveClirSetting(msg.arg1);
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_SS:
                ar = (AsyncResult)msg.obj;
                Rlog.d(LOG_TAG, "Event EVENT_SS received");
                // SS data is already being handled through MMI codes.
                // So, this result if processed as MMI response would help
                // in re-using the existing functionality.
                GsmMmiCode mmi = new GsmMmiCode(this, mUiccApplication.get());
                mmi.processSsData(ar);
                break;

               /* SPRD: add for bug479433 @{ */
               case EVENT_FALLBACK:
                   notifyVideoCallFallBack((AsyncResult) msg.obj);
                   break;
               case EVENT_VIDEOCALLFAIL:
                   notifyVideoCallFail((AsyncResult) msg.obj);
                   break;
               case EVENT_VIDEOCALLCODEC:
                   notifyVideoCallCodec((AsyncResult) msg.obj);
                   break;
               /* @} */

            /* SPRD: function call barring support. @{ */
            case EVENT_SET_CALL_BARRING_DONE:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    // nothing to do so far
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_GET_CALL_BARRING_DONE:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    // nothing to do so far
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;

            case EVENT_CHANGE_CALL_BARRING_PASSWORD_DONE:
                ar = (AsyncResult) msg.obj;
                if (ar.exception == null) {
                    // nothing to do so far
                }
                onComplete = (Message) ar.userObj;
                if (onComplete != null) {
                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
                    onComplete.sendToTarget();
                }
                break;
                /* }@ */

             default:
                 super.handleMessage(msg);
        }
    }

    protected UiccCardApplication getUiccCardApplication() {
            return  mUiccController.getUiccCardApplication(mPhoneId,
                    UiccController.APP_FAM_3GPP);
    }

    @Override
    protected void onUpdateIccAvailability() {
        if (mUiccController == null ) {
            return;
        }

        UiccCardApplication newUiccApplication =
                mUiccController.getUiccCardApplication(mPhoneId, UiccController.APP_FAM_IMS);
        IsimUiccRecords newIsimUiccRecords = null;

        if (newUiccApplication != null) {
            newIsimUiccRecords = (IsimUiccRecords)newUiccApplication.getIccRecords();
            if (LOCAL_DEBUG) log("New ISIM application found");
        }
        mIsimUiccRecords = newIsimUiccRecords;

        newUiccApplication = getUiccCardApplication();

        UiccCardApplication app = mUiccApplication.get();
        if (app != newUiccApplication) {
            if (app != null) {
                if (LOCAL_DEBUG) log("Removing stale icc objects.");
                if (mIccRecords.get() != null) {
                    unregisterForSimRecordEvents();
                    mSimPhoneBookIntManager.updateIccRecords(null);
                }
                mIccRecords.set(null);
                mUiccApplication.set(null);
            }
            if (newUiccApplication != null) {
                if (LOCAL_DEBUG) log("New Uicc application found");
                mUiccApplication.set(newUiccApplication);
                mIccRecords.set(newUiccApplication.getIccRecords());
                registerForSimRecordEvents();
                mSimPhoneBookIntManager.updateIccRecords(mIccRecords.get());
            }
        }
    }

    private void processIccRecordEvents(int eventCode) {
        switch (eventCode) {
            case IccRecords.EVENT_CFI:
                notifyCallForwardingIndicator();
                break;
        }
    }

    /**
     * Sets the "current" field in the telephony provider according to the SIM's operator
     *
     * @return true for success; false otherwise.
     */
    public boolean updateCurrentCarrierInProvider() {
        long currentDds = SubscriptionManager.getDefaultDataSubId();
        String operatorNumeric = getOperatorNumeric();

        log("updateCurrentCarrierInProvider: mSubId = " + getSubId()
                + " currentDds = " + currentDds + " operatorNumeric = " + operatorNumeric);

        if (!TextUtils.isEmpty(operatorNumeric) && (getSubId() == currentDds)) {
            try {
                Uri uri = Uri.withAppendedPath(Telephony.Carriers.CONTENT_URI, "current");
                ContentValues map = new ContentValues();
                map.put(Telephony.Carriers.NUMERIC, operatorNumeric);
                mContext.getContentResolver().insert(uri, map);
                return true;
            } catch (SQLException e) {
                Rlog.e(LOG_TAG, "Can't store current operator", e);
            }
        }
        return false;
    }

    /**
     * Saves CLIR setting so that we can re-apply it as necessary
     * (in case the RIL resets it across reboots).
     */
    public void saveClirSetting(int commandInterfaceCLIRMode) {
        // open the shared preferences editor, and write the value.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(CLIR_KEY + getPhoneId(), commandInterfaceCLIRMode);

        // commit and log the result.
        if (! editor.commit()) {
            Rlog.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }

    private void handleCfuQueryResult(CallForwardInfo[] infos) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            if (infos == null || infos.length == 0) {
                // Assume the default is not active
                // Set unconditional CFF in SIM to false
                r.setVoiceCallForwardingFlag(1, false, null);
            } else {
                for (int i = 0, s = infos.length; i < s; i++) {
                    if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                        r.setVoiceCallForwardingFlag(1, (infos[i].status == 1),
                            infos[i].number);
                        // should only have the one
                        break;
                    }
                }
            }
        }
    }

    /**
     * Retrieves the PhoneSubInfo of the GSMPhone
     */
    @Override
    public PhoneSubInfo getPhoneSubInfo(){
        return mSubInfo;
    }

    /**
     * Retrieves the IccPhoneBookInterfaceManager of the GSMPhone
     */
    @Override
    public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
        return mSimPhoneBookIntManager;
    }

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate 0 = activate, 1 = deactivate
     * @param response Callback message is empty on completion
     */
    @Override
    public void activateCellBroadcastSms(int activate, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] activateCellBroadcastSms() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void getCellBroadcastSmsConfig(Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] getCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response Callback message is empty on completion
     */
    @Override
    public void setCellBroadcastSmsConfig(int[] configValuesArray, Message response) {
        Rlog.e(LOG_TAG, "[GSMPhone] setCellBroadcastSmsConfig() is obsolete; use SmsManager");
        response.sendToTarget();
    }

    @Override
    public boolean isCspPlmnEnabled() {
        IccRecords r = mIccRecords.get();
        return (r != null) ? r.isCspPlmnEnabled() : false;
    }

    boolean isManualNetSelAllowed() {

        int nwMode = Phone.PREFERRED_NT_MODE;
        int subId = getSubId();

        nwMode = android.provider.Settings.Global.getInt(mContext.getContentResolver(),
                    android.provider.Settings.Global.PREFERRED_NETWORK_MODE + subId, nwMode);

        Rlog.d(LOG_TAG, "isManualNetSelAllowed in mode = " + nwMode);
        /*
         *  For multimode targets in global mode manual network
         *  selection is disallowed
         */
        if (isManualSelProhibitedInGlobalMode()
                && ((nwMode == Phone.NT_MODE_LTE_CDMA_EVDO_GSM_WCDMA)
                        || (nwMode == Phone.NT_MODE_GLOBAL)) ){
            Rlog.d(LOG_TAG, "Manual selection not supported in mode = " + nwMode);
            return false;
        } else {
            Rlog.d(LOG_TAG, "Manual selection is supported in mode = " + nwMode);
        }

        /*
         *  Single mode phone with - GSM network modes/global mode
         *  LTE only for 3GPP
         *  LTE centric + 3GPP Legacy
         *  Note: the actual enabling/disabling manual selection for these
         *  cases will be controlled by csp
         */
        return true;
    }

    private boolean isManualSelProhibitedInGlobalMode() {
        boolean isProhibited = false;
        final String configString = getContext().getResources().getString(com.android.internal.
                                            R.string.prohibit_manual_network_selection_in_gobal_mode);

        if (!TextUtils.isEmpty(configString)) {
            String[] configArray = configString.split(";");

            if (configArray != null &&
                    ((configArray.length == 1 && configArray[0].equalsIgnoreCase("true")) ||
                        (configArray.length == 2 && !TextUtils.isEmpty(configArray[1]) &&
                            configArray[0].equalsIgnoreCase("true") &&
                            configArray[1].equalsIgnoreCase(getGroupIdLevel1())))) {
                            isProhibited = true;
            }
        }
        Rlog.d(LOG_TAG, "isManualNetSelAllowedInGlobal in current carrier is " + isProhibited);
        return isProhibited;
    }

    private void registerForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.registerForNetworkSelectionModeAutomatic(
                this, EVENT_SET_NETWORK_AUTOMATIC, null);
        r.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        r.registerForRecordsLoaded(this, EVENT_SIM_RECORDS_LOADED, null);
    }

    private void unregisterForSimRecordEvents() {
        IccRecords r = mIccRecords.get();
        if (r == null) {
            return;
        }
        r.unregisterForNetworkSelectionModeAutomatic(this);
        r.unregisterForRecordsEvents(this);
        r.unregisterForRecordsLoaded(this);
    }

    @Override
    public void exitEmergencyCallbackMode() {
        if (mImsPhone != null) {
            mImsPhone.exitEmergencyCallbackMode();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("GSMPhone extends:");
        super.dump(fd, pw, args);
        pw.println(" mCT=" + mCT);
        pw.println(" mSST=" + mSST);
        pw.println(" mPendingMMIs=" + mPendingMMIs);
        pw.println(" mSimPhoneBookIntManager=" + mSimPhoneBookIntManager);
        pw.println(" mSubInfo=" + mSubInfo);
        if (VDBG) pw.println(" mImei=" + mImei);
        if (VDBG) pw.println(" mImeiSv=" + mImeiSv);
        pw.println(" mVmNumber=" + mVmNumber);
    }

    @Override
    public boolean setOperatorBrandOverride(String brand) {
        if (mUiccController == null) {
            return false;
        }

        UiccCard card = mUiccController.getUiccCard(getPhoneId());
        if (card == null) {
            return false;
        }

        boolean status = card.setOperatorBrandOverride(brand);

        // Refresh.
        if (status) {
            IccRecords iccRecords = mIccRecords.get();
            if (iccRecords != null) {
                TelephonyManager.from(mContext).setSimOperatorNameForPhone(
                        getPhoneId(), iccRecords.getServiceProviderName());
            }
            if (mSST != null) {
                mSST.pollState();
            }
        }
        return status;
    }

    /**
     * @return operator numeric.
     */
    public String getOperatorNumeric() {
        String operatorNumeric = null;
        IccRecords r = mIccRecords.get();
        if (r != null) {
            operatorNumeric = r.getOperatorNumeric();
        }
        return operatorNumeric;
    }

    public void registerForAllDataDisconnected(Handler h, int what, Object obj) {
        ((DcTracker)mDcTracker)
                .registerForAllDataDisconnected(h, what, obj);
    }

    public void unregisterForAllDataDisconnected(Handler h) {
        ((DcTracker)mDcTracker).unregisterForAllDataDisconnected(h);
    }

    public void setInternalDataEnabled(boolean enable, Message onCompleteMsg) {
        ((DcTracker)mDcTracker)
                .setInternalDataEnabled(enable, onCompleteMsg);
    }


    public boolean setInternalDataEnabledFlag(boolean enable) {
        return ((DcTracker)mDcTracker)
                .setInternalDataEnabledFlag(enable);
    }

    public void notifyEcbmTimerReset(Boolean flag) {
        mEcmTimerResetRegistrants.notifyResult(flag);
    }

    /**
     * Registration point for Ecm timer reset
     *
     * @param h handler to notify
     * @param what User-defined message code
     * @param obj placed in Message.obj
     */
    @Override
    public void registerForEcmTimerReset(Handler h, int what, Object obj) {
        mEcmTimerResetRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForEcmTimerReset(Handler h) {
        mEcmTimerResetRegistrants.remove(h);
    }

    /**
     * Sets the SIM voice message waiting indicator records.
     * @param line GSM Subscriber Profile Number, one-based. Only '1' is supported
     * @param countWaiting The number of messages waiting, if known. Use
     *                     -1 to indicate that an unknown number of
     *                      messages are waiting
     */
    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        IccRecords r = mIccRecords.get();
        if (r != null) {
            r.setVoiceMessageWaiting(line, countWaiting);
        } else {
            log("SIM Records not found, MWI not updated");
        }
    }

    protected void log(String s) {
        Rlog.d(LOG_TAG, "[GSMPhone] " + s);
    }

    // -------------------------------------- SPRD --------------------------------

    /**
     * SPRD: Add interfaces for pin/pin2/puk/puk2 remain times. @{
     */
    public int getRemainTimes(int type) {
        Rlog.d(LOG_TAG, "getRemainTimes type:" + type);

        synchronized (mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_REMIAN_TIMES_DONE);
            int[] ints = new int[1];
            ints[0] = type;
            byte[] data= IccUtils.getIntRequestRawBytes(ints,
                    RILConstants.OEM_REQ_FUNCID_GET_REMAIN_TIMES,
                    RILConstants.OEM_REQ_NO_SUBFUNCID);
            mCi.invokeOemRilRequestRaw(data, response);

            Rlog.d(LOG_TAG, "enter mLock.wait");
            try {
                mLock.wait();
                Rlog.d(LOG_TAG, "leave mLock.wait");
            } catch (InterruptedException e) {
                Rlog.d(LOG_TAG, "interrupted while trying to get remain times");
            }
        }

        Rlog.d(LOG_TAG, "getRemainTimes:" + mRemainTimes);
        return mRemainTimes;
    }

    /**
     * SPRD: Add for new features.
     */
    class SyncHandler extends Handler {
        SyncHandler (Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;
            Rlog.d(LOG_TAG, " handleMessage msg.what:" + msg.what);
            switch (msg.what) {
            /* SPRD: Add interfaces for pin/pin2/puk/puk2 remain times. @{ */
                case EVENT_GET_REMIAN_TIMES_DONE:
                    ar = (AsyncResult) msg.obj;
                    Rlog.d(LOG_TAG, "handleMessage EVENT_GET_REMIAN_TIMES_DONE");
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            byte[] ret = (byte[]) ar.result;
                            try {
                                if (ret != null) {
                                    mRemainTimes = IccUtils.byteArrayToInt(ret,0);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            Rlog.d(LOG_TAG, "handleMessage registration state error!");
                            mRemainTimes = -1;
                        }
                        mLock.notifyAll();
                    }
                    break;
            /* @} */
                case EVENT_GET_SIMLOCK_REMIAN_TIMES_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            byte[] ret = ((byte[])ar.result);
                            try {
                                if (ret != null) {
                                    int ret1 = IccUtils.byteArrayToInt(ret,0);
                                    int ret2 = IccUtils.byteArrayToInt(ret,4);
                                    mSimlockRemainTimes = ret1 - ret2;
                                    Rlog.d(LOG_TAG, "[simlock]ret1 = " + ret1 + " ret2 = " + ret2);
                                } else {
                                    Rlog.e(LOG_TAG, "[simlock] get remain times failed ");
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            mSimlockRemainTimes = -1;
                        }
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_GET_SIMLOCK_STATUS_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        isSimLocked = false;
                        if (ar.exception == null) {
                            byte[] oemResp = ((byte[])ar.result);
                            try {
                                if (oemResp != null) {
                                    int status = IccUtils.byteArrayToInt(oemResp,0);
                                    isSimLocked = (status == 1 ? true : false);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        mLock.notifyAll();
                    }
                    break;
            }
        }
    }

    /* SPRD: add for bug479433 @{ */
    protected final RegistrantList mPreciseVideoCallStateRegistrants = new RegistrantList();

    protected final RegistrantList mNewRingingVideoCallRegistrants = new RegistrantList();

    protected final RegistrantList mVideoCallDisconnectRegistrants = new RegistrantList();

    protected final RegistrantList mVideoCallFallBackRegistrants = new RegistrantList();

    protected final RegistrantList mVideoCallFailRegistrants = new RegistrantList();

    protected final RegistrantList mVideoCallCodecRegistrants = new RegistrantList();

    public void registerForVideoCallCodec(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        mVideoCallCodecRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallCodec(Handler h) {
        mVideoCallCodecRegistrants.remove(h);
    }

    public void registerForVideoCallFail(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mVideoCallFailRegistrants.addUnique(h, what, obj);
    }

    public void unregisterForVideoCallFail(Handler h) {
        mVideoCallFailRegistrants.remove(h);
    }

    @Override
    public void registerForVideoCallFallBack(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mVideoCallFallBackRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForVideoCallFallBack(Handler h) {
        mVideoCallFallBackRegistrants.remove(h);
    }

    @Override
    public void codecVP(int type, Bundle param) {
        ((CommandsInterface) mCi).codecVP(type, param, null);
    }

    @Override
    public void controlCamera(boolean bEnable) throws CallStateException {
        // TODO Auto-generated method stub
        RilVideoEx.controlVPCamera(mCi, bEnable, null);
    }

    @Override
    public void controlAudio(boolean bEnable) throws CallStateException {
        // TODO Auto-generated method stub
        RilVideoEx.controlVPAudio(mCi, bEnable, null);
        // ((CommandsInterfaceSprd)mCi).controlVPAudio(bEnable, null);
    }

    @Override
    public void registerForPreciseVideoCallStateChanged(Handler h, int what,
            Object obj) {
        // TODO Auto-generated method stub
        checkCorrectThread(h);

        mPreciseVideoCallStateRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForPreciseVideoCallStateChanged(Handler h) {
        // TODO Auto-generated method stub
        mPreciseVideoCallStateRegistrants.remove(h);
    }

    @Override
    public void registerForNewRingingVideoCall(Handler h, int what, Object obj) {
        // TODO Auto-generated method stub
        checkCorrectThread(h);

        mNewRingingVideoCallRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForNewRingingVideoCall(Handler h) {
        // TODO Auto-generated method stub
        mNewRingingVideoCallRegistrants.remove(h);
    }

    @Override
    public void registerForVideoCallDisconnect(Handler h, int what, Object obj) {
        // TODO Auto-generated method stub
        checkCorrectThread(h);

        mVideoCallDisconnectRegistrants.addUnique(h, what, obj);
    }

    @Override
    public void unregisterForVideoCallDisconnect(Handler h) {
        // TODO Auto-generated method stub
        mVideoCallDisconnectRegistrants.remove(h);
    }

    @Override
    public void fallBack() throws CallStateException {
        // TODO Auto-generated method stub
        mCT.fallBack();
    }

    public void notifyNewRingingVideoCall(Connection cn) {
        Rlog.d(LOG_TAG, " notifyNewRingingVideoCall");
        AsyncResult ar = new AsyncResult(null, cn, null);
        mNewRingingVideoCallRegistrants.notifyRegistrants(ar);
    }

    public void notifyVideoCallDisconnect(Connection cn) {
        Rlog.d(LOG_TAG, " notifyVideoCallDisconnect");
        AsyncResult ar = new AsyncResult(null, cn, null);
        mVideoCallDisconnectRegistrants.notifyRegistrants(ar);
    }

    void notifyVideoCallFallBack(AsyncResult ar) {
        Rlog.d(LOG_TAG, " notifyVideoCallFallBack");
        mVideoCallFallBackRegistrants.notifyRegistrants(ar);
    }

    void notifyVideoCallFail(AsyncResult ar) {
        Rlog.d(LOG_TAG, " notifyVideoCallFail");
        mVideoCallFailRegistrants.notifyRegistrants(ar);
    }

    void notifyVideoCallCodec(AsyncResult ar) {
        Rlog.d(LOG_TAG, " notifyVideoCallCodec");
        mVideoCallCodecRegistrants.notifyRegistrants(ar);
    }

    public void requestVolteCallMediaChange(boolean isVideo, Message response) {
        RilVideoEx.requestVolteCallMediaChange(mCi, isVideo, response);
    }
    /* @} */

    /* SPRD: function call barring support. @{ */
    @Override
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message onComplete) {
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "changeBarringPassword: " + facility);
        mCi.changeBarringPassword(facility, oldPwd, newPwd, obtainMessage(EVENT_CHANGE_CALL_BARRING_PASSWORD_DONE, onComplete));
    }

    @Override
    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message onComplete) {
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "setFacilityLock: " + facility);
        mCi.setFacilityLock(facility, lockState, password, serviceClass, obtainMessage(EVENT_SET_CALL_BARRING_DONE, onComplete));
    }

    @Override
    public void queryFacilityLock(String facility, String password, int serviceClass, Message onComplete) {
        if (LOCAL_DEBUG) Rlog.d(LOG_TAG, "queryFacilityLock: " + facility);
        mCi.queryFacilityLock(facility, password, serviceClass, obtainMessage(EVENT_GET_CALL_BARRING_DONE, onComplete));
    }
    /* }@ */

    /**
     * SPRD:add for sim lock
     */
    @Override
    public int getSimLockRemainTimes(int type) {
        synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_SIMLOCK_REMIAN_TIMES_DONE);
            int[] ints = new int[2];
            ints[0] = type;
            ints[1] = 1;

            byte[] data = IccUtils.getIntRequestRawBytes(ints,
                    RILConstants.OEM_REQ_FUNCID_SIMLOCK,
                    RILConstants.OEM_REQ_SUBFUNCID_GET_SIMLOCK_REMAIN_TIMES);
            mCi.invokeOemRilRequestRaw(data,response);

            Rlog.d(LOG_TAG, "getSimLockRemainTimes type wait");
            try {
                mLock.wait();
                Rlog.d(LOG_TAG, "sim leave mLock.wait");
            } catch (InterruptedException e) {
                Rlog.d(LOG_TAG,"interrupted while trying to get sim remain times");
            }
        }
        Rlog.d(LOG_TAG, "getSimLockRemainTimes:" + mSimlockRemainTimes);
        return mSimlockRemainTimes;
    }

    @Override
    public boolean getSimLockStatus(int type) {
        synchronized(mLock) {
            Message response = mHandler.obtainMessage(EVENT_GET_SIMLOCK_STATUS_DONE);

            int[] ints = new int[2];
            ints[0] = type;
            ints[1] = 1;

            byte[] data = IccUtils.getIntRequestRawBytes(ints,
                    RILConstants.OEM_REQ_FUNCID_SIMLOCK,
                    RILConstants.OEM_REQ_SUBFUNCID_GET_SIMLOCK_STATUS);
            mCi.invokeOemRilRequestRaw(data,response);

            Rlog.d(LOG_TAG, "getSimLockStatus type wait");
            try {
                mLock.wait();
                Rlog.d(LOG_TAG, "sim leave mLock.wait");
            } catch (InterruptedException e) {
                Rlog.d(LOG_TAG,"interrupted while trying to get sim lock status");
            }
        }
        Rlog.d(LOG_TAG, "getSimLockStatus:" + isSimLocked);
        return isSimLocked;
    }

    @Override
    public void setSimLockEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PS, enable, pw, sc, onComplete);
    }

    @Override
    public void setNetworkLockEnabled(boolean enable, String pw, Message onComplete) {
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PN, enable, pw, sc, onComplete);
    }

    @Override
    public void setNetworkSubsetLockEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PU, enable, pw, sc, onComplete);
    }

    @Override
    public void setServiceProviderLockEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PP, enable, pw, sc, onComplete);
    }

    @Override
    public void setCorporateLockEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PC, enable, pw, sc, onComplete);
    }

    @Override
    public void setSimLockPukEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PS_PUK, enable, pw, sc,onComplete);
    }

    @Override
    public void setNetworkLockPukEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PN_PUK, enable, pw, sc,onComplete);
    }

    @Override
    public void setNetworkSubsetLockPukEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PU_PUK, enable, pw, sc,onComplete);
    }

    @Override
    public void setServiceProviderLockPukEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PP_PUK, enable, pw, sc,onComplete);
    }

    @Override
    public void setCorporateLockPukEnabled(boolean enable, String pw, Message onComplete){
        int sc = 0;
        mCi.setFacilityLock(CommandsInterface.CB_FACILITY_BA_PC_PUK, enable, pw, sc,onComplete);
    }
    /** @}*/
    /* SPRD: add for VoLTE. @{ */
    @Override
    public void setCallForwardingOption(int commandInterfaceCFAction,
            int commandInterfaceCFReason,int serviceClass, String dialingNumber,
            int timerSeconds, String ruleSet, Message onComplete){
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null) {
            imsPhone.setCallForwardingOption(commandInterfaceCFAction, commandInterfaceCFReason,
                    serviceClass, dialingNumber, timerSeconds, ruleSet, onComplete);
            return;
        } else {
            if (isValidCommandInterfaceCFAction(commandInterfaceCFAction)
                    && isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
                Message resp;
                if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                    Cfu cfu = new Cfu(dialingNumber, onComplete);
                    resp = obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                            isCfEnable(commandInterfaceCFAction) ? 1 : 0,
                            serviceClass, cfu);
                } else {
                    resp = onComplete;
                }
                mCi.setCallForward(commandInterfaceCFAction,
                        commandInterfaceCFReason, serviceClass, dialingNumber,
                        timerSeconds, resp);
            }
        }
        Rlog.w(LOG_TAG, "setCallForwardingOption->imsPhone is null!.");
    }

    @Override
    public void getCallForwardingOption(int commandInterfaceCFReason, int serviceClass,
            String ruleSet, Message onComplete){
        ImsPhone imsPhone = mImsPhone;
        if (imsPhone != null) {
            imsPhone.getCallForwardingOption(commandInterfaceCFReason, serviceClass,
                    ruleSet, onComplete);
            return;
        } else {
            if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
                if (LOCAL_DEBUG) {
                    Rlog.d(LOG_TAG, "requesting call forwarding query.");
                }
                Message resp;
                if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                    resp = obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
                } else {
                    resp = onComplete;
                }
                mCi.queryCallForwardStatus(commandInterfaceCFReason, serviceClass,
                        null, resp);
            }
        }
        Rlog.w(LOG_TAG, "getCallForwardingOption->imsPhone is null!.");
    }
    /* @} */
}
