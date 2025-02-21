#include <common.h>
#include <asm/arch/sprd_reg.h>
#include "cp_boot.h"

extern void boot_cp0(void);
extern void sipc_addr_reset(void);


void modem_entry(void)
{
#if !defined( CONFIG_KERNEL_BOOT_CP )
	sipc_addr_reset();
	boot_cp0();
#endif
}

