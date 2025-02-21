#include <common.h>
#include <asm/arch/sprd_reg.h>
#include "cp_boot.h"

extern void boot_cp0(void);
extern void sipc_addr_reset(void);


void modem_entry(void)
{
	sipc_addr_reset();

#if !defined( CONFIG_KERNEL_BOOT_CP )
	boot_cp0();
#endif
}

