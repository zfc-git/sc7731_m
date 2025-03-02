#
# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This is a build configuration for a full-featured build of the
# Open-Source part of the tree. It's geared toward a US-centric
# build of the emulator, but all those aspects can be overridden
# in inherited configurations.

PRODUCT_PACKAGES := \
    drmserver \
    libdrmframework \
    libdrmframework_jni \
    libdrmomaplugin \
    libWnnEngDic \
    libWnnJpnDic \
    libwnndict \
    WAPPushManager
# SPRD:
# OpenWnn has been removed as features and marketing request

PRODUCT_PACKAGES += \
    Galaxy4 \
    HoloSpiralWallpaper \
    LiveWallpapers \
    LiveWallpapersPicker \
    MagicSmokeWallpapers \
    NoiseField \
    PhaseBeam \
    PhotoTable

# Additional settings used in all AOSP builds
PRODUCT_PROPERTY_OVERRIDES := \
    ro.config.ringtone=Ring_Synth_04.ogg \
    ro.config.notification_sound=pixiedust.ogg

# Put en_US first in the list, so make it default.
PRODUCT_LOCALES := en_US

# Include drawables for all densities
PRODUCT_AAPT_CONFIG := normal

# Get some sounds
$(call inherit-product-if-exists, frameworks/base/data/sounds/AllAudio.mk)

# Get the TTS language packs
$(call inherit-product-if-exists, external/svox/pico/lang/all_pico_languages.mk)

# Get a list of languages.
$(call inherit-product, $(SRC_TARGET_DIR)/product/locales_full.mk)

# Get everything else from the parent package
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_no_telephony.mk)

# add widevine in none gms version
PRODUCT_PROPERTY_OVERRIDES += \
        drm.service.enabled=true

PRODUCT_PACKAGES += \
        com.google.widevine.software.drm.xml \
        com.google.widevine.software.drm \
        libdrmwvmplugin \
        libwvm \
        libWVStreamControlAPI_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
        libwvdrm_L$(BOARD_WIDEVINE_OEMCRYPTO_LEVEL) \
        liboemcrypto \
        libdrmdecrypt \
        libwvdecryptcommon \
        libwvdrmengine
