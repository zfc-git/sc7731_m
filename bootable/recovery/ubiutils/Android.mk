#Created by Spreadst

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	ubiutils.c

LOCAL_C_INCLUDES += kernel/include/uapi/mtd

LOCAL_MODULE := libubiutils

LOCAL_STATIC_LIBRARIES := libmtdutils

include $(BUILD_STATIC_LIBRARY)

