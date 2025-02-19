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
PLATDIR := device/sprd/scx35

TARGET_BOARD := scx35_j3_3g
PLATCOMM := $(PLATDIR)/common
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
ROOTDIR := $(BOARDDIR)/rootdir
ROOTCOMM := $(PLATCOMM)/rootdir

BOARD_KERNEL_PAGESIZE := 2048
BOARD_KERNEL_SEPARATED_DT := true

# copy media_profiles.xml before calling device.mk,
# because we want to use our file, not the common one
PRODUCT_COPY_FILES += $(BOARDDIR)/media_profiles.xml:system/etc/media_profiles.xml
PRODUCT_COPY_FILES += $(BOARDDIR)/media_codecs.xml:system/etc/media_codecs.xml

ifndef STORAGE_INTERNAL
  STORAGE_INTERNAL := emulated
endif
ifndef STORAGE_PRIMARY
  STORAGE_PRIMARY := internal
endif

ifndef STORAGE_ORIGINAL
  STORAGE_ORIGINAL := true
endif

ifndef ENABLE_OTG_USBDISK
  ENABLE_OTG_USBDISK := true
endif

# include general common configs
$(call inherit-product, $(PLATCOMM)/device.mk)
$(call inherit-product, $(PLATCOMM)/emmc/emmc_device.mk)
$(call inherit-product, $(PLATCOMM)/proprietories.mk)

#$(call inherit-product, vendor/sprd/open-source/res/productinfo/connectivity_configure_j3_3g.mk)
# config selinux policy
BOARD_SEPOLICY_DIRS += $(PLATCOMM)/sepolicy
DEVICE_PACKAGE_OVERLAYS := $(BOARDDIR)/overlay $(PLATCOMM)/overlay

PRODUCT_AAPT_CONFIG := hdpi

# Set default USB interface
PRODUCT_DEFAULT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mass_storage

PRODUCT_PROPERTY_OVERRIDES += \
	keyguard.no_require_sim=true \
	ro.com.android.dataroaming=false \
	persist.msms.phone_default=0 \
        persist.sys.modem.diag=,gser \
        persist.sys.support.vt=false \
        sys.usb.gser.count=5 \
        persist.radio.ssda.testmode=255 \
        persist.radio.ssda.testmode1=10

# board-specific modules
PRODUCT_PACKAGES += \
        sensors.$(TARGET_PLATFORM) \
        fm.$(TARGET_PLATFORM) \
        ValidationTools

PRODUCT_PACKAGES += wpa_supplicant \
	wpa_supplicant.conf \
	wpa_supplicant_overlay.conf \
	hostapd \
	download

# board-specific files
PRODUCT_COPY_FILES += \
	$(BOARDDIR)/slog_modem_$(TARGET_BUILD_VARIANT).conf:system/etc/slog_modem.conf \
	$(ROOTDIR)/root/init.$(TARGET_BOARD).rc:root/init.$(TARGET_BOARD).rc \
	$(ROOTDIR)/root/init.recovery.$(TARGET_BOARD).rc:root/init.recovery.$(TARGET_BOARD).rc \
	$(ROOTDIR)/system/etc/audio_params/tiny_hw.xml:system/etc/tiny_hw.xml \
	$(ROOTDIR)/system/etc/audio_params/codec_pga.xml:system/etc/codec_pga.xml \
	$(ROOTDIR)/system/etc/audio_params/audio_hw.xml:system/etc/audio_hw.xml \
	$(ROOTDIR)/system/etc/audio_params/audio_para:system/etc/audio_para \
	$(ROOTDIR)/system/etc/audio_params/audio_policy.conf:system/etc/audio_policy.conf \
	$(ROOTCOMM)/root/ueventd.sc8830.rc:root/ueventd.$(TARGET_BOARD).rc \
	$(ROOTCOMM)/root/fstab$(FSTAB_SUFFIX).sc8830:root/fstab.$(TARGET_BOARD) \
	frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
	frameworks/native/data/etc/android.software.midi.xml:system/etc/permissions/android.software.midi.xml \
	frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml \
	frameworks/native/data/etc/android.hardware.camera.flash-autofocus.xml:system/etc/permissions/android.hardware.camera.flash-autofocus.xml \
	frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
	vendor/sprd/wcn/wifi/sc2331/6.0/sprdwl.ko:root/lib/modules/sprdwl.ko \
	vendor/sprd/open-source/libs/gpu/utgard/driver/mali/mali.ko:root/lib/modules/mali.ko

$(call inherit-product, vendor/sprd/open-source/res/boot/boot_res_8830s.mk)
$(call inherit-product, frameworks/native/build/phone-xhdpi-1024-dalvik-heap.mk)
$(call inherit-product-if-exists, vendor/sprd/open-source/common_packages.mk)
$(call inherit-product-if-exists, vendor/sprd/open-source/plus_special_packages.mk)
#$(call inherit-product, vendor/sprd/gps/slsi/harrier/device-sprd-gps.mk)
#connectivity configuration
CONNECTIVITY_HW_CONFIG := $(TARGET_BOARD)
CONNECTIVITY_HW_CHISET := $(shell grep BOARD_SPRD_WCNBT $(BOARDDIR)/BoardConfig.mk)
$(call inherit-product, vendor/sprd/open-source/res/connectivity/device-sprd-wcn.mk)
$(call inherit-product, vendor/sprd/partner/shark/bluetooth/device-shark-bt.mk)


# Overrides
#PRODUCT_NAME := scx35_j3_3g
#PRODUCT_DEVICE := $(TARGET_BOARD)
#PRODUCT_MODEL := J3_3G
#PRODUCT_BRAND := SPRD
#PRODUCT_MANUFACTURER := Spreadtrum

#PRODUCT_LOCALES := zh_CN zh_TW en_US
