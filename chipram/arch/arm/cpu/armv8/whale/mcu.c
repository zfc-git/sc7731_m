
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

#define MPLL_REFIN 26
#define DPLL_REFIN 26
#define NINT(FREQ,REFIN)	(FREQ/REFIN)
#define KINT(FREQ,REFIN)	((FREQ-(FREQ/REFIN)*REFIN)*1048576/REFIN)

#if defined(CONFIG_DUAL_DDR)
extern void noc_init(void);
#endif

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
    .dcdc_arm0 = DCDC_ARM0,
    .dcdc_core = DCDC_CORE,
#ifdef DCDC_MEM
	.dcdc_mem = DCDC_MEM,
#endif
#ifdef DCDC_GEN
	.dcdc_gen = DCDC_GEN,
#endif
#if defined(CONFIG_ADIE_SC2723) || defined(CONFIG_ADIE_SC2723S)
	.debug_flags[0] = 0x3FFFFFF1, //0x3FFFFFFF
#endif
#ifdef DCDC_ARM1
	.dcdc_arm1 = DCDC_ARM1,
#endif
    .magic_end = MAGIC_END
};
#endif

static void delay()
{
    volatile uint32 i;
    for (i=0; i<0x100; i++);
}

struct whale_ibias_table {
	unsigned long rate;
	u8 ibias;
};

#define WHALE_PLL_MAX_RATE		(0xFFFFFFFF)

struct whale_ibias_table whale_adjustable_pll_table[] = {
	{
		.rate = 936000000,
		.ibias = 0x0,
	},
	{
		.rate = 1300000000,
		.ibias = 0x1,
	},
	{
		.rate = 1612000000,
		.ibias = 0x10,
	},
	{
		.rate = WHALE_PLL_MAX_RATE,
		.ibias = 0x10,
	},
};

struct whale_ibias_table whale_mpll1_table[] = {
	{
		.rate = 1404000000,
		.ibias = 0x0,
	},
	{
		.rate = 1872000000,
		.ibias = 0x1,
	},
	{
		.rate = 2548000000,
		.ibias = 0x10,
	},
	{
		.rate = WHALE_PLL_MAX_RATE,
		.ibias = 0x10,
	},
};

static uint32 SetBigLitMPllClk(uint32 clk, uint32 litflag)
{
	uint32 clk_cfg1_reg, refin, clk_cfg2_reg, clk_cfg1, clk_cfg2;
	uint32 nint, kint;
	struct whale_ibias_table *itable;

	clk = clk/1000000;

	if (litflag) {
		clk_cfg1_reg = 0x40400024;
		clk_cfg2_reg = 0x40400028;
		refin = 26;
		itable = whale_adjustable_pll_table;
	} else {
		clk_cfg1_reg = 0x4040002c;
		clk_cfg2_reg = 0x40400030;
		refin = 52;
		itable = whale_mpll1_table;
	}

	/*clk_cfg1 = REG32(clk_cfg1_reg);
	clk_cfg1 |= (1 << 13);
	REG32(clk_cfg1_reg) = clk_cfg1;*/
	delay();

	clk_cfg1 = REG32(clk_cfg1_reg);
	clk_cfg1 |= (1 << 25) | (1 << 27);

	nint = (NINT(clk,refin)&0x3f)<<24;
	kint = KINT(clk, refin) & 0xfffff;
	clk_cfg2 = REG32(clk_cfg2_reg);
	clk_cfg2 &= ~(0x3f << 24);
	clk_cfg2 &= ~(0xfffff);
	clk_cfg2 |= nint | kint;
	/* set ibias */
	clk_cfg1 &= ~(3 << 16);
	for (; itable->rate == WHALE_PLL_MAX_RATE; itable++) {
		if (clk*1000000 < itable->rate) {
			clk_cfg1 |= (itable->ibias << 16);
		}
	}

	REG32(clk_cfg1_reg) = clk_cfg1;
	REG32(clk_cfg2_reg) = clk_cfg2;

	delay();
	delay();
	delay();
	delay();
	delay();


	return 0;
}

static uint32 SetMPllClk(uint32 clk)
{
	SetBigLitMPllClk(clk, 1);
	SetBigLitMPllClk(1500000000, 0);

	return 0;
}

static uint32 SetDpllClk(uint32 clk, uint32 dnum)
{
	uint32 clk_cfg1_reg, clk_cfg2_reg, clk_cfg1, clk_cfg2;
	uint32 nint, kint;
	struct whale_ibias_table *itable;

	clk = clk/1000000;

	if (dnum == 0) {
		clk_cfg1_reg = 0x40400034;
		clk_cfg2_reg = 0x40400038;
	} else {
		clk_cfg1_reg = 0x4040003c;
		clk_cfg2_reg = 0x40400040;
	}

	itable = whale_adjustable_pll_table;

	/*clk_cfg1 = REG32(clk_cfg1_reg);
	clk_cfg1 |= (1 << 13);
	REG32(clk_cfg1_reg) = clk_cfg1;*/
	delay();

	clk_cfg1 = REG32(clk_cfg1_reg);
	if (dnum == 0)
		clk_cfg1 |= (1 << 25) | (1 << 27);
	else
		clk_cfg1 |= (1 << 24) | (1 << 26);

	nint = (NINT(clk,26)&0x3f)<<24;
	kint = KINT(clk, 26) & 0xfffff;
	clk_cfg2 = REG32(clk_cfg2_reg);
	clk_cfg2 &= ~(0x3f << 24);
	clk_cfg2 &= ~(0xfffff);
	clk_cfg2 |= nint | kint;
	/* set ibias */
	if (dnum == 0)
		clk_cfg1 &= ~(3 << 16);
	else
		clk_cfg1 &= ~(3 << 17);
	for (; itable->rate == WHALE_PLL_MAX_RATE; itable++) {
		if (clk*1000000 < itable->rate) {
			clk_cfg1 |= (itable->ibias << 16);
		}
	}

	REG32(clk_cfg1_reg) = clk_cfg1;
	REG32(clk_cfg2_reg) = clk_cfg2;

	delay();
	delay();
	delay();
	delay();
	delay();

	REG32(clk_cfg1_reg) &= ~(1 << 13);

	return 0;
}

static uint32 DpllClkConfig(uint32 clk)
{
	/*SetDpllClk(clk, 0);
	SetDpllClk(clk, 1);*/
}

#if defined(CONFIG_VOL_PARA)

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

#if defined(CONFIG_ADIE_SC2731)
static void dcdc_calibrate(int chan, int to_vol)
{
	int i = 0, j = 0;
	uint32 ctl_vol = 0;
	uint16 trim = 0;

	if(to_vol <= 0)
		return;

	ctl_vol = to_vol;

	switch(chan) {
	case 10: // dcdc arm0
		if(ctl_vol < 400)
			return;

		ctl_vol -= 400;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_ARM0_VOL, trim & 0x3FF);

		break;
	case 11: // dcdc arm1
		if(ctl_vol < 400)
			return;

		ctl_vol -= 400;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_ARM1_VOL, trim & 0x3FF);

		break;
	case 12: //dcdc core
		if(ctl_vol < 400)
			return;

		ctl_vol -= 400;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_CORE_VOL, trim & 0x3FF);

		break;
	case 13: //dcdc mem
		if(ctl_vol < 600)
			return;

		ctl_vol -= 600;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_MEM_VOL, trim & 0x3FF);

		break;
	case 14: //dcdc gen
		if(ctl_vol < 600)
			return;

		ctl_vol -= 600;
		trim = (uint16)div_2(ctl_vol << 5, 100, 0);
		ANA_REG_SET(ANA_REG_GLB_DCDC_GEN_VOL, trim & 0x3FF);

		break;
	default:
		break;
	}
}
#endif /* defined(CONFIG_ADIE_SC2731) */

static uint32 ArmCoreConfig(uint32 arm_clk)
{
    uint32 dcdc_arm;

#if defined(CONFIG_VOL_PARA)
    dcdc_calibrate(10,mcu_clk_para.dcdc_arm0);	//dcdc arm
    dcdc_calibrate(11,mcu_clk_para.dcdc_arm1);	//dcdc arm
    dcdc_calibrate(12,mcu_clk_para.dcdc_core);	//dcdc core
    dcdc_calibrate(13, mcu_clk_para.dcdc_mem);  //dcdc mem
    dcdc_calibrate(14, mcu_clk_para.dcdc_gen);  //dcdc gen for LDOs

    REG32(REG_AP_APB_APB_EB) |= BIT_CKG_EB;
#endif
    delay();
    return 0;
}

#endif

static uint32 ApbClkConfig()
{
//#if defined(CONFIG_CLK_PARA)
	/*uint32 apb_cfg;
	apb_cfg  = REG32(0x20000020);
	apb_cfg &=~3;
	apb_cfg |= mcu_clk_para.apb_freq;  //apb select 64M            0:26M 1:64M 2:96M 3:128M
	REG32(0x20000020) = apb_cfg;

	apb_cfg = REG32(0x402d0230);
	apb_cfg &=~3;
	apb_cfg |= mcu_clk_para.aon_apb_freq;  //aon apb select 128M        0:26M 1:76M 2:96M 3:128M
	REG32(0x402d0230) = apb_cfg;
#else*/
	uint32 apb_cfg, i; 
	apb_cfg = REG32(0x20000020); 
	apb_cfg &= ~3; 
	/* ap apb select 64m */
	apb_cfg |= 3;
	REG32(0x20000020) = apb_cfg; 

	apb_cfg = REG32(0x402d0230); 
	apb_cfg &= ~7; 
	/* aon apb select 153.6m */
	apb_cfg |= 5;
	REG32(0x402d0230) = apb_cfg; 

	for (i = 0; i < 0x100; i++) { 
	}
//#endif
	delay();
	return 0;
}

static uint32 AxiClkConfig(uint32 arm_clk)
{
#if defined(CONFIG_CLK_PARA)
	uint32 clk_cfg;

	clk_cfg = REG32(0x402d0324);
	clk_cfg &= ~3;
	clk_cfg |= 3;
	REG32(0x402d0324) = clk_cfg;
#else
	uint32 clk_cfg;

	clk_cfg = REG32(0x402d0324);
	clk_cfg &= ~3;
	clk_cfg |= 3;
	REG32(0x402d0324) = clk_cfg;
#endif
	delay();
	return 0;
}

static uint32 DbgClkConfig(uint32 arm_clk)
{
#if defined(CONFIG_CLK_PARA)
	uint32 clk_cfg;

	clk_cfg = REG32(0x40880020);
	clk_cfg &= ~(7 << 8 | 7 << 12 | 7 << 16);
	clk_cfg |= (1 << 8) | (3 << 12) | (3 << 16);
	REG32(0x40880020) = clk_cfg;

	clk_cfg = REG32(0x40880024);
	clk_cfg &= ~(7 << 8 | 7 << 12 | 7 << 16);
	clk_cfg |= (1 << 8) | (3 << 12) | (3 << 16);
	REG32(0x40880024) = clk_cfg;
#else
	uint32 clk_cfg;

	clk_cfg = REG32(0x40880020);
	clk_cfg &= ~(7 << 8 | 7 << 12 | 7 << 16);
	clk_cfg |= (1 << 8) | (3 << 12) | (3 << 16);
	REG32(0x40880020) = clk_cfg;

	clk_cfg = REG32(0x40880024);
	clk_cfg &= ~(7 << 8 | 7 << 12 | 7 << 16);
	clk_cfg |= (1 << 8) | (3 << 12) | (3 << 16);
	REG32(0x40880024) = clk_cfg;
#endif
    delay();
    return 0;
}

static uint32 McuClkConfig(uint32 arm_clk)
{
	uint32 clk_cfg;

	SetMPllClk(arm_clk);
	delay();

	/* little cpu switch to mpll0 */
	clk_cfg = REG32(0x40880020);
	clk_cfg |= 8;
	REG32(0x40880020) = clk_cfg;
	delay();

	clk_cfg = REG32(0x40880024);
	clk_cfg |= 8;
	REG32(0x40880024) = clk_cfg;
	delay();

	return 0;
}

static void MiscClkConfig(void)
{
	/* set cci clock to 512m */
	REG32(0x402d0300) &= ~3;
	REG32(0x402d0300) |= 3;
}

static void AvsEb()
{
#if !defined(CONFIG_WHALE)
    REG32(REG_AON_APB_APB_EB1) |= BIT_AVS1_EB | BIT_AVS0_EB;
    REG32(0x4003003C) |= 0xF<<5; //enable channel5-8
    REG32(0x40300020)  = 2;
    REG32(0x4030001C)  = 1;
#endif
}

static uint32 ClkConfig(uint32 arm_clk)
{
    ArmCoreConfig(arm_clk);
    //AvsEb();
    AxiClkConfig(arm_clk);
    DbgClkConfig(arm_clk);
    McuClkConfig(ARM_CLK_800M);
    //AhbClkConfig();
    ApbClkConfig();
    MiscClkConfig();
    DpllClkConfig(800000000);
    return 0;
}

uint32 MCU_Init()
{

#if defined(CONFIG_CLK_PARA)
    if (ClkConfig(mcu_clk_para.core_freq))
#else
    if (ClkConfig(ARM_CLK_800M))
#endif
        while(1);
    return 0;
}
#if 0
#if defined(CONFIG_CLK_PARA)
void set_ddr_clk(uint32 ddr_clk)
{
    volatile uint32 reg_val;

    reg_val = REG32(REG_AON_APB_DPLL_CFG1);
    reg_val |= (1<<26); 	//fractional divider
    reg_val |= (3<<18);		// DPLL_REFIN 0-2M 1-4M 2-13M 3-26M
    REG32(REG_AON_APB_DPLL_CFG1) = reg_val;

    reg_val = (KINT(ddr_clk,DPLL_REFIN)&0xfffff);
    reg_val |= (NINT(ddr_clk,DPLL_REFIN)&0x3f)<<24;
    REG32(REG_AON_APB_DPLL_CFG2) = reg_val;

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

#endif
void chipram_env_set(u32 mode)
{
#ifdef CONFIG_WHALE
	chipram_env_t *p_env;
	p_env = (struct chipram_env *)CHIPRAM_ENV_ADDR;
	p_env->magic = CHIPRAM_ENV_MAGIC;
	p_env->mode = mode;
	p_env->dram_size = 0;
#endif
}
/*switch to arch64 mode and set pc to addr*/
extern void switch_to_arch_64(void);
void switch64_and_set_pc(u32 addr)
{
#ifdef CONFIG_WHALE
	/*release ARM7 space, then AP can access*/
	//REG32(REG_AON_APB_ARM7_SYS_SOFT_RST) &= ~(1 << 4);
	REG32(REG_PMU_APB_SYS_SOFT_RST) &= ~(1 << 21);
	/*set core0 reset vector*/
	REG32(0x50820020) = addr;

//	switch_to_arch_64();
#endif
}

void SwitchPll(void)
{
	uint32 clk_cfg;

	/* power up rpll0 and rpll1 */
	REG32(REG_PMU_APB_RPLL_REL_CFG) |= 0x7c007c;
	delay();
	delay();

	/* TWPLL switch */
	clk_cfg = REG32(0x40400060);
	clk_cfg &= ~(7 << 29);
	REG32(0x40400060) = clk_cfg;

	/* LTEPLL switch */
	clk_cfg = REG32(0x40400064);
	clk_cfg &= ~(7 << 27);
	REG32(0x40400064) = clk_cfg;

	clk_cfg = REG32(0x4040006c);
	clk_cfg &= ~(7 << 27);
	REG32(0x4040006c) = clk_cfg;

	/* lvdsrfpll switch */
	clk_cfg = REG32(0x402b034c);
	clk_cfg &= ~(0x3f);
	REG32(0x402b034c) = clk_cfg;

	delay();
	delay();
	delay();
	delay();
	delay();
	delay();
	delay();
}

void Chip_Init (void) /*lint !e765 "Chip_Init" is used by init.s entry.s*/
{
    uint32 value;
    sci_adi_init();
    SwitchPll();
    MCU_Init();

#if defined(CONFIG_CLK_PARA)
    sdram_init(mcu_clk_para.ddr_freq);
#else
    sdram_init(DDR_FREQ);
#endif

#if defined(CONFIG_DUAL_DDR)
    noc_init();
#endif
}

