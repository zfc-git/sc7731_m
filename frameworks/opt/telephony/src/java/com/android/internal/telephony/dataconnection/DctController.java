/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.telephony.dataconnection;

import android.content.Context;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Registrant;
import android.os.RegistrantList;
import android.provider.Settings;
import android.telephony.Rlog;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.LocalLog;
import android.util.SparseArray;

import com.android.internal.telephony.OmaApnReceiver;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.dataconnection.DcSwitchAsyncChannel.RequestInfo;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.IndentingPrintWriter;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

public class DctController extends Handler {
    private static final String LOG_TAG = "DctController";
    private static final boolean DBG = true;

    private static final int EVENT_PROCESS_REQUESTS = 100;
    private static final int EVENT_EXECUTE_REQUEST = 101;
    private static final int EVENT_EXECUTE_ALL_REQUESTS = 102;
    private static final int EVENT_RELEASE_REQUEST = 103;
    private static final int EVENT_RELEASE_ALL_REQUESTS = 104;
    private static final int EVENT_RETRY_ATTACH = 105;
    private static final int EVENT_SETTINGS_CHANGED = 106;
    private static final int EVENT_SUBSCRIPTIONS_CHANGED = 107;

    private static final int EVENT_DATA_ATTACHED = 500;
    private static final int EVENT_DATA_DETACHED = 600;
    private static final int EVENT_EMERGENCY_CALL_TOGGLED = 700;
    /* SPRD: Bug424898 Stop data stall alarm during call @{ */
    private static final int EVENT_VOICE_CALL_ENDED   = 800;
    private static final int EVENT_VOICE_CALL_STARTED = 900;
    /* @} */
    // SPRD: Bug 496290 Fix an infinite loop in DcSwitchStateMachine
    private static final int EVENT_RADIO_ON = 1000;

    private static DctController sDctController;

    private int mPhoneNum;
    private PhoneProxy[] mPhones;
    private DcSwitchStateMachine[] mDcSwitchStateMachine;
    private DcSwitchAsyncChannel[] mDcSwitchAsyncChannel;
    private Handler[] mDcSwitchStateHandler;
    private HashMap<Integer, RequestInfo> mRequestInfos = new HashMap<Integer, RequestInfo>();
    private Context mContext;

    /** Used to send us NetworkRequests from ConnectivityService.  Remember it so we can
     * unregister on dispose. */
    private Messenger[] mNetworkFactoryMessenger;
    private NetworkFactory[] mNetworkFactory;
    private NetworkCapabilities[] mNetworkFilter;

    private SubscriptionController mSubController = SubscriptionController.getInstance();

    private SubscriptionManager mSubMgr;
    // SPRD: Notify switch data card complete
    private RegistrantList mSwitchDataCardCompleteList = new RegistrantList();
    private boolean mSetDefaultDataSubid = true;
    private OmaApnReceiver omaApnReceiver;

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            /*
             * SPRD: for bug 472153 @{
             */
            if (SubscriptionManager.isValidSubscriptionId(mSubController.getDefaultDataSubId())) {
                /*SPRD:Bug 510781 it takes more time to do onSubInfoReady by send message @{*/
                onSubInfoReady();
//                DctController.this.obtainMessage(EVENT_SUBSCRIPTIONS_CHANGED).sendToTarget();
                /* @} */
            } else {
                logd("onSubscriptionsChanged: default data sub id is invalid!");
            }
            /* @} */
        }
    };

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            logd("Settings change");
            DctController.this.obtainMessage(EVENT_SETTINGS_CHANGED).sendToTarget();
        }
    };

    public void updatePhoneObject(PhoneProxy phone) {
        if (phone == null) {
            loge("updatePhoneObject phone = null");
            return;
        }

        PhoneBase phoneBase = (PhoneBase)phone.getActivePhone();
        if (phoneBase == null) {
            loge("updatePhoneObject phoneBase = null");
            return;
        }

        for (int i = 0; i < mPhoneNum; i++) {
            if (mPhones[i] == phone) {
                updatePhoneBaseForIndex(i, phoneBase);
                break;
            }
        }
    }

    private void updatePhoneBaseForIndex(int index, PhoneBase phoneBase) {
        logd("updatePhoneBaseForIndex for phone index=" + index);

        phoneBase.getServiceStateTracker().registerForDataConnectionAttached(mRspHandler,
                   EVENT_DATA_ATTACHED + index, null);
        phoneBase.getServiceStateTracker().registerForDataConnectionDetached(mRspHandler,
                   EVENT_DATA_DETACHED + index, null);
        phoneBase.registerForEmergencyCallToggle(mRspHandler,
                EVENT_EMERGENCY_CALL_TOGGLED + index, null);
        /* SPRD: Bug424898 Stop data stall alarm during call @{ */
        phoneBase.getCallTracker().registerForVoiceCallEnded(mRspHandler,
                EVENT_VOICE_CALL_ENDED + index, null);
        phoneBase.getCallTracker().registerForVoiceCallStarted(mRspHandler,
                EVENT_VOICE_CALL_STARTED + index, null);
        /* @} */
        // SPRD: Bug 496290 Fix an infinite loop in DcSwitchStateMachine
        phoneBase.mCi.registerForOn(mRspHandler, EVENT_RADIO_ON + index, null);

        ConnectivityManager cm = (ConnectivityManager)mPhones[index].getContext()
            .getSystemService(Context.CONNECTIVITY_SERVICE);

        if (mNetworkFactoryMessenger != null) {
            logd("unregister TelephonyNetworkFactory for phone index=" + index);
            cm.unregisterNetworkFactory(mNetworkFactoryMessenger[index]);
            mNetworkFactoryMessenger[index] = null;
            mNetworkFactory[index] = null;
            mNetworkFilter[index] = null;
        }

        mNetworkFilter[index] = new NetworkCapabilities();
        mNetworkFilter[index].addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_MMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_SUPL);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_DUN);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_FOTA);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_IMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_CBS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_IA);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_RCS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_XCAP);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_EIMS);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        mNetworkFilter[index].addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        mNetworkFactory[index] = new TelephonyNetworkFactory(this.getLooper(),
                mPhones[index].getContext(), "TelephonyNetworkFactory", phoneBase,
                mNetworkFilter[index]);
        mNetworkFactory[index].setScoreFilter(50);
        mNetworkFactoryMessenger[index] = new Messenger(mNetworkFactory[index]);
        cm.registerNetworkFactory(mNetworkFactoryMessenger[index], "Telephony");
    }

    private Handler mRspHandler = new Handler() {
        @Override
        public void handleMessage(Message msg){
            /* SPRD: Bug 496290 Fix an infinite loop in DcSwitchStateMachine @{ */
            if (msg.what >= EVENT_RADIO_ON) {
                int phoneId = msg.what - EVENT_RADIO_ON;
                int topPriPhone = getTopPriorityRequestPhoneId();
                if (phoneId == topPriPhone) {
                    logd("Phone " + phoneId + " turned on, resume processing requests");
                    processRequests();
                }
            /* @} */
            /* SPRD: Bug424898 Stop data stall alarm during call @{ */
            } else if (msg.what >= EVENT_VOICE_CALL_STARTED) {
                onVoiceCallStateChange((msg.what - EVENT_VOICE_CALL_STARTED), true);
            } else if (msg.what >= EVENT_VOICE_CALL_ENDED) {
                onVoiceCallStateChange((msg.what - EVENT_VOICE_CALL_ENDED), false);
            /* @} */
            } else if (msg.what >= EVENT_EMERGENCY_CALL_TOGGLED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_EMERGENCY_CALL_TOGGLED + 1)
                        + "_EMERGENCY_CALL_END.");
                AsyncResult ar = (AsyncResult) msg.obj;
                Integer toggle = (Integer) ar.result;
                mDcSwitchAsyncChannel[msg.what - EVENT_EMERGENCY_CALL_TOGGLED].
                        notifyEmergencyCallToggled(toggle.intValue());
            } else if (msg.what >= EVENT_DATA_DETACHED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_DATA_DETACHED + 1)
                        + "_DATA_DETACH.");
                mDcSwitchAsyncChannel[msg.what - EVENT_DATA_DETACHED].notifyDataDetached();

            } else if (msg.what >= EVENT_DATA_ATTACHED) {
                logd("EVENT_PHONE" + (msg.what - EVENT_DATA_ATTACHED + 1)
                        + "_DATA_ATTACH.");
                mDcSwitchAsyncChannel[msg.what - EVENT_DATA_ATTACHED].notifyDataAttached();
            }
        }
    };

    public static DctController getInstance() {
       if (sDctController == null) {
        throw new RuntimeException(
            "DctController.getInstance can't be called before makeDCTController()");
        }
       return sDctController;
    }

    public static DctController makeDctController(PhoneProxy[] phones) {
        if (sDctController == null) {
            logd("makeDctController: new DctController phones.length=" + phones.length);
            sDctController = new DctController(phones);
        }
        logd("makeDctController: X sDctController=" + sDctController);
        return sDctController;
    }

    private DctController(PhoneProxy[] phones) {
        logd("DctController(): phones.length=" + phones.length);
        if (phones == null || phones.length == 0) {
            if (phones == null) {
                loge("DctController(phones): UNEXPECTED phones=null, ignore");
            } else {
                loge("DctController(phones): UNEXPECTED phones.length=0, ignore");
            }
            return;
        }
        mPhoneNum = phones.length;
        mPhones = phones;

        mDcSwitchStateMachine = new DcSwitchStateMachine[mPhoneNum];
        mDcSwitchAsyncChannel = new DcSwitchAsyncChannel[mPhoneNum];
        mDcSwitchStateHandler = new Handler[mPhoneNum];
        mNetworkFactoryMessenger = new Messenger[mPhoneNum];
        mNetworkFactory = new NetworkFactory[mPhoneNum];
        mNetworkFilter = new NetworkCapabilities[mPhoneNum];

        for (int i = 0; i < mPhoneNum; ++i) {
            int phoneId = i;
            mDcSwitchStateMachine[i] = new DcSwitchStateMachine(mPhones[i],
                    "DcSwitchStateMachine-" + phoneId, phoneId);
            mDcSwitchStateMachine[i].start();
            mDcSwitchAsyncChannel[i] = new DcSwitchAsyncChannel(mDcSwitchStateMachine[i], phoneId);
            mDcSwitchStateHandler[i] = new Handler();

            int status = mDcSwitchAsyncChannel[i].fullyConnectSync(mPhones[i].getContext(),
                mDcSwitchStateHandler[i], mDcSwitchStateMachine[i].getHandler());

            if (status == AsyncChannel.STATUS_SUCCESSFUL) {
                logd("DctController(phones): Connect success: " + i);
            } else {
                loge("DctController(phones): Could not connect to " + i);
            }

            // Register for radio state change
            PhoneBase phoneBase = (PhoneBase)mPhones[i].getActivePhone();
            updatePhoneBaseForIndex(i, phoneBase);
        }

        mContext = mPhones[0].getContext();
        omaApnReceiver = new OmaApnReceiver(mContext);
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.android.ApnDataConfig");
        mContext.registerReceiver(omaApnReceiver, filter);
        mSubMgr = SubscriptionManager.from(mContext);
        mSubMgr.addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        //Register for settings change.
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(
                Settings.Global.MULTI_SIM_DATA_CALL_SUBSCRIPTION),
                false, mObserver);
    }

    public void dispose() {
        logd("DctController.dispose");
        for (int i = 0; i < mPhoneNum; ++i) {
            ConnectivityManager cm = (ConnectivityManager)mPhones[i].getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
            cm.unregisterNetworkFactory(mNetworkFactoryMessenger[i]);
            mNetworkFactoryMessenger[i] = null;
        }

        mSubMgr.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
        mContext.getContentResolver().unregisterContentObserver(mObserver);
        mContext.unregisterReceiver(omaApnReceiver);
    }


    @Override
    public void handleMessage (Message msg) {
        logd("handleMessage msg=" + msg);
        switch (msg.what) {
            case EVENT_PROCESS_REQUESTS:
                onProcessRequest();
                break;
            case EVENT_EXECUTE_REQUEST:
                onExecuteRequest((RequestInfo)msg.obj);
                break;
            case EVENT_EXECUTE_ALL_REQUESTS:
                onExecuteAllRequests(msg.arg1);
                break;
            case EVENT_RELEASE_REQUEST:
                onReleaseRequest((RequestInfo)msg.obj);
                break;
            case EVENT_RELEASE_ALL_REQUESTS:
                onReleaseAllRequests(msg.arg1);
                break;
            case EVENT_RETRY_ATTACH:
                onRetryAttach(msg.arg1);
                break;
            case EVENT_SETTINGS_CHANGED:
                onSettingsChanged();
                break;
            case EVENT_SUBSCRIPTIONS_CHANGED:
                onSubInfoReady();
                break;
            default:
                loge("Un-handled message [" + msg.what + "]");
        }
    }

    private int requestNetwork(NetworkRequest request, int priority, LocalLog l, int phoneId) {
        logd("requestNetwork request=" + request
                + ", priority=" + priority);
        l.log("Dctc.requestNetwork, priority=" + priority);

        /* SPRD: Bug 494317 PDP is not activated after switching primary card several times @{ */
        RequestInfo old = mRequestInfos.get(request.requestId);
        if (old != null && old.executed) {
            // This may happen when the pending requests are evaluated twice
            loge("Duplicate network request! Attempting to replace " + old + " with " + request);
        } else {
            RequestInfo requestInfo = new RequestInfo(request, priority, l, phoneId);
            mRequestInfos.put(request.requestId, requestInfo);
        }
        /* @} */
        processRequests();

        return PhoneConstants.APN_REQUEST_STARTED;
    }

    private int releaseNetwork(NetworkRequest request) {
        RequestInfo requestInfo = mRequestInfos.get(request.requestId);
        logd("releaseNetwork request=" + request + ", requestInfo=" + requestInfo);
        if (requestInfo != null) requestInfo.log("DctController.releaseNetwork");

        mRequestInfos.remove(request.requestId);
        releaseRequest(requestInfo);
        processRequests();
        return PhoneConstants.APN_REQUEST_STARTED;
    }

    void processRequests() {
        logd("processRequests");
        sendMessage(obtainMessage(EVENT_PROCESS_REQUESTS));
    }

    void executeRequest(RequestInfo request) {
        logd("executeRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_EXECUTE_REQUEST, request));
    }

    void executeAllRequests(int phoneId) {
        logd("executeAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_EXECUTE_ALL_REQUESTS, phoneId,0));
    }

    void releaseRequest(RequestInfo request) {
        logd("releaseRequest, request= " + request);
        sendMessage(obtainMessage(EVENT_RELEASE_REQUEST, request));
    }

    void releaseAllRequests(int phoneId) {
        logd("releaseAllRequests, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_RELEASE_ALL_REQUESTS, phoneId, 0));
    }

    public void retryAttach(int phoneId) {
        logd("retryAttach, phone:" + phoneId);
        sendMessage(obtainMessage(EVENT_RETRY_ATTACH, phoneId, 0));
    }

    private void onProcessRequest() {
        //process all requests
        //1. Check all requests and find subscription of the top priority
        //   request
        //2. Is current data allowed on the selected subscription
        //2-1. If yes, execute all the requests of the sub
        //2-2. If no, set data not allow on the current PS subscription
        //2-2-1. Set data allow on the selected subscription

        int phoneId = getTopPriorityRequestPhoneId();
        int activePhoneId = -1;

        for (int i=0; i<mDcSwitchStateMachine.length; i++) {
            if (!mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            }
        }

        logd("onProcessRequest phoneId=" + phoneId
                + ", activePhoneId=" + activePhoneId);

        if (activePhoneId == -1 || activePhoneId == phoneId) {
            Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
            while (iterator.hasNext()) {
                RequestInfo requestInfo = mRequestInfos.get(iterator.next());
                if (requestInfo.phoneId == phoneId && !requestInfo.executed) {
                    /* SPRD: Bug 496290 Fix an infinite loop in DcSwitchStateMachine @{ */
                    PhoneBase phoneBase = (PhoneBase)mPhones[phoneId].getActivePhone();
                    /*SPRD: Bug 510781 notify Modem default data card before radio on @{ */
                    if (phoneBase.mCi.getRadioState().isOn() || mSetDefaultDataSubid) {
                        mDcSwitchAsyncChannel[phoneId].connect(requestInfo);
                        mSetDefaultDataSubid = false;
                    /* @} */
                    } else {
                        logd("Phone " + phoneId + " is off, pause processing requests");
                    }
                    /* @} */
                }
            }
        } else {
            mDcSwitchAsyncChannel[activePhoneId].disconnectAll();
        }
    }

    /* SPRD: Bug 491298 Can't activated PDP after switch default data sub @{ */
    private void modifyPhoneIdForDefaultRequests() {
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            String specifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
            if (specifier == null || specifier.equals("")) {
                if (!requestInfo.executed) {
                    int subId = mSubController.getDefaultDataSubId();
                    int phoneId = mSubController.getPhoneId(subId);
                    if (SubscriptionManager.isValidPhoneId(phoneId)) {
                        logd("Modify " + requestInfo + " -> phone:" + phoneId);
                        requestInfo.phoneId = phoneId;
                    }
                }
            }
        }
    }
    /* @} */

    private void onExecuteRequest(RequestInfo requestInfo) {
        if (!requestInfo.executed && mRequestInfos.containsKey(requestInfo.request.requestId)) {
            logd("onExecuteRequest request=" + requestInfo);
            requestInfo.log("DctController.onExecuteRequest - executed=" + requestInfo.executed);
            requestInfo.executed = true;
            String apn = apnForNetworkRequest(requestInfo.request);
            int phoneId = requestInfo.phoneId;
            PhoneBase phoneBase = (PhoneBase)mPhones[phoneId].getActivePhone();
            DcTrackerBase dcTracker = phoneBase.mDcTracker;
            dcTracker.incApnRefCount(apn, requestInfo.getLog());
        }
    }

    private void onExecuteAllRequests(int phoneId) {
        logd("onExecuteAllRequests phoneId=" + phoneId);
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            if (requestInfo.phoneId == phoneId) {
                onExecuteRequest(requestInfo);
            }
        }
    }

    private void onReleaseRequest(RequestInfo requestInfo) {
        logd("onReleaseRequest request=" + requestInfo);
        if (requestInfo != null) {
            requestInfo.log("DctController.onReleaseRequest");
            if (requestInfo.executed) {
                String apn = apnForNetworkRequest(requestInfo.request);
                int phoneId = requestInfo.phoneId;
                PhoneBase phoneBase = (PhoneBase)mPhones[phoneId].getActivePhone();
                DcTrackerBase dcTracker = phoneBase.mDcTracker;
                dcTracker.decApnRefCount(apn, requestInfo.getLog());
                requestInfo.executed = false;
            }
        }
    }

    private void onReleaseAllRequests(int phoneId) {
        logd("onReleaseAllRequests phoneId=" + phoneId);
        Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
        while (iterator.hasNext()) {
            RequestInfo requestInfo = mRequestInfos.get(iterator.next());
            if (requestInfo.phoneId == phoneId) {
                onReleaseRequest(requestInfo);
            }
        }
    }

    private void onRetryAttach(int phoneId) {
        final int topPriPhone = getTopPriorityRequestPhoneId();
        logd("onRetryAttach phoneId=" + phoneId + " topPri phone = " + topPriPhone);

        if (phoneId != -1 && phoneId == topPriPhone) {
            mDcSwitchAsyncChannel[phoneId].retryConnect();
        }
    }

    private void onSettingsChanged() {
        //Sub Selection
        int dataSubId = mSubController.getDefaultDataSubId();
        // SPRD: Bug 494317 PDP is not activated after switching primary card several times
        int ddsPhoneId = SubscriptionManager.getPhoneId(dataSubId);

        int activePhoneId = -1;
        for (int i=0; i<mDcSwitchStateMachine.length; i++) {
            if (!mDcSwitchAsyncChannel[i].isIdleSync()) {
                activePhoneId = i;
                break;
            }
        }

        /*
         * SPRD: Notify switch data card complete @{
         */
        if (DBG) logd("activePhoneId = " + activePhoneId);
        if (activePhoneId == -1 || activePhoneId == SubscriptionManager.getPhoneId(dataSubId)) {
            notifySwitchDataCardComplete();
        }
        /* @} */

        int[] subIds = SubscriptionManager.getSubId(activePhoneId);
        /* SPRD: Bug 494317 PDP is not activated after switching primary card several times @{ */
        // Note: we might want to release the default requests either when the default data sub is
        // changed OR when there is no active phone but the request is not released
        boolean maybeRelease = false;
        if (subIds == null || subIds.length == 0) {
            loge("onSettingsChange, subIds null or length 0 for activePhoneId " + activePhoneId);
            if (activePhoneId == -1) {
                maybeRelease = true;
            }
            // @orig
            // return;
        } else {
            logd("onSettingsChange, data sub: " + dataSubId + ", active data sub: " + subIds[0]);
            if (subIds[0] != dataSubId) {
                maybeRelease = true;
            }
        }
        /* @} */

        // SPRD: Bug 494317 PDP is not activated after switching primary card several times
        if (maybeRelease) {
            Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
            while (iterator.hasNext()) {
                RequestInfo requestInfo = mRequestInfos.get(iterator.next());
                String specifier = requestInfo.request.networkCapabilities.getNetworkSpecifier();
                if (specifier == null || specifier.equals("")) {
                    /* SPRD: Bug 494317 if the phoneId != default phone id, then release it @{ */
                    int phoneId = requestInfo.phoneId;
                    if (requestInfo.executed && phoneId != ddsPhoneId) {
                    /* @} */
                        String apn = apnForNetworkRequest(requestInfo.request);
                        logd("[setDataSubId] activePhoneId:" + activePhoneId + ", subId =" +
                                dataSubId + ", ddsPhoneId=" + ddsPhoneId);
                        requestInfo.log("DctController.onSettingsChange releasing request");
                        /* SPRD: Bug 494317 release the default request @{ */
                        PhoneBase phoneBase =
                                (PhoneBase)mPhones[phoneId].getActivePhone();
                        /* @} */
                        DcTrackerBase dcTracker = phoneBase.mDcTracker;
                        dcTracker.decApnRefCount(apn, requestInfo.getLog());
                        requestInfo.executed = false;
                    }
                }
            }
        }

        // Some request maybe pending due to invalid settings
        // Try to handle pending request when settings changed
        for (int i = 0; i < mPhoneNum; ++i) {
            ((DctController.TelephonyNetworkFactory)mNetworkFactory[i]).evalPendingRequest();
        }

        /* SPRD: Bug 491298 Can't activated PDP after switch default data sub @{ */
        // This is temp fix.
        // Google's latest commit 7c02b0df64bcc66bd0e39ac99d30febb61d8ab6a
        // introduced this bug.
        // The request now carries phoneId, but the default request should not use this
        // parameter, it should use getPhoneId(getDefaultDataSubId())
        // As a temp fix, we modify the phoneId parameter to the default data phone id,
        // so that the request can be executed and released
        modifyPhoneIdForDefaultRequests();
        /* @} */

        processRequests();
    }

    private int getTopPriorityRequestPhoneId() {
        RequestInfo retRequestInfo = null;
        int phoneId = 0;
        int priority = -1;

        //TODO: Handle SIM Switch
        for (int i=0; i<mPhoneNum; i++) {
            Iterator<Integer> iterator = mRequestInfos.keySet().iterator();
            while (iterator.hasNext()) {
                RequestInfo requestInfo = mRequestInfos.get(iterator.next());
                logd("selectExecPhone requestInfo = " + requestInfo);
                if (requestInfo.phoneId == i &&
                        (priority < requestInfo.priority ||
                        (priority == requestInfo.priority && !mDcSwitchAsyncChannel[i].isIdleSync()))) {
                    priority = requestInfo.priority;
                    retRequestInfo = requestInfo;
                }
            }
        }

        if (retRequestInfo != null) {
            phoneId = getRequestPhoneId(retRequestInfo.request);
        } else {
            int defaultDds = mSubController.getDefaultDataSubId();
            phoneId = mSubController.getPhoneId(defaultDds);
            logd("getTopPriorityRequestPhoneId: RequestInfo list is empty, " +
                    "use Dds sub phone id");
        }

        logd("getTopPriorityRequestPhoneId = " + phoneId
                + ", priority = " + priority);

        return phoneId;
    }

    private void onSubInfoReady() {
        logd("onSubInfoReady mPhoneNum=" + mPhoneNum);
        for (int i = 0; i < mPhoneNum; ++i) {
            int subId = mPhones[i].getSubId();
            logd("onSubInfoReady handle pending requests subId=" + subId);
            mNetworkFilter[i].setNetworkSpecifier(String.valueOf(subId));
            ((DctController.TelephonyNetworkFactory)mNetworkFactory[i]).evalPendingRequest();
        }
        processRequests();
    }

    private String apnForNetworkRequest(NetworkRequest nr) {
        NetworkCapabilities nc = nr.networkCapabilities;
        // For now, ignore the bandwidth stuff
        if (nc.getTransportTypes().length > 0 &&
                nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == false) {
            return null;
        }

        // in the near term just do 1-1 matches.
        // TODO - actually try to match the set of capabilities
        int type = -1;
        String name = null;

        boolean error = false;
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_DEFAULT;
            type = ConnectivityManager.TYPE_MOBILE;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_MMS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_MMS;
            type = ConnectivityManager.TYPE_MOBILE_MMS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_SUPL)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_SUPL;
            type = ConnectivityManager.TYPE_MOBILE_SUPL;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_DUN)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_DUN;
            type = ConnectivityManager.TYPE_MOBILE_DUN;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOTA)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_FOTA;
            type = ConnectivityManager.TYPE_MOBILE_FOTA;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IMS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_IMS;
            type = ConnectivityManager.TYPE_MOBILE_IMS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_CBS;
            type = ConnectivityManager.TYPE_MOBILE_CBS;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_IA)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_IA;
            type = ConnectivityManager.TYPE_MOBILE_IA;
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_RCS)) {
            if (name != null) error = true;
            name = null;
            loge("RCS APN type not yet supported");
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_XCAP)) {
            if (name != null) error = true;
            name = null;
            loge("XCAP APN type not yet supported");
        }
        if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_EIMS)) {
            if (name != null) error = true;
            name = PhoneConstants.APN_TYPE_EMERGENCY;
            type = ConnectivityManager.TYPE_MOBILE_EMERGENCY;
        }
        if (error) {
            // TODO: If this error condition is removed, the framework's handling of
            // NET_CAPABILITY_NOT_RESTRICTED will need to be updated so requests for
            // say FOTA and INTERNET are marked as restricted.  This is not how
            // NetworkCapabilities.maybeMarkCapabilitiesRestricted currently works.
            loge("Multiple apn types specified in request - result is unspecified!");
        }
        if (type == -1 || name == null) {
            loge("Unsupported NetworkRequest in Telephony: nr=" + nr);
            return null;
        }
        return name;
    }

    private int getRequestPhoneId(NetworkRequest networkRequest) {
        String specifier = networkRequest.networkCapabilities.getNetworkSpecifier();
        int subId;
        if (specifier == null || specifier.equals("")) {
            subId = mSubController.getDefaultDataSubId();
        } else {
            subId = Integer.parseInt(specifier);
        }
        int phoneId = mSubController.getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            phoneId = 0;
            if (!SubscriptionManager.isValidPhoneId(phoneId)) {
                throw new RuntimeException("Should not happen, no valid phoneId");
            }
        }
        return phoneId;
    }

    /* SPRD: Bug424898 Stop data stall alarm during call @{ */
    private void onVoiceCallStateChange(int phoneId, boolean inCall) {
        logd("Voice call " + (inCall ? "started" : "ended") + " on phone " + phoneId);
        for (int i = 0; i < mPhoneNum; i++) {
            PhoneBase phoneBase = (PhoneBase)mPhones[i].getActivePhone();
            DcTrackerBase dcTracker = phoneBase.mDcTracker;
            if (inCall) {
                dcTracker.onVoiceCallStarted(phoneId);
            } else {
                dcTracker.onVoiceCallEnded(phoneId);
            }
        }
    }
    /* @} */


    /*
     * SPRD: Notify switch data card complete @{
     */
    public void registerForSwitchDataCardComplete(Handler h, int what, Object obj) {
        if (DBG) logd("registerForSwitchDataCardComplete");
        Registrant r = new Registrant(h, what, obj);
        mSwitchDataCardCompleteList.add(r);
    }

    public void unRegisterForSwitchDataCardComplete(Handler h) {
        if (DBG)logd("unRegisterForSwitchDataCardComplete");
        mSwitchDataCardCompleteList.remove(h);
    }

    public void notifySwitchDataCardComplete() {
        if (mSwitchDataCardCompleteList.size() > 0) {
            if (DBG) logd("notifySwitchDataCardComplete");
            mSwitchDataCardCompleteList.notifyRegistrants();
        }
    }
    /* @} */
    private static void logd(String s) {
        if (DBG) Rlog.d(LOG_TAG, s);
    }

    private static void loge(String s) {
        if (DBG) Rlog.e(LOG_TAG, s);
    }

    private class TelephonyNetworkFactory extends NetworkFactory {
        private final SparseArray<NetworkRequest> mPendingReq = new SparseArray<NetworkRequest>();
        private Phone mPhone;
        private class RequestLogger {
            public NetworkRequest request;
            public LocalLog log;

            public RequestLogger(NetworkRequest r, LocalLog log) {
                request = r;
                this.log = log;
            }
        }

        private static final int MAX_REQUESTS_LOGGED = 20;
        private static final int MAX_LOG_LINES_PER_REQUEST = 50;

        private ArrayDeque<RequestLogger> mRequestLogs = new ArrayDeque<RequestLogger>();

        public TelephonyNetworkFactory(Looper l, Context c, String TAG, Phone phone,
                NetworkCapabilities nc) {
            super(l, c, TAG, nc);
            mPhone = phone;
            log("NetworkCapabilities: " + nc);
        }

        public LocalLog requestLog(int requestId, String l) {
            synchronized(mRequestLogs) {
                for (RequestLogger r : mRequestLogs) {
                    if (r.request.requestId == requestId) {
                        r.log.log(l);
                        return r.log;
                    }
                }
            }
            return null;
        }

        private LocalLog addLogger(NetworkRequest request) {
            synchronized(mRequestLogs) {
                for (RequestLogger r : mRequestLogs) {
                    if (r.request.requestId == request.requestId) {
                        return r.log;
                    }
                }
                LocalLog l = new LocalLog(MAX_LOG_LINES_PER_REQUEST);
                RequestLogger logger = new RequestLogger(request, l);
                while (mRequestLogs.size() >= MAX_REQUESTS_LOGGED) {
                    mRequestLogs.removeFirst();
                }
                mRequestLogs.addLast(logger);
                return l;
            }
        }
        /*SPRD: for bug 537741 pending request when voice call on other phone@{*/
        private boolean isOtherPhoneCalling(int requestPhoneId){
            for(Phone p : mPhones) {
                if(p.getState() != PhoneConstants.State.IDLE && p.getPhoneId() != requestPhoneId) {
                    return true;
                }
            }
            return false;
        }
        /* @} */
        @Override
        protected void needNetworkFor(NetworkRequest networkRequest, int score) {
            // figure out the apn type and enable it
            log("Cellular needs Network for " + networkRequest);

            final LocalLog l = addLogger(networkRequest);

            if (!SubscriptionManager.isUsableSubIdValue(mPhone.getSubId()) ||
                    getRequestPhoneId(networkRequest) != mPhone.getPhoneId()) {
                final String str = "Request not useable, pending request.";
                log(str);
                l.log(str);
                mPendingReq.put(networkRequest.requestId, networkRequest);
                return;
            }

            /*SPRD: for bug 537741 pending request when voice call on other phone@{*/
            int subId = mSubController.getDefaultDataSubId();
            int phoneId = mSubController.getPhoneId(subId);
            if(getRequestPhoneId(networkRequest) != phoneId && isOtherPhoneCalling(getRequestPhoneId(networkRequest))){
                logd("needNetworkFor: other phone is calling");
                return;
            }

            DcTrackerBase dcTracker =((PhoneBase)mPhone).mDcTracker;
            String apn = apnForNetworkRequest(networkRequest);
            if (dcTracker.isApnSupported(apn)) {
                requestNetwork(networkRequest, dcTracker.getApnPriority(apn), l,
                        mPhone.getPhoneId());
            } else {
                final String str = "Unsupported APN";
                log(str);
                l.log(str);
            }
        }

        @Override
        protected void releaseNetworkFor(NetworkRequest networkRequest) {
            String str = "Cellular releasing Network for ";
            log(str + networkRequest);
            final LocalLog l = requestLog(networkRequest.requestId, str);

            if (mPendingReq.get(networkRequest.requestId) != null) {
                str = "Sub Info has not been ready, remove request.";
                log(str);
                if (l != null) l.log(str);
                mPendingReq.remove(networkRequest.requestId);
                return;
            }

            releaseNetwork(networkRequest);
        }

        @Override
        protected void log(String s) {
            if (DBG) Rlog.d(LOG_TAG, "[TNF " + mPhone.getSubId() + "]" + s);
        }

        public void evalPendingRequest() {
            log("evalPendingRequest, pending request size is " + mPendingReq.size());
            int key = 0;
            for(int i = 0; i < mPendingReq.size(); i++) {
                key = mPendingReq.keyAt(i);
                NetworkRequest request = mPendingReq.get(key);
                log("evalPendingRequest: request = " + request);

                mPendingReq.remove(request.requestId);
                needNetworkFor(request, 0);
            }
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            super.dump(fd, writer, args);
            final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
            pw.increaseIndent();
            pw.println("Pending Requests:");
            pw.increaseIndent();
            for (int i = 0; i < mPendingReq.size(); i++) {
                NetworkRequest request = mPendingReq.valueAt(i);
                pw.println(request);
            }
            pw.decreaseIndent();

            pw.println("Request History:");
            pw.increaseIndent();
            synchronized(mRequestLogs) {
                for (RequestLogger r : mRequestLogs) {
                    pw.println(r.request);
                    pw.increaseIndent();
                    r.log.dump(fd, pw, args);
                    pw.decreaseIndent();
                }
            }
            pw.decreaseIndent();
            pw.decreaseIndent();
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("DctController:");
        try {
            for (DcSwitchStateMachine dssm : mDcSwitchStateMachine) {
                dssm.dump(fd, pw, args);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");

        try {
            for (Entry<Integer, RequestInfo> entry : mRequestInfos.entrySet()) {
                pw.println("mRequestInfos[" + entry.getKey() + "]=" + entry.getValue());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
        pw.println("TelephonyNetworkFactories:");
        for (NetworkFactory tnf : mNetworkFactory) {
            tnf.dump(fd, pw, args);
        }
        pw.flush();
        pw.println("++++++++++++++++++++++++++++++++");
        pw.flush();
    }
}
