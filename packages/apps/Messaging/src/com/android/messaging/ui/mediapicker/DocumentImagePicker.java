/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.messaging.ui.mediapicker;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.messaging.Factory;
import com.android.messaging.datamodel.data.PendingAttachmentData;
import com.android.messaging.ui.UIIntents;
import com.android.messaging.util.LogUtil;
import com.android.messaging.util.FileUtil;
import com.android.messaging.util.ImageUtils;
import com.android.messaging.util.SafeAsyncTask;
import com.android.messaging.util.UiUtils;
import com.android.messaging.util.UriUtil;

import java.io.File;

import com.sprd.messaging.drm.MessagingDrmSession;
import com.sprd.messaging.drm.MessagingDrmHelper;
import android.util.Log;
import com.android.messaging.util.UiUtils;
import com.android.messaging.R;
import com.android.messaging.util.ContentType;

/**
 * Wraps around the functionalities to allow the user to pick images from the document
 * picker.  Instances of this class must be tied to a Fragment which is able to delegate activity
 * result callbacks.
 */
public class DocumentImagePicker {

    /**
     * An interface for a listener that listens for when a document has been picked.
     */
    public interface SelectionListener {
        /**
         * Called when an document is selected from picker. At this point, the file hasn't been
         * actually loaded and staged in the temp directory, so we are passing in a pending
         * MessagePartData, which the consumer should use to display a placeholder image.
         * @param pendingItem a temporary attachment data for showing the placeholder state.
         */
        void onDocumentSelected(PendingAttachmentData pendingItem);
    }

    // The owning fragment.
    private final Fragment mFragment;

    // The listener on the picker events.
    private final SelectionListener mListener;

    private static final String EXTRA_PHOTO_URL = "photo_url";
    private static final String TAG="DocumentImagePicker";

    /**
     * Creates a new instance of DocumentImagePicker.
     * @param activity The activity that owns the picker, or the activity that hosts the owning
     *        fragment.
     */
    public DocumentImagePicker(final Fragment fragment,
            final SelectionListener listener) {
        mFragment = fragment;
        mListener = listener;
    }

    /**
     * Intent out to open an image/video from document picker.
     */
    public void launchPicker() {
        UIIntents.get().launchDocumentImagePicker(mFragment);
    }

    /**
     * Must be called from the fragment/activity's onActivityResult().
     */
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == UIIntents.REQUEST_PICK_IMAGE_FROM_DOCUMENT_PICKER &&
                resultCode == Activity.RESULT_OK) {
            // Sometimes called after media item has been picked from the document picker.
            String url = data.getStringExtra(EXTRA_PHOTO_URL);
            if (url == null) {
                // we're using the builtin photo picker which supplies the return
                // url as it's "data"
                url = data.getDataString();
                if (url == null) {
                    final Bundle extras = data.getExtras();
                    if (extras != null) {
                        final Uri uri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
                        if (uri != null) {
                            url = uri.toString();
                        }
                    }
                }
            }

            // Guard against null uri cases for when the activity returns a null/invalid intent.
            if (url != null) {
                final Uri uri = Uri.parse(url);
/*                try{
                    String path = MessagingDrmSession.get().getPath(uri);
                    Log.d(TAG, "path is "+path);
                    if (path!=null&&MessagingDrmSession.get().drmCanHandle(path, null)){
                         if (MessagingDrmHelper.isDrmFLFile(mFragment.getContext(), path, null)
                             || MessagingDrmHelper.isDrmCDFile(mFragment.getContext(), path, null)) {
                           Log.d(TAG, " fl or cd mimetype path " + path);
                           UiUtils.showToastAtBottom(R.string.share_muimgae_exceeded);
                           return;
                        }
                    }
                }catch(Exception ex){
                    Log.d(TAG, "onActivityResult ex  " + ex);
                }*/
                prepareDocumentForAttachment(uri);
            }
        }
    }

    private boolean isDrmImageSupport(String origType){
        return ContentType.isDrmImageSupport(origType);
    }

    private void prepareDocumentForAttachment(final Uri documentUri) {
        // Notify our listener with a PendingAttachmentData containing the metadata.
        // Asynchronously get the content type for the picked image since
        // ImageUtils.getContentType() potentially involves I/O and can be expensive.
        new SafeAsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackgroundTimed(final Void... params) {
                if (UriUtil.isFileUri(documentUri) &&
                        FileUtil.isInDataDir(new File(documentUri.getPath()))) {
                    // hacker sending private app data. Bail out
                    if (LogUtil.isLoggable(LogUtil.BUGLE_TAG, LogUtil.ERROR)) {
                        LogUtil.e(LogUtil.BUGLE_TAG, "Aborting attach of private app data ("
                                + documentUri + ")");
                    }
                    return null;
                }
                return ImageUtils.getContentType(
                        Factory.get().getApplicationContext().getContentResolver(), documentUri);
            }

            @Override
            protected void onPostExecute(final String contentType) {
                // Ask the listener to create a temporary placeholder item to show the progress.
                String type = contentType;
				if (contentType == null) {
                    return;     // bad uri on input
                }
				
                try{
                    String path = MessagingDrmSession.get().getPath(documentUri);
                    Log.d(TAG, "path is "+path);
                    if (path!=null&&MessagingDrmSession.get().drmCanHandle(path, null)){
                        type = ContentType.APP_DRM_CONTENT;
                        String drmOrigContentType = MessagingDrmSession.get().getDrmOrigMimeType(path, ContentType.APP_DRM_CONTENT);
                        if (ContentType.isImageType(drmOrigContentType) && !isDrmImageSupport(drmOrigContentType)) {
                           UiUtils.showToastAtBottom(R.string.orgdrm_no_share);
                           return;
                        }
                        if (path != null && !path.contains("/DrmDownload/")) {
                            UiUtils.showToastAtBottom(R.string.drm_shared_not_drmdownload);
                            return;
                        }
                    }
                }catch(Exception ex){
                    Log.d(TAG, "onActivityResult ex  " + ex);
                }
                Log.d(TAG, "type is "+type);
                final PendingAttachmentData pendingItem =
                        PendingAttachmentData.createPendingAttachmentData(type,
                                documentUri);
                mListener.onDocumentSelected(pendingItem);
            }
        }.executeOnThreadPool();
    }
}
