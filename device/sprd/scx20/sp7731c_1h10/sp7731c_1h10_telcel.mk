
include device/sprd/scx20/sp7731c_1h10/sp7731c_1h10_native.mk

PRODUCT_REVISION := oversea telcel
include $(APPLY_PRODUCT_REVISION)

# Override
PRODUCT_NAME := sp7731c_1h10_telcel
