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

import android.hardware.Camera;

import com.android.ex.camera2.portability.CameraCapabilities.*;
import com.android.ex.camera2.portability.debug.Log;

/**
 * The subclass of {@link CameraSettings} for Android Camera 1 API.
 */
public class AndroidCameraSettings extends CameraSettings {
    private static final Log.Tag TAG = new Log.Tag("AndCamSet");

    private static final String TRUE = "true";
    private static final String RECORDING_HINT = "recording-hint";

    public AndroidCameraSettings(CameraCapabilities capabilities, Camera.Parameters params) {
        if (params == null) {
            Log.w(TAG, "Settings ctor requires a non-null Camera.Parameters.");
            return;
        }

        CameraCapabilities.Stringifier stringifier = capabilities.getStringifier();

        setSizesLocked(false);

        // Preview
        Camera.Size paramPreviewSize = params.getPreviewSize();
        setPreviewSize(new Size(paramPreviewSize.width, paramPreviewSize.height));
        setPreviewFrameRate(params.getPreviewFrameRate());
        int[] previewFpsRange = new int[2];
        params.getPreviewFpsRange(previewFpsRange);
        setPreviewFpsRange(previewFpsRange[Camera.Parameters.PREVIEW_FPS_MIN_INDEX],
                previewFpsRange[Camera.Parameters.PREVIEW_FPS_MAX_INDEX]);
        setPreviewFormat(params.getPreviewFormat());

        // Capture: Focus, flash, zoom, exposure, scene mode.
        if (capabilities.supports(CameraCapabilities.Feature.ZOOM)) {
            setZoomRatio(params.getZoomRatios().get(params.getZoom()) / 100f);
        } else {
            setZoomRatio(CameraCapabilities.ZOOM_RATIO_UNZOOMED);
        }
        setExposureCompensationIndex(params.getExposureCompensation());
        setFlashMode(stringifier.flashModeFromString(params.getFlashMode()));
        setFocusMode(stringifier.focusModeFromString(params.getFocusMode()));
        setSceneMode(stringifier.sceneModeFromString(params.getSceneMode()));
        setAntibanding(getAntibandingFromParameters(params.getAntibanding()));//SPRD:Add for antibanding
        setWhiteBalance(stringifier.whiteBalanceFromString(params.getWhiteBalance()));//SPRD:Add for whitebalance

        // Video capture.
        if (capabilities.supports(CameraCapabilities.Feature.VIDEO_STABILIZATION)) {
            setVideoStabilization(isVideoStabilizationEnabled());
        }
        setRecordingHintEnabled(TRUE.equals(params.get(RECORDING_HINT)));

        // Output: Photo size, compression quality
        setPhotoJpegCompressionQuality(params.getJpegQuality());
        Camera.Size paramPictureSize = params.getPictureSize();
        setPhotoSize(new Size(paramPictureSize.width, paramPictureSize.height));
        setPhotoFormat(params.getPictureFormat());

        // SPRD Bug:474721 Feature:Contrast.
        setContrast(getContrastFromParameters(params.getContrast()));

        // SPRD Bug:474715 Feature:Brightness.
        setBrightNess(getBrightNessFromParameters(params.getBrightness()));

        // SPRD Bug:474724 Feature:ISO.
        setISO(getISOFromParameters(params.getISO()));

        // SPRD Bug:474718 Feature:Metering.
        setMetering(getMeteringFromParameters(params.getMeteringMode()));

        // SPRD Bug:474722 Feature:Saturation.
        setSaturation(getSaturationFromParameters(params.getSaturation()));

        // SPRD Bug:474696 Feature:Slow-Motion.
        setVideoSlowMotion(params.getSlowmotion());
    }

    public AndroidCameraSettings(AndroidCameraSettings other) {
        super(other);
    }

    /* SPRD:Add for antibanding @{ */
    private CameraCapabilities.Antibanding getAntibandingFromParameters(String param) {
        if (Camera.Parameters.ANTIBANDING_AUTO.equals(param)) {
            return CameraCapabilities.Antibanding.AUTO;
        } else if (Camera.Parameters.ANTIBANDING_50HZ.equals(param)) {
            return CameraCapabilities.Antibanding.ANTIBANDING_50HZ;
        } else if (Camera.Parameters.ANTIBANDING_60HZ.equals(param)) {
            return CameraCapabilities.Antibanding.ANTIBANDING_60HZ;
        } else if (Camera.Parameters.ANTIBANDING_OFF.equals(param)) {
            return CameraCapabilities.Antibanding.OFF;
        } else {
            return null;
        }
    }
    /* @} */

    @Override
    public CameraSettings copy() {
        return new AndroidCameraSettings(this);
    }

    /*
     * SPRD Bug:474721 Feature:Contrast. @{
     */
    private CameraCapabilities.Contrast getContrastFromParameters(String param) {
        if (AndroidCameraCapabilities.VALUE_ZERO.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_ZERO;
        } else if (AndroidCameraCapabilities.VALUE_ONE.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_ONE;
        } else if (AndroidCameraCapabilities.VALUE_TWO.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_TWO;
        } else if (AndroidCameraCapabilities.VALUE_THREE.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_THREE;
        } else if (AndroidCameraCapabilities.VALUE_FOUR.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_FOUR;
        } else if (AndroidCameraCapabilities.VALUE_FIVE.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_FIVE;
        } else if (AndroidCameraCapabilities.VALUE_SIX.equals(param)) {
            return CameraCapabilities.Contrast.CONTRAST_SIX;
        } else {
            return null;
        }
    }
    /* @} */

    // SPRD Bug:474715 Feature:Brightness.
    private CameraCapabilities.BrightNess getBrightNessFromParameters(String param) {
        if (AndroidCameraCapabilities.VALUE_ZERO.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_ZERO;
        } else if (AndroidCameraCapabilities.VALUE_ONE.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_ONE;
        } else if (AndroidCameraCapabilities.VALUE_TWO.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_TWO;
        } else if (AndroidCameraCapabilities.VALUE_THREE.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_THREE;
        } else if (AndroidCameraCapabilities.VALUE_FOUR.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_FOUR;
        } else if (AndroidCameraCapabilities.VALUE_FIVE.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_FIVE;
        } else if (AndroidCameraCapabilities.VALUE_SIX.equals(param)) {
            return CameraCapabilities.BrightNess.BRIGHTNESS_SIX;
        } else {
            return null;
        }
    }
    /* @} */

    /*
     * SPRD Bug:474724 Feature:ISO. @{
     */
    private CameraCapabilities.ISO getISOFromParameters(String param) {
        if (Camera.Parameters.ISO_AUTO.equals(param)) {
            return CameraCapabilities.ISO.AUTO;
        } else if (Camera.Parameters.ISO_100.equals(param)) {
            return CameraCapabilities.ISO.ISO_100;
        } else if (Camera.Parameters.ISO_200.equals(param)) {
            return CameraCapabilities.ISO.ISO_200;
        } else if (Camera.Parameters.ISO_400.equals(param)) {
            return CameraCapabilities.ISO.ISO_400;
        } else if (Camera.Parameters.ISO_800.equals(param)) {
            return CameraCapabilities.ISO.ISO_800;
        } else if (Camera.Parameters.ISO_1600.equals(param)) {
            return CameraCapabilities.ISO.ISO_1600;
        } else {
            return null;
        }
    }
    /* @} */

    /*
     * SPRD Bug:474718 Feature:Metering. @{
     */
    private CameraCapabilities.Metering getMeteringFromParameters(String param) {
        if (Camera.Parameters.AUTO_EXPOSURE_FRAME_AVG.equals(param)) {
            return CameraCapabilities.Metering.FRAMEAVERAGE;
        } else if (Camera.Parameters.AUTO_EXPOSURE_CENTER_WEIGHTED.equals(param)) {
            return CameraCapabilities.Metering.CENTERWEIGHTED;
        } else if (Camera.Parameters.AUTO_EXPOSURE_SPOT_METERING.equals(param)) {
            return CameraCapabilities.Metering.SPOTMETERING;
        } else {
            return null;
        }
    }
    /* @} */

    /*
     * SPRD Bug:474722 Feature:Saturation. @{
     */
    private CameraCapabilities.Saturation getSaturationFromParameters(String param) {
        if (AndroidCameraCapabilities.VALUE_ZERO.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_ZERO;
        } else if (AndroidCameraCapabilities.VALUE_ONE.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_ONE;
        } else if (AndroidCameraCapabilities.VALUE_TWO.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_TWO;
        } else if (AndroidCameraCapabilities.VALUE_THREE.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_THREE;
        } else if (AndroidCameraCapabilities.VALUE_FOUR.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_FOUR;
        } else if (AndroidCameraCapabilities.VALUE_FIVE.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_FIVE;
        } else if (AndroidCameraCapabilities.VALUE_SIX.equals(param)) {
            return CameraCapabilities.Saturation.SATURATION_SIX;
        } else {
            return null;
        }
    }
    /* @} */
}
