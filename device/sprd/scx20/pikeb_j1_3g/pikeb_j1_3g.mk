include device/sprd/pikeb_j1_3g/pikeb_j1_3g_common.mk

# include the base version features
include $(PLATCOMM)/plus.mk

include $(APPLY_PRODUCT_REVISION)
# Overrides
PRODUCT_NAME := pikeb_j1_3g
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := SP7720EB
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := SPRD
PRODUCT_USE_FPGA := false
PRODUCT_USE_OPENPHONE := false
PRODUCT_SP7727SEEB_PRIME := true

PRODUCT_LOCALES := zh_CN zh_TW en_US
MALLOC_IMPL := dlmalloc
