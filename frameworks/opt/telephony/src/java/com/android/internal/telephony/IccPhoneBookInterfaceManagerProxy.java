/*
 * Copyright (C) 2008 The Android Open Source Project
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
import com.android.internal.telephony.uicc.AdnRecord;


import java.util.List;


/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public class IccPhoneBookInterfaceManagerProxy {
    private IccPhoneBookInterfaceManager mIccPhoneBookInterfaceManager;

    public IccPhoneBookInterfaceManagerProxy(IccPhoneBookInterfaceManager
            iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public void setmIccPhoneBookInterfaceManager(
            IccPhoneBookInterfaceManager iccPhoneBookInterfaceManager) {
        mIccPhoneBookInterfaceManager = iccPhoneBookInterfaceManager;
    }

    public boolean
    updateAdnRecordsInEfBySearch (int efid,
            String oldTag, String oldPhoneNumber,
            String newTag, String newPhoneNumber,
            String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(
                efid, oldTag, oldPhoneNumber, newTag, newPhoneNumber, pin2);
    }

    public boolean
    updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, int index, String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid,
                newTag, newPhoneNumber, index, pin2);
    }

    public int[] getAdnRecordsSize(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsSize(efid);
    }

    public List<AdnRecord> getAdnRecordsInEf(int efid) {
        return mIccPhoneBookInterfaceManager.getAdnRecordsInEf(efid);
    }

    /* SPRD:add SIMPhoneBook for bug 474587 @{ */

    public int updateAdnRecordsInEfBySearch(int efid, String oldTag,
            String oldPhoneNumber, String[] oldEmailList, String oldAnr,
            String oldSne, String oldGrp,
            String newTag, String newPhoneNumber, String[] newEmailList,
            String newAnr, String newAas, String newSne, String newGrp,
            String newGas, String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfBySearch(efid,
                oldTag, oldPhoneNumber, oldEmailList, oldAnr, oldSne, oldGrp, newTag,
                newPhoneNumber, newEmailList, newAnr, newAas, newSne, newGrp,
                newGas, pin2);
    }

    public int updateAdnRecordsInEfByIndex(int efid, String newTag,
            String newPhoneNumber, String[] newEmailList, String newAnr,
            String newAas, String newSne, String newGrp, String newGas,
            int index, String pin2) {
        return mIccPhoneBookInterfaceManager.updateAdnRecordsInEfByIndex(efid,
                newTag, newPhoneNumber, newEmailList, newAnr, newAas, newSne,
                newGrp, newGas, index, pin2);
    }

    public boolean isApplicationOnIcc(int type){
        return mIccPhoneBookInterfaceManager.isApplicationOnIcc(type);
    }

    public int getEmailNum() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getEmailNum();
    }

    public int getUsimGroupNameMaxLen(){
        return mIccPhoneBookInterfaceManager.getUsimGroupNameMaxLen();
    }

    public int getPhoneNumMaxLen() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getPhoneNumMaxLen();
    }

    public int getEmailMaxLen() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getEmailMaxLen();
    }

    public int[] getAvalibleAnrCount(String name, String number,
            String[] emails, String anr, int[] anrNums) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAvalibleAnrCount(name,number,emails,anr,anrNums);
    }

    public int[] getAvalibleEmailCount(String name, String number,
            String[] emails, String anr, int[] emailNums) throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAvalibleEmailCount(name,number,emails,anr,emailNums );
    }

    public int getInsertIndex() {
        return mIccPhoneBookInterfaceManager.getInsertIndex();
    }

    public int getAnrNum() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAnrNum();
    }

    public int[] getAnrRecordsSize() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getAnrRecordsSize();
    }

    public int[] getEmailRecordsSize() throws android.os.RemoteException {
        return mIccPhoneBookInterfaceManager.getEmailRecordsSize();
    }

    public int updateUsimGroupById(String newName,int groupId){
        return mIccPhoneBookInterfaceManager.updateUsimGroupById(newName, groupId);
    }

    public List<String> getGasInEf(){
        return mIccPhoneBookInterfaceManager.getGasInEf();
    }

    public int updateUsimGroupBySearch(String oldName,String newName) {
        return mIccPhoneBookInterfaceManager.updateUsimGroupBySearch(oldName, newName);
    }

    public int getUsimGroupCapacity() {
        return mIccPhoneBookInterfaceManager.getUsimGroupCapacity();
    }
    /* @} */
    /* SPRD: PhoneBook for AAS {@ */
    public List<String> getAasInEf(){
        return mIccPhoneBookInterfaceManager.getAasInEf();
    }

    public int updateUsimAasBySearch(String oldName,String newName) {
        return mIccPhoneBookInterfaceManager.updateUsimAasBySearch(oldName, newName);
    }

    public int updateUsimAasByIndex(String newName,int aasIndex){
        return mIccPhoneBookInterfaceManager.updateUsimAasByIndex(newName, aasIndex);
    }
    /* @} */
    /* SPRD: PhoneBook for SNE {@ */
    public int getSneSize(){
        return mIccPhoneBookInterfaceManager.getSneSize();
    }

    public int[] getSneLength(){
        return mIccPhoneBookInterfaceManager.getSneLength();
    }
    /* @} */
}
