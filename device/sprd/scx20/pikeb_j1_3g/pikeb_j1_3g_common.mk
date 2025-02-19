#
# Copyright (C) 2007 The Android Open Source Project
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

TARGET_PLATFORM := sc8830
PLATDIR := device/sprd/scx20
TARGET_BOARD := pikeb_j1_3g

PLATCOMM := $(PLATDIR)/common
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
ROOTDIR  := $(BOARDDIR)/rootdir
ROOTCOMM := $(PLATCOMM)/rootdir

BOARD_KERNEL_PAGESIZE := 2048
BOARD_KERNEL_SEPARATED_DT := true

ifndef STORAGE_INTERNAL
  STORAGE_INTERNAL := emulated
endif
ifndef STORAGE_PRIMARY
  STORAGE_PRIMARY := internal
endif

# include general common configs
$(call inherit-product, $(PLATCOMM)/device.mk)
$(call inherit-product, $(PLATCOMM)/emmc/emmc_device.mk)
$(call inherit-product, $(PLATCOMM)/proprietories.mk)

DEVICE_PACKAGE_OVERLAYS := $(BOARDDIR)/overlay $(PLATCOMM)/overlay

PRODUCT_AAPT_CONFIG := hdpi
# xhdpi xlarge

# PRODUCT_AAPT_PREF_CONFIG := xhdpi

# Set default USB interface
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mass_storage

PRODUCT_PROPERTY_OVERRIDES += \
	keyguard.no_require_sim=true \
	ro.com.android.dataroaming=false \
	persist.msms.phone_default=0 \
        persist.sys.modem.diag=,gser \
        persist.sys.support.vt=false \
        sys.usb.gser.count=4 \
        lmk.autocalc=false

# board-specific modules
PRODUCT_PACKAGES += \
        sensors.$(TARGET_PLATFORM) \
        fm.$(TARGET_PLATFORM) \
        ValidationTools \
		libefuse

#[[ for autotest
PRODUCT_PACKAGES += autotest
#]]

PRODUCT_PACKAGES += wpa_supplicant \
	wpa_supplicant.conf \
	wpa_supplicant_overlay.conf \
	hostapd \
	download

# board-specific files



PRODUCT_COPY_FILES += \
	$(BOARDDIR)/slog_modem_$(TARGET_BUILD_VARIANT).conf:system/etc/slog_modem.conf \
	$(ROOTDIR)/root/init.j1_3g.rc:root/init.j1_3g.rc \
	$(ROOTDIR)/root/init.recovery.j1_3g.rc:root/init.recovery.j1_3g.rc \
	$(ROOTDIR)/system/etc/audio_hw.xml:system/etc/audio_hw.xml \
	$(ROOTDIR)/system/etc/audio_para:system/etc/audio_para \
	$(ROOTDIR)/system/etc/audio_policy.conf:system/etc/audio_policy.conf \
	$(ROOTDIR)/system/etc/codec_pga.xml:system/etc/codec_pga.xml \
	$(ROOTDIR)/system/etc/tiny_hw.xml:system/etc/tiny_hw.xml \
	$(ROOTCOMM)/root/ueventd.sc8830.rc:root/ueventd.j1_3g.rc \
	$(ROOTCOMM)/root/fstab$(FSTAB_SUFFIX).sc8830:root/fstab.j1_3g \
	$(ROOTCOMM)/system/usr/idc/focaltech_ts.idc:system/usr/idc/focaltech_ts.idc \
	$(ROOTCOMM)/system/usr/idc/msg2138_ts.idc:system/usr/idc/msg2138_ts.idc \
	vendor/sprd/wcn/wifi/sc2331/5.1/sprdwl.ko:root/lib/modules/sprdwl.ko \
	frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
	frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.camera.flash.xml:system/etc/permissions/android.hardware.camera.flash.xml \
	frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml

$(call inherit-product, frameworks/native/build/phone-hdpi-512-dalvik-heap.mk)
$(call inherit-product-if-exists, vendor/sprd/open-source/common_packages.mk)
$(call inherit-product, vendor/sprd/gps/GreenEye/device-sprd-gps.mk)

#connectivity configuration
CONNECTIVITY_HW_CONFIG := $(TARGET_BOARD)
CONNECTIVITY_HW_CHISET := $(shell grep BOARD_SPRD_WCNBT $(BOARDDIR)/BoardConfig.mk)
$(call inherit-product, vendor/sprd/open-source/res/connectivity/device-sprd-wcn.mk)

# Overrides
#PRODUCT_NAME := pikeb_j1_3g_common
#PRODUCT_DEVICE := $(TARGET_BOARD)
#PRODUCT_MODEL := SP7730A
#PRODUCT_BRAND := SPRD
#PRODUCT_MANUFACTURER := SPRD

#PRODUCT_LOCALES := zh_CN zh_TW en_US
