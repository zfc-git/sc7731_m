/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.plugin.TelephonyForOrangeUtils;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * This class implements reading and parsing USIM records.
 * Refer to Spec 3GPP TS 31.102 for more details.
 *
 * {@hide}
 */
public class UsimPhoneBookManager extends Handler implements IccConstants {
    private static final String LOG_TAG = "UsimPhoneBookManager";
    private static final boolean DBG = true;
    private PbrFile mPbrFile;
    private Boolean mIsPbrPresent;
    private IccFileHandler mFh;
    private AdnRecordCache mAdnCache;
    private Object mLock = new Object();
    private ArrayList<AdnRecord> mPhoneBookRecords;
    private boolean mEmailPresentInIap = false;
    private int mEmailTagNumberInIap = 0;
    private ArrayList<byte[]> mIapFileRecord;
    private ArrayList<byte[]> mEmailFileRecord;
    private Map<Integer, ArrayList<String>> mEmailsForAdnRec;
    private boolean mRefreshCache = false;

    private static final int EVENT_PBR_LOAD_DONE = 1;
    private static final int EVENT_USIM_ADN_LOAD_DONE = 2;
    private static final int EVENT_IAP_LOAD_DONE = 3;
    private static final int EVENT_EMAIL_LOAD_DONE = 4;

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    //private static final int USIM_TYPE1_TAG   = 0xA8;
    //private static final int USIM_TYPE2_TAG   = 0xA9;
    public static final int USIM_TYPE1_TAG = 0xA8;
    public static final int USIM_TYPE2_TAG = 0xA9;
    private static final int USIM_TYPE3_TAG   = 0xAA;
    private static final int USIM_EFADN_TAG   = 0xC0;
    private static final int USIM_EFIAP_TAG   = 0xC1;
    private static final int USIM_EFEXT1_TAG  = 0xC2;
    //private static final int USIM_EFSNE_TAG   = 0xC3;
    //private static final int USIM_EFANR_TAG   = 0xC4;
    public static final int USIM_EFSNE_TAG = 0xC3;
    public static final int USIM_EFANR_TAG = 0xC4;
    private static final int USIM_EFPBC_TAG   = 0xC5;
    //private static final int USIM_EFGRP_TAG   = 0xC6;
    public static final int USIM_EFGRP_TAG   = 0xC6;
    //private static final int USIM_EFAAS_TAG   = 0xC7;
    public static final int USIM_EFAAS_TAG = 0xC7;
    //private static final int USIM_EFGSD_TAG   = 0xC8;
    public static final int USIM_EFGAS_TAG = 0xC8;
    /*@}*/
    private static final int USIM_EFUID_TAG   = 0xC9;
    //private static final int USIM_EFEMAIL_TAG = 0xCA;
    public static final int USIM_EFEMAIL_TAG = 0xCA;
    private static final int USIM_EFCCP1_TAG  = 0xCB;
    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    private Object[] mIapFileRecordArray;
    public int[] mAdnRecordSizeArray;
    private int[] mEmailRecordSizeArray;
    private int[] mIapRecordSizeArray;
    private int[] mAnrRecordSizeArray;
    public int mAnrFileCount = 0;
    private int mDoneAdnCount = 0;
    public int mGrpCount = 0;
    private int changedCounter;
    protected int mTotalSize[] = null;
    protected int recordSize[] = new int[3];
    private boolean mAnrPresentInIap = false;
    private boolean mIsPbrFileExisting = true;
    public HashMap<Integer, int[]> mRecordsSize;
    private AtomicInteger mPendingIapLoads=new AtomicInteger(0);
    private AtomicInteger mPendingGrpLoads=new AtomicInteger(0);
    private AtomicInteger mPendingAnrLoads=new AtomicInteger(0);
    private AtomicInteger mPendingPbcLoads = new AtomicInteger(0);
    private AtomicBoolean mIsNotify = new AtomicBoolean(false);

    public boolean mIshaveEmail;
    public boolean mIshaveAnr;
    public boolean mIshaveAas;
    public boolean mIshaveSne;
    public boolean mIshaveGrp;
    public boolean mIshaveGas;

    private ArrayList<byte[]> mAnrFileRecord;
    private ArrayList<byte[]> mGrpFileRecord;
    private ArrayList<byte[]> mPbcFileRecord;
    private ArrayList<byte[]> mGasFileRecord;
    private ArrayList<String> mGasList;

    private static final int EVENT_ADN_RECORD_COUNT = 5;
    private static final int EVENT_AAS_LOAD_DONE = 6;
    private static final int EVENT_SNE_LOAD_DONE = 7;
    private static final int EVENT_GRP_LOAD_DONE = 8;
    private static final int EVENT_GAS_LOAD_DONE = 9;
    private static final int EVENT_ANR_LOAD_DONE = 10;
    private static final int EVENT_PBC_LOAD_DONE = 11;
    public static final int EVENT_EF_CC_LOAD_DONE = 12;
    private static final int EVENT_UPDATE_RECORD_DONE = 13;
    private static final int EVENT_LOAD_EF_PBC_RECORD_DONE = 14;
    /* SPRD:add for TS31.121 8.1.2  @{ */
    private static final int EVENT_EF_PUID_LOAD_DONE = 15;
    private static final int EVENT_UPDATE_UID_DONE = 16;
    private static final int EVENT_EF_PSC_LOAD_DONE = 17;
    private static final int EVENT_UPDATE_CC_DONE = 18;
    private static final int EVENT_GET_RECORDS_COUNT = 19;
    private static final int EVENT_ANR_RECORD_LOAD_DONE = 20;
    /* @} */

    public static final int USIM_SUBJCET_EMAIL = 0;
    public static final int USIM_SUBJCET_ANR = 1;
    public static final int USIM_SUBJCET_GRP = 2;

    private LinkedList<SubjectIndexOfAdn> mAnrInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    private LinkedList<SubjectIndexOfAdn> mEmailInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    /* SPRD: PhoneBook for AAS {@ */
    private ArrayList<String> mAasList;
    private ArrayList<byte[]> mAasFileRecord;
    public static final int USIM_SUBJCET_AAS = 3;
    private LinkedList<SubjectIndexOfAdn> mAasInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    public static final int USIM_SUBJCET_SNE = 4;
    private ArrayList<byte[]> mSneFileRecord;
    private int mSneEfSize = 0;
    private boolean mSnePresentInIap = false;
    private int mSneTagNumberInIap = 0;
    private LinkedList<SubjectIndexOfAdn> mSneInfoFromPBR = new LinkedList<SubjectIndexOfAdn>();
    protected int sneRecordSize[] = new int[3];
    private static final int EVENT_SNE_RECORD_COUNT = 21;
    /* @} */

    public class SubjectIndexOfAdn {

        public int adnEfid;
        public int[] type;
        // <efid, record >
        public Map<Integer, Integer> recordNumInIap;
        // map <efid,ArrayList<byte[]>fileRecord >
        public Map<Integer, ArrayList<byte[]>> record;

        public int[] efids;

        // ArrayList<int[]> usedNumSet;
        Object[] usedSet;

    };
    /*@}*/

    public UsimPhoneBookManager(IccFileHandler fh, AdnRecordCache cache) {
        mFh = fh;
        mPhoneBookRecords = new ArrayList<AdnRecord>();
        mPbrFile = null;
        // We assume its present, after the first read this is updated.
        // So we don't have to read from UICC if its not present on subsequent reads.
        mIsPbrPresent = true;
        mAdnCache = cache;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        mGasList = new ArrayList<String>();
        mIshaveEmail = false;
        mIshaveAas = false;
        mIshaveSne = false;
        mIshaveGrp = false;
        mIshaveGas = false;
        mTotalSize = null;
        /*@}*/
        // SPRD: PhoneBook for AAS
        mAasList = new ArrayList<String>();
    }

    public void reset() {
        mPhoneBookRecords.clear();
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        mGasList.clear();
        /*@}*/
        mIapFileRecord = null;
        mEmailFileRecord = null;
        mPbrFile = null;
        mIsPbrPresent = true;
        mRefreshCache = false;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        mAnrFileRecord = null;
        mGrpFileRecord = null;
        mGasFileRecord = null;
        mPbcFileRecord = null;
        mIshaveEmail = false;
        mIshaveAnr = false;
        mIshaveAas = false;
        mIshaveSne = false;
        mIshaveGrp = false;
        mIshaveGas = false;
        mTotalSize = null;
        mAnrFileCount = 0;
        mAnrInfoFromPBR = null;
        mEmailInfoFromPBR = null;
        /*@}*/
        // SPRD: PhoneBook for AAS
        mAasList.clear();
    }

    public ArrayList<AdnRecord> loadEfFilesFromUsim() {
        synchronized (mLock) {
            if (!mPhoneBookRecords.isEmpty()) {
                /* @orig
                if (mRefreshCache) {
                    mRefreshCache = false;
                    refreshCache();
                }
                */
                return mPhoneBookRecords;
            }

            if (!mIsPbrPresent) return null;

            // Check if the PBR file is present in the cache, if not read it
            // from the USIM.
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            Rlog.i(LOG_TAG, "loadEfFilesFromUsim, mPbrFile:" + mPbrFile);
            /*@}*/
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }

            if (mPbrFile == null) {
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                mIsPbrFileExisting = false;
                Rlog.i(LOG_TAG, "mIsPbrFileExisting = false");
                /*@}*/
                return null;
            }

            int numRecs = mPbrFile.mFileIds.size();
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            mIapFileRecordArray = new Object[numRecs];
            mAdnRecordSizeArray = new int[numRecs];
            mEmailRecordSizeArray = new int[numRecs];
            mIapRecordSizeArray = new int[numRecs];
            mAnrRecordSizeArray = new int[numRecs];

            boolean isGotAdnSize = true;
            mDoneAdnCount = 0;
            Rlog.i(LOG_TAG, "loadEfFilesFromUsim, numRecs:" + numRecs);
            Rlog.i(LOG_TAG, "loadEfFilesFromUsim, mTotalSize:" + mTotalSize);
            if (mTotalSize == null) {
                mTotalSize = new int[3];
                isGotAdnSize = false;
            }
            /*@}*/
            for (int i = 0; i < numRecs; i++) {
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                Rlog.i(LOG_TAG, "loadEfFilesFromUsim, the current record num is:" + i);
                /*@}*/
                readAdnFileAndWait(i);
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                if (!isGotAdnSize) {
                    int[] size = readAdnFileSizeAndWait(i);

                    if (size != null) {
                        mTotalSize[0] = size[0];
                        mTotalSize[1] += size[1];
                        mTotalSize[2] += size[2];
                        /* SPRD:add for Bug 448722  @{ */
                        Rlog.i(LOG_TAG, "mTotalSize[0]" + mTotalSize[0] + "mTotalSize[1]" + mTotalSize[1] + "mTotalSize[2]" + mTotalSize[2] );
                        /* @} */
                    }
                }
                readIapFile(i);
                /*@}*/
                readEmailFileAndWait(i);
                /* SPRD: PhoneBook for SNE {@ */
                if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
                    readSneFileAndWait(i);
                }
                /* @} */
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                readAnrFileAndWait(i);
                /* SPRD:add for Bug 448722  @{ */
                readGrpFileAndWait(i, mTotalSize[2]);
                /* @} */
                updateAdnRecord(i);
                /*@}*/
            }
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            CheckRepeatType2Ef();
            /*@}*/
            // All EF files are loaded, post the response.
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            // update the adn recordNumber
            updateAdnRecordNum();
            // for cta case8.1.1,update the EFpbc&Efcc
            updatePbcAndCc();
            /*@}*/
        }
        return mPhoneBookRecords;
    }

    private void refreshCache() {
        if (mPbrFile == null) return;
        mPhoneBookRecords.clear();

        int numRecs = mPbrFile.mFileIds.size();
        for (int i = 0; i < numRecs; i++) {
            readAdnFileAndWait(i);
        }
    }

    public void invalidateCache() {
        mRefreshCache = true;
    }

    private void readPbrFileAndWait() {
        mFh.loadEFLinearFixedAll(EF_PBR, obtainMessage(EVENT_PBR_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
    }

    private void readEmailFileAndWait(int recNum) {
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        log("readEmailFileAndWait");

        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }

        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return;
        }
        /*@}*/
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null) return;

        if (fileIds.containsKey(USIM_EFEMAIL_TAG)) {
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            mIshaveEmail = true;
            /*@}*/
            int efid = fileIds.get(USIM_EFEMAIL_TAG);
            // Check if the EFEmail is a Type 1 file or a type 2 file.
            // If mEmailPresentInIap is true, its a type 2 file.
            // So we read the IAP file and then read the email records.
            // instead of reading directly.
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_EMAIL, recNum);
            if (records == null) {
                log("readEmailFileAndWait  records == null ");
                return;
            }
            /*@}*/

            if (mEmailPresentInIap) {
                /* @orig
                readIapFileAndWait(fileIds.get(USIM_EFIAP_TAG));
                */
                if (mIapFileRecord == null) {
                    Rlog.e(LOG_TAG, "Error: IAP file is empty");
                    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                    records = null;
                    setSubjectIndex(USIM_SUBJCET_EMAIL, recNum, records);
                    /*@}*/
                    return;
                }
            }
            // Read the EFEmail file.
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            Integer efId = fileIds.get(USIM_EFEMAIL_TAG);
            if (efId == null)
                return;
            /*@}*/
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            /*mFh.loadEFLinearFixedAll(fileIds.get(USIM_EFEMAIL_TAG),
                    obtainMessage(EVENT_EMAIL_LOAD_DONE));*/
            mFh.loadEFLinearFixedAll(efId, obtainMessage(EVENT_EMAIL_LOAD_DONE));
            /*@}*/

            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readEmailFileAndWait");
            }

            if (mEmailFileRecord == null) {
                Rlog.e(LOG_TAG, "Error: Email file is empty");
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                records = null;
                setSubjectIndex(USIM_SUBJCET_EMAIL, recNum, records);
                /*@}*/
                return;
            }

            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //updatePhoneAdnRecord();
            records.record = new HashMap<Integer, ArrayList<byte[]>>();
            records.record.put(efid, mEmailFileRecord);
            log("readEmailFileAndWait recNum " + recNum + "  mEmailFileRecord  size "
                    + mEmailFileRecord.size() + ", fileid:" + fileIds.get(USIM_EFEMAIL_TAG));
            setSubjectIndex(USIM_SUBJCET_EMAIL, recNum, records);
            setSubjectUsedNum(USIM_SUBJCET_EMAIL, recNum);

            mEmailFileRecord = null;
            /*@}*/
        }

    }

    private void readIapFileAndWait(int efid) {
        mFh.loadEFLinearFixedAll(efid, obtainMessage(EVENT_IAP_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    private void updatePhoneAdnRecord() {
        if (mEmailFileRecord == null) return;
        int numAdnRecs = mPhoneBookRecords.size();
        if (mIapFileRecord != null) {
            // The number of records in the IAP file is same as the number of records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the reference file record.
            // i.e value of mEmailTagNumberInIap

            for (int i = 0; i < numAdnRecs; i++) {
                byte[] record = null;
                try {
                    record = mIapFileRecord.get(i);
                } catch (IndexOutOfBoundsException e) {
                    Rlog.e(LOG_TAG, "Error: Improper ICC card: No IAP record for ADN, continuing");
                    break;
                }
                int recNum = record[mEmailTagNumberInIap];

                if (recNum != -1) {
                    String[] emails = new String[1];
                    // SIM record numbers are 1 based
                    emails[0] = readEmailRecord(recNum - 1);
                    AdnRecord rec = mPhoneBookRecords.get(i);
                    if (rec != null) {
                        rec.setEmails(emails);
                    } else {
                        // might be a record with only email
                        rec = new AdnRecord("", "", emails);
                    }
                    mPhoneBookRecords.set(i, rec);
                }
            }
        }

        // ICC cards can be made such that they have an IAP file but all
        // records are empty. So we read both type 1 and type 2 file
        // email records, just to be sure.

        int len = mPhoneBookRecords.size();
        // Type 1 file, the number of records is the same as the number of
        // records in the ADN file.
        if (mEmailsForAdnRec == null) {
            parseType1EmailFile(len);
        }
        for (int i = 0; i < numAdnRecs; i++) {
            ArrayList<String> emailList = null;
            try {
                emailList = mEmailsForAdnRec.get(i);
            } catch (IndexOutOfBoundsException e) {
                break;
            }
            if (emailList == null) continue;

            AdnRecord rec = mPhoneBookRecords.get(i);

            String[] emails = new String[emailList.size()];
            System.arraycopy(emailList.toArray(), 0, emails, 0, emailList.size());
            rec.setEmails(emails);
            mPhoneBookRecords.set(i, rec);
        }
    }

    void parseType1EmailFile(int numRecs) {
        mEmailsForAdnRec = new HashMap<Integer, ArrayList<String>>();
        byte[] emailRec = null;
        for (int i = 0; i < numRecs; i++) {
            try {
                emailRec = mEmailFileRecord.get(i);
            } catch (IndexOutOfBoundsException e) {
                Rlog.e(LOG_TAG, "Error: Improper ICC card: No email record for ADN, continuing");
                break;
            }
            int adnRecNum = emailRec[emailRec.length - 1];

            if (adnRecNum == -1) {
                continue;
            }

            String email = readEmailRecord(i);

            if (email == null || email.equals("")) {
                continue;
            }

            // SIM record numbers are 1 based.
            ArrayList<String> val = mEmailsForAdnRec.get(adnRecNum - 1);
            if (val == null) {
                val = new ArrayList<String>();
            }
            val.add(email);
            // SIM record numbers are 1 based.
            mEmailsForAdnRec.put(adnRecNum - 1, val);
        }
    }

    private String readEmailRecord(int recNum) {
        byte[] emailRec = null;
        try {
            emailRec = mEmailFileRecord.get(recNum);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }

        // The length of the record is X+2 byte, where X bytes is the email address
        String email = IccUtils.adnStringFieldToString(emailRec, 0, emailRec.length - 2);
        return email;
    }

    /*SPRD:Modify this method with return values .add SIMPhoneBook for bug 474587 .@{ */
    //private void readAdnFileAndWait(int recNum) {
    private int[] readAdnFileAndWait(int recNum) {
        Rlog.i(LOG_TAG, "readAdnFileAndWait");
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            mIsPbrFileExisting = false;
            Rlog.i(LOG_TAG, "mIsPbrFileExisting = false");
            return null;
        }
    /*@}*/
        Map <Integer,Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null || fileIds.isEmpty())
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //return;
            return null;
            /*@}*/

        int extEf = 0;
        // Only call fileIds.get while EFEXT1_TAG is available
        if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
            extEf = fileIds.get(USIM_EFEXT1_TAG);
        }

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        Integer efId = fileIds.get(USIM_EFADN_TAG);
        if (efId == null)
            return null;
        Rlog.d(LOG_TAG, "readAdnFileAndWait:efid = "+ efId+"extEf ="+extEf);
        /*@}*/
        mAdnCache.requestLoadAllAdnLike(fileIds.get(USIM_EFADN_TAG),
            extEf, obtainMessage(EVENT_USIM_ADN_LOAD_DONE));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        return recordSize;
        /*@}*/
    }

    private void createPbrFile(ArrayList<byte[]> records) {
        if (records == null) {
            mPbrFile = null;
            mIsPbrPresent = false;
            return;
        }
        mPbrFile = new PbrFile(records);
    }

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        byte data[];
        /*@}*/
        switch(msg.what) {
        case EVENT_PBR_LOAD_DONE:
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                createPbrFile((ArrayList<byte[]>)ar.result);
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_USIM_ADN_LOAD_DONE:
            log("Loading USIM ADN records done");
            ar = (AsyncResult) msg.obj;
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            // add by liguxiang 10-24-11 for NEWMS00132125 begin
            // int size = ((ArrayList<AdnRecord>) ar.result).size();
            int size = 0;
            if ((ar != null) && ((ArrayList<AdnRecord>) ar.result != null)) {
                size = ((ArrayList<AdnRecord>) ar.result).size();
            }
            // add by liguxiang 10-24-11 for NEWMS00132125 end
            log("EVENT_USIM_ADN_LOAD_DONE size " + size);
            /* if (ar.exception == null) {
                mPhoneBookRecords.addAll((ArrayList<AdnRecord>)ar.result);
            }*/
            if ((ar != null) && ar.exception == null) {
                mPhoneBookRecords.addAll((ArrayList<AdnRecord>) ar.result);
            }else if(ar == null){
                log("EVENT_USIM_ADN_LOAD_DONE exception ar is null");
            }else {
                log("EVENT_USIM_ADN_LOAD_DONE exception " + ar.exception);
            }
            /*@}*/
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_IAP_LOAD_DONE:
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //log("Loading USIM IAP records done");
            ar = (AsyncResult) msg.obj;
            int index = msg.arg1;
            /* @} */
            if (ar.exception == null) {
                /* SPRD: @{ */
                if (mIapFileRecord == null) {
                    mIapFileRecord = ((ArrayList<byte[]>)ar.result);
                }
                log("Loading USIM IAP record done "+index);
                mIapFileRecord.set(index, (byte[])ar.result);
            }
            mPendingIapLoads.decrementAndGet();
            if (mPendingIapLoads.get() == 0) {
                if (mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM IAP records done notify");
                }
            }
            /* @} */
            break;
        case EVENT_EMAIL_LOAD_DONE:
            log("Loading USIM Email records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                mEmailFileRecord = ((ArrayList<byte[]>)ar.result);
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                mEmailFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("Loading USIM Email records done size "+ mEmailFileRecord.size());
                /*@}*/
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        case EVENT_ADN_RECORD_COUNT:
            Rlog.i(LOG_TAG, "Loading EVENT_ADN_RECORD_COUNT");
            ar = (AsyncResult) msg.obj;
            synchronized (mLock) {
                if (ar.exception == null) {
                    recordSize = (int[]) ar.result;
                    // recordSize[0] is the record length
                    // recordSize[1] is the total length of the EF file
                    // recordSize[2] is the number of records in the EF file
                    Rlog.i(LOG_TAG, "EVENT_ADN_RECORD_COUNT Size "
                            + recordSize[0] + " total " + recordSize[1]
                            + " #record " + recordSize[2]);
                }
                mLock.notify();
            }
            break;
        case EVENT_GET_RECORDS_COUNT:
            Rlog.i(LOG_TAG, "Loading EVENT_GET_RECORDS_COUNT");
            ar = (AsyncResult) msg.obj;
            synchronized (mLock) {
                if (ar.exception == null) {
                    //recordSize = (int[]) ar.result;
                    // recordSize[0] is the record length
                    // recordSize[1] is the total length of the EF file
                    // recordSize[2] is the number of records in the EF file
//                    Rlog.i(LOG_TAG, "EVENT_GET_RECORDS_COUNT Size "
//                            + recordSize[0] + " total " + recordSize[1]
//                            + " #record " + recordSize[2]);
                    if(mRecordsSize == null){
                        mRecordsSize = new HashMap<Integer, int[]>();
                    }
                    mRecordsSize.put(msg.arg1, (int[]) ar.result);
                }
                mLock.notify();
            }
            break;
        case EVENT_ANR_RECORD_LOAD_DONE:
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if (mAnrFileRecord == null) {
                    mAnrFileRecord = new ArrayList<byte[]>();
                }
                log("Loading USIM ANR record done "+msg.arg1);
                if(msg.arg1 < mAnrFileRecord.size()){
                    mAnrFileRecord.set(msg.arg1, (byte[])ar.result);
                }
            }
            mPendingAnrLoads.decrementAndGet();
            if (mPendingAnrLoads.get() == 0) {
                if (mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM ANR records done notify");
                }
            }
            break;
        case EVENT_ANR_LOAD_DONE:
            log("Loading USIM ANR records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if (mAnrFileRecord == null) {
                    mAnrFileRecord = new ArrayList<byte[]>();
                }

                mAnrFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("mAnrFileRecord.size() is " + mAnrFileRecord.size());
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_GRP_LOAD_DONE:
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                if (mGrpFileRecord == null) {
                    mGrpFileRecord = new ArrayList<byte[]>();
                }
                int i = msg.arg1 + msg.arg2;
                log("Loading USIM Grp record done "+ i);
                /* SPRD:add for Bug 448722  @{ */
                //mGrpFileRecord.set( i, (byte[])ar.result);
                try{
                    mGrpFileRecord.set( i, (byte[])ar.result);
                } catch (IndexOutOfBoundsException exc) {
                    log("IndexOutOfBoundsException readGrpFileAndWait i="+i);
                }
                /* @} */
            }
            mPendingGrpLoads.decrementAndGet();
            if (mPendingGrpLoads.get() == 0) {
                if (mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM Grp records done notify");
                }
            }
            break;
        case EVENT_GAS_LOAD_DONE:
            log("Loading USIM Gas records done, mGasFileRecord:"+mGasFileRecord);
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                //if(mGasFileRecord == null){
                    mGasFileRecord = new ArrayList<byte[]>();
                //}
                mGasFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("Loading USIM Gas records done size "+ mGasFileRecord.size()+", mGasFileRecord:"+mGasFileRecord);
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_LOAD_EF_PBC_RECORD_DONE:
            Rlog.i(LOG_TAG, "Loading EVENT_LOAD_EF_PBC_RECORD_DONE");
            ar = (AsyncResult)(msg.obj);
            if (ar.exception == null) {
                if (mPbcFileRecord == null) {
                    mPbcFileRecord = new ArrayList<byte[]>();
                }
               // mPbcFileRecord.addAll((ArrayList<byte[]>) ar.result);
                mPbcFileRecord.set(msg.arg1, (byte[])ar.result);
                log("Loading USIM PBC record done"+msg.arg1);
            } else {
                log("Loading USIM PBC records failed"+msg.arg1);
            }
            mPendingPbcLoads.decrementAndGet();
            if (mPendingPbcLoads.get() == 0) {
                if (mIsNotify.get()) {
                    mIsNotify.set(false);
                    synchronized (mLock) {
                        mLock.notify();
                    }
                    log("Loading USIM Pbc records done notify");
                }
            }
            break;
        case EVENT_EF_CC_LOAD_DONE:
            ar = (AsyncResult) (msg.obj);
            data = (byte[]) (ar.result);
            int temp = msg.arg1;
            /* SPRD:add for TS31.121 8.1.2  @{ */
            int isUpdateUid = msg.arg2;
            /* @} */
            if (ar.exception != null) {
                Rlog.i(LOG_TAG, "EVENT_EF_CC_LOAD_DONE has exception " + ar.exception);
                break;
            }
            Rlog.i(LOG_TAG, "EVENT_EF_CC_LOAD_DONE data " + IccUtils.bytesToHexString(data));
            if(data == null){
                Rlog.i(LOG_TAG, "EVENT_EF_CC_LOAD_DONE data is null");
                break;
              }
            // update EFcc
            byte[] counter = new byte[2];
            int cc = ((data[0] << 8) & 0xFF00) | (data[1] & 0xFF);
            changedCounter = cc;
            cc += temp;
            if (cc > 0xFFFF) {
                counter[0] = (byte) 0x00;
                counter[1] = (byte) 0x01;
            } else {
                counter[1] = (byte) (cc & 0xFF);
                counter[0] = (byte) (cc >> 8 & 0xFF);
            }
            Rlog.i(LOG_TAG,
                    "EVENT_EF_CC_LOAD_DONE counter " + IccUtils.bytesToHexString(counter));
            /* SPRD:add for TS31.121 8.1.2  @{ */
            if (isUpdateUid == 1) {
                mFh.updateEFTransparent(IccConstants.EF_CC, counter,
                        obtainMessage(EVENT_UPDATE_CC_DONE));
            } else {
                mFh.updateEFTransparent(IccConstants.EF_CC, counter,
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
            }
            /* @} */
            break;
            /* SPRD:add for TS31.121 8.1.2  @{ */
        case EVENT_EF_PUID_LOAD_DONE:
            ar = (AsyncResult) (msg.obj);
            data = (byte[]) (ar.result);
            int recNum = msg.arg1;
            int recordNum = msg.arg2;

            if (ar.exception != null) {
                Log.i(LOG_TAG, "EVENT_EF_PUID_LOAD_DONE has exception " + ar.exception);
                break;
            }
            // update EFuid and EFpuid
            byte[] uidData = new byte[2];
            byte[] newPuid = new byte[2];
            int puid = ((data[0] << 8) & 0xFF00) | (data[1] & 0xFF);
            if (changedCounter == 0xFFFF || puid == 0xFFFF) {
                // update psc
                synchronized (mLock) {
                    mFh.loadEFTransparent(IccConstants.EF_PSC, 4,
                            obtainMessage(EVENT_EF_PSC_LOAD_DONE));
                }
                // regenerate uids
                int uid = 0;
                int[] adnRecordSizeArray = getAdnRecordSizeArray();
                for (int num = 0; num < getNumRecs(); num++) {
                    for (int i = 0; i < adnRecordSizeArray[num]; i++) {
                        int adnRecNum = i;
                        for (int j = 0; j < num; j++) {
                            adnRecNum += adnRecordSizeArray[j];
                        }
                        if (!TextUtils.isEmpty(mPhoneBookRecords.get(adnRecNum).getAlphaTag())
                                || !TextUtils.isEmpty(mPhoneBookRecords.get(adnRecNum)
                                        .getNumber())) {
                            uid++;
                            uidData = IccUtils.intToBytes(uid, 2);
                            synchronized (mLock) {

                                mFh.updateEFLinearFixed(getEfIdByTag(num, USIM_EFUID_TAG),
                                        i + 1, uidData, null,
                                        obtainMessage(EVENT_UPDATE_UID_DONE, -1));
                            }
                        }
                    }
                }

                // update puid
                newPuid = IccUtils.intToBytes(uid, 2);
                Log.d(LOG_TAG, "update puid " + uid);
                mFh.updateEFTransparent(IccConstants.EF_PUID, newPuid,
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
            } else {
                uidData[1] = (byte) ((puid + 1) & 0xFF);
                uidData[0] = (byte) ((puid + 1) >> 8 & 0xFF);
                newPuid[0] = uidData[0];
                newPuid[1] = uidData[1];
                Log.i(LOG_TAG, "updateEFPuid newPuid " + IccUtils.bytesToHexString(newPuid));
                synchronized (mLock) {
                    // update uid
                    mFh.updateEFLinearFixed(getEfIdByTag(recNum, USIM_EFUID_TAG), recordNum,
                            uidData, null,
                            obtainMessage(EVENT_UPDATE_UID_DONE, puid + 1));
                }
            }
            break;
        case EVENT_UPDATE_UID_DONE:
            // update puid
            ar = (AsyncResult) (msg.obj);
            int puid1 = (Integer) (ar.userObj);
            Log.i(LOG_TAG, "EVENT_UPDATE_UID_DONE");
            if (puid1 != -1) {
                Log.i(LOG_TAG, "EVENT_UPDATE_UID_DONE newPuid " + puid1);
                mFh.updateEFTransparent(IccConstants.EF_PUID, IccUtils.intToBytes(puid1, 2),
                        obtainMessage(EVENT_UPDATE_RECORD_DONE));
            }
            break;
        case EVENT_UPDATE_CC_DONE:
            mFh.loadEFTransparent(IccConstants.EF_PUID, 2,
                        obtainMessage(EVENT_EF_PUID_LOAD_DONE));
            break;
        case EVENT_EF_PSC_LOAD_DONE:
            ar = (AsyncResult) (msg.obj);
            data = (byte[]) (ar.result);
            if (ar.exception != null) {
                Log.i(LOG_TAG, "EVENT_EF_PSC_LOAD_DONE has exception " + ar.exception);
                break;
            }
            Log.i(LOG_TAG, "EVENT_EF_PSC_LOAD_DONE data " + IccUtils.bytesToHexString(data));
            byte[] psc = new byte[4];
            int oldPsc = IccUtils.bytesToInt(data);
            if (oldPsc != -1) {
                if (oldPsc == 0xFFFFFFFF) {
                    psc = IccUtils.intToBytes(1, 4);
                } else {
                    psc = IccUtils.intToBytes(oldPsc + 1, 4);
                }
            }
            Log.i(LOG_TAG, "update psc data " + IccUtils.bytesToHexString(psc));
            mFh.updateEFTransparent(IccConstants.EF_PSC, psc,
                    obtainMessage(EVENT_UPDATE_RECORD_DONE));
            break;
            /* @} */
        case EVENT_UPDATE_RECORD_DONE:
            ar = (AsyncResult) (msg.obj);
            if (ar.exception != null) {
                throw new RuntimeException("update EF records failed",
                        ar.exception);
            }
            Rlog.i(LOG_TAG, "update_record_success");
            break;

        /*@}*/
        /* SPRD: PhoneBook for AAS {@ */
        case EVENT_AAS_LOAD_DONE:
            log("Loading USIM AAS records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if (mAasFileRecord == null) {
                    mAasFileRecord = new ArrayList<byte[]>();
                }
                mAasFileRecord.clear();
                mAasFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("mAasFileRecord.size() is " + mAasFileRecord.size());
            }
            synchronized (mLock) {
                mLock.notify();
            }
            break;
        /*@}*/
        /* SPRD: PhoneBook for SNE {@ */
        case EVENT_SNE_LOAD_DONE:
            log("Loading USIM SNE records done");
            ar = (AsyncResult) msg.obj;
            if (ar.exception == null) {
                if (mSneFileRecord == null) {
                    mSneFileRecord = new ArrayList<byte[]>();
                }
                mSneFileRecord.addAll((ArrayList<byte[]>) ar.result);
                log("mSneFileRecord.size() is " + mSneFileRecord.size());
            }

            synchronized (mLock) {
                mLock.notify();
            }
            break;
        case EVENT_SNE_RECORD_COUNT:
            ar = (AsyncResult) msg.obj;
            synchronized (mLock) {
                if (ar.exception == null) {
                    sneRecordSize = (int[]) ar.result;
                    // recordSize[0] is the record length
                    // recordSize[1] is the total length of the EF file
                    // recordSize[2] is the number of records in the EF file
                    Rlog.i(LOG_TAG, "EVENT_SNE_RECORD_COUNT Size "
                            + sneRecordSize[0] + " total " + sneRecordSize[1]
                            + " #record " + sneRecordSize[2]);
                }
                mLock.notify();
            }
            break;
        /* @} */
        }
    }

    private class PbrFile {
        // RecNum <EF Tag, efid>
        HashMap<Integer,Map<Integer,Integer>> mFileIds;
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        public ArrayList<SimTlv> tlvList;
        /* @} */

        PbrFile(ArrayList<byte[]> records) {
            mFileIds = new HashMap<Integer, Map<Integer, Integer>>();
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            tlvList = new ArrayList<SimTlv>();
            /* @} */
            SimTlv recTlv;
            int recNum = 0;
            for (byte[] record: records) {
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                log("before making TLVs, data is "
                        + IccUtils.bytesToHexString(record));
                if (IccUtils.bytesToHexString(record).startsWith("ffff")) {
                    continue;
                }
                /* @} */
                recTlv = new SimTlv(record, 0, record.length);
                /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                parsePBRData(record);
                /* @} */
                parseTag(recTlv, recNum);
                recNum ++;
            }
        }

        void parseTag(SimTlv tlv, int recNum) {
            SimTlv tlvEf;
            int tag;
            byte[] data;
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            ArrayList<Integer> emailEfs = new  ArrayList<Integer>();
            ArrayList<Integer> anrEfs = new  ArrayList<Integer>();
            ArrayList<Integer> emailType = new  ArrayList<Integer>();
            ArrayList<Integer> anrType = new  ArrayList<Integer>();
            int i =0;
            /* @} */
            Map<Integer, Integer> val = new HashMap<Integer, Integer>();
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            SubjectIndexOfAdn emailInfo = new SubjectIndexOfAdn();

            emailInfo.recordNumInIap = new HashMap<Integer, Integer>();

            SubjectIndexOfAdn anrInfo = new SubjectIndexOfAdn();

            anrInfo.recordNumInIap = new HashMap<Integer, Integer>();
            /* @} */
            /* SPRD: PhoneBook for AAS {@ */
            ArrayList<Integer> aasEfs = new  ArrayList<Integer>();
            ArrayList<Integer> aasType = new  ArrayList<Integer>();
            SubjectIndexOfAdn aasInfo = new SubjectIndexOfAdn();
            aasInfo.recordNumInIap = new HashMap<Integer, Integer>();
            /* @} */
            /* SPRD: PhoneBook for SNE {@ */
            ArrayList<Integer> sneEfs = new  ArrayList<Integer>();
            ArrayList<Integer> sneType = new  ArrayList<Integer>();
            SubjectIndexOfAdn sneInfo = new SubjectIndexOfAdn();
            sneInfo.recordNumInIap = new HashMap<Integer, Integer>();
            /* @} */
            do {
                tag = tlv.getTag();
                switch(tag) {
                case USIM_TYPE1_TAG: // A8
                case USIM_TYPE3_TAG: // AA
                case USIM_TYPE2_TAG: // A9
                    data = tlv.getData();
                    tlvEf = new SimTlv(data, 0, data.length);
                    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                    //parseEf(tlvEf, val, tag);
                       parseEf(tlvEf, val, tag,emailInfo,anrInfo,emailEfs,anrEfs,emailType,anrType,
                               /* SPRD: PhoneBook for AAS*/aasInfo, aasEfs,aasType,
                               /* SPRD: PhoneBook for SNE*/sneInfo,sneEfs,sneType);
                    break;
                }
            } while (tlv.nextObject());
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            if(emailEfs.size() > 0){

                emailInfo.efids = new int[emailEfs.size()];
                emailInfo.type = new int[emailEfs.size()];

                for(i=0;i<emailEfs.size();i++){

                    emailInfo.efids[i] = emailEfs.get(i);
                    emailInfo.type[i] = emailType.get(i);
                }
                log("parseTag email ef " +emailEfs + " types " +emailType );
            }
            if(anrEfs.size() > 0){

                anrInfo.efids = new int[anrEfs.size()];
                anrInfo.type = new int[anrEfs.size()];

                for(i=0;i<anrEfs.size();i++){

                    anrInfo.efids[i] = anrEfs.get(i);
                    anrInfo.type[i] = anrType.get(i);

                }
                log("parseTag anr ef " +anrEfs + " types " + anrType);
            }
            /* SPRD: PhoneBook for AAS {@ */
            if(aasEfs.size() > 0){
                aasInfo.efids = new int[aasEfs.size()];
                aasInfo.type = new int[aasEfs.size()];

                for(i=0;i<aasEfs.size();i++){
                    aasInfo.efids[i] = aasEfs.get(i);
                    aasInfo.type[i] = aasType.get(i);
                }
                log("parseTag aas ef " +aasEfs + " types " + aasType);
            }
            /* SPRD: PhoneBook for SNE {@ */
            if(sneEfs.size() > 0){
                mSneEfSize = sneEfs.size();
                sneInfo.efids = new int[sneEfs.size()];
                sneInfo.type = new int[sneEfs.size()];

                for(i=0;i<sneEfs.size();i++){
                    sneInfo.efids[i] = sneEfs.get(i);
                    sneInfo.type[i] = sneType.get(i);
                }
                log("parseTag sne ef " +sneEfs + " types " + sneType);
            }
            /* @} */
            if(mPhoneBookRecords != null && mPhoneBookRecords.isEmpty()){
                if(mAnrInfoFromPBR != null ){
                    mAnrInfoFromPBR.add(anrInfo);
                }
                if(mEmailInfoFromPBR != null){
                    mEmailInfoFromPBR.add(emailInfo);
                }
                /* SPRD: PhoneBook for AAS {@ */
                if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
                    if(mAasInfoFromPBR != null){
                        mAasInfoFromPBR.add(aasInfo);
                    }
                }
                /* SPRD: PhoneBook for SNE {@ */
                if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
                    if(mSneInfoFromPBR != null){
                        mSneInfoFromPBR.add(sneInfo);
                        log("mSneInfoFromPBR.size() = " +mSneInfoFromPBR.size());
                    }
                }
                /* @} */
            }
            /* @} */
            mFileIds.put(recNum, val);
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;
            do {
                tag = tlv.getTag();
                if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
                    mEmailPresentInIap = true;
                    mEmailTagNumberInIap = tagNumberWithinParentTag;
                }
                switch(tag) {
                    case USIM_EFEMAIL_TAG:
                    case USIM_EFADN_TAG:
                    case USIM_EFEXT1_TAG:
                    case USIM_EFANR_TAG:
                    case USIM_EFPBC_TAG:
                    case USIM_EFGRP_TAG:
                    case USIM_EFAAS_TAG:
                    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
                    //case USIM_EFGSD_TAG:
                    case USIM_EFGAS_TAG:
                    /* @} */
                    case USIM_EFUID_TAG:
                    case USIM_EFCCP1_TAG:
                    case USIM_EFIAP_TAG:
                    case USIM_EFSNE_TAG:
                        data = tlv.getData();
                        int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        val.put(tag, efid);
                        break;
                }
                tagNumberWithinParentTag ++;
            } while(tlv.nextObject());
        }

        /* SPRD: add SIMPhoneBook for bug 474587 @{ */

        public ArrayList<Integer> getFileId(int recordNum, int fileTag) {
            ArrayList<Integer> ints = new ArrayList<Integer>();

            try {

                SimTlv recordTlv = tlvList.get(recordNum * tlvList.size() / 2); // tlvList.size()
                // =6

                SimTlv subTlv = new SimTlv(recordTlv.getData(), 0, recordTlv
                        .getData().length);
                for (; subTlv.isValidObject();) {


                    if (subTlv.getTag() == fileTag) {
                        // get the file tag
                        int i = subTlv.getData()[0] << 8;
                        ints.add(i + (int) (subTlv.getData()[1] & 0xff));

                    }
                    //if (!subTlv.nextObject())
                    //return ints;
                }
            } catch (IndexOutOfBoundsException ex) {
                log("IndexOutOfBoundsException: " + ex);
                return ints;
            }
            return ints;
        }

        public ArrayList<Integer> getFileIdsByTagAdn(int tag, int ADNid) {
            log("enter getFileIdsByTagAdn.");
            ArrayList<Integer> ints = new ArrayList<Integer>();
            boolean adnBegin = false;
            for (int i = 0, size = tlvList.size(); i < size; i++) {
                SimTlv recordTlv = tlvList.get(i);
                SimTlv subTlv = new SimTlv(recordTlv.getData(), 0, recordTlv
                        .getData().length);
                do {
                    if (subTlv.getData().length <= 2)
                        continue;
                    int x = subTlv.getData()[0] << 8;
                    x += (int) (subTlv.getData()[1] & 0xff);
                    if (subTlv.getTag() == UsimPhoneBookManager.USIM_EFADN_TAG) {
                        if (x == ADNid)
                            adnBegin = true;
                        else
                            adnBegin = false;
                    }
                    if (adnBegin) {
                        if (subTlv.getTag() == tag) {
                            if (subTlv.getData().length < 2)
                                continue;
                            int y = subTlv.getData()[0] << 8;
                            ints.add(y + (int) (subTlv.getData()[1] & 0xff));
                        }
                    }
                } while (subTlv.nextObject());
            }
            return ints;
        }

        void parseEf(SimTlv tlv, Map<Integer, Integer> val, int parentTag,
                SubjectIndexOfAdn emailInfo,SubjectIndexOfAdn anrInfo, ArrayList<Integer> emailEFS,ArrayList<Integer> anrEFS,ArrayList<Integer> emailTypes,
                ArrayList<Integer>  anrTypes,/* SPRD: PhoneBook for AAS*/SubjectIndexOfAdn aasInfo,
                ArrayList<Integer> aasEFS,ArrayList<Integer> aasTypes,/* SPRD: PhoneBook for SNE */
                SubjectIndexOfAdn sneInfo,ArrayList<Integer> sneEFS,ArrayList<Integer>  sneTypes) {
            int tag;
            byte[] data;
            int tagNumberWithinParentTag = 0;
            /* SPRD:add for Bug 448573  @{ */
            boolean isNeedReadEmail = true;
            boolean isNeedReadAnr = true;
            /* @} */

            do {
                tag = tlv.getTag();

                switch (tag) {
                    case USIM_EFEMAIL_TAG:
                    case USIM_EFADN_TAG:
                    case USIM_EFEXT1_TAG:
                    case USIM_EFANR_TAG:
                    case USIM_EFPBC_TAG:
                    case USIM_EFGRP_TAG:
                    case USIM_EFAAS_TAG:
                    case USIM_EFGAS_TAG:
                    case USIM_EFUID_TAG:
                    case USIM_EFCCP1_TAG:
                    case USIM_EFIAP_TAG:
                    case USIM_EFSNE_TAG:
                        data = tlv.getData();

                        int efid = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                        if(tag == USIM_EFADN_TAG ){

                            emailInfo.adnEfid = efid;
                            anrInfo.adnEfid = efid;
                            // SPRD: PhoneBook for AAS
                            aasInfo.adnEfid = efid;
                            // SPRD: PhoneBook for SNE
                            sneInfo.adnEfid = efid;

                        }

                        if (parentTag == USIM_TYPE2_TAG && tag == USIM_EFEMAIL_TAG) {
                            mEmailPresentInIap = true;
                            mEmailTagNumberInIap = tagNumberWithinParentTag;

                        }
                        /* SPRD:add for Bug 448573  @{ */
                        if (tag == USIM_EFEMAIL_TAG && isNeedReadEmail) {
                        //if (tag == USIM_EFEMAIL_TAG) {
                            /* @} */

                            Rlog.i(LOG_TAG, "parseEf   email  efid " +Integer.toHexString(efid) +"  TAG  " + Integer.toHexString(parentTag)
                                    + "  tagNumberWithinParentTag " +Integer.toHexString(tagNumberWithinParentTag));

                            if (parentTag == USIM_TYPE2_TAG) {
                                emailInfo.recordNumInIap.put(efid,
                                        tagNumberWithinParentTag);

                            }

                            emailEFS.add(efid);
                            emailTypes.add(parentTag);
                            /* SPRD:add for Bug 448573  @{ */
                            isNeedReadEmail = false;
                                /* @} */
                        }

                        /* SPRD:add for Bug 448573  @{ */
                        //if (tag == USIM_EFANR_TAG) {
                        if (tag == USIM_EFANR_TAG && isNeedReadAnr) {
                            /* @} */
                            Rlog.i(LOG_TAG, "parseEf   ANR  efid " +Integer.toHexString(efid) +"  TAG  " + Integer.toHexString(parentTag)
                                    + "  tagNumberWithinParentTag " +Integer.toHexString(tagNumberWithinParentTag));

                            if (parentTag == USIM_TYPE2_TAG) {

                                mAnrPresentInIap = true;
                                anrInfo.recordNumInIap.put(efid,
                                        tagNumberWithinParentTag);

                            }

                            anrEFS.add(efid);
                            anrTypes.add(parentTag);
                            /* SPRD:add for Bug 448573  @{ */
                            isNeedReadAnr = false;
                                /* @} */

                        }
                        /* SPRD: PhoneBook for AAS {@ */
                        if (tag == USIM_EFAAS_TAG && TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
                            Rlog.i(LOG_TAG, "parseEf   aas  efid " +Integer.toHexString(efid) +"  TAG  " + Integer.toHexString(parentTag)
                                    + "  tagNumberWithinParentTag " +Integer.toHexString(tagNumberWithinParentTag));
                            aasEFS.add(efid);
                            aasTypes.add(parentTag);
                        }
                        /* @} */
                        /* SPRD: PhoneBook for SNE {@ */
                        if (tag == USIM_EFSNE_TAG && TelephonyForOrangeUtils.getInstance().IsSupportOrange()) {
                            Rlog.i(LOG_TAG, "parseEf   sne  efid " +Integer.toHexString(efid) +"  TAG  " + Integer.toHexString(parentTag)
                                    + "  tagNumberWithinParentTag " +Integer.toHexString(tagNumberWithinParentTag));
                            if (parentTag == USIM_TYPE2_TAG) {
                                mSnePresentInIap = true;
                                mSneTagNumberInIap = tagNumberWithinParentTag;
                                sneInfo.recordNumInIap.put(efid,tagNumberWithinParentTag);
                            }
                            sneEFS.add(efid);
                            sneTypes.add(parentTag);
                        }
                        /* @} */
                        Rlog.i(LOG_TAG, "parseTag tag " +tag +" efid   "+ efid);
                        /* SPRD:add for Bug 448573  @{ */
                        //val.put(tag, efid);
                        if(!val.containsKey(tag)) {
                            val.put(tag, efid);
                            log("efid= "+val.get(tag));
                        }
                        /* @} */
                        break;
                }
                tagNumberWithinParentTag++;
            } while (tlv.nextObject());


        }

        void parsePBRData(byte[] data) {

            SimTlv tlv;
            int totalLength = 0;
            do {
                tlv = new SimTlv(data, totalLength, data.length);
                if(tlv.getData() == null){
                       log("tlv.getData() is null, break" );
                       break;
                   }
                totalLength += tlv.getData().length + 2;

                addRecord(tlv);
            } while (totalLength < data.length);
        }

        int getValidData(byte[] data) {
            for (int i = 0; i < data.length; i++) {
                if ((data[i] & 0xff) == 0xff)
                    return i;
            }
            return data.length;
        }

        void addRecord(SimTlv tlv) {
            log("addRecord " );
            tlvList.add(tlv);
        }
        /* @} */
    }

    private void log(String msg) {
        if(DBG) Rlog.d(LOG_TAG, msg);
    }

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */

    private String readAnrRecord(int recNum) {
        byte[] anr1Rec = null;
        byte[] anr2Rec = null;
        byte[] anr3Rec = null;
        String anr1 = null;
        String anr2 = null;
        String anr3 = null;
        String anr = null;

        int firstAnrFileRecordCount = mAnrRecordSizeArray[0] / mAnrFileCount;
        // log("firstAnrFileRecordCount is "+firstAnrFileRecordCount);
        if (mAnrFileCount == 0x1) {
            anr1Rec = mAnrFileRecord.get(recNum);
            anr = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
                    (0xff & anr1Rec[2]));
            log("readAnrRecord anr:" + anr);
            return anr;
        } else {
            if (recNum < firstAnrFileRecordCount) {
                try {
                    anr1Rec = mAnrFileRecord.get(recNum);
                    anr2Rec = mAnrFileRecord.get(recNum
                            + firstAnrFileRecordCount);
                    if (mAnrFileCount > 0x2) {
                        anr3Rec = mAnrFileRecord.get(recNum + 2
                                * firstAnrFileRecordCount);
                    }
                    //tmp , merge code from sprdroid 2.3.5
                    // int extEf = 0;
                    // Only call fileIds.get while EFEXT1_TAG is available
                    // if (fileIds.containsKey(USIM_EFEXT1_TAG)) {
                    /// extEf = fileIds.get(USIM_EFEXT1_TAG);
                    //  }
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }

                anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
                        (0xff & anr1Rec[2]));
                anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2,
                        (0xff & anr2Rec[2]));
                if (mAnrFileCount > 0x2) {
                    anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2,
                            (0xff & anr3Rec[2]));
                    anr = anr1 + ";" + anr2 + ";" + anr3;
                    log("readAnrRecord anr:" + anr);
                    return anr;
                }
            } else if (recNum >= firstAnrFileRecordCount
                    && recNum < mAnrFileRecord.size() / mAnrFileCount) {
                int secondAnrFileRecordCount = (mAnrFileRecord.size() - mAnrRecordSizeArray[0])
                / mAnrFileCount;
                // log("secondAnrFileRecordCount is "+secondAnrFileRecordCount);
                try {
                    int secondAnrfileread = mAnrRecordSizeArray[0] + recNum
                    % firstAnrFileRecordCount;
                    anr1Rec = mAnrFileRecord.get(secondAnrfileread);
                    anr2Rec = mAnrFileRecord.get(secondAnrfileread
                            + secondAnrFileRecordCount);
                    if (mAnrFileCount > 0x2) {
                        anr3Rec = mAnrFileRecord.get(secondAnrfileread + 2
                                * secondAnrFileRecordCount);
                    }
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
                anr1 = PhoneNumberUtils.calledPartyBCDToString(anr1Rec, 2,
                        (0xff & anr1Rec[2]));
                anr2 = PhoneNumberUtils.calledPartyBCDToString(anr2Rec, 2,
                        (0xff & anr2Rec[2]));
                if (mAnrFileCount > 0x2) {
                    anr3 = PhoneNumberUtils.calledPartyBCDToString(anr3Rec, 2,
                            (0xff & anr3Rec[2]));
                    anr = anr1 + ";" + anr2 + ";" + anr3;
                    log("readAnrRecord anr:" + anr);
                    return anr;
                }
            } else {
                log("the total anr size is exceed mAnrFileRecord.size()  "
                        + mAnrFileRecord.size());
            }
            anr = anr1 + ";" + anr2;
            log("readAnrRecord anr:" + anr);
            return anr;
        }
    }

    private void readIapFileAndWait(int efid,int recNum) {
        Rlog.i(LOG_TAG, "readIapFileAndWait"+mPhoneBookRecords.size()+mTotalSize[2]);
        //mFh.loadEFLinearFixedAll(efid, obtainMessage(EVENT_IAP_LOAD_DONE));
        if (mIapFileRecord == null) {
            mIapFileRecord = new ArrayList<byte[]>();
        }
        int[] size;
        if (mRecordsSize != null && mRecordsSize.containsKey(efid)) {
            size = mRecordsSize.get(efid);
        } else {
            size = readFileSizeAndWait(efid);
        }
        Rlog.i(LOG_TAG, "readIapFileAndWait size"+size[0]+size[2]);

        for (int i = 0; i < size[2]; i++) {
            byte[] emptyValue = new byte[size[0]];
            for (byte value : emptyValue) {
                value = (byte) 0xFF;
            }
            mIapFileRecord.add(i,emptyValue);
        }
        int offSet = 0;
        offSet = mTotalSize[2] - mTotalSize[2]/(recNum+1);

        Rlog.i(LOG_TAG, "readIapFileAndWait offSet "+offSet+mPhoneBookRecords.size());
        for (int i = offSet; i < mPhoneBookRecords.size() && i < (offSet + size[2]); i++) {
            if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                    || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
                mPendingIapLoads.addAndGet(1);
                Rlog.i(LOG_TAG, "readIapFile index"+i);
                mFh.loadEFLinearFixed(efid, i+1-offSet, size[0], obtainMessage(EVENT_IAP_LOAD_DONE,i-offSet,recNum));
            }
        }
        if (mPendingIapLoads.get() == 0) {
            mIsNotify.set(false);
            return;
        } else {
            mIsNotify.set(true);
        }
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readIapFileAndWait");
        }
    }

    public ArrayList<byte[]> getIapFileRecord(int recNum) {
        int efid = findEFIapInfo(recNum);
        // add begin 2010-11-29 for avoid some exception because ril
        // restart
        if (efid < 0) {
            return null;
        }
        return (ArrayList<byte[]>) mIapFileRecordArray[recNum];
    }

    public PbrFile getPbrFile() {
        return mPbrFile;
    }

    public int getNewSubjectNumber(int type, int num, int efid, int index,
            int adnNum, boolean isInIap) {

        Rlog.i(LOG_TAG, "getNewSubjectNumber  "  + " adnNum "
                + adnNum + " isInIap " + isInIap + " efid " +efid + " index " + index );
        SubjectIndexOfAdn idx = getSubjectIndex(type, num);
        int newSubjectNum = -1;
        if (idx == null) {
            return -1;
        }

        if(idx.record == null || !idx.record.containsKey(efid) ){
            log("getNewSubjectNumber idx.record == null || !idx.record.containsKey(efid)  ");
            return -1;
        }
        int count = idx.record.get(efid).size();
        Rlog.i(LOG_TAG, "getNewSubjectNumber  count " + count + "adnNum "
                + adnNum);
        if (isInIap) {
            Set<Integer> set = (Set<Integer>) idx.usedSet[index];
            for (int i = 1; i <= count; i++) {
                Integer subjectNum = new Integer(i);
                //Rlog.i(LOG_TAG, "getNewSubjectNumber  subjectNum (0)"
                //      + subjectNum);
                if (!set.contains(subjectNum)) {
                    newSubjectNum = subjectNum;
                    Rlog.i(LOG_TAG, "getNewSubjectNumber  subjectNum(1) "
                            + subjectNum);
                    set.add(subjectNum);
                    idx.usedSet[index] = set;
                    setSubjectIndex(type, num, idx);
                    SetRepeatUsedNumSet(type,efid,set);
                    break;
                }
            }
        } else {

            if (adnNum > count) {
                return newSubjectNum;
            } else {
                return adnNum;
            }
        }
        return newSubjectNum;

    }

    public void removeSubjectNumFromSet(int type, int num, int efid, int index,
            int anrNum) {
        Integer delNum = new Integer(anrNum);
        SubjectIndexOfAdn subject = getSubjectIndex(type, num);

        if (subject == null) {
            return;
        }
        int count = subject.record.get(efid).size();

        Set<Integer> set = (Set<Integer>) subject.usedSet[index];
        set.remove(delNum);
        Rlog.i(LOG_TAG, "removeSubjectNumFromSet  delnum(1) " + delNum);

        subject.usedSet[index] = set;
        setSubjectIndex(type, num, subject);
    }

    private int[] readAdnFileSizeAndWait(int recNum) {

        Rlog.i(LOG_TAG, "readAdnFileSizeAndWait");
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return null;
        }
        Map<Integer, Integer> fileIds;

        fileIds = mPbrFile.mFileIds.get(recNum);
        Rlog.i(LOG_TAG, "readAdnFileSizeAndWait, the current recNum is " + recNum);

        if (fileIds == null || fileIds.isEmpty())
            return null;

        Integer efId = fileIds.get(USIM_EFADN_TAG);
        if (efId == null)
            return null;
        mFh.getEFLinearRecordSize(efId, obtainMessage(EVENT_ADN_RECORD_COUNT));
        try {
            mLock.wait();
        } catch (InterruptedException e) {
            Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
        }

        return recordSize;
    }

    private void readIapFile(int recNum) {

        log("readIapFile recNum " + recNum);

        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return;
        }

        Map<Integer, Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);
        if (fileIds == null || fileIds.isEmpty())
            return;

        log("readIapFile mAnrPresentInIap " + mAnrPresentInIap);
        log("readIapFile mEmailPresentInIap " + mEmailPresentInIap);
        if (mAnrPresentInIap || mEmailPresentInIap) {
            Integer efId = fileIds.get(USIM_EFIAP_TAG);
            if (efId == null)
                return;
            readIapFileAndWait(efId,recNum);

        }

    }

    public int[] readFileSizeAndWait(int efId) {

        Rlog.i(LOG_TAG, "readFileSizeAndWait" + efId);
        synchronized (mLock) {
            mFh.getEFLinearRecordSize(efId, obtainMessage(EVENT_GET_RECORDS_COUNT, efId, 0));
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readAdnFileAndWait");
            }
        }
        int[] size = mRecordsSize != null ? mRecordsSize.get(efId) : null;
        return size;
    }

    private SubjectIndexOfAdn getSubjectIndex(int type, int num) {

        LinkedList<SubjectIndexOfAdn> lst = null;
        SubjectIndexOfAdn index = null;
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                lst = mEmailInfoFromPBR;
                break;
            case USIM_SUBJCET_ANR:
                lst = mAnrInfoFromPBR;
                break;
            // SPRD: PhoneBook for AAS
            case USIM_SUBJCET_AAS:
                lst = mAasInfoFromPBR;
                break;
            // SPRD: PhoneBook for SNE
            case USIM_SUBJCET_SNE:
                lst = mSneInfoFromPBR;
                break;
            default:
                break;
        }
        if (lst != null && lst.size() != 0) {
            index = lst.get(num);
            return index;
        }
        return null;
    }

    private void setSubjectIndex(int type, int num,
            SubjectIndexOfAdn subjectIndex) {
        SubjectIndexOfAdn index = null;
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                if (mEmailInfoFromPBR == null) {
                    return;
                }
                mEmailInfoFromPBR.set(num, subjectIndex);
                break;

            case USIM_SUBJCET_ANR:
                if (mAnrInfoFromPBR == null) {
                    return;
                }
                mAnrInfoFromPBR.set(num, subjectIndex);
                break;
            // SPRD: PhoneBook for AAS
            case USIM_SUBJCET_AAS:
                if (mAasInfoFromPBR == null) {
                    return;
                }
                Rlog.d(LOG_TAG,"aas num =="+num + ",subjectIndex == " + subjectIndex);
                mAasInfoFromPBR.set(num, subjectIndex);
                break;
            // SPRD: PhoneBook for SNE
            case USIM_SUBJCET_SNE:
                if (mSneInfoFromPBR == null) {
                    return;
                }
                Rlog.d(LOG_TAG,"sne num =="+num + ",subjectIndex == " + subjectIndex);
                mSneInfoFromPBR.set(num, subjectIndex);
                break;
            default:
                break;
        }
    }

    public  Set<Integer>  getUsedNumSet( Set<Integer>  set1,  Set<Integer> set2, int count){
        Set<Integer> totalSet =  set1 ;
        for (int i = 1; i <= count; i++) {
            Integer subjectNum = new Integer(i);
            if (!totalSet.contains(subjectNum) && set2.contains(subjectNum)) {
                Rlog.i(LOG_TAG, "getUsedNumSet  subjectNum(1) "
                        + subjectNum);
                totalSet.add(subjectNum);
            }
        }
        return totalSet;
    }

    public  Set<Integer> getRepeatUsedNumSet(LinkedList<SubjectIndexOfAdn> lst,int idx, int efid, Set<Integer> set, int count  ){
        SubjectIndexOfAdn index = null;
        Set<Integer> totalSet = set;
        for(int m=idx+1; m<lst.size(); m++){
            index = lst.get(m);
            if(index != null  )
            {
                int num = getUsedNumSetIndex( efid,  index);
                if(num >=0){
                    totalSet =  getUsedNumSet((Set<Integer>) index.usedSet[num],totalSet,count);
                }
            }
        }
        return totalSet;
    }

    private void setSubjectUsedNum(int type, int num) {

        SubjectIndexOfAdn index = getSubjectIndex(type, num);

        log(" setSubjectUsedNum num " + num);

        if (index == null) {

            return;
        }
        int size = index.efids.length;
        log(" setSubjectUsedNum size " + size);

        index.usedSet = new Object[size];

        for (int i = 0; i < size; i++) {

            index.usedSet[i] = new HashSet<Integer>();
        }

        setSubjectIndex(type, num, index);

    }

    private void SetRepeatUsedNumSet(int type,int efid, Set<Integer> totalSet){
        SubjectIndexOfAdn index = null;
        LinkedList<SubjectIndexOfAdn> lst = null;
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                lst = mEmailInfoFromPBR;
                break;

            case USIM_SUBJCET_ANR:
                lst = mAnrInfoFromPBR;
                break;
            // SPRD: PhoneBook for AAS
            case USIM_SUBJCET_AAS:
                lst = mAasInfoFromPBR;
                break;
            // SPRD: PhoneBook for SNE
            case USIM_SUBJCET_SNE:
                lst = mSneInfoFromPBR;
                break;
            /* @} */
            default:
                break;
        }
        if(lst ==  null){
            return;
        }
        for(int m=0; m<lst.size(); m++){
            index = lst.get(m);
            if(index != null && index.recordNumInIap !=null && index.usedSet != null)
            {
                int num = getUsedNumSetIndex( efid,  index);
                if(num >= 0){
                    log(" SetRepeatUsedNumSet efid  " + efid + " num  " + num   + "  totalSet.size  " + totalSet.size());
                    index.usedSet[num] = totalSet;
                    setSubjectIndex(type,m,index);
                }
            }
        }
    }

    private void SetMapOfRepeatEfid(int type, int efid){
        LinkedList<SubjectIndexOfAdn> lst = null;
        SubjectIndexOfAdn index = null;
        int efids[];
        Set<Integer> set ;
        Set<Integer> totalSet = new HashSet<Integer>() ;
        switch (type) {
            case USIM_SUBJCET_EMAIL:
                lst = mEmailInfoFromPBR;
                break;
            case USIM_SUBJCET_ANR:
                lst = mAnrInfoFromPBR;
                break;
            // SPRD: PhoneBook for AAS
            case USIM_SUBJCET_AAS:
                lst = mAasInfoFromPBR;
                break;
            // SPRD: PhoneBook for SNE
            case USIM_SUBJCET_SNE:
                lst = mSneInfoFromPBR;
                break;
            default:
                break;
        }
        if (lst != null && lst.size() != 0) {
            int i =0,j=0;
            for(i=0; i<lst.size(); i++){
                index = lst.get(i);
                if(index != null && index.recordNumInIap !=null && index.record != null && index.record.containsKey(efid) ){
                    int count = index.record.get(efid).size();
                    Rlog.i(LOG_TAG, "SetMapOfRepeatEfid  "  + "count "   + count );
                    int num = getUsedNumSetIndex( efid,  index);
                    if(num >= 0){
                        set = (Set<Integer>) index.usedSet[num];
                        if(set != null){
                            log("SetMapOfRepeatEfid  size " + set.size() );
                            totalSet = getUsedNumSet(totalSet,set,count);
                        }
                    }
                }
            }
        }
        if(totalSet != null){
            log("SetMapOfRepeatEfid  size " + totalSet.size() );
        }
        SetRepeatUsedNumSet(type,efid,totalSet);
    }

    public int getEfIdByTag(int recordNum, int fileTag) {

        Map<Integer, Integer> fileIds;

        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "getEfIdByTag error, Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(recordNum);

        if (fileIds == null) {
            Rlog.e(LOG_TAG, "getEfIdByTag error, fileIds is empty");
            return -1;
        }

        if (fileIds.containsKey(fileTag)) {
            return fileIds.get(fileTag);
        }

        return 0;
    }

    public int findEFEmailInfo(int index) {

        Map<Integer, Integer> fileIds;

        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(index);
        if (fileIds == null) {
            Rlog.i(LOG_TAG, "findEFEmailInfo  fileIds == null  index :" + index);
            return -1;
        }
        if (fileIds.containsKey(USIM_EFEMAIL_TAG)) {

            return fileIds.get(USIM_EFEMAIL_TAG);
        }

        return 0;
    }

    public int findEFAnrInfo(int index) {

        Map<Integer, Integer> fileIds;
        // for reload the pbr file when the pbr file is null
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(index);
        if (fileIds == null) {
            Rlog.i(LOG_TAG, "findEFAnrInfo  fileIds == null  index :" + index);
            return -1;
        }
        if (fileIds.containsKey(USIM_EFANR_TAG)) {
            return fileIds.get(USIM_EFANR_TAG);
        }

        return 0;
    }

    private String getType1Anr(int num, SubjectIndexOfAdn anrInfo, int adnNum, int efid,/* SPRD: PhoneBook for AAS*/ArrayList<Integer> aasIndex) {
        String anr = "";
        int anrTagNumberInIap;
        ArrayList<byte[]> anrFileRecord;
        byte[] anrRec;
        anrFileRecord = anrInfo.record.get(efid);

        if (anrFileRecord == null) {

            return anr;
        }

        if (adnNum < anrFileRecord.size()) {
            anrRec = anrFileRecord.get(adnNum);
            anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
                    (0xff & anrRec[2]));
            log("anrRec[0] == " +anrRec[0] + "anr" +anr);
            aasIndex.add((int)anrRec[0]);
        } else {

            anr = "";
        }
        // SIM record numbers are 1 based
        return anr;
    }

    private String getType2Anr(int num, SubjectIndexOfAdn anrInfo,
            byte[] record, int adnNum, int efid,/* SPRD: PhoneBook for AAS*/ArrayList<Integer> aasIndex) {

        String anr = "";
        int anrTagNumberInIap;
        ArrayList<byte[]> anrFileRecord;
        byte[] anrRec;

        int index = 0;
        //boolean isSet = false;
        log(" getType2Anr  >> anrInfo.recordNumInIap.size() "
                + anrInfo.recordNumInIap.size() + "adnNum  " + adnNum);
        if(record == null){

            return anr;
        }

        index = getUsedNumSetIndex( efid, anrInfo);
        if(index == -1){

            return anr;
        }


        anrTagNumberInIap = anrInfo.recordNumInIap.get(efid);

        anrFileRecord = anrInfo.record.get(efid);

        if (anrFileRecord == null) {

            return anr;
        }
        log(" getType2Anr anrTagNumberInIap"
                + anrTagNumberInIap);
        int recNum = (int) (record[anrTagNumberInIap] & 0xFF);
        recNum = ((recNum == 0xFF) ? (-1) : recNum);
        log(" getType2Anr iap recNum == " + recNum);

        if (recNum > 0 && recNum <= anrFileRecord.size()) {

            anrRec = anrFileRecord.get(recNum -1);
            anr = PhoneNumberUtils.calledPartyBCDToString(anrRec, 2,
                    (0xff & anrRec[2]));
            //SPRD: PhoneBook for AAS
            aasIndex.add((int)anrRec[0]);
            log("getAnrInIap anr:" + anr);
            // SIM record numbers are 1 based
            if(TextUtils.isEmpty(anr)){
                log("getAnrInIap anr is emtry");
                setIapFileRecord(num, adnNum, (byte) 0xFF, anrTagNumberInIap);
                return anr;
            }

            Set<Integer> set = (Set<Integer>) anrInfo.usedSet[index];
            set.add(new Integer(recNum));
            anrInfo.usedSet[index] = set;
            setSubjectIndex(USIM_SUBJCET_ANR,num,anrInfo);

        }
        log( "getType2Anr  >>>>>>>>>>>> anr " + anr);
        return anr;

    }

    private int getUsedNumSetIndex(int efid, SubjectIndexOfAdn index){
        int count = -1;
        if(index != null && index.efids != null ){
            for(int k=0; k<index.efids.length; k++){
                log("getUsedNumSetIndex index.type[k] " + index.type[k]);
                if( index.type[k] == USIM_TYPE2_TAG ){
                    count++;
                    if(index.efids[k] == efid ){
                        log("getUsedNumSetIndex count " + count);
                        return count;
                    }
                }
            }
        }
        return -1;
    }

    public void setIapFileRecord(int recNum, int index, byte value, int numInIap) {
        log("setIapFileRecord >>  recNum: " + recNum + "index: "
                + index + " numInIap: " +numInIap + " value: " +value );
        ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[recNum];
        byte[] record = tmpIapFileRecord.get(index);
        byte[] temp = new byte[record.length];
        for (int i = 0; i < temp.length; i++) {
            temp[i] = record[i];
        }
        temp[numInIap] = value;
        tmpIapFileRecord.set(index, temp);
        mIapFileRecordArray[recNum] = tmpIapFileRecord;
    }

    private String getType1Email(int num, SubjectIndexOfAdn emailInfo,
            int adnNum, int efid) {


        String emails = null;

        mEmailFileRecord = emailInfo.record.get(efid);

        if (mEmailFileRecord == null) {

            return null;
        }
        log("getType1Email size " +  mEmailFileRecord.size());
        emails = readEmailRecord(adnNum);
        log( "getType1Email,emails " + emails);

        if (TextUtils.isEmpty(emails)) {

            log("getType1Email,emails==null");
            return null;
        }
        return emails;
    }

    private String getType2Email(int num, SubjectIndexOfAdn emailInfo,
            byte[] record, int andNum, int efid) {


        String emails = null;
        int index = -1;
        log(" getType2Email >>  emailInfo.recordNumInIap.size() "
                + emailInfo.recordNumInIap.size() + " adnNum " + andNum + " efid " +efid);
        if(record == null){

            return emails;
        }


        index = getUsedNumSetIndex(efid,emailInfo );


        if(index == -1){

            return emails;
        }

        mEmailTagNumberInIap = emailInfo.recordNumInIap.get(efid);
        //log(" getType2Email mEmailTagNumberInIap "
        //      + mEmailTagNumberInIap);
        mEmailFileRecord = emailInfo.record.get(efid);
        //log("getType2Email size " +  mEmailFileRecord.size());
        if (mEmailFileRecord == null) {

            return emails;
        }

        /* SPRD:add for Bug 448573  @{ */
        //int recNum = (int) (record[mEmailTagNumberInIap] & 0xFF);
        int recNum = 0xFF;
        try{
            recNum = (int) (record[mEmailTagNumberInIap] & 0xFF);
        } catch (ArrayIndexOutOfBoundsException ex) {
            log("ex :"+ex);
        }
        /* @} */
        recNum = ((recNum == 0xFF) ? (-1) : recNum);
        log("getType2Email  iap recNum == " + recNum);
        if (recNum != -1) {

            // SIM record numbers are 1 based
            emails = readEmailRecord(recNum - 1);
            log( "getType2Email,emails " + emails);
            // set email
            if (TextUtils.isEmpty(emails)) {

                log("getType2Email,emails ==null");
                setIapFileRecord(num, andNum, (byte) 0xFF, mEmailTagNumberInIap);

                return null;
            }
            Set<Integer> set = (Set<Integer>) emailInfo.usedSet[index];
            log("getType2Email  size (0)" +  set.size()  + " index " + index);
            set.add(new Integer(recNum));
            emailInfo.usedSet[index] = set;
            log("getType2Email  size (1)" +  set.size());
            setSubjectIndex(USIM_SUBJCET_EMAIL,num,emailInfo);
        }

        return emails;

    }

    private String  getAnr(int num, SubjectIndexOfAdn anrInfo,/* SPRD: PhoneBook for AAS @{*/SubjectIndexOfAdn aasInfo, int index,/*@}*/byte[] record,int adnNum){


        log( "getAnr adnNum: " + adnNum + "num " + num);


        String anrGroup = null;
        String anr = null;
        /* SPRD: PhoneBook for AAS @{*/
        String aasGroup = null;
        String aas = null;
        /*@}*/
        if(anrInfo.efids == null ||anrInfo.efids.length == 0){

            log( "getAnr anrInfo.efids == null ||anrInfo.efids.length == 0 ");
            return null;
        }
        /* SPRD: PhoneBook for AAS @{*/
        ArrayList<Integer> aasIndex = new ArrayList<Integer>();
        /*@}*/

        for (int i = 0; i < anrInfo.efids.length; i++) {

            if(anrInfo.type[i] == USIM_TYPE1_TAG){

                anr = getType1Anr( num,  anrInfo,  adnNum,  anrInfo.efids[i],/* SPRD: PhoneBook for AAS*/aasIndex);

            }

            if(anrInfo.type[i] == USIM_TYPE2_TAG && anrInfo.recordNumInIap != null){

                anr = getType2Anr( num,  anrInfo, record,  adnNum, anrInfo.efids[i],/* SPRD: PhoneBook for AAS*/aasIndex);

            }
            /* SPRD: PhoneBook for AAS @{*/
            log("aasIndex.size() == " + aasIndex.size() + "aasIndex.get(i)" + (aasIndex.size() > 0 ? aasIndex.get(i) : null));
            if(i < aasIndex.size() && TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
                aas = getAas(aasInfo, aasIndex.get(i));
            }
            /*@}*/
            if (i == 0) {
                anrGroup = anr;
                //SPRD: PhoneBook for AAS
                aasGroup = aas;
            } else {
                anrGroup = anrGroup + ";" + anr;
                //SPRD: PhoneBook for AAS
                aasGroup = aasGroup + ";" + aas;
            }
            //SPRD: PhoneBook for AAS
            log("anrGroup["+i+"] == " + anrGroup + ", aasGroup["+i+"] == " + aasGroup+" ,mDoneAdnCount  += "+ mDoneAdnCount + " , record == " + record);
        }
        /* SPRD: PhoneBook for AAS @{*/
        if(aasInfo != null && TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
            setAas(index, aasGroup);
        }
        /*@}*/
        return anrGroup;
    }
    /*@}*/
    private  String[] getEmail(int num, SubjectIndexOfAdn emailInfo,
            byte[] record, int adnNum){

        log( "getEmail adnNum: " + adnNum + "num " + num);


        String[] emails = null;
        boolean isEmpty = true;

        if(emailInfo.efids == null ||emailInfo.efids.length == 0){

            log( "getEmail emailInfo.efids == null ||emailInfo.efids.length == 0 ");
            return null;
        }

        emails = new String[emailInfo.efids.length];

        for (int i = 0; i < emailInfo.efids.length; i++) {

            if(emailInfo.type[i] == USIM_TYPE1_TAG){

                emails[i] = getType1Email( num,  emailInfo,  adnNum,  emailInfo.efids[i]);

            }

            if(emailInfo.type[i] == USIM_TYPE2_TAG && emailInfo.recordNumInIap != null){

                emails[i] = getType2Email( num,  emailInfo, record,  adnNum,  emailInfo.efids[i]);

            }

        }

        for(int i=0; i< emails.length; i++){

            if(!TextUtils.isEmpty(emails[i])){

                isEmpty = false;
            }

        }

        if(isEmpty){

            return null;
        }

        return emails;

    }

    private void setEmailandAnr(int adnNum, String[] emails, String anr) {

        AdnRecord rec = mPhoneBookRecords.get(adnNum);

        if (rec == null && (emails != null || anr != null)) {

            rec = new AdnRecord("", "");
        }
        if (emails != null) {
            rec.setEmails(emails);

            log( "setEmailandAnr AdnRecord  emails"
                    + emails[0]);
        }
        if (anr != null) {
            log( "setEmailandAnr AdnRecord  anr"
                    + anr);
            rec.setAnr(anr);
        }
        mPhoneBookRecords.set(adnNum, rec);

    }

    private void setAnrIapFileRecord(int num, int index, byte value,
            int numInIap) {
        log("setAnrIapFileRecord >> num:" + num + "index: "
                + index + "value: " + value + " numInIap:" + numInIap);
        ArrayList<byte[]> tmpIapFileRecord = (ArrayList<byte[]>) mIapFileRecordArray[num];
        byte[] record = tmpIapFileRecord.get(index);
        record[numInIap] = value;
        tmpIapFileRecord.set(index, record);
        mIapFileRecordArray[num] = tmpIapFileRecord;

    }

    public int[] getAdnRecordSizeArray() {
        return mAdnRecordSizeArray;
    }

    public int getNumRecs() {
        // add begin 2010-11-25 for reload the pbr file when the pbr
        // file is null
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
            if (mPbrFile == null) {
                Rlog.e(LOG_TAG, "Error: Pbr file is empty");
                return 0;
            }
        }
        return mPbrFile.mFileIds.size();
    }

    public int getAnrNum() {
        int num = 0;
        int[] efids =  getSubjectEfids(USIM_SUBJCET_ANR,0);
        if(efids == null){
            return 0;
        }
        num = efids.length;
        log( "getAnrNum " +  num);
        return num;
    }

    public int getPhoneNumMaxLen() {
        return AdnRecord.MAX_LENTH_NUMBER;
    }

    // the email ef may be type1 or type2
    public int getEmailType() {
        if (mEmailPresentInIap == true) {
            return 2;
        } else {
            return 1;
        }
    }

    public int getEmailNum() {
        int[] efids =  getSubjectEfids(USIM_SUBJCET_EMAIL,0);
        if(efids == null){
            Rlog.i(LOG_TAG, "efids is NULL");
            return -2;
        }
        int num = efids.length;
        Rlog.i(LOG_TAG, "getEmailNum " + num);
        return num;
    }

    public int[] getValidNumToMatch(AdnRecord adn,int type, int[] subjectNums)
    {
        int []  ret = null;
        int  efid = 0;
        for (int num = 0; num < getNumRecs(); num++) {
            efid = findEFInfo(num);
            if (efid <= 0 ) {
                return null;
            }
            Rlog.i(LOG_TAG, "getEfIdToMatch ");
            ArrayList<AdnRecord> oldAdnList;
            Rlog.e(LOG_TAG, "efid is " + efid);
            oldAdnList = mAdnCache.getRecordsIfLoaded(efid);
            if (oldAdnList == null) {
                return null;
            }
            Rlog.i(LOG_TAG, "getEfIdToMatch (2)");
            int count = 1;
            int adnIndex = 0;
            for (Iterator<AdnRecord> it = oldAdnList.iterator(); it.hasNext();) {
                if (adn.isEqual(it.next())) {
                    Rlog.i(LOG_TAG, "we got the index " + count);
                    adnIndex = count;
                    ret = getAvalibleSubjectCount(num, type,efid,adnIndex,subjectNums);
                    if(ret != null){
                        return ret;
                    }
                }
                count++;
            }
        }
        return null;
    }

    private int getAvalibleAdnCount(){

        List<AdnRecord> adnRecords = mPhoneBookRecords;
        int totalCount = 0;
        int count = 0;
        if(mPhoneBookRecords == null){
            return 0;
        }
        totalCount = mPhoneBookRecords.size();
        AdnRecord adnRecord;
        for(int i=0; i< totalCount; i++){
            adnRecord =  mPhoneBookRecords.get(i);
            if(adnRecord.isEmpty()){
                count++;
            }
        }
        return count;
    }

    public int[] getAvalibleSubjectCount(int num, int type, int efid ,int adnNum, int[] subjectNums){
        SubjectIndexOfAdn index = null;
        int count = 0;
        int avalibleNum = 0;
        int[] ret = null;
        int n = 0;
        log("getAvalibleSubjectCount efid " +  efid + " num " +num );
        log("getAvalibleSubjectCount  " +  " type " + type + " adnNum " + adnNum + "  subjectNums " + subjectNums);
        index = getSubjectIndex( type,  num);
        if (index == null ) {
            return null;
        }
        ret = new int [subjectNums.length];
        log("getAvalibleSubjectCount adnEfid " + index.adnEfid);
        if(index != null  && index.adnEfid == efid &&index.record != null && index.efids!= null && index.type!= null){
            for(int j=0; j< index.efids.length ;j++){
                log("getAvalibleSubjectCount efid " +  index.efids[j] );
                for(int l=0; l<subjectNums.length; l++){
                    log("getAvalibleSubjectCount efid " +  subjectNums[l]  );
                    if(subjectNums[l] == 1 && index.record.containsKey(index.efids[j]) &&index.record.get(index.efids[j])!= null ){
                        count = index.record.get(index.efids[j]).size();
                        // log("getAvalibleSubjectCount index.type[j] " +  index.type[j]  );
                        if(index.type[j] == USIM_TYPE1_TAG ){
                            ret[n] = getAvalibleAdnCount();
                            n++;
                            break;
                        }else if(index.type[j] == USIM_TYPE2_TAG ){
                            int idx = getUsedNumSetIndex( index.efids[j],  index);
                            log("getAvalibleSubjectCount idx " +  idx );
                            if(idx >=0 ){
                                Set<Integer> usedSet =  (Set<Integer>) index.usedSet[idx];
                                avalibleNum =  count -usedSet.size();
                                ret[n] =  avalibleNum;
                                n++;
                                break;
                            }
                        }
                    }
                }
            }
        }
        log("getAvalibleSubjectCount  n " +  n  );
        if( n == 0){
            ret = null;
            return ret;
        }
        for(int i= 0; i<ret.length; i++ ){
            log("getAvalibleSubjectCount  ret[] " +  ret[i]  );
        }
        return ret;
    }

    public  int [] getAvalibleAnrCount(String name, String number,
            String[] emails, String anr, int[] anrNums){
        AdnRecord   adn  = new AdnRecord(name, number, emails,
                anr,"","","","" );

        return getValidNumToMatch(adn,USIM_SUBJCET_ANR,anrNums);
    }

    public int [] getAvalibleEmailCount(String name, String number,
            String[] emails, String anr, int[] emailNums){


        AdnRecord   adn  = new AdnRecord(name, number, emails,
                anr,"","","","" );

        return getValidNumToMatch(adn,USIM_SUBJCET_EMAIL,emailNums);

    }

    private String getGrp(int adnNum) {

        if (mGrpFileRecord == null) {
            return null;
        }
        String retGrp = composeGrpString(mGrpFileRecord.get(adnNum));
        return retGrp;
    }

    private String composeGrpString(byte data[]) {
        String groupInfo = null;
        mGrpCount = data.length;
        for (int i = 0; i < mGrpCount; i++) {

            int temp = data[i] & 0xFF;
            if (temp == 0 || temp == 0xFF)
                continue; // 0X'00' -- no group indicated;

            if (groupInfo == null) {
                groupInfo = Integer.toString(temp);
            } else {
                groupInfo = groupInfo + AdnRecord.ANR_SPLIT_FLG + temp;
            }
        }
        return groupInfo;
    }

    public int[] getSubjectEfids(int type, int num) {
        SubjectIndexOfAdn index = getSubjectIndex(type, num);
        if (index == null) {
            return null;
        }
        int[] result = index.efids;
        if(result != null){
            Rlog.i(LOG_TAG, "getSubjectEfids  "  + "length "
                    + result.length );
        }
        return result;
    }

    public int[][] getSubjectTagNumberInIap(int type, int num) {

        Map<Integer, Integer> anrTagMap = null;
        SubjectIndexOfAdn index = getSubjectIndex(type, num);
        boolean isInIap = false;
        if (index == null) {
            return null;
        }
        int[][] result = new int[index.efids.length][2];
        anrTagMap = index.recordNumInIap;
        if(anrTagMap == null || anrTagMap.size() == 0 ){
            log("getSubjectTagNumberInIap recordNumInIap == null");
            return null;
        }
        for (int i = 0; i < index.efids.length; i++) {
            if(anrTagMap.containsKey(index.efids[i])){
                result[i][1] = anrTagMap.get(index.efids[i]);
                result[i][0] = index.efids[i];
                isInIap = true;
            }
        }
        if(!isInIap){
            result = null;
            log("getSubjectTagNumberInIap isInIap == false");
        }
        return result;
    }

    public int[][] getAnrTagNumberInIap(int num) {
        Map<Integer, Integer> anrTagMap;
        int[][] result = new int[mAnrInfoFromPBR.get(num).efids.length][2];
        anrTagMap = mAnrInfoFromPBR.get(num).recordNumInIap;
        for (int i = 0; i < mAnrInfoFromPBR.get(num).efids.length; i++) {
            result[i][0] = mAnrInfoFromPBR.get(num).efids[i];
            result[i][1] = anrTagMap.get(mAnrInfoFromPBR.get(num).efids[i]);
        }
        return result;
    }

    public boolean isSubjectRecordInIap(int type, int num, int indexOfEfids) {

        SubjectIndexOfAdn index = getSubjectIndex(type, num);

        if (index == null) {
            return false;
        }

        if (index.type[indexOfEfids] == USIM_TYPE2_TAG && index.recordNumInIap.size() > 0) {
            return true;
        } else if (index.type[indexOfEfids] == USIM_TYPE1_TAG) {
            return false;
        }

        return false;

    }

    public int findEFInfo(int index) {
        Map<Integer, Integer> fileIds;
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(index);

        if (fileIds == null) {
            Rlog.e(LOG_TAG, "Error: fileIds is empty");
            return -1;
        }

        if (fileIds.containsKey(USIM_EFADN_TAG)) {
            return fileIds.get(USIM_EFADN_TAG);
        }

        return -1;
    }

    public int findExtensionEFInfo(int index) {

        Map<Integer, Integer> fileIds;

        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(index);

        if (fileIds == null) {
            Rlog.e(LOG_TAG, "Error: fileIds is empty");
            return -1;
        }

        Rlog.i(LOG_TAG, "findExtensionEFInfo fileIds " + fileIds);

        if (fileIds.containsKey(USIM_EFEXT1_TAG)) {

            return fileIds.get(USIM_EFEXT1_TAG);
        }

        return 0;

    }

    public int findEFIapInfo(int index) {

        Map<Integer, Integer> fileIds;

        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(index);
        if (fileIds == null) {
            Rlog.i(LOG_TAG, "findEFIapInfo  fileIds == null  index :" + index);
            return -1;
        }
        if (fileIds.containsKey(USIM_EFIAP_TAG)) {
            return fileIds.get(USIM_EFIAP_TAG);
        }

        return 0;
    }

    public int findEFGasInfo() {

        Map<Integer, Integer> fileIds;
        // for reload the pbr file when the pbr file is null
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return -1;
        }
        fileIds = mPbrFile.mFileIds.get(0);
        if (fileIds == null) {
            Rlog.i(LOG_TAG, "findEFGasInfo fileIds == null");
            return -1;
        }
        if (fileIds.containsKey(USIM_EFGAS_TAG)) {
            return fileIds.get(USIM_EFGAS_TAG);
        }

        return 0;
    }

    public void updateGasList(String groupName, int groupId) {
        if (mGasList.isEmpty())
            return;
        mGasList.set(groupId - 1, groupName);
    }

    private void readAnrFileAndWait(int recNum) {
        log("readAnrFileAndWait recNum " + recNum);
        synchronized (mLock) {
            if (mPbrFile == null) {
                readPbrFileAndWait();
            }
        }
        if (mPbrFile == null) {
            Rlog.e(LOG_TAG, "Error: Pbr file is empty");
            return;
        }
        log("readAnrFileAndWait recNum is   " + recNum);
        Map<Integer, Integer> fileIds;
        fileIds = mPbrFile.mFileIds.get(recNum);

        if (fileIds == null){
            log("readAnrFileAndWait  fileIds == null" );
            return;
        }

        log("readAnrFileAndWait  mAnrInfoFromPBR !=null fileIds.size()   " + fileIds.size() );
        if (fileIds.containsKey(USIM_EFANR_TAG)) {
            mIshaveAnr = true;
            SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_ANR,recNum);
            if(records == null){
                log("readAnrFileAndWait  records == null ");
                return;
            }
            records.record = new HashMap<Integer, ArrayList<byte[]>>();
            if(records.efids == null || records.efids.length == 0){
                log("readAnrFileAndWait  records.efids == null || records.efids.length == 0");
                return;
            }
            mAnrFileCount = records.efids.length;
            boolean isFail =false;
            log("readAnrFileAndWait mAnrFileCount " + mAnrFileCount);
            // Read the anr file.
            for (int i = 0; i < mAnrFileCount; i++) {

//                mFh.loadEFLinearFixedAll(records.efids[i],
//                        obtainMessage(EVENT_ANR_LOAD_DONE));
                log("readAnrFileAndWait type"+records.type[i]);
                if (records.type[i] == USIM_TYPE1_TAG) {
                    if (mAnrFileRecord == null) {
                        mAnrFileRecord = new ArrayList<byte[]>();
                    }
                    int[] size;
                    if (mRecordsSize != null && mRecordsSize.containsKey(records.efids[i])) {
                        size = mRecordsSize.get(records.efids[i]);
                    } else {
                        size = readFileSizeAndWait(records.efids[i]);
                    }
                    Rlog.i(LOG_TAG, "readAnrFileAndWait size[0] = "+size[0] + " size[2] = "+size[2]);

                    for (int j = 0; j < size[2]; j++) {
                        byte[] emptyValue = new byte[size[0]];
                        for (byte value : emptyValue) {
                            value = (byte) 0xFF;
                        }
                        mAnrFileRecord.add(j,emptyValue);
                    }

                    int offSet = 0;
                    offSet = mTotalSize[2] - mTotalSize[2] / (recNum + 1);

                    Rlog.i(LOG_TAG, "readAnrFileAndWait offSet " + offSet + mPhoneBookRecords.size());
                    for (int j = offSet; j < mPhoneBookRecords.size() && j < (offSet + size[2]); j++) {
                        if (!TextUtils.isEmpty(mPhoneBookRecords.get(j).getAlphaTag())
                                || !TextUtils.isEmpty(mPhoneBookRecords.get(j).getNumber())) {
                            mPendingAnrLoads.addAndGet(1);
                            Rlog.i(LOG_TAG, "readAnrFile index" + j);
                            mFh.loadEFLinearFixed(records.efids[i], j + 1 - offSet, size[0],
                                    obtainMessage(EVENT_ANR_RECORD_LOAD_DONE, j - offSet, recNum));
                        }
                    }

                    if (mPendingAnrLoads.get() == 0) {
                        mIsNotify.set(false);
                    } else {
                        mIsNotify.set(true);
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Rlog.e(LOG_TAG,
                            "Interrupted Exception in readEmailFileAndWait");
                        }
                    }
                }else if (records.type[i] == USIM_TYPE2_TAG) {
                    mFh.loadEFLinearFixedAll(records.efids[i],
                           obtainMessage(EVENT_ANR_LOAD_DONE));
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        Rlog.e(LOG_TAG,
                        "Interrupted Exception in readEmailFileAndWait");
                    }
                }

                log("load ANR times ...... " + (i + 1));

                if (mAnrFileRecord == null) {
                    Rlog.e(LOG_TAG, "Error: ANR file is empty");
                    records.efids[i] = 0;
                    isFail = true;
                    continue;
                }

                records.record.put(records.efids[i], mAnrFileRecord);
                mAnrFileRecord = null;
            }
            //if(isFail)//@ temp
            {
                handleReadFileResult(records);
            }
            setSubjectIndex(USIM_SUBJCET_ANR,recNum,records);
            setSubjectUsedNum(USIM_SUBJCET_ANR, recNum);
        }

    }

    private void handleReadFileResult(SubjectIndexOfAdn records ){
        log("handleReadFileResult  " );
        int i=0;
        ArrayList<Integer> efs = new  ArrayList<Integer>();
        if(records == null ||records.efids == null){
            log("handleReadFileResult records == null ||records.efids == null ");
            return;
        }
        for(i=0; i< records.efids.length; i++){

            if(records.efids[i] != 0){

                efs.add(records.efids[i]);
            }else{
                log("handleReadFileResult err efid " +  records.efids[i]);
                if(records.recordNumInIap != null && records.recordNumInIap.containsKey(records.efids[i])){
                    records.recordNumInIap.remove(records.efids[i]);
                }
            }
        }
        log("handleReadFileResult  efs " + efs );
        int[] validEf = new int[efs.size()];
        for(i=0; i<efs.size();i++){

            validEf[i] = efs.get(i);

        }
        records.efids = validEf;
    }

    private void readGrpFileAndWait(int recNum, int totalSize) {
            Rlog.i(LOG_TAG, "readGrpFileAndWait");
            synchronized (mLock) {
                if (mPbrFile == null) {
                    readPbrFileAndWait();
                }
            }
            if (mPbrFile == null) {
                Rlog.e(LOG_TAG, "Error: Pbr file is empty");
                return;
            }

            Map<Integer, Integer> fileIds;
            fileIds = mPbrFile.mFileIds.get(recNum);
            if (fileIds == null || fileIds.isEmpty())
                return;

            Integer efId = fileIds.get(USIM_EFGRP_TAG);
            if (efId == null)
                return;
          // mFh.loadEFLinearFixedAll(efId, obtainMessage(EVENT_GRP_LOAD_DONE));

           if (mGrpFileRecord == null) {
               mGrpFileRecord = new ArrayList<byte[]>();
           }
           int[] size;
           if (mRecordsSize != null && mRecordsSize.containsKey(efId)) {
               size = mRecordsSize.get(efId);
           } else {
               size = readFileSizeAndWait(efId);
           }

           int offSet = 0;
           /* SPRD:add for Bug 448722  @{ */
           //offSet = mTotalSize[2] - mTotalSize[2]/(recNum+1);
           //Rlog.i(LOG_TAG, "readGrpFileAndWait size"+size[2]+" offSet "+offSet);
           offSet = totalSize - totalSize/(recNum+1);
           Rlog.i(LOG_TAG, "readGrpFileAndWait size"+size[2]+" offSet "+offSet+" totalSize "+ totalSize);
           /* @} */
           for (int i = offSet; i < offSet + size[2]; i++) {
               byte[] emptyValue = new byte[size[0]];
               for (byte value : emptyValue) {
                   value = (byte) 0xFF;
               }
               mGrpFileRecord.add(i,emptyValue);
           }
           Rlog.i(LOG_TAG, "readGrpFileAndWait  "+mPhoneBookRecords.size());
           for (int i = offSet; i < mPhoneBookRecords.size() && i < (offSet +size[2]); i++) {
               if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                       || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
                   mPendingGrpLoads.addAndGet(1);
                   Rlog.i(LOG_TAG, "readGrpFile index"+i);
                   mFh.loadEFLinearFixed(efId.intValue(), i+1-offSet, size[0], obtainMessage(EVENT_GRP_LOAD_DONE,i-offSet,offSet));
               }
           }
           if (mPendingGrpLoads.get() == 0) {
               mIsNotify.set(false);
               return;
           } else {
               mIsNotify.set(true);
           }
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                Rlog.e(LOG_TAG, "Interrupted Exception in readGrpFileAndWait");
            }
        }

    private void updateAdnRecord(int num) {

        SubjectIndexOfAdn emailInfo = null;
        int emailType = 0;
        String[] emails = null;
        SubjectIndexOfAdn anrInfo = null;
        int anrType = 0;
        String anr = null;
        int numAdnRecs = mPhoneBookRecords.size();
        /* SPRD: PhoneBook for AAS {@ */
        SubjectIndexOfAdn aasInfo = null;
        String aas = null;
        /* @} */
        /* SPRD: PhoneBook for SNE {@ */
        SubjectIndexOfAdn sneInfo = null;
        String snes = null;
        /* @} */

        mAdnRecordSizeArray[num] = mPhoneBookRecords.size();
        log( "updateAdnRecord numAdnRecs : "
                + numAdnRecs + " num " + num);
        for (int i = 0; i < num; i++) {
            mAdnRecordSizeArray[num] -= mAdnRecordSizeArray[i];
        }

        log( "updateAdnRecord mAdnRecordSizeArray[num] : "
                + mAdnRecordSizeArray[num] + " num " + num);

        int numIapRec = 0;
        int efid = 0;
        byte[] record = null;

        emailInfo = getSubjectIndex(USIM_SUBJCET_EMAIL, num);

        anrInfo = getSubjectIndex(USIM_SUBJCET_ANR, num);
        /* SPRD: PhoneBook for AAS {@ */
        if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
            aasInfo = getSubjectIndex(USIM_SUBJCET_AAS, num);
        }
        /* @} */
        /* SPRD: PhoneBook for SNE {@ */
        if(TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
            sneInfo = getSubjectIndex(USIM_SUBJCET_SNE, num);
        }
        log("emailInfo == " + emailInfo + ",anrInfo == " + anrInfo + ", sneInfo == "
                + sneInfo +",aasInfo == " + aasInfo + ", mIapFileRecord == " + mIapFileRecord);
        /* @} */



        if (mIapFileRecord != null) {
            // The number of records in the IAP file is same as the number of
            // records in ADN file.
            // The order of the pointers in an EFIAP shall be the same as the
            // order of file IDs
            // that appear in the TLV object indicated by Tag 'A9' in the
            // reference file record.
            // i.e value of mEmailTagNumberInIap

            numIapRec = mIapFileRecord.size();
            Rlog.i(LOG_TAG, "updateAdnRecord mIapRecordSizeArray[num] : "
                    + mIapFileRecord.size());

            mIapFileRecordArray[num] = mIapFileRecord;
            mIapRecordSizeArray[num] = mIapFileRecord.size();

            log("updateAdnRecord,numIapRec  " + numIapRec);
            //modify for bug 199741
            numIapRec = ((numAdnRecs-mDoneAdnCount) > numIapRec) ? numIapRec : (numAdnRecs -mDoneAdnCount) ;
        }else{
            numIapRec = numAdnRecs-mDoneAdnCount;
        }
        log("updateAdnRecord,numIapRec  " + numIapRec + " mDoneAdnCount " + mDoneAdnCount);
        for (int i = mDoneAdnCount; i < (mDoneAdnCount+numIapRec); i++) {

            record = null;

            if(mIapFileRecord != null){

                try {
                    record = mIapFileRecord.get((i-mDoneAdnCount));

                } catch (IndexOutOfBoundsException e) {

                    Rlog.e(LOG_TAG,"Error: Improper ICC card: No IAP record for ADN, continuing");

                }
            }
            if(emailInfo != null){
                emails = getEmail(num, emailInfo, record, (i-mDoneAdnCount));
                setEmailandAnr(i, emails, null);
            }
            if(anrInfo !=null){
                anr = getAnr(num, anrInfo, /* SPRD: PhoneBook for AAS @{*/aasInfo, i, /*@}*/record, (i-mDoneAdnCount));
                setEmailandAnr(i, null, anr);
            }
            /* SPRD: PhoneBook for SNE {@ */
            if(sneInfo != null && TelephonyForOrangeUtils.getInstance().IsSupportOrange()){
                snes = getSne(num, sneInfo, record, (i-mDoneAdnCount));
                log("updateAdnRecord,snes =" + snes);
                setSne(i, snes);
            }
            /* @} */

        }

        mIapFileRecord = null;

        mDoneAdnCount += numAdnRecs;
    }

    private void CheckRepeatType2Ef(){

        ArrayList<Integer> efs = getType2Ef(USIM_SUBJCET_EMAIL);
        int i = 0;
        log("CheckRepeatType2Ef ");
        for( i=0 ; i<efs.size(); i++){

            SetMapOfRepeatEfid(USIM_SUBJCET_EMAIL,efs.get(i));

        }

        efs = getType2Ef(USIM_SUBJCET_ANR);
        for( i=0 ; i<efs.size(); i++){

            SetMapOfRepeatEfid(USIM_SUBJCET_ANR,efs.get(i));

        }

    }

    private  ArrayList<Integer>  getType2Ef(int type){

        ArrayList<Integer> efs = new ArrayList<Integer>();
        LinkedList<SubjectIndexOfAdn> lst = null;
        SubjectIndexOfAdn index = null;
        boolean isAdd = false;
        switch (type) {

            case USIM_SUBJCET_EMAIL:

                lst = mEmailInfoFromPBR;
                break;

            case USIM_SUBJCET_ANR:

                lst = mAnrInfoFromPBR;
                break;
            // SPRD: PhoneBook for AAS
            case USIM_SUBJCET_AAS:
                lst = mAasInfoFromPBR;
                break;
            // SPRD: PhoneBook for SNE
            case USIM_SUBJCET_SNE:
                lst = mSneInfoFromPBR;
                break;
            default:
                break;
        }

        if (lst != null && lst.size() != 0) {
            log("getType2Ef size " + lst.size() );
            for(int i = 0; i<lst.size() ; i++){

                index = lst.get(i);

                if(index != null && index.efids != null&& index.type != null ){


                    for(int j=0; j<index.efids.length; j++){

                        if(index.type[j] == USIM_TYPE2_TAG ){
                            isAdd = true;
                            for(int k=0; k<efs.size();k++){

                                if(efs.get(k) == index.efids[j]){

                                    isAdd = false;
                                }

                            }

                            if(isAdd){
                                efs.add(index.efids[j]);
                            }


                        }

                    }


                }
            }

        }
        log("getType2Ef  type "+ type + " efs " +efs );
        return efs;
    }

    private void setUsedNumOfEfid(int type,int idx,  int efid, Object obj ) {

        LinkedList<SubjectIndexOfAdn> lst = null;
        SubjectIndexOfAdn index = null;
        switch (type) {

            case USIM_SUBJCET_EMAIL:

                lst = mEmailInfoFromPBR;
                break;

            case USIM_SUBJCET_ANR:

                lst = mAnrInfoFromPBR;
                break;
            default:
                break;
        }

        if (lst != null && lst.size() != 0) {
            log("setUsedNumOfEfid size " + lst.size() );
            for(int i = 0; i<lst.size() ; i++){

                index = lst.get(i);

                if(index != null && index.efids != null ){
                    for(int j=0; j<index.efids.length; j++){

                        if(index.efids[j] == efid){

                            index.usedSet[idx] =  obj;
                            setSubjectIndex(type,i,index);
                            break;
                        }
                    }
                }
            }
        }
    }

   private void updateAdnRecordNum(){

        int numAdnRecs = mPhoneBookRecords.size();
        log( "updateAdnRecord Num and grp info, adn size:" + numAdnRecs);
        for(int i=0;i<numAdnRecs;i++){
            AdnRecord adn = mPhoneBookRecords.get(i);
            if(adn == null) continue;
            adn.setRecordNumber(i+1);
            if(mGrpFileRecord != null && i < mGrpFileRecord.size()){
               adn.setGrp(getGrp(i));
            }
        }
    }

   private void updatePbcAndCc() {
       Rlog.i(LOG_TAG, "update EFpbc begin");
       Map<Integer, Integer> fileIds;
       fileIds = mPbrFile.mFileIds.get(0);
       if (fileIds == null || fileIds.isEmpty())
           return;
       Integer efPbcId = fileIds.get(USIM_EFPBC_TAG);
       if (efPbcId == null)
           return;
       Rlog.i(LOG_TAG, " USIM_EFPBC_TAG = " + Integer.toHexString(efPbcId));
       int changeCounter = 0;
       if (mPbcFileRecord == null) {
           mPbcFileRecord = new ArrayList<byte[]>();
       }
       int[] size;
       if (mRecordsSize != null && mRecordsSize.containsKey(efPbcId)) {
           size = mRecordsSize.get(efPbcId);
       } else {
           size = readFileSizeAndWait(efPbcId);
       }
       Rlog.i(LOG_TAG, "readPbcFileAndWait size"+size[0]+size[2]);

       for (int i = 0; i < size[2]; i++) {
           byte[] emptyValue = new byte[size[0]];
           for (byte value : emptyValue) {
               value = (byte) 0xFF;
           }
           mPbcFileRecord.add(i,emptyValue);
       }

       for (int i = 0; (i < mPbcFileRecord.size() && i < mPhoneBookRecords.size()) ; i++) {
           if (!TextUtils.isEmpty(mPhoneBookRecords.get(i).getAlphaTag())
                   || !TextUtils.isEmpty(mPhoneBookRecords.get(i).getNumber())) {
               mPendingPbcLoads.addAndGet(1);
               Rlog.i(LOG_TAG, "readPbcFile index"+i);
               mFh.loadEFLinearFixed(efPbcId, i+1, size[0], obtainMessage(EVENT_LOAD_EF_PBC_RECORD_DONE,i,0));
           }
       }
       if (mPendingPbcLoads.get() == 0) {
           mIsNotify.set(false);
       } else {
           mIsNotify.set(true);
           try {
               mLock.wait();
           } catch (InterruptedException e) {
               Rlog.e(LOG_TAG, "Interrupted Exception in updatePbcAndCc");
           }
       }

       for (int i = 0; i < mPbcFileRecord.size(); i++) {
           byte[] temp = null;
           temp = mPbcFileRecord.get(i);
           if (temp != null && ((temp[0] & 0xFF) == 0x01)) {
               changeCounter++;
               byte[] data = new byte[2];
               data[0] = (byte) 0x00;
               data[1] = (byte) 0x00;
               // udpate EF pbc
               mFh.updateEFLinearFixed(efPbcId, i + 1, data, null,
                       obtainMessage(EVENT_UPDATE_RECORD_DONE));
           }

       }
       Rlog.i(LOG_TAG, "update EFpbc end, changeCounter " + changeCounter);

       // update EFcc
       if (changeCounter > 0) {
           // get Change Counter
           mFh.loadEFTransparent(IccConstants.EF_CC, 2,
                   obtainMessage(EVENT_EF_CC_LOAD_DONE, changeCounter,0));
       }
       return;
   }

   public ArrayList<String> loadGasFromUsim() {
       log("loadGasFromUsim");

       if (!mGasList.isEmpty())
           return mGasList;

       if (mGasFileRecord == null) {
           // the gas file is Type3 in Pbr
           readGasFileAndWait(0);
       }

       if (mGasFileRecord == null) {
           Rlog.e(LOG_TAG, "Error: mGasFileRecord file is empty");
           return null;
       }

       int gasSize = mGasFileRecord.size();
       log("getGas size " + gasSize);

       byte[] gasRec = null;
       for (int i = 0; i < gasSize; i++) {
           gasRec = mGasFileRecord.get(i);
           mGasList.add(IccUtils.adnStringFieldToString(gasRec, 0, gasRec.length));
       }
       log("loadGasFromUsim mGasList: " + mGasList);
       return mGasList;
   }

   private void readGasFileAndWait(int recNum) {
       Rlog.i(LOG_TAG, "readGasFileAndWait");
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           mIsPbrFileExisting = false;
           Rlog.i(LOG_TAG, "mIsPbrFileExisting = false");
           return;
       }

       Map<Integer, Integer> fileIds;
       fileIds = mPbrFile.mFileIds.get(recNum);
       if (fileIds == null || fileIds.isEmpty())
           return;

       Integer efId = fileIds.get(USIM_EFGAS_TAG);
       if (efId == null)
           return;
       mFh.loadEFLinearFixedAll(efId, obtainMessage(EVENT_GAS_LOAD_DONE));

       synchronized (mLock) {
           try {
               mLock.wait();
           } catch (InterruptedException e) {
               Rlog.e(LOG_TAG, "Interrupted Exception in readGasFileAndWait");
           }
       }
   }

   public int[] getEmailRecordSizeArray() {
       return mEmailRecordSizeArray;
   }

   public int[] getIapRecordSizeArray() {
       return mIapRecordSizeArray;
   }

   public void setPhoneBookRecords(int index, AdnRecord adn) {
       mPhoneBookRecords.set(index, adn);
   }

   public int getPhoneBookRecordsNum() {
       return mPhoneBookRecords.size();
   }

   public synchronized int[] getAdnRecordsSize() {
       int size[] = new int[3];
       int totalSize[] = new int[3];
       Rlog.i(LOG_TAG, "getAdnRecordsSize");
       synchronized (mLock) {
           // size[2] = mSprdPhoneBookRecords.size();
           // Rlog.i(LOG_TAG, "getEFLinearRecordSize size" + size[2]);
           if (mTotalSize != null) {
               return mTotalSize;
            }
           if (mPbrFile == null) {
               readPbrFileAndWait();
            }
           if (mPbrFile == null)
               return null;
           int numRecs = mPbrFile.mFileIds.size();

           for (int i = 0; i < numRecs; i++) {
               size = readAdnFileSizeAndWait(i);
               if (size != null) {
                   totalSize[0] = size[0];
                   totalSize[1] += size[1];
                   totalSize[2] += size[2];
                   Log.i(LOG_TAG, "getAdnRecordsSize totalSize[0]" + totalSize[0] + "totalSize[1]"
                           + totalSize[1] + "totalSize[2]" + totalSize[2] );
               }
           } // All EF files are loaded, post the response. }
       }
       return totalSize;
   }

   public int[] getEfFilesFromUsim() {

       int[] efids = null;

       int len = 0;

       len = mPbrFile.mFileIds.size();
       Rlog.i(LOG_TAG, "getEfFilesFromUsim" + len);
       efids = new int[len];

       for (int i = 0; i < len; i++) {
           Map<Integer, Integer> fileIds = mPbrFile.mFileIds.get(i);
           efids[i] = fileIds.get(USIM_EFADN_TAG);
           Rlog.i(LOG_TAG, "getEfFilesFromUsim" + efids[i]);
       }

       return efids;
   }
    /*@}*/
   public boolean isPbrFileExisting(){
       Rlog.i(LOG_TAG, "mIsPbrFileExisting" + mIsPbrFileExisting);
       return mIsPbrFileExisting;
   }

   /* SPRD: PhoneBook for AAS {@ */
   public ArrayList<String> loadAasFromUsim() {
       log("loadAasFromUsim");
       if (!mAasList.isEmpty()){
           return mAasList;
       }

       if (mAasFileRecord == null) {
           readAasFileAndWait(0);
       }

       if (mAasFileRecord == null) {
           Rlog.e(LOG_TAG, "Error: mAasFileRecord file is empty");
           return null;
       }

       int aasSize = mAasFileRecord.size();
       log("getAas size " + aasSize);

       byte[] aasRec = null;
       for (int i = 0; i < aasSize; i++) {
           aasRec = mAasFileRecord.get(i);
           mAasList.add(IccUtils.adnStringFieldToString(aasRec, 0, aasRec.length));
       }
       log("loadAasFromUsim mAasList: " + mAasList);
       return mAasList;
   }

   public void readAasFileAndWait(int recNum) {
       log("readAasFileAndWait recNum " + recNum);
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           return;
       }
       log("readAasFileAndWait recNum is   " + recNum);
       Map<Integer, Integer> fileIds;
       fileIds = mPbrFile.mFileIds.get(recNum);

       if (fileIds == null || fileIds.isEmpty()){
           log("readAasFileAndWait  fileIds == null" );
           return;
       }

       log("readAasFileAndWait mAasInfoFromPBR !=null fileIds.size()  " + fileIds.size() );
       if (fileIds.containsKey(USIM_EFAAS_TAG)) {
           SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_AAS,recNum);
           if(records == null){
               log("readAasFileAndWait  records == null ");
               return;
           }

           records.record = new HashMap<Integer, ArrayList<byte[]>>();
           if(records.efids == null || records.efids.length == 0){
               log("readAasFileAndWait  records.efids == null || records.efids.length == 0");
               return;
           }

           mFh.loadEFLinearFixedAll(records.efids[0],obtainMessage(EVENT_AAS_LOAD_DONE));
           synchronized (mLock) {
               try {
                    mLock.wait();
               } catch (InterruptedException e) {
                    Rlog.e(LOG_TAG,"Interrupted Exception in readAasFileAndWait");
               }
           }

           if (mAasFileRecord == null) {
               Rlog.e(LOG_TAG, "Error: Aas file is empty");
               records.efids[0] = 0;
           } else {
               records.record.put(records.efids[0], mAasFileRecord);
           }

           handleReadFileResult(records);
           setSubjectIndex(USIM_SUBJCET_AAS,recNum,records);
           setSubjectUsedNum(USIM_SUBJCET_AAS, recNum);
       }
   }

   public int findEFAasInfo() {
       Map<Integer, Integer> fileIds;
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           return -1;
       }
       fileIds = mPbrFile.mFileIds.get(0);
       if (fileIds == null) {
           Rlog.i(LOG_TAG, "findEFAasInfo fileIds == null");
           return -1;
       }
       if (fileIds.containsKey(USIM_EFAAS_TAG)) {
           return fileIds.get(USIM_EFAAS_TAG);
       }
       return 0;
   }

   public void updateAasList(String aas, int aasIndex) {
       if (mAasList.isEmpty())
           return;
       mAasList.set(aasIndex - 1, aas);
       for(String aasStr : mAasList){
          Rlog.d("dory","updateAasList aasStr== " + aasStr);
       }
   }

   private  String getAas(SubjectIndexOfAdn aasInfo, int adnNum){
       log( "getAas adnNum: " + adnNum);
       adnNum = adnNum -1;
       String aas = null;
       if (aasInfo == null){
           return null;
       }
       if(aasInfo.efids == null ||aasInfo.efids.length == 0){
           log( "getAas aasInfo.efids == null ||aasInfo.efids.length == 0 ");
           return null;
       }
       log("aasInfo.efids == " + aasInfo.efids);
       aas = getTypeAas(aasInfo,  adnNum,  aasInfo.efids[0]);
       return aas;
   }

   private String getTypeAas(SubjectIndexOfAdn aasInfo,
           int adnNum, int efid) {

       String aas = null;
       if (aasInfo == null || aasInfo.record == null ){
           return null;
       }
       mAasFileRecord = aasInfo.record.get(efid);
       if (mAasFileRecord == null) {
           return null;
       }
       log("getTypeAas size " +  mAasFileRecord.size());
       aas = readAasRecord(adnNum);
       log( "getTypeAas,aas " + aas);

       if (TextUtils.isEmpty(aas)) {
           log("getTypeAas,aas==null");
           return null;
       }
       return aas;
   }

   private String readAasRecord(int recNum) {
       byte[] aasRec = null;
       try {
           aasRec = mAasFileRecord.get(recNum);
       } catch (IndexOutOfBoundsException e) {
           return null;
       }
       String aas = IccUtils.adnStringFieldToString(aasRec, 0, aasRec.length - 2);
       return aas;
   }

   private void setAas(int adnNum, String aas) {
       AdnRecord rec = mPhoneBookRecords.get(adnNum);
       log("setAas,rec name:" + rec.getAlphaTag()
               + "num " + rec.getNumber() + " adnNum " + adnNum);

       if (rec == null && aas != null) {
           rec = new AdnRecord("", "");
       }
       if (aas != null) {
           rec.setAas(aas);
           log( "setAas AdnRecord  aas"+ aas);
       }
       mPhoneBookRecords.set(adnNum, rec);
   }
   /* @} */
   /* SPRD: PhoneBook for SNE {@ */
   private void readSneFileAndWait(int recNum) {
       log("readSnelFileAndWait");
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           return;
       }
       Map<Integer, Integer> fileIds;
       fileIds = mPbrFile.mFileIds.get(recNum);
       if (fileIds == null){
           return;
       }

       if (fileIds.containsKey(USIM_EFSNE_TAG)) {
           int efid = fileIds.get(USIM_EFSNE_TAG);
           SubjectIndexOfAdn records = getSubjectIndex(USIM_SUBJCET_SNE, recNum);
           if (records == null) {
               log("readSnelFileAndWait  records == null ");
               return;
           }

           Integer efId = fileIds.get(USIM_EFSNE_TAG);
           if (efId == null){
               return;
           }
           Rlog.d("readSneFileAndWait","efId == " + efId);

           mFh.loadEFLinearFixedAll(efId,obtainMessage(EVENT_SNE_LOAD_DONE));
           try {
               mLock.wait();
           } catch (InterruptedException e) {
               Rlog.e(LOG_TAG, "Interrupted Exception in readSneFileAndWait");
           }

           if (mSneFileRecord == null) {
               Rlog.e(LOG_TAG, "Error: sne file is empty");
               records = null;
               setSubjectIndex(USIM_SUBJCET_SNE, recNum, records);
               return;
           }

           records.record = new HashMap<Integer, ArrayList<byte[]>>();
           records.record.put(efid, mSneFileRecord);
           log("readSnelFileAndWait recNum " + recNum + "  mSneFileRecord  size "
                   + mSneFileRecord.size() + ", fileid:" + fileIds.get(USIM_EFSNE_TAG));
           setSubjectIndex(USIM_SUBJCET_SNE, recNum, records);
           setSubjectUsedNum(USIM_SUBJCET_SNE, recNum);
           mSneFileRecord = null;
       }
   }

   private  String getSne(int num, SubjectIndexOfAdn sneInfo,
           byte[] record, int adnNum){
       log( "getSne sneNum: " + adnNum + "num " + num);

       String sneGroup = null;
       String sne = null;
       if(sneInfo == null || sneInfo.record ==null){
           return null;
       }
       if(sneInfo.efids == null ||sneInfo.efids.length == 0){
           log( "getSne sneInfo.efids == null ||sneInfo.efids.length == 0 ");
           return null;
       }
       for (int i = 0; i < sneInfo.efids.length; i++) {
           if(sneInfo.type[i] == USIM_TYPE1_TAG){
               sne = getType1Sne( num,  sneInfo,  adnNum,  sneInfo.efids[i]);
           }
           if(sneInfo.type[i] == USIM_TYPE2_TAG && sneInfo.recordNumInIap != null){
               sne = getType2Sne( num,  sneInfo, record,  adnNum, sneInfo.efids[i]);
           }
           if (i == 0) {
               sneGroup = sne;
           } else {
               sneGroup = sneGroup + ";" + sne;
           }
       }
       return sneGroup;
   }

   private String getType1Sne(int num, SubjectIndexOfAdn sneInfo,
           int adnNum, int efid) {
       String snes = null;
       mSneFileRecord = sneInfo.record.get(efid);
       if (mSneFileRecord == null) {
           return null;
       }
       log("getType1Sne size " +  mSneFileRecord.size());
       snes = readSneRecord(adnNum);
       log( "getType1Sne,snes " + snes);

       if (TextUtils.isEmpty(snes)) {
           log("getType1Sne,snes==null");
           return null;
       }
       return snes;
   }

   private String getType2Sne(int num, SubjectIndexOfAdn sneInfo,
           byte[] record, int adnNum, int efid) {
       String snes = null;
       int index = -1;
       log(" getType2Sne >>  sneInfo.recordNumInIap.size() "
               + sneInfo.recordNumInIap.size() + " adnNum " + adnNum + " efid " +efid);
       if(record == null){
           return snes;
       }
       index = getUsedNumSetIndex(efid,sneInfo );
       if(index == -1){
           return snes;
       }

       mSneTagNumberInIap = sneInfo.recordNumInIap.get(efid);
       mSneFileRecord = sneInfo.record.get(efid);
       if (mSneFileRecord == null) {
           return snes;
       }

       int recNum = (int) (record[mSneTagNumberInIap] & 0xFF);
       recNum = ((recNum == 0xFF) ? (-1) : recNum);
       log("getType2Sne  iap recNum == " + recNum);
       if (recNum != -1) {
           snes = readSneRecord(recNum - 1);
           log( "getType2Sne,snes " + snes);
           if (TextUtils.isEmpty(snes)) {
               log("getType2Sne,snes ==null");
               setIapFileRecord(num, adnNum, (byte) 0xFF, mSneTagNumberInIap);
               return null;
           }
           Set<Integer> set = (Set<Integer>) sneInfo.usedSet[index];
           log("getType2Sne  size (0)" +  set.size()  + " index " + index);
           set.add(new Integer(recNum));
           sneInfo.usedSet[index] = set;
           log("getType2Sne  size (1)" +  set.size());
           setSubjectIndex(USIM_SUBJCET_SNE,num,sneInfo);
       }
       return snes;
   }

   private String readSneRecord(int recNum) {
       byte[] sneRec = null;
       try {
           sneRec = mSneFileRecord.get(recNum);
       } catch (IndexOutOfBoundsException e) {
           return null;
       }
       // The length of the record is X+2 byte, where X bytes is the sne address
       String sne = IccUtils.adnStringFieldToString(sneRec, 0, sneRec.length - 2);
       return sne;
   }

   private void setSne(int adnNum, String snes) {
       AdnRecord rec = mPhoneBookRecords.get(adnNum);
       log("setSne,rec name:" + rec.getAlphaTag()
               + "num " + rec.getNumber() + " adnNum " + adnNum);

       if (rec == null && snes != null) {
           rec = new AdnRecord("", "");
       }
       if (snes != null) {
           rec.setSne(snes);
           log( "setSne AdnRecord  sne" + snes);
       }
       mPhoneBookRecords.set(adnNum, rec);
   }

   public int getSneSize(){
       return mSneEfSize;
   }

   public int[] getSneLength(){
       int [] sneSize = null;
       synchronized (mLock) {
           sneSize = readSneFileSizeAndWait();
       }
       return sneSize;
   }

   private int[]  readSneFileSizeAndWait(){
       Rlog.i(LOG_TAG, "readSneFileSizeAndWait");
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           return null;
       }
       Map<Integer, Integer> fileIds;
       fileIds = mPbrFile.mFileIds.get(0);
       if (fileIds == null || fileIds.isEmpty()){
           return null;
       }

       Integer efId = fileIds.get(USIM_EFSNE_TAG);
       if (efId == null){
           return null;
       }

       mFh.getEFLinearRecordSize(efId,obtainMessage(EVENT_SNE_RECORD_COUNT));
       try {
           mLock.wait();
       } catch (InterruptedException e) {
           Rlog.e(LOG_TAG, "Interrupted Exception in readSneFileSizeAndWait");
       }
       return sneRecordSize;
   }

   public int findEFSneInfo(int index) {
       Map<Integer, Integer> fileIds;
       synchronized (mLock) {
           if (mPbrFile == null) {
               readPbrFileAndWait();
           }
       }
       if (mPbrFile == null) {
           Rlog.e(LOG_TAG, "Error: Pbr file is empty");
           return -1;
       }
       fileIds = mPbrFile.mFileIds.get(index);
       if (fileIds == null) {
           Rlog.i(LOG_TAG, "findEFSNEInfo  fileIds == null  index :" + index);
           return -1;
       }
       if (fileIds.containsKey(USIM_EFSNE_TAG)) {
           return fileIds.get(USIM_EFSNE_TAG);
       }
       return 0;
   }
   /* @} */
}