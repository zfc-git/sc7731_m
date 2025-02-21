package com.zediel.itemstest;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class NandItem {
	private static final String TAG = "NandItem";
	private static Context context;
	public static String []Volumes;

	public NandItem(Context context) {
		this.context = context;
	}
	public static String readUSB(){
		for(String tmp : Volumes){
			if(tmp.startsWith("usbdisk"))
					return tmp;
		}
		return getUSBTotalSize();
	}

	public static String readSystem(){
		File root = Environment.getDataDirectory();
       StatFs sf = new StatFs(root.getPath());
       long blockSize = sf.getBlockSize();
       long blockCount = sf.getBlockCount();
       long availCount = sf.getAvailableBlocks();
       return ((blockSize*blockCount)/1024/1024/1024)+"G";
	}
	
	public static String readSDCard(){
		for(String tmp : Volumes){
			if(tmp.startsWith("sdcard0"))
				return tmp;
		}
		return getSDTotalSize();

	}
	
	/** 
     * 获得SD卡总大小 
     *  
     * @return 
     */  
    private static  String getSDTotalSize() {  
        File path = new File("/storage/sdcard0");
        if(path.exists()){
		        StatFs stat = new StatFs(path.getPath());  
		        long blockSize = stat.getBlockSize();  
		        long totalBlocks = stat.getBlockCount();
		        return Long.toString(blockSize * totalBlocks/1024/1024);
        }else{
        		return null;
        }
        //return Formatter.formatFileSize(context, blockSize * totalBlocks/1024);  
    } 
    
	/** 
     * 获得SD卡总大小 
     *  
     * @return 
     */  
    private static  String getUSBTotalSize() {  
        File path = new File("/storage/usbdisk");
        if(path.exists()){
		        StatFs stat = new StatFs(path.getPath());  
		        long blockSize = stat.getBlockSize();  
		        long totalBlocks = stat.getBlockCount();  
		        return Long.toString(blockSize * totalBlocks/1024/1024);
        }else{
        		return null;
        }
        //return Formatter.formatFileSize(context, blockSize * totalBlocks/1024);  
    }  
  
    /** 
     * 获得sd卡剩余容量，即可用大小 
     *  
     * @return 
     */  
    private String getSDAvailableSize() {  
        File path = Environment.getExternalStorageDirectory();  
        StatFs stat = new StatFs(path.getPath());  
        long blockSize = stat.getBlockSize();  
        long availableBlocks = stat.getAvailableBlocks();  
        return Formatter.formatFileSize(context, blockSize * availableBlocks);  
    }  
	
	
	
	
	public static void getStorage(){
		String result = "";
		String[] args = new String[1];
		args[0] = "df";
		//args[1] = "-l";
		try{
			Process process = Runtime.getRuntime().exec(args);
		  //get the err line
		  InputStream stderr = process.getErrorStream();
		  InputStreamReader isrerr = new InputStreamReader(stderr);
		  BufferedReader brerr = new BufferedReader(isrerr);
		  //get the output line  
		  InputStream outs = process.getInputStream();
		  InputStreamReader isrout = new InputStreamReader(outs);
		  BufferedReader brout = new BufferedReader(isrout);
		  String line = null;
		  
		  // get the whole error message string  while ( (line = brerr.readLine()) != null)
		   {
			   result += line;
			   result += "/n";
		   } 
		   if( result != "" ){
			   // put the result string on the screen
			   Log.d("readSDCard",result);
		   }
		   // get the whole standard output string
		   while ( (line = brout.readLine()) != null) {
			   result += line;
			   result += "/n";
		   }
		   if( result != "" ) {
			   Log.d("readSDCard",result);
			   // put the result string on the screen
		   }
		}catch(Throwable t){
			   	t.printStackTrace();
		}
		
		Volumes = result.split("/n/storage/");
		Log.d("readSDCard","num:" + Volumes.length);
	}
	
	 public static  String getmemorysize(Context context) {

        StorageManager storageManager = (StorageManager) context.getSystemService("storage");
        try {
            Class storeManagerClazz = Class.forName("android.os.storage.StorageManager");

            Method getVolumesMethod = storeManagerClazz.getMethod("getVolumes");

            List<?> volumeInfos  = (List<?>)getVolumesMethod.invoke(storageManager);

            Class volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo");
            Class deskinfoClazz=Class.forName("android.os.storage.DiskInfo");

            Method getFsUuidMethod = volumeInfoClazz.getMethod("getFsUuid");
            Method getDiskInfo = volumeInfoClazz.getMethod("getDisk");
            Method getDiskInfoDescription = deskinfoClazz.getMethod("getDescription");

            Field pathField = volumeInfoClazz.getDeclaredField("path");
            StringBuffer sb=new StringBuffer();
            if(volumeInfos != null){
                for(Object volumeInfo:volumeInfos){

                    String uuid = (String)getFsUuidMethod.invoke(volumeInfo);
                    if(uuid != null){
                     Object diskinfo=  getDiskInfo.invoke(volumeInfo);
                     String desc=getDiskInfoDescription.invoke(diskinfo).toString();
                     String pathString = (String)pathField.get(volumeInfo);
                        StatFs statFs = new StatFs(pathString);
						//Log.d("qyh",pathString);
                        long totalSize = statFs.getTotalBytes();
                       String size= Formatter.formatFileSize(context, totalSize);
                       sb.append(desc+"  大小："+size+"  ||  ");
                    }
                }
               return sb.toString();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
   return "unknow";
    }
	
}
