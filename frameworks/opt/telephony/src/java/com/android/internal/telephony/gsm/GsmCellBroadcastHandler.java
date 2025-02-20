/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.SystemProperties;
import android.telephony.CellLocation;
import android.telephony.SmsCbLocation;
import android.telephony.SmsCbMessage;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.CellBroadcastHandler;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.TelephonyProperties;
import com.android.internal.telephony.uicc.IccUtils;
import com.android.internal.telephony.BaseCommands.PWSMessage;

import java.util.HashMap;
import java.util.Iterator;

import android.Manifest;
import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SubscriptionManager;

/**
 * Handler for 3GPP format Cell Broadcasts. Parent class can also handle CDMA Cell Broadcasts.
 */
public class GsmCellBroadcastHandler extends CellBroadcastHandler {
    private static final boolean VDBG = true;  // log CB PDU data

    private final HashMap<SmsCbConcatInfoLte, byte[][]> mSmsCbPageMapLte =
                    new HashMap<SmsCbConcatInfoLte, byte[][]>(4);

    /** This map holds incomplete concatenated messages waiting for assembly. */
    private final HashMap<SmsCbConcatInfo, byte[][]> mSmsCbPageMap =
            new HashMap<SmsCbConcatInfo, byte[][]>(4);

    protected GsmCellBroadcastHandler(Context context, PhoneBase phone) {
        super("GsmCellBroadcastHandler", context, phone);
        phone.mCi.setOnNewGsmBroadcastSms(getHandler(), EVENT_NEW_SMS_MESSAGE, null);
        phone.mCi.setOnPWSMessage(getHandler(), EVENT_NEW_SMS_MESSAGE_LTE, null);
    }

    @Override
    protected void onQuitting() {
        mPhone.mCi.unSetOnNewGsmBroadcastSms(getHandler());
        super.onQuitting();     // release wakelock
    }

    /**
     * Create a new CellBroadcastHandler.
     * @param context the context to use for dispatching Intents
     * @return the new handler
     */
    public static GsmCellBroadcastHandler makeGsmCellBroadcastHandler(Context context,
            PhoneBase phone) {
        GsmCellBroadcastHandler handler = new GsmCellBroadcastHandler(context, phone);
        handler.start();
        return handler;
    }

    /**
     * Handle 3GPP-format Cell Broadcast messages sent from radio.
     *
     * @param message the message to process
     * @return true if an ordered broadcast was sent; false on failure
     */
    @Override
    protected boolean handleSmsMessage(Message message) {
        if (message.obj instanceof AsyncResult) {
            SmsCbMessage cbMessage = handleGsmBroadcastSms((AsyncResult) message.obj);
            if (cbMessage != null) {
                handleBroadcastSms(cbMessage);
                return true;
            }
        }
        return super.handleSmsMessage(message);
    }

    /**
     * Dispatch a Cell Broadcast message to listeners.
     * @param message the Cell Broadcast to broadcast
     */
    protected void handleBroadcastSms(SmsCbMessage message) {
        String receiverPermission;
        int appOp;
        Intent intent;
        if (message.isEmergencyMessage()) {
            log("Dispatching emergency SMS CB");
            intent = new Intent(Telephony.Sms.Intents.SMS_EMERGENCY_CB_RECEIVED_ACTION);
            receiverPermission = Manifest.permission.RECEIVE_EMERGENCY_BROADCAST;
            appOp = AppOpsManager.OP_RECEIVE_EMERGECY_SMS;
        } else {
            log("Dispatching SMS CB");
            intent = new Intent(Telephony.Sms.Intents.SMS_CB_RECEIVED_ACTION);
            receiverPermission = Manifest.permission.RECEIVE_SMS;
            appOp = AppOpsManager.OP_RECEIVE_SMS;
        }
        intent.putExtra("message", message);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, mPhone.getPhoneId());
        mContext.sendOrderedBroadcast(intent, receiverPermission, appOp, mReceiver,
                getHandler(), Activity.RESULT_OK, null, null);
    }

    protected boolean handleSmsMessageLte(Message message){
        if (message.obj instanceof AsyncResult) {
            log("handleSmsMessageLte");
            SmsCbMessage cbMessage = handleGsmBroadcastSmsLte((AsyncResult) message.obj);
            if (cbMessage != null) {
                log("cbMessage = " + cbMessage);
                handleBroadcastSms(cbMessage);
                return true;
            }
        }
        return handleSmsMessage(message);
    }

    private SmsCbMessage handleGsmBroadcastSmsLte(AsyncResult ar){
        //get the cb information from RIL
        PWSMessage cb = (PWSMessage)ar.result;
        int segmentId = cb.segmentId;
        int totalSegment = cb.totalSegments;

        int length = cb.length;
        byte[] receivedPdu = IccUtils.hexStringToBytes(cb.data);
        int serialNumber = IccUtils.bytesToInt(IccUtils.hexStringToBytes(cb.data.substring(0, 4)));
        int messageIdentifier = IccUtils.bytesToInt(IccUtils.hexStringToBytes(cb.data.substring(4, 8)));
        int dcs = IccUtils.bytesToInt(IccUtils.hexStringToBytes(cb.data.substring(8, 10)));
        byte[] scope = IccUtils.intToBytes(serialNumber, 2);
        int geographicalScope = IccUtils.bytesToInt(scope);
        log("get cb message from RIL ,  segmentId:" + segmentId + ",  totalSegment:" + totalSegment
                + ",  serialNumber:" + serialNumber + ",  messageIdentifier:" + messageIdentifier + ",  dcs:" + dcs
                + ",  length:" + length + ",  receivedPdu:" + cb.data);
        //construct cb message
        String plmn = SystemProperties.get(TelephonyProperties.PROPERTY_OPERATOR_NUMERIC);
        int lac = -1;
        int cid = -1;
        CellLocation cl = mPhone.getCellLocation();
        // Check if cell location is GsmCellLocation.  This is required to support
        // dual-mode devices such as CDMA/LTE devices that require support for
        // both 3GPP and 3GPP2 format messages
        if (cl instanceof GsmCellLocation) {
            GsmCellLocation cellLocation = (GsmCellLocation)cl;
            lac = cellLocation.getLac();
            cid = cellLocation.getCid();
        }

        SmsCbLocation location;
        switch (geographicalScope) {
            case SmsCbMessage.GEOGRAPHICAL_SCOPE_LA_WIDE:
                location = new SmsCbLocation(plmn, lac, -1);
                break;

            case SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE:
            case SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE:
                location = new SmsCbLocation(plmn, lac, cid);
                break;

            case SmsCbMessage.GEOGRAPHICAL_SCOPE_PLMN_WIDE:
            default:
                location = new SmsCbLocation(plmn);
                break;
        }
        byte[][] pdus;
        if (totalSegment > 1) {
            // Multi-page message
            SmsCbConcatInfoLte concatInfo = new SmsCbConcatInfoLte(serialNumber, location);

            // Try to find other pages of the same message
            pdus = mSmsCbPageMapLte.get(concatInfo);

            if (pdus == null) {
                // This is the first page of this message, make room for all
                // pages and keep until complete
                pdus = new byte[totalSegment][];

                mSmsCbPageMapLte.put(concatInfo, pdus);
            }

            // Page parameter is one-based
            pdus[segmentId - 1] = receivedPdu;

            for (byte[] pdu : pdus) {
                if (pdu == null) {
                    // Still missing pages, exit
                    return null;
                }
            }

            // Message complete, remove and dispatch
            mSmsCbPageMapLte.remove(concatInfo);
        } else {
            // Single page message
            pdus = new byte[1][];
            pdus[0] = receivedPdu;
        }

        // Remove messages that are out of scope to prevent the map from
        // growing indefinitely, containing incomplete messages that were
        // never assembled
        Iterator<SmsCbConcatInfoLte> iter = mSmsCbPageMapLte.keySet().iterator();

        while (iter.hasNext()) {
            SmsCbConcatInfoLte info = iter.next();

            if (!info.matchesLocation(plmn, lac, cid)) {
                iter.remove();
            }
        }
        return GsmSmsCbMessage.createSmsCbMessage(dcs, geographicalScope, serialNumber,
                messageIdentifier, location, pdus, mPhone.getSubId());
    }

    /**
     * Handle 3GPP format SMS-CB message.
     * @param ar the AsyncResult containing the received PDUs
     */
    private SmsCbMessage handleGsmBroadcastSms(AsyncResult ar) {
        try {
            byte[] receivedPdu = (byte[]) ar.result;

            if (VDBG) {
                int pduLength = receivedPdu.length;
                for (int i = 0; i < pduLength; i += 8) {
                    StringBuilder sb = new StringBuilder("SMS CB pdu data: ");
                    for (int j = i; j < i + 8 && j < pduLength; j++) {
                        int b = receivedPdu[j] & 0xff;
                        if (b < 0x10) {
                            sb.append('0');
                        }
                        sb.append(Integer.toHexString(b)).append(' ');
                    }
                    log(sb.toString());
                }
            }

            SmsCbHeader header = new SmsCbHeader(receivedPdu);
            String plmn = TelephonyManager.from(mContext).getNetworkOperatorForPhone(
                    mPhone.getPhoneId());
            int lac = -1;
            int cid = -1;
            CellLocation cl = mPhone.getCellLocation();
            // Check if cell location is GsmCellLocation.  This is required to support
            // dual-mode devices such as CDMA/LTE devices that require support for
            // both 3GPP and 3GPP2 format messages
            if (cl instanceof GsmCellLocation) {
                GsmCellLocation cellLocation = (GsmCellLocation)cl;
                lac = cellLocation.getLac();
                cid = cellLocation.getCid();
            }

            SmsCbLocation location;
            switch (header.getGeographicalScope()) {
                case SmsCbMessage.GEOGRAPHICAL_SCOPE_LA_WIDE:
                    location = new SmsCbLocation(plmn, lac, -1);
                    break;

                case SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE:
                case SmsCbMessage.GEOGRAPHICAL_SCOPE_CELL_WIDE_IMMEDIATE:
                    location = new SmsCbLocation(plmn, lac, cid);
                    break;

                case SmsCbMessage.GEOGRAPHICAL_SCOPE_PLMN_WIDE:
                default:
                    location = new SmsCbLocation(plmn);
                    break;
            }

            byte[][] pdus;
            int pageCount = header.getNumberOfPages();
            if (pageCount > 1) {
                // Multi-page message
                SmsCbConcatInfo concatInfo = new SmsCbConcatInfo(header, location);

                // Try to find other pages of the same message
                pdus = mSmsCbPageMap.get(concatInfo);

                if (pdus == null) {
                    // This is the first page of this message, make room for all
                    // pages and keep until complete
                    pdus = new byte[pageCount][];

                    mSmsCbPageMap.put(concatInfo, pdus);
                }

                // Page parameter is one-based
                pdus[header.getPageIndex() - 1] = receivedPdu;

                for (byte[] pdu : pdus) {
                    if (pdu == null) {
                        // Still missing pages, exit
                        return null;
                    }
                }

                // Message complete, remove and dispatch
                mSmsCbPageMap.remove(concatInfo);
            } else {
                // Single page message
                pdus = new byte[1][];
                pdus[0] = receivedPdu;
            }

            // Remove messages that are out of scope to prevent the map from
            // growing indefinitely, containing incomplete messages that were
            // never assembled
            Iterator<SmsCbConcatInfo> iter = mSmsCbPageMap.keySet().iterator();

            while (iter.hasNext()) {
                SmsCbConcatInfo info = iter.next();

                if (!info.matchesLocation(plmn, lac, cid)) {
                    iter.remove();
                }
            }

            //add sub_id for App bug 489257
            return GsmSmsCbMessage.createSmsCbMessage(header, location, pdus, mPhone.getSubId());

        } catch (RuntimeException e) {
            loge("Error in decoding SMS CB pdu", e);
            return null;
        }
    }

    /**
     * Holds all info about a message page needed to assemble a complete concatenated message.
     */
    private static final class SmsCbConcatInfo {

        private final SmsCbHeader mHeader;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfo(SmsCbHeader header, SmsCbLocation location) {
            mHeader = header;
            mLocation = location;
        }

        @Override
        public int hashCode() {
            return (mHeader.getSerialNumber() * 31) + mLocation.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SmsCbConcatInfo) {
                SmsCbConcatInfo other = (SmsCbConcatInfo)obj;

                // Two pages match if they have the same serial number (which includes the
                // geographical scope and update number), and both pages belong to the same
                // location (PLMN, plus LAC and CID if these are part of the geographical scope).
                return mHeader.getSerialNumber() == other.mHeader.getSerialNumber()
                        && mLocation.equals(other.mLocation);
            }

            return false;
        }

        /**
         * Compare the location code for this message to the current location code. The match is
         * relative to the geographical scope of the message, which determines whether the LAC
         * and Cell ID are saved in mLocation or set to -1 to match all values.
         *
         * @param plmn the current PLMN
         * @param lac the current Location Area (GSM) or Service Area (UMTS)
         * @param cid the current Cell ID
         * @return true if this message is valid for the current location; false otherwise
         */
        public boolean matchesLocation(String plmn, int lac, int cid) {
            return mLocation.isInLocationArea(plmn, lac, cid);
        }
    }

    private static final class SmsCbConcatInfoLte {

        private final int mSerialNumber;
        private final SmsCbLocation mLocation;

        SmsCbConcatInfoLte(int serialNumber, SmsCbLocation location) {
            mSerialNumber = serialNumber;
            mLocation = location;
        }

        @Override
        public int hashCode() {
            return (mSerialNumber * 31) + mLocation.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SmsCbConcatInfoLte) {
                SmsCbConcatInfoLte other = (SmsCbConcatInfoLte)obj;

                // Two pages match if they have the same serial number (which includes the
                // geographical scope and update number), and both pages belong to the same
                // location (PLMN, plus LAC and CID if these are part of the geographical scope).
                return mSerialNumber == other.mSerialNumber
                        && mLocation.equals(other.mLocation);
            }

            return false;
        }

        /**
         * Compare the location code for this message to the current location code. The match is
         * relative to the geographical scope of the message, which determines whether the LAC
         * and Cell ID are saved in mLocation or set to -1 to match all values.
         *
         * @param plmn the current PLMN
         * @param lac the current Location Area (GSM) or Service Area (UMTS)
         * @param cid the current Cell ID
         * @return true if this message is valid for the current location; false otherwise
         */
        public boolean matchesLocation(String plmn, int lac, int cid) {
            return mLocation.isInLocationArea(plmn, lac, cid);
        }
    }
}
