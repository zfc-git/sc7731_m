package com.cghs.stresstest;

import com.cghs.stresstest.log.LogSetting;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.widget.Toast;
import android.util.Log;

public class StressTestActivity extends Activity {

	private ListView mListView; 
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_stress_test);
		mListView = (ListView) findViewById(R.id.listview);
		mListView.setAdapter(new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, TestItems.testItems));
		mListView.setOnItemClickListener(new onTestItemClickListener());
                Log.d("StressTest","apk-version:"+getVersion());
                Toast.makeText(this, getVersion(), Toast.LENGTH_LONG).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_stress_test, menu);
		menu.add(0, 0, 0, R.string.log_setting);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			/*Intent service = new Intent(this, com.cghs.stresstest.log.LogService.class);
			startService(service);*/
			Intent intent = new Intent(this, LogSetting.class);
			startActivity(intent);
			break;
		

		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private class onTestItemClickListener implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			try {
				String className = "com.cghs.stresstest.test."+TestItems.testItems[position];
				Intent intent;
				try {
					intent = new Intent(StressTestActivity.this, Class.forName(className).newInstance().getClass());
					startActivity(intent);
				} catch (InstantiationException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}
	
    public String getVersion() {
      try {
          PackageManager manager = this.getPackageManager();
          PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
          String version = info.versionName;
         return version;
     } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }	
	
	

}
