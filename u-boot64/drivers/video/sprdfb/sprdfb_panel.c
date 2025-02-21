/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
 *
 */

#include "sprdfb_chip_common.h"
#include "sprdfb.h"
#include "sprdfb_panel.h"

#include <sprd_regulator.h>

#if defined(CONFIG_I2C) && defined(CONFIG_SPI)
extern struct panel_if_ctrl sprdfb_mcu_ctrl;
#endif
extern struct panel_if_ctrl sprdfb_rgb_ctrl;
extern struct panel_if_ctrl sprdfb_mipi_ctrl;

extern struct panel_spec lcd_s6e8aa5x01_mipi_spec;
extern struct panel_spec lcd_nt35510_mipi_spec;
extern struct panel_spec lcd_nt35516_mipi_spec;
extern struct panel_spec lcd_nt35516_mcu_spec;
extern struct panel_spec lcd_nt35516_rgb_spi_spec;
extern struct panel_spec lcd_otm8018b_mipi_spec;
extern struct panel_spec lcd_hx8363_mcu_spec;
extern struct panel_spec lcd_panel_hx8363_rgb_spi_spec;
extern struct panel_spec lcd_panel_hx8363_rgb_spi_spec_viva;
extern struct panel_spec lcd_s6d0139_spec;
extern struct panel_spec lcd_otm1283a_mipi_spec;
extern struct panel_spec lcd_ssd2075_mipi_spec;
extern struct panel_spec lcd_panel_st7789v;
extern struct panel_spec lcd_panel_sc7798_rgb_spi;
extern struct panel_spec lcd_hx8369b_mipi_spec;
extern struct panel_spec lcd_sd7798d_mipi_spec;
extern struct panel_spec lcd_nt35502_mipi_spec;
extern struct panel_spec lcd_panel_ili9341;
extern struct panel_spec lcd_panel_ili9486;
extern struct panel_spec lcd_panel_ili9486_rgb_spi;
extern struct panel_spec lcd_ili9486s1_mipi_spec;
extern struct panel_spec lcd_nt51017_mipi_lvds_spec;
extern struct panel_spec lcd_t8861_mipi_spec;
extern struct panel_spec lcd_hx8379a_mipi_spec;
extern struct panel_spec lcd_hx8379c_mipi_spec;
extern struct panel_spec lcd_hx8389c_mipi_spec;
extern struct panel_spec ili6150_lvds_spec;
extern struct panel_spec lcd_rm68180_mipi_spec;
extern struct panel_spec lcd_ili9806e_mipi_spec;
extern struct panel_spec lcd_ili9806e_2_mipi_spec;
extern struct panel_spec lcd_otm8019a_mipi_spec;
extern struct panel_spec lcd_fl10802_mipi_spec;
extern struct panel_spec lcd_jd9161_mipi_spec;
extern struct panel_spec lcd_hx8369b_mipi_vivaltoVE_spec;
extern struct panel_spec lcd_vx5b3d_mipi_spec;
extern struct panel_spec lcd_hx8369b_grandneo_mipi_spec;
extern struct panel_spec lcd_hx8369b_tshark2_j3_mipi_spec;
extern struct panel_spec lcd_sd7798d_mipi_spec;
extern struct panel_spec lcd_s6d77a1_mipi_spec;
extern struct panel_spec lcd_nt51017_mipi_spec;
extern struct panel_spec lcd_hx8394d_mipi_spec;
extern struct panel_spec lcd_hx8394a_mipi_spec;
extern struct panel_spec lcd_nt35596h_mipi_spec;
extern struct panel_spec lcd_nt35596h_rdc_mipi_spec;
extern struct panel_spec lcd_s6d7aa0x62_mipi_spec;
extern struct panel_spec lcd_ams549hq01_mipi_spec;
extern struct panel_spec lcd_ili9881c_mipi_spec;
extern struct panel_spec lcd_ek79007a_mipi_spec;
extern struct panel_spec lcd_si863_ek79007a_mipi_spec;
extern struct panel_spec lcd_ek79029_mipi_spec;
extern struct panel_spec lcd_jd9366_mipi_spec;
extern struct panel_spec lcd_jd9365_mipi_spec;
extern struct panel_spec lcd_jd9365_1129_mipi_spec;
extern struct panel_spec lcd_jd9367_mipi_spec;
extern struct panel_spec lcd_nt35521_mipi_spec;
extern struct panel_spec lcd_nt35521s_mipi_spec;
extern struct panel_spec lcd_ek79029_mipi_spec;
extern struct panel_spec lcd_bi32ta8001_21h_mipi_spec;
extern struct panel_spec lcd_cp51012_mipi_spec;
extern struct panel_spec lcd_s6d7aa0x01_mipi_spec;
extern struct panel_spec lcd_ili6180_mipi_spec;
extern struct panel_spec lcd_nt35521_mipi_spec;
extern struct panel_spec lcd_hx8279_mipi_spec;
extern struct panel_spec lcd_hx8271_mipi_spec;
extern struct panel_spec lcd_hx8260_mipi_spec;
extern struct panel_spec lcd_s7190a_mipi_spec;
extern struct panel_spec lcd_s6d7aa0x02_mipi_spec;
extern struct panel_spec lcd_nt35521s_n070icn_mipi_spec;
extern struct panel_spec lcd_ili9881c_sq101ab4ii312_mipi_spec;
void sprdfb_panel_remove(struct sprdfb_device *dev);

static ushort colormap[256];

static struct panel_cfg panel_cfg[] = {
#if defined (CONFIG_FB_LCD_ILI9881C_SQ101ABII312)|| defined (CONFIG_FB_LCD_K101IM2BYL03AL) || defined (CONFIG_FB_LCD_TV101WXU_N90_9881) ||defined (CONFIG_FB_LCD_WCGX88577_101)||defined (CONFIG_FB_LCD_LXJB101WIM260_27K)
		{
			.lcd_id = 0x9881,
			.panel = &lcd_ili9881c_sq101ab4ii312_mipi_spec,
		},
#endif

#if defined (CONFIG_FB_LCD_HX8379C_MIPI_SHARKL_J1POPLTE)
		{
			.lcd_id = 0x8379,
			.panel = &lcd_hx8379c_mipi_spec,
		},
#endif

#ifdef CONFIG_FB_LCD_NT35521S_N070ICN_PB1
{
	.lcd_id = 0x35521,
	.panel = &lcd_nt35521s_n070icn_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_S6E8AA5X01_MIPI
{
	.lcd_id = 0x400002,
	.panel = &lcd_s6e8aa5x01_mipi_spec,
},
#endif
#ifdef CONFIG_FB_LCD_NT35510_MIPI
	{
		.lcd_id = 0x10,
		.panel = &lcd_nt35510_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT35516_MIPI
	{
		.lcd_id = 0x16,
		.panel = &lcd_nt35516_mipi_spec,
	},
#endif

#if defined(CONFIG_FB_LCD_HX8394D_MIPI) || defined(CONFIG_FB_LCD_HX8394D_KD080D24_31NH_B1_MIPI) ||defined(CONFIG_FB_LCD_HX8394D_BLD_PBTT070H012) \
	||defined(CONFIG_FB_LCD_NT51021_SL007PN20D1417_A00)
	{
		.lcd_id = 0x8394,
		.panel = &lcd_hx8394d_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT35516_MCU
	{
		.lcd_id = 0x16,
		.panel = &lcd_nt35516_mcu_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT35516_RGB_SPI
	{
		.lcd_id = 0x16,
		.panel = &lcd_nt35516_rgb_spi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_OTM8018B_MIPI
	{
		.lcd_id = 0x18,
		.panel = &lcd_otm8018b_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8363_MCU
	{
		.lcd_id = 0x18,
		.panel = &lcd_hx8363_mcu_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_VIVA_RGB_SPI
	{
		.lcd_id = 0x63,
		.panel = &lcd_panel_hx8363_rgb_spi_spec_viva,
	},
#endif

#ifdef CONFIG_FB_LCD_RM68180_MIPI
	{
		.lcd_id = 0x80,
		.panel = &lcd_rm68180_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8363_RGB_SPI
	{
		.lcd_id = 0x84,
		.panel = &lcd_panel_hx8363_rgb_spi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_S6D0139
	{
		.lcd_id = 0x139,
		.panel = &lcd_s6d0139_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_OTM1283A_MIPI
	{
		.lcd_id = 0x1283,
		.panel = &lcd_otm1283a_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_SSD2075_MIPI
	{
		.lcd_id = 0x2075,
		.panel = &lcd_ssd2075_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_ST7789V_MCU
	{
		.lcd_id = 0x7789,
		.panel = &lcd_panel_st7789v,
	},
#endif

#ifdef CONFIG_FB_LCD_SC7798_RGB_SPI
	{
		.lcd_id = 0x7798,
		.panel = &lcd_panel_sc7798_rgb_spi,
	},
#endif

#if defined(CONFIG_FB_LCD_HX8369B_MIPI) || defined(CONFIG_FB_LCD_HX8369B_MIPI_COREPRIMELITE)|| defined(CONFIG_FB_LCD_HX8369B_MIPI_SHARKLS_Z3LTE) || defined(CONFIG_FB_LCD_HX8369B_MIPI_SHARKL_J1POPLTE) || defined (CONFIG_FB_LCD_HX8369B_MIPI_KIRAN3G)|| defined (CONFIG_FB_LCD_HX8369B_MIPI_SHARKLC_Z2LTE)
	{
		.lcd_id = 0x8369,
		.panel = &lcd_hx8369b_mipi_spec,
	},
#endif

#if defined(CONFIG_FB_LCD_SD7798D_MIPI_COREPRIMELITE) || defined(CONFIG_FB_LCD_SD7798D_MIPI_SHARKLS_Z3LTE)
	{
		.lcd_id = 0x55b8f0,
		.panel = &lcd_sd7798d_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT35502_MIPI
	{
		.lcd_id = 0x8370,
		.panel = &lcd_nt35502_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT51017_MIPI
	{
		.lcd_id = 0x51017,
		.panel = &lcd_nt51017_mipi_spec,
	},
#endif


#ifdef CONFIG_FB_LCD_ILI9341
	{
		.lcd_id = 0x9341,
		.panel = &lcd_panel_ili9341,
	},
#endif

#ifdef CONFIG_FB_LCD_ILI9486
	{
		.lcd_id = 0x9486,
		.panel = &lcd_panel_ili9486,
	},
#endif

#ifdef CONFIG_FB_LCD_ILI9486_RGB_SPI
	{
		.lcd_id = 0x9486,
		.panel = &lcd_panel_ili9486_rgb_spi,
	},
#endif

#ifdef CONFIG_FB_LCD_ILI9486S1_MIPI
	{
		.lcd_id = 0x8370,
		.panel = &lcd_ili9486s1_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8369B_MIPI_VIVALTO_VE
	{
		.lcd_id = 0x8369,
		.panel = &lcd_hx8369b_mipi_vivaltoVE_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_VX5B3D_MIPI
	{
		.lcd_id = 0x8282,
		.panel = &lcd_vx5b3d_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_GRANDNEO_MIPI
	{
		.lcd_id = 0x8369,
		.panel = &lcd_hx8369b_grandneo_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_TSHARK2_J3_MIPI
	{
		.lcd_id = 0x8369,
		.panel = &lcd_hx8369b_tshark2_j3_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_SD7798D_MIPI
	{
		.lcd_id = 0x55b8f0,
		.panel = &lcd_sd7798d_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_S6D77A1_MIPI_PIKEA_J1
	{
		.lcd_id = 0x55b810,
		.panel = &lcd_s6d77a1_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_NT51017_LVDS
	{
		.lcd_id = 0xC749,
		.panel = &lcd_nt51017_mipi_lvds_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_T8861_MIPI
	{
		.lcd_id = 0x04,
		.panel = &lcd_t8861_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8379A_MIPI
	{
		.lcd_id = 0x8379,
		.panel = &lcd_hx8379a_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8389C_MIPI
	{
		.lcd_id = 0x8389,
		.panel = &lcd_hx8389c_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_S6D7AA0X62_MIPI
	{
		.lcd_id = 0x6262,
		.panel = &lcd_s6d7aa0x62_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_ILI6150_LVDS
	{
		.lcd_id = 0x1806,
		.panel = &ili6150_lvds_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_ILI9806E_MIPI
{
    .lcd_id = 0x4,
    .panel = &lcd_ili9806e_mipi_spec,
},
#endif
#ifdef CONFIG_FB_LCD_ILI9806E_2_MIPI
{
    .lcd_id = 0x980602,
    .panel = &lcd_ili9806e_2_mipi_spec,
},
#endif
#ifdef CONFIG_FB_LCD_OTM8019A_MIPI
	{
		.lcd_id = 0x8019,
		.panel = &lcd_otm8019a_mipi_spec,
	},
#endif
#ifdef CONFIG_FB_LCD_FL10802_MIPI
	{
		.lcd_id = 0x1080,
		.panel = &lcd_fl10802_mipi_spec,
	},
#endif
#if defined(CONFIG_FB_LCD_JD9161_MIPI)||defined(CONFIG_FB_LCD_JD9161_XBD397_TN_WB076A)
	{
		.lcd_id = 0x916100,
		.panel = &lcd_jd9161_mipi_spec,
	},
#endif

#ifdef CONFIG_FB_LCD_HX8394D_MIPI
{
	.lcd_id = 0x8394,
	.panel = &lcd_hx8394d_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_HX8394A_MIPI
{
	.lcd_id = 0x8394,
	.panel = &lcd_hx8394a_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_NT35596H_MIPI
{
	.lcd_id = 0x96,
	.panel = &lcd_nt35596h_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_NT35596H_RDC_MIPI
{
	.lcd_id = 0x86,
	.panel = &lcd_nt35596h_rdc_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_AMS549HQ01_MIPI
{
	.lcd_id = 0x4010,
	.panel = &lcd_ams549hq01_mipi_spec,
},
#endif

#ifdef CONFIG_FB_LCD_ILI9881C_MIPI 
{
	.lcd_id = 0x9881,
	.panel = &lcd_ili9881c_mipi_spec,
},
#endif

#if  defined (CONFIG_FB_LCD_JD9366_MIPI_H101IV027004_40B)||defined (CONFIG_FB_LCD_JD9364_MIPI_H101NL027004_40L)||defined (CONFIG_FB_LCD_JD9366_MIPI_H080IV021006_31)||defined (CONFIG_FB_LCD_JD9366_MIPI_HX00821_02A) \
    ||defined (CONFIG_FB_LCD_JD9364_MIPI_WJWX101062A)||defined (CONFIG_FB_LCD_JD9366_MIPI_WJWX070138A)||defined (CONFIG_FB_LCD_JD9365_PX101IH27810188A)
	{
		.lcd_id = 0x936600,
		.panel = &lcd_jd9366_mipi_spec,
	},
#endif
#if  defined (CONFIG_FB_LCD_JD9365_MIPI_WJHD69008A)||defined(CONFIG_FB_LCD_JD9365AB_BOE_MIPI_FRD_FY07020DI25E368_D)||defined (CONFIG_FB_LCD_JD9365_MIPI_WJHD069009A)||defined (CONFIG_FB_LCD_JD9365_MIPI_K695_BM2B602_A)||defined (CONFIG_FB_LCD_JD9365_MIPI_K695_BM2B602_A_2LANE)\
			||defined(CONFIG_FB_LCD_JD9365_MIPI_WJHD069009A_2_NSD) || defined(CONFIG_FB_LCD_JD9365_XPP101B215_00) ||defined (CONFIG_FB_LCD_CXP101B002_46_40)
	{
		.lcd_id = 0x936500,
		.panel = &lcd_jd9365_mipi_spec,
	},
#endif

#if defined (CONFIG_FB_LCD_9365_1129)
	{
		.lcd_id = 0x9365,
		.panel = &lcd_jd9365_1129_mipi_spec,
	},
#endif

#if  defined (CONFIG_FB_LCD_JD9367_MIPI_H080NL021016_31G)||defined (CONFIG_FB_LCD_JD9366_JYS080007CIP31L21A05_MIPI)||defined (CONFIG_FB_LCD_JD9366_YDS080WQ01_MIPI)|| defined (CONFIG_FB_LCD_JD9367_SL007PN20D1244_A00_MIPI)\
	||defined (CONFIG_FB_LCD_JD9367_MIPI_SL007PN18D1250_A00)||defined (CONFIG_FB_LCD_JD9367_MIPI_SL007PN18D1250_B00)||defined (CONFIG_FB_LCD_NT35523B_WJWX080030A_MIPI)||defined (CONFIG_FB_LCD_JD9367_MIPI_WJWX080032A)\
	||defined (CONFIG_FB_LCD_JD9366AB_MIPI_AM080NB03D)
	{
		.lcd_id = 0x936700,
		.panel = &lcd_jd9367_mipi_spec,
	},
#endif

#if  defined (CONFIG_FB_LCD_EK79029_MIPI_H101IV027004_40E)||defined(CONFIG_FB_LCD_EK79029_IPS_MIPI_COWIN101HI272640113C)||defined (CONFIG_FB_LCD_EK79029_MIPI_SM080_127A1)||defined (CONFIG_FB_LCD_EK79029_MIPI_NWE070CHH30_25A)||defined(CONFIG_FB_LCD_EK79029_MIPI_SM101_126A1)
	{
		.lcd_id = 0x7902,
		.panel = &lcd_ek79029_mipi_spec,
	},
#endif
#if defined (CONFIG_FB_LCD_S6D7AA0X02_MIPI_LTL070AL03_W01)||defined (CONFIG_FB_LCD_JD9362_BOE_MIPI_K070_IMIB704_B)
{
	.lcd_id = 0xaa02,
	.panel = &lcd_s6d7aa0x02_mipi_spec,
}
#endif
#if  defined (CONFIG_FB_LCD_NT35521S_MIPI_COWIN101CI272640178A)||defined (CONFIG_FB_LCD_NT35521S_MIPI_HX10127_02A)||defined (CONFIG_FB_LCD_NT35521S_MIPI_N070ICE_GB2)||defined (CONFIG_FB_LCD_NT35521S_MIPI_WG10115882881NB)||defined(NT35521_MIPI_SL101PC27Y0877_B00)
	{
		.lcd_id = 0x21,
		.panel = &lcd_nt35521s_mipi_spec,
	},
#endif

#if  defined (CONFIG_FB_LCD_BI31TA8001_21H)
	{
		.lcd_id = 0x6136,
		.panel = &lcd_bi32ta8001_21h_mipi_spec,
	},
#endif

#if  defined (CONFIG_FB_LCD_NT35521_MIPI_HXFPC101C04)||defined (CONFIG_FB_LCD_NT35521_MIPI_N080ICE_GB0)
	{
		.lcd_id = 0x21,
		.panel = &lcd_nt35521_mipi_spec,
	},
#endif

#if defined (CONFIG_FB_LCD_EK79007A_MIPI)||defined (CONFIG_FB_LCD_EK79007A_IPS_MIPI)||defined (CONFIG_FB_LCD_EK79007A_IPS_MIPI_EK73217ACGA)||defined (CONFIG_FB_LCD_EK79007A_TN_MIPI_TB101_F3024E120A_00)
{
	.lcd_id = 0x9007,
	.panel = &lcd_ek79007a_mipi_spec,
}
#endif

#ifdef CONFIG_FB_LCD_SI863_EK79007A_IPS_MIPI
{
	.lcd_id = 0x9007,
	.panel = &lcd_si863_ek79007a_mipi_spec,
},
#endif

#if defined (CONFIG_FB_LCD_S6D7AA0X01_MIPI_BP070WX1_300)||defined (CONFIG_FB_LCD_OTM1284A_CLAA070WQ62XG_MIPI)||defined (CONFIG_FB_LCD_OTM1284A_HC070CWF01_MIPI)\
	||defined (CONFIG_FB_LCD_JD9362_BOE_MIPI_FRD_BP070WX1_300)||defined(CONFIG_FB_LCD_ILI6136S_MIPI_CLAA080WQ65_XG)||defined (CONFIG_FB_LCD_EK79009A_YX070DIPS30A_10328E)||defined(CONFIG_FB_LCD_JD9365_WJWX080050A)||defined(CONFIG_FB_LCD_HX8271_CLAA070WP0B_MIPI)
{
	.lcd_id = 0xaa01,
	.panel = &lcd_s6d7aa0x01_mipi_spec,
}
#endif

#if defined (CONFIG_FB_LCD_ILI6180_IPS_MIPI)||defined (CONFIG_FB_LCD_H070NWS001_MIPI)||defined (CONFIG_FB_LCD_ILI6180_RHF069IN16V0_IPS_MIPI)
{
	.lcd_id = 0x6180,
	.panel = &lcd_ili6180_mipi_spec,
}
#endif

#if defined (CONFIG_FB_LCD_HX8279_MIPI_RS698CTHX)||defined (CONFIG_FB_LCD_MIPI_B070ATN020)||defined (CONFIG_FB_LCD_HX8279_MIPI_RS695CTHX)
{
	.lcd_id = 0x8279,
	.panel = &lcd_hx8279_mipi_spec,
}
#endif

#if defined (CONFIG_FB_LCD_HX8271_MIPI_HX070CH24B17)
{
	.lcd_id = 0x8271,
	.panel = &lcd_hx8271_mipi_spec,
}
#endif

#if defined (CONFIG_FB_LCD_HX8260_MIPI_M070WXBI30_01C)
{
	.lcd_id = 0x8260,
	.panel = &lcd_hx8260_mipi_spec,
}
#endif

#if defined (CONFIG_FB_LCD_S7190A_MIPI_LD070WX6)
{
	.lcd_id = 0x7190,
	.panel = &lcd_s7190a_mipi_spec,
}
#endif
};

vidinfo_t panel_info = {
#if defined(CONFIG_LCD_QVGA)
	.vl_col = 240,
	.vl_row = 320,
#elif defined(CONFIG_LCD_HVGA)
	.vl_col = 320,
	.vl_row = 480,
#elif defined(CONFIG_LCD_WVGA)
	.vl_col = 480,
	.vl_row = 800,
#elif defined(CONFIG_LCD_FWVGA)
	.vl_col = 480,
	.vl_row = 854,
#elif defined(CONFIG_LCD_QHD)
	.vl_col = 540,
	.vl_row = 960,
#elif defined(CONFIG_LCD_WSVGA)
	.vl_col = 1024,
	.vl_row = 600,
#elif defined(CONFIG_LCD_WKVGA)
	.vl_col = 600,
	.vl_row = 1024,	
#elif defined(CONFIG_LCD_WXVGA)
	.vl_col = 800,
	.vl_row = 1280,	
#elif defined(CONFIG_LCD_XGA)
	.vl_col = 768,
	.vl_row = 1024,
#elif defined(CONFIG_LCD_720P)
	.vl_col = 720,
	.vl_row = 1280,
#elif defined(CONFIG_LCD_1080P)
	.vl_col = 1080,
	.vl_row = 1920,
#endif
	.vl_bpix = 4,
	.cmap = colormap,
};

static int32_t panel_reset_dispc(struct panel_spec *self)
{
	#if defined(CONFIG_FB_LCD_EK79029_IPS_MIPI_COWIN101HI272640113C)
	dispc_write(1, DISPC_RSTN);
	mdelay(5);
	dispc_write(0, DISPC_RSTN);
	mdelay(5);
	dispc_write(1, DISPC_RSTN);
	mdelay(5);
	#else
	dispc_write(1, DISPC_RSTN);
	mdelay(20);
	dispc_write(0, DISPC_RSTN);
	mdelay(20);
	dispc_write(1, DISPC_RSTN);
	/* wait 10ms util the lcd is stable */
	mdelay(120);
	#endif
	return 0;
}

static void panel_reset(struct sprdfb_device *dev)
{
	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	//clk/data lane enter LP
	if(NULL != dev->if_ctrl->panel_if_before_panel_reset){
		dev->if_ctrl->panel_if_before_panel_reset(dev);
		mdelay(5);
	}

	//reset panel
	#if defined(ZEDIEL_LCD_PWRCTRL)
		dev->panel->ops->panel_reset(dev->panel);	//YangQing_20180119:新的reset功能放到了屏驱动里
	#else
		panel_reset_dispc(dev->panel);				//兼容旧的驱动
	#endif
}

static int panel_mount(struct sprdfb_device *dev, struct panel_spec *panel)
{
	uint16_t rval = 1;

	printf("sprdfb: [%s], type = %d\n",__FUNCTION__, panel->type);

	switch(panel->type){
#if defined(CONFIG_I2C) && defined(CONFIG_SPI)
	case SPRDFB_PANEL_TYPE_MCU:
		dev->if_ctrl = &sprdfb_mcu_ctrl;
		break;
#endif
	case SPRDFB_PANEL_TYPE_RGB:
	case SPRDFB_PANEL_TYPE_LVDS:
		dev->if_ctrl = &sprdfb_rgb_ctrl;
		break;
#if ((!defined(CONFIG_SC7710G2)) && (!defined(CONFIG_SPX15)))
	case SPRDFB_PANEL_TYPE_MIPI:
		dev->if_ctrl = &sprdfb_mipi_ctrl;
		break;
#endif
	default:
		printf("sprdfb: [%s]: erro panel type.(%d)",__FUNCTION__, panel->type);
		dev->if_ctrl = NULL;
		rval = 0 ;
		break;
	};

	if(NULL == dev->if_ctrl){
		return -1;
	}

	if(dev->if_ctrl->panel_if_check){
		rval = dev->if_ctrl->panel_if_check(panel);
	}

	if(0 == rval){
		printf("sprdfb: [%s] check panel fail!\n", __FUNCTION__);
		dev->if_ctrl = NULL;
		return -1;
	}

	dev->panel = panel;

	if(NULL == dev->panel->ops->panel_reset){
		dev->panel->ops->panel_reset = panel_reset_dispc;
	}

	dev->if_ctrl->panel_if_mount(dev);

	return 0;
}


int panel_init(struct sprdfb_device *dev)
{
	if((NULL == dev) || (NULL == dev->panel)){
		printf("sprdfb: [%s]: Invalid param\n", __FUNCTION__);
		return -1;
	}

	FB_PRINT("sprdfb: [%s], type = %d\n",__FUNCTION__, dev->panel->type);

	if(NULL != dev->if_ctrl->panel_if_init){
		dev->if_ctrl->panel_if_init(dev);
	}
	return 0;
}

int panel_ready(struct sprdfb_device *dev)
{
	if((NULL == dev) || (NULL == dev->panel)){
		printf("sprdfb: [%s]: Invalid param\n", __FUNCTION__);
		return -1;
	}

	FB_PRINT("sprdfb: [%s],  type = %d\n",__FUNCTION__, dev->panel->type);

	if(NULL != dev->if_ctrl->panel_if_ready){
		dev->if_ctrl->panel_if_ready(dev);
	}

	return 0;
}


static struct panel_spec *adapt_panel_from_readid(struct sprdfb_device *dev)
{
	int id, i, ret, b_panel_reset=0;

	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	for(i = 0;i<(sizeof(panel_cfg))/(sizeof(panel_cfg[0]));i++) {
		printf("sprdfb: [%s]: try panel 0x%x\n", __FUNCTION__, panel_cfg[i].lcd_id);
		ret = panel_mount(dev, panel_cfg[i].panel);
		if(ret < 0){
			printf("sprdfb: panel_mount failed!\n");
			continue;
		}
		dev->ctrl->update_clk(dev);
		panel_init(dev);
		if ((b_panel_reset==0) || (1 == dev->panel->is_need_reset))
		{
			panel_reset(dev);
			b_panel_reset=1;
		}
		id = dev->panel->ops->panel_readid(dev->panel);
		if(id == panel_cfg[i].lcd_id) {
			printf("sprdfb: [%s]: LCD Panel 0x%x is attached!\n", __FUNCTION__, panel_cfg[i].lcd_id);

			if(NULL != dev->panel->ops->panel_init){
				dev->panel->ops->panel_init(dev->panel);		//zxdebug modify for LCD adaptor 
			}
			
			save_lcd_id_to_kernel(id);
			panel_ready(dev);
			return panel_cfg[i].panel;
		} else {							//zxdbg for LCD adaptor
			printf("sprdfb: [%s]: LCD Panel 0x%x attached fail!go next\n", __FUNCTION__, panel_cfg[i].lcd_id);
			sprdfb_panel_remove(dev);				//zxdebug modify for LCD adaptor 
		}
	}
	
	printf("sprdfb:  [%s]: final failed to attach LCD Panel!\n", __FUNCTION__);
	return NULL;
}

uint16_t sprdfb_panel_probe(struct sprdfb_device *dev)
{
	struct panel_spec *panel;

	if(NULL == dev){
		printf("sprdfb: [%s]: Invalid param\n", __FUNCTION__);
		return -1;
	}

	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);
	LDO_TurnOnLDO(LDO_LDO_RF1);
	LDO_SetVoltLevel(LDO_LDO_RF1, LDO_VOLT_LEVEL1);
	#if defined(ZEDIEL_LCD_PWRCTRL)
	//reset panel remove to lcd drivers by YangQing_20180119
	#else
		#if defined(CONFIG_FB_LCD_EK79029_IPS_MIPI_COWIN101HI272640113C)
		sprd_gpio_request(NULL, 233);
		sprd_gpio_direction_output(NULL, 233, 0);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_request(NULL, 232);
		sprd_gpio_direction_output(NULL, 232, 0);
		sprd_gpio_set(NULL, 232, 1);
		sprd_gpio_request(NULL, 103);
		sprd_gpio_direction_output(NULL, 103, 0);
		sprd_gpio_set(NULL, 103, 1);
		#elif defined(CONFIG_FB_LCD_HX8279_MIPI_RS698CTHX)||defined (CONFIG_FB_LCD_HX8279_MIPI_RS695CTHX)
		sprd_gpio_request(NULL, 233);
		sprd_gpio_direction_output(NULL, 233, 0);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_request(NULL, 232);
		sprd_gpio_direction_output(NULL, 232, 0);
		sprd_gpio_set(NULL, 232, 1);
		mdelay(50);
		sprd_gpio_set(NULL, 233, 0);
		sprd_gpio_set(NULL, 232, 0);
		mdelay(50);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_set(NULL, 232, 1);
		mdelay(100);
		#elif defined(CONFIG_FB_LCD_EK79029_MIPI_SM080_127A1)
		sprd_gpio_request(NULL, 233);
		sprd_gpio_direction_output(NULL, 233, 0);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_request(NULL, 232);
		sprd_gpio_direction_output(NULL, 232, 0);
		sprd_gpio_set(NULL, 232, 1);
		mdelay(50);
		sprd_gpio_request(NULL, 103);
		sprd_gpio_direction_output(NULL, 103, 0);
		sprd_gpio_set(NULL, 103, 1);
		mdelay(50);
		#elif defined(OTM1284A_HC070CWF01_MIPI)
		sprd_gpio_request(NULL, 233);
		sprd_gpio_direction_output(NULL, 233, 0);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_request(NULL, 232);
		sprd_gpio_direction_output(NULL, 232, 0);
		sprd_gpio_set(NULL, 232, 1);
		mdelay(50);
		sprd_gpio_request(NULL, 103);
		sprd_gpio_direction_output(NULL, 103, 0);
		sprd_gpio_set(NULL, 103, 1);
		mdelay(20);
		sprd_gpio_set(NULL, 103, 0);
		mdelay(20);
		sprd_gpio_set(NULL, 103, 1);
		mdelay(50);
		#else
		sprd_gpio_request(NULL, 233);
		sprd_gpio_direction_output(NULL, 233, 0);
		sprd_gpio_set(NULL, 233, 1);
		sprd_gpio_request(NULL, 232);
		sprd_gpio_direction_output(NULL, 232, 0);
		sprd_gpio_set(NULL, 232, 1);
		mdelay(50);
		#endif
	#endif
	/* can not be here in normal; we should get correct device id from uboot */
	panel = adapt_panel_from_readid(dev);

	if (panel) {
#if defined(CONFIG_LCD_MULTI_RESOLUTION ) || defined (CONFIG_LCD_720P)
        /*To support different resolution in a u-boot.bin,change vl_row && vl_col for lcd_line_length
         * in common/lcd.c after reading panel id*/
#ifdef CONFIG_FB_LOW_RES_SIMU
        panel_info.vl_row = LCD_DISPLAY_HEIGHT;
        panel_info.vl_col = LCD_DISPLAY_WIDTH;
#else
        panel_info.vl_row = panel->height;
        panel_info.vl_col = panel->width;
#endif
#endif
		FB_PRINT("sprdfb: [%s] got panel\n", __FUNCTION__);
		return 0;
	}

	printf("sprdfb: [%s] can not got panel\n", __FUNCTION__);

	return -1;
}

void sprdfb_panel_invalidate_rect(struct panel_spec *self,
				uint16_t left, uint16_t top,
				uint16_t right, uint16_t bottom)
{
	FB_PRINT("sprdfb: [%s]\n, (%d, %d, %d,%d)",__FUNCTION__, left, top, right, bottom);

	if(NULL != self->ops->panel_invalidate_rect){
		self->ops->panel_invalidate_rect(self, left, top, right, bottom);
	}
}

void sprdfb_panel_invalidate(struct panel_spec *self)
{
	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	if(NULL != self->ops->panel_invalidate){
		self->ops->panel_invalidate(self);
	}
}

void sprdfb_panel_before_refresh(struct sprdfb_device *dev)
{
	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	if(NULL != dev->if_ctrl->panel_if_before_refresh)
		dev->if_ctrl->panel_if_before_refresh(dev);
}

void sprdfb_panel_after_refresh(struct sprdfb_device *dev)
{
	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	if(NULL != dev->if_ctrl->panel_if_after_refresh)
		dev->if_ctrl->panel_if_after_refresh(dev);
}

void sprdfb_panel_remove(struct sprdfb_device *dev)
{
	FB_PRINT("sprdfb: [%s]\n",__FUNCTION__);

	if((NULL != dev->if_ctrl) && (NULL != dev->if_ctrl->panel_if_uninit)){
		dev->if_ctrl->panel_if_uninit(dev);
	}
	dev->panel = NULL;
}

