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

package com.android.internal.telephony.test;

import android.os.AsyncResult;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.gsm.CallFailCause;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import java.util.ArrayList;

public final class SimulatedCommands extends BaseCommands
        implements CommandsInterface, SimulatedRadioControl {
    private final static String LOG_TAG = "SimulatedCommands";

    private enum SimLockState {
        NONE,
        REQUIRE_PIN,
        REQUIRE_PUK,
        SIM_PERM_LOCKED
    }

    private enum SimFdnState {
        NONE,
        REQUIRE_PIN2,
        REQUIRE_PUK2,
        SIM_PERM_LOCKED
    }

    private final static SimLockState INITIAL_LOCK_STATE = SimLockState.NONE;
    private final static String DEFAULT_SIM_PIN_CODE = "1234";
    private final static String SIM_PUK_CODE = "12345678";
    private final static SimFdnState INITIAL_FDN_STATE = SimFdnState.NONE;
    private final static String DEFAULT_SIM_PIN2_CODE = "5678";
    private final static String SIM_PUK2_CODE = "87654321";

    //***** Instance Variables

    SimulatedGsmCallState simulatedCallState;
    HandlerThread mHandlerThread;
    SimLockState mSimLockedState;
    boolean mSimLockEnabled;
    int mPinUnlockAttempts;
    int mPukUnlockAttempts;
    String mPinCode;
    SimFdnState mSimFdnEnabledState;
    boolean mSimFdnEnabled;
    int mPin2UnlockAttempts;
    int mPuk2UnlockAttempts;
    int mNetworkType;
    String mPin2Code;
    boolean mSsnNotifyOn = false;

    int mPausedResponseCount;
    ArrayList<Message> mPausedResponses = new ArrayList<Message>();

    int mNextCallFailCause = CallFailCause.NORMAL_CLEARING;

    //***** Constructor

    public
    SimulatedCommands() {
        super(null);  // Don't log statistics
        mHandlerThread = new HandlerThread("SimulatedCommands");
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();

        simulatedCallState = new SimulatedGsmCallState(looper);

        setRadioState(RadioState.RADIO_OFF);
        mSimLockedState = INITIAL_LOCK_STATE;
        mSimLockEnabled = (mSimLockedState != SimLockState.NONE);
        mPinCode = DEFAULT_SIM_PIN_CODE;
        mSimFdnEnabledState = INITIAL_FDN_STATE;
        mSimFdnEnabled = (mSimFdnEnabledState != SimFdnState.NONE);
        mPin2Code = DEFAULT_SIM_PIN2_CODE;
    }

    //***** CommandsInterface implementation

    @Override
    public void getIccCardStatus(Message result) {
        unimplemented(result);
    }

    @Override
    public void supplyIccPin(String pin, Message result)  {
        if (mSimLockedState != SimLockState.REQUIRE_PIN) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
            return;
        }

        if (pin != null && pin.equals(mPinCode)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: success!");
            mPinUnlockAttempts = 0;
            mSimLockedState = SimLockState.NONE;
            mIccStatusChangedRegistrants.notifyRegistrants();

            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            mPinUnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: failed! attempt=" +
                    mPinUnlockAttempts);
            if (mPinUnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin: set state to REQUIRE_PUK");
                mSimLockedState = SimLockState.REQUIRE_PUK;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result)  {
        if (mSimLockedState != SimLockState.REQUIRE_PUK) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
            return;
        }

        if (puk != null && puk.equals(SIM_PUK_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: success!");
            mSimLockedState = SimLockState.NONE;
            mPukUnlockAttempts = 0;
            mIccStatusChangedRegistrants.notifyRegistrants();

            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            mPukUnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: failed! attempt=" +
                    mPukUnlockAttempts);
            if (mPukUnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk: set state to SIM_PERM_LOCKED");
                mSimLockedState = SimLockState.SIM_PERM_LOCKED;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void supplyIccPin2(String pin2, Message result)  {
        if (mSimFdnEnabledState != SimFdnState.REQUIRE_PIN2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: wrong state, state=" +
                    mSimFdnEnabledState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
            return;
        }

        if (pin2 != null && pin2.equals(mPin2Code)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: success!");
            mPin2UnlockAttempts = 0;
            mSimFdnEnabledState = SimFdnState.NONE;

            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            mPin2UnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: failed! attempt=" +
                    mPin2UnlockAttempts);
            if (mPin2UnlockAttempts >= 3) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPin2: set state to REQUIRE_PUK2");
                mSimFdnEnabledState = SimFdnState.REQUIRE_PUK2;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void supplyIccPuk2(String puk2, String newPin2, Message result)  {
        if (mSimFdnEnabledState != SimFdnState.REQUIRE_PUK2) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: wrong state, state=" +
                    mSimLockedState);
            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
            return;
        }

        if (puk2 != null && puk2.equals(SIM_PUK2_CODE)) {
            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: success!");
            mSimFdnEnabledState = SimFdnState.NONE;
            mPuk2UnlockAttempts = 0;

            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            mPuk2UnlockAttempts ++;

            Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: failed! attempt=" +
                    mPuk2UnlockAttempts);
            if (mPuk2UnlockAttempts >= 10) {
                Rlog.i(LOG_TAG, "[SimCmd] supplyIccPuk2: set state to SIM_PERM_LOCKED");
                mSimFdnEnabledState = SimFdnState.SIM_PERM_LOCKED;
            }

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result)  {
        if (oldPin != null && oldPin.equals(mPinCode)) {
            mPinCode = newPin;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin: pin failed!");

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result)  {
        if (oldPin2 != null && oldPin2.equals(mPin2Code)) {
            mPin2Code = newPin2;
            if (result != null) {
                AsyncResult.forMessage(result, null, null);
                result.sendToTarget();
            }

            return;
        }

        if (result != null) {
            Rlog.i(LOG_TAG, "[SimCmd] changeIccPin2: pin2 failed!");

            CommandException ex = new CommandException(
                    CommandException.Error.PASSWORD_INCORRECT);
            AsyncResult.forMessage(result, null, ex);
            result.sendToTarget();
        }
    }

    @Override
    public void
    changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        unimplemented(result);
    }

    @Override
    public void
    setSuppServiceNotifications(boolean enable, Message result) {
        resultSuccess(result, null);

        if (enable && mSsnNotifyOn) {
            Rlog.w(LOG_TAG, "Supp Service Notifications already enabled!");
        }

        mSsnNotifyOn = enable;
    }

    @Override
    public void queryFacilityLock(String facility, String pin,
                                   int serviceClass, Message result) {
        queryFacilityLockForApp(facility, pin, serviceClass, null, result);
    }

    @Override
    public void queryFacilityLockForApp(String facility, String pin, int serviceClass,
            String appId, Message result) {
        if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (result != null) {
                int[] r = new int[1];
                r[0] = (mSimLockEnabled ? 1 : 0);
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: SIM is "
                        + (r[0] == 0 ? "unlocked" : "locked"));
                AsyncResult.forMessage(result, r, null);
                result.sendToTarget();
            }
            return;
        } else if (facility != null && facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (result != null) {
                int[] r = new int[1];
                r[0] = (mSimFdnEnabled ? 1 : 0);
                Rlog.i(LOG_TAG, "[SimCmd] queryFacilityLock: FDN is "
                        + (r[0] == 0 ? "disabled" : "enabled"));
                AsyncResult.forMessage(result, r, null);
                result.sendToTarget();
            }
            return;
        }

        unimplemented(result);
    }

    @Override
    public void setFacilityLock(String facility, boolean lockEnabled, String pin, int serviceClass,
            Message result) {
        setFacilityLockForApp(facility, lockEnabled, pin, serviceClass, null, result);
    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockEnabled,
                                 String pin, int serviceClass, String appId,
                                 Message result) {
        if (facility != null &&
                facility.equals(CommandsInterface.CB_FACILITY_BA_SIM)) {
            if (pin != null && pin.equals(mPinCode)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin is valid");
                mSimLockEnabled = lockEnabled;

                if (result != null) {
                    AsyncResult.forMessage(result, null, null);
                    result.sendToTarget();
                }

                return;
            }

            if (result != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin failed!");

                CommandException ex = new CommandException(
                        CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(result, null, ex);
                result.sendToTarget();
            }

            return;
        }  else if (facility != null &&
                facility.equals(CommandsInterface.CB_FACILITY_BA_FD)) {
            if (pin != null && pin.equals(mPin2Code)) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 is valid");
                mSimFdnEnabled = lockEnabled;

                if (result != null) {
                    AsyncResult.forMessage(result, null, null);
                    result.sendToTarget();
                }

                return;
            }

            if (result != null) {
                Rlog.i(LOG_TAG, "[SimCmd] setFacilityLock: pin2 failed!");

                CommandException ex = new CommandException(
                        CommandException.Error.GENERIC_FAILURE);
                AsyncResult.forMessage(result, null, ex);
                result.sendToTarget();
            }

            return;
        }

        unimplemented(result);
    }

    @Override
    public void supplyNetworkDepersonalization(String netpin, Message result)  {
        unimplemented(result);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result contains a List of DriverCall
     *      The ar.result List is sorted by DriverCall.index
     */
    @Override
    public void getCurrentCalls (Message result) {
        if ((mState == RadioState.RADIO_ON) && !isSimLocked()) {
            //Rlog.i("GSM", "[SimCmds] getCurrentCalls");
            resultSuccess(result, simulatedCallState.getDriverCalls());
        } else {
            //Rlog.i("GSM", "[SimCmds] getCurrentCalls: RADIO_OFF or SIM not ready!");
            resultFail(result,
                new CommandException(
                    CommandException.Error.RADIO_NOT_AVAILABLE));
        }
    }

    /**
     *  @deprecated
     */
    @Deprecated
    @Override
    public void getPDPContextList(Message result) {
        getDataCallList(result);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result contains a List of DataCallResponse
     */
    @Override
    public void getDataCallList(Message result) {
        resultSuccess(result, new ArrayList<DataCallResponse>(0));
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    @Override
    public void dial (String address, int clirMode, Message result) {
        simulatedCallState.onDial(address);

        resultSuccess(result, null);
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    @Override
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        simulatedCallState.onDial(address);

        resultSuccess(result, null);
    }

    @Override
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }
    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is String containing IMSI on success
     */
    @Override
    public void getIMSIForApp(String aid, Message result) {
        resultSuccess(result, "012345678901234");
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is String containing IMEI on success
     */
    @Override
    public void getIMEI(Message result) {
        resultSuccess(result, "012345678901234");
    }

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is String containing IMEISV on success
     */
    @Override
    public void getIMEISV(Message result) {
        resultSuccess(result, "99");
    }

    /**
     * Hang up one individual connection.
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     *
     *  3GPP 22.030 6.5.5
     *  "Releases a specific active call X"
     */
    @Override
    public void hangupConnection (int gsmIndex, Message result) {
        boolean success;

        success = simulatedCallState.onChld('1', (char)('0'+gsmIndex));

        if (!success){
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultFail");
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            Rlog.i("GSM", "[SimCmd] hangupConnection: resultSuccess");
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     *  "Releases all held calls or sets User Determined User Busy (UDUB)
     *   for a waiting call."
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void hangupWaitingOrBackground (Message result) {
        boolean success;

        success = simulatedCallState.onChld('0', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Releases all active calls (if any exist) and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void hangupForegroundResumeBackground (Message result) {
        boolean success;

        success = simulatedCallState.onChld('1', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls (if any exist) on hold and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void switchWaitingOrHoldingAndActive (Message result) {
        boolean success;

        success = simulatedCallState.onChld('2', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Adds a held call to the conversation"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void conference (Message result) {
        boolean success;

        success = simulatedCallState.onChld('3', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Connects the two calls and disconnects the subscriber from both calls"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void explicitCallTransfer (Message result) {
        boolean success;

        success = simulatedCallState.onChld('4', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls on hold except call X with which
     *  communication shall be supported."
     */
    @Override
    public void separateConnection (int gsmIndex, Message result) {
        boolean success;

        char ch = (char)(gsmIndex + '0');
        success = simulatedCallState.onChld('2', ch);

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void acceptCall (Message result) {
        boolean success;

        success = simulatedCallState.onAnswer();

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     *  also known as UDUB
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void rejectCall (Message result) {
        boolean success;

        success = simulatedCallState.onChld('0', '\0');

        if (!success){
            resultFail(result, new RuntimeException("Hangup Error"));
        } else {
            resultSuccess(result, null);
        }
    }

    /**
     * cause code returned as Integer in Message.obj.response
     * Returns integer cause code defined in TS 24.008
     * Annex H or closest approximation.
     * Most significant codes:
     * - Any defined in 22.001 F.4 (for generating busy/congestion)
     * - Cause 68: ACM >= ACMMax
     */
    @Override
    public void getLastCallFailCause (Message result) {
        int[] ret = new int[1];

        ret[0] = mNextCallFailCause;
        resultSuccess(result, ret);
    }

    /**
     * @deprecated
     */
    @Deprecated
    @Override
    public void getLastPdpFailCause (Message result) {
        unimplemented(result);
    }

    @Override
    public void getLastDataCallFailCause(Message result) {
        //
        unimplemented(result);
    }

    @Override
    public void setMute (boolean enableMute, Message result) {unimplemented(result);}

    @Override
    public void getMute (Message result) {unimplemented(result);}

    /**
     * response.obj is an AsyncResult
     * response.obj.result is an int[2]
     * response.obj.result[0] is received signal strength (0-31, 99)
     * response.obj.result[1] is  bit error rate (0-7, 99)
     * as defined in TS 27.007 8.5
     */
    @Override
    public void getSignalStrength (Message result) {
        int ret[] = new int[2];

        ret[0] = 23;
        ret[1] = 0;

        resultSuccess(result, ret);
    }

     /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param result is callback message
     */
    @Override
    public void setBandMode (int bandMode, Message result) {
        resultSuccess(result, null);
    }

    /**
     * Query the list of band mode supported by RF.
     *
     * @param result is callback message
     *        ((AsyncResult)response.obj).result  is an int[] where int[0] is
     *        the size of the array and the rest of each element representing
     *        one available BM_*_BAND
     */
    @Override
    public void queryAvailableBandMode (Message result) {
        int ret[] = new int [4];

        ret[0] = 4;
        ret[1] = Phone.BM_US_BAND;
        ret[2] = Phone.BM_JPN_BAND;
        ret[3] = Phone.BM_AUS_BAND;

        resultSuccess(result, ret);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendTerminalResponse(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEnvelope(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {
        resultSuccess(response, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleCallSetupRequestFromSim(
            boolean accept, Message response) {
        resultSuccess(response, null);
    }

    /**
     * response.obj.result is an String[14]
     * See ril.h for details
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    @Override
    public void getVoiceRegistrationState (Message result) {
        String ret[] = new String[14];

        ret[0] = "5"; // registered roam
        ret[1] = null;
        ret[2] = null;
        ret[3] = null;
        ret[4] = null;
        ret[5] = null;
        ret[6] = null;
        ret[7] = null;
        ret[8] = null;
        ret[9] = null;
        ret[10] = null;
        ret[11] = null;
        ret[12] = null;
        ret[13] = null;

        resultSuccess(result, ret);
    }

    /**
     * response.obj.result is an String[4]
     * response.obj.result[0] is registration state 0-5 from TS 27.007 7.2
     * response.obj.result[1] is LAC if registered or NULL if not
     * response.obj.result[2] is CID if registered or NULL if not
     * response.obj.result[3] indicates the available radio technology, where:
     *      0 == unknown
     *      1 == GPRS only
     *      2 == EDGE
     *      3 == UMTS
     *
     * valid LAC are 0x0000 - 0xffff
     * valid CID are 0x00000000 - 0xffffffff
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" in the Android telephony system
     */
    @Override
    public void getDataRegistrationState (Message result) {
        String ret[] = new String[4];

        ret[0] = "5"; // registered roam
        ret[1] = null;
        ret[2] = null;
        ret[3] = "2";

        resultSuccess(result, ret);
    }

    /**
     * response.obj.result is a String[3]
     * response.obj.result[0] is long alpha or null if unregistered
     * response.obj.result[1] is short alpha or null if unregistered
     * response.obj.result[2] is numeric or null if unregistered
     */
    @Override
    public void getOperator(Message result) {
        String[] ret = new String[3];

        ret[0] = "El Telco Loco";
        ret[1] = "Telco Loco";
        ret[2] = "001001";

        resultSuccess(result, ret);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void sendDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void startDtmf(char c, Message result) {
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void stopDtmf(Message result) {
        resultSuccess(result, null);
    }

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the original value of result.obj
     *  ar.result is null on success and failure
     */
    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        resultSuccess(result, null);
    }

    /**
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    @Override
    public void sendSMS (String smscPDU, String pdu, Message result) {unimplemented(result);}

    /**
     * Send an SMS message, Identical to sendSMS,
     * except that more messages are expected to be sent soon
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    @Override
    public void sendSMSExpectMore (String smscPDU, String pdu, Message result) {
        unimplemented(result);
    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete message at index " + index);
        unimplemented(response);
    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {
        Rlog.d(LOG_TAG, "Delete RUIM message at index " + index);
        unimplemented(response);
    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to SIM with status " + status);
        unimplemented(response);
    }

    @Override
    public void writeSmsToRuim(int status, String pdu, Message response) {
        Rlog.d(LOG_TAG, "Write SMS to RUIM with status " + status);
        unimplemented(response);
    }

    @Override
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, Message result) {
        unimplemented(result);
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {unimplemented(result);}

    @Override
    public void setPreferredNetworkType(int networkType , Message result) {
        mNetworkType = networkType;
        resultSuccess(result, null);
    }

    @Override
    public void getPreferredNetworkType(Message result) {
        int ret[] = new int[1];

        ret[0] = mNetworkType;
        resultSuccess(result, ret);
    }

    @Override
    public void getNeighboringCids(Message result) {
        int ret[] = new int[7];

        ret[0] = 6;
        for (int i = 1; i<7; i++) {
            ret[i] = i;
        }
        resultSuccess(result, ret);
    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {
        unimplemented(response);
    }

    @Override
    public void getSmscAddress(Message result) {
        unimplemented(result);
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        unimplemented(result);
    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {
        unimplemented(result);
    }

    @Override
    public void reportStkServiceIsRunning(Message result) {
        resultSuccess(result, null);
    }

    @Override
    public void getCdmaSubscriptionSource(Message result) {
        unimplemented(result);
    }

    private boolean isSimLocked() {
        if (mSimLockedState != SimLockState.NONE) {
            return true;
        }
        return false;
    }

    @Override
    public void setRadioPower(boolean on, Message result) {
        if(on) {
            setRadioState(RadioState.RADIO_ON);
        } else {
            setRadioState(RadioState.RADIO_OFF);
        }
    }


    @Override
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message result) {
        unimplemented(result);
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu,
            Message result) {
        unimplemented(result);
    }

    @Override
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data,
            String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data,pin2, null, response);
    }

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.userObj will be a SimIoResult on success
     */
    @Override
    public void iccIOForApp (int command, int fileid, String path, int p1, int p2,
                       int p3, String data, String pin2, String aid, Message result) {
        unimplemented(result);
    }

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 1 for "CLIP is provisioned", and 0 for "CLIP is not provisioned".
     *
     * @param response is callback message
     */
    @Override
    public void queryCLIP(Message response) { unimplemented(response); }


    /**
     * response.obj will be a an int[2]
     *
     * response.obj[0] will be TS 27.007 +CLIR parameter 'n'
     *  0 presentation indicator is used according to the subscription of the CLIR service
     *  1 CLIR invocation
     *  2 CLIR suppression
     *
     * response.obj[1] will be TS 27.007 +CLIR parameter 'm'
     *  0 CLIR not provisioned
     *  1 CLIR provisioned in permanent mode
     *  2 unknown (e.g. no network, etc.)
     *  3 CLIR temporary mode presentation restricted
     *  4 CLIR temporary mode presentation allowed
     */

    @Override
    public void getCLIR(Message result) {unimplemented(result);}

    /**
     * clirMode is one of the CLIR_* constants above
     *
     * response.obj is null
     */

    @Override
    public void setCLIR(int clirMode, Message result) {unimplemented(result);}

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 0 for disabled, 1 for enabled.
     *
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {
        unimplemented(response);
    }

    /**
     * @param enable is true to enable, false to disable
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    @Override
    public void setCallWaiting(boolean enable, int serviceClass,
            Message response) {
        unimplemented(response);
    }

    /**
     * @param action is one of CF_ACTION_*
     * @param cfReason is one of CF_REASON_*
     * @param serviceClass is a sum of SERVICE_CLASSS_*
     */
    @Override
    public void setCallForward(int action, int cfReason, int serviceClass,
            String number, int timeSeconds, Message result) {unimplemented(result);}

    /**
     * cfReason is one of CF_REASON_*
     *
     * ((AsyncResult)response.obj).result will be an array of
     * CallForwardInfo's
     *
     * An array of length 0 means "disabled for all codes"
     */
    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message result) {unimplemented(result);}

    @Override
    public void setNetworkSelectionModeAutomatic(Message result) {unimplemented(result);}
    @Override
    public void exitEmergencyCallbackMode(Message result) {unimplemented(result);}
    @Override
    public void setNetworkSelectionModeManual(
            String operatorNumeric, Message result) {unimplemented(result);}

    /**
     * Queries whether the current network selection mode is automatic
     * or manual
     *
     * ((AsyncResult)response.obj).result  is an int[] with element [0] being
     * a 0 for automatic selection and a 1 for manual selection
     */

    @Override
    public void getNetworkSelectionMode(Message result) {
        int ret[] = new int[1];

        ret[0] = 0;
        resultSuccess(result, ret);
    }

    /**
     * Queries the currently available networks
     *
     * ((AsyncResult)response.obj).result  is a List of NetworkInfo objects
     */
    @Override
    public void getAvailableNetworks(Message result) {unimplemented(result);}

    @Override
    public void getBasebandVersion (Message result) {
        resultSuccess(result, "SimulatedCommands");
    }

    /**
     * Simulates an Stk Call Control Alpha message
     * @param alphaString Alpha string to send.
     */
    public void triggerIncomingStkCcAlpha(String alphaString) {
        if (mCatCcAlphaRegistrant != null) {
            mCatCcAlphaRegistrant.notifyResult(alphaString);
        }
    }

    public void sendStkCcAplha(String alphaString) {
        triggerIncomingStkCcAlpha(alphaString);
    }

    /**
     * Simulates an incoming USSD message
     * @param statusCode  Status code string. See <code>setOnUSSD</code>
     * in CommandsInterface.java
     * @param message Message text to send or null if none
     */
    @Override
    public void triggerIncomingUssd(String statusCode, String message) {
        if (mUSSDRegistrant != null) {
            String[] result = {statusCode, message};
            mUSSDRegistrant.notifyResult(result);
        }
    }


    @Override
    public void sendUSSD (String ussdString, Message result) {

        // We simulate this particular sequence
        if (ussdString.equals("#646#")) {
            resultSuccess(result, null);

            // 0 == USSD-Notify
            triggerIncomingUssd("0", "You have NNN minutes remaining.");
        } else {
            resultSuccess(result, null);

            triggerIncomingUssd("0", "All Done");
        }
    }

    // inherited javadoc suffices
    @Override
    public void cancelPendingUssd (Message response) {
        resultSuccess(response, null);
    }


    @Override
    public void resetRadio(Message result) {
        unimplemented(result);
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        // Just echo back data
        if (response != null) {
            AsyncResult.forMessage(response).result = data;
            response.sendToTarget();
        }
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        // Just echo back data
        if (response != null) {
            AsyncResult.forMessage(response).result = strings;
            response.sendToTarget();
        }
    }

    //***** SimulatedRadioControl


    /** Start the simulated phone ringing */
    @Override
    public void
    triggerRing(String number) {
        simulatedCallState.triggerRing(number);
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void
    progressConnectingCallState() {
        simulatedCallState.progressConnectingCallState();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** If a call is DIALING or ALERTING, progress it all the way to ACTIVE */
    @Override
    public void
    progressConnectingToActive() {
        simulatedCallState.progressConnectingToActive();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** automatically progress mobile originated calls to ACTIVE.
     *  default to true
     */
    @Override
    public void
    setAutoProgressConnectingCall(boolean b) {
        simulatedCallState.setAutoProgressConnectingCall(b);
    }

    @Override
    public void
    setNextDialFailImmediately(boolean b) {
        simulatedCallState.setNextDialFailImmediately(b);
    }

    @Override
    public void
    setNextCallFailCause(int gsmCause) {
        mNextCallFailCause = gsmCause;
    }

    @Override
    public void
    triggerHangupForeground() {
        simulatedCallState.triggerHangupForeground();
        mCallStateRegistrants.notifyRegistrants();
    }

    /** hangup holding calls */
    @Override
    public void
    triggerHangupBackground() {
        simulatedCallState.triggerHangupBackground();
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void triggerSsn(int type, int code) {
        SuppServiceNotification not = new SuppServiceNotification();
        not.notificationType = type;
        not.code = code;
        mSsnRegistrant.notifyRegistrant(new AsyncResult(null, not, null));
    }

    @Override
    public void
    shutdown() {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
        Looper looper = mHandlerThread.getLooper();
        if (looper != null) {
            looper.quit();
        }
    }

    /** hangup all */

    @Override
    public void
    triggerHangupAll() {
        simulatedCallState.triggerHangupAll();
        mCallStateRegistrants.notifyRegistrants();
    }

    @Override
    public void
    triggerIncomingSMS(String message) {
        //TODO
    }

    @Override
    public void
    pauseResponses() {
        mPausedResponseCount++;
    }

    @Override
    public void
    resumeResponses() {
        mPausedResponseCount--;

        if (mPausedResponseCount == 0) {
            for (int i = 0, s = mPausedResponses.size(); i < s ; i++) {
                mPausedResponses.get(i).sendToTarget();
            }
            mPausedResponses.clear();
        } else {
            Rlog.e("GSM", "SimulatedCommands.resumeResponses < 0");
        }
    }

    //***** Private Methods

    private void unimplemented(Message result) {
        if (result != null) {
            AsyncResult.forMessage(result).exception
                = new RuntimeException("Unimplemented");

            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultSuccess(Message result, Object ret) {
        if (result != null) {
            AsyncResult.forMessage(result).result = ret;
            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    private void resultFail(Message result, Throwable tr) {
        if (result != null) {
            AsyncResult.forMessage(result).exception = tr;
            if (mPausedResponseCount > 0) {
                mPausedResponses.add(result);
            } else {
                result.sendToTarget();
            }
        }
    }

    // ***** Methods for CDMA support
    @Override
    public void
    getDeviceIdentity(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override
    public void
    getCDMASubscription(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override
    public void
    setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    @Override
    public void
    setPhoneType(int phoneType) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(result);
    }

    /**
     *  Set the TTY mode
     *
     * @param ttyMode is one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    @Override
    public void setTTYMode(int ttyMode, Message response) {
        Rlog.w(LOG_TAG, "Not implemented in SimulatedCommands");
        unimplemented(response);
    }

    /**
     *  Query the TTY mode
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * tty mode:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    @Override
    public void queryTTYMode(Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
        unimplemented(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendCdmaSms(byte[] pdu, Message response){
       Rlog.w(LOG_TAG, "CDMA not implemented in SimulatedCommands");
    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);

    }

    @Override
    public void getCdmaBroadcastConfig(Message response) {
        unimplemented(response);

    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        unimplemented(response);
    }

    public void forceDataDormancy(Message response) {
        unimplemented(response);
    }


    @Override
    public void setGsmBroadcastActivation(boolean activate, Message response) {
        unimplemented(response);
    }


    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        unimplemented(response);
    }

    @Override
    public void getGsmBroadcastConfig(Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPinForApp(String pin, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message response) {
        unimplemented(response);
    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr,
            Message response) {
        unimplemented(response);
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        unimplemented(response);
    }

    @Override
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        unimplemented(response);
    }

    @Override
    public void getVoiceRadioTechnology(Message response) {
        unimplemented(response);
    }

    @Override
    public void getCellInfoList(Message response) {
        unimplemented(response);
    }

    @Override
    public void setCellInfoListRate(int rateInMillis, Message response) {
        unimplemented(response);
    }

    @Override
    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Message result) {
    }

    @Override
    public void setDataProfile(DataProfile[] dps, Message result) {
    }

    @Override
    public void getImsRegistrationState(Message response) {
        unimplemented(response);
    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef,
            Message response){
        unimplemented(response);
    }

    @Override
    public void sendImsGsmSms(String smscPDU, String pdu,
            int retry, int messageRef, Message response){
        unimplemented(response);
    }

    @Override
    public void iccOpenLogicalChannel(String AID, Message response) {
        unimplemented(response);
    }

    @Override
    public void iccCloseLogicalChannel(int channel, Message response) {
        unimplemented(response);
    }

    @Override
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
            int p1, int p2, int p3, String data, Message response) {
        unimplemented(response);
    }

    @Override
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
            int p3, String data, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvReadItem(int itemID, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        unimplemented(response);
    }

    @Override
    public void nvResetConfig(int resetType, Message response) {
        unimplemented(response);
    }

    @Override
    public void getHardwareConfig(Message result) {
        unimplemented(result);
    }

    @Override
    public void requestShutdown(Message result) {
        setRadioState(RadioState.RADIO_UNAVAILABLE);
    }

    @Override
    public void startLceService(int report_interval_ms, boolean pullMode, Message result) {
        unimplemented(result);
    }

    @Override
    public void stopLceService(Message result) {
        unimplemented(result);
    }

    @Override
    public void pullLceData(Message result) {
        unimplemented(result);
    }

    @Override
    public void getModemActivityInfo(Message result) {
        unimplemented(result);
    }

    /* SPRD: add for bug479433 @{ */
    @Override
    public void dialVP(String address, String sub_address, int clirMode, Message result) {

    }

    @Override
    public void codecVP(int type, Bundle param, Message result) {

    }
    /* @} */
}
