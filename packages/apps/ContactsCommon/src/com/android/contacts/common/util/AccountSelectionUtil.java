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
 * limitations under the License.
 */

package com.android.contacts.common.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.contacts.common.R;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.vcard.ImportVCardActivity;

import java.util.List;

/**
 * SPRD:
 *
 * @{
 */
import android.widget.ImageView;
import com.sprd.contacts.common.model.account.SimAccountType;
import com.sprd.contacts.common.model.account.USimAccountType;
import java.util.ArrayList;
import java.util.Iterator;
/**
 * @}
 */

/**
 * Utility class for selecting an Account for importing contact(s)
 */
public class AccountSelectionUtil {
    // TODO: maybe useful for EditContactActivity.java...
    private static final String LOG_TAG = "AccountSelectionUtil";

    public static boolean mVCardShare = false;

    public static Uri mPath;

    public static class AccountSelectedListener
            implements DialogInterface.OnClickListener {

        final private Activity mActivity;
        final private int mResId;
        final private int mSubscriptionId;
        final protected List<AccountWithDataSet> mAccountList;

        public AccountSelectedListener(Activity activity, List<AccountWithDataSet> accountList,
                int resId, int subscriptionId) {
            if (accountList == null || accountList.size() == 0) {
                Log.e(LOG_TAG, "The size of Account list is 0.");
            }
            mActivity = activity;
            mAccountList = accountList;
            mResId = resId;
            mSubscriptionId = subscriptionId;
        }

        public AccountSelectedListener(Activity activity, List<AccountWithDataSet> accountList,
                int resId) {
            // Subscription id is only needed for importing from SIM card. We can safely ignore
            // its value for SD card importing.
            this(activity, accountList, resId, /* subscriptionId = */ -1);
        }

        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            doImport(mActivity, mResId, mAccountList.get(which), mSubscriptionId);
        }
    }

    public static Dialog getSelectAccountDialog(Activity activity, int resId) {
        /**
         * SPRD:
         * Original Android code:
        return getSelectAccountDialog(activity, resId, null, null);
         *
         * @{
         */
        return getSelectAccountDialog(activity, resId, null, null, false);
        /**
         * @}
         */
    }

    public static Dialog getSelectAccountDialog(Activity activity, int resId,
            DialogInterface.OnClickListener onClickListener) {
        /**
         * SPRD:
         * Original Android code:
        return getSelectAccountDialog(activity, resId, onClickListener, null);
         * @{
         */
        return getSelectAccountDialog(activity, resId, onClickListener, null, false);
        /**
         * @}
         */
    }

    /**
     * When OnClickListener or OnCancelListener is null, uses a default listener.
     * The default OnCancelListener just closes itself with {@link Dialog#dismiss()}.
     */
    public static Dialog getSelectAccountDialog(Activity activity, int resId,
            DialogInterface.OnClickListener onClickListener,
            /**
             * Original Android code:
             *
            DialogInterface.OnCancelListener onCancelListener) {
             * @{
             */
            DialogInterface.OnCancelListener onCancelListener, boolean IsDialogFragment) {
            /**
             * @}
             */
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(activity);
        /**
         * SPRD:
         * Original Android code:
         *
        final List<AccountWithDataSet> writableAccountList = accountTypes.getAccounts(true);
         * @{
         */
        final ArrayList<AccountWithDataSet> writableAccountList =
                (ArrayList) accountTypes.getAccounts(true);
        ArrayList<AccountWithDataSet> accounts = (ArrayList) writableAccountList.clone();
        Iterator<AccountWithDataSet> iter = accounts.iterator();
        while (iter.hasNext()) {
            AccountWithDataSet accountWithDataSet = iter.next();
            if (SimAccountType.ACCOUNT_TYPE.equals(accountWithDataSet.type)
                    || USimAccountType.ACCOUNT_TYPE.equals(accountWithDataSet.type)) {
                iter.remove();
            }
        }
        /**
         * @}
         */
        Log.i(LOG_TAG, "The number of available accounts: " + writableAccountList.size());

        // Assume accountList.size() > 1

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(
                activity, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final ArrayAdapter<AccountWithDataSet> accountAdapter =
            /**
             * SPRD:
             * Original Android code:
             *
            new ArrayAdapter<AccountWithDataSet>(activity, android.R.layout.simple_list_item_2,
                    writableAccountList) {
             * @{
             */
            new ArrayAdapter<AccountWithDataSet>(activity, R.layout.account_selector_list_item,
                        accounts) {
            /**
             * @}
             */

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    /**
                     * SPRD:
                     * Original Android code:
                     *
                    convertView = dialogInflater.inflate(
                            android.R.layout.simple_list_item_2,
                            parent, false);
                      * @{
                      */
                    convertView = dialogInflater.inflate(
                            R.layout.account_selector_list_item,
                            parent, false);
                    /**
                     * @}
                     */
                }

                // TODO: show icon along with title
                final TextView text1 =
                        (TextView)convertView.findViewById(android.R.id.text1);
                final TextView text2 =
                        (TextView)convertView.findViewById(android.R.id.text2);
                /**
                 * SPRD:
                 *
                 * @{
                 */
                final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);
                /**
                 * @}
                 */
                final AccountWithDataSet account = this.getItem(position);
                final AccountType accountType = accountTypes.getAccountType(
                        account.type, account.dataSet);
                final Context context = getContext();

                /**
                 * SPRD:
                 * Original Android code:
                text1.setText(account.name);
                text2.setText(accountType.getDisplayLabel(context));
                 * @{
                 */
                if (account.type.equals("sprd.com.android.account.phone")) {
                    text2.setText(context.getString(R.string.label_phone));
                } else {
                    text2.setText(account.name);
                }
                icon.setImageDrawable(accountType.getDisplayIcon(context));
                text1.setText(accountType.getDisplayLabel(context));
                /**
                 * @}
                 */

                return convertView;
            }
        };

        if (onClickListener == null) {
            AccountSelectedListener accountSelectedListener =
                /**
                 * SPRD:
                 * Original Android code:
                new AccountSelectedListener(activity, writableAccountList, resId);
                 * @{
                 */
                new AccountSelectedListener(activity, accounts, resId);
                /**
                 * @}
                 */

            onClickListener = accountSelectedListener;
        }
        /**
         * SPRD:
         * Original Android code:
        if (onCancelListener == null) {
         * @{
         */
        if (!IsDialogFragment && onCancelListener == null) {
        /**
         * @}
         */
            onCancelListener = new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    dialog.dismiss();
                }
            };
        }
        /**
         * SPRD:
         * Original Android code:
        return new AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_new_contact_account)
            .setSingleChoiceItems(accountAdapter, 0, onClickListener)
            .setOnCancelListener(onCancelListener)
            .create();
         * @{
         */
        if (!IsDialogFragment) {
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_new_contact_account)
                    .setSingleChoiceItems(accountAdapter, 0, onClickListener)
                    .setOnCancelListener(onCancelListener)
                    .create();
        } else {
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.dialog_new_contact_account)
                    .setSingleChoiceItems(accountAdapter, 0, onClickListener)
                    .create();
        }
        /**
         * @}
         */
    }


   public static void doImport(Activity activity, int resId, AccountWithDataSet account,
            int subscriptionId) {
        switch (resId) {
            case R.string.import_from_sim: {
                doImportFromSim(activity, account, subscriptionId);
                break;
            }
            case R.string.import_from_vcf_file: {
                doImportFromVcfFile(activity, account);
                break;
            }
        }
    }

    public static void doImportFromSim(Context context, AccountWithDataSet account,
            int subscriptionId) {
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }
        importIntent.putExtra("subscription_id", (Integer) subscriptionId);
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        context.startActivity(importIntent);
    }

    public static void doImportFromVcfFile(Activity activity, AccountWithDataSet account) {
        Intent importIntent = new Intent(activity, ImportVCardActivity.class);
        if (account != null) {
            importIntent.putExtra("account_name", account.name);
            importIntent.putExtra("account_type", account.type);
            importIntent.putExtra("data_set", account.dataSet);
        }

        if (mVCardShare) {
            importIntent.setAction(Intent.ACTION_VIEW);
            importIntent.setData(mPath);
        }
        mVCardShare = false;
        mPath = null;
        activity.startActivityForResult(importIntent, 0);
    }

    /**
    * SPRD:
    *
    * @{
    */
    public static Dialog getSelectAccountDialog(Activity activity, int resId,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnCancelListener onCancelListener) {
        return getSelectAccountDialog(activity, resId, onClickListener, onCancelListener, false);
    }
    /**
    * @}
    */
}
