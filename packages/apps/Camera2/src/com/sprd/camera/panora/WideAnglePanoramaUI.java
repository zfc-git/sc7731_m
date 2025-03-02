/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.sprd.camera.panora;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.camera.CameraActivity;
import com.android.camera.util.CameraUtil;
import com.android.camera.PanoProgressBar;
import com.android.camera.ShutterButton;
import com.android.camera.ui.PreviewStatusListener;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera2.R;

/**
 * The UI of {@link WideAnglePanoramaModule}.
 */
public class WideAnglePanoramaUI implements
        TextureView.SurfaceTextureListener,
        PreviewStatusListener,
        View.OnLayoutChangeListener {

    @SuppressWarnings("unused")
    private static final String TAG = "CAM_WidePanoramaUI";

    private CameraActivity mActivity;
    private WideAnglePanoramaController mController;

    private ViewGroup mRootView;
    private FrameLayout mCaptureLayout;
    private View mReviewLayout;
    private ImageView mReview;
    private ImageView mPreviewThumb;
    private View mPreviewBorder;
    private View mLeftIndicator;
    private View mRightIndicator;
    private View mCaptureIndicator;
    private PanoProgressBar mCaptureProgressBar;
    private PanoProgressBar mSavingProgressBar;
    private TextView mTooFastPrompt;
    private View mPreviewLayout;
    private ViewGroup mReviewControl;
    private TextureView mTextureView;
    private ShutterButton mShutterButton;
    // CID 109176 : UuF: Unused field (FB.UUF_UNUSED_FIELD)
    // private boolean mEnablePreviwThumb;

    private Matrix mProgressDirectionMatrix = new Matrix();
    private float[] mProgressAngle = new float[2];

    private DialogHelper mDialogHelper;

    // Color definitions.
    private int mIndicatorColor;
    private int mIndicatorColorFast;
    private int mReviewBackground;
    private SurfaceTexture mSurfaceTexture;
    private View mPreviewCover;

    // SPRD:Add for bug 479120 wideangle module rotate issue
    private FrameLayout mProgressLayout;

    /** Constructor. */
    public WideAnglePanoramaUI(
            CameraActivity activity,
            WideAnglePanoramaController controller,
            ViewGroup root) {
        mActivity = activity;
        mController = controller;
        mRootView = root;

        createContentView();
        initFilterMakeupButton();
    }

    public void onStartCapture() {
        // SPRD Bug WideAngle Rotate
        int orientation = 0;
        if (mProgressLayout != null) {
            orientation = (int) mProgressLayout.getRotation();
        }
        if (orientation == 0 || orientation == 180) {
            if (mProgressLayout != null && mPanoBg != null && mCaptureProgressBar != null)
                mProgressLayout.setPadding(0, 0, 0,
                        mPanoBg.getHeight() - mCaptureProgressBar.getHeight());
        } else if (orientation == 90 || orientation == 270) {
            if (mProgressLayout != null)
                mProgressLayout.setPadding(0, 0, 0, 20);
        }

        mShutterButton.setImageResource(R.drawable.ic_capture_pano_stop);
        mCaptureIndicator.setVisibility(View.VISIBLE);
        showDirectionIndicators(PanoProgressBar.DIRECTION_NONE);
    }

    public void setShutterButtonVisible(boolean visible) {
        mShutterButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void showPreviewUI() {
        mCaptureLayout.setVisibility(View.VISIBLE);
        mActivity.getCameraAppUI().setBottomBarVisible(true);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    public void onStopCapture() {
        mCaptureIndicator.setVisibility(View.INVISIBLE);
        mActivity.getCameraAppUI().setBottomBarVisible(false);
        mShutterButton.setImageResource(R.drawable.ic_capture_pano);
        mShutterButton.setVisibility(View.GONE);
        hideTooFastIndication();
        hideDirectionIndicators();
    }

    public void setCaptureProgressOnDirectionChangeListener(
            PanoProgressBar.OnDirectionChangeListener listener) {
        mCaptureProgressBar.setOnDirectionChangeListener(listener);
    }

    public void resetCaptureProgress() {
        mCaptureProgressBar.reset();
    }

    public void setMaxCaptureProgress(int max) {
        mCaptureProgressBar.setMaxProgress(max);
    }

    public void showCaptureProgress() {
        mCaptureProgressBar.setVisibility(View.VISIBLE);
    }

    public void updateCaptureProgress(
            float panningRateXInDegree, float panningRateYInDegree,
            float progressHorizontalAngle, float progressVerticalAngle,
            float maxPanningSpeed) {

        if ((Math.abs(panningRateXInDegree) > maxPanningSpeed)
                || (Math.abs(panningRateYInDegree) > maxPanningSpeed)) {
            showTooFastIndication();
        } else {
            hideTooFastIndication();
        }

        // progressHorizontalAngle and progressVerticalAngle are relative to the
        // camera. Convert them to UI direction.
        mProgressAngle[0] = progressHorizontalAngle;
        mProgressAngle[1] = progressVerticalAngle;
        mProgressDirectionMatrix.mapPoints(mProgressAngle);

        int angleInMajorDirection =
                (Math.abs(mProgressAngle[0]) > Math.abs(mProgressAngle[1]))
                        ? (int) mProgressAngle[0]
                        : (int) mProgressAngle[1];
        mCaptureProgressBar.setProgress((angleInMajorDirection));
    }

    public void setProgressOrientation(int orientation) {
        mProgressDirectionMatrix.reset();
        mProgressDirectionMatrix.postRotate(orientation);
    }

    public void showDirectionIndicators(int direction) {
        switch (direction) {
            case PanoProgressBar.DIRECTION_NONE:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
            case PanoProgressBar.DIRECTION_LEFT:
                mLeftIndicator.setVisibility(View.VISIBLE);
                mRightIndicator.setVisibility(View.INVISIBLE);
                break;
            case PanoProgressBar.DIRECTION_RIGHT:
                mLeftIndicator.setVisibility(View.INVISIBLE);
                mRightIndicator.setVisibility(View.VISIBLE);
                break;
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
        Log.d(TAG, " onSurfaceTextureAvailable");
        mSurfaceTexture = surfaceTexture;
        mController.onPreviewUIReady();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        mController.onPreviewUIDestroyed();
        mSurfaceTexture = null;
        Log.d(TAG, "surfaceTexture is destroyed");
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        // mActivity.getCameraAppUI().onSurfaceTextureUpdated(surfaceTexture);
    }

    private void hideDirectionIndicators() {
        mLeftIndicator.setVisibility(View.INVISIBLE);
        mRightIndicator.setVisibility(View.INVISIBLE);
    }

    public Point getPreviewAreaSize() {
        return new Point(
                mTextureView.getWidth(), mTextureView.getHeight());
    }

    public void reset() {
        mReviewLayout.setVisibility(View.GONE);
        mCaptureProgressBar.setVisibility(View.INVISIBLE);
        hideDirectionIndicators();//SPRD: Fix bug 539829
    }

    public void showFinalMosaic(Bitmap bitmap, int orientation) {
        if (bitmap != null && orientation != 0) {
            Matrix rotateMatrix = new Matrix();
            rotateMatrix.setRotate(orientation);
            bitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                    rotateMatrix, false);
        }

        mReview.setImageBitmap(bitmap);
        mCaptureLayout.setVisibility(View.GONE);
        mReviewLayout.setVisibility(View.VISIBLE);
        /* SPRD: fix bug337352 SavingProgressBar and cancel button didn't show @{ */
        mSavingProgressBar.setVisibility(View.VISIBLE);
        /* @} */
    }

    public void onConfigurationChanged(boolean threadRunning) {
        Drawable lowResReview = null;
        if (threadRunning)
            lowResReview = mReview.getDrawable();

        // Change layout in response to configuration change
        LayoutInflater inflater = (LayoutInflater)
                mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mReviewControl.removeAllViews();
        inflater.inflate(R.layout.pano_review_control, mReviewControl, true);

        // mRootView.bringChildToFront(mCameraControls);
        setViews(mActivity.getResources());
        if (threadRunning) {
            mReview.setImageDrawable(lowResReview);
            mCaptureLayout.setVisibility(View.GONE);
            mReviewLayout.setVisibility(View.VISIBLE);
            mSavingProgressBar.setVisibility(View.VISIBLE);// SPRD: BUG 348809
        }
    }

    public void resetSavingProgress() {
        mSavingProgressBar.reset();
        mSavingProgressBar.setRightIncreasing(true);
    }

    public void updateSavingProgress(int progress) {
        mSavingProgressBar.setProgress(progress);
    }

    @Override
    public void onLayoutChange(
            View v, int l, int t, int r, int b,
            int oldl, int oldt, int oldr, int oldb) {
        mController.onPreviewUILayoutChange(l, t, r, b);
    }

    public void showAlertDialog(
            String title, String failedString,
            String OKString, Runnable runnable) {
        mDialogHelper.showAlertDialog(title, failedString, OKString, runnable);
    }

    public void showWaitingDialog(String title) {
        mDialogHelper.showWaitingDialog(title);
    }

    public void dismissAllDialogs() {
        mDialogHelper.dismissAll();
    }

    private void createContentView() {
        LayoutInflater inflator = (LayoutInflater) mActivity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflator.inflate(R.layout.panorama_module, mRootView, true);

        Resources appRes = mActivity.getResources();
        mIndicatorColor = appRes.getColor(R.color.pano_progress_indication);
        mReviewBackground = appRes.getColor(R.color.review_background);
        mIndicatorColorFast = appRes.getColor(R.color.pano_progress_indication_fast);

        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        mPreviewLayout = mRootView.findViewById(R.id.pano_preview_layout);
        mReviewControl = (ViewGroup) mRootView.findViewById(R.id.pano_review_control);
        mReviewLayout = mRootView.findViewById(R.id.pano_review_layout);
        mReview = (ImageView) mRootView.findViewById(R.id.pano_reviewarea);
        mCaptureLayout = (FrameLayout) mRootView.findViewById(R.id.panorama_capture_layout);
        mCaptureProgressBar = (PanoProgressBar) mRootView.findViewById(R.id.pano_pan_progress_bar);
        mCaptureProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mCaptureProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_done));
        mCaptureProgressBar.setIndicatorColor(mIndicatorColor);
        mCaptureProgressBar.setIndicatorWidth(20);

        mPreviewBorder = mCaptureLayout.findViewById(R.id.pano_preview_area_border);

        mLeftIndicator = mRootView.findViewById(R.id.pano_pan_left_indicator);
        mRightIndicator = mRootView.findViewById(R.id.pano_pan_right_indicator);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
        mTooFastPrompt = (TextView) mRootView.findViewById(R.id.pano_capture_too_fast_textview);
        mCaptureIndicator = mRootView.findViewById(R.id.pano_capture_indicator);

        mShutterButton = (ShutterButton) mActivity.getModuleLayoutRoot().findViewById(
                R.id.shutter_button);
        // mShutterButton.setImageResource(R.drawable.btn_new_shutter);
        /* mShutterButton.setOnShutterButtonListener(mController); */
        mReview.setBackgroundColor(mReviewBackground);

        // TODO: set display change listener properly.
        mTextureView = (TextureView) mRootView.findViewById(R.id.pano_preview_textureview);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.addOnLayoutChangeListener(this);

        mDialogHelper = new DialogHelper();
        setViews(appRes);

        // SPRD:Add for bug 479120 wideangle module rotate issue
        mProgressLayout = (FrameLayout) mRootView.findViewById(R.id.pano_progress_layout);

        mPanoBg = mRootView.findViewById(R.id.pano_prog_bg);
    }

    /* SPRD:Add for bug 479120 wideangle module rotate issue @{ */
    public FrameLayout getProgressLayout() {
        return mProgressLayout;
    }

    /* @} */

    private void setViews(Resources appRes) {
        int weight = appRes.getInteger(R.integer.SRI_pano_layout_weight);

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mPreviewLayout.getLayoutParams();
        lp.weight = weight;
        mPreviewLayout.setLayoutParams(lp);

        lp = (LinearLayout.LayoutParams) mReview.getLayoutParams();
        lp.weight = weight;
        mPreviewLayout.setLayoutParams(lp);

        mSavingProgressBar = (PanoProgressBar) mRootView
                .findViewById(R.id.pano_saving_progress_bar);
        mSavingProgressBar.setIndicatorWidth(0);
        mSavingProgressBar.setMaxProgress(100);
        mSavingProgressBar.setBackgroundColor(appRes.getColor(R.color.pano_progress_empty));
        mSavingProgressBar.setDoneColor(appRes.getColor(R.color.pano_progress_indication));
        /* SPRD: fix bug337352 SavingProgressBar and cancel button didn't show @{ */
        mSavingProgressBar.setVisibility(View.GONE);
        /* @} */

        View cancelButton = mRootView.findViewById(R.id.pano_review_cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mShutterButton.setVisibility(View.VISIBLE);
                mController.cancelHighResStitching();
            }
        });
    }

    private void showTooFastIndication() {
        mTooFastPrompt.setVisibility(View.VISIBLE);
        // The PreviewArea also contains the border for "too fast" indication.
        mPreviewBorder.setVisibility(View.VISIBLE);
        mCaptureProgressBar.setIndicatorColor(mIndicatorColorFast);
        mLeftIndicator.setEnabled(true);
        mRightIndicator.setEnabled(true);
    }

    private void hideTooFastIndication() {
        mTooFastPrompt.setVisibility(View.GONE);
        mPreviewBorder.setVisibility(View.INVISIBLE);
        mCaptureProgressBar.setIndicatorColor(mIndicatorColor);
        mLeftIndicator.setEnabled(false);
        mRightIndicator.setEnabled(false);
    }

    public void flipPreviewIfNeeded() {
        // Rotation needed to display image correctly clockwise
        int cameraOrientation = mController.getCameraOrientation();
        // Display rotated counter-clockwise
        int displayRotation = CameraUtil.getDisplayRotation();
        // Rotation needed to display image correctly on current display
        int rotation = (cameraOrientation - displayRotation + 360) % 360;
        if (rotation >= 180) {
            mTextureView.setRotation(180);
        } else {
            mTextureView.setRotation(0);
        }
    }

    public void showPreviewCover() {
        Log.d(TAG, "show mPreviewCover");
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        if (mPreviewCover.getVisibility() != View.GONE) {
            Log.d(TAG, "hide mPreviewCover");
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    private class DialogHelper {
        private ProgressDialog mProgressDialog;
        private AlertDialog mAlertDialog;

        DialogHelper() {
            mProgressDialog = null;
            mAlertDialog = null;
        }

        public void dismissAll() {
            if (mAlertDialog != null) {
                mAlertDialog.dismiss();
                mAlertDialog = null;
            }
            if (mProgressDialog != null) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        }

        public void showAlertDialog(
                CharSequence title, CharSequence message,
                CharSequence buttonMessage, final Runnable buttonRunnable) {
            dismissAll();
            mAlertDialog = (new AlertDialog.Builder(mActivity))
                    .setTitle(title)
                    .setMessage(message)
                    .setNeutralButton(buttonMessage, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            buttonRunnable.run();
                        }
                    })
                    .show();
        }

        public void showWaitingDialog(CharSequence message) {
            dismissAll();
            mProgressDialog = ProgressDialog.show(mActivity, null, message, true, false);
        }
    }

    private static class FlipBitmapDrawable extends BitmapDrawable {

        public FlipBitmapDrawable(Resources res, Bitmap bitmap) {
            super(res, bitmap);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int cx = bounds.centerX();
            int cy = bounds.centerY();
            canvas.save(Canvas.MATRIX_SAVE_FLAG);
            canvas.rotate(180, cx, cy);
            super.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public GestureDetector.OnGestureListener getGestureListener() {
        return null;
    }

    @Override
    public View.OnTouchListener getTouchListener() {
        return null;
    }

    @Override
    public void onPreviewLayoutChanged(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {

    }

    @Override
    public boolean shouldAutoAdjustTransformMatrixOnLayout() {
        return true;
    }

    @Override
    public void onPreviewFlipped() {
        // mController.updateCameraOrientation();
    }

    private void initFilterMakeupButton() {
        // mActivity.getModuleLayoutRoot().findViewById(R.id.btn_filter_button).setVisibility(View.GONE);
        mActivity.getModuleLayoutRoot().findViewById(R.id.btn_beauty_button)
                .setVisibility(View.GONE);
    }

    // SPRD Bug WideAngle Rotate
    private View mPanoBg;

    public void adjustUI(int orientation) {
        if (orientation == 0 || orientation == 180) {
            if (mProgressLayout != null && mPanoBg != null && mCaptureProgressBar != null)
                mProgressLayout.setPadding(0, 0, 0,
                        mPanoBg.getHeight() - mCaptureProgressBar.getHeight());
        } else if (orientation == 90 || orientation == 270) {
            if (mProgressLayout != null)
                mProgressLayout.setPadding(0, 0, 0, 20);
        }
    }
}
