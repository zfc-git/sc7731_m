include device/sprd/scx35/scx35_j3_3g/scx35_j3_3g_common.mk
include $(PLATCOMM)/plus.mk

CHIPRAM_DEFCONFIG := scx35_j3_3g
UBOOT_DEFCONFIG := scx35_j3_3g
KERNEL_DEFCONFIG := scx35_j3_3g_defconfig
DTS_DEFCONFIG := sprd-scx35_j3_3g

PRODUCT_PROPERTY_OVERRIDES += \
		lmk.autocalc=false
$(call inherit-product, vendor/sprd/gps/bcm47520/device-bcm-gps.mk)
# Overrides
PRODUCT_NAME := scx35_j3_3g
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := scx35_j3_3g
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := Spreadtrum

PRODUCT_LOCALES := zh_CN zh_TW en_US
#use sprd's four(wifi bt gps fm) integrated one chip
USE_SPRD_WCN := false
#SANSA|SPRD|NONE
PRODUCT_SECURE_BOOT := NONE
PRODUCT_PACKAGES += imgheaderinsert \
		    packimage.sh
