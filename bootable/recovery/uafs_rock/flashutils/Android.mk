ifeq ($(ROCK_GOTA_SUPPORT),yes)
LOCAL_PATH := $(call my-dir)



# flash_image_gmobi
# =============================================
include $(CLEAR_VARS)
LOCAL_SRC_FILES := flash_image_gmobi.c

LOCAL_MODULE := flash_image_gmobi
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_LIBRARIES += libflashutils_gmobi

LOCAL_STATIC_LIBRARIES += libcutils libstdc++ libc


LOCAL_FORCE_STATIC_EXECUTABLE := true
include $(BUILD_EXECUTABLE)

# scripter_gmobi
# =============================================
include $(CLEAR_VARS)
LOCAL_SRC_FILES := scripter_gmobi.c
LOCAL_SRC_FILES += dirSetHierarchyPermissions.c

LOCAL_MODULE := scripter_gmobi
LOCAL_MODULE_TAGS := optional
LOCAL_STATIC_LIBRARIES += libupdater_gmobi libedify libminzip 

LOCAL_STATIC_LIBRARIES += libcutils libstdc++ libc


LOCAL_FORCE_STATIC_EXECUTABLE := true
include $(BUILD_EXECUTABLE)

# libimage_update_gmobi
# =============================================
include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libimage_update_gmobi 
LOCAL_MODULE_SUFFIX := .a
LOCAL_SRC_FILES := libimage_update_gmobi_$(ROCK_GOTA_ARCH).a 
LOCAL_MODULE_CLASS := STATIC_LIBRARIES

include $(BUILD_PREBUILT)


# libflashutils_gmobi
# =============================================
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libflashutils_gmobi
LOCAL_MODULE_SUFFIX := .a
LOCAL_SRC_FILES := libflashutils_gmobi_$(ROCK_GOTA_ARCH).a
LOCAL_MODULE_CLASS := STATIC_LIBRARIES

include $(BUILD_PREBUILT)

# libupdater_gmobi
# =============================================
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libupdater_gmobi
LOCAL_MODULE_SUFFIX := .a
LOCAL_SRC_FILES := libupdater_gmobi_$(ROCK_GOTA_ARCH).a
LOCAL_MODULE_CLASS := STATIC_LIBRARIES

include $(BUILD_PREBUILT)

endif
