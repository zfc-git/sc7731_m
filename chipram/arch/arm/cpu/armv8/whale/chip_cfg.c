/******************************************************************************
 ** File Name:      sp7100g_cfg.c                                             *
 ** Author:         Xueliang.Wang                                             *
 ** DATE:           08/23/2004                                                *
 ** Copyright:      2004 Spreatrum, Incoporated. All Rights Reserved.         *
 ** Description:    This file defines the config information of SP7100G       *
 **                 project.                                                  *
 **                                                                           *
 ******************************************************************************

 ******************************************************************************
 **                        Edit History                                       *
 ** ------------------------------------------------------------------------- *
 ** DATE            NAME            DESCRIPTION                               *
 ** 08/23/2004      Xueliang.Wang   Create.                                   *
 ******************************************************************************/

/**---------------------------------------------------------------------------*
 **                         Dependencies                                      *
 **---------------------------------------------------------------------------*/
#include "asm/arch/sci_types.h"
#include "asm/arch/arm_reg.h"
#include "asm/arch/sc_reg.h"
#include "asm/arch/sc_reg.h"
//#include "asm/arch/chng_freq.h"
//#include "asm/arch/emc_drv.h"
#include "asm/arch/chip.h"

/**---------------------------------------------------------------------------*
 **                         Compiler Flag                                     *
 **---------------------------------------------------------------------------*/
#ifdef   __cplusplus
extern   "C"
{
#endif

//-----------------------------------------------------------------------------
//
//  The initialized frequency when system start up.
//

//-----------------------------------------------------------------------------
//Global variable contain system clk config
const SYS_CLK_CFG_INFO g_system_clk_cfg =
{

#if  defined PLATFORM_SC6800H
    MPLL_MN_192M_EX26M,
    UPLL_MN_192M_EX26M,
    P192M_ARM192M_AHB96M
#elif defined PLATFORM_SC8800H
    MPLL_MN_164M_EX26M,
    0,
    P340M_ARM170M_AHB85M
#else
    0,
    0,
    0
#endif
};

/*****************************************************************************/
//  Description:    This function get system clk config pointer
//  Author:         Younger.yang
//  Note:
/*****************************************************************************/
SYS_CLK_CFG_INFO *Get_system_clk_cfg (void)
{

    return (SYS_CLK_CFG_INFO *) &g_system_clk_cfg;
}

uint32 FDL_GetBootMode (void)
{
    uint32 bootMode = 0;
    bootMode = (* (volatile uint32 *) (0x00006FF0) >> 8) & 0xff;
    return bootMode;
}
/**---------------------------------------------------------------------------*
 **                         Compiler Flag                                     *
 **---------------------------------------------------------------------------*/
#ifdef   __cplusplus
}
#endif
