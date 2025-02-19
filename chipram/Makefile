

ifdef O
ifeq ("$(origin O)", "command line")
BUILD_DIR := $(O)
endif
endif

ifneq ($(BUILD_DIR),)
saved-output := $(BUILD_DIR)

# Attempt to create a output directory.
$(shell [ -d ${BUILD_DIR} ] || mkdir -p ${BUILD_DIR})

# Verify if it was successful.
BUILD_DIR := $(shell cd $(BUILD_DIR) && /bin/pwd)
$(if $(BUILD_DIR),,$(error output directory "$(saved-output)" does not exist))
endif # ifneq ($(BUILD_DIR),)

OBJTREE		:= $(if $(BUILD_DIR),$(BUILD_DIR),$(CURDIR))
SRCTREE		:= $(CURDIR)
TOPDIR		:= $(SRCTREE)
LNDIR		:= $(OBJTREE)
export	TOPDIR SRCTREE OBJTREE

MKCONFIG	:= $(SRCTREE)/mkconfig

obj := $(OBJTREE)/
src := $(SRCTREE)/

export obj src


#########################################################################

ifeq ($(obj)include/config.mk,$(wildcard $(obj)include/config.mk))

# load ARCH, BOARD, and CPU configuration
include $(obj)include/config.mk
export	ARCH CPU BOARD VENDOR SOC

CROSS_COMPILE ?= arm-eabi-

# load other configuration
include $(TOPDIR)/config.mk

include  $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/config.mk

#########################################################################


# Add GCC lib
PLATFORM_LIBGCC = -L $(shell dirname `$(CC) $(CFLAGS) -print-libgcc-file-name`) -lgcc
PLATFORM_LIBS += $(PLATFORM_LIBGCC)
export PLATFORM_LIBS

NAND_SPL = nand_spl


#########################################################################
#########################################################################
ifdef CONFIG_FPGA
ALL +=  $(NAND_SPL) fdl1
else
ALL +=  $(NAND_SPL) fdl1 DDR_SCAN DDR_AUTO_SLT
endif

all:		$(ALL)


ifndef CONFIG_WHALE
$(NAND_SPL):	PREPARE_DIR  SPL_ALL
else
$(NAND_SPL):	PREPARE_DIR  DDR_INIT SPL_ALL
endif
#		$(MAKE) -C nand_spl/board/$(VENDOR)/$(SOC) all

PREPARE_DIR:
	@mkdir -p $(obj)nand_spl/board/$(VENDOR)/$(SOC)

nandobj	:= $(OBJTREE)/nand_spl/
ifdef CONFIG_SCX35L64
 ifdef CONFIG_SECURE_BOOT
SEC_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec-common.lds
 else
SEC_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec-scx35l64.lds
 endif
SPL_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-spl_scx35l64.lds
else
ifeq ($(CONFIG_SYS_TEXT_BASE),0x50003000)
ifdef CONFIG_SECURE_BOOT
SEC_LDSCRIPT    = $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec-common.lds
else
SEC_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec-1.lds
endif
else
ifdef CONFIG_SECURE_BOOT
SEC_LDSCRIPT    = $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec-common.lds
else
SEC_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-sec.lds
endif
endif
ifdef CONFIG_SPRD_DMC_R1P0
SPL_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-spl-sprd-dmc.lds
else
ifdef CONFIG_DUAL_SPL
SPL_LDSCRIPT    = $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-dual-spl.lds
else
SPL_LDSCRIPT	= $(TOPDIR)/nand_spl/board/$(VENDOR)/$(SOC)/u-boot-spl.lds
endif
endif
endif
SEC_LDFLAGS	= -Bstatic -T $(SEC_LDSCRIPT) $(PLATFORM_LDFLAGS)
SPL_LDFLAGS	= -Bstatic -T $(SPL_LDSCRIPT) $(PLATFORM_LDFLAGS)
AFLAGS		+= -DCONFIG_PRELOADER -DCONFIG_NAND_SPL
#CFLAGS		+= -DCONFIG_PRELOADER -DCONFIG_NAND_SPL -g
CFLAGS		+= -DCONFIG_PRELOADER -g

SPL_SOBJS	= spl_start.o _udivsi3.o _divsi3.o _umodsi3.o boot_mode.o
ifdef CONFIG_SECURE_BOOT
SEC_SOBJS = sec_start_common.o sec_start.o sec_mcu.o sec_boot.o
else
SEC_SOBJS	= sec_start.o sec_mcu.o sec_boot.o
endif

ifdef CONFIG_EMMC_BOOT
SPL_COBJS	= emmc_boot.o mmc.o
else
SPL_COBJS	= sprd_tiger_nand_boot.o 
ifdef CONFIG_DOLPHIN
SPL_COBJS += dolphin_nand.o
else
ifdef CONFIG_SC9630
SPL_COBJS += sc9630_nand.o
else
SPL_COBJS += sc8830_nand.o
endif
endif
endif

ifdef CONFIG_WHALE
include $(SRCTREE)/ddr/Makefile
else
include $(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/Makefile
endif

ifdef CONFIG_USE64
SPL_SOBJS	+= switch64.o
endif

ifdef CONFIG_EFUSE
SPL_COBJS	+= efuse.o ana_efuse.o otp_help.o
endif

ifdef CONFIG_SCX20_MDAR
SPL_COBJS	+= mcu.o $(DDR_COBJS) adi.o chip_cfg.o div0.o sprd_string.o lowlevel_init.o mdar.o
else
SPL_COBJS	+= mcu.o $(DDR_COBJS) adi.o chip_cfg.o div0.o sprd_string.o lowlevel_init.o
endif

ifdef CONFIG_LOAD_PARTITION
SPL_COBJS      +=part.o part_efi.o
endif

ifdef CONFIG_DUAL_DDR
SPL_COBJS	+= noc_drv.o
endif
ifdef CONFIG_DUAL_SPL    #to be modified by new macro
SPL_COBJS      +=sprdsha1.o sprdsha256.o
endif

SPL_LNDIR		:= $(OBJTREE)/nand_spl/board/$(VENDOR)/$(SOC)
SPLOBJDIR  := $(SPL_LNDIR)/
SEC_OBJS	:= $(addprefix $(SPLOBJDIR),$(SEC_SOBJS) $(SEC_COBJS))
SPL_OBJS	:= $(addprefix $(SPLOBJDIR),$(SPL_SOBJS) $(SPL_COBJS))

ifdef CONFIG_WHALE
SPL_OBJS	:= $(ddr_init_obj_list)
endif

__SEC_OBJS	:= $(SEC_COBJS) $(SEC_SOBJS)
__SPL_OBJS	:= $(SPL_COBJS) $(SPL_SOBJS)

FILE_SIZE_CHECK := $(SRCTREE)/nand_spl/board/$(VENDOR)/$(SOC)/file_size_check.sh

ifdef CONFIG_DUAL_SPL
SPL_ALL : $(nandobj)u-boot-spl $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl-16k.bin file_size_check
else
SPL_ALL : $(nandobj)u-boot-spl $(nandobj)u-boot-sec.bin $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl-16k.bin file_size_check
endif

file_size_check: $(nandobj)u-boot-spl-16k.bin
ifdef CONFIG_SPL_32K
ifdef CONFIG_DUAL_SPL
ifdef CONFIG_SPRD_DMC_R1P0
	$(FILE_SIZE_CHECK) 32768 $< $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl
else
	$(FILE_SIZE_CHECK) 40000 $< $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl
endif
else
ifdef CONFIG_SPRD_DMC_R1P0
	$(FILE_SIZE_CHECK) 31744 $< $(nandobj)u-boot-sec.bin $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl $(nandobj)u-boot-sec
else	
	$(FILE_SIZE_CHECK) 31744 $< $(nandobj)u-boot-sec.bin $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl $(nandobj)u-boot-sec
endif
endif
else
	$(FILE_SIZE_CHECK) 23552 $< $(nandobj)u-boot-sec.bin $(nandobj)u-boot-spl.bin $(nandobj)u-boot-spl $(nandobj)u-boot-sec
endif

ifdef CONFIG_DUAL_SPL
$(nandobj)u-boot-spl-16k.bin: $(nandobj)u-boot-spl.bin
	cat $(nandobj)u-boot-spl.bin > $@
else
$(nandobj)u-boot-spl-16k.bin: $(nandobj)u-boot-spl.bin $(nandobj)u-boot-sec.bin
	cat $(nandobj)u-boot-sec.bin $(nandobj)u-boot-spl.bin > $@
endif

ifdef CONFIG_SPL_BASE

ifdef CONFIG_DUAL_SPL
$(nandobj)u-boot-spl.bin:	$(nandobj)u-boot-spl
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50003200" -O binary $< $@
else
$(nandobj)u-boot-sec.bin:	$(nandobj)u-boot-sec
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50003C00" -O binary $< $@

$(nandobj)u-boot-spl.bin:	$(nandobj)u-boot-spl
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50008C00" -O binary $< $@
endif
else
ifdef CONFIG_SP9630_SPL_BASE
$(nandobj)u-boot-sec.bin:	$(nandobj)u-boot-sec
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50003C00" -O binary $< $@
else
ifdef CONFIG_SPRD_DMC_R1P0
$(nandobj)u-boot-sec.bin:	$(nandobj)u-boot-sec
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50003C00" -O binary $< $@
else
$(nandobj)u-boot-sec.bin:       $(nandobj)u-boot-sec
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50005C00" -O binary $< $@
endif
endif
ifdef CONFIG_DUAL_SPL
$(nandobj)u-boot-spl.bin:	$(nandobj)u-boot-spl
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x50008000" -O binary $< $@
else
$(nandobj)u-boot-spl.bin:       $(nandobj)u-boot-spl
	$(OBJCOPY) ${OBJCFLAGS} --pad-to "0x5000AC00" -O binary $< $@
endif
endif

$(nandobj)u-boot-sec:	$(SEC_OBJS) $(SEC_LDSCRIPT)
	cd $(SPL_LNDIR) && $(LD) $(SEC_LDFLAGS) $(__SEC_OBJS) \
		-Map $(nandobj)u-boot-sec.map \
		-o $@

$(nandobj)u-boot-spl:	$(SPL_OBJS) $(SPL_LDSCRIPT)
	cd $(SPL_LNDIR) && $(LD) $(SPL_LDFLAGS) $(__SPL_OBJS) \
		-Map $(nandobj)u-boot-spl.map \
		-o $@

#########################################################################
$(SPLOBJDIR)%.o:	$(SRCTREE)/$(CPUDIR)/%.S
	$(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:	$(SRCTREE)/$(CPUDIR)/%.S
	$(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:	$(SRCTREE)/$(CPUDIR)/$(SOC)/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

ifdef DDR_SCAN_28NM
$(SPLOBJDIR)%.o:	$(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -DDDR_SCAN_SUPPORT_28NM -c -o $@ $<
else
$(SPLOBJDIR)%.o:	$(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

#$(SPLOBJDIR)%.o:        $(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.S
#       $(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:        $(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.S
	$(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<
endif

$(SPLOBJDIR)%.o:	$(SRCTREE)/nand_spl/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:	$(SRCTREE)/nand_spl/board/$(VENDOR)/$(SOC)/%.S
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:	$(SRCTREE)/arch/$(ARCH)/lib/%.S
	$(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:      $(SRCTREE)/arch/$(ARCH)/lib/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

$(SPLOBJDIR)%.o:	$(SRCTREE)/$(CPUDIR)/$(SOC)/%.S
	$(CC) $(AFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<

ifdef CONFIG_DUAL_SPL   #to be modified by new macro
$(SPLOBJDIR)%.o:	$(SRCTREE)/secure/common/%.c
	$(CC) $(CFLAGS) -mlittle-endian -DCONFIG_NAND_SPL -c -o $@ $<
endif




#########################################################################


ifdef CONFIG_FPGA
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1_fpga.lds
else
ifdef CONFIG_UMCTL_28NM
ifdef CONFIG_SECURE_BOOT
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1-28nm-sec.lds
else
ifdef CONFIG_DUAL_SPL
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1-dual.lds
else
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1-28nm.lds
endif
endif
else
ifdef CONFIG_SPRD_DMC_R1P0
ifdef CONFIG_SECURE_BOOT
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1-sprd-dmc-sec.lds
else
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1-sprd-dmc.lds
endif
else
FDL1_LDSCRIPT      = $(TOPDIR)/nand_fdl/board/$(VENDOR)/$(SOC)/fdl1.lds
endif
endif
endif
FDL1_LDFLAGS  = -Bstatic -T $(FDL1_LDSCRIPT) $(PLATFORM_LDFLAGS)

AFLAGS       += -I $(OBJTREE)/include/asm/arch
CFLAGS       += -D CONFIG_FDL1 -D CONFIG_FRMCHECK -I $(OBJTREE)/include/asm/arch

FDL1_PATH     = $(TOPDIR)/nand_fdl/fdl-1

FDL1_LNDIR   := $(OBJTREE)/nand_fdl/fdl-1
FDL1OBJDIR   :=$(FDL1_LNDIR)/

FDL1_OBJ_FILE= migrate.o fdl_main.o fdl_command.o
ifdef CONFIG_SECURE_BOOT
FDL1_OBJ_FILE += init_sec.o
else
FDL1_OBJ_FILE += init.o
endif


ifdef CONFIG_EFUSE
FDL1_OBJ_FILE += efuse.o ana_efuse.o otp_help.o
endif
FDL1_OBJ_FILE += dl_engine.o fdl_crc.o packet.o fdl_channel.o fdl_stdio.o sprd_string.o sprd_usb_dloader.o
ifdef CONFIG_DUAL_SPL    #to be modified by new macro
FDL1_OBJ_FILE += sprdsha1.o sprdsha256.o 
endif

ifdef CONFIG_USB_DWC3
FDL1_OBJ_FILE += sprd_usb3_driver.o
else
FDL1_OBJ_FILE += sprd_usb2_driver.o
endif

FDL1_OBJ_FILE += chip_cfg.o mmu_asm.o mmu.o 
FDL1_OBJ_FILE += adi.o $(DDR_COBJS) mcu.o chg_phy.o
FDL1_OBJ_FILE += serial_sc8800x.o
ifdef CONFIG_SECURE_BOOT
FDL1_OBJ_FILE += sec_start_common.o sec_boot.o
endif
ifdef CONFIG_USE64
FDL1_OBJ_FILE 	+= switch64.o
endif

FDL1_OBJS    = $(addprefix $(FDL1OBJDIR),$(FDL1_OBJ_FILE))

fdlobj       := $(OBJTREE)/nand_fdl/


fdl1:	$(fdlobj)fdl1.bin


$(fdlobj)fdl1.bin : $(fdlobj)fdl1.axf
	$(OBJCOPY) ${OBJCFLAGS} -O binary $< $@

$(fdlobj)fdl1.axf: $(FDL1_OBJS) 
	cd $(FDL1_LNDIR) && $(LD) $(FDL1_LDFLAGS) \
		$(FDL1_OBJ_FILE)  \
		-Map $(fdlobj)fdl1.map \
		-o $@ $(PLATFORM_LIBS)

#########################################################################

$(FDL1_LNDIR)/%.o:$(SRCTREE)/nand_fdl/common/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
$(FDL1_LNDIR)/%.o:$(SRCTREE)/nand_fdl/fdl-1/src/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
$(FDL1_LNDIR)/%.o:$(SRCTREE)/nand_fdl/fdl-1/src/%.S
	$(CC) $(AFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
$(FDL1_LNDIR)/%.o:$(SRCTREE)/arch/$(ARCH)/cpu/$(CPU)/$(SOC)/%.S
	$(CC) $(AFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
$(FDL1_LNDIR)/%.o:$(SRCTREE)/arch/$(ARCH)/cpu/$(CPU)/$(SOC)/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
ifdef DDR_SCAN_28NM
$(FDL1_LNDIR)/%.o:$(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -DDDR_SCAN_SUPPORT_28NM -c -o $@ $<
else
$(FDL1_LNDIR)/%.o:$(SRCTREE)/arch/$(ARCH)/cpu/sprd_dmc/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
endif
ifdef CONFIG_DUAL_SPL     #to be modified by new macro
$(FDL1_LNDIR)/%.o:$(SRCTREE)/secure/common/%.c
	$(CC) $(CFLAGS) -I $(FDL1_PATH)/inc  -c -o $@ $<
endif



#########################################################################

endif	# config.mk


preconfig:
	@rm -f $(obj)include/config.h $(obj)include/config.mk 
	@mkdir -p $(obj)include
	@mkdir -p $(obj)nand_fdl/fdl-1
	@mkdir -p $(obj)/nand_spl/board/spreadtrum/sc8830

#%_config::	unconfig
#	@$(MKCONFIG) -A $(@:_config=)


#========================================================================
# SPRD
#========================================================================

sp8830_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp7715ea_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715ea spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp7715eatrisim_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715eatrisim spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp7715ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715ga spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

sp7715gatrisim_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715gatrisim spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

sp7715ga-hvga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715ga spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

pocket2_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 pocket2 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

corsica_ve_config    : preconfig
	@$(MKCONFIG) $@ arm armv7 corsica_ve spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

vivalto_config    : preconfig
	@$(MKCONFIG) $@ arm armv7 vivalto spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

young2_config : preconfig
	@$(MKCONFIG) $@ arm armv7 young2 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

higgs_config : preconfig
	@$(MKCONFIG) $@ arm armv7 higgs spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

spx15ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 spx15ga spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

sp6815ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp6815ga spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk
	
sp7715eaopenphone_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7715eaopenphone spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	
sp8815ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8815ga spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

sp8815gaopenphone_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8815gaopenphone spreadtrum sc8830
	@echo "CONFIG_DOLPHIN = y" >> $(obj)include/config.mk

sp7735ec_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7735ec spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp8830eb_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830eb spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp8835eb_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8835eb spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp8830ec_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830ec spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp9630fpga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630fpga spreadtrum sc9630
	@echo "CONFIG_FPGA = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_BASE = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

pikelfpga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 pikelfpga spreadtrum sc9630
	@echo "CONFIG_FPGA = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

coreprimelite_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sharkls_z3lte_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sharkl_j1poplte_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9630ea3mn_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9630ea6mn_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9630ea4mn_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9630eb4mn_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830aea_5m_h100_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830aea_5m_h100_32v4_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830a_7h10_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830a_7h10_volte_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830aea_3m_h101_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830a_5h10_5mvolte_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830aeb_5m_h100_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830a_5h10_5mvolte_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830a_2h10_4m_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9830i_2h11_4m_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9830iea_4m_h100_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830iea_5m_h100_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9830ieb_5m_h100_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp9832a_2h11_4m_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9832a_2h11_volte_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9830i_j3lte_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sharklc_z2lte_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9830i_j2lte_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9832a_3h10_volte_config        : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_EFUSE = y" >> $(obj)include/config.mk

sp9820ea_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sp7731cea_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731cea spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPRD_DMC_R1P0 = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX20_MDAR = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp7731ceb_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731ceb spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPRD_DMC_R1P0 = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX20_MDAR = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp9820fpga_config       : preconfig
	@$(MKCONFIG) $@ arm armv7 sp9630 spreadtrum sc9630
	@echo "CONFIG_FPGA = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk

sptsharklfpga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sptsharklfpga spreadtrum sc9630
	@echo "CONFIG_FPGA = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_BASE = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9836aea_4mod_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9836a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9836aea_3mod_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9836a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_5mod_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

jiaotu_tc_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk
	
sp9838aea_fhd_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_auto_slt_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_2342a_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_2342a_fhd_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_volte_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_2723ts_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_sr3593_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_3_volte_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838a spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

ss_sharklt8_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 ss_sharklt8 spreadtrum sc9630
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp9838aea_fpga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp9838afpga spreadtrum sc9630
	@echo "CONFIG_FPGA = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_BASE = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SCX35L64 = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SP9630_SPL_BASE = y" >> $(obj)include/config.mk
	@echo "CONFIG_USE64 = y" >> $(obj)include/config.mk

sp8830gea_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830gea spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

kanas_w_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 kanas_w spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	
kanas_td_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 kanas_td spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	
sp8830ec_nwcn_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830ec spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp8830ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830ga spreadtrum sc8830

sp7730ga_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730ga spreadtrum sc8830

sp7730gga_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730gga spreadtrum sc8830
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731gga_lc_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gga_lc spreadtrum sc8830
	@echo "CONFIG_SPX30G = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7730gga_lc_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730gga_lc spreadtrum sc8830
	@echo "CONFIG_SPX30G = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7730ggaopenphone_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730ggaopenphone spreadtrum sc8830
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731gea_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gea spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7730sw_fpga_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730sw_fpga spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7730sw_t2_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730sw_t2 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	@echo "CONFIG_LOAD_PARTITION = y" >> $(obj)include/config.mk

sp7730sw_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730sw spreadtrum sc8830
	@echo "CONFIG_DUAL_SPL = y" >> $(obj)include/config.mk
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	@echo "CONFIG_LOAD_PARTITION = y" >> $(obj)include/config.mk

sp7731gea_hd_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gea_hd spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	#@echo "CONFIG_SECURE_BOOT = y" >> $(obj)include/config.mk

sp7731gea_hdr_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gea_hdr spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp8730seea_qhd_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp8730seea_qhd spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp8730seea_jig_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp8730seea_jig spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731gepike_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gepike spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731gea_fwvga_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gea_fwvga spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731geaopenphone_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731geaopenphone spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7731gea_lc_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7731gea_lc spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp7730ectrisim_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730ectrisim spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp7730ec_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp7730ec spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp8730sea_config : preconfig
	@$(MKCONFIG) $@ arm armv7 sp8730sea spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

sp5735_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp5735 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	
sp8830lc_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp8830lc spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp5735pad_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp5735pad spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sp5735c1_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sp5735c1 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sc9620openphone_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sc9620openphone spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

sc9620openphone_zt_config	: preconfig
	@$(MKCONFIG) $@ arm armv7 sc9620openphone_zt spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk

tsharkwsamsung_config : preconfig
	@$(MKCONFIG) $@ arm armv7 tsharkwsamsung spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

pikeayoung2dtv_config : preconfig
	@$(MKCONFIG) $@ arm armv7 pikeayoung2dtv spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

tshark2tabe_config : preconfig
	@$(MKCONFIG) $@ arm armv7 tshark2tabe spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

grandprime3g_ve_config : preconfig
	@$(MKCONFIG) $@ arm armv7 grandprime3g_ve spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

coreprime3g_ve_config : preconfig
	@$(MKCONFIG) $@ arm armv7 coreprime3g_ve spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

tizenz3_3g_config : preconfig
	@$(MKCONFIG) $@ arm armv7 tizenz3_3g spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

grandprime_dtv_config : preconfig
	@$(MKCONFIG) $@ arm armv7 grandprime_dtv spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
core3_config : preconfig
	@$(MKCONFIG) $@ arm armv7 core3 spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk

scx35_j3_3g_config : preconfig
	@$(MKCONFIG) $@ arm armv7 scx35_j3_3g spreadtrum sc8830
	@echo "CONFIG_EMMC_BOOT = y" >> $(obj)include/config.mk
	@echo "CONFIG_SPL_32K = y" >> $(obj)include/config.mk
	@echo "CONFIG_UMCTL_28NM = y" >> $(obj)include/config.mk
	@echo "CONFIG_SYS_TEXT_BASE = 0x50003000" >> $(obj)include/config.mk
	@echo "CONFIG_DUAL_SPL = y" >> $(obj)include/config.mk
	@echo "CONFIG_LOAD_PARTITION = y" >> $(obj)include/config.mk

ddr_scan_config :
	@echo "DDR_SCAN_28NM = y" >> $(obj)include/config.mk

#########################################################################
#########################################################################

clean:
	@rm -f $(obj)nand_spl/{u-boot.lds,u-boot-spl,u-boot-spl.map,System.map}
	@rm -f $(TIMESTAMP_FILE) $(VERSION_FILE)
	@find $(OBJTREE) -type f \
		\( -name 'core' -o -name '.depend' -o -name '*.bak' -o -name '*~' \
		-o -name '*.o'	-o -name '*.a' -o -name '*.exe'	\) -print \
		| xargs rm -f


distclean:	
	rm -rf $(obj)*

#########################################################################
