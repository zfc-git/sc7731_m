# Copyright (C) 2007 The Android Open Source Project
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

LOCAL_PATH := $(call my-dir)

# SPRD: add for secure boot @{
ifeq ($(BOARD_SECURE_BOOT_ENABLE), true)
  RECOVERY_COMMON_CGLAGS = -DSECURE_BOOT_ENABLE
endif
ifneq ($(ADDITIONAL_STATIC_LIB_DIR),)

include $(CLEAR_VARS)
LOCAL_MODULE := libsprd_verify
LOCAL_MODULE_STEM := libsprd_verify.a
LOCAL_MODULE_CLASS := STATIC_LIBRARIES
LOCAL_SRC_FILES := ../../$(ADDITIONAL_STATIC_LIB_DIR)/libsprd_verify.a
include $(BUILD_PREBUILT)

endif
# @}

#recovery config:update from internal storage
ifeq ($(ENABLE_INTERNAL_STORAGE),true)
$(warning warning enable internal storage)
USE_INTERNAL_STORAGE := -DENABLE_INTERNAL_STORAGE
endif

include $(CLEAR_VARS)

#recovery config:update from internal storage
LOCAL_CFLAGS += $(USE_INTERNAL_STORAGE)

LOCAL_SRC_FILES := fuse_sideload.c

LOCAL_CFLAGS := -O2 -g -DADB_HOST=0 -Wall -Wno-unused-parameter
LOCAL_CFLAGS += -D_XOPEN_SOURCE -D_GNU_SOURCE

LOCAL_MODULE := libfusesideload

LOCAL_STATIC_LIBRARIES := libcutils libc libmincrypt
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

#recovery config:update from internal storage
LOCAL_CFLAGS += $(USE_INTERNAL_STORAGE)

#recovery config:update from internal storage
ifeq ($(ENABLE_INTERNAL_STORAGE),true)
LOCAL_CFLAGS += -DENABLE_INTERNAL_STORAGE
endif

#SPRD:add show_info function
LOCAL_SRC_FILES := \
    adb_install.cpp \
    asn1_decoder.cpp \
    bootloader.cpp \
    device.cpp \
    fuse_sdcard_provider.c \
    install.cpp \
    recovery.cpp \
    roots.cpp \
    screen_ui.cpp \
    ui.cpp \
    verifier.cpp \
    show_info.c

ifeq ($(ROCK_GOTA_SUPPORT),yes)
	LOCAL_CFLAGS += -DGMT_RECOVERY_MODE_ROCK_GOTA_SUPPORT
endif
LOCAL_MODULE := recovery

LOCAL_FORCE_STATIC_EXECUTABLE := true

ifeq ($(HOST_OS),linux)
LOCAL_REQUIRED_MODULES := mkfs.f2fs
endif

RECOVERY_API_VERSION := 3
RECOVERY_FSTAB_VERSION := 2
LOCAL_CFLAGS += -DRECOVERY_API_VERSION=$(RECOVERY_API_VERSION)
LOCAL_CFLAGS += -Wno-unused-parameter

LOCAL_C_INCLUDES += \
    system/vold \
    system/extras/ext4_utils \
    system/core/adb \

LOCAL_STATIC_LIBRARIES := \
    libext4_utils_static \
    libsparse_static \
    libminzip \
    libz \
    libmtdutils \
    libmincrypt \
    libminadbd \
    libfusesideload \
    libminui \
    libpng \
    libfs_mgr \
    libbase \
    libcutils \
    liblog \
    libselinux \
    libstdc++ \
    libm \
    libc

# SPRD: add for support format vfat @{
LOCAL_STATIC_LIBRARIES += \
    libvfat_format
# @}

# SPRD: add for ubi support
LOCAL_STATIC_LIBRARIES += \
    libubiutils

# SPRD: add fota support
ifeq ($(strip $(FOTA_UPDATE_SUPPORT)), true)
LOCAL_CFLAGS += -DFOTA_UPDATE_SUPPORT
LOCAL_STATIC_LIBRARIES += libfotaupdate
endif

# SPRD: add for secure boot
LOCAL_CFLAGS += $(RECOVERY_COMMON_CGLAGS)
LOCAL_STATIC_LIBRARIES += \
        libsprd_sb_verifier \
        libsprd_verify

ifeq ($(TARGET_USERIMAGES_USE_EXT4), true)
    LOCAL_CFLAGS += -DUSE_EXT4
    LOCAL_C_INCLUDES += system/extras/ext4_utils
    LOCAL_STATIC_LIBRARIES += libext4_utils_static libz
endif

LOCAL_MODULE_PATH := $(TARGET_RECOVERY_ROOT_OUT)/sbin

ifeq ($(TARGET_RECOVERY_UI_LIB),)
  LOCAL_SRC_FILES += default_device.cpp
else
  LOCAL_STATIC_LIBRARIES += $(TARGET_RECOVERY_UI_LIB)
endif

include $(BUILD_EXECUTABLE)

# All the APIs for testing
include $(CLEAR_VARS)
LOCAL_MODULE := libverifier
LOCAL_MODULE_TAGS := tests
LOCAL_SRC_FILES := \
    asn1_decoder.cpp
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := verifier_test
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_MODULE_TAGS := tests
LOCAL_CFLAGS += -Wno-unused-parameter
LOCAL_SRC_FILES := \
    verifier_test.cpp \
    asn1_decoder.cpp \
    verifier.cpp \
    ui.cpp
LOCAL_STATIC_LIBRARIES := \
    libmincrypt \
    libminui \
    libminzip \
    libcutils \
    libstdc++ \
    libc
include $(BUILD_EXECUTABLE)

# SPRD: add fota support
ifeq ($(strip $(FOTA_UPDATE_SUPPORT)), true)
include $(CLEAR_VARS)
LOCAL_PREBUILT_LIBS += libfotaupdate.a
include $(BUILD_MULTI_PREBUILT)
endif

# SPRD: modify for ubi support
# SPRD: modify for secure boot
# SPRD: modify for repartition
# SPRD: modify for backup and resume data
include $(LOCAL_PATH)/minui/Android.mk \
    $(LOCAL_PATH)/minzip/Android.mk \
    $(LOCAL_PATH)/minadbd/Android.mk \
    $(LOCAL_PATH)/mtdutils/Android.mk \
    $(LOCAL_PATH)/tests/Android.mk \
    $(LOCAL_PATH)/tools/Android.mk \
    $(LOCAL_PATH)/edify/Android.mk \
    $(LOCAL_PATH)/uncrypt/Android.mk \
    $(LOCAL_PATH)/updater/Android.mk \
    $(LOCAL_PATH)/applypatch/Android.mk \
    $(LOCAL_PATH)/nvmerge/Android.mk \
    $(LOCAL_PATH)/splmerge/Android.mk \
    $(LOCAL_PATH)/ubiutils/Android.mk \
    $(LOCAL_PATH)/repart/Android.mk \
    $(LOCAL_PATH)/pack/Android.mk \
    $(LOCAL_PATH)/sprd_sb_verifier/Android.mk\
    $(LOCAL_PATH)/uafs_rock/Android.mk \
    $(LOCAL_PATH)/vfat/Android.mk
