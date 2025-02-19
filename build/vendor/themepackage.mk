
skip_definition:=
ifdef LOCAL_PACKAGE_OVERRIDES
  package_overridden := $(call set-inherited-package-variables)
  ifeq ($(strip $(package_overridden)),)
    skip_definition := true
  endif
endif

ifndef skip_definition

LOCAL_THEME_OVERLAY_PACKAGE := $(strip $(LOCAL_THEME_OVERLAY_PACKAGE))
ifeq ($(LOCAL_THEME_OVERLAY_PACKAGE),)
$(error $(LOCAL_PATH): Theme package modules *MUST* define LOCAL_THEME_OVERLAY_PACKAGE)
endif

#LOCAL_PACKAGE_NAME := $(strip $(LOCAL_PACKAGE_NAME))
#ifeq ($(LOCAL_PACKAGE_NAME),)
#$(error $(LOCAL_PATH): Package modules must define LOCAL_PACKAGE_NAME)
#endif

ifeq ($(strip $(LOCAL_PACKAGE_SUPPORT_OVERLAY)),)
LOCAL_PACKAGE_SUPPORT_OVERLAY := true
endif

# The suffix of the theme package, for building  it should always be nil
LOCAL_MODULE_SUFFIX :=

LOCAL_BUILD_VENDOR_FLAG := theme

ifneq ($(strip $(LOCAL_MODULE)),)
$(error $(LOCAL_PATH): Theme package modules MUST *NOT* define LOCAL_MODULE)
endif

ifeq ($(strip $(LOCAL_THEME_NAME)),)
LOCAL_THEME_NAME := default
endif

# If define PRODUCT_DEFAULT_THEME, treat that theme as default
ifeq ($(strip $(PRODUCT_DEFAULT_THEME)),$(strip $(LOCAL_THEME_NAME)))
LOCAL_THEME_NAME := default
endif

LOCAL_MODULE := $(LOCAL_THEME_NAME)$(LOCAL_THEME_OVERLAY_PACKAGE)

ifeq ($(strip $(LOCAL_MANIFEST_FILE)),)
LOCAL_MANIFEST_FILE := AndroidManifest.xml
endif

ifeq ($(strip $(LOCAL_THEME_DUMMY_MANIFEST)),)
LOCAL_FULL_MANIFEST_FILE := $(BUILD_SYSTEM_VENDOR)/dummy/AndroidManifest.xml
endif

PRIVATE_THEME_LOCAL_PATH := $(LOCAL_PATH)

# If you need to put the MANIFEST_FILE outside of LOCAL_PATH
# you can use FULL_MANIFEST_FILE
ifeq ($(strip $(LOCAL_FULL_MANIFEST_FILE)),)
LOCAL_FULL_MANIFEST_FILE := $(LOCAL_PATH)/AndroidManifest.xml
endif

# Protection of dimming LOCAL_MODULE_CLASS , this means where the intermediates
# stored in out/target/common/obj/$(LOCAL_MODULE_CLASS)
ifneq ($(strip $(LOCAL_MODULE_CLASS)),)
$(error $(LOCAL_PATH): Theme package modules may not set LOCAL_MODULE_CLASS)
endif
LOCAL_MODULE_CLASS := THEMEPACKAGE

# Package LOCAL_MODULE_TAGS default to optional
LOCAL_MODULE_TAGS := $(strip $(LOCAL_MODULE_TAGS))
ifeq ($(LOCAL_MODULE_TAGS),)
LOCAL_MODULE_TAGS := optional
endif

ifeq ($(filter tests, $(LOCAL_MODULE_TAGS)),)
# Force localization check if it's not tagged as tests.
LOCAL_AAPT_FLAGS := $(LOCAL_AAPT_FLAGS) -z
endif

# Fill LOCAL_THEME_RESOURCES
ifneq ($(strip $(LOCAL_THEME_RESOURCES)),)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/$(LOCAL_THEME_RESOURCES)
endif

ifeq (,$(LOCAL_RESOURCE_DIR))
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res
endif

# If is default, build pictures by overlay, not put them into theme packages.
ifeq ($(strip $(LOCAL_PACKAGE_SUPPORT_OVERLAY)),true)
ifeq ($(strip $(PRODUCT_BUILD_DEFAULT_THEME_BY_OVERLAY)),true)
ifeq ($(strip $(LOCAL_THEME_NAME)),default)
# Give and empty res location because they already build into original package
# by overlay. It may cut some ROM sizes.
LOCAL_RESOURCE_DIR := $(BUILD_SYSTEM_VENDOR)/dummy/res
endif
endif
endif

ifneq ($(strip $(LOCAL_MODULE_PATH)),)
$(error $(LOCAL_PATH): Theme package could not define LOCAL_MODULE_PATH)
endif # Fill LOCAL_THEME_RESOURCES

ifeq ($(strip $(LOCAL_THEME_VALUES)),)
LOCAL_THEME_VALUES_FULL_PATH := $(BUILD_SYSTEM_VENDOR)/dummy/theme_values.xml
else
LOCAL_THEME_VALUES_FULL_PATH := $(LOCAL_PATH)/$(LOCAL_THEME_VALUES)
endif

PRODUCT_THEME_OUT := $(PRODUCT_OUT)/system/etc/theme
ifeq ($(strip $(LOCAL_THEME_NAME)), default)
LOCAL_MODULE_PATH := $(PRODUCT_THEME_OUT)/default
else
LOCAL_MODULE_PATH := $(PRODUCT_THEME_OUT)/additional/$(LOCAL_THEME_NAME)
endif
# Support resources ovrelay in vendor
package_resource_overlays := $(strip \
    $(wildcard $(foreach dir, $(PRODUCT_PACKAGE_OVERLAYS), \
      $(addprefix $(dir)/, $(LOCAL_RESOURCE_DIR)))) \
    $(wildcard $(foreach dir, $(DEVICE_PACKAGE_OVERLAYS), \
      $(addprefix $(dir)/, $(LOCAL_RESOURCE_DIR)))))

LOCAL_RESOURCE_DIR := $(package_resource_overlays) $(LOCAL_RESOURCE_DIR)


all_resources := $(strip \
    $(foreach dir, $(LOCAL_RESOURCE_DIR), \
      $(addprefix $(dir)/, \
        $(patsubst res/%,%, \
          $(call find-subdir-assets,$(dir)) \
         ) \
       ) \
     ))

all_res_assets := $(strip $(all_resources))

package_expected_intermediates_COMMON := $(call local-intermediates-dir,COMMON)

LOCAL_BUILT_MODULE_STEM := package.apk
##### MUST include base_rules.xmk
#################################
include $(BUILD_SYSTEM)/base_rules.mk
#################################

full_android_manifest := $(LOCAL_FULL_MANIFEST_FILE)
$(LOCAL_INTERMEDIATE_TARGETS): \
    PRIVATE_ANDROID_MANIFEST := $(full_android_manifest)

ifneq ($(all_resources),)

endif  # all_resources

# Define the rule to build the actual package.
$(LOCAL_BUILT_MODULE): $(AAPT) | $(ZIPALIGN)

# Use the global AAPT_CONFIG
$(LOCAL_BUILT_MODULE): PRIVATE_PRODUCT_AAPT_CONFIG := $(PRODUCT_AAPT_CONFIG)
$(LOCAL_BUILT_MODULE): PRIVATE_PRODUCT_AAPT_PREF_CONFIG := $(PRODUCT_AAPT_PREF_CONFIG)

###########################################
$(LOCAL_BUILT_MODULE): PRIVATE_THEME_VALUES_FULL_PATH := $(LOCAL_THEME_VALUES_FULL_PATH)
$(LOCAL_BUILT_MODULE): $(all_res_assets) $(full_android_manifest)
	@echo "target Package: $(PRIVATE_MODULE) ($@)"
	$(create-empty-package)
	$(add-assets-to-theme-package)

############################################

# Save information about this package
PACKAGES.$(LOCAL_PACKAGE_NAME).OVERRIDES := $(strip $(LOCAL_OVERRIDES_PACKAGES))
PACKAGES.$(LOCAL_PACKAGE_NAME).RESOURCE_FILES := $(all_resources)
ifdef package_resource_overlays
PACKAGES.$(LOCAL_PACKAGE_NAME).RESOURCE_OVERLAYS := $(package_resource_overlays)
endif

PACKAGES := $(PACKAGES) $(LOCAL_PACKAGE_NAME)

endif # skip_definition
