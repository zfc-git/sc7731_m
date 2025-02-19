TARGET_PLATFORM := sc8830
TARGET_BOARD := sp7731c_1h10_32v4

PLATDIR := device/sprd/scx20
PLATCOMM := $(PLATDIR)/common
BOARDDIR := $(PLATDIR)/$(TARGET_BOARD)
ROOTDIR  := $(BOARDDIR)/rootdir
ROOTCOMM := $(PLATCOMM)/rootdir

# include the base version features
include $(PLATCOMM)/plus.mk

include $(APPLY_PRODUCT_REVISION)

CHIPRAM_DEFCONFIG := sp7731ceb
UBOOT_DEFCONFIG := sp7731ceb
KERNEL_DEFCONFIG := sp7731ceb_dt_defconfig
DTS_DEFCONFIG := sprd-scx20_sp7731ceb

#use sprd's four(wifi bt gps fm) integrated one chip
USE_SPRD_WCN := true


BOARD_KERNEL_PAGESIZE := 2048
BOARD_KERNEL_SEPARATED_DT := true

ifndef STORAGE_INTERNAL
  STORAGE_INTERNAL := emulated
endif
ifndef STORAGE_PRIMARY
  STORAGE_PRIMARY := external
endif

# SPRD: add for low-memory.set before calling device.mk @{
PRODUCT_RAM := low
#PRODUCT_RAM := high
# @}

# include general common configs
$(call inherit-product, $(PLATCOMM)/device.mk)
$(call inherit-product, $(PLATCOMM)/emmc/emmc_device.mk)
$(call inherit-product, $(PLATCOMM)/proprietories.mk)

# config selinux policy
BOARD_SEPOLICY_DIRS += $(PLATCOMM)/sepolicy

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

#[[ for lava-display test
PRODUCT_PACKAGES += lava_display
#]]

PRODUCT_PACKAGES += wpa_supplicant \
	wpa_supplicant.conf \
	wpa_supplicant_overlay.conf \
	hostapd

# board-specific files
PRODUCT_COPY_FILES += \
	$(BOARDDIR)/slog_modem_$(TARGET_BUILD_VARIANT).conf:system/etc/slog_modem.conf \
	$(ROOTDIR)/root/init.$(TARGET_BOARD).rc:root/init.$(TARGET_BOARD).rc \
	$(ROOTDIR)/root/init.recovery.$(TARGET_BOARD).rc:root/init.recovery.$(TARGET_BOARD).rc \
	$(ROOTDIR)/system/etc/audio_hw.xml:system/etc/audio_hw.xml \
	$(ROOTDIR)/system/etc/audio_para:system/etc/audio_para \
	$(ROOTDIR)/system/etc/audio_policy.conf:system/etc/audio_policy.conf \
	$(ROOTDIR)/system/etc/codec_pga.xml:system/etc/codec_pga.xml \
	$(ROOTDIR)/system/etc/tiny_hw.xml:system/etc/tiny_hw.xml \
	$(ROOTDIR)/system/etc/rx_data.pcm:system/etc/rx_data.pcm \
	$(ROOTDIR)/prodnv/PCBA.conf:prodnv/PCBA.conf \
	$(ROOTDIR)/prodnv/BBAT.conf:prodnv/BBAT.conf \
	$(ROOTCOMM)/root/ueventd.sc8830.rc:root/ueventd.$(TARGET_BOARD).rc \
	$(ROOTCOMM)/root/fstab$(FSTAB_SUFFIX).sc8830:root/fstab.$(TARGET_BOARD) \
	$(ROOTCOMM)/system/usr/idc/focaltech_ts.idc:system/usr/idc/focaltech_ts.idc \
	$(ROOTCOMM)/system/usr/idc/msg2138_ts.idc:system/usr/idc/msg2138_ts.idc \
	frameworks/native/data/etc/android.hardware.sensor.light.xml:system/etc/permissions/android.hardware.sensor.light.xml \
	frameworks/native/data/etc/android.hardware.sensor.proximity.xml:system/etc/permissions/android.hardware.sensor.proximity.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.camera.flash.xml:system/etc/permissions/android.hardware.camera.flash.xml \
	frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml

$(call inherit-product-if-exists, vendor/sprd/open-source/common_packages.mk)
$(call inherit-product, vendor/sprd/gps/GreenEye/device-sprd-gps.mk)

ifeq ($(strip $(USE_SPRD_WCN)),true)
#connectivity configuration
CONNECTIVITY_HW_CONFIG := $(TARGET_BOARD)
CONNECTIVITY_HW_CHISET := $(shell grep BOARD_SPRD_WCNBT $(BOARDDIR)/BoardConfig.mk)
$(call inherit-product, vendor/sprd/open-source/res/connectivity/device-sprd-wcn.mk)
endif


# Overrides
PRODUCT_NAME := sp7731c_1h10_32v4_native
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := SP7731CEA
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := SPRD
PRODUCT_USE_FPGA := false
PRODUCT_USE_OPENPHONE := false

PRODUCT_LOCALES := zh_CN zh_TW en_US

# gif does not need
PRODUCT_PROPERTY_OVERRIDES += persist.sys.cam.gif=false
TARGET_TS_UCAM_MAKEUP_GIF_NENABLE := false
# timestamp does not need
PRODUCT_PROPERTY_OVERRIDES += persist.sys.cam.timestamp=false
