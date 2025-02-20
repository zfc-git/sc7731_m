package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.CommandsInterface;

public class RilVideoEx {

    private static String TAG = "RilVideoEx";

    public static void sendVPString(CommandsInterface ci, String str, Message result) {
        if(ci == null) {
            Rlog.i(TAG, "sendVPString: ci is null ");
            return;
        }

        Rlog.i(TAG, "sendVPString: str = "+str);

        byte[] data = IccUtils.getStringRequestRawBytes(str, OEM_REQ_FUNCTION_ID_VIDEOPHONE, OEM_REQ_SUBFUNCID_VIDEOPHONE_STRING);
        Rlog.i(TAG, "sendVPString: data = "+IccUtils.bytesToHexString(data));
        ci.invokeOemRilRequestRaw(data, result);
    }


    public static void fallBackVP(CommandsInterface ci, Message result) {
        if(ci == null) {
            Rlog.i(TAG, "fallBackVP: ci is null ");
            return;
        }
        /* SPRD: add for bug 503193 @{ */
        byte[] data = IccUtils.getRequestRawBytes(
                OEM_REQ_FUNCTION_ID_VIDEOPHONE,
                OEM_REQ_SUBFUNCID_VIDEOPHONE_FALLBACK);
        /* @} */
        Rlog.i(TAG, "fallBackVP: data = "+IccUtils.bytesToHexString(data));
        ci.invokeOemRilRequestRaw(data, result);

    }

    public static void controlVPLocalMedia(CommandsInterface ci, int datatype, int sw, boolean bReplaceImg, Message result) {
        if(ci == null) {
            Rlog.i(TAG, "controlVPLocalMedia: ci is null ");
            return;
        }

        Rlog.i(TAG, "controlVPLocalMedia: datatype = "+datatype+" sw = "+sw+" bReplaceImg = "+bReplaceImg );
        int[] ints = new int[3];
        ints[0] = datatype;
        ints[1] = sw;
        ints[2] = RilVideoEx.booleanToInt(bReplaceImg);
        byte[] data = IccUtils.getIntRequestRawBytes(ints, OEM_REQ_FUNCTION_ID_VIDEOPHONE, OEM_REQ_SUBFUNCID_VIDEOPHONE_LOCAL_MEDIA);
        Rlog.i(TAG, "controlVPLocalMedia: data = "+IccUtils.bytesToHexString(data));
        ci.invokeOemRilRequestRaw(data, result);
    }

    public static void controlIFrame(CommandsInterface ci, boolean isIFrame, boolean needIFrame, Message result) {
        if(ci == null) {
            Rlog.i(TAG, "controlIFrame: ci is null ");
            return;
        }

        Rlog.i(TAG, "controlIFrame: isIFrame = "+isIFrame+" needIFrame = "+needIFrame);
        int[] ints = new int[2];
        ints[0] = RilVideoEx.booleanToInt(isIFrame);
        ints[1] = RilVideoEx.booleanToInt(needIFrame);
        byte[] data = IccUtils.getIntRequestRawBytes(ints, OEM_REQ_FUNCTION_ID_VIDEOPHONE, OEM_REQ_SUBFUNCID_VIDEOPHONE_CONTROL_IFRAME);
        Rlog.i(TAG, "controlIFrame: data = "+IccUtils.bytesToHexString(data));
        ci.invokeOemRilRequestRaw(data, result);

    }

    public static void controlVPCamera (CommandsInterface ci, boolean bEnable, Message result){
        if(ci == null) {
            Rlog.i(TAG, "controlVPCamera: ci is null ");
            return;
        }

        Rlog.i(TAG, "controlVPCamera: bEnable = "+ bEnable);
        RilVideoEx.sendVPString(ci, bEnable ? "open_:camera_":"close_:camera_", result);
    }

    public static void controlVPAudio(CommandsInterface ci, boolean bEnable, Message result) {
        if(ci == null) {
            Rlog.i(TAG, "controlVPAudio: ci is null ");
            return;
        }

        Rlog.i(TAG, "controlVPAudio: bEnable = "+bEnable);

        int[] ints = new int[1];
        ints[0] = RilVideoEx.booleanToInt(bEnable);
        byte[] data = IccUtils.getIntRequestRawBytes(ints, OEM_REQ_FUNCTION_ID_VIDEOPHONE, OEM_REQ_SUBFUNCID_VIDEOPHONE_CONTROL_VP_AUDIO);
        Rlog.i(TAG, "controlVPAudio: rawBytes = "+IccUtils.bytesToHexString(data));
        ci.invokeOemRilRequestRaw(data, result);

    }

    public static void requestVolteCallMediaChange(CommandsInterface ci, boolean isVideo, Message response) {
        if(ci == null) {
            Rlog.i(TAG, "requestVolteCallMediaChange: ci is null ");
            return;
        }

        Rlog.i(TAG, "requestVolteCallMediaChange: isVideo = "+isVideo);
        int[] ints = new int[1];
//        ints[0] = RilVideoEx.booleanToInt(isVideo);
//        byte[] data = IccUtils.getIntRequestRawBytes(ints, OEM_REQ_FUNCTION_ID_VIDEOPHONE, OEM_REQ_SUBFUNCID_VIDEOPHONE_LOCAL_MEDIA);
//        Rlog.i(TAG, "requestVolteCallMediaChange: data = "+IccUtils.bytesToHexString(data));
//        ci.invokeOemRilRequestRaw(data, result);
    }

    public static int booleanToInt(boolean res) {
        return res ? 1: 0;
    }
}
