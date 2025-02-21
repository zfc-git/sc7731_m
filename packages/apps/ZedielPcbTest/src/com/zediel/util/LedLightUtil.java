package com.zediel.util;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class LedLightUtil {
    private final static String TAG = "LedLightUtil";
    public  static int DELAY_INTERNAL = 500;
    public final static int DELAY_INTERNAL1 = 1000;
    public final static int POWER_LED_OPEN = 0x01;
    public final static int POWER_LED_CLOSE = 0x02;
    public final static int WORK_LED_OPEN = 0x03;
    public final static int WORK_LED_CLOSE = 0x04;
	public  static boolean isAgeingOK = false ;
    public final static String LED_OPEN = "1";
    public final static String LED_CLOSE = "0";
    public final static String POWER_LED = "/sys/class/leds/power_led/brightness";
    public final static String WORK_LED = "/sys/class/leds/work_led/brightness";
    private static WorkHandler sWorkHandler = new WorkHandler();

    public static void writeValue(String path, String value) {
    //    Log.d(TAG, "path=" + path + ", value= " + value);
        try {
            BufferedWriter bufWriter = new BufferedWriter(new FileWriter(path));
            bufWriter.write(value);
            bufWriter.close();
   //         Log.d(TAG, "write success");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "write fail");
        }

		if(isAgeingOK){
			DELAY_INTERNAL = 3000;
		}
    }

    public static void onDestroy() {
        if (sWorkHandler != null) {
            sWorkHandler.removeMessages(POWER_LED_OPEN);
            sWorkHandler.removeMessages(POWER_LED_CLOSE);
            sWorkHandler.removeMessages(WORK_LED_OPEN);
            sWorkHandler.removeMessages(WORK_LED_CLOSE);
        }
        writeValue(POWER_LED, LED_OPEN);
        writeValue(WORK_LED, LED_OPEN);
    }

    public static void testPowerLed() {
        sWorkHandler.removeMessages(POWER_LED_OPEN);
        sWorkHandler.removeMessages(POWER_LED_CLOSE);
        sWorkHandler.sendEmptyMessageDelayed(POWER_LED_OPEN, DELAY_INTERNAL1);
    }

    public static void testWorkLed() {
        sWorkHandler.removeMessages(WORK_LED_OPEN);
        sWorkHandler.removeMessages(WORK_LED_CLOSE);
        sWorkHandler.sendEmptyMessageDelayed(WORK_LED_OPEN, DELAY_INTERNAL1);
    }

    public static class WorkHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == POWER_LED_OPEN) {
                writeValue(POWER_LED, LED_OPEN);
                sWorkHandler.sendEmptyMessageDelayed(POWER_LED_CLOSE, DELAY_INTERNAL);
            } else if (msg.what == POWER_LED_CLOSE) {
                writeValue(POWER_LED, LED_CLOSE);
                sWorkHandler.sendEmptyMessageDelayed(POWER_LED_OPEN, DELAY_INTERNAL);
            } else if (msg.what == WORK_LED_OPEN) {
                writeValue(WORK_LED, LED_OPEN);
                sWorkHandler.sendEmptyMessageDelayed(WORK_LED_CLOSE, DELAY_INTERNAL);
            } else if (msg.what == WORK_LED_CLOSE) {
                writeValue(WORK_LED, LED_CLOSE);
                sWorkHandler.sendEmptyMessageDelayed(WORK_LED_OPEN, DELAY_INTERNAL);
            }
        }
    }
}
