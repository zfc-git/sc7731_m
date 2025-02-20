package com.android.internal.telephony.policy;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.PhoneProxy;
import android.util.Log;

public class RadioInteraction {
    private static final int MSG_POWER_OFF_RADIO = 1;
    private static final int MSG_POWER_OFF_ICC = 2;
    private static final int MSG_POWER_ON_RADIO = 3;

    private TelephonyManager mTelephonyManager;
    private int mPhoneId;

    private volatile Looper mMsgLooper;
    private volatile MessageHandler mMsgHandler;
    private Context mContext;
    private PhoneProxy mPhone;

    private Runnable mRunnable;
    private final static String TAG = "RadioInteraction";

    public RadioInteraction(Context context, int phoneId) {
        mPhoneId = phoneId;
        mContext = context;
        mTelephonyManager = TelephonyManager.getDefault();
        mPhone = (PhoneProxy) PhoneFactory.getPhone(mPhoneId);
        /*
         * It is safer for UI than using thread. We have to {@link #destroy()} the looper after quit
         * this UI.
         */
        HandlerThread thread = new HandlerThread("RadioInteraction[" + phoneId + "]");
        thread.start();

        mMsgLooper = thread.getLooper();
        mMsgHandler = new MessageHandler(mMsgLooper);
    }

    public void setCallBack(Runnable callback) {
        mRunnable = callback;
    }

    private final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "MessageHandler handleMessage " + msg);
            int timeout = Integer.parseInt(String.valueOf(msg.obj));
            switch (msg.what) {
                case MSG_POWER_OFF_RADIO:
                    powerOffRadioInner(timeout);
                    break;
                case MSG_POWER_OFF_ICC:
                    powerOffIccCardInner(timeout);
                    break;
                case MSG_POWER_ON_RADIO:
                    powerOnRadioInner(timeout, msg.arg1 == 1);
                    break;
                default:
                    break;
            }

        }
    }

    public void destoroy() {
        mMsgLooper.quit();
    }

    /*
     * The interface of ITelephony.setRadioPower is a-synchronized handler. But some case should be
     * synchronized handler. A method to power off the radio.
     */
    public void powerOffRadio(int timeout) {
        Log.i(TAG, "powerOffRadio for Phone" + mPhoneId);
        mMsgHandler.sendMessage(mMsgHandler.obtainMessage(MSG_POWER_OFF_RADIO, timeout));
    }

    private void powerOffRadioInner(int timeout) {
        Log.i(TAG, "powerOffRadioInner for Phone" + mPhoneId);
        final long endTime = SystemClock.elapsedRealtime() + timeout;
        boolean radioOff = false;

        radioOff = mPhone == null
                || (mPhone.getServiceState().getState() == ServiceState.STATE_POWER_OFF);
        if (!radioOff) {
            mPhone.setRadioPower(false);
            Log.w(TAG, "Powering off radio...");
        }

        Log.i(TAG, "Waiting for radio poweroff...");

        while (SystemClock.elapsedRealtime() < endTime) {
            // To give a chance for CPU scheduler
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (radioOff) {
                Log.i(TAG, "Radio turned off.");
                break;
            } else {
                radioOff = mPhone == null
                        || (mPhone.getServiceState().getState() == ServiceState.STATE_POWER_OFF);
                Log.w(TAG, "phone = " + mPhone + "  radioOff = " + radioOff);
            }
        }

        if (mRunnable != null) {
            Log.i(TAG, "Run the callback.");
            mRunnable.run();
        }
    }

    /*
     * The interface of ITelephony.setIccCard is a-synchronized handler. But some case should be
     * synchronized handler. A method to power off the IccCard.
     */
    public void powerOffIccCard(int timeout) {
        Log.i(TAG, "powerOffIccCard for Phone" + mPhoneId);
        mMsgHandler.sendMessage(mMsgHandler.obtainMessage(MSG_POWER_OFF_ICC, timeout));
    }

    private void powerOffIccCardInner(int timeout) {
        Log.i(TAG, "powerOffIccCardInner for Phone" + mPhoneId);
        final long endTime = SystemClock.elapsedRealtime() + timeout;
        boolean iccOff = false;

        iccOff = mPhone == null || !mTelephonyManager.hasIccCard();
        if (!iccOff) {
            //mPhone.setIccCard(false);FIXME
            Log.w(TAG, "Powering off IccCard...");
        }

        Log.i(TAG, "Waiting for radio poweroff...");

        while (SystemClock.elapsedRealtime() < endTime) {
            if (iccOff) {
                Log.i(TAG, "IccCard turned off.");
                break;
            } else {
                iccOff = mPhone == null || !mTelephonyManager.hasIccCard();
            }
            // To give a chance for CPU scheduler
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        SystemClock.sleep(500);
        if (mRunnable != null) {
            Log.i(TAG, "Run the callback.");
            mRunnable.run();
        }
    }

    /*
     * A wrapper for interface of ITelephony.setIccCard();
     */
    public void powerOnIccCard() {
        Log.i(TAG, "powerOnIccCard for Phone" + mPhoneId);

        if (mPhone != null) {
            //mPhone.setIccCard(true);FIXME
            Log.i(TAG, "Powering on IccCard...");
        }

        SystemClock.sleep(500);
    }

    /*
     * A wrapper for interface of ITelephony.setRadio();
     */
    public void powerOnRadio() {
        Log.i(TAG, "powerOnRadio for Phone" + mPhoneId);

        if (mPhone != null) {
            mPhone.setRadioPower(true);
            Log.i(TAG, "Powering on radio...");
        }

        SystemClock.sleep(500);
    }

    public void powerOnRadio(int timeout, boolean force) {
        Log.i(TAG, "powerOnIRadio for Phone" + mPhoneId);
        Message msg = mMsgHandler.obtainMessage(MSG_POWER_ON_RADIO, timeout);
        msg.arg1 = force ? 1 : 0;
        mMsgHandler.sendMessage(msg);
    }

    public void powerOnRadioInner(int timeout, boolean force) {
        Log.i(TAG, "powerOnRadioInner for Phone" + mPhoneId);
        final long endTime = SystemClock.elapsedRealtime() + timeout;

        boolean radioOn = false;
        boolean isAirplaneModeOn = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        boolean isStandby = Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.SIM_STANDBY + mPhoneId, 1) == 1;
        Log.d(TAG, "powerOnRadioInner isAirplaneModeOn = " + isAirplaneModeOn +
                " isStandby = " + isStandby);

        radioOn = mPhone != null
                && mPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF;
        if ((force || !isAirplaneModeOn) && isStandby && !radioOn && mPhone != null) {//changed for coverity 107954
            mPhone.setRadioPower(true);
            Log.i(TAG, "Powering on radio...");
        }

        SystemClock.sleep(500);

        Log.i(TAG, "Waiting for radio power on...");

        while (SystemClock.elapsedRealtime() < endTime) {
            // To give a chance for CPU scheduler
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (isAirplaneModeOn || !isStandby || radioOn) {
                Log.i(TAG, "Radio turned on.");
                break;
            } else {

                radioOn = mPhone != null
                        && mPhone.getServiceState().getState() != ServiceState.STATE_POWER_OFF;

            }
        }

        if (mRunnable != null) {
            Log.i(TAG, "Run the callback.");
            mRunnable.run();
        }
    }
}
