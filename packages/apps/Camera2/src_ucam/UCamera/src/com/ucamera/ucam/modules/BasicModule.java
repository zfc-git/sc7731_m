/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.ucamera.ucam.modules;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.graphics.ImageFormat;

import android.hardware.Camera.Parameters;
import android.location.Location;
import android.media.AudioManager;
import android.media.CameraProfile;
import android.media.MediaActionSound;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import android.content.pm.ActivityInfo;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ucamera.ucam.modules.BasicModule.NamedImages.NamedEntity;
import com.android.camera.FocusOverlayManager;
import com.android.camera.PhotoController;
import com.android.camera.ButtonManager;
import com.android.camera.MediaSaverImpl;
import com.android.camera.CameraActivity;
import com.android.camera.CameraModule;
import com.android.camera.Storage;
import com.android.camera.Exif;
import com.android.camera.app.AppController;
import com.android.camera.app.CameraAppUI;
import com.android.camera.app.CameraProvider;
import com.android.camera.SoundPlayer;
import com.android.camera.app.MediaSaver;
import com.android.camera.app.MemoryManager;
import com.android.camera.app.MemoryManager.MemoryListener;
import com.android.camera.app.MotionManager;
import com.android.camera.debug.Log;
import com.android.camera.exif.ExifInterface;
import com.android.camera.exif.ExifTag;
import com.android.camera.exif.Rational;
import com.android.camera.hardware.HardwareSpec;
import com.android.camera.hardware.HardwareSpecImpl;
import com.android.camera.hardware.HeadingSensor;
import com.android.camera.module.ModuleController;
import com.android.camera.one.OneCamera;
import com.android.camera.one.OneCameraAccessException;
import com.android.camera.one.OneCameraException;
import com.android.camera.one.OneCameraManager;
import com.android.camera.one.OneCameraModule;
import com.android.camera.remote.RemoteCameraModule;
import com.android.camera.settings.CameraPictureSizesCacher;
import com.android.camera.settings.Keys;
import com.android.camera.settings.ResolutionUtil;
import com.android.camera.settings.SettingsManager;
import com.android.camera.stats.SessionStatsCollector;
import com.android.camera.stats.UsageStatistics;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.TouchCoordinate;
import com.android.camera.util.AndroidServices;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.GcamHelper;
import com.android.camera.util.GservicesHelper;
import com.android.camera.util.Size;
import com.android.camera2.R;
import com.android.ex.camera2.portability.CameraAgent;
import com.android.ex.camera2.portability.CameraAgent.CameraAFCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraAFMoveCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraPictureCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;
import com.android.ex.camera2.portability.CameraAgent.CameraShutterCallback;
import com.android.ex.camera2.portability.CameraCapabilities;
import com.android.ex.camera2.portability.CameraDeviceInfo.Characteristics;
import com.android.ex.camera2.portability.CameraSettings;
import com.google.common.logging.eventprotos;
//import com.sprd.camera.storagepath.StorageUtil;
import com.sprd.camera.freeze.FreezeFrameDisplayControl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
//import com.sprd.camera.storagepath.MultiStorage;
import com.ucamera.ucam.modules.compatible.PreviewSize;
import com.ucamera.ucam.modules.compatible.ResolutionSize;

import com.ucamera.ucam.modules.ui.BasicUI;
import com.ucamera.ucam.modules.utils.LogUtils;
import com.ucamera.ucam.sound.ShutterSound;
import android.view.OrientationEventListener;

public abstract class BasicModule
        extends CameraModule
        implements PhotoController,
        ModuleController,
        MemoryListener,
        FocusOverlayManager.Listener,
        SettingsManager.OnSettingChangedListener,
        RemoteCameraModule,
        CountDownView.OnCountDownStatusListener
        ,MediaSaverImpl.Listener, /* SPRD:Add for bug 461734 add freeze for scenerymodule */
        FreezeFrameDisplayControl.Listener{

    public static final String BASIC_MODULE_STRING_ID = "BasicModule";

    private static final Log.Tag TAG = new Log.Tag(BASIC_MODULE_STRING_ID);

    // We number the request code from 1000 to avoid collision with Gallery.
    private static final int REQUEST_CROP = 1000;

    // Messages defined for the UI thread handler.
    private static final int MSG_FIRST_TIME_INIT = 1;
    private static final int MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE = 2;

    // The subset of parameters we need to update in setCameraParameters().
    protected static final int UPDATE_PARAM_INITIALIZE = 1;
    protected static final int UPDATE_PARAM_ZOOM = 2;
    protected static final int UPDATE_PARAM_PREFERENCE = 4;
    protected static final int UPDATE_PARAM_ALL = -1;

    // This is the delay before we execute onResume tasks when coming
    // from the lock screen, to allow time for onPause to execute.
    private static final int ON_RESUME_TASKS_DELAY_MSEC = 500;

    public static final String DEBUG_IMAGE_PREFIX = "DEBUG_";

    protected CameraActivity mActivity;
    protected CameraProxy mCameraDevice;
    protected int mCameraId;
    protected CameraCapabilities mCameraCapabilities;
    protected CameraSettings mCameraSettings;
    private HardwareSpec mHardwareSpec;
    protected boolean mPaused;
    protected Parameters mParameters;
    protected ShutterSound mShutterSound;
    protected int mBitPerPixels = 0;
    protected int mPreviewFormat = ImageFormat.UNKNOWN;


    // The activity is going to switch to the specified camera id. This is
    // needed because texture copy is done in GL thread. -1 means camera is not
    // switching.
    protected int mPendingSwitchCameraId = -1;

    // When setCameraParametersWhenIdle() is called, we accumulate the subsets
    // needed to be updated in mUpdateSet.
    protected int mUpdateSet;

    protected float mZoomValue; // The current zoom ratio.
    protected int mTimerDuration;
    /** Set when a volume button is clicked to take photo */
    protected boolean mVolumeButtonClickedFlag = false;

    protected boolean mFocusAreaSupported;
    protected boolean mMeteringAreaSupported;
    protected boolean mAeLockSupported;
    protected boolean mAwbLockSupported;
    protected boolean mContinuousFocusSupported;

    protected static final String sTempCropFilename = "crop-temp";

    protected boolean mFaceDetectionStarted = false;

    // mCropValue and mSaveUri are used only if isImageCaptureIntent() is true.
    protected String mCropValue;
    protected Uri mSaveUri;

    protected Uri mDebugUri;

    // We use a queue to generated names of the images to be used later
    // when the image is ready to be saved.
    protected NamedImages mNamedImages;

    protected final Runnable mDoSnapRunnable = new Runnable() {
        @Override
        public void run() {
            onShutterButtonClick();
        }
    };

    /**
     * An unpublished intent flag requesting to return as soon as capturing is
     * completed. TODO: consider publishing by moving into MediaStore.
     */
    protected static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";

    // The display rotation in degrees. This is only valid when mCameraState is
    // not PREVIEW_STOPPED.
    protected int mDisplayRotation;
    // The value for UI components like indicators.
    protected int mDisplayOrientation;
    // The value for cameradevice.CameraSettings.setPhotoRotationDegrees.
    protected int mJpegRotation;
    // Indicates whether we are using front camera
    protected boolean mMirror;
    protected boolean mFirstTimeInitialized;
    protected boolean mIsImageCaptureIntent;

    protected int mCameraState = PREVIEW_STOPPED;
    protected boolean mSnapshotOnIdle = false;

    protected ContentResolver mContentResolver;

    public AppController mAppController;
    // CID 123773 : UrF: Unread field (FB.URF_UNREAD_FIELD)
    // private OneCameraManager mOneCameraManager;

    private final PostViewPictureCallback mPostViewPictureCallback =
            new PostViewPictureCallback();
    private final RawPictureCallback mRawPictureCallback =
            new RawPictureCallback();
    private final AutoFocusCallback mAutoFocusCallback =
            new AutoFocusCallback();
    private final Object mAutoFocusMoveCallback =
            ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK
                    ? new AutoFocusMoveCallback()
                    : null;

    private long mFocusStartTime;
    private long mShutterCallbackTime;
    private long mPostViewPictureCallbackTime;
    private long mRawPictureCallbackTime;
    private long mJpegPictureCallbackTime;
    private long mOnResumeTime;
    private byte[] mJpegImageData;
    /** Touch coordinate for shutter button press. */
    private TouchCoordinate mShutterTouchCoordinate;
    protected BasicUI mUI;;


    // These latency time are for the CameraLatency test.
    public long mAutoFocusTime;
    public long mShutterLag;
    public long mShutterToPictureDisplayedTime;
    public long mPictureDisplayedToJpegCallbackTime;
    public long mJpegCallbackFinishTime;
    public long mCaptureStartTime;

    // This handles everything about focus.
    protected FocusOverlayManager mFocusManager;

    protected final int mGcamModeIndex;
    protected SoundPlayer mCountdownSoundPlayer;

    protected CameraCapabilities.SceneMode mSceneMode;
    protected CameraCapabilities.ColorEffect mColorEffect;
    //CID 124284:unuse field
    //protected CameraCapabilities.Antibanding mAntibanding;
//    protected CameraCapabilities.BrightNess mBrightness;
    protected CameraCapabilities.BurstNumber mButstNumber;

    protected final Handler mHandler = new MainHandler(this);

    protected boolean mQuickCapture;

    /** Used to detect motion. We use this to release focus lock early. */
    private MotionManager mMotionManager;

    protected int mContinueTakePictureCount;
    protected int mContinuousCaptureCount ;
    private boolean mShutterSoundEnabled = true;

    protected HeadingSensor mHeadingSensor;

    /** True if all the parameters needed to start preview is ready. */
    protected boolean mCameraPreviewParamsReady = false;
    protected int mCameraDisplayOrientation;
    public static String mCurrentModule  = BASIC_MODULE_STRING_ID;

    protected final MediaSaver.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaver.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                        /* SPRD: Add for FreezeViewDisplay if MediaSave Finish */
                        if ((sFreezeFrameControl != null
                                && sFreezeFrameControl.isFreezeFrame()) || mIsImageCaptureIntent) {
                            if (uri != null) {
                                sFreezeFrameControl.proxyRunLoadProxy(uri);
                            } else {
                                sFreezeFrameControl.proxyDoneClicked();
                            }
                        } else if (uri != null) {
                            mActivity.notifyNewMedia(uri);
                        }else{
                            onError();
                        }
                }
            };

    /**
     * Displays error dialog and allows use to enter feedback. Does not shut
     * down the app.
     */
    private void onError() {
        mAppController.getFatalErrorHandler().onMediaStorageFailure();
    }

    private boolean mShouldResizeTo16x9 = false;

    private final Runnable mResumeTaskRunnable = new Runnable() {
        @Override
        public void run() {
            onResumeTasks();
        }
    };

    /**
     * We keep the flash setting before entering scene modes (HDR)
     * and restore it after HDR is off.
     */
    private String mFlashModeBeforeSceneMode;
    // CID 123778 : UuF: Unused field (FB.UUF_UNUSED_FIELD)
    // private String mZSLBeforeSceneMode;

    private void checkDisplayRotation() {
        if (mPaused) {// SPRD:Fix bug404554
            return;
        }
        // Set the display orientation if display rotation has changed.
        // Sometimes this happens when the device is held upside
        // down and camera app is opened. Rotation animation will
        // take some time and the rotation value we have got may be
        // wrong. Framework does not have a callback for this now.
        if (CameraUtil.getDisplayRotation() != mDisplayRotation) {
            setDisplayOrientation();
        }
        if (SystemClock.uptimeMillis() - mOnResumeTime < 5000) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkDisplayRotation();
                }
            }, 100);
        }
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private static class MainHandler extends Handler {
        private final WeakReference<BasicModule> mModule;

        public MainHandler(BasicModule module) {
            super(Looper.getMainLooper());
            mModule = new WeakReference<BasicModule>(module);
        }

        @Override
        public void handleMessage(Message msg) {
            BasicModule module = mModule.get();
            if (module == null) {
                return;
            }
            switch (msg.what) {
                case MSG_FIRST_TIME_INIT: {
                    module.initializeFirstTime();
                    break;
                }

                case MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE: {
                    module.setCameraParametersWhenIdle(0);
                    break;
                }
            }
        }
    }

    private void switchToGcamCapture() {
        if (mActivity != null && mGcamModeIndex != 0) {
            SettingsManager settingsManager = mActivity.getSettingsManager();
            settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                                Keys.KEY_CAMERA_HDR_PLUS, true);

            // Disable the HDR+ button to prevent callbacks from being
            // queued before the correct callback is attached to the button
            // in the new module.  The new module will set the enabled/disabled
            // of this button when the module's preferred camera becomes available.
            ButtonManager buttonManager = mActivity.getButtonManager();

            buttonManager.disableButtonClick(ButtonManager.BUTTON_HDR_PLUS);

            mAppController.getCameraAppUI().freezeScreenUntilPreviewReady();

            // Do not post this to avoid this module switch getting interleaved with
            // other button callbacks.
            mActivity.onModeSelected(mGcamModeIndex);

            buttonManager.enableButtonClick(ButtonManager.BUTTON_HDR_PLUS);
        }
    }

    /**
     * Constructs a new photo module.
     */
    public BasicModule(AppController app) {
        super(app);
        mGcamModeIndex = app.getAndroidContext().getResources()
                .getInteger(R.integer.camera_mode_gcam);
    }

    @Override
    public String getPeekAccessibilityString() {
        return mAppController.getAndroidContext()
            .getResources().getString(R.string.photo_accessibility_peek);
    }

//    @Override
//    public String getModuleStringIdentifier() {
//        return BASIC_MODULE_STRING_ID;
//    }

    @Override
    public void init(CameraActivity activity, boolean isSecureCamera, boolean isCaptureIntent) {
        Log.i(TAG, "init Camera");
        mActivity = activity;
        // TODO: Need to look at the controller interface to see if we can get
        // rid of passing in the activity directly.
        mAppController = mActivity;

        // SPRD initialize mJpegQualityController.
        mJpegQualityController = new JpegQualityController();

        makeModuleUI(this,mActivity.getModuleLayoutRoot());

        mShutterSound = new ShutterSound(mActivity);
        mShutterSound.loadSounds(mActivity);
//        mActivity.setPreviewStatusListener(mUI);

        SettingsManager settingsManager = mActivity.getSettingsManager();
        initCameraID();
        // TODO: Move this to SettingsManager as a part of upgrade procedure.
        // Aspect Ratio selection dialog is only shown for Nexus 4, 5 and 6.
        if (mAppController.getCameraAppUI().shouldShowAspectRatioDialog()) {
            // Switch to back camera to set aspect ratio.
            settingsManager.setToDefault(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID);
        }
        mCameraId = settingsManager.getInteger(mAppController.getModuleScope(),
                                               Keys.KEY_CAMERA_ID);

        mContentResolver = mActivity.getContentResolver();

        // Surface texture is from camera screen nail and startPreview needs it.
        // This must be done before startPreview.
        mIsImageCaptureIntent = isImageCaptureIntent();
        mUI.setCountdownFinishedListener(this);

        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        mHeadingSensor = new HeadingSensor(AndroidServices.instance().provideSensorManager());

        /**
         * CID 123773 : UrF: Unread field (FB.URF_UNREAD_FIELD)
        try {
            mOneCameraManager = OneCameraModule.provideOneCameraManager();
        } catch (OneCameraException e) {
            Log.e(TAG, "Hardware manager failed to open.");
        }
         */
        // TODO: Make this a part of app controller API.
        View cancelButton = mActivity.findViewById(R.id.shutter_cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelCountDown();
            }
        });
        /* SPRD:Add for bug 461734 add freeze for scenerymodule @{ */
        sFreezeFrameControl = new FreezeFrameDisplayControl(mActivity,mUI,mIsImageCaptureIntent);
        sFreezeFrameControl.setListener(BasicModule.this);
        /* @} */
    }

    private void cancelCountDown() {
        if (mUI.isCountingDown()) {
            // Cancel on-going countdown.
            mUI.cancelCountDown();
        }
        if (!mActivity.getCameraAppUI().isInIntentReview() /*&& !mActivity.getCameraAppUI().isInFreezeReview()*/) {//SPRD:Fix bug 398341
            mAppController.getCameraAppUI().transitionToCapture();
            mAppController.getCameraAppUI().setSwipeEnabled(true);
            mAppController.getCameraAppUI().showModeOptions();
            mAppController.setShutterEnabled(true);
        }
    }

    public abstract void makeModuleUI(PhotoController controller,View parent);
    @Override
    public boolean isUsingBottomBar() {
        return true;
    }

    private void initializeControlByIntent() {
        if (mIsImageCaptureIntent) {
            if (!mActivity.getCameraAppUI().isInIntentReview()) {
                mActivity.getCameraAppUI().transitionToIntentCaptureLayout();
            } else {
                mActivity.getCameraAppUI().transitionToIntentReviewLayout();
            }
           // mUI.hideIntentReviewImageView();
            setupCaptureParams();
        }
    }

    private void onPreviewStarted() {
        Log.i(TAG, "onPreviewStarted mContinuousCaptureCount="+mContinuousCaptureCount);
        mAppController.onPreviewStarted();
        Log.i(TAG, "onPreviewStarted mCameraState="+mCameraState);
        if (mContinuousCaptureCount <= 0||!isBurstCapture()|| (mCameraState == PREVIEW_STOPPED)) {//SPRD:Fix bug 388289
            /*mAppController.getCameraAppUI().setSwipeEnabled(true);
            mAppController.getCameraAppUI().showModeOptions();
            mAppController.setShutterEnabled(true);*/
            if(mIsImageCaptureIntent){//SPRD:Fix bug 391829
//                mAppController.getCameraAppUI().showModeOptions();
                if (mUI.isReviewShow()) {// SPRD BUG: 402084
                    mAppController.getCameraAppUI().hideModeOptions();
                }else {
                    mAppController.getCameraAppUI().showModeOptions();
                }
            }
            setCameraState(IDLE);
        }
        // startFaceDetection();
        updateFace();
        onPreviewStartedAfter();
    }

    public void onPreviewStartedAfter() {
        // ignore
    }
    @Override
    public void onPreviewUIReady() {
        Log.i(TAG, "onPreviewUIReady");
        startPreview();
    }

    @Override
    public void onPreviewUIDestroyed() {
        Log.i(TAG, "onPreviewUIDestroyed,CameraDevice = " + mCameraDevice);
        if (mCameraDevice == null) {
            return;
        }
        stopPreview();
        mCameraDevice.setPreviewTexture(null);
    }

    @Override
    public void startPreCaptureAnimation() {
        mAppController.startFlashAnimation(false);
    }

    private void onCameraOpened() {
        openCameraCommon();
        initializeControlByIntent();
        setPreviewFrameLayoutAspectRatio();
        setModeOptionsLayout();
    }

    protected void switchCamera() {
        Log.i(TAG, "switchCamera start");
        if (mPaused) {
            return;
        }
        cancelCountDown();
        if (isBurstCapture()){//SPRD:Fix bug 397568.
            onHideBurstScreenHint();
        }
        mAppController.freezeScreenUntilPreviewReady();
        freezeScreen();
        SettingsManager settingsManager = mActivity.getSettingsManager();

        closeCamera();
        mCameraId = mPendingSwitchCameraId;
        Log.i(TAG, "Start to switch camera. id=" + mPendingSwitchCameraId+" mCameraId="+mCameraId);
        settingsManager.set(mAppController.getModuleScope(), Keys.KEY_CAMERA_ID, mCameraId);
        setPhotoCameraID();
        requestCameraOpen();
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        }

        mMirror = isCameraFrontFacing();
        if (mFocusManager != null) { //SPRD:Fix bug 462621
            mFocusManager.setMirror(mMirror);
        }
        mAppController.setShutterEnabled(true);//SPRD:Fix bug 389213
        // Start switch camera animation. Post a message because
        // onFrameAvailable from the old camera may already exist.
        Log.i(TAG, "switchCamera end");
    }

    /**
     * Uses the {@link CameraProvider} to open the currently-selected camera
     * device, using {@link GservicesHelper} to choose between API-1 and API-2.
     */
    protected void requestCameraOpen() {
        Log.i(TAG, "requestCameraOpen mCameraId:" + mCameraId);
        mActivity.getCameraProvider().requestCamera(mCameraId,
                GservicesHelper.useCamera2ApiThroughPortabilityLayer(mActivity
                        .getContentResolver()));
//        SettingsManager mSettingsManager = mActivity.getSettingsManager();
//        mSettingsManager.setValueByIndex(mAppController.getModuleScope(),
//                Keys.KEY_CAMERA_ID, mCameraId);//SPRD BUG:379926
    }

    protected final ButtonManager.ButtonCallback mCameraCallback =
            new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    // At the time this callback is fired, the camera id
                    // has be set to the desired camera.

                    if (mPaused || mAppController.getCameraProvider().waitingForCamera()) {
                        return;
                    }
                    // If switching to back camera, and HDR+ is still on,
                    // switch back to gcam, otherwise handle callback normally.
                    SettingsManager settingsManager = mActivity.getSettingsManager();
                    if (Keys.isCameraBackFacing(settingsManager,
                                                mAppController.getModuleScope())) {
                        if (Keys.requestsReturnToHdrPlus(settingsManager,
                                                         mAppController.getModuleScope())) {
                            switchToGcamCapture();
                            return;
                        }
                    }

                    ButtonManager buttonManager = mActivity.getButtonManager();
                    buttonManager.disableCameraButtonAndBlock();

                    mPendingSwitchCameraId = state;

                    Log.d(TAG, "Start to switch camera. cameraId=" + state);
                    // We need to keep a preview frame for the animation before
                    // releasing the camera. This will trigger
                    // onPreviewTextureCopied.
                    // TODO: Need to animate the camera switch
                    switchCamera();
                }
            };

    private final ButtonManager.ButtonCallback mHdrPlusCallback =
            new ButtonManager.ButtonCallback() {
                @Override
                public void onStateChanged(int state) {
                    SettingsManager settingsManager = mActivity.getSettingsManager();
                    if (GcamHelper.hasGcamAsSeparateModule(
                            mAppController.getCameraFeatureConfig())) {
                        // Set the camera setting to default backfacing.
                        settingsManager.setToDefault(mAppController.getModuleScope(),
                                                     Keys.KEY_CAMERA_ID);
                        switchToGcamCapture();
                    } else {
                        if (Keys.isHdrOn(settingsManager)) {
                            settingsManager.set(mAppController.getCameraScope(), Keys.KEY_SCENE_MODE,
                                    mCameraCapabilities.getStringifier().stringify(
                                            CameraCapabilities.SceneMode.HDR));
                        } else {
                            settingsManager.set(mAppController.getCameraScope(), Keys.KEY_SCENE_MODE,
                                    mCameraCapabilities.getStringifier().stringify(
                                            CameraCapabilities.SceneMode.AUTO));
                        }
                        updateParametersSceneMode();
                        if (mCameraDevice != null) {
                            mCameraDevice.applySettings(mCameraSettings);
                        }
                        updateSceneMode();
                    }
                }
            };

    private final View.OnClickListener mCancelCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onCaptureCancelled();
        }
    };

    private final View.OnClickListener mDoneCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onCaptureDone();
        }
    };

    private final View.OnClickListener mRetakeCallback = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mActivity.getCameraAppUI().transitionToIntentCaptureLayout();
            onCaptureRetake();
        }
    };

    @Override
    public void hardResetSettings(SettingsManager settingsManager) {
        // PhotoModule should hard reset HDR+ to off,
        // and HDR to off if HDR+ is supported.
        settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR_PLUS, false);
        if (GcamHelper.hasGcamAsSeparateModule(mAppController.getCameraFeatureConfig())) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR, false);
        }
    }

    @Override
    public HardwareSpec getHardwareSpec() {
        if (mHardwareSpec == null) {
            mHardwareSpec = (mCameraSettings != null ?
                    new HardwareSpecImpl(getCameraProvider(), mCameraCapabilities,
                            mAppController.getCameraFeatureConfig(), isCameraFrontFacing()) : null);
        }
        return mHardwareSpec;
    }

    @Override
    public CameraAppUI.BottomBarUISpec getBottomBarSpec() {
        CameraAppUI.BottomBarUISpec bottomBarSpec = new CameraAppUI.BottomBarUISpec();

        bottomBarSpec.enableCamera = true;
        bottomBarSpec.cameraCallback = mCameraCallback;
        bottomBarSpec.enableFlash = !mAppController.getSettingsManager()
            .getBoolean(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR);
        bottomBarSpec.enableHdr = true;
        bottomBarSpec.hdrCallback = mHdrPlusCallback;
        bottomBarSpec.enableGridLines = true;
//        bottomBarSpec.hideVGesture = true;
        if (mCameraCapabilities != null) {
            bottomBarSpec.enableExposureCompensation = true;
            bottomBarSpec.exposureCompensationSetCallback =
                new CameraAppUI.BottomBarUISpec.ExposureCompensationSetCallback() {
                @Override
                public void setExposure(int value) {
                    setExposureCompensation(value);
                }
            };
            bottomBarSpec.minExposureCompensation =
                mCameraCapabilities.getMinExposureCompensation();
            bottomBarSpec.maxExposureCompensation =
                mCameraCapabilities.getMaxExposureCompensation();
            bottomBarSpec.exposureCompensationStep =
                mCameraCapabilities.getExposureCompensationStep();
            Log.i(TAG, "exposureCompensationStep:" + bottomBarSpec.exposureCompensationStep);
        }

        bottomBarSpec.enableSelfTimer = true;
        bottomBarSpec.showSelfTimer = true;

        if (isImageCaptureIntent()) {
            bottomBarSpec.showCancel = true;
            bottomBarSpec.cancelCallback = mCancelCallback;
            bottomBarSpec.showDone = true;
            bottomBarSpec.doneCallback = mDoneCallback;
            bottomBarSpec.showRetake = true;
            bottomBarSpec.retakeCallback = mRetakeCallback;
        }

        return bottomBarSpec;
    }

    // either open a new camera or switch cameras
    private void openCameraCommon() {
        mUI.onCameraOpened(mCameraCapabilities, mCameraSettings);
        if (mIsImageCaptureIntent) {
            // Set hdr plus to default: off.
            SettingsManager settingsManager = mActivity.getSettingsManager();
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,
                                         Keys.KEY_CAMERA_HDR_PLUS);
        }
        // CID 123737 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // SettingsManager settingsManager = mActivity.getSettingsManager();
//        mShutterSoundEnabled = settingsManager.getBoolean(
//                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_SHUTTER_SOUND,
//                true);
        updateSceneMode();
        //SPRD:fix bug534270 shuttersound is still ring when it is off
        updateCameraShutterSound();
    }

    @Override
    public void updatePreviewAspectRatio(float aspectRatio) {
        mAppController.updatePreviewAspectRatio(aspectRatio);
    }

    private void resetExposureCompensation() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if (settingsManager == null) {
            Log.e(TAG, "Settings manager is null!");
            return;
        }
        settingsManager.setToDefault(mAppController.getCameraScope(),
                                     Keys.KEY_EXPOSURE);
    }

    // Snapshots can only be taken after this is called. It should be called
    // once only. We could have done these things in onCreate() but we want to
    // make preview screen appear as soon as possible.
    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        mUI.initializeFirstTime();

        // We set the listener only when both service and shutterbutton
        // are initialized.
        getServices().getMemoryManager().addListener(this);

        mNamedImages = new NamedImages();

        mFirstTimeInitialized = true;
        addIdleHandler();

        mActivity.updateStorageSpaceAndHint(null);
    }

    // If the activity is paused and resumed, this method will be called in
    // onResume.
    private void initializeSecondTime() {
        getServices().getMemoryManager().addListener(this);
        mNamedImages = new NamedImages();
        mUI.initializeSecondTime(mCameraCapabilities, mCameraSettings);
    }

    private void addIdleHandler() {
        MessageQueue queue = Looper.myQueue();
        queue.addIdleHandler(new MessageQueue.IdleHandler() {
            @Override
            public boolean queueIdle() {
                Storage.ensureOSXCompatible();
                return false;
            }
        });
    }

    @Override
    public void startFaceDetection() {
        if (mFaceDetectionStarted || mCameraDevice == null) {
            return;
        }
        Log.i(TAG,"     startFaceDetection.....    mCameraCapabilities.getMaxNumOfFacesSupported()="+mCameraCapabilities.getMaxNumOfFacesSupported());
//        if (mCameraCapabilities.getMaxNumOfFacesSupported() > 0) {
            mFaceDetectionStarted = true;
            mUI.onStartFaceDetection(mDisplayOrientation, isCameraFrontFacing());
            mCameraDevice.setFaceDetectionCallback(mHandler, mUI);
            mCameraDevice.startFaceDetection();
            SessionStatsCollector.instance().faceScanActive(true);
//        }
    }

    @Override
    public void stopFaceDetection() {
        mUI.clearFaces();
        if (!mFaceDetectionStarted || mCameraDevice == null) {
            return;
        }
        Log.i(TAG,"     stopFaceDetection.....    ");
//        if (mCameraCapabilities.getMaxNumOfFacesSupported() > 0) {
            mFaceDetectionStarted = false;
            mCameraDevice.setFaceDetectionCallback(null, null);
            mCameraDevice.stopFaceDetection();
            mUI.clearFaces();
            SessionStatsCollector.instance().faceScanActive(false);
//        }
    }

    private final class ShutterCallback
            implements CameraShutterCallback {

        private final boolean mNeedsAnimation;

        public ShutterCallback(boolean needsAnimation) {
            mNeedsAnimation = needsAnimation;
        }

        @Override
        public void onShutter(CameraProxy camera) {
            mShutterCallbackTime = System.currentTimeMillis();
            mShutterLag = mShutterCallbackTime - mCaptureStartTime;
            Log.i(TAG, "mShutterLag = " + mShutterLag + "ms");
            if (mNeedsAnimation) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        animateAfterShutter();
                    }
                });
            }
        }
    }

    private final class PostViewPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte[] data, CameraProxy camera) {
            mPostViewPictureCallbackTime = System.currentTimeMillis();
            Log.i(TAG, "mShutterToPostViewCallbackTime = "
                    + (mPostViewPictureCallbackTime - mShutterCallbackTime)
                    + "ms");
        }
    }

    private final class RawPictureCallback
            implements CameraPictureCallback {
        @Override
        public void onPictureTaken(byte[] rawData, CameraProxy camera) {
            mRawPictureCallbackTime = System.currentTimeMillis();
            Log.i(TAG, "mShutterToRawCallbackTime = "
                    + (mRawPictureCallbackTime - mShutterCallbackTime) + "ms");
        }
    }

    private static class ResizeBundle {
        byte[] jpegData;
        float targetAspectRatio;
        ExifInterface exif;
    }

    /**
     * @return Cropped image if the target aspect ratio is larger than the jpeg
     *         aspect ratio on the long axis. The original jpeg otherwise.
     */
    private ResizeBundle cropJpegDataToAspectRatio(ResizeBundle dataBundle) {

        final byte[] jpegData = dataBundle.jpegData;
        final ExifInterface exif = dataBundle.exif;
        float targetAspectRatio = dataBundle.targetAspectRatio;

        Bitmap original = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        int newWidth;
        int newHeight;

        if (originalWidth > originalHeight) {
            newHeight = (int) (originalWidth / targetAspectRatio);
            newWidth = originalWidth;
        } else {
            newWidth = (int) (originalHeight / targetAspectRatio);
            newHeight = originalHeight;
        }
        int xOffset = (originalWidth - newWidth)/2;
        int yOffset = (originalHeight - newHeight)/2;

        if (xOffset < 0 || yOffset < 0) {
            return dataBundle;
        }

        Bitmap resized = Bitmap.createBitmap(original,xOffset,yOffset,newWidth, newHeight);
        exif.setTagValue(ExifInterface.TAG_PIXEL_X_DIMENSION, new Integer(newWidth));
        exif.setTagValue(ExifInterface.TAG_PIXEL_Y_DIMENSION, new Integer(newHeight));

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        resized.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        dataBundle.jpegData = stream.toByteArray();
        return dataBundle;
    }

    private MediaActionSound mCameraSound;
    private Location mSprdLocation;

    private final class JpegPictureCallback
            implements CameraPictureCallback {
        Location mLocation;

        public JpegPictureCallback(Location loc) {
            mLocation = loc;
            mSprdLocation = mLocation;
        }

        @Override
        public void onPictureTaken(final byte[] originalJpegData, final CameraProxy camera) {
            Log.i(TAG, "onPictureTaken mCameraState="+mCameraState);
            mAppController.setShutterEnabled(true);
            if (mPaused) {
                return;
            }
            mContinuousCaptureCount--;
            if (mShutterSoundEnabled && mActivity.getCameraProvider().isNewApi()) {
                mCameraSound.play(MediaActionSound.SHUTTER_CLICK);
            }
            if (mIsImageCaptureIntent) {
                stopPreview();
            }
            if (mSceneMode == CameraCapabilities.SceneMode.HDR) {
                mUI.setSwipingEnabled(true);
            }
            mNamedImages.nameNewImage(mCaptureStartTime);
            mJpegPictureCallbackTime = System.currentTimeMillis();
            captureMagic();
            // If postview callback has arrived, the captured image is displayed
            // in postview callback. If not, the captured image is displayed in
            // raw picture callback.
            if (mPostViewPictureCallbackTime != 0) {
                mShutterToPictureDisplayedTime =
                        mPostViewPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mPostViewPictureCallbackTime;
            } else {
                mShutterToPictureDisplayedTime =
                        mRawPictureCallbackTime - mShutterCallbackTime;
                mPictureDisplayedToJpegCallbackTime =
                        mJpegPictureCallbackTime - mRawPictureCallbackTime;
            }
            Log.i(TAG, "mPictureDisplayedToJpegCallbackTime = "
                    + mPictureDisplayedToJpegCallbackTime + "ms");

            updateFocusUI();
            if (!mIsImageCaptureIntent
                    && (GservicesHelper.useCamera2ApiThroughPortabilityLayer(mActivity.getContentResolver())
                    ? true : (mContinuousCaptureCount <= 0))) {
                setupPreview();
            }

            long now = System.currentTimeMillis();
            mJpegCallbackFinishTime = now - mJpegPictureCallbackTime;
            Log.i(TAG, "mJpegCallbackFinishTime = " + mJpegCallbackFinishTime + "ms");
            mJpegPictureCallbackTime = 0;

            final ExifInterface exif = Exif.getExif(originalJpegData);
            final NamedEntity name = mNamedImages.getNextNameEntity();
            if (mShouldResizeTo16x9) {
                Log.i(TAG,"mShouldResizeTo16x9");
                final ResizeBundle dataBundle = new ResizeBundle();
                dataBundle.jpegData = originalJpegData;
                dataBundle.targetAspectRatio = ResolutionUtil.NEXUS_5_LARGE_16_BY_9_ASPECT_RATIO;
                dataBundle.exif = exif;
                new AsyncTask<ResizeBundle, Void, ResizeBundle>() {

                    @Override
                    protected ResizeBundle doInBackground(ResizeBundle... resizeBundles) {
                        return cropJpegDataToAspectRatio(resizeBundles[0]);
                    }

                    @Override
                    protected void onPostExecute(ResizeBundle result) {
                        saveFinalPhoto(result.jpegData, name, result.exif, camera);
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, dataBundle);

            } else {
                saveFinalPhoto(originalJpegData, name, exif, camera);
            }
        }
    }

           protected  void saveFinalPhoto(final byte[] jpegData, NamedEntity name, final ExifInterface exif,
                CameraProxy camera){
            Log.i(TAG, "saveFinalPhoto start!");
            int orientation = Exif.getOrientation(exif);

            float zoomValue = 1.0f;
            if (mCameraCapabilities.supports(CameraCapabilities.Feature.ZOOM)) {
                zoomValue = mCameraSettings.getCurrentZoomRatio();
            }
            boolean hdrOn = CameraCapabilities.SceneMode.HDR == mSceneMode;
            String flashSetting =
                    mActivity.getSettingsManager().getString(mAppController.getCameraScope(),
                                                             Keys.KEY_FLASH_MODE);
            boolean gridLinesOn = Keys.areGridLinesOn(mActivity.getSettingsManager());
            Log.i(TAG, "saveFinalPhoto title="+mNamedImages.mQueue.lastElement().title + ".jpg");
            // CID 123725 : Dereference before null check (REVERSE_INULL)
            if(name != null){
                UsageStatistics.instance().photoCaptureDoneEvent(
                        eventprotos.NavigationChange.Mode.PHOTO_CAPTURE,
                        name.title + ".jpg", exif,
                        isCameraFrontFacing(), hdrOn, zoomValue, flashSetting, gridLinesOn,
                        (float) mTimerDuration, null, mShutterTouchCoordinate, mVolumeButtonClickedFlag,
                        null, null, null);
            }
            mShutterTouchCoordinate = null;
            mVolumeButtonClickedFlag = false;
            Log.i(TAG, "saveFinalPhoto mIsImageCaptureIntent="+mIsImageCaptureIntent);
            if (!mIsImageCaptureIntent) {
                // Calculate the width and the height of the jpeg.
                Integer exifWidth = exif.getTagIntValue(ExifInterface.TAG_PIXEL_X_DIMENSION);
                Integer exifHeight = exif.getTagIntValue(ExifInterface.TAG_PIXEL_Y_DIMENSION);
                int width, height;
                if (mShouldResizeTo16x9 && exifWidth != null && exifHeight != null) {
                    width = exifWidth;
                    height = exifHeight;
                } else {
                    Size s;
                    s = new Size(mCameraSettings.getCurrentPhotoSize());
                    if ((mJpegRotation + orientation) % 180 == 0) {
                        width = s.width();
                        height = s.height();
                    } else {
                        width = s.height();
                        height = s.width();
                    }
                }
                String title = (name == null) ? null : name.title;
                long date = (name == null) ? -1 : name.date;
                Log.i(TAG, "title="+title+" date="+date);
                // Handle debug mode outputs
                if (mDebugUri != null) {
                    // If using a debug uri, save jpeg there.
                    saveToDebugUri(jpegData);

                    // Adjust the title of the debug image shown in mediastore.
                    if (title != null) {
                        title = DEBUG_IMAGE_PREFIX + title;
                    }
                }

                if (title == null) {
                    Log.e(TAG, "Unbalanced name/data pair");
                } else {
                    if (date == -1) {
                        date = mCaptureStartTime;
                    }
                    int heading = mHeadingSensor.getCurrentHeading();
                    if (heading != HeadingSensor.INVALID_HEADING) {
                        // heading direction has been updated by the sensor.
                        ExifTag directionRefTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
                                ExifInterface.GpsTrackRef.MAGNETIC_DIRECTION);
                        ExifTag directionTag = exif.buildTag(
                                ExifInterface.TAG_GPS_IMG_DIRECTION,
                                new Rational(heading, 1));
                        exif.setTag(directionRefTag);
                        exif.setTag(directionTag);
                    }
                    getServices().getMediaSaver().addImage(
                            jpegData, title, date, mSprdLocation, width, height,
                            orientation, exif, mOnMediaSavedListener);
                }
                // Animate capture with real jpeg data instead of a preview
                // frame.
//                mUI.animateCapture(jpegData, orientation, mMirror);
            } else {
                Log.i(TAG, "saveFinalPhoto mQuickCapture="+mQuickCapture);
                mJpegImageData = jpegData;
                if (!mQuickCapture) {
                    Log.v(TAG, "showing UI");
                    mUI.showCapturedImageForReview(jpegData, orientation, mMirror);
                    mAppController.getCameraAppUI().hideModeOptions();//SPRD BUG: 402084
                } else {
                    onCaptureDone();
                }
            }

            // Send the taken photo to remote shutter listeners, if any are
            // registered.
            getServices().getRemoteShutterListener().onPictureTaken(jpegData);

            // Check this in advance of each shot so we don't add to shutter
            // latency. It's true that someone else could write to the SD card
            // in the mean time and fill it, but that could have happened
            // between the shutter press and saving the JPEG too.
//            if (mContinuousCaptureCount <= 0) {//SPRD BUG:388273
//                mAppController.getCameraAppUI().setSwipeEnabled(true);
//                mAppController.setShutterEnabled(true);
//            }
            mActivity.updateStorageSpaceAndHint(null);
            Log.i(TAG, "saveFinalPhoto end! mCameraState="+mCameraState+" mContinuousCaptureCount="+mContinuousCaptureCount);
        }

    private final class AutoFocusCallback implements CameraAFCallback {
        @Override
        public void onAutoFocus(boolean focused, CameraProxy camera) {
            SessionStatsCollector.instance().autofocusResult(focused);
            if (mPaused) {
                return;
            }

            mAutoFocusTime = System.currentTimeMillis() - mFocusStartTime;
            Log.i(TAG, "mAutoFocusTime = " + mAutoFocusTime + "ms   focused = "+focused);
            setCameraState(IDLE);
            mFocusManager.onAutoFocus(focused, false);
        }
    }

    private final class AutoFocusMoveCallback
            implements CameraAFMoveCallback {
        @Override
        public void onAutoFocusMoving(
                boolean moving, CameraProxy camera) {
            mFocusManager.onAutoFocusMoving(moving);
            SessionStatsCollector.instance().autofocusMoving(moving);
        }
    }

    /**
     * This class is just a thread-safe queue for name,date holder objects.
     */
    public static class NamedImages {
        private final Vector<NamedEntity> mQueue;

        public NamedImages() {
            mQueue = new Vector<NamedEntity>();
        }

        public void nameNewImage(long date) {
            NamedEntity r = new NamedEntity();
            r.title = CameraUtil.instance().createJpegName(date);
            r.date = date;
            mQueue.add(r);
        }

        public NamedEntity getNextNameEntity() {
            synchronized (mQueue) {
                if (!mQueue.isEmpty()) {
                    return mQueue.remove(0);
                }
            }
            return null;
        }

        public static class NamedEntity {
            public String title;
            public long date;
        }
    }

    protected void setCameraState(int state) {
        mCameraState = state;
        switch (state) {
            case PREVIEW_STOPPED:
            case SNAPSHOT_IN_PROGRESS:
            case SWITCHING_CAMERA:
                // TODO: Tell app UI to disable swipe
                break;
            case PhotoController.IDLE:
                // TODO: Tell app UI to enable swipe
                break;
        }
    }

    private void animateAfterShutter() {
        // Only animate when in full screen capture mode
        // i.e. If monkey/a user swipes to the gallery during picture taking,
        // don't show animation
        if (!mIsImageCaptureIntent) {
            mUI.animateFlash();
        }
    }

    @Override
    public boolean capture() {
        Log.i(TAG, "capture");
        // If we are already in the middle of taking a snapshot or the image
        // save request is full then ignore.
        if (mCameraDevice == null || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA) {
            return false;
        }
        setCameraState(SNAPSHOT_IN_PROGRESS);

        mCaptureStartTime = System.currentTimeMillis();

        if (getContinuousCount() > 1) {//SPRD BUG:388273
            mUI.enableBurstScreenHint(true);
        }

        mPostViewPictureCallbackTime = 0;
        mJpegImageData = null;

        final boolean animateBefore = (mSceneMode == CameraCapabilities.SceneMode.HDR);

        if (animateBefore) {
            animateAfterShutter();
        }

        Location loc = mActivity.getLocationManager().getCurrentLocation();
        CameraUtil.setGpsParameters(mCameraSettings, loc);
        mCameraDevice.applySettings(mCameraSettings);

        // Set JPEG orientation. Even if screen UI is locked in portrait, camera orientation should
        // still match device orientation (e.g., users should always get landscape photos while
        // capturing by putting device in landscape.)
        Characteristics info = mActivity.getCameraProvider().getCharacteristics(mCameraId);
        int sensorOrientation = info.getSensorOrientation();
        int deviceOrientation =
                mAppController.getOrientationManager().getDeviceOrientation().getDegrees();
        boolean isFrontCamera = info.isFacingFront();
        mJpegRotation =
                CameraUtil.getImageRotation(sensorOrientation, deviceOrientation, isFrontCamera);
        mCameraDevice.setJpegOrientation(mJpegRotation);

        Log.i(TAG, "takePicture start!");
        mCameraDevice.takePicture(mHandler,
                /**
                 * SPRD: fix bug462021 remove capture animation
                 * @{
                new ShutterCallback(!animateBefore),
                 */
                new ShutterCallback(false),
                /**
                 * @}
                 */
                mRawPictureCallback, mPostViewPictureCallback,
                new JpegPictureCallback(loc));

        mNamedImages.nameNewImage(mCaptureStartTime);

        mFaceDetectionStarted = false;
        return true;
    }

    @Override
    public void setFocusParameters() {
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    protected void updateSceneMode() {
        // If scene mode is set, we cannot set flash mode, white balance, and
        // focus mode, instead, we read it from driver. Some devices don't have
        // any scene modes, so we must check both NO_SCENE_MODE in addition to
        // AUTO to check where there is no actual scene mode set.
        if (!(CameraCapabilities.SceneMode.AUTO == mSceneMode ||
                CameraCapabilities.SceneMode.NO_SCENE_MODE == mSceneMode)) {
            overrideCameraSettings(mCameraSettings.getCurrentFlashMode(),
                    mCameraSettings.getCurrentFocusMode());
        }
    }

    private void overrideCameraSettings(CameraCapabilities.FlashMode flashMode,
            CameraCapabilities.FocusMode focusMode) {
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if ((flashMode != null) && (!CameraCapabilities.FlashMode.NO_FLASH.equals(flashMode))) {
            String flashModeString = stringifier.stringify(flashMode);
            Log.v(TAG, "override flash setting to: " + flashModeString);
            settingsManager.set(mAppController.getCameraScope(), Keys.KEY_FLASH_MODE,
                    flashModeString);
        } else {
            Log.v(TAG, "skip setting flash mode on override due to NO_FLASH");
        }
        if (focusMode != null) {
            String focusModeString = stringifier.stringify(focusMode);
            Log.v(TAG, "override focus setting to: " + focusModeString);
            settingsManager.set(mAppController.getCameraScope(), Keys.KEY_FOCUS_MODE,
                    focusModeString);
        }
    }

    public void onOrientationChanged(int orientation) {
    }

    @Override
    public void onCameraAvailable(CameraProxy cameraProxy) {
        Log.i(TAG, "onCameraAvailable");
        if (mPaused) {
            return;
        }
        mHandler.removeCallbacks(mResumeTaskRunnable);//SPRD BUG: 401253
        mCameraDevice = cameraProxy;
        initializeCapabilities();
        // mCameraCapabilities is guaranteed to initialized at this point.
        mAppController.getCameraAppUI().showAccessibilityZoomUI(
                mCameraCapabilities.getMaxZoomRatio());


        // Reset zoom value index.
        mZoomValue = 1.0f;
        if (mFocusManager == null) {
            initializeFocusManager();
        }
        mFocusManager.updateCapabilities(mCameraCapabilities);

        // Do camera parameter dependent initialization.
        mParameters = mCameraDevice.getParameters();
        mCameraSettings = mCameraDevice.getSettings();
        // Set a default flash mode and focus mode
        if (mCameraSettings.getCurrentFlashMode() == null) {
            mCameraSettings.setFlashMode(CameraCapabilities.FlashMode.NO_FLASH);
        }
        if (mCameraSettings.getCurrentFocusMode() == null) {
            mCameraSettings.setFocusMode(CameraCapabilities.FocusMode.AUTO);
        }

        setCameraParameters(UPDATE_PARAM_ALL);
        // Set a listener which updates camera parameters based
        // on changed settings.
        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.addListener(this);
        mCameraPreviewParamsReady = true;

        startPreview();

        onCameraOpened();

        mHardwareSpec = new HardwareSpecImpl(getCameraProvider(), mCameraCapabilities,
                mAppController.getCameraFeatureConfig(), isCameraFrontFacing());

        ButtonManager buttonManager = mActivity.getButtonManager();
        buttonManager.enableCameraButton();
    }

    @Override
    public void onCaptureCancelled() {
        mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
        mActivity.finish();
    }

    @Override
    public void onCaptureRetake() {
        Log.i(TAG, "onCaptureRetake");
        if (mPaused) {
            return;
        }
        mUI.hidePostCaptureAlert();
        mUI.hideIntentReviewImageView();
        mAppController.getCameraAppUI().showModeOptions();//SPRD BUG: 402084
        setupPreview();
    }

    @Override
    public void onCaptureDone() {
        Log.i(TAG, "onCaptureDone");
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value. If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    Log.i(TAG, "saved result to URI: " + mSaveUri);
                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    Log.i(TAG, "exception saving result to URI: " + mSaveUri, ex);
                    // ignore exception
                    onError();
                } finally {
                    CameraUtil.closeSilently(outputStream);
                }
            } else {
                ExifInterface exif = Exif.getExif(data);
                int orientation = Exif.getOrientation(exif);
                Bitmap bitmap = CameraUtil.makeBitmap(data, 50 * 1024);
                bitmap = CameraUtil.rotate(bitmap, orientation);
                Log.i(TAG, "inlined bitmap into capture intent result");
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                /*
                 *  CID 109179 : RV: Bad use of return value (FB.RV_RETURN_VALUE_IGNORED_BAD_PRACTICE) 
                 *  @{ */
                if(path.exists ()){
                    if(!path.delete()){
                        throw new IOException();
                    }
                }
                // path.delete();
                /* @} */

                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
                Log.i(TAG, "wrote temp file for cropping to: " + sTempCropFilename);
            } catch (FileNotFoundException ex) {
                Log.i(TAG, "error writing temp cropping file to: " + sTempCropFilename, ex);
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                onError();
                return;
            } catch (IOException ex) {
                Log.i(TAG, "error writing temp cropping file to: " + sTempCropFilename, ex);
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                onError();
                return;
            } finally {
                CameraUtil.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                Log.i(TAG, "setting output of cropped file to: " + mSaveUri);
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CameraUtil.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CameraUtil.KEY_SHOW_WHEN_LOCKED, true);
            }

            // TODO: Share this constant.
            final String CROP_ACTION = "com.android.camera.action.CROP";
            Intent cropIntent = new Intent(CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);
            Log.i(TAG, "starting CROP intent for capture");
            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    @Override
    public void onShutterCoordinate(TouchCoordinate coord) {
        mShutterTouchCoordinate = coord;
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        // Do nothing. We don't support half-press to focus anymore.
    }

    @Override
    public void onShutterButtonClick() {
        Log.i(TAG, "onShutterButtonClick mPaused:" + mPaused + ",mCameraState:" + mCameraState+" isShutterEnabled=" +isShutterEnabled());
        if (mPaused || (mCameraState == SWITCHING_CAMERA)
                || (mCameraState == PREVIEW_STOPPED) || !isShutterEnabled()/*|| mActivity.isFilmstripCoversPreview()*/) {// SPRD:Fix bug 399745
            Log.i(TAG, "onShutterButtonClick is return !");
            mVolumeButtonClickedFlag = false;
            return;
        }

        // Do not take the picture if there is not enough storage.
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            mVolumeButtonClickedFlag = false;
            return;
        }
        //mAppController.setShutterEnabled(false); // SPRD: FixBug 388808
        if (!mIsImageCaptureIntent) {// SPRD BUG:389377
            mContinuousCaptureCount = getContinuousCount();
        }
        Log.i(TAG, "onShutterButtonClick: mCameraState=" + mCameraState +
                " mVolumeButtonClickedFlag=" + mVolumeButtonClickedFlag+" mContinuousCaptureCount="+mContinuousCaptureCount);
        if (getContinuousCount() > 1) {
            mAppController.getCameraAppUI().setSwipeEnabled(false);
            mAppController.getCameraAppUI().hideModeOptions();
        }
        int countDownDuration = mActivity.getSettingsManager()
            .getInteger(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
        mTimerDuration = countDownDuration;
        if (countDownDuration > 0) {
            // Start count down.
            mAppController.getCameraAppUI().transitionToCancel();
            mAppController.getCameraAppUI().hideModeOptions();
            mAppController.getCameraAppUI().setSwipeEnabled(false);// SPRD: FixBug 391838
            mUI.startCountdown(countDownDuration);
            return;
        } else {
            /*SPRD: Bug385148 Disable shuterbutton after click */
            mAppController.setShutterEnabled(false);
            focusAndCapture();
        }
    }

    private void focusAndCapture() {
        Log.i(TAG, "focusAndCapture");
        if (mSceneMode == CameraCapabilities.SceneMode.HDR) {
            mUI.setSwipingEnabled(false);
        }
        // If the user wants to do a snapshot while the previous one is still
        // in progress, remember the fact and do it after we finish the previous
        // one and re-start the preview. Snapshot in progress also includes the
        // state that autofocus is focusing and a picture will be taken when
        // focus callback arrives.
        if ((mFocusManager.isFocusingSnapOnFinish() || mCameraState == SNAPSHOT_IN_PROGRESS)) {
            if (!mIsImageCaptureIntent) {
                mSnapshotOnIdle = true;
            }
            return;
        }

        mSnapshotOnIdle = false;
        mFocusManager.focusAndCapture(mCameraSettings.getCurrentFocusMode());
    }

    @Override
    public void onRemainingSecondsChanged(int remainingSeconds) {
        if (remainingSeconds == 1 && !isInSilentMode()) {
            mCountdownSoundPlayer.play(R.raw.timer_final_second, 0.6f);
        } else if ((remainingSeconds == 2 || remainingSeconds == 3) && !isInSilentMode()) {
            mCountdownSoundPlayer.play(R.raw.timer_increment, 0.6f);
        } else if (remainingSeconds == 0) {
            mAppController.setShutterEnabled(false);
        }
    }

    /* SPRD:fix bug 550298 @{ */
    private boolean isInSilentMode() {
        AudioManager mAudioManager = (AudioManager)mAppController.getAndroidContext().getSystemService(mAppController.getAndroidContext().AUDIO_SERVICE);
        return (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_SILENT);
    }
    /* @} */

    @Override
    public void onCountDownFinished() {
        mAppController.getCameraAppUI().transitionToCapture();
        // mAppController.getCameraAppUI().showModeOptions(); SPRD: FxiBug 385982, Duplicate show mode options.
        if (mPaused) {
            return;
        }
        focusAndCapture();
    }

    private void onResumeTasks() {
        if (mPaused) {
            return;
        }
        Log.i(TAG, "Executing onResumeTasks.");

        mCountdownSoundPlayer.loadSound(R.raw.timer_final_second);
        mCountdownSoundPlayer.loadSound(R.raw.timer_increment);
        if (mFocusManager != null) {
            // If camera is not open when resume is called, focus manager will
            // not be initialized yet, in which case it will start listening to
            // preview area size change later in the initialization.
            mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
        }
        mAppController.addPreviewAreaSizeChangedListener(mUI);

        CameraProvider camProvider = mActivity.getCameraProvider();
        if (camProvider == null) {
            // No camera provider, the Activity is destroyed already.
            return;
        }
        requestCameraOpen();
        mUI.intializeAIDetection(mActivity.getSettingsManager());//SPRD:fix bug464276 intialize AI Detection

        mJpegPictureCallbackTime = 0;
        mZoomValue = 1.0f;

        mOnResumeTime = SystemClock.uptimeMillis();
        checkDisplayRotation();

        // If first time initialization is not finished, put it in the
        // message queue.
        if (!mFirstTimeInitialized) {
            mHandler.sendEmptyMessage(MSG_FIRST_TIME_INIT);
        } else {
            initializeSecondTime();
        }

        mHeadingSensor.activate();

        getServices().getRemoteShutterListener().onModuleReady(this);
        SessionStatsCollector.instance().sessionActive(true);
//        getServices().getMediaSaver().setListener(this);//SPRD BUG:388273
    }

    /**
     * @return Whether the currently active camera is front-facing.
     */
    public boolean isCameraFrontFacing() {
        return mAppController.getCameraProvider().getCharacteristics(mCameraId)
                .isFacingFront();
    }

    /**
     * The focus manager is the first UI related element to get initialized, and
     * it requires the RenderOverlay, so initialize it here
     */
    private void initializeFocusManager() {
        // Create FocusManager object. startPreview needs it.
        // if mFocusManager not null, reuse it
        // otherwise create a new instance
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        } else {
            mMirror = isCameraFrontFacing();
            String[] defaultFocusModesStrings = mActivity.getResources().getStringArray(
                    R.array.pref_camera_focusmode_default_array);
            ArrayList<CameraCapabilities.FocusMode> defaultFocusModes =
                    new ArrayList<CameraCapabilities.FocusMode>();
            CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
            for (String modeString : defaultFocusModesStrings) {
                CameraCapabilities.FocusMode mode = stringifier.focusModeFromString(modeString);
                if (mode != null) {
                    defaultFocusModes.add(mode);
                }
            }
            mFocusManager =
                    new FocusOverlayManager(mAppController, defaultFocusModes,
                            mCameraCapabilities, this, mMirror, mActivity.getMainLooper(),
                            mUI.getFocusRing());
            mMotionManager = getServices().getMotionManager();
            if (mMotionManager != null) {
                mMotionManager.addListener(mFocusManager);
            }
        }
        mAppController.addPreviewAreaSizeChangedListener(mFocusManager);
    }

    /**
     * @return Whether we are resuming from within the lockscreen.
     */
    private boolean isResumeFromLockscreen() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)
                || MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE.equals(action));
    }

    @Override
    public void resume() {
        Log.i(TAG, "resume start!");
        mPaused = false;
        mCountdownSoundPlayer = new SoundPlayer(mAppController.getAndroidContext());//SPRD:Fix bug 388271
        mShutterSound.loadSounds(mActivity);

        if (mCameraSound == null) {
            mCameraSound = new MediaActionSound();
            // Not required, but reduces latency when playback is requested
            // later.
            mCameraSound.load(MediaActionSound.SHUTTER_CLICK);
        }

        // Add delay on resume from lock screen only, in order to to speed up
        // the onResume --> onPause --> onResume cycle from lock screen.
        // Don't do always because letting go of thread can cause delay.
        if (isResumeFromLockscreen()) {
            Log.i(TAG, "On resume, from lock screen.");
            // Note: onPauseAfterSuper() will delete this runnable, so we will
            // at most have 1 copy queued up.
            if(mCameraDevice == null){//SPRD:Fix bug 393561
                Log.i(TAG, "mResumeTaskRunnable time:" + ON_RESUME_TASKS_DELAY_MSEC);
                mHandler.postDelayed(mResumeTaskRunnable, ON_RESUME_TASKS_DELAY_MSEC);
            }
        } else {
            Log.i(TAG, "On resume.");
            onResumeTasks();
        }
        Log.i(TAG, "resume end!");
    }

    @Override
    public void pause() {
        Log.i(TAG, "pause start!");
        mPaused = true;
        mUI.enableBurstScreenHint(false);//SPRD BUG: 391793
        mHandler.removeCallbacks(mResumeTaskRunnable);
        getServices().getRemoteShutterListener().onModuleExit();
        SessionStatsCollector.instance().sessionActive(false);

        mHeadingSensor.deactivate();

        // Reset the focus first. Camera CTS does not guarantee that
        // cancelAutoFocus is allowed after preview stops.
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            mCameraDevice.cancelAutoFocus();
        }

        // If the camera has not been opened asynchronously yet,
        // and startPreview hasn't been called, then this is a no-op.
        // (e.g. onResume -> onPause -> onResume).
        stopPreview();
        cancelCountDown();
        if (mCountdownSoundPlayer != null) {
            mCountdownSoundPlayer.unloadSound(R.raw.timer_final_second);
            mCountdownSoundPlayer.unloadSound(R.raw.timer_increment);
        }
        mShutterSound.restoreSystemShutterSound();
        // SPRD: Bug540238 SoundPool leaks.
        mShutterSound.unloadSounds();

        if (!mActivity.getCameraAppUI().isInIntentReview()) {
            mNamedImages = null;
            // If we are in an image capture intent and has taken
            // a picture, we just clear it in onPause.
            mJpegImageData = null;
        }

        // Remove the messages and runnables in the queue.
        mHandler.removeCallbacksAndMessages(null);

        if (mMotionManager != null) {
            mMotionManager.removeListener(mFocusManager);
            mMotionManager = null;
        }

        closeCamera();
        mActivity.enableKeepScreenOn(false);
        mUI.onPause();
        setModeOptionsLayout();

        mPendingSwitchCameraId = -1;
        if (mFocusManager != null) {
            mFocusManager.removeMessages();
        }
        getServices().getMemoryManager().removeListener(this);
        mAppController.removePreviewAreaSizeChangedListener(mFocusManager);
        mAppController.removePreviewAreaSizeChangedListener(mUI);

        SettingsManager settingsManager = mActivity.getSettingsManager();
        settingsManager.removeListener(this);

        if(mCurrentModule.equals("UcamFilterPhotoModule")){
            setScreenOrientationAuto();
        }
        if (mCameraSound != null) {
            mCameraSound.release();
            mCameraSound = null;
        }
        setFilteVisibility();

        // SPRD: Bug540238 SoundPool leaks.
        if (mCountdownSoundPlayer != null) {
            mCountdownSoundPlayer.release();
        }
        Log.i(TAG, "pause end!");
    }

    @Override
    public void destroy() {
        Log.i(TAG, "destroy");
        // TODO: implement this.
    }

    @Override
    public void onLayoutOrientationChanged(boolean isLandscape) {
        setDisplayOrientation();
        mUI.setButtonOrientation(mActivity.getOrientationManager().getDisplayRotation());
    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation()) {
            setDisplayOrientation();
        }
    }

    protected boolean canTakePicture() {
        return isCameraIdle()
                && (mActivity.getStorageSpaceBytes() > Storage.LOW_STORAGE_THRESHOLD_BYTES);
    }

    @Override
    public void autoFocus() {
        if (mCameraDevice == null) {
            return;
        }
        Log.i(TAG,"Starting auto focus");
        mFocusStartTime = System.currentTimeMillis();
        mCameraDevice.autoFocus(mHandler, mAutoFocusCallback);
        SessionStatsCollector.instance().autofocusManualTrigger();
        setCameraState(FOCUSING);
        Log.i(TAG, "autoFocus end!");
    }

    @Override
    public void cancelAutoFocus() {
        if (mCameraDevice == null) {
            return;
        }
        mCameraDevice.cancelAutoFocus();
        setCameraState(IDLE);
        setCameraParameters(UPDATE_PARAM_PREFERENCE);
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || mCameraDevice == null || !mFirstTimeInitialized
                || mCameraState == SNAPSHOT_IN_PROGRESS
                || mCameraState == SWITCHING_CAMERA
                || mCameraState == PREVIEW_STOPPED) {
            return;
        }

        // Check if metering area or focus area is supported.
        if (!mFocusAreaSupported && !mMeteringAreaSupported) {
            return;
        }
        mFocusManager.onSingleTapUp(x, y);
    }

    @Override
    public boolean onBackPressed() {
        return mUI.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_FOCUS:
                if (/* TODO: mActivity.isInCameraApp() && */mFirstTimeInitialized &&
                    !mActivity.getCameraAppUI().isInIntentReview()) {
                    if (event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // If we get a dpad center event without any focused view, move
                // the focus to the shutter button and press it.
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    // Start auto-focus immediately to reduce shutter lag. After
                    // the shutter button gets the focus, onShutterButtonFocus()
                    // will be called again but it is fine.
                    onShutterButtonFocus(true);
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (/* mActivity.isInCameraApp() && */mFirstTimeInitialized &&
                    !mActivity.getCameraAppUI().isInIntentReview()) {
                    if (mUI.isCountingDown()) {
                    } else {
                        mVolumeButtonClickedFlag = true;
                        onShutterButtonClick();
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return false;
    }

    public void closeCamera() {
        Log.i(TAG, "closeCamera will! mCameraDevice=" + mCameraDevice);
        if (mCameraDevice != null) {
            stopFaceDetection();
            mCameraDevice.setZoomChangeListener(null);
            mCameraDevice.setFaceDetectionCallback(null, null);

            setPreviewDataCallback();
            mFaceDetectionStarted = false;
            mActivity.getCameraProvider().releaseCamera(mCameraDevice.getCameraId());
            mCameraDevice = null;
            setCameraState(PREVIEW_STOPPED);
            mFocusManager.onCameraReleased();
        }
        Log.i(TAG, "closeCamera end!");
    }

    protected void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation();
        Characteristics info =
                mActivity.getCameraProvider().getCharacteristics(mCameraId);
        mDisplayOrientation = info.getPreviewOrientation(mDisplayRotation);
        mCameraDisplayOrientation = mDisplayOrientation;
        mUI.setDisplayOrientation(mDisplayOrientation);
        if (mFocusManager != null) {
            mFocusManager.setDisplayOrientation(mDisplayOrientation);
        }
        // Change the camera display orientation
        if (mCameraDevice != null) {
            mCameraDevice.setDisplayOrientation(mDisplayRotation);
        }
        Log.v(TAG, "setDisplayOrientation (screen:preview) " +
                mDisplayRotation + ":" + mDisplayOrientation);
    }

    /** Only called by UI thread. */
    protected void setupPreview() {
        Log.i(TAG, "setupPreview");
        mFocusManager.resetTouchFocus();
        startPreview();
    }

    /**
     * Returns whether we can/should start the preview or not.
     */
    protected boolean checkPreviewPreconditions() {
        if (mPaused) {
            return false;
        }

        if (mCameraDevice == null) {
            Log.i(TAG, "startPreview: camera device not ready yet.");
            return false;
        }

        SurfaceTexture st = mActivity.getCameraAppUI().getSurfaceTexture();
        if (st == null) {
            Log.i(TAG, "startPreview: surfaceTexture is not ready.");
            return false;
        }

        if (!mCameraPreviewParamsReady) {
            Log.i(TAG, "startPreview: parameters for preview is not ready.");
            return false;
        }
        return true;
    }

    /**
     * The start/stop preview should only run on the UI thread.
     */
    protected void startPreview() {
        Log.i(TAG, "startPreview start!");
        if (mCameraDevice == null) {
            Log.i(TAG, "attempted to start preview before camera device");
            // do nothing
            return;
        }

        if (!checkPreviewPreconditions()) {
            return;
        }
        /* SPRD: add for bug 380597: switch camera preview has a frame error @{ */
//        mActivity.getCameraAppUI().resetPreview();
        /* @} */
        setDisplayOrientation();

        if (!mSnapshotOnIdle) {
            // If the focus mode is continuous autofocus, call cancelAutoFocus
            // to resume it because it may have been paused by autoFocus call.
            if (mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode()) ==
                    CameraCapabilities.FocusMode.CONTINUOUS_PICTURE) {
                mCameraDevice.cancelAutoFocus();
            }
            mFocusManager.setAeAwbLock(false); // Unlock AE and AWB.
        }

        // Nexus 4 must have picture size set to > 640x480 before other
        // parameters are set in setCameraParameters, b/18227551. This call to
        // updateParametersPictureSize should occur before setCameraParameters
        // to address the issue.
        updateParametersPictureSize();

        setCameraParameters(UPDATE_PARAM_ALL);

        mCameraDevice.setPreviewTexture(mActivity.getCameraAppUI().getSurfaceTexture());

        Log.i(TAG, "startPreview");
        // If we're using API2 in portability layers, don't use startPreviewWithCallback()
        // b/17576554
        CameraAgent.CameraStartPreviewCallback startPreviewCallback =
            new CameraAgent.CameraStartPreviewCallback() {
                @Override
                public void onPreviewStarted() {
                    mFocusManager.onPreviewStarted();
                    BasicModule.this.onPreviewStarted();
                    SessionStatsCollector.instance().previewActive(true);
                    if (mSnapshotOnIdle) {
                        mHandler.post(mDoSnapRunnable);
                    }
                }
            };
        if (GservicesHelper.useCamera2ApiThroughPortabilityLayer(mActivity.getContentResolver()) && !mCurrentModule.equals("UcamFilterPhotoModule")) {
            mCameraDevice.startPreview();
            startPreviewCallback.onPreviewStarted();
        } else {
            mCameraDevice.startPreviewWithCallback(new Handler(Looper.getMainLooper()),
                    startPreviewCallback);
            setFilterPreviewDataCallback();
        }
        Log.i(TAG, "startPreview end!");
    }

    @Override
    public void stopPreview() {
        Log.i(TAG, "stopPreview start!mCameraDevice=" + mCameraDevice);
        if (mCameraDevice != null && mCameraState != PREVIEW_STOPPED) {
            Log.i(TAG, "stopPreview");
            mCameraDevice.stopPreview();
            mFaceDetectionStarted = false;
        }
        mAppController.setShutterEnabled(true);
        setCameraState(PREVIEW_STOPPED);
        if (mFocusManager != null) {
            mFocusManager.onPreviewStopped();
        }
        SessionStatsCollector.instance().previewActive(false);
        Log.i(TAG, "stopPreview end!");
    }

    @Override
    public void onSettingChanged(SettingsManager settingsManager, String key) {
        Log.i(TAG, "BasicModule onSettingChanged key:" + key);
        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(
                mActivity.getAndroidContext());
        SharedPreferences.Editor editor = defaultPrefs.edit();
        int countDownDuration = mActivity.getSettingsManager().getInteger(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
        if (key.equals(Keys.KEY_COUNTDOWN_DURATION) && countDownDuration > 0) {
            closeFace();
            /* SPRD: fix bug 471387  countDown - ZSL @{ */
//            if(Keys.isZslOn(settingsManager)){
//                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_ZSL_DISPLAY);
//                mCameraSettings.setZslModeEnable(0);
//                Toast.makeText(mActivity,R.string.countdown_on_zsl_off,Toast.LENGTH_SHORT).show();
//            }
            /* @} */
        }
        if (key.equals(Keys.KEY_FLASH_MODE)) {
            updateParametersFlashMode();
            /* SPRD: Fix bug 406333 @{ */
            String flashValues = settingsManager.getString(mAppController.getCameraScope(), Keys.KEY_FLASH_MODE);
            String burstNumber = settingsManager.getString(
                    SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_CONTINUE_CAPTURE);
            if (!"off".equals(flashValues) && !"one".equals(burstNumber) /*|| Keys.isZslOn(settingsManager)*/) {// SPRD:Fix bug 409175
                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_CONTINUE_CAPTURE);
                updateParametersBurstCount();
//                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_ZSL_DISPLAY);
//                mCameraSettings.setZslModeEnable(0);
//                Toast.makeText(mActivity,R.string.flashmode_on_burst_off,Toast.LENGTH_SHORT).show();// SPRD:Fix bug 415356
            }
            if (!"off".equals(flashValues)) {
                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SCENE_MODE);
                updateParametersSceneMode();
            }
            /* @} */
        }
        if (key.equals(Keys.KEY_CAMERA_HDR)) {
            if (settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                           Keys.KEY_CAMERA_HDR)) {
                Log.i(TAG, "KEY_CAMERA_HDR:" + settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_CAMERA_HDR));
                // HDR is on.
                /* SPRD: fix bug 471305  hdr - ZSL @{ */
//                mZSLBeforeSceneMode = settingsManager.getString(
//                        SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_ZSL_DISPLAY);
//                editor.putString(Keys.KEY_CAMERA_ZSL_DISPLAY + 1, mZSLBeforeSceneMode);
//                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_ZSL_DISPLAY);
//                mCameraSettings.setZslModeEnable(0);
                /* @} */
                /* SPRD: fix bug 381386  hdr - color effect; hdr - iso @{ */
                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_COLOR_EFFECT);
                updateParametersColorEffect();
//                settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_ISO);
                updateParametersISO();
                /* @} */
                /* SPRD: hdr - burst @{*/
                if (!(settingsManager.getStringDefault(Keys.KEY_CAMERA_CONTINUE_CAPTURE)
                        .equals(settingsManager.getString(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_CONTINUE_CAPTURE)))) {//SPRD:Fix bug 408178
                    settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_CONTINUE_CAPTURE);
                    updateParametersBurstCount();
                }
                /*@}*/
                /* SPRD: Fix bug 390999  hdr - whitebalance  @{ */
                if (!(settingsManager.getStringDefault(Keys.KEY_WHITE_BALANCE)
                     .equals(settingsManager.getString(SettingsManager.SCOPE_GLOBAL, Keys.KEY_WHITE_BALANCE)))) {
                     settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_WHITE_BALANCE);
                     updateParametersWhiteBalance();
                }
               /* @} */
                mFlashModeBeforeSceneMode = settingsManager.getString(
                        mAppController.getCameraScope(), Keys.KEY_FLASH_MODE);
                // SPRD:FixBug  387101,Set flash mode is off when hdr is on!
                settingsManager.set(mAppController.getCameraScope(), Keys.KEY_FLASH_MODE, "off");
                mAppController.getButtonManager().disableButton(ButtonManager.BUTTON_FLASH);

                closeFace();
                editor.apply();
            } else {
                Log.i(TAG, "mFlashModeBeforeSceneMode:" + mFlashModeBeforeSceneMode);
                if (mFlashModeBeforeSceneMode != null) {
                    settingsManager.set(mAppController.getCameraScope(),
                                        Keys.KEY_FLASH_MODE,
                                        mFlashModeBeforeSceneMode);
                    updateParametersFlashMode();
                    mFlashModeBeforeSceneMode = null;
                }
//                if(CameraUtil.isWCNDisabled()){
//                    mAppController.getButtonManager().disableButton(ButtonManager.BUTTON_FLASH);
//                }else{
                    mAppController.getButtonManager().enableButton(ButtonManager.BUTTON_FLASH);
//                }
//                mZSLBeforeSceneMode = defaultPrefs.getString(Keys.KEY_CAMERA_ZSL_DISPLAY + 1, null);
//                if (mZSLBeforeSceneMode != null) {
//                    settingsManager.set(SettingsManager.SCOPE_GLOBAL,
//                                        Keys.KEY_CAMERA_ZSL_DISPLAY,
//                                        mZSLBeforeSceneMode);
//                    mCameraSettings.setZslModeEnable(1);
//                    mZSLBeforeSceneMode = null;
//                }
            }
        }

        if (mCameraDevice != null) {
            mCameraDevice.applySettings(mCameraSettings);
        }
    }

    protected void updateCameraParametersInitialize() {
        // Reset preview frame rate to the maximum because it may be lowered by
        // video camera application.
        int[] fpsRange = CameraUtil.getPhotoPreviewFpsRange(mCameraCapabilities);
        if (fpsRange != null && fpsRange.length > 0) {
            mCameraSettings.setPreviewFpsRange(fpsRange[0], fpsRange[1]);
        }

        mCameraSettings.setRecordingHintEnabled(false);

        if (mCameraCapabilities.supports(CameraCapabilities.Feature.VIDEO_STABILIZATION)) {
            mCameraSettings.setVideoStabilization(false);
        }
    }

    protected void updateCameraParametersZoom() {
        // Set zoom.
        if (mCameraCapabilities.supports(CameraCapabilities.Feature.ZOOM)) {
            mCameraSettings.setZoomRatio(mZoomValue);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoExposureLockIfSupported() {
        if (mAeLockSupported) {
            mCameraSettings.setAutoExposureLock(mFocusManager.getAeAwbLock());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setAutoWhiteBalanceLockIfSupported() {
        if (mAwbLockSupported) {
            mCameraSettings.setAutoWhiteBalanceLock(mFocusManager.getAeAwbLock());
        }
    }

    private void setFocusAreasIfSupported() {
        if (mFocusAreaSupported) {
            mCameraSettings.setFocusAreas(mFocusManager.getFocusAreas());
        }
    }

    private void setMeteringAreasIfSupported() {
        if (mMeteringAreaSupported) {
            mCameraSettings.setMeteringAreas(mFocusManager.getMeteringAreas());
        }
    }

    protected void updateCameraParametersPreference() {
        // some monkey tests can get here when shutting the app down
        // make sure mCameraDevice is still valid, b/17580046
        if (mCameraDevice == null) {
            return;
        }

        setAutoExposureLockIfSupported();
        setAutoWhiteBalanceLockIfSupported();
        setFocusAreasIfSupported();
        setMeteringAreasIfSupported();

        // Initialize focus mode.
        mFocusManager.overrideFocusMode(null);
        mCameraSettings
                .setFocusMode(mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode()));
        SessionStatsCollector.instance().autofocusActive(
                mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode()) ==
                        CameraCapabilities.FocusMode.CONTINUOUS_PICTURE
        );
        SettingsManager settingsManager = mActivity.getSettingsManager();
        //SPRD:Fix bug 390499
        /*boolean isReset = settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMER_RESET);
        if(isReset) {
            resetCameraSettings(settingsManager);
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMER_RESET, !isReset);
        }*/
        if(mIsImageCaptureIntent){//SPRD:Fix bug 392024
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR, false);
        }else{//SPRD BUG: 403928

        updateParametersAntibanding();

        // Set JPEG quality.
        updateParametersPictureQuality();

        updateParametersBrightness();
        // For the following settings, we need to check if the settings are
        // still supported by latest driver, if not, ignore the settings.

        // Set exposure compensation
        updateParametersExposureCompensation();
        // Set the scene mode: also sets flash and white balance.
        updateParametersSceneMode();
        // Set metering.
        updateParametersMetering();
        // Set white balance.
        updateParametersWhiteBalance() ;

        // Set color effect.
        updateParametersColorEffect();
        // Set Contrast
        updateParametersContrast();
        //Set Saturation
        updateParametersSaturation();

        // Set iso.
        updateParametersISO();

        // Set face datect.
        faceDatectMutex();
        //updateFace();

        //if (!mIsImageCaptureIntent) {// SPRD BUG:389377
            updateParametersBurstCount();
        }

        if (mContinuousFocusSupported && ApiHelper.HAS_AUTO_FOCUS_MOVE_CALLBACK) {
            updateAutoFocusMoveCallback();
        }
    }

    /**
     * This method sets picture size parameters. Size parameters should only be
     * set when the preview is stopped, and so this method is only invoked in
     * {@link #startPreview()} just before starting the preview.
     */
    protected void updateParametersPictureSize() {
        if (mCameraDevice == null) {
            Log.i(TAG, "attempting to set picture size without caemra device");
            return;
        }

        List<Size> supported = Size.convert(mCameraCapabilities.getSupportedPhotoSizes());
        CameraPictureSizesCacher.updateSizesForCamera(mAppController.getAndroidContext(),
                mCameraDevice.getCameraId(), supported);

        OneCamera.Facing cameraFacing =
              isCameraFrontFacing() ? OneCamera.Facing.FRONT : OneCamera.Facing.BACK;
        Size pictureSize;
        try {
            pictureSize = mAppController.getResolutionSetting().getPictureSize(
                  mAppController.getCameraProvider().getCurrentCameraId(),
                  cameraFacing);
        } catch (OneCameraAccessException ex) {
            mAppController.getFatalErrorHandler().onGenericCameraAccessFailure();
            return;
        }

        mCameraSettings.setPhotoSize(pictureSize.toPortabilitySize());

        if (ApiHelper.IS_NEXUS_5) {
            // CID 123755 : EC: Comparing incompatible types for equality (FB.EC_UNRELATED_TYPES)
            // if (ResolutionUtil.NEXUS_5_LARGE_16_BY_9.equals(pictureSize)) {
            if (ResolutionUtil.NEXUS_5_LARGE_16_BY_9.equals(pictureSize.toString())) {
                mShouldResizeTo16x9 = true;
            } else {
                mShouldResizeTo16x9 = false;
            }
        }

        // Set a preview size that is closest to the viewfinder height and has
        // the right aspect ratio.
        List<Size> sizes = Size.convert(mCameraCapabilities.getSupportedPreviewSizes());
        Size optimalSize = CameraUtil.getOptimalPreviewSize(sizes,
                (double) pictureSize.width() / pictureSize.height());
        Size original = new Size(mCameraSettings.getCurrentPreviewSize());
        if (!optimalSize.equals(original)) {
            Log.v(TAG, "setting preview size. optimal: " + optimalSize + "original: " + original);
            mCameraSettings.setPreviewSize(optimalSize.toPortabilitySize());

            mCameraDevice.applySettings(mCameraSettings);
            mCameraSettings = mCameraDevice.getSettings();
        }

        if (optimalSize.width() != 0 && optimalSize.height() != 0) {
            Log.i(TAG, "updating aspect ratio");
            mUI.updatePreviewAspectRatio((float) optimalSize.width()
                    / (float) optimalSize.height());
        }
        Log.d(TAG, "Preview size is " + optimalSize);
    }

    private void updateParametersPictureQuality() {
        if (mJpegQualityController != null) {
            SettingsManager settingsManager = mActivity.getSettingsManager();
            String quality = settingsManager.getString(SettingsManager.SCOPE_GLOBAL,Keys.KEY_JPEG_QUALITY);
            int jpegQuality = mJpegQualityController.findJpegQuality(quality);
            mCameraSettings.setPhotoJpegCompressionQuality(jpegQuality);
        }
    }

    private void updateParametersAntibanding() {
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();

        /*
         * CID 109345 : UrF: Unread field (FB.URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD)
         * @{
        mAntibanding = stringifier.
            antibandingModeFromString(settingsManager.getString(mAppController.getCameraScope(),
                                                      Keys.KEY_CAMER_ANTIBANDING));
        @} */
        String mAntibanding = settingsManager.getString(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMER_ANTIBANDING);
         //if (mCameraSettings.getAntibanding() != mAntibanding) {
        mCameraSettings.setAntibanding(stringifier.antibandingModeFromString(mAntibanding));
         //}
    }

    private void updateParametersExposureCompensation() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        if (settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                       Keys.KEY_EXPOSURE_COMPENSATION_ENABLED)) {
            int value = settingsManager.getInteger(mAppController.getCameraScope(),
                                                   Keys.KEY_EXPOSURE);
            int max = mCameraCapabilities.getMaxExposureCompensation();
            int min = mCameraCapabilities.getMinExposureCompensation();
            Log.i(TAG, "max:" + max + ",min:" + min + ",value:" + value);
            if (value >= min && value <= max) {
                mCameraSettings.setExposureCompensationIndex(value);
            } else {
                Log.i(TAG, "invalid exposure range: " + value);
            }
        } else {
            // If exposure compensation is not enabled, reset the exposure compensation value.
            setExposureCompensation(0);
        }
    }

    private void updateParametersSceneMode() {
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();

        /* SPRD:Fix bug 386140 @{ */
        if(isHdr()){
            mSceneMode = stringifier.sceneModeFromString(settingsManager.getString(
                    mAppController.getCameraScope(),
                    Keys.KEY_SCENE_MODE));
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SCENE_MODE);
        } else {
            String sceneMode = settingsManager.getString(settingsManager.SCOPE_GLOBAL, Keys.KEY_SCENE_MODE);
            mSceneMode = stringifier.sceneModeFromString(sceneMode);
        }
        /* @} */
        if (mCameraCapabilities.supports(mSceneMode)) {
            if (mCameraSettings.getCurrentSceneMode() != mSceneMode) {
                mCameraSettings.setSceneMode(mSceneMode);

                // Setting scene mode will change the settings of flash mode,
                // white balance, and focus mode. Here we read back the
                // parameters, so we can know those settings.
                mCameraDevice.applySettings(mCameraSettings);
                mCameraSettings = mCameraDevice.getSettings();
            }
        } else {
            mSceneMode = mCameraSettings.getCurrentSceneMode();
            if (mSceneMode == null) {
                mSceneMode = CameraCapabilities.SceneMode.AUTO;
            }
        }

        if (CameraCapabilities.SceneMode.AUTO == mSceneMode) {
            // Set flash mode.
            updateParametersFlashMode();

            // Set focus mode.
            mFocusManager.overrideFocusMode(null);
            mCameraSettings.setFocusMode(
                    mFocusManager.getFocusMode(mCameraSettings.getCurrentFocusMode()));
        } else {
            mFocusManager.overrideFocusMode(mCameraSettings.getCurrentFocusMode());
        }
    }

    private void updateParametersColorEffect() {
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();
        Log.d(TAG, "update ColorEffect = " + settingsManager.getString(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_COLOR_EFFECT));
        mColorEffect = stringifier.colorEffectFromString(settingsManager.getString(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_COLOR_EFFECT));
        mCameraSettings.setColorEffect(mColorEffect);

    }

    private void updateFace(){
        SettingsManager settingsManager = mActivity.getSettingsManager();
        // CID 109144 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // int face = mCameraCapabilities.getMaxNumOfFacesSupported();
        String mface = settingsManager.getString(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_AI_DATECT);
        Log.i(TAG, " face = " + mface + " hdrState = " + isHdr());
        if(!mface.equals(Keys.CAMERA_AI_DATECT_VAL_OFF) && isCameraIdle() && !isHdr()){
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_AI_DATECT, mface);
            startFaceDetection();
        }else if(mface.equals(Keys.CAMERA_AI_DATECT_VAL_OFF)){
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_AI_DATECT, mface);
            stopFaceDetection();
        }
    }

    protected void updateParametersBurstCount() {
        Log.i(TAG, "updateParametersBurstCount");
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();
        mButstNumber = stringifier.burstNumberFromString(settingsManager.getString(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_CONTINUE_CAPTURE));
        mCameraSettings.setBurstPicNum(mButstNumber);
    }

    protected void updateParametersFlashMode() {
        SettingsManager settingsManager = mActivity.getSettingsManager();

        CameraCapabilities.FlashMode flashMode = mCameraCapabilities.getStringifier()
            .flashModeFromString(settingsManager.getString(mAppController.getCameraScope(),
                                                           Keys.KEY_FLASH_MODE));
        if (mCameraCapabilities.supports(flashMode)) {
            mCameraSettings.setFlashMode(flashMode);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateAutoFocusMoveCallback() {
        if (mCameraDevice == null) {
            return;
        }
        if (mCameraSettings.getCurrentFocusMode() ==
                CameraCapabilities.FocusMode.CONTINUOUS_PICTURE) {
            mCameraDevice.setAutoFocusMoveCallback(mHandler,
                    (CameraAFMoveCallback) mAutoFocusMoveCallback);
        } else {
            mCameraDevice.setAutoFocusMoveCallback(null, null);
        }
    }

    /**
     * Sets the exposure compensation to the given value and also updates settings.
     *
     * @param value exposure compensation value to be set
     */
    public void setExposureCompensation(int value) {
        int max = mCameraCapabilities.getMaxExposureCompensation();
        int min = mCameraCapabilities.getMinExposureCompensation();
        Log.i(TAG, "exposure value: " + value + ",max:" + max + ",min:" + min);
        if (value >= min && value <= max) {
            mCameraSettings.setExposureCompensationIndex(value);
            SettingsManager settingsManager = mActivity.getSettingsManager();
            settingsManager.set(mAppController.getCameraScope(),
                                Keys.KEY_EXPOSURE, value);
        } else {
            Log.i(TAG, "invalid exposure range: " + value);
        }
    }

    // We separate the parameters into several subsets, so we can update only
    // the subsets actually need updating. The PREFERENCE set needs extra
    // locking because the preference can be changed from GLThread as well.
    protected void setCameraParameters(int updateSet) {
        if ((updateSet & UPDATE_PARAM_INITIALIZE) != 0) {
            updateCameraParametersInitialize();
        }

        if ((updateSet & UPDATE_PARAM_ZOOM) != 0) {
            updateCameraParametersZoom();
        }

        if ((updateSet & UPDATE_PARAM_PREFERENCE) != 0) {
            updateCameraParametersPreference();
        }

        if (mCameraDevice != null) {
            mCameraDevice.applySettings(mCameraSettings);
        }
    }

    // If the Camera is idle, update the parameters immediately, otherwise
    // accumulate them in mUpdateSet and update later.
    private void setCameraParametersWhenIdle(int additionalUpdateSet) {
        mUpdateSet |= additionalUpdateSet;
        if (mCameraDevice == null) {
            // We will update all the parameters when we open the device, so
            // we don't need to do anything now.
            mUpdateSet = 0;
            return;
        } else if (isCameraIdle()) {
            setCameraParameters(mUpdateSet);
            updateSceneMode();
            mUpdateSet = 0;
        } else {
            if (!mHandler.hasMessages(MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE)) {
                mHandler.sendEmptyMessageDelayed(MSG_SET_CAMERA_PARAMETERS_WHEN_IDLE, 1000);
            }
        }
    }

    @Override
    public boolean isCameraIdle() {
        return (mCameraState == IDLE) ||
                (mCameraState == PREVIEW_STOPPED) ||
                ((mFocusManager != null) && mFocusManager.isFocusCompleted()
                && (mCameraState != SWITCHING_CAMERA));
    }

    @Override
    public boolean isImageCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
        || CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action));
    }

    private void setupCaptureParams() {
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    private void initializeCapabilities() {
        mCameraCapabilities = mCameraDevice.getCapabilities();
        mFocusAreaSupported = mCameraCapabilities.supports(CameraCapabilities.Feature.FOCUS_AREA);
        mMeteringAreaSupported = mCameraCapabilities.supports(CameraCapabilities.Feature.METERING_AREA);
        mAeLockSupported = mCameraCapabilities.supports(CameraCapabilities.Feature.AUTO_EXPOSURE_LOCK);
        mAwbLockSupported = mCameraCapabilities.supports(CameraCapabilities.Feature.AUTO_WHITE_BALANCE_LOCK);
        mContinuousFocusSupported =
                mCameraCapabilities.supports(CameraCapabilities.FocusMode.CONTINUOUS_PICTURE);
        Log.i(TAG, "mFocusAreaSupported:" + mFocusAreaSupported + ",mMeteringAreaSupported:"
                + mMeteringAreaSupported + ",mAeLockSupported:" + mAeLockSupported
                + ",mAwbLockSupported:" + mAwbLockSupported + ",mContinuousFocusSupported:" + mContinuousFocusSupported);
    }

    @Override
    public void onZoomChanged(float ratio) {
        // Not useful to change zoom value when the activity is paused.
        if (mPaused) {
            return;
        }
        mZoomValue = ratio;
        if (mCameraSettings == null || mCameraDevice == null) {
            return;
        }
        // Set zoom parameters asynchronously
        mCameraSettings.setZoomRatio(mZoomValue);
        mCameraDevice.applySettings(mCameraSettings);
    }

    @Override
    public int getCameraState() {
        return mCameraState;
    }

    @Override
    public void onMemoryStateChanged(int state) {
        Log.i(TAG, "onMemoryStateChanged,(state == MemoryManager.STATE_OK)"+(state == MemoryManager.STATE_OK));
        if (mContinuousCaptureCount <= 0) {
            mAppController.setShutterEnabled(state == MemoryManager.STATE_OK);
        }
    }

    @Override
    public void onLowMemory() {
        // Not much we can do in the photo module.
    }

    // For debugging only.
    public void setDebugUri(Uri uri) {
        mDebugUri = uri;
    }

    // For debugging only.
    protected void saveToDebugUri(byte[] data) {
        if (mDebugUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = mContentResolver.openOutputStream(mDebugUri);
                outputStream.write(data);
                outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception while writing debug jpeg file", e);
            } finally {
                CameraUtil.closeSilently(outputStream);
            }
        }
    }

    @Override
    public void onRemoteShutterPress() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                focusAndCapture();
            }
        });
    }

    private void updateParametersWhiteBalance() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        CameraCapabilities.WhiteBalance  whiteBalance = mCameraCapabilities.getStringifier().whiteBalanceFromString(
                settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
                        Keys.KEY_WHITE_BALANCE));
        if (mCameraCapabilities.supports(whiteBalance)) {
            mCameraSettings.setWhiteBalance(whiteBalance);
        }
    }

    private void updateParametersContrast() {
        // CID 123739 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // SettingsManager settingsManager = mActivity.getSettingsManager();
//        CameraCapabilities.Contrast contrast= mCameraCapabilities.getStringifier().
//                contrastFromString(settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
//                Keys.KEY_CAMERA_CONTRAST));
//        if (mCameraCapabilities.supports(contrast)) {
//            mCameraSettings.setContrast(contrast);
//        }
    }

    private void updateParametersBrightness() {
        /**
         * CID 123738 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        CameraCapabilities.Stringifier stringifier = mCameraCapabilities.getStringifier();
        SettingsManager settingsManager = mActivity.getSettingsManager();
        */
//        mBrightness = stringifier.brightnessFromString(settingsManager.getString(
//                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_BRIGHTNESS));
//        mCameraSettings.setBrightNess(mBrightness);
//        mCameraDevice.applySettings(mCameraSettings);
    }

    private void updateParametersSaturation() {
        // CID 123742 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // SettingsManager settingsManager = mActivity.getSettingsManager();
//        CameraCapabilities.Saturation saturation = mCameraCapabilities.getStringifier().
//                saturationFromString(settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
//                Keys.KEY_CAMERA_SATURATION));
//        if(mCameraCapabilities.supports(saturation)) {
//            mCameraSettings.setSaturation(saturation);
//        }
   }
    private static boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    private int getContinuousCount() {
        mContinueTakePictureCount = 1;
        if (mButstNumber == null) {// SPRD BUG:389377
            return 1;
        }
        if ("ONE".equals(mButstNumber.toString())) {
            mContinueTakePictureCount = 1;
        } else if  ("THREE".equals(mButstNumber.toString())) {
            mContinueTakePictureCount = 3;
        } else if (("SIX".equals(mButstNumber.toString())) ) {
            mContinueTakePictureCount = 6;
        } else if ("TEN".equals(mButstNumber.toString())) {
            mContinueTakePictureCount = 10;
        } else {
            mContinueTakePictureCount = 1;
        }
        return mContinueTakePictureCount;
        }

    private boolean isBurstCapture() {
        if (getContinuousCount() > 1) {
            return true;
        }
        return false;
    }
    private void updateParametersISO() {
        // CID 123740 (#1 of 1): DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // SettingsManager settingsManager = mActivity.getSettingsManager();
//        CameraCapabilities.ISO iso = mCameraCapabilities.getStringifier().isoModeFromString(
//                settingsManager.getString(SettingsManager.SCOPE_GLOBAL,Keys.KEY_CAMERA_ISO));
//        if (mCameraCapabilities.supports(iso)) {
//            mCameraSettings.setISO(iso);
//        }
    }


    /* SPRD: porting new feature JPEG quality start @{ */
    public JpegQualityController mJpegQualityController;
    protected class JpegQualityController {
        private static final String TAG = "CameraJpegQualityController";
        private static final String VAL_NORMAL = "normal";
        private static final String VAL_HIGHT = "hight";
        private static final String VAL_SUPER = "super";
        private static final int VAL_DEFAULT_QUALITY = 85;
        // default construct
        /*package*/
        JpegQualityController() { }

        public int findJpegQuality(String quality) {
            android.util.Log.d("CAM_PhotoModule","findJpegQuality");
            int convertQuality = getConvertJpegQuality(quality);
            int result = VAL_DEFAULT_QUALITY;
            android.util.Log.d("CAM_PhotoModule","findJpegQuality convertQuality = " + convertQuality+" VAL_DEFAULT_QUALITY != convertQuality = "+(VAL_DEFAULT_QUALITY != convertQuality));
            if (VAL_DEFAULT_QUALITY != convertQuality) {
                result = CameraProfile.getJpegEncodingQualityParameter(convertQuality);
                android.util.Log.d("CAM_PhotoModule","findJpegQuality result = " + result);
            }
            android.util.Log.d("CAM_PhotoModule","findJpegQuality result = " + result);
            return result;
        }

        private int getConvertJpegQuality(String quality) {
            android.util.Log.d("CAM_PhotoModule","getConvertJpegQuality");
            int result = VAL_DEFAULT_QUALITY;
            if (quality != null) {
                if (VAL_NORMAL.equals(quality))
                    result = CameraProfile.QUALITY_LOW;
                else if (VAL_HIGHT.equals(quality))
                    result = CameraProfile.QUALITY_MEDIUM;
                else if (VAL_SUPER.equals(VAL_SUPER))
                    result = CameraProfile.QUALITY_HIGH;
            }
            android.util.Log.d("CAM_PhotoModule","getConvertJpegQuality result = " + result);
            return result;
        }
    }
    /* }@ dev new feature jpeg quality end */

    private void updateParametersMetering() {
        // CID 123741 : DLS: Dead local store (FB.DLS_DEAD_LOCAL_STORE)
        // SettingsManager settingsManager = mActivity.getSettingsManager();
//        CameraCapabilities.Metering metering = mCameraCapabilities.getStringifier().meteringFromString(
//                settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
//                        Keys.KEY_CAMER_METERING));
//        if (mCameraCapabilities.supports(metering)) {
//            mCameraSettings.setMetering(metering);
//        }
    }

    /*
    private void resetCameraSettings(SettingsManager settingsManager) {
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_PICTURE_SIZE_FRONT);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_PICTURE_SIZE_BACK);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_RECORD_LOCATION);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMER_ANTIBANDING);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SCENE_MODE);
        settingsManager.setToDefault(mAppController.getCameraScope(), Keys.KEY_FLASH_MODE);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_FOCUS_MODE);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_WHITE_BALANCE);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_JPEG_QUALITY);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_GRID_LINES);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_HDR_PLUS_FLASH_MODE);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_FREEZE_FRAME_DISPLAY);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_COLOR_EFFECT);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_CONTINUE_CAPTURE);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_ISO);
        Keys.setManualExposureCompensation(settingsManager, false);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_JPEG_QUALITY);
        Map<String, String> mStorage= StorageUtil.supportedRootDirectory();
        String external = mStorage.get(StorageUtil.KEY_DEFAULT_EXTERNAL);
        if (null == external) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_STORAGE_PATH, MultiStorage.KEY_DEFAULT_INTERNAL);
        }else{
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_STORAGE_PATH, MultiStorage.KEY_DEFAULT_EXTERNAL);
        }
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMER_METERING);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_CONTRAST);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_SATURATION);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_BRIGHTNESS);
        settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_AI_DATECT);
    }
*/
    private boolean isHdr() {
//        SettingsManager settingsManager = mActivity.getSettingsManager();
//        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR);
        return Keys.isHdrOn(mActivity.getSettingsManager());
    }

    private void faceDatectMutex() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String mface = settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_CAMERA_AI_DATECT);
        if (isHdr() && !mface.equals(Keys.CAMERA_AI_DATECT_VAL_OFF)) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_HDR, false);
        }
        int index = settingsManager.getIndexOfCurrentValue(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_COUNTDOWN_DURATION);
        if (index > 0 && !mface.equals(Keys.CAMERA_AI_DATECT_VAL_OFF)) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_COUNTDOWN_DURATION, 0);
        }
    }

    private void closeFace() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        String mFace = settingsManager.getString(SettingsManager.SCOPE_GLOBAL,
                Keys.KEY_CAMERA_AI_DATECT);
        if (!mFace.equals(Keys.CAMERA_AI_DATECT_VAL_OFF)) {
            settingsManager.set(SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_AI_DATECT, Keys.CAMERA_AI_DATECT_VAL_OFF);
            stopFaceDetection();
        }
    }

    public boolean isShutterEnabled() {
        return mAppController.isShutterEnabled();
    }

    // SPRD:Add for ai detect
    public void setCaptureCount(int count) {
    }
    /* @} */

    public void onHideBurstScreenHint() {
        mUI.enableBurstScreenHint(false);
//        if (mAppController.getCameraAppUI().isInFreezeReview()) {// SPRD :BUG 398284
//            mAppController.getCameraAppUI().setSwipeEnabled(false);
//            mAppController.getCameraAppUI().onShutterButtonClick();
//        } else {
            mAppController.getCameraAppUI().setSwipeEnabled(true);
            mAppController.getCameraAppUI().showModeOptions();
//        }
    }

    public int getContinuousCaptureCount(){
        return mContinuousCaptureCount;
    }

    /* SPRD: BUG 397228 stop or start face detection start @{ */
    @Override
    public void onPreviewVisibilityChanged(int visibility) {
    }
    /* }@ BUG 397228 end */

    /* SPRD:Fix bug 401772 @{ */
    public boolean isContinuousFocusSupported() {
        return mContinuousFocusSupported;
    }
    /* @} */

    protected void setScreenOrientation() {
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    protected void setScreenOrientationAuto() {
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    public void initCameraID() {};
    public void setPhotoCameraID() {};
    protected int getFrontCameraId() {
        if (getCameraProvider() != null) {
            return getCameraProvider().getFirstFrontCameraId();
        }
        return -1;
    }

    protected byte[] mPreviewCallBackBuffer = null;
    protected final BufferedPreviewCallback mBufferedPreviewCallback = new BufferedPreviewCallback();

    private final class BufferedPreviewCallback implements CameraAgent.CameraPreviewDataCallback {
        public void onPreviewFrame(byte[] data, CameraProxy camera) {
            onPreviewCallback(data);
        }
    }

    protected void onPreviewCallback(byte[] data) {
    }

    protected void addPreviewBuffer() {
        if (mCameraDevice != null) {
            mCameraDevice.addCallbackBuffer(mPreviewCallBackBuffer);
        }
    }

    protected void newDataBuffer() {
        ResolutionSize previewSize = PreviewSize.instance(mCameraId).get(mParameters);

        int previewPixels = 800 * 480; // max size of 800*480 screen
        if (previewSize != null) {
            previewPixels = previewSize.width * previewSize.height;
        }

        calcYuvSize();
        int bufferSize = previewPixels * mBitPerPixels / 8;
        if (mPreviewCallBackBuffer == null
                || mPreviewCallBackBuffer.length != bufferSize) {
            mPreviewCallBackBuffer = new byte[bufferSize];
        }
        Log.i(TAG, "new callback Buffer " + bufferSize + " bytes.");
    }

    protected void setPreviewFrameLayoutAspectRatio() {};
    protected void setModeOptionsLayout() {}

    protected boolean checkStorage(){
        if (mActivity == null) {
            return false;
        }
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG,"Not enough space or storage not ready.");
            return false;
        }
        return true;
    }

    protected void calcYuvSize() {
        Parameters parameters = mParameters;
        if (parameters == null) {
            return;
        }
        mPreviewFormat = parameters.getPreviewFormat();
        mBitPerPixels = ImageFormat.getBitsPerPixel(mPreviewFormat);
        /*
         * FIX BUG: 3557 3556 3555 3551 3550
         * FIX COMMENT: generally SP7710GA's preview size is 640x480, so the cache size should be 640x480x12/8 = 460800,
         *              but framework need the cache size must no less than 500000,
         *              to balance this, set the BitPerPixel to 14( larger than 12), so 640x480x14/8=537600.
         * Date: 2013-04-14
         */
        Log.i(TAG, "CameraActivity.calcYuvSize(): mPreviewFormat = " + mPreviewFormat
                + ", mBitPerPixels = " + mBitPerPixels);
        if (mBitPerPixels < 0) {
            mBitPerPixels = 12; // set the default to 12
            mPreviewFormat = ImageFormat.NV21;
        }
    }

    /* SPRD: Add for bug 461734 @{ */
    protected FreezeFrameDisplayControl sFreezeFrameControl;

    @Override
    public void proxyStartPreview() {
        // TODO Auto-generated method stub
        startPreview();
    }

    @Override
    public void proxySetCameraState(int state) {
        // TODO Auto-generated method stub
        setCameraState(state);
    }

    @Override
    public void proxystartFaceDetection() {
        // TODO Auto-generated method stub
        startFaceDetection();
    }

    @Override
    public void proxyCaptureCancelled() {
        // TODO Auto-generated method stub
        onCaptureCancelled();
    }

    @Override
    public void proxyCaptureDone() {
        // TODO Auto-generated method stub
        onCaptureDone();
    }

    @Override
    public boolean proxyIsContinueTakePicture() {
        // TODO Auto-generated method stub
        return false;
    }
    @Override
    public boolean proxyIsPauseStatus() {
        // TODO Auto-generated method stub
        return mPaused;
    }

    @Override
    public void proxySetImageData() {
        // TODO Auto-generated method stub
        mJpegImageData = null;
    }

    public boolean isFreezeFrameDisplay() {
        if (sFreezeFrameControl != null) {
            return sFreezeFrameControl.proxyDisplay();
        }
        return false ;
    }

    /* SPRD: add for filter function @{ */
    public boolean captureMagic() {
        //ignore
        return false;
    }
    public void updateFocusUI() {}
    public void setFilteVisibility() {}
    public void setPreviewDataCallback() {}
    public void setFilterPreviewDataCallback() {}
    public void captureComplete(Bitmap bmp, int picW, int picH) {}
    public void freezeScreen() {}
    /*  @} */
    /*SPRD: fix bug 474672 add for ucam beauty @{ */
    @Override
    public void onBeautyValueChanged(int value) {}

    //SPRD:fix bug534270 shuttersound is still ring when it is off
    private void updateCameraShutterSound() {
        SettingsManager settingsManager = mActivity.getSettingsManager();
        boolean isShutterOn = settingsManager.getBoolean(
                SettingsManager.SCOPE_GLOBAL, Keys.KEY_CAMERA_SHUTTER_SOUND,
                true);
        mShutterSound.setupShutterSound(!isShutterOn);
    }
}
