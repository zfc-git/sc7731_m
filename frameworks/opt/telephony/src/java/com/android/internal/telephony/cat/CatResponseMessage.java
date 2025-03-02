/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.telephony.cat;

public class CatResponseMessage {
    CommandDetails mCmdDet = null;
    ResultCode mResCode = ResultCode.OK;
    int mUsersMenuSelection = 0;
    String mUsersInput = null;
    boolean mUsersYesNoSelection = false;
    boolean mUsersConfirm = false;
    boolean mIncludeAdditionalInfo = false;
    int mAdditionalInfo = 0;
    int mEventValue = -1;
    byte[] mAddedInfo = null;
    /* SPRD: Add here for BIP function @{ */
    byte BearerType = 0;
    String BearerParam = null;
    int bufferSize;
    int ChannelId;
    boolean LinkStatus;
    int channelDataLen = 0;
    String channelData = null;
    int mMode;
    /* @} */

    public CatResponseMessage(CatCmdMessage cmdMsg) {
        mCmdDet = cmdMsg.mCmdDet;
    }

    public void setResultCode(ResultCode resCode) {
        mResCode = resCode;
    }

    public void setMenuSelection(int selection) {
        mUsersMenuSelection = selection;
    }

    public void setInput(String input) {
        mUsersInput = input;
    }

    public void setEventDownload(int event, byte[] addedInfo) {
        this.mEventValue = event;
        this.mAddedInfo = addedInfo;
    }

    public void setYesNo(boolean yesNo) {
        mUsersYesNoSelection = yesNo;
    }

    public void setConfirmation(boolean confirm) {
        mUsersConfirm = confirm;
    }

    public void setAdditionalInfo(int info) {
        mIncludeAdditionalInfo = true;
        mAdditionalInfo = info;
    }

    CommandDetails getCmdDetails() {
        return mCmdDet;
    }

    /* SPRD: Add here for BIP function @{ */
    public void setBearerParam(byte type, String param, int size) {
        BearerType = type;
        BearerParam = param;
        bufferSize = size;
    }

    public void setChannelStatus(int id, boolean status) {
        ChannelId = id;
        LinkStatus = status;
        mMode = 0;
    }

    public void setChannelStatus(int id, boolean status, int mode) {
        ChannelId = id;
        LinkStatus = status;
        mMode = mode;
    }

    public void setChannelDataLen(int len) {
        channelDataLen = len;
    }

    public void setChannelData(String str) {
        channelData = str;
    }
    /* @} */

}
