#
# Copyright (C) 2011 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# secure boot
BOARD_SECURE_BOOT_ENABLE := false
SECURE_BOOT_SIGNAL_KEY := false
include $(PLATCOMM)/BoardConfigCommon.mk
include $(PLATCOMM)/emmc/BoardConfigEmmc.mk

# board configs
TARGET_BOOTLOADER_BOARD_NAME := sp7731gea_hdr

# select camera 2M,3M,5M,8M
ifeq ($(strip $(ZEDIEL_PROJECT_CONFIG)), $(filter $(ZEDIEL_PROJECT_CONFIG), ZEDIEL_RD401))
CAMERA_SUPPORT_SIZE := 5M
FRONT_CAMERA_SUPPORT_SIZE := 0P3M
TARGET_BOARD_NO_FRONT_SENSOR := true
else
CAMERA_SUPPORT_SIZE := 2M
FRONT_CAMERA_SUPPORT_SIZE := 0P3M
TARGET_BOARD_NO_FRONT_SENSOR := false
endif
TARGET_BOARD_CAMERA_FLASH_CTRL := false

CAMERA_PHYSICAL_SIZE := 1_3inch
FRONT_CAMERA_PHYSICAL_SIZE := 1_5inch
# camera sensor type
CAMERA_SENSOR_TYPE_BACK := "ov8825_mipi_raw"
CAMERA_SENSOR_TYPE_FRONT := "GC2155_MIPI_yuv"
AT_CAMERA_SENSOR_TYPE_BACK := "autotest_ov8825_mipi_raw"
AT_CAMERA_SENSOR_TYPE_FRONT := "autotest_GC2155_MIPI_yuv"
#face detect
TARGET_BOARD_CAMERA_FACE_DETECT := true
TARGET_BOARD_CAMERA_FD_LIB := omron

#hdr capture
TARGET_BOARD_CAMERA_HDR_CAPTURE := true

#full screen display
TARGET_BOARD_CAMERA_FULL_SCREEN_DISPLAY := true

#uv denoise
TARGET_BOARD_CAMERA_UV_DENOISE := true

#capture mem
TARGET_BOARD_LOW_CAPTURE_MEM := true

#snesor interface
TARGET_BOARD_BACK_CAMERA_INTERFACE := mipi
TARGET_BOARD_FRONT_CAMERA_INTERFACE := mipi

#select camera zsl cap mode
TARGET_BOARD_CAMERA_CAPTURE_MODE := false

#sprd zsl feature
TARGET_BOARD_CAMERA_SPRD_PRIVATE_ZSL := true

#rotation capture
TARGET_BOARD_CAMERA_ROTATION_CAPTURE := true
ifeq ($(strip $(ZEDIEL_PROJECT_CONFIG)), $(filter $(ZEDIEL_PROJECT_CONFIG), ZEDIEL_SI706AKA ZEDIEL_SI706ASB ZEDIEL_SI101AKA ZEDIEL_RD401))
TARGET_BOARD_BACK_CAMERA_ROTATION := true
TARGET_BOARD_FRONT_CAMERA_ROTATION := true
endif
#rm zoom from 1080p recording
TARGET_BOARD_DISABLE_1080P_RECORDING_ZOOM := true

#select continuous auto focus
TARGET_BOARD_CAMERA_CAF := true

#select no camera flash
ifeq ($(strip $(ZEDIEL_PROJECT_CONFIG)), $(filter $(ZEDIEL_PROJECT_CONFIG), ZEDIEL_RD401))
TARGET_BOARD_CAMERA_NO_FLASH_DEV := true
endif

#image angle in different project
#TARGET_BOARD_CAMERA_ADAPTER_IMAGE := 180

#pre_allocate capture memory
TARGET_BOARD_CAMERA_PRE_ALLOC_CAPTURE_MEM := false
#sc8830g isp ver 0;sc9630 isp ver 1
TARGET_BOARD_CAMERA_ISP_SOFTWARE_VERSION := 0

#select mipi d-phy mode(none, phya, phyb, phyab)
TARGET_BOARD_FRONT_CAMERA_MIPI := phyb
TARGET_BOARD_BACK_CAMERA_MIPI := phya

#select ccir pclk src(source0, source1)
TARGET_BOARD_FRONT_CAMERA_CCIR_PCLK := source0
TARGET_BOARD_BACK_CAMERA_CCIR_PCLK := source0

# select WCN
BOARD_HAVE_BLUETOOTH := true
ifeq ($(strip $(USE_SPRD_WCN)),true)
BOARD_SPRD_WCNBT_SR2351 := true
BOARD_HAVE_FM_TROUT := true
BOARD_USE_SPRD_FMAPP := true
endif

#2351 GPS
BOARD_USE_SPRD_4IN1_GPS := true

# WIFI configs
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
WPA_SUPPLICANT_VERSION      := VER_2_1_DEVEL
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_sprdwl
BOARD_HOSTAPD_DRIVER        := NL80211
BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_sprdwl
BOARD_WLAN_DEVICE           := sc2351
WIFI_DRIVER_FW_PATH_PARAM   := "/data/misc/wifi/fwpath"
WIFI_DRIVER_FW_PATH_STA     := "sta_mode"
WIFI_DRIVER_FW_PATH_P2P     := "p2p_mode"
WIFI_DRIVER_FW_PATH_AP      := "ap_mode"
WIFI_DRIVER_MODULE_PATH     := "/system/lib/modules/sprdwl.ko"
WIFI_DRIVER_MODULE_NAME     := "sprdwl"

# select sensor
#USE_INVENSENSE_LIB := true
USE_SPRD_SENSOR_LIB := true
# BOARD_HAVE_ACC := mxc622x
# BOARD_HAVE_ACC := mc3xxx
# BOARD_HAVE_ACC := stk8baxx
BOARD_HAVE_ACC := compatible
BOARD_ACC_INSTALL := 6
BOARD_HAVE_ORI := NULL
BOARD_ORI_INSTALL := NULL
# BOARD_HAVE_PLS := stk3x1x
BOARD_HAVE_PLS := gsl1680
#BOARD_HAVE_PLS := NULL

# ext4 partition layout
TARGET_USERIMAGES_USE_EXT4 := true
BOARD_CACHEIMAGE_PARTITION_SIZE := 150000000
BOARD_PRODNVIMAGE_PARTITION_SIZE := 5242880
BOARD_SYSINFOIMAGE_PARTITION_SIZE := 5242880
BOARD_PERSISTIMAGE_PARTITION_SIZE := 2097152
BOARD_FLASH_BLOCK_SIZE := 4096
BOARD_CACHEIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_PRODNVIMAGE_FILE_SYSTEM_TYPE := ext4
BOARD_SYSINFOIMAGE_FILE_SYSTEM_TYPE := ext4

TARGET_SYSTEMIMAGES_SPARSE_EXT_DISABLED := true
TARGET_USERIMAGES_SPARSE_EXT_DISABLED := false
TARGET_CACHEIMAGES_SPARSE_EXT_DISABLED := false
TARGET_PRODNVIMAGES_SPARSE_EXT_DISABLED := true
TARGET_SYSINFOIMAGES_SPARSE_EXT_DISABLED := true

PRODUCT_COPY_FILES += $(BOARDDIR)/SC7730_UMS.xml:$(PRODUCT_OUT)/sp7731g_1h10.xml

TARGET_GPU_USE_TILE_ALIGN := true
DEVICE_GSP_NOT_SCALING_UP_TWICE := true

#TARGET_USES_LOGD := false

# powerhint HAL config
# sprdemand, interhotplug, interpowerdown
BOARD_POWERHINT_HAL := sprdemand
