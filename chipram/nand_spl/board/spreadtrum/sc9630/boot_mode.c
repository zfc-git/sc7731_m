#include <config.h>
#include "asm/arch/sci_types.h"
#if defined (CONFIG_SC8830) || defined(CONFIG_SC9630) || defined(CONFIG_SCX35L64)
#include "asm/arch/sprd_reg_global.h"
#include "asm/arch/sprd_reg_ahb.h"
#include "asm/arch/sprd_module_config.h"
#include "asm/arch/regs_ahb.h"
#include "asm/arch/__regs_ahb.h"
#include "asm/arch/chip_drv_common_io.h"
#include  "key_pad_reg.h"

#else
#include "asm/arch/sc8810_reg_global.h"
#include "asm/arch/sc8810_reg_ahb.h"
#include "asm/arch/sc8810_module_config.h"
#endif

/******************judge the boot mode**************/
PUBLIC BOOLEAN isSDCardBoot(void)
{
	uint32 s_keypad_raw_status = 0;
	uint32 s_keypad_status = 0;

	SDIO_Mdelay(20);

	s_keypad_raw_status = *(volatile uint32 *)(KPD_INT_RAW_STATUS);
	s_keypad_status = *(volatile uint32 *)(KPD_STATUS);

	if (((s_keypad_raw_status & 0x03) == 0x03) && (((s_keypad_status & 0x7777) == 0x0001) || ((s_keypad_status & 0x7777) == 0x0100))) {
		return TRUE;
	} else {
		return FALSE;
	}
}
