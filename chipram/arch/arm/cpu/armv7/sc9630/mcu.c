
/******************************************************************************
    David.Jia   2007.10.29      share_version_union

******************************************************************************/

#include <common.h>
#include <asm/arch/sci_types.h>
#include <asm/arch/chip_drv_common_io.h>
#include <asm/arch/adi_hal_internal.h>
#include <asm/arch/sprd_reg.h>
#include <asm/arch/chip_drvapi.h>
#include <asm/arch/sprd_chipram_env.h>
#include <asm/io.h>

#define MPLL_REFIN 26
#define DPLL_REFIN 26
#define NINT(FREQ,REFIN)	(FREQ/REFIN)
#define KINT(FREQ,REFIN)	((FREQ-(FREQ/REFIN)*REFIN)*1048576/REFIN)

/*sharklc chip_id*/
#define SCX9832A_ALPHA_TAPOUT            (0x96301000)
#define SCX9832A_ALPHA_TAPOUT_MASK       (0xFFFFF000)

#if defined(CONFIG_CLK_PARA)
#include <asm/arch/clk_para_config.h>
const MCU_CLK_PARA_T mcu_clk_para=
{
    .magic_header = MAGIC_HEADER,
    .version = CONFIG_PARA_VERSION,
    .core_freq = CLK_CA7_CORE,
    .ddr_freq = DDR_FREQ,
    .axi_freq = CLK_CA7_AXI,
    .dgb_freq = CLK_CA7_DGB,
    .ahb_freq = CLK_CA7_AHB,
    .apb_freq = CLK_CA7_APB,
    .pub_ahb_freq = CLK_PUB_AHB,
    .aon_apb_freq = CLK_AON_APB,
    .dcdc_arm = DCDC_ARM,
    .dcdc_core = DCDC_CORE,
#ifdef DCDC_MEM
	.dcdc_mem = DCDC_MEM,
#endif
#ifdef DCDC_GEN
	.dcdc_gen = DCDC_GEN,
#endif
#if defined(CONFIG_ADIE_SC2723) || defined(CONFIG_ADIE_SC2723S)
	.debug_flags[0] = 0x3FFFFFF1, //0x3FFFFFFF
#elif defined(CONFIG_ADIE_SC2713S)
	.debug_flags[0] = 0xFFFF0FE2, //0xFFFF0FFE
#else /* defined(CONFIG_ADIE_SC2711) || defined(CONFIG_SPX15) */
	.debug_flags[0] = 0x1FFF07F1, //0x1FFF07FF
#endif
    .magic_end = MAGIC_END
};
#endif

struct ibias_table {
	unsigned long rate;
	u8 ibias;
};

#define PLL_MAX_RATE		(0xFFFFFFFF)
struct ibias_table sharklc_adjustable_pll_table[] = {//mpll/lpll/twpll/rpll
	{
		.rate = 988000000,
		.ibias = 0x0,
	},
	{
		.rate = 1248000000,
		.ibias = 0x1,
	},
	{
		.rate = 1612000000,
		.ibias = 0x2,
	},
	{
		.rate = PLL_MAX_RATE,
		.ibias = 0x2,
	},
};

static void delay()
{
    volatile uint32 i;
    for (i=0; i<0x100; i++);
}

static uint32 Get_chip_id()
{
#ifdef REG_AON_APB_AON_CHIP_ID
	return __raw_readl((void *)REG_AON_APB_AON_CHIP_ID);
#else
	return 0;
#endif
}

#ifdef CONFIG_SCX35LT8

#define REG_AON_CLK_CCI_CFG                              SCI_ADDR(SPRD_AONCKG_PHYS, 0x0074)
static void SetMiscConfig(void)
{
    REG32(REG_AON_CLK_CCI_CFG) &= ~0x3;
    REG32(REG_AON_CLK_CCI_CFG) |= 0x2;
}

static uint32 SetMPllClk_T8 (uint32 clk, uint32 litflag)
{
    uint32 mpll_cfg;
    uint32 mpll_reg_cfg1, mpll_reg_cfg2, bit_done;

#define REG_AON_APB_MPLL0_CFG1                          SCI_ADDR(REGS_AON_APB_BASE, 0x00140)
#define REG_AON_APB_MPLL0_CFG2                          SCI_ADDR(REGS_AON_APB_BASE, 0x00144)
#define MPLL0_SOFT_CNT_DONE (BIT(0))

#define MPLL_SOFT_CNT_DONE (BIT(5))

    if(litflag){
        mpll_reg_cfg1 = REG_AON_APB_MPLL0_CFG1;
        mpll_reg_cfg2 = REG_AON_APB_MPLL0_CFG2;
        bit_done = MPLL0_SOFT_CNT_DONE;
    }else{
        mpll_reg_cfg1 = REG_AON_APB_MPLL_CFG1;
        mpll_reg_cfg2 = REG_AON_APB_MPLL_CFG2;
        bit_done = MPLL_SOFT_CNT_DONE;
    }

    REG32(REG_AON_APB_PLL_SOFT_CNT_DONE) &= ~bit_done;

    clk=clk/1000000;

    mpll_cfg  = REG32(mpll_reg_cfg1);

    mpll_cfg |= (1<<26); 	//fractional divider
    mpll_cfg |= (1<<24);//mpll sdm en
    mpll_cfg |= (3<<18);	// MPLL_REFIN 0-2M 1-4M 2-13M 3-26M
    REG32(mpll_reg_cfg1) = mpll_cfg;

    mpll_cfg = (KINT(clk,MPLL_REFIN)&0xfffff);
    mpll_cfg |= (NINT(clk,MPLL_REFIN)&0x3f)<<24;
    REG32(mpll_reg_cfg2) = mpll_cfg;

    delay();
    delay();
    delay();
    delay();
    delay();

    REG32(REG_AON_APB_PLL_SOFT_CNT_DONE) |=  bit_done;

    return 0;
}
#endif

static uint32 SetMPllClk (uint32 clk)
{
    uint32 mpll_cfg, pll_sft_cnt;
	struct ibias_table *itable;

    REG32(REG_AON_APB_PLL_SOFT_CNT_DONE) &= ~1;

    clk=clk/1000000;

    mpll_cfg  = REG32(REG_AON_APB_MPLL_CFG1);
    mpll_cfg |= (1<<26); 	//fractional divider
    mpll_cfg |= (1<<24);//mpll sdm en
    mpll_cfg |= (3<<18);	// MPLL_REFIN 0-2M 1-4M 2-13M 3-26M

    if((Get_chip_id() & SCX9832A_ALPHA_TAPOUT_MASK) ==//is sharklc
    (SCX9832A_ALPHA_TAPOUT & SCX9832A_ALPHA_TAPOUT_MASK)){

		itable = sharklc_adjustable_pll_table;
		for (; itable->rate <= PLL_MAX_RATE; itable++) {//set ibias
			if (clk*1000000 < itable->rate) {
				mpll_cfg |= (itable->ibias << 16);
				break;
			}
		}
	}
    REG32(REG_AON_APB_MPLL_CFG1) = mpll_cfg;

    mpll_cfg = (KINT(clk,MPLL_REFIN)&0xfffff);
    mpll_cfg |= (NINT(clk,MPLL_REFIN)&0x3f)<<24;
    REG32(REG_AON_APB_MPLL_CFG2) = mpll_cfg;

    delay();
    delay();
    delay();
    delay();
    delay();

    REG32(REG_AON_APB_PLL_SOFT_CNT_DONE) |=  1;

    return 0;
}

static uint32 AhbClkConfig()
{
#if defined(CONFIG_CLK_PARA)
    uint32 ahb_cfg;
    ahb_cfg  = REG32(REG_AP_CLK_AP_AHB_CFG);
    ahb_cfg &=~3;
    ahb_cfg |= mcu_clk_para.ahb_freq;  //ahb select 192M           0:26M 1:76M 2:128M 3:192M
    REG32(REG_AP_CLK_AP_AHB_CFG) = ahb_cfg;

    ahb_cfg  = REG32(REG_AON_CLK_PUB_AHB_CFG);
    ahb_cfg &=~3;
    ahb_cfg |= mcu_clk_para.pub_ahb_freq;  //pub ahb select 128M      0:26M 1:4M 2:96M 3:128M
    REG32(REG_AON_CLK_PUB_AHB_CFG) = ahb_cfg;
#else
    uint32 ahb_cfg;
    ahb_cfg  = REG32(REG_AP_CLK_AP_AHB_CFG);
    ahb_cfg &=~3;
    ahb_cfg |= 3;  //ahb select 192M           0:26M 1:76M 2:128M 3:192M
    REG32(REG_AP_CLK_AP_AHB_CFG) = ahb_cfg;

    ahb_cfg  = REG32(REG_AON_CLK_PUB_AHB_CFG);
    ahb_cfg &=~3;
    ahb_cfg |= 3;  //pub ahb select 128M      0:26M 1:4M 2:96M 3:128M
    REG32(REG_AON_CLK_PUB_AHB_CFG) = ahb_cfg;
#endif
    delay();
    return 0;
}

static uint32 ApbClkConfig()
{
#if defined(CONFIG_CLK_PARA)
    uint32 apb_cfg;
    apb_cfg  = REG32(REG_AP_CLK_AP_APB_CFG);
    apb_cfg &=~3;
    apb_cfg |= mcu_clk_para.apb_freq;  //apb select 64M            0:26M 1:64M 2:96M 3:128M
    REG32(REG_AP_CLK_AP_APB_CFG) = apb_cfg;

    apb_cfg = REG32(REG_AON_CLK_AON_APB_CFG);
    apb_cfg &=~3;
    apb_cfg |= mcu_clk_para.aon_apb_freq;  //aon apb select 128M        0:26M 1:76M 2:96M 3:128M
    REG32(REG_AON_CLK_AON_APB_CFG) = apb_cfg;
#ifdef CONFIG_SCX35LT8
    SetMiscConfig();
#endif

#else
    uint32 apb_cfg;
    apb_cfg  = REG32(REG_AP_CLK_AP_APB_CFG);
    apb_cfg &=~3;
    apb_cfg |= 1;  //apb select 64M            0:26M 1:64M 2:96M 3:128M
    REG32(REG_AP_CLK_AP_APB_CFG) = apb_cfg;

    apb_cfg = REG32(REG_AON_CLK_AON_APB_CFG);
    apb_cfg &=~3;
    apb_cfg |= 3;  //aon apb select 96M        0:4M 1:26M 2:76.8M 3:96M
    REG32(REG_AON_CLK_AON_APB_CFG) = apb_cfg;
#endif
    delay();
    return 0;
}

static uint32 AxiClkConfig(uint32 arm_clk)
{
#if defined(CONFIG_CLK_PARA)
    uint32 ca7_ckg_cfg;
    ca7_ckg_cfg  = REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(7<<8);
#ifdef CONFIG_SCX35LT8
    /*mcu_big_axi freq is the same as mcu freq*/
#else
    ca7_ckg_cfg |= ((arm_clk/(mcu_clk_para.axi_freq+1))&0x7)<<8;
#endif

    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;
#else
    uint32 ca7_ckg_cfg;
    ca7_ckg_cfg  = REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(7<<8);
    ca7_ckg_cfg |= ((arm_clk/(ARM_CLK_500M+1))&0x7)<<8;
    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;
#endif
    delay();
    return 0;
}

static uint32 DbgClkConfig(uint32 arm_clk)
{
#if defined(CONFIG_CLK_PARA)
    uint32 ca7_ckg_cfg;
    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(7<<16);
    ca7_ckg_cfg |=  ((arm_clk/(mcu_clk_para.dgb_freq+1))&0x7)<<16;
    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;
#else
    uint32 ca7_ckg_cfg;
    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(7<<16);
    ca7_ckg_cfg |=  ((arm_clk/(ARM_CLK_200M+1))&0x7)<<16;
    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;
#endif
    delay();
    return 0;
}

static uint32 McuClkConfig(uint32 arm_clk)
{
    uint32 ca7_ckg_cfg;

    /* Both big and LITTLE clock set here*/
#ifdef CONFIG_SCX35LT8
    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(0x7e);  //ap clk div = 0;
    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;

    delay();

    SetMPllClk_T8(ARM_CLK_1000M, 1);
    SetMPllClk_T8(ARM_CLK_800M, 0);
    
    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_SEL_CFG);
    ca7_ckg_cfg &= ~(0x3f); //a7 core select 26M
    ca7_ckg_cfg |=  0x36; //LITTLE select mpll0, big select mpll
    REG32(REG_AP_AHB_CA7_CKG_SEL_CFG) = ca7_ckg_cfg;

    delay();
#else
    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_SEL_CFG);
    ca7_ckg_cfg &= ~7; //a7 core select 26M
    REG32(REG_AP_AHB_CA7_CKG_SEL_CFG) = ca7_ckg_cfg;
    delay();

    SetMPllClk(arm_clk);

    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_DIV_CFG);
    ca7_ckg_cfg &= ~(7<<4);  //ap clk div = 0;
    REG32(REG_AP_AHB_CA7_CKG_DIV_CFG) = ca7_ckg_cfg;

    ca7_ckg_cfg  =  REG32(REG_AP_AHB_CA7_CKG_SEL_CFG);
    ca7_ckg_cfg &= ~7; //a7 core select 26M
    ca7_ckg_cfg |=  6; //a7 core select mcu MPLL       0:26M 1:(TWPLL)512M 2:(TWPLL)768M 3:(DPLL)1334M 4:(LTEPLL)1288M 5:(TWPLL)1536M 6:(MPLL)1500M
    REG32(REG_AP_AHB_CA7_CKG_SEL_CFG) = ca7_ckg_cfg;

    delay();
#endif

	/* sharklc set twpll/lpll ibias value */
	if((Get_chip_id() & SCX9832A_ALPHA_TAPOUT_MASK) ==//is sharklc
		(SCX9832A_ALPHA_TAPOUT & SCX9832A_ALPHA_TAPOUT_MASK)){
		REG32(REG_AON_APB_TWPLL_CFG1) &= ~(3<<16);
		REG32(REG_AON_APB_TWPLL_CFG1) |= (2<<16);//set ibias 2

		REG32(REG_AON_APB_LTEPLL_CFG1) &= ~(3<<16);
		REG32(REG_AON_APB_LTEPLL_CFG1) |= (1<<16);//set ibias 1
	}
    return 0;
}

#if defined(CONFIG_VOL_PARA)
typedef struct {
	uint8 trim;
	uint8 offset;
} dcdc_cal_t, * dcdc_cal_t_ptr;

/* FIXME: step 100/32 = 3.125mv */
#define DCDC_CAL_MAX	32
static const dcdc_cal_t dcdc_cal_tbl[DCDC_CAL_MAX] = {
	{.trim = 0, .offset = 0},
	{.trim = 1, .offset = 3},
	{.trim = 2, .offset = 6},
	{.trim = 3, .offset = 9},
	{.trim = 4, .offset = 13},
	{.trim = 5, .offset = 16},
	{.trim = 6, .offset = 19},
	{.trim = 7, .offset = 22},
	{.trim = 8, .offset = 25},
	{.trim = 9, .offset = 28},
	{.trim = 10, .offset = 31},
	{.trim = 11, .offset = 34},
	{.trim = 12, .offset = 38},
	{.trim = 13, .offset = 41},
	{.trim = 14, .offset = 44},
	{.trim = 15, .offset = 47},
	{.trim = 16, .offset = 50},
	{.trim = 17, .offset = 53},
	{.trim = 18, .offset = 56},
	{.trim = 19, .offset = 59},
	{.trim = 20, .offset = 63},
	{.trim = 21, .offset = 66},
	{.trim = 22, .offset = 69},
	{.trim = 23, .offset = 72},
	{.trim = 24, .offset = 75},
	{.trim = 25, .offset = 78},
	{.trim = 26, .offset = 81},
	{.trim = 27, .offset = 84},
	{.trim = 28, .offset = 88},
	{.trim = 29, .offset = 91},
	{.trim = 30, .offset = 94},
	{.trim = 31, .offset = 97},
};

/* DCDC MEM output select:
 * [BONDOPT2 BONDOPT1]
 * 00: DDR2 application (1.2v)
 * 01: DDR3L application (1.35v)
 * 10: DDR3 application (1.5v)
 * 11: DDR1 application (1.8v)
 * DCDC MEM converter control bits with two bonding options as [bpt2 bpt1 xxx], list below:
 * 000: 1.2v
 * 001: 1.25v
 * 010: 1.35v
 * 011: 1.30v
 * 100: 1.50v
 * 101: 1.40v
 * 110: 1.80v
 * 111: 1.90v
 * DCDC MEM calibration control bits with small adjust step is 200/32mv.
 */

typedef struct {
	int idx:3, vol:13;
} dcdc_ctl_t, * dcdc_ctl_t_ptr;

/* FIXME: manual sort dcdc control voltage
 */
#define DCDC_CTL_MAX	8

static const dcdc_ctl_t dcdc_ctl_vols[DCDC_CTL_MAX] = {
	{.idx = 5, .vol = 650,},
	{.idx = 1, .vol = 700,},
	{.idx = 2, .vol = 800,},
	{.idx = 3, .vol = 900,},
	{.idx = 4, .vol = 1000,},
	{.idx = 0, .vol = 1100,}, //chip default
	{.idx = 6, .vol = 1200,},
	{.idx = 7, .vol = 1300,},
};

static const dcdc_ctl_t dcdc_mem_vols[DCDC_CTL_MAX] = {
	{.idx = 0, .vol = 1200,}, //chip default
	{.idx = 1, .vol = 1250,},
	{.idx = 3, .vol = 1300,},
	{.idx = 2, .vol = 1350,},
	{.idx = 5, .vol = 1400,},
	{.idx = 4, .vol = 1500,},
	{.idx = 6, .vol = 1800,},
	{.idx = 7, .vol = 1900,},
};

static const dcdc_ctl_t dcdc_gen_vols[DCDC_CTL_MAX] = {
	{.idx = 1, .vol = 1800,},
	{.idx = 2, .vol = 1900,},
	{.idx = 3, .vol = 2000,},
	{.idx = 4, .vol = 2100,},
	{.idx = 0, .vol = 2200,},
	{.idx = 5, .vol = 2300,},
	{.idx = 6, .vol = 2400,}, //chip default
	{.idx = 7, .vol = 2500,},
};

static int dcdc_match_ctl(dcdc_ctl_t_ptr pctl, int len, int to_vol)
{
	int i = 0;

	for (i = len - 1; i >= 0; i--) {
		if (to_vol >= pctl->vol)
			return i; //return pctl->idx;
		pctl--;
	}

	return -1;
}

static int div_2(unsigned int dividend, unsigned int divisor, int *prem)
{
	if (divisor == 0)
		return 0;

	if (dividend < divisor)
		return 0;
	else if (dividend == divisor)
		return 1;

	unsigned int k, c, res = 0, rem = 0;

	while (dividend > divisor) {
		for (k = 0, c = divisor; dividend >= c; c <<= 1, k++) {
			if (dividend - c < divisor) {
				res += 1 << k;
				rem = dividend - c;
				break;
			}
		}
		if (dividend - c < divisor)
			break;

		res += 1 << (k - 1);
		dividend -= c >> 1;
	}

	if (prem)
		*prem = rem;

	return res;
}

static int dcdc_match_trm(dcdc_cal_t_ptr ptrm, int len, int offset_vol)
{
	int i = 0;

	for (i = len - 1; i >= 0; i--) {
		if (offset_vol >= ptrm->offset)
			return ptrm->trim;
		ptrm--;
	}

	return -1;
}

#if defined(CONFIG_ADIE_SC2723) || defined(CONFIG_ADIE_SC2723S)
static void dcdc_calibrate(int chan, int to_vol)
{
	int i = 0, j = 0;
	uint32 ctl_vol = 0;
	uint16 trim = 0;

	if(to_vol <= 0)
		return;

	ctl_vol = to_vol;

	switch(chan) {
	case 10: // dcdc arm
		i = dcdc_match_ctl((dcdc_ctl_t_ptr)&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm((dcdc_ctl_t_ptr)&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_ARM_ADI, j << 0 | ((dcdc_ctl_vols[i].idx & 0x7) << 5));

		break;
	case 11: //dcdc core
		i = dcdc_match_ctl((dcdc_ctl_t_ptr)&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm((dcdc_ctl_t_ptr)&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_CORE_ADI, j << 0 | ((dcdc_ctl_vols[i].idx & 0x7) << 5));

		break;
	case 12: //dcdc mem
		if(ctl_vol < 600)
			return;

		ctl_vol -= 600;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_MEM_ADI, trim & 0x3FF);

		break;
	case 13: //dcdc gen
		if(ctl_vol < 600)
			return;

		ctl_vol -= 600;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_GEN_ADI, trim & 0x3FF);

		break;
	default:
		break;
	}
}

#elif defined(CONFIG_ADIE_SC2713S)
static void dcdc_calibrate(int chan, int to_vol)
{
	int i = 0, j = 0;
	uint32 ctl_vol = 0;

	if(to_vol <= 0)
		return;

	ctl_vol = to_vol;

	switch(chan) {
	case 10: // dcdc arm
		i = dcdc_match_ctl(&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_ARM_ADI, j << 0);
		ANA_REG_MSK_OR(ANA_REG_GLB_MP_MISC_CTRL, ((dcdc_ctl_vols[i].idx & 0x7) << 6), BIT(6)|BIT(7)|BIT(8));

		break;
	case 11: //dcdc core
		i = dcdc_match_ctl(&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_CORE_ADI, j << 0);
		ANA_REG_MSK_OR(ANA_REG_GLB_MP_MISC_CTRL, ((dcdc_ctl_vols[i].idx & 0x7) << 3), BIT(3)|BIT(4)|BIT(5));

		break;
	case 12: //dcdc mem
		i = dcdc_match_ctl(&dcdc_mem_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;

		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_mem_vols[i].vol) >> 1);
		if(j < 0)
			return;
		j += 0x10;
		if (j <= BITS_DCDC_MEM_CAL_ADI(~0)) {
			ANA_REG_SET(ANA_REG_GLB_DCDC_MEM_ADI, (dcdc_mem_vols[i].idx << 5) | (j << 0));
		}

		break;
	case 13: //dcdc gen
		i = dcdc_match_ctl(&dcdc_gen_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_gen_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_GEN_ADI, (dcdc_gen_vols[i].idx << 5) | (j << 0));

		break;
	default:
		break;
	}

	for(i = 0; i < 0x1000; ++i){};
	return ;
}
#else /* defined(CONFIG_ADIE_SC2711)  || defined(CONFIG_SPX15) */

static void dcdc_calibrate(int chan, int to_vol)
{
	int i = 0, j = 0;
	uint32 ctl_vol = 0;

	if(to_vol <= 0)
		return;

	ctl_vol = to_vol;

	switch(chan) {
	case 10: // dcdc arm
		i = dcdc_match_ctl(&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_ARM_ADI, j << 0 | ((dcdc_ctl_vols[i].idx & 0x7) << 5));

		break;
	case 11: //dcdc core
		i = dcdc_match_ctl(&dcdc_ctl_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_ctl_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_CORE_ADI, j << 0 | ((dcdc_ctl_vols[i].idx & 0x7) << 5));

		break;
	case 12: //dcdc mem
		i = dcdc_match_ctl(&dcdc_mem_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;

		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_mem_vols[i].vol) >> 1);
		if(j < 0)
			return;
		j += 0x10;
		if (j <= BITS_DCDC_MEM_CAL_ADI(~0)) {
			ANA_REG_SET(ANA_REG_GLB_DCDC_MEM_ADI, (dcdc_mem_vols[i].idx << 5) | (j << 0));
		}

		break;
	case 13: //dcdc gen
		i = dcdc_match_ctl(&dcdc_gen_vols[DCDC_CTL_MAX - 1], DCDC_CTL_MAX, ctl_vol);
		if(i < 0)
			return;
		j = dcdc_match_trm(&dcdc_cal_tbl[DCDC_CAL_MAX - 1], DCDC_CAL_MAX, (ctl_vol - dcdc_gen_vols[i].vol));
		if(j < 0)
			return;

		ANA_REG_SET(ANA_REG_GLB_DCDC_GEN_ADI, (dcdc_gen_vols[i].idx << 5) | (j << 0));

		break;
	default:
		break;
	}

	for(i = 0; i < 0x1000; ++i){};
	return ;
}
#endif /* defined(CONFIG_ADIE_SC2723) || defined(CONFIG_ADIE_SC2723S) */

#endif /* defined(CONFIG_VOL_PARA) */

static uint32 ArmCoreConfig(uint32 arm_clk)
{
    uint32 dcdc_arm;

#if defined(CONFIG_VOL_PARA)
#ifdef CONFIG_EFUSE
#define blk_index 15
#define bit_index 7
    uint32 efuse_value =0;
    efuse_value = adie_efuse_read(blk_index);

    /*sharklc chipid auto adapt*/
    if((Get_chip_id() & SCX9832A_ALPHA_TAPOUT_MASK) ==\
    (SCX9832A_ALPHA_TAPOUT & SCX9832A_ALPHA_TAPOUT_MASK)){
            dcdc_calibrate(10,mcu_clk_para.dcdc_arm + 50);//G2 only,default dcdc_arm 1.05v ,final target = 1000mv + 50mv + 150mv = 1200mv
            dcdc_calibrate(11,mcu_clk_para.dcdc_core);//G2 only,default dcdc_core 1.05v
    }
    /*sharkls chipid auto adapt*/
    else{
            if((efuse_value >> bit_index)&BIT(0))
            {
            	dcdc_calibrate(10,mcu_clk_para.dcdc_arm - 150);	//dcdc arm
            	dcdc_calibrate(11,mcu_clk_para.dcdc_core - 150);//dcdc core
	    }else{
        	dcdc_calibrate(10,mcu_clk_para.dcdc_arm);	//dcdc arm
        	dcdc_calibrate(11,mcu_clk_para.dcdc_core);	//dcdc core
            }
    }

#else
    dcdc_calibrate(10,mcu_clk_para.dcdc_arm);	//dcdc arm
    dcdc_calibrate(11,mcu_clk_para.dcdc_core);	//dcdc core
#endif
    dcdc_calibrate(12, mcu_clk_para.dcdc_mem);  //dcdc mem
    dcdc_calibrate(13, mcu_clk_para.dcdc_gen);  //dcdc gen for LDOs

    REG32(REG_AP_APB_APB_EB) |= BIT_AP_CKG_EB;
#endif

    delay();
    return 0;
}

static void AvsEb()
{
#if !defined(CONFIG_ARCH_SCX35L)
    REG32(REG_AON_APB_APB_EB1) |= BIT_AVS1_EB | BIT_AVS0_EB;
    REG32(0x4003003C) |= 0xF<<5; //enable channel5-8
    REG32(0x40300020)  = 2;
    REG32(0x4030001C)  = 1;
#endif
}

#ifdef CONFIG_NAND_SPL
void PLL_Ref_Sel(void)
{
#if defined (CP_SELECT_SWITCH)
	/* step1:choose XTL1_BUF to associate with CP,keep default */

	/* step2:choose CP0/CP1 subsystem to sin1 */
	REG32(REG_PMU_APB_26M_SEL_CFG) |= BIT_CP0_26M_SEL | BIT_CP1_26M_SEL;

	/* step3:choose CP's LTEPLL/TWPLL to sin1 */
	/* LTEPLL select SIN1 */
	REG32(REG_PMU_APB_LTEPLL_REL_CFG) |= BIT_LTEPLL_REF_SEL;
	/* TWPLL select SIN1 */
	REG32(REG_PMU_APB_TWPLL_REL_CFG) |= BIT_TWPLL_REF_SEL;

	delay();
#endif
}
#else
void PLL_Ref_Sel(void) {}
#endif

static uint32 ClkConfig(uint32 arm_clk)
{
    ArmCoreConfig(arm_clk);
    //AvsEb();
    AxiClkConfig(arm_clk);
    DbgClkConfig(arm_clk);
    McuClkConfig(arm_clk);
    AhbClkConfig();
    ApbClkConfig();
    return 0;
}

#ifdef CONFIG_SCX35LT8
static uint32 ADIConfig(void)
{
    REG32(REG_AON_APB_APB_EB0) |= 0x1<<16;	/*ADI_EB*/
#if 0
	REG32(REG_AON_APB_APB_RST0) |= 0x1<<17;	/*ADI_SOFT_RST*/
	{
		 uint32 wait = 50;
		 while(wait --) {}
	}
	REG32(REG_AON_APB_APB_RST0) &= ~(0x1<<17);	/*ADI_SOFT_RST*/
#endif
	REG32(0x40030020) = 0x74d0835d;//data 16 bit cmd 13bit both 29 bit
	REG32(0x40030024) |= (1 << 31);// 2 -wire mode enable
}
#endif

uint32 MCU_Init()
{

#if defined(CONFIG_CLK_PARA)

#ifdef CONFIG_SCX35LT8
	ADIConfig();
#endif

    if (ClkConfig(mcu_clk_para.core_freq))
#else
#if (defined(CONFIG_SPX15) || defined(CONFIG_SC8830))
    if (ClkConfig(ARM_CLK_1000M))
#else
    if (ClkConfig(ARM_CLK_800M))
#endif
#endif
        while(1);
    return 0;
}

#ifdef CONFIG_SCX35LT8
#define REG_AON_CLK_EMC_CFG   (0x50820010)
#endif

#if defined(CONFIG_CLK_PARA)
void set_ddr_clk(uint32 ddr_clk)
{
    volatile uint32 reg_val;
	struct ibias_table *itable;

	if(ddr_clk > 1559)	// fractiional divider max NINT is 60*26
	{
		reg_val = 0;
		reg_val &= ~(1<<26);	//interger divider
		reg_val |= (0x2<<20);		//DPLL_LPF
		reg_val |= (2<<18); 	// DPLL_REFIN 0-2M 1-4M 2-13M 3-26M
		reg_val &= ~(0x3<<16);		//DPLL_IBIAS
		reg_val |= ((NINT(ddr_clk,13)&0x7ff)<<0);		//DPLL_N
		REG32(REG_AON_APB_DPLL_CFG1) = reg_val;

	}
	else
	{
    reg_val = REG32(REG_AON_APB_DPLL_CFG1);
    reg_val |= (1<<26); 	//fractional divider
    reg_val |= (3<<18);		// DPLL_REFIN 0-2M 1-4M 2-13M 3-26M

    if((Get_chip_id() & SCX9832A_ALPHA_TAPOUT_MASK) ==//is sharklc
    (SCX9832A_ALPHA_TAPOUT & SCX9832A_ALPHA_TAPOUT_MASK)){

		itable = sharklc_adjustable_pll_table;
		for (; itable->rate <= PLL_MAX_RATE; itable++) {//set ibias
			if (ddr_clk*1000000 < itable->rate) {
				reg_val |= (itable->ibias << 16);
				break;
			}
		}
	}

    REG32(REG_AON_APB_DPLL_CFG1) = reg_val;

    reg_val = (KINT(ddr_clk,DPLL_REFIN)&0xfffff);
    reg_val |= (NINT(ddr_clk,DPLL_REFIN)&0x3f)<<24;
    REG32(REG_AON_APB_DPLL_CFG2) = reg_val;
	}

#ifdef CONFIG_SCX35LT8
    reg_val = REG32(0x402e0114);
    reg_val &= ~(1<<4);
    REG32(0x402e0114) = reg_val;

    reg_val = REG32(0x402b00b0);
    reg_val &= ~(1<<8);
    REG32(0x402b00b0) = reg_val;
#endif
    //select DPLL 533MHZ source clock
    reg_val = REG32(REG_AON_CLK_EMC_CFG);
    reg_val &= ~0x7;
    //    reg_val |= 0; //default:XTL_26M
    //    reg_val |= 1; //TDPLL_256M
    //    reg_val |= 2; //TDPLL_384M
    reg_val |= 7; //DPLL_533M
    REG32(REG_AON_CLK_EMC_CFG)= reg_val;

    delay();
}
#endif

#if defined(CONFIG_VOL_PARA)
typedef struct {
	uint16 ideal_vol;
	const char name[14];
}vol_para_t;

#define VOL_PARA_NUM	7
vol_para_t vol_para[VOL_PARA_NUM + 1] __align(16) = {
	[0] = { /* Begin Array, DO NOT remove it! */
		.ideal_vol = 0xfaed,	.name = "volpara_begin",
	},
#if defined(CONFIG_SPX30G) || defined(CONFIG_ARCH_SCX35L)
	[1] = {
		.ideal_vol = 900,	.name = "vddarm",
	},
	[2] = {
		.ideal_vol = 900,	.name = "vddcore",
	},
#else
	[1] = {
		.ideal_vol = 1200,	.name = "vddarm",
	},
	[2] = {
		.ideal_vol = 1100,	.name = "vddcore",
	},
#endif
	[3] = {
		.ideal_vol = 0,	.name = "vddmem",
	},

	[4] = {
		.ideal_vol = 0,	.name = "vddgen",
	},

	//TODO: add your ideal ldo here like the following example
	[5] = {
		.ideal_vol = 2800,	.name = "vddcammot",
	},

	[6] = { /* End Array, DO NOT remove it! */
		.ideal_vol = 0xdeaf,	.name = "volpara_end",
	},

	/* Note: 16bytes debug datas at the end! */

	/****************************************
	//
	//byte 5~16: reserved
	//

	//
	//byte 3~4: dcdc ctrl calibration flag
	//
	* bit[13] ~ bit[15] : reserved
	* bit[12] : dcdcgen
	* bit[11] : dcdcmem
	* bit[10] : dcdcarm
	* bit[9]   : dcdccore
	* bit[8]   : vddrf0
	* bit[7]   : vddemmccore
	* bit[6]   : vddemmcio
	* bit[5]   : vdddcxo
	* bit[4]   : vddcon
	* bit[3]   : vdd25
	* bit[2]   : vdd28
	* bit[1]   : vdd18
	* bit[0]   : vddbg

	//
	//byte 1~2: ldo ctrl calibration flag
	//
	* bit[12] ~ bit[15] : reserved
	* bit[11] : vddlpref
	* bit[10] : dcdcwpa
	* bit[9]   : vddclsg
	* bit[8]   : vddusb
	* bit[7]   : vddcammot
	* bit[6]   : vddcamio
	* bit[5]   : vddcamd
	* bit[4]   : vddcama
	* bit[3]   : vddsim2
	* bit[2]   : vddsim1
	* bit[1]   : vddsim0
	* bit[0]   : vddsd
	******************************************/
};

int Vol_Init()
{
	/*
	 * FIXME: Update LDOs voltage in u-boot
	 */
	BUG_ON(sizeof(vol_para_t) != 16);
	(mcu_clk_para.dcdc_arm) ? vol_para[1].ideal_vol = mcu_clk_para.dcdc_arm : 0;
	(mcu_clk_para.dcdc_core) ? vol_para[2].ideal_vol = mcu_clk_para.dcdc_core : 0;
	(mcu_clk_para.dcdc_mem) ? vol_para[3].ideal_vol = mcu_clk_para.dcdc_mem : 0;
	(mcu_clk_para.dcdc_gen) ? vol_para[4].ideal_vol = mcu_clk_para.dcdc_gen : 0;

	if(mcu_clk_para.debug_flags[0]) {
		int num = (VOL_PARA_NUM + 1);
		volatile uint32* p_flag = (volatile uint32*)(&vol_para[num-1]);
		*p_flag = mcu_clk_para.debug_flags[0];
	}

	return (sizeof(vol_para) << 16) + sizeof(vol_para_t);
}
#endif
void chipram_env_set(u32 mode)
{
#if defined(CONFIG_SCX35L64) || defined(CONFIG_CHIP_ENV_SET)
	chipram_env_t *p_env;
	p_env = (struct chipram_env *)CHIPRAM_ENV_ADDR;
	p_env->magic = CHIPRAM_ENV_MAGIC;
	p_env->mode = mode;
	p_env->dram_size = 0;
	p_env->vol_para_addr = (u64)vol_para;
#endif
}
/*switch to arch64 mode and set pc to addr*/
extern void switch_to_arch_64(void);
void switch64_and_set_pc(u32 addr)
{
#ifdef CONFIG_SCX35L64
	/*release ARM7 space, then AP can access*/
	REG32(REG_AON_APB_ARM7_SYS_SOFT_RST) &= ~(1 << 4);
	REG32(REG_PMU_APB_CP_SOFT_RST) &= ~(1 << 8);
	/*set core0 reset vector*/
	REG32(0x50820020) = addr;

#ifdef CONFIG_SCX35LT8
	REG32(0x50820034) |= 0x1;
#else
	REG32(0x50820034) |= 0xf;

	REG32(0x50820024) = addr;
	REG32(0x50820028) = addr;
	REG32(0x5082002c) = addr;
	REG32(0x402b1008) = BIT(24);
	REG32(0x402b100c) = BIT(24);
	REG32(0x402b1010) = BIT(24);
#endif

	switch_to_arch_64();
#endif
}

extern void sdram_init(void);
void Chip_Init (void) /*lint !e765 "Chip_Init" is used by init.s entry.s*/
{
    uint32 value;
    
    /* MUST stand first statement */
    PLL_Ref_Sel();
#if defined(CONFIG_SPX15)
	if(0x2711A000 == ANA_GET_CHIP_ID()) {
	    value = ANA_REG_GET(0x4003883c);
	    value &= ~0x7f00;
	    value |= 0x38 << 8;
	    ANA_REG_SET(0x4003883c,value);

	    value = ANA_REG_GET(0x40038820);
	    value &= ~0xff;
	    value |= 0x38 << 0;
	    ANA_REG_SET(0x40038820,value);
	}
#endif
    
    MCU_Init();
#if defined(CONFIG_VOL_PARA)
    Vol_Init();
#endif
#ifndef CONFIG_SPX35L64_FPGA
    sdram_init();
#endif
}

