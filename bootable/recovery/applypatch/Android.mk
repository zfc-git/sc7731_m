# Copyright (C) 2008 The Android Open Source Project
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
include $(CLEAR_VARS)

#recovery config:update from internal storage
LOCAL_CFLAGS += $(USE_INTERNAL_STORAGE)

LOCAL_SRC_FILES := applypatch.c bspatch.c freecache.c imgpatch.c utils.c
LOCAL_MODULE := libapplypatch
LOCAL_MODULE_TAGS := eng
LOCAL_C_INCLUDES += external/bzip2 external/zlib bootable/recovery
LOCAL_STATIC_LIBRARIES += libmtdutils libmincrypt libbz libz
# SPRD: add for ubi support
LOCAL_STATIC_LIBRARIES += \
    libubiutils

# SPRD: add for secure boot
LOCAL_CFLAGS += $(RECOVERY_COMMON_CGLAGS)
LOCAL_STATIC_LIBRARIES += \
    libsprd_sb_verifier \
    libfs_mgr\
    libsprd_verify

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

#recovery config:update from internal storage
LOCAL_CFLAGS += $(USE_INTERNAL_STORAGE)

LOCAL_SRC_FILES := main.c
LOCAL_MODULE := applypatch
LOCAL_C_INCLUDES += bootable/recovery
LOCAL_STATIC_LIBRARIES += libapplypatch libmtdutils libmincrypt libbz
LOCAL_SHARED_LIBRARIES += libz libcutils libstdc++ libc
# SPRD: add for ubi support
LOCAL_STATIC_LIBRARIES += \
    libubiutils

# SPRD: add for secure boot
LOCAL_CFLAGS += $(RECOVERY_COMMON_CGLAGS)
LOCAL_STATIC_LIBRARIES += \
    libsprd_sb_verifier \
    libefuse \
    libfs_mgr\
    libsprd_verify

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

#recovery config:update from internal storage
LOCAL_CFLAGS += $(USE_INTERNAL_STORAGE)

LOCAL_SRC_FILES := main.c
LOCAL_MODULE := applypatch_static
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_MODULE_TAGS := eng
LOCAL_C_INCLUDES += bootable/recovery
LOCAL_STATIC_LIBRARIES += libapplypatch libmtdutils libmincrypt libbz
LOCAL_STATIC_LIBRARIES += libz libcutils libstdc++ libc
# SPRD: add for ubi support
LOCAL_STATIC_LIBRARIES += \
    libubiutils

# SPRD: add for secure boot
LOCAL_CFLAGS += $(RECOVERY_COMMON_CGLAGS)
LOCAL_STATIC_LIBRARIES += \
    libsprd_sb_verifier \
    libefuse \
    libfs_mgr \
    libcutils \
    liblog\
    libsprd_verify

include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := imgdiff.c utils.c bsdiff.c
LOCAL_MODULE := imgdiff
LOCAL_FORCE_STATIC_EXECUTABLE := true
LOCAL_C_INCLUDES += external/zlib external/bzip2
LOCAL_STATIC_LIBRARIES += libz libbz

include $(BUILD_HOST_EXECUTABLE)
