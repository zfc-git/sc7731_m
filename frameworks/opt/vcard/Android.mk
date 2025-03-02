# Copyright (C) 2010 The Android Open Source Project
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

# 2.3
#LOCAL_SDK_VERSION := 9

LOCAL_MODULE := com.android.vcard
LOCAL_SRC_FILES := $(call all-java-files-under, java)

include $(BUILD_STATIC_JAVA_LIBRARY)

# Build the test package.
include $(call all-makefiles-under, $(LOCAL_PATH))
