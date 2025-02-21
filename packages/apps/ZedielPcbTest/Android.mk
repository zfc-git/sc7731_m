ifneq ($(TARGET_SIMULATOR),true)

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES  := libarity android-support-v4 guava

$(shell cp $(LOCAL_PATH)/api/$(PLATFORM_SDK_VERSION)/SerialNoUtil.java $(LOCAL_PATH)/src/com/zediel/util/SerialNoUtil.java)

#LOCAL_PREBUILT_JNI_LIBS := \
#                          libs/arm64-v8a/libserial_port.so \
#LOCAL_MULTILIB := 64

LOCAL_SRC_FILES := $(call all-java-files-under, src)

#LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := ZedielPcbTest

LOCAL_DEX_PREOPT := false

LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled
LOCAL_PRIVATE_PLATFORM_APIS := true

include $(BUILD_PACKAGE)

endif
