/*
 * Copyright (C) 2006-2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.internal.telephony.cat;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import java.util.Calendar;
import java.util.TimeZone;
import android.os.SystemProperties;
import android.text.TextUtils;

import com.android.internal.telephony.cat.AppInterface.CommandType;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
/* SPRD: Add here for BIP function @{ */
import com.android.internal.telephony.IccUtils;
/* @} */

abstract class ResponseData {
    /**
     * Format the data appropriate for TERMINAL RESPONSE and write it into
     * the ByteArrayOutputStream object.
     */
    public abstract void format(ByteArrayOutputStream buf);

    public static void writeLength(ByteArrayOutputStream buf, int length) {
        // As per ETSI 102.220 Sec7.1.2, if the total length is greater
        // than 0x7F, it should be coded in two bytes and the first byte
        // should be 0x81.
        if (length > 0x7F) {
            buf.write(0x81);
        }
        buf.write(length);
    }
}

class SelectItemResponseData extends ResponseData {
    // members
    private int mId;

    public SelectItemResponseData(int id) {
        super();
        mId = id;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        // Item identifier object
        int tag = 0x80 | ComprehensionTlvTag.ITEM_ID.value();
        buf.write(tag); // tag
        buf.write(1); // length
        buf.write(mId); // identifier of item chosen
    }
}

class GetInkeyInputResponseData extends ResponseData {
    // members
    private boolean mIsUcs2;
    private boolean mIsPacked;
    private boolean mIsYesNo;
    private boolean mYesNoResponse;
    public String mInData;

    // GetInKey Yes/No response characters constants.
    protected static final byte GET_INKEY_YES = 0x01;
    protected static final byte GET_INKEY_NO = 0x00;

    public GetInkeyInputResponseData(String inData, boolean ucs2, boolean packed) {
        super();
        mIsUcs2 = ucs2;
        mIsPacked = packed;
        mInData = inData;
        mIsYesNo = false;
    }

    public GetInkeyInputResponseData(boolean yesNoResponse) {
        super();
        mIsUcs2 = false;
        mIsPacked = false;
        mInData = "";
        mIsYesNo = true;
        mYesNoResponse = yesNoResponse;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // Text string object
        int tag = 0x80 | ComprehensionTlvTag.TEXT_STRING.value();
        buf.write(tag); // tag

        byte[] data;

        if (mIsYesNo) {
            data = new byte[1];
            data[0] = mYesNoResponse ? GET_INKEY_YES : GET_INKEY_NO;
        } else if (mInData != null && mInData.length() > 0) {
            try {
                // ETSI TS 102 223 8.15, should use the same format as in SMS messages
                // on the network.
                if (mIsUcs2) {
                    // ucs2 is by definition big endian.
                    data = mInData.getBytes("UTF-16BE");
                } else if (mIsPacked) {
                    int size = mInData.length();

                    byte[] tempData = GsmAlphabet
                            .stringToGsm7BitPacked(mInData, 0, 0);
                    data = new byte[size];
                    // Since stringToGsm7BitPacked() set byte 0 in the
                    // returned byte array to the count of septets used...
                    // copy to a new array without byte 0.
                    System.arraycopy(tempData, 1, data, 0, size);
                } else {
                    data = GsmAlphabet.stringToGsm8BitPacked(mInData);
                }
            } catch (UnsupportedEncodingException e) {
                data = new byte[0];
            } catch (EncodeException e) {
                data = new byte[0];
            }
        } else {
            data = new byte[0];
        }

        // length - one more for data coding scheme.
        writeLength(buf, data.length + 1);

        // data coding scheme
        if (mIsUcs2) {
            buf.write(0x08); // UCS2
        } else if (mIsPacked) {
            buf.write(0x00); // 7 bit packed
        } else {
            buf.write(0x04); // 8 bit unpacked
        }

        for (byte b : data) {
            buf.write(b);
        }
    }
}

// For "PROVIDE LOCAL INFORMATION" command.
// See TS 31.111 section 6.4.15/ETSI TS 102 223
// TS 31.124 section 27.22.4.15 for test spec
class LanguageResponseData extends ResponseData {
    private String mLang;

    public LanguageResponseData(String lang) {
        super();
        mLang = lang;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // Text string object
        int tag = 0x80 | ComprehensionTlvTag.LANGUAGE.value();
        buf.write(tag); // tag

        byte[] data;

        if (mLang != null && mLang.length() > 0) {
            data = GsmAlphabet.stringToGsm8BitPacked(mLang);
        }
        else {
            data = new byte[0];
        }

        buf.write(data.length);

        for (byte b : data) {
            buf.write(b);
        }
    }
}

// For "PROVIDE LOCAL INFORMATION" command.
// See TS 31.111 section 6.4.15/ETSI TS 102 223
// TS 31.124 section 27.22.4.15 for test spec
class DTTZResponseData extends ResponseData {
    private Calendar mCalendar;

    public DTTZResponseData(Calendar cal) {
        super();
        mCalendar = cal;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            return;
        }

        // DTTZ object
        int tag = 0x80 | CommandType.PROVIDE_LOCAL_INFORMATION.value();
        buf.write(tag); // tag

        byte[] data = new byte[8];

        data[0] = 0x07; // Write length of DTTZ data

        if (mCalendar == null) {
            mCalendar = Calendar.getInstance();
        }
        // Fill year byte
        data[1] = byteToBCD(mCalendar.get(java.util.Calendar.YEAR) % 100);

        // Fill month byte
        data[2] = byteToBCD(mCalendar.get(java.util.Calendar.MONTH) + 1);

        // Fill day byte
        data[3] = byteToBCD(mCalendar.get(java.util.Calendar.DATE));

        // Fill hour byte
        data[4] = byteToBCD(mCalendar.get(java.util.Calendar.HOUR_OF_DAY));

        // Fill minute byte
        data[5] = byteToBCD(mCalendar.get(java.util.Calendar.MINUTE));

        // Fill second byte
        data[6] = byteToBCD(mCalendar.get(java.util.Calendar.SECOND));

        String tz = SystemProperties.get("persist.sys.timezone", "");
        if (TextUtils.isEmpty(tz)) {
            data[7] = (byte) 0xFF;    // set FF in terminal response
        } else {
            TimeZone zone = TimeZone.getTimeZone(tz);
            int zoneOffset = zone.getRawOffset() + zone.getDSTSavings();
            data[7] = getTZOffSetByte(zoneOffset);
        }

        for (byte b : data) {
            buf.write(b);
        }
    }

    private byte byteToBCD(int value) {
        if (value < 0 && value > 99) {
            CatLog.d(this, "Err: byteToBCD conversion Value is " + value +
                           " Value has to be between 0 and 99");
            return 0;
        }

        return (byte) ((value / 10) | ((value % 10) << 4));
    }

    private byte getTZOffSetByte(long offSetVal) {
        boolean isNegative = (offSetVal < 0);

        /*
         * The 'offSetVal' is in milliseconds. Convert it to hours and compute
         * offset While sending T.R to UICC, offset is expressed is 'quarters of
         * hours'
         */

         long tzOffset = offSetVal / (15 * 60 * 1000);
         tzOffset = (isNegative ? -1 : 1) * tzOffset;
         byte bcdVal = byteToBCD((int) tzOffset);
         // For negative offsets, put '1' in the msb
         return isNegative ?  (bcdVal |= 0x08) : bcdVal;
    }

}

/* SPRD: Add here for BIP function @{ */
class OpenChannelResponseData extends ResponseData {
    private byte mBearerType = 0;
    private String mBearerParam = null;
    private int mBufferSize = 0;
    private int mChannelId = 0;
    private boolean mLinkStatus = false;


    public OpenChannelResponseData(byte type, String param, int size, int id, boolean status) {
        super();
        this.mBearerType = type;
        this.mBearerParam = param;
        this.mBufferSize = size;
        this.mChannelId = id;
        this.mLinkStatus = status;
}

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            CatLog.d(this, "OpenChannelResponseData buf is null");
            return;
        }
        int tag;
        // Channel status object
        CatLog.d(this, "[stk] ChannelStatusResponseData mLinkStatus = " + mLinkStatus);
        if (mLinkStatus) {
            //tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
            tag = ComprehensionTlvTag.CHANNEL_STATUS.value();
            buf.write(tag);
            // length
            buf.write(2);
            // channel id & link status
            buf.write(mChannelId | 0x80);
            // channel status
            buf.write(0x00);
        }
        // Bearer Description object
        //tag = 0x80 | ComprehensionTlvTag.BEARER_DESCRIPTION.value();
        tag = ComprehensionTlvTag.BEARER_DESCRIPTION.value();
        buf.write(tag); // tag

        byte[] data = null;
        if (mBearerParam != null && mBearerParam.length() > 0) {
            //31.111 8.52.5 Bearer parameters for E-UTRAN / mapped UTRAN packet service
            //begin  data = IccUtils.hexStringToBytes(mBearerParam);
            String tempBearerParam = mBearerParam;
            if(tempBearerParam.startsWith("09")){
                tempBearerParam = "09"+tempBearerParam.substring(mBearerParam.length()-2,mBearerParam.length());
            }
            CatLog.d(this, "[stk] tempBearerParam  = " + tempBearerParam);
            data = IccUtils.hexStringToBytes(tempBearerParam);
            //end 31.111 8.52.5 Bearer parameters for E-UTRAN / mapped UTRAN packet service
        } else {
            data = new byte[0];
        }
        buf.write(data.length + 1); // length
        buf.write(mBearerType);     // Bearer Type
        for (byte b : data) {       // Bearer param
            buf.write(b);
        }
        // Buffer Size object
        //tag = 0x80 | ComprehensionTlvTag.BUFFER_SIZE.value();
        tag = ComprehensionTlvTag.BUFFER_SIZE.value();
        buf.write(tag);
        // length
        buf.write(2);
        // Buffer Size
        buf.write((mBufferSize & 0xff00) >> 8);
        buf.write(mBufferSize & 0x00ff);
    }
}

class ChannelStatusResponseData extends ResponseData {
    private int mChannelId = 0;
    private boolean mLinkStatus = false;


    public ChannelStatusResponseData(int id, boolean status) {
        super();
        this.mChannelId = id;
        this.mLinkStatus = status;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            CatLog.d(this, "ChannelStatusResponseData buf is null");
            return;
        }
        int tag;
        // Channel status object
        CatLog.d(this, "[stk] ChannelStatusResponseData mLinkStatus = " + mLinkStatus);
        tag = 0x80 | ComprehensionTlvTag.CHANNEL_STATUS.value();
        buf.write(tag);
        // length
        buf.write(2);
        // channel id & link status
        buf.write(mChannelId | (mLinkStatus ? 0x80 : 0));
        // channel status
        buf.write(0x00);
    }
}

class SendDataResponseData extends ResponseData {
    private int mChannelLen = 0;

    public SendDataResponseData(int len) {
        super();
        this.mChannelLen = len;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            CatLog.d(this, "SendDataResponseData buf is null");
            return;
        }
        int tag;
        // Channel data length object
        CatLog.d(this, "[stk] SendDataResponseData mChannelLen = " + mChannelLen);
        tag = 0x80 | ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value();
        buf.write(tag);
        // length
        buf.write(1);
        // channel data length
        buf.write(mChannelLen);
    }
}

class ReceiveDataResponseData extends ResponseData {
    private int mDataLen = 0;
    private String mDataStr = null;

    public ReceiveDataResponseData(int len, String str) {
        super();
        this.mDataLen = len;
        this.mDataStr = str;
    }

    @Override
    public void format(ByteArrayOutputStream buf) {
        if (buf == null) {
            CatLog.d(this, "ReceiveDataResponseData buf is null");
            return;
        }
        int tag;
        // Channel data object
        CatLog.d(this, "[stk] ReceiveDataResponseData mDataLen = " + mDataLen +
                        " mDataStr = " + mDataStr);
        tag = 0x80 | ComprehensionTlvTag.CHANNEL_DATA.value();
        buf.write(tag);
        // length
        byte[] data = null;
        if (mDataStr != null && mDataStr.length() > 0) {
            data = IccUtils.hexStringToBytes(mDataStr);
        } else {
            data = new byte[0];
        }
        if (data.length < 0x80) {
            buf.write(data.length);
        } else {
            buf.write(0x81);
            buf.write(data.length);
        }
        // channel data
        for (byte b : data) {
            buf.write(b);
        }
        // Channel data length object
        tag = 0x80 | ComprehensionTlvTag.CHANNEL_DATA_LENGTH.value();
        buf.write(tag);
        // length
        buf.write(1);
        // channel data length
        buf.write(mDataLen);
    }
}
/* @} */
