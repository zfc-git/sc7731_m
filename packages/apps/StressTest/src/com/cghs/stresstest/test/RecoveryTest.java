package com.cghs.stresstest.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.Toast;
import com.cghs.stresstest.R;
import android.os.Environment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.CompoundButton;
//import com.android.internal.os.storage.ExternalStorageFormatter;
import com.cghs.stresstest.util.StresstestUtil;
import android.util.Log;
import java.io.FileInputStream;
import android.app.Dialog;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import android.app.KeyguardManager;
import android.os.SystemProperties;
import android.os.storage.VolumeInfo;
import android.os.storage.DiskInfo;
import android.os.storage.StorageVolume;
import android.os.storage.StorageManager;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;

public class RecoveryTest extends StressBase{
	public static final String TAG = "RecoveryTest";
	
	public static final String RECOVERY_STATE_FILE = "/cache/recovery/last_Recovery_state";//"/cache/recovery/Recovery_state"; /*Environment.getExternalStorageDirectory().getPath() +*/
	//public static final String RECOVERY_STATE_FILE_TF = "/storage/sdcard0/Recovery_state";
	public static String RECOVERY_STATE_FILE_TF = "/storage/sdcard0/Recovery_state";
        public String usb_dir=null;
	public String sdcard_dir=null;        
	
	private TextView mMaxView;
	private TextView mTestTimeTv;
	private TextView mCountdownTv;
	private TextView mWarning_tf;
	
	private CheckBox mEraseFlashCb;
	private CheckBox mWipeAllCb;
	private CheckBox mCheckSys;
	private CountDownTimer mCountDownTimer;
	
	private int mStartTest = 0;
    private String RebootMode = null;

    private WakeLock mWakeLock;	
	private boolean mIsEraseFlash = false;
	private boolean mIsWipeAll = false;
	private boolean mIsCheckSys = false;
	private boolean mFT = false;
	public static final int MSG_START_TEST = 1;
	private String UMSstate = SystemProperties.get("ro.factory.hasUMS");
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_recovery_test);
		setDefaultBtnId(R.id.start_btn, R.id.stop_btn, R.id.exit_btn,
				R.id.maxtime_btn);
		mMaxView = (TextView) findViewById(R.id.maxtime_tv);
		mTestTimeTv = (TextView) findViewById(R.id.testtime_tv);
		mCountdownTv = (TextView) findViewById(R.id.countdown_tv);
		mWarning_tf = (TextView) findViewById(R.id.warning_tf);
		
		mEraseFlashCb = (CheckBox) findViewById(R.id.erase_cb);
		mWipeAllCb = (CheckBox) findViewById(R.id.wipeall_cb);
		mCheckSys = (CheckBox) findViewById(R.id.check_sys);
//	((KeyguardManager)getSystemService("keyguard")).newKeyguardLock("TestRecovery").disableKeyguard();
	    mWakeLock = ((PowerManager)getSystemService("power")).newWakeLock(PowerManager.FULL_WAKE_LOCK, "RecoveryTest");
        mWakeLock.acquire();
	/*	if(!UMSstate()){
		  mEraseFlashCb.setVisibility(View.GONE);
		  mWarning_tf.setVisibility(View.VISIBLE);
		}*/
                init_StoragePath(this);
Log.d(TAG,"RECOVERY_STATE_FILE_TF:"+RECOVERY_STATE_FILE_TF);
		initData();
		updateUI();
		
		mEraseFlashCb.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
	
			@Override
		    public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				// TODO Auto-generated method stub
				if(arg1)
				mWarning_tf.setVisibility(View.VISIBLE);
				else
				mWarning_tf.setVisibility(View.GONE);
			}});
		mWipeAllCb.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {   
		    @Override
		    public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			// TODO Auto-generated method stub
			if(arg1)
			mWarning_tf.setVisibility(View.VISIBLE);
			else
			mWarning_tf.setVisibility(View.GONE);
			}});
			
			mCheckSys.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {   
		    @Override
		    public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
			// TODO Auto-generated method stub
			}});

		if (mStartTest == 1) {
			updateTestTimeTV();
			if(mIsCheckSys){
				 if(isRebootError()){
					stopTest();
                          Log.e(TAG,"check system error,stop test!!");
					mTestTimeTv.setText(mTestTimeTv.getText()+" Test fail for error!");
				 }else if (mMaxTestCount == 0 || mCurrentCount < mMaxTestCount) {
				   preStartTest();
			   }
		    }else if (mMaxTestCount == 0 || mCurrentCount < mMaxTestCount) {
				preStartTest();
			}
		}
		
	}
        
        public void init_StoragePath(Context context) {
                StorageManager mStorageManager = (StorageManager) getSystemService(StorageManager.class);
		//flash dir
	//	flash_dir = Environment.getExternalStorageDirectory().getPath();		
		final List<VolumeInfo> volumes = mStorageManager.getVolumes();
	        Collections.sort(volumes, VolumeInfo.getDescriptionComparator());
		for (VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                Log.d(TAG, "VolumeInfo.TYPE_PUBLIC");
                Log.d(TAG, "Volume path:"+vol.getPath());
                DiskInfo disk = vol.getDisk();
                if(disk != null) {
                	if(disk.isSd()) {
                		//sdcard dir
                		StorageVolume sv = vol.buildStorageVolume(context, context.getUserId(), false);
                		sdcard_dir = sv.getPath();
                                RECOVERY_STATE_FILE_TF=new String(sdcard_dir)+"/Recovery_state";
                	}else if(disk.isUsb()){
                		//usb dir
               		StorageVolume sv = vol.buildStorageVolume(context, context.getUserId(), false);
                		usb_dir = sv.getPath();
                                RECOVERY_STATE_FILE_TF=new String(usb_dir)+"/Recovery_state";
                	}
                }
            }
        }
	}

    private boolean UMSstate(){
			return UMSstate.equals("true");
	}
	
	private void initData() { 
		mStartTest = getIntent().getIntExtra("enable", 0);
		mCurrentCount = getIntent().getIntExtra("cur", 0);
		mMaxTestCount = getIntent().getIntExtra("max", 0);
                if(mMaxTestCount < 0)
                    mMaxTestCount = 0;
		mIsWipeAll = getIntent().getBooleanExtra("wipeall", false);
		mIsEraseFlash = getIntent().getBooleanExtra("eraseflash", false);
		mIsCheckSys = getIntent().getBooleanExtra("checksys", false);
	}
	
	
	private void updateUI() {
		updateMaxTV();
		mEraseFlashCb.setChecked(mIsEraseFlash);
		mWipeAllCb.setChecked(mIsWipeAll);
		mCheckSys.setChecked(mIsCheckSys);
	}
	
	
	@Override
	public void updateMaxTV() {
		super.updateMaxTV();
		mMaxView.setText(getString(R.string.max_test_time)+mMaxTestCount);
	}
	
	public void updateTestTimeTV() {
		mTestTimeTv.setText(getString(R.string.already_test_time)+mCurrentCount);
		mTestTimeTv.setVisibility(View.VISIBLE);
	}
	
	
	@Override
	public void onStartClick() {
        mFT = true;
		preStartTest();
	}

	@Override
	public void onStopClick() {
		stopTest();
	}

	@Override
	public void onSetMaxClick() {
		
	}
	
	public void preStartTest() {
		mIsEraseFlash = mEraseFlashCb.isChecked();
		mIsWipeAll = mWipeAllCb.isChecked();
		mIsCheckSys = mCheckSys.isChecked();
		mStartTest = 1;
		incCurCount();
		writeRecoveryState(formatStateContent());
		isRunning = true;
		updateBtnState();
		mCountDownTimer = new CountDownTimer(30000, 1000) {
			
			@Override
			public void onTick(long millisUntilFinished) {
				mCountdownTv.setText((millisUntilFinished/1000)+"");
				mCountdownTv.setVisibility(View.VISIBLE);
			}
			
			@Override
			public void onFinish() {
				mCountdownTv.setVisibility(View.INVISIBLE);
				if(mIsCheckSys){
					 if(isSystemError()){
						 stopTest();
						 mTestTimeTv.setText(mTestTimeTv.getText()+" Test fail for error!");
				   }else{
				     startTest();
				    }
				}else{
				startTest();
				}
			}
		}.start();
	}
	
	private void startTest() {
		if (mIsWipeAll||mIsEraseFlash) {
			/*
			try {
				bootCommand(this, "--wipe_all");
			} catch (IOException e) {
				e.printStackTrace();
			}
			*/
			
            Intent intent = new Intent(Intent.ACTION_MASTER_CLEAR);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
			sendBroadcast(intent);
/*		} else if (mIsEraseFlash) {
			Intent intent = new Intent(ExternalStorageFormatter.FORMAT_AND_FACTORY_RESET);
            intent.setComponent(ExternalStorageFormatter.COMPONENT_NAME);
            intent.putExtra(Intent.EXTRA_REASON, "WipeAllFlash");
            this.startService(intent);*/
		} else {
                        Intent intent = new Intent(Intent.ACTION_MASTER_CLEAR);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
			sendBroadcast(intent);
		}
	}
	
	private void stopTest() {
		isRunning = false;
		updateBtnState();
		mStartTest = 0;
		mCurrentCount = 0;
		if (mCountDownTimer != null)
			mCountDownTimer.cancel();
		mCountdownTv.setVisibility(View.INVISIBLE);
		writeRecoveryState(formatStateContent());
	}
	
	private void SavedRebootMode() {
		Process process = null;
		String filePath = "mnt/sdcard/boot_mode.txt";
		File file1 = new File(filePath);
		if (file1.isFile() && file1.exists()) {
				file1.delete();
				}
			try {
					StresstestUtil.getBootMode(true);
			} catch (Exception e) {
					Log.e(TAG, "getBootMode fail!!!");
			}
			try {
					Thread.sleep(1000);
			} catch (InterruptedException e) {
					e.printStackTrace();
			}
			try {
	            String encoding="GBK";
	            File file=new File(filePath);
	            if(file.isFile() && file.exists()){ //
	                InputStreamReader read = new InputStreamReader(
	                new FileInputStream(file),encoding);//
	                BufferedReader bufferedReader = new BufferedReader(read);
	                String lineTxt = null;
	                while((lineTxt = bufferedReader.readLine()) != null){
	                	Log.d(TAG,lineTxt);
	                	int p1 = lineTxt.indexOf("(");
	                	int p2 = lineTxt.indexOf(")");
	                	RebootMode = lineTxt.substring(p1+1, p2);
	                		Toast.makeText(this,RebootMode,Toast.LENGTH_LONG ).show();
	                	
	                }
	                read.close();
	    }else{
	        Log.e(TAG,"not find the mnt/sdcard/boot_mode.txt");
	    }
	    } catch (Exception e) {
	       Log.e(TAG,"read error!!");
	        e.printStackTrace();
	    }
	}
	private boolean isRebootError(){
		SavedRebootMode();
		
		if(RebootMode!=null){
                  if(Integer.valueOf(RebootMode) == 7){
			   Dialog dialog = new AlertDialog.Builder(
					   this)
			.setTitle(getString(R.string.factory_reset_excep))
			.setMessage(getString(R.string.panic_reboot))
			.setPositiveButton(getString(R.string.ok) ,new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					 dialog.cancel();
					}
			}).setNegativeButton(getString(R.string.cancel) ,new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					dialog.cancel();
			}}).create();
			dialog.show();
				return true;

		   }else if(Integer.valueOf(RebootMode) == 8){
			   Dialog dialog = new AlertDialog.Builder(
					   this)
			.setTitle("RecoveryTest Error")
			.setMessage("It's reboot for watchdog,see thelast_log for details")
			.setPositiveButton("ok",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					dialog.cancel();
				}}).setNegativeButton("cancel",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					dialog.cancel();
				}}).create();
				   dialog.show();
				return true;
				}
	 return false;
	 }
	 return false;
	}
	
	private boolean isSystemError(){
		
		InputStreamReader reader = null;
		BufferedReader bufferedReader = null;
		Process process = null;
		String lineText = null;
        if(!mFT){
		  try{
			process = Runtime.getRuntime().exec("logcat -d");
			reader = new InputStreamReader(process.getInputStream());
			bufferedReader = new BufferedReader(reader);
			Log.d("--hjc","-------------->>lineTxt:"+lineText);
			while((lineText = bufferedReader.readLine()) != null){
		//			Log.d("--hjc","-------------->>lineTxt:"+lineText);
			if(lineText.indexOf("Force finishing activity")!=-1||lineText.indexOf("backtrace:")!=-1){
		            Log.d("--hjc","------lineTxt:"+lineText);
					    Dialog dialog = new AlertDialog.Builder(this)
							.setTitle("RecoveryTest Error")
							.setMessage("It's reboot for system,see logcat for details")
							.setPositiveButton("ok",new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,int whichButton) {
								   dialog.cancel();
									}
							}).setNegativeButton("cancel",new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int whichButton) {
										dialog.cancel();
										}}).create();
										dialog.show();
										reader.close();
				bufferedReader.close();
				return true;}
				}
				reader.close();
				bufferedReader.close();
				return false;
				} catch (Exception e) {
			      Log.e(TAG,"process Runtime error!!");
					  e.printStackTrace();
				}
				}	
	 return false;
	}
	
	private String formatStateContent() {
		StringBuilder sb = new StringBuilder();
		sb.append("enable:").append(mStartTest).append("\n");
		sb.append("currenttime:").append(mCurrentCount).append("\n");
		sb.append("maxtime:").append(mMaxTestCount).append("\n");
		sb.append("wipeall:").append(mIsWipeAll?"1":"0").append("\n");
		sb.append("eraseflash:").append(mIsEraseFlash?"1":"0").append("\n");
		sb.append("checksys:").append(mIsCheckSys?"1":"0").append("\n");
		return sb.toString();
	}
	
	private void writeRecoveryState(String content) {
		FileOutputStream fos = null;
		File file;
		if (/*mIsEraseFlash ||*/ mIsWipeAll /*|| !UMSstate()*/ ) {
            file = new File(RECOVERY_STATE_FILE_TF);
		} else {
			file = new File(RECOVERY_STATE_FILE);
		}
		
		if (file != null && !file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			fos = new FileOutputStream(file);
			fos.write(content.getBytes());
                        fos.flush();
			fos.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException ie) {
			ie.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		stopTest();
    mWakeLock.release();
	}
	
	
//===========================for A10 test===========================//	
    /**
     * Reboot into the recovery system with the supplied argument.
     * @param arg to pass to the recovery utility.
     * @throws IOException if something goes wrong.
     */
    private static File RECOVERY_DIR = new File("/cache/recovery");
    private static File COMMAND_FILE = new File(RECOVERY_DIR, "command");
    
    private static void bootCommand(Context context, String arg) throws IOException {
        RECOVERY_DIR.mkdirs();  // In case we need it
        COMMAND_FILE.delete();  // In case it's not writable

        FileWriter command = new FileWriter(COMMAND_FILE);
        try {
            command.write(arg);
            command.write("\n");
        } finally {
            command.close();
        }

        // Having written the command file, go ahead and reboot
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot("recovery");

        throw new IOException("Reboot failed (no permissions?)");
    }
}
