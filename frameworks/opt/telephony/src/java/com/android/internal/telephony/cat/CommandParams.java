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

import android.graphics.Bitmap;
/* SPRD: Add here for BIP function @{ */
import com.android.internal.telephony.cat.bip.*;
/* @{ */

/**
 * Container class for proactive command parameters.
 *
 */
class CommandParams {
    CommandDetails mCmdDet;

    CommandParams(CommandDetails cmdDet) {
        mCmdDet = cmdDet;
    }

    AppInterface.CommandType getCommandType() {
        return AppInterface.CommandType.fromInt(mCmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) { return true; }

    @Override
    public String toString() {
        return mCmdDet.toString();
    }
}

class DisplayTextParams extends CommandParams {
    TextMessage mTextMsg;

    DisplayTextParams(CommandDetails cmdDet, TextMessage textMsg) {
        super(cmdDet);
        mTextMsg = textMsg;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mTextMsg != null) {
            mTextMsg.icon = icon;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TextMessage=" + mTextMsg + " " + super.toString();
    }
}

class LaunchBrowserParams extends CommandParams {
    TextMessage mConfirmMsg;
    LaunchBrowserMode mMode;
    String mUrl;

    LaunchBrowserParams(CommandDetails cmdDet, TextMessage confirmMsg,
            String url, LaunchBrowserMode mode) {
        super(cmdDet);
        mConfirmMsg = confirmMsg;
        mMode = mode;
        mUrl = url;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mConfirmMsg != null) {
            mConfirmMsg.icon = icon;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "TextMessage=" + mConfirmMsg + " " + super.toString();
    }
}

class SetEventListParams extends CommandParams {
    int[] mEventInfo;
    SetEventListParams(CommandDetails cmdDet, int[] eventInfo) {
        super(cmdDet);
        this.mEventInfo = eventInfo;
    }
}

class PlayToneParams extends CommandParams {
    TextMessage mTextMsg;
    ToneSettings mSettings;

    PlayToneParams(CommandDetails cmdDet, TextMessage textMsg,
            Tone tone, Duration duration, boolean vibrate) {
        super(cmdDet);
        mTextMsg = textMsg;
        mSettings = new ToneSettings(duration, tone, vibrate);
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mTextMsg != null) {
            mTextMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class CallSetupParams extends CommandParams {
    TextMessage mConfirmMsg;
    TextMessage mCallMsg;

    CallSetupParams(CommandDetails cmdDet, TextMessage confirmMsg,
            TextMessage callMsg) {
        super(cmdDet);
        mConfirmMsg = confirmMsg;
        mCallMsg = callMsg;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon == null) {
            return false;
        }
        if (mConfirmMsg != null && mConfirmMsg.icon == null) {
            mConfirmMsg.icon = icon;
            return true;
        } else if (mCallMsg != null && mCallMsg.icon == null) {
            mCallMsg.icon = icon;
            return true;
        }
        return false;
    }
}

class SelectItemParams extends CommandParams {
    Menu mMenu = null;
    boolean mLoadTitleIcon = false;

    SelectItemParams(CommandDetails cmdDet, Menu menu, boolean loadTitleIcon) {
        super(cmdDet);
        mMenu = menu;
        mLoadTitleIcon = loadTitleIcon;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mMenu != null) {
            if (mLoadTitleIcon && mMenu.titleIcon == null) {
                mMenu.titleIcon = icon;
            } else {
                for (Item item : mMenu.items) {
                    if (item.icon != null) {
                        continue;
                    }
                    item.icon = icon;
                    break;
                }
            }
            return true;
        }
        return false;
    }
}

class GetInputParams extends CommandParams {
    Input mInput = null;

    GetInputParams(CommandDetails cmdDet, Input input) {
        super(cmdDet);
        mInput = input;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mInput != null) {
            mInput.icon = icon;
        }
        return true;
    }
}

/* SPRD: Add DTMF function. @{ */
class DtmfParams extends CommandParams {
    TextMessage textMsg;
    String dtmfString;

    DtmfParams(CommandDetails cmdDet, TextMessage textMsg, String dtmf) {
        super(cmdDet);
        this.textMsg = textMsg;
        dtmfString = dtmf;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && textMsg != null) {
            textMsg.icon = icon;
            return true;
        }
        return false;
    }
}

/* @} */

/*
 * BIP (Bearer Independent Protocol) is the mechanism for SIM card applications
 * to access data connection through the mobile device.
 *
 * SIM utilizes proactive commands (OPEN CHANNEL, CLOSE CHANNEL, SEND DATA and
 * RECEIVE DATA to control/read/write data for BIP. Refer to ETSI TS 102 223 for
 * the details of proactive commands procedures and their structures.
 */
class BIPClientParams extends CommandParams {
    TextMessage mTextMsg;
    boolean mHasAlphaId;

    BIPClientParams(CommandDetails cmdDet, TextMessage textMsg, boolean has_alpha_id) {
        super(cmdDet);
        mTextMsg = textMsg;
        mHasAlphaId = has_alpha_id;
    }

    @Override
    boolean setIcon(Bitmap icon) {
        if (icon != null && mTextMsg != null) {
            mTextMsg.icon = icon;
            return true;
        }
        return false;
    }
}

/* SPRD: Add here for BIP function @{ */
class OpenChannelDataParams extends CommandParams {
    OpenChannelData openchanneldata;

    OpenChannelDataParams(CommandDetails cmdDet, OpenChannelData opendata) {
        super(cmdDet);
        openchanneldata = opendata;
        openchanneldata.setChannelType(cmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && openchanneldata != null) {
            openchanneldata.icon = icon;
            return true;
        }
        return false;
    }
}

class CloseChannelDataParams extends CommandParams {
    CloseChannelData closechanneldata;
    DeviceIdentities deviceIdentities;

    CloseChannelDataParams(CommandDetails cmdDet, CloseChannelData closedata,
            DeviceIdentities identities) {
        super(cmdDet);
        closechanneldata = closedata;
        closechanneldata.setChannelType(cmdDet.typeOfCommand);
        deviceIdentities = identities;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && closechanneldata != null) {
            closechanneldata.icon = icon;
            return true;
        }
        return false;
    }
}

class ReceiveChannelDataParams extends CommandParams {
    ReceiveChannelData receivedata;

    ReceiveChannelDataParams(CommandDetails cmdDet, ReceiveChannelData rdata) {
        super(cmdDet);
        receivedata = rdata;
        receivedata.setChannelType(cmdDet.typeOfCommand);
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && receivedata != null) {
            receivedata.icon = icon;
            return true;
        }
        return false;
    }
}

class SendChannelDataParams extends CommandParams {
    SendChannelData senddata;
    DeviceIdentities deviceIdentities;

    SendChannelDataParams(CommandDetails cmdDet, SendChannelData sdata, DeviceIdentities identities) {
        super(cmdDet);
        senddata = sdata;
        senddata.setChannelType(cmdDet.typeOfCommand);
        deviceIdentities = identities;
    }

    boolean setIcon(Bitmap icon) {
        if (icon != null && senddata != null) {
            senddata.icon = icon;
            return true;
        }
        return false;
    }
}

class GetChannelStatusParams extends CommandParams {
    GetChannelStatus channelstatus;

    GetChannelStatusParams(CommandDetails cmdDet, GetChannelStatus status) {
        super(cmdDet);
        channelstatus = status;
        channelstatus.setChannelType(cmdDet.typeOfCommand);
    }
}
/* @} */
/* SPRD: add for USAT 27.22.4.25 LANGUAGE NOTIFICATION  @{ */
class LanguageParams extends CommandParams{

    String languageString;

    LanguageParams(CommandDetails cmdDet,String language) {
        super(cmdDet);
        languageString = language;
    }

}
/* @}*/
