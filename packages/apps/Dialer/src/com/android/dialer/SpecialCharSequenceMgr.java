/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.dialer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import com.android.common.io.MoreCloseables;
import com.android.contacts.common.database.NoNullCursorAsyncQueryHandler;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment;
import com.android.contacts.common.widget.SelectPhoneAccountDialogFragment.SelectPhoneAccountListener;
import com.android.dialer.calllog.PhoneAccountUtils;
import com.android.dialer.util.TelecomUtil;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import android.widget.ArrayAdapter;
import android.telecom.PhoneAccount;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import com.android.contacts.common.R;

import java.lang.reflect.Method;
import android.content.ComponentName;

/**
 * Helper class to listen for some magic character sequences
 * that are handled specially by the dialer.
 *
 * Note the Phone app also handles these sequences too (in a couple of
 * relatively obscure places in the UI), so there's a separate version of
 * this class under apps/Phone.
 *
 * TODO: there's lots of duplicated code between this class and the
 * corresponding class under apps/Phone.  Let's figure out a way to
 * unify these two classes (in the framework? in a common shared library?)
 */
public class SpecialCharSequenceMgr {
    private static final String TAG = "SpecialCharSequenceMgr";

    private static final String TAG_SELECT_ACCT_FRAGMENT = "tag_select_acct_fragment";

    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    private static final String MMI_IMEI_DISPLAY = "*#06#";
    private static final String MMI_REGULATORY_INFO_DISPLAY = "*#07#";
	private static final String WRITE_IMEI = "*#788#";
    private static final String PCBPROP_INFO_DISPLAY = "*#*#8#*#*";
	
    private static SimContactQueryCookie mSC;
    // SPRD: add for bug498143
    private static AlertDialog sDialog;
    /**
     * Remembers the previous {@link QueryHandler} and cancel the operation when needed, to
     * prevent possible crash.
     *
     * QueryHandler may call {@link ProgressDialog#dismiss()} when the screen is already gone,
     * which will cause the app crash. This variable enables the class to prevent the crash
     * on {@link #cleanup()}.
     *
     * TODO: Remove this and replace it (and {@link #cleanup()}) with better implementation.
     * One complication is that we have SpecialCharSequenceMgr in Phone package too, which has
     * *slightly* different implementation. Note that Phone package doesn't have this problem,
     * so the class on Phone side doesn't have this functionality.
     * Fundamental fix would be to have one shared implementation and resolve this corner case more
     * gracefully.
     */
    private static QueryHandler sPreviousAdnQueryHandler;

    public static class HandleAdnEntryAccountSelectedCallback extends SelectPhoneAccountListener{
        final private TelecomManager mTelecomManager;
        final private QueryHandler mQueryHandler;
        final private SimContactQueryCookie mCookie;

        public HandleAdnEntryAccountSelectedCallback(TelecomManager telecomManager,
                QueryHandler queryHandler, SimContactQueryCookie cookie) {
            mTelecomManager = telecomManager;
            mQueryHandler = queryHandler;
            mCookie = cookie;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            Uri uri = mTelecomManager.getAdnUriForPhoneAccount(selectedAccountHandle);
            handleAdnQuery(mQueryHandler, mCookie, uri);
            // TODO: Show error dialog if result isn't valid.
        }

    }

    public static class HandleMmiAccountSelectedCallback extends SelectPhoneAccountListener{
        final private Context mContext;
        final private String mInput;
        public HandleMmiAccountSelectedCallback(Context context, String input) {
            mContext = context.getApplicationContext();
            mInput = input;
        }

        @Override
        public void onPhoneAccountSelected(PhoneAccountHandle selectedAccountHandle,
                boolean setDefault) {
            TelecomUtil.handleMmi(mContext, mInput, selectedAccountHandle);
        }
    }

    /** This class is never instantiated. */
    private SpecialCharSequenceMgr() {
    }

    public static boolean handleChars(Context context, String input, EditText textField) {
        //get rid of the separators so that the string gets parsed correctly
        String dialString = PhoneNumberUtils.stripSeparators(input);

        if (handleDeviceIdDisplay(context, dialString)
                || handleRegulatoryInfoDisplay(context, dialString)
                || handleWriteIMEI(context, dialString)
                || handlePinEntry(context, dialString)
                || handleSecretCode(context, dialString)) {
            return true;
        }

        return false;
    }

    /**
     * Cleanup everything around this class. Must be run inside the main thread.
     *
     * This should be called when the screen becomes background.
     */
    public static void cleanup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.wtf(TAG, "cleanup() is called outside the main thread");
            return;
        }

        if (sPreviousAdnQueryHandler != null) {
            sPreviousAdnQueryHandler.cancel();
            sPreviousAdnQueryHandler = null;
        }

        /* SPRD: add for bug 498143 @{ */
        if (sDialog != null && sDialog.isShowing()) {
            sDialog.dismiss();
            sDialog = null;
        }
        /* @} */

        /* SPRD: add for bug494359 & 515570 @{*/
        if (mSC != null) {
           if (mSC.progressDialog != null && mSC.progressDialog.isShowing()) {
              mSC.progressDialog.dismiss();
              mSC.progressDialog = null;
           }
            mSC = null;
        }
        /* @} */
    }

    /**
     * Handles secret codes to launch arbitrary activities in the form of *#*#<code>#*#*.
     * If a secret code is encountered an Intent is started with the android_secret_code://<code>
     * URI.
     * 
     * @param context the context to use
     * @param input the text to check for a secret code in
     * @return true if a secret code was encountered
     */
    static boolean handleSecretCode(Context context, String input) {
        // Secret codes are in the form *#*#<code>#*#*
        int len = input.length();
        if (len > 8 && input.startsWith("*#*#") && input.endsWith("#*#*")) {
			if(input.equals("*#*#6#*#*")){
				Intent intent1 = new Intent();
                intent1.setClassName("com.cghs.stresstest", "com.cghs.stresstest.StressTestActivity");
                intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent1);
				return true;
			}else if(input.equals("*#*#0#*#*")){
                Intent intent1 = new Intent();				
                intent1.setClassName("com.zediel.pcbtest", "com.zediel.pcbtest.ZedielTools");
                intent1.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent1);
                return true;
        }
            final Intent intent = new Intent(SECRET_CODE_ACTION,
                    Uri.parse("android_secret_code://" + input.substring(4, len - 4)));
            context.sendBroadcast(intent);
            return true;
        }

        return false;
    }

    /** 
     * Handle ADN requests by filling in the SIM contact number into the requested
     * EditText.
     *
     * This code works alongside the Asynchronous query handler {@link QueryHandler}
     * and query cancel handler implemented in {@link SimContactQueryCookie}.
     */
    public static boolean handleAdnEntry(Context context, String input, EditText textField) {
        /* SPRD: add for bug 498143 @{ */
        if (context == null) {
            return false;
        }
        /* @} */

        /* SPRD: add for bug 494359 & 512521 @{ */
        if (mSC != null) {
            return false;
        }
        /* @} */

        /* ADN entries are of the form "N(N)(N)#" */
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null
                || telephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_GSM) {
            return false;
        }

        // if the phone is keyguard-restricted, then just ignore this
        // input.  We want to make sure that sim card contacts are NOT
        // exposed unless the phone is unlocked, and this code can be
        // accessed from the emergency dialer.
        KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager.inKeyguardRestrictedInputMode()) {
            return false;
        }

        int len = input.length();
        if ((len > 1) && (len < 5) && (input.endsWith("#"))) {
            try {
                // get the ordinal number of the sim contact
                final int index = Integer.parseInt(input.substring(0, len-1));

                // The original code that navigated to a SIM Contacts list view did not
                // highlight the requested contact correctly, a requirement for PTCRB
                // certification.  This behaviour is consistent with the UI paradigm
                // for touch-enabled lists, so it does not make sense to try to work
                // around it.  Instead we fill in the the requested phone number into
                // the dialer text field.

                // create the async query handler
                final QueryHandler handler = new QueryHandler (context.getContentResolver());

                // create the cookie object
                // SPRD: add for bug 494359
                mSC = new SimContactQueryCookie(index - 1, handler, ADN_QUERY_TOKEN);

                // setup the cookie fields
                mSC.contactNum = index - 1;
                mSC.setTextField(textField);

                // create the progress dialog
                mSC.progressDialog = new ProgressDialog(context);
                mSC.progressDialog.setTitle(R.string.simContacts_title);
                mSC.progressDialog.setMessage(context.getText(R.string.simContacts_emptyLoading));
                mSC.progressDialog.setIndeterminate(true);
                mSC.progressDialog.setCancelable(true);
                mSC.progressDialog.setOnCancelListener(mSC);
                mSC.progressDialog.getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

                final TelecomManager telecomManager =
                        (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                List<PhoneAccountHandle> subscriptionAccountHandles =
                        PhoneAccountUtils.getSubscriptionPhoneAccounts(context);

                boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                        telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL));

                if (subscriptionAccountHandles.size() == 1 || hasUserSelectedDefault) {
                    Uri uri = telecomManager.getAdnUriForPhoneAccount(null);
                    handleAdnQuery(handler, mSC, uri);
                } else if (subscriptionAccountHandles.size() > 1){
                    SelectPhoneAccountListener callback =
                            new HandleAdnEntryAccountSelectedCallback(telecomManager, handler, mSC);

                    /* SPRD: add for bug 498143 @{ */
                    //DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                    //        subscriptionAccountHandles, callback);
                    //dialogFragment.show(((Activity) context).getFragmentManager(),
                    //        TAG_SELECT_ACCT_FRAGMENT);
                    if (sDialog != null && sDialog.isShowing()) {
                        return true;
                    }

                    showDialog(context, subscriptionAccountHandles, callback);
                    /* @} */
                } else {
                    return false;
                }

                return true;
            } catch (NumberFormatException ex) {
                // Ignore
            }
        }
        return false;
    }

    private static void handleAdnQuery(QueryHandler handler, final SimContactQueryCookie cookie,
            Uri uri) {
        if (handler == null || cookie == null || uri == null) {
            Log.w(TAG, "queryAdn parameters incorrect");
            return;
        }

        // display the progress dialog
        /* SPRD: add for bug 494359 & 512521 @{ */
        //cookie.progressDialog.show();
        new Handler().postDelayed(new Runnable() {
            public void run() {
                if (mSC != null && !cookie.progressDialog.isShowing()) {
                    cookie.progressDialog.show();
                }
            }
        }, 100);
        /* @} */

        // run the query.
        handler.startQuery(ADN_QUERY_TOKEN, cookie, uri, new String[]{ADN_PHONE_NUMBER_COLUMN_NAME},
                null, null, null);

        if (sPreviousAdnQueryHandler != null) {
            // It is harmless to call cancel() even after the handler's gone.
            sPreviousAdnQueryHandler.cancel();
        }
        sPreviousAdnQueryHandler = handler;
    }

    static boolean handlePinEntry(final Context context, final String input) {
        if ((input.startsWith("**04") || input.startsWith("**05")) && input.endsWith("#")) {
            final TelecomManager telecomManager =
                    (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            List<PhoneAccountHandle> subscriptionAccountHandles =
                    PhoneAccountUtils.getSubscriptionPhoneAccounts(context);
            boolean hasUserSelectedDefault = subscriptionAccountHandles.contains(
                    telecomManager.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL));

            if (subscriptionAccountHandles.size() == 1 || hasUserSelectedDefault) {
                // Don't bring up the dialog for single-SIM or if the default outgoing account is
                // a subscription account.
                return TelecomUtil.handleMmi(context, input, null);
            } else if (subscriptionAccountHandles.size() > 1){
                SelectPhoneAccountListener listener =
                        new HandleMmiAccountSelectedCallback(context, input);

                /* SPRD: add for bug 498143 @{ */
                //DialogFragment dialogFragment = SelectPhoneAccountDialogFragment.newInstance(
                //        subscriptionAccountHandles, listener);
                //dialogFragment.show(((Activity) context).getFragmentManager(),
                //        TAG_SELECT_ACCT_FRAGMENT);
                showDialog(context, subscriptionAccountHandles, listener);
                /* @} */
            }
            return true;
        }
        return false;
    }

    // TODO: Use TelephonyCapabilities.getDeviceIdLabel() to get the device id label instead of a
    // hard-coded string.
    static boolean handleDeviceIdDisplay(Context context, String input) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null && input.equals(MMI_IMEI_DISPLAY)) {
            int labelResId = (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) ?
                    R.string.imei : R.string.meid;

            List<String> deviceIds = new ArrayList<String>();
            for (int slot = 0; slot < telephonyManager.getPhoneCount(); slot++) {
                String deviceId = telephonyManager.getDeviceId(slot);
                if (!TextUtils.isEmpty(deviceId)) {
                    deviceIds.add(deviceId);
                }
            }

            AlertDialog alert = new AlertDialog.Builder(context)
                    .setTitle(labelResId)
                    .setItems(deviceIds.toArray(new String[deviceIds.size()]), null)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
            return true;
        }
        return false;
    }
	private static boolean handleWriteIMEI(Context context, String input) {
        if (input.equals(WRITE_IMEI)) {
            Intent intent = new Intent("com.sprd.engineermode.dialog");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }
        return false;
    }
    private static boolean handleRegulatoryInfoDisplay(Context context, String input) {
        if (input.equals(MMI_REGULATORY_INFO_DISPLAY)) {
            Log.d(TAG, "handleRegulatoryInfoDisplay() sending intent to settings app");
            Intent showRegInfoIntent = new Intent(Settings.ACTION_SHOW_REGULATORY_INFO);
            try {
                context.startActivity(showRegInfoIntent);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "startActivity() failed: " + e);
            }
            return true;
        }else if(input.equals(PCBPROP_INFO_DISPLAY)){
			try{
				AlertDialog alert = new AlertDialog.Builder(context)
		            .setTitle("PCBProp Value")
					.setMessage(
						"Mem  = " + SystemProperties.get("ro.zediel.ddr", "unknown")	+ "\n" +
						"Lcd = " + SystemProperties.get("ro.zediel.lcd", "unknown")  + "\n" +
						"CTP = " + SystemProperties.get("ro.zediel.touch", "unknown")  + "\n" +
						"CP0 = " + SystemProperties.get("ro.zediel.cp0", "unknown")  + "\n" +
						"CP2 = " + SystemProperties.get("ro.zediel.cp2", "unknown")  + "\n" +
						"NV = " + SystemProperties.get("ro.zediel.nv", "unknown")  + "\n" +
						"OrderNum = " + SystemProperties.get("ro.zediel.ordernum", "unknown")  + "\n" +
						"End")
	                .setPositiveButton(android.R.string.ok, null)
	                .setCancelable(false)
	                .show();
			} catch (Exception e) {
                Log.e(TAG, "get PCBProp Value failed: " + e);
            }
			return true;
		}
        return false;
    }

    /*******
     * This code is used to handle SIM Contact queries
     *******/
    private static final String ADN_PHONE_NUMBER_COLUMN_NAME = "number";
    private static final String ADN_NAME_COLUMN_NAME = "name";
    private static final int ADN_QUERY_TOKEN = -1;
    // SPRD:modify by bug426816
    private static final String ADN_ANR_COLUMN_NAME = "anr";

    /**
     * Cookie object that contains everything we need to communicate to the
     * handler's onQuery Complete, as well as what we need in order to cancel
     * the query (if requested).
     *
     * Note, access to the textField field is going to be synchronized, because
     * the user can request a cancel at any time through the UI.
     */
    private static class SimContactQueryCookie implements DialogInterface.OnCancelListener{
        public ProgressDialog progressDialog;
        public int contactNum;

        // Used to identify the query request.
        private int mToken;
        private QueryHandler mHandler;

        // The text field we're going to update
        private EditText textField;

        public SimContactQueryCookie(int number, QueryHandler handler, int token) {
            contactNum = number;
            mHandler = handler;
            mToken = token;
        }

        /**
         * Synchronized getter for the EditText.
         */
        public synchronized EditText getTextField() {
            return textField;
        }

        /**
         * Synchronized setter for the EditText.
         */
        public synchronized void setTextField(EditText text) {
            textField = text;
        }

        /**
         * Cancel the ADN query by stopping the operation and signaling
         * the cookie that a cancel request is made.
         */
        public synchronized void onCancel(DialogInterface dialog) {
            // close the progress dialog
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

            // setting the textfield to null ensures that the UI does NOT get
            // updated.
            textField = null;

            // Cancel the operation if possible.
            mHandler.cancelOperation(mToken);
            // SPRD: modify for bug512521
            mSC = null;
        }
    }

    /**
     * Asynchronous query handler that services requests to look up ADNs
     *
     * Queries originate from {@link #handleAdnEntry}.
     */
    private static class QueryHandler extends NoNullCursorAsyncQueryHandler {

        private boolean mCanceled;

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        /**
         * Override basic onQueryComplete to fill in the textfield when
         * we're handed the ADN cursor.
         */
        @Override
        protected void onNotNullableQueryComplete(int token, Object cookie, Cursor c) {
            try {
                sPreviousAdnQueryHandler = null;
                if (mCanceled) {
                    return;
                }

                SimContactQueryCookie sc = (SimContactQueryCookie) cookie;

                // close the progress dialog.
                /* SPRD: add for bug512521 @{ */
                if (sc.progressDialog.isShowing()) {
                    sc.progressDialog.dismiss();
                }
                /* @} */

                // get the EditText to update or see if the request was cancelled.
                EditText text = sc.getTextField();
                // SPRD: modify by bug494062
                Context context = sc.progressDialog.getContext();

                // if the TextView is valid, and the cursor is valid and positionable on the
                // Nth number, then we update the text field and display a toast indicating the
                // caller name.
                if ((c != null) && (text != null) && (c.moveToPosition(sc.contactNum))) {
                    String name = c.getString(c.getColumnIndexOrThrow(ADN_NAME_COLUMN_NAME));
                    String number =
                            c.getString(c.getColumnIndexOrThrow(ADN_PHONE_NUMBER_COLUMN_NAME));

                    /* SPRD: modify by bug494062 @{ */
                    String anr = c.getString(c.getColumnIndexOrThrow(ADN_ANR_COLUMN_NAME));
                    if (TextUtils.isEmpty(number)) {
                        if (TextUtils.isEmpty(anr)) {
                            Toast.makeText(context,
                                    R.string.number_anr_isnull,Toast.LENGTH_SHORT).show();
                            // SPRD: modify for bug516356
                            mSC = null;
                            return;
                        } else {
                            Log.d(TAG, "Anr number:" + anr);
                            number = anr;
                        }
                    }
                    /* @} */

                    /* SPRD: add for bug 494359 @{ */
                    int oldLen = text.getText().length();
                    // fill the text in.
                    text.getText().replace(0, oldLen, number);
                    /* @} */

                    // display the name as a toast
                    /* SPRD: ADD FOR BUG525616 @{ */
                    if ((name != null && (name.isEmpty() || name.trim().isEmpty()))
                            || name == null) {
                        name = context.getString(R.string.menu_callNumber, "");
                    } else {
                        name = context.getString(R.string.menu_callNumber, name);
                    }
                    /* @} */
                    Toast.makeText(context, name, Toast.LENGTH_SHORT)
                        .show();

                    /* SPRD: add for bug 494359 @{ */
                } else if ((c != null) && !(c.moveToPosition(sc.contactNum)) && (text != null)) {
                    text.getText().clear();
                    /* @} */
                    /* SPRD: add for bug512521 @{ */
                    Toast.makeText(context, R.string.no_available_number,
                            Toast.LENGTH_SHORT).show();
                    /* @} */
                }
                // SPRD: modify for bug512521
                mSC = null;
            } finally {
                MoreCloseables.closeQuietly(c);
            }
        }

        public void cancel() {
            mCanceled = true;
            // Ask AsyncQueryHandler to cancel the whole request. This will fail when the query is
            // already started.
            cancelOperation(ADN_QUERY_TOKEN);
        }
    }

    /**
     *
     *  SPRD: add for bug 498143 @{
     *
     */
    private static class SelectAccountListAdapter extends ArrayAdapter<PhoneAccountHandle> {
        private int mResId;
        private Context mContext;

        public SelectAccountListAdapter(
                Context context, int resource, List<PhoneAccountHandle> accountHandles) {
            super(context, resource, accountHandles);
            mResId = resource;
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)
                    getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View rowView;
            final ViewHolder holder;

            if (convertView == null) {
                // Cache views for faster scrolling
                rowView = inflater.inflate(mResId, null);
                holder = new ViewHolder();
                holder.labelTextView = (TextView) rowView.findViewById(R.id.label);
                holder.numberTextView = (TextView) rowView.findViewById(R.id.number);
                holder.imageView = (ImageView) rowView.findViewById(R.id.icon);
                rowView.setTag(holder);
            } else {
                rowView = convertView;
                holder = (ViewHolder) rowView.getTag();
            }

            TelecomManager telephonyManager =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            PhoneAccountHandle accountHandle = getItem(position);
            PhoneAccount account = telephonyManager.getPhoneAccount(accountHandle);
            // SPRD: add for bug544932
            if (account == null) {
                Log.d(TAG, "SelectAccountListAdapter: account is null" );
                return rowView;
            }
            holder.labelTextView.setText(account.getLabel());
            if (account.getAddress() == null ||
                    TextUtils.isEmpty(account.getAddress().getSchemeSpecificPart())) {
                holder.numberTextView.setVisibility(View.GONE);
            } else {
                holder.numberTextView.setVisibility(View.VISIBLE);
                holder.numberTextView.setText(
                        PhoneNumberUtils.createTtsSpannable(
                                account.getAddress().getSchemeSpecificPart()));
            }

            if (account.getIcon() != null) {
                holder.imageView.setImageDrawable(account.getIcon().loadDrawable(mContext));
            }
            return rowView;
        }

        private class ViewHolder {
            TextView labelTextView;
            TextView numberTextView;
            ImageView imageView;
        }
    }

    public static void showDialog(Context context, List<PhoneAccountHandle> accountHandles,
            SelectPhoneAccountListener listener) {
        final List<PhoneAccountHandle> mAccountHandles = accountHandles;
        final SelectPhoneAccountListener mlistener = listener;
        int sTitleResId = R.string.phone_account_settings_label;
        final DialogInterface.OnClickListener selectionListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PhoneAccountHandle selectedAccountHandle = mAccountHandles.get(which);
                mlistener.onPhoneAccountSelected(selectedAccountHandle, false);
            }
        };
        /* SPRD: add for bug515570 @{ */
        final DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (mSC != null) {
                            mSC = null;
                        }
                    }
                };
        /* @} */

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        ListAdapter selectAccountListAdapter = new SelectAccountListAdapter(
                builder.getContext(),
                R.layout.select_account_list_item,
                mAccountHandles);

        sDialog = builder.setTitle(sTitleResId)
                .setAdapter(selectAccountListAdapter, selectionListener)
                // SPRD: modify for bug515570
                .setOnCancelListener(cancelListener)
                .create();

        sDialog.show();
    }
    /** @} */
}
