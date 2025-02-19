# Spreadtrum additional build vars cleanner, additional build
# script and take them in vendor for porting easier

# Clear build packages
LOCAL_THEME_OVERLAY_PACKAGE :=
#com.android.mms
# LOCAL_THEME_GENERATE_PATH :=
LOCAL_THEME_RESOURCES :=
#res/
LOCAL_THEME_DUMMY_MANIFEST :=
#AndroidManifest.xml
LOCAL_THEME_VALUES :=
#$theme_values.xml
LOCAL_THEME_NAME :=
#HoloNewInverse
LOCAL_BUILD_VENDOR_FLAG :=

# Assume the building package can support overlay resource directly.
LOCAL_PACKAGE_SUPPORT_OVERLAY :=

# BUILD_THEMES := true
# PRODUCT_THEMEPACKAGE_STOREPATH := $(PRODUCT_OUT)/data/theme/

# Define addtional module which don't support DEX_PREOPT
LOCAL_DISABLE_DEX_PREOPT :=

LOCAL_DEPENDENCY_APPS :=
