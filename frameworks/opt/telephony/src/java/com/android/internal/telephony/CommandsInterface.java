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

package com.android.internal.telephony;

import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.RadioCapability;
import com.android.internal.telephony.uicc.IccCardStatus;

import android.os.Bundle;
import android.os.Message;
import android.os.Handler;


/**
 * {@hide}
 */
public interface CommandsInterface {
    enum RadioState {
        RADIO_OFF,         /* Radio explicitly powered off (eg CFUN=0) */
        RADIO_UNAVAILABLE, /* Radio unavailable (eg, resetting or not booted) */
        RADIO_ON;          /* Radio is on */

        public boolean isOn() /* and available...*/ {
            return this == RADIO_ON;
        }

        public boolean isAvailable() {
            return this != RADIO_UNAVAILABLE;
        }
    }

    //***** Constants

    // Used as parameter to dial() and setCLIR() below
    static final int CLIR_DEFAULT = 0;      // "use subscription default value"
    static final int CLIR_INVOCATION = 1;   // (restrict CLI presentation)
    static final int CLIR_SUPPRESSION = 2;  // (allow CLI presentation)


    // Used as parameters for call forward methods below
    static final int CF_ACTION_DISABLE          = 0;
    static final int CF_ACTION_ENABLE           = 1;
//  static final int CF_ACTION_UNUSED           = 2;
    static final int CF_ACTION_REGISTRATION     = 3;
    static final int CF_ACTION_ERASURE          = 4;

    static final int CF_REASON_UNCONDITIONAL    = 0;
    static final int CF_REASON_BUSY             = 1;
    static final int CF_REASON_NO_REPLY         = 2;
    static final int CF_REASON_NOT_REACHABLE    = 3;
    static final int CF_REASON_ALL              = 4;
    static final int CF_REASON_ALL_CONDITIONAL  = 5;

    // Used for call barring methods below
    static final String CB_FACILITY_BAOC         = "AO";
    static final String CB_FACILITY_BAOIC        = "OI";
    static final String CB_FACILITY_BAOICxH      = "OX";
    static final String CB_FACILITY_BAIC         = "AI";
    static final String CB_FACILITY_BAICr        = "IR";
    static final String CB_FACILITY_BA_ALL       = "AB";
    static final String CB_FACILITY_BA_MO        = "AG";
    static final String CB_FACILITY_BA_MT        = "AC";
    static final String CB_FACILITY_BA_SIM       = "SC";
    static final String CB_FACILITY_BA_FD        = "FD";

    /* SPRD: add for sim lock @{ */
    static final String CB_FACILITY_BA_PS = "PS";
    static final String CB_FACILITY_BA_PN = "PN";
    static final String CB_FACILITY_BA_PU = "PU";
    static final String CB_FACILITY_BA_PP = "PP";
    static final String CB_FACILITY_BA_PC = "PC";
    static final String CB_FACILITY_BA_PS_PUK = "PSP";
    static final String CB_FACILITY_BA_PN_PUK = "PNP";
    static final String CB_FACILITY_BA_PU_PUK = "PUP";
    static final String CB_FACILITY_BA_PP_PUK = "PPP";
    static final String CB_FACILITY_BA_PC_PUK = "PCP";
    /* @} */


    // Used for various supp services apis
    // See 27.007 +CCFC or +CLCK
    static final int SERVICE_CLASS_NONE     = 0; // no user input
    static final int SERVICE_CLASS_VOICE    = (1 << 0);
    static final int SERVICE_CLASS_DATA     = (1 << 1); //synonym for 16+32+64+128
    static final int SERVICE_CLASS_FAX      = (1 << 2);
    static final int SERVICE_CLASS_SMS      = (1 << 3);
    static final int SERVICE_CLASS_DATA_SYNC = (1 << 4);
    static final int SERVICE_CLASS_DATA_ASYNC = (1 << 5);
    static final int SERVICE_CLASS_PACKET   = (1 << 6);
    static final int SERVICE_CLASS_PAD      = (1 << 7);
    static final int SERVICE_CLASS_MAX      = (1 << 7); // Max SERVICE_CLASS value

    // Numeric representation of string values returned
    // by messages sent to setOnUSSD handler
    static final int USSD_MODE_NOTIFY        = 0;
    static final int USSD_MODE_REQUEST       = 1;
    static final int USSD_MODE_NW_RELEASE    = 2;
    static final int USSD_MODE_LOCAL_CLIENT  = 3;
    static final int USSD_MODE_NOT_SUPPORTED = 4;
    static final int USSD_MODE_NW_TIMEOUT    = 5;

    // GSM SMS fail cause for acknowledgeLastIncomingSMS. From TS 23.040, 9.2.3.22.
    static final int GSM_SMS_FAIL_CAUSE_MEMORY_CAPACITY_EXCEEDED    = 0xD3;
    static final int GSM_SMS_FAIL_CAUSE_USIM_APP_TOOLKIT_BUSY       = 0xD4;
    static final int GSM_SMS_FAIL_CAUSE_USIM_DATA_DOWNLOAD_ERROR    = 0xD5;
    static final int GSM_SMS_FAIL_CAUSE_UNSPECIFIED_ERROR           = 0xFF;

    // CDMA SMS fail cause for acknowledgeLastIncomingCdmaSms.  From TS N.S0005, 6.5.2.125.
    static final int CDMA_SMS_FAIL_CAUSE_INVALID_TELESERVICE_ID     = 4;
    static final int CDMA_SMS_FAIL_CAUSE_RESOURCE_SHORTAGE          = 35;
    static final int CDMA_SMS_FAIL_CAUSE_OTHER_TERMINAL_PROBLEM     = 39;
    static final int CDMA_SMS_FAIL_CAUSE_ENCODING_PROBLEM           = 96;

    /* SPRD: function call barring support @{ */
    static final int CB_REASON_AO = 0;
    static final int CB_REASON_OI = 1;
    static final int CB_REASON_OX = 2;
    static final int CB_REASON_AI = 3;
    static final int CB_REASON_IR = 4;
    static final int CB_REASON_AB = 5;

    static final int CB_ACTION_DISABLE = 0;
    static final int CB_ACTION_ENABLE = 1;
    /* @} */

    //***** Methods
    RadioState getRadioState();

    /**
     * response.obj.result is an int[2]
     *
     * response.obj.result[0] is IMS registration state
     *                        0 - Not registered
     *                        1 - Registered
     * response.obj.result[1] is of type RILConstants.GSM_PHONE or
     *                                    RILConstants.CDMA_PHONE
     */
    void getImsRegistrationState(Message result);

    /**
     * Fires on any RadioState transition
     * Always fires immediately as well
     *
     * do not attempt to calculate transitions by storing getRadioState() values
     * on previous invocations of this notification. Instead, use the other
     * registration methods
     */
    void registerForRadioStateChanged(Handler h, int what, Object obj);
    void unregisterForRadioStateChanged(Handler h);

    void registerForVoiceRadioTechChanged(Handler h, int what, Object obj);
    void unregisterForVoiceRadioTechChanged(Handler h);
    void registerForImsNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForImsNetworkStateChanged(Handler h);

    /**
     * Fires on any transition into RadioState.isOn()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForOn(Handler h, int what, Object obj);
    void unregisterForOn(Handler h);

    /**
     * Fires on any transition out of RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForAvailable(Handler h, int what, Object obj);
    void unregisterForAvailable(Handler h);

    /**
     * Fires on any transition into !RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForNotAvailable(Handler h, int what, Object obj);
    void unregisterForNotAvailable(Handler h);

    /**
     * Fires on any transition into RADIO_OFF or !RadioState.isAvailable()
     * Fires immediately if currently in that state
     * In general, actions should be idempotent. State may change
     * before event is received.
     */
    void registerForOffOrNotAvailable(Handler h, int what, Object obj);
    void unregisterForOffOrNotAvailable(Handler h);

    /**
     * Fires on any change in ICC status
     */
    void registerForIccStatusChanged(Handler h, int what, Object obj);
    void unregisterForIccStatusChanged(Handler h);

    void registerForCallStateChanged(Handler h, int what, Object obj);
    void unregisterForCallStateChanged(Handler h);
    void registerForVoiceNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForVoiceNetworkStateChanged(Handler h);
    void registerForDataNetworkStateChanged(Handler h, int what, Object obj);
    void unregisterForDataNetworkStateChanged(Handler h);

    /** InCall voice privacy notifications */
    void registerForInCallVoicePrivacyOn(Handler h, int what, Object obj);
    void unregisterForInCallVoicePrivacyOn(Handler h);
    void registerForInCallVoicePrivacyOff(Handler h, int what, Object obj);
    void unregisterForInCallVoicePrivacyOff(Handler h);

    /** Single Radio Voice Call State progress notifications */
    void registerForSrvccStateChanged(Handler h, int what, Object obj);
    void unregisterForSrvccStateChanged(Handler h);

    /**
     * Handlers for subscription status change indications.
     *
     * @param h Handler for subscription status change messages.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSubscriptionStatusChanged(Handler h, int what, Object obj);
    void unregisterForSubscriptionStatusChanged(Handler h);

    /**
     * fires on any change in hardware configuration.
     */
    void registerForHardwareConfigChanged(Handler h, int what, Object obj);
    void unregisterForHardwareConfigChanged(Handler h);

    /**
     * unlike the register* methods, there's only one new 3GPP format SMS handler.
     * if you need to unregister, you should also tell the radio to stop
     * sending SMS's to you (via AT+CNMI)
     *
     * AsyncResult.result is a String containing the SMS PDU
     */
    void setOnNewGsmSms(Handler h, int what, Object obj);
    void unSetOnNewGsmSms(Handler h);

    /**
     * unlike the register* methods, there's only one new 3GPP2 format SMS handler.
     * if you need to unregister, you should also tell the radio to stop
     * sending SMS's to you (via AT+CNMI)
     *
     * AsyncResult.result is a String containing the SMS PDU
     */
    void setOnNewCdmaSms(Handler h, int what, Object obj);
    void unSetOnNewCdmaSms(Handler h);

    /**
     * Set the handler for SMS Cell Broadcast messages.
     *
     * AsyncResult.result is a byte array containing the SMS-CB PDU
     */
    void setOnNewGsmBroadcastSms(Handler h, int what, Object obj);
    void unSetOnNewGsmBroadcastSms(Handler h);

    /**
     * Register for NEW_SMS_ON_SIM unsolicited message
     *
     * AsyncResult.result is an int array containing the index of new SMS
     */
    void setOnSmsOnSim(Handler h, int what, Object obj);
    void unSetOnSmsOnSim(Handler h);

    /**
     * Register for NEW_SMS_STATUS_REPORT unsolicited message
     *
     * AsyncResult.result is a String containing the status report PDU
     */
    void setOnSmsStatus(Handler h, int what, Object obj);
    void unSetOnSmsStatus(Handler h);

    /**
     * unlike the register* methods, there's only one NITZ time handler
     *
     * AsyncResult.result is an Object[]
     * ((Object[])AsyncResult.result)[0] is a String containing the NITZ time string
     * ((Object[])AsyncResult.result)[1] is a Long containing the milliseconds since boot as
     *                                   returned by elapsedRealtime() when this NITZ time
     *                                   was posted.
     *
     * Please note that the delivery of this message may be delayed several
     * seconds on system startup
     */
    void setOnNITZTime(Handler h, int what, Object obj);
    void unSetOnNITZTime(Handler h);

    /**
     * unlike the register* methods, there's only one USSD notify handler
     *
     * Represents the arrival of a USSD "notify" message, which may
     * or may not have been triggered by a previous USSD send
     *
     * AsyncResult.result is a String[]
     * ((String[])(AsyncResult.result))[0] contains status code
     *      "0"   USSD-Notify -- text in ((const char **)data)[1]
     *      "1"   USSD-Request -- text in ((const char **)data)[1]
     *      "2"   Session terminated by network
     *      "3"   other local client (eg, SIM Toolkit) has responded
     *      "4"   Operation not supported
     *      "5"   Network timeout
     *
     * ((String[])(AsyncResult.result))[1] contains the USSD message
     * The numeric representations of these are in USSD_MODE_*
     */

    void setOnUSSD(Handler h, int what, Object obj);
    void unSetOnUSSD(Handler h);

    /**
     * unlike the register* methods, there's only one signal strength handler
     * AsyncResult.result is an int[2]
     * response.obj.result[0] is received signal strength (0-31, 99)
     * response.obj.result[1] is  bit error rate (0-7, 99)
     * as defined in TS 27.007 8.5
     */

    void setOnSignalStrengthUpdate(Handler h, int what, Object obj);
    void unSetOnSignalStrengthUpdate(Handler h);

    /**
     * Sets the handler for SIM/RUIM SMS storage full unsolicited message.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnIccSmsFull(Handler h, int what, Object obj);
    void unSetOnIccSmsFull(Handler h);

    /**
     * Sets the handler for SIM Refresh notifications.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForIccRefresh(Handler h, int what, Object obj);
    void unregisterForIccRefresh(Handler h);

    void setOnIccRefresh(Handler h, int what, Object obj);
    void unsetOnIccRefresh(Handler h);

    /**
     * Sets the handler for RING notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCallRing(Handler h, int what, Object obj);
    void unSetOnCallRing(Handler h);

    /**
     * Sets the handler for RESTRICTED_STATE changed notification,
     * eg, for Domain Specific Access Control
     * unlike the register* methods, there's only one signal strength handler
     *
     * AsyncResult.result is an int[1]
     * response.obj.result[0] is a bitmask of RIL_RESTRICTED_STATE_* values
     */

    void setOnRestrictedStateChanged(Handler h, int what, Object obj);
    void unSetOnRestrictedStateChanged(Handler h);

    /**
     * Sets the handler for Supplementary Service Notifications.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnSuppServiceNotification(Handler h, int what, Object obj);
    void unSetOnSuppServiceNotification(Handler h);

    /**
     * Sets the handler for Session End Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatSessionEnd(Handler h, int what, Object obj);
    void unSetOnCatSessionEnd(Handler h);

    /**
     * Sets the handler for Proactive Commands for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatProactiveCmd(Handler h, int what, Object obj);
    void unSetOnCatProactiveCmd(Handler h);

    /**
     * Sets the handler for Event Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatEvent(Handler h, int what, Object obj);
    void unSetOnCatEvent(Handler h);

    /**
     * Sets the handler for Call Set Up Notifications for CAT.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatCallSetUp(Handler h, int what, Object obj);
    void unSetOnCatCallSetUp(Handler h);

    /**
     * Enables/disbables supplementary service related notifications from
     * the network.
     *
     * @param enable true to enable notifications, false to disable.
     * @param result Message to be posted when command completes.
     */
    void setSuppServiceNotifications(boolean enable, Message result);
    //void unSetSuppServiceNotifications(Handler h);

    /**
     * Sets the handler for Alpha Notification during STK Call Control.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnCatCcAlphaNotify(Handler h, int what, Object obj);
    void unSetOnCatCcAlphaNotify(Handler h);

    /**
     * Sets the handler for notifying Suplementary Services (SS)
     * Data during STK Call Control.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void setOnSs(Handler h, int what, Object obj);
    void unSetOnSs(Handler h);

    /**
     * Sets the handler for Event Notifications for CDMA Display Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForDisplayInfo(Handler h, int what, Object obj);
    void unregisterForDisplayInfo(Handler h);

    /**
     * Sets the handler for Event Notifications for CallWaiting Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForCallWaitingInfo(Handler h, int what, Object obj);
    void unregisterForCallWaitingInfo(Handler h);

    /**
     * Sets the handler for Event Notifications for Signal Info.
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForSignalInfo(Handler h, int what, Object obj);
    void unregisterForSignalInfo(Handler h);

    /**
     * Registers the handler for CDMA number information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForNumberInfo(Handler h, int what, Object obj);
    void unregisterForNumberInfo(Handler h);

    /**
     * Registers the handler for CDMA redirected number Information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForRedirectedNumberInfo(Handler h, int what, Object obj);
    void unregisterForRedirectedNumberInfo(Handler h);

    /**
     * Registers the handler for CDMA line control information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForLineControlInfo(Handler h, int what, Object obj);
    void unregisterForLineControlInfo(Handler h);

    /**
     * Registers the handler for CDMA T53 CLIR information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerFoT53ClirlInfo(Handler h, int what, Object obj);
    void unregisterForT53ClirInfo(Handler h);

    /**
     * Registers the handler for CDMA T53 audio control information record
     * Unlike the register* methods, there's only one notification handler
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForT53AudioControlInfo(Handler h, int what, Object obj);
    void unregisterForT53AudioControlInfo(Handler h);

    /**
     * Fires on if Modem enters Emergency Callback mode
     */
    void setEmergencyCallbackMode(Handler h, int what, Object obj);

     /**
      * Fires on any CDMA OTA provision status change
      */
     void registerForCdmaOtaProvision(Handler h,int what, Object obj);
     void unregisterForCdmaOtaProvision(Handler h);

     /**
      * Registers the handler when out-band ringback tone is needed.<p>
      *
      *  Messages received from this:
      *  Message.obj will be an AsyncResult
      *  AsyncResult.userObj = obj
      *  AsyncResult.result = boolean. <p>
      */
     void registerForRingbackTone(Handler h, int what, Object obj);
     void unregisterForRingbackTone(Handler h);

     /**
      * Registers the handler when mute/unmute need to be resent to get
      * uplink audio during a call.<p>
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForResendIncallMute(Handler h, int what, Object obj);
     void unregisterForResendIncallMute(Handler h);

     /**
      * Registers the handler for when Cdma subscription changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForCdmaSubscriptionChanged(Handler h, int what, Object obj);
     void unregisterForCdmaSubscriptionChanged(Handler h);

     /**
      * Registers the handler for when Cdma prl changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForCdmaPrlChanged(Handler h, int what, Object obj);
     void unregisterForCdmaPrlChanged(Handler h);

     /**
      * Registers the handler for when Cdma prl changed events
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      *
      */
     void registerForExitEmergencyCallbackMode(Handler h, int what, Object obj);
     void unregisterForExitEmergencyCallbackMode(Handler h);

     /**
      * Registers the handler for RIL_UNSOL_RIL_CONNECT events.
      *
      * When ril connects or disconnects a message is sent to the registrant
      * which contains an AsyncResult, ar, in msg.obj. The ar.result is an
      * Integer which is the version of the ril or -1 if the ril disconnected.
      *
      * @param h Handler for notification message.
      * @param what User-defined message code.
      * @param obj User object.
      */
     void registerForRilConnected(Handler h, int what, Object obj);
     void unregisterForRilConnected(Handler h);

    /**
     * Supply the ICC PIN to the ICC card
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin(String pin, Message result);

    /**
     * Supply the PIN for the app with this AID on the ICC card
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPinForApp(String pin, String aid, Message result);

    /**
     * Supply the ICC PUK and newPin to the ICC card
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk(String puk, String newPin, Message result);

    /**
     * Supply the PUK, new pin for the app with this AID on the ICC card
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPukForApp(String puk, String newPin, String aid, Message result);

    /**
     * Supply the ICC PIN2 to the ICC card
     * Only called following operation where ICC_PIN2 was
     * returned as a a failure from a previous operation
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin2(String pin2, Message result);

    /**
     * Supply the PIN2 for the app with this AID on the ICC card
     * Only called following operation where ICC_PIN2 was
     * returned as a a failure from a previous operation
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC will be PUK locked.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPin2ForApp(String pin2, String aid, Message result);

    /**
     * Supply the SIM PUK2 to the SIM card
     * Only called following operation where SIM_PUK2 was
     * returned as a a failure from a previous operation
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk2(String puk2, String newPin2, Message result);

    /**
     * Supply the PUK2, newPin2 for the app with this AID on the ICC card
     * Only called following operation where SIM_PUK2 was
     * returned as a a failure from a previous operation
     *
     *  AID (Application ID), See ETSI 102.221 8.1 and 101.220 4
     *
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  This exception is CommandException with an error of PASSWORD_INCORRECT
     *  if the password is incorrect
     *
     *  ar.result is an optional array of integers where the first entry
     *  is the number of attempts remaining before the ICC is permanently disabled.
     *
     * ar.exception and ar.result are null on success
     */

    void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result);

    // TODO: Add java doc and indicate that msg.arg1 contains the number of attempts remaining.
    void changeIccPin(String oldPin, String newPin, Message result);
    void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result);
    void changeIccPin2(String oldPin2, String newPin2, Message result);
    void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result);

    void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result);

    void supplyNetworkDepersonalization(String netpin, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DriverCall
     *      The ar.result List is sorted by DriverCall.index
     */
    void getCurrentCalls (Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DataCallResponse
     *  @deprecated Do not use.
     */
    @Deprecated
    void getPDPContextList(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of DataCallResponse
     */
    void getDataCallList(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    void dial (String address, int clirMode, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     * CLIR_DEFAULT     == on "use subscription default value"
     * CLIR_SUPPRESSION == on "CLIR suppression" (allow CLI presentation)
     * CLIR_INVOCATION  == on "CLIR invocation" (restrict CLI presentation)
     */
    void dial(String address, int clirMode, UUSInfo uusInfo, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMSI on success
     */
    void getIMSI(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMSI on success
     */
    void getIMSIForApp(String aid, Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMEI on success
     */
    void getIMEI(Message result);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is String containing IMEISV on success
     */
    void getIMEISV(Message result);

    /**
     * Hang up one individual connection.
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     *
     *  3GPP 22.030 6.5.5
     *  "Releases a specific active call X"
     */
    void hangupConnection (int gsmIndex, Message result);

    /**
     * 3GPP 22.030 6.5.5
     *  "Releases all held calls or sets User Determined User Busy (UDUB)
     *   for a waiting call."
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void hangupWaitingOrBackground (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Releases all active calls (if any exist) and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void hangupForegroundResumeBackground (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls (if any exist) on hold and accepts
     *  the other (held or waiting) call."
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void switchWaitingOrHoldingAndActive (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Adds a held call to the conversation"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void conference (Message result);

    /**
     * Set preferred Voice Privacy (VP).
     *
     * @param enable true is enhanced and false is normal VP
     * @param result is a callback message
     */
    void setPreferredVoicePrivacy(boolean enable, Message result);

    /**
     * Get currently set preferred Voice Privacy (VP) mode.
     *
     * @param result is a callback message
     */
    void getPreferredVoicePrivacy(Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Places all active calls on hold except call X with which
     *  communication shall be supported."
     */
    void separateConnection (int gsmIndex, Message result);

    /**
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void acceptCall (Message result);

    /**
     *  also known as UDUB
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void rejectCall (Message result);

    /**
     * 3GPP 22.030 6.5.5
     * "Connects the two calls and disconnects the subscriber from both calls"
     *
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void explicitCallTransfer (Message result);

    /**
     * cause code returned as int[0] in Message.obj.response
     * Returns integer cause code defined in TS 24.008
     * Annex H or closest approximation.
     * Most significant codes:
     * - Any defined in 22.001 F.4 (for generating busy/congestion)
     * - Cause 68: ACM >= ACMMax
     */
    void getLastCallFailCause (Message result);


    /**
     * Reason for last PDP context deactivate or failure to activate
     * cause code returned as int[0] in Message.obj.response
     * returns an integer cause code defined in TS 24.008
     * section 6.1.3.1.3 or close approximation
     * @deprecated Do not use.
     */
    @Deprecated
    void getLastPdpFailCause (Message result);

    /**
     * The preferred new alternative to getLastPdpFailCause
     * that is also CDMA-compatible.
     */
    void getLastDataCallFailCause (Message result);

    void setMute (boolean enableMute, Message response);

    void getMute (Message response);

    /**
     * response.obj is an AsyncResult
     * response.obj.result is an int[2]
     * response.obj.result[0] is received signal strength (0-31, 99)
     * response.obj.result[1] is  bit error rate (0-7, 99)
     * as defined in TS 27.007 8.5
     */
    void getSignalStrength (Message response);


    /**
     * response.obj.result is an int[3]
     * response.obj.result[0] is registration state 0-5 from TS 27.007 7.2
     * response.obj.result[1] is LAC if registered or -1 if not
     * response.obj.result[2] is CID if registered or -1 if not
     * valid LAC and CIDs are 0x0000 - 0xffff
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    void getVoiceRegistrationState (Message response);

    /**
     * response.obj.result is an int[3]
     * response.obj.result[0] is registration state 0-5 from TS 27.007 7.2
     * response.obj.result[1] is LAC if registered or -1 if not
     * response.obj.result[2] is CID if registered or -1 if not
     * valid LAC and CIDs are 0x0000 - 0xffff
     *
     * Please note that registration state 4 ("unknown") is treated
     * as "out of service" above
     */
    void getDataRegistrationState (Message response);

    /**
     * response.obj.result is a String[3]
     * response.obj.result[0] is long alpha or null if unregistered
     * response.obj.result[1] is short alpha or null if unregistered
     * response.obj.result[2] is numeric or null if unregistered
     */
    void getOperator(Message response);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void sendDtmf(char c, Message result);


    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void startDtmf(char c, Message result);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void stopDtmf(Message result);

    /**
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result is null on success and failure
     */
    void sendBurstDtmf(String dtmfString, int on, int off, Message result);

    /**
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    void sendSMS (String smscPDU, String pdu, Message response);

    /**
     * Send an SMS message, Identical to sendSMS,
     * except that more messages are expected to be sent soon
     * smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     */
    void sendSMSExpectMore (String smscPDU, String pdu, Message response);

    /**
     * @param pdu is CDMA-SMS in internal pseudo-PDU format
     * @param response sent when operation completes
     */
    void sendCdmaSms(byte[] pdu, Message response);

    /**
     * send SMS over IMS with 3GPP/GSM SMS format
     * @param smscPDU is smsc address in PDU form GSM BCD format prefixed
     *      by a length byte (as expected by TS 27.005) or NULL for default SMSC
     * @param pdu is SMS in PDU format as an ASCII hex string
     *      less the SMSC address
     * @param retry indicates if this is a retry; 0 == not retry, nonzero = retry
     * @param messageRef valid field if retry is set to nonzero.
     *        Contains messageRef from RIL_SMS_Response corresponding to failed MO SMS
     * @param response sent when operation completes
     */
    void sendImsGsmSms (String smscPDU, String pdu, int retry, int messageRef,
            Message response);

    /**
     * send SMS over IMS with 3GPP2/CDMA SMS format
     * @param pdu is CDMA-SMS in internal pseudo-PDU format
     * @param response sent when operation completes
     * @param retry indicates if this is a retry; 0 == not retry, nonzero = retry
     * @param messageRef valid field if retry is set to nonzero.
     *        Contains messageRef from RIL_SMS_Response corresponding to failed MO SMS
     * @param response sent when operation completes
     */
    void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response);

    /**
     * Deletes the specified SMS record from SIM memory (EF_SMS).
     *
     * @param index index of the SMS record to delete
     * @param response sent when operation completes
     */
    void deleteSmsOnSim(int index, Message response);

    /**
     * Deletes the specified SMS record from RUIM memory (EF_SMS in DF_CDMA).
     *
     * @param index index of the SMS record to delete
     * @param response sent when operation completes
     */
    void deleteSmsOnRuim(int index, Message response);

    /**
     * Writes an SMS message to SIM memory (EF_SMS).
     *
     * @param status status of message on SIM.  One of:
     *                  SmsManger.STATUS_ON_ICC_READ
     *                  SmsManger.STATUS_ON_ICC_UNREAD
     *                  SmsManger.STATUS_ON_ICC_SENT
     *                  SmsManger.STATUS_ON_ICC_UNSENT
     * @param pdu message PDU, as hex string
     * @param response sent when operation completes.
     *                  response.obj will be an AsyncResult, and will indicate
     *                  any error that may have occurred (eg, out of memory).
     */
    void writeSmsToSim(int status, String smsc, String pdu, Message response);

    void writeSmsToRuim(int status, String pdu, Message response);

    void setRadioPower(boolean on, Message response);

    void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message response);

    void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response);

    /**
     * Acknowledge successful or failed receipt of last incoming SMS,
     * including acknowledgement TPDU to send as the RP-User-Data element
     * of the RP-ACK or RP-ERROR PDU.
     *
     * @param success true to send RP-ACK, false to send RP-ERROR
     * @param ackPdu the acknowledgement TPDU in hexadecimal format
     * @param response sent when operation completes.
     */
    void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response);

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.result will be an IccIoResult on success
     */
    void iccIO (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, Message response);

    /**
     * parameters equivalent to 27.007 AT+CRSM command
     * response.obj will be an AsyncResult
     * response.obj.userObj will be a IccIoResult on success
     */
    void iccIOForApp (int command, int fileid, String path, int p1, int p2, int p3,
            String data, String pin2, String aid, Message response);

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 1 for "CLIP is provisioned", and 0 for "CLIP is not provisioned".
     *
     * @param response is callback message
     */

    void queryCLIP(Message response);

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

    void getCLIR(Message response);

    /**
     * clirMode is one of the CLIR_* constants above
     *
     * response.obj is null
     */

    void setCLIR(int clirMode, Message response);

    /**
     * (AsyncResult)response.obj).result is an int[] with element [0] set to
     * 0 for disabled, 1 for enabled.
     *
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void queryCallWaiting(int serviceClass, Message response);

    /**
     * @param enable is true to enable, false to disable
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void setCallWaiting(boolean enable, int serviceClass, Message response);

    /**
     * @param action is one of CF_ACTION_*
     * @param cfReason is one of CF_REASON_*
     * @param serviceClass is a sum of SERVICE_CLASSS_*
     */
    void setCallForward(int action, int cfReason, int serviceClass,
                String number, int timeSeconds, Message response);

    /**
     * cfReason is one of CF_REASON_*
     *
     * ((AsyncResult)response.obj).result will be an array of
     * CallForwardInfo's
     *
     * An array of length 0 means "disabled for all codes"
     */
    void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, Message response);

    void setNetworkSelectionModeAutomatic(Message response);

    void setNetworkSelectionModeManual(String operatorNumeric, Message response);

    /**
     * Queries whether the current network selection mode is automatic
     * or manual
     *
     * ((AsyncResult)response.obj).result  is an int[] with element [0] being
     * a 0 for automatic selection and a 1 for manual selection
     */

    void getNetworkSelectionMode(Message response);

    /**
     * Queries the currently available networks
     *
     * ((AsyncResult)response.obj).result  is a List of NetworkInfo objects
     */
    void getAvailableNetworks(Message response);

    void getBasebandVersion (Message response);


    /**
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled service classes (sum of SERVICE_CLASS_*)
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */

    void queryFacilityLock (String facility, String password, int serviceClass,
        Message response);

    /**
     * (AsyncResult)response.obj).result will be an Integer representing
     * the sum of enabled service classes (sum of SERVICE_CLASS_*) for the
     * application with appId.
     *
     * @param facility one of CB_FACILTY_*
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param appId is application Id or null if none
     * @param response is callback message
     */

    void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId,
        Message response);

    /**
     * @param facility one of CB_FACILTY_*
     * @param lockState true means lock, false means unlock
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param response is callback message
     */
    void setFacilityLock (String facility, boolean lockState, String password,
        int serviceClass, Message response);

    /**
     * Set the facility lock for the app with this AID on the ICC card.
     *
     * @param facility one of CB_FACILTY_*
     * @param lockState true means lock, false means unlock
     * @param password password or "" if not required
     * @param serviceClass is a sum of SERVICE_CLASS_*
     * @param appId is application Id or null if none
     * @param response is callback message
     */
    void setFacilityLockForApp(String facility, boolean lockState, String password,
        int serviceClass, String appId, Message response);

    void sendUSSD (String ussdString, Message response);

    /**
     * Cancels a pending USSD session if one exists.
     * @param response callback message
     */
    void cancelPendingUssd (Message response);

    void resetRadio(Message result);

    /**
     * Assign a specified band for RF configuration.
     *
     * @param bandMode one of BM_*_BAND
     * @param response is callback message
     */
    void setBandMode (int bandMode, Message response);

    /**
     * Query the list of band mode supported by RF.
     *
     * @param response is callback message
     *        ((AsyncResult)response.obj).result  is an int[] where int[0] is
     *        the size of the array and the rest of each element representing
     *        one available BM_*_BAND
     */
    void queryAvailableBandMode (Message response);

    /**
     *  Requests to set the preferred network type for searching and registering
     * (CS/PS domain, RAT, and operation mode)
     * @param networkType one of  NT_*_TYPE
     * @param response is callback message
     */
    void setPreferredNetworkType(int networkType , Message response);

     /**
     *  Query the preferred network type setting
     *
     * @param response is callback message to report one of  NT_*_TYPE
     */
    void getPreferredNetworkType(Message response);

    /**
     * Query neighboring cell ids
     *
     * @param response s callback message to cell ids
     */
    void getNeighboringCids(Message response);

    /**
     * Request to enable/disable network state change notifications when
     * location information (lac and/or cid) has changed.
     *
     * @param enable true to enable, false to disable
     * @param response callback message
     */
    void setLocationUpdates(boolean enable, Message response);

    /**
     * Gets the default SMSC address.
     *
     * @param result Callback message contains the SMSC address.
     */
    void getSmscAddress(Message result);

    /**
     * Sets the default SMSC address.
     *
     * @param address new SMSC address
     * @param result Callback message is empty on completion
     */
    void setSmscAddress(String address, Message result);

    /**
     * Indicates whether there is storage available for new SMS messages.
     * @param available true if storage is available
     * @param result callback message
     */
    void reportSmsMemoryStatus(boolean available, Message result);

    /**
     * Indicates to the vendor ril that StkService is running
     * and is ready to receive RIL_UNSOL_STK_XXXX commands.
     *
     * @param result callback message
     */
    void reportStkServiceIsRunning(Message result);

    void invokeOemRilRequestRaw(byte[] data, Message response);

    void invokeOemRilRequestStrings(String[] strings, Message response);

    /**
     * Fires when RIL_UNSOL_OEM_HOOK_RAW is received from the RIL.
     */
    void setOnUnsolOemHookRaw(Handler h, int what, Object obj);
    void unSetOnUnsolOemHookRaw(Handler h);

    /**
     * Send TERMINAL RESPONSE to the SIM, after processing a proactive command
     * sent by the SIM.
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with first byte of response data. See
     *                  TS 102 223 for details.
     * @param response  Callback message
     */
    public void sendTerminalResponse(String contents, Message response);

    /**
     * Send ENVELOPE to the SIM, after processing a proactive command sent by
     * the SIM.
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void sendEnvelope(String contents, Message response);

    /**
     * Send ENVELOPE to the SIM, such as an SMS-PP data download envelope
     * for a SIM data download message. This method has one difference
     * from {@link #sendEnvelope}: The SW1 and SW2 status bytes from the UICC response
     * are returned along with the response data.
     *
     * response.obj will be an AsyncResult
     * response.obj.result will be an IccIoResult on success
     *
     * @param contents  String containing SAT/USAT response in hexadecimal
     *                  format starting with command tag. See TS 102 223 for
     *                  details.
     * @param response  Callback message
     */
    public void sendEnvelopeWithStatus(String contents, Message response);

    /**
     * Accept or reject the call setup request from SIM.
     *
     * @param accept   true if the call is to be accepted, false otherwise.
     * @param response Callback message
     */
    public void handleCallSetupRequestFromSim(boolean accept, Message response);

    /**
     * Activate or deactivate cell broadcast SMS for GSM.
     *
     * @param activate
     *            true = activate, false = deactivate
     * @param result Callback message is empty on completion
     */
    public void setGsmBroadcastActivation(boolean activate, Message result);

    /**
     * Configure cell broadcast SMS for GSM.
     *
     * @param response Callback message is empty on completion
     */
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response);

    /**
     * Query the current configuration of cell broadcast SMS of GSM.
     *
     * @param response
     *        Callback message contains the configuration from the modem
     *        on completion
     */
    public void getGsmBroadcastConfig(Message response);

    //***** new Methods for CDMA support

    /**
     * Request the device ESN / MEID / IMEI / IMEISV.
     * "response" is const char **
     *   [0] is IMEI if GSM subscription is available
     *   [1] is IMEISV if GSM subscription is available
     *   [2] is ESN if CDMA subscription is available
     *   [3] is MEID if CDMA subscription is available
     */
    public void getDeviceIdentity(Message response);

    /**
     * Request the device MDN / H_SID / H_NID / MIN.
     * "response" is const char **
     *   [0] is MDN if CDMA subscription is available
     *   [1] is a comma separated list of H_SID (Home SID) in decimal format
     *       if CDMA subscription is available
     *   [2] is a comma separated list of H_NID (Home NID) in decimal format
     *       if CDMA subscription is available
     *   [3] is MIN (10 digits, MIN2+MIN1) if CDMA subscription is available
     */
    public void getCDMASubscription(Message response);

    /**
     * Send Flash Code.
     * "response" is is NULL
     *   [0] is a FLASH string
     */
    public void sendCDMAFeatureCode(String FeatureCode, Message response);

    /** Set the Phone type created */
    void setPhoneType(int phoneType);

    /**
     *  Query the CDMA roaming preference setting
     *
     * @param response is callback message to report one of  CDMA_RM_*
     */
    void queryCdmaRoamingPreference(Message response);

    /**
     *  Requests to set the CDMA roaming preference
     * @param cdmaRoamingType one of  CDMA_RM_*
     * @param response is callback message
     */
    void setCdmaRoamingPreference(int cdmaRoamingType, Message response);

    /**
     *  Requests to set the CDMA subscription mode
     * @param cdmaSubscriptionType one of  CDMA_SUBSCRIPTION_*
     * @param response is callback message
     */
    void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response);

    /**
     *  Requests to get the CDMA subscription srouce
     * @param response is callback message
     */
    void getCdmaSubscriptionSource(Message response);

    /**
     *  Set the TTY mode
     *
     * @param ttyMode one of the following:
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_OFF}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_FULL}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_HCO}
     * - {@link com.android.internal.telephony.Phone#TTY_MODE_VCO}
     * @param response is callback message
     */
    void setTTYMode(int ttyMode, Message response);

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
    void queryTTYMode(Message response);

    /**
     * Setup a packet data connection On successful completion, the result
     * message will return a {@link com.android.internal.telephony.dataconnection.DataCallResponse}
     * object containing the connection information.
     *
     * @param radioTechnology
     *            indicates whether to setup connection on radio technology CDMA
     *            (0) or GSM/UMTS (1)
     * @param profile
     *            Profile Number or NULL to indicate default profile
     * @param apn
     *            the APN to connect to if radio technology is GSM/UMTS.
     *            Otherwise null for CDMA.
     * @param user
     *            the username for APN, or NULL
     * @param password
     *            the password for APN, or NULL
     * @param authType
     *            the PAP / CHAP auth type. Values is one of SETUP_DATA_AUTH_*
     * @param protocol
     *            one of the PDP_type values in TS 27.007 section 10.1.1.
     *            For example, "IP", "IPV6", "IPV4V6", or "PPP".
     * @param result
     *            Callback message
     */
    public void setupDataCall(String radioTechnology, String profile,
            String apn, String user, String password, String authType,
            String protocol, Message result);

    /**
     * Deactivate packet data connection
     *
     * @param cid
     *            The connection ID
     * @param reason
     *            Data disconnect reason.
     * @param result
     *            Callback message is empty on completion
     */
    public void deactivateDataCall(int cid, int reason, Message result);

    /**
     * Activate or deactivate cell broadcast SMS for CDMA.
     *
     * @param activate
     *            true = activate, false = deactivate
     * @param result
     *            Callback message is empty on completion
     */
    public void setCdmaBroadcastActivation(boolean activate, Message result);

    /**
     * Configure cdma cell broadcast SMS.
     *
     * @param response
     *            Callback message is empty on completion
     */
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response);

    /**
     * Query the current configuration of cdma cell broadcast SMS.
     *
     * @param result
     *            Callback message contains the configuration from the modem on completion
     */
    public void getCdmaBroadcastConfig(Message result);

    /**
     *  Requests the radio's system selection module to exit emergency callback mode.
     *  This function should only be called from CDMAPHone.java.
     *
     * @param response callback message
     */
    public void exitEmergencyCallbackMode(Message response);

    /**
     * Request the status of the ICC and UICC cards.
     *
     * @param result
     *          Callback message containing {@link IccCardStatus} structure for the card.
     */
    public void getIccCardStatus(Message result);

    /**
     * Return if the current radio is LTE on CDMA. This
     * is a tri-state return value as for a period of time
     * the mode may be unknown.
     *
     * @return {@link PhoneConstants#LTE_ON_CDMA_UNKNOWN}, {@link PhoneConstants#LTE_ON_CDMA_FALSE}
     * or {@link PhoneConstants#LTE_ON_CDMA_TRUE}
     */
    public int getLteOnCdmaMode();

    /**
     * Request the ISIM application on the UICC to perform the AKA
     * challenge/response algorithm for IMS authentication. The nonce string
     * and challenge response are Base64 encoded Strings.
     *
     * @param nonce the nonce string to pass with the ISIM authentication request
     * @param response a callback message with the String response in the obj field
     * @deprecated
     * @see requestIccSimAuthentication
     */
    public void requestIsimAuthentication(String nonce, Message response);

    /**
     * Request the SIM application on the UICC to perform authentication
     * challenge/response algorithm. The data string and challenge response are
     * Base64 encoded Strings.
     * Can support EAP-SIM, EAP-AKA with results encoded per 3GPP TS 31.102.
     *
     * @param authContext is the P2 parameter that specifies the authentication context per 3GPP TS
     *                    31.102 (Section 7.1.2)
     * @param data authentication challenge data
     * @param aid used to determine which application/slot to send the auth command to. See ETSI
     *            102.221 8.1 and 101.220 4
     * @param response a callback message with the String response in the obj field
     */
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response);

    /**
     * Get the current Voice Radio Technology.
     *
     * AsyncResult.result is an int array with the first value
     * being one of the ServiceState.RIL_RADIO_TECHNOLOGY_xxx values.
     *
     * @param result is sent back to handler and result.obj is a AsyncResult
     */
    void getVoiceRadioTechnology(Message result);

    /**
     * Return the current set of CellInfo records
     *
     * AsyncResult.result is a of Collection<CellInfo>
     *
     * @param result is sent back to handler and result.obj is a AsyncResult
     */
    void getCellInfoList(Message result);

    /**
     * Sets the minimum time in milli-seconds between when RIL_UNSOL_CELL_INFO_LIST
     * should be invoked.
     *
     * The default, 0, means invoke RIL_UNSOL_CELL_INFO_LIST when any of the reported
     * information changes. Setting the value to INT_MAX(0x7fffffff) means never issue
     * A RIL_UNSOL_CELL_INFO_LIST.
     *
     *

     * @param rateInMillis is sent back to handler and result.obj is a AsyncResult
     * @param response.obj is AsyncResult ar when sent to associated handler
     *                        ar.exception carries exception on failure or null on success
     *                        otherwise the error.
     */
    void setCellInfoListRate(int rateInMillis, Message response);

    /**
     * Fires when RIL_UNSOL_CELL_INFO_LIST is received from the RIL.
     */
    void registerForCellInfoList(Handler h, int what, Object obj);
    void unregisterForCellInfoList(Handler h);

    /**
     * Set Initial Attach Apn
     *
     * @param apn
     *            the APN to connect to if radio technology is GSM/UMTS.
     * @param protocol
     *            one of the PDP_type values in TS 27.007 section 10.1.1.
     *            For example, "IP", "IPV6", "IPV4V6", or "PPP".
     * @param authType
     *            authentication protocol used for this PDP context
     *            (None: 0, PAP: 1, CHAP: 2, PAP&CHAP: 3)
     * @param username
     *            the username for APN, or NULL
     * @param password
     *            the password for APN, or NULL
     * @param result
     *            callback message contains the information of SUCCESS/FAILURE
     */
    public void setInitialAttachApn(String apn, String protocol, int authType, String username,
            String password, Message result);

    /**
     * Set data profiles in modem
     *
     * @param dps
     *            Array of the data profiles set to modem
     * @param result
     *            callback message contains the information of SUCCESS/FAILURE
     */
    public void setDataProfile(DataProfile[] dps, Message result);

    /**
     * Notifiy that we are testing an emergency call
     */
    public void testingEmergencyCall();

    /**
     * Open a logical channel to the SIM.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHO command.
     *
     * @param AID Application id. See ETSI 102.221 and 101.220.
     * @param response Callback message. response.obj will be an int [1] with
     *            element [0] set to the id of the logical channel.
     */
    public void iccOpenLogicalChannel(String AID, Message response);

    /**
     * Close a previously opened logical channel to the SIM.
     *
     * Input parameters equivalent to TS 27.007 AT+CCHC command.
     *
     * @param channel Channel id. Id of the channel to be closed.
     * @param response Callback message.
     */
    public void iccCloseLogicalChannel(int channel, Message response);

    /**
     * Exchange APDUs with the SIM on a logical channel.
     *
     * Input parameters equivalent to TS 27.007 AT+CGLA command.
     *
     * @param channel Channel id of the channel to use for communication. Has to
     *            be greater than zero.
     * @param cla Class of the APDU command.
     * @param instruction Instruction of the APDU command.
     * @param p1 P1 value of the APDU command.
     * @param p2 P2 value of the APDU command.
     * @param p3 P3 value of the APDU command. If p3 is negative a 4 byte APDU
     *            is sent to the SIM.
     * @param data Data to be sent with the APDU.
     * @param response Callback message. response.obj.userObj will be
     *            an IccIoResult on success.
     */
    public void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
            int p1, int p2, int p3, String data, Message response);

    /**
     * Exchange APDUs with the SIM on a basic channel.
     *
     * Input parameters equivalent to TS 27.007 AT+CSIM command.
     *
     * @param cla Class of the APDU command.
     * @param instruction Instruction of the APDU command.
     * @param p1 P1 value of the APDU command.
     * @param p2 P2 value of the APDU command.
     * @param p3 P3 value of the APDU command. If p3 is negative a 4 byte APDU
     *            is sent to the SIM.
     * @param data Data to be sent with the APDU.
     * @param response Callback message. response.obj.userObj will be
     *            an IccIoResult on success.
     */
    public void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
            int p3, String data, Message response);

    /**
     * Read one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param response callback message with the String response in the obj field
     */
    void nvReadItem(int itemID, Message response);

    /**
     * Write one of the NV items defined in {@link RadioNVItems} / {@code ril_nv_items.h}.
     * Used for device configuration by some CDMA operators.
     *
     * @param itemID the ID of the item to read
     * @param itemValue the value to write, as a String
     * @param response Callback message.
     */
    void nvWriteItem(int itemID, String itemValue, Message response);

    /**
     * Update the CDMA Preferred Roaming List (PRL) in the radio NV storage.
     * Used for device configuration by some CDMA operators.
     *
     * @param preferredRoamingList byte array containing the new PRL
     * @param response Callback message.
     */
    void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response);

    /**
     * Perform the specified type of NV config reset. The radio will be taken offline
     * and the device must be rebooted after erasing the NV. Used for device
     * configuration by some CDMA operators.
     *
     * @param resetType reset type: 1: reload NV reset, 2: erase NV reset, 3: factory NV reset
     * @param response Callback message.
     */
    void nvResetConfig(int resetType, Message response);

    /**
     *  returned message
     *  retMsg.obj = AsyncResult ar
     *  ar.exception carries exception on failure
     *  ar.userObject contains the orignal value of result.obj
     *  ar.result contains a List of HardwareConfig
     */
    void getHardwareConfig (Message result);

    /**
     * @return version of the ril.
     */
    int getRilVersion();

   /**
     * Sets user selected subscription at Modem.
     *
     * @param slotId
     *          Slot.
     * @param appIndex
     *          Application index in the card.
     * @param subId
     *          Indicates subscription 0 or subscription 1.
     * @param subStatus
     *          Activation status, 1 = activate and 0 = deactivate.
     * @param result
     *          Callback message contains the information of SUCCESS/FAILURE.
     */
    // FIXME Update the doc and consider modifying the request to make more generic.
    public void setUiccSubscription(int slotId, int appIndex, int subId, int subStatus,
            Message result);

    /**
     * Tells the modem if data is allowed or not.
     *
     * @param allowed
     *          true = allowed, false = not alowed
     * @param result
     *          Callback message contains the information of SUCCESS/FAILURE.
     */
    // FIXME We may need to pass AID and slotid also
    public void setDataAllowed(boolean allowed, Message result);

    /**
     * Inform RIL that the device is shutting down
     *
     * @param result Callback message contains the information of SUCCESS/FAILURE
     */
    public void requestShutdown(Message result);

    /**
     *  Set phone radio type and access technology.
     *
     *  @param rc the phone radio capability defined in
     *         RadioCapability. It's a input object used to transfer parameter to logic modem
     *
     *  @param result Callback message.
     */
    public void setRadioCapability(RadioCapability rc, Message result);

    /**
     *  Get phone radio capability
     *
     *  @param result Callback message.
     */
    public void getRadioCapability(Message result);

    /**
     * Registers the handler when phone radio capability is changed.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    public void registerForRadioCapabilityChanged(Handler h, int what, Object obj);

    /**
     * Unregister for notifications when phone radio capability is changed.
     *
     * @param h Handler to be removed from the registrant list.
     */
    public void unregisterForRadioCapabilityChanged(Handler h);

    /**
     * Start LCE (Link Capacity Estimation) service with a desired reporting interval.
     *
     * @param reportIntervalMs
     *        LCE info reporting interval (ms).
     *
     * @param result Callback message contains the current LCE status.
     * {byte status, int actualIntervalMs}
     */
    public void startLceService(int reportIntervalMs, boolean pullMode, Message result);

    /**
     * Stop LCE service.
     *
     * @param result Callback message contains the current LCE status:
     * {byte status, int actualIntervalMs}
     *
     */
    public void stopLceService(Message result);

    /**
     * Pull LCE service for capacity data.
     *
     * @param result Callback message contains the capacity info:
     * {int capacityKbps, byte confidenceLevel, byte lceSuspendedTemporarily}
     */
    public void pullLceData(Message result);

    /**
     * Register a LCE info listener.
     *
     * @param h Handler for notification message.
     * @param what User-defined message code.
     * @param obj User object.
     */
    void registerForLceInfo(Handler h, int what, Object obj);

    /**
     * Unregister the LCE Info listener.
     *
     * @param h handle to be removed.
     */
    void unregisterForLceInfo(Handler h);

    /**
     *
     * Get modem activity info and stats
     *
     * @param result Callback message contains the modem activity information
     */
    public void getModemActivityInfo(Message result);

    /*SPRD: add for unsolOemHookRaw @{ */
    public void registerForOemHookRaw(Handler h, int what, Object obj);
    public void unregisterForOemHookRaw(Handler h);
    /* @} */

    /*SPRD: Bug 474689 Add for SPWRN feature @{*/
    public void setOnPWSMessage(Handler h, int what, Object obj);
    public void unSetOnPWSMessage(Handler h);
    /*@}*/

    /* SPRD: add for bug479433 @{ */
    public void dialVP(String address, String sub_address, int clirMode, Message result);
    public void codecVP(int type, Bundle param, Message result);
    /* @} */

    /*SPRD: Add for one key sim unlock feature @{*/
    public void setFacilityLockByUser(String facility, boolean lockState, Message response);
    /*@}*/

    /*SPRD: Add for VoLTE @{*/
    public void registerForImsCallStateChanged(Handler h, int what, Object obj);
    public void unregisterForImsCallStateChanged(Handler h);
    public void getImsCurrentCalls (Message result);
    public void setImsVoiceCallAvailability(int state, Message response);
    public void getImsVoiceCallAvailability(Message response);
    public void initISIM(String confUri, String instanceId, String impu,
            String impi, String domain, String xCap, String bspAddr,
            Message response);
    public void registerForImpu(Message response, String impu);
    public void registerForImpi(Message response, String impi);
    public void registerForDomain(Message response, String domain);
    public void registerForImei(Message response, String imei);
    public void registerForXcap(Message response, String xcap);
    public void registerForBsf(Message response, String bsf);
    public void disableIms(Message response);
    public void requestVolteCallMediaChange(boolean isVideo, Message response);
    public void responseVolteCallMediaChange(boolean isAccept, Message response);
    public void setImsSmscAddress(String smsc, Message response);
    public void requestVolteCallFallBackToVoice(Message response);
    public void setInitialAttachIMSApn(String apn, String protocol, int authType, String username,
            String password, Message result);
    public void requestInitialGroupCall(String numbers, Message response);
    public void requestAddGroupCall(String numbers, Message response);
    public void setConferenceUri(Message response, String uri);
    public void setCallForward(int action, int cfReason, int serviceClass,
            String number, int timeSeconds, String ruleSet, Message response);
    public void queryCallForwardStatus(int cfReason, int serviceClass,
            String number, String ruleSet, Message response);
    public void enableIms(Message response);
    public void registerForConnImsen(Handler h, int what, Object obj);
    public void unregisterForConnImsen(Handler h);
    /*@}*/
    /* SPRD: add for DATA CLEAR CODE @{ */
    public void registerForRauSuccess(Handler h, int what, Object obj);
    public void unregisterForRauSuccess(Handler h);
    public void registerForClearCodeFallBack(Handler h, int what, Object obj);
    public void unregisterForClearCodeFallBack(Handler h);
    /* @} */
}
