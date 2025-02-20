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

package com.android.internal.telephony.uicc;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.util.SparseArray;

import com.android.internal.telephony.gsm.UsimPhoneBookManager;

import java.util.ArrayList;
import java.util.Iterator;
import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.plugin.TelephonyForCmccPluginsUtils;
import com.android.internal.telephony.plugin.TelephonyForOrangeUtils;
import com.android.internal.telephony.uicc.AdnRecordLoader;
import com.android.internal.telephony.uicc.AdnRecord;
import android.util.Log;
import android.telephony.Rlog;
import android.text.TextUtils;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.IccPhoneBookOperationException;
/**
 * {@hide}
 */
public final class AdnRecordCache extends Handler implements IccConstants {
    //***** Instance Variables

    private IccFileHandler mFh;
    private UsimPhoneBookManager mUsimPhoneBookManager;

    // Indexed by EF ID
    SparseArray<ArrayList<AdnRecord>> mAdnLikeFiles
        = new SparseArray<ArrayList<AdnRecord>>();

    // People waiting for ADN-like files to be loaded
    SparseArray<ArrayList<Message>> mAdnLikeWaiters
        = new SparseArray<ArrayList<Message>>();

    // People waiting for adn record to be updated
    SparseArray<Message> mUserWriteResponse = new SparseArray<Message>();

    //***** Event Constants

    static final int EVENT_LOAD_ALL_ADN_LIKE_DONE = 1;
    static final int EVENT_UPDATE_ADN_DONE = 2;

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    private static String LOG_TAG = "AdnRecordCache";

    SparseArray<ArrayList<byte[]>> mExtLikeFiles
    = new SparseArray<ArrayList<byte[]>>();

    public int mInsertId = -1;

    static final int EVENT_UPDATE_USIM_ADN_DONE = 3;
    static final int EVENT_UPDATE_CYCLIC_DONE = 4;
    static final int EVENT_UPDATE_ANR_DONE = 5;
    static final int EVENT_LOAD_ALL_EXT_LIKE_DONE = 6;
    static final int EVENT_UPDATE_EXT_DONE = 7;
    /*@}*/
    /* SPRD: PhoneBook for SNE {@ */
    static final int EVENT_UPDATE_SNE_DONE = 8;
    /* @} */
    //***** Constructor



    AdnRecordCache(IccFileHandler fh) {
        mFh = fh;
        mUsimPhoneBookManager = new UsimPhoneBookManager(mFh, this);
    }

    //***** Called from SIMRecords

    /**
     * Called from SIMRecords.onRadioNotAvailable and SIMRecords.handleSimRefresh.
     */
    public void reset() {
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        Rlog.i(LOG_TAG, "reset adnLikeFiles");
        mExtLikeFiles.clear();
        /* @} */
        mAdnLikeFiles.clear();
        mUsimPhoneBookManager.reset();

        clearWaiters();
        clearUserWriters();

    }

    private void clearWaiters() {
        int size = mAdnLikeWaiters.size();
        for (int i = 0; i < size; i++) {
            ArrayList<Message> waiters = mAdnLikeWaiters.valueAt(i);
            AsyncResult ar = new AsyncResult(null, null, new RuntimeException("AdnCache reset"));
            notifyWaiters(waiters, ar);
        }
        mAdnLikeWaiters.clear();
    }

    private void clearUserWriters() {
        int size = mUserWriteResponse.size();
        for (int i = 0; i < size; i++) {
            sendErrorResponse(mUserWriteResponse.valueAt(i), "AdnCace reset");
        }
        mUserWriteResponse.clear();
    }

    /**
     * @return List of AdnRecords for efid if we've already loaded them this
     * radio session, or null if we haven't
     */
    public ArrayList<AdnRecord>
    getRecordsIfLoaded(int efid) {
        return mAdnLikeFiles.get(efid);
    }

    /**
     * Returns extension ef associated with ADN-like EF or -1 if
     * we don't know.
     *
     * See 3GPP TS 51.011 for this mapping
     */
    public int extensionEfForEf(int efid) {
        switch (efid) {
            case EF_MBDN: return EF_EXT6;
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case EF_LND:
            /* @} */
            case EF_ADN: return EF_EXT1;
            case EF_SDN: return EF_EXT3;
            case EF_FDN: return EF_EXT2;
            case EF_MSISDN: return EF_EXT1;
            case EF_PBR: return 0; // The EF PBR doesn't have an extension record
            default: return -1;
        }
    }

    private void sendErrorResponse(Message response, String errString) {
        if (response != null) {
            Exception e = new RuntimeException(errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    /**
     * Update an ADN-like record in EF by record index
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param adn is the new adn to be stored
     * @param recordIndex is the 1-based adn record index
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    /*SPRD:Modify this method. add SIMPhoneBook for bug 474587 @{ */
    /*public void updateAdnByIndex(int efid, AdnRecord adn, int recordIndex, String pin2,
            Message response) {*/
    /* @} */
    public void updateAdnByIndex(int efid, AdnRecord newAdn, int recordIndex, String pin2,
                Message response) {

        int extensionEF = extensionEfForEf(efid);
        if (extensionEF < 0) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED
                    , "EF is not known ADN-like EF:" + efid);
            /* @} */
            return;
        }

        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "Have pending update for EF:" + efid);
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED
                    ,"Have pending update for EF:" + efid);
            /* @} */
            return;
        }

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        mInsertId = recordIndex;

        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        if (oldAdnList == null) {
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED ,
                    "Adn list not exist for EF:" + efid);
            return;
        }
        /* @} */
        mUserWriteResponse.put(efid, response);

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        /*new AdnRecordLoader(mFh).updateEF(adn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, adn));*/
        AdnRecord oldAdn = oldAdnList.get(recordIndex - 1);
        Rlog.d(LOG_TAG, "oldAdn extRecord = " + oldAdn.mExtRecord);
        if (newAdn.extRecordIsNeeded()) {
            byte extIndex = (byte) 0xff;
            if (oldAdn.mExtRecord != 0xff) {
                extIndex = (byte) oldAdn.mExtRecord;
            } else {
                extIndex = getAvailableExtIndex(extensionEF, efid);
            }
            Rlog.d(LOG_TAG, "extIndex = " + extIndex);
            if (extIndex == (byte) 0xFF) {
                Rlog.d(LOG_TAG, "ext list full");
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response, IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                        "ext list full");
                return;
            }
            newAdn.mExtRecord = (int) extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, extIndex, pin2,
                    obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        } else {
            if (oldAdn.mExtRecord != 0xff) {
                Rlog.d(LOG_TAG, "need to clear extRecord " + oldAdn.mExtRecord);
                new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, oldAdn.mExtRecord,
                        pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, oldAdn.mExtRecord));
            }
        }
        oldAdn.mExtRecord = 0xff;
        new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                recordIndex, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, recordIndex, newAdn));
    }

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * The ADN-like records must be read through requestLoadAllAdnLike() before
     *
     * @param efid must be one of EF_ADN, EF_FDN, and EF_SDN
     * @param oldAdn is the adn to be replaced
     *        If oldAdn.isEmpty() is ture, it insert the newAdn
     * @param newAdn is the adn to be stored
     *        If newAdn.isEmpty() is true, it delete the oldAdn
     * @param pin2 is required to update EF_FDN, otherwise must be null
     * @param response message to be posted when done
     *        response.exception hold the exception in error
     */
    public void updateAdnBySearch(int efid, AdnRecord oldAdn, AdnRecord newAdn,
            String pin2, Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);

        if (extensionEF < 0) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "EF is not known ADN-like EF:" + efid);
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                    "EF is not known ADN-like EF:" + efid);
            /* @} */
            return;
        }

        ArrayList<AdnRecord>  oldAdnList;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        oldAdnList = getRecordsIfLoaded(efid);

        /*if (efid == EF_PBR) {
            oldAdnList = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            oldAdnList = getRecordsIfLoaded(efid);
        }*/
        /* @} */
        if (oldAdnList == null) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "Adn list not exist for EF:" + efid);
            sendErrorResponse(response,IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                    "Adn list not exist for EF:" + efid);
           /* @} */
            return;
        }

        int index = -1;
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext(); ) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                mInsertId = index;
                /* @} */
                break;
            }
            count++;
        }

        if (index == -1) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "Adn record don't exist for " + oldAdn);
            sendErrorResponse(response, IccPhoneBookOperationException.ADN_CAPACITY_FULL ,
                    "Adn record don't exist for " + oldAdn);
            /* @} */
            return;
        }

        /* SPRD：Remove this.SIMPhoneBook for bug 474587@{ */
        /*if (efid == EF_PBR) {
            AdnRecord foundAdn = oldAdnList.get(index-1);
            efid = foundAdn.mEfid;
            extensionEF = foundAdn.mExtRecord;
            index = foundAdn.mRecordNumber;

            newAdn.mEfid = efid;
            newAdn.mExtRecord = extensionEF;
            newAdn.mRecordNumber = index;
        }*/
        /* @} */
        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //sendErrorResponse(response, "Have pending update for EF:" + efid);
            sendErrorResponse(response,IccPhoneBookOperationException.WRITE_OPREATION_FAILED, "Have pending update for EF:" + efid);
            /* @} */
            return;
        }

        mUserWriteResponse.put(efid, response);

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        oldAdn.mExtRecord = oldAdnList.get(index - 1).mExtRecord;
        Rlog.d(LOG_TAG, "oldAdn extRecord = " + oldAdn.mExtRecord);

        if (newAdn.extRecordIsNeeded()) {
            byte extIndex = (byte) 0xff;
            if (oldAdn.mExtRecord != 0xff) {
                extIndex = (byte) oldAdn.mExtRecord;
            } else {
                extIndex = getAvailableExtIndex(extensionEF, efid);
            }
            Rlog.d(LOG_TAG, "extIndex = "+extIndex);
            if (extIndex == (byte)0xFF) {
                Rlog.d(LOG_TAG, "ext list full");
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response, IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                        "ext list full");
                return;
            }
            newAdn.mExtRecord = (int)extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF,
                    extIndex, pin2,
                    obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        } else {
            if (oldAdn.mExtRecord != 0xff) {
                Rlog.d(LOG_TAG, "need to clear extRecord " + oldAdn.mExtRecord);
                new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF, oldAdn.mExtRecord,
                        pin2, obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, oldAdn.mExtRecord));
            }
        }
        oldAdn.mExtRecord = 0xff;
        /* @} */
        new AdnRecordLoader(mFh).updateEF(newAdn, efid, extensionEF,
                index, pin2,
                obtainMessage(EVENT_UPDATE_ADN_DONE, efid, index, newAdn));
    }


    /**
     * Responds with exception (in response) if efid is not a known ADN-like
     * record
     */
    public void
    requestLoadAllAdnLike (int efid, int extensionEf, Message response) {
        ArrayList<Message> waiters;
        ArrayList<AdnRecord> result;

        if (efid == EF_PBR) {
            result = mUsimPhoneBookManager.loadEfFilesFromUsim();
        } else {
            result = getRecordsIfLoaded(efid);
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            if(result != null){
                if(result.size() == 0){
                result = null;
                }
            }
            /*@}*/
        }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        if (efid == EF_PBR && result == null) {
            efid = EF_ADN;
            extensionEf = extensionEfForEf(efid);
            Rlog.i(LOG_TAG, "pbr is empty,read adn");
            result = getRecordsIfLoaded(efid);
            if (result != null) {
                if (result.size() == 0) {
                    result = null;
                }
            }
        }
        /*@}*/
        // Have we already loaded this efid?
        if (result != null) {
            if (response != null) {
                AsyncResult.forMessage(response).result = result;
                response.sendToTarget();
            }

            return;
        }

        // Have we already *started* loading this efid?

        waiters = mAdnLikeWaiters.get(efid);

        if (waiters != null) {
            // There's a pending request for this EF already
            // just add ourselves to it

            waiters.add(response);
            return;
        }

        // Start loading efid

        waiters = new ArrayList<Message>();
        waiters.add(response);

        mAdnLikeWaiters.put(efid, waiters);


        if (extensionEf < 0) {
            // respond with error if not known ADN-like record

            if (response != null) {
                AsyncResult.forMessage(response).exception
                    = new RuntimeException("EF is not known ADN-like EF:" + efid);
                response.sendToTarget();
            }
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            Rlog.i("AdnRecordCache"," extensionEf < 0 " );
            /*@}*/
            return;
        }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        Rlog.i("AdnRecordCache", "requestLoadAllAdnLike efid " + Integer.toHexString(efid));
         /*new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
            obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, 0));*/
        new AdnRecordLoader(mFh).loadAllExtFromEF(efid, extensionEf,
                obtainMessage(EVENT_LOAD_ALL_EXT_LIKE_DONE, efid, extensionEf));
        /*@}*/
    }

    //***** Private methods

    private void
    notifyWaiters(ArrayList<Message> waiters, AsyncResult ar) {

        if (waiters == null) {
            return;
        }

        for (int i = 0, s = waiters.size() ; i < s ; i++) {
            Message waiter = waiters.get(i);

            AsyncResult.forMessage(waiter, ar.result, ar.exception);
            waiter.sendToTarget();
        }
    }

    //***** Overridden from Handler

    @Override
    public void
    handleMessage(Message msg) {
        AsyncResult ar;
        int efid;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        int index;
        int extensionEf;
        AdnRecord adn;
        Message response;
        /*@}*/

        switch(msg.what) {
            case EVENT_LOAD_ALL_ADN_LIKE_DONE:
                /* arg1 is efid, obj.result is ArrayList<AdnRecord>*/
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                extensionEf = msg.arg2;
                /*@}*/
                ArrayList<Message> waiters;

                waiters = mAdnLikeWaiters.get(efid);
                mAdnLikeWaiters.delete(efid);

                if (ar.exception == null) {
                    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                    ArrayList<AdnRecord> adns = (ArrayList<AdnRecord>) (ar.result);
                    for(AdnRecord adnRecord:adns){
                        if (adnRecord.hasExtendedRecord() && adnRecord.extRecord4DisplayIsNeeded()) {
                            Rlog.d(LOG_TAG, "adn.extRecord = " + adnRecord.mExtRecord);
                            if (mExtLikeFiles.get(extensionEf) != null
                                    && adnRecord.mExtRecord <= mExtLikeFiles.get(extensionEf).size()) {
                                adnRecord.appendExtRecord(mExtLikeFiles.get(extensionEf).get(
                                        adnRecord.mExtRecord - 1));
                            }
                        }
                    }
                    /*@}*/
                    mAdnLikeFiles.put(efid, (ArrayList<AdnRecord>) ar.result);
                }
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                Rlog.d(LOG_TAG, "EVENT_LOAD_ALL_ADN_LIKE_DONE:efid = "+efid +"ar.exception = " +ar.exception);
                if (waiters != null) {
                /*@}*/
                   notifyWaiters(waiters, ar);
                   /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                }
                   /*@}*/
                break;
            case EVENT_UPDATE_ADN_DONE:
                ar = (AsyncResult)msg.obj;
                efid = msg.arg1;
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                //int index = msg.arg2;
                //AdnRecord adn = (AdnRecord) (ar.userObj);
                index = msg.arg2;
                adn = (AdnRecord) (ar.userObj);
                /*if (ar.exception == null) {
                    mAdnLikeFiles.get(efid).set(index - 1, adn);
                    mUsimPhoneBookManager.invalidateCache();
                }

                Message response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);

                AsyncResult.forMessage(response, null, ar.exception);
                response.sendToTarget();*/
                Rlog.d(LOG_TAG, "AdnRecordCache:EVENT_UPDATE_ADN_DONE:mInsertId = " + mInsertId);
                if (ar.exception == null && mAdnLikeFiles.get(efid) != null) {
                    adn.setRecordNumber(mInsertId);
                    Rlog.d(LOG_TAG, "EVENT_UPDATE_ADN_DONE:adn.extRecord = "+adn.mExtRecord);
                    mAdnLikeFiles.get(efid).set(index - 1, adn);
                }
                Rlog.i("AdnRecordCache", "efid" + efid);
                response = mUserWriteResponse.get(efid);
                Rlog.i("AdnRecordCache", "response" + response + "index " + index);
                mUserWriteResponse.delete(efid);

                // yeezone:jinwei return sim_index after add a new contact in
                // SimCard.
                // AsyncResult.forMessage(response, null, ar.exception);
                if (response != null){
                    AsyncResult.forMessage(response, index, ar.exception);
                    Rlog.i("AdnRecordCache", "response" + response + "index " + index
                            + "target " + response.getTarget());
                    response.sendToTarget();
                } else {
                    Rlog.e(LOG_TAG, "EVENT_UPDATE_ADN_DONE response is null efid:" + efid);
                }
                break;
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            case EVENT_LOAD_ALL_EXT_LIKE_DONE:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                extensionEf = msg.arg2;
                if (ar.exception == null) {
                    mExtLikeFiles.put(extensionEf, (ArrayList<byte[]>) ar.result);
                }
                new AdnRecordLoader(mFh).loadAllFromEF(efid, extensionEf,
                        obtainMessage(EVENT_LOAD_ALL_ADN_LIKE_DONE, efid, extensionEf));
                break;
            case EVENT_UPDATE_CYCLIC_DONE:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                adn = (AdnRecord) (ar.userObj);
                mInsertId = 1;
                Rlog.i("AdnRecordCache", "efid " + efid + "mInsertId = " + mInsertId +" ,index = " + index);

                if (ar.exception != null){
                    Rlog.i("AdnRecordCache", "ar.exception != null");
                }

                response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);

                if (response != null) {
                    AsyncResult.forMessage(response, index, ar.exception);
                    Rlog.i("AdnRecordCache", "response" + response + "target "
                            + response.getTarget());
                    response.sendToTarget();
                } else {
                    Rlog.e(LOG_TAG, "EVENT_UPDATE_CYCLIC_DONE response is null efid:" + efid);
                }
                break;

            case EVENT_UPDATE_EXT_DONE:
                ar = (AsyncResult) msg.obj;
                extensionEf = msg.arg1;
                index = msg.arg2;
                byte[] extData = (byte[])ar.result;
                Rlog.d(LOG_TAG, "EVENT_UPDATE_EXT_DONE index = "+index+" extData="+extData);
                if (ar.exception == null && mExtLikeFiles.get(extensionEf) != null
                        && extData != null) {
                    if (index > 0 && index <= mExtLikeFiles.get(extensionEf).size()) {
                        mExtLikeFiles.get(extensionEf).set(index-1, extData);
                    }
                }else {
                    Rlog.e(LOG_TAG, "EVENT_UPDATE_EXT_DONE failed:"+ar.exception);
                }
                break;

            case EVENT_UPDATE_USIM_ADN_DONE:
                Rlog.i("AdnRecordCache", "EVENT_UPDATE_USIM_ADN_DONE");
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                index = msg.arg2;
                adn = (AdnRecord) (ar.userObj);
                int recNum = -1;
                for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
                    int adnEF = mUsimPhoneBookManager.findEFInfo(num);
                    if (efid == adnEF) {
                        recNum = num;
                    }
                }
                int[] mAdnRecordSizeArray = mUsimPhoneBookManager
                        .getAdnRecordSizeArray();
                int adnRecNum;
                if (recNum == -1) {
                    break;
                }
                adnRecNum = index - 1;
                for (int i = 0; i < recNum; i++) {
                    adnRecNum += mAdnRecordSizeArray[i];
                }
                Rlog.d(LOG_TAG, "AdnRecordCache:EVENT_UPDATE_USIM_ADN_DONE:mInsertId = "
                    + mInsertId + "adnRecNum = " + adnRecNum +"adn.extrecord "+adn.mExtRecord);
                if (ar.exception == null && mAdnLikeFiles.get(efid) != null) {
                    adn.setRecordNumber(mInsertId);
                    mUsimPhoneBookManager.setPhoneBookRecords(adnRecNum, adn);
                    mAdnLikeFiles.get(efid).set(index - 1, adn);
                    /* SPRD:add for TS31.121 8.1.2 @{ */
                    TelephonyForCmccPluginsUtils.getInstance().updateUidForAdn(mUsimPhoneBookManager,efid, recNum, adnRecNum,adn,mFh);
                } else {
                    Rlog.e("GSM", " fail to Update Usim Adn");
                }

                response = mUserWriteResponse.get(efid);
                mUserWriteResponse.delete(efid);
                if (response != null) {
                    AsyncResult.forMessage(response, null, ar.exception);
                    response.sendToTarget();
                }

                Rlog.i("AdnRecordCache", "EVENT_UPDATE_USIM_ADN_DONE finish");

                break;
           /*@}*/
           /* SPRD: PhoneBook for SNE {@ */
            case EVENT_UPDATE_SNE_DONE:
                ar = (AsyncResult) msg.obj;
                efid = msg.arg1;
                Rlog.d(LOG_TAG, "EVENT_UPDATE_SNE_DONE exception:"+ar.exception +"efid = "+efid);
                if (ar.exception != null) {
                    response = mUserWriteResponse.get(efid);
                    mUserWriteResponse.delete(efid);
                    if (response != null) {
                        AsyncResult.forMessage(response, null, ar.exception);
                        response.sendToTarget();
                    }else {
                        Rlog.e(LOG_TAG, "EVENT_UPDATE_SNE_DONE response is null efid:"+efid);
                    }
                }
                break;
           /* @} */
        }

    }

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */

    private int[] getRecordsSizeByEf(int efId) {
        int[] size;
        if (mUsimPhoneBookManager.mRecordsSize != null
                && mUsimPhoneBookManager.mRecordsSize.containsKey(efId)) {
            size = mUsimPhoneBookManager.mRecordsSize.get(efId);
        } else {
            size = mUsimPhoneBookManager.readFileSizeAndWait(efId);
        }
        return size;
    }

    boolean isCleanRecord(int num, int type, AdnRecord oldAdn, AdnRecord newAdn, int index) {
        int oldCount = 0, newCount = 0, count = 0, i = 0;
        String str1, str2;
        String[] strArr1, strArr2;
        int efids[] = null;
        strArr1 = getSubjectString(type, oldAdn);
        strArr2 = getSubjectString(type, newAdn);
        /* SPRD: PhoneBook for AAS {@ */
        String aas1, aas2;
        String[] aasArr1, aasArr2;
        aasArr1 = getSubjectAasString(type, oldAdn);
        aasArr2 = getSubjectAasString(type, newAdn);
        /* @} */
        if (strArr1 != null) {
            oldCount = strArr1.length;
        }
        if (strArr2 != null) {
            newCount = strArr2.length;
        }
        Rlog.i(LOG_TAG, "isCleanRecord oldCount =" + oldCount + " newCount " + newCount);
        efids = mUsimPhoneBookManager.getSubjectEfids(type, num);
        if (efids == null) {
            return false;
        }
        count = efids.length;
        Rlog.i(LOG_TAG, "isCleanRecord count =" + count);
        for (i = 0; i < count; i++) {
            if (i < oldCount) {
                str1 = strArr1[i];
            } else {
                str1 = "";
            }
            if (i < newCount) {
                str2 = strArr2[i];
            } else {
                str2 = "";
            }
            /* SPRD: PhoneBook for AAS {@ */
            if (i < oldCount && aasArr1 != null && aasArr1[i] != null) {
                aas1 = aasArr1[i];
            } else {
                aas1 = "";
            }

            if (i < newCount && aasArr2 != null && aasArr2[i] != null) {
               aas2 = aasArr2[i];
            } else {
               aas2 = "";
            }
            Rlog.i(LOG_TAG, "isCleanRecord aas1 =" + aas1 + ", aas2 = " + aas2);
            /* @} */
            if (index == i && (!(str1.trim().equals(str2.trim()))/* SPRD: PhoneBook for AAS*/
                    || !(aas1.trim().equals(aas2.trim()))) && TextUtils.isEmpty(str2)) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<String> loadGasFromUsim() {
        return mUsimPhoneBookManager.loadGasFromUsim();
    }

    public void removedRecordsIfLoaded(int efid) {
        mAdnLikeFiles.remove(efid);
    }

    private boolean compareSubject(int type, AdnRecord oldAdn, AdnRecord newAdn) {
        boolean isEqual = true;
        switch (type) {
            case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:
                isEqual = oldAdn.stringCompareEmails(oldAdn.mEmails, newAdn.mEmails);
                break;
            case UsimPhoneBookManager.USIM_SUBJCET_ANR:
                //SPRD: PhoneBook for AAS
                Rlog.d(LOG_TAG,"USIM_SUBJCET_ANR oldAdn.aas == " + oldAdn.mAas + ", newAdn.aas == " + newAdn.mAas);
                isEqual = oldAdn.stringCompareAnr(oldAdn.mAnr, newAdn.mAnr)
                        && /* SPRD: PhoneBook for AAS*/oldAdn.stringCompareAnr(oldAdn.mAas, newAdn.mAas);
                break;
            case UsimPhoneBookManager.USIM_SUBJCET_GRP:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mGrp, newAdn.mGrp);
                break;
            // SPRD: PhoneBook for AAS
            case UsimPhoneBookManager.USIM_SUBJCET_AAS:
                Rlog.d(LOG_TAG,"oldAdn.aas == " + oldAdn.mAas + ", newAdn.aas == " + newAdn.mAas);
                isEqual = oldAdn.stringCompareAnr(oldAdn.mAas, newAdn.mAas);
                break;
            // SPRD: PhoneBook for SNE
            case UsimPhoneBookManager.USIM_SUBJCET_SNE:
                isEqual = oldAdn.stringCompareAnr(oldAdn.mSne, newAdn.mSne);
                break;
            default:
                break;
        }
        return isEqual;
    }

    private String[] getAnrNumGroup(String anr) {
        String[] pair = null;
        Rlog.i(LOG_TAG, "getAnrNumGroup anr =" + anr);
        if (!TextUtils.isEmpty(anr)) {

            pair = anr.split(";");
        }
        return pair;
    }

    private String[] getSubjectString(int type, AdnRecord adn) {
        String[] s1 = null;
        switch (type) {
        case UsimPhoneBookManager.USIM_SUBJCET_EMAIL:
            s1 = adn.mEmails;
            break;
        case UsimPhoneBookManager.USIM_SUBJCET_ANR:
            s1 = getAnrNumGroup(adn.mAnr);
            break;
        // SPRD: PhoneBook for AAS
        case UsimPhoneBookManager.USIM_SUBJCET_AAS:
            s1 = new String[]{adn.mAas};
            break;
        // SPRD: PhoneBook for SNE
        case UsimPhoneBookManager.USIM_SUBJCET_SNE:
            s1 = new String[]{adn.mSne};
            break;
        default:
            break;
        }
        return s1;
    }

    private int[] getUpdateSubjectFlag(int num,int type, AdnRecord oldAdn,
            AdnRecord newAdn) {
        int[] flag = null;
        int oldCount = 0, newCount = 0,  count = 0,i = 0;
        String str1="", str2="";
        String[] strArr1, strArr2;
        int efids[] =null;
        strArr1 = getSubjectString(type, oldAdn);
        strArr2 = getSubjectString(type, newAdn);
        /* SPRD: PhoneBook for AAS {@ */
        String[] aasArr1, aasArr2;
        String aas1="", aas2="";
        aasArr1 = getSubjectAasString(type, oldAdn);
        aasArr2 = getSubjectAasString(type, newAdn);
        /* @} */
        if (strArr1 != null) {
            oldCount = strArr1.length;
        }
        if (strArr2 != null) {
            newCount = strArr2.length;
        }
        Rlog.i(LOG_TAG, "getUpdateSubjectFlag oldCount =" + oldCount + " newCount " +newCount );
        efids = mUsimPhoneBookManager.getSubjectEfids(type,num);
        if(efids == null){
            return null;
        }
        count = efids.length;
        flag = new int[count];
        Rlog.i(LOG_TAG, "getUpdateSubjectFlag count =" + count);
        for (i = 0; i < count; i++) {
            str1 = "";
            str2 = "";
            /* SPRD: PhoneBook for AAS {@ */
            aas1 = "";
            aas2 = "";
            /* @} */
            if (i < oldCount && strArr1[i]!=null) {
                str1 = strArr1[i];
            }

            if (i < newCount && strArr2[i]!=null) {
                str2 = strArr2[i];
            }
            /* SPRD: PhoneBook for AAS {@ */
            if(i< oldCount && aasArr1 !=null && aasArr1[i] != null) {
                aas1 = aasArr1[i];
            }
            if(i< newCount && aasArr2 !=null && aasArr2[i] != null) {
                aas2 = aasArr2[i];
            }
            /* @} */

            flag[i] = (!(str1.trim().equals(str2.trim())) || !(aas1.trim().equals(aas2.trim())))? 1 : 0;
            Rlog.i(LOG_TAG, "getUpdateSubjectFlag flag[i] =" + flag[i]);
        }
        return flag;
    }

    private int updateSubjectOfAdn(int type, int num,
            AdnRecordLoader adnRecordLoader, int adnNum, int index, int efid,
            AdnRecord oldAdn, AdnRecord newAdn, int iapEF, String pin2,/* SPRD: PhoneBook for AAS*/Object obj) {
        int resultValue = 1;
        int[] subjectNum = null;
        boolean newAnr = false;
        int[] updateSubjectFlag = null;

        ArrayList<Integer> subjectEfids;
        ArrayList<Integer> subjectNums;

        int m = 0, n = 0;
        int[][] anrTagMap; // efid ,numberInIap
        int efids[] = mUsimPhoneBookManager.getSubjectEfids(type, num);

        Rlog.i(LOG_TAG, "Begin : updateSubjectOfAdn num =" + num + " adnNum " + adnNum + " index "
                + index);

        if (compareSubject(type, oldAdn, newAdn)) {
            return 0;
        }

        updateSubjectFlag = getUpdateSubjectFlag(num, type, oldAdn, newAdn);
        subjectEfids = new ArrayList<Integer>();
        subjectNums = new ArrayList<Integer>();

        if (updateSubjectFlag == null || efids == null || efids.length == 0) {
            return 0;
        }
        anrTagMap = mUsimPhoneBookManager.getSubjectTagNumberInIap(type, num);
        subjectNum = new int[efids.length];

        for (m = 0; m < efids.length; m++) {
            subjectEfids.add(efids[m]);
            if (mUsimPhoneBookManager.isSubjectRecordInIap(type, num, m)) {
                Rlog.i(LOG_TAG, "updateSubjectOfAdn  in iap  ");
                byte[] record = null;
                try {
                    ArrayList<byte[]> mIapFileRecord = mUsimPhoneBookManager.getIapFileRecord(num);
                    if (mIapFileRecord != null) {
                        record = mIapFileRecord.get(index - 1);
                    } else {
                        Rlog.i(LOG_TAG, "updateSubjectOfAdn mIapFileRecord == null ");
                        subjectNums.add(0);
                        n++;
                        continue;
                    }
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                }

                if (anrTagMap == null) {
                    subjectNums.add(0);
                    n++;
                    continue;
                }

                if (record != null) {
                    Rlog.i(LOG_TAG, "subjectNumberInIap =" + anrTagMap[m][1]);
                    subjectNum[m] = (int) (record[anrTagMap[m][1]] & 0xFF);
                    Rlog.i(LOG_TAG, "subjectNumber =" + subjectNum[m]);
                    subjectNum[m] = subjectNum[m] == 0xFF ? (-1) : subjectNum[m];
                    Rlog.i(LOG_TAG, "subjectNum[m] =" + subjectNum[m]);
                } else {
                    subjectNum[m] = -1;
                    Rlog.i(LOG_TAG, "subjectNum[m] =" + subjectNum[m]);
                }

                if (subjectNum[m] == -1 && updateSubjectFlag[m] == 1) {
                    subjectNum[m] = mUsimPhoneBookManager.getNewSubjectNumber(type, num, anrTagMap[m][0], n, index, true);
                    if (subjectNum[m] == -1) {
                        Rlog.i(LOG_TAG, "updateSubjectOfAdn   is full  ");
                        n++;
                        subjectNums.add(0);
                        resultValue = -1;
                        continue;
                    }
                }

                Rlog.i(LOG_TAG, "updateSubjectOfAdn   updateSubjectFlag  " + updateSubjectFlag[m] + "subjectNum[m] " + subjectNum[m]);
                if (updateSubjectFlag[m] == 1 && subjectNum[m] != -1) {
                    subjectNums.add(subjectNum[m]);
                } else {
                    subjectNums.add(0);
                }
                if (updateSubjectFlag[m] == 1) {
                    if (isCleanRecord(num, type, oldAdn, newAdn, m)) {
                        Rlog.i(LOG_TAG, " clean anrTagMap[m][0] "  + Integer.toHexString(anrTagMap[m][0]));
                        mUsimPhoneBookManager.removeSubjectNumFromSet(type, num, anrTagMap[m][0], n, subjectNum[m]);//
                        mUsimPhoneBookManager.setIapFileRecord(num, index - 1, (byte) 0xFF, anrTagMap[m][1]);
                        record[anrTagMap[m][1]] = (byte) 0xFF;

                    } else {
                        Rlog.i(LOG_TAG, "  anrTagMap[m][0]     "
                                + Integer.toHexString(anrTagMap[m][0]));
                        mUsimPhoneBookManager.setIapFileRecord(num, index - 1, (byte) (subjectNum[m] & 0xFF), anrTagMap[m][1]);
                        record[anrTagMap[m][1]] = (byte) (subjectNum[m] & 0xFF);
                    }

                    if (anrTagMap[m][0] > 0) {
                        Rlog.e(LOG_TAG, "begin to update IAP ---IAP id  "
                                + adnNum + "iapEF " + Integer.toHexString(iapEF));
                        adnRecordLoader = new AdnRecordLoader(mFh);
                        adnRecordLoader.updateEFIapToUsim(newAdn, iapEF, index, record, pin2, null, getRecordsSizeByEf(iapEF));
                    }
                }
                n++;
            } else {
                if (updateSubjectFlag[m] == 1) {
                    if (mUsimPhoneBookManager.getNewSubjectNumber(type, num,
                            efids[m], 0, index, false) == index) {
                        subjectNums.add(index);
                    } else {
                        subjectNums.add(0);
                        Rlog.e(LOG_TAG, "updateSubjectOfAdn fail to get  new subject ");
                        resultValue = -1;
                    }
                } else {
                    subjectNums.add(0);
                    Rlog.e(LOG_TAG, "updateSubjectOfAdn don't need to update subject ");
                    resultValue = 0;
                }
            }
        }

        Rlog.e(LOG_TAG, " END :updateSubjectOfAdn  updateSubjectOfAdn efids is " + subjectEfids + " subjectNums " + subjectNums);

        for (int i = 0; i < subjectEfids.size(); i++) {
            if (subjectNums.get(i) != 0) {
                ArrayList<Integer> toUpdateNums = new ArrayList<Integer>();
                ArrayList<Integer> toUpdateIndex = new ArrayList<Integer>();
                ArrayList<Integer> toUpdateEfids = new ArrayList<Integer>();
                toUpdateEfids.add(subjectEfids.get(i));
                toUpdateIndex.add(i);
                toUpdateNums.add(subjectNums.get(i));

                adnRecordLoader = new AdnRecordLoader(mFh);

                if (type == UsimPhoneBookManager.USIM_SUBJCET_EMAIL &&
                        resultValue == 1) {
                    adnRecordLoader.updateEFEmailToUsim(newAdn, toUpdateEfids, toUpdateNums, efid,
                            index, toUpdateIndex, pin2, null,getRecordsSizeByEf(toUpdateEfids.get(0)));

                }
                /* SPRD: PhoneBook for SNE {@ */
                if (type == UsimPhoneBookManager.USIM_SUBJCET_SNE &&
                        resultValue == 1 && TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
                    adnRecordLoader.updateEFSneToUsim(newAdn, toUpdateEfids, toUpdateNums,efid,
                            index,toUpdateIndex, pin2, null);
                }
                /* @} */
                if (type == UsimPhoneBookManager.USIM_SUBJCET_ANR) {
                        /* SPRD: PhoneBook for AAS {@ */
                        int[] data = null;
                        if(obj != null){
                            data = (int[])obj;
                        }
                        int aasIndex = (data != null && i < data.length )? data[i] : 0;
                        Rlog.d(LOG_TAG, "aasIndex " + aasIndex);
                        /* @} */
                        Rlog.d(LOG_TAG, "updateEFAnrToUsim ");
                        adnRecordLoader.updateEFAnrToUsim(newAdn, toUpdateEfids, efid, index,
                                toUpdateNums, toUpdateIndex, pin2, null, getRecordsSizeByEf(toUpdateEfids.get(0)),/* SPRD: PhoneBook for AAS*/aasIndex);
                }
            }
        }
        Rlog.d(LOG_TAG, "updateSubjectOfAdnForResult:resultValue = " + resultValue);
        return resultValue;
    }

    private void updateGrpOfAdn(AdnRecordLoader adnRecordLoader, int index, int recNum,
            AdnRecord oldAdn, AdnRecord newAdn, String pin2) {

        if (compareSubject(UsimPhoneBookManager.USIM_SUBJCET_GRP, oldAdn, newAdn)) {
            return;
        }

        String grp = newAdn.getGrp();

        byte[] data = new byte[mUsimPhoneBookManager.mGrpCount];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) 0x00;
        }
        if (!TextUtils.isEmpty(grp)) {
            String[] groups = grp.split(AdnRecord.ANR_SPLIT_FLG);
            for (int i = 0; i < groups.length; i++) {
                int groupId = Integer.valueOf(groups[i]);
                data[groupId - 1] = (byte) groupId;
            }
        }
        int grpEfId = mUsimPhoneBookManager.getEfIdByTag(recNum,
                UsimPhoneBookManager.USIM_EFGRP_TAG);

        adnRecordLoader.updateEFGrpToUsim(grpEfId, index, data, pin2);
    }

    private byte findEmptyExt(ArrayList<byte[]> extList) {
        if (extList == null) {
            Rlog.d(LOG_TAG, "extList is not existed ");
            return (byte)0xFF;
        }
        byte count = 1;
        for (Iterator<byte[]> it = extList.iterator(); it.hasNext();) {
            if ((byte) 0xFF == it.next()[0]) {
                Rlog.d(LOG_TAG, "we got the index " + count);
                return count;
            }
            count++;
        }
        Rlog.d(LOG_TAG, "find no empty ext");
        return (byte)0xFF;
    }

    private ArrayList<Integer> getUsedExtRecordIndex(int efid) {
        ArrayList<Integer> usedIndex = new ArrayList<Integer>();
        ArrayList<AdnRecord> adnList =getRecordsIfLoaded(efid);
        if (adnList == null) {
            return null;
        }
        int index = 0;
        for (Iterator<AdnRecord> it = adnList.iterator(); it.hasNext();) {
            index = it.next().mExtRecord;
            if (index != 0xFF && index != 0) {
                if (!usedIndex.contains(index)) {
                    usedIndex.add(index);
                }
            }
        }
        Rlog.d(LOG_TAG, "usedIndex = "+usedIndex);
        return usedIndex;
    }

    private byte getAvailableExtIndex(int extEfId, int efid) {
        Rlog.d(LOG_TAG, "getAvailableExtIndex:extEfId = " + extEfId);
        ArrayList<byte[]> extList = mExtLikeFiles.get(extEfId);
        byte[] emptyExtRecord = new byte[AdnRecord.EXT_RECORD_LENGTH_BYTES];
        for (int i = 0; i < emptyExtRecord.length; i++) {
            emptyExtRecord[i] = (byte) 0xFF;
        }
        byte index = findEmptyExt(extList);
        Rlog.d(LOG_TAG, "index& 0xff = " + (index & 0xFF));
        if ((index & 0xFF) == 0xFF) {
            // no empty ext record,clean unused ext records
            ArrayList<Integer> usedExtIndexs = getUsedExtRecordIndex(efid);
            Rlog.d(LOG_TAG, "usedExtIndex = " + usedExtIndexs);
            if (usedExtIndexs == null || extList == null) {
                Rlog.e(LOG_TAG, "extList is not existed");
                return (byte) 0xFF;
            }
            int extListSize = extList.size();
            boolean isUsed = false;
            for (int i = 0; i < extListSize; i++) {
                isUsed = false;
                for (Iterator<Integer> it = usedExtIndexs.iterator(); it.hasNext();) {
                    if (i + 1 == it.next()) {
                        isUsed = true;
                        break;
                    }
                }
                if (isUsed == false) {
                    Rlog.d(LOG_TAG, "set emptyRecord : " + (i + 1));
                    mExtLikeFiles.get(extEfId).set(i, emptyExtRecord);
                }
            }
            // find empty ext record
            extList = mExtLikeFiles.get(extEfId);
            return findEmptyExt(extList);
        } else {
            return (byte) index;
        }

    }

    public synchronized void updateUSIMAdnBySearch(int efid, AdnRecord oldAdn,
            AdnRecord newAdn, String pin2, Message response) {
        int extensionEF = 0;
        int index = -1;
        int emailEF = 0;
        int iapEF = 0;
        int recNum = 0;
        int iapRecNum = 0;

        Rlog.i(LOG_TAG, "updateUSIMAdnBySearch efid " + Integer.toHexString(efid));
        for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {

            efid = mUsimPhoneBookManager.findEFInfo(num);
            extensionEF = mUsimPhoneBookManager.findExtensionEFInfo(num);
            iapEF = mUsimPhoneBookManager.findEFIapInfo(num);
            Rlog.e(LOG_TAG, "efid : " + efid + "extensionEF :" + extensionEF
                    + " iapEF:" + iapEF);
            if (efid < 0 || extensionEF < 0) {
                sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                        "EF is not known ADN-like EF:"
                                + "efid" + efid + ",extensionEF=" + extensionEF);
                return;
            }
            Rlog.i(LOG_TAG, "updateUSIMAdnBySearch (1)");
            ArrayList<AdnRecord> oldAdnList;
            Rlog.e(LOG_TAG, "efid is " + efid);
            oldAdnList = getRecordsIfLoaded(efid);
            if (oldAdnList == null) {
                sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                        "Adn list not exist for EF:" + efid);
                return;
            }
            Rlog.i(LOG_TAG, "updateUSIMAdnBySearch (2)");
            int count = 1;
            boolean find_index = false;
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
                if (oldAdn.isEqual(it.next())) {
                    Rlog.d(LOG_TAG, "we got the index " + count);
                    find_index = true;
                    index = count;
                    mInsertId = index;
                    break;
                }
                count++;
            }

            if (find_index) {
                find_index = false;
                recNum = num;
                for (int i = 0; i < num; i++) {
                    mInsertId += mUsimPhoneBookManager.mAdnRecordSizeArray[i];
                }
                Rlog.i(LOG_TAG, "updateUSIMAdnBySearch (3)");
                Rlog.i(LOG_TAG, "mInsertId" + mInsertId);

                AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);

                int updateEmailResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_EMAIL,
                        recNum,
                        adnRecordLoader, mInsertId, index, efid, oldAdn, newAdn, iapEF, pin2,/* SPRD: PhoneBook for AAS*/null);
                Rlog.d(LOG_TAG, "updateEmailResult = " + updateEmailResult);
                if (updateEmailResult == -1) {
                    // in the first pbr,no subject found, search in the second
                    // pbr
                    if (recNum == mUsimPhoneBookManager.getNumRecs() - 1) {
                        sendErrorResponse(response,
                                IccPhoneBookOperationException.EMAIL_CAPACITY_FULL,
                                "Email capacity full");
                        return;
                    } else {
                        Rlog.d(LOG_TAG,
                                "in the first pbr,no subject found, search in the second pbr");
                        find_index = false;
                        continue;
                    }
                }
                Message pendingResponse = mUserWriteResponse.get(efid);
                if (pendingResponse != null) {
                    sendErrorResponse(response,
                            IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                            "Have pending update for EF:" + efid);
                    return;
                }
                mUserWriteResponse.put(efid, response);

                /* SPRD: PhoneBook for AAS {@ */
                int[] aasIndex = null;
                if (TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
                    newAdn.mAas = newAdn.mAas == null ? "" : newAdn.mAas;
                    ArrayList<String> aasArr = loadAasFromUsim();
                    Rlog.d(LOG_TAG,"aasArr == " +aasArr + "newAdn.mAas" + newAdn.mAas);

                    if(aasArr != null && newAdn.mAas != null) {
                        String[] aas = newAdn.mAas.split(AdnRecord.ANR_SPLIT_FLG);
                        aasIndex = new int[aas.length];
                        for (int i = 0; i < aas.length; i++) {
                            aasIndex[i] = aasArr.indexOf(aas[i].toString()) + 1;
                            Rlog.d(LOG_TAG,"aasIndex == " + aasIndex[i]);
                        }
                    }
                }
                /* @} */
                int updateAnrResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_ANR,
                        recNum,
                        adnRecordLoader, mInsertId, index, efid, oldAdn, newAdn, iapEF, pin2,/* SPRD: PhoneBook for AAS*/aasIndex);
                if (updateAnrResult < 0) {
                    if (recNum == mUsimPhoneBookManager.getNumRecs() - 1) {
                        Rlog.d(LOG_TAG, "update anr failed");
                        mUserWriteResponse.delete(efid);
                        sendErrorResponse(response,
                                IccPhoneBookOperationException.ANR_CAPACITY_FULL,
                                "Anr capacity full");
                        return;
                     }else{
                        Rlog.d(LOG_TAG,
                                "in the first pbr,no subject found, search in the second pbr");
                        find_index = false;
                        continue;
                     }
                }
                /* SPRD: PhoneBook for SNE {@ */
                if (TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
                    int updateSneResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_SNE, recNum,
                            adnRecordLoader, mInsertId, index, efid, oldAdn, newAdn, iapEF, pin2,null);
                    Rlog.d(LOG_TAG, "updateSneResult = " + updateSneResult);
                }
                /* @} */
                updateGrpOfAdn(adnRecordLoader, index, recNum, oldAdn, newAdn, pin2);
                if (newAdn.extRecordIsNeeded()) {
                    byte extIndex = getAvailableExtIndex(extensionEF, efid);
                    Rlog.d(LOG_TAG, "extIndex = " + (byte) extIndex);
                    if (extIndex == (byte) 0xFF) {
                        Rlog.d(LOG_TAG, "ext list full");
                        mUserWriteResponse.delete(efid);
                        sendErrorResponse(response,
                                IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                                "ext list full");
                        return;
                    }
                    newAdn.mExtRecord = (int) extIndex;
                    new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF,
                            extIndex, pin2,
                            obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
                }
                new AdnRecordLoader(mFh).updateEFAdnToUsim(newAdn, efid, extensionEF, index,
                        pin2, obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, index,
                                newAdn),getRecordsSizeByEf(efid));

                break;
            }
        }
        if (index == -1) {
            sendErrorResponse(response, IccPhoneBookOperationException.ADN_CAPACITY_FULL,
                    "Adn record don't exist for " + oldAdn);
            return;
        }
        Rlog.i(LOG_TAG, "updateUSIMAdnBySearch  finish");
    }

    public void insertLndBySearch(int efid, AdnRecord oldLnd, AdnRecord newLnd, String pin2,
            Message response) {

        int extensionEF;
        extensionEF = extensionEfForEf(efid);
        Rlog.d(LOG_TAG, "insertLndBySearch:efid" + efid + " extensionEF" + extensionEF);
        if (extensionEF < 0) {
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED ,"EF is not known LND-like EF:" + efid);
            return;
        }
        removedRecordsIfLoaded(efid);
        Message pendingResponse = mUserWriteResponse.get(efid);
        if (pendingResponse != null) {
            sendErrorResponse(response,IccPhoneBookOperationException.WRITE_OPREATION_FAILED , "Have pending update for EF:" + efid);
            return;
        }
        mUserWriteResponse.put(efid, response);
        new AdnRecordLoader(mFh).updateEFCyclic(newLnd, efid, extensionEF, 0, pin2,
                obtainMessage(EVENT_UPDATE_CYCLIC_DONE, efid, 0, newLnd));

    }

    private void sendErrorResponse(Message response,  int errCode, String errString) {
        if (response != null) {
            Exception e = new IccPhoneBookOperationException(errCode,errString);
            AsyncResult.forMessage(response).exception = e;
            response.sendToTarget();
        }
    }

    private byte[] gasToByte(String gas, int recordSize) {

        byte[] gasByte = new byte[recordSize];
        byte[] data;
        for (int i = 0; i < recordSize; i++) {
            gasByte[i] = (byte) 0xFF;
        }

      if (!TextUtils.isEmpty(gas)) {
            try {
              /* SPRD:add SIMPhoneBook for bug 431053 @{ */
              data = GsmAlphabet.stringToGsmAlphaSS(gas);
              /* @} */
                System.arraycopy(data, 0, gasByte, 0, data.length);
            } catch (EncodeException ex) {
                try {
                    data = gas.getBytes("utf-16be");
                    System.arraycopy(data, 0, gasByte, 1, data.length);
                    gasByte[0] = (byte) 0x80;
                } catch (java.io.UnsupportedEncodingException ex2) {
                    Rlog.e(LOG_TAG, "gas convert byte exception");
                } catch (ArrayIndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "over the length of group name");
                    return null;
                }
            }catch (ArrayIndexOutOfBoundsException ex) {
                Rlog.e(LOG_TAG, "over the length of group name");
                return null;
            }
        }
        return gasByte;
    }

    public int updateGasBySearch(String oldGas, String newGas) {

        ArrayList<String> oldGasList = mUsimPhoneBookManager.loadGasFromUsim();

        if (oldGasList == null) {
            Rlog.e(LOG_TAG, "Gas list not exist");
            return -1;
        }

        int index = -1;
        int count = 1;
        for (Iterator<String> it = oldGasList.iterator(); it.hasNext();) {
            if (oldGas.equals(it.next())) {
                index = count;
                break;
            }
            count++;
        }

        if (index == -1) {
            Rlog.e(LOG_TAG, "Gas record don't exist for " + oldGas);
            return IccPhoneBookOperationException.GROUP_CAPACITY_FULL;
        }

        int gasEfId = mUsimPhoneBookManager.findEFGasInfo();
        int[] gasSize = new AdnRecordLoader(mFh).getRecordsSize(gasEfId);
        if (gasSize == null) return -1;
        byte[] data = gasToByte(newGas, gasSize[0]);
        if (data == null) {
            Rlog.d(LOG_TAG, "data == null");
            return IccPhoneBookOperationException.OVER_GROUP_NAME_MAX_LENGTH;
        }
        new AdnRecordLoader(mFh).updateEFGasToUsim(gasEfId, index, data, null);
        mUsimPhoneBookManager.updateGasList(newGas, index);
        return index;
    }

    public int updateGasByIndex(String newGas, int groupId) {

        int gasEfId = mUsimPhoneBookManager.findEFGasInfo();
        int[] gasSize = new AdnRecordLoader(mFh).getRecordsSize(gasEfId);
        if (gasSize == null) return IccPhoneBookOperationException.WRITE_OPREATION_FAILED;

        byte[] data = gasToByte(newGas, gasSize[0]);
        if (data == null) {
            Rlog.d(LOG_TAG, "data == null");
            return IccPhoneBookOperationException.OVER_GROUP_NAME_MAX_LENGTH;
        }
        new AdnRecordLoader(mFh).updateEFGasToUsim(gasEfId, groupId, data, null);
        mUsimPhoneBookManager.updateGasList(newGas, groupId);
        return groupId;
    }

    public synchronized void updateUSIMAdnByIndex(int efid, int simIndex, AdnRecord newAdn,
            String pin2, Message response) {

        int extensionEF = 0;
        int adnIndex = -1;
        int iapEF = 0;

        int recNum = 0;
        AdnRecord oldAdn;

        int pbrRecNum = mUsimPhoneBookManager.getNumRecs();
        Rlog.i(LOG_TAG, "updateUSIMAdnByIndex efid " + Integer.toHexString(efid) +
                " RecsNum: " + mUsimPhoneBookManager.getNumRecs()
                + "simIndex: " + simIndex);

        if (simIndex < 0 || simIndex > mUsimPhoneBookManager.getPhoneBookRecordsNum()) {
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED
                    , "the sim index is invalid");
            return;
        }

        int baseNum = mUsimPhoneBookManager.mAdnRecordSizeArray[0];
        Rlog.d(LOG_TAG, "baseNum=" + baseNum + " simIndex=" + simIndex);
        for (int i = 0; i < pbrRecNum; i++) {
            if (simIndex <= baseNum) {
                recNum = i;
                baseNum -= mUsimPhoneBookManager.mAdnRecordSizeArray[i];
                break;
            }
            baseNum += mUsimPhoneBookManager.mAdnRecordSizeArray[i + 1];
        }
        adnIndex = simIndex - baseNum;
        mInsertId = simIndex;

        efid = mUsimPhoneBookManager.findEFInfo(recNum);
        extensionEF = mUsimPhoneBookManager.findExtensionEFInfo(recNum);

        iapEF = mUsimPhoneBookManager.findEFIapInfo(recNum);

        Rlog.i(LOG_TAG, "adn efid:" + Integer.toHexString(efid) + "  extensionEF:"
                + Integer.toHexString(extensionEF) + "  iapEF:" + Integer.toHexString(iapEF));

        if (efid < 0 || extensionEF < 0) {
            sendErrorResponse(
                    response,
                    IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                    "EF is not known ADN-like EF:"
                            + "efid" + Integer.toHexString(efid) + ",extensionEF="
                            + Integer.toHexString(extensionEF));
            return;
        }

        ArrayList<AdnRecord> oldAdnList = getRecordsIfLoaded(efid);
        if (oldAdnList == null) {
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                    "Adn list not exist for EF:" + efid);
            return;
        }
        oldAdn = oldAdnList.get(adnIndex - 1);

        Rlog.i(LOG_TAG, "recNum: " + recNum + " simIndex:" + simIndex + " adnIndex:" + adnIndex
                + " mInsertId:" + mInsertId + " oldAdn:" + oldAdn);

        Message pendingResponse = mUserWriteResponse.get(efid);

        if (pendingResponse != null) {
            sendErrorResponse(response, IccPhoneBookOperationException.WRITE_OPREATION_FAILED,
                    "Have pending update for EF:" + efid);
            return;
        }

        mUserWriteResponse.put(efid, response);
        AdnRecordLoader adnRecordLoader = new AdnRecordLoader(mFh);
        /* SPRD: PhoneBook for AAS {@ */
        int[] aasIndex = null;
        if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
            newAdn.mAas = newAdn.mAas == null ? "" : newAdn.mAas;
            ArrayList<String> aasArr = loadAasFromUsim();
            Rlog.d(LOG_TAG,"aasArr == " +aasArr + "newAdn.mAas" + newAdn.mAas);
            if(aasArr != null && newAdn.mAas != null) {
               String[] aas = newAdn.mAas.split(AdnRecord.ANR_SPLIT_FLG);
               aasIndex = new int[aas.length];
               for(int i = 0; i< aas.length; i++) {
                     aasIndex[i] = aasArr.indexOf(aas[i].toString()) + 1;
                     Rlog.d(LOG_TAG,"aasArr == " + aasIndex[i]);
               }
           }
        }
        /* @} */
        int updateAnrResult = updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_ANR, recNum,
                adnRecordLoader, mInsertId, adnIndex, efid, oldAdn, newAdn, iapEF, pin2,/* SPRD: PhoneBook for AAS*/aasIndex);
        if (updateAnrResult < 0) {
            return;
        }
        updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_EMAIL, recNum,
                adnRecordLoader, mInsertId, adnIndex, efid, oldAdn, newAdn, iapEF, pin2,/* SPRD: PhoneBook for AAS*/null);

        /* SPRD: PhoneBook for SNE {@ */
        if (TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
            updateSubjectOfAdn(UsimPhoneBookManager.USIM_SUBJCET_SNE, recNum,
                    adnRecordLoader, mInsertId, adnIndex, efid, oldAdn, newAdn, iapEF, pin2,null);
        }
        /* @} */
        updateGrpOfAdn(adnRecordLoader, adnIndex, recNum, oldAdn, newAdn, pin2);
        oldAdn.mExtRecord = 0xff;
        if (newAdn.extRecordIsNeeded()) {
            byte extIndex = getAvailableExtIndex(extensionEF, efid);
            Rlog.d(LOG_TAG, "extIndex = " + extIndex);
            if (extIndex == (byte) 0xFF) {
                Rlog.d(LOG_TAG, "ext list full");
                mUserWriteResponse.delete(efid);
                sendErrorResponse(response, IccPhoneBookOperationException.OVER_NUMBER_MAX_LENGTH,
                        "ext list full");
                return;
            }
            newAdn.mExtRecord = (int) extIndex;
            new AdnRecordLoader(mFh).updateExtEF(newAdn, efid, extensionEF,
                    extIndex, pin2,
                    obtainMessage(EVENT_UPDATE_EXT_DONE, extensionEF, extIndex));
        }
        new AdnRecordLoader(mFh).updateEFAdnToUsim(newAdn, efid, extensionEF, adnIndex,
                pin2, obtainMessage(EVENT_UPDATE_USIM_ADN_DONE, efid, adnIndex,
                        newAdn),getRecordsSizeByEf(efid));
        Rlog.i(LOG_TAG, "updateUSIMAdnByIndex  finish");
    }

    public int getAdnIndex(int efid, AdnRecord oldAdn) {
        ArrayList<AdnRecord> oldAdnList;
        oldAdnList = getRecordsIfLoaded(efid);
        Rlog.i("AdnRecordCache", "getAdnIndex efid " + efid);
        if (oldAdnList == null) {
            return -1;
        }
        Rlog.i("AdnRecordCache", "updateAdnBySearch (2)");
        int index = -1;
        int count = 1;
        for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
            if (oldAdn.isEqual(it.next())) {
                index = count;
                break;
            }
            count++;
        }
        return index;
    }

    public UsimPhoneBookManager getUsimPhoneBookManager() {
        return mUsimPhoneBookManager;
    }

    public int getAdnLikeSize() {
        return mAdnLikeFiles.size();
    }

    /*@}*/
    /* SPRD: PhoneBook for AAS {@ */
    public ArrayList<String> loadAasFromUsim() {
        return mUsimPhoneBookManager.loadAasFromUsim();
    }

    public int updateAasBySearch(String oldAas, String newAas) {
        ArrayList<String> oldAasList = mUsimPhoneBookManager.loadAasFromUsim();

        if (oldAasList == null) {
            Rlog.e(LOG_TAG, "Aas list not exist");
            return -1;
        }

        int index = -1;
        int count = 1;
        for (Iterator<String> it = oldAasList.iterator(); it.hasNext();) {
            if (oldAas.equals(it.next())) {
                index = count;
                break;
            }
            count++;
        }

        if (index == -1) {
            Rlog.e(LOG_TAG, "Aas record don't exist for " + oldAas);
            return IccPhoneBookOperationException.AAS_CAPACITY_FULL;
        }

        int aasEfId = mUsimPhoneBookManager.findEFAasInfo();
        Rlog.d(LOG_TAG,"Aas aasEfId == " + aasEfId);
        int[] aasSize = new AdnRecordLoader(mFh).getRecordsSize(aasEfId);
        if (aasSize == null) {
            return -1;
        }
        byte[] data = gasToByte(newAas, aasSize[0]);
        if (data == null) {
            Rlog.d(LOG_TAG, "data == null");
            return IccPhoneBookOperationException.OVER_AAS_MAX_LENGTH;
        }
        new AdnRecordLoader(mFh).updateEFGasToUsim(aasEfId, index, data, null);
        mUsimPhoneBookManager.updateAasList(newAas, index);
        return index;
    }

    public int updateAasByIndex(String newAas, int aasIndex) {
        int aasEfId = mUsimPhoneBookManager.findEFAasInfo();
        int[] aasSize = new AdnRecordLoader(mFh).getRecordsSize(aasEfId);
        if (aasSize == null) {
            return IccPhoneBookOperationException.WRITE_OPREATION_FAILED;
        }

        byte[] data = gasToByte(newAas, aasSize[0]);
        if (data == null) {
            Rlog.d(LOG_TAG, "data == null");
            return IccPhoneBookOperationException.OVER_AAS_MAX_LENGTH;
        }
        new AdnRecordLoader(mFh).updateEFGasToUsim(aasEfId, aasIndex, data, null);
        mUsimPhoneBookManager.updateAasList(newAas, aasIndex);
        return aasIndex;
    }

    private String[] getSubjectAasString(int type, AdnRecord adn) {
      String[] s1 = null;
      switch (type) {
          case UsimPhoneBookManager.USIM_SUBJCET_ANR:
                s1 = getAnrNumGroup(adn.mAas);
                break;
          default:
                break;
      }
      return s1;
    }
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    public int getSneSize() {
        return mUsimPhoneBookManager.getSneSize();
    }

    public int[] getSneLength(){
        return mUsimPhoneBookManager.getSneLength();
    }
    /* @} */
}
