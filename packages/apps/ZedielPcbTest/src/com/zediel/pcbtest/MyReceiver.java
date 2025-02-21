/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
package com.zediel.pcbtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import android.widget.Toast;

import android.net.Uri;
import android.os.Environment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import android.os.storage.StorageManager;

public class MyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("action.zed.apk.pcbtest".equals(intent.getAction())) {
                       String packageName = intent.getStringExtra("packageName");
                       Log.d("flyz", "----onReceive packageName:" + packageName);
            launchApp(context, packageName);
        }
    }

       private void launchApp(Context context, String packageName) {

                String sdpath = getsdcardpatch(context);
                Log.i("flyz","there is sdpath:" + sdpath);

                File file = new File(sdpath + "/zedpcbtest.txt");
                if (file.exists()) {
                                                         Intent testintent = new Intent(Intent.ACTION_MAIN);
                                                         testintent.setClassName("com.zediel.pcbtest","com.zediel.pcbtest.ZedielTools");
                                                         testintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                         context.startActivity(testintent);
                }else{
                         Log.i("flyz","there is no file");
                         file = new File("/storage/AA07-D452/zedpcbtest.txt");
                         if (file.exists()) {
                                 Intent testintent = new Intent(Intent.ACTION_MAIN);
                                 testintent.setClassName("com.zediel.pcbtest","com.zediel.pcbtest.ZedielTools");
                                 testintent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                 context.startActivity(testintent);
                         }else{
                                Log.i("flyz","there is no file+++++");
                         }
               }

    }

       public String getsdcardpatch(Context context) {

        StorageManager storageManager = (StorageManager) context.getSystemService("storage");
        try {
            Class storeManagerClazz = Class.forName("android.os.storage.StorageManager");

            Method getVolumesMethod = storeManagerClazz.getMethod("getVolumes");

            List<?> volumeInfos  = (List<?>)getVolumesMethod.invoke(storageManager);

            Class volumeInfoClazz = Class.forName("android.os.storage.VolumeInfo");

            Method getFsUuidMethod = volumeInfoClazz.getMethod("getFsUuid");

            Field pathField = volumeInfoClazz.getDeclaredField("path");
            String pathString="";
            if(volumeInfos != null){
                for(Object volumeInfo:volumeInfos){
                    String uuid = (String)getFsUuidMethod.invoke(volumeInfo);
                    Log.i("qyh","there is uuid:" + uuid);
                    if(uuid != null){
                        pathString = (String)pathField.get(volumeInfo);
                        Log.i("qyh","there is pathString:" + pathString);
                        return pathString;
                    }
                }
                //return pathString;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return "unknow";
    }
}