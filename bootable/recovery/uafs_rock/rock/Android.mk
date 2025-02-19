

ifeq ($(ROCK_GOTA_SUPPORT),yes)
LOCAL_PATH := $(call my-dir)
# DMC Prebuild APK
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := RockClient
LOCAL_SRC_FILES := RockGota-01.06.02.164311-mito-icon-p-release.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_CERTIFICATE := platform
#LOCAL_WITH_DEXPREOPT := false
LOCAL_PRIVILEGED_MODULE := true
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)

endif

