package com.zediel.itemstest;

import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.math.BigDecimal;

public class MemeryItme {
	private static final String TAG = "MemeryItme";
    public static final int KEY_INT4 = 4;
    public static final int KEY_INT8 = 8;
    public static final int KEY_INT16 = 16;
    public static final int KEY_INT32 = 32;
    public static final int KEY_INT64 = 64;
    public static final int KEY_INT128 = 128;
    public static final int KEY_INT256 = 256;
	private static Context context;

	public MemeryItme(Context context) {
		this.context = context;
	}

    public static BigDecimal getReasonableSize(BigDecimal decimal) {
        int value = decimal.intValue();
        if (value < KEY_INT4) {
            return new BigDecimal(KEY_INT4);
        } else if (value < KEY_INT8) {
            return new BigDecimal(KEY_INT8);
        } else if (value < KEY_INT16) {
            return new BigDecimal(KEY_INT16);
        } else if (value < KEY_INT32) {
            return new BigDecimal(KEY_INT32);
        } else if (value < KEY_INT64) {
            return new BigDecimal(KEY_INT64);
        } else if (value < KEY_INT128) {
            return new BigDecimal(KEY_INT128);
        } else {
            return new BigDecimal(KEY_INT256);
        }
    }

    public static String getDecimalNumber(String value) {
        if (value != null && !TextUtils.isEmpty(value)) {
            int index = value.indexOf("G");
            if (index != -1) {
                value = value.substring(0, index);
            }
            if (value.matches("\\d*\\.?\\d+")) {
                return value;
            }
        }
        return null;
    }
	
	public static long getmem_freemem(){
		long MEM_UNUSED;
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        MEM_UNUSED = mi.availMem / (1024*1024);
        Log.i(TAG,"<<<<< get the free mem is >>>>>"+MEM_UNUSED);
        return MEM_UNUSED;
	}
	
    public static int getmem_TOLAL() {
        int mTotal;
        String path = "/proc/meminfo";
        String content = null;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(path), 8);
            String line;
            if ((line = br.readLine()) != null) {
                content = line;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // beginIndex
        int begin = content.indexOf(':');
        // endIndex
        int end = content.indexOf('k');
    	content = content.substring(begin + 1, end).trim();
        mTotal = (Integer.parseInt(content))/1024;
        Log.i(TAG,">>>>>> total mem DDR--is ==="+mTotal);
        return mTotal;
    }

    public static long getHasUsedMem(){
    	return getmem_TOLAL() - getmem_freemem();
    }
}
