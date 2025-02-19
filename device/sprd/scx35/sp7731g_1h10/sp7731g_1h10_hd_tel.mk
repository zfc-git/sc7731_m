include device/sprd/scx35/sp7731g_1h10/sp7731g_1h10_common.mk

include $(PLATCOMM)/plus.mk

PRODUCT_REVISION := multiuser telcel

include $(APPLY_PRODUCT_REVISION)

CHIPRAM_DEFCONFIG := sp7731gea_hdr
UBOOT_DEFCONFIG := sp7731gea_hdr
KERNEL_DEFCONFIG := sp7731gea_hdr-dt_defconfig
DTS_DEFCONFIG := sprd-scx35_sp7731gea_hdr
PRODUCT_RAM := high

# Overrides
PRODUCT_NAME := sp7731g_1h10_hd_tel
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := SP7731G
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := SPRD

PRODUCT_LOCALES := zh_CN zh_TW en_US
