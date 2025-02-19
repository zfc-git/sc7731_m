ifneq ($(strip $(PDK_FUSION_PLATFORM_ZIP)),)
    BUILDING_PDK := true
endif

# include aosp base configs
$(call inherit-product, $(PLATCOMM)/full_base.mk)

include $(APPLY_PRODUCT_REVISION)

# sprd telephony
PRODUCT_PACKAGES += \
	Dialer \
	Mms \
	messaging

ifneq ($(strip $(PRODUCT_USE_FPGA)),true)
# graphics modules
PRODUCT_PACKAGES += \
	libGLES_mali.so \
	libboost.so \
	mali.ko
endif

# video modules
PRODUCT_PACKAGES += \
	libstagefright_sprd_soft_mpeg4dec \
	libstagefright_sprd_soft_h264dec \
	libstagefright_sprd_mpeg4dec \
	libstagefright_sprd_mpeg4enc \
	libstagefright_sprd_h264dec \
	libstagefright_sprd_h264enc \
	libstagefright_sprd_vpxdec \
	libstagefright_soft_mjpgdec \
	libstagefright_sprd_mp3dec \
	libstagefright_soft_imaadpcmdec

# default audio
PRODUCT_PACKAGES += \
	audio.a2dp.default \
	audio.usb.default \
	audio.r_submix.default \
	libaudio-resampler

# sprd HAL modules
PRODUCT_PACKAGES += \
	audio.primary.sc8830 \
	audio_policy.sc8830 \
	gralloc.sc8830 \
	camera.sc8830 \
	camera2.sc8830 \
	lights.sc8830 \
	hwcomposer.sc8830 \
	sprd_gsp.sc8830 \
	power.sc8830
#	sensors.sc8830

# misc modules
PRODUCT_PACKAGES += \
	sqlite3 \
	charge \
	poweroff_alarm \
	mplayer \
	e2fsck \
	tinymix \
	audio_vbc_eq \
	calibration_init \
	modemd \
	engpc \
	iwnpi \
	dns6 \
	radvd \
	dhcp6s \
	dhcp6c \
	tcpdump \
	modem_control\
	libengappjni \
	cp_diskserver \
	batterysrv \
	refnotify \
	wcnd \
	libsprdstreamrecoder \
	libvtmanager  \
	zram.sh \
	bdt \
	blktrace \
	blkparse \
	tinyplay \
	tinycap \
	factorytest \
	wpa_cli \
	tune2fs \
    phasecheckserver \
	resize2fs \
        libril_oem

# CallLogProvider modules
PRODUCT_PACKAGES += \
        CallLogBackup

# general configs

PRODUCT_COPY_FILES += \
	$(ROOTCOMM)/root/init.sc8830.rc:root/init.sc8830.rc \
	$(ROOTCOMM)/root/init.sc8830.usb.rc:root/init.sc8830.usb.rc \
	$(ROOTCOMM)/system/usr/keylayout/gpio-keys.kl:system/usr/keylayout/gpio-keys.kl \
	$(ROOTCOMM)/system/usr/keylayout/headset-keyboard.kl:system/usr/keylayout/headset-keyboard.kl \
	$(ROOTCOMM)/system/usr/keylayout/sci-keypad.kl:system/usr/keylayout/sci-keypad.kl \
	$(ROOTCOMM)/system/etc/media_codecs.xml:system/etc/media_codecs.xml \
	$(ROOTCOMM)/system/etc/media_profiles.xml:system/etc/media_profiles.xml \
	$(ROOTCOMM)/system/media/engtest_sample.pcm:system/media/engtest_sample.pcm \
        vendor/sprd/open-source/res/spn/spn-conf.xml:system/etc/spn-conf.xml \
	vendor/sprd/open-source/res/productinfo/productinfo.bin:prodnv/productinfo.bin \
	vendor/sprd/open-source/res/CDROM/adb.iso:system/etc/adb.iso \
	vendor/sprd/open-source/apps/scripts/ext_data.sh:system/bin/ext_data.sh \
	vendor/sprd/open-source/apps/scripts/ext_kill.sh:system/bin/ext_kill.sh \
	vendor/sprd/open-source/apps/scripts/data_on.sh:system/bin/data_on.sh \
	vendor/sprd/open-source/apps/scripts/data_off.sh:system/bin/data_off.sh \
	vendor/sprd/open-source/apps/scripts/inputfreq.sh:system/bin/inputfreq.sh \
	vendor/sprd/open-source/apps/scripts/recoveryfreq.sh:system/bin/recoveryfreq.sh \
	vendor/sprd/open-source/apps/scripts/bih_config.sh:system/bin/bih_config.sh \
	vendor/sprd/open-source/apps/scripts/iosnoop.sh:system/bin/iosnoop.sh \
	frameworks/native/data/etc/handheld_core_hardware.xml:system/etc/permissions/handheld_core_hardware.xml \
	frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
	frameworks/native/data/etc/android.hardware.location.gps.xml:system/etc/permissions/android.hardware.location.gps.xml \
	frameworks/native/data/etc/android.hardware.touchscreen.multitouch.xml:system/etc/permissions/android.hardware.touchscreen.multitouch.xml \
	frameworks/native/data/etc/android.hardware.touchscreen.xml:system/etc/permissions/android.hardware.touchscreen.xml \
	frameworks/native/data/etc/android.hardware.telephony.gsm.xml:system/etc/permissions/android.hardware.telephony.gsm.xml \
	frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
	frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml

# multimedia configs
PRODUCT_COPY_FILES += \
        $(ROOTCOMM)/system/etc/media_codecs_google_audio.xml:system/etc/media_codecs_google_audio.xml \
        frameworks/av/media/libstagefright/data/media_codecs_google_telephony.xml:system/etc/media_codecs_google_telephony.xml \
        $(ROOTCOMM)/system/etc/media_codecs_google_video.xml:system/etc/media_codecs_google_video.xml \
        $(ROOTCOMM)/system/etc/media_codecs_performance.xml:system/etc/media_codecs_performance.xml

APN_VERSION := $(shell cat frameworks/base/core/res/res/xml/apns.xml|grep "<apns version"|cut -d \" -f 2)
PRODUCT_COPY_FILES += vendor/sprd/overlay/apn/apns-conf_$(APN_VERSION).xml:system/etc/apns-conf.xml \
                      vendor/sprd/overlay/apn/apns-conf_$(APN_VERSION).xml:system/etc/old-apns-conf.xml

ifeq ($(strip $(TARGET_GPU_PLATFORM)),midgard)
PRODUCT_COPY_FILES += vendor/sprd/modules/libgpu/gpu/midgard/driver/mali/mali_kbase.ko:root/lib/modules/mali.ko
else
PRODUCT_COPY_FILES += vendor/sprd/modules/libgpu/gpu/utgard/driver/mali/mali.ko:root/lib/modules/mali.ko
endif

ifeq ($(strip $(USE_SPRD_WCN)),true)
PRODUCT_PROPERTY_OVERRIDES += \
	ro.modem.wcn.enable=1 \
	ro.modem.wcn.dev=/dev/cpwcn \
	ro.modem.wcn.tty=/deiv/stty_wcn \
	ro.modem.wcn.diag=/dev/slog_wcn \
	ro.modem.wcn.assert=/dev/spipe_wcn2 \
	ro.modem.wcn.id=1 \
	ro.modem.wcn.count=1 \
	camera.disable_zsl_mode=1 \
	ro.digital.fm.support=1
endif

PRODUCT_PROPERTY_OVERRIDES += \
        ro.frp.pst=/dev/block/platform/sdio_emmc/by-name/persist

ifeq ($(TARGET_BUILD_VARIANT),user)

PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.sprd.modemreset=1 \
	ro.adb.secure=1 \
	persist.sys.sprd.wcnreset=1 \
	persist.sys.apr.enabled=0 \
        persist.sys.engpc.disable=1

else
PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.sprd.modemreset=0 \
	ro.adb.secure=0 \
	persist.sys.sprd.wcnreset=0 \
	persist.sys.apr.enabled=1 \
        persist.sys.engpc.disable=0

endif # TARGET_BUILD_VARIANT == user

PRODUCT_PROPERTY_OVERRIDES += \
	persist.service.agps.network=4g

PRODUCT_PROPERTY_OVERRIDES += \
        persist.sys.bsservice.enable=1

PRODUCT_PROPERTY_OVERRIDES += \
        ro.simlock.unlock.autoshow=1 \
        ro.simlock.unlock.bynv=0 \
        ro.simlock.onekey.lock=0

# APR auto upload
PRODUCT_PROPERTY_OVERRIDES += \
        persist.sys.apr.intervaltime=1 \
        persist.sys.apr.testgroup=CSSLAB \
        persist.sys.apr.autoupload=1

# add GPS engineer mode apk
PRODUCT_PACKAGES += \
        SGPS

#PRODUCT_BOOT_JARS += telephony-common2
#PRODUCT_PACKAGES += telephony-common2

# SPRD: add for low-memory @{
ifeq ($(strip $(PRODUCT_RAM)),low)
$(call inherit-product, frameworks/native/build/phone-hdpi-512-dalvik-heap.mk)
PRODUCT_PROPERTY_OVERRIDES += \
	   ro.board_ram_size=mid \
	   ro.config.low_ram=true \
	   ro.product.ram=low
else
ifeq ($(strip $(PRODUCT_RAM)),high)
$(call inherit-product, frameworks/native/build/phone-xhdpi-1024-dalvik-heap.mk)
PRODUCT_PROPERTY_OVERRIDES += \
	   ro.board_ram_size=high \
	   ro.config.low_ram=false \
	   ro.product.ram=high
endif
endif
# @}

#add for thermal
PRODUCT_COPY_FILES += vendor/sprd/open-source/res/thermal/scx20/thermalSensorsConfig.xml:/system/etc/thermalSensorsConfig.xml
PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.bsservice.enable=1
