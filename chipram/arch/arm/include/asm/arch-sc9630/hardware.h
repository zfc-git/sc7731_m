/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
 */

#ifndef __ASM_ARCH_SCI_HARDWARE_H
#define __ASM_ARCH_SCI_HARDWARE_H

#define SCI_IOMAP(x)	(SCI_IOMAP_BASE + (x))

#ifndef SCI_ADDR
#define SCI_ADDR(_b_, _o_)                              ( (u32)(_b_) + (_o_) )
#endif

#include <asm/sizes.h>

#if defined(CONFIG_SPX15)
#include "chip_x15/__hardware-sc8830.h"
#elif defined(CONFIG_SCX35L64)
#include "chip_x35l64/__hardware-scx35l64.h"
#elif defined(CONFIG_ARCH_SCX35L)
	#if defined(CONFIG_ARCH_SCX20L)
		#include "chip_x20l/__hardware-sc9820.h"
	#else
		#include "chip_x35l/__hardware-sc9630.h"
	#endif
#else
#include "chip_x35/__hardware-sc8830.h"
#endif

#endif /* __ASM_ARCH_SCI_HARDWARE_H */
