#Create by Spreadst for secure boot

LOCAL_PATH:= $(call my-dir)

SECURE_BOOT_INCLUDE_DIR ?= \
    vendor/sprd/proprietories-source/sprd_verify \
    vendor/sprd/open-source/libs/libefuse \
    system/core/fs_mgr/include/ \
    bootable/recovery/mtdutils/

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    sprd_sb_verifier.c \
    sprd_sb_verifier_fsmgr.c

LOCAL_STATIC_LIBRARIES := \
    libefuse \
    libfs_mgr \
    libmtdutils \
    libsprd_verify

LOCAL_C_INCLUDES := \
        $(SECURE_BOOT_INCLUDE_DIR)

#LOCAL_C_INCLUDES += system/core/fs_mgr/include/
#LOCAL_C_INCLUDES += bootable/recovery/mtdutils/

LOCAL_MODULE := libsprd_sb_verifier

include $(BUILD_STATIC_LIBRARY)

