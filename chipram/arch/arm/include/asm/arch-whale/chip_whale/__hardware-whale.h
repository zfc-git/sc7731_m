/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#ifndef __ASM_ARCH_HARDWARE_SC9836A_H
#define __ASM_ARCH_HARDWARE_SC9836A_H

#ifndef __ASM_ARCH_SCI_HARDWARE_H
#error  "Don't include this file directly, include <mach/hardware.h>"
#endif

#define LL_DEBUG_UART_PHYS		SPRD_UART1_PHYS
#define LL_DEBUG_UART_BASE		SPRD_UART1_BASE

#define SPRD_CORESIGHT_BASE		SPRD_CORESIGHT_PHYS
#define SPRD_CORESIGHT_PHYS		0x10000000
#define SPRD_CORESIGHT_SIZE		SZ_64K
/*
#define SPRD_CORE_BASE			SPRD_CORE_PHYS
#define SPRD_CORE_PHYS			0x12000000
#define SPRD_CORE_SIZE			SZ_64K
*/

#define SPRD_APCLK_BASE			SPRD_APCLK_PHYS
#define SPRD_APCLK_PHYS			0X20000000
#define SPRD_APCLK_SIZE			SZ_4K
#define CTL_BASE_AP_CKG                 SPRD_APCLK_PHYS

#define SPRD_DMA_BASE			SPRD_DMA_PHYS
#define SPRD_DMA_PHYS			0X20100000
#define SPRD_DMA_SIZE			SZ_16K


#define SPRD_AHB_BASE			SPRD_AHB_PHYS
#define SPRD_AHB_PHYS			0X20210000
#define SPRD_AHB_SIZE			SZ_64K



#define SPRD_USB_OTG_BASE		SPRD_USB_OTG_PHYS
#define SPRD_USB_OTG_PHYS		0X20300000
#define SPRD_USB_OTG_SIZE		SZ_4K

#define SPRD_USB_HSIC_BASE		SPRD_USB_HSIC_PHYS
#define SPRD_USB_HSIC_PHYS		0X20400000
#define SPRD_USB_HSIC_SIZE		SZ_4K

#define SPRD_USB_BASE			SPRD_USB_PHYS
#define SPRD_USB_PHYS			0X20500000
#define SPRD_USB_SIZE			SZ_4K
#define USB_REG_BASE			0x20500000
#define CTL_BASE_USB3			0x20500000

#define SPRD_SDIO0_BASE			SPRD_SDIO0_PHYS
#define SPRD_SDIO0_PHYS			0X20800000
#define SPRD_SDIO0_SIZE			SZ_4K

#define SPRD_SDIO1_BASE			SPRD_SDIO1_PHYS
#define SPRD_SDIO1_PHYS			0X20810000
#define SPRD_SDIO1_SIZE			SZ_4K

#define SPRD_SDIO2_BASE			SPRD_SDIO2_PHYS
#define SPRD_SDIO2_PHYS			0X20820000
#define SPRD_SDIO2_SIZE			SZ_4K

#define SPRD_EMMC_BASE			SPRD_EMMC_PHYS
#define SPRD_EMMC_PHYS			0X20830000
#define SPRD_EMMC_SIZE			SZ_4K

#define SPRD_PUB0_BASE			SPRD_PUB0_PHYS
#define SPRD_PUB0_PHYS			0X30000000
#define SPRD_PUB0_SIZE			SZ_64K

#define SPRD_PUB0_GLB_BASE		SPRD_PUB0_GLB_PHYS
#define SPRD_PUB0_GLB_PHYS		0X30010000
#define SPRD_PUB0_GLB_SIZE		SZ_64K

#define SPRD_PUB0_AXIBM0_BASE		SPRD_PUB0_AXIBM0_PHYS
#define SPRD_PUB0_AXIBM0_PHYS		0X30020000
#define SPRD_PUB0_AXIBM0_SIZE		SZ_4K

#define SPRD_PUB0_AXIBM1_BASE		SPRD_PUB0_AXIBM1_PHYS
#define SPRD_PUB0_AXIBM1_PHYS		0X30030000
#define SPRD_PUB0_AXIBM1_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM2_BASE		SPRD_PUB0_AXIBM2_PHYS
#define SPRD_PUB0_AXIBM2_PHYS		0X30040000
#define SPRD_PUB0_AXIBM2_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM3_BASE		SPRD_PUB0_AXIBM3_PHYS
#define SPRD_PUB0_AXIBM3_PHYS		0X30050000
#define SPRD_PUB0_AXIBM3_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM4_BASE		SPRD_PUB0_AXIBM4_PHYS
#define SPRD_PUB0_AXIBM4_PHYS		0X30060000
#define SPRD_PUB0_AXIBM4_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM5_BASE		SPRD_PUB0_AXIBM5_PHYS
#define SPRD_PUB0_AXIBM5_PHYS		0X30070000
#define SPRD_PUB0_AXIBM5_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM6_BASE		SPRD_PUB0_AXIBM6_PHYS
#define SPRD_PUB0_AXIBM6_PHYS		0X30080000
#define SPRD_PUB0_AXIBM6_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM7_BASE		SPRD_PUB0_AXIBM7_PHYS
#define SPRD_PUB0_AXIBM7_PHYS		0X30090000
#define SPRD_PUB0_AXIBM7_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM8_BASE		SPRD_PUB0_AXIBM8_PHYS
#define SPRD_PUB0_AXIBM8_PHYS		0X300a0000
#define SPRD_PUB0_AXIBM8_SIZE		(SZ_4K)

#define SPRD_PUB0_AXIBM9_BASE		SPRD_PUB0_AXIBM9_PHYS
#define SPRD_PUB0_AXIBM9_PHYS		0X300b0000
#define SPRD_PUB0_AXIBM9_SIZE		(SZ_4K)
#define SPRD_PUB1_BASE			SPRD_PUB1_PHYS
#define SPRD_PUB1_PHYS			0X30800000
#define SPRD_PUB1_SIZE			SZ_64K

#define SPRD_PUB1_GLB_BASE		SPRD_PUB1_GLB_PHYS
#define SPRD_PUB1_GLB_PHYS		0X30810000
#define SPRD_PUB1_GLB_SIZE		SZ_64K

#define SPRD_PUB1_AXIBM0_BASE		SPRD_PUB1_AXIBM0_PHYS
#define SPRD_PUB1_AXIBM0_PHYS		0X30820000
#define SPRD_PUB1_AXIBM0_SIZE		SZ_4K

#define SPRD_PUB1_AXIBM1_BASE		SPRD_PUB1_AXIBM1_PHYS
#define SPRD_PUB1_AXIBM1_PHYS		0X30830000
#define SPRD_PUB1_AXIBM1_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM2_BASE		SPRD_PUB1_AXIBM2_PHYS
#define SPRD_PUB1_AXIBM2_PHYS		0X30840000
#define SPRD_PUB1_AXIBM2_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM3_BASE		SPRD_PUB1_AXIBM3_PHYS
#define SPRD_PUB1_AXIBM3_PHYS		0X30850000
#define SPRD_PUB1_AXIBM3_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM4_BASE		SPRD_PUB1_AXIBM4_PHYS
#define SPRD_PUB1_AXIBM4_PHYS		0X30860000
#define SPRD_PUB1_AXIBM4_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM5_BASE		SPRD_PUB1_AXIBM5_PHYS
#define SPRD_PUB1_AXIBM5_PHYS		0X30870000
#define SPRD_PUB1_AXIBM5_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM6_BASE		SPRD_PUB1_AXIBM6_PHYS
#define SPRD_PUB1_AXIBM6_PHYS		0X30880000
#define SPRD_PUB1_AXIBM6_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM7_BASE		SPRD_PUB1_AXIBM7_PHYS
#define SPRD_PUB1_AXIBM7_PHYS		0X30890000
#define SPRD_PUB1_AXIBM7_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM8_BASE		SPRD_PUB1_AXIBM8_PHYS
#define SPRD_PUB1_AXIBM8_PHYS		0X308A0000
#define SPRD_PUB1_AXIBM8_SIZE		(SZ_4K)

#define SPRD_PUB1_AXIBM9_BASE		SPRD_PUB1_AXIBM9_PHYS
#define SPRD_PUB1_AXIBM9_PHYS		0X308b0000
#define SPRD_PUB1_AXIBM9_SIZE		(SZ_4K)
#define SPRD_ADI_BASE			SPRD_ADI_PHYS
#define SPRD_ADI_PHYS			0X40030000
#define SPRD_ADI_SIZE			(SZ_4K)

#define SPRD_ADISLAVE_BASE		SPRD_ADISLAVE_PHYS
#define SPRD_ADISLAVE_PHYS		0X40038000
#define SPRD_ADISLAVE_SIZE		(SZ_4K)

#define SPRD_AON_SYSTIMER_BASE		SPRD_AON_SYSTIMER_PHYS
#define SPRD_AON_SYSTIMER_PHYS		0X40040000
#define SPRD_AON_SYSTIMER_SIZE		SZ_4K

#define SPRD_AON_TIMER_BASE		SPRD_AON_TIMER_PHYS
#define SPRD_AON_TIMER_PHYS		0X40050000
#define SPRD_AON_TIMER_SIZE		SZ_4K

#define SPRD_MDAR_BASE			SPRD_MDAR_PHYS
#define SPRD_MDAR_PHYS			0X40070000
#define SPRD_MDAR_SIZE			SZ_4K

#define SPRD_I2C_BASE			SPRD_I2C_PHYS
#define SPRD_I2C_PHYS			0X40080000
#define SPRD_I2C_SIZE			SZ_4K

#define SPRD_LVDS_BASE			SPRD_LVDS_PHYS
#define SPRD_LVDS_PHYS			0X40090000
#define SPRD_LVDS_SIZE			SZ_4K

#define SPRD_MBOX_BASE			SPRD_MBOX_PHYS
#define SPRD_MBOX_PHYS			0X400a0000
#define SPRD_MBOX_SIZE			SZ_4K

#define SPRD_DEF_SLAVE_BASE		SPRD_DEF_SLAVE_PHYS
#define SPRD_DEF_SLAVE_PHYS		0X400F0000
#define SPRD_DEF_SLAVE_SIZE		SZ_4K


#define SPRD_EIC_BASE			SPRD_EIC_PHYS
#define SPRD_EIC_PHYS			0X40210000
#define SPRD_EIC_SIZE			SZ_4K

#define SPRD_APTIMER0_BASE		SPRD_APTIMER0_PHYS
#define SPRD_APTIMER0_PHYS		0X40220000
#define SPRD_APTIMER0_SIZE		SZ_4K

#define SPRD_SYSCNT_BASE		SPRD_AP_SYSTIMER_PHYS
#define SPRD_SYSCNT_PHYS		0X40230000
#define SPRD_SYSCNT_SIZE		SZ_4K

#define SPRD_UIDEFUSE_BASE		SPRD_UIDEFUSE_PHYS
#define SPRD_UIDEFUSE_PHYS		0X40240000
#define SPRD_UIDEFUSE_SIZE		SZ_4K

#define SPRD_KPD_BASE			SPRD_KPD_PHYS
#define SPRD_KPD_PHYS			0X40250000
#define SPRD_KPD_SIZE			SZ_4K

#define SPRD_PWM_BASE			SPRD_PWM_PHYS
#define SPRD_PWM_PHYS			0X40260000
#define SPRD_PWM_SIZE			SZ_4K

#define SPRD_GPIO_BASE			SPRD_GPIO_PHYS
#define SPRD_GPIO_PHYS			0X40280000
#define SPRD_GPIO_SIZE			SZ_4K

#define SPRD_APWDG_BASE			SPRD_APWDG_PHYS
#define SPRD_APWDG_PHYS			0X40290000
#define SPRD_APWDG_SIZE			SZ_4K

#define SPRD_PIN_BASE			SPRD_PIN_PHYS
#define SPRD_PIN_PHYS			0X402A0000
#define SPRD_PIN_SIZE			SZ_4K

#define SPRD_PMU_BASE			SPRD_PMU_PHYS
#define SPRD_PMU_PHYS			0X402B0000
#define SPRD_PMU_SIZE			SZ_64K

#define SPRD_AONCKG_BASE		SPRD_AONCKG_PHYS
#define SPRD_AONCKG_PHYS		0X402D0000
#define SPRD_AONCKG_SIZE		SZ_4K

#define AON_EMMC_CLK_2X_CFG        0X344
#define AON_SDIO0_CLK_2X_CFG       0X328
#define AON_CLK_FREQ_1M                 0x0
#define AON_CLK_FREQ_26M               0x1
#define AON_CLK_FREQ_384M             0X4
#define AON_CLK_FREQ_200K_DIV      0X400
#define AON_CLK_FREQ_24M_DIV       0X700

#define SPRD_AONAPB_BASE		SPRD_AONAPB_PHYS
#define SPRD_AONAPB_PHYS		0X402E0000
#define SPRD_AONAPB_SIZE		SZ_64K

#define SPRD_THM_BASE			SPRD_THM_PHYS
#define SPRD_THM_PHYS			0X402F0000
#define SPRD_THM_SIZE			SZ_4K

#define SPRD_AVSCA53_BASE		SPRD_AVSCA53_PHYS
#define SPRD_AVSCA53_PHYS		0X40300000
#define SPRD_AVSCA53_SIZE		SZ_4K

#define SPRD_CA53WDG_BASE		SPRD_CA53WDG_PHYS
#define SPRD_CA53WDG_PHYS		0X40310000
#define SPRD_CA53WDG_SIZE		SZ_4K

#define SPRD_APTIMER1_BASE		SPRD_APTIMER1_PHYS
#define SPRD_APTIMER1_PHYS		0X40320000
#define SPRD_APTIMER1_SIZE		SZ_4K

#define SPRD_APTIMER2_BASE		SPRD_APTIMER2_PHYS
#define SPRD_APTIMER2_PHYS		0X40330000
#define SPRD_APTIMER2_SIZE		SZ_4K

#define SPRD_DEBUG_BASE			SPRD_DEBUG_PHYS
#define SPRD_DEBUG_PHYS			0X40340000
#define SPRD_DEBUG_SIZE			SZ_4K

#define SPRD_APINTC0_BASE		SPRD_APINTC0_PHYS
#define SPRD_APINTC0_PHYS		0X40350000
#define SPRD_APINTC0_SIZE		SZ_4K

#define SPRD_APINTC1_BASE		SPRD_APINTC1_PHYS
#define SPRD_APINTC1_PHYS		0X40360000
#define SPRD_APINTC1_SIZE		SZ_4K

#define SPRD_APINTC2_BASE		SPRD_APINTC2_PHYS
#define SPRD_APINTC2_PHYS		0X40370000
#define SPRD_APINTC2_SIZE		SZ_4K

#define SPRD_APINTC3_BASE		SPRD_APINTC3_PHYS
#define SPRD_APINTC3_PHYS		0X40380000
#define SPRD_APINTC3_SIZE		SZ_4K

#define SPRD_APINTC4_BASE		SPRD_APINTC4_PHYS
#define SPRD_APINTC4_PHYS		0X40390000
#define SPRD_APINTC4_SIZE		SZ_4K

#define SPRD_APINTC5_BASE		SPRD_APINTC5_PHYS
#define SPRD_APINTC5_PHYS		0X403A0000
#define SPRD_APINTC5_SIZE		SZ_4K

#define SPRD_ANA_MISC_BASE		SPRD_ANA_MISC_PHYS
#define SPRD_ANA_MISC_PHYS		0X40400000
#define SPRD_ANA_MISC_SIZE		SZ_4K


#define SPRD_MIPI_CSI0_BASE		SPRD_MIPI_CSI0_PHYS
#define SPRD_MIPI_CSI0_PHYS		0X40410000
#define SPRD_MIPI_CSI0_SIZE		SZ_4K

#define SPRD_MIPI_CSI1_BASE		SPRD_MIPI_CSI1_PHYS
#define SPRD_MIPI_CSI1_PHYS		0X40420000
#define SPRD_MIPI_CSI1_SIZE		SZ_4K

#define SPRD_MIPI_DSI0_BASE		SPRD_MIPI_DSI0_PHYS
#define SPRD_MIPI_DSI0_PHYS		0X40430000
#define SPRD_MIPI_DSI0_SIZE		SZ_4K

#define SPRD_MIPI_DSI1_BASE		SPRD_MIPI_DSI1_PHYS
#define SPRD_MIPI_DSI1_PHYS		0X40440000
#define SPRD_MIPI_DSI1_SIZE		SZ_4K

#define SPRD_SPINLOCK_BASE		SPRD_SPINLOCK_PHYS
#define SPRD_SPINLOCK_PHYS		0X40500000
#define SPRD_SPINLOCK_SIZE		SZ_4K


#define SPRD_CA53TS1_BASE		SPRD_CA53TS1_PHYS
#define SPRD_CA53TS1_PHYS		0X40610000
#define SPRD_CA53TS1_SIZE		SZ_4K

#define SPRD_SECURITY_BASE		SPRD_SECURITY_PHYS
#define SPRD_SECURITY_PHYS		0X40800000
#define SPRD_SECURITY_SIZE		SZ_4K

#define SPRD_AGCP_BASE			SPRD_AGCP_PHYS
#define SPRD_AGCP_PHYS			0X41000000
#define SPRD_AGCP_SIZE			SZ_4K

#define SPRD_CC63S_BASE			SPRD_CC63S_PHYS
#define SPRD_CC63S_PHYS			0X50000000
#define SPRD_CC63S_SIZE			SZ_4K

#define SPRD_CC63P_BASE			SPRD_CC63P_PHYS
#define SPRD_CC63P_PHYS			0X50100000
#define SPRD_CC63P_SIZE			SZ_4K

#define SPRD_GPUREG_BASE		SPRD_GPUREG_PHYS
#define SPRD_GPUREG_PHYS		0X60000000
#define SPRD_GPUREG_SIZE		SZ_4K

#define SPRD_GPUAPB_BASE		SPRD_GPUAPB_PHYS
#define SPRD_GPUAPB_PHYS		0X60100000
#define SPRD_GPUAPB_SIZE		SZ_4K

#define SPRD_MALI_BASE			SPRD_MALI_PHYS
#define SPRD_MALI_PHYS			0X60200000
#define SPRD_MALI_SIZE			SZ_4K

#define SPRD_VSPCKG_BASE		SPRD_VSPCKG_PHYS
#define SPRD_VSPCKG_PHYS		0X61000000
#define SPRD_VSPCKG_SIZE		(SZ_32K + SZ_16K)

#define SPRD_VSPAHB_BASE		SPRD_VSPAHB_PHYS
#define SPRD_VSPAHB_PHYS		0X61100000
#define SPRD_VSPAHB_SIZE		(SZ_32K + SZ_16K)

#define SPRD_VSP_BASE			SPRD_VSP_PHYS
#define SPRD_VSP_PHYS			0X61200000
#define SPRD_VSP_SIZE			(SZ_32K + SZ_16K)

#define SPRD_VSPMMU_BASE		SPRD_VSPMMU_PHYS
#define SPRD_VSPMMU_PHYS		0X61300000
#define SPRD_VSPMMU_SIZE		(SZ_32K + SZ_16K)

#define SPRD_CAMCKG_BASE		SPRD_CAMCKG_PHYS
#define SPRD_CAMCKG_PHYS		0X62000000
#define SPRD_CAMCKG_SIZE		SZ_64K

#define SPRD_CAMAHB_BASE		SPRD_CAMAHB_PHYS
#define SPRD_CAMAHB_PHYS		0X62100000
#define SPRD_CAMAHB_SIZE		SZ_64K

#define SPRD_DCAM0_BASE			SPRD_DCAM0_PHYS
#define SPRD_DCAM0_PHYS			0X62200000
#define SPRD_DCAM0_SIZE			SZ_64K

#define SPRD_DCAM1_BASE			SPRD_DCAM1_PHYS	
#define SPRD_DCAM1_PHYS			0X62300000
#define SPRD_DCAM1_SIZE			SZ_64K

#define SPRD_ISP_BASE			SPRD_ISP_PHYS
#define SPRD_ISP_PHYS			0X62400000
#define SPRD_ISP_SIZE			SZ_32K

#define SPRD_JPG0_BASE			SPRD_JPG0_PHYS
#define SPRD_JPG0_PHYS			0X62500000
#define SPRD_JPG0_SIZE			SZ_32K

#define SPRD_JPG1_BASE			SPRD_JPG1_PHYS
#define SPRD_JPG1_PHYS			0X62600000
#define SPRD_JPG1_SIZE			SZ_32K

#define SPRD_CSI0_BASE			SPRD_CSI0_PHYS
#define SPRD_CSI0_PHYS			0X62700000
#define SPRD_CSI0_SIZE			SZ_4K

#define SPRD_CSI1_BASE			SPRD_CSI1_PHYS
#define SPRD_CSI1_PHYS			0X62800000
#define SPRD_CSI1_SIZE			SZ_4K

#define SPRD_CAMMMU_BASE		SPRD_CAMMMU_PHYS
#define SPRD_CAMMMU_PHYS		0X62900000
#define SPRD_CAMMMU_SIZE		SZ_4K

#define SPRD_DISPCKG_BASE		SPRD_DISPCKG_PHYS
#define SPRD_DISPCKG_PHYS		0X63000000
#define SPRD_DISPCKG_SIZE		SZ_4K

#define SPRD_DISPAHB_BASE		SPRD_DISPAHB_PHYS
#define SPRD_DISPAHB_PHYS		0X63100000
#define SPRD_DISPAHB_SIZE		SZ_4K

#define SPRD_DISPC0_BASE		SPRD_DISPC0_PHYS
#define SPRD_DISPC0_PHYS		0X63200000
#define SPRD_DISPC0_SIZE		SZ_4K

#define SPRD_DISPC1_BASE		SPRD_DISPC1_PHYS
#define SPRD_DISPC1_PHYS		0X63300000
#define SPRD_DISPC1_SIZE		SZ_4K

#define SPRD_DISPCMMU_BASE		SPRD_DISPCMMU_PHYS
#define SPRD_DISPCMMU_PHYS		0X63400000
#define SPRD_DISPCMMU_SIZE		SZ_4K

#define SPRD_GSP0_BASE			SPRD_GSP0_PHYS
#define SPRD_GSP0_PHYS			0X63500000
#define SPRD_GSP0_SIZE			SZ_4K

#define SPRD_GSP1_BASE			SPRD_GSP1_PHYS
#define SPRD_GSP1_PHYS			0X63600000
#define SPRD_GSP1_SIZE			SZ_4K

#define SPRD_GSP0MMU_BASE		SPRD_GSP0MMU_PHYS
#define SPRD_GSP0MMU_PHYS		0X63700000
#define SPRD_GSP0MMU_SIZE		SZ_64K

#define SPRD_GSP1MMU_BASE		SPRD_GSP1MMU_PHYS
#define SPRD_GSP1MMU_PHYS		0X63800000
#define SPRD_GSP1MMU_SIZE		SZ_64K

#define SPRD_DSI0_BASE			SPRD_DSI0_PHYS
#define SPRD_DSI0_PHYS			0X63900000
#define SPRD_DSI0_SIZE			SZ_4K

#define SPRD_DSI1_BASE			SPRD_DSI1_PHYS
#define SPRD_DSI1_PHYS			0X63A00000
#define SPRD_DSI1_SIZE			SZ_4K

#define SPRD_VPPMMU_BASE		SPRD_VPPMMU_PHYS
#define SPRD_VPPMMU_PHYS		0X63B00000
#define SPRD_VPPMMU_SIZE		SZ_4K

#define SPRD_VPP_BASE			SPRD_VPP_PHYS
#define SPRD_VPP_PHYS			0X63C00000
#define SPRD_VPP_SIZE			SZ_4K

#define SPRD_UART0_BASE			SPRD_UART0_PHYS
#define SPRD_UART0_PHYS			0X70000000
#define SPRD_UART0_SIZE			SZ_4K

#define SPRD_UART1_BASE			SPRD_UART1_PHYS
#define SPRD_UART1_PHYS			0X70100000
#define SPRD_UART1_SIZE			SZ_4K

#define SPRD_UART2_BASE			SPRD_UART2_PHYS
#define SPRD_UART2_PHYS			0X70200000
#define SPRD_UART2_SIZE			SZ_4K

#define SPRD_UART3_BASE			SPRD_UART3_PHYS
#define SPRD_UART3_PHYS			0X70300000
#define SPRD_UART3_SIZE			SZ_4K

#define SPRD_UART4_BASE			SPRD_UART4_PHYS
#define SPRD_UART4_PHYS			0X70400000
#define SPRD_UART4_SIZE			SZ_4K

#define ARM_UART0_BASE       		SPRD_UART0_BASE
#define ARM_UART1_BASE       		SPRD_UART1_BASE
#define ARM_UART2_BASE       		SPRD_UART2_BASE
#define ARM_UART3_BASE       		SPRD_UART3_BASE
#define ARM_UART4_BASE       		SPRD_UART4_BASE

#define SPRD_IIS0_BASE			SPRD_IIS0_PHYS
#define SPRD_IIS0_PHYS			0X70600000
#define SPRD_IIS0_SIZE			SZ_4K

#define SPRD_IIS1_BASE			SPRD_IIS1_PHYS
#define SPRD_IIS1_PHYS			0X70700000
#define SPRD_IIS1_SIZE			SZ_4K

#define SPRD_IIS2_BASE			SPRD_IIS2_PHYS
#define SPRD_IIS2_PHYS			0X70800000
#define SPRD_IIS2_SIZE			SZ_4K

#define SPRD_IIS3_BASE			SPRD_IIS3_PHYS
#define SPRD_IIS3_PHYS			0X70900000
#define SPRD_IIS3_SIZE			SZ_4K

#define SPRD_APBREG_BASE		SPRD_APBREG_PHYS
#define SPRD_APBREG_PHYS		0X70B00000
#define SPRD_APBREG_SIZE		SZ_64K
#define CTL_BASE_APB			SPRD_APBREG_BASE

#define SPRD_I2C0_BASE			SPRD_I2C0_PHYS
#define SPRD_I2C0_PHYS			0X70D00000
#define SPRD_I2C0_SIZE			SZ_4K

#define SPRD_I2C1_BASE			SPRD_I2C1_PHYS
#define SPRD_I2C1_PHYS			0X70E00000
#define SPRD_I2C1_SIZE			SZ_4K

#define SPRD_I2C2_BASE			SPRD_I2C2_PHYS
#define SPRD_I2C2_PHYS			0X70F00000
#define SPRD_I2C2_SIZE			SZ_4K

#define SPRD_I2C3_BASE			SPRD_I2C3_PHYS
#define SPRD_I2C3_PHYS			0X71000000
#define SPRD_I2C3_SIZE			SZ_4K

#define SPRD_I2C4_BASE			SPRD_I2C4_PHYS
#define SPRD_I2C4_PHYS			0X71100000
#define SPRD_I2C4_SIZE			SZ_4K

#define SPRD_I2C5_BASE			SPRD_I2C5_PHYS
#define SPRD_I2C5_PHYS			0X71200000
#define SPRD_I2C5_SIZE			SZ_4K

#define SPRD_SPI0_BASE			SPRD_SPI0_PHYS
#define SPRD_SPI0_PHYS			0X71400000
#define SPRD_SPI0_SIZE			SZ_4K

#define SPRD_SPI1_BASE			SPRD_SPI1_PHYS
#define SPRD_SPI1_PHYS			0X71500000
#define SPRD_SPI1_SIZE			SZ_4K

#define SPRD_SPI2_BASE			SPRD_SPI2_PHYS
#define SPRD_SPI2_PHYS			0X71600000
#define SPRD_SPI2_SIZE			SZ_4K

#define SPRD_SIM0_BASE			SPRD_SIM0_PHYS
#define SPRD_SIM0_PHYS			0X71800000
#define SPRD_SIM0_SIZE			SZ_4K

/* should be check */
#define SPRD_IRAM0_BASE			SPRD_IRAM0_PHYS	
#define SPRD_IRAM0_PHYS			0X0
#define SPRD_IRAM0_SIZE			SZ_4K

#define SPRD_IRAM0H_BASE		SPRD_IRAM0H_PHYS
#define SPRD_IRAM0H_PHYS		0X1000
#define SPRD_IRAM0H_SIZE		SZ_4K

#define SPRD_IRAM1_BASE			SPRD_IRAM1_PHYS
#define SPRD_IRAM1_PHYS			0X50000000
#define SPRD_IRAM1_SIZE			(SZ_32K + SZ_8K + SZ_4K + SZ_2K)

#define SPRD_MEMNAND_SYSTEM_BASE	SPRD_MEMNAND_SYSTEM_PHYS
#define SPRD_MEMNAND_SYSTEM_PHYS	0x8c800000
#define SPRD_MEMNAND_SYSTEM_SIZE	(0xaa00000)

#define SPRD_MEMNAND_USERDATA_BASE	SPRD_MEMNAND_USERDATA_PHYS
#define SPRD_MEMNAND_USERDATA_PHYS	0X97200000
#define SPRD_MEMNAND_USERDATA_SIZE	(0x6a00000)

#define SPRD_MEMNAND_CACHE_BASE		SPRD_MEMNAND_CACHE_PHYS
#define SPRD_MEMNAND_CACHE_PHYS		(0X97200000+0x6a00000)
#define SPRD_MEMNAND_CACHE_SIZE		(0x2400000)

#define CORE_GIC_CPU_VA			(SPRD_CORE_BASE + 0x2000)
#define CORE_GIC_DIS_VA			(SPRD_CORE_BASE + 0x1000)

#define HOLDING_PEN_VADDR		(SPRD_AHB_BASE + 0x14)
#define CPU_JUMP_VADDR			(HOLDING_PEN_VADDR + 0X4)

/* registers for watchdog ,RTC, touch panel, aux adc, analog die... */
#define SPRD_MISC_BASE	((unsigned int)SPRD_ADI_BASE)
#define SPRD_MISC_PHYS	((unsigned int)SPRD_ADI_PHYS)

#define ANA_PWM_BASE		(SPRD_ADISLAVE_BASE + 0x20 )
#define ANA_TIMER_BASE		(SPRD_ADISLAVE_BASE + 0x40 )
#define ANA_FSCHG_BASE		(SPRD_ADISLAVE_BASE + 0x60 )
#define ANA_WDG_BASE		(SPRD_ADISLAVE_BASE + 0x80 )
#define ANA_CHGWDG_BASE		(SPRD_ADISLAVE_BASE + 0xC0 )
#define ANA_TYPEC_BASE		(SPRD_ADISLAVE_BASE + 0x100 )
#define ANA_INTC_BASE	(SPRD_ADISLAVE_BASE + 0x140 )
#define ANA_CAL_BASE	(SPRD_ADISLAVE_BASE + 0x180 )
#define ANA_AUDIFA_BASE	(SPRD_ADISLAVE_BASE + 0x1C0 )
#define ANA_BTLC_BASE	(SPRD_ADISLAVE_BASE + 0x200 )
#define ANA_FLASH_BASE	(SPRD_ADISLAVE_BASE + 0x240 )
#define ANA_RTC_BASE		(SPRD_ADISLAVE_BASE + 0x280 )
#define ANA_EIC_BASE		(SPRD_ADISLAVE_BASE + 0x300 )
#define ANA_EFS_BASE		(SPRD_ADISLAVE_BASE + 0x380 )
#define ANA_THM_BASE		(SPRD_ADISLAVE_BASE + 0x400 )
#define ANA_ADC_BASE		(SPRD_ADISLAVE_BASE + 0x480 )
#define ANA_PIN_BASE		(SPRD_ADISLAVE_BASE + 0x600 )
#define ANA_AUDCFGA_BASE	(SPRD_ADISLAVE_BASE + 0x700 )
#define ANA_AUDDIG_BASE	(SPRD_ADISLAVE_BASE + 0x800 )
#define ANA_BIF_BASE	(SPRD_ADISLAVE_BASE + 0x900 )
#define ANA_FGU_BASE	(SPRD_ADISLAVE_BASE + 0xA00 )
#define ANA_REGS_GLB_BASE	(SPRD_ADISLAVE_BASE + 0xC00)

#ifndef REGS_AHB_BASE
#define REGS_AHB_BASE		( SPRD_AHB_BASE  + 0x200)
#endif

#define SPRD_IRAM_BASE		SPRD_IRAM0_BASE + 0x1000
#define SPRD_IRAM_PHYS		SPRD_IRAM0_PHYS + 0x1000
#define SPRD_IRAM_SIZE		SZ_4K

#define SPRD_GREG_BASE		SPRD_AONAPB_BASE
#define SPRD_GREG_PHYS		SPRD_AONAPB_PHYS
#define SPRD_GREG_SIZE		SZ_64K

#ifndef REGS_GLB_BASE
#define REGS_GLB_BASE           ( SPRD_GREG_BASE )
#endif

#define CHIP_ID_LOW_REG		(SPRD_AHB_BASE + 0xfc)

#define SPRD_GPTIMER_BASE	SPRD_GPTIMER0_BASE
#define SPRD_EFUSE_BASE		SPRD_UIDEFUSE_BASE

#define SDIO0_BASE_ADDR         SPRD_SDIO0_BASE
#define SDIO1_BASE_ADDR         SPRD_SDIO1_BASE
#define SDIO2_BASE_ADDR         SPRD_SDIO2_BASE
#define EMMC_BASE_ADDR          SPRD_EMMC_BASE

#define REGS_ANA_APB_BASE	SPRD_ANA_MISC_BASE
#define REGS_AP_AHB_BASE	SPRD_AHB_BASE
#define REGS_AP_APB_BASE	SPRD_APBREG_BASE
#define REGS_AON_APB_BASE	SPRD_AONAPB_BASE
#define REGS_GPU_APB_BASE	SPRD_GPUAPB_BASE
#define REGS_MM_AHB_BASE	SPRD_MMAHB_BASE
#define REGS_PMU_APB_BASE	SPRD_PMU_BASE
#define REGS_AON_CLK_BASE	SPRD_AONCKG_BASE
#define REGS_AP_CLK_BASE	SPRD_APBCKG_BASE
#define REGS_GPU_CLK_BASE	SPRD_GPUCKG_BASE
#define REGS_MM_CLK_BASE	SPRD_MMCKG_BASE
#define REGS_PUB_APB_BASE	SPRD_PUB_BASE

#define SIPC_SMEM_ADDR 		(CONFIG_PHYS_OFFSET + 120 * SZ_1M)

#define CPT_START_ADDR		(CONFIG_PHYS_OFFSET + 128 * SZ_1M)
#define CPT_TOTAL_SIZE		(SZ_1M * 18)
#define CPT_RING_ADDR		(CPT_START_ADDR + CPT_TOTAL_SIZE - SZ_4K)
#define CPT_RING_SIZE		(SZ_4K)
#define CPT_SMEM_SIZE		(SZ_1M * 2)

#define CPW_START_ADDR		(CONFIG_PHYS_OFFSET + 150* SZ_1M)
#define CPW_TOTAL_SIZE		(SZ_1M * 33)
#define CPW_RING_ADDR		(CPW_START_ADDR + CPW_TOTAL_SIZE - SZ_4K)
#define CPW_RING_SIZE		(SZ_4K)
#define CPW_SMEM_SIZE		(SZ_1M * 2)

#define WCN_START_ADDR		(CONFIG_PHYS_OFFSET + 320 * SZ_1M)
#define WCN_TOTAL_SIZE		(SZ_1M * 5)
#define WCN_RING_ADDR		(WCN_START_ADDR + WCN_TOTAL_SIZE - SZ_4K)
#define WCN_RING_SIZE			(SZ_4K)
#define WCN_SMEM_SIZE		(SZ_1M * 2)

#define GGE_START_ADDR		(CONFIG_PHYS_OFFSET + 128 * SZ_1M)
#define GGE_TOTAL_SIZE		(SZ_1M * 22)
#define GGE_RING_ADDR		(GGE_START_ADDR + GGE_TOTAL_SIZE - SZ_4K)
#define GGE_RING_SIZE		(SZ_4K)
#define GGE_SMEM_SIZE		(SZ_1M * 2)

#define LTE_START_ADDR		(CONFIG_PHYS_OFFSET + 150 * SZ_1M)
#define LTE_TOTAL_SIZE		(SZ_1M * 106)
#define LTE_RING_ADDR		(LTE_START_ADDR + LTE_TOTAL_SIZE - SZ_4K)
#define LTE_RING_SIZE		(SZ_4K)
#define LTE_SMEM_SIZE		(SZ_1M * 2)

#endif
