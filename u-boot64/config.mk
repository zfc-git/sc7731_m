#
# (C) Copyright 2000-2013
# Wolfgang Denk, DENX Software Engineering, wd@denx.de.
#
# SPDX-License-Identifier:	GPL-2.0+
#
#########################################################################

# Set shell to bash if possible, otherwise fall back to sh
SHELL := $(shell if [ -x "$$BASH" ]; then echo $$BASH; \
	else if [ -x /bin/bash ]; then echo /bin/bash; \
	else echo sh; fi; fi)

export	SHELL

ifeq ($(CURDIR),$(SRCTREE))
dir :=
else
dir := $(subst $(SRCTREE)/,,$(CURDIR))
endif

ifneq ($(OBJTREE),$(SRCTREE))
# Create object files for SPL in a separate directory
ifeq ($(CONFIG_SPL_BUILD),y)
ifeq ($(CONFIG_TPL_BUILD),y)
obj := $(if $(dir),$(TPLTREE)/$(dir)/,$(TPLTREE)/)
else
obj := $(if $(dir),$(SPLTREE)/$(dir)/,$(SPLTREE)/)
endif
else
obj := $(if $(dir),$(OBJTREE)/$(dir)/,$(OBJTREE)/)
endif
src := $(if $(dir),$(SRCTREE)/$(dir)/,$(SRCTREE)/)

$(shell mkdir -p $(obj))
else
# Create object files for SPL in a separate directory
ifeq ($(CONFIG_SPL_BUILD),y)
ifeq ($(CONFIG_TPL_BUILD),y)
obj := $(if $(dir),$(TPLTREE)/$(dir)/,$(TPLTREE)/)
else
obj := $(if $(dir),$(SPLTREE)/$(dir)/,$(SPLTREE)/)

endif
$(shell mkdir -p $(obj))
else
obj :=
endif
src :=
endif

# clean the slate ...
PLATFORM_RELFLAGS =
PLATFORM_CPPFLAGS =
PLATFORM_LDFLAGS =

#########################################################################

HOSTCFLAGS	= -Wall -Wstrict-prototypes -O2 -fomit-frame-pointer \
		  $(HOSTCPPFLAGS)
HOSTSTRIP	= strip

#
# Mac OS X / Darwin's C preprocessor is Apple specific.  It
# generates numerous errors and warnings.  We want to bypass it
# and use GNU C's cpp.	To do this we pass the -traditional-cpp
# option to the compiler.  Note that the -traditional-cpp flag
# DOES NOT have the same semantics as GNU C's flag, all it does
# is invoke the GNU preprocessor in stock ANSI/ISO C fashion.
#
# Apple's linker is similar, thanks to the new 2 stage linking
# multiple symbol definitions are treated as errors, hence the
# -multiply_defined suppress option to turn off this error.
#

ifeq ($(HOSTOS),darwin)
# get major and minor product version (e.g. '10' and '6' for Snow Leopard)
DARWIN_MAJOR_VERSION	= $(shell sw_vers -productVersion | cut -f 1 -d '.')
DARWIN_MINOR_VERSION	= $(shell sw_vers -productVersion | cut -f 2 -d '.')

os_x_before	= $(shell if [ $(DARWIN_MAJOR_VERSION) -le $(1) -a \
	$(DARWIN_MINOR_VERSION) -le $(2) ] ; then echo "$(3)"; else echo "$(4)"; fi ;)

# Snow Leopards build environment has no longer restrictions as described above
HOSTCC		 = $(call os_x_before, 10, 5, "cc", "gcc")
HOSTCFLAGS	+= $(call os_x_before, 10, 4, "-traditional-cpp")
HOSTLDFLAGS	+= $(call os_x_before, 10, 5, "-multiply_defined suppress")
else
HOSTCC		= gcc
endif

ifeq ($(HOSTOS),cygwin)
HOSTCFLAGS	+= -ansi
endif

# We build some files with extra pedantic flags to try to minimize things
# that won't build on some weird host compiler -- though there are lots of
# exceptions for files that aren't complaint.

HOSTCFLAGS_NOPED = $(filter-out -pedantic,$(HOSTCFLAGS))
HOSTCFLAGS	+= -pedantic

#########################################################################
#
# Option checker, gcc version (courtesy linux kernel) to ensure
# only supported compiler options are used
#
CC_OPTIONS_CACHE_FILE := $(OBJTREE)/include/generated/cc_options.mk
CC_TEST_OFILE := $(OBJTREE)/include/generated/cc_test_file.o

-include $(CC_OPTIONS_CACHE_FILE)

cc-option-sys = $(shell mkdir -p $(dir $(CC_TEST_OFILE)); \
		if $(CC) $(CFLAGS) $(1) -S -xc /dev/null -o $(CC_TEST_OFILE) \
		> /dev/null 2>&1; then \
		echo 'CC_OPTIONS += $(strip $1)' >> $(CC_OPTIONS_CACHE_FILE); \
		echo "$(1)"; fi)

ifeq ($(CONFIG_CC_OPT_CACHE_DISABLE),y)
cc-option = $(strip $(if $(call cc-option-sys,$1),$1,$2))
else
cc-option = $(strip $(if $(findstring $1,$(CC_OPTIONS)),$1,\
		$(if $(call cc-option-sys,$1),$1,$2)))
endif

# cc-version
# Usage gcc-ver := $(call cc-version)
cc-version = $(shell $(SHELL) $(SRCTREE)/scripts/gcc-version.sh $(CC))
binutils-version = $(shell $(SHELL) $(SRCTREE)/scripts/binutils-version.sh $(AS))
dtc-version = $(shell $(SHELL) $(SRCTREE)/scripts/dtc-version.sh $(DTC))

#
# Include the make variables (CC, etc...)
#
AS	= $(CROSS_COMPILE)as

# Always use GNU ld
LD	= $(shell if $(CROSS_COMPILE)ld.bfd -v > /dev/null 2>&1; \
		then echo "$(CROSS_COMPILE)ld.bfd"; else echo "$(CROSS_COMPILE)ld"; fi;)

CC	= $(CROSS_COMPILE)gcc
CPP	= $(CC) -E
AR	= $(CROSS_COMPILE)ar
NM	= $(CROSS_COMPILE)nm
LDR	= $(CROSS_COMPILE)ldr
STRIP	= $(CROSS_COMPILE)strip
OBJCOPY = $(CROSS_COMPILE)objcopy
OBJDUMP = $(CROSS_COMPILE)objdump
RANLIB	= $(CROSS_COMPILE)RANLIB
DTC	= dtc
CHECK	= sparse

#########################################################################

# Load generated board configuration
ifeq ($(CONFIG_TPL_BUILD),y)
# Include TPL autoconf
sinclude $(OBJTREE)/include/tpl-autoconf.mk
else
ifeq ($(CONFIG_SPL_BUILD),y)
# Include SPL autoconf
sinclude $(OBJTREE)/include/spl-autoconf.mk
else
# Include normal autoconf
sinclude $(OBJTREE)/include/autoconf.mk
endif
endif
sinclude $(OBJTREE)/include/config.mk

# Some architecture config.mk files need to know what CPUDIR is set to,
# so calculate CPUDIR before including ARCH/SOC/CPU config.mk files.
# Check if arch/$ARCH/cpu/$CPU exists, otherwise assume arch/$ARCH/cpu contains
# CPU-specific code.
CPUDIR=arch/$(ARCH)/cpu/$(CPU)
ifneq ($(SRCTREE)/$(CPUDIR),$(wildcard $(SRCTREE)/$(CPUDIR)))
CPUDIR=arch/$(ARCH)/cpu
endif

sinclude $(TOPDIR)/arch/$(ARCH)/config.mk	# include architecture dependend rules
sinclude $(TOPDIR)/$(CPUDIR)/config.mk		# include  CPU	specific rules

ifdef	SOC
sinclude $(TOPDIR)/$(CPUDIR)/$(SOC)/config.mk	# include  SoC	specific rules
endif
ifdef	VENDOR
BOARDDIR = $(VENDOR)/$(BOARD)
else
BOARDDIR = $(BOARD)
endif
ifdef	BOARD
sinclude $(TOPDIR)/board/$(BOARDDIR)/config.mk	# include board specific rules
endif

#########################################################################

# We don't actually use $(ARFLAGS) anywhere anymore, so catch people
# who are porting old code to latest mainline but not updating $(AR).
ARFLAGS = $(error update your Makefile to use cmd_link_o_target and not AR)
RELFLAGS= $(PLATFORM_RELFLAGS)
DBGFLAGS= -g
OPTFLAGS= -Os #-fomit-frame-pointer

OBJCFLAGS += --gap-fill=0xff

gccincdir := $(shell $(CC) -print-file-name=include)

CPPFLAGS := $(DBGFLAGS) $(OPTFLAGS) $(RELFLAGS)		\
	-D__KERNEL__

CPPFLAGS += $(UBOOT_DEBUG_FLAG)

# Enable garbage collection of un-used sections for SPL
ifeq ($(CONFIG_SPL_BUILD),y)
CPPFLAGS += -ffunction-sections -fdata-sections
LDFLAGS_FINAL += --gc-sections
endif

# TODO(sjg@chromium.org): Is this correct on Mac OS?

# MXSImage needs LibSSL
ifneq ($(CONFIG_MX23)$(CONFIG_MX28),)
HOSTLIBS	+= -lssl -lcrypto
# Add CONFIG_MXS into host CFLAGS, so we can check whether or not register
# the mxsimage support within tools/mxsimage.c .
HOSTCFLAGS	+= -DCONFIG_MXS
endif

ifdef CONFIG_FIT_SIGNATURE
HOSTLIBS	+= -lssl -lcrypto

# This affects include/image.h, but including the board config file
# is tricky, so manually define this options here.
HOSTCFLAGS	+= -DCONFIG_FIT_SIGNATURE
endif

ifneq ($(CONFIG_SYS_TEXT_BASE),)
CPPFLAGS += -DCONFIG_SYS_TEXT_BASE=$(CONFIG_SYS_TEXT_BASE)
endif

ifeq ($(CONFIG_SPL_BUILD),y)
CPPFLAGS += -DCONFIG_SPL_BUILD
ifeq ($(CONFIG_TPL_BUILD),y)
CPPFLAGS += -DCONFIG_TPL_BUILD
endif
endif

ifeq ($(AUTOBOOT_FLAG), true)
CPPFLAGS += -DCONFIG_AUTOBOOT
endif

ifeq ($(strip $(UBOOT_CONFIG_PRODUCT)),sp9830aec_4m_h110_dt)
CPPFLAGS += -DUSE_EXT_CHARGE_IC
endif
ifeq ($(strip $(UBOOT_DEFCONFIG)),sp9832a_2h11_4m)
CPPFLAGS += -DUSE_EXT_CHARGE_IC
endif
ifeq ($(strip $(UBOOT_CONFIG_PRODUCT)),jiaotu_tc_volte)
CPPFLAGS += -DVOLTE_SUPPORT
endif
ifeq ($(strip $(UBOOT_CONFIG_PRODUCT)),sp9832a_2h11_4msamsung)
CPPFLAGS += -DSP9832A_2H11_4MSAMSUNG
endif
# It's ugly code for this macro.
# Because this product will use HD screen to simulate FWVGA screen, the code writes in uboot.
ifeq ($(strip $(CHIPRAM_CONFIG_PRODUCT)),SP9832A_2H11_32V4_VOLTE)
CPPFLAGS += -DSP9832A_2H11_32V4_VOLTE_LCD
endif
# Does this architecture support generic board init?
ifeq ($(__HAVE_ARCH_GENERIC_BOARD),)
ifneq ($(CONFIG_SYS_GENERIC_BOARD),)
CHECK_GENERIC_BOARD = $(error Your architecture does not support generic board. \
Please undefined CONFIG_SYS_GENERIC_BOARD in your board config file)
endif
endif

# Sandbox needs the base flags and includes, so keep them around
BASE_CPPFLAGS := $(CPPFLAGS)

ifneq ($(OBJTREE),$(SRCTREE))
BASE_INCLUDE_DIRS := $(OBJTREE)/include
endif

BASE_INCLUDE_DIRS += $(TOPDIR)/include $(SRCTREE)/arch/$(ARCH)/include

CPPFLAGS += $(patsubst %, -I%, $(BASE_INCLUDE_DIRS))
CPPFLAGS += -fno-builtin -ffreestanding -nostdinc	\
	-isystem $(gccincdir) -pipe $(PLATFORM_CPPFLAGS)

CFLAGS := $(CPPFLAGS) -Wall -Wstrict-prototypes

ifdef BUILD_TAG
CFLAGS += -DBUILD_TAG='"$(BUILD_TAG)"'
endif

CFLAGS_SSP := $(call cc-option,-fno-stack-protector)
CFLAGS += $(CFLAGS_SSP)
# Some toolchains enable security related warning flags by default,
# but they don't make much sense in the u-boot world, so disable them.
CFLAGS_WARN := $(call cc-option,-Wno-format-nonliteral) \
	       $(call cc-option,-Wno-format-security)
CFLAGS += $(CFLAGS_WARN)

# Report stack usage if supported
CFLAGS_STACK := $(call cc-option,-fstack-usage)
CFLAGS += $(CFLAGS_STACK)

BCURDIR = $(subst $(SRCTREE)/,,$(CURDIR:$(obj)%=%))

ifeq ($(findstring examples/,$(BCURDIR)),)
ifeq ($(CONFIG_SPL_BUILD),)
ifdef FTRACE
CFLAGS += -finstrument-functions -DFTRACE
endif
endif
endif

CFLAGS += $(ZEDIEL_CDEFS) -DBUILD_LK

ifneq ($(findstring ZEDIEL,$(ZEDIEL_LCD_MODUEL)),)
CFLAGS += -DZEDIEL_LCD_PWRCTRL
endif

# $(CPPFLAGS) sets -g, which causes gcc to pass a suitable -g<format>
# option to the assembler.
AFLAGS_DEBUG :=

# turn jbsr into jsr for m68k
ifeq ($(ARCH),m68k)
ifeq ($(findstring 3.4,$(shell $(CC) --version)),3.4)
AFLAGS_DEBUG := -Wa,-gstabs,-S
endif
endif

AFLAGS := $(AFLAGS_DEBUG) -D__ASSEMBLY__ $(CPPFLAGS)

LDFLAGS += $(PLATFORM_LDFLAGS)
LDFLAGS_FINAL += -Bstatic

LDFLAGS_u-boot += -T $(obj)u-boot.lds $(LDFLAGS_FINAL)
ifneq ($(CONFIG_SYS_TEXT_BASE),)
LDFLAGS_u-boot += -Ttext $(CONFIG_SYS_TEXT_BASE)
endif

LDFLAGS_$(SPL_BIN) += -T $(obj)u-boot-spl.lds $(LDFLAGS_FINAL)
ifneq ($(CONFIG_SPL_TEXT_BASE),)
LDFLAGS_$(SPL_BIN) += -Ttext $(CONFIG_SPL_TEXT_BASE)
endif

# Linus' kernel sanity checking tool
CHECKFLAGS     := -D__linux__ -Dlinux -D__STDC__ -Dunix -D__unix__ \
		  -Wbitwise -Wno-return-void -D__CHECK_ENDIAN__ $(CF)

# Location of a usable BFD library, where we define "usable" as
# "built for ${HOST}, supports ${TARGET}".  Sensible values are
# - When cross-compiling: the root of the cross-environment
# - Linux/ppc (native): /usr
# - NetBSD/ppc (native): you lose ... (must extract these from the
#   binutils build directory, plus the native and U-Boot include
#   files don't like each other)
#
# So far, this is used only by tools/gdb/Makefile.

ifeq ($(HOSTOS),darwin)
BFD_ROOT_DIR =		/usr/local/tools
else
ifeq ($(HOSTARCH),$(ARCH))
# native
BFD_ROOT_DIR =		/usr
else
#BFD_ROOT_DIR =		/LinuxPPC/CDK		# Linux/i386
#BFD_ROOT_DIR =		/usr/pkg/cross		# NetBSD/i386
BFD_ROOT_DIR =		/opt/powerpc
endif
endif

#########################################################################

export	HOSTCC HOSTCFLAGS HOSTLDFLAGS PEDCFLAGS HOSTSTRIP CROSS_COMPILE \
	AS LD CC CPP AR NM STRIP OBJCOPY OBJDUMP MAKE
export	CONFIG_SYS_TEXT_BASE PLATFORM_CPPFLAGS PLATFORM_RELFLAGS CPPFLAGS CFLAGS AFLAGS

#########################################################################

# Allow boards to use custom optimize flags on a per dir/file basis
ALL_AFLAGS = $(AFLAGS) $(AFLAGS_$(BCURDIR)/$(@F)) $(AFLAGS_$(BCURDIR))
ALL_CFLAGS = $(CFLAGS) $(CFLAGS_$(BCURDIR)/$(@F)) $(CFLAGS_$(BCURDIR))
EXTRA_CPPFLAGS = $(CPPFLAGS_$(BCURDIR)/$(@F)) $(CPPFLAGS_$(BCURDIR))
ALL_CFLAGS += $(EXTRA_CPPFLAGS)

# The _DEP version uses the $< file target (for dependency generation)
# See rules.mk
EXTRA_CPPFLAGS_DEP = $(CPPFLAGS_$(BCURDIR)/$(addsuffix .o,$(basename $<))) \
		$(CPPFLAGS_$(BCURDIR))
$(obj)%.s:	%.S
	$(CPP) $(ALL_AFLAGS) -o $@ $<
$(obj)%.o:	%.S
	$(CC)  $(ALL_AFLAGS) -o $@ $< -c
$(obj)%.o:	%.c
ifneq ($(CHECKSRC),0)
	$(CHECK) $(CHECKFLAGS) $(ALL_CFLAGS) $<
endif
	$(CC)  $(ALL_CFLAGS) -o $@ $< -c
$(obj)%.i:	%.c
	$(CPP) $(ALL_CFLAGS) -o $@ $< -c
$(obj)%.s:	%.c
	$(CC)  $(ALL_CFLAGS) -o $@ $< -c -S

#########################################################################

# If the list of objects to link is empty, just create an empty built-in.o
cmd_link_o_target = $(if $(strip $1),\
		      $(LD) $(LDFLAGS) -r -o $@ $1,\
		      rm -f $@; $(AR) rcs $@ )

#########################################################################
