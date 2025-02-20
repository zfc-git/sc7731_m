/*
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.internal.telephony.gsm;

import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Message;
import android.telephony.Rlog;

import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.uicc.IccFileHandler;
import com.android.internal.telephony.gsm.UsimPhoneBookManager;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccCardApplicationStatus.AppType;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */


public class SimPhoneBookInterfaceManager extends IccPhoneBookInterfaceManager {
    static final String LOG_TAG = "SimPhoneBookIM";

    public SimPhoneBookInterfaceManager(GSMPhone phone) {
        super(phone);
        //NOTE service "simphonebook" added by IccSmsInterfaceManagerProxy
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    @Override
    protected void finalize() {
        try {
            super.finalize();
        } catch (Throwable throwable) {
            Rlog.e(LOG_TAG, "Error while finalizing:", throwable);
        }
        if(DBG) Rlog.d(LOG_TAG, "SimPhoneBookInterfaceManager finalized");
    }

    @Override
    public int[] getAdnRecordsSize(int efid) {
        /* SPRD: add SIMPhoneBook for bug 474587 @{ */
        /*if (DBG) logd("getAdnRecordsSize: efid=" + efid);
        synchronized(mLock) {
            checkThread();
            mRecordSize = new int[3];

            //Using mBaseHandler, no difference in EVENT_GET_SIZE_DONE handling
            AtomicBoolean status = new AtomicBoolean(false);
            Message response = mBaseHandler.obtainMessage(EVENT_GET_SIZE_DONE, status);

            IccFileHandler fh = mPhone.getIccFileHandler();
            if (fh != null) {
                fh.getEFLinearRecordSize(efid, response);
                waitForResult(status);
            }
        }

        return mRecordSize;*/
        logd("getAdnRecordsSize");
        if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM
                && (efid == IccConstants.EF_ADN)) {
            int[] size = getUsimAdnRecordsSize();
            if (null == size) {
                size = getRecordsSize(efid);
            }
            return size;
        } else {
            return getRecordsSize(efid);
        }
        /* @} */
    }

    @Override
    protected void logd(String msg) {
        Rlog.d(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }

    @Override
    protected void loge(String msg) {
        Rlog.e(LOG_TAG, "[SimPbInterfaceManager] " + msg);
    }

  /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    private int[] getUsimAdnRecordsSize() {
        logd("getUsimAdnRecordsSize");
        if (mAdnCache == null) {
            return null;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return null;
        }
        return mUsimPhoneBookManager.getAdnRecordsSize();
    }

    @Override
    public int[] getEmailRecordsSize() {
        logd("getEmailRecordsSize");
        if (mAdnCache == null) {
            return null;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return null;
        }
        int efid;
        Set<Integer> usedEfIds = new HashSet<Integer>();
        int[] recordSizeEmail, recordSizeTotal = new int[3];

        for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
            efid = mUsimPhoneBookManager.findEFEmailInfo(num);

            //has this efid been read ?
            if(efid <= 0 || usedEfIds.contains(efid)) {
                continue;
            }else{
                usedEfIds.add(efid);
            }

            recordSizeEmail = getRecordsSize(efid);
            recordSizeTotal[0] = recordSizeEmail[0];
            recordSizeTotal[1] += recordSizeEmail[1];
            recordSizeTotal[2] += recordSizeEmail[2];
        }
        return recordSizeTotal;
    }

    @Override
    public int[] getAnrRecordsSize() {
        logd("getAnrRecordsSize");
        if (mAdnCache == null) {
            return null;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return null;
        }
        int efid;
        int[] recordSizeAnr, recordSizeTotal = new int[3];
        for (int num = 0; num < mUsimPhoneBookManager.getNumRecs(); num++) {
            efid = mUsimPhoneBookManager.findEFAnrInfo(num);
            if (efid <= 0) {
                return null;
            }
            recordSizeAnr = getRecordsSize(efid);
            recordSizeTotal[0] = recordSizeAnr[0];
            recordSizeTotal[1] += recordSizeAnr[1];
            recordSizeTotal[2] += recordSizeAnr[2];
        }
        return recordSizeTotal;
    }

    @Override
    public int getEmailNum() {
        int[] record = null;
        if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            if (mAdnCache == null) {
                return 0;
            }
            UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
            if (mUsimPhoneBookManager == null) {
                return 0;
            }
            return mUsimPhoneBookManager.getEmailNum();
        }

        return 0;
    }

    @Override
    public int getAnrNum() {
        if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            if (mAdnCache == null) {
                return 0;
            }
            UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
            if (mUsimPhoneBookManager == null) {
                return 0;
            }

            return mUsimPhoneBookManager.getAnrNum();

        }

        return 0;

    }

    @Override
    public int getEmailMaxLen() {
        logd("getEmailMaxLen");
        if (mAdnCache == null) {
            return 0;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return 0;
        }

        int efid = mUsimPhoneBookManager.findEFEmailInfo(0);
        int[] recordSizeEmail = getRecordsSize(efid);
        if (recordSizeEmail == null) {
            return 0;
        }
        if (mUsimPhoneBookManager.getEmailType() == 1) {
            return recordSizeEmail[0];
        } else {
            return recordSizeEmail[0] - 2;
        }

    }

    // If the telephone number or SSC is longer than 20 digits, the first 20
    // digits are stored in this data item and the remainder is stored in an
    // associated record in the EFEXT1.
    public int getPhoneNumMaxLen() {
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return AdnRecord.MAX_LENTH_NUMBER;
        } else {
            return mUsimPhoneBookManager.getPhoneNumMaxLen();
        }
    }

    @Override
    public int getUsimGroupNameMaxLen() {
        logd("getGroupNameMaxLen");
        if (mAdnCache == null) {
            return -1;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            return -1;
        }
        int gasEfId = mUsimPhoneBookManager.findEFGasInfo();
        int[] gasSize = mPhone.getIccPhoneBookInterfaceManager().getRecordsSize(gasEfId);
        if (gasSize == null)
            return -1;
        return gasSize[0];
    }

    public int [] getAvalibleEmailCount(String name, String number,
            String[] emails, String anr, int[] emailNums){
        if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            if (mAdnCache == null) {
                return null;
            }
            UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
            if (mUsimPhoneBookManager == null) {
                return null;
            }

            return mUsimPhoneBookManager.getAvalibleEmailCount(name,number,emails,anr,emailNums);

        }

        return null;
      }

      public  int [] getAvalibleAnrCount(String name, String number,
            String[] emails, String anr, int[] anrNums) {
        int[] record = null;
        if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
            if (mAdnCache == null) {
                return null;
            }
            UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
            if (mUsimPhoneBookManager == null) {
                return null;
            }

            return mUsimPhoneBookManager.getAvalibleAnrCount(name, number,
                    emails, anr, anrNums);

        }

        return null;
    }

    private UsimPhoneBookManager getUsimPhoneBookManager() {
          if (mPhone.getCurrentUiccAppType() == AppType.APPTYPE_USIM) {
              return mAdnCache.getUsimPhoneBookManager();
          }
          return null;
      }

    @Override
    public int getUsimGroupCapacity() {
        logd("getUsimGroupCapacity");
        if (mPhone.getCurrentUiccAppType() != AppType.APPTYPE_USIM) {
            loge("Can not get gas from a sim card" );
            return 0;
        }
        if (mAdnCache == null) {
            loge("mAdnCache == null");
            return 0;
        }
        UsimPhoneBookManager mUsimPhoneBookManager = getUsimPhoneBookManager();
        if (mUsimPhoneBookManager == null) {
            loge("mUsimPhoneBookManager == null");
            return 0;
        }
        int gasEfId = mUsimPhoneBookManager.findEFGasInfo();
        int[] gasSize = mPhone.getIccPhoneBookInterfaceManager().getRecordsSize(gasEfId);
        if (gasSize == null || gasSize.length < 3){
            return 0;
         }
        return gasSize[2];
    }

  /* @} */
}

