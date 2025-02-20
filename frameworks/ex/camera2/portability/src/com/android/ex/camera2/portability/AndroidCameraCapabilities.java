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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The subclass of {@link CameraCapabilities} for Android Camera 1 API.
 */
class AndroidCameraCapabilities extends CameraCapabilities {

    private static Log.Tag TAG = new Log.Tag("AndCamCapabs");

    /** Conversion from ratios to percentages. */
    public static final float ZOOM_MULTIPLIER = 100f;

    private FpsComparator mFpsComparator = new FpsComparator();
    private SizeComparator mSizeComparator = new SizeComparator();

    AndroidCameraCapabilities(Camera.Parameters p) {
        super(new Stringifier());
        mMaxExposureCompensation = p.getMaxExposureCompensation();
        mMinExposureCompensation = p.getMinExposureCompensation();
        mExposureCompensationStep = p.getExposureCompensationStep();
        mMaxNumOfFacesSupported = p.getMaxNumDetectedFaces();
        mMaxNumOfMeteringArea = p.getMaxNumMeteringAreas();
        mPreferredPreviewSizeForVideo = new Size(p.getPreferredPreviewSizeForVideo());
        mSupportedPreviewFormats.addAll(p.getSupportedPreviewFormats());
        mSupportedPhotoFormats.addAll(p.getSupportedPictureFormats());
        mHorizontalViewAngle = p.getHorizontalViewAngle();
        mVerticalViewAngle = p.getVerticalViewAngle();
        buildPreviewFpsRange(p);
        buildPreviewSizes(p);
        buildVideoSizes(p);
        buildPictureSizes(p);
        buildSceneModes(p);
        buildFlashModes(p);
        buildFocusModes(p);
        buildWhiteBalances(p);
        buidAntibanding(p);//SPRD:Add for antibanding
        buildColorEffect(p);//SPRD:Add for color effect Bug 474727

        // SPRD Bug:474721 Feature:Contrast.
        buildContrast(p);

        // SPRD Bug:474715 Feature:Brightness.
        buildBrightness(p);

        // SPRD Bug:474718 Feature:Metering.
        buildMetering(p);

        // SPRD Bug:474722 Feature:Saturation.
        buildSaturation(p);

        // SPRD Bug:474696 Feature:Slow-Motion.
        buildVideoSlowMotion(p);

        if (p.isZoomSupported()) {
            mMaxZoomRatio = p.getZoomRatios().get(p.getMaxZoom()) / ZOOM_MULTIPLIER;
            mSupportedFeatures.add(Feature.ZOOM);
        }
        if (p.isVideoSnapshotSupported()) {
            mSupportedFeatures.add(Feature.VIDEO_SNAPSHOT);
        }
        if (p.isAutoExposureLockSupported()) {
            mSupportedFeatures.add(Feature.AUTO_EXPOSURE_LOCK);
        }
        if (p.isAutoWhiteBalanceLockSupported()) {
            mSupportedFeatures.add(Feature.AUTO_WHITE_BALANCE_LOCK);
        }
        if (supports(FocusMode.AUTO)) {
            mMaxNumOfFocusAreas = p.getMaxNumFocusAreas();
            if (mMaxNumOfFocusAreas > 0) {
                mSupportedFeatures.add(Feature.FOCUS_AREA);
            }
        }
        if (mMaxNumOfMeteringArea > 0) {
            mSupportedFeatures.add(Feature.METERING_AREA);
        }

        // SPRD Bug:474724 Feature:ISO.
        buildISO(p);
    }

    AndroidCameraCapabilities(AndroidCameraCapabilities src) {
        super(src);
    }

    private void buildPreviewFpsRange(Camera.Parameters p) {
        List<int[]> supportedPreviewFpsRange = p.getSupportedPreviewFpsRange();
        if (supportedPreviewFpsRange != null) {
            mSupportedPreviewFpsRange.addAll(supportedPreviewFpsRange);
        }
        Collections.sort(mSupportedPreviewFpsRange, mFpsComparator);
    }

    private void buildPreviewSizes(Camera.Parameters p) {
        List<Camera.Size> supportedPreviewSizes = p.getSupportedPreviewSizes();
        if (supportedPreviewSizes != null) {
            for (Camera.Size s : supportedPreviewSizes) {
                mSupportedPreviewSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedPreviewSizes, mSizeComparator);
    }

    private void buildVideoSizes(Camera.Parameters p) {
        List<Camera.Size> supportedVideoSizes = p.getSupportedVideoSizes();
        if (supportedVideoSizes != null) {
            for (Camera.Size s : supportedVideoSizes) {
                mSupportedVideoSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedVideoSizes, mSizeComparator);
    }

    private void buildPictureSizes(Camera.Parameters p) {
        List<Camera.Size> supportedPictureSizes = p.getSupportedPictureSizes();
        if (supportedPictureSizes != null) {
            for (Camera.Size s : supportedPictureSizes) {
                mSupportedPhotoSizes.add(new Size(s.width, s.height));
            }
        }
        Collections.sort(mSupportedPhotoSizes, mSizeComparator);

    }

    private void buildSceneModes(Camera.Parameters p) {
        List<String> supportedSceneModes = p.getSupportedSceneModes();
        if (supportedSceneModes != null) {
            for (String scene : supportedSceneModes) {
                if (Camera.Parameters.SCENE_MODE_AUTO.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.AUTO);
                } else if (Camera.Parameters.SCENE_MODE_ACTION.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.ACTION);
                } else if (Camera.Parameters.SCENE_MODE_BARCODE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.BARCODE);
                } else if (Camera.Parameters.SCENE_MODE_BEACH.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.BEACH);
                } else if (Camera.Parameters.SCENE_MODE_CANDLELIGHT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.CANDLELIGHT);
                } else if (Camera.Parameters.SCENE_MODE_FIREWORKS.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.FIREWORKS);
                } else if (Camera.Parameters.SCENE_MODE_HDR.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.HDR);
                } else if (Camera.Parameters.SCENE_MODE_LANDSCAPE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.LANDSCAPE);
                } else if (Camera.Parameters.SCENE_MODE_NIGHT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.NIGHT);
                } else if (Camera.Parameters.SCENE_MODE_NIGHT_PORTRAIT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.NIGHT_PORTRAIT);
                } else if (Camera.Parameters.SCENE_MODE_PARTY.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.PARTY);
                } else if (Camera.Parameters.SCENE_MODE_PORTRAIT.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.PORTRAIT);
                } else if (Camera.Parameters.SCENE_MODE_SNOW.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SNOW);
                } else if (Camera.Parameters.SCENE_MODE_SPORTS.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SPORTS);
                } else if (Camera.Parameters.SCENE_MODE_STEADYPHOTO.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.STEADYPHOTO);
                } else if (Camera.Parameters.SCENE_MODE_SUNSET.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.SUNSET);
                } else if (Camera.Parameters.SCENE_MODE_THEATRE.equals(scene)) {
                    mSupportedSceneModes.add(SceneMode.THEATRE);
                }
            }
        }
    }

    private void buildFlashModes(Camera.Parameters p) {
        List<String> supportedFlashModes = p.getSupportedFlashModes();
        if (supportedFlashModes == null) {
            // Camera 1 will return NULL if no flash mode is supported.
            mSupportedFlashModes.add(FlashMode.NO_FLASH);
        } else {
            for (String flash : supportedFlashModes) {
                if (Camera.Parameters.FLASH_MODE_AUTO.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.AUTO);
                } else if (Camera.Parameters.FLASH_MODE_OFF.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.OFF);
                } else if (Camera.Parameters.FLASH_MODE_ON.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.ON);
                } else if (Camera.Parameters.FLASH_MODE_RED_EYE.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.RED_EYE);
                } else if (Camera.Parameters.FLASH_MODE_TORCH.equals(flash)) {
                    mSupportedFlashModes.add(FlashMode.TORCH);
                }
            }
        }
    }

    private void buildFocusModes(Camera.Parameters p) {
        List<String> supportedFocusModes = p.getSupportedFocusModes();
        if (supportedFocusModes != null) {
            for (String focus : supportedFocusModes) {
                if (Camera.Parameters.FOCUS_MODE_AUTO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.AUTO);
                } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.CONTINUOUS_PICTURE);
                } else if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.CONTINUOUS_VIDEO);
                } else if (Camera.Parameters.FOCUS_MODE_EDOF.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.EXTENDED_DOF);
                } else if (Camera.Parameters.FOCUS_MODE_FIXED.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.FIXED);
                } else if (Camera.Parameters.FOCUS_MODE_INFINITY.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.INFINITY);
                } else if (Camera.Parameters.FOCUS_MODE_MACRO.equals(focus)) {
                    mSupportedFocusModes.add(FocusMode.MACRO);
                }
            }
        }
    }

    private void buildWhiteBalances(Camera.Parameters p) {
        /*SPRD:Modify for whitebalance @{
        List<String> supportedWhiteBalances = p.getSupportedFocusModes();
        */
        List<String> supportedWhiteBalances = p.getSupportedWhiteBalance();
        Log.d(TAG, "supportedWhiteBalances" + supportedWhiteBalances);
        /* @} */
        if (supportedWhiteBalances != null) {
            for (String wb : supportedWhiteBalances) {
                if (Camera.Parameters.WHITE_BALANCE_AUTO.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.AUTO);
                } else if (Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.CLOUDY_DAYLIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_DAYLIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.DAYLIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_FLUORESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.FLUORESCENT);
                } else if (Camera.Parameters.WHITE_BALANCE_INCANDESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.INCANDESCENT);
                } else if (Camera.Parameters.WHITE_BALANCE_SHADE.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.SHADE);
                } else if (Camera.Parameters.WHITE_BALANCE_TWILIGHT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.TWILIGHT);
                } else if (Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT.equals(wb)) {
                    mSupportedWhiteBalances.add(WhiteBalance.WARM_FLUORESCENT);
                }
            }
        }
    }

    /* SPRD:Add for antibanding @{ */
    private void buidAntibanding(Camera.Parameters p) {
        List<String> supportedAntibanding = p.getSupportedAntibanding();
        Log.d(TAG, "supportedAntibanding" + supportedAntibanding);
        if (supportedAntibanding != null) {
            for (String antibanding : supportedAntibanding) {
                if (Camera.Parameters.ANTIBANDING_AUTO.equals(antibanding)) {
                    mSupportedAntibanding.add(Antibanding.AUTO);
                } else if (Camera.Parameters.ANTIBANDING_50HZ.equals(antibanding)) {
                    mSupportedAntibanding.add(Antibanding.ANTIBANDING_50HZ);
                } else if (Camera.Parameters.ANTIBANDING_60HZ.equals(antibanding)) {
                    mSupportedAntibanding.add(Antibanding.ANTIBANDING_60HZ);
                } else if (Camera.Parameters.ANTIBANDING_OFF.equals(antibanding)) {
                    mSupportedAntibanding.add(Antibanding.OFF);
                }
            }
        }
    }
    /* @} */

    /* SPRD:Add for color effect Bug 474727 @{ */
    private void buildColorEffect(Camera.Parameters p) {
        List<String> supportedColorEffects = p.getSupportedColorEffects();
        Log.d(TAG, "supportedColorEffects" + supportedColorEffects);
        if (supportedColorEffects != null) {
            for (String colorEffect : supportedColorEffects) {
                Log.d(TAG, "colorEffect" + colorEffect);
                if (Camera.Parameters.EFFECT_NONE.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.NONE);
                } else if (Camera.Parameters.EFFECT_MONO.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.MONO);
                } else if (Camera.Parameters.EFFECT_NEGATIVE.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.NEGATIVE);
                } else if (Camera.Parameters.EFFECT_SEPIA.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.SEPIA);
                } else if (Camera.Parameters.EFFECT_AQUA.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.COLD);
                } else if (Camera.Parameters.EFFECT_SOLARIZE.equals(colorEffect)) {
                    mSupportedColorEffects.add(ColorEffect.ANTIQUE);
                }
            }
        }
    }
    /* @} */

    private static class FpsComparator implements Comparator<int[]> {
        @Override
        public int compare(int[] fps1, int[] fps2) {
            return (fps1[0] == fps2[0] ? fps1[1] - fps2[1] : fps1[0] - fps2[0]);
        }
    }

    private static class SizeComparator implements Comparator<Size> {

        @Override
        public int compare(Size size1, Size size2) {
            return (size1.width() == size2.width() ? size1.height() - size2.height() :
                    size1.width() - size2.width());
        }
    }

    // SPRD Bug:474721 Feature:Contrast.
    public static final String VALUE_ZERO = "0";
    public static final String VALUE_ONE = "1";
    public static final String VALUE_TWO = "2";
    public static final String VALUE_THREE = "3";
    public static final String VALUE_FOUR = "4";
    public static final String VALUE_FIVE = "5";
    public static final String VALUE_SIX = "6";
    public static final String VALUE_TEN = "10";
    public static final String VALUE_NINETYNINE = "99";

    // SPRD Bug:474721 Feature:Contrast.
    private void buildContrast(Camera.Parameters p) {
        List<String> supportedContrast = p.getSupportedContrast();
        Log.d(TAG, "supportedContrast" + supportedContrast);
        if (supportedContrast != null) {
            for (String contrast : supportedContrast) {
                if (VALUE_ZERO.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_ZERO);
                } else if (VALUE_ONE.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_ONE);
                } else if (VALUE_TWO.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_TWO);
                } else if (VALUE_THREE.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_THREE);
                } else if (VALUE_FOUR.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_FOUR);
                } else if (VALUE_FIVE.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_FIVE);
                } else if (VALUE_SIX.equals(contrast)) {
                    mSupportedContrast.add(CameraCapabilities.Contrast.CONTRAST_SIX);
                }
            }
        }

    }

    // SPRD Bug:474715 Feature:Brightness.
    private void buildBrightness(Camera.Parameters p) {
        List<String> supportedBrightness = p.getSupportedBrightness();
        Log.d(TAG, "supportedBrightness" + supportedBrightness);
        if (supportedBrightness != null) {
            for (String brightness : supportedBrightness) {
                if (VALUE_ZERO.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_ZERO);
                } else if (VALUE_ONE.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_ONE);
                } else if (VALUE_TWO.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_TWO);
                } else if (VALUE_THREE.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_THREE);
                } else if (VALUE_FOUR.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_FOUR);
                } else if (VALUE_FIVE.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_FIVE);
                } else if (VALUE_SIX.equals(brightness)) {
                    mSupportedBrightNess.add(CameraCapabilities.BrightNess.BRIGHTNESS_SIX);
                }
            }
        }
    }

    // SPRD Bug:474724 Feature:ISO.
    private void buildISO(Camera.Parameters p) {
        List<String> supportedISO = p.getSupportedISO();
        Log.d(TAG, "supportedISO" + supportedISO);
        if (supportedISO != null) {
            for (String iso : supportedISO) {
                if (iso != null) {
                    if (Camera.Parameters.ISO_AUTO.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.AUTO);
                    } else if (Camera.Parameters.ISO_100.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.ISO_100);
                    } else if (Camera.Parameters.ISO_200.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.ISO_200);
                    } else if (Camera.Parameters.ISO_400.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.ISO_400);
                    } else if (Camera.Parameters.ISO_800.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.ISO_800);
                    } else if (Camera.Parameters.ISO_1600.equals(iso)) {
                        mSupportedISO.add(CameraCapabilities.ISO.ISO_1600);
                    }
                }
            }
        }
    }

    // SPRD Bug:474718 Feature:Metering.
    private void buildMetering(Camera.Parameters p) {
        List<String> supportedMetering = p.getSupportedMeteringMode();
        Log.d(TAG, "supportedMetering = " + supportedMetering);
        if (supportedMetering != null) {
            for (String me : supportedMetering) {
                if (Camera.Parameters.AUTO_EXPOSURE_FRAME_AVG.equals(me)) {
                    mSupportedMetering.add(Metering.FRAMEAVERAGE);
                } else if (Camera.Parameters.AUTO_EXPOSURE_CENTER_WEIGHTED.equals(me)) {
                    mSupportedMetering.add(Metering.CENTERWEIGHTED);
                } else if (Camera.Parameters.AUTO_EXPOSURE_SPOT_METERING.equals(me)) {
                    mSupportedMetering.add(Metering.SPOTMETERING);
                }
            }
        }
    }

    // SPRD Bug:474722 Feature:Saturation.
    private void buildSaturation(Camera.Parameters p) {
        List<String> supportedSaturation = p.getSupportedSaturation();
        Log.d(TAG, "supportedSaturation" + supportedSaturation);
        if (supportedSaturation != null) {
            for (String saturation : supportedSaturation) {
                if (VALUE_ZERO.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_ZERO);
                } else if (VALUE_ONE.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_ONE);
                } else if (VALUE_TWO.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_TWO);
                } else if (VALUE_THREE.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_THREE);
                } else if (VALUE_FOUR.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_FOUR);
                } else if (VALUE_FIVE.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_FIVE);
                } else if (VALUE_SIX.equals(saturation)) {
                    mSupportedSaturation.add(CameraCapabilities.Saturation.SATURATION_SIX);
                }
            }
        }
    }
    /* @} */

    /*
     * SPRD Bug:474696 Feature:Slow-Motion. @{
     */
    private void buildVideoSlowMotion(Camera.Parameters p) {
        List<String> supportedSlowMotion = p.getSupportedSlowmotion();
        Log.d(TAG, "supportedSlowMotion = " + supportedSlowMotion);
        if (supportedSlowMotion != null) {
            for (String slowmotion : supportedSlowMotion) {
                if (slowmotion != null) {
                    mSupportedSlowMotion.add(slowmotion);
                }
            }
        }
    }
    /* @} */
}
