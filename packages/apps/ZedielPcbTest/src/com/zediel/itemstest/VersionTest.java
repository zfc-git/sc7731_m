package com.zediel.itemstest;

import android.os.Build;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.util.Log;

public class VersionTest {
	private static final String TAG = "VersionTest";
	public static final String KERNEL_PROC_VERSION = "/proc/version";
	public Context context;
	
	public VersionTest(Context context) {
		super();
		this.context = context;
	}

	public String[] readDeviceString(String devices) {
		if ("" == devices) {
			return null;
		}
		String[] devArray = devices.split("/");
		return devArray;
	}

	public static String readLine(String filename) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(filename),
				256);
		try {
			return reader.readLine();
		} finally {
			reader.close();
		}
	}

	
	public static String operKernelVersion(String procKernelVersion) {
		/*
		 * final String VERSION_PROC_REGEX =
		 * "Linux version (\\S+) \\((\\S+?)\\) " +
		 * "(?:\\(gcc.+? \\)) (#\\d+) (?:.*?)?" +
		 * "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";
		 */

        final String VERSION_PROC_REGEX =
            "Linux version (\\S+) " + /* group 1: "3.0.31-g6fb96c9" */
            "\\((\\S+?)\\) " +        /* group 2: "x@y.com" (kernel builder) */
            "(?:\\(gcc.+? \\)) " +    /* ignore: GCC version information */
            "(#\\d+) " +              /* group 3: "#1" */
            "(?:.*?)?" +              /* ignore: optional SMP, PREEMPT, and any CONFIG_FLAGS */
            "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)"; /* group 4: "Thu Jun 28 11:02:39 PDT 2012" */

		Matcher m = Pattern.compile(VERSION_PROC_REGEX).matcher(
				procKernelVersion);
		if (!m.matches()) {
			Log.e(TAG,
					"red did not match on /proc/version only returned"
							+ m.groupCount());
			return "Unavailable";
		} else if (m.groupCount() < 4) {
			Log.e(TAG,
					"Regex match on /proc/version only returned "
							+ m.groupCount() + "groups");
			return "Unavailable";
		}
        return new StringBuilder().append(m.group(1))
                .append(" ")
                //.append(m.group(2))
                //.append(" ")
                .append(m.group(3))
                .append(" ")
                .append(m.group(4)).toString();
	}

	public static String operKernelVersionR(String procKernelVersion) {
        // Example:
        // 4.9.29-g958411d
        // #1 SMP PREEMPT Wed Jun 7 00:06:03 CST 2017
        final String VERSION_REGEX =
				"Linux version (\\S+) " +
				"\\((\\S+?)\\) " +
				"(?:\\(Android.+?\\(.*?\\).*?\\(.*?\\).*?\\(.*?\\)\\)) " +
                "(#\\d+) " +
                "(?:.*?)?" +
                "((Sun|Mon|Tue|Wed|Thu|Fri|Sat).+)";
        Matcher m = Pattern.compile(VERSION_REGEX).matcher(procKernelVersion);
        if (!m.matches()) {
            Log.e(TAG, "Regex did not match on version " + m.groupCount());
            return "Unavailable";
        }

        // Example output:
        // 4.9.29-g958411d
        // #1 Wed Jun 7 00:06:03 CST 2017
        return new StringBuilder().append(m.group(1))
                .append(" ")
                //.append(m.group(2))
                //.append(" ")
                .append(m.group(3))
                .append(" ")
                .append(m.group(4)).toString();
	}

	/**
	 * get kernel version
	 * 
	 * @return
	 */
	public static String getKernelVersion() {
		try {
			if (Build.VERSION.SDK_INT >= 30) { //Build.VERSION_CODES.R
				return operKernelVersionR(readLine(KERNEL_PROC_VERSION));
			} else {
				return operKernelVersion(readLine(KERNEL_PROC_VERSION));
			}
		} catch (IOException e) {
			// TODO: handle exception
		}

		return "Unavailable";
	}
	/**
	 * 
	 * @return
	 */
	public static String getAndroidSDK(){
		return android.os.Build.VERSION.SDK ;
	}
	
	/**
	 * 
	 * @return
	 */
	public static String getAndroidRelease(){
		return android.os.Build.VERSION.RELEASE;
	}
}
