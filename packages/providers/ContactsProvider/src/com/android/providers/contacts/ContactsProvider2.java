/*
 * Copyright (C) 2009 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.providers.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.SearchManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentService;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.SyncAdapterType;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DatabaseUtils;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.Process;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Authorization;
import android.provider.ContactsContract.CommonDataKinds.Contactables;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DataUsageFeedback;
import android.provider.ContactsContract.DeletedContacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.PhotoFiles;
import android.provider.ContactsContract.PinnedPositions;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.SearchSnippets;
import android.provider.ContactsContract.Settings;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.StreamItemPhotos;
import android.provider.ContactsContract.StreamItems;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.Media;
import android.provider.OpenableColumns;
import android.provider.Settings.Global;
import android.provider.SyncStateContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.common.content.ProjectionMap;
import com.android.common.content.SyncStateContentProviderHelper;
import com.android.common.io.MoreCloseables;
import com.android.internal.util.ArrayUtils;
import com.android.providers.contacts.ContactLookupKey.LookupKeySegment;
import com.android.providers.contacts.ContactsDatabaseHelper.AccountsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.AggregatedPresenceColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.AggregationExceptionColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Clauses;
import com.android.providers.contacts.ContactsDatabaseHelper.ContactsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.ContactsStatusUpdatesColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.DataColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.DataUsageStatColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.DbProperties;
import com.android.providers.contacts.ContactsDatabaseHelper.GroupsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Joins;
import com.android.providers.contacts.ContactsDatabaseHelper.NameLookupColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.NameLookupType;
import com.android.providers.contacts.ContactsDatabaseHelper.PhoneLookupColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.PhotoFilesColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.PreAuthorizedUris;
import com.android.providers.contacts.ContactsDatabaseHelper.PresenceColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Projections;
import com.android.providers.contacts.ContactsDatabaseHelper.RawContactsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.SearchIndexColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.SettingsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.StatusUpdatesColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.StreamItemPhotosColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.StreamItemsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Tables;
import com.android.providers.contacts.ContactsDatabaseHelper.ViewGroupsColumns;
import com.android.providers.contacts.ContactsDatabaseHelper.Views;
import com.android.providers.contacts.SearchIndexManager.FtsQueryBuilder;
import com.android.providers.contacts.aggregation.AbstractContactAggregator;
import com.android.providers.contacts.aggregation.AbstractContactAggregator.AggregationSuggestionParameter;
import com.android.providers.contacts.aggregation.ContactAggregator;
import com.android.providers.contacts.aggregation.ContactAggregator2;
import com.android.providers.contacts.aggregation.ProfileAggregator;
import com.android.providers.contacts.aggregation.util.CommonNicknameCache;
import com.android.providers.contacts.database.ContactsTableUtil;
import com.android.providers.contacts.database.DeletedContactsTableUtil;
import com.android.providers.contacts.database.MoreDatabaseUtils;
import com.android.providers.contacts.util.Clock;
import com.android.providers.contacts.util.ContactsPermissions;
import com.android.providers.contacts.util.DbQueryUtils;
import com.android.providers.contacts.util.NeededForTesting;
import com.android.providers.contacts.util.UserUtils;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.google.android.collect.Sets;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import libcore.io.IoUtils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * SPRD:
 *
 * @{
 */
import android.content.Intent;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.RawContactsMerge;
import com.android.internal.telephony.IccPBForMimetypeException;
import com.android.internal.telephony.IccPBForRecordException;
import com.sprd.providers.contacts.*;
import com.sprd.providers.util.AccountUtils;
import java.util.Iterator;

/**
 * @}
 */

/**
 * Contacts content provider. The contract between this provider and applications
 * is defined in {@link ContactsContract}.
 */
public class ContactsProvider2 extends AbstractContactsProvider
        implements OnAccountsUpdateListener {

    private static final String READ_PERMISSION = "android.permission.READ_CONTACTS";
    private static final String WRITE_PERMISSION = "android.permission.WRITE_CONTACTS";
    private static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";

    /* package */ static final String UPDATE_TIMES_CONTACTED_CONTACTS_TABLE =
          "UPDATE " + Tables.CONTACTS + " SET " + Contacts.TIMES_CONTACTED + "=" +
          " ifnull(" + Contacts.TIMES_CONTACTED + ",0)+1" +
          " WHERE " + Contacts._ID + "=?";

    /* package */ static final String UPDATE_TIMES_CONTACTED_RAWCONTACTS_TABLE =
          "UPDATE " + Tables.RAW_CONTACTS + " SET " + RawContacts.TIMES_CONTACTED + "=" +
          " ifnull(" + RawContacts.TIMES_CONTACTED + ",0)+1 " +
          " WHERE " + RawContacts.CONTACT_ID + "=?";

    /* package */ static final String PHONEBOOK_COLLATOR_NAME = "PHONEBOOK";

    // Regex for splitting query strings - we split on any group of non-alphanumeric characters,
    // excluding the @ symbol.
    /* package */ static final String QUERY_TOKENIZER_REGEX = "[^\\w@]+";

    // The database tag to use for representing the contacts DB in contacts transactions.
    /* package */ static final String CONTACTS_DB_TAG = "contacts";

    // The database tag to use for representing the profile DB in contacts transactions.
    /* package */ static final String PROFILE_DB_TAG = "profile";

    private static final String ACCOUNT_STRING_SEPARATOR_OUTER = "\u0001";
    private static final String ACCOUNT_STRING_SEPARATOR_INNER = "\u0002";

    private static final int BACKGROUND_TASK_INITIALIZE = 0;
    private static final int BACKGROUND_TASK_OPEN_WRITE_ACCESS = 1;
    private static final int BACKGROUND_TASK_UPDATE_ACCOUNTS = 3;
    private static final int BACKGROUND_TASK_UPDATE_LOCALE = 4;
    private static final int BACKGROUND_TASK_UPGRADE_AGGREGATION_ALGORITHM = 5;
    private static final int BACKGROUND_TASK_UPDATE_SEARCH_INDEX = 6;
    private static final int BACKGROUND_TASK_UPDATE_PROVIDER_STATUS = 7;
    private static final int BACKGROUND_TASK_UPDATE_DIRECTORIES = 8;
    private static final int BACKGROUND_TASK_CHANGE_LOCALE = 9;
    private static final int BACKGROUND_TASK_CLEANUP_PHOTOS = 10;
    private static final int BACKGROUND_TASK_CLEAN_DELETE_LOG = 11;

    protected static final int STATUS_NORMAL = 0;
    protected static final int STATUS_UPGRADING = 1;
    protected static final int STATUS_CHANGING_LOCALE = 2;
    protected static final int STATUS_NO_ACCOUNTS_NO_CONTACTS = 3;

    /** Default for the maximum number of returned aggregation suggestions. */
    private static final int DEFAULT_MAX_SUGGESTIONS = 5;

    /** Limit for the maximum number of social stream items to store under a raw contact. */
    private static final int MAX_STREAM_ITEMS_PER_RAW_CONTACT = 5;

    /** Rate limit (in milliseconds) for photo cleanup.  Do it at most once per day. */
    private static final int PHOTO_CLEANUP_RATE_LIMIT = 24 * 60 * 60 * 1000;

    /** Maximum length of a phone number that can be inserted into the database */
    private static final int PHONE_NUMBER_LENGTH_LIMIT = 1000;

    /**
     * Default expiration duration for pre-authorized URIs.  May be overridden from a secure
     * setting.
     */
    private static final int DEFAULT_PREAUTHORIZED_URI_EXPIRATION = 5 * 60 * 1000;

    private static final int USAGE_TYPE_ALL = -1;

    /**
     * Random URI parameter that will be appended to preauthorized URIs for uniqueness.
     */
    private static final String PREAUTHORIZED_URI_TOKEN = "perm_token";

    private static final String PREF_LOCALE = "locale";

    private static int PROPERTY_AGGREGATION_ALGORITHM_VERSION;

    private static final int AGGREGATION_ALGORITHM_OLD_VERSION = 4;

    private static final int AGGREGATION_ALGORITHM_NEW_VERSION = 5;

    private static final String AGGREGATE_CONTACTS = "sync.contacts.aggregate";

    private static final String CONTACT_MEMORY_FILE_NAME = "contactAssetFile";

    /**
     * If set to "1", we don't remove account data when accounts have been removed.
     *
     * This should be used sparingly; even though there are data still available, the UI
     * don't know anything about them, so they won't show up in the contact filter screen, and
     * the contact card/editor may get confused to see unknown custom mimetypes.
     *
     * We can't spell it out because a property name must be less than 32 chars.
     */
    private static final String DEBUG_PROPERTY_KEEP_STALE_ACCOUNT_DATA =
            "debug.contacts.ksad";

    private static final ProfileAwareUriMatcher sUriMatcher =
            new ProfileAwareUriMatcher(UriMatcher.NO_MATCH);

    private static final String FREQUENT_ORDER_BY = DataUsageStatColumns.TIMES_USED + " DESC,"
            + Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";

    private static final int CONTACTS = 1000;
    private static final int CONTACTS_ID = 1001;
    private static final int CONTACTS_LOOKUP = 1002;
    private static final int CONTACTS_LOOKUP_ID = 1003;
    private static final int CONTACTS_ID_DATA = 1004;
    private static final int CONTACTS_FILTER = 1005;
    private static final int CONTACTS_STREQUENT = 1006;
    private static final int CONTACTS_STREQUENT_FILTER = 1007;
    private static final int CONTACTS_GROUP = 1008;
    private static final int CONTACTS_ID_PHOTO = 1009;
    private static final int CONTACTS_LOOKUP_PHOTO = 1010;
    private static final int CONTACTS_LOOKUP_ID_PHOTO = 1011;
    private static final int CONTACTS_ID_DISPLAY_PHOTO = 1012;
    private static final int CONTACTS_LOOKUP_DISPLAY_PHOTO = 1013;
    private static final int CONTACTS_LOOKUP_ID_DISPLAY_PHOTO = 1014;
    private static final int CONTACTS_AS_VCARD = 1015;
    private static final int CONTACTS_AS_MULTI_VCARD = 1016;
    private static final int CONTACTS_LOOKUP_DATA = 1017;
    private static final int CONTACTS_LOOKUP_ID_DATA = 1018;
    private static final int CONTACTS_ID_ENTITIES = 1019;
    private static final int CONTACTS_LOOKUP_ENTITIES = 1020;
    private static final int CONTACTS_LOOKUP_ID_ENTITIES = 1021;
    private static final int CONTACTS_ID_STREAM_ITEMS = 1022;
    private static final int CONTACTS_LOOKUP_STREAM_ITEMS = 1023;
    private static final int CONTACTS_LOOKUP_ID_STREAM_ITEMS = 1024;
    private static final int CONTACTS_FREQUENT = 1025;
    private static final int CONTACTS_DELETE_USAGE = 1026;
    private static final int CONTACTS_ID_PHOTO_CORP = 1027;
    private static final int CONTACTS_ID_DISPLAY_PHOTO_CORP = 1028;

    private static final int RAW_CONTACTS = 2002;
    private static final int RAW_CONTACTS_ID = 2003;
    private static final int RAW_CONTACTS_ID_DATA = 2004;
    private static final int RAW_CONTACT_ID_ENTITY = 2005;
    private static final int RAW_CONTACTS_ID_DISPLAY_PHOTO = 2006;
    private static final int RAW_CONTACTS_ID_STREAM_ITEMS = 2007;
    private static final int RAW_CONTACTS_ID_STREAM_ITEMS_ID = 2008;

    private static final int DATA = 3000;
    private static final int DATA_ID = 3001;
    private static final int PHONES = 3002;
    private static final int PHONES_ID = 3003;
    private static final int PHONES_FILTER = 3004;
    private static final int EMAILS = 3005;
    private static final int EMAILS_ID = 3006;
    private static final int EMAILS_LOOKUP = 3007;
    private static final int EMAILS_FILTER = 3008;
    private static final int POSTALS = 3009;
    private static final int POSTALS_ID = 3010;
    private static final int CALLABLES = 3011;
    private static final int CALLABLES_ID = 3012;
    private static final int CALLABLES_FILTER = 3013;
    private static final int CONTACTABLES = 3014;
    private static final int CONTACTABLES_FILTER = 3015;
    private static final int PHONES_ENTERPRISE = 3016;
    private static final int EMAILS_LOOKUP_ENTERPRISE = 3017;

    private static final int PHONE_LOOKUP = 4000;
    private static final int PHONE_LOOKUP_ENTERPRISE = 4001;

    private static final int AGGREGATION_EXCEPTIONS = 6000;
    private static final int AGGREGATION_EXCEPTION_ID = 6001;

    private static final int STATUS_UPDATES = 7000;
    private static final int STATUS_UPDATES_ID = 7001;

    private static final int AGGREGATION_SUGGESTIONS = 8000;

    private static final int SETTINGS = 9000;

    private static final int GROUPS = 10000;
    private static final int GROUPS_ID = 10001;
    private static final int GROUPS_SUMMARY = 10003;

    private static final int SYNCSTATE = 11000;
    private static final int SYNCSTATE_ID = 11001;
    private static final int PROFILE_SYNCSTATE = 11002;
    private static final int PROFILE_SYNCSTATE_ID = 11003;

    private static final int SEARCH_SUGGESTIONS = 12001;
    private static final int SEARCH_SHORTCUT = 12002;

    private static final int RAW_CONTACT_ENTITIES = 15001;
    private static final int RAW_CONTACT_ENTITIES_CORP = 15002;

    private static final int PROVIDER_STATUS = 16001;

    private static final int DIRECTORIES = 17001;
    private static final int DIRECTORIES_ID = 17002;

    private static final int COMPLETE_NAME = 18000;

    private static final int PROFILE = 19000;
    private static final int PROFILE_ENTITIES = 19001;
    private static final int PROFILE_DATA = 19002;
    private static final int PROFILE_DATA_ID = 19003;
    private static final int PROFILE_AS_VCARD = 19004;
    private static final int PROFILE_RAW_CONTACTS = 19005;
    private static final int PROFILE_RAW_CONTACTS_ID = 19006;
    private static final int PROFILE_RAW_CONTACTS_ID_DATA = 19007;
    private static final int PROFILE_RAW_CONTACTS_ID_ENTITIES = 19008;
    private static final int PROFILE_STATUS_UPDATES = 19009;
    private static final int PROFILE_RAW_CONTACT_ENTITIES = 19010;
    private static final int PROFILE_PHOTO = 19011;
    private static final int PROFILE_DISPLAY_PHOTO = 19012;

    private static final int DATA_USAGE_FEEDBACK_ID = 20001;

    private static final int STREAM_ITEMS = 21000;
    private static final int STREAM_ITEMS_PHOTOS = 21001;
    private static final int STREAM_ITEMS_ID = 21002;
    private static final int STREAM_ITEMS_ID_PHOTOS = 21003;
    private static final int STREAM_ITEMS_ID_PHOTOS_ID = 21004;
    private static final int STREAM_ITEMS_LIMIT = 21005;

    private static final int DISPLAY_PHOTO_ID = 22000;
    private static final int PHOTO_DIMENSIONS = 22001;

    private static final int DELETED_CONTACTS = 23000;
    private static final int DELETED_CONTACTS_ID = 23001;

     /**
     * SPRD: Bug 474798 SprdAppBackup for Contacts.
     *
     * @{
     */
    private static final int RAW_CONTACTS_UNIQUE = 2050;
    /**
     * @}
     */
    /**
     * SPRD: Bug 474752 Add features with multiSelection activity in Contacts.
     *
     * @{
     */
    private static final int DATA_FILTER = 3020;
    /**
     * @}
     */
    /**
     * SPRD: bug441929, for custom display mode query
     *
     * @{
     */
    private static final int CONTACTS_CUSTOM = 50000;
    /**
     * @}
     */

    /**
     * SPRD: bug474742, for cleaning up contacts feature
     *
     * @{
     */
    private static final int RAW_CONTACTS_MERGE = 2051;
    private static final int RAW_CONTACTS_JOIN = 2052;
    private static final int RAW_CONTACTS_UNIQUE_NOSIM = 2053;
    private static final int DATA_DEDUPLICATE_CANDIDATES = 3025;
    /**
     * @}
     */

    // Inserts into URIs in this map will direct to the profile database if the parent record's
    // value (looked up from the ContentValues object with the key specified by the value in this
    // map) is in the profile ID-space (see {@link ProfileDatabaseHelper#PROFILE_ID_SPACE}).
    private static final Map<Integer, String> INSERT_URI_ID_VALUE_MAP = Maps.newHashMap();
    static {
        INSERT_URI_ID_VALUE_MAP.put(DATA, Data.RAW_CONTACT_ID);
        INSERT_URI_ID_VALUE_MAP.put(RAW_CONTACTS_ID_DATA, Data.RAW_CONTACT_ID);
        INSERT_URI_ID_VALUE_MAP.put(STATUS_UPDATES, StatusUpdates.DATA_ID);
        INSERT_URI_ID_VALUE_MAP.put(STREAM_ITEMS, StreamItems.RAW_CONTACT_ID);
        INSERT_URI_ID_VALUE_MAP.put(RAW_CONTACTS_ID_STREAM_ITEMS, StreamItems.RAW_CONTACT_ID);
        INSERT_URI_ID_VALUE_MAP.put(STREAM_ITEMS_PHOTOS, StreamItemPhotos.STREAM_ITEM_ID);
        INSERT_URI_ID_VALUE_MAP.put(STREAM_ITEMS_ID_PHOTOS, StreamItemPhotos.STREAM_ITEM_ID);
    }

    // Any interactions that involve these URIs will also require the calling package to have either
    // android.permission.READ_SOCIAL_STREAM permission or android.permission.WRITE_SOCIAL_STREAM
    // permission, depending on the type of operation being performed.
    private static final List<Integer> SOCIAL_STREAM_URIS = Lists.newArrayList(
            CONTACTS_ID_STREAM_ITEMS,
            CONTACTS_LOOKUP_STREAM_ITEMS,
            CONTACTS_LOOKUP_ID_STREAM_ITEMS,
            RAW_CONTACTS_ID_STREAM_ITEMS,
            RAW_CONTACTS_ID_STREAM_ITEMS_ID,
            STREAM_ITEMS,
            STREAM_ITEMS_PHOTOS,
            STREAM_ITEMS_ID,
            STREAM_ITEMS_ID_PHOTOS,
            STREAM_ITEMS_ID_PHOTOS_ID
    );

    private static final String SELECTION_FAVORITES_GROUPS_BY_RAW_CONTACT_ID =
            RawContactsColumns.CONCRETE_ID + "=? AND "
                + GroupsColumns.CONCRETE_ACCOUNT_ID + "=" + RawContactsColumns.CONCRETE_ACCOUNT_ID
                + " AND " + Groups.FAVORITES + " != 0";

    private static final String SELECTION_AUTO_ADD_GROUPS_BY_RAW_CONTACT_ID =
            RawContactsColumns.CONCRETE_ID + "=? AND "
                + GroupsColumns.CONCRETE_ACCOUNT_ID + "=" + RawContactsColumns.CONCRETE_ACCOUNT_ID
                + " AND " + Groups.AUTO_ADD + " != 0";

    private static final String[] PROJECTION_GROUP_ID
            = new String[] {Tables.GROUPS + "." + Groups._ID};

    private static final String SELECTION_GROUPMEMBERSHIP_DATA = DataColumns.MIMETYPE_ID + "=? "
            + "AND " + GroupMembership.GROUP_ROW_ID + "=? "
            + "AND " + GroupMembership.RAW_CONTACT_ID + "=?";

    private static final String SELECTION_STARRED_FROM_RAW_CONTACTS =
            "SELECT " + RawContacts.STARRED
                    + " FROM " + Tables.RAW_CONTACTS + " WHERE " + RawContacts._ID + "=?";

    private interface DataContactsQuery {
        public static final String TABLE = "data "
                + "JOIN raw_contacts ON (data.raw_contact_id = raw_contacts._id) "
                + "JOIN " + Tables.ACCOUNTS + " ON ("
                    + AccountsColumns.CONCRETE_ID + "=" + RawContactsColumns.CONCRETE_ACCOUNT_ID
                    + ")"
                + "JOIN contacts ON (raw_contacts.contact_id = contacts._id)";

        public static final String[] PROJECTION = new String[] {
            RawContactsColumns.CONCRETE_ID,
            AccountsColumns.CONCRETE_ACCOUNT_TYPE,
            AccountsColumns.CONCRETE_ACCOUNT_NAME,
            AccountsColumns.CONCRETE_DATA_SET,
            DataColumns.CONCRETE_ID,
            ContactsColumns.CONCRETE_ID
        };

        public static final int RAW_CONTACT_ID = 0;
        public static final int ACCOUNT_TYPE = 1;
        public static final int ACCOUNT_NAME = 2;
        public static final int DATA_SET = 3;
        public static final int DATA_ID = 4;
        public static final int CONTACT_ID = 5;
    }

    interface RawContactsQuery {
        String TABLE = Tables.RAW_CONTACTS_JOIN_ACCOUNTS;

        String[] COLUMNS = new String[] {
                RawContacts.DELETED,
                RawContactsColumns.ACCOUNT_ID,
                AccountsColumns.CONCRETE_ACCOUNT_TYPE,
                AccountsColumns.CONCRETE_ACCOUNT_NAME,
                AccountsColumns.CONCRETE_DATA_SET,
        };

        int DELETED = 0;
        int ACCOUNT_ID = 1;
        int ACCOUNT_TYPE = 2;
        int ACCOUNT_NAME = 3;
        int DATA_SET = 4;
    }
    /**
     * SPRD: bug421665
     * Original Android code:
     * @{
    private static final String DEFAULT_ACCOUNT_TYPE = "com.google";
     */
    private static final String DEFAULT_ACCOUNT_TYPE = "sprd.com.android.account.phone";
    /**
     * @}
     */
    /** Sql where statement for filtering on groups. */
    private static final String CONTACTS_IN_GROUP_SELECT =
            Contacts._ID + " IN "
                    + "(SELECT " + RawContacts.CONTACT_ID
                    + " FROM " + Tables.RAW_CONTACTS
                    + " WHERE " + RawContactsColumns.CONCRETE_ID + " IN "
                            + "(SELECT " + DataColumns.CONCRETE_RAW_CONTACT_ID
                            + " FROM " + Tables.DATA_JOIN_MIMETYPES
                            + " WHERE " + DataColumns.MIMETYPE_ID + "=?"
                                    + " AND " + GroupMembership.GROUP_ROW_ID + "="
                                    + "(SELECT " + Tables.GROUPS + "." + Groups._ID
                                    + " FROM " + Tables.GROUPS
                                    + " WHERE " + Groups.TITLE + "=?)))";

    /** Sql for updating DIRTY flag on multiple raw contacts */
    private static final String UPDATE_RAW_CONTACT_SET_DIRTY_SQL =
            "UPDATE " + Tables.RAW_CONTACTS +
            " SET " + RawContacts.DIRTY + "=1" +
            " WHERE " + RawContacts._ID + " IN (";

    /** Sql for updating VERSION on multiple raw contacts */
    private static final String UPDATE_RAW_CONTACT_SET_VERSION_SQL =
            "UPDATE " + Tables.RAW_CONTACTS +
            " SET " + RawContacts.VERSION + " = " + RawContacts.VERSION + " + 1" +
            " WHERE " + RawContacts._ID + " IN (";

    /** Sql for undemoting a demoted contact **/
    private static final String UNDEMOTE_CONTACT =
            "UPDATE " + Tables.CONTACTS +
            " SET " + Contacts.PINNED + " = " + PinnedPositions.UNPINNED +
            " WHERE " + Contacts._ID + " = ?1 AND " + Contacts.PINNED + " <= " +
            PinnedPositions.DEMOTED;

    /** Sql for undemoting a demoted raw contact **/
    private static final String UNDEMOTE_RAW_CONTACT =
            "UPDATE " + Tables.RAW_CONTACTS +
            " SET " + RawContacts.PINNED + " = " + PinnedPositions.UNPINNED +
            " WHERE " + RawContacts.CONTACT_ID + " = ?1 AND " + Contacts.PINNED + " <= " +
            PinnedPositions.DEMOTED;

    // Contacts contacted within the last 3 days (in seconds)
    private static final long LAST_TIME_USED_3_DAYS_SEC = 3L * 24 * 60 * 60;

    // Contacts contacted within the last 7 days (in seconds)
    private static final long LAST_TIME_USED_7_DAYS_SEC = 7L * 24 * 60 * 60;

    // Contacts contacted within the last 14 days (in seconds)
    private static final long LAST_TIME_USED_14_DAYS_SEC = 14L * 24 * 60 * 60;

    // Contacts contacted within the last 30 days (in seconds)
    private static final long LAST_TIME_USED_30_DAYS_SEC = 30L * 24 * 60 * 60;

    private static final String TIME_SINCE_LAST_USED_SEC =
            "(strftime('%s', 'now') - " + DataUsageStatColumns.LAST_TIME_USED + "/1000)";

    private static final String SORT_BY_DATA_USAGE =
            "(CASE WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_3_DAYS_SEC +
            " THEN 0 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_7_DAYS_SEC +
            " THEN 1 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_14_DAYS_SEC +
            " THEN 2 " +
                    " WHEN " + TIME_SINCE_LAST_USED_SEC + " < " + LAST_TIME_USED_30_DAYS_SEC +
            " THEN 3 " +
            " ELSE 4 END), " +
            DataUsageStatColumns.TIMES_USED + " DESC";

    /*
     * Sorting order for email address suggestions: first starred, then the rest.
     * Within the two groups:
     * - three buckets: very recently contacted, then fairly recently contacted, then the rest.
     * Within each of the bucket - descending count of times contacted (both for data row and for
     * contact row).
     * If all else fails, in_visible_group, alphabetical.
     * (Super)primary email address is returned before other addresses for the same contact.
     */
    private static final String EMAIL_FILTER_SORT_ORDER =
        Contacts.STARRED + " DESC, "
        + Data.IS_SUPER_PRIMARY + " DESC, "
        + SORT_BY_DATA_USAGE + ", "
        + Contacts.IN_VISIBLE_GROUP + " DESC, "
        + Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC, "
        + Data.CONTACT_ID + ", "
        + Data.IS_PRIMARY + " DESC";

    /** Currently same as {@link #EMAIL_FILTER_SORT_ORDER} */
    private static final String PHONE_FILTER_SORT_ORDER = EMAIL_FILTER_SORT_ORDER;

    /** Name lookup types used for contact filtering */
    private static final String CONTACT_LOOKUP_NAME_TYPES =
            NameLookupType.NAME_COLLATION_KEY + "," +
            NameLookupType.EMAIL_BASED_NICKNAME + "," +
            NameLookupType.NICKNAME;

    /**
     * If any of these columns are used in a Data projection, there is no point in
     * using the DISTINCT keyword, which can negatively affect performance.
     */
    private static final String[] DISTINCT_DATA_PROHIBITING_COLUMNS = {
            Data._ID,
            Data.RAW_CONTACT_ID,
            Data.NAME_RAW_CONTACT_ID,
            RawContacts.ACCOUNT_NAME,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
            RawContacts.DIRTY,
            RawContacts.SOURCE_ID,
            RawContacts.VERSION,
    };

    private static final ProjectionMap sContactsColumns = ProjectionMap.builder()
            .add(Contacts.CUSTOM_RINGTONE)
            .add(Contacts.DISPLAY_NAME)
            .add(Contacts.DISPLAY_NAME_ALTERNATIVE)
            .add(Contacts.DISPLAY_NAME_SOURCE)
            .add(Contacts.IN_DEFAULT_DIRECTORY)
            /**
             * SPRD:
             *
             * @{
             */
            .add(Contacts.DISPLAY_ACCOUNT_TYPE)
            .add(Contacts.DISPLAY_ACCOUNT_NAME)
            .add(RawContacts.SYNC1)
            /**
             * @}
             */
            .add("is_read_only")
            .add(Contacts.IN_VISIBLE_GROUP)
            .add(Contacts.LAST_TIME_CONTACTED)
            .add(Contacts.LOOKUP_KEY)
            .add(Contacts.PHONETIC_NAME)
            .add(Contacts.PHONETIC_NAME_STYLE)
            .add(Contacts.PHOTO_ID)
            .add(Contacts.PHOTO_FILE_ID)
            .add(Contacts.PHOTO_URI)
            .add(Contacts.PHOTO_THUMBNAIL_URI)
            .add(Contacts.SEND_TO_VOICEMAIL)
            .add(Contacts.SORT_KEY_ALTERNATIVE)
            .add(Contacts.SORT_KEY_PRIMARY)
            .add(ContactsColumns.PHONEBOOK_LABEL_PRIMARY)
            .add(ContactsColumns.PHONEBOOK_BUCKET_PRIMARY)
            .add(ContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE)
            .add(ContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE)
            .add(Contacts.STARRED)
            .add(Contacts.PINNED)
            .add(Contacts.TIMES_CONTACTED)
            .add(Contacts.HAS_PHONE_NUMBER)
            .add(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            .build();

    private static final ProjectionMap sContactsPresenceColumns = ProjectionMap.builder()
            .add(Contacts.CONTACT_PRESENCE,
                    Tables.AGGREGATED_PRESENCE + "." + StatusUpdates.PRESENCE)
            .add(Contacts.CONTACT_CHAT_CAPABILITY,
                    Tables.AGGREGATED_PRESENCE + "." + StatusUpdates.CHAT_CAPABILITY)
            .add(Contacts.CONTACT_STATUS,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS)
            .add(Contacts.CONTACT_STATUS_TIMESTAMP,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_TIMESTAMP)
            .add(Contacts.CONTACT_STATUS_RES_PACKAGE,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_RES_PACKAGE)
            .add(Contacts.CONTACT_STATUS_LABEL,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_LABEL)
            .add(Contacts.CONTACT_STATUS_ICON,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_ICON)
            .build();

    private static final ProjectionMap sSnippetColumns = ProjectionMap.builder()
            .add(SearchSnippets.SNIPPET)
            .build();

    private static final ProjectionMap sRawContactColumns = ProjectionMap.builder()
            .add(RawContacts.ACCOUNT_NAME)
            .add(RawContacts.ACCOUNT_TYPE)
            .add(RawContacts.DATA_SET)
            .add(RawContacts.ACCOUNT_TYPE_AND_DATA_SET)
            .add(RawContacts.DIRTY)
            .add(RawContacts.SOURCE_ID)
            .add(RawContacts.BACKUP_ID)
            .add(RawContacts.VERSION)
            .build();

    private static final ProjectionMap sRawContactSyncColumns = ProjectionMap.builder()
            .add(RawContacts.SYNC1)
            .add(RawContacts.SYNC2)
            .add(RawContacts.SYNC3)
            .add(RawContacts.SYNC4)
            .build();

    private static final ProjectionMap sDataColumns = ProjectionMap.builder()
            .add(Data.DATA1)
            .add(Data.DATA2)
            .add(Data.DATA3)
            .add(Data.DATA4)
            .add(Data.DATA5)
            .add(Data.DATA6)
            .add(Data.DATA7)
            .add(Data.DATA8)
            .add(Data.DATA9)
            .add(Data.DATA10)
            .add(Data.DATA11)
            .add(Data.DATA12)
            .add(Data.DATA13)
            .add(Data.DATA14)
            .add(Data.DATA15)
            .add(Data.CARRIER_PRESENCE)
            .add(Data.DATA_VERSION)
            .add(Data.IS_PRIMARY)
            .add(Data.IS_SUPER_PRIMARY)
            .add(Data.MIMETYPE)
            .add(Data.RES_PACKAGE)
            .add(Data.SYNC1)
            .add(Data.SYNC2)
            .add(Data.SYNC3)
            .add(Data.SYNC4)
            .add(GroupMembership.GROUP_SOURCE_ID)
            .build();

    private static final ProjectionMap sContactPresenceColumns = ProjectionMap.builder()
            .add(Contacts.CONTACT_PRESENCE,
                    Tables.AGGREGATED_PRESENCE + '.' + StatusUpdates.PRESENCE)
            .add(Contacts.CONTACT_CHAT_CAPABILITY,
                    Tables.AGGREGATED_PRESENCE + '.' + StatusUpdates.CHAT_CAPABILITY)
            .add(Contacts.CONTACT_STATUS,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS)
            .add(Contacts.CONTACT_STATUS_TIMESTAMP,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_TIMESTAMP)
            .add(Contacts.CONTACT_STATUS_RES_PACKAGE,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_RES_PACKAGE)
            .add(Contacts.CONTACT_STATUS_LABEL,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_LABEL)
            .add(Contacts.CONTACT_STATUS_ICON,
                    ContactsStatusUpdatesColumns.CONCRETE_STATUS_ICON)
            .build();

    private static final ProjectionMap sDataPresenceColumns = ProjectionMap.builder()
            .add(Data.PRESENCE, Tables.PRESENCE + "." + StatusUpdates.PRESENCE)
            .add(Data.CHAT_CAPABILITY, Tables.PRESENCE + "." + StatusUpdates.CHAT_CAPABILITY)
            .add(Data.STATUS, StatusUpdatesColumns.CONCRETE_STATUS)
            .add(Data.STATUS_TIMESTAMP, StatusUpdatesColumns.CONCRETE_STATUS_TIMESTAMP)
            .add(Data.STATUS_RES_PACKAGE, StatusUpdatesColumns.CONCRETE_STATUS_RES_PACKAGE)
            .add(Data.STATUS_LABEL, StatusUpdatesColumns.CONCRETE_STATUS_LABEL)
            .add(Data.STATUS_ICON, StatusUpdatesColumns.CONCRETE_STATUS_ICON)
            .build();

    private static final ProjectionMap sDataUsageColumns = ProjectionMap.builder()
            .add(Data.TIMES_USED, Tables.DATA_USAGE_STAT + "." + Data.TIMES_USED)
            .add(Data.LAST_TIME_USED, Tables.DATA_USAGE_STAT + "." + Data.LAST_TIME_USED)
            .build();

    /** Contains just BaseColumns._COUNT */
    private static final ProjectionMap sCountProjectionMap = ProjectionMap.builder()
            .add(BaseColumns._COUNT, "COUNT(*)")
            .build();

    /** Contains just the contacts columns */
    private static final ProjectionMap sContactsProjectionMap = ProjectionMap.builder()
            .add(Contacts._ID)
            .add(Contacts.HAS_PHONE_NUMBER)
            .add(Contacts.NAME_RAW_CONTACT_ID)
            .add(Contacts.IS_USER_PROFILE)
            .addAll(sContactsColumns)
            .addAll(sContactsPresenceColumns)
            .build();

    /** Contains just the contacts columns */
    private static final ProjectionMap sContactsProjectionWithSnippetMap = ProjectionMap.builder()
            .addAll(sContactsProjectionMap)
            .addAll(sSnippetColumns)
            .build();

    /** Used for pushing starred contacts to the top of a times contacted list **/
    private static final ProjectionMap sStrequentStarredProjectionMap = ProjectionMap.builder()
            .addAll(sContactsProjectionMap)
            .add(DataUsageStatColumns.TIMES_USED, String.valueOf(Long.MAX_VALUE))
            .add(DataUsageStatColumns.LAST_TIME_USED, String.valueOf(Long.MAX_VALUE))
            .build();

    private static final ProjectionMap sStrequentFrequentProjectionMap = ProjectionMap.builder()
            .addAll(sContactsProjectionMap)
            .add(DataUsageStatColumns.TIMES_USED,
                    "SUM(" + DataUsageStatColumns.CONCRETE_TIMES_USED + ")")
            .add(DataUsageStatColumns.LAST_TIME_USED,
                    "MAX(" + DataUsageStatColumns.CONCRETE_LAST_TIME_USED + ")")
            .build();

    /**
     * Used for Strequent URI with {@link ContactsContract#STREQUENT_PHONE_ONLY}, which allows
     * users to obtain part of Data columns. We hard-code {@link Contacts#IS_USER_PROFILE} to NULL,
     * because sContactsProjectionMap specifies a field that doesn't exist in the view behind the
     * query that uses this projection map.
     **/
    private static final ProjectionMap sStrequentPhoneOnlyProjectionMap
            = ProjectionMap.builder()
            .addAll(sContactsProjectionMap)
            .add(DataUsageStatColumns.TIMES_USED, DataUsageStatColumns.CONCRETE_TIMES_USED)
            .add(DataUsageStatColumns.LAST_TIME_USED, DataUsageStatColumns.CONCRETE_LAST_TIME_USED)
            .add(Phone.NUMBER)
            .add(Phone.TYPE)
            .add(Phone.LABEL)
            .add(Phone.IS_SUPER_PRIMARY)
            .add(Phone.CONTACT_ID)
            .add(Contacts.IS_USER_PROFILE, "NULL")
            .build();

    /** Contains just the contacts vCard columns */
    private static final ProjectionMap sContactsVCardProjectionMap = ProjectionMap.builder()
            .add(Contacts._ID)
            .add(OpenableColumns.DISPLAY_NAME, Contacts.DISPLAY_NAME + " || '.vcf'")
            .add(OpenableColumns.SIZE, "NULL")
            .build();

    /** Contains just the raw contacts columns */
    private static final ProjectionMap sRawContactsProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.CONTACT_ID)
            .add(RawContacts.DELETED)
            .add(RawContacts.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.DISPLAY_NAME_ALTERNATIVE)
            .add(RawContacts.DISPLAY_NAME_SOURCE)
            .add(RawContacts.PHONETIC_NAME)
            .add(RawContacts.PHONETIC_NAME_STYLE)
            .add(RawContacts.SORT_KEY_PRIMARY)
            .add(RawContacts.SORT_KEY_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_LABEL_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_PRIMARY)
            .add(RawContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE)
            .add(RawContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE)
            .add(RawContacts.TIMES_CONTACTED)
            .add(RawContacts.LAST_TIME_CONTACTED)
            .add(RawContacts.CUSTOM_RINGTONE)
            .add(RawContacts.SEND_TO_VOICEMAIL)
            .add(RawContacts.STARRED)
            .add(RawContacts.PINNED)
            .add(RawContacts.AGGREGATION_MODE)
            .add(RawContacts.RAW_CONTACT_IS_USER_PROFILE)
            .addAll(sRawContactColumns)
            .addAll(sRawContactSyncColumns)
            .build();

    /** Contains the columns from the raw entity view*/
    private static final ProjectionMap sRawEntityProjectionMap = ProjectionMap.builder()
            .add(RawContacts._ID)
            .add(RawContacts.CONTACT_ID)
            .add(RawContacts.Entity.DATA_ID)
            .add(RawContacts.DELETED)
            .add(RawContacts.STARRED)
            .add(RawContacts.RAW_CONTACT_IS_USER_PROFILE)
            .addAll(sRawContactColumns)
            .addAll(sRawContactSyncColumns)
            .addAll(sDataColumns)
            .build();

    /** Contains the columns from the contact entity view*/
    private static final ProjectionMap sEntityProjectionMap = ProjectionMap.builder()
            .add(Contacts.Entity._ID)
            .add(Contacts.Entity.CONTACT_ID)
            .add(Contacts.Entity.RAW_CONTACT_ID)
            .add(Contacts.Entity.DATA_ID)
            .add(Contacts.Entity.NAME_RAW_CONTACT_ID)
            .add(Contacts.Entity.DELETED)
            .add(Contacts.IS_USER_PROFILE)
            .addAll(sContactsColumns)
            .addAll(sContactPresenceColumns)
            .addAll(sRawContactColumns)
            .add("is_read_only")
            .addAll(sRawContactSyncColumns)
            .addAll(sDataColumns)
            .addAll(sDataPresenceColumns)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns in PhoneLookup which are not contained in the data view. */
    private static final ProjectionMap sSipLookupColumns = ProjectionMap.builder()
            .add(PhoneLookup.NUMBER, SipAddress.SIP_ADDRESS)
            .add(PhoneLookup.TYPE, "0")
            .add(PhoneLookup.LABEL, "NULL")
            .add(PhoneLookup.NORMALIZED_NUMBER, "NULL")
            .build();

    /** Contains columns from the data view */
    private static final ProjectionMap sDataProjectionMap = ProjectionMap.builder()
            .add(Data._ID)
            .add(Data.RAW_CONTACT_ID)
            .add(Data.HASH_ID)
            .add(Data.CONTACT_ID)
            .add(Data.NAME_RAW_CONTACT_ID)
            .add(RawContacts.RAW_CONTACT_IS_USER_PROFILE)
            .addAll(sDataColumns)
            .addAll(sDataPresenceColumns)
            .addAll(sRawContactColumns)
            .addAll(sContactsColumns)
            .addAll(sContactPresenceColumns)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns from the data view used for SIP address lookup. */
    private static final ProjectionMap sDataSipLookupProjectionMap = ProjectionMap.builder()
            .addAll(sDataProjectionMap)
            .addAll(sSipLookupColumns)
            .build();

    /** Contains columns from the data view */
    private static final ProjectionMap sDistinctDataProjectionMap = ProjectionMap.builder()
            .add(Data._ID, "MIN(" + Data._ID + ")")
            .add(RawContacts.CONTACT_ID)
            .add(RawContacts.RAW_CONTACT_IS_USER_PROFILE)
            .addAll(sDataColumns)
            .addAll(sDataPresenceColumns)
            .addAll(sContactsColumns)
            .addAll(sContactPresenceColumns)
            .addAll(sDataUsageColumns)
            .build();

    /** Contains columns from the data view used for SIP address lookup. */
    private static final ProjectionMap sDistinctDataSipLookupProjectionMap = ProjectionMap.builder()
            .addAll(sDistinctDataProjectionMap)
            .addAll(sSipLookupColumns)
            .build();

    /** Contains the data and contacts columns, for joined tables */
    private static final ProjectionMap sPhoneLookupProjectionMap = ProjectionMap.builder()
            .add(PhoneLookup._ID, "contacts_view." + Contacts._ID)
            .add(PhoneLookup.LOOKUP_KEY, "contacts_view." + Contacts.LOOKUP_KEY)
            .add(PhoneLookup.DISPLAY_NAME, "contacts_view." + Contacts.DISPLAY_NAME)
            .add(PhoneLookup.LAST_TIME_CONTACTED, "contacts_view." + Contacts.LAST_TIME_CONTACTED)
            .add(PhoneLookup.TIMES_CONTACTED, "contacts_view." + Contacts.TIMES_CONTACTED)
            .add(PhoneLookup.STARRED, "contacts_view." + Contacts.STARRED)
            .add(PhoneLookup.IN_DEFAULT_DIRECTORY, "contacts_view." + Contacts.IN_DEFAULT_DIRECTORY)
            .add(PhoneLookup.IN_VISIBLE_GROUP, "contacts_view." + Contacts.IN_VISIBLE_GROUP)
            .add(PhoneLookup.PHOTO_ID, "contacts_view." + Contacts.PHOTO_ID)
            .add(PhoneLookup.PHOTO_FILE_ID, "contacts_view." + Contacts.PHOTO_FILE_ID)
            .add(PhoneLookup.PHOTO_URI, "contacts_view." + Contacts.PHOTO_URI)
            .add(PhoneLookup.PHOTO_THUMBNAIL_URI, "contacts_view." + Contacts.PHOTO_THUMBNAIL_URI)
            .add(PhoneLookup.CUSTOM_RINGTONE, "contacts_view." + Contacts.CUSTOM_RINGTONE)
            .add(PhoneLookup.HAS_PHONE_NUMBER, "contacts_view." + Contacts.HAS_PHONE_NUMBER)
            .add(PhoneLookup.SEND_TO_VOICEMAIL, "contacts_view." + Contacts.SEND_TO_VOICEMAIL)
            .add(PhoneLookup.NUMBER, Phone.NUMBER)
            .add(PhoneLookup.TYPE, Phone.TYPE)
            .add(PhoneLookup.LABEL, Phone.LABEL)
            .add(PhoneLookup.NORMALIZED_NUMBER, Phone.NORMALIZED_NUMBER)
            .build();

    /** Contains the just the {@link Groups} columns */
    private static final ProjectionMap sGroupsProjectionMap = ProjectionMap.builder()
            .add(Groups._ID)
            .add(Groups.ACCOUNT_NAME)
            .add(Groups.ACCOUNT_TYPE)
            .add(Groups.DATA_SET)
            .add(Groups.ACCOUNT_TYPE_AND_DATA_SET)
            .add(Groups.SOURCE_ID)
            .add(Groups.DIRTY)
            .add(Groups.VERSION)
            .add(Groups.RES_PACKAGE)
            .add(Groups.TITLE)
            .add(Groups.TITLE_RES)
            .add(Groups.GROUP_VISIBLE)
            .add(Groups.SYSTEM_ID)
            .add(Groups.DELETED)
            .add(Groups.NOTES)
            .add(Groups.SHOULD_SYNC)
            .add(Groups.FAVORITES)
            .add(Groups.AUTO_ADD)
            .add(Groups.GROUP_IS_READ_ONLY)
            .add(Groups.SYNC1)
            .add(Groups.SYNC2)
            .add(Groups.SYNC3)
            .add(Groups.SYNC4)
            .build();

    private static final ProjectionMap sDeletedContactsProjectionMap = ProjectionMap.builder()
            .add(DeletedContacts.CONTACT_ID)
            .add(DeletedContacts.CONTACT_DELETED_TIMESTAMP)
            .build();

    /**
     * Contains {@link Groups} columns along with summary details.
     *
     * Note {@link Groups#SUMMARY_COUNT} doesn't exist in groups/view_groups.
     * When we detect this column being requested, we join {@link Joins#GROUP_MEMBER_COUNT} to
     * generate it.
     *
     * TODO Support SUMMARY_GROUP_COUNT_PER_ACCOUNT too.  See also queryLocal().
     */
    private static final ProjectionMap sGroupsSummaryProjectionMap = ProjectionMap.builder()
            .addAll(sGroupsProjectionMap)
            .add(Groups.SUMMARY_COUNT, "ifnull(group_member_count, 0)")
            .add(Groups.SUMMARY_WITH_PHONES,
                    "(SELECT COUNT(" + ContactsColumns.CONCRETE_ID + ") FROM "
                        + Tables.CONTACTS_JOIN_RAW_CONTACTS_DATA_FILTERED_BY_GROUPMEMBERSHIP
                        + " WHERE " + Contacts.HAS_PHONE_NUMBER + ")")
            .add(Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT, "0") // Always returns 0 for now.
            .build();

    /** Contains the agg_exceptions columns */
    private static final ProjectionMap sAggregationExceptionsProjectionMap = ProjectionMap.builder()
            .add(AggregationExceptionColumns._ID, Tables.AGGREGATION_EXCEPTIONS + "._id")
            .add(AggregationExceptions.TYPE)
            .add(AggregationExceptions.RAW_CONTACT_ID1)
            .add(AggregationExceptions.RAW_CONTACT_ID2)
            .build();

    /** Contains the agg_exceptions columns */
    private static final ProjectionMap sSettingsProjectionMap = ProjectionMap.builder()
            .add(Settings.ACCOUNT_NAME)
            .add(Settings.ACCOUNT_TYPE)
            .add(Settings.DATA_SET)
            .add(Settings.UNGROUPED_VISIBLE)
            .add(Settings.SHOULD_SYNC)
            .add(Settings.ANY_UNSYNCED,
                    "(CASE WHEN MIN(" + Settings.SHOULD_SYNC
                        + ",(SELECT "
                                + "(CASE WHEN MIN(" + Groups.SHOULD_SYNC + ") IS NULL"
                                + " THEN 1"
                                + " ELSE MIN(" + Groups.SHOULD_SYNC + ")"
                                + " END)"
                            + " FROM " + Views.GROUPS
                            + " WHERE " + ViewGroupsColumns.CONCRETE_ACCOUNT_NAME + "="
                                    + SettingsColumns.CONCRETE_ACCOUNT_NAME
                                + " AND " + ViewGroupsColumns.CONCRETE_ACCOUNT_TYPE + "="
                                    + SettingsColumns.CONCRETE_ACCOUNT_TYPE
                                + " AND ((" + ViewGroupsColumns.CONCRETE_DATA_SET + " IS NULL AND "
                                    + SettingsColumns.CONCRETE_DATA_SET + " IS NULL) OR ("
                                    + ViewGroupsColumns.CONCRETE_DATA_SET + "="
                                    + SettingsColumns.CONCRETE_DATA_SET + "))))=0"
                    + " THEN 1"
                    + " ELSE 0"
                    + " END)")
            .add(Settings.UNGROUPED_COUNT,
                    "(SELECT COUNT(*)"
                    + " FROM (SELECT 1"
                            + " FROM " + Tables.SETTINGS_JOIN_RAW_CONTACTS_DATA_MIMETYPES_CONTACTS
                            + " GROUP BY " + Clauses.GROUP_BY_ACCOUNT_CONTACT_ID
                            + " HAVING " + Clauses.HAVING_NO_GROUPS
                    + "))")
            .add(Settings.UNGROUPED_WITH_PHONES,
                    "(SELECT COUNT(*)"
                    + " FROM (SELECT 1"
                            + " FROM " + Tables.SETTINGS_JOIN_RAW_CONTACTS_DATA_MIMETYPES_CONTACTS
                            + " WHERE " + Contacts.HAS_PHONE_NUMBER
                            + " GROUP BY " + Clauses.GROUP_BY_ACCOUNT_CONTACT_ID
                            + " HAVING " + Clauses.HAVING_NO_GROUPS
                    + "))")
            .build();

    /** Contains StatusUpdates columns */
    private static final ProjectionMap sStatusUpdatesProjectionMap = ProjectionMap.builder()
            .add(PresenceColumns.RAW_CONTACT_ID)
            .add(StatusUpdates.DATA_ID, DataColumns.CONCRETE_ID)
            .add(StatusUpdates.IM_ACCOUNT)
            .add(StatusUpdates.IM_HANDLE)
            .add(StatusUpdates.PROTOCOL)
            // We cannot allow a null in the custom protocol field, because SQLite3 does not
            // properly enforce uniqueness of null values
            .add(StatusUpdates.CUSTOM_PROTOCOL,
                    "(CASE WHEN " + StatusUpdates.CUSTOM_PROTOCOL + "=''"
                    + " THEN NULL"
                    + " ELSE " + StatusUpdates.CUSTOM_PROTOCOL + " END)")
            .add(StatusUpdates.PRESENCE)
            .add(StatusUpdates.CHAT_CAPABILITY)
            .add(StatusUpdates.STATUS)
            .add(StatusUpdates.STATUS_TIMESTAMP)
            .add(StatusUpdates.STATUS_RES_PACKAGE)
            .add(StatusUpdates.STATUS_ICON)
            .add(StatusUpdates.STATUS_LABEL)
            .build();

    /** Contains StreamItems columns */
    private static final ProjectionMap sStreamItemsProjectionMap = ProjectionMap.builder()
            .add(StreamItems._ID)
            .add(StreamItems.CONTACT_ID)
            .add(StreamItems.CONTACT_LOOKUP_KEY)
            .add(StreamItems.ACCOUNT_NAME)
            .add(StreamItems.ACCOUNT_TYPE)
            .add(StreamItems.DATA_SET)
            .add(StreamItems.RAW_CONTACT_ID)
            .add(StreamItems.RAW_CONTACT_SOURCE_ID)
            .add(StreamItems.RES_PACKAGE)
            .add(StreamItems.RES_ICON)
            .add(StreamItems.RES_LABEL)
            .add(StreamItems.TEXT)
            .add(StreamItems.TIMESTAMP)
            .add(StreamItems.COMMENTS)
            .add(StreamItems.SYNC1)
            .add(StreamItems.SYNC2)
            .add(StreamItems.SYNC3)
            .add(StreamItems.SYNC4)
            .build();

    private static final ProjectionMap sStreamItemPhotosProjectionMap = ProjectionMap.builder()
            .add(StreamItemPhotos._ID, StreamItemPhotosColumns.CONCRETE_ID)
            .add(StreamItems.RAW_CONTACT_ID)
            .add(StreamItems.RAW_CONTACT_SOURCE_ID, RawContactsColumns.CONCRETE_SOURCE_ID)
            .add(StreamItemPhotos.STREAM_ITEM_ID)
            .add(StreamItemPhotos.SORT_INDEX)
            .add(StreamItemPhotos.PHOTO_FILE_ID)
            .add(StreamItemPhotos.PHOTO_URI,
                    "'" + DisplayPhoto.CONTENT_URI + "'||'/'||" + StreamItemPhotos.PHOTO_FILE_ID)
            .add(PhotoFiles.HEIGHT)
            .add(PhotoFiles.WIDTH)
            .add(PhotoFiles.FILESIZE)
            .add(StreamItemPhotos.SYNC1)
            .add(StreamItemPhotos.SYNC2)
            .add(StreamItemPhotos.SYNC3)
            .add(StreamItemPhotos.SYNC4)
            .build();

    /** Contains {@link Directory} columns */
    private static final ProjectionMap sDirectoryProjectionMap = ProjectionMap.builder()
            .add(Directory._ID)
            .add(Directory.PACKAGE_NAME)
            .add(Directory.TYPE_RESOURCE_ID)
            .add(Directory.DISPLAY_NAME)
            .add(Directory.DIRECTORY_AUTHORITY)
            .add(Directory.ACCOUNT_TYPE)
            .add(Directory.ACCOUNT_NAME)
            .add(Directory.EXPORT_SUPPORT)
            .add(Directory.SHORTCUT_SUPPORT)
            .add(Directory.PHOTO_SUPPORT)
            .build();

    /**
     * SPRD: Add by SPRD for cleaning up contacts Bug474742
     *
     * @{
     */
    private static final ProjectionMap sDeduplicatioProjectMap = ProjectionMap.builder()
            .add(Data.RAW_CONTACT_ID, "DISTINCT(" + Data.RAW_CONTACT_ID +")")
            .add(Data.DATA1,
                    "CASE WHEN " + Data.HAS_PHONE_NUMBER + " =1 THEN " + Data.DATA1
                            + " ELSE ' ' END")
            .add(Data.PHOTO_ID)
            .add("account_id")
            .add(Data.DISPLAY_NAME_PRIMARY)
            .add(RawContacts.ACCOUNT_NAME)
            .add(RawContacts.ACCOUNT_TYPE)
            .build();
    /**
     * @}
     */

    // where clause to update the status_updates table
    private static final String WHERE_CLAUSE_FOR_STATUS_UPDATES_TABLE =
            StatusUpdatesColumns.DATA_ID + " IN (SELECT Distinct " + StatusUpdates.DATA_ID +
            " FROM " + Tables.STATUS_UPDATES + " LEFT OUTER JOIN " + Tables.PRESENCE +
            " ON " + StatusUpdatesColumns.DATA_ID + " = " + StatusUpdates.DATA_ID + " WHERE ";

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String DEFAULT_SNIPPET_ARG_START_MATCH = "[";
    private static final String DEFAULT_SNIPPET_ARG_END_MATCH = "]";
    private static final String DEFAULT_SNIPPET_ARG_ELLIPSIS = "\u2026";
    private static final int DEFAULT_SNIPPET_ARG_MAX_TOKENS = 5;

    private final StringBuilder mSb = new StringBuilder();
    private final String[] mSelectionArgs1 = new String[1];
    private final String[] mSelectionArgs2 = new String[2];
    private final String[] mSelectionArgs3 = new String[3];
    private final String[] mSelectionArgs4 = new String[4];
    private final ArrayList<String> mSelectionArgs = Lists.newArrayList();

    static {
        // Contacts URI matching table
        final UriMatcher matcher = sUriMatcher;

        // DO NOT use constants such as Contacts.CONTENT_URI here.  This is the only place
        // where one can see all supported URLs at a glance, and using constants will reduce
        // readability.

        /**
         * SPRD: bug441929, for custom display mode query
         *
         * @{
         */
        matcher.addURI(ContactsContract.AUTHORITY, "contactscustom", CONTACTS_CUSTOM);
        /**
         * @}
         */
        matcher.addURI(ContactsContract.AUTHORITY, "contacts", CONTACTS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/data", CONTACTS_ID_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/entities", CONTACTS_ID_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/suggestions",
                AGGREGATION_SUGGESTIONS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/suggestions/*",
                AGGREGATION_SUGGESTIONS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/photo", CONTACTS_ID_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/display_photo",
                CONTACTS_ID_DISPLAY_PHOTO);

        // Special URIs that refer to contact pictures in the corp CP2.
        matcher.addURI(ContactsContract.AUTHORITY, "contacts_corp/#/photo", CONTACTS_ID_PHOTO_CORP);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts_corp/#/display_photo",
                CONTACTS_ID_DISPLAY_PHOTO_CORP);

        matcher.addURI(ContactsContract.AUTHORITY, "contacts/#/stream_items",
                CONTACTS_ID_STREAM_ITEMS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/filter", CONTACTS_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/filter/*", CONTACTS_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*", CONTACTS_LOOKUP);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/data", CONTACTS_LOOKUP_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/photo",
                CONTACTS_LOOKUP_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#", CONTACTS_LOOKUP_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#/data",
                CONTACTS_LOOKUP_ID_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#/photo",
                CONTACTS_LOOKUP_ID_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/display_photo",
                CONTACTS_LOOKUP_DISPLAY_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#/display_photo",
                CONTACTS_LOOKUP_ID_DISPLAY_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/entities",
                CONTACTS_LOOKUP_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#/entities",
                CONTACTS_LOOKUP_ID_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/stream_items",
                CONTACTS_LOOKUP_STREAM_ITEMS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/lookup/*/#/stream_items",
                CONTACTS_LOOKUP_ID_STREAM_ITEMS);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/as_vcard/*", CONTACTS_AS_VCARD);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/as_multi_vcard/*",
                CONTACTS_AS_MULTI_VCARD);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/strequent/", CONTACTS_STREQUENT);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/strequent/filter/*",
                CONTACTS_STREQUENT_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/group/*", CONTACTS_GROUP);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/frequent", CONTACTS_FREQUENT);
        matcher.addURI(ContactsContract.AUTHORITY, "contacts/delete_usage", CONTACTS_DELETE_USAGE);
        /**
         * SPRD:Bug 474798 SprdAppBackup for Contacts.
         * @{
         */
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/unique", RAW_CONTACTS_UNIQUE);
        /**
         * @}
         */
        /**
         * SPRD: Add by SPRD for cleaning up contacts Bug474742
         * @{
         */
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/unique_nosim", RAW_CONTACTS_UNIQUE_NOSIM);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/merge",
                RAW_CONTACTS_MERGE);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/join",
                RAW_CONTACTS_JOIN);
        /**
         * @}
         */
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts", RAW_CONTACTS);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#", RAW_CONTACTS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#/data", RAW_CONTACTS_ID_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#/display_photo",
                RAW_CONTACTS_ID_DISPLAY_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#/entity", RAW_CONTACT_ID_ENTITY);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#/stream_items",
                RAW_CONTACTS_ID_STREAM_ITEMS);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contacts/#/stream_items/#",
                RAW_CONTACTS_ID_STREAM_ITEMS_ID);

        matcher.addURI(ContactsContract.AUTHORITY, "raw_contact_entities", RAW_CONTACT_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "raw_contact_entities_corp", RAW_CONTACT_ENTITIES_CORP);

        matcher.addURI(ContactsContract.AUTHORITY, "data", DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "data/#", DATA_ID);
        /**
         * SPRD: Bug 474752 Add features with multiSelection activity in Contacts.
         *
         * @{
         */
        matcher.addURI(ContactsContract.AUTHORITY, "data/filter/", DATA_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/filter/*", DATA_FILTER);
        /**
         * @}
         */

        /**
         * SPRD: Add by SPRD for cleaning up contacts Bug474742
         *
         * @{
         */
        matcher.addURI(ContactsContract.AUTHORITY, "data/deduplication_candidates",
                DATA_DEDUPLICATE_CANDIDATES);
        /**
         * @}
         */
        matcher.addURI(ContactsContract.AUTHORITY, "data/phones", PHONES);
        matcher.addURI(ContactsContract.AUTHORITY, "data_enterprise/phones", PHONES_ENTERPRISE);
        matcher.addURI(ContactsContract.AUTHORITY, "data/phones/#", PHONES_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "data/phones/filter", PHONES_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/phones/filter/*", PHONES_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails", EMAILS);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/#", EMAILS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/lookup", EMAILS_LOOKUP);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/lookup/*", EMAILS_LOOKUP);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/filter", EMAILS_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/filter/*", EMAILS_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/lookup_enterprise",
                EMAILS_LOOKUP_ENTERPRISE);
        matcher.addURI(ContactsContract.AUTHORITY, "data/emails/lookup_enterprise/*",
                EMAILS_LOOKUP_ENTERPRISE);
        matcher.addURI(ContactsContract.AUTHORITY, "data/postals", POSTALS);
        matcher.addURI(ContactsContract.AUTHORITY, "data/postals/#", POSTALS_ID);
        /** "*" is in CSV form with data IDs ("123,456,789") */
        matcher.addURI(ContactsContract.AUTHORITY, "data/usagefeedback/*", DATA_USAGE_FEEDBACK_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "data/callables/", CALLABLES);
        matcher.addURI(ContactsContract.AUTHORITY, "data/callables/#", CALLABLES_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "data/callables/filter", CALLABLES_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/callables/filter/*", CALLABLES_FILTER);

        matcher.addURI(ContactsContract.AUTHORITY, "data/contactables/", CONTACTABLES);
        matcher.addURI(ContactsContract.AUTHORITY, "data/contactables/filter", CONTACTABLES_FILTER);
        matcher.addURI(ContactsContract.AUTHORITY, "data/contactables/filter/*",
                CONTACTABLES_FILTER);

        matcher.addURI(ContactsContract.AUTHORITY, "groups", GROUPS);
        matcher.addURI(ContactsContract.AUTHORITY, "groups/#", GROUPS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "groups_summary", GROUPS_SUMMARY);

        matcher.addURI(ContactsContract.AUTHORITY, SyncStateContentProviderHelper.PATH, SYNCSTATE);
        matcher.addURI(ContactsContract.AUTHORITY, SyncStateContentProviderHelper.PATH + "/#",
                SYNCSTATE_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/" + SyncStateContentProviderHelper.PATH,
                PROFILE_SYNCSTATE);
        matcher.addURI(ContactsContract.AUTHORITY,
                "profile/" + SyncStateContentProviderHelper.PATH + "/#",
                PROFILE_SYNCSTATE_ID);

        matcher.addURI(ContactsContract.AUTHORITY, "phone_lookup/*", PHONE_LOOKUP);
        matcher.addURI(ContactsContract.AUTHORITY, "phone_lookup_enterprise/*",
                PHONE_LOOKUP_ENTERPRISE);
        matcher.addURI(ContactsContract.AUTHORITY, "aggregation_exceptions",
                AGGREGATION_EXCEPTIONS);
        matcher.addURI(ContactsContract.AUTHORITY, "aggregation_exceptions/*",
                AGGREGATION_EXCEPTION_ID);

        matcher.addURI(ContactsContract.AUTHORITY, "settings", SETTINGS);

        matcher.addURI(ContactsContract.AUTHORITY, "status_updates", STATUS_UPDATES);
        matcher.addURI(ContactsContract.AUTHORITY, "status_updates/#", STATUS_UPDATES_ID);

        matcher.addURI(ContactsContract.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY,
                SEARCH_SUGGESTIONS);
        matcher.addURI(ContactsContract.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*",
                SEARCH_SUGGESTIONS);
        matcher.addURI(ContactsContract.AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*",
                SEARCH_SHORTCUT);

        matcher.addURI(ContactsContract.AUTHORITY, "provider_status", PROVIDER_STATUS);

        matcher.addURI(ContactsContract.AUTHORITY, "directories", DIRECTORIES);
        matcher.addURI(ContactsContract.AUTHORITY, "directories/#", DIRECTORIES_ID);

        matcher.addURI(ContactsContract.AUTHORITY, "complete_name", COMPLETE_NAME);

        matcher.addURI(ContactsContract.AUTHORITY, "profile", PROFILE);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/entities", PROFILE_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/data", PROFILE_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/data/#", PROFILE_DATA_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/photo", PROFILE_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/display_photo", PROFILE_DISPLAY_PHOTO);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/as_vcard", PROFILE_AS_VCARD);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/raw_contacts", PROFILE_RAW_CONTACTS);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/raw_contacts/#",
                PROFILE_RAW_CONTACTS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/raw_contacts/#/data",
                PROFILE_RAW_CONTACTS_ID_DATA);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/raw_contacts/#/entity",
                PROFILE_RAW_CONTACTS_ID_ENTITIES);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/status_updates",
                PROFILE_STATUS_UPDATES);
        matcher.addURI(ContactsContract.AUTHORITY, "profile/raw_contact_entities",
                PROFILE_RAW_CONTACT_ENTITIES);

        matcher.addURI(ContactsContract.AUTHORITY, "stream_items", STREAM_ITEMS);
        matcher.addURI(ContactsContract.AUTHORITY, "stream_items/photo", STREAM_ITEMS_PHOTOS);
        matcher.addURI(ContactsContract.AUTHORITY, "stream_items/#", STREAM_ITEMS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "stream_items/#/photo", STREAM_ITEMS_ID_PHOTOS);
        matcher.addURI(ContactsContract.AUTHORITY, "stream_items/#/photo/#",
                STREAM_ITEMS_ID_PHOTOS_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "stream_items_limit", STREAM_ITEMS_LIMIT);

        matcher.addURI(ContactsContract.AUTHORITY, "display_photo/#", DISPLAY_PHOTO_ID);
        matcher.addURI(ContactsContract.AUTHORITY, "photo_dimensions", PHOTO_DIMENSIONS);

        matcher.addURI(ContactsContract.AUTHORITY, "deleted_contacts", DELETED_CONTACTS);
        matcher.addURI(ContactsContract.AUTHORITY, "deleted_contacts/#", DELETED_CONTACTS_ID);
    }

    private static class DirectoryInfo {
        String authority;
        String accountName;
        String accountType;
    }

    /**
     * An entry in group id cache.
     *
     * TODO: Move this and {@link #mGroupIdCache} to {@link DataRowHandlerForGroupMembership}.
     */
    public static class GroupIdCacheEntry {
        long accountId;
        String sourceId;
        long groupId;
    }

    /**
     * The thread-local holder of the active transaction.  Shared between this and the profile
     * provider, to keep transactions on both databases synchronized.
     */
    private final ThreadLocal<ContactsTransaction> mTransactionHolder =
            new ThreadLocal<ContactsTransaction>();

    // This variable keeps track of whether the current operation is intended for the profile DB.
    private final ThreadLocal<Boolean> mInProfileMode = new ThreadLocal<Boolean>();

    // Depending on whether the action being performed is for the profile, we will use one of two
    // database helper instances.
    private final ThreadLocal<ContactsDatabaseHelper> mDbHelper =
            new ThreadLocal<ContactsDatabaseHelper>();

    // Depending on whether the action being performed is for the profile or not, we will use one of
    // two aggregator instances.
    private final ThreadLocal<AbstractContactAggregator> mAggregator =
            new ThreadLocal<AbstractContactAggregator>();

    // Depending on whether the action being performed is for the profile or not, we will use one of
    // two photo store instances (with their files stored in separate sub-directories).
    private final ThreadLocal<PhotoStore> mPhotoStore = new ThreadLocal<PhotoStore>();

    // The active transaction context will switch depending on the operation being performed.
    // Both transaction contexts will be cleared out when a batch transaction is started, and
    // each will be processed separately when a batch transaction completes.
    private final TransactionContext mContactTransactionContext = new TransactionContext(false);
    private final TransactionContext mProfileTransactionContext = new TransactionContext(true);
    private final ThreadLocal<TransactionContext> mTransactionContext =
            new ThreadLocal<TransactionContext>();

    // Random number generator.
    private final SecureRandom mRandom = new SecureRandom();

    private final HashMap<String, Boolean> mAccountWritability = Maps.newHashMap();

    private PhotoStore mContactsPhotoStore;
    private PhotoStore mProfilePhotoStore;

    private ContactsDatabaseHelper mContactsHelper;
    private ProfileDatabaseHelper mProfileHelper;

    // Separate data row handler instances for contact data and profile data.
    private HashMap<String, DataRowHandler> mDataRowHandlers;
    private HashMap<String, DataRowHandler> mProfileDataRowHandlers;

    /**
     * Cached information about contact directories.
     */
    private HashMap<String, DirectoryInfo> mDirectoryCache = new HashMap<String, DirectoryInfo>();
    private boolean mDirectoryCacheValid = false;

    /**
     * Map from group source IDs to lists of {@link GroupIdCacheEntry}s.
     *
     * We don't need a soft cache for groups - the assumption is that there will only
     * be a small number of contact groups. The cache is keyed off source ID.  The value
     * is a list of groups with this group ID.
     */
    private HashMap<String, ArrayList<GroupIdCacheEntry>> mGroupIdCache = Maps.newHashMap();

    /**
     * Sub-provider for handling profile requests against the profile database.
     */
    private ProfileProvider mProfileProvider;

    private NameSplitter mNameSplitter;
    private NameLookupBuilder mNameLookupBuilder;

    private PostalSplitter mPostalSplitter;

    private ContactDirectoryManager mContactDirectoryManager;

    private boolean mIsPhoneInitialized;
    private boolean mIsPhone;

    private Account mAccount;

    private AbstractContactAggregator mContactAggregator;
    private AbstractContactAggregator mProfileAggregator;

    // Duration in milliseconds that pre-authorized URIs will remain valid.
    private long mPreAuthorizedUriDuration;

    private LegacyApiSupport mLegacyApiSupport;
    private GlobalSearchSupport mGlobalSearchSupport;
    private CommonNicknameCache mCommonNicknameCache;
    private SearchIndexManager mSearchIndexManager;

    private int mProviderStatus = STATUS_NORMAL;
    private boolean mProviderStatusUpdateNeeded;
    private volatile CountDownLatch mReadAccessLatch;
    private volatile CountDownLatch mWriteAccessLatch;
    private boolean mAccountUpdateListenerRegistered;
    private boolean mOkToOpenAccess = true;

    private boolean mVisibleTouched = false;

    private boolean mSyncToNetwork;

    private LocaleSet mCurrentLocales;
    private int mContactsAccountCount;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private long mLastPhotoCleanup = 0;

    private FastScrollingIndexCache mFastScrollingIndexCache;

    // Stats about FastScrollingIndex.
    private int mFastScrollingIndexCacheRequestCount;
    private int mFastScrollingIndexCacheMissCount;
    private long mTotalTimeFastScrollingIndexGenerate;

    @Override
    public boolean onCreate() {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "ContactsProvider2.onCreate start");
        }
        super.onCreate();
        setAppOps(AppOpsManager.OP_READ_CONTACTS, AppOpsManager.OP_WRITE_CONTACTS);
        try {
            return initialize();
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot start provider", e);
            // In production code we don't want to throw here, so that phone will still work
            // in low storage situations.
            // See I5c88a3024ff1c5a06b5756b29a2d903f8f6a2531
            if (shouldThrowExceptionForInitializationError()) {
                throw e;
            }
            return false;
        } finally {
            if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
                Log.d(Constants.PERFORMANCE_TAG, "ContactsProvider2.onCreate finish");
            }
        }
    }

    protected boolean shouldThrowExceptionForInitializationError() {
        return false;
    }

    private boolean initialize() {
        StrictMode.setThreadPolicy(
                new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());

        mFastScrollingIndexCache = FastScrollingIndexCache.getInstance(getContext());

        mContactsHelper = getDatabaseHelper(getContext());
        mDbHelper.set(mContactsHelper);

        // Set up the DB helper for keeping transactions serialized.
        setDbHelperToSerializeOn(mContactsHelper, CONTACTS_DB_TAG, this);

        mContactDirectoryManager = new ContactDirectoryManager(this);
        mGlobalSearchSupport = new GlobalSearchSupport(this);

        // The provider is closed for business until fully initialized
        mReadAccessLatch = new CountDownLatch(1);
        mWriteAccessLatch = new CountDownLatch(1);

        mBackgroundThread = new HandlerThread("ContactsProviderWorker",
                Process.THREAD_PRIORITY_BACKGROUND);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                performBackgroundTask(msg.what, msg.obj);
            }
        };

        // Set up the sub-provider for handling profiles.
        mProfileProvider = newProfileProvider();
        mProfileProvider.setDbHelperToSerializeOn(mContactsHelper, CONTACTS_DB_TAG, this);
        ProviderInfo profileInfo = new ProviderInfo();
        profileInfo.authority = ContactsContract.AUTHORITY;
        mProfileProvider.attachInfo(getContext(), profileInfo);
        mProfileHelper = mProfileProvider.getDatabaseHelper(getContext());

        // Initialize the pre-authorized URI duration.
        mPreAuthorizedUriDuration = DEFAULT_PREAUTHORIZED_URI_EXPIRATION;

        scheduleBackgroundTask(BACKGROUND_TASK_INITIALIZE);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_ACCOUNTS);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_LOCALE);
        scheduleBackgroundTask(BACKGROUND_TASK_UPGRADE_AGGREGATION_ALGORITHM);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_SEARCH_INDEX);
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_PROVIDER_STATUS);
        scheduleBackgroundTask(BACKGROUND_TASK_OPEN_WRITE_ACCESS);
        scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
        scheduleBackgroundTask(BACKGROUND_TASK_CLEAN_DELETE_LOG);

        /**
         * SPRD: Delete marked contacts when boot
         *
         * @{
         */
        scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_DELETED_LOCAL_CONTACTS);
        /**
         * @}
         */

        /**
         * SPRD: add fot update sim contacts, bug421244
         * SPRD: Modify code for BUG:538861:
         *       Start xxWatchDag service delay, so the service will not block sending
         *       Broadcasts.
         *       The effect is that when acore process stop or be killed innormally,
         *       this service will be started delay.
         *       This is for low memory version: [ActivityManager.isLowRamDeviceStatic()]
         * @{
         */
        if (ActivityManager.isLowRamDeviceStatic()) {
            Handler mhandler = new Handler();
            mhandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    getContext().startService(new Intent(getContext(),
                                    SyncSimAccountsWatchdog.class));
                }
            }, 5000);
            Log.d(TAG, "Initialize watchdog service for low memory!");
        } else {
            getContext().startService(new Intent(getContext(), SyncSimAccountsWatchdog.class));
            Log.d(TAG, "Initialize watchdog service normal version");
        }
        /**
         * @}
         */
        return true;
    }

    @VisibleForTesting
    public void setNewAggregatorForTest(boolean enabled) {
        mContactAggregator = (enabled)
                ? new ContactAggregator2(this, mContactsHelper,
                createPhotoPriorityResolver(getContext()), mNameSplitter, mCommonNicknameCache)
                : new ContactAggregator(this, mContactsHelper,
                createPhotoPriorityResolver(getContext()), mNameSplitter, mCommonNicknameCache);
        mContactAggregator.setEnabled(SystemProperties.getBoolean(AGGREGATE_CONTACTS, true));
        initDataRowHandlers(mDataRowHandlers, mContactsHelper, mContactAggregator,
                mContactsPhotoStore);
    }

    // Updates the locale set to reflect a new system locale.
    private static LocaleSet updateLocaleSet(LocaleSet oldLocales, Locale newLocale) {
        final Locale prevLocale = oldLocales.getPrimaryLocale();
        // If primary locale is unchanged then no change to locale set.
        if (newLocale.equals(prevLocale)) {
            return oldLocales;
        }
        // Otherwise, construct a new locale set based on the new locale
        // and the previous primary locale.
        return new LocaleSet(newLocale, prevLocale).normalize();
    }

    private static LocaleSet getProviderPrefLocales(SharedPreferences prefs) {
        final String providerLocaleString = prefs.getString(PREF_LOCALE, null);
        return LocaleSet.getLocaleSet(providerLocaleString);
    }

    // Called by initForDefaultLocale. Returns an updated locale set using the
    // current system locale.
    private LocaleSet getLocaleSet() {
        final Locale curLocale = getLocale();
        if (mCurrentLocales != null) {
            return updateLocaleSet(mCurrentLocales, curLocale);
        }
        // On startup need to reload the locale set from prefs for update.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return updateLocaleSet(getProviderPrefLocales(prefs), curLocale);
    }

    // Static routine called on startup by updateLocaleOffline.
    private static LocaleSet getLocaleSet(SharedPreferences prefs, Locale curLocale) {
        return updateLocaleSet(getProviderPrefLocales(prefs), curLocale);
    }

    /**
     * (Re)allocates all locale-sensitive structures.
     */
    private void initForDefaultLocale() {
        Context context = getContext();
        mLegacyApiSupport =
                new LegacyApiSupport(context, mContactsHelper, this, mGlobalSearchSupport);
        mCurrentLocales = getLocaleSet();
        mNameSplitter = mContactsHelper.createNameSplitter(mCurrentLocales.getPrimaryLocale());
        mNameLookupBuilder = new StructuredNameLookupBuilder(mNameSplitter);
        mPostalSplitter = new PostalSplitter(mCurrentLocales.getPrimaryLocale());
        mCommonNicknameCache = new CommonNicknameCache(mContactsHelper.getReadableDatabase());
        ContactLocaleUtils.setLocales(mCurrentLocales);

        int value = android.provider.Settings.Global.getInt(context.getContentResolver(),
                    Global.NEW_CONTACT_AGGREGATOR, 1);

        // Turn on aggregation algorithm updating process if new aggregator is enabled.
        PROPERTY_AGGREGATION_ALGORITHM_VERSION = (value == 0)
                ? AGGREGATION_ALGORITHM_OLD_VERSION
                : AGGREGATION_ALGORITHM_NEW_VERSION;
        mContactAggregator = (value == 0)
                ? new ContactAggregator(this, mContactsHelper,
                        createPhotoPriorityResolver(context), mNameSplitter, mCommonNicknameCache)
                : new ContactAggregator2(this, mContactsHelper,
                        createPhotoPriorityResolver(context), mNameSplitter, mCommonNicknameCache);

        mContactAggregator.setEnabled(SystemProperties.getBoolean(AGGREGATE_CONTACTS, true));
        mProfileAggregator = new ProfileAggregator(this, mProfileHelper,
                createPhotoPriorityResolver(context), mNameSplitter, mCommonNicknameCache);
        mProfileAggregator.setEnabled(SystemProperties.getBoolean(AGGREGATE_CONTACTS, true));
        mSearchIndexManager = new SearchIndexManager(this);
        mContactsPhotoStore = new PhotoStore(getContext().getFilesDir(), mContactsHelper);
        mProfilePhotoStore =
                new PhotoStore(new File(getContext().getFilesDir(), "profile"), mProfileHelper);

        mDataRowHandlers = new HashMap<String, DataRowHandler>();
        initDataRowHandlers(mDataRowHandlers, mContactsHelper, mContactAggregator,
                mContactsPhotoStore);
        mProfileDataRowHandlers = new HashMap<String, DataRowHandler>();
        initDataRowHandlers(mProfileDataRowHandlers, mProfileHelper, mProfileAggregator,
                mProfilePhotoStore);

        // Set initial thread-local state variables for the Contacts DB.
        switchToContactMode();
    }

    private void initDataRowHandlers(Map<String, DataRowHandler> handlerMap,
            ContactsDatabaseHelper dbHelper, AbstractContactAggregator contactAggregator,
            PhotoStore photoStore) {
        Context context = getContext();
        handlerMap.put(Email.CONTENT_ITEM_TYPE,
                new DataRowHandlerForEmail(context, dbHelper, contactAggregator));
        handlerMap.put(Im.CONTENT_ITEM_TYPE,
                new DataRowHandlerForIm(context, dbHelper, contactAggregator));
        handlerMap.put(Organization.CONTENT_ITEM_TYPE,
                new DataRowHandlerForOrganization(context, dbHelper, contactAggregator));
        handlerMap.put(Phone.CONTENT_ITEM_TYPE,
                new DataRowHandlerForPhoneNumber(context, dbHelper, contactAggregator));
        handlerMap.put(Nickname.CONTENT_ITEM_TYPE,
                new DataRowHandlerForNickname(context, dbHelper, contactAggregator));
        handlerMap.put(StructuredName.CONTENT_ITEM_TYPE,
                new DataRowHandlerForStructuredName(context, dbHelper, contactAggregator,
                        mNameSplitter, mNameLookupBuilder));
        handlerMap.put(StructuredPostal.CONTENT_ITEM_TYPE,
                new DataRowHandlerForStructuredPostal(context, dbHelper, contactAggregator,
                        mPostalSplitter));
        handlerMap.put(GroupMembership.CONTENT_ITEM_TYPE,
                new DataRowHandlerForGroupMembership(context, dbHelper, contactAggregator,
                        mGroupIdCache));
        handlerMap.put(Photo.CONTENT_ITEM_TYPE,
                new DataRowHandlerForPhoto(context, dbHelper, contactAggregator, photoStore,
                        getMaxDisplayPhotoDim(), getMaxThumbnailDim()));
        handlerMap.put(Note.CONTENT_ITEM_TYPE,
                new DataRowHandlerForNote(context, dbHelper, contactAggregator));
        handlerMap.put(Identity.CONTENT_ITEM_TYPE,
                new DataRowHandlerForIdentity(context, dbHelper, contactAggregator));
        /**
         * SPRD: bug429368, for support website
         *
         * @{
         */
        handlerMap.put(Website.CONTENT_ITEM_TYPE,
                new DataRowHandlerForWebsite(context, dbHelper, contactAggregator));
        /**
         * @}
         */
    }

    @VisibleForTesting
    PhotoPriorityResolver createPhotoPriorityResolver(Context context) {
        return new PhotoPriorityResolver(context);
    }

    protected void scheduleBackgroundTask(int task) {
        mBackgroundHandler.sendEmptyMessage(task);
    }

    protected void scheduleBackgroundTask(int task, Object arg) {
        mBackgroundHandler.sendMessage(mBackgroundHandler.obtainMessage(task, arg));
    }

    protected void performBackgroundTask(int task, Object arg) {
        // Make sure we operate on the contacts db by default.
        switchToContactMode();
        switch (task) {
            case BACKGROUND_TASK_INITIALIZE: {
                initForDefaultLocale();
                mReadAccessLatch.countDown();
                mReadAccessLatch = null;
                break;
            }

            case BACKGROUND_TASK_OPEN_WRITE_ACCESS: {
                if (mOkToOpenAccess) {
                    mWriteAccessLatch.countDown();
                    mWriteAccessLatch = null;
                }
                break;
            }

            case BACKGROUND_TASK_UPDATE_ACCOUNTS: {
                Context context = getContext();
                if (!mAccountUpdateListenerRegistered) {
                    AccountManager.get(context).addOnAccountsUpdatedListener(this, null, false);
                    mAccountUpdateListenerRegistered = true;
                }

                // Update the accounts for both the contacts and profile DBs.
                Account[] accounts = AccountManager.get(context).getAccounts();
                switchToContactMode();
                boolean accountsChanged = updateAccountsInBackground(accounts);
                switchToProfileMode();
                accountsChanged |= updateAccountsInBackground(accounts);

                switchToContactMode();

                updateContactsAccountCount(accounts);
                updateDirectoriesInBackground(accountsChanged);
                /**
                 * SPRD: To update ContactAccountManager before sync sim, bug421244
                 *
                 * @{
                 */
                ContactAccountManager.getInstance(getContext()).onAccountUpdate();
                /**
                 * @}
                 */
                break;
            }

            case BACKGROUND_TASK_UPDATE_LOCALE: {
                updateLocaleInBackground();
                break;
            }

            case BACKGROUND_TASK_CHANGE_LOCALE: {
                changeLocaleInBackground();
                break;
            }

            case BACKGROUND_TASK_UPGRADE_AGGREGATION_ALGORITHM: {
                if (isAggregationUpgradeNeeded()) {
                    upgradeAggregationAlgorithmInBackground();
                    invalidateFastScrollingIndexCache();
                }
                break;
            }

            case BACKGROUND_TASK_UPDATE_SEARCH_INDEX: {
                updateSearchIndexInBackground();
                break;
            }

            case BACKGROUND_TASK_UPDATE_PROVIDER_STATUS: {
                updateProviderStatus();
                break;
            }

            case BACKGROUND_TASK_UPDATE_DIRECTORIES: {
                if (arg != null) {
                    mContactDirectoryManager.onPackageChanged((String) arg);
                }
                break;
            }

            case BACKGROUND_TASK_CLEANUP_PHOTOS: {
                // Check rate limit.
                long now = System.currentTimeMillis();
                if (now - mLastPhotoCleanup > PHOTO_CLEANUP_RATE_LIMIT) {
                    mLastPhotoCleanup = now;

                    // Clean up photo stores for both contacts and profiles.
                    switchToContactMode();
                    cleanupPhotoStore();
                    switchToProfileMode();
                    cleanupPhotoStore();

                    switchToContactMode(); // Switch to the default, just in case.
                }
                break;
            }

            /**
             * SPRD: Delete marked contacts when boot
             *
             * @{
             */
            case BACKGROUND_TASK_CLEANUP_DELETED_LOCAL_CONTACTS : {
                switchToContactMode();
                cleanupDeletedLocalContacts();
                break;
            }
            /**
             * @}
             */

            case BACKGROUND_TASK_CLEAN_DELETE_LOG: {
                final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
                DeletedContactsTableUtil.deleteOldLogs(db);
                break;
            }
        }
    }

    public void onLocaleChanged() {
        /**
        * SPRD:Bug451327 Labels are wrong after reboot phone and changing locale with SIM contacts.
        * Original Android code:
        if (mProviderStatus != STATUS_NORMAL
                && mProviderStatus != STATUS_NO_ACCOUNTS_NO_CONTACTS) {
            return;
        }
        * @{
        */
        if (mProviderStatus != STATUS_NORMAL
                && mProviderStatus != STATUS_NO_ACCOUNTS_NO_CONTACTS && mProviderStatus != ProviderStatus.STATUS_IMPORTING) {
            return;
        }
        /**
        * @}
        */
        scheduleBackgroundTask(BACKGROUND_TASK_CHANGE_LOCALE);
    }

    private static boolean needsToUpdateLocaleData(SharedPreferences prefs,
            LocaleSet locales, ContactsDatabaseHelper contactsHelper,
            ProfileDatabaseHelper profileHelper) {
        final String providerLocales = prefs.getString(PREF_LOCALE, null);

        // If locale matches that of the provider, and neither DB needs
        // updating, there's nothing to do. A DB might require updating
        // as a result of a system upgrade.
        if (!locales.toString().equals(providerLocales)) {
            Log.i(TAG, "Locale has changed from " + providerLocales
                    + " to " + locales);
            return true;
        }
        if (contactsHelper.needsToUpdateLocaleData(locales) ||
                profileHelper.needsToUpdateLocaleData(locales)) {
            return true;
        }
        return false;
    }

    /**
     * Verifies that the contacts database is properly configured for the current locale.
     * If not, changes the database locale to the current locale using an asynchronous task.
     * This needs to be done asynchronously because the process involves rebuilding
     * large data structures (name lookup, sort keys), which can take minutes on
     * a large set of contacts.
     */
    protected void updateLocaleInBackground() {

        // The process is already running - postpone the change
        if (mProviderStatus == STATUS_CHANGING_LOCALE) {
            return;
        }

        final LocaleSet currentLocales = mCurrentLocales;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        if (!needsToUpdateLocaleData(prefs, currentLocales, mContactsHelper, mProfileHelper)) {
            return;
        }

        int providerStatus = mProviderStatus;
        setProviderStatus(STATUS_CHANGING_LOCALE);
        mContactsHelper.setLocale(currentLocales);
        mProfileHelper.setLocale(currentLocales);
        mSearchIndexManager.updateIndex(true);
        prefs.edit().putString(PREF_LOCALE, currentLocales.toString()).commit();
        setProviderStatus(providerStatus);
    }

    // Static update routine for use by ContactsUpgradeReceiver during startup.
    // This clears the search index and marks it to be rebuilt, but doesn't
    // actually rebuild it. That is done later by
    // BACKGROUND_TASK_UPDATE_SEARCH_INDEX.
    protected static void updateLocaleOffline(
            Context context,
            ContactsDatabaseHelper contactsHelper,
            ProfileDatabaseHelper profileHelper) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final LocaleSet currentLocales = getLocaleSet(prefs, Locale.getDefault());
        if (!needsToUpdateLocaleData(prefs, currentLocales, contactsHelper, profileHelper)) {
            return;
        }

        contactsHelper.setLocale(currentLocales);
        profileHelper.setLocale(currentLocales);
        contactsHelper.rebuildSearchIndex();
        prefs.edit().putString(PREF_LOCALE, currentLocales.toString()).commit();
    }

    /**
     * Reinitializes the provider for a new locale.
     */
    private void changeLocaleInBackground() {
        // Re-initializing the provider without stopping it.
        // Locking the database will prevent inserts/updates/deletes from
        // running at the same time, but queries may still be running
        // on other threads. Those queries may return inconsistent results.
        SQLiteDatabase db = mContactsHelper.getWritableDatabase();
        SQLiteDatabase profileDb = mProfileHelper.getWritableDatabase();
        db.beginTransaction();
        profileDb.beginTransaction();
        try {
            initForDefaultLocale();
            db.setTransactionSuccessful();
            profileDb.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            profileDb.endTransaction();
        }

        updateLocaleInBackground();
    }

    protected void updateSearchIndexInBackground() {
        mSearchIndexManager.updateIndex(false);
    }

    protected void updateDirectoriesInBackground(boolean rescan) {
        mContactDirectoryManager.scanAllPackages(rescan);
    }

    private void updateProviderStatus() {
        if (mProviderStatus != STATUS_NORMAL
                && mProviderStatus != STATUS_NO_ACCOUNTS_NO_CONTACTS) {
            return;
        }

        // No accounts/no contacts status is true if there are no account and
        // there are no contacts or one profile contact
        if (mContactsAccountCount == 0) {
            boolean isContactsEmpty = DatabaseUtils.queryIsEmpty(mContactsHelper.getReadableDatabase(), Tables.CONTACTS);
            long profileNum = DatabaseUtils.queryNumEntries(mProfileHelper.getReadableDatabase(),
                    Tables.CONTACTS, null);

            // TODO: Different status if there is a profile but no contacts?
            if (isContactsEmpty && profileNum <= 1) {
                setProviderStatus(STATUS_NO_ACCOUNTS_NO_CONTACTS);
            } else {
                setProviderStatus(STATUS_NORMAL);
            }
        } else {
            setProviderStatus(STATUS_NORMAL);
        }
    }

    @VisibleForTesting
    protected void cleanupPhotoStore() {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // Assemble the set of photo store file IDs that are in use, and send those to the photo
        // store.  Any photos that aren't in that set will be deleted, and any photos that no
        // longer exist in the photo store will be returned for us to clear out in the DB.
        long photoMimeTypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        Cursor c = db.query(Views.DATA, new String[] {Data._ID, Photo.PHOTO_FILE_ID},
                DataColumns.MIMETYPE_ID + "=" + photoMimeTypeId + " AND "
                        + Photo.PHOTO_FILE_ID + " IS NOT NULL", null, null, null, null);
         * @{
         */
        String dataView = mDbHelper.get().getDataView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        Cursor c = db.query(dataView, new String[] {Data._ID, Photo.PHOTO_FILE_ID},
                DataColumns.MIMETYPE_ID + "=" + photoMimeTypeId + " AND "
                        + Photo.PHOTO_FILE_ID + " IS NOT NULL", null, null, null, null);
        /**
         * @}
         */
        Set<Long> usedPhotoFileIds = Sets.newHashSet();
        Map<Long, Long> photoFileIdToDataId = Maps.newHashMap();
        try {
            while (c.moveToNext()) {
                long dataId = c.getLong(0);
                long photoFileId = c.getLong(1);
                usedPhotoFileIds.add(photoFileId);
                photoFileIdToDataId.put(photoFileId, dataId);
            }
        } finally {
            c.close();
        }

        // Also query for all social stream item photos.
        c = db.query(Tables.STREAM_ITEM_PHOTOS + " JOIN " + Tables.STREAM_ITEMS
                + " ON " + StreamItemPhotos.STREAM_ITEM_ID + "=" + StreamItemsColumns.CONCRETE_ID,
                new String[] {
                        StreamItemPhotosColumns.CONCRETE_ID,
                        StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID,
                        StreamItemPhotos.PHOTO_FILE_ID
                },
                null, null, null, null, null);
        Map<Long, Long> photoFileIdToStreamItemPhotoId = Maps.newHashMap();
        Map<Long, Long> streamItemPhotoIdToStreamItemId = Maps.newHashMap();
        try {
            while (c.moveToNext()) {
                long streamItemPhotoId = c.getLong(0);
                long streamItemId = c.getLong(1);
                long photoFileId = c.getLong(2);
                usedPhotoFileIds.add(photoFileId);
                photoFileIdToStreamItemPhotoId.put(photoFileId, streamItemPhotoId);
                streamItemPhotoIdToStreamItemId.put(streamItemPhotoId, streamItemId);
            }
        } finally {
            c.close();
        }

        // Run the photo store cleanup.
        Set<Long> missingPhotoIds = mPhotoStore.get().cleanup(usedPhotoFileIds);

        // If any of the keys we're using no longer exist, clean them up.  We need to do these
        // using internal APIs or direct DB access to avoid permission errors.
        if (!missingPhotoIds.isEmpty()) {
            try {
                // Need to set the db listener because we need to run onCommit afterwards.
                // Make sure to use the proper listener depending on the current mode.
                db.beginTransactionWithListener(inProfileMode() ? mProfileProvider : this);
                for (long missingPhotoId : missingPhotoIds) {
                    if (photoFileIdToDataId.containsKey(missingPhotoId)) {
                        long dataId = photoFileIdToDataId.get(missingPhotoId);
                        ContentValues updateValues = new ContentValues();
                        updateValues.putNull(Photo.PHOTO_FILE_ID);
                        updateData(ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                                updateValues, null, null, false);
                    }
                    if (photoFileIdToStreamItemPhotoId.containsKey(missingPhotoId)) {
                        // For missing photos that were in stream item photos, just delete the
                        // stream item photo.
                        long streamItemPhotoId = photoFileIdToStreamItemPhotoId.get(missingPhotoId);
                        db.delete(Tables.STREAM_ITEM_PHOTOS, StreamItemPhotos._ID + "=?",
                                new String[] {String.valueOf(streamItemPhotoId)});
                    }
                }
                db.setTransactionSuccessful();
            } catch (Exception e) {
                // Cleanup failure is not a fatal problem.  We'll try again later.
                Log.e(TAG, "Failed to clean up outdated photo references", e);
            } finally {
                db.endTransaction();
            }
        }
    }

    @Override
    protected ContactsDatabaseHelper getDatabaseHelper(final Context context) {
        return ContactsDatabaseHelper.getInstance(context);
    }

    @Override
    protected ThreadLocal<ContactsTransaction> getTransactionHolder() {
        return mTransactionHolder;
    }

    public ProfileProvider newProfileProvider() {
        return new ProfileProvider(this);
    }

    @VisibleForTesting
    /* package */ PhotoStore getPhotoStore() {
        return mContactsPhotoStore;
    }

    @VisibleForTesting
    /* package */ PhotoStore getProfilePhotoStore() {
        return mProfilePhotoStore;
    }

    /**
     * Maximum dimension (height or width) of photo thumbnails.
     */
    public int getMaxThumbnailDim() {
        return PhotoProcessor.getMaxThumbnailSize();
    }

    /**
     * Maximum dimension (height or width) of display photos.  Larger images will be scaled
     * to fit.
     */
    public int getMaxDisplayPhotoDim() {
        return PhotoProcessor.getMaxDisplayPhotoSize();
    }

    @VisibleForTesting
    public ContactDirectoryManager getContactDirectoryManagerForTest() {
        return mContactDirectoryManager;
    }

    @VisibleForTesting
    protected Locale getLocale() {
        return Locale.getDefault();
    }

    @VisibleForTesting
    final boolean inProfileMode() {
        Boolean profileMode = mInProfileMode.get();
        return profileMode != null && profileMode;
    }

    /**
     * Wipes all data from the contacts database.
     */
    @NeededForTesting
    void wipeData() {
        invalidateFastScrollingIndexCache();
        mContactsHelper.wipeData();
        mProfileHelper.wipeData();
        mContactsPhotoStore.clear();
        mProfilePhotoStore.clear();
        mProviderStatus = STATUS_NO_ACCOUNTS_NO_CONTACTS;
        initForDefaultLocale();
    }

    /**
     * During initialization, this content provider will block all attempts to change contacts data.
     * In particular, it will hold up all contact syncs.  As soon as the import process is complete,
     * all processes waiting to write to the provider are unblocked, and can proceed to compete for
     * the database transaction monitor.
     */
    private void waitForAccess(CountDownLatch latch) {
        if (latch == null) {
            return;
        }

        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private int getIntValue(ContentValues values, String key, int defaultValue) {
        final Integer value = values.getAsInteger(key);
        return value != null ? value : defaultValue;
    }

    private boolean flagExists(ContentValues values, String key) {
        return values.getAsInteger(key) != null;
    }

    private boolean flagIsSet(ContentValues values, String key) {
        return getIntValue(values, key, 0) != 0;
    }

    private boolean flagIsClear(ContentValues values, String key) {
        return getIntValue(values, key, 1) == 0;
    }

    /**
     * Determines whether the given URI should be directed to the profile
     * database rather than the contacts database.  This is true under either
     * of three conditions:
     * 1. The URI itself is specifically for the profile.
     * 2. The URI contains ID references that are in the profile ID-space.
     * 3. The URI contains lookup key references that match the special profile lookup key.
     * @param uri The URI to examine.
     * @return Whether to direct the DB operation to the profile database.
     */
    private boolean mapsToProfileDb(Uri uri) {
        return sUriMatcher.mapsToProfile(uri);
    }

    /**
     * Determines whether the given URI with the given values being inserted
     * should be directed to the profile database rather than the contacts
     * database.  This is true if the URI already maps to the profile DB from
     * a call to {@link #mapsToProfileDb} or if the URI matches a URI that
     * specifies parent IDs via the ContentValues, and the given ContentValues
     * contains an ID in the profile ID-space.
     * @param uri The URI to examine.
     * @param values The values being inserted.
     * @return Whether to direct the DB insert to the profile database.
     */
    private boolean mapsToProfileDbWithInsertedValues(Uri uri, ContentValues values) {
        if (mapsToProfileDb(uri)) {
            return true;
        }
        int match = sUriMatcher.match(uri);
        if (INSERT_URI_ID_VALUE_MAP.containsKey(match)) {
            String idField = INSERT_URI_ID_VALUE_MAP.get(match);
            Long id = values.getAsLong(idField);
            if (id != null && ContactsContract.isProfileId(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Switches the provider's thread-local context variables to prepare for performing
     * a profile operation.
     */
    private void switchToProfileMode() {
        if (ENABLE_TRANSACTION_LOG) {
            Log.i(TAG, "switchToProfileMode", new RuntimeException("switchToProfileMode"));
        }
        mDbHelper.set(mProfileHelper);
        mTransactionContext.set(mProfileTransactionContext);
        mAggregator.set(mProfileAggregator);
        mPhotoStore.set(mProfilePhotoStore);
        mInProfileMode.set(true);
    }

    /**
     * Switches the provider's thread-local context variables to prepare for performing
     * a contacts operation.
     */
    private void switchToContactMode() {
        if (ENABLE_TRANSACTION_LOG) {
            Log.i(TAG, "switchToContactMode", new RuntimeException("switchToContactMode"));
        }
        mDbHelper.set(mContactsHelper);
        mTransactionContext.set(mContactTransactionContext);
        mAggregator.set(mContactAggregator);
        mPhotoStore.set(mContactsPhotoStore);
        mInProfileMode.set(false);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        waitForAccess(mWriteAccessLatch);

        if (mapsToProfileDbWithInsertedValues(uri, values)) {
            switchToProfileMode();
            return mProfileProvider.insert(uri, values);
        }
        switchToContactMode();
        return super.insert(uri, values);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        waitForAccess(mWriteAccessLatch);

        if (mapsToProfileDb(uri)) {
            switchToProfileMode();
            return mProfileProvider.update(uri, values, selection, selectionArgs);
        }
        switchToContactMode();
        return super.update(uri, values, selection, selectionArgs);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        waitForAccess(mWriteAccessLatch);

        if (mapsToProfileDb(uri)) {
            switchToProfileMode();
            return mProfileProvider.delete(uri, selection, selectionArgs);
        }
        switchToContactMode();
        return super.delete(uri, selection, selectionArgs);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        waitForAccess(mReadAccessLatch);
        switchToContactMode();
        if (Authorization.AUTHORIZATION_METHOD.equals(method)) {
            Uri uri = extras.getParcelable(Authorization.KEY_URI_TO_AUTHORIZE);

            ContactsPermissions.enforceCallingOrSelfPermission(getContext(), READ_PERMISSION);

            // If there hasn't been a security violation yet, we're clear to pre-authorize the URI.
            Uri authUri = preAuthorizeUri(uri);
            Bundle response = new Bundle();
            response.putParcelable(Authorization.KEY_AUTHORIZED_URI, authUri);
            return response;
        } else if (PinnedPositions.UNDEMOTE_METHOD.equals(method)) {
            ContactsPermissions.enforceCallingOrSelfPermission(getContext(), WRITE_PERMISSION);
            final long id;
            try {
                id = Long.valueOf(arg);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Contact ID must be a valid long number.");
            }
            undemoteContact(mDbHelper.get().getWritableDatabase(), id);
            return null;
        }
        return null;
    }

    /**
     * Pre-authorizes the given URI, adding an expiring permission token to it and placing that
     * in our map of pre-authorized URIs.
     * @param uri The URI to pre-authorize.
     * @return A pre-authorized URI that will not require special permissions to use.
     */
    private Uri preAuthorizeUri(Uri uri) {
        String token = String.valueOf(mRandom.nextLong());
        Uri authUri = uri.buildUpon()
                .appendQueryParameter(PREAUTHORIZED_URI_TOKEN, token)
                .build();
        long expiration = Clock.getInstance().currentTimeMillis() + mPreAuthorizedUriDuration;

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final ContentValues values = new ContentValues();
        values.put(PreAuthorizedUris.EXPIRATION, expiration);
        values.put(PreAuthorizedUris.URI, authUri.toString());
        db.insert(Tables.PRE_AUTHORIZED_URIS, null, values);

        return authUri;
    }

    /**
     * Checks whether the given URI has an unexpired permission token that would grant access to
     * query the content.  If it does, the regular permission check should be skipped.
     * @param uri The URI being accessed.
     * @return Whether the URI is a pre-authorized URI that is still valid.
     */
    public boolean isValidPreAuthorizedUri(Uri uri) {
        // Only proceed if the URI has a permission token parameter.
        if (uri.getQueryParameter(PREAUTHORIZED_URI_TOKEN) != null) {
            final long now = Clock.getInstance().currentTimeMillis();
            final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
            db.beginTransaction();
            try {
                // First delete any pre-authorization URIs that are no longer valid. Unfortunately,
                // this operation will grab a write lock for readonly queries. Since this only
                // affects readonly queries that use PREAUTHORIZED_URI_TOKEN, it isn't worth moving
                // this deletion into a BACKGROUND_TASK.
                db.delete(Tables.PRE_AUTHORIZED_URIS, PreAuthorizedUris.EXPIRATION + " < ?1",
                        new String[]{String.valueOf(now)});

                // Now check to see if the pre-authorized URI map contains the URI.
                final Cursor c = db.query(Tables.PRE_AUTHORIZED_URIS, null,
                        PreAuthorizedUris.URI + "=?1",
                        new String[]{uri.toString()}, null, null, null);
                final boolean isValid = c.getCount() != 0;

                db.setTransactionSuccessful();
                return isValid;
            } finally {
                db.endTransaction();
            }
        }
        return false;
    }

    @Override
    protected boolean yield(ContactsTransaction transaction) {
        // If there's a profile transaction in progress, and we're yielding, we need to
        // end it.  Unlike the Contacts DB yield (which re-starts a transaction at its
        // conclusion), we can just go back into a state in which we have no active
        // profile transaction, and let it be re-created as needed.  We can't hold onto
        // the transaction without risking a deadlock.
        SQLiteDatabase profileDb = transaction.removeDbForTag(PROFILE_DB_TAG);
        if (profileDb != null) {
            profileDb.setTransactionSuccessful();
            profileDb.endTransaction();
        }

        // Now proceed with the Contacts DB yield.
        SQLiteDatabase contactsDb = transaction.getDbForTag(CONTACTS_DB_TAG);
        return contactsDb != null && contactsDb.yieldIfContendedSafely(SLEEP_AFTER_YIELD_DELAY);
    }

    @Override
    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        waitForAccess(mWriteAccessLatch);
        return super.applyBatch(operations);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        waitForAccess(mWriteAccessLatch);
        return super.bulkInsert(uri, values);
    }

    @Override
    public void onBegin() {
        onBeginTransactionInternal(false);
    }

    protected void onBeginTransactionInternal(boolean forProfile) {
        if (ENABLE_TRANSACTION_LOG) {
            Log.i(TAG, "onBeginTransaction: " + (forProfile ? "profile" : "contacts"),
                    new RuntimeException("onBeginTransactionInternal"));
        }
        if (forProfile) {
            switchToProfileMode();
            mProfileAggregator.clearPendingAggregations();
            mProfileTransactionContext.clearExceptSearchIndexUpdates();
        } else {
            switchToContactMode();
            mContactAggregator.clearPendingAggregations();
            mContactTransactionContext.clearExceptSearchIndexUpdates();
        }
    }

    @Override
    public void onCommit() {
        onCommitTransactionInternal(false);
    }

    protected void onCommitTransactionInternal(boolean forProfile) {
        if (ENABLE_TRANSACTION_LOG) {
            Log.i(TAG, "onCommitTransactionInternal: " + (forProfile ? "profile" : "contacts"),
                    new RuntimeException("onCommitTransactionInternal"));
        }
        if (forProfile) {
            switchToProfileMode();
        } else {
            switchToContactMode();
        }

        flushTransactionalChanges();
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        mAggregator.get().aggregateInTransaction(mTransactionContext.get(), db);
        if (mVisibleTouched) {
            mVisibleTouched = false;
            mDbHelper.get().updateAllVisible();

            // Need to rebuild the fast-indxer bundle.
            invalidateFastScrollingIndexCache();
        }

        updateSearchIndexInTransaction();

        if (mProviderStatusUpdateNeeded) {
            updateProviderStatus();
            mProviderStatusUpdateNeeded = false;
        }
    }

    @Override
    public void onRollback() {
        onRollbackTransactionInternal(false);
    }

    protected void onRollbackTransactionInternal(boolean forProfile) {
        if (ENABLE_TRANSACTION_LOG) {
            Log.i(TAG, "onRollbackTransactionInternal: " + (forProfile ? "profile" : "contacts"),
                    new RuntimeException("onRollbackTransactionInternal"));
        }
        if (forProfile) {
            switchToProfileMode();
        } else {
            switchToContactMode();
        }

        mDbHelper.get().invalidateAllCache();
    }

    private void updateSearchIndexInTransaction() {
        Set<Long> staleContacts = mTransactionContext.get().getStaleSearchIndexContactIds();
        Set<Long> staleRawContacts = mTransactionContext.get().getStaleSearchIndexRawContactIds();
        if (!staleContacts.isEmpty() || !staleRawContacts.isEmpty()) {
            mSearchIndexManager.updateIndexForRawContacts(staleContacts, staleRawContacts);
            mTransactionContext.get().clearSearchIndexUpdates();
        }
    }

    private void flushTransactionalChanges() {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "flushTransactionalChanges: " + (inProfileMode() ? "profile" : "contacts"));
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        for (long rawContactId : mTransactionContext.get().getInsertedRawContactIds()) {
            mDbHelper.get().updateRawContactDisplayName(db, rawContactId);
            mAggregator.get().onRawContactInsert(mTransactionContext.get(), db, rawContactId);
            /**
            * SPRD:Bug474742 Update RawContact for AppBackup and cleaning up contacts.
            *
            * @{
            */
            mDbHelper.get().updateRawContactIdentity(db, rawContactId);
            /**
            * @}
            */

            /**
             * SPRD: insert/update sim-card contact, bug421665
             *
             * @{
             */
            ContactProxyManager contactProxyManager = ContactProxyManager.getInstance(getContext());
            SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(contactProxyManager,
                    getContext());
            ContentValues proxyValues = null;
            if (simProxyUtil.isMyContact(rawContactId)) {
                proxyValues = contactProxyManager.insert(rawContactId,
                        AccountUtils.getAccount(mDbHelper.get().getAccount(
                                mTransactionContext.get().getAccountIdOrNullForRawContact(
                                        rawContactId))));
            }
            if (proxyValues != null) {
                Set<String> keys = proxyValues.keySet();
                StringBuilder sb = new StringBuilder();
                boolean init = true;
                for (String key : keys) {
                    if (!init) {
                        sb.append(", ");
                    }
                    init = false;
                    sb.append(key);
                    sb.append(" = '");
                    sb.append(proxyValues.getAsString(key));
                    sb.append("' ");
                }
                db.execSQL(
                        "UPDATE " + Tables.RAW_CONTACTS +
                                " SET " +
                                sb.toString() +
                                " WHERE " + RawContacts._ID + " = " + rawContactId
                        );
            }
            /**
             * @}
             */
        }

        /**
         * SPRD: insert/update sim-card contact, bug421665
         *
         * @{
         */
        try {
        /**
         * @}
         */
        Set<Long> dirtyRawContacts = mTransactionContext.get().getDirtyRawContactIds();
        if (!dirtyRawContacts.isEmpty()) {
            mSb.setLength(0);
            mSb.append(UPDATE_RAW_CONTACT_SET_DIRTY_SQL);
            appendIds(mSb, dirtyRawContacts);
            mSb.append(")");
            db.execSQL(mSb.toString());
        }

        /**
         * SPRD: insert/update sim-card contact, bug421665
         *
         * @{
         */
        long rawContactId = -1;
        Iterator it = dirtyRawContacts.iterator();
        while (it.hasNext()) {
            rawContactId = (Long) it.next();
            if (!ContactsContract.isProfileId(rawContactId)) {
                ContactProxyManager contactProxyManager = ContactProxyManager
                        .getInstance(getContext());
                SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(
                        contactProxyManager, getContext());
                if (simProxyUtil.isMyContact(rawContactId)) {
                    if ((contactProxyManager.getProxySimContactCache())
                            .getIsDirty(rawContactId)) {
                        simProxyUtil.buildSimContactCache(rawContactId);
                        contactProxyManager.update(rawContactId);
                    }
                }
            }

        }
        /**
         * @}
         */
        Set<Long> updatedRawContacts = mTransactionContext.get().getUpdatedRawContactIds();
        if (!updatedRawContacts.isEmpty()) {
            mSb.setLength(0);
            mSb.append(UPDATE_RAW_CONTACT_SET_VERSION_SQL);
            appendIds(mSb, updatedRawContacts);
            mSb.append(")");
            db.execSQL(mSb.toString());
        }

        final Set<Long> changedRawContacts = mTransactionContext.get().getChangedRawContactIds();
        ContactsTableUtil.updateContactLastUpdateByRawContactId(db, changedRawContacts);

        // Update sync states.
        for (Map.Entry<Long, Object> entry : mTransactionContext.get().getUpdatedSyncStates()) {
            long id = entry.getKey();
            if (mDbHelper.get().getSyncState().update(db, id, entry.getValue()) <= 0) {
                throw new IllegalStateException(
                        "unable to update sync state, does it still exist?");
            }
        }
        /**
         * SPRD: insert/update sim-card contact, bug421665
         *
         * @{
         */
        } catch (Exception e) {
            if (e instanceof IccPBForRecordException) {
                IccPBForRecordException exception = (IccPBForRecordException) e;
                throw exception;
            } else if (e instanceof IccPBForMimetypeException) {
                IccPBForMimetypeException exception = (IccPBForMimetypeException) e;
                throw exception;
            } else {
                e.printStackTrace();
                throw new IllegalStateException("flushTransactionChanges");
            }

        } finally {

        mTransactionContext.get().clearExceptSearchIndexUpdates();

        }
        /**
         * @}
         */
    }

    /**
     * Appends comma separated IDs.
     * @param ids Should not be empty
     */
    private void appendIds(StringBuilder sb, Set<Long> ids) {
        for (long id : ids) {
            sb.append(id).append(',');
        }

        sb.setLength(sb.length() - 1); // Yank the last comma
    }

    @Override
    protected void notifyChange() {
        notifyChange(mSyncToNetwork);
        mSyncToNetwork = false;
    }

    protected void notifyChange(boolean syncToNetwork) {
        getContext().getContentResolver().notifyChange(ContactsContract.AUTHORITY_URI, null,
                syncToNetwork);
    }

    protected void setProviderStatus(int status) {
        if (mProviderStatus != status) {
            mProviderStatus = status;
            getContext().getContentResolver().notifyChange(ProviderStatus.CONTENT_URI, null, false);
        }
    }

    public DataRowHandler getDataRowHandler(final String mimeType) {
        if (inProfileMode()) {
            return getDataRowHandlerForProfile(mimeType);
        }
        DataRowHandler handler = mDataRowHandlers.get(mimeType);
        if (handler == null) {
            handler = new DataRowHandlerForCustomMimetype(
                    getContext(), mContactsHelper, mContactAggregator, mimeType);
            mDataRowHandlers.put(mimeType, handler);
        }
        return handler;
    }

    public DataRowHandler getDataRowHandlerForProfile(final String mimeType) {
        DataRowHandler handler = mProfileDataRowHandlers.get(mimeType);
        if (handler == null) {
            handler = new DataRowHandlerForCustomMimetype(
                    getContext(), mProfileHelper, mProfileAggregator, mimeType);
            mProfileDataRowHandlers.put(mimeType, handler);
        }
        return handler;
    }

    @Override
    protected Uri insertInTransaction(Uri uri, ContentValues values) {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "insertInTransaction: uri=" + uri + "  values=[" + values + "]" +
                    " CPID=" + Binder.getCallingPid());
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        final boolean callerIsSyncAdapter =
                readBooleanQueryParameter(uri, ContactsContract.CALLER_IS_SYNCADAPTER, false);

        final int match = sUriMatcher.match(uri);
        long id = 0;

        switch (match) {
            case SYNCSTATE:
            case PROFILE_SYNCSTATE:
                id = mDbHelper.get().getSyncState().insert(db, values);
                break;

            case CONTACTS: {
                invalidateFastScrollingIndexCache();
                insertContact(values);
                break;
            }

            case PROFILE: {
                throw new UnsupportedOperationException(
                        "The profile contact is created automatically");
            }

            case RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS: {
                invalidateFastScrollingIndexCache();
                id = insertRawContact(uri, values, callerIsSyncAdapter);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case RAW_CONTACTS_ID_DATA:
            case PROFILE_RAW_CONTACTS_ID_DATA: {
                invalidateFastScrollingIndexCache();
                int segment = match == RAW_CONTACTS_ID_DATA ? 1 : 2;
                values.put(Data.RAW_CONTACT_ID, uri.getPathSegments().get(segment));
                id = insertData(values, callerIsSyncAdapter);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS: {
                values.put(StreamItems.RAW_CONTACT_ID, uri.getPathSegments().get(1));
                id = insertStreamItem(uri, values);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case DATA:
            case PROFILE_DATA: {
                invalidateFastScrollingIndexCache();
                id = insertData(values, callerIsSyncAdapter);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case GROUPS: {
                id = insertGroup(uri, values, callerIsSyncAdapter);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case SETTINGS: {
                id = insertSettings(values);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case STATUS_UPDATES:
            case PROFILE_STATUS_UPDATES: {
                id = insertStatusUpdate(values);
                break;
            }

            case STREAM_ITEMS: {
                id = insertStreamItem(uri, values);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case STREAM_ITEMS_PHOTOS: {
                id = insertStreamItemPhoto(uri, values);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS: {
                values.put(StreamItemPhotos.STREAM_ITEM_ID, uri.getPathSegments().get(1));
                id = insertStreamItemPhoto(uri, values);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            default:
                mSyncToNetwork = true;
                return mLegacyApiSupport.insert(uri, values);
        }

        if (id < 0) {
            return null;
        }

        return ContentUris.withAppendedId(uri, id);
    }

    /**
     * If account is non-null then store it in the values. If the account is
     * already specified in the values then it must be consistent with the
     * account, if it is non-null.
     *
     * @param uri Current {@link Uri} being operated on.
     * @param values {@link ContentValues} to read and possibly update.
     * @throws IllegalArgumentException when only one of
     *             {@link RawContacts#ACCOUNT_NAME} or
     *             {@link RawContacts#ACCOUNT_TYPE} is specified, leaving the
     *             other undefined.
     * @throws IllegalArgumentException when {@link RawContacts#ACCOUNT_NAME}
     *             and {@link RawContacts#ACCOUNT_TYPE} are inconsistent between
     *             the given {@link Uri} and {@link ContentValues}.
     */
    private Account resolveAccount(Uri uri, ContentValues values) throws IllegalArgumentException {
        String accountName = getQueryParameter(uri, RawContacts.ACCOUNT_NAME);
        String accountType = getQueryParameter(uri, RawContacts.ACCOUNT_TYPE);
        final boolean partialUri = TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType);

        String valueAccountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        String valueAccountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
        final boolean partialValues = TextUtils.isEmpty(valueAccountName)
                ^ TextUtils.isEmpty(valueAccountType);

        if (partialUri || partialValues) {
            // Throw when either account is incomplete.
            throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                    "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE", uri));
        }

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validUri = !TextUtils.isEmpty(accountName);
        final boolean validValues = !TextUtils.isEmpty(valueAccountName);

        if (validValues && validUri) {
            // Check that accounts match when both present
            final boolean accountMatch = TextUtils.equals(accountName, valueAccountName)
                    && TextUtils.equals(accountType, valueAccountType);
            if (!accountMatch) {
                throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                        "When both specified, ACCOUNT_NAME and ACCOUNT_TYPE must match", uri));
            }
        } else if (validUri) {
            // Fill values from the URI when not present.
            values.put(RawContacts.ACCOUNT_NAME, accountName);
            values.put(RawContacts.ACCOUNT_TYPE, accountType);
        } else if (validValues) {
            accountName = valueAccountName;
            accountType = valueAccountType;

        /**
         * SPRD: for bug 164716
         *
         * @{
         */
        } else if (getDefaultAccount() != null) {
            accountName = getDefaultAccount().name;
            accountType = getDefaultAccount().type;
        /**
         * @}
         */
        } else {
            return null;
        }

        // Use cached Account object when matches, otherwise create
        if (mAccount == null
                || !mAccount.name.equals(accountName)
                || !mAccount.type.equals(accountType)) {
            mAccount = new Account(accountName, accountType);
        }

        return mAccount;
    }

    /**
     * Resolves the account and builds an {@link AccountWithDataSet} based on the data set specified
     * in the URI or values (if any).
     * @param uri Current {@link Uri} being operated on.
     * @param values {@link ContentValues} to read and possibly update.
     */
    private AccountWithDataSet resolveAccountWithDataSet(Uri uri, ContentValues values) {
        final Account account = resolveAccount(uri, values);
        AccountWithDataSet accountWithDataSet = null;
        if (account != null) {
            String dataSet = getQueryParameter(uri, RawContacts.DATA_SET);
            if (dataSet == null) {
                dataSet = values.getAsString(RawContacts.DATA_SET);
            } else {
                values.put(RawContacts.DATA_SET, dataSet);
            }
            accountWithDataSet = AccountWithDataSet.get(account.name, account.type, dataSet);
        }
        return accountWithDataSet;
    }

    /**
     * Inserts an item in the contacts table
     *
     * @param values the values for the new row
     * @return the row ID of the newly created row
     */
    private long insertContact(ContentValues values) {
        throw new UnsupportedOperationException("Aggregate contacts are created automatically");
    }

    /**
     * Inserts a new entry into the raw-contacts table.
     *
     * @param uri The insertion URI.
     * @param inputValues The values for the new row.
     * @param callerIsSyncAdapter True to identify the entity invoking this method as a SyncAdapter
     *     and false otherwise.
     * @return the ID of the newly-created row.
     */
    private long insertRawContact(
            Uri uri, ContentValues inputValues, boolean callerIsSyncAdapter) {

        // Create a shallow copy and initialize the contact ID to null.
        final ContentValues values = new ContentValues(inputValues);
        values.putNull(RawContacts.CONTACT_ID);

        // Populate the relevant values before inserting the new entry into the database.
        final long accountId = replaceAccountInfoByAccountId(uri, values);
        if (flagIsSet(values, RawContacts.DELETED)) {
            values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
        }

        // Databases that were created prior to the 906 upgrade have a default of Int.MAX_VALUE
        // for RawContacts.PINNED. Manually set the value to the correct default (0) if it is not
        // set.
        if (!values.containsKey(RawContacts.PINNED)) {
            values.put(RawContacts.PINNED, PinnedPositions.UNPINNED);
        }

        // Insert the new entry.
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final long rawContactId = db.insert(Tables.RAW_CONTACTS, RawContacts.CONTACT_ID, values);
        /*
        * SPRD:
        * bug 254366 optimize for restoring contacts to phone account from sd card
        * @{
        */
        String accountType = (String) inputValues.get(RawContacts.ACCOUNT_TYPE);
        if ((accountType != null && !accountType.contains("sim")) || accountType == null) {
            ContactProxyManager.getInstance(getContext()).addToNonSimContactCache(rawContactId);
        }
        /*
        * @}
        */
        final int aggregationMode = getIntValue(values, RawContacts.AGGREGATION_MODE,
                RawContacts.AGGREGATION_MODE_DEFAULT);
        mAggregator.get().markNewForAggregation(rawContactId, aggregationMode);

        // Trigger creation of a Contact based on this RawContact at the end of transaction.
        mTransactionContext.get().rawContactInserted(rawContactId, accountId);

        if (!callerIsSyncAdapter) {
            addAutoAddMembership(rawContactId);
            if (flagIsSet(values, RawContacts.STARRED)) {
                updateFavoritesMembership(rawContactId, true);
            }
        }

        mProviderStatusUpdateNeeded = true;
        return rawContactId;
    }

    private void addAutoAddMembership(long rawContactId) {
        final Long groupId =
                findGroupByRawContactId(SELECTION_AUTO_ADD_GROUPS_BY_RAW_CONTACT_ID, rawContactId);
        if (groupId != null) {
            insertDataGroupMembership(rawContactId, groupId);
        }
    }

    private Long findGroupByRawContactId(String selection, long rawContactId) {
        final SQLiteDatabase db = mDbHelper.get().getReadableDatabase();
        Cursor c = db.query(Tables.GROUPS + "," + Tables.RAW_CONTACTS,
                PROJECTION_GROUP_ID, selection,
                new String[] {Long.toString(rawContactId)},
                null /* groupBy */, null /* having */, null /* orderBy */);
        try {
            while (c.moveToNext()) {
                return c.getLong(0);
            }
            return null;
        } finally {
            c.close();
        }
    }

    private void updateFavoritesMembership(long rawContactId, boolean isStarred) {
        final Long groupId =
                findGroupByRawContactId(SELECTION_FAVORITES_GROUPS_BY_RAW_CONTACT_ID, rawContactId);
        if (groupId != null) {
            if (isStarred) {
                insertDataGroupMembership(rawContactId, groupId);
            } else {
                deleteDataGroupMembership(rawContactId, groupId);
            }
        }
    }

    private void insertDataGroupMembership(long rawContactId, long groupId) {
        ContentValues groupMembershipValues = new ContentValues();
        groupMembershipValues.put(GroupMembership.GROUP_ROW_ID, groupId);
        groupMembershipValues.put(GroupMembership.RAW_CONTACT_ID, rawContactId);
        groupMembershipValues.put(DataColumns.MIMETYPE_ID,
                mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE));

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        db.insert(Tables.DATA, null, groupMembershipValues);
    }

    private void deleteDataGroupMembership(long rawContactId, long groupId) {
        final String[] selectionArgs = {
                Long.toString(mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE)),
                Long.toString(groupId),
                Long.toString(rawContactId)};

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        db.delete(Tables.DATA, SELECTION_GROUPMEMBERSHIP_DATA, selectionArgs);
    }

    /**
     * Inserts a new entry into the (contact) data table.
     *
     * @param inputValues The values for the new row.
     * @return The ID of the newly-created row.
     */
    private long insertData(ContentValues inputValues, boolean callerIsSyncAdapter) {
        final Long rawContactId = inputValues.getAsLong(Data.RAW_CONTACT_ID);
        if (rawContactId == null) {
            throw new IllegalArgumentException(Data.RAW_CONTACT_ID + " is required");
        }

        final String mimeType = inputValues.getAsString(Data.MIMETYPE);
        if (TextUtils.isEmpty(mimeType)) {
            throw new IllegalArgumentException(Data.MIMETYPE + " is required");
        }

        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            maybeTrimLongPhoneNumber(inputValues);
        }

        // The input seem valid, create a shallow copy.
        final ContentValues values = new ContentValues(inputValues);

        // Populate the relevant values before inserting the new entry into the database.
        replacePackageNameByPackageId(values);

        // Replace the mimetype by the corresponding mimetype ID.
        values.put(DataColumns.MIMETYPE_ID, mDbHelper.get().getMimeTypeId(mimeType));
        values.remove(Data.MIMETYPE);

        // Insert the new entry.
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final TransactionContext context = mTransactionContext.get();
        final long dataId = getDataRowHandler(mimeType).insert(db, context, rawContactId, values);
        context.markRawContactDirtyAndChanged(rawContactId, callerIsSyncAdapter);
        context.rawContactUpdated(rawContactId);

        return dataId;
    }

    /**
     * Inserts an item in the stream_items table.  The account is checked against the
     * account in the raw contact for which the stream item is being inserted.  If the
     * new stream item results in more stream items under this raw contact than the limit,
     * the oldest one will be deleted (note that if the stream item inserted was the
     * oldest, it will be immediately deleted, and this will return 0).
     *
     * @param uri the insertion URI
     * @param inputValues the values for the new row
     * @return the stream item _ID of the newly created row, or 0 if it was not created
     */
    private long insertStreamItem(Uri uri, ContentValues inputValues) {
        Long rawContactId = inputValues.getAsLong(Data.RAW_CONTACT_ID);
        if (rawContactId == null) {
            throw new IllegalArgumentException(Data.RAW_CONTACT_ID + " is required");
        }

        // The input seem valid, create a shallow copy.
        final ContentValues values = new ContentValues(inputValues);

        // Update the relevant values before inserting the new entry into the database.  The
        // account parameters are not added since they don't exist in the stream items table.
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);

        // Insert the new stream item.
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final long id = db.insert(Tables.STREAM_ITEMS, null, values);
        if (id == -1) {
            return 0;  // Insertion failed.
        }

        // Check to see if we're over the limit for stream items under this raw contact.
        // It's possible that the inserted stream item is older than the the existing
        // ones, in which case it may be deleted immediately (resetting the ID to 0).
        return cleanUpOldStreamItems(rawContactId, id);
    }

    /**
     * Inserts an item in the stream_item_photos table.  The account is checked against
     * the account in the raw contact that owns the stream item being modified.
     *
     * @param uri the insertion URI.
     * @param inputValues The values for the new row.
     * @return The stream item photo _ID of the newly created row, or 0 if there was an issue
     *     with processing the photo or creating the row.
     */
    private long insertStreamItemPhoto(Uri uri, ContentValues inputValues) {
        final Long streamItemId = inputValues.getAsLong(StreamItemPhotos.STREAM_ITEM_ID);
        if (streamItemId == null || streamItemId == 0) {
            return 0;
        }

        // The input seem valid, create a shallow copy.
        final ContentValues values = new ContentValues(inputValues);

        // Update the relevant values before inserting the new entry into the database.  The
        // account parameters are not added since they don't exist in the stream items table.
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);

        // Attempt to process and store the photo.
        if (!processStreamItemPhoto(values, false)) {
            return 0;
        }

        // Insert the new entry and return its ID.
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        return db.insert(Tables.STREAM_ITEM_PHOTOS, null, values);
    }

    /**
     * Processes the photo contained in the {@link StreamItemPhotos#PHOTO} field of the given
     * values, attempting to store it in the photo store.  If successful, the resulting photo
     * file ID will be added to the values for insert/update in the table.
     * <p>
     * If updating, it is valid for the picture to be empty or unspecified (the function will
     * still return true).  If inserting, a valid picture must be specified.
     * @param values The content values provided by the caller.
     * @param forUpdate Whether this photo is being processed for update (vs. insert).
     * @return Whether the insert or update should proceed.
     */
    private boolean processStreamItemPhoto(ContentValues values, boolean forUpdate) {
        byte[] photoBytes = values.getAsByteArray(StreamItemPhotos.PHOTO);
        if (photoBytes == null) {
            return forUpdate;
        }

        // Process the photo and store it.
        IOException exception = null;
        try {
            final PhotoProcessor processor = new PhotoProcessor(
                    photoBytes, getMaxDisplayPhotoDim(), getMaxThumbnailDim(), true);
            long photoFileId = mPhotoStore.get().insert(processor, true);
            if (photoFileId != 0) {
                values.put(StreamItemPhotos.PHOTO_FILE_ID, photoFileId);
                values.remove(StreamItemPhotos.PHOTO);
                return true;
            }
        } catch (IOException ioe) {
            exception = ioe;
        }

        Log.e(TAG, "Could not process stream item photo for insert", exception);
        return false;
    }

    /**
     * Queries the database for stream items under the given raw contact.  If there are
     * more entries than {@link ContactsProvider2#MAX_STREAM_ITEMS_PER_RAW_CONTACT},
     * the oldest entries (as determined by timestamp) will be deleted.
     * @param rawContactId The raw contact ID to examine for stream items.
     * @param insertedStreamItemId The ID of the stream item that was just inserted,
     *     prompting this cleanup.  Callers may pass 0 if no insertion prompted the
     *     cleanup.
     * @return The ID of the inserted stream item if it still exists after cleanup;
     *     0 otherwise.
     */
    private long cleanUpOldStreamItems(long rawContactId, long insertedStreamItemId) {
        long postCleanupInsertedStreamId = insertedStreamItemId;
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        Cursor c = db.query(Tables.STREAM_ITEMS, new String[] {StreamItems._ID},
                StreamItems.RAW_CONTACT_ID + "=?", new String[] {String.valueOf(rawContactId)},
                null, null, StreamItems.TIMESTAMP + " DESC, " + StreamItems._ID + " DESC");
        try {
            int streamItemCount = c.getCount();
            if (streamItemCount <= MAX_STREAM_ITEMS_PER_RAW_CONTACT) {
                // Still under the limit - nothing to clean up!
                return insertedStreamItemId;
            }

            c.moveToLast();
            while (c.getPosition() >= MAX_STREAM_ITEMS_PER_RAW_CONTACT) {
                long streamItemId = c.getLong(0);
                if (insertedStreamItemId == streamItemId) {
                    // The stream item just inserted is being deleted.
                    postCleanupInsertedStreamId = 0;
                }
                deleteStreamItem(db, c.getLong(0));
                c.moveToPrevious();
            }
        } finally {
            c.close();
        }
        return postCleanupInsertedStreamId;
    }

    /**
     * Delete data row by row so that fixing of primaries etc work correctly.
     */
    private int deleteData(String selection, String[] selectionArgs, boolean callerIsSyncAdapter) {
        int count = 0;

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // Note that the query will return data according to the access restrictions,
        // so we don't need to worry about deleting data we don't have permission to read.
        Uri dataUri = inProfileMode()
                ? Uri.withAppendedPath(Profile.CONTENT_URI, RawContacts.Data.CONTENT_DIRECTORY)
                : Data.CONTENT_URI;
        Cursor c = query(dataUri, DataRowHandler.DataDeleteQuery.COLUMNS,
                selection, selectionArgs, null);
        try {
            while(c.moveToNext()) {
                long rawContactId = c.getLong(DataRowHandler.DataDeleteQuery.RAW_CONTACT_ID);
                String mimeType = c.getString(DataRowHandler.DataDeleteQuery.MIMETYPE);
                DataRowHandler rowHandler = getDataRowHandler(mimeType);
                count += rowHandler.delete(db, mTransactionContext.get(), c);
                mTransactionContext.get().markRawContactDirtyAndChanged(
                        rawContactId, callerIsSyncAdapter);
            }
        } finally {
            c.close();
        }

        return count;
    }

    /**
     * Delete a data row provided that it is one of the allowed mime types.
     */
    public int deleteData(long dataId, String[] allowedMimeTypes) {

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // Note that the query will return data according to the access restrictions,
        // so we don't need to worry about deleting data we don't have permission to read.
        mSelectionArgs1[0] = String.valueOf(dataId);
        Cursor c = query(Data.CONTENT_URI, DataRowHandler.DataDeleteQuery.COLUMNS, Data._ID + "=?",
                mSelectionArgs1, null);

        try {
            if (!c.moveToFirst()) {
                return 0;
            }

            String mimeType = c.getString(DataRowHandler.DataDeleteQuery.MIMETYPE);
            boolean valid = false;
            for (String type : allowedMimeTypes) {
                if (TextUtils.equals(mimeType, type)) {
                    valid = true;
                    break;
                }
            }

            if (!valid) {
                throw new IllegalArgumentException("Data type mismatch: expected "
                        + Lists.newArrayList(allowedMimeTypes));
            }
            DataRowHandler rowHandler = getDataRowHandler(mimeType);
            return rowHandler.delete(db, mTransactionContext.get(), c);
        } finally {
            c.close();
        }
    }

    /**
     * Inserts a new entry into the groups table.
     *
     * @param uri The insertion URI.
     * @param inputValues The values for the new row.
     * @param callerIsSyncAdapter True to identify the entity invoking this method as a SyncAdapter
     *     and false otherwise.
     * @return the ID of the newly-created row.
     */
    private long insertGroup(Uri uri, ContentValues inputValues, boolean callerIsSyncAdapter) {
        // Create a shallow copy.
        final ContentValues values = new ContentValues(inputValues);

        /**
         * SPRD:Bug425784 Add for cts AddGroupMembership test.
         *
         * @{
         */
        // set default phone account
        if (TextUtils.isEmpty(values.getAsString(RawContacts.ACCOUNT_NAME))) {
            Log.e(TAG, "set to default account");
            String accountName = getQueryParameter(uri, RawContacts.ACCOUNT_NAME);
            String accountType = getQueryParameter(uri, RawContacts.ACCOUNT_TYPE);
            if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                values.put(RawContacts.ACCOUNT_NAME, accountName);
                values.put(RawContacts.ACCOUNT_TYPE, accountType);
            } else if (getDefaultAccount() != null) {
                values.put(RawContacts.ACCOUNT_NAME, getDefaultAccount().name);
                values.put(RawContacts.ACCOUNT_TYPE, getDefaultAccount().type);
            } else {
                values.put(RawContacts.ACCOUNT_NAME, "phone");
                values.put(RawContacts.ACCOUNT_TYPE, "sprd.com.android.account.phone");
            }
        }
        /**
         * @}
         */
        // Populate the relevant values before inserting the new entry into the database.
        /**
         * SPRD: new contacts can select a group, bug422980
         * Original Android code:
        final long accountId = replaceAccountInfoByAccountId(uri, values);
         * @{
         */
        final long accountId = mDbHelper.get().getOrCreateAccountIdInTransaction(resolveAccountWithDataSet(uri, values));
        values.put(RawContactsColumns.ACCOUNT_ID, accountId);
        /**
         * @}
         */
        replacePackageNameByPackageId(values);
        if (!callerIsSyncAdapter) {
            values.put(Groups.DIRTY, 1);
        }
        /**
         * SPRD: new contacts can select a group, bug422980
         *
         * @{
         */
        Account account = new Account(values.getAsString(RawContacts.ACCOUNT_NAME),
                values.getAsString(RawContacts.ACCOUNT_TYPE));
        //SPRD: bug377378, group table does not require account_name and account_type, remove it before insert
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);
        values.remove(RawContacts.DATA_SET);
        /**
         * @}
         */
        // Insert the new entry.
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final long groupId = db.insert(Tables.GROUPS, Groups.TITLE, values);

        final boolean isFavoritesGroup = flagIsSet(values, Groups.FAVORITES);

        /**
         * SPRD: insert sim group, bug422980
         *
         * @{
         */
        if (!callerIsSyncAdapter) {
            ContactProxyManager contactProxyManager = ContactProxyManager.getInstance(getContext());
            SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(contactProxyManager,
                    getContext());
            ContentValues proxyValues = null;
            if (simProxyUtil.isMyContactGroup(groupId)) {
                proxyValues = contactProxyManager.insertGroup(groupId, values, account);
            }

            if (proxyValues != null) {
                Set<String> keys = proxyValues.keySet();
                StringBuilder sb = new StringBuilder();
                boolean init = true;
                for (String key : keys) {
                    if (!init) {
                        sb.append(", ");
                    }
                    init = false;
                    sb.append(key);
                    sb.append(" = '");
                    sb.append(proxyValues.getAsString(key));
                    sb.append("' ");
                }
                db.execSQL(
                        "UPDATE " + Tables.GROUPS +
                                " SET " +
                                sb.toString() +
                                " WHERE " + Groups._ID + " = " + groupId
                        );
            /**
             * SPRD:Bug454872 Throw the exception when sim/usim group insert failed.
             * @{
             */
            } else if (proxyValues == null && account.type.matches("sprd.*sim")){
                throw new IllegalStateException("sim insert group failed");
            /**
             * @}
             */
            }
        }
        /**
         * @}
         */

        if (!callerIsSyncAdapter && isFavoritesGroup) {
            // Favorite group, add all starred raw contacts to it.
            mSelectionArgs1[0] = Long.toString(accountId);
            Cursor c = db.query(Tables.RAW_CONTACTS,
                    new String[] {RawContacts._ID, RawContacts.STARRED},
                    RawContactsColumns.CONCRETE_ACCOUNT_ID + "=?", mSelectionArgs1,
                    null, null, null);
            try {
                while (c.moveToNext()) {
                    if (c.getLong(1) != 0) {
                        final long rawContactId = c.getLong(0);
                        insertDataGroupMembership(rawContactId, groupId);
                        mTransactionContext.get().markRawContactDirtyAndChanged(
                                rawContactId, callerIsSyncAdapter);
                    }
                }
            } finally {
                c.close();
            }
        }

        if (values.containsKey(Groups.GROUP_VISIBLE)) {
            mVisibleTouched = true;
        }
        return groupId;
    }

    private long insertSettings(ContentValues values) {
        // Before inserting, ensure that no settings record already exists for the
        // values being inserted (this used to be enforced by a primary key, but that no
        // longer works with the nullable data_set field added).
        String accountName = values.getAsString(Settings.ACCOUNT_NAME);
        String accountType = values.getAsString(Settings.ACCOUNT_TYPE);
        String dataSet = values.getAsString(Settings.DATA_SET);
        Uri.Builder settingsUri = Settings.CONTENT_URI.buildUpon();
        if (accountName != null) {
            settingsUri.appendQueryParameter(Settings.ACCOUNT_NAME, accountName);
        }
        if (accountType != null) {
            settingsUri.appendQueryParameter(Settings.ACCOUNT_TYPE, accountType);
        }
        if (dataSet != null) {
            settingsUri.appendQueryParameter(Settings.DATA_SET, dataSet);
        }
        Cursor c = queryLocal(settingsUri.build(), null, null, null, null, 0, null);
        try {
            if (c.getCount() > 0) {
                // If a record was found, replace it with the new values.
                String selection = null;
                String[] selectionArgs = null;
                if (accountName != null && accountType != null) {
                    selection = Settings.ACCOUNT_NAME + "=? AND " + Settings.ACCOUNT_TYPE + "=?";
                    if (dataSet == null) {
                        selection += " AND " + Settings.DATA_SET + " IS NULL";
                        selectionArgs = new String[] {accountName, accountType};
                    } else {
                        selection += " AND " + Settings.DATA_SET + "=?";
                        selectionArgs = new String[] {accountName, accountType, dataSet};
                    }
                }
                return updateSettings(values, selection, selectionArgs);
            }
        } finally {
            c.close();
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // If we didn't find a duplicate, we're fine to insert.
        final long id = db.insert(Tables.SETTINGS, null, values);

        if (values.containsKey(Settings.UNGROUPED_VISIBLE)) {
            mVisibleTouched = true;
        }

        return id;
    }

    /**
     * Inserts a status update.
     */
    private long insertStatusUpdate(ContentValues inputValues) {
        final String handle = inputValues.getAsString(StatusUpdates.IM_HANDLE);
        final Integer protocol = inputValues.getAsInteger(StatusUpdates.PROTOCOL);
        String customProtocol = null;

        final ContactsDatabaseHelper dbHelper = mDbHelper.get();
        final SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (protocol != null && protocol == Im.PROTOCOL_CUSTOM) {
            customProtocol = inputValues.getAsString(StatusUpdates.CUSTOM_PROTOCOL);
            if (TextUtils.isEmpty(customProtocol)) {
                throw new IllegalArgumentException(
                        "CUSTOM_PROTOCOL is required when PROTOCOL=PROTOCOL_CUSTOM");
            }
        }

        long rawContactId = -1;
        long contactId = -1;
        Long dataId = inputValues.getAsLong(StatusUpdates.DATA_ID);
        String accountType = null;
        String accountName = null;
        mSb.setLength(0);
        mSelectionArgs.clear();
        if (dataId != null) {
            // Lookup the contact info for the given data row.

            mSb.append(Tables.DATA + "." + Data._ID + "=?");
            mSelectionArgs.add(String.valueOf(dataId));
        } else {
            // Lookup the data row to attach this presence update to

            if (TextUtils.isEmpty(handle) || protocol == null) {
                throw new IllegalArgumentException("PROTOCOL and IM_HANDLE are required");
            }

            // TODO: generalize to allow other providers to match against email.
            boolean matchEmail = Im.PROTOCOL_GOOGLE_TALK == protocol;

            String mimeTypeIdIm = String.valueOf(dbHelper.getMimeTypeIdForIm());
            if (matchEmail) {
                String mimeTypeIdEmail = String.valueOf(dbHelper.getMimeTypeIdForEmail());

                // The following hack forces SQLite to use the (mimetype_id,data1) index, otherwise
                // the "OR" conjunction confuses it and it switches to a full scan of
                // the raw_contacts table.

                // This code relies on the fact that Im.DATA and Email.DATA are in fact the same
                // column - Data.DATA1
                mSb.append(DataColumns.MIMETYPE_ID + " IN (?,?)" +
                        " AND " + Data.DATA1 + "=?" +
                        " AND ((" + DataColumns.MIMETYPE_ID + "=? AND " + Im.PROTOCOL + "=?");
                mSelectionArgs.add(mimeTypeIdEmail);
                mSelectionArgs.add(mimeTypeIdIm);
                mSelectionArgs.add(handle);
                mSelectionArgs.add(mimeTypeIdIm);
                mSelectionArgs.add(String.valueOf(protocol));
                if (customProtocol != null) {
                    mSb.append(" AND " + Im.CUSTOM_PROTOCOL + "=?");
                    mSelectionArgs.add(customProtocol);
                }
                mSb.append(") OR (" + DataColumns.MIMETYPE_ID + "=?))");
                mSelectionArgs.add(mimeTypeIdEmail);
            } else {
                mSb.append(DataColumns.MIMETYPE_ID + "=?" +
                        " AND " + Im.PROTOCOL + "=?" +
                        " AND " + Im.DATA + "=?");
                mSelectionArgs.add(mimeTypeIdIm);
                mSelectionArgs.add(String.valueOf(protocol));
                mSelectionArgs.add(handle);
                if (customProtocol != null) {
                    mSb.append(" AND " + Im.CUSTOM_PROTOCOL + "=?");
                    mSelectionArgs.add(customProtocol);
                }
            }

            final String dataID = inputValues.getAsString(StatusUpdates.DATA_ID);
            if (dataID != null) {
                mSb.append(" AND " + DataColumns.CONCRETE_ID + "=?");
                mSelectionArgs.add(dataID);
            }
        }

        Cursor cursor = null;
        try {
            cursor = db.query(DataContactsQuery.TABLE, DataContactsQuery.PROJECTION,
                    mSb.toString(), mSelectionArgs.toArray(EMPTY_STRING_ARRAY), null, null,
                    Clauses.CONTACT_VISIBLE + " DESC, " + Data.RAW_CONTACT_ID);
            if (cursor.moveToFirst()) {
                dataId = cursor.getLong(DataContactsQuery.DATA_ID);
                rawContactId = cursor.getLong(DataContactsQuery.RAW_CONTACT_ID);
                accountType = cursor.getString(DataContactsQuery.ACCOUNT_TYPE);
                accountName = cursor.getString(DataContactsQuery.ACCOUNT_NAME);
                contactId = cursor.getLong(DataContactsQuery.CONTACT_ID);
            } else {
                // No contact found, return a null URI.
                return -1;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        final String presence = inputValues.getAsString(StatusUpdates.PRESENCE);
        if (presence != null) {
            if (customProtocol == null) {
                // We cannot allow a null in the custom protocol field, because SQLite3 does not
                // properly enforce uniqueness of null values
                customProtocol = "";
            }

            final ContentValues values = new ContentValues();
            values.put(StatusUpdates.DATA_ID, dataId);
            values.put(PresenceColumns.RAW_CONTACT_ID, rawContactId);
            values.put(PresenceColumns.CONTACT_ID, contactId);
            values.put(StatusUpdates.PROTOCOL, protocol);
            values.put(StatusUpdates.CUSTOM_PROTOCOL, customProtocol);
            values.put(StatusUpdates.IM_HANDLE, handle);
            final String imAccount = inputValues.getAsString(StatusUpdates.IM_ACCOUNT);
            if (imAccount != null) {
                values.put(StatusUpdates.IM_ACCOUNT, imAccount);
            }
            values.put(StatusUpdates.PRESENCE, presence);
            values.put(StatusUpdates.CHAT_CAPABILITY,
                    inputValues.getAsString(StatusUpdates.CHAT_CAPABILITY));

            // Insert the presence update.
            db.replace(Tables.PRESENCE, null, values);
        }

        if (inputValues.containsKey(StatusUpdates.STATUS)) {
            String status = inputValues.getAsString(StatusUpdates.STATUS);
            String resPackage = inputValues.getAsString(StatusUpdates.STATUS_RES_PACKAGE);
            Resources resources = getContext().getResources();
            if (!TextUtils.isEmpty(resPackage)) {
                PackageManager pm = getContext().getPackageManager();
                try {
                    resources = pm.getResourcesForApplication(resPackage);
                } catch (NameNotFoundException e) {
                    Log.w(TAG, "Contact status update resource package not found: " + resPackage);
                }
            }
            Integer labelResourceId = inputValues.getAsInteger(StatusUpdates.STATUS_LABEL);

            if ((labelResourceId == null || labelResourceId == 0) && protocol != null) {
                labelResourceId = Im.getProtocolLabelResource(protocol);
            }
            String labelResource = getResourceName(resources, "string", labelResourceId);

            Integer iconResourceId = inputValues.getAsInteger(StatusUpdates.STATUS_ICON);
            // TODO compute the default icon based on the protocol

            String iconResource = getResourceName(resources, "drawable", iconResourceId);

            if (TextUtils.isEmpty(status)) {
                dbHelper.deleteStatusUpdate(dataId);
            } else {
                Long timestamp = inputValues.getAsLong(StatusUpdates.STATUS_TIMESTAMP);
                if (timestamp != null) {
                    dbHelper.replaceStatusUpdate(
                            dataId, timestamp, status, resPackage, iconResourceId, labelResourceId);
                } else {
                    dbHelper.insertStatusUpdate(
                            dataId, status, resPackage, iconResourceId, labelResourceId);
                }

                // For forward compatibility with the new stream item API, insert this status update
                // there as well.  If we already have a stream item from this source, update that
                // one instead of inserting a new one (since the semantics of the old status update
                // API is to only have a single record).
                if (rawContactId != -1 && !TextUtils.isEmpty(status)) {
                    ContentValues streamItemValues = new ContentValues();
                    streamItemValues.put(StreamItems.RAW_CONTACT_ID, rawContactId);
                    // Status updates are text only but stream items are HTML.
                    streamItemValues.put(StreamItems.TEXT, statusUpdateToHtml(status));
                    streamItemValues.put(StreamItems.COMMENTS, "");
                    streamItemValues.put(StreamItems.RES_PACKAGE, resPackage);
                    streamItemValues.put(StreamItems.RES_ICON, iconResource);
                    streamItemValues.put(StreamItems.RES_LABEL, labelResource);
                    streamItemValues.put(StreamItems.TIMESTAMP,
                            timestamp == null ? System.currentTimeMillis() : timestamp);

                    // Note: The following is basically a workaround for the fact that status
                    // updates didn't do any sort of account enforcement, while social stream item
                    // updates do.  We can't expect callers of the old API to start passing account
                    // information along, so we just populate the account params appropriately for
                    // the raw contact.  Data set is not relevant here, as we only check account
                    // name and type.
                    if (accountName != null && accountType != null) {
                        streamItemValues.put(RawContacts.ACCOUNT_NAME, accountName);
                        streamItemValues.put(RawContacts.ACCOUNT_TYPE, accountType);
                    }

                    // Check for an existing stream item from this source, and insert or update.
                    Uri streamUri = StreamItems.CONTENT_URI;
                    Cursor c = queryLocal(streamUri, new String[] {StreamItems._ID},
                            StreamItems.RAW_CONTACT_ID + "=?",
                            new String[] {String.valueOf(rawContactId)},
                            null, -1 /* directory ID */, null);
                    try {
                        if (c.getCount() > 0) {
                            c.moveToFirst();
                            updateInTransaction(ContentUris.withAppendedId(streamUri, c.getLong(0)),
                                    streamItemValues, null, null);
                        } else {
                            insertInTransaction(streamUri, streamItemValues);
                        }
                    } finally {
                        c.close();
                    }
                }
            }
        }

        if (contactId != -1) {
            mAggregator.get().updateLastStatusUpdateId(contactId);
        }

        return dataId;
    }

    /** Converts a status update to HTML. */
    private String statusUpdateToHtml(String status) {
        return TextUtils.htmlEncode(status);
    }

    private String getResourceName(Resources resources, String expectedType, Integer resourceId) {
        try {
            if (resourceId == null || resourceId == 0) {
                return null;
            }

            // Resource has an invalid type (e.g. a string as icon)? ignore
            final String resourceEntryName = resources.getResourceEntryName(resourceId);
            final String resourceTypeName = resources.getResourceTypeName(resourceId);
            if (!expectedType.equals(resourceTypeName)) {
                Log.w(TAG, "Resource " + resourceId + " (" + resourceEntryName + ") is of type " +
                        resourceTypeName + " but " + expectedType + " is required.");
                return null;
            }

            return resourceEntryName;
        } catch (NotFoundException e) {
            return null;
        }
    }

    @Override
    protected int deleteInTransaction(Uri uri, String selection, String[] selectionArgs) {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "deleteInTransaction: uri=" + uri +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs) +
                    " CPID=" + Binder.getCallingPid() +
                    " User=" + UserUtils.getCurrentUserHandle(getContext()));
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        /**
        * SPRD:
        * Original Android code:
        flushTransactionalChanges();
        * @{
        */
        try {
            flushTransactionalChanges();
        } catch (IllegalStateException e) {
            return 0;
        }
        /**
        * @}
        */
        final boolean callerIsSyncAdapter =
                readBooleanQueryParameter(uri, ContactsContract.CALLER_IS_SYNCADAPTER, false);
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case SYNCSTATE:
            case PROFILE_SYNCSTATE:
                return mDbHelper.get().getSyncState().delete(db, selection, selectionArgs);

            case SYNCSTATE_ID: {
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mDbHelper.get().getSyncState().delete(db, selectionWithId, selectionArgs);
            }

            case PROFILE_SYNCSTATE_ID: {
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mProfileHelper.getSyncState().delete(db, selectionWithId, selectionArgs);
            }

            case CONTACTS: {
                invalidateFastScrollingIndexCache();
                // TODO
                return 0;
            }

            case CONTACTS_ID: {
                invalidateFastScrollingIndexCache();
                long contactId = ContentUris.parseId(uri);
                return deleteContact(contactId, callerIsSyncAdapter);
            }

            case CONTACTS_LOOKUP: {
                invalidateFastScrollingIndexCache();
                final List<String> pathSegments = uri.getPathSegments();
                final int segmentCount = pathSegments.size();
                if (segmentCount < 3) {
                    throw new IllegalArgumentException(
                            mDbHelper.get().exceptionMessage("Missing a lookup key", uri));
                }
                final String lookupKey = pathSegments.get(2);
                final long contactId = lookupContactIdByLookupKey(db, lookupKey);
                return deleteContact(contactId, callerIsSyncAdapter);
            }

            case CONTACTS_LOOKUP_ID: {
                invalidateFastScrollingIndexCache();
                // lookup contact by ID and lookup key to see if they still match the actual record
                final List<String> pathSegments = uri.getPathSegments();
                final String lookupKey = pathSegments.get(2);
                SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                setTablesAndProjectionMapForContacts(lookupQb, null);
                long contactId = ContentUris.parseId(uri);
                String[] args;
                if (selectionArgs == null) {
                    args = new String[2];
                } else {
                    args = new String[selectionArgs.length + 2];
                    System.arraycopy(selectionArgs, 0, args, 2, selectionArgs.length);
                }
                args[0] = String.valueOf(contactId);
                args[1] = Uri.encode(lookupKey);
                lookupQb.appendWhere(Contacts._ID + "=? AND " + Contacts.LOOKUP_KEY + "=?");
                Cursor c = doQuery(db, lookupQb, null, selection, args, null, null, null, null,
                        null);
                try {
                    if (c.getCount() == 1) {
                        // Contact was unmodified so go ahead and delete it.
                        return deleteContact(contactId, callerIsSyncAdapter);
                    }

                    // The row was changed (e.g. the merging might have changed), we got multiple
                    // rows or the supplied selection filtered the record out.
                    return 0;

                } finally {
                    c.close();
                }
            }

            case CONTACTS_DELETE_USAGE: {
                return deleteDataUsage();
            }

            case RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS: {
                invalidateFastScrollingIndexCache();
                int numDeletes = 0;
                Cursor c = db.query(Views.RAW_CONTACTS,
                        new String[] {RawContacts._ID, RawContacts.CONTACT_ID},
                        appendAccountIdToSelection(
                                uri, selection), selectionArgs, null, null, null);
                try {
                    while (c.moveToNext()) {
                        final long rawContactId = c.getLong(0);
                        long contactId = c.getLong(1);
                        numDeletes += deleteRawContact(
                                rawContactId, contactId, callerIsSyncAdapter);
                    }
                } finally {
                    c.close();
                }
                return numDeletes;
            }

            case RAW_CONTACTS_ID:
            case PROFILE_RAW_CONTACTS_ID: {
                invalidateFastScrollingIndexCache();
                final long rawContactId = ContentUris.parseId(uri);
                return deleteRawContact(rawContactId, mDbHelper.get().getContactId(rawContactId),
                        callerIsSyncAdapter);
            }

            case DATA:
            case PROFILE_DATA: {
                invalidateFastScrollingIndexCache();
                mSyncToNetwork |= !callerIsSyncAdapter;
                return deleteData(appendAccountToSelection(
                        uri, selection), selectionArgs, callerIsSyncAdapter);
            }

            case DATA_ID:
            case PHONES_ID:
            case EMAILS_ID:
            case CALLABLES_ID:
            case POSTALS_ID:
            case PROFILE_DATA_ID: {
                invalidateFastScrollingIndexCache();
                long dataId = ContentUris.parseId(uri);
                mSyncToNetwork |= !callerIsSyncAdapter;
                mSelectionArgs1[0] = String.valueOf(dataId);
                return deleteData(Data._ID + "=?", mSelectionArgs1, callerIsSyncAdapter);
            }

            case GROUPS_ID: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                return deleteGroup(uri, ContentUris.parseId(uri), callerIsSyncAdapter);
            }

            case GROUPS: {
                int numDeletes = 0;
                Cursor c = db.query(Views.GROUPS, Projections.ID,
                        appendAccountIdToSelection(uri, selection), selectionArgs,
                        null, null, null);
                try {
                    while (c.moveToNext()) {
                        numDeletes += deleteGroup(uri, c.getLong(0), callerIsSyncAdapter);
                    }
                } finally {
                    c.close();
                }
                if (numDeletes > 0) {
                    mSyncToNetwork |= !callerIsSyncAdapter;
                }
                return numDeletes;
            }

            case SETTINGS: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                return deleteSettings(appendAccountToSelection(uri, selection), selectionArgs);
            }

            case STATUS_UPDATES:
            case PROFILE_STATUS_UPDATES: {
                return deleteStatusUpdates(selection, selectionArgs);
            }

            case STREAM_ITEMS: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                return deleteStreamItems(selection, selectionArgs);
            }

            case STREAM_ITEMS_ID: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                return deleteStreamItems(
                        StreamItems._ID + "=?", new String[] {uri.getLastPathSegment()});
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                String rawContactId = uri.getPathSegments().get(1);
                String streamItemId = uri.getLastPathSegment();
                return deleteStreamItems(
                        StreamItems.RAW_CONTACT_ID + "=? AND " + StreamItems._ID + "=?",
                        new String[] {rawContactId, streamItemId});
            }

            case STREAM_ITEMS_ID_PHOTOS: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                String streamItemId = uri.getPathSegments().get(1);
                String selectionWithId =
                        (StreamItemPhotos.STREAM_ITEM_ID + "=" + streamItemId + " ")
                                + (selection == null ? "" : " AND (" + selection + ")");
                return deleteStreamItemPhotos(selectionWithId, selectionArgs);
            }

            case STREAM_ITEMS_ID_PHOTOS_ID: {
                mSyncToNetwork |= !callerIsSyncAdapter;
                String streamItemId = uri.getPathSegments().get(1);
                String streamItemPhotoId = uri.getPathSegments().get(3);
                return deleteStreamItemPhotos(
                        StreamItemPhotosColumns.CONCRETE_ID + "=? AND "
                                + StreamItemPhotos.STREAM_ITEM_ID + "=?",
                        new String[] {streamItemPhotoId, streamItemId});
            }

            default: {
                mSyncToNetwork = true;
                return mLegacyApiSupport.delete(uri, selection, selectionArgs);
            }
        }
    }

    public int deleteGroup(Uri uri, long groupId, boolean callerIsSyncAdapter) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        mGroupIdCache.clear();
        final long groupMembershipMimetypeId = mDbHelper.get()
                .getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE);
        db.delete(Tables.DATA, DataColumns.MIMETYPE_ID + "="
                + groupMembershipMimetypeId + " AND " + GroupMembership.GROUP_ROW_ID + "="
                + groupId, null);

        try {
            if (callerIsSyncAdapter) {
                /**
                 * SPRD:modify for group
                 * @{
                 */
                db.delete(Tables.DATA, DataColumns.MIMETYPE_ID + "="
                        + groupMembershipMimetypeId + " AND " + GroupMembership.GROUP_ROW_ID + "="
                        + groupId, null);
                /**
                 * @}
                 */
                return db.delete(Tables.GROUPS, Groups._ID + "=" + groupId, null);
            }

            /**
             * SPRD: delete sim group, bug422980
             *
             * @{
             */
            Cursor c = queryLocal(Data.CONTENT_URI, DataRowHandler.DataDeleteQuery.COLUMNS,
                    Data.MIMETYPE + "=\"" + GroupMembership.CONTENT_ITEM_TYPE + "\""
                            + " AND " + GroupMembership.GROUP_ROW_ID + "="
                            + groupId, null, null, -1 /* directory ID */, null);
            DataRowHandler rowHandler = getDataRowHandler(GroupMembership.CONTENT_ITEM_TYPE);
            try {
                if (c.moveToFirst()) {
                    do {
                        long rawContactId = c
                                .getLong(DataRowHandler.DataDeleteQuery.RAW_CONTACT_ID);
                        mTransactionContext.get().markRawContactDirtyAndChanged(rawContactId,
                                false);
                        rowHandler.delete(db, mTransactionContext.get(), c);
                    } while (c.moveToNext());
                }
            } finally {
                c.close();
            }
            ContactProxyManager contactProxyManager = ContactProxyManager.getInstance(getContext());
            SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(contactProxyManager, getContext());
            if (simProxyUtil.isMyContactGroup(groupId)) {
                contactProxyManager.removeGroup(groupId);
            }
            /**
             * @}
             */

            final ContentValues values = new ContentValues();
            values.put(Groups.DELETED, 1);
            values.put(Groups.DIRTY, 1);
            return db.update(Tables.GROUPS, values, Groups._ID + "=" + groupId, null);
        } finally {
            mVisibleTouched = true;
        }
    }

    private int deleteSettings(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final int count = db.delete(Tables.SETTINGS, selection, selectionArgs);
        mVisibleTouched = true;
        return count;
    }

    private int deleteContact(long contactId, boolean callerIsSyncAdapter) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        mSelectionArgs1[0] = Long.toString(contactId);
        Cursor c = db.query(Tables.RAW_CONTACTS, new String[] {RawContacts._ID},
                RawContacts.CONTACT_ID + "=? AND " + RawContacts.RAW_CONTACT_IS_READ_ONLY + "=0", mSelectionArgs1,
                null, null, null);
        try {
            while (c.moveToNext()) {
                long rawContactId = c.getLong(0);
                markRawContactAsDeleted(db, rawContactId, callerIsSyncAdapter);
            }
        } finally {
            c.close();
        }

        mProviderStatusUpdateNeeded = true;

        int result = ContactsTableUtil.deleteContact(db, contactId);
        scheduleBackgroundTask(BACKGROUND_TASK_CLEAN_DELETE_LOG);
        return result;
    }

    public int deleteRawContact(long rawContactId, long contactId, boolean callerIsSyncAdapter) {
        mAggregator.get().invalidateAggregationExceptionCache();
        mProviderStatusUpdateNeeded = true;

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // Find and delete stream items associated with the raw contact.
        Cursor c = db.query(Tables.STREAM_ITEMS,
                new String[] {StreamItems._ID},
                StreamItems.RAW_CONTACT_ID + "=?", new String[] {String.valueOf(rawContactId)},
                null, null, null);
        try {
            while (c.moveToNext()) {
                deleteStreamItem(db, c.getLong(0));
            }
        } finally {
            c.close();
        }

        final boolean contactIsSingleton =
                ContactsTableUtil.deleteContactIfSingleton(db, rawContactId) == 1;
        final int count;

        if (callerIsSyncAdapter || rawContactIsLocal(rawContactId)) {
            // When a raw contact is deleted, a SQLite trigger deletes the parent contact.
            // TODO: all contact deletes was consolidated into ContactTableUtil but this one can't
            // because it's in a trigger.  Consider removing trigger and replacing with java code.
            // This has to happen before the raw contact is deleted since it relies on the number
            // of raw contacts.
            db.delete(Tables.PRESENCE, PresenceColumns.RAW_CONTACT_ID + "=" + rawContactId, null);
            count = db.delete(Tables.RAW_CONTACTS, RawContacts._ID + "=" + rawContactId, null);
            mTransactionContext.get().markRawContactChangedOrDeletedOrInserted(rawContactId);
        } else {
            count = markRawContactAsDeleted(db, rawContactId, callerIsSyncAdapter);
        }
        if (!contactIsSingleton) {
            mAggregator.get().updateAggregateData(mTransactionContext.get(), contactId);
        }
        return count;
    }

    /**
     * Returns whether the given raw contact ID is local (i.e. has no account associated with it).
     */
    private boolean rawContactIsLocal(long rawContactId) {
        final SQLiteDatabase db = mDbHelper.get().getReadableDatabase();
        Cursor c = db.query(Tables.RAW_CONTACTS, Projections.LITERAL_ONE,
                RawContactsColumns.CONCRETE_ID + "=? AND " +
                RawContactsColumns.ACCOUNT_ID + "=" + Clauses.LOCAL_ACCOUNT_ID,
                new String[] {String.valueOf(rawContactId)}, null, null, null);
        try {
            return c.getCount() > 0;
        } finally {
            c.close();
        }
    }

    private int deleteStatusUpdates(String selection, String[] selectionArgs) {
      // delete from both tables: presence and status_updates
      // TODO should account type/name be appended to the where clause?
      if (VERBOSE_LOGGING) {
          Log.v(TAG, "deleting data from status_updates for " + selection);
      }
      final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
      db.delete(Tables.STATUS_UPDATES, getWhereClauseForStatusUpdatesTable(selection),
              selectionArgs);

      return db.delete(Tables.PRESENCE, selection, selectionArgs);
    }

    private int deleteStreamItems(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        int count = 0;
        final Cursor c = db.query(
                Views.STREAM_ITEMS, Projections.ID, selection, selectionArgs, null, null, null);
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                count += deleteStreamItem(db, c.getLong(0));
            }
        } finally {
            c.close();
        }
        return count;
    }

    private int deleteStreamItem(SQLiteDatabase db, long streamItemId) {
        deleteStreamItemPhotos(streamItemId);
        return db.delete(Tables.STREAM_ITEMS, StreamItems._ID + "=?",
                new String[] {String.valueOf(streamItemId)});
    }

    private int deleteStreamItemPhotos(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        return db.delete(Tables.STREAM_ITEM_PHOTOS, selection, selectionArgs);
    }

    private int deleteStreamItemPhotos(long streamItemId) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        // Note that this does not enforce the modifying account.
        return db.delete(Tables.STREAM_ITEM_PHOTOS,
                StreamItemPhotos.STREAM_ITEM_ID + "=?",
                new String[] {String.valueOf(streamItemId)});
    }

    private int markRawContactAsDeleted(
            SQLiteDatabase db, long rawContactId, boolean callerIsSyncAdapter) {

        mSyncToNetwork = true;

        final ContentValues values = new ContentValues();
        values.put(RawContacts.DELETED, 1);
        values.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);
        values.put(RawContactsColumns.AGGREGATION_NEEDED, 1);
        values.putNull(RawContacts.CONTACT_ID);
        values.put(RawContacts.DIRTY, 1);

        /**
         * SPRD: delete sim-card contact, bug421665
         *
         * @{
         */
        if (!ContactsContract.isProfileId(rawContactId)) {
            ContactProxyManager contactProxyManager = ContactProxyManager.getInstance(getContext());
            SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(contactProxyManager,
                    getContext());
            if (simProxyUtil.isMyContact(rawContactId)) {
                contactProxyManager.remove(rawContactId);
            }
        }
        /**
         * @}
         */

        return updateRawContact(db, rawContactId, values, callerIsSyncAdapter);
    }

    private int deleteDataUsage() {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        db.execSQL("UPDATE " + Tables.RAW_CONTACTS + " SET " +
                Contacts.TIMES_CONTACTED + "=0," +
                Contacts.LAST_TIME_CONTACTED + "=NULL");

        db.execSQL("UPDATE " + Tables.CONTACTS + " SET " +
                Contacts.TIMES_CONTACTED + "=0," +
                Contacts.LAST_TIME_CONTACTED + "=NULL");

        db.delete(Tables.DATA_USAGE_STAT, null, null);
        return 1;
    }

    @Override
    protected int updateInTransaction(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        if (VERBOSE_LOGGING) {
            Log.v(TAG, "updateInTransaction: uri=" + uri +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs) +
                    "  values=[" + values + "] CPID=" + Binder.getCallingPid() +
                    " User=" + UserUtils.getCurrentUserHandle(getContext()));
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        int count = 0;

        final int match = sUriMatcher.match(uri);
        if (match == SYNCSTATE_ID && selection == null) {
            long rowId = ContentUris.parseId(uri);
            Object data = values.get(ContactsContract.SyncState.DATA);
            mTransactionContext.get().syncStateUpdated(rowId, data);
            return 1;
        }
        /**
         * SPRD:
         *
         * Original Android code:
        flushTransactionalChanges();
         *
         * @{
         */
        try {
            flushTransactionalChanges();
        } catch (IllegalArgumentException e) {
            return 0;
        }
        /**
        * @}
        */
        final boolean callerIsSyncAdapter =
                readBooleanQueryParameter(uri, ContactsContract.CALLER_IS_SYNCADAPTER, false);
        switch(match) {
            case SYNCSTATE:
            case PROFILE_SYNCSTATE:
                return mDbHelper.get().getSyncState().update(db, values,
                        appendAccountToSelection(uri, selection), selectionArgs);

            case SYNCSTATE_ID: {
                selection = appendAccountToSelection(uri, selection);
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mDbHelper.get().getSyncState().update(db, values,
                        selectionWithId, selectionArgs);
            }

            case PROFILE_SYNCSTATE_ID: {
                selection = appendAccountToSelection(uri, selection);
                String selectionWithId =
                        (SyncStateContract.Columns._ID + "=" + ContentUris.parseId(uri) + " ")
                        + (selection == null ? "" : " AND (" + selection + ")");
                return mProfileHelper.getSyncState().update(db, values,
                        selectionWithId, selectionArgs);
            }

            case CONTACTS:
            case PROFILE: {
                invalidateFastScrollingIndexCache();
                count = updateContactOptions(values, selection, selectionArgs, callerIsSyncAdapter);
                break;
            }

            case CONTACTS_ID: {
                invalidateFastScrollingIndexCache();
                count = updateContactOptions(db, ContentUris.parseId(uri), values,
                        callerIsSyncAdapter);
                break;
            }

            case CONTACTS_LOOKUP:
            case CONTACTS_LOOKUP_ID: {
                invalidateFastScrollingIndexCache();
                final List<String> pathSegments = uri.getPathSegments();
                final int segmentCount = pathSegments.size();
                if (segmentCount < 3) {
                    throw new IllegalArgumentException(
                            mDbHelper.get().exceptionMessage("Missing a lookup key", uri));
                }
                final String lookupKey = pathSegments.get(2);
                final long contactId = lookupContactIdByLookupKey(db, lookupKey);
                count = updateContactOptions(db, contactId, values, callerIsSyncAdapter);
                break;
            }

            case RAW_CONTACTS_ID_DATA:
            case PROFILE_RAW_CONTACTS_ID_DATA: {
                invalidateFastScrollingIndexCache();
                int segment = match == RAW_CONTACTS_ID_DATA ? 1 : 2;
                final String rawContactId = uri.getPathSegments().get(segment);
                String selectionWithId = (Data.RAW_CONTACT_ID + "=" + rawContactId + " ")
                    + (selection == null ? "" : " AND " + selection);

                count = updateData(uri, values, selectionWithId, selectionArgs, callerIsSyncAdapter);
                break;
            }

            case DATA:
            case PROFILE_DATA: {
                invalidateFastScrollingIndexCache();
                count = updateData(uri, values, appendAccountToSelection(uri, selection),
                        selectionArgs, callerIsSyncAdapter);
                if (count > 0) {
                    mSyncToNetwork |= !callerIsSyncAdapter;
                }
                break;
            }

            case DATA_ID:
            case PHONES_ID:
            case EMAILS_ID:
            case CALLABLES_ID:
            case POSTALS_ID: {
                invalidateFastScrollingIndexCache();
                count = updateData(uri, values, selection, selectionArgs, callerIsSyncAdapter);
                if (count > 0) {
                    mSyncToNetwork |= !callerIsSyncAdapter;
                }
                break;
            }
           /**
            * SPRD:Bug 474798 SprdAppBackup for Contacts.
            *
            * @{
            */
            case RAW_CONTACTS_UNIQUE:
                invalidateFastScrollingIndexCache();
                count = uniqueRawContacts();
                break;
            /**
            * @}
            */
            /**
             * SPRD: bug474742, for cleaning up contacts feature
             *
             * @{
             */
            case RAW_CONTACTS_UNIQUE_NOSIM:
                invalidateFastScrollingIndexCache();
                count = uniqueRawContactsWithoutSim();
                break;
            case RAW_CONTACTS_MERGE:
                count = mergeRawContacts(db, values);
                break;
            case RAW_CONTACTS_JOIN:
                count = joinRawContacts(db, values);
                break;
            /**
             * @}
             */

            case RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS: {
                invalidateFastScrollingIndexCache();
                selection = appendAccountIdToSelection(uri, selection);
                count = updateRawContacts(values, selection, selectionArgs, callerIsSyncAdapter);
                break;
            }

            case RAW_CONTACTS_ID: {
                invalidateFastScrollingIndexCache();
                long rawContactId = ContentUris.parseId(uri);
                if (selection != null) {
                    selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                    count = updateRawContacts(values, RawContacts._ID + "=?"
                                    + " AND(" + selection + ")", selectionArgs,
                            callerIsSyncAdapter);
                } else {
                    mSelectionArgs1[0] = String.valueOf(rawContactId);
                    count = updateRawContacts(values, RawContacts._ID + "=?", mSelectionArgs1,
                            callerIsSyncAdapter);
                }
                break;
            }

            case GROUPS: {
               count = updateGroups(values, appendAccountIdToSelection(uri, selection),
                        selectionArgs, callerIsSyncAdapter);
                if (count > 0) {
                    mSyncToNetwork |= !callerIsSyncAdapter;
                }
                break;
            }

            case GROUPS_ID: {
                long groupId = ContentUris.parseId(uri);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(groupId));
                String selectionWithId = Groups._ID + "=? "
                        + (selection == null ? "" : " AND " + selection);
                count = updateGroups(values, selectionWithId, selectionArgs, callerIsSyncAdapter);
                if (count > 0) {
                    mSyncToNetwork |= !callerIsSyncAdapter;
                }
                break;
            }

            case AGGREGATION_EXCEPTIONS: {
                count = updateAggregationException(db, values);
                invalidateFastScrollingIndexCache();
                break;
            }

            case SETTINGS: {
                count = updateSettings(
                        values, appendAccountToSelection(uri, selection), selectionArgs);
                mSyncToNetwork |= !callerIsSyncAdapter;
                break;
            }

            case STATUS_UPDATES:
            case PROFILE_STATUS_UPDATES: {
                count = updateStatusUpdate(values, selection, selectionArgs);
                break;
            }

            case STREAM_ITEMS: {
                count = updateStreamItems(values, selection, selectionArgs);
                break;
            }

            case STREAM_ITEMS_ID: {
                count = updateStreamItems(values, StreamItems._ID + "=?",
                        new String[] {uri.getLastPathSegment()});
                break;
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
                String rawContactId = uri.getPathSegments().get(1);
                String streamItemId = uri.getLastPathSegment();
                count = updateStreamItems(values,
                        StreamItems.RAW_CONTACT_ID + "=? AND " + StreamItems._ID + "=?",
                        new String[] {rawContactId, streamItemId});
                break;
            }

            case STREAM_ITEMS_PHOTOS: {
                count = updateStreamItemPhotos(values, selection, selectionArgs);
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS: {
                String streamItemId = uri.getPathSegments().get(1);
                count = updateStreamItemPhotos(values,
                        StreamItemPhotos.STREAM_ITEM_ID + "=?", new String[] {streamItemId});
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS_ID: {
                String streamItemId = uri.getPathSegments().get(1);
                String streamItemPhotoId = uri.getPathSegments().get(3);
                count = updateStreamItemPhotos(values,
                        StreamItemPhotosColumns.CONCRETE_ID + "=? AND " +
                                StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=?",
                        new String[] {streamItemPhotoId, streamItemId});
                break;
            }

            case DIRECTORIES: {
                mContactDirectoryManager.scanPackagesByUid(Binder.getCallingUid());
                count = 1;
                break;
            }

            case DATA_USAGE_FEEDBACK_ID: {
                count = handleDataUsageFeedback(uri) ? 1 : 0;
                break;
            }
            /**
             * SPRD: add for update sim contacts, bug421244
             *
             * @{
             */
            case PROVIDER_STATUS: {
                Integer newStatus = values.getAsInteger(ProviderStatus.STATUS);
                setProviderStatus(newStatus);
                count = 1;
                break;
            }
            /**
             * @}
             */

            default: {
                mSyncToNetwork = true;
                return mLegacyApiSupport.update(uri, values, selection, selectionArgs);
            }
        }

        return count;
    }

    private int updateStatusUpdate(ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        // update status_updates table, if status is provided
        // TODO should account type/name be appended to the where clause?
        int updateCount = 0;
        ContentValues settableValues = getSettableColumnsForStatusUpdatesTable(values);
        if (settableValues.size() > 0) {
          updateCount = db.update(Tables.STATUS_UPDATES,
                    settableValues,
                    getWhereClauseForStatusUpdatesTable(selection),
                    selectionArgs);
        }

        // now update the Presence table
        settableValues = getSettableColumnsForPresenceTable(values);
        if (settableValues.size() > 0) {
            updateCount = db.update(Tables.PRESENCE, settableValues, selection, selectionArgs);
        }
        // TODO updateCount is not entirely a valid count of updated rows because 2 tables could
        // potentially get updated in this method.
        return updateCount;
    }

    private int updateStreamItems(ContentValues values, String selection, String[] selectionArgs) {
        // Stream items can't be moved to a new raw contact.
        values.remove(StreamItems.RAW_CONTACT_ID);

        // Don't attempt to update accounts params - they don't exist in the stream items table.
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // If there's been no exception, the update should be fine.
        return db.update(Tables.STREAM_ITEMS, values, selection, selectionArgs);
    }

    private int updateStreamItemPhotos(
            ContentValues values, String selection, String[] selectionArgs) {

        // Stream item photos can't be moved to a new stream item.
        values.remove(StreamItemPhotos.STREAM_ITEM_ID);

        // Don't attempt to update accounts params - they don't exist in the stream item
        // photos table.
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        // Process the photo (since we're updating, it's valid for the photo to not be present).
        if (processStreamItemPhoto(values, true)) {
            // If there's been no exception, the update should be fine.
            return db.update(Tables.STREAM_ITEM_PHOTOS, values, selection, selectionArgs);
        }
        return 0;
    }

    /**
     * Build a where clause to select the rows to be updated in status_updates table.
     */
    private String getWhereClauseForStatusUpdatesTable(String selection) {
        mSb.setLength(0);
        mSb.append(WHERE_CLAUSE_FOR_STATUS_UPDATES_TABLE);
        mSb.append(selection);
        mSb.append(")");
        return mSb.toString();
    }

    private ContentValues getSettableColumnsForStatusUpdatesTable(ContentValues inputValues) {
        final ContentValues values = new ContentValues();

        ContactsDatabaseHelper.copyStringValue(
                values, StatusUpdates.STATUS,
                inputValues, StatusUpdates.STATUS);
        ContactsDatabaseHelper.copyStringValue(
                values, StatusUpdates.STATUS_TIMESTAMP,
                inputValues, StatusUpdates.STATUS_TIMESTAMP);
        ContactsDatabaseHelper.copyStringValue(
                values, StatusUpdates.STATUS_RES_PACKAGE,
                inputValues, StatusUpdates.STATUS_RES_PACKAGE);
        ContactsDatabaseHelper.copyStringValue(
                values, StatusUpdates.STATUS_LABEL,
                inputValues, StatusUpdates.STATUS_LABEL);
        ContactsDatabaseHelper.copyStringValue(
                values, StatusUpdates.STATUS_ICON,
                inputValues, StatusUpdates.STATUS_ICON);

        return values;
    }

    private ContentValues getSettableColumnsForPresenceTable(ContentValues inputValues) {
        final ContentValues values = new ContentValues();

        ContactsDatabaseHelper.copyStringValue(
              values, StatusUpdates.PRESENCE, inputValues, StatusUpdates.PRESENCE);
        ContactsDatabaseHelper.copyStringValue(
              values, StatusUpdates.CHAT_CAPABILITY, inputValues, StatusUpdates.CHAT_CAPABILITY);

        return values;
    }

    private interface GroupAccountQuery {
        String TABLE = Views.GROUPS;
        String[] COLUMNS = new String[] {
                Groups._ID,
                Groups.ACCOUNT_TYPE,
                Groups.ACCOUNT_NAME,
                Groups.DATA_SET,
        };
        int ID = 0;
        int ACCOUNT_TYPE = 1;
        int ACCOUNT_NAME = 2;
        int DATA_SET = 3;
    }

    private int updateGroups(ContentValues originalValues, String selectionWithId,
            String[] selectionArgs, boolean callerIsSyncAdapter) {
        mGroupIdCache.clear();

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final ContactsDatabaseHelper dbHelper = mDbHelper.get();

        final ContentValues updatedValues = new ContentValues();
        updatedValues.putAll(originalValues);

        if (!callerIsSyncAdapter && !updatedValues.containsKey(Groups.DIRTY)) {
            updatedValues.put(Groups.DIRTY, 1);
        }
        if (updatedValues.containsKey(Groups.GROUP_VISIBLE)) {
            mVisibleTouched = true;
        }

        /**
        * SPRD: update sim group, bug422980
        *
        * @{
        */
        Cursor cursor = db.query(Tables.GROUPS,
                new String[] { Groups._ID }, selectionWithId, selectionArgs, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long groupRowId = cursor.getLong(0);
                ContactProxyManager contactProxyManager = ContactProxyManager.getInstance(getContext());
                SimContactProxyUtil simProxyUtil = SimContactProxyUtil.getInstance(contactProxyManager, getContext());
                if (simProxyUtil.isMyContactGroup(groupRowId)) {
                    contactProxyManager.updateGroup(groupRowId, updatedValues);
                }
            }
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        /**
         * @}
         */

        // Prepare for account change
        final boolean isAccountNameChanging = updatedValues.containsKey(Groups.ACCOUNT_NAME);
        final boolean isAccountTypeChanging = updatedValues.containsKey(Groups.ACCOUNT_TYPE);
        final boolean isDataSetChanging = updatedValues.containsKey(Groups.DATA_SET);
        final boolean isAccountChanging =
                isAccountNameChanging || isAccountTypeChanging || isDataSetChanging;
        final String updatedAccountName = updatedValues.getAsString(Groups.ACCOUNT_NAME);
        final String updatedAccountType = updatedValues.getAsString(Groups.ACCOUNT_TYPE);
        final String updatedDataSet = updatedValues.getAsString(Groups.DATA_SET);

        updatedValues.remove(Groups.ACCOUNT_NAME);
        updatedValues.remove(Groups.ACCOUNT_TYPE);
        updatedValues.remove(Groups.DATA_SET);

        // We later call requestSync() on all affected accounts.
        final Set<Account> affectedAccounts = Sets.newHashSet();

        // Look for all affected rows, and change them row by row.
        final Cursor c = db.query(GroupAccountQuery.TABLE, GroupAccountQuery.COLUMNS,
                selectionWithId, selectionArgs, null, null, null);
        int returnCount = 0;
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                final long groupId = c.getLong(GroupAccountQuery.ID);

                mSelectionArgs1[0] = Long.toString(groupId);

                final String accountName = isAccountNameChanging
                        ? updatedAccountName : c.getString(GroupAccountQuery.ACCOUNT_NAME);
                final String accountType = isAccountTypeChanging
                        ? updatedAccountType : c.getString(GroupAccountQuery.ACCOUNT_TYPE);
                final String dataSet = isDataSetChanging
                        ? updatedDataSet : c.getString(GroupAccountQuery.DATA_SET);

                if (isAccountChanging) {
                    final long accountId = dbHelper.getOrCreateAccountIdInTransaction(
                            AccountWithDataSet.get(accountName, accountType, dataSet));
                    updatedValues.put(GroupsColumns.ACCOUNT_ID, accountId);
                }

                // Finally do the actual update.
                final int count = db.update(Tables.GROUPS, updatedValues,
                        GroupsColumns.CONCRETE_ID + "=?", mSelectionArgs1);

                if ((count > 0)
                        && !TextUtils.isEmpty(accountName)
                        && !TextUtils.isEmpty(accountType)) {
                    affectedAccounts.add(new Account(accountName, accountType));
                }

                returnCount += count;
            }
        } finally {
            c.close();
        }
        /*
         * SPRD:
         *  Bug264703 After modifying the contact group name, enter the group name of
         *         the modified search within a group of contacts inside the modified.
         *
         *
         * @{
         */
        Cursor cursor2 = db.query(
                Tables.DATA,
                new String[] {
                    Data.RAW_CONTACT_ID
                },
                DataColumns.MIMETYPE_ID + " = " + String.valueOf(mDbHelper.get().getMimeTypeId(
                        GroupMembership.CONTENT_ITEM_TYPE)) + " AND "
                        + GroupMembership.GROUP_ROW_ID + "=?",
                selectionArgs,
                null, null, null);
        try {
            if (cursor2 != null && cursor2.moveToFirst()) {
                do {
                    mTransactionContext.get().invalidateSearchIndexForRawContact(
                            cursor2.getLong(0));
                } while (cursor2.moveToNext());
            }
        } finally {
            cursor2.close();
        }
        /*
        * @}
        */

        // TODO: This will not work for groups that have a data set specified, since the content
        // resolver will not be able to request a sync for the right source (unless it is updated
        // to key off account with data set).
        // i.e. requestSync only takes Account, not AccountWithDataSet.
        if (flagIsSet(updatedValues, Groups.SHOULD_SYNC)) {
            for (Account account : affectedAccounts) {
                ContentResolver.requestSync(account, ContactsContract.AUTHORITY, new Bundle());
            }
        }
        return returnCount;
    }

    private int updateSettings(ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final int count = db.update(Tables.SETTINGS, values, selection, selectionArgs);
        if (values.containsKey(Settings.UNGROUPED_VISIBLE)) {
            mVisibleTouched = true;
        }
        return count;
    }

    private int updateRawContacts(ContentValues values, String selection, String[] selectionArgs,
            boolean callerIsSyncAdapter) {
        if (values.containsKey(RawContacts.CONTACT_ID)) {
            throw new IllegalArgumentException(RawContacts.CONTACT_ID + " should not be included " +
                    "in content values. Contact IDs are assigned automatically");
        }

        if (!callerIsSyncAdapter) {
            selection = DatabaseUtils.concatenateWhere(selection,
                    RawContacts.RAW_CONTACT_IS_READ_ONLY + "=0");
        }

        int count = 0;
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        Cursor cursor = db.query(Views.RAW_CONTACTS,
                Projections.ID, selection,
                selectionArgs, null, null, null);
        try {
            while (cursor.moveToNext()) {
                long rawContactId = cursor.getLong(0);
                updateRawContact(db, rawContactId, values, callerIsSyncAdapter);
                count++;
            }
        } finally {
            cursor.close();
        }

        return count;
    }

    private int updateRawContact(SQLiteDatabase db, long rawContactId, ContentValues values,
            boolean callerIsSyncAdapter) {
        final String selection = RawContactsColumns.CONCRETE_ID + " = ?";
        mSelectionArgs1[0] = Long.toString(rawContactId);

        final ContactsDatabaseHelper dbHelper = mDbHelper.get();

        final boolean requestUndoDelete = flagIsClear(values, RawContacts.DELETED);

        final boolean isAccountNameChanging = values.containsKey(RawContacts.ACCOUNT_NAME);
        final boolean isAccountTypeChanging = values.containsKey(RawContacts.ACCOUNT_TYPE);
        final boolean isDataSetChanging = values.containsKey(RawContacts.DATA_SET);
        final boolean isAccountChanging =
                isAccountNameChanging || isAccountTypeChanging || isDataSetChanging;

        int previousDeleted = 0;
        long accountId = 0;
        String oldAccountType = null;
        String oldAccountName = null;
        String oldDataSet = null;

        if (requestUndoDelete || isAccountChanging) {
            Cursor cursor = db.query(RawContactsQuery.TABLE, RawContactsQuery.COLUMNS,
                    selection, mSelectionArgs1, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    previousDeleted = cursor.getInt(RawContactsQuery.DELETED);
                    accountId = cursor.getLong(RawContactsQuery.ACCOUNT_ID);
                    oldAccountType = cursor.getString(RawContactsQuery.ACCOUNT_TYPE);
                    oldAccountName = cursor.getString(RawContactsQuery.ACCOUNT_NAME);
                    oldDataSet = cursor.getString(RawContactsQuery.DATA_SET);
                }
            } finally {
                cursor.close();
            }
            if (isAccountChanging) {
                // We can't change the original ContentValues, as it'll be re-used over all
                // updateRawContact invocations in a transaction, so we need to create a new one.
                final ContentValues originalValues = values;
                values = new ContentValues();
                values.clear();
                values.putAll(originalValues);

                final AccountWithDataSet newAccountWithDataSet = AccountWithDataSet.get(
                        isAccountNameChanging
                            ? values.getAsString(RawContacts.ACCOUNT_NAME) : oldAccountName,
                        isAccountTypeChanging
                            ? values.getAsString(RawContacts.ACCOUNT_TYPE) : oldAccountType,
                        isDataSetChanging
                            ? values.getAsString(RawContacts.DATA_SET) : oldDataSet
                        );
                accountId = dbHelper.getOrCreateAccountIdInTransaction(newAccountWithDataSet);

                values.put(RawContactsColumns.ACCOUNT_ID, accountId);

                values.remove(RawContacts.ACCOUNT_NAME);
                values.remove(RawContacts.ACCOUNT_TYPE);
                values.remove(RawContacts.DATA_SET);
            }
        }
        if (requestUndoDelete) {
            values.put(ContactsContract.RawContacts.AGGREGATION_MODE,
                    ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT);
        }

        int count = db.update(Tables.RAW_CONTACTS, values, selection, mSelectionArgs1);
        if (count != 0) {
            final AbstractContactAggregator aggregator = mAggregator.get();
            int aggregationMode = getIntValue(
                    values, RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT);

            // As per ContactsContract documentation, changing aggregation mode
            // to DEFAULT should not trigger aggregation
            if (aggregationMode != RawContacts.AGGREGATION_MODE_DEFAULT) {
                aggregator.markForAggregation(rawContactId, aggregationMode, false);
            }
            if (flagExists(values, RawContacts.STARRED)) {
                if (!callerIsSyncAdapter) {
                    updateFavoritesMembership(rawContactId, flagIsSet(values, RawContacts.STARRED));
                }
                aggregator.updateStarred(rawContactId);
                aggregator.updatePinned(rawContactId);
            } else {
                // if this raw contact is being associated with an account, then update the
                // favorites group membership based on whether or not this contact is starred.
                // If it is starred, add a group membership, if one doesn't already exist
                // otherwise delete any matching group memberships.
                if (!callerIsSyncAdapter && isAccountChanging) {
                    boolean starred = 0 != DatabaseUtils.longForQuery(db,
                            SELECTION_STARRED_FROM_RAW_CONTACTS,
                            new String[] {Long.toString(rawContactId)});
                    updateFavoritesMembership(rawContactId, starred);
                }
            }

            // if this raw contact is being associated with an account, then add a
            // group membership to the group marked as AutoAdd, if any.
            if (!callerIsSyncAdapter && isAccountChanging) {
                addAutoAddMembership(rawContactId);
            }

            if (values.containsKey(RawContacts.SOURCE_ID)) {
                aggregator.updateLookupKeyForRawContact(db, rawContactId);
            }
            if (requestUndoDelete && previousDeleted == 1) {
                // Note before the accounts refactoring, we used to use the *old* account here,
                // which doesn't make sense, so now we pass the *new* account.
                // (In practice it doesn't matter because there's probably no apps that undo-delete
                // and change accounts at the same time.)
                mTransactionContext.get().rawContactInserted(rawContactId, accountId);
            }
            mTransactionContext.get().markRawContactChangedOrDeletedOrInserted(rawContactId);
        }
        return count;
    }

    private int updateData(Uri uri, ContentValues inputValues, String selection,
            String[] selectionArgs, boolean callerIsSyncAdapter) {

        final ContentValues values = new ContentValues(inputValues);
        values.remove(Data._ID);
        values.remove(Data.RAW_CONTACT_ID);
        values.remove(Data.MIMETYPE);

        String packageName = inputValues.getAsString(Data.RES_PACKAGE);
        if (packageName != null) {
            values.remove(Data.RES_PACKAGE);
            values.put(DataColumns.PACKAGE_ID, mDbHelper.get().getPackageId(packageName));
        }

        if (!callerIsSyncAdapter) {
            selection = DatabaseUtils.concatenateWhere(selection, Data.IS_READ_ONLY + "=0");
        }

        int count = 0;

        // Note that the query will return data according to the access restrictions,
        // so we don't need to worry about updating data we don't have permission to read.
        Cursor c = queryLocal(uri,
                DataRowHandler.DataUpdateQuery.COLUMNS,
                selection, selectionArgs, null, -1 /* directory ID */, null);
        try {
            while(c.moveToNext()) {
                count += updateData(values, c, callerIsSyncAdapter);
            }
        } finally {
            c.close();
        }

        return count;
    }

    private void maybeTrimLongPhoneNumber(ContentValues values) {
        final String data1 = values.getAsString(Data.DATA1);
        if (data1 != null && data1.length() > PHONE_NUMBER_LENGTH_LIMIT) {
            values.put(Data.DATA1, data1.substring(0, PHONE_NUMBER_LENGTH_LIMIT));
        }
    }

    private int updateData(ContentValues values, Cursor c, boolean callerIsSyncAdapter) {
        if (values.size() == 0) {
            return 0;
        }

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        final String mimeType = c.getString(DataRowHandler.DataUpdateQuery.MIMETYPE);
        if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            maybeTrimLongPhoneNumber(values);
        }

        DataRowHandler rowHandler = getDataRowHandler(mimeType);
        boolean updated =
                rowHandler.update(db, mTransactionContext.get(), values, c,
                        callerIsSyncAdapter);
        if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
            scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
        }
        return updated ? 1 : 0;
    }

    private int updateContactOptions(ContentValues values, String selection,
            String[] selectionArgs, boolean callerIsSyncAdapter) {
        int count = 0;
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        Cursor cursor = db.query(Views.CONTACTS,
                new String[] { Contacts._ID }, selection, selectionArgs, null, null, null);
         * @{
         */
        String contactsView = mDbHelper.get().getContactsView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        Cursor cursor = db.query(contactsView,
                new String[] { Contacts._ID }, selection, selectionArgs, null, null, null);
        /**
         * @}
         */
        try {
            while (cursor.moveToNext()) {
                long contactId = cursor.getLong(0);

                updateContactOptions(db, contactId, values, callerIsSyncAdapter);
                count++;
            }
        } finally {
            cursor.close();
        }

        return count;
    }

    private int updateContactOptions(
            SQLiteDatabase db, long contactId, ContentValues inputValues, boolean callerIsSyncAdapter) {

        final ContentValues values = new ContentValues();
        ContactsDatabaseHelper.copyStringValue(
                values, RawContacts.CUSTOM_RINGTONE,
                inputValues, Contacts.CUSTOM_RINGTONE);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.SEND_TO_VOICEMAIL,
                inputValues, Contacts.SEND_TO_VOICEMAIL);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.LAST_TIME_CONTACTED,
                inputValues, Contacts.LAST_TIME_CONTACTED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.TIMES_CONTACTED,
                inputValues, Contacts.TIMES_CONTACTED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.STARRED,
                inputValues, Contacts.STARRED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.PINNED,
                inputValues, Contacts.PINNED);

        if (values.size() == 0) {
            return 0;  // Nothing to update, bail out.
        }

        boolean hasStarredValue = flagExists(values, RawContacts.STARRED);
        if (hasStarredValue) {
            // Mark dirty when changing starred to trigger sync.
            values.put(RawContacts.DIRTY, 1);
        }

        mSelectionArgs1[0] = String.valueOf(contactId);
        /**
         * SPRD:Bug529351 When add default contact to favorate, you can still see it in short list.
         * Original Android code:
        db.update(Tables.RAW_CONTACTS, values, RawContacts.CONTACT_ID + "=?"
                + " AND " + RawContacts.RAW_CONTACT_IS_READ_ONLY + "=0", mSelectionArgs1);
         * @{
         */
        db.update(Tables.RAW_CONTACTS, values, RawContacts.CONTACT_ID + "=?", mSelectionArgs1);
        /**
         * @}
         */

        if (hasStarredValue && !callerIsSyncAdapter) {
            Cursor cursor = db.query(Views.RAW_CONTACTS,
                    new String[] { RawContacts._ID }, RawContacts.CONTACT_ID + "=?",
                    mSelectionArgs1, null, null, null);
            try {
                while (cursor.moveToNext()) {
                    long rawContactId = cursor.getLong(0);
                    updateFavoritesMembership(rawContactId,
                            flagIsSet(values, RawContacts.STARRED));
                }
            } finally {
                cursor.close();
            }
        }

        // Copy changeable values to prevent automatically managed fields from being explicitly
        // updated by clients.
        values.clear();
        ContactsDatabaseHelper.copyStringValue(
                values, RawContacts.CUSTOM_RINGTONE,
                inputValues, Contacts.CUSTOM_RINGTONE);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.SEND_TO_VOICEMAIL,
                inputValues, Contacts.SEND_TO_VOICEMAIL);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.LAST_TIME_CONTACTED,
                inputValues, Contacts.LAST_TIME_CONTACTED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.TIMES_CONTACTED,
                inputValues, Contacts.TIMES_CONTACTED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.STARRED,
                inputValues, Contacts.STARRED);
        ContactsDatabaseHelper.copyLongValue(
                values, RawContacts.PINNED,
                inputValues, Contacts.PINNED);

        values.put(Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                Clock.getInstance().currentTimeMillis());

        int rslt = db.update(Tables.CONTACTS, values, Contacts._ID + "=?",
                mSelectionArgs1);

        if (inputValues.containsKey(Contacts.LAST_TIME_CONTACTED) &&
                !inputValues.containsKey(Contacts.TIMES_CONTACTED)) {
            db.execSQL(UPDATE_TIMES_CONTACTED_CONTACTS_TABLE, mSelectionArgs1);
            db.execSQL(UPDATE_TIMES_CONTACTED_RAWCONTACTS_TABLE, mSelectionArgs1);
        }
        return rslt;
    }

    private int updateAggregationException(SQLiteDatabase db, ContentValues values) {
        Integer exceptionType = values.getAsInteger(AggregationExceptions.TYPE);
        Long rcId1 = values.getAsLong(AggregationExceptions.RAW_CONTACT_ID1);
        Long rcId2 = values.getAsLong(AggregationExceptions.RAW_CONTACT_ID2);
        if (exceptionType == null || rcId1 == null || rcId2 == null) {
            return 0;
        }

        long rawContactId1;
        long rawContactId2;
        if (rcId1 < rcId2) {
            rawContactId1 = rcId1;
            rawContactId2 = rcId2;
        } else {
            rawContactId2 = rcId1;
            rawContactId1 = rcId2;
        }

        if (exceptionType == AggregationExceptions.TYPE_AUTOMATIC) {
            mSelectionArgs2[0] = String.valueOf(rawContactId1);
            mSelectionArgs2[1] = String.valueOf(rawContactId2);
            db.delete(Tables.AGGREGATION_EXCEPTIONS,
                    AggregationExceptions.RAW_CONTACT_ID1 + "=? AND "
                    + AggregationExceptions.RAW_CONTACT_ID2 + "=?", mSelectionArgs2);
        } else {
            ContentValues exceptionValues = new ContentValues(3);
            exceptionValues.put(AggregationExceptions.TYPE, exceptionType);
            exceptionValues.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
            exceptionValues.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
            db.replace(Tables.AGGREGATION_EXCEPTIONS, AggregationExceptions._ID, exceptionValues);
        }

        final AbstractContactAggregator aggregator = mAggregator.get();
        aggregator.invalidateAggregationExceptionCache();
        aggregator.markForAggregation(rawContactId1, RawContacts.AGGREGATION_MODE_DEFAULT, true);
        aggregator.markForAggregation(rawContactId2, RawContacts.AGGREGATION_MODE_DEFAULT, true);

        aggregator.aggregateContact(mTransactionContext.get(), db, rawContactId1);
        aggregator.aggregateContact(mTransactionContext.get(), db, rawContactId2);

        // The return value is fake - we just confirm that we made a change, not count actual
        // rows changed.
        return 1;
    }

    @Override
    public void onAccountsUpdated(Account[] accounts) {
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_ACCOUNTS);
    }

    /** return serialized version of {@code accounts} */
    @VisibleForTesting
    static String accountsToString(Set<Account> accounts) {
        final StringBuilder sb = new StringBuilder();
        for (Account account : accounts) {
            if (sb.length() > 0) {
                sb.append(ACCOUNT_STRING_SEPARATOR_OUTER);
            }
            sb.append(account.name);
            sb.append(ACCOUNT_STRING_SEPARATOR_INNER);
            sb.append(account.type);
        }
        return sb.toString();
    }

    /**
     * de-serialize string returned by {@link #accountsToString} and return it.
     * If {@code accountsString} is malformed it'll throw {@link IllegalArgumentException}.
     */
    @VisibleForTesting
    static Set<Account> stringToAccounts(String accountsString) {
        final Set<Account> ret = Sets.newHashSet();
        if (accountsString.length() == 0) return ret; // no accounts
        try {
            for (String accountString : accountsString.split(ACCOUNT_STRING_SEPARATOR_OUTER)) {
                String[] nameAndType = accountString.split(ACCOUNT_STRING_SEPARATOR_INNER);
                ret.add(new Account(nameAndType[0], nameAndType[1]));
            }
            return ret;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Malformed string", ex);
        }
    }

    /**
     * @return {@code true} if the given {@code currentSystemAccounts} are different from the
     *    accounts we know, which are stored in the {@link DbProperties#KNOWN_ACCOUNTS} property.
     */
    @VisibleForTesting
    boolean haveAccountsChanged(Account[] currentSystemAccounts) {
        final ContactsDatabaseHelper dbHelper = mDbHelper.get();
        final Set<Account> knownAccountSet;
        try {
            knownAccountSet =
                    stringToAccounts(dbHelper.getProperty(DbProperties.KNOWN_ACCOUNTS, ""));
        } catch (IllegalArgumentException e) {
            // Failed to get the last known accounts for an unknown reason.  Let's just
            // treat as if accounts have changed.
            return true;
        }
        final Set<Account> currentAccounts = Sets.newHashSet(currentSystemAccounts);
        return !knownAccountSet.equals(currentAccounts);
    }

    @VisibleForTesting
    void saveAccounts(Account[] systemAccounts) {
        final ContactsDatabaseHelper dbHelper = mDbHelper.get();
        dbHelper.setProperty(
                DbProperties.KNOWN_ACCOUNTS, accountsToString(Sets.newHashSet(systemAccounts)));
    }

    private boolean updateAccountsInBackground(Account[] systemAccounts) {
        if (!haveAccountsChanged(systemAccounts)) {
            return false;
        }
        if ("1".equals(SystemProperties.get(DEBUG_PROPERTY_KEEP_STALE_ACCOUNT_DATA))) {
            Log.w(TAG, "Accounts changed, but not removing stale data for " +
                    DEBUG_PROPERTY_KEEP_STALE_ACCOUNT_DATA);
            return true;
        }
        Log.i(TAG, "Accounts changed");

        invalidateFastScrollingIndexCache();

        final ContactsDatabaseHelper dbHelper = mDbHelper.get();
        final SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();

        // WARNING: This method can be run in either contacts mode or profile mode.  It is
        // absolutely imperative that no calls be made inside the following try block that can
        // interact with a specific contacts or profile DB.  Otherwise it is quite possible for a
        // deadlock to occur.  i.e. always use the current database in mDbHelper and do not access
        // mContactsHelper or mProfileHelper directly.
        //
        // The problem may be a bit more subtle if you also access something that stores the current
        // db instance in its constructor.  updateSearchIndexInTransaction relies on the
        // SearchIndexManager which upon construction, stores the current db. In this case,
        // SearchIndexManager always contains the contact DB. This is why the
        // updateSearchIndexInTransaction is protected with !isInProfileMode now.
        try {
            // First, remove stale rows from raw_contacts, groups, and related tables.

            // All accounts that are used in raw_contacts and/or groups.
            final Set<AccountWithDataSet> knownAccountsWithDataSets
                    = dbHelper.getAllAccountsWithDataSets();

            // Find the accounts that have been removed.
            final List<AccountWithDataSet> accountsWithDataSetsToDelete = Lists.newArrayList();
            for (AccountWithDataSet knownAccountWithDataSet : knownAccountsWithDataSets) {
                if (knownAccountWithDataSet.isLocalAccount()
                        || knownAccountWithDataSet.inSystemAccounts(systemAccounts)) {
                    continue;
                }
                accountsWithDataSetsToDelete.add(knownAccountWithDataSet);
            }

            if (!accountsWithDataSetsToDelete.isEmpty()) {
                for (AccountWithDataSet accountWithDataSet : accountsWithDataSetsToDelete) {
                    final Long accountIdOrNull = dbHelper.getAccountIdOrNull(accountWithDataSet);

                    // getAccountIdOrNull() really shouldn't return null here, but just in case...
                    if (accountIdOrNull != null) {
                        final String accountId = Long.toString(accountIdOrNull);
                        final String[] accountIdParams =
                                new String[] {accountId};
                        db.execSQL(
                                "DELETE FROM " + Tables.GROUPS +
                                " WHERE " + GroupsColumns.ACCOUNT_ID + " = ?",
                                accountIdParams);
                        db.execSQL(
                                "DELETE FROM " + Tables.PRESENCE +
                                " WHERE " + PresenceColumns.RAW_CONTACT_ID + " IN (" +
                                        "SELECT " + RawContacts._ID +
                                        " FROM " + Tables.RAW_CONTACTS +
                                        " WHERE " + RawContactsColumns.ACCOUNT_ID + " = ?)",
                                        accountIdParams);
                        db.execSQL(
                                "DELETE FROM " + Tables.STREAM_ITEM_PHOTOS +
                                " WHERE " + StreamItemPhotos.STREAM_ITEM_ID + " IN (" +
                                        "SELECT " + StreamItems._ID +
                                        " FROM " + Tables.STREAM_ITEMS +
                                        " WHERE " + StreamItems.RAW_CONTACT_ID + " IN (" +
                                                "SELECT " + RawContacts._ID +
                                                " FROM " + Tables.RAW_CONTACTS +
                                                " WHERE " + RawContactsColumns.ACCOUNT_ID + "=?))",
                                                accountIdParams);
                        db.execSQL(
                                "DELETE FROM " + Tables.STREAM_ITEMS +
                                " WHERE " + StreamItems.RAW_CONTACT_ID + " IN (" +
                                        "SELECT " + RawContacts._ID +
                                        " FROM " + Tables.RAW_CONTACTS +
                                        " WHERE " + RawContactsColumns.ACCOUNT_ID + " = ?)",
                                        accountIdParams);

                        // Delta API is only needed for regular contacts.
                        if (!inProfileMode()) {
                            // Contacts are deleted by a trigger on the raw_contacts table.
                            // But we also need to insert the contact into the delete log.
                            // This logic is being consolidated into the ContactsTableUtil.

                            // deleteContactIfSingleton() does not work in this case because raw
                            // contacts will be deleted in a single batch below.  Contacts with
                            // multiple raw contacts in the same account will be missed.

                            // Find all contacts that do not have raw contacts in other accounts.
                            // These should be deleted.
                            Cursor cursor = db.rawQuery(
                                    "SELECT " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            " FROM " + Tables.RAW_CONTACTS +
                                            " WHERE " + RawContactsColumns.ACCOUNT_ID + " = ?1" +
                                            " AND " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            " IS NOT NULL" +
                                            " AND " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            " NOT IN (" +
                                            "    SELECT " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            "    FROM " + Tables.RAW_CONTACTS +
                                            "    WHERE " + RawContactsColumns.ACCOUNT_ID + " != ?1"
                                            + "  AND " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            "    IS NOT NULL"
                                            + ")", accountIdParams);
                            try {
                                while (cursor.moveToNext()) {
                                    final long contactId = cursor.getLong(0);
                                    ContactsTableUtil.deleteContact(db, contactId);
                                }
                            } finally {
                                MoreCloseables.closeQuietly(cursor);
                            }

                            // If the contact was not deleted, its last updated timestamp needs to
                            // be refreshed since one of its raw contacts got removed.
                            // Find all contacts that will not be deleted (i.e. contacts with
                            // raw contacts in other accounts)
                            cursor = db.rawQuery(
                                    "SELECT DISTINCT " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            " FROM " + Tables.RAW_CONTACTS +
                                            " WHERE " + RawContactsColumns.ACCOUNT_ID + " = ?1" +
                                            " AND " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            " IN (" +
                                            "    SELECT " + RawContactsColumns.CONCRETE_CONTACT_ID +
                                            "    FROM " + Tables.RAW_CONTACTS +
                                            "    WHERE " + RawContactsColumns.ACCOUNT_ID + " != ?1"
                                            + ")", accountIdParams);
                            try {
                                while (cursor.moveToNext()) {
                                    final long contactId = cursor.getLong(0);
                                    ContactsTableUtil.updateContactLastUpdateByContactId(
                                            db, contactId);
                                }
                            } finally {
                                MoreCloseables.closeQuietly(cursor);
                            }
                        }

                        db.execSQL(
                                "DELETE FROM " + Tables.RAW_CONTACTS +
                                " WHERE " + RawContactsColumns.ACCOUNT_ID + " = ?",
                                accountIdParams);
                        db.execSQL(
                                "DELETE FROM " + Tables.ACCOUNTS +
                                " WHERE " + AccountsColumns._ID + "=?",
                                accountIdParams);
                    }
                }

                // Find all aggregated contacts that used to contain the raw contacts
                // we have just deleted and see if they are still referencing the deleted
                // names or photos.  If so, fix up those contacts.
                HashSet<Long> orphanContactIds = Sets.newHashSet();
                Cursor cursor = db.rawQuery("SELECT " + Contacts._ID +
                        " FROM " + Tables.CONTACTS +
                        " WHERE (" + Contacts.NAME_RAW_CONTACT_ID + " NOT NULL AND " +
                                Contacts.NAME_RAW_CONTACT_ID + " NOT IN " +
                                        "(SELECT " + RawContacts._ID +
                                        " FROM " + Tables.RAW_CONTACTS + "))" +
                        " OR (" + Contacts.PHOTO_ID + " NOT NULL AND " +
                                Contacts.PHOTO_ID + " NOT IN " +
                                        "(SELECT " + Data._ID +
                                        " FROM " + Tables.DATA + "))", null);
                try {
                    while (cursor.moveToNext()) {
                        orphanContactIds.add(cursor.getLong(0));
                    }
                } finally {
                    cursor.close();
                }

                for (Long contactId : orphanContactIds) {
                    mAggregator.get().updateAggregateData(mTransactionContext.get(), contactId);
                }
                dbHelper.updateAllVisible();

                // Don't bother updating the search index if we're in profile mode - there is no
                // search index for the profile DB, and updating it for the contacts DB in this case
                // makes no sense and risks a deadlock.
                if (!inProfileMode()) {
                    // TODO Fix it.  It only updates index for contacts/raw_contacts that the
                    // current transaction context knows updated, but here in this method we don't
                    // update that information, so effectively it's no-op.
                    // We can probably just schedule BACKGROUND_TASK_UPDATE_SEARCH_INDEX.
                    // (But make sure it's not scheduled yet. We schedule this task in initialize()
                    // too.)
                    updateSearchIndexInTransaction();
                }
            }

            // Second, remove stale rows from Tables.SETTINGS and Tables.DIRECTORIES
            removeStaleAccountRows(
                    Tables.SETTINGS, Settings.ACCOUNT_NAME, Settings.ACCOUNT_TYPE, systemAccounts);
            removeStaleAccountRows(Tables.DIRECTORIES, Directory.ACCOUNT_NAME,
                    Directory.ACCOUNT_TYPE, systemAccounts);

            // Third, remaining tasks that must be done in a transaction.
            // TODO: Should sync state take data set into consideration?
            dbHelper.getSyncState().onAccountsChanged(db, systemAccounts);

            saveAccounts(systemAccounts);

            db.setTransactionSuccessful();

        /**
         * SPRD: Catch SQLiteFullException when database or disk is full.
         *
         * @{
         */
         } catch (SQLiteFullException e) {
             Log.e(TAG, "Could not insert data to dataBase because SQLite is full");
         } catch (SQLiteDiskIOException e) {
             Log.e(TAG, "Could not insert data to dataBase because disk I/O error");
         } catch (Exception e) {
             Log.e(TAG, "Could not insert data to dataBase");
         /*
          * @}
          */
        } finally {
            /**
             * SPRD: Catch SQLiteFullException when database or disk is full.
             * Original Android code:
             *
            db.endTransaction();
             * @{
             */
            try {
                db.endTransaction();
            } catch (android.database.sqlite.SQLiteCantOpenDatabaseException e2) {
                Log.e(TAG, "SQLiteCantOpenDatabaseException: unable to open database file");
            } catch (SQLiteDiskIOException e) {
                Log.e(TAG, "SQLiteDiskIOException: disk I/O error");
            } catch (Exception e) {
                Log.e(TAG, "endTransaction exception ", e);
            }
            /*
             * @}
             */
        }
        mAccountWritability.clear();

        updateContactsAccountCount(systemAccounts);
        updateProviderStatus();
        return true;
    }

    private void updateContactsAccountCount(Account[] accounts) {
        int count = 0;
        for (Account account : accounts) {
            if (isContactsAccount(account)) {
                count++;
            }
        }
        mContactsAccountCount = count;
    }

    // Overridden in SynchronousContactsProvider2.java
    protected boolean isContactsAccount(Account account) {
        final IContentService cs = ContentResolver.getContentService();
        try {
            return cs.getIsSyncable(account, ContactsContract.AUTHORITY) > 0;
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot obtain sync flag for account", e);
            return false;
        }
    }

    public void onPackageChanged(String packageName) {
        scheduleBackgroundTask(BACKGROUND_TASK_UPDATE_DIRECTORIES, packageName);
    }

    private void removeStaleAccountRows(String table, String accountNameColumn,
            String accountTypeColumn, Account[] systemAccounts) {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        final Cursor c = db.rawQuery(
                "SELECT DISTINCT " + accountNameColumn +
                "," + accountTypeColumn +
                " FROM " + table, null);
        try {
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                final AccountWithDataSet accountWithDataSet = AccountWithDataSet.get(
                        c.getString(0), c.getString(1), null);
                if (accountWithDataSet.isLocalAccount()
                        || accountWithDataSet.inSystemAccounts(systemAccounts)) {
                    // Account still exists.
                    continue;
                }

                db.execSQL("DELETE FROM " + table +
                        " WHERE " + accountNameColumn + "=? AND " +
                        accountTypeColumn + "=?",
                        new String[] {accountWithDataSet.getAccountName(),
                                accountWithDataSet.getAccountType()});
            }
        } finally {
            c.close();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder, CancellationSignal cancellationSignal) {
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "query: uri=" + uri + "  projection=" + Arrays.toString(projection) +
                    "  selection=[" + selection + "]  args=" + Arrays.toString(selectionArgs) +
                    "  order=[" + sortOrder + "] CPID=" + Binder.getCallingPid() +
                    " User=" + UserUtils.getCurrentUserHandle(getContext()));
        }

        waitForAccess(mReadAccessLatch);

        // Query the profile DB if appropriate.
        if (mapsToProfileDb(uri)) {
            switchToProfileMode();
            return mProfileProvider.query(uri, projection, selection, selectionArgs, sortOrder,
                    cancellationSignal);
        }

        // Otherwise proceed with a normal query against the contacts DB.
        switchToContactMode();

        String directory = getQueryParameter(uri, ContactsContract.DIRECTORY_PARAM_KEY);
        final long directoryId =
                (directory == null ? -1 :
                (directory.equals("0") ? Directory.DEFAULT :
                (directory.equals("1") ? Directory.LOCAL_INVISIBLE : Long.MIN_VALUE)));

        if (directoryId > Long.MIN_VALUE) {
            final Cursor cursor = queryLocal(uri, projection, selection, selectionArgs, sortOrder,
                    directoryId, cancellationSignal);
            return addSnippetExtrasToCursor(uri, cursor);
        }

        DirectoryInfo directoryInfo = getDirectoryAuthority(directory);
        if (directoryInfo == null) {
            Log.e(TAG, "Invalid directory ID: " + uri);
            return null;
        }

        Builder builder = new Uri.Builder();
        builder.scheme(ContentResolver.SCHEME_CONTENT);
        builder.authority(directoryInfo.authority);
        builder.encodedPath(uri.getEncodedPath());
        if (directoryInfo.accountName != null) {
            builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, directoryInfo.accountName);
        }
        if (directoryInfo.accountType != null) {
            builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, directoryInfo.accountType);
        }

        String limit = getLimit(uri);
        if (limit != null) {
            builder.appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, limit);
        }

        Uri directoryUri = builder.build();

        if (projection == null) {
            projection = getDefaultProjection(uri);
        }

        Cursor cursor;
        try {
            cursor = getContext().getContentResolver().query(
                    directoryUri, projection, selection, selectionArgs, sortOrder);
            if (cursor == null) {
                return null;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Directory query failed: uri=" + uri, e);
            return null;
        }

        // Load the cursor contents into a memory cursor (backed by a cursor window) and close the
        // underlying cursor.
        try {
            MemoryCursor memCursor = new MemoryCursor(null, cursor.getColumnNames());
            memCursor.fillFromCursor(cursor);
            return memCursor;
        } finally {
            cursor.close();
        }
    }

    private Cursor addSnippetExtrasToCursor(Uri uri, Cursor cursor) {

        // If the cursor doesn't contain a snippet column, don't bother wrapping it.
        if (cursor.getColumnIndex(SearchSnippets.SNIPPET) < 0) {
            return cursor;
        }

        String query = uri.getLastPathSegment();

        // Snippet data is needed for the snippeting on the client side, so store it in the cursor
        if (cursor instanceof AbstractCursor && deferredSnippetingRequested(uri)){
            Bundle oldExtras = cursor.getExtras();
            Bundle extras = new Bundle();
            if (oldExtras != null) {
                extras.putAll(oldExtras);
            }
            extras.putString(ContactsContract.DEFERRED_SNIPPETING_QUERY, query);

            ((AbstractCursor) cursor).setExtras(extras);
        }
        return cursor;
    }

    private Cursor addDeferredSnippetingExtra(Cursor cursor) {
        if (cursor instanceof AbstractCursor){
            Bundle oldExtras = cursor.getExtras();
            Bundle extras = new Bundle();
            if (oldExtras != null) {
                extras.putAll(oldExtras);
            }
            extras.putBoolean(ContactsContract.DEFERRED_SNIPPETING, true);
            ((AbstractCursor) cursor).setExtras(extras);
        }
        return cursor;
    }

    private static final class DirectoryQuery {
        public static final String[] COLUMNS = new String[] {
                Directory._ID,
                Directory.DIRECTORY_AUTHORITY,
                Directory.ACCOUNT_NAME,
                Directory.ACCOUNT_TYPE
        };

        public static final int DIRECTORY_ID = 0;
        public static final int AUTHORITY = 1;
        public static final int ACCOUNT_NAME = 2;
        public static final int ACCOUNT_TYPE = 3;
    }

    /**
     * Reads and caches directory information for the database.
     */
    private DirectoryInfo getDirectoryAuthority(String directoryId) {
        synchronized (mDirectoryCache) {
            if (!mDirectoryCacheValid) {
                mDirectoryCache.clear();
                SQLiteDatabase db = mDbHelper.get().getReadableDatabase();
                Cursor cursor = db.query(
                        Tables.DIRECTORIES, DirectoryQuery.COLUMNS, null, null, null, null, null);
                try {
                    while (cursor.moveToNext()) {
                        DirectoryInfo info = new DirectoryInfo();
                        String id = cursor.getString(DirectoryQuery.DIRECTORY_ID);
                        info.authority = cursor.getString(DirectoryQuery.AUTHORITY);
                        info.accountName = cursor.getString(DirectoryQuery.ACCOUNT_NAME);
                        info.accountType = cursor.getString(DirectoryQuery.ACCOUNT_TYPE);
                        mDirectoryCache.put(id, info);
                    }
                } finally {
                    cursor.close();
                }
                mDirectoryCacheValid = true;
            }

            return mDirectoryCache.get(directoryId);
        }
    }

    public void resetDirectoryCache() {
        synchronized(mDirectoryCache) {
            mDirectoryCacheValid = false;
        }
    }

    protected Cursor queryLocal(final Uri uri, final String[] projection, String selection,
            String[] selectionArgs, String sortOrder, final long directoryId,
            final CancellationSignal cancellationSignal) {

        final SQLiteDatabase db = mDbHelper.get().getReadableDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        String groupBy = null;
        String having = null;
        String limit = getLimit(uri);
        boolean snippetDeferred = false;

        // The expression used in bundleLetterCountExtras() to get count.
        String addressBookIndexerCountExpression = null;

        final int match = sUriMatcher.match(uri);
        switch (match) {
            /**
             * SPRD: bug441929, for custom display mode query
             *
             * @{
             */
            case CONTACTS_CUSTOM:
                return mContactsHelper.getReadableDatabase().query(true, Views.CONTACTS, projection,
                        selection, selectionArgs, null, null, null, null, cancellationSignal);
            /**
             * @}
             */
            case SYNCSTATE:
            case PROFILE_SYNCSTATE:
                return mDbHelper.get().getSyncState().query(db, projection, selection,
                        selectionArgs, sortOrder);

            case CONTACTS: {
                setTablesAndProjectionMapForContacts(qb, projection);
                appendLocalDirectoryAndAccountSelectionIfNeeded(qb, directoryId, uri);
                break;
            }

            case CONTACTS_ID: {
                long contactId = ContentUris.parseId(uri);
                setTablesAndProjectionMapForContacts(qb, projection);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(Contacts._ID + "=?");
                break;
            }

            case CONTACTS_LOOKUP:
            case CONTACTS_LOOKUP_ID: {
                List<String> pathSegments = uri.getPathSegments();
                int segmentCount = pathSegments.size();
                if (segmentCount < 3) {
                    throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                            "Missing a lookup key", uri));
                }

                String lookupKey = pathSegments.get(2);
                if (segmentCount == 4) {
                    long contactId = Long.parseLong(pathSegments.get(3));
                    SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                    setTablesAndProjectionMapForContacts(lookupQb, projection);

                    Cursor c = queryWithContactIdAndLookupKey(lookupQb, db,
                            projection, selection, selectionArgs, sortOrder, groupBy, limit,
                            Contacts._ID, contactId, Contacts.LOOKUP_KEY, lookupKey,
                            cancellationSignal);
                    if (c != null) {
                        return c;
                    }
                }

                setTablesAndProjectionMapForContacts(qb, projection);
                selectionArgs = insertSelectionArg(selectionArgs,
                        String.valueOf(lookupContactIdByLookupKey(db, lookupKey)));
                qb.appendWhere(Contacts._ID + "=?");
                break;
            }

            case CONTACTS_LOOKUP_DATA:
            case CONTACTS_LOOKUP_ID_DATA:
            case CONTACTS_LOOKUP_PHOTO:
            case CONTACTS_LOOKUP_ID_PHOTO: {
                List<String> pathSegments = uri.getPathSegments();
                int segmentCount = pathSegments.size();
                if (segmentCount < 4) {
                    throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                            "Missing a lookup key", uri));
                }
                String lookupKey = pathSegments.get(2);
                if (segmentCount == 5) {
                    long contactId = Long.parseLong(pathSegments.get(3));
                    SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                    setTablesAndProjectionMapForData(lookupQb, uri, projection, false);
                    if (match == CONTACTS_LOOKUP_PHOTO || match == CONTACTS_LOOKUP_ID_PHOTO) {
                        lookupQb.appendWhere(" AND " + Data._ID + "=" + Contacts.PHOTO_ID);
                    }
                    lookupQb.appendWhere(" AND ");
                    Cursor c = queryWithContactIdAndLookupKey(lookupQb, db,
                            projection, selection, selectionArgs, sortOrder, groupBy, limit,
                            Data.CONTACT_ID, contactId, Data.LOOKUP_KEY, lookupKey,
                            cancellationSignal);
                    if (c != null) {
                        return c;
                    }

                    // TODO see if the contact exists but has no data rows (rare)
                }

                setTablesAndProjectionMapForData(qb, uri, projection, false);
                long contactId = lookupContactIdByLookupKey(db, lookupKey);
                selectionArgs = insertSelectionArg(selectionArgs,
                        String.valueOf(contactId));
                if (match == CONTACTS_LOOKUP_PHOTO || match == CONTACTS_LOOKUP_ID_PHOTO) {
                    qb.appendWhere(" AND " + Data._ID + "=" + Contacts.PHOTO_ID);
                }
                qb.appendWhere(" AND " + Data.CONTACT_ID + "=?");
                break;
            }

            case CONTACTS_ID_STREAM_ITEMS: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForStreamItems(qb);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(StreamItems.CONTACT_ID + "=?");
                break;
            }

            case CONTACTS_LOOKUP_STREAM_ITEMS:
            case CONTACTS_LOOKUP_ID_STREAM_ITEMS: {
                List<String> pathSegments = uri.getPathSegments();
                int segmentCount = pathSegments.size();
                if (segmentCount < 4) {
                    throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                            "Missing a lookup key", uri));
                }
                String lookupKey = pathSegments.get(2);
                if (segmentCount == 5) {
                    long contactId = Long.parseLong(pathSegments.get(3));
                    SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                    setTablesAndProjectionMapForStreamItems(lookupQb);
                    Cursor c = queryWithContactIdAndLookupKey(lookupQb, db,
                            projection, selection, selectionArgs, sortOrder, groupBy, limit,
                            StreamItems.CONTACT_ID, contactId,
                            StreamItems.CONTACT_LOOKUP_KEY, lookupKey,
                            cancellationSignal);
                    if (c != null) {
                        return c;
                    }
                }

                setTablesAndProjectionMapForStreamItems(qb);
                long contactId = lookupContactIdByLookupKey(db, lookupKey);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(RawContacts.CONTACT_ID + "=?");
                break;
            }

            case CONTACTS_AS_VCARD: {
                final String lookupKey = Uri.encode(uri.getPathSegments().get(2));
                long contactId = lookupContactIdByLookupKey(db, lookupKey);
                /**
                 * SPRD:Bug474803 Sync Facebook contacts.
                 * Original Android code:
                qb.setTables(Views.CONTACTS);
                 * @{
                 */
                String contactsView = mDbHelper.get().getContactsView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
                qb.setTables(contactsView);
                /**
                 * @}
                 */
                qb.setProjectionMap(sContactsVCardProjectionMap);
                selectionArgs = insertSelectionArg(selectionArgs,
                        String.valueOf(contactId));
                qb.appendWhere(Contacts._ID + "=?");
                break;
            }

            case CONTACTS_AS_MULTI_VCARD: {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                String currentDateString = dateFormat.format(new Date()).toString();
                return db.rawQuery(
                    "SELECT" +
                    " 'vcards_' || ? || '.vcf' AS " + OpenableColumns.DISPLAY_NAME + "," +
                    " NULL AS " + OpenableColumns.SIZE,
                    new String[] { currentDateString });
            }

            case CONTACTS_FILTER: {
                String filterParam = "";
                boolean deferredSnipRequested = deferredSnippetingRequested(uri);
                if (uri.getPathSegments().size() > 2) {
                    filterParam = uri.getLastPathSegment();
                }

                // If the query consists of a single word, we can do snippetizing after-the-fact for
                // a performance boost.  Otherwise, we can't defer.
                snippetDeferred = isSingleWordQuery(filterParam)
                    && deferredSnipRequested && snippetNeeded(projection);
                setTablesAndProjectionMapForContactsWithSnippet(
                        qb, uri, projection, filterParam, directoryId,
                        snippetDeferred);
                break;
            }

            case CONTACTS_STREQUENT_FILTER:
            case CONTACTS_STREQUENT: {
                // Basically the resultant SQL should look like this:
                // (SQL for listing starred items)
                // UNION ALL
                // (SQL for listing frequently contacted items)
                // ORDER BY ...

                final boolean phoneOnly = readBooleanQueryParameter(
                        uri, ContactsContract.STREQUENT_PHONE_ONLY, false);
                if (match == CONTACTS_STREQUENT_FILTER && uri.getPathSegments().size() > 3) {
                    String filterParam = uri.getLastPathSegment();
                    StringBuilder sb = new StringBuilder();
                    sb.append(Contacts._ID + " IN ");
                    appendContactFilterAsNestedQuery(sb, filterParam);
                    selection = DbQueryUtils.concatenateClauses(selection, sb.toString());
                }

                String[] subProjection = null;
                if (projection != null) {
                    subProjection = new String[projection.length + 2];
                    System.arraycopy(projection, 0, subProjection, 0, projection.length);
                    subProjection[projection.length + 0] = DataUsageStatColumns.TIMES_USED;
                    subProjection[projection.length + 1] = DataUsageStatColumns.LAST_TIME_USED;
                }

                // String that will store the query for starred contacts. For phone only queries,
                // these will return a list of all phone numbers that belong to starred contacts.
                final String starredInnerQuery;
                // String that will store the query for frequents. These JOINS can be very slow
                // if assembled in the wrong order. Be sure to test changes against huge databases.
                final String frequentInnerQuery;

                if (phoneOnly) {
                    final StringBuilder tableBuilder = new StringBuilder();
                    // In phone only mode, we need to look at view_data instead of
                    // contacts/raw_contacts to obtain actual phone numbers. One problem is that
                    // view_data is much larger than view_contacts, so our query might become much
                    // slower.

                    // For starred phone numbers, we select only phone numbers that belong to
                    // starred contacts, and then do an outer join against the data usage table,
                    // to make sure that even if a starred number hasn't been previously used,
                    // it is included in the list of strequent numbers.
                    /**
                     * SPRD:Bug474803 Sync Facebook contacts.
                     * Original Android code:
                    tableBuilder.append("(SELECT * FROM " + Views.DATA + " WHERE "
                            + Contacts.STARRED + "=1)" + " AS " + Tables.DATA
                        + " LEFT OUTER JOIN " + Tables.DATA_USAGE_STAT
                            + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "="
                                + DataColumns.CONCRETE_ID + " AND "
                            + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "="
                                + DataUsageStatColumns.USAGE_TYPE_INT_CALL + ")");
                     * @{
                     */
                    String dataView = mDbHelper.get().getDataView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
                    tableBuilder.append("(SELECT * FROM " + dataView + " WHERE "
                            + Contacts.STARRED + "=1)" + " AS " + Tables.DATA
                        + " LEFT OUTER JOIN " + Tables.DATA_USAGE_STAT
                            + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "="
                                + DataColumns.CONCRETE_ID + " AND "
                            + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "="
                                + DataUsageStatColumns.USAGE_TYPE_INT_CALL + ")");
                    /**
                     * @}
                     */
                    appendContactPresenceJoin(tableBuilder, projection, RawContacts.CONTACT_ID);
                    appendContactStatusUpdateJoin(tableBuilder, projection,
                            ContactsColumns.LAST_STATUS_UPDATE_ID);
                    qb.setTables(tableBuilder.toString());
                    qb.setProjectionMap(sStrequentPhoneOnlyProjectionMap);
                    final long phoneMimeTypeId =
                            mDbHelper.get().getMimeTypeId(Phone.CONTENT_ITEM_TYPE);
                    final long sipMimeTypeId =
                            mDbHelper.get().getMimeTypeId(SipAddress.CONTENT_ITEM_TYPE);

                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                                "(" + Contacts.STARRED + "=1",
                                DataColumns.MIMETYPE_ID + " IN (" +
                            phoneMimeTypeId + ", " + sipMimeTypeId + ")) AND (" +
                            RawContacts.CONTACT_ID + " IN " + Tables.DEFAULT_DIRECTORY + ")"));
                    starredInnerQuery = qb.buildQuery(subProjection, null, null,
                        null, Data.IS_SUPER_PRIMARY + " DESC," + SORT_BY_DATA_USAGE, null);

                    qb = new SQLiteQueryBuilder();
                    qb.setStrict(true);
                    // Construct the query string for frequent phone numbers
                    tableBuilder.setLength(0);
                    // For frequent phone numbers, we start from data usage table and join
                    // view_data to the table, assuming data usage table is quite smaller than
                    // data rows (almost always it should be), and we don't want any phone
                    // numbers not used by the user. This way sqlite is able to drop a number of
                    // rows in view_data in the early stage of data lookup.
                    tableBuilder.append(Tables.DATA_USAGE_STAT
                            + " INNER JOIN " + Views.DATA + " " + Tables.DATA
                            + " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "="
                                + DataColumns.CONCRETE_ID + " AND "
                            + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "="
                                + DataUsageStatColumns.USAGE_TYPE_INT_CALL + ")");
                    appendContactPresenceJoin(tableBuilder, projection, RawContacts.CONTACT_ID);
                    appendContactStatusUpdateJoin(tableBuilder, projection,
                            ContactsColumns.LAST_STATUS_UPDATE_ID);
                    qb.setTables(tableBuilder.toString());
                    qb.setProjectionMap(sStrequentPhoneOnlyProjectionMap);
                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                            "(" + Contacts.STARRED + "=0 OR " + Contacts.STARRED + " IS NULL",
                            DataColumns.MIMETYPE_ID + " IN (" +
                            phoneMimeTypeId + ", " + sipMimeTypeId + ")) AND (" +
                            RawContacts.CONTACT_ID + " IN " + Tables.DEFAULT_DIRECTORY + ")"));
                    frequentInnerQuery = qb.buildQuery(subProjection, null, null, null,
                            SORT_BY_DATA_USAGE, "25");

                } else {
                    // Build the first query for starred contacts
                    qb.setStrict(true);
                    setTablesAndProjectionMapForContacts(qb, projection, false);
                    qb.setProjectionMap(sStrequentStarredProjectionMap);

                    starredInnerQuery = qb.buildQuery(subProjection,
                            DbQueryUtils.concatenateClauses(selection, Contacts.STARRED + "=1"),
                            Contacts._ID, null, Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
                            null);

                    // Reset the builder, and build the second query for frequents contacts
                    qb = new SQLiteQueryBuilder();
                    qb.setStrict(true);

                    setTablesAndProjectionMapForContacts(qb, projection, true);
                    qb.setProjectionMap(sStrequentFrequentProjectionMap);
                    qb.appendWhere(DbQueryUtils.concatenateClauses(
                            selection,
                            "(" + Contacts.STARRED + " =0 OR " + Contacts.STARRED + " IS NULL)"));
                    // Note frequentInnerQuery is a grouping query, so the "IN default_directory"
                    // selection needs to be in HAVING, not in WHERE.
                    final String HAVING =
                            RawContacts.CONTACT_ID + " IN " + Tables.DEFAULT_DIRECTORY;
                    frequentInnerQuery = qb.buildQuery(subProjection,
                            null, Contacts._ID, HAVING, SORT_BY_DATA_USAGE, "25");
                }

                // We need to wrap the inner queries in an extra select, because they contain
                // their own SORT and LIMIT

                // Phone numbers that were used more than 30 days ago are dropped from frequents
                final String frequentQuery = "SELECT * FROM (" + frequentInnerQuery + ") WHERE " +
                        TIME_SINCE_LAST_USED_SEC + "<" + LAST_TIME_USED_30_DAYS_SEC;
                final String starredQuery = "SELECT * FROM (" + starredInnerQuery + ")";

                // Put them together
                final String unionQuery =
                        qb.buildUnionQuery(new String[] {starredQuery, frequentQuery}, null, null);

                // Here, we need to use selection / selectionArgs (supplied from users) "twice",
                // as we want them both for starred items and for frequently contacted items.
                //
                // e.g. if the user specify selection = "starred =?" and selectionArgs = "0",
                // the resultant SQL should be like:
                // SELECT ... WHERE starred =? AND ...
                // UNION ALL
                // SELECT ... WHERE starred =? AND ...
                String[] doubledSelectionArgs = null;
                if (selectionArgs != null) {
                    final int length = selectionArgs.length;
                    doubledSelectionArgs = new String[length * 2];
                    System.arraycopy(selectionArgs, 0, doubledSelectionArgs, 0, length);
                    System.arraycopy(selectionArgs, 0, doubledSelectionArgs, length, length);
                }

                Cursor cursor = db.rawQuery(unionQuery, doubledSelectionArgs);
                if (cursor != null) {
                    cursor.setNotificationUri(
                            getContext().getContentResolver(), ContactsContract.AUTHORITY_URI);
                }
                return cursor;
            }

            case CONTACTS_FREQUENT: {
                setTablesAndProjectionMapForContacts(qb, projection, true);
                qb.setProjectionMap(sStrequentFrequentProjectionMap);
                groupBy = Contacts._ID;
                having = Contacts._ID + " IN " + Tables.DEFAULT_DIRECTORY;
                if (!TextUtils.isEmpty(sortOrder)) {
                    sortOrder = FREQUENT_ORDER_BY + ", " + sortOrder;
                } else {
                    sortOrder = FREQUENT_ORDER_BY;
                }
                break;
            }

            case CONTACTS_GROUP: {
                setTablesAndProjectionMapForContacts(qb, projection);
                if (uri.getPathSegments().size() > 2) {
                    qb.appendWhere(CONTACTS_IN_GROUP_SELECT);
                    String groupMimeTypeId = String.valueOf(
                            mDbHelper.get().getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE));
                    selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                    selectionArgs = insertSelectionArg(selectionArgs, groupMimeTypeId);
                }
                break;
            }

            case PROFILE: {
                setTablesAndProjectionMapForContacts(qb, projection);
                break;
            }

            case PROFILE_ENTITIES: {
                setTablesAndProjectionMapForEntities(qb, uri, projection);
                break;
            }

            case PROFILE_AS_VCARD: {
                /**
                 * SPRD:Bug474803 Sync Facebook contacts.
                 * Original Android code:
                qb.setTables(Views.CONTACTS);
                 * @{
                 */
                String contactsView = mDbHelper.get().getContactsView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
                qb.setTables(contactsView);
                /**
                 * @}
                 */
                qb.setProjectionMap(sContactsVCardProjectionMap);
                break;
            }

            case CONTACTS_ID_DATA: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(" AND " + RawContacts.CONTACT_ID + "=?");
                break;
            }

            case CONTACTS_ID_PHOTO: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(" AND " + RawContacts.CONTACT_ID + "=?");
                qb.appendWhere(" AND " + Data._ID + "=" + Contacts.PHOTO_ID);
                break;
            }

            case CONTACTS_ID_ENTITIES: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForEntities(qb, uri, projection);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(contactId));
                qb.appendWhere(" AND " + RawContacts.CONTACT_ID + "=?");
                break;
            }

            case CONTACTS_LOOKUP_ENTITIES:
            case CONTACTS_LOOKUP_ID_ENTITIES: {
                List<String> pathSegments = uri.getPathSegments();
                int segmentCount = pathSegments.size();
                if (segmentCount < 4) {
                    throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                            "Missing a lookup key", uri));
                }
                String lookupKey = pathSegments.get(2);
                if (segmentCount == 5) {
                    long contactId = Long.parseLong(pathSegments.get(3));
                    SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                    setTablesAndProjectionMapForEntities(lookupQb, uri, projection);
                    lookupQb.appendWhere(" AND ");

                    Cursor c = queryWithContactIdAndLookupKey(lookupQb, db,
                            projection, selection, selectionArgs, sortOrder, groupBy, limit,
                            Contacts.Entity.CONTACT_ID, contactId,
                            Contacts.Entity.LOOKUP_KEY, lookupKey,
                            cancellationSignal);
                    if (c != null) {
                        return c;
                    }
                }

                setTablesAndProjectionMapForEntities(qb, uri, projection);
                selectionArgs = insertSelectionArg(
                        selectionArgs, String.valueOf(lookupContactIdByLookupKey(db, lookupKey)));
                qb.appendWhere(" AND " + Contacts.Entity.CONTACT_ID + "=?");
                break;
            }

            case STREAM_ITEMS: {
                setTablesAndProjectionMapForStreamItems(qb);
                break;
            }

            case STREAM_ITEMS_ID: {
                setTablesAndProjectionMapForStreamItems(qb);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(StreamItems._ID + "=?");
                break;
            }

            case STREAM_ITEMS_LIMIT: {
                return buildSingleRowResult(projection, new String[] {StreamItems.MAX_ITEMS},
                        new Object[] {MAX_STREAM_ITEMS_PER_RAW_CONTACT});
            }

            case STREAM_ITEMS_PHOTOS: {
                setTablesAndProjectionMapForStreamItemPhotos(qb);
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS: {
                setTablesAndProjectionMapForStreamItemPhotos(qb);
                String streamItemId = uri.getPathSegments().get(1);
                selectionArgs = insertSelectionArg(selectionArgs, streamItemId);
                qb.appendWhere(StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=?");
                break;
            }

            case STREAM_ITEMS_ID_PHOTOS_ID: {
                setTablesAndProjectionMapForStreamItemPhotos(qb);
                String streamItemId = uri.getPathSegments().get(1);
                String streamItemPhotoId = uri.getPathSegments().get(3);
                selectionArgs = insertSelectionArg(selectionArgs, streamItemPhotoId);
                selectionArgs = insertSelectionArg(selectionArgs, streamItemId);
                qb.appendWhere(StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "=? AND " +
                        StreamItemPhotosColumns.CONCRETE_ID + "=?");
                break;
            }

            case PHOTO_DIMENSIONS: {
                return buildSingleRowResult(projection,
                        new String[] {DisplayPhoto.DISPLAY_MAX_DIM, DisplayPhoto.THUMBNAIL_MAX_DIM},
                        new Object[] {getMaxDisplayPhotoDim(), getMaxThumbnailDim()});
            }
            case PHONES_ENTERPRISE: {
                ContactsPermissions.enforceCallingOrSelfPermission(getContext(),
                        INTERACT_ACROSS_USERS);
                return queryMergedDataPhones(uri, projection, selection, selectionArgs, sortOrder);
            }
            case PHONES:
            case CALLABLES: {
                final String mimeTypeIsPhoneExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
                final String mimeTypeIsSipExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                if (match == CALLABLES) {
                    qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression +
                            ") OR (" + mimeTypeIsSipExpression + "))");
                } else {
                    qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
                }

                final boolean removeDuplicates = readBooleanQueryParameter(
                        uri, ContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
                if (removeDuplicates) {
                    /*
                     * SPRD:Bug 305872
                     * From blackList choose the PhoneNumber,the phone type is disorder.
                     * Orig:
                    groupBy = RawContacts.CONTACT_ID + ", " + Data.DATA1;
                     * @{
                     */
                    groupBy = RawContacts.CONTACT_ID + ", " + Data.DATA2 + ", " + Data.DATA1;
                    /**
                     * @}
                     */

                    // In this case, because we dedupe phone numbers, the address book indexer needs
                    // to take it into account too.  (Otherwise headers will appear in wrong
                    // positions.)
                    // So use count(distinct pair(CONTACT_ID, PHONE NUMBER)) instead of count(*).
                    // But because there's no such thing as pair() on sqlite, we use
                    // CONTACT_ID || ',' || PHONE NUMBER instead.
                    // This only slows down the query by 14% with 10,000 contacts.
                    addressBookIndexerCountExpression = "DISTINCT "
                            + RawContacts.CONTACT_ID + "||','||" + Data.DATA1;
                }
                break;
            }

            case PHONES_ID:
            case CALLABLES_ID: {
                final String mimeTypeIsPhoneExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
                final String mimeTypeIsSipExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                if (match == CALLABLES_ID) {
                    qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression +
                            ") OR (" + mimeTypeIsSipExpression + "))");
                } else {
                    qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
                }
                qb.appendWhere(" AND " + Data._ID + "=?");
                break;
            }

            case PHONES_FILTER:
            case CALLABLES_FILTER: {
                final String mimeTypeIsPhoneExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForPhone();
                final String mimeTypeIsSipExpression =
                        DataColumns.MIMETYPE_ID + "=" + mDbHelper.get().getMimeTypeIdForSip();

                String typeParam = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
                final int typeInt = getDataUsageFeedbackType(typeParam,
                        DataUsageStatColumns.USAGE_TYPE_INT_CALL);
                setTablesAndProjectionMapForData(qb, uri, projection, true, typeInt);
                if (match == CALLABLES_FILTER) {
                    qb.appendWhere(" AND ((" + mimeTypeIsPhoneExpression +
                            ") OR (" + mimeTypeIsSipExpression + "))");
                } else {
                    qb.appendWhere(" AND " + mimeTypeIsPhoneExpression);
                }

                if (uri.getPathSegments().size() > 2) {
                    final String filterParam = uri.getLastPathSegment();
                    final boolean searchDisplayName = uri.getBooleanQueryParameter(
                            Phone.SEARCH_DISPLAY_NAME_KEY, true);
                    final boolean searchPhoneNumber = uri.getBooleanQueryParameter(
                            Phone.SEARCH_PHONE_NUMBER_KEY, true);

                    final StringBuilder sb = new StringBuilder();
                    sb.append(" AND (");

                    boolean hasCondition = false;
                    // This searches the name, nickname and organization fields.
                    final String ftsMatchQuery =
                            searchDisplayName
                            ? SearchIndexManager.getFtsMatchQuery(filterParam,
                            /**
                            * SPRD:
                            *
                            * Original Android code:
                                    FtsQueryBuilder.UNSCOPED_NORMALIZING)
                            * @{
                            */
                            FtsQueryBuilder.SCOPED_NAME_NORMALIZING)
                            /**
                            *
                            * @}
                            */
                            : null;
                    if (!TextUtils.isEmpty(ftsMatchQuery)) {
                        sb.append(Data.RAW_CONTACT_ID + " IN " +
                                "(SELECT " + RawContactsColumns.CONCRETE_ID +
                                " FROM " + Tables.SEARCH_INDEX +
                                " JOIN " + Tables.RAW_CONTACTS +
                                " ON (" + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID
                                        + "=" + RawContactsColumns.CONCRETE_CONTACT_ID + ")" +
                                " WHERE " + SearchIndexColumns.NAME + " MATCH '");
                        sb.append(ftsMatchQuery);
                        sb.append("')");
                        hasCondition = true;
                    }

                    if (searchPhoneNumber) {
                        final String number = PhoneNumberUtils.normalizeNumber(filterParam);
                        if (!TextUtils.isEmpty(number)) {
                            if (hasCondition) {
                                sb.append(" OR ");
                            }
                            sb.append(Data._ID +
                                    " IN (SELECT DISTINCT " + PhoneLookupColumns.DATA_ID
                                    + " FROM " + Tables.PHONE_LOOKUP
                                    + " WHERE " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                            sb.append(number);
                            sb.append("%')");
                            hasCondition = true;
                        }

                        if (!TextUtils.isEmpty(filterParam) && match == CALLABLES_FILTER) {
                            // If the request is via Callable URI, Sip addresses matching the filter
                            // parameter should be returned.
                            if (hasCondition) {
                                sb.append(" OR ");
                            }
                            sb.append("(");
                            sb.append(mimeTypeIsSipExpression);
                            sb.append(" AND ((" + Data.DATA1 + " LIKE ");
                            DatabaseUtils.appendEscapedSQLString(sb, filterParam + '%');
                            sb.append(") OR (" + Data.DATA1 + " LIKE ");
                            // Users may want SIP URIs starting from "sip:"
                            DatabaseUtils.appendEscapedSQLString(sb, "sip:"+ filterParam + '%');
                            sb.append(")))");
                            hasCondition = true;
                        }
                    }

                    if (!hasCondition) {
                        // If it is neither a phone number nor a name, the query should return
                        // an empty cursor.  Let's ensure that.
                        sb.append("0");
                    }
                    sb.append(")");
                    qb.appendWhere(sb);
                }
                if (match == CALLABLES_FILTER) {
                    // If the row is for a phone number that has a normalized form, we should use
                    // the normalized one as PHONES_FILTER does, while we shouldn't do that
                    // if the row is for a sip address.
                    String isPhoneAndHasNormalized = "("
                        + mimeTypeIsPhoneExpression + " AND "
                        + Phone.NORMALIZED_NUMBER + " IS NOT NULL)";
                    groupBy = "(CASE WHEN " + isPhoneAndHasNormalized
                        + " THEN " + Phone.NORMALIZED_NUMBER
                        + " ELSE " + Phone.NUMBER + " END), " + RawContacts.CONTACT_ID;
                } else {
                    groupBy = "(CASE WHEN " + Phone.NORMALIZED_NUMBER
                        + " IS NOT NULL THEN " + Phone.NORMALIZED_NUMBER
                        + " ELSE " + Phone.NUMBER + " END), " + RawContacts.CONTACT_ID;
                }
                if (sortOrder == null) {
                    final String accountPromotionSortOrder = getAccountPromotionSortOrder(uri);
                    if (!TextUtils.isEmpty(accountPromotionSortOrder)) {
                        sortOrder = accountPromotionSortOrder + ", " + PHONE_FILTER_SORT_ORDER;
                    } else {
                        sortOrder = PHONE_FILTER_SORT_ORDER;
                    }
                }
                break;
            }

            /**
            * SPRD:Bug 474752 Add features with multiSelection activity in Contacts.
            *
            * @{
            */
            case DATA_FILTER: {
                setTablesAndProjectionMapForData(qb, uri, projection, true,
                        DataUsageStatColumns.USAGE_TYPE_INT_CALL);

                if (uri.getPathSegments().size() > 2) {
                    String filterParam = uri.getLastPathSegment();
                    StringBuilder sb = new StringBuilder();
                    sb.append(" AND (");

                    boolean hasCondition = false;
                    boolean orNeeded = false;
                    final String ftsMatchQuery = SearchIndexManager.getFtsMatchQuery(
                            filterParam, FtsQueryBuilder.SCOPED_NAME_NORMALIZING);
                    if (ftsMatchQuery.length() > 0) {
                        sb.append(Data.RAW_CONTACT_ID + " IN " +
                                "(SELECT " + RawContactsColumns.CONCRETE_ID +
                                " FROM " + Tables.SEARCH_INDEX +
                                " JOIN " + Tables.RAW_CONTACTS +
                                " ON (" + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID
                                + "=" + RawContactsColumns.CONCRETE_CONTACT_ID + ")"

                                );
                        sb.append(" WHERE " + SearchIndexColumns.NAME + " MATCH '");
                        sb.append(ftsMatchQuery + "'");
                        sb.append(")");
                        orNeeded = true;
                        hasCondition = true;
                    }
                    if (orNeeded) {
                        sb.append(" OR ");
                    }
                    sb.append(Data.DATA1 + " LIKE ");
                    DatabaseUtils.appendEscapedSQLString(sb, '%' + filterParam + '%');
                    hasCondition = true;
                    if (!hasCondition) {
                        // If it is neither a phone number nor a name, the query
                        // should return
                        // an empty cursor. Let's ensure that.
                        sb.append("0");
                    }
                    sb.append(")");
                    qb.appendWhere(sb);
                }
                groupBy = Data.DATA1 + "," + RawContacts.CONTACT_ID;
                break;
            }
            /**
            * @}
            */

            case EMAILS: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                        + mDbHelper.get().getMimeTypeIdForEmail());

                final boolean removeDuplicates = readBooleanQueryParameter(
                        uri, ContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
                if (removeDuplicates) {
                    groupBy = RawContacts.CONTACT_ID + ", " + Data.DATA1;

                    // See PHONES for more detail.
                    addressBookIndexerCountExpression = "DISTINCT "
                            + RawContacts.CONTACT_ID + "||','||" + Data.DATA1;
                }
                break;
            }

            case EMAILS_ID: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                        + mDbHelper.get().getMimeTypeIdForEmail()
                        + " AND " + Data._ID + "=?");
                break;
            }

            case EMAILS_LOOKUP: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                        + mDbHelper.get().getMimeTypeIdForEmail());
                if (uri.getPathSegments().size() > 2) {
                    String email = uri.getLastPathSegment();
                    String address = mDbHelper.get().extractAddressFromEmailAddress(email);
                    selectionArgs = insertSelectionArg(selectionArgs, address);
                    qb.appendWhere(" AND UPPER(" + Email.DATA + ")=UPPER(?)");
                }
                // unless told otherwise, we'll return visible before invisible contacts
                if (sortOrder == null) {
                    sortOrder = "(" + RawContacts.CONTACT_ID + " IN " +
                            Tables.DEFAULT_DIRECTORY + ") DESC";
                }
                break;
            }
            case EMAILS_LOOKUP_ENTERPRISE: {
                return queryEmailsLookupEnterprise(uri, projection, selection, selectionArgs,
                        sortOrder);
            }

            case EMAILS_FILTER: {
                String typeParam = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
                final int typeInt = getDataUsageFeedbackType(typeParam,
                        DataUsageStatColumns.USAGE_TYPE_INT_LONG_TEXT);
                setTablesAndProjectionMapForData(qb, uri, projection, true, typeInt);
                String filterParam = null;

                if (uri.getPathSegments().size() > 3) {
                    filterParam = uri.getLastPathSegment();
                    if (TextUtils.isEmpty(filterParam)) {
                        filterParam = null;
                    }
                }

                if (filterParam == null) {
                    // If the filter is unspecified, return nothing
                    qb.appendWhere(" AND 0");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" AND " + Data._ID + " IN (");
                    sb.append(
                            "SELECT " + Data._ID +
                            " FROM " + Tables.DATA +
                            " WHERE " + DataColumns.MIMETYPE_ID + "=");
                    sb.append(mDbHelper.get().getMimeTypeIdForEmail());
                    sb.append(" AND " + Data.DATA1 + " LIKE ");
                    DatabaseUtils.appendEscapedSQLString(sb, filterParam + '%');
                    if (!filterParam.contains("@")) {
                        sb.append(
                                " UNION SELECT " + Data._ID +
                                " FROM " + Tables.DATA +
                                " WHERE +" + DataColumns.MIMETYPE_ID + "=");
                        sb.append(mDbHelper.get().getMimeTypeIdForEmail());
                        sb.append(" AND " + Data.RAW_CONTACT_ID + " IN " +
                                "(SELECT " + RawContactsColumns.CONCRETE_ID +
                                " FROM " + Tables.SEARCH_INDEX +
                                " JOIN " + Tables.RAW_CONTACTS +
                                " ON (" + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID
                                        + "=" + RawContactsColumns.CONCRETE_CONTACT_ID + ")" +
                                " WHERE " + SearchIndexColumns.NAME + " MATCH '");
                        final String ftsMatchQuery = SearchIndexManager.getFtsMatchQuery(
                                filterParam, FtsQueryBuilder.UNSCOPED_NORMALIZING);
                        sb.append(ftsMatchQuery);
                        sb.append("')");
                    }
                    sb.append(")");
                    qb.appendWhere(sb);
                }

                // Group by a unique email address on a per account basis, to make sure that
                // account promotion sort order correctly ranks email addresses that are in
                // multiple accounts
                groupBy = Email.DATA + "," + RawContacts.CONTACT_ID + "," +
                        RawContacts.ACCOUNT_NAME + "," + RawContacts.ACCOUNT_TYPE;
                if (sortOrder == null) {
                    final String accountPromotionSortOrder = getAccountPromotionSortOrder(uri);
                    if (!TextUtils.isEmpty(accountPromotionSortOrder)) {
                        sortOrder = accountPromotionSortOrder + ", " + EMAIL_FILTER_SORT_ORDER;
                    } else {
                        sortOrder = EMAIL_FILTER_SORT_ORDER;
                    }

                    final String primaryAccountName =
                            uri.getQueryParameter(ContactsContract.PRIMARY_ACCOUNT_NAME);
                    if (!TextUtils.isEmpty(primaryAccountName)) {
                        final int index = primaryAccountName.indexOf('@');
                        if (index != -1) {
                            // Purposely include '@' in matching.
                            final String domain = primaryAccountName.substring(index);
                            final char escapeChar = '\\';

                            final StringBuilder likeValue = new StringBuilder();
                            likeValue.append('%');
                            DbQueryUtils.escapeLikeValue(likeValue, domain, escapeChar);
                            selectionArgs = appendSelectionArg(selectionArgs, likeValue.toString());

                            // similar email domains is the last sort preference.
                            sortOrder += ", (CASE WHEN " + Data.DATA1 + " like ? ESCAPE '" +
                                    escapeChar + "' THEN 0 ELSE 1 END)";
                        }
                    }
                }
                break;
            }

            case CONTACTABLES:
            case CONTACTABLES_FILTER: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);

                String filterParam = null;

                final int uriPathSize = uri.getPathSegments().size();
                if (uriPathSize > 3) {
                    filterParam = uri.getLastPathSegment();
                    if (TextUtils.isEmpty(filterParam)) {
                        filterParam = null;
                    }
                }

                // CONTACTABLES_FILTER but no query provided, return an empty cursor
                if (uriPathSize > 2 && filterParam == null) {
                    qb.appendWhere(" AND 0");
                    break;
                }

                if (uri.getBooleanQueryParameter(Contactables.VISIBLE_CONTACTS_ONLY, false)) {
                    qb.appendWhere(" AND " + Data.CONTACT_ID + " in " +
                            Tables.DEFAULT_DIRECTORY);
                    }

                final StringBuilder sb = new StringBuilder();

                // we only want data items that are either email addresses or phone numbers
                sb.append(" AND (");
                sb.append(DataColumns.MIMETYPE_ID + " IN (");
                sb.append(mDbHelper.get().getMimeTypeIdForEmail());
                sb.append(",");
                sb.append(mDbHelper.get().getMimeTypeIdForPhone());
                sb.append("))");

                // Rest of the query is only relevant if we are handling CONTACTABLES_FILTER
                if (uriPathSize < 3) {
                    qb.appendWhere(sb);
                    break;
                }

                // but we want all the email addresses and phone numbers that belong to
                // all contacts that have any data items (or name) that match the query
                sb.append(" AND ");
                sb.append("(" + Data.CONTACT_ID + " IN (");

                // All contacts where the email address data1 column matches the query
                sb.append(
                        "SELECT " + RawContacts.CONTACT_ID +
                        " FROM " + Tables.DATA + " JOIN " + Tables.RAW_CONTACTS +
                        " ON " + Tables.DATA + "." + Data.RAW_CONTACT_ID + "=" +
                        Tables.RAW_CONTACTS + "." + RawContacts._ID +
                        " WHERE (" + DataColumns.MIMETYPE_ID + "=");
                sb.append(mDbHelper.get().getMimeTypeIdForEmail());

                sb.append(" AND " + Data.DATA1 + " LIKE ");
                DatabaseUtils.appendEscapedSQLString(sb, filterParam + '%');
                sb.append(")");

                // All contacts where the phone number matches the query (determined by checking
                // Tables.PHONE_LOOKUP
                final String number = PhoneNumberUtils.normalizeNumber(filterParam);
                if (!TextUtils.isEmpty(number)) {
                    sb.append("UNION SELECT DISTINCT " + RawContacts.CONTACT_ID +
                            " FROM " + Tables.PHONE_LOOKUP + " JOIN " + Tables.RAW_CONTACTS +
                            " ON (" + Tables.PHONE_LOOKUP + "." +
                            PhoneLookupColumns.RAW_CONTACT_ID + "=" +
                            Tables.RAW_CONTACTS + "." + RawContacts._ID + ")" +
                            " WHERE " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                    sb.append(number);
                    sb.append("%'");
                }

                // All contacts where the name matches the query (determined by checking
                // Tables.SEARCH_INDEX
                sb.append(
                        " UNION SELECT " + Data.CONTACT_ID +
                        " FROM " + Tables.DATA + " JOIN " + Tables.RAW_CONTACTS +
                        " ON " + Tables.DATA + "." + Data.RAW_CONTACT_ID + "=" +
                        Tables.RAW_CONTACTS + "." + RawContacts._ID +

                        " WHERE " + Data.RAW_CONTACT_ID + " IN " +

                        "(SELECT " + RawContactsColumns.CONCRETE_ID +
                        " FROM " + Tables.SEARCH_INDEX +
                        " JOIN " + Tables.RAW_CONTACTS +
                        " ON (" + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID
                        + "=" + RawContactsColumns.CONCRETE_CONTACT_ID + ")" +

                        " WHERE " + SearchIndexColumns.NAME + " MATCH '");

                final String ftsMatchQuery = SearchIndexManager.getFtsMatchQuery(
                        filterParam, FtsQueryBuilder.UNSCOPED_NORMALIZING);
                sb.append(ftsMatchQuery);
                sb.append("')");

                sb.append("))");
                qb.appendWhere(sb);

                break;
            }

            case POSTALS: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                        + mDbHelper.get().getMimeTypeIdForStructuredPostal());

                final boolean removeDuplicates = readBooleanQueryParameter(
                        uri, ContactsContract.REMOVE_DUPLICATE_ENTRIES, false);
                if (removeDuplicates) {
                    groupBy = RawContacts.CONTACT_ID + ", " + Data.DATA1;

                    // See PHONES for more detail.
                    addressBookIndexerCountExpression = "DISTINCT "
                            + RawContacts.CONTACT_ID + "||','||" + Data.DATA1;
                }
                break;
            }

            case POSTALS_ID: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(" AND " + DataColumns.MIMETYPE_ID + " = "
                        + mDbHelper.get().getMimeTypeIdForStructuredPostal());
                qb.appendWhere(" AND " + Data._ID + "=?");
                break;
            }

            case RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS: {
                setTablesAndProjectionMapForRawContacts(qb, uri);
                break;
            }

            case RAW_CONTACTS_ID:
            case PROFILE_RAW_CONTACTS_ID: {
                long rawContactId = ContentUris.parseId(uri);
                setTablesAndProjectionMapForRawContacts(qb, uri);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(" AND " + RawContacts._ID + "=?");
                break;
            }

            case RAW_CONTACTS_ID_DATA:
            case PROFILE_RAW_CONTACTS_ID_DATA: {
                int segment = match == RAW_CONTACTS_ID_DATA ? 1 : 2;
                long rawContactId = Long.parseLong(uri.getPathSegments().get(segment));
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(" AND " + Data.RAW_CONTACT_ID + "=?");
                break;
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForStreamItems(qb);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(StreamItems.RAW_CONTACT_ID + "=?");
                break;
            }

            case RAW_CONTACTS_ID_STREAM_ITEMS_ID: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                long streamItemId = Long.parseLong(uri.getPathSegments().get(3));
                setTablesAndProjectionMapForStreamItems(qb);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(streamItemId));
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(StreamItems.RAW_CONTACT_ID + "=? AND " +
                        StreamItems._ID + "=?");
                break;
            }

            case PROFILE_RAW_CONTACTS_ID_ENTITIES: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(2));
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                setTablesAndProjectionMapForRawEntities(qb, uri);
                qb.appendWhere(" AND " + RawContacts._ID + "=?");
                break;
            }

            case DATA:
            case PROFILE_DATA: {
                final String usageType = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
                final int typeInt = getDataUsageFeedbackType(usageType, USAGE_TYPE_ALL);
                setTablesAndProjectionMapForData(qb, uri, projection, false, typeInt);
                if (uri.getBooleanQueryParameter(Data.VISIBLE_CONTACTS_ONLY, false)) {
                    qb.appendWhere(" AND " + Data.CONTACT_ID + " in " +
                            Tables.DEFAULT_DIRECTORY);
                }
                break;
            }

            case DATA_ID:
            case PROFILE_DATA_ID: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(" AND " + Data._ID + "=?");
                break;
            }

            case PROFILE_PHOTO: {
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                qb.appendWhere(" AND " + Data._ID + "=" + Contacts.PHOTO_ID);
                break;
            }

            case PHONE_LOOKUP_ENTERPRISE: {
                if (uri.getPathSegments().size() != 2) {
                    throw new IllegalArgumentException("Phone number missing in URI: " + uri);
                }
                return queryPhoneLookupEnterprise(uri, projection, selection, selectionArgs,
                        sortOrder);
            }
            case PHONE_LOOKUP: {
                // Phone lookup cannot be combined with a selection
                selection = null;
                selectionArgs = null;
                if (uri.getBooleanQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false)) {
                    if (TextUtils.isEmpty(sortOrder)) {
                        // Default the sort order to something reasonable so we get consistent
                        // results when callers don't request an ordering
                        sortOrder = Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC";
                    }

                    String sipAddress = uri.getPathSegments().size() > 1
                            ? Uri.decode(uri.getLastPathSegment()) : "";
                    setTablesAndProjectionMapForData(qb, uri, null, false, true);
                    StringBuilder sb = new StringBuilder();
                    selectionArgs = mDbHelper.get().buildSipContactQuery(sb, sipAddress);
                    selection = sb.toString();
                } else {
                    if (TextUtils.isEmpty(sortOrder)) {
                        // Default the sort order to something reasonable so we get consistent
                        // results when callers don't request an ordering
                        sortOrder = " length(lookup.normalized_number) DESC";
                    }

                    String number =
                            uri.getPathSegments().size() > 1 ? uri.getLastPathSegment() : "";
                    String numberE164 = PhoneNumberUtils.formatNumberToE164(
                            number, mDbHelper.get().getCurrentCountryIso());
                    String normalizedNumber = PhoneNumberUtils.normalizeNumber(number);
                    mDbHelper.get().buildPhoneLookupAndContactQuery(
                            qb, normalizedNumber, numberE164);
                    qb.setProjectionMap(sPhoneLookupProjectionMap);

                    // removeNonStarMatchesFromCursor() requires the cursor to contain
                    // PhoneLookup.NUMBER. Therefore, if the projection explicitly omits it, extend
                    // the projection.
                    String[] projectionWithNumber = projection;
                    if (projection != null
                            && !ArrayUtils.contains(projection,PhoneLookup.NUMBER)) {
                        projectionWithNumber = ArrayUtils.appendElement(
                                String.class, projection, PhoneLookup.NUMBER);
                    }

                    // Peek at the results of the first query (which attempts to use fully
                    // normalized and internationalized numbers for comparison).  If no results
                    // were returned, fall back to using the SQLite function
                    // phone_number_compare_loose.
                    qb.setStrict(true);
                    boolean foundResult = false;
                    Cursor cursor = doQuery(db, qb, projectionWithNumber, selection, selectionArgs,
                            sortOrder, groupBy, null, limit, cancellationSignal);
                    try {
                        if (cursor.getCount() > 0) {
                            foundResult = true;
                            return PhoneLookupWithStarPrefix
                                    .removeNonStarMatchesFromCursor(number, cursor);
                        }

                        // Use the fall-back lookup method.
                        qb = new SQLiteQueryBuilder();
                        qb.setProjectionMap(sPhoneLookupProjectionMap);
                        qb.setStrict(true);

                        // use the raw number instead of the normalized number because
                        // phone_number_compare_loose in SQLite works only with non-normalized
                        // numbers
                        mDbHelper.get().buildFallbackPhoneLookupAndContactQuery(qb, number);

                        final Cursor fallbackCursor = doQuery(db, qb, projectionWithNumber,
                                selection, selectionArgs, sortOrder, groupBy, having, limit,
                                cancellationSignal);
                        return PhoneLookupWithStarPrefix.removeNonStarMatchesFromCursor(
                                number, fallbackCursor);
                    } finally {
                        if (!foundResult) {
                            // We'll be returning a different cursor, so close this one.
                            cursor.close();
                        }
                    }
                }
                break;
            }

            case GROUPS: {
                qb.setTables(Views.GROUPS);
                qb.setProjectionMap(sGroupsProjectionMap);
                appendAccountIdFromParameter(qb, uri);
                break;
            }

            case GROUPS_ID: {
                qb.setTables(Views.GROUPS);
                qb.setProjectionMap(sGroupsProjectionMap);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(Groups._ID + "=?");
                break;
            }

            case GROUPS_SUMMARY: {
                String tables = Views.GROUPS + " AS " + Tables.GROUPS;
                if (ContactsDatabaseHelper.isInProjection(projection, Groups.SUMMARY_COUNT)) {
                    tables = tables + Joins.GROUP_MEMBER_COUNT;
                }
                if (ContactsDatabaseHelper.isInProjection(
                        projection, Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT)) {
                    // TODO Add join for this column too (and update the projection map)
                    // TODO Also remove Groups.PARAM_RETURN_GROUP_COUNT_PER_ACCOUNT when it works.
                    Log.w(TAG, Groups.SUMMARY_GROUP_COUNT_PER_ACCOUNT + " is not supported yet");
                }
                qb.setTables(tables);
                qb.setProjectionMap(sGroupsSummaryProjectionMap);
                appendAccountIdFromParameter(qb, uri);
                groupBy = GroupsColumns.CONCRETE_ID;
                break;
            }

            case AGGREGATION_EXCEPTIONS: {
                qb.setTables(Tables.AGGREGATION_EXCEPTIONS);
                qb.setProjectionMap(sAggregationExceptionsProjectionMap);
                break;
            }

            case AGGREGATION_SUGGESTIONS: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                String filter = null;
                if (uri.getPathSegments().size() > 3) {
                    filter = uri.getPathSegments().get(3);
                }
                final int maxSuggestions;
                if (limit != null) {
                    maxSuggestions = Integer.parseInt(limit);
                } else {
                    maxSuggestions = DEFAULT_MAX_SUGGESTIONS;
                }

                ArrayList<AggregationSuggestionParameter> parameters = null;
                List<String> query = uri.getQueryParameters("query");
                if (query != null && !query.isEmpty()) {
                    parameters = new ArrayList<AggregationSuggestionParameter>(query.size());
                    for (String parameter : query) {
                        int offset = parameter.indexOf(':');
                        parameters.add(offset == -1
                                ? new AggregationSuggestionParameter(
                                        AggregationSuggestions.PARAMETER_MATCH_NAME,
                                        parameter)
                                : new AggregationSuggestionParameter(
                                        parameter.substring(0, offset),
                                        parameter.substring(offset + 1)));
                    }
                }

                setTablesAndProjectionMapForContacts(qb, projection);

                /**
                 * SPRD: bug423722, the local contacts and sim card contacts should not be merged
                 * Original Android code:
                return mAggregator.get().queryAggregationSuggestions(qb, projection, contactId,
                        maxSuggestions, filter, parameters);
                 * @{
                 */
                return mAggregator.get().queryAggregationSuggestions(qb, projection, contactId,
                        maxSuggestions, filter, parameters, selection);
                /**
                 * @}
                 */
            }

            case SETTINGS: {
                qb.setTables(Tables.SETTINGS);
                qb.setProjectionMap(sSettingsProjectionMap);
                appendAccountFromParameter(qb, uri);

                // When requesting specific columns, this query requires
                // late-binding of the GroupMembership MIME-type.
                final String groupMembershipMimetypeId = Long.toString(mDbHelper.get()
                        .getMimeTypeId(GroupMembership.CONTENT_ITEM_TYPE));
                if (projection != null && projection.length != 0 &&
                        ContactsDatabaseHelper.isInProjection(
                                projection, Settings.UNGROUPED_COUNT)) {
                    selectionArgs = insertSelectionArg(selectionArgs, groupMembershipMimetypeId);
                }
                if (projection != null && projection.length != 0 &&
                        ContactsDatabaseHelper.isInProjection(
                                projection, Settings.UNGROUPED_WITH_PHONES)) {
                    selectionArgs = insertSelectionArg(selectionArgs, groupMembershipMimetypeId);
                }

                break;
            }

            case STATUS_UPDATES:
            case PROFILE_STATUS_UPDATES: {
                setTableAndProjectionMapForStatusUpdates(qb, projection);
                break;
            }

            case STATUS_UPDATES_ID: {
                setTableAndProjectionMapForStatusUpdates(qb, projection);
                selectionArgs = insertSelectionArg(selectionArgs, uri.getLastPathSegment());
                qb.appendWhere(DataColumns.CONCRETE_ID + "=?");
                break;
            }

            case SEARCH_SUGGESTIONS: {
                return mGlobalSearchSupport.handleSearchSuggestionsQuery(
                        db, uri, projection, limit, cancellationSignal);
            }

            case SEARCH_SHORTCUT: {
                String lookupKey = uri.getLastPathSegment();
                String filter = getQueryParameter(
                        uri, SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA);
                return mGlobalSearchSupport.handleSearchShortcutRefresh(
                        db, projection, lookupKey, filter, cancellationSignal);
            }

            case RAW_CONTACT_ENTITIES:
            case PROFILE_RAW_CONTACT_ENTITIES: {
                setTablesAndProjectionMapForRawEntities(qb, uri);
                break;
            }
            case RAW_CONTACT_ENTITIES_CORP: {
                ContactsPermissions.enforceCallingOrSelfPermission(getContext(),
                        INTERACT_ACROSS_USERS);
                final int corpUserId = UserUtils.getCorpUserId(getContext(), false);
                if (corpUserId < 0) {
                    // No Corp user or policy not allowed, return empty cursor
                    final String[] outputProjection = (projection != null) ? projection
                            : sRawEntityProjectionMap.getColumnNames();
                    return new MatrixCursor(outputProjection);
                }
                final Uri remoteUri = maybeAddUserId(RawContactsEntity.CONTENT_URI, corpUserId);
                return getContext().getContentResolver().query(remoteUri, projection, selection,
                        selectionArgs, sortOrder);
            }

            case RAW_CONTACT_ID_ENTITY: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                setTablesAndProjectionMapForRawEntities(qb, uri);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(rawContactId));
                qb.appendWhere(" AND " + RawContacts._ID + "=?");
                break;
            }

            case PROVIDER_STATUS: {
                final int providerStatus;
                if (mProviderStatus == STATUS_UPGRADING
                        || mProviderStatus == STATUS_CHANGING_LOCALE) {
                    providerStatus = ProviderStatus.STATUS_BUSY;
                } else if (mProviderStatus == STATUS_NORMAL) {
                    providerStatus = ProviderStatus.STATUS_NORMAL;
                } else {
                    providerStatus = ProviderStatus.STATUS_EMPTY;
                }
                return buildSingleRowResult(projection,
                        new String[] {ProviderStatus.STATUS},
                        new Object[] {providerStatus});
            }

            case DIRECTORIES : {
                qb.setTables(Tables.DIRECTORIES);
                qb.setProjectionMap(sDirectoryProjectionMap);
                break;
            }

            case DIRECTORIES_ID : {
                long id = ContentUris.parseId(uri);
                qb.setTables(Tables.DIRECTORIES);
                qb.setProjectionMap(sDirectoryProjectionMap);
                selectionArgs = insertSelectionArg(selectionArgs, String.valueOf(id));
                qb.appendWhere(Directory._ID + "=?");
                break;
            }

            case COMPLETE_NAME: {
                return completeName(uri, projection);
            }

            case DELETED_CONTACTS: {
                qb.setTables(Tables.DELETED_CONTACTS);
                qb.setProjectionMap(sDeletedContactsProjectionMap);
                break;
            }

            case DELETED_CONTACTS_ID: {
                String id = uri.getLastPathSegment();
                qb.setTables(Tables.DELETED_CONTACTS);
                qb.setProjectionMap(sDeletedContactsProjectionMap);
                qb.appendWhere(DeletedContacts.CONTACT_ID + "=?");
                selectionArgs = insertSelectionArg(selectionArgs, id);
                break;
            }

            /**
             * SPRD: Add by SPRD for cleaning up contacts Bug474742
             *
             * @{
             */
            case DATA_DEDUPLICATE_CANDIDATES: {
                final String mimeTypeIsPhoneExpression = "mimetype_id = "
                        + mDbHelper.get().getMimeTypeIdForPhone();
                // bug 370511 begin
                final String mimeTypeIsNotPhoneExpression = "mimetype_id != "
                        + mDbHelper.get().getMimeTypeIdForPhone();
                final String mimeTypeExpression = "CASE WHEN (" + Data.HAS_PHONE_NUMBER
                        + "=1) THEN " + mimeTypeIsPhoneExpression// bug 369623
                        + " ELSE " + mimeTypeIsNotPhoneExpression + " END";
                // bug 370511 end
                final String userProfileExpression = "raw_contact_is_user_profile = 0";
                final String rawContactIdExpression1 = "select _id from raw_contacts where deleted = '0' AND display_name_source ='40' AND raw_contact_is_read_only = 0 group by display_name,account_id having count(_id)<2";
                /**
                 * SPRD:Bug 523044/535472 The DUT shows abnormal list menu while merging
                 * the contacts of google account.
                 * @{
                 * original:
                final String rawContactIdExpression2 = "select _id from view_raw_contacts where deleted = '1' or display_name_source!=40 or  account_type like 'sprd.%sim%'";

                 */
                final String rawContactIdExpression2 = "select _id from view_raw_contacts where "
                        + "deleted = '1'or display_name_source!=40 or account_type like 'sprd.%sim%' "
                        + "or account_name like '%gmail.com%'";
                /*
                 * @}
                 */
                StringBuilder sb = new StringBuilder();

                sb.append(mimeTypeExpression);
                sb.append(" and ");
                sb.append(userProfileExpression);
                sb.append(" and ");
                sb.append(Data.IS_READ_ONLY + "=0");
                sb.append(" and ");
                sb.append(Data.RAW_CONTACT_ID + " not in ("
                        + rawContactIdExpression1 + ")");
                sb.append(" and ");
                sb.append(Data.RAW_CONTACT_ID + " not in("
                        + rawContactIdExpression2 + ")");

                String whereExpression = sb.toString();

                qb.setTables(Views.DATA);
                qb.setProjectionMap(sDeduplicatioProjectMap);
                qb.appendWhere(whereExpression);
                groupBy = Data.RAW_CONTACT_ID;
                break;
            }
            /**
             * @}
             */
            default:
                return mLegacyApiSupport.query(
                        uri, projection, selection, selectionArgs, sortOrder, limit);
        }

        qb.setStrict(true);

        // Auto-rewrite SORT_KEY_{PRIMARY, ALTERNATIVE} sort orders.
        String localizedSortOrder = getLocalizedSortOrder(sortOrder);
        Cursor cursor =
                doQuery(db, qb, projection, selection, selectionArgs, localizedSortOrder, groupBy,
                        having, limit, cancellationSignal);

        if (readBooleanQueryParameter(uri, Contacts.EXTRA_ADDRESS_BOOK_INDEX, false)) {
            bundleFastScrollingIndexExtras(cursor, uri, db, qb, selection,
                    selectionArgs, sortOrder, addressBookIndexerCountExpression,
                    cancellationSignal);
        }
        if (snippetDeferred) {
            cursor = addDeferredSnippetingExtra(cursor);
        }

        return cursor;
    }

    // Rewrites query sort orders using SORT_KEY_{PRIMARY, ALTERNATIVE}
    // to use PHONEBOOK_BUCKET_{PRIMARY, ALTERNATIVE} as primary key; all
    // other sort orders are returned unchanged. Preserves ordering
    // (eg 'DESC') if present.
    protected static String getLocalizedSortOrder(String sortOrder) {
        String localizedSortOrder = sortOrder;
        if (sortOrder != null) {
            String sortKey;
            String sortOrderSuffix = "";
            int spaceIndex = sortOrder.indexOf(' ');
            if (spaceIndex != -1) {
                sortKey = sortOrder.substring(0, spaceIndex);
                sortOrderSuffix = sortOrder.substring(spaceIndex);
            } else {
                sortKey = sortOrder;
            }
            if (TextUtils.equals(sortKey, Contacts.SORT_KEY_PRIMARY)) {
                localizedSortOrder = ContactsColumns.PHONEBOOK_BUCKET_PRIMARY
                    + sortOrderSuffix + ", " + sortOrder;
            } else if (TextUtils.equals(sortKey, Contacts.SORT_KEY_ALTERNATIVE)) {
                localizedSortOrder = ContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE
                    + sortOrderSuffix + ", " + sortOrder;
            }
        }
        return localizedSortOrder;
    }

    private Cursor doQuery(final SQLiteDatabase db, SQLiteQueryBuilder qb, String[] projection,
            String selection, String[] selectionArgs, String sortOrder, String groupBy,
            String having, String limit, CancellationSignal cancellationSignal) {
        if (projection != null && projection.length == 1
                && BaseColumns._COUNT.equals(projection[0])) {
            qb.setProjectionMap(sCountProjectionMap);
        }
        final Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, having,
                sortOrder, limit, cancellationSignal);
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), ContactsContract.AUTHORITY_URI);
        }
        return c;
    }

    private static class EnterprisePhoneCursorWrapper extends CursorWrapper {

        public EnterprisePhoneCursorWrapper(Cursor cursor) {
            super(cursor);
        }

        @Override
        public int getInt(int column) {
            return (int) getLong(column);
        }

        @Override
        public long getLong(int column) {
            long result = super.getLong(column);
            String columnName = getColumnName(column);
            // We change contactId only for now
            switch (columnName) {
                case Phone.CONTACT_ID:
                    return result + Contacts.ENTERPRISE_CONTACT_ID_BASE;
                default:
                    return result;
            }
        }
    }

    /**
     * Handles {@link Phone#ENTERPRISE_CONTENT_URI}.
     */
    private Cursor queryMergedDataPhones(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final List<String> pathSegments = uri.getPathSegments();
        final int pathSegmentsSize = pathSegments.size();
        // Ignore the first 2 path segments: "/data_enterprise/phones"
        final StringBuilder newPathBuilder = new StringBuilder(Phone.CONTENT_URI.getPath());
        for (int i = 2; i < pathSegmentsSize; i++) {
            newPathBuilder.append('/');
            newPathBuilder.append(pathSegments.get(i));
        }
        // Change /data_enterprise/phones/... to /data/phones/...
        final Uri localUri = uri.buildUpon().path(newPathBuilder.toString()).build();
        final String directory = getQueryParameter(uri, ContactsContract.DIRECTORY_PARAM_KEY);
        final long directoryId =
                (directory == null ? -1 :
                (directory.equals("0") ? Directory.DEFAULT :
                (directory.equals("1") ? Directory.LOCAL_INVISIBLE : Long.MIN_VALUE)));
        final Cursor primaryCursor = queryLocal(localUri, projection, selection, selectionArgs,
                sortOrder, directoryId, null);
        try {
            final int corpUserId = UserUtils.getCorpUserId(getContext(), false);
            if (corpUserId < 0) {
                // No Corp user or policy not allowed
                return primaryCursor;
            }
            final Uri remoteUri = maybeAddUserId(localUri, corpUserId);
            final Cursor managedCursor = getContext().getContentResolver().query(remoteUri,
                    projection, selection, selectionArgs, sortOrder, null);
            if (managedCursor == null) {
                // No corp results.  Just return the local result.
                return primaryCursor;
            }
            final Cursor[] cursorArray = new Cursor[] {
                    primaryCursor, new EnterprisePhoneCursorWrapper(managedCursor)
            };
            // Sort order is not supported yet, will be fixed in M when we have
            // merged provider
            // MergeCursor will copy all the contacts from two cursors, which may
            // cause OOM if there's a lot of contacts. But it's only used by
            // Bluetooth, and Bluetooth will loop through the Cursor and put all
            // content in ArrayList anyway, so we ignore OOM issue here for now
            final MergeCursor mergeCursor = new MergeCursor(cursorArray);
            return mergeCursor;
        } catch (Throwable th) {
            if (primaryCursor != null) {
                primaryCursor.close();
            }
            throw th;
        }
    }

    private Cursor queryEnterpriseIfNecessary(Uri localUri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, String contactIdColumnName) {

        final int corpUserId = UserUtils.getCorpUserId(getContext(), true);

        // Step 1. Look at the database on the current profile.
        if (VERBOSE_LOGGING) {
            Log.v(TAG, "queryPhoneLookupEnterprise: local query URI=" + localUri);
        }
        final Cursor local = queryLocal(localUri, projection, selection, selectionArgs,
                sortOrder, /* directory */ 0, /* cancellationsignal */null);
        try {
            if (VERBOSE_LOGGING) {
                MoreDatabaseUtils.dumpCursor(TAG, "local", local);
            }

            // If we found a result or there's no corp profile, just return it as-is.
            if (local.getCount() > 0 || corpUserId < 0) {
                return local;
            }
        } catch (Throwable th) { // If something throws, close the cursor.
            local.close();
            throw th;
        }
        // "local" is still open.  If we fail the managed CP2 query, we'll still return it.

        // Step 2.  No rows found in the local db, and there is a corp profile. Look at the corp
        // DB.

        // Add the user-id to the URI, like "content://USER@com.android.contacts/...".
        final Uri remoteUri = maybeAddUserId(localUri, corpUserId);

        if (VERBOSE_LOGGING) {
            Log.v(TAG, "queryPhoneLookupEnterprise: corp query URI=" + remoteUri);
        }
        // Note in order to re-write the cursor correctly, we need all columns from the corp cp2.
        final Cursor corp = getContext().getContentResolver().query(remoteUri, null,
                selection, selectionArgs, sortOrder, /* cancellationsignal */null);
        if (corp == null) {
            return local;
        }
        try {
            local.close();
            if (VERBOSE_LOGGING) {
                MoreDatabaseUtils.dumpCursor(TAG, "corp raw", corp);
            }
            final Cursor rewritten = rewriteCorpLookup(
                    (projection != null ? projection : corp.getColumnNames()), corp,
                    contactIdColumnName);
            if (VERBOSE_LOGGING) {
                MoreDatabaseUtils.dumpCursor(TAG, "corp rewritten", rewritten);
            }
            return rewritten;
        } finally {
            // Always close the corp cursor; as we just return the rewritten one.
            corp.close();
        }
    }

    /**
     * Handles {@link PhoneLookup#ENTERPRISE_CONTENT_FILTER_URI}.
     */
    // TODO Test
    private Cursor queryPhoneLookupEnterprise(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final String phoneNumber = Uri.decode(uri.getLastPathSegment());
        final boolean isSipAddress = uri.getBooleanQueryParameter(
                PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, false);
        final Uri localUri = PhoneLookup.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath(phoneNumber)
                .appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS,
                        String.valueOf(isSipAddress)).build();
        return queryEnterpriseIfNecessary(localUri, projection, null, null, null,
                isSipAddress ? Data.CONTACT_ID : PhoneLookup._ID);
    }

    /**
     * Handles {@link Email#ENTERPRISE_CONTENT_LOOKUP_URI}.
     */
    private Cursor queryEmailsLookupEnterprise(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        final List<String> pathSegments = uri.getPathSegments();
        final int pathSegmentsSize = pathSegments.size();
        // Ignore the first 3 path segments: "/data/emails_enterprise/lookup"
        final StringBuilder newPathBuilder = new StringBuilder(Email.CONTENT_LOOKUP_URI.getPath());
        for (int i = 3; i < pathSegmentsSize; i++) {
            newPathBuilder.append('/');
            newPathBuilder.append(pathSegments.get(i));
        }
        final Uri localUri = uri.buildUpon().path(newPathBuilder.toString()).build();
        return queryEnterpriseIfNecessary(localUri, projection, selection, selectionArgs,
                sortOrder, Data.CONTACT_ID);
    }

    /**
     * Rewrite a cursor from the corp profile data
     */
    @VisibleForTesting
    static Cursor rewriteCorpLookup(String[] projection, Cursor original,
            String contactIdColumnName) {
        final MatrixCursor ret = new MatrixCursor(projection);
        original.moveToPosition(-1);
        while (original.moveToNext()) {
            final int contactId = original.getInt(original.getColumnIndex(contactIdColumnName));
            final MatrixCursor.RowBuilder builder = ret.newRow();
            for (int i = 0; i < projection.length; i++) {
                final String outputColumnName = projection[i];
                final int originalColumnIndex = original.getColumnIndex(outputColumnName);
                switch (outputColumnName) {
                    // Set artificial photo URLs using Contacts.CORP_CONTENT_URI.
                    case Contacts.PHOTO_THUMBNAIL_URI:
                        builder.add(getCorpThumbnailUri(contactId, original));
                        break;
                    case Contacts.PHOTO_URI:
                        builder.add(getCorpDisplayPhotoUri(contactId, original));
                        break;
                    case Data.PHOTO_FILE_ID:
                    case Data.PHOTO_ID:
                        builder.add(null);
                        break;
                    case Data.CUSTOM_RINGTONE:
                        String ringtoneUri = original.getString(originalColumnIndex);
                        // TODO: Remove this conditional block once accessing sounds in corp
                        // profile becomes possible.
                        if (ringtoneUri != null
                                    && !Uri.parse(ringtoneUri).isPathPrefixMatch(
                                            MediaStore.Audio.Media.INTERNAL_CONTENT_URI)) {
                            ringtoneUri = null;
                        }
                        builder.add(ringtoneUri);
                        break;
                    case Contacts.LOOKUP_KEY:
                        final String lookupKey = original.getString(originalColumnIndex);
                        if (TextUtils.isEmpty(lookupKey)) {
                            builder.add(null);
                        } else {
                            builder.add(Contacts.ENTERPRISE_CONTACT_LOOKUP_PREFIX + lookupKey);
                        }
                        break;
                    default:
                        if (outputColumnName.equals(contactIdColumnName)) {
                            // This will be _id if it's PhoneLookup, contacts_id
                            // if it's Data.CONTACT_ID
                            builder.add(original.getLong(originalColumnIndex)
                                    + Contacts.ENTERPRISE_CONTACT_ID_BASE);
                            break;
                        }
                        // Copy the original value.
                        switch (original.getType(originalColumnIndex)) {
                            case Cursor.FIELD_TYPE_NULL:
                                builder.add(null);
                                break;
                            case Cursor.FIELD_TYPE_INTEGER:
                                builder.add(original.getLong(originalColumnIndex));
                                break;
                            case Cursor.FIELD_TYPE_FLOAT:
                                builder.add(original.getFloat(originalColumnIndex));
                                break;
                            case Cursor.FIELD_TYPE_STRING:
                                builder.add(original.getString(originalColumnIndex));
                                break;
                            case Cursor.FIELD_TYPE_BLOB:
                                builder.add(original.getBlob(originalColumnIndex));
                                break;
                        }
                }
            }
        }
        return ret;
    }

    /**
     * Generate a photo URI for {@link PhoneLookup#PHOTO_THUMBNAIL_URI}.
     *
     * Example: "content://USER@com.android.contacts/contacts/ID/photo"
     *
     * {@link #openAssetFile} knows how to fetch from this URI.
     */
    private static String getCorpThumbnailUri(long contactId, Cursor originalCursor) {
        // First, check if the contact has a thumbnail.
        if (originalCursor.isNull(
                originalCursor.getColumnIndex(PhoneLookup.PHOTO_THUMBNAIL_URI))) {
            // No thumbnail.  Just return null.
            return null;
        }
        return ContentUris.appendId(Contacts.CORP_CONTENT_URI.buildUpon(), contactId)
                .appendPath(Contacts.Photo.CONTENT_DIRECTORY).build().toString();
    }

    /**
     * Generate a photo URI for {@link PhoneLookup#PHOTO_URI}.
     *
     * Example: "content://USER@com.android.contacts/contacts/ID/display_photo"
     *
     * {@link #openAssetFile} knows how to fetch from this URI.
     */
    private static String getCorpDisplayPhotoUri(long contactId, Cursor originalCursor) {
        // First, check if the contact has a display photo.
        if (originalCursor.isNull(
                originalCursor.getColumnIndex(PhoneLookup.PHOTO_FILE_ID))) {
            // No display photo, fall-back to thumbnail.
            return getCorpThumbnailUri(contactId, originalCursor);
        }
        return ContentUris.appendId(Contacts.CORP_CONTENT_URI.buildUpon(), contactId)
                .appendPath(Contacts.Photo.DISPLAY_PHOTO).build().toString();
    }

    /**
     * Runs the query with the supplied contact ID and lookup ID.  If the query succeeds,
     * it returns the resulting cursor, otherwise it returns null and the calling
     * method needs to resolve the lookup key and rerun the query.
     * @param cancellationSignal
     */
    private Cursor queryWithContactIdAndLookupKey(SQLiteQueryBuilder lookupQb,
            SQLiteDatabase db,
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder, String groupBy, String limit,
            String contactIdColumn, long contactId, String lookupKeyColumn, String lookupKey,
            CancellationSignal cancellationSignal) {

        String[] args;
        if (selectionArgs == null) {
            args = new String[2];
        } else {
            args = new String[selectionArgs.length + 2];
            System.arraycopy(selectionArgs, 0, args, 2, selectionArgs.length);
        }
        args[0] = String.valueOf(contactId);
        args[1] = Uri.encode(lookupKey);
        lookupQb.appendWhere(contactIdColumn + "=? AND " + lookupKeyColumn + "=?");
        Cursor c = doQuery(db, lookupQb, projection, selection, args, sortOrder,
                groupBy, null, limit, cancellationSignal);
        if (c.getCount() != 0) {
            return c;
        }

        c.close();
        return null;
    }

    private void invalidateFastScrollingIndexCache() {
        // FastScrollingIndexCache is thread-safe, no need to synchronize here.
        mFastScrollingIndexCache.invalidate();
    }

    /**
     * Add the "fast scrolling index" bundle, generated by {@link #getFastScrollingIndexExtras},
     * to a cursor as extras.  It first checks {@link FastScrollingIndexCache} to see if we
     * already have a cached result.
     */
    private void bundleFastScrollingIndexExtras(Cursor cursor, Uri queryUri,
            final SQLiteDatabase db, SQLiteQueryBuilder qb, String selection,
            String[] selectionArgs, String sortOrder, String countExpression,
            CancellationSignal cancellationSignal) {

        if (!(cursor instanceof AbstractCursor)) {
            Log.w(TAG, "Unable to bundle extras.  Cursor is not AbstractCursor.");
            return;
        }
        Bundle b;
        // Note even though FastScrollingIndexCache is thread-safe, we really need to put the
        // put-get pair in a single synchronized block, so that even if multiple-threads request the
        // same index at the same time (which actually happens on the phone app) we only execute
        // the query once.
        //
        // This doesn't cause deadlock, because only reader threads get here but not writer
        // threads.  (Writer threads may call invalidateFastScrollingIndexCache(), but it doesn't
        // synchronize on mFastScrollingIndexCache)
        //
        // All reader and writer threads share the single lock object internally in
        // FastScrollingIndexCache, but the lock scope is limited within each put(), get() and
        // invalidate() call, so it won't deadlock.

        // Synchronizing on a non-static field is generally not a good idea, but nobody should
        // modify mFastScrollingIndexCache once initialized, and it shouldn't be null at this point.
        synchronized (mFastScrollingIndexCache) {
            // First, try the cache.
            mFastScrollingIndexCacheRequestCount++;
            b = mFastScrollingIndexCache.get(
                    queryUri, selection, selectionArgs, sortOrder, countExpression);

            if (b == null) {
                mFastScrollingIndexCacheMissCount++;
                // Not in the cache.  Generate and put.
                final long start = System.currentTimeMillis();

                b = getFastScrollingIndexExtras(db, qb, selection, selectionArgs,
                        sortOrder, countExpression, cancellationSignal);

                final long end = System.currentTimeMillis();
                final int time = (int) (end - start);
                mTotalTimeFastScrollingIndexGenerate += time;
                if (VERBOSE_LOGGING) {
                    Log.v(TAG, "getLetterCountExtraBundle took " + time + "ms");
                }
                mFastScrollingIndexCache.put(queryUri, selection, selectionArgs, sortOrder,
                        countExpression, b);
            }
        }
        ((AbstractCursor) cursor).setExtras(b);
    }

    private static final class AddressBookIndexQuery {
        public static final String NAME = "name";
        public static final String BUCKET = "bucket";
        public static final String LABEL = "label";
        public static final String COUNT = "count";

        public static final String[] COLUMNS = new String[] {
            NAME, BUCKET, LABEL, COUNT
        };

        public static final int COLUMN_NAME = 0;
        public static final int COLUMN_BUCKET = 1;
        public static final int COLUMN_LABEL = 2;
        public static final int COLUMN_COUNT = 3;

        public static final String GROUP_BY = BUCKET + ", " + LABEL;
        public static final String ORDER_BY =
            BUCKET + ", " +  NAME + " COLLATE " + PHONEBOOK_COLLATOR_NAME;
    }

    /**
     * Computes counts by the address book index labels and returns it as {@link Bundle} which
     * will be appended to a {@link Cursor} as extras.
     */
    private static Bundle getFastScrollingIndexExtras(final SQLiteDatabase db,
            final SQLiteQueryBuilder qb, final String selection, final String[] selectionArgs,
            final String sortOrder, String countExpression,
            final CancellationSignal cancellationSignal) {
        String sortKey;

        // The sort order suffix could be something like "DESC".
        // We want to preserve it in the query even though we will change
        // the sort column itself.
        String sortOrderSuffix = "";
        if (sortOrder != null) {
            int spaceIndex = sortOrder.indexOf(' ');
            if (spaceIndex != -1) {
                sortKey = sortOrder.substring(0, spaceIndex);
                sortOrderSuffix = sortOrder.substring(spaceIndex);
            } else {
                sortKey = sortOrder;
            }
        } else {
            sortKey = Contacts.SORT_KEY_PRIMARY;
        }

        String bucketKey;
        String labelKey;
        if (TextUtils.equals(sortKey, Contacts.SORT_KEY_PRIMARY)) {
            bucketKey = ContactsColumns.PHONEBOOK_BUCKET_PRIMARY;
            labelKey = ContactsColumns.PHONEBOOK_LABEL_PRIMARY;
        } else if (TextUtils.equals(sortKey, Contacts.SORT_KEY_ALTERNATIVE)) {
            bucketKey = ContactsColumns.PHONEBOOK_BUCKET_ALTERNATIVE;
            labelKey = ContactsColumns.PHONEBOOK_LABEL_ALTERNATIVE;
        } else {
            return null;
        }

        HashMap<String, String> projectionMap = Maps.newHashMap();
        projectionMap.put(AddressBookIndexQuery.NAME,
                sortKey + " AS " + AddressBookIndexQuery.NAME);
        projectionMap.put(AddressBookIndexQuery.BUCKET,
                bucketKey + " AS " + AddressBookIndexQuery.BUCKET);
        projectionMap.put(AddressBookIndexQuery.LABEL,
                labelKey + " AS " + AddressBookIndexQuery.LABEL);

        // If "what to count" is not specified, we just count all records.
        if (TextUtils.isEmpty(countExpression)) {
            countExpression = "*";
        }

        projectionMap.put(AddressBookIndexQuery.COUNT,
                "COUNT(" + countExpression + ") AS " + AddressBookIndexQuery.COUNT);
        qb.setProjectionMap(projectionMap);
        String orderBy = AddressBookIndexQuery.BUCKET + sortOrderSuffix
            + ", " + AddressBookIndexQuery.NAME + " COLLATE "
            + PHONEBOOK_COLLATOR_NAME + sortOrderSuffix;

        Cursor indexCursor = qb.query(db, AddressBookIndexQuery.COLUMNS, selection, selectionArgs,
                AddressBookIndexQuery.GROUP_BY, null /* having */,
                orderBy, null, cancellationSignal);

        try {
            int numLabels = indexCursor.getCount();
            String labels[] = new String[numLabels];
            int counts[] = new int[numLabels];

            for (int i = 0; i < numLabels; i++) {
                indexCursor.moveToNext();
                labels[i] = indexCursor.getString(AddressBookIndexQuery.COLUMN_LABEL);
                counts[i] = indexCursor.getInt(AddressBookIndexQuery.COLUMN_COUNT);
            }

            return FastScrollingIndexCache.buildExtraBundle(labels, counts);
        } finally {
            indexCursor.close();
        }
    }

    /**
     * Returns the contact Id for the contact identified by the lookupKey.
     * Robust against changes in the lookup key: if the key has changed, will
     * look up the contact by the raw contact IDs or name encoded in the lookup
     * key.
     */
    public long lookupContactIdByLookupKey(SQLiteDatabase db, String lookupKey) {
        ContactLookupKey key = new ContactLookupKey();
        ArrayList<LookupKeySegment> segments = key.parse(lookupKey);

        long contactId = -1;
        if (lookupKeyContainsType(segments, ContactLookupKey.LOOKUP_TYPE_PROFILE)) {
            // We should already be in a profile database context, so just look up a single contact.
           contactId = lookupSingleContactId(db);
        }

        if (lookupKeyContainsType(segments, ContactLookupKey.LOOKUP_TYPE_SOURCE_ID)) {
            contactId = lookupContactIdBySourceIds(db, segments);
            if (contactId != -1) {
                return contactId;
            }
        }

        boolean hasRawContactIds =
                lookupKeyContainsType(segments, ContactLookupKey.LOOKUP_TYPE_RAW_CONTACT_ID);
        if (hasRawContactIds) {
            contactId = lookupContactIdByRawContactIds(db, segments);
            if (contactId != -1) {
                return contactId;
            }
        }

        if (hasRawContactIds
                || lookupKeyContainsType(segments, ContactLookupKey.LOOKUP_TYPE_DISPLAY_NAME)) {
            contactId = lookupContactIdByDisplayNames(db, segments);
        }

        return contactId;
    }

    private long lookupSingleContactId(SQLiteDatabase db) {
        Cursor c = db.query(
                Tables.CONTACTS, new String[] {Contacts._ID}, null, null, null, null, null, "1");
        try {
            if (c.moveToFirst()) {
                return c.getLong(0);
            }
            return -1;
        } finally {
            c.close();
        }
    }

    private interface LookupBySourceIdQuery {
        String TABLE = Views.RAW_CONTACTS;
        String COLUMNS[] = {
                RawContacts.CONTACT_ID,
                RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                RawContacts.ACCOUNT_NAME,
                RawContacts.SOURCE_ID
        };

        int CONTACT_ID = 0;
        int ACCOUNT_TYPE_AND_DATA_SET = 1;
        int ACCOUNT_NAME = 2;
        int SOURCE_ID = 3;
    }

    private long lookupContactIdBySourceIds(
            SQLiteDatabase db, ArrayList<LookupKeySegment> segments) {

        StringBuilder sb = new StringBuilder();
        sb.append(RawContacts.SOURCE_ID + " IN (");
        for (LookupKeySegment segment : segments) {
            if (segment.lookupType == ContactLookupKey.LOOKUP_TYPE_SOURCE_ID) {
                DatabaseUtils.appendEscapedSQLString(sb, segment.key);
                sb.append(",");
            }
        }
        sb.setLength(sb.length() - 1);  // Last comma.
        sb.append(") AND " + RawContacts.CONTACT_ID + " NOT NULL");

        Cursor c = db.query(LookupBySourceIdQuery.TABLE, LookupBySourceIdQuery.COLUMNS,
                 sb.toString(), null, null, null, null);
        try {
            while (c.moveToNext()) {
                String accountTypeAndDataSet =
                        c.getString(LookupBySourceIdQuery.ACCOUNT_TYPE_AND_DATA_SET);
                String accountName = c.getString(LookupBySourceIdQuery.ACCOUNT_NAME);
                int accountHashCode =
                        ContactLookupKey.getAccountHashCode(accountTypeAndDataSet, accountName);
                String sourceId = c.getString(LookupBySourceIdQuery.SOURCE_ID);
                for (int i = 0; i < segments.size(); i++) {
                    LookupKeySegment segment = segments.get(i);
                    if (segment.lookupType == ContactLookupKey.LOOKUP_TYPE_SOURCE_ID
                            && accountHashCode == segment.accountHashCode
                            && segment.key.equals(sourceId)) {
                        segment.contactId = c.getLong(LookupBySourceIdQuery.CONTACT_ID);
                        break;
                    }
                }
            }
        } finally {
            c.close();
        }

        return getMostReferencedContactId(segments);
    }

    private interface LookupByRawContactIdQuery {
        String TABLE = Views.RAW_CONTACTS;

        String COLUMNS[] = {
                RawContacts.CONTACT_ID,
                RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                RawContacts.ACCOUNT_NAME,
                RawContacts._ID,
        };

        int CONTACT_ID = 0;
        int ACCOUNT_TYPE_AND_DATA_SET = 1;
        int ACCOUNT_NAME = 2;
        int ID = 3;
    }

    private long lookupContactIdByRawContactIds(SQLiteDatabase db,
            ArrayList<LookupKeySegment> segments) {
        StringBuilder sb = new StringBuilder();
        sb.append(RawContacts._ID + " IN (");
        for (LookupKeySegment segment : segments) {
            if (segment.lookupType == ContactLookupKey.LOOKUP_TYPE_RAW_CONTACT_ID) {
                sb.append(segment.rawContactId);
                sb.append(",");
            }
        }
        sb.setLength(sb.length() - 1);      // Last comma
        sb.append(") AND " + RawContacts.CONTACT_ID + " NOT NULL");

        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        Cursor c = db.query(LookupByRawContactIdQuery.TABLE, LookupByRawContactIdQuery.COLUMNS,
                 sb.toString(), null, null, null, null);
         * @{
         */
        String rawContactsView = mDbHelper.get()
                .getRawEntitiesView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        Cursor c = db.query(rawContactsView, LookupByRawContactIdQuery.COLUMNS,
                 sb.toString(), null, null, null, null);
        /**
         * @}
         */
        try {
            while (c.moveToNext()) {
                String accountTypeAndDataSet = c.getString(
                        LookupByRawContactIdQuery.ACCOUNT_TYPE_AND_DATA_SET);
                String accountName = c.getString(LookupByRawContactIdQuery.ACCOUNT_NAME);
                int accountHashCode =
                        ContactLookupKey.getAccountHashCode(accountTypeAndDataSet, accountName);
                String rawContactId = c.getString(LookupByRawContactIdQuery.ID);
                for (LookupKeySegment segment : segments) {
                    if (segment.lookupType == ContactLookupKey.LOOKUP_TYPE_RAW_CONTACT_ID
                            && accountHashCode == segment.accountHashCode
                            && segment.rawContactId.equals(rawContactId)) {
                        segment.contactId = c.getLong(LookupByRawContactIdQuery.CONTACT_ID);
                        break;
                    }
                }
            }
        } finally {
            c.close();
        }

        return getMostReferencedContactId(segments);
    }

    private interface LookupByDisplayNameQuery {
        String TABLE = Tables.NAME_LOOKUP_JOIN_RAW_CONTACTS;
        String COLUMNS[] = {
                RawContacts.CONTACT_ID,
                RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                RawContacts.ACCOUNT_NAME,
                NameLookupColumns.NORMALIZED_NAME
        };

        int CONTACT_ID = 0;
        int ACCOUNT_TYPE_AND_DATA_SET = 1;
        int ACCOUNT_NAME = 2;
        int NORMALIZED_NAME = 3;
    }

    private long lookupContactIdByDisplayNames(
            SQLiteDatabase db, ArrayList<LookupKeySegment> segments) {

        StringBuilder sb = new StringBuilder();
        sb.append(NameLookupColumns.NORMALIZED_NAME + " IN (");
        for (LookupKeySegment segment : segments) {
            if (segment.lookupType == ContactLookupKey.LOOKUP_TYPE_DISPLAY_NAME
                    || segment.lookupType == ContactLookupKey.LOOKUP_TYPE_RAW_CONTACT_ID) {
                DatabaseUtils.appendEscapedSQLString(sb, segment.key);
                sb.append(",");
            }
        }
        sb.setLength(sb.length() - 1);  // Last comma.
        sb.append(") AND " + NameLookupColumns.NAME_TYPE + "=" + NameLookupType.NAME_COLLATION_KEY
                + " AND " + RawContacts.CONTACT_ID + " NOT NULL");

        Cursor c = db.query(LookupByDisplayNameQuery.TABLE, LookupByDisplayNameQuery.COLUMNS,
                 sb.toString(), null, null, null, null);
        try {
            while (c.moveToNext()) {
                String accountTypeAndDataSet =
                        c.getString(LookupByDisplayNameQuery.ACCOUNT_TYPE_AND_DATA_SET);
                String accountName = c.getString(LookupByDisplayNameQuery.ACCOUNT_NAME);
                int accountHashCode =
                        ContactLookupKey.getAccountHashCode(accountTypeAndDataSet, accountName);
                String name = c.getString(LookupByDisplayNameQuery.NORMALIZED_NAME);
                for (LookupKeySegment segment : segments) {
                    if ((segment.lookupType == ContactLookupKey.LOOKUP_TYPE_DISPLAY_NAME
                            || segment.lookupType == ContactLookupKey.LOOKUP_TYPE_RAW_CONTACT_ID)
                            && accountHashCode == segment.accountHashCode
                            && segment.key.equals(name)) {
                        segment.contactId = c.getLong(LookupByDisplayNameQuery.CONTACT_ID);
                        break;
                    }
                }
            }
        } finally {
            c.close();
        }

        return getMostReferencedContactId(segments);
    }

    private boolean lookupKeyContainsType(ArrayList<LookupKeySegment> segments, int lookupType) {
        for (LookupKeySegment segment : segments) {
            if (segment.lookupType == lookupType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the contact ID that is mentioned the highest number of times.
     */
    private long getMostReferencedContactId(ArrayList<LookupKeySegment> segments) {

        long bestContactId = -1;
        int bestRefCount = 0;

        long contactId = -1;
        int count = 0;

        Collections.sort(segments);
        for (LookupKeySegment segment : segments) {
            if (segment.contactId != -1) {
                if (segment.contactId == contactId) {
                    count++;
                } else {
                    if (count > bestRefCount) {
                        bestContactId = contactId;
                        bestRefCount = count;
                    }
                    contactId = segment.contactId;
                    count = 1;
                }
            }
        }

        if (count > bestRefCount) {
            return contactId;
        }
        return bestContactId;
    }

    private void setTablesAndProjectionMapForContacts(SQLiteQueryBuilder qb, String[] projection) {
        setTablesAndProjectionMapForContacts(qb, projection, false);
    }

    /**
     * @param includeDataUsageStat true when the table should include DataUsageStat table.
     * Note that this uses INNER JOIN instead of LEFT OUTER JOIN, so some of data in Contacts
     * may be dropped.
     */
    private void setTablesAndProjectionMapForContacts(
            SQLiteQueryBuilder qb, String[] projection, boolean includeDataUsageStat) {
        StringBuilder sb = new StringBuilder();
        if (includeDataUsageStat) {
            sb.append(Views.DATA_USAGE_STAT + " AS " + Tables.DATA_USAGE_STAT);
            sb.append(" INNER JOIN ");
        }

        sb.append(Views.CONTACTS);

        // Just for frequently contacted contacts in Strequent URI handling.
        if (includeDataUsageStat) {
            sb.append(" ON (" +
                    DbQueryUtils.concatenateClauses(
                            DataUsageStatColumns.CONCRETE_TIMES_USED + " > 0",
                            RawContacts.CONTACT_ID + "=" + Views.CONTACTS + "." + Contacts._ID) +
                    ")");
        }

        appendContactPresenceJoin(sb, projection, Contacts._ID);
        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
        qb.setTables(sb.toString());
        qb.setProjectionMap(sContactsProjectionMap);
    }

    /**
     * Finds name lookup records matching the supplied filter, picks one arbitrary match per
     * contact and joins that with other contacts tables.
     */
    private void setTablesAndProjectionMapForContactsWithSnippet(SQLiteQueryBuilder qb, Uri uri,
            String[] projection, String filter, long directoryId, boolean deferSnippeting) {

        StringBuilder sb = new StringBuilder();
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        sb.append(Views.CONTACTS);
         * @{
         */
        String contactsView = mDbHelper.get().getContactsView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        sb.append(contactsView);
        /**
         * @}
         */

        if (filter != null) {
            filter = filter.trim();
        }

        if (TextUtils.isEmpty(filter) || (directoryId != -1 && directoryId != Directory.DEFAULT)) {
            sb.append(" JOIN (SELECT NULL AS " + SearchSnippets.SNIPPET + " WHERE 0)");
        } else {
            appendSearchIndexJoin(sb, uri, projection, filter, deferSnippeting);
        }
        appendContactPresenceJoin(sb, projection, Contacts._ID);
        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
        qb.setTables(sb.toString());
        qb.setProjectionMap(sContactsProjectionWithSnippetMap);
    }

    private void appendSearchIndexJoin(
            StringBuilder sb, Uri uri, String[] projection, String filter,
            boolean  deferSnippeting) {

        if (snippetNeeded(projection)) {
            String[] args = null;
            String snippetArgs =
                    getQueryParameter(uri, SearchSnippets.SNIPPET_ARGS_PARAM_KEY);
            if (snippetArgs != null) {
                args = snippetArgs.split(",");
            }

            String startMatch = args != null && args.length > 0 ? args[0]
                    : DEFAULT_SNIPPET_ARG_START_MATCH;
            String endMatch = args != null && args.length > 1 ? args[1]
                    : DEFAULT_SNIPPET_ARG_END_MATCH;
            String ellipsis = args != null && args.length > 2 ? args[2]
                    : DEFAULT_SNIPPET_ARG_ELLIPSIS;
            int maxTokens = args != null && args.length > 3 ? Integer.parseInt(args[3])
                    : DEFAULT_SNIPPET_ARG_MAX_TOKENS;

            appendSearchIndexJoin(
                    sb, filter, true, startMatch, endMatch, ellipsis, maxTokens, deferSnippeting);
        } else {
            appendSearchIndexJoin(sb, filter, false, null, null, null, 0, false);
        }
    }

    public void appendSearchIndexJoin(StringBuilder sb, String filter,
            boolean snippetNeeded, String startMatch, String endMatch, String ellipsis,
            int maxTokens, boolean deferSnippeting) {
        boolean isEmailAddress = false;
        String emailAddress = null;
        boolean isPhoneNumber = false;
        String phoneNumber = null;
        String numberE164 = null;


        if (filter.indexOf('@') != -1) {
            emailAddress = mDbHelper.get().extractAddressFromEmailAddress(filter);
            isEmailAddress = !TextUtils.isEmpty(emailAddress);
        } else {
            isPhoneNumber = isPhoneNumber(filter);
            if (isPhoneNumber) {
                phoneNumber = PhoneNumberUtils.normalizeNumber(filter);
                numberE164 = PhoneNumberUtils.formatNumberToE164(phoneNumber,
                        mDbHelper.get().getCurrentCountryIso());
            }
        }

        final String SNIPPET_CONTACT_ID = "snippet_contact_id";
        sb.append(" JOIN (SELECT " + SearchIndexColumns.CONTACT_ID + " AS " + SNIPPET_CONTACT_ID);
        if (snippetNeeded) {
            sb.append(", ");
            if (isEmailAddress) {
                sb.append("ifnull(");
                if (!deferSnippeting) {
                    // Add the snippet marker only when we're really creating snippet.
                    DatabaseUtils.appendEscapedSQLString(sb, startMatch);
                    sb.append("||");
                }
                sb.append("(SELECT MIN(" + Email.ADDRESS + ")");
                sb.append(" FROM " + Tables.DATA_JOIN_RAW_CONTACTS);
                sb.append(" WHERE  " + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID);
                sb.append("=" + RawContacts.CONTACT_ID + " AND " + Email.ADDRESS + " LIKE ");
                DatabaseUtils.appendEscapedSQLString(sb, filter + "%");
                sb.append(")");
                if (!deferSnippeting) {
                    sb.append("||");
                    DatabaseUtils.appendEscapedSQLString(sb, endMatch);
                }
                sb.append(",");

                if (deferSnippeting) {
                    sb.append(SearchIndexColumns.CONTENT);
                } else {
                    appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                }
                sb.append(")");
            } else if (isPhoneNumber) {
                sb.append("ifnull(");
                if (!deferSnippeting) {
                    // Add the snippet marker only when we're really creating snippet.
                    DatabaseUtils.appendEscapedSQLString(sb, startMatch);
                    sb.append("||");
                }
                sb.append("(SELECT MIN(" + Phone.NUMBER + ")");
                sb.append(" FROM " +
                        Tables.DATA_JOIN_RAW_CONTACTS + " JOIN " + Tables.PHONE_LOOKUP);
                sb.append(" ON " + DataColumns.CONCRETE_ID);
                sb.append("=" + Tables.PHONE_LOOKUP + "." + PhoneLookupColumns.DATA_ID);
                sb.append(" WHERE  " + Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID);
                sb.append("=" + RawContacts.CONTACT_ID);
                /*
                 * SPRD: Bug 553170 The DUT can not show phone number of contacts from snip searching.
                 * android original code:
                sb.append(" AND " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                 * @{
                 */
                if (!SystemProperties.get("ro.operator").isEmpty()
                        && SystemProperties.get("ro.operator").equals("cmcc")) {
                    sb.append(" AND " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '%");
                } else {
                    sb.append(" AND " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                }
                /*
                 * @}
                 */
                sb.append(phoneNumber);
                sb.append("%'");
                if (!TextUtils.isEmpty(numberE164)) {
                    sb.append(" OR " + PhoneLookupColumns.NORMALIZED_NUMBER + " LIKE '");
                    sb.append(numberE164);
                    sb.append("%'");
                }
                sb.append(")");
                if (! deferSnippeting) {
                    sb.append("||");
                    DatabaseUtils.appendEscapedSQLString(sb, endMatch);
                }
                sb.append(",");

                if (deferSnippeting) {
                    sb.append(SearchIndexColumns.CONTENT);
                } else {
                    appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                }
                sb.append(")");
            } else {
                final String normalizedFilter = NameNormalizer.normalize(filter);
                if (!TextUtils.isEmpty(normalizedFilter)) {
                    if (deferSnippeting) {
                        sb.append(SearchIndexColumns.CONTENT);
                    } else {
                        sb.append("(CASE WHEN EXISTS (SELECT 1 FROM ");
                        sb.append(Tables.RAW_CONTACTS + " AS rc INNER JOIN ");
                        sb.append(Tables.NAME_LOOKUP + " AS nl ON (rc." + RawContacts._ID);
                        sb.append("=nl." + NameLookupColumns.RAW_CONTACT_ID);
                        sb.append(") WHERE nl." + NameLookupColumns.NORMALIZED_NAME);
                        sb.append(" GLOB '" + normalizedFilter + "*' AND ");
                        sb.append("nl." + NameLookupColumns.NAME_TYPE + "=");
                        sb.append(NameLookupType.NAME_COLLATION_KEY + " AND ");
                        sb.append(Tables.SEARCH_INDEX + "." + SearchIndexColumns.CONTACT_ID);
                        sb.append("=rc." + RawContacts.CONTACT_ID);
                        sb.append(") THEN NULL ELSE ");
                        appendSnippetFunction(sb, startMatch, endMatch, ellipsis, maxTokens);
                        sb.append(" END)");
                    }
                } else {
                    sb.append("NULL");
                }
            }
            sb.append(" AS " + SearchSnippets.SNIPPET);
        }

        sb.append(" FROM " + Tables.SEARCH_INDEX);
        sb.append(" WHERE ");
        sb.append(Tables.SEARCH_INDEX + " MATCH '");
        if (isEmailAddress) {
            // we know that the emailAddress contains a @. This phrase search should be
            // scoped against "content:" only, but unfortunately SQLite doesn't support
            // phrases and scoped columns at once. This is fine in this case however, because:
            //  - We can't erroneously match against name, as name is all-hex (so the @ can't match)
            //  - We can't match against tokens, because phone-numbers can't contain @
            final String sanitizedEmailAddress =
                    emailAddress == null ? "" : sanitizeMatch(emailAddress);
            sb.append("\"");
            sb.append(sanitizedEmailAddress);
            sb.append("*\"");
        } else if (isPhoneNumber) {
            // normalized version of the phone number (phoneNumber can only have + and digits)
            final String phoneNumberCriteria = " OR tokens:" + phoneNumber + "*";

            // international version of this number (numberE164 can only have + and digits)
            final String numberE164Criteria =
                    (numberE164 != null && !TextUtils.equals(numberE164, phoneNumber))
                    ? " OR tokens:" + numberE164 + "*"
                    : "";

            // combine all criteria
            final String commonCriteria =
                    phoneNumberCriteria + numberE164Criteria;

            // search in content
            sb.append(SearchIndexManager.getFtsMatchQuery(filter,
                    FtsQueryBuilder.getDigitsQueryBuilder(commonCriteria)));
        } else {
            // general case: not a phone number, not an email-address
            sb.append(SearchIndexManager.getFtsMatchQuery(filter,
                    FtsQueryBuilder.SCOPED_NAME_NORMALIZING));
        }
        // Omit results in "Other Contacts".
        sb.append("' AND " + SNIPPET_CONTACT_ID + " IN " + Tables.DEFAULT_DIRECTORY + ")");
        sb.append(" ON (" + Contacts._ID + "=" + SNIPPET_CONTACT_ID + ")");
    }

    private static String sanitizeMatch(String filter) {
        return filter.replace("'", "").replace("*", "").replace("-", "").replace("\"", "");
    }

    private void appendSnippetFunction(
            StringBuilder sb, String startMatch, String endMatch, String ellipsis, int maxTokens) {
        sb.append("snippet(" + Tables.SEARCH_INDEX + ",");
        DatabaseUtils.appendEscapedSQLString(sb, startMatch);
        sb.append(",");
        DatabaseUtils.appendEscapedSQLString(sb, endMatch);
        sb.append(",");
        DatabaseUtils.appendEscapedSQLString(sb, ellipsis);

        // The index of the column used for the snippet, "content".
        sb.append(",1,");
        sb.append(maxTokens);
        sb.append(")");
    }

    private void setTablesAndProjectionMapForRawContacts(SQLiteQueryBuilder qb, Uri uri) {
        StringBuilder sb = new StringBuilder();
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        sb.append(Views.RAW_CONTACTS);
         * @{
         */
        String rawContactsView = mDbHelper.get().getRawContactsView(
                ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        sb.append(rawContactsView);
        /**
         * @}
         */
        qb.setTables(sb.toString());
        qb.setProjectionMap(sRawContactsProjectionMap);
        appendAccountIdFromParameter(qb, uri);
    }

    private void setTablesAndProjectionMapForRawEntities(SQLiteQueryBuilder qb, Uri uri) {
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        qb.setTables(Views.RAW_ENTITIES);
         * @{
         */
        String rawEntitiesView = mDbHelper.get().getRawEntitiesView(
                ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        qb.setTables(rawEntitiesView);
        /**
         * @}
         */
        qb.setProjectionMap(sRawEntityProjectionMap);
        appendAccountIdFromParameter(qb, uri);
    }

    private void setTablesAndProjectionMapForData(
            SQLiteQueryBuilder qb, Uri uri, String[] projection, boolean distinct) {

        setTablesAndProjectionMapForData(qb, uri, projection, distinct, false, null);
    }

    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb, Uri uri,
            String[] projection, boolean distinct, boolean addSipLookupColumns) {
        setTablesAndProjectionMapForData(qb, uri, projection, distinct, addSipLookupColumns, null);
    }

    /**
     * @param usageType when non-null {@link Tables#DATA_USAGE_STAT} is joined with the specified
     * type.
     */
    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb, Uri uri,
            String[] projection, boolean distinct, Integer usageType) {
        setTablesAndProjectionMapForData(qb, uri, projection, distinct, false, usageType);
    }

    private void setTablesAndProjectionMapForData(SQLiteQueryBuilder qb, Uri uri,
            String[] projection, boolean distinct, boolean addSipLookupColumns, Integer usageType) {
        StringBuilder sb = new StringBuilder();
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        sb.append(Views.DATA);
         * @{
         */
        String dataView = mDbHelper.get().getDataView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        sb.append(dataView);
        /**
         * @}
         */
        sb.append(" data");

        appendContactPresenceJoin(sb, projection, RawContacts.CONTACT_ID);
        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
        appendDataPresenceJoin(sb, projection, DataColumns.CONCRETE_ID);
        appendDataStatusUpdateJoin(sb, projection, DataColumns.CONCRETE_ID);

        appendDataUsageStatJoin(
                sb, usageType == null ? USAGE_TYPE_ALL : usageType, DataColumns.CONCRETE_ID);

        qb.setTables(sb.toString());

        boolean useDistinct = distinct || !ContactsDatabaseHelper.isInProjection(
                projection, DISTINCT_DATA_PROHIBITING_COLUMNS);
        qb.setDistinct(useDistinct);

        final ProjectionMap projectionMap;
        if (addSipLookupColumns) {
            projectionMap =
                    useDistinct ? sDistinctDataSipLookupProjectionMap : sDataSipLookupProjectionMap;
        } else {
            projectionMap = useDistinct ? sDistinctDataProjectionMap : sDataProjectionMap;
        }

        qb.setProjectionMap(projectionMap);
        appendAccountIdFromParameter(qb, uri);
    }

    private void setTableAndProjectionMapForStatusUpdates(
            SQLiteQueryBuilder qb, String[] projection) {

        StringBuilder sb = new StringBuilder();
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        sb.append(Views.DATA);
         * @{
         */
        String dataView = mDbHelper.get().getDataView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        sb.append(dataView);
        /**
         * @}
         */
        sb.append(" data");
        appendDataPresenceJoin(sb, projection, DataColumns.CONCRETE_ID);
        appendDataStatusUpdateJoin(sb, projection, DataColumns.CONCRETE_ID);

        qb.setTables(sb.toString());
        qb.setProjectionMap(sStatusUpdatesProjectionMap);
    }

    private void setTablesAndProjectionMapForStreamItems(SQLiteQueryBuilder qb) {
        qb.setTables(Views.STREAM_ITEMS);
        qb.setProjectionMap(sStreamItemsProjectionMap);
    }

    private void setTablesAndProjectionMapForStreamItemPhotos(SQLiteQueryBuilder qb) {
        qb.setTables(Tables.PHOTO_FILES
                + " JOIN " + Tables.STREAM_ITEM_PHOTOS + " ON ("
                + StreamItemPhotosColumns.CONCRETE_PHOTO_FILE_ID + "="
                + PhotoFilesColumns.CONCRETE_ID
                + ") JOIN " + Tables.STREAM_ITEMS + " ON ("
                + StreamItemPhotosColumns.CONCRETE_STREAM_ITEM_ID + "="
                + StreamItemsColumns.CONCRETE_ID + ")"
                + " JOIN " + Tables.RAW_CONTACTS + " ON ("
                + StreamItemsColumns.CONCRETE_RAW_CONTACT_ID + "=" + RawContactsColumns.CONCRETE_ID
                + ")");
        qb.setProjectionMap(sStreamItemPhotosProjectionMap);
    }

    private void setTablesAndProjectionMapForEntities(
            SQLiteQueryBuilder qb, Uri uri, String[] projection) {

        StringBuilder sb = new StringBuilder();
        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        sb.append(Views.ENTITIES);
         * @{
         */
        String entitiesView = mDbHelper.get().getEntitiesView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        sb.append(entitiesView);
        /**
         * @}
         */
        sb.append(" data");

        appendContactPresenceJoin(sb, projection, Contacts.Entity.CONTACT_ID);
        appendContactStatusUpdateJoin(sb, projection, ContactsColumns.LAST_STATUS_UPDATE_ID);
        appendDataPresenceJoin(sb, projection, Contacts.Entity.DATA_ID);
        appendDataStatusUpdateJoin(sb, projection, Contacts.Entity.DATA_ID);
        // Only support USAGE_TYPE_ALL for now. Can add finer grain if needed in the future.
        appendDataUsageStatJoin(sb, USAGE_TYPE_ALL, Contacts.Entity.DATA_ID);

        qb.setTables(sb.toString());
        qb.setProjectionMap(sEntityProjectionMap);
        appendAccountIdFromParameter(qb, uri);
    }

    private void appendContactStatusUpdateJoin(
            StringBuilder sb, String[] projection, String lastStatusUpdateIdColumn) {

        if (ContactsDatabaseHelper.isInProjection(projection,
                Contacts.CONTACT_STATUS,
                Contacts.CONTACT_STATUS_RES_PACKAGE,
                Contacts.CONTACT_STATUS_ICON,
                Contacts.CONTACT_STATUS_LABEL,
                Contacts.CONTACT_STATUS_TIMESTAMP)) {
            sb.append(" LEFT OUTER JOIN " + Tables.STATUS_UPDATES + " "
                    + ContactsStatusUpdatesColumns.ALIAS +
                    " ON (" + lastStatusUpdateIdColumn + "="
                            + ContactsStatusUpdatesColumns.CONCRETE_DATA_ID + ")");
        }
    }

    private void appendDataStatusUpdateJoin(
            StringBuilder sb, String[] projection, String dataIdColumn) {

        if (ContactsDatabaseHelper.isInProjection(projection,
                StatusUpdates.STATUS,
                StatusUpdates.STATUS_RES_PACKAGE,
                StatusUpdates.STATUS_ICON,
                StatusUpdates.STATUS_LABEL,
                StatusUpdates.STATUS_TIMESTAMP)) {
            sb.append(" LEFT OUTER JOIN " + Tables.STATUS_UPDATES +
                    " ON (" + StatusUpdatesColumns.CONCRETE_DATA_ID + "="
                            + dataIdColumn + ")");
        }
    }

    private void appendDataUsageStatJoin(StringBuilder sb, int usageType, String dataIdColumn) {
        if (usageType != USAGE_TYPE_ALL) {
            sb.append(" LEFT OUTER JOIN " + Tables.DATA_USAGE_STAT +
                    " ON (" + DataUsageStatColumns.CONCRETE_DATA_ID + "=");
            sb.append(dataIdColumn);
            sb.append(" AND " + DataUsageStatColumns.CONCRETE_USAGE_TYPE + "=");
            sb.append(usageType);
            sb.append(")");
        } else {
            sb.append(
                    " LEFT OUTER JOIN " +
                        "(SELECT " +
                            DataUsageStatColumns.CONCRETE_DATA_ID + " as STAT_DATA_ID, " +
                            "SUM(" + DataUsageStatColumns.CONCRETE_TIMES_USED +
                                ") as " + DataUsageStatColumns.TIMES_USED + ", " +
                            "MAX(" + DataUsageStatColumns.CONCRETE_LAST_TIME_USED +
                                ") as " + DataUsageStatColumns.LAST_TIME_USED +
                        " FROM " + Tables.DATA_USAGE_STAT + " GROUP BY " +
                            DataUsageStatColumns.CONCRETE_DATA_ID + ") as " + Tables.DATA_USAGE_STAT
                    );
            sb.append(" ON (STAT_DATA_ID=");
            sb.append(dataIdColumn);
            sb.append(")");
        }
    }

    private void appendContactPresenceJoin(
            StringBuilder sb, String[] projection, String contactIdColumn) {

        if (ContactsDatabaseHelper.isInProjection(
                projection, Contacts.CONTACT_PRESENCE, Contacts.CONTACT_CHAT_CAPABILITY)) {

            sb.append(" LEFT OUTER JOIN " + Tables.AGGREGATED_PRESENCE +
                    " ON (" + contactIdColumn + " = "
                            + AggregatedPresenceColumns.CONCRETE_CONTACT_ID + ")");
        }
    }

    private void appendDataPresenceJoin(
            StringBuilder sb, String[] projection, String dataIdColumn) {

        if (ContactsDatabaseHelper.isInProjection(
                projection, Data.PRESENCE, Data.CHAT_CAPABILITY)) {
            sb.append(" LEFT OUTER JOIN " + Tables.PRESENCE +
                    " ON (" + StatusUpdates.DATA_ID + "=" + dataIdColumn + ")");
        }
    }

    private void appendLocalDirectoryAndAccountSelectionIfNeeded(
            SQLiteQueryBuilder qb, long directoryId, Uri uri) {

        final StringBuilder sb = new StringBuilder();
        if (directoryId == Directory.DEFAULT) {
            sb.append("(" + Contacts._ID + " IN " + Tables.DEFAULT_DIRECTORY + ")");
        } else if (directoryId == Directory.LOCAL_INVISIBLE){
            sb.append("(" + Contacts._ID + " NOT IN " + Tables.DEFAULT_DIRECTORY + ")");
        } else {
            sb.append("(1)");
        }

        final AccountWithDataSet accountWithDataSet = getAccountWithDataSetFromUri(uri);
        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountWithDataSet.getAccountName());
        if (validAccount) {
            final Long accountId = mDbHelper.get().getAccountIdOrNull(accountWithDataSet);
            if (accountId == null) {
                // No such account.
                sb.setLength(0);
                sb.append("(1=2)");
            } else {
                sb.append(
                        " AND (" + Contacts._ID + " IN (" +
                        "SELECT " + RawContacts.CONTACT_ID + " FROM " + Tables.RAW_CONTACTS +
                        " WHERE " + RawContactsColumns.ACCOUNT_ID + "=" + accountId.toString() +
                        "))");
            }
        }
        qb.appendWhere(sb.toString());
    }

    private void appendAccountFromParameter(SQLiteQueryBuilder qb, Uri uri) {
        final AccountWithDataSet accountWithDataSet = getAccountWithDataSetFromUri(uri);

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountWithDataSet.getAccountName());
        if (validAccount) {
            String toAppend = "(" + RawContacts.ACCOUNT_NAME + "="
                    + DatabaseUtils.sqlEscapeString(accountWithDataSet.getAccountName()) + " AND "
                    + RawContacts.ACCOUNT_TYPE + "="
                    + DatabaseUtils.sqlEscapeString(accountWithDataSet.getAccountType());
            if (accountWithDataSet.getDataSet() == null) {
                toAppend += " AND " + RawContacts.DATA_SET + " IS NULL";
            } else {
                toAppend += " AND " + RawContacts.DATA_SET + "=" +
                        DatabaseUtils.sqlEscapeString(accountWithDataSet.getDataSet());
            }
            toAppend += ")";
            qb.appendWhere(toAppend);
        } else {
            qb.appendWhere("1");
        }
    }

    private void appendAccountIdFromParameter(SQLiteQueryBuilder qb, Uri uri) {
        final AccountWithDataSet accountWithDataSet = getAccountWithDataSetFromUri(uri);

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountWithDataSet.getAccountName());
        if (validAccount) {
            final Long accountId = mDbHelper.get().getAccountIdOrNull(accountWithDataSet);
            if (accountId == null) {
                // No such account.
                qb.appendWhere("(1=2)");
            } else {
                qb.appendWhere(
                        "(" + RawContactsColumns.ACCOUNT_ID + "=" + accountId.toString() + ")");
            }
        } else {
            qb.appendWhere("1");
        }
    }

    private AccountWithDataSet getAccountWithDataSetFromUri(Uri uri) {
        final String accountName = getQueryParameter(uri, RawContacts.ACCOUNT_NAME);
        final String accountType = getQueryParameter(uri, RawContacts.ACCOUNT_TYPE);
        final String dataSet = getQueryParameter(uri, RawContacts.DATA_SET);

        final boolean partialUri = TextUtils.isEmpty(accountName) ^ TextUtils.isEmpty(accountType);
        if (partialUri) {
            // Throw when either account is incomplete.
            throw new IllegalArgumentException(mDbHelper.get().exceptionMessage(
                    "Must specify both or neither of ACCOUNT_NAME and ACCOUNT_TYPE", uri));
        }
        return AccountWithDataSet.get(accountName, accountType, dataSet);
    }

    private String appendAccountToSelection(Uri uri, String selection) {
        final AccountWithDataSet accountWithDataSet = getAccountWithDataSetFromUri(uri);

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountWithDataSet.getAccountName());
        if (validAccount) {
            StringBuilder selectionSb = new StringBuilder(RawContacts.ACCOUNT_NAME + "=");
            selectionSb.append(DatabaseUtils.sqlEscapeString(accountWithDataSet.getAccountName()));
            selectionSb.append(" AND " + RawContacts.ACCOUNT_TYPE + "=");
            selectionSb.append(DatabaseUtils.sqlEscapeString(accountWithDataSet.getAccountType()));
            if (accountWithDataSet.getDataSet() == null) {
                selectionSb.append(" AND " + RawContacts.DATA_SET + " IS NULL");
            } else {
                selectionSb.append(" AND " + RawContacts.DATA_SET + "=")
                        .append(DatabaseUtils.sqlEscapeString(accountWithDataSet.getDataSet()));
            }
            if (!TextUtils.isEmpty(selection)) {
                selectionSb.append(" AND (");
                selectionSb.append(selection);
                selectionSb.append(')');
            }
            return selectionSb.toString();
        }
        return selection;
    }

    private String appendAccountIdToSelection(Uri uri, String selection) {
        final AccountWithDataSet accountWithDataSet = getAccountWithDataSetFromUri(uri);

        // Accounts are valid by only checking one parameter, since we've
        // already ruled out partial accounts.
        final boolean validAccount = !TextUtils.isEmpty(accountWithDataSet.getAccountName());
        if (validAccount) {
            final StringBuilder selectionSb = new StringBuilder();

            final Long accountId = mDbHelper.get().getAccountIdOrNull(accountWithDataSet);
            if (accountId == null) {
                // No such account in the accounts table.  This means, there's no rows to be
                // selected.
                // Note even in this case, we still need to append the original selection, because
                // it may have query parameters.  If we remove these we'll get the # of parameters
                // mismatch exception.
                selectionSb.append("(1=2)");
            } else {
                selectionSb.append(RawContactsColumns.ACCOUNT_ID + "=");
                selectionSb.append(Long.toString(accountId));
            }

            if (!TextUtils.isEmpty(selection)) {
                selectionSb.append(" AND (");
                selectionSb.append(selection);
                selectionSb.append(')');
            }
            return selectionSb.toString();
        }

        return selection;
    }

    /**
     * Gets the value of the "limit" URI query parameter.
     *
     * @return A string containing a non-negative integer, or <code>null</code> if
     *         the parameter is not set, or is set to an invalid value.
     */
    private String getLimit(Uri uri) {
        String limitParam = getQueryParameter(uri, ContactsContract.LIMIT_PARAM_KEY);
        if (limitParam == null) {
            return null;
        }
        // Make sure that the limit is a non-negative integer.
        try {
            int l = Integer.parseInt(limitParam);
            if (l < 0) {
                Log.w(TAG, "Invalid limit parameter: " + limitParam);
                return null;
            }
            return String.valueOf(l);

        } catch (NumberFormatException ex) {
            Log.w(TAG, "Invalid limit parameter: " + limitParam);
            return null;
        }
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        boolean success = false;
        try {
            waitForAccess(mode.equals("r") ? mReadAccessLatch : mWriteAccessLatch);
            final AssetFileDescriptor ret;
            if (mapsToProfileDb(uri)) {
                switchToProfileMode();
                ret = mProfileProvider.openAssetFile(uri, mode);
            } else {
                switchToContactMode();
                ret = openAssetFileLocal(uri, mode);
            }
            success = true;
            return ret;

        } finally {
            if (VERBOSE_LOGGING) {
                Log.v(TAG, "openAssetFile uri=" + uri + " mode=" + mode + " success=" + success +
                        " CPID=" + Binder.getCallingPid() +
                        " User=" + UserUtils.getCurrentUserHandle(getContext()));
            }
        }
    }

    public AssetFileDescriptor openAssetFileLocal(
            Uri uri, String mode) throws FileNotFoundException {

        // In some cases to implement this, we will need to do further queries
        // on the content provider.  We have already done the permission check for
        // access to the URI given here, so we don't need to do further checks on
        // the queries we will do to populate it.  Also this makes sure that when
        // we go through any app ops checks for those queries that the calling uid
        // and package names match at that point.
        final long ident = Binder.clearCallingIdentity();
        try {
            return openAssetFileInner(uri, mode);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private AssetFileDescriptor openAssetFileInner(
            Uri uri, String mode) throws FileNotFoundException {

        final boolean writing = mode.contains("w");

        final SQLiteDatabase db = mDbHelper.get().getDatabase(writing);

        int match = sUriMatcher.match(uri);
        switch (match) {
            case CONTACTS_ID_PHOTO: {
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                return openPhotoAssetFile(db, uri, mode,
                        Data._ID + "=" + Contacts.PHOTO_ID + " AND " +
                                RawContacts.CONTACT_ID + "=?",
                        new String[] {String.valueOf(contactId)});
            }

            case CONTACTS_ID_DISPLAY_PHOTO: {
                if (!mode.equals("r")) {
                    throw new IllegalArgumentException(
                            "Display photos retrieved by contact ID can only be read.");
                }
                long contactId = Long.parseLong(uri.getPathSegments().get(1));
                Cursor c = db.query(Tables.CONTACTS,
                        new String[] {Contacts.PHOTO_FILE_ID},
                        Contacts._ID + "=?", new String[] {String.valueOf(contactId)},
                        null, null, null);
                try {
                    if (c.moveToFirst()) {
                        long photoFileId = c.getLong(0);
                        return openDisplayPhotoForRead(photoFileId);
                    }
                    // No contact for this ID.
                    throw new FileNotFoundException(uri.toString());
                } finally {
                    c.close();
                }
            }

            case PROFILE_DISPLAY_PHOTO: {
                if (!mode.equals("r")) {
                    throw new IllegalArgumentException(
                            "Display photos retrieved by contact ID can only be read.");
                }
                Cursor c = db.query(Tables.CONTACTS,
                        new String[] {Contacts.PHOTO_FILE_ID}, null, null, null, null, null);
                try {
                    if (c.moveToFirst()) {
                        long photoFileId = c.getLong(0);
                        return openDisplayPhotoForRead(photoFileId);
                    }
                    // No profile record.
                    throw new FileNotFoundException(uri.toString());
                } finally {
                    c.close();
                }
            }

            case CONTACTS_LOOKUP_PHOTO:
            case CONTACTS_LOOKUP_ID_PHOTO:
            case CONTACTS_LOOKUP_DISPLAY_PHOTO:
            case CONTACTS_LOOKUP_ID_DISPLAY_PHOTO: {
                if (!mode.equals("r")) {
                    throw new IllegalArgumentException(
                            "Photos retrieved by contact lookup key can only be read.");
                }
                List<String> pathSegments = uri.getPathSegments();
                int segmentCount = pathSegments.size();
                if (segmentCount < 4) {
                    throw new IllegalArgumentException(
                            mDbHelper.get().exceptionMessage("Missing a lookup key", uri));
                }

                boolean forDisplayPhoto = (match == CONTACTS_LOOKUP_ID_DISPLAY_PHOTO
                        || match == CONTACTS_LOOKUP_DISPLAY_PHOTO);
                String lookupKey = pathSegments.get(2);
                String[] projection = new String[] {Contacts.PHOTO_ID, Contacts.PHOTO_FILE_ID};
                if (segmentCount == 5) {
                    long contactId = Long.parseLong(pathSegments.get(3));
                    SQLiteQueryBuilder lookupQb = new SQLiteQueryBuilder();
                    setTablesAndProjectionMapForContacts(lookupQb, projection);
                    Cursor c = queryWithContactIdAndLookupKey(
                            lookupQb, db, projection, null, null, null, null, null,
                            Contacts._ID, contactId, Contacts.LOOKUP_KEY, lookupKey, null);
                    if (c != null) {
                        try {
                            c.moveToFirst();
                            if (forDisplayPhoto) {
                                long photoFileId =
                                        c.getLong(c.getColumnIndex(Contacts.PHOTO_FILE_ID));
                                return openDisplayPhotoForRead(photoFileId);
                            }
                            long photoId = c.getLong(c.getColumnIndex(Contacts.PHOTO_ID));
                            return openPhotoAssetFile(db, uri, mode,
                                    Data._ID + "=?", new String[] {String.valueOf(photoId)});
                        } finally {
                            c.close();
                        }
                    }
                }

                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                setTablesAndProjectionMapForContacts(qb, projection);
                long contactId = lookupContactIdByLookupKey(db, lookupKey);
                Cursor c = qb.query(db, projection, Contacts._ID + "=?",
                        new String[] {String.valueOf(contactId)}, null, null, null);
                try {
                    c.moveToFirst();
                    if (forDisplayPhoto) {
                        long photoFileId = c.getLong(c.getColumnIndex(Contacts.PHOTO_FILE_ID));
                        return openDisplayPhotoForRead(photoFileId);
                    }

                    long photoId = c.getLong(c.getColumnIndex(Contacts.PHOTO_ID));
                    return openPhotoAssetFile(db, uri, mode,
                            Data._ID + "=?", new String[] {String.valueOf(photoId)});
                } finally {
                    c.close();
                }
            }

            case RAW_CONTACTS_ID_DISPLAY_PHOTO: {
                long rawContactId = Long.parseLong(uri.getPathSegments().get(1));
                boolean writeable = !mode.equals("r");

                // Find the primary photo data record for this raw contact.
                SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
                String[] projection = new String[] {Data._ID, Photo.PHOTO_FILE_ID};
                setTablesAndProjectionMapForData(qb, uri, projection, false);
                long photoMimetypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);
                Cursor c = qb.query(db, projection,
                        Data.RAW_CONTACT_ID + "=? AND " + DataColumns.MIMETYPE_ID + "=?",
                        new String[] {
                                String.valueOf(rawContactId), String.valueOf(photoMimetypeId)},
                        null, null, Data.IS_PRIMARY + " DESC");
                long dataId = 0;
                long photoFileId = 0;
                try {
                    if (c.getCount() >= 1) {
                        c.moveToFirst();
                        dataId = c.getLong(0);
                        photoFileId = c.getLong(1);
                    }
                } finally {
                    c.close();
                }

                // If writeable, open a writeable file descriptor that we can monitor.
                // When the caller finishes writing content, we'll process the photo and
                // update the data record.
                if (writeable) {
                    return openDisplayPhotoForWrite(rawContactId, dataId, uri, mode);
                }
                return openDisplayPhotoForRead(photoFileId);
            }

            case DISPLAY_PHOTO_ID: {
                long photoFileId = ContentUris.parseId(uri);
                if (!mode.equals("r")) {
                    throw new IllegalArgumentException(
                            "Display photos retrieved by key can only be read.");
                }
                return openDisplayPhotoForRead(photoFileId);
            }

            case DATA_ID: {
                long dataId = Long.parseLong(uri.getPathSegments().get(1));
                long photoMimetypeId = mDbHelper.get().getMimeTypeId(Photo.CONTENT_ITEM_TYPE);
                return openPhotoAssetFile(db, uri, mode,
                        Data._ID + "=? AND " + DataColumns.MIMETYPE_ID + "=" + photoMimetypeId,
                        new String[]{String.valueOf(dataId)});
            }

            case PROFILE_AS_VCARD: {
                // When opening a contact as file, we pass back contents as a
                // vCard-encoded stream. We build into a local buffer first,
                // then pipe into MemoryFile once the exact size is known.
                final ByteArrayOutputStream localStream = new ByteArrayOutputStream();
                outputRawContactsAsVCard(uri, localStream, null, null);
                return buildAssetFileDescriptor(localStream);
            }

            case CONTACTS_AS_VCARD: {
                // When opening a contact as file, we pass back contents as a
                // vCard-encoded stream. We build into a local buffer first,
                // then pipe into MemoryFile once the exact size is known.
                final ByteArrayOutputStream localStream = new ByteArrayOutputStream();
                outputRawContactsAsVCard(uri, localStream, null, null);
                return buildAssetFileDescriptor(localStream);
            }

            case CONTACTS_AS_MULTI_VCARD: {
                final String lookupKeys = uri.getPathSegments().get(2);
                final String[] lookupKeyList = lookupKeys.split(":");
                final StringBuilder inBuilder = new StringBuilder();
                Uri queryUri = Contacts.CONTENT_URI;

                // SQLite has limits on how many parameters can be used
                // so the IDs are concatenated to a query string here instead
                int index = 0;
                for (String lookupKey : lookupKeyList) {
                    inBuilder.append(index == 0 ? "(" : ",");

                    // TODO: Figure out what to do if the profile contact is in the list.
                    long contactId = lookupContactIdByLookupKey(db, lookupKey);
                    inBuilder.append(contactId);
                    index++;
                }

                inBuilder.append(')');
                final String selection = Contacts._ID + " IN " + inBuilder.toString();

                // When opening a contact as file, we pass back contents as a
                // vCard-encoded stream. We build into a local buffer first,
                // then pipe into MemoryFile once the exact size is known.
                final ByteArrayOutputStream localStream = new ByteArrayOutputStream();
                outputRawContactsAsVCard(queryUri, localStream, selection, null);
                return buildAssetFileDescriptor(localStream);
            }

            case CONTACTS_ID_PHOTO_CORP: {
                final long contactId = Long.parseLong(uri.getPathSegments().get(1));
                return openCorpContactPicture(contactId, uri, mode, /* displayPhoto =*/ false);
            }

            case CONTACTS_ID_DISPLAY_PHOTO_CORP: {
                final long contactId = Long.parseLong(uri.getPathSegments().get(1));
                return openCorpContactPicture(contactId, uri, mode, /* displayPhoto =*/ true);
            }

            default:
                throw new FileNotFoundException(
                        mDbHelper.get().exceptionMessage("File does not exist", uri));
        }
    }

    /**
     * Handles "/contacts_corp/ID/{photo,display_photo}", which refer to contact picures in the corp
     * CP2.
     */
    private AssetFileDescriptor openCorpContactPicture(long contactId, Uri uri, String mode,
            boolean displayPhoto) throws FileNotFoundException {
        if (!mode.equals("r")) {
            throw new IllegalArgumentException(
                    "Photos retrieved by contact ID can only be read.");
        }
        final int corpUserId = UserUtils.getCorpUserId(getContext(), true);
        if (corpUserId < 0) {
            // No corp profile or the currrent profile is not the personal.
            throw new FileNotFoundException(uri.toString());
        }
        // Convert the URI into:
        // content://USER@com.android.contacts/contacts_corp/ID/{photo,display_photo}
        final Uri corpUri = maybeAddUserId(
                ContentUris.appendId(Contacts.CONTENT_URI.buildUpon(), contactId)
                        .appendPath(displayPhoto ?
                                Contacts.Photo.DISPLAY_PHOTO : Contacts.Photo.CONTENT_DIRECTORY)
                        .build(), corpUserId);

        // TODO Make sure it doesn't leak any FDs.
        return getContext().getContentResolver().openAssetFileDescriptor(corpUri, mode);
    }

    private AssetFileDescriptor openPhotoAssetFile(
            SQLiteDatabase db, Uri uri, String mode, String selection, String[] selectionArgs)
            throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException(
                    mDbHelper.get().exceptionMessage("Mode " + mode + " not supported.", uri));
        }

        /**
         * SPRD:Bug474803 Sync Facebook contacts.
         * Original Android code:
        String sql = "SELECT " + Photo.PHOTO + " FROM " + Views.DATA + " WHERE " + selection;
         * @{
         */
        String dataView = mDbHelper.get().getDataView(ContactsDatabaseHelper.canAccessRestrictedData(getContext()));
        String sql = "SELECT " + Photo.PHOTO + " FROM " + dataView + " WHERE " + selection;
        /**
         * @}
         */
        try {
            return makeAssetFileDescriptor(
                    DatabaseUtils.blobFileDescriptorForQuery(db, sql, selectionArgs));
        } catch (SQLiteDoneException e) {
            // This will happen if the DB query returns no rows (i.e. contact does not exist).
            throw new FileNotFoundException(uri.toString());
        }
    }

    /**
     * Opens a display photo from the photo store for reading.
     * @param photoFileId The display photo file ID
     * @return An asset file descriptor that allows the file to be read.
     * @throws FileNotFoundException If no photo file for the given ID exists.
     */
    private AssetFileDescriptor openDisplayPhotoForRead(
            long photoFileId) throws FileNotFoundException {

        PhotoStore.Entry entry = mPhotoStore.get().get(photoFileId);
        if (entry != null) {
            try {
                return makeAssetFileDescriptor(
                        ParcelFileDescriptor.open(
                                new File(entry.path), ParcelFileDescriptor.MODE_READ_ONLY),
                        entry.size);
            } catch (FileNotFoundException fnfe) {
                scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
                throw fnfe;
            }
        } else {
            scheduleBackgroundTask(BACKGROUND_TASK_CLEANUP_PHOTOS);
            throw new FileNotFoundException("No photo file found for ID " + photoFileId);
        }
    }

    /**
     * Opens a file descriptor for a photo to be written.  When the caller completes writing
     * to the file (closing the output stream), the image will be parsed out and processed.
     * If processing succeeds, the given raw contact ID's primary photo record will be
     * populated with the inserted image (if no primary photo record exists, the data ID can
     * be left as 0, and a new data record will be inserted).
     * @param rawContactId Raw contact ID this photo entry should be associated with.
     * @param dataId Data ID for a photo mimetype that will be updated with the inserted
     *     image.  May be set to 0, in which case the inserted image will trigger creation
     *     of a new primary photo image data row for the raw contact.
     * @param uri The URI being used to access this file.
     * @param mode Read/write mode string.
     * @return An asset file descriptor the caller can use to write an image file for the
     *     raw contact.
     */
    private AssetFileDescriptor openDisplayPhotoForWrite(
            long rawContactId, long dataId, Uri uri, String mode) {

        try {
            ParcelFileDescriptor[] pipeFds = ParcelFileDescriptor.createPipe();
            PipeMonitor pipeMonitor = new PipeMonitor(rawContactId, dataId, pipeFds[0]);
            pipeMonitor.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Object[]) null);
            return new AssetFileDescriptor(pipeFds[1], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (IOException ioe) {
            Log.e(TAG, "Could not create temp image file in mode " + mode);
            return null;
        }
    }

    /**
     * Async task that monitors the given file descriptor (the read end of a pipe) for
     * the writer finishing.  If the data from the pipe contains a valid image, the image
     * is either inserted into the given raw contact or updated in the given data row.
     */
    private class PipeMonitor extends AsyncTask<Object, Object, Object> {
        private final ParcelFileDescriptor mDescriptor;
        private final long mRawContactId;
        private final long mDataId;
        private PipeMonitor(long rawContactId, long dataId, ParcelFileDescriptor descriptor) {
            mRawContactId = rawContactId;
            mDataId = dataId;
            mDescriptor = descriptor;
        }

        @Override
        protected Object doInBackground(Object... params) {
            AutoCloseInputStream is = new AutoCloseInputStream(mDescriptor);
            try {
                Bitmap b = BitmapFactory.decodeStream(is);
                if (b != null) {
                    waitForAccess(mWriteAccessLatch);
                    PhotoProcessor processor =
                            new PhotoProcessor(b, getMaxDisplayPhotoDim(), getMaxThumbnailDim());

                    // Store the compressed photo in the photo store.
                    PhotoStore photoStore = ContactsContract.isProfileId(mRawContactId)
                            ? mProfilePhotoStore
                            : mContactsPhotoStore;
                    long photoFileId = photoStore.insert(processor);

                    // Depending on whether we already had a data row to attach the photo
                    // to, do an update or insert.
                    if (mDataId != 0) {
                        // Update the data record with the new photo.
                        ContentValues updateValues = new ContentValues();

                        // Signal that photo processing has already been handled.
                        updateValues.put(DataRowHandlerForPhoto.SKIP_PROCESSING_KEY, true);

                        if (photoFileId != 0) {
                            updateValues.put(Photo.PHOTO_FILE_ID, photoFileId);
                        }
                        updateValues.put(Photo.PHOTO, processor.getThumbnailPhotoBytes());
                        update(ContentUris.withAppendedId(Data.CONTENT_URI, mDataId),
                                updateValues, null, null);
                    } else {
                        // Insert a new primary data record with the photo.
                        ContentValues insertValues = new ContentValues();

                        // Signal that photo processing has already been handled.
                        insertValues.put(DataRowHandlerForPhoto.SKIP_PROCESSING_KEY, true);

                        insertValues.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
                        insertValues.put(Data.IS_PRIMARY, 1);
                        if (photoFileId != 0) {
                            insertValues.put(Photo.PHOTO_FILE_ID, photoFileId);
                        }
                        insertValues.put(Photo.PHOTO, processor.getThumbnailPhotoBytes());
                        insert(RawContacts.CONTENT_URI.buildUpon()
                                .appendPath(String.valueOf(mRawContactId))
                                .appendPath(RawContacts.Data.CONTENT_DIRECTORY).build(),
                                insertValues);
                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IoUtils.closeQuietly(is);
            }
            return null;
        }
    }

    /**
     * Returns an {@link AssetFileDescriptor} backed by the
     * contents of the given {@link ByteArrayOutputStream}.
     */
    private AssetFileDescriptor buildAssetFileDescriptor(ByteArrayOutputStream stream) {
        try {
            stream.flush();

            final byte[] byteData = stream.toByteArray();
            return makeAssetFileDescriptor(
                    ParcelFileDescriptor.fromData(byteData, CONTACT_MEMORY_FILE_NAME),
                    byteData.length);
        } catch (IOException e) {
            Log.w(TAG, "Problem writing stream into an ParcelFileDescriptor: " + e.toString());
            return null;
        }
    }

    private AssetFileDescriptor makeAssetFileDescriptor(ParcelFileDescriptor fd) {
        return makeAssetFileDescriptor(fd, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private AssetFileDescriptor makeAssetFileDescriptor(ParcelFileDescriptor fd, long length) {
        return fd != null ? new AssetFileDescriptor(fd, 0, length) : null;
    }

    /**
     * Output {@link RawContacts} matching the requested selection in the vCard
     * format to the given {@link OutputStream}. This method returns silently if
     * any errors encountered.
     */
    private void outputRawContactsAsVCard(
            Uri uri, OutputStream stream, String selection, String[] selectionArgs) {

        final Context context = this.getContext();
        int vcardconfig = VCardConfig.VCARD_TYPE_DEFAULT;
        if(uri.getBooleanQueryParameter(Contacts.QUERY_PARAMETER_VCARD_NO_PHOTO, false)) {
            vcardconfig |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        final VCardComposer composer = new VCardComposer(context, vcardconfig, false);
        Writer writer = null;
        final Uri rawContactsUri;
        if (mapsToProfileDb(uri)) {
            // Pre-authorize the URI, since the caller would have already gone through the
            // permission check to get here, but the pre-authorization at the top level wouldn't
            // carry over to the raw contact.
            rawContactsUri = preAuthorizeUri(RawContactsEntity.PROFILE_CONTENT_URI);
        } else {
            rawContactsUri = RawContactsEntity.CONTENT_URI;
        }

        try {
            writer = new BufferedWriter(new OutputStreamWriter(stream));
            if (!composer.init(uri, selection, selectionArgs, null, rawContactsUri)) {
                Log.w(TAG, "Failed to init VCardComposer");
                return;
            }

            while (!composer.isAfterLast()) {
                writer.write(composer.createOneEntry());
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e);
        } finally {
            composer.terminate();
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException during closing output stream: " + e);
                }
            }
        }
    }

    @Override
    public String getType(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case CONTACTS:
                return Contacts.CONTENT_TYPE;
            case CONTACTS_LOOKUP:
            case CONTACTS_ID:
            case CONTACTS_LOOKUP_ID:
            case PROFILE:
                return Contacts.CONTENT_ITEM_TYPE;
            case CONTACTS_AS_VCARD:
            case CONTACTS_AS_MULTI_VCARD:
            case PROFILE_AS_VCARD:
                return Contacts.CONTENT_VCARD_TYPE;
            case CONTACTS_ID_PHOTO:
            case CONTACTS_LOOKUP_PHOTO:
            case CONTACTS_LOOKUP_ID_PHOTO:
            case CONTACTS_ID_DISPLAY_PHOTO:
            case CONTACTS_LOOKUP_DISPLAY_PHOTO:
            case CONTACTS_LOOKUP_ID_DISPLAY_PHOTO:
            case RAW_CONTACTS_ID_DISPLAY_PHOTO:
            case DISPLAY_PHOTO_ID:
                return "image/jpeg";
            case RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS:
                return RawContacts.CONTENT_TYPE;
            case RAW_CONTACTS_ID:
            case PROFILE_RAW_CONTACTS_ID:
                return RawContacts.CONTENT_ITEM_TYPE;
            case DATA:
            case PROFILE_DATA:
                return Data.CONTENT_TYPE;
            case DATA_ID:
                // We need db access for this.
                waitForAccess(mReadAccessLatch);

                long id = ContentUris.parseId(uri);
                if (ContactsContract.isProfileId(id)) {
                    return mProfileHelper.getDataMimeType(id);
                } else {
                    return mContactsHelper.getDataMimeType(id);
                }
            case PHONES:
            case PHONES_ENTERPRISE:
                return Phone.CONTENT_TYPE;
            case PHONES_ID:
                return Phone.CONTENT_ITEM_TYPE;
            case PHONE_LOOKUP:
            case PHONE_LOOKUP_ENTERPRISE:
                return PhoneLookup.CONTENT_TYPE;
            case EMAILS:
                return Email.CONTENT_TYPE;
            case EMAILS_ID:
                return Email.CONTENT_ITEM_TYPE;
            case POSTALS:
                return StructuredPostal.CONTENT_TYPE;
            case POSTALS_ID:
                return StructuredPostal.CONTENT_ITEM_TYPE;
            case AGGREGATION_EXCEPTIONS:
                return AggregationExceptions.CONTENT_TYPE;
            case AGGREGATION_EXCEPTION_ID:
                return AggregationExceptions.CONTENT_ITEM_TYPE;
            case SETTINGS:
                return Settings.CONTENT_TYPE;
            case AGGREGATION_SUGGESTIONS:
                return Contacts.CONTENT_TYPE;
            case SEARCH_SUGGESTIONS:
                return SearchManager.SUGGEST_MIME_TYPE;
            case SEARCH_SHORTCUT:
                return SearchManager.SHORTCUT_MIME_TYPE;
            case DIRECTORIES:
                return Directory.CONTENT_TYPE;
            case DIRECTORIES_ID:
                return Directory.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS:
                return StreamItems.CONTENT_TYPE;
            case STREAM_ITEMS_ID:
                return StreamItems.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS_ID_PHOTOS:
                return StreamItems.StreamItemPhotos.CONTENT_TYPE;
            case STREAM_ITEMS_ID_PHOTOS_ID:
                return StreamItems.StreamItemPhotos.CONTENT_ITEM_TYPE;
            case STREAM_ITEMS_PHOTOS:
                throw new UnsupportedOperationException("Not supported for write-only URI " + uri);
            default:
                waitForAccess(mReadAccessLatch);
                return mLegacyApiSupport.getType(uri);
        }
    }

    private String[] getDefaultProjection(Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case CONTACTS:
            case CONTACTS_LOOKUP:
            case CONTACTS_ID:
            case CONTACTS_LOOKUP_ID:
            case AGGREGATION_SUGGESTIONS:
            case PROFILE:
                return sContactsProjectionMap.getColumnNames();

            case CONTACTS_ID_ENTITIES:
            case PROFILE_ENTITIES:
                return sEntityProjectionMap.getColumnNames();

            case CONTACTS_AS_VCARD:
            case CONTACTS_AS_MULTI_VCARD:
            case PROFILE_AS_VCARD:
                return sContactsVCardProjectionMap.getColumnNames();

            case RAW_CONTACTS:
            case RAW_CONTACTS_ID:
            case PROFILE_RAW_CONTACTS:
            case PROFILE_RAW_CONTACTS_ID:
                return sRawContactsProjectionMap.getColumnNames();

            case DATA_ID:
            case PHONES:
            case PHONES_ENTERPRISE:
            case PHONES_ID:
            case EMAILS:
            case EMAILS_ID:
            case POSTALS:
            case POSTALS_ID:
            case PROFILE_DATA:
                return sDataProjectionMap.getColumnNames();

            case PHONE_LOOKUP:
            case PHONE_LOOKUP_ENTERPRISE:
                return sPhoneLookupProjectionMap.getColumnNames();

            case AGGREGATION_EXCEPTIONS:
            case AGGREGATION_EXCEPTION_ID:
                return sAggregationExceptionsProjectionMap.getColumnNames();

            case SETTINGS:
                return sSettingsProjectionMap.getColumnNames();

            case DIRECTORIES:
            case DIRECTORIES_ID:
                return sDirectoryProjectionMap.getColumnNames();

            default:
                return null;
        }
    }

    private class StructuredNameLookupBuilder extends NameLookupBuilder {

        public StructuredNameLookupBuilder(NameSplitter splitter) {
            super(splitter);
        }

        @Override
        protected void insertNameLookup(long rawContactId, long dataId, int lookupType,
                String name) {
            mDbHelper.get().insertNameLookup(rawContactId, dataId, lookupType, name);
        }

        @Override
        protected String[] getCommonNicknameClusters(String normalizedName) {
            return mCommonNicknameCache.getCommonNicknameClusters(normalizedName);
        }
    }

    public void appendContactFilterAsNestedQuery(StringBuilder sb, String filterParam) {
        sb.append("(" +
                "SELECT DISTINCT " + RawContacts.CONTACT_ID +
                " FROM " + Tables.RAW_CONTACTS +
                " JOIN " + Tables.NAME_LOOKUP +
                " ON(" + RawContactsColumns.CONCRETE_ID + "="
                        + NameLookupColumns.RAW_CONTACT_ID + ")" +
                " WHERE normalized_name GLOB '");
        sb.append(NameNormalizer.normalize(filterParam));
        sb.append("*' AND " + NameLookupColumns.NAME_TYPE +
                    " IN(" + CONTACT_LOOKUP_NAME_TYPES + "))");
    }

    private boolean isPhoneNumber(String query) {
        if (TextUtils.isEmpty(query)) {
            return false;
        }
        // Assume a phone number if it has at least 1 digit.
        return countPhoneNumberDigits(query) > 0;
    }

    /**
     * Returns the number of digits in a phone number ignoring special characters such as '-'.
     * If the string is not a valid phone number, 0 is returned.
     */
    public static int countPhoneNumberDigits(String query) {
        int numDigits = 0;
        int len = query.length();
        for (int i = 0; i < len; i++) {
            char c = query.charAt(i);
            if (Character.isDigit(c)) {
                numDigits ++;
            } else if (c == '*' || c == '#' || c == 'N' || c == '.' || c == ';'
                    || c == '-' || c == '(' || c == ')' || c == ' ') {
                // Carry on.
            } else if (c == '+' && numDigits == 0) {
                // Plus sign before any digits is OK.
            } else {
                return 0;  // Not a phone number.
            }
        }
        return numDigits;
    }

    /**
     * Takes components of a name from the query parameters and returns a cursor with those
     * components as well as all missing components.  There is no database activity involved
     * in this so the call can be made on the UI thread.
     */
    private Cursor completeName(Uri uri, String[] projection) {
        if (projection == null) {
            projection = sDataProjectionMap.getColumnNames();
        }

        ContentValues values = new ContentValues();
        DataRowHandlerForStructuredName handler = (DataRowHandlerForStructuredName)
                getDataRowHandler(StructuredName.CONTENT_ITEM_TYPE);

        copyQueryParamsToContentValues(values, uri,
                StructuredName.DISPLAY_NAME,
                StructuredName.PREFIX,
                StructuredName.GIVEN_NAME,
                StructuredName.MIDDLE_NAME,
                StructuredName.FAMILY_NAME,
                StructuredName.SUFFIX,
                StructuredName.PHONETIC_NAME,
                StructuredName.PHONETIC_FAMILY_NAME,
                StructuredName.PHONETIC_MIDDLE_NAME,
                StructuredName.PHONETIC_GIVEN_NAME
        );

        handler.fixStructuredNameComponents(values, values);

        MatrixCursor cursor = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            row[i] = values.get(projection[i]);
        }
        cursor.addRow(row);
        return cursor;
    }

    private void copyQueryParamsToContentValues(ContentValues values, Uri uri, String... columns) {
        for (String column : columns) {
            String param = uri.getQueryParameter(column);
            if (param != null) {
                values.put(column, param);
            }
        }
    }


    /**
     * Inserts an argument at the beginning of the selection arg list.
     */
    private String[] insertSelectionArg(String[] selectionArgs, String arg) {
        if (selectionArgs == null) {
            return new String[] {arg};
        }

        int newLength = selectionArgs.length + 1;
        String[] newSelectionArgs = new String[newLength];
        newSelectionArgs[0] = arg;
        System.arraycopy(selectionArgs, 0, newSelectionArgs, 1, selectionArgs.length);
        return newSelectionArgs;
    }

    private String[] appendSelectionArg(String[] selectionArgs, String arg) {
        if (selectionArgs == null) {
            return new String[] {arg};
        }

        int newLength = selectionArgs.length + 1;
        String[] newSelectionArgs = new String[newLength];
        newSelectionArgs[newLength] = arg;
        System.arraycopy(selectionArgs, 0, newSelectionArgs, 0, selectionArgs.length - 1);
        return newSelectionArgs;
    }

    protected Account getDefaultAccount() {
        AccountManager accountManager = AccountManager.get(getContext());
        try {
            Account[] accounts = accountManager.getAccountsByType(DEFAULT_ACCOUNT_TYPE);
            if (accounts != null && accounts.length > 0) {
                return accounts[0];
            }
            // for resolve default sprd account
            Log.e(TAG, "Default account is empty, we should add the phone account");
            Account account = new Account((String) getContext().getResources()
                    .getText(R.string.phone), DEFAULT_ACCOUNT_TYPE);
            accountManager.addAccountExplicitly(account, null, null);
            // set is syncable
            ContentResolver cr = getContext().getContentResolver();
            cr.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
            Log.e(TAG, "The default account is created: " + account);
            return account;
        } catch (Throwable e) {
            Log.e(TAG, "Cannot determine the default account for contacts compatibility", e);
        }
        return null;
    }

    /**
     * Returns true if the specified account type and data set is writable.
     */
    public boolean isWritableAccountWithDataSet(String accountTypeAndDataSet) {
        if (accountTypeAndDataSet == null) {
            return true;
        }

        Boolean writable = mAccountWritability.get(accountTypeAndDataSet);
        if (writable != null) {
            return writable;
        }

        IContentService contentService = ContentResolver.getContentService();
        try {
            // TODO(dsantoro): Need to update this logic to allow for sub-accounts.
            for (SyncAdapterType sync : contentService.getSyncAdapterTypes()) {
                if (ContactsContract.AUTHORITY.equals(sync.authority) &&
                        accountTypeAndDataSet.equals(sync.accountType)) {
                    writable = sync.supportsUploading();
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Could not acquire sync adapter types");
        }

        if (writable == null) {
            writable = false;
        }

        mAccountWritability.put(accountTypeAndDataSet, writable);
        return writable;
    }

    /* package */ static boolean readBooleanQueryParameter(
            Uri uri, String parameter, boolean defaultValue) {

        // Manually parse the query, which is much faster than calling uri.getQueryParameter
        String query = uri.getEncodedQuery();
        if (query == null) {
            return defaultValue;
        }

        int index = query.indexOf(parameter);
        if (index == -1) {
            return defaultValue;
        }

        index += parameter.length();

        return !matchQueryParameter(query, index, "=0", false)
                && !matchQueryParameter(query, index, "=false", true);
    }

    private static boolean matchQueryParameter(
            String query, int index, String value, boolean ignoreCase) {

        int length = value.length();
        return query.regionMatches(ignoreCase, index, value, 0, length)
                && (query.length() == index + length || query.charAt(index + length) == '&');
    }

    /**
     * A fast re-implementation of {@link Uri#getQueryParameter}
     */
    /* package */ static String getQueryParameter(Uri uri, String parameter) {
        String query = uri.getEncodedQuery();
        if (query == null) {
            return null;
        }

        int queryLength = query.length();
        int parameterLength = parameter.length();

        String value;
        int index = 0;
        while (true) {
            index = query.indexOf(parameter, index);
            if (index == -1) {
                return null;
            }

            // Should match against the whole parameter instead of its suffix.
            // e.g. The parameter "param" must not be found in "some_param=val".
            if (index > 0) {
                char prevChar = query.charAt(index - 1);
                if (prevChar != '?' && prevChar != '&') {
                    // With "some_param=val1&param=val2", we should find second "param" occurrence.
                    index += parameterLength;
                    continue;
                }
            }

            index += parameterLength;

            if (queryLength == index) {
                return null;
            }

            if (query.charAt(index) == '=') {
                index++;
                break;
            }
        }

        int ampIndex = query.indexOf('&', index);
        if (ampIndex == -1) {
            value = query.substring(index);
        } else {
            value = query.substring(index, ampIndex);
        }

        return Uri.decode(value);
    }

    private boolean isAggregationUpgradeNeeded() {
        if (!mContactAggregator.isEnabled()) {
            return false;
        }

        int version = Integer.parseInt(
                mContactsHelper.getProperty(DbProperties.AGGREGATION_ALGORITHM, "1"));
        return version < PROPERTY_AGGREGATION_ALGORITHM_VERSION;
    }

    private void upgradeAggregationAlgorithmInBackground() {
        Log.i(TAG, "Upgrading aggregation algorithm");

        final long start = SystemClock.elapsedRealtime();
        setProviderStatus(STATUS_UPGRADING);

        // Re-aggregate all visible raw contacts.
        try {
            int count = 0;
            SQLiteDatabase db = null;
            boolean success = false;
            boolean transactionStarted = false;
            try {
                // Re-aggregation is only for the contacts DB.
                switchToContactMode();
                db = mContactsHelper.getWritableDatabase();

                // Start the actual process.
                db.beginTransaction();
                transactionStarted = true;

                count = mContactAggregator.markAllVisibleForAggregation(db);
                mContactAggregator.aggregateInTransaction(mTransactionContext.get(), db);

                updateSearchIndexInTransaction();

                updateAggregationAlgorithmVersion();

                db.setTransactionSuccessful();

                success = true;
            } finally {
                mTransactionContext.get().clearAll();
                if (transactionStarted) {
                    db.endTransaction();
                }
                final long end = SystemClock.elapsedRealtime();
                Log.i(TAG, "Aggregation algorithm upgraded for " + count + " raw contacts"
                        + (success ? (" in " + (end - start) + "ms") : " failed"));
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to upgrade aggregation algorithm; continuing anyway.", e);

            // Got some exception during re-aggregation.  Re-aggregation isn't that important, so
            // just bump the aggregation algorithm version and let the provider start normally.
            try {
                final SQLiteDatabase db =  mContactsHelper.getWritableDatabase();
                db.beginTransaction();
                try {
                    updateAggregationAlgorithmVersion();
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            } catch (RuntimeException e2) {
                // Couldn't even update the algorithm version...  There's really nothing we can do
                // here, so just go ahead and start the provider.  Next time the provider starts
                // it'll try re-aggregation again, which may or may not succeed.
                Log.e(TAG, "Failed to bump aggregation algorithm version; continuing anyway.", e2);
            }
        } finally { // Need one more finally because endTransaction() may fail.
            setProviderStatus(STATUS_NORMAL);
        }
    }

    private void updateAggregationAlgorithmVersion() {
        mContactsHelper.setProperty(DbProperties.AGGREGATION_ALGORITHM,
                String.valueOf(PROPERTY_AGGREGATION_ALGORITHM_VERSION));
    }

    @VisibleForTesting
    protected boolean isPhone() {
        if (!mIsPhoneInitialized) {
            mIsPhone = new TelephonyManager(getContext()).isVoiceCapable();
            mIsPhoneInitialized = true;
        }
        return mIsPhone;
    }

    protected boolean isVoiceCapable() {
        // this copied from com.android.phone.PhoneApp.onCreate():

        // "voice capable" flag.
        // This flag currently comes from a resource (which is
        // overrideable on a per-product basis):
        return getContext().getResources()
                .getBoolean(com.android.internal.R.bool.config_voice_capable);
        // ...but this might eventually become a PackageManager "system
        // feature" instead, in which case we'd do something like:
        // return
        //   getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY_VOICE_CALLS);
    }

    private void undemoteContact(SQLiteDatabase db, long id) {
        final String[] arg = new String[1];
        arg[0] = String.valueOf(id);
        db.execSQL(UNDEMOTE_CONTACT, arg);
        db.execSQL(UNDEMOTE_RAW_CONTACT, arg);
    }

    private boolean handleDataUsageFeedback(Uri uri) {
        final long currentTimeMillis = Clock.getInstance().currentTimeMillis();
        final String usageType = uri.getQueryParameter(DataUsageFeedback.USAGE_TYPE);
        final String[] ids = uri.getLastPathSegment().trim().split(",");
        final ArrayList<Long> dataIds = new ArrayList<Long>(ids.length);

        for (String id : ids) {
            dataIds.add(Long.valueOf(id));
        }
        final boolean successful;
        if (TextUtils.isEmpty(usageType)) {
            Log.w(TAG, "Method for data usage feedback isn't specified. Ignoring.");
            successful = false;
        } else {
            successful = updateDataUsageStat(dataIds, usageType, currentTimeMillis) > 0;
        }

        // Handle old API. This doesn't affect the result of this entire method.
        final StringBuilder rawContactIdSelect = new StringBuilder();
        rawContactIdSelect.append("SELECT " + Data.RAW_CONTACT_ID + " FROM " + Tables.DATA +
                " WHERE " + Data._ID + " IN (");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                rawContactIdSelect.append(",");
            }
            rawContactIdSelect.append(ids[i]);
        }
        rawContactIdSelect.append(")");

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        mSelectionArgs1[0] = String.valueOf(currentTimeMillis);

        db.execSQL("UPDATE " + Tables.RAW_CONTACTS +
                " SET " + RawContacts.LAST_TIME_CONTACTED + "=?" +
                "," + RawContacts.TIMES_CONTACTED + "=" +
                    "ifnull(" + RawContacts.TIMES_CONTACTED + ",0) + 1" +
                " WHERE " + RawContacts._ID + " IN (" + rawContactIdSelect.toString() + ")"
                , mSelectionArgs1);
        db.execSQL("UPDATE " + Tables.CONTACTS +
                " SET " + Contacts.LAST_TIME_CONTACTED + "=?1" +
                "," + Contacts.TIMES_CONTACTED + "=" +
                    "ifnull(" + Contacts.TIMES_CONTACTED + ",0) + 1" +
                "," + Contacts.CONTACT_LAST_UPDATED_TIMESTAMP + "=?1" +
                " WHERE " + Contacts._ID + " IN (SELECT " + RawContacts.CONTACT_ID +
                    " FROM " + Tables.RAW_CONTACTS +
                    " WHERE " + RawContacts._ID + " IN (" + rawContactIdSelect.toString() + "))"
                , mSelectionArgs1);

        return successful;
    }

    private interface DataUsageStatQuery {
        String TABLE = Tables.DATA_USAGE_STAT;
        String[] COLUMNS = new String[] {DataUsageStatColumns._ID};
        int ID = 0;
        String SELECTION = DataUsageStatColumns.DATA_ID + " =? AND "
                + DataUsageStatColumns.USAGE_TYPE_INT + " =?";
    }

    /**
     * Update {@link Tables#DATA_USAGE_STAT}.
     *
     * @return the number of rows affected.
     */
    @VisibleForTesting
    /* package */ int updateDataUsageStat(
            List<Long> dataIds, String type, long currentTimeMillis) {

        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();

        final String typeString = String.valueOf(getDataUsageFeedbackType(type, null));
        final String currentTimeMillisString = String.valueOf(currentTimeMillis);

        for (long dataId : dataIds) {
            final String dataIdString = String.valueOf(dataId);
            mSelectionArgs2[0] = dataIdString;
            mSelectionArgs2[1] = typeString;
            final Cursor cursor = db.query(DataUsageStatQuery.TABLE,
                    DataUsageStatQuery.COLUMNS, DataUsageStatQuery.SELECTION,
                    mSelectionArgs2, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    final long id = cursor.getLong(DataUsageStatQuery.ID);

                    mSelectionArgs2[0] = currentTimeMillisString;
                    mSelectionArgs2[1] = String.valueOf(id);

                    db.execSQL("UPDATE " + Tables.DATA_USAGE_STAT +
                            " SET " + DataUsageStatColumns.TIMES_USED + "=" +
                                "ifnull(" + DataUsageStatColumns.TIMES_USED +",0)+1" +
                            "," + DataUsageStatColumns.LAST_TIME_USED + "=?" +
                            " WHERE " + DataUsageStatColumns._ID + "=?",
                            mSelectionArgs2);
                } else {
                    mSelectionArgs4[0] = dataIdString;
                    mSelectionArgs4[1] = typeString;
                    mSelectionArgs4[2] = "1"; // times used
                    mSelectionArgs4[3] = currentTimeMillisString;
                    db.execSQL("INSERT INTO " + Tables.DATA_USAGE_STAT +
                            "(" + DataUsageStatColumns.DATA_ID +
                            "," + DataUsageStatColumns.USAGE_TYPE_INT +
                            "," + DataUsageStatColumns.TIMES_USED +
                            "," + DataUsageStatColumns.LAST_TIME_USED +
                            ") VALUES (?,?,?,?)",
                            mSelectionArgs4);
                }
            } finally {
                cursor.close();
            }
        }

        return dataIds.size();
    }

    /**
     * Returns a sort order String for promoting data rows (email addresses, phone numbers, etc.)
     * associated with a primary account. The primary account should be supplied from applications
     * with {@link ContactsContract#PRIMARY_ACCOUNT_NAME} and
     * {@link ContactsContract#PRIMARY_ACCOUNT_TYPE}. Null will be returned when the primary
     * account isn't available.
     */
    private String getAccountPromotionSortOrder(Uri uri) {
        final String primaryAccountName =
                uri.getQueryParameter(ContactsContract.PRIMARY_ACCOUNT_NAME);
        final String primaryAccountType =
                uri.getQueryParameter(ContactsContract.PRIMARY_ACCOUNT_TYPE);

        // Data rows associated with primary account should be promoted.
        if (!TextUtils.isEmpty(primaryAccountName)) {
            StringBuilder sb = new StringBuilder();
            sb.append("(CASE WHEN " + RawContacts.ACCOUNT_NAME + "=");
            DatabaseUtils.appendEscapedSQLString(sb, primaryAccountName);
            if (!TextUtils.isEmpty(primaryAccountType)) {
                sb.append(" AND " + RawContacts.ACCOUNT_TYPE + "=");
                DatabaseUtils.appendEscapedSQLString(sb, primaryAccountType);
            }
            sb.append(" THEN 0 ELSE 1 END)");
            return sb.toString();
        }
        return null;
    }

    /**
     * Checks the URI for a deferred snippeting request
     * @return a boolean indicating if a deferred snippeting request is in the RI
     */
    private boolean deferredSnippetingRequested(Uri uri) {
        String deferredSnippeting =
                getQueryParameter(uri, SearchSnippets.DEFERRED_SNIPPETING_KEY);
        return !TextUtils.isEmpty(deferredSnippeting) &&  deferredSnippeting.equals("1");
    }

    /**
     * Checks if query is a single word or not.
     * @return a boolean indicating if the query is one word or not
     */
    private boolean isSingleWordQuery(String query) {
        // Split can remove empty trailing tokens but cannot remove starting empty tokens so we
        // have to loop.
        String[] tokens = query.split(QUERY_TOKENIZER_REGEX, 0);
        int count = 0;
        for (String token : tokens) {
            if (!"".equals(token)) {
                count++;
            }
        }
        return count == 1;
    }

    /**
     * Checks the projection for a SNIPPET column indicating that a snippet is needed
     * @return a boolean indicating if a snippet is needed or not.
     */
    private boolean snippetNeeded(String [] projection) {
        return ContactsDatabaseHelper.isInProjection(projection, SearchSnippets.SNIPPET);
    }

    /**
     * Replaces the package name by the corresponding package ID.
     *
     * @param values The {@link ContentValues} object to operate on.
     */
    private void replacePackageNameByPackageId(ContentValues values) {
        if (values != null) {
            final String packageName = values.getAsString(Data.RES_PACKAGE);
            if (packageName != null) {
                values.put(DataColumns.PACKAGE_ID, mDbHelper.get().getPackageId(packageName));
            }
            values.remove(Data.RES_PACKAGE);
        }
    }

    /**
     * Replaces the account info fields by the corresponding account ID.
     *
     * @param uri The relevant URI.
     * @param values The {@link ContentValues} object to operate on.
     * @return The corresponding account ID.
     */
    private long replaceAccountInfoByAccountId(Uri uri, ContentValues values) {
        final AccountWithDataSet account = resolveAccountWithDataSet(uri, values);
        final long id = mDbHelper.get().getOrCreateAccountIdInTransaction(account);
        values.put(RawContactsColumns.ACCOUNT_ID, id);

        // Only remove the account information once the account ID is extracted (since these
        // fields are actually used by resolveAccountWithDataSet to extract the relevant ID).
        values.remove(RawContacts.ACCOUNT_NAME);
        values.remove(RawContacts.ACCOUNT_TYPE);
        values.remove(RawContacts.DATA_SET);

        return id;
    }

    /**
     * Create a single row cursor for a simple, informational queries, such as
     * {@link ProviderStatus#CONTENT_URI}.
     */
    @VisibleForTesting
    static Cursor buildSingleRowResult(String[] projection, String[] availableColumns,
            Object[] data) {
        Preconditions.checkArgument(availableColumns.length == data.length);
        if (projection == null) {
            projection = availableColumns;
        }
        final MatrixCursor c = new MatrixCursor(projection, 1);
        final RowBuilder row = c.newRow();

        // It's O(n^2), but it's okay because we only have a few columns.
        for (int i = 0; i < c.getColumnCount(); i++) {
            final String columnName = c.getColumnName(i);

            boolean found = false;
            for (int j = 0; j < availableColumns.length; j++) {
                if (availableColumns[j].equals(columnName)) {
                    row.add(data[j]);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Invalid column " + projection[i]);
            }
        }
        return c;
    }

    /**
     * @return the currently active {@link ContactsDatabaseHelper} for the current thread.
     */
    @NeededForTesting
    protected ContactsDatabaseHelper getThreadActiveDatabaseHelperForTest() {
        return mDbHelper.get();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContactAggregator != null) {
            pw.print("Contact aggregator type: " + mContactAggregator.getClass() + "\n");
        }
        pw.print("FastScrollingIndex stats:\n");
        pw.printf("request=%d  miss=%d (%d%%)  avg time=%dms\n",
                mFastScrollingIndexCacheRequestCount,
                mFastScrollingIndexCacheMissCount,
                safeDiv(mFastScrollingIndexCacheMissCount * 100,
                        mFastScrollingIndexCacheRequestCount),
                safeDiv(mTotalTimeFastScrollingIndexGenerate, mFastScrollingIndexCacheMissCount));
    }

    private static final long safeDiv(long dividend, long divisor) {
        return (divisor == 0) ? 0 : dividend / divisor;
    }

    private static final int getDataUsageFeedbackType(String type, Integer defaultType) {
        if (DataUsageFeedback.USAGE_TYPE_CALL.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_CALL; // 0
        }
        if (DataUsageFeedback.USAGE_TYPE_LONG_TEXT.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_LONG_TEXT; // 1
        }
        if (DataUsageFeedback.USAGE_TYPE_SHORT_TEXT.equals(type)) {
            return DataUsageStatColumns.USAGE_TYPE_INT_SHORT_TEXT; // 2
        }
        if (defaultType != null) {
            return defaultType;
        }
        throw new IllegalArgumentException("Invalid usage type " + type);
    }

    /** Use only for debug logging */
    @Override
    public String toString() {
        return "ContactsProvider2";
    }

    @NeededForTesting
    public void switchToProfileModeForTest() {
        switchToProfileMode();
    }

    //--------------------------------- SPRD --------------------------------------
    /* SPRD: Matching callLog when search in dialpad feature @{ */
    public static String getLimitParam(Uri uri) {
        String limitParam = getQueryParameter(uri, ContactsContract.LIMIT_PARAM_KEY);
        if (limitParam == null) {
            return null;
        }
        // Make sure that the limit is a non-negative integer.
        try {
            int l = Integer.parseInt(limitParam);
            if (l < 0) {
                Log.w(TAG, "Invalid limit parameter: " + limitParam);
                return null;
            }
            return String.valueOf(l);

        } catch (NumberFormatException ex) {
            Log.w(TAG, "Invalid limit parameter: " + limitParam);
            return null;
        }
    }
    /* @} */
    /**
     * SPRD: Delete marked contacts when boot
     *
     * @{
     */

    private static final int BACKGROUND_TASK_CLEANUP_DELETED_LOCAL_CONTACTS = 20;

    private void cleanupDeletedLocalContacts() {
        SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL(
                    "DELETE FROM " + Tables.RAW_CONTACTS + " WHERE deleted = 1 AND " +
                            RawContactsColumns.ACCOUNT_ID + " in "
                            + Clauses.NOT_SYNCABLE_ACCOUNT_IDS
                    );
            db.setTransactionSuccessful();
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed clean up deleted local contacts because " + e.toString());
        } finally {
            try {
                db.endTransaction();
            } catch (SQLiteFullException exception) {
                exception.printStackTrace();
            }
        }
    }
    /**
     * @}
     */

    /**
     * SPRD:Bug 474798 SprdAppBackup for Contacts.
     *
     * @{
     */
    private int uniqueRawContacts() {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        Cursor cursor = db
                .rawQuery(
                        "select _id, contact_id, raw_contact_is_read_only from raw_contacts where deleted=0 and _id not in (select _id from raw_contacts where deleted=0 group by account_id, sync4 );"
                        , null
                );
        int ret = 0;
        try {
            if (cursor.moveToFirst()) {
                do {
                    ret++;
                    long rawContactId = cursor.getLong(0);
                    long contactId = cursor.getLong(1);
                    long isReadOnly = cursor.getLong(2);
                    if (isReadOnly == 0) {
                        deleteRawContact(rawContactId, contactId, false);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return ret;
    }
    /**
     * @}
     */

    /**
     * SPRD: Add by SPRD for cleaning up contacts Bug474742
     *
     * @{
     */
     private int uniqueRawContactsWithoutSim() {
        final SQLiteDatabase db = mDbHelper.get().getWritableDatabase();
        Cursor cursor = db
                .rawQuery(
                        "select _id, contact_id, raw_contact_is_read_only from view_raw_contacts where deleted=0 and" +
                        " account_type not in ('sprd.com.android.account.sim', 'sprd.com.android.account.usim')" +
                        " and _id not in (select _id from raw_contacts where deleted=0 group by account_id, sync4 );"
                        , null
                );
        int ret = 0;
        try {
            if (cursor.moveToFirst()) {
                do {
                    ret++;
                    long rawContactId = cursor.getLong(0);
                    long contactId = cursor.getLong(1);
                    long isReadOnly = cursor.getLong(2);
                    if (isReadOnly == 0) {
                        deleteRawContact(rawContactId, contactId, false);
                    }
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return ret;
    }

    private int mergeRawContacts(SQLiteDatabase db, ContentValues values) {
        int count = 0;
        Long targetRawContactId = values.getAsLong(RawContactsMerge.TARGET_RAW_CONTACT_ID);
        ArrayList<Long> subjectArray = RawContactsMerge.getRawContctsIdArray(values);

        if (targetRawContactId == null || subjectArray == null || subjectArray.isEmpty()) {
            return 0;
        }

        ContentValues convertedValues = new ContentValues();
        convertedValues.put(Data.RAW_CONTACT_ID, targetRawContactId);
        StringBuilder sb = new StringBuilder();
        String rawContactIdSelection;
        sb.append(Data.RAW_CONTACT_ID + " IN(");
        for (Long l : subjectArray) {
            sb.append(l + ",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        rawContactIdSelection = sb.toString();

        String updateMimeTypeException = "(mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForPhone()
                + " OR " + "mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForIm()
                + " OR " + "mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForEmail()
                + " OR " + "mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForSip()
                + " OR " + "mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForStructuredPostal()
                + " OR " + "mimetype_id = "
                + mDbHelper.get().getMimeTypeIdForWebsite() + ")";// bug 380174

        String selection;
        selection = rawContactIdSelection + " AND " + updateMimeTypeException;

        count += db.update(Tables.DATA, convertedValues, selection, null);

        String queryDup = "raw_contact_id=" + targetRawContactId + " AND _id not in (" +
                "select _id from data where raw_contact_id=" + targetRawContactId + " group by data1)";
        db.delete(Tables.DATA, queryDup, null);
        db.delete(Tables.DATA, rawContactIdSelection, null);

        count += db.update(Tables.PHONE_LOOKUP, convertedValues, rawContactIdSelection, null);

        count += db.delete(Tables.NAME_LOOKUP, rawContactIdSelection, null);
        sb.setLength(0);
        sb.append(RawContacts._ID + " IN(");
        for (Long l : subjectArray) {
            sb.append(l + ",");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        selection = sb.toString();

        Cursor cursor = db.query(Tables.RAW_CONTACTS, new String[] {
                RawContacts._ID, RawContacts.CONTACT_ID
        }, selection, null, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        count++;
                        long rawContactId = cursor.getLong(0);
                        long contactId = cursor.getLong(1);
                        deleteRawContact(rawContactId, contactId, false);
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
        }
        /**
         * SPRD:Bug520022 Can't search phone index after arranging contacts.
         * @{
         */
        mSearchIndexManager.updateIndex(true);
        /**
         * @}
         */
        return count;
    }

    private int joinRawContacts(SQLiteDatabase db, ContentValues valuse) {
        return 0;
    }
    /**
     * @}
     */
}
