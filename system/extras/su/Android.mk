LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_CFLAGS := -std=c11 -Wall -Werror

LOCAL_SRC_FILES:= su.c

LOCAL_MODULE:= su

LOCAL_MODULE_PATH := $(TARGET_OUT_OPTIONAL_EXECUTABLES)
LOCAL_MODULE_TAGS := debug

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_MODULE := se_nena_nenamark2_5
LOCAL_MODULE_PATH := $(TARGET_OUT)/preloadapp
LOCAL_SRC_FILES := se_nena_nenamark2_5.apk

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX:=$(COMMON_ANDROID_PACKAGE_SUFFIX)

LOCAL_CERTIFICATE:= platform

include $(BUILD_PREBUILT)
include $(CLEAR_VARS)

LOCAL_MODULE := VoiceCycle
LOCAL_MODULE_PATH := $(TARGET_OUT)/preloadapp
LOCAL_SRC_FILES := VoiceCycle.apk

LOCAL_MODULE_TAGS := eng debug
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX:=$(COMMON_ANDROID_PACKAGE_SUFFIX)

LOCAL_CERTIFICATE:= platform

include $(BUILD_PREBUILT)
