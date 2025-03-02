# ext4 partition layout
TARGET_USERIMAGES_USE_EXT4 := true
ifeq ($(STORAGE_INTERNAL), physical)
BOARD_USERDATAIMAGE_PARTITION_SIZE := 1500000000
else
BOARD_USERDATAIMAGE_PARTITION_SIZE := 835032704
endif
BOARD_CACHEIMAGE_PARTITION_SIZE := 150000000
BOARD_PRODNVIMAGE_PARTITION_SIZE := 5242880
BOARD_SYSINFOIMAGE_PARTITION_SIZE := 5242880
BOARD_PERSISTIMAGE_PARTITION_SIZE := 2097152
BOARD_FLASH_BLOCK_SIZE := 4096
ifeq ($(strip $(BOARD_HAVE_OEM_PARTITION)),true)
BOARD_OEMIMAGE_PARTITION_SIZE := 524288000
BOARD_OEMIMAGE_FILE_SYSTEM_TYPE := ext4
TARGET_OEMIMAGES_SPARSE_EXT_DISABLED := true
endif
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_PRODNVIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSINFOIMAGE_FILE_SYSTEM_TYPE := ext4

TARGET_SYSTEMIMAGES_SPARSE_EXT_DISABLED := true
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := false
TARGET_CACHEIMAGES_SPARSE_EXT_DISABLED := false
TARGET_PRODNVIMAGES_SPARSE_EXT_DISABLED := true
TARGET_SYSINFOIMAGES_SPARSE_EXT_DISABLED := true

#copy partition info xml
PRODUCT_COPY_FILES += $(BOARDDIR)/SC7720_UMS.xml:$(PRODUCT_OUT)/SC7720_UMS.xml
PRODUCT_COPY_FILES += $(BOARDDIR)/SC7720_UMS_prime.xml:$(PRODUCT_OUT)/SC7720_UMS_prime.xml
PRODUCT_COPY_FILES += $(BOARDDIR)/pike_ea_gms.xml:$(PRODUCT_OUT)/pike_ea_gms.xml
