package com.zediel.util;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.util.Log;

import java.io.File;

public class SerialNoUtil {
    private static String TAG = "SerialNoUtil";
    public static final long ONE_GB = 1024 * 1024 * 1024;

    public static String getSerial() {
        return Build.SERIAL;
    }

    public static long[] getPrivateStorageSize(Context context) {
        final StorageManager sm = context.getSystemService(StorageManager.class);
        long[] size = new long[2];
        for (VolumeInfo info : sm.getVolumes()) {
            if (info != null && info.getType() == VolumeInfo.TYPE_PRIVATE && info.isMountedReadable()) {
                final File file = info.getPath();
                size[0] += file.getTotalSpace();
                size[1] += file.getFreeSpace();
                Log.d(TAG, "privateTotalBytes=" + size[0] + ", privateFreeBytes=" + size[1]);
                break;
            }
        }
        return size;
    }
}
