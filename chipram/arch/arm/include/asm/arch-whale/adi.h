/* * Copyright (C) 2012 Spreadtrum Communications Inc.
 */

#ifndef __ADI_H__
#define __ADI_H__

void sci_adi_init(void);

/*
 * sci_get_adie_chip_id - read a-die chip id
 */
u32 sci_get_adie_chip_id(void);

int sci_adi_read(u32 reg);

/*
 * WARN: the arguments (reg, value) is different from
 * the general __raw_writel(value, reg)
 * For sci_adi_write_fast: if set sync 1, then this function will
 * return until the val have reached hardware.otherwise, just
 * async write(is maybe in software buffer)
 */
int sci_adi_write_fast(u32 reg, u16 val, u32 sync);
int sci_adi_write(u32 reg, u16 or_val, u16 clear_msk);

static inline int sci_adi_raw_write(u32 reg, u16 val)
{
#if defined(CONFIG_FPGA)
	return 0;
#else
	return sci_adi_write_fast(reg, val, 1);
#endif  /* CONFIG_FPGA */
}

static inline int sci_adi_set(u32 reg, u16 bits)
{
#if defined(CONFIG_FPGA)
	return 0;
#else
	return sci_adi_write(reg, bits, 0);
#endif  /* CONFIG_FPGA */
}

static inline int sci_adi_clr(u32 reg, u16 bits)
{
#if defined(CONFIG_FPGA)
	return 0;
#else
	return sci_adi_write(reg, 0, bits);
#endif  /* CONFIG_FPGA */
}

#endif
