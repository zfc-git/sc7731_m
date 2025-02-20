/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ex.camera2.portability;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;
import android.hardware.Camera;
import com.android.ex.camera2.portability.CameraAgent.CameraFaceDetectionCallback;
import com.android.ex.camera2.portability.CameraAgent.CameraProxy;

import com.android.ex.camera2.portability.debug.Log;
import com.android.ex.camera2.utils.Camera2RequestSettingsSet;
//import com.android.ex.camera2.utils.Key;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class to implement {@link CameraAgent} of the Android camera2 framework.
 */
class AndroidCamera2AgentImpl extends CameraAgent {
    private static final Log.Tag TAG = new Log.Tag("AndCam2AgntImp");

    private final Camera2Handler mCameraHandler;
    private final HandlerThread mCameraHandlerThread;
    private final CameraStateHolder mCameraState;
    private final DispatchThread mDispatchThread;
    private final CameraManager mCameraManager;
    private final MediaActionSound mNoisemaker;
    /* SPRD: fix bug542668 NullPointerException
    private CameraExceptionHandler mExceptionHandler;
     * @{ */
    private CameraExceptionHandler mExceptionHandler = sDefaultExceptionHandler;
    /* @} */
    private Camera2RequestSettingsSet mPersistentSettings;//SPRD:fix bug 473462 add burst capture
    private int mCanceledCaptureCount;//SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics saveing is wrong

    /**
     * Number of camera devices.  The length of {@code mCameraDevices} does not reveal this
     * information because that list may contain since-invalidated indices.
     */
    private int mNumCameraDevices;

    /**
     * Transformation between integral camera indices and the {@link java.lang.String} indices used
     * by the underlying API.  Note that devices may disappear because they've been disconnected or
     * have otherwise gone offline.  Because we need to keep the meanings of whatever indices we
     * expose stable, we cannot simply remove them in such a case; instead, we insert {@code null}s
     * to invalidate any such indices.  Whenever new devices appear, they are appended to the end of
     * the list, and thereby assigned the lowest index that has never yet been used.
     */
    private final List<String> mCameraDevices;

    /* SPRD: fix bug542668 NullPointerException @{ */
    private static final CameraExceptionHandler sDefaultExceptionHandler =
            new CameraExceptionHandler(null) {
        @Override
        public void onCameraError(int errorCode) {
            Log.w(TAG, "onCameraError called with no handler set: " + errorCode);
        }

        @Override
        public void onCameraException(RuntimeException ex, String commandHistory, int action,
                int state) {
            Log.w(TAG, "onCameraException called with no handler set", ex);
        }

        @Override
        public void onDispatchThreadException(RuntimeException ex) {
            Log.w(TAG, "onDispatchThreadException called with no handler set", ex);
        }
    };
    /* @} */

    AndroidCamera2AgentImpl(Context context) {
        mCameraHandlerThread = new HandlerThread("Camera2 Handler Thread");
        mCameraHandlerThread.start();
        mCameraHandler = new Camera2Handler(mCameraHandlerThread.getLooper());
        mExceptionHandler = new CameraExceptionHandler(mCameraHandler);
        mCameraState = new AndroidCamera2StateHolder();
        mDispatchThread = new DispatchThread(mCameraHandler, mCameraHandlerThread);
        mDispatchThread.start();
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mNoisemaker = new MediaActionSound();
        mNoisemaker.load(MediaActionSound.SHUTTER_CLICK);

        mNumCameraDevices = 0;
        mCameraDevices = new ArrayList<String>();
        updateCameraDevices();
    }

    /**
     * Updates the camera device index assignments stored in {@link mCameraDevices}, without
     * reappropriating any currently-assigned index.
     * @return Whether the operation was successful
     */
    private boolean updateCameraDevices() {
        try {
            String[] currentCameraDevices = mCameraManager.getCameraIdList();
            Set<String> currentSet = new HashSet<String>(Arrays.asList(currentCameraDevices));

            // Invalidate the indices assigned to any camera devices that are no longer present
            for (int index = 0; index < mCameraDevices.size(); ++index) {
                if (!currentSet.contains(mCameraDevices.get(index))) {
                    mCameraDevices.set(index, null);
                    --mNumCameraDevices;
                }
            }

            // Assign fresh indices to any new camera devices
            currentSet.removeAll(mCameraDevices); // The devices we didn't know about
            for (String device : currentCameraDevices) {
                if (currentSet.contains(device)) {
                    mCameraDevices.add(device);
                    ++mNumCameraDevices;
                }
            }

            return true;
        } catch (CameraAccessException ex) {
            Log.e(TAG, "Could not get device listing from camera subsystem", ex);
            return false;
        }
    }

    // TODO: Implement
    @Override
    /**
     * SPRD: fix bug 402668/400619 @{
    public void recycle() {}
     */
    public void recycle() {
        if(mNoisemaker != null){
            mNoisemaker.release();
            Log.i(TAG,"mNoisemaker release!");
        }
        closeCamera(null, true);
        mDispatchThread.end();
    }
    /**
     * @}
     */

    // TODO: Some indices may now be invalid; ensure everyone can handle that and update the docs
    @Override
    public CameraDeviceInfo getCameraDeviceInfo() {
        updateCameraDevices();
        return new AndroidCamera2DeviceInfo(mCameraManager, mCameraDevices.toArray(new String[0]),
                mNumCameraDevices);
    }

    @Override
    protected Handler getCameraHandler() {
        return mCameraHandler;
    }

    @Override
    protected DispatchThread getDispatchThread() {
        return mDispatchThread;
    }

    @Override
    protected CameraStateHolder getCameraState() {
        return mCameraState;
    }

    @Override
    protected CameraExceptionHandler getCameraExceptionHandler() {
        return mExceptionHandler;
    }

    @Override
    public void setCameraExceptionHandler(CameraExceptionHandler exceptionHandler) {
        /* SPRD: fix bug542668 NullPointerException
        mExceptionHandler = exceptionHandler;
         * @{ */
        mExceptionHandler = exceptionHandler != null ? exceptionHandler : sDefaultExceptionHandler;
        /* @} */
    }

    private static abstract class CaptureAvailableListener
            extends CameraCaptureSession.CaptureCallback
            implements ImageReader.OnImageAvailableListener {};

    private class Camera2Handler extends HistoryHandler {
        // Caller-provided when leaving CAMERA_UNOPENED state:
        private CameraOpenCallback mOpenCallback;
        private int mCameraIndex;
        private String mCameraId;
        private int mCancelAfPending = 0;

        // Available in CAMERA_UNCONFIGURED state and above:
        private CameraDevice mCamera;
        private AndroidCamera2ProxyImpl mCameraProxy;
        /**
         * SPRD: fix bug 473462 add burst capture @{
        private Camera2RequestSettingsSet mPersistentSettings;
        * @}
        */
        private Rect mActiveArray;
        private boolean mLegacyDevice;

        // Available in CAMERA_CONFIGURED state and above:
        private Size mPreviewSize;
        private Size mPhotoSize;

        // Available in PREVIEW_READY state and above:
        private SurfaceTexture mPreviewTexture;
        private Surface mPreviewSurface;
        private CameraCaptureSession mSession;
        private ImageReader mCaptureReader;
        private FaceDetectionCallbackForward mFaceDetectionCallback;//SPRD:Add for ai detect

        // Available from the beginning of PREVIEW_ACTIVE until the first preview frame arrives:
        private CameraStartPreviewCallback mOneshotPreviewingCallback;

        // Available in FOCUS_LOCKED between AF trigger receipt and whenever the lens stops moving:
        private CameraAFCallback mOneshotAfCallback;

        // Available when taking picture between AE trigger receipt and autoexposure convergence
        private CaptureAvailableListener mOneshotCaptureCallback;

        // Available whenever setAutoFocusMoveCallback() was last invoked with a non-null argument:
        private CameraAFMoveCallback mPassiveAfCallback;

        // Gets reset on every state change
        private int mCurrentAeState = CaptureResult.CONTROL_AE_STATE_INACTIVE;

        Camera2Handler(Looper looper) {
            super(looper);
        }

        /* SPRD:Add for ai detect @{ */
        private void setFaceDetectionListener(FaceDetectionCallbackForward listener) {
            mFaceDetectionCallback = listener;
        }
        /* @} */

        @Override
        public void handleMessage(final Message msg) {
            super.handleMessage(msg);
            /**
             * SPRD: fix bug 473462 add burst capture @{
            Log.v(TAG, "handleMessage - action = '" + CameraActions.stringify(msg.what) + "'");
            */
            Log.i(TAG, "AppFW handleMessage - action = '" + CameraActions.stringify(msg.what) + "'");
            /**
             * @}
             */
            int cameraAction = msg.what;
            try {
                switch (cameraAction) {
                    case CameraActions.OPEN_CAMERA:
                    case CameraActions.RECONNECT: {
                        CameraOpenCallback openCallback = (CameraOpenCallback) msg.obj;
                        int cameraIndex = msg.arg1;

                        if (mCameraState.getState() > AndroidCamera2StateHolder.CAMERA_UNOPENED) {
                            openCallback.onDeviceOpenedAlready(cameraIndex,
                                    generateHistoryString(cameraIndex));
                            break;
                        }

                        mOpenCallback = openCallback;
                        mCameraIndex = cameraIndex;
                        mCameraId = mCameraDevices.get(mCameraIndex);
                        Log.i(TAG, String.format("Opening camera index %d (id %s) with camera2 API",
                                cameraIndex, mCameraId));

                        if (mCameraId == null) {
                            mOpenCallback.onCameraDisabled(msg.arg1);
                            break;
                        }
                        mCameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, this);

                        break;
                    }

                    case CameraActions.RELEASE: {
                        if (mCameraState.getState() == AndroidCamera2StateHolder.CAMERA_UNOPENED) {
                            Log.w(TAG, "Ignoring release at inappropriate time");
                            break;
                        }

                        Log.i(TAG, "CameraActions.RELEASE,Session = " + mSession + ";CameraDevice = " + mCamera + ";PreviewSurface = "
                                + mPreviewSurface + ";CaptureReader = " + mCaptureReader);
                        if (mSession != null) {
                            Log.i(TAG, "closePreviewSession");
                            closePreviewSession();
                            mSession = null;
                        }
                        if (mCamera != null) {
                            Log.i(TAG, "CameraDevice.close");
                            mCamera.close();
                            mCamera = null;
                        }
                        mCameraProxy = null;
                        mPersistentSettings = null;
                        mActiveArray = null;
                        if (mPreviewSurface != null) {
                            Log.i(TAG, "PreviewSurface.release");
                            mPreviewSurface.release();
                            mPreviewSurface = null;
                        }
                        mPreviewTexture = null;
                        if (mCaptureReader != null) {
                            Log.i(TAG, "CaptureReader.close");
                            mCaptureReader.close();
                            mCaptureReader = null;
                        }
                        mPreviewSize = null;
                        mPhotoSize = null;
                        mCameraIndex = 0;
                        mCameraId = null;
                        changeState(AndroidCamera2StateHolder.CAMERA_UNOPENED);
                        break;
                    }

                    /*case CameraActions.UNLOCK: {
                        break;
                    }

                    case CameraActions.LOCK: {
                        break;
                    }*/

                    case CameraActions.SET_PREVIEW_TEXTURE_ASYNC: {
                        setPreviewTexture((SurfaceTexture) msg.obj);
                        break;
                    }

                    case CameraActions.START_PREVIEW_ASYNC: {
                        if (mCameraState.getState() !=
                                        AndroidCamera2StateHolder.CAMERA_PREVIEW_READY) {
                            // TODO: Provide better feedback here?
                            Log.w(TAG, "Refusing to start preview at inappropriate time");
                            break;
                        }

                        mOneshotPreviewingCallback = (CameraStartPreviewCallback) msg.obj;
                        changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE);
                        try {
                            mSession.setRepeatingRequest(
                                    mPersistentSettings.createRequest(mCamera,
                                            CameraDevice.TEMPLATE_PREVIEW, mPreviewSurface),
                                    /*listener*/mCameraResultStateCallback, /*handler*/this);
                        } catch(CameraAccessException ex) {
                            Log.w(TAG, "Unable to start preview", ex);
                            changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_READY);
                        }
                        break;
                    }

                    // FIXME: We need to tear down the CameraCaptureSession here
                    // (and unlock the CameraSettings object from our
                    // CameraProxy) so that the preview/photo sizes can be
                    // changed again while no preview is running.
                    case CameraActions.STOP_PREVIEW: {
                        if (mCameraState.getState() <
                                        AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.w(TAG, "Refusing to stop preview at inappropriate time");
                            break;
                        }

                        mSession.stopRepeating();
                        changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_READY);
                        break;
                    }

                    /*case CameraActions.SET_PREVIEW_CALLBACK_WITH_BUFFER: {
                        break;
                    }

                    case CameraActions.ADD_CALLBACK_BUFFER: {
                        break;
                    }

                    case CameraActions.SET_PREVIEW_DISPLAY_ASYNC: {
                        break;
                    }

                    case CameraActions.SET_PREVIEW_CALLBACK: {
                        break;
                    }

                    case CameraActions.SET_ONE_SHOT_PREVIEW_CALLBACK: {
                        break;
                    }

                    case CameraActions.SET_PARAMETERS: {
                        break;
                    }

                    case CameraActions.GET_PARAMETERS: {
                        break;
                    }

                    case CameraActions.REFRESH_PARAMETERS: {
                        break;
                    }*/

                    case CameraActions.APPLY_SETTINGS: {
                        AndroidCamera2Settings settings = (AndroidCamera2Settings) msg.obj;
                        applyToRequest(settings);
                        break;
                    }

                    case CameraActions.AUTO_FOCUS: {
                        if (mCancelAfPending > 0) {
                            Log.v(TAG, "handleMessage - Ignored AUTO_FOCUS because there was "
                                    + mCancelAfPending + " pending CANCEL_AUTO_FOCUS messages");
                            break; // ignore AF because a CANCEL_AF is queued after this
                        }
                        // We only support locking the focus while a preview is being displayed.
                        // However, it can be requested multiple times in succession; the effect of
                        // the subsequent invocations is determined by the focus mode defined in the
                        // provided CameraSettings object. In passive (CONTINUOUS_*) mode, the
                        // duplicate requests are no-ops and leave the lens locked at its current
                        // position, but in active (AUTO) mode, they perform another scan and lock
                        // once that is finished. In any manual focus mode, this call is a no-op,
                        // and most notably, this is the only case where the callback isn't invoked.
                        if (mCameraState.getState() <
                                        AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.w(TAG, "Ignoring attempt to autofocus without preview");
                            break;
                        }

                        // The earliest we can reliably tell whether the autofocus has locked in
                        // response to our latest request is when our one-time capture progresses.
                        // However, it will probably take longer than that, so once that happens,
                        // just start checking the repeating preview requests as they complete.
                        final CameraAFCallback callback = (CameraAFCallback) msg.obj;
                        CameraCaptureSession.CaptureCallback deferredCallbackSetter =
                                new CameraCaptureSession.CaptureCallback() {
                            private boolean mAlreadyDispatched = false;

                            @Override
                            public void onCaptureProgressed(CameraCaptureSession session,
                                                            CaptureRequest request,
                                                            CaptureResult result) {
                                checkAfState(result);
                            }

                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session,
                                                           CaptureRequest request,
                                                           TotalCaptureResult result) {
                                checkAfState(result);
                            }

                            private void checkAfState(CaptureResult result) {
                                if (result.get(CaptureResult.CONTROL_AF_STATE) != null &&
                                        !mAlreadyDispatched) {
                                    // Now our mCameraResultStateCallback will invoke the callback
                                    // the first time it finds the focus motor to be locked.
                                    mAlreadyDispatched = true;
                                    mOneshotAfCallback = callback;
                                    // This is an optimization: check the AF state of this frame
                                    // instead of simply waiting for the next.
                                    mCameraResultStateCallback.monitorControlStates(result);
                                }
                            }

                            @Override
                            public void onCaptureFailed(CameraCaptureSession session,
                                                        CaptureRequest request,
                                                        CaptureFailure failure) {
                                Log.e(TAG, "Focusing failed with reason " + failure.getReason());
                                callback.onAutoFocus(false, mCameraProxy);
                            }};

                        // Send a one-time capture to trigger the camera driver to lock focus.
                        changeState(AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED);
                        Camera2RequestSettingsSet trigger =
                                new Camera2RequestSettingsSet(mPersistentSettings);
                        trigger.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        try {
                            mSession.capture(
                                    trigger.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW,
                                            mPreviewSurface),
                                    /*listener*/deferredCallbackSetter, /*handler*/ this);
                        } catch(CameraAccessException ex) {
                            Log.e(TAG, "Unable to lock autofocus", ex);
                            changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE);
                        }
                        break;
                    }

                    case CameraActions.CANCEL_AUTO_FOCUS: {
                        // Ignore all AFs that were already queued until we see
                        // a CANCEL_AUTO_FOCUS_FINISH
                        mCancelAfPending++;
                        // Why would you want to unlock the lens if it isn't already locked?
                        if (mCameraState.getState() <
                                AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.w(TAG, "Ignoring attempt to release focus lock without preview");
                            break;
                        }

                        // Send a one-time capture to trigger the camera driver to resume scanning.
                        changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE);
                        Camera2RequestSettingsSet cancel =
                                new Camera2RequestSettingsSet(mPersistentSettings);
                        cancel.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                        try {
                            mSession.capture(
                                    cancel.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW,
                                            mPreviewSurface),
                                    /*listener*/null, /*handler*/this);
                        } catch(CameraAccessException ex) {
                            Log.e(TAG, "Unable to cancel autofocus", ex);
                            changeState(AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED);
                        }
                        break;
                    }

                    case CameraActions.CANCEL_AUTO_FOCUS_FINISH: {
                        // Stop ignoring AUTO_FOCUS messages unless there are additional
                        // CANCEL_AUTO_FOCUSes that were added
                        mCancelAfPending--;
                        break;
                    }

                    case CameraActions.SET_AUTO_FOCUS_MOVE_CALLBACK: {
                        mPassiveAfCallback = (CameraAFMoveCallback) msg.obj;
                        break;
                    }

                    /*SPRD:Modify for ai detect @{ */
                    /*case CameraActions.SET_ZOOM_CHANGE_LISTENER: {
                        break;
                    }
                    */

                    case CameraActions.SET_FACE_DETECTION_LISTENER: {
                        setFaceDetectionListener((FaceDetectionCallbackForward) msg.obj);
                        break;
                    }

                    case CameraActions.START_FACE_DETECTION: {
                        boolean isFace = mPersistentSettings.set(
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
                        break;
                    }

                    case CameraActions.STOP_FACE_DETECTION: {
                        boolean isFace = mPersistentSettings.set(
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
                        break;
                    }

                    /*
                    case CameraActions.SET_ERROR_CALLBACK: {
                        break;
                    }

                    case CameraActions.ENABLE_SHUTTER_SOUND: {
                        break;
                    }*/
                    /* @} */

                    case CameraActions.SET_DISPLAY_ORIENTATION: {
                        // Only set the JPEG capture orientation if requested to do so; otherwise,
                        // capture in the sensor's physical orientation. (e.g., JPEG rotation is
                        // necessary in auto-rotate mode.
                        mPersistentSettings.set(CaptureRequest.JPEG_ORIENTATION, msg.arg2 > 0 ?
                                mCameraProxy.getCharacteristics().getJpegOrientation(msg.arg1) : 0);
                        break;
                    }

                    case CameraActions.SET_JPEG_ORIENTATION: {
                        mPersistentSettings.set(CaptureRequest.JPEG_ORIENTATION, msg.arg1);
                        break;
                    }

                    case CameraActions.CAPTURE_PHOTO: {
                        if (mCameraState.getState() <
                                        AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                            Log.e(TAG, "Photos may only be taken when a preview is active");
                            break;
                        }
                        if (mCameraState.getState() !=
                                AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED) {
                            Log.w(TAG, "Taking a (likely blurry) photo without the lens locked");
                        }

                        final CaptureAvailableListener listener =
                                (CaptureAvailableListener) msg.obj;
                        if (mLegacyDevice ||
                                (mCurrentAeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                                !mPersistentSettings.matches(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH) &&
                                !mPersistentSettings.matches(CaptureRequest.FLASH_MODE,
                                        CaptureRequest.FLASH_MODE_SINGLE)))
                                {
                            Log.i(TAG,"AppFw:   CameraActions.CAPTURE_PHOTO, mLegacyDevice = " + mLegacyDevice);
                            // Legacy devices don't support the precapture state keys and instead
                            // perform autoexposure convergence automatically upon capture.

                            // On other devices, as long as it has already converged, it determined
                            // that flash was not required, and we're not going to invalidate the
                            // current exposure levels by forcing the force on, we can save
                            // significant capture time by not forcing a recalculation.
                            Log.i(TAG, "Skipping pre-capture autoexposure convergence");
                            mCaptureReader.setOnImageAvailableListener(listener, /*handler*/this);
                            try {
                                mSession.capture(
                                        mPersistentSettings.createRequest(mCamera,
                                                CameraDevice.TEMPLATE_STILL_CAPTURE,
                                                mCaptureReader.getSurface()),
                                        listener, /*handler*/this);
                            } catch (CameraAccessException ex) {
                                Log.e(TAG, "Unable to initiate immediate capture", ex);
                            }
                        } else {
                            // We need to let AE converge before capturing. Once our one-time
                            // trigger capture has made it into the pipeline, we'll start checking
                            // for the completion of that convergence, capturing when that happens.
                            Log.i(TAG, "Forcing pre-capture autoexposure convergence");
                            CameraCaptureSession.CaptureCallback deferredCallbackSetter =
                                    new CameraCaptureSession.CaptureCallback() {
                                private boolean mAlreadyDispatched = false;

                                @Override
                                public void onCaptureProgressed(CameraCaptureSession session,
                                                                CaptureRequest request,
                                                                CaptureResult result) {
                                    checkAeState(result);
                                }

                                @Override
                                public void onCaptureCompleted(CameraCaptureSession session,
                                                               CaptureRequest request,
                                                               TotalCaptureResult result) {
                                    checkAeState(result);
                                }

                                private void checkAeState(CaptureResult result) {
                                    if (result.get(CaptureResult.CONTROL_AE_STATE) != null &&
                                            !mAlreadyDispatched) {
                                        // Now our mCameraResultStateCallback will invoke the
                                        // callback once the autoexposure routine has converged.
                                        mAlreadyDispatched = true;
                                        mOneshotCaptureCallback = listener;
                                        // This is an optimization: check the AE state of this frame
                                        // instead of simply waiting for the next.
                                        mCameraResultStateCallback.monitorControlStates(result);
                                    }
                                }

                                @Override
                                public void onCaptureFailed(CameraCaptureSession session,
                                                            CaptureRequest request,
                                                            CaptureFailure failure) {
                                    Log.e(TAG, "Autoexposure and capture failed with reason " +
                                            failure.getReason());
                                    // TODO: Make an error callback?
                                }};

                            // Set a one-time capture to trigger the camera driver's autoexposure:
                            Camera2RequestSettingsSet expose =
                                    new Camera2RequestSettingsSet(mPersistentSettings);
                            expose.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                            try {
                                mSession.capture(
                                        expose.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW,
                                                mPreviewSurface),
                                        /*listener*/deferredCallbackSetter, /*handler*/this);
                            } catch (CameraAccessException ex) {
                                Log.e(TAG, "Unable to run autoexposure and perform capture", ex);
                            }
                        }
                        break;
                    }

                    /* SPRD: fix bug 473462 add burst capture @{ */
                    case CameraActions.CAPTURE_BURST_PHOTO: {
                        int num = mPersistentSettings.get(CaptureRequest.SPRD_CAPTURE_MODE);
                        Log.i(TAG,"CameraActions.CAPTURE_BURST_PHOTO num="+num);
                        List<CaptureRequest> requests = new ArrayList<CaptureRequest>();
                        final CaptureAvailableListener listener = (CaptureAvailableListener) msg.obj;
                        mCaptureReader.setOnImageAvailableListener(listener, /*handler*/this);

                        for (int i = 0; i < num; i++) {
                            Log.i(TAG,"CameraActions.CAPTURE_BURST_PHOTO i="+i);
                            CaptureRequest request = mPersistentSettings.createRequest(
                                    mCamera, CameraDevice.TEMPLATE_STILL_CAPTURE,
                                    mPreviewSurface,mCaptureReader.getSurface());
                            requests.add(request);
                        }

                        Log.i(TAG,"requests size:" + requests.size());
                        try {
                            Log.i(TAG,"mSession.captureBurs");
                            mSession.captureBurst(requests, listener, /*mHandler*/this);
                        } catch (CameraAccessException ex) {
                            Log.i(TAG, "Unable to run CAPTURE_BURST_PHOTO and perform capture", ex);
                        }

                        break;
                    }

                    case CameraActions.CANCEL_CAPTURE_BURST_PHOTO: {
                        try {
                            Log.i(TAG,"CameraActions.CANCEL_CAPTURE_BURST_PHOTO");
                            //mSession.abortCaptures();
                            /*
                             * SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics
                             * saveing is wrong @{
                             */
                            CancelBurstCaptureCallback cb = (CancelBurstCaptureCallback) msg.obj;
                            mCanceledCaptureCount = mSession.cancelPicture();
                            cb.onCanceled(mCanceledCaptureCount);
                            /* @} */
                        } catch (CameraAccessException ex) {
                            Log.i(TAG, "Unable to run CANCEL_CAPTURE_BURST_PHOTO and perform capture", ex);
                        }

                        break;
                    }
                    /* @} */

                    default: {
                        // TODO: Rephrase once everything has been implemented
                        throw new RuntimeException("Unimplemented CameraProxy message=" + msg.what);
                    }
                }
            } catch (final Exception ex) {
                if (cameraAction != CameraActions.RELEASE && mCamera != null) {
                    // TODO: Handle this better
                    mCamera.close();
                    mCamera = null;
                } else if (mCamera == null) {
                    if (cameraAction == CameraActions.OPEN_CAMERA) {
                        if (mOpenCallback != null) {
                            mOpenCallback.onDeviceOpenFailure(mCameraIndex,
                                    generateHistoryString(mCameraIndex));
                        }
                    } else {
                        Log.w(TAG, "Cannot handle message " + msg.what + ", mCamera is null");
                    }
                    return;
                }

                if (ex instanceof RuntimeException) {
                    String commandHistory = generateHistoryString(Integer.parseInt(mCameraId));
                    mExceptionHandler.onCameraException((RuntimeException) ex, commandHistory,
                            cameraAction, mCameraState.getState());
                }
            } finally {
                WaitDoneBundle.unblockSyncWaiters(msg);
            }
        }

        public CameraSettings buildSettings(AndroidCamera2Capabilities caps) {
            try {
                return new AndroidCamera2Settings(mCamera, CameraDevice.TEMPLATE_PREVIEW,
                        mActiveArray, mPreviewSize, mPhotoSize);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Unable to query camera device to build settings representation");
                return null;
            }
        }

        /**
         * Simply propagates settings from provided {@link CameraSettings}
         * object to our {@link CaptureRequest.Builder} for use in captures.
         * <p>Most conversions to match the API 2 formats are performed by
         * {@link AndroidCamera2Capabilities.IntegralStringifier}; otherwise
         * any final adjustments are done here before updating the builder.</p>
         *
         * @param settings The new/updated settings
         */
        private void applyToRequest(AndroidCamera2Settings settings) {
            // TODO: If invoked when in PREVIEW_READY state, a new preview size will not take effect

            mPersistentSettings.union(settings.getRequestSettings());
            mPreviewSize = settings.getCurrentPreviewSize();
            mPhotoSize = settings.getCurrentPhotoSize();

            if (mCameraState.getState() >= AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                // If we're already previewing, reflect most settings immediately
                try {
                    mSession.setRepeatingRequest(
                            mPersistentSettings.createRequest(mCamera,
                                    CameraDevice.TEMPLATE_PREVIEW, mPreviewSurface),
                            /*listener*/mCameraResultStateCallback, /*handler*/this);
                } catch (CameraAccessException ex) {
                    Log.e(TAG, "Failed to apply updated request settings", ex);
                }
            } else if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_PREVIEW_READY) {
                // If we're already ready to preview, this doesn't regress our state
                changeState(AndroidCamera2StateHolder.CAMERA_CONFIGURED);
            }
        }

        private void setPreviewTexture(SurfaceTexture surfaceTexture) {
            // TODO: Must be called after providing a .*Settings populated with sizes
            // TODO: We don't technically offer a selection of sizes tailored to SurfaceTextures!

            // TODO: Handle this error condition with a callback or exception
            if (mCameraState.getState() < AndroidCamera2StateHolder.CAMERA_CONFIGURED) {
                Log.w(TAG, "Ignoring texture setting at inappropriate time");
                return;
            }

            // Avoid initializing another capture session unless we absolutely have to
            if (surfaceTexture == mPreviewTexture) {
                Log.i(TAG, "Optimizing out redundant preview texture setting");
                return;
            }

            if (mSession != null) {
                closePreviewSession();
            }

            mPreviewTexture = surfaceTexture;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.width(), mPreviewSize.height());

            if (mPreviewSurface != null) {
                mPreviewSurface.release();
            }
            mPreviewSurface = new Surface(surfaceTexture);

            if (mCaptureReader != null) {
                mCaptureReader.close();
            }
            mCaptureReader = ImageReader.newInstance(
                    mPhotoSize.width(), mPhotoSize.height(), ImageFormat.JPEG, 1);

            try {
                mCamera.createCaptureSession(
                        Arrays.asList(mPreviewSurface, mCaptureReader.getSurface()),
                        mCameraPreviewStateCallback, this);
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to create camera capture session", ex);
            }
        }

        private void closePreviewSession() {
            try {
                mSession.abortCaptures();
                mSession = null;
            } catch (CameraAccessException ex) {
                Log.e(TAG, "Failed to close existing camera capture session", ex);
            }
            changeState(AndroidCamera2StateHolder.CAMERA_CONFIGURED);
        }

        private void changeState(int newState) {
            if (mCameraState.getState() != newState) {
                mCameraState.setState(newState);
                if (newState < AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE) {
                    mCurrentAeState = CaptureResult.CONTROL_AE_STATE_INACTIVE;
                    mCameraResultStateCallback.resetState();
                }
            }
        }

        // This callback monitors our connection to and disconnection from camera devices.
        private CameraDevice.StateCallback mCameraDeviceStateCallback =
                new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Log.i(TAG,"onOpened CameraDevice will camera=" + camera);
                mCamera = camera;
                if (mOpenCallback != null) {
                    try {
                        CameraCharacteristics props =
                                mCameraManager.getCameraCharacteristics(mCameraId);
                        CameraDeviceInfo.Characteristics characteristics =
                                getCameraDeviceInfo().getCharacteristics(mCameraIndex);
                        mCameraProxy = new AndroidCamera2ProxyImpl(AndroidCamera2AgentImpl.this,
                                mCameraIndex, mCamera, characteristics, props);
                        mPersistentSettings = new Camera2RequestSettingsSet();
                        mActiveArray =
                                props.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                        mLegacyDevice =
                                props.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
                        changeState(AndroidCamera2StateHolder.CAMERA_UNCONFIGURED);
                        mOpenCallback.onCameraOpened(mCameraProxy);
                    } catch (CameraAccessException ex) {
                        mOpenCallback.onDeviceOpenFailure(mCameraIndex,
                                generateHistoryString(mCameraIndex));
                    }
                }
                Log.i(TAG,"onOpened CameraDevice end mOpenCallback=" + mOpenCallback);
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                Log.w(TAG, "Camera device '" + mCameraIndex + "' was disconnected");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                Log.e(TAG, "Camera device '" + mCameraIndex + "' encountered error code '" +
                        error + '\'');
                if (mOpenCallback != null) {
                    mOpenCallback.onDeviceOpenFailure(mCameraIndex,
                            generateHistoryString(mCameraIndex));
                }
            }};

        // This callback monitors our camera session (i.e. our transition into and out of preview).
        private CameraCaptureSession.StateCallback mCameraPreviewStateCallback =
                new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession session) {
                mSession = session;
                changeState(AndroidCamera2StateHolder.CAMERA_PREVIEW_READY);
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                // TODO: Invoke a callback
                Log.e(TAG, "Failed to configure the camera for capture");
            }

            @Override
            public void onActive(CameraCaptureSession session) {
                if (mOneshotPreviewingCallback != null) {
                    // The session is up and processing preview requests. Inform the caller.
                    mOneshotPreviewingCallback.onPreviewStarted();
                    mOneshotPreviewingCallback = null;
                }
            }};

        private abstract class CameraResultStateCallback
                extends CameraCaptureSession.CaptureCallback {
            public abstract void monitorControlStates(CaptureResult result);

            public abstract void resetState();
        }

        // This callback monitors requested captures and notifies any relevant callbacks.
        private CameraResultStateCallback mCameraResultStateCallback =
                new CameraResultStateCallback() {
            private int mLastAfState = -1;
            private long mLastAfFrameNumber = -1;
            private long mLastAeFrameNumber = -1;

            @Override
            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureResult result) {
                monitorControlStates(result);
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                           TotalCaptureResult result) {
                monitorControlStates(result);
            }

            @Override
            public void monitorControlStates(CaptureResult result) {
                Integer afStateMaybe = result.get(CaptureResult.CONTROL_AF_STATE);
                if (afStateMaybe != null) {
                    int afState = afStateMaybe;
                    // Since we handle both partial and total results for multiple frames here, we
                    // might get the final callbacks for an earlier frame after receiving one or
                    // more that correspond to the next one. To prevent our data from oscillating,
                    // we never consider AF states that are older than the last one we've seen.
                    if (result.getFrameNumber() > mLastAfFrameNumber) {
                        boolean afStateChanged = afState != mLastAfState;
                        mLastAfState = afState;
                        mLastAfFrameNumber = result.getFrameNumber();

                        switch (afState) {
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED: {
                                if (afStateChanged && mPassiveAfCallback != null) {
                                    // A CameraAFMoveCallback is attached. If we just started to
                                    // scan, the motor is moving; otherwise, it has settled.
                                    mPassiveAfCallback.onAutoFocusMoving(
                                            afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                                            mCameraProxy);
                                }
                                break;
                            }

                            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED: {
                                // This check must be made regardless of whether the focus state has
                                // changed recently to avoid infinite waiting during autoFocus()
                                // when the algorithm has already either converged or failed to.
                                if (mOneshotAfCallback != null) {
                                    // A call to autoFocus() was just made to request a focus lock.
                                    // Notify the caller that the lens is now indefinitely fixed,
                                    // and report whether the image we're stuck with is in focus.
                                    mOneshotAfCallback.onAutoFocus(
                                            afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                                            mCameraProxy);
                                    mOneshotAfCallback = null;
                                }
                                break;
                            }
                        }
                    }
                }

                Integer aeStateMaybe = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeStateMaybe != null) {
                    int aeState = aeStateMaybe;
                    // Since we handle both partial and total results for multiple frames here, we
                    // might get the final callbacks for an earlier frame after receiving one or
                    // more that correspond to the next one. To prevent our data from oscillating,
                    // we never consider AE states that are older than the last one we've seen.
                    if (result.getFrameNumber() > mLastAeFrameNumber) {
                        mCurrentAeState = aeStateMaybe;
                        mLastAeFrameNumber = result.getFrameNumber();

                        switch (aeState) {
                            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                            case CaptureResult.CONTROL_AE_STATE_LOCKED: {
                                // This check must be made regardless of whether the exposure state
                                // has changed recently to avoid infinite waiting during
                                // takePicture() when the algorithm has already converged.
                                if (mOneshotCaptureCallback != null) {
                                    // A call to takePicture() was just made, and autoexposure
                                    // converged so it's time to initiate the capture!
                                    mCaptureReader.setOnImageAvailableListener(
                                            /*listener*/mOneshotCaptureCallback,
                                            /*handler*/Camera2Handler.this);
                                    try {
                                        mSession.capture(
                                                mPersistentSettings.createRequest(mCamera,
                                                        CameraDevice.TEMPLATE_STILL_CAPTURE,
                                                        mCaptureReader.getSurface()),
                                                /*callback*/mOneshotCaptureCallback,
                                                /*handler*/Camera2Handler.this);
                                    } catch (CameraAccessException ex) {
                                        Log.e(TAG, "Unable to initiate capture", ex);
                                    } finally {
                                        mOneshotCaptureCallback = null;
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                /* SPRD:Add for ai detect @{ */
                Integer faceState = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
                if (faceState != null) {
                    int aeState = faceState;
                    switch (aeState) {
                        case CaptureResult.STATISTICS_FACE_DETECT_MODE_SIMPLE:
                            android.hardware.camera2.params.Face[] faces = result
                                    .get(CaptureResult.STATISTICS_FACES);
                            Camera.Face[] cFaces = new Camera.Face[faces.length];
                            for (int i = 0; i < faces.length; i++) {
                                Camera.Face face = new Camera.Face();
                                face.score = faces[i].getScore();
                                face.rect = faceForConvertCoordinate(faces[i].getBounds());
                                cFaces[i] = face;
                            }
                            if (mFaceDetectionCallback != null) {
                                mFaceDetectionCallback.onFaceDetection(cFaces, mCameraProxy);
                            }
                            break;
                    }
                }
            }
            /* @} */

            @Override
            public void resetState() {
                mLastAfState = -1;
                mLastAfFrameNumber = -1;
                mLastAeFrameNumber = -1;
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureFailure failure) {
                Log.e(TAG, "Capture attempt failed with reason " + failure.getReason());
            }};
            /* SRPD:Add for ai detect @{ */
            public Rect faceForConvertCoordinate (Rect rect){
                if (mActiveArray == null) {
                    return null;
                }
                //coverity 105552
                //Rect resultRect = new Rect();
                int sensorWidth = mActiveArray.width();
                int sendorHeight = mActiveArray.height();
                int left = rect.left * 2000/sensorWidth - 1000;
                int top = rect.top * 2000/sendorHeight - 1000;
                int right = rect.right * 2000/sensorWidth - 1000;
                int bottom = rect.bottom * 2000/sendorHeight -1000;
                return new Rect(left,top,right,bottom);
            }
            /* @} */
    }

    private class AndroidCamera2ProxyImpl extends CameraAgent.CameraProxy {
        private final AndroidCamera2AgentImpl mCameraAgent;
        private final int mCameraIndex;
        private final CameraDevice mCamera;
        private final CameraDeviceInfo.Characteristics mCharacteristics;
        private final AndroidCamera2Capabilities mCapabilities;
        private CameraSettings mLastSettings;
        private boolean mShutterSoundEnabled;

        public AndroidCamera2ProxyImpl(
                AndroidCamera2AgentImpl agent,
                int cameraIndex,
                CameraDevice camera,
                CameraDeviceInfo.Characteristics characteristics,
                CameraCharacteristics properties) {
            mCameraAgent = agent;
            mCameraIndex = cameraIndex;
            mCamera = camera;
            mCharacteristics = characteristics;
            mCapabilities = new AndroidCamera2Capabilities(properties);
            mLastSettings = null;
            mShutterSoundEnabled = true;
        }

        // TODO: Implement
        @Override
        public android.hardware.Camera getCamera() { return null; }

        @Override
        public int getCameraId() {
            return mCameraIndex;
        }

        @Override
        public CameraDeviceInfo.Characteristics getCharacteristics() {
            return mCharacteristics;
        }

        @Override
        public CameraCapabilities getCapabilities() {
            return mCapabilities;
        }

        public CameraAgent getAgent() {
            return mCameraAgent;
        }

        private AndroidCamera2Capabilities getSpecializedCapabilities() {
            return mCapabilities;
        }

        // FIXME: Unlock the sizes in stopPreview(), as per the corresponding
        // explanation on the STOP_PREVIEW case in the handler.
        @Override
        public void setPreviewTexture(SurfaceTexture surfaceTexture) {
            // Once the Surface has been selected, we configure the session and
            // are no longer able to change the sizes.
            getSettings().setSizesLocked(true);
            super.setPreviewTexture(surfaceTexture);
        }

        // FIXME: Unlock the sizes in stopPreview(), as per the corresponding
        // explanation on the STOP_PREVIEW case in the handler.
        @Override
        public void setPreviewTextureSync(SurfaceTexture surfaceTexture) {
            // Once the Surface has been selected, we configure the session and
            // are no longer able to change the sizes.
            getSettings().setSizesLocked(true);
            super.setPreviewTexture(surfaceTexture);
        }

        // TODO: Implement
        @Override
        public void setPreviewDataCallback(Handler handler, CameraPreviewDataCallback cb) {}

        // TODO: Implement
        @Override
        public void setOneShotPreviewCallback(Handler handler, CameraPreviewDataCallback cb) {}

        // TODO: Implement
        @Override
        public void setPreviewDataCallbackWithBuffer(Handler handler, CameraPreviewDataCallback cb)
                {}

        // TODO: Implement
        public void addCallbackBuffer(final byte[] callbackBuffer) {}

        @Override
        public void autoFocus(final Handler handler, final CameraAFCallback cb) {
            try {
                mDispatchThread.runJob(new Runnable() {
                    @Override
                    public void run() {
                        CameraAFCallback cbForward = null;
                        if (cb != null) {
                            cbForward = new CameraAFCallback() {
                                @Override
                                public void onAutoFocus(final boolean focused,
                                                        final CameraProxy camera) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            cb.onAutoFocus(focused, camera);
                                        }
                                    });
                                }
                            };
                        }

                        mCameraState.waitForStates(AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE |
                                AndroidCamera2StateHolder.CAMERA_FOCUS_LOCKED);
                        mCameraHandler.obtainMessage(CameraActions.AUTO_FOCUS, cbForward)
                                .sendToTarget();
                    }
                });
            } catch (RuntimeException ex) {
                mCameraAgent.getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void setAutoFocusMoveCallback(final Handler handler, final CameraAFMoveCallback cb) {
            try {
                mDispatchThread.runJob(new Runnable() {
                    @Override
                    public void run() {
                        CameraAFMoveCallback cbForward = null;
                        if (cb != null) {
                            cbForward = new CameraAFMoveCallback() {
                                @Override
                                public void onAutoFocusMoving(final boolean moving,
                                                              final CameraProxy camera) {
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            cb.onAutoFocusMoving(moving, camera);
                                        }
                                    });
                                }
                            };
                        }

                        mCameraHandler.obtainMessage(CameraActions.SET_AUTO_FOCUS_MOVE_CALLBACK,
                                cbForward).sendToTarget();
                    }
                });
            } catch (RuntimeException ex) {
                mCameraAgent.getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        @Override
        public void takePicture(final Handler handler,
                                final CameraShutterCallback shutter,
                                CameraPictureCallback raw,
                                CameraPictureCallback postview,
                                final CameraPictureCallback jpeg) {
            Log.i(TAG,"AppFw takePicture");
            // TODO: We never call raw or postview
            final CaptureAvailableListener picListener =
                    new CaptureAvailableListener() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                             long timestamp, long frameNumber) {
                    Log.i(TAG,"AppFw takePicture onCaptureStarted");
                    if (shutter != null) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                /**
                                 * SPRD:fix bug 473462 add burst capture
                                if (mShutterSoundEnabled) {
                                    mNoisemaker.play(MediaActionSound.SHUTTER_CLICK);
                                }
                                */
                                shutter.onShutter(AndroidCamera2ProxyImpl.this);
                            }});
                    }
                }

                @Override
                public void onImageAvailable(ImageReader reader) {
                    Log.i(TAG,"AppFw takePicture onImageAvailable");
                    try (Image image = reader.acquireNextImage()) {
                        if (jpeg != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            final byte[] pixels = new byte[buffer.remaining()];
                            buffer.get(pixels);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    jpeg.onPictureTaken(pixels, AndroidCamera2ProxyImpl.this);
                                }});
                        }
                    }
                }};
            try {
                mDispatchThread.runJob(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG,"AppFw takePicture runJob");
                        // Wait until PREVIEW_ACTIVE or better
                        mCameraState.waitForStates(
                                ~(AndroidCamera2StateHolder.CAMERA_PREVIEW_ACTIVE - 1));
                        /**
                         * SPRD:fix bug 473462 add burst capture
                        mCameraHandler.obtainMessage(CameraActions.CAPTURE_PHOTO, picListener)
                                .sendToTarget();
                        */
                        if (mPersistentSettings.get(CaptureRequest.SPRD_CAPTURE_MODE) > 1) {
                            Log.i(TAG,"AppFw takePicture burst picture");
                            mCameraHandler.obtainMessage(CameraActions.CAPTURE_BURST_PHOTO, picListener).sendToTarget();
                        } else {
                            Log.i(TAG,"AppFw takePicture one picture");
                            mCameraHandler.obtainMessage(CameraActions.CAPTURE_PHOTO, picListener).sendToTarget();
                        }
                    }
                });
            } catch (RuntimeException ex) {
                mCameraAgent.getCameraExceptionHandler().onDispatchThreadException(ex);
            }
        }

        // TODO: Implement
        @Override
        public void setZoomChangeListener(android.hardware.Camera.OnZoomChangeListener listener) {}

        /* SPRD:Add for ai detect bug 474723 @{
        // TODO: Implement
        @Override
        public void setFaceDetectionCallback(Handler handler, CameraFaceDetectionCallback callback)
                {}
        */
        @Override
        public void setFaceDetectionCallback(Handler handler, CameraFaceDetectionCallback callback)
        {
            mCameraHandler.obtainMessage(
                    CameraActions.SET_FACE_DETECTION_LISTENER,
                    FaceDetectionCallbackForward.getNewInstance(handler,
                            AndroidCamera2ProxyImpl.this, callback)).sendToTarget();
        }
        /* @} */

        /* SPRD:Add for ai detect bug 474723 @{
        // TODO: Remove this method override once we handle this message
        @Override
        public void startFaceDetection() {}
        */
        // TODO: Remove this method override once we handle this message
        @Override
        public void startFaceDetection() {
            mCameraHandler.sendEmptyMessage(CameraActions.START_FACE_DETECTION);
        }
        /* @} */

        /* SPRD:Add for ai detect bug 474723 @{
        // TODO: Remove this method override once we handle this message
        @Override
        public void stopFaceDetection() {}
        */
        // TODO: Remove this method override once we handle this message
        @Override
        public void stopFaceDetection() {
            mCameraHandler.sendEmptyMessage(CameraActions.STOP_FACE_DETECTION);
        }
        /* @} */

        // TODO: Implement
        @Override
        public void setParameters(android.hardware.Camera.Parameters params) {}

        // TODO: Implement
        @Override
        public android.hardware.Camera.Parameters getParameters() { return null; }

        @Override
        public CameraSettings getSettings() {
            if (mLastSettings == null) {
                mLastSettings = mCameraHandler.buildSettings(mCapabilities);
            }
            return mLastSettings;
        }

        @Override
        public boolean applySettings(CameraSettings settings) {
            if (settings == null) {
                Log.w(TAG, "null parameters in applySettings()");
                return false;
            }
            if (!(settings instanceof AndroidCamera2Settings)) {
                Log.e(TAG, "Provided settings not compatible with the backing framework API");
                return false;
            }

            // Wait for any state that isn't OPENED
            if (applySettingsHelper(settings, ~AndroidCamera2StateHolder.CAMERA_UNOPENED)) {
                mLastSettings = settings;
                return true;
            }
            return false;
        }

        @Override
        public void enableShutterSound(boolean enable) {
            mShutterSoundEnabled = enable;
        }

        // TODO: Implement
        @Override
        public String dumpDeviceSettings() { return null; }

        @Override
        public Handler getCameraHandler() {
            return AndroidCamera2AgentImpl.this.getCameraHandler();
        }

        @Override
        public DispatchThread getDispatchThread() {
            return AndroidCamera2AgentImpl.this.getDispatchThread();
        }

        @Override
        public CameraStateHolder getCameraState() {
            return mCameraState;
        }

        /* SPRD:fix bug 497854 when cancel 10 burst capture,the count of pics saveing is wrong {@ */
        @Override
        public int cancelBurstCapture(CancelBurstCaptureCallback cb) {
            Log.i(TAG, "cancelBurstCapture");
            getCameraHandler().sendMessageAtFrontOfQueue(
                    getCameraHandler().obtainMessage(CameraActions.CANCEL_CAPTURE_BURST_PHOTO, cb));
            return mCanceledCaptureCount;
        }
        /* @} */
    }


    private static class FaceDetectionCallbackForward implements CameraFaceDetectionCallback {
        private final Handler mHandler;
        private final CameraFaceDetectionCallback mCallback;
        private final CameraAgent.CameraProxy mCamera;

        public static FaceDetectionCallbackForward getNewInstance(Handler handler,
                CameraProxy camera, CameraFaceDetectionCallback cb) {
            if (handler == null || camera == null || cb == null)
                return null;
            return new FaceDetectionCallbackForward(handler, camera, cb);
        }

        private FaceDetectionCallbackForward(Handler h, CameraAgent.CameraProxy camera,
                CameraFaceDetectionCallback cb) {
            mHandler = h;
            mCamera = camera;
            mCallback = cb;
        }

        @Override
        public void onFaceDetection(final Camera.Face[] faces, CameraAgent.CameraProxy camera) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onFaceDetection(faces, mCamera);
                }
            });
        }
    }

    /** A linear state machine: each state entails all the states below it. */
    private static class AndroidCamera2StateHolder extends CameraStateHolder {
        // Usage flow: openCamera() -> applySettings() -> setPreviewTexture() -> startPreview() ->
        //             autoFocus() -> takePicture()
        // States are mutually exclusive, but must be separate bits so that they can be used with
        // the StateHolder#waitForStates() and StateHolder#waitToAvoidStates() methods.
        // Do not set the state to be a combination of these values!
        /* Camera states */
        /** No camera device is opened. */
        public static final int CAMERA_UNOPENED = 1 << 0;
        /** A camera is opened, but no settings have been provided. */
        public static final int CAMERA_UNCONFIGURED = 1 << 1;
        /** The open camera has been configured by providing it with settings. */
        public static final int CAMERA_CONFIGURED = 1 << 2;
        /** A capture session is ready to stream a preview, but still has no repeating request. */
        public static final int CAMERA_PREVIEW_READY = 1 << 3;
        /** A preview is currently being streamed. */
        public static final int CAMERA_PREVIEW_ACTIVE = 1 << 4;
        /** The lens is locked on a particular region. */
        public static final int CAMERA_FOCUS_LOCKED = 1 << 5;

        public AndroidCamera2StateHolder() {
            this(CAMERA_UNOPENED);
        }

        public AndroidCamera2StateHolder(int state) {
            super(state);
        }
    }

    private static class AndroidCamera2DeviceInfo implements CameraDeviceInfo {
        private final CameraManager mCameraManager;
        private final String[] mCameraIds;
        private final int mNumberOfCameras;
        private final int mFirstBackCameraId;
        private final int mFirstFrontCameraId;
        //SPRD:add for smile capture Bug548832
        private boolean isSmileEnable = false;

        public AndroidCamera2DeviceInfo(CameraManager cameraManager,
                                        String[] cameraIds, int numberOfCameras) {
            mCameraManager = cameraManager;
            mCameraIds = cameraIds;
            mNumberOfCameras = numberOfCameras;

            int firstBackId = NO_DEVICE;
            int firstFrontId = NO_DEVICE;
            for (int id = 0; id < cameraIds.length; ++id) {
                try {
                    int lensDirection = cameraManager.getCameraCharacteristics(cameraIds[id])
                            .get(CameraCharacteristics.LENS_FACING);
                    if (firstBackId == NO_DEVICE &&
                            lensDirection == CameraCharacteristics.LENS_FACING_BACK) {
                        firstBackId = id;
                    }
                    if (firstFrontId == NO_DEVICE &&
                            lensDirection == CameraCharacteristics.LENS_FACING_FRONT) {
                        firstFrontId = id;
                    }
                    /*SPRD:add for smile capture Bug548832 @{*/
                    if (cameraManager.getCameraCharacteristics(cameraIds[firstBackId]).get(
                            CameraCharacteristics.CONTROL_AVAILABLE_SMILEENABLE) == 1)
                    {
                        isSmileEnable = true;
                    } else {
                        isSmileEnable = false;
                    }
                    /*@}*/
                } catch (CameraAccessException ex) {
                    Log.w(TAG, "Couldn't get characteristics of camera '" + id + "'", ex);
                }
            }
            mFirstBackCameraId = firstBackId;
            mFirstFrontCameraId = firstFrontId;
        }

        @Override
        public Characteristics getCharacteristics(int cameraId) {
            String actualId = mCameraIds[cameraId];
            try {
                CameraCharacteristics info = mCameraManager.getCameraCharacteristics(actualId);
                return new AndroidCharacteristics2(info);
            } catch (CameraAccessException ex) {
                return null;
            }
        }

        /*SPRD:add for smile capture Bug548832 @{*/
        @Override
        public boolean getSmileEnable(){
            return isSmileEnable;
        }
        /*@}*/
        @Override
        public int getNumberOfCameras() {
            return mNumberOfCameras;
        }

        @Override
        public int getFirstBackCameraId() {
            return mFirstBackCameraId;
        }

        @Override
        public int getFirstFrontCameraId() {
            return mFirstFrontCameraId;
        }

        private static class AndroidCharacteristics2 extends Characteristics {
            private CameraCharacteristics mCameraInfo;

            AndroidCharacteristics2(CameraCharacteristics cameraInfo) {
                mCameraInfo = cameraInfo;
            }

            @Override
            public boolean isFacingBack() {
                return mCameraInfo.get(CameraCharacteristics.LENS_FACING)
                        .equals(CameraCharacteristics.LENS_FACING_BACK);
            }

            @Override
            public boolean isFacingFront() {
                return mCameraInfo.get(CameraCharacteristics.LENS_FACING)
                        .equals(CameraCharacteristics.LENS_FACING_FRONT);
            }

            @Override
            public int getSensorOrientation() {
                return mCameraInfo.get(CameraCharacteristics.SENSOR_ORIENTATION);
            }

            @Override
            public Matrix getPreviewTransform(int currentDisplayOrientation,
                                              RectF surfaceDimensions,
                                              RectF desiredBounds) {
                if (!orientationIsValid(currentDisplayOrientation)) {
                    return new Matrix();
                }

                // The system transparently transforms the image to fill the surface
                // when the device is in its natural orientation. We rotate the
                // coordinates of the rectangle's corners to be relative to the
                // original image, instead of to the current screen orientation.
                float[] surfacePolygon = rotate(convertRectToPoly(surfaceDimensions),
                        2 * currentDisplayOrientation / 90);
                float[] desiredPolygon = convertRectToPoly(desiredBounds);

                Matrix transform = new Matrix();
                // Use polygons instead of rectangles so that rotation will be
                // calculated, since that is not done by the new camera API.
                transform.setPolyToPoly(surfacePolygon, 0, desiredPolygon, 0, 4);
                return transform;
            }

            @Override
            public boolean canDisableShutterSound() {
                return true;
            }

            private static float[] convertRectToPoly(RectF rf) {
                return new float[] {rf.left, rf.top, rf.right, rf.top,
                        rf.right, rf.bottom, rf.left, rf.bottom};
            }

            private static float[] rotate(float[] arr, int times) {
                if (times < 0) {
                    times = times % arr.length + arr.length;
                }

                float[] res = new float[arr.length];
                for (int offset = 0; offset < arr.length; ++offset) {
                    res[offset] = arr[(times + offset) % arr.length];
                }
                return res;
            }
        }
    }
}
