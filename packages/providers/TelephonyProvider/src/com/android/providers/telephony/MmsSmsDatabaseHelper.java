/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.providers.telephony;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Mms.Rate;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.os.SystemProperties;
import android.util.Log;

import com.android.providers.telephony.ext.simmessage.ICCMessageManager;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.PduHeaders;

import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

public class MmsSmsDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MmsSmsDatabaseHelper";

    private static final String SMS_UPDATE_THREAD_READ_BODY =
                        "  UPDATE threads SET read = " +
                        "    CASE (SELECT COUNT(*)" +
                        "          FROM sms" +
                        "          WHERE " + Sms.READ + " = 0" +
                        "            AND " + Sms.THREAD_ID + " = threads._id)" +
                        "      WHEN 0 THEN 1" +
                        "      ELSE 0" +
                        "    END" +
                        "  WHERE threads._id = new." + Sms.THREAD_ID + "; ";

    private static final String UPDATE_THREAD_COUNT_ON_NEW =
                        "  UPDATE threads SET message_count = " +
                        "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                        "      ON threads._id = " + Sms.THREAD_ID +
                        "      WHERE " + Sms.THREAD_ID + " = new.thread_id" +
                        "        AND sms." + Sms.TYPE + " != 3) + " +
                        "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                        "      ON threads._id = " + Mms.THREAD_ID +
                        "      WHERE " + Mms.THREAD_ID + " = new.thread_id" +
                        "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                        "        AND " + Mms.MESSAGE_BOX + " != 3) " +
                        "  WHERE threads._id = new.thread_id; ";

    private static final String UPDATE_THREAD_COUNT_ON_OLD =
                        "  UPDATE threads SET message_count = " +
                        "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                        "      ON threads._id = " + Sms.THREAD_ID +
                        "      WHERE " + Sms.THREAD_ID + " = old.thread_id" +
                        "        AND sms." + Sms.TYPE + " != 3) + " +
                        "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                        "      ON threads._id = " + Mms.THREAD_ID +
                        "      WHERE " + Mms.THREAD_ID + " = old.thread_id" +
                        "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                        "        AND " + Mms.MESSAGE_BOX + " != 3) " +
                        "  WHERE threads._id = old.thread_id; ";

    private static final String SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE =
                        "BEGIN" +
                        "  UPDATE threads SET" +
                        "    date = (strftime('%s','now') * 1000), " +
                        "    snippet = new." + Sms.BODY + ", " +
                        "    snippet_cs = 0" +
                        "  WHERE threads._id = new." + Sms.THREAD_ID + "; " +
                        UPDATE_THREAD_COUNT_ON_NEW +
                        SMS_UPDATE_THREAD_READ_BODY +
                        "END;";

    private static final String PDU_UPDATE_THREAD_CONSTRAINTS =
                        "  WHEN new." + Mms.MESSAGE_TYPE + "=" +
                        PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF +
                        "    OR new." + Mms.MESSAGE_TYPE + "=" +
                        PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND +
                        "    OR new." + Mms.MESSAGE_TYPE + "=" +
                        PduHeaders.MESSAGE_TYPE_SEND_REQ + " ";

    // When looking in the pdu table for unread messages, only count messages that
    // are displayed to the user. The constants are defined in PduHeaders and could be used
    // here, but the string "(m_type=132 OR m_type=130 OR m_type=128)" is used throughout this
    // file and so it is used here to be consistent.
    //     m_type=128   = MESSAGE_TYPE_SEND_REQ
    //     m_type=130   = MESSAGE_TYPE_NOTIFICATION_IND
    //     m_type=132   = MESSAGE_TYPE_RETRIEVE_CONF
    private static final String PDU_UPDATE_THREAD_READ_BODY =
                        "  UPDATE threads SET read = " +
                        "    CASE (SELECT COUNT(*)" +
                        "          FROM " + MmsProvider.TABLE_PDU +
                        "          WHERE " + Mms.READ + " = 0" +
                        "            AND " + Mms.THREAD_ID + " = threads._id " +
                        "            AND (m_type=132 OR m_type=130 OR m_type=128)) " +
                        "      WHEN 0 THEN 1" +
                        "      ELSE 0" +
                        "    END" +
                        "  WHERE threads._id = new." + Mms.THREAD_ID + "; ";

    private static final String PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE =
                        "BEGIN" +
                        "  UPDATE threads SET" +
                        "    date = (strftime('%s','now') * 1000), " +
                        "    snippet = new." + Mms.SUBJECT + ", " +
                        "    snippet_cs = new." + Mms.SUBJECT_CHARSET +
                        "  WHERE threads._id = new." + Mms.THREAD_ID + "; " +
                        UPDATE_THREAD_COUNT_ON_NEW +
                        PDU_UPDATE_THREAD_READ_BODY +
                        "END;";

    private static final String UPDATE_THREAD_SNIPPET_SNIPPET_CS_ON_DELETE =
                        "  UPDATE threads SET snippet = " +
                        "   (SELECT snippet FROM" +
                        "     (SELECT date * 1000 AS date, sub AS snippet, thread_id FROM pdu" +
                        "      UNION SELECT date, body AS snippet, thread_id FROM sms)" +
                        "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                        "  WHERE threads._id = OLD.thread_id; " +
                        "  UPDATE threads SET snippet_cs = " +
                        "   (SELECT snippet_cs FROM" +
                        "     (SELECT date * 1000 AS date, sub_cs AS snippet_cs, thread_id FROM pdu" +
                        "      UNION SELECT date, 0 AS snippet_cs, thread_id FROM sms)" +
                        "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                        "  WHERE threads._id = OLD.thread_id; ";


    // When a part is inserted, if it is not text/plain or application/smil
    // (which both can exist with text-only MMSes), then there is an attachment.
    // Set has_attachment=1 in the threads table for the thread in question.
    private static final String PART_UPDATE_THREADS_ON_INSERT_TRIGGER =
                        "CREATE TRIGGER update_threads_on_insert_part " +
                        " AFTER INSERT ON part " +
                        " WHEN new.ct != 'text/plain' AND new.ct != 'application/smil' " +
                        " BEGIN " +
                        "  UPDATE threads SET has_attachment=1 WHERE _id IN " +
                        "   (SELECT pdu.thread_id FROM part JOIN pdu ON pdu._id=part.mid " +
                        "     WHERE part._id=new._id LIMIT 1); " +
                        " END";

    // When the 'mid' column in the part table is updated, we need to run the trigger to update
    // the threads table's has_attachment column, if the part is an attachment.
    private static final String PART_UPDATE_THREADS_ON_UPDATE_TRIGGER =
                        "CREATE TRIGGER update_threads_on_update_part " +
                        " AFTER UPDATE of " + Part.MSG_ID + " ON part " +
                        " WHEN new.ct != 'text/plain' AND new.ct != 'application/smil' " +
                        " BEGIN " +
                        "  UPDATE threads SET has_attachment=1 WHERE _id IN " +
                        "   (SELECT pdu.thread_id FROM part JOIN pdu ON pdu._id=part.mid " +
                        "     WHERE part._id=new._id LIMIT 1); " +
                        " END";


    // When a part is deleted (with the same non-text/SMIL constraint as when
    // we set has_attachment), update the threads table for all threads.
    // Unfortunately we cannot update only the thread that the part was
    // attached to, as it is possible that the part has been orphaned and
    // the message it was attached to is already gone.
    private static final String PART_UPDATE_THREADS_ON_DELETE_TRIGGER =
                        "CREATE TRIGGER update_threads_on_delete_part " +
                        " AFTER DELETE ON part " +
                        " WHEN old.ct != 'text/plain' AND old.ct != 'application/smil' " +
                        " BEGIN " +
                        "  UPDATE threads SET has_attachment = " +
                        "   CASE " +
                        "    (SELECT COUNT(*) FROM part JOIN pdu " +
                        "     WHERE pdu.thread_id = threads._id " +
                        "     AND part.ct != 'text/plain' AND part.ct != 'application/smil' " +
                        "     AND part.mid = pdu._id)" +
                        "   WHEN 0 THEN 0 " +
                        "   ELSE 1 " +
                        "   END; " +
                        " END";

    // When the 'thread_id' column in the pdu table is updated, we need to run the trigger to update
    // the threads table's has_attachment column, if the message has an attachment in 'part' table
    private static final String PDU_UPDATE_THREADS_ON_UPDATE_TRIGGER =
                        "CREATE TRIGGER update_threads_on_update_pdu " +
                        " AFTER UPDATE of thread_id ON pdu " +
                        " BEGIN " +
                        "  UPDATE threads SET has_attachment=1 WHERE _id IN " +
                        "   (SELECT pdu.thread_id FROM part JOIN pdu " +
                        "     WHERE part.ct != 'text/plain' AND part.ct != 'application/smil' " +
                        "     AND part.mid = pdu._id);" +
                        " END";

    private static MmsSmsDatabaseHelper sInstance = null;
    private static boolean sTriedAutoIncrement = false;
    private static boolean sFakeLowStorageTest = false;     // for testing only

    static final String DATABASE_NAME = "mmssms.db";

    // sprd: 573102 start
    static final int DATABASE_VERSION = 163;
    // sprd: 573102 end

    private static Context mContext;
    private LowStorageMonitor mLowStorageMonitor;


    private MmsSmsDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        mContext = context;
        // sprd: fix for 570184 begin
        initBroadcastforSim();
        // sprd: fix for 570184 end
    }

    /**
     * Return a singleton helper for the combined MMS and SMS
     * database.
     */
    public static synchronized MmsSmsDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MmsSmsDatabaseHelper(context);
            Log.d(TAG, "MmsSmsDatabaseHelper CREATER");
        }
        return sInstance;
    }

    /**
     * Look through all the recipientIds referenced by the threads and then delete any
     * unreferenced rows from the canonical_addresses table.
     */
    private static void removeUnferencedCanonicalAddresses(SQLiteDatabase db) {
        Cursor c = db.query(MmsSmsProvider.TABLE_THREADS, new String[] { "recipient_ids" },
                null, null, null, null, null);
        if (c != null) {
            try {
                if (c.getCount() == 0) {
                    // no threads, delete all addresses
                    int rows = db.delete("canonical_addresses", null, null);
                } else {
                    // Find all the referenced recipient_ids from the threads. recipientIds is
                    // a space-separated list of recipient ids: "1 14 21"
                    HashSet<Integer> recipientIds = new HashSet<Integer>();
                    while (c.moveToNext()) {
                        String[] recips = c.getString(0).split(" ");
                        for (String recip : recips) {
                            try {
                                int recipientId = Integer.parseInt(recip);
                                recipientIds.add(recipientId);
                            } catch (Exception e) {
                            }
                        }
                    }
                    // Now build a selection string of all the unique recipient ids
                    StringBuilder sb = new StringBuilder();
                    Iterator<Integer> iter = recipientIds.iterator();
                    while (iter.hasNext()) {
                        sb.append("_id != " + iter.next());
                        if (iter.hasNext()) {
                            sb.append(" AND ");
                        }
                    }
                    if (sb.length() > 0) {
                        int rows = db.delete("canonical_addresses", sb.toString(), null);
                    }
                }
            } finally {
                c.close();
            }
        }
    }

    public static void updateThread(SQLiteDatabase db, long thread_id) {
        if (thread_id < 0) {
            updateAllThreads(db, null, null);
            return;
        }

        db.beginTransaction();
        try {
            // Delete the row for this thread in the threads table if
            // there are no more messages attached to it in either
            // the sms or pdu tables.
            int rows = db.delete(MmsSmsProvider.TABLE_THREADS,
                      "_id = ? AND _id NOT IN" +
                      "          (SELECT thread_id FROM sms " +
                      "           UNION SELECT thread_id FROM pdu)",
                      new String[] { String.valueOf(thread_id) });
            if (rows > 0) {
                // If this deleted a row, let's remove orphaned canonical_addresses and get outta here
                removeUnferencedCanonicalAddresses(db);
            } else {
                // Update the message count in the threads table as the sum
                // of all messages in both the sms and pdu tables.
                db.execSQL(
                        "  UPDATE threads SET message_count = " +
                                "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                                "      ON threads._id = " + Sms.THREAD_ID +
                                "      WHERE " + Sms.THREAD_ID + " = " + thread_id +
                                "        AND sms." + Sms.TYPE + " != 3) + " +
                                "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                                "      ON threads._id = " + Mms.THREAD_ID +
                                "      WHERE " + Mms.THREAD_ID + " = " + thread_id +
                                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                                "        AND " + Mms.MESSAGE_BOX + " != 3) " +
                                "  WHERE threads._id = " + thread_id + ";");

                // Update the date and the snippet (and its character set) in
                // the threads table to be that of the most recent message in
                // the thread.
                db.execSQL(
                "  UPDATE threads" +
                "  SET" +
                "  date =" +
                "    (SELECT date FROM" +
                "        (SELECT date * 1000 AS date, thread_id FROM pdu" +
                "         UNION SELECT date, thread_id FROM sms)" +
                "     WHERE thread_id = " + thread_id + " ORDER BY date DESC LIMIT 1)," +
                "  snippet =" +
                "    (SELECT snippet FROM" +
                "        (SELECT date * 1000 AS date, sub AS snippet, thread_id FROM pdu" +
                "         UNION SELECT date, body AS snippet, thread_id FROM sms)" +
                "     WHERE thread_id = " + thread_id + " ORDER BY date DESC LIMIT 1)," +
                "  snippet_cs =" +
                "    (SELECT snippet_cs FROM" +
                "        (SELECT date * 1000 AS date, sub_cs AS snippet_cs, thread_id FROM pdu" +
                "         UNION SELECT date, 0 AS snippet_cs, thread_id FROM sms)" +
                "     WHERE thread_id = " + thread_id + " ORDER BY date DESC LIMIT 1)" +
                "  WHERE threads._id = " + thread_id + ";");

                // Update the error column of the thread to indicate if there
                // are any messages in it that have failed to send.
                // First check to see if there are any messages with errors in this thread.
                String query = "SELECT thread_id FROM sms WHERE type=" +
                        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED +
                        " AND thread_id = " + thread_id +
                        " LIMIT 1";
                int setError = 0;
                Cursor c = db.rawQuery(query, null);
                if (c != null) {
                    try {
                        setError = c.getCount();    // Because of the LIMIT 1, count will be 1 or 0.
                    } finally {
                        c.close();
                    }
                }
                // What's the current state of the error flag in the threads table?
                String errorQuery = "SELECT error FROM threads WHERE _id = " + thread_id;
                c = db.rawQuery(errorQuery, null);
                if (c != null) {
                    try {
                        if (c.moveToNext()) {
                            int curError = c.getInt(0);
                            if (curError != setError) {
                                // The current thread error column differs, update it.
                                db.execSQL("UPDATE threads SET error=" + setError +
                                        " WHERE _id = " + thread_id);
                            }
                        }
                    } finally {
                        c.close();
                    }
                }
            }
            db.setTransactionSuccessful();
        } catch (Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
        } finally {
            db.endTransaction();
        }
    }

    public static void updateAllThreads(SQLiteDatabase db, String where, String[] whereArgs) {
        db.beginTransaction();
        try {
            if (where == null) {
                where = "";
            } else {
                where = "WHERE (" + where + ")";
            }
            String query = "SELECT _id FROM threads WHERE _id IN " +
                           "(SELECT DISTINCT thread_id FROM sms " + where + ")";
            Cursor c = db.rawQuery(query, whereArgs);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        updateThread(db, c.getInt(0));
                    }
                } finally {
                    c.close();
                }
            }
            // TODO: there are several db operations in this function. Lets wrap them in a
            // transaction to make it faster.
            // remove orphaned threads
            db.delete(MmsSmsProvider.TABLE_THREADS,
                    "_id NOT IN (SELECT DISTINCT thread_id FROM sms where thread_id NOT NULL " +
                    "UNION SELECT DISTINCT thread_id FROM pdu where thread_id NOT NULL)", null);

            // remove orphaned canonical_addresses
            removeUnferencedCanonicalAddresses(db);

            db.setTransactionSuccessful();
        } catch (Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
        } finally {
            db.endTransaction();
        }
    }

    public static int deleteOneSms(SQLiteDatabase db, int message_id) {
        int thread_id = -1;
        // Find the thread ID that the specified SMS belongs to.
        Cursor c = db.query("sms", new String[] { "thread_id" },
                            "_id=" + message_id, null, null, null, null);
        if (c != null) {
            if (c.moveToFirst()) {
                thread_id = c.getInt(0);
            }
            c.close();
        }

        // Delete the specified message.
        int rows = db.delete("sms", "_id=" + message_id, null);
        if (thread_id > 0) {
            // Update its thread.
            updateThread(db, thread_id);
        }
        return rows;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createMmsTables(db);
        createSmsTables(db);
        createCommonTables(db);
        createCommonTriggers(db);
        createMmsTriggers(db);
        createWordsTables(db);
        createIndices(db);
        Log.d(TAG, "=====wap push======onCreate=======");
        addWapPushColumnToSmsTable(db);
       // onUpgrade(db, 61, DATABASE_VERSION);

        // sprd: fdn and contacts
        // sprd: fix for telcel bug 557685 begin
        createFdnContactsFilterTable(db);
        sendEmptyMesssagingSprd(getHandler(), INIT_ALL_UPDATA_FLAG,0);
        // sprd: fix for telcel bug 557685 begin

        // sprd: fdn and contacts
    }

    private void addWapPushColumnToSmsTable(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + "sms" + " ADD COLUMN " + "expired INTEGER DEFAULT 0;");
        db.execSQL("ALTER TABLE " + "sms" + " ADD COLUMN " + "si_id" + " text;");
        db.execSQL("ALTER TABLE " + "sms" + " ADD COLUMN " + "wap_push INTEGER DEFAULT 0;");
    }

    // When upgrading the database we need to populate the words
    // table with the rows out of sms and part.
    private void populateWordsTable(SQLiteDatabase db) {
        final String TABLE_WORDS = "words";
        {
            Cursor smsRows = db.query(
                    "sms",
                    new String[] { Sms._ID, Sms.BODY },
                    null,
                    null,
                    null,
                    null,
                    null);
            try {
                if (smsRows != null) {
                    smsRows.moveToPosition(-1);
                    ContentValues cv = new ContentValues();
                    while (smsRows.moveToNext()) {
                        cv.clear();

                        long id = smsRows.getLong(0);        // 0 for Sms._ID
                        String body = smsRows.getString(1);  // 1 for Sms.BODY

                        cv.put(Telephony.MmsSms.WordsTable.ID, id);
                        cv.put(Telephony.MmsSms.WordsTable.INDEXED_TEXT, body);
                        cv.put(Telephony.MmsSms.WordsTable.SOURCE_ROW_ID, id);
                        cv.put(Telephony.MmsSms.WordsTable.TABLE_ID, 1);
                        db.insert(TABLE_WORDS, Telephony.MmsSms.WordsTable.INDEXED_TEXT, cv);
                    }
                }
            } finally {
                if (smsRows != null) {
                    smsRows.close();
                }
            }
        }

        {
            Cursor mmsRows = db.query(
                    "part",
                    new String[] { Part._ID, Part.TEXT },
                    "ct = 'text/plain'",
                    null,
                    null,
                    null,
                    null);
            try {
                if (mmsRows != null) {
                    mmsRows.moveToPosition(-1);
                    ContentValues cv = new ContentValues();
                    while (mmsRows.moveToNext()) {
                        cv.clear();

                        long id = mmsRows.getLong(0);         // 0 for Part._ID
                        String body = mmsRows.getString(1);   // 1 for Part.TEXT

                        cv.put(Telephony.MmsSms.WordsTable.ID, id);
                        cv.put(Telephony.MmsSms.WordsTable.INDEXED_TEXT, body);
                        cv.put(Telephony.MmsSms.WordsTable.SOURCE_ROW_ID, id);
                        cv.put(Telephony.MmsSms.WordsTable.TABLE_ID, 1);
                        db.insert(TABLE_WORDS, Telephony.MmsSms.WordsTable.INDEXED_TEXT, cv);
                    }
                }
            } finally {
                if (mmsRows != null) {
                    mmsRows.close();
                }
            }
        }
    }

    private void createWordsTables(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE VIRTUAL TABLE words USING FTS3 (_id INTEGER PRIMARY KEY, index_text TEXT, source_id INTEGER, table_to_use INTEGER);");

            // monitor the sms table
            // NOTE don't handle inserts using a trigger because it has an unwanted
            // side effect:  the value returned for the last row ends up being the
            // id of one of the trigger insert not the original row insert.
            // Handle inserts manually in the provider.
            db.execSQL("CREATE TRIGGER sms_words_update AFTER UPDATE ON sms BEGIN UPDATE words " +
                    " SET index_text = NEW.body WHERE (source_id=NEW._id AND table_to_use=1); " +
                    " END;");
            db.execSQL("CREATE TRIGGER sms_words_delete AFTER DELETE ON sms BEGIN DELETE FROM " +
                    "  words WHERE source_id = OLD._id AND table_to_use = 1; END;");

            populateWordsTable(db);
        } catch (Exception ex) {
            Log.e(TAG, "got exception creating words table: " + ex.toString());
        }
    }

    private void createIndices(SQLiteDatabase db) {
        createThreadIdIndex(db);
    }

    private void createThreadIdIndex(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS typeThreadIdIndex ON sms" +
            " (type, thread_id);");

           // add index  2016/01/13
           db.execSQL("CREATE INDEX IF NOT EXISTS  ThreadIdIndex ON sms" +
            " ( thread_id);");
        } catch (Exception ex) {
            Log.e(TAG, "got exception creating indices: " + ex.toString());
        }
    }

    private void createMmsTables(SQLiteDatabase db) {
        // N.B.: Whenever the columns here are changed, the columns in
        // {@ref MmsSmsProvider} must be changed to match.
        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_PDU + " (" +
                   Mms._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                   Mms.THREAD_ID + " INTEGER," +
                   Mms.DATE + " INTEGER," +
                   Mms.DATE_SENT + " INTEGER DEFAULT 0," +
                   Mms.MESSAGE_BOX + " INTEGER," +
                   Mms.READ + " INTEGER DEFAULT 0," +
                   Mms.MESSAGE_ID + " TEXT," +
                   Mms.SUBJECT + " TEXT," +
                   Mms.SUBJECT_CHARSET + " INTEGER," +
                   Mms.CONTENT_TYPE + " TEXT," +
                   Mms.CONTENT_LOCATION + " TEXT," +
                   Mms.EXPIRY + " INTEGER," +
                   Mms.MESSAGE_CLASS + " TEXT," +
                   Mms.MESSAGE_TYPE + " INTEGER," +
                   Mms.MMS_VERSION + " INTEGER," +
                   Mms.MESSAGE_SIZE + " INTEGER," +
                   Mms.PRIORITY + " INTEGER," +
                   Mms.READ_REPORT + " INTEGER," +
                   Mms.REPORT_ALLOWED + " INTEGER," +
                   Mms.RESPONSE_STATUS + " INTEGER," +
                   Mms.STATUS + " INTEGER," +
                   Mms.TRANSACTION_ID + " TEXT," +
                   Mms.RETRIEVE_STATUS + " INTEGER," +
                   Mms.RETRIEVE_TEXT + " TEXT," +
                   Mms.RETRIEVE_TEXT_CHARSET + " INTEGER," +
                   Mms.READ_STATUS + " INTEGER," +
                   Mms.CONTENT_CLASS + " INTEGER," +
                   Mms.RESPONSE_TEXT + " TEXT," +
                   Mms.DELIVERY_TIME + " INTEGER," +
                   Mms.DELIVERY_REPORT + " INTEGER," +
                   Mms.LOCKED + " INTEGER DEFAULT 0," +
                   Mms.SUBSCRIPTION_ID + " INTEGER DEFAULT "
                           + SubscriptionManager.INVALID_SUBSCRIPTION_ID + ", " +
                   Mms.SEEN + " INTEGER DEFAULT 0," +
                   Mms.CREATOR + " TEXT," +
                   Mms.TEXT_ONLY + " INTEGER DEFAULT 0" +
                   ");");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_ADDR + " (" +
                   Addr._ID + " INTEGER PRIMARY KEY," +
                   Addr.MSG_ID + " INTEGER," +
                   Addr.CONTACT_ID + " INTEGER," +
                   Addr.ADDRESS + " TEXT," +
                   Addr.TYPE + " INTEGER," +
                   Addr.CHARSET + " INTEGER);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_PART + " (" +
                   Part._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                   Part.MSG_ID + " INTEGER," +
                   Part.SEQ + " INTEGER DEFAULT 0," +
                   Part.CONTENT_TYPE + " TEXT," +
                   Part.NAME + " TEXT," +
                   Part.CHARSET + " INTEGER," +
                   Part.CONTENT_DISPOSITION + " TEXT," +
                   Part.FILENAME + " TEXT," +
                   Part.CONTENT_ID + " TEXT," +
                   Part.CONTENT_LOCATION + " TEXT," +
                   Part.CT_START + " INTEGER," +
                   Part.CT_TYPE + " TEXT," +
                   Part._DATA + " TEXT," +
                   Part.TEXT + " TEXT);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_RATE + " (" +
                   Rate.SENT_TIME + " INTEGER);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_DRM + " (" +
                   BaseColumns._ID + " INTEGER PRIMARY KEY," +
                   "_data TEXT);");

        // Restricted view of pdu table, only sent/received messages without wap pushes
        db.execSQL("CREATE VIEW " + MmsProvider.VIEW_PDU_RESTRICTED + " AS " +
                "SELECT * FROM " + MmsProvider.TABLE_PDU + " WHERE " +
                "(" + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_INBOX +
                " OR " +
                Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_SENT + ")" +
                " AND " +
                "(" + Mms.MESSAGE_TYPE + "!=" + PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND + ");");

        /* SPRD: Add this for bug 559631 @{ */
        Log.v(TAG, "**create index on part **");
        db.execSQL("CREATE INDEX index_mid ON "+ MmsProvider.TABLE_PART+"(mid);");

        db.execSQL("CREATE INDEX index_ct_type ON "+ MmsProvider.TABLE_PART+"("+Part.CT_TYPE+");");
        db.execSQL("CREATE INDEX index_threadid ON "+ MmsProvider.TABLE_PDU+"("+Mms.THREAD_ID+");");
        /* @} */

    }

    // Unlike the other trigger-creating functions, this function can be called multiple times
    // without harm.
    private void createMmsTriggers(SQLiteDatabase db) {
        // Cleans up parts when a MM is deleted.
        db.execSQL("DROP TRIGGER IF EXISTS part_cleanup");
        db.execSQL("CREATE TRIGGER part_cleanup DELETE ON " + MmsProvider.TABLE_PDU + " " +
                "BEGIN " +
                "  DELETE FROM " + MmsProvider.TABLE_PART +
                "  WHERE " + Part.MSG_ID + "=old._id;" +
                "END;");

        // Cleans up address info when a MM is deleted.
        db.execSQL("DROP TRIGGER IF EXISTS addr_cleanup");
        db.execSQL("CREATE TRIGGER addr_cleanup DELETE ON " + MmsProvider.TABLE_PDU + " " +
                "BEGIN " +
                "  DELETE FROM " + MmsProvider.TABLE_ADDR +
                "  WHERE " + Addr.MSG_ID + "=old._id;" +
                "END;");

        // Delete obsolete delivery-report, read-report while deleting their
        // associated Send.req.
        db.execSQL("DROP TRIGGER IF EXISTS cleanup_delivery_and_read_report");
        db.execSQL("CREATE TRIGGER cleanup_delivery_and_read_report " +
                "AFTER DELETE ON " + MmsProvider.TABLE_PDU + " " +
                "WHEN old." + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_SEND_REQ + " " +
                "BEGIN " +
                "  DELETE FROM " + MmsProvider.TABLE_PDU +
                "  WHERE (" + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_DELIVERY_IND +
                "    OR " + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_READ_ORIG_IND +
                ")" +
                "    AND " + Mms.MESSAGE_ID + "=old." + Mms.MESSAGE_ID + "; " +
                "END;");

        db.execSQL("DROP TRIGGER IF EXISTS update_threads_on_insert_part");
        db.execSQL(PART_UPDATE_THREADS_ON_INSERT_TRIGGER);

        db.execSQL("DROP TRIGGER IF EXISTS update_threads_on_update_part");
        db.execSQL(PART_UPDATE_THREADS_ON_UPDATE_TRIGGER);

        db.execSQL("DROP TRIGGER IF EXISTS update_threads_on_delete_part");
        db.execSQL(PART_UPDATE_THREADS_ON_DELETE_TRIGGER);

        db.execSQL("DROP TRIGGER IF EXISTS update_threads_on_update_pdu");
        db.execSQL(PDU_UPDATE_THREADS_ON_UPDATE_TRIGGER);

        // Delete pending status for a message when it is deleted.
        db.execSQL("DROP TRIGGER IF EXISTS delete_mms_pending_on_delete");
        db.execSQL("CREATE TRIGGER delete_mms_pending_on_delete " +
                   "AFTER DELETE ON " + MmsProvider.TABLE_PDU + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "  WHERE " + PendingMessages.MSG_ID + "=old._id; " +
                   "END;");

        // When a message is moved out of Outbox, delete its pending status.
        db.execSQL("DROP TRIGGER IF EXISTS delete_mms_pending_on_update");
        db.execSQL("CREATE TRIGGER delete_mms_pending_on_update " +
                   "AFTER UPDATE ON " + MmsProvider.TABLE_PDU + " " +
                   "WHEN old." + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_OUTBOX +
                   "  AND new." + Mms.MESSAGE_BOX + "!=" + Mms.MESSAGE_BOX_OUTBOX + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "  WHERE " + PendingMessages.MSG_ID + "=new._id; " +
                   "END;");

        // Insert pending status for M-Notification.ind or M-ReadRec.ind
        // when they are inserted into Inbox/Outbox.
        db.execSQL("DROP TRIGGER IF EXISTS insert_mms_pending_on_insert");
        db.execSQL("CREATE TRIGGER insert_mms_pending_on_insert " +
                   "AFTER INSERT ON pdu " +
                   "WHEN new." + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND +
                   "  OR new." + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_READ_REC_IND +
                   " " +
                   "BEGIN " +
                   "  INSERT INTO " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "    (" + PendingMessages.PROTO_TYPE + "," +
                   "     " + PendingMessages.MSG_ID + "," +
                   "     " + PendingMessages.MSG_TYPE + "," +
                   "     " + PendingMessages.ERROR_TYPE + "," +
                   "     " + PendingMessages.ERROR_CODE + "," +
                   "     " + PendingMessages.RETRY_INDEX + "," +
                   "     " + PendingMessages.DUE_TIME + ") " +
                   "  VALUES " +
                   "    (" + MmsSms.MMS_PROTO + "," +
                   "      new." + BaseColumns._ID + "," +
                   "      new." + Mms.MESSAGE_TYPE + ",0,0,0,0);" +
                   "END;");


        // Insert pending status for M-Send.req when it is moved into Outbox.
        db.execSQL("DROP TRIGGER IF EXISTS insert_mms_pending_on_update");
        db.execSQL("CREATE TRIGGER insert_mms_pending_on_update " +
                   "AFTER UPDATE ON pdu " +
                   "WHEN new." + Mms.MESSAGE_TYPE + "=" + PduHeaders.MESSAGE_TYPE_SEND_REQ +
                   "  AND new." + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_OUTBOX +
                   "  AND old." + Mms.MESSAGE_BOX + "!=" + Mms.MESSAGE_BOX_OUTBOX + " " +
                   "BEGIN " +
                   "  INSERT INTO " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "    (" + PendingMessages.PROTO_TYPE + "," +
                   "     " + PendingMessages.MSG_ID + "," +
                   "     " + PendingMessages.MSG_TYPE + "," +
                   "     " + PendingMessages.ERROR_TYPE + "," +
                   "     " + PendingMessages.ERROR_CODE + "," +
                   "     " + PendingMessages.RETRY_INDEX + "," +
                   "     " + PendingMessages.DUE_TIME + ") " +
                   "  VALUES " +
                   "    (" + MmsSms.MMS_PROTO + "," +
                   "      new." + BaseColumns._ID + "," +
                   "      new." + Mms.MESSAGE_TYPE + ",0,0,0,0);" +
                   "END;");

        // monitor the mms table
        db.execSQL("DROP TRIGGER IF EXISTS mms_words_update");
        db.execSQL("CREATE TRIGGER mms_words_update AFTER UPDATE ON part BEGIN UPDATE words " +
                " SET index_text = NEW.text WHERE (source_id=NEW._id AND table_to_use=2); " +
                " END;");

        db.execSQL("DROP TRIGGER IF EXISTS mms_words_delete");
        db.execSQL("CREATE TRIGGER mms_words_delete AFTER DELETE ON part BEGIN DELETE FROM " +
                " words WHERE source_id = OLD._id AND table_to_use = 2; END;");

        // Updates threads table whenever a message in pdu is updated.
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");
        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF " + Mms.DATE + ", " + Mms.SUBJECT + ", " + Mms.MESSAGE_BOX +
                   "  ON " + MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Update threads table whenever a message in pdu is deleted
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_delete");
        db.execSQL("CREATE TRIGGER pdu_update_thread_on_delete " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000)" +
                   "  WHERE threads._id = old." + Mms.THREAD_ID + "; " +
                   UPDATE_THREAD_COUNT_ON_OLD +
                   UPDATE_THREAD_SNIPPET_SNIPPET_CS_ON_DELETE +
                   "END;");

        // Updates threads table whenever a message is added to pdu.
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON " +
                   MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in pdu is updated.
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_read_on_update");
        db.execSQL("CREATE TRIGGER pdu_update_thread_read_on_update AFTER" +
                   "  UPDATE OF " + Mms.READ +
                   "  ON " + MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   "BEGIN " +
                   PDU_UPDATE_THREAD_READ_BODY +
                   "END;");

        // Update the error flag of threads when delete pending message.
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete_mms");
        db.execSQL("CREATE TRIGGER update_threads_error_on_delete_mms " +
                   "  BEFORE DELETE ON pdu" +
                   "  WHEN OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");

        // Update the error flag of threads while moving an MM out of Outbox,
        // which was failed to be sent permanently.
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_move_mms");
        db.execSQL("CREATE TRIGGER update_threads_error_on_move_mms " +
                   "  BEFORE UPDATE OF msg_box ON pdu " +
                   "  WHEN (OLD.msg_box = 4 AND NEW.msg_box != 4) " +
                   "  AND (OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10)) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");
    }

    private void createSmsTables(SQLiteDatabase db) {
        // N.B.: Whenever the columns here are changed, the columns in
        // {@ref MmsSmsProvider} must be changed to match.
        db.execSQL("CREATE TABLE sms (" +
                   "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                   "thread_id INTEGER," +
                   "address TEXT," +
                   "person INTEGER," +
                   "date INTEGER," +
                   "date_sent INTEGER DEFAULT 0," +
                   "protocol INTEGER," +
                   "read INTEGER DEFAULT 0," +
                   "status INTEGER DEFAULT -1," + // a TP-Status value
                                                  // or -1 if it
                                                  // status hasn't
                                                  // been received
                   "type INTEGER," +
                   "reply_path_present INTEGER," +
                   "subject TEXT," +
                   "body TEXT," +
                   "service_center TEXT," +
                   "locked INTEGER DEFAULT 0," +
                   "sub_id INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID + ", " +
                   "error_code INTEGER DEFAULT 0," +
                   "creator TEXT," +
                   "seen INTEGER DEFAULT 0" +
                   ");");

        /**
         * This table is used by the SMS dispatcher to hold
         * incomplete partial messages until all the parts arrive.
         */
        db.execSQL("CREATE TABLE raw (" +
                   "_id INTEGER PRIMARY KEY," +
                   "date INTEGER," +
                   "reference_number INTEGER," + // one per full message
                   "count INTEGER," + // the number of parts
                   "sequence INTEGER," + // the part number of this message
                   "destination_port INTEGER," +
                   "address TEXT," +
                   "sub_id INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID + ", " +
                   "pdu TEXT);"); // the raw PDU for this part

        db.execSQL("CREATE TABLE attachments (" +
                   "sms_id INTEGER," +
                   "content_url TEXT," +
                   "offset INTEGER);");

        /**
         * This table is used by the SMS dispatcher to hold pending
         * delivery status report intents.
         */
        db.execSQL("CREATE TABLE sr_pending (" +
                   "reference_number INTEGER," +
                   "action TEXT," +
                   "data TEXT);");

        // Restricted view of sms table, only sent/received messages
        db.execSQL("CREATE VIEW " + SmsProvider.VIEW_SMS_RESTRICTED + " AS " +
                   "SELECT * FROM " + SmsProvider.TABLE_SMS + " WHERE " +
                   Sms.TYPE + "=" + Sms.MESSAGE_TYPE_INBOX +
                   " OR " +
                   Sms.TYPE + "=" + Sms.MESSAGE_TYPE_SENT + ";");
    }

    private void createCommonTables(SQLiteDatabase db) {
        // TODO Ensure that each entry is removed when the last use of
        // any address equivalent to its address is removed.

        /**
         * This table maps the first instance seen of any particular
         * MMS/SMS address to an ID, which is then used as its
         * canonical representation.  If the same address or an
         * equivalent address (as determined by our Sqlite
         * PHONE_NUMBERS_EQUAL extension) is seen later, this same ID
         * will be used. The _id is created with AUTOINCREMENT so it
         * will never be reused again if a recipient is deleted.
         */
        db.execSQL("CREATE TABLE canonical_addresses (" +
                   "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                   "address TEXT);");

        /**
         * This table maps the subject and an ordered set of recipient
         * IDs, separated by spaces, to a unique thread ID.  The IDs
         * come from the canonical_addresses table.  This works
         * because messages are considered to be part of the same
         * thread if they have the same subject (or a null subject)
         * and the same set of recipients.
         */
        db.execSQL("CREATE TABLE threads (" +
                   Threads._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                   Threads.DATE + " INTEGER DEFAULT 0," +
                   Threads.MESSAGE_COUNT + " INTEGER DEFAULT 0," +
                   Threads.RECIPIENT_IDS + " TEXT," +
                   Threads.SNIPPET + " TEXT," +
                   Threads.SNIPPET_CHARSET + " INTEGER DEFAULT 0," +
                   Threads.READ + " INTEGER DEFAULT 1," +
                   Threads.ARCHIVED + " INTEGER DEFAULT 0," +
                   Threads.TYPE + " INTEGER DEFAULT 0," +
                   Threads.ERROR + " INTEGER DEFAULT 0," +
                   Threads.HAS_ATTACHMENT + " INTEGER DEFAULT 0);");

        /**
         * This table stores the queue of messages to be sent/downloaded.
         */
        db.execSQL("CREATE TABLE " + MmsSmsProvider.TABLE_PENDING_MSG +" (" +
                   PendingMessages._ID + " INTEGER PRIMARY KEY," +
                   PendingMessages.PROTO_TYPE + " INTEGER," +
                   PendingMessages.MSG_ID + " INTEGER," +
                   PendingMessages.MSG_TYPE + " INTEGER," +
                   PendingMessages.ERROR_TYPE + " INTEGER," +
                   PendingMessages.ERROR_CODE + " INTEGER," +
                   PendingMessages.RETRY_INDEX + " INTEGER NOT NULL DEFAULT 0," +
                   PendingMessages.DUE_TIME + " INTEGER," +
                   PendingMessages.SUBSCRIPTION_ID + " INTEGER DEFAULT " +
                           SubscriptionManager.INVALID_SUBSCRIPTION_ID + ", " +
                   PendingMessages.LAST_TRY + " INTEGER);");

    }

    // TODO Check the query plans for these triggers.
    private void createCommonTriggers(SQLiteDatabase db) {
        // Updates threads table whenever a message is added to sms.
        db.execSQL("CREATE TRIGGER sms_update_thread_on_insert AFTER INSERT ON sms " +
                   SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in sms is updated.
        db.execSQL("CREATE TRIGGER sms_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF " + Sms.DATE + ", " + Sms.BODY + ", " + Sms.TYPE +
                   "  ON sms " +
                   SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in sms is updated.
        db.execSQL("CREATE TRIGGER sms_update_thread_read_on_update AFTER" +
                   "  UPDATE OF " + Sms.READ +
                   "  ON sms " +
                   "BEGIN " +
                   SMS_UPDATE_THREAD_READ_BODY +
                   "END;");

        // As of DATABASE_VERSION 55, we've removed these triggers that delete empty threads.
        // These triggers interfere with saving drafts on brand new threads. Instead of
        // triggers cleaning up empty threads, the empty threads should be cleaned up by
        // an explicit call to delete with Threads.OBSOLETE_THREADS_URI.

//        // When the last message in a thread is deleted, these
//        // triggers ensure that the entry for its thread ID is removed
//        // from the threads table.
//        db.execSQL("CREATE TRIGGER delete_obsolete_threads_pdu " +
//                   "AFTER DELETE ON pdu " +
//                   "BEGIN " +
//                   "  DELETE FROM threads " +
//                   "  WHERE " +
//                   "    _id = old.thread_id " +
//                   "    AND _id NOT IN " +
//                   "    (SELECT thread_id FROM sms " +
//                   "     UNION SELECT thread_id from pdu); " +
//                   "END;");
//
//        db.execSQL("CREATE TRIGGER delete_obsolete_threads_when_update_pdu " +
//                   "AFTER UPDATE OF " + Mms.THREAD_ID + " ON pdu " +
//                   "WHEN old." + Mms.THREAD_ID + " != new." + Mms.THREAD_ID + " " +
//                   "BEGIN " +
//                   "  DELETE FROM threads " +
//                   "  WHERE " +
//                   "    _id = old.thread_id " +
//                   "    AND _id NOT IN " +
//                   "    (SELECT thread_id FROM sms " +
//                   "     UNION SELECT thread_id from pdu); " +
//                   "END;");

        // TODO Add triggers for SMS retry-status management.

        // Update the error flag of threads when the error type of
        // a pending MM is updated.
        db.execSQL("CREATE TRIGGER update_threads_error_on_update_mms " +
                   "  AFTER UPDATE OF err_type ON pending_msgs " +
                   "  WHEN (OLD.err_type < 10 AND NEW.err_type >= 10)" +
                   "    OR (OLD.err_type >= 10 AND NEW.err_type < 10) " +
                   "BEGIN" +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.err_type >= 10 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id =" +
                   "   (SELECT DISTINCT thread_id" +
                   "    FROM pdu" +
                   "    WHERE _id = NEW.msg_id); " +
                   "END;");

        // Update the error flag of threads after a text message was
        // failed to send/receive.
        db.execSQL("CREATE TRIGGER update_threads_error_on_update_sms " +
                   "  AFTER UPDATE OF type ON sms" +
                   "  WHEN (OLD.type != 5 AND NEW.type = 5)" +
                   "    OR (OLD.type = 5 AND NEW.type != 5) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.type = 5 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id = NEW.thread_id; " +
                   "END;");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
        Log.d(TAG, "=====wap push====1====Upgrading database from version " + oldVersion
                + " to " + currentVersion + ".");

        switch (oldVersion) {
        case 40:
            if (currentVersion <= 40) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion41(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 41:
            if (currentVersion <= 41) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion42(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 42:
            if (currentVersion <= 42) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion43(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 43:
            if (currentVersion <= 43) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion44(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 44:
            if (currentVersion <= 44) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion45(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 45:
            if (currentVersion <= 45) {
                return;
            }
            db.beginTransaction();
            try {
                upgradeDatabaseToVersion46(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 46:
            if (currentVersion <= 46) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion47(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 47:
            if (currentVersion <= 47) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion48(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 48:
            if (currentVersion <= 48) {
                return;
            }

            db.beginTransaction();
            try {
                createWordsTables(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 49:
            if (currentVersion <= 49) {
                return;
            }
            db.beginTransaction();
            try {
                createThreadIdIndex(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break; // force to destroy all old data;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 50:
            if (currentVersion <= 50) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion51(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 51:
            if (currentVersion <= 51) {
                return;
            }
            // 52 was adding a new meta_data column, but that was removed.
            // fall through
        case 52:
            if (currentVersion <= 52) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion53(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 53:
            if (currentVersion <= 53) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion54(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 54:
            if (currentVersion <= 54) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion55(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 55:
            if (currentVersion <= 55) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion56(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 56:
            if (currentVersion <= 56) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion57(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 57:
            if (currentVersion <= 57) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion58(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 58:
            if (currentVersion <= 58) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion59(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 59:
            if (currentVersion <= 59) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion60(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            // fall through
        case 60:
            if (currentVersion <= 60) {
                return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion61(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }
            return;
        case 61:
            if (currentVersion <= 61) {
               return;
            }
            Log.d(TAG, "=====wap push====2===Upgrading database from version "+ oldVersion + " to "+ currentVersion + ".");
            try {
                addWapPushColumnToSmsTable(db);
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }

        case 161:
            if (currentVersion <= 161) {
               return;
            }

            db.beginTransaction();
            try {
                upgradeDatabaseToVersion162(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            } finally {
                db.endTransaction();
            }

            return;

        case 162:
            if (currentVersion <= 162) {
                return;
            }
            Log.d(TAG,
                    "=====ContactsFdn_table====2===Upgrading database from version "
                            + oldVersion + " to " + currentVersion + ".");

            db.beginTransaction();
            try {
                createFdnContactsFilterTable(db);
                db.setTransactionSuccessful();
            } catch (Throwable ex) {
                Log.e(TAG, ex.getMessage(), ex);
                break;
            }finally {
                db.endTransaction();
            }
            return;


            default :
                Log.e(TAG, "default");
                return ;
        }

        Log.e(TAG, "Destroying all old data.");
        try {
            dropAll(db);
            onCreate(db);
        } catch (Throwable ex) {
            Log.e(TAG, ex.getMessage(), ex);
        }

    }

    private void dropAll(SQLiteDatabase db) {
        // Clean the database out in order to start over from scratch.
        // We don't need to drop our triggers here because SQLite automatically
        // drops a trigger when its attached database is dropped.
        db.execSQL("DROP TABLE IF EXISTS canonical_addresses");
        db.execSQL("DROP TABLE IF EXISTS threads");
        db.execSQL("DROP TABLE IF EXISTS " + MmsSmsProvider.TABLE_PENDING_MSG);
        db.execSQL("DROP TABLE IF EXISTS sms");
        db.execSQL("DROP TABLE IF EXISTS raw");
        db.execSQL("DROP TABLE IF EXISTS attachments");
        db.execSQL("DROP TABLE IF EXISTS thread_ids");
        db.execSQL("DROP TABLE IF EXISTS sr_pending");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_PDU + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_ADDR + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_PART + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_RATE + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_DRM + ";");
    }

    private void upgradeDatabaseToVersion41(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_move_mms");
        db.execSQL("CREATE TRIGGER update_threads_error_on_move_mms " +
                   "  BEFORE UPDATE OF msg_box ON pdu " +
                   "  WHEN (OLD.msg_box = 4 AND NEW.msg_box != 4) " +
                   "  AND (OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10)) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion42(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_sms");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete_sms");
    }

    private void upgradeDatabaseToVersion43(SQLiteDatabase db) {
        // Add 'has_attachment' column to threads table.
        db.execSQL("ALTER TABLE threads ADD COLUMN has_attachment INTEGER DEFAULT 0");

        updateThreadsAttachmentColumn(db);

        // Add insert and delete triggers for keeping it up to date.
        db.execSQL(PART_UPDATE_THREADS_ON_INSERT_TRIGGER);
        db.execSQL(PART_UPDATE_THREADS_ON_DELETE_TRIGGER);
    }

    private void upgradeDatabaseToVersion44(SQLiteDatabase db) {
        updateThreadsAttachmentColumn(db);

        // add the update trigger for keeping the threads up to date.
        db.execSQL(PART_UPDATE_THREADS_ON_UPDATE_TRIGGER);
    }

    private void upgradeDatabaseToVersion45(SQLiteDatabase db) {
        // Add 'locked' column to sms table.
        db.execSQL("ALTER TABLE sms ADD COLUMN " + Sms.LOCKED + " INTEGER DEFAULT 0");

        // Add 'locked' column to pdu table.
        db.execSQL("ALTER TABLE pdu ADD COLUMN " + Mms.LOCKED + " INTEGER DEFAULT 0");
    }

    private void upgradeDatabaseToVersion46(SQLiteDatabase db) {
        // add the "text" column for caching inline text (e.g. strings) instead of
        // putting them in an external file
        db.execSQL("ALTER TABLE part ADD COLUMN " + Part.TEXT + " TEXT");

        Cursor textRows = db.query(
                "part",
                new String[] { Part._ID, Part._DATA, Part.TEXT},
                "ct = 'text/plain' OR ct == 'application/smil'",
                null,
                null,
                null,
                null);
        ArrayList<String> filesToDelete = new ArrayList<String>();
        try {
            db.beginTransaction();
            if (textRows != null) {
                int partDataColumn = textRows.getColumnIndex(Part._DATA);

                // This code is imperfect in that we can't guarantee that all the
                // backing files get deleted.  For example if the system aborts after
                // the database is updated but before we complete the process of
                // deleting files.
                while (textRows.moveToNext()) {
                    String path = textRows.getString(partDataColumn);
                    if (path != null) {
                        try {
                            InputStream is = new FileInputStream(path);
                            byte [] data = new byte[is.available()];
                            is.read(data);
                            EncodedStringValue v = new EncodedStringValue(data);
                            db.execSQL("UPDATE part SET " + Part._DATA + " = NULL, " +
                                    Part.TEXT + " = ?", new String[] { v.getString() });
                            is.close();
                            filesToDelete.add(path);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            for (String pathToDelete : filesToDelete) {
                try {
                    (new File(pathToDelete)).delete();
                } catch (SecurityException ex) {
                    Log.e(TAG, "unable to clean up old mms file for " + pathToDelete, ex);
                }
            }
            if (textRows != null) {
                textRows.close();
            }
        }
    }

    private void upgradeDatabaseToVersion47(SQLiteDatabase db) {
        updateThreadsAttachmentColumn(db);

        // add the update trigger for keeping the threads up to date.
        db.execSQL(PDU_UPDATE_THREADS_ON_UPDATE_TRIGGER);
    }

    private void upgradeDatabaseToVersion48(SQLiteDatabase db) {
        // Add 'error_code' column to sms table.
        db.execSQL("ALTER TABLE sms ADD COLUMN error_code INTEGER DEFAULT 0");
    }

    private void upgradeDatabaseToVersion51(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE sms add COLUMN seen INTEGER DEFAULT 0");
        db.execSQL("ALTER TABLE pdu add COLUMN seen INTEGER DEFAULT 0");

        try {
            // update the existing sms and pdu tables so the new "seen" column is the same as
            // the "read" column for each row.
            ContentValues contentValues = new ContentValues();
            contentValues.put("seen", 1);
            int count = db.update("sms", contentValues, "read=1", null);
            Log.d(TAG, "[MmsSmsDb] upgradeDatabaseToVersion51: updated " + count +
                    " rows in sms table to have READ=1");
            count = db.update("pdu", contentValues, "read=1", null);
            Log.d(TAG, "[MmsSmsDb] upgradeDatabaseToVersion51: updated " + count +
                    " rows in pdu table to have READ=1");
        } catch (Exception ex) {
            Log.e(TAG, "[MmsSmsDb] upgradeDatabaseToVersion51 caught ", ex);
        }
    }

    private void upgradeDatabaseToVersion53(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_read_on_update");

        // Updates threads table whenever a message in pdu is updated.
        db.execSQL("CREATE TRIGGER pdu_update_thread_read_on_update AFTER" +
                   "  UPDATE OF " + Mms.READ +
                   "  ON " + MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   "BEGIN " +
                   PDU_UPDATE_THREAD_READ_BODY +
                   "END;");
    }

    private void upgradeDatabaseToVersion54(SQLiteDatabase db) {
        // Add 'date_sent' column to sms table.
        db.execSQL("ALTER TABLE sms ADD COLUMN " + Sms.DATE_SENT + " INTEGER DEFAULT 0");

        // Add 'date_sent' column to pdu table.
        db.execSQL("ALTER TABLE pdu ADD COLUMN " + Mms.DATE_SENT + " INTEGER DEFAULT 0");
    }

    private void upgradeDatabaseToVersion55(SQLiteDatabase db) {
        // Drop removed triggers
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_pdu");
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_when_update_pdu");
    }

    private void upgradeDatabaseToVersion56(SQLiteDatabase db) {
        // Add 'text_only' column to pdu table.
        db.execSQL("ALTER TABLE " + MmsProvider.TABLE_PDU + " ADD COLUMN " + Mms.TEXT_ONLY +
                " INTEGER DEFAULT 0");
    }

    private void upgradeDatabaseToVersion57(SQLiteDatabase db) {
        // Clear out bad rows, those with empty threadIds, from the pdu table.
        db.execSQL("DELETE FROM " + MmsProvider.TABLE_PDU + " WHERE " + Mms.THREAD_ID + " IS NULL");
    }

    private void upgradeDatabaseToVersion58(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + MmsProvider.TABLE_PDU +
                " ADD COLUMN " + Mms.SUBSCRIPTION_ID
                + " INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        db.execSQL("ALTER TABLE " + MmsSmsProvider.TABLE_PENDING_MSG
                +" ADD COLUMN " + "pending_sub_id"
                + " INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        db.execSQL("ALTER TABLE " + SmsProvider.TABLE_SMS
                + " ADD COLUMN " + Sms.SUBSCRIPTION_ID
                + " INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        db.execSQL("ALTER TABLE " + SmsProvider.TABLE_RAW
                +" ADD COLUMN " + Sms.SUBSCRIPTION_ID
                + " INTEGER DEFAULT " + SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    private void upgradeDatabaseToVersion59(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + MmsProvider.TABLE_PDU +" ADD COLUMN "
                + Mms.CREATOR + " TEXT");
        db.execSQL("ALTER TABLE " + SmsProvider.TABLE_SMS +" ADD COLUMN "
                + Sms.CREATOR + " TEXT");
    }

    private void upgradeDatabaseToVersion60(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + MmsSmsProvider.TABLE_THREADS +" ADD COLUMN "
                + Threads.ARCHIVED + " INTEGER DEFAULT 0");
    }

    private void upgradeDatabaseToVersion61(SQLiteDatabase db) {
        db.execSQL("CREATE VIEW " + SmsProvider.VIEW_SMS_RESTRICTED + " AS " +
                   "SELECT * FROM " + SmsProvider.TABLE_SMS + " WHERE " +
                   Sms.TYPE + "=" + Sms.MESSAGE_TYPE_INBOX +
                   " OR " +
                   Sms.TYPE + "=" + Sms.MESSAGE_TYPE_SENT + ";");
        db.execSQL("CREATE VIEW " + MmsProvider.VIEW_PDU_RESTRICTED + "  AS " +
                   "SELECT * FROM " + MmsProvider.TABLE_PDU + " WHERE " +
                   "(" + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_INBOX +
                   " OR " +
                   Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_SENT + ")" +
                   " AND " +
                   "(" + Mms.MESSAGE_TYPE + "!=" + PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND + ");");

    }

    private void upgradeDatabaseToVersion162(SQLiteDatabase db) {
        ICCMessageManager.getInstance().CreateStructure(db);
    }

    @Override
    public synchronized SQLiteDatabase getWritableDatabase() {
        SQLiteDatabase db = super.getWritableDatabase();

        if (!sTriedAutoIncrement) {
            sTriedAutoIncrement = true;
            boolean hasAutoIncrementThreads = hasAutoIncrement(db, MmsSmsProvider.TABLE_THREADS);
            boolean hasAutoIncrementAddresses = hasAutoIncrement(db, "canonical_addresses");
            boolean hasAutoIncrementPart = hasAutoIncrement(db, "part");
            boolean hasAutoIncrementPdu = hasAutoIncrement(db, "pdu");
            Log.d(TAG, "[getWritableDatabase] hasAutoIncrementThreads: " + hasAutoIncrementThreads +
                    " hasAutoIncrementAddresses: " + hasAutoIncrementAddresses +
                    " hasAutoIncrementPart: " + hasAutoIncrementPart +
                    " hasAutoIncrementPdu: " + hasAutoIncrementPdu);
            boolean autoIncrementThreadsSuccess = true;
            boolean autoIncrementAddressesSuccess = true;
            boolean autoIncrementPartSuccess = true;
            boolean autoIncrementPduSuccess = true;
            if (!hasAutoIncrementThreads) {
                db.beginTransaction();
                try {
                    if (false && sFakeLowStorageTest) {
                        Log.d(TAG, "[getWritableDatabase] mFakeLowStorageTest is true " +
                                " - fake exception");
                        throw new Exception("FakeLowStorageTest");
                    }
                    upgradeThreadsTableToAutoIncrement(db);     // a no-op if already upgraded
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, "Failed to add autoIncrement to threads;: " + ex.getMessage(), ex);
                    autoIncrementThreadsSuccess = false;
                } finally {
                    db.endTransaction();
                }
            }
            if (!hasAutoIncrementAddresses) {
                db.beginTransaction();
                try {
                    if (false && sFakeLowStorageTest) {
                        Log.d(TAG, "[getWritableDatabase] mFakeLowStorageTest is true " +
                        " - fake exception");
                        throw new Exception("FakeLowStorageTest");
                    }
                    upgradeAddressTableToAutoIncrement(db);     // a no-op if already upgraded
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, "Failed to add autoIncrement to canonical_addresses: " +
                            ex.getMessage(), ex);
                    autoIncrementAddressesSuccess = false;
                } finally {
                    db.endTransaction();
                }
            }
            if (!hasAutoIncrementPart) {
                db.beginTransaction();
                try {
                    if (false && sFakeLowStorageTest) {
                        Log.d(TAG, "[getWritableDatabase] mFakeLowStorageTest is true " +
                        " - fake exception");
                        throw new Exception("FakeLowStorageTest");
                    }
                    upgradePartTableToAutoIncrement(db);     // a no-op if already upgraded
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, "Failed to add autoIncrement to part: " +
                            ex.getMessage(), ex);
                    autoIncrementPartSuccess = false;
                } finally {
                    db.endTransaction();
                }
            }
            if (!hasAutoIncrementPdu) {
                db.beginTransaction();
                try {
                    if (false && sFakeLowStorageTest) {
                        Log.d(TAG, "[getWritableDatabase] mFakeLowStorageTest is true " +
                        " - fake exception");
                        throw new Exception("FakeLowStorageTest");
                    }
                    upgradePduTableToAutoIncrement(db);     // a no-op if already upgraded
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, "Failed to add autoIncrement to pdu: " +
                            ex.getMessage(), ex);
                    autoIncrementPduSuccess = false;
                } finally {
                    db.endTransaction();
                }
            }
            if (autoIncrementThreadsSuccess &&
                    autoIncrementAddressesSuccess &&
                    autoIncrementPartSuccess &&
                    autoIncrementPduSuccess) {
                if (mLowStorageMonitor != null) {
                    // We've already updated the database. This receiver is no longer necessary.
                    Log.d(TAG, "Unregistering mLowStorageMonitor - we've upgraded");
                    mContext.unregisterReceiver(mLowStorageMonitor);
                    mLowStorageMonitor = null;
                }
            } else {
                if (sFakeLowStorageTest) {
                    sFakeLowStorageTest = false;
                }

                // We failed, perhaps because of low storage. Turn on a receiver to watch for
                // storage space.
                if (mLowStorageMonitor == null) {
                    Log.d(TAG, "[getWritableDatabase] turning on storage monitor");
                    mLowStorageMonitor = new LowStorageMonitor();
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
                    intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
                    mContext.registerReceiver(mLowStorageMonitor, intentFilter);
                }
            }
        }
        return db;
    }

    // Determine whether a particular table has AUTOINCREMENT in its schema.
    private boolean hasAutoIncrement(SQLiteDatabase db, String tableName) {
        boolean result = false;
        String query = "SELECT sql FROM sqlite_master WHERE type='table' AND name='" +
                        tableName + "'";
        Cursor c = db.rawQuery(query, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String schema = c.getString(0);
                    result = schema != null ? schema.contains("AUTOINCREMENT") : false;
                    Log.d(TAG, "[MmsSmsDb] tableName: " + tableName + " hasAutoIncrement: " +
                            schema + " result: " + result);
                }
            } finally {
                c.close();
            }
        }
        return result;
    }

    // upgradeThreadsTableToAutoIncrement() is called to add the AUTOINCREMENT keyword to
    // the threads table. This could fail if the user has a lot of conversations and not enough
    // storage to make a copy of the threads table. That's ok. This upgrade is optional. It'll
    // be called again next time the device is rebooted.
    private void upgradeThreadsTableToAutoIncrement(SQLiteDatabase db) {
        if (hasAutoIncrement(db, MmsSmsProvider.TABLE_THREADS)) {
            Log.d(TAG, "[MmsSmsDb] upgradeThreadsTableToAutoIncrement: already upgraded");
            return;
        }
        Log.d(TAG, "[MmsSmsDb] upgradeThreadsTableToAutoIncrement: upgrading");

        // Make the _id of the threads table autoincrement so we never re-use thread ids
        // Have to create a new temp threads table. Copy all the info from the old table.
        // Drop the old table and rename the new table to that of the old.
        db.execSQL("CREATE TABLE threads_temp (" +
                Threads._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Threads.DATE + " INTEGER DEFAULT 0," +
                Threads.MESSAGE_COUNT + " INTEGER DEFAULT 0," +
                Threads.RECIPIENT_IDS + " TEXT," +
                Threads.SNIPPET + " TEXT," +
                Threads.SNIPPET_CHARSET + " INTEGER DEFAULT 0," +
                Threads.READ + " INTEGER DEFAULT 1," +
                Threads.TYPE + " INTEGER DEFAULT 0," +
                Threads.ERROR + " INTEGER DEFAULT 0," +
                Threads.HAS_ATTACHMENT + " INTEGER DEFAULT 0);");

        db.execSQL("INSERT INTO threads_temp SELECT * from threads;");
        db.execSQL("DROP TABLE threads;");
        db.execSQL("ALTER TABLE threads_temp RENAME TO threads;");
    }

    // upgradeAddressTableToAutoIncrement() is called to add the AUTOINCREMENT keyword to
    // the canonical_addresses table. This could fail if the user has a lot of people they've
    // messaged with and not enough storage to make a copy of the canonical_addresses table.
    // That's ok. This upgrade is optional. It'll be called again next time the device is rebooted.
    private void upgradeAddressTableToAutoIncrement(SQLiteDatabase db) {
        if (hasAutoIncrement(db, "canonical_addresses")) {
            Log.d(TAG, "[MmsSmsDb] upgradeAddressTableToAutoIncrement: already upgraded");
            return;
        }
        Log.d(TAG, "[MmsSmsDb] upgradeAddressTableToAutoIncrement: upgrading");

        // Make the _id of the canonical_addresses table autoincrement so we never re-use ids
        // Have to create a new temp canonical_addresses table. Copy all the info from the old
        // table. Drop the old table and rename the new table to that of the old.
        db.execSQL("CREATE TABLE canonical_addresses_temp (_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "address TEXT);");

        db.execSQL("INSERT INTO canonical_addresses_temp SELECT * from canonical_addresses;");
        db.execSQL("DROP TABLE canonical_addresses;");
        db.execSQL("ALTER TABLE canonical_addresses_temp RENAME TO canonical_addresses;");
    }

    // upgradePartTableToAutoIncrement() is called to add the AUTOINCREMENT keyword to
    // the part table. This could fail if the user has a lot of sound/video/picture attachments
    // and not enough storage to make a copy of the part table.
    // That's ok. This upgrade is optional. It'll be called again next time the device is rebooted.
    private void upgradePartTableToAutoIncrement(SQLiteDatabase db) {
        if (hasAutoIncrement(db, "part")) {
            Log.d(TAG, "[MmsSmsDb] upgradePartTableToAutoIncrement: already upgraded");
            return;
        }
        Log.d(TAG, "[MmsSmsDb] upgradePartTableToAutoIncrement: upgrading");

        // Make the _id of the part table autoincrement so we never re-use ids
        // Have to create a new temp part table. Copy all the info from the old
        // table. Drop the old table and rename the new table to that of the old.
        db.execSQL("CREATE TABLE part_temp (" +
                Part._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Part.MSG_ID + " INTEGER," +
                Part.SEQ + " INTEGER DEFAULT 0," +
                Part.CONTENT_TYPE + " TEXT," +
                Part.NAME + " TEXT," +
                Part.CHARSET + " INTEGER," +
                Part.CONTENT_DISPOSITION + " TEXT," +
                Part.FILENAME + " TEXT," +
                Part.CONTENT_ID + " TEXT," +
                Part.CONTENT_LOCATION + " TEXT," +
                Part.CT_START + " INTEGER," +
                Part.CT_TYPE + " TEXT," +
                Part._DATA + " TEXT," +
                Part.TEXT + " TEXT);");

        db.execSQL("INSERT INTO part_temp SELECT * from part;");
        db.execSQL("DROP TABLE part;");
        db.execSQL("ALTER TABLE part_temp RENAME TO part;");

        // part-related triggers get tossed when the part table is dropped -- rebuild them.
        createMmsTriggers(db);
    }

    // upgradePduTableToAutoIncrement() is called to add the AUTOINCREMENT keyword to
    // the pdu table. This could fail if the user has a lot of mms messages
    // and not enough storage to make a copy of the pdu table.
    // That's ok. This upgrade is optional. It'll be called again next time the device is rebooted.
    private void upgradePduTableToAutoIncrement(SQLiteDatabase db) {
        if (hasAutoIncrement(db, "pdu")) {
            Log.d(TAG, "[MmsSmsDb] upgradePduTableToAutoIncrement: already upgraded");
            return;
        }
        Log.d(TAG, "[MmsSmsDb] upgradePduTableToAutoIncrement: upgrading");

        // Make the _id of the part table autoincrement so we never re-use ids
        // Have to create a new temp part table. Copy all the info from the old
        // table. Drop the old table and rename the new table to that of the old.
        db.execSQL("CREATE TABLE pdu_temp (" +
                Mms._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                Mms.THREAD_ID + " INTEGER," +
                Mms.DATE + " INTEGER," +
                Mms.DATE_SENT + " INTEGER DEFAULT 0," +
                Mms.MESSAGE_BOX + " INTEGER," +
                Mms.READ + " INTEGER DEFAULT 0," +
                Mms.MESSAGE_ID + " TEXT," +
                Mms.SUBJECT + " TEXT," +
                Mms.SUBJECT_CHARSET + " INTEGER," +
                Mms.CONTENT_TYPE + " TEXT," +
                Mms.CONTENT_LOCATION + " TEXT," +
                Mms.EXPIRY + " INTEGER," +
                Mms.MESSAGE_CLASS + " TEXT," +
                Mms.MESSAGE_TYPE + " INTEGER," +
                Mms.MMS_VERSION + " INTEGER," +
                Mms.MESSAGE_SIZE + " INTEGER," +
                Mms.PRIORITY + " INTEGER," +
                Mms.READ_REPORT + " INTEGER," +
                Mms.REPORT_ALLOWED + " INTEGER," +
                Mms.RESPONSE_STATUS + " INTEGER," +
                Mms.STATUS + " INTEGER," +
                Mms.TRANSACTION_ID + " TEXT," +
                Mms.RETRIEVE_STATUS + " INTEGER," +
                Mms.RETRIEVE_TEXT + " TEXT," +
                Mms.RETRIEVE_TEXT_CHARSET + " INTEGER," +
                Mms.READ_STATUS + " INTEGER," +
                Mms.CONTENT_CLASS + " INTEGER," +
                Mms.RESPONSE_TEXT + " TEXT," +
                Mms.DELIVERY_TIME + " INTEGER," +
                Mms.DELIVERY_REPORT + " INTEGER," +
                Mms.LOCKED + " INTEGER DEFAULT 0," +
                Mms.SUBSCRIPTION_ID + " INTEGER DEFAULT "
                        + SubscriptionManager.INVALID_SUBSCRIPTION_ID + ", " +
                Mms.SEEN + " INTEGER DEFAULT 0," +
                Mms.TEXT_ONLY + " INTEGER DEFAULT 0" +
                ");");

        db.execSQL("INSERT INTO pdu_temp SELECT * from pdu;");
        db.execSQL("DROP TABLE pdu;");
        db.execSQL("ALTER TABLE pdu_temp RENAME TO pdu;");

        // pdu-related triggers get tossed when the part table is dropped -- rebuild them.
        createMmsTriggers(db);
    }

    private class LowStorageMonitor extends BroadcastReceiver {

        public LowStorageMonitor() {
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            Log.d(TAG, "[LowStorageMonitor] onReceive intent " + action);

            if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                sTriedAutoIncrement = false;    // try to upgrade on the next getWriteableDatabase
            }
        }
    }

    private void updateThreadsAttachmentColumn(SQLiteDatabase db) {
        // Set the values of that column correctly based on the current
        // contents of the database.
        db.execSQL("UPDATE threads SET has_attachment=1 WHERE _id IN " +
                   "  (SELECT DISTINCT pdu.thread_id FROM part " +
                   "   JOIN pdu ON pdu._id=part.mid " +
                   "   WHERE part.ct != 'text/plain' AND part.ct != 'application/smil')");
    }

    /*******************************************************************************************************
     * Fdn Add Function 2016/05/27 Start //sprd: fix for telcel bug 557685 begin
     ******************************************************************************************************/
    // Create Table
    private void createFdnContactsFilterTable(SQLiteDatabase db) {
//        if(SystemProperties.getInt("ro.messag.fdnfilter", 1) != 1){
//                return;
//        }
        createFdnContactsTable(db, SQL_CONTACTS_SMS_TABLE);
        createFdnContactsTable(db, SQL_CONTACTS_FDN_TABLE);
        createFdnContactsViewTables(db);
    }

    private void createFdnContactsTable(SQLiteDatabase db, String sql) {
        Log.d(TAG, "=====ContactSmsTable======onCreate =======");
        db.execSQL(sql);
        Log.d(TAG, sql);
    }

    private void createFdnContactsViewTables(SQLiteDatabase db) {
            Log.d(TAG,
                    "=====createFdnContacts_ViewTables======onCreate =======");
            createFdnContactsViewTable(db, SQL_CONTACTS_FDN_TABLE_VIEW);
            createFdnContactsViewTable(db, SQL_CONTACTS_SMS_TABLE_VIEW);
            createFdnContactsViewTable(db, SQL_VIEW_CONTACTS_FDN_TABLE_CONFUSE);
    }

    private void createFdnContactsViewTable(SQLiteDatabase db, String sql) {
        Log.d(TAG, "=====ContactFdnSmsTable_View======onCreate =======");
        db.execSQL(sql);
        Log.d(TAG, sql);
    }

    private void initFirstFdnAndContactsTables() {
        try {
            MAX_WAITING_TIMES--;
            if (getSimstatus(getContext()) == TelephonyManager.SIM_STATE_READY) {
                sendEmptyMesssagingSprd(getHandler(), FDNCONTACTS_UPDATA_FLAG,
                        0);
                sendEmptyMesssagingSprd(getHandler(),lOCALCONTACTS_UPDATA_FLAG, 0);
            } else {
                if (MAX_WAITING_TIMES > 0) {
                    sendEmptyMesssagingSprd(getHandler(), INIT_ALL_UPDATA_FLAG,
                            2000);
                }
            }
            Log.i("ContactsSmsProvider", "i------waiting times ---->"
                    + MAX_WAITING_TIMES);
        } catch (Exception e) {
            System.out.println(e.fillInStackTrace());
        }

    }

    private void sendEmptyMesssagingSprd(Handler  handler, int message, long time){
         if(handler.hasMessages(message)){
              handler.removeMessages(message);
         }
         handler.sendEmptyMessageDelayed(message, time);
    }

    private void BlukInsert(SQLiteDatabase sqLiteDatabase, int nCount,
            ContentValues[] contentValues, String tableName) {
        Log.i("ContactsSmsProvider",
                "***********BlukInsert-->*****************");
        Log.i("ContactsSmsProvider",
                "***********tableName-->*****************"+tableName);
        sqLiteDatabase.beginTransaction();
        try {
            for (int j = 0; j < nCount; j++) {
                sqLiteDatabase.insert(tableName, null, contentValues[j]);
            }
            sqLiteDatabase.setTransactionSuccessful();
        } finally {
            sqLiteDatabase.endTransaction();
            contentValues = null;
        }
    }

    private static Cursor queryContacts() {
        Cursor cursor = getContext().getContentResolver().query(CONTACTS_URI,
                PROJECTIONCONTACTS, null, null, SORT_KEY);
        if (cursor != null) {
            Log.i("ContactsSmsProvider",
                    "***********cursor count-->*****************"
                            + cursor.getCount());
        }
        return cursor;
    }

    private static Cursor queryFdnContacts() {
        Cursor cursor = getContext().getContentResolver().query(CONTACTS_URI,
                PROJECTIONCONTACTS, null, null, SORT_KEY);
        if (cursor != null) {
            Log.i("ContactsSmsProvider",
                    "***********cursor count-->*****************"
                            + cursor.getCount());
        }
        return cursor;
    }

    private void initContactsDate(){
        Log.i("ContactsSmsProvider",
                "***********begin time*****************"
                        + SystemClock.currentThreadTimeMillis());
        final SQLiteDatabase sqLiteDatabase = sInstance
                .getWritableDatabase();

        ContentValues[] contentValues = new ContentValues[MAX_COUNT];
        int iCount = 0;
        int nIndex = 0;
        Cursor cursor = queryContacts();
        try {
            sqLiteDatabase.execSQL("delete from ContactSmsTable");
            while (cursor != null && cursor.moveToNext()) {
                nIndex = iCount % MAX_COUNT;
                contentValues[nIndex] = new ContentValues();
                contentValues[nIndex].put("contact_id", cursor.getInt(0));
                contentValues[nIndex].put("display_name",
                        getSafeString(cursor.getString(1)));
                contentValues[nIndex].put("photo_thumb_uri",
                        getSafeString(cursor.getString(2)));
                contentValues[nIndex].put("data1", getSafeString(cursor.getString(3)));
                contentValues[nIndex].put("data2", getSafeString(cursor.getString(4)));
                contentValues[nIndex].put("data3", getSafeString(cursor.getString(5)));
                contentValues[nIndex].put("lookup",getSafeString(cursor.getString(6)));
                contentValues[nIndex].put("_id", cursor.getInt(7));
                contentValues[nIndex].put("sort_key", getSafeString(cursor.getString(8)));
                contentValues[nIndex].put("account_name",
                        getSafeString(cursor.getString(9)));
                contentValues[nIndex].put("fdn_contacts_subId",
                        -1000);

                if (iCount % 50 == 0 && iCount != 0) {
                    BlukInsert(sqLiteDatabase, MAX_COUNT, contentValues,
                            CONTACTS_SMS_TABLE);
                    contentValues = new ContentValues[MAX_COUNT];
                }
                ++iCount;
                nIndex = iCount % MAX_COUNT;
            }

            if (nIndex != 0) {
                // blukinsert
                BlukInsert(sqLiteDatabase, nIndex, contentValues,
                        CONTACTS_SMS_TABLE);
            }
            contentValues = null;
        } catch (Exception exception) {
            Log.i("ContactsSmsProvider", exception.toString());
        } finally{
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        needRegisterUri(CONTACTS_URI.toString(), getContactsObserver());
        Log.i("ContactsSmsProvider",
                "***********end time********************"
                        + SystemClock.currentThreadTimeMillis());
    }

    public static final int lOCALCONTACTS_UPDATA_FLAG = 1005;
    public static final int FDNCONTACTS_UPDATA_FLAG   = 1006;
    public static final int INIT_ALL_UPDATA_FLAG      = 1007;

    /** A content:// style uri to the authority for the contacts provider */
    public static final Uri CONTACTS_DATAIL_URI = Uri
            .parse("content://com.android.contacts/data/phones/filter");

    public static final Uri CONTACTS_URI = Uri
            .parse("content://com.android.contacts/data");

    private static final String URI_SINGAL_SUB = "content://icc/fdn";
    private static final String URI_MORE_SUBID = "content://icc/fdn/subId/";

    private final static String CONTACTS_SMS_TABLE = "ContactSmsTable";

    private final static String CONTACTS_FDN_TABLE = "FdnContactSmsTable";

    private final static String VIEW_CONTACTS_FDN_TABLE = "v_FdnContactSmsTable";

    private final static String VIEW_CONTACTS_SMS_TABLE = "v_ContactSmsTable";

    private final static String VIEW_CONTACTS_FDN_TABLE_CONFUSE = "v_FdnContactsConfuseTable";

    private static final String SORT_KEY = "sort_key";

    private static final String[] PROJECTION = new String[] { "contact_id", // 0
            "display_name", // 1
            "photo_thumb_uri", // 2
            "data1", // 3
            "data2", // 4
            "data3", // 5
            "lookup", // 6
            "_id", // 7
            "sort_key", // 8
            "account_name", // 9
    };

    private static final String[] PROJECTIONCONTACTS = new String[] {
            "contact_id", // 0
            "display_name", // 1
            "photo_thumb_uri", // 2
            "data1", // 3
            "data2", // 4
            "data3", // 5
            "lookup", // 6
            "_id", // 7
            "sort_key", // 8
            "account_name", // 9
    };

    private static final String[] PROJECTIONFDNCONTACTS = new String[] {
            "name", // 0
            "number", // 1
    };

    private final static String SQL_CONTACTS_SMS_TABLE = "CREATE TABLE "
            + CONTACTS_SMS_TABLE + " (" + "contact_id INTEGER,"
            + "display_name TEXT," + "photo_thumb_uri  TEXT," + "data1  TEXT,"
            + "data2 TEXT," + "data3 TEXT," + "lookup TEXT," + "_id INTEGER,"
            + "sort_key TEXT," + "account_name TEXT," + "fdn_contacts_subId INTEGER"+ ");";

    private final static String SQL_CONTACTS_FDN_TABLE = "CREATE TABLE "
            + CONTACTS_FDN_TABLE + " (" + "contact_id INTEGER,"
            + "display_name TEXT," + "photo_thumb_uri  TEXT," + "data1  TEXT,"
            + "data2 TEXT," + "data3 TEXT," + "lookup TEXT," + "_id INTEGER,"
            + "sort_key TEXT," + "account_name TEXT," + "fdn_contacts_subId INTEGER"+");";

    private final static String SQL_CONTACTS_FDN_TABLE_VIEW = "CREATE VIEW "
            + VIEW_CONTACTS_FDN_TABLE + " AS " + " SELECT " + "contact_id,"
            + "display_name," + "photo_thumb_uri," + "data1," + "data2,"
            + "data3," + "lookup," + "_id," + "sort_key," + "account_name,"
            + "fdn_contacts_subId"+" FROM " + CONTACTS_FDN_TABLE;

    private final static String SQL_CONTACTS_SMS_TABLE_VIEW = "CREATE VIEW "
            + VIEW_CONTACTS_SMS_TABLE + " AS " + " SELECT " + "contact_id,"
            + "display_name," + "photo_thumb_uri," + "data1," + "data2,"
            + "data3," + "lookup," + "_id," + "sort_key," + "account_name,"
            + "fdn_contacts_subId"+" FROM " + CONTACTS_SMS_TABLE;

    private final static String SQL_VIEW_CONTACTS_FDN_TABLE_CONFUSE = " CREATE VIEW "
            + VIEW_CONTACTS_FDN_TABLE_CONFUSE
            + " AS   select distinct data1 ,data2,data3,lookup,_id,sort_key,account_name, contact_id,display_name,photo_thumb_uri,fdn_contacts_subId from ( "
            + " SELECT contact_id,display_name,photo_thumb_uri,data1,data2,data3,lookup,_id,sort_key,fdn_contacts_subId,account_name FROM FdnContactSmsTable union  "
            + " SELECT contact_id,display_name,photo_thumb_uri,data1,data2,data3,lookup,_id,sort_key,fdn_contacts_subId,account_name FROM ContactSmsTable) as temp_table group by data1,fdn_contacts_subId";

    protected ArrayList<Integer> getSubIds(
            List<SubscriptionInfo> mSubscriptionInfolist) {
        if (mSubscriptionInfolist == null) {
            return null;
        }
        Iterator<SubscriptionInfo> iterator = mSubscriptionInfolist.iterator();
        if (iterator == null) {
            return null;
        }

        ArrayList<Integer> arrayList = new ArrayList<Integer>(
                mSubscriptionInfolist.size());
        while (iterator.hasNext()) {
            SubscriptionInfo subInfo = iterator.next();
            int phoneId = subInfo.getSimSlotIndex();
            arrayList.add(subInfo.getSubscriptionId());
        }
        return arrayList;
    }

    private ArrayList<Integer> getActivitySubIdList() {
        return getSubIds(SmsManager.getDefault().getActiveSubInfoList());
    }

    private boolean getFdnEnable(final Context context, int subID) {
        return SubscriptionManager.from(context).getFdnEnable(subID);
    }

    private boolean isMultiSimEnabled() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    private void addFdnContactsToTableBySubId(final Context context,
            String SubId) {
        initFromSim(SubId);
    }

    private String getSafeString(String szValue){
         return (szValue == null ? "" : szValue);
    }

    private String getFDNUri(String szSubid) {
        //
        if (szSubid == null || szSubid.equals("")) {
            return null;
        }

        if (isMultiSimEnabled()) {
            return URI_MORE_SUBID + szSubid;
        }
        else{
           return URI_SINGAL_SUB;
        }
    }

    private void initFromSim(String SubId) {
        int nCount = 0;
        Cursor cursor = null;
        int subid = Integer.parseInt(SubId);
        ContentValues[] contentValues  = null;

        if (getFDNUri(SubId) == null  ||  getFDNUri(SubId).equals("")) {
            Log.i("ContactsSmsProvider", "getFDNUri --- >null");
            return;
        }
        SQLiteDatabase database = sInstance.getReadableDatabase();
        database.execSQL("delete from FdnContactSmsTable");


        cursor = getContext().getContentResolver().query(
                Uri.parse(getFDNUri(SubId)), PROJECTIONFDNCONTACTS, null, null,
                null);
        if (null == cursor) {
            return;
        }
        else{
         contentValues = new ContentValues[cursor.getCount()];
        }


        Log.i("ContactsSmsProvider", "initFromSim --- >begin");
        try {
            while (cursor.moveToNext()) {
                Log.i("ContactsSmsProvider", "initFromSim -cursor.moveToNext-- >");
                ContentValues values = new ContentValues();
                values.put("contact_id", -2);
                values.put("display_name", getSafeString(cursor.getString(0)));
                values.put("photo_thumb_uri", "");
                values.put("data1", getSafeString(cursor.getString(1)));
                values.put("data2", "");
                values.put("data3", "");
                values.put("lookup", "");
                values.put("_id", "");
                values.put("sort_key", "");
                values.put("account_name", "");
                values.put("fdn_contacts_subId",subid);
                contentValues[nCount] = values;
                ++nCount;
            }
            BlukInsert(sInstance.getWritableDatabase(), nCount, contentValues,
                    CONTACTS_FDN_TABLE);
            Log.i("ContactsSmsProvider", "initFromSim --nCount- >"+nCount);

            Log.i("ContactsSmsProvider", "initFromSim --- >end");

            needRegisterUri(getFDNUri(SubId), getFdnObserver());

        } catch (Exception e) {
            Log.e(TAG,"Init FdnContacts",  e);
        }finally{
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }

            contentValues = null;
        }
    }

    private ArrayList<Integer> getActivitySubIds(final Context context) {
        return getActivitySubIdList();
    }

    public void initFdnHashTable() {

        ArrayList<Integer> subids = getActivitySubIds(getContext());
        if (null == subids) {
            Log.i("ContactsSmsProvider", "initFdnHashTable --- subids == null");
            return;
        }
        
        if ( subids.size() <= 0) {
            Log.i("ContactsSmsProvider", "initFdnHashTable --- no subids");
            return;
        }

        Log.i("ContactsSmsProvider", "initFdnHashTable--->subids.size() --- "
                + subids.size());
        for (int nIndex = 0; nIndex < subids.size(); ++nIndex) {
            addFdnContactsToTableBySubId(getContext(),
                    String.valueOf(subids.get(nIndex)));
        }
    }

    private int getSimstatus(Context context){
        TelephonyManager tm = (TelephonyManager)context.getSystemService(Service.TELEPHONY_SERVICE);
        return tm.getSimState();
    }

    public static String getFdnViewTable() {
        return VIEW_CONTACTS_FDN_TABLE;
    }

    public static String getLocalContactsViewTable() {
        return VIEW_CONTACTS_SMS_TABLE;
    }

    public static String getConfuseContactsViewTable() {
        return VIEW_CONTACTS_FDN_TABLE_CONFUSE;
    }

    private Handler getHandler() {
        if(mHandler == null){
            initHanlderThread();
        }
        return mHandler;
    }

    private static Context getContext() {
        return mContext;
    }

    private HandlerThread getInitHandlerThread() {
        return mHandlerThread;
    }

    private void initHanlderThread() {
        mHandlerThread = new HandlerThread("initContacts");
        mHandlerThread.setPriority(Thread.MIN_PRIORITY);
        mHandlerThread.start();
        initHandler(mHandlerThread.getLooper());
    }

    //sprd fix for 569683 start
    private void initBroadcastforSim() {
        mSimCardStatusBroadCast = new SimCardStatusBroadCast();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SIM_STATE_CHANGED);
        getContext().registerReceiver(mSimCardStatusBroadCast, filter);
    }
    //sprd fix for 569683 end

    private void initHandler(Looper looper){
        if(mHandler == null){
               mHandler = new UserHandle(looper);
        }
    }

    private void needRegisterUri(String szUri, ContentObserver observer) {
        if (szUri == null || observer == null) {
            return;
        } else if (getRegisterUri().contains(szUri)) {
            Log.e(TAG, "========>>>>>>System HAS Register URI =[" + szUri
                    + "] OBSERVER=[" + observer.getClass().toString() + "]");
            return;
        } else {
            getContext().getContentResolver().registerContentObserver(
                    Uri.parse(szUri), true, observer);
            getRegisterUri().add(szUri);
            Log.e(TAG, "<<<<======System Register URI =[" + szUri
                    + "] OBSERVER=[" + observer.getClass().toString()
                    + "]==>>>>>>");
        }
    }

    private ContentObserver getFdnObserver() {
        if (mFdnObserver == null) {
            mFdnObserver = new FdnContactContentOberver(getHandler());
        }
        return mFdnObserver;
    }

    private ContentObserver getContactsObserver() {
        if (mContactsObserver == null) {
            mContactsObserver = new ContactContentOberver(getHandler());
        }
        return mContactsObserver;
    }

    //568816 sprd start
    private ArrayList<String> getRegisterUri(){ return mRegisturi;}
    private ArrayList<String> mRegisturi = new ArrayList<String>();
    //568816 sprd end

    private int MAX_WAITING_TIMES = 60;
    private int MAX_COUNT = 50; // Max Translation
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private ContentObserver mFdnObserver  = null;// 568816
    private ContentObserver mContactsObserver  = null;// 568816 bug
    private SimCardStatusBroadCast mSimCardStatusBroadCast = null;

    /**********************************************************************************************************
     * Listen Database URI Change
     **********************************************************************************************************/
    class BaseContentObserver extends ContentObserver{

        public BaseContentObserver(Handler handler)
        {
            super(handler);
            mhandler = handler;
        }

        private Handler mhandler;
        protected  Handler getHander(){ return mhandler;}

    }

    class ContactContentOberver extends BaseContentObserver {
        public ContactContentOberver(Handler handler)
        {
            super(handler);
        }
        @Override
        public void onChange(boolean selfChange) {
            getHander().sendEmptyMessage(lOCALCONTACTS_UPDATA_FLAG);

        }
    }

    class FdnContactContentOberver extends BaseContentObserver {
        public FdnContactContentOberver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            getHander().sendEmptyMessage(FDNCONTACTS_UPDATA_FLAG);
          }
    }

    /*****************************************************************************
     * with this handler to deal with the init events
     *******************************************************************************/
    class UserHandle extends Handler
    {
         public  UserHandle(Looper looper)
         {
             super(looper);
         }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
          case lOCALCONTACTS_UPDATA_FLAG:
              initContactsDate();
              break;

          case FDNCONTACTS_UPDATA_FLAG:
              initFdnHashTable();
              break;
          case INIT_ALL_UPDATA_FLAG:
              initFirstFdnAndContactsTables();
              break;
          }
          super.handleMessage(msg);
       }
    }

    /**************************************************************************************
     * Fdn Add Function 2016/05/27 end //sprd: fix for telcel bug 557685 end
     **************************************************************************************/

    /*****************************************************************************
     * a broadcast to init fdncontacts
     * @data: 570184 sprd start
     * @version:  2016/6/04
     *******************************************************************************/
    private final static String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";
    private final static int SIM_VALID = 0;
    private final static int SIM_INVALID = 1;
    private int simState = SIM_INVALID;

    private class SimCardStatusBroadCast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            System.out.println("sim state changed");
            if (intent.getAction().equals(ACTION_SIM_STATE_CHANGED)) {
                switch (getSimstatus(getContext())) {
                case TelephonyManager.SIM_STATE_READY:
                    Log.i("ContactsSmsProvider",
                            "SimCardStatusBroadCast--->android.intent.action.SIM_STATE_CHANGED");
                    sendEmptyMesssagingSprd(getHandler(),
                            FDNCONTACTS_UPDATA_FLAG, 0);
                    sendEmptyMesssagingSprd(getHandler(),
                            lOCALCONTACTS_UPDATA_FLAG, 0);// when database if ok
                                                          // , we need to init
                                                          // the data when sim
                                                          // car is ready
                    break;
                case TelephonyManager.SIM_STATE_UNKNOWN:
                case TelephonyManager.SIM_STATE_ABSENT:
                case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                default:
                    break;
                }
            }
        }
    }
    // sprd 570184 start end
}
