include device/sprd/scx35/sp7730sw_1h10/sp7730sw_dt_common.mk

include $(PLATCOMM)/plus.mk

CHIPRAM_DEFCONFIG := sp7730sw
UBOOT_DEFCONFIG := sp7730sw
KERNEL_DEFCONFIG := sp7730sw-dt_defconfig
DTS_DEFCONFIG := sprd-scx35_sp7730sw


# Overrides
PRODUCT_NAME := sp7730sw_1h10_native
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := SP7730SW
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := SPRD

PRODUCT_LOCALES := zh_CN zh_TW en_US

BUILDING_PDK_NATIVE := true


#use sprd's four(wifi bt gps fm) integrated one chip
USE_SPRD_WCN := true

#SANSA|SPRD|NONE
PRODUCT_SECURE_BOOT := NONE
PRODUCT_PACKAGES += imgheaderinsert \
		    packimage.sh
