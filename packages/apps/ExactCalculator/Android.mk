#
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
#

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_OVERRIDES_PACKAGES := Calculator
LOCAL_PACKAGE_NAME := ExactCalculator
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_CERTIFICATE := platform

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := cr \
                               android-support-v4 \
			       jardata

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_DX_FLAGS := --multi-dex
LOCAL_JACK_FLAGS := --multi-dex native
include $(BUILD_PACKAGE)
##################################################
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := jardata:data.jar

include $(BUILD_MULTI_PREBUILT)
 

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
