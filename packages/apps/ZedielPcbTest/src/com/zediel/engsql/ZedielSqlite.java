package com.zediel.engsql;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class ZedielSqlite {
	private static final String TAG = "ZedielSqlite";
	private Context mContext;
	private SQLiteDatabase mSqLiteDatabase = null;
	
	private static String ENG_ENGTEST_DB_PATH = "/productinfo/engtest.db";
	 public static final int ENG_ENGTEST_VERSION = 1;
	 public static final String ENG_STRING2INT_TABLE = "str2int";
	 public static final String ENG_GROUPID_VALUE = "groupid";
	 public static final String ENG_STRING2INT_NAME = "name";
	 public static final String ENG_STRING2INT_VALUE = "value";
	 public static final int DEFAULT = 2;
	 
	private static ZedielSqlite mZedielSqlite;
	
	public static synchronized ZedielSqlite getInstance(Context context){
		if(mZedielSqlite == null){
			mZedielSqlite = new ZedielSqlite(context);
		}
		return mZedielSqlite;
	}
	private ZedielSqlite(Context context){
		mContext = context;
		File file = new File(ENG_ENGTEST_DB_PATH);
		Process p = null;
		DataOutputStream os = null;
		try {
			p = Runtime.getRuntime().exec("chmod 777 productinfo");
			os = new DataOutputStream(p.getOutputStream());
			BufferedInputStream err = new BufferedInputStream(p.getErrorStream());
			BufferedReader br = new BufferedReader(new InputStreamReader(err));
			Log.v(TAG,"os = "+br.readLine());
			Runtime.getRuntime().exec("chmod 777"+ file.getAbsolutePath());
			int status = p.waitFor();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			if(os != null){
				try {
					os.close();
					p.destroy();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				p.destroy();
			}
		}
		GuanhongDatabaseHelper guanhongdatabaseHelper = new GuanhongDatabaseHelper(mContext);
		mSqLiteDatabase = guanhongdatabaseHelper.getWritableDatabase();
		
	}
	
	private static class GuanhongDatabaseHelper extends SQLiteOpenHelper{
		Context mContext = null;
		public GuanhongDatabaseHelper(Context context) {
			super(context, ENG_ENGTEST_DB_PATH, null, ENG_ENGTEST_VERSION);
			mContext = context;
			// TODO Auto-generated constructor stub
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			// TODO Auto-generated method stub
			db.execSQL("DROP TABLE IF EXISTS " + ENG_STRING2INT_TABLE + ";");
            db.execSQL("CREATE TABLE " + ENG_STRING2INT_TABLE + " (" + BaseColumns._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT," + ENG_GROUPID_VALUE
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + ENG_STRING2INT_NAME + " TEXT," + ENG_STRING2INT_VALUE
                    + " INTEGER NOT NULL DEFAULT 0" + ");");
            
            ContentValues cv = new ContentValues();
            cv.put(ENG_STRING2INT_NAME, "FM test");
            cv.put(ENG_STRING2INT_VALUE, String.valueOf(DEFAULT));
            long returnValue = db.insert(ENG_STRING2INT_TABLE, null, cv);
            if (returnValue == -1) {
                Log.e(TAG, "insert DB error!");
                return;
            }
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			// TODO Auto-generated method stub
			if (newVersion > oldVersion) {
                db.execSQL("DROP TABLE IF EXISTS " + ENG_STRING2INT_TABLE + ";");
                onCreate(db);
            }
		}}
}
