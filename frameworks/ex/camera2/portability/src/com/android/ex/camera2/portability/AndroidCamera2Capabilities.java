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

import static android.hardware.camera2.CameraCharacteristics.*;

import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.util.Range;
import android.util.Rational;

import com.android.ex.camera2.portability.CameraCapabilities.*;

import com.android.ex.camera2.portability.debug.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The subclass of {@link CameraCapabilities} for Android Camera 2 API.
 */
public class AndroidCamera2Capabilities extends CameraCapabilities {
    private static Log.Tag TAG = new Log.Tag("AndCam2Capabs");

    AndroidCamera2Capabilities(CameraCharacteristics p) {
        super(new Stringifier());

        StreamConfigurationMap s = p.get(SCALER_STREAM_CONFIGURATION_MAP);

        for (Range<Integer> fpsRange : p.get(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
            mSupportedPreviewFpsRange.add(new int[] { fpsRange.getLower(), fpsRange.getUpper() });
        }

        // TODO: We only support TextureView preview rendering
        mSupportedPreviewSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(SurfaceTexture.class))));
        for (int format : s.getOutputFormats()) {
            mSupportedPreviewFormats.add(format);
        }

        // TODO: We only support MediaRecorder video capture
        mSupportedVideoSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(MediaRecorder.class))));

        // TODO: We only support JPEG image capture
        mSupportedPhotoSizes.addAll(Size.buildListFromAndroidSizes(Arrays.asList(
                s.getOutputSizes(ImageFormat.JPEG))));
        mSupportedPhotoFormats.addAll(mSupportedPreviewFormats);

        buildSceneModes(p);
        buildFlashModes(p);
        buildFocusModes(p);
        buildWhiteBalances(p);

        buildAntibandingModes(p);// SPRD:Add for antibanding

        buildColorEffects(p);//SPRD:Add for color effect Bug 474727

        // SPRD Bug:474721 Feature:Contrast.
        buildContrast(p);

        // SPRD Bug:474715 Feature:Brightness.
        buildBrightNess(p);

        // SPRD Bug:474724 Feature:ISO.
        buildISO(p);

        // SPRD Bug:474718 Feature:Metering.
        buildMetering(p);

        // SPRD Bug:474722 Feature:Saturation.
        buildSaturation(p);

        // TODO: Populate mSupportedFeatures

        // TODO: Populate mPreferredPreviewSizeForVideo

        Range<Integer> ecRange = p.get(CONTROL_AE_COMPENSATION_RANGE);
        mMinExposureCompensation = ecRange.getLower();
        mMaxExposureCompensation = ecRange.getUpper();

        Rational ecStep = p.get(CONTROL_AE_COMPENSATION_STEP);
        mExposureCompensationStep = (float) ecStep.getNumerator() / ecStep.getDenominator();

        mMaxNumOfFacesSupported = p.get(STATISTICS_INFO_MAX_FACE_COUNT);
        mMaxNumOfMeteringArea = p.get(CONTROL_MAX_REGIONS_AE);

        mMaxZoomRatio = p.get(SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        // TODO: Populate mHorizontalViewAngle
        // TODO: Populate mVerticalViewAngle
        // TODO: Populate mZoomRatioList
        // TODO: Populate mMaxZoomIndex

        if (supports(FocusMode.AUTO)) {
            mMaxNumOfFocusAreas = p.get(CONTROL_MAX_REGIONS_AF);
            if (mMaxNumOfFocusAreas > 0) {
                mSupportedFeatures.add(Feature.FOCUS_AREA);
            }
        }
        if (mMaxNumOfMeteringArea > 0) {
            mSupportedFeatures.add(Feature.METERING_AREA);
        }

        if (mMaxZoomRatio > CameraCapabilities.ZOOM_RATIO_UNZOOMED) {
            mSupportedFeatures.add(Feature.ZOOM);
        }

        // TODO: Detect other features
    }

    private void buildSceneModes(CameraCharacteristics p) {
        int[] scenes = p.get(CONTROL_AVAILABLE_SCENE_MODES);
        if (scenes != null) {
            for (int scene : scenes) {
                SceneMode equiv = sceneModeFromInt(scene);
                if (equiv != null) {
                    mSupportedSceneModes.add(equiv);
                }
            }
        }
    }

    private void buildFlashModes(CameraCharacteristics p) {
        mSupportedFlashModes.add(FlashMode.OFF);
        if (p.get(FLASH_INFO_AVAILABLE)) {
            mSupportedFlashModes.add(FlashMode.AUTO);
            mSupportedFlashModes.add(FlashMode.ON);
            mSupportedFlashModes.add(FlashMode.TORCH);
            for (int expose : p.get(CONTROL_AE_AVAILABLE_MODES)) {
                if (expose == CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
                    mSupportedFlashModes.add(FlashMode.RED_EYE);
                }
            }
        }
    }

    private void buildFocusModes(CameraCharacteristics p) {
        int[] focuses = p.get(CONTROL_AF_AVAILABLE_MODES);
        if (focuses != null) {
            for (int focus : focuses) {
                FocusMode equiv = focusModeFromInt(focus);
                if (equiv != null) {
                    mSupportedFocusModes.add(equiv);
                }
            }
        }
    }

    private void buildWhiteBalances(CameraCharacteristics p) {
        int[] bals = p.get(CONTROL_AWB_AVAILABLE_MODES);
        if (bals != null) {
            for (int bal : bals) {
                WhiteBalance equiv = whiteBalanceFromInt(bal);
                if (equiv != null) {
                    mSupportedWhiteBalances.add(equiv);
                }
            }
        }
    }

    /* SPRD:Add for antibanding @{ */
    private void buildAntibandingModes(CameraCharacteristics p) {
        int[] antibanding = p.get(CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        if (antibanding != null) {
            for (int antiband : antibanding) {
                Antibanding equiv = antibandingFromInt(antiband);
                if (equiv != null) {
                    mSupportedAntibanding.add(equiv);
                }
            }
        }
    }
    /* @} */

    /* SPRD:Add for color effect Bug 474727 @{ */
    private void buildColorEffects(CameraCharacteristics p) {
        int[] effects = p.get(CONTROL_AVAILABLE_EFFECTS);
        if (effects != null) {
            for (int effect : effects) {
                ColorEffect equiv = colorEffectFromInt(effect);
                if (equiv != null) {
                    mSupportedColorEffects.add(equiv);
                }
            }
        }
    }
    /* @} */

    /**
     * Converts the API-related integer representation of the focus mode to the
     * abstract representation.
     *
     * @param fm The integral representation.
     * @return The mode represented by the input integer, or {@code null} if it
     *         cannot be converted.
     */
    public static FocusMode focusModeFromInt(int fm) {
        switch (fm) {
            case CONTROL_AF_MODE_AUTO:
                return FocusMode.AUTO;
            case CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                return FocusMode.CONTINUOUS_PICTURE;
            case CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                return FocusMode.CONTINUOUS_VIDEO;
            case CONTROL_AF_MODE_EDOF:
                return FocusMode.EXTENDED_DOF;
            case CONTROL_AF_MODE_OFF:
                return FocusMode.FIXED;
            // TODO: We cannot support INFINITY
            case CONTROL_AF_MODE_MACRO:
                return FocusMode.MACRO;
        }
        Log.w(TAG, "Unable to convert from API 2 focus mode: " + fm);
        return null;
    }

    /**
     * Converts the API-related integer representation of the scene mode to the
     * abstract representation.
     *
     * @param sm The integral representation.
     * @return The mode represented by the input integer, or {@code null} if it
     *         cannot be converted.
     */
    public static SceneMode sceneModeFromInt(int sm) {
        switch (sm) {
            case CONTROL_SCENE_MODE_DISABLED:
                return SceneMode.AUTO;
            case CONTROL_SCENE_MODE_ACTION:
                return SceneMode.ACTION;
            case CONTROL_SCENE_MODE_BARCODE:
                return SceneMode.BARCODE;
            case CONTROL_SCENE_MODE_BEACH:
                return SceneMode.BEACH;
            case CONTROL_SCENE_MODE_CANDLELIGHT:
                return SceneMode.CANDLELIGHT;
            case CONTROL_SCENE_MODE_FIREWORKS:
                return SceneMode.FIREWORKS;
            case CONTROL_SCENE_MODE_LANDSCAPE:
                return SceneMode.LANDSCAPE;
            case CONTROL_SCENE_MODE_NIGHT:
                return SceneMode.NIGHT;
            // TODO: We cannot support NIGHT_PORTRAIT
            case CONTROL_SCENE_MODE_PARTY:
                return SceneMode.PARTY;
            case CONTROL_SCENE_MODE_PORTRAIT:
                return SceneMode.PORTRAIT;
            case CONTROL_SCENE_MODE_SNOW:
                return SceneMode.SNOW;
            case CONTROL_SCENE_MODE_SPORTS:
                return SceneMode.SPORTS;
            case CONTROL_SCENE_MODE_STEADYPHOTO:
                return SceneMode.STEADYPHOTO;
            case CONTROL_SCENE_MODE_SUNSET:
                return SceneMode.SUNSET;
            case CONTROL_SCENE_MODE_THEATRE:
                return SceneMode.THEATRE;
            case CONTROL_SCENE_MODE_HDR:
                return SceneMode.HDR;
            // TODO: We cannot expose FACE_PRIORITY, or HIGH_SPEED_VIDEO
        }

        Log.w(TAG, "Unable to convert from API 2 scene mode: " + sm);
        return null;
    }

    /**
     * Converts the API-related integer representation of the white balance to
     * the abstract representation.
     *
     * @param wb The integral representation.
     * @return The balance represented by the input integer, or {@code null} if
     *         it cannot be converted.
     */
    public static WhiteBalance whiteBalanceFromInt(int wb) {
        switch (wb) {
            case CONTROL_AWB_MODE_AUTO:
                return WhiteBalance.AUTO;
            case CONTROL_AWB_MODE_CLOUDY_DAYLIGHT:
                return WhiteBalance.CLOUDY_DAYLIGHT;
            case CONTROL_AWB_MODE_DAYLIGHT:
                return WhiteBalance.DAYLIGHT;
            case CONTROL_AWB_MODE_FLUORESCENT:
                return WhiteBalance.FLUORESCENT;
            case CONTROL_AWB_MODE_INCANDESCENT:
                return WhiteBalance.INCANDESCENT;
            case CONTROL_AWB_MODE_SHADE:
                return WhiteBalance.SHADE;
            case CONTROL_AWB_MODE_TWILIGHT:
                return WhiteBalance.TWILIGHT;
            case CONTROL_AWB_MODE_WARM_FLUORESCENT:
                return WhiteBalance.WARM_FLUORESCENT;
        }
        Log.w(TAG, "Unable to convert from API 2 white balance: " + wb);
        return null;
    }

    /* SPRD:Add for antibanding @{ */
    public static Antibanding antibandingFromInt(int antiband) {
        switch (antiband) {
            case STATISTICS_SCENE_FLICKER_NONE:
                return Antibanding.OFF;
            case STATISTICS_SCENE_FLICKER_50HZ:
                return Antibanding.ANTIBANDING_50HZ;
            case STATISTICS_SCENE_FLICKER_60HZ:
                return Antibanding.ANTIBANDING_60HZ;
        }
        Log.w(TAG, "Unable to convert from API 2 antibanding: " + antiband);
        return null;
    }
    /* @} */

    /* SPRD:Add for color effect Bug 474727 @{ */
    public static ColorEffect colorEffectFromInt(int ce) {
        switch (ce) {
            case CONTROL_EFFECT_MODE_OFF:
                return ColorEffect.NONE;
            case CONTROL_EFFECT_MODE_MONO:
                return ColorEffect.MONO;
            case CONTROL_EFFECT_MODE_NEGATIVE:
                return ColorEffect.NEGATIVE;
            case CONTROL_EFFECT_MODE_SEPIA:
                return ColorEffect.SEPIA;
            case CONTROL_EFFECT_MODE_AQUA:
                return ColorEffect.COLD;
            case CONTROL_EFFECT_MODE_SOLARIZE:
                return ColorEffect.ANTIQUE;
        }
        Log.w(TAG, "Unable to convert from API 2 color effect: " + ce);
        return null;
    }
    /* @} */

    /**
     * SPRD:fix bug 473462 add burst capture
     */
    public static BurstNumber burstNumberFromInt(int burstcount) {
        Log.i(TAG,"burstNumberFromInt burstcount="+burstcount);
        switch (burstcount) {
            case CONTROL_CAPTURE_MODE_ONE:
                return BurstNumber.ONE;
            case CONTROL_CAPTURE_MODE_THREE:
                return BurstNumber.THREE;
            case CONTROL_CAPTURE_MODE_SIX:
                return BurstNumber.SIX;
            case CONTROL_CAPTURE_MODE_TEN:
                return BurstNumber.TEN;
            case CONTROL_CAPTURE_MODE_NINETY_NINE:
                return BurstNumber.NINETYNINE;
        }
        Log.i(TAG, "Unable to convert from API 2 burstcount: " + burstcount);
        return null;
    }

    /*
     * SPRD Bug:474721 Feature:Contrast. @{
     */
    private void buildContrast(CameraCharacteristics p) {
        int[] contrast = p.get(CONTROL_AVAILABLE_CONTRAST);
        if (contrast != null) {
            for (int con : contrast) {
                Contrast equiv = contrastFromInt(con);
                if (equiv != null) {
                    mSupportedContrast.add(equiv);
                }
            }
        }
    }

    /*
     * SPRD Bug:474718 Feature:Metering. @{
     */
    private void buildMetering(CameraCharacteristics p) {
        int[] metering = p.get(CONTROL_AVAILABLE_METERING);
        if (metering != null) {
            for (int meter : metering) {
                Metering equiv = meteringFromInt(meter);
                if (equiv != null) {
                    mSupportedMetering.add(equiv);
                }
            }
        }
    }

    /*
     * SPRD Bug:474724 Feature:ISO. @{
     */
    private void buildISO(CameraCharacteristics p) {
        int[] iso = p.get(CONTROL_AVAILABLE_ISO);
        if (iso != null) {
            for (int so : iso) {
                ISO equiv = isoFromInt(so);
                if (equiv != null) {
                    mSupportedISO.add(equiv);
                }
            }
        }
    }

    public static Contrast contrastFromInt(int contrast) {
        switch (contrast) {
            case 0:
                return Contrast.CONTRAST_ZERO;
            case 1:
                return Contrast.CONTRAST_ONE;
            case 2:
                return Contrast.CONTRAST_TWO;
            case 3:
                return Contrast.CONTRAST_THREE;
            case 4:
                return Contrast.CONTRAST_FOUR;
            case 5:
                return Contrast.CONTRAST_FIVE;
            case 6:
                return Contrast.CONTRAST_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 Contrast: " + contrast);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474715 Feature:Brightness. @{
     */
    private void buildBrightNess(CameraCharacteristics p) {
        int[] brightness = p.get(SPRD_OEM_AVAILABLE_BRIGHTNESS);
        if (brightness != null) {
            for (int bright : brightness) {
                BrightNess equiv = brightnessFromInt(bright);
                if (equiv != null) {
                    mSupportedBrightNess.add(equiv);
                }
            }
        }
    }

    public static BrightNess brightnessFromInt(int brightness) {
        switch (brightness) {
            case CONTROL_BRIGHTNESS_ZERO:
                return BrightNess.BRIGHTNESS_ZERO;
            case CONTROL_BRIGHTNESS_ONE:
                return BrightNess.BRIGHTNESS_ONE;
            case CONTROL_BRIGHTNESS_TWO:
                return BrightNess.BRIGHTNESS_TWO;
            case CONTROL_BRIGHTNESS_THREE:
                return BrightNess.BRIGHTNESS_THREE;
            case CONTROL_BRIGHTNESS_FOUR:
                return BrightNess.BRIGHTNESS_FOUR;
            case CONTROL_BRIGHTNESS_FIVE:
                return BrightNess.BRIGHTNESS_FIVE;
            case CONTROL_BRIGHTNESS_SIX:
                return BrightNess.BRIGHTNESS_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 BrightNess: " + brightness);
        return null;
    }
    /* @} */

    public static ISO isoFromInt(int iso) {
        switch (iso) {
            case CONTROL_ISO_MODE_AUTO:
                return ISO.AUTO;
            case CONTROL_ISO_MODE_1600:
                return ISO.ISO_1600;
            case CONTROL_ISO_MODE_800:
                return ISO.ISO_800;
            case CONTROL_ISO_MODE_400:
                return ISO.ISO_400;
            case CONTROL_ISO_MODE_200:
                return ISO.ISO_200;
            case CONTROL_ISO_MODE_100:
                return ISO.ISO_100;
        }
        Log.w(TAG, "Unable to convert from API 2 ISO: " + iso);
        return null;
    }
    /* @} */

    public static Metering meteringFromInt(int metering) {
        switch (metering) {
            case CONTROL_METERING_FRAMEAVERAGE:
                return Metering.FRAMEAVERAGE;
            case CONTROL_METERING_CENTERWEIGHTED:
                return Metering.CENTERWEIGHTED;
            case CONTROL_METERING_SPOTMETERING:
                return Metering.SPOTMETERING;
        }
        Log.w(TAG, "Unable to convert from API 2 Metering: " + metering);
        return null;
    }
    /* @} */

    /*
     * SPRD Bug:474722 Feature:Saturation. @{
     */
    private void buildSaturation(CameraCharacteristics p) {
        int[] saturation = p.get(CONTROL_AVAILABLE_SATURATION);
        if (saturation != null) {
            for (int sat : saturation) {
                Saturation equiv = saturationFromInt(sat);
                if (equiv != null) {
                    mSupportedSaturation.add(equiv);
                }
            }
        }
    }

    public static Saturation saturationFromInt(int saturation) {
        switch (saturation) {
            case 0:
                return Saturation.SATURATION_ZERO;
            case 1:
                return Saturation.SATURATION_ONE;
            case 2:
                return Saturation.SATURATION_TWO;
            case 3:
                return Saturation.SATURATION_THREE;
            case 4:
                return Saturation.SATURATION_FOUR;
            case 5:
                return Saturation.SATURATION_FIVE;
            case 6:
                return Saturation.SATURATION_SIX;
        }
        Log.w(TAG, "Unable to convert from API 2 Saturation: " + saturation);
        return null;
    }
    /* @} */

}
