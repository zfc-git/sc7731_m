package com.zediel.itemstest;

import java.text.SimpleDateFormat;
import java.util.Date;

public class RTCTest {
	public static final String timeFormat = "hh:mm:ss";
	public static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat(timeFormat);

	public RTCTest() {
	}
	
	public  String getTime() {
        return TIME_FORMAT.format(new Date());
    }
}
