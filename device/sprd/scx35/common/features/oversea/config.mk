# Spreadtrum Communications Inc.

# The config.mk will be called after "include $(APPLAY_PRODUCT_REVISION)"

# Currently, we split the feature by using properties, package including and
# overlays. We treat them as features

# WARNING, all the definations must use +=

# The features config already has a common path on
# vendor/sprd/open-source/features/

# Here is the different config from other platform
# Plan A, a big package about the drm addo
# FEATURES.PRODUCT_PACKAGES += DrmAddon

# Plan B, split the packages for drm addons
FEATURES.PRODUCT_PACKAGES += VideoDrm
