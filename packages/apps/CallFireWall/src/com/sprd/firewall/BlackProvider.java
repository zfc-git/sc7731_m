
package com.sprd.firewall;


import com.sprd.firewall.db.BlackColumns;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class BlackProvider extends ContentProvider {
    private static final String TAG = "BlackProvider";

    private SQLiteDatabase sqlDB;

    private DatabaseHelper dbHelper;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "block.db";

        private static final int DATABASE_VERSION = 1;

        public interface Tables {
            public static final String BLACK_MUMBERS = "black_mumbers";

            public static final String BLOCK_RECORDED = "block_recorded";

            public static final String SMS_BLOCK_RECORDED = "sms_block_recorded";
        }

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.BLACK_MUMBERS + " (" + BlackColumns.BlackMumber._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + BlackColumns.BlackMumber.MUMBER_VALUE + " TEXT, "
                    + BlackColumns.BlackMumber.BLOCK_TYPE + " INTEGER, "
                    + BlackColumns.BlackMumber.NOTES + " TEXT, "
                    + BlackColumns.BlackMumber.NAME + " TEXT, "
                    + BlackColumns.BlackMumber.MIN_MATCH + " TEXT "+ ");");

            db.execSQL("CREATE TABLE " + Tables.BLOCK_RECORDED + " ("
                    + BlackColumns.BlockRecorder._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + BlackColumns.BlockRecorder.MUMBER_VALUE + " TEXT, "
                    + BlackColumns.BlockRecorder.CALL_TYPE + " INTEGER, "
                    + BlackColumns.BlockRecorder.BLOCK_DATE + " LONG, "
                    + BlackColumns.BlockRecorder.NAME + " TEXT " + ");");

            db.execSQL("CREATE TABLE " + Tables.SMS_BLOCK_RECORDED + " ("
                    + BlackColumns.SmsBlockRecorder._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + BlackColumns.SmsBlockRecorder.MUMBER_VALUE + " TEXT, "
                    + BlackColumns.SmsBlockRecorder.BLOCK_SMS_CONTENT + " TEXT, "
                    + BlackColumns.SmsBlockRecorder.BLOCK_DATE + " LONG, "
                    + BlackColumns.SmsBlockRecorder.NAME + " TEXT " + ");");
        }


        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + Tables.BLACK_MUMBERS);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.BLOCK_RECORDED);
            db.execSQL("DROP TABLE IF EXISTS " + Tables.SMS_BLOCK_RECORDED);
            onCreate(db);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        int flag;
        sqlDB = dbHelper.getWritableDatabase();
        flag = sqlDB.delete(args.table, selection, selectionArgs);
        if (uri != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return flag;
    }

    @Override
    public String getType(Uri uri) {
        SqlArguments args = new SqlArguments(uri, null, null);
        if (TextUtils.isEmpty(args.where)) {
            return "vnd.android.cursor.dir/" + args.table;
        } else {
            return "vnd.android.cursor.item/" + args.table;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SqlArguments args = new SqlArguments(uri);
        sqlDB = dbHelper.getWritableDatabase(); // called every time you need
                                                // write to database
        long rowId = sqlDB.insert(args.table, "", values);
        if (rowId > 0) {
            Uri rowUri = null;
            if (args.table.equals("block_recorded")) {
                rowUri = ContentUris.appendId(BlackColumns.BlockRecorder.CONTENT_URI.buildUpon(),
                        rowId).build();
            } else if (args.table.equals("black_mumbers")) {
                rowUri = ContentUris.appendId(BlackColumns.BlackMumber.CONTENT_URI.buildUpon(),
                        rowId).build();
            } else if (args.table.equals("sms_block_recorded")) {
                rowUri = ContentUris.appendId(
                        BlackColumns.SmsBlockRecorder.CONTENT_URI.buildUpon(), rowId).build();
            }
            if (rowUri != null) {
                getContext().getContentResolver().notifyChange(rowUri, null);
            }
            return rowUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public boolean onCreate() {
        try {
            dbHelper = new DatabaseHelper(getContext());
            sqlDB = dbHelper.getWritableDatabase();
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot start provider", e);
        }
        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        /* SPRD: add for bug492818 @{ */
        if ("'".equals(selection)) {
            Log.d(TAG, "querying projection: " + projection + " , selection:" + selection);
            return null;
        }
        if (projection != null && projection.length == 1 && "'".equals(projection[0])) {
            Log.d(TAG, "querying projection: " + projection[0] + " , selection:" + selection);
            return null;
        }
        /* @} */
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        SQLiteQueryBuilder qb = null;
        SQLiteDatabase db = null;
        try {
            qb = new SQLiteQueryBuilder();
            db = dbHelper.getReadableDatabase();
        } catch (SQLiteCantOpenDatabaseException e) {
            Log.e(TAG, "query: catnot open database", e);
            return null;
        }

        qb.setTables(args.table);
        Cursor c = qb.query(db, projection, selection, null, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SqlArguments args = new SqlArguments(uri, selection, selectionArgs);
        sqlDB = dbHelper.getWritableDatabase();
        if (uri != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return sqlDB.update(args.table, values, selection, selectionArgs);
    }

    static class SqlArguments {
        public final String table;

        public final String where;

        public final String[] args;

        SqlArguments(Uri url, String where, String[] args) {
            if (url.getPathSegments().size() == 1) {
                this.table = url.getPathSegments().get(0);
                this.where = where;
                this.args = args;
            } else if (url.getPathSegments().size() != 2) {
                throw new IllegalArgumentException("Invalid URI: " + url);
            } else if (!TextUtils.isEmpty(where)) {
                throw new UnsupportedOperationException("WHERE clause not supported: " + url);
            } else {
                this.table = url.getPathSegments().get(0);
                this.where = "_id=" + ContentUris.parseId(url);
                this.args = null;
            }
        }

        SqlArguments(Uri url) {
            if (url.getPathSegments().size() == 1) {
                table = url.getPathSegments().get(0);
                where = null;
                args = null;
            } else {
                throw new IllegalArgumentException("Invalid URI: " + url);
            }
        }
    }
}
