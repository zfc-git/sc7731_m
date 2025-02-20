/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2011-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import android.os.ServiceManager;
import android.os.RemoteException;
import android.telephony.Rlog;

import com.android.internal.telephony.IccPhoneBookInterfaceManagerProxy;
import com.android.internal.telephony.IIccPhoneBook;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.uicc.AdnRecord;

import java.lang.ArrayIndexOutOfBoundsException;
import java.lang.NullPointerException;
import java.util.List;

public class UiccPhoneBookController extends IIccPhoneBook.Stub {
    private static final String TAG = "UiccPhoneBookController";
    private Phone[] mPhone;

    /* only one UiccPhoneBookController exists */
    public UiccPhoneBookController(Phone[] phone) {
        if (ServiceManager.getService("simphonebook") == null) {
               ServiceManager.addService("simphonebook", this);
        }
        mPhone = phone;
    }

    @Override
    public boolean
    updateAdnRecordsInEfBySearch (int efid, String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber, String pin2) throws android.os.RemoteException {
        return updateAdnRecordsInEfBySearchForSubscriber(getDefaultSubscription(), efid, oldTag,
                oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    @Override
    public boolean
    updateAdnRecordsInEfBySearchForSubscriber(int subId, int efid, String oldTag,
            String oldPhoneNumber, String newTag, String newPhoneNumber,
            String pin2) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfBySearch(efid, oldTag,
                    oldPhoneNumber, newTag, newPhoneNumber, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return false;
        }
    }

    @Override
    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) throws android.os.RemoteException {
        return updateAdnRecordsInEfByIndexForSubscriber(getDefaultSubscription(), efid, newTag,
                newPhoneNumber, index, pin2);
    }

    @Override
    public boolean
    updateAdnRecordsInEfByIndexForSubscriber(int subId, int efid, String newTag,
            String newPhoneNumber, int index, String pin2) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndex(efid, newTag,
                    newPhoneNumber, index, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfByIndex iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return false;
        }
    }

    @Override
    public int[] getAdnRecordsSize(int efid) throws android.os.RemoteException {
        return getAdnRecordsSizeForSubscriber(getDefaultSubscription(), efid);
    }

    @Override
    public int[]
    getAdnRecordsSizeForSubscriber(int subId, int efid) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsSize(efid);
        } else {
            Rlog.e(TAG,"getAdnRecordsSize iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    @Override
    public List<AdnRecord> getAdnRecordsInEf(int efid) throws android.os.RemoteException {
        return getAdnRecordsInEfForSubscriber(getDefaultSubscription(), efid);
    }

    @Override
    public List<AdnRecord> getAdnRecordsInEfForSubscriber(int subId, int efid)
           throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                             getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAdnRecordsInEf(efid);
        } else {
            Rlog.e(TAG,"getAdnRecordsInEf iccPbkIntMgrProxy is" +
                      "null for Subscription:"+subId);
            return null;
        }
    }

    /**
     * get phone book interface manager proxy object based on subscription.
     **/
    private IccPhoneBookInterfaceManagerProxy
            getIccPhoneBookInterfaceManagerProxy(int subId) {

        int phoneId = SubscriptionController.getInstance().getPhoneId(subId);
        try {
            return ((PhoneProxy)mPhone[(int)phoneId]).getIccPhoneBookInterfaceManagerProxy();
        } catch (NullPointerException e) {
            Rlog.e(TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace(); //To print stack trace
            return null;
        } catch (ArrayIndexOutOfBoundsException e) {
            Rlog.e(TAG, "Exception is :"+e.toString()+" For subscription :"+subId );
            e.printStackTrace();
            return null;
        }
    }

    private int getDefaultSubscription() {
        return PhoneFactory.getDefaultSubscription();
    }

    /* SPRD: add SIMPhoneBook for bug 474587 @{ */
    public int
    updateAdnRecordsInEfBySearchForSubscriberEx(int subId, int efid, String oldTag,
            String oldPhoneNumber, String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp,
            String newTag, String newPhoneNumber, String[] newEmailList,
            String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfBySearch(efid,
                    oldTag, oldPhoneNumber, oldEmailList, oldAnr, oldSne, oldGrp, newTag,
                    newPhoneNumber, newEmailList, newAnr, newAas, newSne, newGrp,
                    newGas, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return -1;
        }
    }

    public int
    updateAdnRecordsInEfByIndexForSubscriberEx(int subId, int efid, String newTag,
            String newPhoneNumber, String[] newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int index, String pin2){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateAdnRecordsInEfByIndex(efid,
                    newTag, newPhoneNumber, newEmailList, newAnr, newAas, newSne,
                    newGrp, newGas, index, pin2);
        } else {
            Rlog.e(TAG,"updateAdnRecordsInEfBySearch iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return -1;
        }
    }

    public List<String> getGasInEfForSubscriber(int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getGasInEf();
        } else {
            Rlog.e(TAG,"getGasInEfForSubscriber iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public int updateUsimGroupBySearchForSubscriber(int subId, String oldName,String newName) {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateUsimGroupBySearch(oldName, newName);
        } else {
            Rlog.e(TAG,"updateUsimGroupBySearchForSubscriber iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return -1;
        }
    }

    public int updateUsimGroupByIdForSubscriber(int subId, String newName,int groupId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.updateUsimGroupById(newName, groupId);
        } else {
            Rlog.e(TAG,"updateUsimGroupByIdForSubscriber iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return -1;
        }
    }

    public boolean isApplicationOnIcc(int type, int subId) {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.isApplicationOnIcc(type);
        } else {
            Rlog.e(TAG,"isApplicationOnIcc iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return false;
        }
    }

    public int[] getEmailRecordsSize(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getEmailRecordsSize();
        } else {
            Rlog.e(TAG,"getEmailRecordsSize iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public int[] getAnrRecordsSize(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAnrRecordsSize();
        } else {
            Rlog.e(TAG,"getAnrRecordsSize iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public int getAnrNum(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAnrNum();
        } else {
            Rlog.e(TAG,"getAnrNum iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return 0;
        }
    }

    public int getInsertIndex(int subId) {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getInsertIndex();
        } else {
            Rlog.e(TAG,"getInsertIndex iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return -1;
        }
    }

    public int[] getAvalibleEmailCount(String name, String number,
            String[] emails, String anr, int[] emailNums, int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAvalibleEmailCount(name,number,emails,anr,emailNums );
        } else {
            Rlog.e(TAG,"getAvalibleEmailCount iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public int[] getAvalibleAnrCount(String name, String number,
            String[] emails, String anr, int[] anrNums, int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAvalibleAnrCount(name,number,emails,anr,anrNums);
        } else {
            Rlog.e(TAG,"getAvalibleAnrCount iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return null;
        }
    }

    public int getEmailMaxLen(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getEmailMaxLen();
        } else {
            Rlog.e(TAG,"getEmailMaxLen iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return 0;
        }
    }

    public int getPhoneNumMaxLen(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getPhoneNumMaxLen();
        } else {
            Rlog.e(TAG,"getPhoneNumMaxLen iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return 0;
        }
    }

    public int getUsimGroupNameMaxLen(int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getUsimGroupNameMaxLen();
        } else {
            Rlog.e(TAG,"getUsimGroupNameMaxLen iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return 0;
        }
    }

    public int getEmailNum(int subId) throws android.os.RemoteException {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getEmailNum();
        } else {
            Rlog.e(TAG,"getEmailNum iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
            return 0;
        }
    }

    public int getUsimGroupCapacity(int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        int usimGroupCapacity = 0;
        if (iccPbkIntMgrProxy != null) {
            usimGroupCapacity = iccPbkIntMgrProxy.getUsimGroupCapacity();
        } else {
            Rlog.e(TAG,"getUsimGroupCapacity iccPbkIntMgrProxy is" +
                      " null for Subscription:"+subId);
        }
        Rlog.i(TAG,"usimGroupCapacity = " + usimGroupCapacity);
        return usimGroupCapacity;
    }

   /* @} */
    /* SPRD: PhoneBook for AAS {@ */
    public List<String> getAasInEfForSubscriber(int subId) {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getAasInEf();
        }
        return null;
    }

    public int updateUsimAasBySearchForSubscriber(String oldName, String newName, int subId) {
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        int usimAas = 0;
        if (iccPbkIntMgrProxy != null) {
            usimAas = iccPbkIntMgrProxy.updateUsimAasBySearch(oldName, newName);
        }
        Rlog.d(TAG, "updateUsimAasBySearch, usimAas = " + usimAas);
        return usimAas;
    }

    public int updateUsimAasByIndexForSubscriber(String newName,int aasIndex, int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        int usimAas = 0;
        if (iccPbkIntMgrProxy != null) {
            usimAas = iccPbkIntMgrProxy.updateUsimAasByIndex(newName, aasIndex);
        }
        Rlog.d(TAG, "updateUsimAasByIndex, usimAas = " + usimAas);
        return usimAas;
    }
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    public int getSneSize(int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        int sneSize = 0;
        if (iccPbkIntMgrProxy != null) {
            sneSize = iccPbkIntMgrProxy.getSneSize();
        }
        return sneSize;
    }

    public int[] getSneLength(int subId){
        IccPhoneBookInterfaceManagerProxy iccPbkIntMgrProxy =
                getIccPhoneBookInterfaceManagerProxy(subId);
        if (iccPbkIntMgrProxy != null) {
            return iccPbkIntMgrProxy.getSneLength();
        }
        return null;
    }
    /* @} */
}
