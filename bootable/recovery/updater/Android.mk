# Copyright 2009 The Android Open Source Project

LOCAL_PATH := $(call my-dir)

updater_src_files := \
	install.c \
	blockimg.c \
	updater.c

#
# Build a statically-linked binary to include in OTA packages
#
include $(CLEAR_VARS)

# Build only in eng, so we don't end up with a copy of this in /system
# on user builds.  (TODO: find a better way to build device binaries
# needed only for OTA packages.)
LOCAL_MODULE_TAGS := eng

LOCAL_SRC_FILES := $(updater_src_files)

ifeq ($(TARGET_USERIMAGES_USE_EXT4), true)
LOCAL_CFLAGS += -DUSE_EXT4
LOCAL_CFLAGS += -Wno-unused-parameter
LOCAL_C_INCLUDES += system/extras/ext4_utils
LOCAL_STATIC_LIBRARIES += \
    libext4_utils_static \
    libsparse_static \
    libz
endif

# SPRD: add for remove selinux opration
ifeq ($(NOT_HAVE_SELINUX), true)
$(warning not have selinux)
LOCAL_CFLAGS += -DNOT_HAVE_SELINUX
endif # NOT_HAVE_SELINUX

# SPRD: add for support format vfat @{
LOCAL_STATIC_LIBRARIES += \
    libvfat_format
# @}

# SPRD: add for ubi support
LOCAL_STATIC_LIBRARIES += \
    libubiutils

# SPRD: add fota support
ifeq ($(strip $(FOTA_UPDATE_SUPPORT)), true)
LOCAL_CFLAGS += -DFOTA_UPDATE_SUPPORT
LOCAL_STATIC_LIBRARIES += libfotaupdate
endif

# SPRD: add for spl update
LOCAL_STATIC_LIBRARIES += \
    libsplmerge

# SPRD: add for secure boot @{
LOCAL_CFLAGS += $(RECOVERY_COMMON_CGLAGS)
LOCAL_STATIC_LIBRARIES += \
    libsprd_sb_verifier \
    libfs_mgr \
    libefuse\
    libsprd_verify

ifeq ($(TARGET_USERIMAGES_USE_EXT4), true)
    LOCAL_CFLAGS += -DUSE_EXT4
    LOCAL_C_INCLUDES += system/extras/ext4_utils
    LOCAL_STATIC_LIBRARIES += libext4_utils_static libz
endif

#@}

LOCAL_STATIC_LIBRARIES += $(TARGET_RECOVERY_UPDATER_LIBS) $(TARGET_RECOVERY_UPDATER_EXTRA_LIBS)
LOCAL_STATIC_LIBRARIES += libapplypatch libedify libmtdutils libminzip libz
LOCAL_STATIC_LIBRARIES += libmincrypt libbz
LOCAL_STATIC_LIBRARIES += libcutils liblog libstdc++ libc
LOCAL_STATIC_LIBRARIES += libselinux
tune2fs_static_libraries := \
 libext2_com_err \
 libext2_blkid \
 libext2_quota \
 libext2_uuid_static \
 libext2_e2p \
 libext2fs
LOCAL_STATIC_LIBRARIES += libtune2fs $(tune2fs_static_libraries)

LOCAL_C_INCLUDES += external/e2fsprogs/misc
LOCAL_C_INCLUDES += $(LOCAL_PATH)/..

# Each library in TARGET_RECOVERY_UPDATER_LIBS should have a function
# named "Register_<libname>()".  Here we emit a little C function that
# gets #included by updater.c.  It calls all those registration
# functions.

# Devices can also add libraries to TARGET_RECOVERY_UPDATER_EXTRA_LIBS.
# These libs are also linked in with updater, but we don't try to call
# any sort of registration function for these.  Use this variable for
# any subsidiary static libraries required for your registered
# extension libs.

inc := $(call intermediates-dir-for,PACKAGING,updater_extensions)/register.inc

# Encode the value of TARGET_RECOVERY_UPDATER_LIBS into the filename of the dependency.
# So if TARGET_RECOVERY_UPDATER_LIBS is changed, a new dependency file will be generated.
# Note that we have to remove any existing depency files before creating new one,
# so no obsolete dependecy file gets used if you switch back to an old value.
inc_dep_file := $(inc).dep.$(subst $(space),-,$(sort $(TARGET_RECOVERY_UPDATER_LIBS)))
$(inc_dep_file): stem := $(inc).dep
$(inc_dep_file) :
	$(hide) mkdir -p $(dir $@)
	$(hide) rm -f $(stem).*
	$(hide) touch $@

$(inc) : libs := $(TARGET_RECOVERY_UPDATER_LIBS)
$(inc) : $(inc_dep_file)
	$(hide) mkdir -p $(dir $@)
	$(hide) echo "" > $@
	$(hide) $(foreach lib,$(libs),echo "extern void Register_$(lib)(void);" >> $@;)
	$(hide) echo "void RegisterDeviceExtensions() {" >> $@
	$(hide) $(foreach lib,$(libs),echo "  Register_$(lib)();" >> $@;)
	$(hide) echo "}" >> $@

$(call intermediates-dir-for,EXECUTABLES,updater,,,$(TARGET_PREFER_32_BIT))/updater.o : $(inc)
LOCAL_C_INCLUDES += $(dir $(inc))

inc :=
inc_dep_file :=

LOCAL_MODULE := updater

LOCAL_FORCE_STATIC_EXECUTABLE := true

include $(BUILD_EXECUTABLE)
