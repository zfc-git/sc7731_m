#include <asm/types.h>
#include <common.h>
#include "asm/arch/clk_para_config.h"
#include "lpddr3_cfg.h"
#include "dmc_sprd_r1p0.h"
#include "dmc_sprd_misc.h"

#define MAX(x,y)			(((x) > (y)) ? (x) : (y))

static DMC_JEDEC_TIMING_CFG dmc_jedec_timing[4] = {
#if	(defined DDR_533M)
	/*533MHz*/
	{
		/*dtmg0*/
		0x6,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_533M,	/*tRCD*/
		TRP_533M,	/*tRP*/
		/*dtmg1*/
		0x2,		/*tRTR*/
		0xa,		/*tWTR*/
		0x9,		/*tRTW*/
		0x3,		/*tRTP*/
		/*dtmg2*/
		0x46,		/*tRFC*/
		0x0e,		/*tWR*/
		0x4b,		/*tXSR*/
		/*dtmg3*/
		0x4,		/*tCKE*/
		0x6,		/*tXP*/
		0x2,		/*tMRD*/
		0x1b,		/*tFAW*/
		0x17,		/*tRAS*/
	},
#elif	(defined DDR_466M)
	/*466MHz*/
	{
		/*dtmg0*/
		0x5,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_466M,	/*tRCD*/
		TRP_466M,	/*tRP*/
		/*dtmg1*/
		0x2,		/*tRTR*/
		0x9,		/*tWTR*/
		0x8,		/*tRTW*/
		0x2,		/*tRTP*/
		/*dtmg2*/
		0x3d,		/*tRFC*/
		0x0d,		/*tWR*/
		0x42,		/*tXSR*/
		/*dtmg3*/
		0x4,		/*tCKE*/
		0x6,		/*tXP*/
		0x2,		/*tMRD*/
		0x18,		/*tFAW*/
		0x14,		/*tRAS*/
	},
#else
	/*400MHz*/
	{
		/*dtmg0*/
		0x4,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_400M,	/*tRCD*/
		TRP_400M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x8,		/*tWTR*/
		0x8,		/*tRTW*/
		0x2,		/*tRTP*/
		/*dtmg2*/	
		0x34,		/*tRFC*/
		0x0b,		/*tWR*/
		0x38,		/*tXSR*/
		/*dtmg3*/	
		0x3,		/*tCKE*/
		0x5,		/*tXP*/
		0x2,		/*tMRD*/
		0x14,		/*tFAW*/
		0x11,		/*tRAS*/
	},
#endif
	/*384MHz*/
	{
		/*dtmg0*/
		0x4,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_384M,	/*tRCD*/
		TRP_384M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x8,		/*tWTR*/
		0x8,		/*tRTW*/
		0x2,		/*tRTP*/
		/*dtmg2*/	
		0x32,		/*tRFC*/
		0x0b,		/*tWR*/
		0x36,		/*tXSR*/
		/*dtmg3*/	
		0x3,		/*tCKE*/
		0x5,		/*tXP*/
		0x2,		/*tMRD*/
		0x14,		/*tFAW*/
		0x11,		/*tRAS*/
	},
	/*333MHz*/
	{
		/*dtmg0*/
		0x4,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_333M,	/*tRCD*/
		TRP_333M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x8,		/*tWTR*/
		0x7,		/*tRTW*/
		0x2,		/*tRTP*/
		/*dtmg2*/	
		0x2c,		/*tRFC*/
		0x0a,		/*tWR*/
		0x2f,		/*tXSR*/
		/*dtmg3*/	
		0x3,		/*tCKE*/
		0x5,		/*tXP*/
		0x2,		/*tMRD*/
		0x11,		/*tFAW*/
		0x0e,		/*tRAS*/
	},
	/*200MHz*/
	{
		/*dtmg0*/
		0x2,		/*tRRD*/
		0x4,		/*tCCD*/
		TRCD_200M,	/*tRCD*/
		TRP_200M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x7,		/*tWTR*/
		0x7,		/*tRTW*/
		0x1,		/*tRTP*/
		/*dtmg2*/	
		0x1a,		/*tRFC*/
		0x08,		/*tWR*/
		0x1c,		/*tXSR*/
		/*dtmg3*/	
		0x3,		/*tCKE*/
		0x4,		/*tXP*/
		0x2,		/*tMRD*/
		0xa,		/*tFAW*/
		0x9,		/*tRAS*/
	}
};

static DMC_LOCAL_TIMING_CFG dmc_local_timing[4] = {
#if	(defined DDR_533M)
	{
		0x070055ee,	/*dtmg4*/
		0x07800780,	/*dtmg5*/
		0x007f0000,	/*dtmg6*/
		0x007f0000,	/*dtmg7*/
		0x001c0000,	/*dtmg8*/
		0x00700000,	/*dtmg9*/
		0x00010006	/*dtmg10*/
	},
#elif	(defined DDR_466M)
	/*466MHz*/
	{
		0x070055ee,	/*dtmg4*/
		0x07800780,	/*dtmg5*/
		0x007f0000,	/*dtmg6*/
		0x007f0000,	/*dtmg7*/
		0x001c0000,	/*dtmg8*/
		0x00700000,	/*dtmg9*/
		0x00010006	/*dtmg10*/
	},
#else
	/*400MHz*/
	{
		0x01c044ec,	/*dtmg4*/
		0x01e001e0,	/*dtmg5*/
		0x0007f000,	/*dtmg6*/
		0x0007f000,	/*dtmg7*/
		0x0001c000,	/*dtmg8*/
		0x00070000,	/*dtmg9*/
		0x00010004	/*dtmg10*/
	},
#endif
	/*384MHz*/
	{
		0x01c044ec,	/*dtmg4*/
		0x01e001e0,	/*dtmg5*/
		0x0007f000,	/*dtmg6*/
		0x0007f000,	/*dtmg7*/
		0x0001c000,	/*dtmg8*/
		0x00070000,	/*dtmg9*/
		0x00010004	/*dtmg10*/
	},
	/*333MHz*/
	{
		0x01c044eb,	/*dtmg4*/
		0x01e001e0,	/*dtmg5*/
		0x0007f000,	/*dtmg6*/
		0x0007f000,	/*dtmg7*/
		0x0001c000,	/*dtmg8*/
		0x00070000,	/*dtmg9*/
		0x00010004	/*dtmg10*/
	},
	/*200MHz*/
	{
		0x001c22e8,	/*dtmg4*/
		0x001e001e,	/*dtmg5*/
		0x00001fc0,	/*dtmg6*/
		0x00001fc0,	/*dtmg7*/
		0x00000700,	/*dtmg8*/
		0x00001c00,	/*dtmg9*/
		0x00010001	/*dtmg10*/
	}
};

LPDDR3_MR_INFO lpddr3_mr_info = {
													/*burst length*/
#ifdef CFG_BL
								CFG_BL,
#else
								DEFAULT_LPDDR3_BL,					
#endif
	
								 0,					/*burst type*/
								 0,					/*wrap*/
								 3,					/*nwr*/
								 6,					/*read latency*/
								 3,					/*write latency*/
								 DDR_DRV_CFG		/*driver strength*/
	};

static DMC_DELAY_LINE_CFG dl_cfg = {
	/*dll ac*/
	CFG_DLL_CLKWR_AC,		/*dmc_clkwr_dll_ac*/
	/*dll ds*/
	CFG_DLL_CLKWR_DS0,		/*dmc_clkwr_dll_ds0*/
	CFG_DLL_CLKWR_DS1,		/*dmc_clkwr_dll_ds1*/
	CFG_DLL_CLKWR_DS2,		/*dmc_clkwr_dll_ds2*/
	CFG_DLL_CLKWR_DS3,		/*dmc_clkwr_dll_ds3*/
	/*dll dqsin pos*/
	CFG_DLL_DQSIN_POS_DS0,	/*dmc_dqsin_pos_dll_ds0*/
	CFG_DLL_DQSIN_POS_DS1,	/*dmc_dqsin_pos_dll_ds1*/
	CFG_DLL_DQSIN_POS_DS2,	/*dmc_dqsin_pos_dll_ds2*/
	CFG_DLL_DQSIN_POS_DS3,	/*dmc_dqsin_pos_dll_ds3*/
	/*dll dqsin neg*/	
	CFG_DLL_DQSIN_NEG_DS0,	/*dmc_dqsin_neg_dll_ds0*/
	CFG_DLL_DQSIN_NEG_DS1,	/*dmc_dqsin_neg_dll_ds1*/
	CFG_DLL_DQSIN_NEG_DS2,	/*dmc_dqsin_neg_dll_ds2*/
	CFG_DLL_DQSIN_NEG_DS3,	/*dmc_dqsin_neg_dll_ds3*/	
	/*dll dqsgate pre*/
	0x00000000,		/*dmc_dqsgate_pre_dll_ds0*/
	0x00000000,		/*dmc_dqsgate_pre_dll_ds1*/
	0x00000000,		/*dmc_dqsgate_pre_dll_ds2*/
	0x00000000,		/*dmc_dqsgate_pre_dll_ds3*/		
	/*dll dqsgate pst*/
	0x00000000,		/*dmc_dqsgate_pst_dll_ds0*/
	0x00000000,		/*dmc_dqsgate_pst_dll_ds1*/
	0x00000000,		/*dmc_dqsgate_pst_dll_ds2*/
	0x00000000,		/*dmc_dqsgate_pst_dll_ds3*/		
	/*dll date out strobe*/
	0x00000000,		/*dmc_date_out_dll_ds0*/
	0x00000000,		/*dmc_date_out_dll_ds1*/
	0x10000000,		/*dmc_date_out_dll_ds2*/
	0x00000000,		/*dmc_date_out_dll_ds3*/	
	/*dmc_dmdqs_inout_dll_ds*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds0*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds1*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds2*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds3*/
	/*dmc_data_in_dll_ds*/
	0x00000000,		/*dmc_data_in_dll_ds0*/
	0x00000000,		/*dmc_data_in_dll_ds1*/
	0x01000000,		/*dmc_data_in_dll_ds2*/
	0x00000000,		/*dmc_data_in_dll_ds3*/	

	/*dmc_cfg_dll_ac*/
	0x07028404,		/*dmc_cfg_dll_ac*/
	/*dmc_cfg_dll_ds*/
	0x07028404,		/*dmc_cfg_dll_ds0*/
	0x07028404,		/*dmc_cfg_dll_ds1*/
	0x07028404,		/*dmc_cfg_dll_ds2*/
	0x07028404,		/*dmc_cfg_dll_ds3*/
	/*dmc_addr_out_dll_ac;*/
	0x01122222,		/*dmc_addr_out0_dll_ac*/
	0x00000000,		/*dmc_addr_out1_dll_ac*/
};

/*if the DDR chip size is bigger than 4Gbit, fRFCab should be 210ns, else 130ns(default) is enough*/
void adjust_tfcab(DMC_JEDEC_TIMING_CFG *dmc_jedec_timing_ptr)
{
	int i,cs_size[2];

#if defined(CONFIG_CLK_PARA)
		u32 ddr_clk = (mcu_clk_para.ddr_freq)/1000000;
#else
		u32 ddr_clk = DDR_CLK ;
#endif

	for(i = 0;i < 2;i++)
	{
		sdram_cs_whole_size(i,&cs_size[i]);
	}
	if (MAX(cs_size[0], cs_size[1]) > 0x20000000)
	{
		if(ddr_clk == 533)
		{
			dmc_jedec_timing_ptr[0].tRFC = 0x70;	/*533MHz*/
			dmc_jedec_timing_ptr[0].tXSR = 0x76;
		}
		else if(ddr_clk == 466)
		{
			dmc_jedec_timing_ptr[0].tRFC = 0x62;	/*466MHz*/
			dmc_jedec_timing_ptr[0].tXSR = 0x67;
		}
		else if(ddr_clk == 400)
		{
			dmc_jedec_timing_ptr[0].tRFC = 0x54;	/*400MHz*/
			dmc_jedec_timing_ptr[0].tXSR = 0x58;
		}
		dmc_jedec_timing_ptr[1].tRFC = 0x51;	/*384MHz*/
		dmc_jedec_timing_ptr[1].tXSR = 0x55;
		dmc_jedec_timing_ptr[2].tRFC = 0x46;	/*333MHz*/
		dmc_jedec_timing_ptr[2].tXSR = 0x4a;
		dmc_jedec_timing_ptr[3].tRFC = 0x2a;	/*200MHz*/
		dmc_jedec_timing_ptr[3].tXSR = 0x2c;
	}
}


void lpddr_jedec_timing_init(DMC_JEDEC_TIMING_CFG *dmc_jedec_timing_ptr)
{
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
	unsigned int dtmg[4];
	int i,j;

	adjust_tfcab(dmc_jedec_timing_ptr);

	for(i=0; i<4; i++)
	{
		dtmg[0] = ((dmc_jedec_timing_ptr[i].tRRD&0xf)<<24) | ((dmc_jedec_timing_ptr[i].tCCD&0xf)<<16) | ((dmc_jedec_timing_ptr[i].tRCD&0xf)<<8) | (dmc_jedec_timing_ptr[i].tRP&0xf);
		dtmg[1] = ((dmc_jedec_timing_ptr[i].tRTR&0xf)<<24) | ((dmc_jedec_timing_ptr[i].tWTR&0xf)<<16) | ((dmc_jedec_timing_ptr[i].tRTW&0xf)<<8) | (dmc_jedec_timing_ptr[i].tRTP&0xf);
		dtmg[2] = ((dmc_jedec_timing_ptr[i].tRFC&0xff)<<24) | ((dmc_jedec_timing_ptr[i].tWR&0x3f)<<16) | (dmc_jedec_timing_ptr[i].tXSR&0x3ff);
		dtmg[3] = ((dmc_jedec_timing_ptr[i].tCKE&0xf)<<28) | ((dmc_jedec_timing_ptr[i].tXP&0xf)<<24) | ((dmc_jedec_timing_ptr[i].tMRD&0xf)<<16) | ((dmc_jedec_timing_ptr[i].tFAW&0xff)<<8) | (dmc_jedec_timing_ptr[i].tRAS&0x3f);
		for(j = 0;j < 4;j++)
		{
			pdmc->dmc_dtmg_f[i][j] = dtmg[j];
		}
	}	
}


void lpddr_local_timing_init(DMC_LOCAL_TIMING_CFG *dmc_local_timing_ptr)
{
	int i,j;
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
	/*timing array */
	for(i = 0;i < 4;i++)
	{
		for(j = 0;j < sizeof(dmc_local_timing_ptr[0])/sizeof(dmc_local_timing_ptr[0].dtmg[0]);j++)
		{
			pdmc->dmc_dtmg_f[i][j+4]=dmc_local_timing_ptr[i].dtmg[j];
		}
	}
}

static int lpddr3_dmc_delay_line_init(void)
{
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;

	pdmc->dmc_clkwr_dll_ac = dl_cfg.dmc_clkwr_dll_ac;

	pdmc->dmc_clkwr_dll_ds0 = dl_cfg.dmc_clkwr_dll_ds0;
	pdmc->dmc_dqsin_pos_dll_ds0 = dl_cfg.dmc_dqsin_pos_dll_ds0;
	pdmc->dmc_dqsin_neg_dll_ds0 = dl_cfg.dmc_dqsin_neg_dll_ds0;

	pdmc->dmc_clkwr_dll_ds1 = dl_cfg.dmc_clkwr_dll_ds1;
	pdmc->dmc_dqsin_pos_dll_ds1 = dl_cfg.dmc_dqsin_pos_dll_ds1;
	pdmc->dmc_dqsin_neg_dll_ds1 = dl_cfg.dmc_dqsin_neg_dll_ds1;

	pdmc->dmc_clkwr_dll_ds2 = dl_cfg.dmc_clkwr_dll_ds2;
	pdmc->dmc_dqsin_pos_dll_ds2 = dl_cfg.dmc_dqsin_pos_dll_ds2;
	pdmc->dmc_dqsin_neg_dll_ds2 = dl_cfg.dmc_dqsin_neg_dll_ds2;

	pdmc->dmc_clkwr_dll_ds3 = dl_cfg.dmc_clkwr_dll_ds3;
	pdmc->dmc_dqsin_pos_dll_ds3 = dl_cfg.dmc_dqsin_pos_dll_ds3;
	pdmc->dmc_dqsin_neg_dll_ds3 = dl_cfg.dmc_dqsin_neg_dll_ds3;
		

	pdmc->dmc_addr_out0_dll_ac = dl_cfg.dmc_addr_out0_dll_ac;
	pdmc->dmc_addr_out1_dll_ac = dl_cfg.dmc_addr_out1_dll_ac;
	return 0;
}



void lpddr3_timing_init(void)
{
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
	u32 regval;
#if defined(CONFIG_CLK_PARA)
		u32 ddr_clk = (mcu_clk_para.ddr_freq)/1000000;
#else
		u32 ddr_clk = DDR_CLK ;
#endif
	
	lpddr_jedec_timing_init(dmc_jedec_timing);
	lpddr_local_timing_init(dmc_local_timing);
	lpddr3_dmc_delay_line_init();

	regval = pdmc->dmc_lpcfg3;
	if(533 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 0);
	}
	if(466 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 0);
	}
	if(400 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 0);
	}
	else if (384 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 1);
	}
	
	else if (333 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 2);
	}
	else if (200 == ddr_clk)
	{
		regval = u32_bits_set(regval, 4, 2, 3);
	}
	pdmc->dmc_lpcfg3 = regval;
}

int mr_set_zq(void)
{
	dmc_mrw(CMD_CS_0, 10, 0xff);
	dmc_sprd_delay(10);
	dmc_mrw(CMD_CS_1, 10, 0xff);
	return 0;
}

int lpddr3_final_init(void)
{
	unsigned char val = 0;
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
#if defined(CONFIG_CLK_PARA)
		u32 ddr_clk = (mcu_clk_para.ddr_freq)/1000000;
#else
		u32 ddr_clk = DDR_CLK ;
#endif

#if 1
	if ((533 == ddr_clk) || 466 == ddr_clk)
	{
		lpddr3_mr_info.rl = 8;
		lpddr3_mr_info.wl = 4;
	}
	else if ((400 == ddr_clk) || (384 == ddr_clk) || (333 == ddr_clk) )
	{
		lpddr3_mr_info.rl = 6;
		lpddr3_mr_info.wl = 3;		
	}	
	else if (200 == ddr_clk)
	{
		lpddr3_mr_info.rl = 3;
		lpddr3_mr_info.wl = 1;
	}
	else
	{
		return -1;
	}
#endif
		

	/*cke up*/
	pdmc->dmc_dcfg0 |= 1<<14;

	/*tINIT3 > 200us 100:99us*/
	dmc_sprd_delay(300);
	
	/*reset ddr*/
	dmc_mrw(CMD_CS_BOTH, 0x3f, 0);
	/*burst length:4*/
	switch(lpddr3_mr_info.bl)
	{
		case 4:
			val = 2;
			break;
		case 8:
			val = 3;
			break;
		case 16:
			val = 4;
			break;
		default:
			return -1;
	}
	/*burst type:sequential*/
	if (lpddr3_mr_info.bt == 1)
	{
		val |= 1<<3;
	}
	/*WC:wrap*/	
	if (lpddr3_mr_info.wc == 1)
	{
		val |= 1<<4;
	}
	/*nwr:3*/
	if (lpddr3_mr_info.nwr<3 || lpddr3_mr_info.nwr>7)
	{
		return -1;
	}
	val |= (lpddr3_mr_info.nwr-2)<<5;
	dmc_sprd_delay(10);
	dmc_mrw(CMD_CS_BOTH, 0x1, val);
	
	/*read latency:6*/
	/*write latency:3*/
	dmc_sprd_delay(10);
	if (0 != mr_set_rlwl(lpddr3_mr_info.rl, lpddr3_mr_info.wl))
	{
		return -1;
	}
	/*driver strength*/
	dmc_sprd_delay(10);
	if (0 != mr_set_drv(lpddr3_mr_info.ds))
	{
		return -1;
	}
	dmc_sprd_delay(10);

	/*zq calication*/
	dmc_sprd_delay(10);
	mr_set_zq();

	/*adjust drf_t_trefi = 0x18*/
	pdmc->dmc_dcfg3 &= ~0xff;
	pdmc->dmc_dcfg3 |= 0x18;
	
	/*hardware auto refresh enable*/
	pdmc->dmc_dcfg3 |= 1<<12;
	return 0;
}

int lpddr3_ac_train_reset(void)
{
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
	unsigned char val = 0;

	pdmc->dmc_dcfg3 &= ~(1<<12);
	
	/*reset ddr*/
	dmc_mrw(CMD_CS_BOTH, 0x3f, 0);
	/*burst length:4*/
	switch(lpddr3_mr_info.bl)
	{
		case 4:
			val = 2;
			break;
		case 8:
			val = 3;
			break;
		case 16:
			val = 4;
			break;
		default:
			return -1;
	}
	/*burst type:sequential*/
	if (lpddr3_mr_info.bt == 1)
	{
		val |= 1<<3;
	}
	/*WC:wrap*/	
	if (lpddr3_mr_info.wc == 1)
	{
		val |= 1<<4;
	}
	/*nwr:3*/
	if (lpddr3_mr_info.nwr<3 || lpddr3_mr_info.nwr>7)
	{
		return -1;
	}
	val |= (lpddr3_mr_info.nwr-2)<<5;
	dmc_sprd_delay(10);
	dmc_mrw(CMD_CS_BOTH, 0x1, val);
	
	/*read latency:6*/
	/*write latency:3*/
	dmc_sprd_delay(10);
	if (0 != mr_set_rlwl(lpddr3_mr_info.rl, lpddr3_mr_info.wl))
	{
		return -1;
	}
	/*driver strength*/
	dmc_sprd_delay(10);
	if (0 != mr_set_drv(lpddr3_mr_info.ds))
	{
		return -1;
	}
	dmc_sprd_delay(10);

	/*zq calication*/
	dmc_sprd_delay(10);
	mr_set_zq();

	/*adjust drf_t_trefi = 0x18*/
	pdmc->dmc_dcfg3 &= ~0xff;
	pdmc->dmc_dcfg3 |= 0x18;

	/*hardware auto refresh enable*/
	pdmc->dmc_dcfg3 |= 1<<12;
	return 0;
}


#ifdef DDR_AUTO_DETECT
static LPDDR_JEDEC_ORIGINIZE org_standard[] = {
	/*4Gb*/
	{0x20000000,	16,	8,	14,	11},
	{0x20000000,	32,	8,	14,	10},
	/*6Gb*/
	{0x30000000,	16,	8,	15,	11},
	{0x30000000,	32,	8,	15,	10},
	/*8Gb*/
	{0x40000000,	16,	8,	15,	11},
	{0x40000000,	32,	8,	15,	10},
	/*12Gb*/
	{0x60000000,	16,	8,	15,	12},
	{0x60000000,	32,	8,	15,	11},
	/*16Gb*/
	{0x80000000,	16,	8,	15,	12},
	{0x80000000,	32,	8,	15,	11},
};

int lpddr_update_row_column(DRAM_JEDEC_INFO *info,LPDDR_JEDEC_ORIGINIZE *org_standard_ptr,int org_standard_num)
{
	int i;
	for (i=0; i<org_standard_num; i++)
	{
		if ((info->cs_size == org_standard_ptr[i].cs_size) &&
			(info->dw == org_standard_ptr[i].dw) &&
			(info->bank == org_standard_ptr[i].bank))
		{
			info->row = org_standard_ptr[i].row;
			info->column= org_standard_ptr[i].column;
			return 0;
		}
	}
	return -1;
}

int lpddr3_update_jedec_info(u8 val, DRAM_JEDEC_INFO *info)
{
	u8 tmp;
	int org_standard_num = sizeof(org_standard)/sizeof(LPDDR_JEDEC_ORIGINIZE);

	/*dw*/
	tmp = val>>6;
	if (0 == tmp)
	{
		info->dw = 32;
	}
	else if (1 == tmp)
	{
		info->dw = 16;
	}	
	else
	{
		return -1;
	}
	/*cs size*/
	tmp = (val>>2)&0xf;
	switch(tmp)
	{		
		case 6:
			info->cs_size = 0x20000000;
			info->bank = 8;
			break;
		case 7:
			info->cs_size = 0x40000000;
			info->bank = 8;
			break;
		case 8:
			info->cs_size = 0x80000000;
			info->bank = 8;
			break;		
		case 0xd:
			info->cs_size = 0x60000000;
			info->bank = 8;
			break;
		case 0xe:
			info->cs_size = 0x30000000;
			info->bank = 8;
			break;
		default:
			return -2;
	}

	if (0 != lpddr_update_row_column(info,org_standard,org_standard_num))
	{
		return -3;
	}
	return 0;	
}

#endif

