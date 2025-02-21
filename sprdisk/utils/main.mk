buildsh := $(shell if [ -f ./sprdisk/utils/build.sh ]; then echo "./sprdisk/utils/build.sh"; else echo "./utils/build.sh"; fi;)

ifneq ($(TARGET_ARCH),)
	SPRDISK_ARCH := $(TARGET_ARCH)
else
	ifneq ($(target),)
		SPRDISK_ARCH := $(target)
	endif
endif

.PHONY: SPRDISK

SPRDISK:$(TARGET_PREBUILT_KERNEL)
ifneq ($(strip $(SPRDISK_ARCH)),)
	@eval $(buildsh) $(SPRDISK_ARCH) "-c"
	@eval $(buildsh) $(SPRDISK_ARCH) $(arg)
endif

$(INSTALLED_SPRDISK_TARGET) : SPRDISK
