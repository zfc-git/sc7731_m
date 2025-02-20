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

import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;

import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccRecords;
import com.android.internal.telephony.uicc.UiccCard;
import com.android.internal.telephony.uicc.UiccCardApplication;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.IccPhoneBookOperationException;
import android.os.HandlerThread;
/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public abstract class IccPhoneBookInterfaceManager {
    protected static final boolean DBG = true;

    protected PhoneBase mPhone;
    private   UiccCardApplication mCurrentApp = null;
    protected AdnRecordCache mAdnCache;
    protected final Object mLock = new Object();
    protected int mRecordSize[];
    protected boolean mSuccess;
    private   boolean mIs3gCard = false;  // flag to determine if card is 3G or 2G
    protected List<AdnRecord> mRecords;


    protected static final boolean ALLOW_SIM_OP_IN_UI_THREAD = false;

    protected static final int EVENT_GET_SIZE_DONE = 1;
    protected static final int EVENT_LOAD_DONE = 2;
    protected static final int EVENT_UPDATE_DONE = 3;

    /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
    protected boolean mReadAdnRecordSuccess = false;
    protected boolean mReadFdnRecordSuccess = false;
    protected boolean mReadSdnRecordSuccess = false;
    protected boolean mReadLndRecordSuccess = false;
    /* @} */

    protected int mSimIndex = -1;

    public abstract int[] getEmailRecordsSize();

    public abstract int[] getAnrRecordsSize();

    public abstract int getEmailNum();

    public abstract int getAnrNum();

    public abstract int getEmailMaxLen();

    public abstract int getPhoneNumMaxLen();

    public abstract int getUsimGroupNameMaxLen();

    public abstract int getUsimGroupCapacity();

    public abstract int [] getAvalibleEmailCount(String name, String number,
            String[] emails, String anr, int[] emailNums);

    public abstract int [] getAvalibleAnrCount(String name, String number,
            String[] emails, String anr, int[] anrNums);
    /* @} */

     /*SPRD:modify for bug 474587 @{*/
     /*protected Handler mBaseHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_GET_SIZE_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            mRecordSize = (int[])ar.result;
                            // recordSize[0]  is the record length
                            // recordSize[1]  is the total length of the EF file
                            // recordSize[2]  is the number of records in the EF file
                            logd("GET_RECORD_SIZE Size " + mRecordSize[0] +
                                    " total " + mRecordSize[1] +
                                    " #record " + mRecordSize[2]);
                        }
                        notifyPending(ar);
                    }
                    break;
                case EVENT_UPDATE_DONE:
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        mSuccess = (ar.exception == null);
                        logd("EVENT_UPDATE_DONE  mSuccess " +mSuccess);
                        if (mSuccess) {
                            mSimIndex = getInsertIndex();
                        }else {
                            //mSimIndex = -1;
                            loge("[EVENT_UPDATE_DONE] exception = " +  ar.exception);
                            if(ar.exception instanceof IccPhoneBookOperationException){
                                mSimIndex = ((IccPhoneBookOperationException)ar.exception).mErrorCode;
                            }else{
                                mSimIndex = -1;
                            }
                        }
                        loge("EVENT_UPDATE_DONE  mSimIndex " +mSimIndex);
                        notifyPending(ar);
                    }
                    break;
                case EVENT_LOAD_DONE:
                    ar = (AsyncResult)msg.obj;
                    synchronized (mLock) {
                        logd("EVENT_LOAD_DONE, ar.exception = "+ar.exception);
                        if (ar.exception == null) {
                            //mRecords = (List<AdnRecord>) ar.result;
                            mReadRecordSuccess =  true;
                            mRecords = (List<AdnRecord>) ar.result;
                        } else {
                            if(DBG) logd("Cannot load ADN records");
                            mReadRecordSuccess =  false;
                            if (mRecords != null) {
                                mRecords.clear();
                            }
                        }
                        notifyPending(ar);
                    }
                    break;
            }
        }

        private void notifyPending(AsyncResult ar) {
            if (ar.userObj != null) {
                AtomicBoolean status = (AtomicBoolean) ar.userObj;
                status.set(true);
            }
            mLock.notifyAll();
        }
    };*/
    /* @} */

    public IccPhoneBookInterfaceManager(PhoneBase phone) {
        this.mPhone = phone;
        IccRecords r = phone.mIccRecords.get();
        if (r != null) {
            mAdnCache = r.getAdnCache();
        }
        /*SPRD:modify for bug 474587 @{*/
        createUpdateThread();
        /* @} */
    }

    public void dispose() {
    }

    public void updateIccRecords(IccRecords iccRecords) {
        if (iccRecords != null) {
            mAdnCache = iccRecords.getAdnCache();
        } else {
            mAdnCache = null;
        }
    }

    protected abstract void logd(String msg);

    protected abstract void loge(String msg);

    /**
     * Replace oldAdn with newAdn in ADN-like record in EF
     *
     * getAdnRecordsInEf must be called at least once before this function,
     * otherwise an error will be returned. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param oldTag adn tag to be replaced
     * @param oldPhoneNumber adn number to be replaced
     *        Set both oldTag and oldPhoneNubmer to "" means to replace an
     *        empty record, aka, insert new record
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number ot be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) {


        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }


        if (DBG) logd("updateAdnRecordsInEfBySearch: efid=" + efid +
                " ("+ oldTag + "," + oldPhoneNumber + ")"+ "==>" +
                " ("+ newTag + "," + newPhoneNumber + ")"+ " pin2=" + pin2);

        efid = updateEfForIccType(efid);

        synchronized(mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            AdnRecord oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                mAdnCache.updateAdnBySearch(efid, oldAdn, newAdn, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by search due to uninitialised adncache");
            }
        }
        return mSuccess;
    }

    /**
     * Update an ADN-like EF record by record index
     *
     * This is useful for iteration the whole ADN file, such as write the whole
     * phone book or erase/format the whole phonebook. Currently the email field
     * if set in the ADN record is ignored.
     * throws SecurityException if no WRITE_CONTACTS permission
     *
     * @param efid must be one among EF_ADN, EF_FDN, and EF_SDN
     * @param newTag adn tag to be stored
     * @param newPhoneNumber adn number to be stored
     *        Set both newTag and newPhoneNubmer to "" means to replace the old
     *        record with empty one, aka, delete old record
     * @param index is 1-based adn record index to be updated
     * @param pin2 required to update EF_FDN, otherwise must be null
     * @return true for success
     */
    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (DBG) logd("updateAdnRecordsInEfByIndex: efid=" + efid +
                " Index=" + index + " ==> " +
                "("+ newTag + "," + newPhoneNumber + ")"+ " pin2=" + pin2);
        synchronized(mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            AdnRecord newAdn = new AdnRecord(newTag, newPhoneNumber);
            if (mAdnCache != null) {
                mAdnCache.updateAdnByIndex(efid, newAdn, index, pin2, response);
                waitForResult(status);
            } else {
                loge("Failure while trying to update by index due to uninitialised adncache");
            }
        }
        return mSuccess;
    }

    /**
     * Get the capacity of records in efid
     *
     * @param efid the EF id of a ADN-like ICC
     * @return  int[3] array
     *            recordSizes[0]  is the single record length
     *            recordSizes[1]  is the total length of the EF file
     *            recordSizes[2]  is the number of records in the EF file
     */
    public abstract int[] getAdnRecordsSize(int efid);

    /**
     * Loads the AdnRecords in efid and returns them as a
     * List of AdnRecords
     *
     * throws SecurityException if no READ_CONTACTS permission
     *
     * @param efid the EF id of a ADN-like ICC
     * @return List of AdnRecord
     */
    public List<AdnRecord> getAdnRecordsInEf(int efid) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
                    "Requires android.permission.READ_CONTACTS permission");
        }

        efid = updateEfForIccType(efid);
        if (DBG) logd("getAdnRecordsInEF: efid=" + efid);

        synchronized(mLock) {
            /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
            //checkThread();
            setReadRecordOfEfid(efid, false);
            /* @} */
            AtomicBoolean status = new AtomicBoolean(false);
            /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
            Message response = mBaseHandler.obtainMessage(EVENT_LOAD_DONE, efid, 0, status);
            /* @} */
            /* SPRD:add SIMPhoneBook for bug 474587 @{ */
            logd("requestLoadAllAdnLike  efid = " + efid);
            /* @} */
            if (mAdnCache != null) {
                mAdnCache.requestLoadAllAdnLike(efid, mAdnCache.extensionEfForEf(efid), response);
                waitForResult(status);
            } else {
                loge("Failure while trying to load from SIM due to uninitialised adncache");
            }
        }
        /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
        if (!(getReadRecordOfEfid(efid))) {
            logd("mReadRecordSuccess = false efid = " + efid);
            return null;
        }
        logd("getAdnRecordsInEf success :efid = " + efid);
        /* @} */
        return mRecords;
    }

    protected void checkThread() {
        if (!ALLOW_SIM_OP_IN_UI_THREAD) {
            // Make sure this isn't the UI thread, since it will block
            //SPRD:modify for bug 474587
            //if (mBaseHandler.getLooper().equals(Looper.myLooper())) {
            if (mUIHandler.getLooper().equals(Looper.myLooper())) {
                loge("query() called on the main UI thread!");
                throw new IllegalStateException(
                        "You cannot call query on this provder from the main UI thread.");
            }
        }
    }

    protected void waitForResult(AtomicBoolean status) {
        while (!status.get()) {
            try {
                mLock.wait();
            } catch (InterruptedException e) {
                logd("interrupted while trying to update by search");
            }
        }
    }

    private int updateEfForIccType(int efid) {
        boolean isPbrFileExisting =true;
        if(mAdnCache != null && mAdnCache.getUsimPhoneBookManager() !=null ) {
            isPbrFileExisting = mAdnCache.getUsimPhoneBookManager().isPbrFileExisting();
        }
        // Check if we are trying to read ADN records
        if (efid == IccConstants.EF_ADN) {
            logd("isPbrFileExisting = "+isPbrFileExisting);
            if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM && isPbrFileExisting == true) {
                return IccConstants.EF_PBR;
            }
        }
        return efid;
    }

    /* SPRD:add SIMPhoneBook for bug 474587 @{ */

    public int updateAdnRecordsInEfBySearch(int efid, String oldTag,
            String oldPhoneNumber, String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp,
            String newTag, String newPhoneNumber, String[] newEmailList,
            String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }
        logd("updateAdnRecordsInEfBySearchEx: efid=" + efid + " (" + newTag
                    + "," + newPhoneNumber + ")" + " pin2=" + pin2);

        int newid = updateEfForIccType(efid);

        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            AdnRecord oldAdn = null;
            AdnRecord newAdn = null;
            if (mAdnCache == null) {
                loge("updateAdnRecordsInEfBySearchEx failed because mAdnCache is null");
                return mSimIndex;
            }
            if (newid == IccConstants.EF_LND) {
                logd("insertLNDRecord: efid=" + efid + " ("
                        + newTag + "," + newPhoneNumber + ")" + " pin2=" + pin2);
                oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
                newAdn = new AdnRecord(newTag, newPhoneNumber);
                mAdnCache.insertLndBySearch(newid, oldAdn, newAdn, pin2, response);
            } else if (newid == IccConstants.EF_PBR) {
                oldAdn = new AdnRecord(oldTag, oldPhoneNumber, oldEmailList,
                        oldAnr, "", oldSne, oldGrp, "");
                newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailList,
                        newAnr, newAas, newSne, newGrp, newGas);

                mAdnCache.updateUSIMAdnBySearch(newid, oldAdn, newAdn, pin2,
                        response);

            } else {
                oldAdn = new AdnRecord(oldTag, oldPhoneNumber);
                newAdn = new AdnRecord(newTag, newPhoneNumber);
                mAdnCache.updateAdnBySearch(newid, oldAdn, newAdn, pin2,
                        response);
            }
            waitForResult(status);
        }
        logd("updateAdnRecordsInEfBySearchEx end " + mSuccess + ", mSimIndex "+mSimIndex);
        return mSimIndex;
    }

    public int updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, String[] newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int index, String pin2) {

        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }

        if (DBG)
            logd("updateAdnRecordsInEfByIndexEx: efid=" + Integer.toHexString(efid)
                    + " (" + newTag + "," + newPhoneNumber + ")" + " index=" + index);

        int newid = updateEfForIccType(efid);

        if (DBG)
            logd("updateAdnRecordsInEfByIndexEx: newid=" + Integer.toHexString(newid));


        synchronized (mLock) {
            checkThread();
            mSuccess = false;
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_UPDATE_DONE, status);
            AdnRecord oldAdn = null;
            AdnRecord newAdn = null;
            if (mAdnCache == null) {
                loge("updateAdnRecordsInEfByIndexEx failed because mAdnCache is null");
                return mSimIndex;
            }
            if (newid == IccConstants.EF_PBR) {

                newAdn = new AdnRecord(newTag, newPhoneNumber, newEmailList,
                        newAnr, newAas, newSne, newGrp, newGas);
                mAdnCache.updateUSIMAdnByIndex(newid, index, newAdn, pin2,response);
            } else {
                newAdn = new AdnRecord(newTag, newPhoneNumber);
                mAdnCache.updateAdnByIndex(newid, newAdn, index, pin2, response);
            }
            waitForResult(status);
        }
        logd("updateAdnRecordsInEfByIndexEx end "+mSuccess +" mSimIndex = "+mSimIndex);

        return mSimIndex;

    }

    public int[] getRecordsSize(int efid) {
        if (DBG) logd("getRecordsSize: efid=" + Integer.toHexString(efid));
        if(efid<=0){
            loge("the efid is invalid");
            return null;
        }
        synchronized (mLock) {
            checkThread();
            mRecordSize = new int[3];

            // Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE, status);

            if (mPhone.getIccFileHandler() != null) {
                mPhone.getIccFileHandler().getEFLinearRecordSize(efid, response);
            }
            waitForResult(status);
        }

        return mRecordSize;
    }

    public boolean isApplicationOnIcc(int type) {
        if (mPhone.getCurrentUiccAppType() == IccCardApplicationStatus.AppType
                .values()[type]) {
            return true;
        }
        return false;
    }

    public int getInsertIndex() {
        if (mAdnCache == null) {
            loge("getInsertIndex:adn cache is null");
            return -1;
        }
        return mAdnCache.mInsertId;
    }

    public int updateUsimGroupById(String newName,int groupId){
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }
        if (mAdnCache == null) {
            loge("updateUsimGroupById failed because mAdnCache is null");
            return -1;
        }
        return mAdnCache.updateGasByIndex(newName, groupId);
    }

    public List<String> getGasInEf() {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.READ_CONTACTS permission");
        }
        if (mPhone.getCurrentUiccAppType() != AppType.APPTYPE_USIM) {
            loge("Can not get gas from a sim card" );
            return null;
        }
        if (mAdnCache == null) {
            loge("getGasInEf failed because mAdnCache is null");
            return new ArrayList<String>();
        }
        return mAdnCache.loadGasFromUsim();
    }

    public int updateUsimGroupBySearch(String oldName,String newName) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }
        if (mAdnCache == null) {
            loge("updateUsimGroupBySearchEx failed because mAdnCache is null");
            return -1;
        }
        return mAdnCache.updateGasBySearch(oldName, newName);
    }

    /* @} */
    /*SPRD:modify for bug 474587 @{*/
    private HandlerThread mUpdateThread;
    protected UpdateThreadHandler mBaseHandler;
    private Handler mUIHandler = new Handler();

    protected class UpdateThreadHandler extends Handler {
        public UpdateThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
            case EVENT_GET_SIZE_DONE:
                ar = (AsyncResult) msg.obj;
                synchronized (mLock) {
                    if (ar.exception == null) {
                        mRecordSize = (int[])ar.result;
                        // recordSize[0]  is the record length
                        // recordSize[1]  is the total length of the EF file
                        // recordSize[2]  is the number of records in the EF file
                        logd("GET_RECORD_SIZE Size " + mRecordSize[0] +
                                " total " + mRecordSize[1] +
                                " #record " + mRecordSize[2]);
                    }
                    notifyPending(ar);
                }
                break;
            case EVENT_UPDATE_DONE:
                ar = (AsyncResult) msg.obj;
                synchronized (mLock) {
                    mSuccess = (ar.exception == null);
                    logd("EVENT_UPDATE_DONE  mSuccess " +mSuccess);
                    if (mSuccess) {
                        mSimIndex = getInsertIndex();
                    }else {
                        //mSimIndex = -1;
                        loge("[EVENT_UPDATE_DONE] exception = " +  ar.exception);
                        if(ar.exception instanceof IccPhoneBookOperationException){
                            mSimIndex = ((IccPhoneBookOperationException)ar.exception).mErrorCode;
                        }else{
                            mSimIndex = -1;
                        }
                    }
                    loge("EVENT_UPDATE_DONE  mSimIndex " +mSimIndex);
                    notifyPending(ar);
                }
                break;
            case EVENT_LOAD_DONE:
                ar = (AsyncResult)msg.obj;
                synchronized (mLock) {
                    logd("EVENT_LOAD_DONE, ar.exception = " + ar.exception);
                    if (ar.exception == null) {
                        /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
                        logd("EVENT_LOAD_DONE, msg.arg1 = " + msg.arg1);
                        setReadRecordOfEfid(msg.arg1, true);
                        /* @} */
                        mRecords = (List<AdnRecord>) ar.result;
                    } else {
                        if(DBG) logd("Cannot load ADN records");
                        //SPRD BUG 574794: Using the same variable causes disorder when using it
                        setReadRecordOfEfid(msg.arg1, false);
                        /*if (mRecords != null) {
                            mRecords.clear();
                        }*/
                    }
                    notifyPending(ar);
                }
                break;
        }
        }
        private void notifyPending(AsyncResult ar) {
            if (ar.userObj != null) {
                AtomicBoolean status = (AtomicBoolean) ar.userObj;
                status.set(true);
            }
            mLock.notifyAll();
        }
    };

    private void createUpdateThread() {
        mUpdateThread = new HandlerThread("RunningState:Background");
        mUpdateThread.start();
        mBaseHandler = new UpdateThreadHandler(mUpdateThread.getLooper());
    }
    /* @} */
    /* SPRD: PhoneBook for AAS {@ */
    public List<String> getAasInEf() {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.READ_CONTACTS permission");
        }
        if (mPhone.getCurrentUiccAppType() != AppType.APPTYPE_USIM) {
            loge("Can not get aas from a sim card" );
            return null;
        }
        if (mAdnCache == null) {
            loge("getAasInEf failed because mAdnCache is null");
            return new ArrayList<String>();
        }
        return mAdnCache.loadAasFromUsim();
    }

    public int updateUsimAasBySearch(String oldName,String newName) {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }
        if (mAdnCache == null) {
            loge("updateUsimAasBySearch failed because mAdnCache is null");
            return -1;
        }
        return mAdnCache.updateAasBySearch(oldName, newName);
    }

    public int updateUsimAasByIndex(String newName,int aasIndex){
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.WRITE_CONTACTS permission");
        }
        if (mAdnCache == null) {
            loge("updateUsimAasByIndex failed because mAdnCache is null");
            return -1;
        }
        return mAdnCache.updateAasByIndex(newName, aasIndex);
    }
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    public int getSneSize() {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.READ_CONTACTS permission");
        }
        if (mPhone.getCurrentUiccAppType() != AppType.APPTYPE_USIM) {
            loge("Can not get sne size from a sim card" );
            return 0;
        }
        if (mAdnCache == null) {
            loge("getSneSize failed because mAdnCache is null");
            return 0;
        }
        return mAdnCache.getSneSize();
    }

    public int[] getSneLength() {
        if (mPhone.getContext().checkCallingOrSelfPermission(
                android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(
            "Requires android.permission.READ_CONTACTS permission");
        }
        if (mPhone.getCurrentUiccAppType() != AppType.APPTYPE_USIM) {
            loge("Can not get sne length from a sim card" );
            return null;
        }
        if (mAdnCache == null) {
            loge("getSneLength failed because mAdnCache is null");
            return null;
        }
        return mAdnCache.getSneLength();
    }
    /* @} */
    /*SPRD BUG 574794: Using the same variable causes disorder when using it @{ */
    private void setReadRecordOfEfid(int efid , boolean readSuccess){
        switch (efid) {
        case IccConstants.EF_ADN:
        case IccConstants.EF_PBR:
             mReadAdnRecordSuccess = readSuccess;
             break;
        case IccConstants.EF_SDN:
             mReadSdnRecordSuccess = readSuccess;
             break;
        case IccConstants.EF_FDN:
             mReadFdnRecordSuccess = readSuccess;
             break;
        case IccConstants.EF_LND:
             mReadLndRecordSuccess = readSuccess;
             break;
       }
    }

    private boolean getReadRecordOfEfid(int efid){
        switch (efid) {
        case IccConstants.EF_ADN:
        case IccConstants.EF_PBR:
                return mReadAdnRecordSuccess;
        case IccConstants.EF_SDN:
                return mReadSdnRecordSuccess;
        case IccConstants.EF_FDN:
                return mReadFdnRecordSuccess;
        case IccConstants.EF_LND:
                return mReadLndRecordSuccess;
        default: return false;
       }
    }
    /* @} */
}

