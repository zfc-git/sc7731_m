include device/sprd/scx35/sp7731g_1h10/sp7731g_1h10_hd_native.mk

BUILDING_PDK_NATIVE := true

PRODUCT_REVISION := oversea multi-lang
include $(APPLY_PRODUCT_REVISION)

# Overrides
PRODUCT_NAME := si706c7ka
PRODUCT_DEVICE := $(TARGET_BOARD)
PRODUCT_MODEL := SP7731A
PRODUCT_BRAND := SPRD
PRODUCT_MANUFACTURER := SPRD

# ifndef PRODUCT_LOCALES
PRODUCT_LOCALES :=en_US zh_CN zh_HK zh_TW en_US en_AU en_CA en_GB en_IE en_IN en_NZ en_SG en_ZA ar_EG ar_IL fa_IR ru_RU fr_BE fr_CA fr_CH fr_FR ro_RO ms_MY bn_BD pt_BR pt_PT sw_TZ tl_PH th_TH tr_TR es_ES es_US hi_IN in_ID vi_VN ha_GH my_MM ce_PH te_IN ta_IN ug_CN ur_PK am_ET pl_PL bo_CN da_DK de_AT de_CH de_DE de_LI gu_IN nl_BE nl_NL km_KH cs_CZ lo_LA pa_IN sl_SI ja_JP uk_UA el_GR it_CH it_IT as_ET et_EE ml_IN kn_IN mr_IN iw_IL sv_SE no_NO sq_AL sq_MK lt_LT sr_RS sk_SK fi_FI ca_ES hr_HR lv_LV hu_HU bg_BG hy_AM
# endif

#Default language when first Boot
PRODUCT_PROPERTY_OVERRIDES += ro.product.locale.language=en
PRODUCT_PROPERTY_OVERRIDES += ro.product.locale.region=US