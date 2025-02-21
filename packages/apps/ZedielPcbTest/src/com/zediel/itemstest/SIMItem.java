package com.zediel.itemstest;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SIMItem {
	private static final String TAG = "SIMItem";

	private Context context;
	
	private TelephonyManager telMgr;
	private int mSimReadyCount = 0;
	private int phoneCount = 2;
	public SIMItem(Context context) {
		super();
		this.context = context;
		//phoneCount = TelephonyManager.getPhoneCount();
	}
	
	public String getResultList(int simId){
		String returnResult;
		telMgr = (TelephonyManager) context.getSystemService(context.TELEPHONY_SERVICE);
		/*if (telMgr.getSimState() == TelephonyManager.SIM_STATE_READY) {
			returnResult = "Sim State:fine;";
			mSimReadyCount++;
		} else if (telMgr.getSimState() == TelephonyManager.SIM_STATE_ABSENT) {
			returnResult = "no SIM card;";
		} else {
			returnResult = "Sim State:locked/unknow;";
		}*/
		
		/*if (telMgr.getDeviceId() != null) {
			returnResult += "id:"+telMgr.getDeviceId();
		} else {
			returnResult += "no device id";
		}*/
		
		//returnResult += ";Type:";
		/*if (telMgr.getPhoneType() == 0) {
			returnResult +="NONE";
		} else if (telMgr.getPhoneType() == 1) {
			returnResult +="GSM";
		} else if (telMgr.getPhoneType() == 2) {
			returnResult +="CDMA";
		} else if (telMgr.getPhoneType() == 3) {
			returnResult +="SIP";
		}*/
		
		 int simState = telMgr.getSimState();
        boolean result = true;
        switch (simState) {
            case TelephonyManager.SIM_STATE_ABSENT:
                result = false; // 没有SIM卡
                break;
            case TelephonyManager.SIM_STATE_UNKNOWN:
                result = false;
                break;
        }
        if(result){
        String operator = telMgr.getSimOperator();
        if(operator!=null){
            if(operator.equals("46000") || operator.equals("46002")||operator.equals("46007")){
                return "中国移动";
            }
            }else if(operator.equals("46001")){
                return "中国联通";
            }else if(operator.equals("46003")){
                 return "中国电信";
           }
        }else{
                 return "未插入sim卡,测试失败!";
        }
        return "unknow";
		
		
		//return returnResult;
	}
	public String showDevice(){
		String simInfo="";

		//for(int i = 0;i < phoneCount; i++){
		//	simInfo += "Sim" + (i+1)+":" + getResultList(i);
		//	simInfo = simInfo + "\n" + "";
		//}
		simInfo = "Sim 1" +":" + getResultList(0);
		Log.i(TAG,">>>> the siminfo ==="+simInfo);
		return simInfo;
	}
}
