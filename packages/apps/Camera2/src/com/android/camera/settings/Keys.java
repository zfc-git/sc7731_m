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

package com.android.camera.settings;

import android.content.Context;

import com.android.camera.app.LocationManager;
import com.android.camera.util.ApiHelper;
import com.android.camera2.R;



/**
 * Keys is a class for storing SharedPreferences keys and configuring
 * their defaults.
 *
 * For each key that has a default value and set of possible values, it
 * stores those defaults so they can be used by the SettingsManager
 * on lookup.  This step is optional, and it can be done anytime before
 * a setting is accessed by the SettingsManager API.
 */
public class Keys {

    public static final String KEY_RECORD_LOCATION = "pref_camera_recordlocation_key";
    public static final String KEY_VIDEO_QUALITY_BACK = "pref_video_quality_back_key";
    public static final String KEY_VIDEO_QUALITY_FRONT = "pref_video_quality_front_key";
    public static final String KEY_PICTURE_SIZE_BACK = "pref_camera_picturesize_back_key";
    public static final String KEY_PICTURE_SIZE_FRONT = "pref_camera_picturesize_front_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera_jpeg_quality_key";//SPRD:Modify for jpeg quality
    public static final String KEY_FOCUS_MODE = "pref_camera_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera_flashmode_key";
    public static final String KEY_VIDEOCAMERA_FLASH_MODE = "pref_camera_video_flashmode_key";
    public static final String KEY_SCENE_MODE = "pref_camera_scenemode_key";
    public static final String KEY_EXPOSURE = "pref_camera_exposure_key";
    public static final String KEY_VIDEO_EFFECT = "pref_video_effect_key";
    public static final String KEY_CAMERA_ID = "pref_camera_id_key";

    public static final String KEY_CAMERA_HDR = "pref_camera_hdr_key";
    public static final String KEY_CAMERA_HDR_PLUS = "pref_camera_hdr_plus_key";
    public static final String KEY_CAMERA_FIRST_USE_HINT_SHOWN =
            "pref_camera_first_use_hint_shown_key";
    public static final String KEY_VIDEO_FIRST_USE_HINT_SHOWN =
            "pref_video_first_use_hint_shown_key";
    public static final String KEY_STARTUP_MODULE_INDEX = "camera.startup_module";
    public static final String KEY_CAMERA_MODULE_LAST_USED =
            "pref_camera_module_last_used_index";
    public static final String KEY_CAMERA_PANO_ORIENTATION = "pref_camera_pano_orientation";
    public static final String KEY_CAMERA_GRID_LINES = "pref_camera_grid_lines";
    public static final String KEY_RELEASE_DIALOG_LAST_SHOWN_VERSION =
            "pref_release_dialog_last_shown_version";
    public static final String KEY_FLASH_SUPPORTED_BACK_CAMERA =
            "pref_flash_supported_back_camera";
    public static final String KEY_HDR_SUPPORT_MODE_BACK_CAMERA =
            "pref_hdr_support_mode_back_camera";
    public static final String KEY_UPGRADE_VERSION = "pref_upgrade_version";
    public static final String KEY_REQUEST_RETURN_HDR_PLUS = "pref_request_return_hdr_plus";
    public static final String KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING =
            "pref_should_show_refocus_viewer_cling";
    public static final String KEY_EXPOSURE_COMPENSATION_ENABLED =
            "pref_camera_exposure_compensation_key";

    public static final String KEY_CAMERA_CONTINUE_CAPTURE = "pref_camera_burst_key";//SPRD: fix bug 473462 add burst capture
    /**
     * Whether the user has chosen an aspect ratio on the first run dialog.
     */
    public static final String KEY_USER_SELECTED_ASPECT_RATIO = "pref_user_selected_aspect_ratio";

    public static final String KEY_COUNTDOWN_DURATION = "pref_camera_countdown_duration_key";
    public static final String KEY_HDR_PLUS_FLASH_MODE = "pref_hdr_plus_flash_mode";
    public static final String KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING =
            "pref_should_show_settings_button_cling";
    public static final String KEY_HAS_SEEN_PERMISSIONS_DIALOGS = "pref_has_seen_permissions_dialogs";
    /* SPRD: New Feature About Camera FreezePictureDisplay */
    public static final String KEY_FREEZE_FRAME_DISPLAY = "pref_freeze_frame_display_key";

    /* SPRD: New Feature About Antibanding */
    public static final String KEY_CAMER_ANTIBANDING = "pref_camera_antibanding_key";

    /* SPRD: New Feature About mirror */
    public static final String KEY_FRONT_CAMERA_MIRROR = "pref_front_camera_mirror_key";

    public static final String KEY_CAMERA_AI_DATECT = "pref_camera_ai_detect_key"; /* SPRD: New  Feature About face detect */
    public static final String CAMERA_AI_DATECT_VAL_OFF = "off";/* SPRD: New  Feature About face detect */
    public static final String KEY_CAMERA_COLOR_EFFECT = "pref_camera_color_effect_key";/* SPRD: New Feature About Color Effect */
    public static final String KEY_WHITE_BALANCE = "pref_camera_whitebalance_key";/* SPRD: New Feature About White Balance */

    public static final String KEY_CAMERA_STORAGE_PATH = "pref_camera_storage_path";/*SPRD :New Feature storage path */

    public static final String KEY_CAMERA_SHUTTER_SOUND = "pref_shutter_sound_key";//SPRD: fix bug 474665

    public static final String KEY_CAMERA_BEAUTY_ENTERED = "pref_camera_beauty_entered_key";//SPRD: fix bug 474672

    public static final String KEY_MAKEUP_MODE_LEVEL = "pref_makeup_mode_level_key"; // SPRD: fix bug 487525 save makeup level for makeup module

    /* SPRD: for bug 509708 add time lapse */
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL = "pref_video_time_lapse_frame_interval_key";
    public static final String TIME_LAPSE_DEFAULT_VALUE = "0";
    /* SPRD: New Feature About TimeStamp */
    public static final String KEY_CAMERA_TIME_STAMP = "pref_time_stamp_key";/* SPRD: New Feature Time Stamp */
    /*SPRD: New Feature zsl */
    public static final String KEY_CAMERA_ZSL_DISPLAY = "pref_camera_zsl_key";

    public static final String KEY_GIF_FLASH_MODE = "pref_gif_flashmode_key";//SPRD:New Feature Gif
    public static final String KEY_GIF_MODE_PIC_SIZE = "pref_gif_mode_pic_size_key";
    public static final String KEY_GIF_MODE_NUM_SIZE = "pref_gif_mode_pic_number_key";
    public static final String KEY_GIF_RESET = "pref_gif_reset_key";
    public static final String KEY_GIF_ADVANCED_SETTINGS = "pref_gif_advanced_settings";

    /**
     * Set some number of defaults for the defined keys.
     * It's not necessary to set all defaults.
     */
    public static void setDefaults(SettingsManager settingsManager, Context context) {
        settingsManager.setDefaults(KEY_COUNTDOWN_DURATION, 0,
            context.getResources().getIntArray(R.array.pref_countdown_duration));

        settingsManager.setDefaults(KEY_CAMERA_ID,
            context.getString(R.string.pref_camera_id_default),
            context.getResources().getStringArray(R.array.camera_id_entryvalues));

        settingsManager.setDefaults(KEY_SCENE_MODE,
            context.getString(R.string.pref_camera_scenemode_default),
            context.getResources().getStringArray(R.array.pref_camera_scenemode_entryvalues));

        settingsManager.setDefaults(KEY_FLASH_MODE,
            context.getString(R.string.pref_camera_flashmode_default),
            context.getResources().getStringArray(R.array.pref_camera_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_HDR_SUPPORT_MODE_BACK_CAMERA,
            context.getString(R.string.pref_camera_hdr_supportmode_none),
            context.getResources().getStringArray(R.array.pref_camera_hdr_supportmode_entryvalues));

        settingsManager.setDefaults(KEY_CAMERA_HDR, false);
        settingsManager.setDefaults(KEY_CAMERA_HDR_PLUS, false);

        settingsManager.setDefaults(KEY_CAMERA_FIRST_USE_HINT_SHOWN, true);

        settingsManager.setDefaults(KEY_FOCUS_MODE,
            context.getString(R.string.pref_camera_focusmode_default),
            context.getResources().getStringArray(R.array.pref_camera_focusmode_entryvalues));

        String videoQualityBackDefaultValue = context.getString(R.string.pref_video_quality_large);
        // TODO: We tweaked the default setting based on model string which is not ideal. Detecting
        // CamcorderProfile capability is a better way to get this job done. However,
        // |CamcorderProfile.hasProfile| needs camera id info. We need a way to provide camera id to
        // this method. b/17445274
        // Don't set the default resolution to be large if the device supports 4k video.
        if (ApiHelper.IS_NEXUS_6) {
            videoQualityBackDefaultValue = context.getString(R.string.pref_video_quality_medium);
        }
        settingsManager.setDefaults(
            KEY_VIDEO_QUALITY_BACK,
            videoQualityBackDefaultValue,
            context.getResources().getStringArray(R.array.pref_video_quality_entryvalues));
        if (!settingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_VIDEO_QUALITY_BACK)) {
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,
                                         Keys.KEY_VIDEO_QUALITY_BACK);
        }

        settingsManager.setDefaults(KEY_VIDEO_QUALITY_FRONT,
            context.getString(R.string.pref_video_quality_large),
            context.getResources().getStringArray(R.array.pref_video_quality_entryvalues));
        if (!settingsManager.isSet(SettingsManager.SCOPE_GLOBAL, Keys.KEY_VIDEO_QUALITY_FRONT)) {
            settingsManager.setToDefault(SettingsManager.SCOPE_GLOBAL,
                                         Keys.KEY_VIDEO_QUALITY_FRONT);
        }

        settingsManager.setDefaults(KEY_JPEG_QUALITY,
                context.getString(R.string.pref_camera_jpegh_quality_entry_value_super_hight),
                context.getResources().getStringArray(
                        R.array.pref_camera_jpeg_quality_entry_values));//SPRD:Modify for jpegquality

        settingsManager.setDefaults(KEY_VIDEOCAMERA_FLASH_MODE,
            context.getString(R.string.pref_camera_video_flashmode_default),
            context.getResources().getStringArray(
                R.array.pref_camera_video_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_VIDEO_EFFECT,
            context.getString(R.string.pref_video_effect_default),
            context.getResources().getStringArray(R.array.pref_video_effect_entryvalues));

        settingsManager.setDefaults(KEY_VIDEO_FIRST_USE_HINT_SHOWN, true);

        settingsManager.setDefaults(KEY_STARTUP_MODULE_INDEX, 0,
            context.getResources().getIntArray(R.array.camera_modes));

        settingsManager.setDefaults(KEY_CAMERA_MODULE_LAST_USED,
            context.getResources().getInteger(R.integer.camera_mode_photo),
            context.getResources().getIntArray(R.array.camera_modes));

        settingsManager.setDefaults(KEY_CAMERA_PANO_ORIENTATION,
            context.getString(R.string.pano_orientation_horizontal),
            context.getResources().getStringArray(
                R.array.pref_camera_pano_orientation_entryvalues));

        settingsManager.setDefaults(KEY_CAMERA_GRID_LINES, false);

        settingsManager.setDefaults(KEY_SHOULD_SHOW_REFOCUS_VIEWER_CLING, true);

        settingsManager.setDefaults(KEY_HDR_PLUS_FLASH_MODE,
            context.getString(R.string.pref_camera_hdr_plus_flashmode_default),
            context.getResources().getStringArray(
                R.array.pref_camera_hdr_plus_flashmode_entryvalues));

        settingsManager.setDefaults(KEY_SHOULD_SHOW_SETTINGS_BUTTON_CLING, true);

        settingsManager.setDefaults(KEY_CAMER_ANTIBANDING,
                context.getString(R.string.pref_camera_antibanding_entryvalue_50),
                context.getResources().getStringArray(R.array.pref_camera_antibanding_entryvalues));

        /* SPRD: Bug: 495676 set default value for video antibanding */
        settingsManager.setDefaults(KEY_VIDEO_ANTIBANDING,
                context.getString(R.string.pref_camera_antibanding_entryvalue_50),
                context.getResources().getStringArray(R.array.pref_camera_antibanding_entryvalues));

        /* SPRD: Add for FreezeDisplay */
        settingsManager.setDefaults(KEY_FREEZE_FRAME_DISPLAY, false);

        /* SPRD: Set Ddfault Value of face detect Feature*/
        settingsManager.setDefaults(KEY_CAMERA_AI_DATECT, context
                .getString(R.string.pref_ai_detect_entry_value_off), context.getResources()
                .getStringArray(R.array.pref_camera_ai_detect_entryvalues));

        /* SPRD: Set Ddfault Value of Storage Feature*/
        settingsManager.setDefaults(KEY_CAMERA_STORAGE_PATH,
                context.getString(R.string.storage_path_external_default),
                context.getResources().getStringArray(
                        R.array.pref_camera_storage_path_entryvalues));

        /* SPRD:Add for color effect Bug 474727 @{ */
        settingsManager.setDefaults(KEY_CAMERA_COLOR_EFFECT, context
                .getString(R.string.pref_camera_color_effect_entry_value_none), context
                .getResources()
                .getStringArray(R.array.pref_camera_color_effect_entryvalues));
        /* @} */

        // SPRD Bug:474701 Feature:Video Encoding Type.
        settingsManager.setDefaults(KEY_VIDEO_ENCODE_TYPE,
                context.getString(R.string.pref_video_encode_type_value_default),
                context.getResources().getStringArray(
                        R.array.pref_video_encode_type_entry_values));
        /* SPRD: Set Ddfault Value of White Balance Feature*/
        settingsManager.setDefaults(KEY_WHITE_BALANCE,
            context.getString(R.string.pref_camera_whitebalance_default),
            context.getResources().getStringArray(R.array.pref_camera_whitebalance_entryvalues));

        /* SPRD: fix bug 474665 add shutter sound switch @{ */
        settingsManager.setDefaults(KEY_CAMERA_SHUTTER_SOUND, true);

        settingsManager.setDefaults(KEY_CAMERA_CONTINUE_CAPTURE,
                context.getString(R.string.pref_camera_burst_entry_defaultvalue),
                context.getResources().getStringArray(R.array.pref_camera_burst_entryvalues));

        // SPRD Bug:474721 Feature:Contrast.
        settingsManager.setDefaults(KEY_CAMERA_CONTRAST,
                context.getString(R.string.pref_contrast_entry_defaultvalue),
                context.getResources().getStringArray(R.array.pref_camera_contrast_entry_values));

        // SPRD Bug:474715 Feature:Brightness.
        settingsManager.setDefaults(KEY_CAMERA_BRIGHTNESS,
                context.getString(R.string.pref_brightness_entry_defaultvalue),
                context.getResources().getStringArray(R.array.pref_camera_brightness_entryvalues));

        // SPRD Bug:474724 Feature:ISO.
        settingsManager.setDefaults(KEY_CAMERA_ISO,
                context.getString(R.string.pref_entry_value_auto),
                context.getResources().getStringArray(R.array.pref_camera_iso_entryvalues));

        // SPRD Bug:474718 Feature:Metering.
        settingsManager.setDefaults(KEY_CAMER_METERING,
                context.getString(R.string.pref_camera_metering_entry_value_center_weighted),
                context.getResources().getStringArray(R.array.pref_camera_metering_entryvalues));

        // SPRD Bug:474722 Feature:Saturation.
        settingsManager.setDefaults(KEY_CAMERA_SATURATION,
                context.getString(R.string.pref_saturation_entry_defaultvalue),
                context.getResources().getStringArray(R.array.pref_camera_saturation_entry_values));

        // SPRD: for bug 509708 add time lapse
        settingsManager.setDefaults(KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL,
                context.getString(R.string.pref_timelapse_entry_value_default),
                context.getResources().getStringArray(R.array.pref_video_timelapse_entry_values));

        // SPRD Bug:474696 Feature:Slow-Motion.
        settingsManager.setDefaults(KEY_VIDEO_SLOW_MOTION,
                context.getString(R.string.pref_entry_value_one),
                context.getResources().getStringArray(R.array.pref_video_slow_motion_entry_values));


        // SPRD Bug:513927 reset Makeup
        settingsManager.setDefaults(KEY_CAMERA_BEAUTY_ENTERED, false);

        // SPRD Bug:474847 Feature: TimeStamp
        settingsManager.setDefaults(KEY_CAMERA_TIME_STAMP, false);

        /* SPRD: Add for mirror */
        settingsManager.setDefaults(KEY_FRONT_CAMERA_MIRROR, false);

        specialMutexDefault(settingsManager);


        // SPRD Bug:474701 Feature:Video Encoding Type.
        settingsManager.setDefaults(KEY_VIDEO_ENCODE_TYPE,
                context.getString(R.string.pref_video_encode_type_value_default),
                context.getResources().getStringArray(
                        R.array.pref_video_encode_type_entry_values));

        // SPRD: add for GIF
        settingsManager.setDefaults(KEY_GIF_FLASH_MODE,
                context.getString(R.string.pref_camera_video_flashmode_default),
                context.getResources().getStringArray(
                        R.array.pref_camera_video_flashmode_entryvalues));

        // SPRD Bug:505155 Feature: zsl
        settingsManager.setDefaults(KEY_CAMERA_ZSL_DISPLAY, false);
    }

    /**
     * SPRD: mutex - now, front camera and back camera is not the same one to flash, which means
     * back camera and front camera saves the flash value in sharedPreference of itself. But
     * now, flash is not support in front camera and may support later. So, keep one value just
     * like HDR is not wise. For sometimes flash may be reset to off in setMutexPreference() in
     * CameraSettingsActivity, so here, we set default value of front camera to off to avoid
     * flash icon changes without rules.
     */
    public static void specialMutexDefault(SettingsManager settingsManager) {
        settingsManager.set("_preferences_camera_1", Keys.KEY_FLASH_MODE, "off");
    }

    /** Helper functions for some defined keys. */

    /**
     * Returns whether the camera has been set to back facing in settings.
     */
    public static boolean isCameraBackFacing(SettingsManager settingsManager,
                                             String moduleScope) {
        return settingsManager.isDefault(moduleScope, KEY_CAMERA_ID);
    }

    /**
     * Returns whether hdr plus mode is set on.
     */
    public static boolean isHdrPlusOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_HDR_PLUS);
    }

    /**
     * Returns whether hdr mode is set on.
     */
    public static boolean isHdrOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_HDR);
    }

    /**
     * Returns whether the app should return to hdr plus mode if possible.
     */
    public static boolean requestsReturnToHdrPlus(SettingsManager settingsManager,
                                                  String moduleScope) {
        return settingsManager.getBoolean(moduleScope, KEY_REQUEST_RETURN_HDR_PLUS);
    }

    /**
     * Returns whether grid lines are set on.
     */
    public static boolean areGridLinesOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                                          KEY_CAMERA_GRID_LINES);
    }

    /*
     * SPRD Add for new Feature FreezeDisplay Return the value if FreezeDisplay is on
     */
    public static boolean isFreezeDisplayOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                KEY_FREEZE_FRAME_DISPLAY);
    }

    /*
     * SPRD Bug:474701 Feature:Video Encoding Type. @{
     */
    public static final String KEY_VIDEO_ENCODE_TYPE = "pref_video_encode_type";
    public static final String VAL_VIDEO_ENCODE_TYPE_H264 = "h264";
    public static final String VAL_VIDEO_ENCODE_TYPE_MPEG = "mpeg";
    /* @} */

    /* SPRD Bug:495676 add antibanding for DV. */
    public static final String KEY_VIDEO_ANTIBANDING = "pref_video_antibanding_key";

    /**
     * SPRD:fix bug473462 add burst capture
     */
    public static boolean isBurstOff(SettingsManager settingsManager) {
        return "one".equals(settingsManager.getString(
                SettingsManager.SCOPE_GLOBAL, KEY_CAMERA_CONTINUE_CAPTURE));
    }

    // SPRD Bug:474721 Feature:Contrast.
    public static final String KEY_CAMERA_CONTRAST = "pref_camera_contrast_key";

    // SPRD Bug:474715 Feature:Brightness.
    public static final String KEY_CAMERA_BRIGHTNESS = "pref_camera_brightness_key";

    // SPRD Bug:474724 Feature:ISO.
    public static final String KEY_CAMERA_ISO = "pref_camera_iso_key";

    // SPRD Bug:474718 Feature:Metering.
    public static final String KEY_CAMER_METERING = "pref_camera_metering_key";

    // SPRD Bug:474722 Feature:Saturation.
    public static final String KEY_CAMERA_SATURATION = "pref_camera_saturation_key";

    /*
     * SPRD Bug:474694 Feature:Reset Settings. @{
     */
    public static final String KEY_CAMER_RESET = "pref_camera_reset_key";
    public static final String KEY_VIDEO_RESET = "pref_video_reset_key";

    // SPRD Bug:494930 Do not show Location Dialog when resetting settings.
    public static final int RECORD_LOCATION_ON = 1;
    public static final int RECORD_LOCATION_OFF = 0;

    public static void setManualExposureCompensation(SettingsManager settingsManager,
            boolean on) {
        settingsManager.set(SettingsManager.SCOPE_GLOBAL,
                KEY_EXPOSURE_COMPENSATION_ENABLED, on);
    }
    /* @} */

    /*
     * SPRD Bug:474696 Feature:Slow-Motion. @{
     */
    public static final String KEY_VIDEO_SLOW_MOTION = "pref_video_slow_motion_key";
    public static final String SLOWMOTION_DEFAULT_VALUE = "1";
    /* @} */

    public static boolean isTimeStampOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                KEY_CAMERA_TIME_STAMP);
    }

    /*
     * SPRD Bug:505155 Feature:zsl. @{
     */
    public static boolean isZslOn(SettingsManager settingsManager) {
        return settingsManager.getBoolean(SettingsManager.SCOPE_GLOBAL,
                KEY_CAMERA_ZSL_DISPLAY);
    }
     /* @} */

}

