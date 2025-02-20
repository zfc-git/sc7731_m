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
package com.android.internal.telephony;

import android.telephony.TelephonyManager;

/**
 * {@hide}
 */
public class IccCardConstants {

    /* The extra data for broadcasting intent INTENT_ICC_STATE_CHANGE */
    public static final String INTENT_KEY_ICC_STATE = "ss";
    /* UNKNOWN means the ICC state is unknown */
    public static final String INTENT_VALUE_ICC_UNKNOWN = "UNKNOWN";
    /* NOT_READY means the ICC interface is not ready (eg, radio is off or powering on) */
    public static final String INTENT_VALUE_ICC_NOT_READY = "NOT_READY";
    /* ABSENT means ICC is missing */
    public static final String INTENT_VALUE_ICC_ABSENT = "ABSENT";
    /* CARD_IO_ERROR means for three consecutive times there was SIM IO error */
    static public final String INTENT_VALUE_ICC_CARD_IO_ERROR = "CARD_IO_ERROR";
    /* LOCKED means ICC is locked by pin or by network */
    public static final String INTENT_VALUE_ICC_LOCKED = "LOCKED";
    //TODO: we can remove this state in the future if Bug 18489776 analysis
    //#42's first race condition is resolved
    /* INTERNAL LOCKED means ICC is locked by pin or by network */
    public static final String INTENT_VALUE_ICC_INTERNAL_LOCKED = "INTERNAL_LOCKED";
    /* READY means ICC is ready to access */
    public static final String INTENT_VALUE_ICC_READY = "READY";
    /* IMSI means ICC IMSI is ready in property */
    public static final String INTENT_VALUE_ICC_IMSI = "IMSI";
    /* LOADED means all ICC records, including IMSI, are loaded */
    public static final String INTENT_VALUE_ICC_LOADED = "LOADED";
    /* The extra data for broadcasting intent INTENT_ICC_STATE_CHANGE */
    public static final String INTENT_KEY_LOCKED_REASON = "reason";
    /* PIN means ICC is locked on PIN1 */
    public static final String INTENT_VALUE_LOCKED_ON_PIN = "PIN";
    /* PUK means ICC is locked on PUK1 */
    public static final String INTENT_VALUE_LOCKED_ON_PUK = "PUK";
    /* NETWORK means ICC is locked on NETWORK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_NETWORK = "NETWORK";
    /* NETWORK means ICC is locked on NETWORK SUBSET PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_NS = "NS";
    /* NETWORK means ICC is locked on SERVICE PROVIDER PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_SP = "SP";
    /* NETWORK means ICC is locked on CORPRATE PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_CP = "CP";
    /* NETWORK means ICC is locked on SIM PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_SIM = "SIM";
    /* NETWORK means ICC is locked on NETWORK PUK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_NETWORK_PUK = "NETWORK_PUK";
    /* NETWORK means ICC is locked on NETWORK SUBSET PUK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_NS_PUK = "NS_PUK";
    /* NETWORK means ICC is locked on SERVICE PROVIDER PUK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_SP_PUK = "SP_PUK";
    /* NETWORK means ICC is locked on CORPRATE PUK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_CP_PUK = "CP_PUK";
    /* NETWORK means ICC is locked on SIM PUK PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_SIM_PUK = "SIM_PUK";
    /* NETWORK means ICC is locked on FOREVER PERSONALIZATION */
    public static final String INTENT_VALUE_LOCKED_FOREVER = "FOREVER";

    /* PERM_DISABLED means ICC is permanently disabled due to puk fails */
    public static final String INTENT_VALUE_ABSENT_ON_PERM_DISABLED = "PERM_DISABLED";

    /* SPRD: BUG 510153 Add for FDN @{ */
    public static final String INTENT_KEY_FDN_STATUS = "fdn_status";
    /* @} */

    /**
     * This is combination of IccCardStatus.CardState and IccCardApplicationStatus.AppState
     * for external apps (like PhoneApp) to use
     *
     * UNKNOWN is a transient state, for example, after user inputs ICC pin under
     * PIN_REQUIRED state, the query for ICC status returns UNKNOWN before it
     * turns to READY
     *
     * The ordinal values much match {@link TelephonyManager#SIM_STATE_UNKNOWN} ...
     */
    public enum State {
        UNKNOWN,        /** ordinal(0) == {@See TelephonyManager#SIM_STATE_UNKNOWN} */
        ABSENT,         /** ordinal(1) == {@See TelephonyManager#SIM_STATE_ABSENT} */
        PIN_REQUIRED,   /** ordinal(2) == {@See TelephonyManager#SIM_STATE_PIN_REQUIRED} */
        PUK_REQUIRED,   /** ordinal(3) == {@See TelephonyManager#SIM_STATE_PUK_REQUIRED} */
        NETWORK_LOCKED, /** ordinal(4) == {@See TelephonyManager#SIM_STATE_NETWORK_LOCKED} */
        READY,          /** ordinal(5) == {@See TelephonyManager#SIM_STATE_READY} */
        NOT_READY,      /** ordinal(6) == {@See TelephonyManager#SIM_STATE_NOT_READY} */
        PERM_DISABLED,  /** ordinal(7) == {@See TelephonyManager#SIM_STATE_PERM_DISABLED} */
        CARD_IO_ERROR,  /** ordinal(8) == {@See TelephonyManager#SIM_STATE_CARD_IO_ERROR} */
        /** Add for sim lock @{ */
        NETWORK_SUBSET_LOCKED,
        SERVICE_PROVIDER_LOCKED,
        CORPORATE_LOCKED,
        SIM_LOCKED,
        NETWORK_LOCKED_PUK,
        NETWORK_SUBSET_LOCKED_PUK,
        SERVICE_PROVIDER_LOCKED_PUK,
        CORPORATE_LOCKED_PUK,
        SIM_LOCKED_PUK,
        SIM_LOCKED_FOREVER;
        /** @} */

        public boolean isPinLocked() {
            return ((this == PIN_REQUIRED) || (this == PUK_REQUIRED));
        }

        public boolean iccCardExist() {
            return ((this == PIN_REQUIRED) || (this == PUK_REQUIRED)
                    || (this == NETWORK_LOCKED) || (this == READY)
                    || (this == NETWORK_SUBSET_LOCKED) || (this == SERVICE_PROVIDER_LOCKED)
                    || (this == CORPORATE_LOCKED) || (this == SIM_LOCKED)
                    || (this == NETWORK_LOCKED_PUK) || (this == NETWORK_SUBSET_LOCKED_PUK)
                    || (this == SERVICE_PROVIDER_LOCKED_PUK) || (this == CORPORATE_LOCKED_PUK)
                    || (this == SIM_LOCKED_PUK) || (this == SIM_LOCKED_FOREVER)
                    || (this == PERM_DISABLED) || (this == CARD_IO_ERROR));
        }

        public static State intToState(int state) throws IllegalArgumentException {
            switch(state) {
                case 0: return UNKNOWN;
                case 1: return ABSENT;
                case 2: return PIN_REQUIRED;
                case 3: return PUK_REQUIRED;
                case 4: return NETWORK_LOCKED;
                case 5: return READY;
                case 6: return NOT_READY;
                case 7: return PERM_DISABLED;
                case 8: return CARD_IO_ERROR;
                case 9: return NETWORK_SUBSET_LOCKED;
                case 10: return SERVICE_PROVIDER_LOCKED;
                case 11: return CORPORATE_LOCKED;
                case 12: return SIM_LOCKED;
                case 13: return NETWORK_LOCKED_PUK;
                case 14: return NETWORK_SUBSET_LOCKED_PUK;
                case 15: return SERVICE_PROVIDER_LOCKED_PUK;
                case 16: return CORPORATE_LOCKED_PUK;
                case 17: return SIM_LOCKED_PUK;
                case 18: return SIM_LOCKED_FOREVER;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
