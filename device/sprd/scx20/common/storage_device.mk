
# storage init files
ifeq ($(STORAGE_ORIGINAL), true)
    PRODUCT_COPY_FILES += \
	    $(ROOTCOMM)/root/init.storage_original.rc:root/init.storage.rc
    PRODUCT_PROPERTY_OVERRIDES += \
        persist.storage.type=2 \
        sys.internal.emulated=1
else
PRODUCT_COPY_FILES += \
	$(ROOTCOMM)/root/init.storage_$(STORAGE_INTERNAL).rc:root/init.storage.rc
ifeq ($(STORAGE_INTERNAL), emulated)
  PRODUCT_PROPERTY_OVERRIDES += \
    sys.internal.emulated=1
  ifeq ($(STORAGE_PRIMARY), internal)
    PRODUCT_PROPERTY_OVERRIDES += \
        persist.storage.type=2
  endif
  ifeq ($(STORAGE_PRIMARY), external)
    PRODUCT_PROPERTY_OVERRIDES += \
        ro.vold.primary_physical=1 \
        persist.storage.type=1
  endif
endif

ifeq ($(STORAGE_INTERNAL), physical)
  PRODUCT_PROPERTY_OVERRIDES += \
    ro.vold.primary_physical=1 \
    sys.internal.emulated=0
# recovery config: update from interanl storage
  ENABLE_INTERNAL_STORAGE := true
  $(warning warning :enable_internal_storage: $(ENABLE_INTERNAL_STORAGE))
  ifeq ($(STORAGE_PRIMARY), internal)
    PRODUCT_PROPERTY_OVERRIDES += \
        persist.storage.type=2
  endif
  ifeq ($(STORAGE_PRIMARY), external)
    PRODUCT_PROPERTY_OVERRIDES += \
        persist.storage.type=1
  endif
endif
endif

ifndef INSTALL_INTERNAL
  INSTALL_INTERNAL := false
endif

ifeq ($(strip $(INSTALL_INTERNAL)),true)
PRODUCT_PROPERTY_OVERRIDES += \
	ro.storage.install2internal=1
else
PRODUCT_PROPERTY_OVERRIDES += \
	ro.storage.install2internal=0
endif

