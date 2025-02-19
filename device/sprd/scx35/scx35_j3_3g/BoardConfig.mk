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

#ext4 partition
include $(BOARDDIR)/BoardPartitionConfig.mk

# board configs
TARGET_BOOTLOADER_BOARD_NAME := scx35_j3_3g


#TARGET_GPU_PP_CORE := 1

#support hal1.0,hal3.2
TARGET_BOARD_CAMERA_HAL_VERSION := 1.0

# camera sensor type
CAMERA_SENSOR_TYPE_BACK := "s5k4h5yc_mipi"
CAMERA_SENSOR_TYPE_FRONT := "s5k5e3yx_mipi"

# select camera 2M,3M,5M,8M
CAMERA_SUPPORT_SIZE := 8M
FRONT_CAMERA_SUPPORT_SIZE := 5M
TARGET_BOARD_NO_FRONT_SENSOR := false
TARGET_BOARD_CAMERA_FLASH_CTRL := false

#read sensor otp to isp
TARGET_BOARD_CAMERA_READOTP_TO_ISP := true

# use sprd auto lens
TARGET_BOARD_CAMERA_SPRD_AUTOLENS := false

#otp version, v0(OTP on Grandprime, Z3) v1(OTP on J1MINI) v2(Without OTP on TabG)
TARGET_BOARD_CAMERA_OTP_VERSION := 0

#read otp method 1:from kernel 0:from user
TARGET_BOARD_CAMERA_READOTP_METHOD := 1

#face detect
TARGET_BOARD_CAMERA_FACE_DETECT := true
TARGET_BOARD_CAMERA_FD_LIB := omron

#sensor interface
TARGET_BOARD_BACK_CAMERA_INTERFACE := mipi
TARGET_BOARD_FRONT_CAMERA_INTERFACE := mipi

#select camera zsl cap mode
TARGET_BOARD_CAMERA_CAPTURE_MODE := true

#select camera zsl force cap mode
TARGET_BOARD_CAMERA_FORCE_ZSL_MODE := true

#rotation capture
TARGET_BOARD_CAMERA_ROTATION_CAPTURE := false

#select camera not support autofocus
TARGET_BOARD_CAMERA_NO_AUTOFOCUS_DEV := false


#uv denoise enable
TARGET_BOARD_CAMERA_CAPTURE_DENOISE := false

#y denoise enable
TARGET_BOARD_CAMERA_Y_DENOISE := true

#select continuous auto focus
TARGET_BOARD_CAMERA_CAF := true

#select ACuteLogic awb algorithm
TARGET_BOARD_USE_ALC_AWB := true

#pre_allocate capture memory
TARGET_BOARD_CAMERA_PRE_ALLOC_CAPTURE_MEM := true

#sc8830g isp ver 0;sc9630 isp ver 1;tshark2 isp version 2
TARGET_BOARD_CAMERA_ISP_SOFTWARE_VERSION := 2

#support auto anti-flicker
TARGET_BOARD_CAMERA_ANTI_FLICKER := true

#multi cap memory mode
TARGET_BOARD_MULTI_CAP_MEM := true

#select mipi d-phy mode(none, phya, phyb, phyab)
TARGET_BOARD_FRONT_CAMERA_MIPI := phyc
TARGET_BOARD_BACK_CAMERA_MIPI := phyab

#select ccir pclk src(source0, source1)
TARGET_BOARD_FRONT_CAMERA_CCIR_PCLK := source0
TARGET_BOARD_BACK_CAMERA_CCIR_PCLK := source0

#hdr effect enable
TARGET_BOARD_CAMERA_HDR_CAPTURE := true

#close logd
#TARGET_USES_LOGD := false

# select WCN
BOARD_HAVE_BLUETOOTH := true
BOARD_HAVE_BLUETOOTH_SPRD := true
BOARD_HAVE_FM_BCM := true
BOARD_USE_SPRD_FMAPP := true
SPRD_EXTERNAL_WCN := true
WCN_EXTENSION := true
BOARD_SPRD_WCNBT_MARLIN := true

#2351 GPS
BOARD_USE_SPRD_4IN1_GPS := false
#FM VBC IIS configs
BOARD_AUDIO_OLD_MODEM := false

# WIFI configs
BOARD_WPA_SUPPLICANT_DRIVER := NL80211
WPA_SUPPLICANT_VERSION      := VER_2_1_DEVEL
BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_sprdwl
BOARD_HOSTAPD_DRIVER        := NL80211
BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_sprdwl
BOARD_WLAN_DEVICE           := sc2341
WIFI_DRIVER_FW_PATH_PARAM   := "/data/misc/wifi/fwpath"
WIFI_DRIVER_FW_PATH_STA     := "sta_mode"
WIFI_DRIVER_FW_PATH_P2P     := "p2p_mode"
WIFI_DRIVER_FW_PATH_AP      := "ap_mode"
WIFI_DRIVER_MODULE_PATH     := "/system/lib/modules/sprdwl.ko"
WIFI_DRIVER_MODULE_NAME     := "sprdwl"


# select sensor
#USE_INVENSENSE_LIB := true
USE_SPRD_SENSOR_LIB := true
BOARD_HAVE_ACC := K2hh
BOARD_ACC_INSTALL := 0 
BOARD_HAVE_ORI := NULL
BOARD_ORI_INSTALL := NULL
BOARD_HAVE_PLS := LTR558ALS

# use sdcardfs
TARGET_USE_SDCARDFS := true


DEVICE_GSP_NOT_SCALING_UP_TWICE := true


#select ipv6
BOARD_SUPPORT_IPV6 := true
