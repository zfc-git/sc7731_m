#Created by Spreadst

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	vfat_format.c

LOCAL_SRC_FILES += ../../../system/core/toolbox/newfs_msdos.c

LOCAL_MODULE := libvfat_format

include $(BUILD_STATIC_LIBRARY)

