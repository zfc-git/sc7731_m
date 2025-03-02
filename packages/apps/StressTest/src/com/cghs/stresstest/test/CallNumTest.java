package com.cghs.stresstest.test;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.cghs.stresstest.R;
import com.cghs.stresstest.util.NativeInputManager;

import android.os.ServiceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import com.android.internal.telephony.ITelephony;

public class CallNumTest extends StressBase{
	public static final String TAG = "CallNumTest";
	
	private TextView mCallNumTv;
	private TextView mCallMaxTimesTv;
	private TextView mCallTimesTv;
	private Button mSetNumBtn;
	
	private String mCallNumber = "10086";
	private boolean isTalking = false;
	private WakeLock mWakeLock;
	
	private exPhoneCallListener myPhoneCallListener = new exPhoneCallListener();
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call_num_test);
		setDefaultBtnId(R.id.start_btn, R.id.stop_btn, R.id.exit_btn, R.id.maxtime_btn);
		
		initRes();
		mWakeLock = ((PowerManager)getSystemService("power")).newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
		mWakeLock.acquire();
		
		TelephonyManager tm = (TelephonyManager) this
				.getSystemService(Context.TELEPHONY_SERVICE);

		tm.listen(myPhoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);
	}
	
	private void initRes() {
		mCallNumTv = (TextView) findViewById(R.id.callnumber_tv);
		mCallMaxTimesTv = (TextView) findViewById(R.id.maxtime_tv);
		mCallTimesTv = (TextView) findViewById(R.id.testtime_tv);
		mSetNumBtn = (Button) findViewById(R.id.callnumber_btn);
		mSetNumBtn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showSetNumDialog();
			}
		});
		
		updateMaxTV();
		updateNumTv();
	}
	
	@Override
	public void onStartClick() {
		handler.postDelayed(runnable, 5000);
		mCallTimesTv.setVisibility(View.INVISIBLE);
		mCurrentCount = 0;
		isRunning = true; 
	}

	@Override
	public void onStopClick() {
		isRunning = false;
		handler.removeCallbacks(runnable);
	}

	@Override
	public void onSetMaxClick() {
	}
	
	@Override
	public void updateMaxTV() {
		super.updateMaxTV();
		mCallMaxTimesTv.setText(getString(R.string.call_num_count_tv)+mMaxTestCount);
	}
	
	public void updateNumTv() {
		mCallNumTv.setText(getString(R.string.call_num_tv)+mCallNumber);
	}
	
	public void showSetNumDialog() {
		final EditText editText = new EditText(this);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		new AlertDialog.Builder(this)
				.setTitle(R.string.btn_setting)
				.setView(editText)
				.setPositiveButton(android.R.string.ok,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								if (!editText.getText().toString().trim()
										.equals("")) {
									mCallNumber = editText.getText().toString();
									updateNumTv();
								}
							}
						})
				.setNegativeButton(android.R.string.cancel,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.cancel();
							}

						}).show();
	}
	
	Handler handler = new Handler();
	Runnable runnable = new Runnable() {
		// @Override
		public void run() {
			// TODO Auto-generated method stub
			boolean handled = false;
			boolean hungUp = false;
			// key repeats are generated by the window manager, and we don't see
			// them
			// here, so unless the driver is doing something it shouldn't be, we
			// know
			// this is the real press event.
			if (isTalking) {
				ITelephony phoneServ = ITelephony.Stub.asInterface(ServiceManager
						.checkService(Context.TELEPHONY_SERVICE));
				if (phoneServ != null) {
					try {
						//success
						mCurrentCount++;
						mCallTimesTv.setText(getString(R.string.already_test_time)+ mCurrentCount);
						mCallTimesTv.setVisibility(View.VISIBLE);
						handled = hungUp = phoneServ.endCall();
						
					} catch (RemoteException ex) {
						//failed
						Log.w(TAG, "ITelephony threw RemoteException" + ex);
					}
				} else {
					Log.w(TAG, "!!! Unable to find ITelephony interface !!!");
				}
			} else {
				Intent myIntentDial = new Intent("android.intent.action.CALL",
						Uri.parse("tel:"+mCallNumber));
				myIntentDial.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(myIntentDial);
			}
		}
	};
	
	public class exPhoneCallListener extends PhoneStateListener {

		public void onCallStateChanged(int state, String incomingNumber) {
			switch (state) {

			case TelephonyManager.CALL_STATE_IDLE:
				Log.i(TAG, "CALL_STATE_IDLE");
				
				if (isRunning) {
					if (mMaxTestCount == 0 || mCurrentCount < mMaxTestCount) {
						isTalking = false;
						handler.postDelayed(runnable, 10000);
					} else {
						isTalking = false;
						isRunning = false;
					}
					NativeInputManager.sendKeyDownUpSync(4);
				}
				break;

			case TelephonyManager.CALL_STATE_OFFHOOK:
				Log.i(TAG, "CALL_STATE_OFFHOOK");
				handler.postDelayed(runnable, 10000);
				isTalking = true;
				break;

			case TelephonyManager.CALL_STATE_RINGING:
				Log.i(TAG, "CALL_STATE_RINGING");
				break;
			default:
				break;
			}
			super.onCallStateChanged(state, incomingNumber);
		}
	}
	
	protected void onDestroy() {
		TelephonyManager tm = (TelephonyManager) this
				.getSystemService(Context.TELEPHONY_SERVICE);

		tm.listen(myPhoneCallListener, PhoneStateListener.LISTEN_NONE);

		handler.removeCallbacks(runnable);
		mWakeLock.release();
		super.onDestroy();
	}

}
