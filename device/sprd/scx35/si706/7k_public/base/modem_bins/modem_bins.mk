LOCAL_PATH:= $(BOARDDIR)/modem_bins

PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/DSP_DM_G2.bin:dsp_w.bin \
	$(LOCAL_PATH)/nvitem_b1/nvitem.bin:nvitem_w.bin \
	$(LOCAL_PATH)/SC7702_sc7731g_band128_AndroidM.dat:modem_w.dat \
	$(LOCAL_PATH)/SC8800G_x30g_wcn_dts_modem.bin:modem_wcn.bin \
	$(LOCAL_PATH)/nvitem_wcn.bin:nvitem_wcn.bin 

