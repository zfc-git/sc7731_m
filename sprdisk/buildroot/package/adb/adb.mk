#############################################################
#
# adb
#
#############################################################

ADB_VERSION = 1.0
ADB_SITE = http://
ADB_SOURCE = adb-$(ADB_VERSION).tar.gz
ADB_DEPENDENCIES = zlib openssl

define ADB_BUILD_CMDS
	$(MAKE) CC="$(TARGET_CC)" LD="$(TARGET_LD)"	-C $(@D)
endef

define ADB_INSTALL_TARGET_CMDS
	$(INSTALL) -D $(@D)/adb $(TARGET_DIR)/usr/bin/adb
	$(INSTALL) -D $(@D)/adbd $(TARGET_DIR)/usr/bin/adbd
endef

define ADB_UNINSTALL_TARGET_CMDS
	rm -f $(TARGET_DIR)/usr/bin/adb
	rm -f $(TARGET_DIR)/usr/bin/adbd
endef

$(eval $(generic-package))
