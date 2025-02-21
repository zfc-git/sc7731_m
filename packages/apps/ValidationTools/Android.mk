ifneq ($(TARGET_SIMULATOR),true)

LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := telephony-common

LOCAL_JAVA_LIBRARIES += com.broadcom.bt

LOCAL_JAVA_LIBRARIES := org.apache.http.legacy

LOCAL_JNI_SHARED_LIBRARIES := libfmjni

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := ValidationTools

LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

endif
