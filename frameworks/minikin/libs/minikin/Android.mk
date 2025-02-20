# Copyright (C) 2013 The Android Open Source Project
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

LOCAL_SRC_FILES := \
    AnalyzeStyle.cpp \
    CmapCoverage.cpp \
    FontCollection.cpp \
    FontFamily.cpp \
    GraphemeBreak.cpp \
    Hyphenator.cpp \
    Layout.cpp \
    LineBreaker.cpp \
    Measurement.cpp \
    MinikinInternal.cpp \
    MinikinRefCounted.cpp \
    MinikinFontFreeType.cpp \
    SparseBitSet.cpp

LOCAL_MODULE := libminikin

LOCAL_C_INCLUDES += \
    external/harfbuzz_ng/src \
    external/freetype/include \
    frameworks/minikin/include

LOCAL_SHARED_LIBRARIES := \
    libharfbuzz_ng \
    libft2 \
    liblog \
    libpng \
    libz \
    libicuuc \
    libutils

include $(BUILD_SHARED_LIBRARY)
