package com.sprd.android.internal.telephony;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import android.telephony.Rlog;
import android.util.Xml;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import com.android.internal.util.XmlUtils;


public class VolteConfig {

    private static final String TAG = VolteConfig.class.getSimpleName();
    static final String VOLTE_ENABLE_PATH ="etc/volte-conf.xml";
    static final String PREFERENCE_PACKAGE = "com.android.phone";
    private Map<String,String> mConfigMap = new HashMap<String, String>();
    private ArrayList<String> mPrebuiltPlmn = new ArrayList<String>();

    private static VolteConfig instance = null;
    public static VolteConfig getInstance(){
        if(instance == null){
            synchronized(VolteConfig.class){
                if(instance == null){
                    instance = new VolteConfig();
                }
            }
        }
        return instance;
    }

    public void loadVolteConfig(Context context){
        loadUserConfig(context);
        loadPrebuiltConfig();
    }

    public boolean containsCarrier(String carrier) {
        return mConfigMap.containsKey(carrier);
    }

    public boolean getVolteEnable(String carrier) {
        return "true".equals(String.valueOf(mConfigMap.get(carrier)));
    }

    public synchronized ArrayList getPrebuiltConfig(){
        synchronized(this){
            if(mPrebuiltPlmn.isEmpty()){
                loadPrebuiltConfig();
            }
        }
        return mPrebuiltPlmn;
    }

    private void loadUserConfig(Context context){
       try{
            Context otherContext = context.createPackageContext(PREFERENCE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            SharedPreferences sp = otherContext.getSharedPreferences("volteconfig", Activity.MODE_WORLD_READABLE);
            mConfigMap = (Map<String, String>) sp.getAll();
            Rlog.w(TAG, "loadUserConfig in SharedPreferences mConfigMap = "+ mConfigMap);
        }catch(NameNotFoundException e){
            Rlog.w(TAG, "can not loadUserConfig in SharedPreferences");
            e.printStackTrace();
        }
    }

    private void loadPrebuiltConfig() {
        FileReader volteReader;

        File volteFile = new File(Environment.getRootDirectory(),
                VOLTE_ENABLE_PATH);

        try {
            volteReader = new FileReader(volteFile);
        } catch (FileNotFoundException e) {
            Rlog.w(TAG, "Can not open " + volteFile.getAbsolutePath());
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(volteReader);
            XmlUtils.beginDocument(parser, "allowPlmns");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"allowPlmn".equals(name)) {
                    break;
                }

                String numeric = parser.getAttributeValue(null, "numeric");
                String enable = parser.getAttributeValue(null, "enable");

                mConfigMap.put(numeric, enable);
                mPrebuiltPlmn.add(numeric);
            }
        } catch (XmlPullParserException e) {
            Rlog.w(TAG, "Exception in volte-conf parser " + e);
        } catch (IOException e) {
            Rlog.w(TAG, "Exception in volte-conf parser " + e);
        } finally{
            try{
                if(volteReader != null){
                    volteReader.close();
                }
            }catch(IOException e){}
        }
    }
}