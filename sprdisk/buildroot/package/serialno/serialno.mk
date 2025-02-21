#############################################################
#
# serialno
#
#############################################################

SERIALNO_VERSION = 1.0
SERIALNO_SITE = http://
SERIALNO_SOURCE = serialno-$(SERIALNO_VERSION).tar.gz

define SERIALNO_BUILD_CMDS
	$(MAKE) CC="$(TARGET_CC)" LD="$(TARGET_LD)"	-C $(@D)
endef

define SERIALNO_INSTALL_TARGET_CMDS
	$(INSTALL) -D $(@D)/serialno $(TARGET_DIR)/usr/bin/serialno
endef

define SERIALNO_UNINSTALL_TARGET_CMDS
	rm -f $(TARGET_DIR)/usr/bin/serialno
endef

$(eval $(generic-package))
