package com.cghs.stresstest.test;

import com.cghs.stresstest.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.text.InputType;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.os.PowerManager.WakeLock;
import android.os.storage.IMountService;
import android.os.ServiceManager;
import android.os.Environment;
import com.cghs.stresstest.util.StresstestUtil;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import android.app.Dialog;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import android.app.KeyguardManager;
/*
 * Author	: huangjc
 * Date  	: 2013-05-06
 * Function	: Reboot Test
 */

public class RebootTest extends Activity implements OnClickListener {
	private final static String LOG_TAG = "RebootTest";

	private final static int MSG_REBOOT = 0;
	private final static int MSG_REBOOT_COUNTDOWN = 1;
	private final static int MSG_REBOOT_STARTCOUNT = 2;
	
	private final static String SDCARD_PATH = "/storage/sdcard0";
	
	private final static int DELAY_TIME = 5;// x1000ms
	private final int REBOOT_OFF = 0;
	private final int REBOOT_ON = 1;

	private SharedPreferences mSharedPreferences;

	private TextView mCountTV;
	private TextView mCountdownTV;
	private TextView mMaxTV;
	private Button mStartButton;
	private Button mStopButton;
	private Button mExitBtn;
	private Button mSettingButton;
	private Button mSettingDelayButton;
	private Button mClearButton;
	private CheckBox mSdcardCheckCB;
	private CheckBox mAutoCheckSys;
	
        private WakeLock mWakeLock;
	private int mState;
	private int mCount;
	private int mCountDownTime;
	private int mMaxTimes; // max times to reboot
	private int mDelayTime; // delay time to reboot
	private boolean mIsCheckSD = false;
	private boolean mIsCheckSys = false;
	private boolean mFT = false;
	private String mSdState = null;
  private String RebootMode = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_reboot_test);
		// get the reboot flag and count.
		mSharedPreferences = getSharedPreferences("state", 0);
		mState = mSharedPreferences.getInt("reboot_flag", 0);
		mCount = mSharedPreferences.getInt("reboot_count", 0);
		mMaxTimes = mSharedPreferences.getInt("reboot_max", 0);
		mDelayTime = mSharedPreferences.getInt("reboot_delay", 10);
		mIsCheckSD = mSharedPreferences.getBoolean("check_sd", false);

		// init resource
		initRes();
                mWakeLock = ((PowerManager)getSystemService("power")).newWakeLock(PowerManager.FULL_WAKE_LOCK, "reboot test");
                mWakeLock.acquire();
		if (mState == REBOOT_ON) {
			if(mIsCheckSys){
			   Log.d("reboottest","======mIsCheckSys is true");	
			}
			if (mIsCheckSD) {
				mSdState = getSdCardState();
				if (!mSdState.equals(Environment.MEDIA_MOUNTED)) {
					TextView tv =(TextView) findViewById(R.id.sdcard_check_tv); 
					tv.setText(getString(R.string.check_sd_result)+false);
					tv.setVisibility(View.VISIBLE);
					onStopClick();
					return ;
				}
			}
			
			
			if (mMaxTimes != 0 && mMaxTimes <= mCount) {
				mState = REBOOT_OFF;
				saveSharedPreferences(mState, 0);
				saveMaxTimes(0);
				updateBtnState();
				mCountTV.setText(mCountTV.getText()+" TEST FINISH!");
				
			}else if(mIsCheckSys){
				 if(isRebootError()){
					mState = REBOOT_OFF;
					saveSharedPreferences(mState, 0);
					saveMaxTimes(0);
					updateBtnState();
					mCountTV.setText(mCountTV.getText()+" Test fail for error!");
				 }else {
				   mCountDownTime = mDelayTime;//DELAY_TIME / 1000;
				   mHandler.sendEmptyMessage(MSG_REBOOT_STARTCOUNT);
			   }
		    }else {
				mCountDownTime = mDelayTime;//DELAY_TIME / 1000;
				mHandler.sendEmptyMessage(MSG_REBOOT_STARTCOUNT);
			}
		}
	}

	private void initRes() {
		mCountTV = (TextView) findViewById(R.id.count_tv);
		mCountTV.setText(getString(R.string.reboot_time) + mCount);
		mMaxTV = (TextView) findViewById(R.id.maxtime_tv);
		if (mMaxTimes == 0) {
			mMaxTV.setText(getString(R.string.reboot_maxtime)
					+ getString(R.string.not_setting));
		} else {
			mMaxTV.setText(getString(R.string.reboot_maxtime) + mMaxTimes);
		}

		mStartButton = (Button) findViewById(R.id.start_btn);
		mStartButton.setOnClickListener(this);

		mStopButton = (Button) findViewById(R.id.stop_btn);
		mStopButton.setOnClickListener(this);
		
		mExitBtn = (Button) findViewById(R.id.exit_btn);
		mExitBtn.setOnClickListener(this);

		mSettingButton = (Button) findViewById(R.id.setting_btn);
		mSettingButton.setOnClickListener(this);

                mSettingDelayButton = (Button) findViewById(R.id.setting_delay_btn);
                mSettingDelayButton.setOnClickListener(this);

		mClearButton = (Button) findViewById(R.id.clear_btn);
		mClearButton.setOnClickListener(this);
		
		mSdcardCheckCB = (CheckBox) findViewById(R.id.sdcard_check_cb);
		mSdcardCheckCB.setChecked(mIsCheckSD);
		mSdcardCheckCB.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					String state = getSdCardState();
					if (!state.equals(Environment.MEDIA_MOUNTED)) {
						Toast.makeText(RebootTest.this, "Please insert sdcard!", Toast.LENGTH_LONG).show();
						buttonView.setChecked(false);
					}
				}
			}
		});
		
		mAutoCheckSys = (CheckBox) findViewById(R.id.is_check_sys);
		mAutoCheckSys.setChecked(mIsCheckSys);
		mAutoCheckSys.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
       // TODO Auto-generated method stub
			}
		});

		updateBtnState();

		mCountdownTV = (TextView) findViewById(R.id.countdown_tv);
	}

	private void reboot() {
		// save state
		saveSharedPreferences(mState, mCount + 1);

		// 重启
		/*
		 * String str = "重启"; try { str = runCmd("reboot", "/system/bin"); }
		 * catch (IOException e) { e.printStackTrace(); }
		 */
		/*
		 * Intent reboot = new Intent(Intent.ACTION_REBOOT);
		 * reboot.putExtra("nowait", 1); reboot.putExtra("interval", 1);
		 * reboot.putExtra("window", 0); sendBroadcast(reboot);
		 */
		PowerManager pManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		pManager.reboot(null);
		System.out.println("execute cmd--> reboot\n" + "重启");
	}

	private void saveSharedPreferences(int flag, int count) {
		SharedPreferences.Editor edit = mSharedPreferences.edit();
		edit.putInt("reboot_flag", flag);
		edit.putInt("reboot_count", count);
		edit.putBoolean("check_sd", mIsCheckSD);
		edit.putBoolean("check_sys", mIsCheckSys);
		edit.commit();
	}
	
	private void saveMaxTimes(int max) {
		SharedPreferences.Editor edit = mSharedPreferences.edit();
		edit.putInt("reboot_max", max);
		edit.commit();
	}

        private void saveDelayTimes(int time) {
                SharedPreferences.Editor edit = mSharedPreferences.edit();
                edit.putInt("reboot_delay", time);
                edit.commit();
                Toast.makeText(this,"Set delay time:"+time+"s",Toast.LENGTH_LONG ).show();
        }

	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REBOOT:
                                Log.d("reboottest","===MSG_REBOOT mState = "+mState);
				if (mState == 1){
                                Toast.makeText(RebootTest.this, "Start reboot now!!",Toast.LENGTH_LONG).show(); 
					reboot();
                                 }
				break;

			case MSG_REBOOT_COUNTDOWN:
				if (mState == 0)
					return;
                       //                 Log.d("hjc","==MSG_REBOOT_COUNTDOWN===beforce mCountDownTime:"+mCountDownTime);
				if (mCountDownTime != 0) {
					mCountdownTV.setText(getString(R.string.reboot_countdown)
							+ mCountDownTime);
					mCountdownTV.setVisibility(View.VISIBLE);
					mCountDownTime--;
                         //               Log.d("hjc","==MSG_REBOOT_COUNTDOWN===mCountDownTime====="+mCountDownTime);
					sendEmptyMessageDelayed(MSG_REBOOT_COUNTDOWN, 1000);
				} else {
				 if(mIsCheckSys){
				  if(isSystemError()){
					mState = REBOOT_OFF;
					saveSharedPreferences(mState, 0);
				    saveMaxTimes(0);
					updateBtnState();
				    mCountTV.setText(mCountTV.getText()+" Test fail for error!");
							 
				 }else{
					mCountdownTV.setText(getString(R.string.reboot_countdown)
							+ mCountDownTime);
					mCountdownTV.setVisibility(View.VISIBLE);
					sendEmptyMessage(MSG_REBOOT);
                           //             Log.d("hjc","===CheckSys====send MSG_REBOOT==now");
				   }
				}else{
					mCountdownTV.setText(getString(R.string.reboot_countdown)
							+ mCountDownTime);
					mCountdownTV.setVisibility(View.VISIBLE);
                             //           Log.d("hjc","===UnCheckSys====send MSG_REBOOT==now====mCountDownTime:"+mCountDownTime);
					sendEmptyMessage(MSG_REBOOT);
				}
				
				}

				break;
			case MSG_REBOOT_STARTCOUNT:
		//		mWakeLock = ((PowerManager)getSystemService("power")).newWakeLock(PowerManager.FULL_WAKE_LOCK, "reboot test");
		//		mWakeLock.acquire();
				sendEmptyMessage(MSG_REBOOT_COUNTDOWN);
				break;

			default:
				break;
			}
		};
	};

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start_btn:
			onStartClick();
			break;
		case R.id.stop_btn:
			onStopClick();
			break;
		case R.id.exit_btn:
			finish();
			break;
		case R.id.setting_btn:
			onSettingClick();
			break;
                case R.id.setting_delay_btn:
                        onDelayTimeClick();
                        break;
		case R.id.clear_btn:
			onClearSetting();
			break;
		default:
			break;
		}

	}

       @Override
        protected void onDestroy() {
                super.onDestroy();
         //       stopTest();
               if(mWakeLock != null && mWakeLock.isHeld())
                mWakeLock.release();
        }

	private void onStartClick() {
		mFT = true;
                  String MessageString = getString(R.string.reboot_dialog_msg,mDelayTime); 
		new AlertDialog.Builder(RebootTest.this)
				.setTitle(R.string.reboot_dialog_title)
				.setMessage(MessageString)
				.setPositiveButton(R.string.dialog_ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								mState = REBOOT_ON;
								mIsCheckSD = mSdcardCheckCB.isChecked();
								mIsCheckSys = mAutoCheckSys.isChecked();
								mCountDownTime = mDelayTime;//DELAY_TIME / 1000; // ms->s
								updateBtnState();
								mHandler.sendEmptyMessage(MSG_REBOOT_STARTCOUNT);
							}
						})
				.setNegativeButton(R.string.dialog_cancel,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.cancel();
							}
						}).show();
	}

	private void onStopClick() {
		mState = REBOOT_OFF;
		mHandler.removeMessages(MSG_REBOOT);
		mCountdownTV.setVisibility(View.INVISIBLE);
		updateBtnState();
		mIsCheckSD = false;
		mIsCheckSys = false;
		saveSharedPreferences(mState, 0);
		if(mWakeLock != null && mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}
	
	private void onSettingClick() {
		final EditText editText = new EditText(this);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		new AlertDialog.Builder(this)
			.setTitle(R.string.btn_setting)
			.setView(editText)
			.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if(!editText.getText().toString().trim().equals("")) {
						mMaxTimes = Integer.valueOf(editText.getText().toString());
						saveMaxTimes(mMaxTimes);
						mMaxTV.setText(getString(R.string.reboot_maxtime)+mMaxTimes);
					}
				}
			})
			.setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
				
			}).show();
		}

	private void onClearSetting() {
		mMaxTimes = 0;
                mDelayTime = 10;
		saveMaxTimes(mMaxTimes);
                saveDelayTimes(mDelayTime);
             
		mMaxTV.setText(getString(R.string.reboot_maxtime)
				+ getString(R.string.not_setting));
                
	}
         private void onDelayTimeClick() {
                final EditText editText = new EditText(this);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                new AlertDialog.Builder(this)
                        .setTitle(R.string.btn_setting_delay)
                        .setView(editText)
                        .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                        if(!editText.getText().toString().trim().equals("")) {
                                                mDelayTime = Integer.valueOf(editText.getText().toString());
                                                saveDelayTimes(mDelayTime);
                                        }
                                }
                        })
                        .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener()
 {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                }

                        }).show();
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
					Log.e(LOG_TAG, "getBootMode fail!!!");
			}
			try {
					Thread.sleep(1000);
			} catch (InterruptedException e) {
					e.printStackTrace();
			}
			try {
	            String encoding="GBK";
	            File file=new File(filePath);
	            if(file.isFile() && file.exists()){ //判断文件是否存在
	                InputStreamReader read = new InputStreamReader(
	                new FileInputStream(file),encoding);//考虑到编码格式
	                BufferedReader bufferedReader = new BufferedReader(read);
	                String lineTxt = null;
	                while((lineTxt = bufferedReader.readLine()) != null){
	                	Log.d(LOG_TAG,lineTxt);
	                	int p1 = lineTxt.indexOf("(");
	                	int p2 = lineTxt.indexOf(")");
	                	RebootMode = lineTxt.substring(p1+1, p2);
	                		Toast.makeText(this,RebootMode,Toast.LENGTH_LONG ).show();
	                	
	                }
	                read.close();
	    }else{
	        Log.e(LOG_TAG,"not find the mnt/sdcard/boot_mode.txt");
	    }
	    } catch (Exception e) {
	       Log.e(LOG_TAG,"read error!!");
	        e.printStackTrace();
	    }
	}
	private boolean isRebootError(){
		SavedRebootMode();
		
		if(RebootMode!=null){
                  if(Integer.valueOf(RebootMode) == 7){
			   Dialog dialog = new AlertDialog.Builder(
					   this)
			.setTitle("REBOOT TEST FAIL")
			.setMessage("It's reboot fail by panic,please analysis last_log")
			.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					 dialog.cancel();
					}
			}).setNegativeButton("Cancle",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					dialog.cancel();
			}}).create();
			dialog.show();
				return true;

		   }else if(Integer.valueOf(RebootMode) == 8){
			   Dialog dialog = new AlertDialog.Builder(
					   this)
			.setTitle("REBOOT TEST FAIL")
			.setMessage("It's reboot fail by watchdog,please analysis last_log")
			.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					dialog.cancel();
				}}).setNegativeButton("Cancle",new DialogInterface.OnClickListener() {
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
				
		while((lineText = bufferedReader.readLine()) != null){
			//	Log.d("--hjc","-------------->>lineTxt:"+lineText);
         if(lineText.indexOf("Force finishing activity")!=-1||lineText.indexOf("backtrace:")!=-1){
				 Log.d("reboot test","------lineTxt:"+lineText);
         			   Dialog dialog = new AlertDialog.Builder(
					   this)
			.setTitle("REBOOT TEST FAIL")
			.setMessage("System has some error,please analysis logcat")
			.setPositiveButton("Ok",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int whichButton) {
					 dialog.cancel();
					}
			}).setNegativeButton("Cancle",new DialogInterface.OnClickListener() {
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
			Log.e(LOG_TAG,"process Runtime error!!");
		  e.printStackTrace();
		}
		} 
	return false;
	
	}
	
	private void updateBtnState() {
		mStartButton.setEnabled(mState == REBOOT_OFF);
		mClearButton.setEnabled(mState == REBOOT_OFF);
		mSettingButton.setEnabled(mState == REBOOT_OFF);
		mSettingDelayButton.setEnabled(mState == REBOOT_OFF);
		mStopButton.setEnabled(mState == REBOOT_ON);
	}
	
	public static String getSdCardState() {
        try {
        	IMountService mMntSvc = null;
            if (mMntSvc == null) {
                mMntSvc = IMountService.Stub.asInterface(ServiceManager
                                                         .getService("mount"));
            }
            return mMntSvc.getVolumeState(SDCARD_PATH);
        } catch (Exception rex) {
            return Environment.MEDIA_REMOVED;
        }

    }
	

}
