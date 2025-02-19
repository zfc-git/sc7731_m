#include <asm/types.h>
#include <common.h>
#include "asm/arch/clk_para_config.h"
#include "lpddr2_cfg.h"
#include "dmc_sprd_r1p0.h"
#include "dmc_sprd_misc.h"

extern void lpddr_jedec_timing_init(DMC_JEDEC_TIMING_CFG *dmc_jedec_timing_ptr);
extern void lpddr_local_timing_init(DMC_LOCAL_TIMING_CFG *dmc_local_timing_ptr);
extern int lpddr_update_row_column(DRAM_JEDEC_INFO *info,LPDDR_JEDEC_ORIGINIZE *org_standard_ptr,int org_standard_num);
extern int mr_set_zq(void);

static DMC_JEDEC_TIMING_CFG dmc_jedec_timing[4] = {
	/*400MHz*/
	{
		/*dtmg0*/
		0x4,		/*tRRD*/
		0x2,		/*tCCD*/
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
	/*384MHz*/
	{
		/*dtmg0*/
		0x4,		/*tRRD*/
		0x2,		/*tCCD*/
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
		0x2,		/*tCCD*/
		TRCD_333M,	/*tRCD*/
		TRP_333M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x7,		/*tWTR*/
		0x7,		/*tRTW*/
		0x2,		/*tRTP*/
		/*dtmg2*/	
		0x2c,		/*tRFC*/
		0x09,		/*tWR*/
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
		0x2,		/*tCCD*/
		TRCD_200M,	/*tRCD*/
		TRP_200M,	/*tRP*/
		/*dtmg1*/	
		0x2,		/*tRTR*/
		0x5,		/*tWTR*/
		0x6,		/*tRTW*/
		0x1,		/*tRTP*/
		/*dtmg2*/	
		0x1a,		/*tRFC*/
		0x06,		/*tWR*/
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
	/*400MHz*/
	{
		0x010044ec,	/*dtmg4*/
		0x01800180,	/*dtmg5*/
		0x0007f000,	/*dtmg6*/
		0x0007f000,	/*dtmg7*/
		0x0001c000,	/*dtmg8*/
		0x00070000,	/*dtmg9*/
		0x00010004	/*dtmg10*/
	},
	/*384MHz*/
	{
		0x010044ec,	/*dtmg4*/
		0x01800180,	/*dtmg5*/
		0x0007f000,	/*dtmg6*/
		0x0007f000,	/*dtmg7*/
		0x0001c000,	/*dtmg8*/
		0x00070000,	/*dtmg9*/
		0x00010004	/*dtmg10*/
	},
	/*333MHz*/
	{
		0x004033ea,	/*dtmg4*/
		0x00600060,	/*dtmg5*/
		0x0001fc00,	/*dtmg6*/
		0x0001fc00,	/*dtmg7*/
		0x00007000,	/*dtmg8*/
		0x0001c000,	/*dtmg9*/
		0x00010003	/*dtmg10*/
	},
	/*200MHz*/
	{
		0x001022e8,	/*dtmg4*/
		0x00180018,	/*dtmg5*/
		0x00001fc0,	/*dtmg6*/
		0x00001fc0,	/*dtmg7*/
		0x00000700,	/*dtmg8*/
		0x00001c00,	/*dtmg9*/
		0x00010001	/*dtmg10*/
	}
};

LPDDR2_MR_INFO lpddr2_mr_info = {
													/*burst length*/
#ifdef CFG_BL
								CFG_BL,
#else
								DEFAULT_LPDDR2_BL,					
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
	0x00000000,		/*dmc_date_out_dll_ds2*/
	0x00000100,		/*dmc_date_out_dll_ds3*/	
	/*dmc_dmdqs_inout_dll_ds*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds0*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds1*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds2*/
	0x00000000,		/*dmc_dmdqs_inout_dll_ds3*/
	/*dmc_data_in_dll_ds*/
	0x00000000,		/*dmc_data_in_dll_ds0*/
	0x00000000,		/*dmc_data_in_dll_ds1*/
	0x01100000,		/*dmc_data_in_dll_ds2*/
	0x11101111,		/*dmc_data_in_dll_ds3*/	

	/*dmc_cfg_dll_ac*/
	0x07028404,		/*dmc_cfg_dll_ac*/
	/*dmc_cfg_dll_ds*/
	0x07028404,		/*dmc_cfg_dll_ds0*/
	0x07028404,		/*dmc_cfg_dll_ds1*/
	0x07028404,		/*dmc_cfg_dll_ds2*/
	0x07028404,		/*dmc_cfg_dll_ds3*/
	/*dmc_addr_out_dll_ac;*/
	0x00111111,		/*dmc_addr_out0_dll_ac*/
	0x00000000,		/*dmc_addr_out1_dll_ac*/
};

static int lpddr2_dmc_delay_line_init(void)
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


int lpddr2_timing_init(void)
{
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;
	unsigned int regval;

	
#if defined(CONFIG_CLK_PARA)
		u32 ddr_clk = (mcu_clk_para.ddr_freq)/1000000;
#else
		u32 ddr_clk = DDR_CLK ;
#endif
	
	lpddr_jedec_timing_init(dmc_jedec_timing);
	lpddr_local_timing_init(dmc_local_timing);
	lpddr2_dmc_delay_line_init();
	regval = pdmc->dmc_lpcfg3;
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
	return 0;
}

int lpddr2_final_init(void)
{
	unsigned char val = 0;
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;

#if defined(CONFIG_CLK_PARA)
		u32 ddr_clk = (mcu_clk_para.ddr_freq)/1000000;
#else
		u32 ddr_clk = DDR_CLK ;
#endif

	if ((400 == ddr_clk) || (384 == ddr_clk))
	{
		lpddr2_mr_info.rl = 6;
		lpddr2_mr_info.wl = 3;		
	}
	else if (333 == ddr_clk)
	{
		lpddr2_mr_info.rl = 5;
		lpddr2_mr_info.wl = 2;	
	}
	else if (200 == ddr_clk)
	{
		lpddr2_mr_info.rl = 3;
		lpddr2_mr_info.wl = 1;
	}
	else
	{
		return -1;
	}

	/*cke up*/
	pdmc->dmc_dcfg0 |= 1<<14;

	/*tINIT3 > 200us*/
	dmc_sprd_delay(100);
	
	/*reset ddr*/
	dmc_mrw(CMD_CS_BOTH, 0x3f, 0);
	/*burst length:4*/
	switch(lpddr2_mr_info.bl)
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
	if (lpddr2_mr_info.bt == 1)
	{
		val |= 1<<3;
	}
	/*WC:wrap*/	
	if (lpddr2_mr_info.wc == 1)
	{
		val |= 1<<4;
	}
	/*nwr:3*/
	if (lpddr2_mr_info.nwr<3 || lpddr2_mr_info.nwr>7)
	{
		return -1;
	}
	val |= (lpddr2_mr_info.nwr-2)<<5;
	dmc_sprd_delay(10);
	dmc_mrw(CMD_CS_BOTH, 0x1, val);

	/*driver strength*/
	dmc_sprd_delay(10);
	if (0 != mr_set_drv(lpddr2_mr_info.ds))
	{
		return -1;
	}

	/*read latency:6*/
	/*write latency:3*/
	dmc_sprd_delay(10);
	if (0 != mr_set_rlwl(lpddr2_mr_info.rl, lpddr2_mr_info.wl))
	{
		return -1;
	}

	/*zq calication*/
	dmc_sprd_delay(10);
	mr_set_zq();	
	
	dmc_sprd_delay(10);

	/*adjust drf_t_trefi = 0x18*/
	pdmc->dmc_dcfg3 &= ~0xff;
	pdmc->dmc_dcfg3 |= 0x18;

	/*hardware auto refresh enable*/
	pdmc->dmc_dcfg3 |= 1<<12;
	return 0;
}

int lpddr2_ac_train_reset(void)
{
	unsigned char val = 0;
	PIKE_DMC_REG_INFO_PTR pdmc = (PIKE_DMC_REG_INFO_PTR)DMC_REG_ADDR_BASE_PHY;

	/*hardware auto refresh enable*/
	pdmc->dmc_dcfg3 &= ~(1<<12);

	/*reset ddr*/
	dmc_mrw(CMD_CS_BOTH, 0x3f, 0);
	/*burst length:4*/
	switch(lpddr2_mr_info.bl)
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
	if (lpddr2_mr_info.bt == 1)
	{
		val |= 1<<3;
	}
	/*WC:wrap*/ 
	if (lpddr2_mr_info.wc == 1)
	{
		val |= 1<<4;
	}
	/*nwr:3*/
	if (lpddr2_mr_info.nwr<3 || lpddr2_mr_info.nwr>7)
	{
		return -1;
	}
	val |= (lpddr2_mr_info.nwr-2)<<5;
	dmc_sprd_delay(10);
	dmc_mrw(CMD_CS_BOTH, 0x1, val);

	/*driver strength*/
	dmc_sprd_delay(10);
	if (0 != mr_set_drv(lpddr2_mr_info.ds))
	{
		return -1;
	}

	/*read latency:6*/
	/*write latency:3*/
	dmc_sprd_delay(10);
	if (0 != mr_set_rlwl(lpddr2_mr_info.rl, lpddr2_mr_info.wl))
	{
		return -1;
	}

	/*zq calication*/
	dmc_sprd_delay(10);
	mr_set_zq();	

	dmc_sprd_delay(10);

	/*adjust drf_t_trefi = 0x18*/
	pdmc->dmc_dcfg3 &= ~0xff;
	pdmc->dmc_dcfg3 |= 0x18;
	/*hardware auto refresh enable*/
	pdmc->dmc_dcfg3 |= 1<<12;
	return 0;
}


#ifdef DDR_AUTO_DETECT
static LPDDR_JEDEC_ORIGINIZE org_standard[] = {
	/*64Mb*/
	{0x800000,	8,	4,	12,	9},
	{0x800000,	16,	4,	12,	8},
	{0x800000,	32,	4,	12,	7},
	/*128Mb*/
	{0x1000000,	8,	4,	12,	10},
	{0x1000000,	16,	4,	12,	9},
	{0x1000000,	32,	4,	12,	8},
	/*256Mb*/
	{0x2000000,	8,	4,	13,	10},
	{0x2000000,	16,	4,	13,	9},
	{0x2000000,	32,	4,	13,	8},
	/*512Mb*/
	{0x4000000,	8,	4,	13,	11},
	{0x4000000,	16,	4,	13,	10},
	{0x4000000,	32,	4,	13,	9},
	/*1Gb*/
	{0x8000000,	8,	4,	14,	11},
	{0x8000000,	16,	4,	14,	10},
	{0x8000000,	32,	4,	14,	9},
	{0x8000000,	8,	8,	13,	11},
	{0x8000000,	16,	8,	13,	10},
	{0x8000000,	32,	8,	13,	9},
	/*2Gb*/
	{0x10000000,	8,	4,	15,	11},
	{0x10000000,	16,	4,	15,	10},
	{0x10000000,	32,	4,	15,	9},
	{0x10000000,	8,	8,	14,	11},
	{0x10000000,	16,	8,	14,	10},
	{0x10000000,	32,	8,	14,	9},
	/*4Gb*/
	{0x20000000,	8,	8,	14,	12},
	{0x20000000,	16,	8,	14,	11},
	{0x20000000,	32,	8,	14,	10},
	/*6Gb*/
	{0x30000000,	8,	8,	15,	12},
	{0x30000000,	16,	8,	15,	11},
	{0x30000000,	32,	8,	15,	10},
	/*8Gb*/
	{0x40000000,	8,	8,	15,	12},
	{0x40000000,	16,	8,	15,	11},
	{0x40000000,	32,	8,	15,	10},	
};

int lpddr2_update_jedec_info(u8 val, DRAM_JEDEC_INFO *info)
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
	else if (2 == tmp)
	{
		info->dw = 8;
	}
	else
	{
		return -1;
	}
	/*cs size*/
	tmp = (val>>2)&0xf;
	switch(tmp)
	{
		case 0:
			info->cs_size = 0x800000;
			info->bank = 4;
			break;
		case 1:
			info->cs_size = 0x1000000;
			info->bank = 4;
			break;
		case 2:
			info->cs_size = 0x2000000;
			info->bank = 4;
			break;
		case 3:
			info->cs_size = 0x4000000;
			info->bank = 4;
			break;
		case 4:
			info->cs_size = 0x8000000;
			if ((val&0x3) == 0)
			{
				info->bank = 8;
			}
			else if ((val&0x3) == 1)
			{
				info->bank = 4;
			}
			else
			{
				return -2;
			}
			break;
		case 5:
			info->cs_size = 0x10000000;
			if ((val&0x3) == 0)
			{
				info->bank = 8;
			}
			else if ((val&0x3) == 1)
			{
				info->bank = 4;
			}
			else
			{
				return -3;
			}
			break;
		case 6:
			info->cs_size = 0x20000000;
			info->bank = 8;
			break;
		case 7:
			info->cs_size = 0x40000000;
			info->bank = 8;
			break;
		case 0xe:
			info->cs_size = 0x30000000;
			info->bank = 8;
			break;
		default:
			return -4;
	}

	if (0 != lpddr_update_row_column(info,org_standard,org_standard_num))
	{
		return -5;
	}
	return 0;	
}

#endif

