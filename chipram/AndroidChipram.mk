ifeq ($(TOOLCHAIN_64),true)
ifeq ($(UBOOT_USE_ANDROID_TOOLCHAIN),true)
     LOCAL_TOOLCHAIN := $(shell pwd)/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/bin/aarch64-linux-android-
else
     LOCAL_TOOLCHAIN := $(shell pwd)/prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.8/bin/aarch64-linux-android-
endif
else
LOCAL_TOOLCHAIN := arm-eabi-
endif

CHIPRAM_OUT := $(TARGET_OUT_INTERMEDIATES)/chipram

BUILT_SPL := $(CHIPRAM_OUT)/nand_spl/u-boot-spl-16k.bin
BUILT_FDL1 := $(CHIPRAM_OUT)/nand_fdl/fdl1.bin

ifeq ($(BUILD_FPGA),true)
BUILT_DDR_SCAN := $(CHIPRAM_OUT)/ddr_scan/ddr_scan.bin
endif

CHIPRAM_CONFIG := $(CHIPRAM_OUT)/include/config.h

export CHIPRAM_CONFIG_PRODUCT

.PHONY: $(CHIPRAM_OUT)
$(CHIPRAM_OUT):
	@echo "Start chipram build"

$(CHIPRAM_CONFIG):  $(CHIPRAM_OUT)
	@mkdir -p $(CHIPRAM_OUT)
	$(MAKE) -C chipram CROSS_COMPILE=$(LOCAL_TOOLCHAIN) O=../$(CHIPRAM_OUT) distclean
	$(MAKE) -C chipram CROSS_COMPILE=$(LOCAL_TOOLCHAIN) O=../$(CHIPRAM_OUT) $(UBOOT_DEFCONFIG)_config
ifeq ($(strip $(BOARD_KERNEL_SEPARATED_DT)),true)
	@echo "#define CONFIG_OF_LIBFDT" >> $(CHIPRAM_CONFIG)
endif

$(INSTALLED_CHIPRAM_TARGET) : $(CHIPRAM_CONFIG)
	$(MAKE) -C chipram CROSS_COMPILE=$(LOCAL_TOOLCHAIN) AP_VERSION="$(ANDROID_BUILD_DESC)" O=../$(CHIPRAM_OUT)
	@cp $(BUILT_SPL) $(PRODUCT_OUT)
ifeq ($(BUILD_FPGA),true)
	@cp $(BUILT_DDR_SCAN) $(PRODUCT_OUT)
endif
	@cp $(BUILT_FDL1) $(PRODUCT_OUT)
	@echo "Install chipram target done"


