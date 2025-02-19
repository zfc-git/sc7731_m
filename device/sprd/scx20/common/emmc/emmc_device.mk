PRODUCT_PROPERTY_OVERRIDES += \
	ro.storage.flash_type=2

ifndef STORAGE_INTERNAL
  STORAGE_INTERNAL := emulated
endif
ifndef STORAGE_PRIMARY
  STORAGE_PRIMARY := internal
endif

STORAGE_ORIGINAL := true
PRODUCT_REVISION := storage_trunk
include $(APPLY_PRODUCT_REVISION)

FSTAB_SUFFIX :=
ifeq ($(strip $(STORAGE_INTERNAL)),physical)
  FSTAB_SUFFIX := 2
endif

-include $(PLATCOMM)/storage_device.mk

