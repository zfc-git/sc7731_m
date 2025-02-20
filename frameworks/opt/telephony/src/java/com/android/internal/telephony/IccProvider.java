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

import android.content.ContentProvider;
import android.content.UriMatcher;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.telephony.Rlog;

import java.util.List;

import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import com.android.internal.telephony.IccPBForMimetypeException;
import com.android.internal.telephony.IccPBForRecordException;
import com.android.internal.telephony.IccPhoneBookOperationException;
/**
 * {@hide}
 */
public class IccProvider extends ContentProvider {
    private static final String TAG = "IccProvider";
    private static final boolean DBG = true;


    private static final String[] ADDRESS_BOOK_COLUMN_NAMES = new String[] {
        "name","number","emails",/* SPRD:add SIMPhoneBook for bug 474587 @{ */"anr", "aas", "sne", "grp", "gas",
        "index",/*@}*/"_id"
    };

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    private static final String[] FDN_S_COLUMN_NAMES = new String[] { "size" };
    private static final String[] SIM_GROUP_PROJECTION = new String[] { "gas",
            "index" };
    /*@}*/

    /* SPRD: PhoneBook for AAS {@ */
    private static final String[] SIM_AAS_PROJECTION = new String[] {
        "aas", "index"
    };
    /* @} */

    protected static final int ADN = 1;
    protected static final int ADN_SUB = 2;
    protected static final int FDN = 3;
    protected static final int FDN_SUB = 4;
    protected static final int SDN = 5;
    protected static final int SDN_SUB = 6;
    protected static final int ADN_ALL = 7;
    /* SPRD:add SIMPhoneBook for bug 474587 @{ */
    private static final int FDN_S = 8;
    private static final int FDN_S_SUB = 9;
    private static final int GAS = 10;
    private static final int GAS_SUB = 11;
    private static final int LND = 12;
    private static final int LND_SUB = 13;
    /*@}*/
    /* SPRD: PhoneBook for AAS {@ */
    private static final int AAS = 14;
    private static final int AAS_SUB = 15;
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    private static final int SNE_S = 16;
    private static final int SNE_S_SUB = 17;
    /* @} */
    //protected static final String STR_TAG = "tag";
    //protected static final String STR_NUMBER = "number";
    //protected static final String STR_EMAILS = "emails";
    //protected static final String STR_PIN2 = "pin2";
    /* SPRD: add SIMPhoneBook for bug 474587@{ */
    private static final String STR_TAG = "tag";
    private static final String STR_NUMBER = "number";
    private static final String STR_EMAILS = "email";
    private static final String STR_PIN2 = "pin2";
    private static final String STR_ANR = "anr";
    private static final String STR_AAS = "aas";
    private static final String STR_SNE = "sne";
    private static final String STR_GRP = "grp";
    private static final String STR_GAS = "gas";
    private static final String STR_INDEX = "index";
    private static final String STR_NEW_TAG = "newTag";
    private static final String STR_NEW_NUMBER = "newNumber";

    private static final String AUTHORITY = "icc";
    private static final String CONTENT_URI = "content://" + AUTHORITY + "/";
    public static final String WITH_EXCEPTION = "with_exception";
    /*@}*/
    private static final UriMatcher URL_MATCHER =
                            new UriMatcher(UriMatcher.NO_MATCH);

    static {
        /* SPRD: add SIMPhoneBook for bug 474587@{ */
        /*URL_MATCHER.addURI("icc", "adn", ADN);
        URL_MATCHER.addURI("icc", "adn/subId/#", ADN_SUB);
        URL_MATCHER.addURI("icc", "fdn", FDN);
        URL_MATCHER.addURI("icc", "fdn/subId/#", FDN_SUB);
        URL_MATCHER.addURI("icc", "sdn", SDN);
        URL_MATCHER.addURI("icc", "sdn/subId/#", SDN_SUB);*/
        URL_MATCHER.addURI(AUTHORITY, "adn", ADN);
        URL_MATCHER.addURI(AUTHORITY, "adn/subId/#", ADN_SUB);
        URL_MATCHER.addURI(AUTHORITY, "fdn", FDN);
        URL_MATCHER.addURI(AUTHORITY, "fdn/subId/#", FDN_SUB);
        URL_MATCHER.addURI(AUTHORITY, "sdn", SDN);
        URL_MATCHER.addURI(AUTHORITY, "sdn/subId/#", SDN_SUB);
        URL_MATCHER.addURI(AUTHORITY, "fdn_s", FDN_S);
        URL_MATCHER.addURI(AUTHORITY, "fdn_s/subId/#", FDN_S_SUB);
        URL_MATCHER.addURI(AUTHORITY, "gas", GAS);
        URL_MATCHER.addURI(AUTHORITY, "gas/subId/#", GAS_SUB);
        URL_MATCHER.addURI(AUTHORITY, "lnd", LND);
        URL_MATCHER.addURI(AUTHORITY, "lnd/subId/#", LND_SUB);
        /* @} */
        // SPRD: PhoneBook for AAS
        URL_MATCHER.addURI(AUTHORITY, "aas", AAS);
        URL_MATCHER.addURI(AUTHORITY, "aas/subId/#", AAS_SUB);
        // SPRD: PhoneBook for SNE
        URL_MATCHER.addURI(AUTHORITY, "sne", SNE_S);
        URL_MATCHER.addURI(AUTHORITY, "sne/subId/#", SNE_S_SUB);
    }

    private SubscriptionManager mSubscriptionManager;

    @Override
    public boolean onCreate() {
        mSubscriptionManager = SubscriptionManager.from(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sort) {
        if (DBG) log("query");

        switch (URL_MATCHER.match(url)) {
            case ADN:
                return loadFromEf(IccConstants.EF_ADN, SubscriptionManager.getDefaultSubId());

            case ADN_SUB:
                return loadFromEf(IccConstants.EF_ADN, getRequestSubId(url));

            case FDN:
                return loadFromEf(IccConstants.EF_FDN, SubscriptionManager.getDefaultSubId());

            case FDN_SUB:
                return loadFromEf(IccConstants.EF_FDN, getRequestSubId(url));

            case SDN:
                return loadFromEf(IccConstants.EF_SDN, SubscriptionManager.getDefaultSubId());

            case SDN_SUB:
                return loadFromEf(IccConstants.EF_SDN, getRequestSubId(url));

            case ADN_ALL:
                return loadAllSimContacts(IccConstants.EF_ADN);

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case FDN_S:
                return getEfSize(IccConstants.EF_FDN,
                        SubscriptionManager.getDefaultSubId());

            case FDN_S_SUB:
                return getEfSize(IccConstants.EF_FDN, getRequestSubId(url));

            case LND:
                return loadFromEf(IccConstants.EF_LND,
                        SubscriptionManager.getDefaultSubId());

            case LND_SUB:
                return loadFromEf(IccConstants.EF_LND, getRequestSubId(url));

            case GAS:
                return loadGas(SubscriptionManager.getDefaultSubId());

            case GAS_SUB:
                return loadGas(getRequestSubId(url));
            /*@}*/
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
                return loadAas(SubscriptionManager.getDefaultSubId());

            case AAS_SUB:
                return loadAas(getRequestSubId(url));
            /* @} */
            /* SPRD: PhoneBook for SNE {@ */
            case SNE_S:
                return getSneSize(SubscriptionManager.getDefaultSubId());

            case SNE_S_SUB:
                return getSneSize(getRequestSubId(url));
            /* @} */
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    private Cursor loadAllSimContacts(int efType) {
        Cursor [] result;
        List<SubscriptionInfo> subInfoList = mSubscriptionManager.getActiveSubscriptionInfoList();

        if ((subInfoList == null) || (subInfoList.size() == 0)) {
            result = new Cursor[0];
        } else {
            int subIdCount = subInfoList.size();
            result = new Cursor[subIdCount];
            int subId;

            for (int i = 0; i < subIdCount; i++) {
                subId = subInfoList.get(i).getSubscriptionId();
                result[i] = loadFromEf(efType, subId);
                Rlog.i(TAG,"ADN Records loaded for Subscription ::" + subId);
            }
        }

        return new MergeCursor(result);
    }

    @Override
    public String getType(Uri url) {
        switch (URL_MATCHER.match(url)) {
            case ADN:
            case ADN_SUB:
            case FDN:
            case FDN_SUB:
            case SDN:
            case SDN_SUB:
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
            case AAS_SUB:
            /* @} */
            case ADN_ALL:
                return "vnd.android.cursor.dir/sim-contact";

            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    @Override
    public Uri insert(Uri url, ContentValues initialValues) {
        Uri resultUri;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //int efType;
        int efType = -1;
        boolean isGas = false;
        int index = -1;
        /*@}*/
        String pin2 = null;
        int subId;
        //SPRD: PhoneBook for AAS
        boolean isAas = false;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //if (DBG) log("insert");
        if (DBG)
            log("insert, uri:" + url + " initialValues:" + initialValues);
        /*@}*/

        int match = URL_MATCHER.match(url);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                subId = SubscriptionManager.getDefaultSubId();
                break;

            case ADN_SUB:
                efType = IccConstants.EF_ADN;
                subId = getRequestSubId(url);
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = initialValues.getAsString("pin2");
                break;

            case FDN_SUB:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = initialValues.getAsString("pin2");
                break;

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case LND:
                efType = IccConstants.EF_LND;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case LND_SUB:
                efType = IccConstants.EF_LND;
                subId = getRequestSubId(url);
                break;
            case GAS:
                isGas = true;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case GAS_SUB:
                isGas = true;
                subId = getRequestSubId(url);
                break;
            /*@}*/
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
                isAas = true;
                subId = SubscriptionManager.getDefaultSubId();
                break;
            case AAS_SUB:
                isAas = true;
                subId = getRequestSubId(url);
                break;
            /* @} */
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        String tag = initialValues.getAsString("tag");
        String number = initialValues.getAsString("number");
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        String mEmail = initialValues.getAsString("email");
        String[] emails = null;
        if (mEmail != null) {
            emails = new String[1];
            emails[0] = mEmail;
        }

        String anr = initialValues.getAsString("anr");
        String aas = initialValues.getAsString("aas");
        String sne = initialValues.getAsString("sne");
        String grp = initialValues.getAsString("grp");
        String gas = initialValues.getAsString("gas");
        //SPRD: PhoneBook for AAS
        aas = (aas == null) ? "" : aas;

        if (DBG)
            log("insert, tag:" + tag + ",  number:" + number + ",  anr:" + anr
                    + ",  aas:" + aas + ",  sne:" + sne + ",  grp:" + grp
                    + ",  gas:" + gas + ",  Email:"
                    + (emails == null ? "null" : emails[0]));

       if (isGas) {
            index = addUsimGroup(gas, subId);
        }/* SPRD: PhoneBook for AAS {@ */else if(isAas) {
            index = addUsimAas(aas, subId);
            if (index < 0) {
                if(index == IccPhoneBookOperationException.AAS_CAPACITY_FULL) {
                    return Uri.parse("aas/aas_full");
                } else if(index == IccPhoneBookOperationException.OVER_AAS_MAX_LENGTH) {
                    return Uri.parse("aas/over_aas_max_length");
                }
                return null;
            }
        } /* @} */else {
            index = addIccRecordToEf(efType, tag, number, emails, anr, aas,
                    sne, grp, gas, pin2, subId);
        }
        if (index < 0) {
            int errorCode = index;
            log("insert error =  " + errorCode);
            if (url.getBooleanQueryParameter(WITH_EXCEPTION, false)) {
                Rlog.d(TAG, "throw exception");
                throwException(errorCode);
            }
            return null;
        }
        /*@}*/
        /* @orig
         * // TODO(): Read email instead of sending null.
         *  boolean success = addIccRecordToEf(efType, tag, number, null, pin2, subId);

         * if (!success) {
         *   return null;
         *  }
        */
        StringBuilder buf = new StringBuilder("content://icc/");
        switch (match) {
            case ADN:
                buf.append("adn/");
                break;

            case ADN_SUB:
                buf.append("adn/subId/");
                break;

            case FDN:
                buf.append("fdn/");
                break;

            case FDN_SUB:
                buf.append("fdn/subId/");
                break;

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case LND:
                buf.append("lnd/");
                break;

            case LND_SUB:
                buf.append("lnd/subId");
                break;

            case GAS:
                buf.append("gas/");
                break;

            case GAS_SUB:
                buf.append("gas/subId");
                break;
            /*@}*/
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
                buf.append("aas/");
                break;

            case AAS_SUB:
                buf.append("aas/subId");
                break;
            /* @} */
        }

        // TODO: we need to find out the rowId for the newly added record
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //buf.append(0);
        buf.append("/" + index);
        /*@}*/
        resultUri = Uri.parse(buf.toString());

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        // getContext().getContentResolver().notifyChange(url, null);
        if (DBG)
            log("insert resultUri  " + resultUri);
        if (resultUri != null) {
            getContext().getContentResolver().notifyChange(resultUri, null,
                    false);
        }
        /*@}*/
        /*
        // notify interested parties that an insertion happened
        getContext().getContentResolver().notifyInsert(
                resultUri, rowID, null);
        */

        return resultUri;
    }

    private String normalizeValue(String inVal) {
        int len = inVal.length();
        // If name is empty in contact return null to avoid crash.
        if (len == 0) {
            if (DBG) log("len of input String is 0");
            return inVal;
        }
        String retVal = inVal;

        if (inVal.charAt(0) == '\'' && inVal.charAt(len-1) == '\'') {
            retVal = inVal.substring(1, len-1);
        }

        return retVal;
    }

    @Override
    public int delete(Uri url, String where, String[] whereArgs) {
        int efType;
        int subId;
        //SPRD: PhoneBook for AAS
        boolean isAas = false;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        boolean isFdn = false;
        boolean isGas = false;

        if (DBG)
            log("delete");

        Rlog.i(TAG, "delete, uri:" + url + " where:" + where + " whereArgs:"
                + whereArgs);
        /* @} */
        int match = URL_MATCHER.match(url);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                subId = SubscriptionManager.getDefaultSubId();
                break;

            case ADN_SUB:
                efType = IccConstants.EF_ADN;
                subId = getRequestSubId(url);
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                //SPRD: add for bug 495624
                isFdn = true;
                break;

            case FDN_SUB:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
              //SPRD: add for bug 495624
                isFdn = true;
                break;

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case GAS:
                efType = IccConstants.EF_ADN;
                subId = SubscriptionManager.getDefaultSubId();
                isGas = true;
                break;

            case GAS_SUB:
                efType = IccConstants.EF_ADN;
                subId = getRequestSubId(url);
                isGas = true;
                break;
            /* @} */
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
                efType = IccConstants.EF_ADN;
                subId = SubscriptionManager.getDefaultSubId();
                isAas = true;
                break;

            case AAS_SUB:
                efType = IccConstants.EF_ADN;
                subId = getRequestSubId(url);
                isAas = true;
                break;
            /* @} */
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        if (DBG) log("delete");

        // parse where clause
        String tag = null;
        String number = null;
        String[] emails = null;
        String pin2 = null;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        int index = -1;
        boolean success = false;
        /* SPRD: PhoneBook for AAS {@ */
        if (isAas) {
            log("delete AAS");
            index = Integer.parseInt(where);
            int result = updateUsimAasByIndex("", index, subId);
            return result < 0 ? 0 : 1;
        }
        /* @} */

        if (whereArgs == null || whereArgs.length == 0) {
        /* @} */
            String[] tokens = where.split("AND");
            int n = tokens.length;
            while (--n >= 0) {
                   String param = tokens[n];
                   if (DBG) log("parsing '" + param + "'");

                   String[] pair = param.split("=");

                   if (pair.length != 2) {
                        Rlog.e(TAG, "resolve: bad whereClause parameter: " + param);
                        continue;
                   }
                   String key = pair[0].trim();
                   String val = pair[1].trim();

                   if (STR_TAG.equals(key)) {
                      tag = normalizeValue(val);
                   } else if (STR_NUMBER.equals(key)) {
                      number = normalizeValue(val);
                   } else if (STR_EMAILS.equals(key)) {
                    //TODO(): Email is null.
                     emails = null;
                   } else if (STR_PIN2.equals(key)) {
                      pin2 = normalizeValue(val);
                   }/* SPRD: add SIMPhoneBook for bug 474587 @{ */
                   else if (STR_INDEX.equals(key)) {
                       index = Integer.valueOf(normalizeValue(val));
                   }
                   /* @} */
            }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        }else {
            tag = whereArgs[0];
            number = whereArgs[1];
            pin2 = whereArgs[2];
        }
        /* @} */
        if (efType == FDN && TextUtils.isEmpty(pin2)) {
            return 0;
        }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        /*boolean success = deleteIccRecordFromEf(efType, tag, number, emails, pin2, subId);
        if (!success) {
            return 0;
        }

        getContext().getContentResolver().notifyChange(url, null);
        return 1;*/
        if (DBG)
            log("delete tag: " + tag + ", number:" + number + ", index:"
                    + index);

        if (isFdn) {
            if (-1 != deleteIccRecordFromEf(efType, tag, number, null, "", "",
                    "", pin2, subId)){
                success = true;
            }
        } else if (isGas) {
            int result = updateUsimGroupById("", index, subId);
            success = result < 0 ? false : true;
        } else {
            // use the default method to delete iccRecord,because the 3rd app
            // will not use index
            if (index == -1) {
                Rlog.d(TAG, "the 3rd app will not use index");
                success = deleteIccRecordFromEf(efType, tag, number, null, pin2, subId);
            } else {
                int recIndex = -1;
                recIndex = deleteIccRecordFromEfByIndex(efType, index, pin2,
                        subId);
                if (recIndex < 0) {
                    success = false;
                } else {
                    success = true;
                }
            }
        }
        if (DBG)
            log("delete result: " + success);

        if (!success) {
            return 0;
        } else {
            getContext().getContentResolver().notifyChange(url, null);
        }
        return 1;
        /* @} */
    }

    @Override
    public int update(Uri url, ContentValues values, String where, String[] whereArgs) {
        String pin2 = null;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        int efType = -1;
        int subId;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        boolean isFdn = false;
        boolean isGas = false;
        /* @} */
        //SPRD: PhoneBook for AAS
        boolean isAas = false;
        if (DBG) log("update");
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        Rlog.i(TAG, "update, uri:" + url + " where: " + where + " value: "
                + values);
        /* @} */

        int match = URL_MATCHER.match(url);
        switch (match) {
            case ADN:
                efType = IccConstants.EF_ADN;
                subId = SubscriptionManager.getDefaultSubId();
                break;

            case ADN_SUB:
                efType = IccConstants.EF_ADN;
                subId = getRequestSubId(url);
                break;

            case FDN:
                efType = IccConstants.EF_FDN;
                subId = SubscriptionManager.getDefaultSubId();
                pin2 = values.getAsString("pin2");
                //SPRD: add for bug 495624
                isFdn = true;
                break;

            case FDN_SUB:
                efType = IccConstants.EF_FDN;
                subId = getRequestSubId(url);
                pin2 = values.getAsString("pin2");
                //SPRD: add for bug 495624
                isFdn = true;
                break;

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case GAS:
                subId = SubscriptionManager.getDefaultSubId();
                isGas = true;
                break;

            case GAS_SUB:
                subId = getRequestSubId(url);
                isGas = true;
                break;
            /* @} */
            /* SPRD: PhoneBook for AAS {@ */
            case AAS:
                subId = SubscriptionManager.getDefaultSubId();
                isAas = true;
                break;

            case AAS_SUB:
                subId = getRequestSubId(url);
                isAas = true;
                break;
            /* @} */
            default:
                throw new UnsupportedOperationException(
                        "Cannot insert into URL: " + url);
        }

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //String tag = values.getAsString("tag");
        //String number = values.getAsString("number");
        String[] emails = null;
        String newTag = values.getAsString(STR_TAG);
        String newNumber = values.getAsString(STR_NUMBER);
        Integer index = values.getAsInteger(STR_INDEX); // maybe simIndex or groupId
        String newanr = values.getAsString(STR_ANR);
        String newaas = values.getAsString(STR_AAS);
        String newsne = values.getAsString(STR_SNE);
        String newgrp = values.getAsString(STR_GRP);
        String newgas = values.getAsString(STR_GAS);
        String[] newemails = null;
        //String[] newEmails = null;
        String newEmail = values.getAsString(STR_EMAILS);
        if (newEmail != null) {
            newemails = new String[1];
            newemails[0] = newEmail;
        }
        //SPRD: PhoneBook for AAS
        newaas = newaas == null ? "" : newaas;

        if (DBG)
            log("update, new tag: " + newTag + ",  number:" + newNumber
                    + ",  anr:" + newanr + ",  aas: " + newaas + ",  sne:"
                    + newsne + ",  grp:" + newgrp + ",  gas :" + newgas
                    + ",  email:" + newEmail + ",  index:" + index);

        boolean success = false;
        int recIndex = -1;
        // TODO(): Update for email.
        /*boolean success = updateIccRecordInEf(efType, tag, number,
                newTag, newNumber, pin2, subId);

        if (!success) {
            return 0;
        }

        getContext().getContentResolver().notifyChange(url, null);
        return 1;*/
        if (isFdn) {
            // added for fdn
            String tag = "";
            String number = "";
            tag = values.getAsString(STR_TAG);
            number = values.getAsString(STR_NUMBER);
            newTag = values.getAsString(STR_NEW_TAG);
            newNumber = values.getAsString(STR_NEW_NUMBER);
            if (0 <= updateIccRecordInEf(efType, tag, number, null, "", "", "",
                    newTag, newNumber, null, "", "", "", "", "", pin2, subId))
                success = true;
        } else if (isGas) {
            recIndex = updateUsimGroupById(newgas, index, subId);
            if (recIndex < 0) {
                success = false;
                if (url.getBooleanQueryParameter(WITH_EXCEPTION, false)) {
                    Rlog.d(TAG, "throw exception :recIndex = " + recIndex);
                    throwException(recIndex);
                }
            } else {
                success = true;
            }
        } /* SPRD: PhoneBook for AAS {@ */else if (isAas){
            recIndex = updateUsimAasByIndex(newaas, index, subId);
            return recIndex < 0 ? 0 : 1;
        }/* @} */else {
            recIndex = updateIccRecordInEfByIndex(efType, newTag, newNumber,
                    newemails, newanr, newaas, newsne, newgrp, newgas, index,
                    pin2, subId);
            if (recIndex < 0) {
                success = false;
                if (url.getBooleanQueryParameter(WITH_EXCEPTION, false)) {
                    Rlog.d(TAG, "throw exception :recIndex = " + recIndex);
                    throwException(recIndex);
                }
            } else {
                success = true;
            }
        }
        if (!success) {
            return 0;
        } else {
            getContext().getContentResolver().notifyChange(url, null);
        }

        return 1;
    }

    private MatrixCursor loadFromEf(int efType, int subId) {
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //if (DBG) log("loadFromEf: efType=" + efType + ", subscription=" + subId);
        if (DBG)
            log("loadFromEf: efType=" + Integer.toHexString(efType)
                    + ", subId=" + subId);
        /*@}*/
        List<AdnRecord> adnRecords = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                Rlog.i(TAG,"iccIpb = " + iccIpb);
                /*@}*/
                adnRecords = iccIpb.getAdnRecordsInEfForSubscriber(subId, efType);
            }
        } catch (RemoteException ex) {
            // ignore it
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            Rlog.w(TAG, "RemoteException " + ex.toString());
            /*@}*/
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            Rlog.w(TAG, "SecurityException " + ex.toString());
            /*@}*/
        }

        if (adnRecords != null) {
            // Load the results
            final int N = adnRecords.size();
            final MatrixCursor cursor = new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES, N);
            if (DBG) log("adnRecords.size=" + N);
            for (int i = 0; i < N ; i++) {
                loadRecord(adnRecords.get(i), cursor, i);
            }
            return cursor;
        } else {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            // No results to load
            //Rlog.w(TAG, "Cannot load ADN records");
            Rlog.w(TAG,
                    "Cannot load ADN records efType = "
                            + Integer.toHexString(efType) + ", subId=" + subId);
            /*@}*/
            return new MatrixCursor(ADDRESS_BOOK_COLUMN_NAMES);
        }
    }

    /*SPRD:Modify this method with return values for bug 474587.@{
    private boolean
    addIccRecordToEf(int efType, String name, String number, String[] emails,
            String pin2, int subId) {
    @} */
    private int addIccRecordToEf(int efType, String name, String number,
            String[] emails, String anr, String aas, String sne, String grp,
            String gas, String pin2, int subId) {
        if (DBG) log("addIccRecordToEf: efType=" + efType + ", name=" + name +
                ", number=" + number + ", emails=" + emails + /* SPRD: @{ */", grp=" + grp + ",anr= " + anr/*@}*/ +", subscription=" + subId);

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //boolean success = false;
        int retIndex = -1;
        /* @} */

        // TODO: do we need to call getAdnRecordsInEf() before calling
        // updateAdnRecordsInEfBySearch()? In any case, we will leave
        // the UI level logic to fill that prereq if necessary. But
        // hopefully, we can remove this requirement.

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                /* success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType,
                       "", "", name, number, pin2);*/
                retIndex = iccIpb.updateAdnRecordsInEfBySearchForSubscriberEx(subId,
                        efType, "", "", null, "", "", "", name, number, emails,
                        anr, aas, sne, grp, gas, pin2);
               /* @} */
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        // if (DBG) log("addIccRecordToEf: " + success);
        //return success;
        if (DBG)
            log("addIccRecordToEf: " + retIndex);
        return retIndex;
        /* @} */
    }

    /*SPRD:Modify this method with return values for bug 474587.@{ */
    /*private boolean
    updateIccRecordInEf(int efType, String oldName, String oldNumber,
            String newName, String newNumber, String pin2, int subId) {*/
    private int updateIccRecordInEf(int efType, String oldName,
                String oldNumber, String[] oldEmailList, String oldAnr,
                String oldSne, String oldGrp, String newName, String newNumber,
                String[] newEmailList, String newAnr, String newAas, String newSne,
                String newGrp, String newGas, String pin2, int subId) {
        /*if (DBG) log("updateIccRecordInEf: efType=" + efType +
                ", oldname=" + oldName + ", oldnumber=" + oldNumber +
                ", newname=" + newName + ", newnumber=" + newNumber +
                ", subscription=" + subId);*/
       if (DBG)
            log("updateIccRecordInEf: efType = " + Integer.toHexString(efType)
                    + ",  oldname = " + oldName + ",  oldnumber = " + oldNumber
                    + ",  oldAnr = " + oldAnr + ",  newname = " + newName
                    + ",  newnumber = " + newNumber + ",  newAnr = " + newAnr);

       // boolean success = false;
       int retIndex = -1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                /*success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType, oldName,
                        oldNumber, newName, newNumber, pin2);*/
            retIndex = iccIpb.updateAdnRecordsInEfBySearchForSubscriberEx(subId, efType,
                        oldName, oldNumber, oldEmailList, oldAnr, oldSne,
                        oldGrp, newName, newNumber, newEmailList, newAnr,
                        newAas, newSne, newGrp, newGas, pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        //if (DBG) log("updateIccRecordInEf: " + success);
        //return success;
        if (DBG)
            log("updateIccRecordInEf: " + retIndex);
        return retIndex;
    }

    private boolean deleteIccRecordFromEf(int efType, String name, String number, String[] emails,
            String pin2, int subId) {
        if (DBG) log("deleteIccRecordFromEf: efType=" + efType +
                ", name=" + name + ", number=" + number + ", emails=" + emails +
                ", pin2=" + pin2 + ", subscription=" + subId);

        boolean success = false;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                success = iccIpb.updateAdnRecordsInEfBySearchForSubscriber(subId, efType,
                          name, number, "", "", pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }
        if (DBG) log("deleteIccRecordFromEf: " + success);
        return success;
    }

    /**
     * Loads an AdnRecord into a MatrixCursor. Must be called with mLock held.
     *
     * @param record the ADN record to load from
     * @param cursor the cursor to receive the results
     */
    private void loadRecord(AdnRecord record, MatrixCursor cursor, int id) {
        if (!record.isEmpty()) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //Object[] contact = new Object[4];
            Object[] contact = new Object[ADDRESS_BOOK_COLUMN_NAMES.length];
            /*@}*/
            String alphaTag = record.getAlphaTag();
            String number = record.getNumber();

            if (DBG) log("loadRecord: " + alphaTag + ", " + number + ",");
            contact[0] = alphaTag;
            contact[1] = number;

            String[] emails = record.getEmails();
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            String anr = record.getAnr();
            String aas = record.getAas();
            String sne = record.getSne();
            String grp = record.getGrp();
            String gas = record.getGas();
            if (DBG)
                log("loadRecord: " + alphaTag + ", " + number + "," + anr + ", " + aas
                        + ", " + sne+ ", " + grp+ ", " + gas);

           // yeezone:jinwei get sim index from adn record
            String sim_index = String.valueOf(record.getRecordNumber());
            Rlog.d(TAG, "loadRecord::sim_index = " + sim_index);
            /*@}*/
            if (emails != null) {
                StringBuilder emailString = new StringBuilder();
                for (String email: emails) {
                    /* @orig
                     * if (DBG) log("Adding email:" + email);
                       emailString.append(email);
                       emailString.append(",");
                    */
                    emailString.append(email);
                    break;
                }
                contact[2] = emailString.toString();
            }
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //contact[3] = id;
            contact[3] = anr;
            contact[4] = aas;
            contact[5] = sne;
            contact[6] = grp;
            contact[7] = gas;
            contact[8] = sim_index;
            contact[9] = id;
            /*@}*/
            cursor.addRow(contact);
        }
    }

    private void log(String msg) {
        Rlog.d(TAG, "[IccProvider] " + msg);
    }

    private int getRequestSubId(Uri url) {
        if (DBG) log("getRequestSubId url: " + url);

        try {
            return Integer.parseInt(url.getLastPathSegment());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unknown URL " + url);
        }
    }
    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    /** get the size of FDN. maybe useless**/
    private MatrixCursor getEfSize(int efType, int subId) {
        int[] adnRecordSize = null;
        if (DBG)
            log("getEfSize: efType=" + efType);
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnRecordSize = iccIpb.getAdnRecordsSizeForSubscriber(subId, efType);
            }
        } catch (RemoteException ex) {
            if (DBG)
                log(ex.toString());
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (adnRecordSize != null) {
            // Load the results
            MatrixCursor cursor = new MatrixCursor(FDN_S_COLUMN_NAMES, 1);
            Object[] size = new Object[1];
            size[0] = adnRecordSize[2];
            cursor.addRow(size);
            return cursor;
        } else {
            Rlog.w(TAG, "Cannot load ADN records");
            return new MatrixCursor(FDN_S_COLUMN_NAMES);
        }
    }

    /** load GAS from sim card. maybe useless**/
    private MatrixCursor loadGas(int subId) {
        log("loadGas,subId=" + subId);

        List<String> adnGas = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnGas = iccIpb.getGasInEfForSubscriber(subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }

        if (adnGas != null) {
            // Load the results
            final int N = adnGas.size();
            final MatrixCursor cursor = new MatrixCursor(SIM_GROUP_PROJECTION,
                    N);
            if (DBG)
                log("adnGas.size=" + N);
            for (int i = 0; i < N; i++) {
                if (TextUtils.isEmpty(adnGas.get(i)))
                    continue;

                Object[] group = new Object[SIM_GROUP_PROJECTION.length];
                group[0] = adnGas.get(i);
                group[1] = i + 1;
                cursor.addRow(group);
                if (DBG)
                    log("loadGas: " + group[1] + ", " + group[0]);
            }
            return cursor;
        } else {
            // No results to load
            Rlog.w(TAG, "Cannot load Gas records");
            return new MatrixCursor(SIM_GROUP_PROJECTION);
        }
    }

    private int addUsimGroup(String groupName, int subId) {
        if (DBG)
            log("addUsimGroup: groupName=" + groupName + ", subId=" + subId);
        int groupId = -1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                groupId = iccIpb.updateUsimGroupBySearchForSubscriber(subId, "", groupName);
            }

        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("addUsimGroup: " + groupId);
        return groupId;
    }

    private int updateUsimGroupById(String newName, int groupId, int subId) {
        if (DBG)
            log("updateUsimGroupById: newName=" + newName + ", groupId="
                    + groupId + ", subId=" + subId);
        boolean success = false;
        int result = -1;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                result = iccIpb.updateUsimGroupByIdForSubscriber(subId, newName, groupId);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        success = result < 0 ? false : true;
        if (DBG)
            log("updateUsimGroupById: " + success);
        return result;
    }

    private int deleteIccRecordFromEf(int efType, String name, String number,
            String[] emails, String anr, String sne, String grp, String pin2,
            int subId) {
        if (DBG)
            log("deleteIccRecordFromEf: efType=" + efType + ", name=" + name
                    + ", number=" + number + ",anr=" + anr + ", pin2=" + pin2);

        int retIndex = -1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                retIndex = iccIpb.updateAdnRecordsInEfBySearchForSubscriberEx(subId, efType, name,
                        number, emails, anr, sne, grp, "", "", null, "", "",
                        "", "", "", pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("deleteIccRecordFromEf: " + retIndex);
        return retIndex;
    }

    private int deleteIccRecordFromEfByIndex(int efType, int index,
            String pin2, int subId) {
        if (DBG)
            log("deleteIccRecordFromEfByIndex: efType="
                    + Integer.toHexString(efType) + ", index=" + index
                    + ", pin2=" + pin2);

        boolean success = false;
        int recIndex = -1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                recIndex = iccIpb.updateAdnRecordsInEfByIndexForSubscriberEx(subId, efType, "", "",
                        null, "", "", "", "", "", index, pin2);

            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (recIndex < 0) {
            success = false;
        } else {
            success = true;
        }
        if (DBG)
            log("deleteIccRecordFromEfByIndex: " + success + " recIndex = "
                    + recIndex);
        return recIndex;
    }

    // yeezone:jinwei update icc record from sim
    private int updateIccRecordInEfByIndex(int efType, String newName,
            String newNumber, String[] newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int simIndex, String pin2, int subId) {
        if (DBG)
            log("updateIccRecordInEfByIndex: efType=" + efType + ", newname="
                    + newName + ", newnumber=" + newNumber + ", newEmailList="
                    + newEmailList + ", newAnr=" + newAnr + ", newSne="
                    + newSne + ", index=" + simIndex + ", subId:" + subId);
        // boolean success = false;

        int recIndex = -1;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                recIndex = iccIpb.updateAdnRecordsInEfByIndexForSubscriberEx(subId, efType,
                        newName, newNumber, newEmailList, newAnr, newAas,
                        newSne, newGrp, newGas, simIndex, pin2);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("updateIccRecordInEfByIndex: " + recIndex);
        return recIndex;
    }

    private void throwException(int errorCode) {
        switch (errorCode) {
        case IccPhoneBookOperationException.WRITE_OPREATION_FAILED:
            throw new IccPBForRecordException(
                    IccPBForRecordException.WRITE_RECORD_FAILED,
                    "write record failed");

        case IccPhoneBookOperationException.ADN_CAPACITY_FULL:
            throw new IccPBForRecordException(
                    IccPBForRecordException.ADN_RECORD_CAPACITY_FULL,
                    "adn record capacity full");

        case IccPhoneBookOperationException.EMAIL_CAPACITY_FULL:
            throw new IccPBForMimetypeException(
                    IccPBForMimetypeException.CAPACITY_FULL,
                    Email.CONTENT_ITEM_TYPE, "email capacity full");

        case IccPhoneBookOperationException.LOAD_ADN_FAIL:
            throw new IccPBForRecordException(
                    IccPBForRecordException.LOAD_RECORD_FAILED,
                    "load adn failed");

        case IccPhoneBookOperationException.OVER_NAME_MAX_LENGTH:
            throw new IccPBForMimetypeException(
                    IccPBForMimetypeException.OVER_LENGTH_LIMIT,
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    "over the length of name ");

        case IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH:
            throw new IccPBForMimetypeException(
                    IccPBForMimetypeException.OVER_LENGTH_LIMIT,
                    Phone.CONTENT_ITEM_TYPE, "over the length of phone number");
        case IccPhoneBookOperationException.OVER_GROUP_NAME_MAX_LENGTH:
            throw new IccPBForMimetypeException(
                    IccPBForMimetypeException.OVER_LENGTH_LIMIT,
                    GroupMembership.CONTENT_ITEM_TYPE,
                    "over the length of group name");
        case IccPhoneBookOperationException.GROUP_CAPACITY_FULL:
            throw new IccPBForMimetypeException(
                    IccPBForMimetypeException.CAPACITY_FULL,
                    GroupMembership.CONTENT_ITEM_TYPE, "group capacity full");
        case IccPhoneBookOperationException.ANR_CAPACITY_FULL:
            throw new IccPBForRecordException(
                    IccPBForRecordException.ANR_RECORD_CAPACITY_FULL,
                    "anr record capacity full");
        /* SPRD: PhoneBook for AAS {@ */
        case IccPhoneBookOperationException.AAS_CAPACITY_FULL:
            throw new IccPBForRecordException(IccPBForRecordException.AAS_CAPACITY_FULL,
                    "aas capacity full");
        case IccPhoneBookOperationException.OVER_AAS_MAX_LENGTH:
            throw new IccPBForRecordException(IccPBForRecordException.OVER_AAS_MAX_LENGTH,
                    "over the length of aas");
        /* @} */
        default:
            break;
        }
    }
    /*@}*/
    /* SPRD: PhoneBook for AAS {@ */
    private MatrixCursor loadAas(int subId) {
        log("loadAas,subId=" + subId);
        List<String> adnAas = null;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                    ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                adnAas = iccIpb.getAasInEfForSubscriber(subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG) log(ex.toString());
        }

        if (adnAas != null) {
            // Load the results
            final int N = adnAas.size();
            final MatrixCursor cursor = new MatrixCursor(SIM_AAS_PROJECTION, N);
            if (DBG) log("adnAas.size=" + N);
            for (int i = 0; i < N ; i++) {
                if(TextUtils.isEmpty(adnAas.get(i))) continue;

                Object[] aas = new Object[SIM_AAS_PROJECTION.length];
                aas[0] = adnAas.get(i);
                aas[1] = i+1;
                cursor.addRow(aas);
                if (DBG) log("loadAas: " + aas[1] + ", " + aas[0]);
            }
            return cursor;
        } else {
            // No results to load
            Rlog.w(TAG, "Cannot load Aas records");
            return new MatrixCursor(SIM_AAS_PROJECTION);
        }
    }

    private int addUsimAas(String aas, int subId) {
        if (DBG)
            log("addUsimAas: aas=" + aas + ", subId=" + subId);
        int aasIndex=-1;

        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                aasIndex = iccIpb.updateUsimAasBySearchForSubscriber("", aas, subId);
            }

        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        if (DBG)
            log("addUsimAas: " + aasIndex);
        return aasIndex;
    }

    private int updateUsimAasByIndex(String newName, int aasIndex, int subId) {
        if (DBG) log("updateUsimAasByIndex: newName=" + newName + ", aasIndex="
                 + aasIndex + ", subId=" + subId);
        boolean success = false;
        int result = -1;
        try {
            IIccPhoneBook iccIpb = IIccPhoneBook.Stub
                    .asInterface(ServiceManager.getService("simphonebook"));
            if (iccIpb != null) {
                result = iccIpb.updateUsimAasByIndexForSubscriber(newName, aasIndex, subId);
            }
        } catch (RemoteException ex) {
            // ignore it
        } catch (SecurityException ex) {
            if (DBG)
                log(ex.toString());
        }
        success = result < 0 ? false : true;
        if (DBG)
            log("updateUsimAasByIndex: " + success);
        return result;
    }
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    private MatrixCursor getSneSize (int subId) {
       log("getSneSize,subId=" + subId);

       int senSize = 0;
       try {
           IIccPhoneBook iccIpb = IIccPhoneBook.Stub.asInterface(
                ServiceManager.getService("simphonebook"));
           if (iccIpb != null) {
               senSize = iccIpb.getSneSize(subId);
           }
       } catch (RemoteException ex) {
        // ignore it
       } catch (SecurityException ex) {
           if (DBG) log(ex.toString());
       }
       // Load the results
       final MatrixCursor cursor = new MatrixCursor(new String[]{"size"});
       cursor.addRow(new Object[]{senSize});
       return cursor;
    }
    /* @} */
}
