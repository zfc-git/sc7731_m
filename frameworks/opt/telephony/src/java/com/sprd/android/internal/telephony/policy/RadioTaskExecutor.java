
package com.android.internal.telephony.policy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.RadioFeatures;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import com.android.internal.telephony.ProxyController;
import com.android.internal.telephony.TeleUtils;
import com.android.internal.telephony.policy.RadioTaskManager.Event;

//import com.sprd.internal.telephony.uicc.IATUtils;FIXME

public class RadioTaskExecutor {

    enum State {
        NEW,
        RUNNING,
        FINISHED
    }

    enum SubTask {
        UNKNOWN,
        RADIO_POWER_ON,
        RADIO_POWER_OFF,
        SEND_TESTMODE,
        SEND_SPUECAP,
        UPDATA_DEFAULT_DATA,
        AUTO_SET_DEFAULT_PHONE,
        BROADCAST_SET_PRIMARY_CARD_COMPLETE
    }

    private static final String LOG_TAG = "RadioTaskExecutor";
    private static final Object mLock = new Object();
    private static final String AT_ENABLE_LTE = "AT+SPUECAP=0";
    private static final String AT_DISABLE_LTE = "AT+SPUECAP=1";

    private static final int TASK_DONE = 0;
    private static final int SWITCH_DATA_CARD_COMPLETE = 1;
    private static final int EVENT_OFFSET = 4;
    static final int ALL_RADIOS_NEED_OPERATION = 4;

    private static final int RADIO_OPERATION_TIMEOUT = 125000;
    private static final int RADIO_OPERATION_TIMEOUT_PLUS = 160000;

    private static final String SET_PRIMARYCARD_COMPLETE =
            "android.intent.action.SET_PRIMARYCARD_COMPLETE";

    private LinkedList<Task> mQueue = new LinkedList<Task>();
    private Context mContext;
    private static int mPhoneCount = 1;
    private volatile byte mRadioBusyFlag = 0;
    private static RadioTaskExecutor mInstance = null;
    private ExecutorService mExecutorService;
    private List<SubscriptionInfo> mSubInfoRecords;
    private SubscriptionManager mSubManager;
    private TelephonyManager mTelephonyManager;
    private boolean mNeedWaitForNotify;

    private RadioTaskExecutor(Context context) {
        Log.d(LOG_TAG, "create RadioTaskExecutor");
        mContext = context;
        mPhoneCount = TelephonyManager.getDefault().getPhoneCount();
        mExecutorService = Executors.newSingleThreadExecutor();
        mSubManager = SubscriptionManager.from(mContext);
        mTelephonyManager = (TelephonyManager) TelephonyManager.from(mContext);
    }

    /**
     * Returns the singleton instance of the RadioTaskExecutor.
     */
    static RadioTaskExecutor getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new RadioTaskExecutor(context);
        }
        return mInstance;
    }

    private void removeSpareTasks() {
        synchronized (mLock) {
            Log.d(LOG_TAG, "removeSpareTasks");
            Task lastTask = mQueue.getLast();
            Log.d(LOG_TAG, "last task : " + lastTask.mEvent.toString());
            boolean retainLastSameTask = false;
            boolean retainFirstSameTask = false;
            Iterator<Task> it = mQueue.iterator();

            while (it.hasNext()) {
                Task task = it.next();
                Log.d(LOG_TAG, "task : " + task.mEvent.toString() + " task.mEnable = "
                        + task.mEnable);

                if (task.mEvent != Event.SET_PRIMARY_CARD
                        && lastTask.mEvent.equals(task.mEvent)) {
                    if (!retainLastSameTask && !retainFirstSameTask) {
                        if (lastTask.mState.equals(task.mState)) {
                            retainLastSameTask = true;
                            Log.d(LOG_TAG, "retainLastSameTask = " + retainLastSameTask);
                            if (!lastTask.equals(task)) {
                                it.remove();
                            }
                        } else if (lastTask.mEnable == task.mEnable
                                && lastTask.mPhoneId == task.mPhoneId) {
                            retainFirstSameTask = true;
                            Log.d(LOG_TAG, "retainFirstSameTask = " + retainFirstSameTask +
                                    "  task.mEnable = " + task.mEnable);
                        } else {
                            retainLastSameTask = true;
                            retainFirstSameTask = true;
                            Log.d(LOG_TAG, "retainLastSameTask = " + retainLastSameTask
                                    + "  retainFirstSameTask = " + retainFirstSameTask);
                        }
                    } else if (!retainLastSameTask || !lastTask.equals(task)) {
                        it.remove();
                        Log.d(LOG_TAG, "remove task : " + task.mEvent.toString()
                                + " task.mEnable = " + task.mEnable);
                    }
                }
            }
        }
    }

    private void loop() {
        synchronized (mLock) {
            Log.d(LOG_TAG, "start loop");
            printTaskQueue();

            if (mQueue.isEmpty()) {
                Log.d(LOG_TAG, "task queue is empty now, quit loop");
                return;
            }

            final Task currentTask = mQueue.getFirst();
            Log.d(LOG_TAG, currentTask == null ? "null"
                    : ("currentTask state = " + currentTask.mState.toString()));

            if (currentTask != null && currentTask.mState == State.NEW) {
                currentTask.mState = State.RUNNING;
                mExecutorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        execute(currentTask);
                    }
                });
            }
        }
    }

    private void execute(Task task) {
        mTelephonyManager.setRadioBusy(true);
        Iterator<Integer> it = task.mSubQueue.iterator();
        Log.d(LOG_TAG, "start execute task: " + task.mEvent.toString() + ", radio busy start.");
        while (it.hasNext()) {
            int subTaskId = it.next().intValue();
            int eventId;
            int phoneId = ALL_RADIOS_NEED_OPERATION;
            eventId = subTaskId >>> (ALL_RADIOS_NEED_OPERATION * EVENT_OFFSET);
            if (eventId == 0) {
                eventId = subTaskId >> (task.mPhoneId * EVENT_OFFSET);
                phoneId = task.mPhoneId;
            }
            SubTask subTask = SubTask.values()[eventId];
            Log.d(LOG_TAG, "execute sub task : " + subTask.toString() + " phoneId = " + phoneId);

            switch (subTask) {
                case RADIO_POWER_OFF:
                    setRadioPower(phoneId, false, false);
                    break;

                case RADIO_POWER_ON:
                    setRadioPower(phoneId, true, Event.AIRPLANE_MODE.equals(task.mEvent));
                    break;

                case SEND_TESTMODE:
                    switchRadioFeatures();
                    break;

                case SEND_SPUECAP:
                    sendAtCmdToSetLteEnabled(task.mEnable);
                    break;

                case UPDATA_DEFAULT_DATA:
                    // SPRD: add new feature for data switch on/off
                    updateDefaultDataToPrimaryCard();
                    try {
                        Log.d(LOG_TAG,
                                "after updateDefaultDataToPrimaryCard done, sleep 1s before power radio on");
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                case AUTO_SET_DEFAULT_PHONE:
                    autoSetDefaultPhones();
                    break;

                case BROADCAST_SET_PRIMARY_CARD_COMPLETE:
                    broadcastSetPrimaryCardComplete();
                    break;

                default:
                    break;
            }

            if (mRadioBusyFlag > 0) {
                try {
                    synchronized (mLock) {
                        Log.d(LOG_TAG, "waiting...");
                        mLock.wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.d(LOG_TAG, "done");
            }
        }
        Log.d(LOG_TAG, "finish execute task: " + task.mEvent.toString() + ", radio busy end.");
        mHandler.sendEmptyMessage(TASK_DONE);
        mTelephonyManager.setRadioBusy(false);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case SWITCH_DATA_CARD_COMPLETE:
                mHandler.removeMessages(SWITCH_DATA_CARD_COMPLETE);
                synchronized (mLock) {
                    mNeedWaitForNotify = false;
                    Log.d(LOG_TAG, "SWITCH_DATA_CARD_COMPLETE notify all!");
                    mLock.notifyAll();
                }
                break;
            case TASK_DONE:
                finishTask(mQueue.getFirst());
                loop();
                break;
            default:
                break;
            }
        }
    };

    /**
     * Update default data phone id when switching primary card. It is necessary only for LTE device
     * because LTE service requires data registration.
     */
    private void updateDefaultDataToPrimaryCard() {
        int primaryCard = mTelephonyManager.getPrimaryCard();
        if (SubscriptionManager.isValidPhoneId(primaryCard)) {
            int subId = SubscriptionManager.getSubId(primaryCard)[0];
            if (TeleUtils.isSuppSetDataAndPrimaryCardBind()
                    && SubscriptionManager.getDefaultDataSubId() == subId) {
                return;
            }

            Log.d(LOG_TAG, "updateDefaultDataToPrimaryCard: " + primaryCard + " subId = " + subId);
            /*SPRD: add for bug501515*/
            ProxyController.getInstance().setDataAllowed(primaryCard, true, null);
            int lastDefaultDataSubId = SubscriptionManager.getDefaultDataSubId();
            if(lastDefaultDataSubId != subId){
                // SPRD: [Bug551350] Set 'true' as default value for data connection.
                //boolean previousDataEnabled = true;
				boolean previousDataEnabled = "true".equalsIgnoreCase(SystemProperties.get(
               "ro.com.android.mobiledata", "true"));
                if (SubscriptionManager.isValidSubscriptionId(lastDefaultDataSubId)) {
                    previousDataEnabled = mTelephonyManager.getDataEnabled();
                }

                PhoneProxy phone = (PhoneProxy) PhoneFactory.getDefaultPhone();
                phone.registerForSwitchDataCardComplete(mHandler, SWITCH_DATA_CARD_COMPLETE, null);
                mNeedWaitForNotify = true;

                mSubManager.setDefaultDataSubId(subId);

                try {
                    synchronized (mLock) {
                        if (mNeedWaitForNotify) {
                            mHandler.sendEmptyMessageDelayed(SWITCH_DATA_CARD_COMPLETE, 5000);
                            Log.d(LOG_TAG, "waiting for switch data card complete...");
                            mLock.wait();
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                phone.unRegisterForSwitchDataCardComplete(mHandler);

                /* SPRD: [bug475942] If data button in EngineerMode switched on, remain data
                status as the last data state set by user even when sim changed. @} */
                boolean isDataRemainUnchangedSetByEM = "true".equals(
                        SystemProperties.get("persist.sys.data.restore", "false"));
                if (isDataRemainUnchangedSetByEM) {
                    previousDataEnabled = Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.DATA_REMAIN_UNCHANGED, 1) == 1;
                }
                mTelephonyManager.setDataEnabled(previousDataEnabled);
                // SPRD: Bug 548988 After switch primary card, PDP is not activated
                detachDataForOtherSubscriptions();
                if (previousDataEnabled) {
                    disableDataForOtherSubscriptions();
                }
            }
        }
    }

    private void setRadioPower(int phoneId, boolean onOff, boolean force) {
        if (phoneId == ALL_RADIOS_NEED_OPERATION) {
            for (int i = 0; i < mPhoneCount; i++) {
                setRadioPowerStateToDesired(i, onOff, force);
            }
        } else {
            setRadioPowerStateToDesired(phoneId, onOff, force);
        }
    }

    private void setRadioPowerStateToDesired(final int phoneId, final boolean desiredPowerState, boolean force) {
        if (desiredPowerState && !RadioTaskManager.getDefault().canPowerRadioOn(phoneId)) {
            Log.d(LOG_TAG, "Not allowed to power on radio " + phoneId);
            return;
        }

        mRadioBusyFlag++;
        final RadioInteraction radioInteraction = new RadioInteraction(mContext, phoneId);
        radioInteraction.setCallBack(new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "run phone " + phoneId + " power radio "
                        + (desiredPowerState ? "on" : "off")
                        + " callback... mRadioBusyFlag = " + mRadioBusyFlag);
                radioInteraction.destoroy();

                if (getRadioBusyFlag() == 0) {
                    synchronized (mLock) {
                        Log.d(LOG_TAG, "ALL_RADIOS_OPERATION_DONE notify all!");
                        mLock.notifyAll();
                    }
                }
            }
        });

        if (desiredPowerState) {
            radioInteraction.powerOnRadio(RADIO_OPERATION_TIMEOUT, force);
        } else {
            radioInteraction.powerOffRadio(RADIO_OPERATION_TIMEOUT_PLUS);
        }
        Log.d(LOG_TAG, "try to power radio " + (desiredPowerState ? "on " : "off ")
                + phoneId + " mRadioBusyFlag = " + mRadioBusyFlag);
    }

    private synchronized byte getRadioBusyFlag() {
        return --mRadioBusyFlag;
    }

    /**
     * Set sp test mode before powering on all radios.For example,6 means multi mode in 4mod device
     * while it is 7 in 3mod device.However,10 means GSM single mode and 254 means no sim card in
     * any devices.
     */
    private void switchRadioFeatures() {
        RadioFeatures[] radioFeatures = RadioTaskManager.getRadioFeatures();
        TelephonyManager tm = (TelephonyManager) TelephonyManager.from(mContext);
        for (int i = 0; i < mPhoneCount; i++) {
            Log.d(LOG_TAG, "switchRadioFeatures[" + i + "] : " + radioFeatures[i].toString());
            tm.switchRadioFeatures(mContext, i, radioFeatures[i]);
        }
    }

    /**
     * Send AT command to set or get lte state: enable lte(AT+SPUECAP=0) disable lte(AT+SPUECAP=1)
     * get lte state(AT+SPUECAP?)
     *
     * @param enabled
     */
    private void sendAtCmdToSetLteEnabled(boolean enabled) {
        int primaryCard = mTelephonyManager.getPrimaryCard();
        if (primaryCard != IccPolicy.INVALID_PRIMARY_PHONE_ID) {
            String atCommand = enabled ? AT_ENABLE_LTE : AT_DISABLE_LTE;
            String[] response = {
                ""
            };
            int responseLength = mTelephonyManager.invokeOemRilRequestStrings(primaryCard,
                    new String
                    [] {
                        atCommand
                    }, response);
            Log.d(LOG_TAG, "setLteEnabledToDesired sendATCmd : " + atCommand +
                    " response = " + response[0]);
        }
    }

    /* SPRD: Keep lte status with BP. See bug:438813. {@   FIXME*/
    // private void queryLteStatusWithAtCmd() {
    // int primaryCard = mTelephonyManager.getPrimaryCard();
    // if (primaryCard != IccPolicy.INVALID_PRIMARY_PHONE_ID) {
    // String[] resp = {""};
    // int respLength = mTelephonyManager.invokeOemRilRequestStrings(primaryCard,new
    // String[]{AT_QUERY_LTE_STATUS},resp);
    // boolean lteEnabled = false;
    //
    // if (resp[0] != null && resp[0].contains(":")) {
    // resp[0] = resp[0].substring(resp[0].lastIndexOf(":") + 1, resp[0].indexOf("\r"));
    // if (resp[0].trim().equals(LTE_ENABLED)) lteEnabled = true;
    // }
    // Log.d(LOG_TAG, "queryLteStatusWithAtCmd resp: " + resp[0] + ", lteEnabled: " + lteEnabled);
    // RadioTaskManager.getDefault().savePreferredNetworkType(lteEnabled);
    // }
    // }
    /* @} */

    private List<SubscriptionInfo> getActiveSubInfoList() {
        /* SPRD: modify for avoid null point exception @{ */
        if (mSubManager == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        /* @} */
        List<SubscriptionInfo> availableSubInfoList = mSubManager
                .getActiveSubscriptionInfoList();
        if (availableSubInfoList == null) {
            return new ArrayList<SubscriptionInfo>();
        }
        Iterator<SubscriptionInfo> iterator = availableSubInfoList.iterator();
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            boolean isSimReady = mTelephonyManager.getSimState(phoneId) == TelephonyManager.SIM_STATE_READY;
            boolean isSimStandby = Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.SIM_STANDBY + phoneId, 1) == 1;
            if (!isSimStandby || !isSimReady) {
                iterator.remove();
            }
        }
        return availableSubInfoList;
    }

    /**
     * Need auto set default sms/voice settings to active sim after set stand by false. Need restore
     * subscriber settings if target sim is active after set stand by true.
     */
    private void autoSetDefaultPhones() {
        TelephonyManager tm = (TelephonyManager) TelephonyManager.from(mContext);
        mSubInfoRecords = getActiveSubInfoList();
        int defaultVoiceSubId = tm.getMultiSimActiveDefaultVoiceSubId();
        Log.d(LOG_TAG, "autoSetDefaultPhones: defaultVoiceSubId = " + defaultVoiceSubId);
        if (defaultVoiceSubId == SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE
                && mSubInfoRecords.size() < 2
                || !isSubIdActive(defaultVoiceSubId)) {
            if (mSubInfoRecords.size() > 0) {
                int subId = mSubInfoRecords.get(0).getSubscriptionId();
                setDefaultVoiceSubId(subId);
            }
        } else {
            setDefaultVoiceSubId(defaultVoiceSubId);
        }

        int defaultSmsSubId = tm.getMultiSimActiveDefaultSmsSubId();
        // SPRD: add option to enable/disable sim card
        boolean previousDataEnabled = mTelephonyManager.getDataEnabled();
        Log.d(LOG_TAG, "autoSetDefaultPhones: defaultSmsSubId = " + defaultSmsSubId);
        if (defaultSmsSubId == SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE
                && mSubInfoRecords.size() < 2
                || !isSubIdActive(defaultSmsSubId)) {
            if (mSubInfoRecords.size() > 0) {
                mSubManager.setDefaultSmsSubId(mSubInfoRecords.get(0).getSubscriptionId());
            }
        } else {
            mSubManager.setDefaultSmsSubId(defaultSmsSubId);
        }
    }

    /* SPRD: add option to enable/disable sim card @{ */
    private void disableDataForOtherSubscriptions() {
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
        List<SubscriptionInfo> subInfoList = mSubManager.getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                if (subInfo.getSubscriptionId() != defaultDataSubId) {
                    mTelephonyManager.setDataEnabled(subInfo.getSubscriptionId(), false);
                }
            }
        }
    }
    /* @} */

    /* SPRD: Bug 548988 After switch primary card, PDP is not activated @{ */
    private void detachDataForOtherSubscriptions() {
        int defaultDataSubId = SubscriptionManager.getDefaultDataSubId();
        List<SubscriptionInfo> subInfoList = mSubManager.getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            for (SubscriptionInfo subInfo : subInfoList) {
                if (subInfo.getSubscriptionId() != defaultDataSubId) {
                    /*SPRD: add for bug501515*/
                    ProxyController.getInstance().setDataAllowed(SubscriptionManager.getPhoneId(
                                subInfo.getSubscriptionId()), false, null);
                    /* @} */
                }
            }
        }
    }
    /* @} */

    private void setDefaultVoiceSubId(int subId) {
        TelecomManager telecomManager = TelecomManager.from(mContext);
        PhoneAccountHandle phoneAccountHandle =
                subscriptionIdToPhoneAccountHandle(subId);
        telecomManager.setUserSelectedOutgoingPhoneAccount(phoneAccountHandle);
    }

    private void broadcastSetPrimaryCardComplete() {
        Intent intent = new Intent(SET_PRIMARYCARD_COMPLETE);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        mContext.sendBroadcast(intent);
    }

    private PhoneAccountHandle subscriptionIdToPhoneAccountHandle(final int subId) {
        final TelecomManager telecomManager = TelecomManager.from(mContext);
        final TelephonyManager telephonyManager = TelephonyManager.from(mContext);
        final Iterator<PhoneAccountHandle> phoneAccounts =
                telecomManager.getCallCapablePhoneAccounts().listIterator();

        while (phoneAccounts.hasNext()) {
            final PhoneAccountHandle phoneAccountHandle = phoneAccounts.next();
            final PhoneAccount phoneAccount = telecomManager.getPhoneAccount(phoneAccountHandle);

            if (telephonyManager.getSubIdForPhoneAccount(phoneAccount) == subId) {
                return phoneAccountHandle;
            }
        }

        return null;
    }

    private boolean isSubIdActive(long subId) {
        if (subId == SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE) {
            return true;
        }

        for (SubscriptionInfo subInfo : mSubInfoRecords) {
            if (subInfo.getSubscriptionId() == subId) {
                return true;
            }
        }
        return false;
    }

    private static int encodeEventId(SubTask subTask, int phoneId) {
        int eventId = subTask.ordinal();
        return eventId << (phoneId * EVENT_OFFSET);
    }

    public void addTask(Task task) {
        synchronized (mLock) {
            Log.d(LOG_TAG, "add task start");
            if (task != null) {
                mQueue.addLast(task);
                printTaskQueue();
                removeSpareTasks();
                loop();
            }
            Log.d(LOG_TAG, "add task end");
        }
    }

    public Task getTask() {
        synchronized (mLock) {
            Iterator<Task> it = mQueue.iterator();
            Task task;
            while (it.hasNext()) {
                task = it.next();
                if (State.NEW.equals(task.mState)) {
                    task.mState = State.RUNNING;
                    return task;
                }
            }
            return null;
        }
    }

    public void finishTask(Task task) {
        synchronized (mLock) {
            if (task != null) {
                task.mState = State.FINISHED;
                mQueue.remove(task);
            }
        }
    }

    private void printTaskQueue() {
        synchronized (mLock) {
            Iterator<Task> it = mQueue.iterator();
            int taskCount = 0;
            StringBuilder tasks = new StringBuilder("");
            while (it.hasNext()) {
                taskCount++;
                Task task = it.next();
                tasks.append(" [" + taskCount + "]" + task.mEvent.toString());
            }
            Log.d(LOG_TAG, "Task queue :" + tasks.toString());
        }
    }

    static class Task {

        State mState = State.NEW;
        private int mPhoneId = RadioTaskExecutor.ALL_RADIOS_NEED_OPERATION;
        Event mEvent;
        boolean mEnable;
        private List<Integer> mSubQueue = new LinkedList<Integer>();
        boolean mSetDataEnable = false;

        public Task(Event event) {
            mEvent = event;
        }

        public Task(Event event, int phoneId) {
            mEvent = event;
            mPhoneId = phoneId;
        }

        public Task(Event event, boolean enable) {
            mEvent = event;
            mEnable = enable;
        }

        public Task(Event event, int phoneId, boolean enable) {
            mEvent = event;
            mPhoneId = phoneId;
            mEnable = enable;
        }

        public void addSubTask(SubTask subTask) {
            mSubQueue.add(encodeEventId(subTask, mPhoneId));
        }

        public void addSubTask(SubTask subTask, int phoneId) {
            mSubQueue.add(encodeEventId(subTask, phoneId));
        }
    }
}
