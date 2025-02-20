/*
 * Copyright (C) 2006, 2012 The Android Open Source Project
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

import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.uicc.UiccCardApplication;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import android.os.*;
import android.util.Log;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.uicc.IccException;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.uicc.IccFileTypeMismatch;
import com.android.internal.telephony.uicc.IccIoResult;

/**
 * {@hide}
 * This class should be used to access files in USIM ADF
 */
public final class UsimFileHandler extends IccFileHandler implements IccConstants {
    static final String LOG_TAG = "UsimFH";
    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    // ***** types of files UICC 12.1.1.3
    static protected final byte TYPE_FCP = 0x62;
    static protected final byte RESPONSE_DATA_FCP_FLAG = 0;
    static protected final byte TYPE_FILE_DES = (byte) 0x82;
    static protected final byte TYPE_FCP_SIZE = (byte) 0x80;
    static protected final byte RESPONSE_DATA_FILE_DES_FLAG = 2;
    static protected final byte RESPONSE_DATA_FILE_DES_LEN_FLAG = 3;
    static protected final byte TYPE_FILE_DES_LEN = 5;
    static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_1 = 6;
    static protected final byte RESPONSE_DATA_FILE_RECORD_LEN_2 = 7;
    static protected final byte RESPONSE_DATA_FILE_RECORD_COUNT_FLAG = 8;

    static private final byte USIM_RECORD_SIZE_1 = 4;
    static private final byte USIM_RECORD_SIZE_2 = 5;
    static private final byte USIM_RECORD_COUNT = 6;
    static private final int USIM_DATA_OFFSET_2 = 2;
    static private final int USIM_DATA_OFFSET_3 = 3;

    private CommandsInterface mCi;

    // private final int mDualMapFile[] ={EF_ADN,EF_ARR,EF_FDN,EF_SMS,EF_MSISDN,
    // EF_SMSP,EF_SMSS,EF_SMSR,EF_SDN,EF_EXT2,EF_EXT3,EF_EXT4,EF_BDN,EF_TEST};
    private final int mDualMapFile[] = { EF_SMS, EF_PBR , EF_SDN, EF_EXT3};
    private Map<Integer, String> mDualMapFileList;
    private ArrayList<Integer> mFileList;
    /* @} */

    public UsimFileHandler(UiccCardApplication app, String aid, CommandsInterface ci) {
        super(app, aid, ci);
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        mCi = ci;
        initDualMapFileSet();
        /* @} */
    }

    @Override
    protected String getEFPath(int efid) {
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        String path = null;

        path = getEfPathFromList(efid);
        if (path != null) {
            return path;
        }
        /* @} */
        switch(efid) {
        case EF_SMS:
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            return MF_SIM + DF_ADF;
        case EF_ECC:
            /* @} */
        case EF_EXT5:
        case EF_EXT6:
        case EF_MWIS:
        case EF_MBI:
        case EF_SPN:
        case EF_AD:
        case EF_MBDN:
        case EF_PNN:
        case EF_OPL:
        case EF_SPDI:
        case EF_SST:
        case EF_CFIS:
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
             return MF_SIM + DF_ADF;
        case EF_FDN:
             return MF_SIM + DF_ADF;
             /* @} */
        case EF_MAILBOX_CPHS:
        case EF_VOICE_MAIL_INDICATOR_CPHS:
        case EF_CFF_CPHS:
        case EF_SPN_CPHS:
        case EF_SPN_SHORT_CPHS:
        //case EF_MSISDN:
        //case EF_EXT2:
        case EF_INFO_CPHS:
        case EF_CSP_CPHS:
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            return MF_SIM + DF_GSM;
        case EF_MSISDN:
            return MF_SIM + DF_ADF;
            /* @} */
        case EF_GID1:
        case EF_GID2:
        case EF_LI:
           return MF_SIM + DF_ADF;

         // SPRD: [bug452051] Return EF path for EF_DIR
        case EF_DIR:
            return MF_SIM;
        case EF_PBR:
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            // we only support global phonebook.
            //return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            if (mDualMapFileList != null) {
                return mDualMapFileList.get(EF_PBR);
            } else {
                return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            }
            /* @} */
        }
        ///* SPRD: add SIMPhoneBook for bug 474587 @{ */
        //String path = getCommonIccEFPath(efid);
        path = getCommonIccEFPath(efid);
        /* @} */
        if (path == null) {
            // The EFids in USIM phone book entries are decided by the card manufacturer.
            // So if we don't match any of the cases above and if its a USIM return
            // the phone book path.
            /* SPRD: add SIMPhoneBook for bug 474587 @{ */
            //return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            if (mDualMapFileList != null) {
                return mDualMapFileList.get(EF_PBR);
            } else {
                return MF_SIM + DF_TELECOM + DF_PHONEBOOK;
            }
        }
        return path;
    }

    @Override
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, msg);
    }

    @Override
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, msg);
    }

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    private void initDualMapFileSet() {
        mDualMapFileList = new HashMap<Integer, String>();
        mFileList = new ArrayList<Integer>();

        mDualMapFileList.put(mDualMapFile[0], MF_SIM + DF_ADF);
        mDualMapFileList.put(mDualMapFile[1], MF_SIM + DF_TELECOM
                + DF_PHONEBOOK);
        mDualMapFileList.put(mDualMapFile[2], MF_SIM + DF_ADF);
        mDualMapFileList.put(mDualMapFile[3], MF_SIM + DF_ADF);
    }

    private void clearDualMapFileSet() {
        if (mFileList != null) {

            mFileList = null;
        }

        if (mDualMapFileList != null) {
            mDualMapFileList.clear();
            mDualMapFileList = null;
        }
    }

    private void UpdatePathOfDualMapFile(int efid, String path) {

        loge("UpdatePathOfDualMapFile  efid " + efid + " path " + path);

        if (mDualMapFileList != null) {

            mDualMapFileList.put(efid, path);

        }

    }

    public void dispose() {

        super.dispose();
        clearDualMapFileSet();
    }

    protected void finalize() throws Throwable {
        super.finalize();
        Rlog.d(LOG_TAG, "UsimFileHandler finalized");
    }

    /**
     * Load a SIM Transparent EF
     *
     * @param fileid
     *            EF id
     * @param onLoaded
     *            ((AsyncResult)(onLoaded.obj)).result is the byte[]
     */

    private boolean isDualMapFile(int fileId) {

        Rlog.i(LOG_TAG, "isDualMapFile  fileId " + fileId
                + " mDualMapFileList " + mDualMapFileList);
        if (mDualMapFileList == null) {

            return false;
        }

        if (mDualMapFileList.containsKey(fileId)) {

            return true;
        }

        return false;
    }

    private boolean isFinishLoadFile(int fileId, int pathNum) {

        Rlog.i(LOG_TAG, "isFinishLoadFile  fileId " + fileId + ", pathNum "
                + pathNum);

        if (isDualMapFile(fileId)) {

            if (pathNum == 1) {

                return true;

            }
            if (pathNum == 0) {

                return false;
            }
        }

        return true;

    }

    protected String getEFPathofUsim(int efid) {
        String oldPath = getEFPath(efid);
        String pathFirst = "";
        String pathSecond = "";
        String pathLast = "";

        if (oldPath.length() < 8) {
            return null;
        } else {

            pathFirst = oldPath.substring(0, 4);
            pathSecond = oldPath.substring(4, 8);

            if (oldPath.length() > 8) {
                pathLast = oldPath.substring(8, oldPath.length());
            }
        }

        Rlog.i(LOG_TAG, "getEFPathofUsim false , try again pathFirst "
                + pathFirst + ", pathSecond " + pathSecond + ", pathLast "
                + pathLast);
        if (pathSecond.equals(DF_ADF)) {

            pathSecond = DF_TELECOM;

        } else if (pathSecond.equals(DF_TELECOM)) {

            pathSecond = DF_ADF;

        } else {

            return null;
        }

        String newPath = pathFirst + pathSecond + pathLast;
        UpdatePathOfDualMapFile(efid, newPath);
        return newPath;

    }

    public boolean loadFileAgain(int fileId, int pathNum, int event, Object obj) {

        if (isFinishLoadFile(fileId, pathNum)) {

            return false;
        } else {

            String newPath = getEFPathofUsim(fileId);
            if (newPath == null) {

                return false;
            }

            ((LoadLinearFixedContext)obj).mPath = newPath;
            Message response = obtainMessage(event, fileId, 1, obj);

            Rlog.i(LOG_TAG, "isFinishLoadFile  try again newPath   " + newPath);
            mCi.iccIO(COMMAND_GET_RESPONSE, fileId, newPath, 0, 0,
                    GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
        }

        return true;

    }

    // ***** Overridden from IccFileHandler

    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;
        IccIoResult result;
        Message response = null;
        IccFileHandler.LoadLinearFixedContext lc;

        IccException iccException;
        byte data[];
        int size, fcp_size;
        int fileid;
        int recordNum;
        int recordSize[];
        int index = 0;
        int pathNum = msg.arg2;
        String path;
        try {
            switch (msg.what) {
            case EVENT_READ_IMG_DONE:
                ar = (AsyncResult) msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;

                if (ar.exception != null) {
                    Rlog.d(LOG_TAG, "EVENT_READ_IMG_DONE ar fail");
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                if (iccException != null) {
                    Rlog.d(LOG_TAG, "EVENT_READ_IMG_DONE icc fail");
                    sendResult(response, null, iccException);
                    break;
                }
                data = result.payload;
                fileid = lc.mEfid;
                recordNum = lc.mRecordNum;
                Rlog.d(LOG_TAG, "data = " + IccUtils.bytesToHexString(data)
                        + " fileid = " + fileid + " recordNum = " + recordNum);
                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                    Rlog.d(LOG_TAG, "EVENT_READ_IMG_DONE TYPE_EF mismatch");
                    throw new IccFileTypeMismatch();
                }
                if (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                    Rlog.d(LOG_TAG,
                            "EVENT_READ_IMG_DONE EF_TYPE_LINEAR_FIXED mismatch");
                    throw new IccFileTypeMismatch();
                }
                lc.mRecordSize = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                        + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
                lc.mCountRecords = size / lc.mRecordSize;
                if (lc.mLoadAll) {
                    lc.results = new ArrayList<byte[]>(lc.mCountRecords);
                }
                Rlog.d(LOG_TAG, "recordsize:" + lc.mRecordSize + "counts:"
                        + lc.mCountRecords);
                mCi.iccIO(COMMAND_READ_RECORD, lc.mEfid, getEFPath(lc.mEfid),
                        lc.mRecordNum, READ_RECORD_MODE_ABSOLUTE,
                        lc.mRecordSize, null, null,
                        obtainMessage(EVENT_READ_RECORD_DONE, lc));
                break;
            case EVENT_READ_ICON_DONE:
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, result.payload, ar.exception);
                } else {
                    sendResult(response, result.payload, null);
                }
                break;
            /* SPRD:add SIMPhoneBook for bug 430057.USIM case 8.1.1 @{ */
            case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
            case EVENT_GET_RECORD_SIZE_IMG_DONE:
            case EVENT_GET_RECORD_SIZE_DONE:
                logd("msg.what = " + msg.what);
                ar = (AsyncResult) msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;
                if (ar.exception != null) {
                    loge("ar.exception = " + ar.exception);
                    sendResult(response, null, ar.exception);
                    break;
                }
                data = result.payload;
                fileid = lc.mEfid;
                iccException = result.getException();
                if ((iccException != null || (fileid == EF_PBR && !isDataValid(data)))
                        && loadFileAgain(fileid, pathNum, msg.what, lc)) {
                    logd("load again return");
                    return;
                }
                super.handleMessage(msg);
                break;
            case EVENT_GET_BINARY_SIZE_DONE:
                logd("EVENT_GET_BINARY_SIZE_DONE");
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;
                if (ar.exception != null) {
                    loge("ar.exception = " + ar.exception);
                    sendResult(response, null, ar.exception);
                    break;
                }
                iccException = result.getException();
                fileid = msg.arg1;
                if (iccException != null && loadFileAgain(fileid, pathNum, msg.what, response)){
                    logd("load again return");
                    return;
                }
                super.handleMessage(msg);
                break;
                /* @} */
            case EVENT_READ_RECORD_DONE:
                logd("EVENT_READ_RECORD_DONE");
                ar = (AsyncResult) msg.obj;
                lc = (IccFileHandler.LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.mOnLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();

                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                if (!lc.mLoadAll) {
                    sendResult(response, result.payload, null);
                } else {
                    lc.results.add(result.payload);

                    lc.mRecordNum++;

                    if (lc.mRecordNum > lc.mCountRecords) {
                        sendResult(response, lc.results, null);
                    } else {

                        path = getEFPath(lc.mEfid);
                        // logd("EVENT_READ_RECORD_DONE, fileid:"+lc.mEfid+"("+Integer.toHexString(lc.mEfid)+")");
                        // logd("EVENT_READ_RECORD_DONE, path:"+path+",  lc.mRecordNum:"+
                        // lc.mRecordNum);
                        mCi.iccIO(
                                COMMAND_READ_RECORD,
                                lc.mEfid,
                                path,
                                lc.mRecordNum,
                                READ_RECORD_MODE_ABSOLUTE,
                                lc.mRecordSize,
                                null,
                                null,
                                obtainMessage(EVENT_READ_RECORD_DONE, 0,
                                        pathNum, lc));
                    }
                }
                break;

            case EVENT_READ_BINARY_DONE:
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();

                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                sendResult(response, result.payload, null);
                break;
            default:
                super.handleMessage(msg);

            }
        } catch (Exception exc) {
            /*SPRD:Bug 502447 print stack trace for debug @{*/
            exc.printStackTrace();
            if (response != null && response.getTarget() != null) {
             /*@}*/
                sendResult(response, null, exc);
            } else {
                loge("uncaught exception" + exc);
            }
        }
    }

    private String getEfPathFromList(int efid) {

        String path = null;
        // logd("getEfPathFromList, efid:"+Integer.toHexString(efid));
        if (mDualMapFileList == null) {

            return null;
        }

        if (mDualMapFileList.containsKey(efid)) {

            path = mDualMapFileList.get(efid);

            if (path != null) {

                return path;
            }
        }

        if (mFileList == null) {

            return null;
        }

        for (int i = 0; i < mFileList.size(); i++) {

            if (mFileList.get(i) == efid) {

                path = mDualMapFileList.get(EF_PBR);

                if (path != null) {

                    return path;
                } else {

                    break;
                }
            }
        }

        return null;

    }

    protected void logbyte(byte data[]) {
        String test = new String();
        logd("logbyte, data length:" + data.length);
        for (int i = 0; i < data.length; i++) {
            test = Integer.toHexString(data[i] & 0xFF);
            if (test.length() == 1) {
                test = '0' + test;
            }
            logd("payload:" + test);
        }
    }

    protected boolean isDataValid(byte data[]) {
        boolean isValid = false;
        for (int i = 0; i < data.length; i++) {
            if (data[i] != (byte)0xFF) {//Modify by bug 449595. Add cast 0xFF to byte.
                isValid = true;
                break;
            }
        }

        logd("isDataValid:" + isValid);
        return isValid;
    }
    /* @} */
}
