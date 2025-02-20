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

import android.os.Parcel;
import android.os.Parcelable;
/* SPRD: Add here for BIP function @{ */
import com.android.internal.telephony.cat.bip.*;
/* @} */

/**
 * Class used to pass CAT messages from telephony to application. Application
 * should call getXXX() to get commands's specific values.
 *
 */
public class CatCmdMessage implements Parcelable {
    // members
    CommandDetails mCmdDet;
    private TextMessage mTextMsg;
    private Menu mMenu;
    private Input mInput;
    private BrowserSettings mBrowserSettings = null;
    private ToneSettings mToneSettings = null;
    private CallSettings mCallSettings = null;
    private SetupEventListSettings mSetupEventListSettings = null;
    /* SPRD: Add here for BIP function @{ */
    private OpenChannelData mOpenChannel;
    private CloseChannelData mCloseChannel;
    private ReceiveChannelData mReceiveData;
    private SendChannelData mSendData;
    private GetChannelStatus mChannelStatus;
    private DeviceIdentities mDeviceIdentities = null;
    /* @} */
    /* SPRD: Add DTMF function. @{ */
    private DtmfMessage mDtmfMessage;
    /* @} */
    /* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
    private LanguageMessage mLanguageMessage;
    /* @}*/

    /*
     * Container for Launch Browser command settings.
     */
    public class BrowserSettings {
        public String url;
        public LaunchBrowserMode mode;
    }

    /*
     * Container for Call Setup command settings.
     */
    public class CallSettings {
        public TextMessage confirmMsg;
        public TextMessage callMsg;
    }

    public class SetupEventListSettings {
        public int[] eventList;
    }

    public final class SetupEventListConstants {
        // Event values in SETUP_EVENT_LIST Proactive Command as per ETSI 102.223
        /* SPRD: USAT case 27.22.4.16.1 @{ */
        public static final int MT_CALL_EVENT                = 0x00;
        public static final int CALL_CONNECTED_EVENT         = 0x01;
        public static final int CALL_DISCONNECTED_EVENT      = 0x02;
        public static final int LOCATION_STATUS_EVENT        = 0x03;
        /* @} */
        public static final int USER_ACTIVITY_EVENT          = 0x04;
        public static final int IDLE_SCREEN_AVAILABLE_EVENT  = 0x05;
        public static final int LANGUAGE_SELECTION_EVENT     = 0x07;
        public static final int BROWSER_TERMINATION_EVENT    = 0x08;
        public static final int BROWSING_STATUS_EVENT        = 0x0F;
        /* SPRD: add here for EVENTDOWNLOAD function @{ */
        public static final int DATA_AVAILABLE_EVENT        = 0x09;
        public static final int CHANNEL_STATUS_EVENT        = 0x0A;
        /* @} */
    }

    public final class BrowserTerminationCauses {
        public static final int USER_TERMINATION             = 0x00;
        public static final int ERROR_TERMINATION            = 0x01;
    }

    CatCmdMessage(CommandParams cmdParams) {
        mCmdDet = cmdParams.mCmdDet;
        switch(getCmdType()) {
        case SET_UP_MENU:
        case SELECT_ITEM:
            mMenu = ((SelectItemParams) cmdParams).mMenu;
            break;
        /* SPRD: Add DTMF function. @{ */
        case SEND_DTMF:
            mDtmfMessage = new DtmfMessage();
            mDtmfMessage.mdtmfString = ((DtmfParams)cmdParams).dtmfString;
            mTextMsg = ((DtmfParams)cmdParams).textMsg;
            break;
        /* @} */
        case DISPLAY_TEXT:
        case SET_UP_IDLE_MODE_TEXT:
        case SEND_SMS:
        case SEND_SS:
        case SEND_USSD:
            mTextMsg = ((DisplayTextParams) cmdParams).mTextMsg;
            break;
        case GET_INPUT:
        case GET_INKEY:
            mInput = ((GetInputParams) cmdParams).mInput;
            break;
        case LAUNCH_BROWSER:
            mTextMsg = ((LaunchBrowserParams) cmdParams).mConfirmMsg;
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = ((LaunchBrowserParams) cmdParams).mUrl;
            mBrowserSettings.mode = ((LaunchBrowserParams) cmdParams).mMode;
            break;
        case PLAY_TONE:
            PlayToneParams params = (PlayToneParams) cmdParams;
            mToneSettings = params.mSettings;
            mTextMsg = params.mTextMsg;
            break;
        /* SPRD: Modify here for BIP function @{ */
        case GET_CHANNEL_STATUS:
            //mTextMsg = ((CallSetupParams) cmdParams).mConfirmMsg;
            mChannelStatus = ((GetChannelStatusParams) cmdParams).channelstatus;
            break;
        /* @} */
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = ((CallSetupParams) cmdParams).mConfirmMsg;
            mCallSettings.callMsg = ((CallSetupParams) cmdParams).mCallMsg;
            break;
        /* SPRD: Modify here for BIP function @{ */
        case OPEN_CHANNEL:
            mOpenChannel = ((OpenChannelDataParams) cmdParams).openchanneldata;
            break;
        case CLOSE_CHANNEL:
            mCloseChannel = ((CloseChannelDataParams) cmdParams).closechanneldata;
            mDeviceIdentities = ((CloseChannelDataParams) cmdParams).deviceIdentities;
            break;
        case RECEIVE_DATA:
            mReceiveData = ((ReceiveChannelDataParams) cmdParams).receivedata;
            break;
        case SEND_DATA:
            //BIPClientParams param = (BIPClientParams) cmdParams;
            //mTextMsg = param.mTextMsg;
            mSendData = ((SendChannelDataParams) cmdParams).senddata;
            mDeviceIdentities = ((SendChannelDataParams) cmdParams).deviceIdentities;
            break;
        /* @} */
        case SET_UP_EVENT_LIST:
            mSetupEventListSettings = new SetupEventListSettings();
            mSetupEventListSettings.eventList = ((SetEventListParams) cmdParams).mEventInfo;
            break;
        /* SPRD: Add here for REFRESH function @{ */
        case REFRESH:
            mTextMsg = ((DisplayTextParams) cmdParams).mTextMsg;
            break;
        /* @} */
        /* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
        case LANGUAGE_NOTIFACTION:
            mLanguageMessage = new LanguageMessage();
            mLanguageMessage.languageString = ((LanguageParams)cmdParams).languageString;
            break;
        /* @}*/
        case PROVIDE_LOCAL_INFORMATION:
        default:
            break;
        }
    }

    public CatCmdMessage(Parcel in) {
        mCmdDet = in.readParcelable(null);
        mTextMsg = in.readParcelable(null);
        mMenu = in.readParcelable(null);
        mInput = in.readParcelable(null);
        switch (getCmdType()) {
        case LAUNCH_BROWSER:
            mBrowserSettings = new BrowserSettings();
            mBrowserSettings.url = in.readString();
            mBrowserSettings.mode = LaunchBrowserMode.values()[in.readInt()];
            break;
        case PLAY_TONE:
            mToneSettings = in.readParcelable(null);
            break;
        case SET_UP_CALL:
            mCallSettings = new CallSettings();
            mCallSettings.confirmMsg = in.readParcelable(null);
            mCallSettings.callMsg = in.readParcelable(null);
            break;
        case SET_UP_EVENT_LIST:
            mSetupEventListSettings = new SetupEventListSettings();
            int length = in.readInt();
            mSetupEventListSettings.eventList = new int[length];
            for (int i = 0; i < length; i++) {
                mSetupEventListSettings.eventList[i] = in.readInt();
            }
            break;
        /* SPRD: Add here for BIP function @{ */
        case OPEN_CHANNEL:
            mOpenChannel = in.readParcelable(null);
            break;
        case CLOSE_CHANNEL:
            mCloseChannel = in.readParcelable(null);
            break;
        case RECEIVE_DATA:
            mReceiveData = in.readParcelable(null);
            break;
        case SEND_DATA:
            mSendData = in.readParcelable(null);
            break;
        case GET_CHANNEL_STATUS:
            mChannelStatus = in.readParcelable(null);
            break;
        /* @} */
        /* SPRD: Add DTMF function. @{ */
        case SEND_DTMF:
            mDtmfMessage = in.readParcelable(null);
            break;
        /* @} */
        /* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
        case LANGUAGE_NOTIFACTION:
            mLanguageMessage = in.readParcelable(null);
            break;
        /* @}*/
        default:
            break;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mCmdDet, 0);
        dest.writeParcelable(mTextMsg, 0);
        dest.writeParcelable(mMenu, 0);
        dest.writeParcelable(mInput, 0);
        switch(getCmdType()) {
        case LAUNCH_BROWSER:
            dest.writeString(mBrowserSettings.url);
            dest.writeInt(mBrowserSettings.mode.ordinal());
            break;
        case PLAY_TONE:
            dest.writeParcelable(mToneSettings, 0);
            break;
        case SET_UP_CALL:
            dest.writeParcelable(mCallSettings.confirmMsg, 0);
            dest.writeParcelable(mCallSettings.callMsg, 0);
            break;
        case SET_UP_EVENT_LIST:
            dest.writeIntArray(mSetupEventListSettings.eventList);
            break;
        /* SPRD: Add here for BIP function @{ */
        case OPEN_CHANNEL:
            dest.writeParcelable(mOpenChannel, 0);
            break;
        case CLOSE_CHANNEL:
            dest.writeParcelable(mCloseChannel, 0);
            break;
        case RECEIVE_DATA:
            dest.writeParcelable(mReceiveData, 0);
            break;
        case SEND_DATA:
            dest.writeParcelable(mSendData, 0);
            break;
        case GET_CHANNEL_STATUS:
            dest.writeParcelable(mChannelStatus, 0);
            break;
        /* @} */
        /* SPRD: Add DTMF function. @{ */
        case SEND_DTMF:
            dest.writeParcelable(mDtmfMessage, 0);
            break;
        /* @} */
        /* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
        case LANGUAGE_NOTIFACTION:
            dest.writeParcelable(mLanguageMessage, 0);
            break;
        /* @}*/
        default:
            break;
        }
    }

    public static final Parcelable.Creator<CatCmdMessage> CREATOR = new Parcelable.Creator<CatCmdMessage>() {
        @Override
        public CatCmdMessage createFromParcel(Parcel in) {
            return new CatCmdMessage(in);
        }

        @Override
        public CatCmdMessage[] newArray(int size) {
            return new CatCmdMessage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /* external API to be used by application */
    public AppInterface.CommandType getCmdType() {
        return AppInterface.CommandType.fromInt(mCmdDet.typeOfCommand);
    }

    public Menu getMenu() {
        return mMenu;
    }

    public Input geInput() {
        return mInput;
    }

    public TextMessage geTextMessage() {
        return mTextMsg;
    }

    public BrowserSettings getBrowserSettings() {
        return mBrowserSettings;
    }

    public ToneSettings getToneSettings() {
        return mToneSettings;
    }

    public CallSettings getCallSettings() {
        return mCallSettings;
    }

    public SetupEventListSettings getSetEventList() {
        return mSetupEventListSettings;
    }

    /* SPRD: Add DTMF function. @{ */
    public DtmfMessage getDtmfMessage() {
        return mDtmfMessage;
    }
    /* @} */

    /* SPRD: Add here for Open Channel function @{ */
    public CommandDetails getCmdDet() {
        return mCmdDet;
    }

    public OpenChannelData getOpenChannelData() {
        return mOpenChannel;
    }

    public CloseChannelData getCloseChannelData() {
        return mCloseChannel;
    }

    public ReceiveChannelData getReceiveChannelData() {
        return mReceiveData;
    }

    public SendChannelData getSendChannelData() {
        return mSendData;
    }

    public GetChannelStatus getChannelStatus() {
        return mChannelStatus;
    }

    public DeviceIdentities getDeviceIdentities() {
        return mDeviceIdentities;
    }
    /* @} */
    /* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
    public LanguageMessage getLanguageMessage() {
        return mLanguageMessage;
    }
    /* @}*/
}
