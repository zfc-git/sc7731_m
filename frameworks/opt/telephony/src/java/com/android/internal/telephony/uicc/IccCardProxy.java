/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.internal.telephony.uicc;

import static android.Manifest.permission.READ_PHONE_STATE;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.Rlog;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.MccTable;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.cdma.CdmaSubscriptionSourceManager;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.SubscriptionController;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppState;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.PersoSubState;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccCardStatus.PinState;
import com.android.internal.telephony.uicc.UiccController;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * @Deprecated use {@link UiccController}.getUiccCard instead.
 *
 * The Phone App assumes that there is only one icc card, and one icc application
 * available at a time. Moreover, it assumes such object (represented with IccCard)
 * is available all the time (whether {@link RILConstants#RIL_REQUEST_GET_SIM_STATUS} returned
 * or not, whether card has desired application or not, whether there really is a card in the
 * slot or not).
 *
 * UiccController, however, can handle multiple instances of icc objects (multiple
 * {@link UiccCardApplication}, multiple {@link IccFileHandler}, multiple {@link IccRecords})
 * created and destroyed dynamically during phone operation.
 *
 * This class implements the IccCard interface that is always available (right after default
 * phone object is constructed) to expose the current (based on voice radio technology)
 * application on the uicc card, so that external apps won't break.
 */

public class IccCardProxy extends Handler implements IccCard {
    private static final boolean DBG = true;
    private static final String LOG_TAG = "IccCardProxy";

    private static final int EVENT_RADIO_OFF_OR_UNAVAILABLE = 1;
    private static final int EVENT_RADIO_ON = 2;
    private static final int EVENT_ICC_CHANGED = 3;
    private static final int EVENT_ICC_ABSENT = 4;
    private static final int EVENT_ICC_LOCKED = 5;
    private static final int EVENT_APP_READY = 6;
    private static final int EVENT_RECORDS_LOADED = 7;
    private static final int EVENT_IMSI_READY = 8;
    private static final int EVENT_NETWORK_LOCKED = 9;
    private static final int EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 11;
    /** Add for sim lock @{ */
    private static final int EVENT_NETWORK_SUBSET_LOCK = 12;
    private static final int EVENT_SERVICE_PROVIDER_LOCK = 13;
    private static final int EVENT_CORPORATE_LOCK = 14;
    private static final int EVENT_SIM_LOCK = 15;
    private static final int EVENT_SIM_LOCK_FOREVER = 16;
    private static final int EVENT_NETWORK_LOCK_PUK = 17;
    private static final int EVENT_NETWORK_SUBSET_LOCK_PUK = 18;
    private static final int EVENT_SERVICE_PROVIDER_LOCK_PUK = 19;
    private static final int EVENT_CORPORATE_LOCK_PUK = 20;
    private static final int EVENT_SIM_LOCK_PUK = 21;
    /** @} */

    private static final int EVENT_ICC_RECORD_EVENTS = 500;
    private static final int EVENT_SUBSCRIPTION_ACTIVATED = 501;
    private static final int EVENT_SUBSCRIPTION_DEACTIVATED = 502;
    private static final int EVENT_CARRIER_PRIVILIGES_LOADED = 503;

    private Integer mPhoneId = null;

    private final Object mLock = new Object();
    private Context mContext;
    private CommandsInterface mCi;
    private TelephonyManager mTelephonyManager;

    private RegistrantList mAbsentRegistrants = new RegistrantList();
    private RegistrantList mPinLockedRegistrants = new RegistrantList();
    private RegistrantList mNetworkLockedRegistrants = new RegistrantList();

    private RegistrantList mNetworkSubsetLockedRegistrants = new RegistrantList();
    private RegistrantList mServiceProviderLockedRegistrants = new RegistrantList();
    private RegistrantList mCorporateLockedRegistrants = new RegistrantList();
    private RegistrantList mSimLockedRegistrants = new RegistrantList();
    private RegistrantList mSimLockedForeverRegistrants = new RegistrantList();
    private RegistrantList mNetworkSubsetLockedPukRegistrants = new RegistrantList();
    private RegistrantList mServiceProviderLockedPukRegistrants = new RegistrantList();
    private RegistrantList mCorporateLockedPukRegistrants = new RegistrantList();
    private RegistrantList mSimLockedPukRegistrants = new RegistrantList();
    private RegistrantList mNetworkLockedPukRegistrants = new RegistrantList(); //network lock puk

    private int mCurrentAppType = UiccController.APP_FAM_3GPP; //default to 3gpp?
    private UiccController mUiccController = null;
    private UiccCard mUiccCard = null;
    private UiccCardApplication mUiccApplication = null;
    private IccRecords mIccRecords = null;
    private CdmaSubscriptionSourceManager mCdmaSSM = null;
    private boolean mRadioOn = false;
    private boolean mQuietMode = false; // when set to true IccCardProxy will not broadcast
                                        // ACTION_SIM_STATE_CHANGED intents
    private boolean mInitialized = false;
    private State mExternalState = State.UNKNOWN;

    public static final String ACTION_INTERNAL_SIM_STATE_CHANGED = "android.intent.action.internal_sim_state_changed";

    public IccCardProxy(Context context, CommandsInterface ci, int phoneId) {
        if (DBG) log("ctor: ci=" + ci + " phoneId=" + phoneId);
        mContext = context;
        mCi = ci;
        mPhoneId = phoneId;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        mCdmaSSM = CdmaSubscriptionSourceManager.getInstance(context,
                ci, this, EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED, null);
        mUiccController = UiccController.getInstance();
        mUiccController.registerForIccChanged(this, EVENT_ICC_CHANGED, null);
        ci.registerForOn(this,EVENT_RADIO_ON, null);
        ci.registerForOffOrNotAvailable(this, EVENT_RADIO_OFF_OR_UNAVAILABLE, null);

        resetProperties();
        setExternalState(State.NOT_READY, false);
    }

    public void dispose() {
        synchronized (mLock) {
            log("Disposing");
            //Cleanup icc references
            mUiccController.unregisterForIccChanged(this);
            mUiccController = null;
            mCi.unregisterForOn(this);
            mCi.unregisterForOffOrNotAvailable(this);
            mCdmaSSM.dispose(this);
        }
    }

    /*
     * The card application that the external world sees will be based on the
     * voice radio technology only!
     */
    public void setVoiceRadioTech(int radioTech) {
        synchronized (mLock) {
            if (DBG) {
                log("Setting radio tech " + ServiceState.rilRadioTechnologyToString(radioTech));
            }
            if (ServiceState.isGsm(radioTech)) {
                mCurrentAppType = UiccController.APP_FAM_3GPP;
            } else {
                mCurrentAppType = UiccController.APP_FAM_3GPP2;
            }
            updateQuietMode();
        }
    }

    /**
     * In case of 3gpp2 we need to find out if subscription used is coming from
     * NV in which case we shouldn't broadcast any sim states changes.
     */
    private void updateQuietMode() {
        synchronized (mLock) {
            boolean oldQuietMode = mQuietMode;
            boolean newQuietMode;
            int cdmaSource = Phone.CDMA_SUBSCRIPTION_UNKNOWN;
            boolean isLteOnCdmaMode = TelephonyManager.getLteOnCdmaModeStatic()
                    == PhoneConstants.LTE_ON_CDMA_TRUE;
            if (mCurrentAppType == UiccController.APP_FAM_3GPP) {
                newQuietMode = false;
                if (DBG) log("updateQuietMode: 3GPP subscription -> newQuietMode=" + newQuietMode);
            } else {
                if (isLteOnCdmaMode) {
                    log("updateQuietMode: is cdma/lte device, force IccCardProxy into 3gpp mode");
                    mCurrentAppType = UiccController.APP_FAM_3GPP;
                }
                cdmaSource = mCdmaSSM != null ?
                        mCdmaSSM.getCdmaSubscriptionSource() : Phone.CDMA_SUBSCRIPTION_UNKNOWN;

                newQuietMode = (cdmaSource == Phone.CDMA_SUBSCRIPTION_NV)
                        && (mCurrentAppType == UiccController.APP_FAM_3GPP2)
                        && !isLteOnCdmaMode;
                if (DBG) {
                    log("updateQuietMode: cdmaSource=" + cdmaSource
                            + " mCurrentAppType=" + mCurrentAppType
                            + " isLteOnCdmaMode=" + isLteOnCdmaMode
                            + " newQuietMode=" + newQuietMode);
                }
            }

            if (mQuietMode == false && newQuietMode == true) {
                // Last thing to do before switching to quiet mode is
                // broadcast ICC_READY
                log("Switching to QuietMode.");
                setExternalState(State.READY);
                mQuietMode = newQuietMode;
            } else if (mQuietMode == true && newQuietMode == false) {
                if (DBG) {
                    log("updateQuietMode: Switching out from QuietMode."
                            + " Force broadcast of current state=" + mExternalState);
                }
                mQuietMode = newQuietMode;
                setExternalState(mExternalState, true);
            } else {
                if (DBG) log("updateQuietMode: no changes don't setExternalState");
            }
            if (DBG) {
                log("updateQuietMode: QuietMode is " + mQuietMode + " (app_type="
                    + mCurrentAppType + " isLteOnCdmaMode=" + isLteOnCdmaMode
                    + " cdmaSource=" + cdmaSource + ")");
            }
            mInitialized = true;
            sendMessage(obtainMessage(EVENT_ICC_CHANGED));
        }
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case EVENT_RADIO_OFF_OR_UNAVAILABLE:
                mRadioOn = false;
                if (CommandsInterface.RadioState.RADIO_UNAVAILABLE == mCi.getRadioState()) {
                    setExternalState(State.NOT_READY);
                }
                break;
            case EVENT_RADIO_ON:
                mRadioOn = true;
                if (!mInitialized) {
                    updateQuietMode();
                }
                break;
            case EVENT_ICC_CHANGED:
                if (mInitialized) {
                    updateIccAvailability();
                }
                break;
            case EVENT_ICC_ABSENT:
                mAbsentRegistrants.notifyRegistrants();
                setExternalState(State.ABSENT);
                break;
            case EVENT_ICC_LOCKED:
                processLockedState();
                break;
            case EVENT_APP_READY:
                setExternalState(State.READY);
                break;
            case EVENT_RECORDS_LOADED:
                // Update the MCC/MNC.
                if (mIccRecords != null) {
                    String operator = mIccRecords.getOperatorNumeric();
                    log("operator=" + operator + " mPhoneId=" + mPhoneId);

                    if (operator != null) {
                        log("update icc_operator_numeric=" + operator);
                        mTelephonyManager.setSimOperatorNumericForPhone(mPhoneId, operator);
                        String countryCode = operator.substring(0,3);
                        if (countryCode != null) {
                            mTelephonyManager.setSimCountryIsoForPhone(mPhoneId,
                                    MccTable.countryCodeForMcc(Integer.parseInt(countryCode)));
                        } else {
                            loge("EVENT_RECORDS_LOADED Country code is null");
                        }
                    } else {
                        loge("EVENT_RECORDS_LOADED Operator name is null");
                    }
                }
                if (mUiccCard != null && !mUiccCard.areCarrierPriviligeRulesLoaded()) {
                    mUiccCard.registerForCarrierPrivilegeRulesLoaded(
                        this, EVENT_CARRIER_PRIVILIGES_LOADED, null);
                } else {
                    onRecordsLoaded();
                }
                break;
            case EVENT_IMSI_READY:
                broadcastIccStateChangedIntent(IccCardConstants.INTENT_VALUE_ICC_IMSI, null);
                break;
            case EVENT_NETWORK_LOCKED:
                mNetworkLockedRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_LOCKED);
                break;
            case EVENT_CDMA_SUBSCRIPTION_SOURCE_CHANGED:
                updateQuietMode();
                break;
            case EVENT_SUBSCRIPTION_ACTIVATED:
                log("EVENT_SUBSCRIPTION_ACTIVATED");
                onSubscriptionActivated();
                break;

            case EVENT_SUBSCRIPTION_DEACTIVATED:
                log("EVENT_SUBSCRIPTION_DEACTIVATED");
                onSubscriptionDeactivated();
                break;

            case EVENT_ICC_RECORD_EVENTS:
                if ((mCurrentAppType == UiccController.APP_FAM_3GPP) && (mIccRecords != null)) {
                    AsyncResult ar = (AsyncResult)msg.obj;
                    int eventCode = (Integer) ar.result;
                    if (eventCode == SIMRecords.EVENT_SPN) {
                        mTelephonyManager.setSimOperatorNameForPhone(
                                mPhoneId, mIccRecords.getServiceProviderName());
                    }
                }
                break;

            case EVENT_CARRIER_PRIVILIGES_LOADED:
                log("EVENT_CARRIER_PRIVILEGES_LOADED");
                if (mUiccCard != null) {
                    mUiccCard.unregisterForCarrierPrivilegeRulesLoaded(this);
                }
                onRecordsLoaded();
                break;
            /** Add for sim lock @{ */
            case EVENT_SIM_LOCK:
                mSimLockedRegistrants.notifyRegistrants();
                setExternalState(State.SIM_LOCKED);
                break;
            case EVENT_NETWORK_SUBSET_LOCK:
                mNetworkSubsetLockedRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_SUBSET_LOCKED);
                break;
            case EVENT_SERVICE_PROVIDER_LOCK:
                mServiceProviderLockedRegistrants.notifyRegistrants();
                setExternalState(State.SERVICE_PROVIDER_LOCKED);
                break;
            case EVENT_CORPORATE_LOCK:
                mCorporateLockedRegistrants.notifyRegistrants();
                setExternalState(State.CORPORATE_LOCKED);
                break;
            case EVENT_SIM_LOCK_FOREVER:
                mSimLockedForeverRegistrants.notifyRegistrants();
                setExternalState(State.SIM_LOCKED_FOREVER);
                break;
            case EVENT_NETWORK_LOCK_PUK:
                mNetworkLockedPukRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_LOCKED_PUK);
                break;
            case EVENT_NETWORK_SUBSET_LOCK_PUK:
                mNetworkSubsetLockedPukRegistrants.notifyRegistrants();
                setExternalState(State.NETWORK_SUBSET_LOCKED_PUK);
                break;
            case EVENT_SERVICE_PROVIDER_LOCK_PUK:
                mServiceProviderLockedPukRegistrants.notifyRegistrants();
                setExternalState(State.SERVICE_PROVIDER_LOCKED_PUK);
                break;
            case EVENT_CORPORATE_LOCK_PUK:
                mCorporateLockedPukRegistrants.notifyRegistrants();
                setExternalState(State.CORPORATE_LOCKED_PUK);
                break;
            case EVENT_SIM_LOCK_PUK:
                mSimLockedPukRegistrants.notifyRegistrants();
                setExternalState(State.SIM_LOCKED_PUK);
                break;
            /** @} */

            default:
                loge("Unhandled message with number: " + msg.what);
                break;
        }
    }

    private void onSubscriptionActivated() {
        updateIccAvailability();
        updateStateProperty();
    }

    private void onSubscriptionDeactivated() {
        resetProperties();
        updateIccAvailability();
        updateStateProperty();
    }

    private void onRecordsLoaded() {
        broadcastInternalIccStateChangedIntent(IccCardConstants.INTENT_VALUE_ICC_LOADED, null);
    }

    private void updateIccAvailability() {
        synchronized (mLock) {
            UiccCard newCard = mUiccController.getUiccCard(mPhoneId);
            CardState state = CardState.CARDSTATE_ABSENT;
            UiccCardApplication newApp = null;
            IccRecords newRecords = null;
            if (newCard != null) {
                state = newCard.getCardState();
                newApp = newCard.getApplication(mCurrentAppType);
                if (newApp != null) {
                    newRecords = newApp.getIccRecords();
                }
            }

            if (mIccRecords != newRecords || mUiccApplication != newApp || mUiccCard != newCard) {
                if (DBG) log("Icc changed. Reregestering.");
                unregisterUiccCardEvents();
                mUiccCard = newCard;
                mUiccApplication = newApp;
                mIccRecords = newRecords;
                registerUiccCardEvents();
            }
            updateExternalState();
        }
    }

    void resetProperties() {
        if (mCurrentAppType == UiccController.APP_FAM_3GPP) {
            log("update icc_operator_numeric=" + "");
            mTelephonyManager.setSimOperatorNumericForPhone(mPhoneId, "");
            mTelephonyManager.setSimCountryIsoForPhone(mPhoneId, "");
            mTelephonyManager.setSimOperatorNameForPhone(mPhoneId, "");
         }
    }

    private void HandleDetectedState() {
    // CAF_MSIM SAND
//        setExternalState(State.DETECTED, false);
    }

    private void updateExternalState() {

        // mUiccCard could be null at bootup, before valid card states have
        // been received from UiccController.
        if (mUiccCard == null) {
            setExternalState(State.NOT_READY);
            return;
        }

        /* SPRD: Add for feature of APN and DATA Pop up.BugId: 509845. @{ */
        // Logic from google impact SPRD
        // When sim locked and changed,will not notify select primary
      /*if (mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
           if (mRadioOn) {
                setExternalState(State.ABSENT);
            } else {
                setExternalState(State.NOT_READY);
            }
            return;
        }*/

        if (mUiccCard.getCardState() == CardState.CARDSTATE_ABSENT) {
             setExternalState(State.ABSENT);
             return;
         }
        /* @} */

        if (mUiccCard.getCardState() == CardState.CARDSTATE_ERROR) {
            setExternalState(State.CARD_IO_ERROR);
            return;
        }

        if (mUiccApplication == null) {
            setExternalState(State.NOT_READY);
            return;
        }

        switch (mUiccApplication.getState()) {
            case APPSTATE_UNKNOWN:
                setExternalState(State.UNKNOWN);
                break;
            case APPSTATE_DETECTED:
                HandleDetectedState();
                break;
            case APPSTATE_PIN:
                setExternalState(State.PIN_REQUIRED);
                break;
            case APPSTATE_PUK:
                setExternalState(State.PUK_REQUIRED);
                break;
            case APPSTATE_SUBSCRIPTION_PERSO:
                if (mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_NETWORK) {
                    setExternalState(State.NETWORK_LOCKED);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET) {
                    setExternalState(State.NETWORK_SUBSET_LOCKED);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER) {
                    setExternalState(State.SERVICE_PROVIDER_LOCKED);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_CORPORATE) {
                    setExternalState(State.CORPORATE_LOCKED);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_SIM) {
                    setExternalState(State.SIM_LOCKED);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_NETWORK_PUK) {
                    setExternalState(State.NETWORK_LOCKED_PUK);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_NETWORK_SUBSET_PUK) {
                    setExternalState(State.NETWORK_SUBSET_LOCKED_PUK);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_SERVICE_PROVIDER_PUK) {
                    setExternalState(State.SERVICE_PROVIDER_LOCKED_PUK);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_CORPORATE_PUK) {
                    setExternalState(State.CORPORATE_LOCKED_PUK);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_SIM_PUK) {
                    setExternalState(State.SIM_LOCKED_PUK);
                } else if(mUiccApplication.getPersoSubState() ==
                        PersoSubState.PERSOSUBSTATE_SIM_LOCK_FOREVER) {
                    setExternalState(State.SIM_LOCKED_FOREVER);
                } else {
                    setExternalState(State.UNKNOWN);
                }
                break;
            case APPSTATE_READY:
                setExternalState(State.READY);
                break;
        }
    }

    private void registerUiccCardEvents() {
        if (mUiccCard != null) {
            mUiccCard.registerForAbsent(this, EVENT_ICC_ABSENT, null);
        }
        if (mUiccApplication != null) {
            mUiccApplication.registerForReady(this, EVENT_APP_READY, null);
            mUiccApplication.registerForLocked(this, EVENT_ICC_LOCKED, null);
            mUiccApplication.registerForNetworkLocked(this, EVENT_NETWORK_LOCKED, null);
            /** Add for sim lock @{ */
            mUiccApplication.registerForSimLocked(this, EVENT_SIM_LOCK, null);
            mUiccApplication.registerForNetworkSubsetLocked(this, EVENT_NETWORK_SUBSET_LOCK, null);
            mUiccApplication.registerForCorporateLocked(this, EVENT_CORPORATE_LOCK, null);
            mUiccApplication.registerForServiceProviderLocked(this, EVENT_SERVICE_PROVIDER_LOCK, null);
            mUiccApplication.registerForSimLockedForever(this, EVENT_SIM_LOCK_FOREVER, null);
            mUiccApplication.registerForNetworkLockedPuk(this,EVENT_NETWORK_LOCK_PUK, null);
            mUiccApplication.registerForSimLockedPuk(this, EVENT_SIM_LOCK_PUK, null);
            mUiccApplication.registerForNetworkSubsetLockedPuk(this, EVENT_NETWORK_SUBSET_LOCK_PUK, null);
            mUiccApplication.registerForCorporateLockedPuk(this, EVENT_CORPORATE_LOCK_PUK, null);
            mUiccApplication.registerForServiceProviderLockedPuk(this, EVENT_SERVICE_PROVIDER_LOCK_PUK, null);
            /* @} */
        }
        if (mIccRecords != null) {
            mIccRecords.registerForImsiReady(this, EVENT_IMSI_READY, null);
            mIccRecords.registerForRecordsLoaded(this, EVENT_RECORDS_LOADED, null);
            mIccRecords.registerForRecordsEvents(this, EVENT_ICC_RECORD_EVENTS, null);
        }
    }

    private void unregisterUiccCardEvents() {
        if (mUiccCard != null) mUiccCard.unregisterForAbsent(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForReady(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForNetworkLocked(this);
        if (mIccRecords != null) mIccRecords.unregisterForImsiReady(this);
        if (mIccRecords != null) mIccRecords.unregisterForRecordsLoaded(this);
        if (mIccRecords != null) mIccRecords.unregisterForRecordsEvents(this);
        /** Add for sim lock @{ */
        if (mUiccApplication != null) mUiccApplication.unregisterForNetworkSubsetLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForSimLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForServiceProviderLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForCorporateLocked(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForSimLockedForever(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForNetworkLockedPuk(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForNetworkSubsetLockedPuk(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForSimLockedPuk(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForServiceProviderLockedPuk(this);
        if (mUiccApplication != null) mUiccApplication.unregisterForCorporateLockedPuk(this);
        /* @} */
    }

    private void updateStateProperty() {
        mTelephonyManager.setSimStateForPhone(mPhoneId, getState().toString());
    }

    private void broadcastIccStateChangedIntent(String value, String reason) {
        synchronized (mLock) {
            if (mPhoneId == null || !SubscriptionManager.isValidSlotId(mPhoneId)) {
                loge("broadcastIccStateChangedIntent: mPhoneId=" + mPhoneId
                        + " is invalid; Return!!");
                return;
            }

            if (mQuietMode) {
                log("broadcastIccStateChangedIntent: QuietMode"
                        + " NOT Broadcasting intent ACTION_SIM_STATE_CHANGED "
                        + " value=" +  value + " reason=" + reason);
                return;
            }

            Intent intent = new Intent(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
            // TODO - we'd like this intent to have a single snapshot of all sim state,
            // but until then this should not use REPLACE_PENDING or we may lose
            // information
            // intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
            intent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, value);
            intent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
            SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhoneId);
            log("broadcastIccStateChangedIntent intent ACTION_SIM_STATE_CHANGED value=" + value
                + " reason=" + reason + " for mPhoneId=" + mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE,
                    UserHandle.USER_ALL);
        }
    }

    private void broadcastInternalIccStateChangedIntent(String value, String reason) {
        synchronized (mLock) {
            if (mPhoneId == null) {
                loge("broadcastInternalIccStateChangedIntent: Card Index is not set; Return!!");
                return;
            }

            // SPRD: [Bug493799]
            Intent intent = new Intent(ACTION_INTERNAL_SIM_STATE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(PhoneConstants.PHONE_NAME_KEY, "Phone");
            intent.putExtra(IccCardConstants.INTENT_KEY_ICC_STATE, value);
            intent.putExtra(IccCardConstants.INTENT_KEY_LOCKED_REASON, reason);
            intent.putExtra(PhoneConstants.PHONE_KEY, mPhoneId);  // SubId may not be valid.
            log("Sending intent ACTION_INTERNAL_SIM_STATE_CHANGED" + " for mPhoneId : " + mPhoneId);
            ActivityManagerNative.broadcastStickyIntent(intent, null, UserHandle.USER_ALL);
        }
    }

    private void setExternalState(State newState, boolean override) {
        synchronized (mLock) {
            if (mPhoneId == null || !SubscriptionManager.isValidSlotId(mPhoneId)) {
                loge("setExternalState: mPhoneId=" + mPhoneId + " is invalid; Return!!");
                return;
            }

            if (!override && newState == mExternalState) {
                loge("setExternalState: !override and newstate unchanged from " + newState);
                return;
            }
            mExternalState = newState;
            loge("setExternalState: set mPhoneId=" + mPhoneId + " mExternalState=" + mExternalState);
            mTelephonyManager.setSimStateForPhone(mPhoneId, getState().toString());

            // For locked states, we should be sending internal broadcast.
            if (IccCardConstants.INTENT_VALUE_ICC_LOCKED.equals(getIccStateIntentString(mExternalState))) {
                broadcastInternalIccStateChangedIntent(getIccStateIntentString(mExternalState),
                        getIccStateReason(mExternalState));
            } else {
                broadcastIccStateChangedIntent(getIccStateIntentString(mExternalState),
                        getIccStateReason(mExternalState));
            }
            // TODO: Need to notify registrants for other states as well.
            if ( State.ABSENT == mExternalState) {
                mAbsentRegistrants.notifyRegistrants();
            }
        }
    }

    private void processLockedState() {
        synchronized (mLock) {
            if (mUiccApplication == null) {
                //Don't need to do anything if non-existent application is locked
                return;
            }
            PinState pin1State = mUiccApplication.getPin1State();
            if (pin1State == PinState.PINSTATE_ENABLED_PERM_BLOCKED) {
                setExternalState(State.PERM_DISABLED);
                return;
            }

            AppState appState = mUiccApplication.getState();
            switch (appState) {
                case APPSTATE_PIN:
                    mPinLockedRegistrants.notifyRegistrants();
                    setExternalState(State.PIN_REQUIRED);
                    break;
                case APPSTATE_PUK:
                    setExternalState(State.PUK_REQUIRED);
                    break;
                case APPSTATE_DETECTED:
                case APPSTATE_READY:
                case APPSTATE_SUBSCRIPTION_PERSO:
                case APPSTATE_UNKNOWN:
                    // Neither required
                    break;
            }
        }
    }

    private void setExternalState(State newState) {
        setExternalState(newState, false);
    }

    public boolean getIccRecordsLoaded() {
        synchronized (mLock) {
            if (mIccRecords != null) {
                return mIccRecords.getRecordsLoaded();
            }
            return false;
        }
    }

    private String getIccStateIntentString(State state) {
        switch (state) {
            case ABSENT: return IccCardConstants.INTENT_VALUE_ICC_ABSENT;
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case READY: return IccCardConstants.INTENT_VALUE_ICC_READY;
            case NOT_READY: return IccCardConstants.INTENT_VALUE_ICC_NOT_READY;
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            /* SPRD: Add for SIM LOCK. {@ */
            case NETWORK_SUBSET_LOCKED:
            case SERVICE_PROVIDER_LOCKED:
            case CORPORATE_LOCKED:
            case SIM_LOCKED:
            case NETWORK_LOCKED_PUK:
            case NETWORK_SUBSET_LOCKED_PUK:
            case SERVICE_PROVIDER_LOCKED_PUK:
            case CORPORATE_LOCKED_PUK:
            case SIM_LOCKED_PUK:
            case SIM_LOCKED_FOREVER: return IccCardConstants.INTENT_VALUE_ICC_LOCKED;
            /* @} */
            default: return IccCardConstants.INTENT_VALUE_ICC_UNKNOWN;
        }
    }

    /**
     * Locked state have a reason (PIN, PUK, NETWORK, PERM_DISABLED, CARD_IO_ERROR)
     * @return reason
     */
    private String getIccStateReason(State state) {
        switch (state) {
            case PIN_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PIN;
            case PUK_REQUIRED: return IccCardConstants.INTENT_VALUE_LOCKED_ON_PUK;
            case NETWORK_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK;
            case NETWORK_SUBSET_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_NS;
            case SERVICE_PROVIDER_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_SP;
            case CORPORATE_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_CP;
            case SIM_LOCKED: return IccCardConstants.INTENT_VALUE_LOCKED_SIM;
            case NETWORK_LOCKED_PUK: return IccCardConstants.INTENT_VALUE_LOCKED_NETWORK_PUK;
            case NETWORK_SUBSET_LOCKED_PUK: return IccCardConstants.INTENT_VALUE_LOCKED_NS_PUK;
            case SERVICE_PROVIDER_LOCKED_PUK: return IccCardConstants.INTENT_VALUE_LOCKED_SP_PUK;
            case CORPORATE_LOCKED_PUK: return IccCardConstants.INTENT_VALUE_LOCKED_CP_PUK;
            case SIM_LOCKED_PUK: return IccCardConstants.INTENT_VALUE_LOCKED_SIM_PUK;
            case SIM_LOCKED_FOREVER: return IccCardConstants.INTENT_VALUE_LOCKED_FOREVER;
            case PERM_DISABLED: return IccCardConstants.INTENT_VALUE_ABSENT_ON_PERM_DISABLED;
            case CARD_IO_ERROR: return IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR;
            default: return null;
       }
    }

    /* IccCard interface implementation */
    @Override
    public State getState() {
        synchronized (mLock) {
            return mExternalState;
        }
    }

    @Override
    public IccRecords getIccRecords() {
        synchronized (mLock) {
            return mIccRecords;
        }
    }

    @Override
    public IccFileHandler getIccFileHandler() {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                return mUiccApplication.getIccFileHandler();
            }
            return null;
        }
    }

    /**
     * Notifies handler of any transition into State.ABSENT
     */
    @Override
    public void registerForAbsent(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mAbsentRegistrants.add(r);

            if (getState() == State.ABSENT) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForAbsent(Handler h) {
        synchronized (mLock) {
            mAbsentRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.NETWORK_LOCKED
     */
    @Override
    public void registerForNetworkLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mNetworkLockedRegistrants.add(r);

            if (getState() == State.NETWORK_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForNetworkLocked(Handler h) {
        synchronized (mLock) {
            mNetworkLockedRegistrants.remove(h);
        }
    }

    /**
     * Notifies handler of any transition into State.isPinLocked()
     */
    @Override
    public void registerForLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);

            mPinLockedRegistrants.add(r);

            if (getState().isPinLocked()) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForLocked(Handler h) {
        synchronized (mLock) {
            mPinLockedRegistrants.remove(h);
        }
    }

    /**
     * SPRD:add for sim lock
     */
    @Override
    public void registerForNetworkSubsetLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mNetworkSubsetLockedRegistrants.add(r);
            if (getState() == State.NETWORK_SUBSET_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForNetworkSubsetLocked(Handler h) {
        synchronized (mLock) {
            mNetworkSubsetLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForCorporateLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mCorporateLockedRegistrants.add(r);
            if (getState() == State.CORPORATE_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForCorporateLocked(Handler h) {
        synchronized (mLock) {
            mCorporateLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForServiceProviderLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mServiceProviderLockedRegistrants.add(r);
            if (getState() == State.SERVICE_PROVIDER_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForServiceProviderLocked(Handler h) {
        synchronized (mLock) {
            mServiceProviderLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForSimLocked(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mSimLockedRegistrants.add(r);
            if (getState() == State.SIM_LOCKED) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForSimLocked(Handler h) {
        synchronized (mLock) {
            mSimLockedRegistrants.remove(h);
        }
    }

    @Override
    public void registerForSimLockedForever(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mSimLockedForeverRegistrants.add(r);
            if (getState() == State.SIM_LOCKED_FOREVER) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForSimLockedForever(Handler h) {
        synchronized (mLock) {
            mSimLockedForeverRegistrants.remove(h);
        }
    }

    @Override
    public void registerForNetworkLockedPuk(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mNetworkLockedPukRegistrants.add(r);
            if (getState() == State.NETWORK_LOCKED_PUK) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForNetworkLockedPuk(Handler h) {
        synchronized (mLock) {
            mNetworkLockedPukRegistrants.remove(h);
        }
    }

    @Override
    public void registerForSimLockedPuk(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mSimLockedPukRegistrants.add(r);
            if (getState() == State.SIM_LOCKED_PUK) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForSimLockedPuk(Handler h) {
        synchronized (mLock) {
            mSimLockedPukRegistrants.remove(h);
        }
    }

    @Override
    public void registerForNetworkSubsetLockedPuk(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mNetworkSubsetLockedPukRegistrants.add(r);
            if (getState() == State.NETWORK_SUBSET_LOCKED_PUK) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForNetworkSubsetLockedPuk(Handler h) {
        synchronized (mLock) {
            mNetworkSubsetLockedPukRegistrants.remove(h);
        }
    }

    @Override
    public void registerForCorporateLockedPuk(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mCorporateLockedPukRegistrants.add(r);
            if (getState() == State.CORPORATE_LOCKED_PUK) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForCorporateLockedPuk(Handler h) {
        synchronized (mLock) {
            mCorporateLockedPukRegistrants.remove(h);
        }
    }

    @Override
    public void registerForServiceProviderLockedPuk(Handler h, int what, Object obj) {
        synchronized (mLock) {
            Registrant r = new Registrant (h, what, obj);
            mServiceProviderLockedPukRegistrants.add(r);
            if (getState() == State.SERVICE_PROVIDER_LOCKED_PUK) {
                r.notifyRegistrant();
            }
        }
    }

    @Override
    public void unregisterForServiceProviderLockedPuk(Handler h) {
        synchronized (mLock) {
            mServiceProviderLockedPukRegistrants.remove(h);
        }
    }

    @Override
    public void setNetworkSubsetLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setNetworkSubsetLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("NetworkSubsetLock");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setNetworkLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setNetworkLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("NetworkLock");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setServiceProviderLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setServiceProviderLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ServiceProviderLock");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setCorporateLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setCorporateLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CorporateLock");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setSimLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setSimLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("SimLock");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setNetworkLockPukEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setNetworkLockPukEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("NetworkLock Puk");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setNetworkSubsetLockPukEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setNetworkSubsetLockPukEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("NetworkSubsetLock Puk");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setServiceProviderLockPukEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setServiceProviderLockPukEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ServiceProviderLock Puk");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setCorporateLockPukEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setCorporateLockPukEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CorporateLock Puk");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setSimLockPukEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setSimLockPukEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("SimLock Puk");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }
    /** @} */

    @Override
    public void supplyPin(String pin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPin(pin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPuk(String puk, String newPin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPuk(puk, newPin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPin2(String pin2, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPin2(pin2, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyPuk2(String puk2, String newPin2, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyPuk2(puk2, newPin2, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void supplyNetworkDepersonalization(String pin, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.supplyNetworkDepersonalization(pin, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("CommandsInterface is not set.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public boolean getIccLockEnabled() {
        synchronized (mLock) {
            /* defaults to false, if ICC is absent/deactivated */
            Boolean retValue = mUiccApplication != null ?
                    mUiccApplication.getIccLockEnabled() : false;
            return retValue;
        }
    }

    @Override
    public boolean getIccFdnEnabled() {
        synchronized (mLock) {
            Boolean retValue = mUiccApplication != null ?
                    mUiccApplication.getIccFdnEnabled() : false;
            return retValue;
        }
    }

    public boolean getIccFdnAvailable() {
        boolean retValue = mUiccApplication != null ? mUiccApplication.getIccFdnAvailable() : false;
        return retValue;
    }

    public boolean getIccPin2Blocked() {
        /* defaults to disabled */
        Boolean retValue = mUiccApplication != null ? mUiccApplication.getIccPin2Blocked() : false;
        return retValue;
    }

    public boolean getIccPuk2Blocked() {
        /* defaults to disabled */
        Boolean retValue = mUiccApplication != null ? mUiccApplication.getIccPuk2Blocked() : false;
        return retValue;
    }

    @Override
    public void setIccLockEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccLockEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void setIccFdnEnabled(boolean enabled, String password, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.setIccFdnEnabled(enabled, password, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void changeIccLockPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.changeIccLockPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public void changeIccFdnPassword(String oldPassword, String newPassword, Message onComplete) {
        synchronized (mLock) {
            if (mUiccApplication != null) {
                mUiccApplication.changeIccFdnPassword(oldPassword, newPassword, onComplete);
            } else if (onComplete != null) {
                Exception e = new RuntimeException("ICC card is absent.");
                AsyncResult.forMessage(onComplete).exception = e;
                onComplete.sendToTarget();
                return;
            }
        }
    }

    @Override
    public String getServiceProviderName() {
        synchronized (mLock) {
            if (mIccRecords != null) {
                return mIccRecords.getServiceProviderName();
            }
            return null;
        }
    }

    @Override
    public boolean isApplicationOnIcc(IccCardApplicationStatus.AppType type) {
        synchronized (mLock) {
            Boolean retValue = mUiccCard != null ? mUiccCard.isApplicationOnIcc(type) : false;
            return retValue;
        }
    }

    @Override
    public boolean hasIccCard() {
        synchronized (mLock) {
            if (mUiccCard != null && mUiccCard.getCardState() != CardState.CARDSTATE_ABSENT) {
                return true;
            }
            return false;
        }
    }

    private void setSystemProperty(String property, String value) {
        TelephonyManager.setTelephonyProperty(mPhoneId, property, value);
    }

    public IccRecords getIccRecord() {
        return mIccRecords;
    }
    private void log(String s) {
        Rlog.d(LOG_TAG, s);
    }

    private void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("IccCardProxy: " + this);
        pw.println(" mContext=" + mContext);
        pw.println(" mCi=" + mCi);
        pw.println(" mAbsentRegistrants: size=" + mAbsentRegistrants.size());
        for (int i = 0; i < mAbsentRegistrants.size(); i++) {
            pw.println("  mAbsentRegistrants[" + i + "]="
                    + ((Registrant)mAbsentRegistrants.get(i)).getHandler());
        }
        pw.println(" mPinLockedRegistrants: size=" + mPinLockedRegistrants.size());
        for (int i = 0; i < mPinLockedRegistrants.size(); i++) {
            pw.println("  mPinLockedRegistrants[" + i + "]="
                    + ((Registrant)mPinLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mNetworkLockedRegistrants: size=" + mNetworkLockedRegistrants.size());
        for (int i = 0; i < mNetworkLockedRegistrants.size(); i++) {
            pw.println("  mNetworkLockedRegistrants[" + i + "]="
                    + ((Registrant)mNetworkLockedRegistrants.get(i)).getHandler());
        }
        pw.println(" mCurrentAppType=" + mCurrentAppType);
        pw.println(" mUiccController=" + mUiccController);
        pw.println(" mUiccCard=" + mUiccCard);
        pw.println(" mUiccApplication=" + mUiccApplication);
        pw.println(" mIccRecords=" + mIccRecords);
        pw.println(" mCdmaSSM=" + mCdmaSSM);
        pw.println(" mRadioOn=" + mRadioOn);
        pw.println(" mQuietMode=" + mQuietMode);
        pw.println(" mInitialized=" + mInitialized);
        pw.println(" mExternalState=" + mExternalState);

        pw.flush();
    }
}
