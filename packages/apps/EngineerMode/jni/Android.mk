LOCAL_PATH:= $(call my-dir)

#engineermode invoke native methods

include $(CLEAR_VARS)

LOCAL_MODULE        := libjni_engineermode

LOCAL_NDK_STL_VARIANT := stlport_static

ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sp9850s)
LOCAL_C_INCLUDES := vendor/sprd/proprietories-source/trustzone/libefuse
else ifeq ($(strip $(TARGET_BOARD_PLATFORM)),sp9860g)
LOCAL_C_INCLUDES := vendor/sprd/proprietories-source/trustzone/libefuse
else
LOCAL_C_INCLUDES := vendor/sprd/open-source/libs/libefuse
endif
      
#engineermode invoke native lib
LOCAL_SHARED_LIBRARIES := libcutils libutils liblog
LOCAL_SHARED_LIBRARIES += libefuse
LOCAL_LDFLAGS        := -llog

LOCAL_CPPFLAGS += $(JNI_CFLAGS)


LOCAL_CPP_EXTENSION := .cpp
LOCAL_SRC_FILES     := \
    src/jniutils.cpp \


include $(BUILD_SHARED_LIBRARY)
