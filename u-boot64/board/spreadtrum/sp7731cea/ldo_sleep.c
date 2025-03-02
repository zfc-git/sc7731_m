#include <asm/arch/sci_types.h>
#include "adi_hal_internal.h"
#include <asm/arch/chip_drv_common_io.h>
#include <asm/arch/sprd_reg.h>

/***************************************************************************************************************************/
/*     VDD18 VDD28 VDD25 RF0 RF1 RF2 EMMCIO EMMCCORE DCDCARM DCDCWRF DCDCWPA DCDCGEN DCDCOTP AVDD18 SD SIM0 SIM1 SIM2 CAMA */
/* AP    x     x    v     v   v   v     v      v        v       v       v       x       v      v    v    v   v     v    v  */
/* CP0   x     x    v     v   v   x     x      x        x       v       x       x       x      x    x    x   x     x    x  */
/* CP1   x     x    v     x   x   x     x      x        x       x       x       x       x      x    x    x   x     x    x  */
/* CP2   x     x    v     v   x   v     x      x        x       v       x       x       x      x    x    x   x     x    x  */
/* EX0   x     x    x     v   x   x     x      x        x       x       x       x       x      x    x    x   x     x    x  */
/* EX1   x     x    x     x   v   x     x      x        x       x       x       x       x      x    x    x   x     x    x  */
/* EX2   x     x    x     v   x   x     x      x        x       x       x       x       x      x    x    x   x     x    x  */
/***************************************************************************************************************************/

/***************************************************************************************************************************/
/*     CAMD CMAIO CAMMOT USB CLSG LPREF LPRF0 LPRF1 LPRF2 LPEMMCIO LPEMMCCORE LPWPA  LPGEN   LPARM LPMEM LPCORE LPBG  BG   */
/* AP    v     v    v     v   v   v     v      v     v       v       v          x       v      v     v     v     v     v   */
/* CP0   x     x    x     x   x   x     x      x     x       x       x          x       x      x     x     x     x     x   */
/* CP1   x     x    x     x   x   x     x      x     x       x       x          x       x      x     x     x     x     x   */
/* CP2   x     x    x     x   x   x     v      v     x       x       x          x       x      x     x     x     x     x   */
/* EX0   x     x    x     x   x   x     x      x     v       x       x          x       x      x     x     x     x     x   */
/* EX1   x     x    x     x   x   x     x      x     x       x       x          x       x      x     x     x     x     x   */
/* EX2   x     x    x     x   x   x     x      x     x       x       x          x       x      x     x     x     x     x   */
/***************************************************************************************************************************/
static int init_ldo_voltage(void);
static int init_adie_global(void);
extern int sprd_get32_less(void);
void init_ldo_sleep_gr(void)
{
	unsigned int reg_val;
	unsigned int feature_32k_less = sprd_get32_less();

        ANA_REG_SET(ANA_REG_GLB_PWR_WR_PROT_VALUE,0x6e7f);
        while( (ANA_REG_GET(ANA_REG_GLB_PWR_WR_PROT_VALUE) & 0x8000) != 0x8000 );

#if defined(CONFIG_ADIE_SC2723S) || defined(CONFIG_ADIE_SC2723)
	ANA_REG_SET(ANA_REG_GLB_LDO_DCDC_PD,
		//BIT_LDO_EMM_PD | /*use by 7731C efuse*/
		BIT_DCDC_TOPCLK6M_PD | /*no use for 7731C*/
		BIT_DCDC_RF_PD | /*no use for 7731C*/
		//BIT_DCDC_GEN_PD |
		//BIT_DCDC_MEM_PD |
		//BIT_DCDC_ARM_PD |
		//BIT_DCDC_CORE_PD |
		//BIT_LDO_RF0_PD |
		//BIT_LDO_EMMCCORE_PD |
		//BIT_LDO_GEN1_PD |
		//BIT_LDO_DCXO_PD |
		//BIT_LDO_GEN0_PD |
		//BIT_LDO_VDD25_PD |
		//BIT_LDO_VDD28_PD |
		//BIT_LDO_VDD18_PD |
		//BIT_BG_PD |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_LDO_PD_CTRL,
		//BIT_LDO_LPREF_PD_SW |
		BIT_DCDC_WPA_PD | /*no use for 7731C*/
		BIT_DCDC_CON_PD |  /*no use for 7731C*/
		BIT_LDO_WIFIPA_PD | /*no use for 7731C*/
		BIT_LDO_SDCORE_PD |
		BIT_LDO_USB_PD |
		BIT_LDO_CAMMOT_PD |
		BIT_LDO_CAMIO_PD |
		BIT_LDO_CAMD_PD |
		BIT_LDO_CAMA_PD |
		BIT_LDO_SIM2_PD |
		BIT_LDO_SIM1_PD |
		BIT_LDO_SIM0_PD |
		BIT_LDO_SDIO_PD |
		0
	);

        ANA_REG_SET(ANA_REG_GLB_PWR_WR_PROT_VALUE,0x0000);

	if(!feature_32k_less){
		ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL0,
			BIT_SLP_IO_EN |
			//BIT_SLP_DCDCRF_PD_EN | /*no use for 7731C*/
			//BIT_SLP_DCDCCON_PD_EN | /*no use for 7731C*/
			//BIT_SLP_DCDCGEN_PD_EN |
			//BIT_SLP_DCDCWPA_PD_EN | /*no use for 7731C*/
			BIT_SLP_DCDCARM_PD_EN |
			BIT_SLP_LDOVDD25_PD_EN |
			BIT_SLP_LDORF0_PD_EN |
			//BIT_SLP_LDOEMMCCORE_PD_EN | /*control by emmc drv not ap sleep*/
			//BIT_SLP_LDOGEN0_PD_EN | /*control by emmc drv not ap sleep*/
			BIT_SLP_LDODCXO_PD_EN |
			BIT_SLP_LDOGEN1_PD_EN |
			//BIT_SLP_LDOWIFIPA_PD_EN | /*no use for 7731C*/
			//BIT_SLP_LDOVDD28_PD_EN |
			//BIT_SLP_LDOVDD18_PD_EN |
			0
		);
		ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL2,
				//BIT_SLP_DCDCRF_LP_EN |
				//BIT_SLP_DCDCCON_LP_EN |
				BIT_SLP_DCDCCORE_LP_EN |
				BIT_SLP_DCDCMEM_LP_EN |
				//BIT_SLP_DCDCARM_LP_EN |
				BIT_SLP_DCDCGEN_LP_EN |
				//BIT_SLP_DCDCWPA_LP_EN |
				//BIT_SLP_LDORF0_LP_EN |
				//BIT_SLP_LDOEMMCCORE_LP_EN |
				//BIT_SLP_LDOGEN0_LP_EN |
				//BIT_SLP_LDODCXO_LP_EN |
				//BIT_SLP_LDOGEN1_LP_EN |
				//BIT_SLP_LDOWIFIPA_LP_EN |
				BIT_SLP_LDOVDD28_LP_EN |
				//BIT_SLP_LDOVDD18_LP_EN | /*sc2723 have no lp mode*/
				0
			   );
	} else {
		ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL0,
				BIT_SLP_IO_EN |
				//BIT_SLP_DCDCRF_PD_EN | /*no use for 7731C*/
				//BIT_SLP_DCDCCON_PD_EN | /*no use for 7731C*/
				//BIT_SLP_DCDCGEN_PD_EN |
				//BIT_SLP_DCDCWPA_PD_EN | /*no use for 7731C*/
				BIT_SLP_DCDCARM_PD_EN |
				BIT_SLP_LDOVDD25_PD_EN |
				BIT_SLP_LDORF0_PD_EN |
				//BIT_SLP_LDOEMMCCORE_PD_EN | /*control by emmc drv not ap sleep*/
				//BIT_SLP_LDOGEN0_PD_EN | /*control by emmc drv not ap sleep*/
				//BIT_SLP_LDODCXO_PD_EN |
				BIT_SLP_LDOGEN1_PD_EN |
				//BIT_SLP_LDOWIFIPA_PD_EN | /*no use for 7731C*/
				//BIT_SLP_LDOVDD28_PD_EN |
				//BIT_SLP_LDOVDD18_PD_EN |
				0
			   );
		ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL2,
				//BIT_SLP_DCDCRF_LP_EN |
				//BIT_SLP_DCDCCON_LP_EN |
				BIT_SLP_DCDCCORE_LP_EN |
				BIT_SLP_DCDCMEM_LP_EN |
				//BIT_SLP_DCDCARM_LP_EN |
				BIT_SLP_DCDCGEN_LP_EN |
				//BIT_SLP_DCDCWPA_LP_EN |
				//BIT_SLP_LDORF0_LP_EN |
				//BIT_SLP_LDOEMMCCORE_LP_EN |
				//BIT_SLP_LDOGEN0_LP_EN |
				BIT_SLP_LDODCXO_LP_EN |
				//BIT_SLP_LDOGEN1_LP_EN |
				//BIT_SLP_LDOWIFIPA_LP_EN |
				BIT_SLP_LDOVDD28_LP_EN |
				//BIT_SLP_LDOVDD18_LP_EN | /*sc2723 have no lp mode*/
				0
			   );
		ANA_REG_OR(ANA_REG_GLB_32KLESS_CTRL0,
				BIT_SLP_XO_LOW_CUR_EN
				);
	}

	ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL1,
		BIT_SLP_LDO_PD_EN |
		BIT_SLP_LDOLPREF_PD_EN |
		//BIT_SLP_LDOSDCORE_PD_EN | /*control by sdio drv not ap sleep*/
		BIT_SLP_LDOUSB_PD_EN | /*control by usb drv and ap sleep*/
		BIT_SLP_LDOCAMMOT_PD_EN | /*control by CAM drv and ap sleep*/
		BIT_SLP_LDOCAMIO_PD_EN | /*control by CAM drv and ap sleep*/
		BIT_SLP_LDOCAMD_PD_EN | /*control by CAM drv and ap sleep*/
		BIT_SLP_LDOCAMA_PD_EN | /*control by CAM drv and ap sleep*/
		BIT_SLP_LDOSIM2_PD_EN |
		//BIT_SLP_LDOSIM1_PD_EN |
		//BIT_SLP_LDOSIM0_PD_EN |
		//BIT_SLP_LDOSDIO_PD_EN | /*control by sdio drv not ap sleep*/
		0
	);
	ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL3,
		//BIT_SLP_BG_LP_EN |
		//BIT_LDOVDD25_LP_EN_SW |
		//BIT_LDOSDCORE_LP_EN_SW |
		//BIT_LDOUSB_LP_EN_SW |
		//BIT_SLP_LDOVDD25_LP_EN |
		//BIT_SLP_LDOSDCORE_LP_EN |
		//BIT_SLP_LDOUSB_LP_EN |
		//BIT_SLP_LDOCAMMOT_LP_EN |
		//BIT_SLP_LDOCAMIO_LP_EN |
		//BIT_SLP_LDOCAMD_LP_EN |
		//BIT_SLP_LDOCAMA_LP_EN |
		//BIT_SLP_LDOSIM2_LP_EN |
		//BIT_SLP_LDOSIM1_LP_EN |
		//BIT_SLP_LDOSIM0_LP_EN |
		//BIT_SLP_LDOSDIO_LP_EN |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_PWR_SLP_CTRL4,
		//BIT_LDOCAMIO_LP_EN_SW |
		//BIT_LDOCAMMOT_LP_EN_SW |
		//BIT_LDOCAMD_LP_EN_SW |
		//BIT_LDOCAMA_LP_EN_SW |
		//BIT_LDOSIM2_LP_EN_SW |
		//BIT_LDOSIM1_LP_EN_SW |
		//BIT_LDOSIM0_LP_EN_SW |
		//BIT_LDOSDIO_LP_EN_SW |
		//BIT_LDORF0_LP_EN_SW |
		//BIT_LDOEMMCCORE_LP_EN_SW |
		//BIT_LDOGEN0_LP_EN_SW |
		//BIT_LDODCXO_LP_EN_SW |
		//BIT_LDOGEN1_LP_EN_SW |
		//BIT_LDOWIFIPA_LP_EN_SW |
		//BIT_LDOVDD28_LP_EN_SW |
		//BIT_LDOVDD18_LP_EN_SW |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_XTL_WAIT_CTRL,
		BIT_SLP_XTLBUF_PD_EN |
		BIT_XTL_EN |
		BITS_XTL_WAIT(0x32) |
		0
	);

	/****************************************
	*   Following is CP LDO Sleep Control  *
	****************************************/
	ANA_REG_SET(ANA_REG_GLB_PWR_XTL_EN0,
		BIT_LDO_XTL_EN |
		//BIT_LDO_GEN0_EXT_XTL0_EN |
		//BIT_LDO_GEN0_XTL1_EN |
		//BIT_LDO_GEN0_XTL0_EN |
		BIT_LDO_GEN1_EXT_XTL0_EN |
		BIT_LDO_GEN1_XTL1_EN |
		BIT_LDO_GEN1_XTL0_EN |
		BIT_LDO_DCXO_EXT_XTL0_EN |
		BIT_LDO_DCXO_XTL1_EN |
		BIT_LDO_DCXO_XTL0_EN |
		//BIT_LDO_VDD18_EXT_XTL0_EN |
		//BIT_LDO_VDD18_XTL1_EN |
		//BIT_LDO_VDD18_XTL0_EN |
		//BIT_LDO_VDD28_EXT_XTL0_EN |
		//BIT_LDO_VDD28_XTL1_EN |
		//BIT_LDO_VDD28_XTL0_EN |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_PWR_XTL_EN1,
		BIT_LDO_RF0_EXT_XTL0_EN |
		BIT_LDO_RF0_XTL1_EN |
		BIT_LDO_RF0_XTL0_EN |
		//BIT_LDO_WIFIPA_EXT_XTL0_EN |
		//BIT_LDO_WIFIPA_XTL1_EN |
		//BIT_LDO_WIFIPA_XTL0_EN |
		BIT_LDO_SIM2_EXT_XTL0_EN |
		//BIT_LDO_SIM2_XTL1_EN |
		BIT_LDO_SIM2_XTL0_EN |
		//BIT_LDO_SIM1_EXT_XTL0_EN |
		//BIT_LDO_SIM1_XTL1_EN |
		//BIT_LDO_SIM1_XTL0_EN |
		//BIT_LDO_SIM0_EXT_XTL0_EN |
		//BIT_LDO_SIM0_XTL1_EN |
		//BIT_LDO_SIM0_XTL0_EN |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_PWR_XTL_EN2,
		BIT_LDO_VDD25_EXT_XTL0_EN |
		BIT_LDO_VDD25_XTL1_EN |
		BIT_LDO_VDD25_XTL0_EN |
		//BIT_DCDC_RF_EXT_XTL0_EN |
		//BIT_DCDC_RF_XTL1_EN |
		//BIT_DCDC_RF_XTL0_EN |
		BIT_XO_EXT_XTL0_EN |
		BIT_XO_XTL1_EN |
		BIT_XO_XTL0_EN |
		BIT_BG_EXT_XTL0_EN |
		BIT_BG_XTL1_EN |
		BIT_BG_XTL0_EN |
		0
	);
	ANA_REG_SET(ANA_REG_GLB_PWR_XTL_EN3,
		//BIT_DCDC_CON_EXT_XTL0_EN |
		//BIT_DCDC_CON_XTL1_EN |
		//BIT_DCDC_CON_XTL0_EN |
		//BIT_DCDC_WPA_EXT_XTL0_EN |
		//BIT_DCDC_WPA_XTL1_EN |
		//BIT_DCDC_WPA_XTL0_EN |
		BIT_DCDC_MEM_EXT_XTL0_EN |
		BIT_DCDC_MEM_XTL1_EN |
		BIT_DCDC_MEM_XTL0_EN |
		BIT_DCDC_GEN_EXT_XTL0_EN |
		BIT_DCDC_GEN_XTL1_EN |
		BIT_DCDC_GEN_XTL0_EN |
		BIT_DCDC_CORE_EXT_XTL0_EN |
		BIT_DCDC_CORE_XTL1_EN |
		BIT_DCDC_CORE_XTL0_EN |
		0
	);

	/*add by sam.sun, vddsim2 value 2.8v, bit15~bit8:a0*/
	ANA_REG_SET(ANA_REG_GLB_LDO_V_CTRL5,
		BITS_LDO_SIM2_V(0xa0) |
		0
	);
#endif
	/************************************************
	*   Following is AP/CP LDO D DIE Sleep Control   *
	*************************************************/

	CHIP_REG_SET(REG_PMU_APB_XTL0_REL_CFG,
		BIT_XTL0_AP_SEL |
		//BIT_XTL0_CP0_SEL |
		//BIT_XTL0_CP1_SEL |
		BIT_XTL0_CP2_SEL |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_XTL1_REL_CFG,
		//BIT_XTL1_AP_SEL |
		BIT_XTL1_CP0_SEL |
		//BIT_XTL1_CP1_SEL |
		//BIT_XTL1_CP2_SEL |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_XTL2_REL_CFG,
		//BIT_XTL2_AP_SEL |
		//BIT_XTL2_CP0_SEL |
		//BIT_XTL2_CP1_SEL |
		//BIT_XTL2_CP2_SEL |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_XTLBUF0_REL_CFG,
		BIT_XTLBUF0_CP2_SEL |
		BIT_XTLBUF0_CP1_SEL |
		BIT_XTLBUF0_CP0_SEL |
		BIT_XTLBUF0_AP_SEL  |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_XTLBUF1_REL_CFG,
		BIT_XTLBUF1_CP2_SEL |
		BIT_XTLBUF1_CP1_SEL |
		BIT_XTLBUF1_CP0_SEL |
		BIT_XTLBUF1_AP_SEL  |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_MPLL_REL_CFG,
		//BIT_MPLL_REF_SEL |
		//BIT_MPLL_CP2_SEL |
		//BIT_MPLL_CP1_SEL |
		//BIT_MPLL_CP0_SEL |
		BIT_MPLL_AP_SEL  |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_DPLL_REL_CFG,
		//BIT_DPLL_REF_SEL |
		BIT_DPLL_CP2_SEL |
		BIT_DPLL_CP1_SEL |
		BIT_DPLL_CP0_SEL |
		BIT_DPLL_AP_SEL  |
		0
	);
	/*caution tdpll & wpll sel config in spl*/
	reg_val = CHIP_REG_GET(REG_PMU_APB_TDPLL_REL_CFG);
	reg_val &= ~0xF;
	reg_val |= (
		   BIT_TDPLL_CP2_SEL|
		   //BIT_TDPLL_CP1_SEL|
		   BIT_TDPLL_CP0_SEL|
		   BIT_TDPLL_AP_SEL |
		   0);
	CHIP_REG_SET(REG_PMU_APB_TDPLL_REL_CFG,reg_val);

	reg_val = CHIP_REG_GET(REG_PMU_APB_WPLL_REL_CFG);
	reg_val &= ~0xF;
	reg_val |= (
		   //BIT_WPLL_CP2_SEL|
		   //BIT_WPLL_CP1_SEL|
		   BIT_WPLL_CP0_SEL|
		   //BIT_WPLL_AP_SEL |
		   0);
	CHIP_REG_SET(REG_PMU_APB_WPLL_REL_CFG,reg_val);

	CHIP_REG_SET(REG_PMU_APB_CPLL_REL_CFG,
		//BIT_CPLL_REF_SEL |
		BIT_CPLL_CP2_SEL |
		//BIT_CPLL_CP1_SEL |
		//BIT_CPLL_CP0_SEL |
		//BIT_CPLL_AP_SEL  |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_WIFIPLL1_REL_CFG,
		//BIT_WIFIPLL1_REF_SEL |
		BIT_WIFIPLL1_CP2_SEL |
		//BIT_WIFIPLL1_CP1_SEL |
		//BIT_WIFIPLL1_CP0_SEL |
		//BIT_WIFIPLL1_AP_SEL |
		0
	);

	CHIP_REG_SET(REG_PMU_APB_WIFIPLL2_REL_CFG,
		//BIT_WIFIPLL2_REF_SEL |
		BIT_WIFIPLL2_CP2_SEL |
		//BIT_WIFIPLL2_CP1_SEL |
		//BIT_WIFIPLL2_CP0_SEL |
		//BIT_WIFIPLL2_AP_SEL |
		0
	);

	/*chip service package init*/
	CSP_Init(0);
        init_ldo_voltage();
#if defined(CONFIG_ADIE_SC2723S) || defined(CONFIG_ADIE_SC2723)
	init_adie_global();
#endif
}

static int init_adie_global(void)
{
    ANA_REG_OR(ANA_REG_GLB_SWRST_CTRL,
	       BIT_SW_RST_EMMCCORE_PD_EN|
	       BIT_SW_RST_GEN0_PD_EN|
	       0
	       );
    return 0;
}
static int init_ldo_voltage(void)
{
    regulator_set_voltage("vddgen",2100);
    return 0;
}

#ifdef CONFIG_DL_POWER_CONTROL
void dl_power_control(void)
{
	/*Turn off the WCN when it is downloading for pike*/
	ANA_REG_SET(ANA_REG_GLB_PWR_XTL_EN1,
			BIT_LDO_RF0_EXT_XTL0_EN |
			BIT_LDO_RF0_XTL1_EN |
			BIT_LDO_RF0_XTL0_EN |
			//BIT_LDO_WIFIPA_EXT_XTL0_EN |
			//BIT_LDO_WIFIPA_XTL1_EN |
			//BIT_LDO_WIFIPA_XTL0_EN |
			//BIT_LDO_SIM2_EXT_XTL0_EN |
			//BIT_LDO_SIM2_XTL1_EN |
			//BIT_LDO_SIM2_XTL0_EN |
			//BIT_LDO_SIM1_EXT_XTL0_EN |
			//BIT_LDO_SIM1_XTL1_EN |
			//BIT_LDO_SIM1_XTL0_EN |
			//BIT_LDO_SIM0_EXT_XTL0_EN |
			//BIT_LDO_SIM0_XTL1_EN |
			//BIT_LDO_SIM0_XTL0_EN |
			0
		   );
}
#endif
