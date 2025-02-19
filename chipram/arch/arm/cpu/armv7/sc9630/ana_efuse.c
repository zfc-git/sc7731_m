/*
 * Copyright (C) 2014 Spreadtrum Communications Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */

#include <common.h>
#include <linux/types.h>
#include <asm/io.h>
#include <asm/arch/sprd_reg.h>
#include <asm/arch/sys_timer_reg_v0.h>
#include <asm/arch/adi.h>
#include "asm/arch/sprd_regs_ana_efuse.h"

#define	ETIMEDOUT	110	/* Connection timed out */
typedef unsigned long int		uint32;

static uint32 SCI_GetTickCount(void)
{
	volatile uint32 tmp_tick1;
	volatile uint32 tmp_tick2;

	tmp_tick1 = SYSTEM_CURRENT_CLOCK;
	tmp_tick2 = SYSTEM_CURRENT_CLOCK;

	while (tmp_tick1 != tmp_tick2)
	{
		tmp_tick1 = tmp_tick2;
		tmp_tick2 = SYSTEM_CURRENT_CLOCK;
	}

	return tmp_tick1;
}

#define jiffies (SCI_GetTickCount())	/* return msec count */
#define msecs_to_jiffies(a) (a)
#define time_after(a,b)	((int)(b) - (int)(a) < 0)
#define cpu_relax()

#define SPRD_ADISLAVE_BASE (SPRD_ADI_PHYS + 0x8000)

#define ANA_REGS_EFUSE_BASE             ( SPRD_ADISLAVE_BASE + 0x200 )

#define EFUSE_BLOCK_MAX                 ( 32 )
#define EFUSE_BLOCK_WIDTH               ( 8 )	/* bit counts */


static void adie_efuse_lock(void)
{
}

static void adie_efuse_unlock(void)
{
}

static void __adie_efuse_power_on(void)
{
	sci_adi_set(ANA_REG_GLB_ARM_MODULE_EN, BIT_ANA_EFS_EN);
	/* FIXME: rtc_efs only for prog
	   sci_adi_set(ANA_REG_GLB_RTC_CLK_EN, BIT_RTC_EFS_EN);
	 */

	/* FIXME: sclk always on or not ? */
	/* adie_efuse_workaround(); */
}

static void __adie_efuse_power_off(void)
{
	/* FIXME: rtc_efs only for prog
	   sci_adi_clr(ANA_REG_GLB_RTC_CLK_EN, BIT_RTC_EFS_EN);
	 */
	sci_adi_clr(ANA_REG_GLB_ARM_MODULE_EN, BIT_ANA_EFS_EN);
}

static __inline int __adie_efuse_wait_clear(u32 bits)
{
	int ret = 0;
	unsigned long timeout;

	/* wait for maximum of 3000 msec */
	timeout = jiffies + msecs_to_jiffies(3000);
	while (sci_adi_read(ANA_REG_EFUSE_STATUS) & bits) {
		if (time_after(jiffies, timeout)) {
			ret = -ETIMEDOUT;
			break;
		}
		cpu_relax();
	}
	return ret;
}

u32 adie_efuse_read(int blk_index)
{
	u32 val = 0;

	adie_efuse_lock();
	__adie_efuse_power_on();
	/* enable adie_efuse module clk and power before */

	/* adie_efuse_lookat(); */

	/* FIXME: set read timing, why 0x20 (default value)
	   sci_adi_raw_write(ANA_REG_EFUSE_RD_TIMING_CTRL,
	   BITS_EFUSE_RD_TIMING(0x20));
	 */

	sci_adi_raw_write(ANA_REG_EFUSE_BLOCK_INDEX,
			  BITS_READ_WRITE_INDEX(blk_index));
	sci_adi_raw_write(ANA_REG_EFUSE_MODE_CTRL, BIT_RD_START);

	if (__adie_efuse_wait_clear(BIT_READ_BUSY))
		goto out;

	val = sci_adi_read(ANA_REG_EFUSE_DATA_RD);

	/* FIXME: reverse the otp value */
	val = BITS_EFUSE_DATA_RD(~val);

out:
	__adie_efuse_power_off();
	adie_efuse_unlock();
	return val;
}

